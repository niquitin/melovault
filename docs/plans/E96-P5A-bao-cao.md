# E96-P5A — PHYSICAL QA READINESS — BÁO CÁO (Sonnet 5.5)

**Kết luận: P5-A HOÀN TẤT PHẦN CODE + JVM + PREFLIGHT CHỈ-ĐỌC. KHÔNG cài thiết bị, KHÔNG Telegram thật, KHÔNG sang P5-B.** Trạng thái: **READY FOR OWNER DECISIONS (WAIT_OWNER_APPROVAL)** — xem `E96-P5B-APPROVAL-CHECKLIST.md`.

## 1. Git
| Mục | Giá trị |
|---|---|
| Baseline P4 | `8272cc89c7b6a408f713153d65e5fba52c50b44b` (đúng như yêu cầu; worktree P4 sạch, không bị đụng) |
| Nhánh / worktree P5 | `dot/E96-P5-PHYSICAL-GATE` · `D:\Bots\MeloVault-E96-P5-Physical-QA` |
| **Commit code P5-A** | **`8f751064effb9265b7409ca0444b2c4db1c70f33`** |
| Commit báo cáo | commit docs ngay sau (chỉ thêm `docs/plans/E96-P5A-*`) |
| Kiểm | `git diff --check` sạch; không có agent khác đang sửa; E95, P0–P4, Tiny, Private Stable, backend: **không sửa** |
| Bundle chuyển mã sang Tiny (chưa chuyển) | `E96-P5A-evidence\E96-P5A-8f75106.bundle` · 12 374 844 B · SHA-256 `ddf38265605bb2496d59395ba14257b3942faf506a86f430131f8c035ec3b789` · `git bundle verify`: "complete history" |

Commit chạy bằng `-c user.name="Claude Code"` (Ni chưa có git identity; không sửa git config).

## 2. P5A-01 — Cầu nối production, **tắt mặc định**
- `SyncServiceProvider` (mới) là **nơi duy nhất** dựng `TdlibPlaylistSyncService` (dùng đúng `ShareableTdlibOwner` có sẵn: không client/listener/receive loop mới). Khi `TelegramPlaylistFlags.SYNC_ENABLED=false` (giữ nguyên **false**), `actions()` trả `DisabledSyncActions` **trước khi** hỏi owner, host hay file consent → không có đối tượng TDLib nào được chạm tới. V1-only playlist → cũng vô hiệu (fail-closed).
- Một service cho cả tiến trình → **backoff không bị bỏ qua** khi đóng/mở lại màn hình Cài đặt (test f03).
- `SyncRunLog` (mới): lần chạy gần nhất mỗi tài khoản, dùng chung cho Cài đặt và dòng trạng thái Playlist của màn chính; xóa khi đăng xuất. Màn chính đọc lại trạng thái khi `onResume` (đọc file cục bộ, không mạng).
- Màn Cài đặt: `controller.close()` khi thoát → `service.cancel()` + `detach()` (nhả sink TDLib); không ảnh hưởng UI/playback (test f01, f02).
- Task Gradle `reportTelegramConfig` in **chỉ `true|false`**, không in ID/hash.
- Không WorkManager, không boot receiver, không service mới, không timer, không `SyncTrigger` ở production (test a07, a08).
- Thay đổi test P3 (công khai): `TdlibChannelConsentTest.c24` (đổi tên `…ExceptTheGuardedProvider`): allowlist mở rộng **đúng một file** `SyncServiceProvider.kt`; c24 vẫn cấm mọi file production khác chạm service; test a07 siết thêm (service chỉ được dựng ở 1 nơi, sau dòng kiểm công tắc).
- Chưa làm (cố ý, thuộc P5-B): đổi `SYNC_ENABLED`, đường bật cờ lúc build, `SyncTrigger`/tự động theo Save.

## 3. P5A-03 — Test
Lệnh: `.\gradlew.bat --offline --no-daemon :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --rerun-tasks --console=plain` → **BUILD SUCCESSFUL** (4m 25s).

| Biến thể | tests | skipped | failures | errors |
|---|---|---|---|---|
| Debug | **1822** | 21 | 0 | 0 |
| Release | **1822** | 21 | 0 | 0 |

= 1776 (P4) + **46 test mới**; 21 skipped là các test cũ có sẵn (`BackgroundOfflinePlaylistSyncTest`, `SmartOfflinePlaylistSyncTest`, `PlaybackStabilitySoakTest`); **0 test mới bị skip**. P0 58, P1 81, P2 94, P3 126, P4 87 giữ PASS. XML: `E96-P5A-evidence\test-results-debug|release`.

| Nhóm (đề bài) | Test mới | Nội dung chính |
|---|---|---|
| A flag OFF | `P5ReadinessInertnessTest` ×10 | provider không hỏi owner/host/store; mọi lệnh cổng bị từ chối; **0 request, 0 client mới** khi mở/Save/đổi tài khoản; mặc định production = Disabled; không boot receiver/service/worker mới; đăng xuất xoá báo cáo; "Đã đồng bộ" không thể xuất hiện khi flag tắt |
| B flag ON (cổng test) | `P5ReadinessEnabledFlowTest` b01–b07 ×7 | chưa consent ⇒ từ chối; 3 quyền độc lập tại biên service; 2 kênh ⇒ Ambiguous, không tạo/gắn; chat list không tải xong ⇒ Incomplete, không tạo; đăng xuất/A→B→A ⇒ từ chối rồi epoch mới; luồng dò→tạo(đúng 1)→gắn→đồng bộ |
| C chronology | c01–c10 ×10 | >100 tin (phân trang), trang ngắn, **>10 000 tin phát lại qua nhiều lần chạy** (CATCHING_UP, chỉ lần cuối mới SYNCED), kiểm chiều xuôi lỗi/ sai định dạng ⇒ không OK, giao thức tương lai/ checksum hỏng ⇒ RECOVERY & giữ nguyên dữ liệu, mất ACK ⇒ không gửi 2 lần, 429 ⇒ "Thử lại sau", timeout không lưu ⇒ thử lại không trùng, phản hồi tạm không ACK |
| D local Save | `P5LocalSaveAndGatesTest` d01–d05 ×5 | Lưu/Bỏ lưu bền qua khởi động lại khi cloud OFF; nút còn dùng được; outbox đầy từ chối rõ ràng; bấm nhanh gộp đúng số lần ghi thật (5 lần bấm → 1 ghi, 6 lần → 2 ghi, không ghi trùng); nhập V1 hoãn vẫn thấy danh sách |
| E cổng cài | e01–e11 ×11 | `QaInstallPreflight` (đặc tả): sai chữ ký/ package/ hạ version/ ABI/ thiếu Telegram config/ sai serial/ máy chưa duyệt/ model lệch/ vân tay hỏng ⇒ **HALT**; chưa có duyệt của chủ ⇒ không bao giờ chạy; lệnh duy nhất có thể sinh ra: `adb -s <serial> install -r <apk>`; không có uninstall/clear/-d trong mã |
| F vòng đời | f01–f03 ×3 (trong Enabled) | nhả sink khi đóng; UI & playback không bị ảnh hưởng; backoff sống qua mở lại màn hình |

**Giới hạn:** JVM + TDLib giả ≠ TDLib thật ≠ thiết bị thật. `getChatHistory` thật, `updateMessageSendSucceeded` thật, FLOOD_WAIT thật, Keystore: **chưa chứng minh** — là cổng P5-B.

## 4. P5A-02 — Build / ký / cấu hình
| Cổng | Kết quả | Bằng chứng |
|---|---|---|
| Build Ni (Debug+Release) từ `8f75106` | **PASS (biên dịch)**, **không dùng làm bản giao thiết bị** | debug `app-shareable-debug.apk` 33 329 228 B sha256 `171f627c8ebfa4abc0648eb8d160276af50b118c1c9908ad773c4044da8b0213`; release 30 603 862 B sha256 `f42768fca4240f3876e878380671acb1b5b73e9d891ef853b894612c5eb7d9c5`; `app.melovault.player` vc109 `0.6.88-beta`, arm64-v8a. Chỉ nằm trong thư mục build của worktree, **không** đưa vào handoff |
| Chữ ký Ni vs. Beta đang cài | **FAIL** | APK Ni ký debug key của Ni: cert `a9b8cb6b…13db280`; Beta trên Sony **và** Shanling: cert `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` |
| Máy nào build được đúng chữ ký | **Tiny — dự báo PASS (chưa build thử)** | `build.gradle` ký shareable bằng `signingConfigs.debug` (khóa debug **của máy build**). Keystore debug của Tiny alias `androiddebugkey` có SHA-256 `EE:CD:3D:F7:…:FC:E8` = `eecd3df7…fce8` = cert đang cài trên Sony & Shanling. Chỉ đọc vân tay (mật khẩu debug chuẩn công khai); không đụng khóa/secret |
| Telegram config | Ni: **false** (`reportTelegramConfig` → `telegram.config.valid=false`). Tiny: **CHƯA XÁC MINH** | Tiny có `local.properties` (chỉ kiểm tồn tại, không mở). Cách xác minh không lộ giá trị: chạy `reportTelegramConfig` trên cây P5 ở Tiny (bước trong checklist) |
| versionCode | Giữ **109**, không đổi | Cài `-r` cùng versionCode được phép; giữ 109 còn để **rollback về Beta cũ không cần hạ version** (xem checklist). Phương án nâng 110 chỉ là đề xuất, cần chủ duyệt |
| Chuyển mã sang Tiny | **KHẢ THI (chưa thực hiện)** | git 2.55 trên Tiny, ổ C trống 97,3 GB, bundle đã kiểm; cây Tiny hiện có là E93 `e278649` — **không** dùng/sửa; cần clone mới `C:\Bots\MeloVault-E96-P5-Physical-QA` |
| Gắn `local.properties` cho cây mới | **CẦN CHỦ DUYỆT** | Quy trình sẵn có (`tools/e91_prepare_local_telegram_config.py`, giải mã DPAPI cục bộ) đang trỏ cứng `C:\Bots\MeloVault-E91-Shareable`; dùng cho cây mới cần phê duyệt cách làm. Không copy/in giá trị ở bất kỳ đâu |

## 5. Ma trận thiết bị (CHỈ ĐỌC; không bật màn, không chạm, không mở app)
Công cụ: `tools/qa/p5-device-preflight.ps1` (adb `devices -l`, `get-state`, `getprop`, `pm path`, `dumpsys package`, `pull` bản sao `base.apk` vào thư mục tạm để đọc vân tay rồi **xóa ngay**). Bản sao ở Tiny đã xóa; thư mục tạm `C:\Bots\_qa_temp\p5a` do tôi tạo cũng đã xóa.

| Thiết bị | Trạng thái | Chi tiết |
|---|---|---|
| **Sony Xperia 5 II** (Tiny, `QV725WEQ54`) | **PREFLIGHT PASS (read-only)** | `device`, XQ-AS52, Android 12/SDK 31, ABI arm64-v8a+armeabi*; `app.melovault.player` vc109 `0.6.88-beta` (cập nhật 2026-10-23 08:44:13), debuggable, cert `eecd3df7…fce8`. Chưa cài gì. [Log](E96-P5A-evidence/preflight-sony-readonly.txt) |
| **Shanling M3 Ultra** (Ni, `30313860180939`) | **PREFLIGHT PASS (read-only)** — *khác tài liệu preflight trước (lúc đó "vắng mặt"); hiện đang gắn* | `device`, "Shanling M3 Ultra", Android 10/SDK 29, arm64-v8a; vc109 `0.6.88-beta` (cập nhật 2026-10-19 23:21:55), debuggable, **cùng cert `eecd3df7…fce8`**. [Log](E96-P5A-evidence/preflight-shanling-readonly.txt) |
| **FYT 7870** | **BLOCKED_DEVICE** | không có trên adb USB; không quét mạng, không ghép đôi, không mở ADB-qua-mạng |
| Y700 | không cần cho P5 | không kiểm tra |

Cả hai gate chữ ký/ phiên bản/ ABI đã chạy thử với "ứng viên = APK QA P4 của Ni": **PACKAGE PASS · NO_DOWNGRADE PASS (109=109) · ABI PASS · SIGNATURE FAIL** (đúng như dự báo: APK Ni sẽ bị `HALT`, không cài).

## 6. Bảng cổng
| Cổng | Trạng thái |
|---|---|
| CODE | PASS (commit `8f75106`, flag OFF) |
| JVM | PASS (1822/biến thể, 0 fail, 21 skip cũ) |
| APK BUILD (Ni) | PASS biên dịch · **không phải bản giao** |
| APK BUILD đúng chữ ký (Tiny) | **WAIT_OWNER_APPROVAL** (chưa chuyển mã/ chưa build) |
| SIGNATURE MATCH | Ni FAIL · Tiny dự báo PASS (cert khớp), **chưa chứng minh bằng build thật** |
| TELEGRAM CONFIG | Ni false · Tiny UNVERIFIED |
| SONY / M3 | read-only PASS · cài đặt: WAIT_OWNER_APPROVAL |
| FYT | BLOCKED_DEVICE |
| REAL CHANNEL / REAL SYNC | **NOT EXECUTED** (cần duyệt riêng) |
| SCREENSHOTS / VIDEO | **NOT EXECUTED** (không có ảnh thật; không dựng ảnh giả) |

## 7. Rollback / khôi phục
- Mã: bỏ nhánh `dot/E96-P5-PHYSICAL-GATE` (chưa merge).
- Thiết bị: **chưa có gì để hoàn tác** (không cài). Kế hoạch cho P5-B: trước khi cài, kéo bản sao `base.apk` đang chạy của đúng thiết bị (lưu SHA-256) làm bản khôi phục; vì versionCode giữ 109 và cùng cert, khôi phục = `adb -s <serial> install -r <bản đó>` (không cần `-d`, không gỡ cài, dữ liệu giữ nguyên).
- Telegram (nếu sau này được duyệt): ứng dụng **không bao giờ xóa** kênh/tin; hoàn tác kênh là thao tác thủ công của chủ tài khoản; dữ liệu đã gửi nằm trên đám mây Telegram (không mã hóa đầu-cuối).

## 8. Điểm cần ChatGPT lưu ý
1. Tài liệu preflight ghi M3 vắng mặt; lúc chạy hiện **có** M3 ở Ni — chỉ đọc, không đổi gì.
2. `c24` (test P3) đã được sửa allowlist đúng 1 file — công khai ở §2.
3. Dự báo "Tiny build khớp chữ ký" dựa trên so khớp vân tay keystore, chưa phải kết quả build. Xác nhận thật là bước đầu của P5-B.
4. Cả hai máy đang chạy bản Beta **debuggable** ký khóa debug; bản P5-B sẽ cùng khóa/cùng package ⇒ cập nhật được tại chỗ, nhưng mọi bản ký debug vẫn là bản nội bộ.
5. Dòng trạng thái Playlist trên màn chính nay đọc lại khi resume (cục bộ). Không có tác vụ nền nào mới.

## 9. Xác nhận an toàn & dọn dẹp
- **Không** cài/gỡ/xóa dữ liệu/dừng-chạy app, không đánh thức hay tắt màn hình, không chạm cảm ứng, không logcat/đọc playlist, không quét mạng, không mở ADB-qua-mạng.
- **Không** tạo kênh, gửi/sửa/xóa tin, dùng tài khoản Telegram thật; `SYNC_ENABLED=false`; không đổi ký/versionCode; không đụng Private Stable/backend/E95/E93/P0–P4.
- Không đọc/in `local.properties`, keystore private, mật khẩu, API ID/hash (chỉ vân tay công khai).
- Dọn: Gradle `--no-daemon` (không còn tiến trình của tôi); đã xóa `C:\Bots\_qa_temp\p5a` (Tiny) và bản sao APK tạm (Ni, Tiny); không khởi emulator; adb server có sẵn (Tiny pid 27464 từ 9/10, Ni) **không đụng**. Giữ: worktree P5, bundle, XML test, log preflight.

**Đã dừng, chờ ChatGPT nghiệm thu.**
