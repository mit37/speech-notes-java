package io.github.mit37.speechnotes.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mit37.speechnotes.audio.AudioSourceFactory;
import io.github.mit37.speechnotes.audio.FakeAudioSource;
import io.github.mit37.speechnotes.export.MarkdownExporter;
import io.github.mit37.speechnotes.format.FormattingOptions;
import io.github.mit37.speechnotes.notes.NoteAttachment;
import io.github.mit37.speechnotes.notes.NotesDocument;
import io.github.mit37.speechnotes.transcribe.FakeTranscriber;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The session state machine: start, pause, resume, stop, notes, failure — all without hardware. */
@org.junit.jupiter.api.Timeout(value = 120, unit = java.util.concurrent.TimeUnit.SECONDS)
class LiveSessionServiceTest {

  private static FakeTranscriber transcriber() {
    return new FakeTranscriber()
        .says("the cache pays off when the same data repeats", 0, 2)
        .says("todo instrument the hit rate", 2, 4)
        .saysPartially("the cache pays", 0, 1);
  }

  private static LiveSessionService session(AudioSourceFactory sourceFactory) {
    return new LiveSessionService(
        LiveSessionServiceTest::transcriber,
        sourceFactory,
        "fake microphone",
        FormattingOptions.defaults().withTitle("Live session"));
  }

  private static void awaitState(List<SessionUpdate> updates, SessionState expected, long seconds) {
    long deadline = System.nanoTime() + seconds * 1_000_000_000L;
    while (System.nanoTime() < deadline) {
      if (updates.stream().anyMatch(update -> update.state() == expected)) {
        return;
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
    throw new AssertionError(
        "never reached "
            + expected
            + "; saw "
            + updates.stream().map(SessionUpdate::state).toList());
  }

  @Test
  @DisplayName("recording a fake source produces notes with actions and key terms")
  void recordsToNotes() {
    LiveSessionService service = session(() -> FakeAudioSource.tone(440, 5));
    List<SessionUpdate> updates = new CopyOnWriteArrayList<>();
    service.addListener(updates::add);

    service.start();
    awaitState(updates, SessionState.DONE, 20);

    NotesDocument notes = service.notes();
    assertThat(notes).isNotNull();
    assertThat(notes.title()).isEqualTo("Live session");
    // The notes name the audio they came from, which for a live session is the device itself.
    assertThat(notes.source()).contains("fake tone");
    assertThat(notes.transcript()).contains("the cache pays off");
    assertThat(notes.actions()).hasSize(1);
    assertThat(updates).anyMatch(update -> update.state() == SessionState.RECORDING);
    assertThat(updates)
        .anyMatch(update -> update.partial() != null && update.partial().contains("pays"));
    service.close();
  }

  @Test
  @DisplayName("pause stops the recording and resume continues it")
  void pauseAndResume() {
    LiveSessionService service = session(() -> new SlowAudioSource(6, 0.1, 10));
    List<SessionUpdate> updates = new CopyOnWriteArrayList<>();
    service.addListener(updates::add);

    service.start();
    awaitState(updates, SessionState.RECORDING, 20);

    service.pause();
    assertThat(service.state()).isEqualTo(SessionState.PAUSED);
    service.resume();
    assertThat(service.state()).isEqualTo(SessionState.RECORDING);

    service.stop();
    awaitState(updates, SessionState.DONE, 30);
    assertThat(service.notes()).isNotNull();
    service.close();
  }

  @Test
  @DisplayName("stopping early still produces notes from what was captured")
  void stopEarlyStillProducesNotes() {
    LiveSessionService service = session(() -> new SlowAudioSource(30, 0.1, 10));
    List<SessionUpdate> updates = new CopyOnWriteArrayList<>();
    service.addListener(updates::add);

    service.start();
    awaitState(updates, SessionState.RECORDING, 20);
    service.stop();

    awaitState(updates, SessionState.DONE, 30);
    assertThat(service.notes()).isNotNull();
    service.close();
  }

  @Test
  @DisplayName("a model that will not load shows up as a failure with its reason")
  void failureIsReported() {
    LiveSessionService service =
        new LiveSessionService(
            () -> {
              throw new IOException("Vosk model not found in models/nope");
            },
            () -> FakeAudioSource.tone(440, 1),
            "fake microphone",
            FormattingOptions.defaults());
    List<SessionUpdate> updates = new CopyOnWriteArrayList<>();
    service.addListener(updates::add);

    service.start();
    awaitState(updates, SessionState.FAILED, 20);

    assertThat(updates)
        .anyMatch(update -> String.valueOf(update.message()).contains("models/nope"));
    assertThat(service.notes()).isNull();
    service.close();
  }

  @Test
  @DisplayName("a screenshot question asked while recording lands in the notes with its moment")
  void screenshotsAreKeptInTheNotes() {
    LiveSessionService service = session(() -> new SlowAudioSource(30, 0.1, 10));
    List<SessionUpdate> updates = new CopyOnWriteArrayList<>();
    service.addListener(updates::add);

    service.start();
    awaitState(updates, SessionState.RECORDING, 20);
    Path image = Path.of("build", "tmp", "session-shots", "shot-001.png");
    service.recordScreenshot(image, "Which axis is time?", "The x axis.");
    service.recordScreenshot(image, "How big is the cache?", null);
    service.stop();
    awaitState(updates, SessionState.DONE, 30);

    NotesDocument notes = service.notes();
    assertThat(notes.attachments()).hasSize(2);
    NoteAttachment answered = notes.attachments().getFirst();
    assertThat(answered.image()).isEqualTo(image);
    assertThat(answered.question()).isEqualTo("Which axis is time?");
    assertThat(answered.answer()).isEqualTo("The x axis.");
    // The moment comes from the recording, so the export can put the question next to that part.
    assertThat(answered.atSeconds()).isBetween(0.0, notes.audioSeconds() + 1);
    assertThat(notes.attachments().get(1).isAnswered()).isFalse();
    // And it reaches the export, unanswered questions labelled as unanswered rather than dropped.
    String markdown = new MarkdownExporter().render(notes);
    assertThat(markdown).contains("Which axis is time?").contains("The x axis.");
    assertThat(markdown).contains("How big is the cache?").contains("not answered");
    service.close();
  }

  @Test
  @DisplayName("the source is described the way the status line shows it")
  void describesTheSource() {
    LiveSessionService service = session(() -> FakeAudioSource.tone(440, 1));

    assertThat(service.describeSource()).isEqualTo("fake microphone");
    assertThat(service.state()).isEqualTo(SessionState.IDLE);
    service.close();
  }
}
