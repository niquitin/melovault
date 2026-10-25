# E96-P5B3 REWORK-03 — BÁO CÁO TỰ QA TRÊN THIẾT BỊ (Sonnet)

**Đề xuất: PASS_CANDIDATE** (code + test + build + cài + tự QA trên Sony và M3). Sonnet KHÔNG tự duyệt PASS; ChatGPT nghiệm thu độc lập.
Giới hạn quan trọng (mục 6): chưa có ảnh chụp **một thay đổi từ xa thật sự xuất hiện không cần khởi động lại** trên thiết bị — chỉ có bằng chứng JVM xác định + bằng chứng thiết bị rằng đường làm mới chạy ở đúng tiến trình đang chạy.

## 1. Git
| | |
|---|---|
| Worktree / nhánh | `D:\Bots\MeloVault-E96-P5B3-Rework-01`, `dot/E96-P5B3-REWORK-01` (cùng worktree, không tạo mới). Xuất phát `26841bd` sạch |
| Commit code chính | `38e7a1583c2ba6c204ed5d2b384f650ae1789149` (PlaylistLiveRefresh + nhãn trạng thái + 17 test) |
| Commit mã nguồn của APK/test | **`30ebbd9b5f0c73b89110e5c122820f7a9b141fd9`** (thêm log chỉ-đếm `MeloPlaylist resume-refresh count=N changed=bool`, +1 test) |
| Commit báo cáo | commit kế tiếp (chỉ thêm `docs/plans/E96-P5B3-REWORK03-bao-cao.md`) |
| Cây làm việc | sạch; `git diff --check` rc=0 |
| Không làm | không merge, không đụng baseline/E95/Private Stable/backend, không `uninstall`/`pm clear`/downgrade, không gửi metadata Telegram, không bật (c), không cấp lượt, không tạo kênh, không đổi bài của người dùng, không phát nhạc |

## 2. Lỗi và cách sửa
* **Lỗi:** sau khi đồng bộ trong Cài đặt, danh sách Playlist trên màn chính còn cũ cho đến khi khởi động lại app (`onResume` chỉ làm mới dòng trạng thái, không đọc lại danh sách đã lưu).
* **Sửa (nhỏ, trong phạm vi):**
  * `PlaylistSessionApi.snapshotFor(account)`: danh sách đã commit của đúng tài khoản, trong bộ nhớ (không file, không mạng); `null` nếu chưa mở/khác tài khoản/bị chặn. Cài cho V2 và V1.
  * `PlaylistLiveRefresh` (mới, thuần): `onResume` → hỏi luồng Playlist (xếp hàng sau mọi Save đang chờ) → trên UI chỉ áp dụng nếu màn còn sống, **đúng tài khoản**, không có yêu cầu mới hơn/`invalidate()`; danh sách giống hệt thì **không vẽ lại** (giữ cuộn/tìm kiếm). Chỉ danh sách bài đã lưu được trao đi; trạng thái đã tải/đang phát không bị đụng. Bài đã lưu mà thư viện chưa có → dòng placeholder (cơ chế sẵn có `placeholderSong`).
  * `TelegramPreviewActivity.onResume` gọi `reloadSavedPlaylistLive` + `refreshPlaylistStatus`; `closeSavedPlaylist()` gọi `liveRefresh.invalidate()` (đổi/đăng xuất tài khoản). Không gọi `runSync`/TDLib/`SyncRuntime`/lượt gửi (test quét mã khóa).
  * Nhãn khi không có lần chạy nào trong phiên: **"Đã lưu trên máy · Chưa kiểm tra đồng bộ trong phiên này"** (trước: "Chưa đồng bộ lần nào"); không thêm cờ "đã đồng bộ" bền vững.

## 3. Test (red → green; Ni, `--offline --no-daemon … --rerun-tasks`)
* **Đỏ trên `26841bd`:** `red-26841bd-compile.log` (API chưa tồn tại ⇒ test không biên dịch) và `red-26841bd-label.log` (test nhãn: `r3_afterANewProcess…` FAILED trên code cũ). Nhật ký ở `rework03-logs\`.
* **Xanh trên `30ebbd9`:** `.\gradlew.bat --offline --no-daemon :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --rerun-tasks --console=plain` → **BUILD SUCCESSFUL**. Parse JUnit XML (161 file/biến thể; `rework03-logs\junit-final\{debug,release}`):

| Biến thể | tests | failures | errors | skipped |
|---|---|---|---|---|
| Debug | **1957** | 0 | 0 | 21 |
| Release | **1957** | 0 | 0 | 21 |

  REWORK-02 là 1939 ⇒ **+18 test mới**; 21 skip là đúng 21 cũ (không skip mới). Test mới: danh sách từ xa hiện khi resume; thứ tự và không trùng; danh sách giống hệt ⇒ không vẽ lại; xóa từ xa; kết quả cho tài khoản khác/đã đổi ⇒ bỏ; màn đã hủy ⇒ bỏ; kết quả cũ không ghi đè kết quả mới; `invalidate`; phiên chưa mở/khác tài khoản ⇒ `null`; Save cục bộ đang xếp hàng được thấy trước khi làm mới; >300 bài (A 300 + C 50 ⇒ 350 hiện đủ); không gọi mạng/không đụng outbox (đếm fetch/send/outbox); log chỉ có số đếm + cờ; `onResume` không có `runSync/runOnce/SyncRuntime/…`; nhãn trung thực.
* Release: `MELO_SYNC_QA_BUILD=false`, `MELO_SYNC_QA_ALLOW_CREATE=false` (không đổi so với REWORK-02; test cổng build vẫn xanh).

## 4. APK
Build trên Tiny (clone cô lập `C:\Bots\MeloVault-E96-P5B3-Rework02-QA`, bundle Git kiểm SHA256 `3C04237E…D85C7`, HEAD đúng `30ebbd9…`, `telegram.config.valid=true`, `allowcreate=false`); `local.properties` chỉ tạm sao trên Tiny rồi xóa (xác nhận: bản gốc không đổi, bản sao đã mất).

| | |
|---|---|
| Tệp | `rework03-device-qa\MeloVault-E96-P5B3-REWORK03-30ebbd9-TinySigned-SYNCQA-ON.apk` (cũng ở Tiny `C:\Bots\_qa_handoff\E96-P5B3-REWORK03\`) |
| SHA-256 | `41D6CC776388BF9ED370FCD2E295AF72C90DB6761D3855B6DC76CDC502218610` |
| Kích thước | 34 136 403 byte |
| Package / version | `app.melovault.player`, versionCode 109, `0.6.88-beta-syncqa`, ABI `arm64-v8a` |
| Chữ ký | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (khớp Beta) |
| Ghi chú | Bản trung gian `…38e7a15…apk` (chưa có log đếm) đã bị thay thế, không cài lên máy nào |

## 5. Từng thiết bị
Cổng trước cài (cả hai): model/ABI/package/versionCode kiểm; chữ ký bản đang cài = `eecd3df7…`; **sao lưu base.apk đang cài** (SHA `317EE643…58C26F`, chữ ký `eecd3df7…`) — `m3-pre-rework03-installed-base.apk`, `sony-pre-rework03-installed-base.apk`; không có nhạc đang phát; chỉ `adb -s SERIAL install -r`; SHA bản cài trên máy đọc lại bằng `sha256sum` = `41D6CC77…18610` trên cả hai.

| | Shanling M3 Ultra (`30313860180939`, Android 10) | Sony Xperia 5 II (`QV725WEQ54`, Android 12) |
|---|---|---|
| Cài | PASS (`Success`, 15:07) | PASS (`Success`, 15:09) |
| Mở app mới, Playlist | PASS: 2 bài đúng, không trùng — `m3-r3-01-playlist-before.png` (nhãn mới, bị cắt "Chưa kiểm tra đồng…" do màn hẹp) | PASS: 2 bài — `sony-r3-01-playlist-before.png`; nhãn đầy đủ "Chưa kiểm tra đồng bộ trong phiên này" |
| Vào Cài đặt > Đồng bộ Playlist, **"Đồng bộ ngay" chỉ-đọc** (không lượt, (c) tắt) | PASS — `m3-r3-03/04` | PASS — `sony-r3-02/03` ("Đã đồng bộ", (c) tắt) |
| Quay lại Playlist **cùng tiến trình** | PASS: PID 16115 trước = sau; 2 bài; trạng thái tự đổi "Chưa kiểm tra…" → **"Đã đồng bộ"** — `m3-r3-05`; log `MeloPlaylist: resume-refresh count=2 changed=false` | PASS: PID 4652 trước = sau; 2 bài; "Đã đồng bộ" — `sony-r3-04/06/09`; log `…count=2 changed=false` (15:17:54) |
| Resume từ nền (HOME → mở lại) | — | PASS: cùng PID; `resume-refresh` chạy — `sony-r3-07` |
| Ngang / dọc | M3 dọc: PASS | Sony **ngang**: PASS (2 bài, không trùng, nhãn "Đã đồng bộ") — `sony-r3-10-landscape.png`; đã khôi phục dọc (acc_rot=1, user_rot=0) — `sony-r3-11` |
| Crash | không FATAL/ANR của MeloVault; crash buffer 0 dòng | như M3 (các FATAL cũ trong log là `com.facebook.appmanager`, không liên quan) |
| Dọn dẹp | app force-stop; **mWakefulness=Asleep, màn tắt**; không file tạm trên máy | app force-stop; **Display State=OFF**; không file tạm trên máy |
| Trạng thái đồng bộ | (a) bật, (b)(c) tắt, 0 chờ gửi, kênh đã gắn | giống M3 |

Sự cố trong lúc QA (đã xử lý an toàn): hộp thoại hệ thống Sony "Cho phép truy cập?" (USB/MTP, `com.sonyericsson.usbux`) hiện hai lần do USB nối lại; script kiểm tiêu điểm nên không chạm app khi nó chiếm màn; em chọn **"Từ chối"** (không cấp quyền dữ liệu). Một lần chạm tọa độ cố định của em trùng lúc app đang ở Cài đặt (nút "Cho phép gửi 1 thay đổi" lúc đó **bị khóa** vì (c) tắt) nên không có tác dụng; ảnh sau đó (`sony-r3-03`) xác nhận (c) vẫn tắt và không có hộp thoại cấp lượt. Không có tin Telegram nào được gửi (không có lượt, (c) tắt, `opsDispatched` không tồn tại trong các lần chạy chỉ-đọc).

## 6. Giới hạn / chưa kiểm chứng
1. **Không có ảnh thiết bị cho tình huống "danh sách thay đổi từ xa rồi hiện ngay"**: muốn có phải làm thay đổi thật (một thiết bị gửi metadata mới hoặc người dùng lưu/bỏ bài) — đều bị cấm trong lần chạy này. Bằng chứng thay thế: (a) test JVM dùng V2 session thật và nhiều thiết bị giả (`r3_aRemoteUpdateCommittedWhileSettingsWasOpen…`, xóa từ xa, >300 bài) đỏ→xanh; (b) trên máy thật, đường `onResume → snapshotFor → so sánh → (áp dụng)` chạy trong cùng tiến trình, ghi log `count=2 changed=false`, danh sách giữ nguyên và không trùng. Đề xuất gate riêng nếu cần: người dùng đồng ý một thay đổi thật (ví dụ M3 bỏ lưu một bài trong khi Sony đang mở Playlist).
2. Nhãn mới dài; trên M3 (màn hẹp) dòng phụ bị cắt thành "Chưa kiểm tra đồng…" (đầy đủ ở Cài đặt và ở Sony).
3. Âm thanh thật, FYT, mất mạng, Car Mode thật: CHƯA KIỂM CHỨNG (không phát nhạc; Car Mode chỉ được làm mới qua `carScreen?.onPlaylistStatusChanged()` + `scheduleMeloLibraryRefresh()` nhưng không chụp).
4. Log Sony có "chatty" nên các dòng giống hệt nhau gần nhau có thể gộp; bằng chứng log của Sony là các dòng lúc 15:17:13/15:17:54 (đã ghi nhận một dòng).

## 7. Dọn dẹp (xác nhận)
Không có tiến trình Gradle/Java/Kotlin còn lại (mọi lần chạy `--no-daemon`); không emulator; app trên cả hai máy đã dừng, **hai màn hình tắt**; xoay màn Sony đã khôi phục; clone Tiny sạch và không còn `local.properties`; ảnh/log/APK/bản sao lưu nằm ở `D:\Bots\_handoff\MeloVault\E96-CROSS-DEVICE-SYNC\rework03-device-qa\` và `rework03-logs\` (cùng ảnh gốc Sony ở Tiny `C:\Bots\_qa_handoff\E96-P5B3-REWORK03`). Ảnh có tên bài hát, không đăng công khai. Hoàn tác bằng cách `adb -s SERIAL install -r` các `*-pre-rework03-installed-base.apk` (cùng versionCode, cùng chữ ký).

**Đã dừng, chờ ChatGPT nghiệm thu.**
