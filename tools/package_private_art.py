#!/usr/bin/env python3
"""Add private artwork to an unsigned APK, aligning every stored ZIP entry."""
import argparse,copy,struct
from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_STORED,ZIP_DEFLATED

def align(info,offset):
    info.extra=b''
    if info.compress_type==ZIP_STORED:
        boundary=16384 if info.filename.startswith('lib/') and info.filename.endswith('.so') else 4
        n=len(info.filename.encode('utf-8'))
        pad=(-(offset+30+n+4))%boundary
        info.extra=struct.pack('<HH',0xffff,pad)+b'\0'*pad
def main():
    p=argparse.ArgumentParser();p.add_argument('--apk',type=Path,required=True);p.add_argument('--overlay',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    expected={'assets/art/neptune.png','assets/art/paper_desk.png','assets/art/flare.png','assets/art/energy.png'}
    files={f.relative_to(a.overlay).as_posix():f for f in a.overlay.rglob('*') if f.is_file()}
    if set(files)!=expected:raise SystemExit('Unexpected private overlay contents')
    with ZipFile(a.apk) as src,ZipFile(a.output,'w',allowZip64=False) as out:
        names=src.namelist()
        if len(names)!=len(set(names)) or set(names)&expected:raise SystemExit('APK has duplicate or pre-existing private art paths')
        for entry in src.infolist():
            name=entry.filename.upper();leaf=name.rsplit('/',1)[-1]
            if name.startswith('META-INF/') and (leaf=='MANIFEST.MF' or leaf.endswith(('.SF','.RSA','.DSA','.EC')) or leaf.startswith('SIG-')):continue
            data=src.read(entry.filename);info=copy.copy(entry);align(info,out.fp.tell());out.writestr(info,data)
        for name,path in sorted(files.items()):
            info=ZipInfo(name);info.compress_type=ZIP_STORED;align(info,out.fp.tell());out.writestr(info,path.read_bytes())
    print('Added four private app art assets, ready for signing')
if __name__=='__main__':main()
