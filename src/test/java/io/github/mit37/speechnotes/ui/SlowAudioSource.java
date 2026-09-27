package io.github.mit37.speechnotes.ui;

import io.github.mit37.speechnotes.audio.AudioSource;
import java.io.IOException;

/**
 * A source that hands over audio slowly, the way a microphone does.
 *
 * <p>{@code FakeAudioSource} delivers everything instantly, which is perfect for most tests but
 * makes pause and stop impossible to observe. This one takes {@code millisecondsPerChunk} between
 * chunks.
 */
final class SlowAudioSource implements AudioSource {

  private final long totalFrames;
  private final long framesPerChunk;
  private final long millisecondsPerChunk;
  private long framesRead;
  private volatile boolean closed;

  SlowAudioSource(double totalSeconds, double chunkSeconds, long millisecondsPerChunk) {
    this.totalFrames = Math.round(totalSeconds * SAMPLE_RATE);
    this.framesPerChunk = Math.max(1, Math.round(chunkSeconds * SAMPLE_RATE));
    this.millisecondsPerChunk = millisecondsPerChunk;
  }

  @Override
  public int read(byte[] buffer) throws IOException {
    if (closed) {
      return -1;
    }
    if (framesRead >= totalFrames) {
      return -1;
    }
    long frames = Math.min(framesPerChunk, totalFrames - framesRead);
    try {
      Thread.sleep(millisecondsPerChunk);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return -1;
    }
    framesRead += frames;
    int bytes = (int) Math.min(buffer.length, frames * BYTES_PER_FRAME);
    java.util.Arrays.fill(buffer, 0, bytes, (byte) 0);
    return bytes;
  }

  @Override
  public String description() {
    return "slow fake source";
  }

  @Override
  public void close() {
    closed = true;
  }
}
