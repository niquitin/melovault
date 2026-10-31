# Khảo sát giao diện đa thiết bị — kết quả G2 (chặng A + bổ sung)

TASK_ID: `2026-10-31-giao-dien-da-thiet-bi`. Nhánh `dot/2026-10-31-giao-dien-da-thiet-bi`. Claude Code Sonnet 5.5, 31/10/2026.
- Lượt 1 (20:24–20:35): cỡ gốc theo GD3.
- Lượt bổ sung (21:04–22:09): sau khi chủ dự án cho phép đổi tạm `wm size` / `wm density` / `font_scale` (xem bàn giao).

Thiết bị: Sony Xperia 5 II XQ-AS52, serial `QV725WEQ54`, Android 12, qua Tiny. APK trên máy: `app.melovault.player` `0.6.96-beta` vc121, SHA-256 khớp `6e3d6b6e…a029` (đúng nguồn `a2f0a88`).
Ảnh private, **không commit** (thư mục bị `.gitignore`):
- Lượt 1: `docs/plans/artifacts/2026-10-31-giao-dien-da-thiet-bi/G2-20261031-203502/` (M3 gốc: dọc, ngang, chia đôi).
- Lượt bổ sung: `…/G2B/`, mỗi cấu hình có một ảnh tổng hợp `00_tong_hop_<cấu hình>.png`.

## 1. Cấu hình đã khảo sát

Cột “dp cửa sổ” là cỡ cửa sổ app đo từ `dumpsys window` (đã trừ thanh trạng thái và thanh điều hướng), không phải cỡ tấm màn.

| Mã | Cài đặt (`wm size` / `wm density`, xoay) | dp cửa sổ | Bố cục thấy được | Ảnh tổng hợp |
|---|---|---|---|---|
| M3 dọc | gốc 1080×2520 / 420 | 411 × 881 | Điện thoại dọc | `G2-…/00_tong_hop_M3-doc` |
| M3 ngang | gốc, xoay 90° | 912 × 381 | Hai khung | `G2-…/00_tong_hop_M3-ngang` |
| M9 chia đôi | gốc, đa nhiệm | 411 × 435 | **Gọn** (đúng luật h < 480) | `G2-…/00_tong_hop_M9-chia-doi` |
| M1 dọc | 720×1280 / 320 | 360 × 537 | **Điện thoại dọc** (không phải Gọn, xem 4) | `M1-doc` |
| M2 dọc / ngang | 1080×2340 / 480 | 360 × 711 / 738 × 333 | Điện thoại dọc / Hai khung | `M2-doc`, `M2-ngang` |
| M4 dọc | 1080×2340 / 400 | 432 × 854 | Điện thoại dọc | `M4-doc` |
| M5 dọc | 1080×1298 / 250 | 691 × 699 | Tablet dọc | `M5-doc` |
| M6 dọc / ngang | 1080×1728 / 288 | 600 × 846 / 959 × 486 | Tablet dọc / Hai khung | `M6-doc`, `M6-ngang` |
| M7 dọc / ngang | 1080×1728 / 216 | 800 × 1128 / 1280 × 648 | Tablet dọc / Hai khung | `M7-doc`, `M7-ngang` |
| M8 ngang | 608×1080 / 90, xoay 90° | 1920 × 679 | Hai khung | `M8-ngang` (**chỉ 1 ảnh Thư viện**, xem 4) |
| F1 = 1,3 | M3 dọc / ngang; M6 dọc / ngang | như trên | như trên | `F13-M3-doc/ngang`, `F13-M6-doc/ngang` |
| F2 = 1,5 | như F1 | như trên | như trên | `F15-M3-…`, `F15-M6-…` |
| F3 = 2,0 | như F1 | như trên | như trên | `F20-M3-…`, `F20-M6-…` (**ngang M6 chỉ 5 ảnh**) |

Ghi chú về cỡ:
- Sony **không nhận** `wm size` rộng hơn khoảng 1096 px. Lần chạy đầu của M4–M8 theo đúng kích thước plan bị hệ thống ép về 1096 px, nên dp sai (ví dụ M6 thành 548 dp). Đã bỏ kết quả đó và chạy lại bằng cách **giữ chiều rộng ≤ 1080 px và hạ `density`** để ra đúng dp của bảng mục 4: M4 432 dp, M5 691 dp, M6 600 dp, M7 800 dp.
- Bố cục thấy được khớp bảng test G1 ở mọi cấu hình (Điện thoại dọc / Tablet dọc / Hai khung). Chỉ M1 khác (xem mục 4).
- Mỗi cấu hình: thư viện (đầu, cuộn giữa), menu ⋮ dòng bài, ô tìm + bàn phím, tab Nguồn, Đang phát, Tiếp theo, Cài đặt (hai trang). Một số ô trống ở M5–M8 do script không tìm thấy phần tử (xem 4).
- Đổi cỡ khi app đang mở: app tự dựng lại lớp UI (`configChanges`) ở mọi cấu hình, **không có lỗi “đổi cỡ”**, không crash.

## 2. Bảng lỗi UI-xx

Mức: Chặn dùng / Xấu rõ / Nhỏ. Nhóm: a–f theo GD4 trong kế hoạch, hoặc “Đề xuất” (GD5: không tự sửa).

| Mã | Cấu hình | Màn | Mô tả | Nhóm | Mức | Ảnh |
|---|---|---|---|---|---|---|
| UI-01 | **Cửa sổ thấp (≤ ≈ 540 dp):** M2 ngang (333), M3 ngang (381), F20 M3 ngang (381), F20 M6 ngang (486), M1 dọc (537), M5 dọc (699) | Thư viện | Phần đầu cố định (tiêu đề, ô tìm, 2 hàng chip, “Tất cả · … bài” + nút sắp xếp, dòng “Phạm vi”) không cuộn đi. Vùng danh sách còn: ≈ 0,5 dòng (M2 ngang), ≈ 1,3 (M3 ngang), **0 dòng (F20 M3 ngang)**, ≈ 1 dòng (M1 dọc, F20 M6 ngang), ≈ 2 dòng (M5 dọc) | (e) co / cho cuộn vùng phụ trước, giữ danh sách | **Chặn dùng** (M2 ngang, F20 M3 ngang) / Xấu rõ (còn lại) | `M2-ngang-1-thuvien`, `M3-ngang-thuvien-giua`, `F20-M3-ngang-1-thuvien`, `M1-doc-2-giua`, `M5-doc-1-thuvien` |
| UI-02 | **Ô tìm mở bàn phím** ở cửa sổ dọc có phần đầu cao: M3, M1, M5, F20 M3, F20 M6 | Thư viện | Bàn phím chiếm ≈ 40% màn; phần đầu + mini-player vẫn giữ chỗ → kết quả tìm gần như không thấy: ≈ 2,5 dòng (M3), ≈ 1 dòng (M5), **0 dòng (M1, F20 M3, F20 M6)** | (e) | Xấu rõ / **Chặn dùng** khi không thấy dòng nào | `M3-doc-tim-banphim`, `M1-doc-4-tim`, `F20-M3-doc-4-tim`, `M5-doc-4-tim` |
| UI-03 | M3 dọc (và mọi cỡ dọc dùng màn Hàng đợi toàn màn) | Hàng đợi (nút “Xem cả hàng đợi”) | Không có lề ngang: tiêu đề “Hàng đợi · 1 bài” sát mép trái, thẻ bài và nút “Thư viện” chạm mép phải. Ngang (Hai khung) thì có lề đúng → lỗi chỉ ở màn Hàng đợi toàn màn dọc | (d) gần đúng: thiếu lề/insets ngang | Xấu rõ | `M3-doc-hang-doi` (so `M3-ngang-hang-doi`) |
| UI-04 | **Mọi cỡ Hai khung:** M2 ngang, M3 ngang, M6 ngang, M7 ngang, M8, F-M3 ngang, F-M6 ngang | Cột Đang phát bên trái | Không có lề trái: tiêu đề bài, “Kênh: …”, nút Đảo, thanh tua, hàng điều khiển bắt đầu ở x = 0; hàng nút dưới cùng sát mép dưới màn | (d) gần đúng | Nhỏ | `M3-ngang-thuvien-dau`, `M7-ngang-1-thuvien`, `M2-ngang-1-thuvien` |
| UI-05 | M3 dọc (và các cỡ dọc khi bài đã có trạng thái phát) | Thư viện, Nguồn, Đang phát | Dòng trạng thái “Tạm dừng” / “Đang kiểm tra…” lẻ loi dưới “Phạm vi: …”, đè ≈ 10 px lên mép trên danh sách; ở Đang phát dính sát hàng chip kỹ thuật (MP3 / 44.1kHz …) | (b) chồng phần tử | Nhỏ | `M3-doc-sau-hang-doi`, `M3-doc-dang-phat`, `M9-sau-thoat-chia-doi` |
| UI-06 | Cửa sổ ngang thấp: M3 ngang (381), M2 ngang (333), F-M3 ngang (381). M6 ngang (486), M7 ngang (648) **không** bị | Thư viện, ô tìm mở bàn phím | Bàn phím Android dùng chế độ **toàn màn hình** (extract UI): che hết app, chỉ còn một ô nhập và nút “TÌM KIẾM”; không thấy kết quả khi gõ | **Đề xuất** (cờ `IME_FLAG_NO_EXTRACT_UI` trên ô tìm; ngoài a–f) | Nhỏ | `M3-ngang-tim-banphim`, `M2-ngang-4-tim` |
| UI-07 | **Chữ ≥ 1,5** trên M3 dọc (F2, F3) | Thư viện, Nguồn, menu ⋮ | F2 (1,5): chip “Nguồn” bị cắt nửa ở mép phải. F3 (2,0): hàng chip lọc mất 1–2 chip, ô tìm cắt chữ (“Tìm trong thư viện đ…”), nút “Phát ngẫu nhiên” cắt (“Phát ngẫu”), menu ⋮ cắt “Thêm vào Playli…”. Chữ 1,3 thì **không** lỗi | (a) cắt / tràn chữ → xuống dòng hoặc bọc hàng | Xấu rõ | `F15-M3-doc-1-thuvien`, `F20-M3-doc-1-thuvien`, `F20-M3-doc-5-nguon`, `F20-M3-doc-3-menu` |
| UI-08 | F3 (2,0) trên M6 dọc (600 dp) | Thanh tab dưới của mini-player | Nhãn tab cắt: “Thư”, “Telegra” | (a) | Nhỏ | `F20-M6-doc-1-thuvien` |

### Không phải lỗi, nhưng nên biết

- **Chữ lớn nhất (2,0) vẫn dùng được ở Đang phát, Tiếp theo và Cài đặt:** tiêu đề xuống dòng, chip kỹ thuật bọc hàng, thanh tua và hàng điều khiển luôn ở đáy và nhìn thấy. Cài đặt không cắt chữ quan trọng (chỉ rút gọn “Đồng bộ Playlist giữ…”).
- **Tablet dọc M5 / M6 / M7 và Hai khung M6 / M7 ngang:** chỉ là bố cục điện thoại giãn rộng; không cắt, không chồng, nút đủ lớn. M7 ngang (1280 × 648) có cột Đang phát bên trái + danh sách ≈ 5 dòng: tốt nhất trong các cỡ.
- **Chia đôi M9 (Gọn, 435 dp cao):** dùng được; Thư viện thấy 3 dòng, thanh mini-player và tab đủ nút, Đang phát cuộn được, bàn phím không che cửa sổ trên.
- **Menu ⋮ dòng bài:** hiện đủ 5 mục, không vượt màn, ở mọi cỡ đã chụp được.
- **Thẻ “Nghe tiếp” trên mini-player:** lần chạm đầu vào mũi tên `^` tiếp tục phát bài đang dừng thay vì mở Đang phát (hành vi, không phải bố cục; xem mục 5).
- **Hộp thoại mời Car Mode (≥ 1200 dp ngang):** không thấy xuất hiện ở M7 ngang (1280 dp) và M8 (1920 dp). Có thể đã được hỏi ở lần trước (cờ `alreadyAsked`); không kiểm được thêm vì không xóa dữ liệu app.

## 3. Việc cần chủ dự án quyết trước chặng B

1. **Gạch bớt lỗi không muốn sửa.** UI-01 … UI-05, UI-07, UI-08 thuộc GD4 (a, b, d, e); UI-06 là Đề xuất. Mặc định chặng B sửa UI-01 … UI-05, UI-07, UI-08 theo GD4 (UI-06 không sửa), trừ khi chủ dự án gạch.
2. **Câu 2 mục 9 (360 × 640 → Gọn):** chưa trả lời được từ khảo sát (xem 4). Nên giữ nguyên.
3. **Câu 3 mục 9 (mời Car Mode ở ≥ 1200 dp ngang):** không quan sát được vì hộp thoại không hiện (xem trên). Giữ nguyên.
4. UI-01 và UI-02 đụng phần đầu Thư viện dùng chung, có thể làm Gọn và Car xê dịch. Cần hồi quy bằng ảnh M3, M9 và test có sẵn ở chặng B/C.

## 4. Giới hạn, phần chưa kiểm

- **Không tái hiện được Gọn bằng `wm size`.** M1 (720×1280 / 320 → 360 × 537 dp) ra **Điện thoại dọc**, không phải Gọn: app xét “tấm màn nhỏ” từ kích thước màn **vật lý** (Sony 411 × 960 dp), không theo `wm size`. Gọn thật chỉ tái hiện bằng chia đôi màn (M9) hoặc công cụ giả lập có sẵn trong app (Cài đặt → “Thông tin màn hình & giả lập”, không dùng ở chặng này).
- **M8 (1920 dp) chỉ có ảnh Thư viện.** Các màn sau không chụp được: script (uiautomator) không dựng được cây UI ở kích thước 608×1080 / density 90. Hai trong ba ảnh đã hỏng khi chụp, đã loại.
- **F3 (2,0) M6 ngang** chỉ có 5 ảnh (1, 2, 4, 5, 6); thiếu menu ⋮ và Cài đặt vì lượt chạy bù bị mất kết nối ADB giữa chừng.
- **M5 ngang, M4 ngang, M1 ngang** không chụp (không nằm trong ma trận ngang của plan).
- **Âm thanh thật** chưa kiểm: không có nguồn âm độc lập, và không phát nhạc trong phiên bổ sung (xem 5).
- **Chạm, cảm ứng, kích thước vật lý** của tablet/máy gập chỉ là mô phỏng trên Sony: không thay cho QA thiết bị thật.

## 5. Thiết bị đã khôi phục và các sự cố

- **Giá trị gốc:** `wm size` Physical 1080×2520 (không Override), `wm density` Physical 420, `font_scale` 1.0, `user_rotation` 0, `accelerometer_rotation` 1. **Giá trị cuối (22:09):** đúng như vậy, không còn Override; màn OFF (`SONY_SCREEN_END=OFF` lúc 22:09:10; `Display State=OFF`, `mWakefulness=Dozing`).
- Mỗi cấu hình **khôi phục ngay** trong `finally`. **Hai lần phần khôi phục không chạy hết** do ADB chớp rớt (đổi `transport_id`):
  - sau lượt chạy bù M8 (cuối 22:05): máy còn Override 608×1080 / 90 khoảng 2 phút;
  - sau lượt chạy bù F20 M6 ngang (cuối 22:08): `font_scale` 2.0 và `accelerometer_rotation` 0 khoảng 1 phút.

  Cả hai đã phát hiện ở lần kiểm kế tiếp và khôi phục thủ công ngay, rồi kiểm lại. Không có lần nào máy bị kẹt cỡ lạ khi không ai để ý.
- **Phát nhạc:** bài `#140 - EM MUON TU DO - HV REMIX.mp3` luôn ở trạng thái **tạm dừng** trong cả phiên bổ sung (`state=2`). Hai việc không có chủ ý:
  1. Lượt 1: một lần chạm vào mũi tên `^` đã tiếp tục phát bài ≈ 15–30 giây, đã tạm dừng lại ngay.
  2. Phiên bổ sung: các cú vuốt của script đã chạm thanh tua và **kéo vị trí bài** từ 00:46 → 03:27 → 02:48 (cuối). Hàng đợi vẫn 1 bài, không đổi bài. Vị trí phát gốc **không khôi phục chính xác** được; chủ dự án có thể kéo lại thanh tua nếu muốn.
- Chia đôi M9: đã thoát (cửa sổ `fullscreen`). Cửa sổ thứ hai là bản `app.melovault.player.open` (màn đăng nhập của chủ dự án), chỉ hiển thị, không thao tác. Cuối phiên đã đưa bản `.open` trở lại trước như lúc nhận máy.
- Không đăng xuất, không uninstall, không `pm clear`, không máy bay, không đổi tài khoản. Không có crash/ANR của MeloVault trong `logcat -b crash`.
- Ảnh có tên bài, tên kênh Telegram; vài ảnh đa nhiệm (`M9-recents*`, `M9-chon-app-thu-hai`) còn có hình thu nhỏ Chrome. Giữ private, không commit, không đưa lên Drive.
