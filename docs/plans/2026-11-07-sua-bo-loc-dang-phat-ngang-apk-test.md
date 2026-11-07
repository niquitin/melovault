# APK test — 2026-11-07-sua-bo-loc-dang-phat-ngang

**QA CANDIDATE — CHƯA NGHIỆM THU.** Bản Debug QA, không phải bản phát hành. Tên thư mục và tên file trên Drive mang nhãn `QA-CANDIDATE`.

- Thư mục Drive (owner-only, `ntdung.md@gmail.com`): [MeloVault-SUABOLOC-QA-CANDIDATE-20261107-vc122-f054637](https://drive.google.com/drive/folders/16OWV3tB_Bd2yKRmvLfdn2C58MjbLmUTN)
- APK Debug: [MeloVault-0.6.97-beta-vc122-sua-bo-loc-f054637-QA-CANDIDATE-debug.apk](https://drive.google.com/file/d/1q6F0KPWz-QYpe5BPWBrDK7itdnDvPNp8/view?usp=drivesdk)
- Ảnh: [hồ sơ ảnh](2026-11-07-sua-bo-loc-dang-phat-ngang-hinh-nghiem-thu.md)

| Thuộc tính | Giá trị |
|---|---|
| Loại | Debug QA, biến thể `shareable` thường (không `.open`) |
| Package | `app.melovault.player` |
| versionCode / versionName | **122 / `0.6.97-beta`** (không tăng) |
| Source | `f05463709faf532949d58a31ef7a46c67211cfd6`, nhánh `dot/2026-11-07-sua-bo-loc-dang-phat-ngang`, baseline `f82185b`. Mã ứng dụng của source này trùng `233954f`; các commit sau chỉ là tài liệu |
| Build | Tiny, clone cô lập `C:\Bots\MeloVault-SUABOLOC-IsolatedQA-20261107-f054637`; bundle đầy đủ (HEAD), SHA-256 `7C31C93DC2A4EEE90DA93B8D758B122A59666151CD6B9E23C541D6F987815E91`, khớp Ni và Tiny; BUILD SUCCESSFUL 3m17s |
| Byte | 33.910.894 |
| SHA-256 | `7FB940219704E78A754971D9773DE5CE93CE18422277C53262EC144890FB7A24` |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (cùng cert bản đang cài) |
| Telegram config | true (không in giá trị) |
| File thật | Tiny `C:\Bots\_qa_temp\sua-bo-loc-20261107-f054637\`; Ni `docs/plans/artifacts/2026-11-07-sua-bo-loc-dang-phat-ngang/` (không commit); Drive mount `Z:\MeloVault_QA\MeloVault-SUABOLOC-QA-CANDIDATE-20261107-vc122-f054637\` (SHA-256 cả ba khớp) |
| Test JVM | Debug/Release mỗi biến thể 207 suite, 2.799 test, 21 skipped, 0 failure, 0 error; assemble Shareable Debug/Release, `.open` Debug, Personal Debug xanh |
| Thiết bị đã test | Sony XQ-AS52 `QV725WEQ54` Android 12, `adb install -r`, APK pull từ máy khớp SHA. QA Q1–Q6: [báo cáo](2026-11-07-sua-bo-loc-dang-phat-ngang-bao-cao.md) |
| Kiểm Drive | Thư mục, APK và ảnh tổng hợp: một quyền duy nhất, owner. Kích thước APK 33.910.894 B khớp. **Tải bằng trình duyệt chưa thử**; connector không trả SHA Drive |

**APK này không có widget.** Cài nó đã thay candidate widget vc122 (`58BD55F0B5C22A256E6572516C277A3049A617F853A1830B30280F70AD48FD07`) trên Sony. Bản widget đó còn trên Tiny (`preinstall-installed.apk` cùng thư mục QA), muốn sơ bộ widget thì phải cài lại nó.

Lưu ý tải: nếu Drive báo `cannotDownloadAbusiveFile`, ghi BLOCKED riêng phần tải; không né cảnh báo, không mở quyền công khai. Bản đúng SHA đã cài sẵn trên Sony.

Rollback: không hạ APK/DB, không xóa dữ liệu. Revert các commit S1–S5 (mỗi lỗi một commit) rồi build lại với versionCode cao hơn.

**CHƯA XÁC NHẬN ÂM THANH THỰC.** Phần chưa kiểm: [báo cáo](2026-11-07-sua-bo-loc-dang-phat-ngang-bao-cao.md) mục 5.
