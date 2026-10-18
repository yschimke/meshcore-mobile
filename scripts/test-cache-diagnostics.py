#!/usr/bin/env python3
"""Exercise the real diagnostic driver with deterministic Gradle output."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('cache-diagnostics.sh')


class DiagnosticsTest(unittest.TestCase):
    def run_probe(self, warm_status=0, relocated=False, cold_status=0):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / 'repo'
            (root / 'scripts').mkdir(parents=True)
            shutil.copy(SCRIPT, root / 'scripts' / SCRIPT.name)
            (root / 'gradle' / 'wrapper').mkdir(parents=True)
            (root / 'gradle' / 'libs.versions.toml').write_text('agp = "fixture"\n')
            (root / 'gradle' / 'wrapper' / 'gradle-wrapper.properties').write_text(
                'distributionUrl=fixture\n')
            wrapper = root / 'gradlew'
            wrapper.write_text('''#!/usr/bin/env bash
set -eu
# The driver must isolate every probe from ambient remote-cache credentials.
[ -z "${BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN:-}" ]
[ -z "${BUILDFETCH_MESHCORE_GRADLE_REMOTE_CACHE_TOKEN:-}" ]
[ -z "${ON_CI:-}" ]
[[ "$*" == *-PBUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN=* ]]
[[ "$*" == *-PBUILDFETCH_MESHCORE_GRADLE_REMOTE_CACHE_TOKEN=* ]]
if [[ "$*" == *org.gradle.caching.debug* ]]; then
  key=aaaa
  if [[ "$PWD" == *long-path* && "$RELOCATED" == true ]]; then key=bbbb; fi
  echo "Build cache key for task ':compile' is $key"
  exit "$COLD_STATUS"
fi
echo '> Task :compile FROM-CACHE'
echo '> Task :package'
if [ "$WARM_STATUS" != 0 ]; then echo 'BUILD FAILED: deliberate warm failure'; fi
exit "$WARM_STATUS"
''')
            wrapper.chmod(0o755)
            for args in [('init', '-q'), ('add', '.'),
                         ('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.test',
                          '-c', 'core.hooksPath=/dev/null', 'commit', '-qm', 'fixture')]:
                subprocess.run(['git', '-C', str(root), *args], check=True,
                               stdout=subprocess.DEVNULL)
            env = dict(os.environ, RUNNER_TEMP=str(Path(tmp) / 'runner'),
                       BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN='synthetic-only',
                       BUILDFETCH_MESHCORE_GRADLE_REMOTE_CACHE_TOKEN='synthetic-only',
                       ON_CI='true',
                       WARM_STATUS=str(warm_status), COLD_STATUS=str(cold_status),
                       RELOCATED=str(relocated).lower())
            result = subprocess.run(['bash', str(root / 'scripts' / SCRIPT.name), ':compile'],
                                    env=env, text=True, capture_output=True)
            summary = Path(env['RUNNER_TEMP']) / 'cache-diag' / 'summary.md'
            return result, summary.read_text() if summary.exists() else ''

    def test_success(self):
        result, summary = self.run_probe()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('0 of 1', summary)
        self.assertIn(':package', summary)

    def test_relocation_still_fails(self):
        result, summary = self.run_probe(relocated=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('1 of 1', summary)

    def test_warm_failure_without_relocation(self):
        result, summary = self.run_probe(warm_status=23)
        self.assertEqual(result.returncode, 23, result.stdout + result.stderr)
        self.assertIn('0 of 1', summary)
        self.assertIn('Warm rebuild exit status: 23', summary)
        self.assertIn('deliberate warm failure', result.stdout)

    def test_warm_failure_with_relocation(self):
        result, summary = self.run_probe(warm_status=23, relocated=True)
        self.assertEqual(result.returncode, 23)
        self.assertIn('1 of 1', summary)
        self.assertIn('Warm rebuild exit status: 23', summary)

    def test_cold_build_failure(self):
        result, _ = self.run_probe(cold_status=17)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('build failed (short)', result.stdout)


if __name__ == '__main__':
    unittest.main()
