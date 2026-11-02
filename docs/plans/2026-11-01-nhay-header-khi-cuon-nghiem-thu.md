# Nghiệm thu — 2026-11-01-nhay-header-khi-cuon

**Quyết định chủ dự án (02/11/2026): ĐẠT — PASS kèm giới hạn.**

- **Nguồn xác nhận:** chủ dự án nhắn trong chat (hội thoại Claude Code của phiên lập kế hoạch): “Đã nghiệm thu tiến trình trên — Đạt”, sau khi phiên thực thi kết thúc với checkpoint 3 trong [bàn giao](2026-11-01-nhay-header-khi-cuon-ban-giao.md). Ghi đúng lời chủ dự án; agent không suy diễn thêm.
- **Phạm vi được duyệt:**
  - TASK_ID `2026-11-01-nhay-header-khi-cuon`, nhánh `dot/2026-11-01-nhay-header-khi-cuon`, baseline `8a8c978`, kế hoạch `8a48584`.
  - **Mã `4fe5a37`**; hồ sơ thực thi cuối `b4d50e1`.
  - APK QA CANDIDATE Debug vc121 `0.6.96-beta`, SHA-256 `E5D808D2C29F70333350EF3682A50FBF686D6140C93513F1A6AA7534A27C6B74` (33.789.892 B, build Tiny từ `4fe5a37`).
  - Hồ sơ: [kế hoạch](2026-11-01-nhay-header-khi-cuon.md), [báo cáo](2026-11-01-nhay-header-khi-cuon-bao-cao.md), [apk-test](2026-11-01-nhay-header-khi-cuon-apk-test.md), [hình nghiệm thu](2026-11-01-nhay-header-khi-cuon-hinh-nghiem-thu.md).
- **Nội dung:** header (hàng “MeloVault” + “Đã đồng bộ”) không còn ẩn/hiện liên tục khi kéo/lướt danh sách bài của giao diện Mới. Header chỉ đổi khi nhả tay hoặc khi danh sách đang trôi. Độ cuộn được đo theo cùng một dòng, ngưỡng 16dp cộng dồn. Các file đổi: `MeloHeaderFoldPolicy.kt` (mới), `MeloLibraryPolicy.kt`, `MeloLibraryPane.kt`, `MeloHeaderFoldPolicyTest.kt`.
- **Không có nghiệm thu sơ bộ độc lập:** chủ dự án quyết định ĐẠT trước khi mở phiên sơ bộ. `*-nghiem-thu-so-bo.md` **chưa tồn tại** và không được coi là đã làm. Bằng chứng kỹ thuật chỉ gồm:
  - gate Ni: 2698 test / 21 skipped / 0 failures / 0 errors mỗi biến thể; assemble Shareable Debug/Release BUILD SUCCESSFUL;
  - QA Sony của agent thực thi: baseline tái hiện 8 và 14 lần đổi mỗi cử chỉ giữ tay; sau sửa Q1–Q5 PASS, mỗi cử chỉ ≤ 1 lần đổi.
- **Giới hạn đã vào bản ĐẠT (chủ dự án chấp nhận khi ĐẠT):**
  - Hành vi mới “header chỉ gấp/hiện sau khi nhả tay hoặc khi trôi” được chấp nhận cùng bản ĐẠT. Muốn header gấp ngay trong lúc kéo, có hoạt ảnh (phương án B), là nhiệm vụ riêng.
  - QA trên tab Thư viện/Tất cả (Sony chỉ có Playlist “Yêu thích · 2 bài”); chưa kiểm Playlist ≥ 20 bài trên máy.
  - Q6 Hai khung/xoay ngang **CHƯA KIỂM**.
  - Cử chỉ giả lập bằng `input motionevent`/`swipe`, chưa kiểm bằng ngón tay thật trên Sony.
  - Q3b thiếu 1 dòng log (video vẫn 2 lần đổi, không nháy); Q5 không đếm riêng số lần tải thêm.
  - Nguyên nhân 2 (đo sai qua ranh giới dòng) chưa tách riêng được trên máy.
  - **CHƯA XÁC NHẬN ÂM THANH THỰC** (không phát nhạc trong QA).
  - Release/`.open`/Personal chưa chạy trên máy; chữ lớn, TalkBack, tablet/máy gập chưa kiểm.
- **Merge/push/public:** chưa. Chỉ merge vào `feature/e93-03d-y700-offline-cockpit` khi có chỉ thị merge riêng của chủ dự án; push/public cần chỉ thị riêng. Agent không tự mở nhiệm vụ kế.
