package io.github.mit37.speechnotes.gemini;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the optional online features need, and nothing they must have.
 *
 * <p>With no key the whole Gemini side is disabled: {@link #isEnabled()} is false, no request is
 * ever made, and callers fall back to the offline formatter (STANDARDS §2). The caps are hard
 * limits per run, so a key cannot run away with a bill.
 *
 * @param apiKey API key, or {@code null} when the feature is off
 * @param model model id, e.g. {@code gemini-2.5-flash} (set by the operator, never guessed here)
 * @param maxRequests hard limit on requests per run
 * @param maxCharacters hard limit on characters sent per run
 * @param timeout per-request timeout
 */
public record GeminiConfig(
    String apiKey, String model, int maxRequests, int maxCharacters, Duration timeout) {

  public static final String KEY_ENV = "GEMINI_API_KEY";
  public static final String MODEL_ENV = "SPEECH_NOTES_GEMINI_MODEL";
  public static final String MAX_REQUESTS_ENV = "SPEECH_NOTES_GEMINI_MAX_REQUESTS";
  public static final String MAX_CHARS_ENV = "SPEECH_NOTES_GEMINI_MAX_CHARS";
  public static final String TIMEOUT_ENV = "SPEECH_NOTES_GEMINI_TIMEOUT_SECONDS";

  private static final String DEFAULT_MODEL = "gemini-2.5-flash";

  public GeminiConfig {
    if (maxRequests < 0 || maxCharacters < 0) {
      throw new IllegalArgumentException("Gemini caps cannot be negative");
    }
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("Gemini timeout must be positive");
    }
  }

  /** Reads the environment, with the caps defaulted rather than unlimited. */
  public static GeminiConfig fromEnvironment() {
    return fromEnvironment(System.getenv());
  }

  public static GeminiConfig fromEnvironment(Map<String, String> environment) {
    String key = blankToNull(environment.get(KEY_ENV));
    String model = valueOrDefault(environment.get(MODEL_ENV), DEFAULT_MODEL);
    int maxRequests = intOrDefault(environment.get(MAX_REQUESTS_ENV), 20);
    int maxCharacters = intOrDefault(environment.get(MAX_CHARS_ENV), 120_000);
    long timeoutSeconds = longOrDefault(environment.get(TIMEOUT_ENV), 60);
    return new GeminiConfig(
        key, model, maxRequests, maxCharacters, Duration.ofSeconds(timeoutSeconds));
  }

  /** The configuration used when nobody set a key: everything off, caps at zero. */
  public static GeminiConfig disabled() {
    return new GeminiConfig(null, DEFAULT_MODEL, 0, 0, Duration.ofSeconds(60));
  }

  public boolean isEnabled() {
    return apiKey != null && !apiKey.isBlank() && maxRequests > 0 && maxCharacters > 0;
  }

  /** The same settings with a different model, for tests and for `--model`-style overrides. */
  public GeminiConfig withModel(String newModel) {
    return new GeminiConfig(apiKey, newModel, maxRequests, maxCharacters, timeout);
  }

  /** Never logs the key itself. */
  public String describe() {
    return String.format(
        Locale.ROOT,
        "gemini(model=%s, maxRequests=%d, maxCharacters=%d, timeout=%ds, key=%s)",
        model,
        maxRequests,
        maxCharacters,
        timeout.toSeconds(),
        isEnabled() ? "set" : "missing");
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String valueOrDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static int intOrDefault(String value, int fallback) {
    try {
      return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static long longOrDefault(String value, long fallback) {
    try {
      return value == null || value.isBlank() ? fallback : Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
