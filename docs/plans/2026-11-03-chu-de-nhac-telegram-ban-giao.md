# Bàn giao thực thi nhạc theo chủ đề Telegram

**TASK_ID** `2026-11-03-chu-de-nhac-telegram` · **03/11/2026 (Asia/Saigon)** · **Codex, thực thi**
**Checkpoint:** S1–S2 xong ở phạm vi mã/JVM/đọc API; bắt đầu S3. Chưa thay APK ứng dụng/DB trên Sony. [Kế hoạch](2026-11-03-chu-de-nhac-telegram.md) · [Báo cáo và bằng chứng từng lượt](2026-11-03-chu-de-nhac-telegram-bao-cao.md).

- Worktree `C:/Users/ntdun/.codex/worktrees/melovault-chu-de-telegram/MeloVault-E91-Shareable`; nhánh `dot/2026-11-03-chu-de-nhac-telegram`.
- Baseline source `b90834f44457b60a9732e59e59c1bc9c971368c3`; kế hoạch `36c0647192234560d97bc1ebc304e89538094cf6`; HEAD trước checkpoint này `35ba26b53d3eb1f64e9c6d561c0987f3b6c77260`. Lấy commit chứa checkpoint bằng `git log -1 --format=%H -- docs/plans/2026-11-03-chu-de-nhac-telegram-ban-giao.md`.
- **Quyết định chủ dự án trong chat:** “Chốt migration v3 theo kế hoạch, thực thi tiếp S1–S5”. Không hỏi lại quyền migration. Giữ khóa bài, playlist, tệp và account/owner/session; không đổi package/signer/versionCode.

| Mốc | Trạng thái | Bằng chứng / việc tiếp |
|---|---|---|
| S1 hợp đồng | Xong | `TelegramForumContract`, fixture tổng hợp; 11 test Debug, 11 passed, 0 skipped/fail/error. Log `scratch/chu-de-telegram/s1-r4-ni.log`; thử biên ID=1, duplicate/conflict, text-only, inclusive/exclusive. |
| S1 Sony | Xong, chỉ đọc API | Probe opt-in tại `35ba26b`, runner **OK (1 test)**. Một forum có 14 topic/3 trang/có General/end không lặp, hai topic có trang 1 tin→rỗng, đầu lịch sử 9→0 và 50→50; nguồn thường đọc 1 tin. 24 yêu cầu, 109 message đầu đúng metadata, 0 thiếu; 3 seed đã xóa được bỏ qua. |
| S2 | Xong ở mã/JVM SQLite | Schema v3 cộng thêm, SQL/in-memory bảo toàn known khi thiếu metadata, topic list/count/search/page và grouped counts, transaction/cancel. 30/30 test chọn lọc, 0 skipped/fail/error; xem báo cáo S2. Chưa nâng DB thật Sony. |
| S3 | Bắt đầu | Thu thập topic trên quét toàn cục; parser chung audio/document; lịch sử có budget/head refresh/checkpoint/cancel/account. |
| S4 | Chưa làm | Scope/điều hướng/cache/search/Downloaded/queue đúng chủ đề. |
| S5 | Chưa làm | Chưa chạy full test/build Debug+Release, chưa có APK ứng dụng mới/QA UI/Drive. Sau hoàn tất thực thi mới bàn giao phiên sơ bộ mới. |

**Git/mã S1:** các commit `4377f07`, `145bcad`, `99f155e`, `67b6634`, `35ba26b`. Chỉ thêm contract, JVM tests/resources, AndroidTest opt-in và runner trong `app/build.gradle`, cùng hồ sơ. Probe có chuyển server-ID sang raw-ID **chỉ để tìm seed tin tạo trong bộ kiểm** (đối chiếu upstream ghim); sau khi xác minh phản hồi, dùng raw `message.id` nguyên gốc. Không đưa phép chuyển này vào luồng sản phẩm. TDLib native `.so` trong APK sẵn có khớp repo SHA `2E09562262268A1363939E720BE5519187533113E021A558CC6FA89FB4E23D28`.

**Thiết bị/artifact:** Tiny `DESKTOP-HNP4GVR` (Remote Desktop Commander device `9c7f081b-6e1c-4221-ac78-da470bab1e5d`), Sony `QV725WEQ54`, XQ-AS52/Sony. App vẫn là candidate EQ `cade4dc`, vc121/0.6.96-beta, SHA `76D1F355…DB6C`, cert `eecd3df7…fce8`, config=true theo artifact đã đối chiếu. Chỉ cài bộ kiểm `app.melovault.player.test` bằng `install -r`, không thay APK app/DB. Không đọc/copy secret, session/cache, đổi account hay gửi nội dung Telegram. Màn đã tắt trong finally, **SONY_SCREEN_END=OFF**.

- Clone Tiny `C:/Bots/MeloVault-FORUM-S1-IsolatedQA-20261103-4377f07`, hiện detach `35ba26b`; chỉ cập nhật qua Git bundle có kiểm SHA, không sửa qua T:.
- Lượt 5 bundle `C:/Bots/_MeloVault_Git_Transfer/chu-de-telegram-s1-r5-20261103.bundle`, SHA `1B4C0C06E54146D53AB4A734CE7FC069E7C9D288DF9E63897F1C5E0770609C34` khớp Ni/Tiny. Build AndroidTest Tiny 32 giây.
- Log/artifact riêng từng lượt tại `C:/Bots/_qa_temp/forum-s1[-rN]-20261103-<commit>/`; lượt cuối `C:/Bots/_qa_temp/forum-s1-r5-20261103-35ba26b/` có `build.log/.exit`, `probe.log/.exit`, `screen-end.txt`, `probe-r5.apk` (429.055 B, SHA `D8433782…2FB35`). Test APK không phải APK tính năng để bàn giao người dùng.
- Không có tiến trình Ni/Tiny/probe đang chạy tại checkpoint. Các Gradle session Ni `37890`, `72992` đã kết thúc. File đang sửa ở mốc này chỉ báo cáo/checkpoint của Codex, chưa có code S2 chưa commit.

**Prompt tiếp tục**

> Tiếp tục TASK_ID `2026-11-03-chu-de-nhac-telegram`, vai thực thi, đúng worktree/nhánh trên. Đọc CLAUDE.md mục 0, AGENTS.md, quy trình, kế hoạch/báo cáo/checkpoint; kiểm Git và phiên cũ đã dừng. Migration v3 đã được chủ dự án chốt; S1 có bằng chứng native OK, không chạy lại vô cớ. Bắt đầu S2 migration/DAO + SQLite tests, rồi S3–S5 đúng kế hoạch. Dùng chung owner; giữ khóa/playlist/account/queue và dữ liệu cũ; cập nhật .md sau từng mốc. Build/QA Tiny phải qua bundle SHA và clone cô lập; giữ gate package/version/cert/config và màn OFF trong finally. Bàn giao APK hợp lệ rồi prompt cho phiên sơ bộ mới; không tự chuyển vai/merge/push.

**Checkpoint S2:** source/HEAD trước commit S2 `c97767a0ea3be2539ce906dd3075c23a03c2afcb`; commit source S2 là commit chứa cập nhật này (lấy qua git log). File do Codex sửa: `TelegramLibraryEntities/Sql/Dao`, `AndroidTelegramLibraryDatabase`, `InMemoryTelegramLibraryDao`, test mới `TelegramForumDatabaseTest` và assertion schema cũ. Test Ni 30/30 xanh, không có tiến trình còn chạy. Không chuyển source S2/Tiny, không nâng DB thiết bị. Bước tiếp là S3, không cần lặp probe S1 hoặc hỏi lại migration; sửa checkpoint cũ “chưa có code S2” bằng trạng thái ở đây. Chỉ docs/code của phiên này trong worktree, không có thay đổi lạ.

**Checkpoint S3 core:** source trước mốc này `28ff8e1ad50f2028fd37feca4224d86190c1483e`; commit chứa checkpoint sẽ chứa parser/reader + metadata toàn cục và 10 test reader. Ni 44/44 xanh, 0 skipped/fail/error (`scratch/chu-de-telegram/s3-ni-r2.log`, 6 giây). Không có build/probe đang chạy, Sony app/DB không đổi, màn cuối OFF. Bước tiếp S4: scope catalogue/topic + encode/restore/back, gắn reader qua request tag riêng trên owner hiện hữu, cache-first/remote search/cancel, topic filter đủ đường list/count/Downloaded/page và queue toàn bộ bài đã index. S3 core chưa có port/UI đang chạy; chưa gọi API sản phẩm mới trên Sony. File sửa do Codex khớp commit này, không có phần lạ.
