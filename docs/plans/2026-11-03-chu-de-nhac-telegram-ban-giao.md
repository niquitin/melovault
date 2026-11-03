# Bàn giao kế hoạch nhạc theo chủ đề Telegram

**TASK_ID** `2026-11-03-chu-de-nhac-telegram` · **03/11/2026 (Asia/Saigon)** · **Codex, vai lập kế hoạch**
**Trạng thái** Đã lập kế hoạch. Chặng kế tiếp là thực thi ở phiên mới khi chủ dự án giao.

- **Worktree** `C:/Users/ntdun/.codex/worktrees/melovault-chu-de-telegram/MeloVault-E91-Shareable`
- **Nhánh** `dot/2026-11-03-chu-de-nhac-telegram`. Baseline/HEAD trước commit tài liệu `b90834f44457b60a9732e59e59c1bc9c971368c3` từ nhánh tích hợp. Lấy commit kế hoạch bằng `git log -1 --format=%H -- docs/plans/2026-11-03-chu-de-nhac-telegram.md`. Lúc bàn giao, HEAD là commit chứa hai tài liệu.
- **Yêu cầu** Lập kế hoạch cho MeloVault duyệt một nhóm nhạc Telegram có nhiều chủ đề như hai ảnh tham chiếu. Thiết kế đề xuất là chức năng đọc các chủ đề đã có. [Kế hoạch đầy đủ, tiêu chí và bài thử trên máy](2026-11-03-chu-de-nhac-telegram.md)

| Bước | Trạng thái | Bằng chứng / tiếp tục |
|---|---|---|
| Khảo sát | Xong | Đã đọc luồng quét, parser WAV/document, DAO v2, phạm vi browse, đường phát, schema TDLib ghim và tên phương thức trong binary. Checkout chính có dữ liệu của việc cũ, tài liệu nằm ở worktree sạch riêng từ nhánh tích hợp. |
| Thiết kế | Xong | Kế hoạch S1–S5 ghi nguyên tắc phân trang, cập nhật DB, dữ liệu cũ, queue và QA. Đề xuất thay schema cộng thêm v3 cần nằm trong phạm vi chủ dự án chốt cho thực thi. |
| S1 | Chưa làm | Bắt đầu bằng fixture và đọc thử có kiểm soát trên Sony với tài khoản sẵn có, một forum có hai chủ đề và một nguồn thường. Đối chiếu General, trang cuối và cursor trước phần tích hợp. |
| S2–S5 | Chưa làm | Chưa có thay đổi mã ứng dụng, migration, test Gradle, build, APK hay kết quả QA cho tính năng. |

**Git và thiết bị.** Phạm vi commit lập kế hoạch gồm file kế hoạch và file bàn giao này. Kiểm danh sách bằng `git show --stat -1`. Không có tác vụ build/ADB hay tiến trình phiên lập kế hoạch cần tiếp quản. Không sử dụng thiết bị hoặc thay đổi trạng thái màn hình. Phiên lập kế hoạch kết thúc tại checkpoint này.

**Prompt cho phiên thực thi tiếp theo**

> Thực thi TASK_ID `2026-11-03-chu-de-nhac-telegram` trên worktree và nhánh ghi ở trên. Đọc CLAUDE.md mục 0, AGENTS.md, quy trình ba vai trò, kế hoạch cùng TASK_ID và checkpoint này. Đối chiếu HEAD/nhánh/cây sạch và phạm vi migration v3 đã chốt. Bắt đầu S1 bằng TDLib đúng revision và dữ liệu thử của hai chủ đề. Tiếp tục theo thứ tự trong kế hoạch, giữ khóa bài, playlist và phạm vi tài khoản. Ghi bằng chứng thực, cập nhật báo cáo và checkpoint sau từng mốc. Nếu thiếu bằng chứng thiết bị, tách rõ phần bị chặn. Sau các chặng thực thi, bàn giao APK đúng gate và chuyển sang phiên kiểm sơ bộ mới.
