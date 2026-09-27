package io.github.mit37.speechnotes.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestBudgetTest {

  @Test
  void countsRequestsAndCharacters() {
    RequestBudget budget = new RequestBudget(3, 100);

    budget.reserve(40);
    budget.reserve(50);

    assertThat(budget.usedRequests()).isEqualTo(2);
    assertThat(budget.usedCharacters()).isEqualTo(90);
    assertThat(budget.remainingCharacters()).isEqualTo(10);
  }

  @Test
  @DisplayName("the request cap is hard, not advisory")
  void requestCapIsHard() {
    RequestBudget budget = new RequestBudget(2, 1_000);
    budget.reserve(10);
    budget.reserve(10);

    assertThatExceptionOfType(GeminiException.BudgetExceeded.class)
        .isThrownBy(() -> budget.reserve(10))
        .withMessageContaining("request cap reached (2 requests per run)");
  }

  @Test
  @DisplayName("the character cap is hard, and says how much was requested")
  void characterCapIsHard() {
    RequestBudget budget = new RequestBudget(5, 100);
    budget.reserve(90);

    assertThatExceptionOfType(GeminiException.BudgetExceeded.class)
        .isThrownBy(() -> budget.reserve(20))
        .withMessageContaining("character cap reached")
        .withMessageContaining("20 more requested");

    assertThat(budget.usedRequests()).as("a refused request is not counted").isEqualTo(1);
  }

  @Test
  void refusesNegativeInput() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new RequestBudget(-1, 10))
        .withMessageContaining("negative");
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new RequestBudget(1, 10).reserve(-5))
        .withMessageContaining("negative");
  }

  @Test
  @DisplayName("the budget comes from the configuration, so caps are read from the environment")
  void comesFromConfiguration() {
    GeminiConfig config =
        new GeminiConfig("key", "gemini-test-model", 7, 700, Duration.ofSeconds(5));

    RequestBudget budget = RequestBudget.from(config);

    assertThat(budget.maxRequests()).isEqualTo(7);
    assertThat(budget.maxCharacters()).isEqualTo(700);
  }

  @Test
  @DisplayName("environment defaults keep the caps finite even when nobody sets them")
  void environmentDefaultsAreFinite() {
    GeminiConfig config = GeminiConfig.fromEnvironment(Map.of(GeminiConfig.KEY_ENV, "key"));

    assertThat(config.maxRequests()).isPositive();
    assertThat(config.maxCharacters()).isPositive();
    assertThat(config.timeout()).isPositive();
    assertThat(config.isEnabled()).isTrue();
  }

  @Test
  @DisplayName("no key means disabled, not unlimited")
  void noKeyMeansDisabled() {
    GeminiConfig config = GeminiConfig.fromEnvironment(Map.of());

    assertThat(config.isEnabled()).isFalse();
    assertThat(config.describe()).contains("key=missing");
  }

  @Test
  void toStringShowsUsage() {
    RequestBudget budget = new RequestBudget(2, 50);
    budget.reserve(20);

    assertThat(budget.toString()).isEqualTo("1/2 requests, 20/50 characters");
  }
}
