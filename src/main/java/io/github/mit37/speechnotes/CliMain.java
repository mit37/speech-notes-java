package io.github.mit37.speechnotes;

import io.github.mit37.speechnotes.audio.AudioSource;
import io.github.mit37.speechnotes.audio.AudioSourceException;
import io.github.mit37.speechnotes.audio.FileAudioSource;
import io.github.mit37.speechnotes.audio.LimitedAudioSource;
import io.github.mit37.speechnotes.audio.MicAudioSource;
import io.github.mit37.speechnotes.cli.CliArgs;
import io.github.mit37.speechnotes.cli.Command;
import io.github.mit37.speechnotes.cli.FormatterChoice;
import io.github.mit37.speechnotes.cli.Options;
import io.github.mit37.speechnotes.cli.UsageException;
import io.github.mit37.speechnotes.export.MarkdownExporter;
import io.github.mit37.speechnotes.export.PdfExporter;
import io.github.mit37.speechnotes.format.Formatter;
import io.github.mit37.speechnotes.format.FormattingOptions;
import io.github.mit37.speechnotes.format.GeminiFormatter;
import io.github.mit37.speechnotes.format.GeminiPrompts;
import io.github.mit37.speechnotes.format.RuleBasedFormatter;
import io.github.mit37.speechnotes.gemini.GeminiConfig;
import io.github.mit37.speechnotes.gemini.HttpGeminiClient;
import io.github.mit37.speechnotes.gemini.RequestBudget;
import io.github.mit37.speechnotes.notes.NotesDocument;
import io.github.mit37.speechnotes.pipeline.TranscriptionPipeline;
import io.github.mit37.speechnotes.transcribe.TranscriptionException;
import io.github.mit37.speechnotes.ui.UiLauncher;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Command-line entry point.
 *
 * <p>{@link #run} returns an exit code instead of calling {@link System#exit}, so tests can drive
 * the whole application in-process and headlessly (docs/PLAN.md, cloud-instance constraints).
 */
public final class CliMain {

  /** Everything asked for was done. */
  static final int EXIT_OK = 0;

  /**
   * Something failed while running: unreadable audio, a missing model, no device, a write error.
   */
  static final int EXIT_FAILURE = 1;

  /** The command line was wrong (BSD {@code sysexits} EX_USAGE). */
  static final int EXIT_USAGE = 64;

  private CliMain() {}

  public static void main(String[] args) {
    System.exit(run(List.of(args), System.out, System.err));
  }

  /** Runs one command and returns the process exit code. */
  public static int run(List<String> args, PrintStream out, PrintStream err) {
    Command command;
    try {
      command = CliArgs.parse(args);
    } catch (UsageException e) {
      err.println("error: " + e.getMessage());
      err.println();
      err.println(CliArgs.USAGE);
      return EXIT_USAGE;
    }

    return switch (command) {
      case Command.Help ignored -> {
        out.println(CliArgs.USAGE);
        yield EXIT_OK;
      }
      case Command.Version ignored -> {
        out.println(AppInfo.DISPLAY_NAME + " " + AppInfo.VERSION);
        out.println(AppInfo.REPO_URL);
        yield EXIT_OK;
      }
      case Command.TranscribeFile file -> transcribeFile(file, out, err);
      case Command.TranscribeMic mic -> transcribeMic(mic, out, err);
      case Command.ShowUi ui -> showUi(ui, err);
    };
  }

  /** Hand over to JavaFX; on a machine with no display this says so instead of throwing. */
  private static int showUi(Command.ShowUi command, PrintStream err) {
    try {
      UiLauncher.launch(command.options());
      return EXIT_OK;
    } catch (RuntimeException | LinkageError e) {
      err.println("error: the desktop UI cannot start here: " + e.getMessage());
      err.println("With no display, use --file <audio> to transcribe a recording instead.");
      return EXIT_FAILURE;
    }
  }

  private static int transcribeFile(
      Command.TranscribeFile command, PrintStream out, PrintStream err) {
    Options options = command.options();
    Formatter formatter;
    try {
      formatter = formatterFor(options);
    } catch (UsageException e) {
      err.println("error: " + e.getMessage());
      return EXIT_FAILURE;
    }
    try (FileAudioSource source = FileAudioSource.open(command.audio());
        TranscriptionPipeline pipeline =
            TranscriptionPipeline.open(options.modelDir(), formatter)) {
      return transcribe(
          source, source.file().getFileName().toString(), options, pipeline, out, err);
    } catch (AudioSourceException | TranscriptionException e) {
      err.println("error: " + e.getMessage());
      return EXIT_FAILURE;
    } catch (IOException e) {
      err.println("error: " + e.getMessage());
      return EXIT_FAILURE;
    }
  }

  private static int transcribeMic(
      Command.TranscribeMic command, PrintStream out, PrintStream err) {
    Options options = command.options();
    Formatter formatter;
    try {
      formatter = formatterFor(options);
    } catch (UsageException e) {
      err.println("error: " + e.getMessage());
      return EXIT_FAILURE;
    }
    if (!MicAudioSource.isAvailable()) {
      err.println("error: no microphone input device is available on this machine.");
      err.println("Use --file <audio> for a recording; microphone mode needs a capture device.");
      return EXIT_FAILURE;
    }
    try (TranscriptionPipeline pipeline =
        TranscriptionPipeline.open(options.modelDir(), formatter)) {
      MicAudioSource microphone = MicAudioSource.open();
      Double limit = unlimitedRunLimit(options);
      try (AudioSource source =
          limit == null ? microphone : new LimitedAudioSource(microphone, limit)) {
        int exitCode = transcribe(source, "microphone", options, pipeline, out, err);
        if (source instanceof LimitedAudioSource limited && limited.endedBecauseOfStall()) {
          err.println("note: the input device went quiet, so the recording stopped early.");
        }
        return exitCode;
      }
    } catch (AudioSourceException | TranscriptionException e) {
      err.println("error: " + e.getMessage());
      return EXIT_FAILURE;
    } catch (IOException e) {
      err.println("error: " + e.getMessage());
      return EXIT_FAILURE;
    }
  }

  /**
   * How long to record when the user did not say.
   *
   * <p>Unbounded by default, because recording a lecture means pressing stop when the lecture ends.
   * Automated runs set {@code -Dspeechnotes.mic.maxSeconds} so a test can never sit on a live
   * microphone forever — which is exactly what happened once, and is why this exists.
   */
  private static Double unlimitedRunLimit(Options options) {
    if (options.seconds() != null) {
      return options.seconds();
    }
    String safety = System.getProperty("speechnotes.mic.maxSeconds", "").trim();
    if (safety.isEmpty()) {
      return null;
    }
    try {
      double seconds = Double.parseDouble(safety);
      return seconds > 0 ? seconds : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Runs the pipeline and puts the notes where the options ask for them. */
  private static int transcribe(
      AudioSource source,
      String sourceName,
      Options options,
      TranscriptionPipeline pipeline,
      PrintStream out,
      PrintStream err)
      throws IOException {
    double duration = source.durationSeconds();
    err.printf(
        Locale.ROOT,
        "transcribing %s%s — %s — offline%n",
        source.description(),
        duration > 0 ? String.format(Locale.ROOT, " (%.1fs)", duration) : "",
        pipeline.transcriber().describe());

    TranscriptionPipeline.Result result =
        pipeline.run(
            source,
            sourceName,
            formattingOptions(options),
            event -> {
              if (event.isFinal()) {
                err.printf(Locale.ROOT, "  [%s] %s%n", timestamp(event.endSeconds()), event.text());
              }
            });

    reportDryRun(options, result.transcript(), err);
    emit(result.notes(), options, out, err);
    err.printf(
        Locale.ROOT,
        "done: %d words, %.1fs of audio in %.1fs (real-time factor %.2f)%n",
        result.notes().wordCount(),
        result.transcript().audioSeconds(),
        result.wallSeconds(),
        result.realTimeFactor());
    return EXIT_OK;
  }

  /**
   * Puts the notes where the options ask for them.
   *
   * <p>Without {@code --out} the notes are printed as Markdown: standard output is the offline path
   * the README promises, and {@code --export} deliberately has nothing to do with it.
   */
  private static void emit(NotesDocument notes, Options options, PrintStream out, PrintStream err)
      throws IOException {
    if (options.outDir() == null) {
      out.println(new MarkdownExporter().render(notes));
      return;
    }
    List<Path> written = new ArrayList<>();
    if (options.export().writesMarkdown()) {
      written.add(
          new MarkdownExporter()
              .write(
                  notes,
                  options.outDir().resolve(new MarkdownExporter().suggestedFileName(notes))));
    }
    if (options.export().writesPdf()) {
      written.add(
          new PdfExporter()
              .write(notes, options.outDir().resolve(new PdfExporter().suggestedFileName())));
    }
    Path transcriptFile = options.outDir().resolve("transcript.txt");
    Files.createDirectories(transcriptFile.getParent());
    Files.writeString(transcriptFile, notes.transcript(), StandardCharsets.UTF_8);
    written.add(transcriptFile);
    err.println(
        "wrote " + written.stream().map(Path::toString).collect(Collectors.joining(" and ")));
  }

  private static FormattingOptions formattingOptions(Options options) {
    FormattingOptions defaults = FormattingOptions.defaults();
    return new FormattingOptions(
        defaults.pauseParagraphSeconds(),
        defaults.maxParagraphWords(),
        defaults.maxKeyTerms(),
        defaults.minKeyTermOccurrences(),
        options.extractActions(),
        options.title());
  }

  private static String timestamp(double seconds) {
    int total = (int) Math.round(seconds);
    return String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60);
  }

  /**
   * Picks the formatter the options ask for.
   *
   * <p>{@code --format gemini} without a key is a refusal, not a silent downgrade: the user asked
   * for something specific and the answer says what is missing. {@code --dry-run} is the no-key
   * path that still shows exactly what would have been sent (STANDARDS §2).
   */
  private static Formatter formatterFor(Options options) {
    if (options.formatter() != FormatterChoice.GEMINI) {
      return new RuleBasedFormatter();
    }
    GeminiConfig config = GeminiConfig.fromEnvironment();
    if (!config.isEnabled() && !options.dryRun()) {
      throw new UsageException(
          "--format gemini needs "
              + GeminiConfig.KEY_ENV
              + "; without it, run without --format gemini for offline notes, or add --dry-run to see the request");
    }
    if (config.isEnabled()) {
      return new GeminiFormatter(
          new HttpGeminiClient(config, RequestBudget.from(config)), config.model());
    }
    return new RuleBasedFormatter();
  }

  /** Prints what a dry run would have sent, and says plainly that nothing was sent. */
  private static void reportDryRun(
      Options options,
      io.github.mit37.speechnotes.transcribe.Transcript transcript,
      PrintStream err) {
    if (!options.dryRun() || options.formatter() != FormatterChoice.GEMINI) {
      return;
    }
    GeminiConfig config = GeminiConfig.fromEnvironment();
    err.println("dry run: nothing was sent to Gemini.");
    err.println(
        "would POST "
            + HttpGeminiClient.endpointFor(config.model())
            + " ("
            + config.describe()
            + ")");
    err.println("---8<--- request text ---8<---");
    err.println(
        GeminiPrompts.formatting(
            GeminiPrompts.truncate(
                transcript.text(), GeminiPrompts.DEFAULT_MAX_TRANSCRIPT_CHARACTERS)));
    err.println("--->8--- end of request --->8---");
  }
}
