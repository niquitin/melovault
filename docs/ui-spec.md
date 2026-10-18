# MeloVault – Đặc tả giao diện

Oct 17, 2026 · Bs Dũng

## Tổng quan

MeloVault dùng một giao diện tối tông xanh ngọc (teal) cho cả 4 thiết bị, chỉ đổi bố cục và cỡ chữ theo màn hình. Hướng xanh dương (concept 1–4) bị loại, chỉ lấy lại hai ý: thanh điều hướng dọc bên trái (concept 3) và cột danh sách kênh Telegram kèm số bài (concept 4).

| Mockup gốc | Dùng cho |
| --- | --- |
| Thư viện dọc (danh sách + mini player + thanh dưới) | S25 Ultra, Shanling M3 Ultra (bản rút gọn), Y700 dọc |
| Thư viện ngang hai khung (trình phát trái, danh sách phải) | Y700 ngang |
| Nguồn nhạc Telegram (kho tổng hợp + lưới thẻ kênh) | Y700, ô tô (thẻ phóng to) |
| Studio Deck (Đang phát + hàng đợi, sóng âm, EQ, hẹn giờ) | Màn Đang phát trên mọi thiết bị |
| Car Mode (nút lớn, lật trang, tua -30s/+60s) | Màn hình ô tô FYT |

Ba nguyên tắc:

1. Chọn bố cục theo độ rộng màn hình thực tế, không theo tên máy.
2. Mọi bài hát luôn hiện rõ trạng thái: đang phát, đã tải về máy, hay còn trên Telegram.
3. Car Mode ưu tiên bấm trúng hơn đẹp: nút to, ít chữ, không bắt buộc vuốt.

## Hệ màu và chữ

Các mã màu dưới đây lấy gần đúng từ mockup teal; khi code sẽ đặt thành biến dùng chung để đổi một chỗ là đổi toàn app.

| Vai trò | Mã màu | Dùng ở đâu |
| --- | --- | --- |
| Nền | #0B1622 | Nền toàn màn |
| Bề mặt | #12212E | Thẻ, dòng bài hát, ô tìm kiếm |
| Bề mặt nổi | #1A2C3A | Nút phụ, chip chưa chọn |
| Viền | #24394A | Viền thẻ, đường kẻ |
| Nhấn chính | #2FD3B0 | Nút Phát, chip đang chọn, thanh tiến trình, dấu Đã tải |
| Nhấn dịu | #14463F | Nền dòng đang phát |
| Chữ chính | #EAF2F5 | Tên bài, tiêu đề |
| Chữ phụ | #9FB3BF | Tên kênh, thời lượng, ghi chú |
| Chữ trên nền nhấn | #062019 | Biểu tượng trên nút Phát màu teal |
| Cảnh báo / lỗi | #FF7A6B | Tải lỗi, mất kết nối |

Cỡ chữ và cỡ nút theo từng hồ sơ (đơn vị dp/sp của Android; riêng Car Mode tính theo pixel thật của FYT). Font: Roboto có sẵn, đọc tiếng Việt tốt.

| Hồ sơ | Tên bài | Chữ phụ | Ảnh bìa trong danh sách | Chiều cao dòng | Nút Phát/Dừng | Vùng bấm tối thiểu |
| --- | --- | --- | --- | --- | --- | --- |
| Máy nhỏ (Shanling) | 16sp | 13sp | 40dp | 56dp | 56dp | 48dp |
| Điện thoại (S25 Ultra) | 16sp | 13sp | 56dp | 72dp | 64dp | 48dp |
| Tablet (Y700) | 17sp | 14sp | 64dp | 80dp | 72dp | 48dp |
| Car Mode (FYT) | 40px | 30px | 128px | 160px | 240×160px | 100px (15mm) |

Bo góc: thẻ và dòng 16dp, chip và nút dạng viên thuốc. Khoảng cách cơ bản 8dp; lề màn 16dp trên điện thoại, 24dp trên tablet và ô tô.

## Cách app chọn bố cục

App đo độ rộng cửa sổ (tính bằng dp) mỗi khi mở hoặc xoay màn, rồi chọn một trong bốn bố cục dưới đây. Car Mode là công tắc riêng, đè lên mọi quy tắc khác.

| Điều kiện | Bố cục | Thiết bị thường rơi vào |
| --- | --- | --- |
| Car Mode đang bật | Car Mode | FYT 7870 |
| Rộng dưới 600dp và cao dưới 700dp | Gọn (máy nhỏ) | Shanling M3 Ultra |
| Rộng dưới 600dp | Điện thoại dọc | S25 Ultra |
| Rộng 600–839dp | Tablet dọc | Y700 cầm dọc |
| Rộng từ 840dp | Hai khung ngang | Y700 cầm ngang, S25 Ultra xoay ngang |

Về Car Mode:

- Bật/tắt trong Cài đặt, nhớ riêng cho từng máy.
- Lần đầu mở trên màn ngang rộng từ 1200dp, app hỏi “Bật Car Mode?” thay vì tự bật.
- Màn FYT thường khai báo mật độ điểm ảnh thấp, nên app sẽ “thấy” màn rất rộng; nếu chỉ dựa vào độ rộng, chữ sẽ quá nhỏ khi lái.

Các ngưỡng 600/700/840/1200dp là ước lượng. Bước đầu tiên khi code là làm màn “Thông tin màn hình” trong Cài đặt (hiện độ rộng, chiều cao, mật độ), mở trên S25 Ultra và Y700 rồi gửi lại số đo; Shanling và FYT đã có số tính sẵn ở phần Thông số màn hình.

## Giả lập thiết bị (để thử khi không có máy thật)

Chủ dự án thường không có sẵn Shanling (máy nghe nhạc) và FYT (gắn trên ô tô) khi đang code. Màn Y700 gen 3 có 2560 × 1600 điểm ảnh, lớn hơn cả FYT (1920 × 1200) lẫn Shanling (768 × 1280), nên app có mục “Giả lập thiết bị” trong Cài đặt (chế độ thử nghiệm):

- Chọn: Tắt · Shanling M3 Ultra (768 × 1280, mật độ 320) · FYT 7870 mật độ 160 · FYT 7870 mật độ 240.
- App vẽ giao diện bên trong một khung có đúng số điểm ảnh và mật độ đó (căn giữa, phần thừa tô tối), để bố cục, số bài mỗi trang và chữ tràn hiện y như máy thật.
- Kích thước vật lý sẽ khác máy thật (Car Mode trên Y700 nhìn nhỏ hơn); dùng để kiểm tra bố cục, không để đánh giá độ dễ đọc khi lái.
- Chế độ này không đụng tới dữ liệu, đăng nhập, playlist hay cache.

## Thông số màn hình

Shanling M3 Ultra và FYT 7870 là hai máy dùng chính nên được tính sẵn từ thông số phần cứng; S25 Ultra và Y700 là máy phụ, đo bằng màn “Thông tin màn hình”.

| Máy | Độ phân giải | Kích thước | Mật độ vật lý | Mật độ Android | Kích thước theo dp | Ưu tiên |
| --- | --- | --- | --- | --- | --- | --- |
| Shanling M3 Ultra | 768 × 1280 | 4.2" | ~355 ppi | 320 (giả định) | 384 × 640dp | Chính |
| FYT 7870 | 1920 × 1200 | 13.1" | ~173 ppi | 160–240 tuỳ firmware | 1920 × 1200dp đến 1280 × 800dp | Chính |
| Lenovo Y700 gen 3 | 2560 × 1600 | 8.8" | ~343 ppi | Đo bằng app | Đo bằng app | Phụ |
| Samsung S25 Ultra | 3120 × 1440 (thường chạy 2340 × 1080) | 6.9" | ~500 ppi | Đo bằng app | Đo bằng app | Phụ |

Nguồn: https://en.shanling.com/article/101276 , https://m.gsmarena.com/lenovo_legion_y700_(2025)-ampp-13438.php . Mật độ vật lý tính từ độ phân giải và đường chéo.

**Số đo thật Shanling M3 Ultra** (chủ dự án đo bằng màn “Cài đặt giao diện (thử nghiệm)”, 18/10/2026, Android 10, API 29, hướng dọc, cỡ chữ hệ thống ×1.00). Số đo thật thay cho các số tính sẵn ở bảng trên.

| Mục | Giá trị |
| --- | --- |
| Độ phân giải màn | 768 × 1280 px |
| Cửa sổ app (khi có thanh điều hướng) | 768 × 1208 px = 384 × 574dp |
| Mật độ Android | 320 dpi (xhdpi, ×2.00) |
| Mật độ vật lý (xdpi × ydpi) | 278.7 × 232.2 ppi |
| Kích thước ước tính | 76 × 127 mm (~5.8") |
| 1dp | ≈ 0.199 mm (48dp ≈ 9.5 mm) |
| Bố cục app chọn | Gọn (máy nhỏ) |

Hệ quả: cửa sổ thật cao 574dp (không phải 640dp), và khi bàn phím mở chỉ còn khoảng 360dp tính từ đỉnh cửa sổ (đã gồm đầu trang). Màn đăng nhập phải xếp vừa trong khoảng đó (xem “Màn đăng nhập Telegram” ở phần Thành phần dùng chung). Dòng “Mật độ 320 (giả định)” và “4.2"” trong bảng trên chưa đúng với máy đo được.

**Màn đăng nhập Telegram** (số điện thoại → mã → mật khẩu cấp 2): mỗi bước có tiêu đề 18sp đậm, một dòng giải thích 13sp màu chữ phụ, ô nhập 48dp, chữ lỗi đỏ (#FF7A6B) ngay dưới ô (không xóa ô), nút “Tiếp tục” 48dp, hàng nút phụ 48dp. Chưa đăng nhập thì ẩn Thanh đáy gộp (trước đây là mini player) và “Đăng xuất Telegram”; “Đăng nhập bằng QR” chỉ ở bước số điện thoại. Form nằm trong khung cuộn, cao tối đa bằng phần màn còn lại phía trên bàn phím.

**Shanling M3 Ultra — ngân sách chiều cao** (theo số đo thật: cửa sổ app 574dp, dọc, có Thanh đáy gộp):

| Phần | Chiều cao |
| --- | --- |
| Đầu trang (☰, tên nhóm, tìm kiếm, ⋮) | 48dp |
| Danh sách: 8 bài × 56dp | 448dp |
| Thanh đáy gộp | 72dp |
| Cộng | 568dp / 574dp |

Cần đối chiếu lại: 1208px ở mật độ ×2.00 là 604dp, khác con số 574dp ghi ở trên; nếu cửa sổ thật là 604dp thì dư thêm khoảng nửa dòng. Theo số đo thật, 1dp ≈ 0,2mm nên nút 48dp ≈ 9,5mm, bấm thoải mái. Không làm nút nhỏ hơn 48dp.

**FYT 7870 — tính theo pixel thật, không theo dp.** Màn rộng 282 × 176mm, nhìn từ khoảng 70cm (điện thoại khoảng 30cm), nên chữ và nút phải to gấp khoảng 2,3 lần điện thoại. Vì firmware FYT có thể đặt mật độ Android bất kỳ, Car Mode đọc mật độ vật lý thật của màn (xdpi/ydpi) và quy đổi từ milimet, nên hiển thị đúng kích thước dù firmware đặt 160 hay 240.

| Thành phần Car Mode | Pixel trên FYT | Kích thước thật |
| --- | --- | --- |
| Cột điều hướng (rộng) | 160px | 24mm |
| Khung trình phát (rộng) | 760px | 112mm |
| Khung danh sách (rộng) | 1000px | 147mm |
| Tên bài trong khung trình phát | 48px | 7mm |
| Tên bài trong danh sách | 40px | 6mm |
| Chữ phụ | 30px | 4,4mm |
| Ảnh bìa trình phát | 360px | 53mm |
| Dòng bài hát (cao), 5 bài mỗi trang | 160px | 24mm |
| Ảnh bìa trong dòng | 128px | 19mm |
| Nút Lùi / Tiến / Tua (cao) | 160px | 24mm |
| Nút Phát/Dừng | 240 × 160px | 35 × 24mm |
| Thanh tiến trình / nút kéo | 16px / 48px | 2,4mm / 7mm |
| Nút lật trang (cao) | 140px | 21mm |

Tổng chiều cao khung danh sách: 5 dòng + khoảng cách + nút lật trang + lề = khoảng 1080px trên 1200px. Car Mode ẩn thanh hệ thống (toàn màn) để có đủ chỗ.

**Phiên bản Android:** Shanling M3 Ultra chạy Android 10, nên app phải hỗ trợ từ Android 10 trở lên (app hiện có minSdk 26, đã đạt). FYT 7870 cũng thường chạy Android 10.

## Thành phần dùng chung

Mọi màn ghép từ cùng một bộ thành phần; chỉ cỡ và số thông tin hiện ra thay đổi theo hồ sơ.

**Dòng bài hát** (theo mockup Thư viện dọc): ảnh bìa vuông bo góc, tên bài (tối đa 2 dòng, cắt bằng dấu …), dòng trạng thái, thời lượng bên phải, nút ⋮. Bấm cả dòng = phát; giữ lâu = menu.

**Trạng thái bài hát** — luôn có biểu tượng kèm chữ, không chỉ dựa vào màu:

| Trạng thái | Biểu tượng | Chữ | Màu |
| --- | --- | --- | --- |
| Đang phát | 3 vạch nhảy | Đang phát | Nhấn chính, nền dòng nhấn dịu |
| Đã tải về máy | Dấu tích tròn | Đã tải · MP3 320kbps | Nhấn chính |
| Đang tải | Vòng tiến trình | Đang tải 45% | Nhấn chính |
| Còn trên Telegram | Đám mây | Telegram · Chưa tải | Chữ phụ |
| Lỗi | Tam giác | Tải lỗi – bấm thử lại | Cảnh báo |

**Thanh đáy gộp** (thay cho mini player + thanh điều hướng dưới riêng rẽ; học từ app MeloWave, 18/10/2026). Chỉ dùng cho bố cục dọc (Gọn, Điện thoại, Tablet dọc); bố cục ngang và Car Mode giữ cột điều hướng trái và khung trình phát lớn như cũ.

- Một khối duy nhất ở đáy màn, gồm từ trên xuống: thanh tiến trình mảnh 2dp sát mép trên (màu nhấn chính); một dòng tên bài · tên kênh (bấm = mở màn Đang phát, vuốt lên cũng mở); một hàng nút.
- Hàng nút: tab bên trái · Lùi · **Phát/Dừng ở giữa, nút tròn to nhất** · Tiến · tab bên phải. Tab đang chọn có nền nhấn dịu và chữ nhấn chính.
- Không được có chỗ điều khiển phát thứ hai trên cùng màn (không thêm thẻ “đang phát” riêng phía trên thanh đáy).
- Khi chưa có bài nào phát: ẩn dòng tên bài và thanh tiến trình, nút Phát mờ đi.
- Chưa đăng nhập Telegram: ẩn cả thanh (xem “Màn đăng nhập Telegram”).

| Hồ sơ | Chiều cao thanh đáy | Dòng tên bài | Nút Phát | Lùi / Tiến | Tab |
| --- | --- | --- | --- | --- | --- |
| Gọn (Shanling) | 72dp | 18dp, chữ 13sp | tròn 52dp | 48dp | 2 tab: Thư viện · Telegram; Playlist, Đã tải, Cài đặt vào menu ☰ |
| Điện thoại (S25) | 88dp | 22dp, chữ 14sp | tròn 60dp | 48dp | 4 tab: Thư viện · Telegram · Playlist · Đã tải (2 tab mỗi bên nút) |
| Tablet dọc (Y700) | 96dp | 24dp, chữ 15sp | tròn 64dp | 56dp | 4 tab như điện thoại |

Mọi vùng bấm vẫn tối thiểu 48dp. Trên Shanling, nút vật lý của máy cũng điều khiển Lùi / Phát / Tiến như thường.

**Màn Đang phát** (theo Studio Deck): ảnh bìa lớn, tên bài, tên kênh, **hàng viên thông số chất lượng**, thanh sóng âm để tua, hàng nút -30s / Lùi / Phát / Tiến / +60s, hàng nút phụ có chữ bên dưới biểu tượng (Equalizer · Hàng đợi · Hẹn giờ · Tải về · Lưu), góc trên có menu ⋮. Công tắc “Tải trước bài kế” nằm trong hàng đợi.

**Hàng viên thông số chất lượng** (học từ MeloWave): một hàng 3–4 “viên” bo tròn ngay dưới tên bài, mỗi viên có biểu tượng nhỏ + chữ: Định dạng (FLAC / MP3 / M4A) · Độ sâu bit và tần số mẫu (24bit · 96kHz; với MP3/M4A chỉ ghi tần số, ví dụ 44.1kHz) · Bitrate (320 kbps) · Dung lượng (34,2 MB). Viên cao 32dp, chữ 13sp, nền bề mặt nổi, viền nhấn dịu. Thiếu thông tin nào thì bỏ viên đó, không hiện “?” hay “0”. Viên “Hi-Res” (màu nhấn chính) chỉ hiện khi file lossless từ 24bit/48kHz trở lên. Trên Shanling hàng viên luôn hiện (máy hi-fi); trong Car Mode dùng bản rút gọn 1 dòng chữ như cũ.

**Chip lọc**: Tất cả · Đã tải (số bài) · Playlist · các thể loại hay nghe (Chillout, LakHouse, Mixset >30p). Cuộn ngang khi không đủ chỗ.

**Thanh tìm kiếm**: chữ gợi ý hiện tổng số bài (“Tìm trong 27.971 bài hát…”), nút bộ lọc ở cuối; tìm ngay khi gõ, không dấu vẫn ra (gõ “su thanh hoa” ra “Sử Thanh Hóa”).

**Thẻ nguồn nhạc** (học từ MeloWave): đầu màn Thư viện có một hàng thẻ, mỗi nguồn một thẻ gồm biểu tượng, tên nguồn, số bài và số bài đã tải: Telegram (27.971 bài · đã tải 412) · Nhạc trên máy · Google Drive (khi mở rộng sau này). Bấm thẻ = lọc danh sách theo nguồn đó. Nguồn chưa kết nối thì không hiện thẻ (không hiện thẻ “0 bài”). Thẻ cao 88dp, cuộn ngang, thu gọn khi cuộn danh sách xuống. Trên Shanling không đặt hàng thẻ trong Thư viện để dành chỗ cho danh sách; chọn nguồn qua menu ☰. Car Mode dùng lưới thẻ lớn trong màn Telegram như đã mô tả.

**Thanh điều hướng**: khi dọc, nằm trong Thanh đáy gộp (2 tab trên Shanling, 4 tab trên điện thoại và tablet dọc). Khi ngang và Car Mode, là cột dọc bên trái: Thư viện · Telegram · Playlist · Đang phát.

## Samsung S25 Ultra (máy phụ)

Dùng nguyên mockup Thư viện dọc, hồ sơ Điện thoại; đây là bố cục chuẩn để các máy khác rút gọn hoặc mở rộng từ đó.

Từ trên xuống:

1. Đầu trang: chữ MeloVault, số bài đã đồng bộ kèm biểu tượng đám mây, nút Cài đặt, nút ⋮.
2. Thanh tìm kiếm.
3. Hàng chip lọc, cuộn ngang.
4. Tiêu đề nhóm đang xem (ví dụ “Nhạc Lạnh – Chillout (EMI) · 77 bài”) kèm nút Phát hết.
5. Hàng thẻ nguồn nhạc (thu gọn khi cuộn xuống).
6. Danh sách bài, dòng cao 72dp, ảnh bìa 56dp.
7. Thanh đáy gộp 88dp (4 tab + Lùi / Phát / Tiến).

Các điểm riêng cho máy này:

- Màn cao nên phần trên khó với bằng một tay: khi cuộn xuống, đầu trang thu nhỏ và chỉ còn thanh tìm kiếm.
- Xoay ngang thì chuyển sang bố cục Hai khung như Y700.
- Màn Đang phát dạng dọc: ảnh bìa chiếm gần hết chiều ngang, bên dưới là tên bài, sóng âm, hàng nút; vuốt lên để xem hàng đợi.
- Có điều khiển trên màn hình khoá và thông báo phát nhạc chuẩn Android, để đồng hồ và tai nghe Bluetooth điều khiển được.

## Shanling M3 Ultra (máy dùng chính)

Dùng hồ sơ Gọn: vẫn là bố cục dọc như S25 Ultra nhưng bớt thành phần để danh sách có chỗ. Máy này chủ yếu để nghe file đã tải, nên mặc định mở vào chip “Đã tải” (chờ chủ dự án xác nhận).

| Thành phần | S25 Ultra | M3 Ultra |
| --- | --- | --- |
| Đầu trang | Logo, số bài, Cài đặt, ⋮ | Chỉ biểu tượng tìm kiếm và ⋮ |
| Thanh tìm kiếm | Luôn hiện | Ẩn, bấm biểu tượng mới mở |
| Chip lọc | Cuộn ngang | Bỏ; chọn qua menu ⋮ |
| Dòng bài hát | 2 dòng, ảnh 56dp | 1 dòng tên + biểu tượng trạng thái nhỏ, ảnh 40dp |
| Thẻ nguồn nhạc | Hàng thẻ đầu Thư viện | Bỏ; chọn nguồn qua menu ☰ |
| Thanh đáy gộp | 88dp, 4 tab | 72dp, 2 tab (Thư viện · Telegram); mục còn lại vào menu ☰ góc trái |
| Màn Đang phát | Đầy đủ | Ảnh bìa, tên bài, hàng viên thông số chất lượng, thanh tiến trình, 3 nút Lùi / Phát / Tiến; phần còn lại vuốt lên mới thấy |

Các điểm riêng:

- Máy có nút vật lý (phát, chuyển bài, âm lượng): app phải nhận được các nút này cả khi tắt màn.
- Hiện nhãn chất lượng file (FLAC, 24bit, 96 kHz…) rõ hơn các máy khác, vì đây là máy nghe nhạc chuyên dụng.
- Không tải ảnh bìa độ phân giải cao trong danh sách để đỡ tốn pin và bộ nhớ (Snapdragon 665, RAM 3GB).
- Chưa rõ máy có đủ mạnh để đồng bộ ~28.000 bài từ Telegram hay không; cần thử thực tế.

## Lenovo Y700 gen 3 (máy phụ, máy dùng để thử)

Cầm ngang dùng bố cục Hai khung; cầm dọc dùng bố cục điện thoại nhưng danh sách chia lưới. Hồ sơ Tablet cho cả hai.

**Cầm ngang – Thư viện** (theo mockup Thư viện ngang):

- Cột điều hướng dọc bên trái, rộng 80dp.
- Khung trái chiếm khoảng 40% chiều ngang: trình phát với ảnh bìa lớn, tên bài, nhãn chất lượng, thanh tiến trình, hàng nút Lùi / Phát / Tiến, hàng phụ Đảo / Lặp / Lưu / Hàng đợi.
- Khung phải: dòng trạng thái đồng bộ, chip lọc, thanh tìm kiếm, danh sách bài.
- Thanh chữ cái A–Z ở mép phải để nhảy nhanh trong thư viện lớn; mỗi chữ cao ít nhất 24dp, bấm giữ và kéo dọc để lướt.

**Cầm ngang – Nguồn Telegram** (theo mockup Nguồn nhạc Telegram):

- Thẻ lớn “Kho tổng hợp” trên cùng: tổng số bài, số bài đã tải, nút Phát ngẫu nhiên.
- Lưới 3 cột thẻ kênh, mỗi thẻ: ảnh, chấm xanh đã đồng bộ, tên kênh, số bài, số bài đã tải.
- Dòng thu gọn “Các kênh chưa có nhạc” ở cuối; nút Quét lại và Ẩn kênh 0 bài ở góc trên.
- Khi đang mở một kênh, cột trái chuyển thành danh sách kênh kèm số bài (ý từ concept 4) để đổi kênh không cần quay lại.

**Cầm ngang – Đang phát**: nguyên Studio Deck — khung trái đĩa nhạc + sóng âm + nút, khung phải hàng đợi đánh số với trạng thái từng bài.

**Cầm dọc**: giống S25 Ultra, nhưng màn Telegram chia lưới 2 cột thẻ, Thanh đáy gộp cao 96dp.

## Màn hình ô tô FYT 7870 – Car Mode (máy dùng chính)

Dùng mockup Car Mode làm nền, thêm cột điều hướng dọc bên trái (gần ghế lái ở xe tay lái thuận). Mục tiêu: mọi thao tác thường dùng xong trong một lần chạm, không phải vuốt.

Bố cục ngang 1920×1200, từ trái sang phải:

1. Cột điều hướng 160px: Nhạc · Telegram · Playlist · Đã tải · Tìm · Cài đặt; biểu tượng 56px, chữ 26px.
2. Khung trình phát 760px: ảnh bìa 360px cạnh tên bài 2 dòng chữ 48px, tên kênh, nhãn chất lượng và Đã tải; thanh tiến trình; hàng Lùi / Phát / Tiến cao 160px; hàng Tua lùi 30s / Tua tới 60s cao 160px.
3. Khung danh sách 1000px: 5 bài mỗi trang, dòng cao 160px, đánh số thứ tự; hai nút “Cuộn trang lên / xuống” cao 140px ở đáy.

Kích thước đầy đủ nằm ở bảng Car Mode trong phần Thông số màn hình.

Quy tắc riêng khi lái:

- Bỏ thanh chữ cái A–Z và menu ⋮ trong danh sách; thay bằng nút lật trang.
- Tìm kiếm mở bàn phím lớn toàn màn, có nút đọc giọng nói.
- Độ tương phản cao hơn: chữ phụ dùng #C4D3DB thay vì #9FB3BF để đọc được dưới nắng.
- Không hiệu ứng chuyển động dài; mọi chuyển cảnh dưới 200ms.
- Nhận nút trên vô lăng (chuyển bài, phát/dừng) qua điều khiển media chuẩn.
- Khi mất mạng, ưu tiên phát bài đã tải và làm mờ các bài còn trên Telegram.
- “Tải trước bài kế” bật sẵn để tránh ngắt nhạc khi sóng yếu.

Màn Nguồn Telegram trong Car Mode: lưới 2 cột thẻ (thay vì 3), chữ tên kênh 22sp, bỏ dòng “Các kênh chưa có nhạc”.

## Lỗi trong mockup cần tránh

Mockup do AI vẽ nên có vài chỗ không khớp; khi code làm theo bản đặc tả này, không chép nguyên ảnh.

| Mockup | Vấn đề | Cách làm đúng |
| --- | --- | --- |
| Thư viện ngang | Danh sách đánh dấu “Sử Thanh Hóa” đang phát, khung trái lại hiện “CHILLOUT #9” | Cả hai lấy từ cùng một nguồn “bài đang phát” |
| Thư viện ngang | Thời lượng bài trong danh sách là thời gian đã phát (00:09) | Danh sách luôn hiện tổng thời lượng |
| Studio Deck | Phụ đề trang trí (“Âm nhạc là nơi cảm xúc…”) | Bỏ, dành chỗ cho nội dung |
| Studio Deck | Nhãn “HI-RES” gắn cho MP3 320kbps | Chỉ gắn Hi-Res khi file là lossless từ 24bit/48kHz trở lên |
| Concept 4 | Chữ phụ và cột kênh quá nhỏ | Theo bảng cỡ chữ ở phần Hệ màu và chữ |
| Concept 2 | Biểu tượng nốt nhạc thay ảnh bìa | Luôn dùng ảnh bìa; không có ảnh thì tạo ô màu kèm chữ cái đầu tên kênh |
| Nhiều concept | Tên file dài kiểu “Zalo 0948… – Vip Chillout…” | Giữ nguyên tên gốc nhưng cắt gọn 2 dòng; sau này có thể thêm tùy chọn làm sạch tên |
| App MeloWave (tham khảo) | Banner quảng cáo lớn chiếm ~1/4 màn chủ | Không có banner; đầu màn chỉ dành cho tìm kiếm, thẻ nguồn và danh sách |
| App MeloWave (tham khảo) | Thẻ “đang phát” có thanh tiến trình nằm ngay trên thanh đáy cũng có nút Phát (hai chỗ điều khiển) | Chỉ một chỗ: Thanh đáy gộp |
| App MeloWave (tham khảo) | “Nghệ sĩ chưa biết” khi thiếu thông tin | Thay bằng tên kênh Telegram hoặc tên thư mục; không hiện chữ “chưa biết” |
| App MeloWave (tham khảo) | Thẻ “Google Drive 0 bài” khi chưa có nhạc | Ẩn nguồn chưa kết nối |
| App MeloWave (tham khảo) | Chip và thẻ bị cắt mép đọc không ra chữ (“Th”, “Lir”) | Mục cuối phải hiện ít nhất nửa chữ, hoặc thêm vệt mờ ở mép để báo còn cuộn được |
| App MeloWave (tham khảo) | Nút Phát nổi tròn đè lên thanh đáy | Không dùng trong Car Mode; Car Mode giữ nút vuông lớn |

## Thứ tự triển khai cho Claude Code

Làm từng bước, mỗi bước cài thử lên máy thật (hoặc giả lập trên Y700) rồi mới sang bước sau. Tuân theo các gate phát hành sẵn có của dự án (E9_RELEASE_GATES.md): không phá Stable, không xoá dữ liệu người dùng, không gỡ app.

- [ ] 1. Màn “Thông tin màn hình” và mục “Giả lập thiết bị” trong Cài đặt; đo trên S25 Ultra và Y700 để chốt ngưỡng; Shanling và FYT dùng số tính sẵn.
- [ ] 2. Hệ màu, cỡ chữ và 4 hồ sơ (Gọn, Điện thoại, Tablet, Car Mode) thành biến dùng chung.
- [ ] 3. Thành phần dùng chung: dòng bài hát + trạng thái, chip, thanh tìm kiếm, **Thanh đáy gộp** (thay mini player + thanh điều hướng dưới), hàng viên thông số chất lượng, thẻ nguồn nhạc.
- [ ] 4. Bản Gọn cho Shanling M3 Ultra (máy dùng chính): Thư viện, Telegram, Đang phát.
- [ ] 5. Car Mode cho FYT (máy dùng chính): công tắc, bố cục theo pixel thật, nút lật trang, nút vô lăng.
- [ ] 6. Màn Đang phát (Studio Deck) đầy đủ, dạng dọc và ngang.
- [ ] 7. Máy phụ: Thư viện dọc cho S25 Ultra, Hai khung cho Y700.
- [ ] 8. Kiểm tra nút vật lý Shanling, màn hình khoá, Bluetooth trên từng máy.

Bước 1–3 nên chạy bằng Opus vì đặt nền móng cho toàn app; từ bước 4 trở đi là lắp màn theo khuôn có sẵn, chuyển sang Sonnet 5.5 để tiết kiệm lượt dùng.
