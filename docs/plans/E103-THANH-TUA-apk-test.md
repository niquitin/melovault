# E103-THANH-TUA — APK QA Candidate

## Bản hiện tại: vc119 (26/10/2026)

**Trạng thái:** **QA CANDIDATE — CHƯA NGHIỆM THU** (BLOCKED — CHỜ CHỦ DỰ ÁN, xem `E103-THANH-TUA-nghiem-thu.md`). Chức năng tua đã QA trên Sony + Shanling; còn điểm F1 (thanh dày, chấm khó thấy) chờ chủ dự án chốt. Âm thanh thật chưa xác nhận; Teal/FYT/Y700/S25 chưa kiểm.

- [MỞ APK TEST E103 (vc119) TRÊN GOOGLE DRIVE](https://drive.google.com/file/d/1SVXCseL2oRQoKewCkFHcFrfjIFE2gHiY/view)
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
