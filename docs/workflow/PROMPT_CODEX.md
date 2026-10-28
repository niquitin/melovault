# PROMPT MẪU — CODEX LẬP KẾ HOẠCH / THỰC THI / SƠ BỘ / TIẾP TỤC

Hiệu lực 29/10/2026, theo CLAUDE.md mục 0 và [quy trình chung](QUY_TRINH_3_VAI_TRO.md). Chủ dự án chọn model/effort và mở phiên mới cho từng vai. Phiên thực thi dùng model tiết kiệm token/quota hơn phù hợp kế hoạch; không cố định model theo nhỏ/lớn. Codex và Claude Code dùng chung bộ hồ sơ `.md`.

## A. Lập kế hoạch

```text
MELOVAULT — LẬP KẾ HOẠCH CHI TIẾT, KHÔNG CODE
TASK_ID: <...>; nhánh dot/<TASK_ID>; repo/worktree: <đường dẫn thực>.
Mục tiêu chủ dự án giao: <...>; baseline dự kiến: <SHA>.
Model/effort: <chủ dự án chọn; không rõ thì ghi không xác định>.
```

1. Đọc AGENTS.md, CLAUDE.md mục 0, chỉ mục đầu HANDOFF.md, BACKLOG, quy trình chung, bàn giao nếu có và mã liên quan. Kiểm Git/agent theo luật đầu phiên; không nhận phần lạ. Nếu cần tạo nhánh nhiệm vụ, chỉ từ baseline đã xác minh, không sửa nhánh tích hợp.
2. Phân tích nguyên nhân/phương án/rủi ro; có thể chủ động khảo sát Sony, tái hiện và chụp bằng chứng an toàn theo luật thiết bị/màn hình. Ghi rõ bản ứng dụng được khảo sát. Không tự code/build mới.
3. Viết `docs/plans/<TASK_ID>.md` đủ chi tiết cho model thực thi: mục tiêu/phạm vi, baseline, file/hàm, thay đổi và lý do, bước nhỏ có phụ thuộc/điều kiện xong, test hồi quy, Debug/Release, build và bàn giao APK, QA thiết bị, tiêu chí đo được, rollback an toàn và trường hợp dừng. Chỗ chưa chắc ghi `[CẦN XÁC NHẬN]`.
4. Ghi 3–6 thao tác kiểm thử: thiết bị/màn hình/nút/kỳ vọng, ảnh trước/sau, bước cần màn ON; tách âm thanh thật và các phần chưa thể chứng minh. Chia chặng thực thi khi cần; không tự phân công model theo loại việc.
5. Tạo/cập nhật `*-ban-giao.md` theo quy trình chung sau mỗi mốc, ghi quyết định, tiến độ, bằng chứng và prompt tiếp tục. Không chỉ lưu trong chat.
6. Commit tài liệu trên nhánh nhiệm vụ, dẫn kế hoạch/bàn giao và prompt B đã điền (hoặc PROMPT_SONNET.md nếu chủ dự án chọn Claude Code). Không tự bắt đầu code.

Lời kết: Đã làm / Chưa kiểm chứng / Rủi ro. **“Đã dừng. Mở phiên mới, chọn model thực thi tiết kiệm hơn theo lựa chọn của chủ dự án.”**

## B. Thực thi ở phiên mới

```text
MELOVAULT — THỰC THI ĐÚNG KẾ HOẠCH
TASK_ID: <...>; nhánh dot/<TASK_ID>; worktree: <đường dẫn thực>.
Kế hoạch đã chốt: docs/plans/<TASK_ID>.md, commit <PLAN_SHA>.
Bàn giao: docs/plans/<TASK_ID>-ban-giao.md.
Baseline: <BASE_SHA>; HEAD dự kiến: <SHA>; chặng/bước: <...>.
Model/effort: <chủ dự án chọn cho phiên thực thi>.
Lưu ý riêng: <...>.
```

Làm các bước 1–6, quy định màn hình và bàn giao APK trong [mẫu thực thi](PROMPT_SONNET.md), áp dụng cho Codex. Dòng đầu báo cáo ghi `Thực thi: Codex <model> <effort nếu biết>, phiên <...>`; không tự đoán model/effort. Cập nhật báo cáo chi tiết và bàn giao ngay sau từng mốc; ghi đúng source/test/APK.

Thiếu/sai kế hoạch, cần đổi phạm vi/kiến trúc hoặc gặp gate an toàn không đạt: dừng phần bị chặn, ghi rõ vào `.md`, không tự đổi plan. Giữ nguyên mọi luật Git/ADB/bí mật/Tiny và quyền chủ dự án nghiệm thu cuối.

Cuối chặng: commit, bàn giao bước chưa xong. Cuối thực thi: dẫn APK/báo cáo/bàn giao, đưa prompt [sơ bộ](PROMPT_NGHIEM_THU_SO_BO.md) đã điền và ghi **“Đã dừng. Mở phiên mới để nghiệm thu sơ bộ nhanh.”** Không tự chạy vai sơ bộ trong phiên code.

## C. REWORK

```text
MELOVAULT — REWORK
TASK_ID: <...>; nhánh dot/<TASK_ID>; worktree: <...>; HEAD: <SHA>.
Model/effort: <chủ dự án chọn>.
Lỗi chủ dự án giao sửa: <...>.
Nguồn yêu cầu và hồ sơ liên quan: <file/mục>.
```

Đọc bàn giao, báo cáo, sơ bộ và quyết định chủ dự án; kiểm Git. Chỉ sửa lỗi đã giao, cùng TASK_ID/nhánh, không tạo plan trùng hoặc mở rộng phạm vi. Cập nhật mục “Vòng REWORK <n>” giữ lịch sử; test/build/QA phần ảnh hưởng theo B, bàn giao candidate mới và phiên sơ bộ mới.

## D. Sơ bộ ở phiên mới

Dùng [PROMPT_NGHIEM_THU_SO_BO.md](PROMPT_NGHIEM_THU_SO_BO.md). Chỉ kiểm nhanh và ghi biên bản; không code, không ghi PASS chính thức. Chủ dự án nghiệm thu chính thức, chi tiết.

## E. Tiếp tục sau hết quota hoặc đổi công cụ

Dùng mẫu tiếp tục tại [quy trình chung, mục 3](QUY_TRINH_3_VAI_TRO.md#3-checkpoint-để-đổi-codex--claude-code). Đọc `*-ban-giao.md`, kiểm trạng thái thực và tiếp tục đúng vai/bước được giao. Chủ dự án có thể giao Claude Code tiếp trong cùng worktree; không yêu cầu dùng lại Codex để khôi phục ngữ cảnh. Agent cũ phải dừng; phần chưa commit phải khớp bàn giao trước khi tiếp nhận.
