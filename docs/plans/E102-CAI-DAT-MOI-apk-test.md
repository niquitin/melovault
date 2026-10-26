# E102-CAI-DAT-MOI — APK QA Candidate

## Bản hiện tại: vc118 (26/10/2026)

**Trạng thái:** **ĐÃ NGHIỆM THU — PASS** (chủ dự án "ĐẠT" 26/10/2026, xem `E102-CAI-DAT-MOI-nghiem-thu.md` mục 12). Bản test nội bộ; giới hạn: đổi Cũ/Mới và Bento khi quay lại thư viện chỉ kiểm bằng mã; Y700/S25/FYT/Car Mode/Tablet chưa kiểm; âm thanh không liên quan.

- [MỞ APK TEST E102 (vc118) TRÊN GOOGLE DRIVE](https://drive.google.com/file/d/1j_sX4daXfznKWdYCFBvhNlXHP7iWvJ0M/view)
- [THƯ MỤC APK + ẢNH QA vc118](https://drive.google.com/drive/folders/1l1vi3uG7dqghS0OD0ghbvfun7SV4uxc3)

| Trường | Giá trị |
|---|---|
| Build | Debug (flavor shareable), dựng ở Tiny trong clone cô lập `C:\Bots\MeloVault-E102-IsolatedQA` (Git bundle SHA-256 `CDCFA133…1964F83C`, HEAD `2f4d736`, sạch), có Telegram config |
| Package | app.melovault.player |
| versionCode / versionName | 118 / 0.6.93-beta |
| Git source | `2f4d736` (nhánh `dot/E102-CAI-DAT-MOI`) |
| SHA-256 | 0F1DB45A23D82E20CD1F0124CCAC33FD7B7BB21218D6BB16737DE78EE143D414 |
| Bytes | 33445828 |
| Signer cert SHA-256 | eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8 (Android Debug, như các bản trước) |
| Telegram config | true (chỉ boolean, theo log build Tiny của Sonnet; vc118 trên Shanling hiện thư viện Telegram bình thường) |
| Drive file ID | 1j_sX4daXfznKWdYCFBvhNlXHP7iWvJ0M |
| Drive folder | `MeloVault_QA/E102-CAI-DAT-MOI-20261026-vc118-2f4d736` (ID 1l1vi3uG7dqghS0OD0ghbvfun7SV4uxc3), thư mục mới, không ghi đè bản cũ |
| Drive permission | chỉ owner ntdung.md — kiểm bằng connector cho APK, ảnh tổng hợp, thư mục và thư mục cha `MeloVault_QA` |
| Nguồn | Tiny `C:\Bots\_qa_handoff\E102-CAI-DAT-MOI\`, Ni `D:\Bots\_transfer\E102\` |

**Kiểm tra độc lập Opus:** SHA-256 trùng giữa bản Ni, bản chép lên Drive (`SHA256.txt`, kích thước Drive 33.445.828) và `base.apk` đang cài trên Shanling (`sha256sum` trên máy); `aapt2 dump badging` + `apksigner verify --print-certs` như bảng.

Đã cài (Sonnet, `install -r`): Shanling M3 Ultra (30313860180939, vc117 → vc118), Sony Xperia 5 II (QV725WEQ54, vc117 → vc118). Cài đè từ vc ≤ 117 được (cùng chữ ký, không hạ phiên bản).
