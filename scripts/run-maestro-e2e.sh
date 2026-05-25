#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

if ! command -v maestro >/dev/null 2>&1; then
  echo "Maestro CLI is not installed or is not available on PATH." >&2
  echo "Install it with: curl -Ls \"https://get.maestro.mobile.dev\" | bash" >&2
  exit 1
fi

./gradlew :app:installDebug

maestro test e2e/maestro/initial-unauthenticated-state.yaml
maestro test e2e/maestro/count-impression.yaml
maestro test e2e/maestro/logout-keeps-user-unauthenticated.yaml
