# E96-P3 — TDLIB SYNC INTEGRATION: BÁO CÁO (Sonnet 5.5)

**Kết luận đề xuất:** từng cổng con ở mục 2 là **PASS CANDIDATE** cho phạm vi *mã nguồn + JVM với TDLib giả*. Không phải PASS thiết bị, không phải PASS tích hợp TDLib thật. Không merge, không P4, không Telegram thật, không cài APK, không ADB.
**APK:** `QA BUILD PASS / DEVICE INSTALL BLOCKED` (mục 8).

## 1. Git

| Mục | Giá trị |
|---|---|
| Nhánh / worktree | `dot/E96-P3-TDLIB-SYNC-INTEGRATION` / `D:\Bots\MeloVault-E96-P3-Tdlib-Integration` |
| Xuất phát | `302b6a50c9f3891d96d47d2d2e0e04254ba695ee` (đúng HEAD P2 đã PASS; worktree P2 sạch; không agent/Gradle lạ) |
| **Commit mã nguồn** | **`443dbe5c580a02b4040da5d322bc85f71414d65b`** |
| Commit báo cáo | liền sau, chỉ thêm bản sao báo cáo này vào `docs/plans/` |
| Diff so với P2 | 12 file, +3.251/−17: 6 file production (`ShareableTdlibOwner.kt` sửa; `TdlibSyncHub.kt`, `TdlibHistoryReader.kt`, `TdlibPlaylistSyncTransport.kt`, `TdlibSyncChannel.kt`, `TdlibPlaylistSyncService.kt` mới) và 6 file test mới. `git diff --check` sạch. |
| Không làm | merge, push, reset/clean/stash/force push; không đụng worktree E95/P0/P1/P2, production, Private Stable, backend; không đọc/in `local.properties`, secrets; không thay đổi signing/versionCode |
| Ghi chú | Repo chưa cấu hình `user.name/email`; commit dùng đúng danh tính của các commit trước (`Claude Code <noreply@anthropic.com>`) chỉ cho lệnh commit, không sửa cấu hình git. |

## 2. Kết luận theo cổng con

| Cổng | Kết luận | Điều kiện / giới hạn chính |
|---|---|---|
| P3-01 Gateway & router | **PASS CANDIDATE** | Chỉ với TDLib giả. JNI thật, thứ tự sự kiện thật: DEFERRED. |
| P3-02 Adapter lịch sử (an toàn dữ liệu) | **PASS CANDIDATE** | Chứng minh "tiền tố hoàn chỉnh cũ nhất" bằng quét ngược; giới hạn 20.000 tin chưa đọc/lần quét (vượt → fail-closed); hành vi `getChatHistory` thật chưa kiểm. Không phải BLOCKED. |
| P3-03 Gửi + xác nhận | **PASS CANDIDATE** | Tin tạm ≠ ACK; chuẩn hoá văn bản thật của TDLib chưa kiểm (hệ quả an toàn: `Unknown`, rồi khôi phục bằng đọc lịch sử). |
| P3-04 Kênh, đồng ý, quyền sở hữu | **PASS CANDIDATE (chỉ mã + giả)** | Tạo kênh thật: **BLOCKED / DEFERRED** (chưa được phép). UI đồng ý thuộc P4. |
| P3-05 Vòng đời & trigger | **PASS CANDIDATE** | Mặc định tắt; chưa có gì trong app gọi tới. Không có receiver/service/worker/timer mới. |
| P3-06 Test | **PASS CANDIDATE** | 96 test mới (≥30 yêu cầu), 0 skipped. |
| Thiết bị / tích hợp thật | **DEFERRED / BLOCKED** | Không có Shanling/Sony/ADB/Telegram thật trong phiên này. P5 sở hữu. |

## 3. Kiến trúc và vòng đời

```
UI (TelegramPreviewActivity) ──┐
Playback (ShareablePlaybackService) ─┤   ShareableTdlibOwner  ← MỘT client, MỘT vòng nhận (không đổi)
Sync sink (P3, tùy chọn) ──────┘     └ TdlibOwnerCore(port: TdlibJsonPort)   ← cổng tiêm được; thật = NativeTdlibJsonPort(JNI)

 luồng nhận native ──dispatch──▶ @extra "mvsync:*"   → CHỈ sync sink (UI/Playback không thấy)
                                 sự kiện khác        → playback, UI như cũ; thêm bản sao cho sync nếu thuộc danh sách lọc
                                 sync: hàng đợi có giới hạn (512) → 1 luồng "MeloVault-Sync-Dispatch" (chỉ sống khi đang bind)
                                       đầy → onOverflow: hủy mọi thứ đang chờ (fail-closed); luồng nhận KHÔNG bao giờ chờ

 worker thread ─▶ TdlibPlaylistSyncService (1 lần chạy/lần gọi, tryLock → BUSY)
                    ├ SyncGate: SYNC_ENABLED (công tắc build) + đồng ý theo tài khoản (mặc định OFF)
                    ├ TdlibSyncHub.call(): chặn có giới hạn thời gian, tag "mvsync:<loại>:<epoch>:<seq>"
                    ├ TdlibHistoryReader  (quét ngược + snapshot + kiểm tra chéo xuôi)
                    ├ TdlibPlaylistSyncTransport (getMe, sendMessage + chờ updateMessageSendSucceeded)
                    └ PlaylistSyncCoordinator (P2, không đổi) ▶ PlaylistSyncHost = TelegramPlaylistV2Session (P1, không đổi)
```

Quy tắc sở hữu: sync **không sở hữu** client. Khi UI và Playback cùng rời đi, client đóng dù sync còn bind (`f06`); sink được báo `onClientClosed`. `bindSync` chỉ cho một sink (không cướp sự kiện, `f07`). `unbindSync`/`close` giải phóng sink, luồng dispatch và mọi yêu cầu chờ; UI/Playback không bị ảnh hưởng (`f05`). Không giữ Activity trong singleton. Không BOOT_COMPLETED, không foreground service, không WorkManager, không vòng nhận thứ hai, không client thứ hai (`f04`: `clientsCreated == 1`).

Thay đổi trong mã sản xuất đang chạy: **chỉ `ShareableTdlibOwner.kt`**, chuyển thân hàm vào `TdlibOwnerCore` (cùng logic attach/detach/bind/close, cùng vòng nhận, cùng kiểm `@client_id`, cùng thứ tự gọi playback → UI), `object ShareableTdlibOwner` giữ nguyên chữ ký công khai. Thêm: bộ đếm `authEpoch` (tăng khi attach client mới, `markGetMeVerified`, `revokeAuthorization`, đóng client), sync sink/pump, và việc sự kiện có tag `mvsync:` không tới UI/Playback (cần, vì UI hiện hiển thị `error` có tag lạ như lỗi đăng nhập). Không sửa Activity, Service, EQ/DSP/MediaSession/FYT.

## 4. P3-02 — chứng minh hợp đồng "tiền tố hoàn chỉnh cũ nhất"

TDLib trả **mới nhất trước** (id giảm dần) và có thể trả **ít hơn** `limit`. Adapter:
1. **Quét ngược** từ `from_message_id=0`, mỗi lần tiếp theo bắt đầu từ id thấp nhất đã thấy; tiếp tục khi còn tiến triển. Trang ngắn **không** là hết lịch sử.
2. **Ranh giới dưới phải được nhìn thấy:** một tin có id ≤ cursor; hoặc (cursor = 0) một tin có id ≤ `1 shl 20` (tin đầu tiên mà một kênh có thể có: server id 1 dịch 20 bit). Trả lời rỗng hoặc không đi xa hơn **không** được coi là đầu lịch sử: sau 3 lần liên tiếp → `Failed(NETWORK)` (BACKOFF), cursor không đổi (`r09`, `r11`).
3. Chỉ sau khi có bằng chứng mới dựng danh sách tăng dần, trả `limit` tin cũ nhất trên cursor; `completeThroughId` = id tin cuối của trang (còn thêm: `hasMore`), hoặc id mới nhất đã quét. Snapshot có giới hạn (20.000 tin / 12M ký tự) phục vụ các trang kế trong cùng lần chạy; kết thúc cùng lần chạy/TTL 120 giây, lần chạy sau quét lại nên tin mới đến không bao giờ bị tuyên bố đã đọc (`r08`, `r19`).
4. **Kiểm tra chéo độc lập** (truy vấn xuôi `from=cursor, offset=-99`): khi tạo snapshot và với mỗi "cái nhìn thứ hai" của coordinator (`limit=1`). Một tin thật trong (cursor, top] mà quét ngược không có = lỗ hổng → huỷ snapshot, `UNPROVEN` → coordinator từ chối, cursor đứng yên (`r13`). Đây là bổ sung cho giới hạn P2 "không phát hiện lỗ hổng giữa trang".
5. **Fail-closed** (trả `UNPROVEN` hoặc `Failed`): tin của chat khác, JSON sai/null/id 0 (`r12`), tin sync bị sửa (`edit_date>0`, `r14`), cảnh báo xoá/sửa chưa xác nhận (`r14`, `r15`), vượt ngân sách bộ nhớ (`r17`), quá nhiều request/lần gọi → BACKOFF **và quét tiếp từ chỗ đang dừng** chứ không bắt đầu lại (`r18`), lỗi kênh/FLOOD/timeout đúng loại (`r21`).

**Chi phí:** một request/~trang chưa đọc mỗi lần quét + 1 `getChat` + 1 kiểm tra chéo. Chạy tăng dần thường 3 request (`r07`). Đọc đầy đủ 10.500 tin: 105–125 request quét ngược; toàn bộ `getChatHistory` của hai lần chạy FULL_REPLAY < 700 (`r04`, `i06`).

**Đo bằng test:** `r22` chạy 15 hạt giống ngẫu nhiên (số tin 120–519, xoá ngẫu nhiên ~20%, trang tối đa 1–100, limit ngẫu nhiên) và khẳng định ở **mọi** trang: không có tin nào bị bỏ dưới điểm chứng minh, và kết quả cuối đúng bằng sự thật. `readAll` (dùng ở r01–r19) kiểm lời hứa hoàn chỉnh so với sự thật trên từng trang.

**Không cần BLOCKED**, nhưng ghi rõ giới hạn chưa chứng minh: (a) hơn 20.000 tin chưa đọc trong một lần quét → `UNPROVEN` (RECOVERY_REQUIRED). Phương án đã nêu cho P3.5/P4 nếu cần: quét ngược có điểm kiểm tra bền (durable reverse checkpoint) hoặc nén log; (b) nếu tin tạo kênh (id `1 shl 20`) bị xoá và cursor = 0 thì không chứng minh được đầu lịch sử → BACKOFF mãi, chọn fail-closed thay vì đoán.

Chỉ báo FULL_REPLAY: dùng `replayPosition` của P2, tiếp tục qua nhiều lần chạy và khởi động lại, không đói (`i05`: 3 lượt, vị trí tăng; `i06`: 10.500 tin trong ≤3 lần chạy, mỗi tin đọc đúng một lần). `verifyPageStart` giữ `true`.

## 5. P3-03 — gửi và xác nhận

- Chỉ `sendMessage` với `inputMessageText`, `disable_notification=true`. Không `editMessageText`, media, audio, đường dẫn, thông tin đăng nhập (`s09`: các văn bản không bắt đầu bằng marker bị từ chối bằng `require`, không tới TDLib; không có `edit*`/`delete*`/album).
- Tin đầu tiên của TDLib là tin **tạm** → không phải ACK. `Sent(serverId)` chỉ khi `updateMessageSendSucceeded` khớp: cùng chat, `old_message_id` = id tạm, id cuối khác id tạm, không còn `sending_state`, **cùng văn bản** (`s01`, `s08`). Cập nhật đến **trước** hoặc **sau** trả lời tạm đều được (`s02`; bộ đệm `SendWatch` đăng ký trước khi gửi).
- Không có cập nhật thành công / mất trả lời / hết thời gian → `Unknown`, `opId` giữ nguyên, coordinator tìm lại bằng đọc lịch sử (`s03`, `s04`, `s14`; `i03` qua khởi động lại tiến trình: đúng 1 tin trên server, không gửi lần hai).
- `updateMessageSendFailed` và trả lời lỗi: `Failed`; 429/FLOOD_WAIT mang **đúng số giây của server** (35, 61, 86.400 không bị cắt; thiếu số mới dùng mặc định 30 s, không bao giờ 0) (`s05`, `s06`, `h10`); `i04` xác nhận backoff đúng 35.000 ms, 34.999 ms vẫn `BACKOFF_ACTIVE`, không request nào trong lúc chờ.
- Cập nhật trùng, thất bại muộn sau thành công, đồng thời hai lần gửi: không đổi kết quả, mỗi lần nhận id server riêng (`s07`, `s13`, `s15`).
- **Lỗi thật tìm thấy trong lúc test và đã sửa:** một cập nhật *thất bại muộn* có thể ghi đè bằng chứng *thành công* trước khi luồng chờ kịp đọc (test `s07` chập chờn). `SendWatch.offer` giờ không bao giờ ghi đè `Succeeded`; `s15` kiểm xác định (kiểm bằng đột biến, mục 7).

## 6. Cổng danh tính, kênh, đồng ý

- **Danh tính:** mọi gửi/đọc/khám phá kiểm `authorizedAccount()` và `authEpoch()` **trước khi gửi** và **lúc nhận trả lời**; `getMe` phải trùng tài khoản (một lần/transport). Trả lời tới sau đổi tài khoản, đăng xuất, đăng nhập lại **cùng tài khoản**, hoặc A→B→A bị bỏ (`h04`, `h05`, `h06`, `h07`, `s12`, `c22`); trạng thái xác thực khác `Ready` huỷ yêu cầu đang chờ và chặn việc mới (`h08`); kết nối chưa sẵn sàng → không gửi (`h09`). `call()` từ luồng dispatch bị từ chối (`h03`).
- **Đồng ý (3 quyền tách biệt, mặc định OFF theo tài khoản, thêm công tắc build):** (a) bật đồng bộ — cần cảnh báo "lưu trên Telegram cloud, KHÔNG mã hoá đầu-cuối, siêu dữ liệu playlist" đã hiển thị và chấp nhận; (b) tạo kênh mới; (c) ghi metadata. (b),(c) cần (a). Thu hồi → tất cả OFF; tệp lỗi → OFF (`c01`–`c04`).
- **Khám phá kênh** theo định danh giao thức, không theo tiêu đề: dòng đầu mô tả = `melovault-sync v1 <scope>` (scope = băm 16 hex của tài khoản, không phải số tài khoản), cộng kiểm cấu trúc: kênh broadcast, tài khoản là `creator`, không username công khai, ≤1 thành viên, tự xoá = 0, không liên kết chat. Trùng/mơ hồ → `Ambiguous`; giả mạo hoặc sai cấu trúc → `Unsafe`; không thấy → `NotFound`. **Không bao giờ tự tạo** (`c05`–`c09`, `c18`).
- **Tạo kênh** chỉ qua `createChannel` (cần (a)+(b)): khám phá trước và từ chối nếu đã có; request `createNewSupergroupChat{title, is_forum=false, is_channel=true, description≤255 có marker và cảnh báo, message_auto_delete_time=0, for_import=false}` (tên trường theo tài liệu chính thức của lớp `createNewSupergroupChat`, xem mục 9); sau tạo xác minh lại và tìm bản trùng. Không trả lời → `Unknown`, **không bao giờ tạo lại** (`c10`–`c14`). Hai thiết bị cùng khởi tạo: kết thúc với hai kênh, thiết bị thấy hai kênh nhận `NeedsPerson(Ambiguous(2))`, không ai bind (`c13`).
- **Bind** vào checkpoint chỉ sau khi xác minh *và* là kênh duy nhất; không thay kênh khác (`ChannelConflict`), không đặt lại cursor/outbox; lưu qua V2 và còn sau khởi động lại (`c15`–`c17`). Kênh biến mất → `RECOVERY_REQUIRED(CHANNEL_MISSING)`, không tạo lại, không gửi, checkpoint/outbox nguyên (`c21`).
- Công việc chờ gửi mà thiếu (c): `Denied(TRANSMIT_NOT_ALLOWED)`, **0 request** (`c20`); transport còn chốt chặn thứ hai `sendAllowed`.
- **Chứng cứ liên tục của log:** xoá vĩnh viễn trong kênh đã bind hoặc sửa (`updateMessageEdited`, `edit_date` của tin sync) → cảnh báo; đọc tiếp là `RECOVERY_REQUIRED(TRANSPORT_CONTRACT)` với V2/outbox nguyên, không gửi gì, đến khi người dùng `acknowledgeContinuity()` (`i08`). Không xoá tombstone/log. Giới hạn: chỉ quan sát được khi sink đang bind.
- **Hook thủ công cho "tin trễ dưới cursor":** `runSync(account, FULL_REPLAY)`; `i07` chứng minh đọc tăng dần không thấy, FULL_REPLAY thấy. Không có job tự chạy.

## 7. Test

Lệnh: `.\gradlew.bat --no-daemon :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --rerun-tasks --console=plain` trên `443dbe5` → **BUILD SUCCESSFUL**, `59 actionable tasks: 59 executed`. Log và JUnit XML: `docs/plans/artifacts/E96-P3/` (Git bỏ qua).

| Biến thể | Suites | Tests | Passed | **Skipped** | Failed/Errors |
|---|---|---|---|---|---|
| Debug | 137 | 1.659 | 1.638 | 21 (có sẵn từ P0/E94) | 0 / 0 |
| Release | 137 | 1.659 | 1.638 | 21 | 0 / 0 |

1.659 = 1.563 (P2) + **96 mới**, mỗi biến thể, 0 skipped trong 96 test mới. P0 (58), P1 (81), P2 (94) giữ nguyên: tổng cũ không đổi, `TelegramPlaylistSyncCoordinatorTest` 47/47 và `TelegramPlaylistV2SessionTest` 35/35 xanh ở cả hai biến thể (chưa tách lại từng lớp P0/P1/P2 trong lần chạy này ngoài hai lớp trên).

| Lớp (cả Debug lẫn Release) | Tests | Nội dung |
|---|---|---|
| `TdlibOwnerFanOutTest` | 12 | fan-out UI/playback/sync, bind/unbind, sở hữu client, tràn hàng đợi, công tắc OFF khi khởi động |
| `TdlibTransportSendTest` | 25 | tương quan `@extra`, cổng danh tính, A→B→A, đăng nhập lại, gửi tạm vs thành công, lỗi/429, trùng, đồng thời |
| `TdlibHistoryReaderTest` | 23 | quét ngược, trang ngắn, >250, >10.000, hở/trùng/chồng, tin đến giữa lúc quét, rỗng, JSON sai, cảnh báo, 15 hạt giống |
| `TdlibChannelConsentTest` | 24 | đồng ý, khám phá, mơ hồ, không tự tạo, đua hai thiết bị, bind, kênh mất, không có đường vào từ mã sản xuất (quét nguồn) |
| `TdlibSyncIntegrationTest` | 12 | hai thiết bị hội tụ gồm tombstone, không rò rỉ giữa tài khoản, khôi phục sau kill, FLOOD, FULL_REPLAY, 10.500 tin, tin trễ, trigger gộp |

**Độ nhạy bằng đột biến** (mã khôi phục và `cmp` khớp sau mỗi lần; log trong scratchpad của phiên, chưa lưu vào repo):

| Đột biến | Test đỏ |
|---|---|
| coi "không tiến triển" là hết lịch sử | `r09`, `r11` |
| tắt kiểm tra chéo xuôi | `r13` |
| ACK ngay khi có tin tạm (không chờ cập nhật) | `s01`, `s02`, `s03`, `s05`, `s08`, `s13`, `i03`, `i04` |
| cho cập nhật thất bại ghi đè thành công | `s15` |
| bỏ kiểm tra danh tính lúc nhận trả lời | chỉ `s13` đỏ; `h04`/`s12` vẫn xanh vì còn lớp bảo vệ thứ hai (`route()` so epoch) — hai lớp, nên đột biến một lớp không đủ làm đỏ |
| ranh giới quét = "dừng sau trang đầu" (kiểu newest-first ngây thơ) | 29 test đỏ; 12 tên đầu tiên đã ghi lại: `c22`, `r03`, `r04`, `r06`, `r11`, `r13`, `r14`, `r15`, `r18`, `r19`, `r20`, `r21` |

**Test chập chờn không liên quan:** `BulkDownloadTest` xanh 11/11 ở cả Debug lẫn Release trong lần chạy này; không sửa mã tải xuống. Ghi chú nghi vấn đua luồng của P2 giữ nguyên, thuộc ticket riêng.

**Test chập chờn do mã của P3 (đã sửa):** `s07` — xem mục 5.

## 8. APK QA — `QA BUILD PASS / DEVICE INSTALL BLOCKED`

| Mục | Debug (bàn giao) |
|---|---|
| Đường dẫn | `D:\Bots\_handoff\MeloVault\E96-CROSS-DEVICE-SYNC\QA_APK_E96_P3\MeloVault-E96-P3-443dbe5-shareable-debug-vc109-NiSigned-NoTelegramConfig-QA-CANDIDATE.apk` (+ `.sha256`) |
| Build từ | commit mã nguồn `443dbe5` (cây sạch khi build) |
| SHA-256 | `dfb2b358e8a2dac424cab682f728440a9fee9a4a69a750395b9abad46fce6210` |
| Kích thước | 33.263.652 byte |
| Package / version | `app.melovault.player` / vc **109** / `0.6.88-beta` (không bump) |
| Chứng chỉ ký SHA-256 | `a9b8cb6b5b0bc0400955b24dbc85801f5e8855356287caca2dcf3bb4e13db280` (Android Debug của Ni) |
| Telegram config | **false** (`TELEGRAM_APP_API_ID` bằng 0, hash rỗng — chỉ kiểm boolean, không đọc giá trị) · ABI `arm64-v8a` |
| Release (không bàn giao) | SHA-256 `3c99cc7408046ef596d658d8fc368df5b95c9d597cbf3c5142f769b62047f4b7`, 30.554.670 byte, cùng chứng chỉ debug |

**Không cài được / không dùng thử Telegram** (như P2): chữ ký Ni ≠ chữ ký Preview đang cài, thiếu Telegram config, versionCode 109 trùng Beta109, thiết bị chưa được phép. Không sửa signing/versionCode/secrets, không ADB, không `uninstall`/`pm clear`, không upload Drive (việc của ChatGPT). Vì `SYNC_ENABLED=false` và không mã nào dựng `TdlibPlaylistSyncService`, APK không có hành vi sync nào chạy được; thay đổi duy nhất chạy được là bản `ShareableTdlibOwner` đã tái cấu trúc (mục 3) **chưa được thử trên thiết bị**.

## 9. Rủi ro tích hợp thật còn lại (DEFERRED / BLOCKED, chưa PASS thiết bị)

1. **Hành vi thật của `getChatHistory`**: giả định theo tài liệu chính thức (id giảm dần, `from_message_id` tính cả tin đó, trang có thể ngắn, `offset` âm cho tin mới hơn). Truy vấn xuôi (offset −99) từ id chưa tồn tại hoặc `from=1` chưa kiểm với TDLib thật. Kiểm tra chéo chỉ coi *tin thật có trong (cursor, top] mà quét ngược thiếu* là xung đột, nên trả lời thưa của TDLib không gây dương tính giả, nhưng dương tính giả do TDLib thật vẫn khả dĩ → sẽ chỉ gây RECOVERY (không mất dữ liệu).
2. **Giả định `1 shl 20`** cho đầu lịch sử khi cursor=0 (xem mục 4); tin tạo kênh bị xoá → BACKOFF mãi.
3. **Chuẩn hoá văn bản** trong `updateMessageSendSucceeded` (khoảng trắng, entity hashtag): nếu khác thì ACK không bao giờ khớp → `Unknown` → khôi phục bằng đọc lịch sử (an toàn, nhưng mỗi lần gửi tốn thêm một vòng).
4. **Tên trường JSON** của `getSupergroup`/`getSupergroupFullInfo` (`usernames.active_usernames`, `status`, `member_count`, `has_linked_chat`, `linked_chat_id`, `description`) và `createNewSupergroupChat`: tên của `getChatHistory`, `updateMessageSendSucceeded`, `createNewSupergroupChat` đã đối chiếu với tài liệu chính thức; kho không có `td_api.tl`, và bản TDLib ghim ở repo (1.8.68) chưa đối chiếu với lược đồ gốc. Các trường còn lại theo hiểu biết của tôi, chưa kiểm sống.
5. **Quy mô:** >20.000 tin chưa đọc trong một lần quét → `UNPROVEN`. Thời gian thật của ~100–300 request chưa đo.
6. **Epoch:** UI gọi `markGetMeVerified` mỗi lần getMe xong; nếu xảy ra giữa lúc đang chạy sync thì lần chạy đó kết thúc BACKOFF (an toàn, thử lại).
7. **Tin xoá/sửa:** chỉ phát hiện khi sink đang bind; phát hiện muộn cần FULL_REPLAY định kỳ (chính sách P4).
8. Keystore thật, TDLib/JNI thật, kênh Telegram thật, âm thanh thật: **chưa xác nhận**.

## 10. Rollback

Chưa nối vào ứng dụng, nên không có hành vi runtime sync để rollback. `git revert 443dbe5` đưa `ShareableTdlibOwner.kt` về bản P2 và xoá toàn bộ lớp P3 (hoặc bỏ nhánh). Công tắc `TelegramPlaylistFlags.SYNC_ENABLED=false` (mặc định, không đổi trong P3). Không đổi định dạng tệp/khoá/schema; tệp đồng ý mới `tg_sync_consent_v1/` chỉ tạo khi P4 gọi lưu đồng ý.

## 11. Dọn dẹp tài nguyên

- Mọi lệnh Gradle chạy `--no-daemon`; `gradlew --status`: không còn daemon; không tiến trình Gradle/Java của phiên.
- Không emulator, không ADB, không màn hình thiết bị (SONY_SCREEN_END/SHANLING_SCREEN_END: không áp dụng).
- Bản sao lưu tệp dùng cho đột biến và log thử nằm trong scratchpad của phiên; mọi tệp mã nguồn đã khôi phục và `cmp` khớp (không dùng `git checkout`/`reset`). Dữ liệu test: `TemporaryFolder` JUnit và bộ nhớ.
- Giữ nguyên: báo cáo, log, JUnit XML (`docs/plans/artifacts/E96-P3/`, Git bỏ qua), APK QA vòng này và các vòng trước, các worktree/nhánh. Không đụng Agent khác; không `reset --hard`/`clean`/`stash`/force push; không đọc/copy secrets hoặc `local.properties`; không Telegram thật.

Đã dừng, chờ ChatGPT nghiệm thu độc lập.
