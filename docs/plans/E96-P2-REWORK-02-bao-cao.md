# E96-P2-REWORK-02 — BÁO CÁO SỬA LỖI R04 "OK TRUNG THỰC" (Sonnet 5.5)

**Kết luận đề xuất: READY FOR CHATGPT QA lại (cổng mã nguồn + JVM với kênh giả).** Không phải production PASS. Không merge, không P3, không Telegram/TDLib thật, không cài APK, không đụng thiết bị.
**APK: `QA BUILD PASS / DEVICE INSTALL BLOCKED`** (mục 6).

## 1. Git

| Mục | Giá trị |
|---|---|
| Xuất phát | `3e0d969` trên `dot/E96-P2-PLAYLIST-SYNC-CORE` (đúng dự kiến; cây sạch; không agent/tiến trình lạ) |
| **Commit sửa lỗi** | **`63ccbe94f74bf17eb6348902aa0f8c80afc58010`** |
| Commit báo cáo | liền sau, chỉ thêm file này |
| Worktree | `D:\Bots\MeloVault-E96-P2-Sync-Core` |
| Diff so với `3e0d969` | 3 file, +277/−14: `TelegramPlaylistSyncCoordinator.kt` (+50/−13), `TelegramPlaylistSyncCoordinatorTest.kt` (+3/−1, sửa t25), `TelegramPlaylistSyncOutboxCompletionTest.kt` (mới, +224). `git diff --check` sạch. |
| Không làm | merge, push, reset/clean/stash/force push; không sửa worktree P0/P1/E95; không sửa mã tải xuống (`BulkDownload`) hay playback |

R01 (bằng chứng phân trang), R02 (`replayPosition`, `CATCH_UP_INCOMPLETE`) và R03 (FLOOD_WAIT không bị cắt) được **giữ nguyên**: các lớp test của chúng vẫn xanh nguyên vẹn (mục 4).

## 2. Lỗi và bằng chứng trước khi sửa

Lỗi (theo `E96-P2-REWORK-01-INDEPENDENT-QA-RESULT`): `sendPhase` hết `maxEnvelopesPerRun` thì trả `null` như xong việc, `runOnce` dọn dẹp rồi trả `SyncStatus.OK` dù còn thao tác `PENDING/RETRY_REQUIRED`; `t25` (5.000 thao tác, ngân sách 5) còn khẳng định `OK` sau khi chỉ gửi 100 (4.900 còn chờ).

Test viết **trước** và chạy trên hành vi cũ (chỉ thêm tên trạng thái `OUTBOX_INCOMPLETE` để test biên dịch được, chưa đổi logic): **7/7 đỏ** — log `01-BEFORE-FIX-new-tests-fail-on-3e0d969-behaviour.log`, `01b-BEFORE-FIX-junit.xml`:

| Test | Thông báo lỗi thật trên mã cũ |
|---|---|
| `o01` (ngân sách 5, 130 thao tác) | `expected:<OUTBOX_INCOMPLETE> but was:<OK>` — và `OK while operations still wait [dev-a#101…]` |
| `o02` (ngân sách 1, 45 thao tác) | `OK while operations still wait [dev-a#21, …]` |
| `o03` (ranh giới đúng/vượt 1) | `expected:<OUTBOX_INCOMPLETE> but was:<OK>` |
| `o04` (ngân sách 3, 200 thao tác, chạy tới OK) | `OK while operations still wait [dev-a#61, …]` |
| `o05` (khởi động lại giữa các lần chạy) | `OK while operations still wait [dev-a#41, …]` |
| `o06` (bản đầu: Save đến lúc gửi) | `expected:<1> but was:<0>` — bản đầu có kịch bản sai (ngân sách còn dư nên lần lặp kế gửi luôn thao tác mới), đã viết lại sau lần chạy này |
| `o07` (ngân sách ≤ 0) | `maxEnvelopesPerRun=0 must be rejected` — cấu hình bất khả thi được chấp nhận |

(`o06` bản viết lại, `o06b` và `o08` được thêm sau lần chạy đỏ này; `o06` và `o08` được kiểm chứng đỏ bằng đột biến ở mục 4, `o06b` cố ý xanh ở cả hai hành vi vì nó khẳng định hành vi đúng khi ngân sách còn dư.)

## 3. Cách sửa

- **Kết quả gửi có kiểu:** `sendPhase` trả `SendOutcome.Stop(báo cáo)` / `Drained` / `BudgetSpent(left)` thay cho `null`. Hết ngân sách thì nhìn lại một lần (tin cuối có thể vừa làm rỗng hàng đợi → `Drained`, đó **là** hoàn tất).
- **Trạng thái mới `SyncStatus.OUTBOX_INCOMPLETE`:** đã đọc xong lịch sử nhưng còn thao tác chờ gửi; `pendingLeft` chính xác (đếm lại sau khi dọn), `envelopesSent` cho biết đã gửi bao nhiêu tin. Tách bạch với `CATCH_UP_INCOMPLETE` (chưa đọc xong lịch sử, vẫn chặn gửi như R02).
- **`OK` xét theo trạng thái thật ở cuối lần chạy**, không theo kế hoạch của lần chạy: cần lịch sử đã đọc hết **và** không còn thao tác chờ. Do đó một Save đến *trong lúc* chạy (kể cả ngay sau bước dọn dẹp) cũng không bị che bởi `OK`; với ngân sách còn dư thì Save đó được gửi luôn trong cùng lần chạy.
- **Không động tới thao tác chưa gửi:** không ACK giả, không dọn, không đổi thứ tự, không bỏ. Chỉ các mục `ACKNOWLEDGED` (do server xác nhận) được dọn như trước. Phần còn lại giữ nguyên `opId`, thứ tự, trạng thái `PENDING`, `attempts=0` và tiếp tục ở lần chạy sau/sau khởi động lại. Thành công và lỗi (BACKOFF, RECOVERY…) giữ nguyên ngữ nghĩa cũ.
- **Từ chối ngân sách bất khả thi:** `SyncConfig` ném `IllegalArgumentException` khi `pageSize`, `maxPagesPerRun` hoặc `maxEnvelopesPerRun` < 1 (trước đây `maxPagesPerRun=0` cũng cho ra "OK" mà không làm gì).

## 4. Kiểm thử

**9 test mới** (`TelegramPlaylistSyncOutboxCompletionTest`, 0 skipped) và `t25` được sửa đúng kỳ vọng (`OUTBOX_INCOMPLETE`, `pendingLeft = 4.900`, 5 tin đã gửi, 4.900 thao tác nguyên thứ tự và id, rồi có chỗ trống để Save/Unsave lại):

| Test | Kiểm |
|---|---|
| `o01` | ngân sách 5, 130 thao tác: lần 1 gửi 100, còn 30, `OUTBOX_INCOMPLETE`; lần 2 gửi 20 + 10 (tin cuối chỉ một phần) → `OK`, đúng 7 tin |
| `o02` | ngân sách 1, 45 thao tác: `INCOMPLETE`, `INCOMPLETE`, `OK`; còn lại 25 → 5 → 0; mỗi thao tác đúng một lần, đúng thứ tự tạo |
| `o03` | ranh giới: 40 thao tác/ngân sách 2 → `OK` ngay (vừa đủ là hoàn tất); 41 → `INCOMPLETE` còn 1 rồi `OK` |
| `o04` | ngân sách 3, 200 thao tác: 140 → 80 → 20 → 0 qua 4 lần chạy; sau mỗi lần phần còn lại đúng là phần đuôi, cùng id/thứ tự, vẫn `PENDING`, `attempts=0` |
| `o05` | khởi động lại giữa các lần chạy (cả hai lần): outbox còn nguyên từng mục; kết thúc `OK`, 100 thao tác đúng thứ tự trên server |
| `o06` | Save đến lúc gửi tin cuối của ngân sách → `INCOMPLETE`, `pendingLeft=1`, lần sau `OK` |
| `o06b` | còn ngân sách → Save đó được gửi cùng lần chạy, `OK` |
| `o07` | ngân sách/trang 0, âm, `Int.MIN_VALUE` bị từ chối |
| `o08` | Save đến ngay sau bước dọn dẹp (qua host bọc) → `INCOMPLETE`, không `OK` giả |

**Độ nhạy:** trả lại logic cũ (luôn `OK` sau gửi, bỏ kiểm tra cấu hình) → 9 test đỏ (`t25`, `o01`–`o05`, `o06`, `o07`, `o08`; `o06b` vẫn xanh như dự kiến), log `03-mutation-sanity-check.log`; mã đã khôi phục và `cmp` khớp.

**Chạy lại toàn bộ trên commit `63ccbe9`:** `.\gradlew.bat --no-daemon :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --rerun-tasks --console=plain` → **BUILD SUCCESSFUL**, `59 actionable tasks: 59 executed`.

| Biến thể | Suites | Tests | Passed | **Skipped** | Failed/Errors |
|---|---|---|---|---|---|
| Debug | 132 | 1.563 | 1.542 | 21 (có sẵn từ P0/E94) | 0 / 0 |
| Release | 132 | 1.563 | 1.542 | 21 | 0 / 0 |

1.563 = 1.554 + 9. Mỗi biến thể, 0 skipped: **P0 58**, **P1 81** (Model 25, StoreV2 21, V2Session 35), **P2 94** (Protocol 18, Coordinator 47, Rework-01 20, Rework-02 9; trước đó 85 + 9). Chi tiết `06-class-results.txt`.

**Test chập chờn không liên quan:** `BulkDownloadTest.testRetryEligibilityForFailedAndCancelled` (biên bản ghi nhận lỗi một lần ở Release). Trong lần chạy `--rerun-tasks` đầy đủ này cả hai biến thể đều xanh (`BulkDownloadTest` 11/11, 0 lỗi). Không sửa mã tải xuống. **Ghi chú để mở ticket riêng (chưa chứng minh nguyên nhân gốc bằng cách tái hiện):** quan sát mã test cho thấy nghi vấn đua luồng thuần trong test — `startDownload(202)` giao việc cho executor một luồng, rồi gọi ngay `cancelDownload(202)`; luồng tải mở `ByteArrayInputStream(1000)` và có thể xong (`OFFLINE_READY`) trước khi lệnh hủy được xử lý, khớp với "expected CANCELLED but was OFFLINE_READY". Đề xuất: ticket ổn định hóa riêng (làm cho việc tải bị giữ bằng một cổng `CountDownLatch` cho tới khi gọi hủy), không thuộc P2.

## 5. Giới hạn còn lại

1. Giữ nguyên các giới hạn đã báo ở REWORK-01: bằng chứng hoàn chỉnh của trang là trách nhiệm của adapter (kiểm tra lần hai chỉ xác nhận ranh giới dưới); không phát hiện được lỗ hổng *giữa* trang; tin trễ dưới cursor cần `FULL_REPLAY` và lịch gọi thuộc P3; trường `checkpoint` thứ tư khi đang replay mà bản dựng cũ đọc sẽ coi là `FORMAT`; chưa snapshot; `REMOVE` >32 tag là `OP_TOO_LARGE`.
2. `OUTBOX_INCOMPLETE` chỉ mô tả; **ai gọi lại và khi nào** (lịch, UI "đang đồng bộ…") thuộc P3. Với mặc định `maxEnvelopesPerRun=50` (1.000 thao tác/lần chạy) một outbox đầy 5.000 cần 5 lần chạy.
3. Chưa chạy với TDLib/Telegram thật, Keystore thật, thiết bị thật; hiệu năng ghi V2 với outbox lớn trên thiết bị chưa đo (mỗi tin gửi là một lần ghi).

## 6. APK QA — `QA BUILD PASS / DEVICE INSTALL BLOCKED`

Build lại từ `63ccbe9`. APK candidate trước (`eac2fbc`) **không** có bản sửa này: chuỗi `OUTBOX_INCOMPLETE` có trong dex của APK mới (1) và không có trong APK `eac2fbc` (0; cùng lệnh tìm `TRANSPORT_CONTRACT` ở APK cũ cho kết quả 1, xác nhận cách kiểm).

| Mục | Debug (bàn giao) |
|---|---|
| Đường dẫn | `D:\Bots\_handoff\MeloVault\E96-CROSS-DEVICE-SYNC\QA_APK_E96_P2_REWORK_02\MeloVault-E96-P2-REWORK-02-63ccbe9-shareable-debug-vc109-NiSigned-NoTelegramConfig-QA-CANDIDATE.apk` (+ `.sha256`) |
| SHA-256 (bản sao QA = file build) | `81838b7c8d22f810a398c18d8537df209f65ea270965047d3787eaae18d98192` |
| Kích thước | 33.876.251 byte |
| Package / version | `app.melovault.player` / vc **109** / `0.6.88-beta` (không bump) |
| Chứng chỉ ký SHA-256 | `a9b8cb6b5b0bc0400955b24dbc85801f5e8855356287caca2dcf3bb4e13db280` |
| Telegram config | **false** (`TELEGRAM_APP_API_ID=0`) · ABI chỉ `arm64-v8a` |
| Release (không bàn giao) | SHA-256 `ec031772b61edb32ef610cabcaf04ea32a37d8553c0e09673f3d60038c4f7416`, 30.505.518 byte |

Không cài được/không dùng để thử Telegram (như các vòng trước): chữ ký Ni ≠ chữ ký Preview `eecd3df7…`, thiếu Telegram config, versionCode 109 trùng Beta109, AVD sẵn `x86_64`. Không sửa signing/versionCode/secrets/ABI, không cài, không `uninstall`/`pm clear`, không upload Drive. Sync core vẫn không được ứng dụng gọi (`SYNC_ENABLED=false`), nên APK không có hành vi mới nào chạy được.

## 7. Rollback

Chưa nối vào ứng dụng nên không có hành vi runtime để rollback: `git revert 63ccbe9` (trở về hành vi `3e0d969`, có lỗi R04) hoặc bỏ nhánh. `SYNC_ENABLED=false` là mặc định. Không đổi định dạng file/khóa/schema.

## 8. Dọn dẹp tài nguyên

- Mọi lệnh Gradle chạy `--no-daemon` (kiểm lại `gradlew --status`: không còn daemon); không emulator, không ADB, không bật màn hình thiết bị (SONY_SCREEN_END/SHANLING_SCREEN_END: không áp dụng).
- Bản sao lưu 1 file dùng cho kiểm tra đột biến (thư mục scratchpad) đã xóa sau khi khôi phục và `cmp` khớp. Dữ liệu test: `TemporaryFolder` của JUnit và bộ nhớ. Log tạm của shell đã sao vào `docs/plans/artifacts/E96-P2-REWORK-02/` (Git bỏ qua) rồi xóa bản tạm.
- Giữ nguyên: báo cáo, log, APK QA của các vòng trước và vòng này (không xóa/ghi đè), các worktree và nhánh. Không đụng tiến trình Agent khác; không `reset --hard`/`clean`/`stash`/force push; không đọc/copy secrets hay `local.properties`; không Telegram thật.

Đã dừng, chờ ChatGPT nghiệm thu độc lập.
