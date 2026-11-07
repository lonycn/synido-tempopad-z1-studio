#!/usr/bin/env python3
"""Validate retained assets without opening hosts or touching the USB device."""
import argparse
import json
import hashlib
import pathlib
import plistlib
import wave
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-local-archives", action="store_true",
                        help="Require private EASY/GarageBand archive fixtures as well")
    args = parser.parse_args()
    report = {"archives": {}, "local_projects": {}}
    archives = {
        "projects/easy/Z1_EASY_Lights.eas": (64, 64),
        "backups/easy/Z1_Starter_64Pads.eas": (64, 0),
        "backups/easy/Z1_Starter_64Pads_before_lights.eas": (64, 0),
    }
    for relative, expected in archives.items():
        path = ROOT / relative
        if not path.exists() and not args.require_local_archives:
            report["archives"][relative] = {"status": "not included in source checkout"}
            continue
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            assert len(names) == len(set(names)), f"duplicate entries: {relative}"
            assert all(not pathlib.PurePosixPath(n).is_absolute()
                       and ".." not in pathlib.PurePosixPath(n).parts for n in names)
            actual = (sum(n.endswith(".wav") for n in names),
                      sum(n.endswith("/Light.json") for n in names))
            assert actual == expected, (relative, actual, expected)
            assert "content.json" in names, relative
            report["archives"][relative] = {"audio": actual[0], "lights": actual[1]}
    with wave.open(str(ROOT / "assets/audio/Z1_64pads_source.wav")) as source:
        assert (source.getnchannels(), source.getsampwidth(), source.getframerate()) == (1, 2, 44100)
        assert source.getnframes() == 64 * 44100
        report["source_audio"] = {"seconds": 64, "sample_rate": 44100,
                                  "channels": 1, "pcm_bits": 16}
    pad_lines = (ROOT / "docs/PAD_MAP.txt").read_text().splitlines()
    assert sum(line[:2].isdigit() and line[2:3] == ":" for line in pad_lines) == 64
    for name in ["Z1_DRUM_Starter.band", "Z1_KEY_Piano.band"]:
        project = ROOT / "projects/garageband" / name
        if not project.exists() and not args.require_local_archives:
            report["local_projects"][name] = {"status": "not included in source checkout"}
            continue
        assert (project / "projectData").is_file(), name
        assert (project / "Alternatives/000/ProjectData").is_file(), name
        report["local_projects"][name] = {"status": "validated"}
    with (ROOT / "app/Info.plist").open("rb") as stream:
        plist = plistlib.load(stream)
    assert plist["CFBundleIdentifier"] == "local.orange.z1player"
    assert plist["CFBundleShortVersionString"] == "2.1"
    assert plist["CFBundleIconFile"] == "AppIcon"
    app = ROOT / "dist/Z1 演奏助手.app/Contents"
    with (app / "Info.plist").open("rb") as stream:
        installed = plistlib.load(stream)
    for key in ["CFBundleShortVersionString", "CFBundleVersion", "CFBundleIconFile"]:
        assert installed[key] == plist[key], (key, installed.get(key), plist.get(key))
    icon = app / "Resources/AppIcon.icns"
    assert icon.read_bytes()[:4] == b"icns" and icon.stat().st_size > 10000
    resource = app / "Resources/Z1_64pads_source.wav"
    digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    assert digest(resource) == digest(ROOT / "assets/audio/Z1_64pads_source.wav")
    assert digest(icon) == digest(ROOT / "app/Resources/AppIcon.icns")
    source_files = ["Z1Player.swift", "Audio.swift", "MIDI.swift", "Models.swift", "Surface.swift", "Effects.swift", "Lighting.swift"]
    for filename in source_files:
        code = (ROOT / "src" / filename).read_text()
        assert "/Music/Synido Z1 Starter" not in code
        if filename != "MIDI.swift":
            assert "MIDISend(" not in code and "MIDIOutputPortCreate(" not in code
    midi_code = (ROOT / "src/MIDI.swift").read_text()
    assert midi_code.count("MIDISend(") == 1 and "let bytes=LightingWire.frame(colors)" in midi_code
    report["app"] = {"version": plist["CFBundleShortVersionString"], "build": plist["CFBundleVersion"],
                     "icon_sha256": digest(icon), "bundled_audio_sha256": digest(resource),
                     "source_modules": source_files, "outbound_midi": "audited EASY RGB only"}
    report["note"] = "EAS files are encrypted; checks cover entry names/counts, not decrypted audio or light payloads. Hardware playback requires a manual check."
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
