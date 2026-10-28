# Bàn giao — 2026-10-29-quy-trinh-md-ban-giao

- Ngày: 29/10/2026. Agent: Codex; model/effort không được runtime cung cấp. Vai: cập nhật quy trình theo yêu cầu trực tiếp, không phải code tính năng.
- Yêu cầu: ba vai agent qua phiên mới, model thực thi tiết kiệm hơn do chủ dự án chọn, chủ dự án nghiệm thu chính thức; Sony dùng an toàn cả ba vai; Markdown chi tiết để Codex/Claude Code tiếp tục liên tục. Bổ sung: Codex và Claude Code ngang quyền/ngang trách nhiệm, dùng chung repo/worktree và công cụ theo vai được giao.
- Worktree: `C:\Users\ntdun\.codex\worktrees\workflow-md-handoff\MeloVault-E91-Shareable`.
- Nhánh nguồn: `dot/2026-10-29-quy-trinh-md-ban-giao`; nhánh làm hồ sơ tích hợp: `dot/2026-10-29-quy-trinh-md-ban-giao-merge`; nhánh đích: `feature/e93-03d-y700-offline-cockpit`.
- Baseline ban đầu: `7f3cca874e525956a10f94f8caf577c6cc80f815`; commit quy trình lần đầu `1a3ef477ac33c78487fea51aedf1b419da063f0e` cũng là HEAD sạch trước bổ sung ngang quyền. Source ứng dụng không đổi. Commit bổ sung chứa checkpoint này; tra `git log -1 -- docs/plans/2026-10-29-quy-trinh-md-ban-giao-ban-giao.md` sau commit.
- Chủ dự án đã yêu cầu “Merge/push vào repo chính đi”. **Merge tài liệu cục bộ đã xong:** `93c22119e82011d54e7db64eecbd1c8f9e22dd3e`, baseline tích hợp `9adeeba`, không gồm mã E105. HEAD trước commit hồ sơ tích hợp là `93c2211`; commit mới chứa checkpoint này được fast-forward vào nhánh tích hợp.
- [Kế hoạch](2026-10-29-quy-trinh-md-ban-giao.md); [báo cáo](2026-10-29-quy-trinh-md-ban-giao-bao-cao.md); [quy trình chung](../workflow/QUY_TRINH_3_VAI_TRO.md).

| Bước | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| S1 | Xong | CLAUDE.md mục 0/2/5/10, AGENTS.md đồng bộ vai/model, Sony, `.md` và checkpoint. |
| S2 | Xong | QUY_TRINH_3_VAI_TRO.md có bộ file, checkpoint, mẫu tiếp tục và quy tắc nhận phần chưa commit. |
| S3 | Xong | Các mẫu Codex/Claude không còn bắt buộc model theo nhỏ/lớn hoặc tự gộp kế hoạch/code. |
| S4 | Xong | Mẫu sơ bộ mới, mẫu nghiệm thu chính thức và chuyển hướng mẫu Opus cũ. |
| S5 | Xong ở commit 1a3ef47 | 13 file Markdown, 26 liên kết nội bộ hợp lệ, diff --check sạch. |
| S6 | Xong | Nguyên tắc ngang quyền trong CLAUDE.md/AGENTS.md/quy trình chung; quyền hồ sơ/upload/công cụ ghi cả hai agent. |
| S7 | Kiểm xong, bàn giao bằng commit bổ sung | 7 file Markdown, diff --check sạch, danh sách cấm không đổi; commit chứa checkpoint tra bằng lệnh phía trên. |
| S8 | Xong | Nhánh trung gian từ 9adeeba; cherry-pick 4 commit chỉ tài liệu, mapping trong báo cáo. |
| S9 | Xong | Merge --no-ff 93c2211; diff chỉ 14 Markdown, app không đổi; ghi hồ sơ trên nhánh nhiệm vụ và fast-forward sang tích hợp. |
| S10 | BLOCKED | Chưa có remote/URL đích; đã hỏi chủ dự án, chưa push. |

## Trạng thái dữ liệu và công cụ

- Lần đầu thay đổi 13 `.md` trong commit `1a3ef47`; lần bổ sung ngang quyền sửa 7 `.md` theo mục bổ sung của kế hoạch/báo cáo, cùng commit chứa checkpoint mới. Không có sửa mã ứng dụng hoặc phần việc cố ý để dở. Phiên nhận phải kiểm `git status --short`; nếu xuất hiện thay đổi ngoài commit, không tự coi đó là phần của phiên này.
- Không chạy Gradle, build/cài APK, ADB hay dùng Tiny. Không có tác vụ nền do phiên này tạo; không đọc/copy/hash bí mật.
- APK/config/QA mới: không có. `SONY_SCREEN_END = KHÔNG THAO TÁC`; `SHANLING_SCREEN_END = KHÔNG THAO TÁC` (không xác minh trạng thái màn hình, không gọi ADB).
- Ở lần kiểm trước, repo chính `D:\Bots\MeloVault-E91-Shareable` có công việc phiên khác: nhánh `dot/2026-10-29-hoc-hoi-ui-ux-telegram-music`, HEAD `7f3cca8`, untracked `.codex-remote-attachments/` và `docs/plans/2026-10-29-hoc-hoi-ui-ux-telegram-music.md`. Lần bổ sung không sửa repo chính hoặc đưa các file đó vào commit; muốn thao tác tại đó phải kiểm lại trạng thái thực.
- Không sửa kết luận E105 hay lịch sử nghiệm thu. Quy trình đã merge vào nhánh tích hợp cục bộ theo chỉ thị mới; chưa push. Checkout D: chưa cập nhật do vẫn ở nhánh UI/UX có công việc của phiên khác.

## Bước tiếp và prompt dùng khi chuyển Codex/Claude Code

Không có tác vụ nền. Phần merge cục bộ hoàn tất; bước còn lại là push sau khi có URL remote đích. Chủ dự án đã cấp quyền push trong lượt này, không cần xin lại quyền chỉ vì chuyển Codex/Claude Code; phải kiểm đích/lịch sử và không force. Trạng thái commit hồ sơ/nhánh phải đối chiếu bằng Git trước khi tiếp tục.

```text
Đọc CLAUDE.md mục 0 và hồ sơ TASK_ID 2026-10-29-quy-trinh-md-ban-giao
trong worktree C:\Users\ntdun\.codex\worktrees\workflow-md-handoff\MeloVault-E91-Shareable,
nhánh đích feature/e93-03d-y700-offline-cockpit; chỉ sửa hồ sơ trên nhánh dot của nhiệm vụ.
Đọc kế hoạch, báo cáo và file -ban-giao.md; xác minh Git/commit/checkpoint.
Quy trình đã merge cục bộ 93c2211, chỉ Markdown; code ứng dụng giữ nguyên từ 9adeeba.
Push đã được chủ dự án cho phép, đang thiếu URL remote. Khi có URL, kiểm lịch sử/nhánh
rồi push thông thường đúng phạm vi, xác minh SHA remote và cập nhật hồ sơ.
Không tự đẩy E105/nhánh khác; không sửa checkout D: có việc của phiên khác.
Nếu phiên trước dừng bất ngờ, kiểm S9/S10 và diff thực; phần lạ thì dừng.
```
