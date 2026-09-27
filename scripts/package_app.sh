#!/usr/bin/env bash
# Builds a self-contained app image with jpackage (PRD §4: jpackage on release).
#
# The Gradle application plugin already puts every runtime dependency next to the app jar, so
# jpackage can take that directory as its input — no fat-jar plugin and no assembly step to trust.
#
#   scripts/package_app.sh                     # app image in build/jpackage/speech-notes-java
#   TYPE=msi scripts/package_app.sh            # installer; needs WiX on Windows
#   TYPE=deb scripts/package_app.sh            # installer; needs fakeroot on Linux
#
# The app image needs nothing installed: it carries its own Java runtime, which is what makes it
# something a person can double-click. Installers additionally need the platform's packaging tools,
# so this script builds the image by default and says so when an installer is asked for.
set -euo pipefail
cd "$(dirname "$0")/.."

APP_NAME="speech-notes-java"
RAW_VERSION="$(sed -n 's/^version = "\(.*\)"$/\1/p' build.gradle.kts | head -1)"
# jpackage wants digits: 2.0.0-SNAPSHOT would be rejected.
APP_VERSION="${RAW_VERSION%%-*}"
TYPE="${TYPE:-app-image}"
OUT_DIR="${OUT_DIR:-build/jpackage}"

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage was not found on PATH — it ships with the JDK (21+); check JAVA_HOME/bin." >&2
  exit 1
fi

echo "building the distribution (jars + dependencies) ..."
./gradlew --console=plain -q installDist

LIB_DIR="build/install/$APP_NAME/lib"
MAIN_JAR="$(basename "$(ls "$LIB_DIR/$APP_NAME"-*.jar | head -1)")"
rm -rf "$OUT_DIR"

echo "packaging $APP_NAME $APP_VERSION as $TYPE ..."
args=(
  --type "$TYPE"
  --name "$APP_NAME"
  --app-version "$APP_VERSION"
  --input "$LIB_DIR"
  --main-jar "$MAIN_JAR"
  --main-class io.github.mit37.speechnotes.CliMain
  --dest "$OUT_DIR"
  --vendor "mit37"
  --description "Offline speech-to-notes: on-device Vosk transcription, optional Gemini notes."
)
# --license-file is accepted by installers but rejected for a plain app image.
if [[ "$TYPE" != "app-image" ]]; then
  args+=(--license-file LICENSE)
fi
jpackage "${args[@]}" "$@"

echo
echo "done: $OUT_DIR/$APP_NAME"
if [[ "$TYPE" == "app-image" ]]; then
  # Windows puts the launcher at the image root; Linux and macOS use bin/.
  launcher="$OUT_DIR/$APP_NAME/bin/$APP_NAME"
  [[ -x "${launcher}.exe" ]] && launcher="${launcher}.exe"
  [[ -x "$launcher" ]] || launcher="$OUT_DIR/$APP_NAME/$APP_NAME.exe"
  echo "run it with: $launcher --version"
  echo "installers (TYPE=msi/deb/dmg) need the platform's packager tools; see README."
fi
