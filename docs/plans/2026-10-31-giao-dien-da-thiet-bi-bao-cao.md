Thực thi: Claude Code Sonnet 5.5, phiên chặng A (G0–G2).

# Báo cáo thực thi — giao diện phù hợp mọi loại thiết bị

TASK_ID: `2026-10-31-giao-dien-da-thiet-bi`. Nhánh `dot/2026-10-31-giao-dien-da-thiet-bi`; worktree `D:\Bots\_worktrees\MeloVault-giao-dien-da-thiet-bi`. Baseline `94ab6b0`. HEAD lúc nhận: `3cdcefd`.
Kế hoạch: [`2026-10-31-giao-dien-da-thiet-bi.md`](2026-10-31-giao-dien-da-thiet-bi.md). Bàn giao: [`-ban-giao.md`](2026-10-31-giao-dien-da-thiet-bi-ban-giao.md). Khảo sát: [`-khao-sat.md`](2026-10-31-giao-dien-da-thiet-bi-khao-sat.md).
Trạng thái: **chặng A xong; chặng B/C chưa làm. CHƯA phải bản nghiệm thu.** Không có APK mới (không build), nên chưa có hồ sơ APK / Drive.

## Tóm tắt các bước

| Bước | Việc | Kết quả |
|---|---|---|
| Nhận việc | `git status` sạch, nhánh đúng, HEAD `3cdcefd`, `diff --check` sạch; không có tiến trình/agent khác trên nhánh; Sony `device`, màn OFF, không ai dùng | PASS |
| G0 | APK trên Sony: `app.melovault.player` `0.6.96-beta` vc121, kéo `base.apk` (33 593 284 byte) về Tiny, SHA-256 `6E3D6B6E…A029` | **KHỚP** bản `a2f0a88` |
| G1 | Thêm `MeloLayoutMatrixTest` (7 ca) khóa kết quả `chooseLayout` / `profileFor` / `shouldOfferCarMode` / `panelCompact` cho M1–M9 | **PASS** Debug + Release; không dòng nào lệch bảng mục 4; không sửa `MeloScreenPolicy` |
| G2 lượt 1 | Khảo sát Sony ở cỡ gốc: dọc, ngang, chia đôi | UI-01…UI-06 |
| G2 bổ sung | Sau khi chủ dự án cho phép đổi tạm `wm size` / `wm density` / `font_scale` (bàn giao): M1, M2, M4–M8, F1–F3 trên M3 và M6 | Bảng lỗi hợp nhất UI-01…UI-08; M8 và F3 M6 ngang chỉ có một phần ảnh (xem khảo sát mục 4). Mục “Phiên khảo sát bổ sung” bên dưới |

## G0 — chi tiết

- Máy: `adb devices -l` → `QV725WEQ54 device`; `ro.product.model=XQ-AS52`, `manufacturer=Sony`.
- Giá trị gốc trước khảo sát: `wm size` Physical `1080x2520` (không Override), `wm density` Physical `420`, `font_scale` `1.0`, `user_rotation` `0`, `accelerometer_rotation` `1`, `Display State=OFF`.
- Máy đang có cả `app.melovault.player.open` (màn đăng nhập, chủ dự án dùng) và `app.melovault.player`. Khảo sát dùng bản `app.melovault.player` (đúng nguồn kế hoạch); bản `.open` không bị thao tác.

## G1 — chi tiết

- File mới: `app/src/test/java/com/bsdung/musicassistantcar/data/telegram/MeloLayoutMatrixTest.kt`. Commit `d9ab585`.
- Nội dung 7 ca: kích thước dp của ma trận; dọc; ngang; `panelCompact`; mời Car Mode (chỉ ngang ≥ 1200); biên chia đôi M9 (h < 480); các biên 599/600, 559/560, 959/960 và Car Mode thắng mọi cỡ.
- Lệnh: `.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --tests '*MeloLayoutMatrixTest' --console=plain` → `BUILD SUCCESSFUL in 3m 50s`.
- Lưu ý: `--tests` chỉ áp cho tác vụ cuối (Release), nên Debug chạy **toàn bộ** bộ test.
- Cộng XML `build/test-results`:

| Biến thể | suites | tests | skipped | failures | errors |
|---|---|---|---|---|---|
| Debug (toàn bộ) | 180 | 2317 | 21 | 0 | 0 |
| Release (chỉ `MeloLayoutMatrixTest`) | 1 | 7 | 0 | 0 | 0 |

  Debug: trước G1 là 2310/21/0/0 (HANDOFF) → thêm 7 ca = 2317. Khớp.
- Chỉnh bảng mục 4 (theo quy tắc “bảng sai thì sửa bảng”): **chỉ một chỗ nhỏ** — M5 cao 828 dp (bảng ghi ≈ 829, `toInt` cắt 828,95). Không ảnh hưởng bố cục. Không đổi mã.
- Log đầy đủ ở `docs/plans/artifacts/2026-10-31-giao-dien-da-thiet-bi/G1-20261031-202449/` (private, bị `.gitignore`; có `gradle.log` UTF-16 và `tong-ket.txt`).
- Chưa chạy `assemble*` và Personal vì G3 chưa bắt đầu (không sửa mã app).

## G2 — chi tiết

Thao tác trên Sony (qua Tiny, Desktop Commander), ảnh `adb exec-out screencap -p`, kiểm header PNG `89504E47` cho mọi file. Mỗi lần chạm đều kiểm `mCurrentFocus` là `app.melovault.player/` (trừ lúc chia đôi). Không `BACK` ở màn gốc.

| # | Cấu hình | Hành động | Thực tế | Kết quả | Ảnh |
|---|---|---|---|---|---|
| 1 | M3 dọc | Mở app, Thư viện đầu + cuộn giữa | Điện thoại dọc, dùng được | PASS | `M3-doc-thuvien-dau/giua` |
| 2 | M3 dọc | ⋮ dòng bài | menu đủ 5 mục | PASS | `M3-doc-menu-dong` |
| 3 | M3 dọc | Ô tìm + bàn phím | list còn ≈ 2,5 dòng | **UI-02** | `M3-doc-tim-banphim` |
| 4 | M3 dọc | Tab Nguồn | dùng được | PASS | `M3-doc-nguon` |
| 5 | M3 dọc | Mở Đang phát, cuộn xuống Tiếp theo, Hàng đợi | Đang phát đủ; Hàng đợi sát mép | **UI-03, UI-05** | `M3-doc-dang-phat`, `tiep-theo-1/2`, `hang-doi` |
| 6 | M3 dọc | Cài đặt | không lỗi | PASS | `M3-doc-cai-dat-1/2` |
| 7 | M3 ngang | Xoay, Thư viện/Nguồn, cuộn | list ≈ 1,3 dòng; cột trái sát mép | **UI-01, UI-04** | `M3-ngang-thuvien-*` |
| 8 | M3 ngang | ⋮, ô tìm + bàn phím | menu đủ; bàn phím toàn màn | UI-06 (đề xuất) | `M3-ngang-menu-dong`, `tim-banphim` |
| 9 | M3 ngang | Hàng đợi, Cài đặt | có lề, không lỗi | PASS | `M3-ngang-hang-doi`, `cai-dat-1/2` |
| 10 | M9 chia đôi | Đa nhiệm → Chia đôi màn hình → app thứ hai | cửa sổ trên 411 × 435 dp → Gọn | PASS (đúng luật) | `M9-recents*`, `M9-chia-doi-thuvien` |
| 11 | M9 | Đang phát, ô tìm + bàn phím, Cài đặt | dùng được, bàn phím không che cửa sổ trên | PASS | `M9-chia-doi-*` |

Ảnh tổng hợp (cạnh dài ≤ 1600 px): `00_tong_hop_M3-doc.png` (1580×1528), `00_tong_hop_M3-ngang.png` (1580×1086), `00_tong_hop_M9-chia-doi.png` (1600×864).
Danh sách lỗi đầy đủ và quan sát nằm ở file khảo sát.

## Việc dở dang / chưa kiểm chứng

- (Cập nhật sau phiên bổ sung) Những cấu hình ở dòng này lượt 1 chưa kiểm vì chưa được phép đổi cỡ; **nay đã khảo sát** (xem mục “Phiên khảo sát bổ sung”). Còn thiếu: M8 chỉ có ảnh Thư viện; F3 M6 ngang thiếu menu ⋮ và Cài đặt; hộp thoại mời Car Mode không quan sát được.
- Âm thanh thật chưa kiểm: nhạc có lúc phát trong phiên nhưng không có nguồn âm độc lập — CHƯA XÁC NHẬN ÂM THANH THỰC.
- Không có tablet thật, máy gập thật, FYT hay S25 (đã ngoài phạm vi QA).
- Chưa build, chưa `assemble`, chưa kiểm Personal (chặng B).

## Sự cố và thiết bị

- **Vô tình phát nhạc ≈ 15–30 giây:** lần chạm đầu vào mũi tên `^` của thẻ “Nghe tiếp” tiếp tục phát bài #140 đang dừng, thay vì mở Đang phát. Phát hiện sau ≈ 1 phút, bấm Tạm dừng. Không đổi bài, không đổi hàng đợi. Cuối phiên PAUSED, 00:46. (Quy tắc cấm “phát bài mới/đổi hàng đợi” không bị vi phạm; nhưng đây là việc ngoài kế hoạch nên ghi rõ.)
- **Lỗi vặt khi chạy Gradle nền (không ảnh hưởng kết quả):** log do PowerShell ghi dạng UTF-16 và wrapper không tạo được `exit.txt`; Gradle vẫn chạy xong (`BUILD SUCCESSFUL`), kết quả lấy từ XML. Không chạy lại.
- Khôi phục: `wm size`/`wm density`/`font_scale` **không bao giờ bị đổi**; `user_rotation` 0 và `accelerometer_rotation` 1 đã về gốc; thoát chia đôi; đưa app `.open` về trước như lúc nhận.
- `SONY_SCREEN_END=OFF` lúc 2026-10-31 20:34:54 (`Display State=OFF`, `mWakefulness=Dozing`).

## Rủi ro

- Ảnh Sony chứa tên bài/kênh Telegram và vài ảnh có hình thu nhỏ Chrome. Giữ private, không commit.
- Chưa có chủ dự án duyệt bảng lỗi. Chặng B mặc định sửa UI-01…UI-05 theo GD4; UI-06 là Đề xuất.
- UI-01/UI-02 sửa phần đầu Thư viện dùng chung có thể làm Gọn/Car xê dịch → hồi quy bằng test sẵn có và ảnh M1/M9.

## Phiên khảo sát bổ sung (21:04–22:09) — cùng phiên chặng A

- **Căn cứ:** chủ dự án trả lời trong chat sau chặng A: “Cho phép đổi tạm wm size/density/font_scale trên Sony, khôi phục ngay sau mỗi cấu hình. Làm phiên khảo sát bổ sung M1, M2, M4–M8, F1–F3.” Đã ghi vào bàn giao (commit `3ceb057`). Chỉ Sony `QV725WEQ54`; các luật cấm khác giữ nguyên.
- **Cách làm:** script PowerShell trên Tiny (`C:\Bots\_qa_temp\GD-G2\cfg2.ps1`, `survey2.ps1`, `chain*.ps1`). Mỗi cấu hình: đặt cỡ, mở app, tìm phần tử bằng `uiautomator dump` (không đoán tọa độ), chụp, rồi `finally` khôi phục. Mọi thứ nằm ngoài repo, không commit. Ảnh ở `docs/plans/artifacts/…/G2B/`.
- **Kết quả:** bảng UI-01…UI-08 nằm trong [khảo sát](2026-10-31-giao-dien-da-thiet-bi-khao-sat.md). Mở rộng so với chặng A:
  - UI-01 và UI-02 xảy ra ở mọi cửa sổ thấp / có bàn phím, nặng nhất ở M2 ngang (≈ 0,5 dòng) và F3 M3 ngang (0 dòng) → **Chặn dùng** ở các ca đó;
  - UI-04 xảy ra ở mọi cỡ Hai khung;
  - thêm UI-07 (chữ ≥ 1,5 cắt chip / ô tìm / nút) và UI-08 (nhãn tab cắt ở chữ 2,0 trên 600 dp).
- **Bài học kỹ thuật (để chặng sau dùng lại):**
  1. `wm size` của Sony không nhận chiều rộng quá ≈ 1096 px. Muốn ra dp lớn phải hạ `density`: M4 `1080x2340 / 400`, M5 `1080x1298 / 250`, M6 `1080x1728 / 288`, M7 `1080x1728 / 216`.
  2. App xét “tấm màn nhỏ” theo kích thước **vật lý**, nên `wm size` không ra bố cục Gọn (M1 ra Điện thoại dọc).
  3. Vuốt theo chiều dọc trong màn Đang phát có thể rơi vào thanh tua và đổi vị trí phát. Chỉ vuốt trong vùng cuộn.
- **Sự cố, ghi đủ trong khảo sát mục 5:**
  - hai lần khôi phục `finally` bị cắt do ADB chớp rớt, đã khôi phục thủ công trong ≈ 1–2 phút;
  - vị trí phát bài #140 bị kéo từ 00:46 lên 02:48 do vuốt trúng thanh tua (luôn tạm dừng, không đổi bài/hàng đợi).
- **Trạng thái cuối:** không có Override cỡ/mật độ, `font_scale` 1.0, `user_rotation` 0, `accelerometer_rotation` 1; `SONY_SCREEN_END=OFF` lúc 2026-10-31 22:09:10.
- **Chưa làm:** không có APK mới, không `assemble`, không đụng mã app (chỉ thêm test G1). Chưa có chủ dự án duyệt bảng lỗi.
