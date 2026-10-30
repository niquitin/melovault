# Nghiệm thu — 2026-10-30-dao-thu-tu-phat

**Trạng thái: CHỜ CHỦ DỰ ÁN NGHIỆM THU** (QA CANDIDATE — CHƯA NGHIỆM THU). Chưa ghi PASS; chỉ ghi PASS sau khi chủ dự án xác nhận ĐẠT đúng TASK_ID, commit/APK và phạm vi.

| Mục | Giá trị |
|---|---|
| Nhánh / HEAD | `dot/2026-10-30-dao-thu-tu-phat` / `3296b05` (source `798b254`, baseline `c6c7970`) |
| APK | Debug vc121 `0.6.96-beta`, SHA-256 `1D61F67F…D485`, 33.576.900 byte — [apk-test](2026-10-30-dao-thu-tu-phat-apk-test.md) |
| Hình | [hinh-nghiem-thu](2026-10-30-dao-thu-tu-phat-hinh-nghiem-thu.md) |
| Báo cáo | [bao-cao](2026-10-30-dao-thu-tu-phat-bao-cao.md) mục 8 |

## Kết quả kỹ thuật (Sony XQ-AS52, QA từ xa qua ADB)

| Q | Kết quả | Bằng chứng |
|---|---|---|
| Q-1 | PASS | Đảo bật: bài đang phát ở hàng 1, phần sau xáo; nhãn "Thứ tự phát · Đảo bật" |
| Q-2 | PASS | ⏭ ×3 phát đúng hàng 2, 3, 4 |
| Q-3 | PASS | Chạm hàng 6 phát hàng 6; ⏭ → hàng 7; hàng chờ không đổi |
| Q-4 | PASS | "Phát tiếp theo" chèn ngay sau bài hiện tại; ⏭ phát đúng bài đó |
| Q-5 | PASS | Tắt Đảo: về thứ tự gốc, bài giữ nguyên; ⏭ phát bài kế theo gốc |
| Q-6 | PASS có giới hạn | Màn OFF: phím media NEXT ×2 ra hàng 2, 3; hết bài tự nhiên (seek gần cuối, màn OFF) ra đúng hàng 2. Chưa bấm nút thật trên thông báo/khóa màn |
| Q-7 | PASS có giới hạn | Nhạc trên máy (3 bài): xáo khác thứ tự gốc, ⏭ ×2 khớp; tắt Đảo về gốc, không ngắt bài. File `ringtone` (0,0 MB) không phát được nên không dùng cho kiểm tắt Đảo; `ms=1–3` chỉ trên 3 bài |
| Q-8 | PASS | 20 lần bật/tắt Đảo (10 nhanh + 10 cách 1,5 giây): cùng tiến trình, nhạc tiếp tục, không FATAL/ANR |
| Điều kiện BLOCKED | Không xảy ra | Q-2 không lệch; log `order_reset` = 0 |

Tổng log `MeloShuffle`: toggle 25, configure 7, next_inserted 1, local_reorder 5, order_reset 0.

## Chưa kiểm chứng / giới hạn

- **CHƯA XÁC NHẬN ÂM THANH THỰC** (Media3 PLAYING ≠ loa/tai nghe).
- Shanling: ADB Ni không thấy thiết bị → KHÔNG THAO TÁC. Sony không thay chứng cứ Shanling.
- R-1 (danh sách > 2000 bài): chưa kiểm trên máy; danh sách thử 67 và 200 bài.
- Nhạc trên máy 500 bài (đo giật): chưa.
- Ghi nhận hành vi (không phải lỗi chặn): khi Đảo đã bật sẵn và chọn bài từ danh sách thư viện cùng nguồn, thứ tự xáo cũ được giữ, bài vừa chọn nằm giữa hàng (hạng 50); tắt/bật Đảo xáo lại thì bài đang phát đứng đầu. Chủ dự án quyết định có muốn "chọn từ thư viện = xáo lại" hay không.
- Drive: APK/ảnh ở tài khoản connector kiểm được (`ntdung.md@gmail.com`), có thể khác "Drive Ni N"; chưa thử tải bằng trình duyệt.

## Thao tác chủ dự án thử (3–6 bước, Sony hoặc Shanling)

1. Thư viện → nguồn Telegram → chạm một bài → mở Đang phát → **⇄ Đảo** TẮT rồi BẬT: khung ghi "Thứ tự phát · Đảo bật", bài đang nghe ở hàng 1.
2. Bấm ⏭ vài lần: bài phát đúng hàng 2, 3, 4… của khung.
3. Chạm bài giữa khung: phát bài đó, khung không xáo lại.
4. Menu ⋮ một bài → "Phát tiếp theo": bài hiện ngay sau bài đang nghe.
5. Tắt Đảo: khung về thứ tự gốc, nhạc không ngắt.
6. (Nên) bật Đảo, tắt màn, bấm ⏭ từ tai nghe/khóa màn: ra đúng hàng 2.

## Quyết định chủ dự án

- (chưa có) Phản hồi: `ĐẠT 2026-10-30-dao-thu-tu-phat — 3296b05 / APK 1D61F67F…D485` hoặc `Yêu cầu sửa 2026-10-30-dao-thu-tu-phat: <lỗi>`.
- ĐẠT không tự cấp quyền merge/push/public.
