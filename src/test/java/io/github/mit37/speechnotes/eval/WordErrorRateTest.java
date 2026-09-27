package io.github.mit37.speechnotes.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WordErrorRateTest {

  @Test
  @DisplayName("identical text scores zero, punctuation and case included")
  void identicalTextScoresZero() {
    WordErrorRate.Score score = WordErrorRate.of("Cache, the hit rate!", "cache the hit rate");

    assertThat(score.errors()).isZero();
    assertThat(score.referenceWords()).isEqualTo(4);
    assertThat(score.rate()).isZero();
  }

  @Test
  @DisplayName("substitutions, insertions and deletions are counted apart")
  void editOperationsAreCounted() {
    WordErrorRate.Score score = WordErrorRate.of("a b c", "a x c d");

    assertThat(score.substitutions()).isEqualTo(1);
    assertThat(score.insertions()).isEqualTo(1);
    assertThat(score.deletions()).isZero();
    assertThat(score.errors()).isEqualTo(2);
    assertThat(score.rate()).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  @DisplayName("a missing word is a deletion; a wrong word counts once, not twice")
  void deletionsAndSubstitutions() {
    assertThat(WordErrorRate.of("a b c d", "a b d").deletions()).isEqualTo(1);
    assertThat(WordErrorRate.of("a b c d", "a b c d").errors()).isZero();

    WordErrorRate.Score single = WordErrorRate.of("cache", "cashing");
    assertThat(single.substitutions()).isEqualTo(1);
    assertThat(single.errors()).isEqualTo(1);
    assertThat(single.rate()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("an empty reference scores 0 when nothing was heard and 1 when words appear")
  void emptyReferenceIsHandled() {
    assertThat(WordErrorRate.of("", "").rate()).isZero();
    assertThat(WordErrorRate.of("", "something").rate()).isEqualTo(1.0);
    assertThat(WordErrorRate.of("something", "").rate()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("numbers stay words, so a digit form costs two edits instead of matching")
  void digitsDoNotMatchSpokenNumbers() {
    WordErrorRate.Score score = WordErrorRate.of("eighty four percent", "84 percent");

    // "eighty four" -> "84" can be read as a substitution plus a deletion; either way it costs two.
    assertThat(score.errors()).isEqualTo(2);
    assertThat(score.referenceWords()).isEqualTo(3);
    assertThat(score.rate()).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  void wordsNormaliseCaseAndPunctuation() {
    assertThat(WordErrorRate.words("Hello,  world!")).containsExactly("hello", "world");
    assertThat(WordErrorRate.words(null)).isEmpty();
    assertThat(WordErrorRate.words("   ")).isEmpty();
  }

  @Test
  @DisplayName("keyword matching needs the words in order, not just present")
  void keywordMatchingNeedsConsecutiveWords() {
    List<String> transcript = WordErrorRate.words("the hit rate is the number to watch");

    assertThat(WordErrorRate.contains(transcript, "hit rate")).isTrue();
    assertThat(WordErrorRate.contains(transcript, "rate hit")).isFalse();
    assertThat(WordErrorRate.contains(transcript, "the hit rate")).isTrue();
    assertThat(WordErrorRate.contains(transcript, "")).isFalse();
    assertThat(WordErrorRate.contains(transcript, "hit rate of the cache")).isFalse();
  }
}
