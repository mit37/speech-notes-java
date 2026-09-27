#!/usr/bin/env bash
# Runs the committed fixtures through Vosk and regenerates eval/results.md (milestone 8).
#
# Offline from end to end: the same pipeline as `--file`, the same model, no network. The numbers the
# README quotes come out of this script, never out of somebody's memory (STANDARDS §1).
#
#   ./scripts/run_eval.sh                                            # small model
#   MODEL_DIR=models/vosk-model-en-us-0.22 ./scripts/run_eval.sh     # large model, same fixtures
#
# Extra arguments are passed through, e.g. --out eval/results-large.md.
set -euo pipefail
cd "$(dirname "$0")/.."

MODEL_DIR="${MODEL_DIR:-models/vosk-model-small-en-us-0.15}"
if [ ! -f "$MODEL_DIR/am/final.mdl" ]; then
  echo "No Vosk model at $MODEL_DIR — downloading it with scripts/download_model.sh first."
  bash scripts/download_model.sh "$(basename "$MODEL_DIR")"
fi

./gradlew --console=plain -q evalRun --args="--model $MODEL_DIR $*"
