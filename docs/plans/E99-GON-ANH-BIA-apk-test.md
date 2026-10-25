# E99-GON-ANH-BIA — APK QA Candidate

## Bản mới nhất: vc115 — REWORK-01 (25/10/2026)

**Trạng thái:** **QA CANDIDATE — CHƯA NGHIỆM THU** (chờ chủ dự án duyệt REWORK-01: Gọn luôn có ảnh bìa với mọi Kiểu màu). Bản test nội bộ; giới hạn: Janky không đo lại, Tablet/Hai khung/Y700/S25/FYT/Car chưa kiểm, âm thanh thật chưa xác nhận.

- [MỞ APK TEST E99 REWORK-01 (vc115) TRÊN GOOGLE DRIVE](https://drive.google.com/file/d/1b9qo6iH44PFqBy8PPWskFO48wLWgDxml/view)
- [THƯ MỤC APK + ẢNH QA vc115](https://drive.google.com/drive/folders/1SyCcytPbZoScnszCkdPV_ErwOqMHr8nB)

| Trường | Giá trị |
|---|---|
| Build | Debug (flavor shareable), dựng ở Tiny trong clone cô lập `C:\Bots\MeloVault-E99-IsolatedQA` (Git bundle SHA-256 `F25514A0…896FE`, HEAD `6fb4e65`, sạch), có Telegram config |
| Package | app.melovault.player |
| versionCode / versionName | 115 / 0.6.90.1-beta |
| Git source | `6fb4e65` (nhánh `dot/E99-GON-ANH-BIA`) |
| SHA-256 | 341200C7DC2D00B82FB780193997C5661B70164699D5144C7E80FAD995C636E6 |
| Bytes | 33413060 |
| Signer cert SHA-256 | eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8 (Android Debug) |
| Telegram config | true (chỉ boolean; dòng `telegram.config.valid=true` trong log build Tiny, Opus đọc lại) |
| Drive file ID | 1b9qo6iH44PFqBy8PPWskFO48wLWgDxml |
| Drive folder | `MeloVault_QA/E99-GON-ANH-BIA-20261025-vc115-6fb4e65` (ID 1SyCcytPbZoScnszCkdPV_ErwOqMHr8nB), thư mục mới, không ghi đè vc114 |
| Drive permission | chỉ owner ntdung.md — kiểm bằng connector cho APK, thư mục và thư mục cha `MeloVault_QA` |
| Nguồn trên Tiny | `C:\Bots\_qa_handoff\E99-GON-ANH-BIA\MeloVault-E99-REWORK01-6fb4e65-vc115-TinySigned-debug.apk` |

**Kiểm tra độc lập Opus:** SHA-256 trùng giữa APK trên Tiny (`Get-FileHash`), bản Ni (`D:\Bots\_e99apk\`), bản chép lên Drive (`SHA256.txt`, kích thước Drive 33.413.060) và `base.apk` đang cài trên Shanling (`sha256sum` trên máy); `aapt2 dump badging` + `apksigner verify --print-certs` như bảng.

Đã cài (Sonnet, `install -r`): Shanling M3 Ultra (30313860180939, vc114 → vc115), Sony Xperia 5 II (QV725WEQ54, vc114 → vc115). Cài đè từ vc ≤ 114 được (cùng chữ ký, không hạ phiên bản).

## Bản trước: vc114 (25/10/2026)

**Trạng thái:** **QA CANDIDATE — CHƯA NGHIỆM THU** (chờ chủ dự án duyệt, xem `E99-GON-ANH-BIA-nghiem-thu.md`). Bản test nội bộ, không phải Stable, không phát hành công khai; giới hạn: Teal Phone trên Sony chưa kiểm lại, Y700/S25/FYT chưa kiểm, âm thanh thật chưa xác nhận.

- [MỞ APK TEST E99 (vc114) TRÊN GOOGLE DRIVE](https://drive.google.com/file/d/1SyRp6sJlRHwy-n3iIomaQj0yPT_2yZ5c/view)
- [THƯ MỤC APK + ẢNH QA vc114](https://drive.google.com/drive/folders/1ET_ggpbQVFEu-DmMkbshI8QfehljTgXY)

| Trường | Giá trị |
|---|---|
| Build | Debug (flavor shareable), dựng ở Tiny trong clone cô lập `C:\Bots\MeloVault-E99-IsolatedQA` (từ Git bundle SHA-256 `959A430B…8698F`, HEAD `a606bd7`, sạch), có Telegram config |
| Package | app.melovault.player |
| versionCode / versionName | 114 / 0.6.90-beta |
| Git source | `a606bd7` (nhánh `dot/E99-GON-ANH-BIA`) |
| SHA-256 | 272D53AAC70FCD4DDF86C69B4A11C92E6894B5A6AACC7869BBA36DAE7C07B653 |
| Bytes | 33413060 |
| Signer cert SHA-256 | eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8 (Android Debug) |
| Telegram config | true (chỉ boolean, không đọc/in giá trị; Opus chạy `reportTelegramConfig` trên Tiny) |
| Drive file ID | 1SyRp6sJlRHwy-n3iIomaQj0yPT_2yZ5c |
| Drive folder | `MeloVault_QA/E99-GON-ANH-BIA-20261025-vc114-a606bd7` (ID 1ET_ggpbQVFEu-DmMkbshI8QfehljTgXY) |
| Drive permission | chỉ owner ntdung.md — kiểm bằng connector cho APK, thư mục, thư mục cha `MeloVault_QA` và ảnh tổng hợp |
| Nguồn trên Tiny | `C:\Bots\_qa_handoff\E99-GON-ANH-BIA\MeloVault-E99-a606bd7-vc114-TinySigned-debug.apk` |

**Kiểm tra độc lập Opus:** SHA-256 trùng giữa APK trên Tiny, bản chép về Ni (`D:\Bots\_e99apk\`), bản trên Drive (kiểm sau khi chép, kích thước Drive 33.413.060) và `base.apk` đang cài trên Shanling; `aapt2 dump badging` và `apksigner verify --print-certs` như bảng.

Đã cài (Sonnet, `install -r`): Shanling M3 Ultra (30313860180939, vc113 → vc114), Sony Xperia 5 II (QV725WEQ54, vc113 → vc114). Chưa cài: Y700, S25, FYT. Cài đè từ vc ≤ 113 được (không hạ phiên bản, cùng chữ ký); không gỡ cài, không xóa dữ liệu.

Không gửi APK Ni (thiếu Telegram config). Không cài FYT, không đổi versionCode.
