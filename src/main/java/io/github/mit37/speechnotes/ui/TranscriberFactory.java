package io.github.mit37.speechnotes.ui;

import io.github.mit37.speechnotes.transcribe.Transcriber;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Opens a transcriber when a session starts.
 *
 * <p>The production factory loads the Vosk model; tests supply a scripted one, which is how the
 * session state machine (start, pause, stop, notes) is tested with no model and no microphone.
 */
@FunctionalInterface
public interface TranscriberFactory {

  Transcriber open() throws IOException;

  static TranscriberFactory vosk(Path modelDir) {
    return () -> io.github.mit37.speechnotes.transcribe.VoskTranscriber.load(modelDir);
  }
}
