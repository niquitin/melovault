# APK test — 2026-10-30-anh-bia-moi-mau

Trạng thái: **QA CANDIDATE — CHƯA NGHIỆM THU**. Chưa có xác nhận ĐẠT của chủ dự án.

## APK Debug (đã cài QA trên Sony)

| Trường | Giá trị |
|---|---|
| Package | `app.melovault.player` |
| versionCode / versionName | `121` / `0.6.96-beta` (giữ nguyên, không tăng — không phải bản phát hành mới, chỉ QA candidate nối tiếp bản DONUI đã cài) |
| Git HEAD / nhánh | `92e3db9bc35da8344d00b3aba62cef73996eeffa` / `dot/2026-10-30-anh-bia-moi-mau` |
| Nguồn build | Clone cô lập Tiny `C:\Bots\MeloVault-ANHBIA-IsolatedQA-20261030-92e3db9` từ bundle Ni, HEAD khớp |
| SHA-256 APK | `7F51A1B460402949F19886D887359BBB657494512EB0740485AF9E00C38214A9` |
| Kích thước | 33.576.900 byte |
| Chứng chỉ ký (SHA-256) | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (khớp bản đang cài trên Sony trước đó, không đổi signer) |
| Telegram config | `true` (`reportTelegramConfig` → `telegram.config.valid=true`; chỉ boolean, không ghi giá trị) |
| Test trước build | Debug 2290 passed / 21 skipped / 0 failed; Release giống hệt |
| Đường dẫn thật trên Tiny | `C:\Bots\MeloVault-ANHBIA-IsolatedQA-20261030-92e3db9\app\build\outputs\apk\shareable\debug\app-shareable-debug.apk` |
| Cài trên | Sony XQ-AS52, serial `QV725WEQ54`, `adb install -r` (Success); SHA-256 sau cài (pull lại base.apk) khớp hệt bản build |

## APK Release (chỉ build, chưa cài QA)

| Trường | Giá trị |
|---|---|
| SHA-256 | `A8B5517FCE65083C459B728E6955693A1C111BB314564E5E0EFF50C6D0DC5393` |
| Kích thước | 30.769.518 byte |
| Đường dẫn Tiny | `...\app\build\outputs\apk\shareable\release\app-shareable-release.apk` |
| Trạng thái | BUILD SUCCESSFUL; không `install -r`/QA trên thiết bị (ngoài phạm vi S4 của kế hoạch này) |

## Link Drive (đã xác minh owner-only qua connector Google Drive)

- Thư mục: https://drive.google.com/drive/folders/1Ex37qmRNMLlK9hjrG2tjutazOdIEr0BC
- APK Debug: https://drive.google.com/file/d/1DG7zZ8BRfublLAATY3Z47klbapui2p-G/view?usp=drivesdk
- Ảnh tổng hợp: https://drive.google.com/file/d/1CZI8Wk-UMAUnnhcs5jl4CpEP7-RzU6Yq/view?usp=drivesdk

Quyền truy cập (kiểm qua `get_file_permissions`): chỉ một permission — `owner` = `ntdung.md@gmail.com`. Không có "anyone with link", không public.

## Rollback

APK này không thay đổi songKey/schema/owner/session/signer/package. Gỡ bỏ an toàn: `adb install -r` đè lại bản APK trước đó (ví dụ bản DONUI `E6EC8773C8F958301CCE9CB9CFAD4366F77A835A4D5E7A2055F9C1ED1B08E92C`, cùng signer, versionCode 121) nếu chủ dự án yêu cầu quay lại; không cần uninstall/clear data.

## 3–4 thao tác chủ dự án có thể tự kiểm trên Sony

1. Mở MeloVault (theme mặc định "Hiện tại") → xem Thư viện: mỗi dòng bài có ảnh bìa thật (không chỉ chữ cái) — trước đây chỉ Teal mới có.
2. Cài đặt → Giao diện → chọn "Teal sáng" → quay lại Thư viện: ảnh bìa vẫn hiện, không vỡ layout, tên bài vẫn 2 dòng.
3. Cài đặt → tắt "Tải ảnh bìa từ Telegram" → cuộn xuống vùng chưa từng xem: các dòng mới chỉ hiện ô chữ cái (không tải ảnh); bật lại công tắc → cuộn tiếp → ảnh bắt đầu hiện cho các dòng đó.
4. Xoay ngang/dọc vài lần trong khi cuộn hoặc tìm kiếm: hàng đang xem giữ nguyên, không nhảy bài, không có ảnh của bài khác hiện nhầm.
