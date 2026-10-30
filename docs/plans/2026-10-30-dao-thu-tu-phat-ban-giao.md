# Bàn giao — 2026-10-30-dao-thu-tu-phat

## Checkpoint hiện tại

1. **TASK_ID** `2026-10-30-dao-thu-tu-phat`; 30/10/2026 ~11:20 +07; Claude Code, vai **lập kế hoạch**. Chủ dự án giao: “Giao lập kế hoạch chi tiết Đảo” (gỡ BLOCKED thứ tự khi bật Đảo của S6, nhiệm vụ `2026-10-29-melo-ui-ux-ke-hoach`).
2. **Worktree** `D:\Bots\_worktrees\MeloVault-dao-thu-tu-phat`; nhánh `dot/2026-10-30-dao-thu-tu-phat`; baseline source `c6c7970` (R1c `b599e80` + hồ sơ). Commit kế hoạch: xem `git log -1 -- docs/plans/2026-10-30-dao-thu-tu-phat-ban-giao.md`. Chưa có commit source/build.
3. **Hồ sơ:** [Kế hoạch](2026-10-30-dao-thu-tu-phat.md). Chưa có báo cáo/sơ bộ/APK.

| Bước | Trạng thái | Bằng chứng | Việc còn lại |
|---|---|---|---|
| Kế hoạch | Xong | File kế hoạch, commit trên nhánh | Chủ dự án duyệt Q1–Q9 (mục 4) |
| S0 tái hiện Sony | Chưa làm | — | Phiên thực thi 1 |
| S1 policy + test | Chưa làm | — | Phiên thực thi 1 |
| S2 service | Chưa làm | — | Phiên thực thi 1 |
| S3 Activity Telegram | Chưa làm | — | Phiên thực thi 1 |
| S4 Nhạc trên máy | Chưa làm | — | Phiên thực thi 1 (bỏ nếu Q2 = chỉ Telegram) |
| S5 UI chữ + full test/build Ni | Chưa làm | — | Phiên thực thi 1 |
| S6 Tiny build, QA, Drive | Chưa làm | — | Phiên thực thi 2 |

4. **File chưa commit:** không có của phiên này. Thư mục chính `D:\Bots\MeloVault-E91-Shareable` có `?? .codex-remote-attachments/` và `?? docs/plans/2026-10-29-hoc-hoi-ui-ux-telegram-music.md` — **không thuộc nhiệm vụ này**, không động vào.
5. **Test/build:** chưa chạy (phiên kế hoạch).
6. **APK/thiết bị:** không cài, không bật màn. Sony theo hồ sơ R1 đang có R1c Debug vc121 — phải kiểm lại. `SONY_SCREEN_END`/`SHANLING_SCREEN_END`: không thao tác.
7. **Quyết định cần chủ dự án:** Q1–Q9 trong kế hoạch mục 4 (mặc định đề xuất: xáo toàn bộ hàng chờ, bài đang phát đứng đầu; Telegram + Nhạc trên máy; tắt về thứ tự gốc không ngắt bài; chạm hàng chờ không xáo lại; Phát tiếp theo chèn sau bài hiện tại; Phát ngẫu nhiên bắt đầu ngẫu nhiên). Duyệt kế hoạch = cho sửa `ShareablePlaybackService` trong phạm vi mục 5. Lựa chọn khác ghi vào mục “Quyết định chủ dự án” bên dưới trước khi thực thi.
8. **Trạng thái:** phiên kế hoạch đã dừng sau commit tài liệu; không tác vụ chạy nền.

## Quyết định chủ dự án

- (chưa có — phiên thực thi dùng mặc định Q1–Q9 nếu chủ dự án giao thực thi mà không ghi khác)

## Prompt phiên thực thi 1 (S0–S5, Ni + Sony khảo sát) — dán nguyên văn

```text
Thực thi TASK_ID 2026-10-30-dao-thu-tu-phat, phiên thực thi 1, chặng S0–S5.
Worktree D:\Bots\_worktrees\MeloVault-dao-thu-tu-phat; nhánh dot/2026-10-30-dao-thu-tu-phat;
HEAD dự kiến = commit kế hoạch (git log -1 -- docs/plans/2026-10-30-dao-thu-tu-phat-ban-giao.md), baseline source c6c7970.
Đọc CLAUDE.md mục 0 (Codex đọc thêm AGENTS.md), docs/workflow/QUY_TRINH_3_VAI_TRO.md,
docs/plans/2026-10-30-dao-thu-tu-phat-ban-giao.md và docs/plans/2026-10-30-dao-thu-tu-phat.md.
Phiên kế hoạch đã dừng, không có tác vụ chạy nền. Chủ dự án dùng mặc định Q1–Q9 trừ khi mục
"Quyết định chủ dự án" trong file bàn giao ghi khác.
Đầu phiên: git status --short; git branch --show-current; git rev-parse HEAD; git log -5 --oneline;
git diff --check; kiểm không agent nào khác đang sửa nhánh này. Lệch → dừng, không reset/clean/stash.
S0: dùng Sony QV725WEQ54 qua Tiny DESKTOP-HNP4GVR (kiểm adb devices -l, getprop model/manufacturer,
dumpsys display/power trước-sau, bật màn keyevent 224 chỉ khi cần, tắt 223 trong finally) với APK
đang cài (không cài mới): tái hiện kế hoạch mục 3 bằng kịch bản Q-1/Q-2, chụp PNG vào
docs/plans/artifacts/2026-10-30-dao-thu-tu-phat/ (không commit ảnh có Telegram PII).
S1→S5: làm đúng kế hoạch mục 5 và 6, commit riêng từng chặng, sau mỗi chặng cập nhật
docs/plans/2026-10-30-dao-thu-tu-phat-bao-cao.md (tạo mới, ≤ ~250 dòng, bảng trước) và file bàn giao.
Chỉ sửa các file kế hoạch nêu: MeloShuffleOrderPolicy.kt (mới) + test, ShareablePlaybackService.kt,
TelegramPreviewActivity.kt, MeloQueuePane.kt (MeloQueueUiPolicy) + MeloQueueUiPolicyTest, KDoc
MeloScreenHost.queue(). Không đổi schema/songKey/TDLib/package/signer/versionCode.
Cuối S5 chạy một lần, log ra file artifacts, chỉ đọc tổng kết + XML:
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
git diff --check
Ghi passed/skipped/failed từng biến thể. Không build Tiny/cài APK/Drive trong phiên này (đó là S6).
Gặp điều kiện dừng ở kế hoạch mục 10 → ghi BLOCKED đúng phần, cập nhật bàn giao, dừng.
Kết thúc: commit hồ sơ, ghi prompt phiên thực thi 2 (S6) vào file bàn giao, trả lời 3 phần
Đã làm / Chưa kiểm chứng / Rủi ro hoặc điểm dừng, và câu "Đã dừng, bàn giao phiên thực thi tiếp theo."
```

## Prompt phiên thực thi 2 (S6) — do phiên thực thi 1 hoàn thiện

Phiên thực thi 1 ghi prompt đầy đủ ở đây kèm HEAD source đã test, theo mẫu QUY_TRINH_3_VAI_TRO.md mục 3 và kế hoạch mục 6 (S6), 7, 8.
