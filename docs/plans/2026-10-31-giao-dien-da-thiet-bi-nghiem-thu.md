# Nghiệm thu — 2026-10-31-giao-dien-da-thiet-bi

**PASS — chủ dự án xác nhận ĐẠT (01/11/2026)**, kèm giới hạn còn lại ở mục “Quyết định chủ dự án” cuối file.

- Bản/phạm vi: nhánh `dot/2026-10-31-giao-dien-da-thiet-bi`, HEAD `20bdf95`, APK Debug vc121 SHA-256 `367CAA37…F1006` ([`-apk-test.md`](2026-10-31-giao-dien-da-thiet-bi-apk-test.md)).
- Ảnh: [`-hinh-nghiem-thu.md`](2026-10-31-giao-dien-da-thiet-bi-hinh-nghiem-thu.md). Báo cáo: [`-bao-cao.md`](2026-10-31-giao-dien-da-thiet-bi-bao-cao.md) mục “Chặng C”.
- Nghiệm thu sơ bộ 01/11: **SƠ BỘ: CẦN SỬA** (UI-03, UI-02), xem [`-nghiem-thu-so-bo.md`](2026-10-31-giao-dien-da-thiet-bi-nghiem-thu-so-bo.md).
- Điểm agent nêu để chủ dự án quyết: UI-03 chưa sửa được trên máy thật; UI-02 chỉ cải thiện (M1 dọc, F3 M3/M6 dọc vẫn gần 0 dòng kết quả); UI-06 không sửa theo quyết định; Gọn chia đôi chưa chụp lại; âm thanh thật chưa xác nhận.
- Phản hồi: `ĐẠT 2026-10-31-giao-dien-da-thiet-bi — 20bdf95/367CAA37` hoặc `Yêu cầu sửa 2026-10-31-giao-dien-da-thiet-bi: <lỗi>`.

## Quyết định chủ dự án — 01/11/2026

- Nguồn xác nhận: chủ dự án nhắn trực tiếp trong chat: `ĐẠT 2026-10-31-giao-dien-da-thiet-bi — 8664915/367CAA37`.
- Phạm vi PASS: TASK_ID `2026-10-31-giao-dien-da-thiet-bi`, nhánh `dot/2026-10-31-giao-dien-da-thiet-bi`, commit hồ sơ `8664915` (mã cuối `75375b4`), APK Debug vc121 0.6.96-beta SHA-256 `367CAA3738171D4163CAC04844F9E9F4FB2D0BC3C142CDA631F889C2929F1006`. Không phải phát hành công khai.
- **Giới hạn còn lại, chủ dự án chấp nhận khi ĐẠT (chưa sửa):** SB-1 UI-03 (lề màn Hàng đợi chưa có hiệu lực trên máy thật; nguyên nhân khả dĩ: dialog `showMeloQueue()` thiếu `setDecorFitsSystemWindows(false)`); SB-2 UI-02 (cửa sổ thấp có bàn phím vẫn ≈ 0–0,5 dòng kết quả); UI-06 không sửa theo quyết định; UI-01 M2 ngang ≈ 1,5 dòng; Gọn chia đôi chưa chụp lại; tablet/máy gập thật chưa kiểm; **CHƯA XÁC NHẬN ÂM THANH THỰC**.
- Chưa merge, chưa push, chưa public: cần chỉ thị riêng của chủ dự án. Muốn sửa SB-1/SB-2 thì giao REWORK hoặc nhiệm vụ riêng.
