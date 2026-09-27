package io.github.mit37.speechnotes.ui;

import io.github.mit37.speechnotes.cli.CliArgs;
import io.github.mit37.speechnotes.cli.Command;
import io.github.mit37.speechnotes.cli.Options;
import javafx.application.Application;

/**
 * Starts the desktop UI.
 *
 * <p>{@code main} does not live in a class that extends {@link Application}, and that is on
 * purpose: a launcher class that extends {@code Application} refuses to start when JavaFX is on the
 * classpath instead of the module path ("JavaFX runtime components are missing"). This indirection
 * is the documented way around it.
 */
public final class UiLauncher {

  private static volatile Options pending = Options.defaults();

  private UiLauncher() {}

  /** Options the UI should use; set just before the toolkit starts. */
  static Options pendingOptions() {
    return pending;
  }

  /** Starts the window and blocks until it is closed. */
  public static void launch(Options options) {
    pending = options;
    Application.launch(SpeechNotesApp.class);
  }

  /** Entry point for {@code ./gradlew run --args="--ui …"}. */
  public static void main(String[] args) {
    Command command = CliArgs.parse(args);
    launch(command instanceof Command.ShowUi ui ? ui.options() : Options.defaults());
  }
}
