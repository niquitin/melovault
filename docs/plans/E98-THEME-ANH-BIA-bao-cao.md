# E98-THEME-ANH-BIA — báo cáo thực thi (Sonnet)

TASK_ID: `E98-THEME-ANH-BIA` · Kế hoạch: `docs/plans/E98-THEME-ANH-BIA.md`

## 1. Nhánh, commit, nền

| Mục | Giá trị |
|---|---|
| Nhánh | `dot/E98-THEME-ANH-BIA` (repo `D:\Bots\MeloVault-E91-Shareable` trên Ni) |
| Nền (parent của kế hoạch) | `826d166` (vc111, 0.6.88.1) |
| Commit kế hoạch | `25ee89d` |
| S1 | `60cbc58` — chính sách "Kiểu màu", bảng màu TEAL, lựa chọn trong UiLab, công tắc ảnh bìa |
| S2 | `cea4634` — `MeloRowArtPolicy` (luật chọn thumbnail, khóa có account, decode, Scheduler LIFO tối đa 3, MissCache) |
| S3 | `9d60f5e` — `MeloRowArtLoader` (cache, ảnh nhúng / thiết bị / Telegram, ảnh kênh) nối vào host, sự kiện TDLib, library pane |
| S4 | `48986ac` — hàng bài teal, 4 tab menu có biểu tượng, ô tìm kiếm + bảng lọc + chip thể loại, thanh A–Z mọi kiểu sắp xếp |
| S5 | `9afc326` — khung trái Hai khung teal (không cột nav, logo, pill chất lượng, tiến độ chỉ hiển thị, 3 nút lớn, lưới 2×2) và đầu trang điện thoại teal có hàng đồng bộ |
| S6 | **không làm** (nền ảnh mờ; xem mục 4) |
| S7 | `8654323` — nâng shareable beta lên vc112 (`0.6.89-beta`) |

Commit chứa báo cáo này nằm ngay sau `8654323` trên cùng nhánh; chỉ thêm tài liệu, không đổi mã.
Tác giả commit mã: `Claude Code <noreply@anthropic.com>` (cấu hình git toàn cục không bị sửa).

## 2. File đã đổi (26 file, +2448 / −87 so với `25ee89d`)

- Mới: `MeloThemePolicy.kt`, `MeloRowArtPolicy.kt`, `MeloRowArtLoader.kt`, `res/drawable/ic_settings.xml`, `ic_tune.xml`, tests `MeloThemePolicyTest.kt`, `MeloRowArtPolicyTest.kt`.
- Sửa mã: `MeloComponents.kt`, `MeloDesignTokens.kt`, `MeloLibraryPane.kt`, `MeloTabletScreen.kt`, `MeloPhoneScreen.kt`, `MeloScreenHost.kt`, `TelegramPreviewActivity.kt`, `MeloAlphabetBar.kt`, `MeloAlphabetPolicy.kt`, `MeloLibraryPolicy.kt`, `MeloComponentPolicy.kt`, `MeloNowPlayingPolicy.kt`, `MeloUiLabActivity.kt`, `app/build.gradle`.
- Sửa test: `MeloAlphabetPolicyTest`, `MeloComponentPolicyTest`, `MeloDesignTokensTest`, `MeloLibraryPolicyTest`, `MeloNowPlayingPolicyTest`.
- Không đụng: DB/schema, songKey, đồng bộ, Media3 service, đăng nhập, ký, chế độ Gọn, Car Mode, Studio Deck, `CLAUDE.md`, `docs/workflow`, file nghiệm thu.

## 3. Đã làm

- Kiểu màu mới, tùy chọn: "Hiện tại" (mặc định, giữ nguyên) và "Teal sáng · ảnh bìa". Chỉ áp dụng cho bố cục mới trên Điện thoại dọc / Tablet dọc / Hai khung ngang; Gọn và Car Mode không đổi. Khóa theme nằm trong khóa dựng lại màn hình.
- Ảnh bìa trên mỗi hàng bài: ảnh bìa Telegram → ảnh kênh → ô chữ cái. Cache LRU theo byte (trần 12 MB, 1/16 memoryClass), MissCache, Scheduler LIFO tối đa 3 yêu cầu, giữ lại hàng đang hiển thị, watchdog 20 s, một luồng decode ưu tiên thấp, không yêu cầu khi đang fling, tải hàng đang hiển thị + 4 hàng phía trước, decode ≤192 px RGB_565. Công tắc "Tải ảnh bìa từ Telegram" (mặc định bật). Nhật ký chỉ có số đếm (`MeloRowArt`), không có tên bài/kênh.
- 4 tab menu (Tất cả / Đã tải / Playlist / Nguồn) có biểu tượng, ô tìm kiếm có nút lọc mở bảng lọc, chip thể loại, thanh A–Z (kèm chuyển sang sắp xếp theo tên), hàng đồng bộ + ⚙, logo.
- Hai khung ngang: khung trái teal (logo, ảnh bìa lớn, tiến độ chỉ hiển thị, 3 nút lớn, lưới 2×2 Đảo/Lặp/Lưu bài/Hàng đợi).
- Test mới/mở rộng: +58 so với nền 1975.

## 4. Chưa làm / bị chặn

| Mục | Trạng thái | Lý do |
|---|---|---|
| S6 nền ảnh bìa mờ | CHƯA LÀM | không dựng trong phiên này; mặc định không ảnh hưởng gì tới các bước còn lại |
| Cài APK lên Shanling 30313860180939 và hồi quy Gọn | **BLOCKED** | APK duy nhất trên Ni không có cấu hình Telegram và không được cài làm APK QA; APK có cấu hình chỉ nằm trên Tiny, không có đường chuyển hợp lệ sang Ni (ổ `T:` chưa gắn, share `C$` bị từ chối, `net use` bị chặn) và không được sao `local.properties` |
| Y700 / S25 Ultra | KHÔNG KIỂM | không có thiết bị trong phiên |
| Video | KHÔNG CÓ | chỉ dùng ảnh chụp màn hình |

Bằng chứng thay thế cho Gọn (không phải thay thế thật): không có thay đổi riêng cho Gọn/Car Mode (các file chung chỉ thêm nhánh teal đặt sau `MeloComponents.teal`), `ColorTheme.effective` trả về CURRENT khi không có NewLayer hoặc Profile không phải PHONE/TABLET (test `MeloThemePolicyTest`). **Sony không thay được Shanling.** Cài đặt "Tự động" của Gọn chưa từng bị đổi nên không cần hoàn trả.

## 5. Kiểm thử và build

Lệnh (từ gốc repo, Ni): `gradlew testShareableDebugUnitTest testShareableReleaseUnitTest`, rồi `gradlew assembleShareableDebug assembleShareableRelease`, và `git diff --check 25ee89d..8654323`. Log: `%TEMP%\e98_s7_tests.log`, `%TEMP%\e98_s7_assemble.log` trên Ni.

| Bộ | Định nghĩa | Qua | Bỏ qua | Lỗi |
|---|---|---|---|---|
| `testShareableDebugUnitTest` | 2033 | 2012 | 21 | 0 |
| `testShareableReleaseUnitTest` | 2033 | 2012 | 21 | 0 |
| `assembleShareableDebug` / `Release` (Ni, không có cấu hình Telegram) | — | BUILD OK | — | — |
| `git diff --check` | — | sạch | — | — |

Nền trước E98: 1975 test → +58. Số test bỏ qua (21) lấy từ báo cáo Gradle; chưa đối chiếu riêng với nền xem có thay đổi hay không.

## 6. Gói, chứng chỉ, cài đặt

| Mục | Giá trị |
|---|---|
| Gói | `app.melovault.player` (flavor `shareable`) |
| Phiên bản | vc112 / `0.6.89-beta` |
| Build APK QA | Debug, dựng trên Tiny trong bản clone cô lập `C:\Bots\MeloVault-E93D-IsolatedQA`, từ `8654323` (chuyển bằng Git bundle + SHA256 khớp) |
| Cấu hình Telegram | **true** (`reportTelegramConfig` chỉ in true/false; `local.properties` không rời Tiny) |
| Cờ `melo.sync.qa` | không dùng (không hậu tố `-syncqa`) |
| Chứng chỉ ký (debug key) | SHA-256 `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` — khớp bản đang cài, không hạ phiên bản |
| Sony QV725WEQ54 (Tiny) | Đã `adb install -r` sau khi kiểm gói, phiên bản, chữ ký, cấu hình. Không gỡ cài, không `pm clear`, không đăng xuất, không xóa dữ liệu/cache/TDLib |
| Shanling 30313860180939 (Ni) | **BLOCKED** — chưa cài |

## 7. Việc tác nhân đã làm (QA)

| # | Bước | Kết quả |
|---|---|---|
| 1 | Dựng, kiểm tra và cài APK có cấu hình Telegram lên Sony (Tiny) | PASS |
| 2 | Sony dọc, theme Teal: hàng bài có ảnh bìa, 4 tab, bảng lọc, thanh A–Z, fling | PASS (số đo ở mục 8) |
| 3 | Sony ngang (Hai khung): khung trái teal, đang phát, hàng hiện tại | PASS về hiển thị; không xác nhận âm thanh thật |
| 4 | Đặt "Hiện tại": giao diện cũ không đổi; đo fling cùng kịch bản | PASS |
| 5 | Tắt công tắc ảnh bìa: về ô chữ cái, không còn yêu cầu mới trong nhật ký | PASS |
| 6 | Hồi quy Gọn trên Shanling | **BLOCKED** |

Ảnh trước/sau (riêng tư, trên Tiny): Hiện tại `01`, `24`; Teal `05`–`07`, `27`; lọc `08`, `10`–`12`; tab `13`, `14`; A–Z `15`, `16`; ngang `17`, `18`; tắt ảnh bìa `29`, `30`; trạng thái lưu `31`, `32` (xem mục 11).

## 8. Số đo hiệu năng và ảnh bìa (Sony, Android 12, 120 Hz)

| Phép đo | Kết quả |
|---|---|
| Fling 10 s, Teal (lượt đầu, kịch bản tay) | janky 13,79 % |
| Fling 10 s, kịch bản `adb input swipe` 46 lần — Teal | 1032 khung, janky **16,57 %** (p90 21 ms, p99 29 ms) |
| Cùng kịch bản — Hiện tại | 982 khung, janky **20,57 %** (p90 23 ms, p99 34 ms) |
| Chênh lệch Teal − Hiện tại | −4,0 điểm (Teal không tệ hơn; ngưỡng yêu cầu ≤ +5) |
| Phủ ảnh bìa | khung cuối 9/9 hàng hiển thị; gộp toàn phiên: 384/448 = **85,7 %** (65 mẫu, 51 mẫu đủ 100 %) |
| Yêu cầu đồng thời tối đa | 3 |
| Cache tối đa | 8,45 MB (< trần 12 MB) |
| Đếm lần cuối | sent=189 hits=7998 decoded=207 failed=1 miss=4 pending=0 inflight=0 |
| Tắt ảnh bìa + 12 lần cuộn | 0 dòng `MeloRowArt` mới |
| Crash buffer / dropbox ANR / dropbox crash cho `melovault` | rỗng |

Lưu ý: fling bằng `adb input swipe` là tổng hợp, dao động theo tải thiết bị (13,79 % → 16,57 % cho cùng theme Teal ở hai lượt); so sánh đáng tin chỉ là cặp cùng kịch bản (16,57 % so với 20,57 %).

### Media3 / ANR

Không có crash hay ANR trong phiên. Media3 báo `state=3` (đang phát), vị trí tiến lên khi bấm ▶ ở Hai khung; sau đó tạm dừng (`state=2`). Chưa xác nhận bằng tai có âm thanh thật.

## 9. Rủi ro / điểm chưa kiểm chứng

- Chưa chạy Gọn trên Shanling (BLOCKED); chưa kiểm Y700/S25.
- Khung trái Hai khung trên máy cao ≤ 360 dp có thể chật; chưa đo trên Y700.
- S6 (nền mờ) chưa làm.
- Phép đo janky tổng hợp, dao động giữa các lượt.
- **Tác dụng phụ do bấm nhầm trên Sony:** một lần chạm khi tiêu đề thu gọn đã phát một bài Telegram (bài được tải về máy). Đã tạm dừng bằng `keyevent 127` và cuộn về đầu; hàng đợi hiện tại của Sony là bài "NST - 4H NOEL Vietmix…". Không xóa dữ liệu nào.
- Một ảnh bìa lỗi giải mã (`failed=1`), được MissCache giữ, hàng đó rơi về ô chữ cái.

## 10. Màn hình (SCREEN_LIFECYCLE_QA)

Sony: bật bằng `keyevent 224` khi thao tác, tắt bằng `keyevent 223`; xoay màn hình trả về `free` (`user_rotation=0`, `accelerometer_rotation=1` như ban đầu); cài đặt trả về `color_theme=current`, `row_art_telegram=true`, `compact_mode=auto`.

- `SONY_SCREEN_END 2026-10-25T11:31:17` — `Display State=OFF`.
- `SHANLING_SCREEN_END 2026-10-25T11:31:35` — `mWakefulness=Asleep`, `Display Power: state=OFF` (không dùng trong phiên này).

## 11. Đường dẫn bằng chứng (riêng tư, không commit)

- Tiny: `C:\Bots\_qa_handoff\E98-THEME-ANH-BIA\` — APK, `shots\00…32_*.png`, `logcat_meloRowArt_sony.txt` (chỉ số đếm).
- Ni: `docs/plans/artifacts/E98-THEME-ANH-BIA/MANIFEST.txt` (thư mục này bị `.gitignore`, chỉ có hash; ảnh chứa tên bài/kênh nên không đưa vào repo).
- Bundle: `D:\Bots\_transfer\MeloVault_E98-THEME-ANH-BIA_826d166-8654323_delta_20261025.bundle` (+ `.sha256`) trên Ni.

---

## APK QA cần ChatGPT bàn giao

| Mục | Giá trị |
|---|---|
| Commit nguồn APK | `8654323` (nhánh `dot/E98-THEME-ANH-BIA`) |
| Đường dẫn APK (đã kiểm tồn tại) | `C:\Bots\_qa_handoff\E98-THEME-ANH-BIA\MeloVault-E98-8654323-vc112-TinySigned-debug.apk` (máy Tiny) |
| Gói | `app.melovault.player` |
| versionCode / versionName | 112 / `0.6.89-beta` |
| SHA-256 | `B228085997E00F9C0F8CEE6E71EFF334A872513EBDD81E1B3B9BD6366E6D7235` (tính lại bằng `certutil` ngày 25/10/2026, khớp) |
| Kích thước | 33.396.676 byte |
| SHA-256 chứng chỉ | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` |
| Loại build | Debug (flavor `shareable`, ký bằng debug key) |
| Cấu hình Telegram | true |
| Gradle test | Debug và Release: 2033 định nghĩa / 2012 qua / 21 bỏ qua / 0 lỗi |
| ADB | Sony QV725WEQ54: cài `-r`, dọc/ngang/lọc/A–Z/ảnh bìa/tắt ảnh bìa/so sánh Hiện tại PASS; Shanling: BLOCKED |
| Chưa kiểm | hồi quy Gọn trên Shanling; Y700, S25 Ultra; âm thanh thật ở Hai khung; S6; độ chật khung trái ở chiều cao ≤ 360 dp |
