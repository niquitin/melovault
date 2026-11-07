Thực thi: Codex phiên 03/11/2026, chặng A → B (model/effort không được công cụ phiên cung cấp); REWORK: Claude Code, Opus 5.5, phiên 06/11/2026.

# Báo cáo widget Đang phát

## REWORK 06/11/2026 — Claude Code, Opus 5.5

**QA CANDIDATE — CHƯA NGHIỆM THU.** Sửa 2 lỗi trong [biên bản sơ bộ](2026-11-03-widget-dang-phat-nghiem-thu-so-bo.md), tích hợp bản vc122 đã ĐẠT, QA có bài trên Sony. Chưa sơ bộ lại, chưa merge, chưa push. Phần dưới mục này là lịch sử phiên 03/11; kết quả BLOCKED ở đó đã được thay bằng bảng QA sau.

| Mục | Kết quả |
|---|---|
| Source APK | `be61487dc387b05db6798cdaf74ae222b07e7eef` |
| Baseline tích hợp | `f82185b` (vc122 đã ĐẠT, gồm EQ và chủ đề Telegram) |
| Commit | Merge `0506f37` (`42be21c`), sửa lỗi `467e699`, merge `be61487` (`f82185b`) |
| Xung đột | `ShareablePlaybackService.onDestroy` giữ cả dọn widget và gỡ listener EQ; `HANDOFF.md` giữ cả hai phần |
| Phiên bản | vc122 / `0.6.97`, giống hệt `7cd4ebb` nên merge không xung đột. Không nâng lên vc123 |
| APK | [hồ sơ APK](2026-11-03-widget-dang-phat-apk-test.md): 33.937.332 B, SHA-256 `58BD55F0B5C22A256E6572516C277A3049A617F853A1830B30280F70AD48FD07` |

### Hai lỗi đã sửa

1. **Đổi metadata cùng bài mở sớm chặn Tiếp.** `MeloWidgetPolicy.Generation.update` nhận thêm khóa chặn riêng. Khóa hiển thị (item, metadata, owner) vẫn đổi token, nên token cũ và ảnh đến muộn vẫn bị từ chối. Khóa chặn chỉ gồm `mediaId` và owner (`MeloWidgetSession.NextFence`), nên cùng bài đổi tiêu đề hoặc bìa không xóa mốc 2 giây. Đổi bài, đổi owner, về trạng thái trống hoặc lỗi player vẫn mở chặn.
2. **Widget báo trống khi bài Telegram bắt đầu từ yêu cầu chờ kết nối.** `PendingPlayback` giữ thêm `PendingTelegramQueue` (owner, nguồn, bài chọn). Khi controller nối xong, `TelegramPreviewActivity.handOffTelegramQueue` chuyển queue cho service, đọc lại rồi mới phát. Owner đổi hoặc service từ chối thì không phát và báo chọn lại bài (`MeloWidgetPolicy.mayStartPendingTelegram`). Kiểm tra quyền của widget giữ nguyên. Không cần sửa logic trong `ShareablePlaybackService.kt`, ngoài phần giải xung đột.

### Gate Ni

Mỗi biến thể `testShareableDebugUnitTest` / `testShareableReleaseUnitTest`: **208 suite, 2.812 test, 2.791 đạt, 21 skipped, 0 failure, 0 error** (đếm từ XML, trên `467e699`). `MeloWidgetPolicyTest` 16 test (+4). `assembleShareableDebug` + `assembleShareableRelease` + `assembleShareableDebugAndroidTest`, `-PmeloOpen=true assembleShareableDebug`, `assemblePersonalDebug`: BUILD SUCCESSFUL. `git diff --check` sạch. `git diff 467e699 be61487 -- app/` rỗng, nên gate vẫn áp dụng cho source APK. Log: `docs/plans/artifacts/2026-11-03-widget-dang-phat/rework-20261106/ni-*.log` (không commit). Merged manifest sau gate: Shareable Release và Shareable Debug (thư mục này mang kết quả lượt `-PmeloOpen=true` chạy sau cùng) đúng 1 provider, `exported=false`; Personal Debug không có provider. APK thường trên Sony chạy widget thật ở QA.

### Build Tiny

Bundle 14.083.514 B, SHA-256 `34E1B06C913B09579F06F1DD5515562DC53BB9547340DFD25D59F41B4FECE33B`, khớp Ni/Tiny, `git bundle verify` đạt. Clone cô lập `C:\Bots\MeloVault-WIDGET-IsolatedQA-20261106-be61487`, detach đúng source. `telegram.config.valid=true`, BUILD SUCCESSFUL 2m39s. Gate APK kiểm package, version, cert. `adb install -r` Success, APK pull từ máy khớp SHA.

### QA Sony

Sony XQ-AS52 `QV725WEQ54`, Android 12, launcher Xperia. Trước cài: vc122 (bản đã ĐẠT), nhạc tạm dừng, màn OFF, 0 widget MeloVault.

| Q | Thao tác | Kết quả thực tế | Trạng thái |
|---|---|---|---|
| Sửa 2 | `MeloWidgetFixesDeviceTest` chạy riêng: dừng service, chọn bài, nối lại controller | `pending_without_service=true`, `handoff_ok=true queue=200 state=3`, `widgetCanOpenPlayer` true. Bài chọn trong lúc service vắng mặt lên đúng owner và queue | PASS |
| Sửa 1 | Cùng test: bấm Tiếp, đổi metadata A, bấm lại bằng token cũ rồi token mới, trong một khối main thread | Token đổi; token cũ và token mới đều không tạo lượt Tiếp thứ hai (`nextAt` giữ nguyên) | PASS |
| Q1 | Bộ chọn, kéo ra Home | Đúng tên và preview; trống, 3 nút tắt | PASS |
| Q2 | Bài có bìa và ca sĩ; Tạm dừng/Phát từ widget; phím media và nút trong app | Một bài Telegram và một bài trên máy, đều đúng tên, ca sĩ và bìa thật. Tạm dừng tại 54.319 ms, 2 giây sau vẫn 54.319, Phát tiếp 54.435. Nút không mở Activity. Phím media và nút app đổi đúng biểu tượng widget | PASS |
| Q3 | Tiếp, Bài trước, biên, bấm nhanh, B chưa tải, bài cục bộ | Đúng thứ tự trong Đã tải; bài đầu tắt Bài trước, bài cuối tắt Tiếp. Bấm Tiếp 2 lần nhanh chỉ sang 1 bài (đã đối chiếu bằng Bài trước). B chưa tải phát được (vị trí tăng). B thiếu bìa/ca sĩ không giữ của A (A có bìa → B hiện nốt nhạc, `Chưa rõ ca sĩ`) | PASS |
| Q4 | Chạm thẻ khi đang phát, khi tạm dừng; Back | Mở Studio Deck đúng bài; tạm dừng giữ nguyên 20.511 ms, không tự phát; Back về danh sách | PASS |
| Q5 | Hai widget, kéo rộng, đổi bài và tạm dừng từ từng bản | Hai bản cùng bài, cùng nút; bản rộng đổi bố cục, chữ không đè nút; gỡ một bản, bản kia vẫn điều khiển được | PASS, xem giới hạn |
| Q6 | Gỡ widget cuối khi đang phát, thêm lại | Sau gỡ `widgets=0`, nhạc vẫn chạy (14.809 → 21.063 ms); thêm lại hiện đúng bài, nút Tạm dừng, vị trí 35.981 ms | PASS |
| Vòng đời | Kết thúc tiến trình bằng lượt instrumentation bị bỏ qua, bấm Phát trên widget cũ, rồi chạm thẻ | Widget cũ còn nội dung tới khi có tác động (giới hạn đã biết). Bấm Phát: không có MediaSession, widget về trống. Chạm thẻ: mở app, không tự phát | PASS |

Log crash của app: `fatal=0` trong suốt lượt QA. Ảnh: [hồ sơ ảnh](2026-11-03-widget-dang-phat-hinh-nghiem-thu.md).

**Một lần đọc kết quả cần giải thích:** ở nhạc trên máy, Tiếp sang một tệp nhạc chuông dài 940 ms theo MediaStore nên tự chuyển ngay sang bài sau. Không phải widget nhảy 2 bài.

**Một lần test không đạt, chỉ do cách chạy:** chạy cả lớp `MeloWidgetFixesDeviceTest` thì test bài chờ báo `BLOCKED/FAIL: playback service gone`, vì Activity của test trước còn giữ controller nên service không dừng. Test Tiếp vẫn đạt. Chạy riêng test bài chờ thì `OK (1 test)`. Khi chạy lại phải chạy từng test một, hoặc sửa test để đóng Activity.

### Quan sát chưa sửa (ngoài phạm vi 2 lỗi)

- **Kích thước trên Xperia:** bộ chọn báo 3×3 thay vì 4×2 như kế hoạch, widget cao và trống nhiều ở giữa. Chỉ kéo được ngang (`resizeMode=horizontal`), không thu nhỏ dưới 3 cột. Chủ dự án nên xem bằng mắt.
- Bìa sát mép trái vùng nội dung của widget.
- Bài có trùng tên liền nhau trên widget khó phân biệt (gặp ở lần Tiếp cuối). Vị trí về 0 cho thấy đã chuyển bài.
- Khi chạm thẻ để mở app sau lúc tiến trình mới khởi động, bài `Nghe tiếp` hiện ở mini player, widget vẫn trống cho tới khi phát thật. Đúng với thiết kế không tự phát.

### Chưa kiểm chứng

- **CHƯA XÁC NHẬN ÂM THANH THỰC.**
- Lần chọn bài thật trong khe nhỏ trước khi controller nối xong không tái hiện được bằng tay. Lỗi 2 được kiểm bằng test dừng service rồi đi qua đúng đường mã.
- Đếm yêu cầu TDLib thực khi Tiếp sang B chưa tải. Lỗi 1 kiểm ở cổng chặn, không đếm gói TDLib.
- Chữ lớn, TalkBack, API 30 (chỉ có Android 12), màn cũ (chỉ Studio Deck), Shuffle, Y700/S25/FYT, Release/`.open` chạy trên máy.
- Thời gian phản hồi chỉ quan sát: widget đã đúng ở lần chụp sau 1–1,5 giây, chưa đo riêng.
- Tải APK từ Drive bằng trình duyệt chưa thử.

### Trạng thái cuối Sony

Bản đang cài: candidate widget `58BD55F0…` vc122, không còn là `BF7B23A7…`. Widget QA đã gỡ, Home về như ảnh đầu. Nhạc tạm dừng. `app.melovault.player.test` vẫn còn trên máy như các lần QA trước. Vài bài phát thử từ Telegram có thể đã vào cache TDLib; không xóa gì. Đã chạy `logcat -c` trước test. Không đổi tài khoản, không reset. **`SONY_SCREEN_END`: Display State=OFF.**

---

## Lịch sử phiên 03/11/2026

**A hoàn tất gate Ni; B BLOCKED ở QA playback và tải APK Drive. QA CANDIDATE — CHƯA NGHIỆM THU.** Chủ dự án chưa quyết định ĐẠT; phiên này không thực hiện vai sơ bộ, merge hoặc push.

## Nguồn và phạm vi
- TASK_ID `2026-11-03-widget-dang-phat`; nhánh `dot/2026-11-03-widget-dang-phat`; remote github `https://github.com/niquitin/melovault.git`, chưa upstream.
- Worktree Ni: `C:/Users/ntdun/.codex/worktrees/melovault-widget/MeloVault-E91-Shareable`.
- Baseline `b90834f44457b60a9732e59e59c1bc9c971368c3`; kế hoạch/HEAD bắt đầu `192170642163c69229c8945b54c26f7ac2d5a80f`.
- **Source đã build: `49ed94ccbd73abb2d9443828ede26c205e1e9e56`**, 19 file +926/-13 (gồm checkpoint A). Các thay đổi sau đó chỉ hồ sơ.
- A0 đã kiểm status/branch/HEAD/log5/diff-check, cây sạch, đúng baseline; fetch github thành công; ahead1/behind0 so với nhánh tích hợp remote (commit kế hoạch). Không lấy commit EQ hoặc đổi baseline.
- Một agent Codex thực hiện toàn bộ, không agent phụ/đổi model/handoff công cụ. Không chỉnh EQ, source/thiết bị của phiên khác, package/signer/version/schema/songKey/quyền/TDLib auth.
- Sony giữ candidate EQ: không dùng ADB, cài, QA hoặc điều khiển. Shanling đã bán theo luật gốc/BACKLOG; các dòng nhắc Shanling trong plan là lỗi thời, không coi là thiết bị QA hiện có.

| Bước | Trạng thái | Bằng chứng / phạm vi |
|---|---|---|
| A0 | Xong | Git/luật/chỉ mục/quy trình/plan/checkpoint đã kiểm |
| A1 | Xong gate Ni | Policy trạng thái, generation opaque, NEXT fence2s, intent identity, render gate; 12 test mới đạt |
| A2 | Xong gate Ni | Performer riêng mọi factory Telegram/cached/progressive/recovery/QueueTrack; raw MediaStore tag; artist và extras cũ giữ nguyên |
| A3 | Xong gate Ni; Q1 đã QA | Provider/resources chỉ Shareable/.open; exported=false, không quyền/service/poll mới |
| A4–A6 | Xong gate Ni; runtime BLOCKED | sessionPlayer/NextAwarePlayer, receiver hẹp, ảnh worker; chưa chứng minh playback và bìa thật lên launcher |
| A7 | Xong | Ma trận test/build/manifest/diff; source commit49ed94c |
| B bundle/Tiny/APK | Xong gate kỹ thuật | Bundle SHA hai đầu khớp; clone sạch/detach source; build config=true; APK/cert/hash/cài đã kiểm |
| B Q1, Q5 phần trống | Đã kiểm có giới hạn | Picker/preview/add/cold-open; resize4→3 cột; hai instance2/3 |
| B Q2–Q4, Q6 có bài | BLOCKED | AVD mới chưa có phiên QA đăng nhập; app khóa cả mở tệp trước đăng nhập |
| B bàn giao APK tải được | BLOCKED | Drive upload owner-only xong nhưng tải authenticated403 cannotDownloadAbusiveFile |
| B ảnh/hồ sơ/cleanup | Xong | 7 PNG owner-only; composite đọc/tải được; tài nguyên QA đã dọn, retained ghi dưới |

## Mã đã triển khai
- Mới: `MeloWidgetPolicy.kt`, `MeloNowPlayingWidgetProvider.kt`, `MeloWidgetSession.kt`, `MeloWidgetArtwork.kt`; layout thường/compact, XML provider, preview/nền/chuỗi Shareable; `MeloWidgetPolicyTest.kt`.
- Đổi: `MeloLocalSourcePolicy.kt`, `ShareablePlaybackService.kt`, `TelegramPreviewActivity.kt`, manifest Shareable và guard manifest `P5ReadinessInertnessTest.kt`.
- Giữ artist tên nguồn đang dùng; ca sĩ tin cậy nằm extra namespaced riêng. LocalSong thêm optional cuối, raw artist thật trùng tên thư mục vẫn giữ; fallback thư mục không thành ca sĩ. Không migration hoặc đổi định danh.
- Snapshot trên application looper, nghe onEvents và onAvailableCommandsChanged trực tiếp từ wrapper; coalesce theo main loop và so giá trị render. New id/resize force render đầy đủ. Không widget thì không decode/cập nhật RemoteViews theo event, không poll tiến độ.
- PendingIntent immutable, explicit, phân biệt widget/action/token opaque. PLAY/PAUSE idempotent; kiểm lại id/owner/generation/commands; previous-media-item và next-media-item qua wrapper. Widget không start/bind service hoặc tạo player/queue mới. Vùng mở app tiêu thụ intent một lần khi controller sẵn sàng.
- Ảnh: placeholder ngay khi đổi bài, sau đó artworkData/ảnh nhúng file owner/MediaStore hoặc picked URI có quyền. Một worker, byte/bounds/sample giới hạn, bitmap cạnh dài ≤144px, kiểm owner/epoch/generation trước publish; đóng retriever/descriptor, không network/avatar nguồn/cache đĩa/observer TDLib mới.
- MinHeight120dp đủ bìa56 + nút48 + padding16; compact bìa40/margin6. QA Pixel Launcher chấp nhận 4x2 và resize3 cột. Compact nhỏ nhất/chữ lớn chưa kiểm trên launcher.

## Gate Ni
Logs/XML/manifest trong `docs/plans/artifacts/2026-11-03-widget-dang-phat/` (private/không commit).

| Lệnh / kiểm | Kết quả | Log |
|---|---|---|
| Targeted MeloWidgetPolicyTest | 12 test đạt, 0 skipped/lỗi | `a1-a6-policy-20261103.log` |
| testShareableDebugUnitTest + testShareableReleaseUnitTest | BUILD SUCCESSFUL 3m15s; mỗi variant 202 suite, **2.737 tổng / 2.716 passed / 21 skipped / 0 failure / 0 error** (XML) | `a7-tests-r2-20261103.log` |
| assembleShareableDebug + assembleShareableRelease | BUILD SUCCESSFUL 2m13s; Ni config=false, không cài/bàn giao các APK Ni | `a7-assemble-20261103.log` |
| -PmeloOpen=true assembleShareableDebug | BUILD SUCCESSFUL26s; package app.melovault.player.open vc121/0.6.96-open | `a7-open-20261103.log` |
| assemblePersonalDebug | BUILD SUCCESSFUL28s; package com.bsdung.musicassistantcar vc61/0.6.40 | `a7-personal-20261103.log` |
| Merged manifests | Thường Debug/Release và .open đúng một widget provider, exported=false, APPWIDGET_UPDATE/appwidget.provider; Personal vắng widget | `manifest-*.xml` |
| Rà factory/flavor, diff --check | Đạt; không quyền mới; service diff chỉ metadata/widget hooks, không thay cấu hình ExoPlayer/audio/sync | Git diff, `a-source-checkpoint-20261103.json` |

Các lượt chưa đạt được giữ log, không tính là xanh: targeted đầu bị lệch class/test vì tôi bổ sung test giữa lượt compile (`a1-policy-20261103.log`); full đầu Debug 2.737 test/21 skipped/1 failure, Release chưa chạy (`a7-tests-20261103.log`). Guard cũ cấm mọi receiver; đã sửa chỉ cho phép duy nhất widget không exported, vẫn cấm boot/receiver khác và sync service/worker mới. Sau đó full Debug/Release xanh như bảng.

## Build Tiny và APK QA
- Bundle đủ lịch sử chỉ ref widget: 13.829.530B, SHA256 `08AE1D6CC063D529A067D3E9F5D6A731D4D97F19273C281A67744D452DBA58B3`; git bundle verify đạt, Tiny hash khớp.
- Clone riêng `C:/Bots/MeloVault-WIDGET-IsolatedQA-20261103-49ed94c`, source49ed94c, sạch/detach. Chỉ chuyển bundle/script không bí mật qua `_qa_temp`, không ghi đè repo Tiny gốc hoặc EQ.
- Wrapper đầu dừng vì PowerShell coi stderr tiến độ Git là exception. Đối chiếu clone thực đã xong/sạch, không reset/clone đè. R2 PID19436 khởi chạy11:36:46+07, nền ẩn; **BUILD SUCCESSFUL3m10s, exit0, config=true**.
- Config qua symbolic link nội bộ Tiny tới file đã cấp trong clone QA Cài đặt; không mở/in/copy file hoặc giá trị; link nhiệm vụ đã gỡ sau build, file gốc còn nguyên.

| Thuộc tính candidate | Đã kiểm |
|---|---|
| Loại / package | Debug Shareable thường / app.melovault.player |
| Phiên bản / source | vc121, 0.6.96-beta / 49ed94ccbd73abb2d9443828ede26c205e1e9e56 |
| Bytes / config | 33.839.028 / true |
| SHA256 | `3C37DBE24337443E417F0B53CC27198FE3AE8D28048C84615586BB3B16221F75` (Tiny → Ni → installed AVD khớp) |
| Cert SHA256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`; apksigner verify thành công |
| File Ni | `docs/plans/artifacts/2026-11-03-widget-dang-phat/MeloVault-widget-QA-v121-49ed94c-3C37DBE2.apk` |
| File Tiny | clone riêng `app/build/outputs/apk/shareable/debug/app-shareable-debug.apk` |

Hồ sơ/link APK và lỗi tải chính xác: [apk-test](2026-11-03-widget-dang-phat-apk-test.md). **QA CANDIDATE — CHƯA NGHIỆM THU**, không phát hành/Beta mới chính thức.

## QA thực tế và giới hạn
AVD nhiệm vụ riêng `MeloVault_Widget_20261103`, emulator-5586=device, sdk_gphone64_x86_64/Google, Android36, Pixel Launcher; ABI x86_64/arm64-v8a. Package chưa cài và không media session trước cài, không downgrade/chữ ký cũ/EQ. Một lệnh install-r Success; base.apk SHA khớp. Không QA trên thiết bị thật.

| Q | Thao tác/kỳ vọng | Thực tế / bằng chứng | Kết quả |
|---|---|---|---|
| Q1 | Picker → kéo widget; chạm khi chưa bài | Đúng tên/preview4x2; nốt nhạc/tên/hint, 3 nút disabled qua UI tree; cold-open app vào đăng nhập, MediaSession NONE(0), không tự phát | Đã kiểm phần trống; ảnh07/08/09/10 |
| Q2 | A có bìa/ca sĩ, pause/resume | Hai âm thử600s A/B đã scan id19/20, A có JPEG nhúng biết trước và tag Nghe si QA A, B raw artist unknown; không thể chọn/phát vì app khóa mở tệp khi chưa đăng nhập | **BLOCKED phiên QA; bìa thật lên launcher chưa chứng minh** |
| Q3 | Next/previous/rapid tap, biên queue, B thiếu bìa/ca sĩ | Pure policy/generation/NextAware rules đã chạy JVM; không có playback/runtime fixture Telegram được cấp | BLOCKED runtime, gồm next Telegram chưa tải |
| Q4 | Mở Deck đúng prefs khi có bài / empty | Empty cold-open không tự phát đã kiểm; mở khi đang phát/tạm dừng, app nền có bài và legacy player chưa kiểm | BLOCKED phần có bài |
| Q5 | Hai instance/kích thước | Resize4→3 cột thực bounds952→703px, chữ/nút không đè; instance thứ2 ở Home page mới, IDs2/3 cùng tồn tại, empty đúng | Đã kiểm phần trống; ảnh15/17 + dump hai ids; đồng bộ khi đổi bài/API30/31/chữ lớn chưa kiểm |
| Q6 | Xóa cuối, music vẫn chạy, thêm lại | Chưa có phiên phát; thao tác kéo thử chưa chứng minh xóa (dump vẫn2 ids), không ghi PASS. Cleanup sau đó xóa AVD dùng riêng | BLOCKED QA vòng đời có bài; không lấy cleanup thay chứng cứ |

Không nhập account/OTP, chuyển TDLib session/cache hoặc sửa auth/API điều khiển để né gate; không dùng Sony. **CHƯA XÁC NHẬN ÂM THANH THỰC**; pause/resume/skip/performer/artwork runtime/độ trễ1s/đồng bộ notification và hai instance chưa kiểm. Log AndroidRuntime thu trong QA không có FATAL EXCEPTION, chỉ chứng minh phạm vi đã mở.

## Drive, ảnh và trạng thái bàn giao
- [Thư mục Ni N riêng tư](https://drive.google.com/drive/folders/1n7KhjK_3mr3LlBJ74ZHaD6ZhrVuO7K8g); folder/APK/7 PNG đều metadata shared=false, chỉ quyền owner Ni N. Không public.
- Upload APK thành công nhưng authenticated fetch bị403 `cannotDownloadAbusiveFile`, thông báo Google đánh dấu malware/spam. **BLOCKED — CHƯA BÀN GIAO APK TẢI ĐƯỢC**. Không retry acknowledgeAbuse, né cảnh báo hoặc dùng hosting khác. Drive bytes khớp; SHA256 Drive chưa đối chiếu vì không tải được.
- Ảnh tổng hợp6 khung960×1460 đã mở kiểm tại Ni, tải authenticated từ Drive thành công; 6 ảnh gốc + tổng hợp có metadata/bytes khớp bản Ni. SHA256 tính tại Ni trước upload; connector không trả remote checksum nên không ghi đã so SHA Drive.
- [Link-index ảnh](2026-11-03-widget-dang-phat-hinh-nghiem-thu.md); artifact `00_tong_hop.png`, hash/metadata manifests `b-delivery-file-hashes-20261103.json`, `b-drive-verification-20261103.json`. Ảnh chỉ chứng minh các ca trống/resize đã ghi, không chứng minh bìa thật/phát nhạc.

## Cleanup đã xác minh
- AVD đã tắt màn trong finally, lần đo cuối Display State=OFF, sau đó emu kill đúng tên/serial; đợi transport offline biến mất mới dùng avdmanager xóa đúng thư mục/ini đã resolve dưới `.android/avd`. Xác minh cả hai vắng, ADB không còn emulator5586. App/widget/media thử trong AVD đã bị xóa cùng môi trường disposable.
- Xóa đúng ba APK Ni config=false/.open/Personal vừa tạo để kiểm biên dịch và file UI tap tạm; giữ compile cache/XML/log cần đối chiếu.
- Tiny: gỡ đúng symbolic link config trong clone nhiệm vụ (đã kiểm LinkType/Target), file config gốc vẫn tồn tại; xóa bundle chuyển tạm và hai build wrapper tại `_qa_temp` sau khi xác minh source/hash/job đã hết. Không còn job build/test nhiệm vụ chạy.
- **Giữ có chủ đích:** source clone Tiny sạch/detach + candidate gốc; candidate Ni; source bundle Ni, logs/exit/metadata/ảnh và hai fixture vô danh để sơ bộ/tiếp tục B khi có phiên QA. File APK Drive bị chặn giữ owner-only làm hồ sơ lỗi; không giới thiệu là tải được. Đây là chứng cứ/source/phần việc còn chặn, không phải tài nguyên test đang chạy.
- Không dừng daemon Gradle/Kotlin hoặc ADB dùng chung; không sửa/xóa AVD cũ, repo nguồn/EQ, dữ liệu người dùng, config gốc hoặc APK cũ. Không cleanup nào còn cần tác vụ nền.
- `AVD_SCREEN_END=OFF (đo trước emu kill), AVD_DELETED=true`; `SONY_SCREEN_END=không dùng trong nhiệm vụ widget`; `SHANLING_SCREEN_END=không dùng, máy đã bán`. Không suy OFF trên máy không đo.

## Bước tiếp / bàn giao
- Còn chặn: QA bài đang phát/bìa thật và Q6 cần môi trường QA đăng nhập được chủ dự án giao, không phải Sony EQ; tải APK Drive bị policy Google chặn. Không mở rộng auth/TDLib/package/cert/version hoặc thay phương tiện upload để xử lý.
- [Checkpoint đầy đủ](2026-11-03-widget-dang-phat-ban-giao.md), [prompt phiên sơ bộ mới](2026-11-03-widget-dang-phat-prompt-nghiem-thu-so-bo.md). Sơ bộ chỉ kiểm nhanh source/hồ sơ/giới hạn, không tự nghiệm thu chức năng khi các gate còn BLOCKED. Chủ dự án quyết định nghiệm thu cuối; không có chỉ thị merge/push widget.
