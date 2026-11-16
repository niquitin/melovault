# MeloVault — Quy tắc bắt buộc cho Codex / Agent phát hành

Quy tắc áp dụng cho website `https://melovault.pages.dev/` và mọi đợt triển khai, sửa lỗi, bổ sung tính năng của MeloVault. **Không tự ý nới lỏng**.

## 1. Tham gia thử nghiệm và quyền phát APK
- Đường cài ứng dụng công khai duy nhất trên website là: (1) https://groups.google.com/g/melovault-testers và (2) https://play.google.com/apps/testing/app.melovault.player.open.
- Không tạo nút tải APK, không đưa URL tệp APK, GitHub Releases download asset, presigned download URL, token hay hash bí mật vào HTML/JS/JSON, sitemap, changelog, hoặc mã nguồn frontend công khai.
- APK QA, nội bộ và bản thử riêng chỉ được phân phối **sau khi chủ dự án đồng ý rõ ràng cho đúng bản / đúng đối tượng**. Không coi việc biết đường dẫn, nút ẩn, kiểm tra JavaScript, mật khẩu mã hóa trong frontend hoặc robots.txt là cơ chế phân quyền.
- Muốn cấp APK riêng phải lưu ở kho riêng tư với xác thực phía máy chủ hoặc quyền truy cập chủ sở hữu quản lý. Bất kỳ URL APK công khai từng phát hành trên GitHub Releases vẫn có thể tải từ GitHub; việc gỡ nút ở website không thu hồi quyền đó. Cần kiểm toán và di chuyển/thu hồi đúng cách; không âm thầm xóa assets đang được app cũ dùng khi chưa có kế hoạch chuyển tiếp.
- `update.json` công khai **không được chứa đường dẫn APK**. Nếu app còn dùng cơ chế update.json để tải APK, cần chuyển cơ chế cập nhật bản Play sang Google Play trước; không cho agent tự khôi phục `apkUrl` công khai để giải quyết lỗi.
- Tránh triển khai tính năng "download bằng mã truy cập" chỉ ở JavaScript: luôn kiểm tra quyền ở backend, bảo vệ cả API và tệp gốc.

## 2. Nghĩa vụ thông báo website cho MỌI bản cập nhật
- Mỗi phiên bản mới, thay đổi tính năng hoặc bản vá phát hành tới người dùng/tester phải có mục thông báo website **cùng đợt phát hành**, không chỉ cập nhật APK/AAB.
- Trước khi kết luận phát hành xong, cập nhật `changelog.js` (phiên bản mới nhất đứng đầu, versionCode, ngày thực tế, mô tả thân thiện; phân loại new/improved/fixed/test), đồng bộ thông tin `config.js` / `update.json` KHÔNG chứa URL APK; trang chính hiển thị thông báo mới nhất qua `main.js`.
- Chỉ ghi phiên bản đã được phát hành đúng kênh; nếu Play đang chờ duyệt, ghi rõ "đang chờ Google duyệt" hoặc chỉ giữ ghi chú phát triển, không tuyên bố "đã có trên Google Play".
- Kiểm tra quy trình tự động `tools/release/site-update.js` ở repo Android: **cấm tái thêm liên kết APK công khai** vào `config.js` hoặc `update.json`, và bắt buộc cập nhật nhật ký/website theo cùng chu kỳ. Nếu generator chưa sửa, dừng publish cho tới khi có biện pháp an toàn.
- Trước khi merge/publish: chạy `node site-release-policy.cjs`, `node --check main.js`, `git diff --check`; kiểm tra nút Google Groups/Google Play, thông báo phiên bản, bản mobile, không có URL APK công khai. Sau triển khai: kiểm tra URL website live, thông báo và hai nút.
- Website `main` là baseline phát hành; dùng worktree/branch riêng, pull request và nghiệm thu. Không checkout/đè main repo trên Ni PC, không đụng các file đang thay đổi của Android Codex.

## 3. Đảm bảo chất lượng và dọn dẹp
- Test app trên thiết bị thật khi có thể; không coi test JVM là thay thế thử nghiệm Sony/M3/FYT.
- Mỗi bản mới ghi: mã phiên bản, kênh triển khai (Play closed testing hay QA riêng), thời điểm, danh sách thay đổi, các kiểm thử pass/failed, liên kết website thông báo.
- Hoàn tất test phải dừng tác vụ, đóng phiên test, emulator, kết nối không cần thiết; xóa file tạm và dọn tài nguyên ("DÙNG XONG PHẢI DỌN DẸP").
- Không chạm vào dữ liệu cá nhân, quyền Play Console và khóa ký khi không cần thiết.

**Ưu tiên xung đột:** bảo vệ APK riêng và tính đúng đắn của thông báo > tiện tải APK > tốc độ phát hành. Nếu website đang công khai URL APK, coi là lỗi chặn phát hành.
