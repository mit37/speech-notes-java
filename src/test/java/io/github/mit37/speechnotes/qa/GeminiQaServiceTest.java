package io.github.mit37.speechnotes.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.mit37.speechnotes.gemini.GeminiClient;
import io.github.mit37.speechnotes.gemini.GeminiConfig;
import io.github.mit37.speechnotes.gemini.GeminiException;
import io.github.mit37.speechnotes.gemini.GeminiRequest;
import io.github.mit37.speechnotes.gemini.GeminiResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeminiQaServiceTest {

  private static final Path IMAGE = Path.of("build", "tmp", "qa-test", "shot.png");

  private static GeminiConfig enabledConfig() {
    return new GeminiConfig("key", "gemini-test-model", 5, 1_000_000, Duration.ofSeconds(10));
  }

  private static void writeImage(Path path, int bytes) throws IOException {
    Files.createDirectories(path.getParent());
    byte[] data = new byte[bytes];
    data[0] = (byte) 0x89;
    Files.write(path, data);
  }

  private static class RecordingClient implements GeminiClient {
    private final List<GeminiRequest> requests = new ArrayList<>();
    private String reply = "The x axis is time.";
    private RuntimeException failure;

    @Override
    public GeminiResponse generate(GeminiRequest request) {
      requests.add(request);
      if (failure != null) {
        throw failure;
      }
      return new GeminiResponse(reply, request.model(), 0, reply.length());
    }

    @Override
    public String describe() {
      return "recording client";
    }
  }

  @Test
  @DisplayName("a question about an image is sent with the image inline")
  void answersWithTheImageAttached() throws Exception {
    writeImage(IMAGE, 64);
    RecordingClient client = new RecordingClient();

    String answer =
        new GeminiQaService(client, "gemini-test-model", enabledConfig())
            .answer(IMAGE, "Which axis is time?");

    assertThat(answer).isEqualTo("The x axis is time.");
    assertThat(client.requests).hasSize(1);
    assertThat(client.requests.getFirst().parts()).hasSize(2);
    assertThat(client.requests.getFirst().promptText()).contains("Which axis is time?");
  }

  @Test
  @DisplayName("with no key the question is refused, not guessed at offline")
  void withoutAKeyTheQuestionIsRefused() throws Exception {
    writeImage(IMAGE, 64);
    GeminiQaService service = GeminiQaService.disabled(GeminiConfig.disabled());

    assertThat(service.isAvailable()).isFalse();
    assertThat(service.unavailableReason()).contains("GEMINI_API_KEY").contains("unanswered");
    assertThatExceptionOfType(QaException.class)
        .isThrownBy(() -> service.answer(IMAGE, "What is this?"))
        .withMessageContaining("GEMINI_API_KEY");
  }

  @Test
  void missingImagesAreReported() {
    GeminiQaService service = new GeminiQaService(new RecordingClient(), "m", enabledConfig());

    assertThatExceptionOfType(QaException.class)
        .isThrownBy(
            () -> service.answer(Path.of("build", "tmp", "qa-test", "nope.png"), "What is this?"))
        .withMessageContaining("screenshot not found");
  }

  @Test
  void blankQuestionsAreRejected() throws Exception {
    writeImage(IMAGE, 32);
    GeminiQaService service = new GeminiQaService(new RecordingClient(), "m", enabledConfig());

    assertThatExceptionOfType(QaException.class)
        .isThrownBy(() -> service.answer(IMAGE, "   "))
        .withMessageContaining("ask a question");
  }

  @Test
  @DisplayName("an oversized screenshot is refused before it is read into memory")
  void oversizedImagesAreRefused() throws Exception {
    Path big = Path.of("build", "tmp", "qa-test", "big.png");
    writeImage(big, 4 * 1024 * 1024 + 16);
    GeminiQaService service = new GeminiQaService(new RecordingClient(), "m", enabledConfig());

    assertThatExceptionOfType(QaException.class)
        .isThrownBy(() -> service.answer(big, "What is this?"))
        .withMessageContaining("larger than");
    Files.deleteIfExists(big);
  }

  @Test
  @DisplayName("an API failure becomes a QaException, which the UI can show")
  void apiFailuresAreWrapped() throws Exception {
    writeImage(IMAGE, 32);
    RecordingClient failing =
        new RecordingClient() {
          @Override
          public GeminiResponse generate(GeminiRequest request) {
            throw new GeminiException("Gemini refused the request (HTTP 403)");
          }
        };
    GeminiQaService service = new GeminiQaService(failing, "m", enabledConfig());

    assertThatExceptionOfType(QaException.class)
        .isThrownBy(() -> service.answer(IMAGE, "What is this?"))
        .withMessageContaining("HTTP 403");
  }
}
