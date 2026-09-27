package io.github.mit37.speechnotes.gemini;

import java.util.List;

/** One request: which model, which parts, and how much output is acceptable. */
public record GeminiRequest(String model, List<GeminiPart> parts, int maxOutputTokens) {

  public GeminiRequest {
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("a model id is required");
    }
    parts = List.copyOf(parts);
    if (parts.isEmpty()) {
      throw new IllegalArgumentException("a request needs at least one part");
    }
  }

  public static GeminiRequest text(String model, String prompt) {
    return new GeminiRequest(model, List.of(new GeminiPart.Text(prompt)), 4096);
  }

  public static GeminiRequest withImage(
      String model, String prompt, String mediaType, byte[] image) {
    return new GeminiRequest(
        model, List.of(new GeminiPart.Text(prompt), new GeminiPart.Image(mediaType, image)), 1024);
  }

  public GeminiRequest withMaxOutputTokens(int tokens) {
    return new GeminiRequest(model, parts, tokens);
  }

  /** What this request costs against the run's character cap. */
  public int characterCount() {
    return parts.stream().mapToInt(GeminiPart::characterCount).sum();
  }

  /** The concatenated text parts, which is what a log or a dry run shows. */
  public String promptText() {
    StringBuilder text = new StringBuilder();
    for (GeminiPart part : parts) {
      if (part instanceof GeminiPart.Text textPart) {
        if (!text.isEmpty()) {
          text.append("\n\n");
        }
        text.append(textPart.text());
      }
    }
    return text.toString();
  }
}
