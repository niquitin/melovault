# APK QA — Widget Đang phát

03/11/2026 · **QA CANDIDATE — CHƯA NGHIỆM THU**.

**BLOCKED — CHƯA BÀN GIAO APK TẢI ĐƯỢC.** Upload Drive hoàn tất và metadata owner-only đã đọc lại, nhưng thử tải authenticated trả HTTP403, reason `cannotDownloadAbusiveFile`: “This file has been identified as malware or spam and cannot be downloaded.” Không retry với acknowledgeAbuse, không né cảnh báo, không đổi quyền/public hoặc upload nơi khác.

| Thuộc tính | Đã kiểm |
|---|---|
| Source commit | `49ed94ccbd73abb2d9443828ede26c205e1e9e56` |
| Nhánh / baseline | `dot/2026-11-03-widget-dang-phat` / `b90834f44457b60a9732e59e59c1bc9c971368c3` |
| Build | Tiny clone cô lập; Debug Shareable thường; BUILD SUCCESSFUL 3m10s, exit0 |
| Package / phiên bản | `app.melovault.player` · vc121 · `0.6.96-beta` |
| Telegram config | true (chỉ boolean; không đọc/in/copy giá trị) |
| Bytes | 33.839.028 (Ni/Tiny/metadata Drive khớp) |
| SHA256 APK Ni/Tiny/cài AVD | `3C37DBE24337443E417F0B53CC27198FE3AE8D28048C84615586BB3B16221F75` |
| Certificate SHA256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (apksigner verify thành công) |
| Gate Ni | Debug/Release mỗi 2.737 test: 2.716 đạt, 21 skipped, 0 lỗi; assemble Debug/Release/.open/Personal xanh |
| Cài | `adb -s emulator-5586 install -r <APK đã kiểm>` → Success; package mới trên AVD riêng, không downgrade/chữ ký cũ/EQ; installed base.apk SHA khớp |
| QA | Q1 empty/cold-open và Q5 resize/two instances phần trống đã kiểm; Q2–Q4 và Q6 có playback BLOCKED vì thiếu phiên QA đăng nhập |
| Quyền Drive | shared=false, chỉ owner Ni N (`nigamehome@gmail.com`); không public |
| SHA256 Drive / khả năng tải APK | Chưa đối chiếu SHA256 từ xa vì download403; **không coi link dưới là link tải hợp lệ** |

- [File APK trên Drive — BỊ CHẶN TẢI403](https://drive.google.com/file/d/1n-T9fhqU-PI7mCmcPw-Ej8AuYksuUzO5/view?usp=drivesdk).
- [Thư mục QA riêng tư](https://drive.google.com/drive/folders/1n7KhjK_3mr3LlBJ74ZHaD6ZhrVuO7K8g).
- [APK nội bộ Ni đã kiểm](C:/Users/ntdun/.codex/worktrees/melovault-widget/MeloVault-E91-Shareable/docs/plans/artifacts/2026-11-03-widget-dang-phat/MeloVault-widget-QA-v121-49ed94c-3C37DBE2.apk).
- Tiny: `C:\Bots\MeloVault-WIDGET-IsolatedQA-20261103-49ed94c\app\build\outputs\apk\shareable\debug\app-shareable-debug.apk`.
- [Ảnh và giới hạn QA](2026-11-03-widget-dang-phat-hinh-nghiem-thu.md) · [Báo cáo](2026-11-03-widget-dang-phat-bao-cao.md) · [Checkpoint](2026-11-03-widget-dang-phat-ban-giao.md).

Không cài lên Sony đang giữ candidate EQ. Chưa chứng minh bìa thật lên launcher, ca sĩ runtime, pause/resume/previous/next, next Telegram chưa tải, mở Deck khi có bài, độ trễ1s, nhiều instance khi đổi bài hoặc âm thanh thực. Điều kiện hoàn tất chức năng chặng B chưa đạt. Chủ dự án chưa nghiệm thu; không merge/push/phát hành.
