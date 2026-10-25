# E96-P5B3 REWORK-02 — BÁO CÁO SONNET (code / test / build, KHÔNG Telegram, KHÔNG cài thiết bị)

**Kết luận đề xuất: PASS_CANDIDATE (chỉ code + JVM test + build trên Ni). Sonnet KHÔNG tự duyệt PASS.** Mọi thứ về Telegram thật / Sony / M3 vẫn CHƯA KIỂM CHỨNG.

Lưu ý đường dẫn: lời nhắn ghi `D:\Bots_handoff\…` (thiếu dấu `\`); em đọc đúng file tại `D:\Bots\_handoff\MeloVault\E96-CROSS-DEVICE-SYNC\PROMPT_SONNET_E96_P5B3_REWORK_02_20261024.md` và `…\E96-P5B3-REWORK-01-CHATGPT-INDEPENDENT-REWORK-20261024.md`.

## 1. Git
| | |
|---|---|
| Worktree / nhánh (không tạo mới) | `D:\Bots\MeloVault-E96-P5B3-Rework-01`, `dot/E96-P5B3-REWORK-01`, xuất phát đúng `358b131` (sạch) |
| Commit code REWORK-02 | `3128e45a4fba0c544a741e267c16a3be410fc139` ← mã nguồn của test và APK dưới đây |
| Commit báo cáo | commit kế tiếp, chỉ thêm `docs/plans/E96-P5B3-REWORK-02-bao-cao.md` |
| Cây làm việc | sạch (0 file thay đổi) sau commit code và sau commit báo cáo; `git diff --check` rc=0 |
| Không làm | không worktree/dự án mới, không merge, không đụng baseline `2b77e0e`, không Opus, không `adb`, không Telegram thật |

## 2. Lỗi ChatGPT nêu → cách sửa

### Blocker 1 — `sendMessage` trả lỗi 5xx bị coi là "chắc chắn không gửi"
* `TdlibPlaylistSyncTransport`: lỗi trả lời `sendMessage` chỉ là `Failed` khi mã **400..499** (429/FLOOD_WAIT giữ nguyên số giây). Mọi mã khác (500/502/503/504/0/…) → `SendResult.Unknown(SEND_DISPATCH_ERROR, bucket)`; `Unknown` có thêm `bucket` (E5XX…), hiện trong `SyncReport.errorBucket`. Nhánh `updateMessageSendFailed` mã không phải 4xx cũng mang bucket.
* Kết quả: op bị đánh dấu "chưa rõ", **không bao giờ PENDING** sau lỗi 5xx; gửi lại bị chặn tới khi đọc lại thấy.

### Blocker 2 — mất bền vững giữa lúc gửi và lúc ghi "chưa rõ" gây trùng
Thiết kế **write-ahead dùng đúng schema outbox hiện có** (trạng thái `RETRY_REQUIRED`), không migration:
1. Trước `transport.send`, coordinator ghi `markRetryRequired` cho đúng các op của envelope bằng `host.update` (một ghi V2 nguyên tử).
2. **Nếu ghi không `Committed` → KHÔNG gửi** (`Stale` → `STALE_SESSION`; còn lại → `SAVE_FAILED`, stage mới `SEND_PRECHECK_PERSIST`, `opsDispatched=0`).
3. Sau khi gửi: `Sent` → ACK (như cũ); `Unknown` → không cần ghi gì thêm (đã đánh dấu); `Failed` (từ chối chắc chắn, kể cả từ chối *trước* dispatch) → trả cờ về đúng như cũ bằng `PlaylistState.revertRefusedDispatch` (PENDING, `attempts` −1). Nếu bước trả này ghi lỗi thì op vẫn bị cờ = **chặn, không trùng**.
4. Crash/mất điện ở bất kỳ điểm nào sau bước 1, ghi ACK thất bại sau `Sent`, đổi phiên/epoch: op chỉ có thể ở `RETRY_REQUIRED` trên đĩa ⇒ chỉ được **đọc lại**, không bao giờ gửi lại. Uncertainty không bị reset bởi đổi account/epoch.
5. Đánh đổi có chủ ý (ghi rõ trong code): crash *giữa* bước ghi và bước gửi thật sự thì op bị cờ dù chưa rời máy ⇒ bị chặn (an toàn, mất khả năng tự gửi cho tới khi có quy trình/phê duyệt riêng), không bao giờ gửi trùng. Tốn thêm 1 lần ghi V2 mỗi envelope.

### Mục 5 (bổ sung) — đối soát chỉ-đọc khi (c) tắt; hồi sinh grant qua đổi epoch
* `TdlibPlaylistSyncService.runSync`: chỉ yêu cầu consent (c) khi **budget > 0** và còn op chờ (`Denied(TRANSMIT_NOT_ALLOWED)` như cũ, không gọi TDLib). Budget 0 chạy được với (a) + kênh đã gắn: đọc lịch sử, ACK những gì đã có, `opsDispatched=0`; (c) không bao giờ bị bật. Transport vẫn kiểm (c) ở mỗi lần dispatch.
* `SyncSettingsController.run`: ghi lại epoch khi bắt đầu; phần grant còn dư chỉ được trả lại nếu `epoch()` vẫn y nguyên khi cuộc gọi kết thúc (A→B→A trong lúc chạy ⇒ grant = 0, kể cả khi cuộc gọi bị `Denied`).

## 3. Red-first (trên `358b131`) → xanh
Viết `Rework02SafetyTest` (17 test) rồi chạy trên code cũ: **11 FAILED / 17**
(`b1` 5xx; `b2_i` ghi V2 hỏng trước dispatch; `b2_ii` crash sau dispatch và crash trước dispatch; `b2_iv` ghi ACK hỏng; `b2` bước trả cờ hỏng; `b2` đổi phiên giữa lập kế hoạch và ghi; `b3` chỉ-đọc khi (c) tắt; `b3` budget>0 vẫn bị từ chối đúng cách; `b3` 2 test hồi sinh grant qua epoch). Bằng chứng: `rework02-logs\red-358b131-rework02-tests.log`, `red-358b131-failed-tests.txt`, `red-junit\`. 6 test còn lại đã xanh trên code cũ và là lưới an toàn (từ chối 4xx giữ PENDING; ACK đọc-lại legacy/hiện tại; partial ACK; không mất id/tombstone/cursor; grant vẫn trả lại khi cùng epoch).

Các test hồi quy theo đề bài: (i) ghi V2 lỗi trước dispatch ⇒ 0 `send`; (ii) crash ngay sau dispatch ⇒ khởi động lại không gửi lại, đọc lại ACK, đúng 1 lần gửi; (iii) Unknown ⇒ đọc lại tin dạng hiện tại **và** dạng TAB đã bị TDLib làm sạch ⇒ ACK, không gửi lần 2; (iv) `Sent` rồi ghi ACK hỏng ⇒ lần đọc đủ kế tiếp ACK; (v) id/tombstone/`orSet`/cursor/danh sách bài không mất; epoch/phiên; partial ACK 20+5; TDLib thật-giả: 500/502/503/504 ⇒ đúng 1 `sendMessage`, rồi đọc lại ACK; 400/403/429 vẫn là từ chối (attempts=0, 429 giữ 35 s).
Thêm `Rework02SourceGuardTest` (5 test quét mã): đúng 1 điểm `transport.send`, cờ ghi trước nó, ghi lỗi dừng trước khi gửi, chỉ nhánh `Failed` gọi `revertRefusedDispatch`, 4xx-only, `budget > 0` gate, epoch gate.

## 4. Test sau sửa (Ni, `--offline --no-daemon … --rerun-tasks`, trên `3128e45`)
Lệnh: `.\gradlew.bat --offline --no-daemon :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --rerun-tasks --console=plain` → **BUILD SUCCESSFUL**. Parse JUnit XML (159 file/biến thể; bản sao `rework02-logs\junit-final\{debug,release}`):

| Biến thể | tests | failures | errors | skipped |
|---|---|---|---|---|
| Debug | **1939** | 0 | 0 | 21 |
| Release | **1939** | 0 | 0 | 21 |

REWORK-01 là 1917 ⇒ **+22 test mới** (17 + 5). 21 skip là đúng 21 cũ (Background/Smart OfflinePlaylistSync 19+1, PlaybackStabilitySoak 1); **0 skip mới**.
Hai test cũ đổi ngữ nghĩa đúng theo chính sách "chỉ-đọc không cần (c)": `P5ReadinessEnabledFlowTest.b02` (syncNow không grant ⇒ chạy chỉ-đọc, `Done`, 0 `sendMessage`; xin grant khi thiếu (c) ⇒ Refused) và `TdlibChannelConsentTest.c20` (budget>0 ⇒ Denied trước khi hỏi TDLib; budget 0 ⇒ Done, 0 gửi). Không test nào bị xóa hay làm yếu.

## 5. APK Ni — KHÔNG cài thiết bị (thư mục `D:\Bots\_handoff\MeloVault\E96-CROSS-DEVICE-SYNC\rework02-artifacts\`, có `SHA256SUMS.txt`, `APK-INFO.txt`; build từ `3128e45`)
| Tệp | versionName | bytes | SHA-256 |
|---|---|---|---|
| `…REWORK02-3128e45-Ni-standard-debug-SYNCOFF.apk` | 0.6.88-beta | 33345924 | `40e39c0bc0886c8da7d8d52a4f10d26feadf1edf84d00c96f8367204245ae6e6` |
| `…REWORK02-3128e45-Ni-standard-release-SYNCOFF.apk` | 0.6.88-beta | 30604182 | `4c6d373d30da5e318eb1b9bcee1235f60f269f2d15e6b0ba783efc716e2f63f5` |
| `…REWORK02-3128e45-Ni-debug-SYNCQA-NOT-FOR-INSTALL.apk` | 0.6.88-beta-syncqa | 33345928 | `0e5e3b21ece73ae550d5049751e8f0965a361564950bad3e6febe842b002d1b2` |

Package `app.melovault.player`, versionCode 109; chữ ký Ni `a9b8cb6b…13db280` ≠ chữ ký Sony `eecd3df7…` ⇒ không cài đè được; không có cấu hình Telegram (`API_ID=0`). BuildConfig: Release `QA_BUILD=false`/`ALLOW_CREATE=false`; Sync-QA debug `QA_BUILD=true`/`ALLOW_CREATE=false`. Đây chỉ là chứng cứ build, không phải QA candidate. Bộ APK REWORK-01 ở `rework01-artifacts\` đã bị thay thế (giữ nguyên, không xóa).

## 6. Quyết định cần ChatGPT xem
1. Write-ahead dùng `RETRY_REQUIRED` ngay *trước* dispatch ⇒ trạng thái này giờ có nghĩa "có thể đã gửi" (không phân biệt "chắc chắn đã rời máy"). Crash đúng khe giữa ghi và gửi ⇒ chặn (xem 2.5). Không đổi schema/V1/V2.
2. Nếu bước **trả cờ** sau từ chối chắc chắn không ghi được ⇒ op nằm lại ở `RETRY_REQUIRED` (bị chặn tới khi đọc thấy trên kênh — sẽ không bao giờ xảy ra vì thực sự chưa gửi). Đã chọn an toàn thay vì liveness; cần quyết định riêng nếu muốn thêm đường phục hồi.
3. `Unknown` mang `bucket` nhưng bucket chưa hiển thị UI (chỉ trong `SyncReport`).
4. `syncNow` không grant giờ là lần chạy chỉ-đọc hợp lệ (trước đây `Denied` khi chờ gửi mà thiếu (c)); UI vẫn hiện "Chưa cho phép gửi" khi có op chờ và thiếu (c).

## 7. Chưa kiểm chứng / rủi ro
* Không có Telegram thật, Sony, M3, `adb`; hành vi TDLib/`getChatHistory` thật, và việc tin 5 op cũ có trên kênh, vẫn chưa biết.
* Write-ahead tin vào `host.update` = một ghi nguyên tử V2 (đã có sẵn từ P1/P2); tính bền vững thật của phần cứng (fsync) là phạm vi của lớp lưu trữ, không đổi ở đây. Mô phỏng crash trong JVM bằng `SimulatedCrash` + khởi động lại từ file thật.
* Bản UI/hộp thoại vẫn chưa xem trên thiết bị.

## 8. Dọn dẹp
Không có tiến trình Gradle/Java/Kotlin còn lại (mọi lần chạy `--no-daemon`); không `adb`; Sony/M3 không đụng tới (màn hình giữ OFF); không đọc/in secret; log/XML/APK nằm ngoài repo ở `rework02-logs\`, `rework02-artifacts\`; thư mục scratchpad của phiên chỉ có script perl nhỏ.

**Đã dừng, chờ ChatGPT nghiệm thu.**
