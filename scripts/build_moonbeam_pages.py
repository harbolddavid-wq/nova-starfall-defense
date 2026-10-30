#!/usr/bin/env python3
"""Moonbeam gothic coloring-page composer. Game-icons originals remain CC BY 3.0."""
from pathlib import Path
from urllib.request import urlopen
from PIL import Image, ImageDraw, ImageFilter, ImageChops
import cairosvg, io, math, hashlib, json

ROOT=Path(__file__).resolve().parent.parent
SRC=ROOT/'art_sources'/'game_icons'
OUT=ROOT/'coloringbook'/'src'/'main'/'assets'/'pages'
SRC.mkdir(parents=True,exist_ok=True);OUT.mkdir(parents=True,exist_ok=True)
PIN='82d948812bfe3f269ef8f731dcdb07b08160edc4'
FILES=['lorc/dragon-head.svg','lorc/dragon-breath.svg','lorc/pentagram-rose.svg','lorc/evil-moon.svg','lorc/daemon-skull.svg','lorc/crown-of-thorns.svg','lorc/rose.svg','lorc/raven.svg','lorc/rune-stone.svg','lorc/magic-portal.svg','delapouite/gargoyle.svg','delapouite/castle-ruins.svg','delapouite/bat.svg','delapouite/dagger-rose.svg','delapouite/graveyard.svg','delapouite/floating-ghost.svg']
records=[]
for item in FILES+['license.txt']:
    dest=SRC/item;dest.parent.mkdir(parents=True,exist_ok=True)
    url=f'https://raw.githubusercontent.com/game-icons/icons/{PIN}/{item}'
    if not dest.exists():
        with urlopen(url,timeout=35) as response: data=response.read()
        if len(data)<50:raise ValueError('Unexpected empty source '+item)
        dest.write_bytes(data)
    data=dest.read_bytes()
    if item.endswith('.svg') and b'<svg' not in data:raise ValueError('Not SVG '+item)
    records.append(dict(path=item,original_url=url,sha256=hashlib.sha256(data).hexdigest(),author=item.split('/')[0] if '/' in item else 'Game-icons',license='CC BY 3.0',transformation='silhouette edge extraction; arranged with original frame ornaments'))
(SRC/'DOWNLOADED_SOURCE_MANIFEST.json').write_text(json.dumps(dict(repository='https://github.com/game-icons/icons',pinned_commit=PIN,license='https://creativecommons.org/licenses/by/3.0/',downloaded_originals=records),indent=2))

S=2;W=720;H=900;INK=(47,38,55,255)
def q(v):return round(v*S)
def coords(a):return tuple(q(v) for v in a)
def line(d,points,width=3):d.line([(q(x),q(y)) for x,y in points],fill=INK,width=q(width),joint='curve')
def ellipse(d,box,width=3):d.ellipse(coords(box),outline=INK,width=q(width))
def star(d,x,y,r=10):
    pts=[]
    for i in range(10):
        a=-math.pi/2+i*math.pi/5; rr=r if i%2==0 else r*.43
        pts.append((x+math.cos(a)*rr,y+math.sin(a)*rr))
    line(d,pts+[pts[0]],2)
def leaf(d,x,y,r=20,a=0):
    ux=math.cos(a);uy=math.sin(a);vx=-uy;vy=ux
    p=(x-ux*r,y-uy*r);t=(x+ux*r,y+uy*r)
    line(d,[p,(x+vx*r*.6,y+vy*r*.6),t,(x-vx*r*.6,y-vy*r*.6),p],2)
    line(d,[p,t],1)
def icon(im,author,name,x,y,size,flip=False):
    svg=(SRC/author/(name+'.svg')).read_bytes()
    rgba=Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg,output_width=q(size),output_height=q(size)))).convert('RGB')
    mask=rgba.convert('L').point(lambda v:255 if v>=145 else 0)
    # Original SVG has black background/white foreground; convert silhouette to a
    # clean, closed outline with holes outlined, not a giant filled black square.
    edge=ImageChops.subtract(mask,mask.filter(ImageFilter.MinFilter(3))).filter(ImageFilter.MaxFilter(3))
    if flip:edge=edge.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    im.paste(Image.new('RGBA',edge.size,INK),(q(x-size/2),q(y-size/2)),edge)
def flourish(d,x,y,mirror=False):
    s=-1 if mirror else 1
    line(d,[(x,y+35),(x+s*15,y+10),(x+s*37,y),(x+s*52,y+12)],2)
    for k in range(3):leaf(d,x+s*(16+k*13),y+14-k*5,9,math.pi/3)
def frame(d,v):
    line(d,[(58,834),(58,251),(75,205),(110,167),(360,54),(610,167),(645,205),(662,251),(662,834),(58,834)],4)
    line(d,[(75,819),(75,258),(90,219),(124,182),(360,78),(596,182),(630,219),(645,258),(645,819),(75,819)],2)
    line(d,[(58,806),(662,806)],2);line(d,[(75,788),(645,788)],2)
    for x in [101,619]:
        for y in [272,610,763]:star(d,x,y,12)
    for x,s in [(91,1),(629,-1)]:
        for j in range(5):leaf(d,x+s*(7+(j%2)*5),330+j*80,13,math.pi/4*(2*(j%2)-1))
    for x in [160,560]:ellipse(d,(x-12,119,x+12,143),2)
    star(d,360,112,19)
    if v%2==0:
        for x in [130,590]:
            for y in [285,505,720]:star(d,x,y,8)
def page(v):
    im=Image.new('RGBA',(q(W),q(H)),(0,0,0,0));d=ImageDraw.Draw(im);frame(d,v)
    if v==5:
        icon(im,'lorc','dragon-head',360,414,460)
        icon(im,'lorc','dragon-breath',162,620,150)
        icon(im,'lorc','pentagram-rose',550,639,145)
        icon(im,'lorc','evil-moon',161,243,135)
        for y in [285,330,375]:leaf(d,575,y,15,math.pi/3)
    elif v==6:
        icon(im,'delapouite','gargoyle',360,430,480)
        icon(im,'delapouite','castle-ruins',360,725,210)
        icon(im,'delapouite','bat',165,262,130)
        icon(im,'lorc','evil-moon',545,245,125)
        for x in [155,565]:
            line(d,[(x-25,736),(x-25,640),(x,608),(x+25,640),(x+25,736)],3)
            ellipse(d,(x-10,650,x+10,678),2)
    elif v==7:
        icon(im,'lorc','daemon-skull',360,428,455)
        icon(im,'lorc','crown-of-thorns',360,205,190)
        icon(im,'lorc','rose',169,685,175)
        icon(im,'delapouite','dagger-rose',552,679,170)
        for x in [125,595]:
            for y in [350,400,450]:leaf(d,x,y,17,math.pi/4)
        star(d,360,724,25)
    elif v==8:
        icon(im,'lorc','raven',360,404,450)
        icon(im,'lorc','evil-moon',168,245,160)
        icon(im,'delapouite','graveyard',360,715,260)
        icon(im,'lorc','rune-stone',570,280,120)
        for x in [200,520]:
            for y in [495,550]:star(d,x,y,13)
    elif v==9:
        line(d,[(215,749),(215,365),(236,303),(281,258),(360,222),(439,258),(484,303),(505,365),(505,749),(215,749)],6)
        line(d,[(244,730),(244,370),(265,323),(305,286),(360,257),(415,286),(455,323),(476,370),(476,730),(244,730)],4)
        icon(im,'lorc','magic-portal',360,495,255)
        icon(im,'lorc','rune-stone',155,570,138)
        icon(im,'lorc','rune-stone',565,570,138,True)
        icon(im,'delapouite','floating-ghost',360,178,125)
        for x in [270,450]:
            for y in [375,425,475,525,575,625]:ellipse(d,(x-5,y-5,x+5,y+5),2)
        ellipse(d,(447,545,461,559),2)
    for x in [135,585]:flourish(d,x,770,x>360)
    out=OUT/f'page_{v}.png'
    im.resize((W,H),Image.Resampling.LANCZOS).save(out,optimize=True)
    print('BUILT',out,out.stat().st_size)
for i in range(5,10):page(i)
# A compact preview contact sheet helps verify that all five pages differ.
sheet=Image.new('RGB',(W*5,H),'white')
for i in range(5,10):
    p=Image.open(OUT/f'page_{i}.png').convert('RGBA')
    sheet.paste(p,((i-5)*W,0),p)
sheet.thumbnail((1800,500))
sheet.save(ROOT/'Moonbeam_Gothic_Pages_Preview.jpg',quality=88)
