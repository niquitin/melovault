# APK test — 2026-11-02-thiet-ke-lai-cai-dat

**QA CANDIDATE — CHƯA NGHIỆM THU.** Không phải bản phát hành; chưa merge/push. Bản Debug QA để chủ dự án thử trên Sony; chưa có chấp thuận ĐẠT.

- Thư mục Drive (owner-only, `ntdung.md@gmail.com`): [MeloVault-CAIDAT-QA-CANDIDATE-20261102-vc121-d52fe43](https://drive.google.com/drive/folders/1Sm7XOBwnfh6cLwY0o8KcCvtUGt4IemRR)
- APK Debug: [MeloVault-0.6.96-beta-vc121-cai-dat-d52fe43-QA-CANDIDATE-debug.apk](https://drive.google.com/file/d/10YNRVpKLeIU5WVH1B3gyyjKcIWmHVxaL/view?usp=drivesdk)
- Ảnh QA: xem [`-hinh-nghiem-thu.md`](2026-11-02-thiet-ke-lai-cai-dat-hinh-nghiem-thu.md)

| Thuộc tính | Giá trị |
|---|---|
| Loại | Debug QA (debuggable), biến thể `shareable` thường (không `.open`) |
| Package | `app.melovault.player` |
| versionCode / versionName | 121 / `0.6.96-beta` (không đổi, không hạ cấp) |
| Source | Nhánh `dot/2026-11-02-thiet-ke-lai-cai-dat`; APK build từ commit `d52fe43affdb74422224dcc703321214c00b7a77` (checkout detach đúng commit; không có commit hồ sơ nào sau nó vào APK). Baseline `fa4cd09`, kế hoạch `11201fc` |
| Build | Tiny, clone cô lập `C:\Bots\MeloVault-CAIDAT-IsolatedQA-20261102-d52fe43` từ bundle `MeloVault_cai-dat_full_d52fe43_20261102.bundle` 13.777.588 byte, SHA-256 `20E20E1248001EA047FBDC11318A17AA35B777E4369496A733FE2709869D7F29` (khớp Ni và Tiny); `reportTelegramConfig` → `telegram.config.valid=true` (copy `local.properties` ngay trên Tiny theo ngoại lệ đã duyệt, không đọc nội dung); `assembleShareableDebug` BUILD SUCCESSFUL 1m40s |
| Byte | 33.812.590 |
| SHA-256 | `F376BCCBAB9B9FD9381FB7EA08068BDD9203AACB4C00078E282560971F8D77E7` |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (cùng cert các candidate trước; cài đè thành công) |
| Telegram config | true (không in giá trị) |
| File thật | Tiny `C:\Bots\MeloVault-CAIDAT-IsolatedQA-20261102-d52fe43\app\build\outputs\apk\shareable\debug\app-shareable-debug.apk`; bản sao Drive mount Ni `Z:\MeloVault_QA\MeloVault-CAIDAT-QA-CANDIDATE-20261102-vc121-d52fe43\` (SHA-256 bản sao khớp) |
| Test JVM (gate Ni, trên `d52fe43`) | Debug/Release mỗi biến thể 201 suite, **2725 test**, 21 skipped, 0 failures, 0 errors (+27 so với 2698); assemble Shareable Debug/Release, `-PmeloOpen=true` Debug, Personal Debug BUILD SUCCESSFUL; `git diff --check` sạch. Không chạy lại test trên Tiny |
| Thiết bị đã test | Sony XQ-AS52 `QV725WEQ54` Android 12, cài bằng đúng một lệnh `adb install -r` (vc121 = vc121, cert khớp, media không phát, màn OFF); SHA bản cài (pull `base.apk`) = `F376BCCB…77E7`, khớp APK |
| Kiểm Drive | Thư mục, `hinh`, APK và ảnh tổng hợp: chỉ quyền owner (`get_file_permissions` = 1 người, `owner`); kích thước APK trên Drive 33.812.590 byte khớp; `hinh` có 48 ảnh (không có ảnh thư viện có tên bài). Tải bằng trình duyệt chưa thử |

APK Release (build trên Ni, không có config Telegram) **không cài, không upload**; chưa chạy trên máy. Một APK Debug trước đó (`1037FB53…DF93`, commit `2242319`) đã cài lên Sony trong phiên QA và bị thay bằng bản này; **không upload** bản đó. Ảnh Q2–Q5 (giao diện không đổi giữa hai bản) lấy từ bản đó, được gắn tiền tố `A_` trong thư mục `hinh`; ảnh `B_` là bản cuối.

Rollback: cài lại candidate trước bằng `adb install -r` (cùng vc121/cert), ví dụ nháy-header `E5D808D2C29F70333350EF3682A50FBF686D6140C93513F1A6AA7534A27C6B74` (xem `2026-11-01-nhay-header-khi-cuon-apk-test.md`); không xóa dữ liệu. Revert các commit `091dc96`, `2242319`, `d52fe43` trên nhánh nếu cần bỏ thay đổi; chỉ có khóa prefs mới `dev_tools_unlocked`, không migration.

## Chưa xác nhận

**CHƯA XÁC NHẬN ÂM THANH THỰC** (QA không phát nhạc). Lỗi “chữ bị thanh hệ thống che” **không tái hiện được trên Sony Android 12** (cửa sổ không tràn viền ở máy này); sửa dựa trên phân tích mã, **cần xem trên máy Android 15+ (ví dụ S25)**. Soạn thư (tiêu đề + thân thư) chưa kiểm trên máy: Sony không có app email, chỉ kiểm nhánh dự phòng sao chép địa chỉ. Q8 xoay ngang / chữ lớn 1.3 CHƯA KIỂM (cần đổi cài đặt hệ thống). Đồng bộ chỉ đọc (không bật/tắt, không “Đồng bộ ngay”). Release/`.open`/Personal trên máy. Màu “Teal sáng” không áp cho màn Cài đặt (cũng như trước thay đổi này).
