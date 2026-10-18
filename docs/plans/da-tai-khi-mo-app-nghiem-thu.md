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

## Cập nhật ADB — 18/10/2026 23:30 (+07)
- Sau khi chủ dự án bật USB debugging, ChatGPT độc lập xác nhận trên Ni: adb devices -l thấy serial 30313860180939 trạng thái device, manufacturer Shanling, model Shanling M3 Ultra, Android 10; package cài hiện tại app.melovault.player vc107/0.6.86-beta.
- Chứng chỉ APK đang cài có SHA-256 eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8, khớp chứng chỉ APK candidate Beta109 từ Tiny: C:\Bots\_qa_temp\Beta109_9a1f129\MeloVault-Owner-Beta-v0.6.88-b109-0cf7285.apk, SHA-256 file 7989C5F9C298C41FC4F3F9C6D2F145A4B3663B7A35B5418DD4E83EFFC2465C3B. Candidate là vc109/0.6.88-beta; Telegram config Tiny hiện có ID khác zero và hash không rỗng, nhưng CHƯA chứng minh API thực sự đăng nhập/phát được.
- Blocker ADB đã hết. Vẫn BLOCKED — CHỜ CÀI THỬ AN TOÀN, ẢNH SHANLING VÀ BÁC SĨ NGHIỆM THU. CHƯA chạy adb install -r, CHƯA có ảnh Shanling Beta109 và không sửa dữ liệu.
- Sony qua Tiny đã cài vc109 nhưng không thay thế Shanling. Sonnet tiếp tục QA trên chính TASK_ID: lệnh duy nhất adb install -r sau khi xác minh an toàn; ảnh và báo cáo tại docs/plans; ChatGPT nghiệm thu và chủ dự án xác nhận thao tác trước PASS.
## Nghiệm thu độc lập sau phiên Sonnet QA Shanling — 19/10/2026 00:05 (+07)

**PHÁN QUYẾT CẬP NHẬT: REWORK — chức năng lưu và quét nền đạt, nhưng mục tiêu hiển thị số “đã tải” ngay khi mở app CHƯA đạt trên Shanling M3 Ultra (bố cục Gọn). Chủ dự án phải phê duyệt cách trình bày UI trước khi sửa. KHÔNG merge tính năng với kết luận PASS.**

### Bằng chứng xác minh độc lập
- Trên Ni, repository HEAD khi đánh giá là a06c4b5f2356a05fc89bd2b53d50cf46a7941190, branch feature/e93-03d-y700-offline-cockpit, working tree sạch. Commit code hiệu lực là 0cf7285, các commit đến a06c4b5 sau đó chỉ đổi tài liệu.
- Đọc docs/plans/da-tai-khi-mo-app-bao-cao.md 71 dòng cùng danh sách 14 ảnh PNG thực trong docs/plans/artifacts/da-tai-khi-mo-app/.
- ChatGPT trực tiếp xem ảnh 02-the-telegram-ngay-khi-mo.png và 03-the-telegram-sau-quet.png: cả hai đều chỉ thấy “Telegram · 29 nguồn” và “Tất cả nguồn · 29.607 bài”; **không có dòng “đã tải 3”**.
- ChatGPT trực tiếp xem 07-thu-vien-menu.png và 09-da-tai-sau-quet.png: khi vào bộ lọc “Đã tải”, UI hiển thị đúng “Đã tải · 3 bài”; dòng phụ “3 nhóm / 7 bản offline đã xác minh · 29.607 mục đã kiểm tra · 15 mục chưa xác minh được”.
- Ảnh 14-dang-phat-offline.png thể hiện hàng bài đang phát, nút pause và trạng thái phát; Sonnet báo logcat Media3 tiến 5,3→11,4→20,3 giây. Việc không có FATAL/ANR lấy từ báo cáo Sonnet, chưa độc lập kiểm tra mọi log.
- ADB trực tiếp trên Ni: device 30313860180939, getprop Shanling M3 Ultra/Android 10; dumpsys app.melovault.player cho vc109, 0.6.88-beta. APK ứng viên đúng package/version/cert và đã được Sonnet cài giữ dữ liệu; trước đó ChatGPT độc lập so chữ ký cũ và ứng viên vc109.
- ChatGPT trực tiếp gọi Gradle trên Ni lúc 00:02:58–00:04:47 (+07), các task test Debug/Release và assembleShareableDebug/Release kết thúc BUILD SUCCESSFUL (109 task, 11 executed, 98 up-to-date). Đọc XML: mỗi variant **117 suites, 1249 defined, 1228 pass, 21 skipped, 0 failures/errors**. Trước đó ChatGPT đã chạy lại 2 test variant với --rerun-tasks ngày 18/10/2026 khoảng 23:18, cũng PASS. Có cảnh báo SDK XML/deprecated Gradle, không chặn build.
- Đọc code TelegramPreviewActivity.kt: downloadedLibraryTotal được khôi phục từ prefs và sourceSummaries() trả về số này (lines ~3664–3668 và 4487–4488). Nhưng newLayer() có thể null ở bố cục Gọn (lines 4040–4046); UI này không hiển thị sourceSummaries. Do đó logic dữ liệu lành nhưng UI thiếu nơi hiển thị số, phù hợp bằng chứng hình ảnh.

### Cần sửa và điểm chặn
- REWORK chỉ UI bố cục Gọn cho Shanling: đề xuất 1 dòng phụ nhỏ “Đã tải: 3 nhóm” dưới tiêu đề “Tất cả · 29.607 bài” trong Thư viện Gọn, lấy từ downloadedLibraryTotal, ẩn nếu null; hiển thị số lưu ngay khi có, tự cập nhật sau quét, không tự gắn “đã xác minh hiện tại” cho số cache cũ. Không làm màn hẹp bị che list/controls/taskbar.
- Ưu tiên làm trên chính nhánh đang thực hiện E9.3-03D (ngoại lệ do nhiệm vụ bắt đầu trước luật dot/<TASK_ID>); nếu chuyển sang nhánh dot cần chọn migration an toàn và không reset lịch sử. Sonnet phiên mới, không sửa logic scan, TDLib, cache, SQLite, playback hoặc signing.
- Các nguy cơ cần test sau sửa: compact widths 360dp, các mức fontScale, offline count null/stale/zero, rotate/split screen; regression của Sony/Y700 source card và 20s/6h scheduling.
- Đợi chủ dự án **chốt vị trí hiển thị** rồi mới đưa prompt Sonnet REWORK. Chưa có owner PASS trên 3–6 thao tác, do đó tuyệt đối không tuyên bố Beta109 feature đã nghiệm thu đủ. Bản Beta109 Shanling đã cài; không gỡ app/xóa dữ liệu hoặc cài lại chỉ để kiểm số.