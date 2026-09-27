package io.github.mit37.speechnotes.transcribe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.mit37.speechnotes.TestTranscripts;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TranscriptStoreTest {

  @Test
  @DisplayName("events are kept in arrival order and the snapshots cannot be modified")
  void keepsEventsInOrder() {
    TranscriptStore store =
        TestTranscripts.storeOf(
            new TranscriptEvent.Partial(0, 1, "cach"),
            new TranscriptEvent.Final(0, 1.5, "caching works"),
            new TranscriptEvent.Partial(1.5, 2, "becaus"));

    assertThat(store.events()).hasSize(3);
    assertThat(store.events().get(0).text()).isEqualTo("cach");
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> store.events().clear());
  }

  @Test
  @DisplayName("the text view contains finals only")
  void textJoinsFinals() {
    TranscriptStore store =
        TestTranscripts.storeOf(
            new TranscriptEvent.Partial(0, 1, "cach"),
            new TranscriptEvent.Final(0, 1.5, "caching works"),
            new TranscriptEvent.Partial(1.5, 2, "becaus"),
            new TranscriptEvent.Final(1.5, 3, "because it is asked twice"));

    assertThat(store.text()).isEqualTo("caching works because it is asked twice");
    assertThat(store.finals()).hasSize(2);
    assertThat(store.wordCount()).isEqualTo(7);
  }

  @Test
  @DisplayName("the last partial is available until a final replaces it")
  void tracksThePendingPartial() {
    TranscriptStore store = TestTranscripts.storeOf(new TranscriptEvent.Partial(0, 1, "exp"));
    assertThat(store.partialText()).isEqualTo("exp");
    assertThat(store.hasPartial()).isTrue();

    store.accept(new TranscriptEvent.Final(0, 1.2, "expiry rule"));
    assertThat(store.partialText()).isEmpty();
    assertThat(store.hasPartial()).isFalse();
    assertThat(store.text()).isEqualTo("expiry rule");
  }

  @Test
  void durationIsTheEndOfTheLastEvent() {
    TranscriptStore store = TestTranscripts.store("a", "b");

    assertThat(store.durationSeconds()).isEqualTo(4.0);
  }

  @Test
  @DisplayName("listeners see every event and can unsubscribe")
  void notifiesListeners() {
    TranscriptStore store = new TranscriptStore();
    List<String> seen = new ArrayList<>();
    AutoCloseable subscription = store.addListener(event -> seen.add(event.text()));

    store.accept(new TranscriptEvent.Final(0, 1, "one"));

    try {
      subscription.close();
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    store.accept(new TranscriptEvent.Final(1, 2, "two"));

    assertThat(seen).containsExactly("one");
  }

  @Test
  @DisplayName("clearing forgets everything, which is what starting a new recording needs")
  void clearResetsEverything() {
    TranscriptStore store = TestTranscripts.store("one", "two");

    store.clear();

    assertThat(store.events()).isEmpty();
    assertThat(store.text()).isEmpty();
    assertThat(store.wordCount()).isZero();
    assertThat(store.durationSeconds()).isZero();
  }

  @Test
  @DisplayName("events pushed from many threads are all kept")
  void isSafeAcrossThreads() throws InterruptedException {
    TranscriptStore store = new TranscriptStore();
    List<TranscriptEvent> received = new CopyOnWriteArrayList<>();
    store.addListener(received::add);
    int perThread = 200;
    int threads = 4;

    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      for (int thread = 0; thread < threads; thread++) {
        int id = thread;
        pool.submit(
            () -> {
              for (int i = 0; i < perThread; i++) {
                store.accept(new TranscriptEvent.Final(i, i + 0.5, "thread" + id + " item" + i));
              }
            });
      }
      pool.shutdown();
      assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(store.events()).hasSize(threads * perThread);
    assertThat(received).hasSize(threads * perThread);
  }
}
