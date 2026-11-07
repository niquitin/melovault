# Nghiệm thu — 2026-11-03-widget-dang-phat

**ĐẠT — PASS kèm giới hạn.** Chủ dự án tự nhắn "ĐẠT widget" trong chat ngày 07/11/2026, đáp câu hỏi về bản `be61487/58BD55F0` sau [sơ bộ vòng 2](2026-11-03-widget-dang-phat-nghiem-thu-so-bo.md). Tin nhắn không nói chủ dự án đã nghe thử hay chưa; không suy nó thành xác nhận âm thanh hay bằng chứng QA mới.

- **Bản được duyệt:** source `be61487dc387b05db6798cdaf74ae222b07e7eef`, Debug QA, `app.melovault.player`, vc122 / `0.6.97-beta`, 33.937.332 B, SHA-256 `58BD55F0B5C22A256E6572516C277A3049A617F853A1830B30280F70AD48FD07`. **Chưa phải bản phát hành.**
- **Phạm vi:** widget Đang phát trên Home, 2 lỗi của sơ bộ vòng 1, tích hợp baseline vc122 `f82185b`.
- **Kết quả kỹ thuật (không thay quyết định chủ dự án):** Debug/Release mỗi loại 208 suite, 2.812 test, 21 skipped, 0 lỗi; QA Sony theo [báo cáo](2026-11-03-widget-dang-phat-bao-cao.md) và sơ bộ vòng 2.
- **Đã biết, chủ dự án duyệt khi còn các điểm này:** widget trên launcher Xperia mặc định 3×3, cao và trống nhiều ở giữa, chỉ kéo được ngang (O1); "Nghe tiếp" với bài ngoài danh sách đang hiện có hàng đợi 1 bài, nút Tiếp/Trước tắt (O2, suy luận không do widget).
- **Giới hạn:** **CHƯA XÁC NHẬN ÂM THANH THỰC** bởi agent; chữ lớn, TalkBack, API 30, màn cũ, Shuffle, Y700, S25, FYT, Release và `.open` chạy trên máy chưa kiểm.
- **Bản gộp:** sau merge, cây mã khác cả hai nhánh nguồn nên APK `58BD55F0…` không đại diện cho bản gộp. Bản gộp chưa có APK và chưa QA trên máy; phiên phát hành beta sẽ làm.
- **Trạng thái merge/push:** xem mục đầu `HANDOFF.md`.

Bằng chứng: [báo cáo](2026-11-03-widget-dang-phat-bao-cao.md) · [APK](2026-11-03-widget-dang-phat-apk-test.md) · [ảnh](2026-11-03-widget-dang-phat-hinh-nghiem-thu.md).
