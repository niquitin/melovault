# Bàn giao — 2026-11-06-hop-nhat-phien-ban

## Checkpoint cuối thực thi · 06/11/2026 · Claude Code, Opus 5.5

**QA CANDIDATE — CHƯA NGHIỆM THU.** Thực thi xong, chờ sơ bộ/chủ dự án. Chưa merge, chưa push.

| Mục | Giá trị |
|---|---|
| Worktree / nhánh | `D:\Bots\_worktrees\MeloVault-hop-nhat-phien-ban` / `dot/2026-11-06-hop-nhat-phien-ban` |
| Baseline | `42be21cfe851ff5f7dd797de466b59c8381caeff` |
| Source APK | `7cd4ebb0af7a3f75c6a5c038831f7720b24319df`; các commit sau chỉ là hồ sơ. Kiểm: `git diff 7cd4ebb..HEAD -- app/` phải rỗng |
| APK | vc122 / `0.6.97-beta`, 33.894.510 B, SHA-256 `BF7B23A74588BD9826CA9DFBFEBE0AEC5DEC61F8F20D5CD5914CCDDC38C56441`, đang cài trên Sony |
| File chưa commit | Không (artifact private bị `.gitignore`) |
| Job còn chạy | Không có job của nhiệm vụ. Daemon Gradle không dừng |
| Sony | EQ Tắt/Phẳng, nhạc tạm dừng, cấu hình xoay về gốc, `SONY_SCREEN_END`: Display State=OFF |

| Bước | Trạng thái |
|---|---|
| S0–S2 kế hoạch, nâng phiên bản, gate Ni | Xong |
| S3–S5 bundle, build Tiny, cài Sony | Xong |
| S6 QA Q1–Q6 | Xong, [báo cáo](2026-11-06-hop-nhat-phien-ban-bao-cao.md) mục 4 |
| S7 ảnh, Drive, hồ sơ | Xong; tải APK bằng trình duyệt chưa thử |
| Sơ bộ độc lập | Chưa làm |
| Nghiệm thu chủ dự án | Chờ |

**Việc sau ĐẠT:** ghi `*-nghiem-thu.md`, merge `--no-ff` vào `feature/e93-03d-y700-offline-cockpit` trong worktree `C:\Users\ntdun\.codex\worktrees\workflow-md-handoff\MeloVault-E91-Shareable`, cập nhật mục đầu `HANDOFF.md` (chưa cập nhật trên nhánh này để tránh xung đột), rồi push fast-forward lên `source/feature-e93-03d-y700-offline-cockpit` theo chỉ thị 02/11. Không đụng `main`, không đẩy `dot/*`, chưa phát hành APK công khai.

**Widget:** nhánh `dot/2026-11-03-widget-dang-phat` chưa gộp. Nếu chủ dự án giao sửa: sửa 2 lỗi trong biên bản sơ bộ của widget, tích hợp baseline mới nhất (có xung đột `ShareablePlaybackService.kt`, `HANDOFF.md`), dùng versionCode ≥ 122, vì Sony đã ở vc122.

## Prompt nghiệm thu sơ bộ (dán vào phiên MỚI)

```text
MELOVAULT — PHIÊN NGHIỆM THU SƠ BỘ NHANH
Worktree: D:\Bots\_worktrees\MeloVault-hop-nhat-phien-ban   TASK_ID: 2026-11-06-hop-nhat-phien-ban
Nhánh: dot/2026-11-06-hop-nhat-phien-ban   Source APK: 7cd4ebb (sau đó chỉ có hồ sơ)
Đọc: CLAUDE.md → kế hoạch → -bao-cao.md → -ban-giao.md → -apk-test.md → docs/workflow/PROMPT_NGHIEM_THU_SO_BO.md.
Việc: git log 42be21c..HEAD --oneline; git diff 42be21c..7cd4ebb phải chỉ là versionCode/versionName trong app/build.gradle và tài liệu kế hoạch; đối chiếu SHA APK BF7B23A7… và bản đang cài trên Sony; xem ảnh tổng hợp. Không chạy lại toàn bộ Gradle/QA nếu không có lý do. Nếu dùng Sony: kiểm serial/model, không đổi dữ liệu/tài khoản, màn OFF trong finally.
Ghi docs/plans/2026-11-06-hop-nhat-phien-ban-nghiem-thu-so-bo.md: SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN / CẦN SỬA / BLOCKED, kèm phạm vi và giới hạn (CHƯA XÁC NHẬN ÂM THANH THỰC). Không sửa code, không build mới, không merge/push.
Kết thúc: "Đã dừng, chờ chủ dự án nghiệm thu." + cách phản hồi ĐẠT / Yêu cầu sửa.
```
