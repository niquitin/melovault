# CLAUDE.md — MeloVault / chủ dự án nghiệm thu

**Hiệu lực: 29/10/2026. Kế hoạch và thực thi chi tiết bằng Markdown; kiểm tra sơ bộ ở phiên mới; chủ dự án nghiệm thu chính thức.** Codex được chủ dự án giao cập nhật quy trình. Trả lời bằng tiếng Việt, dễ hiểu; không tạo dự án mới. Chỉ thay đổi quy trình khi chủ dự án yêu cầu; agent không tự đổi quy tắc.

Đọc: CLAUDE.md → HANDOFF.md (**chỉ mục cập nhật đầu tiên**, phần dưới khi cần) → docs/plans/BACKLOG.md → docs/workflow/QUY_TRINH_3_VAI_TRO.md → `docs/plans/<TASK_ID>-ban-giao.md` → kế hoạch/báo cáo đúng TASK_ID. Git/source và kết quả test mới hơn luôn có ưu tiên so với handoff cũ.

## 0. Quyết định 29/10/2026 — ưu tiên hơn mọi mục bên dưới và mẫu cũ

**Luồng mặc định: lập kế hoạch → phiên mới thực thi/build APK bằng model tiết kiệm hơn → phiên mới nghiệm thu sơ bộ nhanh → chủ dự án nghiệm thu chính thức, chi tiết.**

**Codex và Claude Code có vai trò, quyền và trách nhiệm ngang nhau trong dự án.** Cả hai được sử dụng cùng repo/worktree và công cụ phát triển bình thường theo nhiệm vụ, vai đã được chủ dự án giao: đọc/sửa mã và tài liệu, thao tác Git an toàn, tạo nhánh nhiệm vụ khi cần, commit, test/build APK, QA và bàn giao. Agent này không cần agent kia phê duyệt; không coi một bên là cấp trên hoặc chỉ là dự phòng của bên kia. Quyền đã được chủ dự án giao tiếp tục có hiệu lực khi đổi Codex ↔ Claude Code trong cùng phạm vi; không xin lại chỉ vì đổi công cụ.

Repo, nhánh và hồ sơ là tài sản chung của dự án; việc một agent tạo chúng không dành quyền sử dụng riêng cho agent đó. Cả hai có thể tiếp nhận cùng công việc qua Markdown sau khi đối chiếu Git và agent cũ dừng. Nguyên tắc ngang quyền áp dụng cùng các giới hạn của vai, luật an toàn và chỉ một agent sửa code mỗi lúc; nghiệm thu chính thức, quyền merge/push/public vẫn theo quyết định của chủ dự án. Công cụ chưa kết nối/không khả dụng là giới hạn kỹ thuật cần báo đúng phần, không phải khác biệt quyền giữa hai agent.

| Giai đoạn | Người làm và đầu ra bắt buộc |
|---|---|
| Lập kế hoạch | Codex hoặc Claude do chủ dự án giao; viết chi tiết `docs/plans/<TASK_ID>.md` và khởi tạo `*-ban-giao.md`. Không tự chuyển sang code. |
| Thực thi | Chủ dự án mở phiên mới, chọn model có mức hao phí token/quota thấp hơn phù hợp kế hoạch, có thể là Codex hoặc Claude Code; code/test/build APK, cập nhật chi tiết `*-bao-cao.md`, `*-ban-giao.md`, hồ sơ APK và ảnh. |
| Nghiệm thu sơ bộ | Chủ dự án mở phiên mới, giao Codex hoặc Claude kiểm nhanh theo `PROMPT_NGHIEM_THU_SO_BO.md`, ghi `*-nghiem-thu-so-bo.md`; không thay nghiệm thu chính thức. |
| Nghiệm thu chính thức | Chủ dự án dùng thử/đánh giá chi tiết và quyết định ĐẠT hoặc yêu cầu sửa; agent ghi đúng quyết định vào `*-nghiem-thu.md`. |

- **Không khóa cứng model hoặc người lập kế hoạch theo nhỏ/lớn.** Chủ dự án chọn công cụ/model/effort cho từng phiên; agent không tự đổi vai, nâng model hoặc xếp loại. Quy định cũ bắt buộc Codex chỉ lập kế hoạch nhỏ, Opus lập kế hoạch lớn, Astra/Sol theo loại và Sonnet chỉ là dự phòng đã được thay thế. Không tự gộp kế hoạch với thực thi, kể cả làn nhanh; số phiên thực thi theo các chặng đã ghi trong kế hoạch.
- **File `.md` là nguồn bàn giao chính, không chỉ trả lời trong chat.** Kế hoạch phải đủ chi tiết cho model thực thi làm từng bước; báo cáo phải ghi việc đã làm, bằng chứng và phần dở dang. `*-ban-giao.md` cập nhật sau mỗi mốc nhỏ, trước khi kết thúc phiên/đổi model và ngay khi thấy sắp hết quota. Nội dung tối thiểu và mẫu tiếp tục ở `docs/workflow/QUY_TRINH_3_VAI_TRO.md`.
- **Codex ↔ Claude Code được tiếp tục cùng TASK_ID/nhánh/worktree khi chủ dự án giao**, kể cả hết quota giữa chặng. Không bắt đầu lại từ đầu, không phụ thuộc lịch sử chat. Phiên nhận đọc file bàn giao, kế hoạch và báo cáo, kiểm Git/source/log thực rồi tiếp tục bước chưa xong. Chỉ MỘT agent sửa code mỗi lúc; phiên cũ phải dừng trước khi phiên mới sửa. Nếu dừng đột ngột, khôi phục hiểu biết từ checkpoint cuối và bằng chứng thực, ghi rõ khoảng chưa xác minh; không tự coi công việc đã xong.
- **Cho phép chủ động dùng Sony đang kết nối trong cả lập kế hoạch, thực thi và nghiệm thu sơ bộ**, không cần hỏi lại cho thao tác an toàn phục vụ nhiệm vụ đã giao. Có thể khảo sát UI, tái hiện lỗi, kiểm chứng giả định, chụp bằng chứng và thử APK đã qua gate. Lập kế hoạch/kiểm sơ bộ không tự sửa code hoặc build mới; nếu cần bản mới thì ghi bước cho phiên thực thi. Vẫn kiểm máy/serial/model, dùng Tiny đúng quy định, không tranh thiết bị đang được sử dụng; giữ nguyên mọi luật cài APK, dữ liệu/bí mật và tắt màn trong `finally`. **Từ 31/10/2026, Sony là thiết bị QA thật duy nhất phía Ni/Tiny (xem mục 3) — không còn khái niệm "chứng cứ Shanling" để thay thế.**
- **Sơ bộ phải nhanh và có giới hạn:** kiểm báo cáo, diff/file rủi ro, nguồn APK và vài luồng chính; chỉ chạy lại test/QA khi có lý do cụ thể. Cần sửa code/build APK mới thì ghi việc cho phiên thực thi. Ghi `SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN`, `SƠ BỘ: CẦN SỬA` hoặc `SƠ BỘ: BLOCKED` kèm phạm vi; không ghi PASS chính thức. Có lỗi thì liệt kê để chủ dự án giao REWORK ở phiên thực thi mới.

- **Chủ dự án là người duy nhất quyết định nghiệm thu cuối:** ĐẠT hoặc yêu cầu sửa. Không bắt buộc phiên Claude Opus nghiệm thu và không yêu cầu chủ dự án chuyển sang Opus để bàn giao APK.
- **Agent thực thi chuẩn bị hồ sơ:** code/test/build/QA trong phạm vi đã giao, gửi APK thử nghiệm hợp lệ, ảnh/video và giới hạn cho phiên sơ bộ và chủ dự án. Kiểm tra kỹ thuật không thay quyết định nghiệm thu của chủ dự án.
- Codex và Claude Code đều được tạo/cập nhật `*-apk-test.md`, `*-hinh-nghiem-thu.md` và `*-nghiem-thu.md` để lưu bằng chứng, điểm chặn và quyết định của chủ dự án. Chỉ ghi PASS sau khi chủ dự án xác nhận ĐẠT cho đúng TASK_ID, commit/APK và phạm vi; ghi ngày, nguồn xác nhận và giới hạn còn lại. Yêu cầu sửa ghi REWORK; chưa duyệt ghi **CHỜ CHỦ DỰ ÁN NGHIỆM THU**, thiếu điều kiện kỹ thuật ghi BLOCKED. Không tự suy lời yêu cầu build/cài/tiếp tục thành ĐẠT.
- Codex và Claude Code đều được chuẩn bị và upload APK/ảnh QA vào Google Drive Ni N **owner-only** sau các gate ở mục 8–9, gửi link đã kiểm; bản chưa được duyệt ghi **QA CANDIDATE — CHƯA NGHIỆM THU**. Nếu Drive chặn tải, báo đúng lỗi và vị trí nội bộ; không coi link đó là tải được, không né cảnh báo hay tự public.
- Chủ dự án nghiệm thu không tự động cho phép merge, push hoặc phát hành công khai. Merge chỉ sau ĐẠT và chỉ thị merge riêng; push/public cần chỉ thị riêng. Không tự chọn/mở nhiệm vụ kế.
- Không sửa lại kết luận lịch sử hoặc coi yêu cầu đổi quy tắc này là nghiệm thu E105. Các mốc nghiệm thu cũ giữ nguyên; quy trình mới áp dụng từ quyết định này.
- **Cách đọc tài liệu/prompt cũ:** yêu cầu "Opus duy nhất nghiệm thu/bàn giao/ghi hồ sơ", "Codex không upload/không viết hồ sơ", cấm dùng Sony khi lập kế hoạch và gộp kế hoạch/code trong làn nhanh hết hiệu lực. "Sonnet" trong chỉ dẫn kỹ thuật cũ đọc là agent thực thi được giao (Codex hoặc Claude Code). Giữ luật an toàn Git/ADB/bí mật/dữ liệu/chữ ký, thiết bị, chất lượng bằng chứng và phạm vi kế hoạch. Các cấm sửa quy trình không ngăn yêu cầu sửa quy trình trực tiếp của chủ dự án.
- Kết thúc thực thi: bàn giao prompt cho **phiên nghiệm thu sơ bộ mới**, không tự chạy vai đó trong cùng phiên. Kết thúc sơ bộ: **"Đã dừng, chờ chủ dự án nghiệm thu."** Kèm cách phản hồi `ĐẠT <TASK_ID> — <commit/APK>` hoặc `Yêu cầu sửa <TASK_ID>: <lỗi>`. Chỉ đổi quy trình thì báo thay đổi/commit, không yêu cầu nghiệm thu APK ngoài phạm vi.

Chi tiết và bộ file: `docs/workflow/QUY_TRINH_3_VAI_TRO.md`. Mẫu Codex: `PROMPT_CODEX.md`; Claude lập kế hoạch: `PROMPT_OPUS.md`; Claude thực thi/tiếp tục: `PROMPT_SONNET.md` (giữ tên để tương thích, model do chủ dự án chọn); kiểm nhanh: `PROMPT_NGHIEM_THU_SO_BO.md`; nghiệm thu chính thức: `PROMPT_CHU_DU_AN_NGHIEM_THU.md`.

Codex và Claude Code đều được phép dùng ADB, Desktop Commander và các công cụ sẵn có phục vụ nhiệm vụ (Ni + Tiny DESKTOP-HNP4GVR); cấu hình công cụ không thay luật repo. **Danh sách cấm trong AGENTS.md và các mục 1, 3, 4, 7 vẫn bắt buộc.**

## 1. Repo duy nhất và khóa phiên

- **Ni PC, workspace duy nhất:** D:\Bots\MeloVault-E91-Shareable. Mỗi lúc tối đa MỘT agent sửa code. Mỗi nhiệm vụ một nhánh độc lập **dot/<TASK_ID>**. Không sửa nhánh tích hợp chính; không merge/cherry-pick sang nhánh chính khi chưa có chủ dự án nghiệm thu ĐẠT và chỉ thị merge riêng.
- Tiny PC (DESKTOP-HNP4GVR) chỉ là nơi build/QA Sony trong clone cô lập. Chuyển code qua Git bundle đã kiểm SHA256, KHÔNG chép đè repo Tiny hoặc chỉnh qua ổ T:.
- CẤM tác động: Private Stable C:\Bots\MusicAssistantCar, backend C:\Bots\MusicAssistant, TelegramSecretary C:\Bots\TelegramSecretary, Preview Beta85 gốc C:\Bots\MeloVault-E91-Shareable trên Tiny, session/cache TDLib.
- Bắt đầu MỌI phiên: chạy git status --short; git branch --show-current; git rev-parse HEAD; git log -5 --oneline; git diff --check; kiểm tra agent/build đang hoạt động (commit mới trong vài phút gần đây trên nhánh = có agent đang chạy). Cây bẩn không thuộc phiên, HEAD lạ, branch lạ, agent khác đang code → DỪNG, không reset/clean/stash/checkout đè. Việc chỉ sửa tài liệu quy trình khi có agent khác đang chạy thì làm trong git worktree riêng.
- Tiếp nhận cùng nhiệm vụ: chỉ tiếp tục phần chưa commit khi chủ dự án đã giao tiếp, agent cũ đã dừng và toàn bộ thay đổi khớp danh sách/bằng chứng trong `*-ban-giao.md`. Đây là phần việc được bàn giao, không phải cây bẩn không rõ nguồn. Có file/HEAD/branch lạ hoặc chưa rõ quyền sở hữu → DỪNG và báo, không tự nhận/commit phần việc của phiên khác.
- REWORK sửa chính nhánh dot/<TASK_ID> qua một hội thoại thực thi MỚI, công cụ/model do chủ dự án chọn; KHÔNG restart dự án.

## 2. Hồ sơ cho từng vai

**A. LẬP KẾ HOẠCH — Codex hoặc Claude:** viết `docs/plans/<TASK_ID>.md` chi tiết và `*-ban-giao.md`: mục tiêu, baseline, phương án/lý do, file/hàm, từng bước nhỏ và phụ thuộc, test, build APK, QA thiết bị, tiêu chí, rủi ro/rollback, trường hợp phải dừng. Ghi 3–6 thao tác để chủ dự án thử (thiết bị, màn hình, nhấn gì, kỳ vọng). Được khảo sát Sony theo mục 0; không code/build mới. Commit tài liệu và bàn giao phiên thực thi mới với model tiết kiệm hơn do chủ dự án chọn.

**B. THỰC THI — model được chủ dự án chọn ở phiên mới:** đọc kế hoạch đã chốt và bàn giao, xác thực nhánh/baseline, code/test/build Debug và Release, QA an toàn đúng phạm vi. Cập nhật `*-bao-cao.md` chi tiết theo bước và `*-ban-giao.md` ngay trong quá trình làm; không đợi cuối phiên. Bàn giao APK/ảnh theo mục 8–9, commit rồi dừng và đưa prompt cho phiên sơ bộ mới. Thiếu kế hoạch hoặc cần đổi phạm vi/kiến trúc → ghi BLOCKED, không tự đoán.

**C. NGHIỆM THU SƠ BỘ — Codex hoặc Claude ở phiên mới:** kiểm nhanh theo `PROMPT_NGHIEM_THU_SO_BO.md`; đọc kế hoạch/báo cáo, kiểm diff và APK đúng nguồn, dùng Sony nếu hữu ích. Ghi phạm vi, kết quả, lỗi có vị trí/bước tái hiện và giới hạn vào `*-nghiem-thu-so-bo.md`; cập nhật bàn giao. Không sửa code, không kiểm toàn diện lại khi không có lý do, không tự ghi PASS chính thức.

**D. CHỦ DỰ ÁN NGHIỆM THU CHÍNH THỨC, CHI TIẾT:** dùng APK hoặc duyệt bằng chứng theo `PROMPT_CHU_DU_AN_NGHIEM_THU.md`. Agent ghi quyết định vào `*-nghiem-thu.md`: chưa duyệt = CHỜ CHỦ DỰ ÁN NGHIỆM THU; thiếu điều kiện kỹ thuật = BLOCKED; chỉ PASS khi chủ dự án xác nhận ĐẠT đúng TASK_ID/commit/APK/phạm vi; yêu cầu sửa = REWORK. Không đổi tiêu chí/kết luận lịch sử. Merge chỉ sau ĐẠT và chỉ thị riêng; không tự push/public/chọn việc tiếp.

**Hồ sơ canonical cùng TASK_ID:** kế hoạch, báo cáo thực thi, bàn giao, kiểm sơ bộ, nghiệm thu chính thức, cùng `*-apk-test.md` / `*-hinh-nghiem-thu.md` khi có APK/ảnh. `D:\Bots\_handoff` chỉ là bản sao. Nội dung đủ để agent khác tiếp tục mà không cần lịch sử chat; không đưa bí mật/PII vào Markdown. Tác vụ chỉ sửa tài liệu không cần build/cài/nghiệm thu APK; vẫn có kế hoạch, báo cáo và bàn giao.

## 3. Thiết bị thử nghiệm & an toàn ADB

**CẬP NHẬT 31/10/2026 — Shanling M3 Ultra ĐÃ BÁN, không còn là thiết bị QA.** Chủ dự án xác nhận trực tiếp trong chat. Ni **không còn thiết bị Android thật nào** để tự cài/QA; mọi QA thiết bị thật trên dự án giờ chỉ còn qua **Sony Xperia 5 II trên Tiny**. Các dòng nhắc tới Shanling/serial `30313860180939` bên dưới và ở các mục khác (0, 6, 7) là **lịch sử**, giữ lại để biết bối cảnh các nghiệm thu cũ, không phải hướng dẫn còn hiệu lực — không tự adb vào serial đó, không đoán có máy thay thế trừ khi chủ dự án nói lại.

- **Tiny PC → Sony Xperia 5 II (model XQ-AS52) — thiết bị QA thật duy nhất:** ADB serial **QV725WEQ54**; xác nhận lại 18–31/10/2026, Sony Android 12, model XQ-AS52. Xác minh lại mỗi phiên bằng `adb devices -l` + `getprop`, không mặc định cáp/serial không đổi.
- Mỗi lần QA: chạy adb devices -l **trên đúng máy (Tiny)** và adb -s <serial> shell getprop ro.product.model + ro.product.manufacturer. Nếu báo không thiết bị/unauthorized → DỪNG phần cài/QA, báo BLOCKED; có thể hoàn tất unit test nhưng KHÔNG tuyên bố đã thử trên thiết bị thật.
- **Agent tự cài DEBUG/Release APK lên Sony** nếu đã verify package, versionCode không downgrade, chữ ký khớp app đang có, và APK có Telegram config hợp lệ nếu thử Telegram. **Chỉ** lệnh adb -s <serial> install -r "<APK_VALIDATED>". Build Ni thiếu Telegram ID/hash hợp lệ có thể PASS Gradle nhưng không dùng được: **KHÔNG CÀI** để test đăng nhập/phát Telegram; dùng APK có config xây dựng an toàn tại Tiny, không sao chép secret sang Ni.
- **Signature mismatch, downgrade, sai package, APK thiếu config, ADB unauthorized:** DỪNG, báo chủ dự án. Tuyệt đối cấm adb uninstall, adb shell pm clear, xoá dữ liệu app, logout Telegram, xoá cache/TDLib, dùng -d để ép cài.
- Sau khi chạy được: mở đúng các màn hình kế hoạch, thao tác, chụp PNG bằng adb -s <serial> exec-out screencap -p (Windows dùng cmd /c với redirect nhị phân). Lưu ảnh dưới docs/plans/artifacts/<TASK_ID>/, tham chiếu file trong báo cáo và gửi bằng chứng cho chủ dự án nghiệm thu. Không commit/upload ảnh lộ nội dung riêng tư; nếu ảnh không thu được ghi BLOCKED.
- Sony qua Tiny được chủ động dùng trong lập kế hoạch, thực thi và sơ bộ theo mục 0; ghi phạm vi thao tác vào hồ sơ. Y700/S25/FYT vẫn phân biệt kiểm thử chủ dự án với test Sony. Không tự cài FYT.

**Lịch sử (trước 31/10, không còn áp dụng):** Ni từng có Shanling M3 Ultra, ADB serial `30313860180939`, xác minh lần đầu 18/10/2026 (Android 10, cài MeloVault vc107/0.6.86-beta tại thời điểm đó). Máy đã bán; không còn dùng được.

## 4. Quy tắc code/bảo mật đang áp dụng

- Shareable Android app.melovault.player, TDLib user account, SQLite với key (accountId,chatId,messageId), Media3/MediaSession; Personal/backend là phạm vi khác. Android Views/AppCompat, không tự chuyển sang Compose. Không đổi songKey, signer, package, schema/owner/session nếu chưa có phê duyệt.
- Không tự xoá bản trùng tên; không hash/xóa cache/Telegram index. DB/TDLib scan trên thread nền với cancel theo lifecycle/account/generation; không hardcode số bài, artwork, bitrate.
- Không đọc/in/log/commit/copy local.properties, secrets.properties, OTP, keystore, credentials, dữ liệu TDLib noBackup/session/cache hay API hash. Không git reset --hard, clean, stash, force push hoặc restore đè. Không ghi đè APK Beta cũ.
- Trên Ni chạy:
    .\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
    .\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
    git diff --check
  Phân biệt tests pass và skipped. JVM tests và Gradle BUILD SUCCESSFUL không chứng minh ADB/TDLib/UI thật.
- Chuỗi gate: kế hoạch `.md` → phiên mới Ni code/test/commit → bundle SHA256 → Tiny clone cô lập build với Telegram config bí mật chỉ ở Tiny + verify package/version/cert → QA thiết bị → candidate APK owner-only Drive sau kiểm kỹ thuật → phiên mới sơ bộ nhanh → chủ dự án nghiệm thu chi tiết, xác nhận ĐẠT → agent ghi PASS đúng phạm vi → có chỉ thị merge riêng mới merge nhánh dot. Không publish hoặc tự cài FYT. Private upload chỉ cho APK đạt gate phù hợp, không cho phát hành artifact thiếu config.

## 5. Lời kết và bàn giao

Mọi phiên agent nói tiếng Việt thường, gồm 3 phần ngắn: **Đã làm** (kèm bằng chứng/commit), **Chưa kiểm chứng**, **Rủi ro hoặc điểm dừng**.
- Lập kế hoạch: dẫn file kế hoạch/bàn giao và prompt thực thi; ghi **"Đã dừng. Mở phiên mới, chọn model thực thi tiết kiệm hơn theo lựa chọn của chủ dự án."**
- Thực thi chưa hết chặng: dẫn checkpoint và bước tiếp, ghi **"Đã dừng, bàn giao phiên thực thi tiếp theo."** Hết chặng cuối: đưa prompt sơ bộ và ghi **"Đã dừng. Mở phiên mới để nghiệm thu sơ bộ nhanh."**
- Sơ bộ: dẫn `*-nghiem-thu-so-bo.md`, APK/ảnh và giới hạn; ghi **"Đã dừng, chờ chủ dự án nghiệm thu."**, kèm `ĐẠT <TASK_ID> — <commit/APK>` hoặc `Yêu cầu sửa <TASK_ID>: <lỗi>`.
- Đổi công cụ/hết quota: luôn dẫn `*-ban-giao.md`, TASK_ID, worktree/nhánh, HEAD và bước chưa xong; chủ dự án có thể giao Claude Code tiếp ngay khi phiên cũ dừng. Không tự mở phiên hoặc nhắn agent khác.
- Agent bàn giao: gửi ảnh/APK/bằng chứng đã kiểm, kết quả kỹ thuật và giới hạn; chủ dự án quyết định ĐẠT/yêu cầu sửa. REWORK ghi đúng lỗi được giao, dùng PROMPT_CODEX.md mục C. Phiên chỉ đổi quy trình báo thay đổi/commit, không yêu cầu nghiệm thu APK.
- Ngoài các prompt trên, **KHÔNG** tự chọn hoặc triển khai nhiệm vụ mới.

## 6. QUY TẮC MỚI 19/10/2026 — Agent tự kiểm thử, chủ dự án nghiệm thu từ xa

**Ưu tiên hơn yêu cầu bác sĩ trực tiếp thao tác trong các plan cũ; áp dụng cùng mục 0 mới.** Chủ dự án thường điều khiển Ni/Tiny từ xa. Agent lập kế hoạch viết 3–6 bước rõ ràng và được khảo sát Sony; agent thực thi PHẢI TỰ THỰC HIỆN các bước ADB trên thiết bị thật nếu an toàn, kiểm tra kỹ thuật và GỬI ẢNH/VIDEO chứng cứ trong hội thoại bằng công cụ sẵn có để chủ dự án duyệt từ xa. Phiên sơ bộ kiểm nhanh có chọn lọc. Không buộc chủ dự án đến tận nơi cầm Sony.
- **Thiết bị ADB (cập nhật 31/10/2026 — xem mục 3):** Tiny → Sony Xperia 5 II XQ-AS52 Android 12, serial QV725WEQ54 — thiết bị QA thật duy nhất. Ni không còn thiết bị Android thật (Shanling M3 Ultra, serial 30313860180939, xác minh lần đầu 19/10/2026, đã bán). Kiểm lại adb devices -l và getprop mỗi phiên.
- Trình tự Sonnet: xác nhận model/serial, APK package/versionCode/cert/Telegram config, MediaSession không đang phát nếu sắp cài → CHỈ adb install -r khi mọi gate hợp lệ → mở app và thao tác 3–6 bước qua adb shell input tap/swipe/keyevent (tọa độ dựa trên ảnh màn hình thực) → chụp PNG trước/sau từng thao tác quan trọng → lấy chứng cứ Media3/queue/timeline/logcat. Không tap tùy tiện, không reset, uninstall, pm clear, xóa data/cache/TDLib, đổi account, force downgrade, cài FYT hoặc bật airplane/tắt mạng khi plan cấm.
- Ảnh private tại docs/plans/artifacts/<TASK_ID>/; tên thứ tự kèm thiết bị và mốc thời gian. Dùng adb -s SERIAL exec-out screencap -p và ghi PNG nhị phân bằng cmd.exe /c trên Windows, kiểm header PNG và xem được hình. Khi ảnh tĩnh không đủ chứng minh thao tác, có thể quay MP4 qua adb screenrecord rồi pull nếu an toàn. Không ghi đè chứng cứ cũ, không commit/đăng công khai ảnh/video/log chứa Telegram PII.
- Báo cáo Sonnet có bảng 3–6 bước: thiết bị/serial, hành động, kỳ vọng, thực tế, PASS/FAIL/BLOCKED, file ảnh trước/sau hoặc video, log/timestamp, và **một ảnh tổng hợp** (mục 10). Agent phải kiểm ảnh thật trên Ni/Tiny, chạy test/build lại khi phù hợp, rồi GỬI ảnh/video quan trọng trong chat để chủ dự án xem và nói ĐẠT hoặc báo lỗi.
- Media3 PLAYING, tiến trình thời lượng tăng và không crash chỉ chứng minh luồng playback; KHÔNG chứng minh loa/tai nghe thực sự phát tiếng. Đánh dấu CHƯA XÁC NHẬN ÂM THANH THỰC khi không có nguồn âm thanh độc lập; không tự tuyên bố test nghe đã PASS.
- Kịch bản không được phép tự động (ví dụ bật chế độ máy bay, xóa file giả lập lỗi, thay tài khoản) phải ghi CHƯA KIỂM CHỨNG/BLOCKED và xin phép riêng nếu thật sự cần, không phá luật an toàn để có ảnh.
- PASS cuối cùng yêu cầu chủ dự án CHẤP THUẬN đúng bản/phạm vi qua dùng thử hoặc chứng cứ từ xa đã được agent kiểm tra kỹ thuật; các giới hạn chưa xác minh phải nêu rõ, có thể tách gate riêng khi quan trọng. Không merge trước khi có phê duyệt.
- Kế hoạch offline-one-tap-play commit 29c54f9 được owner duyệt GIỮ NGUYÊN ngày 19/10; không bắt Opus sửa lại. Rủi ro được nêu trong biên bản audit cũ vẫn là điểm cần QA, không được tự sửa plan.

## 7. Chính sách màn hình QA — hiệu lực 19/10/2026 (cập nhật 31/10/2026: chỉ còn Sony)

**Sony trên Tiny phải tắt màn hình khi không dùng để thao tác/chụp QA.** (Trước 31/10 áp dụng cả M3 Ultra trên Ni; máy đó đã bán, không còn.) CHỈ bật sáng trước bước cần màn ON; sau khi test xong hoặc gặp lỗi phải tắt màn và xác minh qua `dumpsys display` có `Display State=OFF`. Khi chạy Gradle, đọc báo cáo, log hoặc chờ chủ dự án duyệt thì màn hình phải OFF. Không tắt khi agent/người dùng khác đang chạm hoặc quay video. Không tắt nguồn, không sửa khóa màn hình/bảo mật, không đổi timeout/AOD/brightness hay xóa dữ liệu.
- Bật bằng `adb -s SERIAL shell input keyevent 224` (KEYCODE_WAKEUP) **sau khi kiểm trạng thái**; tắt bằng `adb -s SERIAL shell input keyevent 223` (KEYCODE_SLEEP, không phải nút toggle). Kiểm `dumpsys power` và `dumpsys display` trước/sau. Màn Sony có thể `mWakefulness=Dozing` nhưng `Display State=OFF`, như vậy màn đã tắt.
- Phải thực hiện thao tác tắt màn trong bước cleanup `finally`, kể cả test FAIL/BLOCKED, và ghi `SONY_SCREEN_END` trong báo cáo (`SHANLING_SCREEN_END` không còn áp dụng — máy đã bán; chỉ ghi lại nếu một báo cáo cũ vẫn tham chiếu nó).
- Đọc chi tiết `docs/workflow/SCREEN_LIFECYCLE_QA.md` (tài liệu đó vẫn còn nhắc Shanling — chưa cập nhật trong nhiệm vụ này, đọc với lưu ý trên). Agent chỉ chỉnh quy trình này khi chủ dự án yêu cầu. Không tự đánh thức Sony khi không cần QA.

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

- **Tách phiên theo vai và các chặng trong kế hoạch:** thực thi có thể chia code + test Ni / build Tiny + QA để tiết kiệm ngữ cảnh; model do chủ dự án chọn. Luôn có phiên sơ bộ riêng sau khi thực thi xong. Không gộp kế hoạch và code để tiết kiệm quota.
- **Ảnh tổng hợp:** Sonnet ghép 6–8 ảnh QA chính thành **1 file** `docs/plans/artifacts/<TASK_ID>/00_tong_hop.png` (lưới, cạnh dài ≤ 1600 px, ghi số bước trên từng ô). Agent kiểm và gửi chủ dự án ảnh này trước, chỉ mở ảnh gốc khi nghi ngờ.
- **Chi tiết nhưng tránh lặp:** báo cáo thực thi ≤ ~250 dòng, bảng trước, đủ từng bước/kết quả/lỗi/việc còn lại; nếu cần dài hơn, tách phụ lục `.md` có liên kết. Log đầy đủ ở artifacts, chỉ trích dòng then chốt. Không rút kế hoạch hoặc checkpoint thành vài dòng thiếu thông tin để model khác tiếp tục.
- **Kiểm tra kỹ thuật có chọn lọc:** đọc báo cáo + `git log --oneline <base>..HEAD` + `git diff --stat`, rồi chỉ đọc kỹ file rủi ro mà kế hoạch nêu (TDLib/Media3/DB/cancel/account) và test mới.
- **Gradle chạy một lần, xuất ra file**, chỉ đọc dòng tổng kết (BUILD SUCCESSFUL/FAILED, số tests/skipped/failed từ XML `build/test-results`). Không in log Gradle đầy đủ vào hội thoại.
- **Không dùng review nhiều agent** (/code-review ultra, workflow nhiều agent) cho bàn giao thường lệ; agent thực thi chuẩn bị để chủ dự án nghiệm thu. Chỉ dùng khi chủ dự án yêu cầu.
- HANDOFF.md chỉ đọc mục cập nhật đầu; lịch sử chuyển dần sang docs/history/.
