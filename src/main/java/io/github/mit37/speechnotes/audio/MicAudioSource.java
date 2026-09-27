package io.github.mit37.speechnotes.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * The microphone, in {@link AudioSource#TARGET_FORMAT} (PRD G1, live mode).
 *
 * <p>The only device-specific class in the audio layer, and the reason {@link AudioSource} exists:
 * everything downstream of it is tested on a machine with no microphone at all (docs/PLAN.md).
 */
public final class MicAudioSource implements AudioSource {

  private final TargetDataLine line;
  private final String deviceName;
  private boolean closed;

  private MicAudioSource(TargetDataLine line, String deviceName) {
    this.line = line;
    this.deviceName = deviceName;
  }

  /**
   * Opens the default capture device at 16 kHz mono.
   *
   * @throws AudioSourceException when no device can supply that format — the message says so
   *     plainly, because "no microphone" is a normal thing to hit in CI
   */
  public static MicAudioSource open() throws AudioSourceException {
    AudioFormat format = AudioSource.TARGET_FORMAT;
    DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
    if (!AudioSystem.isLineSupported(info)) {
      throw new AudioSourceException(
          "no microphone supports " + describe(format) + " — check the input device or use --file");
    }
    try {
      TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
      line.open(format);
      line.start();
      return new MicAudioSource(line, line.getLineInfo().toString());
    } catch (LineUnavailableException | IllegalArgumentException e) {
      throw new AudioSourceException(
          "cannot open the microphone: "
              + e.getMessage()
              + " — check the input device or use --file",
          e);
    }
  }

  /** True when a capture device for {@link AudioSource#TARGET_FORMAT} exists on this machine. */
  public static boolean isAvailable() {
    return AudioSystem.isLineSupported(
        new DataLine.Info(TargetDataLine.class, AudioSource.TARGET_FORMAT));
  }

  /**
   * Reads only audio that is already available.
   *
   * <p>Deliberately not {@code line.read(buffer, 0, buffer.length)}: that blocks until the device
   * has produced the whole buffer, and a device that is open but silent (virtual loopback devices
   * do this) never does. Here a quiet device returns {@code 0} after a short pause, which the
   * caller treats as "nothing yet" rather than "end of stream".
   */
  @Override
  public int read(byte[] buffer) {
    if (closed) {
      return -1;
    }
    int available = line.available();
    if (available < AudioSource.BYTES_PER_FRAME) {
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return -1;
      }
      return 0;
    }
    return line.read(buffer, 0, Math.min(buffer.length, available));
  }

  /** Seconds of audio captured so far, from the device's own frame counter. */
  public double capturedSeconds() {
    return line.getLongFramePosition() / (double) AudioSource.SAMPLE_RATE;
  }

  @Override
  public String description() {
    return "microphone (" + deviceName + ")";
  }

  @Override
  public void close() {
    closed = true;
    line.stop();
    line.close();
  }

  private static String describe(AudioFormat format) {
    return String.format(
        java.util.Locale.ROOT,
        "%.0f Hz, %d channel(s), %d-bit",
        format.getSampleRate(),
        format.getChannels(),
        format.getSampleSizeInBits());
  }
}
