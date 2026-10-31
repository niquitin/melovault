# AGENTS.md — luật cho Codex trong repo MeloVault

Hiệu lực 29/10/2026. **CLAUDE.md là luật gốc; đọc CLAUDE.md (mục 0 trước) trước mọi việc.** Khi file này và CLAUDE.md lệch nhau, theo CLAUDE.md. Trả lời chủ dự án bằng tiếng Việt dễ hiểu. Agent chỉ sửa quy trình/file này khi chủ dự án yêu cầu.

## Vai của Codex

- **Codex và Claude Code ngang quyền, ngang trách nhiệm.** Cả hai được dùng chung repo/worktree, đọc/sửa mã và tài liệu, thao tác Git an toàn, commit, test/build/QA và bàn giao theo vai/phạm vi đã được chủ dự án giao. Không cần agent kia phê duyệt hoặc xin lại quyền chỉ vì đổi công cụ. Repo/nhánh/hồ sơ không thuộc riêng agent nào; cùng tuân luật an toàn, bàn giao Markdown và một agent sửa code mỗi lúc. Nghiệm thu chính thức và quyền merge/push/public vẫn thuộc chủ dự án theo luật gốc.
- Codex hoặc Claude được chủ dự án giao **lập kế hoạch**, **thực thi** hoặc **nghiệm thu sơ bộ nhanh**; mỗi vai một phiên mới. Không tự đổi vai, model/effort hay xếp loại. Chủ dự án chọn model hao phí token/quota thấp hơn cho phiên thực thi; không khóa model theo nhỏ/lớn.
- **Kế hoạch và thực thi bắt buộc ghi chi tiết bằng `.md`.** Dùng `docs/plans/<TASK_ID>.md`, `*-bao-cao.md`, `*-ban-giao.md` và `*-nghiem-thu-so-bo.md` theo `docs/workflow/QUY_TRINH_3_VAI_TRO.md`. Cập nhật checkpoint sau mỗi mốc, trước khi đổi phiên/model hoặc sắp hết quota; không chỉ lưu trong chat.
- **Codex ↔ Claude Code tiếp tục cùng việc:** khi chủ dự án giao tiếp, đọc bàn giao/kế hoạch/báo cáo, kiểm Git thực và làm từ bước chưa xong; không làm lại từ đầu, không chạy hai agent sửa code cùng lúc. Chỉ nhận thay đổi chưa commit có nguồn gốc/bằng chứng khớp bàn giao; phần lạ thì dừng.
- **Nghiệm thu sơ bộ ở phiên mới**, ghi phạm vi và kết quả sơ bộ, không tự sửa code hoặc ghi PASS chính thức. **Chủ dự án nghiệm thu chính thức, chi tiết** và quyết định ĐẠT/yêu cầu sửa.
- Codex được cập nhật `docs/plans/*-nghiem-thu.md`, `*-apk-test.md`, `*-hinh-nghiem-thu.md`; chỉ ghi PASS khi chủ dự án xác nhận ĐẠT đúng bản/phạm vi. Chưa duyệt: CHỜ CHỦ DỰ ÁN NGHIỆM THU; thiếu điều kiện kỹ thuật: BLOCKED; yêu cầu sửa: REWORK. Không đổi kết luận lịch sử.
- Được upload/bàn giao APK QA hợp lệ vào Drive Ni N owner-only theo CLAUDE.md mục 8–9; không tự public hoặc né cảnh báo tải. Merge cần ĐẠT và chỉ thị merge riêng; push/phát hành công khai cần chỉ thị riêng. Không tự chọn hay mở nhiệm vụ kế.
- Chỉ sửa CLAUDE.md, AGENTS.md, docs/workflow/* khi chủ dự án yêu cầu; không tự thay quy trình.

## Đầu phiên (bắt buộc)

Chạy: `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, `git log -5 --oneline`, `git diff --check`; xem có commit trong vài phút gần đây (agent khác đang chạy). Cây bẩn không thuộc phiên/bàn giao đã xác minh, HEAD/branch lạ, baseline không khớp, agent khác đang code → **DỪNG và báo**, không sửa gì. Ngoại lệ chỉ sửa tài liệu quy trình trong worktree riêng theo CLAUDE.md mục 1. Chỉ làm trên nhánh `dot/<TASK_ID>`; không sửa nhánh tích hợp.

## Danh sách CẤM tuyệt đối (cấu hình Codex không tự chặn — tự tuân thủ)

- Git: cấm `reset --hard`, `clean`, `stash`, `checkout`/`restore` đè file, `push --force`, rebase/sửa lịch sử. Không merge/cherry-pick vào nhánh tích hợp khi chưa có ĐẠT và chỉ thị riêng; không push lên remote khi chưa được yêu cầu.
- ADB: `adb uninstall`, `adb shell pm clear`, `install -d` (downgrade), xóa data/cache/TDLib, logout/đổi tài khoản Telegram, bật airplane/tắt mạng, đổi timeout/brightness/AOD/khóa màn hình, `input keyevent 26`. Cài APK **chỉ** `adb -s <serial> install -r "<APK đã kiểm>"` sau khi kiểm package, versionCode không giảm, cert khớp, Telegram config=true (chỉ boolean).
- Bí mật: không đọc/in/log/commit/copy `local.properties`, `secrets.properties`, keystore/`*.jks`, OTP, credentials, API hash, dữ liệu TDLib session/cache/noBackup. Không mang Telegram config từ Tiny về Ni.
- Máy/thư mục cấm chạm: `C:\Bots\MusicAssistantCar`, `C:\Bots\MusicAssistant`, `C:\Bots\TelegramSecretary`, repo gốc `C:\Bots\MeloVault-E91-Shareable` trên Tiny. Không chép đè hay sửa repo Tiny qua ổ `T:`. Không cài FYT.
- Code: không đổi package, signer/signing config, songKey, schema/owner/session, versionCode ngoài kế hoạch; không chuyển sang Compose; không xóa bản trùng tên hay cache/Telegram index.
- Không ghi đè/xóa APK, ảnh hay artifact cũ. Không public ảnh/log/video có Telegram PII.

## Tiny (DESKTOP-HNP4GVR) qua Desktop Commander

- Chỉ build/QA Sony trong **clone cô lập** mới trên Tiny, nhận code bằng **Git bundle** đã đối chiếu SHA-256 hai đầu. Telegram config chỉ nằm ở Tiny.
- `start_process` từ xa chỉ chờ ~3 giây: chạy Gradle dài bằng `Start-Process ... -WindowStyle Hidden`, ghi log + file exit code dưới `C:\Bots\_qa_temp\`, rồi đọc kết quả sau (có thể đọc qua `T:` từ Ni, chỉ đọc).
- Sony serial `QV725WEQ54` trên Tiny — thiết bị QA thật duy nhất (Shanling M3 Ultra trên Ni đã bán 31/10/2026, không còn dùng được). Mỗi phiên kiểm lại `adb devices -l` và `getprop ro.product.model` trên đúng máy.
- Được chủ động dùng Sony an toàn để khảo sát, kiểm chứng, thử APK hợp lệ trong **lập kế hoạch, thực thi và sơ bộ**, không cần xin lại quyền cho từng bước thuộc nhiệm vụ. Ghi thao tác/kết quả vào hồ sơ; giữ mọi gate cài APK, dữ liệu, bí mật và vòng đời màn hình. Vai lập kế hoạch/sơ bộ không tự sửa code/build mới.

## Màn hình QA

Theo CLAUDE.md mục 7 và `docs/workflow/SCREEN_LIFECYCLE_QA.md`: màn mặc định OFF; bật `keyevent 224` chỉ trước bước cần màn; tắt `keyevent 223` trong `finally` (kể cả FAIL/BLOCKED) và xác minh `dumpsys display` có `Display State=OFF`; ghi `SHANLING_SCREEN_END` / `SONY_SCREEN_END` trong báo cáo.

## Test, build, báo cáo

- Trên Ni: `.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain` và `.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain`, xuất log ra file, chỉ ghi dòng tổng kết; tách tests pass/skipped/failed. Gradle xanh không chứng minh ADB/TDLib/UI thật; Media3 PLAYING không chứng minh có âm thanh thật.
- Báo cáo `docs/plans/<TASK_ID>-bao-cao.md` (≤ ~250 dòng, thêm phụ lục có liên kết khi cần) theo PROMPT_SONNET.md, dòng đầu `Thực thi: <Codex/Claude Code> <model> <effort nếu có>, phiên <...>`; không đoán model/effort nếu không biết. Ghi rõ phần mỗi agent thực hiện. Ảnh private ở `docs/plans/artifacts/<TASK_ID>/` (không commit), có `00_tong_hop.png` khi có QA UI.
- Commit trên `dot/<TASK_ID>` rồi **DỪNG**. Lời kết: Đã làm / Chưa kiểm chứng / Rủi ro; thực thi cuối bàn giao phiên **sơ bộ mới**, sơ bộ xong ghi "Đã dừng, chờ chủ dự án nghiệm thu." theo PROMPT_CODEX.md. Phiên chỉ đổi quy trình báo thay đổi/commit, không yêu cầu nghiệm thu APK.
- Gặp tình huống không có trong kế hoạch, signature mismatch, downgrade, ADB unauthorized, APK thiếu config → **DỪNG, ghi BLOCKED**, không đoán, không phá luật để có kết quả.
