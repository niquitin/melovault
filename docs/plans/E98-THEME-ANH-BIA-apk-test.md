# E98-THEME-ANH-BIA — APK QA Candidate (25/10/2026)

**Trạng thái:** QA CANDIDATE — CHƯA NGHIỆM THU. Bản test, không phải Beta phát hành hay Stable.

- [MỞ APK TEST E98 (vc112) TRÊN GOOGLE DRIVE](https://drive.google.com/file/d/1DzL6ZdUW1YxbyotVyY_ShQWOWAwZIAuk/view)
- [THƯ MỤC APK + ẢNH QA E98](https://drive.google.com/drive/folders/1eKkG5ImPg4hSSDsgdz89HcwRGdABs7nu)

| Trường | Giá trị |
|---|---|
| TASK_ID | E98-THEME-ANH-BIA |
| Build | Debug (flavor shareable), dựng ở Tiny trong clone cô lập `C:\Bots\MeloVault-E93D-IsolatedQA` @ `8654323`, có Telegram config |
| Package | app.melovault.player |
| versionCode / versionName | 112 / 0.6.89-beta |
| Git source | `8654323` (nhánh `dot/E98-THEME-ANH-BIA`) |
| SHA-256 | B228085997E00F9C0F8CEE6E71EFF334A872513EBDD81E1B3B9BD6366E6D7235 |
| Bytes | 33396676 |
| Signer cert SHA-256 | eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8 (Android Debug) |
| Telegram config | true (chứng minh bằng chức năng trên Sony; không đọc/in giá trị) |
| Drive file ID | 1DzL6ZdUW1YxbyotVyY_ShQWOWAwZIAuk |
| Drive folder | `MeloVault_QA/E98-THEME-ANH-BIA-20261025-vc112-8654323` (ID 1eKkG5ImPg4hSSDsgdz89HcwRGdABs7nu) |
| Drive permission | chỉ chủ sở hữu ntdung.md (owner), không có quyền khác — kiểm bằng connector cho file, thư mục và thư mục cha |
| Nguồn trên Tiny | `C:\Bots\_qa_handoff\E98-THEME-ANH-BIA\MeloVault-E98-8654323-vc112-TinySigned-debug.apk` |

**Kiểm tra độc lập Opus:** SHA-256 trùng giữa APK nguồn trên Tiny, bản chép lên Drive (đọc lại sau khi chép) và `base.apk` đang cài trên Sony QV725WEQ54; kích thước trên Drive 33396676 byte. `aapt2 dump badging`: package/vc/vn như trên. `apksigner verify --print-certs`: hợp lệ, cert như trên.

Đã cài: Sony Xperia 5 II (QV725WEQ54, Android 12). Chưa cài: Shanling M3 Ultra (đang vc109 `0.6.88-beta-syncqa`), Y700, S25, FYT. Cài đè `install -r` từ vc ≤ 111 được (không hạ phiên bản, cùng chữ ký debug); không gỡ cài, không xóa dữ liệu.

Không gửi APK Ni (thiếu Telegram config). Không cài FYT, không đổi versionCode.
