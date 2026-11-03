# Kiểm tra sơ bộ — Widget Đang phát

**SƠ BỘ: CẦN SỬA** · 03/11/2026 · Codex (GPT-6), phiên sơ bộ độc lập. Chặng B vẫn **BLOCKED** ở QA có bài và tải APK Drive.

Nhánh `dot/2026-11-03-widget-dang-phat`. Đã kiểm Git đầu phiên trong worktree Ni ghi ở [bàn giao](2026-11-03-widget-dang-phat-ban-giao.md). Cây sạch, không có index.lock, không thấy job/agent khác gắn với nhiệm vụ. Baseline `b90834f44457b60a9732e59e59c1bc9c971368c3` → kế hoạch `1921706` → source APK `49ed94ccbd73abb2d9443828ede26c205e1e9e56` → hồ sơ thực thi `8d78b193a49b2c8c4b7dec196492b42bd451d3d7`. Đã đối chiếu diff baseline–source, diff-check và danh sách file sau source (chỉ Markdown), theo [kế hoạch](2026-11-03-widget-dang-phat.md) và [báo cáo](2026-11-03-widget-dang-phat-bao-cao.md).

## Hai đường mã cần sửa

1. **Nút Tiếp có thể gửi lại yêu cầu cho cùng bài trước khi hết 2 giây.** Trong `MeloWidgetSession.kt:25,99–110,125–128`, khóa thế hệ gồm cả metadata. Khi tiêu đề hoặc bìa của A cập nhật trong lúc đang chờ B, widget vẽ lại với token mới. `MeloWidgetPolicy.kt:84–103` đồng thời xóa mốc thời gian của lệnh Tiếp. Nếu bấm lại trên widget vừa cập nhật, `ShareablePlaybackService.kt:227–247,595–617` có thể xóa trạng thái chờ rồi gửi lại yêu cầu resolve B. Mong đợi một yêu cầu trong cửa sổ 2 giây cho cùng lượt chuyển A→B.

   Kiểm hẹp bằng JShell trên lớp Debug đã biên dịch. Dùng `Generation.update` với khóa mô phỏng (A, title1, owner), bấm ở 0 và 20 ms được true/false. Chỉ đổi title1 thành title2 rồi thử ở 100 ms, token cũ bị từ chối nhưng token mới được chấp nhận. Phép thử xác nhận việc mở khóa sớm ở helper. Chưa quan sát số yêu cầu TDLib hoặc hiện tượng nhảy bài trên launcher. Khi sửa, tách việc làm mới ảnh/metadata khỏi thời hạn chặn Tiếp và giữ kiểm tra token cũ.
2. **Widget có thể báo trống khi bài Telegram đã bắt đầu phát từ yêu cầu chờ kết nối.** Điều kiện tái hiện theo mã là chọn bài Telegram đã tải khi service chưa được tạo. `TelegramPreviewActivity.kt:1521–1525,1575–1588,4214–4224` cấu hình queue rồi giữ các MediaItem để phát sau. `ShareablePlaybackService.kt:69–73` bỏ qua cấu hình nếu chưa có serviceRef. Khi controller nối xong, `TelegramPreviewActivity.kt:1476–1491,4237–4241` chỉ đọc queue hiện có và phát các item đang chờ. Service mới thiếu owner/queue nên `widgetItemAuthorized` (service, dòng 147–156) từ chối item dù player đang phát. Mong đợi widget nhận đúng bài của owner. Đây là đường lỗi tĩnh có điều kiện, chưa tái hiện trên AVD. Khi sửa, chuyển giao và kiểm lại owner/queue trước khi phát yêu cầu chờ. Giữ nguyên kiểm tra quyền của widget.

## Bằng chứng đã đối chiếu

| Phạm vi | Kết quả và giới hạn |
|---|---|
| Ca sĩ | Factory Telegram cho bài cache, progressive, bài kế và QueueTrack truyền performer riêng. Widget đọc extra này. Artist tên nguồn vẫn dành cho giao diện cũ. Local dùng tag trước fallback thư mục, lọc sentinel, giữ tag thật kể cả trùng tên thư mục. Item thiếu extra hiện “Chưa rõ ca sĩ”. Đã đọc test policy. |
| Lệnh | Receiver kiểm widget ID, action và token trong PendingIntent explicit/immutable. Dispatch đọc lại owner, trạng thái và commands của NextAwarePlayer. PAUSE chỉ gọi pause nếu còn ý định phát. Next dùng cả hasNextMediaItem và command của wrapper, nghe cả refreshCommands. Phần chặn lặp có lỗi 1. |
| Ảnh và teardown | Một worker đọc ảnh nhúng/URI hợp lệ, kiểm file Telegram dưới root owner và kiểm epoch/token khi trả ảnh, thu cạnh dài tối đa 144 px. Khi mất widget cuối, hủy việc ảnh. Khi hủy service, vô hiệu token, bỏ listener và vẽ trống trước release. Kết quả ở đây là đọc mã. |
| Flavor và gate Ni | Merged manifest Debug/Release Shareable và .open có đúng một provider exported=false, Personal không có. Manifest nguồn không thêm quyền, widget không poll. Đếm lại XML mỗi Debug/Release được 202 suite, 2.737 test, 2.716 đạt, 21 skipped, 0 failure/error. Log a7-tests-r2, a7-assemble, a7-open và a7-personal đều BUILD SUCCESSFUL. Source không đổi nên dùng lại gate. Chỉ chạy phép thử helper nêu trên. |
| Candidate | Đã đo lại bản Ni 33.839.028 byte, SHA-256 `3C37DBE24337443E417F0B53CC27198FE3AE8D28048C84615586BB3B16221F75`, khớp metadata Tiny cùng source. Theo gate đã lưu, Debug app.melovault.player vc121/0.6.96-beta, config=true, cert SHA-256 `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`, hash bản từng cài trên AVD khớp. [APK nội bộ và hồ sơ](2026-11-03-widget-dang-phat-apk-test.md) |
| Launcher và Drive | Đã xem ảnh tổng hợp trên Ni, so hash 7 PNG với index đã lưu và kiểm dump hai ID 2/3. Q1 có picker, preview, widget trống và mở màn đăng nhập. Q5 có resize 4→3 cột và hai instance trống. Theo metadata đã lưu, ảnh owner-only. APK owner-only bị HTTP 403 cannotDownloadAbusiveFile, chưa giao được APK tải qua Drive và chưa có remote SHA. [Ảnh tổng hợp và sáu ảnh gốc](2026-11-03-widget-dang-phat-hinh-nghiem-thu.md) |

## Phần chờ và cách đối chiếu khi có môi trường QA

AVD nhiệm vụ đã xóa. Theo hồ sơ thực thi, app chặn cả mở tệp trước đăng nhập. Q2–Q4 có bài, Q5 đồng bộ khi đổi bài và Q6 xóa/thêm lại còn thiếu. Chưa xác nhận âm thanh thực, độ trễ, chữ lớn hoặc API 30/31. SONY_SCREEN_END = KHÔNG THAO TÁC (Sony giữ EQ). Shanling đã bán.

Khi chủ dự án giao môi trường QA đăng nhập riêng và phiên thực thi:
1. Mở app từ nguội, chọn nhanh bài Telegram đã tải. Đối chiếu tên, ca sĩ và bìa trên Home với bài thực sự đang phát.
2. Tạm dừng rồi phát tiếp. Kiểm vị trí, nút và thao tác mở đúng màn Đang phát.
3. Với B chưa tải, bấm Tiếp trên A, cập nhật metadata của A rồi bấm lại trong 2 giây. Kiểm chỉ có một yêu cầu B. Đối chiếu bài trước/tiếp và việc xóa bìa cũ ở hai widget.
4. Xóa widget cuối khi đang phát rồi thêm lại. Đối chiếu phiên nhạc và bài đang hiển thị.

Chờ chủ dự án quyết định việc giao sửa trên cùng nhánh. Chặng B cần môi trường QA được cấp và việc bàn giao APK qua Drive vẫn bị chặn riêng. Đã dừng, chờ chủ dự án nghiệm thu.
