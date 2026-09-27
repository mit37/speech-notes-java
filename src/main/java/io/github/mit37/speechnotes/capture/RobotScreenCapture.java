package io.github.mit37.speechnotes.capture;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Screenshots through {@code java.awt.Robot}, which the PRD names for this feature.
 *
 * <p>Headless machines get a plain explanation instead of a stack trace, and the screenshots go
 * into a directory of the caller's choosing so nothing is written outside it.
 */
public final class RobotScreenCapture implements ScreenCapture {

  private final Path directory;

  public RobotScreenCapture(Path directory) {
    this.directory = directory;
  }

  /** True when this machine can take screenshots at all. */
  public static boolean isAvailable() {
    return !GraphicsEnvironment.isHeadless();
  }

  @Override
  public Path capture(String name) throws ScreenCaptureException {
    if (!isAvailable()) {
      throw new ScreenCaptureException(
          "screenshots need a display, and this environment is headless — screenshot Q&A is disabled here");
    }
    try {
      Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
      BufferedImage image = new Robot().createScreenCapture(screen);
      Files.createDirectories(directory);
      Path target = directory.resolve(name + ".png");
      if (!ImageIO.write(image, "png", target.toFile())) {
        throw new ScreenCaptureException("no PNG writer is available to save " + target);
      }
      return target;
    } catch (HeadlessException e) {
      throw new ScreenCaptureException(
          "screenshots need a display, and this environment is headless", e);
    } catch (AWTException | IOException e) {
      throw new ScreenCaptureException("screenshot failed: " + e.getMessage(), e);
    }
  }
}
