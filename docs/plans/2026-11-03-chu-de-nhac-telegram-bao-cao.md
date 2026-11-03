Thực thi: Codex, GPT-6 (model/effort chi tiết không được cung cấp), phiên 03/11/2026, S1–S5.

# Báo cáo nhạc theo chủ đề Telegram

- TASK_ID: `2026-11-03-chu-de-nhac-telegram`.
- Worktree: `C:/Users/ntdun/.codex/worktrees/melovault-chu-de-telegram/MeloVault-E91-Shareable`.
- Nhánh: `dot/2026-11-03-chu-de-nhac-telegram`; baseline source `b90834f44457b60a9732e59e59c1bc9c971368c3`; commit kế hoạch/HEAD đầu phiên `36c0647192234560d97bc1ebc304e89538094cf6`.
- Đầu phiên: Git sạch, nhánh/HEAD/commit tài liệu khớp checkpoint; `git diff --check` không lỗi. Commit gần nhất lúc 13:51 +07, phiên tiếp nhận lúc 18:05 +07; không có Java/Gradle trên Ni trong kiểm tra đầu phiên. Không tiếp quản tiến trình build/cài.
- **Quyết định chủ dự án trong chat 03/11/2026:** “Chốt migration v3 theo kế hoạch, thực thi tiếp S1–S5”. Phạm vi cộng thêm: lưu chủ đề/tiến độ, topic nullable trên bài; giữ songKey, playlist, liên kết tệp và owner/session.
- Đã đọc CLAUDE.md, AGENTS.md, cập nhật đầu HANDOFF.md, BACKLOG, quy trình ba vai trò, kế hoạch, checkpoint, prompt thực thi và vòng đời màn QA. Chỉ Codex phiên này sửa mã; không giao agent khác.

| Mốc | Trạng thái | Bằng chứng / việc tiếp |
|---|---|---|
| S1 hợp đồng | Đang làm | Trên `4377f07`: 9/9 test Debug passed, 0 skipped/fail/error; Ni build AndroidTest thành công (5m11). Đã thêm test biên ID=1, đang chạy lại. |
| S1 Sony | Đang làm | Đọc thật hai topic: 24 trang, 1.200 message có quan hệ đúng, 0 thiếu, 0 mâu thuẫn; chưa tới cuối trong ngân sách 12 trang/topic. Probe báo assertion BLOCKED; không phải khác schema. Đang đổi chọn mẫu/giới hạn kiểm đọc, chưa tích hợp. |
| S2–S4 | Chưa làm | Phạm vi migration đã chốt; chờ cổng S1. |
| S5 | Chưa làm | Chưa có test/build/candidate mới. |

Nguồn hợp đồng: [schema chính thức đúng revision](https://github.com/tdlib/td/blob/c15d3f5a5de6e3ba5839822c451152e5e18bb700/td/generate/scheme/td_api.tl). Các thống kê máy thật chỉ được ghi sau khi chạy; fixture không thay bằng chứng TDLib/Sony.

## Bằng chứng S1 đang có

- Commit hợp đồng/probe ban đầu `4377f07ba61d9f76533019928d5566cc08a94f2b`; Ni log `scratch/chu-de-telegram/s1-ni.log`, exit 0, XML `app/build/test-results/testShareableDebugUnitTest/TEST-com.bsdung.musicassistantcar.data.telegram.TelegramForumContractTest.xml`.
- Bundle 13.813.208 byte, SHA-256 `87D9739D39682D2045C63CBB09C548E32C41BF719DCD6D9BB482ADFB70B8F86D` khớp Ni/mount/Tiny. Clone `C:/Bots/MeloVault-FORUM-S1-IsolatedQA-20261103-4377f07`, detach đúng commit, sạch. Build Tiny chỉ AndroidTest, không đọc/copy Telegram config; log/exit ở `C:/Bots/_qa_temp/forum-s1-20261103-4377f07/`, BUILD SUCCESSFUL 2m51.
- Xác minh app thực đang cài: Debug vc121/0.6.96-beta, **SHA `76D1F355095C6E4E98A28F703BDB196BFEE1E562F1780393DF9E419F3B9BDB6C`**, 33.828.974 byte, cert `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`. Khớp candidate EQ `cade4dc` trong hồ sơ nhánh EQ (Telegram config=true); không nhầm với candidate cài đặt chỉ vì cùng version. Không thay APK ứng dụng.
- Bộ kiểm `app.melovault.player.test`, target `app.melovault.player`, AndroidJUnitRunner, 413.455 byte, SHA `D8B866440CE1C2318FCC697154A00F34E0ED75181B3008FD8EECE1692680AEB8`, cert khớp app. Gói test chưa cài trước đó; một `adb install -r` → Success. Gói kiểm không mở TDLib client/config riêng.
- Probe đọc bằng owner/hub hiện có; keyguard không hiện/không hạn chế input, player paused trước chạy. Lượt 1: `topics=2 history_pages=24 terminal=0 singleton_probes=0 matched=1200 missing=0 requests=34`; runner có 1 assertion failure vì chưa tới cuối (mặc dù ADB exit 0), **không ghi PASS**. Log `probe.log`, `probe-counters.log`. Logcat vòng đệm không giữ dòng danh mục đầu; lượt sau gửi thống kê qua instrumentation status để giữ bằng chứng.
- `SONY_SCREEN_END=OFF`, đã dùng `keyevent 223` trong `finally` và xác minh `Display State=OFF`. Không gửi/đổi nội dung Telegram, tải nhạc, đọc session/cache hay đổi tài khoản. Đây là kiểm API; chưa QA giao diện tính năng.
- Lượt 2 trên `145bcad`: catalogue thật 3 trang, 14 topic nhìn thấy, General=true, end=true, cursor repeated=false, nguồn thường=true. Lịch sử gặp “no verified reply” sau 29,313 giây; không ghi là schema mismatch hay PASS. Log tại `C:/Bots/_qa_temp/forum-s1-r2-20261103-145bcad/`, màn OFF. Test JVM bổ sung: **10/10 passed**, 0 skipped/fail/error, Ni 14 giây.
- Lượt 3 chuẩn bị: tách kiểm hai trang đầu lịch sử khỏi kiểm ranh giới cuối. Probe-only tìm tin dịch vụ tạo topic theo mã nguồn đúng revision (`ForumTopicManager.cpp`, `MessageId.h`), xác minh đối tượng trả về rồi dùng raw `message.id` nguyên gốc để đọc ranh giới; tối đa 6 mẫu không phải General, 4 trang cuối/mẫu, không quét hết topic lớn. Luồng sản phẩm không phụ thuộc phép chuyển ID này. Phân biệt Timeout/Aborted bằng thống kê boolean, timeout yêu cầu 45 giây. Ni AndroidTest build thành công 5 giây; chưa chạy Tiny lượt 3.
- Lượt 3 trên `99f155e`: Tiny AndroidTest build 33 giây; bộ kiểm 426.937 byte, SHA `A6AC11C3AA1C4F3200C7DC67431F5869DE2AE609DC5CD15F93B2F02D3B81FE10`, cert khớp, đã lưu riêng trước cài `install -r`. Catalogue 3 trang/36 topic/có General/end=true/không lặp; hai topic kiểm được điểm cuối (2 trang rỗng dưới tin tạo) và các trang đầu (50→0, 50→10), 110 message đúng metadata; 19 yêu cầu, 4,39 giây. Assertion cuối yêu cầu phải có boundary lặp không đạt: **tin tạo không nằm trong history và không thể bắt buộc phản hồi lặp**. Không ghi PASS toàn S1. Màn OFF; log `C:/Bots/_qa_temp/forum-s1-r3-20261103-99f155e/`.
- Lượt 4 chuẩn bị: đọc một message thực đầu tiên sau tin tạo bằng offset=-1/limit=1 được schema cho phép; lấy raw id trả về, kiểm trang 1 tin → hết và phân biệt inclusive/exclusive. Đọc thêm lịch sử nguồn thường 1 tin. Ni 11 test hợp đồng (thêm trường hợp boundary exclusive) + AndroidTest build thành công 5 giây. Chưa chạy Tiny lượt 4.
