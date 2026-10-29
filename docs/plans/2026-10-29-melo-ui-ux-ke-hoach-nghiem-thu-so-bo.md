# Nghiệm thu sơ bộ — Melo UI/UX, vòng 1

**SƠ BỘ: CẦN SỬA.** Hai lỗi UI trong S7 có bằng chứng phù hợp với source; giữ nguyên mọi BLOCKED. Không kết luận S6/P1 hoàn tất, không PASS chính thức.

- TASK_ID: `2026-10-29-melo-ui-ux-ke-hoach`.
- Người kiểm: Codex; model/effort cụ thể không có metadata xác thực. Phiên được chủ dự án giao riêng vai sơ bộ sau thực thi, ngày 29/10/2026, khoảng 22:39–22:45 +07.
- Worktree: `C:\Users\ntdun\.codex\worktrees\melo-ui-ux-plan\MeloVault-E91-Shareable`.
- Nhánh: `dot/2026-10-29-melo-ui-ux-ke-hoach`; HEAD hồ sơ đầu phiên `d89459e3398addbebd6456856bb2740ac2b55dbe`.
- Baseline `7f3cca874e525956a10f94f8caf577c6cc80f815`; plan `f912ef5eca8574deb304872af73a2f7ad5856f95`.
- Source APK `278f0d277aeb077bb938cf76f0e055c5058dd996`; tree `a7713aaa4321b6fa7c637db687c9061aca7f747d`.
- Chính thức: **CHỜ CHỦ DỰ ÁN NGHIỆM THU**. Cần chủ dự án giao REWORK cho hai lỗi dưới đây; phiên này không tự mở sửa.

## 1. Phạm vi và cách kiểm

Đọc luật 29/10 tại `c9a1c09:CLAUDE.md` mục 0, quy trình và `PROMPT_NGHIEM_THU_SO_BO.md` cùng ref, chỉ mục HANDOFF/BACKLOG, [kế hoạch mục 6](2026-10-29-melo-ui-ux-ke-hoach.md), [phụ lục S2/S5/S7](2026-10-29-melo-ui-ux-ke-hoach-chi-tiet.md), [báo cáo](2026-10-29-melo-ui-ux-ke-hoach-bao-cao.md), [bàn giao](2026-10-29-melo-ui-ux-ke-hoach-ban-giao.md), [APK-index](2026-10-29-melo-ui-ux-ke-hoach-apk-test.md) và [ảnh-index](2026-10-29-melo-ui-ux-ke-hoach-hinh-nghiem-thu.md). Dùng prompt **sơ bộ mới** ở đầu bàn giao, không chạy lại prompt build S7 lịch sử.

Git đầu phiên sạch, đúng HEAD/nhánh chủ dự án chỉ định, diff-check sạch; không thấy Java/Gradle đang chạy trên Ni. Xem log và diff-stat từ baseline: 37 file; source → HEAD hồ sơ chỉ đổi 4 file Markdown. Đọc có chọn lọc diff/mã host, browse/capture/restore, Save/writer, feedback/retry, download facts, queue/service và các test liên quan. Không audit toàn repo hoặc chứng nhận không còn lỗi khác.

| Tự kiểm lại trong phiên sơ bộ | Kết quả |
|---|---|
| SHA-256/bytes hai APK nội bộ Ni | Khớp bảng ở mục 2 |
| Manifest `S6-source-final-20261029_165358/source.json` | 14/14 file khớp SHA-256 hiện tại |
| Cộng XML S6 độc lập | Mỗi Debug/Release: 178 suites, 2261 tổng; **2240 passed /21 skipped /0 failed /0 errors** |
| Hash ảnh tổng hợp v2 + xem ảnh thật | Khớp `F9561AB73078F5A9D12136C0070FF1D9703B9C27E9314339D5D3481D361F0107`; thấy lỗi nhãn và thay đổi vị trí danh sách |
| Đọc XML 22/23 và 3 vòng 25, so nội dung hàng đầu bằng hash, không xuất tên bài | Trước/Back giống nhau; sau xoay khác hàng trước và lặp giống nhau cả 3 vòng; query giữ |
| Đối chiếu snapshot media 15/16a/16b, 24/26, 17/18/19 | Phù hợp lỗi feedback, giữ player qua xoay và Save không phát/seek |

Tái sử dụng bằng chứng cũ: S6 test/build; `build-result.json`, `sony-install-gates.json`, `sony-final-audit.json`, snapshots/ảnh/XML S7 và kết quả Drive ghi lúc 17h. Không chạy Gradle/test/build mới, không cài APK, không thao tác ADB/Tiny/Drive. Không kiểm lại chữ ký/package trực tiếp từ APK trong phiên này; dùng metadata/gate S7 gắn với APK vừa kiểm hash. Không coi kết quả cũ là QA mới lúc 22h.

## 2. APK và truy vết

Cả hai theo metadata S7: `app.melovault.player`, vc121 / `0.6.96-beta`, Telegram config **true**, cert SHA-256 `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`. Debug từng cài Sony sau gate; Release chưa cài/QA UI.

| Biến thể | Bytes tự kiểm | SHA-256 tự kiểm |
|---|---:|---|
| Debug | 33560516 | `EB65BBFEDC851188ADAD28A36F374AC6B09878820060CEA0680F16D44146B133` |
| Release | 30769518 | `51DC50E4787CF127A439F479BA33BF953D6CCDEBF49E53FA942131FAB13BD300` |

Đường dẫn Ni/Tiny, bundle hai đầu, thời gian build và URL lưu trữ: [APK-index](2026-10-29-melo-ui-ux-ke-hoach-apk-test.md). **BLOCKED — CHƯA BÀN GIAO QUA DRIVE**: giữ lỗi HTTP403 `cannotDownloadAbusiveFile` (malware/spam); không thử né, đổi quyền, upload Release hay dùng APK khác. Link lưu trữ không phải link tải hoạt động. Phiên này không kết luận nguyên nhân cảnh báo Drive.

## 3. Hai lỗi UI xác nhận từ bằng chứng S7

### SB-01 — mất vị trí cuộn khi xoay, ảnh hưởng tiêu chí S2/QA2

- Tái hiện trong S7: Sony dọc, Thư viện có query, cuộn xuống → mở Deck → Back → xoay ngang/dọc 3 lần. Kỳ vọng giữ cùng bài neo và offset theo ngữ cảnh, không về đầu hoặc tự phát/seek.
- Thực tế: XML `22` và `23` cùng query, hàng đầu digest S7 `7EE449…F9DF`, bounds `[42,725][965,857]`; ngay vòng xoay 1 đổi thành `78BF51…9D72`, bounds `[42,1164][965,1353]`, lặp cả 3 vòng. Ảnh cho thấy header/thẻ nguồn mở lại. Query/scope còn giữ; snapshot 24/26 cùng media digest, state2/65943ms.
- Đã đọc lại XML gốc riêng tư, so hash các nhãn con của hàng đầu: trước/Back cùng nhau, sau xoay khác nhau. Hash tự tính dùng cách ghép nhãn riêng nên không dùng thay digest của hồ sơ. Dữ liệu đối chiếu S7: `qa2-anchor-comparison.json`.
- Vị trí cần điều tra: `TelegramPreviewActivity.kt:1021` (capture trước đổi cấu hình), `:5354` (detach/rebuild layer), `:6023` (save theo generation); `MeloLibraryPane.kt:196` (onScroll lưu vị trí), `:256`/`:270` (detach/capture), `:310`/`:405` (restore và post selection). Chưa có trace runtime đủ để chốt callback nào ghi đè neo; không khẳng định chỉ sửa một dòng là đủ.
- Test `MeloBrowseStateTest` kiểm bộ nhớ/locate thuần; không chứng minh vòng đời View/ListView và header khi xoay. REWORK cần test đường capture → detach → attach → layout/restore, rồi QA 3 vòng cả Current/Teal, giữ query/scope/bài neo/player. Phân biệt thay đổi bounds vì header với mất identity bài neo.
- Ảnh: [trước xoay](https://drive.google.com/file/d/1lJc_9c1520LNBQ4-SEqoREIsMX96cKx5/view?usp=drivesdk), [sau xoay](https://drive.google.com/file/d/1fOYhmSgB8kbLqlJK1Euj3lqZCKtWaZxH/view?usp=drivesdk). Link owner-only đã kiểm ở S7, không fetch lại trong sơ bộ.

### SB-02 — Deck còn “Tạm dừng” sau Next đang phát, ảnh hưởng S5a/QA5

- Tái hiện trong S7: bài dài đang paused → Next → chờ bài mới phát. Kỳ vọng feedback, queue và transport nhất quán với bài hiện tại.
- Thực tế: ảnh `16a` còn “Tạm dừng” nhưng nút là pause, thời gian `00:42 / 3:05:07`. Media `15` state3/0ms và `16a` state3/41867ms cùng digest bài mới; sau pause chủ động `16b` state2/54544ms. Đây là mâu thuẫn nhãn UI với playback facts, không phải bằng chứng âm thanh thật.
- Source chỉ ra một đường gây lỗi cụ thể: `TelegramPreviewActivity.kt:6316` → nhánh target đã có trong controller ở `:6341` seek/play rồi return, không bắt đầu attempt mới; `MeloPlayUiState.kt:54–56` bỏ observe khi key player khác attempt cũ; `:68` chỉ lọc feedback theo owner, nên PAUSED của A có thể còn hiển thị khi B phát. `TelegramPreviewActivity.kt:5883` trả presentation này; `MeloStudioDeck.kt:441` và `MeloLibraryPane.kt:1016` render lại nó. Deck có tick 1 giây nên không nên mặc định coi là thiếu refresh. Chưa có runtime trace chứng minh S7 đã đi đúng nhánh cached này; đây là cơ chế lỗi thấy trong mã, phù hợp triệu chứng.
- REWORK cần đồng bộ feedback khi đổi bài qua transport/cached Next/Previous, vẫn giữ ERROR/RESOLVING của yêu cầu B khi A còn phát, cooldown và guard owner/generation. Bổ sung ca paused A → cached B playing không có `begin(B)` và ca chuyển tự động/Previous; test hiện có mới kiểm pause/play cùng key và bảo toàn lỗi B. Không xóa mọi feedback chỉ vì player đang playing.
- Ảnh: [nhãn paused trong khi player phát](https://drive.google.com/file/d/1QNX_lyjc0ed2lgDcwn8gRqps83tyyEwy/view?usp=drivesdk); snapshot private `16a-next-settled-media.json`.

## 4. Kiểm nhanh các rủi ro còn lại

| Phần đọc | Nhận xét trong phạm vi sơ bộ |
|---|---|
| Host/browse/async | Có owner + generation, state theo scope, pending anchor và kiểm query/sort; lỗi SB-01 cho thấy policy test chưa đủ chứng minh tích hợp lifecycle |
| Lưu Playlist | `requestPlaylistToggle` → `TelegramPlaylistRuntime` → `playlistSession.toggle`; callback kiểm owner/guard/lifecycle, báo sau ghi. Không thấy writer thứ hai trong phần đọc. Media S7 17/18/19 cùng digest/state2/54544ms, ảnh Playlist 2→3→2 |
| Feedback/retry | Attempt owner/generation, deadline elapsedRealtime/overflow, không auto-retry; cần sửa SB-02 mà giữ bảo vệ request cũ và cooldown |
| Download facts | READY cần completed + tệp đúng owner/root/readable/nonempty/size; Controller kiểm owner/epoch/revision, stat qua worker. Không nâng percent100 thành READY. Chưa chứng minh offline thật/mất quyền tệp trên máy |
| Đảo/queue/transport | Activity Next theo base index hoặc controller; service có đường Next dùng cửa sổ Media3 và successor logic. **BLOCKED S6** còn nguyên, chưa có authority thứ tự chung. Không sửa service/thuật toán hoặc coi nhãn “Thứ tự gốc” là đã đáp ứng thứ tự phát khi Đảo |

## 5. BLOCKED, giới hạn và màn hình

- **BLOCKED S6 Đảo:** cần bổ sung/chốt kế hoạch thứ tự chung UI queue/Next/transport/logical queue/Media3/local và quyền sửa service trước thực thi.
- **BLOCKED Drive:** candidate bị 403; chưa bàn giao tải APK thành công.
- **BLOCKED QA1 tablet thật và QA6 Shanling:** Sony ngang không thay Y700; Ni S7 không có thiết bị ADB. Không xác minh lại kết nối trong phiên này.
- Âm thanh thật chưa xác nhận; offline mất mạng, Release UI, font lớn/IME/keyboard/TalkBack, full queue và star mọi surface, process recreation/deep pagination, S25/FYT chưa kiểm đủ. Crash buffer S7 không thay soak/ANR toàn hệ thống.
- **SONY_SCREEN_END=KHÔNG THAO TÁC, chưa xác minh trong sơ bộ. SHANLING_SCREEN_END=KHÔNG THAO TÁC, chưa xác minh trong sơ bộ.** Mốc lịch sử Sony OFF17:47:44 và Shanling UNKNOWN của S7 giữ nguyên, không cập nhật thành OFF mới.
- Không sửa code/build, luật, APK/ảnh cũ; không gỡ BLOCKED, không PASS chính thức, không merge/push/public hoặc tự mở nhiệm vụ tiếp.

## 6. Bằng chứng cho chủ dự án và bước thử khi được giao

[Ảnh tổng hợp v2](https://drive.google.com/file/d/1stAVkxFoZUTUDVOi4VPKyi9qkNrGYW6q/view?usp=drivesdk) · [toàn bộ ảnh và giới hạn](2026-10-29-melo-ui-ux-ke-hoach-hinh-nghiem-thu.md) · [hồ sơ APK — Drive đang bị chặn](2026-10-29-melo-ui-ux-ke-hoach-apk-test.md).

1. Xem ô 6→7 của ảnh tổng hợp: query giữ nhưng bài neo/vị trí cuộn đổi sau xoay (SB-01).
2. Xem ô 5: chữ “Tạm dừng” mâu thuẫn nút pause và mốc 00:42, đối chiếu snapshot state3 (SB-02).
3. Xem ô 1→2: Playlist 3→2 và player vẫn paused; không suy star mọi màn đã đạt.
4. Sau REWORK được giao và APK mới qua gate, agent thực thi kiểm lại xoay 3 vòng + cached Next/Previous hai màu, đính kèm identity/position/state/ảnh. Chỉ kiểm trên đúng máy/source; không yêu cầu chủ dự án cầm máy trực tiếp hoặc vượt chặn Drive.

Đề nghị chủ dự án giao: `Yêu cầu sửa 2026-10-29-melo-ui-ux-ke-hoach: SB-01 mất anchor khi xoay và SB-02 feedback Tạm dừng sau Next; giữ các BLOCKED.` Đây là phạm vi đề nghị, chưa phải lệnh thực thi. Đảo cần kế hoạch bổ sung riêng trước sửa service.

Commit sơ bộ tra bằng `git log -1 --format=%H -- docs/plans/2026-10-29-melo-ui-ux-ke-hoach-nghiem-thu-so-bo.md`. Chỉ commit biên bản và checkpoint bàn giao; không có tiến trình test/build/QA do phiên này khởi chạy.

**Đã dừng, chờ chủ dự án nghiệm thu.**
