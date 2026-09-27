package io.github.mit37.speechnotes.format;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mit37.speechnotes.notes.NoteSection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarkdownNotesParserTest {

  @Test
  @DisplayName("a title, headings, bullets and paragraphs all come through")
  void parsesFullDocument() {
    String markdown =
        """
        # Caching

        ## Summary

        The lecture explains caching.

        ## Key points

        - A cache needs an expiry rule
        - Measure the hit rate first

        ## Actions

        1. Instrument the hit rate
        """;

    MarkdownNotesParser.ParsedNotes parsed = MarkdownNotesParser.parse(markdown);

    assertThat(parsed.title()).isEqualTo("Caching");
    assertThat(parsed.sections())
        .extracting(NoteSection::heading)
        .containsExactly("Summary", "Key points", "Actions");
    assertThat(parsed.sections().get(1).bullets())
        .containsExactly("A cache needs an expiry rule", "Measure the hit rate first");
    assertThat(parsed.sections().get(2).bullets()).containsExactly("Instrument the hit rate");
    assertThat(parsed.sections().getFirst().paragraphs())
        .containsExactly("The lecture explains caching.");
  }

  @Test
  @DisplayName("text before any heading still becomes a section, so nothing is dropped")
  void looseTextBecomesASection() {
    MarkdownNotesParser.ParsedNotes parsed =
        MarkdownNotesParser.parse("Just one line of notes.\n\n- and a bullet");

    assertThat(parsed.title()).isNull();
    assertThat(parsed.sections()).hasSize(1);
    assertThat(parsed.sections().getFirst().heading()).isEqualTo("Notes");
    assertThat(parsed.sections().getFirst().paragraphs())
        .containsExactly("Just one line of notes.");
    assertThat(parsed.sections().getFirst().bullets()).containsExactly("and a bullet");
  }

  @Test
  void emptyAndBlankInputsProduceNothing() {
    assertThat(MarkdownNotesParser.parse("").isEmpty()).isTrue();
    assertThat(MarkdownNotesParser.parse(null).isEmpty()).isTrue();
    assertThat(MarkdownNotesParser.parse("\n\n   \n").isEmpty()).isTrue();
  }

  @Test
  @DisplayName("a second top-level heading does not overwrite the first title")
  void keepsTheFirstTitle() {
    MarkdownNotesParser.ParsedNotes parsed =
        MarkdownNotesParser.parse("# First title\n\n## Body\n\ntext\n");

    assertThat(parsed.title()).isEqualTo("First title");
    assertThat(parsed.sections()).hasSize(1);
  }

  @Test
  @DisplayName("horizontal rules are separators, not content")
  void ignoresHorizontalRules() {
    MarkdownNotesParser.ParsedNotes parsed =
        MarkdownNotesParser.parse("# Title\n\n---\n\n## Body\n\n- item\n");

    assertThat(parsed.sections()).hasSize(1);
    assertThat(parsed.sections().getFirst().paragraphs()).isEmpty();
  }
}
