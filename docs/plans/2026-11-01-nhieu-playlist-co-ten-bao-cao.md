# Báo cáo thực thi — Nhiều Playlist có tên

TASK_ID: `2026-11-01-nhieu-playlist-co-ten`. Nhánh `dot/2026-11-01-nhieu-playlist-co-ten`, worktree `D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten`.

Thực thi: Claude Code Sonnet 5.5 (effort không xác định từ công cụ), phiên Chặng 1.
Phạm vi phiên: **Chặng 1 (S1–S5)** — mô hình, lưu trữ, session. Không UI, không đồng bộ, không build APK, không ADB/Sony/Tiny/Drive.
Trạng thái: **CHẶNG 1 XONG, chờ phiên thực thi Chặng 2.** Chưa có APK, chưa nghiệm thu.

## 1. Bảng kết quả

| Bước | Việc | Commit | File chính | Test mới | Kết quả |
|---|---|---|---|---|---|
| S1 | Khóa thứ tự phân số base-62 | `fa6e858` | `FractionalIndex.kt` | 10 | PASS |
| S2 | Mô hình CRDT (Playlist, tên, bài, thứ tự, bài trên máy) | `eeb22c8` | `NamedPlaylistModel.kt` | 39 | PASS |
| S3 | Định dạng `MVNL1` + file mã hóa `tg_named_playlists_v1/` | `e0d70c6` | `NamedPlaylistCodec.kt`, `NamedPlaylistStore.kt`, alias `NAMED_V1_ALIAS` | 27 | PASS |
| S4 | Session một bên ghi + cửa đồng bộ + getter runtime | `3336154` | `NamedPlaylistSession.kt`, `TelegramPlaylistRuntime.namedSession` | 18 | PASS |
| S5 | Gate Ni | (commit hồ sơ) | log trong `docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/` | — | PASS |

Tổng test mới: **94** (10 + 39 + 27 + 18), 4 suite mới.

## 2. Gate Ni (S5) — chạy trên HEAD `3336154` (code), một lần

| Mục | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL (3 phút 13 giây) |
| `:app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL (2 phút 37 giây) |
| `git diff --check` | sạch (exit 0) |

Số liệu đếm từ XML `app/build/test-results/`:

| Biến thể | Suite | Test | Skipped | Failures | Errors |
|---|---|---|---|---|---|
| Debug | 190 | 2539 | 21 | 0 | 0 |
| Release | 190 | 2539 | 21 | 0 | 0 |
| Baseline (HANDOFF 01/11) | 186 | 2445 | 21 | 0 | 0 |

- 2539 = 2445 + 94: đúng phần thêm; số skipped không đổi (21, đều có sẵn từ trước).
- Không thêm `@Ignore`; không sửa assertion cũ (không file test cũ nào bị đổi).
- Log: `gate-s5-tests.log`, `gate-s5-assemble.log`, `gate-s5-status.txt` (thư mục artifacts, bị git bỏ qua). Chỉ đọc dòng tổng kết.
- Trong lúc phát triển có chạy lọc `--tests` từng bước (`dev-s1..s4.log`) để bắt lỗi biên dịch; gate đầy đủ chạy đúng một lần sau S4.

## 3. Đã làm — chi tiết theo bước

**S1 `FractionalIndex`** (`between`, `isValid`, `MAX_LEN = 48`, thêm `evenly(count)` để dàn đều khi cần):
- Test: luôn `a < kết quả < b`; 10 000 lần chèn ngẫu nhiên giữ thứ tự và không trùng; chèn đầu/cuối 300 lần; không bao giờ trả chuỗi rỗng hay kết thúc bằng '0'; chèn dồn một chỗ làm khóa dài quá 48 và `evenly` dàn lại; đối số sai bị từ chối.

**S2 `NamedPlaylistModel`** (thuần JVM; có test canh nguồn: không `android`, đồng hồ, `java.io`, ngẫu nhiên, UUID trong file model):
- `ListId` (`<deviceId>.<counter>`), `NamedOp` (6 loại `LCREATE/LRENAME/LDELETE/IADD/IREMOVE/IMOVE`, tự kiểm hình dạng), thanh ghi `Reg` LWW theo `(seq, opId)`, `NamedPlaylistState` bất biến.
- Các hàm đúng kế hoạch: `createList`, `renameList`, `deleteList`, `addTelegram`, `removeTelegram`, `moveItem`, `addLocal`, `removeLocal`, `moveLocal`, `rebalance`, `applyRemote`, `acknowledge`, `markRetryRequired`, `revertRefusedDispatch`, `pruneAcknowledged`; view `lists()`, `entries(listId)`, thêm `listsContaining(item)` cho dấu ✓ ở hộp “Thêm vào Playlist”.
- Quy tắc: tên 1–60 điểm mã sau trim, không ký tự điều khiển; trùng tên so sánh không dấu/không hoa thường/gộp khoảng trắng (có `đ`); giới hạn 100 Playlist, 1000 mục (Telegram + máy), outbox 5000 (ném `OutboxFullException`, trạng thái cũ giữ nguyên).
- Thứ tự: một thao tác Di chuyển chỉ ghi 1 vị trí; khi hai bên cùng vị trí hoặc khóa quá dài thì dàn lại cả Playlist bằng chuỗi `IMOVE`.
- Bài trên máy chỉ nằm ở `localItems`, đặt xen kẽ theo vị trí, không có op, không vào outbox; Playlist bị xóa (cục bộ hoặc từ xa) thì bỏ bài trên máy của nó.
- Test hội tụ: 2 máy trao đổi; thứ tự đảo/trùng lặp; thêm–bỏ–thêm với bỏ đồng thời; hai máy cùng bỏ; đổi tên đồng thời (tie theo thiết bị); đổi tên không hồi sinh Playlist đã xóa (4 thứ tự); xóa đồng thời với thêm (bài giữ trong dữ liệu nhưng ẩn); di chuyển đồng thời; hai máy cùng thêm cuối rồi chèn giữa; thứ tự tạo trên mọi máy; **40 kịch bản ngẫu nhiên 3 máy** (60 bước mỗi kịch bản) đều hội tụ với xáo trộn/lặp/đảo.

**S3 `NamedPlaylistCodec` + `NamedPlaylistStore`**:
- `MVNL1`, schema 1, dòng có tab, `END<TAB>sha256`; tất định; tên là base64url UTF-8 (không padding, kiểm chuẩn); mọi dòng lạ/trùng/sai là Invalid, không bỏ qua; schema mới hơn → `SCHEMA` (đọc-chỉ, không ghi đè); tài khoản khác → `ACCOUNT`; file V2 đưa vào đọc bằng codec này → Invalid.
- Store: `noBackupFilesDir/tg_named_playlists_v1/<dir>.bin`, alias mới `NAMED_V1_ALIAS`, AAD `nl1:<dir>`, ghi nguyên tử, đọc lại đúng mới thay file; file có mà không đọc được thì không ghi đè và không tạo khóa. Test dùng `TestAesGcmCipher`/`TestPlainFileIo` như `TelegramPlaylistStoreV2Test`, gồm: hỏng 1 byte, mất khóa, Keystore lỗi khi mã hóa, đĩa đầy giữa chừng, blob không đọc lại được, hai tài khoản, file bị chép sang tài khoản khác, AAD của V2 không mở được.

**S4 `NamedPlaylistSession`**:
- `open/close/snapshot/snapshotFor/entries/listsContaining` + các thao tác trả `NamedOutcome` (`Ok(listId, changed)`, `Full(limit)`, `OutboxFull`, `Blocked`, `NotOpen`, `Failed`, `DuplicateName`, `Refused`); `NamedPlaylistSyncHost { view(account): NamedSyncView?; update(account, epoch, transform): NamedUpdateResult }` cùng hợp đồng `PlaylistSyncHost`; `epoch` đổi mỗi lần mở/đóng.
- Mở lần đầu (không có file) không ghi gì, không tạo khóa; ghi lần đầu khi có thay đổi đầu tiên. Thay đổi lưu một lần ghi nguyên tử rồi mới nhận vào bộ nhớ; ghi lỗi thì giữ nguyên cả bộ nhớ lẫn file (bộ đếm op không bị tiêu).
- File không đọc được → `Blocked`, mọi ghi bị từ chối, file giữ nguyên từng byte; lỗi đọc tạm thời được thử lại ở lần ghi kế.
- `update` từ chối `Stale` khi đổi/đóng tài khoản; thêm một chốt không có trong kế hoạch: transform không được đổi `accountId`/`deviceId` (trả `Failed`) — phát hiện nhờ test.
- `TelegramPlaylistRuntime.namedSession(noBackupDir)`: thêm getter, không đổi session cũ.

## 4. File đã chạm (đối chiếu với điều cấm)

| File | Thay đổi |
|---|---|
| 5 file code mới trong `data/telegram/` | `FractionalIndex`, `NamedPlaylistModel`, `NamedPlaylistCodec`, `NamedPlaylistStore`, `NamedPlaylistSession` |
| `TelegramSavedPlaylistStore.kt` | +3 dòng: hằng `NAMED_V1_ALIAS` cạnh `V2_ALIAS` |
| `TelegramPlaylistSession.kt` | +8 dòng: `namedSession(...)` trong `TelegramPlaylistRuntime` |
| 4 file test mới | `FractionalIndexTest`, `NamedPlaylistModelTest`, `NamedPlaylistStoreTest`, `NamedPlaylistSessionTest` |

Không sửa: `TelegramPlaylistModel/Codec/StoreV2/V2Session/SyncProtocol/SyncCoordinator`, V1 store (ngoài hằng alias), SQLite, service/Media3, package/signer/versionCode. `git diff --name-only b0cfeac..HEAD` chỉ gồm các file trên + hồ sơ.

## 5. Chưa kiểm chứng / giới hạn

- Chỉ JVM test. Chưa chạy trên Sony/Shanling, chưa có UI, chưa đồng bộ `#mvlist` (Chặng 2), chưa có APK.
- `KeystorePlaylistCipher` thật (Android Keystore) với alias mới chưa chạy trên máy; JVM dùng cipher giả như file V2. Sẽ lộ ra ở QA Sony (Chặng 4).
- Test canh nguồn của model đọc đường dẫn tương đối `src/shareable/...` (thư mục làm việc của Gradle là `app/`); nếu cấu trúc build đổi, test này sẽ fail chứ không bị bỏ qua.
- **Cờ `TelegramPlaylistFlags.NAMED_ENABLED` (kế hoạch mục 8) CHƯA có:** nó phải nằm cạnh `V2_ENABLED` trong `TelegramPlaylistV2Session.kt`, file bị cấm sửa ở Chặng 1. Cần chủ dự án/Chặng 3 chốt chỗ đặt (sửa file đó có ghi nhận, hoặc object cờ riêng ở file mới).
- Playlist trùng tên do đồng bộ vẫn hiện cả hai (đúng kế hoạch); chỉ chặn trùng ở thao tác tạo/đổi tên cục bộ.
- Không đo hiệu năng trên máy thật: model gọi `entries()` (sắp xếp) cho mỗi thao tác, ổn với ≤ 1000 mục trong JVM (test 1000 bài chạy < vài giây); chưa kiểm trên Shanling/Sony (máy này không dùng Shanling nữa).

## 6. Thiết bị / an toàn

`SONY_SCREEN_END=KHÔNG THAO TÁC`. Không ADB, không Tiny, không Drive, không bí mật (`local.properties` không đọc). Repo chính `D:\Bots\MeloVault-E91-Shareable` không bị động tới.

## 7. Việc tiếp

Chặng 2 (S6–S10): `NamedPlaylistSyncProtocol`, `NamedPlaylistSyncCoordinator`, nối `AutomaticPlaylistSync`, trạng thái/Cài đặt. Prompt đầy đủ ở `2026-11-01-nhieu-playlist-co-ten-ban-giao.md`.
