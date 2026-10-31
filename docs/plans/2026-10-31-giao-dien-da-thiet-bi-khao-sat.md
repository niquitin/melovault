# Khảo sát giao diện đa thiết bị — kết quả G2 (chặng A)

TASK_ID: `2026-10-31-giao-dien-da-thiet-bi`. Nhánh `dot/2026-10-31-giao-dien-da-thiet-bi`. Khảo sát: 31/10/2026 20:24–20:35 (+07), Claude Code Sonnet 5.5, phiên chặng A.
Thiết bị: Sony Xperia 5 II XQ-AS52, serial `QV725WEQ54`, Android 12, qua Tiny. APK trên máy: `app.melovault.player` `0.6.96-beta` vc121, SHA-256 khớp `6e3d6b6e…a029` (33 593 284 byte) → đúng nguồn `a2f0a88`.
Ảnh: `docs/plans/artifacts/2026-10-31-giao-dien-da-thiet-bi/G2-20261031-203502/` (private, không commit, đã bị `.gitignore`). Ảnh tổng hợp: `00_tong_hop_M3-doc.png`, `00_tong_hop_M3-ngang.png`, `00_tong_hop_M9-chia-doi.png`.

## 1. Phạm vi thực tế: làm theo GD3 (không đổi cỡ màn)

Bàn giao **chưa ghi** chủ dự án cho phép `wm size` / `wm density` / `font_scale` (câu 1 mục 9 kế hoạch). Vì vậy **không đụng** ba thứ đó. Chỉ làm những gì không đổi cài đặt kích thước:

| Mã | Cấu hình | Làm? | Ghi chú |
|---|---|---|---|
| M3 dọc | Sony gốc 1080×2520 / 420, cửa sổ app 411 × 881 dp | **Đã khảo sát** | Thư viện (đầu, giữa), menu ⋮, ô tìm + bàn phím, Nguồn, Đang phát, Tiếp theo, Hàng đợi, Cài đặt |
| M3 ngang | cùng máy, xoay (`user_rotation=1`, `accelerometer_rotation=0`) 912 × 381 dp | **Đã khảo sát** | Thư viện/Nguồn, menu ⋮, ô tìm + bàn phím, Hàng đợi, Cài đặt (hai trang) |
| M9 chia đôi | Sony gốc, chia đôi bằng đa nhiệm; cửa sổ trên 411 × 435 dp | **Đã khảo sát** | Thư viện, Đang phát, ô tìm + bàn phím, Cài đặt |
| M1, M2, M4–M8 | cần `wm size` / `wm density` | **CHƯA KIỂM** | chờ chủ dự án cho phép |
| F1–F3 | cần `font_scale` | **CHƯA KIỂM** | chờ chủ dự án cho phép |
| M9 ngang | không có trong bảng mục 4 | không làm | |

Ghi chú:
- Cửa sổ app thực đo từ `dumpsys window`: dọc `w411dp h881dp`, ngang `w912dp h381dp`, chia đôi `w411dp h435dp`. Khác nhẹ bảng mục 4 (≈ 411 × 960) vì thanh trạng thái và thanh điều hướng chiếm chỗ.
- Bố cục nhìn thấy khớp G1: dọc → Điện thoại dọc; ngang → Hai khung (cỡ Điện thoại); chia đôi (h435 < 480) → Gọn.
- Xoay và chia đôi làm app tự dựng lại lớp UI; **không có lỗi “đổi cỡ”** (không đứng hình, không bố cục cũ). Không có crash/ANR của MeloVault trong logcat (`-b crash` chỉ có một dòng cũ của tiến trình khác, `Analytics-HighPri-Proc`, 04:58).

## 2. Bảng lỗi UI-xx

Mức: Chặn dùng / Xấu rõ / Nhỏ. Nhóm: a–f theo GD4 trong kế hoạch, hoặc “Đề xuất” (GD5: không tự sửa).

| Mã | Cấu hình | Màn | Mô tả | Nhóm | Mức | Ảnh |
|---|---|---|---|---|---|---|
| UI-01 | M3 ngang | Thư viện, tab Thư viện | Phần đầu cố định (tiêu đề, ô tìm, 2 hàng chip, “Tất cả · 25.470 bài” + nút sắp xếp/Phát hết, dòng “Phạm vi”) chiếm ≈ 80% chiều cao 381 dp. Vùng danh sách còn ≈ 1,3 dòng; không cuộn phần đầu đi được. Duyệt thư viện rất khó | (e) co/cho cuộn vùng phụ trước, giữ vùng danh sách | **Xấu rõ** (sát ngưỡng Chặn dùng) | `M3-ngang-thuvien-giua`, `M3-ngang-menu-dong` |
| UI-02 | M3 dọc | Thư viện, ô tìm mở bàn phím | Bàn phím cao ≈ 40%; phần đầu cố định vẫn chiếm ≈ 60% vùng còn lại. Kết quả tìm chỉ thấy ≈ 2,5 dòng | (e) | **Xấu rõ** | `M3-doc-tim-banphim` |
| UI-03 | M3 dọc | Hàng đợi (nút “Xem cả hàng đợi”) | Không có lề ngang: tiêu đề “Hàng đợi · 1 bài” sát mép trái màn, thẻ bài và nút “Thư viện” chạm mép phải. Ngang (Hai khung) thì có lề đúng → lỗi chỉ ở màn Hàng đợi toàn màn dọc | (d) gần đúng: thiếu lề/insets ngang | **Xấu rõ** | `M3-doc-hang-doi` (so với `M3-ngang-hang-doi`) |
| UI-04 | M3 ngang | Khung Đang phát bên trái (Hai khung) | Cột trái không có lề trái: tiêu đề bài, “Kênh: …”, nút Đảo, thanh tua và hàng điều khiển bắt đầu ở x = 0. Hàng nút dưới cùng sát mép dưới màn | (d) gần đúng | Nhỏ | `M3-ngang-thuvien-dau`, `M3-ngang-thuvien-chip` |
| UI-05 | M3 dọc | Thư viện, Nguồn (sau khi bài đang có trạng thái phát) và Đang phát | Dòng trạng thái “Tạm dừng” xuất hiện lẻ loi dưới “Phạm vi: …” và đè ≈ 10 px lên mép trên danh sách; ở Đang phát nó dính sát hàng chip kỹ thuật (MP3 / 44.1kHz …). Dòng này không có ở ảnh đầu khi chưa phát | (b) chồng phần tử | Nhỏ | `M3-doc-sau-hang-doi`, `M3-doc-dang-phat`, `M9-sau-thoat-chia-doi` |
| UI-06 | M3 ngang | Thư viện, ô tìm mở bàn phím | Bàn phím Android dùng chế độ **toàn màn hình** (extract UI): che hết app, chỉ còn một ô nhập và nút “TÌM KIẾM”. Không thấy kết quả khi gõ | **Đề xuất** (cờ `IME_FLAG_NO_EXTRACT_UI` trên ô tìm; ngoài nhóm a–f) | Nhỏ | `M3-ngang-tim-banphim` |

### Quan sát — không phải lỗi bố cục, chủ dự án tự xem xét

- **Thẻ “Nghe tiếp” trên mini-player:** lần chạm đầu vào mũi tên `^` ở góc phải **tiếp tục phát** bài đang dừng thay vì mở Đang phát; chạm lần hai mới mở. Hành vi, không phải bố cục. Ghi lại vì lần chạm này vô tình bật nhạc trong phiên (xem mục 4).
- **Câu 2 mục 9 (360 × 640 → Gọn):** chưa thể đánh giá vì M1 chưa kiểm (cần `wm size`).
- **Chia đôi M9 (Gọn, 435 dp cao):** dùng được. Thư viện thấy 3 dòng, thanh mini-player và thanh tab đủ nút; Đang phát cuộn được, hàng điều khiển và thanh tua luôn ở đáy; bàn phím không che cửa sổ trên. Không ghi lỗi.
- **Cài đặt dọc và ngang:** không cắt chữ, không chồng phần tử, hàng đủ cao. Không ghi lỗi.
- **Menu ⋮ dòng bài:** hiện đủ 5 mục ở cả dọc và ngang, không vượt màn.

## 3. Việc cần chủ dự án quyết trước chặng B

1. **Có cho phép `wm size` / `wm density` / `font_scale` trên Sony không** (câu 1 mục 9)? Nếu có → cần một phiên khảo sát bổ sung cho M1, M2, M4–M8 và F1–F3. Không có phiên bổ sung → các cấu hình đó chặng C chỉ ghi “chưa kiểm”.
2. **Gạch bớt lỗi không muốn sửa** (UI-01…UI-05 thuộc GD4; UI-06 là Đề xuất, mặc định không sửa).
3. Câu 4 mục 9: mặc định chặng B sửa ngay UI-01…UI-05 theo GD4.

## 4. Thiết bị đã khôi phục

- `wm size`: **không đổi** (chỉ “Physical size: 1080x2520”, không có Override). `wm density`: **không đổi** (Physical 420). `font_scale` = 1.0 (không đổi).
- Xoay: `user_rotation` gốc 0 → về 0; `accelerometer_rotation` gốc 1 → đổi tạm 0 khi chụp ngang → **về 1**.
- Phát nhạc: trạng thái ban đầu là bài `#140 - EM MUON TU DO - HV REMIX.mp3` đang dừng (thẻ “Nghe tiếp”). Một lần chạm vào mũi tên `^` đã **tiếp tục phát ≈ 15–30 giây**; đã bấm Tạm dừng ngay. Cuối phiên `state=2` (PAUSED), vị trí 00:46. Hàng đợi vẫn 1 bài, không thêm bài, không đổi hàng đợi. Vị trí phát gốc không ghi được nên không khôi phục chính xác.
- Chia đôi màn: đã thoát (cửa sổ `fullscreen`). Cửa sổ thứ hai là bản `app.melovault.player.open` (màn đăng nhập của chủ dự án), chỉ **hiển thị**, không thao tác gì. Cuối phiên đã đưa bản `.open` trở lại trước như lúc nhận máy.
- Màn hình: `SONY_SCREEN_END=OFF` lúc 20:34:54 (`Display State=OFF`, `mWakefulness=Dozing`).
- Không đăng xuất, không uninstall, không `pm clear`, không máy bay, không đổi tài khoản.
- Ảnh có chứa tên bài và danh sách kênh Telegram; `M9-chon-app-thu-hai.png` và `M9-recents*.png` còn có hình thu nhỏ trang web đang mở trong Chrome. Tất cả để private trong thư mục artifacts, không commit, không đưa lên Drive.
