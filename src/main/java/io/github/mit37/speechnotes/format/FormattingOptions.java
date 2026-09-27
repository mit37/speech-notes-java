package io.github.mit37.speechnotes.format;

/** Tunables for the note formatters. Defaults are what the CLI uses. */
public record FormattingOptions(
    double pauseParagraphSeconds,
    int maxParagraphWords,
    int maxKeyTerms,
    int minKeyTermOccurrences,
    boolean extractActions,
    String title) {

  public FormattingOptions {
    if (pauseParagraphSeconds <= 0) {
      throw new IllegalArgumentException("pauseParagraphSeconds must be positive");
    }
    if (maxParagraphWords <= 0 || maxKeyTerms < 0 || minKeyTermOccurrences < 1) {
      throw new IllegalArgumentException("paragraph and key-term limits must be positive");
    }
  }

  public static FormattingOptions defaults() {
    return new FormattingOptions(1.0, 55, 8, 2, true, null);
  }

  public FormattingOptions withTitle(String newTitle) {
    return new FormattingOptions(
        pauseParagraphSeconds,
        maxParagraphWords,
        maxKeyTerms,
        minKeyTermOccurrences,
        extractActions,
        newTitle);
  }

  /** Title to use when the caller did not supply one. */
  public String titleOr(String fallback) {
    return title == null || title.isBlank() ? fallback : title;
  }
}
