# MeloVault E9 — Release Gates (WORK IN PROGRESS)

## Goal
Keep existing MeloVault Personal service and users intact while preparing a separate, non-distributable Shareable preview. No Google Play required.

## Implemented
- Isolated Android worktree/branch. Production backend and installed Personal Stable are unchanged.
- Gradle distribution flavors:
  - `personal`: original application ID, original backend wiring retained temporarily, old signing/update compatibility MUST be confirmed.
  - `shareable`: distinct app ID `app.melovault.player`, no embedded owner endpoints/token; remote categories hidden until account-owned providers are implemented.
- Debug and release BuildConfig token field blank for both flavors.
- Distribution boundary unit test and byte-level APK privacy scan.

## Hard blocking conditions BEFORE sharing ANY APK
1. No shared APK may contain owner IPs, bots, tokens, library IDs, Drive links, Google credentials or personal config.
2. Shareable must implement per-user Telegram login (TDLib or equivalent authenticated user client), per-account indexing/playback, sign-out, encrypted local sessions, isolated offline files, and account-switch hygiene. **NOT implemented**.
3. Test two mutually independent Telegram accounts, each seeing ONLY their own permitted music; verify logout/revoke and no traffic to owner backend.
4. Signed release APK must be backed by durable signing key, stored outside worktree and synced Drive, with backup/rotation plan. Current RC is signed by Android Debug Key.
5. Personal release must not break in-place updates for M3/FYT/Sony. Compare installed signing certs first; never uninstall to force key migration.
6. Owner credentials must not traverse unencrypted HTTP LAN. Stage a transport fix and validate LAN/Tailscale behavior without touching backend production.
7. Rollback runbook must avoid destructive git reset / overwriting live music.db; immutable backups, non-destructive restore rehearsal.
8. Run per-variant unit/instrumentation smoke tests, account isolation checks, APK manifest/signature/content scan.
9. Tag, checksum, release notes, install/rollback instructions; distribute only verified signed outputs.
10. Google Drive personal login can be a later scope, separate from private and Telegram Shareable.

## E9.1 Private hardening checkpoint (staged, not released)
- `CarApiClient` will reject HTTP LAN and only select HTTPS or tailnet HTTP endpoints when the **active VPN** has a verified route covering the requested host. URL redirect following disabled for credential-bearing JSON requests.
- Personal flavor network-security config rejects cleartext by default, with a host-scoped tailnet exception; Shareable continues to disallow cleartext.
- `TrustedEndpointPolicyTest` checks LAN denied even with VPN, HTTP tailnet denied without a verified VPN route, HTTPS allowed.
- Local backup via SQLite read-only source snapshot: validated, with restrictive Windows ACL. Full restore has not been exercised.
- Signature inventory: existing Android Debug Key cert matches old M3 stable, E8 RC, and physically installed Sony APK. A **debug-key-signed QA candidate** with that cert exists only under ignored local `dist/qa`, explicitly marked DO-NOT-INSTALL.
- After final staged network-security changes: Personal 798 tests, 0 failed, 1 skipped; Shareable 798 tests, 0 failed, 21 skipped; Build SUCCESSFUL.
- Static Shareable APK guard: PASS for known owner identifiers. Account-to-account leak test still required.
- Route-policy runtime compatibility, credential-free token distribution, and in-place upgrade still need physical QA.

## E9.2 Sony versionCode 60 + full credential-bearing media guard (2026-10-14)
- Personal QA versionName 0.6.40-e9-rc1, versionCode 60. Successfully installed as an in-place upgrade on Sony via `adb install -r`, same signing certificate; existing offline library retained at 14 tracks / 485.2 MB.
- `AuthenticatedTransportGuard` now verifies the requested exact origin is configured and its transport is trusted. Applied to Media3 cache upstream connections and prefetch, pinned offline downloading, and artwork requests; HTTP LAN disabled on all credential-bearing paths tested.
- Existing localhost network/cache tests explicitly inject loopback-only permission; production/default factories ALWAYS use the strict origin/VPN rule.
- Personal: 800 tests, 0 failed, 1 skipped; Shareable: 800 tests, 0 failed, 21 skipped; Gradle BUILD SUCCESSFUL.
- Shareable APK static scan still PASS for known owner IPs/bots/paths. Shareable per-account Telegram login remains UNIMPLEMENTED; DO NOT DISTRIBUTE.
- Sony with Tailscale connected: FLAC stream HTTP 206, Media3 PLAYING, authenticated browse. With Tailscale VPN off: uncached track produced `E9: Protected media transport unavailable`; no authenticated LAN requests observed in backend logs. Wi-Fi off: offline WAV progressed 9 seconds without error.
- Final Sony state: Wi-Fi ON, Tailscale Connected, MeloVault Online and PAUSED, 14 downloaded tracks.
- Isolated SQLite rollback recovery: PASS; production DB remains untouched. This is NOT a full production disaster-recovery rehearsal.
- **Release blockers:** long-lived signing key custody/portable encrypted backup, documented recovery for Tailscale Starting state, final RC regression and versioned release artifact/release notes. Current signed APK is QA only, not final Stable.

## Status
E9_V60_SONY_INPLACE_SECURITY_QA_PASS / NOT_RELEASED
