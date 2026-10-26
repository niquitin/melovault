# Nghiệm thu Opus — E101-ANH-BIA-NHAY (ảnh bìa dòng nhảy/đổi liên tục)

Ngày: 26/10/2026. Người nghiệm thu: Claude Opus (phiên mới, độc lập với phiên lập kế hoạch và Sonnet), theo `docs/workflow/PROMPT_OPUS_NGHIEM_THU.md` (nhánh `dot/workflow-opus-nghiem-thu`).

**Trạng thái: PASS** (chủ dự án "ĐẠT" 26/10/2026, xem mục 9; trước đó BLOCKED — CHỜ CHỦ DỰ ÁN ở `78186ae`).

## 1. Git

| Mục | Giá trị |
|---|---|
| Nhánh / HEAD lúc nghiệm thu | `dot/E101-ANH-BIA-NHAY` @ `1ec3170` (báo cáo Sonnet), cây sạch |
| Nền | `6ae510c` (`feature/e93-03d-y700-offline-cockpit`, sau merge E100) |
| Commit | `409466c` kế hoạch → `1047a22` S2 → `11c851a` S3 → `458b0fb` S4 → `00c4901` S5 vc117 → `1ec3170` báo cáo |
| `git diff --check 6ae510c..HEAD` | sạch |
| File đổi (`--stat`) | `MeloRowArtPolicy.kt`, `MeloRowArtLoader.kt`, `MeloComponents.kt`, `MeloCompactScreen.kt`, `MeloLibraryPane.kt`, `app/build.gradle`, `MeloRowArtPolicyTest.kt`, 2 tài liệu — **đúng mục 4 kế hoạch**, không đụng `TelegramPreviewActivity.kt`, `Scheduler`, hằng số giới hạn, service, DB, Car Mode |

## 2. Đọc mã (diff production ~130 dòng, đọc toàn bộ)

| Nguyên nhân (kế hoạch mục 2) | Mã | Nhận xét |
|---|---|---|
| A. mờ → nét khi ảnh nét đã có | `MeloRowArtLoader.kt:285-291` dùng `blurPlan`; `scheduleBlur` `:316-325` | Đúng thiết kế. Runnable kiểm `generation`, `alive()`, `cache.get(key) == null` ⇒ không đè ảnh nét. Hủy ở `deliver(final)` `:456`, `reset()` `:137-138`; `fail()` `:345-346` chạy ngay ảnh mờ đang chờ (làm luôn phần "lỗi vẫn hiện ảnh mờ", không cần đơn giản hóa). `resolveThumb`, `fail`, `deliver` đều chạy trên luồng chính (qua `main.post`) như `watchdogs` ⇒ `HashMap` an toàn |
| B. thứ tự gửi ngược | `request()` `:94-107` gom `messageJobs` rồi `sendOrder(...).forEach(::enqueue)` trước `retain` | Đúng; nhánh ảnh nhúng/ảnh máy giữ FIFO |
| C. hai nguồn ảnh | Compact `:282`, LibraryPane `:571` dùng `rowPicture(peekRowArt, row.art)` | Đúng; khi không Teal/không rowArt giữ `row.art` như cũ |
| D. ảnh bật giật | `MeloComponents.applyRowArt` `:214-231` trả `ArtChange`, mờ dần 150 ms chỉ khi `APPEAR` + `animate` | Đúng; `null` không lấy ảnh đi; `rowArtShowing` thay giá trị trả về cũ để đếm `withArt` |
| Log | `appeared=`/`swapped=` ở hai màn | Chỉ số đếm, không tên bài/khóa |

Không thấy lỗi chặn. Ghi chú nhỏ (không REWORK): nếu danh sách dựng lại đúng lúc ô chữ cái đang mờ dần thì view mới được tạo (ListView không tái dùng `convertView`), nên không kẹt `alpha`.

## 3. Test và build — Opus tự chạy lại trên Ni

Lệnh: `.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest :app:assembleShareableDebug :app:assembleShareableRelease --rerun-tasks --console=plain` (log ngoài repo). **BUILD SUCCESSFUL in 3m 15s**, 109 task chạy thật.

| Biến thể | tests | skipped | failures | errors |
|---|---|---|---|---|
| Debug | 2060 | 21 | 0 | 0 |
| Release | 2060 | 21 | 0 | 0 |

Khớp báo cáo Sonnet; nền 2055 + 5 test mới (đếm từ XML `MeloRowArtPolicyTest`, có đủ 5 tên). Debug/Release APK build OK (APK Ni không có Telegram config, không dùng để cài).

## 4. APK candidate (CLAUDE.md §9)

| Mục | Kiểm độc lập |
|---|---|
| Tệp | `MeloVault-E101-ANH-BIA-NHAY-00c4901-vc117-TinySigned-Debug.apk` (Ni `D:\Bots\_transfer\E101\`) |
| SHA-256 / bytes | `0EBA0E68…A6537CF` / 33.413.060 — trùng báo cáo, trùng bản trên Drive và **`base.apk` đang cài trên Shanling** (`sha256sum` trên máy) |
| `aapt2` | `app.melovault.player`, vc117, `0.6.92-beta` |
| `apksigner` | cert `eecd3df7…c3fce8` (như mọi bản trước); vc117 > vc116, không hạ phiên bản |
| Telegram config | true (log Tiny theo báo cáo; app trên Shanling hiển thị danh sách Telegram bình thường) |
| Drive | thư mục mới `MeloVault_QA/E101-ANH-BIA-NHAY-20261026-vc117-00c4901`, quyền chỉ owner (connector: thư mục, cha, APK, video) |

Link: **`E101-ANH-BIA-NHAY-apk-test.md`**; ảnh/video: **`E101-ANH-BIA-NHAY-hinh-nghiem-thu.md`**. Nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**.

## 5. Chứng cứ QA (Sonnet tự làm, Opus xem lại)

| # | Thiết bị | Kỳ vọng kế hoạch | Opus đối chiếu | Kết luận |
|---|---|---|---|---|
| 1 | Shanling vc116 | chỉ để so sánh | ảnh ghép `01_*_sheet.png`: ảnh điền lộn xộn, chưa thấy rõ dòng đổi qua lại; log vc116 không có bộ đếm | ghi nhận (yếu, xem mục 6) |
| 2 | Shanling vc117 | 8 dòng, dòng đầu có ảnh, không crash | `02_*.png`: 8 dòng đủ ảnh, Gọn | ĐẠT |
| 3 | Shanling vc117 | không dòng có 2 ảnh khác; điền trên → dưới; `swapped` ≤ 10% `appeared`; 0 FATAL/ANR | Opus tự cộng log `03b_*_log.txt`: 34 dòng `MeloRowArt`, **appeared=15, swapped=0**, 0 FATAL/ANR; ảnh ghép 03b: vuốt nhanh chỉ ô chữ cái, dừng tay mỗi dòng đổi **một lần** sang ảnh rồi đứng yên | ĐẠT |
| 4 | Shanling vc117 | ảnh về đầu giống bước 2 | ảnh 04 giống 02 (Sonnet SSIM 1.0; Opus nhìn ảnh tổng hợp) | ĐẠT |
| 5 | Shanling vc117 | ảnh giống hệt qua 3 lần đổi tab; `swapped=0` | 05a/05b/05c **trùng từng byte** (cùng SHA-256 `b9af7825…`); log trong lúc này 0 dòng refresh ⇒ không có đổi ảnh | ĐẠT |
| 6 | Sony vc117 | như bước 3 trên `MeloLibraryPane` | Sony không ở Teal ⇒ không có ảnh bìa dòng; chỉ khói (vuốt bình thường, 0 FATAL/ANR) | **CHƯA KIỂM** (không chặn PASS; Sonnet đúng khi không đổi theme) |

Màn hình: `SHANLING_SCREEN_END` OFF (Opus kiểm lại ~10:15: `mGlobalDisplayState=OFF`, `mWakefulness=Asleep`); `SONY_SCREEN_END` OFF theo báo cáo. Shanling không phát nhạc (PlaybackState state=0). Opus không chạy lại QA thiết bị (chứng cứ đủ, không mâu thuẫn).

## 6. Đối chiếu tiêu chí PASS (kế hoạch mục 8)

| Tiêu chí | Kết quả |
|---|---|
| Test 0 lỗi, ≥ 2055 + mới, skipped 21; build OK; `diff --check` sạch; đúng file | ĐẠT |
| Shanling bước 2–5: không dòng 2 ảnh khác; `swapped` ≤ 10%; bước 5 giống hệt + `swapped=0`; 8 dòng; không crash/ANR | ĐẠT |
| Ảnh ghép sau ít đổi hình hơn rõ rệt so với trước | **Một phần**: "sau" rõ ràng (mỗi dòng một lần); "trước" của Sonnet không tái hiện mạnh lỗi do bộ đệm ấm. Bằng chứng "trước" mạnh hơn là video Opus quay lúc lập kế hoạch (09:19, 2–3 lần đổi/dòng, `withArt` 8→2→9). Để chủ dự án chấm bằng mắt |
| Chủ dự án nói ĐẠT | **ĐẠT** (26/10, mục 9) |

Không có tiêu chí FAIL nào bị vi phạm.

## 7. Giới hạn chưa kiểm chứng

- Sony/Teal đường `MeloLibraryPane` (Điện thoại dọc, Tablet, Hai khung) chưa kiểm trên máy; mã đổi đối xứng với Gọn.
- Nhánh ảnh mờ hoãn 400 ms và mờ dần 150 ms chưa đo trên máy (chỉ unit test hằng số/quy tắc); mạng hôm nay nhanh, nhiều ảnh nằm sẵn trên đĩa.
- Mạng chậm: `swapped` (mờ → nét) có thể tăng, đúng thiết kế.
- Y700, S25, màn xe FYT: chủ dự án tự xem. Car Mode không dùng loader này.
- Âm thanh: không liên quan.

## 8. Kết luận

**BLOCKED — CHỜ CHỦ DỰ ÁN.** Mã đúng phạm vi và đúng 4 nguyên nhân, test/build Opus tự chạy xanh (2060/21/0 mỗi biến thể), APK đạt gate và đã lên Drive owner-only, QA Shanling bước 2–5 đạt bằng số đếm và ảnh. Chủ dự án xem video `03b` (hoặc dùng vc117 đã cài sẵn trên Shanling) theo 5 bước "Bác sĩ tự kiểm tra" của kế hoạch, rồi nói **ĐẠT** hoặc báo lỗi. Khi ĐẠT: đổi thành PASS, xin xác nhận rồi `git merge --no-ff` vào `feature/e93-03d-y700-offline-cockpit`.

## 9. Chủ dự án duyệt — PASS

- 26/10/2026 chủ dự án trả lời trong phiên Opus nghiệm thu: **"ĐẠT — đổi E101 thành PASS và merge."** (sau khi nhận ảnh tổng hợp, ảnh ghép trước/sau và video `03b`, link Drive owner-only).
- **E101-ANH-BIA-NHAY: PASS** trong phạm vi đã kiểm (Shanling Gọn bước 2–5). Giới hạn mục 7 giữ nguyên: Sony/Teal `MeloLibraryPane`, Tablet/Hai khung, ảnh mờ hoãn 400 ms và mờ dần 150 ms trên máy, Y700/S25/FYT chưa kiểm.
- APK đã duyệt: vc117 / `0.6.92-beta` @ `00c4901`, SHA-256 `0EBA0E68…A6537CF` (`E101-ANH-BIA-NHAY-apk-test.md`).
- Merge `--no-ff` vào `feature/e93-03d-y700-offline-cockpit` theo yêu cầu chủ dự án; merge commit ghi ở `HANDOFF.md`. Chưa push.
