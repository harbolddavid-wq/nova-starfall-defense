#!/usr/bin/env python3
"""Create short character performances from CC0 vocal recordings.

The actor sources and their licenses are documented in AUDIO_CREDITS.md.
This file deliberately leaves the existing gargoyle cues untouched.
"""
from pathlib import Path
import subprocess
import tempfile
import wave
import numpy as np
from scipy.signal import butter, sosfilt

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'tools/samples/sfx'
OUT = ROOT / 'app/src/main/assets/audio'
RATE = 32000
EVENTS = [
    ('rival-hit-a.ogg', '509883__slamaxu__female_gasp.wav', .02, .70, .72),
    ('rival-hit-b.ogg', '727694__owstu__female-vocal-2-higher-notes.wav', .02, .76, .68),
    ('rival-idle-a.ogg', '727696__owstu__female-vocal-melody-humming.wav', .18, .96, .66),
    ('rival-idle-b.ogg', '727696__owstu__female-vocal-melody-humming.wav', 1.35, 2.18, .66),
]

for target, source, start, end, peak in EVENTS:
    raw = subprocess.check_output(['ffmpeg', '-nostdin', '-v', 'error', '-i', str(SRC/source),
        '-ar', str(RATE), '-ac', '1', '-f', 'f32le', 'pipe:1'])
    sound = np.frombuffer(raw, dtype='<f4').astype(np.float64)[int(start*RATE):int(end*RATE)]
    sound -= np.mean(sound)
    sound = sosfilt(butter(2, 110, fs=RATE, btype='highpass', output='sos'), sound)
    fade_in, fade_out = int(.035*RATE), int(.10*RATE)
    sound[:fade_in] *= np.sin(np.linspace(0, np.pi/2, fade_in))
    sound[-fade_out:] *= np.cos(np.linspace(0, np.pi/2, fade_out))
    sound *= peak / max(np.max(np.abs(sound)), 1e-9)
    with tempfile.TemporaryDirectory(prefix='velvet-rival-') as td:
        wav = Path(td)/'voice.wav'
        with wave.open(str(wav), 'wb') as f:
            f.setnchannels(1); f.setsampwidth(2); f.setframerate(RATE)
            f.writeframes((np.clip(sound,-.95,.95)*32767).astype('<i2').tobytes())
        subprocess.run(['ffmpeg','-nostdin','-v','error','-y','-i',str(wav),
            '-map_metadata','-1','-c:a','libvorbis','-q:a','4',str(OUT/target)],check=True)
    print(f'{target}: {len(sound)/RATE:.2f}s; peak {np.max(np.abs(sound)):.2f}')
