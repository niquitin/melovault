# PROMPT MẪU — NGHIỆM THU SƠ BỘ NHANH Ở PHIÊN MỚI

Hiệu lực 29/10/2026, theo CLAUDE.md mục 0. Chủ dự án giao Codex hoặc Claude ở **phiên mới sau thực thi**; model do chủ dự án chọn. Kiểm nhanh để phát hiện lỗi chặn và chuẩn bị cho chủ dự án nghiệm thu chính thức, chi tiết.

```text
MELOVAULT — NGHIỆM THU SƠ BỘ NHANH, KHÔNG CODE
TASK_ID: <...>; nhánh dot/<TASK_ID>; worktree: <đường dẫn thực>.
Baseline: <BASE_SHA>; HEAD dự kiến: <HEAD_SHA>; source APK: <SOURCE_SHA>.
Kế hoạch: docs/plans/<TASK_ID>.md.
Báo cáo: docs/plans/<TASK_ID>-bao-cao.md.
Bàn giao: docs/plans/<TASK_ID>-ban-giao.md.
APK và SHA-256: <đường dẫn/link đã kiểm và hash>; hồ sơ: <TASK_ID>-apk-test.md.
Phạm vi cần kiểm nhanh: <luồng chính/rủi ro trong kế hoạch>.
```

1. Đọc luật, chỉ mục đầu HANDOFF.md, bàn giao/kế hoạch/báo cáo và APK-index. Kiểm Git/nhánh/HEAD/source/agent theo luật đầu phiên. Thiếu/mâu thuẫn nguồn thì ghi BLOCKED đúng phần; không reset/clean/stash hoặc nhận thay đổi lạ.
2. Xem `git log --oneline <base>..HEAD`, `git diff --stat <base>..HEAD`, rồi diff file rủi ro và test liên quan. Đối chiếu yêu cầu, tiến độ, kết quả test/build, APK/package/version/cert/hash/config và bằng chứng. Không mặc định đọc lại toàn repo hoặc chạy lại toàn bộ bộ kiểm thử.
3. Nếu hữu ích, chủ động dùng Sony (thiết bị QA thật duy nhất từ 31/10/2026 — Shanling đã bán) để kiểm vài luồng chính an toàn. Xác minh máy/serial/model, bản cài/source; cài APK sẵn có chỉ sau đủ gate. Giữ màn OFF ngoài thao tác, cleanup `finally` tắt và xác minh `Display State=OFF`. Ghi `SONY_SCREEN_END`; thiết bị không dùng ghi KHÔNG THAO TÁC, không đoán trạng thái. Không coi Media3 PLAYING là âm thanh thật.
4. Chỉ chạy lại test/QA khi source đổi, bằng chứng thiếu/mâu thuẫn hoặc cần xác minh nghi vấn cụ thể; ghi lý do và kết quả. **Không sửa code, không tự build APK mới trong vai sơ bộ.** Nếu cần build/sửa, ghi việc cần giao lại phiên thực thi. Không gọi review nhiều agent trừ khi chủ dự án yêu cầu.
5. Tạo/cập nhật `docs/plans/<TASK_ID>-nghiem-thu-so-bo.md`, giữ dấu vết từng vòng. Ghi agent/model/ngày/phiên mới, branch/HEAD/source/APK hash, tài liệu và diff đã đọc, kiểm nào dựa trên bằng chứng cũ/kiểm nào tự chạy, lỗi có file/vị trí hoặc bước tái hiện/kỳ vọng/thực tế, phạm vi chưa kiểm và ảnh/log liên quan. Không dán log dài hoặc PII.
6. Kết luận một trong: **SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN**, **SƠ BỘ: CẦN SỬA**, **SƠ BỘ: BLOCKED**. Không tự ghi PASS chính thức. `*-nghiem-thu.md` vẫn CHỜ CHỦ DỰ ÁN NGHIỆM THU, hoặc BLOCKED nếu thiếu điều kiện. Nêu rõ cần chủ dự án giao REWORK khi có lỗi; không tự sửa.
7. Cập nhật `*-ban-giao.md` ngay sau mỗi mốc và trước khi dừng; commit tài liệu trên nhánh nhiệm vụ. Gửi biên bản, link APK/ảnh, 3–6 bước chủ dự án thử và giới hạn. Không merge/push/public hoặc mở nhiệm vụ tiếp.

Lời kết: Đã làm / Chưa kiểm chứng / Rủi ro, rồi **“Đã dừng, chờ chủ dự án nghiệm thu.”** Chủ dự án phản hồi `ĐẠT <TASK_ID> — <commit/APK>` hoặc `Yêu cầu sửa <TASK_ID>: <lỗi>`.

Nếu hết quota giữa kiểm tra, dùng checkpoint/mẫu tiếp tục của [quy trình chung](QUY_TRINH_3_VAI_TRO.md). Phiên Codex hoặc Claude Code tiếp nhận đúng phạm vi kiểm còn lại, không đổi sang thực thi.
