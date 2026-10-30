# Bàn giao — bản công khai đăng nhập chỉ bằng số điện thoại

TASK_ID: **2026-10-30-dang-nhap-chi-so-dien-thoai**.
Worktree `D:\Bots\_worktrees\MeloVault-dang-nhap-chi-so-dien-thoai`; nhánh `dot/2026-10-30-dang-nhap-chi-so-dien-thoai`.

## Checkpoint lập kế hoạch — 30/10/2026, Claude Code

- Baseline `baaead77ac60cbd6c752bd0dbe9c575ed30954fc` (nhánh tích hợp sạch lúc 21:51). Kế hoạch [`2026-10-30-dang-nhap-chi-so-dien-thoai.md`](2026-10-30-dang-nhap-chi-so-dien-thoai.md), commit `f1e97f2d9eccf659cf784a40b1dd68e9d8a7cd0f`. HEAD dự kiến khi bắt đầu thực thi: commit chứa file bàn giao này (ngay sau `f1e97f2`).
- Chỉ đọc mã và tài liệu E104. Không code, không build, không dùng ADB/Sony/Tiny/Drive. Không có tiến trình nào còn chạy.
- Chặng: **A** = S0–S4 trên Ni (code + test); **B** = S5–S7 (cổng chủ dự án → Tiny build → QA → hồ sơ).
- **Chờ chủ dự án** (mục 8 kế hoạch):
  1. D6 — mã người dùng đã lưu thắng mã open nhúng. Mặc định: có.
  2. Đăng ký app trên my.telegram.org và thêm `telegram.open.api.id/hash` vào `C:\Bots\MeloVault-E99-IsolatedQA\local.properties` trên Tiny. Chặng A làm trước được; chặng B BLOCKED tới khi xong.
  3. Có được cài mới gói open lên Sony không.
  4. Chữ câu D7 / link D9.
- Không merge/push. Trạng thái: **KẾ HOẠCH — chờ chủ dự án giao thực thi.**

## Prompt phiên thực thi — chặng A (dán nguyên văn vào phiên mới)

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-dang-nhap-chi-so-dien-thoai (repo chuẩn D:\Bots\MeloVault-E91-Shareable; KHÔNG làm trong thư mục chính, nó đang ở nhánh khác)
TASK_ID: 2026-10-30-dang-nhap-chi-so-dien-thoai
Nhánh: dot/2026-10-30-dang-nhap-chi-so-dien-thoai
Kế hoạch đã chốt: docs/plans/2026-10-30-dang-nhap-chi-so-dien-thoai.md
Bàn giao: docs/plans/2026-10-30-dang-nhap-chi-so-dien-thoai-ban-giao.md; HEAD dự kiến: commit bàn giao ngay sau f1e97f2
Baseline: baaead77ac60cbd6c752bd0dbe9c575ed30954fc, commit kế hoạch f1e97f2d9eccf659cf784a40b1dd68e9d8a7cd0f
PHIÊN: 1 = chặng A, code + test Ni, bước S0–S4
Lưu ý riêng:
- Theo đúng quyết định D1–D10 mục 3. D6 áp dụng mặc định trừ khi chủ dự án đã trả lời khác trong bàn giao.
- Không đọc/in/copy local.properties; Ni không có mã nên APK Ni KHÔNG cài.
- S2: đối chiếu tên authenticationCodeType* với TDLib đóng gói trước khi viết; không đoán.
- S3: không được gửi setTdlibParameters lần hai; lưu mã sau parameters → yêu cầu khởi động lại (D8).
- Không đụng QR login, package/signer/versionCode, schema, mã bản riêng.
- Hết S4: dừng, cập nhật bàn giao, đưa prompt chặng B (S5–S7). Không tự làm S5 (cần chủ dự án).

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, file bàn giao và kế hoạch trên. Kiểm git status, branch, HEAD, baseline/plan commit, diff --check, agent đang chạy. Cây bẩn không rõ nguồn hoặc HEAD lạ → STOP, không reset/clean/stash/đè.
2. Làm S1 → S2 → S3 đúng file/hàm trong kế hoạch, commit theo mốc. Viết test mục S2 (≥ 12 ca mới). Chạy đúng các lệnh S4, log ra file dưới docs/plans/artifacts/2026-10-30-dang-nhap-chi-so-dien-thoai/S4-<ts>/, chỉ ghi tổng kết + cộng XML (passed/skipped/failed/errors), git diff --check. Sau mỗi mốc cập nhật docs/plans/2026-10-30-dang-nhap-chi-so-dien-thoai-bao-cao.md (mở đầu "Thực thi: Claude Code <model>, phiên chặng A") và file bàn giao: bước xong/dở, file/commit, bằng chứng, lỗi/giới hạn, việc tiếp.
3. Không ADB/thiết bị trong chặng A. Màn hình Shanling/Sony: không thao tác; ghi SHANLING_SCREEN_END/SONY_SCREEN_END = KHÔNG THAO TÁC.
4. Commit code, báo cáo và bàn giao trên dot/2026-10-30-dang-nhap-chi-so-dien-thoai. Không merge/push/public, không ghi PASS, không chọn việc khác.

Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng, bàn giao phiên thực thi tiếp theo." kèm prompt chặng B đã điền HEAD thật.
```
