package io.github.mit37.speechnotes.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.mit37.speechnotes.AppInfo;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CliArgsTest {

  @ParameterizedTest
  @ValueSource(strings = {"-V", "--version"})
  void versionFlagsParse(String flag) {
    assertThat(CliArgs.parse(List.of(flag))).isEqualTo(new Command.Version());
  }

  @ParameterizedTest
  @ValueSource(strings = {"-h", "--help"})
  void helpFlagsParse(String flag) {
    assertThat(CliArgs.parse(List.of(flag))).isEqualTo(new Command.Help());
  }

  @Test
  @DisplayName("no arguments shows the help")
  void emptyArgumentsShowHelp() {
    assertThat(CliArgs.parse(List.of())).isEqualTo(new Command.Help());
  }

  @Test
  @DisplayName(
      "file mode defaults to the committed small model, stdout output and offline formatting")
  void fileModeDefaults() {
    Command command = CliArgs.parse(List.of("--file", "lecture.wav"));

    assertThat(command).isInstanceOf(Command.TranscribeFile.class);
    Command.TranscribeFile file = (Command.TranscribeFile) command;
    assertThat(file.audio()).isEqualTo(Path.of("lecture.wav"));
    assertThat(file.options().modelDir()).isEqualTo(Path.of(AppInfo.DEFAULT_MODEL_DIR));
    assertThat(file.options().outDir()).isNull();
    assertThat(file.options().seconds()).isNull();
    assertThat(file.options().formatter()).isEqualTo(FormatterChoice.RULE_BASED);
    assertThat(file.options().extractActions()).isTrue();
    assertThat(file.options().title()).isNull();
  }

  @Test
  @DisplayName("options are read whichever order they come in")
  void optionsInAnyOrder() {
    Command first =
        CliArgs.parse(
            List.of(
                "--file",
                "lecture.wav",
                "--out",
                "notes",
                "--model",
                "models/big",
                "--title",
                "Caching"));
    Command second =
        CliArgs.parse(
            List.of(
                "--model",
                "models/big",
                "--title",
                "Caching",
                "--out",
                "notes",
                "--file",
                "lecture.wav"));

    assertThat(first).isEqualTo(second);
    Command.TranscribeFile file = (Command.TranscribeFile) first;
    assertThat(file.options().outDir()).isEqualTo(Path.of("notes"));
    assertThat(file.options().modelDir()).isEqualTo(Path.of("models/big"));
    assertThat(file.options().title()).isEqualTo("Caching");
  }

  @Test
  void formatterAndActionFlagsParse() {
    Command command =
        CliArgs.parse(List.of("--file", "a.wav", "--format", "gemini", "--no-actions"));

    Command.TranscribeFile file = (Command.TranscribeFile) command;
    assertThat(file.options().formatter()).isEqualTo(FormatterChoice.GEMINI);
    assertThat(file.options().extractActions()).isFalse();
  }

  @Test
  void micModeTakesADurationLimit() {
    Command command = CliArgs.parse(List.of("--mic", "--seconds", "12.5"));

    Command.TranscribeMic mic = (Command.TranscribeMic) command;
    assertThat(mic.options().seconds()).isEqualTo(12.5);
  }

  @Test
  void uiModeParses() {
    assertThat(CliArgs.parse(List.of("--ui"))).isInstanceOf(Command.ShowUi.class);
  }

  @Test
  @DisplayName("--export picks what --out writes, and defaults to Markdown")
  void exportFormatParses() {
    Command defaulted = CliArgs.parse(List.of("--file", "a.wav", "--out", "notes"));
    Command pdf = CliArgs.parse(List.of("--file", "a.wav", "--out", "notes", "--export", "pdf"));
    Command both = CliArgs.parse(List.of("--file", "a.wav", "--out", "notes", "--export", "both"));

    assertThat(((Command.TranscribeFile) defaulted).options().export())
        .isEqualTo(ExportFormat.MARKDOWN);
    assertThat(((Command.TranscribeFile) pdf).options().export()).isEqualTo(ExportFormat.PDF);
    assertThat(((Command.TranscribeFile) both).options().export()).isEqualTo(ExportFormat.BOTH);
  }

  @Test
  @DisplayName("--export without --out is refused instead of silently ignored")
  void exportWithoutOutDirIsRejected() {
    assertThatExceptionOfType(UsageException.class)
        .isThrownBy(() -> CliArgs.parse(List.of("--file", "a.wav", "--export", "pdf")))
        .withMessageContaining("--export pdf needs --out")
        .withMessageContaining("nowhere to write");
  }

  @Test
  void unknownExportFormatIsRejected() {
    assertThatExceptionOfType(UsageException.class)
        .isThrownBy(
            () -> CliArgs.parse(List.of("--file", "a.wav", "--out", "notes", "--export", "docx")))
        .withMessageContaining("unknown export format")
        .withMessageContaining("md, pdf or both");
  }

  @Test
  @DisplayName("--file without a path is a usage error")
  void fileWithoutPathIsRejected() {
    assertThatExceptionOfType(UsageException.class)
        .isThrownBy(() -> CliArgs.parse(List.of("--file")))
        .withMessageContaining("--file needs a value");
  }

  @Test
  @DisplayName("two commands at once are rejected")
  void twoCommandsAreRejected() {
    assertThatExceptionOfType(UsageException.class)
        .isThrownBy(() -> CliArgs.parse(List.of("--file", "a.wav", "--mic")))
        .withMessageContaining("only one command at a time");
  }

  @Test
  @DisplayName("leftover arguments are rejected instead of ignored")
  void extraArgumentsAreRejected() {
    assertThatExceptionOfType(UsageException.class)
        .isThrownBy(() -> CliArgs.parse(List.of("--file", "a.wav", "extra")))
        .withMessageContaining("unknown option: extra");
  }

  @Test
  void unknownOptionsAreRejectedByName() {
    assertThatExceptionOfType(UsageException.class)
        .isThrownBy(() -> CliArgs.parse(List.of("--nonsense")))
        .withMessageContaining("--nonsense");
  }

  @Test
  @DisplayName("a negative or non-numeric duration is rejected")
  void durationMustBeAPositiveNumber() {
    assertThatExceptionOfType(UsageException.class)
        .isThrownBy(() -> CliArgs.parse(List.of("--mic", "--seconds", "soon")))
        .withMessageContaining("must be a number of seconds");
    assertThatExceptionOfType(UsageException.class)
        .isThrownBy(() -> CliArgs.parse(List.of("--mic", "--seconds", "-1")))
        .withMessageContaining("positive");
  }

  @Test
  void unknownFormatterIsRejected() {
    assertThatExceptionOfType(UsageException.class)
        .isThrownBy(() -> CliArgs.parse(List.of("--file", "a.wav", "--format", "magic")))
        .withMessageContaining("unknown formatter")
        .withMessageContaining("rule or gemini");
  }

  @Test
  @DisplayName("a lone option with no command explains what to pick")
  void optionsWithoutACommandAreRejected() {
    assertThatExceptionOfType(UsageException.class)
        .isThrownBy(() -> CliArgs.parse(List.of("--title", "Notes")))
        .withMessageContaining("no command given");
  }

  @Test
  @DisplayName("every mode and option is documented in the help text")
  void usageDocumentsEveryMode() {
    assertThat(CliArgs.USAGE)
        .contains("--file")
        .contains("--mic")
        .contains("--ui")
        .contains("--version")
        .contains("--help")
        .contains("--out")
        .contains("--model")
        .contains("--seconds")
        .contains("--format")
        .contains("--no-actions")
        .contains("--export")
        .contains("--title");
  }
}
