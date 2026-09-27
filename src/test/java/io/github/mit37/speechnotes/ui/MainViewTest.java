package io.github.mit37.speechnotes.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mit37.speechnotes.TestTranscripts;
import io.github.mit37.speechnotes.capture.ScreenCapture;
import io.github.mit37.speechnotes.format.FormattingOptions;
import io.github.mit37.speechnotes.format.RuleBasedFormatter;
import io.github.mit37.speechnotes.notes.NoteAttachment;
import io.github.mit37.speechnotes.notes.NotesDocument;
import io.github.mit37.speechnotes.qa.QAService;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.testfx.api.FxRobot;

/**
 * Drives the real scene graph with no display: JavaFX runs on Monocle's headless platform and the
 * session is scripted, so the window is exercised without a microphone, a model or a screen.
 *
 * <p>Mouse input goes through TestFX's robot, so the buttons are really clicked. Waits are explicit
 * barriers on the JavaFX queue rather than TestFX's event-flush helper, because that one waits
 * forever when the toolkit has nothing to do — which is exactly what a hanging test looks like.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class MainViewTest {

  private static final Path SHOTS_DIR = Path.of("build", "tmp", "ui-shots");

  private static FakeSessionService session;
  private static MainView view;
  private static Stage stage;
  private static FxRobot robot;

  @BeforeAll
  static void startHeadlessUi() throws InterruptedException {
    CountDownLatch started = new CountDownLatch(1);
    Platform.startup(started::countDown);
    assertThat(started.await(30, TimeUnit.SECONDS)).as("JavaFX toolkit starts").isTrue();
    robot = new FxRobot();

    session = new FakeSessionService(noteDocument());
    runOnFxThread(
        () -> {
          view =
              newView(session, new FakeScreenCapture(false, SHOTS_DIR), FakeQaService.disabled());
          stage = new Stage();
          stage.setScene(new Scene(view.root(), 900, 600));
          stage.show();
        });
  }

  /**
   * Hides the window without waiting for the FX queue afterwards.
   *
   * <p>Hiding the last window is what makes JavaFX shut the toolkit down (the default implicit
   * exit), so a barrier posted after it may never run — which is a 30-second timeout race, not a
   * real failure. Nothing after this needs the toolkit.
   */
  @AfterAll
  static void stopUi() {
    if (stage != null) {
      Platform.runLater(stage::hide);
    }
  }

  /** Runs the action on the FX thread and waits until the FX queue is empty again. */
  private static void runOnFxThread(Runnable action) {
    if (Platform.isFxApplicationThread()) {
      action.run();
      flushFxQueue();
      return;
    }
    CountDownLatch done = new CountDownLatch(1);
    Platform.runLater(
        () -> {
          try {
            action.run();
          } finally {
            done.countDown();
          }
        });
    await(done, "FX action completes");
    flushFxQueue();
  }

  /** Posts a no-op and waits for it, which cannot return until everything queued before it ran. */
  private static void flushFxQueue() {
    if (Platform.isFxApplicationThread()) {
      return;
    }
    CountDownLatch flush = new CountDownLatch(1);
    Platform.runLater(flush::countDown);
    await(flush, "FX queue drains");
  }

  private static void await(CountDownLatch latch, String what) {
    try {
      if (!latch.await(30, TimeUnit.SECONDS)) {
        throw new AssertionError(what + " timed out after 30s");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  /** Clicks a control the way a person would, so the handlers and bindings are really exercised. */
  private static void click(String id) {
    robot.clickOn("#" + id);
    flushFxQueue();
  }

  private static NotesDocument noteDocument() {
    return new RuleBasedFormatter()
        .format(
            TestTranscripts.transcript(
                "the cache pays off when the same data repeats", "action: instrument the hit rate"),
            FormattingOptions.defaults().withTitle("Caching"));
  }

  private static Button button(String id) {
    return (Button) view.root().lookup("#" + id);
  }

  private static TextArea pane(String id) {
    return (TextArea) view.root().lookup("#" + id);
  }

  private static String label(String id) {
    return ((Label) view.root().lookup("#" + id)).getText();
  }

  @Test
  @Order(1)
  @DisplayName("the window shows the controls, panes and status line")
  void showsTheControls() {
    assertThat(view.root().lookupAll(".button"))
        .as("record, pause, stop and screenshot")
        .hasSizeGreaterThanOrEqualTo(4);
    assertThat(button("record-button")).isNotNull();
    assertThat(button("pause-button")).isNotNull();
    assertThat(button("stop-button")).isNotNull();
    assertThat(pane("transcript-pane").getPromptText()).contains("nothing leaves the machine");
    assertThat(pane("notes-pane")).isNotNull();
    assertThat(label("status-label")).contains("Ready");
    assertThat(label("elapsed-label")).isEqualTo("00:00");
  }

  @Test
  @Order(2)
  @DisplayName("clicking Record starts the session and fills the transcript, partial included")
  void recordStartsTheSession() {
    click("record-button");

    assertThat(session.starts()).isEqualTo(1);
    assertThat(view.viewModel().state()).isEqualTo(SessionState.RECORDING);
    assertThat(pane("transcript-pane").getText())
        .contains("the cache pays off")
        .contains("when the same data");
    assertThat(button("record-button").isDisabled()).isTrue();
    assertThat(button("stop-button").isDisabled()).isFalse();
  }

  @Test
  @Order(3)
  @DisplayName("Pause turns into Resume and back again")
  void pauseAndResume() {
    click("pause-button");

    assertThat(session.pauses()).isEqualTo(1);
    assertThat(button("pause-button").getText()).isEqualTo("Resume");
    assertThat(view.viewModel().state()).isEqualTo(SessionState.PAUSED);

    click("pause-button");

    assertThat(session.resumes()).isEqualTo(1);
    assertThat(button("pause-button").getText()).isEqualTo("Pause");
    assertThat(view.viewModel().state()).isEqualTo(SessionState.RECORDING);
  }

  @Test
  @Order(4)
  @DisplayName("Stop produces notes and re-enables Record")
  void stopShowsNotes() {
    click("stop-button");

    assertThat(session.stops()).isEqualTo(1);
    assertThat(view.viewModel().state()).isEqualTo(SessionState.DONE);
    assertThat(pane("notes-pane").getText()).startsWith("# Caching");
    assertThat(pane("notes-pane").getText()).contains("## Notes").contains("## Transcript");
    assertThat(label("status-label")).contains("Done:");
    assertThat(button("record-button").isDisabled()).isFalse();
    assertThat(button("stop-button").isDisabled()).isTrue();
  }

  @Test
  @Order(5)
  @DisplayName("a screenshot with no display explains itself in the status line")
  void screenshotWithoutADisplayExplains() {
    click("record-button");
    click("screenshot-button");

    assertThat(label("status-label")).contains("headless").contains("disabled here");
    assertThat(view.viewModel().screenshots()).isEmpty();
  }

  @Test
  @Order(6)
  @DisplayName("a screenshot with a display lands in the list")
  void screenshotWithADisplayIsListed() {
    MainView running =
        recordedView(new FakeScreenCapture(true, SHOTS_DIR), FakeQaService.disabled());
    runOnFxThread(() -> ((Button) running.root().lookup("#screenshot-button")).fire());

    assertThat(running.viewModel().screenshots()).isNotEmpty();
    assertThat(running.viewModel().screenshots().getFirst()).startsWith("shot-001.png");
  }

  @Test
  @Order(7)
  @DisplayName("a typed question is asked about the screenshot and recorded in the notes")
  void screenshotQuestionIsAskedAndRecorded() {
    FakeSessionService recorded = new FakeSessionService(noteDocument());
    FakeQaService qa = FakeQaService.answering("The x axis is time.");
    MainView running = recordedView(new FakeScreenCapture(true, SHOTS_DIR), qa, recorded);

    runOnFxThread(
        () ->
            ((TextField) running.root().lookup("#question-field")).setText("Which axis is time?"));
    runOnFxThread(() -> ((Button) running.root().lookup("#screenshot-button")).fire());

    assertThat(qa.asked()).containsExactly("Which axis is time?");
    assertThat(running.viewModel().screenshots().getFirst())
        .contains("Which axis is time?")
        .contains("The x axis is time.");
    assertThat(recorded.screenshots()).hasSize(1);
    NoteAttachment attachment = recorded.screenshots().getFirst();
    assertThat(attachment.question()).isEqualTo("Which axis is time?");
    assertThat(attachment.answer()).isEqualTo("The x axis is time.");
    assertThat(attachment.atSeconds()).isEqualTo(recorded.seconds());
    // The notes pane is filled from the finished document; the attachment rides along with it.
    assertThat(running.viewModel().status()).contains("Answer added to the notes");
  }

  @Test
  @Order(8)
  @DisplayName("without a key the question is recorded unanswered and nothing is sent")
  void withoutAKeyNothingIsSent() {
    FakeSessionService recorded = new FakeSessionService(noteDocument());
    FakeQaService off = FakeQaService.disabled();
    MainView running = recordedView(new FakeScreenCapture(true, SHOTS_DIR), off, recorded);

    runOnFxThread(
        () ->
            ((TextField) running.root().lookup("#question-field")).setText("Which axis is time?"));
    runOnFxThread(() -> ((Button) running.root().lookup("#screenshot-button")).fire());

    assertThat(off.asked()).isEmpty();
    assertThat(recorded.screenshots()).hasSize(1);
    assertThat(recorded.screenshots().getFirst().isAnswered()).isFalse();
    assertThat(running.viewModel().screenshots().getFirst()).endsWith(": (unanswered)");
    assertThat(running.viewModel().status()).contains("GEMINI_API_KEY");
  }

  @Test
  @Order(9)
  @DisplayName("a refused question says so and still lands in the notes as unanswered")
  void refusedQuestionsAreReported() {
    FakeSessionService recorded = new FakeSessionService(noteDocument());
    FakeQaService refused =
        FakeQaService.failing("screenshot Q&A failed: Gemini refused (HTTP 403)");
    MainView running = recordedView(new FakeScreenCapture(true, SHOTS_DIR), refused, recorded);

    runOnFxThread(
        () -> ((TextField) running.root().lookup("#question-field")).setText("What is this?"));
    runOnFxThread(() -> ((Button) running.root().lookup("#screenshot-button")).fire());

    assertThat(running.viewModel().status()).contains("HTTP 403");
    assertThat(recorded.screenshots().getFirst().isAnswered()).isFalse();
    assertThat(running.viewModel().screenshots().getFirst()).contains("(unanswered)");
  }

  @Test
  @Order(10)
  @DisplayName("the screenshot hotkey is bound to the window and captures, unlike the button alone")
  void hotkeyCapturesTheScreen() {
    FakeSessionService recorded = new FakeSessionService(noteDocument());
    MainView running =
        recordedView(new FakeScreenCapture(true, SHOTS_DIR), FakeQaService.disabled(), recorded);

    Runnable accelerator =
        running.root().getScene().getAccelerators().get(MainView.SCREENSHOT_HOTKEY);
    assertThat(accelerator).as("Ctrl/Cmd+Shift+S is registered on the scene (PRD G4)").isNotNull();

    runOnFxThread(accelerator);

    assertThat(running.viewModel().screenshots()).isNotEmpty();
    assertThat(running.viewModel().screenshots().getFirst())
        .startsWith("shot-001.png"); // What is asserted is the accelerator JavaFX would run for
    // Ctrl/Cmd+Shift+S. Neither the TestFX
    // robot nor firing a synthetic KeyEvent reaches it under the Monocle headless platform, because
    // accelerator matching happens in the toolkit's own key path — so the binding, not the OS key
    // routing, is what this test can honestly cover.
  }

  @Test
  @Order(11)
  @DisplayName("the hotkey outside a session refuses to capture and says why")
  void hotkeyOutsideASessionRefuses() {
    MainView idle =
        newView(
            new FakeSessionService(noteDocument()),
            new FakeScreenCapture(true, SHOTS_DIR),
            FakeQaService.disabled());
    runOnFxThread(() -> new Scene(idle.root(), 900, 600));

    Runnable accelerator = idle.root().getScene().getAccelerators().get(MainView.SCREENSHOT_HOTKEY);
    runOnFxThread(accelerator);

    assertThat(idle.viewModel().screenshots()).isEmpty();
    assertThat(idle.viewModel().status()).isEqualTo(MainView.RECORD_FIRST);
  }

  /** A fresh window with a recording session, asked about screenshots on the calling thread. */
  private static MainView recordedView(ScreenCapture capture, QAService qa) {
    return recordedView(capture, qa, new FakeSessionService(noteDocument()));
  }

  private static MainView recordedView(
      ScreenCapture capture, QAService qa, FakeSessionService session) {
    MainView running = newView(session, capture, qa);
    runOnFxThread(
        () -> {
          // One scene per window: a second Scene for the same root is an error in JavaFX.
          new Scene(running.root(), 900, 600);
          ((Button) running.root().lookup("#record-button")).fire();
        });
    return running;
  }

  private static MainView newView(SessionService session, ScreenCapture capture, QAService qa) {
    // A direct executor on purpose: the answer is there when the click handler returns, so no test
    // ever has to sleep hoping that a background thread finished.
    return new MainView(session, capture, qa, Runnable::run);
  }
}
