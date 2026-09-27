package io.github.mit37.speechnotes.format;

import io.github.mit37.speechnotes.notes.ActionItem;
import io.github.mit37.speechnotes.notes.NoteSection;
import io.github.mit37.speechnotes.notes.NotesDocument;
import io.github.mit37.speechnotes.transcribe.Transcript;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The offline formatter (PRD G3).
 *
 * <p>It restructures what was said and never invents anything: paragraphs come from pauses, items
 * from the speaker's own "first / second / finally", key terms from TF-IDF over the session,
 * actions from the speaker's own cue words. There is no summary, because a summary would be content
 * this code cannot know it got right.
 */
public final class RuleBasedFormatter implements Formatter {

  public static final String NAME = "rule-based";

  private final Segmenter segmenter;
  private final KeyTermExtractor keyTermExtractor;
  private final ActionItemExtractor actionItemExtractor;

  public RuleBasedFormatter() {
    this(new Segmenter(), new KeyTermExtractor(), new ActionItemExtractor());
  }

  public RuleBasedFormatter(
      Segmenter segmenter,
      KeyTermExtractor keyTermExtractor,
      ActionItemExtractor actionItemExtractor) {
    this.segmenter = segmenter;
    this.keyTermExtractor = keyTermExtractor;
    this.actionItemExtractor = actionItemExtractor;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public NotesDocument format(Transcript transcript, FormattingOptions options) {
    String title = options.titleOr(defaultTitle(transcript.source()));
    NotesDocument.Builder document =
        NotesDocument.builder(title, transcript.source())
            .engine(transcript.engine())
            .audioSeconds(transcript.audioSeconds())
            .formatter(NAME)
            .transcript(transcript.text());

    List<Segment> segments = segmenter.segment(transcript.store().finals());
    if (segments.isEmpty()) {
      return document
          .sections(
              List.of(
                  new NoteSection(
                      "Notes",
                      1,
                      List.of(
                          "No speech was recognised in this recording, so there is nothing to format."),
                      List.of())))
          .build();
    }

    List<String> keyTerms =
        keyTermExtractor.extract(
            segments.stream().map(Segment::text).toList(),
            options.maxKeyTerms(),
            options.minKeyTermOccurrences());

    List<String> paragraphs = new ArrayList<>();
    List<String> bullets = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int currentWords = 0;
    for (Segment segment : segments) {
      boolean pauseIsLarge = segment.pauseBeforeSeconds() > options.pauseParagraphSeconds();
      boolean paragraphIsFull =
          currentWords > 0 && currentWords + segment.wordCount() > options.maxParagraphWords();
      if (segment.isEnumerator()) {
        flush(current, paragraphs);
        currentWords = 0;
        String item = Segmenter.stripEnumerator(segment.text());
        if (!item.isBlank()) {
          bullets.add(item);
        }
        continue;
      }
      if (currentWords > 0 && (pauseIsLarge || paragraphIsFull)) {
        flush(current, paragraphs);
        currentWords = 0;
      }
      if (!current.isEmpty()) {
        current.append(' ');
      }
      current.append(Segmenter.clean(segment.text()));
      currentWords += segment.wordCount();
    }
    flush(current, paragraphs);

    List<NoteSection> sections = new ArrayList<>();
    sections.add(
        new NoteSection(
            "Notes",
            1,
            paragraphs.stream()
                .map(paragraph -> KeyTermHighlighter.highlight(paragraph, keyTerms))
                .toList(),
            bullets.stream()
                .map(bullet -> KeyTermHighlighter.highlight(bullet, keyTerms))
                .toList()));

    List<ActionItem> actions = List.of();
    if (options.extractActions()) {
      actions = actionItemExtractor.extract(segments);
      if (!actions.isEmpty()) {
        sections.add(
            new NoteSection(
                "Action items", 1, List.of(), actions.stream().map(ActionItem::text).toList()));
      }
    }
    if (!keyTerms.isEmpty()) {
      sections.add(
          new NoteSection("Key terms", 1, List.of(String.join(", ", keyTerms)), List.of()));
    }

    return document.sections(sections).keyTerms(keyTerms).actions(actions).build();
  }

  /**
   * Derives a readable title from the source, e.g. {@code lecture-01-caching.wav} → {@code Lecture
   * 01 caching}.
   */
  public static String defaultTitle(String source) {
    if (source == null || source.isBlank()) {
      return "Notes";
    }
    String name = source;
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      name = name.substring(0, dot);
    }
    name = name.replace('-', ' ').replace('_', ' ').trim().replaceAll("\\s+", " ");
    if (name.isEmpty()) {
      return "Notes";
    }
    return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase(Locale.ROOT);
  }

  private static void flush(StringBuilder current, List<String> paragraphs) {
    String text = current.toString().trim();
    if (!text.isEmpty()) {
      paragraphs.add(capitaliseFirst(text));
    }
    current.setLength(0);
  }

  private static String capitaliseFirst(String text) {
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }
}
