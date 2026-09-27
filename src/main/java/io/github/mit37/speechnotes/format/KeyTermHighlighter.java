package io.github.mit37.speechnotes.format;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bolds the first occurrence of each key term in a paragraph (PRD G3).
 *
 * <p>Token-based on purpose: a term only matches whole words, so "cache" does not light up inside
 * "caches", and anything the text already bolds is left alone.
 *
 * <p>Terms can be phrases ("hit rate"). Phrases are matched before single words so the longer term
 * wins, and the words of a phrase have to be genuinely adjacent — "hit, rate" is two words with
 * punctuation between them, not the phrase. Once a span is bolded, nothing else may cover it.
 */
public final class KeyTermHighlighter {

  private static final Pattern WORD = Pattern.compile("[A-Za-z0-9']+");
  private static final Pattern EXISTING_BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
  private static final Pattern WHITESPACE_ONLY = Pattern.compile("\\s+");

  private KeyTermHighlighter() {}

  /** Applies every term; each one is bolded at most once per paragraph. */
  public static String highlight(String paragraph, List<String> terms) {
    if (paragraph == null || paragraph.isEmpty() || terms == null || terms.isEmpty()) {
      return paragraph;
    }
    List<Word> words = words(paragraph);
    if (words.isEmpty()) {
      return paragraph;
    }
    Set<String> alreadyBold = existingBoldWords(paragraph);
    List<int[]> spans = new ArrayList<>();
    for (String term : phrasesFirst(terms)) {
      int[] span = find(paragraph, words, term, alreadyBold, spans);
      if (span != null) {
        spans.add(span);
      }
    }
    if (spans.isEmpty()) {
      return paragraph;
    }
    spans.sort((left, right) -> Integer.compare(left[0], right[0]));
    StringBuilder out = new StringBuilder(paragraph.length() + spans.size() * 4);
    int cursor = 0;
    for (int[] span : spans) {
      out.append(paragraph, cursor, span[0]).append("**");
      out.append(paragraph, span[0], span[1]).append("**");
      cursor = span[1];
    }
    out.append(paragraph, cursor, paragraph.length());
    return out.toString();
  }

  /** Longest terms first, so "hit rate" is tried before "hit"; ties keep the caller's order. */
  private static List<String> phrasesFirst(List<String> terms) {
    List<String> ordered = new ArrayList<>();
    for (String term : terms) {
      if (term != null && !term.isBlank()) {
        ordered.add(term.strip());
      }
    }
    ordered.sort((left, right) -> Integer.compare(wordCount(right), wordCount(left)));
    return ordered;
  }

  /**
   * The first occurrence of {@code term} that no other span or existing bold already covers.
   *
   * @return {@code [start, end]} character offsets in the paragraph, or {@code null}
   */
  private static int[] find(
      String paragraph, List<Word> words, String term, Set<String> alreadyBold, List<int[]> spans) {
    List<String> wanted = lowerWords(term);
    if (wanted.isEmpty()) {
      return null;
    }
    for (int start = 0; start + wanted.size() <= words.size(); start++) {
      if (!matches(paragraph, words, start, wanted)) {
        continue;
      }
      Word first = words.get(start);
      Word last = words.get(start + wanted.size() - 1);
      if (alreadyBold.contains(first.text()) || overlaps(spans, first.start(), last.end())) {
        continue;
      }
      return new int[] {first.start(), last.end()};
    }
    return null;
  }

  /** Words match, and the phrase's words run together rather than being split by punctuation. */
  private static boolean matches(
      String paragraph, List<Word> words, int start, List<String> wanted) {
    for (int offset = 0; offset < wanted.size(); offset++) {
      if (!wanted.get(offset).equals(words.get(start + offset).text())) {
        return false;
      }
      if (offset > 0
          && !adjacent(
              paragraph, words.get(start + offset - 1).end(), words.get(start + offset).start())) {
        return false;
      }
    }
    return true;
  }

  /** Whether the gap between two words holds nothing but whitespace. */
  static boolean adjacent(String paragraph, int firstEnd, int secondStart) {
    return WHITESPACE_ONLY.matcher(paragraph.substring(firstEnd, secondStart)).matches();
  }

  private static boolean overlaps(List<int[]> spans, int start, int end) {
    for (int[] span : spans) {
      if (start < span[1] && end > span[0]) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> existingBoldWords(String paragraph) {
    Set<String> words = new LinkedHashSet<>();
    Matcher matcher = EXISTING_BOLD.matcher(paragraph);
    while (matcher.find()) {
      Matcher wordMatcher = WORD.matcher(matcher.group(1).toLowerCase(Locale.ROOT));
      while (wordMatcher.find()) {
        words.add(wordMatcher.group());
      }
    }
    return words;
  }

  /** The paragraph's words with their character offsets, lower-cased. */
  private static List<Word> words(String paragraph) {
    List<Word> words = new ArrayList<>();
    Matcher matcher = WORD.matcher(paragraph);
    while (matcher.find()) {
      words.add(new Word(matcher.group().toLowerCase(Locale.ROOT), matcher.start(), matcher.end()));
    }
    return words;
  }

  private static List<String> lowerWords(String term) {
    List<String> words = new ArrayList<>();
    Matcher matcher = WORD.matcher(term.toLowerCase(Locale.ROOT));
    while (matcher.find()) {
      words.add(matcher.group());
    }
    return words;
  }

  private static int wordCount(String term) {
    return lowerWords(term).size();
  }

  /** A word and where it sits in the paragraph. */
  private record Word(String text, int start, int end) {}
}
