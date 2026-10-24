# E96-P5B3 REWORK-01 — BÁO CÁO SONNET (code / test / build, KHÔNG Telegram, KHÔNG cài thiết bị)

**Kết luận đề xuất: PASS_CANDIDATE (chỉ phần code + JVM test + build trên Ni). Sonnet KHÔNG tự duyệt PASS.** Chưa có bằng chứng live nào; mọi thứ về Telegram thật / Sony / M3 vẫn CHƯA KIỂM CHỨNG.

## 1. Git

| | |
|---|---|
| Baseline đã chấp nhận | `D:\Bots\MeloVault-E96-P5B3-Playlist-Completion`, nhánh `dot/E96-P5B3-PLAYLIST-REAL-SYNC-READY`, HEAD `2b77e0efea07acd2ee41da4475d0870b15a7363b` (sạch, không đụng) |
| Worktree REWORK | `D:\Bots\MeloVault-E96-P5B3-Rework-01` (tạo mới bằng `git worktree add -b`; trước đó thư mục và nhánh chưa tồn tại) |
| Nhánh | `dot/E96-P5B3-REWORK-01`, tách đúng từ `2b77e0e` |
| Commit 1 (toàn bộ thay đổi chính) | `fc9ca76bafe8c01f05636eef9539936cf592fa66` |
| Commit 2 (grant mất khi đổi epoch đăng nhập) | `fb089928f60b4e16e49262e297190fbda9de1899` ← **mã nguồn của mọi APK/test dưới đây** |
| Commit 3 | chỉ thêm bản sao báo cáo này vào `docs/plans/` (không đổi code) |
| Diff 2b77e0e..fb08992 | 40 file, +2266 / −153 (29 sửa, 11 mới: 1 file production `TransmitPlanner.kt`, 1 resource `themes.xml`, 9 file test) |
| `git diff --check` | rc = 0 (chỉ cảnh báo LF→CRLF của autocrlf, như baseline) |
| Plan Opus | SHA256 `1935B41E…06C3A` khớp (185 dòng). Đã đọc CHATGPT-APPROVAL → OPUS-PLAN → LIVE-QA → prompt gốc |

Không reset/stash/clean/checkout đè, không merge, không đụng nhánh đã chấp nhận, Tiny, E95, Private Stable, backend.
Ghi chú sự cố nhỏ của chính tôi: hai lần chỉnh hàng loạt làm hỏng `TelegramPlaylistSyncProtocolTest.kt` (file test do tôi đang sửa, chưa commit); tôi dựng lại từ `git show HEAD:<file>` rồi sửa lại đúng cách. Không ảnh hưởng file nào khác.

## 2. Nguyên nhân gốc đã tái hiện (đỏ → xanh)

TDLib (`clean_input_string`) đổi mọi TAB thành dấu cách; giao thức P2 dùng TAB và checksum trên bản có TAB; transport so `provisional` với text gốc ⇒ luôn lệch ⇒ `Unknown` ⇒ 5 op `RETRY_REQUIRED` + backoff 5 s.

* **Bước (a) – đỏ trước:** sửa `FakeTdlibJsonGateway` để mô phỏng đúng `clean_input_string` + trim (bản copy độc lập `FakeTdlibClientText` trong test, có test vector) rồi chạy **code cũ `2b77e0e`**: nhóm test sync (5 lớp, 89 test) → **20 FAILED**, gồm test hồi quy mới `t1_fiveWaitingOperationsOfOneSongAreAcknowledgedByOneNormalisedEnvelope` (đúng tình huống Sony: 1 bài, 5 op, 1 envelope → BACKOFF thay vì OK). Bằng chứng: `rework01-logs\red-old-code.log`, `red-old-code-failed-tests.txt`, `red-old-code-junit\`.
* **Xanh sau sửa:** toàn bộ bộ test, xem §4.

## 3. Đã làm theo từng bước đã duyệt

| Bước | Nội dung | File chính |
|---|---|---|
| (b) Giao thức | Encoder dùng **một dấu cách** làm phân cách, `encode` kiểm bất biến `normalize(text)==text` (fail-closed). `TdlibText.normalize` = bản sao đúng `clean_input_string`. Decoder đọc đúng 3 dạng, mỗi dạng chỉ khi **checksum của chính nó** khớp: (a) dạng cách mới; (b) dạng TAB cũ; (c) dạng TAB cũ **sau khi TDLib đã đổi tab→cách** (dựng lại tab theo từng dòng, giữ nguyên header, kiểm checksum GỐC). Sai trường, 2 dấu cách liền, trường rỗng, sai sender/scope, op trùng, version tương lai, hash đổi ⇒ `Invalid`, **không bao giờ là ACK**. Transport so tin tạm/tin cuối với `normalize(text)`; text mà TDLib sẽ sửa thì **không gửi** (`SEND_PRECHECK_TEXT`). Không có ACK giả. | `TelegramPlaylistSyncProtocol.kt`, `TdlibPlaylistSyncTransport.kt` |
| (c) Hạn mức | `runOnce(account, mode, sendBudget: Int = 0)`: **không có "không giới hạn"**; thiếu/0/âm = chỉ đọc + ACK (`SEND_LIMIT_REACHED`, stage `SEND_BUDGET_ZERO`). `TdlibPlaylistSyncService.runSync(…, sendBudget = 0)` cắt về `SyncSendBudget.clamp(…, cap=3)` (cap chỉ tăng được trong constructor, chỉ test fake dùng). `TransmitPlanner` (thuần): chọn **k op đầu theo thứ tự outbox** (k=min(budget, chờ)), không bỏ qua/đảo/gộp; kiểm phụ thuộc REMOVE↔ADD. Trừ budget **tại lúc giao cho TDLib**, kể cả khi ACK mất (chỉ lỗi *trước* khi giao – consent/identity/connection/text – là miễn phí). Kiểm toàn bộ nơi gọi: `runOnce` chỉ có trong service (truyền `budget`), `runSync` chỉ trong controller (truyền grant), provider (truyền `sendBudget`) và `SyncTrigger` (không truyền ⇒ 0; không ai tạo `SyncTrigger` trong production) – được khóa bằng test quét mã nguồn. | `TransmitPlanner.kt`, `TelegramPlaylistSyncCoordinator.kt`, `TdlibPlaylistSyncService.kt`, `SyncServiceProvider.kt` |
| (c) Không gửi lại khi chưa rõ | `Unknown` ⇒ op đánh `RETRY_REQUIRED` (giữ id/thứ tự). **Nếu còn BẤT KỲ op `RETRY_REQUIRED` nào, planner trả `UnconfirmedBlocked` ⇒ không gửi gì cả** (kể cả op PENDING phía sau, kể cả budget lớn, kể cả `FULL_REPLAY`, kể cả sau nhiều ngày). Cách duy nhất để thoát: lần FETCH sau **đọc thấy** op đó trong lịch sử (kể cả dạng TAB đã bị TDLib làm sạch) ⇒ ACK. Không có đường "xác nhận rồi gửi lại" – đúng amendment 2. `getChatHistory only_local` KHÔNG dùng làm bằng chứng vắng mặt. | `TransmitPlanner.kt`, coordinator |
| (d) Kiểu lỗi + chẩn đoán | `SendResult.Failed` = **chắc chắn không lưu** (lỗi trả lời `sendMessage`, hoặc `updateMessageSendFailed` mã **4xx** kể cả 429) ⇒ op giữ **PENDING**; mọi thứ khác (timeout, mất trả lời, lệch text, đổi identity, `updateMessageSendFailed` mã 5xx/lạ) ⇒ `Unknown(stage)`. Lỗi 400 lạ ⇒ `REJECTED` ⇒ `RECOVERY_REQUIRED(REJECTED_BY_TELEGRAM)`; "have no write access" ⇒ `CHANNEL_MISSING`. `SyncStage` (enum đóng), `ErrorBucket`, `DurationBucket` trong `SyncReport`; UI hiện "Mã chẩn đoán: <TÊN_ENUM>"; log duy nhất `Log.w("MeloSync","stage=<enum> status=<enum>")` – không ID/nội dung/tên bài/token (test quét mã khóa số lượng lệnh `Log.` = 1). | `TelegramPlaylistSyncTransport.kt`, `TdlibSyncHub.kt`, `PlaylistSyncStatusPolicy.kt`, `MeloSyncSettingsActivity.kt` |
| Cấp lượt gửi (UI) | "Cho phép gửi 1/2/3 thay đổi" (hộp thoại xác nhận, xem số op đang chờ), chỉ trong bộ nhớ, thay chứ không cộng dồn, trừ theo `opsDispatched`; về 0 khi: tắt đồng bộ, rút quyền (c), đổi tài khoản/đăng xuất, **đổi epoch đăng nhập** (commit 2), đóng màn hình/khởi động lại tiến trình, hoặc cuộc gọi chết giữa chừng. Không tự cấp, không gửi nền. Trạng thái mới: `SEND_LIMIT_REACHED`, `DELIVERY_UNCONFIRMED` (không bao giờ là "Đã đồng bộ"). | `SyncSettingsController.kt`, `MeloSyncSettingsActivity.kt` |
| (e) Hộp thoại | `app/src/shareable/res/values/themes.xml`: `Theme.MeloVault.Shareable` (con của `Theme.MusicAssistantCar`) có `alertDialogTheme` + `materialAlertDialogTheme` → `Theme.MeloVault.Dialog` (con của theme tối đã dùng được), màu nút `#60A5FA`. Áp cho 4 activity trong manifest, 4 chỗ `setTheme`, `MeloEmulation`, và 11 `AlertDialog.Builder(...)` trong `TelegramPreviewActivity`. Personal **không đổi** (không file nào trong `main/` bị sửa). | `themes.xml`, `AndroidManifest.xml` (shareable) |
| (f) Khóa tạo kênh | Mọi build: `createNow` = LOCKED, `createChannel` ⇒ `Denied(CREATE_LOCKED)` ở controller **và** ở service/provider; chỉ Debug khởi chạy với **cả** `-PmeloSyncQa=true` **và** `-PmeloSyncQaAllowCreate=true` mới mở; Release + cờ ⇒ lỗi build. Kênh đã gắn ⇒ `AlreadyBound`, không tạo. Không có V1/V2 migration, không xóa outbox/tombstone/legacy. | `build.gradle`, `TelegramPlaylistV2Session.kt` (`SYNC_CREATE_ALLOWED`) |

## 4. Test (Ni, `--offline --no-daemon … --rerun-tasks`, trên commit `fb08992`)

Lệnh: `.\gradlew.bat --offline --no-daemon :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --rerun-tasks --console=plain` → **BUILD SUCCESSFUL**.
Parse JUnit XML (157 file mỗi biến thể; bản sao ở `rework01-logs\junit-final\{debug,release}`):

| Biến thể | tests | failures | errors | skipped |
|---|---|---|---|---|
| Debug | **1917** | 0 | 0 | 21 |
| Release | **1917** | 0 | 0 | 21 |

Baseline 1831 ⇒ **+86 test mới**. 21 skipped là đúng 21 cũ (BackgroundOfflinePlaylistSyncTest 19, PlaybackStabilitySoakTest 1, SmartOfflinePlaylistSyncTest 1); **0 test mới bị skip**. Đã sửa 2 test cũ có ngữ nghĩa đổi theo chính sách mới (xem §6) và cập nhật phụ trợ (Unknown thành lớp có stage, thêm budget cho test dùng fake).

Test mới (86): `TdlibTextNormalizationRegressionTest` 2 (T1), `TelegramPlaylistSyncProtocolTdlibTest` 12 (T2, T3, strict decoder), `TransmitPlannerTest` 7 (T4, T5, T18), `PlaylistSyncSendBudgetTest` 16 (T4–T10, T15, T3 tích hợp, T17), `SyncSendGrantTest` 19 (T12, T13, T14, T16, T17, cap production), `TdlibSendDiagnosticsTest` 13 (T10, T11, T14, T19), `ShareableDialogThemeTest` 6 (T20), `SyncBuildGateReworkTest` 7 (T21 + kiểm toán nơi gọi), `SyncStatusAndDataProtectionReworkTest` 4 (T22, T23).
Đối chiếu ma trận Opus: T1–T23 đều có test; T24 = toàn bộ test cũ vẫn PASS (1831 gốc).

## 5. APK build trên Ni — **KHÔNG cài lên thiết bị**

Thư mục: `D:\Bots\_handoff\MeloVault\E96-CROSS-DEVICE-SYNC\rework01-artifacts\` (ngoài repo; có `SHA256SUMS.txt`, `APK-INFO.txt`). Tất cả build từ commit `fb08992`, package `app.melovault.player`, versionCode 109.

| Tệp | Loại | versionName | bytes | SHA-256 |
|---|---|---|---|---|
| `…fb08992-Ni-standard-debug-SYNCOFF.apk` | Debug chuẩn (cloud sync OFF) | 0.6.88-beta | 33345924 | `ecd0cbb8025946fcb623f3772878e3c239fa3183505c0135e91ef82684429aca` |
| `…fb08992-Ni-standard-release-SYNCOFF.apk` | Release (cloud sync OFF cứng) | 0.6.88-beta | 30604182 | `4a9c1aa64ff5b6808c64e7dae8d9daa71978ae60ee391c56ffd5cf910f8c04ba` |
| `…fb08992-Ni-debug-SYNCQA-NOT-FOR-INSTALL.apk` | Debug Sync-QA (`-PmeloSyncQa=true`) | 0.6.88-beta-syncqa | 33345928 | `2faee48db41867732a8f5175e0dd76a72d368bc646043b5be8d8c56bb0a80e1f` |

* Chữ ký Ni (cả ba): `a9b8cb6b5b0bc0400955b24dbc85801f5e8855356287caca2dcf3bb4e13db280` — **khác** chữ ký Tiny/Sony `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` ⇒ **không thể cài đè** bản Beta đang chạy (signature mismatch). Không có `local.properties` trên worktree ⇒ `TELEGRAM_APP_API_ID = 0` (cấu hình Telegram = **false**, không dùng được để đăng nhập). Vì vậy các APK này **chỉ là chứng cứ build**, không phải QA candidate để cài.
* `BuildConfig` đã đọc: Release `MELO_SYNC_QA_BUILD=false`, `MELO_SYNC_QA_ALLOW_CREATE=false`; Sync-QA Debug `MELO_SYNC_QA_BUILD=true`, `MELO_SYNC_QA_ALLOW_CREATE=false`; chuẩn Debug: cả hai false.
* Ma trận cờ (`reportSyncQaFlag`): `qa=true+allowCreate=true` → true/true; chỉ `allowCreate=true` → false/false; không cờ → false/false; `assembleShareableRelease -PmeloSyncQaAllowCreate=true` → **BUILD FAILED** với thông báo rõ.
* Đây KHÔNG phải APK "QA CANDIDATE" để bàn giao Drive: cần build lại tại Tiny với cấu hình Telegram + chữ ký đúng khi ChatGPT/chủ dự án cho phép (ngoài phạm vi phiên này).

## 6. Quyết định / chỗ lệch so với plan Opus — ChatGPT cần xem

1. **Không có đường gửi lại sau `Unknown`** (kể cả "xác nhận + hết local pending" của Opus §2.2): theo amendment 2 chỉ còn đọc-ACK. Hệ quả có chủ ý: 5 op `RETRY_REQUIRED` của Sony, nếu *không* nằm trên kênh, sẽ bị chặn vĩnh viễn (`DELIVERY_UNCONFIRMED`) cho tới khi có quy trình/phê duyệt mới; các thay đổi mới của người dùng xếp hàng *sau* chúng. Không triển khai `localPendingOutgoing` / `DELIVERY_PENDING_IN_TDLIB` (không cần khi không gửi lại).
2. Hai test cũ thay ngữ nghĩa đúng theo chính sách: `t09b` (trước: gửi lại bản trùng khi chưa thấy tin → nay: không gửi, chờ đến khi thấy), `P5ReadinessEnabledFlowTest.c09` (trước: thử lại sau timeout → nay: `DELIVERY_UNCONFIRMED`, không gửi thêm). `t24` dùng lỗi NETWORK (definite) thay cho TIMEOUT để vẫn kiểm "thành công xóa số lần lỗi". Test lost-answer cũ (t09, i03, c07) đổi `BACKOFF` → `DELIVERY_UNCONFIRMED` nhưng giữ nguyên các khẳng định ACK/không gửi hai lần.
3. `updateMessageSendFailed` mã không phải 4xx (vd. 500) ⇒ `Unknown`, không phải `Failed` (có thể server đã xử lý). Test `s05` cập nhật.
4. Grant tính theo **op** (không theo bài), tối đa 3 mỗi lần cho phép, gắn với màn hình Cài đặt (đóng màn hình = mất). Epoch được theo dõi qua `ShareableTdlibOwner.authEpoch()`.
5. `SyncStage`: bỏ `FETCH_IDENTITY`, `SEND_UNCONFIRMED_BLOCKED`; dùng `READBACK_ABSENT` cho "đã đọc hết lịch sử mà không thấy op" (giữ nguyên chặn gửi). `ErrorBucket`/`DurationBucket` có trong `SyncReport` nhưng **chưa hiển thị trên UI** (chỉ stage hiển thị).
6. Theme hộp thoại kế thừa theme tối đã dùng được trong `Theme.MusicAssistantCar.Dialog` thay vì viết overlay mới; kiểm tương phản bằng test đọc XML thật (≥4,5:1: chữ chính/phụ, on-surface, list item, nút). **Chưa xem bằng mắt/ảnh thiết bị** (ngoài phạm vi).

## 7. Chưa kiểm chứng / rủi ro còn lại

* **Chưa chạy gì trên Telegram thật, Sony, M3** (đúng lệnh cấm): chưa xác nhận ngoài đời rằng (i) tin 5 op cũ của Sony đang nằm trên kênh và giờ đọc được; (ii) dạng cách mới sống sót qua TDLib thật (đã chứng minh bằng bản sao `clean_input_string`, không phải bằng TDLib thật; server Telegram còn có thể chuẩn hóa thêm); (iii) hộp thoại hết trắng-trên-trắng trên Sony.
* `clean_input_string` được sao theo mô tả trong plan/ChatGPT (misc.cpp ở revision c15d3f5 trên Tiny); tôi không tự đọc lại nguồn TDLib trên Tiny.
* Bất kỳ lỗi xử lý bất ngờ trong `transport.send` (exception) cũng được coi là `Unknown` (an toàn nhưng chặn gửi) – bảo thủ có chủ ý.
* Bản Ni không có cấu hình Telegram; APK trên chỉ để chứng minh build/cờ/chữ ký.
* Âm thanh thật, FYT, mất mạng: **CHƯA XÁC NHẬN** (không thuộc phạm vi code-only).

## 8. An toàn và dọn dẹp

* Không gửi Telegram thật, không tạo/tìm kênh, không bật Sync trên thiết bị, **không chạy `adb`** (không install/devices/screencap/input), không sửa Private Stable/backend/E95/Tiny, không xóa playlist/outbox/tombstone, không merge, không chạy Opus/agent thứ hai. Không đọc/in/copy `local.properties` hay secrets (không có trong worktree; dựa vào `ANDROID_HOME` của máy).
* Sony và M3: không chạm tới ⇒ trạng thái màn hình không đổi so với khi bàn giao (OFF theo báo cáo live trước).
* Gradle luôn chạy `--no-daemon`; đã kiểm không còn tiến trình Gradle/Java/Kotlin do phiên này mở (xem câu lệnh kiểm trong chat). Thư mục tạm của phiên (`scratchpad`) chỉ chứa script perl nhỏ, không có bí mật.
* Log/XML/APK nằm ngoài repo tại `D:\Bots\_handoff\MeloVault\E96-CROSS-DEVICE-SYNC\rework01-logs\` và `…\rework01-artifacts\`.

**Đã dừng, chờ ChatGPT nghiệm thu.**
