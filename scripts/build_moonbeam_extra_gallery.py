#!/usr/bin/env python3
"""Two extra authentic vector illustrations from Pavel Kukov's CC BY-SA 4.0 collection."""
from pathlib import Path
from urllib.request import urlopen,Request
from PIL import Image,ImageOps,ImageDraw,ImageChops
import cairosvg,io,json,hashlib
ROOT=Path(__file__).resolve().parent.parent
SRC=ROOT/"art_sources"/"public_domain_coloring"
OUT=ROOT/"coloringbook"/"src"/"main"/"assets"/"pages"
SRC.mkdir(parents=True,exist_ok=True);OUT.mkdir(parents=True,exist_ok=True)
PIN="c5f2dcae9a583dcd841122a1d32bc7b40f5746c0"
BASE=f"https://raw.githubusercontent.com/pavelkukov/coloring-pages/{PIN}/"
ITEMS=[
(53,"Knight of the Moon","knight_on_horse.svg","Nata Silina","http://www.supercoloring.com/coloring-pages/cartoon-knight-on-horse"),
(54,"Fairy of Briars","fairy_tattoo.svg","Bianca Vola","http://www.supercoloring.com/coloring-pages/fairy-tattoo?tag=58516")]
records=[]
for idx,title,filename,creator,upstream in ITEMS:
    path=SRC/filename;url=BASE+"images/"+filename
    if not path.exists():
        with urlopen(Request(url,headers={"User-Agent":"MoonbeamColoring/0.1.3"}),timeout=70) as r:data=r.read()
        if len(data)<300 or b"<svg" not in data[:10000]:raise ValueError("Not SVG: "+filename)
        path.write_bytes(data)
    raw=path.read_bytes()
    png=cairosvg.svg2png(bytestring=raw,output_width=650,output_height=790)
    im=Image.open(io.BytesIO(png)).convert("RGBA")
    gray=ImageOps.grayscale(im.convert("RGB"))
    alpha=gray.point(lambda v:0 if v>239 else (255 if v<160 else int((239-v)*255/79)))
    alpha=ImageChops.multiply(alpha,im.getchannel("A"))
    ink=Image.new("RGBA",im.size,(42,36,53,255));ink.putalpha(alpha)
    page=Image.new("RGBA",(720,900),(0,0,0,0))
    page.alpha_composite(ink,((720-im.width)//2,(900-im.height)//2))
    d=ImageDraw.Draw(page);d.rounded_rectangle((19,20,700,880),radius=22,outline=(45,37,54,255),width=3)
    d.rounded_rectangle((29,30,690,870),radius=18,outline=(45,37,54,255),width=1)
    output=OUT/f"page_{idx}.png";page.save(output,optimize=True)
    records.append(dict(index=idx,title=title,creator=creator,upstream_source=upstream,
        source_url=url,source_page=f"https://github.com/pavelkukov/coloring-pages/blob/{PIN}/images/{filename}",
        license="CC BY-SA 4.0 (collection adaptations; upstream identified in Acknowledgments.md)",
        license_url=f"https://github.com/pavelkukov/coloring-pages/blob/{PIN}/LICENSE",
        original_file=str(path.relative_to(ROOT)),source_sha256=hashlib.sha256(raw).hexdigest(),
        derived_file=str(output.relative_to(ROOT)),derived_sha256=hashlib.sha256(output.read_bytes()).hexdigest(),
        adaptation="SVG rendered as transparent line art at 720x900, subtle double frame"))
manifest={"version":"0.1.3","collection":"Pavel Kukov coloring-pages",
    "license":"CC BY-SA 4.0 for adapted collection works; preserve LICENSE and Acknowledgments.md",
    "pages":records}
(ROOT/"MOONBEAM_013_EXTRA_GALLERY_PROVENANCE.json").write_text(json.dumps(manifest,indent=2))
sheet=Image.new("RGB",(720,520),"white");d=ImageDraw.Draw(sheet)
for j,item in enumerate(ITEMS):
    idx,title,*_=item
    im=Image.open(OUT/f"page_{idx}.png").convert("RGBA");im.thumbnail((335,465))
    x=10+j*355;sheet.paste(im,(x,8),im);d.text((x,480),title,fill=(42,36,53))
sheet.save(ROOT/"Moonbeam_013_Extra_Gallery_Contact_Sheet.jpg",quality=92)
print("EXTRA GALLERY",len(records),"original SVGs, adapted pages, attribution and hashes")
