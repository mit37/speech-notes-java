package io.github.mit37.speechnotes.audio;

import java.io.IOException;
import javax.sound.sampled.AudioFormat;

/**
 * A stream of PCM audio in the one format Vosk accepts: 16 kHz, mono, signed 16-bit, little-endian.
 *
 * <p>Implementations are either a file ({@link FileAudioSource}) or the microphone ({@link
 * MicAudioSource}); tests use in-memory fakes. Everything downstream of this interface is
 * device-free, which is what makes the pipeline testable without a microphone (docs/PLAN.md).
 */
public interface AudioSource extends AutoCloseable {

  /** The format every implementation must present. */
  AudioFormat TARGET_FORMAT = new AudioFormat(16_000f, 16, 1, true, false);

  /** Bytes per frame in {@link #TARGET_FORMAT}. */
  int BYTES_PER_FRAME = 2;

  /** The sample rate of {@link #TARGET_FORMAT}. */
  int SAMPLE_RATE = (int) TARGET_FORMAT.getSampleRate();

  /** Always {@link #TARGET_FORMAT} in this codebase; kept explicit so callers can assert it. */
  default AudioFormat format() {
    return TARGET_FORMAT;
  }

  /**
   * Reads up to {@code buffer.length} bytes of audio.
   *
   * @return the number of bytes read, or {@code -1} at the end of the stream
   */
  int read(byte[] buffer) throws IOException;

  /**
   * Reads at most {@code length} bytes into {@code buffer} starting at {@code offset}.
   *
   * <p>The default implementation reads into a temporary buffer and copies, which keeps the
   * interface at one method; sources that care about allocation can override it.
   *
   * @return the number of bytes read, or {@code -1} at the end of the stream
   */
  default int read(byte[] buffer, int offset, int length) throws IOException {
    if (offset == 0 && length == buffer.length) {
      return read(buffer);
    }
    byte[] temporary = new byte[length];
    int read = read(temporary);
    if (read > 0) {
      System.arraycopy(temporary, 0, buffer, offset, read);
    }
    return read;
  }

  /** Human-readable origin, used in logs and in the notes header. */
  String description();

  /** Duration in seconds, or {@code -1} when the source cannot know it (a microphone, say). */
  default double durationSeconds() {
    return -1;
  }

  @Override
  void close() throws IOException;
}
