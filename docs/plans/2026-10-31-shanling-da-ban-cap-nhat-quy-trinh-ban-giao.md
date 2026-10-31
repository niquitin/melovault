# Bàn giao — cập nhật quy trình: Shanling M3 Ultra đã bán

TASK_ID: `2026-10-31-shanling-da-ban-cap-nhat-quy-trinh`. Nhánh `dot/2026-10-31-shanling-da-ban-cap-nhat-quy-trinh`.
Worktree `D:\Bots\_worktrees\MeloVault-shanling-da-ban-cap-nhat-quy-trinh`. Baseline `1d9443c` (HEAD của `D:\Bots\MeloVault-E91-Shareable` lúc bắt đầu, nhánh `dot/2026-10-29-hoc-hoi-ui-ux-telegram-music`; thư mục chính đang có việc khác chưa commit của phiên khác nên làm ở worktree riêng theo đúng CLAUDE.md mục 1).

## Mục tiêu

Sửa CLAUDE.md/HANDOFF.md để không còn hướng dẫn agent dùng "Shanling M3 Ultra" làm thiết bị QA trên Ni — máy đã được chủ dự án bán (báo qua chat ngày 31/10/2026, trong lúc nghiệm thu nhiệm vụ `2026-10-30-dang-nhap-chi-so-dien-thoai`). Chủ dự án xác nhận "Tiến hành" khi được hỏi có muốn mở nhiệm vụ tài liệu riêng này không.

## Đã làm

- `CLAUDE.md`: mục 0 (dòng nhắc "không dùng Sony thay chứng cứ Shanling"), mục 3 (toàn bộ đoạn mô tả Shanling là thiết bị chính — thay bằng thông báo đã bán + Sony là thiết bị QA thật duy nhất Ni/Tiny, giữ đoạn lịch sử có đánh dấu rõ), mục 6 (dòng "Thiết bị ADB đã xác minh 19/10"), mục 7 (tiêu đề + nội dung chính sách màn hình — bỏ M3 Ultra, giữ `SONY_SCREEN_END`, ghi chú `SHANLING_SCREEN_END` không còn áp dụng).
- `HANDOFF.md`: thêm mục cập nhật đầu tiên (trên cả mục 29/10) nêu rõ sự kiện, trỏ tới CLAUDE.md mục 3 và hồ sơ này.
- Đối chiếu bằng `grep -n "Shanling\|30313860180939" CLAUDE.md` sau khi sửa — chỉ còn các dòng tự nhận là lịch sử/đã retired, không còn dòng nào hướng dẫn agent thao tác với Shanling như đang hoạt động.

## Chưa làm (ngoài phạm vi được giao — chỉ "CLAUDE.md/HANDOFF.md")

Các file sau vẫn còn nhắc Shanling/serial `30313860180939` như đang hoạt động, **chưa sửa trong nhiệm vụ này**:
- `docs/workflow/SCREEN_LIFECYCLE_QA.md`
- `docs/workflow/QUY_TRINH_3_VAI_TRO.md`
- `docs/workflow/PROMPT_OPUS.md`, `PROMPT_SONNET.md`, `PROMPT_NGHIEM_THU_SO_BO.md`
- `AGENTS.md`

Nếu chủ dự án muốn dọn tiếp, cần giao riêng (có thể cùng nhánh này, tiếp tục phiên, hoặc nhiệm vụ mới).

## Rủi ro / điểm dừng

- Không đổi mã nguồn, không đổi bất kỳ quyết định nghiệm thu lịch sử nào.
- Chưa commit, chưa merge, chưa push.
- Không ảnh hưởng tới nhánh `dot/2026-10-30-dang-nhap-chi-so-dien-thoai` (nhiệm vụ đăng nhập số điện thoại) — hai nhánh độc lập.

**Trạng thái: hồ sơ xong, chờ commit + chủ dự án xác nhận trước khi merge/push** (theo đúng CLAUDE.md mục 9: "Merge chỉ sau ĐẠT và chỉ thị merge riêng; push/public cần chỉ thị riêng").
