Thực thi: Codex, model/effort không được công cụ phiên cung cấp; phiên 03/11/2026, chặng A → B.

# Báo cáo widget Đang phát

## Phạm vi và trạng thái
- TASK_ID `2026-11-03-widget-dang-phat`; nhánh `dot/2026-11-03-widget-dang-phat`.
- Worktree Ni: `C:\Users\ntdun\.codex\worktrees\melovault-widget\MeloVault-E91-Shareable`.
- Baseline `b90834f44457b60a9732e59e59c1bc9c971368c3`; kế hoạch/HEAD bắt đầu `192170642163c69229c8945b54c26f7ac2d5a80f`.
- Người dùng giao thực thi kế hoạch trong phiên này; không tự đổi model, giao agent, nghiệm thu, merge hoặc push.

| Bước | Trạng thái | Bằng chứng / phần tiếp |
|---|---|---|
| A0 | Xong | Git sạch, đúng nhánh/baseline; diff --check sạch. Đã đọc luật, chỉ mục, quy trình, kế hoạch và bàn giao. |
| A1 | Xong gate Ni | Policy trạng thái, generation, NEXT fence, identity và render gate; 12 test mới đạt. |
| A2 | Xong gate Ni | Extra performer riêng; Telegram factory/cached/progressive/recovery/QueueTrack và LocalSong raw tag. Artist/MIME/size/duration cũ giữ nguyên. |
| A3 | Xong gate Ni, chờ launcher | Provider Shareable, RemoteViews thường/compact, preview, receiver không exported, không quyền mới/poll. |
| A4–A6 | Xong gate Ni, chờ QA | Đọc sessionPlayer; hook available commands; receiver hẹp, token opaque; ảnh worker cục bộ, owner/epoch/generation, tối đa 144px. |
| A7 | Xong | Ma trận tests/build + manifest đạt; source commit sau checkpoint này. |
| B | Chưa làm | Sau source commit: bundle/Tiny build; gate QA riêng, không dùng Sony. |

## A0 — khóa phạm vi, 03/11/2026 11:10 +07
- Remote `github`: `https://github.com/niquitin/melovault.git`; nhánh nhiệm vụ chưa có upstream.
- Fetch thành công. So với `github/source/feature-e93-03d-y700-offline-cockpit`: HEAD ahead 1 / behind 0 (commit kế hoạch).
- Không thấy thay đổi/commit lạ trên nhánh widget; các process công cụ khác không bị dừng hoặc can thiệp.
- Không chỉnh worktree/nhánh EQ. Không dùng ADB Sony hoặc thay candidate EQ.
- Plan còn nhắc Shanling; CLAUDE.md mục 3 và BACKLOG xác nhận máy đã bán. Không coi Shanling là QA khả dụng. B sẽ khảo sát emulator riêng; thiếu điều kiện ghi BLOCKED đúng bước.
- Chưa tạo test server, emulator, bundle, APK hay log. Cleanup A0: không có tài nguyên nhiệm vụ cần dọn.

## Giới hạn và bước tiếp
- Chưa kiểm launcher, bìa thật, Telegram next hoặc âm thanh thực.
- Tiếp A1; cập nhật checkpoint sau từng mốc. Chỉ build/cài/upload khi gate tương ứng đạt.

## Checkpoint mã A1–A6
- Đã tạo `MeloWidgetPolicy`, `MeloNowPlayingWidgetProvider`, `MeloWidgetSession`, `MeloWidgetArtwork`; tài nguyên widget chỉ trong Shareable; thêm test policy.
- Controls dùng PLAY/PAUSE rõ ràng, previous-media-item và next-media-item qua wrapper. Token đổi khi bài/owner/metadata đổi; NEXT trùng token có hàng rào 2 giây.
- Vùng mở app tiêu thụ yêu cầu đúng một lần khi controller sẵn sàng; không tạo queue hoặc tự phát từ widget.
- Bìa: placeholder trước, artworkData / ảnh nhúng file owner / MediaStore thumbnail. Không avatar nguồn, TDLib request, download, timer hoặc cache đĩa. Worker đóng retriever/descriptor và bỏ kết quả cũ.
- Layout đặt minHeight 120dp để đủ bìa 56dp + vùng nút 48dp + padding 16dp; bản compact thu bìa 40dp/viền chữ 6dp. Cần hiệu chỉnh nếu ảnh launcher chứng minh có vấn đề.
- Lượt targeted đầu `a1-policy-20261103.log` **BUILD FAILED**: test được bổ sung sau khi production classes đã compile nên thấy LocalSong cũ, `trustedPerformer` unresolved. Không tính là test đạt; đã kết thúc. Không sửa source khi lượt gate kế đang chạy.
- Lượt targeted kế đang chạy ở Ni, exec session `21737`, log `docs/plans/artifacts/2026-11-03-widget-dang-phat/a1-a6-policy-20261103.log`. Source chưa commit.
- Chưa tạo APK/bundle/emulator; các file mới/chỉnh sửa đều thuộc nhiệm vụ, không có code EQ.

## A7 — ma trận Ni (đang chạy)
- Targeted lần lại **BUILD SUCCESSFUL**: 12 test widget, 0 skipped, 0 failure/error.
- Full lần đầu: Debug 2.737 test, 21 skipped, 1 failure (Release chưa chạy). `P5ReadinessInertnessTest.a08` cũ cấm mọi receiver, xung đột provider đã được duyệt trong A3.
- Đã chỉnh guard a08 chỉ cho phép duy nhất receiver Home widget không exported; vẫn cấm receiver khác, boot action, service/worker tự chạy cho sync. Không nới gate sync.
- Full lượt 2 đang chạy exec `25630`, log `a7-tests-r2-20261103.log`. Không sửa code trong khi gate chạy.
- Chuẩn bị B độc lập: Desktop Commander xác nhận Tiny online; không có Java/Gradle process hiện tại qua truy vấn. Ni không có ADB device, không có emulator đang chạy. Android 36 Google APIs x86_64/WHPX đã có.
- Đang tạo AVD nhiệm vụ riêng `MeloVault_Widget_20261103`; không dùng/sửa AVD có sẵn của dự án khác. Chưa boot/cài. Tài nguyên này phải dừng/dọn khi QA kết thúc.

### Kết quả A7 đã xác minh
- Full lượt 2 **BUILD SUCCESSFUL** 3m15s: mỗi Debug/Release 202 suite, 2.737 test, **2.716 passed / 21 skipped / 0 failure / 0 error** (đếm XML ngay sau lượt chạy).
- Assemble Shareable Debug/Release + boolean Telegram config đang chạy exec ở Ni, log `a7-assemble-20261103.log`; tiếp .open và Personal riêng, lưu merged manifest trước khi đổi cờ.
- AVD riêng đã boot: API36 x86_64, Pixel Launcher `com.google.android.apps.nexuslauncher/.NexusLauncherActivity`, serial `emulator-5586`, process khởi chạy PID22620. Không có thiết bị thật Ni.
- Fixtures A/B được tạo bằng FFmpeg, âm thử 600s, không nội dung cá nhân. A có bìa JPEG nhúng đã biết (3.349 bytes) và ca sĩ `Nghe si QA A`; B thiếu bìa/ca sĩ. Chưa cài app hoặc coi đây là QA đã đạt.

### Khóa chặng A
| Lệnh / kiểm | Kết quả | Log private trong artifacts TASK_ID |
|---|---|---|
| testShareableDebugUnitTest + testShareableReleaseUnitTest | BUILD SUCCESSFUL; mỗi biến thể 2.737 tổng / 2.716 đạt / 21 skipped / 0 lỗi | `a7-tests-r2-20261103.log` |
| assembleShareableDebug + assembleShareableRelease | BUILD SUCCESSFUL 2m13s; config Ni=false | `a7-assemble-20261103.log` |
| -PmeloOpen=true assembleShareableDebug | BUILD SUCCESSFUL 26s; package `app.melovault.player.open`, vc121/0.6.96-open | `a7-open-20261103.log` |
| assemblePersonalDebug | BUILD SUCCESSFUL 28s; package `com.bsdung.musicassistantcar`, vc61/0.6.40 | `a7-personal-20261103.log` |
| Merged manifests | Shareable Debug/Release và .open có đúng 1 widget provider, exported=false, APPWIDGET_UPDATE và appwidget.provider; Personal không có widget | `manifest-*.xml` |
| Rà source + diff --check | Đạt; mọi factory/QueueTrack đã rà. Service diff chỉ metadata + widget hooks, không thay ExoPlayer/audio/sync/TDLib architecture | Git diff; `a-source-checkpoint-20261103.json` |

- Không thêm quyền/service mới/network/poll/observer TDLib/ghi metadata widget ra đĩa. Không đọc/copy bí mật. Không thay package/signer/version/schema/songKey/EQ.
- Chặng A đủ checkpoint đổi model; B chưa QA. Không có build Ni đang chạy. AVD nhiệm vụ đang giữ nguyên để QA B, màn đã xác minh OFF sau chuẩn bị.
