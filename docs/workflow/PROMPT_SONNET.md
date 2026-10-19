# PROMPT MẪU — CLAUDE SONNET THỰC THI

Dành cho phiên Claude Code MỚI, chọn **Sonnet khả dụng**, chế độ thực thi có kiểm soát. Không dùng Resume của Opus.

---

MELOVAULT — PHIÊN SONNET THỰC THI ĐÚNG KẾ HOẠCH

Repo chuẩn: D:\Bots\MeloVault-E91-Shareable
TASK_ID: <TASK_ID>
Nhánh: dot/<TASK_ID>
Kế hoạch Opus đã được chủ dự án CHỐT: docs/plans/<TASK_ID>.md
[Chỉ nếu được ChatGPT chỉ định là LÀN NHANH: không có phiên Opus; tự viết kế hoạch rất ngắn vào cùng file.]

1. Đọc CLAUDE.md, HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md và kế hoạch TASK_ID. Kiểm tra git status, branch, HEAD, baseline/plan commit, diff, agent đang chạy. Nếu tree dirty không rõ nguồn hoặc baseline không khớp, STOP ngay; không reset/clean/stash/đè.
2. Sửa đúng các file và bước trong kế hoạch, không thay kiến trúc hoặc mở rộng scope, không tự làm task khác. Tạo regression test tương ứng; chạy :app:testShareableDebugUnitTest + :app:testShareableReleaseUnitTest và assembleShareableDebug/Release khi phạm vi cho phép; ghi test defined/pass/skipped/fail thực, diff --check.
3. Tầng QA chính: trên Ni chạy adb devices -l, xác nhận **Shanling M3 Ultra** qua ro.product.model/manufacturer và SERIAL authorized. Kiểm package/version/signature/Telegram auth config trước khi cài; nếu hợp lệ, dùng DUY NHẤT adb -s SERIAL install -r APK_DEBUG_VALIDATED. CẤM adb uninstall, pm clear, logout/xóa data/cache/TDLib. Nếu adb không thấy Shanling, signature mismatch, version downgrade hoặc APK thiếu Telegram config → DỪNG phần cài, đánh dấu BLOCKED để ChatGPT/người dùng xử lý; không chuyển sang thiết bị khác rồi coi là QA Shanling.
4. Khi cài được, Sonnet PHẢI tự chạy các bước QA UI an toàn trong kế hoạch qua ADB trên Shanling/Ni (Sony/Tiny khi phù hợp), tự chụp ảnh trước/sau mỗi bước quan trọng bằng adb -s SERIAL exec-out screencap -p, kiểm ảnh đọc được; dùng adb screenrecord nếu cần thể hiện diễn biến. Ghi model/serial/thời gian, trạng thái Media3/queue và logcat. Không coi Media3 PLAYING là bằng chứng nghe được âm thanh thật. Không tự bật airplane/tắt mạng nếu plan cấm. Giữ ảnh private tại docs/plans/artifacts/<TASK_ID>/ để ChatGPT xem và gửi owner duyệt từ xa, không commit ảnh có dữ liệu cá nhân.
5. Lưu docs/plans/<TASK_ID>-bao-cao.md cùng cấp với kế hoạch. Gồm: branch/commits/base, file đổi, chức năng xong/chưa xong, bảng test và lệnh đã chạy, package/cert/install -r hợp lệ hay BLOCKED, bảng 3–6 bước Agent đã tự làm, ảnh/video trước-sau, kết quả PASS/FAIL/BLOCKED và log Media3/ANR, rủi ro/chỗ chưa kiểm chứng, đường dẫn artifacts để ChatGPT gửi owner duyệt từ xa. Không xóa hoặc ghi đè artifact cũ.
6. Commit code và báo cáo trên cùng nhánh dot/<TASK_ID> khi Git an toàn; không sửa CLAUDE.md, docs/workflow, file nghiệm thu ChatGPT hoặc nhánh tích hợp. Không merge, không public/upload APK chưa có gate, không tự mở chủ đề tiếp.

Kết thúc tiếng Việt dễ hiểu với ba đoạn: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối:
"Đã dừng, chờ ChatGPT nghiệm thu."

KHÔNG cung cấp prompt phiên mới hoặc tự đề xuất rồi làm việc kế tiếp.

---
Khi ChatGPT báo REWORK, mở một hội thoại Sonnet MỚI, vào đúng dot/<TASK_ID>, đọc docs/plans/<TASK_ID>-nghiem-thu.md, sửa đúng những lỗi nêu trong đó và cập nhật báo cáo cũ có dấu vết từng vòng; không bắt đầu plan mới.

### QUY ĐỊNH BẮT BUỘC VỀ MÀN HÌNH (19/10/2026)

Đọc `docs/workflow/SCREEN_LIFECYCLE_QA.md` trước mọi ADB QA. **Để màn Shanling trên Ni và Sony trên Tiny tắt khi không chụp/thao tác**. Chỉ wake trước màn hình cần test bằng `adb -s SERIAL shell input keyevent 224` sau khi kiểm trạng thái. Sau chuỗi thao tác, trong khối `finally`, gửi `adb -s SERIAL shell input keyevent 223`; kiểm `dumpsys display` xác nhận Display State=OFF. Không dùng KEYCODE_POWER 26 không kiểm trạng thái, không tắt màn khi người/agent khác đang dùng, không giữ sáng lúc build/test Gradle. Báo cáo `SHANLING_SCREEN_END`, `SONY_SCREEN_END` và blocker nếu có; **không thay timeout/brightness/AOD hoặc xóa dữ liệu**.
