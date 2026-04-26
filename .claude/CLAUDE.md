# Repository instructions for AI agents

## Cloud / remote sessions: install Android SDK

The Android modules (`:app`, `:wear`, `:meshcore-mobile`) need an Android SDK
to assemble or lint. Local developer machines and GitHub-hosted CI runners
already have one; cloud agent sandboxes (Claude Code on the web etc.) usually
don't.

A `SessionStart` hook at `.claude/hooks/session-start.sh` installs JDK 21 (if
missing) and downloads the Android SDK into `$HOME/.android-sdk` with the
`platform-tools`, `platforms;android-36`, and `build-tools;36.0.0` packages,
then exports `ANDROID_HOME` / `ANDROID_SDK_ROOT` / `PATH` via
`$CLAUDE_ENV_FILE`. It also runs `scripts/install-git-hooks.sh` so the
ktfmt pre-commit hook is in place before any agent commit. It's gated on
`CLAUDE_CODE_REMOTE=true` so it's a no-op on developer machines.

If you're running outside the hook (e.g. validating manually) and the SDK is
missing, run the hook directly:

```bash
CLAUDE_CODE_REMOTE=true CLAUDE_ENV_FILE=/tmp/claude-env \
  ./.claude/hooks/session-start.sh
source /tmp/claude-env
```

Bump `COMPILE_SDK` / `BUILD_TOOLS_VERSION` in the hook in lockstep with
`android-compileSdk` in `gradle/libs.versions.toml`.

## JDK

`gradle/gradle-daemon-jvm.properties` requires JDK 21 only — any vendor will
do. Don't reintroduce a `toolchainVendor=` line unless there's a concrete
reason; it forces every consumer (CI, agent sandboxes, contributors) to fetch
that specific vendor.
