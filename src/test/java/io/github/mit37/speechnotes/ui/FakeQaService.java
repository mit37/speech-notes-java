package io.github.mit37.speechnotes.ui;

import io.github.mit37.speechnotes.qa.QAService;
import io.github.mit37.speechnotes.qa.QaException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A screenshot Q&A service with a scripted answer, a scripted failure, or a reason it is off.
 *
 * <p>No HTTP and no key, which is what lets the window's Q&A path be tested at all on a machine
 * that has neither.
 */
public final class FakeQaService implements QAService {

  private final String unavailableReason;
  private final String reply;
  private final String failure;
  private final List<String> asked = new CopyOnWriteArrayList<>();
  private final List<Path> images = new CopyOnWriteArrayList<>();

  private FakeQaService(String unavailableReason, String reply, String failure) {
    this.unavailableReason = unavailableReason;
    this.reply = reply;
    this.failure = failure;
  }

  /** Answers every question with {@code reply}. */
  public static FakeQaService answering(String reply) {
    return new FakeQaService("", reply, null);
  }

  /** Fails every question, the way a refused request or an exhausted cap would. */
  public static FakeQaService failing(String message) {
    return new FakeQaService("", null, message);
  }

  /** Off, the way it is without a key. */
  public static FakeQaService disabled() {
    return new FakeQaService("screenshot Q&A needs GEMINI_API_KEY", null, null);
  }

  @Override
  public String answer(Path image, String question) throws QaException {
    images.add(image);
    asked.add(question);
    if (failure != null) {
      throw new QaException(failure);
    }
    return reply;
  }

  @Override
  public boolean isAvailable() {
    return unavailableReason.isEmpty();
  }

  @Override
  public String unavailableReason() {
    return unavailableReason;
  }

  /**
   * The questions that were actually asked, so a test can prove nothing was sent when it should not
   * be.
   */
  public List<String> asked() {
    return List.copyOf(asked);
  }

  /** The images that were sent, in order. */
  public List<Path> images() {
    return List.copyOf(images);
  }
}
