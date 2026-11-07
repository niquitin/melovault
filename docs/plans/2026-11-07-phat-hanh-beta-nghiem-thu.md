# Nghiệm thu — Phát hành bản beta cho bạn bè

**PASS — chủ dự án xác nhận ĐẠT** (07/11/2026, trong chat Claude Code: "đạt").

| Mục | Giá trị |
|---|---|
| TASK_ID / nhánh | `2026-11-07-phat-hanh-beta` / `dot/2026-11-07-phat-hanh-beta` |
| Source APK | `a0694c05b8df22ef90a01cdd52738528747341b7` (commit sau đó chỉ là tài liệu) |
| APK | `app.melovault.player.open` vc122 `0.6.97-open`, Release, 31.051.460 B, SHA-256 `54AB04C8A0F4418B379EA2DA19FFE281283D84E4A46B5DE90BE7DD8FDFC42CCF`, cert `eecd3df7…fce8` (khóa debug tạm của Tiny) |
| Sơ bộ | [Vòng 1–2](2026-11-07-phat-hanh-beta-nghiem-thu-so-bo.md): KHÔNG PHÁT HIỆN LỖI CHẶN; Q4–Q7 trên Sony BLOCKED (chưa đăng nhập) |
| Cách chủ dự án thử | Tự thử Q4–Q7 trên điện thoại riêng bằng APK trên Drive, rồi nhắn ĐẠT. Chi tiết từng bước chủ dự án không ghi lại |

**Phạm vi ĐẠT:** tên MeloVault, nhãn và dòng không chính thức, ẩn QR và công cụ nhà phát triển, Giới thiệu và giấy phép, quy tắc sao lưu Shareable, APK trên. Chủ dự án chấp nhận giữ nguyên các mục còn treo: hai nút mã API trên màn đăng nhập, nhãn "Thử nghiệm" ở Đồng bộ, ghi nhận nhỏ G1–G7 của sơ bộ.

**Giới hạn còn lại:** agent không có bằng chứng Q4–Q7 hay âm thanh thật; sao lưu chưa thử `bmgr`/chuyển máy; cài mới từ đầu trên máy khác chưa được agent kiểm; rủi ro điều khoản tin tài trợ Telegram chưa chốt (kế hoạch mục 6); khóa ký tạm, đổi khóa sau này bạn bè phải gỡ app.

**Bước tiếp (kế hoạch mục 4):** merge vào nhánh tích hợp và push; Release nháp `v0.6.97-open-beta` kèm APK và `.sha256`; nhánh trang `site/beta-0.6.97` và PR vào `main` (không merge). Chủ dự án tự bấm Publish rồi Merge.
