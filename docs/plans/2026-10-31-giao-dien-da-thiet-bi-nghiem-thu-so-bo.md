# Nghiệm thu sơ bộ — 2026-10-31-giao-dien-da-thiet-bi

## Vòng 1 — 01/11/2026, Claude Code (phiên mới, vai sơ bộ, không sửa mã)

**SƠ BỘ: CẦN SỬA** — phạm vi: UI-03 (chưa có hiệu lực trên máy thật) và UI-02 (cửa sổ thấp có bàn phím vẫn ≈ 0 dòng kết quả). Không thấy lỗi hồi quy hay rủi ro dữ liệu/phát nhạc trong diff. Các phần còn lại (UI-01, UI-04, UI-05, UI-07, UI-08) khớp báo cáo chặng C theo ảnh. Đây **không phải** nghiệm thu chính thức; chủ dự án quyết ĐẠT hoặc giao REWORK.

### Nguồn đã kiểm

| Mục | Kết quả |
|---|---|
| Git | Nhánh `dot/2026-10-31-giao-dien-da-thiet-bi`, HEAD `8664915919d23529d05e6e52f9612778b250c5f9` đúng dự kiến, cây sạch, `diff --check` sạch, commit cuối 31/10 23:59 (không có agent khác) |
| Lịch sử | `94ab6b0..HEAD`: 16 commit; mã chỉ ở 7 commit `fix(ui-da-thiet-bi)` (fd9720f … 75375b4) + 1 test G1 (`d9ab585`); sau `75375b4` chỉ có hồ sơ |
| Diff mã | 10 file UI trong `data/telegram/` + 2 file test; không đụng service, DB, TDLib, package, signer, versionCode, `MeloScreenPolicy` |
| Tài liệu đã đọc | CLAUDE.md, PROMPT_NGHIEM_THU_SO_BO.md, ban-giao, báo cáo mục “Chặng C”, apk-test, hinh-nghiem-thu, nghiem-thu |
| Test/build | **Không chạy lại** (source không đổi từ gate chặng B: 2336/21 skipped/0/0 mỗi biến thể). Dựa trên bằng chứng cũ |

### APK trên Sony (tự kiểm, chỉ đọc)

- `adb devices -l` trên Tiny: `QV725WEQ54 device`, model `XQ-AS52`, hãng `Sony`.
- Kéo `base.apk` đang cài về `C:\Bots\_qa_temp\GD-SOBO\base.apk` (Tiny): **SHA-256 `367CAA3738171D4163CAC04844F9E9F4FB2D0BC3C142CDA631F889C2929F1006`**, 33.593.284 byte — khớp hồ sơ. `apksigner` cert SHA-256 `eecd3df7…fce8` khớp. `versionCode=121`, `0.6.96-beta`, cài lúc 31/10 22:59:25. Không cài lại.
- MediaSession `app.melovault.player`: `state=2` (tạm dừng) ở 111.719 ms (≈ 01:51) — khớp ghi nhận “#3 Up With Wuan C, dừng 01:51” của chặng C. Phiên này **không phát, không đổi hàng đợi**.
- Màn hình: **không đánh thức Sony, không chạm màn**. `wm size`/`density` Physical 1080x2520/420 (không Override), `font_scale 1.0`, `user_rotation 0`, `accelerometer_rotation 1` — đúng gốc. **`SONY_SCREEN_END`: `Display State=OFF`** (kiểm 2 lần, 01/11).

### Drive (connector)

- Thư mục `1MojVY6lBo18qEcd6L1nXmU_KkBT_lEWY` và APK `1yj0UciXS0G2xDJiOf8AW1eXuBjh6AKLO`: quyền chỉ 1 mục `owner` (ntdung.md). APK trên Drive 33.593.284 byte, nằm trong đúng thư mục, tên có `QA-CANDIDATE`. Chưa thử tải bằng trình duyệt.

### Lỗi / điểm cần sửa

**SB-1 — UI-03 chưa có hiệu lực (xác nhận bằng ảnh).** Ảnh `tong-hop-UI-03-05.png` ô 2 “Hàng đợi SAU” (M3 dọc, bản `20bdf95`, 23:49): tiêu đề “Hàng đợi · 200 bài” bắt đầu sát mép trái, thẻ bài và nút “Thư viện” chạm hai mép, khoảng trên giống ảnh TRƯỚC. Kỳ vọng: lề ngang bằng `screenMargin` và khoảng trên `gap`.

Nguyên nhân khả dĩ (đọc mã, chưa thử): `TelegramPreviewActivity.showMeloQueue()` (`TelegramPreviewActivity.kt:4183-4197`) tạo `Dialog(this, android.R.style.Theme_Material_NoActionBar)` nhưng **không** gọi `window.setDecorFitsSystemWindows(false)`. Cửa sổ dialog để decor tự “fit system windows”: khung decor của theme platform (layout có `fitsSystemWindows`) tiêu thụ insets trước, nên `ViewCompat.setOnApplyWindowInsetsListener(pane.view)` nhiều khả năng **không bao giờ được gọi** → `setPadding` của UI-03 (và cả phần padding thanh hệ thống có từ trước) không chạy. Dấu hiệu khớp: ảnh SAU không đổi gì cả (lề lẫn khoảng trên), còn thanh trạng thái/điều hướng vẫn không bị đè vì chính decor đã chừa chỗ. So sánh: hai popup hàng đợi cũ ở `TelegramPreviewActivity.kt:4274` và `:4387` có gọi `setDecorFitsSystemWindows(false)`. `MeloQueuePane.view` (`MeloQueuePane.kt:38`) là `LinearLayout` không có padding riêng. Test JVM `ui03_*` chỉ kiểm hàm thuần `MeloQueueUiPolicy.fullScreenPadding`, không chứng minh listener chạy. Hướng sửa cho phiên REWORK (không làm ở đây): hoặc đặt lề trực tiếp lên `pane.view` (không phụ thuộc listener), hoặc bật edge-to-edge cho cửa sổ dialog rồi giữ listener; kiểm lại bằng ảnh Sony M3 dọc.

**SB-2 — UI-02 chưa hết ở cửa sổ thấp có bàn phím.** Theo bảng chặng C: M1 dọc 0 dòng, F3 M3 dọc và F3 M6 dọc ≈ 0,5 dòng khi ô tìm mở bàn phím (ảnh `tong-hop-UI-01-02-doc.png`, `tong-hop-UI-02-F3M6.png`; không chụp lại ở phiên này). Nguyên nhân khả dĩ: `MeloLibraryPane.applyTrim()` chỉ ẩn 4 phần tùy chọn (scopeNote, note, thẻ nguồn, dải thể loại); phần cố định (hàng tiêu đề, ô tìm, hàng tab `chipHolder`, hàng “Tất cả · N bài / sắp xếp / Phát hết”) không co. Ở chữ lớn, sửa UI-07 cho hàng tab bọc thành 2 dòng (`MeloFlowRow`) còn làm phần cố định cao thêm. Ẩn thanh dưới (`MeloBottomBarPolicy.hiddenForKeyboard`, ngưỡng 900 dp) có chạy nhưng không đủ. Hướng sửa thuộc chủ dự án quyết (khi đang nhập: ẩn hàng tab / hàng tiêu đề, hoặc cho phần đầu cuộn cùng danh sách).

**Ghi chú, không chặn:** UI-01 M2 ngang còn ≈ 1,5 dòng (chủ dự án chấp nhận hay muốn thêm); UI-05 Nguồn chưa so cùng điều kiện; UI-06 còn nguyên theo quyết định.

### Chưa kiểm (giới hạn)

- Không chụp ảnh mới: mở Hàng đợi/Đang phát ở chặng C từng buộc tiếp tục phát nhạc, nên phiên này dùng ảnh chặng C thay vì thao tác lại.
- Gọn (M9 chia đôi) chưa có ảnh; Đang phát/Hàng đợi ở M1/M2/M5–M8; hộp thoại mời Car Mode; Cài đặt dọc M1/M5/M6/F2/F3; tải APK từ Drive bằng trình duyệt; **CHƯA XÁC NHẬN ÂM THANH THỰC**; tablet/máy gập thật.
- Không rà toàn bộ diff `MeloFlowRow.kt`, `MeloBottomBar.kt`; chỉ đọc kỹ file rủi ro được giao.

### 3–6 bước chủ dự án thử (Sony, APK vc121 đang cài)

1. Thư viện, xoay ngang: danh sách bài phải còn ít nhất ≈ 2 dòng (trước là ≈ 1).
2. Thư viện dọc, chạm ô tìm: thanh mini-player/tab dưới tạm ẩn khi bàn phím lên, hiện lại khi đóng bàn phím.
3. Mở Đang phát → Hàng đợi (lưu ý: có thể phải tiếp tục bài đang dừng): kỳ vọng có lề hai bên — **hiện chưa có (SB-1)**.
4. Cài đặt hệ thống cỡ chữ lớn nhất: hàng tab Thư viện xuống dòng đủ chữ, menu ⋮ của bài không bị cắt.
5. Nếu có máy tablet/màn ngang rộng (Hai khung): cột Đang phát bên trái không còn sát mép.

Bài đang ở trên Sony: “#3 Up With Wuan C” tạm dừng ≈ 01:51 (sự cố chặng C); chọn lại #140 nếu muốn.

## Đối chiếu lại — 01/11/2026 06:15, Claude Code (phiên sơ bộ lặp, không làm lại)

Prompt mới giao lại cùng vai sơ bộ với HEAD dự kiến `8664915`; HEAD thực là `7c7602a` (chính biên bản Vòng 1 ở trên, không có commit lạ). Không kiểm lại toàn bộ, chỉ đối chiếu: không có thay đổi `.kt` sau `75375b4`; `showMeloQueue()` (`TelegramPreviewActivity.kt:4183`) vẫn không gọi `setDecorFitsSystemWindows(false)` (chỉ có ở `:4274`, `:4387`) — nguyên nhân khả dĩ SB-1 giữ nguyên; Sony `QV725WEQ54` XQ-AS52, `versionCode=121`, không đánh thức/chạm/phát, **`SONY_SCREEN_END`: `Display State=OFF`**. Kết luận không đổi: **SƠ BỘ: CẦN SỬA** (SB-1 UI-03, SB-2 UI-02).
