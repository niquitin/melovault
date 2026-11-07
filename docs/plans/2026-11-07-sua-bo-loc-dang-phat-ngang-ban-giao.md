# Bàn giao — Sửa nút Bộ lọc, chip thể loại và màn Đang phát khi xoay ngang

## Checkpoint thực thi S0–S6 · 07/11/2026 · Claude Code, Sonnet 5.5

**Code và gate Ni xong; S7–S9 (build Tiny, QA Sony, bàn giao) chưa làm.** Commit: S1 `1efb96b`, S2 `701d834`, S3 `20b3011`, S4 `cb13c21`, S5 `233954f`. Gate: Debug và Release đều 207 suite, 2.799 test, 21 skipped, 0 failures, 0 errors; assemble Shareable Debug/Release, `-PmeloOpen=true` và Personal Debug đều xanh; `git diff --check` sạch. Chi tiết: [báo cáo](2026-11-07-sua-bo-loc-dang-phat-ngang-bao-cao.md). Không file chưa commit, không job chạy.

Bước tiếp (nếu phiên này dừng): S7 — bundle SHA-256, clone cô lập trên Tiny, build có Telegram config, kiểm APK, cài Sony `QV725WEQ54` (thay candidate widget `58BD55F0…`).

## Checkpoint lập kế hoạch · 07/11/2026 · Claude Code, Opus 5.5 (lịch sử)

**Kế hoạch xong, chưa code.** Bước tiếp: phiên thực thi mới, model do chủ dự án chọn. Quyết định hiệu lực (chủ dự án chốt trong chat 07/11/2026): bỏ nút ☰ ở kiểu mặc định; bìa đặt cạnh tên bài khi ngang thấp; chạm lại chip thể loại để bỏ lọc; sửa cùng chip Hẹn giờ lệch.

| Mục | Giá trị |
|---|---|
| TASK_ID | `2026-11-07-sua-bo-loc-dang-phat-ngang` |
| Worktree / nhánh | `D:\Bots\_worktrees\MeloVault-sua-bo-loc-dang-phat-ngang` / `dot/2026-11-07-sua-bo-loc-dang-phat-ngang` |
| Baseline | `f82185ba2ef17fce97d4680de2cd2dc86b2e1b4a` |
| Commit kế hoạch | `git log -1 --format=%H -- docs/plans/2026-11-07-sua-bo-loc-dang-phat-ngang.md` |
| Kế hoạch | [2026-11-07-sua-bo-loc-dang-phat-ngang.md](2026-11-07-sua-bo-loc-dang-phat-ngang.md) |
| File chưa commit | Không |
| Job còn chạy | Không |

| Bước | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| Lập kế hoạch | Xong | Nguyên nhân D1, D3 từ mã; D2 khớp số đo Sony; D4 là suy luận, S3 kiểm lại |
| S0–S6 code và gate Ni | Xong (07/11/2026) | Xem checkpoint trên cùng và báo cáo |
| S7–S9 build, QA Sony, bàn giao | Chưa làm | |
| Sơ bộ, nghiệm thu chủ dự án | Chưa làm | |

## Trạng thái Sony khi lập kế hoạch

Khảo sát bằng APK sẵn có, không cài gì. Sony `QV725WEQ54` đang giữ candidate widget vc122 `58BD55F0…`, nhạc tạm dừng, không có widget trên Home. `SONY_SCREEN_END`: Display State=OFF, đo lúc kết thúc phiên sơ bộ 07/11, không đo lại khi viết kế hoạch.

Bằng chứng khảo sát ở ngoài repo: biên bản `D:\Bots\_handoff\MeloVault\2026-11-07-so-bo-toan-app\`, ảnh riêng tư `C:\Bots\_qa_temp\sobo-20261107\` trên Tiny (`42_ngang_deck.png`, `45_ngang_deck_cuon.png`, `50_bo_loc_2.png`). Không commit các ảnh này.

## Lưu ý cho phiên thực thi

- Cài APK nhiệm vụ này sẽ thay candidate widget trên Sony. Ghi bản đang cài vào bàn giao; widget sơ bộ sau đó phải cài lại APK của nó.
- Repo GitHub công khai và hồ sơ sẽ lên đó sau ĐẠT: không ghi tên bài, tên kênh hay dữ liệu Telegram vào file `.md`.
- Khi thử trên Sony, gửi phím qua `input keyevent` hoặc `input text` lúc không có ô nhập đang focus sẽ thoát touch mode, và phím cách có thể bấm trúng nút đang focus (đã gặp: bật Đảo). Luôn dump UI kiểm tọa độ trước khi chạm, vì vị trí đổi theo độ dài tên bài và độ cuộn.
- Lệnh DC `start_process` nuốt `$`: ghi script `.ps1` qua `write_file` rồi chạy `-File`. Ghi kết quả UTF-8 ra file và đọc qua `T:` để giữ tiếng Việt. `uiautomator dump` có thể lỗi im lặng nên phải xóa file cũ trước khi pull.

## Prompt phiên thực thi

Chủ dự án mở phiên mới, chọn model rồi dán:

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-sua-bo-loc-dang-phat-ngang
TASK_ID: 2026-11-07-sua-bo-loc-dang-phat-ngang
Nhánh: dot/2026-11-07-sua-bo-loc-dang-phat-ngang
Kế hoạch đã được chủ dự án CHỐT: docs/plans/2026-11-07-sua-bo-loc-dang-phat-ngang.md
Bàn giao: docs/plans/2026-11-07-sua-bo-loc-dang-phat-ngang-ban-giao.md
HEAD dự kiến: commit mới nhất của nhánh, chỉ có tài liệu so với baseline
Baseline: f82185ba2ef17fce97d4680de2cd2dc86b2e1b4a
PHIÊN: GỘP (S0–S9), hoặc chủ dự án tách: 1 = S0–S6, 2 = S7–S9
Lưu ý: 4 mục D1–D4, không mở rộng phạm vi. Không tăng versionCode.
Cài APK sẽ thay candidate widget trên Sony, ghi rõ trong bàn giao.
Làm theo docs/workflow/PROMPT_SONNET.md (Codex: PROMPT_CODEX.md). Trả lời tiếng Việt.
```

Đã dừng. Mở phiên mới, chọn model thực thi tiết kiệm hơn theo lựa chọn của chủ dự án.
