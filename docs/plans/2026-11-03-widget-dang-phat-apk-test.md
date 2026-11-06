# APK QA — Widget Đang phát

## Candidate hiện tại · 06/11/2026 · **QA CANDIDATE — CHƯA NGHIỆM THU**

- Thư mục Drive (owner-only, `ntdung.md@gmail.com`): [MeloVault-WIDGET-QA-CANDIDATE-20261106-vc122-be61487](https://drive.google.com/drive/folders/1rVqHjK2cuTqXV-6D1GBy9jmywbtjNwZr)
- APK Debug: [MeloVault-0.6.97-beta-vc122-widget-be61487-QA-CANDIDATE-debug.apk](https://drive.google.com/file/d/1boRUlxxaXwkf-00P2Dk5K8_5PElN1cqN/view?usp=drivesdk)

| Thuộc tính | Giá trị |
|---|---|
| Loại | Debug QA, biến thể `shareable` thường (không `.open`) |
| Package | `app.melovault.player` |
| versionCode / versionName | **122 / `0.6.97-beta`**, cùng số với bản đã ĐẠT `BF7B23A7…`; phân biệt bằng SHA |
| Source | `be61487dc387b05db6798cdaf74ae222b07e7eef`, nhánh `dot/2026-11-03-widget-dang-phat`, baseline `f82185b` |
| Build | Tiny, clone cô lập `C:\Bots\MeloVault-WIDGET-IsolatedQA-20261106-be61487`; bundle SHA-256 `34E1B06C913B09579F06F1DD5515562DC53BB9547340DFD25D59F41B4FECE33B` khớp hai đầu; BUILD SUCCESSFUL 2m39s |
| Byte | 33.937.332 |
| SHA-256 | `58BD55F0B5C22A256E6572516C277A3049A617F853A1830B30280F70AD48FD07` |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (cùng cert bản đang cài, cài đè thành công) |
| Telegram config | true (không in giá trị) |
| File thật | Tiny `C:\Bots\_qa_temp\widget-20261106-rework\`; Ni `docs/plans/artifacts/2026-11-03-widget-dang-phat/rework-20261106/`; Drive mount `Z:\MeloVault_QA\MeloVault-WIDGET-QA-CANDIDATE-20261106-vc122-be61487\` (SHA-256 cả ba khớp) |
| Test JVM | Debug/Release mỗi biến thể 208 suite, 2.812 test, 2.791 đạt, 21 skipped, 0 lỗi; assemble Shareable Debug/Release/AndroidTest, `.open` Debug, Personal Debug xanh |
| Thiết bị đã test | Sony XQ-AS52 `QV725WEQ54` Android 12, `adb install -r`, APK pull từ máy khớp SHA. Chi tiết: [báo cáo](2026-11-03-widget-dang-phat-bao-cao.md) |
| Kiểm Drive | Thư mục, thư mục ảnh, APK, ảnh tổng hợp: một quyền duy nhất, owner. Kích thước APK khớp. **Tải bằng trình duyệt chưa thử**; connector không trả SHA Drive |

APK kiểm thử thiết bị đi kèm (không dành cho người dùng): `widget-device-tests-be61487.apk`, 454.447 B, SHA-256 `DBA4F80FF70864CCA2B5AB869CE50B685215C7238F14893F40F9B5CAC302F8C6`, chỉ trên Tiny/Sony.

Lưu ý tải: APK widget 03/11 từng bị 403 `cannotDownloadAbusiveFile` trên tài khoản Drive khác. Nếu gặp lại, ghi BLOCKED riêng phần tải; không né cảnh báo, không mở quyền công khai. Bản đúng SHA đã cài sẵn trên Sony.

Rollback: gỡ widget khỏi Home. Không hạ APK/DB, không xóa dữ liệu; cài lại `BF7B23A7…` được vì cùng vc122 và cùng cert nếu chủ dự án yêu cầu.

**CHƯA XÁC NHẬN ÂM THANH THỰC.** Các phần chưa kiểm khác: [báo cáo](2026-11-03-widget-dang-phat-bao-cao.md).

## Lịch sử candidate 03/11/2026 (đã thay thế, source `49ed94c`)

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
