# Kế hoạch — E99-GON-ANH-BIA (ảnh bìa dòng trong giao diện Gọn)

**Vai trò:** Claude Opus lập kế hoạch, 25/10/2026. **TASK_ID:** `E99-GON-ANH-BIA`. **Nhánh:** `dot/E99-GON-ANH-BIA`.
Luật vai trò: `CLAUDE.md` của nhánh `dot/workflow-opus-nghiem-thu` (`ffcc584`, chưa merge) — "ChatGPT" trong tài liệu cũ đọc là **Opus nghiệm thu**.
Opus chỉ commit file này. Không sửa code, không build, không cài APK.

## 0. Bối cảnh và mục tiêu

E98-THEME-ANH-BIA (PASS, merge `98662c7`) thêm ảnh bìa nhỏ cho từng dòng bài ở Điện thoại dọc / Tablet dọc / Hai khung khi **Kiểu màu = "Teal sáng · ảnh bìa"**. Gọn (Shanling M3 Ultra) cố ý giữ nguyên nên vẫn vẽ **ô chữ cái**. Chủ dự án thử trên Shanling và CHỐT (25/10): **Gọn cũng phải có ảnh bìa dòng** (thumbnail Telegram → ảnh kênh → ô chữ cái; bài đã tải lấy ảnh nhúng; nhạc trên máy lấy `loadThumbnail`).

**Mục tiêu đo được:**
1. Gọn trên Shanling hiện ảnh bìa ở ô 40 dp bên trái mỗi dòng (cùng nguồn và thứ tự như E98), ô chữ cái khi không có ảnh.
2. **Không đổi kích thước Gọn:** dòng 52 dp, ô ảnh 40 dp, thanh đáy 102 dp, **đủ 8 dòng** trên cửa sổ 574 dp (REWORK-01 E98).
3. **Không đổi màu Gọn** (palette `PALETTE` cũ) — E98 đã cam kết "Gọn giữ nguyên màu".
4. Tái dùng nguyên `MeloRowArtLoader` (≤ 3 yêu cầu TDLib, LIFO, watchdog 20 s, khóa có accountId, reset khi đăng xuất, giải mã ≤ 192 px RGB_565). Không thư viện mới, không ghi/xóa cache, không đổi DB/schema.
5. Car Mode, Điện thoại/Tablet/Hai khung, Studio Deck: không đổi hành vi.

## 1. Git baseline (đọc 25/10/2026)

| Mục | Giá trị |
|---|---|
| Workspace | `D:\Bots\MeloVault-E91-Shareable`, cây sạch |
| Nền | `feature/e93-03d-y700-offline-cockpit` @ **`455c8af`** (đã gồm merge E98 `98662c7`), vc113 / `0.6.89.1-beta` |
| Nhánh | `dot/E99-GON-ANH-BIA` cắt từ `455c8af`; commit kế hoạch = commit đầu tiên trên nhánh (cha `455c8af`) |
| Khác | Nhiều worktree E96/E97A/workflow ở `D:\Bots\_worktrees\*`, `D:\Bots\MeloVault-E96-*` — **không đụng**. Có tiến trình java (Gradle daemon nhàn rỗi) và adb.exe đang chạy, không có commit mới |

Sonnet S0: `git status --short` sạch; `git rev-parse HEAD` = commit kế hoạch; `git rev-parse HEAD^` = `455c8af`. Nếu khác → STOP. Số dòng bên dưới đọc trên `455c8af`; **định vị lại bằng tên hàm**.

## 2. Nguyên nhân gốc (đã đối chiếu mã)

Thư mục: `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/`.

1. `TelegramPreviewActivity.rowArtOn()` (≈ dòng 128) = `rowArtThemeActive && rowArtSwitch(prefs)`. `rowArtThemeActive` chỉ được gán trong `showNewLayer` (≈ 4833: `theme == TEAL_BRIGHT`, mà `theme = MeloThemePolicy.effective(...)` luôn trả CURRENT cho COMPACT/CAR). `applyMeloLayer` nhánh Gọn (≈ 4764–4799) **không bao giờ** gán, và `detachMeloLayer` đặt lại `false` ⇒ trên Gọn `rowArtOn() == false` ⇒ `snapshot()` không gắn `artKey`, không ai gọi `requestRowArt`.
2. `MeloCompactScreen.songRow` (≈ 216) không truyền `artKey`, không `peekRowArt`; danh sách Gọn không có listener cập nhật ảnh, không đo trạng thái cuộn.
3. `MeloComponents.songRow` (≈ 252) chỉ dùng `rowArtView` (khung có `MeloArtTag` để ảnh về sau tìm đúng dòng) khi `teal && model.artKey != null`; Gọn có `teal == false` (palette cũ) nên luôn vẽ `artView` (ô chữ cái).
4. Khóa dựng lại Gọn `"compact:${setting.key}:WxH:dpi:fontScale"` không chứa gì về ảnh bìa ⇒ đổi Kiểu màu trong Cài đặt giao diện xong quay lại, màn Gọn không dựng lại.

Phần còn lại đã sẵn sàng và dùng chung: `snapshot()` (≈ 5082) và `localSnapshot()` (≈ 594) đã gắn `artKey`/`chatId`/`rawMessageId`/`localFile`/`mediaStoreId` khi `rowArtOn()`; `meloHost.requestRowArt/peekRowArt/rowArtSettled/setRowArtListener` (≈ 5183–5207) không phụ thuộc màn; `MeloRowArtPolicy.shouldLoad/rowsToLoad` thuần.

## 3. Phương án

### 3.1 So sánh

| Phương án | Mô tả | Đánh giá |
|---|---|---|
| **A. Mở cờ ảnh bìa cho layer Gọn, giữ palette cũ (CHỌN)** | Tách "được hiện ảnh bìa dòng" khỏi "đang dùng màu Teal": hàm thuần mới `MeloThemePolicy.rowArtLayer(...)`; Gọn đặt `rowArtThemeActive`, dựng `rowArtView` khi có `artKey`, gọi loader giống `MeloLibraryPane` | Ít mã (~80–110 dòng production), tái dùng 100 % loader E98, màu/kích thước Gọn không đổi, rollback bằng công tắc |
| B. Áp cả theme Teal cho Gọn | `appliesTo` nhận COMPACT | Đổi màu + bố cục dòng teal (thẻ bo, 2 dòng) ⇒ phá 52 dp / 8 dòng, trái cam kết E98. **Không làm** |
| C. Bộ tải riêng cho Gọn | Loader/cache thứ hai | Trùng mã, gấp đôi TDLib request/RAM, rủi ro đổi tài khoản. **Không làm** |

### 3.2 Điều kiện bật ảnh bìa ở Gọn — [CẦN XÁC NHẬN chủ dự án]

**Đề xuất mặc định (Sonnet làm theo nếu chủ dự án không nói khác):** Gọn hiện ảnh bìa khi **Kiểu màu = "Teal sáng · ảnh bìa"** VÀ công tắc **"Tải ảnh bìa từ Telegram"** bật (mặc định bật); màu Gọn vẫn như cũ.
- Lý do: giữ đúng hợp đồng E98 "Kiểu màu = Hiện tại ⇒ giao diện y như trước" cho mọi người dùng Shareable chưa chọn Teal (không thêm lưu lượng TDLib cho họ); nhãn "Teal sáng · ảnh bìa" vốn đã hứa ảnh bìa. Shanling hiện có `color_theme=teal_bright`, `row_art_telegram=true` ⇒ thấy ảnh ngay sau khi cài, không cần chỉnh gì.
- Phương án thay thế (nếu chủ dự án muốn Gọn có ảnh bất kể Kiểu màu): chỉ đổi **một dòng** trong `rowArtLayer` (bỏ điều kiện TEAL cho COMPACT) + 2 test; không đổi phần khác.

### 3.3 Thiết kế chi tiết

1. **`MeloThemePolicy.kt`** — thêm hàm thuần:
   ```kotlin
   /** E99: whether a screen shows row covers. The teal layers do; Gọn does too with the teal theme chosen, but keeps its own colours. Car Mode never. */
   fun rowArtLayer(theme: ColorTheme, layer: NewLayer?, profile: Profile): Boolean =
       appliesTo(theme, layer, profile) || (theme == ColorTheme.TEAL_BRIGHT && profile == Profile.COMPACT)
   ```
   Không sửa `appliesTo`/`effective`/`rowArtActive` (test E98 giữ nguyên). Sửa KDoc đầu file: "Gọn giữ màu, có ảnh bìa dòng khi chọn Teal (E99)".
2. **`MeloCompactPolicy.kt`** — hàm thuần khóa dựng lại để test được:
   `fun layerKey(settingKey: String, widthPx: Int, heightPx: Int, densityDpi: Int, fontScale: Float, rowArt: Boolean): String` = chuỗi cũ + `":art"` khi `rowArt` (khi `false` trả **đúng chuỗi cũ**).
3. **`TelegramPreviewActivity.applyMeloLayer`** (nhánh Gọn):
   - Tính `val rowArt = MeloThemePolicy.rowArtLayer(MeloThemePolicy.load(prefs), null, Profile.COMPACT)` **trước** khi tạo `key`; `key = MeloCompactPolicy.layerKey(setting.key, display.widthPx, display.heightPx, display.densityDpi, display.fontScale, rowArt)`.
   - Sau `detachMeloLayer()` (hàm này đặt `rowArtThemeActive = false`) mới gán `rowArtThemeActive = rowArt` — **thứ tự bắt buộc**.
   - `MeloCompactScreen(frameContext, sizes, meloHost, onBackPressedDispatcher, rowArt)`.
   - `showNewLayer`: thay `rowArtThemeActive = theme == TEAL_BRIGHT` bằng `MeloThemePolicy.rowArtLayer(theme, layer, profile)` (kết quả giống hệt cho Phone/Tablet; chỉ để một nguồn quy tắc). Không đổi gì khác; không đụng `onNativeEvent`, `updateFile`, đăng xuất, `onDestroy`.
4. **`MeloComponents.kt`**:
   - `songRow` (nhánh không-teal): điều kiện `teal && model.artKey != null` → `(teal || isCompact) && model.artKey != null`. Car không bao giờ nhận `rowArtView`. Các nơi gọi khác (`MeloStudioDeck`, `MeloUiPreviewActivity`) không truyền `artKey` ⇒ không đổi.
   - Chuyển `findArtHost` từ `MeloLibraryPane` thành hàm dùng chung `internal fun findRowArtFrame(v: View?, depth: Int = 0): FrameLayout?` (top-level trong `MeloComponents.kt`, cạnh `MeloArtTag`), thân giữ nguyên. `MeloLibraryPane` gọi hàm này và xóa bản riêng (chỉ di chuyển, không đổi logic).
5. **`MeloCompactScreen.kt`** — bắt chước đúng `MeloLibraryPane` (E98):
   - Tham số constructor `private val rowArt: Boolean = false`; trường `scrollState`, `lastArtFirst = -1`.
   - `attach()`: nếu `rowArt` → `host.setRowArtListener(::refreshVisibleArt)`. `detach()`: nếu `rowArt` → `host.setRowArtListener(null)`.
   - `OnScrollListener`: `onScrollStateChanged` lưu `scrollState`; nếu `rowArt` và state ≠ FLING → `requestVisibleArt()`. `onScroll`: nếu `rowArt` và `first != lastArtFirst` → `requestVisibleArt()` (giữ nguyên `loadMore`).
   - `onLibraryChanged()` sau `adapter.notifyDataSetChanged()`: nếu `rowArt` → `lastArtFirst = -1; list.post { requestVisibleArt() }`.
   - `songRow(...)`: `art = row.art ?: if (rowArt) host.peekRowArt(row.artKey) else null`, `artKey = if (rowArt) row.artKey else null`. Giữ `rowGap`, `unclickable` y nguyên.
   - `requestVisibleArt()` / `refreshVisibleArt()` sao từ `MeloLibraryPane` (dùng `MeloRowArtPolicy.shouldLoad/rowsToLoad`, `c.applyRowArt`, `findRowArtFrame`); bỏ qua khi `snap.showingSources`. Log duy nhất: `Log.i("MeloRowArt", "compact visible=$shown withArt=$withArt settled=$settled")` — **chỉ số đếm**.
   - **Không** đổi `sourceRow`, header, thanh đáy, `NowPlayingPage`, kích thước, palette.
6. **`MeloUiLabActivity.kt`** — chỉ đổi chữ ghi chú mục "Kiểu màu": "… Chỉ áp dụng cho Điện thoại dọc, Tablet dọc và Hai khung ngang. Bản Gọn giữ màu cũ, chỉ thêm ảnh bìa nhỏ; Car Mode không đổi." Không thêm lựa chọn mới.
7. **`app/build.gradle`** — vc113 → **vc114**, versionName `0.6.89.1` → **`0.6.90`** (hậu tố `-beta` có sẵn). [CẦN XÁC NHẬN] tên phiên bản theo quy ước "nhiệm vụ mới tăng số thứ ba" (vc112 = 0.6.89, rework vc113 = 0.6.89.1); Opus nghiệm thu xác nhận.

### 3.4 Không làm / bảo vệ
- **Không** đổi DB/schema, songKey, sync/TDLib scan, Media3/queue/service, đăng nhập/tài khoản, signing/package, quyền, Car Mode, Studio Deck, palette và kích thước Gọn, thẻ "Đang phát" ở thanh đáy, trang Đang phát Gọn, lưới nguồn Gọn (ô kênh vẫn chữ cái).
- **Không** sửa `MeloRowArtLoader`/`MeloRowArtPolicy` (nếu thấy cần → STOP hỏi). TDLib chỉ nhận đúng các yêu cầu loader E98 đã có (`getMessage`, `getChat`, `downloadFile` priority 1 cho thumbnail). Không ghi/xóa file, không hash/xóa cache, không đụng TDLib session.
- Tài khoản: khóa ảnh có accountId; `reset()` khi đăng xuất/đổi tài khoản giữ nguyên vì không sửa loader.
- Telegram data: chỉ đọc thumbnail; log không có tên bài/kênh/chatId.

## 4. File sẽ đổi (ước lượng ~80–110 dòng production + ~60 dòng test)

| File | Thay đổi |
|---|---|
| `MeloThemePolicy.kt` | `rowArtLayer` + KDoc |
| `MeloCompactPolicy.kt` | `layerKey` |
| `MeloComponents.kt` | điều kiện `rowArtView` cho Gọn; `findRowArtFrame` dùng chung |
| `MeloLibraryPane.kt` | dùng `findRowArtFrame` (xóa bản riêng), không đổi logic |
| `MeloCompactScreen.kt` | tham số `rowArt`, scroll state, request/refresh ảnh, `songRow` truyền `artKey`/peek |
| `TelegramPreviewActivity.kt` | nhánh Gọn của `applyMeloLayer` (rowArt, key, cờ, constructor); `showNewLayer` dùng `rowArtLayer` |
| `MeloUiLabActivity.kt` | chữ ghi chú Kiểu màu |
| `app/build.gradle` | vc114 / 0.6.90 |
| test: `MeloThemePolicyTest.kt`, `MeloCompactPolicyTest.kt` | thêm test (mục 6) |

Không đủ điều kiện làn nhanh (> 2 file production, đụng đường gọi TDLib của loader).

## 5. Trình tự cho Sonnet — **GỘP 1 phiên** (code nhỏ; phiên vẫn chia giai đoạn commit)

- **S0** Kiểm Git như mục 1; đọc kế hoạch; xác nhận không agent khác (commit mới vài phút gần đây, Gradle đang build). Ghi `git log -1` vào báo cáo.
- **S1** `MeloThemePolicy.rowArtLayer` + `MeloCompactPolicy.layerKey` + test. Chạy test hai biến thể. *Commit 1* `E99 S1: row-art rule and compact layer key`.
- **S2** `MeloComponents` (điều kiện + `findRowArtFrame`) + `MeloLibraryPane` dùng hàm chung + `MeloCompactScreen` + `TelegramPreviewActivity` + chữ UiLab. Build Debug. *Commit 2* `E99 S2: row covers in Gọn`.
- **S3** Bump vc114 / 0.6.90. Chạy đủ lệnh mục 6 (xuất log ra file, chỉ đọc dòng tổng kết + XML). *Commit 3* `E99 S3: bump shareable beta to vc114 (0.6.90-beta)`.
- **S4** Git bundle (SHA-256) → clone cô lập trên Tiny → build APK có Telegram config (chỉ ghi true/false) → verify package/vc/cert bằng aapt2/apksigner → mang APK về Ni (kiểm SHA-256 sau khi chép). Không chép secret về Ni. Nếu không có đường bundle → STOP (không dùng patch văn bản như E98 trừ khi chủ dự án cho phép trong chat).
- **S5** QA Shanling (mục 7, bắt buộc) + Sony (mục 7, hồi quy, nếu Tiny ADB sẵn sàng). Màn hình tắt trong `finally`.
- **S6** Ảnh `00_tong_hop.png`, báo cáo `docs/plans/E99-GON-ANH-BIA-bao-cao.md` (≤ ~250 dòng, có mục "APK QA cần Opus nghiệm thu bàn giao"). *Commit 4* báo cáo. DỪNG.

## 6. Test và lệnh

Unit test JVM (không có Robolectric — phần View kiểm bằng QA thiết bị):
- `MeloThemePolicyTest`: `rowArtLayer` — TEAL + COMPACT (layer null) = true; CURRENT + COMPACT = false; TEAL + CAR = false (layer null và mọi layer); TEAL + PHONE/TABLET với mọi `NewLayer` = true; TEAL + PHONE layer null = false; với mọi tổ hợp Phone/Tablet kết quả **bằng** `appliesTo` (khóa hành vi E98).
- `MeloCompactPolicyTest`: `layerKey(..., rowArt=false)` = đúng chuỗi cũ `"compact:<key>:<w>x<h>:<dpi>:<font>"`; `rowArt=true` khác chuỗi `false`; đổi fontScale vẫn đổi khóa.
- Toàn bộ test cũ xanh; nền `455c8af`: **2038 test mỗi biến thể, 21 bỏ qua, 0 lỗi** — sau E99 phải ≥ 2038 + số test mới, 0 lỗi.

Lệnh Ni (CLAUDE.md §4):
```
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
git diff --check
```

## 7. QA thiết bị — Sonnet tự làm qua ADB (CLAUDE.md §3, §6, §7; `docs/workflow/SCREEN_LIFECYCLE_QA.md`)

**Gate trước khi cài (Shanling, Ni):** `adb devices -l` thấy `30313860180939` = device; `getprop ro.product.model/manufacturer` = M3 Ultra / Shanling; app đang cài `app.melovault.player` vc113 (`dumpsys package`), cert APK mới khớp cert app đang có (`eecd3df7…c3fce8`), vc114 > vc113, Telegram config = true; Media3 không đang PLAYING (nếu đang phát → STOP hỏi, không tự dừng nhạc của chủ dự án). Ghi lại prefs UI hiện có (Kiểu màu, công tắc ảnh, chế độ Gọn) bằng cách xem màn Cài đặt giao diện — **không** đọc file prefs bằng run-as nếu không cần. Chỉ `adb -s 30313860180939 install -r "<APK_VALIDATED>"`.

**Đo nền trước khi cài (vc113):** màn ON → Gọn, tab **Tất cả** → `dumpsys gfxinfo app.melovault.player reset` → 10 lần `input swipe` cuộn xuống đều (cùng toạ độ/thời lượng sẽ dùng sau) → `dumpsys gfxinfo` ghi Janky % và 90th percentile. Ảnh `01`.

| # | Thiết bị | Hành động | Kỳ vọng (PASS) | Ảnh |
|---|---|---|---|---|
| 1 | Shanling | Cài vc114, mở app (Gọn tự động, tab mặc định) chờ 5 s | Giao diện Gọn màu như vc113; dòng bài có ảnh bìa nhúng (bài đã tải) hoặc ô chữ cái; **8 dòng đủ**; `uiautomator dump`: mỗi dòng cao 104 px (52 dp), ô ảnh 80×80 px (40 dp), thẻ đáy ≥ 96 px, cửa sổ app 574 dp | 02 |
| 2 | Shanling | Tab **Tất cả** (Telegram), chờ 5 s không chạm | ≥ 80 % dòng đang hiện có ảnh thật hoặc ảnh kênh (đếm từ logcat `MeloRowArt compact visible/withArt` khung cuối + mắt thường trên ảnh); ảnh đúng dòng (khớp tên bài/kênh) | 03 |
| 3 | Shanling | `gfxinfo reset` → 10 swipe như phép đo nền → chờ 5 s | Không ANR/crash (`logcat -b crash` rỗng cho app, không dialog); Janky % không tăng > 5 điểm so với nền vc113; logcat inflight ≤ 3; sau khi dừng ảnh về đủ | 04 |
| 4 | Shanling | Bấm 1 bài **đã tải** (✓) để phát; bấm ⏯ thanh đáy để tạm dừng | Media3 state 3 rồi 2; dòng đang phát vẫn có ảnh + biểu tượng trạng thái; vẫn 8 dòng; thẻ "Đang phát" mở được như E98 | 05 |
| 5 | Shanling | ☰ → Cài đặt giao diện → tắt **"Tải ảnh bìa từ Telegram"** → quay lại; rồi bật lại | Khi tắt: các dòng về ô chữ cái như vc113, logcat không còn request mới; bật lại: ảnh trở lại. **Trả mọi cài đặt về như trước bước 5** | 06, 07 |
| 6 | Shanling (tùy chọn nếu có nguồn "Nhạc trên máy") | Mở danh sách nhạc trên máy trong Gọn, chờ 3 s | Bài có ảnh nhúng hiện qua `loadThumbnail` (Android 10) hoặc ô chữ cái; không crash. Không có nguồn → ghi CHƯA KIỂM | 08 |

**Sony Xperia 5 II (Tiny, `QV725WEQ54`) — hồi quy, làm nếu Tiny ADB sẵn sàng (không chặn PASS nếu thiếu, ghi CHƯA KIỂM):** cài vc114 (cùng gate) → Điện thoại dọc Teal: dòng bài vẫn có ảnh như E98 (đổi `findArtHost` không làm hỏng); Kiểu màu = Hiện tại: giao diện cũ, không ảnh. Tùy chọn: Cài đặt giao diện → Gọn = **Giả lập Shanling** → khung Gọn có ảnh; **trả Gọn về giá trị cũ**. Ảnh `09`, `10`.

**Cleanup bắt buộc (`finally`):** trả cài đặt UI về như lúc bắt đầu; bài đang phát để tạm dừng; `input keyevent 223`; `dumpsys display` Display State=OFF; ghi `SHANLING_SCREEN_END` / `SONY_SCREEN_END`.
Ảnh: `docs/plans/artifacts/E99-GON-ANH-BIA/NN_<thiết bị>_<mô tả>_<HHMMSS>.png`, ghép `00_tong_hop.png` (6–8 ô, cạnh dài ≤ 1600 px, số bước trên ô). Ảnh có tên bài/kênh **không commit**. **CHƯA XÁC NHẬN ÂM THANH THỰC** (Media3 PLAYING chỉ chứng minh luồng phát).

### Bác sĩ tự kiểm tra (Shanling M3 Ultra, sau khi có APK candidate vc114 — hoặc duyệt ảnh từ xa)
1. Mở MeloVault trên Shanling (giao diện Gọn): màu vẫn như cũ, **mỗi dòng bài có ảnh bìa nhỏ** bên trái (bài không có ảnh vẫn là ô chữ cái màu); đếm được **8 bài** trên màn.
2. Bấm tab **Tất cả**, chờ ~5 giây: phần lớn bài hiện ảnh bìa hoặc ảnh kênh; ảnh đúng với bài (không lẫn ảnh bài khác).
3. Vuốt danh sách nhanh vài lần rồi dừng: không đơ/văng app; ảnh hiện lại sau khi dừng.
4. Bấm một bài đã tải (dấu ✓): nhạc phát, thẻ "Đang phát" ở dưới vẫn lớn, bấm vào thẻ mở được màn Đang phát.
5. Muốn tắt ảnh: ☰ → **Cài đặt giao diện** → bỏ chọn **"Tải ảnh bìa từ Telegram"** → các dòng trở về ô chữ cái như trước.

## 8. PASS / FAIL

**PASS (tất cả):**
- Test Debug + Release: 0 lỗi, tổng ≥ 2038 + test mới, bỏ qua không tăng ngoài 21; build Debug/Release OK; `git diff --check` sạch; chỉ đổi đúng các file mục 4.
- Shanling thật bước 1–5 PASS: 8 dòng, dòng 52 dp, ảnh 40 dp, cửa sổ 574 dp (số đo uiautomator); ≥ 80 % dòng tab Tất cả có ảnh sau 5 s; Janky không tăng > 5 điểm % so với vc113; không ANR/crash; inflight ≤ 3; tắt công tắc → ô chữ cái, không request mới.
- Màu Gọn không đổi (so ảnh 01 vs 02 phần nền/chữ/thanh đáy); Car Mode và Phone/Tablet không đổi hành vi (mã + test; Sony nếu có).
- Log `MeloRowArt` chỉ có số đếm; không ghi/xóa cache/DB; không sửa loader.
- Chủ dự án xem ảnh/cài vc114 và nói **ĐẠT**.

**FAIL (bất kỳ):** Gọn còn < 8 dòng hoặc dòng/ảnh/thanh đáy đổi kích thước; ảnh sai dòng; crash/ANR; request TDLib không giới hạn (inflight > 3 kéo dài); màu Gọn đổi; Car Mode hoặc Teal Phone/Tablet hỏng; Kiểu màu = Hiện tại trên Phone bị đổi; sửa file ngoài mục 4 (trừ test/báo cáo).

## 9. STOP — Sonnet dừng và báo, không tự xoay xở

- Cây bẩn không rõ nguồn, HEAD/nhánh lệch baseline, có agent khác đang sửa/build.
- Cần sửa `MeloRowArtLoader`/`MeloRowArtPolicy`, DB/schema, TDLib event handler hay Media3 để làm được ⇒ STOP hỏi (vượt phạm vi).
- `adb devices -l` không thấy Shanling / unauthorized / getprop không phải M3 Ultra ⇒ BLOCKED phần QA (vẫn hoàn tất code + test, không tuyên bố đã thử Shanling; không dùng Sony thay).
- Signature mismatch, version downgrade, sai package, APK thiếu Telegram config, `install -r` lỗi ⇒ STOP; cấm `uninstall`, `pm clear`, `-d`, xóa dữ liệu/cache/TDLib, logout.
- Shanling đang phát nhạc (Media3 PLAYING) do người dùng ⇒ hỏi trước khi cài/chạm.
- Không tái hiện được Gọn (app mở ra không phải Gọn, ví dụ chưa đăng nhập hay chế độ Gọn bị đổi) ⇒ không tự đổi tài khoản/đăng nhập; chỉ được đổi Gọn = Tự động nếu trước đó nó là Tự động; còn lại BLOCKED.
- Sau khi bật ảnh, Gọn chỉ còn 7 dòng ⇒ FAIL, báo số đo; **không** tự thu nhỏ chữ/thanh đáy.
- Không có đường Git bundle tới Tiny ⇒ STOP hỏi (không tự chép đè repo Tiny, không dùng ổ T:).

## 10. Rủi ro và rollback

| Rủi ro | Giảm thiểu |
|---|---|
| Thêm FrameLayout/ImageView mỗi dòng làm Shanling (Android 10, ListView không tái dùng view) giật hơn | Đo Janky trước/sau cùng kịch bản; loader dừng khi fling; giải mã nền ≤ 192 px; ngưỡng FAIL +5 điểm |
| Dòng cao hơn 52 dp vì khung ảnh | `rowArtView` cùng cỡ `sizes.rowArt` (40 dp) như `artView`; kiểm bằng uiautomator |
| Ảnh sai dòng khi ListView dựng lại | `MeloArtTag(key)` + `applyRowArt` chỉ gán vào khung có đúng key (cơ chế E98) |
| Đổi Kiểu màu không áp vào Gọn | Khóa `layerKey` có `:art`; test |
| Cờ `rowArtThemeActive` bị `detachMeloLayer` xóa | Gán sau `detachMeloLayer()` (ghi rõ S2) |
| Lưu lượng TDLib trên 29,8K bài | Chỉ dòng hiện + 4, ≤ 3 inflight, negative cache, công tắc tắt |
| Bo góc ô ảnh khác nhẹ (10 dp) so với ô chữ cái cũ | Thẩm mỹ, chủ dự án xem ảnh |

**Rollback:** người dùng: tắt "Tải ảnh bìa từ Telegram" hoặc Kiểu màu = Hiện tại ⇒ Gọn về y vc113. Mã: revert Commit 2 (Commit 1 chỉ thêm hàm thuần không ai gọi), vc114 giữ để không downgrade. Không di trú dữ liệu.

## 11. Chưa kiểm chứng / cần xác nhận

- [CẦN XÁC NHẬN] Điều kiện bật ở Gọn: theo Kiểu màu Teal + công tắc (đề xuất) hay chỉ theo công tắc (mục 3.2).
- [CẦN XÁC NHẬN] versionName `0.6.90` cho vc114.
- Ảnh bìa ở thẻ "Đang phát" thanh đáy và trang Đang phát Gọn **không** thuộc E99 (vẫn dùng `thumbnailCache` cũ); lưới nguồn Gọn vẫn ô chữ cái. Nếu chủ dự án muốn, tách nhiệm vụ sau.
- Gọn lúc đang quét offline (có thể 7 dòng — giới hạn còn mở từ E98) không thuộc E99.
- Y700/S25/FYT/Car Mode trên máy: không kiểm trong E99. **Âm thanh thật chưa xác nhận.**
