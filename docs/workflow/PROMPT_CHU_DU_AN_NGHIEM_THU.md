# Bàn giao để chủ dự án nghiệm thu chính thức — 29/10/2026

Chủ dự án nghiệm thu **chính thức, chi tiết**, là người quyết định ĐẠT hoặc yêu cầu sửa. Sau thực thi có **phiên mới kiểm sơ bộ nhanh** bởi Codex hoặc Claude theo [mẫu sơ bộ](PROMPT_NGHIEM_THU_SO_BO.md). Agent chuẩn bị hồ sơ và ghi đúng quyết định, không thay quyền nghiệm thu của chủ dự án. Đọc CLAUDE.md mục 0 trước; không đổi tiêu chí hoặc kết luận lịch sử.

## Agent chuẩn bị bàn giao

1. Xác minh nhánh `dot/<TASK_ID>`, baseline/source commit và cây làm việc theo luật đầu phiên. Chỉ rõ APK đang bàn giao thuộc commit nào; không dùng APK/ảnh cũ làm chứng cứ mã mới.
2. Dẫn kế hoạch, báo cáo thực thi, `*-ban-giao.md` và `*-nghiem-thu-so-bo.md` của đúng source/APK. Sơ bộ đã kiểm code rủi ro/kết quả/bằng chứng trong phạm vi được ghi, không coi là kiểm toàn diện. Test JVM không chứng minh thiết bị/Telegram/UI/âm thanh thật. Nêu tests thành công/bỏ qua/lỗi, bước đã làm/chưa kiểm; chạy lại khi mã thay đổi hoặc bằng chứng thiếu/mâu thuẫn.
3. Bàn giao APK test sau khi xác minh package, versionCode, cert, SHA-256, bytes và cấu hình phù hợp. Bản riêng cần Telegram config=true; open không nhúng config nhưng QA Telegram cần cấu hình runtime/đăng nhập hợp lệ. Không cài sai chữ ký/downgrade, không sửa signer hoặc mang bí mật giữa máy.
4. Tạo/cập nhật `docs/plans/<TASK_ID>-apk-test.md` và `*-hinh-nghiem-thu.md`: file/ảnh thực, nguồn commit, URL được xác minh, giới hạn QA. Upload chỉ Google Drive Ni N owner-only; giữ artifact cũ. Nếu Drive chặn tải, báo BLOCKED phần bàn giao, giữ vị trí nội bộ và không né cảnh báo hoặc coi URL là tải được.
5. Gửi APK, ảnh tổng hợp và 3–6 bước thử có kỳ vọng rõ ràng để chủ dự án tự dùng thử hoặc duyệt từ xa. Không bắt buộc đi tới thiết bị. Các thao tác agent làm vẫn tuân quy tắc dữ liệu, ADB và vòng đời màn hình.
6. Hồ sơ `docs/plans/<TASK_ID>-nghiem-thu.md` ghi kết quả kỹ thuật và **CHỜ CHỦ DỰ ÁN NGHIỆM THU**; dùng BLOCKED khi thiếu điều kiện kỹ thuật. Bản chưa duyệt là **QA CANDIDATE — CHƯA NGHIỆM THU**. Không ghi PASS chỉ vì build/test xanh.

## Ghi nhận quyết định của chủ dự án

- `ĐẠT <TASK_ID> — <commit/APK>`: agent ghi PASS theo phạm vi chủ dự án xác nhận, ngày, nguồn xác nhận và các giới hạn đã nêu. Có thể hiểu lời "ĐẠT" ngắn khi ngữ cảnh chỉ có một bản rõ ràng; nếu còn mơ hồ về bản/phạm vi thì làm rõ trước khi ghi PASS.
- `Yêu cầu sửa <TASK_ID>: <lỗi>`: agent ghi REWORK, mô tả lỗi và bước tái hiện; thực thi theo phạm vi được giao, không tự mở nhiệm vụ khác.
- Yêu cầu build/cài/tiếp tục/đổi quy tắc không phải ĐẠT. Các giới hạn chưa được chủ dự án chấp nhận không được tự coi là đã nghiệm thu.
- ĐẠT không tự cấp quyền merge/push/phát hành công khai. Merge cần chỉ thị riêng sau nghiệm thu; push/public cũng cần chỉ thị riêng.

Commit hồ sơ và cập nhật bàn giao trên nhánh nhiệm vụ. Lời kết: Đã làm / Chưa kiểm chứng / Rủi ro; khi đang chờ, ghi **"Đã dừng, chờ chủ dự án nghiệm thu."** Sơ bộ do Codex hoặc Claude được giao, không bắt buộc Opus và không ghi PASS chính thức thay chủ dự án.
