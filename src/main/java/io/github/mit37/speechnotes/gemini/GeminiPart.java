package io.github.mit37.speechnotes.gemini;

import java.util.Base64;

/** One piece of a request: text, or an image for the screenshot questions. */
public sealed interface GeminiPart {

  /** Characters this part contributes to the request budget. */
  int characterCount();

  record Text(String text) implements GeminiPart {

    public Text {
      if (text == null) {
        throw new IllegalArgumentException("text cannot be null");
      }
    }

    @Override
    public int characterCount() {
      return text.length();
    }
  }

  /** An inline image, base64 encoded on the way out. */
  record Image(String mediaType, byte[] data) implements GeminiPart {

    public Image {
      if (data == null || data.length == 0) {
        throw new IllegalArgumentException("image data cannot be empty");
      }
      mediaType = mediaType == null || mediaType.isBlank() ? "image/png" : mediaType;
    }

    /**
     * Characters this counts against the budget.
     *
     * <p>The base64 length, not the byte count, because that is what actually travels.
     */
    @Override
    public int characterCount() {
      return (int) (4L * Math.ceil(data.length / 3.0));
    }

    public String base64() {
      return Base64.getEncoder().encodeToString(data);
    }
  }
}
