# Nghiệm thu — 2026-11-07-sua-bo-loc-dang-phat-ngang

**ĐẠT — PASS kèm giới hạn.** Chủ dự án tự nhắn "ĐẠT, giữ N1" trong chat ngày 07/11/2026, đáp câu hỏi về bản `f054637` sau [sơ bộ](2026-11-07-sua-bo-loc-dang-phat-ngang-nghiem-thu-so-bo.md). Tin nhắn không nói chủ dự án đã nghe thử hay chưa; không suy nó thành xác nhận âm thanh hay bằng chứng QA mới.

- **Bản được duyệt:** APK Debug QA, `app.melovault.player`, vc122 / `0.6.97-beta`, 33.910.894 B, SHA-256 `7FB940219704E78A754971D9773DE5CE93CE18422277C53262EC144890FB7A24`, source `f05463709faf532949d58a31ef7a46c67211cfd6`. Link và chi tiết: [apk-test](2026-11-07-sua-bo-loc-dang-phat-ngang-apk-test.md), [hình](2026-11-07-sua-bo-loc-dang-phat-ngang-hinh-nghiem-thu.md). **Chưa phải bản phát hành.**
- **Phạm vi:** D1 bỏ nút ☰ Bộ lọc ở kiểu mặc định và màn Gọn; D2 dạng ngang thấp (bìa cạnh tên, EQ và Hẹn giờ cùng hàng nút đóng); D3 chạm lại chip thể loại để bỏ lọc; D4 căn thẳng EQ và Hẹn giờ.
- **Kết quả kỹ thuật (không thay quyết định chủ dự án):** Debug/Release mỗi loại 207 suite, 2.799 test, 21 skipped, 0 lỗi; Sony Q1–Q6 theo [báo cáo](2026-11-07-sua-bo-loc-dang-phat-ngang-bao-cao.md) mục 4; sơ bộ không phát hiện lỗi chặn.
- **Lỗi đã biết, chủ dự án chọn giữ (N1):** ở dạng ngang thấp, tên 3 dòng cộng dòng trạng thái ("Tạm dừng") làm hàng Đảo/Lặp/Lưu mất đáy 16 px (110/126 px) và nhảy 16 px khi đổi giữa phát và tạm dừng; vuốt cột trái thì lộ đủ. Không sửa trong nhiệm vụ này.
- **Giới hạn đã vào nhánh tích hợp:** **CHƯA XÁC NHẬN ÂM THANH THỰC** bởi agent; máy 16:9, tablet, xe, chia đôi cửa sổ, chữ lớn, Teal, Gọn chưa thử trên máy; lỗi phát có nút "Thử lại" ở dạng ngang thấp chưa thử; màn dọc không so được với APK baseline cùng bài; tải APK bằng trình duyệt chưa thử.
- **Lệch ui-spec có chủ ý:** nút bộ lọc chỉ còn ở kiểu Teal (đã ghi trong `docs/ui-spec.md`).
- **Trạng thái merge/push:** xem mục đầu `HANDOFF.md` (chủ dự án chỉ thị 02/11: sau mỗi ĐẠT agent tự merge và push lên nhánh `source/…` của repo công khai; phát hành APK công khai vẫn chưa làm).
