"""E9 preflight: fail closed if known owner infrastructure is in a shareable APK.

This is only a static check. It cannot replace runtime traffic monitoring,
credential review, or two-account Telegram isolation QA.
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path

BANNED = (
    b"100.82.19.36",
    b"192.168.68.66",
    b"DESKTOP-HNP4GVR",
    b"@Troly_music_bsdung_bot",
    b"@thuky_bsdung_bot",
    b"Z:\\MusicAssistant",
    b"C:\\Bots\\MusicAssistant",
)

def scan(apk: Path) -> int:
    if not apk.is_file():
        print("BLOCK: APK not found")
        return 2
    if not zipfile.is_zipfile(apk):
        print("BLOCK: invalid APK archive")
        return 2
    hits: list[tuple[str, str]] = []
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if name.endswith("/"):
                continue
            content = z.read(name)
            for pattern in BANNED:
                if pattern in content or pattern.decode("ascii", "ignore").encode("utf-16le") in content:
                    hits.append((name, pattern.decode("ascii", "replace")))
    if hits:
        for name, pattern in hits:
            print(f"BLOCK: owner identifier present in {name}: {pattern}")
        return 1
    print("PASS: no known owner endpoint/path/bot identifier in APK payload")
    print("CAUTION: this is NOT proof of session privacy or Telegram account isolation")
    return 0

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    sys.exit(scan(args.apk))
