# Hình nghiệm thu — 2026-11-01-nhieu-playlist-co-ten

**QA CANDIDATE — CHƯA NGHIỆM THU.** Ảnh QA trên Sony XQ-AS52 `QV725WEQ54` với APK Debug `5B2FB5CC…FEF8` (HEAD build `0efec5a`). Chi tiết bảng bước: [phụ lục Chặng 4](2026-11-01-nhieu-playlist-co-ten-bao-cao-chang4.md).

- Thư mục ảnh: [hinh](https://drive.google.com/drive/folders/15EXRhg7w7eBzsgzI5bM0zYqkNzyskJSJ) (trong [thư mục QA candidate](https://drive.google.com/drive/folders/18RmsQArh_0QVdBId3pDjYwR2RuyU9Zvx), owner-only)
- **Ảnh tổng hợp (xem trước):** [00_tong_hop.png](https://drive.google.com/file/d/1jBiqcr6tjfvcdc7NYXX2ZrBY5Y4TSbE2/view) — 8 ô: Q1 chỉ mục, Q2 thêm bài, Q2 dấu ✓, Q2 chi tiết 4 bài, Q3 hộp sắp xếp, Q3 sau mở lại, Q5 phát (toast bài máy), Q4 lưu hàng chờ.

| Bước | Kết quả | Ảnh |
|---|---|---|
| Q1 | PASS — chỉ mục “★ Yêu thích · 2 bài” + “+ Tạo Playlist” | [03_q1_sau_playlist](https://drive.google.com/file/d/1aSIEtSyl9tVVFDj6QLc4z_wNa-3ZRUxZ/view) |
| Q2 | PASS — hộp tạo có bàn phím; thêm 3 bài Telegram + 1 bài máy; ✓ đúng; không phát; chi tiết 4 bài | [06_q2_da_nhap_ten](https://drive.google.com/file/d/1Zdwj_iVj6bCgWnueNm8y8UIZp9ssXLIs/view), [13_q2_hop_co_tick](https://drive.google.com/file/d/1XghetcqWcOHjNVM7YE7F7mRL3cqqYoa8/view), [19_q2_chi_tiet_4_bai](https://drive.google.com/file/d/1Jx7EPA5L8ZYi6zBRV-BuG4rMzMgE9BEf/view) |
| Q3 | PASS — Sắp xếp ↑/↓, Bee Moved lên đầu; giữ thứ tự sau khi tiến trình khởi động lại | [22_q3_sau_sap_xep](https://drive.google.com/file/d/1BjaWLQxoCl-BjTEGfhOieZQC407J8x6R/view), [24_q3_sau_mo_lai](https://drive.google.com/file/d/12FS6Tit-Z2fF8Lj2l4aotuOh80-OvIAt/view) |
| Q4 | PASS — “Đã lưu 3 bài, bỏ qua 0 bài.”; Playlist “QA Hang cho · 3 bài” | [38_q4_da_luu_toast](https://drive.google.com/file/d/179vdh58eLz6G4KSVolpEdjI3uK6xwylB/view) |
| Q5 | PASS (điều chỉnh) — hàng chờ chỉ 3 bài Telegram; thông báo bài máy không nằm trong hàng chờ; Next chuyển đúng bài; Media3 PLAYING; **CHƯA XÁC NHẬN ÂM THANH THỰC** | [31_q5_toast](https://drive.google.com/file/d/1k-4jzM2pYyJwZ990EY1dGymUJ1PAFVU9/view) |
| Q6 | PASS — xóa có hộp xác nhận (bài còn nguyên trong thư viện); đổi tên `QA Thu 2`; xoay ngang giữ chi tiết, xoay dọc lại ổn | [41_q6_hop_xoa](https://drive.google.com/file/d/15hBZyoTtC2OU5Gb-lq76ckaZWYZZ2BMb/view), [48_q6_xoay_ngang](https://drive.google.com/file/d/1lIhHOsorE-3nG01IN7ParG4T2Bdc2tOa/view) |

Bản sao đầy đủ 53 ảnh, log build/gate/USB ở Ni `docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/chang4/` (bị `.gitignore`). Ảnh 01/01a (màn hệ thống do sự cố `monkey`) chủ ý **không** đưa lên Drive.

Chưa kiểm được bằng ảnh: **âm thanh thật**; đồng bộ hai máy (Q7–Q9, BLOCKED); đồng bộ Telegram thật cho Playlist có tên; tên có dấu/emoji trên máy; chữ lớn 1.3; TalkBack; Release/`.open`/Personal trên máy; tablet/Hai khung/Gọn trên máy thật.
