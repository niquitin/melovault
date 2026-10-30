# Bàn giao — 2026-10-30-dao-thu-tu-phat

## Checkpoint hiện tại (sau phiên thực thi 1, S0–S5)

1. **TASK_ID** `2026-10-30-dao-thu-tu-phat`; 30/10/2026 ~11:45 +07; Claude Code (Sonnet 5.5), vai **thực thi phiên 1**. Nhiệm vụ: gỡ BLOCKED thứ tự khi bật Đảo của S6 (`2026-10-29-melo-ui-ux-ke-hoach`).
2. **Worktree** `D:\Bots\_worktrees\MeloVault-dao-thu-tu-phat`; nhánh `dot/2026-10-30-dao-thu-tu-phat`; baseline source `c6c7970`; commit kế hoạch `72e456d`. **HEAD source đã test = `798b254`** (`798b25465b5fe2773ecb5dc8119e73ce73250f7f`). Commit sau đó chỉ là hồ sơ: `git log -1 --format=%h -- docs/plans/2026-10-30-dao-thu-tu-phat-ban-giao.md`.
3. **Hồ sơ:** [Kế hoạch](2026-10-30-dao-thu-tu-phat.md) · [Báo cáo thực thi 1](2026-10-30-dao-thu-tu-phat-bao-cao.md). Chưa có `*-apk-test.md`, `*-hinh-nghiem-thu.md`, `*-nghiem-thu.md` (thuộc S6).

| Bước | Trạng thái | Bằng chứng | Việc còn lại |
|---|---|---|---|
| Kế hoạch | Xong | `72e456d` | — |
| S0 tái hiện Sony | Xong (một phần) | Báo cáo mục 3; ảnh ở Tiny `%TEMP%\dtp_s0` | Đảo bật + ⏭ ×3 ra thứ tự gốc (lệch tái hiện được); chưa thấy khung hàng chờ, chưa thử hết bài tự nhiên |
| S1 policy + test | Xong | `f04dd86`; 18 test pass 2 biến thể | — |
| S2 service | Xong | `768b3ae`; biên dịch 2 biến thể | Chưa chạy trên máy |
| S3 Activity Telegram | Xong | `31a6098`; biên dịch 2 biến thể | Chưa chạy trên máy |
| S4 Nhạc trên máy | Xong (Q2 mặc định: cả Telegram và Local) | `7da4620` | Chưa chạy trên máy; đo `ms=` local_reorder |
| S5 UI chữ + full test/build Ni | Xong | `798b254`; 2287 test, 21 skipped, 0 failed (Debug = Release); assemble 2 biến thể xanh; `diff --check` sạch | — |
| S6 Tiny build, QA, Drive | Chưa làm | — | **Phiên thực thi 2** |

4. **File chưa commit:** không còn sau commit hồ sơ. Thư mục chính `D:\Bots\MeloVault-E91-Shareable` có `?? .codex-remote-attachments/` và `?? docs/plans/2026-10-29-hoc-hoi-ui-ux-telegram-music.md` — **không thuộc nhiệm vụ này**, không động vào. `docs/plans/artifacts/` bị `.gitignore` toàn bộ (log/ảnh không thể bị commit).
5. **Test/build:** chạy trên Ni một lần ở S5 (báo cáo mục 5). Chỉ chứng minh JVM + biên dịch, không chứng minh ADB/TDLib/UI/âm thanh thật.
6. **APK/thiết bị:** không cài APK, không build Tiny, không Drive. Sony `QV725WEQ54` vẫn R1c Debug vc121 (chưa có thay đổi này). S0 dùng Sony qua Tiny: **SONY_SCREEN_END** (màn OFF, kiểm `Display State=OFF`), bài đang phát được tạm dừng. Shanling: không thao tác. APK build trên Ni không có cấu hình Telegram nên **không** dùng để QA.
7. **Quyết định cần chủ dự án:** (a) Q1–Q9 đã áp dụng theo mặc định, chưa có lựa chọn khác; (b) **R-1** (báo cáo mục 6): giới hạn 2000 bài của service khiến Đảo vô tác dụng khi danh sách đang hiện > 2000 bài; cần quyết có mở rộng không (ngoài phạm vi kế hoạch hiện tại).
8. **Trạng thái:** phiên thực thi 1 đã dừng; không tác vụ chạy nền. Điều kiện dừng mục 10 kế hoạch chưa xảy ra.

## Quyết định chủ dự án

- (chưa có — phiên thực thi 1 đã dùng mặc định Q1–Q9; S6 giữ nguyên trừ khi chủ dự án ghi khác ở đây)

## Prompt phiên thực thi 1 (S0–S5, Ni + Sony khảo sát) — đã chạy, giữ để tham chiếu

```text
Thực thi TASK_ID 2026-10-30-dao-thu-tu-phat, phiên thực thi 1, chặng S0–S5.
Worktree D:\Bots\_worktrees\MeloVault-dao-thu-tu-phat; nhánh dot/2026-10-30-dao-thu-tu-phat;
HEAD dự kiến = commit kế hoạch (git log -1 -- docs/plans/2026-10-30-dao-thu-tu-phat-ban-giao.md), baseline source c6c7970.
Đọc CLAUDE.md mục 0 (Codex đọc thêm AGENTS.md), docs/workflow/QUY_TRINH_3_VAI_TRO.md,
docs/plans/2026-10-30-dao-thu-tu-phat-ban-giao.md và docs/plans/2026-10-30-dao-thu-tu-phat.md.
Phiên kế hoạch đã dừng, không có tác vụ chạy nền. Chủ dự án dùng mặc định Q1–Q9 trừ khi mục
"Quyết định chủ dự án" trong file bàn giao ghi khác.
Đầu phiên: git status --short; git branch --show-current; git rev-parse HEAD; git log -5 --oneline;
git diff --check; kiểm không agent nào khác đang sửa nhánh này. Lệch → dừng, không reset/clean/stash.
S0: dùng Sony QV725WEQ54 qua Tiny DESKTOP-HNP4GVR (kiểm adb devices -l, getprop model/manufacturer,
dumpsys display/power trước-sau, bật màn keyevent 224 chỉ khi cần, tắt 223 trong finally) với APK
đang cài (không cài mới): tái hiện kế hoạch mục 3 bằng kịch bản Q-1/Q-2, chụp PNG vào
docs/plans/artifacts/2026-10-30-dao-thu-tu-phat/ (không commit ảnh có Telegram PII).
S1→S5: làm đúng kế hoạch mục 5 và 6, commit riêng từng chặng, sau mỗi chặng cập nhật
docs/plans/2026-10-30-dao-thu-tu-phat-bao-cao.md (tạo mới, ≤ ~250 dòng, bảng trước) và file bàn giao.
Chỉ sửa các file kế hoạch nêu: MeloShuffleOrderPolicy.kt (mới) + test, ShareablePlaybackService.kt,
TelegramPreviewActivity.kt, MeloQueuePane.kt (MeloQueueUiPolicy) + MeloQueueUiPolicyTest, KDoc
MeloScreenHost.queue(). Không đổi schema/songKey/TDLib/package/signer/versionCode.
Cuối S5 chạy một lần, log ra file artifacts, chỉ đọc tổng kết + XML:
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
git diff --check
Ghi passed/skipped/failed từng biến thể. Không build Tiny/cài APK/Drive trong phiên này (đó là S6).
Gặp điều kiện dừng ở kế hoạch mục 10 → ghi BLOCKED đúng phần, cập nhật bàn giao, dừng.
Kết thúc: commit hồ sơ, ghi prompt phiên thực thi 2 (S6) vào file bàn giao, trả lời 3 phần
Đã làm / Chưa kiểm chứng / Rủi ro hoặc điểm dừng, và câu "Đã dừng, bàn giao phiên thực thi tiếp theo."
```

## Prompt phiên thực thi 2 (S6) — dán nguyên văn

```text
Thực thi TASK_ID 2026-10-30-dao-thu-tu-phat, phiên thực thi 2, chặng S6 (Tiny build + QA thiết bị + hồ sơ bàn giao).
Worktree D:\Bots\_worktrees\MeloVault-dao-thu-tu-phat (Ni); nhánh dot/2026-10-30-dao-thu-tu-phat.
HEAD source đã test = 798b254 (798b25465b5fe2773ecb5dc8119e73ce73250f7f). HEAD dự kiến của phiên = commit hồ sơ phiên 1
(git log -1 --format=%h -- docs/plans/2026-10-30-dao-thu-tu-phat-ban-giao.md); sau 798b254 chỉ được có commit tài liệu.
Đọc CLAUDE.md mục 0, 3, 4, 6–10; docs/workflow/QUY_TRINH_3_VAI_TRO.md; docs/plans/2026-10-30-dao-thu-tu-phat-ban-giao.md;
docs/plans/2026-10-30-dao-thu-tu-phat-bao-cao.md (mục 3 S0, 4, 6) và kế hoạch mục 6 (S6), 7, 8, 9, 10.
KHÔNG sửa source. Chỉ build/QA/hồ sơ. Chủ dự án dùng mặc định Q1–Q9 trừ khi mục "Quyết định chủ dự án" ghi khác.
Đầu phiên (Ni): git status --short; git branch --show-current; git rev-parse HEAD; git log -5 --oneline; git diff --check;
kiểm không agent khác đang sửa nhánh. Lệch → dừng, không reset/clean/stash. Kiểm git diff --stat 798b254..HEAD chỉ chứa docs/.
Lưu ý: Ni chưa cấu hình user.name/email git; commit hồ sơ dùng -c user.name="Claude Code" -c user.email="noreply@anthropic.com".
S6 theo thứ tự:
1) Git bundle từ nhánh + SHA256; Tiny DESKTOP-HNP4GVR clone cô lập (không chạm repo Tiny gốc/T:), đối chiếu HEAD = 798b254 + hồ sơ.
2) Build Debug trên Tiny với cấu hình Telegram bí mật chỉ ở Tiny (không chép về Ni, không in giá trị). Gate APK: package app.melovault.player,
   versionCode 121 (không tăng), versionName 0.6.96-beta, cert SHA-256 eecd3df7…ce8, Telegram config true/false, SHA-256 và bytes, đúng commit.
3) Gate Sony QV725WEQ54 (adb devices -l, getprop model XQ-AS52/Sony, MediaSession không đang phát, versionCode ≥ đang cài, cert khớp) → chỉ adb install -r.
   Không uninstall/pm clear/xóa cache/TDLib/logout/downgrade/-d. Màn: bật 224 chỉ khi cần, tắt 223 trong finally, ghi SONY_SCREEN_END.
4) QA Q-1…Q-8 (kế hoạch mục 8) trên Sony; Shanling 30313860180939 nếu ADB thấy (KHÔNG dùng cmd media_session dispatch trên Shanling).
   Thu log: adb logcat -s MeloShuffle:D (stage=configure|toggle|next_inserted|order_reset|local_reorder; local_reorder có ms=) và dumpsys media_session (chỉ trạng thái).
   Đối chiếu: ⏭ trên app, ⏭ thông báo/khóa màn, hàng chờ hiển thị = thứ tự phát. Cần tìm lối vào khung hàng chờ (S0 chưa thấy ở màn Đang phát compact; thử Deck/xoay ngang/nút Hàng chờ).
   Điều kiện BLOCKED: Q-2 vẫn lệch (⏭ ×3 không khớp hàng chờ) hoặc log order_reset lặp lại khi Đảo bật → ghi BLOCKED đúng phần, không tự đổi kiến trúc.
   Ghi đúng: Media3 PLAYING ≠ âm thanh thật → CHƯA XÁC NHẬN ÂM THANH THỰC. Kiểm thêm R-1 (danh sách > 2000 bài) chỉ để ghi nhận, không sửa.
5) Ảnh S0 đang ở Tiny %TEMP%\dtp_s0 (không dùng làm chứng cứ bản mới; chụp lại). Tạo docs/plans/artifacts/2026-10-30-dao-thu-tu-phat/00_tong_hop.png (≤1600 px). Không commit ảnh có Telegram PII.
6) Drive Ni N owner-only: APK QA hợp lệ + 5–10 ảnh quan trọng, kiểm SHA-256/bytes, shared=false; ghi *-apk-test.md (nhãn QA CANDIDATE — CHƯA NGHIỆM THU),
   *-hinh-nghiem-thu.md, *-nghiem-thu.md theo PROMPT_CHU_DU_AN_NGHIEM_THU.md; thao tác chủ dự án = kế hoạch mục 9. Drive chặn tải/thiếu gate → BLOCKED — CHƯA BÀN GIAO, không bịa link.
7) Cập nhật bao-cao (≤ ~250 dòng) và ban-giao; commit hồ sơ trên nhánh; không merge/push/public; không chọn việc tiếp.
Kết thúc, trả lời 3 phần Đã làm / Chưa kiểm chứng / Rủi ro hoặc điểm dừng, rồi câu "Đã dừng, chờ chủ dự án nghiệm thu." kèm
cách phản hồi `ĐẠT 2026-10-30-dao-thu-tu-phat — <commit/APK>` hoặc `Yêu cầu sửa 2026-10-30-dao-thu-tu-phat: <lỗi>`.
```
