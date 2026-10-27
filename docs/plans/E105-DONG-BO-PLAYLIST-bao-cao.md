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
