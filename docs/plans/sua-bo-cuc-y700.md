# Kế hoạch: sửa 5 lỗi chủ dự án báo khi QA Y700 ngang bản b99

> Phiên lập kế hoạch: 18/10/2026, branch `feature/e93-03d-y700-offline-cockpit`, HEAD `83e0b80` (Beta99, vc99 / `0.6.78-beta`). Chưa sửa code. **Không đổi schema DB** (vẫn v2), không đụng `songKey`, khóa `(accountId, chatId, messageId)`, Car (`MeloCarScreen`), Gọn (`MeloCompactScreen`), giao diện "Cũ", flavor `personal`.
> Bằng chứng Sony (b99 đã cài, 25.103 bài) lưu local trên Tiny `C:\Bots\_qa_temp\PlanY700Layout_83e0b80\` (ảnh `s01…s12`, log `log_tap1..3.txt`, có tên bài riêng tư, không chép đi nơi khác).

Thứ tự ưu tiên của chủ dự án: (1) kênh "Kênh Nhạc Sưu Tầm Những Năm Về Trước" bấm bài không phản ứng (ĐÃ XÁC NHẬN còn ở b99 trên Y700); (2) màn Đang phát cột trái tràn; (3) núm thanh tua ở vị trí 0 bị cắt nửa; (4) mở một kênh thì hàng bài đầu bị cắt dưới tiêu đề nhóm; (5) tên kênh dài chồng lên nút "Mới nhất/Phát hết".

---

## Lỗi 1 — Kênh "Sưu Tầm Những Năm Về Trước": bấm bài không phản ứng (ƯU TIÊN CAO)

### Tái hiện trên Sony (18/10/2026, 14:10–14:16)
- Kênh có 761 bài (khớp số chủ dự án nói). Các bài là bản mix 3–4 giờ, **mỗi tệp ~580 MB** (MP3 · 44.1 kHz · 320 kbps; màn Đang phát hiện "Đã nhận 353,4 / 580,5 MB").
- Dọc (Điện thoại): mở kênh từ lưới Telegram, bấm bài đầu → **phát được** trong < 15 s (MediaSession `state=3`, mini player 00:26 / 3:00:00).
- Ngang (Hai khung): bấm bài thứ 2 → phát được; đổi sang BOX MUSIC HOUSE bằng khung đổi kênh rồi quay lại "Sưu Tầm…", cuộn xuống, bấm bài thứ 3 và thứ 4 → đều phát được.
- logcat: không `AndroidRuntime`/`ANR`/`FATAL`; TDLib tìm thấy tin trong chat `-100…` (kênh thật, không phải basic group cũ).
- **Kết luận: Sony KHÔNG tái hiện được.** Lỗi chỉ xảy ra trên Y700 → nguyên nhân gốc **[CẦN XÁC NHẬN]**.
- Ghi chú phụ: sau 6 phút thử, Sony đã tự tải ~1,7 GB (3 bài "Đã tải" + bài kế do "Tải trước bài kế"); bộ nhớ Sony còn 8,2 GB / 104 GB. Không xóa (cấm xóa cache).

### Điều chắc chắn từ code (vì sao "không phản ứng" thay vì báo lỗi)
Đường đi: `MeloLibraryPane` (bấm dòng) → `host.playAt` → `playShownSong` → `downloadAndPlay` (`TelegramPreviewActivity.kt:5773`) → `resolveTelegramMessage` (`getMessage`, chờ tối đa 12 s, `:4695`) → `TelegramAudioResolverPolicy.parseMessage` → `TelegramProgressiveCache.begin` → `enqueueProgressivePlayback` + `downloadFile`.
- **Mọi thông báo của đường này đi qua `showMessage()` (`:4608`) = TextView `status` của giao diện cũ, nằm dưới lớp Melo → trên màn mới người dùng không thấy gì**: cả "Đang kiểm tra tệp Telegram…", "Đang đệm nhạc…" lẫn mọi lỗi ("Không thể phát bài: …", "Quá thời gian kết nối…", "Không xác định được nguồn…", "Phiên Telegram chưa sẵn sàng", "Không tạo được bộ đệm…").
- Không có dòng `Log` nào cho lỗi `getMessage` (nhánh `kind == "error"`, `:4738`) hay kết quả `parseMessage`. Lỗi `downloadFile` của TDLib cũng không được báo lên.
- Dòng bài không đổi trạng thái khi đang chờ (chỉ đổi khi `playingKey` đổi) → trong 12 s chờ, hoặc khi lỗi, màn hình đứng yên.

### Giả thuyết cho Y700 [CẦN XÁC NHẬN — cần log từ Y700]
Xếp theo khả năng:
- **(e) Tệp rất lớn (~580 MB, 3–4 giờ):** TDLib trên Y700 tải chậm/không tải được (mạng, DC khác của kênh, bộ nhớ Y700 đầy, hoặc 2–3 luồng 580 MB cùng lúc do "Tải trước bài kế"), ExoPlayer chờ dữ liệu mãi → nhìn như không phản ứng. Kênh khác (bài 5–15 MB) không bị.
- **(d) TDLib trên Y700 chưa nạp chat này** → `getMessage` lỗi 400 ("Chat not found"/"Message not found") → lỗi im lặng.
- **(c) Dữ liệu chỉ mục trên Y700** (đồng bộ riêng từ máy đó) có `genuineMessageId <= 0` cho kênh này → "Không xác định được nguồn…" im lặng.
- (b) Tin không phải âm thanh hỗ trợ → `parseMessage` trả lỗi im lặng. Ít khả năng: Sony phát được cùng các tin này.
- (a) Basic group nâng cấp supergroup → loại: TDLib Sony thấy chat `-100…`.

### Cách sửa (2 phần; phần A làm trước, độc lập với nguyên nhân)
**A. Bỏ "im lặng" — luôn có phản hồi khi bấm bài (Melo):**
1. `MeloScreenHost`: thêm `fun playStatus(): PlayStatus?` (giai đoạn `RESOLVING` / `BUFFERING` / `ERROR` + câu tiếng Việt + `songKey`), hoặc tối thiểu callback `onPlayMessage(text, isError)`. Activity gọi khi `showMessage` được gọi từ đường phát (thay `showMessage` ở `downloadAndPlay`/`resolveTelegramMessage` bằng hàm mới `reportPlayStage(...)` vẫn ghi `status` cũ cho giao diện Cũ).
2. Màn Melo (Điện thoại/Tablet/Hai khung qua `MeloLibraryPane`, và Gọn nếu dùng chung host — không bắt buộc): dòng bài vừa bấm hiện ngay "Đang mở…" (trạng thái `Status.DOWNLOADING` 0% hoặc nhãn mới), lỗi → Toast ngắn câu lỗi + dòng bài về trạng thái cũ. Câu lỗi cố định, thân thiện (ví dụ "Không mở được bài này: Telegram không tìm thấy tin nhắn (400)").
3. Log chẩn đoán thẻ `MeloVaultPlay` (mức `i`/`w`), **không chứa tên bài/tên tệp**: `stage=resolve|parsed|begin|download|error`, `chat=<chatId>`, `msg=<messageId>`, `size=<bytes>`, `code`/`message` lỗi TDLib, `downloaded_size`/`is_downloading_active` sau 10 s. Thêm vào nhánh lỗi `resolveMsg:` và nhánh lỗi của tag `download:`.
4. Hàm thuần mới `TelegramPlayFeedbackPolicy` (hoặc thêm vào `MeloNowPlayingPolicy`): `messageFor(stage, tdlibCode, tdlibMessage)` → câu tiếng Việt; có test JVM.

**B. Sửa nguyên nhân gốc — sau khi có log Y700:**
- Nếu (e): thêm theo dõi tiến độ tải; quá 20 s chưa nhận byte nào → báo "Telegram đang tải chậm, thử lại sau" và dừng; cân nhắc chỉ tải trước bài kế khi tệp < ngưỡng (ví dụ 200 MB) — **cần chủ dự án duyệt ngưỡng**.
- Nếu (d): trước `getMessage` gọi `getChat`(chat_id) một lần mỗi phiên cho chat chưa biết, rồi thử lại `getMessage` một lần.
- Nếu (c): log đếm số bài `genuineMessageId <= 0` theo kênh; sửa ở chỗ ghi chỉ mục (không đổi schema; nếu cần đổi dữ liệu thì DỪNG, lập kế hoạch riêng).
- Phiên thực hiện chỉ làm phần A + bump, phát hành Beta100 để chủ dự án bấm thử trên Y700 và gửi ảnh câu lỗi; phần B là phiên sau (hoặc làm luôn nếu chủ dự án đã gửi được ảnh/log trước khi bắt đầu).

### Kiểm tra lỗi 1
- Test JVM: `TelegramPlayFeedbackPolicyTest` — mỗi stage/mã lỗi ra đúng câu; không câu nào chứa tên tệp; mã 400/404/429/timeout.
- Sony: bấm bài trong "Sưu Tầm…" → dòng hiện "Đang mở…" ngay (< 0,5 s) rồi "Đang phát"; logcat `MeloVaultPlay` có `stage=resolve` → `begin`/`download`. Giả lập lỗi: bật chế độ máy bay rồi bấm bài chưa tải → sau ≤ 12 s có Toast lỗi, dòng không treo "Đang mở…", tắt máy bay. Kênh khác (BOX, DJ) vẫn phát bình thường.
- Y700 (chủ dự án): bấm 2–3 bài trong "Sưu Tầm…"; chụp màn hình câu hiện ra (hoặc báo "phát được"); bấm thử một bài ở kênh khác để so sánh.
- **Tiêu chí đạt:** không còn trường hợp bấm bài mà màn hình đứng yên: luôn có "Đang mở…" rồi hoặc phát, hoặc câu lỗi rõ. Có log đủ để phiên sau sửa nguyên nhân gốc trên Y700.

---

## Lỗi 2 — Màn Đang phát / khung trình phát: cột trái tràn, ⏮▶⏭ và hàng Đảo/Lặp/Lưu/Hàng đợi phải kéo mới thấy

### Nguyên nhân
- **Đã tái hiện trên Sony ngang** (`s11_deck_land.png`): Studio Deck ngang, cột trái chỉ thấy tới thanh tua; ⏮▶⏭, −30s/+60s, Đảo/Lặp/Lưu nằm dưới mép màn.
- Hàng "Đảo / Lặp / Lưu / **Hàng đợi**" mà chủ dự án tả chỉ có ở **khung trình phát lớn của Hai khung** (`MeloTabletScreen.buildPlayerPane`, `:190-198`); Studio Deck (`MeloStudioDeck`) có Đảo/Lặp/Lưu nhưng không có chip Hàng đợi → chủ dự án có thể đang nói khung trái Hai khung **[CẦN XÁC NHẬN]**; hai chỗ cùng một lỗi nên sửa cả hai.
- Cả hai đặt cạnh ảnh bìa theo **tỉ lệ cố định của chiều cao**, không trừ phần còn lại của cột: `MeloTabletScreen.kt:201` `min(rộng, cao × 0,55)`; `MeloStudioDeck.kt:208` `min(rộng cột, cao × 0,42)`. Phần dưới ảnh (tiêu đề 2 dòng, tên kênh, nhãn chất lượng, thanh tua 48–72dp, giờ, hàng nút cao `playButtonHeight` 72dp, hàng chip 48dp, các khoảng cách) chiếm ~330–400dp → ở Y700 ngang (cửa sổ cao ~750dp trừ thanh hệ thống) và Sony ngang (~411dp) tổng vượt chiều cao, cột thành cuộn.

### Cách sửa
- Hàm thuần `MeloNowPlayingPolicy.artSide(availableWidthPx, availableHeightPx, restHeightPx, minSidePx): Int` = `clamp(availableHeight − restHeight, minSide, availableWidth)`; nếu `availableHeight − restHeight < minSide` thì trả `minSide` (cột vẫn cuộn được, chỉ khi màn quá thấp).
- `MeloTabletScreen.buildPlayerPane` và `MeloStudioDeck.buildLandscape` (và `buildPortrait` dùng cùng cách, giữ trần 45% chiều cao): trong listener layout, đo phần còn lại = chiều cao đo được của các view con trừ `artHost` (gọi `measure` với `UNSPECIFIED` cho chiều cao, `EXACTLY` chiều rộng cột) + margin; gọi `sizeArt(artSide(...))`. Tránh vòng lặp layout: chỉ đặt lại khi giá trị thay đổi (đã có `artSide` cache ở Tablet; thêm cho Deck).
- Không đổi thứ tự/thành phần; không thêm thư viện.

### Kiểm tra lỗi 2
- Test JVM `MeloNowPlayingPolicyTest`: đủ chỗ → ảnh = chiều cao − phần còn lại; bị chặn bởi chiều rộng; quá thấp → `minSide`; số âm/0 → `minSide`.
- Sony ngang: mở Đang phát (Studio Deck) → thấy đủ ảnh, tiêu đề, thanh tua, −30s/⏮/▶/⏭/+60s và Đảo/Lặp/Lưu **không cần kéo**. Hai khung, tab Thư viện (khung trái trình phát lớn) → thấy đủ ⏮▶⏭ và Đảo/Lặp/Lưu/Hàng đợi. Dọc: Studio Deck không đổi dáng (ảnh vẫn lớn).
- Y700 ngang (chủ dự án): như Sony; chụp màn Đang phát và khung trái Thư viện.
- **Tiêu chí đạt:** ở Y700 và Sony ngang, mọi nút điều khiển của cột trái nằm trong màn khi chưa cuộn; ảnh bìa ≥ 56dp.

---

## Lỗi 3 — Núm thanh tua ở vị trí 0 bị cắt nửa

### Nguyên nhân (đã thấy trên Sony, `s11_deck_land.png`)
`MeloWaveformView.onDraw` (`:79-89`, khi chưa có sóng âm): rãnh vẽ từ `x = 0` đến `w`, núm tròn tâm `x = w × tiến độ`, bán kính `barH × 1,4` → ở 0 nửa trái núm nằm ngoài view (cắt), ở 100% nửa phải bị cắt. Dùng ở `MeloStudioDeck` và `MeloTabletScreen`.

### Cách sửa
- Thụt hai đầu rãnh bằng bán kính núm `r`: rãnh `[r, w − r]`, tâm núm `r + (w − 2r) × tiến độ`; vạch dọc khi có sóng âm cũng dùng cùng vùng (cột sóng giữ nguyên toàn chiều rộng hoặc cùng vùng — chọn cùng vùng cho khớp chạm).
- `MeloNowPlayingPolicy.seekFraction(x, width, inset = 0f)` = `((x − inset) / (width − 2·inset)).coerceIn(0, 1)`; view truyền `inset = r`. Hàm `knobCenter(fraction, width, inset)` để test.

### Kiểm tra lỗi 3
- Test JVM: `seekFraction` với inset (x ≤ inset → 0, x ≥ w − inset → 1, giữa → đúng tỉ lệ; width ≤ 2·inset → 0, không chia 0); `knobCenter(0) = inset`, `knobCenter(1) = w − inset`.
- Sony: mở Đang phát khi bài vừa bắt đầu (00:00) → núm tròn đủ; tua về đầu bằng −30s → núm đủ; chạm sát mép trái → về 0:00, sát mép phải → gần cuối.
- **Tiêu chí đạt:** núm luôn tròn đủ ở 0% và 100% (Studio Deck + khung trái Hai khung), chạm tua vẫn đúng vị trí.

---

## Lỗi 4 — Mở một kênh: hàng bài đầu bị cắt dưới tiêu đề nhóm

### Nguyên nhân [CẦN XÁC NHẬN]
- Sony **không tái hiện** được (dump: tiêu đề/nút kết thúc y=581, dòng 0 bắt đầu y=591 ở Hai khung; dọc 726 → 736). Có thể chỉ xảy ra trên Y700, hoặc khi đổi kênh lúc danh sách cũ đang cuộn giữa chừng.
- Giả thuyết theo code: `MeloLibraryPane.resetScroll()` (`:145`) gọi `list.setSelection(0)` ngay khi khóa nhóm đổi, **trước** khi bài của kênh mới về (nạp bất đồng bộ). Adapter không có id ổn định (`getItemId = position`), nên khi dữ liệu mới về ListView giữ nguyên độ lệch đỉnh của dòng đầu từ danh sách cũ → dòng 0 lệch lên trên, bị khung danh sách cắt mép trên (nhìn như nằm dưới tiêu đề nhóm).
- Lỗi cùng loại đã biết: khung đổi kênh tự cuộn bằng `setSelectionFromTop(index, s.minTouch)` (`MeloTabletScreen.kt:288`) làm kênh đang mở hơi bị cắt mép trên (ghi trong HANDOFF b99).

### Cách sửa
- `MeloLibraryPane`: cờ `pendingTopReset` bật trong `resetScroll()`; ở `onLibraryChanged()`, khi `!showingSources && rows.isNotEmpty() && pendingTopReset` → tắt cờ, `list.post { list.setSelectionFromTop(0, 0) }`. Logic quyết định đặt trong `MeloLibraryPolicy.shouldResetTop(pending, showingSources, rowCount)` để test.
- `MeloTabletScreen.renderQuickSwitch`: thay `setSelectionFromTop(index, s.minTouch)` bằng giữ dòng đang mở nằm trọn trong khung (offset = `quickList.height/3` chặn trong `[0, height − rowHeight]`), hoặc `smoothScrollToPosition(index)` khi đã hiện.

### Kiểm tra lỗi 4
- Test JVM `MeloLibraryPolicyTest`: `shouldResetTop` (đúng khi có cờ + có dòng + không xem lưới; sai khi chưa có dòng/đang xem lưới).
- Sony dọc và ngang: ở kênh lớn cuộn xuống giữa danh sách (dòng đầu đang bị che một nửa), đổi sang kênh khác bằng khung đổi kênh / BACK → lưới → kênh khác → dump: `top(dòng 0) ≥ top(danh sách)` và dòng 0 hiện trọn. Lặp 5 lần với 3 kênh.
- Y700 (chủ dự án): mở 3 kênh liên tiếp, dòng bài đầu hiện trọn.
- **Tiêu chí đạt:** mở/đổi kênh luôn bắt đầu ở đầu danh sách, dòng đầu không bị cắt; kênh đang mở trong khung đổi kênh không bị cắt mép.

---

## Lỗi 5 — Tên kênh dài chồng lên nút "Mới nhất / Phát hết"

### Nguyên nhân [CẦN XÁC NHẬN]
- Hàng tiêu đề nhóm `MeloLibraryPane.kt:88-95`: tiêu đề `width 0, weight 1, maxLines 1, ellipsize END` + 2 chip `WRAP_CONTENT`, cao cố định `minTouch`, **chip không giới hạn 1 dòng**.
- Trên Sony (dọc và Hai khung) tiêu đề được cắt "…" đúng, không chồng (dump: tiêu đề [1092..1819], chip từ 1819). Trên Y700 khung phải Hai khung hẹp hơn tỉ lệ chữ (hồ sơ Tablet chữ 17sp/14sp, có thể cỡ chữ hệ thống > 1) → tổng chiều rộng 2 chip vượt chỗ trống; LinearLayout ép chip, chữ chip xuống dòng nhưng chiều cao cố định → chữ tràn ra ngoài nền viên thuốc, và tiêu đề còn rất hẹp → nhìn như chồng lên nhau. Cần ảnh Y700 hoặc dump để xác nhận.

### Cách sửa
- Chip: `maxLines = 1`, `ellipsize = null` (chip không bao giờ bị ép; dùng `minWidth` bằng chiều rộng đo được của chữ).
- Khi khung hẹp: tiêu đề **một hàng riêng** (tối đa 2 dòng, "…") phía trên, hàng chip bên dưới căn phải. Quy tắc trong `MeloLibraryPolicy.headerStacked(paneWidthPx, titleWidthPx, chipsWidthPx, minTitlePx)` = `paneWidth − chipsWidth < max(minTitlePx, 40% paneWidth)`; `MeloLibraryPane` đo trong listener layout và chuyển giữa 2 dạng (giữ 1 bộ view, chỉ đổi cha/orientation, không dựng lại mỗi lần).
- Áp dụng cho mọi nơi dùng `MeloLibraryPane` (Điện thoại, Tablet dọc, Hai khung).

### Kiểm tra lỗi 5
- Test JVM `MeloLibraryPolicyTest.headerStacked`: khung rộng → một hàng; khung hẹp → hai hàng; chip rộng hơn khung → hai hàng; biên đúng 40%.
- Sony: Hai khung + kênh "Kênh Nhạc Sưu Tầm Những Năm Về Trước"; thêm thử cỡ chữ hệ thống ×1,3 (`settings put system font_scale 1.3`, **khôi phục 1.0 sau đó**) → dump: bounds tiêu đề và 2 chip không giao nhau; chữ chip nằm trọn trong chip. Dọc: kênh dài vẫn đúng.
- Y700 (chủ dự án): mở kênh tên dài ở Hai khung, chụp hàng tiêu đề.
- **Tiêu chí đạt:** tiêu đề và 2 nút không bao giờ chồng nhau; tên dài được cắt "…" hoặc xuống hàng riêng; nút luôn đọc được.

---

## File cần sửa
(Gốc: `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/`.)

| File | Lỗi | Sửa gì |
|---|---|---|
| `TelegramPreviewActivity.kt` | 1 | Chỉ đường phát: `reportPlayStage(...)` thay `showMessage` trong `downloadAndPlay`/`resolveTelegramMessage`/nhánh lỗi `resolveMsg:` + `download:`; log `MeloVaultPlay`; cấp `playStatus()` cho host. Không đụng sync, DAO, auth, epoch. |
| `MeloScreenHost.kt` | 1 | `playStatus()` (hoặc callback thông báo phát). |
| `MeloLibraryPane.kt` | 1, 4, 5 | Dòng "Đang mở…"/Toast lỗi; `pendingTopReset`; hàng tiêu đề co giãn 1↔2 hàng. |
| `MeloComponents.kt` | 5 | `chip`: `maxLines = 1`. |
| `MeloTabletScreen.kt` | 2, 3, 4 | Ảnh bìa theo `artSide`; inset núm (qua `MeloWaveformView`); cuộn khung đổi kênh không cắt mép. |
| `MeloStudioDeck.kt` | 2 | Ảnh bìa theo `artSide` (ngang + dọc). |
| `MeloWaveformView.kt` | 3 | Thụt rãnh/núm, `seekFraction` có inset. |
| `MeloNowPlayingPolicy.kt` | 2, 3 | `artSide`, `seekFraction(x, width, inset)`, `knobCenter`. |
| `MeloLibraryPolicy.kt` | 4, 5 | `shouldResetTop`, `headerStacked`. |
| `TelegramPlayFeedbackPolicy.kt` (mới) | 1 | Câu thông báo theo giai đoạn/mã lỗi. |
| Test `app/src/test/.../data/telegram/` | | `MeloNowPlayingPolicyTest`, `MeloLibraryPolicyTest` (bổ sung), `TelegramPlayFeedbackPolicyTest` (mới). |
| `app/build.gradle` (khối flavor `shareable`) | | Bump versionCode 100 / versionName `0.6.79-beta`. |

Không đụng: schema/`TelegramLibraryDao`/`AndroidTelegramLibraryDatabase`/`TelegramLibrarySql`, `songKey`, khóa bài, `ShareablePlaybackService` (trừ khi lỗi 1 phần B cần — khi đó lập kế hoạch riêng), `MeloCarScreen`, `MeloCompactScreen`, flavor `personal`, package, signing. Không thêm thư viện/Compose.

## Các bước làm (phiên thực hiện)
1. Đầu phiên kiểm git theo CLAUDE.md (HEAD phải là commit docs của kế hoạch này hoặc mới hơn trên cùng branch).
2. **Lỗi 1 phần A** (policy + test → host → activity → pane). Test JVM. Commit `fix(shareable): show play progress/errors in Melo screens + MeloVaultPlay log`.
3. **Lỗi 3** (policy + test → view). Commit `fix(shareable): keep seek knob inside the bar`.
4. **Lỗi 2** (policy + test → Tablet + Deck). Commit `fix(shareable): size cover art so player controls fit`.
5. **Lỗi 4** (policy + test → pane + Tablet). Commit `fix(shareable): reset song list to top on group change`.
6. **Lỗi 5** (policy + test → pane + chip). Commit `fix(shareable): stack group title when the header is narrow`.
7. Bump vc100 / `0.6.79-beta`; test Debug + Release (mốc b99: 1.178 tests, 21 skipped, 0 failed; mốc mới = 1.178 + số test thêm), `assembleShareableDebug/Release`, `git diff --check`. Commit bump.
8. Gate theo CLAUDE.md: bundle delta + SHA256 → Tiny `--ff-only` → build có Telegram config → verify signer `eecd3df7…ce8` → QA Sony (mục dưới) → Drive `MeloVault-Owner-Beta-v0.6.79-b100-Y700-REVIEW`.
9. Cập nhật HANDOFF (bản mới nhất, bug mở) + BACKLOG, commit docs, dừng.

## Cách kiểm trên Sony (tổng hợp, sau gate)
1. `adb install -r` đè b99; không gỡ, không `pm clear`, không đăng xuất. Kiểm `mCurrentFocus` = `app.melovault.player` trước mỗi chạm, không BACK ở root, script UTF-8 BOM. Ghi tổng bài/nguồn/playlist/sắp xếp trước-sau (phải y nguyên).
2. Lỗi 1: như mục "Kiểm tra lỗi 1" (gồm thử máy bay → bật lại).
3. Xoay ngang (`user_rotation 1`, `accelerometer_rotation 0`): lỗi 2, 3, 4, 5 như từng mục; chụp `uiautomator dump` + ảnh mỗi bước.
4. Dọc: lặp nhanh lỗi 3, 4, 5.
5. Dừng nhạc; khôi phục rotation 1/0 → `user_rotation 0`, `accelerometer_rotation 1`, density 420, font_scale 1.0. logcat không `AndroidRuntime`/`ANR`.
6. Lưu ý dung lượng: mỗi bài kênh "Sưu Tầm…" ~580 MB; chỉ bấm tối đa 3 bài của kênh này.

## Cách kiểm trên Y700 (chủ dự án)
Cài bản b100 từ thư mục Drive `MeloVault-Owner-Beta-v0.6.79-b100-Y700-REVIEW`, cầm ngang:
1. Kênh "Sưu Tầm…": bấm 2–3 bài → phát được, hoặc chụp câu báo lỗi hiện ra.
2. Mở Đang phát và tab Thư viện: thấy đủ nút ⏮▶⏭ và Đảo/Lặp/Lưu(/Hàng đợi) không cần kéo; núm tua ở đầu bài tròn đủ.
3. Mở 3 kênh liên tiếp: dòng bài đầu hiện trọn; tên kênh dài không chồng lên "Mới nhất/Phát hết".

## Tiêu chí đạt (cả phiên thực hiện)
- 5 tiêu chí ở từng lỗi đạt trên Sony (lỗi 1: phần A) và chủ dự án xác nhận trên Y700.
- Test Debug + Release 0 failed, `git diff --check` sạch, signer `eecd3df7…ce8`; DB vẫn v2; số bài/nguồn/playlist y nguyên; không ANR/crash.

## Ngoài phạm vi
- Lỗi 1 phần B (sửa nguyên nhân gốc trên Y700) — làm khi có log/ảnh Y700.
- Phát hiện phụ trên Sony (ghi lại, chưa sửa): bấm nút ⋮ trên dòng bài ở màn mới **phát bài** thay vì mở menu (các view con bị tắt bấm trong `MeloLibraryPane.RowsAdapter`; menu chỉ có khi giữ lâu); mini player khi tạm dừng dòng bài vẫn ghi "Đang phát".
- Chính sách tải trước với tệp rất lớn (cần chủ dự án quyết ngưỡng), dọn cache.
- EQ, chip Mixset theo thời lượng, Car, Gọn, giao diện Cũ, flavor `personal`, schema DB.
