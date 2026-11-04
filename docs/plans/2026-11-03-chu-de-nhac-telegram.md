# Duyệt nhạc Telegram theo nhóm và chủ đề

**TASK_ID** `2026-11-03-chu-de-nhac-telegram` · **Vai** lập kế hoạch · **Ngày** 03/11/2026
**Nguồn** MeloVault Shareable, nhánh tích hợp `feature/e93-03d-y700-offline-cockpit` tại `b90834f44457b60a9732e59e59c1bc9c971368c3`. Nhánh thực hiện là `dot/2026-11-03-chu-de-nhac-telegram`. [Checkpoint và prompt thực thi](2026-11-03-chu-de-nhac-telegram-ban-giao.md)

## 1. Người nghe sẽ dùng thế nào

Trong ảnh, người dùng vào một nhóm lớn rồi chọn một chủ đề. Mỗi chủ đề có lịch sử riêng, có thể chứa cả lời nhắn, ảnh bìa và nhiều tệp nhạc. Phạm vi đề xuất cho MeloVault là duyệt và nghe nhạc từ những chủ đề Telegram đã có mà tài khoản đang được xem. Việc tạo chủ đề, đăng hoặc chuyển bài trên Telegram là chức năng quản trị riêng. [Telegram Forum Topics](https://core.telegram.org/api/forum)

| Người dùng làm gì | MeloVault hiển thị hoặc thực hiện |
|---|---|
| Mở **Nhóm/kênh/bot** | Mỗi nguồn vẫn xuất hiện một lần. Nhóm đã xác nhận có chủ đề có dấu hiệu **Có chủ đề** và mũi tên mở. Kênh/nhóm thường mở thẳng danh sách bài. |
| Vào một nhóm có chủ đề | Dòng đầu là **Tất cả nhạc của nhóm**. Bên dưới là tên gốc của các chủ đề Telegram, chủ đề ghim đứng trước, sau đó theo thứ tự Telegram trả về. Cho tìm tên. Hiện các chủ đề nhìn thấy được, kể cả chủ đề chưa phân loại được bài nào. |
| Chọn **NHẠC QUỐC TẾ DSD** | Tiêu đề có tên nhóm và chủ đề. Danh sách hiện các tệp nhạc hợp lệ của đúng chủ đề, ví dụ các track WAV được đánh số trong ảnh. Dùng cách sắp xếp hiện hành của người nghe. |
| Bấm bài, Phát hết hoặc bài kế tiếp | Khi khởi tạo hàng đợi từ chủ đề, mọi bài đều thuộc phạm vi hiện chọn. Chỉ chuyển màn hình xem chủ đề khác thì nhạc đang phát tiếp tục. Hàng đợi người dùng chủ động sửa sau đó vẫn theo hành vi trình phát. |
| Nhấn Quay lại | Từ danh sách bài về đúng vị trí trong danh sách chủ đề, rồi về đúng nhóm trong màn nguồn. Tìm kiếm, bộ lọc Đã tải và vị trí cuộn gắn với đúng phạm vi. |

**Cách đọc số đếm.** Con số “1.213 messages” trong ảnh là số tin nhắn. Trong MeloVault, chỉ đếm bản ghi nhạc hợp lệ còn hiệu lực. Khi chưa biết số bài, hiện **Chưa lập chỉ mục**. Khi mới đọc một phần, hiện **Đã tìm thấy N bài**. Chỉ hiện **Chưa có nhạc** sau khi đọc hết lịch sử khả dụng. Tìm không ra vì bộ lọc và lỗi truy cập có thông báo riêng. Tên chủ đề DSD không tự xác nhận định dạng hay khả năng giải mã DSD của một tệp.

## 2. Những điểm đã kiểm trong mã nguồn

Các tên file Kotlin dưới đây thuộc `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/`.

| Vị trí ở baseline | Ý nghĩa đối với thiết kế |
|---|---|
| `TelegramPreviewActivity.kt` · `processSyncPage`, `parseTelegramSong` | Quét toàn thư viện bằng `searchMessages` qua hai pha audio/document. Parser lấy tin nhắn, chat và file. Document có danh sách phần mở rộng, gồm WAV, FLAC. Trước khi ghi SQLite, mã hiện tại bỏ qua chủ đề trên message. |
| `TelegramLibraryEntities.kt`, `TelegramLibrarySql.kt`, `AndroidTelegramLibraryDatabase.kt` | DB v2. Khóa bài là account/chat/message. Hai đường `upsertSongs` và `commitPageAndCheckpoint` có ghi kiểu replace. Cần giữ quan hệ chủ đề khi phản hồi sau thiếu metadata. |
| `TelegramLibraryDao.kt`, `InMemoryTelegramLibraryDao.kt` | Trang bài, đếm bài và số bài mỗi nguồn hiện có phạm vi tài khoản rồi đến chat. SQL và bản in-memory cần cùng quy tắc lọc. |
| `MeloBrowseUiPolicy.Scope`, `MeloBrowseState.Key`, `TelegramPreviewActivity.refreshSongRows/loadMoreSongsIfNeeded` | Phạm vi hiện nhớ chat. Thêm topic vào danh tính màn hình, trang kế, tìm kiếm, bộ lọc, vị trí quay lại và kiểm kết quả đến trễ. |
| `TelegramPreviewActivity.configureTelegramQueueFor/offlineQueueSongs` | Luồng phát lấy danh sách đang hiển thị hoặc tập Đã tải rồi giao cho service. Kiểm riêng trường hợp hai chủ đề cùng tên bài và nút Phát hết trên danh sách nhiều trang. |
| `E91_TDLIB_NATIVE_INTEGRATION.md`, `app/src/shareable/jniLibs/arm64-v8a/libtdjsonjava.so` | Repo ghim TDLib 1.8.68, commit `c15d3f5`. Đã kiểm tên `getForumTopics`, `getForumTopicHistory`, `messageTopicForum` trong binary. Hành vi thực tế với tài khoản có forum là cổng kiểm đầu tiên. |

## 3. Các bước triển khai

### S1 · Chứng minh đường đọc Telegram trước khi đổi DB

- Dùng chung TDLib owner hiện hữu. Từ chat kiểu supergroup lấy `supergroup_id` để gọi `getSupergroup` hoặc đọc đối tượng đã nhận. Chỉ `is_forum=true` mới đi vào danh sách chủ đề. Giữ ba trạng thái **chưa biết / có / không**. Khi tra cứu lỗi và chưa có cache, cho mở nhạc toàn nhóm và thử lại. Làm theo nhu cầu lúc mở nguồn, tránh hỏi mọi chat khi khởi động.
- Gọi `getForumTopics` với **chat_id** của nhóm. Trang đầu dùng bộ offset bằng 0. Trang sau dùng nguyên bộ `next_offset_date`, `next_offset_message_id`, `next_offset_forum_topic_id`. Đọc tên, ID, General/ẩn/đóng từ `topic.info`, trạng thái ghim từ `topic`. Khử trùng theo ID khi danh sách đổi giữa các trang. Chỉ kết thúc ở tín hiệu hết trang đã kiểm. Cursor lặp trong khi còn việc là trạng thái chưa hoàn tất.
- Chọn `getForumTopicHistory(chat_id, forum_topic_id, from_message_id, offset, limit)` để bổ sung nhạc cho một chủ đề. Ví dụ dùng trang 50 tin, tiếp tục từ **message ID gốc** của ranh giới, xử lý tin ranh giới lặp. Kiểm trên Sony cách TDLib trả trang cuối kể cả trang một tin. Trang ngắn hoặc toàn tin chữ chưa chứng minh đã hết. Khi cursor không tiến, dừng vòng lặp, giữ trạng thái có thể tiếp tục và chỉ đánh dấu hoàn tất nếu đã xác nhận hết lịch sử.
- Trên đường quét toàn nhóm, chỉ nhận quan hệ khi `message.topic_id.@type == messageTopicForum`, đọc `forum_topic_id` kiểu int32. Các loại thread, Saved Messages và direct messages có kiểu riêng. Với lịch sử đã gọi theo chủ đề, chấp nhận ID từ ngữ cảnh yêu cầu khi phản hồi đúng chat và thiếu metadata, nếu ID tường minh mâu thuẫn thì loại kết quả và ghi lỗi chẩn đoán tổng hợp. General dùng `is_general` và ID do TDLib cung cấp.
- **Đầu ra S1** là fixture ẩn danh, test parser/phân trang và bằng chứng đọc trên ít nhất một forum có hai chủ đề cùng một nguồn thường. Chỉ ghi kiểu đối tượng, số lượng và kết quả so khớp. Nếu thiếu forum thật, phần bằng chứng thực ghi BLOCKED, vẫn làm được test hợp đồng độc lập. Nếu binary trả khác schema, dừng đường tích hợp tại S1. [Schema TDLib đúng revision](https://github.com/tdlib/td/blob/c15d3f5a5de6e3ba5839822c451152e5e18bb700/td/generate/scheme/td_api.tl)

### S2 · Lưu chủ đề và nâng cấp thư viện cũ

- Đề xuất migration cộng thêm **v2 → v3**, áp dụng sau khi chủ dự án chốt phạm vi thay đổi schema trong kế hoạch. Giữ cả đường cài mới và nâng từ v1 qua v2. Bảng chủ đề mới lấy khóa chính `(account_id, chat_id, forum_topic_id)`, chứa tên, trạng thái General/ẩn/đóng/ghim và thứ tự. Lưu trạng thái forum ba giá trị của nguồn, bảo toàn qua lần ghi nguồn thiếu thông tin.
- Thêm `forum_topic_id` nullable vào `telegram_songs` và chỉ mục theo tài khoản, chat, topic, trạng thái xóa, ngày. `NULL` là chưa có phân loại cho bài cũ. **Tất cả nhạc của nhóm** truy vấn theo chat nên vẫn chứa bài cũ ngay sau nâng cấp. Danh sách theo chủ đề chỉ lấy quan hệ đã biết. Thư viện, playlist, tệp tải, yêu thích và khóa `songKey` tiếp tục trỏ đến cùng bài.
- Cho `getSongs/getSongCount` nhận thêm phạm vi topic tùy chọn ở cuối chữ ký với mặc định tương thích. Đưa cùng điều kiện vào danh sách, count, tìm kiếm, Đã tải và tất cả các trang. Dùng một truy vấn nhóm để đếm nhiều chủ đề và kiểm query plan với bộ dữ liệu lớn. Hai đường ghi full/incremental và bản DAO in-memory phải bảo toàn topic đã biết khi message thiếu trường. Trường hợp có quan hệ mới xác thực thì cập nhật đúng một bản ghi.
- **Đạt S2** khi nâng DB mẫu v1/v2 có dữ liệu lên v3 hai đường đều giữ số bài, khóa, khả năng tìm theo playlist và liên kết tệp cũ. Hai tài khoản, hai chat hoặc hai chủ đề trùng tên vẫn tách biệt. Có test DB SQLite thực cho migration/replace, bên cạnh test DAO thuần.

### S3 · Hiện dữ liệu nhanh và bổ sung phần nhạc còn thiếu

- Trong `processSyncPage`, lấy topic từ message nguyên gốc trước khi tạo `IndexedTelegramSong`. Giữ hai pha quét audio/document hiện có và sử dụng chung tiêu chí nhận tệp giữa quét toàn cục và quét chủ đề. Tin thông báo, bìa album, PDF và ZIP không trở thành bài. Gửi WAV/FLAC dưới dạng document vẫn được nhận theo quy tắc hiện tại.
- Khi vào nhóm, đọc danh mục local trước rồi làm mới danh sách chủ đề. Chỉ khi vào một chủ đề mới đọc lịch sử của nó. Hiện bài đã lập chỉ mục ngay. Bổ sung theo lô nhỏ có điểm dừng, ví dụ tối đa 3 trang cho một lần mở rồi hiện **Tìm thêm trong chủ đề** nếu còn dữ liệu. Một phản hồi toàn tin chữ vẫn phải tiến cursor. Không tải nội dung các file nhạc chỉ để lập danh mục.
- Lưu tiến độ riêng theo account/chat/topic, tách cursor danh mục chủ đề, cursor đọc lịch sử cũ và checkpoint quét toàn cục. Ghi bài + tiến độ trang trong một giao dịch, chỉ tiến khi ghi thành công. Sau mở lại app, tiếp tục lịch sử cũ từ checkpoint. Khi làm mới, đọc thêm đầu lịch sử tới mốc đã biết, kể cả lúc đợt quét cũ chưa xong.
- Kiểm account, yêu cầu hiện hành và generation trước cả ghi DB lẫn cập nhật giao diện. Gộp yêu cầu trùng, hủy khi rời scope, có giới hạn tải lại sau lỗi mạng/rate limit. Đổi tên giữ nguyên quan hệ. General ẩn thì ẩn hàng tương ứng. Chủ đề đóng vẫn đọc được nếu còn quyền. Chỉ loại chủ đề khỏi điều hướng khi có bằng chứng xóa/không còn xem được, tránh kết luận từ trang danh mục thiếu hoặc mạng lỗi. Khi có lỗi mất quyền xác nhận, dừng đọc mới và áp dụng chính sách truy cập hiện hành. Offline dùng cache đúng tài khoản, chỉ các tệp đã tải đủ mới bảo đảm phát offline.
- **Đạt S3** khi quét lại cùng trang hoặc chạy xen với quét toàn cục cho một bản ghi mỗi message, cập nhật đúng số bài, và quay sang chủ đề khác không bao giờ thấy kết quả đến muộn của chủ đề cũ.

### S4 · Nối cả điều hướng và hàng đợi phát

- Thêm hai phạm vi duyệt rõ ràng trong `MeloBrowseUiPolicy` / `MeloBrowseState` cho **danh mục chủ đề** và **bài của chủ đề**. Mục Tất cả dùng lại phạm vi chat. Gắn owner/chat/topic vào lưu/khôi phục màn, xoay, tìm kiếm, sort, bộ lọc, request và scroll. Phiên bản state cũ thiếu topic sẽ khôi phục vào nhóm.
- Nối các điểm mở nguồn trong `TelegramPreviewActivity` (host `openSource`, các thẻ Bento và nhánh giao diện dự phòng). Dùng lại hàng nguồn/hàng bài, Android Views và trình phát hiện hữu. Màn hẹp đi theo từng trang, màn hai khung giữ ngữ cảnh nhóm/chủ đề. Với nhóm rất nhiều mục, tải danh mục theo trang. Tìm tên từ xa theo query khi danh mục chưa đủ, cache/tách riêng kết quả tìm để không thay thế danh mục chính.
- Tách rõ **Mở chủ đề** với **Phát**. Khi bấm Phát hết, lấy toàn bộ bài **đã lập chỉ mục** khớp chủ đề và bộ lọc ở thời điểm bấm theo thứ tự đã chọn, kể cả ngoài trang đang hiển thị. Nếu lịch sử còn thiếu, báo phạm vi đã tìm thấy. Chỉ thay hàng đợi khi người dùng ra lệnh phát. Với bấm một bài, kiểm đường giữ queue cũ, lọc Đã tải, Shuffle, Next/Previous và trường hợp hai nguồn có cùng tên bài. Thêm vào playlist dùng `songKey` hiện hữu.
- **Đạt S4** khi mở/chuyển chủ đề không ngắt nhạc, Phát hết A không chèn bài B, mọi lần trở lại khôi phục đúng màn, và nguồn thường vẫn đi thẳng đến bài.

### S5 · Kiểm thử, bàn giao APK và nghiệm thu

| Kiểm tra | Bằng chứng cần có |
|---|---|
| Phân trang và phạm vi | Fixture hai nhóm có topic trùng ID, hai tài khoản, hai topic trùng tên, trang trùng/thiếu, toàn tin chữ, General, phản hồi cũ về sau khi người dùng chuyển màn, lỗi quyền/mạng. |
| Dữ liệu và hàng đợi | Fixture có hai bài trùng tên khác message, audio và document nhạc, ZIP/PDF, bài chưa rõ topic. Tổng ở nhóm bằng số message nhạc phân biệt. Kiểm tìm, count, Đã tải, Phát hết >1 trang, Next và playlist cũ. |
| Tải và hiệu năng | Danh sách dài hơn một trang, truy vấn SQLite dùng chỉ mục, không quét mọi topic khi vào nhóm, mở lần hai hiển thị cache trước, số yêu cầu trong ngân sách đã chọn. Ghi số trang/yêu cầu và độ trễ đo được, tách cold/warm. |
| Build Ni | Chạy `.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain`, rồi `.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain` và `git diff --check`. Ghi pass/skipped/failed từ XML và xác định commit chứa code. |
| Máy thật | Đưa Git bundle đã đối chiếu SHA sang clone riêng trên Tiny, build candidate có cấu hình Telegram. Xác minh package, version, cert, config và Sony Xperia 5 II theo quy trình repo. Chụp bằng chứng riêng tư, dùng dữ liệu tổng hợp khi có thể. Ghi riêng âm thanh nghe thực tế với trạng thái player. |
| Bàn giao | Hai chặng thực thi dự kiến là S1–S4 và test Ni, rồi QA cuối S5 trên Tiny/Sony. Có thể cần Sony sớm cho S1. Báo cáo, checkpoint và bộ hồ sơ APK/ảnh theo TASK_ID. Phiên mới kiểm sơ bộ, chủ dự án nghiệm thu bản cụ thể. |

**Sáu thao tác để chủ dự án thử trên Sony**

1. Mở **Nhóm/kênh/bot** rồi mở một kênh thường. Kỳ vọng thấy ngay các bài đã có.
2. Mở nhóm có forum. Đối chiếu hai tên chủ đề với Telegram. Bấm **Tất cả nhạc của nhóm** và tìm một bài có từ trước khi nâng cấp.
3. Mở một chủ đề có nhiều tệp WAV/FLAC. Kiểm đúng tên bài, tệp tài liệu nhạc xuất hiện, thông báo và ảnh bìa không được tính thành bài.
4. Tìm một bài chỉ có ở chủ đề B khi đang ở A. Quay lại danh mục rồi vào B, lặp lại và thấy bài. Nếu A chưa quét đủ, kiểm trạng thái dữ liệu một phần.
5. Bấm **Phát hết** trong A, bấm kế tiếp, rồi mở B để duyệt. Hàng đợi vẫn của A. Chỉ khi bấm một bài ở B mới chuyển hàng đợi sang B.
6. Quay về danh mục, mở lại một chủ đề đã xem, xoay màn và thử bộ lọc **Đã tải**. Đúng chủ đề, vị trí và bộ lọc được giữ. Nếu có bài đã tải đủ thì thử phát và xác nhận nghe được.

## 4. Điểm dừng và đường lui

- Chốt thay đổi DB cộng thêm ở S2 trước khi thực thi migration. Nếu baseline, schema hoặc thư viện native đã đổi, đối chiếu lại theo checkpoint trước khi viết. Nếu không thể xác thực đúng topic, giữ đường vào **Tất cả nhạc của nhóm** và trạng thái **Thử lại**.
- Nếu phát hiện mất bài cũ, lẫn tài khoản/chủ đề, sai hàng đợi hoặc migration phá dữ liệu, dừng bàn giao candidate. Đường lui cho bản sửa tiến là vô hiệu điều hướng và quét theo topic, trở lại duyệt cấp chat trên DB v3 tương thích với bản sửa. Giữ DB, playlist và tệp người dùng. Không hạ APK/DB trên máy đang có dữ liệu.
- Khả năng nghe tệp định dạng mới, tự gom album từ ảnh bìa và quản trị nội dung Telegram là các quyết định sản phẩm riêng. Mốc nghiệm thu của tính năng này là tìm đúng chủ đề, thấy đúng bài và phát đúng hàng đợi với các định dạng ứng dụng đã hỗ trợ.
