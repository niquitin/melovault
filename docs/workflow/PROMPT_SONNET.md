# PROMPT MẪU — CLAUDE SONNET THỰC THI

Dành cho phiên Claude Code MỚI, chọn **Sonnet khả dụng**, chế độ thực thi có kiểm soát. Không dùng Resume của Opus.
Agent lập kế hoạch điền mẫu này cuối phiên (chỉ thay TASK_ID, baseline, PHIÊN và ≤ 8 dòng lưu ý riêng). Cập nhật 28/10/2026: **chủ dự án trực tiếp nghiệm thu** theo CLAUDE.md mục 0 và `PROMPT_CHU_DU_AN_NGHIEM_THU.md`. Mẫu này là dự phòng khi chủ dự án giao Sonnet; Codex thực thi dùng PROMPT_CODEX.md.

---

MELOVAULT — PHIÊN SONNET THỰC THI ĐÚNG KẾ HOẠCH

Repo chuẩn: D:\Bots\MeloVault-E91-Shareable
TASK_ID: <TASK_ID>
Nhánh: dot/<TASK_ID>
Kế hoạch Opus đã được chủ dự án CHỐT: docs/plans/<TASK_ID>.md
Baseline: <BASE_SHA>, commit kế hoạch <PLAN_SHA>
PHIÊN: <1 = code + test Ni (các bước Sx–Sy) | 2 = build Tiny + QA + báo cáo (bước Sz) | GỘP = cả hai>
[Chỉ nếu được chỉ định là LÀN NHANH: không có phiên Opus; tự viết kế hoạch rất ngắn vào cùng file.]
[Lưu ý riêng của nhiệm vụ: ≤ 8 dòng]

1. Đọc CLAUDE.md, mục cập nhật đầu của HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md và kế hoạch TASK_ID. Kiểm tra git status, branch, HEAD, baseline/plan commit, diff, agent đang chạy. Phiên 2: kiểm commit của phiên 1 đã có trên nhánh. Nếu tree dirty không rõ nguồn hoặc baseline không khớp, STOP ngay; không reset/clean/stash/đè.
2. (Phiên 1/GỘP) Sửa đúng các file và bước trong kế hoạch, commit theo giai đoạn, không thay kiến trúc hoặc mở rộng scope, không tự làm task khác. Tạo regression test tương ứng; chạy :app:testShareableDebugUnitTest + :app:testShareableReleaseUnitTest và assembleShareableDebug/Release; xuất log Gradle ra file, chỉ ghi dòng tổng kết; ghi test defined/pass/skipped/fail thực, diff --check. Phiên 1 ghi tiến độ ngắn vào docs/plans/<TASK_ID>-bao-cao.md (mục "Phiên 1").
3. (Phiên 2/GỘP) QA theo kế hoạch: trên đúng máy chạy adb devices -l, xác nhận model/manufacturer và SERIAL authorized (Shanling trên Ni; Sony trên Tiny khi kế hoạch yêu cầu). APK cần Telegram phải build ở Tiny có config (Git bundle có SHA256), không mang secret về Ni. Kiểm package/version/signature/Telegram auth config trước khi cài; nếu hợp lệ, dùng DUY NHẤT adb -s SERIAL install -r APK_VALIDATED. CẤM adb uninstall, pm clear, logout/xóa data/cache/TDLib. Nếu không thấy thiết bị, signature mismatch, version downgrade hoặc APK thiếu Telegram config → DỪNG phần cài, đánh dấu BLOCKED; không dùng thiết bị khác thay cho Shanling.
4. Khi cài được, PHẢI tự chạy các bước QA UI an toàn trong kế hoạch qua ADB, tự chụp ảnh trước/sau mỗi bước quan trọng bằng adb -s SERIAL exec-out screencap -p, kiểm ảnh đọc được; dùng adb screenrecord nếu cần. Ghi model/serial/thời gian, trạng thái Media3/queue và logcat. Không coi Media3 PLAYING là bằng chứng nghe được âm thanh thật. Không tự bật airplane/tắt mạng nếu plan cấm. Ảnh private tại docs/plans/artifacts/<TASK_ID>/, không commit ảnh có dữ liệu cá nhân. **Ghép 6–8 ảnh chính thành 1 file `00_tong_hop.png`** (lưới, cạnh dài ≤ 1600 px, đánh số bước).
5. Lưu docs/plans/<TASK_ID>-bao-cao.md (≤ ~250 dòng, bảng trước, không dán log dài): branch/commits/base, file đổi, chức năng xong/chưa xong, bảng test và lệnh đã chạy, package/cert/install -r hợp lệ hay BLOCKED, bảng 3–6 bước Agent đã tự làm, ảnh/video trước-sau + ảnh tổng hợp, PASS/FAIL/BLOCKED, log Media3/ANR, rủi ro/chỗ chưa kiểm chứng, đường dẫn artifacts. Không xóa hoặc ghi đè artifact cũ.
6. Commit code và báo cáo trên cùng nhánh dot/<TASK_ID> khi Git an toàn. Chỉ sửa quy trình khi chủ dự án yêu cầu; được chuẩn bị hồ sơ nghiệm thu/apk-test/hinh-nghiem-thu và upload APK QA owner-only sau các gate. Chủ dự án quyết định ĐẠT; không tự ghi PASS, merge, public hoặc mở nhiệm vụ tiếp. Merge cần chỉ thị riêng sau ĐẠT; push/public cần chỉ thị riêng.

Kết thúc tiếng Việt dễ hiểu với ba đoạn: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối:
- Phiên 1: "Đã dừng. Mở phiên Sonnet mới cho phiên 2 (build + QA)."
- Phiên 2/GỘP: "Đã dừng, chờ chủ dự án nghiệm thu." và cách phản hồi `ĐẠT <TASK_ID> — <commit/APK>` hoặc `Yêu cầu sửa <TASK_ID>: <lỗi>`.

Ngoài dòng trên, KHÔNG tự đề xuất rồi làm việc kế tiếp.

---
Khi chủ dự án yêu cầu sửa, ghi nhận REWORK, mở một hội thoại thực thi MỚI theo phân công, vào đúng dot/<TASK_ID>, đọc docs/plans/<TASK_ID>-nghiem-thu.md, sửa đúng lỗi được giao và cập nhật báo cáo cũ có dấu vết từng vòng; không bắt đầu plan mới.

### QUY ĐỊNH BẮT BUỘC VỀ MÀN HÌNH (19/10/2026)

Đọc `docs/workflow/SCREEN_LIFECYCLE_QA.md` trước mọi ADB QA. **Để màn Shanling trên Ni và Sony trên Tiny tắt khi không chụp/thao tác**. Chỉ wake trước màn hình cần test bằng `adb -s SERIAL shell input keyevent 224` sau khi kiểm trạng thái. Sau chuỗi thao tác, trong khối `finally`, gửi `adb -s SERIAL shell input keyevent 223`; kiểm `dumpsys display` xác nhận Display State=OFF. Không dùng KEYCODE_POWER 26 không kiểm trạng thái, không tắt màn khi người/agent khác đang dùng, không giữ sáng lúc build/test Gradle. Báo cáo `SHANLING_SCREEN_END`, `SONY_SCREEN_END` và blocker nếu có; **không thay timeout/brightness/AOD hoặc xóa dữ liệu**.

### BÀN GIAO APK QA BẮT BUỘC (19/10/2026)

Khi kết thúc mỗi TASK_ID có một APK test **mới** thực sự, bổ sung vào `docs/plans/<TASK_ID>-bao-cao.md` mục **"APK QA bàn giao chủ dự án nghiệm thu"**: Git commit, tên/đường dẫn APK từ Ni hoặc Tiny (có xác minh tồn tại), package, versionCode/versionName, SHA-256, bytes, cert SHA256, loại build, trạng thái Telegram config phù hợp (boolean, không in API ID/hash), Gradle tests, kết quả ADB/thiết bị, điểm chưa QA. Agent kiểm các gate rồi upload owner-only, xác minh metadata và khả năng tải, ghi `docs/plans/<TASK_ID>-apk-test.md`, gửi link cho chủ dự án. Không xóa/ghi đè APK cũ, public hoặc né cảnh báo Drive. Bản chưa duyệt ghi QA CANDIDATE — CHƯA NGHIỆM THU. Nếu APK không hợp lệ hoặc Drive chặn tải, ghi BLOCKED đúng phần; không giới thiệu artifact lỗi hay URL bị chặn như bản tải dùng được. Chủ dự án quyết định nghiệm thu theo `PROMPT_CHU_DU_AN_NGHIEM_THU.md`.
