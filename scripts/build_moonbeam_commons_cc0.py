#!/usr/bin/env python3
"""Acquire and adapt two inspected CC0 Wikimedia Commons coloring illustrations.

Never substitute procedural icons for the original drawings. Source SVGs and
license evidence remain in the source archive; each derived page is hash logged.
"""
from pathlib import Path
from urllib.request import Request, urlopen
from PIL import Image, ImageDraw, ImageChops, ImageOps
from collections import deque
import cairosvg, hashlib, io, json, time

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "art_sources" / "wikimedia_cc0"
OUT = ROOT / "coloringbook" / "src" / "main" / "assets" / "pages"
SRC.mkdir(parents=True, exist_ok=True)
OUT.mkdir(parents=True, exist_ok=True)
ITEMS = [
    dict(index=52, title="The Dragon's Last Stand", name="Saint_George_slaying_the_Dragon_blank_drawing.svg",
         url="https://upload.wikimedia.org/wikipedia/commons/1/13/Saint_George_slaying_the_Dragon_%E2%80%93_blank_drawing.svg",
         page="https://commons.wikimedia.org/wiki/File:Saint_George_slaying_the_Dragon_%E2%80%93_blank_drawing.svg",
         creator="KissCC0", expected_sha1="a160b452fa138cff8b6b1841f8f94e03ce4a35ad"),
    dict(index=53, title="The Witch's Cottage", name="Witchs_cottage_in_black_and_white.svg",
         url="https://upload.wikimedia.org/wikipedia/commons/1/15/Witch%27s_cottage_in_black_and_white.svg",
         page="https://commons.wikimedia.org/wiki/File:Witch%27s_cottage_in_black_and_white.svg",
         creator="Ftiercel (Openclipart)", expected_sha1=None),
]
INK=(42,36,53,255)
def enclosed_count(im):
    # Count actually enclosed light regions at half resolution, not just SVG paths.
    sm=im.resize((360,450),Image.Resampling.LANCZOS)
    a=sm.getchannel("A").tobytes()
    seen=bytearray(len(a)); n=0
    for start,alpha in enumerate(a):
        if alpha>=140 or seen[start]: continue
        q=deque([start]);seen[start]=1;area=0;edge=False
        while q:
            k=q.popleft();area+=1;x=k%360;y=k//360
            if x==0 or y==0 or x==359 or y==449:edge=True
            for j in (k-1 if x else -1,k+1 if x<359 else -1,k-360 if y else -1,k+360 if y<449 else -1):
                if j>=0 and not seen[j] and a[j]<140:
                    seen[j]=1;q.append(j)
        if not edge and area>=16:n+=1
    return n
records=[]
for item in ITEMS:
    dest=SRC/item["name"]
    if not dest.exists():
        req=Request(item["url"],headers={"User-Agent":"MoonbeamColoring/0.1.3 (educational art; Wikimedia Commons CC0)"})
        for attempt in range(3):
            try:
                with urlopen(req,timeout=75) as response: data=response.read()
                if len(data)<300 or b"<svg" not in data[:10000]:raise ValueError("Not a nonempty SVG")
                dest.write_bytes(data);break
            except Exception:
                if attempt==2:raise
                time.sleep(3*(attempt+1))
    original=dest.read_bytes()
    if item["expected_sha1"] and hashlib.sha1(original).hexdigest()!=item["expected_sha1"]:
        raise RuntimeError("Wikimedia source changed unexpectedly: "+item["name"])
    if len(original)>2_000_000 or b"<svg" not in original[:10000]:
        raise ValueError("Invalid SVG source "+item["name"])
    # Render original art, preserving its contours, not replacing it with generated shapes.
    rgba=Image.open(io.BytesIO(cairosvg.svg2png(bytestring=original,output_width=650))).convert("RGBA")
    rgba.thumbnail((650,790),Image.Resampling.LANCZOS)
    grey=ImageOps.grayscale(rgba.convert("RGB"))
    alpha=grey.point(lambda v:0 if v>241 else (255 if v<165 else int((241-v)*255/76)))
    alpha=ImageChops.multiply(alpha,rgba.getchannel("A"))
    ink=Image.new("RGBA",rgba.size,INK);ink.putalpha(alpha)
    page=Image.new("RGBA",(720,900),(0,0,0,0))
    page.alpha_composite(ink,((720-rgba.width)//2,(900-rgba.height)//2))
    # A subtle frame preserves the artist's work and gives a consistent gallery.
    d=ImageDraw.Draw(page)
    d.rounded_rectangle((18,18,702,882),radius=22,outline=INK,width=3)
    d.rounded_rectangle((28,28,692,872),radius=18,outline=INK,width=1)
    path=OUT/f'page_{item["index"]}.png';path.parent.mkdir(parents=True,exist_ok=True)
    page.save(path,optimize=True)
    count=enclosed_count(page)
    if count<6:raise RuntimeError(f'Artwork not sufficiently fillable: {item["name"]}, {count} regions')
    records.append(dict(index=item["index"],title=item["title"],creator=item["creator"],
        source_url=item["url"],source_page=item["page"],license="CC0 1.0 Universal",
        license_url="https://creativecommons.org/publicdomain/zero/1.0/",
        original_file=str(dest.relative_to(ROOT)),source_sha1=hashlib.sha1(original).hexdigest(),
        source_sha256=hashlib.sha256(original).hexdigest(),derived_file=str(path.relative_to(ROOT)),
        derived_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),closed_regions_half_resolution=count,
        adaptation="Rasterized original CC0 SVG to 720x900; white removed for flood-fill; original double frame added"))
manifest={"project":"Moonbeam Coloring","version":"0.1.3","source":"Wikimedia Commons",
          "license_evidence":"See each Wikimedia Commons source page; each explicitly displays CC0 1.0.",
          "pages":records}
(ROOT/"MOONBEAM_013_COMMONS_CC0_PROVENANCE.json").write_text(json.dumps(manifest,indent=2))
sheet=Image.new("RGB",(720,520),"white");draw=ImageDraw.Draw(sheet)
for j,item in enumerate(ITEMS):
    p=Image.open(OUT/f'page_{item["index"]}.png').convert("RGBA")
    p.thumbnail((335,465))
    x=10+j*355;y=8
    sheet.paste(p,(x,y),p)
    draw.text((x,y+470),item["title"],fill=(42,36,53))
sheet.save(ROOT/"Moonbeam_013_Commons_CC0_Contact_Sheet.jpg",quality=92)
(SRC/"LICENSE_AND_SOURCES.md").write_text(
    "# CC0 Wikimedia Commons source evidence\n\n"
    "Both original SVGs were downloaded from the Wikimedia Commons URLs in "
    "MOONBEAM_013_COMMONS_CC0_PROVENANCE.json. The linked description pages "
    "explicitly state CC0 1.0; creators are KissCC0 and Ftiercel. "
    "Original bytes and their SHA-256 hashes are retained. "
    "The adapted PNGs are rasterized black line art with a small added border.\n")
print("COMMONS CC0 PAGES",[(r["index"],r["closed_regions_half_resolution"]) for r in records])
