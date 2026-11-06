# APK test — 2026-11-02-bo-chinh-am-eq

**QA CANDIDATE — CHƯA NGHIỆM THU.** Không phải bản phát hành; chưa merge/push. Bản Debug QA để chủ dự án thử trên Sony; chưa có chấp thuận ĐẠT.

**Candidate hiện hành (từ 03/11/2026): commit `cade4dc` — đã chỉnh preamp theo yêu cầu “Chỉnh preamp”.** Candidate trước (`e2faf85`) vẫn còn trên Drive nhưng **đã được thay thế**; không xóa.

- Thư mục Drive (owner-only, `ntdung.md@gmail.com`): [MeloVault-EQ-QA-CANDIDATE-20261103-vc121-cade4dc](https://drive.google.com/drive/folders/1xUoRJImQ3leE0rZYEj_rGWhCdYifn9w8)
- APK Debug: [MeloVault-0.6.96-beta-vc121-bo-chinh-am-eq-cade4dc-QA-CANDIDATE-debug.apk](https://drive.google.com/file/d/1zVwDHid0eLEhRvrIaSKd8ff2JHZQ7Hy6/view?usp=drivesdk)
- Ảnh QA: xem [`-hinh-nghiem-thu.md`](2026-11-02-bo-chinh-am-eq-hinh-nghiem-thu.md)

| Thuộc tính | Giá trị |
|---|---|
| Loại | Debug QA (debuggable), biến thể `shareable` thường (không `.open`) |
| Package | `app.melovault.player` |
| versionCode / versionName | 121 / `0.6.96-beta` (không đổi, không hạ cấp) |
| Source | Nhánh `dot/2026-11-02-bo-chinh-am-eq`; APK build từ commit `cade4dc` (mã EQ `7353587`, sửa hàng mẫu `e2faf85`, chỉnh preamp `cade4dc`); checkout detach đúng commit. Baseline `b90834f`, kế hoạch `297d635` |
| Build | Tiny, clone cô lập `C:\Bots\MeloVault-EQ-IsolatedQA-20261103-cade4dc` từ bundle `EQ-cade4dc_20261103.bundle` 13.842.423 byte, SHA-256 `CABF62E1C91C05A7DE60216A981D03C841EB6C009035747B0CF333EA938D8540` (khớp Ni và Tiny); `reportTelegramConfig` → `telegram.config.valid=true` (chép `local.properties` ngay trên Tiny, không đọc nội dung); `assembleShareableDebug` BUILD SUCCESSFUL 3m08s |
| Byte | 33.828.974 |
| SHA-256 | `76D1F355095C6E4E98A28F703BDB196BFEE1E562F1780393DF9E419F3B9BDB6C` |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (cùng cert app đang cài trên Sony; cài đè thành công) |
| Telegram config | true (không in giá trị) |
| File thật | Tiny `C:\Bots\MeloVault-EQ-IsolatedQA-20261103-cade4dc\app\build\outputs\apk\shareable\debug\app-shareable-debug.apk`; bản sao Drive mount Ni `Z:\MeloVault_QA\MeloVault-EQ-QA-CANDIDATE-20261103-vc121-cade4dc\` (SHA-256 bản sao khớp) |
| Test JVM (gate Ni, trên `cade4dc`) | Debug/Release mỗi biến thể 203 suite, **2759 test** (+34 so với baseline 2725), 21 skipped, 0 failures, 0 errors; assemble Shareable Debug/Release, `-PmeloOpen=true` Debug, Personal Debug BUILD SUCCESSFUL; `git diff --check` sạch. Không chạy lại test trên Tiny |
| Thiết bị đã test | Sony XQ-AS52 `QV725WEQ54` Android 12, cài bằng đúng một lệnh `adb install -r` (vc121 = vc121, cert khớp, không có phiên phát của chủ dự án, màn OFF). Kiểm lại trên máy: preamp mới và log chặn biên (báo cáo mục 11); QA Q1–Q7 đầy đủ làm trên `e2faf85` (báo cáo mục 6) |
| Kiểm Drive | Thư mục, `hinh`, APK: chỉ quyền owner (`get_file_permissions` = 1 người, `owner`); kích thước APK trên Drive 33.828.974 byte khớp; `hinh` có 7 tệp (ảnh bảng EQ + log; không ảnh thư viện có tên bài, không ảnh bìa người). Tải bằng trình duyệt chưa thử |

APK Release (build trên Ni, không có config Telegram) **không cài, không upload**. Các bản Debug trước của nhiệm vụ này (`7353587` `03309F19…F36A`, `e2faf85` `146A4285…368D`) đã cài lên Sony trong phiên QA và bị thay thế; chỉ `e2faf85` còn trên Drive (thư mục `…-20261102-vc121-e2faf85`), `7353587` không upload.

Rollback: cài lại candidate trước bằng `adb install -r` (cùng vc121/cert), ví dụ `e2faf85` (`146A42859CFDC3EB204FBE1396BEE1A4EFE40F0C02EC8DADE46415446E1F368D`) hoặc Cài đặt `F376BCCBAB9B9FD9381FB7EA08068BDD9203AACB4C00078E282560971F8D77E7`; không xóa dữ liệu. Hoặc đặt `MeloEqPolicy.FEATURE_ENABLED = false` rồi build lại (pipeline như `b90834f`, chip về Toast cũ). Chỉ thêm 3 khóa prefs `eq_enabled`/`eq_preset`/`eq_custom_db`, vô hại khi bỏ, không migration.

## Chưa xác nhận

**CHƯA XÁC NHẬN ÂM THANH THỰC** (agent không nghe được; bằng chứng là test JVM đáp tuyến và log `MeloEq` RMS vào/ra trên Sony). Âm lượng tổng khi bật EQ nay **nhỏ hơn bản trước khoảng 1 dB** (Tăng bass −7,1 dB thay vì −6; Rock −5,8 dB thay vì −5) để không bị cắt biên — chủ dự án nghe và báo nếu muốn đổi (có thể bù lại bằng âm lượng máy). Chưa kiểm: xoay ngang, chữ lớn 1.3, Teal sáng, FLAC/hi-res, “Nhạc trên máy”, tai nghe/Bluetooth, Y700/S25/FYT, Release/`.open`/Personal trên máy. Hiệu ứng hệ thống Sony (Dolby/ClearAudio+) nếu đang bật sẽ cộng thêm lên EQ của app.
