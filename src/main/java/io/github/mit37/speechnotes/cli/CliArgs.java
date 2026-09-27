package io.github.mit37.speechnotes.cli;

import io.github.mit37.speechnotes.AppInfo;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

/** Turns argv into a {@link Command}. The only place command-line grammar lives. */
public final class CliArgs {

  /** Help text, also printed on a usage error. Kept honest about what needs a key. */
  public static final String USAGE =
      """
      Usage: speech-notes-java <command> [options]

      Commands
        --file <audio>     transcribe an audio file (WAV or MP3), offline
        --mic              transcribe the microphone, offline
        --ui               open the desktop UI
        -V, --version      print the version and exit
        -h, --help         print this help and exit

      Options
        --out <dir>        write the notes and transcript.txt there instead of printing notes
        --export <how>     md (default), pdf, or both — needs --out
        --model <dir>      Vosk model directory (default: models/vosk-model-small-en-us-0.15)
        --seconds <n>      stop microphone recording after n seconds
        --format <how>     rule (default, offline) or gemini (needs GEMINI_API_KEY)
        --dry-run          show the Gemini request without sending it, and stay offline
        --no-actions       leave out the action-item section
        --title <text>     notes title (default: derived from the file name)

      Transcription is always offline. Gemini is only used for formatting when asked for with
      --format gemini and a key is present; without one the app says so and stops.
      """;

  private CliArgs() {}

  /**
   * Parses {@code args}.
   *
   * @throws UsageException if the arguments are not understood
   */
  public static Command parse(List<String> args) {
    if (args.isEmpty()) {
      return new Command.Help();
    }
    return new Parser(args.iterator()).command();
  }

  /** Convenience overload for callers that already have an array (i.e. {@code main}). */
  public static Command parse(String... args) {
    return parse(List.of(args));
  }

  private static final class Parser {

    private final Iterator<String> tokens;
    private Mode mode;
    private Path audio;
    private Path outDir;
    private Path modelDir = Path.of(AppInfo.DEFAULT_MODEL_DIR);
    private Double seconds;
    private FormatterChoice formatter = FormatterChoice.RULE_BASED;
    private boolean extractActions = true;
    private String title;
    private boolean dryRun;
    private ExportFormat export = ExportFormat.MARKDOWN;

    private Parser(Iterator<String> tokens) {
      this.tokens = tokens;
    }

    private Command command() {
      while (tokens.hasNext()) {
        String token = tokens.next();
        switch (token) {
          case "-h", "--help" -> setMode(Mode.HELP, token);
          case "-V", "--version" -> setMode(Mode.VERSION, token);
          case "--mic" -> setMode(Mode.MIC, token);
          case "--ui" -> setMode(Mode.UI, token);
          case "--file" -> {
            setMode(Mode.FILE, token);
            audio = Path.of(value(token));
          }
          case "--out" -> outDir = Path.of(value(token));
          case "--model" -> modelDir = Path.of(value(token));
          case "--seconds" -> seconds = positiveNumber(token);
          case "--format" -> formatter = FormatterChoice.parse(value(token));
          case "--no-actions" -> extractActions = false;
          case "--dry-run" -> dryRun = true;
          case "--title" -> title = value(token);
          case "--export" -> export = ExportFormat.parse(value(token));
          default -> throw new UsageException("unknown option: " + token);
        }
      }
      if (mode == null) {
        throw new UsageException("no command given — pick one of --file, --mic or --ui");
      }
      if (export != ExportFormat.MARKDOWN && outDir == null) {
        throw new UsageException(
            "--export " + export.flag() + " needs --out <dir>: there is nowhere to write the file");
      }
      Options options =
          new Options(modelDir, outDir, seconds, formatter, extractActions, title, dryRun, export);
      return switch (mode) {
        case HELP -> new Command.Help();
        case VERSION -> new Command.Version();
        case FILE -> new Command.TranscribeFile(audio, options);
        case MIC -> new Command.TranscribeMic(options);
        case UI -> new Command.ShowUi(options);
      };
    }

    private void setMode(Mode newMode, String token) {
      if (mode != null && mode != newMode) {
        throw new UsageException("only one command at a time: " + token + " after " + mode.flag);
      }
      mode = newMode;
    }

    private String value(String flag) {
      if (!tokens.hasNext()) {
        throw new UsageException(flag + " needs a value");
      }
      String value = tokens.next();
      if (value.isBlank()) {
        throw new UsageException(flag + " needs a value");
      }
      return value;
    }

    private Double positiveNumber(String flag) {
      String raw = value(flag);
      try {
        double parsed = Double.parseDouble(raw);
        if (parsed <= 0) {
          throw new UsageException(flag + " must be a positive number of seconds, got " + raw);
        }
        return parsed;
      } catch (NumberFormatException e) {
        throw new UsageException(flag + " must be a number of seconds, got " + raw);
      }
    }
  }

  private enum Mode {
    HELP("--help"),
    VERSION("--version"),
    FILE("--file"),
    MIC("--mic"),
    UI("--ui");

    private final String flag;

    Mode(String flag) {
      this.flag = flag;
    }
  }
}
