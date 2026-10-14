"""E9 isolated rollback rehearsal: never writes to production database.

Exercises a test-only mutation, recovery from an immutable SQLite snapshot,
integrity validation, and confirms source checksum is unchanged. Temporary
database copies are deleted; a safe manifest without personal data remains.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from contextlib import closing
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
from datetime import datetime, timezone

def sha(path):
    h=hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda: f.read(1024*1024),b""):
            h.update(b)
    return h.hexdigest()

def lock_down(folder: Path):
    if os.name != "nt":
        raise RuntimeError("Windows-specific restrictive ACL preflight required")
    owner=subprocess.check_output(["whoami"],text=True).strip()
    subprocess.run(["icacls",str(folder),"/grant:r",f"{owner}:(OI)(CI)F",
                    "SYSTEM:(OI)(CI)F","Administrators:(OI)(CI)F"],
                   check=True,stdout=subprocess.DEVNULL)
    subprocess.run(["icacls",str(folder),"/inheritance:r"],
                   check=True,stdout=subprocess.DEVNULL)

def main(snapshot: Path, output_root: Path):
    if not snapshot.is_file():
        raise ValueError("Immutable snapshot does not exist")
    digest=sha(snapshot)
    dest=output_root/("e9-isolated-rollback-"+datetime.now().strftime("%Y%m%d-%H%M%S"))
    dest.mkdir(parents=True,exist_ok=False)
    lock_down(dest)
    work=dest/"test_mutated.db"
    recovered=dest/"test_recovered.db"
    outcome={"status":"BLOCKED","snapshot_sha256":digest,
             "production_db_touched":False,"real_restore_executed":False}
    try:
        shutil.copy2(snapshot,work)
        with closing(sqlite3.connect(str(work))) as c:
            if c.execute("PRAGMA integrity_check").fetchone()[0]!="ok":
                raise ValueError("Source snapshot fails integrity verification")
            c.execute("CREATE TABLE __e9_disposable_rehearsal(marker TEXT NOT NULL)")
            c.execute("INSERT INTO __e9_disposable_rehearsal VALUES ('mutated in isolated clone')")
            c.commit()
        with closing(sqlite3.connect(str(work))) as c:
            count=c.execute("SELECT count(*) FROM __e9_disposable_rehearsal").fetchone()[0]
            if count!=1:
                raise AssertionError("Mutation rehearsal did not persist")
        # Simulate a recovery into an isolated destination, NEVER the live db.
        shutil.copy2(snapshot,recovered)
        with closing(sqlite3.connect("file:"+recovered.resolve().as_posix()+"?mode=ro",uri=True)) as c:
            if c.execute("PRAGMA integrity_check").fetchone()[0]!="ok":
                raise ValueError("Recovered db fails integrity verification")
            has_marker=c.execute("SELECT count(*) FROM sqlite_master WHERE name='__e9_disposable_rehearsal'").fetchone()[0]
        if has_marker!=0 or sha(recovered)!=digest or sha(snapshot)!=digest:
            raise AssertionError("Restored clone differs from immutable snapshot")
        outcome.update(status="PASS_ISOLATED_RECOVERY",
                       mutation_confined_to_test_clone=True,
                       recovered_integrity="ok",
                       source_checksum_unchanged=True)
    finally:
        work.unlink(missing_ok=True)
        recovered.unlink(missing_ok=True)
        (dest/"rehearsal_manifest.json").write_text(json.dumps(outcome,indent=2)+"\n",encoding="utf-8")
    print("ISOLATED_ROLLBACK_REHEARSAL="+outcome["status"])
    print("REPORT="+str(dest/"rehearsal_manifest.json"))
    return 0 if outcome["status"]=="PASS_ISOLATED_RECOVERY" else 1

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--snapshot",type=Path,required=True)
    p.add_argument("--out-root",type=Path,required=True)
    args=p.parse_args()
    raise SystemExit(main(args.snapshot,args.out_root))
