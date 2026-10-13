#!/usr/bin/env bash
set -euo pipefail
app_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
tools_root=$(cd "${1:-$app_root/../compose-ai-tools}" && pwd)
builder_root=$(cd "${2:-$app_root/../compose-ui-builder}" && pwd)
run_gradle() {
  if command -v build-brief >/dev/null; then build-brief "$@"; else "$@"; fi
}
# Sequential: these composite builds share source output directories.
run_gradle "$app_root/gradlew" -p "$app_root" --max-workers=2 \
  "-PcomposeUiBuilderDir=$builder_root" \
  :ui-builder-catalog:ktfmtCheck :ui-builder-catalog:test :ui-builder-catalog:unpackJvmCatalogBundle
# Public service reader in an independent consumer; no app classes enter its classpath.
run_gradle "$app_root/gradlew" -p "$app_root" --max-workers=2 \
  "-PcomposeUiBuilderDir=$builder_root" :ui-builder-catalog-consumer:test :ui-builder-catalog-consumer:ktfmtCheck
