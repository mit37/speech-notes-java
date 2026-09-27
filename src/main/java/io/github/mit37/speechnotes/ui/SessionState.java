package io.github.mit37.speechnotes.ui;

/** What the recording session is doing, as far as the UI is concerned. */
public enum SessionState {
  /** Nothing running yet. */
  IDLE,
  /** Opening the microphone and loading the model. */
  STARTING,
  /** Capturing and transcribing. */
  RECORDING,
  /** Capture paused; nothing is being recorded. */
  PAUSED,
  /** Capture finished, notes being produced. */
  FORMATTING,
  /** Finished, notes available. */
  DONE,
  /** Something went wrong; the reason is in the status text. */
  FAILED;

  public boolean isRunning() {
    return this == STARTING || this == RECORDING || this == PAUSED || this == FORMATTING;
  }
}
