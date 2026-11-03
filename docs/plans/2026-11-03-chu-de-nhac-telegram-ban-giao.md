# Bàn giao thực thi nhạc theo chủ đề Telegram

**TASK_ID** `2026-11-03-chu-de-nhac-telegram` · **03/11/2026 (Asia/Saigon)** · **Codex, thực thi**
**Checkpoint đang thực thi:** S1–S3 đã commit; S4 đã nối các giao diện, đang gate Ni cuối trước commit và S5 Tiny/Sony. [Kế hoạch](2026-11-03-chu-de-nhac-telegram.md) · [Báo cáo chi tiết từng lượt](2026-11-03-chu-de-nhac-telegram-bao-cao.md).

- Worktree `C:/Users/ntdun/.codex/worktrees/melovault-chu-de-telegram/MeloVault-E91-Shareable`; nhánh `dot/2026-11-03-chu-de-nhac-telegram`.
- Baseline source `b90834f44457b60a9732e59e59c1bc9c971368c3`; kế hoạch `36c0647192234560d97bc1ebc304e89538094cf6`; HEAD hiện `95d6fdad17608c5db7963e892e9927986f839dbb` (S3 core).
- **Chủ dự án đã chốt trong chat:** “Chốt migration v3 theo kế hoạch, thực thi tiếp S1–S5”. Không hỏi lại quyền migration. Giữ songKey/playlist/tệp/account/owner/session; không đổi package/signer/versionCode.
- Tất cả file chưa commit là thay đổi của Codex phiên này, đã đối chiếu Git; không có phần lạ. Không có agent phụ hay agent khác sửa code.

| Mốc | Trạng thái | Bằng chứng / tiếp |
|---|---|---|
| S1 hợp đồng/native | Xong | 11 JVM tests; Sony opt-in probe tại `35ba26b` **OK (1 test)**. Forum 14 topic/3 trang/General/end không lặp; 2 topic có trang một tin→rỗng, nguồn thường; 109 message đúng metadata, 24 yêu cầu. Các lượt thất bại trước giữ nguyên trong báo cáo. |
| S2 DB/DAO | Đã commit `28ff8e1` | v3 cộng thêm, topic nullable, metadata/progress, transaction/cancel, giữ dữ liệu và PK/playlist. 30/30 tests chọn lọc. Chưa nâng DB thật Sony. |
| S3 parser/reader | Đã commit `95d6fda` | Parser chung audio/document, metadata global, reader budget/head/older/checkpoint/search/General/cancel/backoff. 44/44 tests chọn lọc. |
| S4 UI/queue | Code xong, chưa commit | Catalogue/topic scope/state/back/restore; owner request tags process-wide, worker/cancel; cache-first/search/count/filter; full indexed topic queue theo ý định phát. Phone/tablet/two-pane/Gọn/Car cùng host. |
| S5 | Đang gate Ni | Full Debug/Release trước sửa Gọn/Car: mỗi variant 2.740 passed, 21 skipped, 0 failed/error (205 suites, 2.761 defined), assemble hai variant + AndroidTest thành công. Chạy lại sau sửa Gọn/Car. Chưa có APK tính năng hợp lệ/QA UI/Drive. |

**File S4 chưa commit:** `app/build.gradle` (Gradle nhận đường dẫn config đã có trên Tiny); DAO/SQLite/in-memory lookup topic/progress; `MeloBrowseState/UiPolicy`, `MeloScreenHost/LibraryPane/LibraryPolicy/PhoneScreen/TabletScreen/CompactScreen/CarScreen`; `TelegramForumReader`, `TelegramPreviewActivity`; mới `MeloForumCatalogueView`, `MeloForumUiPolicy` và test; sửa scope/index tests. Hai AndroidTest opt-in mới: baseline v2 đọc số bài/playlist, feature test dùng host thật kiểm topic A/B/queue/Downloaded/xoay/recreate. Không tự chạy test live nếu thiếu opt-in.

**Gate Ni đang chạy:** session `5286`, `scratch/chu-de-telegram/ni-final-r4.log` và `.exit`; JDK `C:/Users/ntdun/.cache/codex-toolchains/openjdk-21.0.12/jdk-21.0.12+8`, SDK `C:/Users/ntdun/.cache/codex-toolchains/android-sdk`. Đọc exit/XML trước, không khởi động Gradle trùng. Lượt r3 chỉ sai đường dẫn JAVA_HOME, chưa chạy Gradle. Gate trước r4 ở `ni-final-gate.log` đã xanh 2m40. APK Ni config=false chỉ kiểm compile, không cài/bàn giao.

**Thiết bị:** Tiny `DESKTOP-HNP4GVR`, Remote Desktop Commander device `9c7f081b-6e1c-4221-ac78-da470bab1e5d`. Sony `QV725WEQ54`, XQ-AS52, kiểm lại kết nối/mẫu 03/11 khoảng 19:37; màn OFF. App vẫn candidate EQ source `cade4dc`, package `app.melovault.player`, vc121/0.6.96-beta, SHA `76D1F355095C6E4E98A28F703BDB196BFEE1E562F1780393DF9E419F3B9BDB6C`, cert `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`, config=true theo bằng chứng S1. Chưa thay app/DB. Chỉ test APK S1 đã được cài; `SONY_SCREEN_END=OFF`.

**Tiny / tiếp theo:**

1. Chờ gate Ni, tổng hợp XML, `git diff --check`, commit S4 cùng báo cáo/checkpoint. Tạo bundle đúng source SHA, đối chiếu SHA-256 hai đầu, clone Tiny **mới**. Không sửa source Tiny qua T:; không chạm repo Tiny gốc.
2. Build Debug candidate + AndroidTest trên clone mới. Gradle đọc config đã có trên Tiny bằng `-PmeloLocalPropertiesFile=C:/Bots/MeloVault-EQ-IsolatedQA-20261103-cade4dc/local.properties`; đã kiểm existence=true, không đọc/copy/hash/log nội dung. `reportTelegramConfig` chỉ boolean.
3. Lưu APK riêng tên duy nhất, kiểm package/version/cert/config/hash/bytes. Cài test APK `-r`, chạy baseline opt-in trên app cũ để ghi DB v2/số bài. MediaSession không phát trước khi cài feature `-r`; không downgrade/uninstall/clear. Kiểm APK cài khớp candidate.
4. Chạy feature test opt-in với baselineSongs, chụp từng `forum_stage=Q...` bằng exec-out PNG nhị phân; bổ sung 3–6 thao tác ADB dựa ảnh thật. Cleanup 223 trong finally + xác minh OFF. Thiếu dữ liệu 2 topic nhạc hoặc điều kiện khác phải báo BLOCKED đúng phần.
5. Ghi kết quả thực, ảnh private và `00_tong_hop.png` (6–8 ảnh, cạnh dài ≤1600). Giữ giới hạn âm thanh thật/offline/FYT riêng. Upload APK QA hợp lệ và 5–10 ảnh Drive Ni N owner-only; readback metadata.
6. Cập nhật báo cáo, hồ sơ `*-apk-test.md`, `*-hinh-nghiem-thu.md`, checkpoint và prompt **phiên sơ bộ mới**; commit trên task branch rồi dừng. Không tự nghiệm thu/merge/push.

**Artifact S1:** clone `C:/Bots/MeloVault-FORUM-S1-IsolatedQA-20261103-4377f07` detach `35ba26b`; log `C:/Bots/_qa_temp/forum-s1[-rN]-20261103-<commit>/`. Lượt cuối bộ kiểm SHA `D843378259755AB00232FC4824B3832C811DD7EFDF8E517101CB90261332FB35`, 429.055 B. Native repo và APK sẵn có cùng SHA `2E09562262268A1363939E720BE5519187533113E021A558CC6FA89FB4E23D28`. Không có job Tiny/Sony đang chạy tại checkpoint này.

**Drive:** đã dùng skill Google Drive, xác minh tài khoản đích **Ni N / nigamehome@gmail.com**, connector link `link_68aaf36f9ca8819198e563269f52e8d5`; không dùng tài khoản khác. Không đổi quyền public, không ghi đè artifact cũ. Folder/artifacts chỉ tạo sau gate. Artifact local dưới `docs/plans/artifacts/<TASK_ID>/` bị .gitignore, không commit PII.

**Cập nhật kết thúc gate S4:** session `5286` đã xong, `ni-final-r4.exit=0`, BUILD SUCCESSFUL 2m51. Hai variant mỗi loại 2.740 passed/21 skipped/0 failed/error. Assemble Debug/Release và AndroidTest xanh. Không có job Ni/Tiny còn chạy. Commit chứa checkpoint này là source S4 để chuyển Tiny; lấy bằng `git log -1 --format=%H -- app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloCarScreen.kt`. Bước tiếp S5 clone mới + config=true trên Tiny, baseline v2 trước feature install.
