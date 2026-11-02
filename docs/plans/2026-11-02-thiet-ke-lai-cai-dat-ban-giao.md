# 2026-11-02-thiet-ke-lai-cai-dat — Bàn giao (checkpoint)

## Checkpoint 0 — 02/11/2026, kết thúc phiên LẬP KẾ HOẠCH

1. **TASK_ID:** `2026-11-02-thiet-ke-lai-cai-dat`. Vai: lập kế hoạch (Claude Code). Chưa code, chưa build.
2. **Repo/worktree:** `D:\Bots\_worktrees\MeloVault-thiet-ke-lai-cai-dat`; nhánh `dot/2026-11-02-thiet-ke-lai-cai-dat`; baseline **`fa4cd09`** (tip `feature/e93-03d-y700-offline-cockpit`). Commit kế hoạch: `git log -1 -- docs/plans/2026-11-02-thiet-ke-lai-cai-dat.md`.
3. **Hồ sơ:** [kế hoạch](2026-11-02-thiet-ke-lai-cai-dat.md). Báo cáo/apk-test/hình/sơ bộ/nghiệm thu: chưa có.
4. **Quyết định chủ dự án (02/11):** LD1 ẩn Thiết bị & màn hình + Nâng cao sau 7 chạm vào dòng phiên bản; LD2 nhóm thẻ có biểu tượng + tiêu đề cố định; LD3 Giới thiệu = tên Nguyễn Dũng + GitHub + email góp ý (+ giấy phép); LD4 sửa chữ bị che, tách Màn Đang phát, rút gọn mô tả Lời nhạc (giữ công bố qua hộp đồng ý), Đồng bộ ghi Bật/Tắt.
5. **Chờ chủ dự án trước khi thực thi:** **CĐR1 địa chỉ email góp ý** (sẽ công khai). CĐR2 cách ghi tên (mặc định “Nguyễn Dũng”). Chưa có CĐR1 → phiên thực thi làm mọi bước khác, để `FEEDBACK_EMAIL` trống và DỪNG ở phần email.
6. **Thiết bị:** phiên kế hoạch không dùng Sony. Sony `QV725WEQ54` đang cài vc121 `E5D808D2…` (theo HANDOFF).
7. **File chưa commit:** không có.

| Bước | Trạng thái |
|---|---|
| Kế hoạch | Xong |
| S0–S8 (Chặng 1, Ni) | Chưa bắt đầu |
| S9–S11 (Chặng 2, Tiny/Sony/Drive) | Chưa bắt đầu |

### Prompt thực thi Chặng 1 (dán vào phiên MỚI)

```text
MELOVAULT — THỰC THI CHẶNG 1 (S0–S8, chỉ Ni: code + test + commit)
TASK_ID: 2026-11-02-thiet-ke-lai-cai-dat
Worktree: D:\Bots\_worktrees\MeloVault-thiet-ke-lai-cai-dat; nhánh dot/2026-11-02-thiet-ke-lai-cai-dat; baseline fa4cd09.
Đọc theo thứ tự: CLAUDE.md → docs/workflow/PROMPT_SONNET.md → docs/plans/2026-11-02-thiet-ke-lai-cai-dat.md → docs/plans/2026-11-02-thiet-ke-lai-cai-dat-ban-giao.md.
Email góp ý (CĐR1): <chủ dự án điền, hoặc ghi "chưa có">.
Làm S0–S8 đúng kế hoạch; S1 chỉ đọc mã/insets (tái hiện trên Sony để Chặng 2 nếu không có Tiny trong phiên này).
Cập nhật -bao-cao.md và -ban-giao.md sau mỗi bước. Không đổi package/signer/versionCode, không đụng service/Media3/DB/TDLib.
Kết thúc: commit, cập nhật bàn giao, đưa prompt Chặng 2, ghi "Đã dừng, bàn giao phiên thực thi tiếp theo."
```
