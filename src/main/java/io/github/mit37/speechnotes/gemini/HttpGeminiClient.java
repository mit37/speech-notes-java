package io.github.mit37.speechnotes.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * The real Gemini call, through {@code java.net.http} as the PRD asks for.
 *
 * <p>Deliberately plain HTTP and JSON: no SDK, no hidden retries, no hidden spend. The key travels
 * in the {@code x-goog-api-key} header rather than the query string so it stays out of logs, and
 * every request goes through {@link RequestBudget} before the socket is touched.
 */
public final class HttpGeminiClient implements GeminiClient {

  /** The real API. Tests override this with a loopback server. */
  public static final String DEFAULT_ENDPOINT =
      "https://generativelanguage.googleapis.com/v1beta/models/";

  private final GeminiConfig config;
  private final RequestBudget budget;
  private final HttpClient http;
  private final String endpointBase;
  private final ObjectMapper mapper = new ObjectMapper();

  public HttpGeminiClient(GeminiConfig config, RequestBudget budget) {
    this(config, budget, clientFor(config), DEFAULT_ENDPOINT);
  }

  /** Injection point for tests: the fake API server runs on loopback with an ordinary client. */
  public HttpGeminiClient(GeminiConfig config, RequestBudget budget, HttpClient http) {
    this(config, budget, http, DEFAULT_ENDPOINT);
  }

  public HttpGeminiClient(
      GeminiConfig config, RequestBudget budget, HttpClient http, String endpointBase) {
    this.config = config;
    this.budget = budget;
    this.http = http;
    this.endpointBase = endpointBase;
  }

  private static HttpClient clientFor(GeminiConfig config) {
    return HttpClient.newBuilder()
        .connectTimeout(config.timeout())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  @Override
  public GeminiResponse generate(GeminiRequest request) {
    if (!config.isEnabled()) {
      throw new GeminiException.Disabled(
          "no " + GeminiConfig.KEY_ENV + " is set, so nothing was sent to Gemini");
    }
    budget.reserve(request.characterCount());
    HttpRequest httpRequest =
        HttpRequest.newBuilder(URI.create(endpointBase + request.model() + ":generateContent"))
            .timeout(config.timeout())
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", config.apiKey())
            .POST(HttpRequest.BodyPublishers.ofString(buildBody(request), StandardCharsets.UTF_8))
            .build();
    try {
      HttpResponse<String> response =
          http.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() / 100 != 2) {
        throw new GeminiException(
            "Gemini refused the request (HTTP "
                + response.statusCode()
                + "): "
                + summarise(response.body()));
      }
      return parseResponse(request.model(), response.body());
    } catch (IOException e) {
      throw new GeminiException("Gemini call failed: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GeminiException("Gemini call was interrupted", e);
    }
  }

  @Override
  public String describe() {
    return config.describe();
  }

  /** The exact JSON body, exposed so a dry run can show it without sending it. */
  public String buildBody(GeminiRequest request) {
    ObjectNode root = mapper.createObjectNode();
    ArrayNode contents = root.putArray("contents");
    ObjectNode userTurn = contents.addObject();
    userTurn.put("role", "user");
    ArrayNode parts = userTurn.putArray("parts");
    for (GeminiPart part : request.parts()) {
      if (part instanceof GeminiPart.Text textPart) {
        parts.addObject().put("text", textPart.text());
      } else if (part instanceof GeminiPart.Image imagePart) {
        ObjectNode inline = parts.addObject().putObject("inline_data");
        inline.put("mime_type", imagePart.mediaType());
        inline.put("data", imagePart.base64());
      }
    }
    root.putObject("generationConfig").put("maxOutputTokens", request.maxOutputTokens());
    try {
      return mapper.writeValueAsString(root);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new GeminiException("could not build the request body: " + e.getMessage(), e);
    }
  }

  private GeminiResponse parseResponse(String model, String body) {
    try {
      JsonNode root = mapper.readTree(body);
      StringBuilder text = new StringBuilder();
      for (JsonNode candidate : root.path("candidates")) {
        for (JsonNode part : candidate.path("content").path("parts")) {
          if (part.hasNonNull("text")) {
            if (!text.isEmpty()) {
              text.append('\n');
            }
            text.append(part.get("text").asText());
          }
        }
      }
      return new GeminiResponse(text.toString(), model, 0, text.length());
    } catch (IOException e) {
      throw new GeminiException("Gemini returned unreadable JSON: " + e.getMessage(), e);
    }
  }

  /** A short, single-line summary of an error body; never echoes a key. */
  private static String summarise(String body) {
    if (body == null || body.isBlank()) {
      return "(empty response body)";
    }
    String flat = body.replaceAll("\\s+", " ").trim();
    return flat.length() <= 300 ? flat : flat.substring(0, 300) + "…";
  }

  /** The endpoint a request for {@code model} would go to, which a dry run prints. */
  public static String endpointFor(String model) {
    return String.format(Locale.ROOT, "%s%s:generateContent", DEFAULT_ENDPOINT, model);
  }

  /** The endpoint this instance actually talks to. */
  public String endpoint() {
    return endpointBase;
  }

  /** The timeout the client will wait for a response. */
  public Duration timeout() {
    return config.timeout();
  }
}
