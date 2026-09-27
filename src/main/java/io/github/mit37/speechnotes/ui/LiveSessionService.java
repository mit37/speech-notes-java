package io.github.mit37.speechnotes.ui;

import io.github.mit37.speechnotes.audio.AudioSource;
import io.github.mit37.speechnotes.audio.AudioSourceFactory;
import io.github.mit37.speechnotes.audio.PausableAudioSource;
import io.github.mit37.speechnotes.format.Formatter;
import io.github.mit37.speechnotes.format.FormattingOptions;
import io.github.mit37.speechnotes.format.RuleBasedFormatter;
import io.github.mit37.speechnotes.notes.NoteAttachment;
import io.github.mit37.speechnotes.notes.NotesDocument;
import io.github.mit37.speechnotes.transcribe.Transcriber;
import io.github.mit37.speechnotes.transcribe.Transcript;
import io.github.mit37.speechnotes.transcribe.TranscriptEvent;
import io.github.mit37.speechnotes.transcribe.TranscriptStore;
import io.github.mit37.speechnotes.transcribe.VoskTranscriber;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The real session: microphone in, notes out, on a background thread so the window keeps painting.
 *
 * <p>State changes and transcript progress go out as {@link SessionUpdate}s. Pausing is handled by
 * {@link PausableAudioSource}, stopping by closing the source — which makes the reader see the end
 * of the stream and lets the worker format whatever was captured.
 */
public final class LiveSessionService implements SessionService {

  /** How often partial results may trigger an update; finals always update immediately. */
  private static final long PARTIAL_UPDATE_INTERVAL_MILLIS = 150;

  private final TranscriberFactory transcriberFactory;
  private final AudioSourceFactory sourceFactory;
  private final String sourceDescription;
  private final FormattingOptions formattingOptions;
  private final Formatter formatter;
  private final List<Consumer<SessionUpdate>> listeners = new CopyOnWriteArrayList<>();
  private final List<NoteAttachment> attachments = new CopyOnWriteArrayList<>();

  private volatile SessionState state = SessionState.IDLE;
  private volatile NotesDocument notes;
  private volatile PausableAudioSource pausable;
  private volatile Thread worker;
  private volatile TranscriptStore store;
  private long lastPartialUpdateNanos;

  public LiveSessionService(
      Path modelDir, AudioSourceFactory sourceFactory, FormattingOptions formattingOptions) {
    this(modelDir, sourceFactory, "microphone", formattingOptions);
  }

  public LiveSessionService(
      Path modelDir,
      AudioSourceFactory sourceFactory,
      String sourceDescription,
      FormattingOptions formattingOptions) {
    this(modelDir, sourceFactory, sourceDescription, formattingOptions, new RuleBasedFormatter());
  }

  public LiveSessionService(
      Path modelDir,
      AudioSourceFactory sourceFactory,
      String sourceDescription,
      FormattingOptions formattingOptions,
      Formatter formatter) {
    this(
        () -> VoskTranscriber.load(modelDir),
        sourceFactory,
        sourceDescription,
        formattingOptions,
        formatter);
  }

  /** Full constructor: tests bring their own transcriber so no model and no device are needed. */
  public LiveSessionService(
      TranscriberFactory transcriberFactory,
      AudioSourceFactory sourceFactory,
      String sourceDescription,
      FormattingOptions formattingOptions) {
    this(
        transcriberFactory,
        sourceFactory,
        sourceDescription,
        formattingOptions,
        new RuleBasedFormatter());
  }

  public LiveSessionService(
      TranscriberFactory transcriberFactory,
      AudioSourceFactory sourceFactory,
      String sourceDescription,
      FormattingOptions formattingOptions,
      Formatter formatter) {
    this.transcriberFactory = transcriberFactory;
    this.sourceFactory = sourceFactory;
    this.sourceDescription = sourceDescription;
    this.formattingOptions = formattingOptions;
    this.formatter = formatter;
  }

  @Override
  public SessionState state() {
    return state;
  }

  @Override
  public void addListener(Consumer<SessionUpdate> listener) {
    listeners.add(listener);
  }

  @Override
  public synchronized void start() {
    if (state.isRunning()) {
      return;
    }
    setState(SessionState.STARTING, "Opening the input and loading the model (offline)…");
    worker = Thread.ofVirtual().name("speech-notes-session").start(this::runSession);
  }

  @Override
  public void pause() {
    PausableAudioSource current = pausable;
    if (current != null && state == SessionState.RECORDING) {
      current.pause();
      setState(SessionState.PAUSED, "Paused. Nothing is being recorded.");
    }
  }

  @Override
  public void resume() {
    PausableAudioSource current = pausable;
    if (current != null && state == SessionState.PAUSED) {
      current.resume();
      setState(SessionState.RECORDING, "Recording again.");
    }
  }

  @Override
  public void stop() {
    PausableAudioSource current = pausable;
    if (current == null) {
      return;
    }
    setState(SessionState.FORMATTING, "Finishing up and building the notes…");
    // Ends the stream cleanly; the worker closes the device itself when it is done reading.
    current.stop();
  }

  @Override
  public NotesDocument notes() {
    return notes;
  }

  @Override
  public void recordScreenshot(Path image, String question, String answer) {
    TranscriptStore current = store;
    double atSeconds = current == null ? 0 : current.durationSeconds();
    attachments.add(new NoteAttachment(atSeconds, image, question, answer));
  }

  @Override
  public String describeSource() {
    return sourceDescription;
  }

  @Override
  public void close() {
    stop();
    Thread current = worker;
    if (current != null) {
      try {
        current.join(TimeUnit.SECONDS.toMillis(5));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void runSession() {
    TranscriptStore store = new TranscriptStore();
    this.store = store;
    attachments.clear();
    try (Transcriber transcriber = transcriberFactory.open();
        AudioSource source = sourceFactory.open()) {
      PausableAudioSource pausableSource = new PausableAudioSource(source);
      pausable = pausableSource;
      setState(SessionState.RECORDING, "Recording " + source.description() + " — offline.");

      transcriber.transcribe(pausableSource, event -> publish(store, event));

      setState(SessionState.FORMATTING, "Building the notes…");
      NotesDocument built =
          formatter.format(
              new Transcript(
                  store,
                  source.description(),
                  transcriber.describe(),
                  store.durationSeconds(),
                  Instant.now()),
              formattingOptions);
      NotesDocument document =
          attachments.isEmpty() ? built : built.withAttachments(List.copyOf(attachments));
      notes = document;
      listeners.forEach(
          listener ->
              listener.accept(
                  new SessionUpdate(
                      SessionState.DONE,
                      document.transcript(),
                      "",
                      summarise(document),
                      document.audioSeconds(),
                      document.actions().size(),
                      document)));
    } catch (Exception e) {
      setState(SessionState.FAILED, friendlyFailure(e));
    } finally {
      pausable = null;
      this.store = null;
    }
  }

  private void publish(TranscriptStore store, TranscriptEvent event) {
    store.accept(event);
    long now = System.nanoTime();
    boolean isFinal = event.isFinal();
    if (!isFinal
        && now - lastPartialUpdateNanos
            < TimeUnit.MILLISECONDS.toNanos(PARTIAL_UPDATE_INTERVAL_MILLIS)) {
      return;
    }
    lastPartialUpdateNanos = now;
    listeners.forEach(
        listener ->
            listener.accept(
                SessionUpdate.recording(
                    store.text(), store.partialText(), store.durationSeconds(), -1)));
  }

  private static String summarise(NotesDocument document) {
    return String.format(
        java.util.Locale.ROOT,
        "Done: %d words, %d action item(s) — %s",
        document.wordCount(),
        document.actions().size(),
        document.formatter());
  }

  private static String friendlyFailure(Exception e) {
    String message = e.getMessage();
    return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
  }

  private void setState(SessionState newState, String message) {
    state = newState;
    listeners.forEach(listener -> listener.accept(SessionUpdate.of(newState, message)));
  }
}
