# Bàn giao — Widget Đang phát
Checkpoint A hoàn tất · 03/11/2026 · Codex thực thi, phiên đang hoạt động; không có agent phụ. Chuẩn bị commit source rồi tiếp B.

- HEAD bắt đầu/commit kế hoạch: `192170642163c69229c8945b54c26f7ac2d5a80f`; baseline `b90834f44457b60a9732e59e59c1bc9c971368c3`.
- Git sạch khi bắt đầu; fetch `github` thành công; ahead 1 / behind 0 so với nhánh tích hợp remote, chưa có upstream.
- [Báo cáo thực thi](2026-11-03-widget-dang-phat-bao-cao.md). A0–A7 xong gate Ni; chưa QA launcher/Telegram/âm thanh thật. Source commit sẽ chứa checkpoint này; lấy SHA bằng git log thay vì tự tham chiếu.
- Chưa commit: bốn lớp widget mới, manifest, LocalSourcePolicy, PlaybackService, PreviewActivity, tài nguyên Shareable, test policy và hai hồ sơ. Đối chiếu danh sách bằng git status/diff; không nhận thay đổi ngoài danh sách này.
- Targeted đã BUILD SUCCESSFUL, 12 test / 0 skipped / 0 lỗi. Full lượt 2 BUILD SUCCESSFUL: mỗi Debug/Release 202 suite, 2.737 test / 2.716 passed / 21 skipped / 0 failure/error, log `a7-tests-r2-20261103.log`. Hai lượt thất bại trước đã lưu lý do trong báo cáo.
- Assemble Shareable Debug/Release BUILD SUCCESSFUL 2m13s; .open 26s; Personal 28s. `telegram.config.valid=false` trên Ni; không cài/bàn giao các APK Ni. Merged manifests đã đối chiếu: widget có ở Shareable/.open, vắng Personal, exported=false, không quyền mới. Không còn Gradle job nhiệm vụ đang chạy.
- Bước tiếp: commit explicit source/test/hồ sơ, ghi SHA nguồn; bundle chỉ nhánh widget, kiểm hash Ni/Tiny; clone Tiny cô lập, build cấu hình an toàn tại Tiny; gate APK rồi QA trên AVD riêng. Chưa có bundle/candidate Tiny.
- Tài nguyên QA chuẩn bị: AVD mới `MeloVault_Widget_20261103`, serial riêng `emulator-5586`, launcher process Ni PID `22620`, no-window/no-audio/no-snapshot; Android API 36 x86_64. Chưa cài app. Không dùng AVD cũ.
- Fixtures vô danh trong `artifacts/.../fixtures/`: âm thử A có ảnh nhúng hình vuông vàng/xanh/tím và tag ca sĩ `Nghe si QA A`; âm thử B thiếu bìa/ca sĩ. Không phải nhạc/dữ liệu người dùng; phải dọn khỏi AVD sau QA, giữ ảnh/log cần nghiệm thu.
- Sony không dùng. Shanling đã bán theo luật gốc mới hơn; nội dung nhắc Shanling trong kế hoạch là lỗi thời. B chỉ QA trên môi trường riêng khi có đủ gate; không thay launcher/bản EQ.
- Cleanup: chưa tạo tài nguyên tạm. Model/effort không được công cụ phiên cung cấp, không tự suy đoán.

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
