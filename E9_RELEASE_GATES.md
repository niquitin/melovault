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

## Status
E9_PRIVATE_TRANSPORT_STAGED_TESTS_PASS / DO_NOT_DISTRIBUTE
