# Nghiệm thu ChatGPT — da-tai-khi-mo-app (Beta109, 18/10/2026)

**Trạng thái: BLOCKED — CHỜ THIẾT BỊ SHANLING VÀ CHỦ DỰ ÁN XÁC NHẬN.** Không được gọi PASS/merge chỉ vì Gradle xanh. Đây là nhiệm vụ đang triển khai TRƯỚC khi bắt đầu quy tắc nhánh dot/<TASK_ID>; không tự đổi lại Git history.

## Đối chiếu kỹ thuật độc lập
- Plan canonical docs/plans/da-tai-khi-mo-app.md, lập từ cefafcd, với mục tiêu hiện số nhóm đã tải khi mở app.
- Commit code trên Ni: 7b99528, bump Beta109 9a1f129, sửa thời điểm khởi chạy d5409eb; HEAD d5409eb4c9b1e1687a673b981d108c8de0a9a2e2, branch cũ feature/e93-03d-y700-offline-cockpit, tree sạch.
- File code thay đổi so với ba9fdf7: app/build.gradle, TelegramPreviewActivity.kt, MeloDownloadedCountPolicy.kt, MeloDownloadedCountPolicyTest.kt. Có chính sách nền 20 giây/tối đa 6 giờ, lưu số theo account, background scan có điều kiện, chưa chứng minh đo pin/Shanling ngoài thiết bị.
- ChatGPT **tự chạy** trên Ni lệnh Gradle Debug+Release tests+assembleShareableDebug+assembleShareableRelease (BUILD SUCCESSFUL 18/10/2026 23:12 +07); sau đó **chạy lại test buộc thực thi --rerun-tasks** (BUILD SUCCESSFUL 18/10/2026 23:18 +07, 59 tasks executed). XML mỗi variant 117 suites, 1249 defined, **1228 executed/passed, 21 skipped, 0 failures, 0 errors**. Kotlin/Android compilation và build đã đạt, có warning deprecated SDK/Gradle đã tồn tại.
- Windows Ni nhận USB Shanling M3 Ultra qua PnP (USB\VID_18D1&PID_4EE1\30313860180939) nhưng **adb devices -l không phát hiện thiết bị**; đó chỉ là Windows USB device instance ID, không phải serial ADB. Do đó CHƯA có APK debug in-place, chưa thể tự kiểm tra/chụp Shanling và không có file ảnh Shanling nào có thể nghiệm thu.
- Sony qua Tiny được ADB xác minh serial QV725WEQ54, Android 12 XQ-AS52. Sony đang cài versionCode109 / 0.6.88-beta (dumpsys), có thư mục QA Beta109_9a1f129; **[CẦN XÁC NHẬN]** hiện chưa có bằng chứng QA đầy đủ đối với hotfix commit d5409eb. Sony != Shanling.
- Tại thời điểm kiểm toán chưa có canonical docs/plans/da-tai-khi-mo-app-bao-cao.md do Sonnet lập theo quy trình mới; tài liệu này ghi nhận khoảng trống, không tự viết thay báo cáo Sonnet.

## Bác sĩ tự kiểm tra — chỉ sau khi có APK đúng và cài an toàn
1. **Shanling M3 Ultra:** mở MeloVault → Thư viện: số "đã tải" phải hiện ngay hoặc có trạng thái chờ rõ ràng mà không bịa số.
2. Đóng app rồi mở lại khi dữ liệu đã được lưu: xem thẻ Telegram có số đã tải ngay, không cần mở chip "Đã tải" trước.
3. Bấm "Đã tải": số nhóm trong chip/danh sách sau quét phải khớp thẻ Telegram; kiểm một bài thực sự có thể phát offline.
4. Trong lúc đang tải/đang đồng bộ, thử bấm phát một bài và cuộn danh sách: app không giật rõ, không ngắt nhạc.
5. Trở lại Thư viện và kiểm tra khi không có mạng/ở chế độ tiết kiệm pin: không tuyên bố số cũ là số mới đã xác minh.
**Chỉ ChatGPT đánh dấu PASS sau khi chủ dự án báo kết quả các bước, cộng với bằng chứng test và ảnh.**

## Điều kiện để bỏ BLOCKED
- Kết nối Shanling USB debugging để adb devices -l trên Ni hiển thị authorized serial + getprop đúng model; không đoán serial từ PnP.
- Xác nhận APK dùng để test có package/version/signature khớp và Telegram API config hợp lệ. Nếu mismatch, dừng, KHÔNG uninstall/pm clear.
- Sonnet tạo docs/plans/da-tai-khi-mo-app-bao-cao.md ghi commit/test/ảnh hoặc nêu lý do chưa test. ChatGPT kiểm tra screenshot, chạy lại test/build nếu có commit mới và hỏi bác sĩ 3–6 thao tác.
- Chưa được hợp nhất/chốt PASS tính năng Beta109 hay khởi động phiên Opus tiếp theo trước khi chủ dự án chốt.
