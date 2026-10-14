"""Audit official TDLib JSONJava ARM64 native build before packaging.

Checks ELF structure, architecture, file checksum, source pin and absence of
known owner-specific indicators. No credentials or session data are accessed.
A PASS is a static baseline, not proof of runtime/account isolation.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import sys

SOURCE = Path("C:/Bots/_deps/tdlib-e91-src")
LIB = SOURCE / "example/android/tdlib/libs/arm64-v8a/libtdjsonjava.so"
PIN = "c15d3f5a5de6e3ba5839822c451152e5e18bb700"
BAD = [
    "100.82.19.36", "192.168.68.66", "DESKTOP-HNP4GVR",
    "@Troly_music_bsdung_bot", "@thuky_bsdung_bot",
    "Z:\\MusicAssistant", "C:\\Bots\\MusicAssistant",
]

def main() -> int:
    result = {"status": "NOT_BUILT", "tdlib_source_commit": PIN,
              "native_interface": "JSONJava", "abi": "arm64-v8a",
              "ready_for_distribution": False}
    if not LIB.is_file():
        print(json.dumps(result, indent=2))
        return 2
    original = subprocess.check_output(
        ["git", "-C", str(SOURCE), "rev-parse", "HEAD"], text=True
    ).strip()
    binary = LIB.read_bytes()
    embedded = [
        marker for marker in BAD
        if marker.encode() in binary or marker.encode("utf-16-le") in binary
    ]
    # ELF header e_ident[4] = 2 (ELF64), e_ident[5] = 1 (little endian)
    # ELF64 e_machine @ offset 18 = 183 (EM_AARCH64).
    arm64 = (len(binary) >= 64 and binary[:4] == b"\x7fELF"
             and binary[4:6] == b"\x02\x01"
             and int.from_bytes(binary[18:20], "little") == 183)
    passing = original == PIN and arm64 and len(binary) > 1024 * 1024 and not embedded
    result.update(
        status="PASS_STATIC_NATIVE_AUDIT" if passing else "FAIL_STATIC_NATIVE_AUDIT",
        source_pin_match=(original == PIN),
        elf_arm64=arm64,
        native_size_bytes=len(binary),
        native_sha256=hashlib.sha256(binary).hexdigest(),
        known_owner_markers_absent=(len(embedded) == 0),
        marker_count=len(embedded),
        ready_for_distribution=False,
    )
    print(json.dumps(result, indent=2))
    return 0 if passing else 1

if __name__ == "__main__":
    sys.exit(main())
