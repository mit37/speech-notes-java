package io.github.mit37.speechnotes.notes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything a session produced: structured notes, key terms, actions, attachments and the raw
 * transcript they came from.
 *
 * <p>Built through {@link #builder} because there are a lot of fields and most callers only care
 * about two or three of them.
 */
public record NotesDocument(
    String title,
    String source,
    String engine,
    double audioSeconds,
    String formatter,
    String transcript,
    List<NoteSection> sections,
    List<String> keyTerms,
    List<ActionItem> actions,
    List<NoteAttachment> attachments,
    Instant generatedAt) {

  public NotesDocument {
    sections = List.copyOf(sections);
    keyTerms = List.copyOf(keyTerms);
    actions = List.copyOf(actions);
    attachments = List.copyOf(attachments);
  }

  /** True when the formatter had no speech to work with. */
  public boolean isEmpty() {
    return sections.stream().allMatch(NoteSection::isEmpty) && actions.isEmpty();
  }

  public int wordCount() {
    String trimmed = transcript.trim();
    return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
  }

  /**
   * The same document with a different set of screenshot questions.
   *
   * <p>Questions can be asked while the session is still running, so the notes are built without
   * them and this puts them in once the recorder stops.
   */
  public NotesDocument withAttachments(List<NoteAttachment> newAttachments) {
    return new NotesDocument(
        title,
        source,
        engine,
        audioSeconds,
        formatter,
        transcript,
        sections,
        keyTerms,
        actions,
        newAttachments,
        generatedAt);
  }

  public static Builder builder(String title, String source) {
    return new Builder(title, source);
  }

  /** Small mutable helper so the formatter reads like a story instead of a constructor call. */
  public static final class Builder {

    private final String title;
    private final String source;
    private String engine = "";
    private double audioSeconds = -1;
    private String formatter = "";
    private String transcript = "";
    private List<NoteSection> sections = new ArrayList<>();
    private List<String> keyTerms = new ArrayList<>();
    private List<ActionItem> actions = new ArrayList<>();
    private List<NoteAttachment> attachments = new ArrayList<>();
    private Instant generatedAt = Instant.now();

    private Builder(String title, String source) {
      this.title = title;
      this.source = source;
    }

    public Builder engine(String engine) {
      this.engine = engine;
      return this;
    }

    public Builder audioSeconds(double audioSeconds) {
      this.audioSeconds = audioSeconds;
      return this;
    }

    public Builder formatter(String formatter) {
      this.formatter = formatter;
      return this;
    }

    public Builder transcript(String transcript) {
      this.transcript = transcript;
      return this;
    }

    public Builder sections(List<NoteSection> sections) {
      this.sections = new ArrayList<>(sections);
      return this;
    }

    public Builder keyTerms(List<String> keyTerms) {
      this.keyTerms = new ArrayList<>(keyTerms);
      return this;
    }

    public Builder actions(List<ActionItem> actions) {
      this.actions = new ArrayList<>(actions);
      return this;
    }

    public Builder attachments(List<NoteAttachment> attachments) {
      this.attachments = new ArrayList<>(attachments);
      return this;
    }

    public Builder generatedAt(Instant generatedAt) {
      this.generatedAt = generatedAt;
      return this;
    }

    public NotesDocument build() {
      return new NotesDocument(
          title,
          source,
          engine,
          audioSeconds,
          formatter,
          transcript,
          sections,
          keyTerms,
          actions,
          attachments,
          generatedAt);
    }
  }
}
