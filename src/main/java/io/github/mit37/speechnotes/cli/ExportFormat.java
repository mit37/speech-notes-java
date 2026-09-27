package io.github.mit37.speechnotes.cli;

import java.util.Locale;

/**
 * Which files {@code --out} writes: Markdown, PDF, or both (PRD G5).
 *
 * <p>It only ever affects what lands on disk. Printing notes to standard output stays Markdown,
 * because a PDF is not something anybody reads in a terminal.
 */
public enum ExportFormat {
  MARKDOWN("md", true, false),
  PDF("pdf", false, true),
  BOTH("both", true, true);

  private final String flag;
  private final boolean markdown;
  private final boolean pdf;

  ExportFormat(String flag, boolean markdown, boolean pdf) {
    this.flag = flag;
    this.markdown = markdown;
    this.pdf = pdf;
  }

  public String flag() {
    return flag;
  }

  public boolean writesMarkdown() {
    return markdown;
  }

  public boolean writesPdf() {
    return pdf;
  }

  public static ExportFormat parse(String value) {
    String normalised = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    for (ExportFormat format : values()) {
      if (format.flag.equals(normalised)) {
        return format;
      }
    }
    throw new UsageException("unknown export format: " + value + " (expected md, pdf or both)");
  }
}
