# E96-P5B3 REWORK-04 — BÁO CÁO TỰ QA (Sonnet)

**Đề xuất: PASS_CANDIDATE.** Sonnet không tự duyệt PASS; ChatGPT nghiệm thu độc lập. Không gửi metadata Telegram, không bật (c), không cấp lượt, không tạo kênh, không đổi playlist của người dùng.

## 1. Git
| | |
|---|---|
| Worktree / nhánh | `D:\Bots\MeloVault-E96-P5B3-Rework-01`, `dot/E96-P5B3-REWORK-01`, xuất phát đúng `142a2b6866fbbec264bec384aa59711d736d7cef` (sạch) |
| Commit mã nguồn (test + APK) | **`8fb223b75e107dca4aa3b5f91ef0566af0c2be6d`** |
| Commit báo cáo | commit kế tiếp (chỉ thêm `docs/plans/E96-P5B3-REWORK04-bao-cao.md`) |
| Cây làm việc | sạch; `git diff --check` rc=0 |

## 2. BLOCKER A — `TdlibTransportSendTest.s13` ngẫu nhiên đỏ → **lỗi nằm ở fixture test, không ở sản phẩm**
* **Tái hiện xác định:** `TdlibConcurrentSendStressTest.r4_theFakeNeverHandsTwoSimultaneousRequestsTheSameTemporaryId` dùng `CyclicBarrier` ép hai `sendMessage` vào bộ đếm cùng lúc, lặp 300 vòng. Trên `142a2b6` **đỏ ngay vòng 0**: `Unknown(SEND_ACK_MISMATCH)` — đúng chữ ký lỗi của s13 (`rework04-logs\red-142a2b6-s13-stress.log/.xml`).
* **Nguyên nhân (fake `FakeTdlibJsonGateway`, hai chỗ không an toàn luồng):**
  1. `provisional` là `Long` thường, `++provisional` — hai request đồng thời nhận **cùng id tạm** ⇒ hai cập nhật thành công lẫn nhau ⇒ transport đúng khi trả `Unknown` (cập nhật không thuộc tin của nó).
  2. `store()` đọc lại tin từ `TreeMap` ngay khi request kia chèn vào (chỉ `post` được khóa) — đọc cấu trúc đang bị sửa. Ngoài ra `sendModes.poll()` (ArrayDeque) và `nonTextSends++` cũng không khóa.
* **Sửa:** `AtomicLong` cho id tạm; `store()` khóa trên `world` cả bước ghi+đọc; `sendModes.poll()` và bộ đếm trong `synchronized`. **Mã sản phẩm không đổi** — ngữ nghĩa Unknown fail-closed giữ nguyên (test `r4_aSendWhoseUpdateBelongsToAnotherMessageIsStillUnknown…` khóa lại), không skip/không nới test.
* **Sau sửa:** sau khi chỉ sửa bộ đếm, stress còn đỏ ngẫu nhiên (lộ ra lỗi thứ hai là `TreeMap`); sau khi sửa cả hai: **6/6 lần chạy ép `--rerun-tasks` xanh** (6×300 = 1800 cặp gửi trùng thời điểm cưỡng bức) + `TdlibTransportSendTest` xanh 3/3 (`rework04-logs\stress-after-fix-run1..6.log`, `green-s13-stress-run*.log`).

## 3. Kiểm thử đầy đủ (Ni, `--offline --no-daemon … --rerun-tasks`, trên `8fb223b`)
`BUILD SUCCESSFUL` (4m08s). JUnit XML (163 file/biến thể; `rework04-logs\junit-final\{debug,release}`):

| Biến thể | tests | failures | errors | skipped |
|---|---|---|---|---|
| Debug | **1964** | 0 | 0 | 21 |
| Release | **1964** | 0 | 0 | 21 |

REWORK-03 là 1957 ⇒ **+7** (2 stress/semantics + 5 mô phỏng QA/nhãn); 21 skip là đúng 21 cũ (không skip mới). Release: `MELO_SYNC_QA_BUILD=false`, `ALLOW_CREATE=false` (test cổng build xanh).

## 4. BLOCKER B — `changed=true` trên máy thật, không ghi Telegram, không đổi playlist
* **Đường kiểm cô lập (chỉ build Sync-QA debug):** `PlaylistQaSimulation`. Một **tệp đánh dấu rỗng trong `cache/`** (đặt/xóa bằng `run-as`, app debug) làm bước đọc của refresh thêm **một khóa bài lạ** vào danh sách vừa đọc — như thể thiết bị khác vừa lưu một bài mà thư viện máy này chưa biết. **Chỉ đọc**: không ghi V2/V1/outbox/consent, không Telegram. Bản chuẩn/Release/không có tệp ⇒ không làm gì (test). Gỡ tệp ⇒ lần resume sau trở lại danh sách thật.
* **Bằng chứng JVM:** `r4_theLiveRefreshShowsChangedTrueThenBackToTheRealList_andNothingIsWritten` (so sánh byte mọi tệp trạng thái trước/sau, 0 fetch/0 send).
* **Bằng chứng thiết bị (cùng tiến trình, không khởi động lại):**
  * **M3 (dọc)** PID 16629 suốt phiên: 2 bài → đặt tệp, HOME → mở lại → log `resume-refresh count=3 changed=true`, ảnh **"Playlist · 3 bài"** với dòng thứ ba placeholder "Bài đã lưu · chưa có trong thư viện…" (`m3-r4-02-simulated-remote-changed-true.png`) → gỡ tệp, resume → `count=2 changed=true`, trở lại 2 bài (`m3-r4-03-…`).
  * **Sony (ngang)** PID 12130: ảnh gốc 2 bài (`sony-r4-01`), mô phỏng "Playlist · 3 bài" + dòng placeholder (`sony-r4-02-landscape-simulated-changed-true.png`; log `count=3 changed=true` 18:32:10), gỡ tệp ⇒ 2 bài (`sony-r4-03`, log `count=2 changed=true`), khôi phục dọc (`sony-r4-04`).
  * **Dữ liệu người dùng không đổi:** `sha256sum` (qua `run-as`, chỉ băm) của `no_backup/tg_playlists_v2`, `tg_playlists_v1`, `tg_sync_consent_v1` **trước = sau** trên cả hai máy (`m3-data-hashes-*.txt`, `sony-data-hashes-*.txt`; diff = 0). Tệp đánh dấu đã xóa (đếm 0), không file tạm nào còn.
* **Giới hạn trung thực:** đây là mô phỏng **hiển thị** của thay đổi từ xa (nguồn đọc được thay), không phải thay đổi V2 do đồng bộ thật; phần V2 → `snapshotFor` được chứng minh bằng JVM với phiên V2 thật và nhiều thiết bị giả (REWORK-03 + test mới). Bằng chứng "một thiết bị khác thật sự gửi metadata mới rồi thiết bị này tự hiện" vẫn đòi **một thao tác metadata có hạn, có thể hoàn tác, do người dùng đồng ý riêng** — không thực hiện ở đây.

## 5. MINOR — nhãn bị cắt trên M3
`MeloCompactScreen.downloadedLine` cho phép 2 dòng. M3 giờ hiện đủ "Đã lưu trên máy · Chưa kiểm tra đồng bộ trong phiên này" (`m3-r4-01-playlist-real-2.png`); Sony một dòng đủ.

## 6. APK
Build tại Tiny (clone cô lập `C:\Bots\MeloVault-E96-P5B3-Rework02-QA`, bundle SHA256 `91CD184F…E551` đã kiểm, HEAD đúng `8fb223b`, `telegram.config.valid=true`, `allowcreate=false`); `local.properties` chỉ sao tạm trên Tiny rồi xóa (bản gốc không đổi, bản sao đã mất; clone sạch).
* Tệp: `rework04-device-qa\MeloVault-E96-P5B3-REWORK04-8fb223b-TinySigned-SYNCQA-ON.apk` (Tiny: `C:\Bots\_qa_handoff\E96-P5B3-REWORK04\…REWORK04-30ebbd9-…apk` — tên cũ do lỗi script, **cùng nội dung/SHA**).
* SHA-256 **`89DF19BE5F6748D7A17C7F50CB50A38B495A4B7DBB0F012A34D876ADE7054647`**, 34 137 181 byte, `app.melovault.player` vc109 `0.6.88-beta-syncqa`, `arm64-v8a`, chữ ký `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`.

## 7. Cài đặt từng máy (cổng: model/ABI/package/vc; chữ ký bản đang cài `eecd3df7…`; sao lưu base.apk; không phát nhạc; chỉ `adb -s SERIAL install -r`)
| | M3 `30313860180939` (Android 10) | Sony `QV725WEQ54` (Android 12) |
|---|---|---|
| Bản sao lưu (SHA `41D6CC77…18610` = bản REWORK-03) | `m3-pre-rework04-installed-base.apk` | `sony-pre-rework04-installed-base.apk` |
| Cài | Success; SHA trên máy = `89DF19BE…4647` | Success; SHA trên máy = `89DF19BE…4647` |
| QA | M3 **dọc**: PASS (xem §4/§5) | Sony **ngang**: PASS; dọc khôi phục (acc_rot=1, user_rot=0) |
| Crash | 0 (crash buffer 0, không FATAL của MeloVault) | 0 |
| Cuối | app dừng, **Asleep, màn tắt** | app dừng, **Display State=OFF** |
| Trạng thái đồng bộ | (a) bật, (b)(c) tắt, 0 chờ gửi, kênh đã gắn, 2 bài | giống M3 |

Sự cố: không có hộp thoại USB lần này. Hoàn tác = `install -r` các tệp `*-pre-rework04-installed-base.apk` (cùng versionCode/chữ ký).

## 8. Chưa kiểm chứng / rủi ro
1. Thay đổi từ xa **thật** hiện ngay trên thiết bị (cần một thao tác metadata được đồng ý riêng, xem §4).
2. Âm thanh thật, FYT, Car Mode thật, mất mạng: không thử.
3. Log Android có "chatty" gộp dòng giống nhau; bằng chứng log là các dòng đã lưu trong `*-logcat-sanitized.txt` và trích trong báo cáo.
4. `PlaylistQaSimulation` tồn tại trong bản Sync-QA debug (tệp đánh dấu trong cache); bản chuẩn/Release không có tác dụng. Nên xem xét gỡ trước khi phát hành bản không phải QA.

## 9. Dọn dẹp
Không còn tiến trình Gradle/Java/Kotlin; không emulator; app trên hai máy đã dừng, **hai màn hình tắt**; xoay Sony đã khôi phục; tệp đánh dấu mô phỏng đã xóa; clone Tiny sạch, không còn `local.properties`. Kết quả/ảnh/log/APK sao lưu: `D:\Bots\_handoff\MeloVault\E96-CROSS-DEVICE-SYNC\rework04-device-qa\` và `rework04-logs\` (ảnh có tên bài hát, không đăng công khai).

**Đã dừng, chờ ChatGPT nghiệm thu.**
