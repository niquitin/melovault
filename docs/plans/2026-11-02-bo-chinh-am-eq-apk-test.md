# APK test — 2026-11-02-bo-chinh-am-eq

**QA CANDIDATE — CHƯA NGHIỆM THU.** Không phải bản phát hành; chưa merge/push. Bản Debug QA để chủ dự án thử trên Sony; chưa có chấp thuận ĐẠT.

- Thư mục Drive (owner-only, `ntdung.md@gmail.com`): [MeloVault-EQ-QA-CANDIDATE-20261102-vc121-e2faf85](https://drive.google.com/drive/folders/1DpJDqqFrHrYGGosqOLkzuw1rfagTQDVe)
- APK Debug: [MeloVault-0.6.96-beta-vc121-bo-chinh-am-eq-e2faf85-QA-CANDIDATE-debug.apk](https://drive.google.com/file/d/1Od1p2Qjj8jYtCSawRzCclTd4D2eiwiQc/view?usp=drivesdk)
- Ảnh QA: xem [`-hinh-nghiem-thu.md`](2026-11-02-bo-chinh-am-eq-hinh-nghiem-thu.md)

| Thuộc tính | Giá trị |
|---|---|
| Loại | Debug QA (debuggable), biến thể `shareable` thường (không `.open`) |
| Package | `app.melovault.player` |
| versionCode / versionName | 121 / `0.6.96-beta` (không đổi, không hạ cấp) |
| Source | Nhánh `dot/2026-11-02-bo-chinh-am-eq`; APK build từ commit `e2faf85` (mã EQ `7353587` + sửa hàng mẫu `e2faf85`); checkout detach đúng commit. Baseline `b90834f`, kế hoạch `297d635` |
| Build | Tiny, clone cô lập `C:\Bots\MeloVault-EQ-IsolatedQA-20261102-e2faf85` từ bundle `EQ-e2faf85_20261102.bundle` 13.830.095 byte, SHA-256 `25AA16A30793F322230855F927A9C2DE63B82750B7936F43AD0D79A5C83B4BC7` (khớp Ni và Tiny); `reportTelegramConfig` → `telegram.config.valid=true` (chép `local.properties` ngay trên Tiny, không đọc nội dung); `assembleShareableDebug` BUILD SUCCESSFUL 1m38s |
| Byte | 33.828.974 |
| SHA-256 | `146A42859CFDC3EB204FBE1396BEE1A4EFE40F0C02EC8DADE46415446E1F368D` |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (cùng cert app đang cài trên Sony; cài đè thành công) |
| Telegram config | true (không in giá trị) |
| File thật | Tiny `C:\Bots\MeloVault-EQ-IsolatedQA-20261102-e2faf85\app\build\outputs\apk\shareable\debug\app-shareable-debug.apk`; bản sao Drive mount Ni `Z:\MeloVault_QA\MeloVault-EQ-QA-CANDIDATE-20261102-vc121-e2faf85\` (SHA-256 bản sao khớp) |
| Test JVM (gate Ni, trên `e2faf85`) | Debug/Release mỗi biến thể 203 suite, **2754 test** (+29 so với 2725), 21 skipped, 0 failures, 0 errors; assemble Shareable Debug/Release, `-PmeloOpen=true` Debug, Personal Debug BUILD SUCCESSFUL; `git diff --check` sạch. Không chạy lại test trên Tiny |
| Thiết bị đã test | Sony XQ-AS52 `QV725WEQ54` Android 12, cài bằng đúng một lệnh `adb install -r` (vc121 = vc121, cert khớp, không có phiên phát, màn OFF). QA Q1–Q7 đạt (xem báo cáo mục 6); Q6 xoay/chữ lớn chưa kiểm |
| Kiểm Drive | Thư mục, `hinh`, APK: chỉ quyền owner (`get_file_permissions` = 1 người, `owner`); kích thước APK trên Drive 33.828.974 byte khớp; `hinh` có 11 tệp (ảnh bảng EQ + log; không ảnh thư viện có tên bài, không ảnh bìa người). Tải bằng trình duyệt chưa thử |

APK Release (build trên Ni, không có config Telegram) **không cài, không upload**. Bản Debug trước của chính nhiệm vụ này (`7353587`, SHA `03309F19FFBED7696DFE562DBC17BF26B81A965DC488DAFEF83D894F46E6F36A`) đã cài lên Sony trong phiên QA và bị thay bằng bản này; **không upload** bản đó. Q1–Q3 chụp trên bản đó (giao diện không đổi giữa hai bản, trừ hàng mẫu cuộn).

Rollback: cài lại candidate trước bằng `adb install -r` (cùng vc121/cert), ví dụ Cài đặt `F376BCCBAB9B9FD9381FB7EA08068BDD9203AACB4C00078E282560971F8D77E7` (xem `2026-11-02-thiet-ke-lai-cai-dat-apk-test.md`); không xóa dữ liệu. Hoặc đặt `MeloEqPolicy.FEATURE_ENABLED = false` rồi build lại (pipeline như `b90834f`, chip về Toast cũ). Chỉ thêm 3 khóa prefs `eq_enabled`/`eq_preset`/`eq_custom_db`, vô hại khi bỏ, không migration.

## Chưa xác nhận

**CHƯA XÁC NHẬN ÂM THANH THỰC** (agent không nghe được; bằng chứng là test JVM đáp tuyến và log `MeloEq` RMS vào/ra trên Sony). Chưa kiểm: xoay ngang, chữ lớn 1.3, Teal sáng, FLAC/hi-res, “Nhạc trên máy”, tai nghe/Bluetooth, Y700/S25/FYT, Release/`.open`/Personal trên máy. Một số mẫu bị chặn biên (vài trăm / 10 s ở đoạn to khi nhiều dải dương chồng nhau); nghe thử và báo nếu rè (báo cáo mục 7). Hiệu ứng hệ thống Sony (Dolby/ClearAudio+) nếu đang bật sẽ cộng thêm lên EQ của app.
