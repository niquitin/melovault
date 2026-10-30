Thực thi: Codex gpt-6-astra medium, phiên 1

# E105 — Báo cáo phiên 1 (S0–S5)

| Mục | Kết quả |
|---|---|
| Phạm vi | Phương án A, mở đồng bộ thủ công bản riêng và open; không ADB/Tiny/cài APK |
| Nhánh | `dot/E105-DONG-BO-PLAYLIST` |
| Baseline | `9adeeba6c367a05194c36d8d6df6cbfb8f16efe9` |
| Commit kế hoạch | `72c1e1e2f5956a4d62411f1123b007bfb753d155` |
| Commit production/test | `a199fe6d8611a151232d807896b56e65b4d02e8f`; bổ sung test ranh giới open tại `1a4c0b4` (không đổi production) |
| Kiểm đầu phiên | Cây sạch, đúng nhánh/HEAD; commit kế hoạch cách khoảng 13 phút; không có Java/Gradle đang chạy; diff check sạch |
| Phiên bản được chủ dự án duyệt | Shareable `121 / 0.6.96`, giữ hậu tố `-beta` / `-open` |
| Tiến độ | Hoàn tất code, full test và build đủ 4 biến thể trên Ni; chưa QA thiết bị |

## Thay đổi đúng phạm vi

| File production (đều trong kế hoạch) | Thay đổi |
|---|---|
| `app/build.gradle` | Cờ sản phẩm Shareable=true, Personal=false; QA/create vẫn riêng và khóa Release/open; chỉ bump 2 dòng version Shareable |
| `TelegramPlaylistV2Session.kt` | `SYNC_ENABLED` đọc cờ sản phẩm; `SYNC_QA_ENABLED` cần Debug + QA; tạo kênh cần thêm cổng QA |
| `TelegramPreviewActivity.kt` | Chặn fixture và simulation trước khi đọc/xóa marker trong bản sản phẩm |
| `PlaylistQaFixture.kt`, `PlaylistQaSimulation.kt` | Cập nhật mô tả cổng QA |
| `TdlibSyncChannel.kt` | Warning=2; xác nhận mới không kế thừa quyền tạo/gửi; giữ nguyên định dạng `mvconsent v1` |
| `SyncSettingsController.kt` | Cảnh báo thủ công/REMOVE; reset quyền và lượt gửi khi xác nhận; khóa tạo kể cả gọi trực tiếp; NotFound không mời tạo trong sản phẩm |
| `MeloSyncSettingsActivity.kt` | Ẩn quyền/nút tạo trong sản phẩm; giữ tìm/gắn, cấp lượt, đồng bộ và đọc lại lịch sử |
| `MeloUiLabActivity.kt` | Mô tả đồng bộ thủ công qua kênh có sẵn |

Không đổi schema/songKey/giao thức cloud/SyncActionsPort, package, signer, signing config, luồng cấu hình E104 hay runtime/provider. Không thêm gửi nền.

## Test và lý do đổi kỳ vọng

- Thêm 7 test: ma trận cờ theo flavor/QA; consent E96 chưa nhận warning=2 bị khóa; reset quyền cũ; lỗi lưu; định dạng consent không đổi; thu hồi grant khi xác nhận lại; hai máy giả trao đổi ADD/REMOVE và giữ 2 bài nền.
- Test hai máy dùng service/V2 thật với TDLib giả, cổng sản phẩm thật và khóa tạo; budget 0 không gửi, thao tác pending không báo Synced, tắt không đổi state playlist/outbox/checkpoint.
- Giữ các hồi quy budget 1–3, write-ahead, đọc trước gửi, unknown delivery, account/epoch, refresh, không gửi theo Save/resume và cổng service không tạo.
- `SyncBuildGateReworkTest`, `PlaylistStatusFactsTest`, `TdlibOwnerFanOutTest`: kỳ vọng cờ sản phẩm false → true theo S1; kiểm cổng false vẫn giữ bằng dependency giả, mặc định không consent vẫn bị từ chối.
- `P5ReadinessInertnessTest`: dùng provider với build=false để giữ assertion không chạm owner/host; không gọi provider sản phẩm như thể tính năng vẫn tắt.
- `PlaylistQaFixtureTest` / `PlaylistQaSimulationTest`: chuyển assertion sang cổng QA riêng và kiểm short-circuit trước marker.
- `SyncSendGrantTest`: tạo dữ liệu consent QA cũ bằng store giả; thêm assertion `setAllowCreate(true)` bị từ chối, vẫn giữ assertion không tạo kênh dù có quyền cũ.
- `TdlibRework01Test`: lượt đầu lỗi duy nhất do test coi warning=2 là tương lai. Đổi mẫu tương lai thành `WARNING_VERSION + 1`; giữ nguyên assertion từ chối và mọi mẫu hỏng khác. Không xóa/nới assertion an toàn, không thêm skip.
- `E9DistributionBoundaryTest`: open lần đầu lỗi vì test cũ nhầm package open thành Personal. Thêm chính xác `app.melovault.player.open` vào nhánh Shareable, giữ mọi assertion backend=false/endpoint rỗng/token rỗng; thêm assertion package Personal chính xác ở nhánh còn lại. Không đổi production.

## Kiểm thử Ni

Log/XML/APK riêng tư, không commit, tại:
`D:\Bots\MeloVault-E91-Shareable\docs\plans\artifacts\E105-DONG-BO-PLAYLIST\session1-20261027-140719` (gọi tắt ART).

| Lượt | Tổng | Thành công | Bỏ qua | Lỗi | Kết quả |
|---|---:|---:|---:|---:|---|
| Riêng Debug lần 1 | 2134 | 2112 | 21 | 1 | Test warning=2 cũ; đã sửa như trên |
| Riêng Debug lần 2 | 2135 | 2114 | 21 | 0 | Thành công |
| Riêng Release lần 2 | 2135 | 2114 | 21 | 0 | Thành công |
| Open Debug lần 1 | 2135 | 2113 | 21 | 1 | Test phân loại package cũ; đã sửa như trên |
| Open Debug lần 2 | 2135 | 2114 | 21 | 0 | Thành công |
| Open Release lần 2 | 2135 | 2114 | 21 | 0 | Thành công |

Lệnh tuần tự, một Gradle owner; output đầy đủ chỉ lưu log:
1. `./gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain` — `private-tests-01.log` thất bại như trên; `private-tests-02.log` thành công, XML lưu riêng từng lượt.
2. `./gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease :app:reportTelegramConfig --console=plain` — `private-build.log`: BUILD SUCCESSFUL, config=false.
3. `./gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest -PmeloOpen=true --console=plain` — `open-tests.log` lỗi phân loại package; `open-tests-02.log` thành công; XML lưu riêng.
4. `./gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease :app:reportTelegramConfig -PmeloOpen=true --console=plain` — `open-build.log`: BUILD SUCCESSFUL, config=false (đúng bản open).
5. Ba dry-run đều exit=1 đúng gate, trước build: `assembleShareableRelease -PmeloSyncQa=true`; `assembleShareableRelease -PmeloSyncQaAllowCreate=true`; `assembleShareableDebug -PmeloOpen=true -PmeloSyncQa=true`. Log `gate-release-qa.log`, `gate-release-create.log`, `gate-open-qa.log`.
6. `:app:reportSyncQaFlag -PmeloSyncQa=true -PmeloSyncQaAllowCreate=true` thành công: QA=true/create=true; test source kiểm thêm điều kiện BuildConfig.DEBUG. Log `gate-private-qa.log`.
7. Sau sửa test package, chạy lại riêng `E9DistributionBoundaryTest` cho cả Debug/Release bản riêng: mỗi biến thể 1/1 thành công, 0 bỏ qua/lỗi; `private-boundary-final.log`, XML lưu riêng. Không có thay đổi production sau full suite.

Các lượt lỗi không được tính thành công. 21 test bỏ qua có sẵn, không thêm skip; 7 test mới đều chạy ở cả 4 biến thể. Gradle báo deprecation tương thích Gradle 9; không đổi build tool ngoài kế hoạch.

## APK QA cần Opus nghiệm thu bàn giao

Ni không cài APK. APK riêng thiếu Telegram config, chưa đủ điều kiện QA Telegram; không upload/công bố. APK cũ được giữ tại `ART/preexisting-apk` trước build; artifact mới lưu tên riêng trước chuyển ma trận.

Source/HEAD của APK riêng: `a199fe6d8611a151232d807896b56e65b4d02e8f`; APK open: `1a4c0b421b0d121575369d7b32d891179ce06b85`. Production giống nhau, commit sau chỉ sửa test.
Chứng thư Ni (apksigner verify thành công): `a9b8cb6b5b0bc0400955b24dbc85801f5e8855356287caca2dcf3bb4e13db280`.
Chưa đối chiếu cert với app trên thiết bị; không suy ra đủ điều kiện install -r.

| APK (tương đối ART) | Package / version | Bytes | SHA-256 | Config nhúng |
|---|---|---:|---|---|
| `private-apk/E105-a199fe6-vc121-private-debug.apk` | app.melovault.player / 121 / 0.6.96-beta | 33462212 | 88F6337132A33BBC2548DC0BCC272C2F8F60998061B46F4D266E54FC6C0A657E | false |
| `private-apk/E105-a199fe6-vc121-private-release.apk` | app.melovault.player / 121 / 0.6.96-beta | 30703982 | 5DA18E1902C1D5A621B7DE07D9E4662160450F52081F3F5CBC7742DFE9FD04FB | false |
| `open-apk/E105-1a4c0b4-vc121-open-debug.apk` | app.melovault.player.open / 121 / 0.6.96-open | 33462216 | 70A2200DD439E46A53E3F0D772F17E942C3CC37B5974F0E1FD65739A0914A37C | false |
| `open-apk/E105-1a4c0b4-vc121-open-release.apk` | app.melovault.player.open / 121 / 0.6.96-open | 30703990 | 6C0D38F2AA243B59F24AA2B39F81E66EA2338AAB43548A277DBF76C9B2677B24 | false |

Cả 4 file đã xác minh tồn tại, package/version bằng aapt, chữ ký bằng apksigner và SHA-256 trên file lưu riêng. Bản open chưa có runtime config/đăng nhập được kiểm trên thiết bị; kiểm APK không nhúng hash theo E104 thực hiện trên Tiny ở phiên 2 với config nguồn phù hợp. Trạng thái bàn giao: **BLOCKED — CHƯA BÀN GIAO**, chờ build/QA Tiny và Opus kiểm toán; không giới thiệu APK Ni như bản test Telegram dùng được.

## QA thiết bị và bàn giao phiên 2

| Bước kế hoạch | Thiết bị | Thực tế phiên 1 |
|---|---|---|
| 1. Cảnh báo mới, gửi chưa bật, không tạo | Shanling | Chưa thực hiện; không ADB |
| 2. Tìm/gắn, đọc-only | Shanling + Sony | Chưa thực hiện |
| 3. ADD bài được duyệt, nhận máy kia | Shanling → Sony | Chưa thực hiện; chỉ test giả |
| 4. REMOVE bài thử, giữ tập nền | Sony → Shanling | Chưa thực hiện; chỉ test giả |
| 5. Tắt/mở không tự gửi | Shanling | Chưa thực hiện trên máy |
| 6. Lặp riêng/open Debug/Release | Cả hai | Chưa thực hiện |

`SHANLING_SCREEN_END=UNKNOWN`; `SONY_SCREEN_END=UNKNOWN`: phiên 1 không dùng ADB, không bật/tắt hay kiểm màn. Không có ảnh/video/`00_tong_hop.png` vì không QA thiết bị. Phiên 2 phải theo finally + xác minh Display State=OFF; không suy diễn màn hiện đang OFF.

Chủ dự án duyệt riêng ngày 27/10/2026 cho phiên 2: trong clone E105 mới trên Tiny, được `Copy-Item C:\Bots\MeloVault-E99-IsolatedQA\local.properties` theo đúng E104 S11; không đọc/in/hash, không mang về Ni, kiểm bằng `reportTelegramConfig` (không `-PmeloOpen`). Chưa thực hiện trong phiên 1.

Phiên 2 nhận source bằng Git bundle đối chiếu SHA-256 hai đầu, clone Tiny cô lập; build đủ 4 biến thể, kiểm APK/package/version/cert/config. Bản open cần chủ dự án nhập cấu hình/đăng nhập riêng; không copy session. Cần chủ dự án chỉ định bài và duyệt ADD/REMOVE; outbox ngoài bộ thử thì dừng, không xóa. Chỉ cài sau đủ gate, không downgrade. Opus mới được bàn giao APK/ảnh owner-only và nghiệm thu.

## Rủi ro / chưa kiểm chứng

- Chưa QA Telegram thật, UI, hai thiết bị, cấu hình runtime open, âm thanh thật; Gradle/test giả không thay chứng cứ thiết bị.
- Giữ các giới hạn E96 đã nêu trong kế hoạch (unknown delivery, lịch sử lớn, REMOVE nhiều tag, lỗi mạng/Keystore).
- Rollback: tắt đồng bộ giữ dữ liệu/kênh; APK phục hồi phải cùng package/cert và versionCode không giảm. Sau vc121 không tự cài baseline vc120.
- Không sửa file quy trình/nghiệm thu, không merge/push, không upload, không mở nhiệm vụ khác.
- Phiên bị ngắt do hạn mức rồi tiếp tục theo yêu cầu chủ dự án; kiểm lại Git đúng `a199fe6`, chỉ còn test package và báo cáo do chính phiên này tạo. Không có Java/Gradle khác khi tiếp tục.
- `git diff --check` sạch; 9 file production đúng S1–S3, 10 file test liên quan và báo cáo. Commit báo cáo là điểm dừng phiên 1; không sang phiên 2 trong hội thoại này.

## Phiên 2
Thực thi: Codex gpt-6-astra medium, phiên 2

| Nội dung | Kết quả 27/10/2026 (+07), chưa phải nghiệm thu |
|---|---|
| Git | `dot/E105-DONG-BO-PLAYLIST`, đầu phiên `31f127a3bbe98f464bac9504f03cf3c9e53bf6c7`; baseline `9adeeba`, plan `72c1e1e`, production `a199fe6`, test `1a4c0b4` |
| Kiểm đầu phiên | Cây sạch, diff check sạch; Gradle Ni idle từ 17:26:44, Tiny không build khác. Không sửa production/test |
| Sau gián đoạn hạn mức | Tiếp tục lúc 22:32 theo yêu cầu chủ dự án; HEAD không đổi. Chỉ có `.codex-remote-attachments/` chứa đúng ảnh chủ dự án vừa gửi, để nguyên và không commit |
| Build | Đủ thường/open × Debug/Release vc121 trên clone Tiny mới; tất cả build exit=0 |
| Cài | Thường Debug: Shanling 120→121, Sony 119→121; open Debug: Shanling 120→121. Cả 3 lần `install -r` báo Success |
| Điểm dừng | **BLOCKED đồng bộ thật**: Sony mất ADB thoáng qua khi tiếp tục; sau đó phát hiện Shanling có thay đổi ngoài bộ thử trong khoảng gián đoạn |
| Phạm vi hoàn tất | Build, kiểm 4 APK, cảnh báo/consent mới hai máy, open chưa đăng nhập bị khóa, ảnh và báo cáo |

### Bundle, config và build Tiny

- Bundle Ni: `D:\Bots\_transfer\E105-31f127a-20261027-1736\E105-31f127a.bundle`; Tiny: `C:\Bots\_qa_temp\E105-31f127a-20261027-1736.bundle`.
- SHA-256 hai đầu: `50CAD9CD3AC72B3D9F5024E07ED2C1387192D2C7E687936D96B980E90A90B2D0`. Bundle đủ lịch sử; verify thành công trong repo. Lượt verify đầu ngoài repo báo “need a repository”; đã verify lại trong clone, không sửa bundle.
- Clone mới: `C:\Bots\MeloVault-E105-IsolatedQA-20261027-1736`, đúng HEAD/nhánh. Tiny thao tác qua Desktop Commander; HTTP tạm chỉ phục vụ thư mục bundle đã dừng sau chuyển. `T:` chỉ dùng đọc/sao chép artifact về Ni.
- Config: đúng một `Copy-Item` nguồn `C:\Bots\MeloVault-E99-IsolatedQA\local.properties` sang clone mới theo ngoại lệ đã duyệt. Không mở/in/hash nội dung, không lấy từ repo gốc, không mang về Ni. `git check-ignore local.properties` đúng; Git clone sạch.
- `:app:reportTelegramConfig` không cờ open: **true**, exit=0. Build open kèm report: **false** đúng thiết kế. Không dùng giá trị secret làm chứng cứ.
- Chạy Gradle nền bằng `Start-Process -WindowStyle Hidden`; script/log/exit dưới `C:\Bots\_qa_temp\`. Thường: `:app:assembleShareableDebug :app:assembleShareableRelease --console=plain`, BUILD SUCCESSFUL 5m48s. Open: cùng task + `:app:reportTelegramConfig -PmeloOpen=true`, BUILD SUCCESSFUL 1m49s.
- `:app:verifyOpenApkHasNoTelegramHash -PopenApk=<APK>`: open Debug/Release đều `found=false`, exit=0; thường Debug/Release đối chứng đều `found=true`, exit=1 đúng kỳ vọng. `overall.exit=0`.
- Không chạy lại unit test vì không đổi mã/test; bằng chứng phiên 1: mỗi biến thể 2.135 tổng, 2.114 thành công, 21 bỏ qua, 0 lỗi ở lượt cuối. Không tính build Tiny là test thiết bị thành công.

### APK QA cần Opus nghiệm thu bàn giao

Artifact Ni (A): `D:\Bots\MeloVault-E91-Shareable\docs\plans\artifacts\E105-DONG-BO-PLAYLIST\session2-20261027-1736`.
Artifact Tiny (T): `C:\Bots\_qa_temp\E105-31f127a-20261027-1736`. Bốn APK dưới đây đều tồn tại ở A và T; SHA-256 đối chiếu khớp.
HEAD build `31f127a3bbe98f464bac9504f03cf3c9e53bf6c7`; production `a199fe6`; test `1a4c0b4`. ABI cả bốn `arm64-v8a`.
Cert SHA-256 cả bốn: `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`; apksigner verify thành công.

| Tên file tại A/T | Package / vc / versionName | Bytes | SHA-256 | Config nhúng |
|---|---|---:|---|---|
| `E105-31f127a-vc121-private-debug.apk` | app.melovault.player / 121 / 0.6.96-beta | 33462212 | 66506738EBFDD50C7FA24BD9A65D83AEABA228D989BEFF8FA29313534BCC1B8A | true |
| `E105-31f127a-vc121-private-release.apk` | app.melovault.player / 121 / 0.6.96-beta | 30703982 | 5124B7F4261FF59E32FCBA9E5785C92970C4B1CA3A029C32AE7272F9A77DE8EC | true |
| `E105-31f127a-vc121-open-debug.apk` | app.melovault.player.open / 121 / 0.6.96-open | 33462216 | 19DF0328BC889EE1EF44563AC74031AEADB34864ED62D43863CA304B1EBE52EF | false |
| `E105-31f127a-vc121-open-release.apk` | app.melovault.player.open / 121 / 0.6.96-open | 30703990 | B85B765CFF89DF9FFA4E508B08EC799A71C6A31C39889A02F6039C8286158722 | false |

Metadata đầy đủ: `A/apk-metadata.json`; log Gradle/exit: `A/tiny-logs/`. APK cũ giữ riêng `A/preexisting-shanling-*-vc120.apk`, `T/preexisting-sony-private-vc119.apk`; không dùng để downgrade.
Trạng thái: **QA CANDIDATE — CHƯA NGHIỆM THU; CHƯA BÀN GIAO**. Codex không upload/công bố. Opus phải kiểm toán độc lập và bàn giao owner-only theo quy trình. Release chưa cài/QA UI; open chưa có cấu hình runtime/đăng nhập.

### QA thiết bị và chứng cứ

Ni: `30313860180939`, Shanling M3 Ultra/Shanling; Tiny: `QV725WEQ54`, XQ-AS52/Sony; cả hai arm64. Đã kiểm lại ADB/model mỗi đợt, package/vc/cert trước cài. MediaSession trước cài thường không có state=3 (Shanling state=0; Sony state=2/0 và một session lỗi Bluetooth cũ), không phát nhạc trong QA.
Cả hai màn Cài đặt trước cài ghi “Đã đăng nhập”; `melovault_library_counts.xml` chỉ chứa số đếm/thời điểm, mỗi máy có đúng một khóa tài khoản và khớp nhau (đối chiếu riêng tư). Đây là chứng cứ tương quan tài khoản đang dùng với bộ đếm, **không phải phép so sánh trực tiếp hai kết quả getMe trong RAM**. Không đọc dữ liệu TDLib/session/noBackup.

| Bước | Thực tế / trạng thái | Ảnh tại A |
|---|---|---|
| 1. Nền, cảnh báo mới | Trước gián đoạn cả hai đúng 2 bài nền; nhận warning mới trên vc121, quyền gửi vẫn tắt, không nút tạo | `07`, `08`, `16`, `17`, `19`–`22` |
| 2. Kênh, budget 0 | Đã bấm Tìm kênh chỉ đọc; UI vẫn “Đã gắn kênh đồng bộ”, chưa xác minh độc lập kênh hợp lệ/không mơ hồ. Grant=0, chưa bấm Đồng bộ ngay | `23`–`27` |
| 3. ADD Shanling→Sony | **BLOCKED**, chưa chọn/thêm bài thử, chưa cấp lượt, chưa gửi ADD | `37`, `40`, `41` là hiện trạng ngoài bộ thử |
| 4. REMOVE Sony→Shanling | **BLOCKED**, không bỏ bài nào, không gửi REMOVE; không xóa outbox | `39-sony-playlist-final.png` |
| 5. Tắt/mở sync | Chưa thực hiện chuỗi tắt/bật sau sync do blocker; không nhận kết quả gián đoạn là test tự gửi | `38`, `41` ghi quyền/lượt cuối |
| 6. Open Debug | Cài open vc121; panel API trống, không nhập API/số điện thoại/OTP. Vào Cài đặt→Đồng bộ: nút bật/gửi/tìm/gắn bị khóa, grant=0 | `31`, `34`–`36` |

Tên ảnh đầy đủ mang tiền tố số trong A; `00_tong_hop.png` ở thư mục cha A ghép 8 ảnh, 1600×1480; ảnh private không commit/upload.
Tập nền trước: “Trôi Kem (Vmix China) 2026” và “Đặt 1h30 (TD Team) - Nothingg Mix (Trần Anh Vũ) #2.mp3”. Sony cuối vẫn đúng 2 bài; Shanling cuối có hai bài nền **và thêm “Mixtape Chillout Viet Trung - Duc Kenzo”**, tổng 3. Không mất hai bài nền; không thể ghi toàn bộ tập không đổi.

### BLOCKED và giới hạn cần nghiệm thu xem

- Phiên bị hết hạn mức sau ảnh 26/27 (~17:48), tiếp tục 22:32. Agent chưa từng cấp quyền gửi/cấp lượt, chưa bấm Đồng bộ ngay/đọc lại lịch sử, chưa ADD/REMOVE. Sau gián đoạn Shanling đổi bài đang chọn và Playlist; không có chứng cứ xác định người/tiến trình gây đổi, không quy kết nguyên nhân.
- 22:33 Sony mất ADB giữa lần wake/chụp/finally: `device not found`, `29-sony-resume.png` rỗng, kết quả đầu vẫn ON. Giữ file lỗi, không dùng làm ảnh chứng cứ. Chỉ cleanup khi ADB trở lại đúng serial/model; xác minh OFF. Dừng đồng bộ hai máy; ảnh cuối chỉ đọc hiện trạng.
- Shanling 22:37: **1 thay đổi chờ gửi ngoài bộ thử** (`40`); quyền metadata đã bật khác ảnh `21`, nhưng grant vẫn 0 (`41`). Giữ nguyên quyền/hàng đợi/bài, không tự xử lý. Sony cuối consent bật, quyền metadata tắt, grant=0 (`38`). Đây là blocker độc lập với ADB.
- Bản riêng vẫn đăng nhập và hiển thị hai bài nền sau cài open; giới hạn trên khiến chưa thể kết luận “mọi dữ liệu riêng không đổi”. Không suy ra lỗi do open từ việc bài thêm đã xuất hiện sau gián đoạn.
- Open không đăng nhập: status hiện “Đang đọc Playlist…” dù các nút đã khóa (`35`); ghi nhận UI cần Opus xem, không tự sửa mã. Chưa kiểm đồng bộ thật open, Release UI, mạng lỗi/unknown delivery, âm thanh thật.
- Crash buffer cuối Shanling không có dòng AndroidRuntime phù hợp; Sony có hai FATAL của `com.facebook.appmanager` lúc 00:24/16:39, không phải MeloVault. Không thấy crash MeloVault trong buffer còn lưu; không coi là kiểm ANR/toàn bộ lịch sử đầy đủ. MediaSession cuối Shanling state=2/0, không chứng minh âm thanh.
- `SHANLING_SCREEN_END=OFF` lúc 22:38:34: Android 10 trả `mGlobalDisplayState=OFF` và `mScreenState=OFF` (không có chuỗi đúng dạng `Display State=`). `SONY_SCREEN_END=OFF` lúc 22:38:06: `Display State=OFF`. Cleanup dùng keyevent 223 trong finally, không đổi timeout/brightness/AOD/network.
- Rollback chỉ bằng APK cùng package/cert, vc không giảm; không cài lại vc119/120 sau vc121. Không merge/push/upload; không sửa plan/workflow/nghiệm thu. Chỉ commit phần bổ sung báo cáo, giữ nguyên phiên 1 và toàn bộ artifacts.

## Vòng REWORK 1
Thực thi: Codex gpt-6-astra medium, phiên REWORK 1 (27/10/2026, +07)

- Phạm vi được duyệt: R1/R2 mục 4 biên bản; chỉ code/test/build Ni/báo cáo. Không QA thiết bị/Tiny, cài APK, ghi cloud, upload, push hoặc merge.
- Đầu phiên: đúng `dot/E105-DONG-BO-PLAYLIST`, HEAD `4c7b2a631d4d0071bd0ed5ad30c87902b79463c4`; baseline/plan như phiên 1. Diff check sạch; chỉ `.codex-remote-attachments/` untracked đã biết, giữ nguyên. Commit cuối lúc 23:02, kiểm lúc khoảng 23:09; nguồn bàn giao xác nhận phiên kiểm toán đã dừng sửa. Gradle daemon cũ idle (build cuối kết thúc 22:59:53); không có build khác.
- R1 (`PlaylistSyncStatusPolicy.kt`): account không hợp lệ trả NEEDS_LOGIN / “Cần đăng nhập Telegram” và hướng dẫn đăng nhập trước; account hợp lệ thiếu facts vẫn LOADING, không lộ facts tài khoản khác.
- R2 (`SyncSettingsController.kt`): liên kết đã lưu không tự mở đồng bộ. Phiên màn hình mới/đóng màn/đổi account hoặc epoch/tắt sync xóa xác minh; chỉ Tìm kênh chủ động và kết quả Found mới xác minh. Tìm lại khóa ngay; Unsafe/Failed/Incomplete/Ambiguous/NotFound hiển thị thay Bound, tiếp tục khóa cấp lượt/sync/replay cả UI và gọi trực tiếp.
- `MeloSyncSettingsActivity.kt`: khóa nút ngay khi nhận thao tác và chặn nhận thêm lần bấm tới khi xong; tránh xếp hàng grant/sync/replay phía sau discovery trên worker một luồng. Đây là phần UI của R2; chưa QA trên thiết bị.
- Generation + account/epoch loại kết quả discovery cũ; kết quả cũ không tắt busy của tác vụ mới. Từ chối không gọi actions.run và không tiêu grant. Tìm thành công không tự gửi hoặc cấp lại grant/quyền; tắt/đổi scope/đóng màn thu hồi grant. Giữ cổng service/transport và versionCode 121; không đổi API công khai/schema/giao thức/package/signer.
- Thêm 9 test (2 status, 7 verification), bao gồm kênh đã gắn, lỗi tìm lại, denial/exception, account sai, busy, late result, close/reopen, turnOff/epoch, login và giữ grant. Cập nhật fixture các test controller/grant/P5/Rework02 để chủ động tìm kênh; giữ hồi quy consent, budget 1–3, chống gửi trùng, backoff và không tự gửi. Không thêm skip.
- Chủ dự án xác nhận **bài thứ ba trên Shanling do mình chủ động thêm**. Ghi nhận bổ sung cho lịch sử phiên 2; không quy kết tự phát sinh. Giữ nguyên bài/quyền/pending; không coi đây là bài thử mặc định. Chưa có chỉ dẫn xử lý pending/bài thử: **QA cloud vẫn BLOCKED**.

Artifact vòng này (R): `D:\Bots\MeloVault-E91-Shareable\docs\plans\artifacts\E105-DONG-BO-PLAYLIST\rework1-20261027-2315` (tên thư mục định danh, không phải mốc bắt đầu build).
APK/XML có sẵn được sao lưu tại `R/preexisting-outputs/`; log/XML/APK mới lưu riêng từng ma trận, không commit artifacts.

### Kiểm chứng REWORK trên Ni

Lệnh tuần tự (một Gradle owner), log dưới R:
1. `./gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain` — `private-tests-01/02/03.log`, XML riêng từng lượt.
2. `./gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease :app:reportTelegramConfig --console=plain` — `private-build.log`.
3. `./gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest -PmeloOpen=true --console=plain` — `open-tests.log`.
4. `./gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease :app:reportTelegramConfig -PmeloOpen=true --console=plain` — `open-build.log`.

Lượt thường 01: Debug 2.144 tổng / 2.122 thành công / 21 bỏ qua / 1 lỗi `Rework02SourceGuardTest` do yêu cầu kiểm epoch tường minh; đã giữ lại phép kiểm đó cùng generation, không sửa/nới test. Release chưa chạy lượt 01 (XML Release đi kèm là cũ, không tính).
Lượt thường 02: cả Debug/Release 2.144 tổng / 2.123 thành công / 21 bỏ qua / 0 lỗi. Lượt 03 chạy lại sau bổ sung khóa nhận thao tác UI và giữ fixture NotFound của test cấm tạo kênh; đây là lượt thường cuối.

| Ma trận cuối | Tổng | Thành công | Bỏ qua | Lỗi | Build |
|---|---:|---:|---:|---:|---|
| Thường Debug | 2144 | 2123 | 21 | 0 | Thành công |
| Thường Release | 2144 | 2123 | 21 | 0 | Thành công |
| Open Debug | 2144 | 2123 | 21 | 0 | Thành công |
| Open Release | 2144 | 2123 | 21 | 0 | Thành công |

Thống kê XML: `R/test-summary.json`; 9 test mới đều không skip/lỗi. Build thường/open mỗi lượt 1m25s; `reportTelegramConfig=false` cả hai. `git diff --check` sạch. Không đổi mã/test sau ma trận cuối ngoài chỉnh thụt dòng UI (không đổi hành vi); build dùng file cuối khớp `R/source-sha256.json` (9 file).

### APK REWORK nội bộ — chưa đủ điều kiện bàn giao QA

Nguồn: HEAD build `4c7b2a631d4d0071bd0ed5ad30c87902b79463c4` + diff REWORK của chính commit chứa mục này; SHA-256 từng source/test tại `R/source-sha256.json`, SHA commit cuối ghi tại `R/source-commit.txt` sau commit và đối chiếu lại. Không dùng APK phiên trước làm kết quả REWORK.
Cả bốn vc121; thường `app.melovault.player / 0.6.96-beta`, open `app.melovault.player.open / 0.6.96-open`; config nhúng=false. `aapt` xác minh package/version và `apksigner verify --print-certs` thành công cả bốn.
Cert SHA-256 Ni: `a9b8cb6b5b0bc0400955b24dbc85801f5e8855356287caca2dcf3bb4e13db280`; khác cert Tiny phiên 2, không cài thay APK thiết bị. Không thay signing config.

| APK dưới R | Bytes | SHA-256 |
|---|---:|---|
| `E105-rework1-private-debug.apk` | 33462212 | 9F00100A4823FC27903ED9F3BDD879A34140C50398DB42716E1D64BD1D715B37 |
| `E105-rework1-private-release.apk` | 30703982 | 1946EF707B17B30558E8831532DD97DE5837D15EA69DD38F1A813CFBAB32DA0D |
| `E105-rework1-open-debug.apk` | 33462216 | 80837888C4C6533E80D8589A253CCD37BFF6AE2A68060D47198A5628AEA6CE80 |
| `E105-rework1-open-release.apk` | 30703990 | D313F7CBF333BBA395E9D5CA37F96C638930894410CB09D213296540A5558CFB |

Chi tiết đường dẫn tuyệt đối: `R/private-apk-metadata.json`, `R/open-apk-metadata.json`. **BLOCKED — CHƯA BÀN GIAO**: không dùng APK Ni để QA Telegram; chưa build Tiny/cài/QA REWORK, không upload/công bố.

### Chưa kiểm chứng / điểm dừng REWORK

- QA R1 open chưa đăng nhập và R2 tìm lại kênh trên UI thật chưa thực hiện; không có ảnh mới, giữ `00_tong_hop.png`/ảnh cũ. Test JVM và build không chứng minh Telegram/TDLib/ADD/REMOVE hai chiều hoặc âm thanh thật.
- `SHANLING_SCREEN_END=NOT_RECHECKED`; `SONY_SCREEN_END=NOT_RECHECKED`: vòng này không ADB, không bật/tắt hay thao tác thiết bị; không lấy log OFF phiên 2 làm trạng thái hiện tại.
- Các gate dữ liệu/pending, tài khoản/kênh, đồng bộ thật, open/Release, thiết bị và chủ dự án trong biên bản vẫn nguyên. Việc xác nhận chủ dự án thêm bài chỉ làm rõ nguồn bài thứ ba, không cho phép gửi pending/ADD/REMOVE/read replay.
- Rollback giữ dữ liệu, không tự đảo cloud; APK phải cùng package/cert và vc không giảm. Không sửa biên bản/quy trình, không PASS/merge/push/upload, không tự mở nhiệm vụ tiếp. Commit code/test/báo cáo rồi dừng chờ Claude Opus nghiệm thu.

- Tiếp tục ngày 28/10/2026 theo yêu cầu chủ dự án sau gián đoạn hạn mức: HEAD/nhánh và 10 file đã stage giữ nguyên; không Java/build khác. Đối chiếu lại 9 source/test với manifest và index, 4 APK với hash/bytes, 4 exit test/build cuối đều 0. Không cần chạy lại vì không đổi code/test; chỉ bổ sung ghi nhận này rồi commit. Ảnh mới trong `.codex-remote-attachments/` giữ nguyên, không add.
