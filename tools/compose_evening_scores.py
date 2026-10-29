#!/usr/bin/env python3
"""Compose the Velvet Fling level scores as original, gentle retro melodies.

Lanternwake's rounded triangle lead and quiet sine bass informed the palette;
these note sequences, harmonies, and arrangements are new. Requires NumPy and
ffmpeg. Each score is a 16-bar loop with two distinct melodic phrases.
"""
from pathlib import Path
import math,subprocess,tempfile,wave
import numpy as np

OUT=Path(__file__).resolve().parents[1]/'app/src/main/assets/audio'
RATE=32000
MINOR=[0,2,3,5,7,8,10]
MAJOR=[0,2,4,5,7,9,11]
# Two four-bar, quarter-note phrases per piece; None is a deliberate breath.
# Menu then nights 1–19. Public-source fallback; private family builds overlay
# licensed recordings. Intervals are diatonic scale degrees, not MIDI pitches.
SCORES=[
 ('menu',57,'minor',90,[0,5,2,4],
  [0,2,4,2, 5,4,2,0, 2,4,6,4, 4,1,0,None],
  [4,6,7,6, 5,2,0,2, 4,2,1,2, 1,4,2,0]),
 ('01',57,'minor',96,[0,5,2,4],
  [4,2,0,2, 5,2,0,None, 2,4,6,4, 1,4,2,0],
  [0,2,4,6, 5,2,7,5, 4,2,1,2, 4,1,0,None]),
 ('02',55,'major',100,[0,3,5,4],
  [2,4,2,0, 3,5,3,2, 5,7,5,4, 4,2,1,None],
  [4,5,4,2, 3,2,0,2, 5,4,2,0, 1,2,0,None]),
 ('03',50,'minor',94,[0,5,2,4],
  [0,4,2,0, 2,5,4,2, 6,4,2,None, 1,4,2,0],
  [2,4,6,4, 5,7,5,2, 4,6,4,2, 1,0,1,None]),
 ('04',57,'minor',102,[0,5,2,4],
  [4,6,7,4, 5,4,2,0, 2,1,2,4, 6,4,1,None],
  [0,2,4,2, 5,7,5,4, 2,4,2,1, 4,1,0,None]),
 ('05',48,'major',96,[0,3,5,4],
  [0,2,4,6, 5,3,2,0, 2,5,4,2, 1,4,2,None],
  [4,2,0,2, 3,5,7,5, 5,4,2,0, 1,2,0,None]),
 ('06',55,'minor',92,[0,5,2,4],
  [0,2,4,6, 5,2,0,2, 4,6,4,2, 1,2,0,None],
  [4,2,0,2, 5,4,5,7, 6,4,2,4, 1,4,2,0]),
 ('07',59,'minor',100,[0,5,2,4],
  [2,4,0,2, 5,2,5,4, 6,4,2,4, 1,2,0,None],
  [4,6,4,2, 5,7,5,2, 4,2,4,6, 4,1,0,None]),
 ('08',53,'major',98,[0,3,5,4],
  [4,2,0,2, 3,5,3,2, 5,4,2,5, 4,2,1,None],
  [0,2,4,2, 3,5,7,5, 5,2,4,2, 1,2,0,None]),
 ('09',50,'minor',104,[0,5,2,4],
  [0,2,4,6, 5,4,2,5, 6,4,2,1, 4,2,0,None],
  [4,6,7,4, 5,7,5,2, 4,2,1,2, 1,4,2,0]),
 ('10',52,'minor',95,[0,5,2,4],
  [0,2,4,2, 5,4,2,None, 2,4,6,4, 1,2,0,None],
  [4,5,7,5, 4,2,0,2, 5,4,2,0, 1,4,2,None]),
 ('11',50,'minor',98,[0,5,2,4],
  [2,4,5,4, 2,0,2,None, 4,6,5,2, 1,2,0,None],
  [0,4,6,4, 5,2,4,2, 6,4,2,0, 1,4,2,None]),
 ('12',48,'major',92,[0,3,5,4],
  [4,2,0,2, 3,5,4,2, 5,4,2,None, 1,2,0,None],
  [0,2,4,6, 5,3,2,0, 2,4,5,2, 1,4,2,None]),
 ('13',53,'minor',100,[0,5,2,4],
  [0,2,5,4, 2,4,6,4, 5,4,2,None, 1,4,2,0],
  [4,6,7,6, 5,2,4,5, 6,4,2,0, 1,2,0,None]),
 ('14',50,'minor',96,[0,5,2,4],
  [2,4,6,4, 5,2,0,2, 4,5,4,2, 1,2,0,None],
  [4,2,0,2, 5,7,5,4, 6,4,2,0, 1,4,2,None]),
 ('15',53,'minor',94,[0,5,2,4],
  [0,2,4,5, 4,2,0,2, 5,4,2,None, 1,2,0,None],
  [4,5,6,4, 5,2,4,2, 6,4,2,0, 1,4,2,None]),
 ('16',50,'major',98,[0,3,5,4],
  [2,4,2,0, 3,5,4,2, 5,4,2,None, 1,2,0,None],
  [4,5,7,5, 3,2,0,2, 5,4,2,0, 1,4,2,None]),
 ('17',52,'minor',96,[0,5,2,4],
  [0,4,2,0, 2,5,4,2, 6,4,2,4, 1,2,0,None],
  [4,6,5,2, 5,7,5,4, 4,2,0,2, 1,4,2,None]),
 ('18',48,'minor',100,[0,5,2,4],
  [2,4,5,2, 5,4,2,0, 2,4,6,4, 1,2,0,None],
  [0,2,4,6, 5,2,4,2, 6,4,2,0, 1,4,2,None]),
 ('19',50,'minor',98,[0,5,2,4],
  [4,2,0,2, 5,4,2,5, 6,4,2,None, 1,4,2,0],
  [0,2,4,6, 5,7,5,2, 4,5,4,2, 1,2,0,None]),
]

def midi(root,scale,degree):
    octave,index=divmod(degree,7)
    return root+12*octave+scale[index]

def add(out,note,at,duration,gain,tone='lead'):
    tail=.15 if tone=='lead' else .22
    count=round((duration+tail)*RATE); t=np.arange(count,dtype=np.float64)/RATE
    f=440*2**((note-69)/12); phase=2*np.pi*f*t
    if tone=='lead':
        # Rounded triangle-like tone, with the harsh top end deliberately low.
        sig=np.sin(phase)-.105*np.sin(3*phase)+.025*np.sin(5*phase)
    elif tone=='bass':sig=np.sin(phase)+.18*np.sin(2*phase)
    else:sig=np.sin(phase)
    attack=.035 if tone=='lead' else .085
    env=np.minimum(1,t/attack)
    env*=np.minimum(1,np.maximum(0,(duration+tail-t)/tail))
    if tone=='lead':env*=np.exp(-.22*t)
    data=sig*env*gain
    pos=round(at*RATE);idx=(np.arange(count)+pos)%len(out)
    np.add.at(out,idx,data)

def render(entry):
    name,tonic,mode,bpm,chords,a,b=entry
    assert len(a)==16 and len(b)==16
    scale=MINOR if mode=='minor' else MAJOR;beat=60/bpm;bar=4*beat
    length=round(16*bar*RATE);out=np.zeros(length,dtype=np.float64)
    # A → B → A with a passing note → B ending on the dominant.
    phrases=[a,b,a.copy(),b.copy()]
    if phrases[2][2] is not None:phrases[2][2]+=1
    phrases[3][-1]=4
    for measure in range(16):
        pos=measure*bar;root=chords[measure%4];phrase=phrases[measure//4]
        # A soft sustained triad, fading into the next harmony.
        for deg in (root,root+2,root+4):add(out,midi(tonic,scale,deg),pos,bar*.88,.020,'pad')
        # Warm bass, low enough to support the tune but with a phone-audible overtone.
        for beat_index in (0,2):add(out,midi(tonic-12,scale,root),pos+beat*beat_index,beat*1.5,.045,'bass')
        for j,deg in enumerate(phrase[(measure%4)*4:(measure%4+1)*4]):
            if deg is not None:add(out,midi(tonic+12,scale,deg),pos+j*beat,beat*.72,.095,'lead')
        # A light answer in alternating bars, never on every beat.
        if measure%2==1:add(out,midi(tonic,scale,root+4),pos+beat*2.5,beat*.6,.025,'lead')
    # Subtle short room reflections are circular so tails can cross the loop seam.
    dry=out.copy()
    for seconds,gain in [(.11,.10),(.23,.055),(.37,.03)]:out+=np.roll(dry,round(seconds*RATE))*gain
    peak=float(np.max(np.abs(out)));out*=.72/max(peak,1e-9)
    return out,bpm

def save(name,x):
    with tempfile.TemporaryDirectory(prefix='velvet-score-') as td:
        wav=Path(td)/'score.wav'
        with wave.open(str(wav),'wb') as f:
            f.setnchannels(1);f.setsampwidth(2);f.setframerate(RATE);f.writeframes((np.clip(x,-.95,.95)*32767).astype('<i2').tobytes())
        subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','3',str(OUT/f'evening-{name}.ogg')],check=True)

if __name__=='__main__':
    OUT.mkdir(parents=True,exist_ok=True)
    for item in SCORES:
        track,bpm=render(item);save(item[0],track)
        seam=abs(float(track[0]-track[-1]));rms=float(np.sqrt(np.mean(track*track)))
        print(f"evening-{item[0]}: {len(track)/RATE:.1f}s, {bpm} bpm, rms {rms:.3f}, seam {seam:.5f}")
