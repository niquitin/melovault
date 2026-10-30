# APK test — 2026-10-30-don-ui-sau-xoay

**QA CANDIDATE — CHƯA NGHIỆM THU.** Không phải bản phát hành; chưa merge/push.

- Thư mục Drive (owner-only, `ntdung.md@gmail.com`): [MeloVault-DONUI-QA-CANDIDATE-20261030-vc121-8bf9d03](https://drive.google.com/drive/folders/1vMO9Z57VPJa0ui12Y8QD2V2CmsTDKLb7)
- APK Debug: [MeloVault-0.6.96-beta-vc121-don-ui-8bf9d03-QA-CANDIDATE-debug.apk](https://drive.google.com/file/d/1RTdJgqffd8nAUpaSMRnzRMmnVRrJfN-B/view)

| Thuộc tính | Giá trị |
|---|---|
| Loại | Debug QA (debuggable) |
| Package | `app.melovault.player` |
| versionCode / versionName | 121 / `0.6.96-beta` (không đổi, không hạ cấp) |
| Source | `8bf9d03f1e457fb1c996b05340eb5695f7dfa56e` (nhánh `dot/2026-10-30-don-ui-sau-xoay`) |
| Build | Tiny, clone cô lập `C:\Bots\MeloVault-DONUI-IsolatedQA-20261030-8bf9d03`, từ bundle SHA `7C8681E6…6837` |
| Byte | 33.576.900 |
| SHA-256 | `E6EC8773C8F958301CCE9CB9CFAD4366F77A835A4D5E7A2055F9C1ED1B08E92C` |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (khớp app đang cài) |
| Telegram config | true (không in giá trị) |
| File thật | Tiny `C:\Bots\_qa_temp\don-ui-8bf9d03-20261030\MeloVault-DONUI-QA-CANDIDATE-CHUA-NGHIEM-THU-vc121-8bf9d03-debug-20261030.apk` |
| Thiết bị đã test | Sony XQ-AS52 `QV725WEQ54` (đang cài bản này, SHA sau cài khớp) |
| Kiểm Drive | Quyền chỉ owner (thư mục và APK); đọc lại qua Drive for desktop trên Ni: 33.576.900 byte, SHA khớp. Tải bằng trình duyệt chưa thử |

Bản Release cùng source (chỉ dùng để chứng minh D1, **không** tải lên Drive): 30.769.518 byte, SHA `50F1FFB59CA2BD4114EBAC7E5D2A7AA91581B0220DD0210E347B317DE19D0205`, cùng cert, non-debuggable, 0 chuỗi `MeloAnchor` trong dex; nằm cạnh bản Debug trên Tiny.

Rollback: cài lại APK Đảo đã ĐẠT (`1D61F67F…D485`, cùng vc121/cert) bằng `adb install -r`; không cần xóa dữ liệu.

Chưa xác nhận: âm thanh thật (**CHƯA XÁC NHẬN ÂM THANH THỰC**); tablet/Shanling ngoài phạm vi theo quyết định chủ dự án 30/10 17:01.
