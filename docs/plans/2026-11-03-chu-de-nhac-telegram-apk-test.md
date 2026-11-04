# APK QA nhạc theo chủ đề Telegram

**TASK_ID** `2026-11-03-chu-de-nhac-telegram` · **APK QA ĐÃ ĐƯỢC CHỦ DỰ ÁN NGHIỆM THU ĐẠT, kèm giới hạn**, theo lời “Đạt” trong chat 04/11/2026; [biên bản](2026-11-03-chu-de-nhac-telegram-nghiem-thu.md). **BLOCKED — CHƯA BÀN GIAO APK qua link tải Drive** vẫn giữ nguyên. Gate kỹ thuật chốt 00:01:29 4/11/2026 (Asia/Saigon); không build/cài/QA lại khi ghi nhận quyết định. Chưa phát hành APK công khai.

| Thuộc tính | Bằng chứng |
|---|---|
| Source | `e55ca7e932a249504cce83f2338d45e5dede9079` trên nhánh `dot/2026-11-03-chu-de-nhac-telegram` |
| Build | ShareableDebug QA; Tiny BUILD SUCCESSFUL 1 phút 14 giây, exit0; config Telegram=true (chỉ boolean) |
| Package / phiên bản | `app.melovault.player` · versionCode121 · 0.6.96-beta |
| Byte / SHA-256 | 33878126 B · `71809F4C96788F84A1A00FEA04B7446A8DF01426D6BE08D6D184D234BEC90820` |
| Certificate SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (khớp bản đang cài) |
| Sony | QV725WEQ54 / XQ-AS52 / Android12; install-r Success, APK pull từ máy khớp SHA |
| DB / yêu thích | DB v3, 25.568 bài và 2 mục yêu thích sau cài; v2 trước nâng có25.558 bài/2 mục yêu thích, global sync thêm 10 bài sau đó |
| Ni test cuối | Debug và Release mỗi loại2.741 passed / 21 skipped / 0 failed/error; assembleDebug/Release/AndroidTest thành công |
| Sony native | Feature32,144 giây OK (1 test): queue226, Next trong A, duyệt B giữ queue, count refresh giữbadge, touch-mode sauBack không crash, xoay/recreate đúng |

[APK lưu trên Drive — HIỆN BỊ CHẶN TẢI](https://drive.google.com/file/d/1BZqzp5ggmvGN8fa8iVjBffy_dV4NJQDV/view?usp=drivesdk) · [Thư mục QA riêng tư](https://drive.google.com/drive/folders/1lIIvjxchi691qKSZ0wY47zpJ0Dtc3Kgs) · [Ảnh và chứng cứ](2026-11-03-chu-de-nhac-telegram-hinh-nghiem-thu.md).

Upload đã thành công, metadata đọc lại có size33.878.126 B/shared=false/owner duy nhất Ni N (nigamehome@gmail.com). Kiểm tải raw qua connector trả HTTP403, reason cannotDownloadAbusiveFile, thông báo provider đánh dấu tệp là malware hoặc spam. **Không khẳng định link tải được**, không acknowledgeAbuse/public/đổi đuôi/đóng gói để né cảnh báo. Bản e55ca7e đã cài trên Sony nên vẫn có thể kiểm bản trên thiết bị.

[Bản nội bộ Ni đã đối chiếu SHA](C:/Users/ntdun/.codex/worktrees/melovault-chu-de-telegram/MeloVault-E91-Shareable/docs/plans/artifacts/2026-11-03-chu-de-nhac-telegram/qa-e55ca7e/MeloVault-forum-QA-vc121-e55ca7e.apk)

Bản Tiny: `C:/Bots/_qa_temp/forum-touch-20261103-e55ca7e/MeloVault-forum-QA-vc121-e55ca7e.apk`. Clone build: `C:/Bots/MeloVault-FORUM-TouchQA-20261103-e55ca7e`. Log/exit/gate/installed-match tại cùng thư mục _qa_temp và bản sao private trong qa-e55ca7e trên Ni.

Bundle13918905B, SHA7424F726D4ED0CF5A6489D8B079B93DA3D4867C61DF6B260666DB1A67CEC9A7D khớp hai đầu. Source APK e55ca7e; commit chốt tài liệu sau đó không thay mã APK. Không nhận diện bản chỉ qua versionCode vì các candidate cùng121.

Giới hạn: chưa nghe âm thanh thực; Đã tải trong B=0, chưa thử mất mạng; WAV/FLAC document kiểm bằng fixture, Sony lượt này phátMP3; Gọn/Car compile, chưa QA thiết bị tương ứng/FYT. Không hạ APK/DB để rollback; dùng bản sửa tiến trên DB v3, giữ dữ liệu/tệp/playlist/phiên. Kết quả merge/push sau ĐẠT xem HANDOFF; chưa phát hành APK công khai.

Các APK982eeee,33b16f9,04aa5ba là lịch sử, đã được thay bằng e55ca7e sau sửa UI/metadata/crash; không dùng chúng để nghiệm thu bản cuối. Xem [phụ lục](2026-11-03-chu-de-nhac-telegram-phu-luc-thuc-thi.md). SONY_SCREEN_END=OFF.
