package io.github.mit37.speechnotes.format;

import io.github.mit37.speechnotes.transcribe.TranscriptEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns final transcript events into {@link Segment}s.
 *
 * <p>Vosk's small English model emits no punctuation, so one final result is usually one thought
 * and the pause before it is the only structure available. A transcript that <em>does</em> carry
 * punctuation is split on sentence boundaries as well, with the original time window shared out by
 * word count.
 *
 * <p>List detection is a heuristic and is honest about it: "first" or "finally" at the start of a
 * thought starts a bullet, while words like "next", "then" and "last" only count once a list is
 * already open — otherwise "Next week we will cover indexes" would become a bullet.
 */
public final class Segmenter {

  /** Ordinals that start a list on their own. */
  private static final Set<String> ORDINALS =
      Set.of("first", "second", "third", "fourth", "fifth", "sixth", "finally", "lastly");

  /** Words that continue a list but never start one. */
  private static final Set<String> CONTINUATIONS = Set.of("next", "then", "also", "last");

  private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");
  private static final Pattern WORD = Pattern.compile("[A-Za-z0-9']+");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern WORD_THEN_PUNCTUATION = Pattern.compile("^\\s*[A-Za-z']+\\s*[,:;]");

  /**
   * Segments the finals, in order. Each segment records how long the speaker paused before it,
   * which is what the formatter uses to decide where a paragraph ends.
   */
  public List<Segment> segment(List<TranscriptEvent.Final> finals) {
    List<Segment> segments = new ArrayList<>();
    double previousEnd = -1;
    for (TranscriptEvent.Final event : finals) {
      double pauseBefore = previousEnd < 0 ? 0 : Math.max(0, event.startSeconds() - previousEnd);
      segments.addAll(split(event, pauseBefore));
      previousEnd = event.endSeconds();
    }
    return promoteEnumerators(segments);
  }

  private List<Segment> split(TranscriptEvent.Final event, double pauseBefore) {
    String text = clean(event.text());
    if (text.isEmpty()) {
      return List.of();
    }
    List<String> sentences = splitSentences(text);
    if (sentences.size() <= 1) {
      return List.of(
          new Segment(
              text, event.startSeconds(), event.endSeconds(), pauseBefore, candidate(text)));
    }
    int totalWords = sentences.stream().mapToInt(Segmenter::countWords).sum();
    double duration = Math.max(0, event.endSeconds() - event.startSeconds());
    double cursor = event.startSeconds();
    List<Segment> out = new ArrayList<>();
    for (int i = 0; i < sentences.size(); i++) {
      String sentence = sentences.get(i);
      double share =
          totalWords == 0
              ? duration / sentences.size()
              : duration * countWords(sentence) / totalWords;
      double start = cursor;
      double end = Math.min(event.endSeconds(), cursor + share);
      out.add(new Segment(sentence, start, end, i == 0 ? pauseBefore : 0, candidate(sentence)));
      cursor = end;
    }
    return out;
  }

  /** Keeps a candidate only where the rules allow it; clears the rest. */
  private List<Segment> promoteEnumerators(List<Segment> segments) {
    List<Segment> promoted = new ArrayList<>(segments.size());
    boolean listIsOpen = false;
    for (Segment segment : segments) {
      String candidate = segment.enumerator();
      boolean keep = false;
      if (candidate != null) {
        boolean punctuated = startsWithWordThenPunctuation(segment.text());
        if (ORDINALS.contains(candidate)) {
          keep = punctuated || segment.wordCount() >= 4;
        } else if (CONTINUATIONS.contains(candidate)) {
          keep = punctuated || listIsOpen;
        }
      }
      listIsOpen = keep;
      promoted.add(
          keep
              ? segment
              : new Segment(
                  segment.text(),
                  segment.startSeconds(),
                  segment.endSeconds(),
                  segment.pauseBeforeSeconds(),
                  null));
    }
    return promoted;
  }

  /** Splits on {@code . ! ?} boundaries, keeping the punctuation. */
  public static List<String> splitSentences(String text) {
    List<String> sentences = new ArrayList<>();
    for (String piece : SENTENCE_BOUNDARY.split(clean(text))) {
      String trimmed = clean(piece);
      if (!trimmed.isEmpty()) {
        sentences.add(trimmed);
      }
    }
    return sentences;
  }

  /** The list word this text starts with, before the promote/clear pass. */
  public static String candidateOf(String text) {
    return candidate(text);
  }

  /** Removes the leading list word and its punctuation, and capitalises what is left. */
  public static String stripEnumerator(String text) {
    String trimmed = clean(text);
    String first = firstWord(trimmed);
    if (!ORDINALS.contains(first) && !CONTINUATIONS.contains(first)) {
      return trimmed;
    }
    String rest = trimmed.substring(Math.min(first.length(), trimmed.length()));
    rest = rest.replaceFirst("^[\\s,;:—–-]+", "").trim();
    if (rest.isEmpty()) {
      return rest;
    }
    return Character.toUpperCase(rest.charAt(0)) + rest.substring(1);
  }

  public static String clean(String text) {
    return text == null ? "" : WHITESPACE.matcher(text.trim()).replaceAll(" ");
  }

  static int countWords(String text) {
    String clean = clean(text);
    return clean.isEmpty() ? 0 : WORD.split(clean).length;
  }

  private static String candidate(String text) {
    String first = firstWord(text);
    return ORDINALS.contains(first) || CONTINUATIONS.contains(first) ? first : null;
  }

  private static boolean startsWithWordThenPunctuation(String text) {
    Matcher matcher = WORD_THEN_PUNCTUATION.matcher(clean(text));
    return matcher.find();
  }

  private static String firstWord(String text) {
    Matcher matcher = WORD.matcher(clean(text).toLowerCase(Locale.ROOT));
    return matcher.find() ? matcher.group() : "";
  }
}
