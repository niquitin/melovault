Thực thi: Codex model/effort không xác định từ metadata, S0–S1, phiên tiếp tục S2–S6 và phiên S7 có điều kiện ngày 29/10/2026. REWORK R1 ngày 30/10/2026: Claude Code.

# Báo cáo thực thi Melo UI/UX

TASK_ID: `2026-10-29-melo-ui-ux-ke-hoach`.
Trạng thái chính thức: **CHỜ CHỦ DỰ ÁN NGHIỆM THU**. Các mục S0–S7 dưới đây giữ lịch sử; REWORK R1 hiện hành ở mục đầu.

## REWORK R1 — SB-01 + SB-02, 30/10/2026 (Claude Code, phiên thực thi)

- Chủ dự án giao 30/10/2026 trong chat Claude Code: “Giao REWORK SB-01 + SB-02”, giữ mọi BLOCKED (Đảo, Drive403, QA1 tablet thật, QA6 Shanling). Nền: HEAD `3d3165c` (sơ bộ vòng 1), source APK cũ `278f0d2`. Git đầu phiên sạch, Codex đã dừng (hết quota), không Java/Gradle chạy.
- **SB-02** (`MeloPlayUiState.kt`): thêm cờ `settled` = player đã hiện attempt ít nhất một lần. Khi player chuyển sang bài khác mà không qua `begin` (Next/Previous có sẵn trong controller, tự chuyển bài), `observe` nhận bài mới thành attempt mới (generation tăng) và bỏ feedback cũ của bài vừa rời; sau đó Tạm dừng/Đang đệm tính theo bài mới. Attempt chưa `settled` (RESOLVING/BUFFERING/ERROR của yêu cầu B khi A còn phát) giữ nguyên như cũ; cooldown `waits` và feedback của bài khác không bị xóa. Phụ: callback tải xong của A sau khi đã Next không còn `acceptsPlay` nên không giành lại player.
- **SB-01** (`MeloBrowseState.kt`, `MeloLibraryPane.kt`): cơ chế xác định trong mã: `setSelectionFromTop(anchor)` được post, nhưng trước khi ListView layout có `notifyDataSetChanged()` (đổi bề rộng khi xoay, refresh playback/library) → `rememberSyncState` đồng bộ lại về hàng đầu cũ; đồng thời `restoringSongScroll` tắt ngay sau post nên `onScroll` lưu đè hàng sai làm neo → các vòng xoay sau lặp đúng hàng sai. Sửa: `SongRestore` giữ yêu cầu neo tới khi layout thật hiện bài neo (hoặc list kẹp ở cuối); mọi refresh hàng đi qua `refreshRows()` áp lại neo; không lưu vị trí trong lúc chờ; chạm/fling, nhảy chữ cái, đổi ngữ cảnh, detach kết thúc yêu cầu.
- Test mới: `MeloPlayUiStateTest` +5 (paused A → Next B không `begin`; tự chuyển/Previous; B đang đệm khi A còn phát; lỗi A đã hiện bị bỏ nhưng lỗi B chưa hiện giữ; cooldown bài khác + owner khác + cancel). `MeloBrowseStateTest` +2 (mô phỏng ListView: refresh trước layout làm mất neo nếu không áp lại; 3 vòng xoay giữ neo/offset/query; `SongRestore` chờ đúng hàng, chấp nhận kẹp cuối, cancel). Đây là mô hình JVM, không thay QA xoay thật.
- Ni full test `R1/R1-full-test-20261030_092213.log`: BUILD SUCCESSFUL; mỗi Debug/Release 178 suites, 2268 tổng = **2247 passed / 21 skipped / 0 failed / 0 errors**. Ni assemble Debug+Release BUILD SUCCESSFUL (APK Ni không dùng để cài). `git diff --check` sạch.
- Production đổi 3 file; không đổi service/queue/Media3/DB/sync/cache/timeout/versionCode/signing.

## S7 có điều kiện — kết thúc phần đã kiểm 29/10/2026

- Chủ dự án cho phép build/QA phần độc lập, giữ **BLOCKED S6 thứ tự khi bật Đảo**; không sửa service/playback hoặc tự mở rộng kế hoạch. S6/P1 chưa hoàn tất toàn bộ. Nhãn: **QA CANDIDATE — CHƯA NGHIỆM THU; còn điểm chặn thứ tự khi bật Đảo.**
- Git đầu phiên sạch, đúng nhánh/worktree giao; source `278f0d277aeb077bb938cf76f0e055c5058dd996`, tree `a7713aaa4321b6fa7c637db687c9061aca7f747d`, plan `f912ef5eca8574deb304872af73a2f7ad5856f95`. Không wrapper/worker, chỉ daemon. Luật29/10 đọc tại `c9a1c09`, không sửa quy trình/source.
- Tái sử dụng full test/build Ni S6 đã đối chiếu 14 hashes/manifest và cộng XML: mỗi Debug/Release 178 suites,2261 total =2240 passed/21 skipped/0 failures/0 errors,exit0; không chạy lại vì source không đổi.
- Bundle 13089657 bytes,SHA256 hai đầu `8016797C18E0C93A9D9F797DE388E74C7D4E776CA723BCD57ECB0431E6F98DD9`; clone mới `C:\Bots\MeloVault-S7-IsolatedQA-20261029_170707`. Chỉ copy config tại Tiny theo ngoại lệ đã cấp; không đọc/in/hash/mang raw secrets về Ni.
- Tiny build17:11:07–17:17:09 +07,exit0,BUILD SUCCESSFUL in5m42s; package `app.melovault.player`,vc121,`0.6.96-beta`,config=true,cả hai cert `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`,source/tree đúng và sạch. Script đầu lỗi mã hóa/stderr trước Gradle; giữ log riêng, chỉ v3 là build thực.
- Debug33560516 bytes,SHA256 `EB65BBFEDC851188ADAD28A36F374AC6B09878820060CEA0680F16D44146B133`; Release30769518 bytes,SHA256 `51DC50E4787CF127A439F479BA33BF953D6CCDEBF49E53FA942131FAB13BD300`. Ni copy cùng hash; đường dẫn/gates ở [APK-index](2026-10-29-melo-ui-ux-ke-hoach-apk-test.md).
- Sony Tiny `QV725WEQ54`/XQ-AS52/Sony; installedvc121,cert khớp,media state0 trước cài. Chỉ install-r Debug đã kiểm,Success; Release chưa cài. Mở app không tự phát. Shanling: ADB Ni trống, **BLOCKED QA6**, không dùng Sony thay thế.
- Drive Ni N nhận Debug,folder/file chỉowner `nigamehome@gmail.com`,shared=false,size khớp; kiểm tải HTTP403 `cannotDownloadAbusiveFile` (malware/spam) → **BLOCKED — CHƯA BÀN GIAO QUA DRIVE**. Không né cảnh báo/public/upload Release làm đường vòng. URL lưu trữ và lỗi ở APK-index.
- Đã quan sát mixset3:21:07 paused; +60 từ60199→120404ms,−30→90404ms,giữ identity/state2. Kéo tua lượt xác minh lại→3029030ms,state2; queue preview hiện tối đa12/54,dock giữ ở đáy. Một lượt ADB reconnect,không dùng làm bằng chứng tua; xác minh lại serial/model/device,finally OFF.
- **Lỗi mới QA5:** Next đổi identity và phát(state3); sau≈42s,queue/nút pause đúng đang phát nhưng feedback Deck vẫn “Tạm dừng” (`16a_sony_next_settled_1730.png`,media digest/timestamp private). Đã pause lại,state2. Không sửa code trong S7; cần chủ dự án giao phạm vi sửa và test nhãn theo player thực.
- Lượt menu theo tọa độ trong scan đổi hàng không chứng minh save; có chạm phát ngoài dự kiến,đã pause và loại bằng chứng đó. QA3 thật dùng target XML mới ở17–19: Playlist2→3→2,có★; media cùng identity/state2/54544ms khi lưu/bỏ lưu. Membership đã trả ban đầu; chưa kiểm đủ star mọi surface/keyboard/TalkBack.
- QA4 ảnh20–21 có Đang đệm/Đang tải→row Đang phát khi chọn bài chưa tải đủ thật. Không tạo flood/xóa tệp/thay mạng. FLOOD_WAIT_3/error/invalid file và font/layout matrix dùng JVM S6,không thay QA thật.
- **Lỗi mới QA2:** query thực “mĩ” (IME từ phím mix),scope giữ; Deck→Back22→23 giữ digest dòng `7EE449…F9DF`,bounds[42,725][965,857]. Cả3 chu kỳ xoay sau đó mất anchor ngay lần1: digest `78BF51…9D72`,bounds[42,1164][965,1353],về đầu list. Media vẫn cùng identity/state2/65943ms. `qa2-anchor-comparison.json` có full hashes; không sửa S7.
- Hai màu Current/Teal đã quan sát trên Sony dọc/ngang. Sony Teal Nguồn→kênh khác→query2026 có159 kết quả trong kênh,player trái giữ identity/state2/65943ms (29–31). Đây là bổ sung trên điện thoại,không thay tablet.
- Artifact S7: `docs/plans/artifacts/2026-10-29-melo-ui-ux-ke-hoach/S7-20261029_170707/`; Tiny `C:\Bots\_qa_temp\MeloVault-S7-20261029_170707`. Ảnh/log/XML private,không commit. [Ảnh-index](2026-10-29-melo-ui-ux-ke-hoach-hinh-nghiem-thu.md). Chưa tự sơ bộ/PASS/merge/push/P2.

| QA | Phần đã kiểm kỹ thuật | Còn thiếu / kết quả |
|---|---|---|
| QA1 | Sony ngang hai màu,player giữ khi mở nguồn/kênh/tìm | BLOCKED tablet thật |
| QA2 | Query/scope,Deck→Back,3 chu kỳ xoay; media không tự play/seek | LỖI mất anchor khi xoay; process recreation chưa thử |
| QA3 | Lưu qua ⋮,★ trong Playlist,bỏ lưu,không phát/seek | Star mọi surface/keyboard/TalkBack chưa đủ |
| QA4 | Buffer/download thật,bài chưa tải đủ; fake JVM S6 | FLOOD/error/tệp invalid thật chưa thử |
| QA5 | Mixset>2h,paused,Next,±seek/seekbar,preview queue,dock | LỖI feedback paused khi playing; Đảo BLOCKED; full queue/chữ lớn/IME chưa đủ |
| QA6 | Compact/Car unit S6 | BLOCKED Shanling ADB trống; không FYT |

- Ảnh bàn giao:8 ảnh che nội dung + [00_tong_hop.png](https://drive.google.com/file/d/1stAVkxFoZUTUDVOi4VPKyi9qkNrGYW6q/view?usp=drivesdk),1280×1580,SHA256 `F9561AB73078F5A9D12136C0070FF1D9703B9C27E9314339D5D3481D361F0107`. Folder/file Ni N owner-only,shared=false,9 sizes khớp; summary fetch thành công. Bản cuối local `S7-20261029_170707/redacted-v2/00_tong_hop.png`; bản nháp đầu giữ private,không upload,không ghi đè. Vùng che có thể che cả status trong dòng; ảnh gốc/media private để kiểm chi tiết.
- Cuối QA đã trả màu Hiện tại,xóa query thử,xoay free,mật độ420,font1.0; media state2/65943ms. Installed APK kéo lại có SHA khớp Debug. Crash buffer PID14776 từ17:18:exit0,0 dòng/0 fatal-ANR match; không suy thành kiểm ANR toàn hệ thống/soak.
- **SONY_SCREEN_END=OFF lúc17:47:44 +07**,finally OFF sau từng nhóm UI. **SHANLING_SCREEN_END=UNKNOWN** (không kết nối/không thao tác). Audio thật CHƯA XÁC NHẬN; offline mất mạng,tablet/Y700/S25/FYT thật,font1.3/1.5/2 thực CHƯA KIỂM CHỨNG. QA thay bài/nghe tiếp và bắt đầu tải bài; không xóa cache/data.
- S7 chỉ hoàn thành build/gates/QA và hồ sơ trong điều kiện có được; **không kết luận toàn bộ S6/P1 hoàn tất**. Trước sửa order Đảo cần bổ sung kế hoạch authority chung cho queue/Next/transport,logical queue/Media3 window/local,quyền sửa service và test. Hai lỗi UI cần chủ dự án giao phạm vi sửa/test riêng sau sơ bộ; không tự mở REWORK.
- Bàn giao prompt sơ bộ ở mục S7 của [bàn giao](2026-10-29-melo-ui-ux-ke-hoach-ban-giao.md). Chưa nghiệm thu chính thức; commit hồ sơ rồi dừng,không merge/push.
- Tiếp tục22:37 +07 theo chủ dự án: Git/source và4 hồ sơ staged đúng mốc cũ; SHA hai APK/summary v2 còn khớp,không build/test/QA lại. Commit trước chưa tạo vì thiếu identity; dùng danh tính Codex/codex@openai.com như lịch sử qua `git -c` riêng lệnh commit,không sửa config global. Mốc màn/Drive17h giữ nguyên,không tuyên bố đã kiểm mới22h.

## Phiên tiếp tục S2–S6

<!-- S6_REPORT_START -->
### S6 — checkpoint UI/test; BLOCKED riêng thứ tự Đảo

- Nền S5b `a908151caeaa2a911b9a7a27646fa3ac389a6a64`. Thêm MeloQueuePane + MeloQueueUiPolicyTest; sửa NowPlayingPolicy/test, Host, Activity, StudioDeck, TabletScreen, PhoneScreen, LibraryPane, Components/DesignTokens. S6 có bổ sung nhỏ Controller/test S5b: force completion trùng dùng chung pending check, giữ listener action; stat kiểm owner/root trước metadata tệp.
- Queue renderer dùng host.queue/playQueueAt/openQueueSongMenu, tap/menu kiểm owner+songKey rồi tra lại index. Render-key gồm owner/items/current/playing/paused/buffering/shuffle, không gồm position. Pause/download/content đổi cùng key vẫn refresh; giữ anchor cùng owner, chỉ cuộn khi current/owner đổi. Không quản lý reorder/shuffle/prefetch riêng.
- Studio Deck portrait ghi “Tiếp theo · tối đa12 / tổng”, có full queue cùng renderer; không gọi12 bài là toàn queue. Tablet mở queue tại rightSlot, player trái giữ nguyên, Thư viện/Back trả pane đang mount giữ query/filter/scroll S2. Full queue từ Deck: Thư viện đóng queue+Deck, Back chỉ đóng queue, không pause/seek.
- Host bổ sung buffering/playing default an toàn; paused khác engine BUFFERING thật, không suy !isPlaying luôn pause. Library LOCAL cũng hiển thị đúng trục playback/“Trên máy”. Gọn/Car dùng fallback queue cũ, enum Status không đổi.
- Dock Tablet và Deck ngoài scroll phụ; Deck xử lý IME bottom, đo text nút thật và chuyển hai nút tua sang hàng riêng trước khi thiếu48dp. Chữ lớn đưa duration xuống body, hai nhãn thời gian tách hàng, chips/điều khiển phụ có chiều cao co giãn; title tối đa2 dòng, artwork theo phần còn lại. Đây là thay đổi code, chưa kiểm touch/keyboard/font thật.
- Highlight mới Phone/Tablet opt-in nền surfaceRaised, chữ phụ text chính; test contrast các màu chữ nhỏ trên hai theme >=4.5:1. Bỏ tail gradient sáng gây thiếu tương phản ở các dòng opt-in; palette Gọn/Car giữ nguyên. Phép tính màu source không thay ảnh UI.
- **BLOCKED S6 thứ tự khi Đảo bật (Telegram và Local)**: Activity `meloHost.queue` map base queueSongOrder; `toggleShuffle` chỉ đổi controller flag; `playAdjacent` Telegram chọn index±1. Service `currentTelegramQueue/setQueue` giữ base queue; `nextTrack` dùng immediateSuccessor nhưng NextAwarePlayer.seekToNextMediaItem chuyển cho ExoPlayer khi cached next đã có. Media3 chỉ chứa cửa sổ cached/progressive, khác logical queue đầy đủ. Vì vậy chưa có hợp đồng chung để gọi snapshot là thứ tự phát thực khi Đảo bật. Local snapshot cũng index nền. Đây là đối chiếu source, chưa tái hiện thiết bị; không sửa service/thuật toán để vượt kế hoạch. UI ghi “Thứ tự gốc · Đảo đang bật”. Cần bổ sung kế hoạch/quyết định về hợp đồng thứ tự trước khi gỡ BLOCKED; chưa hoàn tất toàn bộ S6/chưa đủ điều kiện tự mở S7.
- Test mục tiêu `S6-targeted-20261029_164456`: mỗi biến thể140 passed/0 skipped/0 failed/0 errors, exit0. Sau chỉnh owner/local/force-coalesce/route/root-first đã chạy full lại, chỉ dùng lượt source cuối bên dưới cho bàn giao.
- **Full source cuối `S6-source-final-20261029_165358`**, 16:53:58–16:56:03 +07: mỗi Debug/Release178 suites,2261 tests = **2240 passed /21 skipped /0 failed /0 errors**, exit0, BUILD SUCCESSFUL in2m5s. Hai task đã thực chạy, XML fresh, SHA256 từng file source.json khớp. Nền HEAD trên + đúng manifest, commit S6 chứa các file đó và hồ sơ. Log `gradle.log`, `result.json`, `source.json`, XML từng biến thể nằm dưới artifacts TASK_ID/thư mục lượt chạy.
- 21 skipped/biến thể: BackgroundOfflinePlaylistSyncTest19 + SmartOfflinePlaylistSyncTest1 bị Assume theo PERSONAL_BACKEND_ENABLED (Shareable không có Personal backend), PlaybackStabilitySoakTest1 cần trigger/env bật soak riêng. Không đổi các điều kiện này; passed không cộng skipped. Các lượt full trung gian `S6-full-20261029_164638`/`S6-full-final-20261029_164920`2239 passed và `S6-full-checkpoint-20261029_165125`2240 passed mỗi biến thể vẫn giữ riêng, chưa chứa route/root-first cuối.
- Lệnh full đúng yêu cầu: `.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain`. Full suite gồm hồi quy Compact/Car/Layout/Screen/BottomBar/Resume/Save/Playlist session/sync/RowArt/Offline/Search/seek E103. Test công thức font1/1.3/1.5/2,48dp/contrast/long duration không chứng minh bố cục hoặc âm thanh trên máy.
- **Assemble Ni `S6-assemble-20261029_165631`**,16:56:31–16:58:29 +07: đúng hai task assemble Debug/Release, exit0, BUILD SUCCESSFUL in1m57s. APK mới được sao lưu riêng trong `apk/`, Fresh=true; test/build manifest và hash source hiện tại khớp (`evidence-check.json`). Assemble không chạy test, không có số passed/skipped riêng. Metadata build: `app.melovault.player`,versionCode121,versionName0.6.96-beta, cả hai biến thể; ghi `apk-metadata.json`, chưa kiểm cert/config/gate candidate.
- APK Debug33,560,516 bytes,SHA256 `5C485580165865B5F93DF3172D566A89FDCDBC13B18D86F72BD8B8DD91C7CDF6`; Release30,769,518 bytes,SHA256 `9E804FCCDCF25BAB8E62C338C82EFC03F3F16E4CF8ADD83C060ECF129AF1D563`. Tất cả log/result/XML/APK ở artifacts bị loại khỏi commit; không ghi đè artifact cũ. Source cuối là commit chứa checkpoint S6 này; tra SHA theo MeloQueuePane.kt hoặc file bàn giao sau commit.
- Không cài/ADB/Tiny/Drive/S7, không sơ bộ/PASS/merge/push; không đổi package/signer/versionCode/schema/songKey/owner/session/service hoặc thuật toán sync/cache. SHANLING_SCREEN_END/SONY_SCREEN_END: không thao tác, chưa xác minh. Handoff có bước gỡ BLOCKED và prompt S7 có điều kiện; không tự chuyển chặng.
<!-- S6_REPORT_END -->


### S5b — bằng chứng tệp cho trạng thái Đã tải

- Nền `3615d643593d4650b36addfacc8d6b122d5328a0`; đối chiếu tám đường cachedSongPaths. Thêm MeloDownloadUiPolicy/Controller + 12 test; sửa Activity, Host, Components/LibraryPane/StudioDeck để dùng facts typed chung. Không đổi service/cache/index/schema hoặc truy vấn sync.
- UNKNOWN/REMOTE/DOWNLOADING/READY/UNAVAILABLE tách playback. READY chỉ completed thật từ message/resolve/completion/local scan, đúng owner/root, readable/non-empty và exact expected size khi có; percent100 không đủ. Bỏ shortcut bộ lọc Đã tải. Scan/callback owner/query/session cũ không publish; checker có owner/epoch/revision, gộp trùng và kiểm nền, shutdown khi Activity hủy.
- Path từ service queue chưa có completion giữ UNKNOWN; bàn giao path cũ cho playback chỉ sau worker kiểm, không tự nâng READY. List/queue/now dùng cùng presentation; quality bytes lấy từ RAM, getter/bind không stat file/TDLib. Foreground kiểm lại các bài hiện có và nhóm offline; pending action listener được giữ nếu check còn chạy. Global downloadedCount vẫn generation/pipeline cũ, không tính lại từ subset rows.
- Representative offline dùng helper với facts READY, chuyển sang bản còn hợp lệ, giữ nguyên group/index/Playlist. Mất/unreadable/zero/mismatch ghi “Tệp trên máy không còn dùng được”; trước phát check nền theo action đã bấm, failure chỉ retry thủ công qua resolver cũ. Local vẫn “Trên máy”. Không xóa nhạc thật để test.
- Test cuối `S5b-final-20261029_163743`: Debug và Release mỗi biến thể **58 passed/0 skipped/0 failed/0 errors**, 6 suites, exit0, XML fresh/source.json match; log/result/XML ở artifacts TASK_ID cùng tên. Nền HEAD trên + manifest. Lượt đầu57 và checkpoint58 giữ riêng; chỉnh cuối bảo toàn cached-path handoff nên chỉ lượt final đại diện source bàn giao.
- Hai task test Debug/Release cùng filters: MeloDownloadUiPolicyTest, TelegramOfflineLibraryTest, TelegramOfflinePlayPolicyTest, TelegramAccountPartitionTest, MeloDownloadedCountPolicyTest, MeloComponentPolicyTest; `--console=plain`. Kiểm diff-check sạch. Không Gradle worker còn chạy sau exit; artifact không commit.
- Chưa QA tệp bị thu hồi trên thiết bị/offline thật hoặc các màu/font; không APK/ADB. SHANLING_SCREEN_END/SONY_SCREEN_END không thao tác, chưa xác minh. S5b commit riêng chứa hồ sơ này; tiếp tục S6 đã được giao. Hàng chờ typed refresh/paused/buffering sẽ chốt ở S6.


- S5a xong code/test trên HEAD nền S4 `cfd0bd3160dcad183d3e12c2d9bc8acebeb264c2`. Tiếp tục 16:01 +07 sau chỉ thị chủ dự án; hai file policy/state chưa commit trước đó khớp phần do phiên này viết, không file lạ hoặc worker nền. S5b–S6 chưa làm.

### S5a — feedback và retry theo attempt

- Thêm `MeloPlayUiState.kt` / test (15 ca), sửa PlayFeedbackPolicy/test, Host, Activity và inline view dùng chung trong LibraryPane/Tablet/StudioDeck. Không thêm enum trạng thái global, service/sync/cache algorithm hoặc tăng timeout12s/stall15s.
- PlayAttempt owner + generation + songKey; cùng bài đang resolve/buffer không tạo resolve thứ hai. Callback resolve/download/stall phải khớp attempt; tag download thêm owner/generation, capture identity trước cleanup. A→B→A cũ bị bỏ; completion không enqueue lại stream đã phát.
- Chỉ chuyển/hủy download cũ sau khi bài mới resolve hợp lệ và bắt đầu cache được; lỗi resolve/cache B giữ luồng A cũ. Notify dựa isPlaying/playWhenReady/STATE_BUFFERING, không xóa opening chỉ vì current key đã trùng. Lỗi Media3/resolve/download có câu cố định, không đưa raw TDLib message vào UI.
- FLOOD_WAIT matchEntire trước code420/429; kiểm Long overflow cả nhân/cộng, malformed/PII/thiếu/âm/tràn về generic. Deadline SystemClock.elapsedRealtime giữ qua rotate/đổi nguồn/local detour; 429 không N không bịa countdown. Gate ở downloadAndPlay cho dòng/menu/retry; hết hạn chỉ mở nút, không auto-retry.
- Inline vài dòng + nút retry≥48dp, error gắn “Bài vừa chọn”, không giả lỗi danh sách mới. Timer chỉ ở view đang gắn/hiện/có focus; detach/ẩn cửa sổ hủy pending; Gọn/legacy giữ thông báo cũ. Lỗi permanent không có nút retry, timeout/network/server tạm thời có.
- Test cuối `S5a-checkpoint-20261029_161346`: Debug **77 passed/0 skipped/0 failed/0 errors**, Release cùng kết quả, 6 suites/biến thể, exit0, BUILD SUCCESSFUL in 16s, XML fresh/source.json match. Nền `cfd0bd3` + manifest; log/result/XML dưới artifacts cùng tên. Lượt đầu `S5a-20261029_160943` cũng 77/biến thể, sau đó có chốt send-error cố định/visibility generation nên dùng lượt cuối cho source bàn giao.
- Bộ mục tiêu hai task Debug/Release: MeloPlayUiStateTest, TelegramPlayFeedbackPolicyTest, TelegramAudioResolverPolicyTest, MeloNowPlayingPolicyTest, MeloComponentPolicyTest, TelegramSessionEpochTest; cùng `--tests`, `--console=plain`. `git diff --check` đạt.
- Chưa tạo FLOOD thật, chưa QA UI/audio hoặc xác nhận A vẫn có tiếng khi B lỗi trên máy; fake clock/timer và code guard không thay bằng chứng này. S5a commit chứa mục này; bước tiếp S5b theo kế hoạch.

- Chủ dự án xác nhận S0–S1 đã làm xong, giao liên tục S2–S6 trong cùng vai THỰC THI; không coi là nghiệm thu chính thức.
- Đầu phiên 11:19 +07: HEAD S1 `fb896d4c537d007c633d9008f3a95ee521d9784f`, cây sạch, nhánh/worktree và diff-check đúng; không có build/test worker đang chạy, chỉ daemon thường trú.
- S2 commit `e18979fa5dd37b2f889348991e8442d5f60f56fb`; S3 commit `12d5fd2554eafe1be848bb8c6892a1d8b9cf1e21`; S4 xong code/test mục tiêu; tiếp tục S5a–S6. S6 full unit + assemble. Không làm S7/P2/cài thiết bị hoặc sơ bộ.

### S4 — lưu nhanh theo đúng bài trên dòng

- Sửa Host Row/API, Activity snapshot/toggle/notify, Components, LibraryPane, MeloSavePolicy; thêm `MeloRowSavePolicyTest.kt` (9 ca). Row có saved/busy/availability mặc định không actionable; LOCAL không giả hỗ trợ, ⋮ có giải thích hạn chế.
- Direct ☆/★ chỉ Phone/Tablet opt-in khi body còn ≥160dp sau ảnh/gap/duration đo thật/hai nút ≥48dp, fontScale≤1.3; thiếu chỗ giữ ⋮. Save/menu có focus và listener riêng; parent tap/long press được nối riêng theo owner + songKey, không dựa vị trí sau async/recycle.
- Row target chỉ từ bản đang hiển thị, owner khớp và (chat,message,key) thật; nhóm offline lưu đúng representative. Cùng `requestPlaylistToggle`/SaveToggleGuard/writer và hook E105 `afterSave`, không sync trực tiếp từ UI, không playback trong row-save handler.
- ★ từ favoriteIds đã commit; busy theo target in-flight/queued parity. Lỗi/not-ready/full giữ committed state. Callback owner/guard/lifecycle cũ bị bỏ; NowPlaying busy cũng theo đúng target, wording cũ giữ nguyên. Save/remote đổi star ngoài Playlist chỉ notify UI, không reload/query/scan hay reset scope; trong Playlist vẫn refresh thành viên.
- Test `S4-20261029_114717`: Debug **122 passed/0 skipped/0 failed/0 errors**, Release cùng kết quả; 7 suites/biến thể, exit 0, BUILD SUCCESSFUL in 22s; XML fresh và source.json match. Source nền `12d5fd2` + manifest; log/result/XML tại artifacts cùng tên.
- Bộ mục tiêu Debug/Release: MeloRowSavePolicyTest, MeloSavePolicyTest, PlaylistLiveRefreshTest, TelegramPlaylistSessionTest, TelegramPlaylistV2SessionTest, TelegramPlaylistViewPolicyTest, AutomaticPlaylistSyncTest; cùng `--tests`, `--console=plain`. `git diff --check` đạt.
- Chưa QA3/touch/keyboard/TalkBack trên máy; không dùng test policy như chứng minh click thật không phát. Gọn/Car/queue không opt-in nút dòng. S4 commit chứa mục này; bước tiếp S5a.

### S3 — nguồn bên phải, player giữ bên trái

- `MeloTabletScreen`: ngừng mount nav/quickPane trái và gọi renderQuickSwitch/updateMini; full player luôn ở leftSlot, giữ tỷ lệ 40/60/ngưỡng layout. Màu thường giữ Cài đặt/Mở Đang phát ở heading phải; thường và Teal cùng bốn tab tại vùng thư viện.
- `MeloLibraryPane`: Hai khung dùng ListView + adapter tái sử dụng dòng nguồn, không tạo một View cho mỗi kênh. Nguồn count>0, nhóm count=0 thu gọn, count âm ghi chưa rõ; chọn nguồn ghi ✓/Đang duyệt theo chatId, độc lập playingKey. Portrait giữ renderer grid hiện có.
- `MeloLibraryPolicy`/test và `MeloBrowseUiPolicyTest`: scope toàn cục chuẩn, selection khác scope kênh/Tin đã lưu, count unknown và load/empty/error. Host/Activity thêm route `openBrowseScope` nguyên tử, `filterInSource` riêng trong kênh, Back Sources về context cũ; chọn lại scope hiện hành không chạy load thứ hai. Không gọi player trong route chỉ duyệt.
- Bridge S2 (`MeloBrowseState`, Host, Activity) bổ sung anchor chatId + offset cho danh sách nguồn ảo hóa; Bundle vẫn chỉ scalar/owner-verified. Yêu cầu Phát ngẫu nhiên còn chờ được gắn owner/scope/generation, bị hủy khi chuyển scope; không biến thao tác duyệt thành phát bài khác.
- Test cuối `S3-checkpoint-20261029_113957`: Debug **130 passed/0 skipped/0 failed/0 errors**, Release cùng kết quả; 6 suites/biến thể, exit 0, BUILD SUCCESSFUL in 7s; XML fresh, source.json khớp. Log/result/XML tại thư mục artifacts cùng tên. Lượt trung gian `S3-20261029_113833` 129 tests/biến thể vẫn giữ, không thay nguồn cuối.
- Bộ mục tiêu: MeloLibraryPolicyTest, MeloBrowseUiPolicyTest, MeloBrowseStateTest, MeloScreenPolicyTest, MeloSourceCardPolicyTest, MeloBottomBarPolicyTest; hai task Debug/Release cùng `--tests`, `--console=plain`. `git diff --check` đạt.
- Chưa QA1/QA2 hoặc xác minh font/layout trên máy; chỉ xác minh code/test theo giới hạn cấm thiết bị của phiên. Source S3 là commit chứa mục này, tra log theo MeloTabletScreen.kt; tiếp theo S4 đã được giao.

### S2 — giữ ngữ cảnh duyệt

- Thêm `MeloBrowseState.kt` và `MeloBrowseStateTest.kt`; sửa `MeloScreenHost.kt`, `TelegramPreviewActivity.kt`, `MeloLibraryPane.kt`, `MeloStudioDeck.kt`. Chỉ state/adapter UI; không gọi restore playback/seek/playWhenReady, không đổi schema/sync/cache.
- Key owner + scope/ID; LRU 16, query/sort/letterFloor/anchor songKey + offset, cuộn nguồn riêng. Đổi query/sort hủy anchor; mở/đóng deck và dựng lại renderer giữ rows/paging hiện có; ngữ cảnh LOCAL tách riêng.
- Bundle chỉ active/return contexts, query tối đa 512 ký tự và scalar UI/deck; decode sai fallback, chỉ apply sau verified owner khớp. Pending anchor không bị tiêu ở batch đầu; chỉ áp đúng query/owner/generation, không tự nạp vô hạn để tìm bài.
- Debounce flush đúng một lần khi detach/rotate/save/open player; trả lại nguồn đã ghé khôi phục query (kể cả rỗng), source title không làm identity. Đường mode/sources cũ Gọn/Car vẫn tách khỏi điều hướng mới.
- Test cuối `S2-checkpoint-20261029_113203`: Debug **126 passed/0 skipped/0 failed/0 errors**, Release cùng kết quả; 7 suites/biến thể, exit 0, BUILD SUCCESSFUL in 11s. Source nền `fb896d4` + manifest `source.json` trong thư mục log; SHA từng file sau test khớp, XML Fresh=true. Log/exit/counts/XML: `artifacts/2026-10-29-melo-ui-ux-ke-hoach/S2-checkpoint-20261029_113203/` (`gradle.log`, `result.json`). Hai lượt trung gian 125 tests/biến thể vẫn giữ; source sau đó có chỉnh guard scroll/query nên chỉ lượt 126 đại diện bàn giao.
- Lệnh mục tiêu: hai task test Shareable Debug/Release, cùng `--tests` cho MeloBrowseStateTest, MeloBrowseUiPolicyTest, MeloLibraryPolicyTest, MeloLocalLibraryTest, MeloAlphabetPolicyTest, MeloScreenPolicyTest, TelegramSessionEpochTest; `--console=plain`.
- Giới hạn: QA2 ba lần xoay chưa chạy trên máy vì phiên này cấm thiết bị; process recreation chỉ nạp trang chuẩn, anchor sâu chờ trang người dùng nạp hoặc bỏ khi dataset đã hết. Không coi test JVM là chứng minh UI thật. Commit S2 là commit chứa mục này; source SHA tra `git log -1 --format=%H -- app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloBrowseState.kt`.

## Hồ sơ S0–S1 đã commit tại fb896d4 (giữ lịch sử phạm vi/kết quả)

| Bước | Trạng thái | Bằng chứng / phần còn lại |
|---|---|---|
| S0 | Xong | Cây sạch; nhánh/HEAD/baseline khớp; đã đọc luật 29/10 bằng Git |
| S1 | Xong code + test mục tiêu | Scope/loading/empty/error/metadata; 220 passed mỗi biến thể, UI thật chưa kiểm |
| S2–S6 | Chưa làm | Ngoài phạm vi phiên được giao |
| S7 | Chưa làm | Không build candidate/cài/QA/Drive trong phiên này |
| Sơ bộ/chính thức | Chưa làm | Không tự chuyển vai hoặc ghi PASS |

## 1. Nguồn và kiểm đầu phiên

- Worktree: `C:\Users\ntdun\.codex\worktrees\melo-ui-ux-plan\MeloVault-E91-Shareable`.
- Nhánh: `dot/2026-10-29-melo-ui-ux-ke-hoach`.
- HEAD kế hoạch đã kiểm: `f912ef5eca8574deb304872af73a2f7ad5856f95`.
- Baseline source: `7f3cca874e525956a10f94f8caf577c6cc80f815`; là ancestor của HEAD.
- `git status --short` rỗng; `git diff --check` exit 0; log -5 khớp bàn giao; diff baseline..HEAD chỉ bốn tài liệu kế hoạch.
- Kiểm process đầu phiên: không có Java/Gradle; các process CLI/ADB tồn tại từ trước không có tham chiếu MeloVault/worktree trong command line. Chủ dự án xác nhận phiên kế hoạch đã dừng, không có build/ADB nền.
- Luật áp dụng: bốn file `c9a1c09:CLAUDE.md`, `AGENTS.md`, `docs/workflow/QUY_TRINH_3_VAI_TRO.md`, `PROMPT_CODEX.md`; không merge/copy luật vào working tree.
- Một sub-agent chỉ đọc để kiểm kê test; agent chính là người duy nhất sửa file/code; không giao vai nghiệm thu.

## 2. Thay đổi S1 và quyết định bổ sung phạm vi

- Đã đọc kế hoạch chính, bàn giao và phụ lục S0–S1; sau đó chỉ source/test liên quan S1.
- LOCAL: chủ dự án đã cho phép bổ sung tối thiểu `MeloLocalLibrary.kt` và test tương ứng trong hội thoại 29/10; đã thực hiện.
- Chủ dự án tiếp tục cho phép thêm tối thiểu cờ kết quả đọc trong `LocalMediaRepository.kt` và test để nhận lỗi MediaStore bị lớp dưới che. Đã thêm cờ, giữ query/cache/giá trị trả về cho nơi gọi cũ; LOCAL không nhận danh sách rỗng/một phần do lỗi là một lần đọc thành công. Hai điểm BLOCKED trên đã được gỡ bằng hai xác nhận trực tiếp trong hội thoại.
- LOCAL artist hiện có thể là tên thư mục dự phòng; adapter dùng phân biệt thư mục hiện có, ghi “Thư mục: …”, không gắn thành nghệ sĩ giả.

| File | Thay đổi |
|---|---|
| `MeloBrowseUiPolicy.kt` mới | Bảy scope, nhãn/hint, load state, count có thẩm quyền, guard callback, metadata thuần |
| `MeloScreenHost.kt` | Thêm scope/state/owner/generation và performer/thư mục với mặc định; `retryBrowse` mặc định không làm gì |
| `TelegramPreviewActivity.kt` | Adapter snapshot, refresh/scan/paging; giữ rows khi cập nhật/lỗi; callback có owner/generation/lifecycle; retry đọc đúng scope |
| `MeloLibraryPane.kt` | Dùng cùng policy ở thường/Teal; scope riêng, empty/error trong vùng list, retry; key nguồn dùng ID |
| `MeloComponents.kt` | Metadata opt-in cho list Phone/Tablet; mặc định Gọn/Car/queue giữ đường cũ; giữ tên tối đa hai dòng |
| `MeloLocalLibrary.kt` | Expose trạng thái đọc/rebuild và generation; lỗi giữ rows trước, retry giữ query; callback cũ vẫn bị bỏ theo epoch/sequence |
| `LocalMediaRepository.kt` | Chỉ thêm cờ kết quả đọc: lỗi/thiếu resolver/null cursor/phần dữ liệu do lỗi không thành thành công; giữ truy vấn, cache và giá trị trả về API cũ |
| `MeloBrowseUiPolicyTest.kt` mới | 20 test scope/hint/state/count/identity/callback/metadata |
| `MeloLocalLibraryTest.kt` | Thêm 7 test tải/cập nhật/lỗi/retry/clear/callback lỗi cũ |
| `LocalMediaRepositoryTest.kt` | Thêm 3 test lỗi đọc, empty thật, cache giữ kết quả lỗi và retry |

Các file trên nằm trong thư mục `app/src/shareable/java/.../telegram`, `app/src/test/java/.../telegram`; hai file repository ở `app/src/main/java/.../data` và `app/src/test/java/.../data`.

- Scope: LIBRARY, CHANNEL bắt buộc chatId, DOWNLOADED (có/không chatId), PLAYLIST, SOURCES, SAVED_MESSAGES, LOCAL. DOWNLOADED trong kênh và Playlist + Đã tải đều ghi cả hai điều kiện; title không làm identity.
- INITIAL_LOADING: “Đang tải danh sách…”; UPDATING giữ rows, “Đang cập nhật danh sách…”; scan Đã tải dùng “Đang kiểm tra tệp trên máy…”.
- READY rỗng phân biệt có/không query; ERROR có “Thử lại”, giữ rows cũ có ghi chú. Retry chỉ đọc lại scope hiện tại, không gọi đồng bộ toàn bộ hoặc playback.
- Count chưa có kết quả đầy đủ để null, không dùng 0 của buffer/scan một phần. Query/sort/filter hiện có và tìm không dấu giữ nguyên; không thêm DB/TDLib trên bind dòng.
- Nghệ sĩ từ performer thật/local metadata; nguồn Telegram ghi “Kênh: …”; thiếu tag thì bỏ, không tạo chất lượng hoặc Unknown artist.
- Scan lại có rows cũ chỉ thay rows sau kết quả hoàn tất; tải thêm trang có UPDATING/ERROR, không che list; callback cũ không đổi dữ liệu/trạng thái/count của query mới.

## 3. Test và artifact

Lượt 1, 11:02:15–11:05:04 +07: Debug và Release mỗi biến thể **219 passed, 0 skipped, 0 failed, 0 errors**; 14 suite; Gradle exit 0, BUILD SUCCESSFUL. Log `s1-targeted-20261029_110215.log`, manifest `s1-source-20261029_110215.txt`, XML đã sao lưu riêng trong `s1-results-20261029_110215/` dưới `docs/plans/artifacts/2026-10-29-melo-ui-ux-ke-hoach/`; không commit artifact.

Sau lượt 1 đã chỉnh thêm adapter tiến độ scan, trạng thái/lỗi paging và test callback lỗi LOCAL cũ. Đã chạy lại đúng source cuối:

| Biến thể | Suites | Tests | Passed | Skipped | Failed | Errors |
|---|---:|---:|---:|---:|---:|---:|
| Shareable Debug | 14 | 220 | 220 | 0 | 0 | 0 |
| Shareable Release | 14 | 220 | 220 | 0 | 0 | 0 |

- Lượt cuối: **29/10/2026 11:07:11–11:07:31 +07**, `BUILD SUCCESSFUL in 19s`, exit 0. Hai task test đã thực chạy; XML cuối tạo sau thời điểm bắt đầu.
- Log: [s1-targeted-20261029_110711.log](artifacts/2026-10-29-melo-ui-ux-ke-hoach/s1-targeted-20261029_110711.log); exit code/thời gian ở tệp `.log.exit.txt` cùng tên.
- XML giữ riêng: `artifacts/2026-10-29-melo-ui-ux-ke-hoach/s1-results-20261029_110711/`; tổng lấy từ `tests/skipped/failures/errors`, passed không cộng skipped.
- Source manifest: [s1-source-20261029_110711.txt](artifacts/2026-10-29-melo-ui-ux-ke-hoach/s1-source-20261029_110711.txt), SHA-256 `022977A92D683B1E473838AA437F7D082052F9E5822F14173F99850998FF1951`. Đã đối chiếu SHA-256 từng file mã/test sau test, tất cả khớp; sau đó chỉ sửa hồ sơ.
- Source test: HEAD nền `f912ef5` + đúng 10 file mã/test trong manifest. Source S1 được commit cùng báo cáo này; lấy SHA không tự tham chiếu bằng `git log -1 --format=%H -- app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/TelegramPreviewActivity.kt`.
- Có 30 test mới; hồi quy gồm tìm không dấu, bản trùng tên/ID, owner/session, offline library, library/component, local source và Gọn/Car/NowPlaying.

Lệnh đã chạy, cùng bộ lọc cho hai biến thể:

~~~powershell
$names = @('MeloBrowseUiPolicyTest','MeloComponentPolicyTest','MeloLocalLibraryTest',
  'MeloLocalSourcePolicyTest','MeloLibraryPolicyTest','TelegramSearchPolicyTest',
  'TelegramOfflineLibraryTest','TelegramAccountPartitionTest','TelegramSessionEpochTest',
  'MeloCompactPolicyTest','MeloCarPolicyTest','MeloCarLayoutPolicyTest','MeloNowPlayingPolicyTest')
$filters = @()
foreach ($name in $names) { $filters += @('--tests', "com.bsdung.musicassistantcar.data.telegram.$name") }
$filters += @('--tests','com.bsdung.musicassistantcar.data.LocalMediaRepositoryTest')
.\gradlew.bat :app:testShareableDebugUnitTest @filters :app:testShareableReleaseUnitTest @filters --console=plain
~~~

Mỗi lượt redirect toàn bộ output sang log riêng; không ghi đè log/XML đã lưu của lượt trước. Không chạy full suite/assemble vì S1 chưa là candidate; gate đầy đủ ở S6–S7 theo kế hoạch.

## 4. Giới hạn và điểm dừng

- Chưa kiểm UI thật/màu thường–Teal, thiết bị, hiệu năng, âm thanh/offline. Unit/build xanh không thay bằng chứng này.
- Không tạo APK candidate, không cài/ADB/Tiny/Drive; không xác nhận bản đang cài.
- Không có ảnh/video hoặc `00_tong_hop.png` vì không làm QA UI. Không dùng artifact/APK cũ như bằng chứng source S1.
- SHANLING_SCREEN_END: không thao tác thiết bị, chưa xác minh trạng thái màn.
- SONY_SCREEN_END: không thao tác thiết bị, chưa xác minh trạng thái màn.
- Không sửa S2–S7/P2, package/signer/versionCode/schema/songKey/owner/session/service/playback/sync/cache.
- Cuối test không có Gradle wrapper/test worker còn chạy. Chỉ còn Gradle daemon PID 27832 và Kotlin daemon PID 27228 thường trú; lệnh test đã exit 0, không có build/ADB nền do phiên này chạy tiếp.
- Đã kiểm diff kỹ thuật và `git diff --check`; chỉ commit S1 + hồ sơ trên nhánh nhiệm vụ, loại toàn bộ artifacts. Commit chứa hồ sơ tra bằng `git log -1 --format=%H -- docs/plans/2026-10-29-melo-ui-ux-ke-hoach-bao-cao.md`.
- Lệnh commit đầu bị Git từ chối vì chưa có user.name/email. Đã đối chiếu ba commit gần nhất đều là `Codex <codex@openai.com>`; dùng lại danh tính đó bằng `git -c` riêng lệnh commit, không sửa cấu hình global/local hay source, không cần chạy lại test.
- Bước còn lại: S2–S6 code/test tích hợp, S7 candidate/build/QA, rồi phiên sơ bộ mới và chủ dự án nghiệm thu chính thức; chỉ bắt đầu khi được chủ dự án giao. Không merge/push/tự mở chặng tiếp.

Đã dừng, bàn giao phiên thực thi tiếp theo.
