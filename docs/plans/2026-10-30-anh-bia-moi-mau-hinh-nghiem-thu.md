# Hình nghiệm thu — 2026-10-30-anh-bia-moi-mau

Thư mục Drive (owner-only, xác minh qua connector — chỉ owner `ntdung.md@gmail.com`, không public):
https://drive.google.com/drive/folders/1Ex37qmRNMLlK9hjrG2tjutazOdIEr0BC

## Ảnh tổng hợp (xem trước, 8 ô)

https://drive.google.com/file/d/1CZI8Wk-UMAUnnhcs5jl4CpEP7-RzU6Yq/view?usp=drivesdk

1. Q1 — Current dọc: ảnh bìa hiện trên mọi dòng (theme mặc định, trước đây chỉ Teal mới có).
2. Q1 — Current ngang (Hai khung): khung phát trái + thư viện phải, ảnh vẫn hiện.
3. Q2 — Teal dọc: ảnh bìa + card bo góc, không hồi quy.
4. Q2 — Teal ngang (Hai khung).
5. Q3 — Phạm vi "Đã tải": 4 bài khác, ảnh khác nhau khớp đúng bài, không ảnh sai/lặp.
6. Q4 — Công tắc tải ảnh TẮT, cuộn sâu vùng mới: chỉ ô chữ cái, không có request mới (logcat xác nhận 0 dòng `MeloRowArt`).
7. Q4 — Bật lại công tắc, cuộn sâu tiếp: ảnh mới bắt đầu xuất hiện (logcat `sent` tăng 160→212).
8. Q5a — Deck bấm Dừng: nhãn "Tạm dừng" hiện đúng lúc (hồi quy D2/D3, không đổi bởi nhiệm vụ này).

Chỉ mở ảnh gốc bên dưới khi cần xem rõ hơn ảnh tổng hợp.

## Ảnh gốc (từng file trong thư mục Drive trên)

| File | Nội dung |
|---|---|
| `qa1-01-current-port.png` | Current dọc, mở nguội |
| `qa1-02-current-land.png` | Current ngang (Hai khung) |
| `qa2-01-teal-port.png` | Teal dọc |
| `qa2-02-teal-land.png` | Teal ngang (Hai khung) |
| `qa3-01-fast-scroll-200.png` | Sau cuộn nhanh ~200 dòng, vẫn theme Current |
| `qa3b-02-datai-scope.png` | Phạm vi "Đã tải" (68 bài), ảnh khớp đúng bài |
| `qa3b-03-search-nhac.png` | Tìm "nhạc", kết quả lọc đúng |
| `qa4e-01-off-deep.png` | Công tắc tải ảnh TẮT, vùng mới chỉ ô chữ cái |
| `qa4e-02-resumed-deep.png` | Công tắc bật lại, ảnh mới xuất hiện |
| `qa5a-01-deck-paused-by-button.png` | Deck: nhãn "Tạm dừng" sau khi bấm Dừng |
| `qa5a2-02-next-3s.png` | Deck: sau Bài kế +3s, hết nhãn, đang phát |

## Giới hạn

- Không có ảnh/video cho Q5b (Đảo + thứ tự hàng chờ) — màn Deck dọc trên Sony không hiện danh sách hàng chờ dạng số trong phiên này để chụp; xem báo cáo thực thi mục 6.
- Âm thanh thật chưa xác nhận (chỉ có bằng chứng Media3 PLAYING + logcat/ảnh UI).
- Tablet, Shanling ngoài phạm vi (quyết định chủ dự án 30/10 17:01).
