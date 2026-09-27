package io.github.mit37.speechnotes.audio;

import javax.sound.sampled.AudioFormat;

/**
 * Test helper: builds raw PCM byte arrays in memory, so audio tests need no files and no device.
 */
final class PcmTestData {

  private PcmTestData() {}

  /** A sine wave in channel 0 and silence in every other channel, interleaved per frame. */
  static byte[] sine(AudioFormat format, double frequency, double seconds) {
    int frames = (int) Math.round(format.getSampleRate() * seconds);
    int channels = format.getChannels();
    int bytesPerSample = format.getFrameSize() / channels;
    byte[] out = new byte[frames * format.getFrameSize()];
    int offset = 0;
    for (int frame = 0; frame < frames; frame++) {
      for (int channel = 0; channel < channels; channel++) {
        double t = frame / format.getSampleRate();
        double value = channel == 0 ? Math.sin(2 * Math.PI * frequency * t) * 0.8 : 0.0;
        writeSample(out, offset, bytesPerSample, format.isBigEndian(), isSigned(format), value);
        offset += bytesPerSample;
      }
    }
    return out;
  }

  /** Every frame carries the same value. */
  static byte[] constant(AudioFormat format, double value, int frames) {
    int channels = format.getChannels();
    int bytesPerSample = format.getFrameSize() / channels;
    byte[] out = new byte[frames * format.getFrameSize()];
    int offset = 0;
    for (int frame = 0; frame < frames; frame++) {
      for (int channel = 0; channel < channels; channel++) {
        writeSample(out, offset, bytesPerSample, format.isBigEndian(), isSigned(format), value);
        offset += bytesPerSample;
      }
    }
    return out;
  }

  static boolean isSigned(AudioFormat format) {
    return AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding());
  }

  /** Signed 16-bit little-endian samples, the format every {@link AudioSource} presents. */
  static short[] toSamples(byte[] pcm16Mono) {
    short[] samples = new short[pcm16Mono.length / 2];
    for (int i = 0; i < samples.length; i++) {
      samples[i] = (short) ((pcm16Mono[i * 2] & 0xFF) | (pcm16Mono[i * 2 + 1] << 8));
    }
    return samples;
  }

  /** Number of sign changes, a cheap and robust proxy for the dominant frequency. */
  static int zeroCrossings(byte[] pcm16Mono) {
    short[] samples = toSamples(pcm16Mono);
    int crossings = 0;
    for (int i = 1; i < samples.length; i++) {
      if ((samples[i - 1] < 0 && samples[i] >= 0) || (samples[i - 1] >= 0 && samples[i] < 0)) {
        crossings++;
      }
    }
    return crossings;
  }

  static int peak(byte[] pcm16Mono) {
    int peak = 0;
    for (short sample : toSamples(pcm16Mono)) {
      peak = Math.max(peak, Math.abs(sample));
    }
    return peak;
  }

  private static void writeSample(
      byte[] out, int offset, int bytesPerSample, boolean bigEndian, boolean signed, double value) {
    double clamped = Math.max(-1.0, Math.min(1.0, value));
    if (bytesPerSample == 1) {
      long encoded = signed ? Math.round(clamped * 127) : Math.round(clamped * 127) + 128;
      out[offset] = (byte) (encoded & 0xFF);
      return;
    }
    long full = (long) (clamped * ((1L << (bytesPerSample * 8 - 1)) - 1));
    if (bigEndian) {
      for (int i = 0; i < bytesPerSample; i++) {
        out[offset + i] = (byte) ((full >> (8 * (bytesPerSample - 1 - i))) & 0xFF);
      }
    } else {
      for (int i = 0; i < bytesPerSample; i++) {
        out[offset + i] = (byte) ((full >> (8 * i)) & 0xFF);
      }
    }
  }
}
