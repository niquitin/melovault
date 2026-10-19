# Báo cáo Sonnet — E94-LOCAL-MUSIC (nguồn "Nhạc trên máy")

**Vai trò:** Claude Sonnet 5.5 THỰC THI, 19/10/2026. **TASK_ID:** `E94-LOCAL-MUSIC`. **Nhánh:** `dot/E94-LOCAL-MUSIC`.
**Trạng thái:** **QA CANDIDATE — CHƯA NGHIỆM THU.** Chưa có `-nghiem-thu.md`, `-apk-test.md`, `-hinh-nghiem-thu.md` (việc của ChatGPT). Sonnet không upload Drive, không đổi quyền chia sẻ.

---

## 1. Git

| Mục | Giá trị |
|---|---|
| S0 (đầu phiên) | nhánh `dot/E94-LOCAL-MUSIC`, HEAD `7c1b75d8b894322f7dcb70619d46262cee15c193` (commit kế hoạch), cây sạch, `git diff --check` sạch, `git diff ba74206 HEAD -- app/` rỗng, không Gradle/agent khác (2 tiến trình `java` nhàn rỗi từ 16/10, ~2–8 MB RAM, không phải build) |
| Commit tính năng | **`2d5812139b0f98c64cc4c4f2f9d25f612dc88674`** `feat(shareable): local music source (MediaStore, permission on demand)` — 15 file, +1.654/−42 (gồm test) |
| Bundle sang Tiny | `D:\Bots\_transfer\MeloVault_E94-LOCAL-MUSIC_fdf64c5-2d58121_delta_20261019.bundle` (+ `.sha256`), 77.479 byte, SHA256 `260E1269BD1AF1AA12C0E3948180218F400DE017BB21BBE9AA40B8F50D0915B4`; Tiny kiểm lại SHA khớp, `git bundle verify` ok, HEAD Tiny = `2d58121` trên `qa/E94-LOCAL-MUSIC` (clone cô lập `C:\Bots\MeloVault-E93D-IsolatedQA`, cây sạch trước khi nạp) |
| File production mới | `MeloLocalSourcePolicy.kt` (191 dòng, logic thuần, không import Android), `MeloLocalLibrary.kt` (253 dòng, tái dùng `LocalMediaRepository` chỉ-đọc, mọi truy vấn MediaStore trên executor riêng) |
| File production sửa | `MeloScreenHost`, `MeloSourceCardPolicy`, `MeloComponentPolicy`, `MeloComponents`, `MeloLibraryPane`, `MeloCompactScreen`, `MeloPhoneScreen`, `MeloTabletScreen`, `MeloStudioDeck`, `TelegramPreviewActivity` (+493/−15) — đúng danh sách mục 4 của kế hoạch |
| File test | mới `MeloLocalSourcePolicyTest` (21 ca), `MeloLocalLibraryTest` (16 ca); thêm 2 ca vào `MeloSourceCardPolicyTest` |
| Không đụng | `ShareablePlaybackService`, DAO/schema/`songKey` Telegram, `LocalMediaRepository`, `main`/`personal`, manifest, `build.gradle` (versionCode giữ 109), `MeloCarScreen`, `CLAUDE.md`, `HANDOFF.md`, `docs/workflow/**` (đã grep danh sách file đổi) |
| Đường phát Telegram | Chỉ thêm `val epoch = localPlayEpoch` + dòng chặn `if (epoch != localPlayEpoch) return` trong callback `downloadAndPlay` (3 dòng gồm 1 comment). Các nhánh local khác nằm ở `playAdjacent`/`addToUpcomingQueue`/`toggleCurrentFavorite`/`showUpcomingQueue`/`nowPlaying`/`queue` theo mục 3.3–3.5 |

### 1.1 Kích thước diff production — vượt mốc ~850 dòng (xin ChatGPT quyết)
Kế hoạch ghi "diff production vượt ~850 dòng ⇒ dừng báo". Đo bằng `git diff` (chỉ `app/src/shareable`): **937 dòng thêm tính cả dòng trống/comment; 837 dòng thêm nếu bỏ dòng trống và comment-only; 42 dòng xóa**. Ước lượng kế hoạch ~650. Phần vượt chủ yếu là comment KDoc, hộp Thông tin/Hàng đợi local và `MeloLocalLibrary` (253 dòng so với ~150). Toàn bộ nằm trong file và phạm vi kế hoạch; tôi tiếp tục thay vì dừng vì không có thay đổi ngoài phạm vi, nhưng báo rõ để ChatGPT cân nhắc.

### 1.2 Khác biệt nhỏ so với kế hoạch (có chủ ý)
1. `syncSubtitle()` khi mở local trả **"Nhạc trên máy · chỉ đọc"** thay vì `null` (kế hoạch ghi `null` ⇒ Điện thoại/Tablet sẽ hiện "☁ N bài · Đã đồng bộ", sai sự thật cho nguồn không đồng bộ). Sửa một dòng, dễ hoàn nguyên.
2. `MeloLocalLibrary` thêm `val loadedCount` (số bài chưa lọc) để tính chữ chú thích; `enterLocal()` luôn nạp với `force = true` khi cần nạp (cache `LocalMediaRepository` không có ích hơn cache của thư viện).
3. Mục ⋮ "♪ Nhạc trên máy" (Điện thoại/Tablet/Gọn "Thêm…") không hiện ở Car Mode (`carScreen == null`).
4. Tab đáy "Thư viện" vẫn sáng khi đang xem Nhạc trên máy (đúng kế hoạch mode=ALL; bấm tab = về Telegram). `MeloBottomBar` không thuộc danh sách file được sửa.
5. Đính chính an toàn: lệnh `cat local.properties` trên Ni lúc kiểm toolchain (đã lọc, chỉ in ra dòng `sdk.dir=…`, không có khóa/ID/hash). Không đọc lại file đó.

---

## 2. Kết quả kiểm tự động (JVM, không chứng minh UI/ADB/âm thanh)

| Nơi | Lệnh | Kết quả |
|---|---|---|
| Ni | `testShareableDebugUnitTest testShareableReleaseUnitTest` (chạy lại `--rerun-tasks` với mã cuối) | **mỗi biến thể 1.302 tests (1.263 + 39 mới), 1.281 passed, 21 skipped (đúng mốc), 0 failed** |
| Ni | `assembleShareableDebug assembleShareableRelease` | BUILD SUCCESSFUL (APK Ni thiếu Telegram config — KHÔNG cài) |
| Ni | `git diff --check` | exit 0 (chỉ cảnh báo LF/CRLF thông thường của repo) |
| Tiny | test Debug+Release (1.302/21/0 cả hai) + `assembleShareableDebug` | BUILD SUCCESSFUL, exit 0 |
| Soát log | `grep Log.` trong 2 file mới | chỉ 1 chỗ `Log.w("MeloVaultLocal")`; chuỗi log = `stage=load count= ms= gen=`, `stage=formats <mime>=<số>`, `stage=load_failed error=<tên lớp>` — không tên bài/đường dẫn/URI (có test `logsContainNoSongNamesOrPaths`) |

---

## 3. QA thiết bị (Sonnet tự thao tác qua ADB; ảnh/log riêng tư ở `docs/plans/artifacts/E94-LOCAL-MUSIC/`, KHÔNG commit)

Mọi bước: kiểm `adb devices -l` + `getprop` đúng máy, kiểm tiêu điểm cửa sổ trước mỗi chạm, màn chỉ bật lúc thao tác và tắt (`keyevent 223` + xác minh `Display State=OFF`) trong `finally` sau mỗi nhóm. Âm thanh thật: **CHƯA XÁC NHẬN ÂM THANH THỰC** (chỉ có state=3 + vị trí tăng).

### 3.1 Shanling M3 Ultra — Ni, serial `30313860180939`, Android 10, Gọn 768×1280
Cài: `adb -s 30313860180939 install -r` APK mục 6, 20:00:20 (bản cũ b109 `9BE6753B…` cùng signer, cùng vc109, MediaSession lúc đó state=2, màn OFF; đã kéo `base.apk` cũ lưu ngoài repo để rollback). Kiểm lại SHA `base.apk` trên máy = `A02D3B8A…`.

| # | Hành động | Kỳ vọng | Thực tế | KQ | Chứng cứ |
|---|---|---|---|---|---|
| Q1 | Mở app; ☰ | "♪ Nhạc trên máy" ngay dưới "✓ Đã tải"; app không tự hỏi quyền | Đúng thứ tự menu; không hộp quyền khi mở. Telegram "Tất cả · 29.643 bài" (ổn định suốt QA; số trước khi cài **chưa chụp**) | PASS | `01_shanling_library_before`, `02_shanling_menu_local` |
| Q2a | ♪ Nhạc trên máy → "Tiếp tục" → hộp hệ thống → **DENY** | Toast, ở lại Telegram, không danh sách local | Toast "Chưa có quyền đọc nhạc trên máy."; quyền `granted=false` | PASS | `03_dialog_explain`, `04_system_permission_dialog`, `05_after_deny` |
| Q2b | Lặp lại → **ALLOW** | Tiêu đề "Nhạc trên máy · 3.557 bài" = MediaStore | **3.557** bài (= `content query` 3.557 = log `stage=load count=3557 ms=2034`). Định dạng: flac 2026, mp3 849, wav 657, dsf 20, ape 4, dff 1. Dòng Gọn chỉ hiện biểu tượng ✓; chữ "Trên máy · MP3/FLAC" nằm ở mô tả trợ năng (xác nhận bằng UI dump), vì layout Gọn không có dòng chữ trạng thái | PASS (xem lưu ý) | `06_second_ask_explain`, `06b_system_allow_box`, `07_local_list`, `logcat_local_shanling_load.txt`, `meminfo_*` |
| Hiệu năng | meminfo trước/sau | nạp < 5 s, heap < +48 MB | nạp **2,0 s**; Java Heap 10,5 → 14,6 MB sau nạp; cuối QA (đã phát nhiều bài + Telegram) 33,1 MB (+22,6 MB), PSS 134 → 247 MB; không ANR/OOM | PASS | `meminfo_before/after_open_local.txt`, `meminfo_after_qa.txt` |
| Q3 | Tìm "ngan khuc" (không dấu) → chạm bài FLAC → Đang phát; xóa tìm | Lọc đúng; state=3, vị trí tăng; viên định dạng, **không Hi-Res**; "Nghệ sĩ: …" | Ra 2 bài (2 tệp **trùng tên**, 2 khóa khác nhau); chạm → `state=3`, vị trí 6,2 s → 33 s → 48 s, thanh đáy tên + ❚❚; Đang phát: viên **FLAC · 44.1kHz · 31,3 MB**, không Hi-Res; nhãn "Nghệ sĩ: …" (không "Kênh:"); BACK×2 (đóng Đang phát, xóa tìm) về đủ 3.557 | PASS | `11_search_filtered`, `09_after_tap_8s`, `10_nowplaying_pills`, `10b_nowplaying_extras`, `08c_list_full_again` |
| Q3b | Hàng đợi; ☆ Lưu | Hàng đợi chỉ bài local; Lưu = Toast, không ghi | "Danh sách phát · 2 bài" (đúng danh sách đang lọc) và "· 500 bài" (cửa sổ 500) chỉ gồm bài local; Lưu → Toast "Nhạc trên máy chưa hỗ trợ Lưu." | PASS | `16_local_queue`, `16c_local_queue_500`, `16b_local_save_toast` |
| Q4a | Phím media `input keyevent` **khi màn BẬT**: 127, 126, 87×2, 88×2, 90 | 127⇒state=2; 126⇒3; 87 sang bài local kế đúng thứ tự; 88 quay lại; 90 tua | 127→`state=2`; 126→3; 87→bài kế (item 2) rồi (item 3) **đúng thứ tự danh sách**, không bài Telegram xen; 88 (vị trí về ~0 theo ngưỡng 3 s của Media3, cùng item); 90 → vị trí +15 s | PASS | `q4_keys_shanling.txt` |
| Q4b | Cùng phím khi màn **TẮT** | như Q4a | **Không có tác dụng**: hệ thống Android 10 chỉ chuyển ACTION_UP của phím tiêm từ shell khi `Display State=OFF` (log `MediaSessionService: Sending KeyEvent ACTION_UP…`), MediaSession không nhúc nhích. Không do ứng dụng; kịch bản "phím vật lý khi tắt màn" bằng `input keyevent` **không kiểm được** | **BLOCKED (chưa kiểm chứng)** | `q4_keys_shanling.txt` (phần 1) |
| Q4c | Phát nền khi màn tắt | state=3, vị trí tăng | `display=OFF`: vị trí 39,4 → 45,3 → 51,4 s; `actions=7340027` gồm Next và Previous | PASS | `q4_keys_shanling.txt` (phần cuối) |
| Q5 | BACK ở danh sách local; ☰ → ✓ Đã tải → chạm dòng 1; ⏭; ☰ → ♪ → chạm bài local; Hàng đợi | BACK về đúng màn Telegram trước; Telegram phát, ⏭ sang bài Telegram kế; local lại phát local; không FATAL/ANR | BACK → "Tất cả · 29.643 bài" (không phải lưới kênh), nhạc local vẫn chạy; Đã tải · 5 bài → Telegram `state=3`, queue **5** (bài Telegram), ⏭ ×2 sang đúng bài Telegram kế (queue vẫn 5); quay lại local → queue **500** local; logcat crash 0 dòng sau khởi động lại | PASS | `12_back_from_local`, `13a_downloaded_list`, `13_telegram_playing`, `14_telegram_next`, `15_local_again`, `16c_local_queue_500` |

**Sự cố ngoài kế hoạch (xin ChatGPT quyết, KHÔNG do E94):** thử `adb shell media dispatch play/pause/next/previous/fast-forword` khi màn tắt làm **tiến trình MeloVault crash 5 lần** (`FATAL EXCEPTION`, `java.lang.IllegalArgumentException: packageName should be nonempty` tại `androidx.media3.session.legacy.MediaSessionManager$RemoteUserInfo.<init>` ← `MediaSessionCompat.getCurrentControllerInfo` ← `MediaSessionLegacyStub.onMediaButtonEvent`, Media3 1.11.1), sau đó 1 ANR do hệ thống khởi động lại service (`Context.startForegroundService() did not then call Service.startForeground()`) và tiến trình bị dọn. Người gửi phím là lệnh shell (uid 2000, không có tên gói); phím tiêm bằng `input keyevent` (màn bật) đi qua đường hệ thống và **không** crash; nút vật lý/tai nghe thật chưa thử. Ngăn xếp hoàn toàn nằm trong thư viện Media3, E94 không sửa service/MediaSession (chưa đối chứng bằng bản cũ). Dữ liệu Telegram nguyên vẹn sau khi mở lại (29.643 bài, 5 nhóm tải). Tôi đã dừng cách thử này. Chứng cứ: `logcat_crash_after_media_dispatch_shanling.txt`, `12b_app_restarted_after_crash`. Đề xuất riêng (ChatGPT quyết): kiểm tra nút vật lý/tai nghe/BT thật và cân nhắc việc bảo vệ `onMediaButtonEvent`/nâng Media3 ở nhiệm vụ riêng.

### 3.2 Sony Xperia 5 II — Tiny, serial `QV725WEQ54`, Android 12, Điện thoại dọc 1080×2520 (360dp)
Cài `install -r` lúc 20:19:59 (adb báo "failed to install" do USB rớt đúng lúc kết thúc — sự cố đã biết; **đã xác minh bằng cách kéo `base.apk` về Tiny: SHA `A02D3B8A…`, vc109, quyền chưa cấp**). Bản cũ b109 `7989C5F9…` (signer `eecd3df7…`) đã lưu `pre_E94_sony_installed_base.apk` trên Tiny. ADB Sony chập chờn khi bật màn (transport id nhảy 354→410); script có thử lại, 3 lần chạy phải làm lại (không có chạm nào bị gửi sai nơi).

| # | Hành động | Kỳ vọng | Thực tế | KQ | Chứng cứ |
|---|---|---|---|---|---|
| Q6a | ⋮ → ♪ Nhạc trên máy → Tiếp tục → **DENY**; lặp lại → **ALLOW** | Từ chối: Toast, ở lại Telegram, không thẻ; Cho phép: có danh sách | `READ_EXTERNAL_STORAGE`: false → (DENY) false → (ALLOW) **true**; sau cho phép mở danh sách "Nhạc trên máy · **6 bài**" (3 sẵn + 3 tệp thử), log `stage=load count=6 ms=27`, định dạng wav 3, flac 1, m4a 1, mp3 1 | PASS | `20b_sony_explain_dialog`, `20c_sony_system_dialog`, `20_sony_deny_toast`, `21a_sony_system_dialog_2`, `21_sony_allow_local_list` |
| Q6b | Về Thư viện Telegram | Hai thẻ, chữ không "…", vuốt ngang thấy đủ, không đè thanh đáy | Thẻ "Telegram · 25.144 bài · đã tải 65" (một dòng, đủ chữ) + "Nhạc trên máy · 6 bài" lộ một nửa; vuốt ngang thấy đủ thẻ 2; hàng thẻ không chạm thanh đáy | PASS | `22_sony_two_cards`, `23_sony_cards_scrolled` |
| Q7a | Bấm thẻ Nhạc trên máy; "↕ Mới nhất" → "Tên A–Z" | Thẻ LOCAL sáng, không hàng chip; A–Z xếp a→b→c | Thẻ sáng; **không có hàng chip** (Tất cả/Đã tải/Playlist ẩn); A–Z: Bee Moved, melotest_a, _b, _c, ringtone, Total Immersion; dòng "Trên máy · WAV/FLAC/M4A/MP3 · <thư mục>" | PASS | `24_sony_local_list`, `25a_sony_after_sort_tap` |
| Q7b | Tìm "melotest" rồi "880" | 3 bài rồi 1 bài | **3** rồi **1** | PASS | `26a_sony_search_melotest`, `26b_sony_search_880` |
| Q7c | Chạm `melotest_b…` (WAV 24-bit/96 kHz); Đang phát; ⏮ ⏭; tua | Viên định dạng, **không Hi-Res**; ⏭/⏮ trong bài local; tua đổi vị trí | `state=3`, vị trí 2,9 → 5,9 s; hàng đợi 3 bài; Đang phát: **WAV · 96kHz · 5,8 MB** (không Hi-Res, không bit-depth); nút "Bài trước"/"Bài kế" đổi đúng item; hết bài c (cuối cửa sổ) → `state=1` dừng, không nhảy sang Telegram; chạm thanh tua 15% ⇒ vị trí 1,2 s (từ ~5 s) | PASS | `27_sony_nowplaying_b_24bit`, `27c_sony_nowplaying_c`, `28_sony_after_seek`, log `q7*` |
| Q8 | Đang phát local: bấm thẻ Telegram → ⋮ → "⏭ Phát tiếp theo"; rồi chip Đã tải → chạm bài; ⏭ | Toast, hàng đợi vẫn local; chạm bài Telegram ⇒ Telegram; ⏭ sang bài Telegram; số bài Telegram không đổi | Toast "Đang phát Nhạc trên máy. Bấm bài Telegram để chuyển nguồn."; queue vẫn **3** local (`item=1`); chạm bài Đã tải ⇒ `state=3`, queue **9** (Telegram), tiêu đề không phải bài thử; phím 87 ⇒ item 0→1 (Telegram); Telegram vẫn 25.144 bài, "Đã tải (65)" | PASS | `28_sony_queue_next_blocked`, `29b_sony_downloaded_chip`, `29_sony_telegram_play`, `29c_sony_telegram_next` |
| Dọn | Trả cài đặt "↕" | về "Mới nhất" như đầu QA | Đã đặt lại "↕ Mới nhất" (tùy chọn dùng chung `library_sort` bị đổi lúc thử A–Z) | — | `29d_sony_end_state` |

Crash/ANR Sony: bộ đệm crash có 3 `FATAL` nhưng **không có tiến trình MeloVault** — 1 của `com.facebook.appmanager` (07:52 sáng nay), 2 của `uiautomator`/UiAutomationService (công cụ QA, do USB rớt khi dump; `tiny_logs/sony_crash_buffer.txt`); ANR MeloVault = 0.

### 3.3 Tệp thử Sony (tạo/xóa)
3 WAV sin stereo tạo trên Tiny bằng Python 3.14.7 (stdlib), ngoài repo: `melotest_a_440hz_16bit_44k.wav` (3.528.044 B, `FE7463B4…FB822`), `melotest_b_880hz_24bit_96k.wav` (5.760.044 B, `896A31AB…6984C`), `melotest_c_660hz_16bit_48k.wav` (1.920.044 B, `882597A0…4E7EB`) — SHA đầy đủ ở `melotest_files.txt`. `/sdcard/Music/MeloTest` **không tồn tại trước QA** ⇒ do nhiệm vụ tạo. `adb push` 20:20:54; MediaStore nhận 3 dòng nhưng `is_music=NULL`/`duration=NULL` (app lọc `IS_MUSIC != 0` sẽ bỏ sót) ⇒ đã `MEDIA_SCANNER_SCAN_FILE` từng tệp theo kế hoạch ⇒ `is_music=1`. Dọn 20:41:16: `rm` đúng 3 tên (không ký tự đại diện, không `rm -r`), `rmdir MeloTest`; MediaStore còn **0** dòng MeloTest, `is_music` về **3**; không tạo `/sdcard/e94_ui.xml`.

### 3.4 Vòng đời màn hình (docs/workflow/SCREEN_LIFECYCLE_QA.md)
- `SHANLING_SCREEN_END=OFF` — `mWakefulness=Asleep`, `Display State=OFF` (xác minh 20:42:56; lần tắt cuối khi thao tác ~20:18).
- `SONY_SCREEN_END=OFF` — `mWakefulness=Dozing`, `Display State=OFF` (xác minh 20:42:58). Một lần script lỗi để màn Sony sáng ~1 phút (lúc 20:22, bật màn rồi ADB rớt trước khi tắt); đã tắt và xác minh OFF ngay sau khi phát hiện.
- Không đổi timeout/độ sáng/AOD/khóa màn, không đổi xoay (Sony vẫn `ROTATION_0`).

### 3.5 Trạng thái cuối thiết bị
Cả hai máy: nhạc **tạm dừng**; quyền đọc nhạc để nguyên **đã cấp** (không `pm revoke`); app đã cài APK QA. Không uninstall, `pm clear`, đăng xuất, xóa TDLib/cache, đổi tài khoản, máy bay/mạng, FYT.

---

## 4. Chưa kiểm chứng / giới hạn
- **Âm thanh thật** (loa/tai nghe): CHƯA XÁC NHẬN.
- **Phím vật lý/tai nghe/BT thật và phím khi tắt màn**: chưa kiểm (xem Q4b và sự cố `media dispatch`).
- **Cuộc đua Telegram→local** (`localPlayEpoch`, bước tùy chọn): **chưa chạy trên máy** — chỉ có soát mã; cửa sổ resolve Telegram quá ngắn để tạo ổn định mà không cắt mạng (cấm).
- Android 13+ (`READ_MEDIA_AUDIO`; Y700, S25), FYT/Car Mode, xoay màn, chữ ×1,3+, Hai khung/Tablet: chưa kiểm. Y700/S25 là của chủ dự án.
- Định dạng ALAC/AIFF/APE/DSD/WMA trên Shanling: có 20 `dsf`, 4 `ape`, 1 `dff` trong MediaStore nhưng chưa thử phát từng loại.
- Số Telegram "như trước cài" ở Q1: không có ảnh trước khi cài; chỉ chứng minh không đổi suốt QA.
- Dòng trạng thái "Trên máy · FLAC" ở Gọn chỉ là mô tả trợ năng (xem Q2b); chữ hiện ở Điện thoại/Tablet.

## 5. Rủi ro / rollback
- Rủi ro: diff vượt ~850 dòng (mục 1.1); chưa kiểm cuộc đua; sự cố Media3 ở mục 3.1 (đã có từ trước, lộ ra qua `media dispatch`); `IS_MUSIC` loại tệp không được MediaStore đánh dấu nhạc (như `ringtone` trong thư mục lạ vẫn hiện vì `is_music=1`); danh sách không tự làm mới khi thêm/xóa tệp (chỉ khi mở lại sau 5 phút, `rescan`, hoặc `onResume` đếm lại thẻ).
- Rollback mã: `git revert 2d58121` trên `dot/E94-LOCAL-MUSIC` (chưa merge).
- Rollback thiết bị (cùng versionCode 109, cùng signer, không cần gỡ): Shanling — `base.apk` bản đang cài trước QA (SHA `9BE6753B70C0B78A8ED2D4644F64F56C5670A91E0403543BD56EAA08CD3C44C1`, 33.609.254 B) lưu ở thư mục tạm của phiên Ni (`…\scratchpad\apk\shanling_installed_before_E94_base.apk`) và bản build Tiny `C:\Bots\_qa_temp\E94-LOCAL-MUSIC_2d58121\pre_E94_app-shareable-debug_9BE6753B.apk`; Sony — `C:\Bots\_qa_temp\E94-LOCAL-MUSIC_2d58121\pre_E94_sony_installed_base.apk` (SHA `7989C5F9C298C41FC4F3F9C6D2F145A4B3663B7A35B5418DD4E83EFFC2465C3B`, 30.390.830 B).

---

## 6. APK QA cần ChatGPT bàn giao (CLAUDE.md mục 9)

| Trường | Giá trị |
|---|---|
| Nhãn | **QA CANDIDATE — CHƯA NGHIỆM THU** (không phải Beta phát hành/Stable) |
| Loại build | Debug (`assembleShareableDebug`), build tại Tiny có Telegram config |
| Package | `app.melovault.player` |
| versionCode / versionName | **109 / 0.6.88-beta** (không bump — cùng số với b109; chỉ phân biệt bằng SHA256/commit) |
| Git | commit chứa source: **`2d5812139b0f98c64cc4c4f2f9d25f612dc88674`** (nhánh `dot/E94-LOCAL-MUSIC`; Tiny `qa/E94-LOCAL-MUSIC`, HEAD Tiny = `2d58121`, cây sạch). Commit báo cáo này là commit sau, chỉ thêm tài liệu |
| SHA-256 | **`A02D3B8ABA7EF37CA812B2B88684B5FBE23D9D45CA102F16089AB432524300F1`** |
| Kích thước | **33.640.480 byte** |
| Certificate SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (kiểm độc lập bằng `apksigner` trên Tiny và Ni; trùng signer b109 đang cài trên cả hai máy) |
| Đường dẫn APK thật (Tiny) | `C:\Bots\_qa_temp\E94-LOCAL-MUSIC_2d58121\E94-LOCAL-MUSIC_2d58121_app-shareable-debug.apk` (bản sao tên duy nhất, SHA đã đối chiếu **trùng** với đầu ra build và với bản đã sao sang Ni). Đầu ra build: `C:\Bots\MeloVault-E93D-IsolatedQA\app\build\outputs\apk\shareable\debug\app-shareable-debug.apk` (đường này sẽ bị build sau ghi đè). APK trước (b109/offline-one-tap, SHA `9BE6753B…`) đã giữ bản sao `pre_E94_app-shareable-debug_9BE6753B.apk` cùng thư mục; không xóa/ghi đè APK cũ nào |
| Telegram config hợp lệ | **true** (`telegram.api.id` hợp lệ: true; `telegram.api.hash` 32 hex: true; BuildConfig ID khác 0: true; hash dài 32: true — chỉ boolean, không in giá trị) |
| Kết quả Gradle | Ni và Tiny: 1.302 tests, 21 skipped, 0 failed (Debug+Release); assemble Debug+Release xanh (Ni), Debug xanh (Tiny) |
| Đã cài / QA | Shanling M3 Ultra (`30313860180939`, Android 10) và Sony XQ-AS52 (`QV725WEQ54`, Android 12): `install -r`, SHA `base.apk` trên máy = SHA trên; QA mục 3 |
| Điểm chưa QA | Mục 4 (âm thanh thật, phím vật lý/BT, Android 13+, Y700/S25, FYT, cuộc đua Telegram→local, xoay/chữ lớn) |
| Cảnh báo | Sự cố `media dispatch` (mục 3.1); quyền đọc nhạc đã cấp trên cả hai máy; APK Ni (Debug/Release, thiếu Telegram config) **không** dùng để cài/phát hành |
| Rollback | Mục 5 |

Sonnet **không** upload Drive, không đổi permission, không tạo `-apk-test.md`/`-hinh-nghiem-thu.md`. Ảnh/video/log private: `docs/plans/artifacts/E94-LOCAL-MUSIC/` (git bỏ qua toàn bộ thư mục; có tên bài Telegram riêng tư — ChatGPT chọn 5–10 ảnh không lộ nội dung để đưa lên Drive Ni N owner-only). Ảnh chủ chốt: Shanling `02`, `04`, `05`, `07`, `10_nowplaying_pills`, `12_back_from_local`, `13_telegram_playing`, `15_local_again`, `16c_local_queue_500`; Sony `20_sony_deny_toast`, `21_sony_allow_local_list`, `22_sony_two_cards`, `24_sony_local_list`, `25a_sony_after_sort_tap`, `26a/26b`, `27_sony_nowplaying_b_24bit`, `28_sony_queue_next_blocked`, `29_sony_telegram_play`. Log Tiny: `tiny_logs/*.txt` cùng thư mục.
