# E105 — Mở đồng bộ Playlist thủ công trên Beta riêng và open

Lập kế hoạch: Codex, phiên 27/10/2026; chủ dự án giao nhiệm vụ LỚN và giao Codex lập kế hoạch thay Opus.
Model/effort cho phiên thực thi mới: gpt-6-astra medium. Opus nghiệm thu độc lập.
Trạng thái: chủ dự án đã chọn phương án A và mở cùng bản open trong hội thoại; chưa thực thi code/test/QA.

## 1. Hiện trạng, Git và quyết định

- Baseline tích hợp: `feature/e93-03d-y700-offline-cockpit` @ `9adeeba6c367a05194c36d8d6df6cbfb8f16efe9`, chứa merge E104 `fc1e62f` và E96 qua E98 `98662c7`.
- Nhánh công việc: `dot/E105-DONG-BO-PLAYLIST`, tạo từ đúng baseline trên. Commit kế hoạch: lấy SHA commit chứa file này từ Git và ghi vào prompt thực thi, không dùng SHA giả hoặc tự tham chiếu trong file.
- Đầu phiên khảo sát: cây sạch, diff check sạch, checkout E104 @ `d8029d8dc1e96de75d17bd5e466e7c3a2d3bda05`; chênh với tích hợp chỉ HANDOFF. Không có commit vài phút gần đây hoặc tiến trình Java/Gradle lúc kiểm lại S0.
- Đọc CLAUDE.md mục 0 trước, AGENTS.md, mục đầu HANDOFF trên baseline, BACKLOG, QUY_TRINH_3_VAI_TRO.md, PROMPT_OPUS.md, PROMPT_CODEX.md và báo cáo/mã E96.

| Phần E96 | Bằng chứng và giới hạn |
|---|---|
| P0–P1: bảo vệ playlist, V2, hòa giải V1/V2, giữ bài thiếu metadata | Có mã và test JVM; không tự ghi đè dữ liệu không đọc được. V2 là nguồn chính, V1 là bản chiếu. |
| P2–P3: hòa giải nhiều máy, lịch sử, outbox, TDLib, quyền và kiểm tra kênh | Có mã và test; đã sửa bỏ sót trang, báo hoàn tất sai, gửi lại khi kết quả chưa chắc chắn. |
| P4–P5A: Cài đặt, quyền riêng biệt, đồng bộ thủ công | Đã nối ứng dụng; tối đa 3 thao tác mỗi lượt, cấp quyền từng lượt; chưa có đồng bộ nền. |
| P5B3 FINAL LIVE QA | Báo cáo ghi Sony ADD bài tổng hợp → Shanling nhận; Shanling REMOVE → Sony nhận, không restart; giữ hai bài gốc. |
| Nghiệm thu P5B3 | Báo cáo cuối chỉ ghi PASS_CANDIDATE, chờ nghiệm thu độc lập. Chưa tìm thấy biên bản xác nhận PASS riêng trong repo; merge cùng E98 không thay chứng cứ nghiệm thu. |

Nguồn: các `docs/plans/E96-*-bao-cao.md`, đặc biệt `E96-P5B3-FINAL-LIVE-QA-bao-cao.md`, REWORK-01/02/03/04/05 và `E96-P5B-approval-checklist.md`. Phiếu P5B là lịch sử, không coi các ô chưa ký là quyền mới cho E105. FINAL LIVE QA mới hơn REWORK04/05 đã có bằng chứng delta thật, không chỉ mô phỏng.

Hiện `SYNC_ENABLED` đọc `MELO_SYNC_QA_BUILD`: Debug chỉ bật khi `-PmeloSyncQa=true`, Release luôn tắt. Open cấm kết hợp cờ QA. Cờ này còn mở `PlaylistQaSimulation` và `PlaylistQaFixture`; không được bật nguyên xi cho sản phẩm. Cài đặt E102 vẫn mô tả đồng bộ chưa bật.

Giới hạn: mất mạng/5xx trên máy thật, lịch sử lớn, lỗi Keystore/phục hồi dữ liệu hỏng chưa chứng minh đầy đủ. Unknown delivery có thể bị chặn lâu dài để tránh gửi trùng. Báo cáo P2/P3 còn giới hạn REMOVE >32 tag, quét >20.000 tin, discovery lớn và chưa có snapshot. Không sửa các giới hạn đó trong E105. FYT/Y700/S25 và âm thanh thật ngoài phạm vi.

| Phương án | Lợi ích | Hạn chế/rủi ro dữ liệu |
|---|---|---|
| A — Kênh có sẵn, thủ công | Tận dụng đường đã QA; không phát sinh kênh; thay đổi ít hơn | Người chưa có kênh hợp lệ chưa dùng được. REMOVE từ máy khác được áp dụng vào danh sách; cần giữ tập bài nền và chỉ thử bài đã duyệt. |
| B — Thêm tạo kênh cho người mới | Hoàn chỉnh bước bắt đầu | Cần duyệt tạo kênh riêng, QA chống tạo trùng/chọn nhầm; nguy cơ chia tách danh sách. |
| C — Khép nghiệm thu E96 trước | Giữ nguyên bản thường | Chưa đáp ứng dùng đồng bộ ngay; không phát sinh ghi cloud mới. |

**Khuyến nghị A. [CẦN CHỦ DỰ ÁN CHỌN] — ĐÃ CHỌN A trong hội thoại 27/10/2026, đồng thời chọn mở cả bản open.**
Mục tiêu: riêng/open, Debug/Release đều có tính năng; mặc định chưa đồng ý; chỉ chạy khi bấm. Không tạo kênh trong bản sản phẩm, không đồng bộ nền, không nâng 3 thao tác/lượt. Không xóa playlist local; chỉ áp dụng thao tác thêm/bỏ hợp lệ theo giao thức sẵn có khi người dùng chủ động đồng bộ.

## 2. Phiên 1 — code + test Ni

### S0 — Git và hồ sơ
- Kiểm lại status, branch, HEAD, log -5, diff --check và agent/build. Sai baseline/cây bẩn ngoài phiên/agent đang ghi → STOP.
- Phiên lập kế hoạch chỉ tạo nhánh và commit file này. Phiên thực thi là hội thoại Codex MỚI, xác minh SHA kế hoạch và baseline trước code; không đổi vai trong phiên lập kế hoạch.

### S1 — Tách tính năng khỏi QA
- `app/build.gradle`: thêm `MELO_PLAYLIST_SYNC_ENABLED=true` cho Shareable, mặc định false cho Personal; cả Debug/Release và `-PmeloOpen`.
- Giữ `MELO_SYNC_QA_BUILD`/`MELO_SYNC_QA_ALLOW_CREATE` riêng, Release false; yêu cầu QA cùng Release báo lỗi; giữ cấm open + meloSyncQa. Chỉnh comment/thông báo cũ nói Release luôn tắt cloud để diễn đạt đúng: Release tắt QA, không tắt tính năng mới.
- `TelegramPlaylistV2Session.kt`, object `TelegramPlaylistFlags`: `SYNC_ENABLED` đọc cờ tính năng; thêm `SYNC_QA_ENABLED` chỉ đúng khi Debug và QA bật; `SYNC_CREATE_ALLOWED` phụ thuộc QA riêng và quyền tạo QA, không suy ra từ tính năng sản phẩm.
- `TelegramPreviewActivity.runQaFixtureIfRequested` và đường gọi `PlaylistQaSimulation`: dùng `SYNC_QA_ENABLED`, short-circuit trước mọi đọc/xóa marker khi bản sản phẩm. Cập nhật mô tả trong hai lớp QA cho đúng cổng.
- Không đổi package, signer, signing config, versionCode 120/versionName hiện tại; không sửa manifest.

### S2 — Đồng ý mới, không kế thừa quyền QA âm thầm
- `TdlibSyncChannel.kt`: `SyncConsent.WARNING_VERSION` 1 → 2; giữ `mvconsent v1`, tên file, các trường.
- `SyncPrivacyText.WARNING_LINES` trong `SyncSettingsController.kt`: nói rõ thủ công, REMOVE máy khác được áp dụng, chỉ dùng kênh đã có, không tự tải/phát nhạc; giữ cảnh báo không mã hóa đầu-cuối và chỉ metadata.
- `SyncSettingsController.enable` và `SyncConsentActions.enable`: chấp nhận cảnh báo mới bật enabled nhưng đặt quyền tạo/gửi false; không mang quyền cũ sang. UI không được biểu diễn quyền gửi cũ là đang hoạt động khi warning chưa hợp lệ.
- Consent v1 không đủ điều kiện chạy; chỉ ghi v2 sau xác nhận. Không ghi lại playlist/outbox/checkpoint khi nâng consent.
- Không đổi schema playlist/SQLite/giao thức/songKey/owner/session. Đây là phiên bản cảnh báo bằng trường có sẵn; nếu thực thi cần đổi schema thì STOP, trình migration và **[CẦN DUYỆT]** trước sửa.

### S3 — Màn hình và cổng tạo kênh
- `MeloSyncSettingsActivity.render`: bản sản phẩm ẩn quyền/nút tạo kênh, hiện thông báo chỉ tìm/gắn kênh có sẵn; giữ tìm, gắn, cấp 1–3 lượt, đồng bộ ngay, đọc lại lịch sử.
- `SyncSettingsController.screen`, `setAllowCreate`, `createChannel` và cổng service tiếp tục khóa tạo dù gọi ngoài UI khi channelCreationUnlocked=false.
- Đổi nhãn gửi "lần đầu" thành quyền gửi metadata. Trạng thái NotFound trong bản sản phẩm không được mời tạo kênh; Incomplete/Ambiguous/Unsafe/Failed giải thích rồi dừng, không chọn đại/hướng dẫn sửa marker.
- `MeloUiLabActivity` dòng cài đặt đồng bộ: thay câu "Đồng bộ đám mây chưa bật" bằng "Đồng bộ thủ công qua kênh Telegram có sẵn".

### S4 — Open
- Giữ luồng cấu hình/đăng nhập E104; không nhúng mã Telegram, không copy session/dữ liệu từ gói riêng. Chưa đăng nhập thì đồng bộ khóa. Cùng tài khoản sau đăng nhập tìm/gắn kênh bằng luồng hiện có.
- Không đổi `SyncActionsPort.run(..., sendBudget=0)` hay định dạng cloud; không thêm API công khai, worker/timer hoặc tự gửi theo Save/resume. Giữ `SyncRuntime.provider` và cổng account/epoch hiện có.

### S5 — Test, build, báo cáo, commit
Test JVM mới hoặc mở rộng:
1. Ma trận riêng/open × Debug/Release: tính năng true, QA/create false; QA Debug riêng vẫn có cổng riêng, tổ hợp bị cấm bị từ chối.
2. Marker QA không tác động trong sản phẩm (không đọc/xóa/toggle), kể cả marker cũ còn từ E96.
3. Consent mới/E96: chưa nhận v2 không chạy; nhận mới không tự cấp tạo/gửi; lỗi lưu không báo thành công.
4. Mở màn/resume/Save không tự gửi; budget 0 không gửi, budget 1–3 không vượt mức; gọi tạo ngoài UI bị từ chối.
5. Tắt giữ playlist/outbox/checkpoint; account/epoch cũ không áp dụng kết quả hay phục hồi grant.
6. Hai máy giả ADD/REMOVE một bài, giữ tập nền; pending/unknown không báo Synced; giữ hồi quy write-ahead, đọc trước gửi, chống gửi trùng.

Cập nhật `SyncBuildGateReworkTest`, `P5ReadinessInertnessTest`, `PlaylistStatusFactsTest`, `TdlibOwnerFanOutTest`, `PlaylistQaSimulationTest`, `PlaylistQaFixtureTest` và test consent/controller liên quan theo hành vi mới. Giữ kiểm cổng tắt bằng dependency giả; không xóa assertion an toàn hay thêm skip để xanh.

Chạy tuần tự, một Gradle owner:
```
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest -PmeloOpen=true --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease -PmeloOpen=true --console=plain
```
Lưu log/XML mỗi ma trận trước lượt sau, chỉ ghi tổng kết tests thành công/bỏ qua/thất bại. Lưu APK riêng tên duy nhất trước biến thể tiếp theo, không ghi đè artifact bàn giao cũ. Ni không cài APK. Gradle xanh không thay QA thật.
Báo cáo `docs/plans/E105-DONG-BO-PLAYLIST-bao-cao.md` đầu dòng `Thực thi: Codex gpt-6-astra medium, phiên 1`, theo PROMPT_SONNET.md. Commit code/test/báo cáo, dừng; không sang Tiny trong phiên 1.

## 3. Phiên 2 — Tiny, QA hai máy, bàn giao

- Nhận đúng source commit phiên 1 bằng Git bundle SHA-256 hai đầu, clone Tiny mới; không sửa repo Tiny gốc/qua T:, không ghi đè clone/artifact cũ.
- Cấp config bằng quy trình Tiny đã được phép cho clone này; ngoại lệ E104 không mặc nhiên cấp quyền E105. Chưa có cách hợp lệ → BLOCKED. Không đọc/in/copy secret về Ni; chỉ kết quả boolean.
- Build bốn biến thể riêng/open × Debug/Release, tên artifact duy nhất. Gradle từ xa dài dùng Start-Process -WindowStyle Hidden, log/exit code dưới C:\Bots\_qa_temp\.
- Open: chạy task E104 kiểm không nhúng hash; `reportTelegramConfig=false` đúng lúc build. Trước QA Telegram cần runtime config khả dụng=true và đã đăng nhập, không xuất giá trị. Chủ dự án tự nhập mã/OTP; agent dừng điều khiển khi nhập.
- Shanling `30313860180939` trên Ni và Sony `QV725WEQ54` trên Tiny bắt buộc cho hai chiều. Kiểm devices/model/manufacturer, package/version/cert/ABI/config, không đang phát trước cài; chỉ install -r. Không dùng Sony thay chứng cứ Shanling.
- Một bài Telegram do chủ dự án chỉ định, chưa nằm trong playlist hai máy; **duyệt riêng ADD/REMOVE metadata**. Không dùng fixture. Có outbox cũ ngoài bộ thử → không cấp lượt gửi, BLOCKED, không xóa hàng đợi.

### Bác sĩ tự kiểm tra / QA từ xa — 6 bước
Agent thực hiện; chủ dự án duyệt ảnh trước/sau. Mỗi biến thể ghi thiết bị, thời điểm, hành động, kỳ vọng, thực tế và ảnh.
1. Shanling → Cài đặt → Đồng bộ Playlist: chưa nhận cảnh báo mới phải tắt; ghi tập/số bài nền. Chấp nhận → gửi chưa bật, không nút tạo.
2. Cả hai máy: Tìm kênh → Dùng kênh này nếu chưa gắn → Đồng bộ ngay budget 0. Đúng kênh hợp lệ, không gửi; không chứng minh được điều kiện → dừng.
3. Shanling Lưu bài thử → cấp 1 lượt → đồng bộ. Sony đọc-only → quay lại Playlist: đúng bài thử xuất hiện không restart, tập nền nguyên vẹn.
4. Sony bỏ đúng bài thử → cấp 1 lượt → đồng bộ. Shanling đọc-only: bài thử biến mất, tập nền nguyên vẹn.
5. Shanling tắt sync, đóng/mở app: danh sách nguyên, sync tắt. Bật lại không tự cấp gửi, mở màn không gửi.
6. Lặp 1–5 cho open, cùng tài khoản/kênh, dữ liệu riêng của gói giữ nguyên. Chạy chuỗi cả Debug/Release; không suy diễn Release từ Debug.

Màn chỉ ON trước UI bằng 224; finally dùng 223 và xác minh Display State=OFF, kể cả FAIL/BLOCKED. Ghi SHANLING_SCREEN_END/SONY_SCREEN_END. Build/chờ duyệt màn OFF. Không đổi mạng/timeout/brightness/AOD/khóa màn hoặc dùng keyevent 26.
Ảnh private ở `docs/plans/artifacts/E105-DONG-BO-PLAYLIST/`, không commit; `00_tong_hop.png` ghép 6–8 ảnh chính, cạnh dài ≤1600px. Báo cáo ≤~250 dòng, đầu dòng phiên 2; ghi APK source SHA, SHA256, bytes, package, vc/vname, cert, config boolean, đường dẫn và giới hạn.
Opus kiểm toán và bàn giao APK/ảnh owner-only; Codex không upload/công bố, không viết file nghiệm thu/apk-test/hinh-nghiem-thu, không merge. Commit báo cáo rồi dừng.

## 4. Tiêu chí, rollback và STOP

Điều kiện đề nghị Opus nghiệm thu (Codex không tự ghi PASS cuối):
- Bốn biến thể test/build thành công, test mới không skip, không hồi quy; trạng thái test được đếm rõ.
- Riêng/open dùng được không cần QA; Release không mở QA/create. Cờ sản phẩm bật không chạy fixture hay tạo kênh.
- ADD/REMOVE hai chiều được đọc lại sau một lượt sync hoàn tất của bộ thử nhỏ, không restart/trùng; tập nền giữ nguyên. Pending/unknown không báo hoàn tất.
- Có bằng chứng thật cả open/riêng và hai máy; thiếu đăng nhập open/Sony → BLOCKED phần tương ứng. Opus độc lập + chủ dự án duyệt mới là PASS cuối.

Rollback: tắt sync, giữ dữ liệu/kênh. APK khôi phục phải cùng package/cert, vc không giảm; giữ base.apk trước cài. Sony có thể đang vc<120, không cài lại bản thấp: dùng artifact phục hồi baseline vc120 đúng signer sau kiểm tra. Không tự đảo cloud, xóa dữ liệu hay sửa lịch sử Git. Consent warning=2 khiến bản cũ không nhận quyền và tắt sync an toàn; schema playlist không đổi.

STOP khi: Git đổi ngoài phiên; agent/build khác ghi; sai baseline; signature mismatch/downgrade; unauthorized/model lạ; thiếu runtime config; chưa duyệt bài/metadata; outbox ngoài bộ thử; kênh mơ hồ/không an toàn; dữ liệu không đọc được; unknown delivery/FLOOD_WAIT/crash/mất bài nền. Không tự sửa giao thức/schema/phục hồi hoặc phá luật để có kết quả. Không logout/đổi account/xóa cache/TDLib, không cài FYT.

## 5. Bàn giao

Phiên lập kế hoạch chỉ commit tài liệu này; chưa code/test/build/ADB/cài. Chủ dự án đã chọn A + open. Phiên 1 mới đọc kế hoạch, baseline và SHA commit tài liệu theo PROMPT_CODEX.md mục B, model gpt-6-astra medium; phạm vi S1–S5. Phiên 2 mới làm mục 3, cùng model. Không bắt đầu code trong hội thoại lập kế hoạch này.
