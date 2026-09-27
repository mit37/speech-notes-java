package io.github.mit37.speechnotes.audio;

import java.io.IOException;

/**
 * Stops a live source after a fixed number of seconds, which is what {@code --mic --seconds 30}
 * means.
 *
 * <p>It also stops when the device goes quiet for {@code stallSeconds}: a capture line can be open
 * and simply never deliver audio (virtual loopback devices with nothing routed to them), and an
 * open-ended wait is worse than an early stop that says why. It never asks the device for more
 * audio than the caller allowed, so the microphone is closed promptly instead of being left
 * running.
 */
public final class LimitedAudioSource implements AudioSource {

  /** Seconds of silence after which a bounded recording gives up on the device. */
  public static final double DEFAULT_STALL_SECONDS = 2.0;

  private static final int SCRATCH_BYTES = 16 * 1024;

  private final AudioSource delegate;
  private final long maxFrames;
  private final long stallNanos;
  private final byte[] scratch = new byte[SCRATCH_BYTES];
  private long framesRead;
  private long lastProgressNanos = System.nanoTime();
  private boolean stalled;

  public LimitedAudioSource(AudioSource delegate, double maxSeconds) {
    this(delegate, maxSeconds, DEFAULT_STALL_SECONDS);
  }

  public LimitedAudioSource(AudioSource delegate, double maxSeconds, double stallSeconds) {
    if (maxSeconds <= 0) {
      throw new IllegalArgumentException("maxSeconds must be positive, got " + maxSeconds);
    }
    if (stallSeconds < 0) {
      throw new IllegalArgumentException("stallSeconds cannot be negative, got " + stallSeconds);
    }
    this.delegate = delegate;
    this.maxFrames = Math.round(maxSeconds * SAMPLE_RATE);
    this.stallNanos = Math.round(stallSeconds * 1_000_000_000L);
  }

  @Override
  public int read(byte[] buffer) throws IOException {
    long remainingFrames = maxFrames - framesRead;
    if (remainingFrames <= 0) {
      return -1;
    }
    int remainingBytes = (int) Math.min(Integer.MAX_VALUE, remainingFrames * BYTES_PER_FRAME);
    int wanted = Math.min(Math.min(buffer.length, scratch.length), remainingBytes);
    int read = delegate.read(scratch, 0, wanted);
    if (read < 0) {
      return -1;
    }
    if (read == 0) {
      if (stallNanos > 0 && System.nanoTime() - lastProgressNanos > stallNanos) {
        stalled = true;
        return -1;
      }
      return 0;
    }
    System.arraycopy(scratch, 0, buffer, 0, read);
    framesRead += read / BYTES_PER_FRAME;
    lastProgressNanos = System.nanoTime();
    return read;
  }

  /** Frames handed over so far, always at most the requested duration. */
  public long framesRead() {
    return framesRead;
  }

  /** True when the recording ended because the device stopped delivering audio. */
  public boolean endedBecauseOfStall() {
    return stalled;
  }

  @Override
  public String description() {
    return delegate.description() + ", first " + (maxFrames / (double) SAMPLE_RATE) + "s";
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }
}
