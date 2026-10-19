# Kế hoạch — E94-LOCAL-MUSIC (nguồn "Nhạc trên máy")

**Vai trò:** Claude Opus lập kế hoạch, 19/10/2026. **TASK_ID:** `E94-LOCAL-MUSIC`. **Nhánh:** `dot/E94-LOCAL-MUSIC`, tạo từ nhánh tích hợp `feature/e93-03d-y700-offline-cockpit`.
**Baseline:** mốc nghiệm thu gần nhất `ba742063f563066b29d13b1516af10b54d538c4e` (`ba74206 docs: owner PASS offline one-tap QA…`). Trong lúc lập kế hoạch ChatGPT thêm commit chỉ-tài-liệu `0cc29e320990863d98c59e184b835e108491e5cf` (`docs: deliver each verified test APK via private Drive link`: CLAUDE.md mục 9 + docs/workflow). `git diff ba74206 0cc29e3 -- app/` rỗng ⇒ **mã nguồn baseline = `ba74206`**. Nhánh dot tạo từ HEAD nhánh tích hợp lúc commit kế hoạch (dự kiến `0cc29e3`); cây sạch, `git diff --check` sạch, không Gradle nào chạy. Commit kế hoạch này là HEAD Sonnet phải thấy khi bắt đầu.
**Sáu quyết định chủ dự án đã duyệt (19/10/2026), không hỏi lại:** (1) giai đoạn 1 chỉ đọc + danh sách + tìm/sắp xếp + phát/tạm dừng/chuyển bài/tua/MediaSession; (2) ưu tiên MediaStore, KHÔNG đổi schema SQLite Telegram, KHÔNG đổi `songKey`, định danh Local độc lập; (3) vẫn bắt buộc đăng nhập Telegram trước; (4) Gọn có "Nhạc trên máy" trong ☰, Điện thoại/Tablet dùng thẻ nguồn; (5) Sony được đẩy tối đa 3 tệp thử vào `Music/MeloTest`, chỉ xóa đúng các tệp đó; (6) hai thẻ nguồn trên màn hẹp chia độ rộng hoặc cuộn ngang, không tràn chữ, không che thanh đáy.

---

## 1. Mục tiêu và phạm vi

**Mục tiêu:** MeloVault Shareable đọc nhạc trong bộ nhớ máy qua MediaStore; thẻ "Nhạc trên máy" cạnh thẻ Telegram khi đã cấp quyền và có bài; xem danh sách, tìm không dấu, sắp xếp Mới nhất / Tên A–Z; phát bằng Media3 hiện có (Phát/Tạm dừng/⏮/⏭/tua, màn khóa, phím vật lý, tai nghe); chuyển Telegram ↔ Local không làm hỏng luồng phát Telegram.

**Làm (giai đoạn 1):**
- Xin quyền đọc âm thanh **chỉ khi người dùng bấm** "Nhạc trên máy" (Android 13+: `READ_MEDIA_AUDIO`; ≤ 12: `READ_EXTERNAL_STORAGE`). Không `MANAGE_EXTERNAL_STORAGE`. Manifest KHÔNG đổi (hai quyền đã có ở `app/src/main/AndroidManifest.xml:11-14`).
- Lối vào: Gọn → ☰ "♪ Nhạc trên máy"; Điện thoại/Tablet/Hai khung → thẻ "Nhạc trên máy" (khi có quyền và > 0 bài) + mục "♪ Nhạc trên máy" trong menu ⋮ (cho lần đầu, khi chưa có thẻ).
- Danh sách local trong khung Thư viện sẵn có (Gọn, Điện thoại, Tablet, Hai khung): tên, nghệ sĩ (hoặc tên thư mục), thời lượng, trạng thái "Trên máy · FLAC".
- Phát local: hàng đợi chỉ gồm bài local (tối đa 500 bài quanh bài bấm); ⏮/⏭/tua/MediaSession qua ExoPlayer có sẵn.
- Quay lại Telegram: thẻ Telegram, tab Thư viện/Telegram, ☰ Thư viện/Đã tải/Playlist, BACK.

**Không làm (BACKLOG):** Playlist/Yêu thích cho bài local, hàng đợi trộn nguồn, duyệt thư mục/ổ, tự làm mới khi thêm/xóa tệp (ContentObserver), ảnh bìa nhúng, sóng âm và độ sâu bit cho bài local (⇒ **không có viên Hi-Res cho bài local**), Car Mode, Drive/OneDrive, EQ, theme, đổi schema/DB/`songKey`/TDLib/đăng nhập/đồng bộ, sửa `ShareablePlaybackService`, sửa `LocalMediaRepository` (source set `main`, dùng chung flavor `personal`), bump versionCode, upload Drive.

---

## 2. Kiến trúc hiện tại (đã đối chiếu code tại `ba74206`)

`TPA` = `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/TelegramPreviewActivity.kt`; các file `Melo*` cùng thư mục.

- **MediaStore có sẵn:** `app/src/main/java/com/bsdung/musicassistantcar/data/LocalMediaRepository.kt` — `LocalMediaTrack(id, volumeName, title, artist, album, durationMs, sizeBytes, mimeType, relativePath, contentUri)`; `hasPermission()` (176, đúng nhánh API 33+/≤32); `getAllTracks()` (218) đọc **toàn bộ** `EXTERNAL_CONTENT_URI` với `IS_MUSIC != 0`, giữ khóa đối tượng suốt truy vấn + dựng cây thư mục (228), **cache cả kết quả rỗng/dở dang** (gọi lúc chưa có quyền ⇒ rỗng tới khi `invalidateCache()` 171; lỗi giữa cursor ⇒ danh sách dở, 611); fallback "Nhạc thiết bị #id"/"Nghệ sĩ không rõ" (599-600); không có DATE_ADDED. 12 test JVM, tiêm được `permissionChecker`/`tracksProvider`. Shareable chưa gọi.
- **Thẻ nguồn:** `MeloSourceCardPolicy.kt` — `Kind { TELEGRAM, LOCAL, DRIVE }`, `titleOf(LOCAL)="Nhạc trên máy"`, `badgeOf(LOCAL)="♪"`, `cards()` bỏ nguồn `total` null/≤0, `rowVisible()`, `cardWidthPx()` (không vừa ⇒ thẻ cuối lộ đúng một nửa, cuộn ngang), `selectedKind(selectedChatId, showingSources)` chỉ biết TELEGRAM. `sourceSummaries()` chỉ trả TELEGRAM (TPA:4522-4523). `MeloLibraryPane.buildSourceCards` (334-350) trong `HorizontalScrollView`, bấm thẻ chỉ xử lý TELEGRAM (343). `MeloComponents.sourceCard` (365-399): dòng chi tiết `maxLines = 1` + "…".
- **Host:** `MeloScreenHost.kt`, một chỗ cài `meloHost` (TPA:4270-4621). `snapshot()` (4271-4323) suy mode từ cờ `playlistMode/filterOnlyDownloaded/browseSources/allChatsMode/selectedChatId`; `playingKey` (4278) và `nowPlaying().songKey` (4426/4433) đều = `currentTelegramMessageId()` (3456-3460, null nếu mediaId không bắt đầu `telegram:`). Các màn so hai số này trong `rowsStale` (`MeloCompactScreen:340`, `MeloCarScreen:280`, `MeloLibraryPane:257`) — mỗi giây `miniPlayerProgressTick` (740-749) gọi `notifyMeloPlayback`. Vẽ lại qua `scheduleMeloLibraryRefresh()` (4208-4220).
- **BACK:** `MeloCompactScreen.backCallback` (62-71), `MeloPhoneScreen` (46-51), `MeloTabletScreen` (93-98) đều gọi `host.showSources()` khi `sourceTitle != null`; điều kiện bật ở `MeloCompactScreen:159`, `MeloPhoneScreen:104`, `MeloTabletScreen:432`. Activity có callback riêng (TPA:471) cho lớp cũ.
- **Gọn:** ☰ `showMainMenu` (`MeloCompactScreen:282-302`, id 1–6); dòng "Đã tải: N nhóm" lấy `sourceSummaries().firstOrNull()` (152-154); nhãn "Kênh: …" ở Đang phát (507). Gọn không có hàng thẻ nguồn.
- **Phát:** Activity giữ `MediaController` (571-597), tự dựng MediaItem (`mediaItem()` 599-605), `enqueueItems → startPlayback` (696-709) = `setMediaItems(...)` **thay toàn bộ danh sách**. Đường "Chọn tệp nhạc" (`openDocumentResult` 368-395, mediaId `picked:audio`) đã phát `content://` được. Service: ExoPlayer dùng `TelegramOwnerAudioDataSource.Factory` bọc `DefaultDataSource` ⇒ `content://` đi thẳng (`TelegramOwnerAudioDataSource.kt:35-39`); `nextTrack()`/`NextAwarePlayer`/tải nền/`STATE_ENDED` chỉ hành động khi mediaId là `telegram:<Long>` (`ShareablePlaybackService.kt:236, 320-330`) ⇒ bài local do ExoPlayer tự ⏭/⏮. Đăng xuất: service tự dừng + xóa danh sách (263-275).
- **Chỗ chỉ biết Telegram:** `playAdjacent` (4707-4726, return sớm nếu không có id Telegram); `addToUpcomingQueue` (4763, sẽ chèn bài Telegram vào danh sách local); `downloadAndPlay` (6074-6197): callback `resolveTelegramMessage` **không có số thế hệ** ⇒ bấm bài Telegram rồi bấm bài local trước khi resolve xong thì callback vẫn `enqueue*Playback` và **thay danh sách local**; `playIfDownloaded` (6199-6223) tương tự khi `downloadWaiting` còn; `prefetchNextCachedNeighbor` (5981, an toàn khi `currentSongIndex < 0`); `qualityChips` (4583, rỗng); `toggleCurrentFavorite` (3990, im lặng); `showUpcomingQueue` (3284, hiện "Danh sách nguồn đang được khôi phục… Telegram"); `syncSubtitle()` (4517) dòng đồng bộ Telegram; phụ đề "Telegram · Đang phát" (541-542); `updateMiniPlayerCover` (912)/`updateCockpitCover` (1572) parse `removePrefix("telegram:").toLongOrNull()` không kiểm tiền tố ⇒ mediaId local phải có **tiền tố chữ**.
- **Chip:** `MeloLibraryPane.renderChips` (289-310): chip mode (Tất cả/Đã tải/Playlist) + chip thể loại; chọn theo `snap.mode`.
- **Ô tìm:** `MeloLibraryPane` (80/85) và `MeloCompactScreen` (60) giữ `lastQuery`/chữ cũ, debounce 250 ms trước `host.setQuery`.
- **Quyền khi chạy:** mẫu `notificationPermissionResult` + `maybeAskNotificationPermission` (TPA:325-345).
- **Vòng đời:** `dbExecutor`/`offlineExecutor` + `uiQueryGeneration` (243-279); `onDestroy` (6269-6304); reset tài khoản chỉ ở `logoutUser` (6225-6267) và nhánh TDLib đóng phiên (5329-5369); lớp Melo: `showNewLayer` khi `layer == null` (4079), `showCarLayer` (4122). Không lưu bài/hàng đợi đang phát ra đĩa.
- **Định danh Telegram:** `TelegramSongKey.forMessage` luôn ≥ 0 (id Saved Messages hoặc `(1 shl 62) | hash`). `TelegramPlaylistPolicy.toggle` có `require(targetId > 0)` (`TelegramSavedPlaylistStore.kt:21`).
- **Thiết bị (Opus kiểm chỉ-đọc qua ADB 19/10/2026, không bật màn, không cài, không chạm):**
  - Shanling `30313860180939` (M3 Ultra, Android 10): MeloVault vc109 / `0.6.88-beta`; `READ_EXTERNAL_STORAGE: granted=false` (restricted, cờ `RESTRICTION_INSTALLER_EXEMPT` ⇒ vẫn cấp qua hộp thoại) ⇒ **thử được Từ chối + Cho phép**; `content query … --projection _id --where "is_music!=0"` chạy từ shell, **3.557 bài**.
  - Sony `QV725WEQ54` (XQ-AS52, Android 12): vc109 / `0.6.88-beta`; `READ_EXTERNAL_STORAGE: granted=false`; MediaStore **3 bài** `is_music` sẵn có; `/sdcard/Music/MeloTest` **chưa tồn tại**.
- **Test:** `unitTests.returnDefaultValues = true`; mốc mỗi biến thể 1.263 tests, 1.242 passed, 21 skipped, 0 failed.

---

## 3. Phương án đề xuất và lý do

### 3.1 Định danh bài local
- `localKey = Long.MIN_VALUE or (mediaStoreId and ((1L shl 62) - 1))`, yêu cầu `mediaStoreId > 0` ⇒ mọi `localKey` ≤ −2^62: **không bao giờ** trùng `songKey` Telegram (≥ 0), không phải −1/0; mọi kiểm `key > 0` sẵn có tự từ chối. Đảo ngược được (`idOfKey`).
- mediaId Media3 = `"local:<mediaStoreId>"` (không dùng `device:` của flavor personal). URI = `ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)` (giống `LocalMediaRepository`).
- Phát theo **MediaStore `_ID`**, không dò tên ⇒ hai tệp trùng tên vẫn là hai bài. `_ID` là AUTOINCREMENT; MediaStore dựng lại thì id đổi — chấp nhận vì giai đoạn 1 **không lưu** khóa local ra đĩa.
- **Không bao giờ dựng `Song` (kiểu Telegram) cho bài local** (tránh `require(targetId > 0)`, `downloadAndPlay`, `configureTelegramQueue`).
- Không dùng bảng `local_songs`/nhét vào `songs` (cấm bởi quyết định 2; MediaStore đã là chỉ mục).

### 3.2 Dữ liệu: tái dùng `LocalMediaRepository` (chỉ đọc) + lớp mỏng
- **Tái dùng** `LocalMediaRepository(applicationContext)` không sửa: `hasPermission()`, `getAllTracks()`, `invalidateCache()` — mã MediaStore đã có test, đúng nhánh quyền, không đụng flavor personal. **Chỉ gọi `getAllTracks()`/`invalidateCache()` trên `localExecutor`** (hai hàm dùng chung khóa; gọi từ main thread khi đang nạp sẽ treo UI).
- **Thêm** `MeloLocalLibrary` (Shareable): executor riêng một luồng; chuyển `LocalMediaTrack` → `LocalSong` thuần (không giữ `Uri`), tính sẵn `searchText` và `alphaKey`; lọc/sắp xếp/phân trang trong bộ nhớ; đếm nhanh cho thẻ. Thư viện thật: Shanling 3.557 bài ⇒ nạp hết vào bộ nhớ hợp lý.
- **Đếm cho thẻ** (chỉ khi đã có quyền, không hỏi): trên executor `query(EXTERNAL_CONTENT_URI, arrayOf(_ID), "is_music != 0", null, null)?.use { it.count }` — cùng URI + điều kiện với repository nên khớp số; sau khi nạp đủ, số thẻ = kích thước danh sách thật.
- **Phân trang:** danh sách đã lọc trong bộ nhớ; màn nhận 200 dòng, `loadMore` +200. Không dùng `LIMIT/OFFSET` MediaStore (tìm không dấu + A–Z cần cả danh sách; `LIMIT` trong sortOrder bị chặn ở Android 11+ với target ≥ 30; Bundle `QUERY_ARG_LIMIT` không chắc trên Android 10).
- **Sắp xếp:** `NEWEST` = `mediaStoreId` giảm dần (proxy "mới thêm", repository không có DATE_ADDED); `NAME` = `alphaKey = MeloAlphabetPolicy.keyOf(title, artist, null)` tăng, hòa ⇒ id tăng. Cùng `alphaKey` cho `Row.sortKey` và `firstIndexAtOrAfter`. Sort dùng `activeSort()` (Gọn luôn NEWEST), lưu chung `library_sort`.
- **Tìm:** `TelegramSearchPolicy.queryTerms(query)` (không dấu, AND, ≤ 8 từ) so với `searchText = MeloComponentPolicy.searchKey("title artist album thư-mục")`.
- **Nghệ sĩ hiển thị:** `"<unknown>"`, `"Nghệ sĩ không rõ"`, rỗng/null ⇒ tên thư mục cuối của `relativePath`; không có ⇒ `null` (không hiện chữ "chưa biết").
- **Làm mới:** mở "Nhạc trên máy" khi danh sách chưa có hoặc cũ > 5 phút ⇒ `invalidateCache()` + nạp (trên executor); `host.rescan()` ở local = nạp lại ngay (cũng chữa danh sách dở do lỗi cursor); `onResume` đếm lại trên executor. Không ContentObserver.
- **Hủy:** `generation` (AtomicLong) tăng khi đóng local/đổi truy vấn-sắp xếp/đăng xuất/`onDestroy`; kết quả chỉ áp dụng nếu generation khớp và Activity chưa hủy; `shutdownNow()` trong `onDestroy`.
- **Log:** tag `MeloVaultLocal` mức **W**, chỉ đếm/ms/generation/phân bố MIME — **không** tên bài, đường dẫn, URI.

### 3.3 Phát `content://` an toàn với Media3
- **Nhận biết bài đang phát theo mediaId, không theo `localMode`** (người dùng có thể thoát danh sách local khi nhạc local vẫn chạy; Activity tạo lại thì mất danh sách local): `currentLocalId() = MeloLocalSourcePolicy.idOfMediaId(player?.currentMediaItem?.mediaId)`; `currentSongKey() = currentTelegramMessageId() ?: currentLocalId()?.let(::keyOf)`. Dùng `currentSongKey()` cho **cả** `Snapshot.playingKey` (mọi chế độ) **và** `NowPlaying.songKey` ⇒ `rowsStale` không bật mỗi giây. Giữ `currentTelegramMessageId()` nguyên cho mã chỉ-Telegram.
- MediaItem local mang đủ thông tin trong `MediaMetadata`: title, artist hiển thị, và `extras` Bundle `local_mime`, `local_size`, `local_duration_ms` ⇒ hàng đợi/viên chất lượng đọc thẳng từ controller, không cần danh sách local.
- `playLocal(index)`:
  1. `localPlayEpoch++` (xem bước 3). Dọn trạng thái Telegram như đường "Chọn tệp nhạc": nếu `downloadWaiting != null` ⇒ `TelegramProgressiveCache.stop(it)` (chỉ tắt bộ đọc trong bộ nhớ, không gọi TDLib, không xóa cache); `downloadWaiting = null`, `currentDownloadingSong = null`, `currentSongIndex = -1`; `playFeedback?.let { setPlayFeedback(null, it.songKey) }`; `ShareablePlaybackService.clearTelegramQueue()`; `queueSongOrder = emptyList()`. **Không** gửi lệnh TDLib nào (không `cancelDownloadFile`): tải Telegram dở cứ chạy tiếp vào cache TDLib như đường "Chọn tệp nhạc" hiện nay.
  2. `queueWindow(size, index)` tối đa 500 bài (100 trước) từ danh sách local đang lọc + sắp xếp ⇒ MediaItem `local:<id>`.
  3. `enqueueItems(items, index - start)` có sẵn ⇒ `setMediaItems` thay toàn bộ ⇒ **không trộn** với Telegram.
- **Chặn cuộc đua Telegram chậm:** trong `downloadAndPlay`, chụp `val epoch = localPlayEpoch` ở đầu; dòng đầu callback `resolveTelegramMessage` thêm `if (epoch != localPlayEpoch) return@resolveTelegramMessage` ⇒ chỉ bỏ kết quả Telegram khi **người dùng đã bấm bài local sau đó**; hành vi Telegram ↔ Telegram không đổi. (Đây là sửa đổi duy nhất trong đường phát Telegram: 2 dòng.)
- Chiều ngược lại: bấm bài Telegram đi đường cũ ⇒ `setMediaItems` thay danh sách local.
- `playAdjacent` (4707): **dòng đầu tiên** (trước `auth`/`currentTelegramMessageId`) nhánh `currentLocalId() != null` ⇒ `seekToNextMediaItem()`/`seekToPreviousMediaItem()` + `play()`. Hết cửa sổ 500 bài ⇒ dừng (STATE_ENDED ⇒ `playAdjacent(1)` không còn bài kế; nạp cửa sổ sau để BACKLOG). Màn khóa/tai nghe/phím vật lý/`keyevent 87/88` đi MediaSession → `NextAwarePlayer` → ExoPlayer (service không đổi).
- "⏭ Phát tiếp theo" (`addToUpcomingQueue` 4763) khi bài hiện tại là local ⇒ Toast "Đang phát Nhạc trên máy. Bấm bài Telegram để chuyển nguồn.", không chèn. Menu bài local chỉ có "▶ Phát bài này" và "ℹ Thông tin" (tên, nghệ sĩ, album, định dạng, dung lượng, thời lượng, thư mục tương đối — không URI).
- "☆ Lưu" khi bài local ⇒ Toast "Nhạc trên máy chưa hỗ trợ Lưu." (không ghi gì).
- Hàng đợi (`host.queue()`, `playQueueAt`, `openQueueSongMenu`, hộp `showUpcomingQueue`): nhánh **theo bài hiện tại** đọc `controller.getMediaItemAt(i)` (0 until `mediaItemCount`), bấm = `seekToDefaultPosition(i)` + `play()`; `showUpcomingQueue` local = AlertDialog danh sách tên (bài hiện tại có "▶ ").
- Lỗi phát (tệp xóa, thẻ SD rút, định dạng không hỗ trợ): listener sẵn có báo "Không phát được tệp âm thanh (Media3 mã …)"; không tự nhảy bài.

### 3.4 Quyền Android 10 / 12 / 13+
- `permissionFor(sdk)`: ≥ 33 ⇒ `READ_MEDIA_AUDIO`; còn lại ⇒ `READ_EXTERNAL_STORAGE`. Shanling (10) và Sony (12) đi nhánh `READ_EXTERNAL_STORAGE`; nhánh 33+ chỉ chủ dự án thử trên Y700/S25 **[CẦN XÁC NHẬN]**.
- `permissionStep(granted, askedBefore, showRationale)`: có quyền ⇒ `OPEN`; chưa hỏi lần nào hoặc rationale ⇒ `EXPLAIN_THEN_ASK` (AlertDialog "MeloVault cần quyền đọc tệp âm thanh trên máy để liệt kê và phát nhạc của bạn. Ứng dụng chỉ đọc, không sửa, xóa hay gửi tệp đi đâu." — "Tiếp tục" / "Để sau"); đã hỏi + không rationale ⇒ `OPEN_SETTINGS` (dialog "Quyền đọc nhạc đang tắt" — "Mở cài đặt" → `ACTION_APPLICATION_DETAILS_SETTINGS` của chính app / "Để sau").
- Cờ "đã hỏi" ở prefs riêng `melovault_local_music`, khóa `read_audio_asked` (thiết bị, không xóa khi đăng xuất). Không hỏi lúc mở app, không hỏi lặp tự động.
- Kết quả: cho phép ⇒ nạp lại (executor) + mở danh sách; từ chối ⇒ Toast "Chưa có quyền đọc nhạc trên máy.", ở lại Telegram, không thẻ. Quay về từ Cài đặt ⇒ `onResume` đếm lại; thẻ hiện nếu đã bật quyền (không tự mở local).
- Chỉ khi `auth.isReady()` (quyết định 3).

### 3.5 Giao diện
- `MeloScreenHost`: thêm `openLocal()`, `closeLocal()`; `Snapshot.local = false`; `Row.local = false`, `Row.mimeType: String? = null`; `NowPlaying.local = false`; `QueueItem.local = false` (mặc định ⇒ mọi chỗ cũ không đổi).
- `snapshot()` khi `localMode`: kiểm `localMode` **trước** và không đọc `shownSongs`: `showingSources=false`, `mode=ALL`, `rows` = trang local (cache danh sách `Row` theo phiên bản danh sách + số dòng + sort, vì `snapshot()` chạy 2 lần mỗi lần vẽ), `sources=emptyList()`, `total` = số bài sau lọc, `sourceTitle="Nhạc trên máy"`, `playingKey=currentSongKey()`, `query` = truy vấn local riêng (không đụng `songFilter` để khỏi chạy truy vấn DB Telegram), `note` = "Đang đọc nhạc trên máy…" / "Không tìm thấy bài nhạc nào trên máy." / "Không có bài khớp.", `sort=activeSort()`, `letterFloor=null`, `selectedChatId=null`, `local=true`. Row local: `downloaded=true`, `downloadingPercent=null`, `art=null`, `sortKey=alphaKey` khi NAME. `syncSubtitle()` ⇒ `null` khi `localMode`.
- `setMode`/`showSources`/`openSource` + mục ⋮ "Tin đã lưu" (id 1) và "Nhóm / kênh / bot" (id 5) ⇒ `exitLocalIfNeeded()` ở dòng đầu rồi chạy như cũ.
- **Thẻ:** `sourceSummaries()` = TELEGRAM (luôn **đứng đầu**) + `cardSource(granted, count)` (LOCAL, `downloaded=null`). `MeloSourceCardPolicy.selectedKind(selectedChatId, showingSources, localOpen: Boolean = false)` ⇒ LOCAL khi local; TELEGRAM khi không local và không mở kênh. Bấm thẻ LOCAL chưa chọn ⇒ `host.openLocal()`; TELEGRAM chưa chọn ⇒ `host.openSource(null)` (như cũ, tự thoát local).
- **Quyết định 6:** giữ `cardWidthPx` (vừa ⇒ chia đều; không vừa ⇒ thẻ 1 ≥ 200dp/240dp, thẻ 2 lộ đúng nửa, cuộn ngang). `sourceCard`: dòng chi tiết `maxLines = 2` để "25.105 bài · đã tải 67" không bị "…" khi thẻ ~213dp (điện thoại 360dp); thẻ trong dải `LayoutParams(cardWidth, MATCH_PARENT)` để cao bằng nhau. Hàng thẻ vẫn thu gọn khi cuộn, không chạm thanh đáy.
- **Chip:** khi `snap.local` ẩn cả hàng chip (`renderChips`: `if (snap.showingSources || snap.local) GONE`) — tránh "Tất cả" sáng và số "Đã tải" Telegram trên danh sách local. Sort chip + thanh A–Z dùng như Telegram; `jumpToLetter` local nới trang chứa index rồi **trả index** (không null).
- **Ô tìm:** khi `snap.local` đổi giá trị, đặt lại chữ ô tìm và `lastQuery` theo `snap.query` (`MeloLibraryPane`, `MeloCompactScreen`); thêm `snap.local` vào `groupKey` (`MeloLibraryPane:185`) để về đầu danh sách.
- **Dòng bài:** icon "Đã tải" nhưng chữ **"Trên máy · FLAC"**: `presentStatus(..., local = true)`; định dạng `MeloComponentPolicy.formatOf(row.mimeType, row.filename)` (`MeloLibraryPane:375`, `MeloCompactScreen:195`); `MeloComponents` truyền cờ ở cả hai chỗ gọi `presentStatus` (164, 308); hàng đợi Studio Deck (`MeloStudioDeck:454`) truyền `QueueItem.local`.
- **BACK khi local ⇒ `host.closeLocal()`** (về đúng màn Telegram trước đó, không phải lưới kênh): nhánh `snap.local` đứng đầu `backCallback` của `MeloCompactScreen` (trước nhánh `sourceTitle != null`), `MeloPhoneScreen`, `MeloTabletScreen` (điều kiện bật đã đúng vì `sourceTitle != null`); callback Activity (471) thêm `if (localMode) { closeLocalMusic(); return }`.
- **Gọn:** ☰ thêm `menu.add(0, 7, 4, "♪ Nhạc trên máy")` **ngay sau** dòng `menu.add(0, 6, 4, "✓ Đã tải")` ⇒ hiện ngay dưới "✓ Đã tải" → `host.openLocal()`; tiêu đề tự thành "Nhạc trên máy · N bài" (`MeloCompactPolicy.headerTitle`); dòng "Đã tải: N nhóm" lấy nguồn `kind == TELEGRAM` thay `firstOrNull()`; nhãn Đang phát (507) khi `now.local` = "Nghệ sĩ: …" thay "Kênh: …".
- **Điện thoại/Tablet:** `showLibraryOverflow` (2923) thêm `menu.menu.add(0, 11, 11, "♪ Nhạc trên máy")` khi `meloLayer != null` ⇒ `openLocalMusic()`.
- **Car / mất lớp Melo:** `closeLocalMusic()` ở dòng đầu `showCarLayer` (4122, trước return sớm) và ở nhánh `layer == null` của `showNewLayer` (4079). **Không** gọi trong `detachMeloLayer()` (xoay màn đổi lớp phải giữ local).
- Phụ đề lớp cũ (541-542) khi bài local: "Nhạc trên máy · Đang phát". (Cockpit 1497, hộp cũ 3253, Car 298: giữ nguyên — ghi BACKLOG.)

### 3.6 Chất lượng âm thanh và định dạng
- Không DSP/EQ mới. Media3 1.11.1 + MediaCodec: MP3, FLAC, WAV PCM 16/24 bit, M4A/AAC, OGG/Opus phát được; ALAC/AIFF/APE/DSD/WMA **[CẦN XÁC NHẬN]** trên Shanling — không phát được thì Toast lỗi, không crash.
- Viên chất lượng bài local: định dạng = MIME MediaStore từ `extras` (decoder WAV báo `audio/raw` nên không dùng làm nhãn), kHz/kbps từ `Format` track đang phát, dung lượng = `local_size`; `bitDepth = null` ⇒ **không có viên Hi-Res cho bài local** (đúng quy tắc "chưa đọc được thông số thực"). `currentLocalFile()` giữ `null` cho bài local ⇒ không phân tích sóng âm/`probeBitDepth`.
- Log phân bố định dạng mỗi lần nạp: `MeloVaultLocal stage=formats audio/flac=… audio/mpeg=…`.

### 3.7 Khác với kế hoạch cũ `docs/plans/nhac-tren-may.md` (18/10)
Giữ: không đổi DB, MediaStore trực tiếp, quyền khi bấm, hàng đợi một nguồn, khóa local tách không gian. Khác: (a) khóa = cờ bit 63 trên `_ID` thay vì băm (đảo được, không va chạm vì `_ID` duy nhất trong external.db); (b) tái dùng `getAllTracks()` + lọc/phân trang trong bộ nhớ thay vì `limit/offset` MediaStore; (c) Gọn có mục ☰ (quyết định 4); (d) chặn cuộc đua `downloadAndPlay` và nhận biết bài local theo mediaId (phản biện code); (e) không bump versionCode, không upload; (f) QA từ xa Shanling + Sony, vòng đời màn hình, bàn giao APK theo CLAUDE.md mục 9.

---

## 4. Danh sách file cần sửa

**Mới (production):**
1. `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloLocalSourcePolicy.kt` — logic thuần, không import Android (~170 dòng):
   ```kotlin
   internal object MeloLocalSourcePolicy {
       const val MEDIA_ID_PREFIX = "local:"; const val SOURCE_TITLE = "Nhạc trên máy"
       const val PAGE_SIZE = 200; const val QUEUE_MAX = 500; const val QUEUE_BEFORE = 100
       const val PREFS_NAME = "melovault_local_music"; const val KEY_ASKED = "read_audio_asked"
       const val PERMISSION_AUDIO = "android.permission.READ_MEDIA_AUDIO"
       const val PERMISSION_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
       const val RELOAD_AFTER_MS = 5 * 60_000L
       const val EXTRA_MIME = "local_mime"; const val EXTRA_SIZE = "local_size"; const val EXTRA_DURATION = "local_duration_ms"
       data class LocalSong(val mediaStoreId: Long, val title: String, val artist: String?, val album: String?,
                            val folder: String, val durationMs: Long, val sizeBytes: Long, val mimeType: String?,
                            val searchText: String, val alphaKey: String) { val key: Long get() = keyOf(mediaStoreId) }
       fun keyOf(mediaStoreId: Long): Long            // require(id > 0)
       fun isLocalKey(key: Long): Boolean             // (key ushr 62) == 2L
       fun idOfKey(key: Long): Long?
       fun mediaIdOf(mediaStoreId: Long): String
       fun idOfMediaId(mediaId: String?): Long?       // chỉ "local:<số dương>"
       fun displayArtist(rawArtist: String?, relativePath: String): String?
       fun songOf(id: Long, rawTitle: String?, rawArtist: String?, rawAlbum: String?, relativePath: String,
                  durationMs: Long, sizeBytes: Long, mimeType: String?): LocalSong?   // id <= 0 ⇒ null
       fun sorted(songs: List<LocalSong>, sort: SongSort): List<LocalSong>
       fun filtered(songs: List<LocalSong>, query: String?): List<LocalSong>
       fun queueWindow(size: Int, index: Int, max: Int = QUEUE_MAX, before: Int = QUEUE_BEFORE): IntRange
       fun permissionFor(sdkInt: Int): String
       enum class PermissionStep { OPEN, EXPLAIN_THEN_ASK, OPEN_SETTINGS }
       fun permissionStep(granted: Boolean, askedBefore: Boolean, showRationale: Boolean): PermissionStep
       fun cardSource(granted: Boolean, count: Int?): MeloSourceCardPolicy.Source?   // LOCAL, downloaded = null
       fun note(loading: Boolean, total: Int, shown: Int, query: String?): String?
       fun canQueueNext(currentIsLocal: Boolean, songIsLocal: Boolean): Boolean
       fun formatCounts(mimes: List<String?>): String   // "audio/flac=3 audio/mpeg=2" cho log
   }
   ```
2. `…/telegram/MeloLocalLibrary.kt` — trạng thái local (~150 dòng). Hàm khởi tạo tiêm: `repository: LocalMediaRepository`, `countQuery: () -> Int?`, `executor: Executor`, `post: (Runnable) -> Unit`, `clock: () -> Long`. API: `count(): Int?`, `loading: Boolean`, `loadedAt`, `refreshCount(onDone)`, `load(force, onDone)`, `setQuery(q, onDone)`, `setSort(s, onDone)`, `query`, `version` (tăng khi danh sách hiển thị đổi), `shown(): List<LocalSong>`, `visible(): List<LocalSong>`, `loadMore(): Boolean`, `jumpTo(lowerBound): Int`, `byId(id): LocalSong?`, `clear()` (generation++ ngay; `invalidateCache()` chạy trên executor), `shutdown()`. `onDone` qua `post` và chỉ khi generation khớp. Không biết Activity/MediaController/TDLib.

**Sửa (production):**
3. `MeloScreenHost.kt` (+~14): `openLocal()`, `closeLocal()`, `Snapshot.local`, `Row.local`, `Row.mimeType`, `NowPlaying.local`, `QueueItem.local`.
4. `MeloSourceCardPolicy.kt` (+~3): `selectedKind(..., localOpen: Boolean = false)`.
5. `MeloComponentPolicy.kt` (+~5): `presentStatus(..., local: Boolean = false)` ⇒ DOWNLOADED + local ⇒ "Trên máy" (+ " · FLAC").
6. `MeloComponents.kt` (+~6): `SongRowModel.local = false` → `presentStatus` ở 164 và 308; `sourceCard` chi tiết `maxLines = 2`.
7. `MeloLibraryPane.kt` (+~16): `renderSourceCards` truyền `snap.local`; thẻ LOCAL ⇒ `host.openLocal()`; thẻ cao `MATCH_PARENT`; ẩn chip khi local; `groupKey` + đồng bộ ô tìm; dòng bài `row.local` + `formatOf(row.mimeType, …)`.
8. `MeloCompactScreen.kt` (+~14): ☰ id 7; BACK local; đồng bộ ô tìm; dòng bài như 7; `downloadedLine` theo kind; nhãn "Nghệ sĩ:" khi `now.local`.
9. `MeloPhoneScreen.kt`, `MeloTabletScreen.kt` (+~2 mỗi file): nhánh `snap.local ⇒ host.closeLocal()` đầu `backCallback`.
10. `MeloStudioDeck.kt` (+~1): dòng hàng đợi (454) truyền `QueueItem.local` vào trạng thái. **Không đổi gì khác.**
11. `TelegramPreviewActivity.kt` (+~250/−~5): bước S4–S5.

**Mới/sửa (test):** `app/src/test/java/com/bsdung/musicassistantcar/data/telegram/MeloLocalSourcePolicyTest.kt`, `…/MeloLocalLibraryTest.kt`; thêm ca vào `MeloSourceCardPolicyTest.kt` (mặc định `localOpen=false` giữ nguyên ca 83-86) và test `presentStatus` (thêm vào test có sẵn của `MeloComponentPolicy` hoặc vào `MeloLocalSourcePolicyTest`).

**Không được đổi:** `ShareablePlaybackService.kt`, `TelegramOwnerAudioDataSource.kt`, `TelegramProgressiveCache.kt`, mọi DAO/SQL/schema (`TelegramLibrary*`, `AndroidTelegramLibraryDatabase`), `TelegramSongKey.kt`, `TelegramSavedPlaylistStore.kt`, `TelegramSyncCoordinator.kt`, `ShareableTdlibOwner.kt`, `TelegramOfflineLibrary.kt`, `MeloCarScreen.kt`, `LocalMediaRepository.kt` và mọi `app/src/main/**`, `app/src/personal/**`, mọi `AndroidManifest.xml`, `app/build.gradle` (versionCode giữ 109), `CLAUDE.md`, `HANDOFF.md`, `docs/workflow/**`, file nghiệm thu/link-index/apk-test.

Ước lượng production ~650 dòng thêm (2 file mới ~320, Activity ~250, UI/policy ~80). Không phải làn nhanh.

---

## 5. Trình tự thực thi cho Sonnet

Commit chỉ ở S7 (một commit tính năng) và S11 (báo cáo). Bước đỏ không sửa được trong phạm vi ⇒ DỪNG (mục 11). Màn Shanling/Sony để **OFF** suốt S0–S8.

**S0 — Kiểm Git.** `git status --short; git branch --show-current; git rev-parse HEAD; git log -5 --oneline; git diff --check`. Phải: nhánh `dot/E94-LOCAL-MUSIC`, HEAD = commit kế hoạch, `git diff ba74206 HEAD -- app/` rỗng, cây sạch, không Gradle/agent khác. Sai ⇒ DỪNG.

**S1 — `MeloLocalSourcePolicy.kt` + test.** Được: hai file mới; dùng `MeloComponentPolicy.searchKey`, `TelegramSearchPolicy.queryTerms`, `MeloAlphabetPolicy.keyOf`, `MeloSourceCardPolicy.Source/Kind`, `SongSort`. Cấm: import `android.*`, đổi file khác. Test mục 6.1: `.\gradlew.bat :app:testShareableDebugUnitTest --tests "*MeloLocalSourcePolicyTest" --console=plain`. Xong khi xanh. Rollback: xóa hai file.

**S2 — `MeloLocalLibrary.kt` + test.** `load()` trên executor: `invalidateCache()` nếu `force`; `!repository.hasPermission()` ⇒ rỗng; `getAllTracks()` → `songOf(...)` (bỏ id ≤ 0) → `all`, `loadedAt`; tính `shown` theo `query/sort`; log W `stage=load count=… ms=… gen=…` + `stage=formats …`; `post { if (gen khớp) onDone() }`. `refreshCount()` trên executor, bắt mọi exception ⇒ null. Test với `LocalMediaRepository(permissionChecker = …, tracksProvider = …)`, `executor = Executor { it.run() }`, `post = { it.run() }`. Cấm: MediaController/TDLib, giữ `Context` ngoài `applicationContext`. Rollback: xóa hai file.

**S3 — Hợp đồng host + UI thuần.** File 3–6 mục 4 + test 6.3. Cấm đổi hành vi khi các cờ mới ở mặc định. Biên dịch được (nếu cần, thêm `openLocal/closeLocal` rỗng vào `meloHost` ngay trong bước này và hoàn thiện ở S4; không commit trạng thái lỗi). Test cũ xanh.

**S4 — Activity: trạng thái, quyền, lối vào, snapshot** (TPA):
- Trường: `localMusic = MeloLocalLibrary(LocalMediaRepository(applicationContext), countQuery = { đếm _ID với "is_music != 0" }, executor = Executors.newSingleThreadExecutor(), post = { mainHandler.post(it) }, clock = SystemClock::elapsedRealtime)`; `localMode = false`; `localPlayEpoch = 0L`; `localPermissionResult = registerForActivityResult(ActivityResultContracts.RequestPermission())` cạnh `notificationPermissionResult` (325).
- `openLocalMusic()`: `auth.isReady()`; `permissionStep(granted = repository.hasPermission(), asked = prefs, rationale = shouldShowRequestPermissionRationale(perm))` ⇒ `enterLocal()` / dialog giải thích → ghi cờ đã hỏi → `launch(perm)` / dialog "Mở cài đặt". Callback: được ⇒ `enterLocal(force = true)`; không ⇒ Toast + `refreshLocalCount()`.
- `enterLocal(force)`: `localMode = true`; chưa nạp/cũ/force ⇒ `localMusic.load(force) { scheduleMeloLibraryRefresh() }`; `scheduleMeloLibraryRefresh()`.
- `closeLocalMusic()` / `exitLocalIfNeeded()`: `localMode = false`, xóa truy vấn local, `scheduleMeloLibraryRefresh()`. Không dừng nhạc local đang phát.
- `refreshLocalCount()`: chỉ khi `auth.isReady()` + đã có quyền; gọi trong `loadDatabaseIndex` ngay sau `startDownloadedCountFlow(userId)` (~5641, trong `runOnUiThread`) và trong `onResume` khi `auth.isReady()`; xong ⇒ `scheduleMeloLibraryRefresh()`.
- `meloHost`: `openLocal()`/`closeLocal()`; `snapshot()` theo 3.5 với `playingKey = currentSongKey()` cả hai chế độ; `setMode`/`showSources`/`openSource` gọi `exitLocalIfNeeded()` đầu tiên; `setQuery`/`setSort`/`sort`/`loadMore`/`jumpToLetter`/`rescan`/`playStatus` (local ⇒ null)/`syncSubtitle` (local ⇒ null) nhánh `localMode`; `sourceSummaries()` = TELEGRAM + `cardSource(...)`.
- `showLibraryOverflow` (2923): mục 11 "♪ Nhạc trên máy" khi `meloLayer != null`; mục 1 và 5 gọi `exitLocalIfNeeded()` trước.
- BACK Activity (471), `showCarLayer` (4122), `showNewLayer` nhánh `layer == null` (4079): theo 3.5.
- Reset: `logoutUser` (cạnh `telegramLibraryTotal = null`, 6253) và nhánh đóng phiên (trong `runOnUiThread`, cạnh `telegramLibraryTotal = null`, ~5357): `localMode = false; localMusic.clear()`. `onDestroy`: `localMusic.shutdown()`.
- Cấm: hỏi quyền tự động; đổi nhánh Telegram ngoài các dòng nêu; ghi DB; gọi TDLib; gọi `getAllTracks/invalidateCache` trên main thread.

**S5 — Activity: phát local.**
- `currentLocalId()`, `currentSongKey()` (3.3). `nowPlaying()` dùng `currentSongKey()`; nhánh local: title/artist từ metadata, `favorite=false`, `art=null`, `downloadNote=null`, `local=true`.
- `playLocal(index)` theo 3.3 (MediaItem có `extras` mime/size/duration). `playAt`/`playAll(shuffle)` nhánh `localMode` (`shuffle` ⇒ `player?.shuffleModeEnabled = true`).
- `downloadAndPlay`: 2 dòng chặn `localPlayEpoch` (3.3). Không sửa gì khác trong hàm.
- `playAdjacent` nhánh local dòng đầu; `addToUpcomingQueue` chặn; `toggleFavorite` local ⇒ Toast.
- `queue()`/`playQueueAt`/`openQueueSongMenu`/`showUpcomingQueue`/`openSongMenu`/`qualityChips`/`qualityLabel`: nhánh **theo `currentLocalId()`** (hàng đợi/viên chất lượng) hoặc `localMode` (danh sách/menu dòng) như 3.3, 3.6.
- `onMediaItemTransition` (535-557): phụ đề local; giữ `currentSongIndex = -1` sẵn có.
- Cấm: sửa `enqueueTelegramPlayback`, `enqueueProgressivePlayback`, `playIfDownloaded`, `prefetchNextCachedNeighbor`, service; dựng `Song` cho bài local.

**S6 — Màn hình.** File 7–10 mục 4. Cấm đổi bố cục/kích thước khác.

**S7 — Kiểm toàn bộ + commit.**
```
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
git diff --check
```
Kỳ vọng: mỗi biến thể 1.263 + số test mới, **21 skipped**, 0 failed; `git diff --stat` chỉ file mục 4. Soát: `grep -n "Log\." ` trong 2 file mới không in tên/đường dẫn/URI. Commit `feat(shareable): local music source (MediaStore, permission on demand)`. Rollback: `git revert` (chưa merge).

**S8 — Gate APK (không bump version).** Bundle delta `<HEAD nhánh tích hợp>..HEAD` + SHA256 → Tiny clone cô lập `C:\Bots\MeloVault-E93D-IsolatedQA`, nhánh `qa/E94-LOCAL-MUSIC`; chạy test + `assembleShareableDebug` có Telegram config (chỉ kiểm có/không). Kiểm APK: package `app.melovault.player`, versionCode **109**, signer `eecd3df7…ce8`, SHA256, bytes. APK Ni (thiếu config) **không cài**.

**S9 — QA Shanling** (7.1 Q1–Q5). **S10 — QA Sony** (7.1 Q6–Q8 + 7.3 tệp thử).

**S11 — Báo cáo** `docs/plans/E94-LOCAL-MUSIC-bao-cao.md`: bảng bước + ảnh + log + `SHANLING_SCREEN_END`/`SONY_SCREEN_END` + tệp thử tạo/xóa (SHA256) + **mục bắt buộc "APK QA cần ChatGPT bàn giao"** (CLAUDE.md mục 9): commit chứa source, đường dẫn APK thật trên Tiny (đã kiểm tồn tại; không xóa/ghi đè APK cũ), Debug, `app.melovault.player`, vc109 / `0.6.88-beta`, SHA-256, bytes, cert SHA-256, Telegram config hợp lệ true/false (không in giá trị), kết quả Gradle, cài/QA Shanling + Sony, điểm chưa QA, rollback, nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**. Sonnet **không** upload Drive, không đổi quyền chia sẻ, không tạo `-apk-test.md`/`-hinh-nghiem-thu.md`. Commit báo cáo, **DỪNG** chờ ChatGPT. Không sửa HANDOFF/BACKLOG.

---

## 6. Regression tests (JVM)

### 6.1 `MeloLocalSourcePolicyTest` (≥ 20 ca)
1. `keyOf` âm, ≤ −2^62, `isLocalKey`; `idOfKey(keyOf(id)) == id`; id khác ⇒ khóa khác; `keyOf(0)`/âm ném lỗi.
2. Không trùng Telegram: nhiều `TelegramSongKey.forMessage(chat, msg, saved)` (Saved + chat khác) ⇒ ≥ 0 và `isLocalKey == false`.
3. `mediaIdOf(42) == "local:42"`; `idOfMediaId` nhận `local:42`, từ chối `telegram:42`, `device:external_primary:42`, `picked:audio`, `local:`, `local:-1`, `local:abc`, `null`; `"local:42".removePrefix("telegram:").toLongOrNull() == null` (chốt TPA:912/1572).
4. `displayArtist`: `"<unknown>"`, `"Nghệ sĩ không rõ"`, `""`, `null` ⇒ thư mục cuối (`"Music/Vietnam"` ⇒ `"Vietnam"`); không thư mục ⇒ `null`; nghệ sĩ thật giữ nguyên.
5. `sorted(NEWEST)` id giảm; `sorted(NAME)` theo `alphaKey`, hòa ⇒ id tăng; "Đêm", "Ánh" xếp như không dấu; `alphaKey == MeloAlphabetPolicy.keyOf(title, artist, null)`.
6. `filtered`: "su thanh hoa" khớp "Sử Thanh Hóa"; AND nhiều từ; khớp nghệ sĩ/album/thư mục; rỗng ⇒ nguyên danh sách; không khớp ⇒ rỗng.
7. **Trùng tên:** hai bài cùng tiêu đề khác id ⇒ hai khóa khác, `sorted`/`filtered` giữ cả hai.
8. `queueWindow`: (0,0) rỗng; (10,3) ⇒ 0..9; (2000,0) ⇒ 0..499; (2000,1500) ⇒ 1400..1899; (2000,1999) ⇒ 1500..1999; index ngoài biên ⇒ kẹp; luôn chứa index.
9. `permissionFor(29/31/32)` = `READ_EXTERNAL_STORAGE`; `permissionFor(33/36)` = `READ_MEDIA_AUDIO`.
10. `permissionStep`: granted ⇒ OPEN; chưa hỏi ⇒ EXPLAIN_THEN_ASK; đã hỏi + rationale ⇒ EXPLAIN_THEN_ASK; đã hỏi + không rationale ⇒ OPEN_SETTINGS.
11. `cardSource`: không quyền ⇒ null; count null/0 ⇒ null; 3 ⇒ `Source(LOCAL, 3, null)`; qua `MeloSourceCardPolicy.cards` cùng TELEGRAM ⇒ 2 thẻ, TELEGRAM trước, LOCAL "3 bài" (không "đã tải").
12. `note`: đang nạp / 0 bài / lọc 0 / bình thường (null).
13. `canQueueNext`: local/local, tg/tg ⇒ true; trộn ⇒ false.
14. `songOf`: id ≤ 0 ⇒ null; duration 0 giữ 0; MIME null giữ null.
15. `formatCounts` chỉ MIME + số.

### 6.2 `MeloLocalLibraryTest` (≥ 8 ca)
Không quyền ⇒ rỗng, `count()` null; có quyền ⇒ đúng số bài; phân trang (`visible` 200 → `loadMore` 400 → trang cuối dừng); `setQuery` lọc; `setSort` đổi thứ tự; `jumpTo("m")` trả index đúng và nới `visible`; `clear()` làm kết quả `load` đang dở bị bỏ (executor giữ Runnable, chạy sau `clear()`); `countQuery` ném lỗi ⇒ null, không crash; `byId`; `version` tăng khi danh sách hiển thị đổi.

### 6.3 Bổ sung file có sẵn
`MeloSourceCardPolicyTest`: `selectedKind(null, false, localOpen = true) == LOCAL`; `selectedKind(5L, false, true) == LOCAL`; ca cũ không đổi; `cardWidthPx(328, 2, 200, 8) == 213` (thẻ 2 lộ nửa). `presentStatus(DOWNLOADED, "FLAC", local = true).label == "Trên máy · FLAC"`; mặc định vẫn "Đã tải · FLAC".

### 6.4 Hồi quy toàn bộ
Toàn bộ suite Debug + Release (gồm `TelegramSongKeyTest`, `MeloSourceCardPolicyTest`, `MeloCompactPolicyTest`, `LocalMediaRepositoryTest`, `TelegramOfflinePlayPolicyTest`) xanh; skipped **21**; assemble Debug + Release thành công; `git diff --check` sạch. Test JVM **không** chứng minh UI/ADB/âm thanh.

---

## 7. Device QA từ xa (Sonnet tự làm qua ADB)

### 7.0 Quy tắc chung
- Trước mọi thao tác, trên **đúng máy** (Shanling ở Ni, Sony ở Tiny): `adb devices -l`; `adb -s SERIAL shell getprop ro.product.model|ro.product.manufacturer|ro.build.version.release`. Sai/unauthorized/không thấy ⇒ máy đó BLOCKED.
- Trước khi cài: `dumpsys media_session` — MeloVault **không** `state=3`; đang phát ⇒ DỪNG hỏi chủ dự án. Ghi versionCode/signer bản đang cài; APK mới cùng package, vc109 (không hạ), cùng signer, có Telegram config.
- **Chỉ** `adb -s SERIAL install -r "<APK Tiny đã kiểm>"`. Cấm: uninstall, `pm clear`, `pm grant/revoke`, xóa dữ liệu/cache/TDLib, đăng xuất, đổi tài khoản, `-d`, máy bay/tắt Wi-Fi, đổi brightness/timeout/AOD, cài FYT.
- Quyền trước QA (chỉ đọc): `dumpsys package app.melovault.player` → `READ_EXTERNAL_STORAGE: granted=…` (19/10 cả hai máy `false`). Nếu lúc QA đã `true` ⇒ bước Từ chối của máy đó **CHƯA KIỂM CHỨNG** (không revoke).
- Số MediaStore đối chiếu (không in tên): `adb -s SERIAL shell content query --uri content://media/external/audio/media --projection _id --where "is_music!=0"` → đếm dòng `Row:` (Shanling 19/10 = 3.557; Sony = 3, sau khi đẩy tệp thử = 6).
- Trạng thái phát: `dumpsys media_session` (state, position, description, queue size) đọc **hai lần cách 5 s**. Crash/ANR: `logcat -d -b crash`, `logcat -d | findstr /i "FATAL ANR MeloVaultLocal"`.
- Hiệu năng (Shanling): `dumpsys meminfo app.melovault.player` (TOTAL, Java Heap) trước/sau mở Nhạc trên máy; thời gian từ log `stage=load ms=`.
- **Màn hình** (`docs/workflow/SCREEN_LIFECYCLE_QA.md`): `dumpsys power`/`dumpsys display` → `input keyevent 224` ngay trước nhóm bước cần màn ON → thao tác/chụp → `input keyevent 223` + xác minh `Display State=OFF` sau mỗi nhóm và trong `finally` (kể cả FAIL/BLOCKED). Ghi `SHANLING_SCREEN_END`, `SONY_SCREEN_END`.
- Ảnh: `cmd /c "adb -s SERIAL exec-out screencap -p > docs\plans\artifacts\E94-LOCAL-MUSIC\NN_<máy>_<việc>_<HHMMSS>.png"`, kiểm header PNG, tự mở xem, không ghi đè. Có thể `screenrecord --time-limit 20` + pull. Ảnh có tên bài ⇒ riêng tư, không commit.
- Chạm: tọa độ từ ảnh/`uiautomator dump` thật; trước mỗi chạm kiểm `dumpsys window | findstr mCurrentFocus` là `app.melovault.player` (hoặc hộp quyền hệ thống ở bước quyền); không BACK ở màn gốc; script PowerShell lưu UTF-8 BOM, tên biến serial khác tên biến đường dẫn.
- **Âm thanh:** Media3 PLAYING + position tăng chỉ chứng minh luồng phát ⇒ **CHƯA XÁC NHẬN ÂM THANH THỰC**.

### 7.1 Bảng bước QA

| # | Máy / bố cục | Màn | Thao tác | Kỳ vọng | Bằng chứng |
|---|---|---|---|---|---|
| Q1 | Shanling, Gọn | ON | Mở app; ☰ | "♪ Nhạc trên máy" ngay dưới "✓ Đã tải"; Telegram "Tất cả · X bài" như trước cài | `01_shanling_library_before`, `02_shanling_menu_local` |
| Q2 | Shanling, Gọn | ON | ☰ → ♪ Nhạc trên máy → "Tiếp tục" → hộp hệ thống **Từ chối**; lặp lại → **Cho phép** | Từ chối: ở lại Telegram, Toast, không danh sách local. Cho phép: tiêu đề "Nhạc trên máy · N bài", N = 3.557 (= MediaStore = log `stage=load count`); dòng "Trên máy · FLAC/MP3" | `03_dialog_explain`, `04_system_deny`, `05_after_deny`, `06_system_allow`, `07_local_list`; log; meminfo trước/sau |
| Q3 | Shanling, Gọn | ON | Chạm một bài local (ưu tiên FLAC); mở Đang phát; biểu tượng tìm → gõ một từ ASCII có trong tên → xóa | state=3, position tăng sau 5 s, metadata = tên dòng; thanh đáy tên + ❚❚; Đang phát có viên định dạng/kHz/dung lượng, "Nghệ sĩ: …", **không Hi-Res**; tìm lọc đúng, xóa về đủ | `08_before_tap`, `09_after_tap_5s`, `10_nowplaying_pills`, `11_search_filtered`; dumpsys ×2 |
| Q4 | Shanling | **OFF** | Tắt màn; `input keyevent 127`, `126`, `87` ×2, `88`, `90` | 127 ⇒ state=2; 126 ⇒ 3; 87 ⇒ bài local kế theo thứ tự danh sách, state=3; 88 ⇒ quay lại; 90 ⇒ position nhảy tới; không bài Telegram xen vào | dumpsys sau mỗi phím |
| Q5 | Shanling, Gọn | ON | Ở danh sách local bấm BACK; ☰ → ✓ Đã tải → chạm dòng 1; ⏭; ☰ → ♪ Nhạc trên máy → chạm bài local; ☰ → ☷ Hàng đợi ở Đang phát | BACK ⇒ về màn Telegram trước đó (không phải lưới kênh); Telegram số bài như Q1, phát Telegram (state=3), ⏭ sang bài Telegram kế; quay lại local phát local; hộp Hàng đợi liệt kê bài local; không FATAL/ANR | `12_back_from_local`, `13_telegram_playing`, `14_telegram_next`, `15_local_again`, `16_local_queue`; logcat crash rỗng |
| Q6 | Sony, Điện thoại dọc | ON | Đẩy tệp thử (7.3) → mở app → ⋮ → ♪ Nhạc trên máy → Từ chối → lặp lại → Cho phép → quay về Telegram (thẻ Telegram) | Quyền nhánh Android 12 như Q2; sau Cho phép hàng thẻ có **2 thẻ**: "Telegram · … bài · đã tải …" không bị "…", "Nhạc trên máy · 6 bài" (thẻ 2 lộ nửa hoặc chia đều, vuốt ngang thấy đủ); hàng thẻ không đè thanh đáy | `20_sony_deny`, `21_sony_allow`, `22_sony_two_cards`, `23_sony_cards_scrolled` |
| Q7 | Sony, Điện thoại dọc | ON | Bấm thẻ Nhạc trên máy; "↕ Mới nhất"→"↕ Tên A–Z"; tìm "melotest" rồi "880"; chạm `melotest_b…`; Đang phát; ⏭ ⏮; chạm sóng âm/thanh tiến trình | Thẻ LOCAL sáng, không có hàng chip; A–Z xếp a→b→c; tìm ra 3 rồi 1; viên "WAV · 96kHz · … MB", **không Hi-Res**; ⏭/⏮ trong bài local; tua đổi position | `24_sony_local_list`, `25_sony_sort_name`, `26_sony_search`, `27_sony_nowplaying`; dumpsys |
| Q8 | Sony, Điện thoại dọc | ON | Đang phát bài local: bấm thẻ Telegram → ⋮ một bài Telegram → "⏭ Phát tiếp theo"; rồi chạm một bài Telegram đã tải; ⏭ | "Phát tiếp theo" ⇒ Toast, hàng đợi vẫn chỉ local (dumpsys queue); chạm bài Telegram ⇒ phát Telegram, ⏭ sang bài Telegram; số bài Telegram không đổi | `28_sony_queue_next_blocked`, `29_sony_telegram_play`; dumpsys |

Tùy chọn (ghi rõ có làm hay không): Sony — chạm bài Telegram **chưa tải, < 20 MB**, trong 2 s chạm bài local; chờ 60 s ⇒ vẫn phát local (chứng minh chặn `localPlayEpoch` + `playIfDownloaded`). Không làm nếu không tìm được bài nhỏ.

Cuối mỗi máy: đưa trình phát về trạng thái lúc bắt đầu (thường tạm dừng, `state=2`), `keyevent 223`, xác minh `Display State=OFF`. Quyền đọc nhạc để nguyên (đã cấp) và ghi vào báo cáo; không revoke bằng adb.

### 7.2 Shanling — ghi chú
- Không đẩy tệp thử lên Shanling (quyết định 5 chỉ cho Sony); dùng 3.557 bài sẵn có.
- Hiệu năng: nạp > 10 s, ANR, OOM hoặc Java Heap tăng > 96 MB ⇒ FAIL, báo ChatGPT (đề xuất giai đoạn 1b đọc MediaStore gọn hơn). 5–10 s hoặc 48–96 MB ⇒ cảnh báo, không tự sửa.

### 7.3 Sony — tệp thử `Music/MeloTest` (quyết định 5)
1. Tạo 3 tệp WAV sóng sin, không nhạy cảm, bằng Python thư viện chuẩn (`wave`, `struct`, `math`) trong thư mục tạm ngoài repo `C:\Bots\_qa_temp\E94-LOCAL-MUSIC\` trên Tiny: `melotest_a_440hz_16bit_44k.wav` (20 s, 44,1 kHz/16 bit, stereo), `melotest_b_880hz_24bit_96k.wav` (10 s, 96 kHz/24 bit), `melotest_c_660hz_16bit_48k.wav` (10 s, 48 kHz/16 bit). Ghi SHA256 + bytes. Tiny không có Python ⇒ tạo trên Ni, chép vào thư mục tạm đó (không vào repo Tiny).
2. `adb -s QV725WEQ54 shell ls -la /sdcard/Music/MeloTest`: chưa có (19/10: chưa có) ⇒ `mkdir`, ghi "thư mục do nhiệm vụ tạo"; đã có ⇒ không đụng tệp khác. **Trùng tên ⇒ không ghi đè, DỪNG phần Sony.**
3. `adb push` từng tệp vào `/sdcard/Music/MeloTest/<tên>`. Kiểm `content query --uri content://media/external/audio/media --projection _id:_display_name --where "relative_path='Music/MeloTest/'"` ⇒ đúng 3 dòng. Thiếu ⇒ `am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///storage/emulated/0/Music/MeloTest/<tên>` từng tệp rồi kiểm lại; vẫn thiếu ⇒ BLOCKED (không reboot).
4. Sau QA (trong `finally`): dừng phát nếu đang phát tệp thử; `adb shell rm "/sdcard/Music/MeloTest/<đúng tên>"` cho **từng** tệp (không `*`, không `rm -r`); thư mục do nhiệm vụ tạo và rỗng ⇒ `rmdir`. Kiểm `content query` còn 0 dòng; còn dòng cũ ⇒ broadcast từng đường dẫn rồi ghi kết quả. Ghi danh sách tạo/xóa vào báo cáo.

### 7.4 Gói bằng chứng và bàn giao APK
`docs/plans/artifacts/E94-LOCAL-MUSIC/`: ảnh/video theo 7.1, `dumpsys_*.txt`, `logcat_local_*.txt` (lọc, không tên bài), `meminfo_*.txt`, `melotest_files.txt`. Báo cáo trỏ đúng tên tệp. Việc của **ChatGPT** (Sonnet không làm): chọn 5–10 ảnh lên Drive Ni N riêng tư + `docs/plans/E94-LOCAL-MUSIC-hinh-nghiem-thu.md`; kiểm độc lập APK (SHA/bytes/cert/package/version/config), upload owner-only theo TASK_ID/ngày/commit, gửi link APK trực tiếp + `docs/plans/E94-LOCAL-MUSIC-apk-test.md`, nhãn **QA CANDIDATE — CHƯA NGHIỆM THU** (CLAUDE.md mục 9).

### 7.5 Bác sĩ tự kiểm tra (Sonnet làm qua ADB, chủ dự án duyệt ảnh/video từ xa)
1. **Shanling M3 Ultra, Thư viện Gọn:** ☰ → "♪ Nhạc trên máy" → "Tiếp tục" → "Cho phép" ⇒ tiêu đề "Nhạc trên máy · 3.557 bài" và các dòng "Trên máy · FLAC/MP3".
2. **Shanling, danh sách Nhạc trên máy:** chạm một bài FLAC ⇒ thanh đáy hiện tên bài, nút ❚❚; mở Đang phát thấy viên "FLAC · …kHz · … MB", **không** có Hi-Res.
3. **Shanling, tắt màn:** bấm ⏭ (phím vật lý/tai nghe/MediaSession) ⇒ sang bài kế trong Nhạc trên máy, nhạc vẫn chạy.
4. **Shanling:** BACK về Telegram → ☰ → ✓ Đã tải → chạm một bài Telegram ⇒ phát bài Telegram; ⏭ sang bài Telegram kế, không lẫn bài trên máy; số bài Telegram như trước.
5. **Sony Xperia 5 II, Điện thoại dọc:** đầu Thư viện có hai thẻ "Telegram" và "Nhạc trên máy", chữ không bị "…", vuốt ngang thấy đủ; bấm thẻ Nhạc trên máy ⇒ danh sách có 3 bài thử; "↕ Tên A–Z" xếp lại đúng.
6. **Sony, quyền:** lần đầu "Từ chối" ⇒ không có thẻ Nhạc trên máy, app không tự hỏi lại; bấm "♪ Nhạc trên máy" lần nữa → "Cho phép" ⇒ có thẻ.

Giới hạn: không chứng minh âm thanh thật; không kiểm Android 13+ (`READ_MEDIA_AUDIO`), Y700, S25, FYT, Car Mode; không thử rút thẻ SD/xóa tệp đang phát.

---

## 8. Rủi ro và rollback

| Rủi ro | Giảm thiểu | Phát hiện |
|---|---|---|
| Bấm Telegram rồi bấm local khi resolve chưa xong ⇒ Telegram chiếm lại trình phát | `localPlayEpoch` chặn callback `downloadAndPlay`; xóa `downloadWaiting` | Bước tùy chọn Q8, soát diff |
| Danh sách vẽ lại mỗi giây (`rowsStale`) | `currentSongKey()` dùng chung cho `playingKey` và `NowPlaying.songKey`; cache Row local | Quan sát cuộn/giật Q3, Q7 |
| Sót đường Telegram làm trộn hàng đợi / gọi TDLib với bài local | `setMediaItems` thay toàn bộ; mediaId tiền tố chữ; khóa âm; chặn "Phát tiếp theo"; không dựng `Song` cho local | Test 6.1 (3, 13), Q5, Q8 |
| Ghi khóa local vào Yêu thích Telegram | Khóa âm; toggle local chỉ Toast | Test 6.1 (1-2), soát diff |
| Treo UI do khóa của repository | Mọi `getAllTracks/invalidateCache` trên `localExecutor` | ANR Q2 |
| Thư viện lớn chậm/tốn heap | Executor riêng, chỉ đếm lúc mở app, ngưỡng 7.2 | Log `stage=load ms`, meminfo |
| Định dạng không hỗ trợ | Toast lỗi, không crash | `stage=formats`, Q3 |
| Hộp quyền OEM khác | Lấy chữ nút từ `uiautomator dump` | Ảnh Q2/Q6 |
| Android 13+ chưa thử | Test `permissionFor(33)`; chủ dự án thử Y700/S25 | [CẦN XÁC NHẬN] |
| Lộ dữ liệu riêng tư | Log chỉ số; ảnh private, không commit | Soát log/ảnh |
| `TelegramPreviewActivity` phình to | Logic ở 2 file mới | Diff stat |

**Rollback:** mã — `git revert <commit tính năng>` trên `dot/E94-LOCAL-MUSIC` (chưa merge). Thiết bị — `adb install -r` lại APK vc109 trước đó (Shanling: bản đang cài, kéo `base.apk` về lưu ngoài repo trước khi cài; Sony: b109 `7989C5F9…5C3B`), cùng versionCode nên không phải gỡ; quyền đọc nhạc còn lại vô hại (tắt trong Cài đặt nếu muốn). Tệp thử Sony xóa ở 7.3. Không đổi DB/TDLib nên không cần khôi phục dữ liệu.

---

## 9. Tiêu chí PASS / REWORK / BLOCKED

**Đạt kỹ thuật (ChatGPT kiểm):** diff chỉ file mục 4; không đổi service/DB/schema/TDLib/manifest/gradle/`songKey`/`main`/`personal`/`MeloCarScreen`; thay đổi trong đường phát Telegram chỉ là 2 dòng `localPlayEpoch`; test Debug + Release xanh, skipped 21, các test mục 6 có mặt; assemble Debug + Release thành công; APK Tiny đúng package/vc109/signer/config; Q1–Q8 PASS có ảnh/dumpsys; thẻ hai nguồn không cắt chữ; không Hi-Res cho bài local; không FATAL/ANR; tệp thử Sony đã xóa đúng; `SHANLING_SCREEN_END=OFF`, `SONY_SCREEN_END=OFF`; báo cáo có đủ mục "APK QA cần ChatGPT bàn giao" khớp commit/SHA của APK đã cài.

**REWORK:** bài Telegram xen vào hàng đợi local hoặc ngược lại; Telegram chiếm lại trình phát sau khi đã bấm bài local; ⏭/⏮/MediaSession không chạy với local; BACK từ local về lưới kênh; Telegram phát/đếm/đăng nhập thay đổi; app tự hỏi quyền khi mở hoặc hỏi lặp; thẻ "Nhạc trên máy" hiện khi chưa có quyền hoặc 0 bài; chữ thẻ bị "…" trên Sony; viên Hi-Res cho bài local; log có tên bài/đường dẫn; sửa file cấm; test đỏ/skipped khác 21 không giải thích; vượt ngưỡng 7.2.

**BLOCKED:** ADB không thấy/unauthorized; APK thiếu Telegram config, sai signer, hạ version; máy đang phát nhạc của chủ dự án lúc cần cài; MediaStore Shanling 0 bài; tệp thử Sony trùng tên hoặc không vào MediaStore; hộp quyền không thao tác an toàn; hoặc chưa có chủ dự án duyệt ảnh/video ⇒ **BLOCKED — CHỜ CHỦ DỰ ÁN**. PASS cuối chỉ khi ChatGPT kiểm toán độc lập và chủ dự án duyệt mục 7.5; ghi rõ âm thanh thật, Android 13+, Y700/S25/FYT chưa kiểm.

---

## 10. Những vấn đề [CẦN XÁC NHẬN]

1. 3.557 bài `is_music` của Shanling (19/10) có gồm đủ nhạc thẻ SD và khớp thư viện app nhạc Shanling không.
2. Thời gian nạp và heap thật với 3.557 bài trên Shanling (ngưỡng 7.2 là ước lượng).
3. Nhánh `READ_MEDIA_AUDIO` Android 13+ (Y700, S25) — không có máy ADB.
4. ALAC/AIFF/APE/DSD/WMA trên Shanling; nhạc trong thư mục không được đánh dấu `IS_MUSIC`.
5. "Mới nhất" theo `_ID` (thứ tự quét) thay vì DATE_ADDED.
6. Chữ "Trên máy", ẩn hàng chip khi local, BACK từ local về màn Telegram trước đó — chờ chủ dự án xem ảnh.
7. Âm thanh thật (không có nguồn thu độc lập).
8. Tiny có Python không (nếu không thì tạo WAV trên Ni).
9. Hết cửa sổ 500 bài thì dừng (chưa nạp cửa sổ sau) — chấp nhận ở giai đoạn 1?
10. Thời điểm bump versionCode/đưa Drive — ChatGPT/chủ dự án quyết sau nghiệm thu.

---

## 11. Điểm Sonnet phải DỪNG và báo

- Git/HEAD/nhánh/cây khác S0; agent/Gradle khác đang chạy.
- Cần đổi schema/DB, `songKey`, `ShareablePlaybackService`, TDLib, manifest, `build.gradle`, `LocalMediaRepository`/`main`/`personal`, `MeloCarScreen`, hoặc sửa đường phát Telegram nhiều hơn 2 dòng `localPlayEpoch`.
- Media3 không phát `content://` từ controller cùng tiến trình (cần `MediaSession.Callback`) ⇒ dừng, không sửa service.
- Diff production vượt ~850 dòng hoặc phải sửa file ngoài mục 4.
- Signature mismatch, downgrade, sai package, APK thiếu Telegram config, ADB không thấy/unauthorized, máy đang phát nhạc của chủ dự án.
- Hộp quyền không nhận diện được nút an toàn; tệp thử trùng tên; MediaStore không nhận tệp thử sau broadcast.
- Vượt ngưỡng 7.2, ANR, OOM, FATAL.
- Màn không tắt được (`Display State` vẫn ON) hoặc máy khóa cần PIN.

---

## 12. Ngoài phạm vi — gợi ý BACKLOG (ChatGPT quyết)

Playlist/Yêu thích cho bài local (cần khóa local lưu được), hàng đợi trộn nguồn, nạp cửa sổ hàng đợi kế khi hết 500 bài, duyệt thư mục/ổ (`LocalMediaFolder`), ContentObserver, ảnh bìa nhúng, sóng âm + độ sâu bit qua `ContentResolver` (Hi-Res thật cho FLAC 24bit local), DATE_ADDED, truy vấn MediaStore gọn/phân trang nếu chậm, Car Mode cho nhạc local, chữ "Telegram" còn ở Cockpit/hộp cũ/Car khi phát local, tự nhảy bài khi lỗi định dạng.
