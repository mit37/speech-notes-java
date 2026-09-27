package io.github.mit37.speechnotes.cli;

/** Thrown when the command line cannot be understood; {@code CliMain} turns it into an error. */
public class UsageException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UsageException(String message) {
    super(message);
  }
}
