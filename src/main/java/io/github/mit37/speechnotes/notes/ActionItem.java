package io.github.mit37.speechnotes.notes;

/** Something the speaker asked for, with the cue that triggered it and when it was said. */
public record ActionItem(String text, String cue, double atSeconds) {}
