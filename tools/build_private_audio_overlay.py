#!/usr/bin/env python3
"""Make the optional family-build audio overlay from privately held packs.

The source recordings and transformed audio are deliberately not part of the
public source tree. Run this locally with the two original Library archives;
include only the resulting overlay in the playable APK, not as an audio pack.
"""
import argparse
from array import array
import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path
from zipfile import ZipFile

MUSIC_SHA = "f18237c465a871afc13b07e54a244ad584e9fdfbd950477a3562c1c80a66804c"
SFX_SHA = "8a6959b9f88771cf0953e638706ab5c4cb6ec04baf59ea5569e3a7005e4d6dc8"
MUSIC = [
    ("evening-01.ogg", "LOOPS/FOREWORD/Foreword LOOP 1.ogg", -3.8, 42.667),
    ("evening-02.ogg", "LOOPS/SPACE CAT/Space Cat LOOP 2.ogg", -3.2, 16.271),
    ("evening-03.ogg", "LOOPS/SPACE CAT/Space Cat LOOP 4.ogg", -5.3, 40.678),
    ("evening-04.ogg", "LOOPS/SPACE CAT/Space Cat LOOP 3.ogg", -5.6, 32.542),
    ("evening-10.ogg", "LOOPS/FOREWORD/Foreword LOOP 2.ogg", -3.8, 42.667),
    ("evening-11.ogg", "LOOPS/SPACE CAT/Space Cat LOOP 1.ogg", -5.2, 16.271),
    ("evening-12.ogg", "LOOPS/FOREWORD/Foreword LOOP 3.ogg", -3.8, 42.667),
    ("evening-13.ogg", "LOOPS/LIGHTS ON THE BRIDGE/Lights on the Bridge LOOP 1.ogg", -6.0, 48.0),
    ("evening-14.ogg", "LOOPS/THE FOX/The Fox LOOP 3.ogg", -4.5, 32.0),
    ("evening-15.ogg", "LOOPS/LIGHTS ON THE BRIDGE/Lights on the Bridge LOOP 2.ogg", -6.2, 60.0),
    ("evening-16.ogg", "LOOPS/THE FOX/The Fox LOOP 1.ogg", -3.7, 32.0),
    ("evening-17.ogg", "LOOPS/THE FOX/The Fox LOOP 2.ogg", -4.7, 32.0),
    ("evening-18.ogg", "LOOPS/THE FOX/The Fox LOOP 4.ogg", -4.6, 32.0),
    ("evening-19.ogg", "LOOPS/THE FOX/The Fox LOOP 5.ogg", -3.3, 80.0),
    ("evening-20.ogg", "LOOPS/THE FOX/The Fox LOOP 6.ogg", -2.5, 128.0),
    ("evening-21.ogg", "LOOPS/OVERTIME/Overtime LOOP 1.ogg", -2.5, 56.471),
    ("evening-22.ogg", "LOOPS/CONSPIRACIES/Conspiracies LOOP 2.ogg", -3.7, 26.378),
    ("evening-23.ogg", "LOOPS/SPINNER/Spinner LOOP 4.ogg", -3.7, 22.588),
    ("evening-24.ogg", "LOOPS/ANEXODUS/Anexodus LOOP 3.ogg", -6.3, 15.118),
]
# An existing game effect is mixed with a short, quiet, precisely timed excerpt.
# start, end, fade start, fade duration, old gain, supplied gain
SFX = [
    ("sling.ogg", "Whoosh 1-1.wav", .035, .35, .235, .08, .80, .38),
    ("wood.ogg", "Wood 14-8.wav", 0, .30, .22, .08, .72, .45),
    ("iron.ogg", "Shield Metal Impact 1-3.wav", 0, .32, .24, .08, .72, .40),
]

def digest(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--music-zip", type=Path, required=True)
    parser.add_argument("--effects-zip", type=Path, required=True)
    parser.add_argument("--base-audio", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--only-music", nargs="+", help="Render named music files only, for incremental family-build updates")
    args = parser.parse_args()
    if digest(args.music_zip) != MUSIC_SHA or digest(args.effects_zip) != SFX_SHA:
        raise SystemExit("An input archive does not match the documented original.")
    audio = args.output / "assets" / "audio"
    audio.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="velvet-audio-") as temp:
        tmp = Path(temp)
        with ZipFile(args.music_zip) as music:
            for dest, member, gain, expected_duration in MUSIC:
                if args.only_music and dest not in args.only_music:
                    continue
                source = tmp / "track.ogg"
                source.write_bytes(music.read(member))
                # Decode to samples rather than trusting the supplied Ogg's
                # timestamps, which contain small discontinuities.
                pcm = subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-i", str(source),
                    "-af", f"asetpts=N/SR/TB,volume={gain}dB,treble=g=-2.5:f=3500:w=0.7",
                    "-ar", "48000", "-ac", "2", "-f", "f32le", "-"], check=True,
                    stdout=subprocess.PIPE).stdout
                samples = array("f")
                samples.frombytes(pcm)
                if sys.byteorder != "little":
                    samples.byteswap()
                frames = len(samples) // 2
                if abs(frames / 48000 - expected_duration) > .1:
                    raise SystemExit(f"The music source {member} did not decode completely.")
                # End with a tail-to-head blend, then restart at that same
                # point in the head. This avoids clicks on looping WebViews.
                fade_frames = int(.12 * 48000)
                loop = samples[fade_frames * 2:(frames - fade_frames) * 2]
                for n in range(fade_frames):
                    u = (n + 1) / fade_frames
                    for ch in range(2):
                        loop.append((1-u)*samples[(frames-fade_frames+n)*2+ch] + u*samples[n*2+ch])
                if sys.byteorder != "little":
                    loop.byteswap()
                # Match the earlier score's approximate integrated loudness and
                # gently reduce very high frequencies; keep stereo and tempo.
                subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-y", "-f", "f32le",
                    "-ar", "48000", "-ac", "2", "-i", "pipe:0", "-map_metadata", "-1",
                    "-c:a", "libvorbis", "-q:a", "4", str(audio / dest)],
                    input=loop.tobytes(), check=True)
                actual_duration = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries",
                    "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", str(audio / dest)],
                    check=True, stdout=subprocess.PIPE, text=True).stdout.strip())
                if abs(actual_duration - (expected_duration - .12)) > .15:
                    raise SystemExit(f"The rendered track {dest} is unexpectedly short.")
        with ZipFile(args.effects_zip) as effects:
            for dest, member, start, end, fade_at, fade_len, base_gain, pack_gain in SFX:
                if args.only_music:
                    continue
                source = tmp / "effect.wav"
                source.write_bytes(effects.read("Free Sounds Pack/" + member))
                graph = (f"[0:a]aformat=channel_layouts=mono,volume={base_gain}[base];"
                         f"[1:a]atrim=start={start}:end={end},asetpts=PTS-STARTPTS,"
                         f"aformat=channel_layouts=mono,afade=t=out:st={fade_at}:d={fade_len},"
                         f"volume={pack_gain}[pack];[base][pack]amix=inputs=2:"
                         "duration=longest:normalize=0,alimiter=limit=0.95:level=0:latency=1[out]")
                run("ffmpeg", "-nostdin", "-v", "error", "-y", "-i", str(args.base_audio / dest),
                    "-i", str(source), "-filter_complex", graph, "-map", "[out]",
                    "-map_metadata", "-1", "-ar", "44100", "-ac", "1", "-c:a", "libvorbis",
                    "-q:a", "4", str(audio / dest))
    (args.output / "assets" / "asset-credits.js").write_text(
        'window.VELVET_ASSET_CREDIT_LINE = "Music: Tomality  •  Action sounds: Place Holder Assets";\n',
        encoding="utf-8")
    print(json.dumps({"music": [x[0] for x in MUSIC], "effects": [x[0] for x in SFX],
                      "sha256": {p.name: digest(p) for p in sorted(audio.iterdir())}}, indent=2))

if __name__ == "__main__":
    main()
