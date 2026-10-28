# PROMPT MẪU — CLAUDE CODE THỰC THI / TIẾP TỤC

Dành cho phiên Claude Code MỚI với model tiết kiệm token/quota hơn do chủ dự án chọn (có thể Sonnet); giữ tên file để tương thích. Không dùng phiên lập kế hoạch để code. Cập nhật 29/10/2026: **phiên sơ bộ mới sau thực thi; chủ dự án nghiệm thu chính thức, chi tiết** theo CLAUDE.md mục 0 và `QUY_TRINH_3_VAI_TRO.md`.
Mẫu áp dụng cho kế hoạch do Codex hoặc Claude viết, và để tiếp tục khi Codex hết quota. Codex dùng cùng các bước kỹ thuật qua PROMPT_CODEX.md; không bắt buộc Claude chỉ là phương án dự phòng. Điền TASK_ID, worktree, baseline, HEAD, chặng và bước tiếp cụ thể.

---

MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: <đường dẫn thực; repo chuẩn D:\Bots\MeloVault-E91-Shareable>
TASK_ID: <TASK_ID>
Nhánh: dot/<TASK_ID>
Kế hoạch Codex/Claude đã được chủ dự án CHỐT: docs/plans/<TASK_ID>.md
Bàn giao: docs/plans/<TASK_ID>-ban-giao.md; HEAD dự kiến: <HEAD_SHA>
Model: <chủ dự án chọn>; bước tiếp tục: <Sx và việc cụ thể>
Baseline: <BASE_SHA>, commit kế hoạch <PLAN_SHA>
PHIÊN: <1 = code + test Ni (các bước Sx–Sy) | 2 = build Tiny + QA + báo cáo (bước Sz) | GỘP = cả hai>
[Kế hoạch phải có trước phiên thực thi, không tự gộp lập kế hoạch và code.]
[Lưu ý riêng của nhiệm vụ: ≤ 8 dòng]

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, quy trình chung, `*-ban-giao.md`, kế hoạch và báo cáo TASK_ID. Kiểm git status, branch, HEAD, baseline/plan commit, diff, agent đang chạy. Tiếp tục từ Codex/Claude: xác minh agent cũ dừng, phần chưa commit khớp bàn giao, tác vụ build/cài trước đã kết thúc; không lặp việc có bằng chứng còn hợp lệ. Nếu tree dirty không rõ nguồn hoặc baseline không khớp, STOP; không reset/clean/stash/đè.
2. (Phiên 1/GỘP) Sửa đúng file/bước trong kế hoạch, commit theo mốc, không thay kiến trúc/mở rộng scope/tự làm task khác. Tạo regression test phù hợp; chạy :app:testShareableDebugUnitTest + :app:testShareableReleaseUnitTest và assembleShareableDebug/Release; log ra file, chỉ ghi tổng kết, test defined/pass/skipped/fail thực và diff --check. **Sau mỗi mốc** cập nhật `*-bao-cao.md` chi tiết và `*-ban-giao.md`: bước xong/dở, file/commit, bằng chứng, lỗi/giới hạn và việc tiếp. Không đợi cuối phiên hoặc hết quota.
3. (Phiên 2/GỘP) QA theo kế hoạch; được chủ động dùng Sony để kiểm chứng trong nhiệm vụ. Trên đúng máy chạy adb devices -l, xác nhận model/manufacturer và SERIAL authorized (Shanling trên Ni; Sony trên Tiny). APK cần config nhúng phải build Tiny có config (Git bundle kiểm SHA256), không mang secret về Ni. Kiểm package/version/signature/config và trạng thái phát trước khi cài; nếu hợp lệ, chỉ adb -s SERIAL install -r APK_VALIDATED. CẤM uninstall, pm clear, logout/xóa data/cache/TDLib. Thiết bị không nhận, mismatch, downgrade hoặc thiếu config cần thiết → DỪNG phần cài, BLOCKED; không dùng Sony thay chứng cứ Shanling.
4. Khi cài được, PHẢI tự chạy các bước QA UI an toàn trong kế hoạch qua ADB, tự chụp ảnh trước/sau mỗi bước quan trọng bằng adb -s SERIAL exec-out screencap -p, kiểm ảnh đọc được; dùng adb screenrecord nếu cần. Ghi model/serial/thời gian, trạng thái Media3/queue và logcat. Không coi Media3 PLAYING là bằng chứng nghe được âm thanh thật. Không tự bật airplane/tắt mạng nếu plan cấm. Ảnh private tại docs/plans/artifacts/<TASK_ID>/, không commit ảnh có dữ liệu cá nhân. **Ghép 6–8 ảnh chính thành 1 file `00_tong_hop.png`** (lưới, cạnh dài ≤ 1600 px, đánh số bước).
5. Báo cáo `docs/plans/<TASK_ID>-bao-cao.md` (≤ ~250 dòng, phụ lục `.md` có liên kết nếu cần) mở đầu `Thực thi: <Codex/Claude Code> <model> <effort nếu biết>, phiên <...>`. Ghi branch/commits/base, file đổi, kết quả từng bước và phần dở, bảng test/lệnh/log, APK/gate/cài, QA 3–6 bước/ảnh tổng hợp, kết quả kỹ thuật, rủi ro và việc chưa kiểm. Phân biệt phần từng agent, không đoán model hoặc chứng cứ. Không xóa/ghi đè artifact cũ. Cập nhật checkpoint theo mục 3 quy trình chung.
6. Commit code, báo cáo và bàn giao trên cùng dot/<TASK_ID> khi Git an toàn. Được chuẩn bị hồ sơ/APK QA owner-only sau gate; không tự ghi PASS chính thức/merge/public/chọn việc kế. Hoàn tất thực thi: đưa prompt `PROMPT_NGHIEM_THU_SO_BO.md` đã điền source/APK/hash để chủ dự án mở **phiên sơ bộ mới**; không tự chuyển vai. Chỉ sửa quy trình khi chủ dự án yêu cầu. Merge cần ĐẠT và chỉ thị riêng; push/public cần chỉ thị riêng.

Kết thúc tiếng Việt dễ hiểu với ba đoạn: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối:
- Chặng chưa cuối: "Đã dừng, bàn giao phiên thực thi tiếp theo." Dẫn file bàn giao, bước tiếp và prompt; chủ dự án có thể đổi Codex ↔ Claude Code.
- Phiên cuối/GỘP: "Đã dừng. Mở phiên mới để nghiệm thu sơ bộ nhanh." Dẫn APK, báo cáo, bàn giao và prompt sơ bộ; chủ dự án vẫn là người nghiệm thu chính thức.

Ngoài dòng trên, KHÔNG tự đề xuất rồi làm việc kế tiếp.

---
Khi chủ dự án yêu cầu sửa, ghi nhận REWORK, dùng phiên thực thi MỚI với model được chủ dự án chọn, vào đúng dot/<TASK_ID>, đọc bàn giao/sơ bộ/nghiệm thu, sửa đúng lỗi được giao và cập nhật báo cáo có dấu vết từng vòng; không bắt đầu plan mới. Hết quota thì cập nhật checkpoint sớm nhất có thể; phiên nhận dùng mẫu tiếp tục trong quy trình chung, không bắt làm lại hoặc tự đổi phạm vi.

### QUY ĐỊNH BẮT BUỘC VỀ MÀN HÌNH (19/10/2026)

Đọc `docs/workflow/SCREEN_LIFECYCLE_QA.md` trước mọi ADB QA. **Để màn Shanling trên Ni và Sony trên Tiny tắt khi không chụp/thao tác**. Chỉ wake trước màn hình cần test bằng `adb -s SERIAL shell input keyevent 224` sau khi kiểm trạng thái. Sau chuỗi thao tác, trong khối `finally`, gửi `adb -s SERIAL shell input keyevent 223`; kiểm `dumpsys display` xác nhận Display State=OFF. Không dùng KEYCODE_POWER 26 không kiểm trạng thái, không tắt màn khi người/agent khác đang dùng, không giữ sáng lúc build/test Gradle. Báo cáo `SHANLING_SCREEN_END`, `SONY_SCREEN_END` và blocker nếu có; **không thay timeout/brightness/AOD hoặc xóa dữ liệu**.

### BÀN GIAO APK QA BẮT BUỘC (19/10/2026)

Khi kết thúc mỗi TASK_ID có một APK test **mới** thực sự, bổ sung vào `docs/plans/<TASK_ID>-bao-cao.md` mục **"APK QA bàn giao chủ dự án nghiệm thu"**: Git commit, tên/đường dẫn APK từ Ni hoặc Tiny (có xác minh tồn tại), package, versionCode/versionName, SHA-256, bytes, cert SHA256, loại build, trạng thái Telegram config phù hợp (boolean, không in API ID/hash), Gradle tests, kết quả ADB/thiết bị, điểm chưa QA. Agent kiểm các gate rồi upload owner-only, xác minh metadata và khả năng tải, ghi `docs/plans/<TASK_ID>-apk-test.md`, gửi link cho chủ dự án. Không xóa/ghi đè APK cũ, public hoặc né cảnh báo Drive. Bản chưa duyệt ghi QA CANDIDATE — CHƯA NGHIỆM THU. Nếu APK không hợp lệ hoặc Drive chặn tải, ghi BLOCKED đúng phần; không giới thiệu artifact lỗi hay URL bị chặn như bản tải dùng được. Chủ dự án quyết định nghiệm thu theo `PROMPT_CHU_DU_AN_NGHIEM_THU.md`.
