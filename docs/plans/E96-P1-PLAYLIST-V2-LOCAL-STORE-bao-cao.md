# E96-P1-PLAYLIST-V2-LOCAL-STORE — BÁO CÁO THỰC THI (Sonnet 5.5)

**Kết luận đề xuất: PASS CANDIDATE (cổng mã nguồn + JVM).** Chờ ChatGPT nghiệm thu độc lập. Không merge, không P2, không tuyên bố production.
**APK: `QA BUILD PASS / DEVICE INSTALL BLOCKED`** — QA CANDIDATE, NOT PRODUCTION (xem mục 9).
Không có gì được kiểm trên Sony / Shanling / FYT / Telegram thật / emulator.

## 1. Git

| Mục | Giá trị |
|---|---|
| Kế thừa | P0 `dot/E96-P0-PLAYLIST-DATA-SAFETY` @ `152ead0139d4b7b1d8851ea6ddb1b234e7aa3f41` (đúng như đề bài; cây P0 sạch, không agent/tiến trình lạ) |
| Nhánh P1 | `dot/E96-P1-PLAYLIST-V2-LOCAL` |
| Worktree | `D:\Bots\MeloVault-E96-P1-Playlist-V2` (giữ lại; P0 worktree không bị sửa) |
| Commit mã nguồn | **`2dc45a3226d231a53089368bd26007f9b3e8b0b1`** (APK build từ đúng cây này) |
| Commit báo cáo | liền sau, chỉ thêm file này |
| Không làm | merge, push, reset, clean, stash, force push; không đụng E95/P0/main |

## 2. File thay đổi (10 file mã + báo cáo; +2128 / −14) — không file ngoài phạm vi

| File | Loại | Vai trò |
|---|---|---|
| `TelegramPlaylistModel.kt` | mới | `Dot`, `PlaylistItemId`, `PlaylistOp`, **`PlaylistOrSet`**, `OutboxEntry`, `PlaylistState` (thuần, không Android/đồng hồ/I-O) |
| `TelegramPlaylistCodec.kt` | mới | định dạng văn bản V2 xác định, chặt, có checksum |
| `TelegramPlaylistStoreV2.kt` | mới | file V2 mã hóa AES-GCM, nguyên tử, không ghi đè khi không đọc được |
| `TelegramPlaylistV2Session.kt` | mới | phiên V2: di trú V1→V2, bản chiếu V1, hòa giải xung đột, `PlaylistSessionApi`, `PlaylistV1Reconciler`, `TelegramPlaylistFlags` |
| `TelegramSavedPlaylistStore.kt` | sửa nhỏ | `KeystorePlaylistCipher(keyAlias)` + hằng `V1_ALIAS`/`V2_ALIAS`; thêm lý do `SCHEMA`, `ACCOUNT` (V1 giữ nguyên alias/định dạng) |
| `TelegramPlaylistSession.kt` | sửa nhỏ | phiên P0 thành `PlaylistSessionApi` (giữ nguyên hành vi); thêm kết quả `OutboxFull`; `TelegramPlaylistRuntime` chọn V1/V2 theo cờ |
| `TelegramPreviewActivity.kt` | sửa nhỏ (+29/−4) | Save/Unsave truyền `PlaylistTarget(songKey, chatId, rawMessageId)`; gắn chỉ mục thư viện; hook nâng khóa legacy sau `loadDatabaseIndex`; thông báo `OutboxFull`; tháo chỉ mục ở `onDestroy` |
| 3 file test mới | test | `TelegramPlaylistModelTest` (25), `TelegramPlaylistStoreV2Test` (21), `TelegramPlaylistV2SessionTest` (30) |

Không đổi: DB/schema SQLite, TDLib, đăng nhập, Media3/MediaSession, EQ/DSP, bố cục UI, Gradle, signing, versionCode. Không thêm Worker/Service/Receiver, không gửi gì ra Telegram.

## 3. Kiến trúc Playlist V2

```
 UI (Studio Deck / Gọn / Car / Phone / menu ⋮)            TelegramPreviewActivity
   toggleCurrentFavorite / toggleFavoriteSong  ──►  requestPlaylistToggle(PlaylistTarget)
                                                          │  (luồng "MeloVault-Playlist", FIFO, 1 người ghi)
                                                          ▼
                                   TelegramPlaylistRuntime ──► PlaylistSessionApi
                                                          ├─ V2_ENABLED=true : TelegramPlaylistV2Session  ◄── chính
                                                          └─ V2_ENABLED=false: TelegramPlaylistSession (P0, V1) ◄── lối lui
 TelegramPlaylistV2Session
   ├─ PlaylistState (RAM, bất biến)  ── OR-Set + legacy + outbox + migration + projection + checkpoint
   ├─ TelegramPlaylistStoreV2  ─►  noBackupFilesDir/tg_playlists_v2/<tg-sha256>.bin   (V2, khóa riêng, nguồn chính)
   └─ TelegramSavedPlaylistStore ─► noBackupFilesDir/tg_playlists_v1/<tg-sha256>.bin  (V1 = bản chiếu cho APK cũ)
 PlaylistIndex (chỉ mục SQLite, getSongsByKeys) ──► đổi songKey ↔ (chatId, rawMessageId)
```

Mỗi thay đổi: (a) `PlaylistState` mới (items + **outbox** + bộ đếm) ghi **một lần, nguyên tử** vào file V2 → (b) ghi bản chiếu V1. (a) và (b) là **hai file khác nhau, KHÔNG phải một giao dịch**; các digest trong trạng thái V2 cho biết lần khởi động sau bước nào còn thiếu.

## 4. Schema dữ liệu V2 (plaintext của file mã hóa)

Văn bản UTF-8, phân cách TAB, xác định (cùng trạng thái → cùng byte), dòng cuối `END<TAB>sha256(thân)`:

```
MVPL2
schema 2 | account <userId> | device <uuid> | lamport <n> | opcounter <n>
migration <NOT_STARTED|IN_PROGRESS|COMPLETE> <sha256 danh sách V1> <số bài V1>
projection <IN_SYNC|STALE|DIVERGED_ACK> <lastDigest> <pendingDigest> <truncated> <imported> <skipped> <ignoredRemovals>
checkpoint <channelId> <lastAppliedServerId>          ← dành cho P2/P3, P1 không đọc/ghi
A  <tg:chat:msg> <device#n> <seq>                      ← mỗi add tag còn/đã bị xóa
R  <tg:chat:msg> <device#n>                            ← tombstone: add tag đã bị remove
L  <songKey> <seq>   | LR <songKey>                    ← legacy chưa xác minh / legacy đã xóa
O  <opId> <ADD|REMOVE> <tg:chat:msg> <seq> <dots> <PENDING|ACKNOWLEDGED|RETRY_REQUIRED> <attempts>
```
- Danh tính bài: **`accountId` (ở mức trạng thái) + `chatId` + `rawMessageId`**; `songKey` chỉ là khóa hiển thị/V1/SQLite.
- Mã hóa: AES-256-GCM, Keystore alias **riêng** `melovault_e96_playlist_v2_aes_v1` (V1 giữ `melovault_e91_saved_playlist_aes_v1`), AAD = `"v2:" + tên thư mục tài khoản`, thư mục riêng `tg_playlists_v2` trong `noBackupFilesDir`.
- Phân loại tải: **Missing / Valid / Unreadable(IO, SIZE, DECRYPT, FORMAT, SCHEMA, ACCOUNT)**. Schema mới hơn → Unreadable(SCHEMA), không bao giờ bị ghi đè.
- Giới hạn: file ≤ 8 MB; outbox ≤ 5.000; số bài hiển thị tối đa 300 khi **thêm cục bộ** (như P0), nhưng V2 không cắt khi vượt.

## 5. OR-Set và Durable Outbox

**OR-Set.** Trạng thái = (`adds`: bài → {add tag → vị trí logic `seq`}, `removed`: bài → {add tag đã bị xóa}). Có mặt ⇔ còn ít nhất một add tag chưa bị tombstone. ADD sinh tag mới `(deviceId, bộ đếm đơn điệu)`; REMOVE chỉ chứa **các tag máy đó đã quan sát**. Merge = hợp tập → **giao hoán, kết hợp, lũy đẳng**. Một add mà bên xóa chưa thấy thì **không mất** (add-wins); một add đến sau remove của chính nó vẫn bị loại (tombstone giữ lại). Thứ tự hiển thị theo `seq` kiểu Lamport (bộ đếm logic), tie-break theo tag; **không dùng đồng hồ hệ thống** nên lệch giờ không đổi kết quả. Thứ tự V1 cũ được giữ vì di trú cấp `seq` 1..n theo thứ tự lưu.

**Durable Outbox.** Mỗi Save/Unsave Telegram tạo một `PlaylistOp` với `opId = "<device>#<bộ đếm>"` duy nhất, bất biến; op và trạng thái playlist nằm **trong cùng một lần ghi nguyên tử** của file V2 → kill app/mất mạng/khởi động lại không làm mất. Mặc định `PENDING`; `RETRY_REQUIRED` tăng `attempts` nhưng giữ nguyên `opId`; `ACKNOWLEDGED` chỉ để mô hình/test và `pruneAcknowledged()` là cách duy nhất xóa entry (chưa xác nhận thì không bao giờ bị xóa). Đầy 5.000 entry → từ chối thay đổi mới với thông báo, không bỏ entry cũ. **P1 không gửi gì và không đánh dấu ACK** — không có "Đã đồng bộ cloud" ở UI. Bài `legacy` (chưa xác minh) và nhạc local **không** sinh op/outbox.

## 6. Di trú V1 → V2 (bằng chứng: test, mục 8)

1. Đọc V1 (chỉ đọc). Không đọc được → **DỪNG**, V1 và V2 không bị chạm (`Blocked`).
2. Mỗi key V1 theo đúng thứ tự: chỉ mục xác minh được `(chatId, rawMessageId)` và `songKey` khớp → bài Telegram (+ op PENDING); không xác minh được → **`legacy` (UNRESOLVED_LEGACY)**, giữ nguyên khóa, không đoán chatId, không vào outbox.
3. Ghi V2 với `migration=IN_PROGRESS` (nguyên tử) → **đọc lại** và so thứ tự với V1 → ghi lại `COMPLETE`. Chỉ khi đó mới coi thành công.
4. V1 **không bị xóa/ghi/đổi tên** trong suốt quá trình. Chạy lại: có file V2 thì không nhập lại (không nhân đôi); `IN_PROGRESS` còn dở thì chỉ hoàn tất.
5. Kill ở bất kỳ điểm nào: trước commit → không có V2, V1 nguyên, thử lại sạch; sau commit IN_PROGRESS nhưng trước COMPLETE → lần mở sau hoàn tất (không trùng).
6. Sau khi chỉ mục nạp xong, `resolveLegacyKeys` đổi `legacy` thành bài thật **đúng vị trí cũ** (op ADD mới, không tombstone).

## 7. Tương thích V1/V2, xung đột, rollback

- V2 là nguồn chính. V1 = "bản chiếu": **300 bài thêm gần nhất** (APK cũ cắt ở 300 và sẽ hiện nhầm 300 bài *cũ nhất* nếu ghi nhiều hơn). **V2 > 300: V2 giữ đủ, APK cũ chỉ thấy 300 bài mới nhất; muốn thấy đủ phải dùng bản E96.** Ghi `truncated=1`.
- Ghi V1 lỗi → V2 đã commit **không** bị hoàn tác; trạng thái `STALE` (RAM) và lần mở sau sửa lại. V1 không đọc được → để nguyên, V2 vẫn chạy.
- **V1 bị APK cũ đổi sau di trú** (phát hiện bằng digest: khác `lastDigest`, `pendingDigest` và nội dung kỳ vọng): bài APK cũ **thêm** được nhập như ADD mới; bài V2 đã/đang biết mà từng bị xóa **không** được hồi sinh; bài APK cũ **bỏ** khỏi V1 **không** bị xóa khỏi V2 (chỉ đếm) vì mất bài tệ hơn hiện thừa bài; sau khi V2 hợp nhất đã commit mới thay V1 bằng bản chiếu. Đếm vào `conflictImported/Skipped/IgnoredRemovals`. Idempotent. V1 bị mất file trong khi V2 còn bài → dựng lại V1, không coi là "xóa hết".
- **Rollback:** (1) đổi `TelegramPlaylistFlags.V2_ENABLED=false` (hằng trong code) → dùng lại phiên P0 trên V1 (V1 luôn là bản chiếu mới nhất ≤300), file V2 giữ nguyên, không xóa gì; bật lại sau đó thì bộ hòa giải ở trên gộp thay đổi. (2) hoặc `git revert 2dc45a3`. Android không cho cài đè versionCode thấp hơn → quay lại = phát bản có versionCode cao hơn. Không `uninstall`/`pm clear`.

## 8. Kiểm thử (JVM; chạy trên Ni, worktree P1, JDK 21)

`.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain` → **BUILD SUCCESSFUL**.

| Biến thể | Tests | Passed | **Skipped** | Failed/Errors |
|---|---|---|---|---|
| Debug | 1.464 | 1.443 | 21 (có sẵn từ P0/E94, không phải của P1) | 0 / 0 |
| Release | 1.464 | 1.443 | 21 | 0 / 0 |

= 1.388 (P0) + 76 mới. Lớp playlist (cả 2 biến thể, 0 skipped): Model 25, StoreV2 21, V2Session 30, + P0: Policy 8, Session 15, ViewPolicy 7, SavedPlaylistStore 23, SongsByKeysSql 5.

| # | Yêu cầu | Kết quả | Test |
|---|---|---|---|
| 1 | V1→V2 rỗng | PASS | `emptyV1MigratesToAnEmptyCompleteV2`, `noV1AtAllAlsoGivesAnEmptyV2` |
| 2 | 1 bài | PASS | `oneSongMigratesWithItsRealSourceWhenTheIndexKnowsIt` |
| 3 | 300 bài | PASS | `threeHundredSongsMigrateInOrderAndV1IsNotTouched` (V1 byte-từng-byte) |
| 4 | Chạy lại không nhân đôi | PASS | `reopeningManyTimesNeverDuplicatesAnything` |
| 5 | Crash trước commit V2 | PASS | `powerLostBeforeV2IsCommittedLeavesNothingAndRetriesCleanly` |
| 6 | Crash sau commit V2, trước bản chiếu V1 | PASS | `powerLostAfterV2CommitButBeforeTheV1CopyIsRepairedOnTheNextStart`, `powerLostAfterV2IsCommittedButBeforeItIsMarkedCompleteFinishesLater` |
| 7 | V1 lỗi giải mã không bị ghi đè | PASS | `anUnreadableV1StopsMigrationAndLeavesEveryFileAlone` |
| 8 | V2 lỗi giải mã không bị ghi đè | PASS | `anUnreadableV2IsNeverOverwritten…`, `aDamagedV2FileIsUnreadable…`, `aNewerSchemaFileIsNeverOverwritten` |
| 9 | Khóa V2 thiếu không phá dữ liệu cũ | PASS | `aMissingV2KeyDoesNotDestroyEitherFileOrMintAKey`, `aMissingV2KeyNeverCreatesANewOneForAnExistingFile` (đếm `encrypt`=0) |
| 10 | Hai tài khoản không trộn | PASS | `twoAccountsNeverMix…`, `twoAccountsNeverShareAFileAndASwappedFileIsRejected` |
| 11 | Cùng rawMessageId, khác nguồn | PASS | `sameRawMessageIdInTwoChatsAreTwoDifferentSongs`, `twoChatsWithTheSameRawMessageIdStayTwoSongs` |
| 12 | Legacy không xác định vẫn giữ | PASS | `unresolvedLegacyKeysSurviveMigrationAndAreNamedLater…`, `anIndexThatFailsOrLies…`, `anUnverifiedLegacyKeyNeverCreatesAnOperation…` |
| 13 | ADD/REMOVE đúng thứ tự | PASS | `addAndRemoveInOrderAndReAddGivesANewTag`, `anAddThatArrivesAfterItsRemoveStaysRemoved` |
| 14 | ADD đồng thời 2 client giả | PASS | `concurrentAddsOnTwoClientsBothSurviveAndOrderMatches`, `theSameSongAddedOnTwoClients…` |
| 15 | REMOVE sau khi đã quan sát ADD | PASS | `aRemoveAfterObservingTheAddRemovesItEverywhere`, `anAddThatTheRemoverNeverSawIsNotLost` |
| 16 | Merge trùng lặp cùng kết quả | PASS | `addingTheSameSongTwice…`, `everyDeliveryOrderWithRepeats…` |
| 17 | Outbox còn sau restart | PASS | `theOutboxIsStillThereAfterARestartWithTheSameOperationIds` |
| 18 | `opId` không đổi khi retry | PASS | `retryKeepsTheOperationId…`, `anOperationKeepsItsIdAcrossSaveRetryAndReload` |
| 19 | 2 thao tác liên tiếp | PASS | `twoChangesInARowBothStick…`, `aRapidBurstFromTwoThreadsLosesNothing` (80/80) |
| 20 | Đổi tài khoản khi còn pending | PASS | `twoAccountsNeverMixAndOnePendingOutboxStaysWithItsAccount` |
| 21 | Playlist ngoài 200 bài RAM | PASS (logic) | `aListLongerThanTheRamWindowIsReturnedInFull`; P0 `aSavedSongOutsideTheNewest200IsStillShownFrom25000Songs` |
| 22 | V2 > 300 không mất bài | PASS | `moreThan300SongsInV2AreKeptWholeAndTheV1CopyIsCappedOnPurpose` (350 giữ nguyên) |
| 23 | Bản chiếu V1 lỗi không phá V2 | PASS | `aV1CopyThatFailsNeverRollsBackOrDamagesV2`, `anUnreadableV1CopyIsLeftAlone…` |
| 24 | Phát hiện V1 bị APK cũ đổi | PASS | `v1ChangedByAnOlderAppIsDetectedMergedWithoutResurrection…`, `aV1CopyThatIsOnlyStale…IsRepairedNotTreatedAsAConflict` |
| 25 | Hồi quy P0 | PASS | toàn bộ lớp test P0 giữ nguyên và xanh (58 test) |
| — | OR-Set: kết hợp/giao hoán/lũy đẳng/hội tụ | PASS | `mergeIsCommutativeAssociativeAndIdempotent` (25 seed × 3 máy), `everyDeliveryOrderWithRepeatsConvergesToTheSameSetAndOrder` (30 thứ tự xáo + lặp), `mergingTheReplicasOfAllDevicesEqualsApplyingEveryOperation` |

**Kiểm tra độ nhạy của test (đột biến tạm thời, đã khôi phục, `cmp` khớp):** tắt tombstone trong `liveDots`, bỏ chặn ghi đè của `PlaylistStoreV2.save`, và cho phép hồi sinh bài đã xóa → 14/76 test đỏ (log `02-mutation-sanity-check.log`). Mã cuối không chứa đột biến.

Giới hạn của JVM: Keystore dùng AES-GCM phần mềm cùng bố cục; file dùng `java.nio` thay `AtomicFile` ở test; "mất điện" mô phỏng bằng lỗi ghi; `TelegramPreviewActivity` chỉ được **biên dịch**, chưa chạy.

## 9. Build và APK QA — `QA BUILD PASS / DEVICE INSTALL BLOCKED`

`.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain` → **BUILD SUCCESSFUL**. `git diff --check` sạch.

| Mục | Debug (bàn giao) |
|---|---|
| Đường dẫn APK QA | `D:\Bots\_handoff\MeloVault\E96-CROSS-DEVICE-SYNC\QA_APK_E96_P1\MeloVault-E96-P1-PLAYLIST-V2-LOCAL-2dc45a3-shareable-debug-vc109-NiSigned-NoTelegramConfig-QA-CANDIDATE.apk` (+ `.sha256`) |
| SHA-256 (đọc lại từ bản sao QA = file build) | `1ffea5b55563df812af4fc2bf6b0f9b8d82f569fa128449533542ee21f581f6c` |
| Kích thước | 33.181.732 byte |
| Package / version | `app.melovault.player` / vc **109** / `0.6.88-beta` (không bump) |
| Chứng chỉ ký SHA-256 | `a9b8cb6b5b0bc0400955b24dbc85801f5e8855356287caca2dcf3bb4e13db280` |
| Telegram config | **false** (`TELEGRAM_APP_API_ID=0`) |
| ABI | chỉ `arm64-v8a` |
| Release (không bàn giao) | SHA-256 `e5e806c6cc26cfdb0c018f3fc72676fccc6f5a1b7dbd78f6467601eff49b6f05`, 30.472.750 byte, cùng chứng chỉ |

Vì sao thiết bị BLOCKED (giống P0, không tự vượt): (1) chữ ký debug của Ni `a9b8cb6b…` ≠ chữ ký Preview `eecd3df7…` của bản đang cài → cài đè sẽ **signature mismatch → dừng**; (2) Telegram config=false → không đăng nhập/thử playlist thật được; (3) versionCode 109 trùng Beta109 (quyết định #13 cần bump trước khi lên máy/Drive); (4) emulator `x86_64` ≠ APK `arm64-v8a`. Không sửa signing/versionCode/secrets/ABI, không cài, không `uninstall`/`pm clear`. Muốn QA chức năng: Git bundle + SHA256 → build ở Tiny clone cô lập (có Telegram config) rồi verify package/version/cert. Không upload Drive, không đổi quyền chia sẻ.

## 10. Giới hạn chưa kiểm trên thiết bị thật

Keystore thật với alias V2 (tạo khóa, đọc lại, hành vi khi alias mất); file V1 thật của chủ dự án; di trú trên dữ liệu thật và thời gian di trú; chỉ mục SQLite thật làm `PlaylistIndex` (kể cả tỉ lệ bài vào `legacy` lúc mở app lần đầu vì chỉ mục nạp sau); thao tác chạm Save/Unsave trên FYT/Phone/Shanling; kill tiến trình thật; playback/MediaSession/âm thanh (không đổi nhưng **CHƯA XÁC NHẬN**); kích thước file V2 theo thời gian.

## 11. Rủi ro / điểm cần ChatGPT cân nhắc

1. **Outbox không có ai xả trong P1** (chưa có đồng bộ): sau 5.000 thay đổi chưa ACK, Save/Unsave bị từ chối (`OutboxFull`) cho tới P2. Rất khó gặp nhưng là điểm dừng cứng; P2 phải xả hoặc nâng/nén có kiểm soát.
2. Tombstone và add tag không bao giờ bị dọn (chưa có "safe point" của các thiết bị) → file V2 chỉ tăng; còn dư địa lớn so với giới hạn 8 MB.
3. Thao tác **bỏ bài của APK cũ** không được áp vào V2 (cố ý, an toàn dữ liệu): sau rollback tạm thời, bài đã bỏ có thể hiện lại.
4. Lần mở đầu tiên, nếu chỉ mục SQLite chưa sẵn sàng, mọi key V1 thành `legacy` rồi được nâng sau khi chỉ mục nạp; trong lúc đó chúng chưa có op (an toàn, hiển thị như trước). Bài mà dòng chỉ mục đã bị đánh dấu xóa sẽ ở `legacy` mãi (vẫn hiện giữ chỗ, vẫn xóa được).
5. `deviceId` là UUID ngẫu nhiên theo tài khoản/máy; xóa file V2 (không phải việc của app) sẽ tạo thiết bị mới.
6. Cờ `V2_ENABLED` là hằng trong code (không thêm `buildConfigField`); đổi cờ cần build mới.
7. V1 projection của danh sách > 300 là có tổn hao (đã nêu rõ ở mục 7).

## 12. Chuẩn bị cho P2

Có sẵn: `PlaylistOp` bất biến + `opId`; `PlaylistState.applyRemote`, `acknowledge`, `markRetryRequired`, `pruneAcknowledged`; `SyncCheckpoint(channelId, lastAppliedServerId)`; merge thuần của OR-Set; bài `legacy`/local không bao giờ vào op. Cần làm ở P2: thêm vào `PlaylistSessionApi` đường nhận op từ xa và lấy/ack outbox; bộ giải mã tin `#mvsync` (acct fingerprint, phiên bản, chia lô ≤ giới hạn tin); chính sách dọn tombstone theo "safe point" các thiết bị đã biết; xử lý `OutboxFull`; fake channel Telegram cho test hội tụ nhiều máy. P2 vẫn không chạm Telegram thật nếu chưa có phê duyệt riêng.

## 13. Dọn dẹp tài nguyên

- Không khởi động emulator/dịch vụ/tiến trình nền; không ADB tới thiết bị; không bật màn hình (SONY_SCREEN_END/SHANLING_SCREEN_END: không áp dụng).
- Dữ liệu test: `TemporaryFolder` của JUnit (tự xóa) và SQLite trong bộ nhớ. Bản sao lưu 3 file dùng cho kiểm tra đột biến (thư mục scratchpad của phiên) đã xóa sau khi khôi phục và `cmp` khớp.
- Log tạm trong thư mục tạm của shell đã sao vào `docs/plans/artifacts/E96-P1-PLAYLIST-V2-LOCAL/` (Git bỏ qua) rồi xóa bản tạm. Giữ nguyên: báo cáo, log, APK QA, worktree P0 và P1, nhánh.
- Gradle daemon dùng cho các lệnh build của phiên này (1 tiến trình IDLE, PID 23684): đã dừng bằng `gradlew --stop`; kiểm lại `gradlew --status` không còn daemon nào. Không đụng tiến trình của Agent khác.
- Không dùng `reset --hard`, `clean`, `stash`, force push. Không đọc/copy `local.properties` hay secrets. Không đụng Private Stable, backend, TelegramSecretary, Beta85 gốc, session/cache TDLib.

Đã dừng, chờ ChatGPT nghiệm thu.
