# E98-THEME-ANH-BIA — Biên bản nghiệm thu (Opus)

**Vai trò:** Claude Opus nghiệm thu độc lập, 25/10/2026, theo `docs/workflow/PROMPT_OPUS_NGHIEM_THU.md` và `CLAUDE.md` của nhánh `dot/workflow-opus-nghiem-thu` (`ffcc584`).

| Vòng | Thời điểm | Trạng thái |
|---|---|---|
| 1 | 25/10 ~13:35 | BLOCKED — CHỜ CHỦ DỰ ÁN (kỹ thuật đạt, chờ duyệt ảnh) |
| 1 — phản hồi chủ dự án | 25/10 13:39 | "Về đa phần đã đạt" + báo lỗi: bài đang phát ở thanh dưới nhỏ quá, rất khó nhấn |
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
| Test Debug/Release xanh, không giảm số test | **ĐẠT** | Opus tự chạy lại (mục 4): 2033 / 2012 qua / 21 bỏ qua / 0 lỗi mỗi biến thể; nền 1975 (21 bỏ qua) → +58, không thêm test bị bỏ qua |
| Build OK | **ĐẠT** | `BUILD SUCCESSFUL in 1m 46s` (assembleShareableDebug/Release) |
| CURRENT không đổi | **ĐẠT có giới hạn** | Ảnh 01 và 24 (Hiện tại, vc112) đúng bố cục cũ; mã mới đều sau nhánh `teal`. Không có ảnh vc111 để so pixel |
| Theme hiện đủ §3.3–3.5 | **ĐẠT trên Sony** | Ảnh 06/15, 08/11, 13, 14, 16, 17/18, 28 |
| ≥ 80 % dòng có ảnh thật/ảnh kênh sau 5 s | **ĐẠT** | logcat: 384/448 = 85,7 % toàn phiên; khung cuối 9/9 |
| Không ANR/crash khi cuộn 25.292 bài | **ĐẠT** | crash buffer / dropbox rỗng (báo cáo §8) |
| Janky không tăng > 5 điểm | **ĐẠT** | Teal 16,57 % vs Hiện tại 20,57 % cùng kịch bản (−4,0) |
| Request TDLib có giới hạn | **ĐẠT** | logcat inflight tối đa 3; Scheduler LIFO tối đa 3, watchdog 20 s |
| Không log PII | **ĐẠT** | 130/130 dòng `MeloRowArt` chỉ là số đếm |
| Gọn/Car không đổi | **CHƯA KIỂM TRÊN THIẾT BỊ** | Mã + test: `MeloThemePolicy.effective` trả CURRENT cho COMPACT/CAR. Shanling BLOCKED |
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

## 10. REWORK-01 — Thẻ "Đang phát" ở thanh đáy dễ bấm (Teal sáng)

**Lỗi (chủ dự án, ảnh 13:39, điện thoại dọc, Teal sáng):** dòng "tên bài · kênh" phía trên ⏮▶⏭ quá nhỏ, rất khó nhấn để mở màn Đang phát.
**Nguyên nhân (Opus đọc mã):** `MeloBottomBar` (Thanh đáy gộp, có từ trước E98, dùng chung Gọn/Điện thoại/Tablet dọc) chỉ dành dải `titleLineDp` = 22 dp (PHONE) / 24 dp (TABLET), chữ 14–15 sp; hàng dưới là tab + ⏮▶⏭ giữ hết cú chạm ⇒ vùng mở Đang phát chỉ ~24 dp, dưới mức chạm tối thiểu 48 dp (`MeloBottomBarPolicy.MIN_TOUCH_DP`).

**Phải làm (chỉ khi `c.teal`, tức PHONE/TABLET theme Teal sáng):**
1. Thay dải tên bằng **thẻ Đang phát** cao tối thiểu **56 dp (PHONE) / 60 dp (TABLET)**, tăng theo cỡ chữ hệ thống, không bao giờ < 48 dp: trái là ảnh bìa vuông 40/44 dp bo góc (`now.art` qua `c.artView(...)`, không có thì ô chữ cái sẵn có); giữa 2 dòng: **tên bài đậm 15/16 sp**, 1 dòng, `…` cuối; dưới là kênh/nghệ sĩ 12 sp màu `textSecondary`; phải là biểu tượng mở rộng "⌃" ~24 dp. Nền `surface`/`accentSoft` bo góc, viền `border`.
2. **Bấm bất kỳ chỗ nào trên thẻ** → `onOpenNowPlaying()` (một lần); vuốt lên trên thẻ vẫn mở như cũ. Tab và ⏮▶⏭ giữ nguyên kích thước, vị trí, hành động; bấm chúng **không** mở Đang phát.
3. Giữ thanh tiến trình 2 dp (mép trên thẻ). Chưa có bài: thẻ ẩn nhưng giữ chỗ như hiện nay (`INVISIBLE`) để danh sách không nhảy.
4. `contentDescription` của thẻ = `MeloBottomBarPolicy.contentDescription(...)` hiện có.
5. **Theme Hiện tại, Gọn (Shanling) và Car: không đổi một pixel.** Giữ nguyên `SPEC_COMPACT/PHONE/TABLET`; thêm spec/hàm riêng cho teal (ví dụ `spec(profile, teal)`), Gọn luôn dùng spec cũ.
6. File được sửa: `MeloBottomBar.kt`, `MeloBottomBarPolicy.kt`, `MeloBottomBarPolicyTest.kt`, `app/build.gradle` (vc113 / `0.6.89.1` + `-beta`). Không sửa `MeloComponents.kt` hay file khác; ước lượng ≤ 90 dòng production.

**Unit test (`MeloBottomBarPolicyTest`):** spec không-teal của COMPACT/PHONE/TABLET đúng giá trị cũ (72/18/13, 88/22/14, 96/24/15); spec teal PHONE/TABLET: thẻ ≥ 56/60 dp, `playDp`/`skipDp` không đổi, tổng chiều cao = hàng nút cũ + tiến trình + thẻ; chiều cao thẻ ở fontScale 1,3 ≥ chiều cao ở 1,0 và ≥ 48; COMPACT với teal vẫn trả spec cũ. Toàn bộ test cũ xanh, số test không giảm.

**QA Sony QV725WEQ54 (Tiny, dọc, Teal sáng), theo CLAUDE.md §3, §6, §7:**
1. Build ở Tiny (bundle SHA256), kiểm package/vc113/cert/config, `install -r` đè vc112. Ảnh thanh đáy trước khi phát.
2. Phát **một bài trong "Đã tải"** (không làm tải bài mới). Ảnh thẻ Đang phát.
3. `uiautomator dump` lấy bounds của thẻ (content-desc "Đang phát: …"): chiều cao ≥ 56 dp × density; ghi số px.
4. Bấm tâm thẻ, gần mép trái (ảnh bìa) và gần mép phải (⌃): mỗi lần mở Đang phát → ảnh → Back.
5. Bấm ⏸ ở hàng dưới: chỉ tạm dừng, không mở Đang phát; bấm tab Playlist: chỉ đổi tab. Vuốt lên trên thẻ → mở Đang phát.
6. Đổi Kiểu màu = Hiện tại → thanh đáy giống ảnh 01/24 vòng 1; trả cài đặt UiLab về đúng giá trị lúc bắt đầu. Màn OFF trong `finally`.
Ghép `00_tong_hop.png`. Báo cáo: thêm mục **"REWORK-01"** vào `docs/plans/E98-THEME-ANH-BIA-bao-cao.md`, giữ nội dung vòng 1. Sonnet **không** cài Shanling, **không** upload APK.

**Opus nghiệm thu vòng sau sẽ:** chạy lại test/build, kiểm APK vc113, upload Drive owner-only, rồi tự làm hồi quy Gọn trên Shanling 30313860180939 (vì thanh đáy dùng chung với Gọn): chuyển APK qua Drive owner-only sang Ni, kiểm SHA/package/cert, chỉ `install -r` (vc113 thay vc109 syncqa, không hạ phiên bản), chụp 2–3 ảnh Gọn, tắt màn.

**Bác sĩ tự kiểm tra (điện thoại dọc, sau khi cài vc113):** (1) Phát một bài → thẻ Đang phát ở đáy có ảnh nhỏ + tên bài chữ to. (2) Bấm vào bất kỳ chỗ nào trên thẻ → mở màn Đang phát. (3) Bấm ⏸ ở hàng dưới → chỉ dừng nhạc, không mở màn khác.
