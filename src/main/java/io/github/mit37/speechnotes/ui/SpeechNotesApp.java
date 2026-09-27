package io.github.mit37.speechnotes.ui;

import io.github.mit37.speechnotes.AppInfo;
import io.github.mit37.speechnotes.audio.AudioSourceFactory;
import io.github.mit37.speechnotes.capture.RobotScreenCapture;
import io.github.mit37.speechnotes.cli.Options;
import io.github.mit37.speechnotes.format.FormattingOptions;
import io.github.mit37.speechnotes.format.GeminiFormatter;
import io.github.mit37.speechnotes.qa.GeminiQaService;
import java.nio.file.Path;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * The desktop window (PRD G6): record, pause, stop, a transcript pane, a notes pane and
 * screenshots.
 *
 * <p>Deliberately thin — it builds a {@link LiveSessionService} from the CLI options and hands it
 * to {@link MainView}. Launched through {@link UiLauncher}, because a class that both extends
 * {@code Application} and holds {@code main} needs JavaFX on the module path to start.
 */
public final class SpeechNotesApp extends Application {

  private MainView view;

  @Override
  public void start(Stage stage) {
    Options options = UiLauncher.pendingOptions();
    LiveSessionService session =
        new LiveSessionService(
            options.modelDir(),
            AudioSourceFactory.microphone(),
            "microphone",
            formattingOptions(options),
            GeminiFormatter.fromEnvironment());
    view =
        new MainView(
            session,
            new RobotScreenCapture(Path.of("screenshots")),
            GeminiQaService.fromEnvironment());
    stage.setTitle(
        AppInfo.DISPLAY_NAME
            + " "
            + AppInfo.VERSION
            + " — transcription runs offline "
            + MainView.SCREENSHOT_HOTKEY.getDisplayText()
            + " captures the screen");
    stage.setScene(new Scene(view.root(), 1040, 720));
    stage.show();
  }

  @Override
  public void stop() {
    if (view != null) {
      view.dispose();
    }
  }

  private static FormattingOptions formattingOptions(Options options) {
    FormattingOptions defaults = FormattingOptions.defaults();
    return new FormattingOptions(
        defaults.pauseParagraphSeconds(),
        defaults.maxParagraphWords(),
        defaults.maxKeyTerms(),
        defaults.minKeyTermOccurrences(),
        options.extractActions(),
        options.title());
  }
}
