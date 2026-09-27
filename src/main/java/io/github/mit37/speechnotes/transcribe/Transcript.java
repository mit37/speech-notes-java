package io.github.mit37.speechnotes.transcribe;

import java.time.Instant;

/**
 * A finished (or in-progress) session: the {@link TranscriptStore} plus what produced it.
 *
 * <p>Metadata lives here rather than in the store so the formatter and the exporters can label
 * their output without knowing anything about audio.
 */
public record Transcript(
    TranscriptStore store, String source, String engine, double audioSeconds, Instant finishedAt) {

  public String text() {
    return store.text();
  }
}
