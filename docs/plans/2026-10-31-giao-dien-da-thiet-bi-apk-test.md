# APK test — 2026-10-31-giao-dien-da-thiet-bi

**QA CANDIDATE — CHƯA NGHIỆM THU.** Không phải bản phát hành; chưa merge/push. Kèm hạn chế: UI-03 chưa sửa được trên máy thật, UI-02 chỉ cải thiện một phần (xem [`-bao-cao.md`](2026-10-31-giao-dien-da-thiet-bi-bao-cao.md) mục “Chặng C”).

- Thư mục Drive (owner-only, `ntdung.md@gmail.com`): [MeloVault-GDTB-QA-CANDIDATE-20261031-vc121-20bdf95](https://drive.google.com/drive/folders/1MojVY6lBo18qEcd6L1nXmU_KkBT_lEWY)
- APK Debug: [MeloVault-0.6.96-beta-vc121-giao-dien-da-thiet-bi-20bdf95-QA-CANDIDATE-debug.apk](https://drive.google.com/file/d/1yj0UciXS0G2xDJiOf8AW1eXuBjh6AKLO/view)

| Thuộc tính | Giá trị |
|---|---|
| Loại | Debug QA (debuggable), biến thể `shareable` thường (không `.open`) |
| Package | `app.melovault.player` |
| versionCode / versionName | 121 / `0.6.96-beta` (không đổi, không hạ cấp) |
| Source | `20bdf9509464e4fc79f7129875bcc8c9e219564e` (nhánh `dot/2026-10-31-giao-dien-da-thiet-bi`); code cuối `75375b4`, sau đó chỉ commit hồ sơ |
| Baseline | `94ab6b0` (app trên Sony trước đó = `a2f0a88`, SHA `6e3d6b6e…a029`) |
| Build | Tiny, clone cô lập `C:\Bots\MeloVault-GDTB-IsolatedQA-20261031-20bdf95` từ bundle SHA `7751DECE37D0BED72E3E50C03D83F1C2522C02F4D5533874609D55009D1D0C48`; `BUILD SUCCESSFUL` 2m42s |
| Byte | 33.593.284 |
| SHA-256 | `367CAA3738171D4163CAC04844F9E9F4FB2D0BC3C142CDA631F889C2929F1006` |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (khớp app đang cài) |
| Telegram config | true (`reportTelegramConfig` → `telegram.config.valid=true`; không in giá trị) |
| File thật | Tiny `C:\Bots\_qa_temp\GD-G4\MeloVault-gdtb-20bdf95-vc121-debug.apk`; Ni `docs/plans/artifacts/2026-10-31-giao-dien-da-thiet-bi/G4/` (bị `.gitignore`) |
| Test JVM (gate Ni chặng B) | Debug/Release 2336 / 21 skipped / 0 failures / 0 errors |
| Thiết bị đã test | Sony XQ-AS52 `QV725WEQ54` Android 12 (đang cài bản này) |
| Kiểm Drive | Thư mục và APK: chỉ quyền owner (`get_file_permissions` = 1 người, `owner`); kích thước APK trên Drive 33.593.284 byte khớp, SHA-256 bản trong thư mục đồng bộ khớp trước khi tải lên. Tải bằng trình duyệt chưa thử |

Rollback: cài lại APK baseline `a2f0a88` (cùng vc121/cert, SHA `6e3d6b6e…a029`) bằng `adb install -r`; không xóa dữ liệu.

Chưa xác nhận: âm thanh thật (**CHƯA XÁC NHẬN ÂM THANH THỰC**); Gọn chia đôi bằng ảnh; Release APK không build/tải lên ở chặng này; tablet/máy gập thật ngoài phạm vi.
