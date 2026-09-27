package io.github.mit37.speechnotes.format;

import io.github.mit37.speechnotes.notes.NoteSection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns Markdown into note sections.
 *
 * <p>Used for the Gemini formatter's reply: the model returns Markdown, and the notes document
 * keeps structure rather than one blob. Anything it cannot classify becomes a paragraph, so no text
 * is ever silently dropped.
 */
public final class MarkdownNotesParser {

  /** {@code 1.} or {@code 1)} at the start of a line. */
  private static final Pattern ORDERED_ITEM = Pattern.compile("^\\d+[.)]\\s+.*");

  /** The result: an optional title from a top-level heading, plus the sections. */
  public record ParsedNotes(String title, List<NoteSection> sections) {

    public ParsedNotes {
      sections = List.copyOf(sections);
    }

    public boolean isEmpty() {
      return sections.isEmpty();
    }
  }

  private MarkdownNotesParser() {}

  public static ParsedNotes parse(String markdown) {
    String title = null;
    String heading = null;
    List<NoteSection> sections = new ArrayList<>();
    List<String> paragraphs = new ArrayList<>();
    List<String> bullets = new ArrayList<>();
    StringBuilder pendingParagraph = new StringBuilder();

    for (String rawLine : (markdown == null ? "" : markdown).split("\n", -1)) {
      String line = rawLine.strip();
      if (line.isEmpty() || line.equals("---")) {
        endParagraph(pendingParagraph, paragraphs);
      } else if (line.startsWith("## ") || line.startsWith("### ")) {
        endParagraph(pendingParagraph, paragraphs);
        flush(sections, heading, paragraphs, bullets);
        heading = line.substring(line.indexOf(' ') + 1).strip();
      } else if (line.startsWith("# ")) {
        endParagraph(pendingParagraph, paragraphs);
        flush(sections, heading, paragraphs, bullets);
        String text = line.substring(2).strip();
        if (title == null) {
          title = text;
        } else {
          // A second top-level heading is a section, not a new title: no text may be lost.
          heading = text;
        }
      } else if (line.startsWith("- ") || line.startsWith("* ")) {
        endParagraph(pendingParagraph, paragraphs);
        bullets.add(line.substring(2).strip());
      } else if (ORDERED_ITEM.matcher(line).matches()) {
        endParagraph(pendingParagraph, paragraphs);
        bullets.add(line.replaceFirst("^\\d+[.)]\\s+", ""));
      } else {
        if (!pendingParagraph.isEmpty()) {
          pendingParagraph.append(' ');
        }
        pendingParagraph.append(line);
      }
    }
    endParagraph(pendingParagraph, paragraphs);
    flush(sections, heading, paragraphs, bullets);
    return new ParsedNotes(title, sections);
  }

  /** Closes the section being collected. */
  private static void flush(
      List<NoteSection> sections, String heading, List<String> paragraphs, List<String> bullets) {
    if (heading != null || !paragraphs.isEmpty() || !bullets.isEmpty()) {
      sections.add(
          new NoteSection(
              heading == null ? "Notes" : heading,
              1,
              List.copyOf(paragraphs),
              List.copyOf(bullets)));
    }
    paragraphs.clear();
    bullets.clear();
  }

  /** Moves the lines collected since the last break into the section as one paragraph. */
  private static void endParagraph(StringBuilder pending, List<String> paragraphs) {
    if (!pending.isEmpty()) {
      paragraphs.add(pending.toString());
      pending.setLength(0);
    }
  }
}
