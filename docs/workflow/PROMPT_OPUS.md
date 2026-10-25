# PROMPT MẪU — CLAUDE OPUS LẬP KẾ HOẠCH

Dành cho phiên Claude Code MỚI, chọn model **Opus cao nhất hiện có**, chế độ lập kế hoạch/duyệt thao tác. Không dùng Resume của phiên trước.

---

MELOVAULT — PHIÊN OPUS LẬP KẾ HOẠCH, KHÔNG SỬA CODE

Repo chuẩn: D:\Bots\MeloVault-E91-Shareable
TASK_ID: <TASK_ID>
Nhiệm vụ được chủ dự án CHỐT: <MỤC_TIÊU_CỤ_THỂ>
Nhánh dự kiến: dot/<TASK_ID>

1. Đọc CLAUDE.md, HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, BACKLOG và code hiện tại. Kiểm tra git status, HEAD, branch, diff và agent khác đang làm. Nếu tree dirty/HEAD không như mong đợi, DỪNG. Nếu nhánh dot mới chưa tồn tại và repo sạch, tạo nhánh dot/<TASK_ID> từ commit tích hợp hiện hành được thống nhất; không sửa nhánh chính.
2. Chỉ nghiên cứu và so sánh phương án; nêu vì sao chọn giải pháp, cái gì không làm, cách bảo vệ Stable/Telegram data/TDLib/Media3, rủi ro và rollback.
3. Tạo CHỈ docs/plans/<TASK_ID>.md, gồm: baseline SHA + plan commit, mục tiêu và phạm vi, nguyên nhân gốc, từng file sửa/các hàm cụ thể, các bước rất nhỏ Sonnet làm được, dữ liệu/test cần có, bài test hồi quy, cả Debug/Release, kiểm tra Shanling Ni và Sony Tiny nếu cần, tiêu chí PASS/FAIL; phần bất định ghi [CẦN XÁC NHẬN].
4. Mục BẮT BUỘC: viết 3–6 bước nghiệm thu từ xa với thiết bị/màn hình/nút/kỳ vọng/ảnh trước-sau; Sonnet tự thực hiện các bước an toàn qua ADB, bác sĩ chỉ cần xem bằng chứng và duyệt. Nêu giới hạn không kiểm được qua ảnh như âm thanh thật hoặc thao tác không được phép tự động.
5. Plan ghi rõ những trường hợp Sonnet phải STOP và xin kiểm toán thay vì đoán, kể cả signature mismatch, ADB không nhận, missing Telegram API config hoặc layout không tái hiện.
6. Không sửa .kt, .gradle, manifest, data, CLAUDE.md hay docs/workflow; không chạy build, cài APK, xóa dữ liệu, không tự chuyển sang task khác. Chỉ commit file kế hoạch trên dot/<TASK_ID> khi Git sạch, báo đường dẫn file, commit và điểm chặn.

7. Nhiệm vụ lớn: chia rõ **Sonnet phiên 1** (code + test Ni, các bước nào) và **Sonnet phiên 2** (build Tiny + QA + báo cáo); nhiệm vụ nhỏ ghi GỘP. Tiêu chí PASS/FAIL phải đo được để phiên Opus nghiệm thu chấm thẳng.

Kết thúc bằng ba đoạn dễ hiểu: Đã làm / Chưa kiểm chứng / Rủi ro, rồi **prompt Sonnet phiên 1** (hoặc GỘP) điền từ docs/workflow/PROMPT_SONNET.md (chỉ thay TASK_ID, baseline, PHIÊN và ≤ 8 dòng lưu ý riêng), và đúng câu:
"Giờ mở phiên mới, chọn Sonnet."

DỪNG. Không tự triển khai code.

---
Khi phải sửa chính kế hoạch theo kết quả Opus nghiệm thu REWORK, mở phiên Opus MỚI trên cùng nhánh, không tạo nhánh/plan trùng.
(Cập nhật 25/10/2026: "ChatGPT" ở các mục dưới đọc là "Opus nghiệm thu".)

### QUY ĐỊNH MÀN HÌNH KHI LẬP KẾ HOẠCH (19/10/2026)

Khi lập 3–6 bước QA từ xa, xác định bước nào cần màn ON và bước nào có thể thực hiện màn OFF (test JVM, log/Media3). Yêu cầu Sonnet dùng vòng đời `wake → thao tác/ảnh → sleep + verify Display OFF` theo `docs/workflow/SCREEN_LIFECYCLE_QA.md`. Không đòi giữ màn M3/Sony sáng lúc build hay chờ bác sĩ duyệt. Không chỉnh độ sáng/thời gian tắt/AOD của Android.

### YÊU CẦU KẾ HOẠCH APK TEST (19/10/2026)

Nếu nhiệm vụ sẽ tạo APK thử nghiệm, kế hoạch phải có bước **bàn giao APK QA**: Sonnet ghi package, vc/vname, signer, SHA256, bytes, source commit, vị trí artifact và tình trạng config/test; ChatGPT kiểm toán độc lập, upload Google Drive Ni N riêng tư, gửi link trực tiếp và tạo `docs/plans/<TASK_ID>-apk-test.md`. Link QA có thể gửi trước khi owner nghiệm thu nhưng phải ghi rõ **CHƯA PASS**. Không public, không đổi signer/version trái yêu cầu, không đưa secrets lên Drive.
