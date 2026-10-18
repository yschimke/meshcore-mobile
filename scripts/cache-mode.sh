#!/usr/bin/env bash
# Match settings.gradle.kts for CI's environment-only token sources. Emit only
# the effective push boolean, never a credential. setup runs before Gradle.
set -euo pipefail
# Kotlin Char.isWhitespace includes nonbreaking Unicode spaces that POSIX
# [:space:] omits. List its codepoints so blank tokens match Gradle's trim().
kotlin_whitespace=$' \t\n\v\f\r\x1c\x1d\x1e\x1f\u00a0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u2028\u2029\u202f\u205f\u3000'
has_token=false
for token_name in BUILDFETCH_MESHCORE_GRADLE_REMOTE_CACHE_TOKEN BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN; do
  token_value="${!token_name:-}"
  # Kotlin trim() treats whitespace-only credentials as absent.
  if [[ -n "${token_value//[$kotlin_whitespace]/}" ]]; then has_token=true; fi
done
if [[ "${ON_CI:-false}" =~ ^[Tt][Rr][Uu][Ee]$ && "$has_token" == true ]]; then
  echo true
else
  echo false
fi
