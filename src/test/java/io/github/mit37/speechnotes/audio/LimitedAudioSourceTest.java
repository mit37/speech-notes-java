package io.github.mit37.speechnotes.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimitedAudioSourceTest {

  private static byte[] drain(AudioSource source) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int read;
    while ((read = source.read(buffer)) >= 0) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  @Test
  @DisplayName("a one-second limit takes exactly one second of 16 kHz audio")
  void limitsToTheRequestedDuration() throws IOException {
    AudioSource unlimited = FakeAudioSource.tone(440, 3.0);

    try (LimitedAudioSource limited = new LimitedAudioSource(unlimited, 1.0)) {
      byte[] audio = drain(limited);

      assertThat(audio.length / AudioSource.BYTES_PER_FRAME).isEqualTo(16_000);
      assertThat(limited.framesRead()).isEqualTo(16_000);
    }
  }

  @Test
  @DisplayName("a source shorter than the limit is passed through whole")
  void shortSourceIsNotPadded() throws IOException {
    try (LimitedAudioSource limited = new LimitedAudioSource(FakeAudioSource.tone(440, 0.5), 5.0)) {
      assertThat(drain(limited)).hasSize(16_000);
    }
  }

  @Test
  @DisplayName("the audio that comes out is the audio that went in, in order")
  void isTransparent() throws IOException {
    byte[] original = new byte[16_000];
    for (int i = 0; i < original.length; i++) {
      original[i] = (byte) (i % 251);
    }
    try (LimitedAudioSource limited =
        new LimitedAudioSource(new FakeAudioSource(original, 1024, "fixed"), 1.0)) {
      assertThat(drain(limited)).isEqualTo(original);
    }
  }

  @Test
  @DisplayName("a delegate that hands over short reads is still fully consumed")
  void handlesShortReads() throws IOException {
    byte[] original = new byte[8_000];
    try (LimitedAudioSource limited =
        new LimitedAudioSource(new FakeAudioSource(original, 777, "short reads"), 0.25)) {
      assertThat(drain(limited)).hasSize(8_000);
    }
  }

  @Test
  void descriptionSaysWhatItLimits() throws IOException {
    try (LimitedAudioSource limited = new LimitedAudioSource(FakeAudioSource.tone(440, 2.0), 3.0)) {
      assertThat(limited.description()).contains("first 3.0s");
    }
  }

  @Test
  @DisplayName("a device that opens but stays silent ends the recording instead of hanging")
  void stopsWhenTheDeviceGoesQuiet() throws IOException {
    SilentAudioSource silent = new SilentAudioSource();

    try (LimitedAudioSource limited = new LimitedAudioSource(silent, 30.0, 0.05)) {
      byte[] buffer = new byte[1024];
      long deadline = System.nanoTime() + 5_000_000_000L;
      int read;
      do {
        read = limited.read(buffer);
      } while (read >= 0 && System.nanoTime() < deadline);

      assertThat(read).isEqualTo(-1);
      assertThat(limited.endedBecauseOfStall()).isTrue();
      assertThat(limited.framesRead()).isZero();
    }
  }

  @Test
  @DisplayName("an empty read is passed through while the device is still alive")
  void emptyReadsAreNotTheEndOfTheStream() throws IOException {
    byte[] data = new byte[1024];
    try (LimitedAudioSource limited =
        new LimitedAudioSource(new SilentAudioSource(2, data), 1.0, 5.0)) {
      byte[] buffer = new byte[512];

      assertThat(limited.read(buffer)).isZero();
      assertThat(limited.read(buffer)).isZero();
      assertThat(limited.read(buffer)).isPositive();
      assertThat(limited.endedBecauseOfStall()).isFalse();
    }
  }

  @Test
  void rejectsNonPositiveLimits() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new LimitedAudioSource(FakeAudioSource.tone(440, 1.0), 0))
        .withMessageContaining("positive");
  }
}
