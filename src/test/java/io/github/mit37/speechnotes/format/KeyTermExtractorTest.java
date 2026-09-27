package io.github.mit37.speechnotes.format;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeyTermExtractorTest {

  private final KeyTermExtractor extractor = new KeyTermExtractor();

  @Test
  @DisplayName("a term used across the session beats one that appears everywhere")
  void ranksByTfIdf() {
    List<String> documents =
        List.of(
            "the cache needs an expiry rule",
            "the cache hit rate was measured",
            "the cache experiment reached eighty percent",
            "the speaker also said something about the weather");

    List<String> terms = extractor.extract(documents, 5, 2);

    assertThat(terms).contains("cache");
    assertThat(terms.indexOf("cache")).isZero();
    assertThat(terms).doesNotContain("the");
  }

  @Test
  @DisplayName("stopwords are never key terms")
  void filtersStopwords() {
    List<String> terms =
        extractor.extract(List.of("it is the same and the other one", "it is here"), 5, 2);

    assertThat(terms).isEmpty();
  }

  @Test
  @DisplayName("a term has to be repeated to qualify")
  void requiresRepetition() {
    List<String> terms = extractor.extract(List.of("caching pays off when data repeats"), 5, 2);

    assertThat(terms).isEmpty();
  }

  @Test
  @DisplayName("with a single segment the ranking falls back to frequency")
  void singleSegmentStillRanks() {
    List<String> terms = extractor.extract(List.of("expiry expiry expiry cache cache graph"), 2, 2);

    assertThat(terms).containsExactly("expiry", "cache");
  }

  @Test
  @DisplayName("short words, numbers and punctuation are not terms")
  void ignoresShortTokensAndNumbers() {
    List<String> terms = extractor.extract(List.of("a to be 42 xxyy11 zz"), 10, 1);

    assertThat(terms).doesNotContain("a", "to", "be", "42");
  }

  @Test
  void respectsTheLimit() {
    List<String> terms =
        extractor.extract(List.of("alpha alpha beta beta gamma gamma delta delta"), 2, 1);

    assertThat(terms).hasSize(2);
  }

  @Test
  void tokenizeLowercasesAndFilters() {
    assertThat(KeyTermExtractor.tokenize("The Cache, and the EXPIRY-RULE!"))
        .containsExactly("cache", "expiry-rule");
  }

  @Test
  @DisplayName("the smoothed TF-IDF score never goes negative or zero")
  void scoreIsAlwaysPositive() {
    assertThat(KeyTermExtractor.score(1, 1, 1)).isGreaterThan(0);
    assertThat(KeyTermExtractor.score(5, 1, 10)).isGreaterThan(KeyTermExtractor.score(5, 10, 10));
  }

  @Test
  @DisplayName("a repeated phrase beats the words it is made of")
  void repeatedPhrasesAreTerms() {
    List<String> terms =
        extractor.extract(
            List.of(
                "the hit rate tells you what happened",
                "the hit rate is the number to watch",
                "measure the hit rate this week"),
            3,
            2);

    assertThat(terms).containsExactly("hit rate");
  }

  @Test
  @DisplayName("a phrase is only a term when its words really are adjacent")
  void phrasesMustBeLiteral() {
    List<String> terms =
        extractor.extract(
            List.of("cache needs an expiry rule", "every cache needs an expiry rule"), 3, 2);

    assertThat(terms).contains("expiry rule").doesNotContain("cache needs", "cache expiry");
  }

  @Test
  @DisplayName("a word repeated on its own is not a phrase")
  void repeatedSingleWordIsNotAPhrase() {
    List<String> terms = extractor.extract(List.of("expiry expiry expiry cache cache graph"), 5, 2);

    assertThat(terms).contains("expiry").contains("cache");
    assertThat(terms).noneMatch(term -> term.contains(" "));
  }

  @Test
  @DisplayName("with everything else equal, the term from the opening segment wins")
  void topicPositionGetsTheBonus() {
    List<String> terms = extractor.extract(List.of("cache cache", "minute minute"), 2, 2);

    assertThat(terms).containsExactly("cache", "minute");
  }

  @Test
  @DisplayName("a word mostly used inside a chosen phrase is not listed twice")
  void subsumedWordsAreDropped() {
    List<String> terms =
        extractor.extract(
            List.of(
                "the query plan says seq scan",
                "the query plan is what we read",
                "check the query plan before guessing"),
            4,
            2);

    assertThat(terms).contains("query plan");
    assertThat(terms).doesNotContain("query", "plan");
  }

  @Test
  @DisplayName("rank keeps the scores that produced the order")
  void rankExposesScores() {
    List<KeyTermExtractor.Term> ranked =
        extractor.rank(List.of("the hit rate", "the hit rate again"), 2, 2);

    assertThat(ranked).isNotEmpty();
    assertThat(ranked.getFirst().text()).isEqualTo("hit rate");
    assertThat(ranked.getFirst().wordCount()).isEqualTo(2);
    assertThat(ranked.getFirst().occurrences()).isEqualTo(2);
    assertThat(ranked.getFirst().score()).isGreaterThan(0);
    assertThat(ranked)
        .isSortedAccordingTo(Comparator.comparingDouble(KeyTermExtractor.Term::score).reversed());
  }

  @Test
  void noDocumentsNoTerms() {
    assertThat(extractor.extract(List.of(), 5, 2)).isEmpty();
    assertThat(extractor.extract(List.of("cache cache"), 0, 2)).isEmpty();
  }
}
