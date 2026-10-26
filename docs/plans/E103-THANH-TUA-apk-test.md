# E103-THANH-TUA — APK QA Candidate

## Bản hiện tại: vc119 `0a9beda` — REWORK vòng 1 (26/10/2026)

**Trạng thái:** **ĐÃ NGHIỆM THU — PASS** (chủ dự án "ĐẠT" 26/10/2026, xem `E103-THANH-TUA-nghiem-thu.md` mục 12). Bản test nội bộ, F1 đã sửa (thanh mảnh, chấm tròn rõ). Âm thanh thật chưa xác nhận; Teal/FYT/Y700/S25 chưa kiểm.

- [MỞ APK TEST E103 (vc119, 0a9beda) TRÊN GOOGLE DRIVE](https://drive.google.com/file/d/14t6QbFxB_G3DfmzPct8-ytbx7pZKDtFz/view)
- [THƯ MỤC APK + ẢNH QA vc119 0a9beda](https://drive.google.com/drive/folders/1XiaVUvVMou8mMTboD2XWCRc1e1t819En)

| Trường | Giá trị |
|---|---|
| Build | Debug (flavor shareable), dựng ở Tiny trong clone cô lập `C:\Bots\MeloVault-E103-IsolatedQA2` (Git bundle SHA-256 `0237FFD0…49677FBB`, HEAD `0a9beda`, sạch), có Telegram config |
| Package | app.melovault.player |
| versionCode / versionName | 119 / 0.6.94-beta (giữ nguyên, cài `install -r` cùng vc) |
| Git source | `0a9beda` (nhánh `dot/E103-THANH-TUA`) |
| SHA-256 | 15A596FFF75B7D69810AA8C97CB623AB1A1F53E4133DD7F1324DF99B2EEC5425 |
| Bytes | 34334950 |
| Signer cert SHA-256 | eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8 |
| Telegram config | true (boolean từ log build Tiny; hai máy chạy bài Telegram bình thường sau cài) |
| Drive file ID | 14t6QbFxB_G3DfmzPct8-ytbx7pZKDtFz |
| Drive folder | `MeloVault_QA/E103-THANH-TUA-20261026-vc119-0a9beda` (ID 1XiaVUvVMou8mMTboD2XWCRc1e1t819En), thư mục mới, không ghi đè |
| Drive permission | chỉ owner ntdung.md — kiểm bằng connector cho APK và thư mục (thư mục cha `MeloVault_QA` đã kiểm owner-only) |
| Nguồn | Tiny `C:\Bots\_qa_handoff\E103-THANH-TUA\`, Ni `D:\Bots\_transfer\E103\` |

**Kiểm tra độc lập Opus:** SHA-256 trùng giữa bản Ni, bản chép lên Drive (`SHA256.txt`, kích thước Drive 34.334.950) và `base.apk` đang cài trên Shanling (`sha256sum` trên máy); `aapt2 dump badging` + `apksigner verify --print-certs` như bảng. Sony và Shanling đều đã `install -r` bản này (Sonnet).

## Bản cũ: vc119 `3d576bb` (26/10/2026) — REWORK F1, không dùng để duyệt

**Trạng thái:** thay bằng `0a9beda` (thanh dày, chấm khó thấy).

- [APK E103 (vc119, 3d576bb) TRÊN GOOGLE DRIVE](https://drive.google.com/file/d/1SVXCseL2oRQoKewCkFHcFrfjIFE2gHiY/view)
- [THƯ MỤC APK + ẢNH QA vc119](https://drive.google.com/drive/folders/1aeCnnh9bouU5iO6RpNQcW2o_JbVywifN)

| Trường | Giá trị |
|---|---|
| Build | Debug (flavor shareable), dựng ở Tiny trong clone cô lập `C:\Bots\MeloVault-E103-IsolatedQA2` (Git bundle SHA-256 `C869D6A4…D397B733`, HEAD `3d576bb`, sạch), có Telegram config |
| Package | app.melovault.player |
| versionCode / versionName | 119 / 0.6.94-beta |
| Git source | `3d576bb` (nhánh `dot/E103-THANH-TUA`) |
| SHA-256 | DF7EA87ACE5925176DACD22DF3958E557AAA56F2E0EEF61FBB81B113EA7215EC |
| Bytes | 33445828 |
| Signer cert SHA-256 | eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8 (Android Debug, như các bản trước) |
| Telegram config | true (chỉ boolean, theo log build Tiny của Sonnet; hai máy chạy bài Telegram bình thường sau cài) |
| Drive file ID | 1SVXCseL2oRQoKewCkFHcFrfjIFE2gHiY |
| Drive folder | `MeloVault_QA/E103-THANH-TUA-20261026-vc119-3d576bb` (ID 1aeCnnh9bouU5iO6RpNQcW2o_JbVywifN), thư mục mới, không ghi đè bản cũ |
| Drive permission | chỉ owner ntdung.md — kiểm bằng connector cho APK, thư mục và thư mục cha `MeloVault_QA` |
| Nguồn | Tiny `C:\Bots\_qa_handoff\E103-THANH-TUA\`, Ni `D:\Bots\_transfer\E103\` |

**Kiểm tra độc lập Opus:** SHA-256 trùng giữa bản Ni, bản chép lên Drive (`SHA256.txt`, kích thước Drive 33.445.828) và `base.apk` đang cài trên Shanling (`sha256sum` trên máy); `aapt2 dump badging` + `apksigner verify --print-certs` như bảng.

Đã cài (Sonnet, `install -r`): Shanling M3 Ultra (30313860180939, vc118 → vc119), Sony Xperia 5 II (QV725WEQ54, vc118 → vc119). Cài đè từ vc ≤ 118 được (cùng chữ ký, không hạ phiên bản).

**Không dùng** APK `fc98e0e` (SHA `692C983E…D3656CB1B`): lỗi thả tay tua về chỗ cũ.
