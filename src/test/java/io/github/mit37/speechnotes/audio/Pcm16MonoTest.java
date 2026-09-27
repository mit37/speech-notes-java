package io.github.mit37.speechnotes.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Pcm16MonoTest {

  private static final AudioFormat MONO_8K = new AudioFormat(8000f, 16, 1, true, false);
  private static final AudioFormat MONO_44K = new AudioFormat(44100f, 16, 1, true, false);
  private static final AudioFormat STEREO_44K = new AudioFormat(44100f, 16, 2, true, false);
  private static final AudioFormat MONO_22K_BIG = new AudioFormat(22050f, 16, 1, true, true);
  private static final AudioFormat UNSIGNED_8BIT = new AudioFormat(8000f, 8, 1, false, false);

  /** Feeds the whole stream through the converter in fixed-size slices and collects the output. */
  private static byte[] convert(AudioFormat format, byte[] source, int chunkSize) {
    Pcm16Mono converter = new Pcm16Mono(format);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] dst = new byte[7919];
    for (int offset = 0; offset < source.length; offset += chunkSize) {
      int length = Math.min(chunkSize, source.length - offset);
      byte[] slice = new byte[length];
      System.arraycopy(source, offset, slice, 0, length);
      int written = converter.convert(slice, length, dst);
      out.write(dst, 0, written);
    }
    while (true) {
      int written = converter.flush(dst);
      if (written == 0) {
        break;
      }
      out.write(dst, 0, written);
    }
    return out.toByteArray();
  }

  @Test
  @DisplayName("8 kHz upsampled to 16 kHz keeps the frequency and doubles the frame count")
  void upsamples8k() {
    byte[] converted = convert(MONO_8K, PcmTestData.sine(MONO_8K, 440, 0.25), 8192);

    assertThat(PcmTestData.toSamples(converted).length).isBetween(3990, 4010);
    assertThat(PcmTestData.zeroCrossings(converted)).isBetween(214, 226);
  }

  @Test
  @DisplayName("44.1 kHz downsampled to 16 kHz keeps the frequency")
  void downsamples44k() {
    byte[] converted = convert(MONO_44K, PcmTestData.sine(MONO_44K, 440, 0.25), 8192);

    assertThat(PcmTestData.toSamples(converted).length).isBetween(3990, 4010);
    assertThat(PcmTestData.zeroCrossings(converted)).isBetween(210, 230);
  }

  @Test
  @DisplayName("a big-endian source decodes with the same amplitude")
  void decodesBigEndian() {
    byte[] converted = convert(MONO_22K_BIG, PcmTestData.constant(MONO_22K_BIG, 0.5, 100), 64);

    assertThat(PcmTestData.peak(converted)).isBetween(16000, 16500);
  }

  @Test
  @DisplayName("stereo is averaged, so a silent right channel halves the peak")
  void mixesStereoToMono() {
    byte[] converted = convert(STEREO_44K, PcmTestData.sine(STEREO_44K, 440, 0.1), 8192);

    // The left channel peaks at 0.8 full scale; averaged with silence that is 0.4.
    assertThat(PcmTestData.peak(converted)).isBetween(12800, 13400);
  }

  @Test
  @DisplayName("unsigned 8-bit silence is centred on 128 and converts to near-silence")
  void handlesUnsignedEightBit() {
    byte[] converted = convert(UNSIGNED_8BIT, PcmTestData.constant(UNSIGNED_8BIT, 0.0, 800), 8192);

    assertThat(PcmTestData.peak(converted)).isLessThan(4);
  }

  @Test
  @DisplayName("chunking is irrelevant: byte-at-a-time equals one-shot")
  void chunkingDoesNotChangeTheOutput() {
    byte[] source = PcmTestData.sine(MONO_8K, 300, 0.1);

    byte[] oneShot = convert(MONO_8K, source, source.length);
    byte[] byteByByte = convert(MONO_8K, source, 1);
    byte[] oddChunks = convert(MONO_8K, source, 7);

    assertThat(byteByByte).isEqualTo(oneShot);
    assertThat(oddChunks).isEqualTo(oneShot);
  }

  @Test
  @DisplayName("full-scale input clamps instead of wrapping around")
  void clampsFullScale() {
    byte[] converted = convert(MONO_8K, PcmTestData.constant(MONO_8K, 1.0, 400), 8192);

    // Full scale in, full scale out: nothing wraps to a large negative value.
    for (short sample : PcmTestData.toSamples(converted)) {
      assertThat(sample).isGreaterThanOrEqualTo((short) 32700);
    }
  }

  @Test
  @DisplayName("flush emits the interpolated tail of a short stream")
  void flushEmitsTheTail() {
    // 100 frames at 8 kHz is 12.5 ms, which is 200 frames at 16 kHz.
    byte[] converted = convert(MONO_8K, PcmTestData.constant(MONO_8K, 0.25, 100), 8192);

    assertThat(PcmTestData.toSamples(converted).length).isBetween(198, 202);
  }

  @Test
  void emptyInputProducesNothing() {
    Pcm16Mono converter = new Pcm16Mono(MONO_8K);
    byte[] dst = new byte[64];

    assertThat(converter.convert(new byte[0], 0, dst)).isZero();
    assertThat(converter.flush(dst)).isZero();
    assertThat(converter.isDrained()).isTrue();
  }

  @Test
  @DisplayName("non-PCM encodings are rejected with a clear message")
  void rejectsNonPcmFormats() {
    AudioFormat ulaw = new AudioFormat(AudioFormat.Encoding.ULAW, 8000f, 8, 1, 1, 8000f, false);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new Pcm16Mono(ulaw))
        .withMessageContaining("PCM");
  }

  @Test
  @DisplayName("a format without a frame size is rejected")
  void rejectsUnknownFrameSize() {
    AudioFormat unknown =
        new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 8000f, 16, 1, -1, 8000f, false);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new Pcm16Mono(unknown))
        .withMessageContaining("frame size");
  }
}
