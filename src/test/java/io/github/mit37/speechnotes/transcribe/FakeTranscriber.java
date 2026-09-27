package io.github.mit37.speechnotes.transcribe;

import io.github.mit37.speechnotes.audio.AudioSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A transcriber that says whatever it was told to say, whatever audio it is handed.
 *
 * <p>It exists so microphone and UI behaviour can be tested without a microphone, a model or any
 * native library — the fake replays a script and drains the source, exactly like the real one.
 */
public final class FakeTranscriber implements Transcriber {

  private final List<TranscriptEvent> script = new ArrayList<>();
  private double audioSeconds;
  private boolean closed;

  /** Adds a final result to the script; the audio time is derived from the last one. */
  public FakeTranscriber says(String text, double startSeconds, double endSeconds) {
    script.add(new TranscriptEvent.Final(startSeconds, endSeconds, text));
    audioSeconds = Math.max(audioSeconds, endSeconds);
    return this;
  }

  /** Adds a partial before the finals, so listeners see the same shape the real engine produces. */
  public FakeTranscriber saysPartially(String text, double startSeconds, double endSeconds) {
    script.add(0, new TranscriptEvent.Partial(startSeconds, endSeconds, text));
    return this;
  }

  public boolean isClosed() {
    return closed;
  }

  @Override
  public double transcribe(AudioSource source, Consumer<TranscriptEvent> sink) throws IOException {
    byte[] buffer = new byte[4096];
    long frames = 0;
    int read;
    while ((read = source.read(buffer)) >= 0) {
      frames += read / AudioSource.BYTES_PER_FRAME;
    }
    for (TranscriptEvent event : script) {
      sink.accept(event);
    }
    return audioSeconds > 0 ? audioSeconds : frames / (double) AudioSource.SAMPLE_RATE;
  }

  @Override
  public String describe() {
    return "fake transcriber (test)";
  }

  @Override
  public void close() {
    closed = true;
  }
}
