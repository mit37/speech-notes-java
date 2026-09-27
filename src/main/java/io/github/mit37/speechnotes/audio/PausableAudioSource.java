package io.github.mit37.speechnotes.audio;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Makes any source pausable: while paused it hands over no audio, but it does not end the stream
 * either, so the transcript so far is kept and recording continues where it left off.
 *
 * <p>Implemented by returning {@code 0} (meaning "nothing yet") instead of blocking, so a paused
 * session still responds to Stop immediately.
 *
 * <p>{@link #stop()} ends the stream cleanly instead of throwing: closing a capture device out from
 * under a reader surfaces as an exception, which would lose the transcript. Stopping by returning
 * {@code -1} lets the reader finish normally, format the notes it has, and close the device itself.
 */
public final class PausableAudioSource implements AudioSource {

  private static final long PAUSE_POLL_MILLIS = 20;

  private final AudioSource delegate;
  private final AtomicBoolean paused = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  public PausableAudioSource(AudioSource delegate) {
    this.delegate = delegate;
  }

  public void pause() {
    paused.set(true);
  }

  public void resume() {
    paused.set(false);
  }

  public boolean isPaused() {
    return paused.get();
  }

  /** Ends the stream on the next read, so the reader can finish what it has. */
  public void stop() {
    stopped.set(true);
    paused.set(false);
  }

  public boolean isStopped() {
    return stopped.get();
  }

  @Override
  public int read(byte[] buffer) throws IOException {
    if (stopped.get()) {
      return -1;
    }
    if (paused.get()) {
      try {
        Thread.sleep(PAUSE_POLL_MILLIS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return -1;
      }
      return 0;
    }
    return delegate.read(buffer);
  }

  @Override
  public String description() {
    return delegate.description();
  }

  @Override
  public double durationSeconds() {
    return delegate.durationSeconds();
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }
}
