package io.github.mit37.speechnotes.ui;

import io.github.mit37.speechnotes.capture.ScreenCapture;
import io.github.mit37.speechnotes.capture.ScreenCaptureException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Takes "screenshots" that are really one-line files, or fails on demand. */
public final class FakeScreenCapture implements ScreenCapture {

  private final boolean available;
  private final Path directory;

  public FakeScreenCapture(boolean available, Path directory) {
    this.available = available;
    this.directory = directory;
  }

  @Override
  public Path capture(String name) throws ScreenCaptureException {
    if (!available) {
      throw new ScreenCaptureException(
          "screenshots need a display, and this environment is headless — screenshot Q&A is disabled here");
    }
    try {
      Files.createDirectories(directory);
      Path target = directory.resolve(name + ".png");
      Files.writeString(target, "not really a png", StandardCharsets.UTF_8);
      return target;
    } catch (IOException e) {
      throw new ScreenCaptureException("could not write " + name, e);
    }
  }
}
