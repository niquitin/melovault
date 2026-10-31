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
- Không merge/push. Trạng thái lúc lập kế hoạch: **KẾ HOẠCH — chờ chủ dự án giao thực thi.**

## Checkpoint chặng A — 31/10/2026 20:35, Claude Code Sonnet 5.5

- **Chặng A (G0–G2) xong.** Báo cáo: [`-bao-cao.md`](2026-10-31-giao-dien-da-thiet-bi-bao-cao.md). Khảo sát: [`-khao-sat.md`](2026-10-31-giao-dien-da-thiet-bi-khao-sat.md).
- G0: APK trên Sony khớp SHA-256 `6e3d6b6e…a029` (Debug thường `a2f0a88`).
- G1: `MeloLayoutMatrixTest` (7 ca), commit `d9ab585`. Debug 2317/21/0/0 (toàn bộ), Release 7/0/0/0 (chỉ ca mới). Không dòng nào lệch bảng mục 4; không sửa `MeloScreenPolicy`.
- G2: khảo sát **theo GD3** vì bàn giao **chưa ghi** chủ dự án cho phép `wm size` / `wm density` / `font_scale`. Đã chụp M3 dọc, M3 ngang, M9 chia đôi. **Chưa kiểm:** M1, M2, M4–M8, F1–F3.
- Lỗi tìm được (chi tiết ở file khảo sát):

| Mã | Tóm tắt | Nhóm | Mức |
|---|---|---|---|
| UI-01 | M3 ngang: phần đầu Thư viện chiếm ≈ 80% chiều cao, list ≈ 1,3 dòng | (e) | Xấu rõ |
| UI-02 | M3 dọc: ô tìm mở bàn phím, list ≈ 2,5 dòng | (e) | Xấu rõ |
| UI-03 | M3 dọc: Hàng đợi toàn màn không có lề ngang | (d) gần đúng | Xấu rõ |
| UI-04 | M3 ngang: cột Đang phát bên trái sát mép | (d) gần đúng | Nhỏ |
| UI-05 | M3 dọc: dòng “Tạm dừng” đè mép trên list / dính chip | (b) | Nhỏ |
| UI-06 | M3 ngang: bàn phím toàn màn (extract UI) che app | Đề xuất | Nhỏ |

- **Chờ chủ dự án** (xem khảo sát mục 3):
  - cho phép đổi cỡ màn / chữ để khảo sát thêm, hoặc chấp nhận “chưa kiểm” ở chặng C;
  - gạch lỗi không muốn sửa.

  Không trả lời → chặng B sửa UI-01…UI-05 (UI-06 là Đề xuất, không sửa).
- Thiết bị đã khôi phục. `wm size` / `wm density` / `font_scale` không bị đổi; xoay về `user_rotation=0`, `accelerometer_rotation=1`; thoát chia đôi. Nhạc: một lần chạm vô tình **tiếp tục phát ≈ 15–30 giây** bài #140 đang dừng, đã tạm dừng lại (00:46). `SONY_SCREEN_END=OFF` lúc 20:34:54.
- Ảnh private ở `docs/plans/artifacts/2026-10-31-giao-dien-da-thiet-bi/G2-20261031-203502/` (không commit).
- Không merge/push/public, không ghi PASS. Không có APK mới.
- Trạng thái: **CHẶNG A XONG — chờ chủ dự án xem bảng lỗi, giao chặng B.**
- HEAD chặng B: commit cuối của nhánh (chứa file này); số thật nằm trong tin nhắn bàn giao.

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

## Prompt phiên thực thi — chặng B (dán nguyên văn)

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-giao-dien-da-thiet-bi (repo chuẩn D:\Bots\MeloVault-E91-Shareable; KHÔNG làm trong thư mục chính)
TASK_ID: 2026-10-31-giao-dien-da-thiet-bi
Nhánh: dot/2026-10-31-giao-dien-da-thiet-bi
Kế hoạch đã chốt: docs/plans/2026-10-31-giao-dien-da-thiet-bi.md
Bàn giao: docs/plans/2026-10-31-giao-dien-da-thiet-bi-ban-giao.md; HEAD dự kiến: commit cuối của nhánh (chứa file bàn giao; số thật do tin nhắn bàn giao chặng A ghi)
Baseline: 94ab6b0 (app/ giống a2f0a88). Chặng A đã có commit test d9ab585.
PHIÊN: 2 = chặng B, bước G3 (sửa lỗi UI-xx thuộc GD4 + gate Ni; KHÔNG build Tiny, KHÔNG cài APK)
Lưu ý riêng:
- Đọc docs/plans/2026-10-31-giao-dien-da-thiet-bi-khao-sat.md. Sửa UI-01…UI-05 theo GD4 trừ lỗi chủ dự án đã gạch trong bàn giao (mục "Chờ chủ dự án"). UI-06 là Đề xuất: KHÔNG sửa.
- Mỗi lỗi một commit: `fix(ui-da-thiet-bi): UI-xx …`. Điều kiện tính được bằng hàm thuần thì thêm test JVM (GD6).
- KHÔNG sửa MeloScreenPolicy.chooseLayout/profileFor hay hằng ngưỡng; MeloLayoutMatrixTest phải vẫn xanh.
- Không đụng service, DB, TDLib, package, signer, versionCode. Không bố cục mới, không đổi màu/nhận diện.
- Gate Ni (một lần, log ra file, chỉ đọc dòng tổng kết + cộng XML): test Debug + Release; assembleShareableDebug + Release; -PmeloOpen=true assembleShareableDebug; assemblePersonalDebug; git diff --check.
- Không dùng Sony trong chặng này (không có APK mới). Chưa kiểm cỡ khác Sony gốc: ghi "chưa kiểm" nếu chủ dự án chưa cho phép đổi wm size/density/font_scale.

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, file bàn giao, kế hoạch, báo cáo, khảo sát. Kiểm git status, branch, HEAD, diff --check, agent đang chạy. Cây bẩn không rõ nguồn hoặc HEAD lạ → STOP.
2. G3 theo mục 5 kế hoạch. Sau mỗi lỗi cập nhật docs/plans/2026-10-31-giao-dien-da-thiet-bi-bao-cao.md (mở đầu "Thực thi: <Claude Code/Codex> <model>, phiên chặng B") và file bàn giao.
3. Commit trên dot/2026-10-31-giao-dien-da-thiet-bi. Không merge/push/public, không ghi PASS, không chọn việc khác.

Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng, bàn giao phiên thực thi tiếp theo." kèm prompt chặng C đã điền HEAD thật.
```
