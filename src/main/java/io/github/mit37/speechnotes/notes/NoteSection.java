package io.github.mit37.speechnotes.notes;

import java.util.List;

/**
 * One section of the notes.
 *
 * <p>Paragraph strings may contain {@code **bold**} runs; the exporters turn those into real
 * emphasis (Markdown keeps the markers, the PDF exporter renders bold text).
 */
public record NoteSection(
    String heading, int level, List<String> paragraphs, List<String> bullets) {

  public NoteSection {
    paragraphs = List.copyOf(paragraphs);
    bullets = List.copyOf(bullets);
  }

  public boolean isEmpty() {
    return paragraphs.isEmpty() && bullets.isEmpty();
  }
}
