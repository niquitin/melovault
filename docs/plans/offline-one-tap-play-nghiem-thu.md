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

## Kiểm toán độc lập sau Sonnet — 19/10/2026, 09:27 (+07)

**TRẠNG THÁI: BLOCKED — CHƯA ĐỦ BẰNG CHỨNG QA THIẾT BỊ, KHÔNG PASS, KHÔNG MERGE.** Đây là đánh giá cho việc triển khai và nghiệm thu thực tế, không phải yêu cầu Opus sửa lại kế hoạch đã được chủ dự án duyệt nguyên văn.

### Đã kiểm chứng
- Nhánh Ni `dot/offline-one-tap-play`; code tính năng commit `fdf64c5`, chỉ sửa 3 tệp: mới TelegramOfflinePlayPolicy.kt, TelegramOfflinePlayPolicyTest.kt, sửa TelegramPreviewActivity.kt. Quy tắc giữ màn hình chỉ trong docs commit `93c50c1` (`CLAUDE.md`, `docs/workflow/SCREEN_LIFECYCLE_QA.md` và SOP/prompt mẫu). Git sạch tại thời điểm đánh giá.
- Soát diff: single tap ở cả Legacy và các host Melo giờ gọi `playOfflineGroup` khi nhóm nhiều bản; menu ba chấm có mục chọn bản; hàng đợi Đã tải dùng `queueEntries` mỗi nhóm một bản. TDLib/SQLite/Media3 service không bị sửa; chưa chứng minh hành vi runtime qua màn thiết bị.
- ChatGPT tự chạy `gradlew :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest :app:assembleShareableDebug :app:assembleShareableRelease --rerun-tasks --console=plain --no-daemon` trên Ni từ 09:17:05 đến 09:23:41. **BUILD SUCCESSFUL, 109/109 tác vụ thực thi**. Từng variant Debug/Release: 118 XML suites, 1263 tests defined, **1242 passed, 21 skipped, 0 fail, 0 error**. Có warning deprecated Android/Gradle đang tồn tại, không làm test thất bại.
- Tiny clone cô lập có branch `qa/offline-one-tap-play` ở commit fdf64c5, working tree sạch; `app-shareable-debug.apk` (33.609.254 byte) được build trên Tiny lúc 09:16:58. **[CHƯA XÁC NHẬN]** Tiny debug APK đã được xác minh package/cert/auth config và cài lên Shanling hay chưa, vì chưa có báo cáo QA/verify tương ứng.
- Ni có ảnh mở ứng dụng `docs/plans/artifacts/offline-one-tap-play/01_app_launched_shanling_092121.png`, ChatGPT trực tiếp xem; ảnh cho thấy màn Thư viện Gọn và header đã tải nhưng **KHÔNG chứng minh chạm nhóm một lần để phát, menu ⋮, chọn bản khác hay Next**. Ảnh `00_shanling_baseline_before_install.png` chỉ 3922 byte, không coi là bằng chứng QA đủ.
- Hiện **CHƯA có** `docs/plans/offline-one-tap-play-bao-cao.md`, ảnh/video các thao tác 3–6 bước và bảng ADB/Media3 playback; không coi flow này đã được kiểm trên thiết bị.
- Bảo vệ màn hình: ngày 19/10 khoảng 09:25, Tiny/Sony xác nhận `Display State=OFF` trong khi `mWakefulness=Dozing`; khoảng 09:26 Ni/Shanling được gửi `KEYCODE_SLEEP=223` sau khi không còn bước QA thực hiện, kết quả `mWakefulness=Asleep` và `mScreenState=OFF`. Không tắt nguồn, uninstall hay xóa dữ liệu.

### Điểm cần Sonnet hoàn tất (KHÔNG Opus mới)
1. Trong **hội thoại Sonnet mới** đọc CLAUDE.md, `docs/workflow/SCREEN_LIFECYCLE_QA.md`, plan và biên bản này; đối chiếu đúng branch/HEAD, không viết lại code đã commit trừ khi QA phát hiện lỗi.
2. Kiểm chứng bản APK Debug Tiny có Telegram config hợp lệ, đúng package/versionCode/cert ký khớp app Shanling; nếu mismatch DỪNG, tuyệt đối không uninstall/pm clear. Nếu chưa cài, chỉ `adb install -r` khi chắc chắn an toàn và không cắt ngang nhạc đang phát.
3. Thực hiện qua ADB trên Shanling ít nhất các thao tác trong plan: mở mục Đã tải, chạm nhóm nhiều bản → phát ngay/Media3 tiến, menu ⋮ có chọn bản khác, chọn bản khác chuyển bài, Next kiểm hàng đợi, quay Thư viện không regression. Chụp hình trước/sau và screenrecord ngắn khi cần. Nếu không thể nghe âm thực, phải ghi CHƯA XÁC NHẬN AUDIO THỰC.
4. Sonnet lập bảng PASS/FAIL/BLOCKED có ảnh/video/serial/timestamp, commit báo cáo `docs/plans/offline-one-tap-play-bao-cao.md` trên cùng nhánh rồi DỪNG.
5. QUAN TRỌNG: Chỉ bật màn M3/Sony khi thao tác UI, tắt màn (KEYCODE_SLEEP=223) và kiểm Display OFF trong `finally`, không để màn sáng khi build/đợi.
6. ChatGPT sau đó tự kiểm chứng ảnh/test/playback và gửi bộ hình thực cho chủ dự án xem nghiệm thu từ xa; **chưa đánh dấu PASS/merge** trước khi chủ dự án duyệt.

### Rủi ro còn phải xem xét khi nghiệm thu
- Theo plan đã duyệt, `defaultEntry` có fallback sang ứng viên không còn đọc được rồi cho `downloadAndPlay` quyết định; nếu offline mất nhưng có mạng, có thể stream lại. Không gọi đó là "đã chứng minh offline hoàn toàn" nếu chưa test tình huống; không được phá dữ liệu/bật máy bay trái quyền để tạo case.
- Media3 chuyển sang PLAYING/time tăng không chứng minh loa/tai nghe phát âm thực; không tuyên bố đã nghe âm thực khi chưa có bằng chứng.
