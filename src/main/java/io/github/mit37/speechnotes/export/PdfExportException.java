package io.github.mit37.speechnotes.export;

/**
 * Thrown when the PDF cannot be built. Rendering cannot fail for input reasons, only for library
 * ones.
 */
public class PdfExportException extends RuntimeException {

  public PdfExportException(String message, Throwable cause) {
    super(message, cause);
  }
}
