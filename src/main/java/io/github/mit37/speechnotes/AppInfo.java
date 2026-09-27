package io.github.mit37.speechnotes;

/** Static facts about the build: names, version and where the Vosk model is expected. */
public final class AppInfo {

  /** Repository name, also the Gradle root project name. */
  public static final String NAME = "speech-notes-java";

  /** Human-readable name used in the UI and in CLI output. */
  public static final String DISPLAY_NAME = "Speech to Notes";

  /** Kept in step with the Gradle project version. */
  public static final String VERSION = "2.0.0-SNAPSHOT";

  /** Public repository URL, printed by {@code --version} so bug reports can point at it. */
  public static final String REPO_URL = "https://github.com/mit37/speech-notes-java";

  /** Model downloaded by {@code scripts/download_model.sh}; never committed (STANDARDS §2). */
  public static final String DEFAULT_MODEL_NAME = "vosk-model-small-en-us-0.15";

  /** Directory the download script extracts the model into, relative to the working directory. */
  public static final String DEFAULT_MODEL_DIR = "models/" + DEFAULT_MODEL_NAME;

  private AppInfo() {}
}
