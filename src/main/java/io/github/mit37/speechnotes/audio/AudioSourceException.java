package io.github.mit37.speechnotes.audio;

import java.io.IOException;

/** Raised when an audio source cannot be opened or read; the message is meant for the user. */
public class AudioSourceException extends IOException {

  private static final long serialVersionUID = 1L;

  public AudioSourceException(String message) {
    super(message);
  }

  public AudioSourceException(String message, Throwable cause) {
    super(message, cause);
  }
}
