package io.github.mit37.speechnotes.format;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the terms worth bolding: repeated words <em>and</em> repeated phrases, ranked by TF-IDF
 * (PRD G3).
 *
 * <p>Each segment counts as a document: a term used often but only in a couple of segments is what
 * the lecture was about, while one used in every segment is just how the speaker talks. IDF is the
 * smoothed variant, {@code log((N + 1) / (df + 1)) + 1}, so nothing scores zero and a
 * single-segment session still ranks by frequency.
 *
 * <p>Three rules on top of plain frequency, each one a response to a term the earlier
 * single-word-only version got wrong (see the README's before/after):
 *
 * <ul>
 *   <li><strong>Phrases count as terms.</strong> "Hit rate" is a term; "hit" and "rate" separately
 *       are not the same thing, and bolding both halves says less. Up to {@value #MAX_PHRASE_WORDS}
 *       adjacent content words form a candidate, but only when they are literally adjacent in the
 *       text — a phrase the highlighter cannot find would be a term nobody ever sees.
 *   <li><strong>Cohesion.</strong> A phrase scores higher when its words rarely appear apart, which
 *       is what separates a real collocation from a coincidence, capped at double.
 *   <li><strong>Topic position.</strong> The first segment gets a bonus: a lecture says what it is
 *       about near the start, and that is exactly what a term like "caching" looks like to a
 *       frequency counter that would otherwise prefer "minute" and "percent".
 *   <li><strong>No past-tense tails.</strong> A term may not end in "-ed". Repeated sentence
 *       structure produced "expiry reached" and "minute expiry reached" on the fixture this was
 *       measured on: a verb form that happens to repeat is grammar, not a topic. The rule is about
 *       the tail only, because "-ed" words are often adjectives in front ("fixed cost") and "-ing"
 *       words are kept outright ("caching" and "testing" are the topics of two of the fixtures). It
 *       is a heuristic, not grammar, and it is listed as one in the README.
 * </ul>
 *
 * <p>A word whose uses are mostly inside a chosen phrase is dropped rather than listed twice.
 */
public final class KeyTermExtractor {

  /** Longest phrase considered. Three words covers "eighty four percent" and stops there. */
  public static final int MAX_PHRASE_WORDS = 3;

  /** How much a term that appears in the opening segment outranks an otherwise equal one. */
  public static final double TOPIC_BONUS = 1.25;

  private static final Pattern TOKEN = Pattern.compile("[a-z][a-z0-9'-]*");
  private static final Pattern PAST_TENSE = Pattern.compile("[a-z]{3,}ed");
  private static final Set<String> STOPWORDS =
      Set.of(
          "the", "a", "an", "and", "or", "but", "if", "then", "than", "that", "this", "these",
          "those", "is", "are", "was", "were", "be", "been", "being", "am", "do", "does", "did",
          "done", "have", "has", "had", "having", "will", "would", "shall", "should", "can",
          "could", "may", "might", "must", "of", "in", "on", "at", "to", "for", "with", "from",
          "by", "about", "into", "over", "under", "again", "once", "here", "there", "when", "where",
          "why", "how", "all", "any", "both", "each", "few", "more", "most", "other", "some",
          "such", "no", "nor", "not", "only", "own", "same", "so", "too", "very", "just", "you",
          "your", "we", "our", "us", "i", "me", "my", "he", "him", "his", "she", "her", "it", "its",
          "they", "them", "their", "what", "which", "who", "whom", "as", "because", "before",
          "after", "up", "down", "out", "off", "get", "got", "one", "two", "three", "four", "five",
          "first", "second", "third", "finally", "next", "also", "last", "let", "lets", "well",
          "okay", "right", "like", "say", "says", "said", "thing", "things", "today", "now", "need",
          "needs", "way", "make", "makes", "made", "take", "takes", "every", "much", "many",
          "still", "even", "ever", "never", "always", "often");

  /**
   * A candidate term, its score and how often it was used; exposed so the ranking can be tested.
   */
  public record Term(String text, double score, int occurrences) {

    public int wordCount() {
      return text.split(" ").length;
    }
  }

  /** Terms worth bolding: repeated, not stopwords, ranked by TF-IDF plus the rules above. */
  public List<String> extract(List<String> documents, int limit, int minOccurrences) {
    return rank(documents, limit, minOccurrences).stream().map(Term::text).toList();
  }

  /** The same ranking with scores attached, and in the order it would be cut. */
  public List<Term> rank(List<String> documents, int limit, int minOccurrences) {
    if (limit <= 0 || minOccurrences < 1 || documents == null || documents.isEmpty()) {
      return List.of();
    }
    Map<String, Counted> counts = count(documents);
    List<Term> ranked = new ArrayList<>();
    for (Map.Entry<String, Counted> entry : counts.entrySet()) {
      Counted counted = entry.getValue();
      if (counted.termFrequency < minOccurrences) {
        continue;
      }
      double score =
          scoreOf(entry.getKey(), counted, counts, documents.size())
              * (counted.firstSegment == 0 ? TOPIC_BONUS : 1.0);
      ranked.add(new Term(entry.getKey(), score, counted.termFrequency));
    }
    ranked.sort(Comparator.comparingDouble(Term::score).reversed().thenComparing(Term::text));
    return select(ranked, limit);
  }

  /** Smoothed TF-IDF: frequency in the session times how few segments the term appears in. */
  public static double score(int termFrequency, int documentFrequency, double documentCount) {
    double idf = Math.log((documentCount + 1.0) / (documentFrequency + 1.0)) + 1.0;
    return termFrequency * idf;
  }

  /** Lower-cased content words of at least three letters that are not stopwords. */
  public static List<String> tokenize(String text) {
    List<String> tokens = new ArrayList<>();
    Matcher matcher = TOKEN.matcher(text == null ? "" : text.toLowerCase(Locale.ROOT));
    while (matcher.find()) {
      String token = matcher.group();
      if (token.length() >= 3 && !STOPWORDS.contains(token)) {
        tokens.add(token);
      }
    }
    return tokens;
  }

  public static boolean isStopword(String word) {
    return STOPWORDS.contains(word.toLowerCase(Locale.ROOT));
  }

  /** Exposed so the README and tests can state exactly what is filtered out. */
  public static Set<String> stopwords() {
    return STOPWORDS;
  }

  private static double scoreOf(
      String term, Counted counted, Map<String, Counted> counts, int documentCount) {
    double base = score(counted.termFrequency, counted.documentFrequency, documentCount);
    int words = term.split(" ").length;
    return words == 1 ? base : base * cohesion(term, counted, counts);
  }

  /** How strongly the words of a phrase travel together: 1.0 apart, up to 2.0 always together. */
  private static double cohesion(String phrase, Counted counted, Map<String, Counted> counts) {
    int rarestPart = Integer.MAX_VALUE;
    for (String word : phrase.split(" ")) {
      Counted part = counts.get(word);
      rarestPart = Math.min(rarestPart, part == null ? 0 : part.termFrequency);
    }
    if (rarestPart <= 0) {
      return 1.0;
    }
    return 1.0 + Math.min(1.0, (double) counted.termFrequency / rarestPart);
  }

  /** Walks the ranking and drops words that are mostly accounted for by a phrase already chosen. */
  private static List<Term> select(List<Term> ranked, int limit) {
    List<Term> chosen = new ArrayList<>(Math.min(limit, ranked.size()));
    for (Term term : ranked) {
      if (chosen.size() >= limit) {
        break;
      }
      if (!subsumed(term, chosen)) {
        chosen.add(term);
      }
    }
    return List.copyOf(chosen);
  }

  private static boolean subsumed(Term term, List<Term> chosen) {
    if (term.wordCount() > 1) {
      return false;
    }
    for (Term other : chosen) {
      if (other.wordCount() == 1 || !List.of(other.text().split(" ")).contains(term.text())) {
        continue;
      }
      if (term.occurrences() <= other.occurrences() * 2) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, Counted> count(List<String> documents) {
    Map<String, Counted> counts = new LinkedHashMap<>();
    for (int index = 0; index < documents.size(); index++) {
      int segmentIndex = index;
      List<String> words = rawTokens(documents.get(index));
      Set<String> seenHere = new HashSet<>();
      for (int start = 0; start < words.size(); start++) {
        if (isStopword(words.get(start))) {
          continue;
        }
        for (int length = 1;
            length <= MAX_PHRASE_WORDS && start + length <= words.size();
            length++) {
          List<String> slice = words.subList(start, start + length);
          if (length > 1 && (isStopword(slice.get(length - 1)) || !distinct(slice))) {
            break;
          }
          if (isPastTense(slice.get(length - 1))) {
            break;
          }
          String term = String.join(" ", slice);
          Counted counted = counts.computeIfAbsent(term, unused -> new Counted(segmentIndex));
          counted.termFrequency++;
          if (seenHere.add(term)) {
            counted.documentFrequency++;
          }
        }
      }
    }
    return counts;
  }

  /**
   * Every word of the text, stopwords included, lower-cased.
   *
   * <p>Phrases are built from these rather than from {@link #tokenize} on purpose: filtering
   * stopwords first would glue "cache needs an expiry rule" into "cache expiry", a phrase that does
   * not exist in the text and that the highlighter could never find.
   */
  private static List<String> rawTokens(String text) {
    List<String> tokens = new ArrayList<>();
    Matcher matcher = TOKEN.matcher(text == null ? "" : text.toLowerCase(Locale.ROOT));
    while (matcher.find()) {
      tokens.add(matcher.group());
    }
    return tokens;
  }

  private static boolean distinct(List<String> words) {
    return new HashSet<>(words).size() == words.size();
  }

  /** True for the past-tense shape a repeated template sentence leaves behind. */
  static boolean isPastTense(String word) {
    return word != null && PAST_TENSE.matcher(word).matches();
  }

  /** Mutable per-term tally while the counts are collected. */
  private static final class Counted {

    private final int firstSegment;
    private int termFrequency;
    private int documentFrequency;

    private Counted(int firstSegment) {
      this.firstSegment = firstSegment;
    }
  }
}
