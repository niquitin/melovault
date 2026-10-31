# BACKLOG — việc còn lại (18/10/2026)

Mỗi phiên làm đúng 1 việc: tạo `docs/plans/<việc>.md` (mục tiêu, phạm vi, file đụng, test, tiêu chí xong), làm, cập nhật `HANDOFF.md`, dừng. Việc đổi schema DB phải được chủ dự án duyệt trước.

## Cập nhật 31/10/2026 — việc còn mở sau chuỗi 30–31/10

Đã ĐẠT và đã merge (chi tiết ở HANDOFF):
- UI/UX P1 `2026-10-29-melo-ui-ux-ke-hoach`;
- Đảo `2026-10-30-dao-thu-tu-phat`;
- D1–D3 `2026-10-30-don-ui-sau-xoay`;
- P2-A ảnh bìa `2026-10-30-anh-bia-moi-mau`;
- đăng nhập chỉ bằng số điện thoại cho bản open `2026-10-30-dang-nhap-chi-so-dien-thoai`.

Mỗi việc dưới đây cần chủ dự án giao riêng; quy trình 29/10 (lập kế hoạch → thực thi → sơ bộ → chủ dự án).

- **A. P2-B lời nhạc** — chưa làm.
  - Trước khi code phải có phiên lập kế hoạch chốt nguồn/dịch vụ lời nhạc, chính sách dữ liệu và cache.
  - Đã có hợp đồng UI dự kiến: [phụ lục UI/UX mục P2-B](2026-10-29-melo-ui-ux-ke-hoach-chi-tiet.md).
- **B. Giao diện phù hợp mọi loại thiết bị** — định hướng chủ dự án nêu ngày 31/10; chưa có kế hoạch hay phạm vi cụ thể.
- **C. Đăng bản open công khai** — cần:
  - khóa ký phát hành (hiện chỉ có khóa debug của Tiny);
  - chủ dự án đọc điều khoản API Telegram cho app bên thứ ba;
  - chỉ thị public riêng.
- **D. Push** — BLOCKED: repo chưa có remote/URL đích.
- **E. Chưa kiểm** (ghi trong các hồ sơ ĐẠT; mỗi mục cần giao mới thực hiện):
  - Đăng nhập:
    - mã open thật bị Telegram từ chối (câu D7);
    - D8 (lưu mã API sau khi đã gửi tham số TDLib) chưa có test JVM, chưa thử trên máy;
    - người dùng E104 đã lưu mã riêng rồi cập nhật lên bản mới.
  - Đảo: danh sách > 2000 bài; 500 bài nhạc trên máy; Q5b ⏭ ×2 trong QA ảnh bìa.
  - Chung:
    - Release UI trên máy;
    - mất mạng thật;
    - app bị hệ thống tắt rồi mở lại;
    - chữ lớn / bàn phím / TalkBack thật;
    - lỗi FLOOD Telegram thật;
    - nút trên thông báo/màn khóa;
    - âm thanh thật của các bản ngày 30/10.
- **F. Thiết bị** — chỉ còn Sony XQ-AS52 qua Tiny (Shanling đã bán 31/10). Tablet Y700, S25, FYT ngoài phạm vi QA theo quyết định 30/10 17:01, trừ khi chủ dự án giao lại.
- **G. Chỉ nghiên cứu, chưa giao code** — nhiều Playlist có tên; ghim nguồn.

## 1. ~~Thanh A–Z (Thư viện)~~ — ĐÃ LÀM ở Beta97 (18/10/2026, `cfaa993` + `5ebd7dd`)
- Theo `docs/plans/a-z.md`: chip Mới nhất / Tên A–Z, thanh chữ cái, không đổi schema. Sony QA OK; chờ chủ dự án QA Y700 (dọc + Hai khung ngang) và S25. Lưới 3 cột Hai khung trên Y700 vẫn chưa kiểm.
- Còn lại nếu cần: nhóm "…" cho chữ ngoài Latin; chỉ mục `(account_id, is_deleted, search_key)` chỉ khi đo thấy chậm (đổi schema → phải duyệt).

## 2. EQ
- Nút EQ ở Studio Deck hiện ghi "Sắp có". Cần quyết định phạm vi (EQ ExoPlayer/AudioEffect, bit-perfect, DSP FYT) trước khi code. Không đổi ExoPlayer/MediaSession khi chưa có plan.

## 3. Tìm không dấu
- DB dùng `LIKE`, phân biệt dấu: "su thanh hoa" không ra "Sử Thanh Hóa". Cần cột/chỉ mục chuẩn hóa không dấu, có thể đổi schema → phải được duyệt, kèm migration an toàn cho DB đang có.
- Kế hoạch `docs/plans/tim-khong-dau.md`: ĐÃ LÀM ở Beta96 (18/10/2026), chủ dự án đã QA Y700 OK (18/10/2026).

## 4. ~~Sửa test mạng flaky trên Tiny~~ — XONG (18/10/2026, commit `9597332`)
- Nguyên nhân: server giả lập của test RST socket sau `sleep(100)` ở offset cắt; RST làm OS vứt byte client chưa đọc, nên số byte client nhận (`totalRead`, dòng 194) phụ thuộc tốc độ client (Tiny chạy lần đầu chậm hơn → < 128 KB). Tái hiện được trên Ni bằng cách cho client chờ 400 ms.
- Sửa: server chờ cổng `CountDownLatch` (client gọi `markClientDrained` khi đã đọc hết byte trước điểm cắt; chờ tối đa 10 s) rồi mới RST. Không dùng FIN vì JDK client sẽ thấy EOF sạch thay vì IOException (cache tưởng bài chỉ dài 128 KB). Assertion giữ nguyên, không skip.
- Kiểm: 5 lần liên tiếp Debug + 5 lần Release xanh (6/6 test) trên Ni và trên clone cô lập Tiny (ngay sau fast-forward, lần đầu); xanh cả khi cố ý làm client chậm 400 ms. Full suite Ni: 1.132 tests, 21 skipped, 0 failed ở cả hai biến thể.

## 5. ~~Đổi kênh nhanh ở Hai khung + dòng "Các kênh chưa có nhạc (N)"~~ — ĐÃ LÀM ở Beta99 (18/10/2026, `edfe86f`)
- Theo `docs/plans/doi-kenh-nhanh.md`, không đổi schema. Sony QA OK (Hai khung ngang). Chờ chủ dự án QA Y700 ngang và S25 ngang. Dòng "Các kênh chưa có nhạc" CHƯA kiểm trên thiết bị vì tài khoản Sony không có kênh 0 bài (29/29 kênh có bài) — nên thử ở máy nào có kênh 0 bài.
- Ngoài phạm vi, còn lại nếu muốn: đổi kênh nhanh ở Điện thoại/Tablet dọc; ghim/đổi thứ tự kênh; ảnh đại diện kênh; số bài đã tải theo kênh.

## 6. Chip Mixset theo thời lượng
- Chip "Mixset" hiện chỉ lọc theo tên (từ khóa). Làm lọc theo thời lượng (bài dài) cần quyết định ngưỡng và có thể thêm truy vấn/chỉ mục → viết plan trước, có thể phải duyệt nếu đổi schema.

## 0. Thanh đáy gộp — Phần A ĐÃ LÀM ở Beta106 (`fd729a0` + `633ce37`), Phần B (hàng viên chất lượng) ĐÃ LÀM ở Beta107 (18/10/2026, `8eac0ff`); Phần C (thẻ nguồn, chỉ Điện thoại/Tablet) ĐÃ LÀM ở Beta108 (18/10/2026, `112456c`, kế hoạch `docs/plans/the-nguon-nhac.md`); chờ chủ dự án QA Shanling và Y700. Còn mở: "Nhạc trên máy"/Drive chưa có dữ liệu nên chưa có thẻ; số "đã tải" ĐÃ hiện ngay khi mở app ở Beta109 (số lưu + quét nền; chờ nghiệm thu Shanling; còn thiếu cập nhật +1/−1 theo sự kiện tải/xóa)

## 7. Sửa 5 lỗi QA Y700 b99 — ĐÃ LÀM ở Beta100 (18/10/2026, `18bce8b`), còn phần B của lỗi 1
- Theo `docs/plans/sua-bo-cuc-y700.md`: lỗi 1 phần A (phản hồi khi bấm bài, log `MeloVaultPlay`, nhắc sau 15 s nếu Telegram không gửi dữ liệu), lỗi 3 (núm tua), lỗi 2 (ảnh bìa theo chiều cao còn lại + dạng gọn khi màn thấp + hàng Đảo/Lặp/Lưu/Hàng đợi xuống 2 dòng khi hẹp), lỗi 4 (về đầu danh sách khi đổi nhóm; cuộn khung đổi kênh), lỗi 5 (tiêu đề nhóm xuống dòng riêng khi hẹp, nút chip không xuống dòng). Sony QA OK; chờ chủ dự án QA Y700.
- **Lỗi 1 phần B (nguyên nhân gốc kênh "Sưu Tầm Những Năm Về Trước" trên Y700) chưa sửa — cần ảnh/log từ Y700 sau khi cài b100.**
- Nút ⋮ trên dòng bài màn Melo (MeloLibraryPane): ĐÃ SỬA ở Beta102 (18/10/2026, 5e24661); chờ chủ dự án QA Y700. Danh sách Gọn và hàng đợi trong Đang phát: ĐÃ SỬA ở Beta104 (e2845ff), chờ QA thiết bị Gọn/Y700.
- Mini player/dòng bài khi tạm dừng ghi "Đang phát": ĐÃ SỬA ở Beta103 (18/10/2026, f2223a4) cho danh sách Melo; còn dòng hàng đợi trong Đang phát và UI cũ chưa đổi; chờ chủ dự án QA Y700.
- Còn lại: chính sách tải trước với tệp rất lớn (cần chủ dự án quyết ngưỡng).
