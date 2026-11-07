# APK test — 2026-11-07-phat-hanh-beta

**QA CANDIDATE — CHƯA NGHIỆM THU — CHƯA ĐĂNG.** Bản open Release cho beta bạn bè. Không phải bản phát hành; chưa có tag, Release hay trang tải.

- Thư mục Drive (owner-only, `ntdung.md@gmail.com`): [MeloVault-BETA-QA-CANDIDATE-20261107-vc122-a0694c0](https://drive.google.com/drive/folders/16_4T98kAlVHbHYrSdjDwcRfLPYSCnY-C)
- APK: [MeloVault-0.6.97-open-vc122-beta-a0694c0-QA-CANDIDATE-CHUA-DANG-release.apk](https://drive.google.com/file/d/1MGjVueIvlBI3IeASZJ4RzTvcRv7XzAZT/view?usp=drivesdk)
- Ảnh: [hồ sơ ảnh](2026-11-07-phat-hanh-beta-hinh-nghiem-thu.md)

| Thuộc tính | Giá trị |
|---|---|
| Loại | Release, `-PmeloOpen=true`, không debuggable; ký bằng khóa debug tạm của Tiny (quyết định chủ dự án 07/11/2026) |
| Package | `app.melovault.player.open` (không phải `app.melovault.player`) |
| versionCode / versionName | **122 / `0.6.97-open`** (không tăng), nhãn launcher `MeloVault` |
| Source | `a0694c05b8df22ef90a01cdd52738528747341b7`, nhánh `dot/2026-11-07-phat-hanh-beta`, baseline `71189c9` (gộp widget `7ce38ec`). Commit sau đó chỉ là tài liệu |
| Build | Tiny, clone cô lập `C:\Bots\MeloVault-BETA-IsolatedQA-20261107-a0694c0`; bundle SHA-256 `757403676C236121BD966A161C19AE5E1BD2E3CF17C970404BB60D6C04A26ABE`, khớp Ni và Tiny; BUILD SUCCESSFUL 2m31s |
| Byte | 31.051.460 |
| SHA-256 | `54AB04C8A0F4418B379EA2DA19FFE281283D84E4A46B5DE90BE7DD8FDFC42CCF` |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`, apksigner Verifies (v2) |
| Telegram config | Cặp open hợp lệ nhúng (true, không in giá trị); `verifyOpenApkHasNoTelegramHash` → found=false; `verifyOpenApkHasOpenTelegramHash` → found=true |
| File thật | Tiny `C:\Bots\_qa_temp\beta-20261107\r2\`; Drive mount `Z:\MeloVault_QA\MeloVault-BETA-QA-CANDIDATE-20261107-vc122-a0694c0\` (SHA-256 khớp) |
| Test JVM | Debug/Release mỗi loại 209 suite, 2.824 test, 21 skipped, 0 lỗi; assemble Shareable Debug/Release, `.open` Debug/Release, Personal Debug xanh |
| Thiết bị | Sony XQ-AS52 `QV725WEQ54` Android 12, `adb install -r`, APK pull lại khớp SHA; `app.melovault.player` không đổi |
| Kiểm Drive | Thư mục, APK, ảnh: một quyền duy nhất, owner; kích thước APK 31.051.460 B khớp. **Tải bằng trình duyệt chưa thử** |

**Bản đang cài trên Sony** là đúng APK này (gói `.open`); bản widget `app.melovault.player` vẫn còn nguyên. Sony chưa đăng nhập bản `.open` nên chưa phát được: chủ dự án đăng nhập rồi thử mục "Chủ dự án tự thử" của [kế hoạch](2026-11-07-phat-hanh-beta.md).

Lưu ý tải: nếu Drive báo `cannotDownloadAbusiveFile`, ghi BLOCKED riêng phần tải; không né cảnh báo, không mở quyền công khai.

Rollback: không hạ APK, không xóa dữ liệu. Chưa đăng nên không cần gỡ gì ngoài Drive. Revert commit `9526d35` và `a0694c0` rồi build lại với versionCode cao hơn nếu cần.

**CHƯA XÁC NHẬN ÂM THANH THỰC.** Phần chưa kiểm: [báo cáo](2026-11-07-phat-hanh-beta-bao-cao.md) mục "APK QA bàn giao".
