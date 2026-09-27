package io.github.mit37.speechnotes.ui;

import io.github.mit37.speechnotes.notes.NotesDocument;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Record, pause, stop — the thing the buttons talk to.
 *
 * <p>An interface because the UI has to be testable on a machine with no microphone (docs/PLAN.md):
 * tests plug in a scripted session, the real one drives {@code MicAudioSource} and Vosk.
 */
public interface SessionService extends AutoCloseable {

  SessionState state();

  /** Registers a listener for every update; the listener may be called from any thread. */
  void addListener(Consumer<SessionUpdate> listener);

  /** Starts capturing audio and transcribing it. */
  void start();

  /** Stops capturing without losing the transcript so far. */
  void pause();

  /** Continues after {@link #pause()}. */
  void resume();

  /** Stops, formats the notes and reports them through a DONE update. */
  void stop();

  /** The notes of the last finished session, or {@code null}. */
  NotesDocument notes();

  /**
   * Records a screenshot question in the notes of this session, with its answer when there is one.
   *
   * <p>The attachment carries the moment it was asked at, so the export can put it next to the
   * right part of the lecture. An unanswered question is recorded as unanswered, never guessed: an
   * offline build has to say what it did not do.
   */
  void recordScreenshot(Path image, String question, String answer);

  /** What is being recorded, for the window title and the status line. */
  String describeSource();

  @Override
  void close();
}
