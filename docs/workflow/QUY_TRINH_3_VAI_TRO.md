# MELOVAULT — Quy trình 3 vai trò, hội thoại độc lập

## CẬP NHẬT 26/10/2026 — Codex thực thi, Claude nghiệm thu (ưu tiên hơn mọi mục bên dưới)

Chủ dự án quyết định ngày 26/10/2026 (chi tiết CLAUDE.md mục 0): **Codex thay Sonnet ở vai thực thi**; ở mọi mục bên dưới "Sonnet" đọc là **"Codex thực thi"**. Claude Opus vẫn nghiệm thu.

- **Nhiệm vụ nhỏ** (đạt đủ điều kiện làn nhanh): chủ dự án CHỐT → **Codex `gpt-6-astra` medium** (phiên mới) viết `docs/plans/<TASK_ID>.md` và chỉ commit plan → **Codex `gpt-6.1-sol` xhigh** (phiên mới, GỘP) code + test + build + QA + báo cáo → **Claude Opus nghiệm thu**.
- **Nhiệm vụ lớn**: chủ dự án CHỐT → **Claude Opus lập kế hoạch** (phiên mới) → **Codex `gpt-6-astra` medium** phiên 1 (code + test Ni) và phiên 2 (build Tiny + QA + báo cáo), mỗi phiên là phiên Codex mới → **Claude Opus nghiệm thu** (phiên mới, khác phiên lập kế hoạch).
- REWORK: phiên Codex MỚI, cùng model thực thi, trên chính nhánh. Codex lỗi/hết hạn mức: dừng, chủ dự án có thể chuyển dự phòng Sonnet.
- Prompt mẫu Codex: `docs/workflow/PROMPT_CODEX.md`. Codex đọc luật qua `AGENTS.md` ở gốc repo. Codex không sửa tài liệu quy trình/nghiệm thu, không upload APK, không merge.
- Tính độc lập: nghiệm thu luôn là phiên Claude mới; với nhiệm vụ lớn, phiên nghiệm thu không phải phiên đã lập kế hoạch và chấm đúng tiêu chí PASS/FAIL đã ghi, không nới.

## CẬP NHẬT 25/10/2026 — Opus nghiệm thu thay ChatGPT (ưu tiên hơn mọi mục bên dưới)

Chủ dự án quyết định ngày 25/10/2026: **Claude Opus trong một hội thoại MỚI (không Resume phiên lập kế hoạch) làm vai NGHIỆM THU thay ChatGPT.** Ở mọi mục bên dưới, chữ "ChatGPT" ở vai đề xuất/kiểm toán/nghiệm thu/merge/chuẩn hóa ảnh/bàn giao link APK đọc là **"Opus nghiệm thu"**. Chỉ Opus (khi chủ dự án yêu cầu) biên tập SOP, prompt mẫu và tài liệu nghiệm thu; Sonnet không.

Mỗi vòng: Opus nghiệm thu đề xuất (hoặc chủ dự án tự nêu) → chủ dự án CHỐT → **Opus lập kế hoạch** (phiên mới, cuối phiên đưa prompt Sonnet điền từ mẫu) → **Sonnet phiên 1** code + test Ni → **Sonnet phiên 2** build Tiny + QA + báo cáo (nhiệm vụ nhỏ gộp 1 phiên) → **Opus nghiệm thu** (phiên mới, prompt `PROMPT_OPUS_NGHIEM_THU.md`) gửi ảnh + link APK → chủ dự án nói ĐẠT/báo lỗi → Opus nghiệm thu ghi PASS và merge sau khi chủ dự án xác nhận thao tác merge. REWORK: Opus nghiệm thu ghi lỗi và đưa prompt Sonnet REWORK; Sonnet MỚI sửa trên chính nhánh. BLOCKED: xác định nút thắt trước khi làm tiếp.

Tính độc lập: phiên nghiệm thu chấm theo tiêu chí PASS/FAIL đã ghi trong kế hoạch, tự chạy lại test/build và xem ảnh thật; không dựa vào lời tóm tắt của Sonnet, không nới tiêu chí để đạt. Tiết kiệm token theo CLAUDE.md mục 10.

---

*Bản gốc 18/10/2026 (đọc với ánh xạ ở trên):* Chủ sở hữu phê duyệt quy trình ngày 18/10/2026. ChatGPT duy nhất biên tập SOP, prompt mẫu và tài liệu nghiệm thu. Claude không tự chỉnh tài liệu quy trình, không lập prompt công việc tiếp theo. Mỗi vòng: ChatGPT đề xuất → chủ dự án CHỐT → Opus lập kế hoạch → Sonnet thi hành → ChatGPT QA độc lập → chủ dự án thử thao tác → ChatGPT PASS và merge → đề xuất vòng sau. Nếu REWORK mở Sonnet hội thoại MỚI trên chính nhánh; nếu BLOCKED xác định nút thắt trước khi thực thi tiếp.

## A. Bắt đầu nhiệm vụ
- Mỗi nhiệm vụ được chủ dự án duyệt có TASK_ID duy nhất: chữ thường, dấu gạch nối, ví dụ downloaded-count-startup. Mở nhánh **dot/<TASK_ID>** từ HEAD nhánh tích hợp đã được xác minh; không sửa mã trực tiếp trên nhánh tích hợp.
- Ghi baseline commit đầy đủ vào kế hoạch. Nếu code/branch/working tree thay đổi bất ngờ, có agent khác làm, hoặc thiếu dữ liệu: dừng. Không reset, stash, clean, overwrite.
- **Hội thoại Opus** riêng đọc CLAUDE.md và HANDOFF.md, source thực, backlog. Tạo docs/plans/<TASK_ID>.md và chỉ commit docs trên dot/<TASK_ID>. Trong plan phải có mục 3–6 thao tác **Bác sĩ tự kiểm tra** có thiết bị/màn hình/từng nút và kết quả quan sát được. Kết thúc nhắc mở Sonnet ở phiên mới. Không code/test APK.
- **Hội thoại Sonnet** riêng đọc kế hoạch và commit của Opus; xác nhận nhánh/baseline. Thực thi đúng plan, unit test và build; ghi docs/plans/<TASK_ID>-bao-cao.md. Thử Shanling trên Ni khi ADB, phiên bản, chữ ký và Telegram config hợp lệ; lưu ảnh trong docs/plans/artifacts/<TASK_ID>/. Không claim test khi thiếu thiết bị; dừng nếu cách làm cần thay đổi kiến trúc hoặc dữ liệu.
- **ChatGPT** đọc Git/diff và report, chủ động test+build lại trên Ni, xem PNG thực, đánh giá thông tin dữ liệu/rollback, ghi docs/plans/<TASK_ID>-nghiem-thu.md. Nếu cần QA Sony/Tiny thì chỉ QA trong môi trường cô lập và không tự đánh dấu thay cho Shanling/Y700.

## B. Kết quả bắt buộc
- **PASS:** các test và QA kỹ thuật cần thiết đạt, mọi ảnh/thiết bị đối chiếu, và **chủ dự án đã xác nhận** kết quả 3–6 thao tác qua ảnh/video QA từ xa hoặc tự thử trực tiếp. Sau đó ChatGPT mới merge dot/<TASK_ID> (không force) vào nhánh tích hợp được phê duyệt. Nếu code vốn đã nằm trên nhánh tích hợp do vòng cũ, không tự viết lại lịch sử; ghi ngoại lệ và chỉ áp dụng quy ước dot cho việc mới.
- **REWORK:** diff/test/UI/logic không đúng kế hoạch. ChatGPT chỉ rõ file và bằng chứng trong nghiệm thu; chủ dự án duyệt sửa, Sonnet MỚI tiếp tục trên dot/<TASK_ID>, không tạo nhánh khác.
- **BLOCKED:** thiếu thiết bị ADB, config, credential, quyền, dữ liệu hoặc chưa có xác nhận của chủ dự án. Test xanh nhưng thiếu owner duyệt bằng chứng QA trực tiếp/từ xa được gắn nhãn BLOCKED — CHỜ CHỦ DỰ ÁN, không merge. Nếu Sonnet đã commit, giữ nguyên branch.
- Nếu chỉ có test JVM và ảnh Sony mô phỏng → không tuyên bố đã thử Shanling thật/Y700 thật. Số liệu screen/product thực phải có nguồn.

## C. Luật cài máy và bảo vệ dữ liệu
- Dùng adb devices -l trên **Ni** cho Shanling; adb devices -l trên **Tiny** cho Sony. Mỗi lần truy vấn model/manufacturer bằng adb -s SERIAL shell getprop.
- Ni: **ADB xác minh thành công** Shanling M3 Ultra Android 10, serial **30313860180939**, trạng thái device (18/10/2026 23:30 +07). Thiết bị đang cài MeloVault vc107 khi kiểm; mã USB PnP không thay thế ADB serial. Trước mỗi phiên phải kiểm tra lại adb devices -l và getprop. Việc ADB sẵn sàng KHÔNG có nghĩa Beta109 đã được cài/QA hay chủ dự án đã nghiệm thu.
- Tiny: Sony Xperia XQ-AS52, serial QV725WEQ54 được xác minh qua ADB (18/10/2026).
- Sonnet cài DEBUG APK lên Shanling **chỉ khi hợp lệ**: đúng package, không downgrade versionCode, cert khớp, Telegram config khả dụng nếu test TDLib, thiết bị authorized. Lệnh duy nhất **adb -s SERIAL install -r PATH**. Cấm adb uninstall, pm clear, xoá app data, TDLib cache/session, đăng xuất Telegram. Signature mismatch → dừng báo chủ dự án, không tự gỡ cài lại. Ni APK thiếu Telegram API ID không phải bản để test Telegram.
- Mở màn hình đúng plan, thao tác và dùng adb -s SERIAL exec-out screencap -p. Trên Windows khi xuất PNG nhị phân dùng cmd /c với dấu > trỏ vào docs/plans/artifacts/<TASK_ID>/; ghi đường dẫn ảnh vào report, đối chiếu PNG đọc được và đảm bảo không chứa bí mật/PII nếu muốn chia sẻ. Artifacts folder không commit.
- Sony trên Tiny để smoke/đối chiếu khác tầng Shanling. Bản phát hành thử/Drive cá nhân chỉ sau kiểm toán kỹ thuật, owner QA vẫn bắt buộc để PASS. Không tự cài FYT.

## D. Làn nhanh
Sonnet làm thẳng, **không cần Opus**, chỉ khi **cả**: (a) một màn hình, (b) tối đa hai file production, (c) tối đa 80 dòng production đổi theo diff (không tính test/report), (d) không DB/schema/TDLib/auth/account/cache/Media3/queue/architecture/permission/security/signing/version change, (e) không migration/data-loss risk, (f) ChatGPT đề xuất và owner chốt là "làn nhanh". Sonnet vẫn tạo plan một trang, report, test, Shanling screenshot nếu UX và phải qua ChatGPT/owner PASS như thường.

## E. Nơi lưu / quản trị
- File canonical trong repo: docs/plans/<TASK_ID>.md (Opus), docs/plans/<TASK_ID>-bao-cao.md (Sonnet), docs/plans/<TASK_ID>-nghiem-thu.md (ChatGPT). BACKLOG vẫn là danh sách, HANDOFF là mốc phiên bản.
- Ảnh/log riêng docs/plans/artifacts/<TASK_ID>/ bị Git ignore, không đưa audio/PII/OTP/khóa vào Git. Thư mục D:\Bots\_handoff chỉ bản sao.
- Hai prompt chính thức nằm trong docs/workflow/PROMPT_OPUS.md và docs/workflow/PROMPT_SONNET.md. Chỉ ChatGPT thay đổi chúng.
- Để tiết kiệm quota, Opus viết plan đủ chính xác một lần, Sonnet không tự phân tích lại kiến trúc; nếu plan sai/thiếu, Sonnet dừng và báo để ChatGPT quyết định REWORK/BLOCKED. Không resume lịch sử giữa phiên.
- Đầu/cuối đều dùng tiếng Việt đời thường. Claude cuối phiên chỉ báo đã làm, chưa xác minh, rủi ro; **không tự đặt nhiệm vụ tiếp, không tự đưa prompt phiên sau**.

## F. Quy trình riêng khi Claude bị hết quota
Không giả định hội thoại được chuyển giữa tài khoản. Agent mới dùng đúng repo Ni, CLAUDE.md, HEAD Git, ba file TASK_ID và HANDOFF để khôi phục. Nếu đầu việc dở và uncommitted files: không xóa, không reset; chỉ báo tiến độ/dirty diff để ChatGPT quyết định, không chạy hai agent song song.

## G. Quy trình nghiệm thu từ xa được duyệt 19/10/2026

- Opus vẫn chỉ lập kế hoạch; Sonnet tự QA từ xa qua ADB trên thiết bị thật, ChatGPT kiểm toán lại và gửi hình/video nghiệm thu trực tiếp trong hội thoại, chủ dự án chỉ cần xem và chốt. Mục 3–6 bước bác sĩ tự kiểm trong plan được Agent thực hiện nếu an toàn, không đòi bác sĩ trực tiếp đến Shanling/Sony.
- Ni/Shanling M3 Ultra: serial đã xác minh 30313860180939; Tiny/Sony Xperia 5 II: QV725WEQ54. Mỗi phiên xác nhận lại adb devices -l và getprop; dùng clone/build đã kiểm mã, version, chữ ký, Telegram config. Cài giữ dữ liệu chỉ adb install -r, nếu signature mismatch hoặc version downgrade thì dừng.
- Sonnet phải thử click/chuyển màn/menu/player/Next tương ứng 3–6 bước, chụp trước/sau bằng adb exec-out screencap -p, có thể screenrecord nếu an toàn, lưu docs/plans/artifacts/<TASK_ID>/ (private, không commit), ghi timestamp, model/serial, kỳ vọng, thực tế, PASS/FAIL/BLOCKED, Media3 playback state/position và log crash/ANR.
- ChatGPT truy cập ảnh thật trên Ni/Tiny, xác nhận code/diff/test/build, và đính kèm ảnh/video quan trọng vào chat cho owner; không chỉ gửi đường dẫn Windows mà owner phải tự vào tìm. Không phát tán Telegram PII hay thông tin tài khoản.
- Trạng thái Media3/progress và ảnh không tự chứng minh âm thanh thực sự phát ra; nếu không có ghi âm/kiểm tra độc lập thì ghi CHƯA XÁC NHẬN ÂM THANH THỰC. Không tự bật airplane/tắt Wi-Fi, sửa account, xóa file hoặc session trái plan. Những bước không thể làm an toàn phải giữ CHƯA KIỂM CHỨNG.
- Owner duyệt từ xa là phê duyệt nghiệm thu cho phần đủ bằng chứng; ChatGPT mới quyết PASS/REWORK/BLOCKED, ghi hạn chế chưa kiểm chứng và không merge trước owner duyệt. Không yêu cầu bác sĩ có mặt ở địa điểm thiết bị.
- Kế hoạch offline-one-tap-play commit 29c54f9 ĐƯỢC CHỦ DỰ ÁN DUYỆT NGUYÊN VĂN, không Opus REWORK. Ý kiến phản biện trước đó giữ làm checklist QA, không còn là blocker lập kế hoạch.

## H. Quy tắc bắt buộc bảo vệ màn hình thiết bị QA (19/10/2026)

Áp dụng cho mọi phiên Opus/Sonnet và cả ChatGPT kiểm toán. **M3 Ultra (Ni) và Sony (Tiny) mặc định để màn hình tắt; chỉ bật lúc cần tap/chụp/quay UI**, xong bước hoặc kết thúc QA thì **tắt ngay và xác minh Display State=OFF**. Không tắt lúc đang có người/agent khác thao tác, không tắt nguồn hoặc sửa data. Không cần màn ON để build, test JVM, đọc log hoặc chờ chủ dự án nghiệm thu từ xa.

Chính sách và lệnh an toàn ở `docs/workflow/SCREEN_LIFECYCLE_QA.md`: KEYCODE_WAKEUP=224 bật khi cần, KEYCODE_SLEEP=223 tắt theo hướng idempotent (tránh KEYCODE_POWER=26 toggle), `dumpsys power` + `dumpsys display` kiểm tra; `Dozing` chỉ được coi tắt khi display thực sự OFF. Sonnet bảo đảm cleanup trong `finally` sau cả PASS/FAIL/BLOCKED và ghi trạng thái màn hình cuối từng máy. Nếu cần người dùng mở khóa hợp lệ hoặc screen cannot turn OFF, báo rõ, không tự phá security.

## H. Bàn giao ảnh/video có liên kết bấm trực tiếp (19/10/2026)

- ChatGPT đưa bộ screenshot/video tiêu biểu lên thư mục Google Drive Ni N riêng tư sau khi QA; kiểm Drive owner-only, URL thực từ connector, không mở share public. Không upload có OTP/credentials; với Telegram metadata cá nhân chỉ lưu riêng tư.
- ChatGPT tạo docs/plans/<TASK_ID>-hinh-nghiem-thu.md liệt kê từng thao tác và URL bấm trực tiếp, cùng folder QA. Claude Code có thể mở Markdown và Ctrl+Click; trong ChatGPT cung cấp link Drive tương ứng. Không cho link giả từ tên file Windows.
- Sonnet chịu trách nhiệm thu ảnh/video, không tự thay permission Drive hoặc công bố file. Nếu chưa có ảnh hay chưa hoàn tất đồng bộ, phải nói rõ và không đánh dấu giao nộp xong.

## I. Giao APK thử nghiệm mới từ xa (duyệt 19/10/2026)

- Mỗi APK test mới, phân biệt bằng TASK_ID + Git commit + SHA-256 file, đều phải được **bàn giao cho owner bằng link bấm trực tiếp** khi vượt qua cổng kiểm tra APK hợp lệ. Không đồng nhất APK mới với việc vừa chạy lại Gradle hoặc dùng lại file cũ.
- Sonnet tạo APK và viết báo cáo gồm: đường dẫn Windows, bytes, SHA256, cert SHA256, package, vc/vname, đúng source commit, Debug/Release, Telegram config có/không, tests, trạng thái ADB cài Shanling/Sony và các lỗi QA. Sonnet **không tự đổi Drive permissions, public hoặc upload vào nơi không được chỉ định**, không xóa APK cũ.
- ChatGPT tự xác minh file APK/bằng chứng, chỉ đưa bản có package/phiên bản/chữ ký/cấu hình phù hợp vào **Drive Ni N owner-only**, lưu từng TASK_ID/ngày/commit độc lập, xác minh metadata share và gửi **link APK** (không chỉ folder link) cùng SHA-256 trong chat. Dùng `docs/plans/<TASK_ID>-apk-test.md` làm index bấm mở từ Claude Code. Nếu upload chưa xác minh được, phải báo BLOCKED, không cung cấp link đoán.
- APK có thể được đưa lên Drive **trước khi owner PASS tính năng** nếu đủ điều kiện an toàn để QA: đặt nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**, nêu test còn thiếu; không gọi Stable hay production. PASS/merge vẫn cần kiểm toán độc lập và owner duyệt như quy trình cũ.
- Không đưa secrets, local.properties, signing key, TDLib session, cache lên Drive. Không sửa file code để đạt việc upload, không tự tăng vc hoặc ép hạ version, không thay đổi chính sách screen lifecycle.
