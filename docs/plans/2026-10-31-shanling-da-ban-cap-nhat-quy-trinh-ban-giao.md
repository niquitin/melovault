# Bàn giao — cập nhật quy trình: Shanling M3 Ultra đã bán

TASK_ID: `2026-10-31-shanling-da-ban-cap-nhat-quy-trinh`. Nhánh `dot/2026-10-31-shanling-da-ban-cap-nhat-quy-trinh`.
Worktree `D:\Bots\_worktrees\MeloVault-shanling-da-ban-cap-nhat-quy-trinh`. Baseline `1d9443c` (HEAD của `D:\Bots\MeloVault-E91-Shareable` lúc bắt đầu, nhánh `dot/2026-10-29-hoc-hoi-ui-ux-telegram-music`; thư mục chính đang có việc khác chưa commit của phiên khác nên làm ở worktree riêng theo đúng CLAUDE.md mục 1).

## Mục tiêu

Sửa CLAUDE.md/HANDOFF.md để không còn hướng dẫn agent dùng "Shanling M3 Ultra" làm thiết bị QA trên Ni — máy đã được chủ dự án bán (báo qua chat ngày 31/10/2026, trong lúc nghiệm thu nhiệm vụ `2026-10-30-dang-nhap-chi-so-dien-thoai`). Chủ dự án xác nhận "Tiến hành" khi được hỏi có muốn mở nhiệm vụ tài liệu riêng này không.

## Đã làm

- `CLAUDE.md`: mục 0 (dòng nhắc "không dùng Sony thay chứng cứ Shanling"), mục 3 (toàn bộ đoạn mô tả Shanling là thiết bị chính — thay bằng thông báo đã bán + Sony là thiết bị QA thật duy nhất Ni/Tiny, giữ đoạn lịch sử có đánh dấu rõ), mục 6 (dòng "Thiết bị ADB đã xác minh 19/10"), mục 7 (tiêu đề + nội dung chính sách màn hình — bỏ M3 Ultra, giữ `SONY_SCREEN_END`, ghi chú `SHANLING_SCREEN_END` không còn áp dụng).
- `HANDOFF.md`: thêm mục cập nhật đầu tiên (trên cả mục 29/10) nêu rõ sự kiện, trỏ tới CLAUDE.md mục 3 và hồ sơ này.
- **Chủ dự án yêu cầu "làm luôn cho gọn gàng"** → dọn tiếp cùng nhánh/phiên: `AGENTS.md` (dòng 33), `docs/workflow/QUY_TRINH_3_VAI_TRO.md` (mục 4), `docs/workflow/SCREEN_LIFECYCLE_QA.md` (mở đầu + danh sách thiết bị + `SHANLING_SCREEN_END`), `docs/workflow/PROMPT_OPUS.md` (bước 4), `docs/workflow/PROMPT_SONNET.md` (bước 3 + đoạn màn hình), `docs/workflow/PROMPT_NGHIEM_THU_SO_BO.md` (bước 3) — cùng kiểu sửa: bỏ hướng dẫn "dùng/so với Shanling" như đang hoạt động, nêu rõ Sony là thiết bị QA thật duy nhất, giữ các chỗ còn nhắc Shanling dưới dạng lịch sử có đánh dấu.
- Đối chiếu bằng `grep -rn "Shanling\|30313860180939" --include="*.md" .` trên toàn repo (trừ `docs/history/`, `docs/plans/`) sau khi sửa — chỉ còn `docs/ui-spec.md` (cố ý không đụng, xem dưới) và các mục lịch sử trong `HANDOFF.md` (cũng cố ý không đụng).

## Cố ý không đụng (khác với "chưa làm")

- `docs/ui-spec.md`: Shanling ở đây là **thông số màn hình/layout** cho lớp kích thước "máy nhỏ" (768×1280, chế độ "Gọn") — một đặc tả thiết kế UI, không phải chỉ định thiết bị QA đang hoạt động. Vẫn đúng/còn ý nghĩa dù máy vật lý cụ thể đã bán (ứng dụng vẫn có chế độ "Gọn" cho lớp màn hình đó). Sửa file này là việc khác (cập nhật UI spec), ngoài phạm vi nhiệm vụ dọn quy trình QA.
- Các mục lịch sử trong `HANDOFF.md` (vd. "Shanling đang cài vc117", các dòng "Giới hạn còn mở... Shanling") — đây là **kết luận/trạng thái lịch sử tại thời điểm nghiệm thu các task cũ**, CLAUDE.md cấm sửa lại kết luận lịch sử. Không đụng.

## Rủi ro / điểm dừng

- Không đổi mã nguồn, không đổi bất kỳ quyết định nghiệm thu lịch sử nào.
- Chưa commit, chưa merge, chưa push.
- Không ảnh hưởng tới nhánh `dot/2026-10-30-dang-nhap-chi-so-dien-thoai` (nhiệm vụ đăng nhập số điện thoại) — hai nhánh độc lập.

**Trạng thái: hồ sơ xong, chờ commit + chủ dự án xác nhận trước khi merge/push** (theo đúng CLAUDE.md mục 9: "Merge chỉ sau ĐẠT và chỉ thị merge riêng; push/public cần chỉ thị riêng").
