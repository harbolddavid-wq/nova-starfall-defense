#!/usr/bin/env python3
"""Build the launcher and a separate, synthetic widget provider for CI only."""
from pathlib import Path
import os
import shutil
import subprocess
import zipfile

root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ["ANDROID_HOME"])
tools=sdk/"build-tools/35.0.0"
android=sdk/"platforms/android-35/android.jar"
fixture=root/"tests/widget-fixture"
build=root/"build/widget-fixture"
shutil.rmtree(build,ignore_errors=True)
for folder in ("classes","dex"):
    (build/folder).mkdir(parents=True,exist_ok=True)

def run(*args):
    subprocess.run([str(x) for x in args],cwd=root,check=True)

run("python3",root/"scripts/build-android.py")
run("javac","--release","8","-cp",android,"-d",build/"classes",*sorted((fixture/"src").rglob("*.java")))
run(tools/"aapt2","compile","--dir",fixture/"res","-o",build/"resources.zip")
run(tools/"aapt2","link","-o",build/"base.apk","--manifest",fixture/"AndroidManifest.xml","-I",android,build/"resources.zip")
run("java","-cp",tools/"lib/d8.jar","com.android.tools.r8.D8","--release","--min-api","26","--lib",android,"--output",build/"dex",*sorted((build/"classes").rglob("*.class")))
with zipfile.ZipFile(build/"base.apk","a",zipfile.ZIP_DEFLATED) as apk:
    for dex in sorted((build/"dex").glob("*.dex")): apk.write(dex,dex.name)
run(tools/"zipalign","-f","-p","4",build/"base.apk",build/"fixture-unsigned.apk")
run("keytool","-genkeypair","-keystore",build/"ci-only.jks","-storepass","androidtest","-keypass","androidtest","-alias","ci","-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN=CI Test Only","-noprompt")
for name,source in (("launcher",root/"output/starlight-unsigned.apk"),("fixture",build/"fixture-unsigned.apk")):
    run(tools/"apksigner","sign","--ks",build/"ci-only.jks","--ks-pass","pass:androidtest","--key-pass","pass:androidtest","--out",build/(name+".apk"),source)
    run(tools/"apksigner","verify",build/(name+".apk"))
print("Built two CI-only APKs with an ephemeral test key.")
