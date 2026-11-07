#!/usr/bin/env python3
"""Offline pinned input and (optionally) final APK audit. No downloads or devices."""
from pathlib import Path
import hashlib, json, sys, zipfile
root=Path(__file__).resolve().parent
manifest=json.loads((root/'assets/licenses/instrument-sources.json').read_text())
for entry in manifest['files']:
    data=(root/entry['path']).read_bytes()
    assert len(data)==entry['size'], entry['path']
    assert hashlib.sha256(data).hexdigest()==entry['sha256'], entry['path']
if len(sys.argv)>1:
    with zipfile.ZipFile(sys.argv[1]) as apk:
        names=apk.namelist()
        assert len([p for p in names if p.startswith('res/raw/pad_') and p.endswith('.wav')])==64
        assert [p for p in names if p.startswith('lib/')]==['lib/arm64-v8a/libz1instrument.so']
        for path in (root/'assets').rglob('*'):
            if path.is_file(): assert apk.read('assets/'+str(path.relative_to(root/'assets')))==path.read_bytes(),str(path)
        for name in names:
            assert not any(s in name for s in ['.deb','SynthTest','controller-stubs','.java','.class','.keystore']),name
        dex=b''.join(apk.read(p) for p in names if p.endswith('.dex'))
        for forbidden in [b'ProtocolTest;',b'SynthTest;',b'LightingTest;',b'InstrumentsTest;',b'MidiLightingTest;']:
            assert forbidden not in dex, forbidden
print('PASS: pinned TSF/SF2/notices SHA256/size'+('; APK 64 WAV, exact bank/notices, arm64 only, no tests/archives/keys' if len(sys.argv)>1 else ''))
