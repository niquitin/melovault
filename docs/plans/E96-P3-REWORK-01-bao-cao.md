# E96-P3-REWORK-01 — BÁO CÁO (Sonnet 5.5)

**Kết luận đề xuất:** R01, R02, R03 đều **READY FOR CHATGPT QA lại** ở cổng *mã nguồn + JVM với TDLib giả*. Không phải PASS thiết bị hay tích hợp TDLib thật. Không merge, không P4, không Telegram thật, không ADB/thiết bị. `SYNC_ENABLED` vẫn `false`.
**APK:** `QA BUILD PASS / DEVICE INSTALL BLOCKED` (mục 7).

## 0. Về việc "không thấy commit REWORK"

Trước phiên này **chưa có** commit hoặc báo cáo REWORK nào ở bất kỳ nhánh/thư mục nào: tôi kiểm tra `git log` của worktree P3 (HEAD đúng `e2e6815`, cây sạch), `git worktree list` (không worktree mới) và thư mục handoff (chỉ có `PROMPT_…REWORK_01…`, chưa có báo cáo). Toàn bộ R01–R03 được làm trong phiên này, trên chính worktree chuẩn, không ghi đè hay reset gì.

## 1. Git

| Mục | Giá trị |
|---|---|
| Worktree / nhánh | `D:\Bots\MeloVault-E96-P3-Tdlib-Integration` / `dot/E96-P3-TDLIB-SYNC-INTEGRATION` |
| Xuất phát | `e2e681579da5b65ffd140edeca28132c300f2195` (đúng dự kiến; cây sạch; không agent/Gradle lạ) |
| **Commit sửa lỗi (HEAD mã nguồn mới)** | **`4fc44e3f6812da5570bf02bab0d000fb848cf473`** |
| Commit báo cáo | liền sau, chỉ thêm bản sao báo cáo này vào `docs/plans/` |
| Diff so với `e2e6815` | 6 file, +667/−58 |
| File production | `TdlibSyncChannel.kt` (+197/−; R01 + R02), `TdlibHistoryReader.kt` (+46/−; R03), `TdlibPlaylistSyncService.kt` (+13/−; chuyển `channelId` đã bind và giới hạn khám phá) |
| File test | `TdlibRework01Test.kt` (mới, 30 test), `TdlibFakeKit.kt` (hạ tầng giả TDLib: danh sách chat, lưu trữ, tải từng phần, lỗi truy vấn xuôi), `TdlibOwnerFanOutTest.kt` (chỉ thêm `getChats` vào danh sách request bị cấm khi công tắc tắt) |
| `git diff --check` | sạch |
| Không làm | merge, push, reset/clean/stash/force push; không sửa worktree E95/P0/P1/P2; không đụng playback, signing, versionCode, cấu hình; không đọc secrets/`local.properties` |

## 2. R01 — khám phá kênh phải CHỨNG MINH sự vắng mặt

**Lỗi:** `discover()` chỉ `searchChats("MeloVault Sync", 50)`, mà `searchChats` là tìm theo *tiêu đề* trên các chat *đã biết* (offline). Kênh có marker nhưng đổi tên, nằm trong lưu trữ, chưa tải, hoặc vượt 50 kết quả → `NotFound` sai → người dùng cho phép tạo sẽ sinh kênh thứ hai và tách đôi dữ liệu.

**Thiết kế mới** (`TdlibChannelDirectory`, ràng buộc cứng trong `DiscoveryLimits`):
1. **Chat đã bind** (checkpoint có `channelId`): xác minh **trực tiếp** chat đó (`getChat`, `getSupergroup`, `getSupergroupFullInfo`), không quét. Không xác minh được → `Unsafe`/`Failed(CHANNEL_MISSING)`, **không bao giờ** `NotFound`. Đã bind thì `createChannel` trả `AlreadyBound` và không gửi request nào.
2. **Chưa bind** → quét vét cạn cả `chatListMain` **và** `chatListArchive`: `loadChats(limit 100)` lặp đến khi TDLib trả **404** ("đã tải hết"), tối đa `maxLoadRounds=60` vòng, rồi `getChats(limit 1000)`; nếu câu trả lời **lấp đầy** giới hạn (có thể bị cắt) → `Incomplete(TOO_MANY_CHATS)`.
3. Mọi supergroup mà tài khoản là **chủ hoặc quản trị** đều được đọc đầy đủ và xét bằng marker trong mô tả (không bao giờ theo tiêu đề); chat chỉ đang *theo dõi*, chat riêng, nhóm cơ bản bị bỏ qua mà không tốn request. Quản trị viên không phải chủ có marker vẫn là `Unsafe(NOT_OWNER)` (không bị bỏ sót khi chuyển quyền sở hữu).
4. Ngân sách tổng `maxRequests=3000` → `Incomplete(BUDGET)`.
5. Bất kỳ lỗi/timeout/giới hạn nào = "chưa biết", **không bao giờ** `NotFound`. `NotFound` chỉ khi mọi bước hoàn tất.
6. **`create()`** từ chối trừ khi vắng mặt đã được chứng minh: `Found/Ambiguous/Unsafe` → `Exists`; `Failed` → `Failed`; `Incomplete` → `DiscoveryIncomplete`; đã bind → `AlreadyBound`. Sau khi tạo vẫn quét lại để phát hiện kênh trùng (hai thiết bị cùng lúc) — kết quả không phải đúng-một-kênh → `NeedsPerson`, không bind.
7. **`bind()`**: kênh đã bind trùng id → xác minh trực tiếp; id khác → `ChannelConflict` (không thay thế, không đặt lại cursor); chưa bind → chỉ bind khi quét vét cạn cho đúng `Found(chatId)` duy nhất.

`searchChats` không còn được dùng.

## 3. R02 — tệp đồng ý được phân tích nghiêm ngặt

`FileSyncConsentStore.load` giờ chỉ chấp nhận **đúng** một tệp mà `save` có thể viết: byte hợp lệ UTF-8, ≤ 256 byte, kết thúc bằng `\n` (tệp bị cắt không có), đúng 5 dòng; dòng đầu **chính xác** `mvconsent v1`; mỗi khoá trong `{enabled, create, transmit, warning}` đúng một lần, dạng `khoá<dấu cách>giá trị`; cờ chỉ `0` hoặc `1`; `warning` là số nguyên chuẩn (`0` hoặc không có số 0 đầu, ≤ 9 chữ số) và **không lớn hơn** phiên bản cảnh báo hiện tại (`1`); và `create`/`transmit` không được bật khi `enabled` tắt (các hành động không bao giờ tạo trạng thái đó). Mọi sai khác → `SyncConsent()` (tất cả OFF). Tệp hợp lệ (mọi tổ hợp mà `save` tạo ra) vẫn đọc đúng (`p02`).

Đã phủ: tiêu đề hỏng nhưng cờ hợp lệ (đúng ví dụ kiểm toán `corrupted\nenabled 1\ncreate 1\ntransmit 1\nwarning 1\n`; cùng 7 biến thể tiêu đề khác), trùng khoá, thiếu khoá, bị cắt, giá trị sai (10 kiểu), khoá thừa/dòng thừa/CRLF/BOM/NUL/khoảng trắng đặc biệt/UTF-8 hỏng/quá lớn, quyền không thể do hành động tạo, và cổng `SyncGate` không mở tạo kênh/gửi với tệp hỏng (`p01`–`p08`).

## 4. R03 — lần nhìn thứ hai phải fail-closed

`crossCheckConflicts` (trả `false` khi lỗi/timeout/trả lời hỏng) được thay bằng kiểu có kiểu **`Look`**: `Verified` / `Conflict` / `Inconclusive(failure)`.
- `Inconclusive` (lỗi, timeout, bị huỷ do đổi danh tính, hoặc trả lời hỏng) → `FetchResult.Failed` (`NETWORK`/`TIMEOUT`/mã lỗi tương ứng): **không phục vụ trang, không có `completeThroughId`, cursor không nhúc nhích, không gửi gì**.
- Snapshot được giữ lại nhưng ở trạng thái **chưa xác minh** (`verified=false`); lần gọi kế tiếp hỏi lại. `Conflict` (có tin thật mà quét ngược thiếu) → `UNPROVEN` như cũ, bỏ snapshot, lần sau quét lại. Chỉ `Verified` mới đánh dấu snapshot đã xác minh; "cái nhìn thứ hai" của coordinator (limit 1) luôn hỏi lại và cũng fail-closed.
- Giữ nguyên: ngân sách snapshot, `completeThroughId` của P2, `verifyPageStart=true`.

## 5. Kiểm thử

**Test mới trước khi vá — chạy trên logic cũ (log `01-BEFORE-FIX-new-tests.log`, `01b-BEFORE-FIX-junit.xml`): 28/30 ĐỎ.** Hai test xanh: `d15` (đối chiếu hiệu năng, trùng hợp xanh trên logic cũ) và `p02` (tính tương thích tệp hợp lệ — phải xanh ở cả hai). Để test biên dịch được trước khi vá, chỉ thêm *tên kiểu* (`Incomplete`, `AlreadyBound`, `DiscoveryIncomplete`, `DiscoveryLimits`, `MARKER_MISSING`), chưa đổi logic.

| Nhóm | Test | Nội dung |
|---|---|---|
| R01 | `d01`–`d15` | đổi tên, lưu trữ+chưa tải, >50 trùng tiêu đề, tải từng phần lỗi, tải không bao giờ xong (bị chặn, đúng 5 vòng), danh sách quá dài, vắng mặt được chứng minh rồi tạo đúng 1 kênh, bind trực tiếp không quét, bind mất/đổi marker, đã bind không tạo, hai thiết bị (máy mới tìm thấy kênh đã lưu trữ), mơ hồ (đổi tên + lưu trữ), vượt ngân sách, kênh chỉ theo dõi không bị đọc mô tả |
| R02 | `p01`–`p08` | như mục 3 |
| R03 | `x01`–`x07` | khuyết tin giữa + truy vấn xuôi lỗi/hỏng/timeout (reader), thử lại: phát hiện lỗ hổng rồi quét sạch hội tụ (`x04`), không lỗ hổng: hỏi lại và hội tụ, "cái nhìn thứ hai" của coordinator, và cả một lần `runSync` đầy đủ: lần 1 BACKOFF, cursor/outbox/không gửi giữ nguyên, lần sau OK và đúng 1 tin trên server (`x07`) |

**Sau khi vá, trên commit `4fc44e3`:** `.\gradlew.bat --no-daemon :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --rerun-tasks --console=plain` → **BUILD SUCCESSFUL in 5m 37s**, `59 actionable tasks: 59 executed`. Log: `03-full-unit-tests.log`; JUnit XML: `junit-debug/`, `junit-release/` (thư mục `docs/plans/artifacts/E96-P3-REWORK-01/`, Git bỏ qua).

| Biến thể | Suites | Tests | Passed | **Skipped** | Failed/Errors |
|---|---|---|---|---|---|
| Debug | 138 | 1.689 | 1.668 | 21 (có sẵn từ P0/E94) | 0 / 0 |
| Release | 138 | 1.689 | 1.668 | 21 | 0 / 0 |

1.689 = 1.659 + **30 mới**. Mỗi biến thể: `TdlibRework01Test` 30/30; **96 test P3 gốc vẫn 96/96** (`TdlibOwnerFanOutTest` 12, `TdlibTransportSendTest` 25, `TdlibHistoryReaderTest` 23, `TdlibChannelConsentTest` 24, `TdlibSyncIntegrationTest` 12), không sửa/bỏ assertion nào của chúng (chỉ thêm `getChats` vào danh sách request bị cấm ở `f11`); tổng P2/P1/P0 cũ không đổi (1.563). `BulkDownloadTest` 11/11 cả hai biến thể, không có hiện tượng chập chờn lần này; không sửa mã tải xuống.

## 6. Giới hạn còn lại (DEFERRED / BLOCKED, chưa PASS thiết bị)

1. **Hành vi thật của `loadChats`/`getChats`**: giả định theo tài liệu — `loadChats` trả lỗi 404 khi đã tải hết, `getChats` trả danh sách từ đầu (tài liệu ghi "chỉ để tham khảo"). Việc `getChats` có giới hạn trên thấp hơn 1000 trong TDLib 1.8.68 **chưa kiểm**; nếu thấp hơn mà trả ít hơn giới hạn đã hỏi thì phép kiểm "lấp đầy" không bắt được cắt xén. Cần xác minh ở P5 với TDLib thật.
2. **Người dùng có ≥1.000 chat trong một danh sách:** không bao giờ chứng minh được vắng mặt → `Incomplete(TOO_MANY_CHATS)` → **không tạo được kênh tự động** (đường duy nhất là P4: để người dùng chọn kênh sẵn có). Chọn fail-closed thay vì đoán. Chi phí ước lượng của một lần quét: `ceil(chat/100)` request tải mỗi danh sách, mỗi chat 2 request (`getChat`, `getSupergroup`), thêm 1 (`getSupergroupFullInfo`) cho mỗi supergroup mà tài khoản sở hữu/quản trị; tối đa 3.000 request.
3. Chat không thuộc danh sách chính hay lưu trữ (ví dụ đã rời) không liệt kê được; kênh chủ sở hữu đã xoá thì không còn tồn tại. Kênh mà tài khoản chỉ còn là thành viên thường sẽ bị bỏ qua (không phải của mình).
4. **Chưa thử** TDLib/JNI thật, Telegram thật, Keystore, thiết bị, âm thanh. Mọi kết quả ở đây là mô phỏng.
5. Các giới hạn cũ của báo cáo P3 (mục 9) vẫn đúng: `getChatHistory` thật, `1 shl 20`, chuẩn hoá văn bản, tên trường JSON chưa đối chiếu với `td_api.tl`, quy mô >20.000 tin chưa đọc.
6. Tệp đồng ý ghi bởi bản build tương lai với `warning` > 1 bị bản này coi là không hỗ trợ → OFF (chủ đích, fail-closed).

## 7. APK QA — `QA BUILD PASS / DEVICE INSTALL BLOCKED`

Build lại từ `4fc44e3` (cây sạch). APK cũ `443dbe5` **không** có bản vá này (SHA-256 `dfb2b358…6210`) và không được dùng cho vòng nghiệm thu này.

| Mục | Debug (bàn giao) |
|---|---|
| Đường dẫn | `D:\Bots\_handoff\MeloVault\E96-CROSS-DEVICE-SYNC\QA_APK_E96_P3_REWORK_01\MeloVault-E96-P3-REWORK-01-4fc44e3-shareable-debug-vc109-NiSigned-NoTelegramConfig-QA-CANDIDATE.apk` (+ `.sha256`) |
| SHA-256 | `818d0b56edd3b59ca7d9f71cb61937ed0d3c45e595064e2ee279e6c1efd71075` |
| Kích thước | 33.977.985 byte |
| Package / version | `app.melovault.player` / vc **109** / `0.6.88-beta` (không bump) |
| Chứng chỉ ký SHA-256 | `a9b8cb6b5b0bc0400955b24dbc85801f5e8855356287caca2dcf3bb4e13db280` (Android Debug của Ni) |
| Telegram config | **false** (`TELEGRAM_APP_API_ID` bằng 0, hash rỗng — chỉ kiểm boolean) · ABI `arm64-v8a` |
| Release (không bàn giao) | SHA-256 `b833b6115722be8ac1283996e4bd942228da6e96125e57e58142079e0daf5d58`, 30.554.670 byte, cùng chứng chỉ debug |

Không cài được/không dùng thử Telegram (như vòng trước): chữ ký Ni ≠ bản Preview đang cài, thiếu Telegram config, versionCode 109 trùng Beta109, chưa được phép dùng thiết bị. Không sửa signing/versionCode/cấu hình, không ADB, không `uninstall`/`pm clear`, không upload Drive. Vì công tắc build tắt và không mã nào dựng `TdlibPlaylistSyncService`, APK không có hành vi sync nào chạy được.

## 8. Rollback

`git revert 4fc44e3` đưa về `e2e6815` (có ba lỗi của kiểm toán) hoặc bỏ nhánh. Không có hành vi runtime sync cần rollback (công tắc `false`, không nơi nào trong app gọi service). Không đổi định dạng tệp: tệp đồng ý cũ do `save` ghi vẫn hợp lệ với bộ phân tích mới.

## 9. Dọn dẹp tài nguyên

- Mọi lệnh Gradle `--no-daemon`; `gradlew --status`: không còn daemon; không emulator, không ADB, không bật màn hình thiết bị (SONY_SCREEN_END/SHANLING_SCREEN_END: không áp dụng).
- Tệp tạm của phiên (script soạn thảo, mẫu ghép) nằm ở thư mục tạm/scratchpad; không có tệp tạm trong repo (`git status` sạch). Không dùng `checkout`/`reset`/`clean`/`stash`. Dữ liệu test: `TemporaryFolder` và bộ nhớ.
- Giữ nguyên: báo cáo, log (gồm log đỏ trước khi vá), JUnit XML, APK QA các vòng, worktree và nhánh. Không đụng tiến trình Agent khác, không đọc/sao chép secrets hay `local.properties`, không Telegram thật.

Đã dừng, chờ ChatGPT nghiệm thu độc lập.
