# Bàn giao — lời nhạc Shareable (P2-B)

TASK_ID: **2026-10-31-loi-nhac-p2b**. Worktree `D:\Bots\_worktrees\MeloVault-loi-nhac-p2b`; nhánh `dot/2026-10-31-loi-nhac-p2b`; baseline `94ab6b0`.

## Checkpoint lập kế hoạch — 31/10/2026, Claude Code

- Kế hoạch: [`2026-10-31-loi-nhac-p2b.md`](2026-10-31-loi-nhac-p2b.md). Commit đầu `118c5e9`; HEAD dự kiến khi bắt đầu thực thi là commit chứa file bàn giao này.
- Đã làm:
  - đọc phụ lục P2-B;
  - đọc mã lyrics ở `main` (LrcParser/LyricsModels là mã thuần; LyricsRepository gắn với CarApiClient);
  - đọc tài liệu API LRCLIB `lrclib.net/docs.md`.
- Không code, không build, không dùng ADB/Drive. Không có tiến trình nào còn chạy.
- Chặng: **A** = L0–L4 (lõi + test, Ni); **B** = L5–L6 (UI + gate Ni); **C** = L7–L8 (Tiny + QA Sony + hồ sơ).
- **Chờ chủ dự án:** 6 câu ở kế hoạch mục 9. Chủ dự án không trả lời khác thì dùng mặc định. Riêng câu 3 (URL/email liên hệ) để trống thì User-Agent chỉ ghi `MeloVault/<ver>`.
- Không merge/push. Trạng thái: **KẾ HOẠCH — chờ chủ dự án giao thực thi.**

## Prompt phiên thực thi — chặng A (dán nguyên văn)

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-loi-nhac-p2b (repo chuẩn D:\Bots\MeloVault-E91-Shareable; KHÔNG làm trong thư mục chính)
TASK_ID: 2026-10-31-loi-nhac-p2b
Nhánh: dot/2026-10-31-loi-nhac-p2b
Kế hoạch đã chốt: docs/plans/2026-10-31-loi-nhac-p2b.md
Bàn giao: docs/plans/2026-10-31-loi-nhac-p2b-ban-giao.md; HEAD dự kiến: commit cuối của nhánh (chứa file bàn giao)
Baseline: 94ab6b0 (nhánh tích hợp feature/e93-03d-y700-offline-cockpit)
PHIÊN: 1 = chặng A, lõi + test Ni, bước L0–L4
Lưu ý riêng:
- Theo đúng LD1–LD10 và mục 4; câu nào ở mục 9 chủ dự án đã trả lời khác (ghi trong bàn giao) thì theo câu trả lời đó.
- Chỉ dùng lại LrcParser/LyricsLine ở app/src/main/.../lyrics; KHÔNG import LyricsRepository/LyricsCache/CarApiClient.
- Không thêm thư viện HTTP; HttpURLConnection sau interface LyricsTransport để test bằng transport giả. Chặng A KHÔNG gọi mạng thật.
- Request chỉ gồm track_name/artist_name/album_name/duration + User-Agent; test phải chứng minh không có ID Telegram/songKey/đường dẫn.
- Cache ở noBackupFilesDir/melo_lyrics_v1/<ownerId>/, không đụng TDLib DB/cache.
- Hết L4: dừng, cập nhật bàn giao, đưa prompt chặng B (L5–L6). Không làm UI trong chặng A.

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, file bàn giao và kế hoạch trên. Kiểm git status, branch, HEAD, baseline, diff --check, agent đang chạy. Cây bẩn không rõ nguồn hoặc HEAD lạ → STOP, không reset/clean/stash/đè.
2. Làm L1 → L2 → L3 → L4 đúng file/hàm trong kế hoạch, commit theo mốc. Test mới: MeloLyricsPolicyTest (≥ 20 ca), ShareableLyricsProviderTest, ShareableLyricsCacheTest, ShareableLyricsControllerTest. Chạy test mục tiêu Debug + Release, rồi full :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest; log ra file dưới docs/plans/artifacts/2026-10-31-loi-nhac-p2b/A-<ts>/, chỉ ghi tổng kết + cộng XML (passed/skipped/failed/errors), git diff --check. Sau mỗi mốc cập nhật docs/plans/2026-10-31-loi-nhac-p2b-bao-cao.md (mở đầu "Thực thi: <Claude Code/Codex> <model>, phiên chặng A") và file bàn giao.
3. Không ADB/thiết bị trong chặng A; ghi SONY_SCREEN_END = KHÔNG THAO TÁC.
4. Commit trên dot/2026-10-31-loi-nhac-p2b. Không merge/push/public, không ghi PASS, không chọn việc khác.

Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng, bàn giao phiên thực thi tiếp theo." kèm prompt chặng B đã điền HEAD thật.
```
