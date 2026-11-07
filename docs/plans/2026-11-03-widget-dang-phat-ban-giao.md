# Bàn giao — Widget Đang phát

## Checkpoint sơ bộ sau REWORK · 07/11/2026 · Claude Code, Opus 5.5

**SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN**, vòng 2 của [biên bản](2026-11-03-widget-dang-phat-nghiem-thu-so-bo.md). **CHỜ CHỦ DỰ ÁN NGHIỆM THU.** Chủ dự án muốn gom widget vào bản beta sau khi duyệt. Sony đang cài APK `58BD55F0…`, widget để sẵn trên Home, nhạc tạm dừng, Display State=OFF. **Trước khi merge:** bỏ tên bài trong `-bao-cao.md` (O3), giải xung đột `HANDOFF.md`, chạy lại gate vì bản gộp khác cả hai nhánh.

## Checkpoint cuối REWORK · 06/11/2026 · Claude Code, Opus 5.5

**QA CANDIDATE — CHƯA NGHIỆM THU.** Thực thi xong, chờ sơ bộ và chủ dự án. Chưa merge, chưa push.

| Mục | Giá trị |
|---|---|
| Worktree / nhánh | `C:/Users/ntdun/.codex/worktrees/melovault-widget/MeloVault-E91-Shareable` / `dot/2026-11-03-widget-dang-phat` |
| Baseline | `f82185ba2ef17fce97d4680de2cd2dc86b2e1b4a` (vc122 đã ĐẠT) |
| Source APK | `be61487dc387b05db6798cdaf74ae222b07e7eef`; các commit sau chỉ là hồ sơ. Kiểm: `git diff be61487..HEAD -- app/` phải rỗng |
| APK | vc122 / `0.6.97-beta`, 33.937.332 B, SHA-256 `58BD55F0B5C22A256E6572516C277A3049A617F853A1830B30280F70AD48FD07`, đang cài trên Sony |
| File chưa commit | Không (artifact private bị `.gitignore`) |
| Job còn chạy | Không có job của nhiệm vụ. Daemon Gradle không dừng |
| Sony | Widget QA đã gỡ, nhạc tạm dừng, `SONY_SCREEN_END`: Display State=OFF |

| Bước | Trạng thái |
|---|---|
| Tích hợp `42be21c`, `f82185b`, giải xung đột | Xong |
| Sửa lỗi 1 (chặn Tiếp), lỗi 2 (bài chờ kết nối) | Xong, kiểm JVM và Sony |
| Gate Ni, build Tiny, gate APK, cài Sony | Xong |
| Q1–Q6 có bài trên Sony | Xong, [báo cáo](2026-11-03-widget-dang-phat-bao-cao.md) mục REWORK |
| Ảnh, APK trên Drive owner-only | Xong; tải APK bằng trình duyệt chưa thử |
| Sơ bộ độc lập | Chưa làm. [Prompt](2026-11-03-widget-dang-phat-prompt-nghiem-thu-so-bo.md) |
| Nghiệm thu chủ dự án | Chờ, [biên bản](2026-11-03-widget-dang-phat-nghiem-thu.md) |

**Việc sau ĐẠT:** ghi `*-nghiem-thu.md`, merge `--no-ff` vào `feature/e93-03d-y700-offline-cockpit` trong worktree `C:\Users\ntdun\.codex\worktrees\workflow-md-handoff\MeloVault-E91-Shareable`, cập nhật mục đầu `HANDOFF.md`, rồi push theo chỉ thị 02/11. Kiểm lại nhánh tích hợp vì có thể đã đi tiếp sau `f82185b`. Không đụng `main`, không đẩy `dot/*`.

**Cần chủ dự án để ý:** widget trên launcher Xperia mặc định 3×3, cao và trống nhiều ở giữa, chỉ kéo được ngang. Kế hoạch ghi 4×2. Chưa sửa vì ngoài phạm vi 2 lỗi.

## Checkpoint sơ bộ · 03/11/2026 · Codex (lịch sử)

**SƠ BỘ: CẦN SỬA. Chặng B vẫn BLOCKED.** HEAD nhận bàn giao `8d78b19`, source APK `49ed94c`. Phiên sơ bộ đã kết thúc trên nhánh nhiệm vụ. [Biên bản, vị trí mã và cách kiểm lại](2026-11-03-widget-dang-phat-nghiem-thu-so-bo.md).

| Bước | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| Đối chiếu source/APK và gate Ni | Xong | SHA-256 candidate Ni khớp Tiny. Đếm lại XML, đối chiếu manifest bốn biến thể, ảnh và metadata lưu tại Ni. |
| Điều khiển và khởi động nguội | Cần sửa | Đổi metadata cùng bài xóa chặn NEXT sớm (đã kiểm helper Debug). Khi cấu hình queue đến trước lúc service tồn tại, bài Telegram chờ phát có thể thiếu owner/queue ở widget (phân tích tĩnh). |
| B / tải APK | BLOCKED | Thiếu QA đăng nhập cho Q2–Q4, Q5 có bài và Q6. Ảnh hiện có chỉ bao phủ phần trống. Drive owner-only trả 403 cannotDownloadAbusiveFile. |
| Bàn giao chủ dự án | Chờ quyết định | Nếu giao sửa, phiên thực thi mới đọc hai mục trong biên bản, kiểm lại trên cùng nhánh và cần môi trường QA được cấp. |

Phiên sơ bộ chỉ đổi biên bản và checkpoint này, không có job nhiệm vụ để tiếp quản. Lấy SHA hồ sơ bằng lệnh trong bảng định danh dưới đây.

## Lịch sử bàn giao thực thi
Checkpoint cuối phiên thực thi · 03/11/2026 · Codex; model/effort không được công cụ phiên cung cấp. Code/build/QA đã dừng; chỉ hoàn tất commit hồ sơ và kiểm Git. Không agent phụ hoặc tác vụ nền nhiệm vụ.

**A hoàn tất gate Ni; B BLOCKED ở QA bài đang phát và tải APK Drive. Chưa nghiệm thu, merge hoặc push.**

## Định danh
| Mục | Giá trị đã kiểm |
|---|---|
| TASK_ID / nhánh | `2026-11-03-widget-dang-phat` / `dot/2026-11-03-widget-dang-phat` |
| Worktree Ni | `C:/Users/ntdun/.codex/worktrees/melovault-widget/MeloVault-E91-Shareable` |
| Baseline | `b90834f44457b60a9732e59e59c1bc9c971368c3` |
| Kế hoạch / HEAD đầu phiên | `192170642163c69229c8945b54c26f7ac2d5a80f` |
| Source commit / APK | **`49ed94ccbd73abb2d9443828ede26c205e1e9e56`** |
| Commit hồ sơ/checkpoint | Lấy `git log -1 --format=%H -- docs/plans/2026-11-03-widget-dang-phat-ban-giao.md`; tránh SHA tự tham chiếu. Các commit sau source chỉ hồ sơ. |
| Git đầu phiên | Sạch, đúng nhánh/baseline; fetch github, ahead1/behind0 so remote tích hợp; chưa upstream; không code lạ nhận bàn giao |
| Remote | `github` → `https://github.com/niquitin/melovault.git`; chưa push nhiệm vụ |

[Kế hoạch](2026-11-03-widget-dang-phat.md) · [Báo cáo](2026-11-03-widget-dang-phat-bao-cao.md) · [APK và lỗi tải](2026-11-03-widget-dang-phat-apk-test.md) · [Ảnh](2026-11-03-widget-dang-phat-hinh-nghiem-thu.md) · [Prompt sơ bộ mới](2026-11-03-widget-dang-phat-prompt-nghiem-thu-so-bo.md).

## Quyết định/phạm vi còn hiệu lực
- Widget Home: bìa thật nếu có, tên, ca sĩ thật hoặc unknown; Previous / PLAY-PAUSE / Next; source Shareable thường/.open, Personal không provider. Không EQ, schema/songKey/package/signer/version/quyền/TDLib auth mới.
- Sony giữ candidate EQ: **không cài, QA hoặc điều khiển Sony**. Shanling đã bán theo CLAUDE.md/BACKLOG, tham chiếu trong plan lỗi thời.
- Không tự đổi model/giao agent, nghiệm thu chính thức, merge, push/public hoặc chọn nhiệm vụ mới. Phiên mới theo vai sơ bộ do chủ dự án mở/chọn model; tiếp B cần môi trường QA được giao, không né auth hay chặn Drive.

## Bảng checkpoint
| Bước | Trạng thái | Bằng chứng / còn lại |
|---|---|---|
| A0 | Xong | Đọc luật/chỉ mục/quy trình/plan và kiểm Git |
| A1–A6 | Xong gate Ni | Policy/generation/fence12 test; performer riêng; provider/RemoteViews; sessionPlayer/NextAware commands; ảnh worker owner/epoch/generation. Runtime bìa/transport còn BLOCKED |
| A7 | Xong | Mỗi Debug/Release 202 suite,2737 tổng,2716 passed,21 skipped,0 lỗi. Assemble Debug/Release/.open/Personal xanh; manifest flavor và diff-check sạch |
| B bundle/clone/build | Xong gate kỹ thuật | Bundle SHA Ni/Tiny khớp; clone sạch/detach49ed94c; Tiny config=true, build exit0 |
| B gate APK/cài | Xong kỹ thuật | Package/version/cert/hash/bytes kiểm; cài AVD mới install-r Success, installed SHA khớp |
| Q1 | Đã kiểm | Picker/preview4x2, add widget trống, nút disabled, cold-open không tự phát |
| Q2–Q4 playback/artwork | **BLOCKED** | AVD chưa account QA đăng nhập; app khóa cả mở tệp trước đăng nhập. Bìa thật lên launcher/performer/pause-resume/skip/next Telegram/Deck có bài chưa chứng minh |
| Q5 | Một phần | Resize4→3 cột, hai ids2/3 tồn tại trên hai Home page; chưa đổi bài/chữ lớn/API30/31 |
| Q6 | **BLOCKED** | Chưa phiên phát để kiểm xóa widget cuối giữ music/thêm lại bài; kéo thử chưa xóa qua dumpsys, không ghi PASS |
| Ảnh Drive | Xong | Tổng hợp6 khung +6 gốc, owner-only/shared=false/bytes khớp; tổng hợp mở Ni và authenticated fetch thành công |
| Tải APK Drive | **BLOCKED — CHƯA BÀN GIAO** | Upload owner-only xong, authenticated fetch403 cannotDownloadAbusiveFile. Không coi link APK là tải được, không retry abuse/public/hosting khác |
| Cleanup | Xong tài nguyên không cần | AVD và file tạm đã dọn; retained rõ bên dưới, không job nhiệm vụ chạy |

## APK / bằng chứng
- **QA CANDIDATE — CHƯA NGHIỆM THU**: Debug thường `app.melovault.player`, vc121 / `0.6.96-beta`, config=true, 33.839.028B.
- SHA256 `3C37DBE24337443E417F0B53CC27198FE3AE8D28048C84615586BB3B16221F75`; certificate `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`. Tiny→Ni→installed AVD hash khớp, apksigner verify đạt.
- Ni candidate: `docs/plans/artifacts/2026-11-03-widget-dang-phat/MeloVault-widget-QA-v121-49ed94c-3C37DBE2.apk`.
- Tiny clone/candidate: `C:/Bots/MeloVault-WIDGET-IsolatedQA-20261103-49ed94c`, `app/build/outputs/apk/shareable/debug/app-shareable-debug.apk`; sạch/detach49ed94c.
- Tiny device `DESKTOP-HNP4GVR`, DC id `9c7f081b-6e1c-4221-ac78-da470bab1e5d`. Build r2 PID19436 đã kết thúc exit0; log/exit/metadata giữ tại `C:/Bots/_qa_temp/widget-20261103-49ed94c-r2.*`; không build đang chạy.
- Source bundle Ni `widget-49ed94c-20261103.bundle`,13.829.530B, SHA256 `08AE1D6CC063D529A067D3E9F5D6A731D4D97F19273C281A67744D452DBA58B3`, git bundle verify đạt; bản chuyển tạm Tiny đã dọn.
- Logs Ni/XML/manifest/ảnh/source hash/Drive metadata/cleanup proof: `docs/plans/artifacts/2026-11-03-widget-dang-phat/` (không commit). Lượt test/build chưa đạt trước được ghi đúng trong báo cáo, không tính xanh.
- [Drive riêng tư](https://drive.google.com/drive/folders/1n7KhjK_3mr3LlBJ74ZHaD6ZhrVuO7K8g), [ảnh tổng hợp](https://drive.google.com/file/d/1L5oFn0s003lQXao2Ibt3mWwxOb5ucuY2/view?usp=drivesdk). Chi tiết link APK bị chặn ở apk-test.
- **Chưa xác nhận âm thanh thực hoặc bìa thật lên launcher.** Tests JVM, MediaSession NONE và ảnh empty không thay thế QA đang phát.

## Cleanup / ownership
- AVD mới riêng `MeloVault_Widget_20261103` / emulator-5586 / API36 / Pixel Launcher đã emu kill đúng tên, đợi transport biến mất, xóa directory/ini qua avdmanager sau kiểm đường dẫn không reparse/outside root. ADB không còn serial này; hai widget/app/media thử xóa cùng AVD.
- `AVD_SCREEN_END=OFF` đo trước kill, `AVD_DELETED=true`; `SONY_SCREEN_END=không dùng`, `SHANLING_SCREEN_END=không dùng, đã bán`. Không đo/suy trạng thái màn Sony.
- Đã dọn ba APK Ni vừa tạo cho compile (thường Release/.open Debug/Personal Debug), UI tap temp; Tiny symbolic link config + bundle chuyển tạm +2 wrapper build. Kiểm link đúng trước xóa, config gốc Tiny vẫn còn, clone nguồn sạch.
- Giữ có chủ đích source clone/candidate Tiny, candidate/bundle Ni, test XML/log/manifest/ảnh/metadata và fixture A/B vô danh để sơ bộ/tiếp tục gate B. APK Drive bị chặn giữ private như hồ sơ lỗi, không gọi tải được. Không giữ emulator/server/watcher/tunnel/test job.
- Không dừng daemon Gradle/Kotlin/ADB dùng chung, xóa config nguồn/dữ liệu người dùng/APK cũ/AVD khác, hoặc tác động EQ. `cleanup-verified-20261103.json` ghi kiểm Ni; log/exit Tiny còn để audit.

## File chưa commit và bước phiên mới
- Source49ed94c đã khóa. Lúc viết checkpoint chỉ thay hồ sơ TASK_ID, prompt sơ bộ và chỉ mục HANDOFF; sẽ commit explicit các file này. Phiên mới kiểm Git sạch/commit hồ sơ và `git diff 49ed94c..HEAD -- app/src app/build.gradle` phải rỗng, không nhận file lạ.
- Bước kế được giao: **phiên sơ bộ mới** đọc prompt liên kết, kiểm nhanh source/diff/log/metadata và ghi đúng giới hạn BLOCKED. Không cần chạy lại toàn bộ gate khi source không đổi; không sửa code/build hoặc tự ghi PASS.
- Để hoàn tất B sau này: cần môi trường QA đăng nhập do chủ dự án giao (không phải Sony EQ), thực hiện Q2–Q4/QA bìa thật/Q6, và giải quyết việc Drive chặn tải theo chỉ thị riêng. Không tự đăng nhập/chuyển TDLib, đổi auth/package/cert/version/hosting để né gate.
