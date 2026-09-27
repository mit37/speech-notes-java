package io.github.mit37.speechnotes.export;

import io.github.mit37.speechnotes.notes.NoteAttachment;
import io.github.mit37.speechnotes.notes.NoteSection;
import io.github.mit37.speechnotes.notes.NotesDocument;
import java.util.List;

/**
 * Markdown output: the format the CLI prints and the default file export.
 *
 * <p>Section paragraphs arrive with {@code **bold**} runs from the formatter and are written
 * through unchanged — Markdown is where that markup already means what it says.
 */
public final class MarkdownExporter implements NotesExporter {

  public static final String NAME = "markdown";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String extension() {
    return ".md";
  }

  @Override
  public String render(NotesDocument document) {
    StringBuilder markdown = new StringBuilder();
    markdown.append("# ").append(document.title()).append("\n\n");
    // Blank line on purpose: without it Markdown folds the first "##" heading into the metadata
    // line.
    markdown.append(metadata(document)).append("\n\n");

    for (NoteSection section : document.sections()) {
      if (section.isEmpty()) {
        continue;
      }
      markdown.append("## ").append(section.heading()).append("\n\n");
      for (String paragraph : section.paragraphs()) {
        markdown.append(paragraph).append("\n\n");
      }
      for (String bullet : section.bullets()) {
        markdown.append("- ").append(bullet).append('\n');
      }
      if (!section.bullets().isEmpty()) {
        markdown.append('\n');
      }
    }

    markdown.append(screenshotQuestions(document.attachments()));
    markdown.append("## Transcript\n\n");
    markdown
        .append("_")
        .append(document.wordCount())
        .append(" words, verbatim from the recogniser._\n\n");
    markdown
        .append(document.transcript().isBlank() ? "_(empty)_" : document.transcript())
        .append('\n');
    return markdown.toString();
  }

  private static String metadata(NotesDocument document) {
    return "_" + NoteFormatting.metadata(document) + "_";
  }

  private static String screenshotQuestions(List<NoteAttachment> attachments) {
    if (attachments.isEmpty()) {
      return "";
    }
    StringBuilder markdown = new StringBuilder("## Screenshot questions\n\n");
    for (NoteAttachment attachment : attachments) {
      markdown
          .append("### ")
          .append(NoteFormatting.duration(attachment.atSeconds()))
          .append("\n\n");
      markdown.append("**Q:** ").append(attachment.question()).append("\n\n");
      markdown
          .append("**A:** ")
          .append(
              attachment.isAnswered()
                  ? attachment.answer()
                  : "_(not answered — no key or offline)_")
          .append("\n\n");
      markdown
          .append("Screenshot: `")
          .append(NoteFormatting.displayPath(attachment.image()))
          .append("`\n\n");
    }
    return markdown.toString();
  }
}
