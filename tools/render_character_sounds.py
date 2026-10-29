#!/usr/bin/env python3
"""Render concise original game cues from CC0 recordings; see AUDIO_CREDITS.md.

Requires ffmpeg, NumPy and SciPy. The input music/sound files are never changed.
"""
from pathlib import Path
import subprocess, tempfile, wave
import numpy as np
from scipy.signal import butter, sosfilt

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'tools'/'samples'/'sfx'
OUT=ROOT/'app'/'src'/'main'/'assets'/'audio'
RATE=32000

def decode(name, highpass=90):
    data=subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-i',str(SRC/name),'-f','f32le','-ar',str(RATE),'-ac','1','pipe:1'],check=True,stdout=subprocess.PIPE).stdout
    x=np.frombuffer(data,dtype='<f4').copy()
    if not len(x):raise ValueError('empty source '+name)
    x-=np.mean(x)
    if highpass:x=sosfilt(butter(2,highpass,fs=RATE,btype='highpass',output='sos'),x)
    threshold=max(.0015,float(np.max(np.abs(x)))*.012)
    active=np.flatnonzero(np.abs(x)>threshold)
    if len(active):x=x[max(0,active[0]-int(.015*RATE)):min(len(x),active[-1]+int(.04*RATE))]
    return x.astype(np.float64)

def shape(x,duration=None,peak=.78):
    if duration is not None:x=x[:round(duration*RATE)]
    x=np.array(x,dtype=np.float64,copy=True)
    if len(x)<32:raise ValueError('sound too short')
    # Short equal-power fades prevent clicks without dulling a hit transient.
    a=min(round(.003*RATE),len(x)//3);b=min(round(.045*RATE),len(x)//3)
    x[:a]*=np.sin(np.linspace(0,np.pi/2,a));x[-b:]*=np.cos(np.linspace(0,np.pi/2,b))
    x*=peak/max(float(np.max(np.abs(x))),1e-9)
    return np.clip(x,-.95,.95)

def mix(length,*items):
    result=np.zeros(round(length*RATE),dtype=np.float64)
    for data,gain,start in items:
        pos=round(start*RATE);n=min(len(data),len(result)-pos)
        if n>0:result[pos:pos+n]+=data[:n]*gain
    return result

def save(name,x):
    OUT.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='velvet-voice-') as td:
        wav=Path(td)/'cue.wav'
        with wave.open(str(wav),'wb') as f:
            f.setnchannels(1);f.setsampwidth(2);f.setframerate(RATE);f.writeframes((x*32767).astype('<i2').tobytes())
        subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','4',str(OUT/name)],check=True)
    rms=float(np.sqrt(np.mean(x*x)))
    print(f'{name}: {len(x)/RATE:.2f}s, peak {np.max(np.abs(x)):.2f}, RMS {rms:.3f}')

# Two playful reactions and two occasional idle performances by the same
# actor. Their names refer to gameplay events, not any particular character.
voices={
 'voice-hit-a.ogg':'323707__reitanna__ooh.wav',
 'voice-hit-b.ogg':'343878__reitanna__big-gasp.wav',
 'voice-idle-a.ogg':'343991__reitanna__giggle8.wav',
 'voice-idle-b.ogg':'343944__reitanna__laugh87.wav',
}
for name,source in voices.items():save(name,shape(decode(source,120),peak=.79))

# Layer familiar musical signatures underneath concise, real transients.
whoosh=shape(decode('420668__sypherzent__basic-melee-swing-miss-whoosh.wav',110),peak=.65)
pluck=shape(decode('sling-original.ogg',100),duration=.32,peak=.6)
save('sling.ogg',shape(mix(.42,(whoosh,.90,0),(pluck,.32,0)),peak=.78))
knock=shape(decode('421929__micahlg__chopping-wood-02.wav',110),peak=.76)
bass=shape(decode('wood-original.ogg',90),duration=.26,peak=.5)
save('wood.ogg',shape(mix(.32,(knock,.88,0),(bass,.25,0)),peak=.80))
glass=shape(decode('371091__mccormick_iain__glass-break-4.wav',180),duration=.58,peak=.70)
harp=shape(decode('glass-original.ogg',130),duration=.46,peak=.48)
save('glass.ogg',shape(mix(.65,(glass,.80,0),(harp,.22,.02)),peak=.76))
# A brief damped metallic ring, not an explosion, distinguishes fixed iron.
t=np.arange(round(.30*RATE))/RATE
ring=(np.sin(2*np.pi*1270*t)+.43*np.sin(2*np.pi*2110*t))*np.exp(-t*19)
save('iron.ogg',shape(mix(.30,(knock,.32,0),(ring,.34,0)),peak=.72))
