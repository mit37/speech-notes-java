package io.github.mit37.speechnotes.eval;

import io.github.mit37.speechnotes.AppInfo;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Runs the eval and writes {@code eval/results.md} (milestone 8).
 *
 * <p>Started by {@code scripts/run_eval.sh} through the Gradle task {@code evalRun}, so the README
 * can quote numbers that a script produced instead of numbers somebody typed (STANDARDS §1).
 */
public final class EvalMain {

  private static final Path DEFAULT_AUDIO = Path.of("eval", "audio");
  private static final Path DEFAULT_FIXTURES = Path.of("eval", "fixtures");
  private static final Path DEFAULT_MODEL = Path.of(AppInfo.DEFAULT_MODEL_DIR);
  private static final Path DEFAULT_OUT = Path.of("eval", "results.md");

  private static final String USAGE =
      """
      Usage: evalRun [--audio <dir>] [--fixtures <dir>] [--model <dir>] [--out <file>]

        --audio     directory of fixture recordings   (default: eval/audio)
        --fixtures  directory of scripts and rubrics  (default: eval/fixtures)
        --model     Vosk model directory              (default: models/vosk-model-small-en-us-0.15)
        --out       where the Markdown report goes    (default: eval/results.md)
      """;

  private EvalMain() {}

  public static void main(String[] args) {
    Path audioDir = DEFAULT_AUDIO;
    Path fixturesDir = DEFAULT_FIXTURES;
    Path modelDir = DEFAULT_MODEL;
    Path out = DEFAULT_OUT;
    for (int index = 0; index < args.length; index += 2) {
      if (index + 1 >= args.length) {
        System.err.println("error: " + args[index] + " needs a value");
        System.err.println(USAGE);
        System.exit(2);
        return;
      }
      String value = args[index + 1];
      switch (args[index]) {
        case "--audio" -> audioDir = Path.of(value);
        case "--fixtures" -> fixturesDir = Path.of(value);
        case "--model" -> modelDir = Path.of(value);
        case "--out" -> out = Path.of(value);
        default -> {
          System.err.println("error: unknown option " + args[index]);
          System.err.println(USAGE);
          System.exit(2);
          return;
        }
      }
    }
    try {
      EvalRunner.Report report = run(audioDir, fixturesDir, modelDir, System.err);
      String markdown = report.markdown(Instant.now());
      if (out.getParent() != null) {
        Files.createDirectories(out.getParent());
      }
      Files.writeString(out, markdown, StandardCharsets.UTF_8);
      System.out.print(markdown);
      System.err.println("wrote " + out);
    } catch (IOException e) {
      System.err.println("error: " + e.getMessage());
      System.exit(1);
    }
  }

  /**
   * The part a test can drive: measure and return the report, with progress lines on {@code
   * progress}.
   */
  public static EvalRunner.Report run(
      Path audioDir, Path fixturesDir, Path modelDir, PrintStream progress) throws IOException {
    return new EvalRunner(modelDir)
        .run(EvalFixtures.load(audioDir, fixturesDir), progress::println);
  }
}
