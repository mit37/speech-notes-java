package io.github.mit37.speechnotes.transcribe;

import io.github.mit37.speechnotes.audio.AudioSource;
import java.io.IOException;
import java.util.function.Consumer;

/** Turns an {@link AudioSource} into {@link TranscriptEvent}s. */
public interface Transcriber extends AutoCloseable {

  /**
   * Transcribes until the source ends, pushing every event into {@code sink} as it is produced.
   *
   * @return the audio time, in seconds, that was processed
   */
  double transcribe(AudioSource source, Consumer<TranscriptEvent> sink) throws IOException;

  /** Human-readable description of the engine, printed in the notes header and in logs. */
  String describe();

  @Override
  void close() throws IOException;
}
