package io.github.mit37.speechnotes.audio;

import java.io.IOException;
import java.nio.file.Path;

/**
 * An {@link AudioSource} that replays bytes from memory.
 *
 * <p>This is how microphone and UI behaviour gets tested on machines with no microphone and no
 * display (docs/PLAN.md, cloud-instance constraints).
 */
public final class FakeAudioSource implements AudioSource {

  private final byte[] data;
  private final int chunkBytes;
  private final String description;
  private int offset;
  private boolean closed;

  public FakeAudioSource(byte[] pcm16Mono, int chunkBytes, String description) {
    this.data = pcm16Mono.clone();
    this.chunkBytes = Math.max(2, chunkBytes);
    this.description = description;
  }

  /** Replays a tone, sized as if it came from a device. */
  public static FakeAudioSource tone(double frequency, double seconds) {
    byte[] data = PcmTestData.sine(AudioSource.TARGET_FORMAT, frequency, seconds);
    return new FakeAudioSource(data, 4096, "fake tone " + (int) frequency + " Hz");
  }

  /** Replays a real file's samples without holding the file open. */
  public static FakeAudioSource fromFile(Path audio) throws IOException {
    try (FileAudioSource source = FileAudioSource.open(audio)) {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[16 * 1024];
      int read;
      while ((read = source.read(buffer)) >= 0) {
        out.write(buffer, 0, read);
      }
      return new FakeAudioSource(out.toByteArray(), 8192, "fake replay of " + audio.getFileName());
    }
  }

  @Override
  public int read(byte[] buffer) throws IOException {
    if (closed) {
      throw new IOException("audio source is closed: " + description);
    }
    if (offset >= data.length) {
      return -1;
    }
    int count = Math.min(Math.min(chunkBytes, buffer.length), data.length - offset);
    System.arraycopy(data, offset, buffer, 0, count);
    offset += count;
    return count;
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public double durationSeconds() {
    return (data.length / BYTES_PER_FRAME) / (double) SAMPLE_RATE;
  }

  @Override
  public void close() {
    closed = true;
  }
}
