# Quy trình kế hoạch, thực thi, nghiệm thu sơ bộ và bàn giao Markdown

- Ngày: 29/10/2026. TASK_ID: `2026-10-29-quy-trinh-md-ban-giao`.
- Yêu cầu trực tiếp: Codex hoặc Claude lập kế hoạch chi tiết bằng `.md`; chủ dự án mở phiên mới với model tiết kiệm hơn để code/build APK; phiên mới kiểm tra sơ bộ nhanh; chủ dự án nghiệm thu chính thức, chi tiết. Cho phép chủ động dùng Sony trong cả ba vai agent. Lưu tiến độ để đổi Codex ↔ Claude Code mà vẫn tiếp tục cùng công việc.
- Baseline: `7f3cca874e525956a10f94f8caf577c6cc80f815`.
- Nhánh: `dot/2026-10-29-quy-trinh-md-ban-giao`.
- Worktree: `C:\Users\ntdun\.codex\worktrees\workflow-md-handoff\MeloVault-E91-Shareable`.
- Repo chính có tài liệu UI/UX chưa commit của phiên khác. Áp dụng ngoại lệ tài liệu ở CLAUDE.md mục 1; không sửa cây làm việc đó.

## Phạm vi và cách sửa

Chỉ sửa tài liệu quy trình theo yêu cầu; không sửa ứng dụng, build/cài APK hay thao tác điện thoại trong phiên này. Các lệnh cấm Git/ADB, bí mật, chữ ký, dữ liệu, vòng đời màn hình và quyền nghiệm thu cuối giữ nguyên.

| Bước | File | Nội dung và điều kiện hoàn tất |
|---|---|---|
| S1 | `CLAUDE.md`, `AGENTS.md` | Ghi quyết định mới ở luật ưu tiên; bỏ khóa cứng người/model theo nhỏ/lớn; tách ba vai agent qua phiên mới; chủ dự án quyết định model và nghiệm thu cuối. |
| S2 | `docs/workflow/QUY_TRINH_3_VAI_TRO.md` | Quy định bộ hồ sơ `.md`, checkpoint tiến độ, cách nhận bàn giao khi đổi công cụ/hết quota; không chạy hai agent cùng lúc. |
| S3 | `PROMPT_CODEX.md`, `PROMPT_OPUS.md`, `PROMPT_SONNET.md` trong `docs/workflow/` | Mẫu lập kế hoạch/thực thi/REWORK cùng nguồn quy trình; không còn làn nhanh tự gộp lập kế hoạch và code. |
| S4 | `PROMPT_NGHIEM_THU_SO_BO.md`, `PROMPT_CHU_DU_AN_NGHIEM_THU.md`, `PROMPT_OPUS_NGHIEM_THU.md` trong `docs/workflow/` | Mẫu kiểm tra nhanh theo đúng source/APK ở phiên mới; kết quả sơ bộ tách khỏi PASS chính thức và không sửa code trong vai kiểm tra. |
| S5 | Báo cáo, bàn giao cùng TASK_ID và chỉ mục đầu `HANDOFF.md` | Ghi thay đổi, kiểm tra, giới hạn, nhánh/worktree và bước tiếp; commit tài liệu rồi dừng. |

## Kiểm tra

1. Đối chiếu yêu cầu với luật gốc và mọi mẫu đang dùng: người lập kế hoạch, model thực thi do chủ dự án chọn, phiên kiểm sơ bộ riêng, nghiệm thu chính thức của chủ dự án, Sony cả ba vai, Markdown chi tiết và chuyển tiếp Codex/Claude.
2. Kiểm liên kết Markdown nội bộ, tìm quy định model/vai cũ còn mâu thuẫn, đọc lại toàn bộ diff.
3. `git diff --check`; xác nhận thay đổi chỉ là `.md` trong phạm vi và không có bí mật/artifact.
4. Kiểm repo chính vẫn đúng HEAD/nhánh và không bị phiên này sửa; không merge/push.

## Rủi ro, điểm dừng và khôi phục

- Tài liệu chỉ có trên nhánh/worktree này cho tới khi được đưa vào nhánh khác bằng chỉ thị riêng; phiên tiếp cần mở đúng worktree hoặc đối chiếu commit này.
- Mất quota đột ngột có thể làm thiếu checkpoint cuối: phiên nhận phải kiểm Git/file/log thực, không đoán trạng thái hay chạy lại thao tác thiết bị.
- Sony chỉ cung cấp bằng chứng Sony; không thay cho Shanling. Quyền dùng Sony không bỏ gate cài APK hoặc bảo vệ dữ liệu.
- Nếu thay đổi ngoài phạm vi xuất hiện trong worktree, dừng. Không khôi phục bằng reset/clean/stash/ghi đè; nếu cần sửa quy tắc, dùng một commit tài liệu tiếp theo được chủ dự án giao.

Đây là phiên chỉ sửa quy trình theo yêu cầu trực tiếp, không phải phiên lập kế hoạch hay thực thi tính năng và không cần nghiệm thu APK.

## Bổ sung theo yêu cầu chủ dự án — Codex và Claude Code ngang quyền

- Baseline bổ sung: `1a3ef477ac33c78487fea51aedf1b419da063f0e`, cùng TASK_ID/nhánh/worktree; đầu phiên cây sạch, commit gần nhất do chính phiên trước tạo.
- Yêu cầu: Codex và Claude Code có vai trò, quyền và trách nhiệm ngang nhau; đều sử dụng repo và công cụ phát triển bình thường trong phạm vi đã giao.
- S6: thêm nguyên tắc ở CLAUDE.md mục 0, AGENTS.md và quy trình chung; nêu quyền đọc/sửa mã và tài liệu, dùng repo/worktree, Git, test/build/QA/bàn giao theo vai. Đồng bộ các dòng quyền đang chỉ ghi tên Codex thành cả hai agent; không cần agent còn lại phê duyệt hoặc xin lại quyền chỉ vì đổi công cụ.
- S7: cập nhật chỉ mục HANDOFF.md, báo cáo và checkpoint; kiểm diff chỉ gồm 7 file Markdown, danh sách cấm không đổi, chạy `git diff --check`, commit bổ sung rồi dừng.
- Quyền ngang nhau không đổi vai được giao, giới hạn bảo vệ dữ liệu, một agent sửa code mỗi lúc, quyền nghiệm thu cuối của chủ dự án hoặc điều kiện merge/push/public. Không tự tích hợp nhánh quy trình vào repo chính, không dùng thiết bị/build APK trong lần bổ sung này.

## Chỉ thị tích hợp và push — 29/10/2026

- Chủ dự án yêu cầu tiếp: “Merge/push vào repo chính đi”, sau bàn giao `1a3ef47` và `c1daa9c`. Phạm vi là quy trình đã sửa; không phải phê duyệt/merge E105 hoặc phát hành APK.
- Nhánh tích hợp hiện hành: `feature/e93-03d-y700-offline-cockpit`, HEAD `9adeeba6c367a05194c36d8d6df6cbfb8f16efe9`, chưa checkout ở worktree nào. Nhánh quy trình `c1daa9c` có tổ tiên chứa mã E105 ngoài phạm vi, nên không merge toàn bộ nhánh đó vào tích hợp.
- S8: tạo nhánh trung gian `dot/2026-10-29-quy-trinh-md-ban-giao-merge` từ `9adeeba` trong worktree đang sạch của nhiệm vụ. Cherry-pick riêng các commit tài liệu `7f3cca8` (quyền nghiệm thu của chủ dự án, điều kiện nền), `1a3ef47`, `c1daa9c` và commit chứa mục chỉ thị này. Không lấy commit code/báo cáo E105.
- S9: kiểm diff so với `9adeeba` chỉ gồm Markdown quy trình/hồ sơ cùng TASK_ID; cây `app/` phải giống hệt trước. Giải quyết xung đột tài liệu nếu có, giữ lịch sử và luật đã được giao. Cập nhật báo cáo/checkpoint/chỉ mục rồi commit trên nhánh trung gian. Sau khi kiểm nhánh tích hợp vẫn ở đúng baseline, merge `--no-ff` theo chỉ thị chủ dự án; không sửa file trực tiếp trên nhánh tích hợp.
- S10: kiểm merge/tree/diff, rồi push bình thường đến remote/nhánh đã xác minh; không force hoặc đẩy nhánh khác. `git remote` và refs/remotes hiện rỗng: đã hỏi chủ dự án URL đích, chỉ thực hiện push khi có đích xác định. Nếu remote có lịch sử lệch hoặc có code ngoài phạm vi cần đưa vào thì dừng phần push và báo.
- Repo chính `D:\Bots\MeloVault-E91-Shareable` vẫn có 2 mục untracked của phiên khác trên nhánh UI/UX tại `7f3cca8`; không sửa/chuyển nhánh/commit chúng. Merge thực hiện trong worktree sạch, cập nhật nhánh tích hợp dùng chung của repo. Không chạy build/ADB vì chỉ tích hợp tài liệu.
- Ghi kết quả thực: sau merge, cập nhật báo cáo/checkpoint/chỉ mục trên nhánh nhiệm vụ đã fast-forward tới merge, commit hồ sơ rồi fast-forward nhánh tích hợp. Không chỉnh file trực tiếp trên nhánh tích hợp; push chỉ sau khi có remote đích.
