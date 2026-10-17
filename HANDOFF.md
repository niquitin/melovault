# HANDOFF — MeloVault / Music Assistant Car

**Snapshot:** 17/10/2026 (Asia/Ho_Chi_Minh). Tài liệu phục vụ AI/kỹ sư mới tiếp quản. Thông tin lấy từ code, Gradle, Git, test XML và các báo cáo có thật. Phần chưa kiểm chứng đánh dấu **[CẦN XÁC NHẬN]**. Trước khi hành động phải kiểm tra Git live vì trạng thái có thể thay đổi sau khi viết tài liệu.

**ĐIỂM DỪNG QUAN TRỌNG (cập nhật 17/10/2026):** Beta86 đã CHỐT. APK chính thức: build từ dbafe3e, vc86 / 0.6.65-beta, SHA256 B5F9C2611A5804798A1015D8D411640BD4EFEC4D791510A3B005B25A80AC523F; đã qua Sony QA, upload Drive riêng, chủ dự án QA Y700 đạt. Theme 03 đã chốt. Việc tiếp theo: thiết kế Theme 04 từ ảnh concept của chủ dự án (chỉ làm khi chủ dự án giao). APK trial vc85 thiếu Telegram config và APK vc86 cũ build từ f2eae36 (98FA98F6…5D85) **KHÔNG CÀI / GỬI / UPLOAD** (xem mục 5).

## 0. Đọc nhanh / môi trường

- Repo lập trình chính: Ni PC Windows, D:\Bots\MeloVault-E91-Shareable.
- Branch hiện tại lúc viết: feature/e93-03d-y700-offline-cockpit; HEAD 83b66c177498457165ec5d009a5afb0414fa2aee; base Beta 85 e278649a6c9f2166e39653bf151dec5a4af94b0e.
- Clone Tiny để build/test độc lập: C:\Bots\MeloVault-E93D-IsolatedQA, HEAD 83b66c1.
- Worktree Beta 85 gốc của Tiny: C:\Bots\MeloVault-E91-Shareable, HEAD e278649; là worktree liên kết với Git metadata của Private Stable nên không được sao chép .git hoặc chỉnh trực tiếp để cập nhật từ Ni.
- TUYỆT ĐỐI không sửa/tác động Private Stable C:\Bots\MusicAssistantCar, production backend C:\Bots\MusicAssistant, dự án Telegram Secretary C:\Bots\TelegramSecretary hoặc các session TDLib người dùng.
- Handoff mới hơn file cũ: D:\Bots\_handoff\MeloVault\CHATGPT_TINY_TRANSFER_BUILD_GATE_E93_03D_20261017.md; CODEX_NI_E93_03D_IMPLEMENTATION_REPORT.md; CHATGPT_NI_INDEPENDENT_AUDIT_E93_03D_20261017.md; FIELD_REPORT_E93_03D_Y700_BETA85_DOWNLOADED_DUPLICATES_COCKPIT_CLIP_20261017.md. File LATEST_HANDOFF.txt ở đó dừng tại Beta 85 và **không phản ánh đầy đủ E9.3-03D**.

Kiểm tra đầu phiên (PowerShell):

~~~powershell
cd D:\Bots\MeloVault-E91-Shareable
git status --short
git branch --show-current
git rev-parse HEAD
git log -5 --oneline
git diff --check
~~~

Nếu branch, HEAD, working tree hoặc process Agent đang hoạt động khác snapshot này, **dừng và đối chiếu**, không git reset/clean/stash/checkout đè.

## 1. Mục tiêu sản phẩm và phạm vi hiện tại

MeloVault là ứng dụng quản lý/phát nhạc Android cho điện thoại, tablet và màn hình giải trí ô tô. Mục tiêu UX là phát nhạc ổn định, thư viện lớn, tìm kiếm, playlist/hàng đợi, tải offline/cache và giao diện phù hợp dọc/ngang/chia cửa sổ, điều khiển MediaSession/phím vô lăng. Thiết bị phát triển/QA chính: Sony Xperia, Lenovo Legion Y700 Gen 3, FYT 7870 (trên xe).

**Phạm vi tích cực đang làm: APK Shareable Preview** phục vụ nhạc trong tài khoản Telegram được chính người dùng đăng nhập bằng TDLib. Đồng bộ metadata nhạc từ nhóm/kênh/chat/Saved Messages mà tài khoản có quyền truy cập, index và tìm kiếm; phát nhạc bằng Media3 từ file TDLib, hỗ trợ nguồn offline. Hiện E9.3-03D xử lý lỗi lọc offline và Cockpit ngang trên Y700.

Hai flavor trong một module :app:
- personal: applicationId mặc định com.bsdung.musicassistantcar, versionCode 61/versionName 0.6.40 trong defaultConfig; còn code API/backend cá nhân và UI cũ. Giữ nguyên Private Stable đã phát hành và dữ liệu của nó.
- shareable: applicationId app.melovault.player, PERSONAL_BACKEND_ENABLED=false, launcher TelegramPreviewActivity, ShareablePlaybackService riêng; bản trên Y700 hiện là Beta86 (vc86, 0.6.65-beta, build dbafe3e, SHA256 B5F9C261…523F), đã chốt 17/10/2026.
- bridge/ chứa Python bridge (app.py, catalog.py, streaming.py, telegram_stream.py, tests) nhưng không phải luồng bắt buộc cho Shareable native Telegram. [CẦN XÁC NHẬN] môi trường chạy bridge hiện còn dùng cho mục đích nào.
- **Nguồn nhạc Google Drive/OneDrive riêng trong Shareable chưa triển khai**. Các file Drive/backend trong src/main thuộc kiến trúc cũ, không có nghĩa đã có OAuth/library Drive trên Shareable. Google Drive hiện dùng lưu APK/handoff cho chủ dự án.
- Có 9 concept UI định hướng phát triển, nhưng **không có 9 theme hoàn chỉnh trong code**.

## 2. Tech stack, phiên bản, build/chạy/test

Xác nhận trực tiếp:
- Windows; Java source/target 17, Kotlin jvmTarget 17; trên Ni dùng OpenJDK 21 để chạy Gradle.
- Gradle Wrapper 8.13; Android Gradle Plugin 8.13.0; Kotlin plugin 2.2.10; Groovy Gradle.
- Android: compileSdk 36, targetSdk 36, minSdk 26. Module Gradle duy nhất :app; flavor dimension distribution: personal/shareable, buildTypes debug/release.
- Shareable UI dùng Android Views/AppCompat (Activity, LinearLayout, ListView, ScrollView, v.v.), **không dùng Jetpack Compose**.
- Media3 1.11.1 (ExoPlayer, Session, DataSource), WorkManager 2.9.1, RecyclerView 1.3.2, Coil 2.6.0, AndroidX Core KTX 1.18.0, Activity KTX 1.13.0, AppCompat 1.7.0, Material 1.12.0.
- Telegram: TDLib JNI/JSON, thư viện libtdjsonjava.so đóng gói. Tài liệu E9.1 từng ghi TDLib 1.8.68 revision c15d3f5a5de6e3ba5839822c451152e5e18bb700; **[CẦN XÁC NHẬN]** revision/hashes chính xác của binary hiện đóng gói trước khi phát hành rộng.
- Data: SQLite/SQLiteOpenHelper, DAO tự viết; JUnit4 JVM tests; Android instrumentation sources hiện diện.

Lệnh trên Ni (PowerShell, repo đã mở):

~~~powershell
cd D:\Bots\MeloVault-E91-Shareable
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug --console=plain
.\gradlew.bat :app:assembleShareableRelease --console=plain
git diff --check
~~~

Ni Android SDK: C:\Users\ntdun\.cache\codex-toolchains\android-sdk (ghi sdk.dir vào local.properties, file ignored). Tiny SDK: C:\Android\Sdk. Gradle đọc telegram.api.id và telegram.api.hash từ local.properties local-private. Nếu thiếu/sai, build **vẫn có thể PASS** nhưng BuildConfig sẽ dùng ID 0 và hash rỗng, đăng nhập Telegram không sử dụng được. Không bao giờ đưa các giá trị đó vào Git, file HANDOFF, Drive, log, ChatGPT hoặc Ni chỉ để build.

Shareable hiện có signingConfig signingConfigs.debug ngay cả release: dùng **chữ ký Preview cũ**, không phải khóa phát hành production lâu dài. Build output: app\build\outputs\apk\shareable\release\app-shareable-release.apk. Không được tự cài/chia sẻ file build nếu chưa qua gate version/config/signature/QA.

Test hiện đã kiểm tra trên Ni **và Tiny clone độc lập** cho HEAD 83b66c1:
- Debug: 100 suites, 1.050 defined, **1.029 executed/passed**, 21 skipped, 0 failed/error.
- Release: như trên. Tiny Gradle test+assembleShareableRelease **BUILD SUCCESSFUL**.
- XML: app\build\test-results\testShareableDebugUnitTest và ...\testShareableReleaseUnitTest. Các test skipped không tính pass.
- Chưa chạy trên thiết bị thật cho HEAD 83b66c1; unit tests không thay runtime TDLib/Android layout testing.
- Để cài test **chỉ sau khi APK hợp lệ và cho phép**: adb devices; adb -s <SERIAL> install -r "<SIGNED_APK>"; mở launcher MeloVault. Không uninstall/pm clear/logout/cache wipe. Dùng apksigner trong Android SDK build-tools thực tế để verify cert/package/version trước khi cài. [CẦN XÁC NHẬN] path apksigner cụ thể trên máy kế nhiệm.

## 3. Kiến trúc: module, dữ liệu, Telegram, phát nhạc

### 3.1. Cấu trúc code

- app/src/main/java/com/bsdung/musicassistantcar: code chung/Personal, MainActivity.kt, MeloVaultApplication.kt, data/, lyrics/, playback/, ui/.
- app/src/main/.../data/telegram/TelegramAuthorizationFlow.kt, TelegramSessionEpoch.kt, TelegramAccountPartition.kt: auth/epoch/identity và bảo vệ vùng file theo chủ tài khoản.
- app/src/shareable/AndroidManifest.xml: launcher TelegramPreviewActivity; thay thế/tắt Activity/Service của Personal, ShareablePlaybackService (MediaSessionService + MEDIA_BUTTON), usesCleartextTraffic=false.
- app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/TelegramPreviewActivity.kt: UI Shareable, auth callback, nguồn/bộ lọc/tìm kiếm, lựa chọn bài, player, theme/Bento, offline discovery và Cockpit.
- ShareableTdlibOwner.kt + TdlibNativeJsonGateway.kt: TDLib client, một receive loop, callback UI và background, getMe-verified owner.
- TelegramSyncCoordinator.kt, TelegramLibraryDao.kt, TelegramLibraryEntities.kt, TelegramLibrarySql.kt, AndroidTelegramLibraryDatabase.kt: phân trang/cursor/checkpoint và SQLite library.
- ShareablePlaybackService.kt: Media3 ExoPlayer, MediaSession, queue/prefetch và playback trong service.
- TelegramAudioResolverPolicy.kt, TelegramOwnerAudioDataSource.kt, TelegramProgressiveCache.kt, TelegramBackgroundPrefetchPolicy.kt, TelegramQueueRetentionPolicy.kt: giải quyết current file_id, đọc file/cache TDLib và queue.
- TelegramSavedPlaylistStore.kt: saved playlist account-scoped, dữ liệu noBackup bảo vệ AES-GCM Android Keystore.
- UiThemePolicy.kt, CockpitLayoutPolicy.kt, TelegramVaultBentoPolicy.kt, NowPlayingFullScreenDialog.kt, WindowInsetsPolicy.kt, CarPaneLayoutPolicy.kt: theme, split và responsive UI.
- E9.3-03D mới: TelegramOfflineLibrary.kt (policy scan/nhóm offline); TelegramOfflineLibraryTest.kt và CockpitMeasuredFitTest.kt (JVM tests). AndroidTelegramLibraryDatabase.kt và InMemoryTelegramLibraryDao.kt được sửa tie-break sort.
- app/src/test/: unit; app/src/androidTest/: instrumentation (mức độ test thực trên các biến thể mới **[CẦN XÁC NHẬN]**).
- tools/: các scripts TDLib preflight/ELF security audit, APK release guard, Sony soak... đọc kỹ trước khi chạy vì có scripts phục vụ giai đoạn cũ.
- bridge/: Python source, tách luồng Shareable.

### 3.2. Dữ liệu Telegram

Luồng chuẩn:

~~~text
TDLib native/JSON
  -> ShareableTdlibOwner + TelegramAuthorizationFlow + TelegramSessionEpoch
  -> TelegramPreviewActivity nhận event/ủy quyền
  -> TelegramSyncCoordinator: Saved Messages getChatHistory,
     toàn nguồn searchMessages (audio/document) với paging/cursor/incremental
  -> TelegramLibraryDao -> AndroidTelegramLibraryDatabase (SQLite)
  -> UI thư viện/Bento/Search/Queue/Offline
~~~

TDLib phải báo authorizationStateReady và **getMe() thành công** trước khi coi account là authenticated. Số điện thoại/username nhập tay không phải identity. Nhiều trạng thái auth gồm phone/email/code/QR/2FA tùy native, callback cũ bị kiểm soát bằng epoch/generation. Không lưu OTP/password trong prefs/log.

Sync state: TelegramSyncCoordinator có PAGE_LIMIT=100, phases audio/document, checkpoint/resume, incremental overlap, rate-limit backoff và generation chống cập nhật quá hạn. Saved Messages riêng dùng getChatHistory. Các loại audio và tài liệu có đuôi nhạc được parse; không giả định mọi tin nhắn đều là bài hát.

SQLite local ở app-private: melovault_tg_index_{accountId}.db, các bảng songs/sources/sync_state. Định danh bền: **(accountId, chatId, messageId)**; songKey 64-bit dùng để phân biệt queue/playlist; TDLib fileId là **session-scoped** (không dùng lại file_id cũ từ SQLite để phát). Source count lấy từ index thật, không hardcode. Metadata khác nội dung nhạc: audio file vẫn thuộc TDLib files/cache. Song entity chứa title/filename/performer/duration/size/MIME/source/date/isDeleted.

Session TDLib cần thư mục trước khi có getMe: slot UUID tại noBackupFilesDir/telegram_session_slots/slot-..., filesRoot nằm dưới slot hiện hành; có khóa Android Keystore cho TDLib database. Bảo vệ path qua TelegramAccountStoragePolicy.isOwnedFile (canonical path containment), hạn chế đọc nhầm account.

### 3.3. Phát nhạc

~~~text
Người dùng chọn bài trong TelegramPreviewActivity
 -> xác định chatId/messageId + account chủ
 -> TDLib getMessage để resolve file_id HỢP LỆ Ở PHIÊN HIỆN TẠI
 -> TDLib downloadFile / updateFile (khi cần)
 -> file đã hoàn tất HOẶC prefix đang tải do TDLib quản lý
 -> TelegramProgressiveCache.Reader + TelegramOwnerAudioDataSource
 -> Media3 ExoPlayer trong ShareablePlaybackService
 -> MediaSession (phát nền, hàng đợi, media buttons/phím vô lăng)
~~~

TelegramOwnerAudioDataSource giữ nguồn file/content thông thường qua DefaultDataSource, dùng scheme riêng melovault-tdlib cho progressive stream. TelegramProgressiveCache đọc prefix byte liên tục của file TDLib, chờ dữ liệu tiếp theo, không tạo public stream/proxy. Service quản lý queue và tải bài kế tiếp có giới hạn. Không tự đem backend Personal vào Shareable.

**E9.3-03D offline:** TelegramOfflineLibrary.scan đọc tất cả trang phù hợp (mỗi trang 200), resolver getMessageLocally tối đa 32/batch trên executor riêng; không gọi downloadFile khi chỉ kiểm tra offline. Chỉ xác nhận offline nếu TDLib cho biết tải hoàn tất, file local thuộc root chủ, có thể đọc và size khớp. Thiếu metadata = **chưa xác minh**, không tự kết luận “chưa tải”. Nhóm cùng canonical path là cùng file; cùng title nhưng path khác hiển thị “Các bản cùng tên” và cho mở mọi bản/nguồn. Không xóa/gộp cache, Telegram message hay SQLite row.

## 4. Quyết định kỹ thuật đã chốt và lý do

1. **Shareable/Personal tách package + backend boundary:** không làm hỏng Stable, không lộ backend/token riêng, cho APK Telegram độc lập.
2. **TDLib user client thay Telegram Bot API:** cần truy cập nhạc từ quyền truy cập Telegram của chính user, không phải bot.
3. **getMe + auth epoch + account-scoped files:** tránh đồng bộ/phát dữ liệu của account khác; không dùng phone/account input làm bằng chứng identity.
4. **Composite key account/chat/message và fileId theo phiên:** chống trùng message ID liên chat và stale TDLib file_id.
5. **SQLite + checkpoint + paging:** phù hợp thư viện hàng chục nghìn bài, hỗ trợ resume, hạn chế main-thread/OOM và số liệu giả.
6. **Media3/ExoPlayer + TDLib progressive DataSource:** playback nền, media buttons/phím vô lăng và nhạc chưa tải đủ, không cần owner backend.
7. **Không destructive dedup:** nhiều kênh gửi bài cùng tên không chắc nội dung giống nhau; nhóm ở UI kèm chooser, giữ nguyên dữ liệu.
8. **Theme mặc định Portrait Edge-to-Edge Pro xanh ngọc #3DDC97.** Theme 02 Cockpit Two-Pane 42:58 được chủ dự án nghiệm thu chức năng, màu sắc còn polish. Theme 03 Telegram Vault Bento đã chủ dự án chốt trên Y700 ở Beta86 (17/10/2026). Trong UiThemePolicy chỉ có các preset PORTRAIT_EDGE_PRO, COCKPIT_TWO_PANE, CLASSIC; Bento/List là chế độ xem nguồn, không phải cả 9 concept đã hoàn thành.
9. **WindowInsets một lần và đo khung thật:** tránh che status/taskbar Y700; không ép immersive; thu nhỏ artwork trước khi giấu action, overflow khi chiều cao quá thấp.
10. **Ni Codex làm code, Tiny QA thông qua SHA-verified Git bundle:** Tiny original Preview là linked worktree của Stable; không chép đè .git/repo hoặc cho hai Agent cùng sửa.
11. **Release-gate:** test JVM -> build/signature/API/privacy -> Sony QA -> private Google Drive -> Y700 owner QA -> chốt/theme tiếp theo. Chủ dự án đã duyệt tự upload APK Preview lên Drive riêng **sau khi kiểm toán đạt**, không duyệt cài FYT hay publish công khai.

## 5. Đã xong / đang làm / chưa làm

### Đã có
- Native TDLib, auth UI và xác minh account; index SQLite, Saved Messages, các nguồn Telegram, tìm kiếm, playlist, queue, phát bằng Media3/cache/stream/offline.
- Theme 01 mặc định; Theme 02 42:58 và MediaSession; Theme 03 Bento Grid, kênh 0 bài, tổng thư viện và sửa stale header ở Beta85.
- Beta85 owner-installed, đã có phản hồi ảnh/video Y700. Bản gốc Beta85 vc85/0.6.64-beta là fallback.
- Code E9.3-03D đã commit + kiểm toán code + unit test cả 2 biến thể trên Ni và Tiny, nhưng **không đồng nghĩa chức năng trên thiết bị đã nghiệm thu**.

### Đang làm dở / dừng ở đâu

Ni commit 83b66c1 (parent e278649) đổi đúng 7 file:
- app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/TelegramOfflineLibrary.kt — **mới**, scan+group offline.
- cùng thư mục/TelegramPreviewActivity.kt — worker offline, chooser nguồn, phân trang và measured Cockpit.
- cùng thư mục/CockpitLayoutPolicy.kt — measuredCoverSize.
- cùng thư mục/AndroidTelegramLibraryDatabase.kt và InMemoryTelegramLibraryDao.kt — sort tie-break chatId.
- app/src/test/java/com/bsdung/musicassistantcar/data/telegram/TelegramOfflineLibraryTest.kt — mới.
- cùng test directory/CockpitMeasuredFitTest.kt — mới.

Mã này đã import vào Tiny standalone clone C:\Bots\MeloVault-E93D-IsolatedQA và Tiny đã BUILD SUCCESSFUL. Delta bundle Ni: D:\Bots\_transfer\MeloVault_E93_03D_83b66c1_delta_20261017.bundle, SHA256 1EAA7E2E94AEB7D28F1D485ADD09D8E76B9AEEA9B6ADDA3AB1D5F5358682295D, 12.861 bytes. Original Tiny Preview Beta85 vẫn e278649 và sạch.

**BLOCKER bắt buộc:** Tiny build trial từ isolated clone tạo APK 30.210.458 bytes, SHA256 0B507D40A48142C3F88700AB40117AEAA6F9540E72E6F390ED24435885540976 và signer SHA256 eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8; **APK NÀY KHÔNG ĐƯỢC CÀI** vì vẫn vc85 0.6.64-beta + build thiếu Telegram API config, mặc dù test/build PASS.

**P1 ĐÃ XONG trên Ni (17/10/2026, Claude Code):** commit b490ded4531f5b9baf884d83bb9f8118ee11adc8 `chore(shareable): bump Beta 86 versionCode 86 / 0.6.65-beta`, chỉ đổi 2 dòng flavor shareable trong app/build.gradle (Personal vc61/0.6.40, applicationId, signing giữ nguyên). Test JVM Ni sau bump: Debug và Release mỗi biến thể 1.050 tests, 1.029 passed, 21 skipped, 0 failed; git diff --check sạch. **Chưa** build APK, chưa chuyển sang Tiny.

**P2 (phần Ni) ĐÃ XONG:** bundle delta D:\Bots\_transfer\MeloVault_E93_03D_Beta86_83b66c1-f2eae36_delta_20261017.bundle, 17.640 bytes, SHA256 527991A356318AC133D3FE5D4E01EEBB63D6105D621CE0E2F13B14B0827B764C (sidecar `.sha256` cùng thư mục; sha256sum và certutil khớp). Prerequisite 83b66c177498457165ec5d009a5afb0414fa2aee; ref refs/heads/feature/e93-03d-y700-offline-cockpit -> f2eae36fa1c53a0b2e41714e2c997859352219ee; `git bundle verify` OK trên Ni. Gồm 4 commit 594eea1, 5a59bcb, b490ded, f2eae36 (docs + bump). Commit docs ghi lại P2 nằm sau f2eae36 nên KHÔNG có trong bundle này (chỉ là docs, không ảnh hưởng build). **Chưa** copy sang Tiny, chưa import vào C:\Bots\MeloVault-E93D-IsolatedQA. Việc tiếp: trên Tiny verify SHA256 + `git bundle verify` (clone phải có 83b66c1), fetch vào clone cô lập, không đụng worktree Beta85 gốc.

**P2 (phần Tiny) ĐÃ XONG** (theo báo cáo của chủ dự án 17/10/2026; Claude trên Ni không kiểm trực tiếp): SHA256 bundle khớp trên Tiny; đã import vào clone cô lập C:\Bots\MeloVault-E93D-IsolatedQA, fast-forward 83b66c1 → f2eae36.

**P3 ĐÃ XONG** (kiểm trực tiếp trên Tiny bằng aapt2 + apksigner build-tools 36.0.0; báo cáo gốc BUILD_REPORT_Beta86_f2eae36.txt cùng thư mục APK, Ni đã đọc lại): Tiny test Debug và Release mỗi biến thể 1.029 passed / 21 skipped / 0 failed (100 suites). APK release app-shareable-release: package app.melovault.player, versionCode 86, versionName 0.6.65-beta, targetSdk 36, 30.210.458 bytes, có Telegram API config (giá trị không được kiểm/in). Signer SHA-256 đầy đủ eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8 (DN CN=Android Debug), trùng signer Preview Beta85; `apksigner verify` OK. SHA256 APK 98FA98F62E6D14FFB7D45F43629B7E8DD4FADCCF8CE9C55086DF50263FEA5D85, lưu tại C:\Bots\_qa_temp\Beta86_f2eae36\ trên Tiny. Chỉ đã cài lên Sony để QA (P4 bên dưới); **CHƯA upload, CHƯA cài Y700/FYT.** APK trial vc85 ở đoạn BLOCKER bên trên vẫn cấm cài (cùng kích thước 30.210.458 bytes nhưng SHA256 khác; phân biệt bằng SHA256 và versionCode, không bằng kích thước); ứng viên Beta86 hợp lệ duy nhất là APK SHA256 98FA98F6…5D85.

**P4 Sony QA ĐẠT** (17/10/2026 17:55–18:00, Sony XQ-AS52 Android 12; báo cáo gốc T:\_qa_temp\Beta86_f2eae36\qa\QA_REPORT_Sony_Beta86_f2eae36.txt, Ni chỉ đọc): `adb install -r` đè Beta85 (không uninstall/pm clear/logout), SHA256 APK verify lại trước khi cài, giữ session và dữ liệu. PASS: cold launch, đăng nhập, sync xong 25.085 bài / 29 nguồn; lọc offline quét hết 25.085 bài (không còn dừng ở trang 200), 76 bản offline đã xác minh trong 50 nhóm, 4 bản không xác minh được hiện là "unverified" chứ không phải "chưa tải"; chooser bản trùng mở và phát được; phát MP3 offline 44.1 kHz stereo 320 kbps, MediaSession hoạt động; Cockpit ngang trên Sony: cover co lại, đủ 4 action phía trên navigation bar, không bị cắt; không crash/ANR. Trạng thái Sony đã khôi phục (accelerometer_rotation=1, user_rotation=0). Ảnh chụp chứa tên bài riêng tư, chỉ giữ local trên Tiny.
- 2 lỗi nhỏ (không chặn) từ QA này đã sửa trên Ni ở commit 2a9ac8e: tiêu đề dialog chọn nguồn khó đọc (dialog thiếu style Theme.MusicAssistantCar.Dialog) và dòng bản trùng tên giống hệt nhau (thêm ngày tin nhắn + dung lượng qua TelegramSongDisplayPolicy.versionLabel). Không đổi logic nhóm/offline. Test Ni sau sửa: mỗi biến thể 1.054 tests, 1.033 passed, 21 skipped, 0 failed. Bản sửa này không có trong APK 98FA98F6…5D85 (f2eae36); nó nằm trong APK Beta86 build lại từ dbafe3e bên dưới.

**Beta86 build lại từ dbafe3e (APK CHÍNH THỨC)** (báo cáo gốc T:\_qa_temp\Beta86_dbafe3e\QA_REPORT_Beta86_dbafe3e.txt, Ni đã đọc lại): bundle f2eae36..dbafe3e SHA256 08006ED74FEBE1F0B795A97EB95E5261607A0090784A1B0DAD6E5E098D9153A6 (10.443 bytes), verify trên Tiny, fast-forward clone cô lập. Tiny test Debug và Release mỗi biến thể 1.054 / 1.033 passed / 21 skipped / 0 failed. APK app.melovault.player vc86 0.6.65-beta, signer eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8 (Preview cũ), `apksigner` OK, có Telegram config, 30.210.458 bytes, **SHA256 B5F9C2611A5804798A1015D8D411640BD4EFEC4D791510A3B005B25A80AC523F**, tại C:\Bots\_qa_temp\Beta86_dbafe3e\ trên Tiny. Sony QA bản này (cài `adb install -r` đè bản f2eae36, giữ data/session) ĐẠT: tiêu đề chooser đọc được, các bản cùng tên cùng chat phân biệt được bằng ngày tin nhắn, chọn bản phát được, scan offline vẫn 76 đã xác minh / 50 nhóm, không crash. Ghi chú: với các bản đó index không có dung lượng nên dòng không hiện MB (đúng thiết kế, không hiện số giả).
- **Hai APK khác nhau cùng mang vc86 / 0.6.65-beta và cùng 30.210.458 bytes**: f2eae36 (98FA98F6…5D85, đã thay thế, không phát hành) và dbafe3e (B5F9C261…523F, chính thức). Chỉ phân biệt được bằng SHA256. Bản phát hành sau phải bump versionCode ≥ 87.

**P5 ĐÃ XONG** (theo chủ dự án): APK dbafe3e B5F9C261…523F đã upload lên Google Drive riêng, thư mục MeloVault-Owner-Beta-v0.6.65-b86-Y700-REVIEW. Chủ dự án cài và QA trên Lenovo Legion Y700 Gen 3, **ĐẠT ngày 17/10/2026**. [CẦN XÁC NHẬN] trên Ni không có báo cáo audit độc lập P5 và không có chi tiết case Y700 (fontScale, IME ngang, phím media); nếu cần thì bổ sung đường dẫn báo cáo.

**P6 ĐÃ XONG** (theo chủ dự án): chốt Beta86 (APK dbafe3e) và chốt Theme 03 Telegram Vault Bento.
- Sony KHÔNG test: Cockpit ngang trên Y700 (mục tiêu thật của sửa lỗi clip), IME/bàn phím khi ngang, fontScale > 1, MEDIA_BUTTON/phím vô lăng, phát nền lâu.

**Beta87 — Thiết kế giao diện mới, bước 1–3 của docs/ui-spec.md (code + test trên Ni xong, CHƯA build Tiny, CHƯA QA thiết bị).** Đặc tả của chủ dự án: docs/ui-spec.md (commit 5da1e21). Commit:
- 96b093c bước 2: MeloDesignTokens.kt — bảng màu teal (Car Mode chữ phụ #C4D3DB), 4 hồ sơ Gọn / Điện thoại / Tablet theo dp/sp, Car Mode theo bảng pixel FYT ở mật độ vật lý 172.8 ppi quy qua milimet (không đổi theo mật độ firmware 160/240 hay cỡ chữ hệ thống).
- 2252782 bước 3: MeloComponentPolicy.kt (5 trạng thái bài luôn có biểu tượng + chữ; nhãn chất lượng chỉ hiện phần biết chắc, Hi-Res chỉ khi lossless ≥24bit/48kHz; gợi ý tìm kiếm dùng số bài thật; tìm không dấu) + MeloComponents.kt (dòng bài hát, chip, thanh tìm kiếm, mini player, biểu tượng trạng thái vẽ bằng canvas).
- ae0ecc0 bước 1: MeloScreenPolicy.kt (số đo, ngưỡng bố cục 600/700/840/1200dp, giả lập Shanling M3 Ultra 768×1280@320, FYT 7870 1920×1200@160 và @240) + MeloUiLabActivity (màn "Cài đặt giao diện (thử nghiệm)": Thông tin màn hình, nút Sao chép số đo, Giả lập thiết bị, Xem như Car Mode) + MeloUiPreviewActivity (khung đúng số px/mật độ, căn giữa, phần thừa tô tối, chỉ thu nhỏ kèm thông báo khi khung lớn hơn màn; dữ liệu mẫu). Màn hiện có chỉ thêm 1 mục menu ⋮ "⚙ Cài đặt giao diện (thử nghiệm)" (3 dòng trong TelegramPreviewActivity) + 2 activity trong manifest shareable. Prefs riêng `melovault_ui_lab`; không đụng đăng nhập/thư viện/playlist/cache.
- Commit bump riêng: shareable versionCode 87 / 0.6.66-beta.
- Test Ni sau bump: Debug và Release mỗi biến thể 1.084 tests, 1.063 passed, 21 skipped, 0 failed (+30 test mới MeloDesignTokensTest, MeloScreenPolicyTest, MeloComponentPolicyTest). assembleShareableDebug trên Ni BUILD SUCCESSFUL (APK Ni thiếu Telegram config, không cài).
- Chưa làm: bước 4–8 của đặc tả; chưa đưa token/thành phần vào màn hiện có. [CẦN XÁC NHẬN] ngưỡng bố cục chờ số đo thật S25 Ultra + Y700 từ màn Thông tin màn hình; xdpi thật của FYT (nếu firmware báo sai, app tạm dùng mật độ Android và ghi chú trên màn).
- Bundle Beta87 trên Ni: D:\Bots\_transfer\MeloVault_Beta87_dbafe3e-bae696c_delta_20261017.bundle, 94.592 bytes, SHA256 0F250AF48BF1A121095B737A9A69F44FC802F46738561BFE6EAE6052EAE784B4 (sidecar `.sha256`; sha256sum và certutil khớp; `git bundle verify` OK). Prerequisite dbafe3ee627947d116188654ad4de568ae7cb3bd (HEAD clone cô lập Tiny theo QA_REPORT_Beta86_dbafe3e); ref feature/e93-03d-y700-offline-cockpit → bae696cf185f0c26b334d0e917c3585c0f748185, gồm 7 commit 5dc992d..bae696c. Commit docs ghi dòng này nằm sau bae696c, không có trong bundle.
- **P2/P3 Beta87 trên Tiny XONG (17/10/2026, Claude Code chạy qua Desktop Commander trên DESKTOP-HNP4GVR = Tiny):** bundle SHA256 khớp trên Tiny, `git bundle verify` OK, clone cô lập fast-forward dbafe3e → bae696c (tree sạch). Test Tiny Debug và Release mỗi biến thể 103 suites, 1.084 tests, 1.063 passed, 21 skipped, 0 failed; assembleShareableRelease BUILD SUCCESSFUL; Telegram config có mặt (không in giá trị; log build không chứa chuỗi giống hash). APK `C:\Bots\_qa_temp\Beta87_bae696c\MeloVault_Shareable_Beta87_vc87_0.6.66-beta_bae696c_UNTESTED.apk`, 30.243.294 bytes, **SHA256 2D8E621DDA2F74EE5FD0C80EF06D8AB61CC1F91405DB7C3BAEE075E784B371EE**; aapt2: app.melovault.player, vc87, 0.6.66-beta, minSdk 26, targetSdk 36; `apksigner verify` OK (v2). Báo cáo: BUILD_REPORT_Beta87_bae696c.txt cùng thư mục. **Khóa ký TRÙNG Beta86:** cả hai APK (Beta87 và Beta86 B5F9C261…523F) có signer SHA-256 eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8 (CN=Android Debug) → cài đè được, không cần gỡ. **CHƯA cài, chưa Sony QA**; chủ dự án tự cài thử trên Y700.
- **Upload Drive riêng Beta87 XONG (17/10/2026, theo lệnh chủ dự án; từ nay là mặc định, xem CLAUDE.md "Quy trình gate"):** thư mục mới `H:\My Drive\MeloVault-Owner-Beta-v0.6.66-b87-Y700-REVIEW` trên Tiny (Google Drive for desktop) gồm MeloVault-Owner-Beta-v0.6.66-b87.apk (30.243.294 bytes, SHA256 trên H: khớp 2D8E621D…71EE), SHA256.txt, README_OWNER_ONLY.txt (ghi rõ chưa Sony QA, rollback về b86). [CẦN XÁC NHẬN] Drive đã đồng bộ lên mây và thư mục chỉ chủ dự án xem được (tài khoản Drive trên Tiny khác tài khoản connector của Claude nên Claude không kiểm được quyền chia sẻ).
- Chủ dự án báo đã cài Beta87 lên **S25 Ultra** (17/10/2026).
- **Số đo S25 Ultra (SM-S938B, Android 17 / API 37) từ màn Thông tin màn hình, chủ dự án gửi 17/10/2026:** 1080 × 2340 px; cửa sổ app = toàn màn; dọc 384 × 832 dp, ngang 832 × 384 dp; cạnh ngắn 384 dp; mật độ Android 450 dpi (×2.81); vật lý 375.8 × 376.2 ppi; 73 × 158 mm, 6.9"; 1 dp = 0.190 mm (48 dp ≈ 9.1 mm); cỡ chữ ×1.00. Kết quả: dọc → Điện thoại dọc (đúng). Ngang ban đầu ra "Tablet dọc" (832 < 840) là SAI so với đặc tả → đã sửa quy tắc trong `MeloScreenPolicy.chooseLayout`: từ 600dp trở lên, cửa sổ ngang (rộng > cao) luôn là Hai khung; cửa sổ dọc 600–839dp vẫn Tablet dọc; ≥840dp vẫn Hai khung. S25 không bao giờ vào bản Gọn (cao 832 ≥ 700). Thay đổi chỉ ảnh hưởng màn mới (Cài đặt giao diện / xem trước), không đổi màn cũ; sẽ có trong bản build kế tiếp. **Chủ dự án quyết định bỏ đo Y700 (17/10/2026):** ngưỡng Tablet/Hai khung giữ ước lượng 600/840dp của đặc tả + quy tắc màn ngang ở trên; không chờ số đo Y700.

**Beta88 — Bước 4 docs/ui-spec.md: bản Gọn cho Shanling M3 Ultra (code + test trên Ni xong).** Chủ dự án chọn: lớp giao diện mới phủ lên màn chính, dùng lại nguyên logic cũ; tự bật theo kích thước màn hình + công tắc; Shanling mở vào "Đã tải". Commit:
- 61b6c79 MeloCompactPolicy (+test): CompactSetting AUTO/ON/OFF/EMULATE_SHANLING (prefs `melovault_ui_lab`, key `compact_mode`); AUTO dựa trên kích thước **màn hình** (không theo cửa sổ chia đôi); không bao giờ bật trước khi đăng nhập; mở vào Đã tải đúng 1 lần (cờ `compact_default_applied`).
- c9e40d8 MeloEmulation: tách khung giả lập dùng chung (preview không đổi hành vi).
- 5abbc35 MeloCompactScreen: đầu trang ☰ / tiêu đề / ⌕ / ⋮; dòng bài 1 dòng + icon trạng thái; trang Telegram (nguồn + số bài); mini player 56dp; Đang phát (màn đầu: bìa, tên, tiến trình, Lùi/Phát/Tiến; cuộn mới thấy kênh, chất lượng, Đảo/Lặp/Lưu/Hàng đợi). Chỉ đọc qua `Host`.
- 5b73640 móc vào TelegramPreviewActivity: bọc cây view cũ trong FrameLayout ngoài; khi bật Gọn, cây cũ INVISIBLE nhưng vẫn chạy, lớp Gọn phủ lên. Host ánh xạ vào refreshSongRows / filterOnlyDownloaded / playlistMode / browseSources / selectedChatId / downloadAndPlay (nhóm bản trùng → chooser) / playAdjacent / Đảo-Lặp-Lưu-Hàng đợi / menu ⋮ cũ. Tách nguyên khối (không đổi hành vi): phân trang → `loadMoreSongsIfNeeded()`, dòng tải của Đang phát → `currentDownloadNote()`. Cài đặt giao diện thêm mục "Bố cục Gọn" (gồm "Thử trong khung Shanling" 768×1280 @320).
- d67fae3 bump shareable vc88 / 0.6.67-beta.
- Test Ni: Debug và Release mỗi biến thể 1.095 tests, 1.074 passed, 21 skipped, 0 failed (+11 MeloCompactPolicyTest). assembleShareableDebug BUILD SUCCESSFUL.
- Giới hạn đã biết: trong khung giả lập, menu/popup/dialog vẫn vẽ theo mật độ thật của máy; ⋮ trên từng dòng không bấm riêng được trong danh sách Gọn (giữ lâu dòng để mở cùng menu); ảnh bìa chỉ lấy từ cache sẵn có, không tải mới. [CẦN XÁC NHẬN] chạy thật trên Shanling M3 Ultra (Android 10, 3GB RAM) + nút vật lý khi tắt màn (bước 8).
- **Beta88 build Tiny + upload Drive XONG (17/10/2026, mặc định mới):** bundle `D:\Bots\_transfer\MeloVault_Beta88_bae696c-d8e5992_delta_20261017.bundle` (75.057 bytes, SHA256 3E44AEAAA8F20BF729F17905E0F29178DA1D932BF6B9F8676BEEDF88E0BEE76B, khớp trên Tiny), clone cô lập fast-forward bae696c → d8e5992. Test Tiny Debug và Release mỗi biến thể 104 suites, 1.095 tests, 1.074 passed, 21 skipped, 0 failed; Telegram config có mặt. APK `C:\Bots\_qa_temp\Beta88_d8e5992\MeloVault_Shareable_Beta88_vc88_0.6.67-beta_d8e5992_UNTESTED.apk`, 30.276.062 bytes, **SHA256 6151B49A3B24E162623F701CE780E29BA9FCCBAAAF7DF69DDE57B274070ACEA0**; aapt2 app.melovault.player vc88 0.6.67-beta targetSdk 36; signer eecd3df7…ce8 (trùng Beta86/87). Drive: `H:\My Drive\MeloVault-Owner-Beta-v0.6.67-b88-Y700-REVIEW` (APK + SHA256.txt + README_OWNER_ONLY.txt; SHA256 bản trên H: khớp). Chưa cài, chưa Sony QA. Lưu ý: quay về Beta87 là hạ version — Android có thể đòi gỡ app; KHÔNG gỡ, hỏi chủ dự án trước.

**Beta89 — Bước 5 docs/ui-spec.md: Car Mode cho FYT 7870 (code + test trên Ni xong).** Chủ dự án chọn: tìm kiếm dùng bàn phím máy + nút giọng nói; tìm không dấu tách việc riêng sau (DB hiện dùng `LIKE`, phân biệt dấu — "su thanh hoa" KHÔNG ra "Sử Thanh Hóa"). Commit:
- 1e2c08b MeloCarPolicy (+test): CarSetting OFF/ON/EMULATE_FYT_160/240 (prefs `melovault_ui_lab`, key `car_mode`, cờ `car_mode_asked`); chỉ sau đăng nhập; hỏi "Bật Car Mode?" 1 lần khi màn hình ngang ≥1200dp; `carScale` = min(ppi/172.8, rộng/1920, cao/1200) để firmware báo xdpi sai không đẩy cột ra ngoài màn; 5 bài / 4 thẻ nguồn mỗi trang; tua −30s/+60s kẹp theo bài; mất mạng làm mờ bài chưa tải.
- 325d90e `MeloScreenHost` (interface dùng chung cho Gọn và Car) + `MeloCarScreen`: cột điều hướng 160px (Nhạc, Telegram, Playlist, Đã tải, Tìm, Cài đặt), khung trình phát 760px (bìa 360px, tên 48px, tiến trình 16px/núm 48px, Lùi / Phát 240×160 / Tiến, ⏪ 30s / 60s ⏩), khung danh sách 1000px (5 bài đánh số cao 160px, nút lật trang 140px, không ⋮ / A–Z), Telegram lưới 2 cột không có kênh 0 bài, Tìm dùng bàn phím máy + 🎤 (ẩn nếu máy không có nhận giọng nói).
- dc5232a móc vào TelegramPreviewActivity: `applyMeloLayer()` ưu tiên Car > Gọn > giao diện cũ; Car ẩn thanh hệ thống, tắt Car thì hiện lại; giả lập FYT 160/240 trong khung 1920×1200; nhận giọng nói qua `RecognizerIntent` (vi-VN), chỉ điền vào ô tìm, không lưu; manifest thêm `<queries>` cho Android 11+. Cài đặt giao diện thêm mục "Car Mode".
- 08572c6 bump shareable vc89 / 0.6.68-beta.
- Test Ni: Debug và Release mỗi biến thể 1.107 tests, 1.086 passed, 21 skipped, 0 failed (+10 MeloCarPolicyTest).
- Ghi chú: tên kênh trên thẻ Telegram Car dùng cỡ tên bài (40px) thay vì 22sp của đặc tả cho dễ đọc từ 70cm. Nút vô lăng vẫn do MediaSession của service xử lý (không đổi). [CẦN XÁC NHẬN] trên FYT thật: xdpi firmware báo, bàn phím tiếng Việt, có dịch vụ nhận giọng nói không, nút vô lăng (bước 8).
- **Beta89 build Tiny + upload Drive XONG (17/10/2026):** bundle `D:\Bots\_transfer\MeloVault_Beta89_d8e5992-50ffa5b_delta_20261017.bundle` (28.717 bytes, SHA256 35FC2A04CA64EA9A27E7210042802907F32B595FBE254ABB5A8FA9B2A89D1028, khớp trên Tiny), clone cô lập fast-forward d8e5992 → 50ffa5b. Test Tiny Debug và Release mỗi biến thể 105 suites, 1.107 tests, 1.086 passed, 21 skipped, 0 failed; Telegram config có mặt. APK `C:\Bots\_qa_temp\Beta89_50ffa5b\MeloVault_Shareable_Beta89_vc89_0.6.68-beta_50ffa5b_UNTESTED.apk`, 30.292.526 bytes, **SHA256 8903EFD5743E8DFA0C961B7E5C3ED8CE40C4918C61E73A2E1A74741E84835A56**; aapt2 vc89 0.6.68-beta; signer eecd3df7…ce8 (trùng). Drive: `H:\My Drive\MeloVault-Owner-Beta-v0.6.68-b89-Y700-REVIEW` (APK + SHA256.txt + README_OWNER_ONLY.txt; SHA256 khớp). Chưa cài, chưa Sony QA, chưa cài FYT.
- **Gate cài Beta87 lên Y700:** chỉ hướng dẫn cài đè (`adb install -r`, không bao giờ gỡ app) khi SHA-256 chứng chỉ ký của APK Beta87 build trên Tiny TRÙNG với Beta86 đang cài (eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8), so bằng `apksigner verify --print-certs`. Khác khóa → dừng, báo chủ dự án.

### Chưa làm / chưa nghiệm thu
- Theme 04 (việc tiếp theo: thiết kế từ ảnh concept của chủ dự án), các theme sau, 9 concept UI đầy đủ và tinh chỉnh màu từng theme.
- Sony chưa test: IME/bàn phím khi ngang, fontScale > 1, MEDIA_BUTTON/phím vô lăng, phát nền lâu ([CẦN XÁC NHẬN] Y700 QA của chủ dự án có phủ các case này không).
- Google Drive/OneDrive như nguồn nhạc cho tài khoản Shareable; chưa có.
- Phát hành public/Google Play, release signing key quản lý chuyên nghiệp (hiện signer Preview dùng debug config).
- [CẦN XÁC NHẬN] kiểm thử đa tài khoản TDLib end-to-end theo mọi case trên thiết bị mới, và tiêu chí bit-perfect/DSP FYT.

## 6. Bug đã biết, workaround, chỗ dễ vỡ

- **Beta85 offline bị sai tổng do chỉ lọc trang đầu 200:** E9.3-03D đã sửa trong code, **chưa QA thiết bị**. Không dùng con số “9 bài” của screenshot Y700 làm tổng offline thật.
- **Cùng bài xuất hiện từ nhiều nhóm Telegram:** UI E9.3-03D nhóm file cùng path/cùng tên có thể mở danh sách bản gốc; không xóa dựa vào title. Nếu khác nội dung nhưng cùng tên chỉ là nhóm mơ hồ.
- **Cockpit Y700 ngang ảnh bìa chiếm chiều cao, nút phụ dưới taskbar:** đã có measured fit + nút 48dp + overflow; Sony QA đạt và chủ dự án QA Y700 đạt ở Beta86 (17/10/2026). Cần thử fontScale/IME/cover/no-cover và nút bấm thực. Đừng kết luận dựa mỗi math unit test.
- **getMessageLocally có thể thiếu metadata cho file offline:** Unknown không có nghĩa chưa tải. Hệ thống có thể không xác minh hết các file cache đã có từ phiên trước.
- **Scan 30k bài, progress UI có thể giật:** worker riêng, batch 32; nhưng mỗi trang có thể tái tính nhóm, update cachedSongPaths và adapter trên UI; đo responsiveness/ANR thật trước tối ưu. Không hash toàn bộ file trong UI thread.
- **file_id TDLib stale:** phải resolve fresh getMessage theo chat+message khi playback; đừng dùng persisted fileId qua các phiên.
- **Tài khoản, noBackup, path traversal/callback muộn:** kiểm tra authorized account + epoch/generation + canonical path, không copy/merge TDLib DB/cache.
- **Source overview header count:** đã sửa từ Beta85; tránh vô tình trộn số bài của kênh riêng với tổng.
- **Insets Android taskbar + keyboard:** padding đúng một lần; không hide bars hoặc chồng mini player/danh sách.
- **Release ký bằng debug key; build có thể PASS nhưng Telegram API ID 0:** luôn kiểm tra config riêng tại Tiny và signature/version trước APK thực.
- **21 skipped mỗi variant:** [CẦN XÁC NHẬN] lý do/coverage trước public release; JVM test không chứng minh UI/TDLib thực.

Workaround: dùng Beta85 owner-only đã cài làm rollback; mọi thay đổi lỗi ở E9.3-03D quay về Ni Codex để sửa, không gỡ/xóa data người dùng, không chạy đồng thời Agent sửa code hai máy.

## 7. Quy ước code, naming, repo và quyền thao tác

- Kotlin file ở app/src/{main,shareable,personal}/java/com/bsdung/musicassistantcar; Shareable Telegram ở data/telegram, test cùng package dưới app/src/test/java/...
- PascalCase class, camelCase biến/hàm; file *Policy.kt cho quy tắc tương đối thuần dễ JVM test, *Dao.kt cho contract persistence, *Database.kt cho SQLite, *Entities.kt cho data class, *Coordinator.kt cho tiến trình, *Test.kt cho regression.
- Không đổi songKey/composite primary key/DB schema/signing/package tùy tiện; không hardcode số bài, số nguồn, album artwork hay thông số sample rate/bitrate giả.
- Không thêm framework UI mới (ví dụ Compose) hoặc viết lại architecture nếu không có lệnh; giữ mạch Android Views đang hoạt động.
- Main thread chỉ thao tác hiển thị; DB/TDLib scan/tệp âm thanh chạy thread pool; cancellation theo owner/generation/lifecycle.
- .gitignore đã bỏ local.properties, secrets.properties, *.keystore/*.jks, APK/log/debug cache, bridge/.env, session files. Không đưa OTP/password, Telegram API ID/hash, bot token, owner backend secrets, noBackup files, TDLib session keys/cache lên Git/Drive.
- Mỗi vòng: kiểm tra Git trạng thái; commit scoped trên Ni; test Debug+Release; git diff --check; tạo Git bundle delta với SHA256; kiểm tra prerequisites rồi fetch vào clone Tiny cô lập; Tiny build/QA; lưu report mới; đợi gate tiếp.
- Không dùng git reset --hard/clean/stash không xin phép; không đồng thời dùng Antigravity sửa cùng branch khi Codex Ni đang làm.
- Không xóa APK Beta cũ. Không uninstall Android app, pm clear, logout Telegram, xóa database/cache/playlist, can thiệp private Stable/backend, upload public hay cài FYT nếu không có quyền riêng.
- Chủ dự án đã duyệt tự động upload private APK đã *qua kiểm toán và QA* lên Google Drive riêng, KHÔNG duyệt upload artifact build-only đang thiếu auth hoặc APK chưa thử thiết bị.

## 8. Việc làm tiếp theo theo thứ tự ưu tiên

1. **P0 — Kiểm tra trạng thái live.** git HEAD/branch/status trên Ni và Tiny, các agent/process đang chạy, tài liệu handoff. Nếu đã có commit vc86, đừng bump lại; nếu source dirty hoặc branch khác thì dừng để đối chiếu. Chặn trial APK vc85 thiếu auth.
2. **P1 — Bump Beta86 trên Ni. [ĐÃ XONG — b490ded]** Sửa riêng app/build.gradle shareable: versionCode 86; versionName 0.6.65; suffix -beta. Giữ Personal. Commit riêng và chạy ít nhất 2 biến thể test, diff-check. Không đưa Telegram credentials sang Ni.
3. **P2 — Chuyển commit mới sang Tiny clone cô lập. [ĐÃ XONG — bundle 83b66c1..f2eae36, SHA256 khớp trên Tiny, fast-forward clone cô lập lên f2eae36]** Git delta bundle từ đúng HEAD trước đó, verify SHA256/prerequisite, import vào C:\Bots\MeloVault-E93D-IsolatedQA; giữ nguyên C:\Bots\MeloVault-E91-Shareable Beta85 và Stable.
4. **P3 — Build Tiny hợp lệ. [ĐÃ XONG — APK vc86 0.6.65-beta, SHA256 98FA98F6…5D85, C:\Bots\_qa_temp\Beta86_f2eae36\ trên Tiny; chưa cài/upload]** Dùng local cấu hình Telegram hợp lệ được cấp và giữ **chỉ trên Tiny** ngoài Git; không in/lộ raw ID/hash. Test Debug+Release, assembleShareableRelease; verify package app.melovault.player, vc86 / 0.6.65-beta, signer Preview cũ, SHA256, privacy/manifest. Lưu đường dẫn APK riêng, không overwrite.
5. **P4 — Sony QA tại chỗ [ĐẠT 17/10/2026 — xem mục 5; 2 lỗi nhỏ đã sửa ở 2a9ac8e, chưa có trong APK]** dùng đúng APK SHA256 98FA98F62E6D14FFB7D45F43629B7E8DD4FADCCF8CE9C55086DF50263FEA5D85 (verify lại SHA256/package/version/signer ngay trước khi cài): adb install -r (không uninstall/pm clear). Test nhạc đã tải nằm sau 200 bài, grouping/chooser từng nguồn, queue/playlist/favorite, playback giữ trạng thái, source Bento, Cockpit ngang và tất cả action phía trên taskbar, IME/insets, sự mượt khi sync 30k bài. Ảnh/video và báo cáo không tiết lộ private Telegram data. Restore mọi WM/density/rotation Sony đã thay.
6. **P5 — ChatGPT độc lập audit native QA [ĐÃ XONG — APK dbafe3e B5F9C261…523F upload Drive riêng MeloVault-Owner-Beta-v0.6.65-b86-Y700-REVIEW; Y700 đạt 17/10/2026]**, rồi tự upload APK lên owner-only Drive theo ủy quyền trước đó, verify checksum/quyền truy cập; chủ dự án cài đè và kiểm thử trên Lenovo Y700 Gen 3.
7. **P6 — Chốt Theme 03 [ĐÃ XONG — chốt Beta86 và Theme 03, 17/10/2026]** khi owner xác nhận. Nếu Y700 lỗi, quay về Ni Codex sửa E9.3-03D, lặp lại gate. Chỉ sau đó mới lập plan Theme 04 và các theme khác; Drive/OneDrive nguồn nhạc là workstream riêng, không làm chen ngang.
8. **P6b — Giao diện mới theo docs/ui-spec.md [ĐANG LÀM — bước 1–3 ở Beta87 (đã cài S25 Ultra), bước 4 Gọn ở Beta88, bước 5 Car Mode ở Beta89; Y700 bỏ đo; việc tiếp: chủ dự án thử Beta89 ("Thử trong khung FYT" khi cầm ngang), rồi bước 6 Đang phát Studio Deck; tìm không dấu là việc riêng]:** (trước đây ghi là Theme 04) thiết kế từ ảnh concept của chủ dự án. Chỉ bắt đầu khi chủ dự án gửi ảnh và giao việc; giữ Android Views/AppCompat, không thêm framework UI, lập plan trước khi code. Bản phát hành có Theme 04 phải bump versionCode ≥ 87 và đi lại đủ gate.
9. **P7 — Trước public/FYT:** [CẦN XÁC NHẬN] full two-account isolation, 21 skipped, provenance native TDLib, long-lived signing key, privacy audit và thiết bị FYT; tuyệt đối không mặc định sẵn sàng chỉ vì unit tests xanh.

## 9. Tài liệu nguồn và độ ưu tiên

Ưu tiên theo thứ tự: **Git+source hiện tại > test XML/build report vừa chạy > QA thiết bị có artifact > báo cáo E9.3-03D > handoff Beta85 > docs E9.1 lịch sử**.

- D:\Bots\_handoff\MeloVault\CODEX_NI_E93_03D_IMPLEMENTATION_REPORT.md
- D:\Bots\_handoff\MeloVault\CHATGPT_NI_INDEPENDENT_AUDIT_E93_03D_20261017.md
- D:\Bots\_handoff\MeloVault\CHATGPT_TINY_TRANSFER_BUILD_GATE_E93_03D_20261017.md
- D:\Bots\_handoff\MeloVault\FIELD_REPORT_E93_03D_Y700_BETA85_DOWNLOADED_DUPLICATES_COCKPIT_CLIP_20261017.md
- D:\Bots\_handoff\MeloVault\WORK_ORDER_E93_03D_Y700_OFFLINE_DUPES_COCKPIT_FIT_20261017.md
- D:\Bots\_handoff\MeloVault\LATEST_HANDOFF.txt — chỉ tới E9.3-03C/Beta85, **không phải checkpoint E9.3-03D mới nhất**.
- Z:\MusicAssistant\AgentBridge\artifacts\e91-shareable\... — handoff/QA Tiny nếu Google DriveFS đang mount; global AgentBridge\LATEST_HANDOFF.txt có thể thuộc dự án khác hoặc bị Access Denied.
- Các file E91_SHAREABLE_TELEGRAM_PLAN.md, E91_TDLIB_NATIVE_INTEGRATION.md và E9_RELEASE_GATES.md trong repo là **hồ sơ lịch sử**; dòng “chưa có TDLib/login” ở giai đoạn cũ **không thể coi là trạng thái Beta85/E9.3-03D hiện hành**.

**AI tiếp quản:** Không tuyên bố hoàn tất điều chưa thử trên thiết bị; mọi bất định phải ghi **[CẦN XÁC NHẬN]**. Không tự phá điểm dừng, không tự triển khai Theme04/FYT hoặc sửa Stable.
