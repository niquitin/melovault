# Báo cáo thực thi — Nhiều Playlist có tên

TASK_ID: `2026-11-01-nhieu-playlist-co-ten`. Nhánh `dot/2026-11-01-nhieu-playlist-co-ten`, worktree `D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten`.

Thực thi: Claude Code Sonnet 5.5 (effort không xác định từ công cụ), phiên Chặng 1.
Phạm vi phiên: **Chặng 1 (S1–S5)** — mô hình, lưu trữ, session. Không UI, không đồng bộ, không build APK, không ADB/Sony/Tiny/Drive.
Trạng thái: **CHẶNG 1, 2 VÀ 3 XONG, chờ phiên thực thi Chặng 4.** Chưa có APK, chưa nghiệm thu.

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

---

# Chặng 2 (S6–S10) — đồng bộ `#mvlist v1`

Thực thi: Claude Code Sonnet 5.5 (effort không xác định từ công cụ), phiên Chặng 2. Chủ dự án ra lệnh “tiếp tục chặng 2” ngay trong hội thoại của Chặng 1 (cùng agent; Chặng 1 đã dừng, cây sạch, HEAD `d71bc44` khớp bàn giao trước khi làm).
Phạm vi: S6–S10. Không UI, không build APK, không ADB/Sony/Tiny/Drive; `SONY_SCREEN_END=KHÔNG THAO TÁC`.
Trạng thái: **CHẶNG 2 XONG.** Mã dừng ở `2ebf127`. Chưa có APK, chưa nghiệm thu.

## 1. Bảng kết quả

| Bước | Việc | Commit | File chính | Test mới | Kết quả |
|---|---|---|---|---|---|
| S6 | Giao thức `#mvlist v1` | `ea38b30` | `NamedPlaylistSyncProtocol.kt` | 19 | PASS |
| S7 | Coordinator đồng bộ Playlist có tên | `7f8248d` | `NamedPlaylistSyncCoordinator.kt` | 26 | PASS |
| S8 | Nối vào tự đồng bộ (sau Yêu thích), service, cổng | `3994772` | `AutomaticPlaylistSync`, `TdlibPlaylistSyncService`, `SyncServiceProvider`, `SyncSettingsController`, `TdlibPlaylistSyncTransport`, `TdlibHistoryReader` | 13 lịch + 7 TDLib giả + 5 guard | PASS |
| S9 | Trạng thái + “Thay đổi chờ gửi” | `2ebf127` | `NamedPendingReviewPolicy.kt`, `PlaylistSyncStatusPolicy`, `AutomaticPlaylistSync`, `MeloSyncSettingsActivity` | 10 + 3 | PASS |
| S10 | Gate Ni | (commit hồ sơ) | log trong `docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/` | — | PASS |

Test mới Chặng 2: **83** = 19 (`NamedPlaylistSyncProtocolTest`) + 26 (`NamedPlaylistSyncCoordinatorTest`) + 16 (`AutomaticPlaylistSyncNamedTest`, gồm 3 của S9) + 7 (`NamedPlaylistTdlibSyncTest`) + 5 (`NamedPlaylistSyncCallSitesTest`) + 10 (`NamedSyncStatusPolicyTest`); 6 suite mới.

## 2. Gate Ni (S10) — chạy trên HEAD `2ebf127` (cây sạch), một lần

| Mục | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL (2 phút 4 giây) |
| `:app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL (1 phút 25 giây) |
| `git diff --check` | sạch (exit 0) |

| Biến thể | Suite | Test | Skipped | Failures | Errors |
|---|---|---|---|---|---|
| Debug | 196 | 2622 | 21 | 0 | 0 |
| Release | 196 | 2622 | 21 | 0 | 0 |
| Baseline Chặng 2 (= kết quả Chặng 1) | 190 | 2539 | 21 | 0 | 0 |

- 2622 = 2539 + 83: đúng phần mới; skipped không đổi (21, có sẵn). Không `@Ignore`; **không sửa assertion của test cũ** (không file test cũ nào bị đổi; xem mục 5 về một guard nguồn).
- Log: `gate-s10-tests.log`, `gate-s10-assemble.log`, `gate-s10-status.txt`. Trong lúc làm có chạy lọc `--tests` (kể cả một lượt rộng `*Sync* *Tdlib* *Playlist*`: 730 test, 0 lỗi sau khi sửa guard) để bắt lỗi sớm; gate đầy đủ chỉ chạy một lần sau S9.

## 3. Đã làm — chi tiết

**S6 `NamedPlaylistSyncProtocol`** (đúng kế hoạch 4.4): sáu dòng `op` (`LCREATE/LRENAME/LDELETE/IADD/IREMOVE/IMOVE`), một dấu cách giữa trường, tên base64url (không padding), `sum` sha256; dùng lại `TdlibText`, `PlaylistSyncProtocol.scopeOf`, giới hạn và danh sách lỗi (`PlaylistSyncProtocol.Error`).
- Test: mọi loại op vòng tròn; điểm cố định của `TdlibText.normalize`; 20 op tên dài nhất luôn ≤ 3500 ký tự (planner tách nhiều tin, đủ thứ tự); 20 op ngắn vừa một tin; **test chéo hai chiều**: `PlaylistSyncProtocol.decode(tin #mvlist)` = Foreign và `NamedPlaylistSyncProtocol.decode(tin #mvsync)` = Foreign; phiên bản mới → `FUTURE_VERSION`; sai tài khoản chỉ báo sau cùng; sai checksum, cắt, đổi ngẫu nhiên 300 vị trí, ~40 dạng dòng op hỏng, quá 20 op, quá 32 dot, trùng op, sender không khớp → đều Invalid, không bao giờ Ok.

**S7 `NamedPlaylistSyncCoordinator`** (viết riêng theo từng luật của `PlaylistSyncCoordinator`, **không sửa class cũ**, không rút helper chung): fetch (đọc từ 0, FULL_REPLAY có vị trí lưu, chứng minh trang, kiểm đầu trang, dừng trước tin hỏng, op của mình phải về nguyên văn) và send (write-ahead cờ RETRY_REQUIRED trước khi gửi, hoàn tác khi chắc chắn bị từ chối, `UnconfirmedBlocked`, chặn xóa-trước-thêm), `NamedTransmitPlanner`. Kênh = kênh của Yêu thích; lần chạy đầu ghi vào checkpoint riêng, kênh khác về sau → RECOVERY_REQUIRED (không đổi ngầm). Dùng chung `SyncReport/SyncStatus/SyncBackoff/PlaylistSyncTransport`; lối vào là `runNamedOnce` (xem mục 5).
- Test 26: hai máy hội tụ (tạo/đổi tên/thêm/move/xóa), đổi đồng thời, replay từ 0 và tiếp tục qua khởi động lại, trang không chứng minh được, xáo/lặp trang, FLOOD_WAIT 35 s (không gọi lại khi còn chờ), mất ACK (không gửi lại, tìm thấy trong lịch sử mới ACK), crash giữa chừng, không ghi được cờ ⇒ không gửi, ngân sách theo số op, hàng chờ 200 bài ≈ 11 tin (1 tin/lượt), phiên đổi giữa chừng, tin `#mvsync` xen kẽ, Yêu thích chạy trên cùng kênh cho kết quả y hệt đối chứng, 12 kịch bản ngẫu nhiên 3 máy hội tụ.

**S8 nối vào tự đồng bộ:**
- `AutomaticPlaylistSync.run`: sau lượt Yêu thích, cùng scope/epoch, gọi `actions.runNamed(INCREMENTAL, 1 tin)` nếu Yêu thích không ở trạng thái BACKOFF/BACKOFF_ACTIVE/RECOVERY_REQUIRED/SAVE_FAILED/STALE_SESSION/NOT_OPEN/NO_CHANNEL và quyền tự đồng bộ còn bật. Playlist lỗi hoặc ném ngoại lệ không đổi báo cáo Yêu thích và không dừng lịch; còn việc ở bất kỳ bên nào ⇒ lượt kế sau 1 s; lỗi kết nối (BACKOFF) của Playlist có tên giữ cả hai theo `notBefore`; lỗi riêng (hỏng tin, giao chưa rõ) chỉ chờ trong coordinator riêng với backoff riêng.
- `SyncActionsPort.runNamed` (mặc định BUILD_OFF, `DisabledSyncActions` tường minh), `TdlibSyncActions.runNamed`, `TdlibPlaylistSyncService.runNamedSync` (cùng cổng consent/build/foreground/identity như `runSync`; tối đa 1 tin/lượt dù người gọi xin nhiều; không tạo kênh), `SyncServiceProvider`/`SyncRuntime` truyền `namedHost`.
- **Chạm transport TDLib (có chủ ý):** `TdlibPlaylistSyncTransport.send` chỉ chấp nhận tin bắt đầu bằng `#mvsync`; nếu không mở thì mọi tin `#mvlist` bị coi là “giao chưa rõ”. Đã cho phép thêm tiền tố `#mvlist` (mở rộng, hành vi `#mvsync` giữ nguyên) và cho `TdlibHistoryReader` coi tin `#mvlist` bị sửa là bằng chứng liên tục giống `#mvsync`. Cả hai nằm trong phạm vi “transport chỉ ở mức văn bản”, không thuộc điều kiện dừng mục 8.
- Test: 13 (lịch: thứ tự, FLOOD, recovery, lỗi/ném ngoại lệ, tiếp tục sau 1 s, tắt đồng bộ giữ dữ liệu, rút quyền/đổi tài khoản giữa lượt, build tắt, cổng mặc định BUILD_OFF, provider tắt không đọc `namedHost`) + 7 qua TDLib giả thật (tin `#mvlist` đi qua transport thật tới máy khác; Yêu thích và Playlist chung kênh không ảnh hưởng nhau; tin bị sửa chặn đọc; thiếu quyền gửi ⇒ Denied, không gửi; build tắt/chưa nối/chưa gắn kênh không chạm TDLib; 1 tin/lượt) + 5 guard nguồn mới.

**S9 trạng thái + Cài đặt:**
- `PlaylistStatusFacts` thêm `namedPendingCount/namedRetryRequiredCount` (mặc định 0) + `withNamed(state)`, `allPendingCount`; `PlaylistSyncStatusPolicy` tính tổng: “Đã đồng bộ” cần cả hai bên hết chờ; câu chữ có ghi chú “Trong đó N thay đổi của các Playlist có tên” và “… đã gửi nhưng chưa rõ đã tới kênh chưa”. Không có Playlist có tên thì dòng trạng thái **y hệt cũ** (test so sánh).
- `NamedPendingReviewPolicy.rows`: loại thay đổi + tên Playlist + tên bài, không hiện id/tag/chat/tin; thêm cờ “chưa xác nhận đã tới kênh”. `PendingPlaylistReview` mang thêm `namedEntries/namedOpCounter/namedNames`; `enable()` yêu cầu khớp cả hai: **thay đổi Playlist có tên sau khi xem làm hủy xác nhận.** `MeloSyncSettingsActivity` liệt kê thêm các dòng này và đếm “Xem thay đổi chờ gửi (N)” gồm cả hai.

## 4. Chưa kiểm chứng / giới hạn

- Chỉ JVM + TDLib giả. Chưa chạy trên Sony; chưa gửi/nhận tin `#mvlist` thật qua Telegram; Keystore thật với alias mới vẫn chưa chạy.
- **Chưa có gì mở `NamedPlaylistSession` trong app** (việc của S13, Chặng 3). Tới lúc đó `AutomaticPlaylistSync` thấy view = null và `runNamedSync` trả NOT_OPEN: mã Chặng 2 hiện chưa hoạt động trên máy thật; thay đổi ở màn Cài đặt (dòng Playlist có tên, đếm chờ gửi) cũng chưa hiện được.
- Dòng “Tự đồng bộ Playlist” ở Cài đặt và nhãn Yêu thích: để S11.
- E105 vẫn BLOCKED nghiệm thu; nhiệm vụ này dựa trên đường đó và không sửa nó.
- Chủ dự án còn hai quyết định treo từ kế hoạch (4.5 công tắc chung — đang theo mặc định “một công tắc chung”; mục 7 QA hai máy). Chưa ảnh hưởng Chặng 3.

## 5. Điểm cần chủ dự án biết

1. **Guard nguồn cũ `SyncBuildGateReworkTest.everyProductionCallToTheCoordinatorOrTheServiceRunnerIsAccountedFor`** đếm đúng 2 lời gọi `.runOnce(` trong `TdlibPlaylistSyncService.kt`. Lời gọi coordinator mới là lời gọi thứ ba nên guard fail ở lần chạy lọc đầu. Vì luật “không sửa assertion cũ”, tôi **không** đổi test đó mà đặt tên lối vào mới là `runNamedOnce` / `runNamed` / `runNamedSync` (không khớp regex cũ) và thêm `NamedPlaylistSyncCallSitesTest` đếm đúng từng nơi gọi mới (chỉ service gọi `runNamedOnce`, chỉ provider gọi `runNamedSync`, chỉ bộ lập lịch gọi `runNamed` sau Yêu thích và sau kiểm quyền; lớp Yêu thích không nhắc `Named`/`mvlist`; lớp mới không tạo thread/timer). Nếu chủ dự án muốn guard cũ được cập nhật thay vì đặt tên khác, ghi REWORK.
2. Cờ `NAMED_ENABLED` vẫn chưa có (cần sửa `TelegramPlaylistV2Session.kt`); phương án ở prompt Chặng 3.
3. Máy bản cũ coi tin `#mvlist` là Foreign và bỏ qua (có test), nên thêm các tin này vào kênh không làm hỏng đồng bộ Yêu thích của máy cũ.

## 6. Thiết bị / an toàn

`SONY_SCREEN_END=KHÔNG THAO TÁC`. Không ADB, không Tiny, không Drive, không bí mật (`local.properties` không đọc). Repo chính không bị động tới.

## 7. Việc tiếp

Chặng 3 (S11–S19, UI Views). Prompt đầy đủ ở `2026-11-01-nhieu-playlist-co-ten-ban-giao.md`.

---

# Chặng 3 (S11–S19) — giao diện Playlist có tên

Thực thi: Claude Code Sonnet 5.5 (effort không xác định), phiên Chặng 3 (lệnh “Tiếp chặng 3” trong cùng hội thoại). Chi tiết ở phụ lục [2026-11-01-nhieu-playlist-co-ten-bao-cao-chang3.md](2026-11-01-nhieu-playlist-co-ten-bao-cao-chang3.md). Mã dừng ở `751b623`. **Chưa chạy trên máy; chưa có APK; chưa nghiệm thu.**

| Bước | Việc | Commit | Test mới | Kết quả |
|---|---|---|---|---|
| S11 | Nhãn Yêu thích (Phụ lục A); sửa 6 dòng test nhãn | `3f150c5` | 0 | PASS |
| S12 | `MeloNamedPlaylistPolicy` thuần | `a02ed2a` | 29 | PASS |
| S13 | `NamedPlaylistController`, mở/đóng cùng Yêu thích, cờ hoàn tác | `79b318e` | 22 | PASS |
| S14–S18 | Tab Playlist 2 mức, Thêm vào Playlist, Đổi tên/Xóa, Sắp xếp, Lưu hàng chờ, Phát | `0e8d45a`, `751b623` | 10 (guard nguồn) | PASS |
| S19 | Gate Ni | (commit hồ sơ) | — | PASS |

Gate S19 (một lần, cây sạch): Debug và Release mỗi biến thể 199 suite, **2683 test, 21 skipped, 0 failures, 0 errors** (baseline Chặng 3: 196/2622/21/0/0; +61 đúng phần mới); `assembleShareableDebug/Release`, `-PmeloOpen=true` (test + assemble Debug) và `assemblePersonalDebug` đều BUILD SUCCESSFUL; `git diff --check` sạch.

Điểm cần chủ dự án biết (chi tiết: phụ lục mục 5–6):
1. Từ Chặng 3, session Playlist có tên được mở trong app ⇒ mã đồng bộ Chặng 2 chạy thật ở foreground khi tự đồng bộ đang bật.
2. Phát Playlist: hàng chờ chỉ gồm bài Telegram; bài trên máy chạm từng bài (service giữ hai hàng chờ riêng). **Khác Q5 của kế hoạch khi Playlist có bài trên máy.**
3. Cờ `NAMED_ENABLED` ở `NamedPlaylistFlags.kt` (file `TelegramPlaylistV2Session.kt` không đổi).
4. Sắp xếp là hộp ↑/↓ (không phải chế độ ngay trên dòng); Gọn dùng hộp danh sách làm chỉ mục.
5. **Giao diện chưa từng được nhìn thấy trên máy**: bố cục, vùng chạm, xoay, chữ lớn, TalkBack chỉ có guard nguồn.
