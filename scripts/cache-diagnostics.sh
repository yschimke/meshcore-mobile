#!/usr/bin/env bash
# Build-cache diagnostics for the BuildFetch remote cache.
#
# Both probes run against the LOCAL build cache with the *remote* cache forced
# off (no token, ON_CI unset), so results are deterministic and never touch or
# pollute the shared BuildFetch remote.
#
#   1. relocatability (the priority — hypothesis #3)
#      Build the SAME commit at two different absolute paths and diff each
#      cacheable task's build-cache key. A key that differs between the two
#      paths means the task encoded an absolute path (or other machine-local
#      state) into its inputs, so it can NEVER hit across machines or checkouts.
#      These are the tasks dragging the cross-machine hit rate down; fix them
#      (e.g. the org.gradle.android.cache-fix plugin, or making the offending
#      input path-relative).
#
#   2. cacheability (the general per-task breakdown)
#      Build cold (populate) then warm (consume) at one path, and list every
#      task that is NOT reused FROM-CACHE/UP-TO-DATE on the warm run — i.e.
#      non-cacheable tasks or tasks with an unstable key.
#
# Usage:  scripts/cache-diagnostics.sh [gradle tasks...]
# Default tasks mirror the ci.yml cache consumers.
set -euo pipefail

TASKS=("$@")
if [ ${#TASKS[@]} -eq 0 ]; then
  TASKS=(":app:assembleDebug" ":wear:assembleDebug" "test" "lintDebug")
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${RUNNER_TEMP:-/tmp}/cache-diag"
rm -rf "$WORK"
mkdir -p "$WORK"

# Force the remote cache off regardless of ambient env and keep the local cache
# on (settings.gradle.kts: no token => remote disabled; ON_CI unset => local
# enabled). This isolates path-sensitivity from remote availability.
unset BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN \
      BUILDFETCH_MESHCORE_GRADLE_REMOTE_CACHE_TOKEN \
      ON_CI 2>/dev/null || true

# Two pristine checkouts of HEAD at deliberately different-length absolute paths.
# `git archive` gives byte-identical trees; only the containing path differs, so
# any per-task key difference is caused solely by that path.
SHORT="$WORK/s"
LONG="$WORK/long-path/nested-deeper/and-deeper-still"
mkdir -p "$SHORT" "$LONG"
git -C "$REPO_ROOT" archive --format=tar HEAD | tar -x -C "$SHORT"
git -C "$REPO_ROOT" archive --format=tar HEAD | tar -x -C "$LONG"

# The Android SDK is preinstalled on ubuntu-latest; point both trees at it
# (local.properties is gitignored, so the archives don't carry one).
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
printf 'sdk.dir=%s\n' "$SDK" > "$SHORT/local.properties"
printf 'sdk.dir=%s\n' "$SDK" > "$LONG/local.properties"

# Separate Gradle homes => full isolation (neither build's local cache can
# satisfy the other, so every task executes and logs its key).
GH_S="$WORK/gh-s"
GH_L="$WORK/gh-l"

# Clean build that prints each task's computed build-cache key.
build_keys() { # <srcdir> <gradle-home> <label>
  local src="$1" gh="$2" label="$3" raw="$WORK/$3.raw" keys="$WORK/$3.keys"
  echo ">>> building at $src ($label)"
  if ! ( cd "$src" && ./gradlew --no-daemon --build-cache -g "$gh" \
           -Dorg.gradle.caching.debug=true --console=plain "${TASKS[@]}" ) \
         > "$raw" 2>&1; then
    echo "!!! build failed ($label); tail of log:"
    tail -n 60 "$raw"
    return 1
  fi
  # "Build cache key for task ':m:t' is <hash>" -> "<task> <hash>"
  grep -E "Build cache key for task '" "$raw" \
    | sed -E "s/.*task '([^']+)' is ([0-9a-f]+).*/\1 \2/" \
    | sort -u > "$keys"
}

build_keys "$SHORT" "$GH_S" short
build_keys "$LONG"  "$GH_L" long

# Tasks present in both runs whose key differs => path-sensitive (non-relocatable).
join -j 1 "$WORK/short.keys" "$WORK/long.keys" \
  | awk '$2 != $3 { print $1 }' | sort -u > "$WORK/nonreloc.txt" || true

# Warm rebuild at the short path (GH_S is now populated) => cacheability check.
echo ">>> warm rebuild at $SHORT (cacheability)"
( cd "$SHORT" && ./gradlew --no-daemon --build-cache -g "$GH_S" \
    --console=plain "${TASKS[@]}" ) > "$WORK/warm.raw" 2>&1 || true
# Task lines with no reuse suffix re-executed despite a warm cache.
grep -E '^> Task :' "$WORK/warm.raw" \
  | grep -vE '(FROM-CACHE|UP-TO-DATE|NO-SOURCE|SKIPPED)$' \
  | sed -E 's/^> Task (:[^ ]+).*/\1/' | sort -u > "$WORK/uncached.txt" || true

# ---- report ----------------------------------------------------------------
reloc_count=$(wc -l < "$WORK/nonreloc.txt" | tr -d ' ')
uncached_count=$(wc -l < "$WORK/uncached.txt" | tr -d ' ')
total_keys=$(wc -l < "$WORK/short.keys" | tr -d ' ')

{
  echo "# Build-cache diagnostics"
  echo
  echo "Tasks probed: \`${TASKS[*]}\`"
  echo
  echo "## 1. Relocatability (cross-machine hit killers)"
  echo
  echo "$reloc_count of $total_keys cacheable tasks changed their build-cache key"
  echo "when built at a different absolute path. These can never hit across"
  echo "machines/checkouts:"
  echo
  if [ "$reloc_count" -gt 0 ]; then
    echo '```'
    cat "$WORK/nonreloc.txt"
    echo '```'
  else
    echo "_None — all probed tasks are relocatable._"
  fi
  echo
  echo "## 2. Cacheability (re-ran despite a warm local cache)"
  echo
  if [ "$uncached_count" -gt 0 ]; then
    echo "$uncached_count tasks were not reused FROM-CACHE/UP-TO-DATE on a warm"
    echo "rebuild — non-cacheable or unstable-key:"
    echo
    echo '```'
    cat "$WORK/uncached.txt"
    echo '```'
  else
    echo "_All probed tasks were reused on the warm rebuild._"
  fi
  echo
  echo "Raw Gradle logs: \`$WORK/{short,long,warm}.raw\` (uploaded as an artifact in CI)."
} | tee "$WORK/summary.md"

# Mirror the summary into the CI job summary when running under Actions.
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  cat "$WORK/summary.md" >> "$GITHUB_STEP_SUMMARY"
fi

# Non-zero exit if any relocatability offender was found, so the job flags it.
[ "$reloc_count" -eq 0 ]
