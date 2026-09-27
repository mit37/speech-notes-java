package io.github.mit37.speechnotes.format;

/**
 * The prompts. Kept in one place so they can be read, tested and shown by a dry run.
 *
 * <p>Both prompts say the same two things: stay inside the transcript, and do not invent anything.
 * A formatter that fabricates content is worse than no formatter, especially under a name that
 * claims the notes came from this recording.
 */
public final class GeminiPrompts {

  /** How much transcript is sent in one go; longer transcripts are truncated with a marker. */
  public static final int DEFAULT_MAX_TRANSCRIPT_CHARACTERS = 60_000;

  private GeminiPrompts() {}

  /** The note-formatting prompt. */
  public static String formatting(String transcript) {
    return """
        You are formatting a raw, unpunctuated speech transcript into study notes.

        Rules:
        - Use only what the transcript says. Never add facts, numbers or advice of your own.
        - Write Markdown: one "# " title, then "## " sections such as Summary, Key points,
          Terms and Actions.
        - Bullets are one line each and stay close to the speaker's own words.
        - Keep action items only if the speaker asked for something to be done.
        - If the transcript is too garbled to structure, say so in one line instead of guessing.

        Transcript:
        """
        + transcript;
  }

  /** The screenshot question prompt. */
  public static String screenshotQuestion(String question) {
    return """
        You are answering a question about a screenshot of a lecture slide.

        Rules:
        - Answer from the image and from the notes you are given; if the image does not show it,
          say that plainly.
        - Two sentences at most, no preamble.

        Question:"""
        + " "
        + question;
  }

  /**
   * Cuts a transcript down to {@code maxCharacters}, ending with a marker rather than mid-word.
   *
   * <p>Truncating is what keeps the character cap from turning into a refusal.
   */
  public static String truncate(String transcript, int maxCharacters) {
    if (transcript == null || transcript.length() <= maxCharacters) {
      return transcript == null ? "" : transcript;
    }
    if (maxCharacters <= 32) {
      return transcript.substring(0, Math.max(0, maxCharacters));
    }
    int cut = transcript.lastIndexOf(' ', maxCharacters - 32);
    if (cut <= 0) {
      cut = maxCharacters - 32;
    }
    return transcript.substring(0, cut).trim() + " […transcript truncated here…]";
  }
}
