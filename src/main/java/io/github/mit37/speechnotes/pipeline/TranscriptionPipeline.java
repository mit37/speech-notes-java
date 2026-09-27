package io.github.mit37.speechnotes.pipeline;

import io.github.mit37.speechnotes.audio.AudioSource;
import io.github.mit37.speechnotes.format.Formatter;
import io.github.mit37.speechnotes.format.FormattingOptions;
import io.github.mit37.speechnotes.notes.NotesDocument;
import io.github.mit37.speechnotes.transcribe.Transcriber;
import io.github.mit37.speechnotes.transcribe.Transcript;
import io.github.mit37.speechnotes.transcribe.TranscriptEvent;
import io.github.mit37.speechnotes.transcribe.TranscriptStore;
import io.github.mit37.speechnotes.transcribe.VoskTranscriber;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Audio in, notes out: transcribes a source into a {@link TranscriptStore} and hands the result to
 * a {@link Formatter}.
 *
 * <p>This is the one place where the pieces meet, which keeps the CLI and the UI from each growing
 * their own slightly different copy of the same wiring.
 */
public final class TranscriptionPipeline implements AutoCloseable {

  /** What a run produced, including how long it took (needed for the real-time factor). */
  public record Result(NotesDocument notes, Transcript transcript, double wallSeconds) {

    /** Processing time divided by audio time: below 1.0 means faster than real time. */
    public double realTimeFactor() {
      return transcript.audioSeconds() <= 0 ? -1 : wallSeconds / transcript.audioSeconds();
    }
  }

  private final Transcriber transcriber;
  private final Formatter formatter;

  public TranscriptionPipeline(Transcriber transcriber, Formatter formatter) {
    this.transcriber = transcriber;
    this.formatter = formatter;
  }

  /** Loads the model and wires the given formatter; the model is closed by {@link #close()}. */
  public static TranscriptionPipeline open(Path modelDir, Formatter formatter) {
    return new TranscriptionPipeline(VoskTranscriber.load(modelDir), formatter);
  }

  /** Transcribes {@code source} and formats the result. */
  public Result run(
      AudioSource source,
      String sourceName,
      FormattingOptions options,
      Consumer<TranscriptEvent> observer)
      throws IOException {
    TranscriptStore store = new TranscriptStore();
    if (observer != null) {
      store.addListener(observer);
    }
    long startedAt = System.nanoTime();
    double audioSeconds = transcriber.transcribe(source, store::accept);
    double wallSeconds = (System.nanoTime() - startedAt) / 1e9;
    Transcript transcript =
        new Transcript(store, sourceName, transcriber.describe(), audioSeconds, Instant.now());
    return new Result(formatter.format(transcript, options), transcript, wallSeconds);
  }

  /** The formatter in use, for logs and the notes header. */
  public Formatter formatter() {
    return formatter;
  }

  public Transcriber transcriber() {
    return transcriber;
  }

  @Override
  public void close() throws IOException {
    transcriber.close();
  }
}
