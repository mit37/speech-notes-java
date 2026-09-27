package io.github.mit37.speechnotes.ui;

import io.github.mit37.speechnotes.export.MarkdownExporter;
import java.util.Locale;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * The MVVM-ish half of the UI: observable state that the scene binds to and that a {@link
 * SessionService} updates. No JavaFX controls here, so it can be asserted on directly.
 */
public final class RecordViewModel {

  private final StringProperty state = new SimpleStringProperty(SessionState.IDLE.name());
  private final BooleanProperty busy = new SimpleBooleanProperty(false);
  private final BooleanProperty paused = new SimpleBooleanProperty(false);
  private final StringProperty transcript = new SimpleStringProperty("");
  private final StringProperty partial = new SimpleStringProperty("");
  private final StringProperty notes = new SimpleStringProperty("");
  private final StringProperty status =
      new SimpleStringProperty(
          "Ready. Press Record to transcribe the microphone — offline. "
              + MainView.SCREENSHOT_HOTKEY.getDisplayText()
              + " screenshots the screen while recording.");
  private final StringProperty elapsed = new SimpleStringProperty("00:00");
  private final IntegerProperty actionCount = new SimpleIntegerProperty(0);
  private final ObservableList<String> screenshots = FXCollections.observableArrayList();

  /** Applies one update from the session. */
  public void apply(SessionUpdate update) {
    if (update.state() != null) {
      state.set(update.state().name());
      busy.set(update.state().isRunning());
      paused.set(update.state() == SessionState.PAUSED);
    }
    if (update.transcript() != null) {
      transcript.set(update.transcript());
    }
    if (update.partial() != null) {
      partial.set(update.partial());
    }
    if (update.message() != null) {
      status.set(update.message());
    }
    if (update.elapsedSeconds() >= 0) {
      elapsed.set(formatDuration(update.elapsedSeconds()));
    }
    if (update.actionCount() >= 0) {
      actionCount.set(update.actionCount());
    }
    if (update.notes() != null) {
      notes.set(new MarkdownExporter().render(update.notes()));
      partial.set("");
      transcript.set(update.notes().transcript());
    }
  }

  /** Transcript plus whatever is still being revised, which is what the pane shows. */
  public StringBinding liveTranscript() {
    return Bindings.createStringBinding(
        () -> {
          String settled = transcript.get();
          String pending = partial.get();
          if (pending.isBlank()) {
            return settled;
          }
          return settled.isBlank() ? pending : settled + " " + pending;
        },
        transcript,
        partial);
  }

  public StringProperty transcriptProperty() {
    return transcript;
  }

  public StringProperty notesProperty() {
    return notes;
  }

  public StringProperty statusProperty() {
    return status;
  }

  public StringProperty elapsedProperty() {
    return elapsed;
  }

  public StringProperty stateProperty() {
    return state;
  }

  public BooleanProperty busyProperty() {
    return busy;
  }

  public BooleanProperty pausedProperty() {
    return paused;
  }

  public IntegerProperty actionCountProperty() {
    return actionCount;
  }

  public ObservableList<String> screenshots() {
    return screenshots;
  }

  public String transcript() {
    return transcript.get();
  }

  public String partial() {
    return partial.get();
  }

  public String notes() {
    return notes.get();
  }

  public String status() {
    return status.get();
  }

  public String elapsed() {
    return elapsed.get();
  }

  public SessionState state() {
    return SessionState.valueOf(state.get());
  }

  private static String formatDuration(double seconds) {
    int total = (int) Math.round(Math.max(0, seconds));
    return String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60);
  }
}
