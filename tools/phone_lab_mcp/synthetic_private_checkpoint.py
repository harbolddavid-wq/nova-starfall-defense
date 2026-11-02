"""Synthetic-only private checkpoint proof; not enabled for real app data."""
import hashlib
import os
import re
import subprocess
import sys
from pathlib import Path
from checkpoint_crypto import decode_key
from checkpoint_service import CheckpointService
from private_store import PrivateStore

PACKAGE = "com.novasky.phonelab.fixture"
REPO = "harbolddavid-wq/nova-phone-lab-backups-"
APP_DIR = "/data/user/0/" + PACKAGE
MAX_ARCHIVE = 256 * 1024

def adb(*args, input_bytes=None):
    return subprocess.run(["adb", *args], input=input_bytes, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, check=True, timeout=30).stdout

def main():
    if len(sys.argv) != 3 or sys.argv[1] not in ("save", "restore"):
        raise SystemExit("Expected save|restore APK_PATH")
    mode, apk = sys.argv[1:]
    run_id = os.environ.get("GITHUB_RUN_ID", "")
    if not re.fullmatch(r"[0-9]{1,20}", run_id):
        raise RuntimeError("Missing or invalid run ID")
    digest = hashlib.sha256(Path(apk).read_bytes()).hexdigest()
    key = decode_key(os.environ.get("PHONE_LAB_CHECKPOINT_KEY", ""))
    store = PrivateStore(REPO, os.environ.get("PHONE_LAB_BACKUP_TOKEN", ""),
                         "checkpoints/synthetic-" + run_id + ".npl")
    service = CheckpointService(store, key, PACKAGE, digest)
    previous = service.read()
    if mode == "save":
        if previous is not None:
            raise RuntimeError("This test run already has a checkpoint")
        adb("shell", "am", "force-stop", PACKAGE)
        archive = adb("exec-out", "run-as", PACKAGE, "tar", "-C", APP_DIR, "-cf", "-", "shared_prefs")
        if not archive or len(archive) > MAX_ARCHIVE:
            raise RuntimeError("Synthetic checkpoint size invalid")
        service.write(archive)
        print("Synthetic encrypted checkpoint written and read-back verified.")
    else:
        if previous is None or len(previous) > MAX_ARCHIVE:
            raise RuntimeError("Synthetic checkpoint missing or too large")
        adb("shell", "am", "force-stop", PACKAGE)
        adb("shell", "run-as", PACKAGE, "tar", "-C", APP_DIR, "-xf", "-", input_bytes=previous)
        print("Synthetic checkpoint authenticated and restored.")

if __name__ == "__main__":
    main()
