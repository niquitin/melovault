# 2026-11-02-bo-chinh-am-eq — Kế hoạch: Bộ chỉnh âm (EQ) cho màn Đang phát

Vai: **lập kế hoạch** (Claude Code, Opus 5.5), 02/11/2026. Không code, không build. Nhánh `dot/2026-11-02-bo-chinh-am-eq`, worktree `D:\Bots\_worktrees\MeloVault-eq`, baseline `b90834f` (đầu nhánh tích hợp `feature/e93-03d-y700-offline-cockpit` sau merge Cài đặt).

## 1. Yêu cầu và quyết định của chủ dự án (chat 02/11/2026)

Chủ dự án giao “lập plan về EQ”. BACKLOG mục 2: nút EQ ở Studio Deck đang báo “Sắp có”; phải chốt phạm vi trước khi code. Chủ dự án đã trả lời 4 câu (đều chọn phương án khuyên dùng):

| # | Câu hỏi | Lựa chọn |
|---|---|---|
| LD1 | EQ xử lý âm thanh bằng cách nào | **Tự xử lý trong app**: bộ lọc số chạy trong Media3 (`AudioProcessor`), không dùng `android.media.audiofx.Equalizer` của hệ thống |
| LD2 | Giao diện | **Mẫu sẵn + 10 dải**: Bật/Tắt, ~9 mẫu, Tùy chỉnh 10 thanh ±12 dB, tự giảm âm lượng đầu vào để không vỡ tiếng, nút Đặt lại |
| LD3 | Lối vào | **Chỉ nút EQ ở màn Đang phát** (Studio Deck); không thêm dòng trong Cài đặt |
| LD4 | EQ theo đầu ra (loa/tai nghe/Bluetooth/xe) | **Để nhiệm vụ sau**; bản này một cài đặt EQ chung cho cả app |

## 2. Baseline (đọc mã tại `b90834f`)

- **Nút EQ:** `MeloStudioDeck.topBar()` (`app/src/shareable/.../data/telegram/MeloStudioDeck.kt` ~dòng 394) tạo `c.chip("EQ", false)` → `Toast "EQ sắp có trong bản sau"`, `contentDescription = "Bộ chỉnh âm, sắp có"`. Nằm cùng hàng với chip “Hẹn giờ” (hàng chia đôi; chữ lớn > 1.3 thì xếp dọc). KDoc đầu lớp ghi “EQ is not available yet”.
- **Trình phát Shareable:** `ShareablePlaybackService.onCreate()` (`app/src/shareable/.../data/telegram/ShareablePlaybackService.kt` ~dòng 243) dựng `ExoPlayer.Builder(this)` **không có RenderersFactory riêng**, không có `AudioProcessor` nào, `AudioAttributes.DEFAULT` + xử lý audio focus, `setHandleAudioBecomingNoisy(true)`. Nguồn dữ liệu `TelegramOwnerAudioDataSource.Factory`. Bài Telegram lẫn “Nhạc trên máy” đều qua player này. Offload Media3 mặc định tắt; float output mặc định tắt.
- **Mẫu sẵn trong repo:** bản Personal (`app/src/main/.../playback/MusicService.kt` ~dòng 310) đã dùng `DefaultRenderersFactory` ghi đè `buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)` → `DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(gainAudioProcessor))`; `GainAudioProcessor` (`app/src/main/.../playback/GainAudioProcessor.kt`) là `BaseAudioProcessor` hỗ trợ PCM 16/24/32-bit và float, đường tắt “byte giống hệt” khi hệ số = 1, `onFlush(StreamMetadata)`. Trạng thái bật/tắt đọc từ SharedPreferences và một `OnSharedPreferenceChangeListener` trong service. Test: `app/src/test/.../playback/GainAudioProcessorTest.kt`. **EQ đi theo đúng khuôn này** nhưng nằm trong source set `shareable`.
- **Prefs dùng chung UI ↔ service:** `MeloNowPlayingPolicy.PLAYBACK_PREFS = UiThemePolicy.PREFS_NAME = "melovault_ui_preferences"` (đã dùng cho `prefetch_next`). Không cần DB/schema.
- **Media3** `1.11.1`, minSdk 26, targetSdk 36, Shareable vc121 `0.6.96-beta`. Test JVM của lớp Shareable nằm ở `app/src/test/.../data/telegram/` (ví dụ `MeloNowPlayingPolicyTest.kt`). Gate gần nhất: 201 suite, **2725 test**, 21 skipped, 0/0.
- **Khảo sát Sony 02/11/2026 (chỉ đọc, màn không bật):** `QV725WEQ54`, `XQ-AS52`, Sony, Android 12; màn `Dozing`, không phiên phát đang chạy. `/vendor/etc/audio_effects.xml` có `equalizer` (bundle AOSP), `equalizer1–4` (Sony), `bassboost`, `virtualizer`, `dynamics_processing`, `loudness_enhancer`, `dap` (Dolby phần mềm), `music_helper` áp cho luồng music. ⇒ EQ hệ thống *có* trên Sony nhưng số dải/tần số tùy máy và có các hiệu ứng riêng của hãng chồng lên — thêm lý do chọn LD1 (tự xử lý, nghe giống nhau mọi máy). Hiệu ứng âm thanh hệ thống của Sony (Dolby/ClearAudio+) **không được đổi** trong QA; nếu đang bật thì EQ của app cộng thêm lên — ghi nhận là giới hạn, không phải lỗi.

## 3. Mục tiêu / ngoài phạm vi

**Mục tiêu (bản này):**
1. Chạm nút EQ ở Studio Deck → mở **bảng EQ**: công tắc Bật/Tắt, hàng mẫu, 10 thanh dải, nút Đặt lại, một dòng giải thích.
2. Âm thanh của mọi bài phát qua `ShareablePlaybackService` (Telegram + Nhạc trên máy) đi qua bộ lọc EQ khi bật; đổi mẫu/kéo thanh có hiệu lực trong ≤ ~0,5 s mà không dừng nhạc, không tua lại, không mất vị trí.
3. **Tắt EQ (mặc định) = âm thanh giữ nguyên từng byte như hiện nay** (đường tắt bypass, có test).
4. Không vỡ tiếng khi tăng dải: tự giảm đầu vào (preamp tự động = −max(dải dương)) + chặn biên an toàn.
5. Cài đặt EQ lưu trong prefs, còn sau khi tắt app/khởi động lại máy; không gắn với tài khoản (đăng xuất không xóa).
6. Chip EQ cho biết trạng thái: tắt → “EQ” như cũ; bật → chip tô màu nhấn, chữ “EQ · <tên mẫu>”.

**Ngoài phạm vi:** EQ theo đầu ra (LD4); dòng EQ trong Cài đặt (LD3); EQ hệ thống `audiofx`; bass boost/virtualizer/reverb/loudness riêng; EQ tham số (tự chọn tần số/Q); lưu mẫu tùy chỉnh có tên/nhiều mẫu tùy chỉnh; màn Đang phát kiểu cũ (`NowPlayingFullScreenDialog`), Car Mode UI, Gọn có màn riêng (nếu Gọn mở Studio Deck thì tự có); bản Personal (`MusicService`); đổi Media3/MediaSession ngoài việc gắn RenderersFactory; float output/hi-res output; package, signer, versionCode, DB, TDLib.

## 4. Phương án và lý do

### 4.1 Xử lý âm thanh — `MeloEqAudioProcessor` (Media3 `BaseAudioProcessor`)

- **Vị trí:** file mới `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloEqAudioProcessor.kt` (chỉ Shareable; Personal không đổi).
- **Bộ lọc:** 10 bộ lọc **peaking biquad** (công thức RBJ Audio EQ Cookbook) nối tiếp, tần số trung tâm chuẩn ISO **31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz**, Q = 1,41 (≈ 1 quãng tám). Dải có `f ≥ 0,45 × sampleRate` thì **bỏ qua** (ví dụ 16 kHz ở tệp 32 kHz). Dải gain 0 dB thì bỏ qua (không tốn CPU). Tính bằng `Double`, dạng Transposed Direct Form II, trạng thái riêng cho từng kênh.
- **Định dạng:** giống `GainAudioProcessor`: `onConfigure` nhận PCM 16/24/32-bit và float, trả `inputAudioFormat` (không đổi định dạng/sample rate/kênh); định dạng khác → `AudioFormat.NOT_SET` (bỏ qua, không làm hỏng phát). Đọc mẫu → float [-1,1] → lọc → nhân preamp → chặn biên → ghi lại đúng định dạng.
- **Luôn active với PCM**, bypass bên trong: khi EQ tắt hoặc mọi dải = 0 → `output.put(input)` (byte giống hệt). Lý do: bật/tắt `isActive` đòi Media3 cấu hình lại sink (flush, có thể ngắt tiếng); bypass nội bộ đổi tức thì. Đây cũng là cách `GainAudioProcessor` đang làm.
- **Đổi tham số an toàn luồng:** UI/service đặt `@Volatile var settings: MeloEqPolicy.Settings` (đối tượng bất biến). Luồng âm thanh, đầu mỗi `queueInput`, so tham chiếu/sample rate với bản đã tính; khác thì tính lại hệ số (10 dải, rẻ) và **giữ nguyên trạng thái bộ lọc** (không reset) để tránh tiếng bụp. Preamp đổi dần tuyến tính trong buffer đầu tiên sau khi đổi (ramp) để giảm tiếng tách. Chuyển giữa bypass ↔ lọc: khi bật lại, reset trạng thái bộ lọc về 0 trước buffer đầu.
- **`onFlush`/`onReset`:** reset trạng thái bộ lọc (tua, đổi bài, cấu hình lại) — không lan “đuôi” của bài trước.
- **Preamp tự động:** `preampDb = −max(0, max(gain các dải đang dùng))` (ví dụ Tăng bass +6 → −6 dB). Sau đó chặn biên cứng ([-1,1] float / dải nguyên) như Gain. Không có limiter mềm trong bản này.
- **Log Debug (`BuildConfig.DEBUG`), tag `MeloEq`:** khi cấu hình (`sr`, `encoding`, `channels`), khi đổi cài đặt (`enabled`, `preset`, `preampDb`), và mỗi ~10 s khi đang lọc: `inRmsDb`, `outRmsDb`, số mẫu bị chặn biên, thời gian xử lý trung bình µs/buffer. Release không log. Log này là **bằng chứng kỹ thuật trên máy** rằng EQ thật sự chạy (agent không nghe được âm thanh).
- **CPU ước tính:** 10 biquad × 2 kênh × 44,1 kHz ≈ 0,9 triệu phép lọc/s (vài chục MFLOPS ở 192 kHz) — nhỏ; vẫn đo trên Sony ở QA.

**Phương án loại:** `android.media.audiofx.Equalizer` gắn `audioSessionId` (chủ dự án không chọn, LD1): ít mã nhưng dải/tần số do máy quyết (Sony 5 dải bundle + bản Sony), có ROM/đầu xe bỏ qua hoặc ném lỗi, không test JVM được.

### 4.2 Gắn vào trình phát — `ShareablePlaybackService`

- Thêm `private val eqProcessor = MeloEqAudioProcessor()`; trong `onCreate()` dựng `object : DefaultRenderersFactory(this) { override fun buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams) = DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput).setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams).setAudioProcessors(arrayOf(eqProcessor)).build() }` và `.setRenderersFactory(renderersFactory)` vào `ExoPlayer.Builder` hiện có. **Mọi tùy chọn khác của builder giữ nguyên** (media source factory, audio attributes, noisy). Không thêm renderer ALAC (Personal có, Shareable không — giữ như cũ).
- Cờ hoàn tác `MeloEqPolicy.FEATURE_ENABLED` (const `true`): `false` → không gắn RenderersFactory (pipeline giống hệt `b90834f`) và chip EQ quay lại Toast cũ.
- Đọc prefs khi `onCreate` → `eqProcessor.settings = MeloEqPolicy.load(prefs)`; `registerOnSharedPreferenceChangeListener` chỉ phản ứng khóa `eq_*`, gỡ ở `onDestroy`. Đây là một nguồn sự thật: UI chỉ ghi prefs.
- **Không** đổi hàng đợi, shuffle, prefetch, hẹn giờ, MediaSession, quyền TDLib.

### 4.3 Chính sách thuần JVM — `MeloEqPolicy.kt` (file mới, shareable)

- `BANDS_HZ = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)`, `MIN_DB = -12`, `MAX_DB = 12`, bước **1 dB**.
- `enum class Preset(val key, val label, val gains: IntArray)` — giá trị khởi điểm (dB, theo thứ tự dải trên) **[CẦN XÁC NHẬN: chủ dự án có thể chỉnh sau khi nghe]**:

| Mẫu (key) | 31 | 62 | 125 | 250 | 500 | 1k | 2k | 4k | 8k | 16k |
|---|---|---|---|---|---|---|---|---|---|---|
| Phẳng (`flat`) | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| Tăng bass (`bass_up`) | 6 | 5 | 4 | 2 | 0 | 0 | 0 | 0 | 0 | 0 |
| Giảm bass (`bass_down`) | −6 | −5 | −4 | −2 | 0 | 0 | 0 | 0 | 0 | 0 |
| Tăng treble (`treble_up`) | 0 | 0 | 0 | 0 | 0 | 1 | 2 | 4 | 5 | 6 |
| Giọng hát (`vocal`) | −2 | −2 | −1 | 0 | 2 | 4 | 4 | 2 | 0 | −1 |
| Rock (`rock`) | 5 | 4 | 2 | 0 | −1 | −1 | 1 | 3 | 4 | 5 |
| Pop (`pop`) | −1 | 0 | 2 | 4 | 4 | 2 | 0 | −1 | −1 | −1 |
| Jazz (`jazz`) | 3 | 2 | 1 | 2 | −1 | −1 | 0 | 1 | 2 | 3 |
| Cổ điển (`classical`) | 4 | 3 | 2 | 1 | 0 | 0 | 0 | 2 | 3 | 4 |
| Tùy chỉnh (`custom`) | giá trị người dùng kéo | | | | | | | | | |

- `data class Settings(val enabled: Boolean, val preset: Preset, val custom: IntArray)` + `gains()` (mẫu → `preset.gains`, Tùy chỉnh → `custom`), `isFlat()`, `preampDb()`, `isBypass() = !enabled || isFlat()`. Ghi đè equals/hashCode cho mảng (hoặc dùng `List<Int>`).
- Prefs (trong `PLAYBACK_PREFS`): `eq_enabled` (Boolean, mặc định **false**), `eq_preset` (String key, mặc định `flat`), `eq_custom_db` (String `"0,0,…"` 10 số nguyên). `load(prefs)`/`save(editor, settings)`; giá trị hỏng/thiếu/ngoài khoảng → kẹp về [−12, 12] hoặc về Phẳng; số lượng khác 10 → Phẳng. Không bao giờ ném lỗi.
- Hàm cho UI: `chipLabel(settings)` → “EQ” / “EQ · Rock” / “EQ · Tùy chỉnh”; `chipDescription(settings)` → “Bộ chỉnh âm, đang tắt” / “Bộ chỉnh âm, đang bật, mẫu Rock”; `bandLabel(hz)` → “31 Hz”, “1 kHz”, “16 kHz”; `gainLabel(db)` → “+3 dB”, “0 dB”, “−4 dB” (dấu trừ U+2212); `onBandDragged(settings, index, db)` → chuyển sang Tùy chỉnh, chép giá trị mẫu hiện tại vào custom rồi đổi dải đó; `reset(settings)` → Phẳng, giữ Bật/Tắt.
- `object Biquad` (thuần): `peaking(fHz, gainDb, q, sampleRate): Coeffs` và `magnitudeDb(coeffs, fHz, sampleRate)` để test đáp tuyến.

### 4.4 Giao diện — `MeloEqPanel.kt` (file mới, shareable) + sửa `MeloStudioDeck`

- Chip EQ: `c.chip(MeloEqPolicy.chipLabel(s), selected = s.enabled && !FEATURE_DISABLED)`; chạm → `MeloEqPanel(activity, c).show { refresh chip }`. Hàm `refresh()` của Deck cập nhật chip (đọc prefs nhẹ trên main thread — SharedPreferences đã nạp sẵn).
- Bảng EQ: `Dialog` cùng kiểu theme với Deck (hoặc bottom sheet tự dựng bằng `Dialog` + gravity BOTTOM; **không thêm thư viện**). Dùng `MeloComponents` (`c.text`, `c.chip`, `c.chipRow`, kích thước `c.sizes`, màu `c.palette`) để đúng 2 bảng màu và cỡ Gọn. Nội dung từ trên xuống:
  1. Thanh tiêu đề: “Bộ chỉnh âm (EQ)” + nút ✕ “Đóng”.
  2. Hàng công tắc: “Bật EQ” + `Switch` (mặc định Tắt). Khi Tắt, phần dưới vẫn hiện nhưng mờ (alpha 0.5) và vẫn chỉnh được — chỉnh khi đang Tắt **không** tự bật (tránh bất ngờ). **[CẦN XÁC NHẬN: hay chỉnh thanh là tự bật?]** Mặc định kế hoạch: không tự bật.
  3. `chipRow` các mẫu (cuộn ngang), chip đang chọn tô màu.
  4. 10 hàng ngang (dễ dùng trên điện thoại dọc và màn xe hơn thanh dọc): nhãn dải bên trái (“31 Hz”), `SeekBar` 0..24 (= −12..+12 dB), số dB bên phải. Cao mỗi hàng ≥ `minTouch`. Toàn bộ trong `ScrollView` để vừa màn ngang/chữ lớn. `contentDescription` từng thanh: “Dải 31 Hz, cộng 3 dB”.
  5. Dòng chú thích nhỏ: “EQ xử lý lại âm thanh trong app. Tắt EQ để nghe nguyên bản. Âm lượng tự giảm khi tăng dải để không vỡ tiếng.”
  6. Nút “Đặt lại” (về Phẳng).
- Mỗi thay đổi (công tắc, chọn mẫu, kéo thanh `onProgressChanged` fromUser) → `MeloEqPolicy.save(prefs.edit(), newSettings).apply()` → service áp ngay qua listener. Kéo thanh cập nhật số dB tức thì; ghi prefs mỗi bước 1 dB (tối đa 24 lần/lần kéo — chấp nhận được).
- Xoay màn khi bảng đang mở: bảng đóng theo Deck (Deck dựng lại) là chấp nhận được; **không** được làm hỏng/mất cài đặt (đã lưu từng bước).
- Sửa KDoc đầu `MeloStudioDeck` bỏ câu “EQ is not available yet”.

## 5. Bố cục đích (điện thoại dọc)

```
┌──────────────────────────────────┐
│ Bộ chỉnh âm (EQ)              ✕  │
│ Bật EQ                    [ ●━ ] │
│ [Phẳng][Tăng bass][Giảm bass][Tă…│  ← cuộn ngang
│  31 Hz  ━━━━━━━━━●━━━━━━  +6 dB  │
│  62 Hz  ━━━━━━━━●━━━━━━━  +5 dB  │
│ 125 Hz  ━━━━━━━●━━━━━━━━  +4 dB  │
│   …   (10 hàng, cuộn được)       │
│  16 kHz ━━━━━━●━━━━━━━━━   0 dB  │
│ EQ xử lý lại âm thanh trong app… │
│                        [Đặt lại] │
└──────────────────────────────────┘
Chip ở Studio Deck:  [ EQ · Tăng bass ] [ Hẹn giờ ]   (EQ tô màu nhấn khi bật)
```

## 6. Các bước thực thi

**Chặng 1 (Ni, không cần thiết bị): S0–S7. Chặng 2 (Tiny + Sony + Drive + hồ sơ): S8–S11.** Có thể gộp 2 chặng trong một hội thoại nếu chủ dự án giao; luôn cập nhật bàn giao sau mỗi bước.

| Bước | Việc | Xong khi |
|---|---|---|
| S0 | Kiểm phiên (CLAUDE.md mục 1) trong `D:\Bots\_worktrees\MeloVault-eq`: `git status --short; git branch --show-current; git rev-parse HEAD; git log -5 --oneline; git diff --check`; HEAD = commit kế hoạch trên `b90834f`, không agent khác. Chạy gate baseline 1 lần (ra file log trong `docs/plans/artifacts/2026-11-02-bo-chinh-am-eq/`). | Ghi số test baseline (kỳ vọng 2725/21/0/0) |
| S1 | `MeloEqPolicy.kt`: dải, `Preset`, `Settings`, prefs load/save chống hỏng, preamp, nhãn, `onBandDragged`, `reset`, `Biquad`. Test `MeloEqPolicyTest.kt`: mặc định Tắt/Phẳng; load prefs rỗng/hỏng/thiếu phần tử/ngoài khoảng; round-trip save→load; preamp (Tăng bass = −6, Giảm bass = 0); `isBypass`; nhãn chip/mô tả/dB (dấu −); kéo dải từ mẫu Rock → Tùy chỉnh mang giá trị Rock trừ dải đã kéo; `Biquad.magnitudeDb` tại tâm ≈ gain (±0,5 dB) ở 44,1/48/96 kHz, xa tâm 3 quãng tám ≈ 0 (±1 dB); gain 0 → hệ số trung tính. | Test mới xanh |
| S2 | `MeloEqAudioProcessor.kt` theo mục 4.1. Test `MeloEqAudioProcessorTest.kt` (mẫu `GainAudioProcessorTest`): bypass (Tắt, và Bật+Phẳng) → **byte giống hệt** cho 16/24/32/float; định dạng không hỗ trợ → `NOT_SET`; sine 1 kHz qua mẫu có +6 dB ở 1 kHz (Tùy chỉnh) → biên độ ra ≈ +6 dB − preamp 6 = ≈ 0 dB (±1 dB), sine 62 Hz giữ ≈ −6 dB; không NaN/Inf; chặn biên khi đầu vào full-scale; đổi sample rate (`configure` lại) tính lại hệ số; `flush` reset trạng thái (impulse sau flush không có đuôi cũ); dải ≥ 0,45·sr bị bỏ qua (sr 32 kHz không lỗi); stereo/mono giữ đúng kênh, không trộn. | Test mới xanh |
| S3 | `ShareablePlaybackService`: RenderersFactory + sink + processor + đọc prefs + listener `eq_*` + gỡ ở `onDestroy`; cờ `FEATURE_ENABLED`. Không đổi phần nào khác của builder. Log Debug `MeloEq` khi áp cài đặt. | Build Debug xanh; diff service chỉ ở các dòng trên |
| S4 | `MeloEqPanel.kt` + sửa chip trong `MeloStudioDeck` (nhãn/màu/mô tả, mở bảng, refresh sau khi đóng), sửa KDoc. Không đổi hàng Hẹn giờ/bố cục khác. | Build Debug xanh |
| S5 | Rà chuỗi tiếng Việt (chính tả, dấu −, không cắt chữ ở cỡ Gọn và chữ lớn 1.3), `contentDescription`, hai bảng màu (Hiện tại / Teal sáng nếu còn). | Danh sách chuỗi trong báo cáo |
| S6 | Gate Ni: `.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain`; `.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain`; `-PmeloOpen=true :app:assembleShareableDebug`; `:app:assemblePersonalDebug`; `git diff --check`. | BUILD SUCCESSFUL, 0 failures; ghi số test (> 2725) |
| S7 | Commit mã (thông điệp tiếng Anh ngắn như các nhiệm vụ trước), cập nhật báo cáo + bàn giao. **Kết thúc Chặng 1.** | Commit; bàn giao checkpoint |
| S8 | Bundle SHA256 → Tiny clone cô lập build **Debug** có Telegram config → verify package `app.melovault.player`/vc121 (không downgrade)/cert khớp app đang cài/SHA-256/bytes → cài **một** lệnh `adb -s QV725WEQ54 install -r` (chỉ khi không có phiên phát của chủ dự án) → QA mục 7 với `logcat -s MeloEq` song song, chụp ảnh, ảnh tổng hợp `00_tong_hop.png`. Màn Sony OFF trong `finally`, ghi `SONY_SCREEN_END`. | Bảng QA PASS/FAIL/BLOCKED đủ |
| S9 | Đo nhanh trên Sony khi EQ Bật (mẫu Rock) phát ≥ 2 phút: `dumpsys media.audio_flinger` (số underrun của track app trước/sau), `top -n 1`/`dumpsys cpuinfo` (CPU tiến trình app), log `MeloEq` (µs/buffer, số mẫu chặn biên). So với EQ Tắt. | Số liệu trong báo cáo; underrun không tăng rõ |
| S10 | Drive Ni N owner-only: APK + ảnh; `*-apk-test.md`, `*-hinh-nghiem-thu.md` ghi **QA CANDIDATE — CHƯA NGHIỆM THU**. | shared=false đã kiểm |
| S11 | Báo cáo, bàn giao, prompt nghiệm thu sơ bộ; dừng. Trả Sony về như trước QA (EQ **Tắt**, mẫu Phẳng, Hẹn giờ Tắt). | Commit hồ sơ |

## 7. QA Sony (XQ-AS52, dọc, giao diện Mới; màn ON chỉ khi chạy)

Phát một bài **đã tải sẵn/đã có trong cache** (tránh phụ thuộc mạng) hoặc một bài Nhạc trên máy. Không đổi âm lượng hệ thống quá mức đang có, không đổi hiệu ứng âm thanh của Sony, không đăng xuất, không bấm Đồng bộ ngay.

| # | Thao tác | Kỳ vọng |
|---|---|---|
| Q1 | Mở Đang phát (Studio Deck), chụp hàng EQ/Hẹn giờ; chạm EQ | Chip “EQ” không tô màu; bảng EQ mở, công tắc Tắt, mẫu Phẳng, 10 thanh ở 0 dB, chữ không bị cắt |
| Q2 | Bật EQ → chọn “Tăng bass”, chụp; xem `logcat -s MeloEq` | Thanh 31–250 Hz nhảy đúng giá trị; log `enabled=true preset=bass_up preampDb=-6`, sau ≤ 10 s có dòng stats `outRms` khác `inRms`; nhạc không dừng, vị trí không nhảy |
| Q3 | Kéo thanh 1 kHz lên +4 dB | Chip mẫu chuyển “Tùy chỉnh”, các dải khác giữ giá trị Tăng bass; log cập nhật |
| Q4 | Đóng bảng; chụp chip; tắt app khỏi đa nhiệm (vuốt) **chỉ khi không ảnh hưởng chủ dự án** → mở lại → phát → mở bảng EQ | Chip “EQ · Tùy chỉnh” tô màu; sau khi mở lại vẫn Bật + Tùy chỉnh đúng giá trị (log áp lại khi service tạo) |
| Q5 | Tắt EQ | Chip về “EQ”; log `enabled=false`, không còn dòng stats lọc (bypass) |
| Q6 | Chuyển bài (Tiến), tua +60s, xoay ngang khi bảng mở, chữ lớn 1.3 **chỉ nếu đổi được trong app** (không đổi cài đặt hệ thống → nếu không: CHƯA KIỂM) | Không crash; chuyển bài/tua không có lỗi phát; xoay không mất cài đặt |
| Q7 | Đặt lại → Phẳng; trả Sony về EQ Tắt | Như trước QA |

Âm thanh: agent **không nghe được** → ghi **CHƯA XÁC NHẬN ÂM THANH THỰC**; bằng chứng kỹ thuật là test JVM (đáp tuyến) + log `MeloEq` RMS trên máy. Chủ dự án nghe theo mục 11.

## 8. File dự kiến thay đổi

- Mới: `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloEqPolicy.kt`, `MeloEqAudioProcessor.kt`, `MeloEqPanel.kt`.
- Sửa: `ShareablePlaybackService.kt` (RenderersFactory + prefs listener), `MeloStudioDeck.kt` (chip EQ + KDoc).
- Test mới: `app/src/test/java/com/bsdung/musicassistantcar/data/telegram/MeloEqPolicyTest.kt`, `MeloEqAudioProcessorTest.kt`.
- Hồ sơ: `docs/plans/2026-11-02-bo-chinh-am-eq-{bao-cao,ban-giao,apk-test,hinh-nghiem-thu}.md`; ảnh/log ở `docs/plans/artifacts/2026-11-02-bo-chinh-am-eq/` (bị `.gitignore`).
- **Không đụng:** `MusicService`/`GainAudioProcessor` (Personal), DB/SQLite, TDLib, `TelegramOwnerAudioDataSource`, MediaSession, hàng đợi/shuffle/prefetch, `build.gradle` (không thêm thư viện, không đổi version/signer/package), `AndroidManifest`.

## 9. Rủi ro, rollback, điểm dừng

| Rủi ro | Giảm thiểu |
|---|---|
| Gắn RenderersFactory làm đổi hành vi phát (giải mã, gapless, định dạng ra) | Chỉ thêm sink có processor, giữ `enableFloatOutput`/`PlaybackParams` truyền vào; bypass byte giống hệt khi Tắt (test S2); QA Q6 chuyển bài/tua. Nếu thấy khác biệt (lỗi định dạng, im tiếng, crash) → `FEATURE_ENABLED=false`, ghi BLOCKED |
| Vỡ tiếng/chói khi tăng dải | Preamp tự động + chặn biên; log đếm mẫu bị chặn; test full-scale |
| Tiếng bụp khi đổi mẫu/bật tắt | Giữ trạng thái bộ lọc khi đổi hệ số, ramp preamp, reset khi bật lại từ bypass; chủ dự án nghe (mục 11) |
| Tốn CPU/underrun ở tệp hi-res | Bỏ dải gain 0, Double nhưng vòng đơn giản; đo S9. Nếu underrun tăng rõ → báo, không tự đổi kiến trúc |
| Kẹt luồng âm thanh vì đồng bộ hóa | Chỉ `@Volatile` đối tượng bất biến, không khóa trong `queueInput` |
| Chồng hiệu ứng Sony (Dolby/ClearAudio+) | Không đổi cài đặt hệ thống; ghi giới hạn |
| Prefs hỏng làm crash service | `load` không ném lỗi, test dữ liệu hỏng |
| Cần đổi kiến trúc (ví dụ phải bật float output, thêm thư viện DSP, đổi MediaSession) | **DỪNG, ghi BLOCKED**, hỏi chủ dự án |

**Rollback:** đặt `MeloEqPolicy.FEATURE_ENABLED = false` (pipeline về như `b90834f`, chip về Toast cũ) hoặc không merge nhánh. Dữ liệu duy nhất thêm vào là 3 khóa prefs `eq_*`, vô hại khi bỏ.

## 10. Điều chưa rõ (không chặn thực thi — mặc định ghi rõ)

1. **[CẦN XÁC NHẬN]** Giá trị dB của 9 mẫu (bảng mục 4.3) — dùng giá trị trên; chủ dự án nghe rồi yêu cầu chỉnh ở REWORK nếu muốn.
2. **[CẦN XÁC NHẬN]** Kéo thanh khi EQ đang Tắt có tự bật không — mặc định **không**.
3. Tên hiển thị: “Bộ chỉnh âm (EQ)” trong bảng, chip giữ “EQ”.

## 11. Thao tác để chủ dự án tự thử (sau khi có APK)

Nên dùng **tai nghe** để nghe rõ khác biệt; giữ âm lượng vừa phải trước khi bật mẫu.

1. Mở một bài → màn Đang phát → chạm **EQ**: bảng mở, công tắc Tắt, 10 thanh ở 0. Đóng bảng: nghe phải **y như bản cũ**.
2. Bật EQ → chọn **Tăng bass**: tiếng trầm dày hơn rõ, âm lượng tổng không to vọt, không rè/vỡ; nhạc không dừng, không tua lại. Chip đổi thành “EQ · Tăng bass” có màu.
3. Lần lượt chọn **Giọng hát**, **Tăng treble**, **Rock**: mỗi lần đổi nghe khác trong khoảng nửa giây, **không có tiếng bụp/tách lớn**.
4. Kéo thanh **1 kHz** lên cao: mẫu chuyển “Tùy chỉnh”; tắt hẳn app rồi mở lại, phát bài khác → EQ vẫn Bật, vẫn Tùy chỉnh đúng như đã kéo.
5. Thử với một bài **FLAC/Hi-Res** và một bài **Nhạc trên máy**: EQ đều có tác dụng, không giật/ngắt tiếng. Nếu có điện thoại/xe khác (Y700, S25, FYT) thì thử thêm — không bắt buộc.
6. **Tắt EQ** hoặc **Đặt lại**: về âm thanh gốc.

Phản hồi: `ĐẠT 2026-11-02-bo-chinh-am-eq — <commit/APK>` hoặc `Yêu cầu sửa 2026-11-02-bo-chinh-am-eq: <lỗi>` (ví dụ chỉnh giá trị mẫu).
