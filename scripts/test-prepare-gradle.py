import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("prepare_gradle", Path(__file__).with_name("prepare-gradle.py"))
bootstrap = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bootstrap)


class PrepareGradleTest(unittest.TestCase):
    def test_cached_wrapper_never_downloads_or_rewrites(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            calls = []
            def run(command, **kwargs):
                calls.append(command)
                return subprocess.CompletedProcess(command, 0)
            bootstrap.prepare(root, root, run)
            self.assertEqual(1, len(calls))

    def test_fallback_checks_archive_before_using_it(self):
        for valid in [True, False]:
            with self.subTest(valid=valid), tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {"GH_TOKEN": "synthetic"}):
                root = Path(directory)
                properties = root / "gradle/wrapper/gradle-wrapper.properties"
                properties.parent.mkdir(parents=True)
                archive = b"test distribution"
                checksum = hashlib.sha256(archive if valid else b"wrong").hexdigest()
                original = ("distributionUrl=https\\://services.gradle.org/distributions/gradle-9.9.0-bin.zip\n"
                            + "distributionSha256Sum=" + checksum + "\n")
                properties.write_text(original)
                calls = []
                def run(command, **kwargs):
                    calls.append(command)
                    if command[0] == "gh":
                        target = root / "gradle-release/gradle-9.9.0-bin.zip"
                        target.write_bytes(archive)
                    return subprocess.CompletedProcess(command, 1 if len(calls) == 1 else 0)
                if valid:
                    bootstrap.prepare(root, root, run)
                    self.assertEqual(3, len(calls))
                    self.assertIn("distributionUrl=file\\:", properties.read_text())
                    self.assertIn("--repo", calls[1])
                else:
                    with self.assertRaisesRegex(RuntimeError, "does not match"):
                        bootstrap.prepare(root, root, run)
                    self.assertEqual(original, properties.read_text())
                    self.assertEqual(2, len(calls))

    def test_windows_uses_native_wrapper_without_wsl(self):
        command = bootstrap.wrapper_command(Path("checkout"), windows=True)
        self.assertEqual(["cmd.exe", "/d", "/c"], command[:3])
        self.assertTrue(command[3].endswith("gradlew.bat"))
        self.assertEqual("bash", bootstrap.wrapper_command(Path("checkout"), windows=False)[0])


if __name__ == "__main__":
    unittest.main()
