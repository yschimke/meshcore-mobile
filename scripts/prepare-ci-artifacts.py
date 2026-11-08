#!/usr/bin/env python3
"""Stage exact CI dependencies via the release API when release URLs return 504."""

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.request


def request(url, accept="application/vnd.github+json"):
    headers = {"Accept": accept}
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token:
        headers["Authorization"] = "Bearer " + token
    return urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=60)


def download(repo, tag, filename, destination):
    with request(f"https://api.github.com/repos/{repo}/releases/tags/{tag}") as response:
        release = json.load(response)
    asset = next(asset for asset in release["assets"] if asset["name"] == filename)
    digest = asset.get("digest") or ""
    if not digest.startswith("sha256:") or len(digest) != 71:
        raise RuntimeError(f"No SHA-256 digest for {filename}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    if os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN"):
        subprocess.run(["gh", "release", "download", tag, "--repo", "github.com/" + repo,
                        "--pattern", filename, "--dir", str(destination.parent), "--clobber"], check=True)
    else:
        with request(asset["url"], "application/octet-stream") as source, destination.open("wb") as target:
            shutil.copyfileobj(source, target)
    with destination.open("rb") as source:
        actual = hashlib.file_digest(source, "sha256").hexdigest()
    if actual != digest.removeprefix("sha256:"):
        destination.unlink()
        raise RuntimeError(f"Release digest mismatch for {filename}")


def prepare_maven(temporary):
    # ktfmt 0.65's compiler dependency; retain its published POM and transitive dependencies.
    version = "2.4.10"
    name = f"kotlin-compiler-embeddable-{version}"
    repository = temporary / "ci-maven"
    directory = repository / f"org/jetbrains/kotlin/kotlin-compiler-embeddable/{version}"
    download("JetBrains/kotlin", f"v{version}", name + ".jar", directory / (name + ".jar"))
    pom_url = f"https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/{version}/{name}.pom"
    with urllib.request.urlopen(pom_url, timeout=60) as source:
        (directory / (name + ".pom")).write_bytes(source.read())
    init = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "init.d/meshcore-ci-artifacts.init.gradle"
    init.parent.mkdir(parents=True, exist_ok=True)
    init.write_text(f"""// CI-only verified mirror of one unavailable Maven artifact.
def addMirror = {{ repositories ->
    repositories.with {{
        exclusiveContent {{
            forRepository {{ maven {{ url = uri('{repository.resolve().as_uri()}') }} }}
            filter {{ includeVersion('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '{version}') }}
        }}
    }}
}}
settingsEvaluated {{ settings ->
    addMirror(settings.dependencyResolutionManagement.repositories)
    if (settings.dependencyResolutionManagement.repositoriesMode.get().toString() == 'PREFER_PROJECT') {{
        gradle.afterProject {{ project ->
            if (!project.repositories.empty) addMirror(project.repositories)
        }}
    }}
}}
""")


def prepare_binaryen(temporary):
    version = "130"
    base = temporary / "binaryen-release"
    filename = f"binaryen-version_{version}-x86_64-linux.tar.gz"
    download("WebAssembly/binaryen", f"version_{version}", filename,
             base / f"version_{version}" / filename)
    with open(os.environ["GITHUB_ENV"], "a") as output:
        output.write("BINARYEN_DOWNLOAD_BASE_URL=" + base.resolve().as_uri() + "\n")


if __name__ == "__main__":
    temporary = Path(os.environ.get("RUNNER_TEMP", tempfile.gettempdir()))
    {"maven": prepare_maven, "binaryen": prepare_binaryen}[sys.argv[1]](temporary)
