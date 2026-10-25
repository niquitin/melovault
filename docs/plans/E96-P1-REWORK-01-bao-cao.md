# E96-P1-REWORK-01 — BÁO CÁO SỬA LỖI HÒA GIẢI V1/V2 (Sonnet 5.5)

**Kết luận đề xuất: PASS CANDIDATE (cổng mã nguồn + JVM)** cho hai lỗi trong biên bản `E96-P1-INDEPENDENT-QA-REWORK-20261023.md`. Chờ ChatGPT nghiệm thu lại. Không merge, không P2, không đụng Telegram thật, thiết bị thật hay emulator.

## 1. Git

| Mục | Giá trị |
|---|---|
| Nhánh / worktree | `dot/E96-P1-PLAYLIST-V2-LOCAL` — `D:\Bots\MeloVault-E96-P1-Playlist-V2` |
| Xuất phát | `4009027` (cây sạch, không tiến trình lạ) |
| **Commit sửa lỗi** | **`52a8828befac3d505cfd294eced61204e4674eef`** |
| Commit báo cáo | liền sau, chỉ thêm file này |
| Phạm vi diff so với `4009027` | 4 file, +194/−19 — `TelegramPlaylistV2Session.kt` (+62/−18), `TelegramPlaylistSession.kt` (+2/−1), `TelegramPreviewActivity.kt` (+13), `TelegramPlaylistV2SessionTest.kt` (+117). Không file ngoài phạm vi; `git diff --check` sạch. |

## 2. Lỗi đã sửa và cách sửa

**REWORK-01 — Outbox đủ 5.000 thao tác + V1 bị APK cũ thêm bài → `OutboxFullException` thoát khỏi `open()`.**
Chuỗi cũ: `reconcileProjection → reconcileDivergence → PlaylistV1Reconciler.reconcile → addTelegram` ném ngoại lệ; `TelegramPlaylistRuntime.submit` nuốt nó nên màn hình không nhận kết quả nào.
Sửa:
- `PlaylistV1Reconciler.reconcile` giờ trả kiểu có định danh `Outcome.Merged | Outcome.OutboxFull` và tự bắt `OutboxFullException`. **Tất cả hoặc không gì cả**: hết chỗ trong hàng đợi thì không nhập bài nào (không nhập dở rồi ghi đè V1 làm rơi phần còn lại). Bài không cần thao tác (khóa legacy chưa xác minh) vẫn nhập được khi hàng đợi đầy.
- `reconcileDivergence` khi `OutboxFull` trả `Pass(trạng thái V2 cũ, IMPORT_DEFERRED_OUTBOX_FULL)`: **không ghi V2, không ghi V1, không xóa/đổi outbox**.
- `open()` trả `OpenResult.Ready(ids, warning)`: playlist đã lưu vẫn hiển thị, kèm cảnh báo rõ (trường `warning` có mặc định `null` nên các test/hợp đồng P0 không đổi).
- Khi đang hoãn, **V1 không bị thay** (`importDeferred`): kể cả khi người dùng thao tác một Save không cần chỗ trong hàng đợi (khóa legacy), bài do APK cũ thêm vẫn còn nguyên trong V1 cho lần nhập sau. Thao tác cần chỗ trong hàng đợi bị từ chối bằng `ToggleOutcome.OutboxFull` như trước (không bỏ gì).
- Lần mở sau (hoặc khi hàng đợi đã có chỗ, việc của P2) hòa giải chạy lại từ cùng V1 và nhập bài.

**REWORK-02 — Hòa giải không lưu được V2 (I/O hoặc mã hóa) mà vẫn báo `Ready` như không có gì.**
Sửa: `trySave(merged)` thất bại → `Pass(trạng thái cũ, IMPORT_NOT_SAVED)`; `open()` trả `Ready` **kèm cảnh báo**; hai file giữ nguyên byte-từng-byte; V1 không bị thay.

**Lưới an toàn (cùng nguồn gốc "im lặng"):** `open()` bọc toàn bộ nạp/di trú/hòa giải bằng bắt ngoại lệ bất ngờ → trả `Blocked(IO)` thay vì để ngoại lệ bị nuốt. Tại thời điểm đó chưa ghi gì, nên hai file nguyên vẹn; lần `toggle` sau thử đọc lại như P0.

**Giao diện:** `showPlaylistImportDeferred` hiện thông báo + toast: "…chưa nhập được vì hàng đợi thay đổi đang đầy. Không bài nào bị mất; ứng dụng sẽ thử lại ở lần mở sau." hoặc "Chưa lưu được các thay đổi Playlist từ bản ứng dụng cũ. Không bài nào bị mất; hãy mở lại ứng dụng để thử lại." Không có chữ nào nói về đồng bộ cloud.

## 3. Regression test mới (5, đều 0 skipped)

| Test | Kiểm |
|---|---|
| `aFullOutboxWithANewSongInV1IsDeferredWithAWarningNotAnExceptionAndRecoversAfterARestart` | Đúng kịch bản biên bản: V2 hợp lệ có đúng 5.000 op PENDING + V1 có bài mới *resolvable* do APK cũ thêm. `open()` **không ném**, trả `Ready([…], IMPORT_DEFERRED_OUTBOX_FULL)`; V1 và V2 giữ **byte-từng-byte**; outbox vẫn 5.000; một Save legacy trong lúc hoãn **không** thay V1 (bài của APK cũ còn trong V1); Save cần chỗ → `OutboxFull`; sau khi hàng đợi có chỗ + khởi động lại: nhập đúng 1 bài, V1 = bản chiếu hợp nhất, `conflictImported=1`, đúng 1 op mới |
| `aV2WriteFailureWhileImportingIsReportedLeavesBothFilesAloneAndRecovers` | Lỗi I/O ghi V2: `Ready(…, IMPORT_NOT_SAVED)`, V1+V2 nguyên byte; khởi động lại khi I/O ổn → nhập thành công |
| `anEncryptionFailureWhileImportingIsReportedLeavesBothFilesAloneAndRecovers` | Lỗi mã hóa khi ghi V2: cùng kết quả như trên, rồi phục hồi sau restart |
| `anUnexpectedErrorWhileOpeningComesBackAsAnAnswerAndWritesNothing` | Ngoại lệ bất ngờ → `Blocked(IO)`, không file V2 nào được tạo, không `save` |
| `theReconcilerReportsAFullQueueInsteadOfThrowingAndLeavesTheStateAlone` | Bộ hòa giải thuần: hàng đợi đầy → `Outcome.OutboxFull`; khóa không cần chỗ vẫn `Merged`; outbox không đổi |

**Độ nhạy:** tạm trả lại hành vi cũ (ném `OutboxFullException` trong bộ hòa giải) → 2/35 test của lớp đỏ (`aFullOutbox…`, `theReconciler…`); đã khôi phục, `cmp` khớp (log `03-mutation-sanity-check.log`). Chạy lại toàn bộ trên mã đã commit sau đó (mục 4).

## 4. Kết quả chạy lại toàn bộ

Lệnh (trên commit `52a8828`): `.\gradlew.bat --no-daemon :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --rerun-tasks --console=plain` → **BUILD SUCCESSFUL**, `59 actionable tasks: 59 executed` (không dùng cache).

| Biến thể | Tests | Passed | **Skipped** | Failed/Errors |
|---|---|---|---|---|
| Debug | 1.469 | 1.448 | 21 (có sẵn từ trước, không phải của rework) | 0 / 0 |
| Release | 1.469 | 1.448 | 21 | 0 / 0 |

1.469 = 1.464 (P1) + 5 mới. Cả hai biến thể, 0 skipped: **58 test P0** (Policy 8, Session 15, ViewPolicy 7, SavedPlaylistStore 23, SongsByKeysSql 5) và **76 test P1 trước rework** (Model 25, StoreV2 21, V2Session 30 → nay 35 với 5 test mới) đều xanh. Chi tiết: `docs/plans/artifacts/E96-P1-REWORK-01/04-class-results.txt`.

## 5. Chưa kiểm / giới hạn

- Không build APK mới: yêu cầu rework chỉ gồm sửa lỗi + test. **APK QA cũ `…2dc45a3…` KHÔNG chứa bản sửa này** (SHA-256 `1ffea5b5…81f6c` vẫn là của `2dc45a3`); khi cần APK phải build lại từ `52a8828`. Giới hạn ký/Telegram/ABI/versionCode như báo cáo P1 (`QA BUILD PASS / DEVICE INSTALL BLOCKED`).
- Cảnh báo UI chỉ được biên dịch, chưa thấy trên màn hình; Keystore thật, thiết bị thật (FYT/Sony/Shanling), kill tiến trình thật: DEFERRED / NOT TESTED. Playback/âm thanh không đổi, CHƯA XÁC NHẬN.
- Khi `Ready` kèm cảnh báo, cảnh báo chỉ hiện một lần lúc mở (không nhắc lại ở mỗi Save). Hàng đợi đầy vẫn là điểm dừng cứng cho tới khi P2 xả hàng đợi.

## 6. Rollback

`git revert 52a8828` (trở về hành vi `4009027`, có lỗi đã nêu) hoặc `TelegramPlaylistFlags.V2_ENABLED=false` (về phiên P0). Không có thay đổi định dạng file/khóa/schema nên không cần di trú ngược.

## 7. Dọn dẹp tài nguyên

- Mọi lệnh Gradle chạy với `--no-daemon`: không để daemon; không emulator, không ADB, không bật màn hình thiết bị (SONY_SCREEN_END/SHANLING_SCREEN_END: không áp dụng).
- Bản sao lưu dùng cho kiểm tra đột biến (thư mục scratchpad của phiên) đã xóa sau khi khôi phục và `cmp` khớp. Log tạm trong thư mục tạm của shell sao vào `docs/plans/artifacts/E96-P1-REWORK-01/` (Git bỏ qua) rồi xóa bản tạm.
- Giữ nguyên: báo cáo, log, APK QA cũ (không xóa/ghi đè), worktree P0 + P1, nhánh. Không đụng tiến trình Agent khác; không `reset --hard`/`clean`/`stash`/force push; không đọc/copy secrets hay `local.properties`.

Đã dừng, chờ ChatGPT nghiệm thu lại.
