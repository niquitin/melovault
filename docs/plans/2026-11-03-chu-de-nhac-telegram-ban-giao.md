# Bàn giao thực thi nhạc theo chủ đề Telegram

**TASK_ID** `2026-11-03-chu-de-nhac-telegram` · **03/11/2026 (Asia/Saigon)** · **Codex, vai thực thi**
**Trạng thái** Đang S1. Chủ dự án đã chốt migration v3 và thực thi tiếp S1–S5 trong chat 03/11/2026. [Báo cáo thực thi](2026-11-03-chu-de-nhac-telegram-bao-cao.md).

- **Worktree** `C:/Users/ntdun/.codex/worktrees/melovault-chu-de-telegram/MeloVault-E91-Shareable`
- **Nhánh** `dot/2026-11-03-chu-de-nhac-telegram`. Baseline source `b90834f44457b60a9732e59e59c1bc9c971368c3`; commit kế hoạch/HEAD đầu thực thi `36c0647192234560d97bc1ebc304e89538094cf6`. Xác định commit checkpoint bằng `git log -1 --format=%H -- docs/plans/2026-11-03-chu-de-nhac-telegram-ban-giao.md`.
- **Yêu cầu** Lập kế hoạch cho MeloVault duyệt một nhóm nhạc Telegram có nhiều chủ đề như hai ảnh tham chiếu. Thiết kế đề xuất là chức năng đọc các chủ đề đã có. [Kế hoạch đầy đủ, tiêu chí và bài thử trên máy](2026-11-03-chu-de-nhac-telegram.md)

| Bước | Trạng thái | Bằng chứng / tiếp tục |
|---|---|---|
| Khảo sát | Xong | Đã đọc luồng quét, parser WAV/document, DAO v2, phạm vi browse, đường phát, schema TDLib ghim và tên phương thức trong binary. Checkout chính có dữ liệu của việc cũ, tài liệu nằm ở worktree sạch riêng từ nhánh tích hợp. |
| Thiết kế | Xong, phạm vi đã chốt | Chat chủ dự án: “Chốt migration v3 theo kế hoạch, thực thi tiếp S1–S5”. |
| S1 hợp đồng | Đang làm | Thêm `TelegramForumContract`, 9 test JVM + fixture tổng hợp hai trang và probe instrumentation read-only. Schema chính thức đúng revision đã đối chiếu. Test/build Ni đang chạy, chưa có kết quả. |
| S1 Sony | Đang làm | Tiny online; Sony authorized, XQ-AS52/Sony; màn OFF; app sẵn có Debug vc121/0.6.96-beta, player paused. Probe dùng owner/hub có sẵn, không tạo TDLib client hoặc đăng nhập mới; chưa cài/chạy probe. |
| S2–S5 | Chưa làm | Chưa đổi DB, giao diện hay hàng đợi. Chờ bằng chứng TDLib S1 trước tích hợp. |

**Git và tiến trình.** Phần đang sửa đều do Codex phiên này: `app/build.gradle` (runner AndroidTest), contract, test JVM/resources, `app/src/androidTestShareable/` (probe) và báo cáo/checkpoint. Không đổi bí mật, package, signer, versionCode, owner/session. Gradle Ni: `:app:testShareableDebugUnitTest --tests '*TelegramForumContractTest' :app:assembleShareableDebugAndroidTest --console=plain`, log `scratch/chu-de-telegram/s1-ni.log`, exit `scratch/chu-de-telegram/s1-ni.exit` (chỉ xuất khi xong); session công cụ `37890`. Chưa có build/cài đang chạy trên Tiny. Không wake/mở UI Sony; `SONY_SCREEN_END=OFF` ở kiểm tra đầu phiên.

**Prompt cho phiên thực thi tiếp theo**

> Tiếp tục TASK_ID `2026-11-03-chu-de-nhac-telegram`, vai thực thi, đúng worktree/nhánh trên. Đọc luật gốc, kế hoạch, báo cáo và checkpoint. Chủ dự án đã chốt migration cộng thêm v3; không hỏi lại. Xác minh phiên cũ đã dừng, Git và kết quả tiến trình Ni trước khi sửa/chạy lại. Hoàn tất S1: fixture + probe đọc forum thật có hai topic và nguồn thường trên Sony, qua bundle SHA và clone cô lập Tiny; không thay APK ứng dụng chỉ để probe nếu baseline đáp ứng. Nếu binary khác schema, dừng tích hợp S1. Sau đó S2–S5 đúng thứ tự, giữ songKey/playlist/account. Cập nhật bằng chứng sau từng mốc, bàn giao APK qua gate và prompt phiên sơ bộ mới khi xong.
