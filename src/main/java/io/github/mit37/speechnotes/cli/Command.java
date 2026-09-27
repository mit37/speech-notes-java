package io.github.mit37.speechnotes.cli;

import java.nio.file.Path;

/**
 * A parsed command line. Sealed so that every mode has to be handled explicitly (PRD §4 uses Java
 * 21 sealed interfaces and records).
 */
public sealed interface Command {

  /** {@code -h} / {@code --help}, and the default when no argument is given. */
  record Help() implements Command {}

  /** {@code -V} / {@code --version}. */
  record Version() implements Command {}

  /** {@code --file <audio>} — transcribe an audio file. Offline (G1). */
  record TranscribeFile(Path audio, Options options) implements Command {}

  /** {@code --mic} — transcribe the microphone. Offline (G1). */
  record TranscribeMic(Options options) implements Command {}

  /** {@code --ui} — open the JavaFX desktop UI (G6). */
  record ShowUi(Options options) implements Command {}
}
