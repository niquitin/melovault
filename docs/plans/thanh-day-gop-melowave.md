# Việc: Thanh đáy gộp + hàng viên chất lượng + thẻ nguồn (học từ MeloWave)

Ngày giao: 18/10/2026. Nguồn: chủ dự án gửi ảnh app MeloWave; Claude (claude.ai) đã cập nhật `docs/ui-spec.md` (mục Thành phần dùng chung: "Thanh đáy gộp", "Hàng viên thông số chất lượng", "Thẻ nguồn nhạc"; bảng Shanling; bảng "Lỗi trong mockup cần tránh" có 6 dòng MeloWave). Thay đổi đặc tả này đang là thay đổi chưa commit trong `docs/ui-spec.md`: commit nó trước, riêng một commit `docs:`.

Làm theo thứ tự, mỗi phần một commit, test + gate như mọi bản Beta. Không đụng TDLib, reducer, DB, cache, đăng nhập.

## Phần A — Thanh đáy gộp (ưu tiên, máy chính Shanling)

- Chỉ bố cục dọc: Gọn (`MeloCompactScreen`), Điện thoại/Tablet dọc (`MeloLibraryPane` khi dọc). Ngang và Car Mode (`MeloCarScreen`) không đổi.
- Thay mini player + thanh điều hướng dưới bằng một khối: thanh tiến trình 2dp ở mép trên, một dòng "tên bài · tên kênh" (bấm/vuốt lên = Đang phát), hàng nút: tab · Lùi · Phát/Dừng tròn ở giữa · Tiến · tab.
- Kích thước theo bảng trong đặc tả: Gọn 72dp (2 tab Thư viện · Telegram, mục còn lại vào ☰), Điện thoại 88dp và Tablet dọc 96dp (4 tab Thư viện · Telegram · Playlist · Đã tải).
- Chỉ một chỗ điều khiển phát trên màn. Chưa có bài: ẩn dòng tên + tiến trình, nút Phát mờ. Chưa đăng nhập: ẩn cả thanh (giữ quy tắc `TelegramLoginStepPolicy`).
- Trạng thái Phát/Dừng và dòng trạng thái bài trong danh sách lấy từ cùng một nguồn (liên quan BACKLOG mục 7: khi tạm dừng không ghi "Đang phát").
- Kiểm: Shanling vẫn thấy đủ 8 dòng bài (đối chiếu lại chiều cao cửa sổ 574dp hay 604dp và ghi số đúng vào đặc tả), mọi vùng bấm ≥ 48dp, nút vật lý Shanling vẫn điều khiển được.

## Phần B — Hàng viên thông số chất lượng

- Trong `MeloStudioDeck` (và bản Gọn của Đang phát): hàng viên dưới tên bài: Định dạng · bit/kHz · kbps · dung lượng. Thiếu số nào thì bỏ viên đó. Viên Hi-Res chỉ khi lossless ≥ 24bit/48kHz. Car Mode giữ một dòng chữ.
- Có test JVM cho phần quyết định viên nào hiện và nhãn Hi-Res (logic thuần).

## Phần C — Thẻ nguồn nhạc (máy phụ, làm sau cùng)

- Hàng thẻ đầu Thư viện cho Điện thoại/Tablet: Telegram (số bài · đã tải) · Nhạc trên máy (nếu có). Nguồn chưa kết nối thì ẩn. Không làm trên Shanling. Google Drive để sau (workstream riêng).

## Ngoài phạm vi

Đổi màu (giữ teal), Car Mode, Drive/OneDrive, Theme 04.

## Báo cáo cuối phiên

Như mọi phiên: kết quả test, APK/SHA256/signer, Drive, việc chủ dự án cần thử trên Shanling (ưu tiên) và Y700, đề xuất bước tiếp bằng lời thường.
