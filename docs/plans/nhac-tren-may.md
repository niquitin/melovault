# Việc: Nguồn "Nhạc trên máy" cho Shareable

Ngày viết: 18/10/2026, trên HEAD `77a6b8f` (sau Beta108). Chủ dự án đã chọn "có" (18/10/2026) cho câu hỏi "có làm nguồn Nhạc trên máy không". Bản dự kiến: **Beta110** (sau Beta109 của `da-tai-khi-mo-app.md`; nếu làm trước thì Beta109).
**Chưa sửa code.** Việc này đụng kiến trúc (danh tính bài, quyền, phát) nên phiên thực hiện nên là **Opus 5.5 · Trung bình · Tự động** sau khi chủ dự án trả lời các câu ở mục 8. Điều chưa chắc ghi **[CẦN XÁC NHẬN]**.

## 1. Mục tiêu
Trong Thư viện kiểu Mới, thẻ nguồn **"Nhạc trên máy"** (`MeloSourceCardPolicy.Kind.LOCAL`, huy hiệu "♪", đã có sẵn trong logic) hiện cạnh thẻ Telegram, có số bài thật; bấm thẻ = xem và phát nhạc nằm trong bộ nhớ máy (MediaStore). Chưa cấp quyền hoặc máy không có nhạc thì **không có thẻ** (không "0 bài"). Đặc biệt hữu ích cho Shanling M3 Ultra (máy nghe nhạc) — nhưng xem mục 8.4.

## 2. Hiện trạng mã (đã đọc)
- `LocalMediaRepository` (`app/src/main/.../data/LocalMediaRepository.kt`) có sẵn truy vấn MediaStore (`LocalMediaTrack(id, volumeName, title, artist, album, durationMs, sizeBytes, mimeType, relativePath, contentUri)`, `mediaId = "device:<volume>:<id>"`, có `LocalMediaFolder`/`LocalMediaVolume`, test `LocalMediaRepositoryTest`). Nó nằm ở source set `main` nên **đã được biên dịch vào APK Shareable** nhưng Shareable không gọi (`MainActivity`/`MusicService` bị gỡ khỏi flavor này). Dùng lại làm điểm xuất phát, không viết lại.
- `AndroidManifest.xml` (main) đã khai báo `READ_MEDIA_AUDIO` và `READ_EXTERNAL_STORAGE` (≤ API 32), nên **không cần thêm quyền vào manifest**; nhưng app chưa bao giờ **xin quyền khi chạy** trong Shareable.
- Danh tính bài trong Shareable gắn với Telegram: `IndexedTelegramSong` khóa `(accountId, chatId, messageId)`, `songKey` 64-bit (`TelegramSongKey`), bảng `songs` SQLite (`melovault_tg_index_{accountId}.db`); `Song` trong `TelegramPreviewActivity` có `fileId`, `chatId`, `messageId`. Playlist/Yêu thích khóa bằng `songKey`.
- Phát: `ShareablePlaybackService` + ExoPlayer; nguồn Telegram qua DataSource `melovault-tdlib`. Bài local dùng `content://` (DataSource mặc định), chưa có đường vào service.
- Nút "♫ Chọn tệp nhạc" (`ACTION_OPEN_DOCUMENT`, `openDocumentResult`) là cách chọn **một tệp** có sẵn, không quét.
- `MeloScreenHost.sourceSummaries()` (Beta108) trả danh sách `Source(kind, total, downloaded)`; chỉ cần thêm `Source(LOCAL, total, null)` thì thẻ tự hiện.

## 3. Quyết định kiến trúc (cần chủ dự án duyệt trước khi code — xem mục 8)
**Danh tính bài local.** Ba cách:
1. **(Đề xuất, giai đoạn 1) Không đổi DB.** Đọc thẳng MediaStore (nó đã là một chỉ mục nhanh), phân trang bằng `ContentResolver` (limit/offset), bài local có mã riêng `localKey` = băm 64-bit ổn định từ `volumeName + MediaStore id` **trong không gian khóa tách biệt** (bit cao đánh dấu LOCAL, không bao giờ trùng `songKey` Telegram). Không thêm bảng, không đổi `songKey`/khóa Telegram.
2. Thêm bảng `local_songs` trong DB tài khoản (schema v3): chỉ khi cần tìm/xếp A–Z nhanh hơn MediaStore cho vài chục nghìn bài. **Đổi schema → phải duyệt.** Để giai đoạn 2 nếu đo thấy chậm.
3. Nhét vào bảng `songs` với `chatId` giả: **loại** (đụng khóa, đếm nguồn, dọn "đã xóa", đồng bộ).
**Phát.** Thêm vào service một đường phát `content://` (MediaItem từ `LocalMediaTrack.contentUri`); `NextAwarePlayer`, hẹn giờ, Bài kế, MediaSession dùng chung. Không đổi ExoPlayer/MediaSession ngoài việc thêm loại MediaItem.
**Quyền.** Chỉ xin `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (≤ 32) khi chủ dự án chủ động bấm (mục "Thêm nhạc trên máy" ở menu ☰ hoặc ô trống trong Thư viện), kèm một câu giải thích; không xin lúc mở app. Từ chối → không có thẻ, không hỏi lại liên tục (nhớ trạng thái từ chối, chỉ nhắc khi chủ dự án bấm lại). Không yêu cầu `MANAGE_EXTERNAL_STORAGE`.

## 4. Phạm vi
**Giai đoạn 1 (việc của kế hoạch này):**
- Quyền khi chạy + thẻ "Nhạc trên máy" có số bài thật (đếm MediaStore, nền).
- Bấm thẻ → danh sách bài local trong khung Thư viện hiện có (Điện thoại/Tablet/Hai khung/**Gọn**): tên, nghệ sĩ, thời lượng, định dạng; phát được; Tạm dừng/Bài kế/hẹn giờ/tua hoạt động.
- Trạng thái dòng: bài local luôn "có sẵn" (không "Chưa tải", không tiến độ tải); không có menu "Tải về".
- Sắp xếp Mới nhất (theo `DATE_ADDED`) và tên A–Z (sắp xếp trong bộ nhớ theo `search_key`-tương-đương, dùng cùng chuẩn hóa không dấu của Beta96 nếu tái dùng được); tìm theo ô tìm kiếm (lọc trong bộ nhớ hoặc `LIKE` MediaStore).
**Ngoài giai đoạn 1:** playlist/yêu thích cho bài local, thư mục (`LocalMediaFolder`), nhiều ổ (thẻ SD), đồng bộ lại khi thêm/xóa tệp (ContentObserver), ảnh bìa nhúng, waveform cho bài local (`MeloWaveform.probeBitDepth` đọc `File`, cần đọc qua `ContentResolver`), Car Mode, Drive/OneDrive, thay đổi schema. Ghi lại thành BACKLOG sau khi xong.
**Không làm:** đụng TDLib, đăng nhập, đồng bộ Telegram, `songKey`/khóa Telegram, schema DB, flavor `personal`.

## 5. Các bước làm
1. Đầu phiên: kiểm Git theo `CLAUDE.md`; chủ dự án đã trả lời mục 8 (nếu chưa: **dừng**, không đoán).
2. `MeloLocalSourcePolicy.kt` (logic thuần, mục 6) + test; chạy test riêng.
3. Lớp đọc MediaStore cho Shareable (bọc/gọi `LocalMediaRepository`, chạy executor, hủy theo lifecycle/generation): `count()`, `page(offset, limit, sort, query)`. Không chạy trên main thread.
4. `MeloScreenHost`: `sourceSummaries()` thêm LOCAL; `openSource(...)`/`snapshot()` hiểu nguồn LOCAL (thêm `selectedKind` cho LOCAL) mà **không** động tới danh sách/bảng Telegram; `MeloLibraryPane` bấm thẻ LOCAL → chọn nguồn local.
5. Xin quyền (hợp đồng `ActivityResultContracts.RequestPermission`), trạng thái từ chối được nhớ trong prefs riêng (không DB).
6. Phát: đường `content://` trong `ShareablePlaybackService` (và `playAt`/`playAll`/hàng đợi cho bài local).
7. Test Debug + Release, `assembleShareableDebug/Release`, `git diff --check`; commit `feat(shareable): local music source (MediaStore, permission on demand)`.
8. Bump vc110 (hoặc 109), gate mặc định `CLAUDE.md`, QA Sony theo mục 7, upload Drive riêng, cập nhật `HANDOFF.md`, `docs/plans/BACKLOG.md`, `docs/ui-spec.md`. Dừng.

## 6. Test JVM (tối thiểu 12)
`MeloLocalSourcePolicyTest`: `localKey` ổn định theo (volume, id), không trùng không gian `songKey` Telegram, khác nhau giữa hai tệp; thẻ chỉ có khi đã cấp quyền và `total > 0`; từ chối quyền → không thẻ; trạng thái dòng local luôn "có sẵn"; không có hành động "Tải về" cho local; sắp xếp Mới nhất/A–Z; lọc theo từ tìm (không dấu); phân trang (offset/limit, trang cuối); lỗi MediaStore (cursor null/ngoại lệ) → danh sách rỗng, không thẻ, không crash; bài thiếu title → lấy tên tệp; thiếu duration → 0, không hiện "0:00" giả. Tái dùng mẫu `LocalMediaRepositoryTest` cho phần đọc con trỏ. Mốc hiện 1.240 → khoảng 1.252+, 21 skipped, 0 failed.

## 7. Kiểm trên thiết bị
**Sony (Claude QA, an toàn Sony như `CLAUDE.md`):**
1. Cài đè; chưa cấp quyền → không có thẻ "Nhạc trên máy"; menu ☰/ô trống có "Thêm nhạc trên máy".
2. Bấm → hộp xin quyền → Cho phép → thẻ hiện "Nhạc trên máy · N bài" với N khớp số nhạc thật trên Sony; Từ chối → không thẻ, không lặp lại hộp.
3. Bấm thẻ → danh sách local; bấm bài → phát (state=3), tạm dừng/bài kế/tua hoạt động, màn khoá/tai nghe vẫn điều khiển được; chuyển lại Telegram → bài Telegram vẫn phát bình thường.
4. Telegram: số bài và danh sách y nguyên so với b108/b109 (25.10x bài), không mất dữ liệu.
5. Cần có vài tệp nhạc trên Sony: **[CẦN XÁC NHẬN]** nếu Sony chưa có, Claude chỉ được đẩy 2–3 tệp thử vào thư mục riêng `Music/MeloTest` bằng `adb push` khi chủ dự án cho phép trong chat (xóa sau QA bằng chính các tệp đó, không đụng tệp khác); nếu không cho phép thì chỉ kiểm các mục không cần tệp (quyền, không thẻ khi chưa có nhạc) và nhờ chủ dự án QA phần phát trên máy của anh.
6. Khôi phục rotation 1/0, density 420; không ANR/crash.
**Chủ dự án tự QA:** Y700 + S25 + **Shanling** (bố cục Gọn hiện danh sách local ra sao, phím vật lý, thẻ nguồn ở Gọn — hiện Gọn **không** có thẻ nguồn, nên chọn nguồn local qua menu ☰) — xem câu hỏi 8.4.

## 8. Câu hỏi cho chủ dự án (phải trả lời trước khi code)
1. **Phạm vi giai đoạn 1** như mục 4 (chỉ xem + phát, chưa Playlist/Yêu thích/thư mục)? **[CẦN XÁC NHẬN]**
2. **Không đổi DB/schema** ở giai đoạn 1 (đọc MediaStore trực tiếp, mã `localKey` riêng)? Nếu chủ dự án muốn tìm/xếp A–Z nhanh cho rất nhiều bài thì mới cần bảng `local_songs` (đổi schema, cần duyệt riêng).
3. **Đăng nhập Telegram có bắt buộc** để thấy nhạc local không? Hiện app chỉ vào thư viện sau khi đăng nhập; đề xuất giữ nguyên (nhạc local chỉ hiện sau đăng nhập) cho giai đoạn 1.
4. **Shanling:** Gọn hiện chưa có thẻ nguồn (đã chốt ở đặc tả). Muốn Gọn có mục "Nhạc trên máy" trong ☰ (đề xuất: có, vì đây là nơi nhạc local có ích nhất) hay chỉ Điện thoại/Tablet/Hai khung?
5. **Tệp thử trên Sony**: cho phép Claude `adb push` 2–3 tệp vào `Music/MeloTest` để QA (rồi xóa đúng các tệp đó) không?
6. **Nhiều thẻ trên điện thoại hẹp (360dp):** với hai thẻ thì mỗi thẻ ~ nửa khung (logic `cardWidthPx` đã có, thẻ tự chia hoặc cuộn ngang với thẻ thứ hai lộ một nửa). OK?

## 9. Tiêu chí đạt
- Test Debug + Release xanh (Ni, Tiny); APK ký `eecd3df7…ce8`, có Telegram config.
- Sony: mục 7 đúng; số trên thẻ = số nhạc thật trong MediaStore; từ chối quyền → không thẻ; Telegram không đổi (số bài, phát, đăng nhập).
- Không đổi DB/schema, TDLib, `songKey`, đăng nhập, đồng bộ (nếu chủ dự án duyệt phương án 3.1); không thêm quyền ngoài `READ_MEDIA_AUDIO`/`READ_EXTERNAL_STORAGE` đã có; không `MANAGE_EXTERNAL_STORAGE`.
- Còn mở **[CẦN XÁC NHẬN]**: Y700/S25/Shanling thật, thẻ SD/nhiều ổ, thư mục, Playlist/Yêu thích cho bài local, đồng bộ khi thêm/xóa tệp, waveform và chip chất lượng cho bài local.

## 10. Rủi ro
- **Quyền đọc nhạc** nhạy cảm hơn Telegram-only; xin có lý do, đúng lúc, không dọa; không lưu URI/đường dẫn tệp vào log.
- **Android 11+ scoped storage / Android 13+ quyền theo loại media**: máy test Sony là Android 12 (dùng `READ_EXTERNAL_STORAGE`), Y700/S25 là Android 13+/17 (`READ_MEDIA_AUDIO`) → phải kiểm cả hai nhánh quyền **[CẦN XÁC NHẬN]** trên máy thật (Sony không kiểm được nhánh 33+).
- **Hiệu năng MediaStore** với vài chục nghìn bài: truy vấn phân trang theo `limit/offset`, không tải hết; nếu chậm thì đề xuất phương án 3.2 (cần duyệt).
- **Trộn hai loại bài** trong hàng đợi/Playlist/Yêu thích (Telegram + local): giai đoạn 1 chỉ cho phát trong một nguồn mỗi lần; hàng đợi hỗn hợp để sau.
