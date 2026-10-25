# E98-THEME-ANH-BIA — APK QA Candidate

## Bản mới nhất: REWORK-01, vc113 (25/10/2026)

**Trạng thái:** ĐÃ NGHIỆM THU — PASS (chủ dự án "ĐẠT" 25/10/2026, xem `E98-THEME-ANH-BIA-nghiem-thu.md` mục 12). Bản test nội bộ, không phải Stable, không phát hành công khai; giới hạn: Y700/S25/FYT, âm thanh thật chưa xác nhận.

- [MỞ APK TEST E98 REWORK-01 (vc113) TRÊN GOOGLE DRIVE](https://drive.google.com/file/d/1PMwmQfhKnampHqOUnROc6c_SW8PAwMZe/view)
- [THƯ MỤC APK + ẢNH QA vc113](https://drive.google.com/drive/folders/1Seuc8lMGKj47Kv3-CNAhvWJVV8ELY0KS)

| Trường | Giá trị |
|---|---|
| Build | Debug (flavor shareable), dựng ở Tiny trong clone cô lập `C:\Bots\MeloVault-E93D-IsolatedQA` (`8654323` + patch; 7 blob mã/test khớp `48449ab`), có Telegram config |
| Package | app.melovault.player |
| versionCode / versionName | 113 / 0.6.89.1-beta |
| Git source | `48449ab` (nhánh `dot/E98-THEME-ANH-BIA`) |
| SHA-256 | 200E6EBC501FED01954B78E8BAEA575D24811E3A8B7AD813B9C3905A450210F6 |
| Bytes | 33413064 |
| Signer cert SHA-256 | eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8 (Android Debug) |
| Telegram config | true (chỉ boolean, không đọc/in giá trị) |
| Drive file ID | 1PMwmQfhKnampHqOUnROc6c_SW8PAwMZe |
| Drive folder | `MeloVault_QA/E98-THEME-ANH-BIA-20261025-vc113-48449ab` (ID 1Seuc8lMGKj47Kv3-CNAhvWJVV8ELY0KS) |
| Drive permission | chỉ owner ntdung.md — kiểm bằng connector cho APK, thư mục, thư mục cha và ảnh |
| Nguồn trên Tiny | `C:\Bots\_qa_handoff\E98-THEME-ANH-BIA-REWORK01\MeloVault-E98-REWORK01-48449ab-vc113-TinySigned-debug.apk` |

**Kiểm tra độc lập Opus:** SHA-256 trùng giữa APK trên Tiny, bản chép về Ni và bản trên Drive (đọc lại sau khi chép); `aapt2 dump badging` và `apksigner verify --print-certs` như bảng.

Đã cài: Sony Xperia 5 II (QV725WEQ54, Sonnet, vc112 → vc113), **Shanling M3 Ultra (30313860180939, Opus, vc112 → vc113, `install -r`)**. Chưa cài: Y700, S25, FYT. Cài đè `install -r` từ vc ≤ 112 được (không hạ phiên bản, cùng chữ ký debug); không gỡ cài, không xóa dữ liệu.

## Bản cũ: vòng 1, vc112 (đã thay bằng vc113)

- [APK vc112](https://drive.google.com/file/d/1DzL6ZdUW1YxbyotVyY_ShQWOWAwZIAuk/view) · [thư mục vc112](https://drive.google.com/drive/folders/1eKkG5ImPg4hSSDsgdz89HcwRGdABs7nu)
- vc112 / `0.6.89-beta` @ `8654323`, SHA-256 `B228085997E00F9C0F8CEE6E71EFF334A872513EBDD81E1B3B9BD6366E6D7235`, 33.396.676 byte, cùng cert. Giữ nguyên trên Drive, không ghi đè.

Không gửi APK Ni (thiếu Telegram config). Không cài FYT, không đổi versionCode.
