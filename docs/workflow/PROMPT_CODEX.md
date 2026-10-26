# PROMPT MẪU — CODEX LẬP KẾ HOẠCH (NHỎ) / THỰC THI / REWORK

Hiệu lực 26/10/2026 (CLAUDE.md mục 0). Mỗi vai là một **phiên Codex MỚI** mở tại `D:\Bots\MeloVault-E91-Shareable`. Codex đọc luật qua `AGENTS.md`. Chỉ Claude Opus sửa mẫu này khi chủ dự án yêu cầu.

| Vai | Model | Effort | Mục |
|---|---|---|---|
| Lập kế hoạch nhiệm vụ nhỏ | `gpt-6-astra` | medium | A |
| Thực thi nhiệm vụ nhỏ (GỘP 1 phiên) | `gpt-6.1-sol` | xhigh | B |
| Thực thi nhiệm vụ lớn (phiên 1, phiên 2) | `gpt-6-astra` | medium | B |
| REWORK | đúng model thực thi của nhiệm vụ | như trên | C |

Chạy từ dòng lệnh (khi không dùng Codex app):
`codex exec -m <model> -c model_reasoning_effort="<effort>" -C D:\Bots\MeloVault-E91-Shareable "<prompt>"`

---

## A. CODEX LẬP KẾ HOẠCH — NHIỆM VỤ NHỎ (gpt-6-astra, medium)

MELOVAULT — PHIÊN CODEX LẬP KẾ HOẠCH NHIỆM VỤ NHỎ, KHÔNG SỬA CODE

TASK_ID: <TASK_ID> — nhánh dot/<TASK_ID>
Nhiệm vụ chủ dự án CHỐT là NHỎ: <MỤC_TIÊU_CỤ_THỂ>

1. Đọc AGENTS.md, CLAUDE.md (mục 0 trước), mục đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, BACKLOG và code liên quan. Kiểm tra đầu phiên theo AGENTS.md; sai → DỪNG. Nhánh dot/<TASK_ID> chưa có và repo sạch → tạo từ HEAD nhánh tích hợp hiện hành.
2. Xác nhận nhiệm vụ đạt ĐỦ điều kiện "nhỏ" (CLAUDE.md mục 0). Nếu không đạt (cần > 2 file production, > 80 dòng, hoặc chạm DB/TDLib/Media3/queue/signing/build config…) → DỪNG, ghi lý do, đề nghị chủ dự án chuyển sang Claude Opus lập kế hoạch. Không tự thu hẹp yêu cầu để lọt điều kiện.
3. Chỉ tạo `docs/plans/<TASK_ID>.md` (≤ ~120 dòng): baseline SHA, mục tiêu/phạm vi, nguyên nhân, file + hàm sửa chính xác, bước nhỏ, test hồi quy, Debug/Release, **3–6 bước QA từ xa** (thiết bị, màn hình, nút, kỳ vọng, ảnh trước/sau, bước nào cần màn ON), tiêu chí PASS/FAIL đo được, rủi ro + rollback, trường hợp phải STOP. Chỗ chưa chắc ghi [CẦN XÁC NHẬN].
4. Không sửa code/gradle/manifest, không build, không cài APK. Chỉ commit file kế hoạch trên dot/<TASK_ID>.

Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro, rồi prompt mục B đã điền (PHIÊN = GỘP, model gpt-6.1-sol xhigh) và đúng câu:
"Giờ mở phiên Codex mới, chọn gpt-6.1-sol xhigh."

---

## B. CODEX THỰC THI

MELOVAULT — PHIÊN CODEX THỰC THI ĐÚNG KẾ HOẠCH

TASK_ID: <TASK_ID> — nhánh dot/<TASK_ID>
Kế hoạch đã CHỐT: docs/plans/<TASK_ID>.md (lập bởi <Codex gpt-6-astra | Claude Opus>)
Baseline: <BASE_SHA>, commit kế hoạch <PLAN_SHA>
Model thực thi: <gpt-6.1-sol xhigh (nhỏ) | gpt-6-astra medium (lớn)>
PHIÊN: <GỘP | 1 = code + test Ni (bước Sx–Sy) | 2 = build Tiny + QA + báo cáo (bước Sz)>
[Lưu ý riêng: ≤ 8 dòng]

Làm đúng docs/workflow/PROMPT_SONNET.md các bước 1–6 cùng hai mục bắt buộc về màn hình và bàn giao APK ở cuối file đó, với "Sonnet" đọc là "Codex", và thêm:
- Đầu báo cáo `docs/plans/<TASK_ID>-bao-cao.md` ghi dòng: `Thực thi: Codex <model> <effort>, phiên <GỘP|1|2>`.
- Mọi lệnh ADB/Git/Desktop Commander tuân danh sách cấm trong AGENTS.md. Tiny chỉ qua Desktop Commander trong clone cô lập, nhận code bằng Git bundle có SHA-256.
- Plan sai/thiếu, cần đổi phạm vi hoặc kiến trúc → DỪNG và ghi vào báo cáo; không tự sửa plan, không đoán.
- Không sửa CLAUDE.md, AGENTS.md, docs/workflow/*, file nghiệm thu/apk-test/hinh-nghiem-thu. Không merge, không upload/công bố APK.

Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối:
- Phiên 1: "Đã dừng. Mở phiên Codex mới cho phiên 2 (build + QA), cùng model."
- GỘP/phiên 2: "Đã dừng, chờ Claude Opus nghiệm thu." và dòng `Nghiệm thu <TASK_ID> theo docs/workflow/PROMPT_OPUS_NGHIEM_THU.md`

---

## C. CODEX REWORK

MELOVAULT — PHIÊN CODEX REWORK

TASK_ID: <TASK_ID> — nhánh dot/<TASK_ID>, HEAD hiện tại <HEAD_SHA>
Model: <đúng model thực thi ban đầu>
Lỗi cần sửa: docs/plans/<TASK_ID>-nghiem-thu.md mục <số>

Kiểm tra đầu phiên theo AGENTS.md. Chỉ sửa đúng các lỗi nêu trong nghiệm thu, không mở rộng phạm vi, không tạo nhánh/plan mới. Chạy lại test/build và các bước QA bị ảnh hưởng theo mục B, cập nhật báo cáo cũ với mục "Vòng REWORK <n>" (giữ dấu vết vòng trước). Lời kết như mục B (GỘP/phiên 2).
