# Kế hoạch — E95-FYT-UI-OPTIMIZATION (tối ưu giao diện cho FYT 7870)

**Vai trò:** Claude Opus lập kế hoạch, 19/10/2026. **TASK_ID:** `E95-FYT-UI-OPTIMIZATION`. **Nhánh triển khai:** `dot/E95-FYT-UI-OPTIMIZATION`.
**Phụ thuộc:** E95 **chỉ được triển khai sau khi E94-LOCAL-MUSIC đã PASS và được merge vào nhánh tích hợp** `feature/e93-03d-y700-offline-cockpit` (xem mục 10).

---

## 1. Baseline Git và trạng thái E94 (đã xác minh 19/10/2026)

| Mục | Giá trị thực tế |
|---|---|
| Repo | `D:\Bots\MeloVault-E91-Shareable`, cây sạch, `git diff --check` sạch |
| Nhánh đang mở trong repo | `dot/E94-LOCAL-MUSIC`, HEAD `7c1b75d8b894322f7dcb70619d46262cee15c193` (chỉ commit kế hoạch E94) |
| Nhánh tích hợp | `feature/e93-03d-y700-offline-cockpit` @ `0cc29e320990863d98c59e184b835e108491e5cf` (docs-only sau mốc nghiệm thu `ba74206`) |
| Worktree khác | ChatGPT: `dot/apk-test-delivery-policy` @ `0cc29e3` (docs) và các worktree cũ; không có agent nào đang commit code |
| **E94** | **Mới có kế hoạch** (`docs/plans/E94-LOCAL-MUSIC.md`). Chưa có commit code Sonnet, chưa có báo cáo, chưa có nghiệm thu. **Không coi E94 là đã code/QA/PASS.** |

Nhánh kế hoạch này (`dot/E95-FYT-UI-OPTIMIZATION`) được tạo từ `0cc29e3` qua một **git worktree tạm** (không checkout trong repo chính, không đụng nhánh E94); worktree tạm được gỡ sau khi commit để nhánh có thể mở lại trong workspace chính. Khi bắt đầu triển khai, nhánh phải được cập nhật lên HEAD tích hợp **sau khi E94 đã merge** (mục 5, bước S0).

---

## 2. Bằng chứng đã đọc

Gói `D:\Bots\_transfer\E95-FYT-DELIVERY\E95-FYT-UI-OPTIMIZATION\`: 12 tệp, **SHA-256 khớp toàn bộ** `MANIFEST_SHA256.json`. Đọc `PROMPT_FOR_CLAUDE_OPUS.md`, `README_E95_EVIDENCE.md`; xem trực tiếp **8/8 ảnh**; video gốc `09_…_71s.mp4` (h264 1152×720, 71,912 s) xem qua **khung trích mỗi 2 s** (36 khung) + **5 khung đầy đủ** (3 s, 5 s, 6,5 s, 21 s, 69 s) bằng ffmpeg (ảnh tạm trong thư mục scratchpad của phiên, ngoài repo). **Hạn chế:** không xem liên tục từng khung; chuyển động giữa các mốc 2 s chưa quan sát; preview `10_…mp4` không dùng.

**Thiết bị:** FYT 7870, Android 13 (API 33), UNISOC uis7870sc, mật độ 254 dpi (×1,5875), **cỡ chữ ×1,25**, ngang. App chạy trong "khung thẻ" của launcher FYT (thanh trạng thái FYT ở trên, dock + mini player hệ thống ở dưới, tay nắm "•••" ở giữa mép trên mỗi cửa sổ).

**Kích thước cửa sổ thực tế (ước từ ảnh/video, phải đo lại runtime):** toàn màn ≈ **1179×642dp**; chia 2/3 ≈ **782×642dp** (= 1243×1020 px, khớp ảnh 01); chia 1/2 ≈ **592×642dp**; chia 1/3 ≈ **388×642dp**.

**Mốc video:** 0:00–0:06 chia 1/3: Studio Deck dạng dọc một cột cuộn, người dùng cuộn xuống hàng đợi ⇒ hàng nút −30s/⏮/⏸/⏭/+60s **không còn trên màn** (F01); "•••" đè dòng trên cùng (F04). 0:08–0:14 giao diện **Gọn** (`MeloCompactScreen`: "Đã tải", "Tất cả · 29.551", "Telegram · 27 nguồn" với **mọi kênh "0 bài"** (F05), Đang phát Gọn có SeekBar). 0:16 chuyển tỷ lệ (T01). 0:18–0:26 chia 1/2: vẫn Gọn; Đang phát Gọn "Vuốt lên để xem thêm". 0:28–0:50 chia 2/3: **Hai khung** (`MeloTabletScreen`), "Các kênh chưa có nhạc (27)". 0:54 app bên phải 2/3: Hai khung. 0:58–1:10 Gọn ở 1/2 và 1/3.

### Bảng truy vết F01–F09, T01

| ID | Ưu tiên | Bằng chứng | Lớp UI thực tế trên FYT | Nguyên nhân (đã đối chiếu mã) | Kết luận |
|---|---|---|---|---|---|
| F01 | P0 | video 0:02–0:06 (khung 3 s, 6,5 s); ảnh 08 | `MeloStudioDeck` dạng dọc (dialog mở lại sau khi đổi cỡ, TPA:397-418) | Deck dọc là **một `ScrollView` duy nhất** (MeloStudioDeck.kt:169-188); ước 48+8+ảnh 289+tên 74+nguồn 27+viên 40+sóng âm 88+giờ 19+nút 80 ≈ 673dp > 642dp; Deck dọc không có art-fit (requestArtFit return :244) | **Xác nhận** |
| F02 | P0 | ảnh 07; video 0:30–0:50 | `MeloTabletScreen` (Hai khung 782dp, đang mở kênh) | Khung trái 40% chỉ còn ~262dp; khi mở kênh khung trái thành danh sách đổi kênh `quickSwitchRows` **bỏ kênh count ≤ 0** (MeloLibraryPolicy.kt:69-70) ⇒ rỗng vì F05 ⇒ vùng trống lớn; không có trạng thái rỗng | **Xác nhận** (một phần do F05) |
| F03 | P1 | ảnh 01, 04–08; video | Gọn ở 388/592dp; Hai khung ở 782/1179dp | (a) "màn hình" đo bằng `maximumWindowMetrics` (MeloUiLabActivity.kt:42-50) — trên FYT trả **kích thước cửa sổ** (ảnh 01: màn = cửa sổ = 1243×1020) ⇒ quy tắc Gọn "theo cả màn" thực chất theo cửa sổ; (b) nếu chỉ sửa đo thì `chooseLayout(592,642)` vẫn ra COMPACT → `newLayer` null → **giao diện cũ** (MeloLibraryPolicy.kt:38); (c) quy tắc phụ `widthDp > heightDp → TWO_PANE` (MeloScreenPolicy.kt:115) ép 782dp thành Hai khung; (d) mỗi lần đổi tỷ lệ, khóa lớp chứa kích thước px (TPA:4031, 4083) ⇒ **dựng lại màn**: mất vị trí cuộn, ô tìm trống trong khi danh sách vẫn lọc (MeloLibraryPane.kt:80, 185-188); (e) màn "Thông tin màn hình" không đo lại khi đổi cỡ (giải thích ảnh 01) | **Xác nhận** |
| F04 | P1 | ảnh 02, 03, 05, 06, 07, 08; video 0:00, 0:21 | mọi lớp | Lớp Melo chỉ cộng `systemBars|displayCutout` (TPA:4057-4062, 4111-4119); tay nắm "•••" của FYT vẽ đè trong cửa sổ, **giả thuyết: không được báo là captionBar inset** (chưa đo) | **Xác nhận hiện tượng**, nguyên nhân cần đo runtime |
| F05 | P1 | ảnh 02, 06; video 0:12, 0:42, 0:58 | lưới kênh (Hai khung) và danh sách kênh Gọn | `telegram_sources.track_count` là cột lưu, **chỉ tính lại khi đồng bộ hoàn tất cả hai pha** (TelegramSyncCoordinator.kt:362-363); kênh mới được chèn với 0 (AndroidTelegramLibraryDatabase.kt:382); quét đầy đủ bị ngắt khi app bị tắt và lần đăng nhập sau chạy `incremental=true` không tiếp con trỏ cũ ⇒ có thể **không bao giờ hoàn tất** trên FYT. Mâu thuẫn chứng minh được: kênh "Nhạc Lạnh - Chillout (EMI)" nằm trong "chưa có nhạc" (ảnh 06) trong khi mở ra có **77 bài** (ảnh 07) | **Sai lệch hiển thị đã chứng minh**; trạng thái DB FYT cần đo bằng dòng chẩn đoán |
| F06 | P1 | ảnh 07 | `MeloLibraryPane` hàng thẻ | Thẻ "Telegram 42.734 bài" khi đang mở kênh là **thiết kế có chủ đích** (docs/plans/the-nguon-nhac.md:20, 57, 122; MeloSourceCardPolicy.kt:97-99): bấm = về toàn bộ Telegram | **Đúng thiết kế**, chỉ làm rõ nhãn |
| F07 | P2 | ảnh 04, 07, 08; video | thanh phát nhỏ Hai khung; dòng Gọn | Thanh phát nhỏ khung đổi kênh: phần cố định 224/246dp ⇒ tên còn ~22dp ⇒ "..." (MeloTabletScreen.kt:300-322, :81); dòng Gọn 1 dòng theo đặc tả Shanling; dòng Điện thoại/Tablet/Hai khung đã 2 dòng; **không có marquee** trong mã | **Xác nhận** (thanh phát nhỏ); Gọn giữ nguyên |
| F08 | P2 | ảnh 03, 07, 08; dock FYT | Deck, khung trái, dòng bài, MediaSession | Dưới tên bài luôn là **tên kênh** (MeloStudioDeck.kt:384, MeloTabletScreen.kt:451); MediaSession `artist = fallbackArtist(tên kênh)` (TPA:599-605, 626, 680) ⇒ dock FYT hiện tên kênh như nghệ sĩ; `performer` của Telegram **không được lưu DB** (TPA:5861-5875) | **Xác nhận** |
| F09 | P0 | ảnh 08 (Deck), 02–04 (khung trái) | `MeloStudioDeck` + `MeloTabletScreen` | `MeloWaveformView` (kéo/chạm để tua) dùng đúng 2 nơi: Deck (:112-115, :283-285) và khung trái Hai khung (:102-105, :186, dạng thanh dày không có peaks) | **Yêu cầu chủ dự án** |
| T01 | — | video 0:16, 0:28, 0:52, 0:56 | — | Hiệu ứng đổi tỷ lệ của Android/launcher | **ĐÓNG**, không sửa |

---

## 3. Phương án kiến trúc UI

### 3.1 Đo đạc đúng (F03a, F03e)
- Thêm **kích thước tấm nền vật lý**: lấy **lớn nhất** giữa `display.mode.physicalWidth/Height` của display hiện tại và của `DisplayManager.getDisplay(DEFAULT_DISPLAY).mode` (API 23+; dưới API 30 dùng `windowManager.defaultDisplay`); bỏ qua giá trị 0/vô lý; dp = px / `density`. (Không dùng `getRealMetrics` làm dự phòng chính: từ API 31 nó bị giới hạn theo max bounds của app.) Đưa vào `DisplaySpec`/`Measurements` (`panelShortDp`, `panelLongDp`).
- **Gọn chỉ khi máy nhỏ và không chia màn**: `panelCompact = !isInMultiWindowMode && panelShort < 600 && panelLong < 700` (Shanling 768×1280@320 ⇒ 384×640 ⇒ có; FYT 1920×1200@254 ⇒ 1209×756 ⇒ không; Sony ⇒ không). Điều kiện `!isInMultiWindowMode` là chốt chặn nếu FYT chạy mỗi thẻ cửa sổ như một display riêng (khi đó `getMode` có thể trả cỡ thẻ) — **[CẦN XÁC NHẬN] bằng dòng chẩn đoán**. Không dùng tên máy.
- `MeloCompactPolicy.shouldUseCompact(AUTO)` dùng `panelCompact` thay vì `maximumWindowMetrics`.
- Chẩn đoán thêm: `displayId`, mode hiện tại, `supportedModes`, `isInMultiWindowMode`.
- Màn "Thông tin màn hình" (`MeloUiLabActivity.infoLines` 215-233): thêm dòng **Tấm nền (vật lý) px/dp**, **Cửa sổ tối đa (maximumWindowMetrics)**, **Vị trí cửa sổ (bounds.top/left)**, **Chế độ nhiều cửa sổ (isInMultiWindowMode)**, **Insets trên/dưới** của `statusBars`, `navigationBars`, `captionBar`, `displayCutout`, `systemGestures`, `tappableElement` (đọc từ `rootWindowInsets` sau khi gắn view), **Bố cục thực tế đang dùng** (tính như TPA, gồm Gọn/Car/Cũ). Lab không khai báo `configChanges` nên thường được tạo lại khi đổi cỡ; vẫn thêm OnLayoutChangeListener để **đo lại** phòng khi Android không tạo lại, và vì `rootWindowInsets` chỉ có sau khi view gắn vào cửa sổ (không đổi manifest). "Sao chép số đo" chép cả các dòng mới.

### 3.2 Chọn bố cục theo cửa sổ thực tế (F03b-c, F02)
`MeloScreenPolicy.chooseLayout(widthDp, heightDp, carMode, panelCompact)`:
1. `carMode` ⇒ CAR.
2. `panelCompact && w < 600 && h < 700` ⇒ COMPACT (chỉ máy nhỏ như Shanling).
2b. `w < 600 && h < 480` ⇒ COMPACT như **hiện nay** (điện thoại chia màn rất thấp: S25 384×~400, Sony 360×~400, ngang chia 416×384 — vẫn ra giao diện cũ vì `newLayer(COMPACT)` = null; không đổi hành vi, tránh bố cục Điện thoại không còn chỗ cho danh sách).
3. `w < 600` ⇒ PHONE_PORTRAIT (**FYT 1/3 và 1/2** ⇒ `MeloPhoneScreen`: danh sách + Thanh đáy gộp luôn hiện, tên bài 2 dòng).
4. `w >= TWO_PANE_PLAYER_MIN_WIDTH_DP` (**960**) ⇒ TWO_PANE (**FYT toàn màn 1179dp**, Y700 ngang).
5. `w > h && h < LIST_FIRST_MIN_HEIGHT_DP` (**560**) ⇒ TWO_PANE (máy ngang thấp: S25 832×384, Sony ~912×411 — **giữ như cũ**).
6. còn lại ⇒ TABLET_PORTRAIT = "danh sách + Thanh đáy gộp" (**FYT 2/3 = 782×642** và cửa sổ cao 600–959dp): bỏ khung trình phát lớn, danh sách rộng, điều khiển ở Thanh đáy (cao 96dp, ⏮/⏭ 56dp, ▶ 64dp).
- Ngưỡng 960/560/480 là hằng số một chỗ; tinh chỉnh sau QA FYT **[CẦN XÁC NHẬN]**. Bảng kiểm thử ở mục 6. Y700 chia trên/dưới (~800×620) cũng chuyển TWO_PANE → TABLET_PORTRAIT (đúng ý "chia đôi ưu tiên danh sách").
- **Một hàm profile duy nhất** `MeloLibraryPolicy.profileFor(smallestWidthDp)` (như TPA:4090) cho lớp, Lab và Studio Deck (bỏ `chooseLayout`+`MeloScreenPolicy.profileFor` ở `MeloStudioDeck.kt:102-105`) để vùng sw 560–599 không lệch TABLET/PHONE giữa các màn; profile nằm trong khóa lớp.
- Bố cục Điện thoại khi cửa sổ thấp: `MeloSourceCardPolicy.rowVisible` ẩn hàng thẻ 88dp ở PHONE_PORTRAIT khi `windowHeightDp < 700` (FYT 388/592×642 còn đủ chỗ cho danh sách; Sony dọc ~840dp vẫn có thẻ; lối vào Nhạc trên máy của E94 vẫn qua menu ⋮).
- Thanh đáy ở 388dp: nhãn tab "Thư viện/Telegram" không vừa ô ~44dp ⇒ dùng `MeloBottomBarPolicy.tabWidthDp` (:98) để **chỉ hiện biểu tượng** khi ô < 60dp (giữ contentDescription).
- `showNewLayer` truyền `panelCompact`; COMPACT + Gọn tắt ⇒ vẫn giao diện cũ như hiện nay (Shanling không đổi hành vi).
- `maybeAskCarMode` (TPA ~4024) **giữ nguyên số đo cũ**, không chuyển sang tấm nền (tấm nền FYT 1209dp ≥ 1200 sẽ bật hộp "Bật Car Mode?" — ngoài phạm vi).

### 3.3 Giữ trạng thái khi đổi tỷ lệ (F03d)
- Khóa lớp Melo **không chứa kích thước px** khi loại lớp và profile không đổi: `"new:$layer:$profile:$densityDpi:$fontScale"` ⇒ đổi tỷ lệ trong cùng loại lớp **không dựng lại màn** (giữ cuộn, ô tìm, nhóm mở rộng); view tự co giãn (đã kiểm: art-fit `MeloTabletScreen.kt:215-217`, đầu nhóm `MeloLibraryPane.kt:112`, thẻ `:115-117`, hàng thẻ đọc lại `screenHeightDp` `:318`). Nhánh dùng lại (TPA:4084-4086) phải gọi thêm `ViewCompat.requestApplyInsets(meloLayer)` để tính lại insets/dải tay nắm.
- Khi buộc phải đổi loại lớp (vd. 1179 → 782dp): host giữ **mốc cuộn** (songKey dòng đầu + offset) khi `pane.detach()` và khôi phục ở `attach()` nếu cùng nhóm (pure `MeloLibraryPolicy.anchorIndex(rows, key)`); `MeloLibraryPane` khởi tạo ô tìm từ `snapshot.query` (hết cảnh "ô trống nhưng vẫn lọc").
- Trình phát/hàng đợi/kênh đang mở/chế độ đã nằm ở Activity và service ⇒ không mất (đã đối chiếu: TPA 4271-4323). Studio Deck vẫn tạo lại khi đổi cấu hình (TPA:403-415) nhưng điều khiển được ghim (3.4) nên không phụ thuộc vị trí cuộn.

### 3.4 Đang phát: bỏ sóng âm, ghim điều khiển (F09, F01)
- **Xóa `MeloWaveformView`** khỏi `MeloStudioDeck` và `MeloTabletScreen`; **không thay bằng thanh tua khác**. Giữ chữ thời gian "06:17 / 2:32:28", nút **−30s / +60s** (Deck), ⏮/⏸/⏭, `host.seekTo` và seek của Media3/MediaSession (không đụng service).
- Deck **dạng dọc**: tách **"bến điều khiển" ghim đáy** ngoài `ScrollView` gồm dòng thời gian + hàng −30s/⏮/⏸/⏭/+60s, **chiều cao cố định** (không nằm trong `applyTight`); phần cuộn chỉ còn ảnh bìa, tên, nguồn, viên chất lượng, Đảo/Lặp/Lưu, hàng đợi. Art-fit dọc: `fitScroll = scroll`, `fitColumn` = **khối trình phát** mới (topBar, ảnh, tên, nguồn, viên, ghi chú, Đảo/Lặp/Lưu) — **không** dùng cả cột (`:171`, sẽ tính cả 12 dòng hàng đợi ⇒ ảnh luôn tụt về `rowArt`); bỏ listener 0,45 (`:184-186`); fit lại khi đổi cả rộng lẫn cao; tái dùng `MeloArtFit.restHeight`/`MeloNowPlayingPolicy.artSide`, tối thiểu `rowArt`.
- Deck **dạng ngang**: cột trái = LinearLayout dọc [ScrollView weight 1][bến điều khiển]; `fitScroll` là ScrollView bên trong (`:215`).
- `applyTight` (`:230-240`): bỏ `waveform` và dòng `:239`, bỏ `transportRow` khỏi danh sách (bến có chiều cao cố định; nếu bến không phải `LinearLayout.LayoutParams` thì dòng `:236` sẽ crash ⇒ đổi tương ứng).
- Hàng Đảo/Lặp/Lưu của Deck (`:309-313`): ở ×1,25 "↻ Lặp: một bài" bị cắt trong ô ~105dp ⇒ dùng `MeloNowPlayingPolicy.extrasStacked` + `repeatChipLabel` như `MeloTabletScreen.kt:254-258`.
- Deck chọn ngang/dọc bằng quy tắc riêng (không còn bám `chooseLayout == TWO_PANE`, MeloNowPlayingPolicy.kt:34-37): **ngang khi `w > h && w >= 600`** ⇒ FYT 782×642 vẫn Deck ngang (người chơi trái, hàng đợi phải như ảnh 08); 388/592dp ⇒ Deck dọc có bến ghim.
- Cỡ nút: tối thiểu 48dp; **56dp** khi cửa sổ cao ≥ 600dp (pure `MeloNowPlayingPolicy.transportHeightDp(windowHeightDp)`); ▶ giữ 64/72dp.
- Bỏ phân tích sóng âm (CPU/pin): thay `startAnalysis` (`:507-536`) bằng hai việc nhẹ trên worker có sẵn khi có tệp đã tải: (a) **chỉ với bài lossless** gọi `MeloWaveform.probeBitDepth(file)` (như Gọn, MeloCompactScreen.kt:526-549); (b) **với mọi định dạng** khi `now.art == null` vẫn chạy `embeddedCover` (không để MP3/M4A mất ảnh bìa nhúng) ⇒ **sửa luôn lỗi mất viên Hi-Res khi mở lại bài** (đọc cache trả bitDepth null, MeloStudioDeck.kt:520). Xóa các chỗ `:63, :77-78, :112-115, :283-285, :356, :362, :378, :386`; Tablet: `:55, :102-105, :186, :275 (danh sách applyTight), :446, :453`.
- Xóa mã chết: `MeloWaveformView.kt`; trong `MeloWaveform.kt` bỏ `Result/cacheFile/loadCached/saveCached/analyze/slicePeak/isFloatOutput/bufferPeak` (giữ `probeBitDepth`, `sourceBitDepth`); trong `MeloNowPlayingPolicy` bỏ `WAVEFORM_BUCKETS/bucketPeaks/seekFraction/knobCenter/waveformCacheName` và test tương ứng. **Không xóa** thư mục cache `melo_waveform` cũ (dữ liệu dẫn xuất nhỏ; không có lệnh xóa dữ liệu).
- **Giữ nguyên** SeekBar của Gọn (Shanling, không có nút ±), Car Mode và hộp Đang phát cũ: không phải sóng âm, không chạy trên FYT sau 3.2 — **quyết định D1**.

### 3.5 Khung trái Hai khung (F02, F07)
- `quickSwitchVisible` thêm điều kiện **có ít nhất 1 kênh hợp lệ**; rỗng ⇒ hiện trình phát lớn thay vì khoảng trống.
- Thanh phát nhỏ khung đổi kênh: hai hàng — [ảnh 48dp | tên bài **2 dòng**] trên, [⏮ ⏸ ⏭] dưới, nút ≥ 48dp.

### 3.6 Vùng an toàn trên cùng (F04)
- Ưu tiên insets: lớp Melo và các dialog (Deck, hàng đợi cũ) cộng `captionBar` (đã nằm trong `systemBars`) như hiện nay.
- Thêm **dải tay nắm đo được** (pure `WindowInsetsPolicy.handleBandPx(topDp, inMultiWindow, statusTopPx, captionTopPx, setting, bandPx)`): ở chế độ **Tự động** chừa **24dp** khi `0 < topDp ≤ 80 && isInMultiWindowMode && statusTop == 0 && captionTop == 0`; `topDp` lấy bằng `v.rootView.getLocationOnScreen()` trong listener (đúng cả trong dialog). Điện thoại toàn màn, Shanling (top = 0), cửa sổ dưới của chia trên/dưới (top lớn hơn 80dp) không bị ảnh hưởng. Không dùng tên máy FYT.
- Công tắc thoát hiểm trong "Cài đặt giao diện (thử nghiệm)": **"Chừa chỗ nút ••• hệ thống: Tự động / Luôn chừa / Không chừa"** (prefs `melovault_ui_lab`, mặc định Tự động) để chủ dự án chỉnh ngay trên FYT nếu quy tắc tự động sai.
- Áp dụng ở: listener Gọn (TPA:4057-4062, **cộng** vào top vì listener này thay padding), `installLayerInsets` (TPA:4111-4119), listener Deck (`MeloStudioDeck.kt:161-165`), hai hộp hàng đợi cũ (TPA:3315-3330, 3358-3373), `applyRootWindowInsets` (TPA:813-823, màn đăng nhập cũ). **Chưa phủ (ghi nhận):** `NowPlayingFullScreenDialog.kt:279-300` (file cấm sửa) và lớp Car (TPA:4122-4146, không có listener insets).
- Hộp hàng đợi cũ (mở từ "Xem cả hàng đợi"/"☰ Hàng đợi"): chiều cao dòng cố định dp(56)/dp(60)/dp(47) (TPA:3383-3392) cắt dòng thứ hai ở ×1,25 ⇒ WRAP_CONTENT + `minHeight`.
- Dòng chẩn đoán 3.1 cho phép xác nhận trên FYT; nếu FYT có báo caption inset thì dải tự về 0.

### 3.7 Số bài theo kênh (F05) — không đổi schema, không ghi DB
- Thêm truy vấn **chỉ đọc** `getLiveSourceCounts(accountId): Map<Long, Int>` = `SELECT chat_id, COUNT(*) FROM telegram_songs WHERE account_id=? AND is_deleted=0 GROUP BY chat_id` (DAO interface + `AndroidTelegramLibraryDatabase` + `InMemoryTelegramLibraryDao`). Không sửa `recomputeSourceTrackCounts`, không ghi `telegram_sources`, không đổi đồng bộ. Chỉ mục sẵn có `idx_tg_songs_chat (account_id, chat_id, is_deleted)` (TelegramLibrarySql.kt:73) phủ đúng truy vấn ⇒ quét chỉ mục, không cần chỉ mục mới; chạy trên executor, tối đa một lần mỗi lần dựng danh sách kênh.
- Một helper trên executor `withLiveCounts(sources, live)` dùng ở **4 chỗ** dựng danh sách kênh: nhánh lưới (TPA ~3818: áp vào `rawSources` **trước** sắp xếp ~3836, `hideZeroSources` ~3838, `activeCount` ~3840 và lưới bento ~3876), `loadDatabaseIndex` (~5627-5636), `requestMeloChannels` (4246-4261), `reloadIndexedSourcesFromDb` (5671-5689, chạy khi đồng bộ Completed — nếu bỏ sót sẽ ghi đè số sống bằng số lưu). Số bài hiển thị = số **sống**; kênh có bài nhưng chưa có dòng nguồn ⇒ thêm với tên `sourceNames`/"Nguồn Telegram …". Trong lúc đồng bộ: làm mới `meloChannelRows` có giới hạn (≥ 10 s/lần, khi một trang được ghi) vì hiện không có gì làm mới (`:4247`). ⇒ hết "chưa có nhạc" giả, khung đổi kênh có dữ liệu (F02). Gọn (`MeloCompactScreen:220`) và Car (`MeloCarScreen:413`) đọc `visibleSourceCounts` nên tự đúng qua nhánh lưới.
- Không thêm `MAX(source_title)` vào truy vấn (buộc đọc bảng); test sqlite-jdbc khẳng định `EXPLAIN QUERY PLAN` dùng `idx_tg_songs_chat`.
- Chẩn đoán ở "Thông tin màn hình" (mục Thư viện): tổng bài, số kênh có `track_count > 0` (lưu) vs có bài thật (sống), đồng bộ hoàn tất lần cuối (`telegram_sync_state.last_sync_completed_at_ms`) — chỉ số, không tên. `MeloUiLabActivity` không truy cập DB: TPA giữ các số này (tính lúc dựng danh sách kênh, trên executor) và truyền qua Intent extras khi mở lab (như `EXTRA_SONG_COUNT` hiện có, TPA:4496-4497); thiếu số ⇒ lab ghi "chưa có số".
- Định dạng số: `TelegramLibrarySyncDisplayPolicy` (:39-52) và `TelegramVaultBentoPolicy` (:82, 96) dùng dấu chấm như `MeloComponentPolicy.formatCount` ("29.551", không "29,551").

### 3.8 Thẻ Telegram khi đang mở kênh (F06)
- Giữ chức năng điều hướng. Khi đang mở một kênh: chi tiết thẻ thành **"Về tất cả · 42.734 bài"** (+ "· đã tải N"), contentDescription "Về toàn bộ Telegram, 42.734 bài". Khi không mở kênh: như cũ. (Pure `MeloSourceCardPolicy.cards(..., channelOpen)`.) Chữ cuối cùng **[CẦN XÁC NHẬN]** qua ảnh.

### 3.9 Nghệ sĩ và nguồn (F08)
- Trên màn Đang phát (Deck, khung trái Hai khung): dòng dưới tên ghi rõ **"Kênh: …"** cho Telegram; với bài local (E94): **"Nghệ sĩ: …"** khi MediaStore có nghệ sĩ thật, **"Thư mục: …"** khi không có (không dán nhãn thư mục là nghệ sĩ). Pure `MeloNowPlayingPolicy.sourceLine(...)`.
- MediaSession (dock FYT, màn khóa, Bluetooth): **quyết định D2** — đề xuất `artist` = nghệ sĩ thật (performer Telegram khi có, nghệ sĩ MediaStore với local) hoặc để trống; tên kênh/thư mục đưa sang `albumTitle`. Nếu chủ dự án chọn giữ nguyên thì bỏ bước S7b.

---

## 4. Danh sách file (sau khi E94 đã merge — số dòng hiện tại để tham chiếu)

| File | Thay đổi |
|---|---|
| `MeloScreenPolicy.kt` | `chooseLayout(..., panelCompact)`, hằng `TWO_PANE_PLAYER_MIN_WIDTH_DP=960`, `LIST_FIRST_MIN_HEIGHT_DP=560`, `panelCompact(...)`; `DisplaySpec` thêm kích thước tấm nền |
| `MeloCompactPolicy.kt` | `shouldUseCompact` AUTO dùng tấm nền |
| Mọi nơi gọi `chooseLayout` (6 chỗ: `MeloCompactPolicy.kt:42`, `MeloNowPlayingPolicy.kt:35`, `MeloStudioDeck.kt:103`, `MeloUiLabActivity.kt:95`, `MeloUiPreviewActivity.kt:48`, TPA:4077) | truyền `panelCompact` **tường minh** (không giá trị mặc định); `MeloUiPreviewActivity` dùng `panelCompact` của khung giả lập (Shanling giả lập vẫn ra Gọn); `MeloStudioDeck.kt:102-105` bỏ `chooseLayout`, dùng `MeloLibraryPolicy.profileFor(smallestWidthDp)` |
| `MeloUiLabActivity.kt` | `realMeasurements` đo tấm nền; dòng chẩn đoán (3.1, 3.7); đo lại khi đổi cỡ |
| `MeloLibraryPolicy.kt` | `quickSwitchVisible(..., rowCount)`, `anchorIndex(...)` |
| `MeloLibraryPane.kt` | ô tìm từ `snapshot.query`; lưu mốc cuộn ở `detach()` (163-167), khôi phục thay cho `resetScroll()` lần đầu khi cùng `groupKey` (182-188) |
| `MeloScreenHost.kt` | thêm `saveListAnchor(groupKey, songKey, offsetPx)` / `listAnchor()` (host giữ trong bộ nhớ, không lưu đĩa) |
| `MeloStudioDeck.kt` | bỏ sóng âm; bến điều khiển ghim (dọc + ngang); probe bit-depth + ảnh bìa nhúng; dòng "Kênh:/Nghệ sĩ:/Thư mục:" |
| `MeloTabletScreen.kt` | bỏ thanh `MeloWaveformView`; khung đổi kênh rỗng ⇒ trình phát; thanh phát nhỏ 2 hàng; dòng nguồn |
| `MeloNowPlayingPolicy.kt` | `transportHeightDp`, `sourceLine`; xóa helper sóng âm |
| `MeloWaveform.kt` | chỉ giữ `probeBitDepth`/`sourceBitDepth` |
| `MeloWaveformView.kt` | **xóa** |
| `WindowInsetsPolicy.kt` | `handleBandPx(...)` (gồm cài đặt Tự động/Luôn/Không) |
| `MeloSourceCardPolicy.kt` | nhãn thẻ khi đang mở kênh; `rowVisible` ẩn ở PHONE_PORTRAIT khi cửa sổ < 700dp cao |
| `MeloBottomBar.kt`, `MeloBottomBarPolicy.kt` | tab chỉ biểu tượng khi ô tab < 60dp |
| `TelegramLibrarySyncDisplayPolicy.kt`, `TelegramVaultBentoPolicy.kt` | dấu chấm ngăn nghìn |
| `TelegramLibraryDao.kt`, `AndroidTelegramLibraryDatabase.kt`, `InMemoryTelegramLibraryDao.kt` | **chỉ thêm** `getLiveSourceCounts` (đọc) |
| `TelegramPreviewActivity.kt` | `applyMeloLayer`/`showNewLayer` (tấm nền, khóa lớp, `requestApplyInsets` khi dùng lại), dải tay nắm ở listener Gọn/`installLayerInsets`/`applyRootWindowInsets`/hai hộp hàng đợi cũ, dòng hộp hàng đợi WRAP_CONTENT, `withLiveCounts` ở 4 chỗ dựng kênh + làm mới có giới hạn khi đồng bộ, extras chẩn đoán khi mở Lab, (S7b) `mediaItem` artist/albumTitle |
| Test | `MeloScreenPolicyTest`, `MeloCompactPolicyTest` (:16-43), `MeloLibraryPolicyTest` (:36, :43 truyền `panelCompact`), `MeloNowPlayingPolicyTest` (xóa :52-68, :121-125 cùng mã sóng âm; sửa :25-29 `deckLayout`), `WindowInsetsPolicyTest`, `TelegramLibrarySyncDisplayPolicyTest`, `MeloSourceCardPolicyTest`, `MeloBottomBarPolicyTest`, test DAO in-memory + JDBC (kể cả `EXPLAIN QUERY PLAN`) cho truy vấn số sống |

**Không được đổi:** `ShareablePlaybackService.kt`, `TelegramOwnerAudioDataSource.kt`, `TelegramProgressiveCache.kt`, schema/`TelegramLibrarySql` DDL, `recomputeSourceTrackCounts`, `TelegramSyncCoordinator.kt`, `TelegramSongKey.kt`, `TelegramSavedPlaylistStore.kt`, `ShareableTdlibOwner.kt`, `TelegramOfflineLibrary.kt`, `TelegramOfflinePlayPolicy.kt` (phát một chạm đã nghiệm thu), hàng đợi/`TelegramQueueRetentionPolicy`/`TelegramContiguousQueuePolicy`, `MeloLocalSourcePolicy.kt`/`MeloLocalLibrary.kt` (E94, trừ nhãn hiển thị ở host), `MeloCompactScreen.kt`, `MeloCarScreen.kt`, `NowPlayingFullScreenDialog.kt`, mọi `AndroidManifest.xml`, `build.gradle` (versionCode), `app/src/main/**`, `app/src/personal/**`, `CLAUDE.md`, `HANDOFF.md`, `docs/workflow/**`.

Ước lượng: ~+700/−400 dòng production (xóa sóng âm ~−350). Không phải làn nhanh.

---

## 5. Trình tự cho Sonnet (commit theo giai đoạn để rollback từng phần)

**S0 — Cổng phụ thuộc.** Chỉ bắt đầu khi ChatGPT ghi E94 **PASS** và E94 đã merge vào `feature/e93-03d-y700-offline-cockpit`, và ChatGPT/chủ dự án chốt baseline. Kiểm Git như CLAUDE.md (cây sạch, không agent khác). Trong workspace chính `D:\Bots\MeloVault-E91-Shareable`, mở `dot/E95-FYT-UI-OPTIMIZATION` rồi `git merge --no-ff <HEAD tích hợp đã chốt>` (nhánh E95 chỉ có tài liệu nên không xung đột); ghi SHA baseline vào báo cáo. Sai bất kỳ điều kiện ⇒ DỪNG.

**S1 — Quy tắc làm việc.** Mỗi giai đoạn S2–S7 viết **logic thuần + test của riêng nó trước** (các policy ở mục 4), chạy test liên quan, rồi mới nối view; **mỗi commit A–E phải tự biên dịch và test xanh** (đổi chữ ký `chooseLayout` thì sửa cả 6 nơi gọi trong cùng commit A) để revert từng commit không làm vỡ build.

**S2 — Đo + chọn bố cục + giữ trạng thái (F03).** `MeloScreenPolicy`/`MeloCompactPolicy`/`MeloNowPlayingPolicy.deckLayout` (quy tắc ngang `w > h && w >= 600`) và 6 nơi gọi `chooseLayout`; `MeloUiLabActivity` (tấm nền, chẩn đoán, đo lại); TPA `applyMeloLayer`/`showNewLayer`/khóa lớp (+ `requestApplyInsets` khi dùng lại); `MeloLibraryPane` (ô tìm, mốc cuộn); hàng thẻ ẩn khi Điện thoại thấp; tab chỉ biểu tượng khi hẹp. **Commit A** `feat(shareable): window-size layouts and panel-aware compact rule (E95 F03)`.

**S3 — Đang phát (F09, F01).** `MeloStudioDeck`, `MeloTabletScreen` (bỏ thanh), xóa `MeloWaveformView.kt` và mã/test sóng âm. **Commit B** `feat(shareable): remove waveform scrubber and pin transport controls (E95 F09 F01)`.

**S4 — Khung trái Hai khung (F02, F07).** `MeloTabletScreen` khung đổi kênh + thanh phát nhỏ. **Commit C** (gộp với S5 được).

**S5 — Vùng an toàn (F04).** `handleBandPx` ở listener Gọn, `installLayerInsets`, `applyRootWindowInsets`, Deck, hai hộp hàng đợi cũ (+ dòng WRAP_CONTENT); công tắc "Chừa chỗ nút •••" trong `MeloUiLabActivity`. **Commit C** `feat(shareable): FYT-safe top band and quick-pane fallback (E95 F02 F04 F07)`.

**S6 — Dữ liệu (F05, F06).** DAO đọc số sống + 3 chỗ dựng kênh + chẩn đoán thư viện; nhãn thẻ; định dạng số. **Commit D** `fix(shareable): live channel counts while syncing and clearer source labels (E95 F05 F06)`.

**S7 — Nhãn nguồn (F08).** `sourceLine` ở Deck/khung trái. **S7b chỉ làm khi chủ dự án đã chốt D2 = "đổi" (mặc định KHÔNG làm):** `mediaItem()` + `QueueTrack.artist` đổi artist/albumTitle. **Commit E** `feat(shareable): separate artist from channel labels (E95 F08)`.

**S8 — Kiểm toàn bộ.** `.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain`; `.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain`; `git diff --check`. Skipped phải giữ **21** (hoặc giải thích). Soát `git diff --stat` chỉ gồm file mục 4.

**S9 — Gate APK** (không bump version trừ khi ChatGPT/chủ dự án quyết): bundle SHA256 → Tiny clone cô lập, nhánh `qa/E95-FYT-UI-OPTIMIZATION`, test + `assembleShareableDebug` có Telegram config; kiểm package/vc/signer/SHA/bytes. APK Ni (thiếu config) không cài.

**S10 — QA Sony (Tiny)**, **S11 — QA Shanling (Ni)** theo mục 7. **S12 — Gói FYT cho chủ dự án** (mục 7.1): Sonnet không cài FYT; chuẩn bị bảng kiểm trong báo cáo.

**S13 — Báo cáo** `docs/plans/E95-FYT-UI-OPTIMIZATION-bao-cao.md`: bảng F01–F09 trước/sau, ảnh Sony/Shanling, `SHANLING_SCREEN_END`/`SONY_SCREEN_END`, mục **"APK QA cần ChatGPT bàn giao"** (CLAUDE.md mục 9), nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**, danh sách thiết bị đã/chưa kiểm. Commit, **DỪNG** chờ ChatGPT.

**Rollback:** `git revert` từng commit A–E trên nhánh dot (chưa merge); thiết bị `install -r` lại APK trước đó (cùng vc, không gỡ).

**Sonnet phải DỪNG và báo khi:** E94 chưa PASS/merge hoặc baseline chưa chốt; cây bẩn/agent khác; cần đổi schema, ghi DB, sửa `recomputeSourceTrackCounts`/đồng bộ/service/TDLib/`songKey`/hàng đợi/một chạm, manifest, versionCode; Gọn trên Shanling bị đổi; không tách được bến điều khiển mà phải thêm thanh tua; diff production vượt ~1.200 dòng thay đổi hoặc phải sửa file ngoài mục 4; signature/vc/package/Telegram config sai; ADB không thấy/unauthorized; máy đang phát nhạc của chủ dự án lúc cần cài; FATAL/ANR; màn không tắt được.

---

## 6. Unit tests và regression tests

- **`MeloScreenPolicyTest` — bảng bố cục:** FYT 1179×642 ⇒ TWO_PANE; 782×642 ⇒ TABLET_PORTRAIT; 592×642, 388×642 (panel lớn) ⇒ PHONE_PORTRAIT; Shanling 384×574 (panelCompact) ⇒ COMPACT; S25 832×384 ⇒ TWO_PANE; Sony ngang 840×360 ⇒ TWO_PANE; Sony dọc 360×840 ⇒ PHONE_PORTRAIT (số theo `MeloLibraryPolicyTest.kt:39-40`); điện thoại chia màn 384×400, 360×400, 416×384 ⇒ COMPACT (giữ như cũ, quy tắc 2b); Y700 1280×800 ⇒ TWO_PANE, 800×1280 ⇒ TABLET_PORTRAIT, 853×800 và 800×620 ⇒ TABLET_PORTRAIT; carMode ⇒ CAR; biên 959/960, 559/560, 479/480, 599/600. `panelCompact`: Shanling true; FYT/Sony/Y700/S25 false; **bất kỳ máy nào khi `inMultiWindow` ⇒ false**; giá trị mode 0/vô lý ⇒ dùng số còn lại. **Test cũ giữ nguyên kỳ vọng** (`layoutThresholdsFollowSpecTable`, `measuredS25UltraUsesPhonePortraitAndTwoPaneLandscape`, `landscapeBetween600And839IsTwoPanePortraitStaysTablet`, `y700LikeDisplay…` — MeloScreenPolicyTest.kt:53-145), chỉ thêm đối số `panelCompact` (ca `(599, 699)` ⇒ COMPACT với `panelCompact = true`); thêm ca `(599, 699, panelCompact = false)` ⇒ PHONE_PORTRAIT.
- **`MeloCompactPolicyTest`:** AUTO theo tấm nền; ON/OFF/EMULATE không đổi.
- **`MeloLibraryPolicyTest`:** `quickSwitchVisible` false khi không có kênh; `anchorIndex` tìm đúng/không thấy ⇒ 0.
- **`MeloNowPlayingPolicyTest`:** xóa test sóng âm cùng mã; `deckLayout` mới (782×642 ⇒ ngang, 1179×642 ⇒ ngang, 912×411 ⇒ ngang, 592×642/388×642/411×860 ⇒ dọc); thêm `transportHeightDp` (≥600 ⇒ 56, thấp ⇒ 48), `sourceLine` (Telegram "Kênh:", local nghệ sĩ thật "Nghệ sĩ:", local không nghệ sĩ "Thư mục:", không bao giờ suy nghệ sĩ từ kênh), quyết định probe bit-depth chỉ cho lossless.
- **`WindowInsetsPolicyTest`:** dải tay nắm (Tự động): caption > 0 ⇒ 0; status > 0 ⇒ 0; top 0 ⇒ 0; top > 80dp ⇒ 0; không multi-window ⇒ 0; 0 < top ≤ 80 & multi-window & status 0 & caption 0 ⇒ band; "Luôn chừa" ⇒ band; "Không chừa" ⇒ 0.
- **`MeloSourceCardPolicyTest`/`MeloBottomBarPolicyTest`:** hàng thẻ ẩn ở PHONE_PORTRAIT cao 642, hiện ở 840; tab chỉ biểu tượng khi ô < 60dp.
- **`TelegramLibrarySyncDisplayPolicyTest`:** "29.551", "42.734"; cập nhật kỳ vọng cũ có dấu phẩy.
- **`MeloSourceCardPolicyTest`:** nhãn "Về tất cả · …" khi đang mở kênh; như cũ khi không.
- **Số sống:** InMemory DAO (bỏ bài `isDeleted`, nhiều kênh, kênh chưa có dòng nguồn) + JDBC sqlite (thư viện test đã có) chạy đúng câu SQL; khẳng định **không có lệnh ghi**.
- **Hồi quy toàn bộ:** `TelegramOfflinePlayPolicyTest` (một chạm), `TelegramSongKeyTest`, `MeloBottomBarPolicyTest`, các test E94 (`MeloLocalSourcePolicyTest`, `MeloLocalLibraryTest`), `LocalMediaRepositoryTest` — xanh; Debug + Release; skipped 21.

---

## 7. Device QA

### 7.0 Quy tắc chung
Như CLAUDE.md và `docs/workflow/SCREEN_LIFECYCLE_QA.md`: xác minh `adb devices -l` + getprop **trên đúng máy**; chỉ `adb install -r`; cấm uninstall/`pm clear`/`pm grant|revoke`/xóa dữ liệu/đăng xuất/máy bay/đổi brightness-timeout-AOD/cài FYT; Shanling/Sony **chỉ bật màn khi thao tác**, `keyevent 223` + `Display State=OFF` trong `finally`; ảnh `adb exec-out screencap -p` vào `docs/plans/artifacts/E95-FYT-UI-OPTIMIZATION/` (không commit); không tuyên bố âm thanh thật. Không dùng `wm size`/`wm density` trên Sony trừ khi chủ dự án cho phép trong chat (**D5**).

### 7.1 FYT 7870 — thiết bị nghiệm thu chính (chủ dự án thực hiện khi **xe đỗ an toàn**; Sonnet không có ADB FYT, không cài FYT)
ChatGPT gửi link APK owner-only; chủ dự án cài đè và quay màn hình/ chụp ảnh. Mỗi bước ghi "Thông tin màn hình → Sao chép số đo" (có tấm nền, cửa sổ, insets, bố cục thực tế).

| # | Trạng thái | Thao tác | Kỳ vọng (PASS khi thấy) |
|---|---|---|---|
| FYT-1 | Toàn màn | Mở Thư viện, Telegram, Playlist; mở Đang phát | Hai khung: trình phát trái + danh sách; khung trái **không có thanh sóng âm**, có giờ "mm:ss / h:mm:ss"; "•••" không đè chữ |
| FYT-2 | Chia 2/3 với Maps | Thư viện, mở 1 kênh, Telegram, Playlist | **Danh sách rộng + Thanh đáy** (⏮ ⏸ ⏭ luôn thấy); không khoảng trống lớn bên trái; tên bài 2 dòng; thẻ Telegram ghi "Về tất cả · …" khi đang trong kênh |
| FYT-3 | Chia 1/2 và 1/3 | Thư viện, Telegram, Đang phát, cuộn hàng đợi xuống cuối | Bố cục **Điện thoại** (không phải Gọn), Thanh đáy luôn thấy; Đang phát: hàng −30s/⏮/⏸/⏭/+60s **luôn trên màn khi cuộn**; không sóng âm |
| FYT-4 | Đang phát bài dài | Đổi tỷ lệ 1/3 → 1/2 → 2/3 → toàn màn và ngược lại; đổi vị trí trái/phải | Nhạc không dừng/không đổi bài; hàng đợi giữ nguyên; kênh/chế độ/từ tìm giữ nguyên; vị trí cuộn giữ khi cùng loại bố cục |
| FYT-5 | Mọi tỷ lệ | ⏮, ⏭, ⏸/▶, −30s, +60s (và nút vô lăng nếu có) | Mỗi nút có tác dụng, không phải cuộn để bấm; nút ≥ 48dp (≈7,6 mm) |
| FYT-6 | Telegram | Sau khi đồng bộ (đang chạy và **sau khi hoàn tất**): lưới kênh, "Các kênh chưa có nhạc", khung đổi kênh ở toàn màn | Kênh có bài hiện đúng số (kênh EMI ≈ 77), không bị xếp "chưa có nhạc"; khung đổi kênh có danh sách; chẩn đoán Thư viện khớp |
| FYT-7 | Mọi màn | Nhìn dòng dưới tên bài, dock FYT | "Kênh: …" không giả làm nghệ sĩ; dock theo quyết định D2 |
| FYT-8 | Hàng đợi | Mở "Xem cả hàng đợi"/hộp hàng đợi ở 1/3 và toàn màn | Tiêu đề, nút "Xong", các bài không bị "•••" che; dùng hết chiều rộng |

### 7.2 Sony Xperia 5 II (Tiny, `QV725WEQ54`) — Sonnet tự làm
| # | Màn | Thao tác | Kỳ vọng |
|---|---|---|---|
| S-1 | ON | Dọc: Thư viện → chạm bài → mở Đang phát (Deck dọc); cuộn xuống hàng đợi | Không có sóng âm; giờ hiện; hàng −30s/⏮/⏸/⏭/+60s **ghim đáy** khi cuộn; bấm +60s ⇒ position tăng ~60 s (dumpsys) |
| S-2 | ON | Xoay ngang (Hai khung Sony, đo bằng màn chẩn đoán; test repo dùng 840×360): khung trái + Deck ngang | Hai khung như cũ (ngang thấp); khung trái không còn thanh; Deck ngang nút ghim |
| S-3 | ON | Mở một kênh ở Hai khung | Khung đổi kênh có danh sách (số sống), thanh phát nhỏ thấy tên bài |
| S-4 | ON | "Thông tin màn hình" | Có dòng tấm nền 1080×2520, cửa sổ, insets, bố cục thực tế |
| S-5 | ON | E94 hồi quy: thẻ Nhạc trên máy → phát bài local → ⏭; **tua bằng +60s/−30s** (thay Q7 cũ "chạm sóng âm") | Local phát, tua đổi position, không trộn hàng đợi; dòng "Nghệ sĩ:/Thư mục:" đúng |
| S-6 | OFF | `keyevent 87/88/127/126` | MediaSession điều khiển như cũ |
Khôi phục rotation như lúc đầu; tắt màn; ghi `SONY_SCREEN_END`.

### 7.3 Shanling M3 Ultra (Ni, `30313860180939`) — hồi quy
| # | Màn | Thao tác | Kỳ vọng |
|---|---|---|---|
| H-1 | ON | Mở app | **Vẫn Gọn** (tấm nền nhỏ), 8 dòng, 2 tab; "•••" không có ⇒ không có dải trống thừa |
| H-2 | ON | ☰ → ✓ Đã tải → chạm một lần | Phát một chạm như PASS cũ; hàng đợi theo nguồn |
| H-3 | ON | ☰ → ♪ Nhạc trên máy (E94) → phát bài local; Đang phát Gọn | Phát; SeekBar Gọn giữ nguyên (D1) |
| H-4 | OFF | `keyevent 87/88/127/126` | Điều khiển khi tắt màn như cũ |
Tắt màn, `SHANLING_SCREEN_END`.

### 7.4 Y700 / S25 Ultra
Chủ dự án tự thử nếu có thể (Y700 ngang = Hai khung, Y700 chia màn 2/3 = danh sách + Thanh đáy). Không có ⇒ **CHƯA KIỂM CHỨNG**, không chặn PASS FYT nhưng phải ghi rõ.

### 7.5 Bác sĩ tự kiểm tra (FYT 7870, xe đỗ; Sony/Shanling do Sonnet làm qua ADB)
1. **FYT, chia đôi 1/3 với Google Maps, màn Đang phát:** cuộn xuống hàng đợi ⇒ hàng −30s / ⏮ / ⏸ / ⏭ / +60s **vẫn nằm trên màn**; không còn thanh sóng âm; vẫn thấy "06:17 / 2:32:28".
2. **FYT, chia đôi 2/3, Thư viện:** danh sách chiếm gần hết bề ngang, dưới đáy có Thanh đáy (⏮ ⏸ ⏭); bên trái **không còn ô trống lớn**.
3. **FYT, kéo thanh chia 1/3 → 1/2 → 2/3 → toàn màn khi đang phát:** nhạc chạy liên tục, đúng bài, hàng đợi không đổi; kênh đang mở và từ tìm giữ nguyên.
4. **FYT, toàn màn, tab Telegram:** kênh "Nhạc Lạnh - Chillout (EMI)" hiện số bài (~77), **không** nằm trong "Các kênh chưa có nhạc"; mở kênh ⇒ khung trái có danh sách kênh; thẻ Telegram ghi "Về tất cả · …".
5. **FYT, mọi màn:** chữ đầu trang/ "Hàng đợi" không bị nút "•••" của FYT che.
6. **Shanling M3 Ultra (Sonnet qua ADB, chủ dự án xem ảnh):** vẫn giao diện Gọn 8 dòng; ☰ → ✓ Đã tải chạm một lần phát ngay; ♪ Nhạc trên máy phát bài local; tắt màn bấm ⏭ vẫn chuyển bài.

### 7.6 Bằng chứng
Sonnet: ảnh/video Sony + Shanling, dumpsys, log (chỉ số), bảng trước/sau F01–F09 (ảnh "trước" = ảnh 01–08 và mốc video của chủ dự án). FYT: video/ảnh do chủ dự án quay. ChatGPT: kiểm độc lập, upload Drive Ni N owner-only, lập `docs/plans/E95-FYT-UI-OPTIMIZATION-hinh-nghiem-thu.md` và `docs/plans/E95-FYT-UI-OPTIMIZATION-apk-test.md`; không báo upload khi chưa xác minh.

---

## 8. Điều kiện nghiệm thu, rollback

**Đạt kỹ thuật (ChatGPT):** diff chỉ file mục 4; không đổi service/schema/sync/`songKey`/hàng đợi/một chạm/TDLib/manifest/gradle; truy vấn số sống chỉ đọc; test Debug + Release xanh, skipped 21; Sony S-1…S-6 và Shanling H-1…H-4 PASS có ảnh/dumpsys; không FATAL/ANR; màn cuối OFF; báo cáo có mục bàn giao APK.
**PASS cuối:** chủ dự án duyệt FYT-1…FYT-8 qua ảnh/video thật trên FYT (xe đỗ). Không có ⇒ **BLOCKED — CHỜ CHỦ DỰ ÁN**. Không PASS FYT từ emulator/suy luận.
**REWORK:** còn sóng âm hoặc có thanh tua mới ở Đang phát; điều khiển phải cuộn mới thấy; FYT 1/2–1/3 vẫn ra Gọn hoặc giao diện cũ; Shanling không còn Gọn; nhạc/hàng đợi bị reset khi đổi tỷ lệ; số kênh vẫn 0 khi kênh có bài; nhãn kênh vẫn đóng vai nghệ sĩ trong app; hồi quy một chạm/E94/MediaSession; sửa file cấm; ghi DB.
**BLOCKED:** E94 chưa PASS/merge; thiếu Telegram config; ADB Sony/Shanling không thấy; chủ dự án chưa có điều kiện thử FYT khi xe đỗ.
**Rollback:** revert commit A–E độc lập (A: bố cục; B: sóng âm/điều khiển; C: insets/khung trái; D: số kênh/nhãn; E: nghệ sĩ); thiết bị `install -r` APK trước (cùng vc).

---

## 9. Vấn đề chưa đủ bằng chứng / cần chủ dự án quyết

- **D1** Giữ SeekBar Gọn (Shanling, không có nút ±), SeekBar Car Mode và hộp Đang phát cũ? Đề xuất **giữ** (không phải sóng âm, không dùng trên FYT sau 3.2).
- **D2** MediaSession: `artist` = nghệ sĩ thật hoặc trống, tên kênh sang `albumTitle` (dock FYT có thể chỉ hiện tên bài)? Đề xuất **đổi** để không suy đoán nghệ sĩ; nếu dock FYT xấu đi thì revert riêng commit E.
- **D3** Ngưỡng 960dp (trình phát lớn) và 560dp (danh sách trước) — chỉnh sau FYT-1…FYT-3.
- **D4** Chữ thẻ "Về tất cả · 42.734 bài".
- **D5** Cho phép tạm `wm size/density` trên Sony để giả lập cửa sổ FYT? Mặc định **không**.
- FYT có báo `captionBar` inset cho "•••" không; `maximumWindowMetrics`, `display.mode`, `displayId`, `isInMultiWindowMode` trên FYT trả gì — nhất là ở chế độ "toàn màn" trong thẻ launcher (đo bằng màn chẩn đoán). Nếu FYT toàn màn **không** là multi-window và `getMode` trả cỡ thẻ thì quy tắc vẫn đúng (1179dp ≥ 700 ⇒ không Gọn); nếu dải tay nắm Tự động không bật ở toàn màn thì chủ dự án dùng công tắc "Luôn chừa".
- DB FYT: đồng bộ đầy đủ đã từng hoàn tất chưa (chẩn đoán Thư viện); chưa có ADB/SQL trên FYT.
- FYT có ADB (USB/Wi-Fi) không — nếu có, ChatGPT/chủ dự án có thể mở rộng QA tự động sau.
- Âm thanh thật, nút vô lăng FYT, Y700/S25: chưa kiểm.

## 10. Quan hệ E94 ↔ E95

- E94 sửa: `MeloScreenHost`, `MeloSourceCardPolicy`, `MeloComponentPolicy`, `MeloComponents`, `MeloLibraryPane`, `MeloCompactScreen`, `MeloPhoneScreen`, `MeloTabletScreen`, `MeloStudioDeck`, TPA + 2 file mới. E95 sửa lại `MeloLibraryPane`, `MeloTabletScreen`, `MeloStudioDeck`, `MeloSourceCardPolicy`, TPA ⇒ **chồng file** ⇒ không phát triển song song; E95 đợi E94 merge (S0).
- E95 kế thừa toàn bộ quyết định E94 (MediaStore chỉ đọc, khóa local tách, hàng đợi một nguồn, quyền khi bấm, không đổi schema/`songKey`, QA Shanling/Sony, Drive riêng).
- E95 thay đổi nhãn local của E94: thư mục không còn hiển thị như nghệ sĩ (3.9); MediaSession theo D2.
- **E94 Q7** ("chạm sóng âm/thanh tiến trình") vẫn đúng khi chạy E94 (sóng âm còn tồn tại lúc đó); trong hồi quy E95 thay bằng tua **−30s/+60s** (S-5). Không bỏ kiểm thử phát nhạc local.
- **Phát hiện cho E94 (báo ChatGPT, không sửa nhánh E94):** Ni **không có Python** (chỉ alias Store) nên E94 mục 7.3 "Tiny không có Python ⇒ tạo trên Ni" không chạy được; Ni có **ffmpeg 9.0.1** (libmp3lame, flac, pcm_s16le/s24le) ⇒ đề nghị ChatGPT cho phép tạo 3 tệp thử bằng ffmpeg (ví dụ `ffmpeg -f lavfi -i sine=frequency=880:duration=10 -ar 96000 -ac 2 -c:a flac -sample_fmt s32 …`).
