package io.github.mit37.speechnotes.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.github.mit37.speechnotes.AppInfo;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The eval path, run for real on one committed fixture.
 *
 * <p>Needs the downloaded model, so it skips when that is missing and fails when CI says it must be
 * there ({@code -Dspeechnotes.requireModel=true}) — the same rule as the CLI file test.
 */
class EvalRunnerTest {

  private static final Path MODEL_DIR = Path.of(AppInfo.DEFAULT_MODEL_DIR);
  private static final Path AUDIO = Path.of("eval", "audio", "lecture-01-caching.wav");

  private static void requireModelOrSkip() {
    boolean present =
        Files.isDirectory(MODEL_DIR) && Files.isRegularFile(MODEL_DIR.resolve("am/final.mdl"));
    if (!present && Boolean.getBoolean("speechnotes.requireModel")) {
      fail(
          "the Vosk model is missing and speechnotes.requireModel=true — run scripts/download_model.sh");
    }
    Assumptions.assumeTrue(present, "Vosk model not downloaded: " + MODEL_DIR);
  }

  @Test
  @DisplayName("one fixture is transcribed and scored: WER, real-time factor and keyword recall")
  void measuresOneRealFixture() throws IOException {
    requireModelOrSkip();
    EvalFixture fixture = EvalFixture.load(AUDIO, Path.of("eval", "fixtures"));

    EvalRunner.Report report = new EvalRunner(MODEL_DIR).run(List.of(fixture), line -> {});

    EvalRunner.Entry entry = report.entries().getFirst();
    assertThat(entry.name()).isEqualTo("lecture-01-caching");
    assertThat(entry.referenceWords()).isGreaterThan(50);
    assertThat(entry.transcriptWords()).isGreaterThan(50);
    // The recogniser is good but not perfect on synthetic speech: both halves have to be true.
    assertThat(entry.wordErrorRate()).isBetween(0.0, 0.5);
    assertThat(entry.realTimeFactor()).isBetween(0.0, 2.0);
    assertThat(entry.keywordRecall()).isGreaterThan(0.5);
    assertThat(report.overallRealTimeFactor()).isPositive();
    // The formatter side of the same measurement: real terms out, and the score is a fraction.
    assertThat(entry.keyTerms()).isNotEmpty();
    assertThat(entry.keyTermRecall()).isBetween(0.0, 1.0);
    assertThat(entry.rubricPhrasesInKeyTerms())
        .as("only phrases the recogniser heard can count")
        .isSubsetOf(entry.keywordsFound());
    assertThat(report.keyTermRecall()).isBetween(0.0, 1.0);
    assertThat(report.markdown(java.time.Instant.parse("2026-09-26T00:00:00Z")))
        .contains("# Eval results")
        .contains("lecture-01-caching")
        .contains("Mean WER")
        .contains("Key-term recall");
  }

  @Test
  @DisplayName("EvalMain writes the report file and prints the same Markdown")
  void mainWritesTheReport() throws IOException {
    requireModelOrSkip();
    Path out = Path.of("build", "tmp", "eval-test", "results.md");
    Files.deleteIfExists(out);

    EvalRunner.Report report =
        EvalMain.run(
            Path.of("eval", "audio"),
            Path.of("eval", "fixtures"),
            MODEL_DIR,
            new PrintStream(java.io.OutputStream.nullOutputStream()));
    Files.createDirectories(out.getParent());
    Files.writeString(out, report.markdown(java.time.Instant.now()));

    assertThat(out).exists();
    assertThat(Files.readString(out)).contains("Real-time factor").contains("lecture-01-caching");
    assertThat(report.entries()).hasSize(5);
  }
}
