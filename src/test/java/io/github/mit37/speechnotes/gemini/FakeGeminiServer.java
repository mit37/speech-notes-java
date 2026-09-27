package io.github.mit37.speechnotes.gemini;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A real HTTP server on loopback that answers like the Gemini API does.
 *
 * <p>Better than mocking the client: the code under test builds a real request, opens a real socket
 * and parses a real response body, so the JSON and the status handling are exercised. CI never
 * talks to the internet for this (STANDARDS §2: features that call a paid API are mocked in CI).
 */
public final class FakeGeminiServer implements AutoCloseable {

  /** One received request: what was asked and with which headers. */
  public record Received(String path, String apiKeyHeader, String body) {}

  private final HttpServer server;
  private final List<Received> received = new CopyOnWriteArrayList<>();
  private volatile int status = 200;
  private volatile String responseBody = defaultResponse("Some formatted notes.");

  public FakeGeminiServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          handle(exchange);
        });
    server.start();
  }

  /** The base URL the client should use; the client is pointed here by a test-only override. */
  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta/models/";
  }

  public List<Received> received() {
    return new ArrayList<>(received);
  }

  public Received lastRequest() {
    return received.isEmpty() ? null : received.get(received.size() - 1);
  }

  /** Serve this status code and body for the next requests. */
  public FakeGeminiServer respondWith(int status, String body) {
    this.status = status;
    this.responseBody = body;
    return this;
  }

  public FakeGeminiServer respondWith(String modelText) {
    this.status = 200;
    this.responseBody = defaultResponse(modelText);
    return this;
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    String key = exchange.getRequestHeaders().getFirst("x-goog-api-key");
    if (key == null) {
      key = exchange.getRequestHeaders().getFirst("X-Goog-Api-Key");
    }
    received.add(new Received(exchange.getRequestURI().toString(), key, body));

    byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, payload.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(payload);
    }
  }

  /** The shape the API returns, with the text in the first candidate. */
  public static String defaultResponse(String text) {
    String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\""
        + escaped
        + "\"}],\"role\":\"model\"},"
        + "\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":42,\"candidatesTokenCount\":7}}";
  }
}
