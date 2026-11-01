# APK test — 2026-10-31-loi-nhac-p2b

**QA CANDIDATE — CHƯA NGHIỆM THU.** Không phải bản phát hành; chưa merge/push. **Có 1 lỗi đã biết (B1, Q8):** sau khi xoay màn hình, pane Lời nhạc có thể kẹt “Đang tìm lời…” (xem [`-bao-cao.md`](2026-10-31-loi-nhac-p2b-bao-cao.md) mục 10.3). Tính năng tắt sẵn cho tới khi người dùng đồng ý.

- Thư mục Drive (owner-only, `ntdung.md@gmail.com`): [MeloVault-LOINHAC-QA-CANDIDATE-20261101-vc121-8d4284e](https://drive.google.com/drive/folders/1MF7ltkP3LgMcwckr0LOYeDGg9sJAJ3ST)
- APK Debug: [MeloVault-0.6.96-beta-vc121-loi-nhac-p2b-8d4284e-QA-CANDIDATE-debug.apk](https://drive.google.com/file/d/1XJAcC1qiKx2EE4OXL14ZyCCx5AUpL38k/view)
- Ảnh QA: xem [`-hinh-nghiem-thu.md`](2026-10-31-loi-nhac-p2b-hinh-nghiem-thu.md)

| Thuộc tính | Giá trị |
|---|---|
| Loại | Debug QA (debuggable), biến thể `shareable` thường (không `.open`) |
| Package | `app.melovault.player` |
| versionCode / versionName | 121 / `0.6.96-beta` (không đổi, không hạ cấp) |
| Source | `8d4284e24456c37b2ace90b24a14480016de3a7d` (nhánh `dot/2026-10-31-loi-nhac-p2b`); code cuối `3447310`, sau đó chỉ commit hồ sơ |
| Baseline | `94ab6b0` (app trên Sony trước đó = `0.6.96-beta` SHA `367CAA37…F1006`) |
| Build | Tiny, clone cô lập `C:\Bots\MeloVault-LOINHAC-IsolatedQA-20261101-8d4284e` từ bundle SHA `d3ee9e53db1ba6b5374c81f8c099c62358906290cdb8b55f649be8daece86a90`; `BUILD SUCCESSFUL` 2m44s |
| Byte | 33.642.436 |
| SHA-256 | `31609D80389A9E6EAD8F6417719B640FFBAFFEC3D92916083E3611E687821E88` |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (khớp app đang cài) |
| Telegram config | true (`reportTelegramConfig` → `telegram.config.valid=true`; không in giá trị) |
| File thật | Tiny `C:\Bots\MeloVault-LOINHAC-IsolatedQA-20261101-8d4284e\app\build\outputs\apk\shareable\debug\app-shareable-debug.apk`; bản sao Drive mount Ni `Z:\MeloVault_QA\MeloVault-LOINHAC-QA-CANDIDATE-20261101-vc121-8d4284e\` |
| Test JVM (gate Ni chặng B) | Debug/Release 2412 / 21 skipped / 0 failures / 0 errors (102 test lời nhạc) |
| Gate Ni khác | assemble Shareable Debug/Release, open Debug, Personal Debug đều `BUILD SUCCESSFUL` |
| Thiết bị đã test | Sony XQ-AS52 `QV725WEQ54` Android 12 (đang cài bản này; SHA bản cài lại khớp) |
| Kiểm Drive | Thư mục, thư mục `hinh` và APK: chỉ quyền owner (`get_file_permissions` = 1 người, `owner`, `ntdung.md@gmail.com`); kích thước APK trên Drive 33.642.436 byte khớp; SHA-256 bản trong thư mục đồng bộ `Z:` khớp `31609D80…1E88` trước khi tải lên; 17 ảnh có đủ, kích thước khớp bản gốc. Tải bằng trình duyệt chưa thử. (Lần chép đầu vào gốc Drive khác `X:` đã được tôi xóa; chỉ còn bản trong `MeloVault_QA`.) |

Rollback: cài lại APK baseline (vc121/cert `eecd3df7…fce8`, SHA `367CAA37…F1006`, candidate giao diện đa thiết bị trong `2026-10-31-giao-dien-da-thiet-bi-apk-test.md`) bằng `adb install -r`; không xóa dữ liệu. Tắt tính năng: công tắc “Lời nhạc từ LRCLIB” trong Cài đặt.

Chưa xác nhận: âm thanh thật (**CHƯA XÁC NHẬN ÂM THANH THỰC**); lời tự động cho bài Telegram có tên + nghệ sĩ khớp LRCLIB (tài khoản không có bài như vậy); xoay ngang → dọc; chữ lớn 1.3; Release/open/Personal trên máy; Y700/S25/FYT.
