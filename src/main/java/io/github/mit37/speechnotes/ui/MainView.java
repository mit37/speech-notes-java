package io.github.mit37.speechnotes.ui;

import io.github.mit37.speechnotes.capture.ScreenCapture;
import io.github.mit37.speechnotes.capture.ScreenCaptureException;
import io.github.mit37.speechnotes.gemini.GeminiConfig;
import io.github.mit37.speechnotes.qa.QAService;
import io.github.mit37.speechnotes.qa.QaException;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The scene graph: record / pause / stop, a transcript pane, a notes pane and the screenshot list.
 *
 * <p>Everything here binds to {@link RecordViewModel} and talks to {@link SessionService}; it knows
 * nothing about audio, Vosk or Gemini. That is what lets the UI be tested headless with a scripted
 * session (see {@code MainViewTest}).
 */
public final class MainView {

  /**
   * PRD G4 asks for a hotkey, not only a button: Ctrl/Cmd+Shift+S captures the screen.
   *
   * <p>It is a window-scoped accelerator, not a system-wide hook — the window has to have focus for
   * JavaFX to see the key, and a global one would need a native library this project does not take
   * on (the README says so where it lists limitations).
   */
  public static final KeyCodeCombination SCREENSHOT_HOTKEY =
      new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);

  /** Shown when the hotkey is pressed before recording, where there is no session to note it in. */
  static final String RECORD_FIRST =
      "Press Record first: a screenshot is answered at the moment it was taken, so it needs a "
          + "running session.";

  private final SessionService session;
  private final ScreenCapture screenCapture;
  private final QAService qaService;
  private final Executor askExecutor;
  private final RecordViewModel model = new RecordViewModel();
  private final BorderPane root = new BorderPane();
  private final TextArea transcriptArea = new TextArea();
  private final TextArea notesArea = new TextArea();
  private final ListView<String> screenshotList = new ListView<>();
  private final Button recordButton = new Button("Record");
  private final Button pauseButton = new Button("Pause");
  private final Button stopButton = new Button("Stop");
  private final Button screenshotButton = new Button("Screenshot");
  private final Label statusLabel = new Label();
  private final Label elapsedLabel = new Label();
  private final TextField questionField = new TextField();
  private int screenshotCounter;

  /** Production wiring: questions are answered on their own thread so the window keeps painting. */
  public MainView(SessionService session, ScreenCapture screenCapture, QAService qaService) {
    this(
        session,
        screenCapture,
        qaService,
        command -> Thread.ofVirtual().name("screenshot-qa").start(command));
  }

  /**
   * Full constructor, used by tests to answer questions on the calling thread.
   *
   * <p>Injecting the executor is what keeps the Q&A path testable: a test passes a direct executor
   * and the answer is there when the click handler returns, with no sleeping and no FX pumping.
   */
  public MainView(
      SessionService session,
      ScreenCapture screenCapture,
      QAService qaService,
      Executor askExecutor) {
    this.session = session;
    this.screenCapture = screenCapture;
    this.qaService = qaService;
    this.askExecutor = askExecutor;
    session.addListener(update -> Platform.runLater(() -> model.apply(update)));
    build();
    bindHotkey();
  }

  /**
   * Registers the accelerator as soon as the root lands in a scene.
   *
   * <p>Done here rather than in {@code SpeechNotesApp} so every window — including the ones tests
   * build — gets the hotkey, and so {@link #SCREENSHOT_HOTKEY} is the single place it is described.
   */
  private void bindHotkey() {
    root.sceneProperty()
        .addListener(
            (observable, oldScene, scene) -> {
              if (scene != null) {
                scene.getAccelerators().put(SCREENSHOT_HOTKEY, this::screenshotFromHotkey);
              }
            });
  }

  /** The hotkey and the button have to agree: both refuse to capture outside a session. */
  private void screenshotFromHotkey() {
    if (screenshotButton.isDisabled()) {
      model.statusProperty().set(RECORD_FIRST);
      return;
    }
    takeScreenshot();
  }

  public Parent root() {
    return root;
  }

  public RecordViewModel viewModel() {
    return model;
  }

  /** Unhooks from the session; called when the window closes. */
  public void dispose() {
    session.close();
  }

  private void build() {
    recordButton.setId("record-button");
    pauseButton.setId("pause-button");
    stopButton.setId("stop-button");
    screenshotButton.setId("screenshot-button");
    statusLabel.setId("status-label");
    elapsedLabel.setId("elapsed-label");
    transcriptArea.setId("transcript-pane");
    notesArea.setId("notes-pane");
    screenshotList.setId("screenshot-list");

    recordButton.setOnAction(event -> session.start());
    pauseButton.setOnAction(event -> togglePause());
    stopButton.setOnAction(event -> session.stop());
    screenshotButton.setOnAction(event -> takeScreenshot());
    // Same action, same guard, two ways in: the button and SCREENSHOT_HOTKEY.
    screenshotButton.setTooltip(
        new Tooltip(
            "Capture the screen for a question (" + SCREENSHOT_HOTKEY.getDisplayText() + ")"));

    recordButton.disableProperty().bind(model.busyProperty());
    stopButton.disableProperty().bind(model.busyProperty().not());
    pauseButton.disableProperty().bind(model.busyProperty().not());
    screenshotButton.disableProperty().bind(model.busyProperty().not());
    pauseButton
        .textProperty()
        .bind(
            javafx.beans.binding.Bindings.when(model.pausedProperty())
                .then("Resume")
                .otherwise("Pause"));

    transcriptArea.setEditable(false);
    transcriptArea.setWrapText(true);
    transcriptArea.setPromptText(
        "The transcript appears here as you speak — nothing leaves the machine.");
    transcriptArea.textProperty().bind(model.liveTranscript());

    notesArea.setEditable(false);
    notesArea.setWrapText(true);
    notesArea.setPromptText("Notes (headings, bullets, key terms, action items) appear here.");
    notesArea.textProperty().bind(model.notesProperty());

    questionField.setId("question-field");
    questionField.setPrefColumnCount(24);
    questionField.setPromptText(
        qaService.isAvailable()
            ? "Ask about the next screenshot (optional)"
            : "Screenshot Q&A needs " + GeminiConfig.KEY_ENV);

    screenshotList.setItems(model.screenshots());
    screenshotList.setPlaceholder(new Label("No screenshots yet."));
    screenshotButton.setAccessibleText(
        "Capture the screen (" + SCREENSHOT_HOTKEY.getDisplayText() + ")");

    HBox controls =
        new HBox(
            8,
            recordButton,
            pauseButton,
            stopButton,
            screenshotButton,
            questionField,
            elapsedLabel);
    controls.setAlignment(Pos.CENTER_LEFT);
    controls.setPadding(new Insets(8));

    HBox statusBar = new HBox(new Label("Status:"), statusLabel);
    statusBar.setSpacing(6);
    statusBar.setPadding(new Insets(6, 8, 8, 8));
    statusLabel.textProperty().bind(model.statusProperty());
    elapsedLabel.textProperty().bind(model.elapsedProperty());

    SplitPane panes =
        new SplitPane(titled("Transcript", transcriptArea), titled("Notes", notesArea));
    SplitPane.setResizableWithParent(panes, true);

    VBox right = new VBox(6, new Label("Screenshots"), screenshotList);
    VBox.setVgrow(screenshotList, Priority.ALWAYS);
    right.setPadding(new Insets(8));
    right.setPrefWidth(220);
    HBox.setHgrow(screenshotList, Priority.ALWAYS);

    root.setTop(controls);
    root.setCenter(panes);
    root.setRight(right);
    root.setBottom(statusBar);
  }

  private static VBox titled(String title, TextArea area) {
    Label label = new Label(title);
    VBox box = new VBox(4, label, area);
    VBox.setVgrow(area, Priority.ALWAYS);
    box.setPadding(new Insets(8));
    return box;
  }

  private void togglePause() {
    if (model.pausedProperty().get()) {
      session.resume();
    } else {
      session.pause();
    }
  }

  /**
   * Captures the screen and, when a question is typed, asks it about that screenshot.
   *
   * <p>With no key the question is still recorded — answered as unanswered, never invented — and
   * the status line says why nothing was sent.
   */
  private void takeScreenshot() {
    Path image;
    try {
      image = screenCapture.capture(String.format("shot-%03d", ++screenshotCounter));
    } catch (ScreenCaptureException e) {
      model.statusProperty().set(e.getMessage());
      return;
    }
    String fileName = image.getFileName().toString();
    String question = questionField.getText() == null ? "" : questionField.getText().strip();
    if (question.isEmpty()) {
      model.screenshots().add(fileName + " — saved; type a question next time to have it answered");
      model.statusProperty().set("Screenshot saved to " + image + ".");
      return;
    }
    if (!qaService.isAvailable()) {
      session.recordScreenshot(image, question, null);
      model.screenshots().add(fileName + " — " + question + ": (unanswered)");
      model.statusProperty().set(qaService.unavailableReason());
      return;
    }
    int slot = model.screenshots().size();
    model.screenshots().add(fileName + " — " + question + ": asking…");
    model.statusProperty().set("Asking about " + fileName + " — the transcript keeps running.");
    askExecutor.execute(() -> askInBackground(image, question, fileName, slot));
  }

  /** Runs on the ask executor, so a slow answer never freezes the window. */
  private void askInBackground(Path image, String question, String fileName, int slot) {
    String answer = null;
    String failure = null;
    try {
      answer = qaService.answer(image, question);
    } catch (QaException e) {
      failure = e.getMessage();
    }
    session.recordScreenshot(image, question, answer);
    String line = fileName + " — " + question + ": " + (answer == null ? "(unanswered)" : answer);
    String status = failure == null ? "Answer added to the notes." : failure;
    Platform.runLater(
        () -> {
          if (slot < model.screenshots().size()) {
            model.screenshots().set(slot, line);
          } else {
            model.screenshots().add(line);
          }
          model.statusProperty().set(status);
        });
  }
}
