package io.github.mit37.speechnotes.transcribe;

import io.github.mit37.speechnotes.audio.AudioSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import org.vosk.Model;
import org.vosk.Recognizer;

/**
 * Offline transcription through the Vosk Java API (PRD G1).
 *
 * <p>This class is the only place that touches the native recogniser, and it never touches the
 * network. Audio is fed in 8 KB chunks; partial results are emitted as they change, finals when
 * Vosk's endpointing closes a segment.
 */
public final class VoskTranscriber implements Transcriber {

  private static final int CHUNK_BYTES = 8 * 1024;

  private final Model model;
  private final Path modelDir;
  private final VoskResultParser parser;

  private VoskTranscriber(Model model, Path modelDir, VoskResultParser parser) {
    this.model = model;
    this.modelDir = modelDir;
    this.parser = parser;
  }

  /**
   * Loads the model from {@code modelDir}.
   *
   * @throws TranscriptionException if the directory is missing, incomplete or unloadable
   */
  public static VoskTranscriber load(Path modelDir) {
    if (!Files.isDirectory(modelDir)) {
      throw new TranscriptionException(
          "Vosk model not found in "
              + modelDir.toAbsolutePath()
              + " — run scripts/download_model.sh");
    }
    if (!Files.isRegularFile(modelDir.resolve("am").resolve("final.mdl"))) {
      throw new TranscriptionException(
          modelDir.toAbsolutePath() + " does not look like a Vosk model (am/final.mdl is missing)");
    }
    try {
      return new VoskTranscriber(new Model(modelDir.toString()), modelDir, new VoskResultParser());
    } catch (IOException e) {
      throw new TranscriptionException(
          "cannot load the Vosk model from " + modelDir.toAbsolutePath() + ": " + e.getMessage(),
          e);
    }
  }

  @Override
  public double transcribe(AudioSource source, Consumer<TranscriptEvent> sink) throws IOException {
    try (Recognizer recognizer = new Recognizer(model, AudioSource.SAMPLE_RATE)) {
      byte[] buffer = new byte[CHUNK_BYTES];
      long frames = 0;
      double segmentStart = 0;
      int read;
      while ((read = source.read(buffer)) >= 0) {
        if (read == 0) {
          continue;
        }
        frames += read / AudioSource.BYTES_PER_FRAME;
        double audioTime = frames / (double) AudioSource.SAMPLE_RATE;
        if (recognizer.acceptWaveForm(buffer, read)) {
          Optional<TranscriptEvent.Final> event =
              parser.finalResult(recognizer.getResult(), segmentStart, audioTime);
          event.ifPresent(sink);
          segmentStart = audioTime;
        } else {
          parser.partial(recognizer.getPartialResult(), segmentStart, audioTime).ifPresent(sink);
        }
      }
      double audioTime = frames / (double) AudioSource.SAMPLE_RATE;
      parser.finalResult(recognizer.getFinalResult(), segmentStart, audioTime).ifPresent(sink);
      return audioTime;
    }
  }

  @Override
  public String describe() {
    return "Vosk " + modelVersion() + " (16 kHz mono, model " + modelDir.getFileName() + ")";
  }

  private String modelVersion() {
    // Vosk's Java API does not expose its version; state the wrapper, not a made-up number.
    return "vosk-api";
  }

  /** The model directory this transcriber loaded. */
  public Path modelDir() {
    return modelDir;
  }

  @Override
  public void close() throws IOException {
    model.close();
  }
}
