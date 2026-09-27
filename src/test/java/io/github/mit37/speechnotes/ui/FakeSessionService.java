package io.github.mit37.speechnotes.ui;

import io.github.mit37.speechnotes.notes.NoteAttachment;
import io.github.mit37.speechnotes.notes.NotesDocument;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A session that does exactly what a test tells it to.
 *
 * <p>This is how the window is tested with no microphone, no model and no waiting: press Record and
 * the scripted session reports a partial, a final and finally a note document.
 */
public final class FakeSessionService implements SessionService {

  private final List<Consumer<SessionUpdate>> listeners = new CopyOnWriteArrayList<>();
  private final List<NoteAttachment> screenshots = new CopyOnWriteArrayList<>();
  private final NotesDocument document;
  private SessionState state = SessionState.IDLE;
  private double seconds = 12.5;
  private int starts;
  private int pauses;
  private int resumes;
  private int stops;
  private boolean closed;

  public FakeSessionService(NotesDocument document) {
    this.document = document;
  }

  @Override
  public SessionState state() {
    return state;
  }

  @Override
  public void addListener(Consumer<SessionUpdate> listener) {
    listeners.add(listener);
  }

  @Override
  public void start() {
    starts++;
    state = SessionState.RECORDING;
    emit(
        SessionUpdate.of(
            SessionState.STARTING, "Opening the input and loading the model (offline)…"));
    emit(SessionUpdate.recording("the cache pays off", "when the same data", 1.5, -1));
  }

  @Override
  public void pause() {
    pauses++;
    state = SessionState.PAUSED;
    emit(SessionUpdate.of(SessionState.PAUSED, "Paused. Nothing is being recorded."));
  }

  @Override
  public void resume() {
    resumes++;
    state = SessionState.RECORDING;
    emit(SessionUpdate.recording("the cache pays off", "", 2.5, -1));
  }

  @Override
  public void stop() {
    stops++;
    state = SessionState.DONE;
    emit(SessionUpdate.of(SessionState.FORMATTING, "Finishing up and building the notes…"));
    emit(
        new SessionUpdate(
            SessionState.DONE,
            document.transcript(),
            "",
            "Done: 8 words, 1 action item(s) — rule-based",
            document.audioSeconds(),
            document.actions().size(),
            document));
  }

  @Override
  public NotesDocument notes() {
    return document;
  }

  @Override
  public void recordScreenshot(Path image, String question, String answer) {
    screenshots.add(new NoteAttachment(seconds, image, question, answer));
  }

  @Override
  public String describeSource() {
    return "fake microphone";
  }

  @Override
  public void close() {
    closed = true;
  }

  public int starts() {
    return starts;
  }

  public int pauses() {
    return pauses;
  }

  public int resumes() {
    return resumes;
  }

  public int stops() {
    return stops;
  }

  public boolean isClosed() {
    return closed;
  }

  /** The screenshot questions this session was told to keep, in order. */
  public List<NoteAttachment> screenshots() {
    return List.copyOf(screenshots);
  }

  /** What the session reports as the current recording position. */
  public double seconds() {
    return seconds;
  }

  public void setSeconds(double newSeconds) {
    this.seconds = newSeconds;
  }

  private void emit(SessionUpdate update) {
    for (Consumer<SessionUpdate> listener : listeners) {
      listener.accept(update);
    }
  }
}
