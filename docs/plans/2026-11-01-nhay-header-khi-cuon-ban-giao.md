# 2026-11-01-nhay-header-khi-cuon — Bàn giao (checkpoint)

## Checkpoint 1 — 01/11/2026, kết thúc phiên lập kế hoạch

1. **TASK_ID:** `2026-11-01-nhay-header-khi-cuon`.
   - Vai: lập kế hoạch. Công cụ: Claude Code (model do chủ dự án chọn).
   - Yêu cầu: chủ dự án báo lỗi “kéo danh sách bị nháy chồng hình” ở Playlist và các danh sách khác khi lướt nhanh (video 01/11 22:21). Chủ dự án chọn “Viết kế hoạch”.
2. **Repo/worktree:** `D:\Bots\_worktrees\MeloVault-nhay-header-khi-cuon` (worktree của `D:\Bots\MeloVault-E91-Shareable`).
   - Nhánh `dot/2026-11-01-nhay-header-khi-cuon`.
   - Baseline `8a8c978` (tip `feature/e93-03d-y700-offline-cockpit` lúc tạo nhánh).
   - Commit kế hoạch: xem `git log -1 -- docs/plans/2026-11-01-nhay-header-khi-cuon-ban-giao.md`.
   - Chưa có source commit/build.
3. **Hồ sơ:** [kế hoạch](2026-11-01-nhay-header-khi-cuon.md). Báo cáo/sơ bộ/APK: chưa có.

| Bước | Trạng thái | Bằng chứng | Việc còn lại |
|---|---|---|---|
| Chẩn đoán | Xong | Kế hoạch mục 3 (khung hình video + `MeloLibraryPane.kt:240-259`, `MeloPhoneScreen.kt:103-105`, `MeloLibraryPolicy.kt:233-238`) | Xác nhận nguyên nhân 2 bằng log trên Sony (S1/S8) |
| Kế hoạch S0–S10 | Xong | `2026-11-01-nhay-header-khi-cuon.md` | Chủ dự án chốt, mở phiên thực thi |
| S0–S10 thực thi | Chưa làm | — | Theo kế hoạch mục 6 |

4. **File chưa commit:** không có (sau commit kế hoạch).
5. **Test/build:** chưa chạy (phiên lập kế hoạch không build).
6. **APK/thiết bị:**
   - Chưa có APK mới. Phiên này không dùng Sony.
   - Bản trong video của chủ dự án: “nhiều playlist có tên” `fe518f5` vc121, quay trên máy của chủ dự án (1080×2340).
   - Video gốc không lưu vào repo vì có PII trong tên bài.
7. **Quyết định/rủi ro:**
   - Chọn phương án A (không đổi header khi đang kéo tay; delta theo cùng dòng; ngưỡng dp cộng dồn).
   - Loại phương án B (translationY) và C (chỉ tăng ngưỡng); lý do ở kế hoạch mục 5.
   - Điểm cần chủ dự án xác nhận khi nghiệm thu: header chỉ gấp khi nhả tay hoặc khi đang trôi.
8. **Trạng thái:** phiên lập kế hoạch đã dừng sau commit, không còn tác vụ chạy.

### Prompt thực thi (dán vào phiên MỚI, model do chủ dự án chọn)

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH
Repo/worktree: D:\Bots\_worktrees\MeloVault-nhay-header-khi-cuon (repo chuẩn D:\Bots\MeloVault-E91-Shareable)
TASK_ID: 2026-11-01-nhay-header-khi-cuon
Nhánh: dot/2026-11-01-nhay-header-khi-cuon
Kế hoạch: docs/plans/2026-11-01-nhay-header-khi-cuon.md
Bàn giao: docs/plans/2026-11-01-nhay-header-khi-cuon-ban-giao.md; HEAD dự kiến: commit kế hoạch (git log -1)
Baseline: 8a8c978
PHIÊN: GỘP (S0–S10); nếu thiếu quota thì dừng ở checkpoint sau S6 (Chặng A Ni) và bàn giao Chặng B (Tiny/Sony).
Lưu ý riêng:
- Lỗi: header (hàng MeloVault + Đã đồng bộ) ẩn/hiện liên tục khi kéo/lướt danh sách bài (MeloLibraryPane onScroll).
- Sửa theo phương án A ở mục 5–6 kế hoạch. Chỉ đụng file ở mục 8; không sửa TelegramPreviewActivity, MainActivity, Media3/DB/TDLib.
- Tái hiện baseline trên Sony (S1) trước khi sửa; đo số lần đổi header bằng log Debug MeloHeaderFold.
- Không tăng versionCode; video/ảnh có tên bài/kênh riêng tư không commit.
Làm theo docs/workflow/PROMPT_SONNET.md (Claude Code) hoặc PROMPT_CODEX.md (Codex).
```
