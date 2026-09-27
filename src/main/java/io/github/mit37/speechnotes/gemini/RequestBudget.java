package io.github.mit37.speechnotes.gemini;

/**
 * A hard spend cap for one run: so many requests and so many characters, and then no more.
 *
 * <p>Checked before anything leaves the machine, so an accidental loop or a huge transcript cannot
 * turn into a bill. The counters are exposed so the CLI can print what the run actually used.
 */
public final class RequestBudget {

  private final int maxRequests;
  private final int maxCharacters;
  private int usedRequests;
  private int usedCharacters;

  public RequestBudget(int maxRequests, int maxCharacters) {
    if (maxRequests < 0 || maxCharacters < 0) {
      throw new IllegalArgumentException("budget limits cannot be negative");
    }
    this.maxRequests = maxRequests;
    this.maxCharacters = maxCharacters;
  }

  public static RequestBudget from(GeminiConfig config) {
    return new RequestBudget(config.maxRequests(), config.maxCharacters());
  }

  /**
   * Reserves room for one request.
   *
   * @throws GeminiException.BudgetExceeded when either cap would be passed
   */
  public void reserve(int characters) {
    if (characters < 0) {
      throw new IllegalArgumentException("characters cannot be negative");
    }
    if (usedRequests + 1 > maxRequests) {
      throw new GeminiException.BudgetExceeded(
          "request cap reached (" + maxRequests + " requests per run) — nothing else was sent");
    }
    if (usedCharacters + characters > maxCharacters) {
      throw new GeminiException.BudgetExceeded(
          "character cap reached ("
              + maxCharacters
              + " characters per run, "
              + characters
              + " more requested) — nothing else was sent");
    }
    usedRequests++;
    usedCharacters += characters;
  }

  public int usedRequests() {
    return usedRequests;
  }

  public int usedCharacters() {
    return usedCharacters;
  }

  public int maxRequests() {
    return maxRequests;
  }

  public int maxCharacters() {
    return maxCharacters;
  }

  /**
   * How many characters are still allowed, which is how prompts get truncated rather than refused.
   */
  public int remainingCharacters() {
    return Math.max(0, maxCharacters - usedCharacters);
  }

  @Override
  public String toString() {
    return usedRequests
        + "/"
        + maxRequests
        + " requests, "
        + usedCharacters
        + "/"
        + maxCharacters
        + " characters";
  }
}
