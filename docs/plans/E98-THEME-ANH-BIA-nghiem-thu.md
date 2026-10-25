# E98-THEME-ANH-BIA — Biên bản nghiệm thu (Opus)

**Vai trò:** Claude Opus nghiệm thu độc lập, 25/10/2026, theo `docs/workflow/PROMPT_OPUS_NGHIEM_THU.md` và `CLAUDE.md` của nhánh `dot/workflow-opus-nghiem-thu` (`ffcc584`).

| Vòng | Thời điểm | Trạng thái |
|---|---|---|
| 1 | 25/10 ~13:35 | BLOCKED — CHỜ CHỦ DỰ ÁN (kỹ thuật đạt, chờ duyệt ảnh) |
| 1 — phản hồi chủ dự án | 25/10 13:39 | "Về đa phần đã đạt" + báo lỗi: bài đang phát ở thanh dưới nhỏ quá, rất khó nhấn |
| REWORK-01 bản đầu | 25/10 13:46 (`0bab5cc`) | chỉ cho Teal sáng — **đã thay** |
| Chủ dự án chốt thêm | 25/10 | "Tôi muốn thẻ lớn cho tất cả các giao diện"; Shanling: **giữ đủ 8 bài** |
| **Hiện tại** | 25/10 | **REWORK-01** — sửa đúng mục 10, phiên Sonnet MỚI trên `dot/E98-THEME-ANH-BIA` |

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
| Test Debug/Release xanh, không giảm số test | **ĐẠT** | Opus tự chạy lại (mục 4): 2033 / 2012 qua / 21 bỏ qua / 0 lỗi mỗi biến thể; nền 1975 (21 bỏ qua) → +58 |
| Build OK | **ĐẠT** | `BUILD SUCCESSFUL in 1m 46s` (assembleShareableDebug/Release) |
| CURRENT không đổi | **ĐẠT có giới hạn** | Ảnh 01 và 24 (Hiện tại, vc112) đúng bố cục cũ; mã mới đều sau nhánh `teal`. REWORK-01 sẽ đổi thanh đáy của cả Hiện tại theo yêu cầu chủ dự án |
| Theme hiện đủ §3.3–3.5 | **ĐẠT trên Sony** | Ảnh 06/15, 08/11, 13, 14, 16, 17/18, 28 |
| ≥ 80 % dòng có ảnh thật/ảnh kênh sau 5 s | **ĐẠT** | logcat: 384/448 = 85,7 % toàn phiên; khung cuối 9/9 |
| Không ANR/crash khi cuộn 25.292 bài | **ĐẠT** | crash buffer / dropbox rỗng (báo cáo §8) |
| Janky không tăng > 5 điểm | **ĐẠT** | Teal 16,57 % vs Hiện tại 20,57 % cùng kịch bản (−4,0) |
| Request TDLib có giới hạn | **ĐẠT** | logcat inflight tối đa 3; Scheduler LIFO tối đa 3, watchdog 20 s |
| Không log PII | **ĐẠT** | 130/130 dòng `MeloRowArt` chỉ là số đếm |
| Gọn/Car không đổi | **CHƯA KIỂM TRÊN THIẾT BỊ** | Mã + test: `MeloThemePolicy.effective` trả CURRENT cho COMPACT/CAR. Shanling chưa cài |
| Ảnh sai dòng / đổi DB-schema-Media3-sync | **Không thấy** | Ảnh khớp tiêu đề; diff không đụng DB/schema/Media3/sync |
| Chủ dự án duyệt ảnh từ xa | **ĐA PHẦN ĐẠT, 1 LỖI** | Phản hồi 13:39 kèm ảnh điện thoại Samsung của chủ dự án (17.080 bài): thanh đáy → mục 10 |

## 3. Đọc mã rủi ro (TDLib / account / cancel)

- `TelegramPreviewActivity.onNativeEvent`: tag `rowArt*` xử lý **đầu tiên** rồi `return`; `updateFile` chỉ chuyển tiếp thêm cho loader.
- File thumbnail không kích hoạt phát nhạc: `playIfDownloaded`/`observePrefetch` lọc đúng file id của bài.
- `MeloRowArtLoader`: trạng thái trên luồng chính, phản hồi TDLib được `post` về; khóa có `accountId`; job mang `account` + `generation`; `reset()` khi đăng xuất (luồng chính), tắt công tắc, đổi theme; `shutdown()` ở `onDestroy`. Không ghi/xóa đĩa; giải mã 1 luồng nền, ≤ 192 px RGB_565.
- Ghi chú nhỏ (không chặn): khi watchdog 20 s nhả job, `fileWaiters` của job đó còn giữ nên `updateFile` vẫn được post về luồng chính tới khi `reset()` — chi phí nhỏ (`MeloRowArtLoader.watchdogFired`).

## 4. Test và build Opus tự chạy lại (Ni, 13:21–13:23)

`gradlew :app:testShareableDebugUnitTest --rerun :app:testShareableReleaseUnitTest --rerun :app:assembleShareableDebug :app:assembleShareableRelease` → `BUILD SUCCESSFUL in 1m 46s`. XML `app/build/test-results`: Debug 2033 / 2012 / 21 bỏ qua / 0 lỗi; Release giống hệt. Khớp báo cáo Sonnet.

## 5. APK candidate vòng 1 (CLAUDE.md §9)

vc112 / `0.6.89-beta` @ `8654323`, SHA-256 `B228085997E00F9C0F8CEE6E71EFF334A872513EBDD81E1B3B9BD6366E6D7235`, 33.396.676 byte, cert `eecd3df7…c3fce8`; trùng `base.apk` trên Sony; Telegram config = true (chức năng). Drive owner-only: `docs/plans/E98-THEME-ANH-BIA-apk-test.md`. Nhãn: QA CANDIDATE — CHƯA NGHIỆM THU.

## 6. Ảnh

Opus ghép `00_tong_hop.png` (8 ô) từ ảnh gốc Sony; bản sao ở `docs/plans/artifacts/E98-THEME-ANH-BIA/` (bị `.gitignore`). Link: `docs/plans/E98-THEME-ANH-BIA-hinh-nghiem-thu.md`.

## 7. Quan sát (không phải lỗi E98)

- Viên chất lượng không hiện ở Hai khung trên Sony: quy tắc cửa sổ thấp E95 (`MeloTabletScreen.qualityVisibility`). Xem trên Y700.
- 4 ô menu nhãn ngắn khi khung < 520 dp (đúng kế hoạch).
- A–Z "L" mở đầu bằng "I Am Louisvuitonmusic 2026": khóa sắp xếp tên có từ trước E98 (giống ở giao diện Hiện tại, ảnh 24).
- Dòng bài không hiện thời lượng khi dữ liệu không có `durationSeconds` (không hardcode).

## 8. Giới hạn / chưa kiểm chứng

- Hồi quy Gọn trên Shanling chưa chạy (Shanling đang vc109 `0.6.88-beta-syncqa`); Car Mode chưa chạy trên thiết bị.
- Y700 / S25 / FYT: chỉ có 1 ảnh từ điện thoại Samsung của chủ dự án. **CHƯA XÁC NHẬN ÂM THANH THỰC.**

## 9. Kết luận

**REWORK-01.** Các phần khác chủ dự án chấp nhận ("đa phần đã đạt"); chỉ sửa mục 10, không mở rộng phạm vi.

## 10. REWORK-01 — Thẻ "Đang phát" lớn ở Thanh đáy gộp, MỌI giao diện

**Lỗi (chủ dự án, ảnh 13:39, điện thoại dọc):** dòng "tên bài · kênh" phía trên ⏮▶⏭ quá nhỏ, rất khó nhấn để mở màn Đang phát. Chủ dự án chốt: **thẻ lớn cho tất cả các giao diện**; Shanling **giữ đủ 8 bài**.
**Nguyên nhân (Opus đọc mã):** `MeloBottomBar` (Thanh đáy gộp, có từ trước E98) chỉ dành dải `titleLineDp` 18/22/24 dp (Gọn/Điện thoại/Tablet dọc), chữ 13–15 sp; hàng dưới là tab + ⏮▶⏭ giữ hết cú chạm ⇒ vùng mở Đang phát ~20–26 dp, dưới mức chạm tối thiểu 48 dp (`MeloBottomBarPolicy.MIN_TOUCH_DP`; `docs/ui-spec.md`: "Không làm nút nhỏ hơn 48dp").
**Phạm vi:** cả theme Hiện tại và Teal sáng; hồ sơ Gọn (Shanling), Điện thoại dọc, Tablet dọc. Car Mode (`spec(CAR) = null`) và Hai khung ngang không có thanh này ⇒ không đổi.

**Phải làm:**
1. Thay dải tên bằng **thẻ Đang phát** theo bảng; thanh đáy mới = 2 dp tiến trình + thẻ + hàng nút cũ (hàng nút giữ nguyên chiều cao, `playDp`, `skipDp`, tab):

| Hồ sơ | Thẻ (tối thiểu) | Ảnh bìa | Tên bài (đậm) | Dòng kênh | Thanh đáy |
|---|---|---|---|---|---|
| Gọn (Shanling) | 48 dp | 36 dp | 14 sp | 12 sp | 2 + 48 + 52 = **102 dp** (cũ 72) |
| Điện thoại dọc | 56 dp | 40 dp | 15 sp | 12 sp | 2 + 56 + 64 = **122 dp** (cũ 88) |
| Tablet dọc | 60 dp | 44 dp | 16 sp | 13 sp | 2 + 60 + 70 = **132 dp** (cũ 96) |

2. Trong thẻ: trái là ảnh bìa `now.art` qua `MeloComponents.artView(...)` (không có ảnh → ô chữ cái sẵn có); giữa là tên bài 1 dòng, `…` cuối, dưới là kênh/nghệ sĩ màu `textSecondary`; phải là "⌃" ~24 dp. Nền `c.roundedBackground(...)` theo **palette của theme đang dùng** (Hiện tại dùng palette Hiện tại; Teal dùng palette teal).
3. **Bấm bất kỳ chỗ nào trên thẻ** → `onOpenNowPlaying()` (một lần); vuốt lên vẫn mở như cũ. Tab và ⏮▶⏭ giữ nguyên vị trí và hành động; bấm chúng **không** mở Đang phát.
4. Thanh tiến trình 2 dp ở mép trên. Chưa có bài: thẻ `INVISIBLE` giữ chỗ (như hiện nay). Thẻ cao tối thiểu theo bảng, tăng theo cỡ chữ hệ thống, không bao giờ < 48 dp. `contentDescription` = `MeloBottomBarPolicy.contentDescription(...)` hiện có.
5. **Gọn giữ 8 bài:** hạ chiều cao dòng hồ sơ COMPACT 56 → **52 dp** (`MeloDesignTokens.kt`, `rowDp` của bảng COMPACT; dùng qua `sizes.rowHeight`). Ảnh dòng Gọn giữ 40 dp. Các hồ sơ khác không đổi chiều cao dòng. Ngân sách: 574 − 48 − 102 − 2 = 422 ≥ 8 × 52 = 416.
6. Phiên bản: `app/build.gradle` vc113 / `0.6.89.1` + `-beta`.

**File được sửa (không file nào khác):** `MeloBottomBar.kt`, `MeloBottomBarPolicy.kt` (bảng `SPEC_*` + cỡ ảnh/chữ thẻ), `MeloDesignTokens.kt` (chỉ `rowDp` COMPACT), `MeloCompactScreen.kt` (chỉ chú thích "56dp … 72dp bar" ~dòng 253), `MeloBottomBarPolicyTest.kt`, `MeloDesignTokensTest.kt`, `app/build.gradle`, `docs/ui-spec.md` (bảng Thanh đáy gộp ~dòng 168–170, 198, 218, 248; ngân sách Shanling ~dòng 109–118). Ước lượng ≤ 120 dòng production.

**Unit test:** bảng spec mới (thanh đáy 102/122/132, thẻ 48/56/60; `playDp`/`skipDp`/hàng nút không đổi); thẻ ≥ 48 dp ở fontScale 1,0 và 1,3; ngân sách Shanling `visibleRows(574, 48, 102, 52, 0, pad) >= 8` và `(604, …) >= 8` (thay các assert 72/56 cũ); `compact.rowHeight == 52`, `compact.rowArt == 40`; `spec(CAR) == null`; `tabWidthDp` không đổi. Chỉ sửa đúng các assert của bảng cũ (liệt kê trong báo cáo); toàn bộ test xanh, số test không giảm.

**QA Sony QV725WEQ54 (Tiny, dọc), theo CLAUDE.md §3, §6, §7:**
1. Build ở Tiny (bundle SHA256), kiểm package/vc113/cert/config, `install -r` đè vc112.
2. Kiểu màu **Hiện tại**: phát **một bài trong "Đã tải"** (không làm tải bài mới) → ảnh thẻ; `uiautomator dump` bounds thẻ (content-desc "Đang phát: …") ≥ 56 dp × density, ghi số px.
3. Bấm tâm thẻ, gần mép trái (ảnh) và gần mép phải (⌃): mỗi lần mở Đang phát → ảnh → Back. Bấm ⏸ ở hàng dưới: chỉ tạm dừng; bấm tab Playlist: chỉ đổi tab; vuốt lên trên thẻ → mở Đang phát.
4. Kiểu màu **Teal sáng**: ảnh thẻ + 1 lần bấm thẻ.
5. **Gọn trên Sony:** Cài đặt giao diện → "Thử trong khung Shanling (768 × 1280)" → ảnh danh sách Gọn + thẻ, ghi số dòng thấy được (khung 640 dp phải ≥ 8); bấm thẻ mở Đang phát.
6. Trả cài đặt UiLab về đúng giá trị lúc bắt đầu; màn OFF trong `finally`.
Ghép `00_tong_hop.png`. Báo cáo: thêm mục **"REWORK-01"** vào `docs/plans/E98-THEME-ANH-BIA-bao-cao.md`, giữ nội dung vòng 1. Sonnet **không** cài Shanling, **không** upload APK.

**Opus nghiệm thu vòng sau sẽ:** chạy lại test/build, kiểm APK vc113, upload Drive owner-only, rồi tự làm hồi quy Gọn trên **Shanling thật** 30313860180939: chuyển APK qua Drive owner-only sang Ni, kiểm SHA/package/cert, chỉ `install -r` (vc113 thay vc109 syncqa, không hạ phiên bản), chụp thẻ + đủ 8 dòng + bấm thẻ, tắt màn.

**Bác sĩ tự kiểm tra (sau khi cài vc113):** (1) Điện thoại, cả Hiện tại và Teal: phát một bài → thẻ ở đáy có ảnh nhỏ + tên bài chữ to. (2) Bấm bất kỳ chỗ nào trên thẻ → mở màn Đang phát. (3) Bấm ⏸ ở hàng dưới → chỉ dừng nhạc. (4) Shanling: vẫn thấy đủ 8 bài trong danh sách và thẻ dễ bấm.
