# MeloVault — lập kế hoạch, thực thi, chủ dự án nghiệm thu

**Hiệu lực 28/10/2026. CLAUDE.md mục 0 là luật gốc.** Chủ dự án trực tiếp nghiệm thu; bỏ bước bắt buộc Claude Opus nghiệm thu. Giữ tên file để không làm hỏng tham chiếu hiện có; lịch sử quy trình trước thay đổi nằm trong Git.

## 1. Phân công

| Công việc | Người thực hiện |
|---|---|
| Chốt phạm vi, loại nhiệm vụ, tiêu chí và quyết định ĐẠT/yêu cầu sửa | Chủ dự án |
| Lập kế hoạch nhỏ | Codex gpt-6-astra medium, khi được giao |
| Lập kế hoạch lớn | Claude Opus, trừ khi chủ dự án chỉ định khác |
| Thực thi nhỏ | Codex gpt-6.1-sol xhigh, một phiên |
| Thực thi lớn | Codex gpt-6-astra medium, hai phiên theo kế hoạch |
| Kiểm kỹ thuật, chuẩn bị APK/ảnh và ghi nhận quyết định | Agent thực thi |

Giữ quy tắc phiên mới cho vai lập kế hoạch/thực thi và REWORK, chỉ một agent sửa code. Sonnet là dự phòng khi chủ dự án giao. Không tự xếp lại loại hoặc mở nhiệm vụ kế.

## 2. Thực thi và bàn giao

1. Chủ dự án chốt TASK_ID/phạm vi; agent lập kế hoạch trên `dot/<TASK_ID>`, ghi baseline, tiêu chí đo được và 3–6 bước kiểm thử. Không sửa nhánh tích hợp.
2. Agent thực thi đúng kế hoạch, test/build/QA an toàn và ghi `docs/plans/<TASK_ID>-bao-cao.md`. Phân biệt test JVM, QA thiết bị thật, âm thanh thật và giới hạn chưa chứng minh.
3. Agent xác minh APK đúng source/package/version/cert/SHA-256/bytes/cấu hình; chuẩn bị ảnh/video thật. Được upload bản QA hợp lệ vào Drive Ni N owner-only; giữ bản cũ, kiểm quyền và khả năng tải. Chặn tải/thiếu gate thì ghi BLOCKED đúng phần, không né cảnh báo hoặc bịa link.
4. Agent ghi `*-apk-test.md`, `*-hinh-nghiem-thu.md` và hồ sơ `*-nghiem-thu.md` theo [mẫu bàn giao](PROMPT_CHU_DU_AN_NGHIEM_THU.md); gửi APK, bằng chứng và cách thử để chủ dự án dùng thử hoặc duyệt từ xa. Bản chưa duyệt là **QA CANDIDATE — CHƯA NGHIỆM THU**.
5. Chủ dự án quyết định. Agent chỉ ghi PASS khi có xác nhận ĐẠT đúng bản/phạm vi; yêu cầu sửa ghi REWORK; chưa phản hồi ghi CHỜ CHỦ DỰ ÁN NGHIỆM THU. Không dùng test xanh hoặc yêu cầu "tiếp tục" thay xác nhận.
6. Commit hồ sơ trên nhánh nhiệm vụ rồi dừng. Merge chỉ khi đã ĐẠT và có chỉ thị merge riêng; push/phát hành công khai cần chỉ thị riêng. Không tự chọn việc tiếp.

## 3. Bảo vệ dữ liệu và bằng chứng

- Tuân đầy đủ AGENTS.md và CLAUDE.md về đầu phiên Git, repo duy nhất trên Ni, nhánh nhiệm vụ, bí mật, package/signer/version, thiết bị và luật cài APK. Không reset/clean/stash/ghi đè lịch sử hoặc dữ liệu để có kết quả đẹp.
- Tiny chỉ build/QA trong clone cô lập từ Git bundle đối chiếu SHA-256; không sửa repo Tiny gốc/qua T:. Không chuyển bí mật về Ni. Quyền sửa quy trình không tự cấp quyền thay môi trường ký/cấu hình.
- ADB phải xác minh đúng máy/serial/model mỗi phiên. Chỉ cài `install -r` sau các gate; không uninstall/clear data/downgrade/logout/xóa cache/TDLib. Không dùng Sony thay chứng cứ Shanling.
- Màn chỉ bật trước thao tác cần UI; cleanup `finally` tắt và kiểm OFF theo [vòng đời màn hình](SCREEN_LIFECYCLE_QA.md). Không đổi timeout/brightness/AOD/bảo mật.
- Không xóa/ghi đè artifact cũ, không public Telegram PII. Báo cáo ≤~250 dòng, ảnh tổng hợp `00_tong_hop.png` khi có QA UI; không dùng ảnh cũ làm chứng cứ bản mới. Âm thanh thật chỉ xác nhận khi có bằng chứng phù hợp.
- Nếu bị chặn, giữ nguyên dữ liệu/nhánh và báo nguyên nhân. Chủ dự án quyết định phạm vi nghiệm thu; agent không tự che hoặc bỏ giới hạn.

## 4. Quyền sửa quy trình và lịch sử

Agent chỉ sửa CLAUDE.md, AGENTS.md và docs/workflow khi chủ dự án yêu cầu. Thay đổi 28/10 do chủ dự án giao trực tiếp cho Codex; không còn độc quyền sửa quy trình của Opus khi chủ dự án đã giao agent khác.

Không viết lại biên bản nghiệm thu lịch sử, không tự đánh dấu E105 ĐẠT do thay quy tắc. Các mẫu cũ yêu cầu "chờ Opus nghiệm thu" được thay bằng **"Đã dừng, chờ chủ dự án nghiệm thu."** Chỉ đổi quy trình thì báo thay đổi/commit, không yêu cầu thử APK ngoài phạm vi.
