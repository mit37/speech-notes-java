package io.github.mit37.speechnotes.format;

import io.github.mit37.speechnotes.gemini.GeminiClient;
import io.github.mit37.speechnotes.gemini.GeminiConfig;
import io.github.mit37.speechnotes.gemini.GeminiException;
import io.github.mit37.speechnotes.gemini.GeminiRequest;
import io.github.mit37.speechnotes.gemini.GeminiResponse;
import io.github.mit37.speechnotes.gemini.HttpGeminiClient;
import io.github.mit37.speechnotes.gemini.RequestBudget;
import io.github.mit37.speechnotes.notes.NoteSection;
import io.github.mit37.speechnotes.notes.NotesDocument;
import io.github.mit37.speechnotes.transcribe.Transcript;
import java.util.ArrayList;
import java.util.List;

/**
 * The optional online formatter (PRD G3).
 *
 * <p>Three things this deliberately does:
 *
 * <ul>
 *   <li>It never fails the run. No key, a refused request, an exhausted cap — all of it produces
 *       the offline rule-based notes plus a line saying what happened.
 *   <li>It says in the document which formatter produced it, so nobody has to guess later.
 *   <li>It truncates the transcript to fit the character cap instead of being refused by it.
 * </ul>
 */
public final class GeminiFormatter implements Formatter {

  public static final String NAME = "gemini";

  private final GeminiClient client;
  private final RuleBasedFormatter fallback;
  private final String model;
  private final int maxTranscriptCharacters;

  public GeminiFormatter(GeminiClient client, String model) {
    this(client, model, GeminiPrompts.DEFAULT_MAX_TRANSCRIPT_CHARACTERS, new RuleBasedFormatter());
  }

  public GeminiFormatter(
      GeminiClient client, String model, int maxTranscriptCharacters, RuleBasedFormatter fallback) {
    this.client = client;
    this.model = model;
    this.maxTranscriptCharacters = maxTranscriptCharacters;
    this.fallback = fallback;
  }

  /**
   * The formatter the window should use: Gemini when {@code GEMINI_API_KEY} is set, the offline
   * rule-based one otherwise.
   *
   * <p>Different from the command line on purpose. A CLI user who typed {@code --format gemini} is
   * refused when the key is missing, because they asked for something specific; somebody pressing
   * Record in a window did not ask for anything, so falling back to offline notes is right — and
   * the document still records which formatter produced it.
   */
  public static Formatter fromEnvironment() {
    GeminiConfig config = GeminiConfig.fromEnvironment();
    if (!config.isEnabled()) {
      return new RuleBasedFormatter();
    }
    return new GeminiFormatter(
        new HttpGeminiClient(config, RequestBudget.from(config)), config.model());
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public NotesDocument format(Transcript transcript, FormattingOptions options) {
    if (transcript.text().isBlank()) {
      return fallback.format(transcript, options);
    }
    String prompt =
        GeminiPrompts.formatting(
            GeminiPrompts.truncate(transcript.text(), maxTranscriptCharacters));
    try {
      GeminiResponse response = client.generate(GeminiRequest.text(model, prompt));
      MarkdownNotesParser.ParsedNotes parsed = MarkdownNotesParser.parse(response.text());
      if (parsed.isEmpty()) {
        return withNotice(
            fallback.format(transcript, options),
            "Gemini returned no usable structure, so these notes are the offline rule-based ones.");
      }
      String title =
          parsed.title() != null && !parsed.title().isBlank()
              ? parsed.title()
              : options.titleOr(RuleBasedFormatter.defaultTitle(transcript.source()));
      return NotesDocument.builder(title, transcript.source())
          .engine(transcript.engine())
          .audioSeconds(transcript.audioSeconds())
          .formatter(NAME)
          .transcript(transcript.text())
          .sections(parsed.sections())
          .build();
    } catch (GeminiException e) {
      return withNotice(
          fallback.format(transcript, options),
          "Gemini formatting was not used ("
              + e.getMessage()
              + "). These notes are rule-based and offline.");
    }
  }

  /** Adds an honest note about the fallback to an otherwise normal document. */
  private static NotesDocument withNotice(NotesDocument document, String notice) {
    List<NoteSection> sections = new ArrayList<>();
    sections.add(new NoteSection("About these notes", 1, List.of(notice), List.of()));
    sections.addAll(document.sections());
    return NotesDocument.builder(document.title(), document.source())
        .engine(document.engine())
        .audioSeconds(document.audioSeconds())
        .formatter(document.formatter() + " (gemini skipped)")
        .transcript(document.transcript())
        .sections(sections)
        .keyTerms(document.keyTerms())
        .actions(document.actions())
        .attachments(document.attachments())
        .generatedAt(document.generatedAt())
        .build();
  }
}
