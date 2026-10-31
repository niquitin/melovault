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
| G2 | Khảo sát Sony ở cỡ gốc: dọc, ngang, chia đôi | 6 mục UI-xx; M1, M2, M4–M8, F1–F3 **chưa kiểm** (chưa được phép) |

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

- M1, M2, M4–M8 và F1–F3 chưa kiểm (không được phép đổi `wm size` / `wm density` / `font_scale`). Bố cục Tablet dọc / Hai khung cỡ Tablet / máy gập **chỉ được khóa bằng test JVM**, chưa có ảnh thật.
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
