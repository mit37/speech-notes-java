package io.github.mit37.speechnotes.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvalFixturesTest {

  private static Path scratch(String name) throws IOException {
    Path directory = Path.of("build", "tmp", "eval-fixtures", name);
    deleteRecursively(directory);
    Files.createDirectories(directory);
    return directory;
  }

  private static void deleteRecursively(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return;
    }
    try (var paths = Files.walk(directory)) {
      for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }

  @Test
  @DisplayName("audio files are paired with their script and rubric, in name order")
  void pairsAudioWithScriptsAndRubrics() throws IOException {
    Path audio = scratch("audio");
    Path fixtures = scratch("fixtures");
    Files.writeString(audio.resolve("lecture-02-b.wav"), "not really audio");
    Files.writeString(audio.resolve("lecture-01-a.wav"), "not really audio");
    Files.writeString(audio.resolve("notes.txt"), "not audio at all");
    Files.writeString(fixtures.resolve("lecture-01-a.txt"), "the first script");
    Files.writeString(
        fixtures.resolve("lecture-01-a.keywords.txt"), "# rubric\nfirst term\n\nsecond term\n");
    Files.writeString(fixtures.resolve("lecture-02-b.txt"), "the second script");

    List<EvalFixture> loaded = EvalFixtures.load(audio, fixtures);

    assertThat(loaded)
        .extracting(EvalFixture::name)
        .containsExactly("lecture-01-a", "lecture-02-b");
    assertThat(loaded.getFirst().reference()).isEqualTo("the first script");
    assertThat(loaded.getFirst().keywords()).containsExactly("first term", "second term");
    // No rubric file is allowed: it becomes an empty rubric rather than an error.
    assertThat(loaded.get(1).keywords()).isEmpty();
  }

  @Test
  @DisplayName("a fixture without a script is an error that names the missing file")
  void missingScriptIsReported() throws IOException {
    Path audio = scratch("audio-missing");
    Path fixtures = scratch("fixtures-missing");
    Files.writeString(audio.resolve("lecture-09-orphan.wav"), "not really audio");

    assertThatExceptionOfType(IOException.class)
        .isThrownBy(() -> EvalFixtures.load(audio, fixtures))
        .withMessageContaining("no reference script")
        .withMessageContaining("lecture-09-orphan.txt");
  }

  @Test
  void emptyDirectoriesAreRejected() throws IOException {
    Path audio = scratch("audio-empty");
    Path fixtures = scratch("fixtures-empty");

    assertThatExceptionOfType(IOException.class)
        .isThrownBy(() -> EvalFixtures.load(audio, fixtures))
        .withMessageContaining("no .wav or .mp3 fixtures");

    assertThatExceptionOfType(IOException.class)
        .isThrownBy(() -> EvalFixtures.load(Path.of("no-such-directory"), fixtures))
        .withMessageContaining("no audio directory");
  }
}
