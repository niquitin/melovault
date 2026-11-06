# Nghiệm thu — 2026-11-06-hop-nhat-phien-ban

**ĐẠT — PASS kèm giới hạn.** Chủ dự án tự nhắn `ĐẠT 7cd4ebb/BF7B23A7` trong chat ngày 06/11/2026, đáp đề nghị `ĐẠT 2026-11-06-hop-nhat-phien-ban — 7cd4ebb/BF7B23A7`. **Không có nghiệm thu sơ bộ độc lập** (chủ dự án quyết định ĐẠT trước khi mở phiên sơ bộ). Tin nhắn ĐẠT không nói chủ dự án đã nghe thử hay chưa; không suy quyết định này thành bằng chứng QA mới.

- Bản được duyệt: APK Debug QA, `app.melovault.player`, vc122 / `0.6.97-beta`, 33.894.510 B, SHA-256 `BF7B23A74588BD9826CA9DFBFEBE0AEC5DEC61F8F20D5CD5914CCDDC38C56441`, source `7cd4ebb0af7a3f75c6a5c038831f7720b24319df`. Link và chi tiết: [apk-test](2026-11-06-hop-nhat-phien-ban-apk-test.md), [hình](2026-11-06-hop-nhat-phien-ban-hinh-nghiem-thu.md). **Chưa phải bản phát hành.**
- Phạm vi: gộp các tính năng đã ĐẠT trên nhánh tích hợp `42be21c` (gồm chủ đề Telegram và EQ), nâng số phiên bản. **Không gồm Widget Đang phát** (`dot/2026-11-03-widget-dang-phat` chưa ĐẠT, sơ bộ CẦN SỬA).
- Kết quả kỹ thuật của agent thực thi (không thay quyết định chủ dự án): gate Ni Debug/Release mỗi loại 207 suite, 2.796 test, 21 skipped, 0 lỗi; Sony Q1–Q6 PASS theo [báo cáo](2026-11-06-hop-nhat-phien-ban-bao-cao.md) mục 4.
- **Giới hạn đã vào nhánh tích hợp (chủ dự án chấp nhận khi ĐẠT):** **CHƯA XÁC NHẬN ÂM THANH THỰC** của bản hợp nhất bởi agent; phát offline trong chủ đề, mất mạng, FLAC/WAV document trên máy, tai nghe/Bluetooth, Shuffle/Previous trong chủ đề, Gọn/Car/FYT, Y700/S25, chữ lớn, Release/`.open`/Personal chạy trên máy chưa kiểm; tải APK từ Drive bằng trình duyệt chưa thử.
- Quan sát chưa sửa: chip "Hẹn giờ" lệch và bị cắt đáy khi xoay ngang; hàng "Tiếp theo | Lời nhạc" bị che một phần khi màn dọc cho đến khi cuộn; lần chạm đầu vào "Giới thiệu" không mở trang, lần sau mở đúng, chưa rõ nguyên nhân và chưa tái hiện.
- Hệ quả vận hành: Sony đang ở vc122, mọi APK QA sau phải có versionCode ≥ 122 và nhánh mới lấy baseline từ bản này.
- Trạng thái merge/push: xem mục đầu `HANDOFF.md` (chủ dự án chỉ thị 02/11: sau mỗi ĐẠT agent tự merge và push lên nhánh `source/…` của repo công khai; phát hành APK công khai vẫn chưa làm).
