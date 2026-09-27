package io.github.mit37.speechnotes.gemini;

/**
 * The one door to the Gemini API.
 *
 * <p>Everything above this interface is testable with a fake client, and the disabled
 * implementation below is what runs when there is no key: it says so clearly and never touches the
 * network.
 */
public interface GeminiClient {

  /**
   * Sends a request.
   *
   * @throws GeminiException.Disabled when the feature is switched off
   * @throws GeminiException.BudgetExceeded when a hard cap would be passed
   * @throws GeminiException when the API refuses or the call fails
   */
  GeminiResponse generate(GeminiRequest request);

  /** What this client is configured to do, for logs — never includes the key. */
  String describe();

  /** Off by default: no key, no network, and a message that says exactly that. */
  static GeminiClient disabled() {
    return disabled("no " + GeminiConfig.KEY_ENV + " is set, so Gemini features are off");
  }

  static GeminiClient disabled(String reason) {
    return new GeminiClient() {
      @Override
      public GeminiResponse generate(GeminiRequest request) {
        throw new GeminiException.Disabled(reason);
      }

      @Override
      public String describe() {
        return "gemini(disabled: " + reason + ")";
      }
    };
  }
}
