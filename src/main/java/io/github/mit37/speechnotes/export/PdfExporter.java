package io.github.mit37.speechnotes.export;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import io.github.mit37.speechnotes.AppInfo;
import io.github.mit37.speechnotes.notes.NoteAttachment;
import io.github.mit37.speechnotes.notes.NoteSection;
import io.github.mit37.speechnotes.notes.NotesDocument;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF output through OpenPDF (PRD G5).
 *
 * <p>Three decisions worth knowing about:
 *
 * <ul>
 *   <li>Standard Helvetica, no embedded font files. The text is sanitised to Windows-1252 instead,
 *       which keeps the build free of font binaries and the output reproducible; characters outside
 *       that set become {@code ?} rather than a blank box.
 *   <li>{@code **bold**} runs become real bold runs, so the key terms the formatter marked are
 *       visible in the printed page, not asterisks.
 *   <li>The document says which formatter produced it and when, in the same words the Markdown
 *       export uses — the two files are meant to be interchangeable.
 * </ul>
 */
public final class PdfExporter {

  public static final String NAME = "pdf";

  private static final Pattern BOLD_RUN = Pattern.compile("\\*\\*(.+?)\\*\\*");

  /** Helvetica's encoding; also the set of characters the sanitiser guarantees. */
  private static final Charset WINANSI = Charset.forName(BaseFont.WINANSI);

  private static final float MARGIN = 56f;

  public String name() {
    return NAME;
  }

  public String extension() {
    return ".pdf";
  }

  /** Suggested file name inside an output directory. */
  public String suggestedFileName() {
    return "notes" + extension();
  }

  public byte[] render(NotesDocument document) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    Document pdf = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
    try {
      PdfWriter.getInstance(pdf, bytes);
      pdf.addTitle(sanitise(document.title()));
      pdf.addAuthor("speech-notes-java");
      pdf.addCreator(AppInfo.DISPLAY_NAME + " " + AppInfo.VERSION);
      pdf.addSubject(
          "Offline Vosk transcript with "
              + (document.formatter().isBlank() ? "notes" : document.formatter() + " notes"));
      pdf.open();

      pdf.add(
          spacing(paragraph(document.title(), font(20, Font.BOLD), font(20, Font.BOLD)), 0f, 10f));
      pdf.add(
          spacing(
              paragraph(
                  NoteFormatting.metadata(document),
                  font(9, Font.ITALIC),
                  font(9, Font.BOLD | Font.ITALIC)),
              0f,
              14f));

      for (NoteSection section : document.sections()) {
        addSection(pdf, section);
      }
      addScreenshotQuestions(pdf, document.attachments());
      addTranscript(pdf, document);
    } catch (DocumentException e) {
      throw new PdfExportException("could not build the PDF: " + e.getMessage(), e);
    } finally {
      pdf.close();
    }
    return bytes.toByteArray();
  }

  /** Renders and writes, creating the parent directory when needed. */
  public Path write(NotesDocument document, Path target) throws IOException {
    if (target.getParent() != null) {
      Files.createDirectories(target.getParent());
    }
    Files.write(target, render(document));
    return target;
  }

  private static void addSection(Document pdf, NoteSection section) throws DocumentException {
    if (section.isEmpty()) {
      return;
    }
    pdf.add(
        spacing(paragraph(section.heading(), font(13, Font.BOLD), font(13, Font.BOLD)), 8f, 4f));
    for (String text : section.paragraphs()) {
      pdf.add(spacing(paragraph(text, font(11, Font.NORMAL), font(11, Font.BOLD)), 0f, 6f));
    }
    for (String bullet : section.bullets()) {
      // Not com.lowagie.text.List: its bullet glyphs are drawn before the text, so reading the PDF
      // back puts every bullet marker at the top of the page. An indented dash reads better in the
      // text layer and matches what the Markdown export writes.
      Paragraph item =
          spacing(paragraph("- " + bullet, font(11, Font.NORMAL), font(11, Font.BOLD)), 0f, 3f);
      item.setIndentationLeft(14f);
      pdf.add(item);
    }
  }

  private static void addScreenshotQuestions(
      Document pdf, java.util.List<NoteAttachment> attachments) throws DocumentException {
    if (attachments.isEmpty()) {
      return;
    }
    pdf.add(
        spacing(
            paragraph("Screenshot questions", font(13, Font.BOLD), font(13, Font.BOLD)), 8f, 4f));
    for (NoteAttachment attachment : attachments) {
      pdf.add(
          spacing(
              paragraph("Q: " + attachment.question(), font(10, Font.NORMAL), font(10, Font.BOLD)),
              4f,
              2f));
      String answer =
          attachment.isAnswered()
              ? "A: " + attachment.answer()
              : "A: (not answered — no key or offline)";
      pdf.add(spacing(paragraph(answer, font(10, Font.NORMAL), font(10, Font.BOLD)), 0f, 2f));
      pdf.add(
          spacing(
              paragraph(
                  "Screenshot: "
                      + NoteFormatting.displayPath(attachment.image())
                      + " at "
                      + NoteFormatting.duration(attachment.atSeconds()),
                  font(8, Font.ITALIC),
                  font(8, Font.ITALIC)),
              0f,
              8f));
    }
  }

  private static void addTranscript(Document pdf, NotesDocument document) throws DocumentException {
    pdf.add(spacing(paragraph("Transcript", font(13, Font.BOLD), font(13, Font.BOLD)), 8f, 4f));
    pdf.add(
        spacing(
            paragraph(
                document.wordCount() + " words, verbatim from the recogniser.",
                font(9, Font.ITALIC),
                font(9, Font.ITALIC)),
            0f,
            4f));
    String transcript = document.transcript().isBlank() ? "(empty)" : document.transcript();
    pdf.add(paragraph(transcript, font(9, Font.NORMAL), font(9, Font.BOLD)));
  }

  /** Builds a paragraph, turning {@code **runs**} into bold chunks instead of literal asterisks. */
  private static Paragraph paragraph(String text, Font regular, Font bold) {
    Paragraph paragraph = new Paragraph();
    Matcher matcher = BOLD_RUN.matcher(text == null ? "" : text);
    int last = 0;
    while (matcher.find()) {
      if (matcher.start() > last) {
        paragraph.add(new Chunk(sanitise(text.substring(last, matcher.start())), regular));
      }
      paragraph.add(new Chunk(sanitise(matcher.group(1)), bold));
      last = matcher.end();
    }
    String tail = text == null ? "" : text.substring(last);
    if (!tail.isEmpty()) {
      paragraph.add(new Chunk(sanitise(tail), regular));
    }
    return paragraph;
  }

  private static Paragraph spacing(Paragraph paragraph, float before, float after) {
    paragraph.setSpacingBefore(before);
    paragraph.setSpacingAfter(after);
    return paragraph;
  }

  private static Font font(float size, int style) {
    return FontFactory.getFont(FontFactory.HELVETICA, BaseFont.WINANSI, false, size, style);
  }

  /** Keeps every character inside Helvetica's encoding; nothing silently disappears. */
  private static String sanitise(String text) {
    if (text == null) {
      return "";
    }
    StringBuilder safe = new StringBuilder(text.length());
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      if (character == '\n' || character == '\r' || character == '\t') {
        safe.append(' ');
      } else if (character < ' ' || !WINANSI.newEncoder().canEncode(character)) {
        safe.append('?');
      } else {
        safe.append(character);
      }
    }
    return safe.toString();
  }
}
