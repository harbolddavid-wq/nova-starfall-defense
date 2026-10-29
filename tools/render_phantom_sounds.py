#!/usr/bin/env python3
"""Render compact CC0 vocal reactions for the Masquerade character.

The sources are credited in AUDIO_CREDITS.md; earlier voice cues are untouched.
"""
from pathlib import Path
import subprocess, tempfile, wave
import numpy as np
from scipy.signal import butter, sosfilt

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'tools/samples/sfx'
OUT=ROOT/'app/src/main/assets/audio'
RATE=32000
EVENTS=[
 ('phantom-hit-a.ogg','218908__martian__female-grunts-breaths_a.wav',0.0,.33,.70,1.0),
 ('phantom-hit-b.ogg','218908__martian__female-grunts-breaths_b.wav',0.0,.27,.70,1.0),
 ('phantom-idle-a.ogg','384992__threadzz__woman-sigh.wav',0.03,.86,.64,1.0),
 ('phantom-idle-b.ogg','384992__threadzz__woman-sigh.wav',0.08,.82,.64,.94),
]
for dest,source,start,end,peak,rate in EVENTS:
 raw=subprocess.check_output(['ffmpeg','-nostdin','-v','error','-i',str(SRC/source),'-ar',str(RATE),'-ac','1','-f','f32le','pipe:1'])
 x=np.frombuffer(raw,dtype='<f4').astype(np.float64)[int(start*RATE):int(end*RATE)]
 x-=np.mean(x);x=sosfilt(butter(2,110,fs=RATE,btype='highpass',output='sos'),x)
 if rate!=1:
  newlen=round(len(x)/rate);x=np.interp(np.arange(newlen)*rate,np.arange(len(x)),x)
 attack=min(round(.02*RATE),len(x)//4);release=min(round(.07*RATE),len(x)//4)
 x[:attack]*=np.sin(np.linspace(0,np.pi/2,attack));x[-release:]*=np.cos(np.linspace(0,np.pi/2,release))
 x*=peak/max(np.max(np.abs(x)),1e-9)
 with tempfile.TemporaryDirectory(prefix='velvet-phantom-') as td:
  wav=Path(td)/'voice.wav'
  with wave.open(str(wav),'wb') as f:
   f.setnchannels(1);f.setsampwidth(2);f.setframerate(RATE);f.writeframes((np.clip(x,-.95,.95)*32767).astype('<i2').tobytes())
  subprocess.run(['ffmpeg','-nostdin','-v','error','-y','-i',str(wav),'-map_metadata','-1','-c:a','libvorbis','-q:a','4',str(OUT/dest)],check=True)
 print(f'{dest}: {len(x)/RATE:.2f}s; peak {np.max(np.abs(x)):.2f}')
