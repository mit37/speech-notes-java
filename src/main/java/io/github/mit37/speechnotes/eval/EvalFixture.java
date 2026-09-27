package io.github.mit37.speechnotes.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * One measured fixture: the audio, the script it was synthesised from, and the words the notes are
 * expected to contain.
 *
 * <p>The rubric lives next to the script as {@code <name>.keywords.txt} — one phrase per line,
 * blank lines and {@code #} comments ignored. It is written by hand on purpose: a keyword list the
 * recogniser itself produced could not fail.
 */
public record EvalFixture(String name, Path audio, String reference, List<String> keywords) {

  public EvalFixture {
    keywords = List.copyOf(keywords);
  }

  /** Loads the fixture for {@code audio}: its script and rubric named after the audio file. */
  public static EvalFixture load(Path audio, Path fixturesDir) throws IOException {
    String name = withoutExtension(audio.getFileName().toString());
    Path referenceFile = fixturesDir.resolve(name + ".txt");
    Path keywordFile = fixturesDir.resolve(name + ".keywords.txt");
    if (!Files.isRegularFile(referenceFile)) {
      throw new IOException(
          "no reference script for " + audio + " (expected " + referenceFile + ")");
    }
    List<String> keywords =
        Files.isRegularFile(keywordFile) ? keywordsFrom(keywordFile) : List.of();
    return new EvalFixture(
        name, audio, Files.readString(referenceFile, StandardCharsets.UTF_8).strip(), keywords);
  }

  /** Builds a fixture in memory; used by tests that do not need files. */
  public static EvalFixture of(String name, Path audio, String reference, List<String> keywords) {
    return new EvalFixture(name, audio, reference, keywords);
  }

  private static List<String> keywordsFrom(Path file) throws IOException {
    return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
        .map(String::strip)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .toList();
  }

  private static String withoutExtension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot <= 0 ? fileName : fileName.substring(0, dot);
  }
}
