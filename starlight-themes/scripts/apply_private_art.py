#!/usr/bin/env python3
"""Add personal art to the unsigned APK without losing ZIP_STORED alignment."""
from pathlib import Path
import argparse
import struct
import zipfile

p=argparse.ArgumentParser()
p.add_argument('--apk',type=Path,required=True)
p.add_argument('--art',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
art={f.relative_to(a.art).as_posix():f for f in a.art.rglob('*') if f.is_file()}
expected={'assets/wallpapers/moonflower.png','assets/wallpapers/conservatory.png','assets/ambient/night-sky.png'}
keys=['notes','dream','atlas','video','reader','camera','music','files','settings','messages','calendar','browser','phone','gallery','weather','clock','search','apps']
expected.update('assets/icons/'+palette+'_'+key+'.png' for palette in ('midnight','petal') for key in keys)
expected.update('assets/moth/moth_%02d.png' % i for i in range(16))
if set(art)!=expected: raise SystemExit('Private art files differ from the expected set')
with zipfile.ZipFile(a.apk) as source, zipfile.ZipFile(a.output,'w') as target:
    for old in source.infolist():
        if old.filename in art: raise SystemExit('Unexpected private art already in input')
        item=zipfile.ZipInfo(old.filename,old.date_time)
        item.compress_type=old.compress_type; item.external_attr=old.external_attr
        item.comment=old.comment; item.extra=old.extra
        if item.compress_type==zipfile.ZIP_STORED:
            offset=target.fp.tell(); name=item.filename.encode('utf-8')
            pad=(- (offset+30+len(name)+len(item.extra))) % 4
            if pad: item.extra+=struct.pack('<HH',0xd935,pad)+b'\0'*pad
        target.writestr(item,source.read(old))
    for name,file in sorted(art.items()): target.write(file,name,compress_type=zipfile.ZIP_DEFLATED)
with zipfile.ZipFile(a.output) as z:
    if z.testzip() is not None: raise SystemExit('Bad APK ZIP')
    raw=a.output.read_bytes()
    for item in z.infolist():
        if item.compress_type==zipfile.ZIP_STORED:
            n,e=struct.unpack_from('<HH',raw,item.header_offset+26)
            if (item.header_offset+30+n+e)%4: raise SystemExit('Unaligned entry: '+item.filename)
print('Applied private artwork; APK ZIP and stored-file alignment checked.')
