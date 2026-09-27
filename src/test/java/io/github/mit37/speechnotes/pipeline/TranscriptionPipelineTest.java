package io.github.mit37.speechnotes.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.mit37.speechnotes.audio.AudioSource;
import io.github.mit37.speechnotes.audio.FakeAudioSource;
import io.github.mit37.speechnotes.format.FormattingOptions;
import io.github.mit37.speechnotes.format.RuleBasedFormatter;
import io.github.mit37.speechnotes.transcribe.FakeTranscriber;
import io.github.mit37.speechnotes.transcribe.TranscriptEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The whole pipeline with a fake source and a scripted transcriber: no microphone, no model, no
 * native library. This is the test that makes microphone mode reviewable in CI.
 */
class TranscriptionPipelineTest {

  private static FakeTranscriber transcriber() {
    return new FakeTranscriber()
        .says("the cache pays off when the same data repeats", 0, 2.5)
        .says("todo instrument the hit rate", 2.5, 5.5)
        .saysPartially("the cache pays", 0, 1.2);
  }

  @Test
  @DisplayName("a fake source and transcriber produce real notes")
  void producesNotesWithoutADevice() throws IOException {
    FakeAudioSource source = FakeAudioSource.tone(440, 6.0);
    try (TranscriptionPipeline pipeline =
        new TranscriptionPipeline(transcriber(), new RuleBasedFormatter())) {
      TranscriptionPipeline.Result result =
          pipeline.run(
              source, "microphone", FormattingOptions.defaults().withTitle("Live session"), null);

      assertThat(result.notes().title()).isEqualTo("Live session");
      assertThat(result.notes().source()).isEqualTo("microphone");
      assertThat(result.notes().engine()).isEqualTo("fake transcriber (test)");
      assertThat(result.notes().transcript()).contains("the cache pays off");
      assertThat(result.notes().actions()).hasSize(1);
      assertThat(result.transcript().audioSeconds()).isEqualTo(5.5);
      assertThat(result.realTimeFactor()).isGreaterThanOrEqualTo(0);
    }
  }

  @Test
  @DisplayName("an observer sees every event the store keeps, partials included")
  void notifiesTheObserver() throws IOException {
    FakeAudioSource source = FakeAudioSource.tone(440, 6.0);
    List<TranscriptEvent> observed = new ArrayList<>();
    try (TranscriptionPipeline pipeline =
        new TranscriptionPipeline(transcriber(), new RuleBasedFormatter())) {
      TranscriptionPipeline.Result result =
          pipeline.run(source, "microphone", FormattingOptions.defaults(), observed::add);

      assertThat(observed).hasSameSizeAs(result.transcript().store().events());
      assertThat(observed).anyMatch(event -> !event.isFinal());
      assertThat(observed).anyMatch(TranscriptEvent::isFinal);
    }
  }

  @Test
  @DisplayName("a formatter that goes wrong does not leave the transcriber open")
  void closesTheTranscriber() throws IOException {
    FakeTranscriber fake = transcriber();
    try (TranscriptionPipeline pipeline =
        new TranscriptionPipeline(fake, new RuleBasedFormatter())) {
      pipeline.run(
          FakeAudioSource.tone(440, 1.0), "microphone", FormattingOptions.defaults(), null);
    }

    assertThat(fake.isClosed()).isTrue();
  }

  @Test
  @DisplayName("a broken audio source surfaces as an I/O error, not a crash")
  void propagatesAudioFailures() {
    AudioSource broken =
        new AudioSource() {
          @Override
          public int read(byte[] buffer) throws IOException {
            throw new IOException("device disappeared");
          }

          @Override
          public String description() {
            return "broken device";
          }

          @Override
          public void close() {}
        };

    try (TranscriptionPipeline pipeline =
        new TranscriptionPipeline(new FakeTranscriber(), new RuleBasedFormatter())) {
      assertThatExceptionOfType(IOException.class)
          .isThrownBy(
              () -> pipeline.run(broken, "broken device", FormattingOptions.defaults(), null))
          .withMessageContaining("device disappeared");
    } catch (IOException e) {
      throw new AssertionError("closing the pipeline should not fail", e);
    }
  }
}
