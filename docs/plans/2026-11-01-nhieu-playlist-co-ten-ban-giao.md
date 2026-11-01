# Bàn giao — Nhiều Playlist có tên

TASK_ID: **2026-11-01-nhieu-playlist-co-ten**.

## Checkpoint 01/11/2026 — lập kế hoạch xong (Claude Code, Opus 5.5)

- **Vai:** lập kế hoạch. Không code, không build, không ADB/Sony, không Drive.
- **Quyết định chủ dự án (chat 01/11):**
  - phạm vi Nhiều Playlist có tên;
  - đồng bộ ngay qua kênh hiện có;
  - Playlist cũ giữ làm “Yêu thích” mặc định;
  - thêm Sắp xếp lại bài, Lưu hàng chờ thành Playlist, Bài Nhạc trên máy.
- **Đã duyệt khi giao thực thi:** file mới `tg_named_playlists_v1/` + loại tin `#mvlist v1` (kế hoạch mục 1, 4).
- **Repo/worktree:** `D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten`; nhánh `dot/2026-11-01-nhieu-playlist-co-ten`.
- **Baseline:** `57c1be64436c3c364bcd8c1a5f8a8193ea52a553` (`feature/e93-03d-y700-offline-cockpit`).
- **Commit kế hoạch:** `git log -1 --format=%H -- docs/plans/2026-11-01-nhieu-playlist-co-ten.md`.
- **Kế hoạch:** [2026-11-01-nhieu-playlist-co-ten.md](2026-11-01-nhieu-playlist-co-ten.md). Báo cáo/sơ bộ/APK: chưa có.

| Chặng / bước | Trạng thái | Bằng chứng | Việc còn lại |
|---|---|---|---|
| Kế hoạch | Xong | file kế hoạch, commit trên nhánh | Chủ dự án giao thực thi |
| Chặng 1 S1–S5 (mô hình, lưu, session) | Chưa làm | — | Phiên thực thi 1 |
| Chặng 2 S6–S10 (`#mvlist`, coordinator, nối tự đồng bộ) | Chưa làm | — | Phiên thực thi 2 |
| Chặng 3 S11–S19 (UI) | Chưa làm | — | Phiên thực thi 3 |
| Chặng 4 S20–S24 (Tiny, Sony, Drive) | Chưa làm | — | Phiên thực thi 4 |

- **File chưa commit:** không có, sau commit kế hoạch.
- **Repo chính** `D:\Bots\MeloVault-E91-Shareable` có `?? .codex-remote-attachments/` và `?? docs/plans/2026-10-29-hoc-hoi-ui-ux-telegram-music.md` của phiên trước. Không thuộc nhiệm vụ này, không động tới.
- **Test/build:** chưa chạy. Baseline theo HANDOFF: 2445 test / 21 skipped / 0 lỗi mỗi biến thể.
- **APK/thiết bị:** không có APK mới. Sony không thao tác; `SONY_SCREEN_END=KHÔNG THAO TÁC`.
- **Chỗ cần chủ dự án quyết định:**
  1. Kế hoạch 4.5: một công tắc tự đồng bộ chung (mặc định) hay công tắc riêng.
  2. Kế hoạch mục 7: cách QA đồng bộ hai máy — bản open trên Sony do chủ dự án đăng nhập, máy thứ hai, hoặc chấp nhận BLOCKED.
- **Rủi ro chính:** E105 (tự đồng bộ) vẫn BLOCKED nghiệm thu; nhiệm vụ này dựa trên đường đó, không sửa nó.
- **Bước tiếp:** chủ dự án mở phiên thực thi mới cho **Chặng 1** bằng prompt dưới. Phiên lập kế hoạch đã dừng, không còn tác vụ chạy.

### Prompt cho phiên thực thi Chặng 1

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten (repo chuẩn D:\Bots\MeloVault-E91-Shareable — KHÔNG làm ở repo chính, nó đang ở nhánh tài liệu khác và có file chưa commit của phiên khác)
TASK_ID: 2026-11-01-nhieu-playlist-co-ten
Nhánh: dot/2026-11-01-nhieu-playlist-co-ten
Kế hoạch Claude đã được chủ dự án CHỐT: docs/plans/2026-11-01-nhieu-playlist-co-ten.md
Bàn giao: docs/plans/2026-11-01-nhieu-playlist-co-ten-ban-giao.md; HEAD dự kiến: commit kế hoạch (git log -1 --format=%H -- docs/plans/2026-11-01-nhieu-playlist-co-ten-ban-giao.md)
Model: do chủ dự án chọn; bước tiếp tục: Chặng 1, S1 → S5
Baseline: 57c1be64436c3c364bcd8c1a5f8a8193ea52a553, commit kế hoạch: git log -1 --format=%H -- docs/plans/2026-11-01-nhieu-playlist-co-ten.md
PHIÊN: 1 = code + test Ni, CHỈ Chặng 1 (S1–S5). Không làm Chặng 2/3/4.
Lưu ý riêng:
- Chủ dự án đã duyệt file mới tg_named_playlists_v1/ và tin #mvlist v1 (kế hoạch mục 1, 4). KHÔNG sửa TelegramPlaylistModel/Codec/StoreV2/V2Session/SyncProtocol/SyncCoordinator, V1, SQLite, service/Media3, package/signer/versionCode.
- File mới tại app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/, test tại app/src/test/java/com/bsdung/musicassistantcar/data/telegram/. Thuần JVM, không Android/IO/đồng hồ trong model.
- Chỉ thêm NAMED_V1_ALIAS cạnh alias V2 và getter session mới trong TelegramPlaylistRuntime; không đổi đường cũ.
- Gate kế hoạch mục 6: log ra docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/, đếm XML; baseline 2445/21/0/0 mỗi biến thể; không @Ignore, không sửa assertion cũ.
- Không ADB/Sony/Tiny/Drive ở chặng này. Màn Sony giữ OFF, ghi SONY_SCREEN_END=KHÔNG THAO TÁC.
- Gặp điều kiện dừng ở kế hoạch mục 8 → ghi BLOCKED vào bàn giao, dừng.

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, *-ban-giao.md, kế hoạch TASK_ID. Kiểm git status, branch, HEAD, baseline/plan commit, git diff --check, agent/build đang chạy. Tree dirty không rõ nguồn hoặc baseline không khớp → STOP; không reset/clean/stash/đè.
2. Làm S1→S4 đúng kế hoạch mục 5 (bảng Chặng 1), commit theo mốc từng bước. Viết test đủ cột "Xong khi". Chạy gate mục 6 một lần sau S4 (S5), log ra file, chỉ ghi tổng kết, test defined/pass/skipped/fail thực và diff --check. Sau mỗi mốc cập nhật docs/plans/2026-11-01-nhieu-playlist-co-ten-bao-cao.md (mở đầu "Thực thi: Claude Code <model> <effort nếu biết>, phiên Chặng 1") và *-ban-giao.md: bước xong/dở, file/commit, bằng chứng, lỗi/giới hạn, việc tiếp.
3. Báo cáo ≤ ~250 dòng, bảng trước. Không đoán model/chứng cứ.
4. Commit code, báo cáo, bàn giao trên dot/2026-11-01-nhieu-playlist-co-ten. Không merge/push/public. Viết prompt đầy đủ cho phiên thực thi Chặng 2 (S6–S10) vào *-ban-giao.md theo cùng mẫu này.

Kết thúc tiếng Việt dễ hiểu ba đoạn: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng, bàn giao phiên thực thi tiếp theo." kèm đường dẫn bàn giao và prompt Chặng 2. KHÔNG tự đề xuất rồi làm việc kế tiếp.
```
