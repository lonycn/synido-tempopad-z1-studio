#!/usr/bin/env python3
"""Split the original mono WAV losslessly into 64 one-second test samples."""
from pathlib import Path
import wave, hashlib, json, shutil
ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parent
out = ROOT / 'build/generated/res'
(out/'raw').mkdir(parents=True, exist_ok=True)
(out/'drawable-nodpi').mkdir(parents=True, exist_ok=True)
source = PROJECT/'assets/audio/Z1_64pads_source.wav'
report = []
with wave.open(str(source),'rb') as src:
    assert (src.getnchannels(), src.getsampwidth(), src.getframerate()) == (1,2,44100)
    assert src.getnframes() == 64*44100
    for i in range(64):
        data = src.readframes(44100)
        path = out/'raw'/f'pad_{i+1:02d}.wav'
        with wave.open(str(path),'wb') as dst:
            dst.setnchannels(1); dst.setsampwidth(2); dst.setframerate(44100); dst.writeframes(data)
        with wave.open(str(path),'rb') as check:
            assert check.readframes(44100) == data
        report.append({'pad':i+1,'pcm_sha256':hashlib.sha256(data).hexdigest(),'rms_nonzero':any(data)})
shutil.copyfile(PROJECT/'assets/branding/Z1Studio-1024.png', out/'drawable-nodpi/ic_z1.png')
(ROOT/'build/sample-manifest.json').write_text(json.dumps({'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'samples':report},indent=2)+'\n')
print('PASS: 64 lossless one-second WAV segments from original source')
