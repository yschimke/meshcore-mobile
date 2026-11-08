#!/usr/bin/env python3
"""Bootstrap CI through the official release API when the distribution URL is unavailable."""

import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile


def prepare(root, temporary, run=subprocess.run):
    wrapper = root / "gradlew"
    command = ["bash", wrapper.as_posix(), "--version"]
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
    run(["gh", "release", "download", "v" + version,
         "--repo", "github.com/gradle/gradle-distributions", "--pattern", filename,
         "--dir", str(directory), "--clobber"], cwd=root, check=True)
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
