# CLAUDE.md — MeloVault (Shareable Preview)

Trả lời chủ dự án bằng tiếng Việt. Ứng dụng nghe nhạc Android, nguồn Telegram của chính người dùng (TDLib), phát bằng Media3. Trạng thái, kiến trúc, bug mở: `HANDOFF.md`; lịch sử: `docs/history/`; việc còn lại: `docs/plans/BACKLOG.md`. Độ tin cậy: Git + source > test/build vừa chạy > QA thiết bị có artifact > HANDOFF.md > tài liệu E9/E91 cũ.

## Quy ước phiên
- Mỗi phiên làm đúng 1 việc theo `docs/plans/<việc>.md`. Xong thì cập nhật `HANDOFF.md` (bản mới nhất, bug mở, việc tiếp) và dừng.
- Đầu phiên: `git status --short; git branch --show-current; git rev-parse HEAD; git log -5 --oneline; git diff --check`. Cây bẩn bất ngờ, branch lạ hoặc agent khác đang sửa cùng branch: DỪNG và báo. Không bao giờ hai agent cùng sửa một branch.
- Commit nhỏ, đúng phạm vi: `fix(shareable): ...` / `feat(...)` / `docs: ...`. Chạy test Debug + Release và `git diff --check` trước khi commit. Ni chưa có git identity: `git -c user.name="Claude Code" -c user.email="noreply@anthropic.com" commit ...` (không sửa config).
- Điều chưa chắc ghi **[CẦN XÁC NHẬN]**. Không tuyên bố "đã xong/đã chạy" khi chưa test trên thiết bị; unit test xanh ≠ nghiệm thu.

## Kết thúc mỗi phiên: báo cáo cho chủ dự án (bắt buộc)
Chủ dự án không biết lập trình. Câu trả lời cuối của MỌI phiên phải có mục **"Đề xuất cho anh"**, viết bằng tiếng Việt thường, không thuật ngữ code, gồm đúng 4 phần:
1. **Vừa xong:** 1–2 câu, kết quả nhìn thấy được trên app (không kể tên file/hàm).
2. **Anh cần làm:** việc cụ thể trên máy (cài bản nào từ thư mục Drive nào, bấm thử gì, chụp gì gửi lại), hoặc "Không cần làm gì".
3. **Việc nên làm tiếp:** đề xuất 1 việc (lấy từ `docs/plans/BACKLOG.md` hoặc lỗi vừa phát hiện), lý do 1 câu.
4. **Câu lệnh cho phiên tiếp theo:** ghi rõ model và chế độ, rồi một khối lệnh để anh dán nguyên văn:
   - Việc nhỏ, đã rõ cách làm → **Sonnet 5.5 · Trung bình · Tự động**, lệnh thực thi trực tiếp.
   - Việc đụng dữ liệu/DB/schema, kiến trúc, hoặc chưa rõ cách làm → **Opus 5.5 · Trung bình · Kế hoạch**, lệnh yêu cầu viết `docs/plans/<việc>.md` (file sửa, bước làm, test, cách kiểm trên Sony, tiêu chí đạt), không sửa code, commit rồi dừng. Phiên sau đó dùng **Sonnet 5.5 · Trung bình · Tự động** với lệnh "Thực hiện docs/plans/<việc>.md…".
   Nhắc anh: mỗi việc mở phiên MỚI (nút Mới), không nhắn tiếp phiên cũ.

## Máy
- **Ni**: repo `D:\Bots\MeloVault-E91-Shareable`, nơi viết code.
- **Tiny** (Desktop Commander `DESKTOP-HNP4GVR`, ổ `T:` trên Ni): chỉ build/QA trong clone cô lập `C:\Bots\MeloVault-E93D-IsolatedQA` và `C:\Bots\_qa_temp\Beta<code>_<sha>\`. Code sang Tiny bằng Git bundle + SHA256, không chép thư mục, không sửa qua `T:`.
- KHÔNG đụng: `C:\Bots\MusicAssistantCar`, `C:\Bots\MusicAssistant`, `C:\Bots\TelegramSecretary`, worktree Beta85 gốc `C:\Bots\MeloVault-E91-Shareable` trên Tiny, session/cache TDLib.

## Cấm tuyệt đối (trừ khi chủ dự án cho phép rõ trong chat)
- `git reset --hard`, `git clean`, `git stash`, `checkout`/`restore` đè thay đổi, `push --force`, viết lại lịch sử.
- `adb uninstall`, `pm clear`, logout Telegram, xóa database/cache/playlist/APK Beta cũ.
- Cài/gửi APK chưa qua gate (trừ upload Drive riêng ở dưới). APK thiếu Telegram config (build Ni, vc85 trial) KHÔNG được cài. Không cài FYT, không publish công khai.
- Đọc/in/log/commit/chuyển: `local.properties`, `secrets.properties`, Telegram API ID/hash, keystore, OTP/2FA, file noBackup/session TDLib.
- Đổi package, signing, `songKey`, khóa `(accountId, chatId, messageId)`, schema DB khi chưa duyệt.
- Thêm framework UI (Compose...) hoặc viết lại kiến trúc (UI dùng Android Views/AppCompat). Tự làm Theme 04, động vào flavor `personal`.

## Build và test (tại repo, PowerShell)
```powershell
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug --console=plain
.\gradlew.bat :app:assembleShareableRelease --console=plain
git diff --check
```
Mốc Beta95: mỗi biến thể 1.132 tests, 1.111 passed, 21 skipped, 0 failed (skipped không tính pass). Build Ni có Telegram API ID 0 là bình thường, APK không dùng được. Stack: Kotlin, JVM 17 (Gradle chạy JDK 21), Gradle 8.13, AGP 8.13.0, compileSdk/targetSdk 36, minSdk 26, module `:app`, flavor `personal`/`shareable`.

## Quy ước code
- PascalCase class, camelCase hàm/biến. `*Policy.kt` = logic thuần dễ test JVM; `*Dao.kt`, `*Database.kt`, `*Entities.kt`, `*Coordinator.kt`, `*Test.kt`.
- Main thread chỉ hiển thị; DB/TDLib/scan chạy executor, hủy theo owner/generation/lifecycle. Không dedup phá hủy. Không hardcode số bài, số nguồn, artwork, sample rate/bitrate giả. Màn Melo mới đi theo mẫu `MeloScreenHost`, không tự truy vấn DB/TDLib.

## Quy trình gate
Test JVM trên Ni → commit → bundle delta + SHA256 → import vào clone cô lập Tiny → build có Telegram config (chỉ Tiny) → verify package/version/signer/SHA256 → QA Sony → upload Drive riêng → chủ dự án QA Y700.

**Mặc định (đã duyệt, không cần hỏi lại):** sau bump + bundle, Claude tự build trên Tiny và upload Drive riêng:
1. Bundle: `git bundle create <file> <tiny-HEAD>..<branch>`, `git bundle verify`, sidecar `.sha256` trong `D:\Bots\_transfer\`; không ghi đè bundle cũ. Trên Tiny: verify SHA256, `--ff-only`, dừng nếu cây bẩn/HEAD lạ.
2. Build test Debug + Release + `assembleShareableRelease` (chạy lại một lần nếu `AudioStreamingNetworkRecoveryTest` fail lần đầu, ghi vào báo cáo); aapt2 (package/vc/versionName) + `apksigner --print-certs`. Signer phải là `eecd3df7…ce8`; khác thì DỪNG, không upload, báo chủ dự án. Chỉ kiểm Telegram config có/không, không in giá trị.
3. Upload: thư mục MỚI `H:\My Drive\MeloVault-Owner-Beta-v<versionName>-b<versionCode>-Y700-REVIEW` trên Tiny gồm `MeloVault-Owner-Beta-v<ver>-b<code>.apk`, `SHA256.txt`, `README_OWNER_ONLY.txt` (mẫu thư mục b86; ghi rõ đã/không QA Sony). Kiểm SHA256 bản trên H:. Không ghi đè/xóa thư mục cũ, không chia sẻ công khai, không đụng `melovault-private-signer`.
4. KHÔNG tự cài lên thiết bị chủ dự án. Cài Sony chỉ để QA, bằng `adb install -r`, kiểm app ở tiền cảnh trước mỗi bước, không BACK ở root, khôi phục rotation/density. Hai APK cùng versionCode chỉ phân biệt bằng SHA256 → bản sau luôn bump versionCode.
