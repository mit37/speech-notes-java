package io.github.mit37.speechnotes.capture;

/** Raised when a screenshot cannot be taken; the message is meant for the user. */
public class ScreenCaptureException extends Exception {

  private static final long serialVersionUID = 1L;

  public ScreenCaptureException(String message, Throwable cause) {
    super(message, cause);
  }

  public ScreenCaptureException(String message) {
    super(message);
  }
}
