package io.github.mit37.speechnotes.format;

/**
 * A stretch of speech the formatter treats as one thought.
 *
 * <p>Usually one Vosk final result; a final carrying punctuation is split further, and the pieces
 * inherit a share of the original time window.
 */
public record Segment(
    String text,
    double startSeconds,
    double endSeconds,
    double pauseBeforeSeconds,
    String enumerator) {

  public boolean isEnumerator() {
    return enumerator != null;
  }

  public int wordCount() {
    return text.isBlank() ? 0 : text.trim().split("\\s+").length;
  }
}
