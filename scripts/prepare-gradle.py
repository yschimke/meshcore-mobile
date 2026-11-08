#!/usr/bin/env python3
"""Bootstrap CI through the official release API when the distribution URL is unavailable."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import urllib.request


def wrapper_command(root, windows=None):
    windows = os.name == "nt" if windows is None else windows
    return (["cmd.exe", "/d", "/c", str(root / "gradlew.bat"), "--version"] if windows
            else ["bash", str(root / "gradlew"), "--version"])


def download_release(filename, version, directory, run):
    if os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN"):
        run(["gh", "release", "download", "v" + version,
             "--repo", "github.com/gradle/gradle-distributions", "--pattern", filename,
             "--dir", str(directory), "--clobber"], check=True)
        return
    # Reusable workflows do not expose github.token to caller-owned build commands.
    # Public releases also support the same API without authentication.
    base = "https://api.github.com/repos/gradle/gradle-distributions/releases/"
    with urllib.request.urlopen(base + "tags/v" + version, timeout=60) as response:
        release = json.load(response)
    asset = next(asset for asset in release["assets"] if asset["name"] == filename)
    request = urllib.request.Request(base + "assets/" + str(asset["id"]),
                                     headers={"Accept": "application/octet-stream"})
    with urllib.request.urlopen(request, timeout=60) as source, (directory / filename).open("wb") as target:
        shutil.copyfileobj(source, target)


def prepare(root, temporary, run=subprocess.run):
    command = wrapper_command(root)
    if run(command, cwd=root).returncode == 0:
        return

    properties = root / "gradle/wrapper/gradle-wrapper.properties"
    original = properties.read_text()
    match = re.search(r"^distributionUrl=.*?/(gradle-([\d.]+)-(?:bin|all)\.zip)$", original, re.M)
    checksum = re.search(r"^distributionSha256Sum=([a-f0-9]{64})$", original, re.M)
    if match is None or checksum is None:
        raise RuntimeError("Release API fallback requires a pinned Gradle distribution and SHA-256")

    filename, version = match.groups()
    directory = temporary / "gradle-release"
    directory.mkdir(parents=True, exist_ok=True)
    download_release(filename, version, directory, run)
    archive = directory / filename
    with archive.open("rb") as source:
        actual = hashlib.file_digest(source, "sha256").hexdigest()
    if actual != checksum.group(1):
        raise RuntimeError("Downloaded Gradle archive does not match distributionSha256Sum")

    # CI-only override. The checked-in version/checksum still select and verify the exact bytes.
    url = archive.resolve().as_uri().replace(":", r"\:")
    properties.write_text(re.sub(r"^distributionUrl=.*$", lambda _: "distributionUrl=" + url,
                                 original, flags=re.M))
    try:
        run(command, cwd=root, check=True)
    except BaseException:
        properties.write_text(original)
        raise


if __name__ == "__main__":
    prepare(Path(__file__).resolve().parent.parent,
            Path(os.environ.get("RUNNER_TEMP", tempfile.gettempdir())))
