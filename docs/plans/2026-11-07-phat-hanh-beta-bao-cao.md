# Báo cáo thực thi — Phát hành bản beta cho bạn bè

Thực thi: Claude Code Sonnet 5.5, phiên GỘP (S0–S8). **Trạng thái: S0–S8 xong về kỹ thuật; Q4–Q7 BLOCKED (Sony chưa đăng nhập bản .open).**

| Mục | Giá trị |
|---|---|
| TASK_ID / nhánh | `2026-11-07-phat-hanh-beta` / `dot/2026-11-07-phat-hanh-beta` |
| Baseline | `71189c9` (đã gộp widget `7ce38ec`) |
| Commit | `9526d35` (S1–S4), `3511ea4` (báo cáo + sửa test), **`a0694c0` (sửa nhãn Preview, source của APK)** |
| Không làm | Không tăng versionCode (giữ vc122), không tạo khóa ký, không công khai gì |

## S0 — Kiểm đầu phiên
Cây sạch, HEAD `71189c9`, `7ce38ec` là tổ tiên của HEAD, nghiệm thu widget ghi **ĐẠT** (07/11/2026). Đi tiếp.

## S1 — Tên và ký
- `app/build.gradle`: `resValue 'string','app_name'` = `MeloVault` khi `-PmeloOpen=true`, giữ `MeloVault Preview` cho bản riêng. Đã xóa `app/src/shareable/res/values/strings.xml` (chỉ chứa `app_name`, sẽ trùng resource).
- Giữ `signingConfigs.debug`, sửa ghi chú theo quyết định 07/11/2026. Không thêm `meloPublicRelease`. vc122, `0.6.97-open`.
- Kiểm trên Ni (aapt2): open Debug/Release đều `app.melovault.player.open`, vc122, `0.6.97-open`, nhãn `MeloVault`; Release **không** `application-debuggable`.

## S2 — Tuân thủ trong app (`MeloPublicBuildPolicy`, thuần, có test JVM)
| Việc | Nơi sửa |
|---|---|
| Dòng phụ "Nhạc từ Telegram của bạn · Ứng dụng không chính thức" | `TelegramPreviewActivity` (text khởi tạo của `syncSubtitle`; **bị ghi đè ngay bằng "Telegram · Chờ kết nối"/số bài**, nên chưa đủ) |
| **Nhãn chữ hoa luôn hiện "TELEGRAM · NHẠC CÁ NHÂN · PREVIEW" → "TELEGRAM · NHẠC CỦA BẠN · KHÔNG CHÍNH THỨC"** (kế hoạch bỏ sót; phát hiện qua QA Sony, sửa ở `a0694c0`) | `TelegramPreviewActivity` `note`, `MeloPublicBuildPolicy.eyebrow` |
| Ẩn nút QR | `TelegramLoginPanel.render` |
| Câu đăng ký tài khoản mới | `TelegramPreviewActivity` (WAIT_REGISTRATION) |
| Dòng "không chính thức" + liên kết Quyền riêng tư trên màn đăng nhập | `TelegramLoginPanel` (chỉ bản open) |
| Ẩn Công cụ nhà phát triển (Giả lập thiết bị, Sao chép số đo…) kể cả đã mở khóa; 7 lần chạm phiên bản không còn tác dụng | `MeloUiLabActivity` (`devUnlocked` qua chính sách) |
| About của bản open có thêm dòng không chính thức + hàng mở trang riêng tư trên web | `MeloUiLabActivity.renderAbout` |

**Không ẩn, ghi nhận:** hàng "Đồng bộ Yêu thích & Playlist" mang nhãn "Thử nghiệm" — là tính năng thật, chưa rõ chủ dự án muốn giữ nhãn cho bạn bè; để nguyên. Các thông báo QR nhắc máy Lenovo vẫn nằm trong mã nhưng không còn lối vào ở bản open.

## S3 — Giới thiệu & giấy phép — **lệch kế hoạch có lý do**
Kế hoạch ghi "chưa có MeloAboutActivity, licenses.txt". Thực tế nhánh đã có (từ nhiệm vụ Cài đặt 02/11): trang **Giới thiệu** (phiên bản, tác giả, mã nguồn, góp ý, Quyền riêng tư) và **Giấy phép mã nguồn mở** trong Cài đặt, đọc toàn văn từ `res/raw/third_party_notices.txt`, cả hai bản. Tạo thêm Activity trùng lặp là mở rộng kiến trúc nên **không làm**; thay vào đó:
- Danh sách thư viện lấy từ `gradlew :app:dependencies --configuration shareableReleaseRuntimeClasspath` (log nằm ở phụ lục artifact) cộng TDLib. Phát hiện **thiếu** trong danh sách cũ và đã bổ sung: Guava, OkHttp/Okio, Kotlin Coroutines, JetBrains Annotations/JSpecify/Error Prone, và nới mô tả AndroidX (Lifecycle, Room, Fragment…).
- `third_party_notices.txt` thêm **toàn văn Apache 2.0** (sao từ tệp LICENSE trong bản phân phối Gradle 8.13, 177 dòng điều khoản chuẩn) và **Boost 1.0** (TDLib).
- Chưa kiểm trực quan trên máy: màn Giấy phép cuộn toàn văn (Q3, S7).

## S4 — Sao lưu
Shareable thừa kế `allowBackup=true` từ manifest chính; quy tắc chung chỉ loại trừ token của bản Personal. Khảo sát nơi lưu: khóa DB TDLib, session, cặp API, playlist, cache lời nhạc đều ở `noBackupFilesDir` (OS không sao lưu). Còn lọt ngoài đó: DB thư viện `databases/melovault_tg_index_<tài khoản>.db` (tên kênh/bài/định danh), và prefs `e91_tdlib_preview` (con trỏ thư mục session), `melovault_resume`, `melovault_library_counts`, `melovault_local_music`.
→ Thêm `app/src/shareable/res/xml/backup_rules.xml` và `data_extraction_rules.xml` (chỉ Shareable; Personal giữ nguyên, `allowBackup` không đổi) loại trừ các mục trên (cloud-backup và device-transfer). Giữ prefs giao diện/EQ. **Chưa chứng minh bằng `adb shell bmgr`/chuyển máy** — chỉ kiểm cú pháp qua build và test đọc tệp.

## S5 — Gate Ni (log: `docs/plans/artifacts/2026-11-07-phat-hanh-beta/gate_*.log`)
| Lệnh | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest` + `Release` | BUILD SUCCESSFUL (lần đầu 1 test mới của tôi sai khẳng định về `personal/res/xml`, đã sửa test, chạy lại sạch) |
| Đếm từ XML — Debug (lần cuối, source `a0694c0`) | 209 suite, 2.824 test, 21 skipped, 0 failures, 0 errors |
| Đếm từ XML — Release (lần cuối) | 209 suite, 2.824 test, 21 skipped, 0 failures, 0 errors |
| So baseline `7ce38ec` | 208 suite / 2.815 → 209 / 2.824 (+9 test, +1 suite: `MeloPublicBuildPolicyTest`; gate chạy lại sau `a0694c0`, BUILD SUCCESSFUL; assemble không chạy lại, đã chạy Tiny) |
| `assembleShareableDebug/Release` (bản riêng) | BUILD SUCCESSFUL |
| `-PmeloOpen=true assembleShareableDebug/Release` | BUILD SUCCESSFUL |
| `assemblePersonalDebug` | BUILD SUCCESSFUL |
| `git diff --check` | sạch (chỉ cảnh báo LF→CRLF của Git) |

APK build trên Ni **không** dùng để QA hay đăng: không kiểm giá trị cấu hình Telegram trên Ni (cấm đọc); APK QA phải build ở Tiny có cấu hình (S6).


## S6 — Build Tiny và gate APK (source `a0694c0`)
Lượt 1 (source `3511ea4`) build và cài xong nhưng QA Sony lộ lỗi nhãn Preview; **APK lượt 1 bị loại**, không upload. Lượt 2 là bản bàn giao.

| Mục | Kết quả |
|---|---|
| Bundle | Đầy đủ lịch sử, SHA-256 `75740367…26ABE`, Ni và Tiny khớp, `git bundle verify` đạt; clone cô lập `C:\Bots\MeloVault-BETA-IsolatedQA-20261107-a0694c0`, detach đúng source, cây sạch |
| Config | `-PmeloLocalPropertiesFile` tới local.properties của clone EQ cũ; chỉ kiểm có/không khóa; `telegram.open.config.valid=true`, `telegram.open.distinct=true` |
| Build open Release | BUILD SUCCESSFUL 2m31s |
| `verifyOpenApkHasNoTelegramHash` | `open.apk.telegram.hash.found=false` |
| `verifyOpenApkHasOpenTelegramHash` | `open.apk.open.hash.found=true` |
| aapt | `app.melovault.player.open`, vc122, `0.6.97-open`, nhãn `MeloVault`, **không** `application-debuggable` |
| apksigner | Verifies, scheme v2 đạt, 1 signer, cert `eecd3df7…fce8` |
| APK | 31.051.460 B, SHA-256 `54AB04C8A0F4418B379EA2DA19FFE281283D84E4A46B5DE90BE7DD8FDFC42CCF` |
| Hồi quy Debug thường (`-PmeloOpen` tắt) | BUILD SUCCESSFUL 1m08s; `app.melovault.player` vc122 `0.6.97-beta`, nhãn `MeloVault Preview`, cert đúng, `telegram.config.valid=true`; 33.941.680 B (không cài) |

## S7 — QA Sony `QV725WEQ54` (XQ-AS52, Sony, Android 12), 07/11/2026
Gate trước cài: model/manufacturer đúng, `device`, `.open` đang vc121 `0.6.96-open` (không downgrade), MediaSession đang tạm dừng (state=2), APK cũ của hai gói được pull làm bản lưu. Chỉ `adb install -r`; APK pull lại từ máy khớp SHA (`INSTALLED_HASH_MATCH=true`). **`app.melovault.player` (vc122 `0.6.97-beta`, widget) không đổi**: APK pull trước/sau cùng SHA, `lastUpdateTime` giữ nguyên.

| Q | Kết quả | Ghi chú |
|---|---|---|
| Q1 Launcher | **PASS** | Ngăn kéo có "MeloVault", "MeloVault Preview" và một "MeloVault" khác (gói cũ); mở đúng `.open` bằng `am start` |
| Q2 Màn đăng nhập | **PASS sau sửa** | Lượt 1 FAIL: nhãn vẫn "…PREVIEW" (lỗi nêu ở S2). Lượt 2: nhãn "KHÔNG CHÍNH THỨC", dòng không chính thức + liên kết, **không có nút QR**. Chưa chạm liên kết (mở trình duyệt) |
| Q3 Cài đặt, Giới thiệu, Giấy phép | **PASS** | Không có mục Công cụ nhà phát triển; Giới thiệu `0.6.97-open (vc122)` có dòng không chính thức và hàng trang riêng tư; danh sách giấy phép đủ mục mới; toàn văn Apache cuộn được (chữ đơn cách xuống dòng hơi xấu, chấp nhận). Mục "Đồng bộ… Thử nghiệm" vẫn hiện (xem S2) |
| Q4 Phát bài, tạm dừng, Tiếp | **BLOCKED** | Bản `.open` trên Sony chưa đăng nhập (màn nhập số điện thoại). Agent không nhập số/mã |
| Q5 Bản sửa bộ lọc, Đang phát ngang | **BLOCKED** | Cùng lý do |
| Q6 Widget | **BLOCKED** | Cần phiên phát trong bản `.open`; widget hiện trên Home là của gói `app.melovault.player`, không phải bản này. Không thêm widget mới ra Home |
| Q7 EQ | **BLOCKED** | Cần đăng nhập/phát |
| Q8 Log crash; gói cũ | **PASS** | Crash buffer: 0 dòng MeloVault; chỉ có một FATAL của gói `com.facebook.appmanager` (15:22, trước khi cài). `app.melovault.player` không đổi |

Màn Sony tắt trong `finally`, `SONY_SCREEN_END`: `Display State=OFF` (cả hai lượt). Ảnh tổng hợp: `docs/plans/artifacts/2026-11-07-phat-hanh-beta/r2/00_tong_hop.png` (không commit); không có tên bài, tên kênh. **CHƯA XÁC NHẬN ÂM THANH THỰC.**

**Phát hiện khác, không sửa (ngoài kế hoạch):** màn đăng nhập bản open luôn hiện hai nút "Nhập lại mã API" và "Dùng mã API riêng (nâng cao)" khi chưa đăng nhập; đây là hành vi D7 có sẵn, chưa rõ có nên giấu với bạn bè không → chủ dự án quyết.

## S8 — Bàn giao
- **QA CANDIDATE — CHƯA NGHIỆM THU — CHƯA ĐĂNG.** Hồ sơ: [apk-test](2026-11-07-phat-hanh-beta-apk-test.md), [hình](2026-11-07-phat-hanh-beta-hinh-nghiem-thu.md). Drive owner-only đã kiểm (một quyền: owner), kích thước APK khớp.
- Không đăng gì, không tạo tag/Release, không đụng `app.melovault.player` (kiểm SHA trước/sau).

## APK QA bàn giao chủ dự án nghiệm thu
| Mục | Giá trị |
|---|---|
| Git commit | `a0694c05b8df22ef90a01cdd52738528747341b7` (nhánh `dot/2026-11-07-phat-hanh-beta`; commit tài liệu sau đó không đổi mã) |
| File (Tiny) | `C:\Bots\_qa_temp\beta-20261107\r2\MeloVault-0.6.97-open-vc122-betaa0694c0-QA-CANDIDATE-release.apk` |
| Package / version | `app.melovault.player.open` / 122 / `0.6.97-open` |
| Loại | Release (không debuggable), ký bằng khóa debug tạm của Tiny |
| SHA-256 / byte | `54AB04C8A0F4418B379EA2DA19FFE281283D84E4A46B5DE90BE7DD8FDFC42CCF` / 31.051.460 |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` |
| Telegram | open pair hợp lệ nhúng (true); APK **không** chứa hash của cặp riêng (false) |
| Gradle test | 209 suite, 2.824 test, 21 skipped, 0 lỗi (Debug và Release) |
| Chưa QA | Q4–Q7 (đăng nhập, phát, bộ lọc, widget, EQ); âm thanh thật; máy khác Sony; cài mới từ đầu; chạm liên kết riêng tư; chuyển máy/sao lưu thực |

## Rủi ro và việc chưa kiểm
- Release chưa chạy nội dung thật trên máy: Q4 chặn vì chưa đăng nhập → **chủ dự án đăng nhập trên Sony rồi mới có bằng chứng** (hoặc giao phiên QA tiếp).
- Sao lưu: chỉ kiểm cú pháp qua build; chưa thử `bmgr`/chuyển máy.
- Trang giấy phép: danh sách lấy từ cây phụ thuộc runtime Release; Kotlin stdlib và androidx có nhiều phiên bản trong cây, ghi chung theo nhóm.
- Mục "Thử nghiệm" ở Đồng bộ, hai nút mã API trên màn đăng nhập: để nguyên.
- Rủi ro điều khoản Telegram (tin tài trợ), khóa debug tạm: theo kế hoạch mục 6.
