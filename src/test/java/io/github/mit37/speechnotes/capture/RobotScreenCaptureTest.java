package io.github.mit37.speechnotes.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RobotScreenCaptureTest {

  private static final Path DIRECTORY = Path.of("build", "tmp", "screenshots");

  @Test
  @DisplayName("with no display, capture explains itself instead of throwing a stack trace")
  void headlessCaptureExplainsItself() {
    Assumptions.assumeTrue(GraphicsEnvironment.isHeadless(), "this machine has a display");

    assertThat(RobotScreenCapture.isAvailable()).isFalse();
    assertThatExceptionOfType(ScreenCaptureException.class)
        .isThrownBy(() -> new RobotScreenCapture(DIRECTORY).capture("shot-001"))
        .withMessageContaining("headless")
        .withMessageContaining("disabled here");
  }

  @Test
  @DisplayName("with a display, a real screenshot is written as a PNG")
  void captureWritesAPngWhenADisplayExists() throws Exception {
    Assumptions.assumeTrue(!GraphicsEnvironment.isHeadless(), "no display on this machine");

    Path image = new RobotScreenCapture(DIRECTORY).capture("shot-real");

    assertThat(Files.exists(image)).isTrue();
    assertThat(Files.size(image)).isGreaterThan(0);
    assertThat(image.getFileName().toString()).endsWith(".png");
  }
}
