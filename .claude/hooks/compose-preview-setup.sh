#!/usr/bin/env bash
# Cloud session bootstrap for Compose @Preview rendering.
#
# Provisions the toolchain + the compose-preview skill via coo.ee (devenv
# backend), then bootstraps the compose-preview CLI and warms preview
# discovery once. The warm-up resolves the auto-injected Gradle plugin's
# artifacts so the first interactive `compose-preview list` works WITHOUT
# `ee.schimke.composeai.preview` being applied in any build file.
#
# stdout is reserved for the SessionStart JSON payload at the end (it asks
# Claude Code to re-scan skills), so every provisioning line is routed to
# stderr to keep stdout clean.
#
# No-op on developer machines (gated on CLAUDE_CODE_REMOTE), matching the
# sibling session-start.sh hook.
set -euo pipefail

[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"

# 1. Toolchain (JDK adopted; Android SDK + devenv via Nix) + the
#    compose-preview skill bundle. Idempotent: re-runs short-circuit.
#    Installer chatter -> stderr so stdout stays reserved for the JSON below.
curl -fsSL 'https://env.coo.ee/compose?devenv=1' | bash 1>&2

# 2. Activate the env coo.ee just persisted. The `curl | bash` above ran in a
#    pipe subshell and cannot mutate this process, so source it here for the
#    warm-up in step 3 -- AND append it to $CLAUDE_ENV_FILE so every later
#    Bash tool command in the session inherits it too (mirrors how the sibling
#    session-start.sh exports the SDK env). Without the env-file step the
#    warm-up works but subsequent commands still hit `compose-preview: command
#    not found`.
env_sh="$HOME/.config/coo-ee/env.sh"
[ -f "$env_sh" ] && . "$env_sh" >&2 || true
export PATH="$HOME/.local/bin:$PATH"

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    [ -f "$env_sh" ] && echo ". \"$env_sh\""
    echo "export PATH=\"\$HOME/.local/bin:\$PATH\""
  } >> "$CLAUDE_ENV_FILE"
fi

# 3. Bootstrap the compose-preview CLI (the skill ships a self-installing
#    stub) and warm discovery once from the project root. Best-effort: a
#    network/build hiccup here must not fail session start.
cli=""
if command -v compose-preview >/dev/null 2>&1; then
  cli=compose-preview
elif [ -x "$HOME/.claude/skills/compose-preview/scripts/compose-preview" ]; then
  cli="$HOME/.claude/skills/compose-preview/scripts/compose-preview"
fi
[ -n "$cli" ] && { "$cli" list >/dev/null 2>&1 || true; }

# 4. coo.ee wrote the compose-preview skill into ~/.claude/skills AFTER Claude
#    Code's initial skill scan, so signal a reload to make it usable in THIS
#    session (documented SessionStart hookSpecificOutput field).
printf '%s\n' '{"hookSpecificOutput":{"hookEventName":"SessionStart","reloadSkills":true}}'
