# MeloVault E9 — Non-destructive release and rollback runbook

**State:** DRAFT / not executed as a production rollback.

## Hard prohibitions
- Never use `git reset --hard` or recursively stop all Python/music processes as a rollback.
- Never restore an old `music.db` over the current live production database.
- Never DROP the pairing tables: this would revoke or break users with E8 device credentials.
- Never uninstall existing MeloVault Personal builds merely to change APK signing certificates.
- Never wipe device cache/offline files, Telegram Secretary data, or the Google Drive library.
- Never upload raw SQLite snapshots, .env, keystores, user Telegram sessions or encrypted auth files to the public Shareable distribution folder.

## Preflight before any deployment (non-destructive)
1. Confirm target backend revision and identify *only* the MusicAssistant Car API service/task.
2. Run `tools/e9_sqlite_backup_preflight.py --db <music.db> --out-root <local-protected-backup-directory>` on Lenovo. This uses SQLite read-only source and backup API.
3. Verify backup with `PRAGMA integrity_check` and SHA-256; record path and timestamp. Keep snapshot local in a protected folder.
4. Verify APK signature digest matches the installed app on target devices. The tested v0.6.39 M3, E8 RC, and Sony APK certificates match the local debug keystore. This is an **existing-debug-key compatibility fact**, NOT a final long-lived signing strategy.
5. Ensure a new Android release increments versionCode. For non-debuggable APKs, older versions cannot generally be reinstalled as a downgrade.
6. Stage auth/transport with the same M3/Sony/FYT connectivity configuration before rollout. Fail if private credentials can use unencrypted LAN.
7. Record package names, installed app metadata, and checksum without extracting tokens or account data.

## Safe release rollback (if app has trouble)
- Prefer server-side feature toggle / forward-compatible application update with a *higher* versionCode and the **same accepted signing lineage**.
- Roll forward with a tested corrective APK; do not assume `adb install -r` of an older version will work.
- If a backend code change must be reverted, deploy a known tested compatible build via an isolated release branch, after capturing current live data; do not overwrite the database.
- Database recovery is a dedicated disaster-recovery operation, never the default rollback: stop only the impacted service, capture current DB with WAL, prepare a point-in-time migration or merge, rehearse on a copy, and obtain approval before changing production.

## Rehearsal status
- E9 SQLite snapshot backup: PASS, source read only. This does **not** demonstrate successful restoration.
- APK in-place update test: NOT DONE.
- Signing-key disaster-recovery backup: NOT DONE (requires an owner-held encrypted/offline backup strategy).
- Production rollback rehearsal: NOT DONE.
- Transport runtime test on M3/FYT/Sony: NOT DONE.

## Stop conditions
Any signer mismatch, missing/invalid backup, uncertified VPN/HTTPS route, server 401, inaccessible offline tracks, lost playlists, or unexpected traffic toward owner resources from Shareable means **DO NOT RELEASE**.
