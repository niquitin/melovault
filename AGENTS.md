# AGENTS.md — luật cho Codex trong repo MeloVault

Hiệu lực 26/10/2026. **CLAUDE.md là luật gốc; đọc CLAUDE.md (mục 0 trước) trước mọi việc.** Khi file này và CLAUDE.md lệch nhau, theo CLAUDE.md. Trả lời chủ dự án bằng tiếng Việt dễ hiểu. Chỉ Claude Opus sửa file này khi chủ dự án yêu cầu.

## Vai của Codex

- Codex chỉ làm vai được giao trong prompt (mẫu `docs/workflow/PROMPT_CODEX.md`): **lập kế hoạch nhiệm vụ nhỏ** (gpt-6-astra medium) hoặc **thực thi** (nhỏ: gpt-6.1-sol xhigh; lớn: gpt-6-astra medium). Không tự đổi vai, không tự xếp loại nhỏ/lớn.
- Claude Opus nghiệm thu. Codex không viết file nghiệm thu, không ghi PASS, không merge, không upload/công bố APK, không chọn hay mở nhiệm vụ kế.
- Không sửa: CLAUDE.md, AGENTS.md, docs/workflow/*, `docs/plans/*-nghiem-thu.md`, `*-apk-test.md`, `*-hinh-nghiem-thu.md`.

## Đầu phiên (bắt buộc)

Chạy: `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, `git log -5 --oneline`, `git diff --check`; xem có commit trong vài phút gần đây (agent khác đang chạy). Cây bẩn không thuộc phiên, HEAD/branch lạ, baseline không khớp, agent khác đang code → **DỪNG và báo**, không sửa gì. Chỉ làm trên nhánh `dot/<TASK_ID>`; không sửa nhánh tích hợp.

## Danh sách CẤM tuyệt đối (cấu hình Codex không tự chặn — tự tuân thủ)

- Git: `reset --hard`, `clean`, `stash`, `checkout`/`restore` đè file, `push --force`, rebase/sửa lịch sử, merge/cherry-pick vào nhánh tích hợp, push lên remote khi chưa được yêu cầu.
- ADB: `adb uninstall`, `adb shell pm clear`, `install -d` (downgrade), xóa data/cache/TDLib, logout/đổi tài khoản Telegram, bật airplane/tắt mạng, đổi timeout/brightness/AOD/khóa màn hình, `input keyevent 26`. Cài APK **chỉ** `adb -s <serial> install -r "<APK đã kiểm>"` sau khi kiểm package, versionCode không giảm, cert khớp, Telegram config=true (chỉ boolean).
- Bí mật: không đọc/in/log/commit/copy `local.properties`, `secrets.properties`, keystore/`*.jks`, OTP, credentials, API hash, dữ liệu TDLib session/cache/noBackup. Không mang Telegram config từ Tiny về Ni.
- Máy/thư mục cấm chạm: `C:\Bots\MusicAssistantCar`, `C:\Bots\MusicAssistant`, `C:\Bots\TelegramSecretary`, repo gốc `C:\Bots\MeloVault-E91-Shareable` trên Tiny. Không chép đè hay sửa repo Tiny qua ổ `T:`. Không cài FYT.
- Code: không đổi package, signer/signing config, songKey, schema/owner/session, versionCode ngoài kế hoạch; không chuyển sang Compose; không xóa bản trùng tên hay cache/Telegram index.
- Không ghi đè/xóa APK, ảnh hay artifact cũ. Không public ảnh/log/video có Telegram PII.

## Tiny (DESKTOP-HNP4GVR) qua Desktop Commander

- Chỉ build/QA Sony trong **clone cô lập** mới trên Tiny, nhận code bằng **Git bundle** đã đối chiếu SHA-256 hai đầu. Telegram config chỉ nằm ở Tiny.
- `start_process` từ xa chỉ chờ ~3 giây: chạy Gradle dài bằng `Start-Process ... -WindowStyle Hidden`, ghi log + file exit code dưới `C:\Bots\_qa_temp\`, rồi đọc kết quả sau (có thể đọc qua `T:` từ Ni, chỉ đọc).
- Sony serial `QV725WEQ54` trên Tiny; Shanling M3 Ultra serial `30313860180939` trên Ni. Mỗi phiên kiểm lại `adb devices -l` và `getprop ro.product.model` trên đúng máy. Không dùng Sony thay chứng cứ Shanling.

## Màn hình QA

Theo CLAUDE.md mục 7 và `docs/workflow/SCREEN_LIFECYCLE_QA.md`: màn mặc định OFF; bật `keyevent 224` chỉ trước bước cần màn; tắt `keyevent 223` trong `finally` (kể cả FAIL/BLOCKED) và xác minh `dumpsys display` có `Display State=OFF`; ghi `SHANLING_SCREEN_END` / `SONY_SCREEN_END` trong báo cáo.

## Test, build, báo cáo

- Trên Ni: `.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain` và `.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain`, xuất log ra file, chỉ ghi dòng tổng kết; tách tests pass/skipped/failed. Gradle xanh không chứng minh ADB/TDLib/UI thật; Media3 PLAYING không chứng minh có âm thanh thật.
- Báo cáo `docs/plans/<TASK_ID>-bao-cao.md` (≤ ~250 dòng) theo PROMPT_SONNET.md, dòng đầu `Thực thi: Codex <model> <effort>, phiên <...>`. Ảnh private ở `docs/plans/artifacts/<TASK_ID>/` (không commit), có `00_tong_hop.png`.
- Commit trên `dot/<TASK_ID>` rồi **DỪNG**. Lời kết: Đã làm / Chưa kiểm chứng / Rủi ro, và dòng cuối theo PROMPT_CODEX.md.
- Gặp tình huống không có trong kế hoạch, signature mismatch, downgrade, ADB unauthorized, APK thiếu config → **DỪNG, ghi BLOCKED**, không đoán, không phá luật để có kết quả.
