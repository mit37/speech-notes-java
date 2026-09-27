package io.github.mit37.speechnotes.qa;

import io.github.mit37.speechnotes.format.GeminiPrompts;
import io.github.mit37.speechnotes.gemini.GeminiClient;
import io.github.mit37.speechnotes.gemini.GeminiConfig;
import io.github.mit37.speechnotes.gemini.GeminiException;
import io.github.mit37.speechnotes.gemini.GeminiRequest;
import io.github.mit37.speechnotes.gemini.GeminiResponse;
import io.github.mit37.speechnotes.gemini.HttpGeminiClient;
import io.github.mit37.speechnotes.gemini.RequestBudget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Screenshot Q&A through Gemini (PRD G4).
 *
 * <p>Disabled unless a key is configured, and honest about it: {@link #unavailableReason()} says
 * exactly why, which is what the UI shows instead of a spinner that never finishes.
 */
public final class GeminiQaService implements QAService {

  /** Images above this size are refused rather than sent, because the cap is in characters. */
  private static final long MAX_IMAGE_BYTES = 4L * 1024 * 1024;

  private final GeminiClient client;
  private final String model;
  private final String unavailableReason;

  public GeminiQaService(GeminiClient client, String model, GeminiConfig config) {
    this.client = client;
    this.model = model;
    this.unavailableReason =
        config.isEnabled()
            ? ""
            : "screenshot Q&A needs "
                + GeminiConfig.KEY_ENV
                + "; without it, the question stays unanswered rather than guessed offline";
  }

  /** The service used when there is no key: it refuses, with a message a person can act on. */
  public static GeminiQaService disabled(GeminiConfig config) {
    return new GeminiQaService(GeminiClient.disabled(), config.model(), config);
  }

  /** The service the window should use: enabled with a key, refusing clearly without one. */
  public static GeminiQaService fromEnvironment() {
    GeminiConfig config = GeminiConfig.fromEnvironment();
    if (!config.isEnabled()) {
      return disabled(config);
    }
    return new GeminiQaService(
        new HttpGeminiClient(config, RequestBudget.from(config)), config.model(), config);
  }

  @Override
  public boolean isAvailable() {
    return unavailableReason.isEmpty();
  }

  @Override
  public String unavailableReason() {
    return unavailableReason;
  }

  @Override
  public String answer(Path image, String question) throws QaException {
    if (!isAvailable()) {
      throw new QaException(unavailableReason);
    }
    if (question == null || question.isBlank()) {
      throw new QaException("ask a question about the screenshot first");
    }
    byte[] bytes;
    try {
      if (!Files.isRegularFile(image)) {
        throw new QaException("screenshot not found: " + image);
      }
      if (Files.size(image) > MAX_IMAGE_BYTES) {
        throw new QaException(
            "screenshot is larger than "
                + (MAX_IMAGE_BYTES / (1024 * 1024))
                + " MB, so it was not sent");
      }
      bytes = Files.readAllBytes(image);
    } catch (IOException e) {
      throw new QaException("cannot read " + image + ": " + e.getMessage(), e);
    }
    try {
      GeminiResponse response =
          client.generate(
              GeminiRequest.withImage(
                  model, GeminiPrompts.screenshotQuestion(question), mediaTypeFor(image), bytes));
      if (response.isBlank()) {
        throw new QaException("Gemini returned an empty answer");
      }
      return response.text().trim();
    } catch (GeminiException e) {
      throw new QaException("screenshot Q&A failed: " + e.getMessage(), e);
    }
  }

  private static String mediaTypeFor(Path image) {
    String name = image.getFileName().toString().toLowerCase(Locale.ROOT);
    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
      return "image/jpeg";
    }
    if (name.endsWith(".webp")) {
      return "image/webp";
    }
    return "image/png";
  }
}
