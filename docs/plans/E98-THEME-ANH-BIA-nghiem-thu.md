# E98-THEME-ANH-BIA — Biên bản nghiệm thu (Opus)

**Vai trò:** Claude Opus nghiệm thu độc lập, 25/10/2026, theo `docs/workflow/PROMPT_OPUS_NGHIEM_THU.md` và `CLAUDE.md` của nhánh `dot/workflow-opus-nghiem-thu` (`ffcc584`).
**Trạng thái: BLOCKED — CHỜ CHỦ DỰ ÁN** (kỹ thuật đạt; chưa có chủ dự án duyệt ảnh, hồi quy Gọn trên Shanling chưa chạy).

## 1. Git

| Mục | Kết quả |
|---|---|
| Nhánh / HEAD lúc nghiệm thu | `dot/E98-THEME-ANH-BIA` @ `c462ead` (báo cáo Sonnet), cây sạch, commit cuối 11:34 — không agent nào đang chạy |
| Nền | `826d166` (vc111) → kế hoạch `25ee89d` → S1 `60cbc58`, S2 `cea4634`, S3 `9d60f5e`, S4 `48986ac`, S5 `9afc326`, S7 `8654323`, báo cáo `c462ead` |
| `git diff --stat 826d166..HEAD` | 28 file, +2814/−87 (26 file mã/test + kế hoạch + báo cáo) |
| `git diff --check 826d166..HEAD` | sạch |
| File ngoài phạm vi | **không có** — 17 file production + 2 vector + `app/build.gradle` đều nằm trong bảng §4 kế hoạch; 7 file test đúng §6 |
| S6 (nền ảnh mờ) | không làm — kế hoạch cho phép bỏ |

## 2. Bảng kiểm theo tiêu chí PASS/FAIL (kế hoạch §8)

| Tiêu chí | Kết quả | Bằng chứng |
|---|---|---|
| Test Debug/Release xanh, không giảm số test | **ĐẠT** | Opus tự chạy lại (mục 4): 2033 / 2012 qua / 21 bỏ qua / 0 lỗi mỗi biến thể; nền 1975 (21 bỏ qua) → +58, không thêm test bị bỏ qua |
| Build OK | **ĐẠT** | `BUILD SUCCESSFUL in 1m 46s` (assembleShareableDebug/Release) |
| CURRENT không đổi | **ĐẠT có giới hạn** | Ảnh 01 và 24 (Hiện tại, vc112) đúng bố cục cũ: hàng chip Chillout/LakHouse, ô chữ cái, không ảnh bìa; mã mới đều sau nhánh `teal`. Không có ảnh vc111 trước khi cài để so pixel |
| Theme hiện đủ §3.3–3.5 | **ĐẠT trên Sony** | Ảnh 06/15 (dòng có ảnh bìa, dòng đang phát gradient + ‖), 08/11 (bảng lọc, chip "Chillout ✕"), 13 (Nguồn), 14 (Đã tải ✓), 16 (A–Z), 17/18 (Hai khung: logo hai màu, tiến trình chỉ hiển thị, 3 nút, 2×2, không cột điều hướng), 28 (Kiểu màu + công tắc) |
| ≥ 80 % dòng có ảnh thật/ảnh kênh sau 5 s | **ĐẠT** | logcat: 384/448 = 85,7 % toàn phiên; khung cuối 9/9 |
| Không ANR/crash khi cuộn 25.292 bài | **ĐẠT** | crash buffer / dropbox rỗng (báo cáo §8) |
| Janky không tăng > 5 điểm | **ĐẠT** | Teal 16,57 % vs Hiện tại 20,57 % cùng kịch bản (−4,0) |
| Request TDLib có giới hạn | **ĐẠT** | Opus đọc 130 dòng logcat: inflight tối đa 3; mã: Scheduler LIFO tối đa 3, watchdog 20 s |
| Không log PII | **ĐẠT** | 130/130 dòng `MeloRowArt` chỉ là số đếm (Opus kiểm bằng regex) |
| Gọn/Car không đổi | **CHƯA KIỂM TRÊN THIẾT BỊ** | Mã + test: `MeloThemePolicy.effective` trả CURRENT cho COMPACT/CAR. Shanling BLOCKED (báo cáo §4) |
| Ảnh sai dòng / đổi DB-schema-Media3-sync | **Không thấy** | Ảnh khớp tiêu đề (vd. "Việt Mix – Tuyết Yêu Thương"); diff không đụng DB/schema/Media3/sync |
| Chủ dự án duyệt ảnh từ xa | **CHỜ** | Ảnh đã gửi trong chat 25/10 |

## 3. Đọc mã rủi ro (TDLib / account / cancel)

- `TelegramPreviewActivity.onNativeEvent`: tag `rowArt*` xử lý **đầu tiên** rồi `return`, nên không lọt vào handler `message/chat/file`. `updateFile` chỉ chuyển tiếp thêm cho loader (chỉ đọc khi đang chờ file).
- File thumbnail không kích hoạt phát nhạc: `playIfDownloaded` và `observePrefetch` lọc đúng `downloadWaiting`/`prefetchWaiting` (file id của bài).
- `MeloRowArtLoader`: mọi trạng thái trên luồng chính, phản hồi TDLib được `post` về; khóa có `accountId`; job mang `account` + `generation`; `reset()` khi đăng xuất (2 chỗ, đều trên luồng chính), khi tắt công tắc/đổi theme; `shutdown()` ở `onDestroy`. Không ghi/xóa đĩa; giải mã 1 luồng nền ưu tiên thấp, ≤ 192 px RGB_565, kiểm bounds trước.
- Bài đã tải: `isOwnedFile` trước `MediaMetadataRetriever`; bài trên máy: `loadThumbnail` chỉ API ≥ 29.
- Ghi chú nhỏ (không chặn): khi watchdog 20 s nhả job, `fileWaiters` của job đó vẫn giữ nên `hasFileWaiters` có thể còn `true` và mỗi `updateFile` vẫn được post về luồng chính tới khi `reset()` — chi phí nhỏ, không sai kết quả (`MeloRowArtLoader.kt`, `watchdogFired`).

## 4. Test và build Opus tự chạy lại (Ni, 13:21–13:23)

`.\gradlew.bat :app:testShareableDebugUnitTest --rerun :app:testShareableReleaseUnitTest --rerun :app:assembleShareableDebug :app:assembleShareableRelease --console=plain` → `BUILD SUCCESSFUL in 1m 46s`. Số từ XML `app/build/test-results` (166 file mỗi biến thể, ghi lúc 13:22 và 13:23):

| Biến thể | Tests | Qua | Bỏ qua | Lỗi |
|---|---|---|---|---|
| Debug | 2033 | 2012 | 21 | 0 |
| Release | 2033 | 2012 | 21 | 0 |

Khớp báo cáo Sonnet. Đây là test JVM; không chứng minh UI/TDLib thật.

## 5. APK candidate (CLAUDE.md §9)

Opus tự kiểm trên Tiny: SHA-256 `B228085997E00F9C0F8CEE6E71EFF334A872513EBDD81E1B3B9BD6366E6D7235`, 33.396.676 byte; `aapt2`: `app.melovault.player`, vc112, `0.6.89-beta`; `apksigner verify`: hợp lệ, cert SHA-256 `eecd3df7…c3fce8` (debug, khớp các bản trước). `base.apk` đang cài trên Sony QV725WEQ54 có **cùng SHA-256** (cài 11:16). Telegram config = true: chứng minh bằng chức năng (Sony đăng nhập, 25.292 bài, 189 `getMessage` ảnh bìa thành công) — không đọc giá trị. Đã upload Drive owner-only, xem `docs/plans/E98-THEME-ANH-BIA-apk-test.md`. Nhãn: **QA CANDIDATE — CHƯA NGHIỆM THU**.

## 6. Ảnh

Bản Sonnet theo mẫu cũ nên chưa có ảnh tổng hợp. Opus ghép `00_tong_hop.png` (1570×1008, 8 ô) từ ảnh gốc Sony, không sửa ảnh gốc; bản sao ở `docs/plans/artifacts/E98-THEME-ANH-BIA/00_tong_hop.png` (bị `.gitignore`). Opus đã xem thêm 12 ảnh gốc (04–31). Link: `docs/plans/E98-THEME-ANH-BIA-hinh-nghiem-thu.md`.

## 7. Quan sát (không phải lỗi E98)

- **Viên chất lượng `MP3 • … kbps` không hiện ở Hai khung trên Sony:** đúng quy tắc cửa sổ thấp có từ E95 (`MeloTabletScreen.qualityVisibility(tight)` ẩn khi máy ngang thấp ~411 dp). Phải xem trên Y700 (bước 3 "Bác sĩ tự kiểm tra").
- 4 ô menu dùng nhãn ngắn ("Tất cả", "Nguồn") vì khung phải < 520 dp trên Sony; nhãn đầy đủ + số "(25K)" chỉ hiện khi khung rộng hơn.
- A–Z bấm "L" hiện trước tiên các bài "I Am Louisvuitonmusic 2026": khóa sắp xếp tên có sẵn từ trước E98 (cùng thứ tự trong giao diện Hiện tại, ảnh 24), E98 chỉ đổi điều kiện hiện thanh.
- Dòng bài chưa hiện thời lượng: mã chỉ hiện khi có `durationSeconds`; dữ liệu Telegram hiện tại không có → không hiện (không hardcode).
- Tác dụng phụ QA: Sonnet bấm nhầm phát một bài Telegram trên Sony (bài được tải về), đã tạm dừng; hàng đợi Sony đổi. Không xóa dữ liệu.

## 8. Giới hạn / chưa kiểm chứng

- **Hồi quy Gọn trên Shanling: BLOCKED.** Shanling 30313860180939 đang chạy vc109 `0.6.88-beta-syncqa`; chưa cài vc112. Car Mode chưa chạy trên thiết bị.
- Y700 / S25 / FYT chưa thử (viên chất lượng, khung trái khi máy cao, nhãn đầy đủ).
- **CHƯA XÁC NHẬN ÂM THANH THỰC** (Media3 state=3 chỉ chứng minh luồng phát).
- Không có ảnh vc111 trước khi cài để so pixel "Hiện tại".
- Janky đo bằng `adb input swipe` tổng hợp, dao động giữa các lượt.
- Màn hình: Sony `Display State=OFF` (Opus kiểm 13:27); Shanling `mWakefulness=Asleep` (13:30). Opus không đánh thức thiết bị nào.

## 9. Kết luận và điều kiện để PASS

**BLOCKED — CHỜ CHỦ DỰ ÁN.** Không có lỗi nào buộc REWORK. Để chuyển PASS cần:
1. Chủ dự án xem ảnh/APK và trả lời **ĐẠT** hoặc báo lỗi.
2. Chủ dự án chọn cho gate Gọn/Shanling: (a) chấp nhận bằng chứng mã + test, hoặc (b) cho phép cài vc112 lên Shanling (thay bản vc109 syncqa) để chạy hồi quy Gọn.
Sau khi có ĐẠT: Opus ghi PASS, xin xác nhận merge `--no-ff` vào nhánh tích hợp đã thống nhất.
