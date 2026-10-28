#!/usr/bin/env python3
"""Render the original After Hours work-in-progress waltz and event cues.

Uses CC0 VCSL and VSCO 2 CE recordings; source hashes/provenance in AUDIO_CREDITS.md.
Requires Python 3, numpy, scipy, and ffmpeg. Output is stereo Vorbis, 32 kHz.
"""
from pathlib import Path
from functools import lru_cache
from fractions import Fraction
import math, subprocess, tempfile, wave
import numpy as np
from scipy.io import wavfile
from scipy.signal import resample_poly
ROOT=Path(__file__).resolve().parents[1]
SAMPLES=ROOT/'tools'/'samples'
OUT=ROOT/'app'/'src'/'main'/'assets'/'audio'
RATE=32000
BANKS={
 'harp':[(70,'harp_bb3.wav'),(86,'harp_d5.wav')],
 'strum':[(54,'strum_fs2.wav'),(76,'strum_e4.wav')],
 'cello':[(47,'susvib_B1_v1_1.wav'),(50,'susvib_D2_v1_1.wav')],
 'violin':[(62,'VlnEns_susVib_D3_v1.wav'),(69,'VlnEns_susVib_A3_v1.wav')],
}
@lru_cache(maxsize=None)
def sample(instrument,note):
 root,name=min(BANKS[instrument],key=lambda x:abs(x[0]-note))
 sr,data=wavfile.read(SAMPLES/name)
 if np.issubdtype(data.dtype,np.integer): data=data.astype(np.float64)/max(abs(np.iinfo(data.dtype).min),np.iinfo(data.dtype).max)
 else: data=data.astype(np.float64)
 if data.ndim==2:data=data.mean(axis=1)
 data-=data.mean()
 ratio=Fraction((RATE/sr)/(2**((note-root)/12))).limit_denominator(300)
 data=resample_poly(data,ratio.numerator,ratio.denominator).astype(np.float32)
 return data/max(float(np.max(np.abs(data))),1e-8)

def put(out,instrument,note,start,duration,gain,pan=0,bowed=False,wrap=True):
 data=sample(instrument,note)
 length=min(len(data),round((duration+(.35 if bowed else .75))*RATE))
 if length<2:return
 data=data[:length].copy()
 attack=min(length//2,round((.08 if bowed else .005)*RATE))
 release=min(length//2,round((.32 if bowed else .22)*RATE))
 if attack:data[:attack]*=np.linspace(0,1,attack,dtype=np.float32)
 if release:data[-release:]*=np.linspace(1,0,release,dtype=np.float32)
 data*=gain
 left,right=math.sqrt((1-pan)/2),math.sqrt((1+pan)/2)
 pos=round(start*RATE); N=len(out)
 if wrap:pos%=N
 if pos>=N:return
 first=min(length,N-pos)
 out[pos:pos+first,0]+=data[:first]*left;out[pos:pos+first,1]+=data[:first]*right
 if wrap and first<length:
  rest=min(length-first,N);out[:rest,0]+=data[first:first+rest]*left;out[:rest,1]+=data[first:first+rest]*right

def room(mix,wrap=True):
 dry=mix.copy()
 for delay,gain in ((.085,.08),(.157,.06),(.277,.045),(.41,.025)):
  shift=round(delay*RATE)
  if wrap:
   mix[:,0]+=gain*np.roll(dry[:,1],shift);mix[:,1]+=gain*np.roll(dry[:,0],shift+71)
  else:
   mix[shift:,0]+=gain*dry[:-shift,1];mix[shift:,1]+=gain*dry[:-shift,0]

def save(name,data,peak=.72):
 m=float(np.max(np.abs(data)));data=data*(peak/max(m,1e-8));data=np.clip(data,-.98,.98)
 with tempfile.TemporaryDirectory(prefix='velvet-audio-') as td:
  wav=Path(td)/'mix.wav'
  with wave.open(str(wav),'wb') as f:f.setnchannels(2);f.setsampwidth(2);f.setframerate(RATE);f.writeframes((data*32767).astype('<i2').tobytes())
  subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','3',str(OUT/name)],check=True)
 return m

def main():
 OUT.mkdir(parents=True,exist_ok=True)
 bpm=108;beat=60/bpm;bar=beat*3
 chords=[(50,53,57),(43,46,50),(46,50,53),(45,49,52),
         (50,53,57),(48,52,55),(46,50,53),(45,49,52),
         (46,50,53),(48,52,55),(50,53,57),(45,49,52),
         (43,46,50),(46,50,53),(45,49,52),(50,53,57),
         (50,53,57),(43,46,50),(46,50,53),(45,49,52),
         (43,46,50),(46,50,53),(45,49,52),(50,53,57)]
 # A new lightly syncopated tune, arranged in three eight-bar phrases.
 motifs=[[(.0,74,.5),(.75,77,.5),(1.5,81,.5),(2.2,77,.6)],
         [(.0,74,.7),(1.0,70,.5),(1.75,67,.5),(2.5,70,.4)],
         [(.25,77,.5),(1,74,.5),(1.75,70,.5),(2.5,74,.4)],
         [(.0,73,.5),(.75,76,.5),(1.5,79,.5),(2.25,76,.5)],
         [(.0,81,.5),(.75,77,.5),(1.5,74,.7),(2.5,77,.4)],
         [(.25,79,.5),(1,76,.5),(1.75,72,.5),(2.5,76,.4)],
         [(.0,77,.5),(.75,74,.5),(1.5,70,.7),(2.5,74,.4)],
         [(.0,73,.5),(.75,76,.5),(1.5,79,.5),(2.25,81,.5)]]
 N=round(len(chords)*bar*RATE);mix=np.zeros((N,2),dtype=np.float32)
 for i,ch in enumerate(chords):
  at=i*bar
  # A low pulse and alternating chord tones establish a small cabaret waltz.
  put(mix,'cello',ch[0],at,.75*beat,.065,pan=-.23,bowed=True)
  for tick,idx in ((.95,1),(1.95,2)):
   put(mix,'strum',ch[idx]+12,at+tick*beat,.40*beat,.075,pan=.25 if idx==1 else -.05)
  for t,n,d in motifs[i%8]:
   # Middle phrase is lifted by an answering register, not repeated verbatim.
   n=n+(12 if 8<=i<16 and t>=2 else 0)
   put(mix,'harp',n,at+t*beat,d*beat,.11,pan=.13*math.sin(i+t))
  if i>=8:
   put(mix,'violin',ch[2]+12,at+.25*beat,1.35*beat,.045,pan=-.3,bowed=True)
  if i>=16:
   put(mix,'strum',ch[1]+24,at+1.5*beat,.35*beat,.035,pan=.32)
 room(mix);save('after-hours-waltz.ogg',mix)
 # Short event cues use the same palette; their timings stay independent of music.
 specs={
  'sling.ogg': [('strum',76,.0,.12,.9,0)],
  'wood.ogg': [('cello',47,.0,.08,.7,-.1)],
  'glass.ogg': [('harp',86,.0,.16,.8,.1),('harp',93,.05,.16,.3,-.2)],
  'charm.ogg': [('harp',77,.0,.16,.65,-.1),('harp',81,.07,.2,.55,.15)],
  'power.ogg': [('strum',76,.0,.15,.55,-.2),('harp',86,.04,.2,.7,.2)],
  'win.ogg': [('harp',74,.0,.20,.6,-.1),('harp',77,.13,.20,.6,.1),('harp',81,.26,.32,.7,0),('violin',74,.18,.48,.35,-.2)],
 }
 for name,notes in specs.items():
  cue=np.zeros((round(1.5*RATE),2),dtype=np.float32)
  for ins,note,t,d,g,pan in notes:put(cue,ins,note,t,d,g,pan,bowed=ins in ('cello','violin'),wrap=False)
  room(cue,wrap=False);save(name,cue,.62)
 print(f'Original {len(chords)}-bar, {N/RATE:.2f}-second waltz and {len(specs)} event cues rendered.')
if __name__=='__main__':main()
