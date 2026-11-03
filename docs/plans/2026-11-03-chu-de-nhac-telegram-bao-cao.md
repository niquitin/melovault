Thực thi: Codex, GPT-6 (model/effort chi tiết không được cung cấp), phiên 03/11/2026, S1–S5.

# Báo cáo nhạc theo chủ đề Telegram

**TASK_ID** `2026-11-03-chu-de-nhac-telegram` · **ĐANG THỰC THI**. Bản `33b16f9` đã qua QA luồng A/B, nhưng manual phát hiện bước tính lại số bài làm rơi cờ forum đã lưu. Đang sửa và chạy gate Ni mới; chưa bàn giao source cuối. Drive chặn tải các APK đã upload. Chưa có quyết định ĐẠT, chưa merge/push.

| Phạm vi | Kết quả / bằng chứng |
|---|---|
| Git | Worktree `C:/Users/ntdun/.codex/worktrees/melovault-chu-de-telegram/MeloVault-E91-Shareable`, nhánh `dot/2026-11-03-chu-de-nhac-telegram`; baseline `b90834f44457b60a9732e59e59c1bc9c971368c3`; kế hoạch `36c0647192234560d97bc1ebc304e89538094cf6`. |
| Phạm vi đã chốt | Chủ dự án: “Chốt migration v3 theo kế hoạch, thực thi tiếp S1–S5”. Không thay songKey, PK/playlist/tệp/account/owner/session, package/chữ ký/versionCode. |
| Source cuối | `33b16f9855deff9e62dc78cdec82737bbe9bff92`; S2 `28ff8e1`, S3 `95d6fda`, S4 `982eeee`, sửa hiển thị cuối `33b16f9`. Commit hồ sơ sau không thay source APK. |
| Ni cuối | `ni-final-ui-r5.*`: tests Debug/Release + assemble Debug/Release/AndroidTest **BUILD SUCCESSFUL 4m7, exit0**, diffcheck sạch. |
| Sony đã kiểm | `982eeee`: DBv2→v3 giữ 25.558 bài, nguồn thường, catalogue/All group, A226/queue226 (>200 hàng), NextA, duyệtB giữqueueA, DownloadedB0, xoay/recreate đúng owner/topic/filter. Source cuối đang chờ kiểm lại. |
| APK/Drive | APK982eeee hợp lệ, cài `-r` đúng hash; Drive upload private thành công nhưng fetch trả **403/cannotDownloadAbusiveFile**. **BLOCKED — CHƯA BÀN GIAO APK qua link tải**. Không né cảnh báo. |

[Kế hoạch](2026-11-03-chu-de-nhac-telegram.md) · [Checkpoint](2026-11-03-chu-de-nhac-telegram-ban-giao.md) · [Lịch sử đầy đủ S1–S5, giữ nguyên từng lượt](2026-11-03-chu-de-nhac-telegram-phu-luc-thuc-thi.md).

## Thay đổi và cách kiểm

| Mốc | Thực hiện | Bằng chứng |
|---|---|---|
| S1 | Đối chiếu đúng TDLib1.8.68 revision `c15d3f5a5de6e3ba5839822c451152e5e18bb700`, typed topic, triple cursor, trang ngắn/toàn chữ/biên inclusive hoặc exclusive. Không hardcode General trong sản phẩm. | 11 tests hợp đồng; Sony probe opt-in OK1test tại35ba26b:14topic/3trang/General/end, hai topic singleton→rỗng, nguồn thường,109message đúng/0thiếu,24requests. Native `.so` khớp APK sẵn có. |
| S2 | Schema3 cộng thêm: topic nullable trên bài, isForum tri-state trên nguồn, metadata/history progress. Ghi COALESCE giữ metadata đã biết, transaction songs+cursor, grouped count, lọc account/chat/topic đủ list/search/count/page. | 7 SQLiteJDBC tests mới, fixtures v1/v2 độc lập,2.000 bản ghi/covering index,rollback/cancel; 30tests chọn lọc đạt. Không destructive migration. |
| S3 | Parser audio/document dùng chung WAV/FLAC/MP3/M4A/OGG/OPUS/AAC, không thêm DSF. Reader on-demand tối đa3trang/lần; head/older cursor riêng, catalogue/search riêng, General/rename/hidden/closed, chống callback trễ và flood/network backoff. | 44tests chọn lọc đạt; native read S1 chứng minh phương thức/schema đúng binary. Không đọc toàn bộ topic khi mở nhóm, không tải nhạc để lập chỉ mục. |
| S4 | Scope/state/bundle owner+chat+topic, parent/scroll/query/sort/Downloaded; phone/tablet/two-pane/Gọn/Car dùng chung host. Worker/tag process-wide/cancel trên owner hiện hữu. Queue snapshot toàn bộ index đúng topic/bộ lọc, giữ khi chỉ duyệt màn khác. | 115tests chọn lọc, gồm queue503 cùng tên/foreign scope, state cũ; đầy đủ gate Ni. Sony A226 bài, màn chỉ200 hàng nhưng queue226; Next và browseB giữqueue đã kiểm. |
| S4 cuối | Grid phone dùng SourcePresentation chung để hiện Có chủ đề; catalogue subtitle đúng nhóm, bỏ strip thể loại/sourcecards và đo ListView như nội dung danh sách. | Phát hiện qua ảnh/manual982eeee, sửa tại33b16f9; gate Ni xanh. Chưa thay decoder/player/service/DB/reader trong lần sửa này. |

## Gate Ni cuối

| Variant | Suites | Defined | Passed | Skipped | Failed/errors |
|---|---:|---:|---:|---:|---:|
| ShareableDebug |205|2761|2740|21|0/0|
| ShareableRelease |205|2761|2740|21|0/0|

Lệnh chạy `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest :app:assembleShareableDebug :app:assembleShareableRelease :app:assembleShareableDebugAndroidTest --console=plain`; XML `app/build/test-results/`; log `scratch/chu-de-telegram/ni-final-ui-r5.log`, exit0. Các gate trước và lỗi assertion schema/index cũ đã sửa được giữ trong phụ lục. APK Ni config=false chỉ dùng kiểm compile, không cài/bàn giao.

## Build Tiny và thiết bị

- Tiny `DESKTOP-HNP4GVR`, device connector `9c7f081b-6e1c-4221-ac78-da470bab1e5d`; Sony `QV725WEQ54`, XQ-AS52/Android12. Chỉ thiết bị này; không cài FYT. Không có agent phụ hoặc agent khác sửa code.
- Source cuối bundle `forum-final-20261103-33b16f9.bundle`, **13.890.645B**, SHA **8182EC08F89DE369B83DF2561284270517AC982B209A18886144ADC3CA33B540**, khớp hai đầu và verifyGit. Clone mới `C:/Bots/MeloVault-FORUM-FinalQA-20261103-33b16f9`, đúngHEAD/cây sạch.
- Build cuối PID32060, log/exit `C:/Bots/_qa_temp/forum-final-20261103-33b16f9/build.*`, JDK17/SDK Tiny. Gradle dùng config đã có trên Tiny qua property `meloLocalPropertiesFile`; không đọc/copy/hash/log secret. Đang chờ kết quả và config boolean.
- Bản trước source982eeee: APK33.878.126B, SHA `FEAC5545ACAC3C0C19779921C64632D6C57E8EECAD76CC804E01BBAFB10386CE`, vc121/0.6.96-beta, package `app.melovault.player`, cert `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`, config=true; install-r và pull APK cài khớp. Không downgrade/uninstall/clear.

## Sony đã có bằng chứng tại982eeee

| Bước | Kỳ vọng / thực tế | Bằng chứng kỹ thuật |
|---|---|---|
| Baseline trước cài | DBv2,25.558 bài,2 yêu thích. Không thay app/data để đo. | Baseline opt-in OK1test; chỉ đọc catalogue qua API app. |
| Nguồn thường→forum→All group | Nguồn thường mở bài; forum mở catalogue và All group giữ bài cũ. | Q1–Q3, host thật/DAO thật; discovery9requests ở feature probe. |
| Phát hết A |226bài đã index, danh sách200hàng; queue226 đúngA, Next vẫnA. | Q4, assertion membership theo owner/chat/topic/same songKey. |
| Duyệt B |15bàiB, queueA không đổi, player tiếp tục. | Q5, queue equality; playing=true/position617ms lượt chụp lại. Không chứng minh tiếng thật. |
| Đã tải/xoay/recreate | B có0tệp offline đã xác minh; giữB/filter/owner,200key sample sau migration còn resolve và queue không đổi. | Q6–Q8; probe44,839s và28,561s, mỗi lượt OK1test. Mẫu key được lấy sau migration, không coi là đối chiếu toàn bộ khóa trước/sau. |
| Chạm thực | Telegram→nhóm→LUSH→nhậpNam, còn1bài đúngB. | ManualM2–M6, tọa độ từ ảnh/XML thật; count toàn account23:10=25.568 do global sync thêm10bài sau mốc19:47. |

Ảnh cũ source982eeee tại `docs/plans/artifacts/2026-11-03-chu-de-nhac-telegram/qa-982eeee/capture-r3/`, PNGheader/SHA/bytes Tiny→Ni đã khớp; collage6 ảnh1600×1410 ở `00_tong_hop.png`, SHA `34C39EF55E7368CD8142143EC5D605B6D6E3E808DA7EC2CC4084D68B013C62FD`. Không gán ảnh cũ cho source cuối. Watcher đầu bị log rỗng, đã sửa/chụp lại; không sửa kết luận lịch sử.

## Giới hạn và bàn giao

- **CHƯA XÁC NHẬN ÂM THANH THỰC**; không có nguồn nghe độc lập. Sony từng có MediaSession state7 do ngắt Bluetooth trước cài; không đổi route/volume để che lỗi.
- DownloadedB=0 nên chưa chứng minh phát offlineB; không tắt mạng/airplane. WAV/FLAC document được kiểm bằng parser fixture, chưa có ảnh playback file đó trong lượt Sony này. DSD decoding ngoài phạm vi.
- General/closed/rename/access/network/rate-limit/callback cũ có fixture; chỉ một phần được quan sát thật trong probe. Không tự đổi quyền Telegram/đổi account/tắt mạng để ép lỗi. Gọn/Car đã nối và compile; chưa có QA phần cứng tương ứng/FYT.
- Rollback dùng bản sửa tiến trên DBv3; không cài APK cũ hạ DB. Giữ playlist/tệp/phiên, không xóa cache hay index.
- Mỗi script UI có cleanup223 và xác minh OFF sau800ms. Ảnh manualM0/M1 sau gián đoạn ở app khác không dùng làm QA; đã mở/kiểm foreground đúng MeloVault trước thao tác tiếp. **SONY_SCREEN_END=OFF** ở mốc trước build cuối.
- Drive982eeee upload owner-only Ni N/nigamehome@gmail.com, readback shared=false, nhưng kiểm tải403. File giữ nguyên, không public/đổiđuôi/acknowledgeAbuse/đóng gói để né. Hồ sơ cuối phải ghi rõ link có tải được hay không.
- Chỉ sau hoàn tất build/QA/ảnh/hồ sơ source cuối mới chuyển **phiên nghiệm thu sơ bộ mới**. Chủ dự án quyết định ĐẠT hoặc yêu cầu sửa; không tự nghiệm thu/merge/push.
