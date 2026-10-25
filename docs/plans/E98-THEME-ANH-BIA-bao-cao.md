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

---

# REWORK-01 (25/10/2026) — Thẻ "Đang phát" lớn trong Thanh đáy gộp, mọi giao diện

Phiên Sonnet GỘP (code + test Ni + build Tiny + QA Sony), theo `docs/plans/E98-THEME-ANH-BIA-nghiem-thu.md` mục 10 và luật mới `dot/workflow-opus-nghiem-thu` (CLAUDE.md, PROMPT_SONNET.md). Nội dung vòng 1 ở trên giữ nguyên. Không mở kế hoạch mới.

## R1. Git

| Mục | Giá trị |
|---|---|
| Nhánh / HEAD lúc bắt đầu | `dot/E98-THEME-ANH-BIA` @ `2f00755`, cây sạch, `826d166` là tổ tiên, `git diff --check` sạch. Tiến trình `java` 09:42 là daemon Gradle cũ của toolchain, không có agent khác đang sửa |
| Commit code | `48449ab` — 8 file, +193/−52 (production ≈ +100/−23, ~123 dòng; kế hoạch ước ≈ 120) |
| Commit báo cáo | commit kế tiếp (chỉ file này) |
| File sửa (đúng danh sách mục 10) | `MeloBottomBar.kt`, `MeloBottomBarPolicy.kt`, `MeloDesignTokens.kt` (chỉ `rowDp` Gọn 56→52), `MeloCompactScreen.kt` (chỉ chú thích), `MeloBottomBarPolicyTest.kt`, `MeloDesignTokensTest.kt`, `app/build.gradle` (vc113 / `0.6.89.1-beta`), `docs/ui-spec.md`. Car Mode và Hai khung không đụng |

## R2. Đã làm so với mục 10

| # | Yêu cầu | Kết quả |
|---|---|---|
| 1 | Bảng thẻ/thanh đáy | Gọn 102 dp (2+48+52), Điện thoại 122 (2+56+64), Tablet dọc 132 (2+60+70); hàng nút giữ 52/64/70, `playDp`/`skipDp`/tab không đổi. Ảnh 36/40/44 dp, tên bài 14/15/16 sp đậm, kênh 12/12/13 sp |
| 2 | Nội dung thẻ | Trái: `MeloComponents.artView(...)` (không ảnh → ô chữ cái sẵn có); giữa: tên bài 1 dòng, "…" cuối; dưới: kênh/nghệ sĩ màu `textSecondary`; phải: "⌃" 24 dp. Nền `c.roundedBackground(p.surface, stroke = p.border)` theo `c.palette` của giao diện đang dùng (Hiện tại hoặc Teal) |
| 3 | Bấm | Cả thẻ → `onOpenNowPlaying()` một lần; vuốt lên vẫn mở; tab và ⏮ ⏯ ⏭ không đổi |
| 4 | Trạng thái | Tiến trình 2 dp ở mép trên; chưa có bài: thẻ `INVISIBLE` giữ chỗ; chiều cao thẻ = `cardHeightDp(spec, fontScale)` ≥ bảng, tăng theo cỡ chữ, không dưới 48 dp; `contentDescription` = `MeloBottomBarPolicy.contentDescription(...)` (nay gắn trên thẻ để bấm/đọc đúng vùng) |
| 5 | Gọn giữ 8 bài | `rowDp` 56→52, ảnh dòng 40 dp; ngân sách 574 − 48 − 102 − 2 = 422 ≥ 8 × 52 = 416 (có test) |
| 6 | Phiên bản | vc113 / `0.6.89.1-beta` |

Ghi chú thiết kế: bấm/vuốt được gắn ở cả thanh (như cũ) và thẻ (thẻ là view bấm được riêng, nên cần bộ nhận vuốt lên riêng, nếu không vuốt trên thẻ bị nuốt). Chữ "tên bài · kênh" cũ chỉ còn dùng cho `contentDescription`.

## R3. Test, build (Ni, log trong artifacts)

| Phép | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest` | 2038 định nghĩa / 2017 qua / 21 bỏ qua / 0 lỗi |
| `:app:testShareableReleaseUnitTest` | 2038 / 2017 / 21 / 0 (BUILD SUCCESSFUL 2m24s) |
| Nền vòng 1 | 2033 → +5 test mới (số test không giảm; 21 bỏ qua như cũ) |
| `:app:assembleShareableDebug` + `Release` | BUILD SUCCESSFUL 1m2s |
| `git diff --check` | sạch |

Test mới (`MeloBottomBarPolicyTest`, 19 test): bảng thẻ (cao/ảnh/chữ), hàng nút giữ 52/64/70, thẻ ≥ 48 dp ở font ×0,85 / ×1,0 / ×1,3 (Gọn ×1,3 → 53 dp, Điện thoại ×1,3 → 56 dp), `channelLine`, `tabWidthDp` không đổi (110 / 49 dp), Shanling `visibleRows(574, 48, 102, 52, 0, 2) = 8` và 604 → ≥ 8, `spec(CAR) == null` (đã có). Assert cũ chỉ sửa đúng bảng cũ: chiều cao 72/88/96 → 102/122/132, `titleLineDp` → `cardDp`, ngân sách 56/72 → 52/102; `MeloDesignTokensTest`: `rowHeight` Gọn 56→52 (và 112→104 ở density 2).

## R4. Build Tiny, gói, cài (CLAUDE.md §4, §6)

| Mục | Giá trị |
|---|---|
| Cách chuyển code | **Lệch quy trình, nêu rõ:** không có đường chuyển bundle hợp lệ Ni→Tiny trong phiên này (Tiny không gắn ổ T:; ổ Google Drive dùng chung thì Sonnet không được ghi lên), nên chuyển **patch văn bản** `D:\Bots\_transfer\E98-THEME-ANH-BIA_REWORK01_2f00755-48449ab_code.patch` (23.379 byte, SHA-256 `C9AE9454858B9182203EEF0D5975B4EE15ABABD65B06247BED9F0FDBD540AA11`) qua kênh Desktop Commander tới `C:\Bots\_MeloVault_Git_Transfer\`. Bản chép đầu có 2 ký tự sai, **hash lệch nên bị phát hiện**, sửa rồi hash khớp từng byte. Áp vào clone cô lập `C:\Bots\MeloVault-E93D-IsolatedQA` (đang `8654323`, sạch) bằng `git apply --check` rồi `git apply`. **7 blob mã/test trên Tiny khớp hash blob trong `48449ab` của Ni** (`git hash-object`). Chưa commit trong clone Tiny; `docs/ui-spec.md` không cần cho build nên không chuyển |
| Cấu hình Telegram | `telegram.config.valid=true` (chỉ true/false; `local.properties` không đọc, không rời Tiny) |
| Build | Debug, `BUILD SUCCESSFUL in 51s` |
| Gói / phiên bản | `app.melovault.player`, vc113 / `0.6.89.1-beta` (aapt2) |
| Chứng chỉ | SHA-256 `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` — khớp bản vc112 đang cài, nâng cấp không hạ phiên bản |
| Cài | Sony QV725WEQ54 (XQ-AS52, Sony, Android 12), phiên Media3 đang tạm dừng (state 2) → `adb install -r` `Success`. Không uninstall, không `pm clear`, không xóa dữ liệu, không `-d` |
| Shanling | Không cài (đúng chỉ thị) |

## R5. QA Sony QV725WEQ54 (Tiny), 14:14–14:25

| # | Hành động | Kỳ vọng | Thực tế | KQ | Ảnh |
|---|---|---|---|---|---|
| 1 | Mở app, tab **Đã tải**, phát 1 bài đã tải (bài đầu, có ảnh bìa) — kiểu **Hiện tại** | Thẻ lớn ở đáy | Thẻ ảnh bìa + tên bài đậm "…" + kênh + "⌃"; bounds `[52,2058][1028,2205]` = 976 × 147 px = **56,0 dp** (density 2,625); nút Phát 157 px = 60 dp | PASS | 03b |
| 2 | Bấm giữa thẻ, sát mép trái (ảnh), sát mép phải (⌃); mỗi lần Back | Mỗi lần mở Đang phát, một Back là về danh sách | Cả 3 mở (`Hàng đợi` hiện), Back → danh sách | PASS | 04, 05, 06 |
| 3 | Bấm ⏸ ở hàng dưới; bấm tab Playlist; vuốt lên trên thẻ | Chỉ tạm dừng; chỉ đổi tab; vuốt mở Đang phát | Media3 3→2 (không mở Đang phát); chỉ chuyển sang Playlist, thẻ còn; vuốt mở Đang phát | PASS | 07, 08, 09 |
| 4 | **Teal sáng** (UiLab), ảnh thẻ, bấm thẻ | Thẻ theo palette teal, bấm mở | Cùng bounds 56,0 dp, nền teal, bấm mở Đang phát, Back về | PASS | 11, 12 |
| 5 | **Gọn** qua "Thử trong khung Shanling (768 × 1280)": danh sách + thẻ, bấm thẻ | 8 dòng; thẻ ≥ 48 dp | Khung density 2,0: **8 dòng đầy đủ** × 104 px (= 52 dp) + dòng 9 bị che một phần; thẻ 96 px = **48 dp**; thanh đáy 204 px = **102 dp**; bấm thẻ mở Đang phát của Gọn | PASS | 13, 14 |
| 6 | Trả UiLab; màn OFF | Về đúng giá trị đầu | `melovault_ui_lab.xml` giống hệt lúc đầu (`compact_mode=auto`, `color_theme=current`, `row_art_telegram=true`, `car_mode=off`, `library_sort=NAME`, `library_style=new`); xoay màn `0/1` như cũ | PASS | 18 |

Chỉ phát bài trong "Đã tải"; không chạm bài Telegram chưa tải. Ảnh tổng hợp: **`00_tong_hop.png`** (1600 × 1192, 8 ô theo số bước trên: 1 → 03b, 2 → 04, 3 → 07, 4 → 08, 5 → 09, 6 → 11, 7 → 13, 8 → 14).

Ghi chú quan sát: Media3 báo `state=3` rồi `state=2` sau ⏸ (vị trí 94.100 → 95.686 ms) — chỉ chứng minh luồng phát, **CHƯA XÁC NHẬN ÂM THANH THỰC**. Crash buffer cho MeloVault: 0; dropbox ANR/crash: 0 (dòng `FATAL EXCEPTION` duy nhất trong logcat là của `com.facebook.appmanager` lúc 00:00, không phải MeloVault).

## R6. Màn hình (SCREEN_LIFECYCLE_QA)

- `SONY_SCREEN_BEGIN 2026-10-25T14:14:36`; ảnh `01_launch_sony_141442` là ảnh đen 16 KB vì màn vẫn OFF (hàm kiểm trạng thái màn của tôi khớp nhầm dòng nên không gửi lệnh bật; đã sửa, ảnh `…141506` là ảnh đúng, vô hại).
- `SONY_SCREEN_END 2026-10-25T14:25:09` — `Display State=OFF` (`keyevent 223`, kiểm `dumpsys display`), `mWakefulness=Dozing`.
- Shanling: không dùng, không chạm. Build Gradle trên Ni không bật thiết bị nào.

## R7. Chưa kiểm chứng / rủi ro

- **Gọn trên Shanling thật chưa chạy** (đúng chỉ thị, Opus làm vòng sau). Khung giả lập cao 640 dp, cao hơn cửa sổ thật 574 dp. **Lưu ý ngân sách:** trong khung giả lập, phần trên danh sách chiếm 82 dp (đầu trang 48 dp + dòng ghi chú quét "65 nhóm / 97 bản offline… " ~34 dp). Với cửa sổ 574 dp: không có dòng ghi chú → 574 − 48 − 102 − 2 = 422 ≥ 416 (8 dòng, đúng kế hoạch); **có dòng ghi chú → 574 − 82 − 102 − 2 = 388 chỉ còn 7 dòng**. Dòng ghi chú có từ trước REWORK-01 (cùng mức chiếm chỗ khi thanh đáy 72 dp), nhưng Opus nên xem trên Shanling thật lúc đang/không quét offline.
- Tablet dọc (thanh 132 dp, thẻ 60 dp) mới kiểm bằng test, chưa trên Y700; Điện thoại kiểm trên Sony 411 dp (S25 chưa).
- Cỡ chữ hệ thống ×1,3 kiểm bằng test đơn vị (thẻ tự cao thêm), chưa đổi cỡ chữ trên máy thật.
- Thẻ có nền `surface` + viền; trong Teal sáng hơi chìm so với thanh. Đọc được, nhưng đây là chi tiết thẩm mỹ để chủ dự án xem ảnh 11.
- Tác dụng phụ trên Sony: Sony bật hộp thoại hệ thống "Cho phép truy cập?" (USB/MTP, `ConfirmMtpModeActivity`) đúng lúc tôi bấm bánh răng (không rõ nguyên nhân, có thể do cáp USB); tôi chọn **TỪ CHỐI** (ADB vẫn `device`). Hàng đợi tạm dừng cũ ("NST - 4H NOEL…") được thay bằng hàng đợi "Đã tải" (bài đầu, tạm dừng ở ~1:36). Số "Đã tải" 59 → 65 trong lúc quét offline đang chạy (84 → 97 bản xác minh), không phải do tải; tôi chỉ bấm bài trạng thái "Đã tải".
- Một ảnh có tên bài/kênh của người dùng nên chỉ ở Tiny (không commit).

## R8. Đường dẫn bằng chứng (riêng tư, không commit)

- Tiny `C:\Bots\_qa_handoff\E98-THEME-ANH-BIA-REWORK01\`: `00_tong_hop.png` (1.284.050 byte, SHA-256 `6604B49AAFC466B38BF485DECE6C0DB1C4537EED9649EC59881EE99335BBAFE1`), `shots\` (23 PNG), `tiny_build.log`, `qa_lib.ps1`, `make_sheet.ps1`, APK. Ảnh nhị phân không có đường chuyển hợp lệ sang Ni; MANIFEST (hash) ở Ni: `docs/plans/artifacts/E98-THEME-ANH-BIA/MANIFEST_REWORK01.txt`.
- Ni (gitignored): `docs/plans/artifacts/E98-THEME-ANH-BIA/rework01_gradle_unit.log`, `rework01_gradle_assemble.log`.

## R9. APK QA cần Opus nghiệm thu bàn giao (REWORK-01)

| Mục | Giá trị |
|---|---|
| Nhãn | **QA CANDIDATE — CHƯA NGHIỆM THU** |
| Commit nguồn | `48449ab` (nhánh `dot/E98-THEME-ANH-BIA`); APK dựng từ cây Tiny = `8654323` + patch có 7 blob khớp `48449ab` |
| APK (đã kiểm tồn tại, máy Tiny) | `C:\Bots\_qa_handoff\E98-THEME-ANH-BIA-REWORK01\MeloVault-E98-REWORK01-48449ab-vc113-TinySigned-debug.apk` |
| Gói / phiên bản | `app.melovault.player` / vc113 / `0.6.89.1-beta` |
| SHA-256 / byte | `200E6EBC501FED01954B78E8BAEA575D24811E3A8B7AD813B9C3905A450210F6` / 33.413.064 |
| SHA-256 chứng chỉ | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` |
| Loại build | Debug (flavor `shareable`, ký debug key) |
| Telegram config | true (chỉ boolean) |
| Gradle test | Debug và Release: 2038 / 2017 qua / 21 bỏ qua / 0 lỗi |
| ADB | Sony QV725WEQ54: `install -r` vc112 → vc113 OK; QA R5 PASS; Shanling: chưa cài |
| Chưa QA | Shanling thật (Gọn, 8 dòng khi có/không dòng ghi chú quét); Y700 (Tablet dọc); S25; cỡ chữ lớn trên máy; âm thanh thật |
| Không làm | Không upload, không đổi quyền Drive, không cài Shanling/FYT |
