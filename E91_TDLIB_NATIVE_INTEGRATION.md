# E9.1-C — TDLib Android Native JNI (Internal Engineering)

Status: **TOOLCHAIN_READY / OPENSSL_ARM64_BUILD_RUNNING / JSONJAVA_BRIDGE_COMPILES / NATIVE_APP_NOT_AUTHENTICATING**.

## Provenance and pins
- Official TDLib source: https://github.com/tdlib/td.git
- Pinned revision: `c15d3f5a5de6e3ba5839822c451152e5e18bb700` (TDLib 1.8.68, dated 2026-10-09).
- Isolated checkout `C:\Bots\_deps\tdlib-e91-src`, separate from MeloVault Personal/production.
- Android NDK 28.2.13676358 and CMake 3.22.1 from official Android SDK manager.
- MSYS2 UCRT64 installed via Chocolatey package 20260927.0.0; host packages gperf, GNU Make, Perl, PHP 8.5, GCC and Ninja.
- OpenSSL version **3.5.4**, official upstream source archive (GitHub HEAD 200). No legacy OpenSSL 1.1.1 build.
- Reproducible ARM64-only trial command:
  `C:\tools\msys64\usr\bin\bash.exe /c/Bots/MeloVault-E91-Shareable/tools/e91_build_tdlib_arm64_msys2.sh`
- Script pins TDLib commit, builds OpenSSL static `arm64-v8a` only using an isolated copy of upstream script with the ABI loop narrowed. Then invokes official TDLib `build-tdlib.sh` in JSONJava mode for Android API 23 / arm64-v8a, using C++ static runtime.
- Native build logs remain local ignored `scratch/e91_tdlib_native_arm64_build.log`; may not have completed yet.
- No raw signing key, Telegram app API hash, phone, session secret, OTP or personal Music Assistant API URL is passed to compiler scripts.

## Android source boundary
- Upstream `org.drinkless.tdlib.JsonClient.java` copied unchanged to `app/src/shareable/java`; SHA256 equals official source:
  `0cecc3b8957f680c3706e3b9d062238a2e0eb467195acef797ead10bad630d8b`.
- Original Java file carries upstream copyright notice and Boost Software License v1.0 reference. Before distribution bundle full license notice.
- `TdlibNativeJsonGateway.kt` is Shareable flavor only. It reports unavailable JNI explicitly, uses a single receive consumer, and does **not log** request/response data, OTP or 2FA secrets.
- `gradlew :app:assembleShareableDebug --offline --no-daemon` -> BUILD SUCCESSFUL in 1m34s with no native libraries included yet.
- Debug APK SHA256 `0a74395ad4c5444e2ae33cfeef4ce6375890dd70909b42cef688936f649e1c11`; static known-owner endpoint/path scan PASS (not a proof of user isolation).
- **Do not install/distribute this pre-TDLib debug APK as a working Telegram client.** It has no actual login UI, music access or native .so.

## Mandatory native artifact gates
1. Finish OpenSSL arm64 build and official TDLib JSONJava `tdjni` link. Do not pass based on process activity alone.
2. Audit `libtdjsonjava.so` with `tools/e91_tdlib_elf_security_audit.py`: ELF64 AArch64 identity, SHA256, source pin, known-owner indicator absence.
3. Check upstream license/notice and native dependency linkages, 16-KiB page-size compatibility for modern Android, Android minSdk and runtime crash-free loading.
4. Vendor only vetted artifacts into **Shareable-specific** JNI source directory. NEVER into Personal variant or owner Stable APK.
5. Build real TDLib user session initialization with database encryption and stored session-slot binding. Handle phone/QR/email/OTP/2FA within Android app, not ChatGPT.
6. Two independently authenticated Telegram accounts with isolation of browse, playback, downloads and cache; no owner's backend requests.
7. Only after complete QA and privacy review create a signed Shareable release.
