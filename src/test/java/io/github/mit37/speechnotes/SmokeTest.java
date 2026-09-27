package io.github.mit37.speechnotes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke test of the assembled application: it starts, answers {@code --version} and {@code --help},
 * refuses modes it cannot do yet, and reports a missing file instead of pretending to work.
 */
class SmokeTest {

  private record Run(int exitCode, String out, String err) {}

  private static Run run(String... args) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int exitCode;
    try (PrintStream outStream = new PrintStream(out, true, StandardCharsets.UTF_8);
        PrintStream errStream = new PrintStream(err, true, StandardCharsets.UTF_8)) {
      exitCode = CliMain.run(List.of(args), outStream, errStream);
    }
    return new Run(
        exitCode, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("--version prints the version and the repository and exits 0")
  void versionSmokeTest() {
    Run run = run("--version");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains(AppInfo.DISPLAY_NAME).contains(AppInfo.VERSION);
    assertThat(run.out()).contains(AppInfo.REPO_URL);
    assertThat(run.err()).isEmpty();
  }

  @Test
  @DisplayName("no arguments prints the help and exits 0")
  void helpByDefaultSmokeTest() {
    Run run = run();

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains("Usage: speech-notes-java").contains("--file").contains("--mic");
  }

  @Test
  @DisplayName("a file that does not exist fails with the path and exit code 1")
  void missingFileFailsCleanly() {
    Run run = run("--file", "no-such-recording.wav");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).contains("not found").contains("no-such-recording.wav");
  }

  // `--ui` is deliberately not run here: it now starts a real JavaFX window and would block the
  // suite. The window itself is covered by MainViewTest, headless through Monocle.

  @Test
  @DisplayName(
      "microphone mode either records for the bounded test window or explains what is missing")
  void microphoneModeIsBoundedAndHonest() {
    // The test JVM sets -Dspeechnotes.mic.maxSeconds=3, so this cannot record indefinitely.
    long startedAt = System.nanoTime();
    Run mic = run("--mic");
    double elapsed = (System.nanoTime() - startedAt) / 1e9;

    assertThat(elapsed).isLessThan(60);
    assertThat(mic.exitCode()).isIn(0, 1);
    if (mic.exitCode() == 1) {
      assertThat(mic.err()).contains("microphone");
    } else {
      assertThat(mic.err()).contains("real-time factor");
    }
  }

  @Test
  @DisplayName("asking for Gemini formatting without a key is refused, not silently downgraded")
  void geminiFormattingIsRefusedWithoutAKey() {
    // The refusal comes before the audio is opened, so the missing file is never reached.
    Run run = run("--file", "lecture.wav", "--format", "gemini");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err())
        .contains("--format gemini needs")
        .contains("GEMINI_API_KEY")
        .contains("--dry-run");
  }

  @Test
  @DisplayName("a bad command line exits with the usage code and shows the help")
  void usageErrorsExitWithUsageCodeTest() {
    Run run = run("--nonsense");

    assertThat(run.exitCode()).isEqualTo(64);
    assertThat(run.err()).contains("unknown option: --nonsense").contains("Usage:");
  }
}
