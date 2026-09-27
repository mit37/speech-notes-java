package io.github.mit37.speechnotes.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;

/**
 * An {@link AudioSource} backed by a file.
 *
 * <p>WAV is decoded by the JDK itself; MP3 comes from the {@code mp3spi} SPI that is a declared
 * dependency, which is what "a bundled decoder" means in the PRD. Either way the samples are
 * converted to {@link AudioSource#TARGET_FORMAT} on the way out.
 */
public final class FileAudioSource implements AudioSource {

  private final Path file;
  private final Pcm16Source delegate;
  private final double durationSeconds;

  private FileAudioSource(Path file, Pcm16Source delegate, double durationSeconds) {
    this.file = file;
    this.delegate = delegate;
    this.durationSeconds = durationSeconds;
  }

  /**
   * Opens {@code file} for reading.
   *
   * @throws AudioSourceException if the file is missing or cannot be decoded
   */
  public static FileAudioSource open(Path file) throws AudioSourceException {
    if (!Files.isRegularFile(file)) {
      throw new AudioSourceException("audio file not found: " + file.toAbsolutePath());
    }
    AudioInputStream stream;
    try {
      stream = AudioSystem.getAudioInputStream(file.toFile());
    } catch (UnsupportedAudioFileException e) {
      throw new AudioSourceException(
          "cannot decode "
              + file.getFileName()
              + " — WAV and MP3 are supported ("
              + e.getMessage()
              + ")",
          e);
    } catch (IOException e) {
      throw new AudioSourceException(
          "cannot read " + file.toAbsolutePath() + ": " + e.getMessage(), e);
    }
    double duration = durationOf(stream);
    String type = describeType(file);
    String description = file.getFileName() + " (" + type + ")";
    return new FileAudioSource(file, new Pcm16Source(stream, description, duration), duration);
  }

  /**
   * True when the bundled mp3spi decoder is registered as a {@code javax.sound} reader.
   *
   * <p>This is checked through the service loader rather than {@code
   * AudioSystem.getAudioFileTypes()} because mp3spi 1.9.5.x never overrides {@code
   * getAudioFileTypes()} — it decodes MP3 through {@code getAudioInputStream} without advertising
   * the type, which is why the type list alone cannot answer this question.
   */
  public static boolean isMp3DecoderRegistered() {
    for (ServiceLoader.Provider<AudioFileReader> provider :
        ServiceLoader.load(AudioFileReader.class).stream().toList()) {
      if (provider.type().getName().contains("Mpeg")) {
        return true;
      }
    }
    return false;
  }

  private static double durationOf(AudioInputStream stream) {
    long frames = stream.getFrameLength();
    float rate = stream.getFormat().getFrameRate();
    if (frames == AudioSystem.NOT_SPECIFIED || rate <= 0) {
      return -1;
    }
    return frames / (double) rate;
  }

  private static String describeType(Path file) {
    String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "unknown format" : name.substring(dot + 1);
  }

  /** The file this source reads. */
  public Path file() {
    return file;
  }

  @Override
  public int read(byte[] buffer) throws IOException {
    return delegate.read(buffer);
  }

  @Override
  public String description() {
    return delegate.description();
  }

  @Override
  public double durationSeconds() {
    return durationSeconds;
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }
}
