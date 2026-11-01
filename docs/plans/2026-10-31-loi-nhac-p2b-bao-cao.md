# Báo cáo thực thi — lời nhạc Shareable (P2-B)

Thực thi: Claude Code (Sonnet 5.5), phiên chặng A. TASK_ID `2026-10-31-loi-nhac-p2b`, nhánh `dot/2026-10-31-loi-nhac-p2b`, worktree `D:\Bots\_worktrees\MeloVault-loi-nhac-p2b`, baseline `94ab6b0`.
Kế hoạch: [`2026-10-31-loi-nhac-p2b.md`](2026-10-31-loi-nhac-p2b.md). Bàn giao: [`-ban-giao.md`](2026-10-31-loi-nhac-p2b-ban-giao.md).
Trạng thái: **CHẶNG A, B, C XONG (mục 9 và 10) và REWORK-01 XONG (mục 11): lỗi B1 (xoay màn hình làm pane Lời nhạc kẹt “Đang tìm lời…”) đã sửa ở `fd1f943` và kiểm lại PASS trên Sony. CHỜ CHỦ DỰ ÁN NGHIỆM THU — QA CANDIDATE — CHƯA NGHIỆM THU.** Bản APK hiện hành là `fd1f943` (mục 10 mô tả bản cũ `8d4284e`). APK/Drive: [`-apk-test.md`](2026-10-31-loi-nhac-p2b-apk-test.md); ảnh: [`-hinh-nghiem-thu.md`](2026-10-31-loi-nhac-p2b-hinh-nghiem-thu.md).
Chặng B: Thực thi: Claude Code (Sonnet 5.5), cùng phiên với chặng A theo yêu cầu “Làm ngay ở đây” của chủ dự án.

## 1. Tóm tắt

| Bước | Nội dung | Kết quả |
|---|---|---|
| L0 | Git sạch, HEAD `8bf3baa` khớp bàn giao, không java/gradle chạy | XONG |
| L1 | `MeloLyricsPolicy` + `MeloLyricsPolicyTest` | XONG, 23 test |
| L2 | `ShareableLyricsProvider` + `ShareableLyricsProviderTest` | XONG, 20 test |
| L3 | `ShareableLyricsCache` + `ShareableLyricsCacheTest` | XONG, 18 test |
| L4 | `ShareableLyricsController` + `ShareableLyricsControllerTest` | XONG, 22 test |

Commit mã: `6f274b9`. Mã nằm trong `app/src/shareable/.../data/telegram/`, test trong `app/src/test/.../data/telegram/`. Không đụng service, Media3, DB, TDLib, package, signer, versionCode, `main`.

## 2. Quyết định mục 9 của kế hoạch

Chủ dự án chỉ giao “Lời nhạc P2-B”, không trả lời khác → dùng **mặc định** cả 6 câu: LRCLIB gọi trực tiếp; tắt sẵn + hỏi đồng ý lần đầu; **không có URL/email liên hệ** nên User-Agent chỉ là `MeloVault/<ver>`; ngưỡng mixset 15 phút; có trong bản open (vẫn tắt sẵn); bỏ qua lời nhúng/`.lrc`.

## 3. Việc đã làm theo từng bước

- **L1.** `signature` = SHA-256 của `title|artist|album|giây` đã chuẩn hóa (NFC, chữ thường, gộp khoảng trắng, bỏ `feat./ft./featuring` ở nghệ sĩ). `shouldAutoFetch`: thiếu/“unknown”/“chưa xác định” → MISSING_METADATA; > 900 giây → TOO_LONG. `pickCandidate`: chỉ tự nhận khi **đúng 1** kết quả lệch ≤ 2 giây; chưa biết thời lượng thì không bao giờ tự nhận; tối đa 20, ứng viên gần nhất xếp trước. `currentLineIndex` gọi `LrcParser.findActiveLineIndex`. `userAgent` bỏ ký tự điều khiển.
- **L2.** `LyricsTransport` + `HttpUrlConnectionLyricsTransport` (kết nối 5 s, đọc 8 s, thân tối đa 512 KB, không theo redirect). Request chỉ có `track_name`, `artist_name`, `album_name`, `duration`, và `q` ở tìm thủ công; header chỉ `User-Agent`, `Accept`. 200 → bản ghi; 404 → NoMatch (không bao giờ thành “instrumental”); 429 → chờ `Retry-After` (mặc định 5 s, tối đa 600 s) + 0–1 s ngẫu nhiên và **chặn các request sau đó** đến hết thời gian; 5xx/mã lạ/IOException/JSON hỏng → TemporaryError. Cách nhau ≥ 300 ms. Log chỉ `stage=lyrics_request|response|blocked` + mã HTTP.
- **L3.** `noBackupFilesDir/melo_lyrics_v1/<owner>/<sig>.json` (đường dẫn gốc do chặng B truyền vào), ghi tệp tạm rồi `Files.move`. Hạn: có lời 90 ngày, NoMatch 3 ngày, chọn thủ công không hết hạn. Giới hạn 3000 mục/20 MB **mỗi tài khoản**, xóa theo LRU (mtime). Owner ID lạ (`../`, `/`) được băm; chữ ký lạ bị từ chối. Mọi lỗi I/O nuốt, không làm hỏng việc phát.
- **L4.** Thứ tự: đồng ý → cache → mạng → công bố qua `post`. Mỗi `request` tăng `generation`; kết quả chỉ được công bố nếu owner + generation + chữ ký bài còn đúng (nếu không ghi `stage=lyrics_drop`). Có `cancel`, `searchManual`, `choose`, `clearChoice`, `clearOwner`. Controller **không có tham chiếu tới player/queue**; mọi ngoại lệ thành `TemporaryError`.

## 4. Test và gate

Log đầy đủ ở thư mục cục bộ (bị `.gitignore`): `docs/plans/artifacts/2026-10-31-loi-nhac-p2b/A-20261101-105218/` (`A1-debug-targeted.log`, `A2-full-debug-release.log`, `A2-exit.txt`).

| Lệnh | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest --tests` 4 lớp mới | BUILD SUCCESSFUL, 83 test, 0 lỗi |
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL (2 phút 49 giây) |
| Cộng XML Debug | 183 suite, **2393 test, 21 skipped, 0 failures, 0 errors** |
| Cộng XML Release | 183 suite, **2393 test, 21 skipped, 0 failures, 0 errors** |
| `git diff --check` | sạch |

2393 = 2310 (gate S4 ở baseline) + 83 test mới. Skipped 21 là các test đã bỏ qua từ trước, không thêm. `OnlineLyricsTest` (53) của lõi Personal vẫn xanh.

Test chứng minh yêu cầu của kế hoạch (mục 7):
- request không chứa chat/message/songKey/account/đường dẫn: `noTelegramIdentifierPathOrSongKeyIsInTheRequest`, `getUrlCarriesOnlyTheFourAllowedFields`;
- log không có tên bài/nghệ sĩ: `logsHoldStagesAndCodesButNoTitleOrArtist`, `logsCarryNoSongNames`;
- kết quả lộn xộn A → B → A, owner khác, đóng tab: `answersArrivingOutOfOrderOnlyShowTheLatestSong`, `answerForAnotherAccountIsDropped`, `cancelDropsTheAnswerOfAClosedTab`;
- cache bài A không dùng cho cùng tên khác thời lượng: `cacheOfSongAIsNotUsedForSameNameOtherLength`;
- lỗi không thành ngoại lệ: `anyFailureBecomesAStateNeverAnException`.

## 5. Lệch so với kế hoạch (ghi để chặng sau biết)

1. Dựng URL bằng `URLEncoder` thay cho `Uri.Builder`, vì `android.net.Uri` không chạy được trong test JVM; kết quả mã hóa giống nhau (khoảng trắng → `%20`).
2. Kế hoạch ghi `LrcParser.kt` và `LyricsModels.kt` “không import gì”. Đúng, nhưng `LyricsTrackMetadata` trong `LyricsModels.kt` có gọi `LyricsCache.computeMetadataSignature` (cùng package, không cần import). Mã mới chỉ dùng `LrcParser` và `LyricsLine`, không dùng `LyricsTrackMetadata`/`LyricsCache`/`LyricsRepository`/`CarApiClient`.
3. Giới hạn 3000 mục/20 MB áp **mỗi tài khoản**, không phải toàn cục. Tệp `.part` mồ côi sau sự cố chỉ bị xóa khi xóa cả tài khoản.
4. Đồng ý lần đầu thể hiện bằng `LyricsConsent { UNDECIDED, GRANTED, DENIED }` do chặng B cấp qua một lambda; controller chỉ đọc.
5. Bài thiếu tên/nghệ sĩ vẫn đọc cache trước, nên lời đã **chọn thủ công** hiện lại khi quay về bài (khớp Q5).

## 6. Chưa làm / chưa kiểm chứng

- **Không có UI** (L5), công tắc Cài đặt, hộp đồng ý, nối `TelegramPreviewActivity`, xóa cache khi đăng xuất: thuộc chặng B.
- **Chưa gọi mạng thật.** Chưa xác nhận LRCLIB còn trả đúng định dạng trong tài liệu (phiên lập kế hoạch 31/10 đọc được `docs.md`); chỉ có transport giả.
- Chưa build APK, chưa Tiny, chưa Sony, chưa Q1–Q6. Âm thanh thật: **CHƯA XÁC NHẬN**.
- Chưa chạy `assembleShareableDebug/Release`, `-PmeloOpen=true`, `assemblePersonalDebug` (thuộc gate L6).
- Chưa đo thời gian ghi/đọc cache trên máy thật.

## 7. Thiết bị / màn hình

Không dùng ADB, không đụng thiết bị. `SONY_SCREEN_END` = KHÔNG THAO TÁC.

## 8. Rủi ro / rollback

- Rủi ro: LRCLIB đổi API hoặc chặn → `TemporaryError`, app vẫn phát. Bản quyền lời khi đăng công khai vẫn là câu hỏi mở (LD9).
- Rollback: chặng A chỉ thêm 8 file mới và không có điểm nối vào app nên có thể `git revert 6f274b9` hoàn toàn; không có migration.

## 9. Chặng B — UI + gate Ni (L5–L6), 01/11/2026

Commit mã: `3447310`. Log cục bộ (bị `.gitignore`): `docs/plans/artifacts/2026-10-31-loi-nhac-p2b/B-20261101-110516/` (`B1`…`B5`, `B-exit.txt`, `run-gate.ps1`).

### 9.1 Đã làm (L5)

| File | Nội dung |
|---|---|
| `MeloLyricsUiPolicy.kt` (mới) | Chữ và nút cho từng `LyricsState`; chữ hộp đồng ý LD2 đúng theo kế hoạch; nhãn ứng viên; chữ điền sẵn ô tìm; quy tắc nút “Về dòng đang hát”, cuộn về giữa, chiều cao pane, chiều cao nút theo cỡ chữ; đọc/ghi đồng ý và tab trong prefs `melovault_ui_lab` (`lyrics_consent`, `lyrics_tab`) |
| `MeloLyricsPane.kt` (mới) | `MeloLyricsAccess` (giao diện app cấp cho pane), `MeloLyricsPane` (Android Views: thông báo + nút, lời đồng bộ tô và tự cuộn, lời thường, danh sách chọn, ô tìm thủ công có IME “Tìm”, dòng “Lời: LRCLIB”), `MeloLyricsTabs` (thanh “Tiếp theo \| ♪ Lời nhạc”, nhớ tab) |
| `MeloScreenHost.kt` | thêm `lyrics(): MeloLyricsAccess? = null` (host không có lời thì không hiện tab) |
| `MeloStudioDeck.kt` | dọc: khối hàng đợi nằm dưới thanh tab, pane lời cao cố định `portraitPaneHeight`; ngang: cột phải có thanh tab; mở/đóng Deck bật/tắt pane |
| `MeloTabletScreen.kt` | panel Hàng đợi ở cột phải có thanh tab; “Thư viện” chuyển về cuối thanh tab |
| `MeloUiLabActivity.kt` | công tắc “Lời nhạc từ LRCLIB” (nhóm THƯ VIỆN & ĐỒNG BỘ); chữ phụ nêu rõ dữ liệu gửi đi và “Mặc định tắt” |
| `TelegramPreviewActivity.kt` | tạo controller (executor 1 luồng, `post` về luồng chính, log mức **W**), `lyricsMetaNow()` (Telegram: title/performer/thời lượng, không album; nhạc trên máy: có album, tên thư mục không bị coi là nghệ sĩ), `clearLyricsOf()` gọi đầu `logoutUser()` |

Không đụng service, Media3, DB, TDLib (không gọi thêm), package, signer, versionCode, Car, Gọn. Không đọc tệp nhạc.

### 9.2 Gate L6

| Lệnh | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL (1 phút 41 giây) |
| Cộng XML Debug | 184 suite, **2412 test, 21 skipped, 0 failures, 0 errors** |
| Cộng XML Release | 184 suite, **2412 test, 21 skipped, 0 failures, 0 errors** |
| `:app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL |
| `-PmeloOpen=true :app:assembleShareableDebug` | BUILD SUCCESSFUL |
| `:app:assemblePersonalDebug` | BUILD SUCCESSFUL |
| `git diff --check` | sạch |

2412 = 2393 (sau chặng A) + 19 (`MeloLyricsUiPolicyTest`). Năm lớp test lời nhạc: 23 + 19 + 18 + 22 + 20 = 102, đều xanh ở hai biến thể.

### 9.3 Lệch so với kế hoạch (chủ dự án cần biết)

1. **Hai khung:** pane lời không nằm ở khung phát bên trái. Bản trái không có phần “Tiếp theo”; hàng đợi của Hai khung mở ở cột phải bằng nút “☰ Hàng đợi”. Thanh tab “Tiếp theo | Lời nhạc” gắn vào chính panel đó. Vì vậy ở Hai khung muốn xem lời phải bấm “Hàng đợi” rồi chọn tab Lời nhạc; **bước Q6 của kế hoạch phải đọc theo cách này**. Nếu chủ dự án muốn lời nằm cố định ở khung trái thì cần đổi thiết kế (REWORK/nhiệm vụ riêng).
2. **Nhớ tab:** lưu trong prefs `lyrics_tab` (cùng file Cài đặt), không thêm trường vào `MeloBrowseState.Deck`, nên không đụng cơ chế lưu/khôi phục trạng thái S2. Tab vẫn được nhớ qua xoay và mở lại màn.
3. **Nhịp 1 giây:** pane có `Handler` luồng chính riêng (chỉ chạy khi hiện), không dùng ticker của Deck, để dùng chung cho Hai khung. Không thêm luồng nền.
4. **Công tắc Cài đặt** nằm ở trang chính, nhóm “THƯ VIỆN & ĐỒNG BỘ”. Bật ở đây là cùng một đồng ý với hộp ở tab; tắt thì tab hiện “Lời nhạc đang tắt” và nút “Bật lời nhạc” (mở lại hộp đồng ý).

### 9.4 Chưa kiểm chứng

- **Toàn bộ phần nhìn thấy được chưa từng chạy:** `MeloLyricsPane`, `MeloLyricsTabs`, gắn vào Deck/Hai khung chỉ được biên dịch, không có test JVM (view). Chưa thấy bằng mắt trên máy nào.
- Chưa biết: cuộn lồng nhau của pane cao cố định trong màn dọc của Deck; ô tìm với bàn phím trong cửa sổ `Dialog` của Deck; chữ lớn 1.3 trên thiết bị; xoay giữ tab và câu tô; Hai khung ngang trên Sony (cao ~360 dp có đủ chỗ cho pane không).
- Chưa gọi LRCLIB thật. Âm thanh thật: **CHƯA XÁC NHẬN**.
- APK build trên Ni **không có Telegram config** nên **KHÔNG CÀI** để QA. Chưa build ở Tiny, chưa kiểm package/versionCode/chứng chỉ, chưa Sony, chưa Drive.
- Chưa có `-apk-test.md`, `-hinh-nghiem-thu.md`, `00_tong_hop.png` (thuộc L8).

### 9.5 Thiết bị / màn hình

Không dùng ADB hay thiết bị. `SONY_SCREEN_END` = KHÔNG THAO TÁC.

### 9.6 Rủi ro / rollback

- Rủi ro lớn nhất là bố cục: lỗi chỉ lộ khi chạy thật (chặng C). Lỗi lời nhạc không chạm player/queue theo thiết kế (controller không có tham chiếu tới player).
- Rollback: tính năng tắt sẵn. Muốn ẩn hẳn mà giữ lõi: `git revert 3447310`.

## 10. Chặng C — Tiny build + QA Sony (01/11/2026 11:23–11:45, Claude Code Sonnet 5.5)

Thực thi: Claude Code (Sonnet 5.5), phiên chặng C, cùng phiên với A và B theo yêu cầu của chủ dự án. **Không sửa mã trong chặng này.** Ảnh và log (bị `.gitignore`): `docs/plans/artifacts/2026-10-31-loi-nhac-p2b/C-20261101/` (37 ảnh, `l7-build.log`, `lyrics-log.txt`) và `00_tong_hop.png`. Hồ sơ APK/Drive: [`-apk-test.md`](2026-10-31-loi-nhac-p2b-apk-test.md); link ảnh: [`-hinh-nghiem-thu.md`](2026-10-31-loi-nhac-p2b-hinh-nghiem-thu.md).

### 10.1 L7 — build và cài

| Bước | Kết quả |
|---|---|
| Nhận việc | Cây Ni sạch, HEAD `8d4284e`, `diff --check` sạch; java đang chạy chỉ là daemon Gradle của chính phiên này |
| Bundle | `MeloVault_loi-nhac-p2b_full_8d4284e_20261101.bundle` 13.411.312 byte, SHA-256 `d3ee9e53db1ba6b5374c81f8c099c62358906290cdb8b55f649be8daece86a90`, `git bundle verify` ok; **khớp SHA hai đầu** (Ni và Tiny `C:\Bots\_MeloVault_Git_Transfer\`) |
| Clone Tiny | `C:\Bots\MeloVault-LOINHAC-IsolatedQA-20261101-8d4284e`, HEAD `8d4284e24456c37b2ace90b24a14480016de3a7d`, cây sạch; `local.properties` chép từ clone GDTB trên Tiny theo ngoại lệ E104 S11 (không đọc/in) |
| `reportTelegramConfig` | `telegram.config.valid=true` (chạy KHÔNG có `-PmeloOpen`) |
| `:app:assembleShareableDebug` | `BUILD SUCCESSFUL` 2m44s |
| Gate APK | `app.melovault.player`, vc **121** / `0.6.96-beta`, debuggable, cert `eecd3df7…fce8`, 33.642.436 byte, SHA-256 `31609D80389A9E6EAD8F6417719B640FFBAFFEC3D92916083E3611E687821E88` |
| Chữ ký bản đang cài | pull `base.apk` từ Sony: cert `eecd3df7…fce8` (trùng); bản cũ SHA `367CAA37…F1006` (candidate giao diện đa thiết bị) |
| Cài | Sony `QV725WEQ54` (`XQ-AS52` / `Sony` / Android 12), nhạc đang tạm dừng (`state=2`), đúng một lệnh `adb -s QV725WEQ54 install -r` → `Success`; SHA bản cài lại khớp APK vừa build |

### 10.2 L8 — kết quả QA trên Sony (`1080×2520`, density 420, font 1.0)

| Bước | Thao tác | Thực tế | Kết quả | Ảnh |
|---|---|---|---|---|
| Q1 | Deck → tab Lời nhạc | Hộp đồng ý đúng chữ LD2, nút Bật/Không; logcat 0 dòng `lyrics_request`. Chọn **Không** → “Lời nhạc đang tắt.” + “Bật lời nhạc”, vẫn 0 request | **PASS** | `q1-consent`, `q1-declined` |
| Q2 | Bật; phát ~11 giây | Câu tô chuyển “Ngăn giọt lệ…” (2:09) → “Phận duyên mong manh…” (2:21), đúng chiều. **Lời này do chọn thủ công cho bài mixset** (xem giới hạn) | **PASS có giới hạn** | `q2-a-before`, `q2-b-after` |
| Q3 | Tua +60 giây; cuộn tay | Câu tô nhảy theo (3:21); cuộn tay thì hiện “Về dòng đang hát”, bấm thì tự cuộn lại, nút mất | **PASS** | `q2-a-before` (nút hiện), `q3-a-follow-again`, `q3-b-after-seek60` |
| Q4 | ⏭ sang bài kế | Lời bài cũ biến mất; bài mới “#Bản nháp 1” hiện “Thiếu tên bài hoặc nghệ sĩ”; player sang bài kế bình thường, không crash | **PASS** | `q4-next` |
| Q5 | Bài thiếu nghệ sĩ/mixset → Tìm lời → gõ từ khóa → chọn | Không tự tìm (0 request); ô tìm điền sẵn tên tệp; bàn phím hiện và bố cục co đúng; danh sách “Lạc Trôi — Sơn Tùng M-TP · 3:53 · đồng bộ”…; chọn kết quả đầu → lời đồng bộ, có “Bỏ lời đã chọn”. **Chưa kiểm “quay lại bài thì còn nhớ”** | **PASS có giới hạn** | `q5-search-open`, `q5-retry1`, `q5-chosen` |
| Q6 | Xoay ngang → Hai khung | Hai khung: bấm “☰ Hàng đợi” → cột phải có “Tiếp theo \| ♪ Lời nhạc”, “Thư viện” ở cuối; tab Lời nhạc được giữ, hiện “Không tìm thấy lời cho bài này.” + “Tìm lời” (từ cache). Theo bản đã sửa của kế hoạch (bàn giao chặng B) | **PASS** (với thay đổi ghi ở 9.3) | `q6-twopane`, `q6-twopane-queue` |
| Q7 | Cài đặt → công tắc “Lời nhạc từ LRCLIB” | Có hàng với chữ nêu dữ liệu gửi đi; đang BẬT → bấm → TẮT; mở Deck: “Lời nhạc đang tắt.”, không có request mới | **PASS** | `q7-settings`, `q7-off`, `q7-deck-off` |
| Q8 | Deck dọc → ngang (Deck ngang) | Tab Lời nhạc được giữ, **nhưng nội dung kẹt ở “Đang tìm lời…” mãi mãi**; log có `stage=lyrics_drop` | **FAIL** (lỗi B1) | `q6-deck-landscape`, `q6-deck-landscape2` |

Bổ sung (không nằm trong bảng kế hoạch):
- **Đường tự động thật:** phát bài “Nhạc trên máy” có thẻ (“Total Immersion — Akira Inoue”, M4A) → request tự động `get` → 404 → `search`. Lần đầu LRCLIB trả 503 → “Chưa tải được lời. Thử lại sau.” + “Thử lại”; bấm lại → `search` 200, không có kết quả → “Không tìm thấy lời cho bài này.” + “Tìm lời” (`q2l-deck`, `q2l-retry-b1`). Lần sau lấy từ cache (`stage=lyrics_cache hit=true`, không gọi mạng). Ô tìm điền sẵn “Total Immersion Akira Inoue” (title + nghệ sĩ).
- **Log:** chỉ có `stage=lyrics_request|response|cache|drop` kèm mã HTTP (`kind=get|search`). Tìm trong log đã lưu: 0 lần xuất hiện tên bài/nghệ sĩ/từ khóa. `logcat -c` đã chạy một lần giữa phiên nên `lyrics-log.txt` chỉ giữ phần sau 11:39; các dòng sớm hơn (11:36–11:37: `search` 503 rồi 200 ×3) được ghi lại từ quan sát trực tiếp.
- Không có `FATAL EXCEPTION`/ANR của app, bộ đệm crash rỗng.

### 10.3 Lỗi và phát hiện

**B1 — LỖI (FAIL Q8): sau khi xoay màn hình, pane Lời nhạc kẹt “Đang tìm lời…”.** Tái hiện: đang ở Deck dọc, tab Lời nhạc, bài đang có, xoay ngang. Nguyên nhân khả dĩ (từ log, chưa sửa): Deck mới tạo pane và gọi `request` (tăng generation), sau đó Deck cũ bị đóng và gọi `setShown(false)` → `controller.cancel()` tăng generation thêm một lần nên kết quả của pane mới bị coi là cũ và bị bỏ (`stage=lyrics_drop`); `requestedKey` của pane mới đã khớp nên không hỏi lại. Hai khung không tái hiện (đường cache khác). Đề xuất sửa cho REWORK: cancel chỉ khi pane hiện hành là pane đang giữ generation, hoặc pane mới tự hỏi lại khi còn `Loading` sau một khoảng; kèm test JVM cho thứ tự “pane mới request → pane cũ cancel”. **Chưa kiểm hướng ngang → dọc ngoài việc khôi phục xoay.**

**F2 — quan sát thiết kế:** hầu hết bài Telegram của tài khoản này không có nghệ sĩ (tên bài là tên tệp, ví dụ “#3 Up With Wuan C”, “#Bản nháp 1”) nên theo LD4 chỉ hiện “Thiếu tên bài hoặc nghệ sĩ” và không tự tìm; lời đồng bộ tự động chỉ có với bài có thẻ nhạc. Chủ dự án cần biết trước khi nghiệm thu kỳ vọng.

**F3 — LRCLIB chập chờn:** `/api/search` đôi lúc trả 503 (cũng tái hiện từ Ni bằng yêu cầu công khai, 5/6 lần ở một đợt thử), `/api/get` ổn định. App xử lý đúng (thông báo + “Thử lại”), không crash.

**F4 — bố cục Deck dọc:** ảnh bìa lớn nên thanh tab và pane lời nằm dưới màn hình đầu, phải vuốt trang mới thấy; pane cao cố định, cuộn trong pane và cuộn trang cùng hoạt động (cuộn tay trong pane tắt tự cuộn như thiết kế). Có thể là điểm chủ dự án cần đánh giá về tiện dùng.

### 10.4 Trạng thái Sony thay đổi do QA (ghi đúng)

- Gói thường `app.melovault.player` nay là bản `8d4284e` (cùng vc121, chữ ký trùng). Màn hình lúc bắt đầu đang ở **gói `.open`** từ phiên QA trước; tôi không thao tác trong bản `.open`.
- Lệnh `monkey` đầu tiên lỡ mở “Game enhancer” của Sony (không chạm gì ở đó); sau đó mở app bằng `am start` đúng activity.
- Chạm thẻ “Nghe tiếp” đã **tự phát lại bài #3 ~15 giây** (như các phiên trước); sau đó tôi phát thêm vài giây để QA (mixset ~11 giây, bài kế ~4 giây, bài trên máy ~3 giây) rồi tạm dừng ngay mỗi lần. **Bài hiện tại giờ là “Total Immersion” (nhạc trên máy) tạm dừng ở 0:02**; trước phiên là “#3 Up With Wuan C” dừng ở 1:51. Hàng đợi đã đổi.
- Lời nhạc trên máy: **đồng ý đang ở trạng thái “tắt”** (chọn Không rồi tắt ở Cài đặt), không còn “chưa quyết định”; cache có 1 lựa chọn thủ công (bài mixset #3) và 1 mục “không có lời” (Total Immersion).
- Xoay: đặt ngang rồi khôi phục. Cuối phiên `accelerometer_rotation=1`, `user_rotation=0`, `1080×2520`/420, font 1.0. (Lần khôi phục đầu đọc lại là 0 do màn hình đang xoay; đặt lại và kiểm: 1/0.)
- **`SONY_SCREEN_END`: `Display State=OFF`** (xác minh sau `finally`). Không uninstall, không `pm clear`, không logout, không máy bay.

### 10.5 Chưa kiểm chứng

- **Bài Telegram có tên + nghệ sĩ khớp LRCLIB (đường tự động ra lời đồng bộ)** — không có bài như vậy trong tài khoản; chỉ kiểm bằng lời chọn thủ công.
- Q5 “quay lại bài thì còn nhớ lựa chọn” và nút “Bỏ lời đã chọn” trên máy (có nút, chưa bấm).
- Xoay ngang → dọc; chữ lớn 1.3; TalkBack; Release/`.open`/Personal trên máy; lỗi mạng thật, 429, hết hạn cache (chỉ JVM); Y700/S25/FYT. **CHƯA XÁC NHẬN ÂM THANH THỰC.**

## 11. REWORK-01 — sửa lỗi B1 (01/11/2026, Claude Code Sonnet 5.5)

Chủ dự án yêu cầu “Sửa ngay ở đây” sau khi nhận báo cáo chặng C. Chỉ sửa lỗi B1; không đổi phạm vi khác.

### 11.1 Nguyên nhân (xác nhận bằng đọc mã)

Giả thuyết ở 10.3 đúng. Khi xoay, `MeloStudioDeck` mới được dựng và `show()` → `lyricsTabs.setActive(true)` → pane mới gọi `controller.request` (tăng generation). Deck cũ đóng sau đó: `stop()` → `setActive(false)` → `setShown(false)` → `controller.cancel()` tăng generation lần nữa và đặt `current = null`. Kết quả của pane mới đến sau, `deliver` thấy generation/`current` không còn khớp nên bỏ (`stage=lyrics_drop`); `requestedKey` của pane mới đã khớp nên `update()` không hỏi lại → kẹt `Loading`.

### 11.2 Sửa (commit `fd1f943`)

| File | Thay đổi |
|---|---|
| `ShareableLyricsController.kt` | `request(…, publish, client: Any? = null)` lưu “chủ yêu cầu”; `cancel(client: Any? = null)` chỉ hủy khi `client` chính là chủ của yêu cầu hiện hành, nếu yêu cầu hiện hành thuộc pane khác thì không làm gì; `cancel()` không tham số vẫn hủy tất cả (đăng xuất, `clearOwner`) |
| `MeloLyricsPane.kt` | `request(..., client = this)`, `cancel(this)` ở `setShown(false)` và khi không có bài; **khóa phụ**: nếu `Loading` kéo dài ≥ 40 giây thì tự hỏi lại (tránh kẹt vĩnh viễn nếu một câu trả lời nào đó mất) |
| `MeloLyricsUiPolicy.kt` | `LOADING_STUCK_MS = 40_000` và `loadingStuck(sinceMs, nowMs)`; 40 giây > 2 × (kết nối 5 s + đọc 8 s) của một lần `get` + `search` |
| Test | `ShareableLyricsControllerTest` +5: pane cũ đóng sau khi pane mới hỏi không làm mất lời của pane mới (đúng thứ tự gây lỗi), pane vẫn tự hủy yêu cầu của mình, hủy của pane chưa từng hỏi không đổi gì, `cancel()` không client vẫn hủy hết, hỏi lại khi mất câu trả lời; `MeloLyricsUiPolicyTest` +2 (ngưỡng 40 giây và ràng buộc theo timeout) |

Không đụng service, Media3, DB, TDLib, package, signer, versionCode.

### 11.3 Gate Ni (log cục bộ `docs/plans/artifacts/2026-10-31-loi-nhac-p2b/R1-20261101-145357/`)

| Lệnh | Kết quả |
|---|---|
| Test lời nhạc mục tiêu (Debug) | 23 + 21 + 18 + 27 + 20 = 109 test, 0 lỗi |
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL (1m52s); Debug 184 suite **2419 test**, 21 skipped, 0 failures, 0 errors; Release y hệt |
| `assembleShareableDebug/Release`, `-PmeloOpen=true assembleShareableDebug`, `assemblePersonalDebug` | đều BUILD SUCCESSFUL |
| `git diff --check` | sạch |

2419 = 2412 (sau chặng B) + 7 test mới.

### 11.4 Tiny build và Sony

- Bundle `MeloVault_loi-nhac-p2b_full_fd1f943_20261101.bundle` 13.428.563 byte, SHA-256 `4d13ba15208a7a08a8441e83eb0d663d5b8617f3835ea30009a03aba7cc97b73`, khớp hai đầu; clone `C:\Bots\MeloVault-LOINHAC-IsolatedQA-20261101-fd1f943` HEAD `fd1f943…`, cây sạch; `reportTelegramConfig` true; `assembleShareableDebug` BUILD SUCCESSFUL 2m52s.
- Gate APK: vc121, debuggable, cert `eecd3df7…fce8`, 33.642.436 byte, SHA-256 `62ED3500C171AAF9CCFCEE3EDDB8D7A11F3D08B65B93DA425A3790126B173DB9`. Sony `XQ-AS52`/`Sony`, nhạc đang tạm dừng (`state=2`); đúng một lệnh `adb -s QV725WEQ54 install -r` → `Success`; SHA bản cài khớp.
- **Kiểm lại Q8:** bật lại công tắc Lời nhạc (Cài đặt), mở Deck dọc ở tab Lời nhạc, rồi xoay ngang → dọc → ngang → dọc (5 giây mỗi lần). Mọi lần pane hiện đúng “Thiếu tên bài hoặc nghệ sĩ để tìm lời.” + “Tìm lời”; **không còn kẹt**; `logcat` `MeloVaultLyrics` có 0 dòng `stage=lyrics_drop`; không `FATAL EXCEPTION`/ANR. **PASS.** Ảnh: `r1-landscape-1`, `r1-portrait-1`, `r1-landscape-2`, `r1-portrait-2`, ảnh tổng hợp `00_tong_hop_rework.png`.
- Giới hạn: bài dùng để thử là bài thiếu nghệ sĩ (kết quả ra ngay, không có request mạng đang bay lúc xoay). Đường “request mạng đang bay khi xoay” chỉ được phủ bằng test JVM ở 11.2.

### 11.5 Trạng thái Sony sau REWORK (ghi đúng)

- App nay là `fd1f943` (cùng vc121, chữ ký trùng). Lần mở app sau khi cài đã khiến thẻ “Nghe tiếp” **phát lại ~27 giây** bài “#Bản nháp 1” (BOX MUSIC HOUSE) trước khi tôi tạm dừng; bài hiện tại là “#Bản nháp 1.mp3” tạm dừng ở 0:27 (trước REWORK: “Total Immersion” dừng 0:02).
- Công tắc “Lời nhạc từ LRCLIB” đang **BẬT** (tôi bật lại để kiểm).
- Xoay đã khôi phục: `accelerometer_rotation=1`, `user_rotation=0`, 1080×2520 / 420, font 1.0. **`SONY_SCREEN_END`: `Display State=OFF`.** Không uninstall, không `pm clear`, không logout, không máy bay.

### 11.6 Còn lại / chưa kiểm chứng

F2 (bài Telegram thiếu nghệ sĩ nên không tự tìm), F3 (LRCLIB `search` chập chờn 503) và F4 (thanh tab dưới ảnh bìa lớn) ở mục 10.3 **không đổi** — là quan sát/thiết kế cho chủ dự án, không phải lỗi đã sửa. Vẫn chưa kiểm: âm thanh thật, lời tự động cho bài Telegram có tên + nghệ sĩ khớp LRCLIB, “quay lại bài còn nhớ lựa chọn” và “Bỏ lời đã chọn” trên máy, chữ lớn 1.3, Release/`.open`/Personal trên máy, Y700/S25/FYT.
