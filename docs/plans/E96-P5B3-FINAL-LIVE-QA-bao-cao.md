# E96-P5B3 FINAL LIVE QA — Báo cáo Sonnet — 2026-10-24

**Đề xuất của Sonnet: PASS_CANDIDATE (chờ ChatGPT nghiệm thu độc lập).** Sonnet không tự tuyên bố PASS cuối, không merge production.

## 1. Kết quả một dòng
Thay đổi đồng bộ **thật** qua kênh Telegram riêng đã có: **Sony ADD bài QA tổng hợp → M3 hiện 2 → 3 không khởi động lại; M3 REMOVE đúng bài đó → Sony hiện 3 → 2 không khởi động lại.** Đúng 2 thao tác gửi, 2 bài thật của người dùng không bị đụng, (c) tắt sau mỗi giai đoạn, không tạo kênh thứ hai, không gửi file nhạc.

## 2. Git / build
| | |
|---|---|
| Worktree / nhánh | `D:\Bots\MeloVault-E96-P5B3-Rework-01`, `dot/E96-P5B3-REWORK-01` |
| Xuất phát | `4b390ef102436e6f28ef52c4672bb7c4b9b1f833` (sạch) |
| Commit mã (fixture QA) | **`a8e2a10054a23c3c4bfa4d367b2666f90be418d7`** (`PlaylistQaFixture.kt`, nối `TelegramPreviewActivity.runQaFixtureIfRequested`, `PlaylistQaFixtureTest.kt`) |
| Commit báo cáo | commit kế tiếp (chỉ `docs/plans/E96-P5B3-FINAL-LIVE-QA-bao-cao.md`) |
| Test (Ni, `--rerun-tasks --offline --no-daemon`, một Gradle owner) | BUILD SUCCESSFUL 5m33s; Debug = Release = **1975 tests, 0 failures, 0 errors, 21 skipped** (= 21 cũ, không skip mới; +7 test mới = 1968 + 7). Gồm assembleShareableDebug/Release. XML: `final-live-qa\xml-run1-Debug|Release`, log `gradle-full-forced-run1.log` |

## 3. Fixture QA an toàn (vì chưa có sẵn)
`PlaylistQaFixture` chỉ chạy ở bản **Sync-QA debug** (`TelegramPlaylistFlags.SYNC_ENABLED`; Release/bản chuẩn bỏ qua hoàn toàn). Một **tệp marker rỗng trong `cache/`** (đặt bằng `run-as`) yêu cầu đúng MỘT thay đổi (ADD hoặc REMOVE) bài tổng hợp (`chatId=-1009999990001`, `messageId=1048576×9001`: không tài khoản/chat/tin nào thật ⇒ thư viện vẽ placeholder "Bài đã lưu · chưa có trong thư viện", **không lộ bài nhạc nào của người dùng**). Marker luôn bị xóa khi dùng; mỗi bước chạy tối đa 1 lần/cài (`qa_fixture_used.txt` ghi TRƯỚC khi ghi playlist). Fixture đi qua đúng đường Save (`session.toggle`) ⇒ chỉ **xếp 1 op vào outbox**; **không gửi, không đụng consent**. Việc gửi vẫn do (c) + lượt gửi 1 + "Đồng bộ ngay" bấm tay trong Cài đặt.
Test JVM (`PlaylistQaFixtureTest`, 7 test): bản chuẩn không bao giờ chạy; 1 marker = 1 bước; mỗi bước 1 lần; item không phải bài thật; **mô phỏng 2 thiết bị trên kênh giả: ADD A → gửi đúng 1 → B đọc (ngân sách 0) thấy 3 → B REMOVE gửi đúng 1 → A đọc thấy lại 2 bài gốc, tổng đúng 2 lần gửi, pending 0 hai máy**; fixture không chứa lời gọi gửi/consent; Activity gate `SYNC_ENABLED`, xóa marker và ghi `used` trước khi `toggle`, đúng 1 `toggle`.

## 4. APK QA (bản Tiny, config Telegram chỉ ở Tiny)
Clone cô lập Tiny `C:\Bots\MeloVault-E96-P5B3-Rework02-QA`; bundle `E96-P5B3-FINAL-LIVE-QA-a8e2a10.bundle` SHA256 `FD8DCC205DA21B56526F709FD8AD10DBF953ABDBFCB36D0745FE8339D97748F9` (đã kiểm hai đầu); HEAD Tiny = `a8e2a10…`; `telegram.config.valid=true`, `melo.sync.qa=true`, `allowcreate=false` (không in giá trị); `local.properties` bản sao đã xóa, bản gốc không đổi (script log).
| | |
|---|---|
| Tệp | `final-live-qa\MeloVault-E96-P5B3-FINAL-a8e2a10-TinySigned-SYNCQA-ON.apk` (Tiny: `C:\Bots\_qa_handoff\E96-P5B3-FINAL\`) |
| SHA-256 / byte | **`944904B5A51942FC88F7F9D98F4667A0E66F4F674BFBE12E1E6AFE2FAED637C8`** / 34 139 851 |
| Package / version / ABI | `app.melovault.player`, vc109 `0.6.88-beta-syncqa`, `arm64-v8a` |
| Chữ ký | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (= bản đang cài) |
| Rollback (cả hai máy) | bản REWORK-04 SHA `89DF19BE5F6748D7A17C7F50CB50A38B495A4B7DBB0F012A34D876ADE7054647`: `m3-pre-final-installed-base.apk`, `sony-pre-final-installed-base.apk` (cùng vc109/chữ ký ⇒ `install -r` được) |

## 5. Cài đặt (chỉ `adb -s SERIAL install -r`, không uninstall/pm clear/downgrade)
| | M3 `30313860180939` (Android 10, Ni) | Sony `QV725WEQ54` (Android 12, Tiny) |
|---|---|---|
| Cổng | model/ABI/package/vc/chữ ký đạt; không phát nhạc; sao lưu base.apk | giống |
| Kết quả | Success; SHA trên máy = `944904B5…37C8` | Success; SHA trên máy = `944904B5…37C8` |
| Dữ liệu người dùng sau cài | hash V1/V2/consent **không đổi** so với trước (`m3-fl-data-hashes-after-install.txt`) | không đo riêng (cài đè cùng dữ liệu) |

## 6. Cổng đọc-chỉ (trước mọi ghi)
Cả hai máy, trước khi cài bản mới: Playlist đúng 2 bài thật; tab Cài đặt: (a) bật, (b) tắt, **(c) tắt**, kênh "Đã gắn kênh đồng bộ", nút lượt gửi mờ. Chạy "Đồng bộ ngay" với ngân sách 0 (chỉ đọc/ACK) ⇒ **"Đã đồng bộ — Danh sách khớp với kênh Telegram riêng của bạn"**; hash V1/V2/consent M3 **không đổi** sau lần đọc. (Ảnh `m3-fl-0x`, `sony-fl-0x`.) Trạng thái "Đã đồng bộ" = không còn op chờ/không chắc ⇒ 5 op lịch sử không còn ở trạng thái chưa chắc. Giới hạn: Sonnet không đọc trực tiếp nội dung kênh bằng công cụ khác, chỉ dựa trạng thái app.

## 7. Giai đoạn 1 — Sony ADD (gửi thật #1) → M3 đọc
| # | Hành động | Kỳ vọng | Thực tế | Kết quả | Chứng cứ |
|---|---|---|---|---|---|
| 1 | Sony: marker ADD + resume | xếp 1 op, danh sách 3 (placeholder) | log `qa-fixture step=ADD outcome=Added`; Playlist 3 bài; Settings "Có 1 thay đổi chờ gửi" | PASS | `sony-s1-02`, `-03` |
| 2 | Sony: bật (c); Cho phép gửi **1**; xác nhận hộp thoại; "Đồng bộ ngay" **một lần** | gửi đúng 1 op | lượt gửi về "Chưa cho phép…" (đã dùng); trạng thái **"Đã đồng bộ"** | PASS | `sony-s1-04..09` |
| 3 | Sony: tắt (c) | `transmit 0` | file consent `transmit 0` | PASS | `sony-s1-10`, `sony-fl-data-hashes-final.txt` |
| 4 | M3: "Đồng bộ ngay" ngân sách 0, BACK ×2 (**cùng PID 25642**) | Playlist 2 → 3 không restart | log **`resume-refresh count=3 changed=true`** 23:02:18; "Playlist · 3 bài · Đã đồng bộ", dòng 3 placeholder | **PASS** (delta thật, không mô phỏng) | `m3-s1-01` (2 bài) → `m3-s1-06` (3 bài) |

## 8. Giai đoạn 2 — M3 REMOVE (gửi thật #2) → Sony đọc
| # | Hành động | Kỳ vọng | Thực tế | Kết quả | Chứng cứ |
|---|---|---|---|---|---|
| 5 | M3: marker REMOVE + resume (cùng PID) | xếp 1 op, danh sách 2 | log `qa-fixture step=REMOVE outcome=Removed`; Settings "Có 1 thay đổi chờ gửi" | PASS | `m3-s2-01`, `-02` |
| 6 | M3: bật (c); Cho phép gửi 1; xác nhận; "Đồng bộ ngay" một lần | gửi đúng 1 op | lượt về 0; **"Đã đồng bộ"** | PASS | `m3-s2-04..09` |
| 7 | M3: tắt (c) | `transmit 0` | `transmit 0` | PASS | `m3-s2-10`, `m3-fl-data-hashes-final.txt` |
| 8 | Sony: "Đồng bộ ngay" ngân sách 0, BACK ×2 (**cùng PID 17053**) | Playlist 3 → 2 không restart | log **`resume-refresh count=2 changed=true`** 23:04:57; "Playlist · 2 bài · Đã đồng bộ" | **PASS** | `sony-s2-01..03` |
| 9 | Sony ngang | 2 bài thật, cuộn/tìm vẫn có | bố cục hai khung, 2 bài, thanh tìm "Tìm trong 2 bài hát…" | PASS | `sony-s2-04`, `-05` |
M3 chỉ dọc (Shanling), đúng yêu cầu; sau REMOVE M3 hiện lại "Playlist · 2 bài · Đã đồng bộ" (`m3-s2-11`).

## 9. Giới hạn gửi / an toàn
- **Tổng số lần gửi mới = 2** (1 ADD từ Sony, 1 REMOVE từ M3): mỗi lần đúng một lượt "Cho phép gửi 1", dùng đúng một lần; không bấm lại. Không có lần gửi nào ngoài hai lần này (cả hai lần đọc-chỉ đều ngân sách 0 và (c) tắt; `used` ghi ADD ở Sony, REMOVE ở M3).
- Không tạo kênh (b tắt, `allowcreate=false`), không gửi nhạc/bài thật nào của người dùng, không đổi tài khoản, không xóa dữ liệu/cache/TDLib, không uninstall/pm clear.
- **Hai bài Playlist gốc**: cùng tiêu đề trong ảnh trước/sau cả hai máy (`m3-s1-01` ↔ `m3-s2-11`; `sony-fl-...` ↔ `sony-s2-03`). Danh tính (chat,message) nằm trong file mã hóa nên không đọc trực tiếp; V1/V2 đổi hash do ghi/đồng bộ hai op (mã hóa nonce ngẫu nhiên), không đối chiếu byte được. Consent (`mvconsent v1 enabled 1 / create 0 / transmit 0 / warning 1`) cuối phiên = hash đầu phiên ở cả hai máy.
- Outbox cuối: Settings hiển thị "Đã đồng bộ" trên cả hai máy (không còn op chờ/không chắc).
- Crash: crash buffer 0 dòng trên cả hai máy.

## 10. Chưa kiểm chứng / giới hạn
1. **Bằng chứng ACK phía máy chủ**: dựa trên trạng thái app ("Đã đồng bộ") và việc máy kia đọc lại được từ kênh thật (đây là chứng cứ mạnh nhất có được); log `MeloSync` stage không bắt được trong logcat Sony (Sony ẩn log) nên không có dòng "ack" thô để đính kèm. Chưa đọc trực tiếp tin trong kênh bằng công cụ độc lập.
2. M3 chỉ thử dọc; Sony thử dọc + ngang. Y700/S25/FYT và âm thanh thật: không thử (không thuộc cổng này).
3. Mất mạng/5xx/Unknown: không thử (cấm tự tạo).
4. Bản QA debug chứa `PlaylistQaFixture` + `PlaylistQaSimulation`; bản chuẩn/Release không có tác dụng (test + cổng build). Nên xem xét gỡ trước khi phát hành bản không phải QA.

## 11. Dọn dẹp (cuối phiên)
- App trên cả hai máy đã `force-stop` (không `pm clear`), không tiến trình test; **M3: `mScreenState=OFF`, Asleep (`SHANLING_SCREEN_END=OFF`); Sony: `Display State=OFF` (Dozing) (`SONY_SCREEN_END=OFF`)**. Xoay Sony khôi phục `accelerometer_rotation=1`, `user_rotation=0` (có một lần ghi tạm acc=0 do lỗi script, đã sửa lại ngay).
- Marker đã tự xóa khi dùng (không còn `qa_fixture_*_track.flag`); tệp `cache/qa_fixture_used.txt` (ADD ở Sony, REMOVE ở M3) cố ý **giữ lại** làm cầu chì chống chạy lặp; xóa nếu ChatGPT muốn (`run-as … rm`).
- Không còn tiến trình Gradle/Java; clone Tiny không còn `local.properties`; bản sao lưu APK và log giữ nguyên.

## 12. Bằng chứng
`D:\Bots\_handoff\MeloVault\E96-CROSS-DEVICE-SYNC\final-live-qa\` (ảnh PNG M3 `m3-*`, Sony `sony-*`, hash dữ liệu, logcat, `tiny-build.log`, `sony-install.log`, APK + backup, XML JUnit, log Gradle). Ảnh có tên bài hát → không đăng công khai; chưa chuẩn hóa lên Drive (ChatGPT làm theo CLAUDE.md §8).

**Đã dừng, chờ ChatGPT nghiệm thu.**
