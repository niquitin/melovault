# Việc: Thẻ nguồn nhạc (Phần C của `thanh-day-gop-melowave.md`)

Ngày viết: 18/10/2026, trên HEAD `11c2e06` (sau Beta107). Bản dự kiến: **Beta108**, vc108 / `0.6.87-beta`.
Đặc tả: `docs/ui-spec.md` mục "Thẻ nguồn nhạc", danh sách S25 (mục 5), bảng Shanling (dòng "Thẻ nguồn nhạc": Bỏ), bảng "Lỗi trong mockup cần tránh" (dòng "Google Drive 0 bài", dòng "Chip và thẻ bị cắt mép").

## 1. Phạm vi

**Làm:**
- Một hàng thẻ nguồn ở màn Thư viện kiểu Mới cho **Điện thoại dọc** (`NewLayer.PHONE_PORTRAIT`), **Tablet dọc** (`TABLET_PORTRAIT`) và **Hai khung ngang** (`TWO_PANE`, khung phải) khi cửa sổ đủ cao (xem 3.2).
- Mỗi thẻ: biểu tượng, tên nguồn, dòng "25.104 bài · đã tải 57". Bấm thẻ = lọc danh sách theo nguồn đó. Thẻ đang chọn có nền nhấn dịu + viền nhấn.
- Nguồn chưa kết nối hoặc chưa biết số bài thì **không có thẻ** (không bao giờ hiện "0 bài").
- Thẻ thu gọn (ẩn) khi cuộn danh sách xuống, hiện lại khi cuộn lên/về đầu, theo đúng quy tắc `MeloLibraryPolicy.headerCollapsed` đang dùng cho đầu trang.

**Không làm:**
- Shanling / bố cục Gọn (`MeloCompactScreen` không dùng `MeloLibraryPane`, nên không cần đụng). Car Mode (`MeloCarScreen`). Giao diện cũ Cockpit/Portrait.
- **"Nhạc trên máy"**: Shareable hiện KHÔNG có nguồn nhạc trên máy (không quét MediaStore, không xin quyền đọc nhạc; `LocalMediaRepository` chỉ dùng ở `MainActivity`/`MusicService` của flavor cũ, đã bị gỡ khỏi Shareable). Theo quy tắc "chưa kết nối thì ẩn", thẻ này không hiện. Chỉ chuẩn bị chỗ trong logic thuần (`Kind.LOCAL`) để sau gắn vào; **không** thêm quyền, không xin quyền, không quét. Có làm nguồn nhạc trên máy hay không là việc riêng **[CẦN XÁC NHẬN]** chủ dự án.
- Google Drive / OneDrive (workstream riêng; chỉ có `Kind.DRIVE` trong enum, không có dữ liệu).
- Không đụng TDLib, reducer, DB/schema, cache, đăng nhập, đồng bộ, phát. Không đổi màu. Không thêm framework.

Hệ quả cần nói rõ với chủ dự án: hôm nay chỉ có **một thẻ Telegram**. Bấm thẻ đưa danh sách về "toàn bộ Telegram" (bỏ kênh đang mở), giữ nguyên chip đang chọn (Tất cả / Đã tải). Giá trị chính hiện tại là nhìn thấy tổng số bài và số bài đã tải ngay đầu Thư viện.

## 2. Hiện trạng mã (đã đọc)

- `MeloLibraryPane` (khung Thư viện dùng chung cho Điện thoại dọc, Tablet dọc và khung phải Hai khung): thứ tự hiện tại `searchHolder` → `chipHolder` → `header` (tiêu đề nhóm + "↕ Mới nhất" + "▶ Phát hết") → `note` → `listFrame` (danh sách + thanh A–Z) / `sourcesScroll` (lưới kênh). Đã có `collapsed` + `onHeaderCollapse` tính theo cuộn.
- `MeloPhoneScreen` tạo pane với `onHeaderCollapse = setHeaderCollapsed` (ẩn hàng "MeloVault" + dòng đồng bộ). `MeloTabletScreen` tạo pane `NewLayer.TWO_PANE` không truyền `onHeaderCollapse`.
- `MeloScreenHost` (interface, chỉ một chỗ cài đặt: `meloHost` trong `TelegramPreviewActivity`).
- **Số đã tải hiện có KHÔNG dùng được cho thẻ:** `downloadedCount()` = `offlineGroups.size` khi `offlineSummary != null`, mà `refreshSongRows()` đặt `offlineSummary = null` + `offlineGroups = emptyList()` mỗi lần đổi màn; chỉ có số khi đang ở chế độ Đã tải, và số đó bị lọc theo kênh/từ tìm đang mở; lúc đang quét thì `offlineSummary = "Đang kiểm tra…"` còn `offlineGroups` rỗng → ra 0. Không được đưa số này vào thẻ.
- **Tổng số bài Telegram:** `db.getSongCount(userId)` (không lọc) được gọi ở `loadDatabaseIndex` (~dòng 5407), nhánh lưới nguồn của `refreshSongRows` (~3617) và sau đồng bộ (~5474); kết quả hiện chỉ đổ vào `totalCountForCurrentView` (bị ghi đè theo màn đang xem). Cần một biến riêng.

## 3. Thiết kế

### 3.1 Logic thuần mới: `MeloSourceCardPolicy.kt`

Thư mục `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/`. Không import Android.

```kotlin
internal object MeloSourceCardPolicy {
    enum class Kind { TELEGRAM, LOCAL, DRIVE }          // thứ tự hiển thị = thứ tự enum
    /** Số thật từ host; null = chưa biết / chưa kết nối. */
    data class Source(val kind: Kind, val total: Int?, val downloaded: Int?)
    data class Card(val kind: Kind, val title: String, val detail: String,
                    val selected: Boolean, val contentDescription: String)

    fun cards(sources: List<Source>, selected: Kind?): List<Card>
    fun rowVisible(layer: NewLayer, showingSources: Boolean, mode: Mode,
                   windowHeightDp: Int, cardCount: Int): Boolean
    fun cardWidthPx(paneWidthPx: Int, count: Int, minCardPx: Int, gapPx: Int): Int
    fun selectedKind(selectedChatId: Long?, showingSources: Boolean): Kind?
}
```

Quy tắc:
1. `cards`: bỏ nguồn có `total == null` hoặc `total <= 0`; xếp theo `Kind`; mỗi kind tối đa một thẻ (trùng thì lấy cái đầu). Tên: "Telegram", "Nhạc trên máy", "Google Drive". `detail` = `"${formatCount(total)} bài"` + (`" · đã tải ${formatCount(downloaded)}"` chỉ khi `downloaded != null && downloaded >= 0`). Dùng `MeloComponentPolicy.formatCount` (dấu chấm ngăn nghìn). `contentDescription` = "Nguồn Telegram, 25.104 bài, đã tải 57" + ", đang chọn" khi chọn.
2. `rowVisible`: true chỉ khi `cardCount > 0` **và** `!showingSources` (lưới kênh đã có thẻ "Kho tổng hợp" riêng) **và** `mode != PLAYLIST` **và** layer là `PHONE_PORTRAIT` / `TABLET_PORTRAIT`, hoặc `TWO_PANE` với `windowHeightDp >= 560`.
   - 560dp: Sony ngang (360dp) và S25 ngang (384dp) không có hàng thẻ để danh sách còn chỗ; Y700 ngang cao hơn nhiều nên có. Số đo chiều cao Y700 ngang chưa ghi trong đặc tả **[CẦN XÁC NHẬN]**; nếu chủ dự án thấy Hai khung chật thì chỉ cần đổi hằng số này.
3. `cardWidthPx` (tránh lỗi "thẻ bị cắt mép đọc không ra chữ"): `count <= 0` → 0; nếu `count` thẻ rộng `minCardPx` + khe vừa khung → chia đều khung (một thẻ = rộng hết khung); không vừa → chọn độ rộng ≥ `minCardPx` sao cho thẻ cuối nhìn thấy đúng một nửa (`paneWidth / (k + 0.5)` với k = số thẻ trọn vừa khung, trừ khe). `minCardPx` = 200dp Điện thoại, 240dp Tablet (đổi ra px ở lớp view).
4. `selectedKind`: Telegram khi `!showingSources && selectedChatId == null`; null khi đang mở một kênh cụ thể (khi đó bấm thẻ = quay về toàn bộ Telegram).

### 3.2 Host: số liệu thật

`MeloScreenHost` thêm:
```kotlin
/** Library-wide numbers per connected source; never narrowed by chip, channel or search. */
fun sourceSummaries(): List<MeloSourceCardPolicy.Source>
```

`TelegramPreviewActivity`:
- Thêm `@Volatile private var telegramLibraryTotal: Int? = null`: gán bằng `db.getSongCount(userId)` (bản không lọc) ở 3 chỗ đã có lời gọi đó (gán trên UI thread trong `runOnUiThread` sẵn có, kiểm `userId` như dòng xung quanh). Không thêm truy vấn DB mới.
- Thêm `private var downloadedLibraryTotal: Int? = null`: gán `groups.size` trong `publish(...)` của `discoverOffline` **chỉ khi** `snapshot.finished && chat == null && query.isBlank() && !playlist` (lần quét toàn thư viện đã xong). Lần quét bị lọc hoặc chưa xong không ghi đè. Chỉ giữ trong bộ nhớ, không lưu prefs/DB.
  - Hệ quả: lần mở app đầu tiên thẻ chỉ ghi "25.104 bài"; sau khi chủ dự án mở chip "Đã tải" một lần (không lọc kênh/tìm) thẻ có thêm "· đã tải N". **Không** tự kích hoạt quét offline chỉ để lấy số (quét gọi TDLib theo lô, tốn pin trên máy yếu). Muốn có số ngay khi mở app thì là việc riêng **[CẦN XÁC NHẬN]**.
- Xóa cả hai biến (về null) ở những chỗ đang đặt `meloChannelRows = emptyList()` khi đăng xuất / đổi tài khoản (~dòng 5137 và 6023).
- `sourceSummaries()` = `listOf(Source(TELEGRAM, telegramLibraryTotal, downloadedLibraryTotal))`. Không có LOCAL/DRIVE.
- Sau khi gán số mới gọi `scheduleMeloLibraryRefresh()` (đã có) để thẻ cập nhật.

### 3.3 Lớp view

- **`MeloComponents`**: thêm `sourceCard(card: Card, minHeightPx: Int, onClick)` — `LinearLayout` ngang, cao 88dp, nền `p.surface` viền `p.border` (chọn: `p.accentSoft` viền `p.accent`), bên trái huy hiệu tròn 40dp nền `p.accent` chữ `p.onAccent` ("T" cho Telegram, "♪" cho Nhạc trên máy, "D" cho Drive) — **không dùng emoji** (emoji lỗi font trên Sony Android 12, đã gặp ở Beta107); bên phải 2 dòng: tên (`s.titleText`, đậm, 1 dòng) + `detail` (`s.secondaryText`, `p.textSecondary`, 1 dòng, ellipsize cuối). `isClickable`, `contentDescription` lấy từ `Card`.
- **`MeloLibraryPane`**:
  - Thêm `sourceCardsHolder` (`HorizontalScrollView` bọc `LinearLayout` ngang, tắt thanh cuộn) đặt **giữa `header` và `note`**, đúng thứ tự đặc tả S25 (… tiêu đề nhóm, hàng thẻ nguồn, danh sách). Lề dưới `s.gap`.
  - Constructor thêm tham số `windowHeightDp: () -> Int` (lấy `resources.configuration.screenHeightDp` ở chỗ tạo pane) — hoặc đọc trực tiếp `context.resources.configuration.screenHeightDp` trong pane; chọn cách sau nếu đơn giản hơn.
  - `renderSourceCards(snap)` gọi trong `onLibraryChanged()`: tính `cards`, `rowVisible`; dựng lại view chỉ khi khóa (`cards` + `selected`) đổi (giống `chipKey`) để không giật khi `onLibraryChanged` chạy liên tục. Độ rộng thẻ tính bằng `cardWidthPx` theo `view.width` (sau layout: `post {}` như `fitHeader`).
  - Bấm thẻ TELEGRAM → `host.openSource(null)` (không đổi chip/chế độ; `openSource` đã xóa ô tìm và đặt `browseSources = false`). Thẻ đang chọn bấm lại → không làm gì.
  - Thu gọn: trong `onScroll`, khi `collapsed` đổi thì ngoài `onHeaderCollapse(next)` còn đặt `sourceCardsHolder.visibility` = GONE/VISIBLE (khi `rowVisible`). `resetScroll()` hiện lại hàng thẻ. Lưu ý giật cuộn: ẩn 88dp làm danh sách dài ra; `COLLAPSE_THRESHOLD_PX` + `lastScrollKey` hiện có đã chịu được khi ẩn đầu trang ở `MeloPhoneScreen`; nếu trên Sony thấy hàng thẻ nhấp nháy ẩn/hiện khi cuộn chậm ở gần đầu danh sách thì cập nhật `lastScrollKey` ngay sau khi đổi visibility (bỏ qua một lần delta).
- **Không đổi** `MeloPhoneScreen`, `MeloTabletScreen`, `MeloCompactScreen`, `MeloCarScreen` (trừ khi cần truyền chiều cao cửa sổ; khi đó chỉ thêm một tham số).

## 4. Các bước làm (một commit tính năng + một commit bump)

1. Đầu phiên: kiểm Git theo `CLAUDE.md` (cây sạch, đúng branch `feature/e93-03d-y700-offline-cockpit`, HEAD từ `11c2e06` trở đi).
2. Viết `MeloSourceCardPolicy.kt` + `MeloSourceCardPolicyTest.kt` (mục 5); chạy test riêng file đó cho xanh.
3. Thêm `sourceSummaries()` vào `MeloScreenHost`; cài đặt trong `TelegramPreviewActivity` với 2 biến số liệu (mục 3.2).
4. `MeloComponents.sourceCard` + `MeloLibraryPane` (hàng thẻ, chọn, bấm, thu gọn).
5. Chạy đủ test Debug + Release, `assembleShareableDebug`, `assembleShareableRelease`, `git diff --check`. Commit `feat(shareable): source cards row in Melo library (phone/tablet; hidden when not connected)`.
6. Bump vc108 / `0.6.87-beta`, commit `chore(shareable): bump b108 (0.6.87-beta)`.
7. Gate mặc định trong `CLAUDE.md`: bundle delta + SHA256 → Tiny `--ff-only` → test + build Release có Telegram config → aapt2 + signer `eecd3df7…ce8` → QA Sony (mục 6) → Drive `H:\My Drive\MeloVault-Owner-Beta-v0.6.87-b108-Y700-REVIEW`.
8. Cập nhật `HANDOFF.md` (mục 5 Beta108, mục 7 việc tiếp), `docs/plans/BACKLOG.md` mục 0 (Phần C đã làm), đánh dấu `docs/ui-spec.md` nếu có thay đổi quyết định (ngưỡng 560dp Hai khung). Commit `docs:`.

## 5. Test JVM (`app/src/test/java/com/bsdung/musicassistantcar/data/telegram/MeloSourceCardPolicyTest.kt`)

Tối thiểu 10 test, thêm vào mốc 1.229 → khoảng 1.239 mỗi biến thể, 21 skipped, 0 failed.

1. `telegramWithCountsShowsOneCard`: `Source(TELEGRAM, 25104, 57)` → 1 thẻ, title "Telegram", detail "25.104 bài · đã tải 57".
2. `unknownDownloadedOmitsThatPart`: downloaded null → "25.104 bài" (không "đã tải 0", không "?").
3. `zeroDownloadedIsShownWhenKnown`: downloaded 0 → "25.104 bài · đã tải 0" (số thật, đã quét xong) — **[CẦN XÁC NHẬN]** nếu chủ dự án muốn ẩn "đã tải 0" thì đổi test này.
4. `notConnectedOrZeroTotalHidesCard`: total null, total 0, total âm → không có thẻ; danh sách rỗng → rỗng.
5. `localAndDriveOnlyWhenTheyHaveSongs`: LOCAL total null + DRIVE total 0 + TELEGRAM 10 → chỉ Telegram; LOCAL 120 → hai thẻ theo thứ tự Telegram, Nhạc trên máy.
6. `duplicateKindKeepsFirst`.
7. `selectionAndContentDescription`: selected TELEGRAM → `selected = true`, mô tả kết thúc ", đang chọn"; selected null → không thẻ nào chọn.
8. `selectedKindFollowsChannel`: `selectedChatId = null` → TELEGRAM; `= 123L` → null; `showingSources = true` → null.
9. `rowVisibleOnlyOnPhoneTabletLibrary`: PHONE_PORTRAIT/TABLET_PORTRAIT + ALL/DOWNLOADED → true; PLAYLIST → false; `showingSources` → false; `cardCount = 0` → false.
10. `twoPaneNeedsTallWindow`: TWO_PANE với 360 (Sony ngang), 384 (S25 ngang) → false; 560, 800 → true.
11. `cardWidthFillsOrShowsHalfOfLast`: 1 thẻ khung 1080px → 1080; 2 thẻ min 525px khung 1080 gap 30 → chia đều 525; 3 thẻ min 600 khung 1080 → độ rộng ≥ 600 và `1080 - width - gap` hiện được ~nửa thẻ thứ hai; count 0 → 0.

Không có test cho Gọn/Car vì không đổi mã; `MeloLibraryPolicyTest.newLayerOnlyForPhoneTabletAndTwoPaneAfterLogin` đã chứng minh Gọn/Car không vào `MeloLibraryPane`.

## 6. Kiểm trên thiết bị

### Sony XQ-AS52 (Claude QA qua adb trên Tiny, cài đè `adb install -r` b107→b108, không gỡ/xóa dữ liệu)
Tuân quy tắc an toàn Sony trong `CLAUDE.md`: kiểm app ở tiền cảnh trước mỗi bước, không BACK ở root, khôi phục rotation/density cuối buổi (rotation 1/0, density 420), nhạc để tạm dừng.
1. **Điện thoại dọc (360×840dp)**: mở app → Thư viện có một thẻ "Telegram · 25.1xx bài" giữa tiêu đề nhóm và danh sách, thẻ rộng hết khung, nền nhấn dịu (đang chọn). Không có thẻ "Nhạc trên máy"/"Google Drive". Chụp ảnh.
2. Bấm chip **Đã tải** (chờ quét xong) → về **Tất cả** → thẻ hiện thêm "· đã tải N", N khớp số trên chip "Đã tải (N)". Chụp ảnh.
3. Cuộn danh sách xuống → hàng thẻ ẩn cùng đầu trang; cuộn lên về đầu → hiện lại; không nhấp nháy khi cuộn chậm.
4. Tab **Telegram** → lưới kênh: KHÔNG có hàng thẻ (chỉ thẻ "Kho tổng hợp" cũ). Mở một kênh → danh sách kênh có hàng thẻ, thẻ Telegram không sáng; bấm thẻ → về toàn bộ Telegram, thẻ sáng, chip giữ nguyên.
5. Tab **Playlist** → không có hàng thẻ.
6. **Hai khung ngang (840×360dp)**: xoay ngang → khung phải KHÔNG có hàng thẻ (cửa sổ thấp hơn 560dp). Xoay về dọc.
7. **Tablet dọc**: tạm `adb shell wm density 240` (Sony thành ~720dp rộng ≥ 600 → Tablet dọc), kiểm thẻ hiện đúng, chữ không bị cắt; trả `wm density 420` (hoặc `wm density reset` rồi kiểm lại 420) ngay sau đó.
8. **Gọn**: Cài đặt giao diện → "Thử trong khung Shanling" → Thư viện Gọn KHÔNG có hàng thẻ, vẫn 8–9 dòng như b107; trả về Tự động.
9. Không ANR/crash (logcat `FATAL EXCEPTION` của app, `ANR in app.melovault.player`).

### Y700 (chủ dự án tự QA, bản trên Drive)
1. Cầm dọc: thẻ Telegram đầu Thư viện, số bài khớp dòng đồng bộ trên đầu trang.
2. Cầm ngang (Hai khung): khung phải có hàng thẻ (nếu cửa sổ ≥ 560dp cao) và danh sách vẫn còn ít nhất ~6 dòng; nếu thấy chật → báo, Claude đổi ngưỡng.
3. Mở Đã tải rồi quay lại Tất cả → thẻ có "đã tải N".
4. Chụp màn hình gửi lại.

## 7. Tiêu chí đạt

- Test Debug + Release xanh trên Ni và Tiny (mốc 1.229 + test mới, 21 skipped, 0 failed); `git diff --check` sạch; APK Release ký `eecd3df7…ce8`, có Telegram config (chỉ kiểm có/không).
- Sony: các bước 1–9 mục 6 đúng, có ảnh cho bước 1, 2, 4, 6, 8 (lưu local Tiny `C:\Bots\_qa_temp\Beta108_<sha>\`, không đưa lên Drive vì có tên bài).
- Không bao giờ thấy thẻ "0 bài" hay thẻ nguồn không có dữ liệu; không có số giả (số bài = `getSongCount` toàn thư viện, số đã tải = lần quét toàn thư viện đã xong).
- Shanling/Gọn và Car Mode không đổi so với b107 (Gọn vẫn đủ 8 dòng ở 574dp).
- Không đổi DB/schema, TDLib, đăng nhập, phát; số bài sau cài đè không giảm.
- Còn mở sau phiên (ghi HANDOFF **[CẦN XÁC NHẬN]**): Y700 dọc/ngang, ngưỡng 560dp, có muốn số "đã tải" ngay khi mở app không, có làm "Nhạc trên máy" không.
