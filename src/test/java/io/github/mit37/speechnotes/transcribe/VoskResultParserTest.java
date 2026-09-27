package io.github.mit37.speechnotes.transcribe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VoskResultParserTest {

  private final VoskResultParser parser = new VoskResultParser();

  @Test
  @DisplayName("a partial result carries the caller's window, since Vosk sends no times")
  void parsesPartial() {
    String json = "{\"partial\" : \"caching in web sy\"}";

    TranscriptEvent.Partial partial = parser.partial(json, 4.0, 6.5).orElseThrow();

    assertThat(partial.text()).isEqualTo("caching in web sy");
    assertThat(partial.startSeconds()).isEqualTo(4.0);
    assertThat(partial.endSeconds()).isEqualTo(6.5);
    assertThat(partial.isFinal()).isFalse();
  }

  @Test
  @DisplayName("a final result uses Vosk's own word timestamps when they are present")
  void parsesFinalWithWordTimes() {
    String json =
        """
        {"result" : [
           {"conf" : 1.0, "end" : 0.87, "start" : 0.36, "word" : "caching"},
           {"conf" : 1.0, "end" : 1.36, "start" : 0.87, "word" : "works"}
         ], "text" : "caching works"}
        """;

    TranscriptEvent.Final event = parser.finalResult(json, 0.0, 99.0).orElseThrow();

    assertThat(event.text()).isEqualTo("caching works");
    assertThat(event.startSeconds()).isEqualTo(0.36);
    assertThat(event.endSeconds()).isEqualTo(1.36);
    assertThat(event.isFinal()).isTrue();
  }

  @Test
  @DisplayName("a final result without a word list falls back to the audio window")
  void parsesFinalWithoutWordTimes() {
    TranscriptEvent.Final event =
        parser.finalResult("{\"text\" : \"measure the hit rate\"}", 12.0, 14.5).orElseThrow();

    assertThat(event.text()).isEqualTo("measure the hit rate");
    assertThat(event.startSeconds()).isEqualTo(12.0);
    assertThat(event.endSeconds()).isEqualTo(14.5);
  }

  @Test
  @DisplayName("a backwards window is repaired instead of producing negative duration")
  void repairsBackwardsWindow() {
    TranscriptEvent.Final event =
        parser.finalResult("{\"text\" : \"hello\"}", 5.0, 3.0).orElseThrow();

    assertThat(event.endSeconds()).isEqualTo(5.0);
  }

  @Test
  void blankAndMissingTextProduceNothing() {
    assertThat(parser.partial("{\"partial\" : \"   \"}", 0, 1)).isEmpty();
    assertThat(parser.partial("{}", 0, 1)).isEmpty();
    assertThat(parser.finalResult("{\"text\" : \"\"}", 0, 1)).isEmpty();
    assertThat(parser.finalResult("{}", 0, 1)).isEmpty();
  }

  @Test
  @DisplayName("unknown fields are ignored rather than fatal")
  void ignoresExtraFields() {
    TranscriptEvent.Final event =
        parser
            .finalResult("{\"text\" : \"ok\", \"speaker\" : 3, \"words\" : []}", 0, 1)
            .orElseThrow();

    assertThat(event.text()).isEqualTo("ok");
  }

  @Test
  @DisplayName("unreadable JSON raises a transcription error, not a Jackson exception")
  void rejectsMalformedJson() {
    assertThatExceptionOfType(TranscriptionException.class)
        .isThrownBy(() -> parser.finalResult("{not json", 0, 1))
        .withMessageContaining("unreadable JSON");

    assertThatExceptionOfType(TranscriptionException.class)
        .isThrownBy(() -> parser.finalResult("[]", 0, 1))
        .withMessageContaining("not an object");

    assertThatExceptionOfType(TranscriptionException.class)
        .isThrownBy(() -> parser.partial("", 0, 1))
        .withMessageContaining("empty result");
  }
}
