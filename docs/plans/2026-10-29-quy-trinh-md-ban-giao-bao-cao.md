Thực thi: Codex (model/effort không được runtime cung cấp), phiên cập nhật quy trình 29/10/2026.

# Báo cáo — quy trình Markdown và tiếp tục Codex/Claude Code

| Hạng mục | Kết quả |
|---|---|
| Yêu cầu | Lập kế hoạch chi tiết → phiên mới model tiết kiệm hơn code/build APK → phiên mới sơ bộ nhanh → chủ dự án nghiệm thu chính thức, chi tiết. |
| Sony | Ghi quyền chủ động dùng ở cả ba vai agent, giữ gate ADB/cài APK/bí mật/dữ liệu/màn hình và phân biệt chứng cứ thiết bị. |
| Tiếp nối | Bắt buộc kế hoạch/báo cáo `.md` chi tiết và `*-ban-giao.md` cập nhật từng mốc; mẫu nhận bàn giao Codex ↔ Claude Code trên cùng TASK_ID/worktree. |
| Thay đổi ứng dụng/APK | Không có; chỉ tài liệu theo yêu cầu trực tiếp. |
| Git | Nhánh `dot/2026-10-29-quy-trinh-md-ban-giao`, baseline `7f3cca874e525956a10f94f8caf577c6cc80f815`; commit tài liệu tra bằng `git log -1 -- docs/plans/2026-10-29-quy-trinh-md-ban-giao-bao-cao.md`. |

## File và các bước đã làm

| Bước | File | Thay đổi |
|---|---|---|
| S1 | CLAUDE.md, AGENTS.md | Luật ưu tiên 29/10; ba vai agent tách phiên, model do chủ dự án chọn; báo cáo/checkpoint; chính thức chỉ chủ dự án quyết định. |
| S2 | docs/workflow/QUY_TRINH_3_VAI_TRO.md | Nội dung tối thiểu từng `.md`; checkpoint cả phần dở/chưa commit/tác vụ nền/test/APK; xác minh trước khi nhận tiếp, không chạy hai agent sửa code. |
| S3 | PROMPT_CODEX.md, PROMPT_OPUS.md, PROMPT_SONNET.md | Đồng bộ vai, model, dùng Sony, kế hoạch chi tiết và prompt tiếp tục; giữ tên file để không hỏng liên kết lịch sử. |
| S4 | PROMPT_NGHIEM_THU_SO_BO.md, PROMPT_CHU_DU_AN_NGHIEM_THU.md, PROMPT_OPUS_NGHIEM_THU.md | Mẫu kiểm nhanh phiên mới; kết luận sơ bộ tách khỏi PASS chính thức; lỗi cần chủ dự án giao REWORK. |
| S5 | HANDOFF.md và ba hồ sơ TASK_ID này | Chỉ mục, kế hoạch, báo cáo và bàn giao; giữ lịch sử, nêu rõ worktree riêng/chưa merge. |

## Kiểm tra kỹ thuật

- Đầu phiên đã chạy status/branch/HEAD/log/diff --check. Repo chính có 2 mục untracked của phiên khác, nhánh UI/UX; giữ nguyên. Tạo worktree riêng theo ngoại lệ tài liệu của CLAUDE.md mục 1, từ đúng HEAD `7f3cca8`, rồi tạo nhánh nhiệm vụ.
- `git diff --check` sau sửa luật/mẫu: không lỗi khoảng trắng. Rà quy tắc cũ và sửa chỗ cho phép chạy lại build trong vai sơ bộ để thống nhất: cần APK mới thì giao phiên thực thi.
- Kiểm nội dung diff và phạm vi: chỉ **13 file `.md`**, không đổi ứng dụng/build/signing/artifact. Đối chiếu tự động xác nhận toàn bộ mục “Danh sách CẤM tuyệt đối” trong AGENTS.md và phần lịch sử HANDOFF.md không đổi.
- Kiểm Markdown: **26 liên kết file nội bộ** hợp lệ trong bộ tài liệu sửa (HANDOFF chỉ kiểm mục mới); không có khối code chưa đóng. `git diff --check` sạch. Không kiểm chạy thực tế các prompt bằng phiên model khác.
- Repo chính kiểm lại vẫn cùng HEAD/nhánh, 2 mục untracked; kế hoạch UI/UX vẫn thời gian sửa `29/10/2026 00:34:40 +07`, 13.626 byte. Không sửa/copy/commit phần đó.
- Không chạy Gradle/unit test/build do chỉ đổi `.md`. Tests passed/skipped/failed: **KHÔNG CHẠY**, không ghi 0 như đã chạy.
- Không dùng ADB/Tiny/điện thoại. `SONY_SCREEN_END = KHÔNG THAO TÁC`; `SHANLING_SCREEN_END = KHÔNG THAO TÁC`, không tuyên bố xác minh OFF.

## Chưa kiểm chứng và rủi ro

- Chưa diễn tập chuyển công cụ khi quota cắt thật; checkpoint quy định phải xác minh Git/file/log, không bảo đảm khôi phục phần ngữ cảnh chưa ghi ra đĩa.
- Quy tắc mới ở worktree `C:\Users\ntdun\.codex\worktrees\workflow-md-handoff\MeloVault-E91-Shareable`; chưa áp dụng vào cây làm việc chính hoặc nhánh tích hợp, chưa merge/push. Phiên đọc repo chính sẽ còn thấy luật cũ cho tới khi có chỉ thị tích hợp phù hợp.
- Không có APK mới/QA thiết bị, không cần nghiệm thu APK cho phiên sửa quy trình. Không sửa kết luận lịch sử hoặc đánh dấu bất kỳ nhiệm vụ tính năng nào ĐẠT.
- Khôi phục/sửa tiếp bằng commit tài liệu mới theo yêu cầu; không reset/rebase/ghi đè công việc khác.

[Bàn giao và prompt tiếp tục](2026-10-29-quy-trinh-md-ban-giao-ban-giao.md) · [Kế hoạch](2026-10-29-quy-trinh-md-ban-giao.md)
