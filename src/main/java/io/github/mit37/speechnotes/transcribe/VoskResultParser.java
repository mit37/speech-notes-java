package io.github.mit37.speechnotes.transcribe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

/**
 * Turns Vosk's JSON payloads into {@link TranscriptEvent}s.
 *
 * <p>Kept separate from the recogniser so the JSON handling can be tested without the model or its
 * native libraries — the shapes here are exactly what {@code vosk-api} 0.3.45 emits.
 */
public final class VoskResultParser {

  private final ObjectMapper mapper;

  public VoskResultParser() {
    this(new ObjectMapper());
  }

  public VoskResultParser(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * Parses a {@code getPartialResult()} payload.
   *
   * <p>Partial results carry no timestamps, so the caller supplies the window the partial covers.
   */
  public Optional<TranscriptEvent.Partial> partial(
      String json, double startSeconds, double endSeconds) {
    String text = parse(json).path("partial").asText("").trim();
    if (text.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new TranscriptEvent.Partial(startSeconds, endSeconds, text));
  }

  /**
   * Parses a final result payload.
   *
   * <p>When the payload includes per-word times they win; otherwise the caller's window is used,
   * which is what happens for {@code getFinalResult()} on some Vosk builds.
   */
  public Optional<TranscriptEvent.Final> finalResult(
      String json, double fallbackStart, double fallbackEnd) {
    JsonNode node = parse(json);
    String text = node.path("text").asText("").trim();
    if (text.isEmpty()) {
      return Optional.empty();
    }
    double start = fallbackStart;
    double end = fallbackEnd;
    JsonNode words = node.path("result");
    if (words.isArray() && !words.isEmpty()) {
      JsonNode first = words.get(0);
      JsonNode last = words.get(words.size() - 1);
      if (first.hasNonNull("start")) {
        start = first.get("start").asDouble(start);
      }
      if (last.hasNonNull("end")) {
        end = last.get("end").asDouble(end);
      }
    }
    if (end < start) {
      end = start;
    }
    return Optional.of(new TranscriptEvent.Final(start, end, text));
  }

  private JsonNode parse(String json) {
    if (json == null || json.isBlank()) {
      throw new TranscriptionException("Vosk returned an empty result");
    }
    try {
      JsonNode node = mapper.readTree(json);
      if (node == null || !node.isObject()) {
        throw new TranscriptionException("Vosk returned JSON that is not an object: " + json);
      }
      return node;
    } catch (JsonProcessingException e) {
      throw new TranscriptionException(
          "Vosk returned unreadable JSON: " + e.getOriginalMessage(), e);
    }
  }
}
