# APK test — 2026-10-31-loi-nhac-p2b

**QA CANDIDATE — CHƯA NGHIỆM THU.** Không phải bản phát hành; chưa merge/push. Tính năng tắt sẵn cho tới khi người dùng đồng ý. Bản hiện hành là **REWORK-01 (`fd1f943`)**, đã sửa lỗi B1 (xoay màn hình làm pane Lời nhạc kẹt “Đang tìm lời…”); bản cũ `8d4284e` **đã bị thay thế, không dùng**.

## Bản hiện hành — REWORK-01 (`fd1f943`)

- Thư mục Drive (owner-only, `ntdung.md@gmail.com`): [MeloVault-LOINHAC-QA-CANDIDATE-20261101-vc121-fd1f943](https://drive.google.com/drive/folders/1RYfu5WrIY7_fqx-mA_sTukNfImvurHAm)
- APK Debug: [MeloVault-0.6.96-beta-vc121-loi-nhac-p2b-fd1f943-QA-CANDIDATE-debug.apk](https://drive.google.com/file/d/1VlIW8XODu7ba0vyNfF0pZQfBSnVZrRHV/view)
- Ảnh QA: xem [`-hinh-nghiem-thu.md`](2026-10-31-loi-nhac-p2b-hinh-nghiem-thu.md)

| Thuộc tính | Giá trị |
|---|---|
| Loại | Debug QA (debuggable), biến thể `shareable` thường (không `.open`) |
| Package | `app.melovault.player` |
| versionCode / versionName | 121 / `0.6.96-beta` (không đổi, không hạ cấp) |
| Source | `fd1f943c5f844869db7581d697fb2aaaedfb0740` (nhánh `dot/2026-10-31-loi-nhac-p2b`); mã sửa B1 ở `fd1f943`, sau đó chỉ commit hồ sơ |
| Baseline | `94ab6b0`; bản trước trên Sony = `8d4284e` (SHA `31609D80…1E88`) |
| Build | Tiny, clone cô lập `C:\Bots\MeloVault-LOINHAC-IsolatedQA-20261101-fd1f943` từ bundle `MeloVault_loi-nhac-p2b_full_fd1f943_20261101.bundle` 13.428.563 byte, SHA-256 `4d13ba15208a7a08a8441e83eb0d663d5b8617f3835ea30009a03aba7cc97b73` (khớp hai đầu); `reportTelegramConfig` → `telegram.config.valid=true`; `BUILD SUCCESSFUL` 2m52s |
| Byte | 33.642.436 |
| SHA-256 | `62ED3500C171AAF9CCFCEE3EDDB8D7A11F3D08B65B93DA425A3790126B173DB9` |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (khớp app đang cài) |
| Telegram config | true (không in giá trị) |
| File thật | Tiny `C:\Bots\MeloVault-LOINHAC-IsolatedQA-20261101-fd1f943\app\build\outputs\apk\shareable\debug\app-shareable-debug.apk`; bản sao Drive mount Ni `Z:\MeloVault_QA\MeloVault-LOINHAC-QA-CANDIDATE-20261101-vc121-fd1f943\` |
| Test JVM (gate Ni) | Debug/Release 2419 / 21 skipped / 0 failures / 0 errors (109 test lời nhạc; +7 cho B1) |
| Gate Ni khác | assemble Shareable Debug/Release, open Debug, Personal Debug đều `BUILD SUCCESSFUL` |
| Thiết bị đã test | Sony XQ-AS52 `QV725WEQ54` Android 12, cài bằng đúng một lệnh `adb install -r`; SHA bản cài (pull `base.apk`) khớp APK |
| Kiểm Drive | Thư mục và APK: chỉ quyền owner (`get_file_permissions` = 1 người, `owner`); kích thước APK trên Drive 33.642.436 byte khớp; SHA-256 bản trong thư mục đồng bộ `Z:` khớp trước khi tải lên; 10 tệp (APK + 9 ảnh). Tải bằng trình duyệt chưa thử |

Rollback: cài lại `8d4284e` (cùng vc121/cert; có lỗi B1) hoặc candidate giao diện đa thiết bị (`367CAA37…F1006`, xem `2026-10-31-giao-dien-da-thiet-bi-apk-test.md`) bằng `adb install -r`; không xóa dữ liệu. Tắt tính năng: công tắc “Lời nhạc từ LRCLIB” trong Cài đặt.

## Bản đã thay thế — `8d4284e` (có lỗi B1)

- Thư mục: [MeloVault-LOINHAC-QA-CANDIDATE-20261101-vc121-8d4284e](https://drive.google.com/drive/folders/1MF7ltkP3LgMcwckr0LOYeDGg9sJAJ3ST); APK [`…-8d4284e-QA-CANDIDATE-debug.apk`](https://drive.google.com/file/d/1XJAcC1qiKx2EE4OXL14ZyCCx5AUpL38k/view), SHA-256 `31609D80389A9E6EAD8F6417719B640FFBAFFEC3D92916083E3611E687821E88`, 33.642.436 byte. Chỉ còn giá trị làm bằng chứng QA ban đầu; **không dùng để nghiệm thu**.

## Chưa xác nhận

Âm thanh thật (**CHƯA XÁC NHẬN ÂM THANH THỰC**); lời tự động cho bài Telegram có tên + nghệ sĩ khớp LRCLIB (tài khoản không có bài như vậy); chữ lớn 1.3; Release/open/Personal trên máy; B1 chỉ kiểm lại bằng bài thiếu nghệ sĩ (đường “cache/chính sách”), chưa kiểm bằng bài đang có request mạng; Y700/S25/FYT.
