package io.github.mit37.speechnotes.qa;

import java.nio.file.Path;

/**
 * Answers a question about a screenshot (PRD G4).
 *
 * <p>Offline this has no implementation that can work — the feature is Gemini's, and pretending
 * otherwise would mean inventing an answer about a slide. So the only thing the offline path does
 * is refuse clearly, and {@link QaException} carries that message to the UI.
 */
public interface QAService {

  /**
   * Answers {@code question} about the image at {@code image}.
   *
   * @throws QaException when the feature is off, or the call fails
   */
  String answer(Path image, String question) throws QaException;

  /** True when this service can actually answer anything. */
  boolean isAvailable();

  /** Why it cannot answer, when it cannot. */
  String unavailableReason();
}
