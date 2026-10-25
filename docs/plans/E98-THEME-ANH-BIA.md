# Kế hoạch — E98-THEME-ANH-BIA (theme "Teal sáng" + ảnh bìa nhỏ cho mọi bài)

**Vai trò:** Claude Opus lập kế hoạch, 25/10/2026. **TASK_ID:** `E98-THEME-ANH-BIA`. **Nhánh:** `dot/E98-THEME-ANH-BIA`.
Sau khi duyệt, Opus chỉ tạo nhánh và commit **một file** `docs/plans/E98-THEME-ANH-BIA.md` (nội dung = kế hoạch này). Không sửa code/build/cài.

## 0. Bối cảnh

Chủ dự án gửi ảnh mẫu (tablet ngang, Hai khung) và muốn một **theme mới**:
- **Mọi dòng bài hát có ảnh bìa nhỏ** bên trái để dễ nhận ra bài.
- **Hàng menu 4 ô** có biểu tượng: `Tất cả (27K)` · `Đã tải về máy` · `Playlist` · `Nguồn Telegram`; trên cùng là `☁ Đồng bộ: 27.971 bài ●` và nút ⚙; ô tìm kiếm có nút bộ lọc; thanh A–Z bên phải.
- Khung trái: logo **Melo**(trắng)**Vault**(teal), ảnh bìa lớn, tên bài, kênh, viên chất lượng `MP3 • 44.1 kHz • 320 kbps`, thanh tiến trình + giờ, 3 nút lớn ⏮ ⏸ ⏭, lưới 2×2 `Đảo` / `Không lặp` / `Lưu bài` / `Hàng đợi`.
- Màu **sáng hơn một chút** so với hiện tại.

**Chủ dự án đã chốt (25/10):**
1. Ảnh bìa bài chưa tải lấy **ảnh bìa thật từ Telegram**; không có thì ảnh kênh; cuối cùng ô chữ cái.
2. Theme là **lựa chọn mới** "Kiểu màu" trong Cài đặt giao diện, **mặc định giữ giao diện hiện tại**. Áp dụng cho **Hai khung** và **Điện thoại/Tablet dọc**. **Gọn (Shanling) và Car Mode giữ nguyên.**
3. Nút thể loại (Chillout/LakHouse/Mixset) và sắp xếp Tên A–Z **chuyển vào nút bộ lọc**; hàng menu đúng 4 ô.
4. Nền mã: **`dot/E97A-RECOVERY-VC111` @ `826d166`** (đã chứa E95 + E96 + E97A, vc111).

Nhiệm vụ **không đủ điều kiện làn nhanh** (nhiều màn, >80 dòng, có truy vấn TDLib) ⇒ quy trình đầy đủ Opus → Sonnet → ChatGPT.

## 1. Git baseline (đọc 25/10/2026)

| Mục | Giá trị |
|---|---|
| Workspace | `D:\Bots\MeloVault-E91-Shareable`, cây sạch, đang ở `dot/E95-FYT-UI-OPTIMIZATION` @ `9ee6f71` |
| Nền cho E98 | `dot/E97A-RECOVERY-VC111` @ `826d166` (= `3c448d3` + bump vc111/0.6.88.1); E95 và E96-P5B3-REWORK-01 đều là tổ tiên |
| Nhánh tích hợp | `feature/e93-03d-y700-offline-cockpit` @ `0b4aa49` (cũ, chưa có E95–E97A) |
| Worktree khác | Nhiều worktree E96/E97A ở `D:\Bots\MeloVault-E96-*`, `MeloVault-E97A-*` — **không đụng** |

Sonnet S0: kiểm `git status --short` sạch, `git rev-parse dot/E98-THEME-ANH-BIA^` = commit kế hoạch có cha `826d166`; nếu ChatGPT/chủ dự án đã merge nền mới hơn thì DỪNG hỏi. Số dòng dưới đây đọc trên `9ee6f71`/`826d166`; **định vị lại bằng tên hàm**.

## 2. Hiện trạng mã (đã đối chiếu)

Thư mục: `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/`.

- **Dòng bài đã có ô ảnh** bên trái: `MeloComponents.songRow()` → `artView(model.art, …, sizes.rowArt)`; không có bitmap thì ô chữ cái màu (`MeloComponentPolicy.initialOf/placeholderColor`). Kích thước ô: PHONE 56dp / TABLET 64dp (`MeloDesignTokens`).
- **Không có bộ tải ảnh theo dòng.** `snapshot()` trong `TelegramPreviewActivity` chỉ đọc `thumbnailCache.get(song.messageId)` (LruCache 60 mục, khóa = songKey, không có account) — chỉ có ảnh khi màn cũ/mini player tình cờ đã nạp. Bài trên máy (E94) luôn `art = null`.
- TDLib dùng JSON (`send(type, tag){…}`, `onNativeEvent`, `updateFile`). `parseTelegramSong` **bỏ qua** `album_cover_minithumbnail`/`album_cover_thumbnail` (audio) và `minithumbnail`/`thumbnail` (document). DB **không có** cột ảnh — **không thêm cột** (cấm đổi schema).
- `getChat` đã được gửi (tag `sourceName:`) để lấy tên kênh; JSON chat có `photo.small` (file ảnh kênh) nhưng chưa dùng.
- Danh sách Phone/Tablet/Hai khung: `MeloLibraryPane.RowsAdapter` (ListView, dựng lại dòng mỗi lần `getView`, không tái dùng `convertView`).
- Màu: `MeloDesignTokens.PALETTE` (+ `CAR_PALETTE`), chọn theo profile trong `MeloComponents(…, palette = MeloDesignTokens.palette(profile))`. Không có lựa chọn màu cho người dùng. Mọi nền dựng bằng `GradientDrawable` một màu, chưa có gradient.
- Menu nhóm: `MeloLibraryPolicy.chips()` → `Tất cả`, `Đã tải (n)`, `Playlist`, `Chillout`, `LakHouse`, `Mixset`; vẽ bằng `MeloComponents.chip()` (chỉ chữ). Không có ô "Nguồn Telegram" (mở nguồn qua `host.showSources()` từ cột điều hướng/thẻ nguồn).
- Ô tìm: `MeloComponents.searchBar()`; nút "☰ Bộ lọc" **hiện nhưng không làm gì** (`MeloLibraryPane` truyền `onFilter = null`); pane lấy EditText bằng `getChildAt(1)` (dễ vỡ).
- A–Z: `MeloAlphabetPolicy` + `MeloAlphabetBar`, chỉ hiện khi sắp xếp Tên A–Z và ≥ 50 dòng.
- Hai khung: `MeloTabletScreen` có **cột điều hướng** (Thư viện/Telegram/Playlist/Đang phát/⚙) + khung trái Đang phát (ảnh, tên, nguồn, chất lượng dạng chữ, giờ dạng chữ — **không có thanh tiến trình** từ E95 F09, ⏮▶⏭, 4 chip Đảo/Lặp/Lưu/Hàng đợi).
- Biểu tượng vector có sẵn ở `app/src/main/res/drawable/` (`ic_play_arrow, ic_pause, ic_skip_next, ic_skip_previous, ic_shuffle, ic_repeat, ic_repeat_one, ic_search, ic_cloud, ic_download, ic_download_done, ic_playlist, ic_queue_music, ic_library_music, ic_heart_*, ic_send`…); **thiếu** ⚙ settings và "tune/thanh trượt". Màn Melo hiện chỉ dùng ký tự Unicode.
- Cài đặt: `MeloUiLabActivity.render()` (prefs `melovault_ui_lab`), mẫu `choiceRow` như mục "Thư viện" (`MeloLibraryPolicy.LibraryStyle`).
- Màn được dựng lại khi khóa `"new:$layer:$profile:$densityDpi:$fontScale"` đổi (`showNewLayer`).

## 3. Phương án

### 3.1 Lựa chọn "Kiểu màu" (không đổi mặc định)
- Tệp mới `MeloThemePolicy.kt` (logic thuần): `enum ColorTheme(key, label) { CURRENT("current","Hiện tại"), TEAL_BRIGHT("teal_bright","Teal sáng · ảnh bìa") }`, `KEY = "color_theme"`, `KEY_ROW_ART = "row_art_telegram"` (mặc định `true`), `load/save`, `fromKey` (khóa lạ → CURRENT), `appliesTo(layer, profile)` = TEAL_BRIGHT **và** layer ∈ {TWO_PANE, PHONE_PORTRAIT, TABLET_PORTRAIT} **và** profile ≠ CAR/COMPACT.
- `MeloDesignTokens`: thêm `TEAL_PALETTE` (sáng hơn ~1 bậc) + vài màu phụ cho gradient: nền `#10263A`→`#0C1C2B` (dọc), surface `#16334A`, surfaceRaised `#1D4058`, border `#2C5670`, accent `#36E2C2`, accentEnd `#22B8A6` (gradient nút/ô chọn), accentSoft `#1A5552`, text `#F3FAFC`, textSecondary `#B8CCD6`, onAccent `#05231D`, warning `#FF8B7B`, statusOk `#3DDC97`. Hàm `palette(profile, theme)`; CAR/COMPACT luôn trả palette cũ. **Giữ nguyên PALETTE/CAR_PALETTE** (test cũ không đổi).
- Độ tương phản WCAG ≥ 4.5:1 cho text/textSecondary trên surface & surfaceRaised, ≥ 3:1 cho onAccent trên accent ⇒ có unit test tính contrast.
- `MeloComponents` nhận thêm `theme: ColorTheme` (mặc định CURRENT) và helper `gradient(top,bottom,radius)`; mọi thay đổi hình dạng chỉ chạy khi `theme == TEAL_BRIGHT` ⇒ CURRENT giữ nguyên từng pixel.
- `MeloUiLabActivity`: mục mới **"Kiểu màu"** (choiceRow Hiện tại / Teal sáng · ảnh bìa) + công tắc **"Tải ảnh bìa từ Telegram"** (Bật/Tắt — công tắc ngắt khẩn cấp, rollback mềm).
- `TelegramPreviewActivity.showNewLayer`: thêm `theme.key` vào khóa dựng lại; truyền theme vào screen.

### 3.2 Bộ tải ảnh bìa theo dòng (chỉ chạy khi TEAL_BRIGHT)
Tệp mới `MeloRowArtPolicy.kt` (thuần, test JVM) + `MeloRowArtLoader.kt` (Android).

**Thứ tự nguồn ảnh cho mỗi dòng:**
1. **Bộ nhớ đệm** (LruCache theo **byte**, ~1/16 memoryClass, trần 12 MB; khóa `"$accountId:$chatId:$messageId"` cho Telegram, `"local:$mediaStoreId"` cho bài trên máy) + bộ nhớ "không có ảnh" (negative cache) để không hỏi lại.
2. **Bài đã tải** (đường dẫn trong `cachedSongPaths`, qua `TelegramAccountStoragePolicy.isOwnedFile`): `MediaMetadataRetriever.embeddedPicture`, giải mã theo **kích thước ảnh** (`inJustDecodeBounds` + inSampleSize; sửa không dùng `computeSample` theo số byte), trần 8 MB như `MeloStudioDeck.embeddedCover`.
3. **Bài trên máy (E94)**: `ContentResolver.loadThumbnail(ContentUris.withAppendedId(Audio.Media.EXTERNAL_CONTENT_URI, id), Size(px,px))` (API 29+); API < 29 bỏ qua → ô chữ cái.
4. **Telegram ảnh bìa thật**: `getMessage(chat_id, message_id)` tag `rowArtMsg:$gen:$chat:$msg` → `MeloRowArtPolicy.pickThumb(messageJson)` trả `{minithumbData?, thumbFileId?, w, h}` từ `audio.album_cover_minithumbnail`/`album_cover_thumbnail` hoặc `document.minithumbnail`/`thumbnail` (chỉ nhận format jpeg/png/webp; bỏ qua tgs/webm/mpeg4).
   - Có `minithumbnail` (base64 nhỏ ~40px) ⇒ hiện **ngay** (mờ) trong lúc chờ.
   - Có `thumbnail.file`: nếu `local.is_downloading_completed` ⇒ giải mã luôn; chưa có ⇒ `downloadFile(file_id, priority=1, synchronous=false)` tag `rowArtFile:$gen:$fileId`; kết quả đến qua `updateFile` (thêm một dòng chuyển tiếp `rowArtLoader.onFile(file)` trong nhánh `updateFile` và `kind=="file"` có tag `rowArtFile:`). Tệp ảnh do TDLib tự quản trong thư mục của nó; **không** tự ghi đĩa cache riêng, **không** xoá gì.
5. **Ảnh kênh**: `getChat` tag `rowArtChat:$gen:$chatId` (một lần/kênh/phiên) → `photo.small` (+`photo.minithumbnail`) → cùng đường tải như trên; map `chatId → bitmap` trong RAM.
6. Không có gì ⇒ ô chữ cái hiện tại.

**Giới hạn & huỷ:**
- Chỉ tải cho **dòng đang hiện + 4 dòng kế**; khi cuộn nhanh (`SCROLL_STATE_FLING`) tạm dừng, nạp khi `IDLE`/`TOUCH_SCROLL`.
- Tối đa **3** yêu cầu TDLib đang chạy; hàng đợi LIFO (dòng mới hiện ưu tiên); bỏ yêu cầu khi dòng đã ra khỏi màn.
- `generation: AtomicLong` tăng khi đổi tài khoản / đăng xuất / đổi theme / tắt công tắc / `onDestroy`; mọi phản hồi kiểm `gen` + `library.authorizedUser()?.value == account` + `!isDestroyed` (theo mẫu kiểm có sẵn ~dòng 3978).
- Giải mã trên một executor nền riêng (`MeloVault-RowArt`, ưu tiên thấp), **không** trên luồng TDLib/UI. Lỗi TDLib (`kind=="error"` với tag `rowArt*`) ⇒ ghi negative cache, `return` sớm (như `sourceName:`), không hiện thông báo.
- Kích thước giải mã: cạnh = `sizes.rowArt` px (≤ 192px).
- Khi ảnh về: **không** gọi `notifyDataSetChanged`; gom 120 ms rồi cập nhật trực tiếp các dòng đang hiện có `tag == artKey` (dòng tạo bởi `songRow` gắn tag vào ImageView). `snapshot()` dùng `rowArtLoader.peek(key)` để dòng dựng lại có ảnh ngay.
- Đăng xuất/đổi tài khoản: `rowArtLoader.clear()` cạnh chỗ đang `thumbnailCache.evictAll()`/`TelegramProgressiveCache.clear()`.
- Khi theme = CURRENT hoặc công tắc Tắt: loader không gửi gì ⇒ hành vi cũ nguyên vẹn.

### 3.3 Dòng bài theo theme (giống ảnh mẫu)
`MeloComponents.songRow` nhánh TEAL_BRIGHT: thẻ bo 14dp nền `surface` + viền `border`, khoảng cách dòng 8dp; ảnh bìa vuông bo 10dp (giữ `rowArt` 56/64dp); **tên bài** 1–2 dòng chữ to; dòng phụ nhỏ `nghệ sĩ` (E95 F08) hoặc `kênh` màu textSecondary 1 dòng; bên phải: **biểu tượng trạng thái** (`MeloStatusIconView`: sóng nhạc đang phát / ✓ đã tải trong vòng tròn xanh / đám mây chưa tải / vòng tiến độ) → **thời lượng** → **⋮**. Dòng đang phát: nền gradient accentSoft→accent 35%, viền accent, tên màu accent. Giữ nguyên tag `MELO_SONG_ROW_MORE_TAG`, click/long-click qua ListView như cũ.

### 3.4 Đầu trang, 4 ô menu, ô tìm, bộ lọc, A–Z
- **Dòng đồng bộ** (đầu khung phải Hai khung / dưới logo ở dọc): biểu tượng `ic_cloud` + `Đồng bộ: 27.971 bài` + chấm tròn 8dp: xanh `statusOk` = đã đồng bộ, vàng `warning` nhạt = đang đồng bộ, xám = chưa kết nối (suy từ dữ liệu `syncSubtitle()`/`snap.total` sẵn có, hàm thuần `MeloThemePolicy.syncDot(...)`). Bên phải: nút ⚙ (vector `ic_settings` mới) → `host.openUiLab()`.
- **4 ô menu** (thay hàng chip khi TEAL_BRIGHT): hàm mới `MeloComponents.tabCard(icon, label, selected)`; ô chọn = gradient accent→accentEnd, chữ đậm; ô thường = surfaceRaised + viền. Nhãn từ hàm thuần `MeloLibraryPolicy.themeTabs(totalSongs, downloadedCount, widthDp)`:
  - `Tất cả (27K)` [ic_library_music] → ALL; số rút gọn bằng `MeloComponentPolicy.shortCount` (999 → "999", 9.540 → "9,5K", 27.971 → "27K", 1.250.000 → "1,2M"; chưa biết → không hiện ngoặc).
  - `Đã tải về máy` [ic_download] → DOWNLOADED.
  - `Playlist` [ic_playlist] → PLAYLIST.
  - `Nguồn Telegram` [ic_send] → `host.showSources()`; ô được tô khi `snap.showingSources`.
  - Khi khung hẹp (< 520dp) rút nhãn: `Tất cả`, `Đã tải`, `Playlist`, `Nguồn`; vẫn cuộn ngang được nếu thiếu chỗ.
- **Ô tìm** TEAL_BRIGHT: bo 16dp, `ic_search`, gợi ý `Tìm bài hát theo tên...`, nút `ic_tune` (vector mới) bên phải. Sửa `searchBar` trả về đối tượng có `field: EditText` để bỏ `getChildAt(1)` (áp dụng cả CURRENT, không đổi giao diện).
- **Bảng bộ lọc** (bấm `ic_tune`): dialog dùng theme dialog Shareable có sẵn (E96 `themes.xml`): mục **Sắp xếp** (Mới nhất / Tên A–Z → `MeloAlphabetPolicy.SongSort`, lưu `library_sort` như cũ) và **Thể loại** (Tất cả / Chillout / LakHouse / Mixset → `MeloLibraryPolicy.queryFor(genre)` hiện có). Ô tìm hiện chip nhỏ "Chillout ✕" khi đang lọc thể loại để bỏ lọc. Khi CURRENT: giữ hàng chip cũ, nút lọc vẫn không làm gì như trước.
- **A–Z** TEAL_BRIGHT: hiện khi ≥ 50 dòng và không ở lưới nguồn (bỏ điều kiện chỉ khi sắp xếp Tên); chữ đang chọn vẽ trong vòng tròn accent. Bấm chữ khi đang "Mới nhất" ⇒ chuyển sang Tên A–Z (lưu pref) rồi nhảy, hiện toast `Đã sắp xếp theo tên A–Z`. Logic trong `MeloAlphabetPolicy.barVisibleThemed(...)` và `needsSortSwitch(sort)`.

### 3.5 Khung trái Hai khung (Đang phát) theo ảnh mẫu
Chỉ `MeloTabletScreen`, chỉ TEAL_BRIGHT:
- **Ẩn cột điều hướng** (chức năng đã có ở 4 ô menu + ⚙). Bấm ảnh bìa lớn = mở Đang phát đầy đủ như NavItem NOW hiện có.
- Đầu khung trái: logo `Melo` (text) + `Vault` (accent) bằng `SpannableString`.
- Ảnh bìa bo 20dp, đổ bóng nhẹ (`elevation`); giữ `fitArt/sizeArt` của E95.
- Tên bài đậm căn giữa; dòng nguồn/nghệ sĩ giữ `MeloNowPlayingPolicy.sourceLine`; **viên chất lượng** (pill viền, chữ `host.qualityLabel`) thay chữ thường.
- **Thanh tiến trình chỉ hiển thị** (không kéo/chạm tua — giữ nguyên quyết định E95 F09/E97A an toàn khi lái xe): `ProgressBar` ngang mảnh với chấm tròn ở đầu (vẽ bằng drawable), giờ hiện tại trái, tổng phải; cập nhật cùng nhịp `time` hiện có.
- 3 nút lớn bo 18dp dùng vector `ic_skip_previous` / `ic_play_arrow|ic_pause` / `ic_skip_next`; nút giữa gradient accent.
- Lưới 2×2 có biểu tượng: `Đảo: Tắt|Bật` [ic_shuffle], `Không lặp | Lặp tất cả | Lặp 1 bài` [ic_repeat/ic_repeat_one], `Lưu bài | Đã lưu` [ic_heart_outline/filled], `Hàng đợi` [ic_queue_music]. Nhãn từ hàm thuần mới trong `MeloNowPlayingPolicy` (`themedShuffleLabel`, `themedRepeatLabel`, `themedFavoriteLabel`); hành động giữ nguyên.
- Nền khung: gradient palette; **tùy chọn (bước S6, có thể bỏ nếu giật)**: ảnh bìa thu nhỏ 24px phóng to alpha 30% làm nền mờ (chạy được Android 10, không cần RenderEffect).
- Điện thoại/Tablet dọc: `MeloPhoneScreen` đổi đầu trang (logo hai màu, dòng đồng bộ + ⚙), 4 ô menu, ô tìm, dòng bài; **màn Đang phát dọc (Studio Deck) không đổi** trong E98.

### 3.6 Không làm / không đổi
DB/schema, songKey, sync/TDLib scan, Media3/queue/service, đăng nhập/tài khoản, signing/package, Car Mode, Gọn (Shanling mặc định), Studio Deck, giao diện "Cũ (Cockpit)". Không thêm thư viện (không dùng Coil cho Shareable). Không thêm quyền.

## 4. File

| File | Thay đổi |
|---|---|
| `MeloThemePolicy.kt` (mới) | enum ColorTheme, prefs, appliesTo, syncDot |
| `MeloRowArtPolicy.kt` (mới) | pickThumb(JSON), artKey, decodeSample(w,h,target), hàng đợi/giới hạn thuần, shouldLoad(scrollState) |
| `MeloRowArtLoader.kt` (mới) | cache byte, executor, getMessage/getChat/downloadFile, onFile, onError, generation, peek/request/cancelInvisible/clear |
| `MeloDesignTokens.kt` | TEAL_PALETTE + `palette(profile, theme)` |
| `MeloComponents.kt` | tham số theme, gradient, songRow nhánh theme + tag ảnh, tabCard, iconButton dùng drawable, searchBar trả field, qualityPill |
| `MeloComponentPolicy.kt` | `shortCount` |
| `MeloLibraryPolicy.kt` | `themeTabs` |
| `MeloAlphabetPolicy.kt`, `MeloAlphabetBar.kt` | quy tắc hiện khi theme, vòng tròn chữ chọn |
| `MeloNowPlayingPolicy.kt` | nhãn 2×2 theo theme |
| `MeloLibraryPane.kt` | đầu trang đồng bộ, 4 ô, ô tìm + bộ lọc, gọi loader theo dòng hiện, cập nhật ảnh theo tag |
| `MeloTabletScreen.kt` | ẩn nav, logo, khung trái mới |
| `MeloPhoneScreen.kt` | đầu trang theme |
| `MeloScreenHost.kt` | `colorTheme()`, `requestRowArt(rows, visibleRange)`, `peekRowArt(key)`, `openFilter()` (hoặc pane tự mở), `setGenre()` |
| `MeloUiLabActivity.kt` | mục Kiểu màu + công tắc ảnh bìa |
| `TelegramPreviewActivity.kt` | tạo loader, chuyển tiếp `updateFile`/`file`/`error` tag `rowArt*`, `peek` trong snapshot, khóa dựng lại + theme, clear khi đăng xuất/đổi tài khoản/onDestroy |
| `app/src/shareable/res/drawable/ic_settings.xml`, `ic_tune.xml` (mới) | vector 24dp |
| `app/build.gradle` | versionCode 111 → 112, versionName theo quy ước hiện có (ghi rõ trong báo cáo) |

Ước lượng ~900–1.300 dòng production + ~500 dòng test. Commit theo giai đoạn để rollback từng phần.

## 5. Trình tự cho Sonnet

- **S0** Kiểm Git (mục 1), đọc kế hoạch, `git diff --check`. Không merge gì thêm.
- **S1** `MeloThemePolicy` + `TEAL_PALETTE` + `palette(profile, theme)` + mục Kiểu màu/công tắc ở UiLab + khóa dựng lại. Test. *Commit 1.* (CURRENT không đổi gì.)
- **S2** `MeloRowArtPolicy` + test JSON mẫu (audio có/không cover, document thumbnail, minithumbnail, format lạ, file đã tải/chưa). *Commit 2.*
- **S3** `MeloRowArtLoader` + nối vào `TelegramPreviewActivity` (chuyển tiếp event, clear, peek) + `MeloLibraryPane` gọi theo dòng hiện + cập nhật theo tag. Log tag `MeloRowArt` (chỉ đếm: hit/miss/req/inflight, **không** log tên bài/chat). *Commit 3.*
- **S4** Dòng bài theme + 4 ô menu + dòng đồng bộ + ô tìm + bộ lọc + A–Z. Vector mới. *Commit 4.*
- **S5** Khung trái Hai khung + đầu trang `MeloPhoneScreen`. *Commit 5.*
- **S6** (tuỳ chọn) nền mờ từ ảnh bìa; chỉ giữ nếu QA không giật. *Commit 6 hoặc bỏ.*
- **S7** Bump vc112, chạy test/build Ni, bundle → Tiny build có Telegram config, QA, báo cáo `docs/plans/E98-THEME-ANH-BIA-bao-cao.md`. Commit, DỪNG.

## 6. Unit tests

- `MeloThemePolicyTest`: fromKey/round-trip, khóa lạ → CURRENT, `appliesTo` (TWO_PANE/PHONE/TABLET = có; CAR/COMPACT/LEGACY = không), công tắc ảnh mặc định true, `syncDot`.
- `MeloDesignTokensTest` (thêm): CURRENT/CAR không đổi; TEAL contrast text & textSecondary ≥ 4.5 trên surface/surfaceRaised, onAccent ≥ 3 trên accent và accentEnd; CAR/COMPACT + TEAL vẫn trả palette cũ.
- `MeloRowArtPolicyTest`: pickThumb các dạng JSON; artKey có accountId; decodeSample theo kích thước (2000×2000→192 ⇒ 8); hàng đợi LIFO giới hạn 3, bỏ khóa ngoài màn; không tải khi FLING; generation cũ bị bỏ.
- `MeloComponentPolicyTest`: `shortCount` (0, 999, 1.000, 9.540, 27.971, 1.250.000, âm/null).
- `MeloLibraryPolicyTest`: `themeTabs` nhãn đầy đủ/rút gọn, chọn đúng ô theo mode/showingSources.
- `MeloAlphabetPolicyTest`: hiện khi theme ≥ 50 dòng bất kể sort; `needsSortSwitch`.
- `MeloNowPlayingPolicyTest`: nhãn Đảo/Lặp/Lưu theo theme.
- Toàn bộ test cũ phải xanh; ghi số tests/skipped/failed mỗi biến thể.

Lệnh Ni (theo CLAUDE.md §4):
```
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
git diff --check
```

## 7. QA thiết bị (Sonnet tự làm qua ADB; tuân §3, §6, §7 CLAUDE.md và `docs/workflow/SCREEN_LIFECYCLE_QA.md`)

APK thử ảnh bìa Telegram **phải build ở Tiny có Telegram config** (APK Ni ID 0 không dùng được). Verify package `app.melovault.player`, vc112 > vc đang cài, cert khớp, config=true; MediaSession không đang phát trước khi `install -r`. Màn hình ON chỉ khi thao tác, OFF trong `finally`, ghi `SONY_SCREEN_END` / `SHANLING_SCREEN_END`.

**Sony Xperia 5 II (Tiny, `QV725WEQ54`) — chính cho theme:**
1. Cài đè vc111→vc112 (không gỡ/xóa dữ liệu). Mở app: giao diện **giống hệt trước** (CURRENT) — ảnh so sánh.
2. ⚙ → Cài đặt giao diện → Kiểu màu = **Teal sáng · ảnh bìa** → quay lại. Dọc: logo hai màu, dòng đồng bộ có chấm, 4 ô menu, ô tìm có nút lọc, dòng bài có ảnh bìa (ảnh thật/ảnh kênh/chữ cái); chụp trước & sau 5 s chờ ảnh về.
3. Ngang (Hai khung): khung trái như mẫu (logo, ảnh lớn, viên chất lượng, thanh tiến trình, 3 nút, 2×2); không có cột điều hướng. Bấm bài → phát; dòng đang phát gradient + sóng nhạc.
4. Ô "Nguồn Telegram" mở lưới nguồn; "Đã tải về máy" lọc đúng; nút lọc → Thể loại Chillout → danh sách lọc + chip "Chillout ✕"; Sắp xếp Tên A–Z; A–Z bấm "M" nhảy đúng.
5. Cuộn nhanh 27K bài 10 giây: không ANR/crash; `dumpsys gfxinfo app.melovault.player` (janky % trước/sau theme); `dumpsys meminfo` (Java heap); logcat `MeloRowArt` đếm req ≤ 3 inflight.
6. Đổi lại Kiểu màu = Hiện tại → giao diện cũ; tắt công tắc ảnh → không còn req `MeloRowArt`.

**Shanling M3 Ultra (Ni, `30313860180939`) — hồi quy:** mặc định là **Gọn** ⇒ xác nhận Gọn **không đổi** khi Kiểu màu = Teal sáng (theme không áp cho Gọn). Nếu an toàn, tạm đặt Gọn = Tắt trong UiLab để thấy Điện thoại dọc + ảnh bìa trên Android 10 (`loadThumbnail` cho bài trên máy), rồi **trả Gọn về Tự động**. Không ép theme vào Gọn.

Ảnh: `docs/plans/artifacts/E98-THEME-ANH-BIA/NN_<thiết bị>_<mô tả>_<HHMMSS>.png`; ảnh có tên bài/kênh **không commit**. Media3 PLAYING ≠ đã nghe thấy tiếng: ghi **CHƯA XÁC NHẬN ÂM THANH THỰC**.

### Bác sĩ tự kiểm tra (Y700 hoặc tablet ngang, sau khi cài APK candidate)
1. Mở MeloVault → ⚙ (góc trên) → **Cài đặt giao diện** → **Kiểu màu** chọn **"Teal sáng · ảnh bìa"** → quay lại: phải thấy giao diện sáng hơn, 4 ô menu có biểu tượng, cột điều hướng trái biến mất.
2. Ở **Tất cả**, chờ ~5 giây, cuộn chậm: hầu hết bài có **ảnh bìa nhỏ** (ảnh bài hoặc ảnh kênh); bài không có ảnh hiện ô chữ cái — không có ô trống/xám.
3. Bấm một bài: khung trái hiện ảnh lớn, tên, kênh, viên **MP3 • … kbps**, thanh tiến trình chạy, nút ⏸; dòng đang phát sáng màu teal có sóng nhạc.
4. Bấm **nút lọc** (biểu tượng thanh trượt cạnh ô tìm) → chọn **Chillout**: chỉ còn bài Chillout và có chip "Chillout ✕"; bấm ✕ để bỏ lọc.
5. Bấm **Nguồn Telegram** → thấy danh sách kênh; bấm **Đã tải về máy** → chỉ bài đã tải (dấu ✓ xanh).
6. Muốn quay lại như cũ: Cài đặt giao diện → Kiểu màu = **Hiện tại**.

## 8. PASS / FAIL, rủi ro, rollback

**PASS:** test Debug/Release xanh (không giảm số test), build OK; CURRENT không đổi (ảnh so sánh); theme hiện đủ các phần mục 3.3–3.5; ≥ 80% dòng hiện ảnh thật/ảnh kênh sau 5 s trên Sony với tài khoản thật; không ANR/crash khi cuộn 27K; janky frames không tăng > 5 điểm % so với CURRENT; Gọn/Car không đổi; không log PII; chủ dự án duyệt ảnh từ xa.
**FAIL:** CURRENT bị đổi; crash/ANR; ảnh hiện sai dòng (ảnh bài A trên dòng B); request TDLib không giới hạn; đổi DB/schema/Media3/sync.

| Rủi ro | Giảm thiểu |
|---|---|
| Nhiều `getMessage`/`downloadFile` làm chậm TDLib hoặc tốn mạng | Chỉ dòng hiện + 4, ≤ 3 inflight, dừng khi fling, negative cache, ảnh ~5–30 KB; công tắc tắt ảnh |
| Ảnh sai dòng do ListView dựng lại | Gắn tag `artKey` vào ImageView, chỉ gán khi tag khớp; generation |
| Đổi tài khoản lộ ảnh tài khoản cũ | Khóa có accountId, clear + generation khi đổi/đăng xuất |
| OOM | Cache theo byte ≤ 12 MB, decode ≤ 192px, bounds trước |
| Phản hồi `rowArt*` lọt vào handler khác (`message`, `chat`, `file`) | Kiểm tra `onNativeEvent`: xử lý tag `rowArt*` **đầu tiên** và `return` |
| TDLib tự tải thêm file ảnh vào thư mục TDLib | Chỉ thumbnail nhỏ; không xoá/hash cache; ghi rõ trong báo cáo |
| Thanh tiến trình bị hiểu là tua được | Chỉ hiển thị, không nhận chạm (giữ E95 F09) |
| Nhãn dài trên màn hẹp | `themeTabs` rút gọn < 520dp; test chữ ×1,3 trên Sony |

**Rollback:** người dùng: Kiểu màu = Hiện tại hoặc tắt công tắc ảnh. Mã: revert từng commit S6→S1 (độc lập theo giai đoạn); không có di trú dữ liệu.

## 9. Chưa kiểm chứng / cần xác nhận
- Tỷ lệ bài Telegram có `album_cover_thumbnail` thực tế (nhiều bài kênh có thể không có ⇒ ảnh kênh) — đo ở QA.
- `getMessage` có trả từ DB cục bộ TDLib hay phải lên mạng (phụ thuộc `use_message_database`) — Sonnet ghi lại thời gian trung bình.
- Y700/S25/FYT chỉ chủ dự án thử; Sony ngang chỉ cao ~360dp nên khung trái có thể bị thu gọn (dùng art-fit E95).
- Tên versionName vc112 do Sonnet đọc quy ước hiện có; ChatGPT xác nhận khi nghiệm thu.

## 10. Việc Opus làm sau khi được duyệt
1. `git switch -c dot/E98-THEME-ANH-BIA 826d166` (cây đang sạch; không đụng worktree khác).
2. Ghi `docs/plans/E98-THEME-ANH-BIA.md` (nội dung kế hoạch này), `git diff --check`, commit "docs(plan): E98-THEME-ANH-BIA teal theme with row cover art".
3. Báo ngắn 3 phần + dòng **"Giờ mở phiên mới, chọn Sonnet."**
