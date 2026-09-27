#!/usr/bin/env bash
# Fetch the Vosk small English model. Model weights are never committed (STANDARDS §2), so a
# fresh clone runs this once. No API key, no account, nothing paid.
#
#   scripts/download_model.sh                                          # small model (default)
#   scripts/download_model.sh vosk-model-en-us-0.22                    # large model, for the eval
#   scripts/download_model.sh --force                                  # ignore an existing copy
#   SPEECH_NOTES_MODEL_ZIP=/path/to/model.zip scripts/download_model.sh # use a local/mirrored zip
#
# Both models are Apache 2.0, same license as Vosk itself. Sizes and checksums are pinned below:
# a download nobody verified is not evidence. The large model is 1.8 GB zipped / 2.7 GB extracted
# and is only needed for the small-vs-large part of the eval (scripts/run_eval.sh).
# Source: https://alphacephei.com/vosk/models
set -euo pipefail

MODEL_NAME="${1:-vosk-model-small-en-us-0.15}"
if [[ "$MODEL_NAME" == "--force" ]]; then
  MODEL_NAME="vosk-model-small-en-us-0.15"
fi

case "$MODEL_NAME" in
  vosk-model-small-en-us-0.15)
    MODEL_SIZE_HINT="~40 MB zipped, ~68 MB extracted"
    EXPECTED_SHA256="30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"
    ;;
  vosk-model-en-us-0.22)
    MODEL_SIZE_HINT="1.8 GB zipped, 2.7 GB extracted"
    EXPECTED_SHA256="47f9a81ebb039dbb0bd319175c36ac393c0893b796c2b6303e64cf58c27b69f6"
    ;;
  *)
    echo "unknown model: $MODEL_NAME" >&2
    echo "known models: vosk-model-small-en-us-0.15 (default), vosk-model-en-us-0.22" >&2
    exit 2
    ;;
esac

MODEL_URL="${SPEECH_NOTES_MODEL_URL:-https://alphacephei.com/vosk/models/${MODEL_NAME}.zip}"

MODELS_DIR="${SPEECH_NOTES_MODELS_DIR:-models}"
MODEL_DIR="$MODELS_DIR/$MODEL_NAME"
CACHED_ZIP="$MODELS_DIR/$MODEL_NAME.zip"

force=0
if [[ "${1:-}" == "--force" || "${2:-}" == "--force" ]]; then
  force=1
fi

# An explicitly supplied zip means "use this one", so skip the already-downloaded shortcut.
if [[ -n "${SPEECH_NOTES_MODEL_ZIP:-}" ]]; then
  force=1
fi

if [[ -d "$MODEL_DIR" && $force -eq 0 ]]; then
  echo "$MODEL_DIR is already there — nothing to do (use --force to re-download)."
  exit 0
fi

echo "model: $MODEL_NAME ($MODEL_SIZE_HINT)"

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

extract_zip() {
  local zip="$1" abs
  abs="$(cd "$(dirname "$zip")" && pwd)/$(basename "$zip")"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$zip" -d "$MODELS_DIR"
  elif command -v jar >/dev/null 2>&1; then
    (cd "$MODELS_DIR" && jar xf "$abs")
  elif command -v python >/dev/null 2>&1; then
    python -c 'import sys, zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' "$abs" "$MODELS_DIR"
  else
    echo "need one of unzip, jar (JDK) or python to extract $zip" >&2
    return 1
  fi
}

mkdir -p "$MODELS_DIR"

zip_path="$CACHED_ZIP"
cleanup_zip=0
if [[ -n "${SPEECH_NOTES_MODEL_ZIP:-}" ]]; then
  zip_path="$SPEECH_NOTES_MODEL_ZIP"
  if [[ ! -f "$zip_path" ]]; then
    echo "SPEECH_NOTES_MODEL_ZIP=$zip_path does not exist" >&2
    exit 1
  fi
  echo "using local zip $zip_path"
else
  echo "downloading $MODEL_URL"
  # 900s is enough for the small model and, on the machine this was built on, for the large one too;
  # a slow link can raise it with SPEECH_NOTES_MODEL_TIMEOUT.
  curl -L --fail --progress-bar --max-time "${SPEECH_NOTES_MODEL_TIMEOUT:-3600}" -o "$CACHED_ZIP" "$MODEL_URL"
  cleanup_zip=1
fi

echo "verifying sha256 ..."
actual="$(sha256_of "$zip_path")"
if [[ "$actual" != "$EXPECTED_SHA256" ]]; then
  echo "checksum mismatch for $zip_path" >&2
  echo "  expected $EXPECTED_SHA256" >&2
  echo "  actual   $actual" >&2
  if [[ $cleanup_zip -eq 1 ]]; then
    rm -f "$CACHED_ZIP"
  fi
  exit 1
fi

rm -rf "$MODEL_DIR"
extract_zip "$zip_path"

missing=0
for required in am/final.mdl conf/mfcc.conf; do
  if [[ ! -e "$MODEL_DIR/$required" ]]; then
    echo "extracted model is incomplete: $MODEL_DIR/$required is missing" >&2
    missing=1
  fi
done
# The decoding graph is HCLG.fst in some Vosk models and HCLr.fst in others.
if [[ ! -e "$MODEL_DIR/graph/HCLG.fst" && ! -e "$MODEL_DIR/graph/HCLr.fst" ]]; then
  echo "extracted model is incomplete: no decoding graph in $MODEL_DIR/graph" >&2
  missing=1
fi
if [[ $missing -eq 1 ]]; then
  echo "delete $MODEL_DIR and run this script again" >&2
  exit 1
fi

if [[ $cleanup_zip -eq 1 ]]; then
  rm -f "$CACHED_ZIP"
fi

echo "model ready: $MODEL_DIR ($(du -sh "$MODEL_DIR" | cut -f1))"
echo "Next: ./gradlew run --args=\"--file <audio>\" — the app looks in $MODEL_DIR,"
echo "or wherever SPEECH_NOTES_MODEL_DIR points (see .env.example)."
echo "For the eval with this model: MODEL_DIR=$MODEL_DIR ./scripts/run_eval.sh"
