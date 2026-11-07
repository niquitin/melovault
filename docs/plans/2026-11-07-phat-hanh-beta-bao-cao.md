# Báo cáo thực thi — Phát hành bản beta cho bạn bè

Thực thi: Claude Code Sonnet 5.5, phiên GỘP (S0–S8). **Trạng thái: S0–S5 xong; S6–S8 chưa làm (xem bàn giao).**

| Mục | Giá trị |
|---|---|
| TASK_ID / nhánh | `2026-11-07-phat-hanh-beta` / `dot/2026-11-07-phat-hanh-beta` |
| Baseline | `71189c9` (đã gộp widget `7ce38ec`) |
| Mốc code S1–S4 | `9526d35` (+ sửa test ở commit gate S5) |
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
| Dòng phụ "Nhạc từ Telegram của bạn · Ứng dụng không chính thức" | `TelegramPreviewActivity` (text khởi tạo của `syncSubtitle`) |
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
| Đếm từ XML — Debug | 209 suite, 2.823 test, 21 skipped, 0 failures, 0 errors |
| Đếm từ XML — Release | 209 suite, 2.823 test, 21 skipped, 0 failures, 0 errors |
| So baseline `7ce38ec` | 208 suite / 2.815 → 209 / 2.823 (+8 test, +1 suite: `MeloPublicBuildPolicyTest`) |
| `assembleShareableDebug/Release` (bản riêng) | BUILD SUCCESSFUL |
| `-PmeloOpen=true assembleShareableDebug/Release` | BUILD SUCCESSFUL |
| `assemblePersonalDebug` | BUILD SUCCESSFUL |
| `git diff --check` | sạch (chỉ cảnh báo LF→CRLF của Git) |

APK build trên Ni **không** dùng để QA hay đăng: không kiểm giá trị cấu hình Telegram trên Ni (cấm đọc); APK QA phải build ở Tiny có cấu hình (S6).

## Việc còn lại
S6 (build Tiny + gate APK), S7 (QA Sony), S8 (bàn giao). Chi tiết ở [bàn giao](2026-11-07-phat-hanh-beta-ban-giao.md).
