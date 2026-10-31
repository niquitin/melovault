# Bàn giao — giao diện phù hợp mọi loại thiết bị

TASK_ID: **2026-10-31-giao-dien-da-thiet-bi**. Worktree `D:\Bots\_worktrees\MeloVault-giao-dien-da-thiet-bi`; nhánh `dot/2026-10-31-giao-dien-da-thiet-bi`; baseline `94ab6b0`.

## Checkpoint lập kế hoạch — 31/10/2026, Claude Code

- Kế hoạch: [`2026-10-31-giao-dien-da-thiet-bi.md`](2026-10-31-giao-dien-da-thiet-bi.md). HEAD dự kiến khi thực thi: commit cuối của nhánh (chứa file này).
- Đã đọc mã:
  - `MeloScreenPolicy` (luật chọn bố cục, mời Car Mode, giả lập);
  - insets ở `MeloStudioDeck`;
  - manifest `configChanges`;
  - `MeloUiPreviewActivity` (chỉ xem trước thành phần, không đủ cho khảo sát).

  Bảng kỳ vọng mục 4 đã tính tay từ luật hiện tại; G1 sẽ kiểm bằng test.
- Không code, không build, không dùng ADB. Không có tiến trình nào còn chạy.
- Chặng: **A** = G0–G2 (test khóa + khảo sát Sony, không sửa UI); **B** = G3 (sửa + gate Ni); **C** = G4 (Tiny build + chụp lại + hồ sơ).
- **Chờ chủ dự án:** câu 1 mục 9 (cho phép đổi tạm `wm size` / `wm density` / `font_scale` trên Sony). Không trả lời thì G2 làm theo GD3 (chỉ cỡ gốc). Câu 2–4 có mặc định.
- Không merge/push. Trạng thái: **KẾ HOẠCH — chờ chủ dự án giao thực thi.**

## Prompt phiên thực thi — chặng A (dán nguyên văn)

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-giao-dien-da-thiet-bi (repo chuẩn D:\Bots\MeloVault-E91-Shareable; KHÔNG làm trong thư mục chính)
TASK_ID: 2026-10-31-giao-dien-da-thiet-bi
Nhánh: dot/2026-10-31-giao-dien-da-thiet-bi
Kế hoạch đã chốt: docs/plans/2026-10-31-giao-dien-da-thiet-bi.md
Bàn giao: docs/plans/2026-10-31-giao-dien-da-thiet-bi-ban-giao.md; HEAD dự kiến: commit cuối của nhánh (chứa file bàn giao)
Baseline: 94ab6b0 (app/ giống a2f0a88)
PHIÊN: 1 = chặng A, bước G0–G2 (test khóa + khảo sát Sony; KHÔNG sửa mã giao diện)
Lưu ý riêng:
- G1 chỉ thêm MeloLayoutMatrixTest theo bảng mục 4; không sửa MeloScreenPolicy. Dòng nào lệch bảng thì sửa bảng (ghi lý do), không sửa mã.
- G2 dùng Sony QV725WEQ54 qua Tiny (Desktop Commander). wm size/wm density/font_scale CHỈ khi bàn giao ghi chủ dự án đã cho phép (câu 1 mục 9); chưa có thì làm GD3.
- Ghi giá trị gốc trước; mỗi cấu hình khôi phục ngay; finally: wm size reset, wm density reset, font_scale/xoay về gốc, keyevent 223, kiểm Display State=OFF. Ghi SONY_SCREEN_END.
- Không phát bài mới/đổi hàng chờ, không đăng xuất, không máy bay, không uninstall/clear. Đổi cỡ lúc app đang mở; app không tự dựng lại thì ghi lỗi UI-xx.
- Kết quả: docs/plans/2026-10-31-giao-dien-da-thiet-bi-khao-sat.md (bảng UI-xx: cấu hình, màn, mô tả, nhóm GD4 a–f hoặc Đề xuất, mức, ảnh) + ảnh tổng hợp mỗi nhóm cấu hình. Ảnh private, không commit.

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, docs/workflow/SCREEN_LIFECYCLE_QA.md, file bàn giao và kế hoạch trên. Kiểm git status, branch, HEAD, baseline, diff --check, agent/thiết bị đang dùng. Cây bẩn không rõ nguồn hoặc HEAD lạ → STOP, không reset/clean/stash/đè.
2. G0: đối chiếu APK trên Sony với SHA-256 6e3d6b6e98de9974d399eea850ec691df1ab2324172ec3990fba483e6869a029 (Debug thường a2f0a88). G1: test mục tiêu Debug + Release, log dưới docs/plans/artifacts/2026-10-31-giao-dien-da-thiet-bi/G1-<ts>/, chỉ ghi tổng kết + cộng XML; commit. G2: khảo sát theo mục 4, ghi file khảo sát. Sau mỗi mốc cập nhật docs/plans/2026-10-31-giao-dien-da-thiet-bi-bao-cao.md (mở đầu "Thực thi: <Claude Code/Codex> <model>, phiên chặng A") và file bàn giao.
3. Commit test, báo cáo, khảo sát, bàn giao trên dot/2026-10-31-giao-dien-da-thiet-bi. Không merge/push/public, không ghi PASS, không chọn việc khác.

Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Gửi ảnh tổng hợp khảo sát cho chủ dự án. Dòng cuối: "Đã dừng, bàn giao phiên thực thi tiếp theo." kèm prompt chặng B đã điền HEAD thật.
```
