package io.github.mit37.speechnotes.ui;

import io.github.mit37.speechnotes.notes.NotesDocument;
import io.github.mit37.speechnotes.transcribe.TranscriptEvent;

/**
 * One message from a session to whatever is showing it.
 *
 * <p>Everything the UI needs to change is in here, which is what keeps {@link MainView} free of any
 * knowledge about audio, Vosk or the network — and therefore testable with a fake session.
 */
public record SessionUpdate(
    SessionState state,
    String transcript,
    String partial,
    String message,
    double elapsedSeconds,
    int actionCount,
    NotesDocument notes) {

  public static SessionUpdate of(SessionState state, String message) {
    return new SessionUpdate(state, null, null, message, -1, -1, null);
  }

  public static SessionUpdate recording(
      String transcript, String partial, double elapsedSeconds, int actionCount) {
    return new SessionUpdate(
        SessionState.RECORDING, transcript, partial, null, elapsedSeconds, actionCount, null);
  }

  public static SessionUpdate event(TranscriptEvent event) {
    return new SessionUpdate(
        SessionState.RECORDING, null, null, null, event.endSeconds(), -1, null);
  }
}
