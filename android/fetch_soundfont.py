#!/usr/bin/env python3
"""Explicit build-time font preparation. Never installs/executes a Debian package."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parent
MEMBER = "usr/share/sounds/sf2/FluidR3_GM.sf2"


def verify(path, expected):
    if path.stat().st_size != expected["size"]:
        raise ValueError(f"Size mismatch: {path}")
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    if digest.hexdigest() != expected["sha256"]:
        raise ValueError(f"SHA-256 mismatch: {path}")


def extract_font(archive, target, package, font):
    """Read one regular member only; no extractall, links, scripts or package installation."""
    verify(archive, package)
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="z1-font-", dir=target.parent) as directory:
        work = Path(directory)
        data = work / "data.tar"
        with archive.open("rb") as source:
            if source.read(8) != b"!<arch>\n":
                raise ValueError("Not a Debian ar archive")
            found = False
            while True:
                header = source.read(60)
                if not header:
                    break
                if len(header) != 60 or header[58:] != b"`\n":
                    raise ValueError("Invalid ar member header")
                name = header[:16].decode("ascii").strip().rstrip("/")
                size = int(header[48:58].decode("ascii").strip())
                start = source.tell()
                if size < 0 or start + size > archive.stat().st_size:
                    raise ValueError("Invalid ar member size")
                if name in ("data.tar.xz", "data.tar.gz", "data.tar.bz2", "data.tar"):
                    if found:
                        raise ValueError("Duplicate data archive")
                    with data.open("wb") as output:
                        remaining = size
                        while remaining:
                            block = source.read(min(1024 * 1024, remaining))
                            if not block:
                                raise ValueError("Truncated data archive")
                            output.write(block)
                            remaining -= len(block)
                    found = True
                source.seek(start + size + size % 2)
        if not found:
            raise ValueError("Supported data archive not found")
        with tarfile.open(data, "r:*") as content:
            matches = [m for m in content.getmembers() if m.name in (MEMBER, "./" + MEMBER)]
            if len(matches) != 1 or not matches[0].isfile() or matches[0].size != font["size"]:
                raise ValueError("Expected one regular, correctly sized SF2 member")
            staged = work / "FluidR3_GM.sf2"
            with content.extractfile(matches[0]) as source, staged.open("wb") as output:
                shutil.copyfileobj(source, output, 1024 * 1024)
        verify(staged, font)
        # Existing assets must not be overwritten, even if an unrelated writer raced us.
        try:
            os.link(staged, target)  # Same filesystem: atomic publication, no partial asset.
        except FileExistsError:
            verify(target, font)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, help="Use an already downloaded .deb, offline")
    args = parser.parse_args()
    manifest = json.loads((ROOT / "assets/licenses/instrument-sources.json").read_text())
    package = manifest["verifiedDebianArchive"]
    font = next(f for f in manifest["files"] if f["path"] == "assets/FluidR3_GM.sf2")
    target = ROOT / font["path"]
    if target.exists():
        verify(target, font)
        print("PASS: existing FluidR3 GM size/SHA-256; no download needed")
        return
    if args.archive:
        extract_font(args.archive.resolve(), target, package, font)
    else:
        print("Downloading pinned FluidR3 package (~120 MB); font data MIT, notices retained.")
        with tempfile.TemporaryDirectory(prefix="z1-download-") as directory:
            archive = Path(directory) / "fluid-soundfont-gm.deb"
            request = urllib.request.Request(package["url"], headers={"User-Agent": "Z1Studio-source-setup"})
            with urllib.request.urlopen(request, timeout=60) as response, archive.open("wb") as output:
                shutil.copyfileobj(response, output, 1024 * 1024)
            extract_font(archive, target, package, font)
    print("PASS: archive and SF2 size/SHA-256; only font extracted, no package scripts executed")


if __name__ == "__main__":
    main()
