#!/usr/bin/env python3
"""Apply game assets to an APK before signing; keep stored data aligned."""
import argparse
import copy
import struct
from pathlib import Path
from zipfile import ZipFile, ZIP_STORED

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--apk", type=Path, required=True)
    p.add_argument("--overlay", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    a = p.parse_args()
    replacements = {p.relative_to(a.overlay).as_posix(): p for p in a.overlay.rglob("*") if p.is_file()}
    allowed = {"assets/asset-credits.js", *(f"assets/audio/evening-{i:02}.ogg" for i in range(1, 5)),
               "assets/audio/sling.ogg", "assets/audio/wood.ogg", "assets/audio/iron.ogg"}
    if set(replacements) != allowed:
        raise SystemExit("Unexpected overlay assets; refusing to produce an APK.")
    with ZipFile(a.apk) as src, ZipFile(a.output, "w", allowZip64=False) as dest:
        names = src.namelist()
        if len(names) != len(set(names)) or not allowed.issubset(names):
            raise SystemExit("Input APK has duplicate or missing entries.")
        for item in src.infolist():
            # Discard the debug signature; the caller signs the finished APK.
            if item.filename.startswith("META-INF/"):
                continue
            data = replacements[item.filename].read_bytes() if item.filename in replacements else src.read(item.filename)
            info = copy.copy(item)
            info.extra = b""
            if info.compress_type == ZIP_STORED:
                # A well-formed ZIP extra field pads the payload to 4 bytes.
                filename_size = len(info.filename.encode("utf-8"))
                padding = (-(dest.fp.tell() + 30 + filename_size + 4)) % 4
                info.extra = struct.pack("<HH", 0xFFFF, padding) + b"\0" * padding
            dest.writestr(info, data)
    print(f"Applied {len(replacements)} private game assets to {a.output}")

if __name__ == "__main__":
    main()
