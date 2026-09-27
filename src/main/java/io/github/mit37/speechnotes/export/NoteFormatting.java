package io.github.mit37.speechnotes.export;

import io.github.mit37.speechnotes.notes.NotesDocument;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The few pieces of text both exporters share, so the Markdown and the PDF never drift apart.
 *
 * <p>Times are formatted in UTC on purpose: the notes outlive the machine that made them.
 */
final class NoteFormatting {

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);

  private NoteFormatting() {}

  static String timestamp(Instant moment) {
    return TIMESTAMP.format(moment);
  }

  static String duration(double seconds) {
    int total = (int) Math.round(Math.max(0, seconds));
    return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
  }

  /**
   * A path as notes should show it: forward slashes always, so the same document reads the same on
   * every platform (and so golden files do not depend on where they were generated).
   */
  static String displayPath(java.nio.file.Path path) {
    return path.toString().replace('\\', '/');
  }

  /** The one-line provenance the notes carry: source, engine, length, formatter, time. */
  static String metadata(NotesDocument document) {
    StringBuilder line = new StringBuilder("Source: ").append(document.source());
    if (!document.engine().isBlank()) {
      line.append(" · Engine: ").append(document.engine());
    }
    if (document.audioSeconds() > 0) {
      line.append(" · Audio: ").append(duration(document.audioSeconds()));
    }
    if (!document.formatter().isBlank()) {
      line.append(" · Formatter: ").append(document.formatter());
    }
    return line.append(" · Generated: ").append(timestamp(document.generatedAt())).toString();
  }
}
