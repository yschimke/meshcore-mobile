import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("ci_artifacts", Path(__file__).with_name("prepare-ci-artifacts.py"))
artifacts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(artifacts)


class CiArtifactsTest(unittest.TestCase):
    def test_archive_is_verified_and_mismatch_is_removed(self):
        for valid in [True, False]:
            with self.subTest(valid=valid), tempfile.TemporaryDirectory() as directory:
                archive = b"official release bytes"
                digest = hashlib.sha256(archive if valid else b"different bytes").hexdigest()
                metadata = {"assets": [{"name": "tool.zip", "url": "https://api.github.com/asset",
                                         "digest": "sha256:" + digest}]}
                with patch.dict(os.environ, {"GH_TOKEN": "", "GITHUB_TOKEN": ""}), patch.object(
                    artifacts, "request", side_effect=[io.BytesIO(json.dumps(metadata).encode()), io.BytesIO(archive)]
                ):
                    target = Path(directory) / "tool.zip"
                    if valid:
                        artifacts.download("owner/repo", "v1", "tool.zip", target)
                        self.assertEqual(archive, target.read_bytes())
                    else:
                        with self.assertRaisesRegex(RuntimeError, "digest mismatch"):
                            artifacts.download("owner/repo", "v1", "tool.zip", target)
                        self.assertFalse(target.exists())

    def test_missing_digest_never_downloads(self):
        metadata = {"assets": [{"name": "tool.zip", "digest": None}]}
        with patch.object(artifacts, "request", return_value=io.BytesIO(json.dumps(metadata).encode())) as request:
            with self.assertRaisesRegex(RuntimeError, "No SHA-256"):
                artifacts.download("owner/repo", "v1", "tool.zip", Path("unused"))
            self.assertEqual(1, request.call_count)


if __name__ == "__main__":
    unittest.main()
