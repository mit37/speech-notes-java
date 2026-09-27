package io.github.mit37.speechnotes.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;

/**
 * Golden-file comparison with a deliberate update path.
 *
 * <p>Regenerate with {@code ./gradlew test -Dspeechnotes.updateGolden=true} (or {@code
 * scripts/regen_golden.sh}): the file is written and the test fails once, so the updated file has
 * to be looked at and committed on purpose rather than as a side effect.
 */
final class Golden {

  /** Relative to the project directory, which is where Gradle runs tests from. */
  private static final Path DIRECTORY = Path.of("src", "test", "resources", "golden");

  private Golden() {}

  static void matches(String fileName, String actual) {
    Path golden = DIRECTORY.resolve(fileName);
    if (Boolean.getBoolean("speechnotes.updateGolden")) {
      write(golden, actual);
      throw new AssertionError(
          "golden file " + golden + " was updated; re-run without -Dspeechnotes.updateGolden");
    }
    String expected;
    try {
      expected = Files.readString(golden, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new AssertionError(
          "missing golden file "
              + golden
              + " — create it with scripts/regen_golden.sh (see docs/PLAN.md)",
          e);
    }
    Assertions.assertThat(actual)
        .as("differs from golden file %s (regenerate with scripts/regen_golden.sh)", golden)
        .isEqualTo(expected);
  }

  private static void write(Path golden, String actual) {
    try {
      Files.createDirectories(golden.getParent());
      Files.writeString(golden, actual, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new AssertionError("could not write golden file " + golden, e);
    }
  }
}
