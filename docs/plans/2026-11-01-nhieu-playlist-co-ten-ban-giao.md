# Bàn giao — Nhiều Playlist có tên

TASK_ID: **2026-11-01-nhieu-playlist-co-ten**.

## Checkpoint 01/11/2026 — Chặng 1 (S1–S5) xong (Claude Code, Sonnet 5.5)

- **Vai:** thực thi Chặng 1 (code + test Ni). Không ADB/Sony/Tiny/Drive; `SONY_SCREEN_END=KHÔNG THAO TÁC`. Phiên đã dừng, không còn tác vụ chạy.
- **Worktree/nhánh:** `D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten`, `dot/2026-11-01-nhieu-playlist-co-ten`. Baseline `57c1be6`, commit kế hoạch `b0cfeac`. HEAD hiện tại: `git rev-parse HEAD` (commit hồ sơ Chặng 1 nằm sau mã `3336154`).
- **Báo cáo:** [2026-11-01-nhieu-playlist-co-ten-bao-cao.md](2026-11-01-nhieu-playlist-co-ten-bao-cao.md).

| Chặng / bước | Trạng thái | Commit | Bằng chứng | Việc còn lại |
|---|---|---|---|---|
| S1 `FractionalIndex` | Xong | `fa6e858` | 10 test pass | — |
| S2 `NamedPlaylistModel` | Xong | `eeb22c8` | 39 test pass | — |
| S3 `NamedPlaylistCodec` + `NamedPlaylistStore` + `NAMED_V1_ALIAS` | Xong | `e0d70c6` | 27 test pass | — |
| S4 `NamedPlaylistSession` + `TelegramPlaylistRuntime.namedSession` | Xong | `3336154` | 18 test pass | — |
| S5 gate Ni | Xong | — | Debug và Release mỗi biến thể: 190 suite, 2539 test, 21 skipped, 0 failures, 0 errors (baseline 186/2445/21/0/0; +94 đúng phần mới); assembleShareableDebug+Release BUILD SUCCESSFUL; `git diff --check` sạch | — |
| Chặng 2 S6–S10 | Chưa làm | — | — | Phiên thực thi 2 |
| Chặng 3 S11–S19 | Chưa làm | — | — | Phiên thực thi 3 |
| Chặng 4 S20–S24 | Chưa làm | — | — | Phiên thực thi 4 |

- **File chưa commit:** không có sau commit hồ sơ (log gate nằm ở `docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/`, bị git bỏ qua). Repo chính vẫn có `?? .codex-remote-attachments/` và kế hoạch UI/UX chưa commit của phiên khác, không động tới.
- **File đã chạm ngoài file mới:** `TelegramSavedPlaylistStore.kt` (+3 dòng, `NAMED_V1_ALIAS`), `TelegramPlaylistSession.kt` (+8 dòng, `namedSession` trong runtime). Không đụng class/định dạng Yêu thích, `#mvsync`, SQLite, service/Media3, package/signer/versionCode.
- **Lỗi/giới hạn còn mở:**
  1. Cờ `NAMED_ENABLED` (kế hoạch mục 8) chưa có vì phải nằm trong `TelegramPlaylistV2Session.kt`, file bị cấm. Chặng 3 (hoặc chủ dự án) chốt chỗ đặt.
  2. Chưa chạy Keystore thật với alias mới; chỉ cipher giả trong JVM.
  3. Còn hai chỗ chủ dự án cần quyết định từ kế hoạch (4.5 công tắc chung, mục 7 QA hai máy) — chưa ảnh hưởng Chặng 2.
- **Bước tiếp:** chủ dự án mở phiên thực thi mới cho **Chặng 2 (S6–S10)** bằng prompt dưới.

### Prompt cho phiên thực thi Chặng 2

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten (repo chuẩn D:\Bots\MeloVault-E91-Shareable — KHÔNG làm ở repo chính, nó đang ở nhánh tài liệu khác và có file chưa commit của phiên khác)
TASK_ID: 2026-11-01-nhieu-playlist-co-ten
Nhánh: dot/2026-11-01-nhieu-playlist-co-ten
Kế hoạch Claude đã được chủ dự án CHỐT: docs/plans/2026-11-01-nhieu-playlist-co-ten.md
Bàn giao: docs/plans/2026-11-01-nhieu-playlist-co-ten-ban-giao.md; báo cáo Chặng 1: docs/plans/2026-11-01-nhieu-playlist-co-ten-bao-cao.md
HEAD dự kiến: commit hồ sơ Chặng 1 (git log -1 --format=%H -- docs/plans/2026-11-01-nhieu-playlist-co-ten-ban-giao.md); mã Chặng 1 kết thúc ở 3336154
Model: do chủ dự án chọn; bước tiếp tục: Chặng 2, S6 → S10
Baseline: 57c1be64436c3c364bcd8c1a5f8a8193ea52a553, commit kế hoạch: b0cfeac138e3cb0edbb4857f650795c79bd78957; Chặng 1 xong tại mã S4 3336154.
PHIÊN: 2 = code + test Ni, CHỈ Chặng 2 (S6–S10). Không làm Chặng 1 lại, không làm Chặng 3/4.
Lưu ý riêng:
- Chặng 1 đã có (đọc trước khi viết): FractionalIndex.kt, NamedPlaylistModel.kt (NamedOp, NamedPlaylistState, ListId, NamedOpType, Reg, NamedPlaylistNames), NamedPlaylistCodec.kt (encodeName/decodeName base64url KHÔNG padding, dùng lại cho tin #mvlist), NamedPlaylistStore.kt, NamedPlaylistSession.kt (NamedPlaylistSyncHost { view(account): NamedSyncView?; update(account, epoch, transform): NamedUpdateResult }), TelegramPlaylistRuntime.namedSession(noBackupDir). Test tương ứng ở app/src/test/.../data/telegram/.
- Hợp đồng model cần nhớ: applyRemote(op) idempotent, KHÔNG đụng outbox (coordinator tự acknowledge op của mình); NamedOp tự kiểm (tên 1–60 điểm mã, pos hợp lệ ≤ 48, dots ≤ 32, LCREATE phải có dot = opId = listId) và ném IllegalArgumentException — decode tin phải bắt thành Invalid; localItems không bao giờ vào op/outbox; checkpoint nằm trong NamedPlaylistState.checkpoint (SyncCheckpoint dùng lại, replayPosition như Yêu thích); thanh ghi tên/pos LWW theo (seq, opId).
- Chủ dự án đã duyệt tin #mvlist v1 (kế hoạch mục 4.4). KHÔNG sửa TelegramPlaylistModel/Codec/StoreV2/V2Session/SyncProtocol/SyncCoordinator, V1, SQLite, service/Media3, package/signer/versionCode. Class Yêu thích chỉ được đọc để bắt chước hợp đồng; helper thuần dùng chung chỉ khi giữ nguyên byte hành vi cũ và toàn bộ test cũ pass không đổi assertion (kế hoạch S7).
- Chỉ được sửa AutomaticPlaylistSync.run / SyncActionsPort / SyncServiceProvider như S8 mô tả; mọi test cũ giữ nguyên assertion.
- Gate kế hoạch mục 6: log ra docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/, đếm XML. Baseline cho Chặng 2 = kết quả Chặng 1 (xem báo cáo Chặng 1: số test/skipped/fail mỗi biến thể); không @Ignore, không sửa assertion cũ.
- Cờ NAMED_ENABLED (kế hoạch mục 8) CHƯA có: nó phải nằm cạnh V2_ENABLED trong TelegramPlaylistV2Session.kt, file mà Chặng 1 cấm sửa. Chặng 2 vẫn không sửa file đó; nếu cần cờ cho S8, tạo object cờ riêng trong file mới và ghi quyết định vào báo cáo để chủ dự án/Chặng 3 chốt chỗ đặt.
- Không ADB/Sony/Tiny/Drive ở chặng này. Màn Sony giữ OFF, ghi SONY_SCREEN_END=KHÔNG THAO TÁC.
- Gặp điều kiện dừng ở kế hoạch mục 8 → ghi BLOCKED vào bàn giao, dừng.

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, *-ban-giao.md, *-bao-cao.md, kế hoạch TASK_ID. Kiểm git status, branch, HEAD, plan commit, git diff --check, agent/build đang chạy. Tree dirty không rõ nguồn hoặc HEAD không khớp bàn giao → STOP; không reset/clean/stash/đè.
2. Làm S6→S9 đúng kế hoạch mục 5 (bảng Chặng 2), commit theo mốc từng bước. Viết test đủ cột "Xong khi". Chạy gate mục 6 một lần sau S9 (S10), log ra file, chỉ ghi tổng kết, test defined/pass/skipped/fail thực và diff --check. Sau mỗi mốc cập nhật báo cáo (mục "Chặng 2", mở đầu "Thực thi: Claude Code <model> <effort nếu biết>, phiên Chặng 2") và *-ban-giao.md: bước xong/dở, file/commit, bằng chứng, lỗi/giới hạn, việc tiếp.
3. Báo cáo ≤ ~250 dòng, bảng trước (nếu vượt, tách phụ lục .md). Không đoán model/chứng cứ.
4. Commit code, báo cáo, bàn giao trên dot/2026-11-01-nhieu-playlist-co-ten. Không merge/push/public. Viết prompt đầy đủ cho phiên thực thi Chặng 3 (S11–S19) vào *-ban-giao.md theo cùng mẫu này.

Kết thúc tiếng Việt dễ hiểu ba đoạn: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng, bàn giao phiên thực thi tiếp theo." kèm đường dẫn bàn giao và prompt Chặng 3. KHÔNG tự đề xuất rồi làm việc kế tiếp.
```

---

## (Lịch sử)

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
