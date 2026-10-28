# Bàn giao — 2026-10-29-quy-trinh-md-ban-giao

- Ngày: 29/10/2026. Agent: Codex; model/effort không được runtime cung cấp. Vai: cập nhật quy trình theo yêu cầu trực tiếp, không phải code tính năng.
- Yêu cầu: ba vai agent qua phiên mới, model thực thi tiết kiệm hơn do chủ dự án chọn, chủ dự án nghiệm thu chính thức; Sony dùng an toàn cả ba vai; Markdown chi tiết để Codex/Claude Code tiếp tục liên tục.
- Worktree: `C:\Users\ntdun\.codex\worktrees\workflow-md-handoff\MeloVault-E91-Shareable`.
- Nhánh: `dot/2026-10-29-quy-trinh-md-ban-giao`.
- Baseline và HEAD trước commit tài liệu: `7f3cca874e525956a10f94f8caf577c6cc80f815`. Source ứng dụng không đổi. Commit kế hoạch/báo cáo/checkpoint cùng commit tài liệu; tra `git log -1 -- docs/plans/2026-10-29-quy-trinh-md-ban-giao-ban-giao.md` sau commit.
- [Kế hoạch](2026-10-29-quy-trinh-md-ban-giao.md); [báo cáo](2026-10-29-quy-trinh-md-ban-giao-bao-cao.md); [quy trình chung](../workflow/QUY_TRINH_3_VAI_TRO.md).

| Bước | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| S1 | Xong | CLAUDE.md mục 0/2/5/10, AGENTS.md đồng bộ vai/model, Sony, `.md` và checkpoint. |
| S2 | Xong | QUY_TRINH_3_VAI_TRO.md có bộ file, checkpoint, mẫu tiếp tục và quy tắc nhận phần chưa commit. |
| S3 | Xong | Các mẫu Codex/Claude không còn bắt buộc model theo nhỏ/lớn hoặc tự gộp kế hoạch/code. |
| S4 | Xong | Mẫu sơ bộ mới, mẫu nghiệm thu chính thức và chuyển hướng mẫu Opus cũ. |
| S5 | Kiểm xong, bàn giao bằng commit tài liệu | 13 file Markdown, 26 liên kết nội bộ hợp lệ, diff --check sạch; commit chứa checkpoint tra bằng lệnh phía trên. |

## Trạng thái dữ liệu và công cụ

- Toàn bộ thay đổi của phiên này là 13 `.md` liệt kê trong báo cáo và diff so với baseline, được đưa vào cùng commit chứa checkpoint; không có sửa mã ứng dụng hoặc phần việc cố ý để dở. Phiên nhận phải kiểm `git status --short`; nếu xuất hiện thay đổi ngoài commit, không tự coi đó là phần của phiên này.
- Không chạy Gradle, build/cài APK, ADB hay dùng Tiny. Không có tác vụ nền do phiên này tạo; không đọc/copy/hash bí mật.
- APK/config/QA mới: không có. `SONY_SCREEN_END = KHÔNG THAO TÁC`; `SHANLING_SCREEN_END = KHÔNG THAO TÁC` (không xác minh trạng thái màn hình, không gọi ADB).
- Repo chính `D:\Bots\MeloVault-E91-Shareable` thuộc phiên khác: nhánh `dot/2026-10-29-hoc-hoi-ui-ux-telegram-music`, HEAD `7f3cca8`, untracked `.codex-remote-attachments/` và `docs/plans/2026-10-29-hoc-hoi-ui-ux-telegram-music.md`. Không đưa chúng vào commit này.
- Không sửa kết luận E105 hay lịch sử nghiệm thu. Không merge/push. Quy trình mới chỉ nằm trên nhánh/worktree này cho tới chỉ thị đưa sang nhánh khác.

## Bước tiếp và prompt dùng khi chuyển Codex/Claude Code

Điểm kết thúc phiên là commit chứa checkpoint này rồi dừng, không có tác vụ nền; không tự mở việc tiếp. Nếu chủ dự án giao kiểm/sửa thêm hoặc áp dụng sang nhánh khác, xác minh commit và trạng thái mới trước; không tự tích hợp khi chưa có chỉ thị phù hợp.

```text
Đọc CLAUDE.md mục 0 và hồ sơ TASK_ID 2026-10-29-quy-trinh-md-ban-giao
trong worktree C:\Users\ntdun\.codex\worktrees\workflow-md-handoff\MeloVault-E91-Shareable,
nhánh dot/2026-10-29-quy-trinh-md-ban-giao.
Đọc kế hoạch, báo cáo và file -ban-giao.md; xác minh Git/commit/checkpoint.
Đây là thay đổi quy trình riêng, chưa merge/push; repo chính có việc của phiên khác.
Chỉ tiếp tục việc chủ dự án giao cụ thể; không code tính năng/build/cài APK.
Nếu phiên trước dừng bất ngờ, kiểm S5 và diff thực trước khi tiếp tục; phần lạ thì dừng.
```
