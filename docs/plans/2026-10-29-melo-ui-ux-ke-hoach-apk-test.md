# Hồ sơ APK QA — Melo UI/UX

TASK_ID: `2026-10-29-melo-ui-ux-ke-hoach`.

## REWORK R1c — hiện hành, 30/10/2026

**QA CANDIDATE — CHƯA NGHIỆM THU; giữ BLOCKED Đảo/QA1 tablet/QA6 Shanling. BLOCKED — CHƯA BÀN GIAO QUA DRIVE.**

| Mục | Giá trị |
|---|---|
| Source | `b599e805533e212dd4b6f5cde352c98f29057828` (tree từ `R1c-prepare.json` trên Tiny) |
| Bundle | `MeloVault-R1c.bundle`, SHA-256 hai đầu `F47D88182B660BF4D478308A26A78471B536A8D7C63C6D16400443CE9EA5EB0F` |
| Tiny clone | `C:\Bots\MeloVault-R1-IsolatedQA-20261030_093104` (fetch bundle, checkout detached, sạch); config copy file-to-file tại Tiny từ clone S7, không đọc/in/mang về Ni |
| Gate | `reportTelegramConfig` → `telegram.config.valid=true`; aapt `app.melovault.player` vc121 `0.6.96-beta`; apksigner cert `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`; APK fresh |
| Debug | 34.570.449 bytes, `138688175DC02450467381F49CF4FB61CF1361B9EDA87EC39CE56ABE3D063482` |
| Release | 30.769.518 bytes, `0CBE222F2B8948A97922A80B77DC78746A9038A41D3EC99E8A548F238CFA0A72` — chưa cài/QA UI |

- Sony `QV725WEQ54` XQ-AS52/Sony: trước cài vc121, cert khớp, media không phát; chỉ `install -r` Debug → Success; vc121; APK kéo lại từ máy cùng hash Debug. Không downgrade/uninstall/xóa data.
- Bản nội bộ (không phải link Internet): [Debug Ni](C:/Users/ntdun/.codex/worktrees/melo-ui-ux-plan/MeloVault-E91-Shareable/docs/plans/artifacts/2026-10-29-melo-ui-ux-ke-hoach/R1-20261030_093104/MeloVault-R1-QA-CANDIDATE-CHUA-NGHIEM-THU-vc121-b599e80-debug-20261030_093104.apk), [Release Ni](C:/Users/ntdun/.codex/worktrees/melo-ui-ux-plan/MeloVault-E91-Shareable/docs/plans/artifacts/2026-10-29-melo-ui-ux-ke-hoach/R1-20261030_093104/MeloVault-R1-QA-CANDIDATE-CHUA-NGHIEM-THU-vc121-b599e80-release-20261030_093104.apk); Tiny `C:\Bots\_qa_temp\MeloVault-R1-20261030_093104\`.
- Drive: connector của Claude Code không thuộc tài khoản Ni N (không đọc được thư mục S7), nên không upload và kiểm owner-only được; APK S7 trên Ni N từng bị 403 `cannotDownloadAbusiveFile`. Không public, không đường vòng. Gửi Debug qua chat Claude Code ngày 30/10 chỉ hiện trong app desktop trên Ni (33 MB vượt giới hạn 30 MB của điện thoại/web); chưa có đường tải từ xa.
- Bản trung gian R1 `91e6f19` (Debug `40A65539…48AD`) và R1b `0634d47` (Debug `A9A2BFAA…5D0E`) chỉ dùng chẩn đoán trên Sony, không bàn giao.
- Rollback: cài lại candidate S7 (`278f0d2`, cùng vc121/cert) bằng `install -r` sau gate; không xóa data.

## S7 — lịch sử

**QA CANDIDATE — CHƯA NGHIỆM THU; còn điểm chặn thứ tự khi bật Đảo.**

Trạng thái hiện tại: APK mới đã build/gate và cài Debug Sony; **BLOCKED — CHƯA BÀN GIAO QUA DRIVE** do kiểm tải HTTP403 `cannotDownloadAbusiveFile` (Drive nhận diện malware/spam). Không né cảnh báo hoặc đổi quyền. Link lưu trữ dưới đây không được coi là link tải hoạt động.
S2–S5b hoàn thành code/test; S6/P1 chưa hoàn tất toàn bộ. Chủ dự án cho phép S7 độc lập ngày 29/10/2026; không cho sửa service/playback hoặc gỡ BLOCKED Đảo.

| Nguồn | Giá trị |
|---|---|
| Source | `278f0d277aeb077bb938cf76f0e055c5058dd996` |
| Source tree | `a7713aaa4321b6fa7c637db687c9061aca7f747d` |
| Plan commit | `f912ef5eca8574deb304872af73a2f7ad5856f95` |
| Nhánh | `dot/2026-10-29-melo-ui-ux-ke-hoach` |
| Bundle | `MeloVault-S7-278f0d2.bundle`, 13.089.657 bytes |
| Bundle SHA-256 hai đầu | `8016797C18E0C93A9D9F797DE388E74C7D4E776CA723BCD57ECB0431E6F98DD9` |
| Tiny clone mới | `C:\Bots\MeloVault-S7-IsolatedQA-20261029_170707` |
| Tiny artifact | `C:\Bots\_qa_temp\MeloVault-S7-20261029_170707` |
| Ni artifact | `C:\Users\ntdun\.codex\worktrees\melo-ui-ux-plan\MeloVault-E91-Shareable\docs\plans\artifacts\2026-10-29-melo-ui-ux-ke-hoach\S7-20261029_170707` |

Test S6 source cuối đã đối chiếu manifest/XML: mỗi Debug/Release 178 suites, 2261 tổng = 2240 passed /21 skipped /0 failed /0 errors, exit0. Build Ni S6 đúng source, không cài vì chưa dùng như APK cấu hình hợp lệ. Không chạy lại full vì source không đổi.

## APK mới và gate

Build Tiny 17:11:07–17:17:09 +07, exit0; `BUILD SUCCESSFUL in 5m 42s`. `v3-config.log`: `telegram.config.valid=true`; source/tree sạch đúng bảng trên. Lượt script đầu lỗi mã hóa và xử lý stderr trước Gradle, giữ log riêng; chỉ v3 là build thực. Không sửa code/build config.

| APK | Bytes | SHA-256 |
|---|---:|---|
| Debug | 33.560.516 | `EB65BBFEDC851188ADAD28A36F374AC6B09878820060CEA0680F16D44146B133` |
| Release | 30.769.518 | `51DC50E4787CF127A439F479BA33BF953D6CCDEBF49E53FA942131FAB13BD300` |

Cả hai: package `app.melovault.player`, vc121, `0.6.96-beta`, cert SHA256 `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`, Telegram config **true**, APK fresh, đã kiểm aapt/apksigner/hash/bytes. Ni copy cùng hash; tên `MeloVault-S7-vc121-278f0d2-<debug|release>-20261029_170707.apk` dưới Ni artifact ở bảng. Tiny giữ tên đầy đủ `MeloVault-S7-QA-CANDIDATE-CHUA-NGHIEM-THU-vc121-278f0d2-<debug|release>-20261029_170707.apk`.

Sony cài `adb -s QV725WEQ54 install -r <Debug APK đã kiểm>` thành công; installed vc121, cert khớp, MediaSession state0 trước cài, màn OFF sau cài. Release chưa cài/QA UI. Không hạ phiên bản, không xóa dữ liệu.

- [Debug nội bộ Ni — không phải link Internet](C:/Users/ntdun/.codex/worktrees/melo-ui-ux-plan/MeloVault-E91-Shareable/docs/plans/artifacts/2026-10-29-melo-ui-ux-ke-hoach/S7-20261029_170707/MeloVault-S7-vc121-278f0d2-debug-20261029_170707.apk).
- [Release nội bộ Ni — chưa cài/QA UI](C:/Users/ntdun/.codex/worktrees/melo-ui-ux-plan/MeloVault-E91-Shareable/docs/plans/artifacts/2026-10-29-melo-ui-ux-ke-hoach/S7-20261029_170707/MeloVault-S7-vc121-278f0d2-release-20261029_170707.apk).
- Kéo lại APK đã cài sau QA: SHA256 đúng Debug ở bảng, xác minh đang kiểm đúng candidate. Crash buffer PID hiện tại từ17:18 trả exit0,0 dòng; không suy kiểm toàn bộ ANR/soak.

## Drive Ni N — đã upload nhưng tải BLOCKED

- [Thư mục owner-only](https://drive.google.com/drive/folders/1Xs7u14weay2nhDwyfC1AmGsYISglKaZ5).
- [APK Debug lưu trên Drive — TẢI BỊ CHẶN 403](https://drive.google.com/file/d/1_HHKMGM4y5CGqYhp0n8t1qxLDZsbtqzf/view?usp=drivesdk).
- Connector đọc lại folder và Debug: owner Ni N (`nigamehome@gmail.com`), chỉ permission owner, `shared=false`; Debug size33560516 khớp. Kiểm tải `fetch(download_raw_file=true, include_base64=false)` trả HTTP403 `cannotDownloadAbusiveFile`: “This file has been identified as malware or spam and cannot be downloaded.” Không acknowledgeAbuse, không public, không dùng URL khác để vượt chặn.
- Release giữ nội bộ; không đưa Release lên làm đường vòng sau khi cổng tải candidate bị chặn. Chưa xác minh hash bản tải xuống vì Drive không cho tải. Bản nội bộ Ni/Tiny vẫn có SHA/bytes/cert đã kiểm.

Shanling Ni: `adb devices -l` không có thiết bị, QA6 BLOCKED. Sony Tiny đã xác minh `QV725WEQ54` / XQ-AS52 / Sony; trước cài vc121, signer `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`. Không downgrade/đổi signer/package/config/versionCode.

## QA và điểm dừng

- QA Sony đã quan sát launch không tự phát,lưu/bỏ lưu thật(Playlist2→3→2,không đổi player),đệm/tải thật,mixset>2h,+60/−30/seek,Next,queue preview/dock,hai màu và Nguồn/kênh bên phải. Xem [ảnh và ma trận QA1–QA6](2026-10-29-melo-ui-ux-ke-hoach-hinh-nghiem-thu.md).
- **Hai lỗi UI mới:** mất anchor ngay lần xoay đầu dù giữ query/scope; feedback Deck còn Tạm dừng khi Next đã đổi bài và phát≈42s,queue/nút pause đúng. Không sửa code; chưa đủ điều kiện kết luận P1/S6 hoàn tất.
- QA1 tablet thật và QA6 Shanling **BLOCKED**. Âm thanh thật **CHƯA XÁC NHẬN**; offline mất mạng,tablet/S25/FYT thật,font lớn/IME/keyboard/TalkBack/star mọi surface và full queue chưa kiểm đủ. Fake JVM S6 không thay QA thật.
- SONY_SCREEN_END=OFF17:47:44 +07; SHANLING_SCREEN_END=UNKNOWN vì ADB Ni trống. Đã trả Current/query rỗng/xoay free/font1.0/density420; media cuối paused/65943ms. QA đã thay bài/nghe tiếp và khởi đầu tải bài; không xóa cache/data.
- Chưa nghiệm thu sơ bộ/chính thức; không merge/push/public. Trước sửa BLOCKED Đảo cần bổ sung kế hoạch authority chung UI queue/Next/transport,logical queue/Media3 window/local và quyền sửa service/test. Hai lỗi UI cần chủ dự án giao phạm vi sửa/test riêng; không tự mở REWORK.
- Rollback: giữ APK/source cũ,không tự cài downgrade/gỡ/xóa data hoặc đổi signer. Candidate cùngvc121; chỉ cài bản khác khi đã kiểm gate và được giao phù hợp.
