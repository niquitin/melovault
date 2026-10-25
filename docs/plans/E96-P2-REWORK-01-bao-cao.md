# E96-P2-REWORK-01 — BÁO CÁO SỬA LỖI SYNC CORE (Sonnet 5.5)

**Kết luận đề xuất: READY FOR CHATGPT QA lại (cổng mã nguồn + JVM với kênh giả).** Không phải production PASS. Không merge, không P3, không Telegram/TDLib thật, không cài APK, không đụng thiết bị.
**APK: `QA BUILD PASS / DEVICE INSTALL BLOCKED`** (mục 7).

## 1. Git

| Mục | Giá trị |
|---|---|
| Xuất phát | `f146d33` trên `dot/E96-P2-PLAYLIST-SYNC-CORE` (đúng dự kiến; cây sạch; không agent/tiến trình lạ) |
| **Commit sửa lỗi** | **`eac2fbc9a39487296b7fe557b2a4d0ae2df65535`** |
| Commit báo cáo | liền sau, chỉ thêm file này |
| Worktree | `D:\Bots\MeloVault-E96-P2-Sync-Core` |
| Không làm | merge, push, reset/clean/stash/force push; không sửa worktree P0/P1/E95 |

Diff so với `f146d33` (7 file, +602/−46): `TelegramPlaylistSyncCoordinator.kt` (+117/−25), `TelegramPlaylistSyncTransport.kt` (+38/−9), `TelegramPlaylistModel.kt` (+15/−1), `TelegramPlaylistCodec.kt` (+7/−3), `SyncTestKit.kt` (+11/−3), `TelegramPlaylistSyncCoordinatorTest.kt` (+15/−5), `TelegramPlaylistSyncReworkTest.kt` (mới, +399). Không đổi Activity, UI, playback, TDLib, SQLite, Gradle, signing, versionCode.

## 2. Quy trình "test trước, sửa sau"

Các test mới được viết và chạy **trên mã cũ (`f146d33`)** trước khi sửa: **8/8 đỏ**, đúng như biên bản (log `01-BEFORE-FIX-new-tests-fail-on-d3b95cd-code.log` + `01b-BEFORE-FIX-junit.xml`; nguồn của lớp P2 khi đó chính là `d3b95cd`):

| Test (trên mã cũ) | Thông báo lỗi thật |
|---|---|
| `r01_aTransportThatPagesNewestFirst…` | `message 5003 is below the cursor 5642 but dev-x#1 is not applied` — cursor nhảy qua 150 tin chưa đọc |
| `r02_runningOutOfPagesWhileMoreRemains…` | `20 of 45 is not 'synchronised'. Actual: OK` |
| `r02_catchUpOverSeveralRuns…` | `OK only when everything was read expected:<45> but was:<10>` |
| `r02_aFullReplayLongerThanOneRun…` | `the late message was reached by replaying across runs` — replay đọc lại 20 tin đầu mãi, không tới tin trễ |
| `r03_aFloodWaitOfThirtySixHours…` / `r03_aThirtySixHourWait…` | `expected:<129600000> but was:<86400000>` |
| `r03_floodWaitBoundaries` | `expected:<86401000> but was:<86400000>` |
| `r03_theWaitNeverOverflowsTheClock` | `expected:<9223372036854775807> but was:<-9223372036768375819>` — thời điểm chờ tràn số thành quá khứ |

## 3. Cách sửa

**R01 — phân trang an toàn.** Hợp đồng nay nằm trong `PlaylistSyncTransport`/`FetchResult.Page`: `fetchAfter` trả **phần CŨ NHẤT chưa đọc** sau cursor, và mỗi trang phải mang `completeThroughId` = lời cam kết "mọi tin hiển thị có id trong `(after, completeThroughId]` đều nằm trong trang". Điều phối:
- cursor chỉ được dời **tới đúng bằng chứng đó, không vượt** (kể cả qua khoảng trống id; trang rỗng mà `completeThroughId` tiến lên vẫn làm cursor tiến — id thưa không gây kẹt); tin nằm trên mức chứng minh bị bỏ qua (sẽ đọc ở trang sau);
- trang **không có bằng chứng** (`UNPROVEN`, mặc định), bằng chứng **thấp hơn vị trí**, hoặc **báo còn nữa mà không tiến** → bị từ chối: `RECOVERY_REQUIRED(TRANSPORT_CONTRACT)`, **không áp dụng gì, cursor đứng yên, không gửi**;
- kiểm tra thứ hai (mặc định bật, `SyncConfig.verifyPageStart`): hỏi lại một trang cỡ 1 tại cùng vị trí — tin cũ nhất phải đúng bằng tin đầu của trang. Trang "mới nhất trước" dù khai báo bằng chứng giả, hoặc trang bỏ sót tin cũ nhất, bị bắt ở đây;
- thứ tự trong trang, lặp lại và khoảng trống id vẫn được chịu (sắp xếp, bỏ lặp).

**R02 — chưa hoàn tất không phải OK.** Hết `maxPagesPerRun` mà trang cuối còn `hasMore` → `SyncStatus.CATCH_UP_INCOMPLETE` (kèm `cursor`, `replayPosition`), **không gửi, không dọn outbox** cho tới khi đọc xong (FETCH vẫn đi trước SEND). `OK` chỉ khi thực sự đọc hết. `FULL_REPLAY` giờ **giữ vị trí** trong `SyncCheckpoint.replayPosition` (ghi chung một lần ghi nguyên tử với áp dụng + cursor), nên gọi lại — sau nghỉ hoặc sau khởi động lại — tiếp tục đúng chỗ; hết lịch sử thì tự xóa vị trí (kể cả khi trang cuối rỗng). Vị trí replay tách khỏi `lastAppliedServerId` (cursor tăng dần của đọc tăng dần); đọc tăng dần xen giữa không làm hỏng replay đang dở. Định dạng file: dòng `checkpoint` vẫn **y như cũ (3 trường)** khi không có replay; chỉ khi đang replay mới có trường thứ tư (hợp lệ ≥ 0, âm bị từ chối).

**R03 — FLOOD_WAIT.** Bỏ trần 24 giờ: chờ đúng số giây server yêu cầu (sàn 1 giây cho giá trị ≤ 1), `Int.MAX_VALUE` giây vẫn đúng (tính bằng Long), thời điểm hết chờ **bão hòa ở `Long.MAX_VALUE`** thay vì tràn về quá khứ.

## 4. Test mới (20, `TelegramPlaylistSyncReworkTest`, 0 skipped) và kết quả

| Nhóm | Test | Kiểm |
|---|---|---|
| R01 | `r01_aTransportThatPagesNewestFirstNeverMakesTheCursorSkipUnreadMessages` | bất biến: cursor không bao giờ đứng trên tin chưa áp (đối chiếu từng tin trong kênh) |
| R01 | `r01_newestFirstWithoutAProofIsRefused…AndNothingIsLostOnceTheTransportIsRight` | không bằng chứng → từ chối, cursor 0, không gửi; sửa transport → đọc đủ 250 + 1 |
| R01 | `r01_aTransportThatLiesAboutBeingCompleteIsCaughtByTheSecondLook` | newest-first khai bằng chứng giả → bắt được, cursor 0 |
| R01 | `r01_aPageThatLeavesOutTheOldestUnreadMessageIsCaught` | trang bỏ tin cũ nhất → bắt được |
| R01 | `r01_anHonestPartialPageLosesNothing` | trang chỉ chứng minh nửa đầu + kèm tin thừa → 120/120, không mất |
| R01 | `r01_sparseIdsAndEmptyPagesMakeProgressThroughTheProof` | id cách nhau 7×10¹² và trang rỗng vẫn tiến |
| R01 | `r01_everyBrokenPromiseIsRefusedWithoutMovingTheCursor` | 4 kiểu vi phạm hợp đồng, cursor không đổi, rồi hồi phục |
| R01 | `r01_messagesAboveTheProofCountForNothing` | tin trên mức chứng minh không bị áp trước |
| R02 | `r02_runningOutOfPagesWhileMoreRemainsIsNotReportedAsOk`, `r02_catchUpOverSeveralRunsEndsInARealOkAndNotBefore`, `r02_theBudgetBoundaryIsExact` (20/21 tin) | trạng thái `CATCH_UP_INCOMPLETE` đúng ranh giới; `OK` thật ở lần cuối |
| R02 | `r02_nothingIsSentWhileTheHistoryIsNotReadAndItIsSentAfterwards` | không gửi khi chưa đọc xong |
| R02 | `r02_aFullReplayLongerThanOneRunContinues…AndFindsTheLateMessage` | replay nhiều lần chạy + khởi động lại giữa chừng, tìm được tin trễ, kết thúc `OK` |
| R02 | `r02_aReplayRemembersWhereItStoodAndClearsItselfWhenDone`, `r02_aReplayThatEndsRightOnAnEmptyPageStillClearsItsPosition` | vị trí bền qua restart, không bị đọc tăng dần phá, tự xóa; replay kế bắt đầu lại từ đầu |
| R02 | `r02_theCheckpointFileFormatIsUnchangedUnlessAReplayIsRunning` | dòng 3 trường khi không replay; 4 trường khi replay; âm bị từ chối |
| R03 | `r03_aFloodWaitOfThirtySixHoursIsObeyedAsGiven`, `r03_floodWaitBoundaries` (24 h, ±1 s, `Int.MAX_VALUE`, 0, −5, 1, 2), `r03_theWaitNeverOverflowsTheClock`, `r03_aThirtySixHourWaitHoldsTheCoordinatorForThirtySixHours` | đúng 36 h (129.600.000 ms), ranh giới, bão hòa, điều phối thật không chạy sớm |

**Hai test cũ được siết chặt, không hạ:** `t16c` (trang rỗng báo còn nữa): từ "OK" sang `RECOVERY_REQUIRED/TRANSPORT_CONTRACT` (vẫn đúng 1 lần gọi, không quay vòng, cursor 0); `t15b`: thêm kiểm trạng thái `CATCH_UP_INCOMPLETE`, `CATCH_UP_INCOMPLETE`, `OK`. Kênh giả (`FakeTelegramChannel`) nay trả bằng chứng đúng sự thật (kiểm chứng ngược lại hợp đồng); khung test thêm `transportFactory`, `allMessages()`.

**Chạy lại toàn bộ** (trên commit `eac2fbc`): `.\gradlew.bat --no-daemon :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --rerun-tasks --console=plain` → **BUILD SUCCESSFUL**, `59 actionable tasks: 59 executed`.

| Biến thể | Tests | Passed | **Skipped** | Failed/Errors |
|---|---|---|---|---|
| Debug | 1.554 | 1.533 | 21 (có sẵn từ P0/E94, không do rework) | 0 / 0 |
| Release | 1.554 | 1.533 | 21 | 0 / 0 |

1.554 = 1.534 + 20. Mỗi biến thể, 0 skipped: **P0 58**, **P1 81**, **P2 gốc 65 (Protocol 18, Coordinator 47)**, **rework 20**. Chi tiết: `docs/plans/artifacts/E96-P2-REWORK-01/06-playlist-class-results.txt`.

**Độ nhạy của bản sửa (đột biến tạm thời, đã khôi phục, `cmp` khớp):** bỏ kiểm tra lần hai, bỏ kiểm tra bằng chứng, cho hết ngân sách vẫn báo OK, không lưu vị trí replay, thêm lại trần 24 giờ → 13/20 test rework đỏ (`03-mutation-sanity-check.log`).

## 5. Hợp đồng Coordinator ↔ Transport (cho P3)

`fetchAfter(channel, after, limit)` phải trả phần **cũ nhất** chưa đọc sau `after` (không bao giờ "mới nhất trước"), cùng `completeThroughId`; chưa chứng minh được thì để `UNPROVEN`/ `after` để bị từ chối. Lưu ý riêng cho adapter TDLib: `getChatHistory` đi từ mới tới cũ — adapter phải lùi tới đúng `after`, đảo thứ tự rồi mới phục vụ phần đầu của dãy đó, và chỉ khai bằng chứng cho phần nó đã thực sự đi hết. `Page` có mặc định `UNPROVEN`: adapter quên khai báo sẽ bị chặn an toàn thay vì làm mất tin.

## 6. Giới hạn còn lại

1. **Lỗ hổng giữa trang không kiểm chứng được ở tầng điều phối:** kiểm tra lần hai chỉ xác nhận ranh giới dưới (tin cũ nhất). Một transport tự khai bằng chứng nhưng làm rơi một tin ở giữa trang vẫn có thể gây mất tin; tính đúng đắn của bằng chứng là trách nhiệm của adapter (P3, kèm test với TDLib giả) — ranh giới tin cậy này chưa được loại bỏ.
2. Kiểm tra lần hai thêm một lời gọi nhỏ mỗi trang có tin (tắt được bằng `verifyPageStart=false`); khi tin hiện muộn giữa hai lời gọi nó có thể báo nhầm `TRANSPORT_CONTRACT` (an toàn: cursor đứng yên, thử lại sau backoff).
3. `FULL_REPLAY` chỉ tiếp tục khi được gọi lại bằng chế độ `FULL_REPLAY`; việc ai gọi và bao lâu một lần (lịch) thuộc P3. `INCREMENTAL` cố ý không đụng replay đang dở.
4. Khi đang replay, file V2 có trường `checkpoint` thứ tư; bản dựng cũ (P1/P2 gốc) đọc file đó sẽ coi là `FORMAT` (bị chặn, không bị ghi đè). Bản chưa phát hành ra thiết bị nên rủi ro thấp, nhưng cần nhớ khi rollback giữa chừng một replay.
5. Tin trễ id thấp hơn cursor vẫn chỉ được nhặt bằng replay; chưa snapshot; `REMOVE` >32 tag vẫn là `OP_TOO_LARGE` (như báo cáo P2); lịch đồng bộ, tạo kênh, consent: P3.
6. Chưa chạy với TDLib/Telegram thật, Keystore thật, thiết bị thật; hiệu năng ghi V2 trên thiết bị chưa đo.

## 7. APK QA — `QA BUILD PASS / DEVICE INSTALL BLOCKED`

Build lại từ `eac2fbc` (không dùng APK cũ để khẳng định đã sửa; APK P2 cũ `d3b95cd` **không** chứa `TRANSPORT_CONTRACT`, APK mới có — đã kiểm trong dex).

| Mục | Debug (bàn giao) |
|---|---|
| Đường dẫn | `D:\Bots\_handoff\MeloVault\E96-CROSS-DEVICE-SYNC\QA_APK_E96_P2_REWORK_01\MeloVault-E96-P2-REWORK-01-eac2fbc-shareable-debug-vc109-NiSigned-NoTelegramConfig-QA-CANDIDATE.apk` (+ `.sha256`) |
| SHA-256 (bản sao QA = file build) | `297c078f612932c5e87d816294808521ff449aea2422a3b10f0835865d732b27` |
| Kích thước | 33.874.670 byte (chỉ `classes5.dex` khác bản P2 trước +9 KB mã) |
| Package / version | `app.melovault.player` / vc **109** / `0.6.88-beta` (không bump) |
| Chứng chỉ ký SHA-256 | `a9b8cb6b5b0bc0400955b24dbc85801f5e8855356287caca2dcf3bb4e13db280` |
| Telegram config | **false** (`TELEGRAM_APP_API_ID=0`) · ABI chỉ `arm64-v8a` |
| Release (không bàn giao) | SHA-256 `1a87142ca7665f9c8c636aa37e03aaaccaf56357ce3ff3bc36d9cc9a66971700`, 30.505.518 byte |

Không cài được/không dùng để thử Telegram (như P0/P1/P2): chữ ký Ni ≠ chữ ký Preview `eecd3df7…`, thiếu Telegram config, versionCode 109 trùng Beta109, AVD sẵn `x86_64`. Không sửa signing/versionCode/secrets/ABI, không cài, không `uninstall`/`pm clear`, không upload Drive. Sync core vẫn không được ứng dụng gọi (`SYNC_ENABLED=false`), nên APK không có hành vi mới chạy được.

## 8. Rollback

Chưa nối vào ứng dụng nên không có hành vi runtime để rollback: `git revert eac2fbc` (trở về hành vi `f146d33`, có ba lỗi đã nêu) hoặc bỏ nhánh. Cờ `SYNC_ENABLED=false` là mặc định.

## 9. Dọn dẹp tài nguyên

- Mọi lệnh Gradle chạy `--no-daemon` (kiểm lại `gradlew --status`: không còn daemon); không emulator, không ADB, không bật màn hình thiết bị (SONY_SCREEN_END/SHANLING_SCREEN_END: không áp dụng).
- Bản sao lưu 2 file dùng cho kiểm tra đột biến (thư mục scratchpad) đã xóa sau khi khôi phục và `cmp` khớp. Dữ liệu test: `TemporaryFolder` của JUnit và bộ nhớ. Log tạm của shell đã sao vào `docs/plans/artifacts/E96-P2-REWORK-01/` (Git bỏ qua) rồi xóa bản tạm.
- Giữ nguyên: báo cáo, log, APK QA của P0/P1/P2 và rework (không xóa/ghi đè), các worktree và nhánh. Không đụng tiến trình Agent khác; không `reset --hard`/`clean`/`stash`/force push; không đọc/copy secrets hay `local.properties`; không Telegram thật.

Đã dừng, chờ ChatGPT nghiệm thu độc lập.
