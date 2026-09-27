#!/usr/bin/env bash
# Local secret scan (STANDARDS §2: gitleaks in CI *and* locally before the first push).
#
# CI runs gitleaks/gitleaks-action@v2 over the git history. This script is the local equivalent and
# works before there is any history at all: it scans the working tree, with .gitleaks.toml skipping
# build/ and models/ so a several-gigabyte model directory does not turn it into a coffee break.
#
#   scripts/scan_secrets.sh            # docker (no install needed)
#   GITLEAKS=gitleaks scripts/scan_secrets.sh   # use a binary already on PATH
#
# Exits 1 if anything is found. Nothing found prints "no leaks found".
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -n "${GITLEAKS:-}" ]]; then
  echo "scanning the working tree with $GITLEAKS ..." >&2
  exec "$GITLEAKS" detect --source . --no-git --config .gitleaks.toml --redact --verbose
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "neither \$GITLEAKS nor docker is available." >&2
  echo "Install gitleaks (https://github.com/gitleaks/gitleaks) or docker, then rerun." >&2
  exit 2
fi

# MSYS_NO_PATHCONV keeps Git Bash on Windows from rewriting /repo into a Windows path.
echo "scanning the working tree in a gitleaks container ..." >&2
MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$(pwd -W 2>/dev/null || pwd):/repo:ro" \
  ghcr.io/gitleaks/gitleaks:latest \
  detect --source /repo --no-git --config /repo/.gitleaks.toml --redact --verbose
