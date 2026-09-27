package io.github.mit37.speechnotes.audio;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Opens the audio a session should transcribe.
 *
 * <p>Lets the UI and the pipeline be driven by a microphone, by a file, or by a test double without
 * any of them knowing which: {@link #microphone()}, {@link #file(Path)}, and whatever a test needs.
 */
@FunctionalInterface
public interface AudioSourceFactory {

  AudioSource open() throws IOException;

  /** The default: live microphone input. */
  static AudioSourceFactory microphone() {
    return MicAudioSource::open;
  }

  /** Replays a recording as if it were live, which is how the UI can be demonstrated politely. */
  static AudioSourceFactory file(Path audio) {
    return () -> FileAudioSource.open(audio);
  }
}
