# E96-P2-PLAYLIST-SYNC-CORE — BÁO CÁO THỰC THI (Sonnet 5.5)

**Kết luận đề xuất: READY FOR CHATGPT QA (cổng mã nguồn + JVM với kênh giả).** Không phải production PASS. Không merge, không P3, không tạo kênh/gửi tin Telegram thật, không đụng thiết bị.
**APK: `QA BUILD PASS / DEVICE INSTALL BLOCKED`** (mục 9). Đồng bộ chạy thật chưa tồn tại: sync core chỉ được gọi từ test, cờ runtime `SYNC_ENABLED=false`, Activity không gọi nó.

## 1. Git

| Mục | Giá trị |
|---|---|
| Kế thừa | P1 PASS `dot/E96-P1-PLAYLIST-V2-LOCAL` @ `7c20fd1ad4d04b30d68c39694677afa73ed8f086` (đúng dự kiến; cây sạch; không agent/tiến trình lạ; có REWORK `52a8828`) |
| Nhánh / worktree P2 | `dot/E96-P2-PLAYLIST-SYNC-CORE` — `D:\Bots\MeloVault-E96-P2-Sync-Core` |
| **Commit mã nguồn** | **`d3b95cd32eca012cae3866b0e98fe9f443139117`** (APK và test chạy trên đúng cây này) |
| Commit báo cáo | liền sau, chỉ thêm file này |
| Không làm | merge, push, reset, clean, stash, force push; không sửa worktree P0/P1/E95 |

## 2. Diff từng file (so với `7c20fd1`; 7 file, +1847/−1)

| File | Dòng | Vai trò |
|---|---|---|
| `TelegramPlaylistSyncProtocol.kt` (mới, shareable) | +144 | định dạng tin `#mvsync v1`, mã hóa/giải mã chặt, chia lô |
| `TelegramPlaylistSyncTransport.kt` (mới) | +78 | `PlaylistSyncTransport` (send/fetchAfter) không biết TDLib, kiểu kết quả/lỗi, `SyncBackoff` |
| `TelegramPlaylistSyncCoordinator.kt` (mới) | +323 | `PlaylistSyncHost`, `PlaylistSyncCoordinator` (máy trạng thái), báo cáo `SyncReport`, `applyRemoteBatch` |
| `TelegramPlaylistV2Session.kt` (sửa) | +52/−1 | phiên V2 cài `PlaylistSyncHost` (`view`/`update` + `epoch`) và cờ `SYNC_ENABLED=false`; toggle/migration/reconcile không đổi |
| `SyncTestKit.kt` (mới, test) | +227 | `FakeTelegramChannel`, `FakeSyncDevice`, `SimulatedCrash`, `SyncFaultyIo` |
| `TelegramPlaylistSyncProtocolTest.kt` (mới, test) | +193 | 18 test |
| `TelegramPlaylistSyncCoordinatorTest.kt` (mới, test) | +830 | 47 test |

Không đổi: Activity, UI/Car Mode, playback/Media3, TDLib, SQLite schema, Gradle, signing, versionCode, mã P0/P1 (ngoài chỗ cài host ở trên). Không có file nào ngoài phạm vi.

## 3. Kiến trúc

```
 (tương lai P3)  TDLib adapter ──implements──►  PlaylistSyncTransport  ◄──implements──  FakeTelegramChannel (test)
                                                      ▲
 PlaylistSyncCoordinator.runOnce(account, mode) ──────┘        (một lần chạy, tuần tự, không thread/sleep/TDLib)
        │  view(account) / update(account, epoch, transform)
        ▼
 PlaylistSyncHost  ◄── TelegramPlaylistV2Session (người ghi duy nhất, khóa phiên, epoch tăng khi open/close)
        └─ V2 store: MỖI update = 1 lần ghi nguyên tử (trạng thái + outbox + checkpoint) rồi mới "Committed"
```

**Giao thức (P2-01).** `#mvsync v1` / `scope` (vân tay 16 hex của tài khoản, không phải số tài khoản) / `sender` / `batch` / 1..20 dòng `op` (opId, ADD|REMOVE, `tg:chat:msg`, seq, dots) / `sum` = SHA-256 của mọi dòng trước. **`sum` chỉ là checksum chống hỏng, KHÔNG phải chữ ký**; ai đăng được vào kênh đều viết được tin hợp lệ (có test ghi nhận). Mã hóa xác định (cùng op → cùng chữ, nên gửi lại là tin y hệt). Giải mã chặt: phiên bản tương lai (`FUTURE_VERSION`), sai định dạng, sai checksum, sai tài khoản (kiểm sau checksum), quá cỡ (>4096), >20 op, >32 tag, trùng opId, item không phải bài Telegram (`legacy:`/`local:`/`tg:0:…`), opId/tag mới không thuộc người gửi. Không có số điện thoại, phiên, hash API, token, đường dẫn, byte âm thanh. Chữ **không** bắt đầu bằng `#mvsync` là `Foreign` (được bỏ qua, cursor đi qua); bắt đầu bằng `#mvsync` mà không hợp lệ là `Invalid` (dừng).

**Trạng thái một lần chạy (P2-02).**
```
runOnce ─ chưa đến giờ backoff? → BACKOFF_ACTIVE (không gọi transport)
        ─ phiên không mở / tài khoản khác → NOT_OPEN ; chưa có kênh → NO_CHANNEL (không bao giờ tạo kênh)
        ─ FETCH (lặp theo trang): đọc trang → sắp xếp, bỏ lặp, bỏ id ≤ vị trí → giải mã từng tin theo id
              Foreign → bỏ qua, cursor đi qua | Ok → gom op; op của CHÍNH MÌNH trùng outbox → gom ACK
              Invalid → DỪNG tại đó
              ghi MỘT lần: áp op + ACK + cursor (cho phần tốt TRƯỚC tin hỏng) ; lỗi ghi → SAVE_FAILED, không gì đổi
              có tin hỏng → RECOVERY_REQUIRED (cursor đứng ngay trước nó; KHÔNG gửi)
        ─ SEND (≤ maxEnvelopesPerRun): lấy op chờ (PENDING/RETRY_REQUIRED) theo thứ tự → 1 tin → send
              Sent(serverId) → ACK (ghi bền) | Unknown/timeout/lỗi tạm → RETRY_REQUIRED(attempts+1), BACKOFF
              lỗi vĩnh viễn → RECOVERY_REQUIRED, không đổi gì
        ─ PRUNE: chỉ khi có ACKNOWLEDGED, ghi riêng, chỉ bỏ ACKNOWLEDGED
        ─ OK
```
- **Điều kiện ACK:** (a) câu trả lời của server cho lần gửi (`Sent(serverMessageId)`), hoặc (b) tìm lại đúng opId (nội dung giống hệt) trong lịch sử. Gọi `send` rồi không có trả lời **không** là ACK. FETCH chạy trước SEND nên op server đã có được ACK thay vì gửi lại.
- **Điều kiện tiến cursor:** chỉ sau khi một lần ghi V2 chứa phần dữ liệu tương ứng thành công; cursor luôn đơn điệu (`max`); không bao giờ vượt tin `Invalid`; vượt tin `Foreign` là có chủ ý.
- **Chống callback cũ:** mỗi bước kiểm lại `view().epoch` cùng tài khoản; `update` từ chối (`Stale`) khi phiên đã đóng/đổi, kể cả A→B→A.
- **Backoff:** lỗi mạng/timeout 5 s, 10 s, 20 s… tối đa 300 s; `FLOOD_WAIT` theo đúng số giây server cho; sau thành công quên hết; gọi sớm → không có cuộc gọi nào. Đồng hồ chỉ quyết định *khi nào* thử lại, không quyết định nội dung playlist.
- **Phục hồi bộ đếm:** nếu op của chính thiết bị này quay về với bộ đếm cao hơn bộ đếm đang lưu (file cũ được khôi phục), bộ đếm được nâng để không cấp lại cùng một tag.

**P2-03 các quyết định.** Snapshot: **NOT IMPLEMENTED** (không chứng minh được an toàn áp dụng); log không bị cắt, tombstone không bị dọn theo TTL/5.000 op; thiết bị mới dựng lại đúng từ toàn bộ log (test). Tin trễ có id thấp hơn cursor chỉ được `FULL_REPLAY` nhặt lại (lịch gọi `FULL_REPLAY` là việc của P3).

## 4. Kiểm thử (JUnit XML đọc lại sau `--rerun-tasks`)

Lệnh trên commit `d3b95cd`: `.\gradlew.bat --no-daemon :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --rerun-tasks --console=plain` → **BUILD SUCCESSFUL**, `59 actionable tasks: 59 executed` (không UP-TO-DATE).

| Biến thể | Tests | Passed | **Skipped** | Failed/Errors |
|---|---|---|---|---|
| Debug | 1.534 | 1.513 | 21 (có sẵn từ P0/E94, không do P2) | 0 / 0 |
| Release | 1.534 | 1.513 | 21 | 0 / 0 |

1.534 = 1.469 (sau P1 rework) + **65 mới** (Protocol 18, Coordinator 47). Hồi quy, cả hai biến thể, 0 skipped: **P0 58** (Policy 8, Session 15, ViewPolicy 7, SavedPlaylistStore 23, SongsByKeysSql 5) và **P1 81** (Model 25, StoreV2 21, V2Session 35) — đều xanh, không hạ assertion, không đánh skip.

Ma trận 30 ca (tên test trong `TelegramPlaylistSyncCoordinatorTest`, trừ ghi chú):

| # | Ca | Test | KQ |
|---|---|---|---|
| 1 | A lưu X → B thấy | `t01` | PASS |
| 2 | B xóa X → A không còn | `t02` | PASS |
| 3 | Offline thêm X/Y → hội tụ | `t03` | PASS |
| 4 | Cùng X hai máy → một dòng | `t04` | PASS |
| 5 | ADD đồng thời REMOVE cũ → add-wins | `t05` | PASS |
| 6 | REMOVE quan sát hết → biến mất | `t06` | PASS |
| 7 | REMOVE đến trước ADD | `t07` | PASS |
| 8 | Trùng/replay >3 lần | `t08` (replay 5×, mọi trang lặp) | PASS |
| 9 | Mất ACK sau khi server nhận, gửi lại cùng opId | `t09`, `t09b` (bản sao thứ hai y hệt vẫn một bài) | PASS |
| 10 | Chết trước commit V2 cục bộ | `t10` | PASS |
| 11 | Chết sau commit, trước gửi | `t11` | PASS |
| 12 | Chết sau gửi, trước ACK | `t12` (không gửi lần hai) | PASS |
| 13 | Chết sau apply, trước cursor | `t13` (apply+cursor là MỘT lần ghi) | PASS |
| 14 | Sau commit cursor / restart | `t14` | PASS |
| 15 | >250 sự kiện, phân trang | `t15` (270 tin, 3 trang), `t15b` | PASS |
| 16 | Đảo thứ tự / không liên tục / trễ | `t16`, `t16b` (trễ → FULL_REPLAY), `t16c`, `t16d` | PASS |
| 17 | Sai tài khoản | `t17`, `t17b` | PASS |
| 18 | Logout A→B→A, callback cũ bị từ chối | `t18`, `t18b`, `t18c` | PASS |
| 19 | Hai chat cùng rawMessageId | `t19` | PASS |
| 20 | Legacy chưa xác minh không bao giờ lên kênh | `t20`, `t36` | PASS |
| 21 | Local-only không vào outbox | `t21` + protocol test (`local:`/`legacy:` bị từ chối) | PASS |
| 22 | Hỏng/tương lai/checksum sai → không nuốt, không tăng cursor | `t22` (4 kiểu), `t22b`, `t22c` | PASS |
| 23 | Kênh bị xóa/không quyền → không tạo lại | `t23` (deleted/unauthorized/revoked) | PASS |
| 24 | Mạng chập chờn, timeout, 429, không busy loop | `t24`, `t24b` | PASS |
| 25 | Outbox đầy không mất op | `t25` (5.000 op, 100 đi, 4.900 nguyên thứ tự) | PASS |
| 26 | ACK/prune không xóa pending | `t26` | PASS |
| 27 | Offline rất lâu vẫn hội tụ | `t27` (90 ngày giả lập + 30 thay đổi) | PASS |
| 28 | Snapshot cũ không hồi sinh bài | snapshot **NOT IMPLEMENTED**; thay bằng `t28` (thiết bị mới dựng lại đúng từ log, tombstone còn, log không bị cắt) | NOT IMPLEMENTED / thay thế PASS |
| 29 | Replay/hoán vị/property | `t29` (12 seed × 3 máy chập chờn × 70 bước, hội tụ + bằng fold của log), cùng `TelegramPlaylistModelTest` của P1 | PASS |
| 30 | So sánh chính xác id, adds, tombstones, outbox, cursor, cô lập tài khoản | `t30`, `t17b`, `t29` | PASS |
| + | bộ đếm sau khi file cũ được khôi phục | `t31` | PASS |
| + | ghi ACK lỗi, lịch sử sửa lại, không gửi lại | `t32` | PASS |
| + | op quá lớn bị giữ, báo lỗi | `t33` | PASS |
| + | không kênh / phiên không mở | `t34`, `t35` | PASS |

**Độ nhạy của test (đột biến tạm thời, đã khôi phục, `cmp` khớp):** (1) coi gửi mất trả lời là ACK, (2) cho cursor đi qua tin hỏng, (3) bỏ kiểm `epoch`, (4) `pruneAcknowledged` xóa hết → 9/47 test đỏ (`t09,t09b,t24` / `t22,t22b,t17` / `t18c` / `t25,t26`), log `02-mutation-sanity-check.log`.

Giới hạn của kiểm thử: kênh, thiết bị, mất điện đều là mô phỏng; "chết" = ném `Error` (không bị bắt) hoặc lỗi ghi file; mã hóa Keystore thật, TDLib thật, đồng hồ thật: chưa chạy.

## 5. Build và APK — `QA BUILD PASS / DEVICE INSTALL BLOCKED`

`.\gradlew.bat --no-daemon :app:assembleShareableDebug :app:assembleShareableRelease` → **BUILD SUCCESSFUL**; `git diff --check` sạch.

| Mục | Debug (bàn giao) |
|---|---|
| Đường dẫn APK QA | `D:\Bots\_handoff\MeloVault\E96-CROSS-DEVICE-SYNC\QA_APK_E96_P2\MeloVault-E96-P2-PLAYLIST-SYNC-CORE-d3b95cd-shareable-debug-vc109-NiSigned-NoTelegramConfig-QA-CANDIDATE.apk` (+ `.sha256`) |
| SHA-256 (đọc lại từ bản sao QA = file build) | `d64562509ff5ea5a6834846b3ecfc099cb757bca1e39ef8aa24b698c0f6bbf16` |
| Kích thước | 33.214.500 byte |
| Package / version | `app.melovault.player` / vc **109** / `0.6.88-beta` (không bump) |
| Chứng chỉ ký SHA-256 | `a9b8cb6b5b0bc0400955b24dbc85801f5e8855356287caca2dcf3bb4e13db280` |
| Telegram config | **false** (`TELEGRAM_APP_API_ID=0`) · ABI chỉ `arm64-v8a` |
| Nguồn | `d3b95cd` — **có** REWORK `52a8828` (đã kiểm: chuỗi `IMPORT_DEFERRED_OUTBOX_FULL` có trong dex); **không** phải APK cũ `2dc45a3` |
| Release (không bàn giao) | SHA-256 `d90c1f87dd54ab1024716c56c54c08f50d48e374c1b6af0c6774e4f4e0700717`, 30.505.518 byte, cùng chứng chỉ |

Không cài được/không dùng được để thử Telegram (cùng lý do P0/P1): chữ ký Ni ≠ chữ ký Preview `eecd3df7…` → cài đè sẽ signature mismatch; thiếu Telegram config; versionCode 109 trùng Beta109; AVD sẵn là `x86_64`. Không sửa signing/versionCode/secrets/ABI, không cài, không `uninstall`/`pm clear`, không upload Drive. Lưu ý: APK **không có hành vi mới nào chạy được** vì sync core không được gọi từ ứng dụng.

## 6. Rủi ro và giới hạn đã biết

1. **Chưa chứng minh với TDLib thật.** Hợp đồng P3 phải giữ: `send` chỉ trả `Sent` sau khi server xác nhận (TDLib: `updateMessageSendSucceeded`, không phải tin tạm); `fetchAfter` phải trả đầy đủ id > mốc và `hasMore` đúng; id tin của kênh riêng đơn điệu. Giới hạn tốc độ, độ dài tin có định dạng, hành vi khi người dùng tự xóa/sửa tin trong kênh: chưa biết.
2. **Chính sách `Foreign`:** tin không có marker bị bỏ qua và cursor đi qua. Nếu dòng đầu của một tin sync bị hỏng đến mức mất marker, tin đó (và op bên trong) bị coi là chữ thường và mất theo; chỉ tin có marker mà hỏng mới dừng cursor.
3. **Tin trễ dưới cursor** chỉ được nhặt bằng `FULL_REPLAY`; chưa có lịch tự động (P3), và `FULL_REPLAY` tốn bằng cả lịch sử.
4. **`sum` không phải chữ ký:** người có quyền đăng vào kênh có thể chèn op hợp lệ. Mô hình đe dọa dựa vào việc chỉ tài khoản chủ đọc/ghi kênh riêng tư.
5. **Snapshot chưa có:** log chỉ dài thêm, tombstone không dọn → file V2 và thời gian `FULL_REPLAY` tăng theo thời gian; chưa có điểm an toàn để cắt (cần biết mọi thiết bị).
6. **Chặn đầu hàng đợi:** một op không thể mã hóa (REMOVE quan sát >32 tag) giữ nguyên hàng đợi phía sau nó ở `RECOVERY_REQUIRED/OP_TOO_LARGE`. Cần hơn 32 thiết bị cùng thêm một bài nên rất khó gặp.
7. Hiệu năng: mỗi `update` so sánh sâu trạng thái và ghi lại cả file V2 (kể cả khi outbox 5.000); mỗi tin gửi/ACK là một lần ghi. Chưa đo trên thiết bị.
8. `RECOVERY_REQUIRED` cũng đặt backoff (5 s → 300 s) để không gõ cửa liên tục; người dùng bấm "thử lại" cần `resetBackoff()`.
9. Mã sync core nằm trong APK nhưng chưa được tham chiếu từ ứng dụng.

## 7. Rollback

Chưa nối vào ứng dụng nên không có hành vi runtime để rollback: `git revert d3b95cd` (bỏ 7 file) hoặc bỏ nhánh. Cờ `SYNC_ENABLED=false` đã là mặc định. Không đổi định dạng file/khóa/schema; file V2 của P1 đọc/ghi như cũ (thêm giá trị `checkpoint` chỉ khi test gắn kênh).

## 8. Đường dẫn log và artifacts

`D:\Bots\MeloVault-E96-P2-Sync-Core\docs\plans\artifacts\E96-P2-PLAYLIST-SYNC-CORE\` (Git bỏ qua): `01-new-p2-tests-first-run.log`, `02-mutation-sanity-check.log`, `03-full-unit-tests-rerun-debug-release.log`, `04-assemble-debug-release.log`, `05-playlist-class-results.txt`. APK QA: `…\E96-CROSS-DEVICE-SYNC\QA_APK_E96_P2\`.

## 9. Việc chuẩn bị cho P3 (không làm trong P2)

Adapter TDLib hiện thực `PlaylistSyncTransport` (qua `ShareableTdlibOwner`, không tạo client mới, `bindSync` không giữ client sống); khám phá/tạo kênh với consent riêng và ghi `checkpoint.channelId`; lịch gọi `runOnce` (khởi động, resume, kết nối lại, sau Save, gom 3 s) và `FULL_REPLAY`; hiển thị trạng thái (đang chờ/đã đồng bộ/cần khôi phục); quyết định về snapshot và điểm cắt log; thử nghiệm thiết bị thật là cổng riêng.

## 10. Dọn dẹp tài nguyên

- Mọi lệnh Gradle chạy với `--no-daemon`; kiểm lại `gradlew --status` không còn daemon nào. Không emulator, không ADB, không bật màn hình thiết bị (SONY_SCREEN_END/SHANLING_SCREEN_END: không áp dụng).
- Bản sao lưu 3 file dùng cho kiểm tra đột biến (thư mục scratchpad của phiên) đã xóa sau khi khôi phục và `cmp` khớp. Dữ liệu test: `TemporaryFolder` của JUnit (tự xóa) và bộ nhớ. Log tạm của shell đã sao vào thư mục artifacts rồi xóa bản tạm.
- Giữ nguyên: báo cáo, log, APK QA của P0/P1/P2 (không xóa/ghi đè), các worktree và nhánh. Không đụng tiến trình Agent khác; không `reset --hard`/`clean`/`stash`/force push; không đọc/copy secrets hay `local.properties`; không Telegram thật, không backend/Private Stable.

Đã dừng, chờ ChatGPT nghiệm thu độc lập.
