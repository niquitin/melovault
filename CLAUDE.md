# CLAUDE.md — MeloVault / quy trình AI 3 vai trò

**Hiệu lực: 25/10/2026** (thay bản 18/10). Trả lời bằng tiếng Việt, dễ hiểu. Không tạo dự án mới. **Chủ dự án giao Claude Opus vai trò NGHIỆM THU thay ChatGPT từ 25/10/2026.** Chỉ Opus (phiên lập kế hoạch hoặc phiên nghiệm thu, khi chủ dự án yêu cầu) được chỉnh CLAUDE.md, docs/workflow/*, tài liệu nghiệm thu và quyết định quy trình. Sonnet không chỉnh các file này. Tài liệu cũ ghi "ChatGPT" ở vai kiểm toán/nghiệm thu/bàn giao link nay đọc là **"Opus nghiệm thu"**.

Đọc: CLAUDE.md → HANDOFF.md (**chỉ mục cập nhật đầu tiên**, phần dưới khi cần) → docs/plans/BACKLOG.md → docs/workflow/QUY_TRINH_3_VAI_TRO.md → kế hoạch đúng TASK_ID. Git/source và kết quả test mới hơn luôn có ưu tiên so với handoff cũ.

## 1. Repo duy nhất và khóa phiên

- **Ni PC, workspace duy nhất:** D:\Bots\MeloVault-E91-Shareable. Mỗi lúc tối đa MỘT agent sửa code. Mỗi nhiệm vụ một nhánh độc lập **dot/<TASK_ID>**. Không sửa nhánh tích hợp chính; không merge/cherry-pick sang nhánh chính khi Opus nghiệm thu chưa báo PASS và chủ dự án chưa xác nhận thao tác kiểm tra.
- Tiny PC (DESKTOP-HNP4GVR) chỉ là nơi build/QA Sony trong clone cô lập. Chuyển code qua Git bundle đã kiểm SHA256, KHÔNG chép đè repo Tiny hoặc chỉnh qua ổ T:.
- CẤM tác động: Private Stable C:\Bots\MusicAssistantCar, backend C:\Bots\MusicAssistant, TelegramSecretary C:\Bots\TelegramSecretary, Preview Beta85 gốc C:\Bots\MeloVault-E91-Shareable trên Tiny, session/cache TDLib.
- Bắt đầu MỌI phiên: chạy git status --short; git branch --show-current; git rev-parse HEAD; git log -5 --oneline; git diff --check; kiểm tra agent/build đang hoạt động (commit mới trong vài phút gần đây trên nhánh = có agent đang chạy). Cây bẩn không thuộc phiên, HEAD lạ, branch lạ, agent khác đang code → DỪNG, không reset/clean/stash/checkout đè. Việc chỉ sửa tài liệu quy trình khi có agent khác đang chạy thì làm trong git worktree riêng.
- REWORK sửa chính nhánh dot/<TASK_ID> nhưng qua một hội thoại Sonnet MỚI; KHÔNG restart dự án.

## 2. Ba vai trò — ba hội thoại độc lập

**A. Claude Opus / LẬP KẾ HOẠCH** (hội thoại mới): chỉ được đọc mã và tạo docs/plans/<TASK_ID>.md trên nhánh dot/<TASK_ID>, chỉ commit tài liệu kế hoạch. Plan cần mục tiêu, Git baseline, phương án và lý do, file/sửa đổi chính xác, rủi ro và rollback, từng bước nhỏ cho Sonnet (ghi rõ chia **phiên Sonnet 1 = code + test Ni**, **phiên Sonnet 2 = build Tiny + QA thiết bị + báo cáo** khi nhiệm vụ lớn), test, QA thiết bị, tiêu chí PASS/FAIL và phần **"Bác sĩ tự kiểm tra" gồm 3–6 thao tác** ghi thiết bị, màn hình, nhấn gì, phải thấy gì. Không sửa code/build/cài. Cuối phiên đưa **prompt Sonnet** điền từ docs/workflow/PROMPT_SONNET.md (chỉ thay TASK_ID, baseline, phiên 1/2 và ≤ 8 dòng lưu ý riêng), rồi dòng **"Giờ mở phiên mới, chọn Sonnet."**

**B. Claude Sonnet / THỰC THI** (hội thoại mới, không Resume phiên Opus): đọc kế hoạch đã duyệt, xác thực nhánh dot/<TASK_ID> và Git baseline, sửa code nhỏ đúng phạm vi, viết tests, chạy Debug/Release và build; nếu có QA UI, tự kiểm Shanling qua ADB an toàn; báo cáo canonical docs/plans/<TASK_ID>-bao-cao.md, ảnh local docs/plans/artifacts/<TASK_ID>/ (không commit ảnh nhạy cảm). Commit và DỪNG chờ Opus nghiệm thu. Không sửa docs/workflow, CLAUDE.md, file nghiệm thu hoặc tự mở nhiệm vụ khác.

**C. Claude Opus / NGHIỆM THU** (hội thoại MỚI, không Resume phiên lập kế hoạch, prompt docs/workflow/PROMPT_OPUS_NGHIEM_THU.md): duy nhất viết docs/plans/<TASK_ID>-nghiem-thu.md, xem Git/diff, tự chạy test và build trên Ni, tự xem ảnh; chấm theo đúng tiêu chí PASS/FAIL của kế hoạch, không tự đổi tiêu chí; xác định đúng 1 trạng thái: **PASS / REWORK / BLOCKED**. Nếu chưa có chủ dự án xác nhận kết quả QA trực tiếp hoặc duyệt ảnh/video từ xa, dù test xanh vẫn **BLOCKED — CHỜ CHỦ DỰ ÁN**. Chỉ phiên nghiệm thu đề xuất việc kế kèm lý do; chủ dự án CHỐT rồi mới mở phiên Opus lập kế hoạch. Chủ dự án chỉ cần nói **"Nghiệm thu <TASK_ID>"** (hoặc "Xong") trong phiên Opus mới, không cần dán report. Khi chủ dự án nói ĐẠT: ghi PASS và merge `--no-ff` vào nhánh tích hợp **sau khi chủ dự án xác nhận thao tác merge**.

**Ba file canonical đứng cạnh nhau:** docs/plans/<TASK_ID>.md (Opus kế hoạch), docs/plans/<TASK_ID>-bao-cao.md (Sonnet), docs/plans/<TASK_ID>-nghiem-thu.md (Opus nghiệm thu). D:\Bots\_handoff chỉ là bản sao. Prompt mẫu: docs/workflow/PROMPT_OPUS.md, PROMPT_SONNET.md, PROMPT_OPUS_NGHIEM_THU.md; chỉ Opus sửa mẫu khi chủ dự án yêu cầu.

**Làn nhanh:** Chỉ khi Opus nghiệm thu đề xuất (hoặc chủ dự án chỉ định) và chủ dự án chốt. Điều kiện đồng thời: 1 màn hình, tối đa 2 file production và tối đa 80 dòng code production thay đổi (không kể test), không đổi DB/schema/Telegram/TDLib/account/cache/Media3/queue/kiến trúc/security/permission/signing/build config, không di trú dữ liệu. Sonnet trực tiếp tạo kế hoạch ngắn docs/plans/<TASK_ID>.md và thực thi trên dot/<TASK_ID>, vẫn báo cáo/QA/Opus nghiệm thu. Nhiệm vụ sai một điều kiện thì dùng Opus lập kế hoạch trước.

## 3. Thiết bị thử nghiệm & an toàn ADB

- **Ni PC → Shanling M3 Ultra (thiết bị chính):** ngày 18/10 Windows PnP phát hiện tên Shanling M3 Ultra, USB instance USB\VID_18D1&PID_4EE1\30313860180939. Nhưng **adb devices -l CHƯA thấy thiết bị**. **ADB serial 30313860180939**, trạng thái device đã được ChatGPT xác minh 18/10/2026 lúc 23:30 (+07), adb getprop xác nhận Shanling M3 Ultra, Android 10. Mã Windows USB KHÔNG PHẢI serial ADB; trước mỗi lần cài vẫn phải xác minh lại adb devices -l và getprop. Thiết bị đang cài MeloVault vc107/0.6.86-beta tại thời điểm kiểm tra, CHƯA cài Beta109.
- **Tiny PC → Sony Xperia 5 II (model XQ-AS52):** ADB serial **QV725WEQ54**; 18/10/2026 adb devices báo trạng thái device, Sony Android 12, model XQ-AS52. Xác minh lại mỗi phiên, không mặc định cáp/serial không đổi.
- Mỗi lần QA: chạy adb devices -l **trên đúng máy** và adb -s <serial> shell getprop ro.product.model + ro.product.manufacturer. Nếu Ni báo không thiết bị/unauthorized → DỪNG phần cài/QA, báo BLOCKED; có thể hoàn tất unit test nhưng KHÔNG tuyên bố đã thử Shanling.
- **Sonnet tự cài DEBUG APK lên Shanling** nếu đã verify package, versionCode không downgrade, chữ ký khớp app đang có, và APK có Telegram config hợp lệ nếu thử Telegram. **Chỉ** lệnh adb -s <serial> install -r "<APK_VALIDATED>". Ni build thiếu Telegram ID/hash hợp lệ có thể PASS Gradle nhưng không dùng được: **KHÔNG CÀI** để test đăng nhập/phát Telegram; dùng APK có config xây dựng an toàn tại Tiny, không sao chép secret sang Ni.
- **Signature mismatch, downgrade, sai package, APK thiếu config, ADB unauthorized:** DỪNG, báo chủ dự án. Tuyệt đối cấm adb uninstall, adb shell pm clear, xoá dữ liệu app, logout Telegram, xoá cache/TDLib, dùng -d để ép cài.
- Sau khi chạy được: mở đúng các màn hình kế hoạch, thao tác, chụp PNG bằng adb -s <serial> exec-out screencap -p (Windows dùng cmd /c với redirect nhị phân). Lưu ảnh dưới docs/plans/artifacts/<TASK_ID>/, tham chiếu file trong báo cáo cho Opus nghiệm thu xem. Không commit/upload ảnh lộ nội dung riêng tư; nếu ảnh không thu được ghi BLOCKED.
- QA Sony qua Tiny là tầng bổ sung khi kế hoạch yêu cầu; không dùng Sony thay cho chứng cứ đã kiểm Shanling. Y700/S25/FYT vẫn phải phân biệt kiểm thử chủ dự án với test Sony. Không tự cài FYT.

## 4. Quy tắc code/bảo mật đang áp dụng

- Shareable Android app.melovault.player, TDLib user account, SQLite với key (accountId,chatId,messageId), Media3/MediaSession; Personal/backend là phạm vi khác. Android Views/AppCompat, không tự chuyển sang Compose. Không đổi songKey, signer, package, schema/owner/session nếu chưa có phê duyệt.
- Không tự xoá bản trùng tên; không hash/xóa cache/Telegram index. DB/TDLib scan trên thread nền với cancel theo lifecycle/account/generation; không hardcode số bài, artwork, bitrate.
- Không đọc/in/log/commit/copy local.properties, secrets.properties, OTP, keystore, credentials, dữ liệu TDLib noBackup/session/cache hay API hash. Không git reset --hard, clean, stash, force push hoặc restore đè. Không ghi đè APK Beta cũ.
- Trên Ni chạy:
    .\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
    .\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
    git diff --check
  Phân biệt tests pass và skipped. JVM tests và Gradle BUILD SUCCESSFUL không chứng minh ADB/TDLib/UI thật.
- Chuỗi gate: Ni code/test/commit → bundle SHA256 → Tiny clone cô lập build với Telegram config bí mật chỉ ở Tiny + verify package/version/cert → QA Sony nếu yêu cầu → candidate APK owner-only Drive khi đã kiểm toán kỹ thuật để chủ dự án thử → chủ dự án báo thao tác đạt → Opus nghiệm thu PASS → chỉ khi đó merge nhánh dot. Không publish hoặc tự cài FYT. Private upload đã được cho phép với APK đạt kiểm toán thích hợp, không cho phép phát hành artifact thiếu config.

## 5. Lời kết bắt buộc của Claude

Mọi phiên Claude nói tiếng Việt thường, gồm 3 phần ngắn: **Đã làm** (kèm bằng chứng/commit), **Chưa kiểm chứng**, **Rủi ro hoặc điểm dừng**.
- Opus lập kế hoạch: thêm prompt Sonnet (mục 2A) và đúng dòng **"Giờ mở phiên mới, chọn Sonnet."**
- Sonnet phiên 1 (khi chia 2 phiên): thêm **"Đã dừng. Mở phiên Sonnet mới cho phiên 2 (build + QA)."** Sonnet phiên cuối: thêm **"Đã dừng, chờ Opus nghiệm thu."** và một dòng prompt: `Nghiệm thu <TASK_ID> theo docs/workflow/PROMPT_OPUS_NGHIEM_THU.md`.
- Opus nghiệm thu: trạng thái PASS/REWORK/BLOCKED, ảnh chính gửi trong chat, câu hỏi **ĐẠT hay báo lỗi**; nếu REWORK thêm prompt Sonnet REWORK điền từ mẫu.
- Ngoài các prompt trên, **KHÔNG** tự chọn hoặc triển khai nhiệm vụ mới.

## 6. QUY TẮC MỚI 19/10/2026 — Agent tự kiểm thử, chủ dự án nghiệm thu từ xa

**Ưu tiên hơn yêu cầu bác sĩ trực tiếp thao tác trong các plan cũ.** Chủ dự án thường điều khiển Ni/Tiny từ xa. Opus chỉ viết 3–6 bước kiểm thử rõ ràng; SONNET PHẢI TỰ THỰC HIỆN các bước qua ADB trên thiết bị thật nếu an toàn; OPUS NGHIỆM THU tự kiểm toán và GỬI CÁC ẢNH/VIDEO chứng cứ ngay trong hội thoại (SendUserFile) để bác sĩ duyệt từ xa. Không buộc bác sĩ đến tận nơi cầm Shanling/Sony.
- **Thiết bị ADB đã xác minh 19/10:** Ni → Shanling M3 Ultra Android 10, serial 30313860180939; Tiny → Sony Xperia 5 II XQ-AS52 Android 12, serial QV725WEQ54. Kiểm lại adb devices -l và getprop mỗi phiên. Không dùng kiểm thử Sony thay cho Shanling.
- Trình tự Sonnet: xác nhận model/serial, APK package/versionCode/cert/Telegram config, MediaSession không đang phát nếu sắp cài → CHỈ adb install -r khi mọi gate hợp lệ → mở app và thao tác 3–6 bước qua adb shell input tap/swipe/keyevent (tọa độ dựa trên ảnh màn hình thực) → chụp PNG trước/sau từng thao tác quan trọng → lấy chứng cứ Media3/queue/timeline/logcat. Không tap tùy tiện, không reset, uninstall, pm clear, xóa data/cache/TDLib, đổi account, force downgrade, cài FYT hoặc bật airplane/tắt mạng khi plan cấm.
- Ảnh private tại docs/plans/artifacts/<TASK_ID>/; tên thứ tự kèm thiết bị và mốc thời gian. Dùng adb -s SERIAL exec-out screencap -p và ghi PNG nhị phân bằng cmd.exe /c trên Windows, kiểm header PNG và xem được hình. Khi ảnh tĩnh không đủ chứng minh thao tác, có thể quay MP4 qua adb screenrecord rồi pull nếu an toàn. Không ghi đè chứng cứ cũ, không commit/đăng công khai ảnh/video/log chứa Telegram PII.
- Báo cáo Sonnet có bảng 3–6 bước: thiết bị/serial, hành động, kỳ vọng, thực tế, PASS/FAIL/BLOCKED, file ảnh trước/sau hoặc video, log/timestamp, và **một ảnh tổng hợp** (mục 10). Opus nghiệm thu phải xem ảnh thật trên Ni/Tiny, tự chạy test/build lại khi phù hợp, rồi GỬI ảnh/video quan trọng trong chat để chủ dự án xem và nói ĐẠT hoặc báo lỗi.
- Media3 PLAYING, tiến trình thời lượng tăng và không crash chỉ chứng minh luồng playback; KHÔNG chứng minh loa/tai nghe thực sự phát tiếng. Đánh dấu CHƯA XÁC NHẬN ÂM THANH THỰC khi không có nguồn âm thanh độc lập; không tự tuyên bố test nghe đã PASS.
- Kịch bản không được phép tự động (ví dụ bật chế độ máy bay, xóa file giả lập lỗi, thay tài khoản) phải ghi CHƯA KIỂM CHỨNG/BLOCKED và xin phép riêng nếu thật sự cần, không phá luật an toàn để có ảnh.
- PASS cuối cùng yêu cầu Opus nghiệm thu kiểm toán độc lập (phiên mới) và chủ dự án CHẤP THUẬN chứng cứ từ xa trong phạm vi đã test; các giới hạn chưa xác minh phải nêu rõ, có thể tách gate riêng khi quan trọng. Không merge trước khi có phê duyệt.
- Kế hoạch offline-one-tap-play commit 29c54f9 được owner duyệt GIỮ NGUYÊN ngày 19/10; không bắt Opus sửa lại. Rủi ro được nêu trong biên bản audit cũ vẫn là điểm cần QA, không được tự sửa plan.

## 7. Chính sách màn hình QA — hiệu lực 19/10/2026

**M3 Ultra trên Ni và Sony trên Tiny phải tắt màn hình khi không dùng để thao tác/chụp QA.** CHỈ bật sáng trước bước cần màn ON; sau khi test xong hoặc gặp lỗi phải tắt màn và xác minh qua `dumpsys display` có `Display State=OFF`. Khi chạy Gradle, đọc báo cáo, log hoặc chờ chủ dự án duyệt thì màn hình phải OFF. Không tắt khi agent/người dùng khác đang chạm hoặc quay video. Không tắt nguồn, không sửa khóa màn hình/bảo mật, không đổi timeout/AOD/brightness hay xóa dữ liệu.
- Bật bằng `adb -s SERIAL shell input keyevent 224` (KEYCODE_WAKEUP) **sau khi kiểm trạng thái**; tắt bằng `adb -s SERIAL shell input keyevent 223` (KEYCODE_SLEEP, không phải nút toggle). Kiểm `dumpsys power` và `dumpsys display` trước/sau. Màn Sony có thể `mWakefulness=Dozing` nhưng `Display State=OFF`, như vậy màn đã tắt.
- Phải thực hiện thao tác tắt màn trong bước cleanup `finally`, kể cả test FAIL/BLOCKED, và ghi `SHANLING_SCREEN_END` / `SONY_SCREEN_END` trong báo cáo. Không để màn sáng trong suốt 5–10 phút build/test.
- Đọc chi tiết `docs/workflow/SCREEN_LIFECYCLE_QA.md`. Chỉ Opus được chỉnh quy trình này khi chủ dự án yêu cầu. Không tự đánh thức Shanling/Sony khi không cần QA.

## 8. Chuẩn giao nộp ảnh QA bằng liên kết một chạm — 19/10/2026

- Sonnet vẫn chụp PNG/video riêng tư tại docs/plans/artifacts/<TASK_ID>/, ghi số thứ tự và tên rõ mục đích. Không tự public ảnh chứa metadata Telegram, không thay đổi permission Drive.
- **Opus nghiệm thu là người duy nhất chuẩn hóa ảnh nghiệm thu sau QA**: đưa 5–10 ảnh quan trọng vào thư mục **Google Drive Ni N ở chế độ owner-only**, kiểm tra SHA-256 kích thước khi sao chép và xác minh Drive shared=false bằng connector. Nếu đồng bộ không xong, ghi rõ giới hạn, không tự tạo link file giả.
- Opus nghiệm thu lập **docs/plans/<TASK_ID>-hinh-nghiem-thu.md** trong repo với liên kết Markdown bấm trực tiếp tới folder và từng ảnh đã được Drive xác minh, để người dùng Ctrl+Click từ Claude Code mà không cần tìm file trong thư mục Windows.
- Khi owner dùng tài khoản khác, liên kết Drive Ni N riêng tư có thể yêu cầu đăng nhập đúng tài khoản; KHÔNG mở quyền Anyone with link nếu chủ dự án chưa cho phép.
- Tài liệu nghiệm thu phải trỏ tới file link-index và nói rõ audio thật/mất mạng/thiết bị nào chưa kiểm được. Coi link-index là một phần bắt buộc của QA handoff, không chỉ là ưu đãi một lần.

## 9. BẮT BUỘC 19/10/2026 — Bàn giao từng APK test mới bằng link tải trực tiếp

**Quyết định chủ dự án:** Mỗi khi có một APK **test mới hợp lệ** (phân biệt bằng TASK_ID + Git commit/build hash, không phải mọi lần Gradle chạy lại), phải cung cấp file để chủ dự án dễ cài từ xa: **ưu tiên upload Google Drive Ni N riêng tư rồi Opus nghiệm thu gửi link bấm trực tiếp ngay trong hội thoại**. Không bắt chủ dự án đi tìm APK trong ổ Ni/Tiny. Bằng chứng ảnh/video QA vẫn dùng bộ link riêng.
- Sonnet sau khi build phải chuẩn bị thông tin bàn giao APK trong `docs/plans/<TASK_ID>-bao-cao.md`: loại build (Debug/Release QA), package, versionCode/versionName, Git HEAD và commit chứa source, SHA-256 của tệp, số byte, certificate SHA-256, đường dẫn file APK thực trên Ni/Tiny, trạng thái Telegram config (chỉ true/false, KHÔNG xuất giá trị), kết quả test và cài/QA, rollback/warnings. Đối chiếu APK với đúng commit; không đưa bản build cũ trá hình thành bản mới.
- **Opus nghiệm thu là người duy nhất phát hành link QA tới chủ dự án:** độc lập xác minh APK sha/bytes/cert/package/version, không downgrade/cài mismatch, không thiếu config dùng được; nếu cần kiểm thử đăng nhập Telegram thì APK được build với config phù hợp tại Tiny và không mang raw credentials về Ni. Chỉ khi đạt cổng **APK đủ điều kiện test** mới upload vào **Google Drive Ni N owner-only**, thư mục riêng theo TASK_ID/ngày/version/commit, tên APK duy nhất; không đổi quyền thành Anyone with link, không public GitHub/Telegram, không ghi đè/xóa APK cũ. Kiểm metadata Drive owner/share sau upload.
- Bản còn chờ chủ dự án xem/chốt ghi nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**, không gọi Beta phát hành/Stable/PASS chức năng chỉ vì build xanh. Khi được chủ dự án duyệt và Opus nghiệm thu PASS mới thay trạng thái nghiệm thu; giữ thông tin hạn chế (thiết bị chưa test, audio thực chưa xác nhận, v.v.).
- Opus nghiệm thu gửi trong chat **link Drive trực tiếp đến APK + link thư mục**, kèm versionCode/versionName, commit, SHA-256, thiết bị đã test và trạng thái; ghi cùng các URL được xác minh vào `docs/plans/<TASK_ID>-apk-test.md` để người dùng Ctrl+Click từ Claude Code. **Không bịa link từ đường dẫn Windows**.
- Nếu Drive không thể truy cập, permission không đúng hoặc APK không đạt gate: **BLOCKED — CHƯA BÀN GIAO**, báo lý do và lưu vị trí bản nội bộ; không tuyên bố upload thành công hoặc tự cấp quyền public. Không tự tăng versionCode, đổi signing keys hoặc cài FYT để giải quyết vướng mắc.
- Thực hiện điều này với **mỗi candidate test mới**; không cần chủ dự án nhắc lại sau từng lần Sonnet báo Xong. Chỉ Opus nghiệm thu sửa link-index và upload/công bố APK; Sonnet không upload hoặc tự công bố APK.

## 10. Tiết kiệm token quota — 25/10/2026

- **Nhiệm vụ lớn chia 2 phiên Sonnet:** phiên 1 code + test Ni + commit theo giai đoạn; phiên 2 build Tiny + QA thiết bị + báo cáo. Phiên QA không mang theo ngữ cảnh code. Nhiệm vụ nhỏ/làn nhanh: 1 phiên.
- **Ảnh tổng hợp:** Sonnet ghép 6–8 ảnh QA chính thành **1 file** `docs/plans/artifacts/<TASK_ID>/00_tong_hop.png` (lưới, cạnh dài ≤ 1600 px, ghi số bước trên từng ô). Opus nghiệm thu xem ảnh này trước, chỉ mở ảnh gốc khi nghi ngờ.
- **Báo cáo gọn:** báo cáo Sonnet ≤ ~250 dòng, bảng trước, không dán log dài; log đầy đủ để ở artifacts, chỉ trích dòng then chốt.
- **Opus nghiệm thu không đọc toàn bộ diff:** đọc báo cáo + `git log --oneline <base>..HEAD` + `git diff --stat`, rồi chỉ đọc kỹ file rủi ro mà kế hoạch nêu (TDLib/Media3/DB/cancel/account) và test mới.
- **Gradle chạy một lần, xuất ra file**, chỉ đọc dòng tổng kết (BUILD SUCCESSFUL/FAILED, số tests/skipped/failed từ XML `build/test-results`). Không in log Gradle đầy đủ vào hội thoại.
- **Không dùng review nhiều agent** (/code-review ultra, workflow nhiều agent) cho nghiệm thu thường lệ; một phiên Opus là đủ. Chỉ dùng khi chủ dự án yêu cầu.
- HANDOFF.md chỉ đọc mục cập nhật đầu; lịch sử chuyển dần sang docs/history/.
