package io.github.mit37.speechnotes.transcribe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The session transcript: every {@link TranscriptEvent} in order, plus the derived views the
 * formatter and the UI need.
 *
 * <p>Events are appended from whichever thread is reading audio; listeners are notified in append
 * order. Nothing here is device-specific, so the whole thing is unit-testable.
 */
public final class TranscriptStore {

  private final List<TranscriptEvent> events = new ArrayList<>();
  private final List<TranscriptEvent.Final> finals = new ArrayList<>();
  private final List<Consumer<TranscriptEvent>> listeners = new CopyOnWriteArrayList<>();

  /** Appends an event and notifies listeners. */
  public void accept(TranscriptEvent event) {
    synchronized (this) {
      events.add(event);
      if (event instanceof TranscriptEvent.Final finalEvent) {
        finals.add(finalEvent);
      }
    }
    for (Consumer<TranscriptEvent> listener : listeners) {
      listener.accept(event);
    }
  }

  /** Every event, partials included, in the order it arrived. */
  public synchronized List<TranscriptEvent> events() {
    return List.copyOf(events);
  }

  /** Only the final events: text the recogniser will not revise. */
  public synchronized List<TranscriptEvent.Final> finals() {
    return List.copyOf(finals);
  }

  /** The last partial text seen, or an empty string when the last event was a final. */
  public synchronized String partialText() {
    for (int i = events.size() - 1; i >= 0; i--) {
      TranscriptEvent event = events.get(i);
      if (event.isFinal()) {
        return "";
      }
      return event.text();
    }
    return "";
  }

  /** The final text, joined with single spaces. */
  public synchronized String text() {
    StringBuilder text = new StringBuilder();
    for (TranscriptEvent.Final event : finals) {
      if (!text.isEmpty()) {
        text.append(' ');
      }
      text.append(event.text());
    }
    return text.toString();
  }

  /** Seconds of audio covered by the transcript so far. */
  public synchronized double durationSeconds() {
    double duration = 0;
    for (TranscriptEvent event : events) {
      duration = Math.max(duration, event.endSeconds());
    }
    return duration;
  }

  /** Words in the final text. */
  public synchronized int wordCount() {
    String text = text();
    return text.isBlank() ? 0 : text.split("\\s+").length;
  }

  /** True while a partial is waiting to be replaced by a final. */
  public synchronized boolean hasPartial() {
    return !events.isEmpty() && !events.get(events.size() - 1).isFinal();
  }

  /** Registers a listener for later events; returns a handle that can stop the notifications. */
  public AutoCloseable addListener(Consumer<TranscriptEvent> listener) {
    listeners.add(listener);
    return () -> listeners.remove(listener);
  }

  /** Forgets everything; used when a new recording starts. */
  public void clear() {
    synchronized (this) {
      events.clear();
      finals.clear();
    }
  }
}
