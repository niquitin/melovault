# CLAUDE.md — MeloVault / chủ dự án nghiệm thu

**Hiệu lực: 28/10/2026. Chủ dự án trực tiếp NGHIỆM THU.** Quyết định mới: "giờ tôi là người nghiệm thu, hãy thay đổi quy tắc". Codex được chủ dự án giao cập nhật quy trình trong phiên này. Trả lời bằng tiếng Việt, dễ hiểu; không tạo dự án mới. Chỉ thay đổi quy trình khi chủ dự án yêu cầu; agent không tự đổi quy tắc.

Đọc: CLAUDE.md → HANDOFF.md (**chỉ mục cập nhật đầu tiên**, phần dưới khi cần) → docs/plans/BACKLOG.md → docs/workflow/QUY_TRINH_3_VAI_TRO.md → kế hoạch đúng TASK_ID. Git/source và kết quả test mới hơn luôn có ưu tiên so với handoff cũ.

## 0. Phân vai và nghiệm thu — 28/10/2026 (ưu tiên hơn mọi mục bên dưới và mẫu cũ)

- **Chủ dự án là người duy nhất quyết định nghiệm thu cuối:** ĐẠT hoặc yêu cầu sửa. Không bắt buộc phiên Claude Opus nghiệm thu và không yêu cầu chủ dự án chuyển sang Opus để bàn giao APK.
- **Codex thực thi và chuẩn bị hồ sơ:** code/test/build/QA trong phạm vi đã giao, kiểm tra kỹ thuật, gửi APK thử nghiệm hợp lệ, ảnh/video và giới hạn để chủ dự án trực tiếp dùng thử hoặc duyệt từ xa. Kiểm tra kỹ thuật không thay quyết định nghiệm thu của chủ dự án.
- Codex được tạo/cập nhật `*-apk-test.md`, `*-hinh-nghiem-thu.md` và `*-nghiem-thu.md` để lưu bằng chứng, điểm chặn và quyết định của chủ dự án. Chỉ ghi PASS sau khi chủ dự án xác nhận ĐẠT cho đúng TASK_ID, commit/APK và phạm vi; ghi ngày, nguồn xác nhận và giới hạn còn lại. Yêu cầu sửa ghi REWORK; chưa duyệt ghi **CHỜ CHỦ DỰ ÁN NGHIỆM THU**, thiếu điều kiện kỹ thuật ghi BLOCKED. Không tự suy lời yêu cầu build/cài/tiếp tục thành ĐẠT.
- Codex được chuẩn bị và upload APK/ảnh QA vào Google Drive Ni N **owner-only** sau các gate ở mục 8–9, gửi link đã kiểm; bản chưa được duyệt ghi **QA CANDIDATE — CHƯA NGHIỆM THU**. Nếu Drive chặn tải, báo đúng lỗi và vị trí nội bộ; không coi link đó là tải được, không né cảnh báo hay tự public.
- Chủ dự án nghiệm thu không tự động cho phép merge, push hoặc phát hành công khai. Merge chỉ sau ĐẠT và chỉ thị merge riêng; push/public cần chỉ thị riêng. Không tự chọn/mở nhiệm vụ kế.
- Không sửa lại kết luận lịch sử hoặc coi yêu cầu đổi quy tắc này là nghiệm thu E105. Các mốc nghiệm thu cũ giữ nguyên; quy trình mới áp dụng từ quyết định này.
- **Cách đọc tài liệu/prompt cũ:** các yêu cầu "Opus duy nhất nghiệm thu/bàn giao/ghi hồ sơ", "Codex không upload/không viết hồ sơ" và "chờ Opus nghiệm thu" hết hiệu lực. Công việc kiểm tra/bàn giao/ghi nhận do agent thực thi thực hiện; quyết định ĐẠT do chủ dự án. Các cấm sửa quy trình không ngăn yêu cầu sửa quy trình trực tiếp của chủ dự án. Giữ nguyên các luật an toàn Git/ADB/bí mật/dữ liệu/chữ ký, thiết bị, chất lượng bằng chứng và phạm vi kế hoạch.
- Kết thúc phiên thực thi cuối: **"Đã dừng, chờ chủ dự án nghiệm thu."** Kèm cách phản hồi `ĐẠT <TASK_ID> — <commit/APK>` hoặc `Yêu cầu sửa <TASK_ID>: <lỗi>`. Khi chỉ đổi quy trình, báo thay đổi/commit, không yêu cầu nghiệm thu APK không thuộc công việc đó.

Chi tiết bàn giao và ghi nhận: `docs/workflow/PROMPT_CHU_DU_AN_NGHIEM_THU.md`. Phân công lập kế hoạch/model dưới đây giữ nguyên trừ khi chủ dự án chỉ định khác.

**Phân công kế thừa từ 26/10/2026:** Codex nhận vai THỰC THI thay Claude Sonnet và LẬP KẾ HOẠCH nhiệm vụ nhỏ; Claude Opus lập kế hoạch nhiệm vụ lớn khi được giao. "Sonnet" trong hướng dẫn thực thi cũ đọc là "Codex thực thi". Codex đọc luật qua `AGENTS.md` (trỏ về file này).

| Loại nhiệm vụ | Lập kế hoạch | Thực thi (code + test + build + QA) | Nghiệm thu |
|---|---|---|---|
| **Nhỏ** | Codex `gpt-6-astra`, effort **medium** (phiên Codex mới) | Codex `gpt-6.1-sol`, effort **xhigh** (phiên Codex mới, GỘP 1 phiên) | Chủ dự án |
| **Lớn** | Claude Opus (phiên mới) | Codex `gpt-6-astra`, effort **medium** (phiên 1 = code + test Ni; phiên 2 = build Tiny + QA) | Chủ dự án |

- **Nhỏ** = đạt **đồng thời** mọi điều kiện của Làn nhanh (mục 2): 1 màn hình, ≤ 2 file production, ≤ 80 dòng production, không DB/schema/Telegram/TDLib/account/cache/Media3/queue/kiến trúc/security/permission/signing/build config, không di trú dữ liệu. Sai một điều kiện = **Lớn**. Agent có thể đề xuất loại, chủ dự án CHỐT; Codex không tự xếp loại hay tự nâng/hạ loại.
- Codex chạy trong **phiên/hội thoại Codex mới** cho mỗi vai (Codex app, hoặc `codex exec -m <model> -c model_reasoning_effort="<effort>" -C D:\Bots\MeloVault-E91-Shareable`), prompt điền từ `docs/workflow/PROMPT_CODEX.md`. Không Resume phiên Codex cũ cho vai khác. REWORK: phiên Codex MỚI, cùng model thực thi của nhiệm vụ, trên chính dot/<TASK_ID>.
- Codex được chủ dự án cấp ADB và Desktop Commander (Ni + Tiny DESKTOP-HNP4GVR). Cấu hình Codex không chặn lệnh, nên **danh sách cấm trong AGENTS.md và các mục 1, 3, 4, 7 là ranh giới bắt buộc**; vi phạm = REWORK/BLOCKED dù kết quả đúng.
- Codex chỉ sửa CLAUDE.md, AGENTS.md, docs/workflow/* khi chủ dự án yêu cầu. Hồ sơ/bàn giao APK và ghi nhận nghiệm thu tuân mục 0 mới; không tự nghiệm thu, merge, public hay chọn nhiệm vụ kế.
- **Dự phòng:** Codex hết hạn mức/lỗi công cụ → dừng, giữ nguyên nhánh; chủ dự án có thể giao Claude Sonnet tiếp theo PROMPT_SONNET.md. Báo cáo ghi rõ phần nào do Codex, phần nào do Sonnet.
- Vẫn **chỉ MỘT agent sửa code** mỗi lúc (Codex hoặc Claude), kể cả khi Claude đang chờ hồi quota.

## 1. Repo duy nhất và khóa phiên

- **Ni PC, workspace duy nhất:** D:\Bots\MeloVault-E91-Shareable. Mỗi lúc tối đa MỘT agent sửa code. Mỗi nhiệm vụ một nhánh độc lập **dot/<TASK_ID>**. Không sửa nhánh tích hợp chính; không merge/cherry-pick sang nhánh chính khi chưa có chủ dự án nghiệm thu ĐẠT và chỉ thị merge riêng.
- Tiny PC (DESKTOP-HNP4GVR) chỉ là nơi build/QA Sony trong clone cô lập. Chuyển code qua Git bundle đã kiểm SHA256, KHÔNG chép đè repo Tiny hoặc chỉnh qua ổ T:.
- CẤM tác động: Private Stable C:\Bots\MusicAssistantCar, backend C:\Bots\MusicAssistant, TelegramSecretary C:\Bots\TelegramSecretary, Preview Beta85 gốc C:\Bots\MeloVault-E91-Shareable trên Tiny, session/cache TDLib.
- Bắt đầu MỌI phiên: chạy git status --short; git branch --show-current; git rev-parse HEAD; git log -5 --oneline; git diff --check; kiểm tra agent/build đang hoạt động (commit mới trong vài phút gần đây trên nhánh = có agent đang chạy). Cây bẩn không thuộc phiên, HEAD lạ, branch lạ, agent khác đang code → DỪNG, không reset/clean/stash/checkout đè. Việc chỉ sửa tài liệu quy trình khi có agent khác đang chạy thì làm trong git worktree riêng.
- REWORK sửa chính nhánh dot/<TASK_ID> nhưng qua một hội thoại Sonnet MỚI; KHÔNG restart dự án.

## 2. Lập kế hoạch, thực thi và chủ dự án nghiệm thu

**A. Claude Opus / LẬP KẾ HOẠCH** (hội thoại mới): chỉ được đọc mã và tạo docs/plans/<TASK_ID>.md trên nhánh dot/<TASK_ID>, chỉ commit tài liệu kế hoạch. Plan cần mục tiêu, Git baseline, phương án và lý do, file/sửa đổi chính xác, rủi ro và rollback, từng bước nhỏ cho Sonnet (ghi rõ chia **phiên Sonnet 1 = code + test Ni**, **phiên Sonnet 2 = build Tiny + QA thiết bị + báo cáo** khi nhiệm vụ lớn), test, QA thiết bị, tiêu chí PASS/FAIL và phần **"Bác sĩ tự kiểm tra" gồm 3–6 thao tác** ghi thiết bị, màn hình, nhấn gì, phải thấy gì. Không sửa code/build/cài. Cuối phiên đưa **prompt Sonnet** điền từ docs/workflow/PROMPT_SONNET.md (chỉ thay TASK_ID, baseline, phiên 1/2 và ≤ 8 dòng lưu ý riêng), rồi dòng **"Giờ mở phiên mới, chọn Sonnet."**

**B. Claude Sonnet / THỰC THI** (hội thoại mới, không Resume phiên Opus): đọc kế hoạch đã duyệt, xác thực nhánh dot/<TASK_ID> và Git baseline, sửa code nhỏ đúng phạm vi, viết tests, chạy Debug/Release và build; nếu có QA UI, tự kiểm Shanling qua ADB an toàn; báo cáo canonical docs/plans/<TASK_ID>-bao-cao.md, ảnh local docs/plans/artifacts/<TASK_ID>/ (không commit ảnh nhạy cảm). Chuẩn bị hồ sơ/APK theo mục 0, commit rồi DỪNG chờ chủ dự án nghiệm thu. Chỉ sửa quy trình khi chủ dự án yêu cầu; không tự mở nhiệm vụ khác.

**C. CHỦ DỰ ÁN / NGHIỆM THU:** chủ dự án dùng APK hoặc duyệt bằng chứng theo `docs/workflow/PROMPT_CHU_DU_AN_NGHIEM_THU.md`. Agent ghi hồ sơ kỹ thuật và giới hạn; chưa duyệt ghi CHỜ CHỦ DỰ ÁN NGHIỆM THU, thiếu điều kiện kỹ thuật ghi BLOCKED. Chỉ ghi PASS khi chủ dự án xác nhận ĐẠT đúng TASK_ID/commit/APK/phạm vi; yêu cầu sửa ghi REWORK. Không đổi tiêu chí để đạt. Merge --no-ff chỉ sau ĐẠT và chỉ thị merge riêng; không tự push hoặc chọn việc tiếp.

**Ba file canonical đứng cạnh nhau:** docs/plans/<TASK_ID>.md (kế hoạch đã chốt), docs/plans/<TASK_ID>-bao-cao.md (thực thi), docs/plans/<TASK_ID>-nghiem-thu.md (agent lưu bằng chứng và quyết định chủ dự án). D:\Bots\_handoff chỉ là bản sao. Mẫu bàn giao: PROMPT_CHU_DU_AN_NGHIEM_THU.md; mẫu lập kế hoạch/thực thi: PROMPT_OPUS.md, PROMPT_CODEX.md và PROMPT_SONNET.md dự phòng. Agent chỉ sửa mẫu/quy trình khi chủ dự án yêu cầu.

**Làn nhanh:** Chỉ khi agent đề xuất (hoặc chủ dự án chỉ định) và chủ dự án chốt. Điều kiện đồng thời: 1 màn hình, tối đa 2 file production và tối đa 80 dòng code production thay đổi (không kể test), không đổi DB/schema/Telegram/TDLib/account/cache/Media3/queue/kiến trúc/security/permission/signing/build config, không di trú dữ liệu. Sonnet trực tiếp tạo kế hoạch ngắn docs/plans/<TASK_ID>.md và thực thi trên dot/<TASK_ID>, vẫn báo cáo/QA/chủ dự án nghiệm thu. Nhiệm vụ sai một điều kiện thì dùng Opus lập kế hoạch trước.

## 3. Thiết bị thử nghiệm & an toàn ADB

- **Ni PC → Shanling M3 Ultra (thiết bị chính):** ngày 18/10 Windows PnP phát hiện tên Shanling M3 Ultra, USB instance USB\VID_18D1&PID_4EE1\30313860180939. Nhưng **adb devices -l CHƯA thấy thiết bị**. **ADB serial 30313860180939**, trạng thái device đã được ChatGPT xác minh 18/10/2026 lúc 23:30 (+07), adb getprop xác nhận Shanling M3 Ultra, Android 10. Mã Windows USB KHÔNG PHẢI serial ADB; trước mỗi lần cài vẫn phải xác minh lại adb devices -l và getprop. Thiết bị đang cài MeloVault vc107/0.6.86-beta tại thời điểm kiểm tra, CHƯA cài Beta109.
- **Tiny PC → Sony Xperia 5 II (model XQ-AS52):** ADB serial **QV725WEQ54**; 18/10/2026 adb devices báo trạng thái device, Sony Android 12, model XQ-AS52. Xác minh lại mỗi phiên, không mặc định cáp/serial không đổi.
- Mỗi lần QA: chạy adb devices -l **trên đúng máy** và adb -s <serial> shell getprop ro.product.model + ro.product.manufacturer. Nếu Ni báo không thiết bị/unauthorized → DỪNG phần cài/QA, báo BLOCKED; có thể hoàn tất unit test nhưng KHÔNG tuyên bố đã thử Shanling.
- **Sonnet tự cài DEBUG APK lên Shanling** nếu đã verify package, versionCode không downgrade, chữ ký khớp app đang có, và APK có Telegram config hợp lệ nếu thử Telegram. **Chỉ** lệnh adb -s <serial> install -r "<APK_VALIDATED>". Ni build thiếu Telegram ID/hash hợp lệ có thể PASS Gradle nhưng không dùng được: **KHÔNG CÀI** để test đăng nhập/phát Telegram; dùng APK có config xây dựng an toàn tại Tiny, không sao chép secret sang Ni.
- **Signature mismatch, downgrade, sai package, APK thiếu config, ADB unauthorized:** DỪNG, báo chủ dự án. Tuyệt đối cấm adb uninstall, adb shell pm clear, xoá dữ liệu app, logout Telegram, xoá cache/TDLib, dùng -d để ép cài.
- Sau khi chạy được: mở đúng các màn hình kế hoạch, thao tác, chụp PNG bằng adb -s <serial> exec-out screencap -p (Windows dùng cmd /c với redirect nhị phân). Lưu ảnh dưới docs/plans/artifacts/<TASK_ID>/, tham chiếu file trong báo cáo và gửi bằng chứng cho chủ dự án nghiệm thu. Không commit/upload ảnh lộ nội dung riêng tư; nếu ảnh không thu được ghi BLOCKED.
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
- Chuỗi gate: Ni code/test/commit → bundle SHA256 → Tiny clone cô lập build với Telegram config bí mật chỉ ở Tiny + verify package/version/cert → QA Sony nếu yêu cầu → candidate APK owner-only Drive khi đã kiểm toán kỹ thuật để chủ dự án thử → chủ dự án xác nhận ĐẠT → agent ghi PASS đúng phạm vi → có chỉ thị merge riêng mới merge nhánh dot. Không publish hoặc tự cài FYT. Private upload đã được cho phép với APK đạt kiểm toán thích hợp, không cho phép phát hành artifact thiếu config.

## 5. Lời kết và bàn giao

Mọi phiên agent nói tiếng Việt thường, gồm 3 phần ngắn: **Đã làm** (kèm bằng chứng/commit), **Chưa kiểm chứng**, **Rủi ro hoặc điểm dừng**.
- Opus lập kế hoạch (nhiệm vụ lớn, mục 0): thêm prompt Codex thực thi phiên 1 điền từ docs/workflow/PROMPT_CODEX.md và đúng dòng **"Giờ mở phiên Codex mới, chọn gpt-6-astra medium."** (Chỉ khi chủ dự án chọn dự phòng Sonnet mới dùng prompt Sonnet và dòng "Giờ mở phiên mới, chọn Sonnet.")
- Codex lập kế hoạch/thực thi: lời kết theo PROMPT_CODEX.md. Phiên thực thi cuối kết bằng **"Đã dừng, chờ chủ dự án nghiệm thu."**, kèm cách phản hồi `ĐẠT <TASK_ID> — <commit/APK>` hoặc `Yêu cầu sửa <TASK_ID>: <lỗi>`.
- Sonnet dự phòng phiên 1: thêm **"Đã dừng. Mở phiên Sonnet mới cho phiên 2 (build + QA)."** Phiên cuối: **"Đã dừng, chờ chủ dự án nghiệm thu."** và cách phản hồi ĐẠT/yêu cầu sửa đúng bản.
- Agent bàn giao: gửi ảnh/APK/bằng chứng đã kiểm, kết quả kỹ thuật và giới hạn; chủ dự án quyết định ĐẠT/yêu cầu sửa. REWORK ghi đúng lỗi được giao, dùng PROMPT_CODEX.md mục C. Phiên chỉ đổi quy trình báo thay đổi/commit, không yêu cầu nghiệm thu APK.
- Ngoài các prompt trên, **KHÔNG** tự chọn hoặc triển khai nhiệm vụ mới.

## 6. QUY TẮC MỚI 19/10/2026 — Agent tự kiểm thử, chủ dự án nghiệm thu từ xa

**Ưu tiên hơn yêu cầu bác sĩ trực tiếp thao tác trong các plan cũ.** Chủ dự án thường điều khiển Ni/Tiny từ xa. Opus chỉ viết 3–6 bước kiểm thử rõ ràng; SONNET PHẢI TỰ THỰC HIỆN các bước qua ADB trên thiết bị thật nếu an toàn; AGENT THỰC THI kiểm tra kỹ thuật và GỬI CÁC ẢNH/VIDEO chứng cứ ngay trong hội thoại (SendUserFile) để bác sĩ duyệt từ xa. Không buộc bác sĩ đến tận nơi cầm Shanling/Sony.
- **Thiết bị ADB đã xác minh 19/10:** Ni → Shanling M3 Ultra Android 10, serial 30313860180939; Tiny → Sony Xperia 5 II XQ-AS52 Android 12, serial QV725WEQ54. Kiểm lại adb devices -l và getprop mỗi phiên. Không dùng kiểm thử Sony thay cho Shanling.
- Trình tự Sonnet: xác nhận model/serial, APK package/versionCode/cert/Telegram config, MediaSession không đang phát nếu sắp cài → CHỈ adb install -r khi mọi gate hợp lệ → mở app và thao tác 3–6 bước qua adb shell input tap/swipe/keyevent (tọa độ dựa trên ảnh màn hình thực) → chụp PNG trước/sau từng thao tác quan trọng → lấy chứng cứ Media3/queue/timeline/logcat. Không tap tùy tiện, không reset, uninstall, pm clear, xóa data/cache/TDLib, đổi account, force downgrade, cài FYT hoặc bật airplane/tắt mạng khi plan cấm.
- Ảnh private tại docs/plans/artifacts/<TASK_ID>/; tên thứ tự kèm thiết bị và mốc thời gian. Dùng adb -s SERIAL exec-out screencap -p và ghi PNG nhị phân bằng cmd.exe /c trên Windows, kiểm header PNG và xem được hình. Khi ảnh tĩnh không đủ chứng minh thao tác, có thể quay MP4 qua adb screenrecord rồi pull nếu an toàn. Không ghi đè chứng cứ cũ, không commit/đăng công khai ảnh/video/log chứa Telegram PII.
- Báo cáo Sonnet có bảng 3–6 bước: thiết bị/serial, hành động, kỳ vọng, thực tế, PASS/FAIL/BLOCKED, file ảnh trước/sau hoặc video, log/timestamp, và **một ảnh tổng hợp** (mục 10). Agent phải kiểm ảnh thật trên Ni/Tiny, chạy test/build lại khi phù hợp, rồi GỬI ảnh/video quan trọng trong chat để chủ dự án xem và nói ĐẠT hoặc báo lỗi.
- Media3 PLAYING, tiến trình thời lượng tăng và không crash chỉ chứng minh luồng playback; KHÔNG chứng minh loa/tai nghe thực sự phát tiếng. Đánh dấu CHƯA XÁC NHẬN ÂM THANH THỰC khi không có nguồn âm thanh độc lập; không tự tuyên bố test nghe đã PASS.
- Kịch bản không được phép tự động (ví dụ bật chế độ máy bay, xóa file giả lập lỗi, thay tài khoản) phải ghi CHƯA KIỂM CHỨNG/BLOCKED và xin phép riêng nếu thật sự cần, không phá luật an toàn để có ảnh.
- PASS cuối cùng yêu cầu chủ dự án CHẤP THUẬN đúng bản/phạm vi qua dùng thử hoặc chứng cứ từ xa đã được agent kiểm tra kỹ thuật; các giới hạn chưa xác minh phải nêu rõ, có thể tách gate riêng khi quan trọng. Không merge trước khi có phê duyệt.
- Kế hoạch offline-one-tap-play commit 29c54f9 được owner duyệt GIỮ NGUYÊN ngày 19/10; không bắt Opus sửa lại. Rủi ro được nêu trong biên bản audit cũ vẫn là điểm cần QA, không được tự sửa plan.

## 7. Chính sách màn hình QA — hiệu lực 19/10/2026

**M3 Ultra trên Ni và Sony trên Tiny phải tắt màn hình khi không dùng để thao tác/chụp QA.** CHỈ bật sáng trước bước cần màn ON; sau khi test xong hoặc gặp lỗi phải tắt màn và xác minh qua `dumpsys display` có `Display State=OFF`. Khi chạy Gradle, đọc báo cáo, log hoặc chờ chủ dự án duyệt thì màn hình phải OFF. Không tắt khi agent/người dùng khác đang chạm hoặc quay video. Không tắt nguồn, không sửa khóa màn hình/bảo mật, không đổi timeout/AOD/brightness hay xóa dữ liệu.
- Bật bằng `adb -s SERIAL shell input keyevent 224` (KEYCODE_WAKEUP) **sau khi kiểm trạng thái**; tắt bằng `adb -s SERIAL shell input keyevent 223` (KEYCODE_SLEEP, không phải nút toggle). Kiểm `dumpsys power` và `dumpsys display` trước/sau. Màn Sony có thể `mWakefulness=Dozing` nhưng `Display State=OFF`, như vậy màn đã tắt.
- Phải thực hiện thao tác tắt màn trong bước cleanup `finally`, kể cả test FAIL/BLOCKED, và ghi `SHANLING_SCREEN_END` / `SONY_SCREEN_END` trong báo cáo. Không để màn sáng trong suốt 5–10 phút build/test.
- Đọc chi tiết `docs/workflow/SCREEN_LIFECYCLE_QA.md`. Agent chỉ chỉnh quy trình này khi chủ dự án yêu cầu. Không tự đánh thức Shanling/Sony khi không cần QA.

## 8. Chuẩn giao nộp ảnh QA bằng liên kết một chạm — 19/10/2026

- Sonnet vẫn chụp PNG/video riêng tư tại docs/plans/artifacts/<TASK_ID>/, ghi số thứ tự và tên rõ mục đích. Không tự public ảnh chứa metadata Telegram, không thay đổi permission Drive.
- **Agent thực thi chuẩn hóa ảnh bàn giao sau QA**: đưa 5–10 ảnh quan trọng vào thư mục **Google Drive Ni N ở chế độ owner-only**, kiểm tra SHA-256 kích thước khi sao chép và xác minh Drive shared=false bằng connector. Nếu đồng bộ không xong, ghi rõ giới hạn, không tự tạo link file giả.
- Agent lập **docs/plans/<TASK_ID>-hinh-nghiem-thu.md** trong repo với liên kết Markdown bấm trực tiếp tới folder và từng ảnh đã được Drive xác minh, để người dùng Ctrl+Click từ Claude Code mà không cần tìm file trong thư mục Windows.
- Khi owner dùng tài khoản khác, liên kết Drive Ni N riêng tư có thể yêu cầu đăng nhập đúng tài khoản; KHÔNG mở quyền Anyone with link nếu chủ dự án chưa cho phép.
- Tài liệu nghiệm thu phải trỏ tới file link-index và nói rõ audio thật/mất mạng/thiết bị nào chưa kiểm được. Coi link-index là một phần bắt buộc của QA handoff, không chỉ là ưu đãi một lần.

## 9. BẮT BUỘC 19/10/2026 — Bàn giao từng APK test mới bằng link tải trực tiếp

**Quyết định chủ dự án:** Mỗi khi có một APK **test mới hợp lệ** (phân biệt bằng TASK_ID + Git commit/build hash, không phải mọi lần Gradle chạy lại), phải cung cấp file để chủ dự án dễ cài từ xa: **ưu tiên agent upload Google Drive Ni N riêng tư rồi gửi link bấm trực tiếp ngay trong hội thoại**. Không bắt chủ dự án đi tìm APK trong ổ Ni/Tiny. Bằng chứng ảnh/video QA vẫn dùng bộ link riêng.
- Sonnet sau khi build phải chuẩn bị thông tin bàn giao APK trong `docs/plans/<TASK_ID>-bao-cao.md`: loại build (Debug/Release QA), package, versionCode/versionName, Git HEAD và commit chứa source, SHA-256 của tệp, số byte, certificate SHA-256, đường dẫn file APK thực trên Ni/Tiny, trạng thái Telegram config (chỉ true/false, KHÔNG xuất giá trị), kết quả test và cài/QA, rollback/warnings. Đối chiếu APK với đúng commit; không đưa bản build cũ trá hình thành bản mới.
- **Agent thực thi bàn giao link QA tới chủ dự án:** kiểm tra trực tiếp APK sha/bytes/cert/package/version, không downgrade/cài mismatch, không thiếu config dùng được; nếu cần kiểm thử đăng nhập Telegram thì APK được build với config phù hợp tại Tiny và không mang raw credentials về Ni. Chỉ khi đạt cổng **APK đủ điều kiện test** mới upload vào **Google Drive Ni N owner-only**, thư mục riêng theo TASK_ID/ngày/version/commit, tên APK duy nhất; không đổi quyền thành Anyone with link, không public GitHub/Telegram, không ghi đè/xóa APK cũ. Kiểm metadata Drive owner/share sau upload.
- Bản còn chờ chủ dự án xem/chốt ghi nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**, không gọi Beta phát hành/Stable/PASS chức năng chỉ vì build xanh. Khi được chủ dự án xác nhận ĐẠT đúng bản/phạm vi mới ghi PASS vào hồ sơ nghiệm thu; giữ thông tin hạn chế (thiết bị chưa test, audio thực chưa xác nhận, v.v.).
- Agent gửi trong chat **link Drive trực tiếp đến APK + link thư mục**, kèm versionCode/versionName, commit, SHA-256, thiết bị đã test và trạng thái; ghi cùng các URL được xác minh vào `docs/plans/<TASK_ID>-apk-test.md` để người dùng Ctrl+Click từ Claude Code. **Không bịa link từ đường dẫn Windows**.
- Nếu Drive không thể truy cập, permission không đúng hoặc APK không đạt gate: **BLOCKED — CHƯA BÀN GIAO**, báo lý do và lưu vị trí bản nội bộ; không tuyên bố upload thành công hoặc tự cấp quyền public. Không tự tăng versionCode, đổi signing keys hoặc cài FYT để giải quyết vướng mắc.
- Thực hiện điều này với **mỗi candidate test mới**; không cần chủ dự án nhắc lại sau từng lần Sonnet báo Xong. Agent cập nhật link-index và upload APK QA owner-only sau các gate. Không tự phát hành công khai; chủ dự án quyết định nghiệm thu.

## 10. Tiết kiệm token quota — 25/10/2026

- **Nhiệm vụ lớn chia 2 phiên Sonnet:** phiên 1 code + test Ni + commit theo giai đoạn; phiên 2 build Tiny + QA thiết bị + báo cáo. Phiên QA không mang theo ngữ cảnh code. Nhiệm vụ nhỏ/làn nhanh: 1 phiên.
- **Ảnh tổng hợp:** Sonnet ghép 6–8 ảnh QA chính thành **1 file** `docs/plans/artifacts/<TASK_ID>/00_tong_hop.png` (lưới, cạnh dài ≤ 1600 px, ghi số bước trên từng ô). Agent kiểm và gửi chủ dự án ảnh này trước, chỉ mở ảnh gốc khi nghi ngờ.
- **Báo cáo gọn:** báo cáo Sonnet ≤ ~250 dòng, bảng trước, không dán log dài; log đầy đủ để ở artifacts, chỉ trích dòng then chốt.
- **Kiểm tra kỹ thuật có chọn lọc:** đọc báo cáo + `git log --oneline <base>..HEAD` + `git diff --stat`, rồi chỉ đọc kỹ file rủi ro mà kế hoạch nêu (TDLib/Media3/DB/cancel/account) và test mới.
- **Gradle chạy một lần, xuất ra file**, chỉ đọc dòng tổng kết (BUILD SUCCESSFUL/FAILED, số tests/skipped/failed từ XML `build/test-results`). Không in log Gradle đầy đủ vào hội thoại.
- **Không dùng review nhiều agent** (/code-review ultra, workflow nhiều agent) cho bàn giao thường lệ; agent thực thi chuẩn bị để chủ dự án nghiệm thu. Chỉ dùng khi chủ dự án yêu cầu.
- HANDOFF.md chỉ đọc mục cập nhật đầu; lịch sử chuyển dần sang docs/history/.
