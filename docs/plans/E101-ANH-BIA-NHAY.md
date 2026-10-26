# Kế hoạch — E101-ANH-BIA-NHAY (ảnh bìa dòng nhảy/đổi liên tục khi lướt danh sách dài)

## 0. Bối cảnh và mục tiêu

Chủ dự án báo 26/10: ở danh sách bài nhiều (Tất cả · 29.840 bài), khi lướt thì ảnh bìa "hay bị chồng ảnh". Hỏi lại trong phiên Opus này, chủ dự án chọn: **"Ảnh nhảy, đổi liên tục"**, gặp trên **Shanling M3 Ultra, Sony Xperia 5 II, Y700 và màn xe (FYT)**.

Opus đã tái hiện trên Shanling (vc116, Gọn, 26/10 09:19): quay 8 s lúc vuốt nhanh 3 lần rồi kéo chậm. Khung hình cho thấy: khi đang vuốt nhanh các dòng chỉ có ô chữ cái; khi danh sách chậm lại thì ảnh hiện **lần lượt, không theo thứ tự trên → dưới**, và một số dòng đổi **chữ cái → ảnh mờ → ảnh nét** (2–3 lần đổi/dòng). Logcat `MeloRowArt` cùng lúc: `visible=9 withArt=8` rồi `withArt=2` rồi `withArt=9` trong vòng 3 s. Chưa bắt được dòng tự đổi qua lại giữa hai ảnh khác nhau (nguyên nhân C chỉ suy từ mã).

**Mục tiêu** (Gọn, Điện thoại dọc Teal, Tablet dọc/Hai khung Teal — những màn dùng `MeloRowArtLoader`):
1. Mỗi dòng **đổi hình tối đa 1 lần** trong trường hợp thường: ô chữ cái → ảnh thật (ảnh mờ chỉ hiện khi ảnh thật đến chậm > 400 ms).
2. Ảnh **không bao giờ đổi qua lại** giữa hai nguồn trên cùng một dòng.
3. Ảnh xuất hiện **từ trên xuống** trong vùng đang nhìn, và hiện ra **mờ dần mượt (150 ms)** thay vì bật giật.
4. Không chậm hơn: không thêm yêu cầu TDLib, không tăng bộ nhớ đệm, giữ "không tải khi đang vuốt nhanh".

Không đổi: Car Mode (`MeloCarScreen` dùng `artView` không có khóa ảnh), giao diện cũ (Cockpit/Portrait, `bindSongThumbnail`), thẻ Đang phát/thanh đáy/Nghe tiếp, bộ đệm `thumbnailCache` cũ, công tắc "Tải ảnh bìa từ Telegram".

## 1. Git baseline (đọc 26/10/2026)

| Mục | Giá trị |
|---|---|
| Nền | `feature/e93-03d-y700-offline-cockpit` @ **`6ae510c`** (docs handoff sau merge E100 `8ca1457`), cây sạch, `git diff --check` sạch |
| App | vc116 / `0.6.91-beta` (`app/build.gradle:93-94`); Shanling đang cài vc116 (kiểm 26/10 09:18); Sony vc116 theo HANDOFF |
| Test nền | 2055 test mỗi biến thể, 21 bỏ qua, 0 lỗi (Opus `--rerun-tasks` 26/10 trên cây E100) |
| Nhánh | `dot/E101-ANH-BIA-NHAY` cắt từ `6ae510c`; commit kế hoạch: `<PLAN_SHA>` (xem `git log -1` của nhánh) |

## 2. Nguyên nhân gốc (đã đối chiếu mã)

Tất cả trong `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/`.

**A. Ảnh mờ rồi ảnh nét — kể cả khi ảnh nét đã có sẵn trên máy.** `MeloRowArtLoader.resolveThumb` (275-301) luôn giải mã minithumbnail ~40px trước (`decodeBytes(..., final = false)` ở 279) khi cache trống, **kể cả khi `thumb.localPath != null`** (file thumbnail TDLib đã tải xong). Hai lần giải mã chạy nối tiếp trên cùng một luồng nền ⇒ dòng đổi chữ cái → mờ → nét trong vài chục–vài trăm ms. Khi phải tải file (`fileId`), ảnh mờ đứng đó tới khi mạng xong rồi mới đổi.

**B. Thứ tự gửi yêu cầu ngược.** `requestVisibleArt` (Compact 209-217, LibraryPane 582-590) gửi các dòng theo thứ tự trên → dưới (+4 dòng phía dưới). `MeloRowArtPolicy.Scheduler` (157-198) là **mới nhất trước** (`removeLastOrNull`) ⇒ dòng **cuối** (dòng chuẩn bị, chưa thấy) được gửi trước, dòng trên cùng gửi sau cùng ⇒ ảnh hiện lộn xộn, từ dưới lên.

**C. Hai nguồn ảnh giành nhau trên cùng một dòng.** Khi dựng dòng, `getView` dùng `row.art ?: host.peekRowArt(row.artKey)` (Compact 275, LibraryPane 571) — `row.art` = `thumbnailCache.get(song.messageId)` (Activity 5189, bộ đệm cũ 60 ô của giao diện cũ, thẻ mini/cockpit, ảnh nhúng trong file/Media3 `artworkData`). Nhưng `refreshVisibleArt` (Compact 220-234, LibraryPane 593-607) đặt **`peekRowArt(key)`** đè lên mỗi lần có ảnh mới (≤ 120 ms/lần, `MeloRowArtLoader.scheduleNotify`). Mỗi lần danh sách dựng lại (`onLibraryChanged` → `notifyDataSetChanged`; có ~31 nơi gọi `scheduleMeloLibraryRefresh`, kể cả khi tải thêm trang lúc cuộn, đổi tab, cập nhật đồng bộ) dòng quay về `row.art`. Khi hai ảnh khác nhau (ví dụ bài đang phát: ảnh nhúng của file vs ảnh bìa Telegram) ⇒ dòng **đổi qua lại liên tục**.

**D. Ảnh bật ra đột ngột.** `MeloComponents.applyRowArt` (210-221) đặt ảnh rồi `tile.visibility = GONE` ngay, không có chuyển tiếp ⇒ cả màn nháy theo từng đợt 120 ms.

Không phải nguyên nhân (đã kiểm): ListView của Gọn/LibraryPane **không tái sử dụng** `convertView` (dựng view mới mỗi `getView`) nên không có ảnh cũ dính sang dòng mới; khóa ảnh `tg:<account>:<chat>:<rawMessageId>` đủ duy nhất; "không tải khi vuốt nhanh" (`shouldLoad`) là chủ ý, giữ nguyên.

## 3. Phương án

### 3.1 So sánh

| Phương án | Ưu | Nhược | Chọn |
|---|---|---|---|
| A. Sửa tại chỗ 4 nguyên nhân trong loader/policy/2 màn + chuyển tiếp mờ dần ở `applyRowArt` | Nhỏ (~100 dòng), không thêm yêu cầu TDLib/bộ nhớ, giữ kiến trúc E98/E99, test JVM được phần quy tắc | Ảnh mờ trễ 400 ms cần 1 bộ hẹn giờ nhỏ trong loader | **Chọn** |
| B. Chuyển danh sách sang RecyclerView + Coil cho ảnh dòng | Thư viện xử lý sẵn | Viết lại 2 màn danh sách, rủi ro hồi quy lớn (click/long-press/⋮, cuộn, 8 dòng Gọn), Coil không đọc được file TDLib theo `file_id` | Không |
| C. Bỏ hẳn ảnh mờ, chỉ hiện ảnh nét | Đơn giản nhất | Bài có ảnh nhưng mạng chậm thì chỉ thấy chữ cái lâu; bài chỉ có minithumbnail vẫn cần ảnh mờ | Không (giữ ảnh mờ nhưng hoãn 400 ms) |
| D. Tải ảnh cả khi vuốt nhanh / tăng PREFETCH | Ít ô chữ cái hơn | Thêm tải TDLib lúc vuốt (đã cố ý chặn từ E98), không giải quyết "đổi liên tục" | Không |

### 3.2 Thiết kế chi tiết

**Chính sách thuần JVM — `MeloRowArtPolicy.kt` (thêm, không đổi hàm/test cũ):**
```kotlin
/** A blur waits this long for the real cover before it is shown (E101). */
const val BLUR_DELAY_MS = 400L
/** The letter tile fades out over this long when a cover first appears (E101). */
const val FADE_MS = 150L

enum class Blur { NONE, NOW, LATER }
/** NONE: no minithumb, or the real cover is already on disk. NOW: the blur is all there is. LATER: show it only if the download is slow. */
fun blurPlan(hasMini: Boolean, sharpOnDisk: Boolean, sharpToDownload: Boolean): Blur

/** One picture source per row: the loader's cover wins, the old cache only fills in when the loader has none. */
fun <T : Any> rowPicture(loaded: T?, legacy: T?): T? = loaded ?: legacy

enum class ArtChange { NONE, APPEAR, SWAP }
/** What putting a picture into a row does: nothing, first picture over the letter tile, or one picture replacing another. */
fun artChange(showing: Boolean, sameAsShown: Boolean, incoming: Boolean): ArtChange
// !incoming → NONE; !showing → APPEAR; sameAsShown → NONE; else SWAP

/** Requests in the order the scheduler should send them: the scheduler sends the newest first, so the top row goes in last. */
fun <T> sendOrder(rowsTopDown: List<T>): List<T> = rowsTopDown.asReversed()
```
`blurPlan`: `!hasMini` → NONE; `sharpOnDisk` → NONE; `sharpToDownload` → LATER; còn lại → NOW.

**Loader — `MeloRowArtLoader.kt`:**
1. `resolveThumb` (275): thay dòng 279 bằng `when (MeloRowArtPolicy.blurPlan(mini != null, path != null, fileId != null))`: `NOW` → `decodeBytes(key, mini, final = true)` (như cũ khi chỉ có ảnh mờ); `LATER` → `scheduleBlur(key, mini)`; `NONE` → không làm gì. Phần `when` tải/giải mã file phía dưới giữ nguyên. Chỉ áp dụng khi `cache.get(key) == null` như cũ.
2. Thêm `private val blurTimers = HashMap<String, Runnable>()` và:
   ```kotlin
   /** Shows the blur only if the real cover has not arrived within [MeloRowArtPolicy.BLUR_DELAY_MS]. */
   private fun scheduleBlur(key: String, mini: ByteArray) {
       if (key in blurTimers) return
       val g = generation
       val r = Runnable {
           blurTimers.remove(key)
           if (MeloRowArtPolicy.isCurrent(g, generation) && alive() && cache.get(key) == null) decodeBytes(key, mini, final = false)
       }
       blurTimers[key] = r
       main.postDelayed(r, MeloRowArtPolicy.BLUR_DELAY_MS)
   }
   ```
   Hủy: trong `deliver` khi `final && bmp != null` → `blurTimers.remove(key)?.let(main::removeCallbacks)`; trong `fail(key)` cũng hủy (ảnh nét lỗi thì **vẫn cho** ảnh mờ: ở `fail`, nếu có timer đang chờ thì **chạy ngay** thay vì hủy — xem STOP nếu khó); trong `reset()` → hủy tất cả + `clear()`.
   [Đơn giản hóa cho Sonnet] Nếu xử lý "lỗi thì vẫn hiện ảnh mờ" làm phức tạp `fail()`, chấp nhận: lỗi ⇒ dòng giữ chữ cái (ghi vào báo cáo). Không được để ảnh mờ đè lên ảnh nét (giữ kiểm tra `deliver` 422).
3. `request` (87-108): các nhánh `decodeEmbedded`/`decodeDeviceThumb` giữ thứ tự trên → dưới (executor FIFO); các `Job(Kind.MESSAGE…)` gom vào một list rồi `MeloRowArtPolicy.sendOrder(list).forEach(::enqueue)` trước `scheduler.retain(keep)` ⇒ dòng trên cùng được gửi trước. Không đổi `Scheduler`, `MAX_IN_FLIGHT`, `PREFETCH_AHEAD`.

**View — `MeloComponents.kt`:**
- `applyRowArt(frame: FrameLayout, bmp: Bitmap?, animate: Boolean = false): MeloRowArtPolicy.ArtChange`:
  - `val showing = image.visibility == View.VISIBLE`; `change = artChange(showing, image.tag === bmp, bmp != null)`.
  - `NONE` → không làm gì (giữ "null không lấy ảnh đi").
  - `APPEAR` → `setImageBitmap`, `image.tag = bmp`, image `VISIBLE`; nếu `animate`: `tile.animate().alpha(0f).setDuration(FADE_MS).withEndAction { tile.visibility = View.GONE; tile.alpha = 1f }`; không thì `tile.visibility = GONE` ngay (như cũ).
  - `SWAP` → `setImageBitmap` + `tag` (không hiệu ứng).
- Thêm `fun rowArtShowing(frame: FrameLayout): Boolean` (= ảnh đang VISIBLE) để hai màn đếm `withArt` như trước.
- `rowArtView` (202): `applyRowArt(frame, art)` giữ nguyên (không hiệu ứng khi dựng dòng; bỏ qua giá trị trả về).

**Hai màn danh sách — `MeloCompactScreen.kt`, `MeloLibraryPane.kt`:**
- `getView`/`songRow`: `art = if (rowArt /* Pane: c.teal */) MeloRowArtPolicy.rowPicture(host.peekRowArt(row.artKey), row.art) else row.art` (Compact 275, Pane 571).
- `refreshVisibleArt`: `val change = c.applyRowArt(frame, host.peekRowArt(key), animate = true)`; `if (c.rowArtShowing(frame)) withArt++`; đếm `appeared`/`swapped`. Log (chỉ số đếm, không tên): `"compact visible=$shown withArt=$withArt settled=$settled appeared=$appeared swapped=$swapped"` (Pane bỏ tiền tố `compact`).

### 3.3 Không làm / bảo vệ
- Không đụng `ShareablePlaybackService`, DB/schema, TDLib handler, `TelegramPreviewActivity.kt` (kể cả `thumbnailCache`/`bindSongThumbnail`), `MeloCarScreen`, `MeloBottomBar`, `MeloScreenHost`, signer/package, công tắc cài đặt.
- Không tăng `CACHE_CAP_BYTES`, `MAX_IN_FLIGHT`, `PREFETCH_AHEAD`; không tải khi đang vuốt nhanh.
- Không log tên bài/kênh/khóa; log chỉ số đếm.

## 4. File sẽ đổi (ước lượng ~90–120 dòng production + ~80 dòng test)

| File | Thay đổi |
|---|---|
| `MeloRowArtPolicy.kt` | `BLUR_DELAY_MS`, `FADE_MS`, `Blur`/`blurPlan`, `rowPicture`, `ArtChange`/`artChange`, `sendOrder` |
| `MeloRowArtLoader.kt` | `resolveThumb` theo `blurPlan`, `scheduleBlur` + `blurTimers` (hủy ở deliver/fail/reset), `request` gửi theo `sendOrder` |
| `MeloComponents.kt` | `applyRowArt(..., animate)` trả `ArtChange` + mờ dần ô chữ cái, `rowArtShowing` |
| `MeloCompactScreen.kt`, `MeloLibraryPane.kt` | `rowPicture` trong `getView`; `refreshVisibleArt` dùng `animate = true` + đếm appeared/swapped trong log |
| `app/build.gradle` | vc116 → **vc117**, versionName `0.6.91` → **`0.6.92`** (hậu tố `-beta` có sẵn) |
| test: `MeloRowArtPolicyTest.kt` | mục 6 |

Không phải làn nhanh (5 file production, đụng bộ tải ảnh dùng chung E98/E99).

## 5. Trình tự cho Sonnet — **GỘP 1 phiên** (chia giai đoạn commit)

- **S0** Kiểm Git như mục 1 (nhánh `dot/E101-ANH-BIA-NHAY`, HEAD = commit kế hoạch, cây sạch, không agent/Gradle khác). Ghi `git log -1` vào báo cáo.
- **S1 (đo nền, trước khi sửa, vc116 đang cài):** QA bước 1 mục 7 trên Shanling (quay video + logcat với vc116). Đây là ảnh/video "trước". Màn tắt sau khi xong.
- **S2** `MeloRowArtPolicy.kt` + test. Chạy test hai biến thể. *Commit 1* `E101 S2: row cover rules for delayed blur, single source, top-first order`.
- **S3** `MeloRowArtLoader.kt` (blurPlan, scheduleBlur, sendOrder). Build Debug + test. *Commit 2* `E101 S3: row cover loader skips needless blur and sends top row first`.
- **S4** `MeloComponents.kt` + `MeloCompactScreen.kt` + `MeloLibraryPane.kt`. Build Debug. *Commit 3* `E101 S4: one picture source per row, fade in first cover, count swaps`.
- **S5** Bump vc117 / 0.6.92. Chạy đủ lệnh mục 6 (log ra file, chỉ đọc dòng tổng kết + XML). *Commit 4* `E101 S5: bump shareable beta to vc117 (0.6.92-beta)`.
- **S6** Git bundle (SHA-256) → clone cô lập trên Tiny → build APK có Telegram config (chỉ ghi true/false) → verify package/vc/cert (aapt2/apksigner) → mang APK về Ni, kiểm SHA-256.
- **S7** QA Shanling bước 2–5 (bắt buộc) + Sony bước 6 (nếu Tiny ADB sẵn sàng). Màn hình tắt trong `finally`.
- **S8** `00_tong_hop.png`, báo cáo `docs/plans/E101-ANH-BIA-NHAY-bao-cao.md` (≤ ~250 dòng, có mục "APK QA cần Opus nghiệm thu bàn giao"). *Commit 5* báo cáo. DỪNG.

## 6. Test và lệnh

Unit test JVM (`MeloRowArtPolicyTest.kt`, giữ toàn bộ test cũ, kể cả `rowsToLoadAreVisiblePlusFour` và các test Scheduler):
- `blurPlan`: (false, *, *) = NONE; (true, true, true) = NONE; (true, true, false) = NONE; (true, false, true) = LATER; (true, false, false) = NOW.
- `rowPicture`: ("a","b")="a"; (null,"b")="b"; (null,null)=null.
- `artChange`: incoming=false → NONE (cả khi showing); showing=false+incoming → APPEAR; showing+same → NONE; showing+khác → SWAP.
- `sendOrder` + `Scheduler` thật: `Scheduler()` nhận `sendOrder(listOf("r0".."r8"))` → 3 lần `next()` trả `r0`, `r1`, `r2` (dòng trên cùng trước).
- `BLUR_DELAY_MS == 400`, `FADE_MS == 150` (hằng số đúng hợp đồng).
- Toàn bộ test cũ xanh; sau E101 ≥ 2055 + test mới, 0 lỗi, bỏ qua vẫn 21.

Lệnh Ni (CLAUDE.md §4):
```
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
git diff --check
```

## 7. QA thiết bị — Sonnet tự làm qua ADB (CLAUDE.md §3, §6, §7; `docs/workflow/SCREEN_LIFECYCLE_QA.md`)

**Gate trước khi cài (Shanling, Ni):** `adb devices -l` thấy `30313860180939` = device; getprop = M3 Ultra / Shanling; app đang cài `app.melovault.player` vc116; cert APK mới khớp `eecd3df7…c3fce8`; vc117 > vc116; Telegram config = true (APK Tiny); `dumpsys media_session` không PLAYING (đang phát → STOP hỏi). Chỉ `adb -s 30313860180939 install -r "<APK_VALIDATED>"`.

**Kịch bản vuốt chuẩn (dùng y hệt cho trước/sau, màn Gọn, tab Thư viện, "Tất cả", đang ở đầu danh sách):** `logcat -c` → `screenrecord --time-limit 12 /sdcard/Download/e101_<nhãn>.mp4` chạy nền → 3 lần `input swipe 380 900 380 250 120` cách 0,4 s → chờ 3 s → 3 lần `input swipe 380 850 380 450 600` cách 1 s → chờ 3 s. Kéo video về `docs/plans/artifacts/E101-ANH-BIA-NHAY/`, **xóa file tạm trên máy**, tách khung `ffmpeg -vf fps=10,scale=256:-1,tile=8x4` thành ảnh ghép; `logcat -d -s MeloRowArt` lưu file. Về đầu danh sách trước mỗi lần (vuốt xuống hoặc chạm tiêu đề — không đổi cài đặt).

| # | Thiết bị | Màn | Hành động | Kỳ vọng (PASS) | Ảnh/video |
|---|---|---|---|---|---|
| 1 | Shanling vc116 (S1, trước khi sửa) | ON | Kịch bản vuốt chuẩn | Chỉ để so sánh: ghi số dòng thấy đổi ≥ 2 lần trong ảnh ghép | 01_truoc.mp4 + 01_truoc_sheet.png |
| 2 | Shanling vc117 | ON | Mở app → Gọn, Thư viện, Tất cả, đầu danh sách, chờ 5 s, chụp | 8 dòng như cũ, dòng đầu có ảnh, không crash | 02 |
| 3 | Shanling vc117 | ON | Kịch bản vuốt chuẩn | Ảnh ghép: **không dòng nào có 2 ảnh thật khác nhau**; sau khi dừng tay ảnh điền **từ trên xuống**; logcat: tổng `swapped` ≤ 10% tổng `appeared` (chỉ ảnh mờ → nét khi tải chậm), không `FATAL`/ANR | 03.mp4 + 03_sheet.png + 03_log.txt |
| 4 | Shanling vc117 | ON | Vuốt nhanh về đầu danh sách, chờ 2 s, chụp; so với ảnh bước 2 | Các dòng đã có ảnh hiện ngay (từ bộ đệm), cùng ảnh như bước 2 (so cắt ô ảnh 8 dòng) | 04 |
| 5 | Shanling vc117 | ON | Tại đầu danh sách (có dòng bài đang nạp/tạm dừng nếu thấy): bấm tab **Telegram** rồi **Thư viện** 3 lần (mỗi lần chờ 2 s), chụp sau mỗi lần quay về | Ô ảnh mỗi dòng **giống hệt** qua 3 ảnh (danh sách dựng lại không làm đổi ảnh); logcat `swapped=0` trong lúc này | 05a–c |
| 6 | Sony (Tiny, `QV725WEQ54`) vc117 | ON | Điện thoại dọc Teal: kịch bản vuốt chuẩn (tọa độ theo ảnh màn Sony) | Như bước 3 (đường `MeloLibraryPane`) | 06.mp4 + 06_sheet.png |

Bước 6 không chặn PASS nếu Tiny ADB không sẵn sàng (ghi CHƯA KIỂM). **Không phát nhạc** cho E101 (không cần); nếu Shanling đang PLAYING do người dùng → hỏi trước.

**Cleanup bắt buộc (`finally`):** không đổi cài đặt UI; xóa video tạm trên máy (`/sdcard/Download/e101_*.mp4`, chỉ file của mình); `input keyevent 223`; `dumpsys display` Display State=OFF; ghi `SHANLING_SCREEN_END` / `SONY_SCREEN_END`.
Ảnh/video: `docs/plans/artifacts/E101-ANH-BIA-NHAY/NN_<thiết bị>_<mô tả>_<HHMMSS>.<ext>`, ghép `00_tong_hop.png` (đặt cạnh nhau ảnh ghép trước/sau bước 1 và 3 + ảnh 05a–c; cạnh dài ≤ 1600 px). Ảnh có tên bài/kênh **không commit**.
**Không kiểm được:** Y700/S25/FYT (chủ dự án tự xem); cảm nhận "mượt" là chủ quan — chứng cứ chính là đếm `swapped` + ảnh ghép.

### Bác sĩ tự kiểm tra (Shanling M3 Ultra, sau khi có APK candidate vc117 — hoặc duyệt video/ảnh từ xa)
1. Mở MeloVault (Gọn) → Thư viện → Tất cả: thấy 8 bài, có ảnh bìa như trước.
2. Vuốt thật nhanh xuống vài lần rồi thả tay: khi danh sách dừng, ảnh hiện **từ trên xuống**, mỗi dòng hiện **một lần** (ô chữ cái mờ dần thành ảnh), **không nhảy qua lại**.
3. Kéo chậm từng đoạn: dòng mới vào màn hình có ảnh, không đổi sang ảnh khác.
4. Vuốt về đầu danh sách: các ảnh đã thấy hiện ngay, đúng ảnh cũ.
5. Bấm tab Telegram rồi Thư viện vài lần: ảnh các dòng **đứng yên**, không đổi.

## 8. PASS / FAIL

**PASS (tất cả):**
- Test Debug + Release 0 lỗi, tổng ≥ 2055 + test mới, bỏ qua 21; build Debug/Release OK; `git diff --check` sạch; chỉ đổi đúng file mục 4.
- Shanling bước 2–5 PASS: không dòng nào có 2 ảnh thật khác nhau; tổng `swapped` ≤ 10% `appeared` ở bước 3; bước 5 ô ảnh giống hệt qua 3 lần dựng lại và `swapped=0`; Gọn vẫn 8 dòng; không crash/ANR.
- Ảnh ghép sau (bước 3) có ít lần đổi hình mỗi dòng hơn rõ rệt so với trước (bước 1).
- Chủ dự án xem video/ảnh hoặc cài vc117 và nói **ĐẠT**.

**FAIL (bất kỳ):** còn dòng đổi qua lại giữa hai ảnh; ảnh mờ đè lên ảnh nét; dòng hiện ảnh của bài khác; ảnh không hiện nữa (mất ảnh so với vc116) hoặc chậm hẳn; tải ảnh khi đang vuốt nhanh; Gọn < 8 dòng; crash/ANR; Car Mode/giao diện cũ đổi; sửa file ngoài mục 4 (trừ test/báo cáo).

## 9. STOP — Sonnet dừng và báo, không tự xoay xở

- Cây bẩn không rõ nguồn, HEAD/nhánh lệch, agent khác đang sửa/build.
- Cần sửa `TelegramPreviewActivity.kt`, service, DB/schema, TDLib handler, `MeloScreenHost` hoặc đổi `Scheduler`/hằng số giới hạn để làm được ⇒ STOP hỏi.
- Bước 1 (đo nền) không tái hiện được ảnh đổi nhiều lần ⇒ vẫn làm tiếp, ghi rõ; nhưng nếu bước 3 vẫn thấy dòng đổi qua lại hai ảnh khác nhau ⇒ STOP, báo kèm video (không tự mở rộng phạm vi).
- Hiệu ứng mờ dần gây ô chữ cái đè lên ảnh khi danh sách dựng lại giữa chừng ⇒ STOP báo (phương án dự phòng: bỏ hiệu ứng, giữ phần còn lại — chỉ khi Opus nghiệm thu đồng ý).
- ADB không thấy Shanling / unauthorized / getprop sai ⇒ BLOCKED phần QA (vẫn xong code + test; không dùng Sony thay).
- Signature mismatch, downgrade, sai package, APK thiếu Telegram config, `install -r` lỗi ⇒ STOP; cấm `uninstall`, `pm clear`, `-d`, xóa dữ liệu/cache/TDLib, logout, đổi tài khoản.
- Shanling đang PLAYING do người dùng ⇒ hỏi trước khi cài.
- Mở app không ra Gọn hoặc chưa đăng nhập ⇒ không tự đăng nhập/đổi chế độ; BLOCKED bước đó.
- Không có đường Git bundle tới Tiny ⇒ STOP hỏi.

## 10. Rủi ro và rollback

| Rủi ro | Giảm thiểu |
|---|---|
| Ảnh mờ hoãn 400 ms ⇒ bài chỉ có ảnh nét tải chậm sẽ thấy chữ cái lâu hơn 0,4 s | Chấp nhận; đổi lại không nhảy. Hằng số trong policy, chỉnh được |
| `thumb.localPath` trỏ file đã bị TDLib dọn ⇒ không còn ảnh mờ dự phòng, dòng giữ chữ cái | Như hành vi "không có ảnh"; lần sau TDLib báo path null ⇒ đi nhánh tải |
| Timer ảnh mờ chạy sau `reset()` (đổi tài khoản/tắt ảnh) | Kiểm `generation` + `alive()` trong runnable; `reset()` hủy hết timer |
| Ưu tiên ảnh loader làm bài có ảnh nhúng (file đã tải) đổi sang ảnh khác so với vc116 | Loader vốn ưu tiên ảnh nhúng của file đã tải (`decodeEmbedded`) ⇒ cùng nguồn; chỉ khác khi loader chưa có thì dùng `row.art` |
| Hiệu ứng mờ dần trên nhiều dòng cùng lúc làm giật | 150 ms, chỉ đổi alpha một `TextView`; danh sách dựng lại tạo view mới nên không kẹt trạng thái |
| Log thêm 2 số | Chỉ số đếm, cùng tag `MeloRowArt` |

**Rollback:** revert Commit 3 (view/màn) rồi Commit 2 (loader); Commit 1 chỉ thêm hàm thuần. vc117 giữ để không downgrade. Không có dữ liệu/di trú nào.

## 11. Chưa kiểm chứng / cần xác nhận

- Nguyên nhân C chỉ suy từ mã; Opus chưa bắt được trên video. Bước 5 kiểm trực tiếp.
- [CẦN XÁC NHẬN] Ngưỡng `swapped ≤ 10% appeared` là ước lượng; Opus nghiệm thu có thể chấm theo ảnh ghép nếu mạng Shanling chậm làm nhiều ảnh mờ phải hiện.
- [CẦN XÁC NHẬN] versionName `0.6.92` cho vc117.
- Y700, S25, màn xe FYT: chưa kiểm (chủ dự án tự xem). Màn xe Car Mode không dùng loader này; nếu FYT đang ở giao diện Gọn/Teal thì được hưởng sửa đổi.
- Âm thanh: không liên quan.

---

### Prompt Sonnet (GỘP) — điền từ `docs/workflow/PROMPT_SONNET.md` (nhánh `dot/workflow-opus-nghiem-thu`)

Như mẫu, chỉ thay: TASK_ID `E101-ANH-BIA-NHAY`; Nhánh `dot/E101-ANH-BIA-NHAY`; Baseline `6ae510c`, commit kế hoạch `<PLAN_SHA>`; PHIÊN: **GỘP** (S0–S8). Lưu ý riêng:
1. Đọc quy trình mới bằng `git show dot/workflow-opus-nghiem-thu:docs/workflow/PROMPT_SONNET.md` nếu nhánh chưa có; "ChatGPT" = Opus nghiệm thu.
2. S1 quay video "trước" với vc116 đang cài, **trước** khi sửa code.
3. Chỉ sửa 5 file production mục 4 + `app/build.gradle`; không đụng `TelegramPreviewActivity.kt`, `Scheduler`, hằng số giới hạn.
4. Ảnh mờ: không có khi ảnh nét đã có trên máy; hoãn 400 ms khi phải tải; không bao giờ đè ảnh nét.
5. Log `MeloRowArt` thêm `appeared`/`swapped` (chỉ số đếm); bước 3 và 5 chấm bằng số này + ảnh ghép.
6. Không phát nhạc; xóa video tạm trên máy sau khi kéo về; tắt màn trong `finally`.
