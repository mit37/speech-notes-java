package io.github.mit37.speechnotes.format;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mit37.speechnotes.TestTranscripts;
import io.github.mit37.speechnotes.gemini.GeminiClient;
import io.github.mit37.speechnotes.gemini.GeminiException;
import io.github.mit37.speechnotes.gemini.GeminiRequest;
import io.github.mit37.speechnotes.gemini.GeminiResponse;
import io.github.mit37.speechnotes.notes.NotesDocument;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The optional formatter: it may fail, but it may never fail the run or invent content. */
class GeminiFormatterTest {

  /** Records what it was asked and answers from a script. */
  private static final class RecordingClient implements GeminiClient {
    private final List<GeminiRequest> requests = new ArrayList<>();
    private String reply;
    private RuntimeException failure;

    RecordingClient replying(String reply) {
      this.reply = reply;
      return this;
    }

    RecordingClient failing(RuntimeException failure) {
      this.failure = failure;
      return this;
    }

    @Override
    public GeminiResponse generate(GeminiRequest request) {
      requests.add(request);
      if (failure != null) {
        throw failure;
      }
      return new GeminiResponse(reply, request.model(), 0, reply.length());
    }

    @Override
    public String describe() {
      return "recording client";
    }

    String lastPrompt() {
      return requests.getLast().promptText();
    }
  }

  private static NotesDocument format(RecordingClient client, String... transcript) {
    return new GeminiFormatter(client, "gemini-test-model")
        .format(TestTranscripts.transcript(transcript), FormattingOptions.defaults());
  }

  @Test
  @DisplayName("Markdown from the model becomes note sections and the formatter is named")
  void formatsMarkdownFromTheModel() {
    RecordingClient client =
        new RecordingClient()
            .replying(
                """
                # Caching lecture

                ## Summary

                The lecture explains caching.

                ## Actions

                - Instrument the hit rate""");

    NotesDocument document = format(client, "the cache pays off when data repeats");

    assertThat(document.title()).isEqualTo("Caching lecture");
    assertThat(document.formatter()).isEqualTo("gemini");
    assertThat(document.sections())
        .extracting(section -> section.heading())
        .containsExactly("Summary", "Actions");
    assertThat(document.transcript()).isEqualTo("the cache pays off when data repeats");
  }

  @Test
  @DisplayName("the prompt carries the transcript and the no-invention rules")
  void sendsAStrictPrompt() {
    RecordingClient client = new RecordingClient().replying("## Notes\n\nSomething.");

    format(client, "expiry keeps a cache honest");

    assertThat(client.lastPrompt())
        .contains("expiry keeps a cache honest")
        .contains("Never add facts")
        .contains("Use only what the transcript says");
  }

  @Test
  @DisplayName("no key means offline notes with a line that says so")
  void disabledFallsBackToOfflineNotes() {
    RecordingClient client =
        new RecordingClient().failing(new GeminiException.Disabled("no GEMINI_API_KEY is set"));

    NotesDocument document = format(client, "the cache pays off when data repeats");

    assertThat(document.formatter()).contains("gemini skipped");
    assertThat(document.sections().getFirst().heading()).isEqualTo("About these notes");
    assertThat(document.sections().getFirst().paragraphs().getFirst())
        .contains("rule-based and offline");
    assertThat(document.transcript()).contains("the cache pays off");
  }

  @Test
  @DisplayName("an exhausted cap falls back instead of retrying")
  void budgetExceededFallsBack() {
    RecordingClient client =
        new RecordingClient()
            .failing(
                new GeminiException.BudgetExceeded("request cap reached (20 requests per run)"));

    NotesDocument document = format(client, "the cache pays off when data repeats");

    assertThat(document.formatter()).contains("gemini skipped");
    assertThat(document.sections().getFirst().paragraphs().getFirst())
        .contains("request cap reached");
  }

  @Test
  @DisplayName("an empty reply falls back rather than producing an empty note")
  void emptyReplyFallsBack() {
    NotesDocument document =
        format(new RecordingClient().replying("   "), "the cache pays off when data repeats");

    assertThat(document.formatter()).contains("gemini skipped");
    assertThat(document.sections().getFirst().paragraphs().getFirst())
        .contains("no usable structure");
  }

  @Test
  @DisplayName("a long transcript is truncated to fit the cap instead of being refused")
  void truncatesLongTranscripts() {
    RecordingClient client = new RecordingClient().replying("## Notes\n\nFine.");
    String longSentence = "the cache pays off when the same data is requested more than once";

    new GeminiFormatter(client, "gemini-test-model", 200, new RuleBasedFormatter())
        .format(
            TestTranscripts.transcript(
                longSentence, longSentence, longSentence, longSentence, longSentence),
            FormattingOptions.defaults());

    assertThat(client.lastPrompt()).contains("transcript truncated here");
    // The instructions are fixed; the part that must respect the cap is the transcript itself.
    String sentTranscript =
        client.lastPrompt().substring(client.lastPrompt().indexOf("Transcript:") + 11).strip();
    assertThat(sentTranscript.length()).isLessThanOrEqualTo(200);
    assertThat(sentTranscript).endsWith("[…transcript truncated here…]");
  }

  @Test
  void anEmptyTranscriptNeverCallsTheModel() {
    RecordingClient client = new RecordingClient().replying("## Notes\n\nFine.");

    NotesDocument document = format(client);

    assertThat(client.requests).isEmpty();
    assertThat(document.formatter()).isEqualTo("rule-based");
  }

  @Test
  void formatterNameIsGemini() {
    assertThat(new GeminiFormatter(new RecordingClient().replying("x"), "m").name())
        .isEqualTo("gemini");
  }
}
