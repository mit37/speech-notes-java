package io.github.mit37.speechnotes.format;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeyTermHighlighterTest {

  @Test
  @DisplayName("only the first occurrence in a paragraph is bolded")
  void boldsOncePerParagraph() {
    String highlighted =
        KeyTermHighlighter.highlight(
            "The cache pays off when the cache is asked twice", List.of("cache"));

    assertThat(highlighted).isEqualTo("The **cache** pays off when the cache is asked twice");
  }

  @Test
  void boldsEveryTermOnItsFirstOccurrence() {
    String highlighted =
        KeyTermHighlighter.highlight(
            "Expiry rules make the cache honest", List.of("cache", "expiry"));

    assertThat(highlighted).isEqualTo("**Expiry** rules make the **cache** honest");
  }

  @Test
  @DisplayName("a term does not light up inside another word")
  void matchesWholeWordsOnly() {
    String highlighted =
        KeyTermHighlighter.highlight("Caches are like cache but longer", List.of("cache"));

    assertThat(highlighted).isEqualTo("Caches are like **cache** but longer");
  }

  @Test
  @DisplayName("text that is already bold is left alone")
  void respectsExistingBoldText() {
    String highlighted = KeyTermHighlighter.highlight("The **cache** is a bet", List.of("cache"));

    assertThat(highlighted).isEqualTo("The **cache** is a bet");
  }

  @Test
  void punctuationStaysOutsideTheBoldRun() {
    String highlighted =
        KeyTermHighlighter.highlight("First, expiry, second, cache.", List.of("expiry"));

    assertThat(highlighted).isEqualTo("First, **expiry**, second, cache.");
  }

  @Test
  @DisplayName("a phrase is bolded as one run, not word by word")
  void boldsPhrasesAsOneRun() {
    String highlighted =
        KeyTermHighlighter.highlight("The hit rate was measured", List.of("hit rate"));

    assertThat(highlighted).isEqualTo("The **hit rate** was measured");
  }

  @Test
  @DisplayName("the longest term wins where they overlap, and each term still gets one bold")
  void longestTermWinsAtTheSameSpot() {
    String highlighted =
        KeyTermHighlighter.highlight(
            "The hit rate matters more than hit alone", List.of("hit", "hit rate"));

    // The phrase owns the overlapping spot; the standalone word is bolded where it is not covered.
    // In practice the extractor drops a word whose uses are mostly inside a phrase, so this only
    // arises for a word that genuinely stands on its own elsewhere.
    assertThat(highlighted).isEqualTo("The **hit rate** matters more than **hit** alone");
  }

  @Test
  @DisplayName("punctuation between the words means it is not that phrase")
  void punctuationBreaksAPhrase() {
    String highlighted = KeyTermHighlighter.highlight("The hit, rate was odd", List.of("hit rate"));

    assertThat(highlighted).isEqualTo("The hit, rate was odd");
  }

  @Test
  @DisplayName("a phrase is bolded once, and nothing ends up nested in a bold run")
  void phraseIsBoldedOnceAndNotNested() {
    String highlighted =
        KeyTermHighlighter.highlight(
            "The hit rate went up, so the hit rate was watched", List.of("hit rate", "rate"));

    assertThat(highlighted).isEqualTo("The **hit rate** went up, so the hit **rate** was watched");
    assertThat(highlighted)
        .as("one bold per term, and the phrase is not repeated")
        .doesNotContain("****")
        .doesNotContain("the **hit rate** was watched");
  }

  @Test
  @DisplayName("adjacency is whitespace, and nothing else")
  void adjacencyIsWhitespaceOnly() {
    assertThat(KeyTermHighlighter.adjacent("hit rate", 3, 4)).isTrue();
    assertThat(KeyTermHighlighter.adjacent("hit  rate", 3, 5)).isTrue();
    assertThat(KeyTermHighlighter.adjacent("hit, rate", 3, 5)).isFalse();
  }

  @Test
  void nothingToDoReturnsTheInput() {
    assertThat(KeyTermHighlighter.highlight("plain text", List.of())).isEqualTo("plain text");
    assertThat(KeyTermHighlighter.highlight("", List.of("cache"))).isEmpty();
    assertThat(KeyTermHighlighter.highlight(null, List.of("cache"))).isNull();
  }

  @Test
  @DisplayName("matching is case-insensitive but the original casing is kept")
  void keepsOriginalCasing() {
    String highlighted = KeyTermHighlighter.highlight("Cache expiry", List.of("CACHE"));

    assertThat(highlighted).isEqualTo("**Cache** expiry");
  }
}
