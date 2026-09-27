package io.github.mit37.speechnotes.export;

import io.github.mit37.speechnotes.notes.NotesDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a {@link NotesDocument} somewhere as text: Markdown today.
 *
 * <p>{@link PdfExporter} deliberately does not implement this interface — a PDF is bytes, not a
 * string, and pretending it is one would mean encoding it twice.
 */
public interface NotesExporter {

  /** Short name for logs and file headers, e.g. {@code markdown}. */
  String name();

  /** File extension including the dot, e.g. {@code .md}. */
  String extension();

  /** The document as text. */
  String render(NotesDocument document);

  /** Renders and writes, creating the parent directory when needed. */
  default Path write(NotesDocument document, Path target) throws IOException {
    if (target.getParent() != null) {
      Files.createDirectories(target.getParent());
    }
    Files.writeString(target, render(document), StandardCharsets.UTF_8);
    return target;
  }

  /** Suggested file name inside an output directory, e.g. {@code notes.md}. */
  default String suggestedFileName(NotesDocument document) {
    return "notes" + extension();
  }
}
