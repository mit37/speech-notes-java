package io.github.mit37.speechnotes.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FileAudioSourceTest {

  private static final Path FIXTURE = Path.of("eval", "audio", "lecture-01-caching.wav");

  @Test
  @DisplayName("the committed fixture comes out as 16 kHz mono, matching its real duration")
  void readsTheFixture() throws IOException {
    try (FileAudioSource source = FileAudioSource.open(FIXTURE)) {
      assertThat(source.format()).isEqualTo(AudioSource.TARGET_FORMAT);
      assertThat(source.durationSeconds()).isGreaterThan(20);

      long frames = drain(source);

      assertThat(frames / (double) AudioSource.SAMPLE_RATE)
          .isCloseTo(source.durationSeconds(), org.assertj.core.data.Offset.offset(0.05));
    }
  }

  @Test
  @DisplayName("a missing file is reported with its path")
  void reportsMissingFiles() {
    Path missing = Path.of("eval", "audio", "not-here.wav");

    assertThatExceptionOfType(AudioSourceException.class)
        .isThrownBy(() -> FileAudioSource.open(missing))
        .withMessageContaining("not found")
        .withMessageContaining("not-here.wav");
  }

  @Test
  @DisplayName("a file that is not audio is rejected with a decodable-formats hint")
  void reportsUndecodableFiles() throws IOException {
    Path bogus = Path.of("build", "tmp", "tests", "not-audio.wav");
    Files.createDirectories(bogus.getParent());
    Files.writeString(bogus, "this is not a wav file", StandardCharsets.UTF_8);

    assertThatExceptionOfType(AudioSourceException.class)
        .isThrownBy(() -> FileAudioSource.open(bogus))
        .withMessageContaining("cannot decode")
        .withMessageContaining("WAV and MP3");
  }

  @Test
  @DisplayName("the bundled MP3 decoder registers itself with javax.sound")
  void mp3DecoderIsOnTheClasspath() {
    assertThat(FileAudioSource.isMp3DecoderRegistered())
        .as("mp3spi provides the MP3 decoder that PRD G1 asks for")
        .isTrue();
  }

  @Test
  @DisplayName("an MP3 decodes when one is present, and is honest when none is")
  void decodesMp3WhenAvailable() throws IOException {
    Path mp3 = Path.of("eval", "audio", "lecture-01-caching.mp3");
    // No MP3 encoder was available in this environment, so no MP3 fixture is committed;
    // eval/README.md records that this path is registered but not verified end to end here.
    Assumptions.assumeTrue(
        Files.isRegularFile(mp3), "no MP3 fixture committed in this environment");

    try (FileAudioSource source = FileAudioSource.open(mp3)) {
      assertThat(source.format()).isEqualTo(AudioSource.TARGET_FORMAT);
      assertThat(drain(source) / (double) AudioSource.SAMPLE_RATE).isGreaterThan(20);
    }
  }

  private static long drain(AudioSource source) throws IOException {
    byte[] buffer = new byte[8 * 1024];
    long frames = 0;
    int read;
    while ((read = source.read(buffer)) >= 0) {
      frames += read / AudioSource.BYTES_PER_FRAME;
    }
    return frames;
  }
}
