#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# JDK 21: any vendor satisfies gradle/gradle-daemon-jvm.properties (toolchainVersion=21).
if ! dpkg -s openjdk-21-jdk-headless >/dev/null 2>&1; then
  sudo DEBIAN_FRONTEND=noninteractive apt-get update -y
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends openjdk-21-jdk-headless
fi

# Android SDK: needed for :app, :wear, :meshcore-mobile assemble + lint.
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/.android-sdk}"
CMDLINE_TOOLS_DIR="$ANDROID_SDK_ROOT/cmdline-tools/latest"
COMPILE_SDK=36
BUILD_TOOLS_VERSION=36.0.0
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"

if [ ! -x "$CMDLINE_TOOLS_DIR/bin/sdkmanager" ]; then
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  tmp=$(mktemp -d)
  curl -fsSL -o "$tmp/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
  unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
  rm -rf "$CMDLINE_TOOLS_DIR"
  mv "$tmp/cmdline-tools" "$CMDLINE_TOOLS_DIR"
  rm -rf "$tmp"
fi

export PATH="$CMDLINE_TOOLS_DIR/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

printf 'y\n%.0s' $(seq 100) | sdkmanager --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null
sdkmanager --sdk_root="$ANDROID_SDK_ROOT" \
  "platform-tools" \
  "platforms;android-$COMPILE_SDK" \
  "build-tools;$BUILD_TOOLS_VERSION" >/dev/null

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export ANDROID_HOME=\"$ANDROID_SDK_ROOT\""
    echo "export ANDROID_SDK_ROOT=\"$ANDROID_SDK_ROOT\""
    echo "export PATH=\"$CMDLINE_TOOLS_DIR/bin:$ANDROID_SDK_ROOT/platform-tools:\$PATH\""
  } >> "$CLAUDE_ENV_FILE"
fi

# Install repo-managed git hooks (ktfmt pre-commit) so agent commits are
# formatted before they land. Idempotent — overwrites .git/hooks/pre-commit.
repo_root="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -n "$repo_root" ] && [ -x "$repo_root/scripts/install-git-hooks.sh" ]; then
  "$repo_root/scripts/install-git-hooks.sh" >/dev/null
fi
