# Nghiệm thu — 2026-11-02-thiet-ke-lai-cai-dat

**ĐẠT — PASS kèm giới hạn.** Chủ dự án nhắn “đạt” trong chat 02/11/2026, đáp đề nghị nghiệm thu bản `d52fe43`/`F376BCCB…77E7` (đề nghị: `ĐẠT 2026-11-02-thiet-ke-lai-cai-dat — d52fe43/F376BCCB`). **Không có nghiệm thu sơ bộ độc lập** (chủ dự án quyết định ĐẠT trước khi mở phiên sơ bộ).

- Bản được duyệt: APK Debug vc121 `0.6.96-beta`, SHA-256 `F376BCCBAB9B9FD9381FB7EA08068BDD9203AACB4C00078E282560971F8D77E7`, source `d52fe43affdb74422224dcc703321214c00b7a77`. Link và chi tiết: [apk-test](2026-11-02-thiet-ke-lai-cai-dat-apk-test.md), [hình](2026-11-02-thiet-ke-lai-cai-dat-hinh-nghiem-thu.md). Vẫn là QA candidate, **chưa phải bản phát hành**.
- Kết quả kỹ thuật của agent thực thi (không thay quyết định chủ dự án): gate Ni xanh (2725 test, 21 skipped, 0 lỗi); QA Sony Q1–Q7 PASS.
- **Giới hạn đã vào nhánh tích hợp (chủ dự án chấp nhận khi ĐẠT):** lỗi “chữ bị che” chưa tái hiện/chứng minh trên máy Android 15+ (chỉ sửa theo phân tích mã); soạn thư thật chưa kiểm (Sony không có app email); Q8 xoay ngang/chữ lớn chưa kiểm; **CHƯA XÁC NHẬN ÂM THANH THỰC**; Release/`.open`/Personal trên máy chưa kiểm; màn Cài đặt không đổi màu theo “Kiểu màu”; `MeloSyncSettingsActivity` còn mẫu insets cũ.
- Trạng thái merge/push: xem [bàn giao](2026-11-02-thiet-ke-lai-cai-dat-ban-giao.md) và mục đầu `HANDOFF.md` (chủ dự án chỉ thị 02/11: sau mỗi ĐẠT agent tự merge và push lên nhánh `source/…` của repo công khai; phát hành APK công khai vẫn chưa làm).
