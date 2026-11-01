# Nghiệm thu chính thức — 2026-10-31-loi-nhac-p2b (lời nhạc cho bản Shareable, P2-B)

## Kết luận: **PASS — chủ dự án nghiệm thu ĐẠT (01/11/2026)**, kèm giới hạn ở mục 3

| Mục | Giá trị |
|---|---|
| Quyết định | Chủ dự án trả lời **“ĐẠT”** trong chat (01/11/2026), đáp lại câu hỏi “Anh nghiệm thu bản fd1f943 chứ?” ở cuối biên bản sơ bộ vòng 2 |
| Nhiệm vụ / nhánh | `2026-10-31-loi-nhac-p2b` / `dot/2026-10-31-loi-nhac-p2b` (baseline `94ab6b0`) |
| Mã được nghiệm thu | A `6f274b9` (lõi), B `3447310` (UI), REWORK-01 `fd1f943` (sửa B1); hồ sơ sau đó chỉ là tài liệu (đến `a4ef26e`) |
| APK được nghiệm thu | Debug QA `app.melovault.player` vc121 `0.6.96-beta`, SHA-256 `62ED3500C171AAF9CCFCEE3EDDB8D7A11F3D08B65B93DA425A3790126B173DB9`, 33.642.436 byte, cert `eecd3df7…fce8`, build từ `fd1f943`; Drive owner-only ở [`-apk-test.md`](2026-10-31-loi-nhac-p2b-apk-test.md) |
| Phạm vi | Tab “Lời nhạc” trong Studio Deck (dọc/ngang) và trong panel Hàng đợi của Hai khung; nguồn LRCLIB gọi trực tiếp; tắt sẵn + hộp đồng ý lần đầu + công tắc trong Cài đặt; cache theo tài khoản (xóa khi đăng xuất); tìm và chọn lời thủ công; lời đồng bộ tự cuộn và nút “Về dòng đang hát” |
| Nguồn xác nhận | Lời nhắn trong chat của chủ dự án; chứng cứ kỹ thuật: [`-bao-cao.md`](2026-10-31-loi-nhac-p2b-bao-cao.md) mục 9–11, [`-nghiem-thu-so-bo.md`](2026-10-31-loi-nhac-p2b-nghiem-thu-so-bo.md) (vòng 1 CẦN SỬA, vòng 2 KHÔNG PHÁT HIỆN LỖI CHẶN), [`-hinh-nghiem-thu.md`](2026-10-31-loi-nhac-p2b-hinh-nghiem-thu.md) |

## 1. Lịch sử các vòng (giữ nguyên, không sửa kết luận cũ)

| Vòng | Bản | Kết quả |
|---|---|---|
| Chặng C QA | `8d4284e` (APK `31609D80…1E88`) | Q1–Q7 đạt, **Q8 lỗi B1** (xoay màn hình làm pane kẹt “Đang tìm lời…”) |
| Sơ bộ vòng 1 (phiên Opus mới) | `8d4284e` | SƠ BỘ: CẦN SỬA (B1; xác nhận nguyên nhân bằng đọc mã) |
| REWORK-01 | `fd1f943` | Sửa B1; gate Ni 2419 test / 21 skipped / 0 lỗi mỗi biến thể; kiểm lại trên Sony PASS |
| Sơ bộ vòng 2 (cùng phiên đã viết mã, theo yêu cầu của chủ dự án; **không độc lập**) | `fd1f943` | SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN; thêm ca xoay khi request mạng đang bay |
| Nghiệm thu chính thức | `fd1f943` / `62ED3500…3DB9` | **ĐẠT** |

Bản `8d4284e` (có lỗi B1) **đã bị thay thế**, chỉ giữ làm bằng chứng lịch sử.

## 2. Bằng chứng chính

- Gate Ni (REWORK-01): `testShareableDebugUnitTest` + `testShareableReleaseUnitTest` mỗi biến thể 184 suite, 2419 test, 21 skipped, 0 failures, 0 errors (109 test lời nhạc); `assembleShareableDebug/Release`, `-PmeloOpen=true assembleShareableDebug`, `assemblePersonalDebug` đều BUILD SUCCESSFUL; `git diff --check` sạch.
- Tiny: bundle `fd1f943` SHA-256 `4d13ba15…7b73` khớp hai đầu, clone cô lập, `telegram.config.valid=true`, `assembleShareableDebug` BUILD SUCCESSFUL.
- Sony `XQ-AS52` `QV725WEQ54`: Q1–Q7 PASS, Q8 PASS sau sửa (4 lần xoay + ca request mạng đang bay), 0 crash/ANR, log chỉ có `stage`/mã HTTP, không có tên bài/nghệ sĩ.
- Drive: APK và ảnh chỉ quyền owner, kích thước và SHA khớp.

## 3. Giới hạn còn lại (chủ dự án đã chấp nhận khi ĐẠT)

- **CHƯA XÁC NHẬN ÂM THANH THỰC.**
- Lời **tự động** cho bài Telegram có đủ tên + nghệ sĩ khớp LRCLIB chưa kiểm trên máy (tài khoản không có bài như vậy); chỉ kiểm bằng lời chọn thủ công và đường tự động cho bài trên máy (không tìm thấy / nhạc cụ).
- Hầu hết bài Telegram của tài khoản **thiếu nghệ sĩ**, nên theo thiết kế (LD4) chỉ hiện “Thiếu tên bài hoặc nghệ sĩ” và không tự tìm; cần “Tìm lời” thủ công.
- LRCLIB `/api/search` (và đôi khi `/api/get`) thỉnh thoảng trả HTTP 503; app báo “Chưa tải được lời” + “Thử lại”, không treo.
- Ở Hai khung, tab Lời nhạc nằm trong panel “Hàng đợi” (không ở khung trái); ở Deck dọc, thanh tab nằm dưới ảnh bìa lớn nên phải vuốt.
- Chưa kiểm: “quay lại bài còn nhớ lựa chọn” và “Bỏ lời đã chọn” trên máy; chữ lớn 1.3; TalkBack; Release/`.open`/Personal trên máy; hai pane cùng mở trên máy (theo mã chỉ chậm tới 40 giây); lỗi mạng thật/429/hết hạn cache (chỉ test JVM); Y700/S25/FYT.
- Chưa có điều khoản bản quyền lời rõ ràng từ LRCLIB (LD9); tính năng tắt sẵn cho tới khi người dùng đồng ý. Quyết định đưa tính năng vào bản `open` công khai vẫn cần chủ dự án chốt khi chuẩn bị đăng.
- Bản đang cài trên Sony là Debug QA, chưa phải bản phát hành.

## 4. Trạng thái merge / push / public

**Chưa merge, chưa push, chưa public.** ĐẠT không tự cho phép merge (CLAUDE.md mục 0). Chờ chỉ thị riêng của chủ dự án.

Thông tin cho phiên merge khi được giao (không làm bây giờ):
- Nhánh tích hợp `feature/e93-03d-y700-offline-cockpit` hiện ở `f7acb55`; merge-base với nhánh này là `94ab6b0`. Chạy thử khô `git merge-tree --write-tree` cho thấy **merge sạch, không xung đột** (01/11/2026).
- Nên merge `--no-ff` trong worktree tạm; sau merge giống hệt nhánh nguồn thì có thể bỏ qua Gradle nếu `git diff` rỗng, còn không thì chạy lại gate Ni (test Debug/Release + assemble).
- Cập nhật `HANDOFF.md` mục đầu và `docs/plans/BACKLOG.md` (mục A “P2-B lời nhạc” → xong) làm ở bước merge, không làm trên nhánh này để tránh xung đột với bản mới hơn ở nhánh tích hợp.
- Dùng Debug candidate trên Drive để người dùng cài thử; không đăng công khai khi chưa có khóa phát hành và chỉ thị riêng.
