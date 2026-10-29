# Melo — Kế hoạch UI/UX và tính năng, bổ sung từ video

## 1. Mục tiêu và trạng thái tài liệu

**Video có nhiều điểm đáng học, đặc biệt là trải nghiệm tablet: đổi nguồn, duyệt bài và điều khiển phát trong cùng một màn hình.** Kế hoạch tiếp thu các cách tương tác này, giữ màu sắc và bản sắc Melo.

Các lựa chọn đã chốt:

- Ưu tiên điện thoại và tablet; phục vụ cả bài hát lẫn mixset dài.
- Đánh giá UI/UX và tính năng ngang nhau.
- Loại bình luận, livestream và nghe chung khỏi định hướng.
- Nhiều Playlist có tên được ghi thành hướng nghiên cứu sau.
- Chỉ lập kế hoạch; lập trình phải có chỉ định riêng.

**Tệp:** `D:\Bots\MeloVault-E91-Shareable\docs\plans\2026-10-29-hoc-hoi-ui-ux-telegram-music.md`.

**Trạng thái: đã ghi tài liệu xuống đĩa ngày 29/10/2026 theo yêu cầu chủ dự án.** Nội dung này thay thế bản kế hoạch trước trong hội thoại. Đây là tài liệu định hướng UI/UX và tính năng, chưa phải kế hoạch kỹ thuật để lập trình.

**Git baseline khảo sát:** `7f3cca874e525956a10f94f8caf577c6cc80f815`. Nhánh lưu tài liệu: `dot/2026-10-29-hoc-hoi-ui-ux-telegram-music`; không commit trong phiên này theo yêu cầu chủ dự án.

Cơ sở đánh giá: chín ảnh, [video bổ sung](<C:/Users/ntdun/Downloads/ScreenRecording_10-29-2026 00-10-51_1.MP4>) dài khoảng 81,6 giây và source Melo tại commit `7f3cca8`. Video được khảo sát bằng chuỗi khung hình cách nhau hai giây và các khung chi tiết; chưa đánh giá âm thanh.

## 2. Bằng chứng mới từ video

| Mốc video | Quan sát được | Bài học cho Melo |
|---|---|---|
| 00:02–00:16 | Cột nguồn bên trái, danh sách bài bên phải; thanh phát còn hiện khi đổi nguồn | Tách việc duyệt thư viện khỏi bài đang nghe |
| 00:06–00:08 | Mục Tải xuống truy cập trực tiếp từ cột trái | Đã tải phải dễ tìm, không nằm sâu trong menu |
| 00:18–00:28 | Dòng bài đổi trạng thái; xuất hiện hộp lỗi `FLOOD_WAIT_1` | Phản hồi khi chọn bài cần rõ; lỗi tạm thời nên có hướng xử lý dễ hiểu |
| 00:32–00:48 | Trình phát mở rộng cùng danh sách phía dưới; lời nhạc chuyển từ đang tìm sang lỗi | Có thể tiếp tục duyệt bài khi mở trình phát; lỗi lời nhạc cần tách khỏi lỗi phát |
| Khoảng 00:50 | Có hộp “Thêm vào danh sách phát”, nhập tên danh sách mới | Playlist có tên là tính năng riêng, không chỉ đổi biểu tượng lưu |
| 00:58–01:04 | Tim của một bài chuyển sang trạng thái đã chọn và còn hiện ở danh sách Tải xuống | Trạng thái lưu cần thống nhất giữa các màn |
| 01:04–01:10 | Danh sách Tải xuống có thêm bài; thanh tiến trình thay đổi, màn mở rộng hiện khoảng 6:45 | Cần phản hồi tải và tua nhất quán; clip chưa xác nhận chính xác cử chỉ tua |
| 01:12–01:20 | Sau khi xoay màn, danh sách và trình phát nằm cạnh nhau; đổi nguồn rồi chọn bài trong bố cục đó | Bố cục tablet nên hỗ trợ nghe và duyệt đồng thời |

Các giới hạn cần ghi đúng:

- Dấu tải và mục Tải xuống chưa chứng minh phát offline thành công.
- Clip chưa cho thấy tạo Playlist mới thành công. Hộp thoại chỉ **mô tả** việc tạo kênh Telegram riêng và chuyển tiếp tin nhắn.
- Có màn “Nghe cùng nhau”, nhưng chưa chứng minh phiên nghe chung hoạt động; tính năng này vẫn ngoài phạm vi đã chốt.
- Clip chưa chứng minh lời chạy đồng bộ, tìm kiếm toàn bộ Telegram hoặc hiệu năng với thư viện lớn.
- Bảng có nút AirPlay ở đầu clip không đủ để kết luận ứng dụng có hệ truyền phát riêng.

## 3. Định hướng giao diện và tương tác

### Thư viện, nguồn và tìm kiếm

Melo đã có ảnh bìa, tìm không dấu, bộ lọc nguồn và đổi kênh nhanh ở Hai khung. Cải tiến tập trung vào cách tiếp cận các chức năng đó.

- Trên tablet, khu vực duyệt có lối vào rõ cho **Thư viện, Đã tải, Playlist và Nguồn Telegram**.
- Danh sách nguồn cuộn độc lập, thể hiện nguồn đang chọn và số bài khi đã biết.
- Chỉ ưu tiên nguồn nhạc liên quan; không đưa toàn bộ danh sách trò chuyện lên màn nghe nhạc.
- Khi thiếu chiều rộng, danh sách nguồn thay thế tạm thời vùng danh sách bài hoặc mở từ nút Nguồn; không ép thêm cột nhỏ.
- Đổi nguồn chỉ thay nội dung đang duyệt. Bài đang nghe tiếp tục giữ nguyên cho đến khi người dùng chọn bài hoặc ra lệnh phát.
- Ô tìm kiếm nói rõ phạm vi: thư viện đã đồng bộ, nguồn đang mở, Đã tải, Playlist hoặc Nhạc trên máy.
- Phân biệt không có kết quả với đang cập nhật dữ liệu.

Dòng bài gồm ảnh bìa, tên bài, metadata đáng tin cậy, thời lượng, trạng thái tải và menu. Tên bài tối đa hai dòng trên điện thoại/tablet; nguồn vẫn nhận biết được khi xem nhiều kênh.

Không suy tên kênh thành nghệ sĩ, không lặp “Unknown artist” trên mọi dòng và không xóa các bản trùng tên. Thông tin phiên bản giúp người dùng phân biệt chúng.

### Trình phát thu gọn và Đang phát

- Giữ Thanh đáy gộp của Melo: ảnh bìa, tên bài, nguồn/nghệ sĩ, tiến trình và điều khiển chính.
- Giữ “Nghe tiếp” khi chưa nạp bài; mở ứng dụng không tự phát.
- Chạm vùng thông tin mở Đang phát. Thanh tiến trình thu gọn vẫn chỉ hiển thị trong định hướng hiện tại; clip chưa đủ căn cứ để chốt thêm thao tác tua tại đây.
- Khi trở về thư viện, giữ từ khóa, bộ lọc và vị trí cuộn.

Màn Đang phát ưu tiên ảnh bìa vừa phải, tên bài, thanh tua, thời gian, các nút phát và vùng chuyển giữa **Tiếp theo / Lời nhạc**. Nút phát cùng thanh tua luôn thấy khi cuộn nội dung.

Giữ thao tác −30/+60 giây, hẹn giờ và thông tin chất lượng của Melo. Thời lượng nhiều giờ dùng dạng giờ–phút–giây.

Mặc định mở Tiếp theo; khi người dùng đổi tab, giữ lựa chọn trong phiên và khi xoay màn. Chỉ đưa tab Lời nhạc vào giao diện sử dụng khi tính năng đó sẵn sàng.

### Tablet: bổ sung trọng tâm từ video

Áp dụng nguyên lý **duyệt và điều khiển cùng lúc**, giữ cách bố trí trái/phải quen thuộc của Melo.

- Trên Hai khung, chọn nguồn và bài trong khung thư viện; bài hiện tại cập nhật tại khung phát.
- Khi mở nội dung bổ trợ, hàng chờ hoặc lời nhạc dùng khung phù hợp mà không làm mất điều khiển phát.
- Xoay màn giữ bài, vị trí phát, nguồn, bộ lọc và tab đang chọn.
- Thu gọn hoặc đóng trình phát mở rộng không đồng nghĩa dừng nhạc.
- Bài đang nghe, bài đang được mở và nguồn đang duyệt là ba trạng thái riêng; không dùng cùng một dấu hiệu gây nhầm.

Shanling và Car Mode chỉ thuộc phạm vi đánh giá tương thích, chưa áp bố cục mới lên hai chế độ này.

### Lưu và Playlist

Video cho thấy Yêu thích và Playlist có tên là hai khái niệm riêng. Với Melo:

- Nhóm ưu tiên vẫn dùng **Lưu/Bỏ lưu vào Playlist hiện hữu**, có thể đặt nút ☆/★ trực tiếp trên dòng bài.
- Chạm lưu không phát bài hoặc mở menu dòng.
- Trạng thái lưu thống nhất giữa thư viện, Đã tải và Đang phát; thất bại không hiện thành đã lưu.
- Khi thiếu chỗ, đưa thao tác vào ⋮, giữ vùng bấm đủ lớn.
- Nhiều Playlist có tên thuộc phần nghiên cứu sau; chưa chọn mô hình dữ liệu, cách đồng bộ hoặc cơ chế tạo kênh/chuyển tiếp tin nhắn.

### Độ rõ và trạng thái lỗi

Đây là phần được nâng ưu tiên sau khi xem video.

- Khi chọn bài, hiện rõ đang mở, đang tải hoặc cần chờ; tránh cảm giác bấm không có tác dụng.
- Với yêu cầu chờ từ Telegram, thông báo bằng tiếng Việt và chỉ hiện thời gian khi có dữ liệu thật. `FLOOD_WAIT_X` yêu cầu chờ số giây tương ứng trước khi lặp hành động. [Tài liệu Telegram](https://core.telegram.org/api/errors)
- Lỗi tạm thời ưu tiên thông báo ngay tại khu vực liên quan, hạn chế hộp thoại che toàn bộ thư viện.
- Phân biệt **đã tải đủ**, đang tải, đang phát từ mạng và tệp không còn dùng được.
- Không lấy việc đã nghe vài giây làm bằng chứng bài sẵn sàng offline.
- Giảm nền mờ, tăng độ rõ chữ phụ; dùng biểu tượng hoặc chữ cùng màu trạng thái.
- Giữ vùng bấm tối thiểu 48 × 48 dp và bố cục đọc được khi tăng cỡ chữ. [Hướng dẫn Android](https://support.google.com/accessibility/android/answer/7101858?hl=en)

## 4. Ưu tiên tính năng và lời nhạc

Các mức này là thứ tự cân nhắc, **không phải lệnh triển khai**.

| Mức | Hạng mục |
|---|---|
| P1 | Duyệt nguồn và điều khiển đồng thời trên tablet; giữ ngữ cảnh khi xoay màn |
| P1 | Danh sách dễ đọc, phạm vi tìm kiếm rõ, lưu nhanh vào Playlist hiện hữu |
| P1 | Phản hồi mở bài/tải/chờ/lỗi và trạng thái Đã tải đáng tin cậy |
| P1 | Hàng chờ dễ mở, bài hiện tại và trạng thái tạm dừng nhất quán |
| P2 | Lời nhạc cho Shareable; ảnh bìa nhất quán giữa các kiểu màu |
| Nghiên cứu sau | Nhiều Playlist có tên; ghim nguồn thường dùng |

**Lời nhạc cần được thiết kế theo trạng thái:**

- Đang tìm.
- Có lời đồng bộ.
- Có lời văn bản.
- Không tìm thấy bản phù hợp.
- Không thể truy vấn lúc này.
- Có bản đã lưu để xem khi không truy vấn được.

Video cho thấy cả thông báo lỗi truy vấn và thông báo không tìm thấy lời. Melo cần phân biệt hai trường hợp; không kết luận bài là nhạc không lời chỉ vì tìm kiếm không có kết quả.

Khi có lời đồng bộ, cho phép cuộn xem đoạn khác rồi trở lại dòng hiện tại. Khi đổi bài, kết quả cũ không được xuất hiện trên bài mới. Trạng thái trống/lỗi cần gọn, có đường chuyển sang hàng chờ và không ảnh hưởng phát nhạc.

Repo có thành phần lời nhạc tại màn cũ qua `CarApiClient`, nhưng chưa thấy nối vào UI Shareable. Việc dùng lại chỉ là hướng khảo sát; chưa chốt đường dịch vụ. LRCLIB có lời văn bản và lời có thời gian, nhưng khớp bản thu phụ thuộc metadata và thời lượng, nên remix/mixset cần kiểm riêng. [Tài liệu LRCLIB](https://www.lrclib.net/docs)

Không gửi định danh tài khoản, nhóm hoặc tin nhắn Telegram làm dữ liệu tra lời; không sửa dữ liệu gốc để ép khớp.

## 5. Tiêu chí đánh giá và điểm dừng

Các kịch bản dưới đây là tiêu chí cho thiết kế và kiểm thử về sau, chưa chạy QA trong phiên này.

| Kịch bản | Kỳ vọng |
|---|---|
| Tablet đang nghe A, chuyển nguồn để tìm B | Vẫn thấy điều khiển và thông tin A; chỉ đổi bài khi có lệnh phát |
| Mở/thu gọn Đang phát, xoay dọc/ngang | Giữ bài, vị trí, bộ lọc, từ khóa và ngữ cảnh danh sách |
| Lưu một bài rồi xem ở màn khác | Trạng thái thống nhất; không vô tình phát bài; lỗi lưu được báo đúng |
| Bài tải chậm hoặc Telegram yêu cầu chờ | Có phản hồi rõ; thời gian chờ đúng dữ liệu; không báo đã tải đủ |
| Lời nhạc lỗi, không tìm thấy hoặc đổi bài nhanh | Thông báo đúng loại; không hiện lời bài trước; điều khiển phát vẫn dùng được |
| Mixset trên hai giờ, tên dài, cỡ chữ lớn | Thời lượng dễ đọc; tua chính xác; nút chính không bị che hoặc thu nhỏ |

**API và dữ liệu:** tài liệu này không thay API, schema, định danh bài, cơ chế đồng bộ Playlist, tài khoản hoặc playback. Hạng mục được chọn sau này cần kế hoạch kỹ thuật riêng trước khi lập trình.

**Đã làm:** lưu kế hoạch đã bổ sung bằng chứng theo mốc video, nâng ưu tiên UX tablet và xử lý trạng thái lỗi, ghi nhiều Playlist thành hướng nghiên cứu sau theo lựa chọn của chủ dự án.

**Chưa kiểm chứng:** âm thanh, phát offline thật, tìm kiếm, tạo Playlist thành công, đồng bộ lời và hiệu năng trên thiết bị.

**Điểm dừng:** đã ghi tệp Markdown trên nhánh tài liệu riêng, Git HEAD giữ nguyên baseline. Thay đổi trong phiên chỉ là tệp kế hoạch này; giữ nguyên thư mục đính kèm có sẵn. Không sửa code, build, cài APK, commit hoặc tự mở nhiệm vụ lập trình.
