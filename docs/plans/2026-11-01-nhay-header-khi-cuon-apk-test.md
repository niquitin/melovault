# APK test — 2026-11-01-nhay-header-khi-cuon

**QA CANDIDATE — CHƯA NGHIỆM THU.** Không phải bản phát hành; chưa merge/push. Đây là bản Debug QA để chủ dự án thử trên Sony; chưa có chấp thuận ĐẠT.

- Thư mục Drive (owner-only, `ntdung.md@gmail.com`): [MeloVault-NHAYHEADER-QA-CANDIDATE-20261101-vc121-4fe5a37](https://drive.google.com/drive/folders/1pMc9Loe7Z5cCwdW1vSVK3VEHTw9yIDgJ)
- APK Debug: [MeloVault-0.6.96-beta-vc121-nhay-header-4fe5a37-QA-CANDIDATE-debug.apk](https://drive.google.com/file/d/1KUE-zK-sMNsc3nmJTUxtJmpEV1dqc274/view)
- Ảnh/video QA: xem [`-hinh-nghiem-thu.md`](2026-11-01-nhay-header-khi-cuon-hinh-nghiem-thu.md)

| Thuộc tính | Giá trị |
|---|---|
| Loại | Debug QA (debuggable), biến thể `shareable` thường (không `.open`) |
| Package | `app.melovault.player` |
| versionCode / versionName | 121 / `0.6.96-beta` (không đổi, không hạ cấp) |
| Source | Nhánh `dot/2026-11-01-nhay-header-khi-cuon`; APK build từ commit mã `4fe5a370ce58719495d63950d9dd33e6ed7a0619` (checkout detach đúng commit này; các commit sau chỉ là hồ sơ). Baseline `8a8c978`, kế hoạch `8a48584` |
| Build | Tiny, clone cô lập `C:\Bots\MeloVault-NHAY-IsolatedQA-20261101-4fe5a37` từ bundle `MeloVault_nhay-header_full_4fe5a37_20261101.bundle` 13.721.556 byte, SHA-256 `D7167D579437216B7737D5D0F095255302D1AF994CA8F6EDB84A454F8596E495` (khớp Ni và Tiny); `reportTelegramConfig` → `telegram.config.valid=true`; `assembleShareableDebug` BUILD SUCCESSFUL 2m54s |
| Byte | 33.789.892 |
| SHA-256 | `E5D808D2C29F70333350EF3682A50FBF686D6140C93513F1A6AA7534A27C6B74` |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (khớp app đang cài trước đó) |
| Telegram config | true (không in giá trị) |
| File thật | Tiny `C:\Bots\MeloVault-NHAY-IsolatedQA-20261101-4fe5a37\app\build\outputs\apk\shareable\debug\app-shareable-debug.apk`; bản sao Drive mount Ni `Z:\MeloVault_QA\MeloVault-NHAYHEADER-QA-CANDIDATE-20261101-vc121-4fe5a37\` (SHA-256 bản sao khớp) |
| Test JVM (gate Ni, trên `4fe5a37`) | Debug/Release mỗi biến thể 200 suite, **2698 test**, 21 skipped, 0 failures, 0 errors (+15 so với 2683); assemble Shareable Debug/Release BUILD SUCCESSFUL; `git diff --check` sạch. Không chạy lại test trên Tiny |
| Thiết bị đã test | Sony XQ-AS52 `QV725WEQ54` Android 12, cài bằng đúng một lệnh `adb install -r` (vc121 = vc121, cert khớp, media không phát); SHA bản cài (pull `base.apk`) = `E5D808D2…6B74`, khớp APK |
| Kiểm Drive | Thư mục, `hinh` và APK: chỉ quyền owner (`get_file_permissions` = 1 người, `owner`); kích thước APK trên Drive 33.789.892 byte khớp; 18 tệp (APK + 17 tệp trong `hinh`). Tải bằng trình duyệt chưa thử |

APK Release (đã build trên Ni bằng `assembleShareableRelease`, chưa có config Telegram) **không cài, không upload**, chưa chạy trên máy.

Rollback: cài lại bản trước trên Sony bằng `adb install -r` (cùng vc121/cert), ví dụ candidate nhiều playlist `5B2FB5CC323B486ADFC2F359BF7C9FCC20E104D0D6FDE4DB3D2D3B3BDE03FEF8` (xem `2026-11-01-nhieu-playlist-co-ten-apk-test.md`); không xóa dữ liệu. Revert commit `4fe5a37` trên nhánh nếu cần bỏ sửa; không có migration.

## Chưa xác nhận

**CHƯA XÁC NHẬN ÂM THANH THỰC** (QA không phát nhạc). Playlist ≥ 20 bài (Sony chỉ có “Yêu thích · 2 bài”; QA dùng tab Thư viện/Tất cả). Q6 Hai khung/xoay ngang chưa kiểm. Kéo bằng ngón tay thật của người dùng (QA dùng `input motionevent` giả lập). Release/`.open`/Personal trên máy. Nguyên nhân 2 (công thức đo sai ở ranh giới dòng) chưa tách riêng trên máy. Hành vi mới “header chỉ đổi khi nhả tay hoặc khi danh sách trôi” cần chủ dự án xác nhận.
