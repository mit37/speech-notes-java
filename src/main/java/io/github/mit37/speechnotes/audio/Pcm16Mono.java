package io.github.mit37.speechnotes.audio;

import javax.sound.sampled.AudioFormat;

/**
 * Streaming converter from any PCM {@link AudioFormat} to {@link AudioSource#TARGET_FORMAT}.
 *
 * <p>Chunk-safe: the caller may feed arbitrary slices, including slices that split a source frame,
 * and the output is byte-for-byte identical to converting the whole stream in one call (there is a
 * test for exactly that). Channel mixing is an average, resampling is linear interpolation with the
 * last sample held at the end of the stream.
 */
public final class Pcm16Mono {

  private static final int MIN_COMPACT_SIZE = 4096;

  private final AudioFormat sourceFormat;
  private final int channels;
  private final int bytesPerSample;
  private final boolean sourceSigned;
  private final boolean bigEndian;
  private final double step;

  private float[] samples = new float[4096];
  private int sampleCount;
  private double cursor;
  private byte[] partialFrame = new byte[0];
  private boolean endOfInput;
  private boolean drained;
  private long outputFrames;

  public Pcm16Mono(AudioFormat sourceFormat) {
    this.sourceFormat = sourceFormat;
    if (!AudioFormat.Encoding.PCM_SIGNED.equals(sourceFormat.getEncoding())
        && !AudioFormat.Encoding.PCM_UNSIGNED.equals(sourceFormat.getEncoding())) {
      throw new IllegalArgumentException(
          "only PCM audio can be converted, got " + sourceFormat.getEncoding());
    }
    int frameSize = sourceFormat.getFrameSize();
    if (frameSize <= 0 || sourceFormat.getChannels() <= 0 || sourceFormat.getSampleRate() <= 0) {
      throw new IllegalArgumentException(
          "audio format is missing its frame size, channels or sample rate");
    }
    this.channels = sourceFormat.getChannels();
    this.bytesPerSample = frameSize / channels;
    if (bytesPerSample != 1 && bytesPerSample != 2 && bytesPerSample != 3 && bytesPerSample != 4) {
      throw new IllegalArgumentException("unsupported sample size: " + bytesPerSample + " bytes");
    }
    this.sourceSigned = AudioFormat.Encoding.PCM_SIGNED.equals(sourceFormat.getEncoding());
    this.bigEndian = sourceFormat.isBigEndian();
    this.step = sourceFormat.getSampleRate() / (double) AudioSource.SAMPLE_RATE;
  }

  /**
   * Converts as much of {@code src} as the output buffer has room for.
   *
   * @return the number of bytes written to {@code dst}; anything past that must be ignored
   */
  public int convert(byte[] src, int length, byte[] dst) {
    if (src.length < length) {
      throw new IllegalArgumentException("length " + length + " exceeds the source buffer");
    }
    int frameSize = sourceFormat.getFrameSize();
    int offset = 0;

    if (partialFrame.length > 0) {
      int missing = frameSize - partialFrame.length;
      int take = Math.min(missing, length);
      byte[] frame = new byte[frameSize];
      System.arraycopy(partialFrame, 0, frame, 0, partialFrame.length);
      System.arraycopy(src, 0, frame, partialFrame.length, take);
      offset += take;
      if (partialFrame.length + take == frameSize) {
        append(decodeMono(frame, 0));
        partialFrame = new byte[0];
      } else {
        partialFrame = frame;
        return produce(dst);
      }
    }

    while (length - offset >= frameSize) {
      append(decodeMono(src, offset));
      offset += frameSize;
    }

    int leftover = length - offset;
    if (leftover > 0) {
      byte[] remainder = new byte[leftover];
      System.arraycopy(src, offset, remainder, 0, leftover);
      partialFrame = remainder;
    }

    return produce(dst);
  }

  /** Marks the input as finished and writes whatever interpolation is still owed. */
  public int flush(byte[] dst) {
    if (dst.length < AudioSource.BYTES_PER_FRAME) {
      throw new IllegalArgumentException("output buffer must hold at least one 16-bit sample");
    }
    endOfInput = true;
    return produce(dst);
  }

  /** Total output frames produced so far, at {@link AudioSource#SAMPLE_RATE}. */
  public long outputFrames() {
    return outputFrames;
  }

  /** True once {@link #flush} has handed over everything the converter can produce. */
  public boolean isDrained() {
    return drained;
  }

  private int produce(byte[] dst) {
    int written = 0;
    drained = false;
    if (endOfInput) {
      drained = cursor >= sampleCount;
    }
    if (drained) {
      return 0;
    }
    while (written + AudioSource.BYTES_PER_FRAME <= dst.length) {
      int lower = (int) Math.floor(cursor);
      int upper = lower + 1;
      boolean haveUpper = upper < sampleCount;
      if (!haveUpper && (!endOfInput || lower >= sampleCount)) {
        break;
      }
      float low = samples[lower];
      float high = haveUpper ? samples[upper] : low;
      float interpolated = (float) (low + (high - low) * (cursor - lower));
      short encoded = toPcm16(interpolated);
      dst[written++] = (byte) (encoded & 0xFF);
      dst[written++] = (byte) ((encoded >> 8) & 0xFF);
      cursor += step;
      outputFrames++;
    }
    if (endOfInput && cursor >= sampleCount) {
      drained = true;
    }
    compact();
    return written;
  }

  private void append(float sample) {
    if (sampleCount == samples.length) {
      float[] grown = new float[samples.length * 2];
      System.arraycopy(samples, 0, grown, 0, sampleCount);
      samples = grown;
    }
    samples[sampleCount++] = sample;
  }

  private void compact() {
    int keepFrom = (int) Math.floor(cursor);
    if (keepFrom < MIN_COMPACT_SIZE) {
      return;
    }
    int remaining = sampleCount - keepFrom;
    if (remaining > 0) {
      System.arraycopy(samples, keepFrom, samples, 0, remaining);
    }
    sampleCount = Math.max(remaining, 0);
    cursor -= keepFrom;
  }

  private float decodeMono(byte[] buffer, int offset) {
    double sum = 0;
    for (int channel = 0; channel < channels; channel++) {
      sum += decodeSample(buffer, offset + channel * bytesPerSample);
    }
    return (float) (sum / channels);
  }

  private float decodeSample(byte[] buffer, int offset) {
    long raw = 0;
    if (bigEndian) {
      for (int i = 0; i < bytesPerSample; i++) {
        raw = (raw << 8) | (buffer[offset + i] & 0xFFL);
      }
    } else {
      for (int i = bytesPerSample - 1; i >= 0; i--) {
        raw = (raw << 8) | (buffer[offset + i] & 0xFFL);
      }
    }
    int bits = bytesPerSample * 8;
    long signBit = 1L << (bits - 1);
    if (!sourceSigned) {
      // Unsigned PCM is centred on half scale: 128 for 8-bit, 32768 for 16-bit.
      return (float) ((raw - signBit) / (double) signBit);
    }
    long value = (raw & signBit) != 0 ? raw - (1L << bits) : raw;
    return (float) (value / (double) signBit);
  }

  private static short toPcm16(float value) {
    float clamped = Math.max(-1f, Math.min(1f, value));
    return (short) Math.round(clamped * 32767f);
  }
}
