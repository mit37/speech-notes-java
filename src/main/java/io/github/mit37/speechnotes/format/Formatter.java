package io.github.mit37.speechnotes.format;

import io.github.mit37.speechnotes.notes.NotesDocument;
import io.github.mit37.speechnotes.transcribe.Transcript;

/**
 * Turns a {@link Transcript} into a {@link NotesDocument}.
 *
 * <p>Two implementations: {@link RuleBasedFormatter}, which is entirely offline and is the default,
 * and the optional Gemini-backed one. Callers can therefore always format something, key or no key.
 */
public interface Formatter {

  /** Short name recorded in the document and shown in the UI, e.g. {@code rule-based}. */
  String name();

  NotesDocument format(Transcript transcript, FormattingOptions options);
}
