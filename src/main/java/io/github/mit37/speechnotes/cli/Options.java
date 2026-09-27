package io.github.mit37.speechnotes.cli;

import io.github.mit37.speechnotes.AppInfo;
import java.nio.file.Path;

/**
 * Options that apply to every mode. Defaults are the ones the README promises: the small local
 * model, notes printed to standard output, offline formatting.
 */
public record Options(
    Path modelDir,
    Path outDir,
    Double seconds,
    FormatterChoice formatter,
    boolean extractActions,
    String title,
    boolean dryRun,
    ExportFormat export) {

  public static Options defaults() {
    return new Options(
        Path.of(AppInfo.DEFAULT_MODEL_DIR),
        null,
        null,
        FormatterChoice.RULE_BASED,
        true,
        null,
        false,
        ExportFormat.MARKDOWN);
  }

  public Options withModelDir(Path newModelDir) {
    return new Options(
        newModelDir, outDir, seconds, formatter, extractActions, title, dryRun, export);
  }

  public Options withOutDir(Path newOutDir) {
    return new Options(
        modelDir, newOutDir, seconds, formatter, extractActions, title, dryRun, export);
  }
}
