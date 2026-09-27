package io.github.mit37.speechnotes.transcribe;

/** Raised when transcription cannot start or must stop; the message is meant for the user. */
public class TranscriptionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TranscriptionException(String message) {
    super(message);
  }

  public TranscriptionException(String message, Throwable cause) {
    super(message, cause);
  }
}
