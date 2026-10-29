#!/usr/bin/env python3
"""Build an unsigned APK with the Android SDK. Private art is added after CI."""
from pathlib import Path
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ['ANDROID_HOME'])
TOOLS = SDK / 'build-tools' / '35.0.0'
ANDROID = SDK / 'platforms' / 'android-35' / 'android.jar'
BUILD = ROOT / 'build' / 'android'
if not ANDROID.exists() or not (TOOLS / 'lib/d8.jar').exists():
    raise SystemExit('Android platform 35 and build-tools 35.0.0 are required')
if BUILD.exists(): shutil.rmtree(BUILD)
for path in (BUILD / 'classes', BUILD / 'dex', ROOT / 'output'): path.mkdir(parents=True, exist_ok=True)
def run(args): subprocess.run([str(a) for a in args], cwd=ROOT, check=True)
run(['javac', '--release', '8', '-cp', ANDROID, '-d', BUILD / 'classes', *sorted((ROOT / 'android/src').rglob('*.java'))])
run([TOOLS / 'aapt2', 'compile', '--dir', ROOT / 'android/res', '-o', BUILD / 'resources.zip'])
run([TOOLS / 'aapt2', 'link', '-o', BUILD / 'base.apk', '--manifest', ROOT / 'android/AndroidManifest.xml', '-I', ANDROID, BUILD / 'resources.zip'])
run(['java', '-cp', TOOLS / 'lib/d8.jar', 'com.android.tools.r8.D8', '--release', '--min-api', '26', '--lib', ANDROID, '--output', BUILD / 'dex', *sorted((BUILD / 'classes').rglob('*.class'))])
with zipfile.ZipFile(BUILD / 'base.apk', 'a', zipfile.ZIP_DEFLATED) as apk:
    for f in sorted((BUILD / 'dex').glob('*.dex')): apk.write(f, f.name)
run([TOOLS / 'zipalign', '-f', '-p', '4', BUILD / 'base.apk', ROOT / 'output' / 'starlight-unsigned.apk'])
shutil.copyfile(TOOLS / 'lib/apksigner.jar', ROOT / 'output' / 'apksigner.jar')
print('Built unsigned APK; artwork and signing are applied privately.')
