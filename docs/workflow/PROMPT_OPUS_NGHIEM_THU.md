# PROMPT MẪU — CLAUDE OPUS NGHIỆM THU

Dành cho phiên Claude Code MỚI, chọn **Opus**, KHÔNG Resume phiên lập kế hoạch hay phiên Sonnet. Hiệu lực 25/10/2026 (thay vai ChatGPT).
Chủ dự án chỉ cần gõ: `Nghiệm thu <TASK_ID> theo docs/workflow/PROMPT_OPUS_NGHIEM_THU.md`.

---

MELOVAULT — PHIÊN OPUS NGHIỆM THU ĐỘC LẬP

Repo chuẩn: D:\Bots\MeloVault-E91-Shareable
TASK_ID: <TASK_ID> — nhánh dot/<TASK_ID>

1. **Git (rẻ trước):** git status --short, branch, HEAD, `git log --oneline <base>..HEAD`, `git diff --stat <base>..HEAD`, `git diff --check <base>..HEAD`. Có commit trong vài phút gần đây/agent đang chạy → DỪNG. Không reset/clean/stash. Sửa tài liệu nghiệm thu chỉ khi cây sạch.
2. **Đọc có chọn lọc:** kế hoạch (mục phạm vi, file, PASS/FAIL, Bác sĩ tự kiểm tra) + báo cáo Sonnet. Không đọc toàn bộ diff: chỉ đọc kỹ các file rủi ro kế hoạch nêu (TDLib/Media3/DB/schema/cancel/account/security) và test mới; còn lại kiểm bằng --stat có nằm đúng danh sách file không. File ngoài phạm vi → ghi REWORK.
3. **Tự chạy lại một lần trên Ni**, xuất ra file trong scratchpad, chỉ đọc dòng tổng kết và số test từ `app/build/test-results/**.xml`:
   `.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest :app:assembleShareableDebug :app:assembleShareableRelease --console=plain`
   So số tests/skipped/failed với báo cáo; số test không được giảm so với baseline.
4. **Ảnh:** xem `docs/plans/artifacts/<TASK_ID>/00_tong_hop.png` trước; chỉ mở ảnh gốc khi nghi ngờ. Đối chiếu từng bước QA với kỳ vọng. Không tự chạy lại QA thiết bị trừ khi chứng cứ thiếu/mâu thuẫn và an toàn (tuân CLAUDE.md §3, §7 màn hình). Âm thanh thật: CHƯA XÁC NHẬN nếu không có nguồn độc lập.
5. **APK (CLAUDE.md §9):** xác minh SHA-256/bytes/package/vc/cert/Telegram config=true (không in giá trị) của APK candidate; đạt gate thì upload Google Drive owner-only (thư mục TASK_ID/ngày/vc/commit, không ghi đè), kiểm share=private, ghi `docs/plans/<TASK_ID>-apk-test.md`. Ảnh chính → Drive owner-only + `docs/plans/<TASK_ID>-hinh-nghiem-thu.md` (§8). Không đạt/không truy cập được → BLOCKED — CHƯA BÀN GIAO.
6. **Kết luận** đúng một trạng thái theo tiêu chí trong kế hoạch (không nới): PASS / REWORK / BLOCKED. Chưa có chủ dự án duyệt ảnh → **BLOCKED — CHỜ CHỦ DỰ ÁN**. Ghi `docs/plans/<TASK_ID>-nghiem-thu.md` (≤ ~150 dòng: bảng kiểm, bằng chứng, lỗi kèm file:dòng, giới hạn chưa kiểm), commit trên dot/<TASK_ID>.
7. **Gửi chủ dự án trong chat** (SendUserFile): ảnh tổng hợp + 2–4 ảnh then chốt, link APK + thư mục Drive, vc/commit/SHA-256; hỏi **ĐẠT hay báo lỗi**.
8. Khi chủ dự án nói ĐẠT: cập nhật nghiệm thu thành PASS, xin xác nhận thao tác merge, rồi `git merge --no-ff` vào nhánh tích hợp đã thống nhất (không force, không push nếu chưa được yêu cầu); cập nhật mục đầu HANDOFF.md ngắn gọn.
9. REWORK: liệt kê lỗi cụ thể, đưa prompt Sonnet REWORK điền từ docs/workflow/PROMPT_SONNET.md. Có thể đề xuất việc kế kèm lý do; chủ dự án CHỐT mới mở phiên Opus lập kế hoạch.

Không sửa code sản phẩm. Không dùng review nhiều agent. Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro hoặc điểm dừng, kèm trạng thái và câu hỏi ĐẠT/lỗi.
