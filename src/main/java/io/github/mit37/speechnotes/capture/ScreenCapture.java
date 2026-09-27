package io.github.mit37.speechnotes.capture;

import java.nio.file.Path;

/**
 * Takes a screenshot for the question-and-answer feature (PRD G4).
 *
 * <p>An interface for the same reason the audio is one: the real capture needs a display, and this
 * machine has none, so the UI and its tests use fakes (docs/PLAN.md).
 */
public interface ScreenCapture {

  /**
   * Captures the screen as a PNG.
   *
   * @param name file name without extension, e.g. {@code shot-001}
   * @return the file that was written
   * @throws ScreenCaptureException when there is no display or the capture fails
   */
  Path capture(String name) throws ScreenCaptureException;
}
