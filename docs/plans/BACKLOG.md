# BACKLOG — việc còn lại (18/10/2026)

Mỗi phiên làm đúng 1 việc: tạo `docs/plans/<việc>.md` (mục tiêu, phạm vi, file đụng, test, tiêu chí xong), làm, cập nhật `HANDOFF.md`, dừng. Việc đổi schema DB phải được chủ dự án duyệt trước.

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

## 7. Sửa 5 lỗi QA Y700 b99 — ĐÃ LÀM ở Beta100 (18/10/2026, `18bce8b`), còn phần B của lỗi 1
- Theo `docs/plans/sua-bo-cuc-y700.md`: lỗi 1 phần A (phản hồi khi bấm bài, log `MeloVaultPlay`, nhắc sau 15 s nếu Telegram không gửi dữ liệu), lỗi 3 (núm tua), lỗi 2 (ảnh bìa theo chiều cao còn lại + dạng gọn khi màn thấp + hàng Đảo/Lặp/Lưu/Hàng đợi xuống 2 dòng khi hẹp), lỗi 4 (về đầu danh sách khi đổi nhóm; cuộn khung đổi kênh), lỗi 5 (tiêu đề nhóm xuống dòng riêng khi hẹp, nút chip không xuống dòng). Sony QA OK; chờ chủ dự án QA Y700.
- **Lỗi 1 phần B (nguyên nhân gốc kênh "Sưu Tầm Những Năm Về Trước" trên Y700) chưa sửa — cần ảnh/log từ Y700 sau khi cài b100.**
- Còn lại: nút ⋮ trên dòng bài ở màn mới phát bài thay vì mở menu (chỉ giữ lâu mới ra menu); mini player khi tạm dừng dòng bài vẫn ghi "Đang phát"; chính sách tải trước với tệp rất lớn (cần chủ dự án quyết ngưỡng).
