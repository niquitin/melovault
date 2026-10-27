# E105 — Nghiệm thu độc lập

Nghiệm thu: Codex gpt-6-astra medium, phiên độc lập 27/10/2026 (+07).
Ngoại lệ riêng E105 do chủ dự án duyệt trong prompt: thay Opus để kiểm toán và viết biên bản này; không đổi quy trình gốc.

## 1. Kết luận

**REWORK** — hai lỗi xác định tại mục 4. Các gate QA thiết bị, dữ liệu và chủ dự án tại mục 5 vẫn **BLOCKED** độc lập; sửa code không tự gỡ các gate này.
Chưa đủ điều kiện PASS, merge hoặc bàn giao APK. Không sửa production, không ghi cloud, không cài APK, không upload/push/merge trong phiên này.

## 2. Git, phạm vi và phương pháp

- Nhánh `dot/E105-DONG-BO-PLAYLIST`; HEAD kiểm toán `747520dde46ebece3520cbb6e9c2123ba06d24a7`.
- Baseline `9adeeba6c367a05194c36d8d6df6cbfb8f16efe9`; kế hoạch `72c1e1e`; production `a199fe6`; test `1a4c0b4`; báo cáo phiên 1 `31f127a`, phiên 2 `747520d`.
- Đầu phiên 22:49: đúng branch/HEAD, diff check sạch; commit bàn giao cách khoảng 8 phút; không thấy Java/Gradle chạy. Có tiến trình Claude tồn tại, nhưng không có bằng chứng chúng đang ghi repo. Các lần kiểm Git tiếp theo giữ nguyên HEAD/cây tracked.
- Sau thông báo gián đoạn hạn mức, kiểm lại 22:58: HEAD/branch không đổi; chỉ các tiến trình Java của lượt kiểm này. `.codex-remote-attachments/` là ảnh đã biết, giữ nguyên, không add/xóa/sửa.
- Đã đọc CLAUDE mục 0 trước, AGENTS, cập nhật đầu HANDOFF, BACKLOG, quy trình, kế hoạch, báo cáo cả hai phiên và PROMPT_OPUS_NGHIEM_THU.
- Diff gồm 9 file production, 10 file test, kế hoạch và báo cáo. Các file đúng phạm vi; vc121 là ngoại lệ đã được chủ dự án duyệt. Không thấy đổi package/signer/schema/songKey/owner/session, worker hoặc tự gửi nền trong diff.
- Đọc kỹ flags, consent, controller, UI, các test thay đổi và đường service/transport liên quan. Không coi báo cáo thực thi là kết luận.
- Đã xem ảnh thật `00_tong_hop.png` trước, sau đó ảnh gốc 35, 38, 41. Không làm QA thiết bị mới vì trạng thái dữ liệu đang bị chặn; không suy ra tài khoản hiện tại từ bộ đếm cũ.

Quy ước đường dẫn, đều dưới `docs/plans/artifacts/E105-DONG-BO-PLAYLIST/`:

- **A** = `audit-20261027-224957/`: log/XML/APK/probe của kiểm toán, Git ignore.
- **S2** = `session2-20261027-1736/`: chứng cứ phiên 2, giữ nguyên.
- APK và XML có sẵn trong build outputs được sao lưu tại `A/preexisting-outputs/` trước khi chạy; kết quả mỗi ma trận lưu riêng.

## 3. Test/build Ni và APK

Chạy tuần tự, một Gradle owner. Bản thường chạy lại thật bằng `--rerun-tasks`:
`./gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest :app:assembleShareableDebug :app:assembleShareableRelease --console=plain --rerun-tasks`
Lượt open dùng cùng bốn task, thêm `-PmeloOpen=true`, không thêm `--rerun-tasks`; kiểm log để phân biệt task test thực thi với UP-TO-DATE.

| Biến thể | Tổng | Thành công | Bỏ qua | Failure/error | Build |
|---|---:|---:|---:|---:|---|
| Thường Debug | 2135 | 2114 | 21 | 0/0 | Thành công |
| Thường Release | 2135 | 2114 | 21 | 0/0 | Thành công |
| Open Debug | 2135 | 2114 | 21 | 0/0 | Thành công |
| Open Release | 2135 | 2114 | 21 | 0/0 | Thành công |

- Thường: `A/private-gradle.log`, exit=0, BUILD SUCCESSFUL 6m06s, 109 task thực thi; XML tại `A/private-test-results/`, tổng hợp `A/private-test-summary.json`.
- Open: `A/open-gradle.log`, exit=0, BUILD SUCCESSFUL 3m24s, 42 task thực thi/67 up-to-date; hai task unit test thực thi thật, không UP-TO-DATE. XML tại `A/open-test-results/`, tổng hợp `A/open-test-summary.json`.
- Bảy test `e105_*` đều chạy, không bỏ qua ở cả bốn biến thể. Số dòng khai báo @Test trong source tăng 2128→2135; không giảm test so với baseline. Diff không thêm skip/loại bỏ assertion an toàn để né lỗi.
- Test giả kiểm ADD/REMOVE, consent mới, budget và cổng QA là bằng chứng JVM; không thay thế TDLib/UI/âm thanh thật.
- Đã tự tính SHA-256/bytes, chạy aapt và apksigner verify trên **cả bốn APK Tiny lưu tại S2**: khớp hoàn toàn báo cáo, verify exit=0, ABI arm64-v8a, vc121.

| APK S2 (tiền tố `E105-31f127a-vc121-`) | Package / versionName | Bytes | SHA-256 |
|---|---|---:|---|
| private-debug.apk | app.melovault.player / 0.6.96-beta | 33462212 | 66506738EBFDD50C7FA24BD9A65D83AEABA228D989BEFF8FA29313534BCC1B8A |
| private-release.apk | app.melovault.player / 0.6.96-beta | 30703982 | 5124B7F4261FF59E32FCBA9E5785C92970C4B1CA3A029C32AE7272F9A77DE8EC |
| open-debug.apk | app.melovault.player.open / 0.6.96-open | 33462216 | 19DF0328BC889EE1EF44563AC74031AEADB34864ED62D43863CA304B1EBE52EF |
| open-release.apk | app.melovault.player.open / 0.6.96-open | 30703990 | B85B765CFF89DF9FFA4E508B08EC799A71C6A31C39889A02F6039C8286158722 |

- Cert cả bốn: `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`; chi tiết độc lập `A/candidate-verification.json`.
- Bundle Ni tự hash khớp `50CAD9CD3AC72B3D9F5024E07ED2C1387192D2C7E687936D96B980E90A90B2D0`. Không truy cập Tiny trong phiên này; hash đầu Tiny/source build dựa chứng cứ bàn giao, không tuyên bố đã tái kiểm trực tiếp máy Tiny.
- Đọc kết quả boolean trong log Tiny: config thường=true, open=false; kiểm hash nhúng open Debug/Release found=false; đối chứng thường found=true và exit=1 là kết quả âm tính có chủ đích. Không đọc/in/hash secret; không chạy lại phép kiểm cần secret trên Ni.
- Chưa tự xác minh runtime config/auth/cert đang cài trên thiết bị trong phiên này. Không dùng APK build Ni thay candidate Tiny hoặc coi metadata APK là QA chức năng.

## 4. Lỗi cần REWORK

### R1 — P2: chưa đăng nhập nhưng báo đang đọc vô thời hạn

- `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloSyncSettingsActivity.kt:97`: account=null gọi evaluate(0, null, ...) rồi return, không xếp công việc đọc.
- `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/PlaylistSyncStatusPolicy.kt:121`: facts=null luôn trả LOADING / “Đang đọc Playlist…”. Không có thông báo cần đăng nhập ở nhánh này.
- Ảnh thật `S2/35-shanling-open-sync-locked.png` xác nhận nhãn đó trên open chưa đăng nhập; nút bị khóa đúng. Đây là lỗi hiển thị, chưa có bằng chứng tự gửi.
- Cần phân biệt chưa đăng nhập với đang tải; nhãn/hướng dẫn đúng và giữ mọi cổng khóa. Thêm kiểm hồi quy account=null, loading thật và account đã sẵn sàng; QA lại open chưa đăng nhập. Không nhập API/OTP trong phiên sửa nếu chưa có chỉ dẫn phù hợp.

### R2 — P1: kết quả kênh không an toàn bị che và không chặn yêu cầu đồng bộ

- `SyncSettingsController.kt:347` lưu kết quả discovery, nhưng `:237` luôn thay bằng Bound khi facts.channelBound=true; `:235`/`:236` chỉ xét opted/bound/running để mở đồng bộ/đọc lại.
- Đường `TdlibSyncChannel.kt:276` có thể trả Unsafe/Failed khi xác minh lại kênh đã gắn. `MeloSyncSettingsActivity.kt:228` không hiện toast cho ActionResult.Done, nên kết quả này không được báo bằng đường khác.
- Test `SyncSettingsControllerTest.kt:285` kiểm các kết quả discovery nhưng đặt channelBound=false tại dòng 297, bỏ sót trường hợp đã gắn.
- Tái hiện độc lập bằng `A/AuditProbe.java` trên class vừa biên dịch: dùng controller thật, kho/facts/actions giả sẵn trong test, đặt cổng tạo=false. Không gọi TDLib, không dùng dữ liệu người dùng.
- Kết quả `A/probe-result.txt`: `discovery=Unsafe(PUBLIC); channel=Bound; syncNow=AVAILABLE`; sau grant 1 và sync: `fakeCalls=[discover, run]; fakeBudgets=[1]`.
- Chứng minh UI/controller chuyển yêu cầu xuống port sau khi đã biết kết quả không an toàn; **không** chứng minh metadata đã được gửi ra kênh thật. Không quy kết kênh của chủ dự án là public/unsafe. Nhãn Unicode ở dòng cuối probe bị lỗi mã hóa console; không dùng dòng đó làm chứng cứ R1.
- Cần ưu tiên hiển thị kết quả kiểm kênh mới, chặn cấp lượt/chạy đồng bộ khi kết quả không an toàn, chưa đầy đủ, mơ hồ hoặc lỗi; chỉ mở lại sau xác minh hợp lệ. Kiểm cả gọi trực tiếp controller, không chỉ ẩn nút. Giữ cổng service/transport, không mở tạo kênh hoặc đổi schema/giao thức.
- Thêm hồi quy channelBound=true cho Unsafe/Failed/Incomplete, trạng thái hợp lệ và luồng khôi phục sau tìm lại; bảo đảm không gọi run hoặc tiêu lượt gửi ở nhánh bị chặn.

Cả hai đường lỗi có từ mã nền E96; E105 mở cho bản sản phẩm và yêu cầu trạng thái/cổng an toàn tại S3–S4. Không coi là thay đổi ngoài nhiệm vụ để bỏ qua. Chưa sửa trong phiên nghiệm thu.

## 5. Gate BLOCKED và điều kiện gỡ

| Gate | Chứng cứ / còn thiếu | Điều kiện gỡ |
|---|---|---|
| Dữ liệu trước QA | Ảnh tổng hợp + 40/41: Shanling 3 bài, 1 pending ngoài bộ thử, quyền metadata bật, grant=0; 38/39: Sony 2 bài, quyền gửi tắt, grant=0 | Chủ dự án làm rõ thay đổi ngoài bộ thử và cho chỉ dẫn xử lý hợp lệ; không tự gửi/xóa/bỏ bài để làm sạch nền |
| Tài khoản/kênh | Ảnh “Đã gắn” không chứng minh kiểm kênh thành công, nhất là R2; khóa bộ đếm cũ không chứng minh tài khoản đang đăng nhập | Kiểm lại auth hiện tại/cùng tài khoản và kênh hợp lệ bằng cách an toàn trước QA; không suy từ bộ đếm |
| Đồng bộ thật | Chưa ADD/REMOVE bài thử, chưa cấp lượt, chưa Đồng bộ ngay/read replay; không có bằng chứng hai chiều E105 | Duyệt bài cụ thể + ADD/REMOVE, nền/outbox phù hợp; chạy đúng 6 bước kế hoạch trên Shanling và Sony, giữ tập nền |
| Open và Release | Open Debug mới chỉ khóa khi chưa đăng nhập; Release chưa QA UI | Runtime config/auth open hợp lệ do chủ dự án xử lý; hoàn thành ma trận thường/open × Debug/Release, không suy từ Debug |
| Thiết bị/màn | Sony từng mất ADB; log cleanup S2 xác nhận OFF sau đó | Khi QA mới: kiểm serial/model/auth/version/cert/playback, finally 223 và xác minh OFF cả hai máy |
| Chủ dự án | Chưa có duyệt chứng cứ hoàn tất; ảnh hiện tại chỉ chứng minh một phần | Hoàn tất REWORK và QA, chủ dự án duyệt đúng phạm vi; không nới tiêu chí |
| Bàn giao | Chưa có link-index/Drive được xác minh trong nghiệm thu này | Theo chỉ dẫn riêng sau này; phiên ngoại lệ hiện tại cấm upload/công bố APK, chỉ chuẩn bị thông tin |

- `SHANLING_SCREEN_END=NOT_RECHECKED`; `SONY_SCREEN_END=NOT_RECHECKED` trong phiên kiểm toán: không ADB, không bật màn hay thao tác thiết bị.
- Chứng cứ **phiên 2** đã đọc: Shanling `mGlobalDisplayState=OFF`, `mScreenState=OFF`; Sony `Display State=OFF`. Không coi log cũ là trạng thái hiện tại.
- Chưa xác nhận âm thanh thật, mạng lỗi/unknown delivery trên máy, lịch sử lớn/Keystore; không lấy build xanh làm chứng cứ thiết bị.
- Báo cáo/log hiện có không cho thấy lệnh cấm, nhưng không phải nhật ký lệnh đầy đủ để chứng nhận tuyệt đối mọi thao tác phiên thực thi.

## 6. Bàn giao và prompt REWORK (chưa thực hiện)

Ảnh chính local/private: [tổng hợp](artifacts/E105-DONG-BO-PLAYLIST/00_tong_hop.png), [open khóa](artifacts/E105-DONG-BO-PLAYLIST/session2-20261027-1736/35-shanling-open-sync-locked.png), [Shanling quyền cuối](artifacts/E105-DONG-BO-PLAYLIST/session2-20261027-1736/41-shanling-permissions-final.png), [Sony quyền cuối](artifacts/E105-DONG-BO-PLAYLIST/session2-20261027-1736/38-sony-final-readonly.png). Đã xem và hiển thị ảnh trong hội thoại; không có SendUserFile trong bộ công cụ, không bịa link Drive.

Prompt theo PROMPT_CODEX mục C, chỉ để chủ dự án bàn giao phiên mới sau khi chọn REWORK:
> MELOVAULT — PHIÊN CODEX REWORK. TASK_ID: E105-DONG-BO-PLAYLIST, nhánh dot/E105-DONG-BO-PLAYLIST. HEAD: commit riêng chứa biên bản này (lấy SHA từ lời kết nghiệm thu và kiểm lại Git, không mặc định 747520d). Model: gpt-6-astra medium. Lỗi cần sửa: mục 4, R1 và R2. Kiểm đầu phiên theo AGENTS.md; chỉ sửa các lỗi này, không tạo nhánh/plan mới. Chạy test/build theo PROMPT_CODEX mục B và QA bị ảnh hưởng khi an toàn; cập nhật báo cáo với “Vòng REWORK 1”, giữ lịch sử. Các gate mục 5 còn nguyên: không tự xử lý pending, không QA ghi cloud trước khi chủ dự án làm rõ. Không sửa biên bản/quy trình, không merge/push/upload. Dừng chờ Claude Opus nghiệm thu; ngoại lệ Codex nghiệm thu lần này không tự gia hạn.

Chỉ commit biên bản này trên nhánh nhiệm vụ rồi dừng; không mở nhiệm vụ kế.
