package io.github.mit37.speechnotes.audio;

import java.io.IOException;
import javax.sound.sampled.AudioInputStream;

/**
 * Presents any PCM {@link AudioInputStream} as an {@link AudioSource} in {@link
 * AudioSource#TARGET_FORMAT}, using {@link Pcm16Mono} to mix down and resample.
 */
public final class Pcm16Source implements AudioSource {

  private static final int RAW_BUFFER_BYTES = 16 * 1024;

  private final AudioInputStream rawStream;
  private final Pcm16Mono converter;
  private final String description;
  private final double durationSeconds;
  private final byte[] rawBuffer = new byte[RAW_BUFFER_BYTES];
  private final byte[] pending = new byte[RAW_BUFFER_BYTES];
  private int pendingStart;
  private int pendingEnd;
  private boolean sourceDone;
  private boolean flushed;
  private long framesRead;
  private boolean closed;

  /**
   * @param rawStream the decoded stream, in any PCM format
   * @param description what to call this source in logs and notes
   * @param durationSeconds known duration, or {@code -1}
   */
  public Pcm16Source(AudioInputStream rawStream, String description, double durationSeconds) {
    this.rawStream = rawStream;
    this.converter = new Pcm16Mono(rawStream.getFormat());
    this.description = description;
    this.durationSeconds = durationSeconds;
  }

  @Override
  public int read(byte[] buffer) throws IOException {
    if (closed) {
      throw new IOException("audio source is closed: " + description);
    }
    if (pendingStart >= pendingEnd) {
      refill();
      if (pendingStart >= pendingEnd) {
        return -1;
      }
    }
    int count = Math.min(buffer.length, pendingEnd - pendingStart);
    System.arraycopy(pending, pendingStart, buffer, 0, count);
    pendingStart += count;
    framesRead += count / BYTES_PER_FRAME;
    return count;
  }

  /** Frames of 16 kHz audio handed to the caller so far. */
  public long framesRead() {
    return framesRead;
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public double durationSeconds() {
    return durationSeconds;
  }

  @Override
  public void close() throws IOException {
    closed = true;
    rawStream.close();
  }

  private void refill() throws IOException {
    pendingStart = 0;
    pendingEnd = 0;
    while (pendingEnd == 0) {
      if (!sourceDone) {
        int read = rawStream.read(rawBuffer, 0, rawBuffer.length);
        if (read < 0) {
          sourceDone = true;
          continue;
        }
        pendingEnd = converter.convert(rawBuffer, read, pending);
      } else if (!flushed) {
        flushed = true;
        pendingEnd = converter.flush(pending);
      } else {
        return;
      }
    }
  }
}
