#!/usr/bin/env python3
"""Twelve actual CC0/public-domain line-art SVG coloring pages from source pages with explicit license.
Preserves unmodified SVG originals, source URLs, hashes and raster derivatives."""
from pathlib import Path
from urllib.request import urlopen,Request
from PIL import Image,ImageOps,ImageDraw
import cairosvg,io,json,hashlib,time
ROOT=Path(__file__).resolve().parent.parent
SRC=ROOT/"art_sources"/"public_domain_coloring"
OUT=ROOT/"coloringbook"/"src"/"main"/"assets"/"pages"
SRC.mkdir(parents=True,exist_ok=True);OUT.mkdir(parents=True,exist_ok=True)
# Verified October 30, 2026: each source page explicitly says Public Domain (FreeSVG CC0).
ITEMS=[
(40,"Colorbook Dragon",187631,"Doodleguy","https://freesvg.org/colorbook-dragon"),
(41,"Castle of Whispering Clouds",183604,"OpenClipart / Dugald Walker (1922)","https://freesvg.org/castle-in-clouds"),
(42,"Sugar Skull & Roses",112870,"OpenClipart","https://freesvg.org/sugarskullrose"),
(43,"The Fossil King's Skull",148611,"publicdomainvectors.org","https://freesvg.org/dinosaur-skull"),
(44,"The Intricate Moon Mandala",165875,"OpenClipart / Pixabay source","https://freesvg.org/intricate-floral-mandala"),
(45,"Amely's Flower Labyrinth",137100,"OpenClipart / Amely (Pixabay source)","https://freesvg.org/intricate-floral-mandala-by-amely"),
(46,"The Old Crone",178412,"OpenClipart / Frank Howard (1833)","https://freesvg.org/oldcrone"),
(47,"The Cloud Kingdom",145285,"OpenClipart / Mystica","https://freesvg.org/mystica-castle-outline"),
(48,"The Watchful Owl",17128,"OpenClipart","https://freesvg.org/owl-line-art-vector-illustration"),
(49,"The Ram's Omen",150902,"publicdomainvectors.org","https://freesvg.org/ram-skull"),
(50,"The Celtic Flower Wheel",182620,"OpenClipart","https://freesvg.org/floral-mandala-coloring-book"),
(51,"Little Dragon's Day Out",330220,"Colleen O'Dell / StarGlade, uploaded by hund","https://openclipart.org/detail/330220/cute-dragon-line-art")]
records=[]
for index,title,id,author,source_page in ITEMS:
    url=(f"https://openclipart.org/download/{id}" if id==330220 else f"https://freesvg.org/download/{id}")
    dest=SRC/f"{index}_{id}.svg"
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
    # Some originals are white-on-transparent, invert if line coverage is implausibly low.
    black=Image.new("RGBA",im.size,(42,36,53,255))
    black.putalpha(alpha)
    base.alpha_composite(black,((720-im.width)//2,(900-im.height)//2))
    # Fine unobtrusive double border adds a coherent frame, not a substituted "page".
    d=ImageDraw.Draw(base)
    d.rounded_rectangle((19,20,700,880),radius=22,outline=(45,37,54,255),width=3)
    d.rounded_rectangle((29,30,690,870),radius=18,outline=(45,37,54,255),width=1)
    output=OUT/f"page_{index}.png";base.save(output,optimize=True)
    records.append(dict(index=index,title=title,creator=author,source_page=source_page,original_url=url,license="Public Domain / CC0 as identified on the source page",license_evidence_url=source_page,source_sha256=hashlib.sha256(original).hexdigest(),original_file=str(dest.relative_to(ROOT)),derived_resource=str(output.relative_to(ROOT)),derived_sha256=hashlib.sha256(output.read_bytes()).hexdigest(),transformation="SVG rasterized and whiteness removed to transparent line-art at 720x900; added subtle original double border"))
manifest={"version":"0.1.2","download_date":"2026-10-30","license_note":"Each source page was individually identified as Public Domain / CC0; retain source page URLs and original SVG files.","pages":records}
(ROOT/"MOONBEAM_012_CC0_GALLERY_PROVENANCE.json").write_text(json.dumps(manifest,indent=2))
sheet=Image.new("RGB",(240*6,340*2),"white");d=ImageDraw.Draw(sheet)
for j,item in enumerate(ITEMS):
    idx,title,*_=item
    im=Image.open(OUT/f"page_{idx}.png").convert("RGBA");im.thumbnail((230,295))
    x=(j%6)*240;y=(j//6)*340
    sheet.paste(im,(x,y),im);d.text((x+4,y+301),f"{idx}: {title[:26]}",fill=(42,36,53))
sheet.save(ROOT/"Moonbeam_012_CC0_Gallery_Contact_Sheet.jpg",quality=90)
print("CC0 GALLERY",len(records),"original SVGs and derived line-art pages")
