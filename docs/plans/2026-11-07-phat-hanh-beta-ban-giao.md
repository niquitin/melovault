# Bàn giao — Phát hành bản beta cho bạn bè

## Checkpoint lập kế hoạch · 07/11/2026 · Claude Code, Opus 5.5

**Kế hoạch xong, chưa code.** Bốn quyết định của chủ dự án: [kế hoạch](2026-11-07-phat-hanh-beta.md) mục 1.

| Mục | Giá trị |
|---|---|
| Worktree / nhánh | `D:\Bots\_worktrees\MeloVault-phat-hanh-beta` / `dot/2026-11-07-phat-hanh-beta` |
| Baseline | `a982a9a7cf9a0b9b912cb6ce6d092473d3195e79` |
| File chưa commit | Không |
| Job còn chạy | Không |

| Bước | Trạng thái | Việc còn lại |
|---|---|---|
| Sơ bộ Widget | Xong: không phát hiện lỗi chặn | Biên bản `docs/plans/2026-11-03-widget-dang-phat-nghiem-thu-so-bo.md` nằm trên nhánh `dot/2026-11-03-widget-dang-phat`, commit `85e4031`, chưa push |
| Chủ dự án ĐẠT Widget | **Đang chờ** | Sau ĐẠT: bỏ tên bài trong `-bao-cao.md` của widget, merge, giải `HANDOFF.md`, chạy lại gate |
| S0–S8 | Chưa làm | Phiên thực thi mới |
| Sơ bộ, ĐẠT, chuẩn bị đăng | Chưa làm | Kế hoạch mục 4 |

**Sony:** đang cài APK widget `58BD55F0…` (`app.melovault.player`), widget để sẵn trên Home, nhạc tạm dừng, Display State=OFF.

## Prompt phiên thực thi

Chỉ mở sau khi Widget đã ĐẠT và merge xong.

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-phat-hanh-beta
TASK_ID: 2026-11-07-phat-hanh-beta
Nhánh: dot/2026-11-07-phat-hanh-beta
Kế hoạch đã được chủ dự án CHỐT: docs/plans/2026-11-07-phat-hanh-beta.md
Bàn giao: docs/plans/2026-11-07-phat-hanh-beta-ban-giao.md
Baseline: a982a9a7cf9a0b9b912cb6ce6d092473d3195e79
PHIÊN: GỘP (S0–S8), hoặc chủ dự án tách: 1 = S0–S5, 2 = S6–S8
Lưu ý: S0 phải thấy Widget đã ĐẠT và merge, không thì BLOCKED. Không tăng versionCode, không tạo khóa ký.
Tuyệt đối không đăng app.melovault.player. Không công khai gì trong phiên này.
Làm theo docs/workflow/PROMPT_SONNET.md (Codex: PROMPT_CODEX.md). Trả lời tiếng Việt.
```

Đã dừng. Chờ chủ dự án phản hồi về Widget.
