# Nghiệm thu — 2026-11-01-nhieu-playlist-co-ten

**Quyết định chủ dự án (01/11/2026): ĐẠT — PASS kèm giới hạn.**

- **Nguồn xác nhận:** chủ dự án nhắn trong chat (hội thoại Claude Code) “Đạt”, đáp tin nhắn của agent đề nghị phản hồi `ĐẠT 2026-11-01-nhieu-playlist-co-ten — fe518f5/5B2FB5CC` hoặc `Yêu cầu sửa …`. Ghi theo đúng lời chủ dự án; agent không suy diễn thêm.
- **Phạm vi được duyệt:** TASK_ID `2026-11-01-nhieu-playlist-co-ten`, nhánh `dot/2026-11-01-nhieu-playlist-co-ten`, mã tính năng đến `751b623`, hồ sơ cuối `fe518f5`; APK QA CANDIDATE Debug vc121 `0.6.96-beta`, SHA-256 `5B2FB5CC323B486ADFC2F359BF7C9FCC20E104D0D6FDE4DB3D2D3B3BDE03FEF8` (build từ `0efec5a`). Hồ sơ: [apk-test](2026-11-01-nhieu-playlist-co-ten-apk-test.md), [hinh-nghiem-thu](2026-11-01-nhieu-playlist-co-ten-hinh-nghiem-thu.md), [phụ lục Chặng 4](2026-11-01-nhieu-playlist-co-ten-bao-cao-chang4.md).
- **Không có nghiệm thu sơ bộ độc lập:** chủ dự án quyết định ĐẠT trước khi mở phiên sơ bộ; `*-nghiem-thu-so-bo.md` **chưa tồn tại** và không được coi là đã làm. Bằng chứng kỹ thuật chỉ gồm gate Ni (2683 test / 21 skipped / 0 failures / 0 errors mỗi biến thể; assemble Shareable Debug/Release, `-PmeloOpen=true`, Personal Debug BUILD SUCCESSFUL) và QA Sony của agent thực thi (Q1–Q6 PASS, Q5 theo điều chỉnh).
- **Giới hạn đã vào bản ĐẠT (chủ dự án chấp nhận khi ĐẠT):**
  - **CHƯA XÁC NHẬN ÂM THANH THỰC** (Media3 PLAYING chỉ chứng minh luồng phát).
  - Đồng bộ hai máy Q7–Q9: **BLOCKED — chưa QA đồng bộ hai máy thật**; đồng bộ Telegram thật cho Playlist có tên (`#mvlist`) chưa chạy trên máy; E105 vẫn BLOCKED nghiệm thu.
  - Khi ▶ Phát một Playlist, hàng chờ chỉ gồm bài Telegram; bài trên máy phải chạm từng bài (khác Q5 gốc của kế hoạch).
  - Sắp xếp là hộp ↑/↓; Gọn dùng hộp danh sách làm chỉ mục; cờ `NAMED_ENABLED` ở `NamedPlaylistFlags.kt`; một công tắc đồng bộ chung.
  - Tên Playlist có dấu/emoji chưa thử trên máy (QA dùng tên không dấu); Release/`.open`/Personal chưa chạy trên máy; chữ lớn, TalkBack, tablet/Hai khung/Gọn trên máy thật chưa kiểm.
  - Lỗi có sẵn SB-1 (lề màn Hàng đợi sát mép) chưa sửa; nhận xét thẩm mỹ nhỏ ở phụ lục Chặng 4.
- **Merge/push/public:** chưa. Chỉ merge khi có chỉ thị merge riêng của chủ dự án; push/public cần chỉ thị riêng. Agent không tự mở nhiệm vụ kế.
