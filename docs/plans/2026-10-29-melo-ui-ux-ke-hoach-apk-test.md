# S7 — hồ sơ APK QA có điều kiện

TASK_ID: `2026-10-29-melo-ui-ux-ke-hoach`.

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
