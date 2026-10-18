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
