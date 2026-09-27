package io.github.mit37.speechnotes.transcribe;

/**
 * One thing the recogniser said, with the audio time it covers.
 *
 * <p>Partials are replaced by later partials; finals are never revised. The UI shows both, the
 * formatter works on finals only.
 */
public sealed interface TranscriptEvent {

  /** Seconds into the audio where this text starts. */
  double startSeconds();

  /** Seconds into the audio where this text ends. */
  double endSeconds();

  /** The recognised text, already trimmed; never blank in events the transcriber emits. */
  String text();

  /** True for {@link Final} events. */
  boolean isFinal();

  /** A best-effort guess at text that is still being revised. */
  record Partial(double startSeconds, double endSeconds, String text) implements TranscriptEvent {

    @Override
    public boolean isFinal() {
      return false;
    }
  }

  /** Text the recogniser has accepted and will not revise. */
  record Final(double startSeconds, double endSeconds, String text) implements TranscriptEvent {

    @Override
    public boolean isFinal() {
      return true;
    }
  }
}
