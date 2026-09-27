package io.github.mit37.speechnotes.eval;

import io.github.mit37.speechnotes.audio.FileAudioSource;
import io.github.mit37.speechnotes.format.FormattingOptions;
import io.github.mit37.speechnotes.format.RuleBasedFormatter;
import io.github.mit37.speechnotes.pipeline.TranscriptionPipeline;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Runs every fixture through Vosk and measures what came out (PRD G2, milestone 8).
 *
 * <p>Offline from end to end: same pipeline as {@code --file}, same model, no network. Every number
 * in the report is computed here rather than typed, which is what STANDARDS §1 asks for.
 */
public final class EvalRunner {

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);

  /** One fixture's numbers. */
  public record Entry(
      String name,
      double audioSeconds,
      double wallSeconds,
      double realTimeFactor,
      int referenceWords,
      WordErrorRate.Score errors,
      int transcriptWords,
      List<String> keywordsFound,
      List<String> keywordsMissing,
      List<String> keyTerms,
      List<String> rubricPhrasesInKeyTerms,
      String transcript) {

    public Entry {
      keywordsFound = List.copyOf(keywordsFound);
      keywordsMissing = List.copyOf(keywordsMissing);
      keyTerms = List.copyOf(keyTerms);
      rubricPhrasesInKeyTerms = List.copyOf(rubricPhrasesInKeyTerms);
    }

    public double wordErrorRate() {
      return errors.rate();
    }

    public int keywords() {
      return keywordsFound.size() + keywordsMissing.size();
    }

    public double keywordRecall() {
      return keywords() == 0 ? Double.NaN : (double) keywordsFound.size() / keywords();
    }

    /** Rubric phrases the recogniser heard but the formatter did not surface. */
    public List<String> rubricPhrasesMissedByFormatter() {
      return keywordsFound.stream()
          .filter(phrase -> !rubricPhrasesInKeyTerms.contains(phrase))
          .toList();
    }

    /**
     * Of the rubric phrases the recogniser actually heard, the share the offline formatter put in
     * its Key terms section.
     *
     * <p>The denominator is what was heard, not what the rubric lists: a phrase the recogniser
     * never produced cannot be a formatter failure, and scoring it as one would blame the wrong
     * component (PRD G3 measured here, milestone 8).
     */
    public double keyTermRecall() {
      return keywordsFound.isEmpty()
          ? Double.NaN
          : (double) rubricPhrasesInKeyTerms.size() / keywordsFound.size();
    }
  }

  /** The whole run: what was measured, and where the numbers come from. */
  public record Report(
      String model,
      String engine,
      List<Entry> entries,
      double totalAudioSeconds,
      double totalWallSeconds) {

    public Report {
      entries = List.copyOf(entries);
    }

    /** Processing time over audio time for the whole run; below 1.0 is faster than real time. */
    public double overallRealTimeFactor() {
      return totalAudioSeconds <= 0 ? -1 : totalWallSeconds / totalAudioSeconds;
    }

    public double meanWordErrorRate() {
      return entries.stream().mapToDouble(Entry::wordErrorRate).average().orElse(Double.NaN);
    }

    public int referenceWords() {
      return entries.stream().mapToInt(Entry::referenceWords).sum();
    }

    public int errors() {
      return entries.stream().mapToInt(entry -> entry.errors().errors()).sum();
    }

    /** Keyword recall over the whole run, in phrases rather than fixture averages. */
    public double keywordRecall() {
      int total = entries.stream().mapToInt(Entry::keywords).sum();
      if (total == 0) {
        return Double.NaN;
      }
      return (double) entries.stream().mapToInt(entry -> entry.keywordsFound().size()).sum()
          / total;
    }

    /** Key-term recall over the whole run, against the phrases that were actually heard. */
    public double keyTermRecall() {
      int heard = entries.stream().mapToInt(entry -> entry.keywordsFound().size()).sum();
      if (heard == 0) {
        return Double.NaN;
      }
      return (double)
              entries.stream().mapToInt(entry -> entry.rubricPhrasesInKeyTerms().size()).sum()
          / heard;
    }

    /** The Markdown the README quotes; generated, never edited by hand. */
    public String markdown(Instant generatedAt) {
      StringBuilder markdown = new StringBuilder();
      markdown.append("# Eval results\n\n");
      markdown
          .append("Generated: ")
          .append(TIMESTAMP.format(generatedAt))
          .append(" by `scripts/run_eval.sh` — offline, with `")
          .append(engine)
          .append("`.\n\n");
      markdown.append(
          "| Fixture | Audio s | Wall s | Real-time factor | Words | WER | Keyword recall |"
              + " Key-term recall |\n");
      markdown.append("|---|---|---|---|---|---|---|---|\n");
      for (Entry entry : entries) {
        markdown
            .append("| ")
            .append(entry.name())
            .append(" | ")
            .append(round(entry.audioSeconds(), 1))
            .append(" | ")
            .append(round(entry.wallSeconds(), 1))
            .append(" | ")
            .append(round(entry.realTimeFactor(), 2))
            .append(" | ")
            .append(entry.referenceWords())
            .append(" | ")
            .append(percent(entry.wordErrorRate()))
            .append(" | ")
            .append(percent(entry.keywordRecall()))
            .append(" (")
            .append(entry.keywordsFound().size())
            .append("/")
            .append(entry.keywords())
            .append(") | ")
            .append(percent(entry.keyTermRecall()))
            .append(" (")
            .append(entry.rubricPhrasesInKeyTerms().size())
            .append("/")
            .append(entry.keywordsFound().size())
            .append(") |\n");
      }
      markdown.append("\n- Mean WER: **").append(percent(meanWordErrorRate())).append("** (");
      markdown.append(errors()).append(" errors over ").append(referenceWords());
      markdown.append(" reference words, ").append(entries.size()).append(" fixtures).\n");
      markdown.append("- Overall real-time factor: **").append(round(overallRealTimeFactor(), 2));
      markdown.append("** (").append(round(totalWallSeconds, 1)).append("s of processing for ");
      markdown.append(round(totalAudioSeconds, 1)).append("s of audio).\n");
      markdown.append("- Keyword recall: **").append(percent(keywordRecall()));
      markdown.append("** of the hand-written rubric phrases were heard by the recogniser.\n");
      markdown.append("- Key-term recall: **").append(percent(keyTermRecall()));
      markdown.append(
          "** of the phrases it heard were surfaced as key terms by the offline formatter.\n");

      List<Entry> withMisses =
          entries.stream().filter(entry -> !entry.keywordsMissing().isEmpty()).toList();
      if (!withMisses.isEmpty()) {
        markdown.append("\n## What the recogniser missed\n\n");
        for (Entry entry : withMisses) {
          markdown.append("- `").append(entry.name()).append("`: ");
          markdown.append(String.join(", ", entry.keywordsMissing())).append('\n');
        }
      }
      markdown.append("\n## What the formatter put in Key terms\n\n");
      markdown.append(
          "Scored against the same hand-written rubric, but only against the phrases the recogniser\n"
              + "actually heard: a phrase that never reached the transcript cannot be a formatter\n"
              + "failure. Key terms are quoted exactly as the notes contain them.\n\n");
      markdown.append(
          "The denominator is small and the clips are short — see the last section before reading\n"
              + "these numbers as a verdict on the formatter.\n\n");
      for (Entry entry : entries) {
        markdown.append("- `").append(entry.name()).append("` — ");
        markdown
            .append(
                entry.keyTerms().isEmpty() ? "no key terms" : String.join(", ", entry.keyTerms()))
            .append('\n');
        List<String> formatterMisses = entry.rubricPhrasesMissedByFormatter();
        if (!formatterMisses.isEmpty()) {
          markdown
              .append("  - heard by the recogniser but not key-termed: ")
              .append(String.join(", ", formatterMisses))
              .append('\n');
        }
      }

      entries.stream()
          .findFirst()
          .ifPresent(
              first -> {
                markdown.append("\n## What the recogniser wrote (");
                markdown.append(first.name());
                markdown.append(", verbatim)\n\n```text\n");
                markdown.append(first.transcript().strip()).append("\n```\n");
              });

      markdown.append("\n## What this does not measure\n\n");
      markdown.append(
          "- A microphone, a real lecture, or any other language: the audio is synthetic speech\n"
              + "  (Windows SAPI voices) of the committed scripts in `eval/fixtures`.\n");
      markdown.append(
          "- Whether the notes are *readable*. Key-term recall asks whether the right phrases were\n"
              + "  surfaced, not whether the paragraphs, bullets or action items are any good; those\n"
              + "  are covered by tests, not by a score.\n");
      markdown.append(
          "- A large rubric: 39 hand-written phrases across five fixtures, written before the\n"
              + "  extractor was changed. A phrase list this short moves in 2.6-point steps per\n"
              + "  phrase, so treat the number as a smoke test, not a benchmark.\n");
      markdown.append(
          "- Anything about longer recordings. Every fixture is about forty seconds long, and most\n"
              + "  of the rubric's phrases are used exactly once in that time. A frequency-based\n"
              + "  extractor can only surface what repeats, so these clips are its floor case: a\n"
              + "  twenty-minute lecture repeats its terms far more than a forty-second clip can.\n");
      markdown.append(
          "- Gemini paths: this run is entirely offline and never touches the network.\n");
      return markdown.toString();
    }
  }

  private final Path modelDir;

  public EvalRunner(Path modelDir) {
    this.modelDir = modelDir;
  }

  /** Transcribes and scores every fixture, reporting progress through {@code progress}. */
  public Report run(List<EvalFixture> fixtures, Consumer<String> progress) throws IOException {
    List<Entry> entries = new ArrayList<>();
    String engine = "";
    double totalAudio = 0;
    double totalWall = 0;
    try (TranscriptionPipeline pipeline =
        TranscriptionPipeline.open(modelDir, new RuleBasedFormatter())) {
      engine = pipeline.transcriber().describe();
      for (EvalFixture fixture : fixtures) {
        progress.accept(
            "transcribing " + fixture.name() + " (" + fixture.audio().getFileName() + ")");
        try (FileAudioSource source = FileAudioSource.open(fixture.audio())) {
          TranscriptionPipeline.Result result =
              pipeline.run(source, fixture.name(), FormattingOptions.defaults(), event -> {});
          List<String> heard = WordErrorRate.words(result.transcript().text());
          List<String> found = new ArrayList<>();
          List<String> missing = new ArrayList<>();
          for (String keyword : fixture.keywords()) {
            if (WordErrorRate.contains(heard, keyword)) {
              found.add(keyword);
            } else {
              missing.add(keyword);
            }
          }
          List<String> keyTerms = result.notes().keyTerms();
          List<String> inKeyTerms =
              found.stream().filter(phrase -> keyTermCovers(keyTerms, phrase)).toList();
          entries.add(
              new Entry(
                  fixture.name(),
                  result.transcript().audioSeconds(),
                  result.wallSeconds(),
                  result.realTimeFactor(),
                  WordErrorRate.words(fixture.reference()).size(),
                  WordErrorRate.of(fixture.reference(), result.transcript().text()),
                  heard.size(),
                  found,
                  missing,
                  keyTerms,
                  inKeyTerms,
                  result.transcript().text()));
          totalAudio += result.transcript().audioSeconds();
          totalWall += result.wallSeconds();
        }
      }
    }
    return new Report(
        modelDir.getFileName() == null ? modelDir.toString() : modelDir.getFileName().toString(),
        engine,
        entries,
        totalAudio,
        totalWall);
  }

  /**
   * True when a single key term carries the phrase as consecutive words.
   *
   * <p>Checked term by term on purpose: flattening the whole list first would let "query" and
   * "plan" from two unrelated terms count as the phrase "query plan".
   */
  static boolean keyTermCovers(List<String> keyTerms, String phrase) {
    return keyTerms.stream()
        .anyMatch(term -> WordErrorRate.contains(WordErrorRate.words(term), phrase));
  }

  static String percent(double fraction) {
    if (Double.isNaN(fraction)) {
      return "n/a";
    }
    return Math.round(fraction * 1000) / 10.0 + "%";
  }

  static String round(double value, int decimals) {
    double factor = Math.pow(10, decimals);
    return String.valueOf(Math.round(value * factor) / factor);
  }
}
