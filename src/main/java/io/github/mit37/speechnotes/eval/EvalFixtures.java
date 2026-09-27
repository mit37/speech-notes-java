package io.github.mit37.speechnotes.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Finds the audio files in a directory and pairs each one with its script and rubric. */
public final class EvalFixtures {

  /**
   * Extensions {@code FileAudioSource} can decode, so nothing advertised here is silently
   * unsupported.
   */
  private static final List<String> AUDIO_EXTENSIONS = List.of(".wav", ".mp3");

  private EvalFixtures() {}

  public static List<EvalFixture> load(Path audioDir, Path fixturesDir) throws IOException {
    if (!Files.isDirectory(audioDir)) {
      throw new IOException("no audio directory: " + audioDir);
    }
    List<Path> audio;
    try (var files = Files.list(audioDir)) {
      audio = files.filter(Files::isRegularFile).filter(EvalFixtures::isAudio).sorted().toList();
    }
    if (audio.isEmpty()) {
      throw new IOException("no .wav or .mp3 fixtures in " + audioDir);
    }
    List<EvalFixture> fixtures = new ArrayList<>();
    for (Path path : audio) {
      fixtures.add(EvalFixture.load(path, fixturesDir));
    }
    return List.copyOf(fixtures);
  }

  private static boolean isAudio(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return AUDIO_EXTENSIONS.stream().anyMatch(name::endsWith);
  }
}
