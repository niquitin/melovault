# E100-THANH-DAY-CHUA-PHAT — APK QA Candidate

## Bản hiện tại: vc116 (26/10/2026)

**Trạng thái:** **QA CANDIDATE — CHƯA NGHIỆM THU** (chờ chủ dự án xem ảnh / dùng thử và nói ĐẠT, xem `E100-THANH-DAY-CHUA-PHAT-nghiem-thu.md`). Bản test nội bộ; giới hạn: âm thanh thật chưa xác nhận, xóa khi đăng xuất chỉ kiểm bằng mã + unit test, nhánh "bài chưa tải phát từ đầu" chưa kiểm trên máy, Tablet/Hai khung/Y700/S25/FYT/Car chưa kiểm.

- [MỞ APK TEST E100 (vc116) TRÊN GOOGLE DRIVE](https://drive.google.com/file/d/14LGhgoPnFf96ZYhsR7k3HMpYdFQrYms0/view)
- [THƯ MỤC APK + ẢNH QA vc116](https://drive.google.com/drive/folders/1LBH9TFftaesZSuf3osufbDYzEjyllX9Z)

| Trường | Giá trị |
|---|---|
| Build | Debug (flavor shareable), dựng ở Tiny trong clone cô lập `C:\Bots\MeloVault-E100-IsolatedQA` (Git bundle SHA-256 `43136DC4…6A5049`, HEAD `8295598`, sạch), có Telegram config |
| Package | app.melovault.player |
| versionCode / versionName | 116 / 0.6.91-beta |
| Git source | `8295598` (nhánh `dot/E100-THANH-DAY-CHUA-PHAT`) |
| SHA-256 | F5E358CE89F627DCA9BC57F08861556A4C99CF1DEFB516985CAC3540FEA19515 |
| Bytes | 33413060 |
| Signer cert SHA-256 | eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8 (Android Debug, như các bản trước) |
| Telegram config | true (chỉ boolean; dòng `telegram.config.valid=true` trong log build Tiny `C:\Bots\_qa_temp\e100_build.log`, Opus đọc lại; bản sao `local.properties` đã xóa khỏi clone sau build) |
| Drive file ID | 14LGhgoPnFf96ZYhsR7k3HMpYdFQrYms0 |
| Drive folder | `MeloVault_QA/E100-THANH-DAY-CHUA-PHAT-20261026-vc116-8295598` (ID 1LBH9TFftaesZSuf3osufbDYzEjyllX9Z), thư mục mới, không ghi đè bản cũ |
| Drive permission | chỉ owner ntdung.md — kiểm bằng connector cho APK, thư mục và thư mục cha `MeloVault_QA` |
| Nguồn | Tiny `C:\Bots\_qa_handoff\E100-THANH-DAY-CHUA-PHAT\`, Ni `D:\Bots\_transfer\E100\` |

**Kiểm tra độc lập Opus:** SHA-256 trùng giữa APK trên Tiny, bản Ni, bản chép lên Drive (`SHA256.txt`, kích thước Drive 33.413.060) và `base.apk` đang cài trên Shanling (`sha256sum` trên máy); `aapt2 dump badging` + `apksigner verify --print-certs` như bảng.

Đã cài (Sonnet, `install -r`): Shanling M3 Ultra (30313860180939, vc115 → vc116), Sony Xperia 5 II (QV725WEQ54, vc115 → vc116). Cài đè từ vc ≤ 115 được (cùng chữ ký, không hạ phiên bản).
