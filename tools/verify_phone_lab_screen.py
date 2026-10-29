#!/usr/bin/env python3
"""Check a captured Android PNG for the selected Bloom button in the real arena.

Canvas is rendered at 960x540 with aspect-preserving letterboxing. This is a
small smoke-test oracle: it distinguishes the selected control from the title
screen or Android's first-run fullscreen overlay, not a test of game feel.
Uses only the Python standard library on the hosted Android runner.
"""
import struct,sys,zlib
from pathlib import Path

def pixels(path):
 data=Path(path).read_bytes()
 if data[:8]!=b'\x89PNG\r\n\x1a\n':raise ValueError('Not a PNG screenshot')
 p=8;raw=[];width=height=depth=color=interlace=None
 while p<len(data):
  n=struct.unpack_from('>I',data,p)[0];kind=data[p+4:p+8];body=data[p+8:p+8+n];p+=n+12
  if kind==b'IHDR':width,height,depth,color,_,_,interlace=struct.unpack('>IIBBBBB',body)
  elif kind==b'IDAT':raw.append(body)
  elif kind==b'IEND':break
 if depth!=8 or color not in (2,6) or interlace!=0:raise ValueError('Unsupported PNG layout')
 bpp=4 if color==6 else 3;stride=width*bpp;raw=zlib.decompress(b''.join(raw));rows=[];pos=0;prev=bytearray(stride)
 for _ in range(height):
  filt=raw[pos];pos+=1;row=bytearray(raw[pos:pos+stride]);pos+=stride
  for i in range(stride):
   a=row[i-bpp] if i>=bpp else 0;b=prev[i];c=prev[i-bpp] if i>=bpp else 0
   if filt==1:v=a
   elif filt==2:v=b
   elif filt==3:v=(a+b)//2
   elif filt==4:
    q=a+b-c;da=abs(q-a);db=abs(q-b);dc=abs(q-c);v=a if da<=db and da<=dc else b if db<=dc else c
   elif filt==0:v=0
   else:raise ValueError('Unsupported PNG filter')
   row[i]=(row[i]+v)&255
  rows.append(row);prev=row
 return width,height,bpp,rows

def point(w,h,world_x,world_y):
 scale=min(w/960,h/540);ox=(w-960*scale)/2;oy=(h-540*scale)/2
 return round(ox+world_x*scale),round(oy+world_y*scale)

def main(path):
 w,h,bpp,rows=pixels(path)
 if w<=h:raise AssertionError(f'Expected landscape gameplay, got {w}x{h}')
 # Selected Bloom button upper-left interior: avoids its label and rounded edge.
 x,y=point(w,h,450,493);off=x*bpp;r,g,b=rows[y][off:off+3]
 if not(r>165 and r>g*1.35 and r>b*1.16):
  raise AssertionError(f'Bloom selected button not detected at ({x},{y}); RGB={r},{g},{b}. Check screenshot for overlay or wrong screen.')
 print(f'PASS visible selected Bloom control at ({x},{y}); screenshot {w}x{h}; RGB={r},{g},{b}.')

def check_shot(before,after,heart_x=570):
 w,h,bpp,rows=pixels(before);w2,h2,bpp2,rows2=pixels(after)
 if (w,h)!=(w2,h2):raise AssertionError('Screen dimensions changed during the shot')
 x,y=point(w,h,heart_x,36);red_before=rows[y][x*bpp];red_after=rows2[y][x*bpp2]
 if red_before-red_after<25:raise AssertionError(f'Last shot indicator did not dim: {red_before} -> {red_after}. Inspect screenshots.')
 print(f'PASS shot indicator dimmed after swipe: red {red_before} -> {red_after}.')

def check_rival(path):
 w,h,bpp,rows=pixels(path)
 # The Night 10 fixture has a sixth shot heart and three tiny rose health
 # hearts above the new opponent. These pixels are not present on Night 1.
 for wx,wy in [(606,38),(655,284),(667,284),(679,284)]:
  x,y=point(w,h,wx,wy);r,g,b=rows[y][x*bpp:x*bpp+3]
  if not(r>115 and r>g*1.25 and r>b*1.05):
   raise AssertionError(f'Night 10 marker absent at world {wx},{wy}: {r},{g},{b}')
 print('PASS six-shot Night 10 and three visible rival hearts.')

if __name__=='__main__':
 if len(sys.argv)==4 and sys.argv[1]=='--shot':check_shot(sys.argv[2],sys.argv[3])
 elif len(sys.argv)==4 and sys.argv[1]=='--shot-6':check_shot(sys.argv[2],sys.argv[3],606)
 elif len(sys.argv)==3 and sys.argv[1]=='--rival':check_rival(sys.argv[2])
 elif len(sys.argv)==2:main(sys.argv[1])
 else:raise SystemExit('Usage: verify_phone_lab_screen.py SELECTED.png | --shot BEFORE.png AFTER.png')
