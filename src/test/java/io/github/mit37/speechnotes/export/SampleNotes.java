package io.github.mit37.speechnotes.export;

import io.github.mit37.speechnotes.notes.ActionItem;
import io.github.mit37.speechnotes.notes.NoteAttachment;
import io.github.mit37.speechnotes.notes.NoteSection;
import io.github.mit37.speechnotes.notes.NotesDocument;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * One deterministic document, shared by the Markdown and PDF golden-file tests.
 *
 * <p>Everything in it is fixed — including the timestamp — because a golden file that changes every
 * time it is regenerated would be worse than no golden file at all.
 */
final class SampleNotes {

  private SampleNotes() {}

  static NotesDocument document() {
    return NotesDocument.builder("Caching lecture", "lecture-01-caching.wav")
        .engine("Vosk vosk-api (model vosk-model-small-en-us-0.15)")
        .audioSeconds(132.5)
        .formatter("rule-based")
        .transcript(
            "today we look at caching the cache pays off when the same data is requested more than "
                + "once a hit rate tells you how often that happens and action instrument the hit rate")
        .sections(
            List.of(
                new NoteSection(
                    "Summary",
                    1,
                    List.of(
                        "Caching keeps recently used data close by, so the **cache** pays off when the"
                            + " same request repeats."),
                    List.of()),
                new NoteSection(
                    "Key points",
                    1,
                    List.of("A hit rate counts how often a lookup finds the data already cached."),
                    List.of(
                        "Requesting the same data twice is what makes caching worthwhile",
                        "The **hit rate** is the number to watch")),
                new NoteSection("Action items", 1, List.of(), List.of("Instrument the hit rate"))))
        .keyTerms(List.of("cache", "hit rate"))
        .actions(List.of(new ActionItem("Instrument the hit rate", "action:", 128.0)))
        .attachments(
            List.of(
                new NoteAttachment(
                    95.0,
                    Path.of("screenshots", "shot-001.png"),
                    "Which axis is time?",
                    "The x axis."),
                new NoteAttachment(
                    140.0, Path.of("screenshots", "shot-002.png"), "How big is the cache?", null)))
        .generatedAt(Instant.parse("2026-09-26T09:30:00Z"))
        .build();
  }
}
