Thực thi: Codex (model/effort không được công cụ xác nhận), phiên code + test Ni, 28/10/2026 (+07).

# E105 — Tự đồng bộ Playlist khi mở app

## 1. Kết quả phiên code

| Phần | Kết quả |
|---|---|
| Bật một lần | Cảnh báo v3; quyền thủ công v2 không tự bật; tự tìm/kiểm/gắn kênh đã có |
| Thay đổi chờ gửi | Xem từng ADD/REMOVE theo thứ tự, tên/nguồn và mã tin để phân biệt bài gần trùng; xác nhận đúng bản xem trước |
| Tự đồng bộ | Foreground + playlist sẵn sàng; sau lưu 500 ms; kiểm mỗi 15 s; tiếp sau 1 s khi có tiến triển; tối đa 3 thao tác/lượt |
| Cập nhật UI | Playlist đang mở tự cập nhật qua live refresh, không restart; nút Đồng bộ ngay dùng cùng bộ điều phối |
| Dừng an toàn | Rời app/tắt/đổi auth epoch hủy; chặn kết quả mạng cũ trước commit và trước gửi; không thêm service/worker nền |
| Thêm/bỏ/thêm lại | Kiểm thử hai replica dùng V2/core thật và kênh giả thành công; hai tap tạo ADD rồi REMOVE được giữ đúng nghĩa |
| Thiết bị | Chưa cài/chưa QA Shanling/Sony; chưa xác định hai mục đang tồn trên Shanling |
| Nghiệm thu | Chưa nghiệm thu; bàn giao phiên QA theo 5 bước trong kế hoạch đã chốt lại |

Chủ dự án yêu cầu IMPLEMENT kế hoạch tự đồng bộ ngay trong hội thoại; phạm vi phiên này là code/test/build Ni/commit.
Nhánh `dot/E105-DONG-BO-PLAYLIST`, baseline `6d427f4c55c6e191c448861778da05df4b8c9f7d` (source trước sửa `449ff72`).
Đầu phiên tracked sạch, chỉ `.codex-remote-attachments/` đã được cho giữ nguyên; không Java/build khác. Không thêm thư mục đó vào commit.
Kế hoạch canonical có mục chốt lại 28/10 ở đầu; phần thủ công cũ được giữ làm lịch sử.
Toàn bộ báo cáo phiên 1/2 và REWORK 1 trước đó giữ nguyên tại [báo cáo trước tự động](../history/E105-DONG-BO-PLAYLIST-bao-cao-truoc-auto-6d427f4.md), đồng thời có trong Git baseline.

## 2. Mã và test

- `AutomaticPlaylistSync`: điều phối một executor, gom yêu cầu, timer hủy được, account/auth/playlist epoch + generation; dùng lại `SyncActionsPort`, outbox và giao thức cũ.
- `PlaylistSyncForeground` theo dõi Activity resumed của ứng dụng, gồm Cài đặt. Không giữ native client sống, không WorkManager/service/receiver mới. Rời app không còn lịch polling.
- `SyncRuntime` sở hữu bộ điều phối và listener; `TelegramPreviewActivity` kích hoạt sau mở playlist/lưu thành công, cập nhật live/status; unlisten khi pause/destroy.
- `MeloSyncSettingsActivity` dùng một luồng bật/tắt, xem pending, Đồng bộ ngay. Bỏ giao diện cấp lượt thủ công; helper controller cũ giữ để tương thích/hồi quy, không còn là luồng màn sản phẩm.
- Bản xem pending gắn account/auth epoch/playlist epoch/opCounter và chính xác danh sách outbox; thay đổi giữa lúc xem và xác nhận thì từ chối bật. Metadata lấy từ index local qua ứng dụng, không ghi log tên/mã bài.
- Setup chỉ lưu quyền đọc tạm trước discovery; chỉ cấp quyền gửi sau kênh hợp lệ và kiểm lại bản xem trước. Lỗi ghi quyền gửi không báo bật thành công. Lỗi ghi trạng thái tắt vẫn chặn tài khoản đó trong tiến trình, kể cả đổi auth epoch hoặc A→B→A; có nút thử lưu trạng thái tắt lại.
- `TdlibPlaylistSyncService` kiểm foreground và cancellation token ở cửa vào, trước gửi và trước cập nhật host. Dùng lại read-before-send/backoff; unknown delivery không tự gửi lại. Không tạo kênh; không thay đổi owner/session storage.
- Giữ giới hạn 3 thao tác/lượt; tự nối lượt khi có tiến triển. Tiếp tục full replay còn dang dở theo checkpoint cũ; không tự khởi tạo replay mới. FLOOD_WAIT discovery/run không bị nút thủ công hoặc pause/resume bỏ qua.
- Cảnh báo 2→3 qua trường sẵn có, định dạng `mvconsent v1` giữ nguyên. Không đổi schema playlist/SQLite/songKey/package/signing/versionCode 121.
- Thêm **24 test**: 22 scheduler/setup/convergence và 2 service thật trên TDLib giả. Bao phủ >3 thay đổi, hai chiều thêm/bỏ/thêm lại, hai tap, stale pending review, bind, lỗi lưu đầu/cuối, consent v2, foreground, auth race, hủy giữa history, backoff/unknown delivery, replay cũ, đóng playlist không tự xếp lại lịch và callback playlist-ready của phiên mới không bị mất.
- Cập nhật các assertion cũ về lời nhắc thủ công/cảnh báo v2 và danh sách entry point được phép để phản ánh kế hoạch mới. Không thêm skip, không bỏ kiểm tra an toàn/gửi trùng.

## 3. Kiểm chứng Ni

**A** = `D:\Bots\MeloVault-E91-Shareable\docs\plans\artifacts\E105-DONG-BO-PLAYLIST\auto-20261028-142343` (ignored, private).
APK/XML có sẵn được giữ trong A/preexisting-* trước build; mỗi ma trận mới được sao lưu riêng trước chuyển private→open.
Source cuối: 18 tệp Kotlin trong A/source-scope-final-sha256.json; commit chứa source được ghi vào A/source-commit.txt sau commit và đối chiếu manifest.

| Ma trận cuối | Tổng test | Thành công | Bỏ qua | Failure/error | Build |
|---|---:|---:|---:|---:|---|
| Riêng Debug | 2168 | 2147 | 21 | 0/0 | Thành công |
| Riêng Release | 2168 | 2147 | 21 | 0/0 | Thành công |
| Open Debug | 2168 | 2147 | 21 | 0/0 | Thành công |
| Open Release | 2168 | 2147 | 21 | 0/0 | Thành công |

Lệnh, một Gradle owner, tuần tự:
1. `./gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain`
2. `./gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease :app:reportTelegramConfig --console=plain`
3. Hai lệnh trên lặp với `-PmeloOpen=true` cho open.

Lượt private-01: 3 lỗi assertion cũ (cảnh báo v2/không tự gửi thêm); 16 test mới đều thành công.
Lượt private-02: 3 lỗi câu chữ cũ về trạng thái/đề nghị tạo kênh; 18 test mới đều thành công.
Lượt private-03: Debug/Release thành công, 2164 tổng/2143 thành công/21 bỏ qua/0 lỗi.
Lượt private-final và ma trận guard-final (riêng/open) đều thành công, 2166 tổng/2145 thành công/21 bỏ qua/0 lỗi; 22 test mới không bỏ qua/lỗi.
Sau rà soát thêm đường đóng phiên, bổ sung 2 test và chạy lại ma trận scope-final: đây mới là kết quả cuối ở bảng. Không sửa code sau lượt này.
Log/XML từng lượt được giữ; riêng scope-final test 5m04s/build 2m22s; open test 3m31s/build 1m15s; bốn exit=0. Thống kê A/scope-final-test-summary.json. Toàn bộ 24 test mới không skip/lỗi trong cả bốn biến thể. Không coi XML Release tồn từ lượt lỗi Debug là lần chạy mới.
`git diff --check` sạch; đã đối chiếu SHA-256 18 source với manifest sau test/build. Không sửa Gradle/manifest/quy trình/file nghiệm thu. Kiểm lại HEAD vẫn đúng baseline trước commit.

## 4. APK QA cần Opus nghiệm thu bàn giao

APK Ni chỉ là artifact build nội bộ, **chưa đủ điều kiện cài QA Telegram**: config nhúng=false; signer Ni khác cert Tiny ghi trong biên bản trước. Không cài hoặc upload các APK này.
Tất cả vc121, ABI arm64-v8a. Bản riêng `app.melovault.player / 0.6.96-beta`; open `app.melovault.player.open / 0.6.96-open`.
Cert Ni SHA-256: `a9b8cb6b5b0bc0400955b24dbc85801f5e8855356287caca2dcf3bb4e13db280`.
A/scope-final-apk-metadata.json chứa đường dẫn tuyệt đối, bytes, SHA-256, cert, package/version, config boolean và source manifest của bốn APK cuối. Các APK vòng trước giữ riêng, không dùng thay cho source cuối.

| APK dưới A | Bytes | SHA-256 |
|---|---:|---|
| private-scope-final-apks/debug/app-shareable-debug.apk | 33478596 | 0B88EC1FA0916B74B9C536EBDEAFDE61CFDABB845EA4A771AC4AA022CD36F10E |
| private-scope-final-apks/release/app-shareable-release.apk | 30703982 | 026699D923954FC98940685F86B8E208841C2FF848C92A71F4504F72F131F8B3 |
| open-scope-final-apks/debug/app-shareable-debug.apk | 33478600 | 8CE018EB17DA0A0AC49FB78E10D103D3465BE5088B5C7248BF7250ACD85917F2 |
| open-scope-final-apks/release/app-shareable-release.apk | 30703990 | 33C2B349ADFEE6DF76139AA55330BD76F60713F2D11234B77B6498C997C2AB12 |

`aapt dump badging` xác minh package/version/ABI; `apksigner verify --print-certs` xác minh chữ ký; metadata ở trên chỉ lấy từ artifact mới đã sao lưu.
Phiên QA sau build Tiny trong clone mới từ Git bundle SHA-256, cấu hình/chữ ký đúng theo quyền đã ghi trong hồ sơ E105; không chép bí mật về Ni. Không tự đổi vc/signer để cài.

## 5. Chưa kiểm chứng / bàn giao QA

- Chưa ADB/cài/Tiny/cloud; không bật/tắt màn hoặc sửa playlist thật. `SHANLING_SCREEN_END=NOT_RECHECKED`, `SONY_SCREEN_END=NOT_RECHECKED`.
- Hai pending Shanling và bài thử khôi phục trong biên bản trước chưa được xem lại. Phiên QA xem **Xem thay đổi chờ gửi**, đối chiếu đúng nguồn/tin; không gửi hoặc xóa mù quáng.
- Agent QA thực hiện 5 bước đầu kế hoạch: bật một lần → Shanling thêm/Sony nhận → Sony bỏ/Shanling nhận rồi thêm lại → Sony ra nền/mở lại → chờ hai chu kỳ, mở lại và tắt sync.
- Mốc nghiệm thu: ≤30 giây/thay đổi đơn khi mạng bình thường và Telegram không giới hạn; không bấm Đồng bộ ngay để chứng minh tự động; tập nền nguyên, không trùng/mất bài, pending đã xác nhận hết. Chụp/quay hai máy, finally tắt và xác minh màn OFF.
- Open cần cấu hình/đăng nhập hợp lệ và QA thật; test JVM không chứng minh Telegram, UI thật, thời gian ≤30 giây hay hoạt động trên Sony/Shanling. Không suy biến thể chưa thử từ biến thể khác.
- Chưa có ảnh mới/00_tong_hop.png mới vì đây là phiên code. Giữ ảnh/artifact cũ; không dùng chúng làm chứng cứ tự đồng bộ mới.
- Rollback: tắt tự đồng bộ giữ playlist/outbox/kênh. APK rollback phải đúng package/cert, vc không giảm; cảnh báo v3 khiến APK cũ chưa hiểu v3 không được kế thừa quyền gửi.
- Không sửa tài liệu nghiệm thu/quy trình, không nghiệm thu, merge, push hoặc upload APK. Lỗi Drive cũ thuộc bàn giao riêng, không dùng làm kết luận đồng bộ đúng/sai.

Đã dừng. Mở phiên Codex mới cho phiên 2 (build + QA), cùng model.
