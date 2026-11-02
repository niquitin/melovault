# 2026-11-01-nhay-header-khi-cuon — Nghiệm thu chính thức

**Trạng thái: ĐẠT — PASS kèm giới hạn** (quyết định của chủ dự án).

- **Quyết định:** chủ dự án nhắn “Đã đạt” trong chat ngày 02/11/2026, ngay sau khi phiên thực thi báo xong và đưa APK QA candidate. Đây là nguồn xác nhận duy nhất; không dùng dạng `ĐẠT <TASK_ID> — <commit/APK>` nên phạm vi được hiểu theo thông báo ngay trước đó.
- **Đúng bản/phạm vi:** TASK_ID `2026-11-01-nhay-header-khi-cuon`; commit mã `4fe5a370ce58719495d63950d9dd33e6ed7a0619` (nhánh `dot/2026-11-01-nhay-header-khi-cuon`, hồ sơ cuối `b4d50e1`); APK Debug vc121 0.6.96-beta, SHA-256 `E5D808D2C29F70333350EF3682A50FBF686D6140C93513F1A6AA7534A27C6B74`. Phạm vi: header không nháy khi kéo/lướt danh sách bài trong giao diện Mới (`MeloLibraryPane`).
- **Không có nghiệm thu sơ bộ độc lập:** chủ dự án quyết định ĐẠT trước khi mở phiên sơ bộ; bằng chứng kỹ thuật chỉ là của phiên thực thi ([báo cáo](2026-11-01-nhay-header-khi-cuon-bao-cao.md), [apk-test](2026-11-01-nhay-header-khi-cuon-apk-test.md), [hình](2026-11-01-nhay-header-khi-cuon-hinh-nghiem-thu.md)).

## Giới hạn còn lại (chủ dự án chấp nhận khi ĐẠT)

- Hành vi mới: header chỉ gấp/hiện sau khi nhả tay hoặc khi danh sách trôi, không gấp ngay lúc kéo. Muốn gấp ngay lúc kéo → phương án B, nhiệm vụ riêng.
- Q6 (xoay ngang/Hai khung) **CHƯA KIỂM**; Playlist ≥ 20 bài chưa kiểm (QA dùng tab Thư viện); ngón tay thật chưa kiểm (QA giả lập ADB); **CHƯA XÁC NHẬN ÂM THANH THỰC**; Release/`.open`/Personal trên máy; chữ lớn, TalkBack.
- Nguyên nhân 2 (đo sai ở ranh giới dòng) chưa tách riêng trên máy; một lần chạy Q3b thiếu 1 dòng log (video vẫn đủ).

## Trạng thái merge/push

Chưa merge, chưa push, chưa public. Merge vào nhánh tích hợp chỉ khi có chỉ thị riêng của chủ dự án; push/public cần chỉ thị riêng.
