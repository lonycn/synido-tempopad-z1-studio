"""Small offline fixtures: no downloads, signing material or hardware."""
import hashlib
import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("fetch_soundfont", Path(__file__).resolve().parents[1] / "android/fetch_soundfont.py")
fetch = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(fetch)


def metadata(data):
    return {"size": len(data), "sha256": hashlib.sha256(data).hexdigest()}


def deb(payload, name="data.tar.xz"):
    header = f"{name + '/':<16}{0:<12}{0:<6}{0:<6}{'100644':<8}{len(payload):<10}`\n".encode()
    assert len(header) == 60
    return b"!<arch>\n" + header + payload + (b"\n" if len(payload) % 2 else b"")


def fixture(content=b"test font", duplicate=False, symlink=False, member=fetch.MEMBER):
    output = io.BytesIO()
    with tarfile.open(fileobj=output, mode="w:xz") as archive:
        for _ in range(2 if duplicate else 1):
            info = tarfile.TarInfo(member)
            info.size = len(content)
            if symlink:
                info.type = tarfile.SYMTYPE
                info.linkname = "/etc/passwd"
                archive.addfile(info)
            else:
                archive.addfile(info, io.BytesIO(content))
        script = b"must never be executed"
        info = tarfile.TarInfo("postinst")
        info.size = len(script)
        archive.addfile(info, io.BytesIO(script))
    return deb(output.getvalue())


class FetchTest(unittest.TestCase):
    def run_extract(self, package_bytes, font=None, existing=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "source.deb"
            archive.write_bytes(package_bytes)
            target = root / "assets/FluidR3_GM.sf2"
            if existing is not None:
                target.parent.mkdir()
                target.write_bytes(existing)
            fetch.extract_font(archive, target, metadata(package_bytes), font or metadata(b"test font"))
            self.assertEqual(target.read_bytes(), b"test font")
            self.assertEqual(sorted(p.name for p in target.parent.iterdir()), [target.name])
            self.assertFalse((root / "postinst").exists())

    def test_extract_only_font(self):
        self.run_extract(fixture())

    def test_dot_prefix(self):
        self.run_extract(fixture(member="./" + fetch.MEMBER))

    def test_existing_valid_file_not_replaced(self):
        self.run_extract(fixture(), existing=b"test font")

    def test_invalid_inputs(self):
        for data in [b"invalid", fixture(duplicate=True), fixture(symlink=True), fixture(member="../escape"), deb(b"not tar")]:
            with self.subTest(data_size=len(data)), self.assertRaises((ValueError, tarfile.TarError)):
                self.run_extract(data)

    def test_wrong_font_hash(self):
        wrong = metadata(b"test font")
        wrong["sha256"] = "0" * 64
        with self.assertRaises(ValueError):
            self.run_extract(fixture(), font=wrong)

    def test_existing_invalid_file_never_replaced(self):
        with self.assertRaises(ValueError):
            self.run_extract(fixture(), existing=b"keep this file")

    def test_archive_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "bad.deb"
            archive.write_bytes(fixture())
            target = Path(directory) / "font.sf2"
            with self.assertRaises(ValueError):
                fetch.extract_font(archive, target, {"size": 1, "sha256": "0" * 64}, metadata(b"test font"))
            self.assertFalse(target.exists())


if __name__ == "__main__":
    unittest.main()
