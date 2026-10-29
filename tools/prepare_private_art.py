#!/usr/bin/env python3
"""Extract only the chosen family-app art from David's original private packs."""
import argparse, hashlib
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile
from PIL import Image

EXPECTED={"neptune":"6390a2de7258b0729b2c189b2c07f1b9aa3e96b4468e42109caba6652d0ea999",
          "paper":"f3025307567879b2ef19b5e53c0453aaa066b8432e18e13d0173b30f4f5d9d9c",
          "particles":"322245f7e5af2ecd55832e8f6d3ea3a462b7587210e343e1ae70b8ea87b3d692"}
def checksum(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for x in iter(lambda:f.read(1024*1024),b''):h.update(x)
    return h.hexdigest()
def save(image,out,maximum=None):
    img=image.convert('RGBA')
    if maximum:img.thumbnail((maximum,maximum),Image.Resampling.LANCZOS)
    img.save(out,optimize=True)
def main():
    p=argparse.ArgumentParser()
    for name in EXPECTED:p.add_argument('--'+name,required=True,type=Path)
    p.add_argument('--output',required=True,type=Path);a=p.parse_args()
    for name,digest in EXPECTED.items():
        if checksum(getattr(a,name))!=digest:raise SystemExit('Original '+name+' asset does not match its documented hash')
    out=a.output/'assets/art';out.mkdir(parents=True,exist_ok=True)
    with Image.open(a.neptune) as image:save(image,out/'neptune.png',640)
    with ZipFile(a.paper) as z:
        with Image.open(BytesIO(z.read('Humble Gift - Paper UI System v1.1/Sprites/Book Desk/1.png'))) as image:save(image,out/'paper_desk.png')
    with ZipFile(a.particles) as z:
        for name,dest in [('Particles/Complex/flare/flare_3.png','flare.png'),('Particles/Color/energyball/energyball_1.png','energy.png')]:
            with Image.open(BytesIO(z.read(name))) as image:save(image,out/dest,256)
    print('Prepared four small, credited app art assets.')
if __name__=='__main__':main()
