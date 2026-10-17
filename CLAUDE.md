# CLAUDE.md — MeloVault (Shareable Preview)

Trả lời chủ dự án bằng tiếng Việt. Ứng dụng nghe nhạc Android, nguồn nhạc là tài khoản Telegram của chính người dùng (TDLib), phát bằng Media3. Chi tiết đầy đủ: `HANDOFF.md` (đọc khi cần, không đọc lại cả file mỗi lượt). Thứ tự tin cậy: Git + source hiện tại > test XML/build vừa chạy > QA thiết bị có artifact > HANDOFF.md > tài liệu E9/E91 cũ ở thư mục gốc (chỉ là lịch sử).

## Máy và thư mục
- **Ni** (máy này): repo lập trình chính `D:\Bots\MeloVault-E91-Shareable`. Claude Code thay Codex làm code ở đây.
- **Tiny**: máy build/QA, xuất hiện trên Ni dưới dạng ổ `T:` (= `C:\Bots` của Tiny). Clone QA cô lập: `C:\Bots\MeloVault-E93D-IsolatedQA`. Code chuyển sang Tiny bằng Git bundle có SHA256, không chép thư mục, không sửa trực tiếp qua `T:`.
- KHÔNG đọc/sửa/tác động: Private Stable `C:\Bots\MusicAssistantCar`, backend `C:\Bots\MusicAssistant`, `C:\Bots\TelegramSecretary`, worktree Beta85 gốc `C:\Bots\MeloVault-E91-Shareable` trên Tiny, mọi session/cache TDLib của người dùng.

## Đầu mỗi phiên (bắt buộc)
```powershell
git status --short; git branch --show-current; git rev-parse HEAD; git log -5 --oneline; git diff --check
```
Nếu working tree bẩn bất ngờ, branch lạ, hoặc có agent khác (Codex, Antigravity) đang sửa cùng branch: DỪNG và báo chủ dự án. Không bao giờ hai agent cùng sửa một branch.

## Cấm tuyệt đối (trừ khi chủ dự án cho phép rõ ràng trong chat)
- `git reset --hard`, `git clean`, `git stash`, `git checkout`/`restore` đè lên thay đổi, `git push --force`, viết lại lịch sử.
- `adb uninstall`, `pm clear`, logout Telegram, xóa database/cache/playlist/APK Beta cũ.
- Cài/gửi/upload APK chưa qua gate (ngoại lệ: upload Drive riêng mặc định theo mục "Quy trình gate" bên dưới). APK vc85 build thử ở Tiny (thiếu Telegram API config) KHÔNG được cài.
- Đọc, in, log, commit hoặc chuyển đi: `local.properties`, `secrets.properties`, Telegram API ID/hash, keystore, OTP/2FA, file noBackup/session TDLib. Telegram credentials chỉ nằm trên Tiny.
- Đổi package, signing, `songKey`, khóa chính `(accountId, chatId, messageId)`, schema DB khi chưa được duyệt.
- Thêm framework UI mới (Compose...) hoặc viết lại kiến trúc. UI Shareable dùng Android Views/AppCompat.
- Tự làm Theme 04, cài lên FYT, publish công khai, động vào flavor `personal`.
- Tuyên bố "đã xong/đã chạy" khi chưa test trên thiết bị. Unit test xanh ≠ đã nghiệm thu. Điều chưa chắc ghi **[CẦN XÁC NHẬN]**.

## Build và test (PowerShell, tại repo)
```powershell
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug --console=plain
.\gradlew.bat :app:assembleShareableRelease --console=plain
git diff --check
```
Mốc hiện tại (Beta89, sau bước 5 ui-spec): mỗi biến thể 1.107 tests, 1.086 passed, 21 skipped, 0 failed. Skipped không tính là pass. Build trên Ni có Telegram API ID 0 là bình thường; build PASS không có nghĩa APK dùng được.

## Stack
Kotlin, Java/JVM 17 (Gradle chạy bằng JDK 21), Gradle 8.13, AGP 8.13.0, Kotlin 2.2.10, compileSdk/targetSdk 36, minSdk 26. Một module `:app`, flavor `personal` / `shareable` (`app.melovault.player`). Media3 1.11.1, WorkManager, SQLite tự viết DAO, TDLib JNI/JSON, JUnit4.

## Kiến trúc cần nhớ
- Code Shareable: `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/`. Test JVM: `app/src/test/java/.../data/telegram/`.
- Luồng: TDLib → `ShareableTdlibOwner` + `TelegramAuthorizationFlow` + `TelegramSessionEpoch` → `TelegramSyncCoordinator` → `TelegramLibraryDao` / SQLite `melovault_tg_index_{accountId}.db` → UI `TelegramPreviewActivity`.
- Phát: chọn bài → `getMessage` lấy file_id MỚI của phiên hiện tại (không dùng file_id lưu trong DB) → `TelegramProgressiveCache` + `TelegramOwnerAudioDataSource` → ExoPlayer trong `ShareablePlaybackService` + MediaSession.
- Chỉ coi là đã đăng nhập khi `authorizationStateReady` VÀ `getMe()` thành công. Mọi file đi qua kiểm tra account/epoch/canonical path.
- Không dedup phá hủy: bài trùng tên chỉ nhóm ở UI kèm chooser.
- Main thread chỉ hiển thị; DB/TDLib/scan file chạy executor, hủy theo owner/generation/lifecycle.

## Quy ước
- PascalCase class, camelCase hàm/biến. `*Policy.kt` = logic thuần dễ test JVM, `*Dao.kt`, `*Database.kt`, `*Entities.kt`, `*Coordinator.kt`, `*Test.kt`.
- Mỗi thay đổi: commit nhỏ, đúng phạm vi, message kiểu `fix(shareable): ...` / `feat(...)` / `docs: ...`. Chạy test cả Debug + Release và `git diff --check` trước khi commit.
- Không hardcode số bài, số nguồn, artwork, sample rate/bitrate giả.

## Quy trình gate (không bỏ bước)
Test JVM trên Ni → commit → Git bundle delta + SHA256 → import vào clone cô lập trên Tiny → build có Telegram config hợp lệ (chỉ trên Tiny) → verify package/version/signer/SHA256 → QA Sony → audit độc lập → upload Drive riêng (đã được duyệt sau khi qua QA) → chủ dự án QA trên Lenovo Y700.

**Mặc định mới (chủ dự án duyệt trong chat 17/10/2026, áp dụng từ Beta87):** sau khi bump + bundle, Claude tự build trên Tiny và tự upload Drive riêng, không cần hỏi lại:
1. Tiny = Desktop Commander device `DESKTOP-HNP4GVR`. Chỉ thao tác trong clone cô lập `C:\Bots\MeloVault-E93D-IsolatedQA` (fast-forward `--ff-only`, dừng nếu bẩn/HEAD lạ) và `C:\Bots\_qa_temp\Beta<code>_<sha>\`. Không đọc/in Telegram config; chỉ kiểm có/không trong BuildConfig.
2. Build test Debug+Release + `assembleShareableRelease`; verify aapt2 (package/vc/versionName) + `apksigner --print-certs`. Signer phải = `eecd3df7…ce8`; khác thì DỪNG, không upload, báo chủ dự án.
3. Upload: tạo thư mục MỚI `H:\My Drive\MeloVault-Owner-Beta-v<versionName>-b<versionCode>-Y700-REVIEW` trên Tiny (Google Drive for desktop), chứa `MeloVault-Owner-Beta-v<ver>-b<code>.apk`, `SHA256.txt`, `README_OWNER_ONLY.txt` (theo mẫu thư mục b86). Kiểm SHA256 bản trên H:. Không ghi đè/xóa thư mục cũ, không chia sẻ công khai, không đụng `melovault-private-signer`.
4. README ghi rõ đã/không QA Sony. Vẫn KHÔNG tự cài lên thiết bị, KHÔNG cài FYT.

## Trạng thái và việc tiếp theo
- Branch `feature/e93-03d-y700-offline-cockpit`. Mốc an toàn: tag `pre-claude-migration` (= 83b66c1, code E9.3-03D, chưa QA thiết bị).
- P1 đã xong: commit `b490ded` bump shareable lên vc86 / `0.6.65-beta`; test sau bump giữ mốc 1.029 passed / 21 skipped / 0 failed mỗi biến thể.
- P2 phía Ni đã xong: bundle `D:\Bots\_transfer\MeloVault_E93_03D_Beta86_83b66c1-f2eae36_delta_20261017.bundle` (SHA256 `527991A3…B764C`, đủ giá trị trong HANDOFF.md và file `.sha256` cạnh bundle). Bundle tạo bằng `git bundle create <file> <tiny-HEAD>..<branch>`, rồi `git bundle verify` + sidecar `.sha256`; không ghi đè bundle cũ.
- P2–P6 đã xong, **Beta86 và Theme 03 đã chốt (17/10/2026)**. APK chính thức build từ `dbafe3e` (có fix chooser `2a9ac8e`), vc86 `0.6.65-beta`, SHA256 `B5F9C261…523F`; đã upload Drive riêng, chủ dự án QA Y700 đạt. APK vc86 cũ từ `f2eae36` (`98FA98F6…5D85`) đã bị thay thế: không cài/gửi.
- Hai APK cùng vc86 chỉ phân biệt bằng SHA256 → bản phát hành sau phải bump versionCode ≥ 87.
- Giao diện mới theo `docs/ui-spec.md`: bước 1–3 code xong ở Beta87 (vc87 `0.6.66-beta`), file `Melo*.kt`; chưa build Tiny/QA. Màn mới dùng `MeloDesignTokens` + `MeloComponents` (kích thước px từ hồ sơ), không sửa màn cũ.
- Cài Beta87 lên Y700 chỉ khi SHA-256 cert ký trùng Beta86 (`eecd3df7…ce8`); khác thì dừng. Không bao giờ gỡ app.
- APK Beta87 đã build + verify trên Tiny (SHA256 `2D8E621D…71EE`, signer trùng Beta86). Build trên Tiny chạy qua Desktop Commander, máy `DESKTOP-HNP4GVR` = Tiny, chỉ trong clone cô lập + `C:\Bots\_qa_temp\`.
- Bước 4 (bản Gọn Shanling) code xong ở Beta88 (vc88 `0.6.67-beta`): `MeloCompactScreen` là lớp view phủ lên `TelegramPreviewActivity` qua interface `MeloScreenHost`; KHÔNG tự truy vấn DB/TDLib. Bước 5 (Car Mode) ở Beta89 (`MeloCarScreen`, cùng mẫu; `applyMeloLayer()` ưu tiên Car > Gọn > cũ). Màn mới sau này đi theo cùng mẫu.
- Y700 không đo (chủ dự án bỏ). Tìm không dấu là việc riêng, có thể cần đổi schema → phải được duyệt.
- Việc kế tiếp (chỉ làm khi chủ dự án yêu cầu): chủ dự án thử Beta89, rồi bước 6 (màn Đang phát Studio Deck). Xem mục 5 và 8 của `HANDOFF.md`.
- Commit trên Ni: máy chưa có git identity; dùng `git -c user.name="Claude Code" -c user.email="noreply@anthropic.com" commit ...` (không sửa git config), giống cách Codex dùng identity riêng.
- Kết thúc mỗi phần việc: cập nhật mục "Đang làm dở" và "Việc làm tiếp theo" trong `HANDOFF.md`, cập nhật file này nếu có quy ước mới, rồi commit.
