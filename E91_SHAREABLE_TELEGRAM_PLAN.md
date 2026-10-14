# MeloVault E9.1 — Shareable Telegram owned-account roadmap

**Worktree:** `C:\Bots\MeloVault-E91-Shareable`
**Branch:** `feature/e91-shareable-telegram` forked from `stable/private-v0.6.40-b61`
**Status:** FOUNDATION / NOT LOGIN-CAPABLE / DO NOT DISTRIBUTE
**Private owner Stable:** v0.6.40 build 61 unchanged.

## Product contract
Each APK installer has an independent Telegram user account; no connection to the original owner's Telegram sessions, bot, playlist, library or backend. Shareable build uses Android package `app.melovault.player`, `PERSONAL_BACKEND_ENABLED=false`, `CAR_API_PRIMARY/SECONDARY/TOKEN=""`. Never embed owner Google Drive or Telegram credentials. User-owned Google Drive OAuth is a separate future implementation, not available now.

## Native Telegram solution
- Use upstream official **TDLib** (https://github.com/tdlib/td) built for Android with JNI Java interface and vetted version/commit. Do **not** simulate user sign-in with Telegram Bot API; it cannot access an individual's private cloud chats/messages as their own user.
- Official Android build steps: https://github.com/tdlib/td/blob/master/example/android/README.md
- Android SDK available; NDK 28.2.13676358 and CMake 3.22.1 installed in C:\Android\Sdk. Official upstream TDLib 1.8.68 source independently cloned to C:\Bots\_deps\tdlib-e91-src, pinned at c15d3f5a5de6e3ba5839822c451152e5e18bb700 (2026-10-09). Windows host build is **not yet ready**: gperf, php, GNU make, and a complete Bash/MSYS2 toolchain are missing. No JNI .so is packaged, no user authorized.
- Developer Telegram API ID/hash from my.telegram.org/apps were entered in a LOCAL Lenovo GUI, saved as Windows DPAPI-encrypted `C:\Bots\_secrets\melovault-e91-telegram-api\telegram_api.dpapi` with restrictive ACL, and verified by a decrypt-in-RAM check. Actual ID/hash were never printed, logged, or uploaded to Drive. The binary is not linked yet. For eventual public APKs, app-wide Telegram API ID/hash cannot be assumed secret against APK reverse engineering; do not embed any user session, bot token, or private account credential.
- Authentication states: wait TDLib parameters; phone/QR where supported by installed TDLib; code; 2FA password if requested; authorizationStateReady; getMe numeric userId. Never treat typed phone number or a matching username as proof of identity.
- **Initial TDLib authorization occurs before getMe() can provide the user ID.** Create an unpredictable pending session slot under noBackupFilesDir first, then bind the slot to the verified user ID only after authorizationStateReady + getMe(). Resume a session from its slot and reverify getMe(); never infer identity from a phone number or a directory. Encrypt the slot-to-user registry and TDLib DB keys with Android Keystore. Offline files/streaming caches are separate per verified Telegram user. Never allow path escaping or migration while an old TDLib client remains open.
- Login UI should display real TDLib statuses and errors; no 'connected' state until TDLib confirms an authenticated identity. Do not retain OTP, 2FA password, access code in prefs/logs/Drive.
- Import Telegram audio/document messages only from chats/channels the user can access, honoring media metadata, MIME/extension and downloads. No owner backend proxy or mixed cloud caching.
- Logout/switch must close previous native client, invalidate its async callbacks, stop ongoing playback/download, clear active index and never show another account's content. Actual user-controlled delete/wipe is separate from logout.

## Implemented E9.1-A (foundation)
- `TelegramUserId` and `TelegramAccountStoragePolicy`: opaque account-scoped session, download and streaming cache folder IDs; canonical path containment guard.
- `TelegramAccountLibrary`: authenticated-account gated in-memory catalog, account-scoped item identity (chatId/messageId), switch/logout access checks.
- `TelegramSessionEpoch`: rejects stale asynchronous callbacks from previous native clients after switching users.
- E9.1-B: `TelegramAuthorizationFlow` models official auth steps, rejects premature READY and requires native getMe for the current epoch; five JUnit tests for stale callbacks, logout, two-user transitions, error and pre-authorization. **No network/native login yet.**
- `tools/e91_telegram_app_config_gui.py`: local masked-entry window with DPAPI encryption, input validation and local check. **No raw developer API secrets in Git/Drive.**
- `tools/e91_tdlib_native_preflight.py`: pinned source/NDK/CMake/host-tool checks; reports toolchain blocked rather than fabricating a working TDLib APK.
- JUnit two-account isolation tests, traversal rejection, unauthenticated access rejection, epoch isolation.
- These are a non-network foundation. They do **not** yet implement Telegram sign-in or playback. They are not a security proof for code not built yet.

## Gates before any Shareable APK is issued
1. Official/vetted TDLib Android native binary packaged for arm64-v8a and where required armeabi-v7a/x86_64; hashes and upstream revision in build manifest; no Telegram credentials hardcoded.
2. End-to-end user login with real Telegram account, OTP and 2FA handling in native app UI.
3. Import/listen/seek/download FLAC/MP3/M4A audio from account's accessible chats/channels, replay background via Media3 and steering controls.
4. Two independently owned Telegram test accounts: A's music must not be visible/playable/downloadable under B, including same message IDs, logout/relogin and offline mode. Ensure cache file scope and async callback safety.
5. APK static and runtime privacy audit: no owner phone, token, Google Drive library, local backend URL, bot ID, account data. Test clean installation on non-owner Android phone.
6. Separate future user-owned Google Drive OAuth integration and account switch/isolation with **no owner Drive permissions**.

## Non-negotiable invariants
No changes to owner Personal Stable tag or signed APK, M3, FYT, Sony installed Personal APK, production backend, Telegram Secretary, or shared production Telegram bot.
