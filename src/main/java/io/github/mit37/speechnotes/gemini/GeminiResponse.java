package io.github.mit37.speechnotes.gemini;

/** What came back: the text, plus what the API said it cost. */
public record GeminiResponse(
    String text, String model, int promptCharacters, int outputCharacters) {

  public GeminiResponse {
    text = text == null ? "" : text;
  }

  public boolean isBlank() {
    return text.isBlank();
  }
}
