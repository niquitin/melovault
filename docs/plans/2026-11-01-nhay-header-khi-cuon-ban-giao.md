# 2026-11-01-nhay-header-khi-cuon — Bàn giao (checkpoint)

## Checkpoint 2 — 01/11/2026, sau S6 (Chặng A xong trên Ni)

1. **TASK_ID:** `2026-11-01-nhay-header-khi-cuon`. Vai: thực thi (Claude Code, Sonnet 5.5), phiên GỘP; đang ở ranh giới Chặng A → Chặng B.
2. **Repo/worktree:** `D:\Bots\_worktrees\MeloVault-nhay-header-khi-cuon`; nhánh `dot/2026-11-01-nhay-header-khi-cuon`; baseline `8a8c978`; commit kế hoạch `8a48584`; **commit mã `4fe5a37`** (nguồn dùng build).
   - Commit chứa checkpoint này: `git log -1 -- docs/plans/2026-11-01-nhay-header-khi-cuon-ban-giao.md`.
3. **Hồ sơ:** [kế hoạch](2026-11-01-nhay-header-khi-cuon.md), [báo cáo](2026-11-01-nhay-header-khi-cuon-bao-cao.md). Chưa có sơ bộ/APK.

| Bước | Trạng thái | Bằng chứng | Việc còn lại |
|---|---|---|---|
| S0 | Xong | HEAD `8a48584`, cây sạch | — |
| S1 baseline Sony | Xong, tái hiện được | Báo cáo mục 3; video `docs/plans/artifacts/2026-11-01-nhay-header-khi-cuon/base_0*.mp4` (private, gitignore); header đổi 8 và 14 lần/cử chỉ giữ tay | — |
| S2–S5 mã + test | Xong | Commit `4fe5a37` | — |
| S6 gate Ni | Xong | Test 2698/21/0/0 Debug và Release; assemble Debug/Release xanh; `diff --check` sạch | — |
| S7 build Tiny | **Chưa làm** | — | Bundle + SHA256, clone cô lập, build Debug có config, verify package/version/cert |
| S8 cài + QA Sony | Chưa làm | — | `install -r`, Q1–Q6, đếm số lần đổi header bằng log `MeloHeaderFold` và `count_header.ps1`, `00_tong_hop.png` |
| S9–S10 | Chưa làm | — | Drive owner-only, `-apk-test.md`, `-hinh-nghiem-thu.md`, prompt sơ bộ |

4. **File chưa commit:** chỉ các file hồ sơ (báo cáo + bàn giao) của checkpoint này nếu chưa commit; mã đã commit.
5. **Thiết bị:** Sony `QV725WEQ54` trên Tiny, app đang cài vc121 0.6.96-beta **bản cũ (chưa sửa)**. Màn Sony đã tắt sau baseline. Tiny có sẵn thư mục scratch `C:\Bots\_qa_temp\nhay-header\` (video/ảnh baseline gốc và `gesture.ps1` dùng quay + chạy cử chỉ).
6. **Công cụ QA đã có:**
   - `gesture.ps1 -Tag <t> -Seq 'F,S,U,SU,H,HU,P' -Limit <s>` trên Tiny: quay màn hình, chạy cử chỉ, lấy logcat `MeloHeaderFold`. `H`/`HU` = giữ tay kéo xuống/lên bằng `input motionevent` (tái hiện lỗi; `input swipe` thì không).
   - `count_header.ps1 -Video <mp4>` (trên Ni, dùng ffmpeg Ni): đếm số lần header đổi theo độ sáng vùng biểu tượng ⚙ tại (810,105,80×80) trên khung 1080×2520. Bản sao script nằm trong scratchpad của phiên; nếu mất, viết lại theo mô tả.
   - ffmpeg trên Tiny **không chạy được**; xử lý video trên Ni sau khi chép qua `T:\_qa_temp\nhay-header\` (chỉ đọc).
7. **Rủi ro/quyết định:**
   - Baseline Sony không có playlist ≥ 20 bài; dùng Thư viện/Tất cả. Q1–Q3 làm trên tab đó (ghi rõ trong QA).
   - Hành vi mới: header chỉ đổi khi nhả tay hoặc khi danh sách trôi (CẦN chủ dự án xác nhận khi nghiệm thu).
8. **Trạng thái bàn giao:** nếu phiên này tiếp tục S7–S10 thì checkpoint này được thay bằng checkpoint cuối; nếu phiên dừng ở đây thì không còn tác vụ chạy.

### Prompt tiếp tục (Chặng B)

```text
Tiếp tục TASK_ID 2026-11-01-nhay-header-khi-cuon, vai thực thi, Chặng B (S7–S10).
Worktree D:\Bots\_worktrees\MeloVault-nhay-header-khi-cuon; nhánh dot/2026-11-01-nhay-header-khi-cuon; mã dùng build 4fe5a37.
Đọc CLAUDE.md mục 0, docs/plans/2026-11-01-nhay-header-khi-cuon-ban-giao.md, kế hoạch, báo cáo.
Phiên trước đã dừng; không có tác vụ chạy. Kiểm Git trước khi làm.
Làm S7 (build Tiny có config) → S8 (cài Sony, QA Q1–Q6) → S9–S10. Không tăng versionCode; không commit video/ảnh riêng tư.
```
