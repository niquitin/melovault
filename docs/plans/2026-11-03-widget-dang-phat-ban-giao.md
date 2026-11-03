# Bàn giao — Widget Đang phát
Checkpoint 0 · 03/11/2026 · Codex lập kế hoạch. Chủ dự án đã chọn widget Đang phát gồm bìa, tên bài, ca sĩ và ba nút điều khiển. Chủ dự án giao model khác thực thi.

| Mục | Trạng thái |
|---|---|
| TASK_ID / nhánh | `2026-11-03-widget-dang-phat` / `dot/2026-11-03-widget-dang-phat` |
| Worktree trên Ni | `C:\Users\ntdun\.codex\worktrees\melovault-widget\MeloVault-E91-Shareable` |
| SHA nguồn trước lập kế hoạch | `b90834f44457b60a9732e59e59c1bc9c971368c3` (tích hợp sau Cài đặt) |
| Mốc kế hoạch | Commit tài liệu mang tên `docs(widget): plan Android now playing widget and implementation handoff`. Lấy SHA bằng `git log -1 --format=%H -- docs/plans/2026-11-03-widget-dang-phat.md` ngay đầu phiên thực thi. |
| Đã làm | Đối chiếu manifest Shareable, lifecycle service, NextAwarePlayer, đường metadata/ảnh Telegram và local, chọn phạm vi với chủ dự án. Đã viết [kế hoạch chi tiết](2026-11-03-widget-dang-phat.md). |
| Đầu ra đã tạo | Chỉ hai file kế hoạch và bàn giao. Không có source widget, APK, log test, ảnh QA hay kết luận nghiệm thu. |
| Việc tiếp theo | **A0**, kiểm checkout/gate phiên mới, sau đó A1–A7 trên Ni. Sau commit mã mới dùng SHA đó cho chặng B. |
| Ràng buộc thiết bị | Sony đang giữ bản EQ, không dùng cho widget. Chặng B kiểm launcher bằng máy QA riêng và Shanling nếu đủ gate; ghi rõ mọi giới hạn. |

## Prompt giao phiên thực thi mới
```text
MELOVAULT — THỰC THI WIDGET ĐANG PHÁT
TASK_ID: 2026-11-03-widget-dang-phat
Worktree: C:\Users\ntdun\.codex\worktrees\melovault-widget\MeloVault-E91-Shareable
Nhánh: dot/2026-11-03-widget-dang-phat
Baseline: b90834f44457b60a9732e59e59c1bc9c971368c3 + commit kế hoạch trên chính nhánh.
Đọc CLAUDE.md mục 0 trước, AGENTS.md, chỉ mục HANDOFF/BACKLOG, quy trình ba vai, file -ban-giao.md và docs/plans/2026-11-03-widget-dang-phat.md. Kiểm Git đầu phiên; bắt đầu A0. Chỉ một agent sửa code mỗi lúc.
Chủ dự án chọn widget Home Android hiển thị bìa thật nếu có, tên bài, ca sĩ và Bài trước / Phát hoặc Tạm dừng / Bài tiếp. Làm theo mục 4–7 của plan. Chú ý performer khác tên nguồn và dùng NextAwarePlayer cho bài Telegram chưa tải. Không đụng nhánh/tính năng EQ và không cài hay QA trên Sony đang giữ EQ.
Thực hiện chặng A trên Ni, ghi checkpoint để có thể đổi model. Sau đó theo chặng B để bàn giao APK + ảnh nếu đủ gate; ghi BLOCKED đúng bước nếu thiếu điều kiện. Tạo báo cáo và cập nhật bàn giao ngay sau mỗi mốc. Commit trên nhánh nhiệm vụ. Kết thúc chặng cuối thì đưa prompt cho phiên sơ bộ mới; không tự nghiệm thu, merge hay push.
```
