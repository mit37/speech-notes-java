package io.github.mit37.speechnotes.gemini;

/** Raised when a Gemini request fails or is refused before it is sent. */
public class GeminiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public GeminiException(String message) {
    super(message);
  }

  public GeminiException(String message, Throwable cause) {
    super(message, cause);
  }

  /** Raised when the feature is switched off, so callers can fall back without logging an error. */
  public static final class Disabled extends GeminiException {

    private static final long serialVersionUID = 1L;

    public Disabled(String message) {
      super(message);
    }
  }

  /** Raised when a hard cap is reached: requests per run or characters per run. */
  public static final class BudgetExceeded extends GeminiException {

    private static final long serialVersionUID = 1L;

    public BudgetExceeded(String message) {
      super(message);
    }
  }
}
