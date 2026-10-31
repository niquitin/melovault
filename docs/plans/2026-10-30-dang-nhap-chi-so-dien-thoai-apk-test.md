# APK test — bản công khai đăng nhập chỉ bằng số điện thoại

TASK_ID: `2026-10-30-dang-nhap-chi-so-dien-thoai`. Trạng thái: **QA CANDIDATE — CHƯA NGHIỆM THU.**
Nguồn: Git HEAD `5805df2` (nhánh `dot/2026-10-30-dang-nhap-chi-so-dien-thoai`), mã build từ commit `a2f0a88`.
Build trên Tiny, clone cô lập `C:\Bots\MeloVault-dang-nhap-IsolatedQA-20261030-a2f0a88`. Chữ ký debug cả 3 APK: `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (khớp các bản Beta Tiny trước đó).

**Thư mục Drive (owner-only, đã xác nhận `permissions` chỉ có `ntdung.md@gmail.com` role `owner`):**
[2026-10-30-dang-nhap-chi-so-dien-thoai-20261031-vc121-a2f0a88](https://drive.google.com/drive/folders/1PqAqNBewgGKHEg5N0OpWzjZVfsw2RK2b)

| APK | Package | vc / versionName | Bytes | SHA-256 | Telegram config | Link |
|---|---|---|---:|---|---|---|
| Thường Debug | `app.melovault.player` | 121 / `0.6.96-beta` | 33.593.284 | `6e3d6b6e98de9974d399eea850ec691df1ab2324172ec3990fba483e6869a029` | `telegram.config.valid=true` (mã riêng, không đổi) | [app-shareable-debug-THUONG-vc121-0.6.96-beta.apk](https://drive.google.com/file/d/1G4X0OBXqwcISVGwOoLQtC1ejuq4uPYlB/view) |
| Open Debug | `app.melovault.player.open` | 121 / `0.6.96-open` | 33.593.288 | `a241c32363b40bc77e3d7d1509b0e80d2483e262eb1fa2a151e0e0d39c26b0d9` | `telegram.open.config.valid=true`, `telegram.open.distinct=true` | [app-shareable-debug-OPEN-vc121-0.6.96-open.apk](https://drive.google.com/file/d/1Tn0t8-2ubI55cofpjNO1qJEpdczq4RPU/view) |
| Open Release | `app.melovault.player.open` | 121 / `0.6.96-open` | 30.785.910 | `0ca06484ec53a2f4118c22e17e1936790b52dcb984d792f7911133f17cdfa705` | `telegram.open.config.valid=true`, `telegram.open.distinct=true` | [app-shareable-release-OPEN-vc121-0.6.96-open.apk](https://drive.google.com/file/d/1DlzRyqLdU2eFVb-O-QGd2hGUGxs1mIha/view) |

[SHA256.txt](https://drive.google.com/file/d/1erbJxkrnLryohCqGnlQrxRp2FmRixPlz/view) (bản sao nội bộ: `docs/plans/artifacts/2026-10-30-dang-nhap-chi-so-dien-thoai/SHA256.txt`).

## Kết quả verify (S6.7, trên Tiny)

- `verifyOpenApkHasNoTelegramHash`: Open Debug/Release đều `found=false` (không chứa hash bản riêng); đối chứng chạy trên APK thường cho `found=true` (xác nhận cơ chế dò hoạt động đúng, không sai âm tính).
- `verifyOpenApkHasOpenTelegramHash`: Open Debug/Release đều `found=true` (có đúng hash open nhúng).
- `aapt2 dump badging`: package/versionCode/versionName đúng cho cả 3 APK (xem bảng trên).
- `apksigner verify --print-certs`: cả 3 APK cùng chữ ký `eecd3df7…fce8`.

## QA thiết bị đã làm (S7, Sony `QV725WEQ54`)

- **Q5 (hồi quy bản thường): PASS.** Cài đè `-r` (vc không đổi, cert khớp, không phát nhạc trước khi cài) → vẫn đăng nhập, vào thẳng thư viện 25.470 bài.
- **Q1 (bản open, lần mở đầu): PASS.** Vào thẳng "Nhập số điện thoại", không panel API.
- **Q2 (link nâng cao + quay lại): PASS.** Panel E104 hiện đúng; "Quay lại" về màn số, không lưu, không gõ API ID/hash.
- **Q3–Q4: CHƯA LÀM.** Cần chính chủ dự án tự nhập số điện thoại + mã (+2FA) — agent không được gõ các giá trị này. App open đang để sẵn ở màn nhập số trên Sony (màn hình đã tắt).
- **Q6 (JVM), Shanling: CHƯA LÀM** trong phiên này (chủ dự án yêu cầu bỏ qua Shanling, chỉ làm Sony).
- Chi tiết từng bước, lệnh, ảnh: [`-bao-cao.md`](2026-10-30-dang-nhap-chi-so-dien-thoai-bao-cao.md) mục "S7 — QA thiết bị Sony"; ảnh ở [`-hinh-nghiem-thu.md`](2026-10-30-dang-nhap-chi-so-dien-thoai-hinh-nghiem-thu.md).

## Giới hạn/chưa kiểm chứng

- Âm thanh thật, luồng đăng nhập thật hoàn chỉnh (Q3–Q4), Release UI trên máy, Y700/S25/FYT, Shanling — chưa thử trong phiên này.
- Mã open thật bị Telegram từ chối (D7) — chưa thử.
- Không có giá trị `api_id`/`api_hash` nào trong file này hay bất kỳ hồ sơ nào khác trong repo/Drive.

**Trạng thái nghiệm thu: CHỜ CHỦ DỰ ÁN NGHIỆM THU.** Không merge/push/public.
