# MeloVault — ba vai agent và chủ dự án nghiệm thu chính thức

**Hiệu lực 29/10/2026. CLAUDE.md mục 0 là luật gốc.** Giữ tên file để không hỏng tham chiếu; lịch sử quy trình nằm trong Git. Quyết định này không sửa kết luận nghiệm thu đã có.

## 1. Trình tự các phiên

**Codex và Claude Code có quyền, vai trò và trách nhiệm ngang nhau.** Chủ dự án giao vai cho bên nào thì bên đó dùng repo/worktree và công cụ bình thường trong phạm vi ấy: đọc/sửa mã, tài liệu, Git/commit, test/build APK, QA và bàn giao. Không cần sự phê duyệt của agent còn lại hay xin lại quyền chỉ vì đổi công cụ. Repo, nhánh và hồ sơ dùng chung; agent tạo ra chúng không có quyền sở hữu riêng. Việc tiếp nhận vẫn kiểm Git/checkpoint, agent cũ dừng và giữ một agent sửa code mỗi lúc. Các luật an toàn và quyền nghiệm thu cuối/merge/push/public áp dụng như nhau cho cả hai.

| Giai đoạn | Người thực hiện | Kết quả |
|---|---|---|
| 1. Lập kế hoạch | Codex hoặc Claude do chủ dự án giao | Kế hoạch `.md` chi tiết, checkpoint và prompt thực thi |
| 2. Phiên mới thực thi | Model tiết kiệm token/quota hơn do chủ dự án chọn, Codex hoặc Claude Code | Code, test, APK, báo cáo chi tiết, bằng chứng và checkpoint |
| 3. Phiên mới sơ bộ | Codex hoặc Claude được giao | Kiểm tra nhanh, biên bản sơ bộ và giới hạn |
| 4. Nghiệm thu chính thức | Chủ dự án | Dùng thử/đánh giá chi tiết, quyết định ĐẠT hoặc yêu cầu sửa |

Chủ dự án chọn công cụ/model/effort; không khóa cứng theo nhỏ/lớn. Agent không tự chuyển vai, đổi model, mở phiên hoặc giao cho agent khác. Kế hoạch và code không gộp cùng phiên kể cả việc nhỏ. Có thể chia thực thi thành các chặng nhỏ đã ghi trong kế hoạch. REWORK ở phiên thực thi mới trên cùng TASK_ID/nhánh.

## 2. Bộ hồ sơ Markdown bắt buộc

Các file cùng nằm trong `docs/plans/`, dùng cùng TASK_ID. Chat chỉ tóm tắt và dẫn link; quyết định, tiến độ và bằng chứng cần thiết phải được ghi vào file.

| File | Nội dung tối thiểu |
|---|---|
| `<TASK_ID>.md` | Yêu cầu/chốt của chủ dự án; baseline SHA, nhánh/worktree; mục tiêu và ngoài phạm vi; nguyên nhân/phương án/lý do; file/hàm cụ thể; bước nhỏ có thứ tự/phụ thuộc và tiêu chí hoàn tất; test Debug/Release, build APK, QA, bàn giao; 3–6 thao tác thử; rủi ro, rollback an toàn, trường hợp dừng và điều chưa rõ. |
| `<TASK_ID>-bao-cao.md` | Agent/model/phiên (không biết thì ghi không xác định); từng bước và trạng thái; file thay đổi/commit; kết quả thực và lý do; lệnh test/build, log, passed/skipped/failed; APK/thiết bị/bằng chứng; lỗi, phần chưa xong, giới hạn và bước tiếp. Ghi rõ phần Codex/Claude đã làm. |
| `<TASK_ID>-ban-giao.md` | Checkpoint hiện tại theo mục 3, đủ để một phiên Codex hoặc Claude Code mới tiếp tục mà không cần lịch sử chat. |
| `<TASK_ID>-nghiem-thu-so-bo.md` | Agent/phiên mới, source commit/APK hash, phạm vi kiểm nhanh, bằng chứng được dùng/chạy lại, lỗi cần sửa có vị trí/bước tái hiện, phần chưa kiểm, kết luận sơ bộ. |
| `<TASK_ID>-nghiem-thu.md` | Trạng thái chính thức, đúng bản/phạm vi, quyết định của chủ dự án, ngày/nguồn xác nhận, giới hạn còn lại; không sửa kết luận lịch sử. |
| `<TASK_ID>-apk-test.md`, `<TASK_ID>-hinh-nghiem-thu.md` | Khi có APK/QA UI: metadata, đường dẫn thật, SHA-256/bytes, source commit, gate cài, URL Drive đã kiểm, ảnh/video và giới hạn theo CLAUDE.md mục 8–9. |

Tạo file khi tới giai đoạn tương ứng, không tạo kết quả giả cho giai đoạn chưa diễn ra. Báo cáo chính ≤ ~250 dòng; chi tiết dài chuyển sang phụ lục `.md` có liên kết. Không ép kế hoạch vào giới hạn dòng khiến thiếu chỉ dẫn thực thi. Không lưu secrets, thông tin đăng nhập hoặc Telegram PII trong hồ sơ.

## 3. Checkpoint để đổi Codex ↔ Claude Code

Agent cập nhật `*-ban-giao.md` sau mỗi bước nhỏ/mốc commit/test/build/QA, trước khi kết thúc phiên, đổi vai/model hoặc thấy sắp hết quota. Không đợi gần hết quota mới ghi toàn bộ tiến độ. Đầu HANDOFF.md chỉ là chỉ mục tới nhiệm vụ/hồ sơ hiện tại, cập nhật khi an toàn; file bàn giao theo TASK_ID là nguồn chi tiết.

Mỗi checkpoint có:

1. TASK_ID, thời gian, người/công cụ/model, vai và chặng; yêu cầu/quyết định của chủ dự án còn hiệu lực.
2. Đường dẫn repo/worktree thực, nhánh, baseline source, commit kế hoạch, HEAD đã kiểm và commit source dùng build; xác định commit chứa checkpoint qua `git log -1 -- docs/plans/<TASK_ID>-ban-giao.md` để tránh tự tham chiếu SHA.
3. Liên kết kế hoạch/báo cáo/sơ bộ/APK; bảng `Bước / Xong-Đang làm-Chưa làm-BLOCKED / Bằng chứng / Việc còn lại`.
4. File đã sửa nhưng chưa commit, mục đích, phần đang dở và bằng chứng đối chiếu (diff/sha của file mã hoặc tài liệu an toàn). Không hash/đọc bí mật; file không rõ nguồn phải tách rõ, không nhận là của mình.
5. Test/build đã chạy, source tương ứng, kết quả/log; tác vụ còn chạy có máy/clone/PID/log/file exit code nếu biết. Không tự chạy một build/cài thứ hai khi chưa xác định tác vụ trước đã kết thúc.
6. APK hiện có và bản đã cài theo thiết bị, hash/package/version/cert, config chỉ boolean; QA đã làm, màn hình cuối, artifact/link, giới hạn chưa chứng minh. Không dùng APK cũ như bằng chứng source mới.
7. Vấn đề/rủi ro/chỗ cần chủ dự án quyết định, lựa chọn đã loại và lý do ngắn; bước tiếp theo chính xác, điều kiện chạy và điều kiện dừng.
8. Trạng thái bàn giao: agent cũ đã dừng hay còn tác vụ; prompt tiếp tục tự đủ nghĩa theo mẫu bên dưới. Không ghi thông tin chưa xác minh như sự thật.

Nếu quota cắt đột ngột trước checkpoint cuối, phiên nhận đọc checkpoint gần nhất rồi đối chiếu Git/file/log. Ghi rõ khoảng chưa xác minh; không đoán đã xong, không lặp cài APK/thao tác thiết bị chỉ vì thiếu chat. Việc bàn giao giữ liên tục bằng dữ liệu trên đĩa, không bảo đảm phục hồi phần suy nghĩ chưa được ghi.

### Mẫu prompt tiếp tục dùng cho cả Codex và Claude Code

```text
Tiếp tục TASK_ID <...>, vai <lập kế hoạch/thực thi/sơ bộ>, chặng <...>.
Worktree <đường dẫn thực>; nhánh dot/<TASK_ID>; HEAD dự kiến <SHA>.
Đọc CLAUDE.md mục 0 (Codex đọc thêm AGENTS.md), quy trình hiện hành,
docs/plans/<TASK_ID>-ban-giao.md, kế hoạch và báo cáo được dẫn trong đó.
Phiên trước đã dừng: <bằng chứng/trạng thái>; phần còn chạy: <không có/chi tiết>.
Kiểm Git, đối chiếu checkpoint và phần chưa commit trước khi sửa.
Tiếp tục bước <Sx, việc cụ thể>; không làm lại phần có bằng chứng còn hợp lệ.
Giữ nguyên phạm vi, cập nhật hồ sơ .md sau mỗi mốc. Có nguồn gốc/HEAD
không khớp hoặc agent cũ còn sửa code thì dừng và báo; không reset/clean/stash.
```

Chủ dự án mở phiên mới và giao tiếp; chỉ một agent sửa code tại một thời điểm. Phần chưa commit chỉ được tiếp nhận khi nguồn gốc khớp bàn giao và phiên cũ đã dừng. Không rõ nguồn thì báo chặn, không tự commit/gộp. Hết quota không phải lý do tạo nhánh/dự án mới hay tự đổi phạm vi.

## 4. Quyền dùng Sony và phạm vi kiểm tra

- Chủ dự án cho phép dùng Sony đang kết nối ở cả ba vai agent: khảo sát UI/tái hiện khi lập kế hoạch, thử trong thực thi, kiểm luồng chính khi sơ bộ. Không cần xin lại quyền cho thao tác an toàn thuộc nhiệm vụ; ghi mục đích/kết quả vào hồ sơ đúng vai.
- Kiểm đúng máy/ADB serial/model mỗi phiên. Sony `QV725WEQ54` trên Tiny, Shanling `30313860180939` trên Ni là thông tin tham chiếu, phải xác minh lại. Không dùng Sony thay chứng cứ Shanling/Y700/S25/FYT.
- Lập kế hoạch và sơ bộ dùng app/bản APK sẵn có đã kiểm; không tự code/build mới. Cài chỉ `install -r` sau package/version/cert/config và trạng thái phát hợp lệ theo luật gốc; bị chặn thì ghi BLOCKED đúng phần. Không uninstall/clear/downgrade/logout/đổi tài khoản/xóa cache/TDLib hay cài FYT.
- Tiny chỉ build/QA trong clone cô lập từ Git bundle đối chiếu SHA-256; không sửa repo Tiny gốc/qua T:, không chuyển bí mật về Ni. Không tranh thiết bị đang được người/agent khác dùng.
- Màn chỉ bật trước bước UI; cleanup `finally` tắt và kiểm OFF theo [vòng đời màn hình](SCREEN_LIFECYCLE_QA.md). Không đổi timeout/brightness/AOD/bảo mật. Không bật điện thoại chỉ để sửa tài liệu.
- Giữ artifact cũ, ảnh riêng tư, không public Telegram PII. Khi có QA UI, tạo `00_tong_hop.png`; Media3 PLAYING không chứng minh âm thanh thật.

## 5. Sơ bộ, chính thức và kết thúc

Thực thi bàn giao APK hợp lệ, báo cáo và prompt cho [phiên sơ bộ mới](PROMPT_NGHIEM_THU_SO_BO.md). Sơ bộ ưu tiên đọc bằng chứng đã có, diff và file rủi ro, kiểm source/APK, vài thao tác Sony nếu hữu ích; chạy lại khi có thay đổi/thiếu/mâu thuẫn, ghi lý do. Không mặc định chạy lại toàn bộ test/build hoặc review nhiều agent.

Kết luận sơ bộ chỉ dùng `SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN`, `SƠ BỘ: CẦN SỬA` hoặc `SƠ BỘ: BLOCKED`. Không có lỗi chặn trong phạm vi kiểm không có nghĩa đã nghiệm thu toàn bộ. Agent bàn giao kết quả cho chủ dự án [nghiệm thu chính thức](PROMPT_CHU_DU_AN_NGHIEM_THU.md); chính thức vẫn **CHỜ CHỦ DỰ ÁN NGHIỆM THU** (hoặc BLOCKED khi thiếu điều kiện).

Chỉ ghi PASS sau xác nhận ĐẠT đúng bản/phạm vi; chủ dự án yêu cầu sửa thì REWORK ở phiên thực thi mới rồi sơ bộ lại phần ảnh hưởng. Kết luận sơ bộ không tự cho phép sửa code, merge hay phát hành. Commit tài liệu trên nhánh nhiệm vụ rồi dừng; merge cần ĐẠT và chỉ thị riêng, push/public cần chỉ thị riêng. Không tự chọn việc kế.

Phiên chỉ sửa quy trình: vẫn ghi kế hoạch/báo cáo/bàn giao và commit, không build/cài hoặc yêu cầu nghiệm thu APK. Agent chỉ sửa CLAUDE.md, AGENTS.md và docs/workflow khi chủ dự án yêu cầu.
