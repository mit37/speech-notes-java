package io.github.mit37.speechnotes.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PausableAudioSourceTest {

  @Test
  @DisplayName("while paused nothing is handed over, but the stream is still alive")
  void pauseDeliversNothingButKeepsTheStream() throws IOException {
    try (PausableAudioSource source = new PausableAudioSource(FakeAudioSource.tone(440, 1))) {
      byte[] buffer = new byte[1024];

      assertThat(source.read(buffer)).isPositive();
      source.pause();
      assertThat(source.isPaused()).isTrue();
      assertThat(source.read(buffer)).isZero();

      source.resume();
      assertThat(source.isPaused()).isFalse();
      assertThat(source.read(buffer)).isPositive();
    }
  }

  @Test
  @DisplayName("stop ends the stream cleanly instead of throwing at the reader")
  void stopEndsTheStream() throws IOException {
    try (PausableAudioSource source = new PausableAudioSource(FakeAudioSource.tone(440, 5))) {
      byte[] buffer = new byte[1024];
      assertThat(source.read(buffer)).isPositive();

      source.stop();

      assertThat(source.isStopped()).isTrue();
      assertThat(source.read(buffer)).isEqualTo(-1);
    }
  }

  @Test
  @DisplayName("stopping while paused still ends the stream")
  void stopWinsOverPause() throws IOException {
    try (PausableAudioSource source = new PausableAudioSource(FakeAudioSource.tone(440, 5))) {
      source.pause();
      source.stop();

      assertThat(source.read(new byte[512])).isEqualTo(-1);
      assertThat(source.isPaused()).isFalse();
    }
  }

  @Test
  void describesAndMeasuresLikeItsDelegate() throws IOException {
    try (PausableAudioSource source = new PausableAudioSource(FakeAudioSource.tone(440, 2))) {
      assertThat(source.description()).contains("fake tone");
      assertThat(source.durationSeconds()).isEqualTo(2.0);
      assertThat(source.format()).isEqualTo(AudioSource.TARGET_FORMAT);
    }
  }
}
