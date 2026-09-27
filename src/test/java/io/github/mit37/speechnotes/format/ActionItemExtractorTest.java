package io.github.mit37.speechnotes.format;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mit37.speechnotes.notes.ActionItem;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActionItemExtractorTest {

  private final ActionItemExtractor extractor = new ActionItemExtractor();

  private static Segment segment(String text, double endSeconds) {
    return new Segment(text, endSeconds - 2, endSeconds, 0, null);
  }

  @Test
  @DisplayName("an \"action:\" cue becomes an action item with its timestamp")
  void findsActionCue() {
    List<ActionItem> items =
        extractor.extract(
            List.of(
                segment("the rest is history", 2),
                segment("action: instrument the hit rate this week", 6)));

    assertThat(items).hasSize(1);
    assertThat(items.getFirst().text()).isEqualTo("Instrument the hit rate this week");
    assertThat(items.getFirst().cue()).isEqualTo("action:");
    assertThat(items.getFirst().atSeconds()).isEqualTo(6);
  }

  @Test
  @DisplayName("todo, remember to and we need to are all cues")
  void findsSeveralCues() {
    List<ActionItem> items =
        extractor.extract(
            List.of(
                segment("todo write the test first", 2),
                segment("remember to measure the hit rate", 4),
                segment("we need to expire the entry", 6)));

    assertThat(items).hasSize(3);
    assertThat(items)
        .extracting(ActionItem::cue)
        .containsExactly("todo", "remember to", "we need to");
  }

  @Test
  @DisplayName("a sentence without a cue is not an action")
  void ignoresSentencesWithoutCues() {
    assertThat(extractor.extract(List.of(segment("caching is a bet about the future", 2))))
        .isEmpty();
  }

  @Test
  @DisplayName("a cue has to be a whole word")
  void doesNotMatchInsideWords() {
    assertThat(extractor.extract(List.of(segment("todos are a list of things", 2)))).isEmpty();
  }

  @Test
  @DisplayName("a cue with nothing after it is dropped rather than reported empty")
  void dropsEmptyBodies() {
    assertThat(extractor.extract(List.of(segment("action:", 2)))).isEmpty();
    assertThat(extractor.extract(List.of(segment("todo: ", 2)))).isEmpty();
  }

  @Test
  void duplicateActionsAreReportedOnce() {
    List<ActionItem> items =
        extractor.extract(
            List.of(
                segment("action: instrument the hit rate", 2),
                segment("action: instrument the hit rate", 4)));

    assertThat(items).hasSize(1);
  }

  @Test
  @DisplayName("the longest cue wins, so \"action item:\" does not get read as \"action\"")
  void prefersTheLongestCue() {
    List<ActionItem> items =
        extractor.extract(List.of(segment("action item: fix the flaky test", 2)));

    assertThat(items.getFirst().cue()).isEqualTo("action item:");
    assertThat(items.getFirst().text()).isEqualTo("Fix the flaky test");
  }

  @Test
  @DisplayName("actions come back in the order they were said")
  void sortsByTime() {
    List<ActionItem> items =
        extractor.extract(List.of(segment("todo second thing", 8), segment("todo first thing", 4)));

    assertThat(items).extracting(ActionItem::text).containsExactly("First thing", "Second thing");
  }

  @Test
  void cuesAreDocumented() {
    assertThat(ActionItemExtractor.cues()).contains("action:", "todo", "we need to", "remember to");
  }
}
