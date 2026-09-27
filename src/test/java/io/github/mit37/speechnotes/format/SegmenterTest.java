package io.github.mit37.speechnotes.format;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mit37.speechnotes.TestTranscripts;
import io.github.mit37.speechnotes.transcribe.TranscriptEvent;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SegmenterTest {

  private final Segmenter segmenter = new Segmenter();

  private List<Segment> segment(TranscriptEvent.Final... finals) {
    return segmenter.segment(List.of(finals));
  }

  @Test
  void oneFinalBecomesOneSegment() {
    List<Segment> segments = segment(TestTranscripts.finalEvent("a cache pays off", 0, 2));

    assertThat(segments).hasSize(1);
    assertThat(segments.getFirst().text()).isEqualTo("a cache pays off");
    assertThat(segments.getFirst().pauseBeforeSeconds()).isZero();
    assertThat(segments.getFirst().isEnumerator()).isFalse();
  }

  @Test
  @DisplayName("the pause before a segment is measured from the end of the previous one")
  void recordsThePauseBeforeEachSegment() {
    List<Segment> segments =
        segment(
            TestTranscripts.finalEvent("first thought", 0, 2),
            TestTranscripts.finalEvent("second thought", 3.5, 5));

    assertThat(segments.get(1).pauseBeforeSeconds()).isEqualTo(1.5);
  }

  @Test
  void blankFinalsAreDropped() {
    assertThat(segment(TestTranscripts.finalEvent("   ", 0, 1))).isEmpty();
  }

  @Test
  @DisplayName("punctuation splits one final into sentences with shared timing")
  void punctuationSplitsSentences() {
    List<Segment> segments =
        segment(
            TestTranscripts.finalEvent(
                "A cache pays off. Every entry needs an expiry rule. Measure the hit rate!", 0, 9));

    assertThat(segments).hasSize(3);
    assertThat(segments.get(0).text()).isEqualTo("A cache pays off.");
    assertThat(segments.get(2).text()).isEqualTo("Measure the hit rate!");
    assertThat(segments.getFirst().startSeconds()).isZero();
    assertThat(segments.getLast().endSeconds()).isEqualTo(9.0);
    // Sentence two starts where sentence one ended, and never runs backwards.
    assertThat(segments.get(1).startSeconds()).isEqualTo(segments.get(0).endSeconds());
    assertThat(segments.get(1).endSeconds()).isGreaterThanOrEqualTo(segments.get(1).startSeconds());
  }

  @Test
  @DisplayName("an ordinal that starts a thought starts a list item")
  void detectsOrdinalListItems() {
    List<Segment> segments =
        segment(
            TestTranscripts.finalEvent("First a cache only pays off twice", 0, 2),
            TestTranscripts.finalEvent("Every entry needs an expiry rule", 2, 4),
            TestTranscripts.finalEvent("Finally remember that a cache is a bet", 4, 6));

    assertThat(segments.get(0).isEnumerator()).isTrue();
    assertThat(segments.get(0).enumerator()).isEqualTo("first");
    assertThat(segments.get(1).isEnumerator()).isFalse();
    assertThat(segments.get(2).enumerator()).isEqualTo("finally");
  }

  @Test
  @DisplayName("a punctuated list word counts even in a short fragment")
  void detectsPunctuatedListWords() {
    List<Segment> segments =
        segment(
            TestTranscripts.finalEvent("First, caching.", 0, 1),
            TestTranscripts.finalEvent("Second, expiry.", 1, 2));

    assertThat(segments).allMatch(Segment::isEnumerator);
  }

  @Test
  @DisplayName("\"Next week we will cover indexes\" is not a list item")
  void doesNotMistakeContinuationsForNewLists() {
    List<Segment> segments =
        segment(
            TestTranscripts.finalEvent("This lecture explains indexes", 0, 2),
            TestTranscripts.finalEvent("Next week we will look at query plans", 3, 5));

    assertThat(segments).noneMatch(Segment::isEnumerator);
  }

  @Test
  @DisplayName("a continuation does count once a list is already open")
  void continuationContinuesAnOpenList() {
    List<Segment> segments =
        segment(
            TestTranscripts.finalEvent("First indexes make reads faster", 0, 2),
            TestTranscripts.finalEvent("Next the writer pays for it", 2, 4));

    assertThat(segments.get(0).isEnumerator()).isTrue();
    assertThat(segments.get(1).enumerator()).isEqualTo("next");
  }

  @Test
  void stripEnumeratorRemovesTheCueAndCapitalises() {
    assertThat(Segmenter.stripEnumerator("finally remember that a cache is a bet"))
        .isEqualTo("Remember that a cache is a bet");
    assertThat(Segmenter.stripEnumerator("First, a cache pays off")).isEqualTo("A cache pays off");
    assertThat(Segmenter.stripEnumerator("a cache pays off")).isEqualTo("a cache pays off");
  }

  @Test
  void splitSentencesKeepsPunctuation() {
    assertThat(Segmenter.splitSentences("One. Two? Three!"))
        .containsExactly("One.", "Two?", "Three!");
    assertThat(Segmenter.splitSentences("no punctuation here"))
        .containsExactly("no punctuation here");
    assertThat(Segmenter.splitSentences("  ")).isEmpty();
  }

  @Test
  void cleanCollapsesWhitespace() {
    assertThat(Segmenter.clean("  a   b \n c ")).isEqualTo("a b c");
  }
}
