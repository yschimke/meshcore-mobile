#!/usr/bin/env python3
"""Install the catalog-pinned preview CLI through the verified GitHub release API."""

import hashlib
import json
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import tomllib


def install():
    root = Path(__file__).resolve().parent.parent
    catalog = tomllib.loads((root / "gradle/libs.versions.toml").read_text())
    version = catalog["versions"]["composePreviewPlugin"]
    repo = "github.com/yschimke/compose-ai-tools"
    filename = f"compose-preview-{version}.tar.gz"
    metadata = json.loads(subprocess.check_output([
        "gh", "api", "--hostname", "github.com",
        f"repos/yschimke/compose-ai-tools/releases/tags/v{version}",
    ]))
    asset = next(asset for asset in metadata["assets"] if asset["name"] == filename)
    digest = asset.get("digest", "")
    if not digest.startswith("sha256:") or len(digest) != 71:
        raise RuntimeError("Preview CLI release has no SHA-256 digest")
    destination = Path(os.environ["RUNNER_TEMP"]) / "compose-preview"
    destination.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as directory:
        subprocess.run(["gh", "release", "download", f"v{version}", "--repo", repo,
                        "--pattern", filename, "--dir", directory], check=True)
        archive = Path(directory) / filename
        with archive.open("rb") as source:
            actual = hashlib.file_digest(source, "sha256").hexdigest()
        if actual != digest.removeprefix("sha256:"):
            raise RuntimeError("Preview CLI archive does not match its release digest")
        with tarfile.open(archive) as source:
            source.extractall(destination, filter="data")
    executable = next(destination.glob("*/bin/compose-preview"))
    subprocess.run([str(executable), "--version"], check=True)
    with open(os.environ["GITHUB_PATH"], "a") as output:
        output.write(str(executable.parent) + "\n")


if __name__ == "__main__":
    install()
