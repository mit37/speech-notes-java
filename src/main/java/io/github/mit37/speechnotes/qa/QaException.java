package io.github.mit37.speechnotes.qa;

/** Raised when a screenshot question cannot be answered; the message is meant for the user. */
public class QaException extends Exception {

  private static final long serialVersionUID = 1L;

  public QaException(String message) {
    super(message);
  }

  public QaException(String message, Throwable cause) {
    super(message, cause);
  }
}
