Thực thi: Codex, GPT-6 (không có thông tin chi tiết model/effort), phiên03/11/2026; chốt hồ sơ 00:01:29 4/11/2026 (Asia/Saigon).

# Báo cáo nhạc theo chủ đề Telegram

**TASK_ID** `2026-11-03-chu-de-nhac-telegram` · **ĐẠT — PASS kèm giới hạn** theo lời “Đạt” của chủ dự án trong chat 04/11/2026, đúng source/APK bên dưới; [biên bản nghiệm thu](2026-11-03-chu-de-nhac-telegram-nghiem-thu.md). Mã, gate Ni, build Tiny và QA Sony đã hoàn tất ở phạm vi ghi nhận. **BLOCKED riêng link tải APK Drive (HTTP403)** vẫn giữ nguyên; APK hợp lệ đã cài trên Sony và có bản nội bộ đúng SHA. Không có QA mới trong lần ghi nhận quyết định.

| Mục | Kết quả |
|---|---|
| Source cuối | `e55ca7e932a249504cce83f2338d45e5dede9079`; nhánh `dot/2026-11-03-chu-de-nhac-telegram`; baseline `b90834f44457b60a9732e59e59c1bc9c971368c3`; kế hoạch `36c0647192234560d97bc1ebc304e89538094cf6` |
| Quyết định phạm vi | Chủ dự án: “Chốt migration v3 theo kế hoạch, thực thi tiếp S1–S5”. Giữ songKey/PK/playlist/tệp/account/owner/session/package/signer/versionCode |
| Ni cuối | Debug/Release mỗi variant2.741 passed / 21 skipped / 0 failed/error; assemble3 APK thành công2 phút 30 giây/exit0 |
| Tiny cuối | Clone cô lập mới, bundle SHA khớp; Debug + AndroidTest build1 phút 14 giây/exit0/configtrue |
| Sony cuối | DB v3/25.568 bài/2 mục yêu thích; queueA226 vượt trang 200, Next trong A, duyệt B giữ queue, badge giữsautính lại số bài, touch-mode/Back không crash, xoay/recreate và querykhôi phục |
| APK | vc121/0.6.96-beta,33.878.126 B, SHA `71809F4C96788F84A1A00FEA04B7446A8DF01426D6BE08D6D184D234BEC90820` |
| Drive | APK upload owner-only/sizekhớp nhưng tải403; 10 ảnh private metadatađã xác minh |

[Kế hoạch](2026-11-03-chu-de-nhac-telegram.md) · [Checkpoint và prompt phiên mới](2026-11-03-chu-de-nhac-telegram-ban-giao.md) · [APK](2026-11-03-chu-de-nhac-telegram-apk-test.md) · [Ảnh](2026-11-03-chu-de-nhac-telegram-hinh-nghiem-thu.md) · [Lịch sử đầy đủ](2026-11-03-chu-de-nhac-telegram-phu-luc-thuc-thi.md).

## Thực hiện

| Mốc | Thay đổi / kiểm chứng |
|---|---|
| S1 | Đúng TDLib 1.8.68 revision c15d3f5a5de6e3ba5839822c451152e5e18bb700; typed topic/triple cursor, không hardcodeGeneral hoặc coi trangngắn làhết. 11 tests hợpđồng. Native Sony tại35ba26b OK (1 test):14 chủ đề / 3 trang/General/end,2 chủ đề singleton→rỗng, nguồn thường,109 message đúng/0thiếu,24 request; binary khớp repo |
| S2 | Schema3 cộng thêm topic nullable/isForum tri-state/metadata/history. SQL và in-memory giữ scopeaccount/chat/topic, grouped counts, transaction songs+cursor/rollback/cancel; fixturelegacyv1/v2 giữPK/songKey/lookupplaylist/file metadata và2.000 bản ghi/indexcovering |
| S3 | Parser dùngchung audio/document WAV/FLAC/MP3/M4A/OGG/OPUS/AAC, không thêmDSF. Reader on-demand tối đa 3 trang/lần, cursorolder/head/catalogue/searchriêng, General/hidden/closed/rename, cancellation/owner/authEpoch/backoff. Không mở client/receiver thứhai, không tải nhạc để lậpchỉmục |
| S4 | FORUM_TOPICS/FORUM_TOPIC cóowner/chat/topic trongstate/query/sort/Downloaded/scroll/bundle. Phone/tablet/two-pane/Gọn/Car cùnghost, nguồn đãxácnhận cóbadge, Allgroup giữbàiNULLlegacy. Queue snapshot toàn bộ index đúng scope vàbộ lọc, không bịgiới hạn200, chỉduyệt mànkhông đổi queue |
| S5/sửa nguồn | Recompute/count/title maintenance từng dùng REPLACE làm rơi isForum/type. Đổi INSERTORIGNORE + UPDATE từng trường trong transaction, giữ metadata. kiểm hồi quy SQLite JDBC + Sony chủ động recompute rồi assert DB/root hasTopics/Q9 |
| S5/sửa crash | Manual04aa5ba bắt IllegalStateException số hàng RowsAdapter đổi khi ListView ẩn không được notify. e55ca7e notify ngay khi đổi Snapshot, trước reset/nhánh trả về sớm; giữ khôi phục vị trí. Sony kiểm ListView thật đang gắn với màn.onTouchModeChanged(true) sau B→catalogue; chạm thực mở lại B giữ PID/không có FATAL |

## Test/build cuối

| Variant | Suites | Defined | Passed | Skipped | Failed/errors |
|---|---:|---:|---:|---:|---:|
| ShareableDebug |205|2762|2741|21|0/0|
| ShareableRelease |205|2762|2741|21|0/0|

Ni: `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest :app:assembleShareableDebug :app:assembleShareableRelease :app:assembleShareableDebugAndroidTest --console=plain`; log `scratch/chu-de-telegram/ni-hidden-list-r7.log`, exit0,2 phút 30 giây. XML ở app/build/test-results. Diffcheck sạch ở mốc source; chỉ hồ sơ được cập nhật sau QA. APK Ni configfalse chỉ compile, không cài/bàn giao.

Tiny DESKTOP-HNP4GVR: clone `C:/Bots/MeloVault-FORUM-TouchQA-20261103-e55ca7e`, bundle 13.918.905 B/SHA7424F726D4ED0CF5A6489D8B079B93DA3D4867C61DF6B260666DB1A67CEC9A7D khớp Ni/Tiny vàverifyGit. JDK17/SDK Tiny, Gradle dùng config đãcó qua meloLocalPropertiesFile; chỉ reportbooleantrue, không đọc/copy/hash/log secret hoặc session. Build1 phút 14 giây/exit0. Cert `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`; install-r vàpull APK đãcài khớp SHA. Không downgrade/uninstall/clear.

## Sony thật và thao tác chạm

Sony QV725WEQ54, XQ-AS52/Android12, thiết bị thật duy nhất. Không cài FYT. Baseline trước nâng: DB v2/25.558 bài/2 mục yêu thích. Sau nâng đầu: DB v3/25.558 bài; global sync sau đó thêm 10 bài. Bản cuối baseline OK (1 test): DB v3/**25.568 bài/2 mục yêu thích**. Không coi200 khóa sample lấy sau migration là so sánh toàn bộ khóa trước/sau; bằng chứng bảo toàn PK đầy đủ ở fixture độc lập.

| Bước | Kỳ vọng / thực tế đã đối chiếu | Bằng chứng |
|---|---|---|
| 1 Nguồn thường / forum / Allgroup | Nguồn thường mở bài; forum mở danh mục; Allgroup gồm4.403 bài đãindex kểcảlegacy | Q1–Q3 |
| 2 Phát hết A / Next | List chỉ200/226, queue226 đúngA; Next vẫnA | Q4, assertion membership từngkey/owner/chat/topic |
| 3 Duyệt B |15 bàiB, queueA khôngđổi; player playing=true/position483ms | Q5 vàlog, không chứng minh tiếng thật |
| 4 Recompute nguồn / touch-mode | Cờforum vàbadge vẫnđúng; B→catalogue→touchmode→mở lại B, queuegiữ | Q9; touch_mode_after_back=true |
| 5 Downloaded / xoay / recreate | B có0 tệp offline đã xác minh; giữ owner/topic/filter vàqueue,200 khóa sample resolve | Q6–Q8 |
| 6 Chạm thực / query | Telegram→nhóm→LUSH→Nam ra1 bài→Back vềdanhmụckhông lọc→mở lại LUSH vẫn Nam. PID trước/sau chạm giữ nguyên, không có FATALmới | manualM1–M6, manual-tail-result.txt, android-runtime-guarded.txt |

Feature native cuối **OK (1 test),32,144 giây**; discovery9 request, history/cursor được giới hạn theo reader. Ngân sách/index/cursor được kiểm bằng fixture vàSQL; chưa benchmark cold/warm độc lập. Manual helper từng nhận hint “Tìm tên chủ đề…” thành text nên dừng nhầm ởguard; đã đối chiếu ảnh và chạm tiếp có guard foreground/PID/log. Không đổi mã app để xử lý hiểu nhầm đó.

## Bàn giao và giới hạn

10 ảnh của đúnge55ca7e đã upload Ni N/nigamehome@gmail.com, từng file owner duy nhất/shared=false/bytes khớp; PNG headers vàSHA Tiny→Ni khớp. Collage1600×1410/SHA C3794F31E99A1E00AF53D858D4139385EC03A4EF3DDB72AB299105593D1BE113 đã xem trực tiếp. Ảnh/log private trong docs/plans/artifacts/TASK_ID, không commit; ảnh cũ giữriêng nguyênhash.

APK Drive đã uploadđủ metadata nhưng fetch raw **403/cannotDownloadAbusiveFile**, provider đánh dấu malware hoặcspam. **BLOCKED — CHƯA BÀN GIAO APK qua link tải**; không né/public/đổi đuôi/đóng gói/acknowledgeAbuse. APK hiện đã cài trên Sony; bản Ni/Tiny và link bị chặn ghi trong hồ sơ APK.

- **CHƯA XÁC NHẬN ÂM THANH THỰC**; không có nguồn nghe độc lập. Media3playing/tiếntrình không thay bằngchứngnghe.
- B không có tệp offline đã xác minh; chưa phát offlineB/mất mạng. Không bật airplane/tắt mạng/đổi quyền/account để ép lỗi.
- WAV/FLAC document có fixture, Sony lượt này phátMP3. giải mã DSD ngoài phạm vi. General/closed/rename/access/flood/callbackcũ có fixture; chỉ một phần quan sát thật.
- Gọn/Car được nối vàcompile, chưa QA thiết bị tương ứng/FYT. Cold/warm benchmark vàShuffle/Previous thực riêng chưa thực hiện; Next vàqueuemembership đã kiểm.
- Rollback bằng bản sửa tiến trên DB v3, không hạ APK/DB, giữ playlist/tệp/phiên. Khi chốt thực thi chưa merge/push; kết quả tích hợp sau ĐẠT xem HANDOFF. Chưa phát hành APK công khai.
- Mỗi lượtUI cleanup 223 + đợi 800 ms + xác minh OFF. **SONY_SCREEN_END=OFF**; không job build/instrument của phiên còn chạy khi chốt.

Lịch sử: thực thi đã bàn giao prompt cho phiên sơ bộ mới và không tự ghi PASS. Sau đó chủ dự án quyết định **ĐẠT** trước khi có phiên sơ bộ độc lập. Merge/push theo chỉ thị thường trực 02/11 được ghi kết quả tại mục đầu `HANDOFF.md` trên nhánh tích hợp; không phát hành APK công khai.
