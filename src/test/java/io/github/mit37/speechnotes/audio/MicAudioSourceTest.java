package io.github.mit37.speechnotes.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The microphone cannot be tested without one, so these tests pin down both possible outcomes
 * honestly: either a device exists and the source behaves like an {@link AudioSource}, or opening
 * it fails with a message a user can act on (docs/PLAN.md, cloud-instance constraints).
 */
class MicAudioSourceTest {

  @Test
  @DisplayName("availability can be asked for without throwing either way")
  void availabilityIsAnswerable() {
    assertThat(MicAudioSource.isAvailable()).isEqualTo(MicAudioSource.isAvailable());
  }

  @Test
  @DisplayName("opening either works or fails with advice, never with a stack trace")
  void openEitherWorksOrExplains() throws IOException {
    if (!MicAudioSource.isAvailable()) {
      assertThatExceptionOfType(AudioSourceException.class)
          .isThrownBy(MicAudioSource::open)
          .withMessageContaining("--file");
      return;
    }
    try (MicAudioSource source = MicAudioSource.open()) {
      assertThat(source.description()).contains("microphone");
      byte[] buffer = new byte[2048];
      // A live device is read for a moment: it either delivers audio or reports "nothing yet" (0).
      // Reads must never throw or block, so anything other than a negative count is a pass.
      long deadline = System.nanoTime() + 3_000_000_000L;
      int read = 0;
      while (read == 0 && System.nanoTime() < deadline) {
        read = source.read(buffer);
      }
      assertThat(read).isGreaterThanOrEqualTo(0);
      assertThat(source.capturedSeconds()).isGreaterThanOrEqualTo(0);
    }
  }

  @Test
  @DisplayName("a closed microphone reports end of stream instead of throwing")
  void closedMicEndsTheStream() throws IOException {
    if (!MicAudioSource.isAvailable()) {
      return;
    }
    MicAudioSource source = MicAudioSource.open();
    byte[] buffer = new byte[1024];
    source.read(buffer);
    source.close();

    assertThat(source.read(buffer)).isEqualTo(-1);
  }
}
