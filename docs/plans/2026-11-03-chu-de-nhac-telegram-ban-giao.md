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
| S1 hợp đồng | Đang làm | `4377f07`: 9/9 test passed, không skipped/fail/error; Ni AndroidTest build xanh. Đang bổ sung test ID=1 và giữ trạng thái stalled, chạy Ni lại trong `scratch/chu-de-telegram/s1-contract-r2.log`. |
| S1 Sony | Đang làm | Probe lần 1 đọc 1.200 message/24 trang hai topic đúng metadata, không thiếu/mâu thuẫn. Chưa tới trang cuối trong ngân sách; assertion BLOCKED, không phải schema mismatch. Đang chọn hai mẫu gần ngày tạo hơn, tối đa 80 trang/topic và gửi thống kê qua runner. App Sony giữ candidate EQ `cade4dc`, SHA `76D1F355…DB6C`, vc121/config=true/cert khớp; chỉ cài bộ AndroidTest. Màn đã OFF. |
| S2–S5 | Chưa làm | Chưa đổi DB, giao diện hay hàng đợi. Chờ bằng chứng TDLib S1 trước tích hợp. |

**Git và tiến trình.** Commit S1 đầu `4377f07`; phần sửa tiếp đều do Codex: contract/test (biên ID=1, order dạng số), fixture kết thúc rỗng đúng native, probe chọn mẫu/ngân sách/giữ thống kê, báo cáo/checkpoint. Ni session cũ `37890` đã xong exit 0; lượt test bổ sung session `72992`, log/exit `scratch/chu-de-telegram/s1-contract-r2.*`. Tiny build/probe lượt 1 đã xong; clone `C:/Bots/MeloVault-FORUM-S1-IsolatedQA-20261103-4377f07`, logs `C:/Bots/_qa_temp/forum-s1-20261103-4377f07/`; chưa có lượt 2 đang chạy. Không đổi bí mật, package, signer, versionCode, owner/session hoặc DB. `SONY_SCREEN_END=OFF` sau `finally`, có file `screen-end.txt`.

**Checkpoint bổ sung S1 lượt 2/3:** HEAD trước lượt 3 `145bcad7a4cfb985eeb9246d0f2cb13c702b6952`. Ni test bổ sung xong: 10/10, không skipped/fail/error, 14 giây; session `72992` đã kết thúc. Tiny lượt 2 xong: build 38 giây; catalogue 3 trang/14 topic/có General/hết trang/không cursor lặp/có nguồn thường; lịch sử timeout trước bằng chứng cuối, màn OFF. Bundle lượt 2 SHA `63E45106F406523C2327407E57D1E0E1041D9FBABD689B6EE87C64E96ABB7526`; logs `C:/Bots/_qa_temp/forum-s1-r2-20261103-145bcad/`. Hai APK bộ kiểm đã lưu riêng trong thư mục log từng lượt; không thay APK ứng dụng.
Lượt 3 đang chuẩn bị probe tối đa 6 seed tạo topic đã xác minh từ `getMessage`, dùng raw id phản hồi để kiểm ranh giới cuối, riêng hai trang đầu để kiểm tiến cursor. Chi tiết/lý do/nguồn upstream trong báo cáo; không đưa phép chuyển ID seed vào mã sản phẩm. Ni build AndroidTest xong, log `scratch/chu-de-telegram/s1-probe-r3-ni.log`. Chưa có tác vụ Ni/Tiny đang chạy ở checkpoint này. Bước tiếp: bundle/check SHA → cập nhật clone cô lập từ Git → build/kiểm chữ ký bộ test → install-r bộ test → probe, cleanup OFF. Chưa sửa DB/UI.

**Prompt cho phiên thực thi tiếp theo**

> Tiếp tục TASK_ID `2026-11-03-chu-de-nhac-telegram`, vai thực thi, đúng worktree/nhánh trên. Đọc luật gốc, kế hoạch, báo cáo và checkpoint. Chủ dự án đã chốt migration cộng thêm v3; không hỏi lại. Xác minh phiên cũ đã dừng, Git và kết quả tiến trình Ni trước khi sửa/chạy lại. Hoàn tất S1: fixture + probe đọc forum thật có hai topic và nguồn thường trên Sony, qua bundle SHA và clone cô lập Tiny; không thay APK ứng dụng chỉ để probe nếu baseline đáp ứng. Nếu binary khác schema, dừng tích hợp S1. Sau đó S2–S5 đúng thứ tự, giữ songKey/playlist/account. Cập nhật bằng chứng sau từng mốc, bàn giao APK qua gate và prompt phiên sơ bộ mới khi xong.
