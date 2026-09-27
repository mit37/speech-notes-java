package io.github.mit37.speechnotes.format;

import io.github.mit37.speechnotes.notes.ActionItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds the "do this" sentences (PRD G3).
 *
 * <p>Two kinds of cue, because they behave differently in speech:
 *
 * <ul>
 *   <li><b>Leading labels</b> — "action:", "todo", "next step:" — only count at the start of a
 *       thought, so "the action of a cache is subtle" stays a sentence, while "action: instrument
 *       the hit rate" becomes an item. A colon is optional because Vosk emits no punctuation.
 *   <li><b>Verb phrases</b> — "remember to", "we need to" — count anywhere in a thought.
 * </ul>
 *
 * The extractor reports the cue it matched and the words that followed, and never rewrites them.
 */
public final class ActionItemExtractor {

  /** Labels a speaker uses to announce a task; they must open the thought. */
  private static final List<String> LEADING_CUES =
      List.of(
          "action item:",
          "action item",
          "action:",
          "action",
          "next step:",
          "next step",
          "todo:",
          "todo",
          "to do:");

  /** Phrases that state a task in mid-sentence. */
  private static final List<String> PHRASE_CUES =
      List.of(
          "don't forget to",
          "dont forget to",
          "remember to",
          "make sure to",
          "we need to",
          "i need to",
          "you need to",
          "we should",
          "we must");

  public List<ActionItem> extract(List<Segment> segments) {
    List<ActionItem> items = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (Segment segment : segments) {
      String text = segment.text();
      String lower = text.toLowerCase(Locale.ROOT);
      Match match = bestMatch(lower);
      if (match == null) {
        continue;
      }
      String body = text.substring(match.index() + match.cue().length());
      body = body.replaceFirst("^[\\s,;:—–-]+", "").trim().replaceFirst("[.]+$", "").trim();
      if (body.isEmpty()) {
        continue;
      }
      if (seen.add(body.toLowerCase(Locale.ROOT))) {
        items.add(new ActionItem(capitalise(body), match.cue(), segment.endSeconds()));
      }
    }
    items.sort(Comparator.comparingDouble(ActionItem::atSeconds));
    return items;
  }

  /** Exposed so tests and the README can state exactly which cues are recognised. */
  public static List<String> cues() {
    List<String> all = new ArrayList<>(LEADING_CUES);
    all.addAll(PHRASE_CUES);
    return List.copyOf(all);
  }

  private record Match(String cue, int index) {}

  private Match bestMatch(String lowerText) {
    String trimmed = lowerText.stripLeading();
    int leadingOffset = lowerText.length() - trimmed.length();
    Match best = null;
    for (String cue : LEADING_CUES) {
      int index = indexOfCue(trimmed, cue);
      if (index == 0) {
        best = prefer(best, new Match(cue, index + leadingOffset));
      }
    }
    for (String cue : PHRASE_CUES) {
      int index = indexOfCue(lowerText, cue);
      if (index >= 0) {
        best = prefer(best, new Match(cue, index));
      }
    }
    return best;
  }

  /** Earliest match wins; at the same position the longest cue wins. */
  private static Match prefer(Match current, Match candidate) {
    if (current == null) {
      return candidate;
    }
    if (candidate.index() < current.index()) {
      return candidate;
    }
    if (candidate.index() == current.index() && candidate.cue().length() > current.cue().length()) {
      return candidate;
    }
    return current;
  }

  private static int indexOfCue(String lowerText, String cue) {
    int from = 0;
    while (true) {
      int index = lowerText.indexOf(cue, from);
      if (index < 0) {
        return -1;
      }
      boolean startsClean = index == 0 || !Character.isLetterOrDigit(lowerText.charAt(index - 1));
      int after = index + cue.length();
      boolean endsClean =
          after >= lowerText.length() || !Character.isLetterOrDigit(lowerText.charAt(after));
      if (startsClean && endsClean) {
        return index;
      }
      from = index + 1;
    }
  }

  private static String capitalise(String text) {
    return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }
}
