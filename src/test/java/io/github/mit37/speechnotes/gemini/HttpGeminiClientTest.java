package io.github.mit37.speechnotes.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpGeminiClientTest {

  private static FakeGeminiServer server;

  @BeforeAll
  static void startServer() throws Exception {
    server = new FakeGeminiServer();
  }

  @AfterAll
  static void stopServer() {
    server.close();
  }

  private static GeminiConfig config(String key, int maxRequests, int maxCharacters) {
    return new GeminiConfig(
        key, "gemini-test-model", maxRequests, maxCharacters, Duration.ofSeconds(10));
  }

  private static HttpGeminiClient client(GeminiConfig config, RequestBudget budget) {
    return new HttpGeminiClient(config, budget, HttpClient.newHttpClient(), server.baseUrl());
  }

  @Test
  @DisplayName(
      "a request carries the key header, the model path and the prompt, and the reply is parsed")
  void sendsAndParses() {
    server.respondWith("## Summary\n\n- the cache pays off");
    RequestBudget budget = new RequestBudget(5, 10_000);

    GeminiResponse response =
        client(config("test-key", 5, 10_000), budget)
            .generate(GeminiRequest.text("gemini-test-model", "format this transcript"));

    assertThat(response.text()).contains("## Summary").contains("the cache pays off");
    FakeGeminiServer.Received request = server.lastRequest();
    assertThat(request.apiKeyHeader()).isEqualTo("test-key");
    assertThat(request.path()).contains("gemini-test-model:generateContent");
    assertThat(request.body()).contains("format this transcript");
    assertThat(budget.usedRequests()).isEqualTo(1);
    assertThat(budget.usedCharacters()).isGreaterThan(0);
  }

  @Test
  @DisplayName("an image part is sent inline, base64 encoded")
  void sendsInlineImages() {
    server.respondWith("The x axis is time.");
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    client(config("test-key", 5, 100_000), new RequestBudget(5, 100_000))
        .generate(GeminiRequest.withImage("gemini-test-model", "what is this?", "image/png", png));

    String body = server.lastRequest().body();
    assertThat(body).contains("inline_data").contains("image/png").contains("\"data\"");
    assertThat(body).contains("what is this?");
  }

  @Test
  @DisplayName("an API error becomes a GeminiException that carries the status and the reason")
  void reportsApiErrors() {
    server.respondWith(400, "{\"error\":{\"message\":\"API key not valid\"}}");

    assertThatExceptionOfType(GeminiException.class)
        .isThrownBy(
            () ->
                client(config("bad-key", 5, 10_000), new RequestBudget(5, 10_000))
                    .generate(GeminiRequest.text("gemini-test-model", "hello")))
        .withMessageContaining("400")
        .withMessageContaining("API key not valid");
  }

  @Test
  @DisplayName("with no key nothing is sent at all")
  void withoutAKeyNothingLeavesTheMachine() {
    int before = server.received().size();

    assertThatExceptionOfType(GeminiException.Disabled.class)
        .isThrownBy(
            () ->
                client(config(null, 5, 10_000), new RequestBudget(5, 10_000))
                    .generate(GeminiRequest.text("gemini-test-model", "should not be sent")))
        .withMessageContaining("GEMINI_API_KEY");

    assertThat(server.received()).hasSize(before);
  }

  @Test
  @DisplayName("a cap stops the request before the socket is touched")
  void budgetStopsRequestsBeforeTheyAreSent() {
    RequestBudget budget = new RequestBudget(1, 10_000);
    HttpGeminiClient client = client(config("test-key", 1, 10_000), budget);
    client.generate(GeminiRequest.text("gemini-test-model", "first"));
    int afterFirst = server.received().size();

    assertThatExceptionOfType(GeminiException.BudgetExceeded.class)
        .isThrownBy(() -> client.generate(GeminiRequest.text("gemini-test-model", "second")))
        .withMessageContaining("request cap");

    assertThat(server.received()).hasSize(afterFirst);
  }

  @Test
  @DisplayName("the character cap is checked too, and the message says by how much")
  void characterCapIsEnforced() {
    RequestBudget budget = new RequestBudget(10, 20);

    assertThatExceptionOfType(GeminiException.BudgetExceeded.class)
        .isThrownBy(
            () ->
                client(config("test-key", 10, 20), budget)
                    .generate(GeminiRequest.text("gemini-test-model", "x".repeat(100))))
        .withMessageContaining("character cap");
  }

  @Test
  @DisplayName("describe() names the configuration and never the key")
  void describeNeverLeaksTheKey() {
    String described = client(config("super-secret", 3, 900), new RequestBudget(3, 900)).describe();

    assertThat(described)
        .contains("maxRequests=3")
        .contains("key=set")
        .doesNotContain("super-secret");
  }

  @Test
  void bodyIsInspectableForADryRun() {
    String body =
        client(config("test-key", 5, 10_000), new RequestBudget(5, 10_000))
            .buildBody(GeminiRequest.text("gemini-test-model", "hello").withMaxOutputTokens(128));

    assertThat(body)
        .contains("\"maxOutputTokens\":128")
        .contains("hello")
        .contains("\"role\":\"user\"");
  }
}
