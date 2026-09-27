package io.github.mit37.speechnotes.format;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mit37.speechnotes.TestTranscripts;
import io.github.mit37.speechnotes.notes.NoteSection;
import io.github.mit37.speechnotes.notes.NotesDocument;
import io.github.mit37.speechnotes.transcribe.Transcript;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleBasedFormatterTest {

  private final RuleBasedFormatter formatter = new RuleBasedFormatter();

  private static List<String> paragraphs(NotesDocument document) {
    return document.sections().stream()
        .filter(section -> section.heading().equals("Notes"))
        .findFirst()
        .orElseThrow()
        .paragraphs();
  }

  private static List<String> bullets(NotesDocument document) {
    return document.sections().stream()
        .filter(section -> section.heading().equals("Notes"))
        .findFirst()
        .orElseThrow()
        .bullets();
  }

  private static List<String> headings(NotesDocument document) {
    return document.sections().stream().map(NoteSection::heading).toList();
  }

  @Test
  @DisplayName("a long pause starts a new paragraph, a short one does not")
  void pausesSplitParagraphs() {
    Transcript shortPauses =
        TestTranscripts.transcriptOf(
            TestTranscripts.storeWithPauses(
                0.2, "the cache pays off when data repeats", "expiry keeps it honest"));
    Transcript longPause =
        TestTranscripts.transcriptOf(
            TestTranscripts.storeWithPauses(
                1.5, "the cache pays off when data repeats", "expiry keeps it honest"));

    assertThat(paragraphs(formatter.format(shortPauses, FormattingOptions.defaults()))).hasSize(1);
    assertThat(paragraphs(formatter.format(longPause, FormattingOptions.defaults()))).hasSize(2);
  }

  @Test
  @DisplayName("paragraphs are capped by the word budget")
  void wordBudgetSplitsParagraphs() {
    Transcript transcript =
        TestTranscripts.transcript(
            "the cache pays off when the same data is requested more than once",
            "every cached entry needs an expiry rule or stale data outlives the bug",
            "measure the hit rate before you add another layer");
    FormattingOptions tight = new FormattingOptions(5.0, 18, 8, 2, true, null);

    NotesDocument document = formatter.format(transcript, tight);

    assertThat(paragraphs(document)).hasSizeGreaterThan(1);
  }

  @Test
  @DisplayName("the speaker's own list words become bullets, without the cue")
  void enumeratorsBecomeBullets() {
    Transcript transcript =
        TestTranscripts.transcript(
            "First a cache only pays off twice",
            "Second every entry needs an expiry rule",
            "Finally measure before adding a layer");

    NotesDocument document = formatter.format(transcript, FormattingOptions.defaults());

    assertThat(bullets(document))
        .containsExactly(
            "A cache only pays off twice",
            "Every entry needs an expiry rule",
            "Measure before adding a layer");
    assertThat(paragraphs(document)).isEmpty();
  }

  @Test
  @DisplayName("action items get their own section, with the cue sentence as the source")
  void actionItemsBecomeASection() {
    Transcript transcript =
        TestTranscripts.transcript(
            "the cache pays off when data repeats",
            "action: instrument the hit rate on the product page this week");

    NotesDocument document = formatter.format(transcript, FormattingOptions.defaults());

    assertThat(headings(document)).contains("Action items");
    assertThat(document.actions()).hasSize(1);
    assertThat(document.actions().getFirst().text()).contains("Instrument the hit rate");
  }

  @Test
  @DisplayName("--no-actions really does leave the section out")
  void actionExtractionCanBeTurnedOff() {
    Transcript transcript = TestTranscripts.transcript("todo instrument the hit rate");
    FormattingOptions noActions = new FormattingOptions(1.0, 55, 8, 2, false, null);

    NotesDocument document = formatter.format(transcript, noActions);

    assertThat(headings(document)).doesNotContain("Action items");
    assertThat(document.actions()).isEmpty();
  }

  @Test
  @DisplayName("repeated terms are listed and bolded once in the notes")
  void repeatedTermsAreKeyTermsAndBolded() {
    Transcript transcript =
        TestTranscripts.transcript(
            "the cache pays off when the same data repeats",
            "the cache needs an expiry rule",
            "the cache experiment reached eighty percent");

    NotesDocument document = formatter.format(transcript, FormattingOptions.defaults());

    assertThat(document.keyTerms()).contains("cache");
    assertThat(headings(document)).contains("Key terms");
    assertThat(paragraphs(document).getFirst()).startsWith("The **cache**");
  }

  @Test
  @DisplayName("an empty transcript produces an honest note instead of an empty document")
  void emptyTranscriptIsHandled() {
    Transcript empty = TestTranscripts.transcript();

    NotesDocument document = formatter.format(empty, FormattingOptions.defaults());

    assertThat(document.isEmpty()).isFalse();
    assertThat(paragraphs(document).getFirst()).contains("No speech was recognised");
    assertThat(document.transcript()).isEmpty();
  }

  @Test
  @DisplayName("the title comes from the options, or from the file name")
  void titles() {
    Transcript transcript = TestTranscripts.transcript("cache cache");

    assertThat(formatter.format(transcript, FormattingOptions.defaults()).title())
        .isEqualTo("Lecture");
    assertThat(
            formatter
                .format(transcript, FormattingOptions.defaults().withTitle("Caching week 3"))
                .title())
        .isEqualTo("Caching week 3");
  }

  @Test
  void defaultTitlesAreReadable() {
    assertThat(RuleBasedFormatter.defaultTitle("lecture-01-caching.wav"))
        .isEqualTo("Lecture 01 caching");
    assertThat(RuleBasedFormatter.defaultTitle("meeting_notes.mp3")).isEqualTo("Meeting notes");
    assertThat(RuleBasedFormatter.defaultTitle("")).isEqualTo("Notes");
  }

  @Test
  @DisplayName("the document records where it came from and what produced it")
  void metadataIsRecorded() {
    Transcript transcript = TestTranscripts.transcript("cache cache cache");

    NotesDocument document = formatter.format(transcript, FormattingOptions.defaults());

    assertThat(document.source()).isEqualTo("lecture.wav");
    assertThat(document.engine()).isEqualTo("Vosk (test)");
    assertThat(document.formatter()).isEqualTo("rule-based");
    assertThat(document.audioSeconds()).isEqualTo(2.0);
    assertThat(document.transcript()).isEqualTo("cache cache cache");
    assertThat(document.wordCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("formatting is deterministic apart from the timestamp")
  void isDeterministic() {
    Transcript transcript =
        TestTranscripts.transcript("first the cache pays off", "finally measure the hit rate");
    FormattingOptions options = FormattingOptions.defaults();

    NotesDocument first = formatter.format(transcript, options);
    NotesDocument second = formatter.format(transcript, options);

    assertThat(first.sections()).isEqualTo(second.sections());
    assertThat(first.keyTerms()).isEqualTo(second.keyTerms());
    assertThat(first.actions()).isEqualTo(second.actions());
  }

  @Test
  @DisplayName("no key terms are invented when nothing repeats")
  void noKeyTermsWhenNothingRepeats() {
    NotesDocument document =
        formatter.format(
            TestTranscripts.transcript("alpha beta gamma"), FormattingOptions.defaults());

    assertThat(document.keyTerms()).isEmpty();
    assertThat(headings(document)).doesNotContain("Key terms");
  }

  @Test
  void formatterNameIsRuleBased() {
    assertThat(formatter.name()).isEqualTo("rule-based");
  }
}
