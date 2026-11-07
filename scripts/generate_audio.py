"""Regenerate deterministic starter audio in an explicit output directory.

Requires numpy. Defaults to generated/audio, never overwrites the retained asset
unless --output-dir assets/audio is explicitly supplied.
"""
import argparse
import numpy as np,wave,pathlib
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output-dir', type=pathlib.Path,
                    default=pathlib.Path(__file__).resolve().parents[1]/'generated'/'audio')
root = parser.parse_args().output_dir.resolve()
root.mkdir(parents=True,exist_ok=True)
sr=44100;t=np.arange(sr)/sr;rng=np.random.default_rng(42)
def sine(f):return np.sin(2*np.pi*f*t)
def noise(decay):
 n=rng.standard_normal(sr);n=np.r_[0,np.diff(n)];return n*np.exp(-t/decay)
def drum(i):
 if i in [0,1]:
  f=48 if i==0 else 58; return np.sin(2*np.pi*(f*t+9*(1-np.exp(-t/.018))))*np.exp(-t/.16)+.1*noise(.006)
 if i in [2,3]:return .55*noise(.12 if i==2 else .06)+.45*sine(180)*np.exp(-t/.07)
 if i==4:return noise(.025)
 if i==5:return noise(.19)
 if i==6:
  env=sum(np.where(t>=x,np.exp(-(t-x)/.012),0) for x in [0,.018,.035]);return noise(.18)*env
 if i in [7,8,9]:return sine([0,0,0,0,0,0,0,150,115,85][i])*np.exp(-t/.18)+.08*noise(.01)
 if i in [10,11]:return (sine(540)+sine(800)+sine(1130))*.3*np.exp(-t/(.15 if i==10 else .4))+.15*noise(.22)
 if i==12:return (sine(800)+sine(540))*.4*np.exp(-t/.08)
 if i==13:return noise(.035)*(.5+.5*sine(1800))
 if i==14:return sine(740)*np.exp(-t/.045)+.15*noise(.009)
 return .7*sine(1600)*np.exp(-t/.012)+.2*noise(.008)
names=['Kick Deep','Kick Punch','Snare','Rim Snare','Closed Hat','Open Hat','Clap','Tom High','Tom Mid','Tom Low','Crash Short','Ride','Cowbell','Shaker','Clave','Click'];audio=[]
for i in range(64):
 if i<16:y=drum(i);name=names[i]
 else:
  j=i-16;note=48+12*(j//5)+[0,2,4,7,9][j%5];f=440*2**((note-69)/12);y=(sine(f)+.25*sine(f*2)+.12*sine(f*3))*np.exp(-t/.22);name=f'Pentatonic {note}'
 y*=np.minimum(t/.002,1)*np.minimum((1-t)/.02,1);y=y/max(1,np.max(np.abs(y)))*.6
 audio.append((y*32767).astype('<i2'));names.append(name) if i>=16 else None
with wave.open(str(root/'Z1_64pads_source.wav'),'wb') as w:w.setparams((1,2,sr,0,'NONE','not compressed'));w.writeframes(np.concatenate(audio).tobytes())
(root/'PAD_MAP.txt').write_text('Custom synthesized starter sounds. Not an official Synido preset.\nPAD 1-16: drums. PAD 17-64: C major pentatonic plucks.\n'+'\n'.join(f'{i+1:02}: {names[i]}' for i in range(64)))
print(root)
