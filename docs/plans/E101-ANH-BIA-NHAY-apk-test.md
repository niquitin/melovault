# E101-ANH-BIA-NHAY — APK QA Candidate

## Bản hiện tại: vc117 (26/10/2026)

**Trạng thái:** **QA CANDIDATE — CHƯA NGHIỆM THU** (chờ chủ dự án xem ảnh/video và nói ĐẠT, xem `E101-ANH-BIA-NHAY-nghiem-thu.md`). Bản test nội bộ; giới hạn: Sony đường `MeloLibraryPane` (Teal) chưa kiểm vì Sony không ở Teal; nhánh ảnh mờ hoãn 400 ms chưa thấy chạy trên máy; Tablet/Hai khung/Y700/S25/FYT chưa kiểm.

- [MỞ APK TEST E101 (vc117) TRÊN GOOGLE DRIVE](https://drive.google.com/file/d/1743HVhmXFc9jvEECQPYCcSVDSn1PMV2s/view)
- [THƯ MỤC APK + ẢNH/VIDEO QA vc117](https://drive.google.com/drive/folders/1Y2xEzSYnj9cxzMyfP-BHDvp0pYJZ2Xra)

| Trường | Giá trị |
|---|---|
| Build | Debug (flavor shareable), dựng ở Tiny trong clone cô lập `C:\Bots\MeloVault-E101-IsolatedQA` (Git bundle SHA-256 `ED760E37…55CDE3B9`, HEAD `00c4901`, sạch), có Telegram config |
| Package | app.melovault.player |
| versionCode / versionName | 117 / 0.6.92-beta |
| Git source | `00c4901` (nhánh `dot/E101-ANH-BIA-NHAY`) |
| SHA-256 | 0EBA0E683D64F44085F2FD63187846BADE320264FD4B04E54626672D3A6537CF |
| Bytes | 33413060 |
| Signer cert SHA-256 | eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8 (Android Debug, như các bản trước) |
| Telegram config | true (chỉ boolean, theo log build Tiny của Sonnet; app vc117 trên Shanling hiện danh sách Telegram bình thường) |
| Drive file ID | 1743HVhmXFc9jvEECQPYCcSVDSn1PMV2s |
| Drive folder | `MeloVault_QA/E101-ANH-BIA-NHAY-20261026-vc117-00c4901` (ID 1Y2xEzSYnj9cxzMyfP-BHDvp0pYJZ2Xra), thư mục mới, không ghi đè bản cũ |
| Drive permission | chỉ owner ntdung.md — kiểm bằng connector cho APK, video, thư mục và thư mục cha `MeloVault_QA` |
| Nguồn | Tiny `C:\Bots\_qa_handoff\E101-ANH-BIA-NHAY\`, Ni `D:\Bots\_transfer\E101\` |

**Kiểm tra độc lập Opus:** SHA-256 trùng giữa bản Ni, bản chép lên Drive (`SHA256.txt`, kích thước Drive 33.413.060) và `base.apk` đang cài trên Shanling (`sha256sum` trên máy); `aapt2 dump badging` + `apksigner verify --print-certs` như bảng.

Đã cài (Sonnet, `install -r`): Shanling M3 Ultra (30313860180939, vc116 → vc117), Sony Xperia 5 II (QV725WEQ54, vc116 → vc117). Cài đè từ vc ≤ 116 được (cùng chữ ký, không hạ phiên bản).
