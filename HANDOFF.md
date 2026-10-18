# HANDOFF — MeloVault (Shareable Preview)

**Snapshot:** 18/10/2026. Lịch sử Beta85–95 (bundle, SHA256, báo cáo QA từng bản) ở `docs/history/HANDOFF_ARCHIVE_2026-10.md`. Điều chưa kiểm chứng ghi **[CẦN XÁC NHẬN]**. Luôn kiểm Git live trước khi hành động.

## 1. Môi trường
- **Ni** (Windows): repo `D:\Bots\MeloVault-E91-Shareable`, branch `feature/e93-03d-y700-offline-cockpit`. Claude Code làm code ở đây. Gradle chạy bằng JDK 21; SDK `C:\Users\ntdun\.cache\codex-toolchains\android-sdk`.
- **Tiny** (`DESKTOP-HNP4GVR`, ổ `T:` trên Ni): chỉ build/QA. Clone cô lập `C:\Bots\MeloVault-E93D-IsolatedQA`, tạm `C:\Bots\_qa_temp\`, SDK `C:\Android\Sdk`, Drive `H:\My Drive`. Code sang Tiny bằng Git bundle delta + SHA256 (`D:\Bots\_transfer\`).
- Telegram API ID/hash chỉ ở `local.properties` trên Tiny. Build Ni có ID 0: PASS nhưng APK không dùng được.
- Không đụng: Private Stable `C:\Bots\MusicAssistantCar`, backend `C:\Bots\MusicAssistant`, `C:\Bots\TelegramSecretary`, worktree Beta85 gốc trên Tiny, session/cache TDLib.
- Thiết bị: Sony XQ-AS52 (QA qua adb trên Tiny), Lenovo Y700 Gen 3 và S25 Ultra (chủ dự án tự QA), Shanling M3 Ultra, FYT 7870 (chưa có thiết bị thật).

## 2. Sản phẩm và stack
Ứng dụng nghe nhạc Android, nguồn là tài khoản Telegram của chính người dùng (TDLib), phát bằng Media3. Flavor `shareable` (`app.melovault.player`, launcher `TelegramPreviewActivity`, `ShareablePlaybackService`); flavor `personal` giữ nguyên, không đụng. Nguồn Drive/OneDrive cho Shareable chưa có. Có 9 concept UI nhưng không có 9 theme hoàn chỉnh.

Kotlin/JVM 17, Gradle 8.13, AGP 8.13.0, Kotlin 2.2.10, compileSdk/targetSdk 36, minSdk 26, một module `:app`. Android Views/AppCompat (không Compose). Media3 1.11.1, WorkManager 2.9.1, SQLite DAO tự viết, TDLib JNI/JSON (revision binary **[CẦN XÁC NHẬN]**), JUnit4.

Test mốc Beta95: mỗi biến thể 1.132 tests, 1.111 passed, 21 skipped (**[CẦN XÁC NHẬN]** lý do), 0 failed. Release ký bằng chữ ký Preview cũ (debug config), signer SHA-256 `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`.

## 3. Kiến trúc
- Code Shareable: `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/`; test JVM `app/src/test/java/.../data/telegram/`.
- Luồng dữ liệu: TDLib → `ShareableTdlibOwner` + `TelegramAuthorizationFlow` + `TelegramSessionEpoch` → `TelegramSyncCoordinator` (PAGE_LIMIT 100, phase audio/document, checkpoint, backoff) → `TelegramLibraryDao` / SQLite `melovault_tg_index_{accountId}.db` → UI. Chỉ coi đã đăng nhập khi `authorizationStateReady` VÀ `getMe()` thành công. Khóa bài `(accountId, chatId, messageId)`; `songKey` 64-bit; file_id TDLib theo phiên, không dùng lại từ DB.
- Phát: chọn bài → `getMessage` lấy file_id mới → `TelegramProgressiveCache` + `TelegramOwnerAudioDataSource` (scheme `melovault-tdlib`) → ExoPlayer trong `ShareablePlaybackService` + MediaSession. `NextAwarePlayer` (ForwardingPlayer) cho Bài kế từ màn khoá/tai nghe/BT; callback nền của service phải chuyển tiếp cả `message` (thẻ `background:resolve:`).
- Offline: `TelegramOfflineLibrary.scan` (trang 200, resolver 32/batch); chỉ xác nhận offline khi TDLib báo tải xong, file thuộc root chủ, đọc được, size khớp; thiếu metadata = "chưa xác minh". Bản trùng tên nhóm ở UI kèm chooser, không xóa/gộp dữ liệu.
- Giao diện mới theo `docs/ui-spec.md`, file `Melo*.kt`: `MeloDesignTokens`, `MeloComponents`, `MeloScreenPolicy`. Lớp view phủ lên `TelegramPreviewActivity` qua `MeloScreenHost` (không tự truy vấn DB/TDLib); `applyMeloLayer()` ưu tiên Car > Gọn > Mới (Phone/Tablet + `MeloLibraryPane`) > cũ (Cockpit/Portrait). `MeloCompactScreen` (Shanling), `MeloCarScreen` (FYT), `MeloStudioDeck` (Đang phát; hẹn giờ + công tắc tải trước trong service), `MeloWaveform`. Cài đặt giao diện lưu ở prefs `melovault_ui_lab`.
- Main thread chỉ hiển thị; DB/TDLib/scan chạy executor, hủy theo owner/generation/lifecycle.

## 4. Quyết định đã chốt
1. Shareable/Personal tách package + backend. 2. TDLib user client, không Bot API. 3. getMe + epoch + file theo tài khoản. 4. Khóa (account, chat, message), fileId theo phiên. 5. SQLite + checkpoint + paging. 6. Media3 + TDLib progressive DataSource. 7. Không dedup phá hủy. 8. Theme mặc định Portrait Edge-to-Edge Pro #3DDC97; Theme 02 Cockpit, Theme 03 Bento đã chốt (Beta86). 9. WindowInsets một lần, đo khung thật. 10. Ni code, Tiny QA qua bundle có SHA256. 11. Gate: test JVM → commit → bundle → Tiny build → verify signer → Sony QA → Drive riêng → chủ dự án QA Y700. 12. Y700 không đo thêm; S25 Ultra (384×832dp) dọc = Điện thoại, ngang ≥600dp luôn Hai khung. 13. Hai APK cùng vc86 chỉ phân biệt bằng SHA256 → mọi bản sau bump versionCode.

## 5. Bản mới nhất
**Beta95** = bản mới nhất trên Drive: commit `9c2c521`, vc95 / `0.6.74-beta`, SHA256 `8C07116077287F63FA43F4FD88C44A1FE203A05AE3561DBE34BA3797D888E0AF`, signer `eecd3df7…ce8`; Drive `H:\My Drive\MeloVault-Owner-Beta-v0.6.74-b95-Y700-REVIEW` (b94 bị thay thế, không dùng). HEAD Ni sau đó chỉ thêm commit docs (`77121c7`). Sony đã QA bước 8: Bài kế khi tắt màn với bài streaming chạy trong 8 s. Beta86 (vc86, SHA256 `B5F9C261…523F`) là bản đã chốt và là rollback; hạ version có thể bị Android đòi gỡ app → KHÔNG gỡ, hỏi chủ dự án.

Đặc tả `docs/ui-spec.md` 8 bước đã hoàn tất về code.

## 6. Bug / rủi ro đang mở
- **Test mạng flaky: ĐÃ SỬA** (commit `9597332`, chỉ đổi test; không nằm trong APK Beta95 vì không bump/build). Nguyên nhân: RST sau sleep 100 ms làm mất byte client chưa đọc; server test nay chờ client báo đã đọc xong rồi mới RST. 5 lần Debug + 5 lần Release xanh trên Ni và Tiny. Chi tiết: `docs/plans/BACKLOG.md` mục 4. Clone Tiny đã ff tới `9597332`.
- **Chưa kiểm trên thiết bị thật [CẦN XÁC NHẬN]:** nút vật lý Shanling khi tắt màn; nút vô lăng FYT (xdpi firmware, bàn phím tiếng Việt, nhận giọng nói); tai nghe/đồng hồ BT; màn khoá S25 (Android 17, cần quyền thông báo); vuốt app khỏi recents; tự sang bài khi hết bài lúc tắt màn; tốc độ phân tích sóng âm và native heap khi quét offline trên Shanling 3GB; lưới 3 cột Hai khung trên Y700; IME ngang, fontScale > 1.
- Studio Deck: núm thanh tua tròn ở vị trí 0 bị cắt nửa khi chưa có sóng âm (nhỏ).
- Tìm kiếm phân biệt dấu (DB dùng `LIKE`): "su thanh hoa" không ra "Sử Thanh Hóa".
- Thiếu so với đặc tả bước 7: A–Z, đổi kênh nhanh ở khung trái Hai khung, dòng "Các kênh chưa có nhạc (N)", chip Mixset theo thời lượng.
- Sự cố đã biết khi test Sony: BACK thừa thoát app làm cú bấm sau rơi vào launcher Sony; script phải kiểm app ở tiền cảnh trước mỗi bước.
- Release ký bằng debug key; 21 test skipped; chưa kiểm đa tài khoản end-to-end; chưa sẵn sàng public/FYT.

## 7. Việc tiếp theo
Xem `docs/plans/BACKLOG.md` (A–Z, EQ, tìm không dấu). Mỗi phiên làm đúng một việc theo `docs/plans/<việc>.md`. Theme 04 và FYT chỉ làm khi chủ dự án giao; Drive/OneDrive là workstream riêng.
- Tìm không dấu: kế hoạch `docs/plans/tim-khong-dau.md` (18/10/2026) — thêm cột `search_key`, DB v1→v2 bằng `ALTER TABLE ADD COLUMN` + điền lại trong giao dịch `onUpgrade`, không mất dữ liệu. **Chờ chủ dự án duyệt đổi schema** trước khi thực hiện.

## 8. Nguồn tài liệu
Ưu tiên: Git + source > test XML/build vừa chạy > QA thiết bị có artifact > HANDOFF này > `docs/history/` và các tài liệu E9/E91 ở thư mục gốc (chỉ là lịch sử). Báo cáo QA từng bản nằm trên Tiny dưới `C:\Bots\_qa_temp\` (ảnh chứa tên bài riêng tư, chỉ giữ local); handoff cũ ở `D:\Bots\_handoff\MeloVault\`.
