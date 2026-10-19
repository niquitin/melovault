# ChatGPT - Kiem toan ke hoach Opus offline-one-tap-play (19/10/2026)

KET LUAN: REWORK PLAN - CHUA CHO SONNET VIET CODE.

- Git tai luc audit: dot/offline-one-tap-play, HEAD 29c54f9c667033a786fe95da3a9324116a4889bf, parent c99fbfb, working tree sach. Plan: docs/plans/offline-one-tap-play.md. Opus chua sua source.
- Yeu cau DUOC owner chot chinh xac: "Cham vao bai hat: phat ngay ban offline phu hop" trong muc Da tai. Owner chi lua chon ro diem single-tap; CHUA chap thuan ro rang thay doi semantics hang doi moi nhom mot bai, hay bat buoc them menu moi "Chon ban khac (N ban)" thay cho menu hien huu.
- Code hien tai da co giao dien chon phien ban tu dau ba cham: TelegramPreviewActivity.kt line ~2593/4382 goi showOfflineVersions(group, anchor), chon mot phien ban thi phat. CAN GIU lai hanh vi menu ba cham hien co tru khi chung minh khong dap ung UX va xin owner chap thuan.
- Hai duong cham mo dialog: Legacy tracks.onItemClickListener line ~2696 va playShownSong line ~4229. Phai sua de 1-tap phat nhat quan tren cac layout, nhung khong nhat thiet sua showSongOptionsMenu, playlist, queue, prefetch.
- DEFER change scope queue: plan muc 1 va 5.I doi offlineGroups.flatMap sang queueEntries mot bai/nhom trong enqueueTelegramPlayback & enqueueProgressivePlayback. Owner CHUA chot, rui ro doi semantics Next, queue, other screens. Giu queue behavior cu ngoai single-tap; neu muon queue-dedup can phai xin owner chot rieng/TASK_ID rieng.
- QUAN TRONG: plan defaultEntry fallback ve entry khong doc duoc va goi downloadAndPlay co the tao stream online. Trong tab Da tai, single-tap chi nen phat ban offline co tep hop le (canonical path dung account root, isFile/canRead/size>0, size expected neu co). Neu KHONG con ban doc duoc thi thong bao file khong san sang va de nghi quet lai; KHONG ngam tai/stream online, khong xoa cache. Neu code hien tai khong dam bao duoc offline playback khi khong mang thi danh dau [CAN XAC NHAN] va danh gia giai phap truoc khi giao Sonnet.
- Chon ban deterministic: uu tien ban dang phat trong group neu con doc duoc, sau do ban dau tien con doc duoc. Khi ban duoc phat khac representative row truoc do, cap nhat songKey/hien thi dung; dung identity songKey va account/chat/message, khong dung raw messageId don le lam dinh danh giua chats.
- Tests can no-readable -> khong phat/khong download, account isolation, same-title different file/path, playing/default, async account switch, GON/Legacy taps, va regression ba cham hien co. 3-6 buoc manual owner QA duoc giu, bo tieu chi queue-per-group chua phe duyet.
- Neu Opus nhan thay bat buoc phai doi queue/menu/TDLib/Media3 de hoan thanh one-tap, DUNG va de xuat cho owner chot; khong ghi "chu du an chot" khi thuc te chua.
- Bước kế: Opus HOI THOAI MOI doc danh gia nay, sua CHI file docs/plans/offline-one-tap-play.md tren CUNG nhanh dot/offline-one-tap-play, commit plan revision. KHONG sua source/build/cai APK. ChatGPT audit plan moi truoc khi gui Sonnet.

## Quyết định mới của chủ dự án 19/10/2026 — duyệt plan nguyên văn

**TRẠNG THÁI PLAN: ĐÃ DUYỆT — thay thế kết luận REWORK PLAN phía trên.** Chủ dự án nói rõ "plan này ko cần chỉnh sửa gì đâu". Kế hoạch Opus ở commit 29c54f9 được phê duyệt toàn bộ (kể cả thiết kế menu/queue trong plan), KHÔNG được yêu cầu thêm một vòng Opus sửa. Các rủi ro về lựa chọn file, phát online fallback và queue được giữ trong hồ sơ để Sonnet và ChatGPT kiểm thử, không tự ý viết lại plan.
**Bổ sung QA từ xa, không đổi plan:** bác sĩ không thường ở cạnh thiết bị. Sonnet phải tự thực hiện test và chụp ảnh/video bằng ADB (Shanling Ni, Sony Tiny nếu phù hợp); ChatGPT xem ảnh, độc lập kiểm tra và gửi ảnh/video cho bác sĩ duyệt ngay tại hội thoại. Bài test âm thanh thật hoặc đổi chế độ máy bay mà Agent không được phép tự làm phải ghi CHƯA KIỂM CHỨNG, không tuyên bố đã đạt.
**Bước tiếp theo:** Claude Sonnet trong hội thoại MỚI, nhánh dot/offline-one-tap-play, dùng đúng plan và SOP mới. Không mở lại Opus.
