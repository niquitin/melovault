# Kế hoạch — E103-THANH-TUA (đưa thanh kéo tua trở lại Studio Deck và Hai khung)

## 0. Bối cảnh và mục tiêu

Ngày 26/10/2026 chủ dự án báo: "tôi thấy bị mất thanh kéo và tua bài hát". Opus đã đối chiếu mã và kết luận đây **không phải lỗi hồi quy**. Thanh tua bị gỡ có chủ ý ở E95 (F09, mục 3.4: "Xóa `MeloWaveformView`… **không thay bằng thanh tua khác**"). Sang E98, khung trái Hai khung (Teal) chỉ thêm thanh **xem tiến trình**, không chạm được (`MeloThumbProgress`). E102 không đụng phần này.

Sau khi nghe giải thích, chủ dự án **chốt** (26/10, trong phiên Opus): *"Có, lập kế hoạch (Opus) để đưa thanh kéo tua trở lại Studio Deck và Hai khung, vẫn giữ nút −30s/+60s."* Quyết định này **thay** quyết định E95 F09/E98 "không có thanh tua ở Đang phát". Tiêu chí REWORK cũ của E95 ("có thanh tua mới ở Đang phát") không còn áp dụng cho E103.

Hiện trạng theo màn:

| Màn | Hiện tại | Sau E103 |
|---|---|---|
| Studio Deck (Đang phát mặc định của Điện thoại/Tablet/FYT/Sony; dọc và ngang) | Dòng giờ + −30s/⏮/⏯/⏭/+60s, **không có thanh** (`MeloStudioDeck.kt:247-271`) | **Thanh kéo tua** trong bến ghim, nằm trên dòng giờ; giữ nguyên 5 nút |
| Hai khung, khung trái, màu thường | Chỉ dòng giờ (`MeloTabletScreen.kt:202`) | Thanh kéo tua + dòng giờ |
| Hai khung, khung trái, Teal | `MeloThumbProgress` chỉ để xem (`MeloTabletScreen.kt:270-279`) | Thanh kéo tua cùng màu/cỡ, hai mốc giờ giữ nguyên |
| Gọn (Shanling), Car Mode, "Màn cũ" | Đã có `SeekBar` | **Không đổi** |

Hai khung hiện không có nút ±. E103 **không thêm** nút ± vào Hai khung; "giữ nút −30s/+60s" là giữ các nút đang có ở Deck.

**Mục tiêu**
1. Trên Deck và Hai khung có thanh kéo được. Kéo thì dòng giờ hiện ngay mốc đích, **thả tay mới tua**. Chạm một điểm trên thanh thì tua tới điểm đó (giống `SeekBar` của Gọn và Car Mode).
2. Phím DPAD/núm xoay hoặc TalkBack chỉnh thanh thì cũng tua (đây là trường hợp `fromUser` nhưng không có chạm).
3. Khi chưa biết thời lượng (0, âm hoặc `C.TIME_UNSET`), thanh bị khóa và ở 0.
4. Nhịp cập nhật 1 giây **không kéo thanh về** lúc người dùng đang kéo.
5. Kéo tới cuối thanh thì đích bị chặn ở `thời lượng − 1 s`, để thả tay ở mép phải không nhảy bài ngay lập tức.

**Không đổi:** Gọn, Car Mode, `NowPlayingFullScreenDialog`, `TelegramPreviewActivity` (cockpit), `MeloScreenHost.seekTo`, service/Media3/MediaSession/hàng đợi, TDLib/DB/cache, manifest, signing, khóa prefs. Không có lựa chọn mới trong Cài đặt.

## 1. Git baseline (đọc 26/10/2026)

| Mục | Giá trị |
|---|---|
| Nền | `feature/e93-03d-y700-offline-cockpit` @ **`da7d33d`** (handoff sau merge E102 `e2ab5ae`), cây sạch |
| App | vc118 / `0.6.93-beta` (`app/build.gradle:93-94`); Shanling và Sony đang cài vc118 (theo HANDOFF) |
| Test nền | 2089 test mỗi biến thể, 21 bỏ qua, 0 lỗi (Opus `--rerun` 26/10, cây E102) |
| Nhánh | `dot/E103-THANH-TUA` cắt từ `da7d33d`; commit kế hoạch: xem `git log -1` của nhánh (commit `docs(plan): E103-THANH-TUA …`) |

## 2. Phương án và lý do

- **Chọn:** một lớp dùng chung `MeloSeekLine : SeekBar` trong `MeloComponents.kt`. Lớp này vẽ đúng kiểu thanh mảnh có chấm tròn đang dùng (track + fill bằng `LayerDrawable`/`ClipDrawable` như `MeloCarScreen.progressTrack`, `MeloCarScreen.kt:308-316`; thumb hình oval). Lớp **thay** `MeloThumbProgress` (chỉ Hai khung dùng, `MeloComponents.kt:883-935`).
  - Lý do: `SeekBar` của Android có sẵn xử lý chạm, kéo, phím DPAD và TalkBack (quan trọng trên FYT có núm xoay). Gọn và Car Mode đã dùng `SeekBar` ổn định. Gom vào một lớp thì Deck và Hai khung chỉ thêm vài dòng, các quy tắc (khóa khi chưa có thời lượng, không giật lúc kéo, chặn mép cuối) chỉ viết một lần.
  - Phép tính tách ra hàm thuần trong `MeloNowPlayingPolicy` để unit test được.
- **Không chọn:** (a) cho `MeloThumbProgress` nhận chạm: phải tự viết kéo, phím và trợ năng, nhiều rủi ro hơn; (b) chép listener `SeekBar` của Gọn vào 2 màn: lặp mã, lại không xử lý được phím và mép cuối; (c) sửa luôn SeekBar của Gọn/Car cho dùng lớp mới: ngoài phạm vi, Gọn là màn chính trên Shanling, để việc khác.
- **Vị trí trên Deck:** trong **bến ghim** (ngoài `ScrollView`), thứ tự [thanh tua][dòng giờ][hàng 5 nút]. Như vậy thanh luôn trên màn khi cuộn hàng đợi (giữ F01 của E95). Bến cao thêm khoảng 48 dp. Art-fit tự tính lại vì `ScrollView` đổi chiều cao (listener `MeloStudioDeck.kt:197-199`, `:237-239`).
- **Vị trí trên Hai khung:** đúng chỗ dòng giờ/thanh xem hiện tại trong cột cuộn; gom [thanh][giờ] thành `timeBlock` cho **cả hai** kiểu màu, để `applyTight` chỉ cần dùng `timeBlock`.

## 3. Thiết kế chi tiết

### 3.1 `MeloNowPlayingPolicy` (hàm thuần, thêm cạnh `seekTarget`, `MeloNowPlayingPolicy.kt:66`)
```kotlin
const val SEEK_BAR_MAX = 1000
/** Thả tay ở mép phải không nhảy bài ngay. */
const val SEEK_BAR_END_GUARD_MS = 1_000L
fun seekBarEnabled(durationMs: Long): Boolean = durationMs > 0L
/** 0..SEEK_BAR_MAX; thời lượng chưa biết ⇒ 0; vị trí âm ⇒ 0; vượt thời lượng ⇒ MAX. */
fun seekBarProgress(positionMs: Long, durationMs: Long): Int
/** null khi chưa biết thời lượng; progress ngoài 0..MAX bị chặn; kết quả trong [0, max(0, dur − GUARD)]. */
fun seekBarTarget(progress: Int, durationMs: Long): Long?
```
Phép nhân dùng `Long` (bài 10 giờ = 36.000.000 ms × 1000 vẫn trong `Long`).

### 3.2 `MeloSeekLine` (trong `MeloComponents.kt`, thay `MeloThumbProgress`)
```kotlin
internal class MeloSeekLine(
    context: Context, trackColor: Int, fillColor: Int, thumbColor: Int,
    trackPx: Int, thumbPx: Int, touchPx: Int,
    private val onPreview: (Long?) -> Unit,   // mốc đích khi đang kéo; null khi thả/hủy
    private val onSeek: (Long) -> Unit
) : SeekBar(context)
```
- `max = SEEK_BAR_MAX`, `progressDrawable` = track (`trackColor`, cao `trackPx`, bo tròn) + fill (`fillColor`, `ClipDrawable`), id `android.R.id.background`/`android.R.id.progress`; `thumb` oval `thumbPx`; `splitTrack = false`; `minimumHeight = touchPx`; padding trái/phải = `thumbPx / 2`; `contentDescription = "Thanh tua bài hát"`.
- `fun set(positionMs: Long, durationMs: Long)`: lưu `durationMs`; `isEnabled = seekBarEnabled(dur)`; khi **không** đang kéo thì `progress = seekBarProgress(...)` (chỉ gán khi khác, tránh vẽ lại mỗi giây).
- Listener:
  - `onStartTrackingTouch` ⇒ `dragging = true`; `onPreview(target)`.
  - `onProgressChanged(fromUser = true)`: nếu `dragging` ⇒ `onPreview(target)`. Nếu **không** `dragging` (phím/TalkBack) ⇒ `target?.let(onSeek)`.
  - `onStopTrackingTouch` ⇒ `dragging = false`; `onPreview(null)`; `target?.let(onSeek)`.
- Lớp chỉ dùng hai hàm policy ở 3.1; không gọi `host` trực tiếp.
- Xóa `MeloThumbProgress` (lớp và KDoc E98 "Display only…"). Không còn nơi nào dùng (đã grep: chỉ `MeloTabletScreen.kt:64, :270`).

### 3.3 `MeloStudioDeck`
- Trường mới `seekLine: MeloSeekLine`, tạo trong `init` sau `c`: màu `p.border` / `p.accent` / `p.accent`, `s.progressHeight`, `s.progressThumb`, `touchPx = s.minTouch`. `onPreview = { previewMs = it; renderTime(host.nowPlaying()) }`, `onSeek = { host.seekTo(it); refresh() }`.
- `buildDock` (`:247`): `dock.addView(seekLine, MATCH_PARENT × s.minTouch)` **trước** `time`; `time` giữ `Gravity.END`, cách thanh `s.gap / 2`. Hàng nút giữ nguyên, kể cả `seekBy` cho −30s/+60s.
- Tách `renderTime(now)` từ `renderNow` (`:428`, `:436`): giờ hiện = `progressText((previewMs ?: now.positionMs) / 1000, now.durationMs / 1000)`; `now == null` ⇒ `""`.
- `renderNow`: `now == null` ⇒ `seekLine.set(0, 0)`; còn lại `seekLine.set(now.positionMs, now.durationMs)`.
- Sửa KDoc lớp (`:43-45`) và KDoc `buildDock` (`:243-246`): "E103: thanh kéo tua quay lại trong bến điều khiển, trên dòng giờ."
- Không đụng `applyTight` (bến không nằm trong đó) và `requestArtFit`.

### 3.4 `MeloTabletScreen`
- `timeBlock` thành trường dùng cho cả hai kiểu (bỏ chú thích "Teal theme only" cho `timeBlock`); `thumbProgress` → `seekLine: MeloSeekLine`.
- `buildPlayerPane` (màu thường, `:202`): thay `column.addView(time…)` bằng `timeBlock` = [seekLine (MATCH × s.minTouch)][time END], `topMargin = s.gap * 2`. Màu thanh: `p.border` / `p.accent` / `p.accent`.
- `buildThemedPlayerPane` (Teal, `:270-279`): thay `MeloThumbProgress(...)` bằng `MeloSeekLine(context, p.border, p.accent, p.text, s.progressHeight, s.progressThumb, s.minTouch, …)`, cao `s.minTouch`; hàng `timeLeft`/`timeRight` giữ nguyên.
- `onPreview = { previewMs = it; renderTimes(host.nowPlaying()) }`, `onSeek = { host.seekTo(it); onPlaybackChanged() }`.
- `onPlaybackChanged` (`:625-665`): gọi `seekLine.set(...)` ở cả hai kiểu (null ⇒ `set(0, 0)`). Giờ trái (Teal) hoặc `time` (thường) dùng `previewMs ?: now.positionMs`. Gom vào một hàm nhỏ `renderTimes(now)`.
- `applyTight` (`:407`): `if (teal) timeBlock else time` ⇒ `timeBlock`.

### 3.5 Phiên bản
`app/build.gradle:93-94`: vc **119** / `0.6.94` (hậu tố `-beta` có sẵn).

## 4. File sửa

| File | Thay đổi | Ước lượng |
|---|---|---|
| `app/src/shareable/.../MeloNowPlayingPolicy.kt` | 2 hằng + 3 hàm 3.1 | +20 |
| `app/src/shareable/.../MeloComponents.kt` | `MeloSeekLine` thay `MeloThumbProgress` | +70 / −60 |
| `app/src/shareable/.../MeloStudioDeck.kt` | 3.3 | +25 / −5 |
| `app/src/shareable/.../MeloTabletScreen.kt` | 3.4 | +30 / −15 |
| `app/build.gradle` | vc119 / 0.6.94 | 2 |
| `app/src/test/.../MeloNowPlayingPolicyTest.kt` | test 3.1 | +60 |

(`...` = `java/com/bsdung/musicassistantcar/data/telegram`.) Tổng production khoảng +145 / −80. Không phải làn nhanh (4 file production và đụng Đang phát của nhiều bố cục).

## 5. Bước cho Sonnet

**Phiên 1: code + test Ni (S0–S4)**
- **S0** Kiểm Git: nhánh `dot/E103-THANH-TUA`, HEAD = commit kế hoạch, cây sạch, không agent khác đang sửa/build.
- **S1** TDD 3.1: viết test trước rồi mới viết hàm. *Commit 1* `E103 S1: seek bar policy + tests`.
- **S2** `MeloSeekLine` (3.2) và nối vào `MeloTabletScreen` (3.4); xóa `MeloThumbProgress`. Biên dịch được. *Commit 2* `E103 S2: draggable seek line in Hai khung`.
- **S3** Deck (3.3). *Commit 3* `E103 S3: seek line back in the Studio Deck dock`.
- **S4** Bump vc119 / 0.6.94. Chạy đủ lệnh mục 6 (log ra file, chỉ đọc dòng tổng kết). Ghi "Phiên 1" vào `docs/plans/E103-THANH-TUA-bao-cao.md`. *Commit 4*.

**Phiên 2: build Tiny + QA + báo cáo (S5–S6)**
- **S5** Git bundle (kèm SHA256) sang Tiny, build trong clone cô lập có Telegram config; kiểm package/vc119/cert/config. Cài Sony (Tiny) và Shanling (Ni) theo cổng mục 7.
- **S6** QA mục 7, ghép `00_tong_hop.png`, hoàn tất báo cáo với mục "APK QA cần Opus nghiệm thu bàn giao", commit, DỪNG.

## 6. Test và build

- `MeloNowPlayingPolicyTest` thêm **≥ 10 ca**:
  - `seekBarEnabled`: 0, −1, `Long.MIN_VALUE + 1` (`C.TIME_UNSET`) ⇒ false; 1 ⇒ true.
  - `seekBarProgress`: dur 0 ⇒ 0; pos âm ⇒ 0; giữa bài; pos > dur ⇒ 1000; bài 10 giờ không tràn số.
  - `seekBarTarget`: dur ≤ 0 ⇒ null; 0 ⇒ 0; 500 với dur 200.000 ⇒ 100.000; 1000 ⇒ dur − 1000; progress −5/1200 bị chặn; dur 600 ms (< GUARD) ⇒ 0.
  - Khứ hồi: `seekBarTarget(seekBarProgress(p, d), d)` lệch ≤ d/1000 với p ≤ d − 1000.
- Tổng ≥ 2089 + số test mới, 21 bỏ qua, 0 lỗi, cả Debug và Release.
- Lệnh trên Ni (CLAUDE.md §4):
```
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
git diff --check
```
- JVM không chứng minh được chạm/kéo thật. Phần đó dựa vào mục 7.

## 7. QA thiết bị: Sonnet tự làm qua ADB (CLAUDE.md §3, §6, §7; `docs/workflow/SCREEN_LIFECYCLE_QA.md`)

**Cổng trước khi cài (mỗi máy):** `adb devices -l` thấy serial ở trạng thái device, getprop đúng model. Package `app.melovault.player`, máy đang ở vc118, APK vc119 (không downgrade), cert khớp app đang cài, Telegram config = true (APK Tiny). `dumpsys media_session` không PLAYING; nếu đang phát thì STOP và hỏi. Chỉ dùng `adb -s <serial> install -r "<APK_VALIDATED>"`.

**Cách đo:**
- Lấy tọa độ thanh bằng `uiautomator dump` (đọc `bounds` của nút có `content-desc="Thanh tua bài hát"`). Không đoán tọa độ.
- Vị trí phát lấy từ `dumpsys media_session` (`position=` trong PlaybackState), đọc **trước và sau** mỗi thao tác, kèm thời lượng bài. Kéo: `input swipe x1 y x2 y 800` trên đúng hàng của thanh.
- Chọn bài **đã tải đủ** ("✓ Đã tải"), dài ≥ 3 phút, để tua không phải chờ tải.

**Ảnh "trước":** Sony vc118 Deck dọc (chưa có thanh), chụp trước khi cài.

| # | Thiết bị | Hành động | Kỳ vọng (PASS) | Ảnh |
|---|---|---|---|---|
| 1 | Sony `QV725WEQ54` vc119, Điện thoại dọc | Phát một bài đã tải → bấm thẻ Đang phát ở thanh đáy → Deck mở | Bến ghim theo thứ tự: thanh tua, dòng giờ, −30s/⏮/⏸/⏭/+60s; thanh tiến theo nhạc; ảnh bìa không bị đè | 01 |
| 2 | Sony | Kéo thanh từ chấm hiện tại tới ~75% chiều rộng | `position` ≈ 75% thời lượng (±3%); dòng giờ đúng mốc mới; vẫn PLAYING, **cùng bài** | 02 |
| 3 | Sony | Chạm một điểm ~25% trên thanh; rồi bấm +60s; rồi −30s | Lần lượt ≈ 25%; +60 s; −30 s (±2 s mỗi lần) | 03a, 03b |
| 4 | Sony | Cuộn hàng đợi xuống cuối | Thanh tua và 5 nút **vẫn trên màn** (hồi quy F01) | 04 |
| 5 | Shanling `30313860180939` vc119, Gọn | Đang phát Gọn → kéo `SeekBar` tới ~50% | Màn Gọn không đổi so với vc118; `position` ≈ 50%; không crash | 05 |
| 6 | Sony ngang (Hai khung) | **Chỉ khi chủ dự án cho phép xoay màn Sony.** Khung trái → kéo thanh | Như bước 2 trên Hai khung | 06 |

- **Bước 6** mặc định **CHƯA KIỂM**: xoay màn bằng ADB là đổi cài đặt hệ thống (`user_rotation`), cần chủ dự án cho phép riêng. Nếu được phép, phải trả lại đúng giá trị cũ trong `finally`.
- Không tua bài đang tải dở trên máy (chỉ ghi nhận rủi ro mục 10). Không đổi Kiểu màu hay bố cục trong Cài đặt.
- **Cleanup (`finally`):** dừng phát (`input keyevent 127` hoặc nút ⏸), `input keyevent 223`, kiểm `dumpsys display` có `Display State=OFF`, ghi `SHANLING_SCREEN_END` / `SONY_SCREEN_END`.
- Ảnh lưu ở `docs/plans/artifacts/E103-THANH-TUA/NN_<máy>_<mô tả>_<HHMMSS>.png` rồi ghép `00_tong_hop.png`. Ảnh có tên bài/kênh **không commit**.
- **Không kiểm được:** âm thanh thật (CHƯA XÁC NHẬN ÂM THANH THỰC), Hai khung Teal/thường trên máy thật nếu không xoay, Deck ngang, FYT/Y700/S25, núm xoay/DPAD trên FYT, tua vào phần chưa tải.

### Bác sĩ tự kiểm tra (Sony hoặc điện thoại, sau khi có APK candidate vc119, hoặc duyệt ảnh từ xa)
1. Mở MeloVault, phát một bài, bấm thẻ Đang phát ở dưới: thấy **thanh tua** ngay trên dòng giờ, dưới nữa là −30s / ⏮ / ⏸ / ⏭ / +60s.
2. Kéo chấm tròn sang phải rồi thả: giờ chạy theo khi kéo, thả tay thì nhạc nhảy tới đúng chỗ đó.
3. Bấm +60s và −30s: vẫn tua như cũ.
4. Cuộn hàng đợi xuống: thanh tua và các nút vẫn nằm yên ở đáy.
5. (Máy tính bảng/FYT ngang, Hai khung) kéo thanh ở khung trái: nhạc tua tới chỗ đó.

## 8. PASS / FAIL

**PASS khi đạt tất cả:**
- Test Debug + Release 0 lỗi, có đủ test mới; build OK; `git diff --check` sạch; chỉ đổi file ở mục 4 (cùng báo cáo).
- Sony bước 1–4 PASS; Shanling bước 5 PASS; bước 6 PASS hoặc CHƯA KIỂM có lý do.
- Không crash/ANR trong logcat suốt QA.
- Chủ dự án xem ảnh hoặc tự cài vc119 và nói **ĐẠT**.

**FAIL khi gặp bất kỳ điều nào:**
- Deck hoặc Hai khung không có thanh, hoặc thanh không kéo được.
- Đang kéo mà thanh bị nhịp 1 s kéo về, hoặc tua liên tục trong lúc kéo (chỉ được tua khi thả tay).
- Mất nút −30s/+60s, hoặc ±30/60 tua sai.
- Bến điều khiển bị cuộn mất, hoặc ảnh bìa/chữ bị đè.
- Màn Gọn, Car Mode hoặc "Màn cũ" bị đổi.
- Kéo tới cuối mà nhảy bài trước khi thả tay.
- Crash/ANR; sửa service/Media3/hàng đợi/DB/TDLib.

## 9. STOP: Sonnet dừng và báo, không tự xoay xở

- Cây bẩn không rõ nguồn, HEAD/nhánh lệch, agent khác đang sửa/build.
- Phải sửa `MeloScreenHost.seekTo`, `TelegramPreviewActivity`, service, Media3, Gọn hoặc Car Mode thì mới làm được.
- `SeekBar` trong `Dialog` của Deck không nhận kéo (ví dụ bị cử chỉ vuốt-đóng hay `ScrollView` chặn): ghi lại và STOP, không tự đổi cấu trúc bến.
- Bến cao thêm làm ảnh bìa Deck dọc trên Sony nhỏ hơn `rowArt` hoặc đè chữ: STOP, gửi ảnh.
- ADB không thấy máy / unauthorized / signature mismatch / downgrade / sai package / APK thiếu Telegram config: BLOCKED phần QA. Cấm `uninstall`, `pm clear`, `-d`, xóa dữ liệu/cache/TDLib, đăng xuất, đổi tài khoản, tự xoay màn khi chưa được phép.
- Không có đường Git bundle tới Tiny: STOP và hỏi.

## 10. Rủi ro và rollback

| Rủi ro | Giảm thiểu |
|---|---|
| Chạm nhầm thanh khi lái xe (lý do E95 gỡ thanh) | Chủ dự án đã chốt đưa lại. Chỉ tua khi thả tay; Car Mode giữ nguyên. Nếu cần, bỏ "chạm để nhảy" ở một việc sau |
| Tua tới đoạn chưa tải của bài Telegram đang tải dở | Đi cùng đường `host.seekTo` như +60s hiện nay. Media3 sẽ đệm; không thử trên máy ở E103, ghi giới hạn |
| Bến Deck cao thêm khoảng 48 dp, ảnh bìa nhỏ lại trên cửa sổ thấp (FYT 642 dp) | Art-fit và `playerTight` có sẵn; QA bước 1, 4; FYT để chủ dự án xem |
| Nhịp 1 s ghi đè lúc đang kéo | Cờ `dragging` trong `MeloSeekLine` |
| Phím DPAD/núm xoay tua từng 5% | Hành vi chuẩn của `SeekBar`; ghi chưa kiểm trên FYT |
| Hai khung Teal trông khác E98 | Cùng màu, cùng cỡ track/thumb; chỉ vùng chạm cao hơn |

**Rollback:** revert Commit 3 rồi Commit 2 (Commit 1 chỉ thêm hàm thuần). Giữ vc119 để không downgrade. Không có dữ liệu hay di trú nào.

## 11. Chưa kiểm chứng / cần xác nhận

- [CẦN XÁC NHẬN] versionName `0.6.94` cho vc119.
- [CẦN XÁC NHẬN] Cho phép Sonnet xoay màn Sony qua ADB (bước 6) hay để chủ dự án tự xem Hai khung trên máy tính bảng/FYT. Mặc định: **không xoay**.
- [CẦN XÁC NHẬN] Chặn mép cuối 1 s (mục tiêu 5). Nếu chủ dự án muốn kéo tới cuối là sang bài kế thì đặt `SEEK_BAR_END_GUARD_MS = 0`.
- Âm thanh thật, FYT, Y700, S25, Deck ngang: chưa kiểm (chủ dự án tự xem).
