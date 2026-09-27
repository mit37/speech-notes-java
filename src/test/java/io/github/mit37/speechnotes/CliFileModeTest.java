package io.github.mit37.speechnotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The command the Definition of Done names, run end to end: a file in, notes out, fully offline.
 *
 * <p>Needs the downloaded model, so it skips when that is missing and fails when CI says it must be
 * there ({@code -Dspeechnotes.requireModel=true}).
 */
class CliFileModeTest {

  private static final Path MODEL_DIR = Path.of(AppInfo.DEFAULT_MODEL_DIR);
  private static final Path FIXTURE = Path.of("eval", "audio", "lecture-01-caching.wav");

  private record Run(int exitCode, String out, String err) {}

  private static boolean modelPresent() {
    return Files.isDirectory(MODEL_DIR) && Files.isRegularFile(MODEL_DIR.resolve("am/final.mdl"));
  }

  private static void requireModelOrSkip() {
    if (!modelPresent() && Boolean.getBoolean("speechnotes.requireModel")) {
      fail(
          "the Vosk model is missing and speechnotes.requireModel=true — run scripts/download_model.sh");
    }
    Assumptions.assumeTrue(modelPresent(), "Vosk model not downloaded: " + MODEL_DIR);
  }

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
  @DisplayName("--file prints notes to stdout, offline, with a real transcript")
  void printsNotesToStdout() {
    requireModelOrSkip();

    Run run = run("--file", FIXTURE.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).startsWith("# Lecture 01 caching");
    assertThat(run.out()).contains("## Notes").contains("## Transcript");
    assertThat(run.out().toLowerCase(Locale.ROOT)).contains("cache");
    // The engine banner is the honest claim: on-device Vosk, no network involved.
    assertThat(run.out()).contains("Engine: Vosk");
    assertThat(run.err()).contains("offline").contains("real-time factor");
    // The transcript section is the raw recogniser output, so it should be substantial.
    assertThat(run.out().split("\\s+").length).isGreaterThan(80);
  }

  @Test
  @DisplayName("--out writes notes.md and transcript.txt instead of printing")
  void writesFilesWhenAsked() throws IOException {
    requireModelOrSkip();
    Path outDir = Path.of("build", "tmp", "cli-out");
    deleteRecursively(outDir);

    Run run =
        run("--file", FIXTURE.toString(), "--out", outDir.toString(), "--title", "Caching lecture");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEmpty();
    Path notes = outDir.resolve("notes.md");
    Path transcript = outDir.resolve("transcript.txt");
    assertThat(notes).exists();
    assertThat(transcript).exists();
    assertThat(Files.readString(notes)).startsWith("# Caching lecture");
    assertThat(Files.readString(transcript).toLowerCase(Locale.ROOT)).contains("cache");
    assertThat(run.err()).contains("wrote");
  }

  @Test
  @DisplayName("--export both writes a Markdown and a PDF that say the same thing")
  void writesPdfWhenAsked() throws IOException {
    requireModelOrSkip();
    Path outDir = Path.of("build", "tmp", "cli-pdf-out");
    deleteRecursively(outDir);

    Run run =
        run(
            "--file",
            FIXTURE.toString(),
            "--out",
            outDir.toString(),
            "--export",
            "both",
            "--title",
            "Caching lecture");

    assertThat(run.exitCode()).isZero();
    assertThat(outDir.resolve("notes.md")).exists();
    Path pdf = outDir.resolve("notes.pdf");
    assertThat(pdf).exists();
    byte[] bytes = Files.readAllBytes(pdf);
    assertThat(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    try (PDDocument document = Loader.loadPDF(bytes)) {
      String text = new PDFTextStripper().getText(document);
      assertThat(text).contains("Caching lecture").contains("Transcript");
      assertThat(text).contains("Source: " + FIXTURE.getFileName());
    }
    assertThat(run.err()).contains("notes.pdf").contains("transcript.txt");
  }

  @Test
  @DisplayName("--export pdf writes no Markdown, because that is what was asked for")
  void pdfOnlySkipsMarkdown() throws IOException {
    requireModelOrSkip();
    Path outDir = Path.of("build", "tmp", "cli-pdf-only");
    deleteRecursively(outDir);

    Run run = run("--file", FIXTURE.toString(), "--out", outDir.toString(), "--export", "pdf");

    assertThat(run.exitCode()).isZero();
    assertThat(outDir.resolve("notes.pdf")).exists();
    assertThat(outDir.resolve("notes.md")).doesNotExist();
  }

  @Test
  @DisplayName("--no-actions leaves the action section out")
  void noActionsFlagIsHonoured() {
    requireModelOrSkip();

    Run withActions = run("--file", FIXTURE.toString());
    Run withoutActions = run("--file", FIXTURE.toString(), "--no-actions");

    assertThat(withActions.out()).contains("## Action items");
    assertThat(withoutActions.out()).doesNotContain("## Action items");
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
}
