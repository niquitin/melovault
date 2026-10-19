# MeloVault — Vòng đời màn hình thiết bị QA

**Quyết định chủ dự án 19/10/2026.** Sonnet và ChatGPT chỉ làm màn hình **Shanling M3 Ultra** (Ni PC) và **Sony Xperia 5 II** (Tiny PC) sáng trong khoảng thời gian thực sự thao tác UI, chụp ảnh hoặc quay video. Ngoài khoảng này, để màn hình tắt nhằm bảo vệ màn hình/tiết kiệm pin. Đây là **tắt màn hình, không phải tắt nguồn**.

## Thiết bị và kiểm tra an toàn

- Ni → Shanling M3 Ultra, ADB serial xác minh `30313860180939` (Android 10).
- Tiny → Sony Xperia 5 II XQ-AS52, serial `QV725WEQ54` (Android 12).
- Trước mỗi phiên: `adb devices -l`, `adb -s SERIAL shell getprop ro.product.model`, kiểm tra người hay agent khác có đang dùng thiết bị hay không. Không thay thế kết quả thử Shanling bằng Sony hoặc ngược lại.
- Không điều khiển màn hình khi người dùng hoặc agent khác đang QA. Nếu ADB không được cấp quyền, thiết bị khóa hoặc phải nhập PIN, dừng và yêu cầu hỗ trợ; tuyệt đối không vượt khóa bảo mật.

## Lệnh ADB và xác minh

1. **Kiểm trước:** `adb -s SERIAL shell dumpsys power` đọc `mWakefulness`; `adb -s SERIAL shell dumpsys display` đọc `Display State=ON/OFF`. Với Sony, `mWakefulness=Dozing` vẫn có thể là màn tắt nếu **Display State=OFF**.
2. **Chỉ bật màn khi chuẩn bị chạm/chụp:** `adb -s SERIAL shell input keyevent 224` (**KEYCODE_WAKEUP**). Chỉ mở khóa nếu đó là thao tác thử đã được chủ dự án cho phép; không nhập mật khẩu/PIN thay người dùng.
3. **Làm thao tác thật, chụp PNG trước/sau**, quay video khi cần và được phép. Không giữ màn sáng trong các bước Gradle/unit test, kiểm Git/log/Media3 hay chờ phê duyệt từ xa.
4. **Ngay sau nhóm thao tác / khi kết thúc / khi gặp lỗi**, trong phần cleanup `finally` (kể cả BLOCKED/FAIL), dùng `adb -s SERIAL shell input keyevent 223` (**KEYCODE_SLEEP**). Không dùng `KEYCODE_POWER=26` tùy tiện vì là toggle có thể vô tình làm sáng màn đang tắt.
5. Sau 0,5–2 giây xác minh **Display State=OFF** bằng `dumpsys display`, và ghi trạng thái cuối. Nếu vẫn ON hoặc không đọc được, báo blocker; không tự sửa brightness/time-out/AOD/system settings.

## Phạm vi và bảo toàn dữ liệu

- Để màn hình tắt khi test phát nhạc nền nếu kịch bản không đòi UI; **không** hiểu lệnh tắt màn như dừng nhạc, tắt nguồn hoặc xóa dữ liệu.
- Không dùng `adb uninstall`, `pm clear`, logout, xóa cache/TDLib, đổi account, thay chế độ máy bay/network hay cài FYT để thực hiện chính sách màn hình.
- Nếu test mở ứng dụng làm bật màn tự động, kết thúc phải kiểm và đưa về OFF, không bỏ mặc thiết bị sáng.
- Sonnet ghi `SHANLING_SCREEN_END=OFF/ON (lý do)/UNKNOWN` và `SONY_SCREEN_END=OFF/ON (lý do)/UNKNOWN` cùng thời điểm xác nhận trong `docs/plans/<TASK_ID>-bao-cao.md`. ChatGPT khi QA lại cũng tuân thủ.
- Chủ dự án nghiệm thu bằng ảnh/video **sau khi màn đã tắt**, không yêu cầu thiết bị sáng liên tục trong lúc chờ chủ dự án trả lời.

Quy tắc này thuộc tài liệu quy trình; **chỉ ChatGPT được sửa**. Không thay đổi mã nguồn ứng dụng chỉ để thực hiện chính sách này.
