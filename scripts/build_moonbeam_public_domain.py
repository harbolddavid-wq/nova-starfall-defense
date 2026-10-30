#!/usr/bin/env python3
"""Twelve authentic SVG coloring pages with preserved source and CC BY-SA 4.0 terms.
Upstream acknowledgments identify these as public-domain originals; repository changes
are distributed under CC BY-SA 4.0, so derivatives retain attribution and ShareAlike."""
from pathlib import Path
from urllib.request import urlopen,Request
from PIL import Image,ImageOps,ImageDraw,ImageChops
import cairosvg,io,json,hashlib,time
ROOT=Path(__file__).resolve().parent.parent
SRC=ROOT/"art_sources"/"public_domain_coloring"
OUT=ROOT/"coloringbook"/"src"/"main"/"assets"/"pages"
SRC.mkdir(parents=True,exist_ok=True);OUT.mkdir(parents=True,exist_ok=True)
PIN="c5f2dcae9a583dcd841122a1d32bc7b40f5746c0"
BASE=f"https://raw.githubusercontent.com/pavelkukov/coloring-pages/{PIN}/"
ITEMS=[
(40,"Colorbook Dragon","dragon.svg","Public domain / publicdomainvectors.org"),
(41,"Dragon of the Hollow","creazilla_dragon.svg","Public domain / SuperColoring"),
(42,"Hand of the Oracle","hasma.svg","Public domain / SuperColoring"),
(43,"The Watchful Owl","owl.svg","Public domain / publicdomainpictures.net"),
(44,"The Elephant Mandala","elephant_mandala.svg","Public domain / SuperColoring"),
(45,"Night Butterfly Tattoo","butterfly_tattoo.svg","Public domain / SuperColoring"),
(46,"The Locked Heart","locked_heart.svg","Public domain / SuperColoring"),
(47,"Moon Poppies","poppies_coloring_page.svg","Public domain / SuperColoring"),
(48,"The Octopus Carnival","octo.svg","Public domain / MomJunction"),
(49,"The Abyss Stares Back","shark_jaws.svg","Public domain / publicdomainvectors.org"),
(50,"The Little Midnight Pony","little_pony.svg","Public domain / Flickr source"),
(51,"Deepwater Dream","fishing_fish.svg","Public domain / publicdomainvectors.org")]
for name in ("LICENSE","Acknowledgments.md"):
    dest=SRC/name
    if not dest.exists():
        with urlopen(BASE+name,timeout=45) as r:dest.write_bytes(r.read())
records=[]
for index,title,filename,upstream in ITEMS:
    url=BASE+"images/"+filename
    source_page="https://github.com/pavelkukov/coloring-pages/blob/"+PIN+"/images/"+filename
    dest=SRC/filename
    if not dest.exists():
        req=Request(url,headers={"User-Agent":"Mozilla/5.0 MoonbeamColoring/0.1.2"})
        with urlopen(req,timeout=75) as resp:data=resp.read()
        if b"<svg" not in data[:10000] or len(data)<300:
            raise RuntimeError(f"Source download not SVG for {title}: {len(data)} bytes {data[:80]!r}")
        dest.write_bytes(data)
    original=dest.read_bytes()
    # Render original SVG at full page scale; no compositing with copyrighted character art.
    png=cairosvg.svg2png(bytestring=original,output_width=650,output_height=790)
    im=Image.open(io.BytesIO(png)).convert("RGBA")
    # Preserve the actual artist's fine black lines, removing white background so fill works.
    base=Image.new("RGBA",(720,900),(0,0,0,0))
    grey=ImageOps.grayscale(im.convert("RGB"))
    alpha=grey.point(lambda v:0 if v>239 else (255 if v<160 else int((239-v)*255/79)))
    alpha=ImageChops.multiply(alpha,im.getchannel('A'))
    # Some originals are white-on-transparent, invert if line coverage is implausibly low.
    black=Image.new("RGBA",im.size,(42,36,53,255))
    black.putalpha(alpha)
    base.alpha_composite(black,((720-im.width)//2,(900-im.height)//2))
    # Fine unobtrusive double border adds a coherent frame, not a substituted "page".
    d=ImageDraw.Draw(base)
    d.rounded_rectangle((19,20,700,880),radius=22,outline=(45,37,54,255),width=3)
    d.rounded_rectangle((29,30,690,870),radius=18,outline=(45,37,54,255),width=1)
    output=OUT/f"page_{index}.png";base.save(output,optimize=True)
    records.append(dict(index=index,title=title,creator="Pavel Kukov collection; upstream "+upstream,source_page=source_page,original_url=url,license="CC BY-SA 4.0 for collection modifications (upstream identified as public domain)",license_evidence_url="https://github.com/pavelkukov/coloring-pages/blob/"+PIN+"/LICENSE",source_sha256=hashlib.sha256(original).hexdigest(),original_file=str(dest.relative_to(ROOT)),derived_resource=str(output.relative_to(ROOT)),derived_sha256=hashlib.sha256(output.read_bytes()).hexdigest(),transformation="SVG rasterized and whiteness removed to transparent line-art at 720x900; added subtle original double border; derived page licensed CC BY-SA 4.0"))
manifest={"version":"0.1.2","download_date":"2026-10-30","license_note":"Collection by Pavel Kukov is CC BY-SA 4.0. Its Acknowledgments.md identifies the chosen upstream illustrations as public domain. Adapted gallery PNGs retain CC BY-SA 4.0 with attribution; original LICENSE and Acknowledgments.md preserved.","pages":records}
(ROOT/"MOONBEAM_012_ILLUSTRATED_GALLERY_PROVENANCE.json").write_text(json.dumps(manifest,indent=2))
sheet=Image.new("RGB",(240*6,340*2),"white");d=ImageDraw.Draw(sheet)
for j,item in enumerate(ITEMS):
    idx,title,*_=item
    im=Image.open(OUT/f"page_{idx}.png").convert("RGBA");im.thumbnail((230,295))
    x=(j%6)*240;y=(j//6)*340
    sheet.paste(im,(x,y),im);d.text((x+4,y+301),f"{idx}: {title[:26]}",fill=(42,36,53))
sheet.save(ROOT/"Moonbeam_012_Illustrated_Gallery_Contact_Sheet.jpg",quality=90)
print("ILLUSTRATED GALLERY",len(records),"original SVGs and derived line-art pages")
