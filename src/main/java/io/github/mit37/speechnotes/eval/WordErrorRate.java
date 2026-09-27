package io.github.mit37.speechnotes.eval;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Word error rate against a written reference: the standard substitutions + insertions + deletions
 * over reference words (PRD G2 measurement, milestone 8).
 *
 * <p>Comparison is deliberately blunt — lower case, letters and digits only — because the point is
 * how much of the sentence survived, not how the recogniser punctuates. Numbers stay as words, so a
 * reference that says "eighty four percent" and a recogniser that writes "84" count as a
 * substitution, which is the honest answer for a text diff.
 */
public final class WordErrorRate {

  /** The edit counts and the reference length, kept apart so a report can explain its number. */
  public record Score(int substitutions, int insertions, int deletions, int referenceWords) {

    public int errors() {
      return substitutions + insertions + deletions;
    }

    /**
     * Errors per reference word. An empty reference scores 1 when anything was heard, 0 when not.
     */
    public double rate() {
      if (referenceWords == 0) {
        return insertions == 0 ? 0 : 1;
      }
      return (double) errors() / referenceWords;
    }
  }

  private WordErrorRate() {}

  /**
   * Scores {@code hypothesis} (what the recogniser heard) against {@code reference} (the script).
   */
  public static Score of(String reference, String hypothesis) {
    return of(words(reference), words(hypothesis));
  }

  public static Score of(List<String> reference, List<String> hypothesis) {
    int referenceCount = reference.size();
    int hypothesisCount = hypothesis.size();
    int[][] distance = new int[referenceCount + 1][hypothesisCount + 1];
    for (int i = 0; i <= referenceCount; i++) {
      distance[i][0] = i;
    }
    for (int j = 0; j <= hypothesisCount; j++) {
      distance[0][j] = j;
    }
    for (int i = 1; i <= referenceCount; i++) {
      for (int j = 1; j <= hypothesisCount; j++) {
        int substitution =
            distance[i - 1][j - 1] + (reference.get(i - 1).equals(hypothesis.get(j - 1)) ? 0 : 1);
        distance[i][j] =
            Math.min(substitution, Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1));
      }
    }

    int substitutions = 0;
    int insertions = 0;
    int deletions = 0;
    int i = referenceCount;
    int j = hypothesisCount;
    while (i > 0 || j > 0) {
      if (i > 0
          && j > 0
          && reference.get(i - 1).equals(hypothesis.get(j - 1))
          && distance[i][j] == distance[i - 1][j - 1]) {
        i--;
        j--;
      } else if (i > 0 && j > 0 && distance[i][j] == distance[i - 1][j - 1] + 1) {
        substitutions++;
        i--;
        j--;
      } else if (j > 0 && distance[i][j] == distance[i][j - 1] + 1) {
        insertions++;
        j--;
      } else {
        deletions++;
        i--;
      }
    }
    return new Score(substitutions, insertions, deletions, referenceCount);
  }

  /** The words as they are compared: lower case, punctuation dropped, blanks ignored. */
  public static List<String> words(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    return Arrays.stream(
            text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{Alnum}]+", " ").trim().split("\\s+"))
        .filter(word -> !word.isEmpty())
        .toList();
  }

  /** True when {@code transcript} contains {@code phrase} as consecutive words. */
  public static boolean contains(List<String> transcript, String phrase) {
    List<String> needle = words(phrase);
    if (needle.isEmpty() || needle.size() > transcript.size()) {
      return false;
    }
    for (int start = 0; start + needle.size() <= transcript.size(); start++) {
      if (transcript.subList(start, start + needle.size()).equals(needle)) {
        return true;
      }
    }
    return false;
  }
}
