# PROMPT MẪU — CLAUDE LẬP KẾ HOẠCH

Hiệu lực 29/10/2026. Dành cho phiên Claude Code mới, model do chủ dự án chọn; giữ tên PROMPT_OPUS.md để tương thích liên kết cũ. CLAUDE.md mục 0 và [quy trình chung](QUY_TRINH_3_VAI_TRO.md) là nguồn chính. Không giới hạn Claude chỉ lập kế hoạch lớn; Codex cũng được lập kế hoạch khi chủ dự án giao.

```text
MELOVAULT — LẬP KẾ HOẠCH CHI TIẾT, KHÔNG CODE
Repo/worktree: <đường dẫn thực>.
TASK_ID: <...>; nhánh dot/<TASK_ID>; baseline dự kiến: <SHA>.
Mục tiêu chủ dự án giao: <...>.
Bàn giao nếu đang tiếp tục: docs/plans/<TASK_ID>-ban-giao.md.
Model: <chủ dự án chọn>.
```

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, BACKLOG, quy trình chung, bàn giao nếu có và code liên quan. Kiểm Git/agent/baseline theo luật; phần lạ thì dừng. Tạo nhánh nhiệm vụ từ baseline đã xác minh khi cần và an toàn; không sửa nhánh tích hợp.
2. Nghiên cứu và so sánh phương án, nguyên nhân gốc, giới hạn, bảo vệ dữ liệu/TDLib/Media3, rủi ro và rollback. Được chủ động dùng Sony để khảo sát/tái hiện/kiểm chứng theo luật ADB và màn hình; ghi bản ứng dụng, thao tác và bằng chứng.
3. Viết `docs/plans/<TASK_ID>.md` chi tiết theo bộ hồ sơ chung: mục tiêu/phạm vi, baseline, file/hàm, các bước nhỏ có phụ thuộc/điều kiện xong, test hồi quy Debug/Release, build APK, QA thiết bị, tiêu chí, rollback và các trường hợp phải dừng. Chỗ chưa rõ ghi `[CẦN XÁC NHẬN]`; không để model thực thi tự đoán kiến trúc hoặc phạm vi.
4. Viết 3–6 thao tác QA rõ thiết bị/màn hình/nút/kỳ vọng/ảnh trước-sau, bước nào cần màn ON. Tách chứng cứ Sony (thiết bị QA thật duy nhất từ 31/10/2026, Shanling đã bán), âm thanh thật và việc không được phép tự động. Tuân [vòng đời màn hình](SCREEN_LIFECYCLE_QA.md).
5. Nếu tạo APK, có bước kiểm package/version/cert/SHA-256/bytes/source/config chỉ boolean, build Tiny bằng bundle kiểm hash, bàn giao Drive Ni N owner-only theo gate và `*-apk-test.md`. Bản chờ duyệt ghi **QA CANDIDATE — CHƯA NGHIỆM THU**; không tự public hay đổi signer/version.
6. Tạo/cập nhật `*-ban-giao.md` sau mỗi mốc; phân chặng thực thi hợp lý, lưu quyết định và bước tiếp. Không sửa code/build mới; không tự chuyển sang thực thi. Chỉ sửa quy trình khi chủ dự án yêu cầu.
7. Commit tài liệu, dẫn kế hoạch/bàn giao và prompt thực thi đã điền từ [mẫu Codex](PROMPT_CODEX.md) hoặc [mẫu Claude Code](PROMPT_SONNET.md). Chủ dự án mở phiên mới, chọn model hao phí token/quota thấp hơn phù hợp; không bắt buộc công cụ/model cụ thể.

Kết thúc: Đã làm / Chưa kiểm chứng / Rủi ro, rồi **“Đã dừng. Mở phiên mới, chọn model thực thi tiết kiệm hơn theo lựa chọn của chủ dự án.”**

Nếu hết quota/đổi công cụ giữa lúc lập kế hoạch, cập nhật bàn giao và dùng mẫu tiếp tục của quy trình chung; Codex hoặc Claude Code tiếp tục cùng kế hoạch, không tạo lại từ đầu. Sửa kế hoạch theo yêu cầu cũng ở phiên lập kế hoạch mới, cùng TASK_ID.
