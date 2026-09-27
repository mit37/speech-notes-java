package io.github.mit37.speechnotes.notes;

import java.nio.file.Path;

/**
 * A screenshot question and answer pinned to a point in the notes (PRD G4).
 *
 * <p>The answer is {@code null} while the question is still being asked, and the {@code answer}
 * field is what an offline build leaves empty rather than inventing anything.
 */
public record NoteAttachment(double atSeconds, Path image, String question, String answer) {

  public boolean isAnswered() {
    return answer != null && !answer.isBlank();
  }
}
