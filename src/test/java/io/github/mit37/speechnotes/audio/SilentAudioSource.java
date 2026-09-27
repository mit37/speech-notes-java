package io.github.mit37.speechnotes.audio;

/**
 * A capture device that is open but produces nothing.
 *
 * <p>Real machines have these: virtual loopback devices with nothing routed to them report
 * themselves as available and then never deliver a byte. The code has to survive that without
 * hanging, which is what this fake exists to prove.
 */
final class SilentAudioSource implements AudioSource {

  private final int zeroReadsBeforeData;
  private final byte[] data;
  private int zeroReads;
  private int offset;

  /** Never delivers anything. */
  SilentAudioSource() {
    this(Integer.MAX_VALUE, new byte[0]);
  }

  /** Returns {@code 0} a few times (as a real device does between buffers), then delivers data. */
  SilentAudioSource(int zeroReadsBeforeData, byte[] data) {
    this.zeroReadsBeforeData = zeroReadsBeforeData;
    this.data = data.clone();
  }

  @Override
  public int read(byte[] buffer) {
    if (zeroReads < zeroReadsBeforeData) {
      zeroReads++;
      return 0;
    }
    if (offset >= data.length) {
      return -1;
    }
    int count = Math.min(buffer.length, data.length - offset);
    System.arraycopy(data, offset, buffer, 0, count);
    offset += count;
    return count;
  }

  @Override
  public String description() {
    return "silent device";
  }

  @Override
  public void close() {}
}
