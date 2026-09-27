#!/usr/bin/env bash
# Rewrites the golden files under src/test/resources/golden from the current exporters.
#
# The tests are meant to fail once after writing, so the new files show up in `git diff` and have to
# be looked at before they are committed (STANDARDS §1: artifacts come from the repo, and a golden
# file nobody reviewed is worse than none). That one failure is why this script tolerates a
# non-zero Gradle exit here.
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew test --tests '*GoldenTest' --tests '*PdfExporterTest' -Dspeechnotes.updateGolden=true || true

echo
echo "Golden files under src/test/resources/golden were rewritten — review 'git diff', then re-run:"
echo "  ./gradlew test"
