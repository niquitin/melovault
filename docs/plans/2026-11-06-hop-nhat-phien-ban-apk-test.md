# APK test — 2026-11-06-hop-nhat-phien-ban

**ĐÃ ĐẠT 06/11/2026** ([biên bản](2026-11-06-hop-nhat-phien-ban-nghiem-thu.md)). Vẫn là bản Debug QA, **không phải bản phát hành**. Tên thư mục và tên file trên Drive giữ nhãn `QA-CANDIDATE` từ lúc tải lên, không đổi.

- Thư mục Drive (owner-only, `ntdung.md@gmail.com`): [MeloVault-HOPNHAT-QA-CANDIDATE-20261106-vc122-7cd4ebb](https://drive.google.com/drive/folders/1jCgMBnZgXlBUKdZepRP8T6R6V96yuymq)
- APK Debug: [MeloVault-0.6.97-beta-vc122-hop-nhat-7cd4ebb-QA-CANDIDATE-debug.apk](https://drive.google.com/file/d/1auI5QVrt2nQfTWGC8I4ZIm8JNeXoi-zB/view?usp=drivesdk)
- Ảnh: [hồ sơ ảnh](2026-11-06-hop-nhat-phien-ban-hinh-nghiem-thu.md)

| Thuộc tính | Giá trị |
|---|---|
| Loại | Debug QA, biến thể `shareable` thường (không `.open`) |
| Package | `app.melovault.player` |
| versionCode / versionName | **122 / `0.6.97-beta`** |
| Source | `7cd4ebb0af7a3f75c6a5c038831f7720b24319df`, nhánh `dot/2026-11-06-hop-nhat-phien-ban`, baseline `42be21c` |
| Build | Tiny, clone cô lập `C:\Bots\MeloVault-HOPNHAT-IsolatedQA-20261106-7cd4ebb`; bundle SHA-256 `6EB3A23F7B8F784C47D5636E4EA6E1EFA9113E7933A4A04395AC0BCE701C2FE2` khớp hai đầu; BUILD SUCCESSFUL 3m32s |
| Byte | 33.894.510 |
| SHA-256 | `BF7B23A74588BD9826CA9DFBFEBE0AEC5DEC61F8F20D5CD5914CCDDC38C56441` |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (cùng cert bản đang cài, cài đè thành công) |
| Telegram config | true (không in giá trị) |
| File thật | Tiny `C:\Bots\_qa_temp\hop-nhat-20261106-7cd4ebb\`; Ni `docs/plans/artifacts/2026-11-06-hop-nhat-phien-ban/`; Drive mount `Z:\MeloVault_QA\MeloVault-HOPNHAT-QA-CANDIDATE-20261106-vc122-7cd4ebb\` (SHA-256 cả ba khớp) |
| Test JVM | Debug/Release mỗi biến thể 207 suite, 2.796 test, 21 skipped, 0 failure, 0 error; assemble Shareable Debug/Release, `.open` Debug, Personal Debug xanh |
| Thiết bị đã test | Sony XQ-AS52 `QV725WEQ54` Android 12, `adb install -r`, APK pull từ máy khớp SHA. Chi tiết: [báo cáo](2026-11-06-hop-nhat-phien-ban-bao-cao.md) mục 4 |
| Kiểm Drive | Thư mục, APK, ảnh tổng hợp: một quyền duy nhất, owner. Kích thước APK 33.894.510 B khớp. **Tải bằng trình duyệt chưa thử**; connector không trả SHA Drive |

APK kiểm thử thiết bị đi kèm (không dành cho người dùng): `hop-nhat-device-tests-7cd4ebb.apk`, 444.599 B, SHA-256 `DC1DAC3E25E6ED9B1633B95A7D335648A10B34A73247A86EE0D2D5BC08ED8A20`, chỉ trên Tiny/Sony.

Lưu ý tải: nếu Drive báo `cannotDownloadAbusiveFile`, ghi BLOCKED riêng phần tải; không né cảnh báo, không mở quyền công khai. Bản đúng SHA đã cài sẵn trên Sony.

Rollback: không hạ APK/DB, không xóa dữ liệu. Sửa tiến bằng versionCode cao hơn. Cờ riêng: `MeloEqPolicy.FEATURE_ENABLED`, `NamedPlaylistFlags.NAMED_ENABLED`.

**CHƯA XÁC NHẬN ÂM THANH THỰC.** Các phần chưa kiểm khác: [báo cáo](2026-11-06-hop-nhat-phien-ban-bao-cao.md) mục 5.
