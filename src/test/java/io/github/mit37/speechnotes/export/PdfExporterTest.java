package io.github.mit37.speechnotes.export;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mit37.speechnotes.notes.NoteSection;
import io.github.mit37.speechnotes.notes.NotesDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the PDF by reading it back with PDFBox, not by trusting the writer.
 *
 * <p>The golden file is the whole extracted text of {@link SampleNotes#document()}; the other tests
 * check the parts a text diff cannot express (document metadata, bold runs, pagination).
 */
class PdfExporterTest {

  private final PdfExporter exporter = new PdfExporter();

  private static String extractText(byte[] pdf) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      // PDFBox separates lines with the platform's line separator; the golden file uses LF so the
      // same expectation works on Windows and on CI.
      return new PDFTextStripper().getText(document).replace("\r\n", "\n").replace('\r', '\n');
    }
  }

  @Test
  void producesAReadablePdfWithMetadata() throws Exception {
    byte[] pdf = exporter.render(SampleNotes.document());

    assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertThat(document.getNumberOfPages()).isPositive();
      assertThat(document.getDocumentInformation().getTitle()).isEqualTo("Caching lecture");
      assertThat(document.getDocumentInformation().getCreator())
          .startsWith(io.github.mit37.speechnotes.AppInfo.DISPLAY_NAME);
    }
  }

  @Test
  @DisplayName("the extracted text matches the committed golden file")
  void extractedTextMatchesTheGoldenFile() throws Exception {
    Golden.matches("sample-notes.pdf.txt", extractText(exporter.render(SampleNotes.document())));
  }

  @Test
  @DisplayName("**bold** runs become bold text instead of literal asterisks")
  void boldRunsBecomeBoldText() throws Exception {
    byte[] pdf = exporter.render(SampleNotes.document());

    assertThat(fontsOnLinesContaining(pdf, "cache")).anyMatch(font -> font.contains("Bold"));
    assertThat(extractText(pdf)).doesNotContain("**");
  }

  @Test
  @DisplayName("an unanswered screenshot question is labelled as unanswered, never empty")
  void unansweredQuestionsAreLabelled() throws Exception {
    String text = extractText(exporter.render(SampleNotes.document()));

    assertThat(text).contains("Which axis is time?").contains("A: The x axis.");
    assertThat(text).contains("How big is the cache?").contains("(not answered");
    assertThat(text).contains("Screenshot: screenshots/shot-002.png at 2:20");
  }

  @Test
  @DisplayName("text outside Helvetica's encoding is replaced, not dropped")
  void unsupportedCharactersAreReplaced() throws Exception {
    NotesDocument document =
        NotesDocument.builder("Caching", "lecture.wav")
            .sections(
                List.of(new NoteSection("Notes", 1, List.of("日本語 and a 中文 phrase."), List.of())))
            .build();

    String text = extractText(exporter.render(document));

    assertThat(text).contains("Notes").contains("??");
  }

  @Test
  @DisplayName("a long transcript flows onto further pages without losing its end")
  void longTranscriptsPaginate() throws Exception {
    String longTranscript =
        IntStream.range(0, 1200)
            .mapToObj(index -> "word" + index)
            .reduce((left, right) -> left + " " + right)
            .orElse("");
    NotesDocument document =
        NotesDocument.builder("Long lecture", "long.wav")
            .engine("Vosk vosk-api (model vosk-model-small-en-us-0.15)")
            .formatter("rule-based")
            .transcript(longTranscript)
            .sections(List.of(new NoteSection("Notes", 1, List.of("Something."), List.of())))
            .build();

    byte[] pdf = exporter.render(document);

    try (PDDocument loaded = Loader.loadPDF(pdf)) {
      assertThat(loaded.getNumberOfPages()).isGreaterThan(1);
    }
    assertThat(extractText(pdf)).contains("word0 ").contains("word1199");
  }

  @Test
  void writesFilesAndCreatesDirectories() throws Exception {
    Path target = Path.of("build", "tmp", "pdf-test", "notes.pdf");
    Files.deleteIfExists(target);

    Path written = exporter.write(SampleNotes.document(), target);

    assertThat(written).isEqualTo(target);
    assertThat(Files.readAllBytes(target)).isNotEmpty();
    assertThat(extractText(Files.readAllBytes(target))).contains("Caching lecture");
  }

  @Test
  void namesAndExtensions() {
    assertThat(exporter.name()).isEqualTo("pdf");
    assertThat(exporter.extension()).isEqualTo(".pdf");
    assertThat(exporter.suggestedFileName()).isEqualTo("notes.pdf");
  }

  /**
   * The fonts used anywhere on a line containing {@code needle}, so real bold can be asserted.
   *
   * <p>{@link TextPosition#getUnicode()} is a single character, so the search has to look at the
   * line PDFBox hands to {@code writeString} and then collect the fonts of that line.
   */
  private static List<String> fontsOnLinesContaining(byte[] pdf, String needle) throws IOException {
    List<String> fonts = new ArrayList<>();
    try (PDDocument document = Loader.loadPDF(pdf)) {
      PDFTextStripper stripper =
          new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) {
              if (!text.contains(needle)) {
                return;
              }
              for (TextPosition position : positions) {
                fonts.add(position.getFont().getName());
              }
            }
          };
      stripper.getText(document);
    }
    return fonts;
  }
}
