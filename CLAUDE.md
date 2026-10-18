# CLAUDE.md — MeloVault / quy trình AI 3 vai trò

**Hiệu lực: 18/10/2026.** Trả lời bằng tiếng Việt, dễ hiểu. Không tạo dự án mới. **CHỈ ChatGPT** chỉnh CLAUDE.md, docs/workflow/*, tài liệu nghiệm thu và quyết định quy trình. Claude không tự chỉnh các file này, không tự viết prompt phiên sau.

Đọc: CLAUDE.md → HANDOFF.md → docs/plans/BACKLOG.md → docs/workflow/QUY_TRINH_3_VAI_TRO.md → kế hoạch đúng TASK_ID. Git/source và kết quả test mới hơn luôn có ưu tiên so với handoff cũ.

## 1. Repo duy nhất và khóa phiên

- **Ni PC, workspace duy nhất:** D:\Bots\MeloVault-E91-Shareable. Mỗi lúc tối đa MỘT agent sửa code. Mỗi nhiệm vụ một nhánh độc lập **dot/<TASK_ID>**. Không sửa nhánh tích hợp chính; không merge/cherry-pick sang nhánh chính khi ChatGPT chưa báo PASS và chủ dự án chưa xác nhận thao tác kiểm tra.
- Tiny PC (DESKTOP-HNP4GVR) chỉ là nơi build/QA Sony trong clone cô lập. Chuyển code qua Git bundle đã kiểm SHA256, KHÔNG chép đè repo Tiny hoặc chỉnh qua ổ T:.
- CẤM tác động: Private Stable C:\Bots\MusicAssistantCar, backend C:\Bots\MusicAssistant, TelegramSecretary C:\Bots\TelegramSecretary, Preview Beta85 gốc C:\Bots\MeloVault-E91-Shareable trên Tiny, session/cache TDLib.
- Bắt đầu MỌI phiên: chạy git status --short; git branch --show-current; git rev-parse HEAD; git log -5 --oneline; git diff --check; kiểm tra agent/build đang hoạt động. Cây bẩn không thuộc phiên, HEAD lạ, branch lạ, agent khác đang code → DỪNG, không reset/clean/stash/checkout đè.
- REWORK sửa chính nhánh dot/<TASK_ID> nhưng qua một hội thoại Sonnet MỚI; KHÔNG restart dự án.

## 2. Ba vai trò — ba hội thoại độc lập

**A. Claude Opus / LẬP KẾ HOẠCH** (hội thoại mới): chỉ được đọc mã và tạo docs/plans/<TASK_ID>.md trên nhánh dot/<TASK_ID>, chỉ commit tài liệu kế hoạch. Plan cần mục tiêu, Git baseline, phương án và lý do, file/sửa đổi chính xác, rủi ro và rollback, từng bước nhỏ cho Sonnet, test, QA thiết bị, tiêu chí PASS/FAIL và phần **"Bác sĩ tự kiểm tra" gồm 3–6 thao tác** ghi thiết bị, màn hình, nhấn gì, phải thấy gì. Không sửa code/build/cài. Báo ngắn và kết thúc đúng dòng: **"Giờ mở phiên mới, chọn Sonnet."** Không viết prompt phiên kế.

**B. Claude Sonnet / THỰC THI** (hội thoại mới, không Resume phiên Opus): đọc kế hoạch đã duyệt, xác thực nhánh dot/<TASK_ID> và Git baseline, sửa code nhỏ đúng phạm vi, viết tests, chạy Debug/Release và build; nếu có QA UI, tự kiểm Shanling qua ADB an toàn; báo cáo canonical docs/plans/<TASK_ID>-bao-cao.md, ảnh local docs/plans/artifacts/<TASK_ID>/ (không commit ảnh nhạy cảm). Commit và DỪNG chờ ChatGPT. Không sửa docs/workflow, CLAUDE.md, file nghiệm thu hoặc tự mở nhiệm vụ khác.

**C. ChatGPT / NGHIỆM THU ĐỘC LẬP:** duy nhất viết docs/plans/<TASK_ID>-nghiem-thu.md, xem Git/diff, tự chạy test và build trên Ni, tự xem ảnh; xác định đúng 1 trạng thái: **PASS / REWORK / BLOCKED**. Nếu chưa có chủ dự án xác nhận mục "Bác sĩ tự kiểm tra", dù test xanh vẫn **BLOCKED — CHỜ CHỦ DỰ ÁN**. Chỉ ChatGPT đề xuất việc kế kèm lý do; chủ dự án CHỐT rồi ChatGPT mới đưa prompt cho Claude. Chủ dự án chỉ cần nói **"Xong"** để ChatGPT kiểm toán, không cần dán report.

**Ba file canonical đứng cạnh nhau:** docs/plans/<TASK_ID>.md (Opus), docs/plans/<TASK_ID>-bao-cao.md (Sonnet), docs/plans/<TASK_ID>-nghiem-thu.md (ChatGPT). D:\Bots\_handoff chỉ là bản sao. Prompt mẫu có trong docs/workflow/PROMPT_OPUS.md và docs/workflow/PROMPT_SONNET.md; Claude KHÔNG sửa mẫu.

**Làn nhanh:** Chỉ khi ChatGPT đề xuất và chủ dự án chốt. Điều kiện đồng thời: 1 màn hình, tối đa 2 file production và tối đa 80 dòng code production thay đổi (không kể test), không đổi DB/schema/Telegram/TDLib/account/cache/Media3/queue/kiến trúc/security/permission/signing/build config, không di trú dữ liệu. Sonnet trực tiếp tạo kế hoạch ngắn docs/plans/<TASK_ID>.md và thực thi trên dot/<TASK_ID>, vẫn báo cáo/QA/ChatGPT nghiệm thu. Nhiệm vụ sai một điều kiện thì dùng Opus trước.

## 3. Thiết bị thử nghiệm & an toàn ADB

- **Ni PC → Shanling M3 Ultra (thiết bị chính):** ngày 18/10 Windows PnP phát hiện tên Shanling M3 Ultra, USB instance USB\VID_18D1&PID_4EE1\30313860180939. Nhưng **adb devices -l CHƯA thấy thiết bị**. **ADB serial 30313860180939**, trạng thái device đã được ChatGPT xác minh 18/10/2026 lúc 23:30 (+07), adb getprop xác nhận Shanling M3 Ultra, Android 10. Mã Windows USB KHÔNG PHẢI serial ADB; trước mỗi lần cài vẫn phải xác minh lại adb devices -l và getprop. Thiết bị đang cài MeloVault vc107/0.6.86-beta tại thời điểm kiểm tra, CHƯA cài Beta109.
- **Tiny PC → Sony Xperia 5 II (model XQ-AS52):** ADB serial **QV725WEQ54**; 18/10/2026 adb devices báo trạng thái device, Sony Android 12, model XQ-AS52. Xác minh lại mỗi phiên, không mặc định cáp/serial không đổi.
- Mỗi lần QA: chạy adb devices -l **trên đúng máy** và adb -s <serial> shell getprop ro.product.model + ro.product.manufacturer. Nếu Ni báo không thiết bị/unauthorized → DỪNG phần cài/QA, báo BLOCKED; có thể hoàn tất unit test nhưng KHÔNG tuyên bố đã thử Shanling.
- **Sonnet tự cài DEBUG APK lên Shanling** nếu đã verify package, versionCode không downgrade, chữ ký khớp app đang có, và APK có Telegram config hợp lệ nếu thử Telegram. **Chỉ** lệnh adb -s <serial> install -r "<APK_VALIDATED>". Ni build thiếu Telegram ID/hash hợp lệ có thể PASS Gradle nhưng không dùng được: **KHÔNG CÀI** để test đăng nhập/phát Telegram; dùng APK có config xây dựng an toàn tại Tiny, không sao chép secret sang Ni.
- **Signature mismatch, downgrade, sai package, APK thiếu config, ADB unauthorized:** DỪNG, báo chủ dự án. Tuyệt đối cấm adb uninstall, adb shell pm clear, xoá dữ liệu app, logout Telegram, xoá cache/TDLib, dùng -d để ép cài.
- Sau khi chạy được: mở đúng các màn hình kế hoạch, thao tác, chụp PNG bằng adb -s <serial> exec-out screencap -p (Windows dùng cmd /c với redirect nhị phân). Lưu ảnh dưới docs/plans/artifacts/<TASK_ID>/, tham chiếu file trong báo cáo và cho ChatGPT xem. Không commit/upload ảnh lộ nội dung riêng tư; nếu ảnh không thu được ghi BLOCKED.
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
- Chuỗi gate: Ni code/test/commit → bundle SHA256 → Tiny clone cô lập build với Telegram config bí mật chỉ ở Tiny + verify package/version/cert → QA Sony nếu yêu cầu → candidate APK owner-only Drive khi đã kiểm toán kỹ thuật để chủ dự án thử → chủ dự án báo thao tác đạt → ChatGPT PASS → chỉ khi đó merge nhánh dot. Không publish hoặc tự cài FYT. Private upload đã được cho phép với APK đạt kiểm toán thích hợp, không cho phép phát hành artifact thiếu config.

## 5. Lời kết bắt buộc của Claude

Mọi phiên Claude nói tiếng Việt thường, gồm 3 phần ngắn: **Đã làm** (kèm bằng chứng/commit), **Chưa kiểm chứng**, **Rủi ro hoặc điểm dừng**. Opus thêm đúng một dòng **"Giờ mở phiên mới, chọn Sonnet."**; Sonnet thêm **"Đã dừng, chờ ChatGPT nghiệm thu."**. **KHÔNG** đưa prompt công việc tiếp theo, không tự chọn hoặc triển khai bước mới.
