package io.github.mit37.speechnotes.export;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mit37.speechnotes.notes.ActionItem;
import io.github.mit37.speechnotes.notes.NoteAttachment;
import io.github.mit37.speechnotes.notes.NoteSection;
import io.github.mit37.speechnotes.notes.NotesDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarkdownExporterTest {

  private final MarkdownExporter exporter = new MarkdownExporter();

  private static NotesDocument document() {
    return NotesDocument.builder("Caching", "lecture-01-caching.wav")
        .engine("Vosk vosk-api (model vosk-model-small-en-us-0.15)")
        .audioSeconds(35.4)
        .formatter("rule-based")
        .transcript("the cache pays off when the same data is requested more than once")
        .sections(
            List.of(
                new NoteSection(
                    "Notes", 1, List.of("The **cache** pays off."), List.of("Measure first")),
                new NoteSection("Action items", 1, List.of(), List.of("Instrument the hit rate"))))
        .keyTerms(List.of("cache", "expiry"))
        .actions(List.of(new ActionItem("Instrument the hit rate", "action:", 12.0)))
        .generatedAt(Instant.parse("2026-09-25T20:00:00Z"))
        .build();
  }

  @Test
  void rendersTitleAndMetadata() {
    String markdown = exporter.render(document());

    assertThat(markdown).startsWith("# Caching\n\n");
    assertThat(markdown).contains("_Source: lecture-01-caching.wav");
    assertThat(markdown).contains("Engine: Vosk vosk-api");
    assertThat(markdown).contains("Audio: 0:35");
    assertThat(markdown).contains("Formatter: rule-based");
    assertThat(markdown).contains("Generated: 2026-09-25 20:00 UTC_");
    // The metadata line ends with a blank line, or Markdown would read the next heading as text.
    assertThat(markdown).contains("UTC_\n\n## Notes");
  }

  @Test
  @DisplayName("sections become headings, bullets become list items and bold markup survives")
  void rendersSections() {
    String markdown = exporter.render(document());

    assertThat(markdown).contains("## Notes\n");
    assertThat(markdown).contains("The **cache** pays off.");
    assertThat(markdown).contains("- Measure first");
    assertThat(markdown).contains("## Action items\n");
    assertThat(markdown).contains("- Instrument the hit rate");
  }

  @Test
  @DisplayName("the transcript sits at the end so nobody mistakes it for the notes")
  void rendersTranscriptLast() {
    String markdown = exporter.render(document());

    int transcriptIndex = markdown.indexOf("## Transcript");
    assertThat(transcriptIndex).isGreaterThan(markdown.indexOf("## Action items"));
    assertThat(markdown).contains("_13 words, verbatim from the recogniser._");
    assertThat(markdown.substring(transcriptIndex)).contains("the cache pays off when");
  }

  @Test
  @DisplayName("an unanswered screenshot question is labelled as unanswered, not as answered")
  void rendersUnansweredAttachments() {
    NotesDocument withAttachment =
        NotesDocument.builder("Caching", "lecture.wav")
            .sections(List.of(new NoteSection("Notes", 1, List.of("Text."), List.of())))
            .attachments(
                List.of(
                    new NoteAttachment(
                        12.0, Path.of("screenshots", "shot-1.png"), "What is this chart?", null)))
            .build();

    String markdown = exporter.render(withAttachment);

    assertThat(markdown).contains("## Screenshot questions");
    assertThat(markdown).contains("**Q:** What is this chart?");
    assertThat(markdown).contains("**A:** _(not answered — no key or offline)_");
  }

  @Test
  void rendersAnsweredAttachments() {
    NotesDocument withAnswer =
        NotesDocument.builder("Caching", "lecture.wav")
            .sections(List.of(new NoteSection("Notes", 1, List.of("Text."), List.of())))
            .attachments(
                List.of(
                    new NoteAttachment(
                        3.0, Path.of("shot.png"), "Which axis is time?", "The x axis.")))
            .build();

    assertThat(exporter.render(withAnswer)).contains("**A:** The x axis.");
  }

  @Test
  @DisplayName("an empty transcript is marked empty rather than left blank")
  void rendersEmptyTranscript() {
    NotesDocument empty = NotesDocument.builder("Nothing", "silence.wav").build();

    String markdown = exporter.render(empty);

    assertThat(markdown).contains("## Transcript");
    assertThat(markdown).contains("_(empty)_");
    assertThat(markdown).contains("_0 words");
  }

  @Test
  void writesFilesAndCreatesDirectories() throws IOException {
    Path target = Path.of("build", "tmp", "export-test", "notes.md");
    Files.deleteIfExists(target);

    Path written = exporter.write(document(), target);

    assertThat(written).isEqualTo(target);
    assertThat(Files.readString(target)).startsWith("# Caching");
  }

  @Test
  void namesAndExtensions() {
    assertThat(exporter.name()).isEqualTo("markdown");
    assertThat(exporter.extension()).isEqualTo(".md");
    assertThat(exporter.suggestedFileName(document())).isEqualTo("notes.md");
  }
}
