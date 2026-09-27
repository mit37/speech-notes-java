package io.github.mit37.speechnotes.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Markdown export of {@link SampleNotes#document()} is checked against a committed file.
 *
 * <p>The unit tests in {@link MarkdownExporterTest} cover the rules; this one catches accidental
 * changes to the whole rendered document, which is what a reader actually sees.
 */
class MarkdownGoldenTest {

  @Test
  @DisplayName("the rendered Markdown matches the committed golden file")
  void renderedMarkdownMatchesTheGoldenFile() {
    Golden.matches("sample-notes.md", new MarkdownExporter().render(SampleNotes.document()));
  }
}
