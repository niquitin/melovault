# Kế hoạch — E100-THANH-DAY-CHUA-PHAT (thanh đáy gộp khi chưa có bài đang phát)

## 0. Bối cảnh và mục tiêu

Ảnh chủ dự án 26/10 (điện thoại Teal): khi mở app mà chưa có bài nào trong trình phát, thẻ "Đang phát" của Thanh đáy gộp để trống (`MeloBottomBar.update(null)` đặt thẻ `INVISIBLE`, ▶ mờ và không bấm được). Chủ dự án CHỐT (HANDOFF 26/10) và đã trả lời các điểm [CẦN XÁC NHẬN] trong phiên Opus này:

| Câu hỏi | Chủ dự án chọn (26/10) |
|---|---|
| Nghe tiếp từ đâu | **Từ chỗ đã dừng nếu bài đã tải đủ trên máy; bài chưa tải thì phát từ đầu** |
| Hàng đợi khi nghe tiếp | **Danh sách đang xem** (giống bấm 1 bài: bài nằm trong danh sách đang mở → Bài kế theo danh sách đó; không thì chỉ 1 bài). Không lưu/khôi phục hàng đợi cũ |
| Nhạc trên máy | **Chỉ nhớ bài Telegram**; bài "Nhạc trên máy" không ghi đè |
| Xóa khi đăng xuất / đổi tài khoản | Bắt buộc (HANDOFF) |
| file_id | Luôn lấy mới qua `getMessage` (đường `downloadAndPlay` sẵn có) |

Mục tiêu, áp dụng cho **Gọn (Shanling), Điện thoại dọc, Tablet dọc** (3 màn dùng `MeloBottomBar`):
1. **Có bài nghe lần trước của đúng tài khoản** → thẻ hiện ảnh (nếu đang có trong bộ nhớ, không thì ô chữ cái), tên bài, dòng 2 "Nghe tiếp · <kênh>". Bấm thẻ hoặc ▶ = phát bài đó. **Không tự phát khi mở app.**
2. **Chưa có bài nào** → thẻ gợi ý "Chưa phát bài nào" / "Bấm để phát danh sách đang xem"; bấm thẻ hoặc ▶ = Phát hết (`MeloScreenHost.playAll(false)`). Khi danh sách đang xem không phát được (màn Telegram/nguồn, danh sách rỗng, bài đầu là bài Playlist chưa có trong thư viện) → dòng 2 "Chọn một bài trong danh sách", thẻ và ▶ không bấm được.
3. Khi đang có bài (kể cả tạm dừng): **giữ nguyên** hành vi E98/E99 (thẻ mở Đang phát, ▶ = Phát/Dừng).

Không đổi: Car Mode (`MeloCarScreen`), Hai khung (`MeloTabletScreen`), giao diện cũ (Cockpit/Portrait), kích thước thanh đáy (Gọn 102 dp, 8 dòng).

## 1. Git baseline (đọc 26/10/2026)

| Mục | Giá trị |
|---|---|
| Nền | `feature/e93-03d-y700-offline-cockpit` @ **`c606424`** (docs handoff trên merge E99 `6510d05`), cây sạch, `git diff --check` sạch |
| App | vc115 / `0.6.90.1-beta` (`app/build.gradle:93-95`); Shanling + Sony đang cài vc115 |
| Test nền | 2042 test mỗi biến thể, 21 bỏ qua, 0 lỗi (Opus `--rerun` 25/10 trên cây E99) |
| Nhánh | `dot/E100-THANH-DAY-CHUA-PHAT` cắt từ `c606424`; commit kế hoạch: `<PLAN_SHA>` (Opus điền sau khi commit) |

## 2. Nguyên nhân gốc (đã đối chiếu mã)

- `MeloBottomBar.kt:200-219` — `update(null)`: thẻ `INVISIBLE`, `card/view.isClickable=false`, ▶ alpha 0.4 + disabled; `onPlayClick` (239) và cử chỉ mở (244-256) chỉ chạy khi `hasSong`.
- `MeloBottomBarPolicy.kt:79-84` — `showsSongInfo/playEnabled/playAlpha` = `hasSong`.
- `TelegramPreviewActivity.kt:5289` `nowPlaying()` trả null khi controller chưa có `currentMediaItem` (mở app lạnh, sau `install -r`, sau khi service dừng).
- **Không có bất kỳ lưu trữ bài/vị trí nghe cuối**: service chỉ giữ hàng đợi trong RAM (`ShareablePlaybackService.kt:85-87`, xóa ở `onDestroy`); `restorePlayingQueueIfAny` (Activity 3709) chỉ chép hàng đợi RAM của service. Media3 không dùng `onPlaybackResumption`.
- `startPlayback` (Activity 1138-1143) luôn `setMediaItems(items, startIndex, C.TIME_UNSET)` — chưa có tham số vị trí bắt đầu.

## 3. Phương án

### 3.1 So sánh

| Phương án | Ưu | Nhược | Chọn |
|---|---|---|---|
| A. Activity ghi bản ghi nhỏ (chỉ số) vào SharedPreferences riêng theo accountId; tra tên/kênh từ DB khi mở app; bấm = `downloadAndPlay` sẵn có | Nhỏ, tái dùng đường phát đã kiểm (getMessage, file_id mới, hàng đợi theo danh sách); không đụng service/DB schema; mẫu y hệt `MeloDownloadedCountPolicy` (prefs theo account, `clear()` khi đăng xuất) | Bài kế đổi bằng màn khóa khi Activity đã bị hủy không được ghi | **Chọn** |
| B. Service ghi bản ghi (onMediaItemTransition trong `ShareablePlaybackService`) | Ghi cả khi Activity không còn | Đụng service Media3 (rủi ro cao hơn, vùng crash Legacy còn mở), hai nơi ghi | Không |
| C. Media3 `MediaSession.Callback.onPlaybackResumption` + lưu hàng đợi | Chuẩn Android, nút tai nghe/BT phát lại | Lớn, đụng service/hàng đợi/quyền thông báo; chủ dự án chọn không khôi phục hàng đợi | Không |

### 3.2 Thiết kế chi tiết

**Lưu trữ** — prefs mới `melovault_resume` (chỉ số, không tên bài/kênh/đường dẫn):
- `resume_song_<accountId>` (Long songKey = `Song.messageId`), `resume_pos_<accountId>` (Long ms), `resume_at_<accountId>` (Long thời điểm).
- Ghi trên main thread bằng `apply()`, **chỉ khi** `auth.isReady()` và `library.authorizedUser()?.value > 0` và mediaId bắt đầu bằng `telegram:` (bỏ `picked:audio`, nhạc trên máy theo `MeloLocalSourcePolicy.idOfMediaId`).
- Thời điểm ghi (trong `playbackListener`, Activity 952-1003, và `onPause` 917):
  - `onMediaItemTransition` (mediaId telegram) → songKey + pos 0.
  - `onIsPlayingChanged(false)` và `onPause()` → pos = `controller.currentPosition` cho đúng songKey hiện tại (chỉ khi `currentMediaItem` là telegram và khớp key).
- Xóa: gọi `clearResume()` (prefs `.edit().clear().apply()` + biến RAM về null) ở **cả hai** đường đăng xuất: `logoutUser()` (cạnh `clearDownloadedCount()` 7215) và nhánh TDLib LoggingOut/Closing/Closed (cạnh 6305). Gọi **sau** `clearPlaybackSession()`; điều kiện `auth.isReady()` chặn listener ghi lại khi controller dừng.

**Nạp khi mở app** — trong `loadDatabaseIndex` (Activity 6571), ngay sau `startDownloadedCountFlow(userId)` (6587) gọi `startResumeFlow(userId)`:
- `executeOnDb {}`: đọc prefs của `userId` → `MeloResumePolicy.record(...)`; nếu có → `db.getSongByKey(userId, songKey)`; null hoặc `isDeleted` → bỏ (xóa key của account đó).
- `runOnUiThread`: kiểm lại `auth.isReady() && authorizedUser == userId && !isDestroyed` (giống 6582), lưu `resumeSong = indexedToSong(row)` + `resumePositionMs`, rồi `notifyMeloPlayback()` để thanh đáy vẽ lại.
- Khi transition ghi bài mới thì cũng cập nhật `resumeSong` trong RAM (tra `shownSongs` → `queueSongOrder` → `songs` theo key, như `nowPlaying()` 5294-5296).

**Bấm Nghe tiếp** — `meloHost.playResume()`:
- Lấy song: ưu tiên bản trong RAM (`shownSongs`/`songs` cùng key), không thì `resumeSong`. Gọi `downloadAndPlay(song, resumeFromMs = resumePositionMs)`.
- `downloadAndPlay(song: Song, resumeFromMs: Long = 0L)`: chỉ nhánh **đã tải đủ** (7096-7104) truyền vị trí: `enqueueTelegramPlayback(resolvedSong, localFile, MeloResumePolicy.startPositionMs(resumeFromMs, song.durationSeconds, fileComplete = true))`. Nhánh progressive (7105+) **luôn từ đầu** (không truyền). `playIfDownloaded` giữ nguyên (không có vị trí).
- `enqueueTelegramPlayback(song, target, startPositionMs = 0L)` → `enqueueItems(items, startIndex, startPositionMs = 0L)` → `PendingPlayback(items, startIndex, positionMs = 0L)` → `startPlayback(controller, items, startIndex, positionMs)`: `setMediaItems(items, startIndex, if (positionMs > 0) positionMs else C.TIME_UNSET)`. Mọi lời gọi cũ dùng mặc định 0 ⇒ hành vi cũ không đổi.
- Hàng đợi: không đổi gì — `TelegramQueueRetentionPolicy.forSelected` (danh sách đang xem nếu chứa bài, không thì hàng đợi trước, không thì 1 bài) đúng lựa chọn chủ dự án.
- Lỗi `getMessage` (bài bị xóa trên Telegram, mạng): Toast sẵn có của `reportPlayError`; giữ bản ghi (lần phát bài khác sẽ ghi đè).

**Host (MeloScreenHost.kt)** — thêm, đều có mặc định để Car/Tablet/test giả không đổi:
```kotlin
data class Resume(val songKey: Long, val title: String, val sourceTitle: String?, val art: Bitmap?)
fun resumeCard(): Resume? = null
fun canPlayAll(): Boolean = false
fun playResume() = Unit
```
Activity cài: `resumeCard()` = `resumeSong` → `Resume(key, TelegramSongDisplayPolicy.label(title, filename), sourceTitle ?: sourceNames[chatId], art = thumbnailCache.get(key) ?: (nếu rowArtOn()) peekRowArt(MeloRowArtPolicy.telegramKey(acc, chatId, genuineMessageId)))` — **chỉ đọc, không gọi requestRowArt** (tránh làm lệch hàng đợi ảnh của danh sách). `canPlayAll()` = `auth.isReady()` và: localMode → `localMusic.shown().isNotEmpty()`; còn lại → `!(allChatsMode && browseSources && !playlistMode)` (không ở màn nguồn, công thức như `snapshot()` 5076) và `shownSongs.firstOrNull()?.placeholder == false`.

**Chính sách thuần JVM**
- File mới `MeloResumePolicy.kt`: `PREFS_NAME`, `songKey/posKey/atKey(accountId)`, `data class Record(accountId, songKey, positionMs, savedAtMs)`, `record(accountId, songKey: Long?, positionMs, savedAtMs): Record?` (null khi accountId ≤ 0, songKey null/0), `songKeyOf(mediaId: String?): Long?` (chỉ `telegram:<số>`), `shouldWrite(authReady, accountId, mediaId)`, `startPositionMs(savedMs, durationSeconds, fileComplete)`: 0 nếu `!fileComplete`, savedMs < `MIN_RESUME_MS = 5_000`, hoặc (durationSeconds > 0 và savedMs > durationSeconds·1000 − `END_GUARD_MS = 10_000`); ngược lại savedMs.
- `MeloBottomBarPolicy.kt` thêm (giữ nguyên các hàm cũ và test cũ):
  - `enum class Idle { RESUME, PLAY_ALL, PICK }`, `idle(hasResume: Boolean, canPlayAll: Boolean): Idle` (RESUME thắng).
  - `idleActionable(idle) = idle != PICK`; `HINT_TITLE = "Chưa phát bài nào"`, `HINT_PLAY_ALL = "Bấm để phát danh sách đang xem"`, `HINT_PICK = "Chọn một bài trong danh sách"`, `RESUME_LABEL = "Nghe tiếp"`, `resumeLine(sourceTitle)` = "Nghe tiếp · <kênh>" hoặc "Nghe tiếp", `idleDescription(idle, title, sourceTitle)` (TalkBack: "Nghe tiếp: <bài · kênh>. Bấm để phát" / "Chưa phát bài nào. Bấm để phát danh sách đang xem" / "Chưa phát bài nào. Chọn một bài trong danh sách"), `idlePlayDescription(idle)` = "Nghe tiếp" / "Phát hết" / "Phát".

**View (MeloBottomBar.kt)**
- `update(null)`: không ẩn thẻ nữa. Tính `idle = MeloBottomBarPolicy.idle(host.resumeCard() != null, host.canPlayAll())`; thẻ `VISIBLE`, thanh tiến trình vẫn `INVISIBLE`; RESUME: tên bài + `resumeLine`, `showArt` dùng ảnh/khóa của Resume (tách `showArt(art, sourceTitle, title)` dùng chung với NowPlaying); PLAY_ALL/PICK: tiêu đề `HINT_TITLE`, dòng 2 tương ứng, ô ảnh = ô chữ cái "♫"/tiêu đề qua `c.artView(null, null, HINT_TITLE, …)` [Sonnet chọn ký tự ô theo `artView` sẵn có, không thêm drawable].
- `card.isClickable = view.isClickable = idleActionable`; ▶: alpha/enabled theo `idleActionable`, chữ "▶", `contentDescription = idlePlayDescription`.
- Thêm `private var idleShown: Idle?` + key bài để không đặt lại view khi không đổi (update gọi mỗi giây khi phát).
- Bấm: `onPlayClick()` và click thẻ: `hasSong` → như cũ; RESUME → `host.playResume()`; PLAY_ALL → `host.playAll(false)`; PICK → không làm gì. Vuốt lên (fling) **chỉ** khi `hasSong` (giữ nguyên).
- Thêm `fun refreshIdle() { if (!hasSong) update(null) }`; khi `has` chuyển true→false/false→true vẫn chạy khối hiện có (`hasSong` vẫn là cờ "đang có bài").
- `MeloCompactScreen.onLibraryChanged()` và `MeloPhoneScreen.onLibraryChanged()` gọi `bar.refreshIdle()` ở cuối (để PLAY_ALL/PICK theo danh sách đang mở).

### 3.3 Không làm / bảo vệ
- Không đụng `ShareablePlaybackService`, `MeloCarScreen`, `MeloTabletScreen`, DB/schema, TDLib handler, `MeloRowArtLoader`/`MeloRowArtPolicy`, Playlist store, signer/package. Không tự phát khi mở app; không gọi `prepare()` sẵn.
- Không lưu tên bài/kênh/đường dẫn/file_id vào prefs; file_id luôn mới qua `getMessage`.
- Không khôi phục hàng đợi cũ; không nhớ nhạc trên máy.
- Không thêm log có tên bài; nếu cần log chỉ `MeloResume stage=… hasRecord=… pos=<giây>`.

## 4. File sẽ đổi (ước lượng ~170–220 dòng production + ~120 dòng test)

| File | Thay đổi |
|---|---|
| `MeloResumePolicy.kt` (mới) | khóa prefs, Record, songKeyOf, shouldWrite, startPositionMs |
| `MeloBottomBarPolicy.kt` | Idle, chữ gợi ý/Nghe tiếp, mô tả TalkBack |
| `MeloBottomBar.kt` | trạng thái chờ (RESUME/PLAY_ALL/PICK), bấm, `refreshIdle()` |
| `MeloScreenHost.kt` | `Resume`, `resumeCard()`, `canPlayAll()`, `playResume()` (mặc định) |
| `MeloCompactScreen.kt`, `MeloPhoneScreen.kt` | 1 dòng `bar.refreshIdle()` mỗi file |
| `TelegramPreviewActivity.kt` | ghi/xóa/nạp bản ghi, cài 3 hàm host, tham số vị trí `downloadAndPlay → enqueueTelegramPlayback → enqueueItems → PendingPlayback → startPlayback` |
| `app/build.gradle` | vc115 → **vc116**, versionName `0.6.90.1` → **`0.6.91`** (hậu tố `-beta` có sẵn) |
| test: `MeloResumePolicyTest.kt` (mới), `MeloBottomBarPolicyTest.kt` | mục 6 |

Không phải làn nhanh (> 2 file production, đụng vị trí bắt đầu Media3 và luồng phát).

## 5. Trình tự cho Sonnet — **GỘP 1 phiên** (chia giai đoạn commit)

- **S0** Kiểm Git như mục 1 (nhánh `dot/E100-THANH-DAY-CHUA-PHAT`, HEAD = commit kế hoạch, cây sạch, không agent/Gradle khác). Ghi `git log -1` vào báo cáo.
- **S1** `MeloResumePolicy.kt` + phần thêm của `MeloBottomBarPolicy.kt` + 2 file test. Chạy test hai biến thể. *Commit 1* `E100 S1: resume record and idle bottom-card rules`.
- **S2** `MeloScreenHost.kt` + `MeloBottomBar.kt` + 2 dòng `refreshIdle()`. Build Debug. *Commit 2* `E100 S2: bottom bar shows resume or play-all card when idle`.
- **S3** `TelegramPreviewActivity.kt`: (a) tham số vị trí xuống `startPlayback` (mặc định 0); (b) ghi bản ghi trong listener + `onPause`; (c) `clearResume()` ở 2 đường đăng xuất; (d) `startResumeFlow` trong `loadDatabaseIndex`; (e) 3 hàm host. Build Debug + test. *Commit 3* `E100 S3: remember last Telegram song per account and resume it`.
- **S4** Bump vc116 / 0.6.91. Chạy đủ lệnh mục 6 (log ra file, chỉ đọc dòng tổng kết + XML). *Commit 4* `E100 S4: bump shareable beta to vc116 (0.6.91-beta)`.
- **S5** Git bundle (SHA-256) → clone cô lập trên Tiny → build APK có Telegram config (chỉ ghi true/false) → verify package/vc/cert (aapt2/apksigner) → mang APK về Ni, kiểm SHA-256.
- **S6** QA Shanling (mục 7, bắt buộc) + Sony (mục 7, nếu Tiny ADB sẵn sàng). Màn hình tắt trong `finally`.
- **S7** `00_tong_hop.png`, báo cáo `docs/plans/E100-THANH-DAY-CHUA-PHAT-bao-cao.md` (≤ ~250 dòng, có mục "APK QA cần Opus nghiệm thu bàn giao"). *Commit 5* báo cáo. DỪNG.

## 6. Test và lệnh

Unit test JVM (không Robolectric; View kiểm bằng QA thiết bị):
- `MeloResumePolicyTest`: khóa prefs chứa đúng accountId và hai account khác nhau cho khóa khác nhau; `record` null khi accountId ≤ 0 / songKey null hoặc 0; `songKeyOf("telegram:123")=123`, `"telegram:abc"`, `"picked:audio"`, id nhạc trên máy, null → null; `shouldWrite` false khi `!authReady`, account 0, mediaId không phải telegram; `startPositionMs`: `fileComplete=false` → 0; 4 999 → 0; 5 000 → 5 000; bài 200 s: 189 000 → 189 000, 190 001 → 0; duration 0 (không rõ) → giữ savedMs.
- `MeloBottomBarPolicyTest` (giữ test cũ): `idle(true, *) = RESUME`; `idle(false, true) = PLAY_ALL`; `idle(false, false) = PICK`; `idleActionable(PICK)=false`, còn lại true; `resumeLine(null/"  ")="Nghe tiếp"`, `resumeLine("Kênh A")="Nghe tiếp · Kênh A"`; mô tả TalkBack 3 trạng thái đúng chữ mục 3.2.
- Toàn bộ test cũ xanh; sau E100 phải ≥ 2042 + test mới, 0 lỗi, bỏ qua vẫn 21.

Lệnh Ni (CLAUDE.md §4):
```
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
git diff --check
```

## 7. QA thiết bị — Sonnet tự làm qua ADB (CLAUDE.md §3, §6, §7; `docs/workflow/SCREEN_LIFECYCLE_QA.md`)

**Gate trước khi cài (Shanling, Ni):** `adb devices -l` thấy `30313860180939` = device; getprop = M3 Ultra / Shanling; app đang cài `app.melovault.player` vc115; cert APK mới khớp `eecd3df7…c3fce8`; vc116 > vc115; Telegram config = true (APK Tiny); `dumpsys media_session` không PLAYING (đang phát → STOP hỏi). Chỉ `adb -s 30313860180939 install -r "<APK_VALIDATED>"`. Ghi lại bài đang nạp/tạm dừng của chủ dự án trước khi cài (tên chỉ trong báo cáo local).

**Được phép riêng cho E100:** `adb -s SERIAL shell am force-stop app.melovault.player` để giả lập "mở app lạnh" (không xóa dữ liệu/cache/TDLib), chỉ khi Media3 không PLAYING. Ghi rõ trong báo cáo; hàng đợi RAM cũ mất là đúng dự kiến (chính tính năng E100 nhớ lại bài).

| # | Thiết bị | Màn | Hành động | Kỳ vọng (PASS) | Ảnh |
|---|---|---|---|---|---|
| 1 | Shanling | ON | Sau `install -r` (tiến trình cũ đã dừng), mở app → Gọn, tab mặc định, chờ 5 s | Thẻ đáy **không trống**: "Chưa phát bài nào" / "Bấm để phát danh sách đang xem" (hoặc "Chọn một bài trong danh sách" nếu đang ở màn nguồn); ▶ sáng khi bấm được; Media3 không PLAYING, không tự phát; `uiautomator dump`: thanh đáy vẫn 204 px (102 dp), **8 dòng** | 01 |
| 2 | Shanling | ON | Chuyển tab **Telegram** (màn nguồn), chụp; quay lại tab **Thư viện**, chờ 3 s | Màn nguồn: "Chọn một bài trong danh sách", bấm thẻ không làm gì; Thư viện: lại "Bấm để phát danh sách đang xem" | 02, 03 |
| 3 | Shanling | ON | Bấm thẻ gợi ý | Phát bài **đầu** danh sách đang xem (Media3 state 3 rồi PLAYING; tên trên thẻ = dòng 1 danh sách); thẻ trở lại kiểu "Đang phát" E98/E99, bấm thẻ mở Đang phát | 04 |
| 4 | Shanling | ON | Mở danh sách bài **đã tải** (✓), bấm 1 bài, để phát ≥ 30 s, bấm ⏯ tạm dừng; ghi `position` trong `dumpsys media_session` | Media3 PAUSED, position ≈ 30 s | 05 |
| 5 | Shanling | ON | `am force-stop` → mở lại app, chờ 10 s **không chạm** | Thẻ: tên bài bước 4, dòng 2 "Nghe tiếp · <kênh>"; ▶ sáng; **Media3 không PLAYING** sau 10 s (không tự phát) | 06 |
| 6 | Shanling | ON | Bấm ▶ trên thẻ (không bấm thẻ) | Phát đúng bài bước 4 từ **≈ vị trí đã dừng** (`dumpsys media_session` position ≥ 25 s trong 5 s đầu); Bài kế đi theo danh sách đang xem nếu bài nằm trong đó; logcat không crash/ANR | 07 |

Nếu còn thời gian (không chặn PASS): bấm 1 bài **chưa tải** để phát ~20 s, dừng, force-stop, mở lại, bấm thẻ → phát **từ đầu** (position < 5 s). Ảnh `08`.

**Sony Xperia 5 II (Tiny, `QV725WEQ54`) — hồi quy, làm nếu Tiny ADB sẵn sàng (không chặn PASS, thiếu thì ghi CHƯA KIỂM):** cài vc116 (cùng gate) → Điện thoại dọc Teal: lặp bước 1 (gợi ý), bước 5–6 rút gọn (Nghe tiếp sau force-stop, bấm thẻ phát đúng bài). Ảnh `09`, `10`.

**Cleanup bắt buộc (`finally`):** bài đang phát để **tạm dừng** (không dừng/xóa hàng đợi); không đổi cài đặt UI; `input keyevent 223`; `dumpsys display` Display State=OFF; ghi `SHANLING_SCREEN_END` / `SONY_SCREEN_END`.
Ảnh: `docs/plans/artifacts/E100-THANH-DAY-CHUA-PHAT/NN_<thiết bị>_<mô tả>_<HHMMSS>.png`, ghép `00_tong_hop.png` (6–8 ô, cạnh dài ≤ 1600 px, số bước). Ảnh có tên bài/kênh **không commit**. **CHƯA XÁC NHẬN ÂM THANH THỰC.** **Không kiểm được trên máy:** xóa bản ghi khi đăng xuất/đổi tài khoản (cấm logout) — chỉ chứng minh bằng mã + unit test, ghi CHƯA KIỂM THIẾT BỊ.

### Bác sĩ tự kiểm tra (Shanling M3 Ultra, sau khi có APK candidate vc116 — hoặc duyệt ảnh từ xa)
1. Mở MeloVault (Gọn) lần đầu sau khi cài: thẻ dưới đáy ghi **"Chưa phát bài nào · Bấm để phát danh sách đang xem"**, không còn ô trống; vẫn thấy 8 bài.
2. Bấm vào thẻ đó: nhạc phát bài đầu tiên của danh sách đang xem, thẻ đổi thành bài đang phát như trước.
3. Bấm một bài đã tải (✓), nghe khoảng nửa phút, bấm ⏯ tạm dừng.
4. Vuốt tắt hẳn app khỏi danh sách ứng dụng gần đây, mở lại: thẻ hiện **tên bài vừa nghe** với chữ **"Nghe tiếp"**; nhạc **không tự phát**.
5. Bấm ▶ hoặc bấm thẻ: bài đó phát tiếp **từ chỗ đã dừng** (bài chưa tải thì phát lại từ đầu).

## 8. PASS / FAIL

**PASS (tất cả):**
- Test Debug + Release 0 lỗi, tổng ≥ 2042 + test mới, bỏ qua 21; build Debug/Release OK; `git diff --check` sạch; chỉ đổi đúng file mục 4.
- Shanling bước 1–6 PASS: thẻ không trống ở cả 3 trạng thái; không tự phát sau mở lạnh (Media3 không PLAYING 10 s); Nghe tiếp đúng bài, bài đã tải tiếp từ vị trí (≥ 25 s khi dừng ở ~30 s); bấm gợi ý = Phát hết bài đầu; Gọn vẫn 8 dòng, thanh đáy 102 dp; không crash/ANR.
- Khi đang có bài: thẻ mở Đang phát, ▶ Phát/Dừng như vc115.
- Prefs `melovault_resume` chỉ chứa số; mã xóa ở cả 2 đường đăng xuất; unit test khóa theo account.
- Chủ dự án xem ảnh/cài vc116 và nói **ĐẠT**.

**FAIL (bất kỳ):** thẻ còn trống khi chưa có bài; app tự phát khi mở; Nghe tiếp phát sai bài hoặc bài của tài khoản khác; bài đã tải không tiếp vị trí / bài chưa tải bị tua giữa chừng gây đứng; Gọn < 8 dòng hoặc thanh đáy đổi cỡ; crash/ANR; Car Mode/Hai khung đổi hành vi; đụng service/DB/schema/loader ảnh; sửa file ngoài mục 4 (trừ test/báo cáo).

## 9. STOP — Sonnet dừng và báo, không tự xoay xở

- Cây bẩn không rõ nguồn, HEAD/nhánh lệch, agent khác đang sửa/build.
- Cần sửa `ShareablePlaybackService`, DB/schema, TDLib handler, `MeloRowArtLoader` hoặc khôi phục hàng đợi để làm được ⇒ STOP hỏi.
- `setMediaItems` với vị trí gây lỗi/ANR trên Shanling ⇒ STOP, báo log (không tự chuyển sang seek sau `prepare`).
- ADB không thấy Shanling / unauthorized / getprop sai ⇒ BLOCKED phần QA (vẫn xong code + test; không dùng Sony thay).
- Signature mismatch, downgrade, sai package, APK thiếu Telegram config, `install -r` lỗi ⇒ STOP; cấm `uninstall`, `pm clear`, `-d`, xóa dữ liệu/cache/TDLib, logout, đổi tài khoản.
- Shanling đang PLAYING do người dùng ⇒ hỏi trước khi cài/force-stop.
- Mở app không ra Gọn hoặc chưa đăng nhập ⇒ không tự đăng nhập/đổi chế độ; BLOCKED bước đó.
- Không có đường Git bundle tới Tiny ⇒ STOP hỏi.

## 10. Rủi ro và rollback

| Rủi ro | Giảm thiểu |
|---|---|
| Listener ghi lại bản ghi ngay sau khi đăng xuất (controller dừng) | `shouldWrite` yêu cầu `auth.isReady()` + account > 0; `clearResume()` gọi sau `clearPlaybackSession()` |
| Tua vào phần chưa tải của file đang tải dần → đứng lâu | Vị trí chỉ dùng khi file đã tải đủ và thuộc vùng tài khoản (nhánh 7096) |
| Bài cuối đã bị xóa/ẩn | `getSongByKey` null/`isDeleted` → bỏ bản ghi, hiện gợi ý Phát hết; `getMessage` lỗi → Toast có sẵn |
| Activity bị hủy khi đang phát nền (bài kế từ màn khóa không được ghi) | Giới hạn đã nêu; khi mở lại mà service còn bài thì thẻ là "Đang phát" bình thường |
| `update(null)` gọi thường xuyên làm vẽ lại | Cache `idleShown` + key; `resumeCard/canPlayAll` chỉ đọc biến RAM |
| Ảnh bìa thẻ Nghe tiếp là ô chữ cái khi mở lạnh | Chỉ đọc cache sẵn có (không gọi tải ảnh mới) — chấp nhận, nêu cho chủ dự án |
| TalkBack đọc sai trạng thái | `idleDescription` + unit test |

**Rollback:** mã: revert Commit 3 (Activity) và Commit 2 (view) — Commit 1 chỉ thêm hàm thuần; vc116 giữ để không downgrade. Dữ liệu: prefs `melovault_resume` là file riêng chỉ chứa số, bỏ qua được; không di trú dữ liệu.

## 11. Chưa kiểm chứng / cần xác nhận

- [CẦN XÁC NHẬN] versionName `0.6.91` cho vc116 (quy ước nhiệm vụ mới tăng số thứ ba); Opus nghiệm thu xác nhận.
- Xóa khi đăng xuất/đổi tài khoản: chỉ mã + unit test (QA cấm logout).
- Ảnh bìa thẻ Nghe tiếp khi mở lạnh có thể là ô chữ cái (không thêm tải ảnh mới trong E100).
- Nút Play của tai nghe/BT khi app chưa có bài (Media3 resumption) không thuộc E100.
- Âm thanh thật chưa xác nhận.

---

### Prompt Sonnet (GỘP) — điền từ `docs/workflow/PROMPT_SONNET.md` (nhánh `dot/workflow-opus-nghiem-thu`)

Như mẫu, chỉ thay: TASK_ID `E100-THANH-DAY-CHUA-PHAT`; Nhánh `dot/E100-THANH-DAY-CHUA-PHAT`; Baseline `c606424`, commit kế hoạch `<PLAN_SHA>`; PHIÊN: **GỘP** (S0–S7). Lưu ý riêng:
1. Đọc quy trình mới bằng `git show dot/workflow-opus-nghiem-thu:docs/workflow/PROMPT_SONNET.md` nếu nhánh chưa có; "ChatGPT" = Opus nghiệm thu.
2. Không đụng `ShareablePlaybackService`, DB/schema, `MeloRowArtLoader`, Car Mode, `MeloTabletScreen`.
3. Prefs `melovault_resume` chỉ lưu số; xóa ở cả `logoutUser()` và nhánh TDLib Closing/Closed.
4. Vị trí nghe tiếp chỉ khi file đã tải đủ; progressive luôn từ đầu.
5. QA E100 được phép `am force-stop app.melovault.player` (không PLAYING) để mở lạnh; cấm logout/pm clear/uninstall.
6. Gọn phải giữ 8 dòng, thanh đáy 102 dp (uiautomator).
