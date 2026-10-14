"""Non-destructive SQLite snapshot for E9 release/rollback preflight.

The live database is opened read-only; no schema changes, no restore, no
process restart, no copy of .env or Telegram credentials. Keep backups local.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sqlite3
import subprocess
from datetime import datetime, timezone
from pathlib import Path

def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def run(live: Path, root: Path) -> int:
    if not live.is_file():
        print("BLOCK: source database missing")
        return 2
    root.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    destination = root / ("e9-preflight-" + timestamp)
    destination.mkdir(exist_ok=False)
    # The source folder may grant read access to all local users. Restrict
    # the NEW backup directory before placing any music/library data in it.
    if os.name != "nt":
        raise RuntimeError("E9 backup currently requires Windows ACL verification")
    owner = subprocess.check_output(["whoami"], text=True).strip()
    subprocess.run(["icacls", str(destination), "/grant:r",
                    f"{owner}:(OI)(CI)F", "SYSTEM:(OI)(CI)F",
                    "Administrators:(OI)(CI)F"], check=True,
                   stdout=subprocess.DEVNULL)
    subprocess.run(["icacls", str(destination), "/inheritance:r"],
                   check=True, stdout=subprocess.DEVNULL)
    backup_path = destination / "music.db"
    db_uri = "file:" + live.resolve().as_posix() + "?mode=ro"
    with sqlite3.connect(db_uri, uri=True, timeout=30) as source:
        with sqlite3.connect(str(backup_path), timeout=30) as target:
            source.backup(target, pages=1000, sleep=0.1)
            result = target.execute("PRAGMA integrity_check").fetchone()[0]
    if result.lower() != "ok":
        print("BLOCK: snapshot integrity check did not pass")
        return 1
    manifest = {
        "status": "SNAPSHOT_INTEGRITY_OK",
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "bytes": backup_path.stat().st_size,
        "sha256": sha256(backup_path),
        "source_mutated": False,
        "includes_credentials_file": False,
        "restore_attempted": False,
        "backend_stopped": False,
    }
    (destination / "manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    print("PASS: read-only SQLite backup API, verified snapshot integrity")
    print("LOCAL_BACKUP=" + str(backup_path))
    print("SNAPSHOT_SHA256=" + manifest["sha256"])
    return 0

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", type=Path, required=True)
    parser.add_argument("--out-root", type=Path, required=True)
    arguments = parser.parse_args()
    raise SystemExit(run(arguments.db, arguments.out_root))
