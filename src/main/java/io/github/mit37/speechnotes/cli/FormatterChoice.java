package io.github.mit37.speechnotes.cli;

import java.util.Locale;

/** Which note formatter to use. {@code gemini} needs a key; {@code rule} never does. */
public enum FormatterChoice {
  RULE_BASED("rule"),
  GEMINI("gemini");

  private final String flag;

  FormatterChoice(String flag) {
    this.flag = flag;
  }

  public String flag() {
    return flag;
  }

  public static FormatterChoice parse(String value) {
    String normalised = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    for (FormatterChoice choice : values()) {
      if (choice.flag.equals(normalised)) {
        return choice;
      }
    }
    throw new UsageException("unknown formatter: " + value + " (expected rule or gemini)");
  }
}
