# MeloVault Personal v0.6.40 (build 61) — Signed Stable Candidate

**Status:** QA VERIFIED / **NOT YET RELEASED**.
**Build date:** 2026-10-14, Vietnam.
**Package:** `com.bsdung.musicassistantcar`, versionCode `61`, versionName `0.6.40`.
**Distribution:** owner-only. **NEVER share this APK with friends**: it contains owner infrastructure configuration, although no embedded bearer token.
**Signing:** existing unique Android Debug Key for in-place upgrade compatibility. Not yet backed up portably under independent custody.

## What's included
- Media3 streaming and cache/prefetch with HTTP origin/VPN transport validation before opening protected upstream requests.
- Non-HTTPS LAN fallback blocked for credential-bearing requests.
- Safe pinned downloads and artwork guard.
- Existing library, playlists, Telegram bot pairing and offline storage remain functional on the owner's devices; public independent Telegram user client remains future work.
- Personal user experience is unchanged from the build 60 E9 QA branch, except final candidate versionCode/name.

## Tested
- Android Personal unit suite: 800 total, 0 failed, 1 skipped.
- Android Shareable suite: 800 total, 0 failed, 21 skipped (server-backed tests not applicable).
- Sony Xperia 5 II versionCode 60 -> 61 `adb install -r` successful; no uninstall or clear. Cache and external app files' sizes unchanged immediately after install.
- Existing account session still showed online status; 14 offline songs / 485.2 MB preserved.
- Cached FLAC and uncached MP3: Media3 ultimately PLAYING, no playback exception; uncached MP3 startup initially BUFFERING, then backend returned HTTP 200/206 and track advanced.
- Tailscale remains connected, local playback paused at QA conclusion.
- v60 30-minute FLAC soak: PASS (31/31 PLAYING, 0 errors, backend 12 stream 206 and 59 API 200).
- Sony build 60 VPN-off protected-media guard test PASSED. This security test was not rerun on build 61 because only app version changed.

## Candidate artifact (do NOT copy to shared/public distribution)
Local: `C:\Bots\MeloVault-E9-Release\dist\qa\20261014-e9-stable-candidate-b61\MeloVault-PERSONAL-v0.6.40-build61-SIGNED-QA-DO-NOT-SHARE.apk`
SHA-256: `316563152d66f20ed13f3f1bb7bd7f199eb835b6a82336d020e72c79e536a314`
Signer certificate SHA-256: `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`.

## Release blockers
1. Verified **portable offline** signing-key recovery (encrypted copy and independently held passphrase). The local DPAPI encrypted copy has not had recovery verified and is tied to the Lenovo profile; it is not disaster recovery.
2. Confirm final owner-only distribution permissions before copying any APK to Google Drive. Never place the Private APK alongside Shareable.
3. Assess UX of cold/uncached playback startup and artwork timeout under slower backend conditions; existing Sony test recovered automatically, not a continuous failure.
4. Keep forward-only rollback with higher versionCode and same signing lineage, no uninstall or database overwrite.
5. Shareable Telegram independent user login and multi-account isolation are UNIMPLEMENTED.

## What's unchanged
M3 Stable installation, FYT installation, Music Assistant backend production, Telegram Secretary, user offline data and personal media library.
