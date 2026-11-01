# Hình nghiệm thu — 2026-10-31-loi-nhac-p2b

**QA CANDIDATE — CHƯA NGHIỆM THU.** Ảnh chụp trên Sony XQ-AS52 `QV725WEQ54` (Android 12, 1080×2520, density 420), APK `31609D80…1E88` (HEAD `8d4284e`). Ảnh có tên bài và kênh riêng tư: chỉ ở Drive owner-only (`ntdung.md@gmail.com`), không commit ảnh vào repo.

- Thư mục ảnh: [hinh](https://drive.google.com/drive/folders/1RWZMcFt_n_aGKgWT4l59Q53YOw1x4weF) (trong [thư mục QA candidate](https://drive.google.com/drive/folders/1MF7ltkP3LgMcwckr0LOYeDGg9sJAJ3ST))
- **Ảnh tổng hợp (xem trước):** [00_tong_hop.png](https://drive.google.com/file/d/1uqxSqr6Y8jSRB4vBH62nYHfjBqYx-2Uw/view) — 8 ô: Q1 hộp đồng ý, Q1 chọn Không, Q5 danh sách, Q2 câu tô đổi, Q3 tua +60s, Q8 xoay ngang kẹt (lỗi), Q6 Hai khung, Q7 công tắc.

| Bước | Kết quả | Ảnh |
|---|---|---|
| Q1 | PASS — hộp đồng ý, rồi chọn Không → tắt, không request | [q1-consent](https://drive.google.com/file/d/1wpb3GQnGpRyxnY3AbjWA7IuUvZ_2Gi5c/view), [q1-declined](https://drive.google.com/file/d/1CeyN4xO7ib4w4qXeta0fkjfvcno1W5NH/view) |
| Q2 | PASS có giới hạn — câu tô đổi sau ~11 giây (lời chọn thủ công) | [q2-a-before](https://drive.google.com/file/d/1A3odj-qTbdlIktpcIDxGv8gzxP5AsZmW/view), [q2-b-after](https://drive.google.com/file/d/171aVoQA3CyJXS11PCYOUCxpIRWakcLF9/view) |
| Q3 | PASS — tua +60 giây, câu tô nhảy; nút “Về dòng đang hát” | [q3-b-after-seek60](https://drive.google.com/file/d/1TApXKE56paKRet8NZ2Jm2q_JGjhmIOL5/view) |
| Q4 | PASS — sang bài kế, lời cũ biến mất | [q4-next](https://drive.google.com/file/d/1elwP9XQyY1aP40Fz00IUacj0Hq1wPQs5/view) |
| Q5 | PASS có giới hạn — tìm thủ công, chọn kết quả | [q5-search-open](https://drive.google.com/file/d/17OPSg2pUBWbPy68m85dbpweQdSut5z-4/view), [q5-retry1](https://drive.google.com/file/d/146fe9-qsR6LTrBacO4thNwZAyocmBXNg/view), [q5-chosen](https://drive.google.com/file/d/1KAVxh3mVwtS0RWFj2UyJlQaJItZWkc5_/view) |
| Q6 | PASS — Hai khung, tab trong panel Hàng đợi | [q6-twopane-queue](https://drive.google.com/file/d/1ZkljUZc_cian0jtuaB6L4jzb8r43tEQJ/view) |
| Q7 | PASS — công tắc Cài đặt; tab báo tắt | [q7-settings](https://drive.google.com/file/d/1HSS7IXE8lbF3YGCOncbT_G8HqzhFz95v/view), [q7-off](https://drive.google.com/file/d/1Z6pmDzxBuPRqtvRVv3OZQtAFVnq3iATQ/view), [q7-deck-off](https://drive.google.com/file/d/15eNrIrv0g71aV9UxtJZcPeqShPaNpBah/view) |
| Q8 | **FAIL (B1)** — xoay ngang, pane kẹt “Đang tìm lời…” | [q6-deck-landscape2](https://drive.google.com/file/d/1MtCOaVtREIlNMD4y0ZwSbrazjvbKBE0U/view) |
| Bổ sung | Đường tự động bài trên máy: 503 → “Thử lại” → không tìm thấy | [q2l-deck](https://drive.google.com/file/d/1C75k2yT8iLhMnTAsknKSRAbMIf95dU5p/view), [q2l-retry-b1](https://drive.google.com/file/d/1DfPVxmPdJqg26_pc4UYTOblosy_iLNp-/view) |

Bản sao đầy đủ 37 ảnh + log ở Ni `docs/plans/artifacts/2026-10-31-loi-nhac-p2b/C-20261101/` (bị `.gitignore`).

Chưa kiểm được bằng ảnh: **âm thanh thật (CHƯA XÁC NHẬN ÂM THANH THỰC)**; lời tự động cho bài Telegram có tên + nghệ sĩ khớp LRCLIB; xoay ngang → dọc; chữ lớn 1.3; mất mạng/429 (chỉ test JVM); Release/`.open`/Personal trên máy; Y700/S25/FYT.
