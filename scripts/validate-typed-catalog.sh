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
# This host classpath contains no application classes.
run_gradle "$builder_root/gradlew" -p "$builder_root" --max-workers=2 \
    "-PtypedCatalogSmokeDirectory=$app_root/ui-builder-catalog/build/catalog" \
  :ui-builder-runtime:test --tests '*ExternalTypedCatalogSmokeTest' :ui-builder-runtime:ktfmtCheck
# A second, executable host test compiles without app classes and loads the staged JAR bundle.
run_gradle "$builder_root/gradlew" -p "$builder_root" --max-workers=2 \
    "-PtypedCatalogJvmBundle=$app_root/ui-builder-catalog/build/installed-jvm-catalog" \
  :ui-builder:jvmTest --tests '*ExternalJvmCatalogEndToEndTest' :ui-builder:ktfmtCheck
