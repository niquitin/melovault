# MeloVault Private Stable — E9 release operations (draft)

**Scope:** Owner-only Personal app. NOT a publicly shareable APK.
**Current tested RC:** Personal 0.6.40-e9-rc1 / versionCode 60 / Sony.
**Next Stable target:** Personal 0.6.40 / versionCode 61, only if release gates pass.

## Package and signer invariants
- Keep Personal applicationId `com.bsdung.musicassistantcar`.
- Keep v3 signing lineage compatible with the already-installed package. For the immediate private RC this means the existing legacy Android Debug Key whose certificate matches devices. A move to a dedicated long-lived key must be tested as an explicit rotation project, not silently attempted.
- Never ship a private owner backend URL/token in a Shareable APK.
- Never upload `.keystore`, private Telegram sessions, the live DB, `.env` or local backups to the user-facing APK distribution location.
- A 2026-10-14 local DPAPI-protected legacy key copy exists in `C:\Bots\_secrets\melovault-private-signer` behind locked ACL. It is machine/user-profile bound. **This is NOT a verified, portable disaster-recovery key backup**, and the restore test remains open.
- For long-term key recovery, owner must separately preserve a passphrase-encrypted portable backup and recovery passphrase OFFLINE under independent custody. Do not put the passphrase in project source, Antigravity prompt, Telegram, Drive, or this handoff.

## Network behavior
- Personal backend may be accessed through HTTPS or the configured Tailscale IPv4 endpoint while Android verifies active VPN route.
- Plain LAN HTTP must not carry bearer credentials under any circumstances. Do not disable this protection to fix connectivity.
- Media3 HTTP stream, prefetch, pinned download and remote artwork use the authenticated origin guard.
- 30-minute runtime soak and redirect/header review must pass before Stable signoff.

## User-facing recovery when music shows Offline
1. Check Wi-Fi/mobile data or play a previously downloaded item.
2. Open Tailscale, verify it reads "Connected". VPN may show "Starting…" despite Android having a VPN interface.
3. If stuck, close/reopen Tailscale. In Sony QA, a force-stop and reopen (without deleting data) restored Tailnet connectivity.
4. Return to MeloVault and retry library/track. Do NOT add a LAN URL or paste tokens into any other app.
5. If recovery still fails, report the time, route and sanitized logs. Retain all offline files.

## Distribution prerequisites
- Gradle unit tests Personal/Shareable PASS; Sony signature-preserving 59->60 upgrade PASS.
- Full 30-minute v60 Sony FLAC telemetry, backend log correlation, battery and memory profile, VPN-off fail-closed result.
- Make final v61 APK with the compatible signer, not the Shareable flavor; verify package, version, certificate, SHA-256 and APK content guard.
- Place only signed Personal APK, checksum, version manifest, and release notes in a private owner-only distribution directory.
- Never place Shareable into public distribution until independent Telegram user auth and two-account isolation tests pass.
- Record final signed APK's checksum and direct install/rollback steps.
- If failure after install, keep app data, diagnose, and issue a higher-versionCode corrective build signed by the same lineage; do not uninstall or restore an old production database.

## Remaining caveats
- Android media session telemetry cannot prove acoustic playback without microphone/recording.
- The app cannot reliably force a separate VPN client to reconnect by itself.
- Media redirects and existing connections should be considered when reviewing the authenticated data plane. Guard is pre-open; it does not by itself prove every redirect scenario.
- Backing up Android Debug Key with Windows DPAPI on the same machine does not satisfy portable offsite disaster recovery.
