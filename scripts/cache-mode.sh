#!/usr/bin/env bash
# Match settings.gradle.kts for CI's environment-only token sources. Emit only
# the effective push boolean, never a credential. setup runs before Gradle.
set -euo pipefail
has_token=false
for token_name in BUILDFETCH_MESHCORE_GRADLE_REMOTE_CACHE_TOKEN BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN; do
  token_value="${!token_name:-}"
  # Kotlin trim() treats whitespace-only credentials as absent.
  if [[ "$token_value" == *[![:space:]]* ]]; then has_token=true; fi
done
if [[ "${ON_CI:-false}" =~ ^[Tt][Rr][Uu][Ee]$ && "$has_token" == true ]]; then
  echo true
else
  echo false
fi
