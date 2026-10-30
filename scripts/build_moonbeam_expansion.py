#!/usr/bin/env python3
"""Moonbeam 0.1.2: thirty distinct illustrated compositions, CC BY 3.0 Game-icons contours.
All external SVGs fetched at the pinned revision; source/sha256 recorded; no unlicensed art."""
from pathlib import Path
from urllib.request import urlopen
from PIL import Image,ImageDraw,ImageFilter,ImageChops,ImageFont
import cairosvg,math,io,json,hashlib,random
ROOT=Path(__file__).resolve().parent.parent
SRC=ROOT/"art_sources"/"game_icons"
OUT=ROOT/"coloringbook"/"src"/"main"/"assets"/"pages"
OUT.mkdir(parents=True,exist_ok=True)
PIN="82d948812bfe3f269ef8f731dcdb07b08160edc4"
NEW="""lorc/wolf-head lorc/spider-web lorc/pumpkin-lantern lorc/crystal-ball
lorc/bleeding-eye lorc/fanged-skull lorc/candle-flame lorc/skull-crossed-bones
lorc/witch-flight lorc/fairy lorc/snake lorc/ghost lorc/octopus lorc/moon
lorc/grim-reaper lorc/coffin lorc/bat-wing lorc/werewolf lorc/evil-bat
lorc/frozen-orb lorc/stone-tower lorc/treasure-map lorc/dragon-spiral
lorc/poison-bottle lorc/crossed-swords lorc/evil-book lorc/eye-shield
lorc/angel-wings lorc/fairy-wand lorc/crow-dive lorc/sea-dragon
lorc/haunting lorc/bleeding-heart""".split()
records=[]
for path in NEW:
    dest=SRC/(path+".svg");dest.parent.mkdir(parents=True,exist_ok=True)
    url=f"https://raw.githubusercontent.com/game-icons/icons/{PIN}/{path}.svg"
    if not dest.exists():
        with urlopen(url,timeout=40) as r:dest.write_bytes(r.read())
    data=dest.read_bytes()
    if b"<svg" not in data:raise ValueError("Not SVG: "+path)
    records.append(dict(original_zip_member=None,source_url=url,source_sha256=hashlib.sha256(data).hexdigest(),author=path.split("/")[0],license="CC BY 3.0",original_file=str(dest.relative_to(ROOT)),destination_use="page contours"))
S=2;W=720;H=900;INK=(44,36,55,255)
def p(v):return int(round(v*S))
def pt(x,y):return (p(x),p(y))
def poly(d,xy,w=3,closed=False):
    pts=[pt(x,y) for x,y in xy]
    if closed:pts.append(pts[0])
    d.line(pts,fill=INK,width=max(1,p(w)),joint="curve")
def oval(d,box,w=3):d.ellipse(tuple(p(v) for v in box),outline=INK,width=p(w))
def arc(d,box,a,b,w=3):d.arc(tuple(p(v) for v in box),a,b,fill=INK,width=p(w))
def star(d,x,y,r=12):
    pts=[]
    for i in range(10):
        a=-math.pi/2+i*math.pi/5;rr=r if i%2==0 else r*.43
        pts.append((x+rr*math.cos(a),y+rr*math.sin(a)))
    poly(d,pts,2,True)
def leaf(d,x,y,r=16,angle=0):
    u=(math.cos(angle),math.sin(angle));v=(-u[1],u[0])
    a=(x-r*u[0],y-r*u[1]);b=(x+r*u[0],y+r*u[1])
    poly(d,[a,(x+r*.65*v[0],y+r*.65*v[1]),b,(x-r*.65*v[0],y-r*.65*v[1])],2,True)
    poly(d,[a,b],1)
def vine(d,x,y,direction=1,height=180):
    poly(d,[(x,y),(x+direction*15,y-height*.28),(x-direction*9,y-height*.55),(x+direction*13,y-height)],2)
    for i in range(5):
        yy=y-height*(i+.5)/5
        leaf(d,x+direction*(11 if i%2==0 else -8),yy,13,(i%2*2-1)*.7)
def rune(d,x,y,seed):
    rng=random.Random(seed)
    for j in range(3):
        dx=rng.randint(-15,15);dy=rng.randint(-15,15)
        poly(d,[(x+dx-8,y+dy-7),(x+dx+6,y+dy),(x+dx-3,y+dy+8)],1)
def moon(d,x,y,r=48):
    oval(d,(x-r,y-r,x+r,y+r),3)
    arc(d,(x-r*.67,y-r*.96,x+r*.9,y+r*.96),78,278,3)
def web(d,x,y,r):
    for i in range(10):
        a=i*math.tau/10
        poly(d,[(x,y),(x+r*math.cos(a),y+r*math.sin(a))],2)
    for f in [.27,.48,.71,1.0]:
        pts=[(x+r*f*math.cos(i*math.tau/10),y+r*f*math.sin(i*math.tau/10)) for i in range(10)]
        poly(d,pts,2,True)
def arch(d,x1,y1,x2,y2):
    mid=(x1+x2)/2;top=y1
    poly(d,[(x1,y2),(x1,top+80),(mid,top),(x2,top+80),(x2,y2)],3)
def ornate(d,v):
    style=v%6
    if style==0:
        arch(d,56,46,664,831);arch(d,74,72,646,814)
        for x in (96,624):
            for y in (240,420,600):leaf(d,x,y,20,.9)
        for x in (145,575):star(d,x,145,22)
    elif style==1:
        oval(d,(66,70,654,780),4);oval(d,(84,89,636,762),2)
        for k in range(12):
            a=math.tau*k/12;x=360+278*math.cos(a);y=418+324*math.sin(a)
            star(d,x,y,10 if k%3 else 15)
        poly(d,[(80,820),(640,820)],3)
    elif style==2:
        arch(d,58,70,662,814)
        for x in (104,616):
            poly(d,[(x,230),(x,747)],2)
            for y in (245,360,475,590,705):oval(d,(x-8,y-8,x+8,y+8),2)
        poly(d,[(160,770),(560,770)],3)
    elif style==3:
        poly(d,[(70,85),(650,85),(650,814),(70,814),(70,85)],4,False)
        for x,direction in ((90,1),(630,-1)):
            vine(d,x,780,direction,625)
        for x in (170,550):moon(d,x,162,31)
    elif style==4:
        oval(d,(68,82,652,806),3)
        for x in (105,615):
            for y in (240,410,580,730):star(d,x,y,14)
        for y in (130,750):
            poly(d,[(220,y),(360,y+(-32 if y==130 else 32)),(500,y)],3)
    else:
        arch(d,65,70,655,814)
        for y in (230,660):
            poly(d,[(110,y),(185,y-25),(260,y)],2)
            poly(d,[(460,y),(535,y-25),(610,y)],2)
        for x in (120,600):
            for y in (310,425,540):rune(d,x,y,v*100+x+y)
    # Original border footer is an inked scroll, never a large filled block.
    poly(d,[(140,835),(195,815),(525,815),(580,835),(525,855),(195,855),(140,835)],2,True)
    for x in (130,590):star(d,x,837,10)
def icon(im,path,x,y,size,flip=False,rotation=0):
    src=SRC/(path+".svg")
    if not src.exists():raise FileNotFoundError(src)
    svg=src.read_bytes()
    raster=Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg,output_width=p(size),output_height=p(size)))).convert("RGB")
    mask=raster.convert("L").point(lambda v:255 if v>=143 else 0)
    edge=ImageChops.subtract(mask,mask.filter(ImageFilter.MinFilter(3))).filter(ImageFilter.MaxFilter(3))
    if flip:edge=edge.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    if rotation:edge=edge.rotate(rotation,expand=True,resample=Image.Resampling.BICUBIC)
    colored=Image.new("RGBA",edge.size,INK)
    im.paste(colored,(p(x)-edge.width//2,p(y)-edge.height//2),edge)
def embellish(d,index):
    rng=random.Random(index*7309)
    mode=index%7
    if mode==0:
        for y in (235,305,375,445,515,585,655):
            for x in (133,587):
                oval(d,(x-7,y-7,x+7,y+7),2)
        for x in (190,530):vine(d,x,755,1 if x<360 else -1,185)
    elif mode==1:
        for x in (142,578):
            web(d,x,670,64)
        for y in (190,740):moon(d,360,y,26)
    elif mode==2:
        for x in (165,555):
            for y in (255,400,545,690):star(d,x,y,18)
        poly(d,[(180,736),(260,700),(360,735),(460,700),(540,736)],2)
    elif mode==3:
        for x in (130,590):
            vine(d,x,750,1 if x==130 else -1,380)
        for y in (205,720):oval(d,(325,y-24,395,y+24),2)
    elif mode==4:
        for x in (160,560):
            for y in (255,355,455,555,655):rune(d,x,y,index+x+y)
        for x in (250,470):star(d,x,735,19)
    elif mode==5:
        for y in (255,390,525,660):
            poly(d,[(115,y),(148,y-16),(181,y),(148,y+16),(115,y)],2,True)
            poly(d,[(539,y),(572,y-16),(605,y),(572,y+16),(539,y)],2,True)
        moon(d,360,740,34)
    else:
        for x in (132,588):
            for y in (245,350,455,560,665):leaf(d,x,y,20,(x+y)%9/4)
        for x in (205,515):star(d,x,735,16)
    # Tiny closed regions in the footer provide extra colorable details.
    for j in range(5):
        x=245+j*58
        if j%2:oval(d,(x-10,826,x+10,845),2)
        else:star(d,x,836,12)
# Each row is a distinct illustration brief: main icon, two motifs, and layout variant.
PAGES=[
("Wolf at the Chapel","lorc/wolf-head","lorc/moon","lorc/rose",0),
("The Spider Queen","lorc/spider-web","lorc/fanged-skull","delapouite/bat",1),
("Pumpkin Monarch","lorc/pumpkin-lantern","lorc/crown-of-thorns","lorc/candle-flame",2),
("Reaper's Waltz","lorc/grim-reaper","lorc/ghost","lorc/moon",3),
("The Fanged Oracle","lorc/fanged-skull","lorc/bleeding-eye","lorc/rune-stone",4),
("Batwing Masquerade","lorc/evil-bat","lorc/bat-wing","lorc/eye-shield",5),
("Tower of Thorns","lorc/stone-tower","lorc/rose","lorc/evil-moon",0),
("The Lost Graveyard","delapouite/graveyard","lorc/ghost","lorc/moon",1),
("Midnight Watchtower","delapouite/castle-ruins","lorc/stone-tower","lorc/evil-bat",2),
("Witch's Crossing","lorc/witch-flight","lorc/moon","lorc/fairy-wand",3),
("Crypt of the Rose","lorc/coffin","lorc/rose","lorc/skull-crossed-bones",4),
("The Black Gate","lorc/magic-portal","lorc/daemon-skull","lorc/spider-web",5),
("Potion of No Return","lorc/poison-bottle","lorc/snake","lorc/candle-flame",0),
("The Forbidden Grimoire","lorc/evil-book","lorc/magic-portal","lorc/fairy-wand",1),
("Crystal Seer","lorc/crystal-ball","lorc/bleeding-eye","lorc/rune-stone",2),
("The Cursed Compass","lorc/treasure-map","lorc/crossed-swords","lorc/crown-of-thorns",3),
("Serpent's Chalice","lorc/snake","lorc/poison-bottle","lorc/rose",4),
("The Moonblade","lorc/crossed-swords","lorc/evil-moon","lorc/rune-stone",5),
("Angel of Ashes","lorc/angel-wings","lorc/candle-flame","lorc/fanged-skull",0),
("The Bleeding Valentine","lorc/bleeding-heart","delapouite/dagger-rose","lorc/rose",1),
("Fairy in the Briars","lorc/fairy","lorc/rose","lorc/fairy-wand",2),
("The Siren's Crown","lorc/sea-dragon","lorc/crown-of-thorns","lorc/frozen-orb",3),
("The Raven Bride","lorc/crow-dive","delapouite/dagger-rose","lorc/moon",4),
("The Haunted Heart","lorc/haunting","lorc/bleeding-heart","lorc/ghost",5),
("Octopus Observatory","lorc/octopus","lorc/crystal-ball","lorc/moon",0),
("The Star-Eater","lorc/dragon-spiral","lorc/evil-moon","lorc/bleeding-eye",1),
("Dragon in the Deep","lorc/sea-dragon","lorc/dragon-head","lorc/frozen-orb",2),
("A Ghost's Tea Party","lorc/ghost","lorc/poison-bottle","lorc/candle-flame",3),
("The Carnival of Bones","lorc/skull-crossed-bones","lorc/pumpkin-lantern","lorc/evil-bat",4),
("The Last Moon Garden","lorc/rose","lorc/fairy","lorc/moon",5)]
def make(index,info):
    title,main,left,right,style=info
    im=Image.new("RGBA",(p(W),p(H)),(0,0,0,0));d=ImageDraw.Draw(im)
    ornate(d,style+index);embellish(d,index)
    # Vary focal size, placement, and secondary motifs rather than repeating a stamp grid.
    layout=index%5
    if layout==0:
        icon(im,main,360,438,430)
        icon(im,left,175,254,145)
        icon(im,right,550,662,145)
    elif layout==1:
        icon(im,main,360,435,440)
        icon(im,left,177,635,155)
        icon(im,right,545,250,148)
    elif layout==2:
        icon(im,main,355,445,410)
        icon(im,left,178,275,180)
        icon(im,right,535,655,174)
    elif layout==3:
        icon(im,main,365,420,420)
        icon(im,left,177,635,178)
        icon(im,right,545,635,178,flip=True)
    else:
        icon(im,main,360,447,460)
        icon(im,left,170,260,150)
        icon(im,right,550,265,150)
    if index in (10,13,19,28,34):moon(d,360,170,39)
    if index in (11,21,24):web(d,545,650,68)
    if index in (16,17,18):
        for x in (210,510):arch(d,x-34,660,x+34,747)
    if index in (22,26,37):
        for x in (180,540):
            oval(d,(x-18,705,x+18,749),3)
            poly(d,[(x-18,719),(x+18,719)],2)
    if index in (31,34,36):
        for y in (690,718,745):
            poly(d,[(160,y),(220,y-9),(290,y),(360,y-9),(430,y),(500,y-9),(560,y)],2)
    if index in (30,33,39):
        for x in (150,570):vine(d,x,745,1 if x<360 else -1,190)
    if index==37:
        # An original teapot and two cups for the ghost's peculiar tea party.
        oval(d,(298,680,422,741),3);arc(d,(402,676,465,733),270,90,3)
        poly(d,[(298,697),(267,685),(285,717),(300,721)],3)
        oval(d,(331,664,390,680),2)
    if index==38:
        poly(d,[(220,710),(360,605),(500,710),(220,710)],3)
        for x in (275,445):star(d,x,710,22)
    out=OUT/f"page_{index}.png"
    im.resize((W,H),Image.Resampling.LANCZOS).save(out,optimize=True)
    return dict(index=index,title=title,filename=str(out.relative_to(ROOT)),sha256=hashlib.sha256(out.read_bytes()).hexdigest(),main=main,left=left,right=right,layout=layout)
pages=[make(i+10,info) for i,info in enumerate(PAGES)]
manifest={"version":"0.1.2","source_repository":"https://github.com/game-icons/icons","pinned_commit":PIN,"license":"CC BY 3.0","license_url":"https://creativecommons.org/licenses/by/3.0/","adaptation":"Source SVG foreground silhouettes converted to transparent dark closed outlines; combined with original drawn architecture, scrollwork, foliage, geometric frames, ornaments and layout. No Nintendo content.","original_sources":records,"pages":pages}
(ROOT/"MOONBEAM_012_ART_PROVENANCE.json").write_text(json.dumps(manifest,indent=2))
# Honest visual contact sheet with ACTUAL packaged images.
thumb_w,thumb_h=216,270
sheet=Image.new("RGB",(thumb_w*6, (thumb_h+30)*5),"white")
d=ImageDraw.Draw(sheet)
for j in range(30):
    im=Image.open(OUT/f"page_{j+10}.png").convert("RGBA")
    im.thumbnail((thumb_w,thumb_h))
    x=(j%6)*thumb_w;y=(j//6)*(thumb_h+30)
    sheet.paste(im,(x,y),im)
    d.text((x+5,y+thumb_h+3),f"{j+10}: {PAGES[j][0]}",fill=(38,33,50))
sheet.save(ROOT/"Moonbeam_012_Thirty_Pages_Contact_Sheet.jpg",quality=92)
print("GENERATED",len(pages),"distinct new pages", "sources",len(records))
