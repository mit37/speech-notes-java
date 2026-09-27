package io.github.mit37.speechnotes;

import io.github.mit37.speechnotes.transcribe.Transcript;
import io.github.mit37.speechnotes.transcribe.TranscriptEvent;
import io.github.mit37.speechnotes.transcribe.TranscriptStore;
import java.time.Instant;

/**
 * Test helper: builds transcripts without audio, so formatting tests stay fast and deterministic.
 */
public final class TestTranscripts {

  private TestTranscripts() {}

  /** One final per sentence, each 2 seconds long, back to back, with no pauses. */
  public static TranscriptStore store(String... sentences) {
    return storeWithPauses(0, sentences);
  }

  /** One final per sentence, 2 seconds long, separated by {@code pauseSeconds} of silence. */
  public static TranscriptStore storeWithPauses(double pauseSeconds, String... sentences) {
    TranscriptStore store = new TranscriptStore();
    double cursor = 0;
    for (String sentence : sentences) {
      double start = cursor;
      double end = start + 2;
      store.accept(new TranscriptEvent.Final(start, end, sentence));
      cursor = end + pauseSeconds;
    }
    return store;
  }

  /** A store with an explicit event list, including partials. */
  public static TranscriptStore storeOf(TranscriptEvent... events) {
    TranscriptStore store = new TranscriptStore();
    for (TranscriptEvent event : events) {
      store.accept(event);
    }
    return store;
  }

  public static TranscriptEvent.Final finalEvent(String text, double start, double end) {
    return new TranscriptEvent.Final(start, end, text);
  }

  public static Transcript transcript(String... sentences) {
    TranscriptStore store = store(sentences);
    return new Transcript(
        store, "lecture.wav", "Vosk (test)", store.durationSeconds(), Instant.EPOCH);
  }

  public static Transcript transcriptOf(TranscriptStore store) {
    return new Transcript(
        store, "lecture.wav", "Vosk (test)", store.durationSeconds(), Instant.EPOCH);
  }
}
