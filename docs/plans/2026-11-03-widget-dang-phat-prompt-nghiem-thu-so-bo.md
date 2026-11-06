# Prompt cho phiên sơ bộ mới — Widget Đang phát

Không phải biên bản/kết luận sơ bộ. Chủ dự án mở phiên mới và tự chọn model/effort. Bản 06/11/2026, thay prompt của candidate `49ed94c`.

```text
MELOVAULT — NGHIỆM THU SƠ BỘ NHANH SAU REWORK
TASK_ID: 2026-11-03-widget-dang-phat
Worktree: C:\Users\ntdun\.codex\worktrees\melovault-widget\MeloVault-E91-Shareable
Nhánh: dot/2026-11-03-widget-dang-phat
Baseline: f82185b (vc122 đã ĐẠT)   Source APK: be61487dc387b05db6798cdaf74ae222b07e7eef
APK: Debug app.melovault.player vc122/0.6.97-beta, config=true
SHA-256: 58BD55F0B5C22A256E6572516C277A3049A617F853A1830B30280F70AD48FD07
Đọc CLAUDE.md mục 0 → docs/plans/2026-11-03-widget-dang-phat-ban-giao.md → mục REWORK trong -bao-cao.md
→ -nghiem-thu-so-bo.md (2 lỗi của lần trước) → -apk-test.md → docs/workflow/PROMPT_NGHIEM_THU_SO_BO.md.
Kiểm Git đầu phiên; git diff be61487..HEAD -- app/ phải rỗng.
Tập trung:
1. Hai lỗi cũ: MeloWidgetPolicy.Generation.update + MeloWidgetSession.NextFence (metadata cùng bài không mở chặn Tiếp,
   token cũ vẫn bị từ chối); TelegramPreviewActivity.handOffTelegramQueue + mayStartPendingTelegram (bài chờ kết nối
   chuyển queue và kiểm owner trước khi phát).
2. Merge: git diff f82185b be61487 -- app/ chỉ gồm phần widget; ShareablePlaybackService.onDestroy giữ cả dọn widget và gỡ listener EQ.
3. Đọc phần "Quan sát chưa sửa" và "Chưa kiểm chứng" trong báo cáo, đặc biệt kích thước 3×3 trên launcher Xperia.
Reuse gate Ni: mỗi Debug/Release 2.812 test, 2.791 đạt, 21 skipped, 0 lỗi. Chỉ chạy lại khi có lý do cụ thể.
Nếu dùng Sony: kiểm serial/model, không đổi dữ liệu/tài khoản, màn OFF trong finally. Bản đang cài đã đúng SHA trên.
Nếu chạy MeloWidgetFixesDeviceTest: chạy từng test một (-e class ...#tenTest -e widgetFixesProbe true) và cần có widget trên Home cho test Tiếp.
Ghi -nghiem-thu-so-bo.md (giữ biên bản cũ làm lịch sử), cập nhật -ban-giao.md: SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN / CẦN SỬA / BLOCKED,
kèm phạm vi và giới hạn (CHƯA XÁC NHẬN ÂM THANH THỰC). Không sửa code, không build mới, không merge/push.
Kết thúc: "Đã dừng, chờ chủ dự án nghiệm thu." + cách phản hồi ĐẠT 2026-11-03-widget-dang-phat — be61487/58BD55F0 hoặc Yêu cầu sửa 2026-11-03-widget-dang-phat: <lỗi>.
```
