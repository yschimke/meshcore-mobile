#!/usr/bin/env python3
"""Check CI cache selection against the actual Gradle settings, using fake tokens.

Run without arguments for the fast shell matrix; --gradle also evaluates the
repository's buildCache block in an isolated project (no Android build/network).
Only booleans and case names are printed, never credentials.
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent.parent
PROJECT = 'BUILDFETCH_MESHCORE_GRADLE_REMOTE_CACHE_TOKEN'
GENERAL = 'BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN'
# name, ON_CI, project env, general env, Gradle properties, remote enabled, push
CASES = [
    ('main-present', 'true', None, 'fixture', {}, True, True),
    ('main-absent', 'true', None, None, {}, False, False),
    ('main-empty', 'true', None, '', {}, False, False),
    ('main-whitespace', 'true', None, ' \t\n ', {}, False, False),
    ('main-unicode-blank', 'true', None, '\u00a0\u2007\u202f\u3000', {}, False, False),
    ('pr-present', 'false', None, 'fixture', {}, True, False),
    ('pr-absent', 'false', None, None, {}, False, False),
    ('developer-present', None, None, 'fixture', {}, True, False),
    ('project-present', 'true', 'fixture', None, {}, True, True),
    ('blank-project-fallback', 'true', ' \t', 'fixture', {}, True, True),
    ('mixed-case-ci', 'TrUe', None, 'fixture', {}, True, True),
    ('not-true-ci', '1', None, 'fixture', {}, True, False),
    # Developer property fallback remains supported by Gradle. Hosted CI setup
    # has no user gradle.properties before setup-gradle restores its cache.
    ('property-fallback', 'true', '', '', {GENERAL: 'fixture'}, True, True),
    ('blank-property', 'true', None, None, {PROJECT: '  '}, False, False),
]


def main():
    use_gradle = '--gradle' in sys.argv
    settings = (ROOT / 'settings.gradle.kts').read_text()
    cache_block = settings[settings.index('val onCi ='):settings.index('include(":meshcore-core")')]
    for name, ci, project, general, props, remote, push in CASES:
        env = dict(os.environ)
        for key in ('ON_CI', PROJECT, GENERAL,
                    'ORG_GRADLE_PROJECT_' + PROJECT, 'ORG_GRADLE_PROJECT_' + GENERAL):
            env.pop(key, None)
        for key, value in [('ON_CI', ci), (PROJECT, project), (GENERAL, general)]:
            if value is not None:
                env[key] = value
        selected = subprocess.check_output(['bash', str(ROOT / 'scripts/cache-mode.sh')],
                                           env=env, text=True).strip()
        if not props:
            assert selected == str(push).lower(), name + ': setup cache mode mismatch'
        if use_gradle:
            with tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp)
                (path / 'settings.gradle.kts').write_text(
                    'rootProject.name = "cache-mode-fixture"\n' + cache_block + f'''
check(buildCache.local.isEnabled == {str(not push).lower()}) {{ "{name}: local" }}
val configuredRemote = buildCache.remote!!
check(configuredRemote.isEnabled == {str(remote).lower()}) {{ "{name}: remote" }}
check((configuredRemote.isEnabled && configuredRemote.isPush) == {str(push).lower()}) {{ "{name}: push" }}
''')
                (path / 'gradle.properties').write_text(
                    '\n'.join(key + '=' + value for key, value in props.items()))
                # -g isolates this from any developer's credential properties.
                result = subprocess.run([str(ROOT / 'gradlew'), '-p', tmp,
                                         '-g', str(ROOT / '.gradle/cache-mode-test'),
                                         '--console=plain', 'help'], env=env,
                                        text=True, capture_output=True)
                if result.returncode:
                    # All injected credentials are synthetic; still avoid logging
                    # Gradle output so this test cannot accidentally reveal one.
                    raise AssertionError(name + ': Gradle settings evaluation failed')
        if props and not use_gradle:
            print(name + ': deferred to --gradle (property-only case)')
        else:
            print(name + ': PASS' + (' (Gradle)' if props else ' (Gradle + setup)' if use_gradle else ' (setup)'))
    print(f'{len(CASES) if use_gradle else sum(not case[4] for case in CASES)} configuration cases passed')


if __name__ == '__main__':
    main()
