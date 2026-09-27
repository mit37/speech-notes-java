package io.github.mit37.speechnotes.transcribe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

import io.github.mit37.speechnotes.AppInfo;
import io.github.mit37.speechnotes.audio.AudioSource;
import io.github.mit37.speechnotes.audio.FileAudioSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real Vosk model on the committed fixtures.
 *
 * <p>The model is downloaded, never committed (STANDARDS §2), so these tests skip when it is absent
 * and fail instead when {@code -Dspeechnotes.requireModel=true} is set (CI sets it).
 */
class VoskTranscriberTest {

  private static final Path MODEL_DIR = Path.of(AppInfo.DEFAULT_MODEL_DIR);
  private static final Path FIXTURE = Path.of("eval", "audio", "lecture-01-caching.wav");

  private static boolean modelPresent() {
    return Files.isDirectory(MODEL_DIR) && Files.isRegularFile(MODEL_DIR.resolve("am/final.mdl"));
  }

  private static void requireModelOrSkip() {
    if (!modelPresent() && Boolean.getBoolean("speechnotes.requireModel")) {
      fail(
          "the Vosk model is missing and speechnotes.requireModel=true — "
              + "run scripts/download_model.sh before the tests");
    }
    Assumptions.assumeTrue(modelPresent(), "Vosk model not downloaded: " + MODEL_DIR);
  }

  @Test
  @DisplayName("transcribes the caching fixture offline and hears the topic")
  void transcribesTheFixture() throws IOException {
    requireModelOrSkip();
    List<TranscriptEvent> events = new ArrayList<>();

    double audioSeconds;
    long startNanos = System.nanoTime();
    try (VoskTranscriber transcriber = VoskTranscriber.load(MODEL_DIR);
        FileAudioSource source = FileAudioSource.open(FIXTURE)) {
      audioSeconds = transcriber.transcribe(source, events::add);
    }
    double wallSeconds = (System.nanoTime() - startNanos) / 1e9;

    String text = finalText(events);
    System.out.printf(
        Locale.ROOT,
        "transcribed %.1fs of audio in %.1fs (real-time factor %.2f)%n",
        audioSeconds,
        wallSeconds,
        wallSeconds / audioSeconds);

    assertThat(audioSeconds).isGreaterThan(20);
    assertThat(text.length()).isGreaterThan(100);
    assertThat(text.toLowerCase(Locale.ROOT)).contains("cache");
    assertThat(text.toLowerCase(Locale.ROOT)).contains("expiry");
  }

  @Test
  @DisplayName("partials arrive before the finals that replace them, in time order")
  void emitsPartialsThenFinals() throws IOException {
    requireModelOrSkip();
    List<TranscriptEvent> events = new ArrayList<>();

    try (VoskTranscriber transcriber = VoskTranscriber.load(MODEL_DIR);
        FileAudioSource source = FileAudioSource.open(FIXTURE)) {
      transcriber.transcribe(source, events::add);
    }

    assertThat(events).isNotEmpty();
    assertThat(events.get(0)).isInstanceOf(TranscriptEvent.Partial.class);
    assertThat(events).anyMatch(TranscriptEvent::isFinal);

    double previousEnd = -1;
    for (TranscriptEvent event : events) {
      assertThat(event.startSeconds()).isGreaterThanOrEqualTo(0);
      assertThat(event.endSeconds()).isGreaterThanOrEqualTo(event.startSeconds());
      if (event.isFinal()) {
        assertThat(event.startSeconds()).isGreaterThanOrEqualTo(previousEnd - 0.001);
        previousEnd = event.endSeconds();
      }
    }
  }

  @Test
  @DisplayName("a missing model points at the download script")
  void reportsAMissingModel() {
    assertThatExceptionOfType(TranscriptionException.class)
        .isThrownBy(() -> VoskTranscriber.load(Path.of("models", "does-not-exist")))
        .withMessageContaining("download_model.sh");
  }

  @Test
  @DisplayName("a directory that is not a model is reported as such")
  void reportsAnIncompleteModel() throws IOException {
    Path notAModel = Path.of("build", "tmp", "tests", "not-a-model");
    Files.createDirectories(notAModel);

    assertThatExceptionOfType(TranscriptionException.class)
        .isThrownBy(() -> VoskTranscriber.load(notAModel))
        .withMessageContaining("am/final.mdl");
  }

  private static String finalText(List<TranscriptEvent> events) {
    StringBuilder text = new StringBuilder();
    for (TranscriptEvent event : events) {
      if (event.isFinal()) {
        if (!text.isEmpty()) {
          text.append(' ');
        }
        text.append(event.text());
      }
    }
    return text.toString();
  }

  @Test
  @DisplayName("the target format is what Vosk is told to expect")
  void targetFormatIsSixteenKilohertzMono() {
    assertThat(AudioSource.SAMPLE_RATE).isEqualTo(16_000);
    assertThat(AudioSource.TARGET_FORMAT.getChannels()).isEqualTo(1);
    assertThat(AudioSource.TARGET_FORMAT.getSampleSizeInBits()).isEqualTo(16);
  }
}
