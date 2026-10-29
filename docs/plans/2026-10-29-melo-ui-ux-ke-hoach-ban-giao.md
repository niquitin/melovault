# Bàn giao — Melo UI/UX sau video

TASK_ID: **2026-10-29-melo-ui-ux-ke-hoach**.

## Phiên đang thực thi S2–S6 — 29/10/2026

<!-- S6_CURRENT_START -->
- **Checkpoint cuối phiên S2–S6: S2/S3/S4/S5a/S5b đã commit; S6 phần UI/test/build xong, BLOCKED riêng thứ tự khi Đảo bật. Chưa hoàn tất toàn bộ S6; chưa mở S7.** Source S6 là commit chứa checkpoint này, nền `a908151caeaa2a911b9a7a27646fa3ac389a6a64`; SHA cuối lấy `git log -1 --format=%H -- app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloQueuePane.kt`, đối chiếu HEAD thực trước tiếp nhận. Các dòng bước tiếp S5a/S5b lịch sử bên dưới đã được thay bằng mốc này.
- Source S5a `3615d643593d4650b36addfacc8d6b122d5328a0`; S5b `a908151caeaa2a911b9a7a27646fa3ac389a6a64`. Mốc S2/S3/S4 giữ bên dưới; không quay về HEAD kế hoạch/S1 để tiếp tục.
- S6: MeloQueuePane/QueueUiPolicyTest mới; sửa NowPlayingPolicy/test, Host, Activity, StudioDeck, TabletScreen, PhoneScreen, LibraryPane, Components/DesignTokens; bổ sung Controller/test S5b coalesce pending force và root-first stat. Cùng renderer preview/full/tablet; refresh theo typed facts, giữ anchor cùng owner, pause không tự scroll. Tablet queue trong khung phải, Back/Thư viện giữ browse; Dock ngoài scroll phụ, IME/font/contrast opt-in. Full queue từ Deck: Thư viện đóng cả hai UI, không playback.
- **BLOCKED phần thứ tự Đảo (Telegram/Local)**: `meloHost.queue` trả base queue, `toggleShuffle` chỉ đổi Media3 flag, `playAdjacent` Telegram index±1; service `currentTelegramQueue/setQueue` base order, `nextTrack` tuyến tính nhưng NextAwarePlayer.seekToNextMediaItem có thể dùng ExoPlayer shuffle khi cached next có sẵn. Media3 chỉ có cửa sổ cached/progressive, không phải logical queue đầy đủ; local snapshot cũng base index. Chưa có hợp đồng order chung; UI ghi Thứ tự gốc khi Đảo bật. Chỉ là đối chiếu source, chưa QA thiết bị. Không sửa service/thuật toán hoặc tự sort để che khác biệt.
- **Bước cần tiếp chính xác:** bổ sung kế hoạch/quyết định về hợp đồng thứ tự khi Đảo bật trước khi gỡ BLOCKED S6. Nêu cùng một authority cho UI Next/transport/queue, mapping logical queue và Media3 window, hành vi local; xác định file/test và quyền sửa service nếu cần. Phiên này không tự chọn kiến trúc hoặc thực thi phần đó. Các phần độc lập S6 đã kiểm kỹ thuật/commit; chưa gọi S6 hoàn tất hay candidate sẵn sàng.
- Full cuối `S6-source-final-20261029_165358`,16:53:58–16:56:03 +07: mỗi Debug/Release178 suites,2261 tests = **2240 passed/21 skipped/0 failed/0 errors**, exit0, XML fresh/source.json match. Skipped20 theo Personal backend disabled,1 simulated soak cần bật riêng. Lệnh đúng hai task test Debug/Release `--console=plain`; các lượt trung gian vẫn giữ, chỉ lượt này đại diện source cuối.
- Assemble Ni `S6-assemble-20261029_165631`,16:56:31–16:58:29 +07: Debug/Release exit0, APK mới/Fresh=true; test/build/current-source manifest khớp. Log/result/source.json/evidence-check/apk-metadata/APK riêng dưới artifacts TASK_ID, không commit. Package `app.melovault.player`,versionCode121,versionName0.6.96-beta; SHA/bytes hai APK trong báo cáo và result.json. **Chưa kiểm cert/config/gates candidate, không dùng APK này như QA đã đạt.**
- Sau commit: không code dở/artifact được stage, không wrapper/test worker còn chạy; daemon thường trú có thể còn. Cần kiểm git status/branch/HEAD/log/diff-check/process thực khi tiếp nhận. Không ADB/cài/Tiny/Drive/QA UI/audio/offline; font1–2 mới kiểm công thức/đọc layout, không ảnh. SHANLING_SCREEN_END/SONY_SCREEN_END không thao tác/chưa xác minh. Không sơ bộ/PASS/merge/push/S7/P2.
<!-- S6_CURRENT_END -->

### Prompt S7 có điều kiện — lưu để bàn giao, chưa được chạy trong phiên này

Chỉ dùng sau khi BLOCKED S6 về order được giải quyết bằng kế hoạch/source được chủ dự án giao và xác định lại HEAD/test tương ứng; không lấy build xanh hiện tại để bỏ gate.

~~~text
MELOVAULT — THỰC THI S7, TASK_ID 2026-10-29-melo-ui-ux-ke-hoach.
Dùng model/effort tôi chọn, giữ vai THỰC THI; không tự chuyển sơ bộ.
Worktree C:\Users\ntdun\.codex\worktrees\melo-ui-ux-plan\MeloVault-E91-Shareable.
Nhánh dot/2026-10-29-melo-ui-ux-ke-hoach; HEAD lấy từ bàn giao S6 và phần gỡ BLOCKED mới nhất.
Đọc bàn giao/báo cáo mới nhất, mục S7 kế hoạch chính và phụ lục; luật29/10 tại c9a1c09 bằng git show nếu working files còn cũ, không merge integration.
Kiểm status/branch/HEAD/log-5/diff-check/agent-build; thay đổi lạ thì dừng, không reset/clean/stash/restore đè.
Xác nhận BLOCKED order đã có bằng chứng xử lý và source/test cuối tương ứng; chưa có thì dừng đúng phần, không tự sửa service hoặc bỏ tiêu chí.
Thực thi S7 theo gate: source/test/build đúng commit; metadata APK/package/version/cert/config boolean; không đọc/log bí mật.
Bundle và SHA256 hai đầu, clone Tiny cô lập, config chỉ ở Tiny; không chạm repo gốc/copy session/cache.
Chỉ cài sau gates đúng máy/serial/model theo luật, không dùng Sony thay Shanling; QA1–6 với hai màu/font/layout và âm thanh/offline đúng bằng chứng.
Giữ artifact cũ, log/ảnh private; màn OFF trong finally và xác minh cuối; 00_tong_hop.png và Drive Ni N owner-only sau khi đủ điều kiện.
Thiếu Tiny/Drive/thiết bị/gate thì BLOCKED đúng phần; không dùng APK cũ thay source mới.
Cập nhật báo cáo/bàn giao, commit trên nhánh nhiệm vụ rồi dừng; không PASS chính thức/merge/push/P2.
Bàn giao prompt sơ bộ cho phiên mới; chủ dự án nghiệm thu chính thức.
~~~


- **S5b xong code/test mục tiêu**, nền `3615d643593d4650b36addfacc8d6b122d5328a0`; commit riêng chứa mốc này. Policy facts/checker owner+epoch+revision, worker stat, READY từ completion thật+owner+readable/nonempty/size; path-only queue UNKNOWN; không đổi cache/service/sync. Các trạng thái mới đồng nhất ở list/queue/now, offline chọn bản hợp lệ và giữ index.
- Test cuối `S5b-final-20261029_163743`: Debug/Release mỗi biến thể58 passed/0 skipped/0 failed/0 errors, 6 suites, exit0, XML fresh/source.json match; log/result/XML artifacts cùng tên. Không code dở/test đang chạy, artifact không commit. File: DownloadUiPolicy mới/test, Activity, Host, Components, LibraryPane, StudioDeck và hai hồ sơ.
- **Bước tiếp S6**: queue renderer tái dùng/typed refresh, paused/buffering, queue khung phải tablet/Back giữ browse, dock/font/contrast opt-in; đối chiếu queue order với service trước khi khẳng định. Full unit Debug/Release rồi assemble hai biến thể, log duy nhất; không làm S7/cài/QA/merge/push. Chưa kiểm chứng UI/audio/offline thật; màn hai máy không thao tác/chưa xác minh. Các dòng S5a/bước tiếp lịch sử dưới đây đã được thay bằng mốc này.


- Checkpoint đang làm **S5a**, tiếp tục sau chỉ thị “tiến hành tiếp” 16:01 +07: HEAD vẫn `cfd0bd3160dcad183d3e12c2d9bc8acebeb264c2`; phần chưa commit trước nối tiếp chỉ policy feedback và state mới do chính phiên này viết, không có file lạ/worker nền. S2/S3/S4 đã commit, không làm lại.
- **S5a xong code/test mục tiêu**, commit riêng chứa checkpoint này; bước tiếp **S5b** đã được chủ dự án giao. Mốc này thay các dòng bước tiếp S4/S5a lịch sử ở dưới. PlayAttempt owner/generation/key, gate FLOOD_WAIT deadline monotonic, inline retry; timeout12s/stall15s và writer/service/sync/cache algorithm giữ nguyên. Callback download mang owner/gen, không xóa song trước kiểm; chỉ hủy luồng cũ khi bài mới resolve/cache hợp lệ.
- Test cuối S5a `S5a-checkpoint-20261029_161346` dưới artifacts TASK_ID: Debug/Release mỗi biến thể77 passed/0 skipped/0 failed/0 errors, 6 suites, exit0, source.json match, XML fresh; không còn test chạy. Lượt đầu `S5a-20261029_160943` giữ riêng, không thay source cuối. File commit: PlayUiState mới/test, PlayFeedbackPolicy/test, Host, Activity, LibraryPane (inline view), TabletScreen, StudioDeck và hai hồ sơ; không code dở/không artifact. Chưa APK/ADB/FLOOD thật/audio; màn hai máy chưa xác minh vì không thao tác.
- Bước tiếp chính xác: đọc riêng phụ lục S5b, đối chiếu mọi nơi ghi cachedSongPaths với completed/verified scan + owner, dựng facts UI kiểm file ở nền. Thiếu facts phải ghi BLOCKED đúng phần, không đoán READY từ path/percent hoặc sửa cache/service để vượt kế hoạch.

- Chủ dự án xác nhận S0–S1 đã làm xong và giao liên tục S2 → S3 → S4 → S5a → S5b → S6, mỗi chặng test/ghi hồ sơ/commit riêng; không cần xin lại trong phạm vi kế hoạch. Không phải xác nhận PASS chính thức.
- Đầu phiên 11:19 +07: cây sạch, đúng nhánh/worktree bên dưới; HEAD `fb896d4c537d007c633d9008f3a95ee521d9784f`; log -5/diff-check khớp S1. Không có wrapper/worker hoặc process tham chiếu worktree; Gradle/Kotlin daemon thường trú PID 27832/27228 từ phiên trước.
- Đã đối chiếu S1 và luật tại c9a1c09. **S2 e18979f; S3 12d5fd2554eafe1be848bb8c6892a1d8b9cf1e21; S4 xong code/test mục tiêu**, commit riêng chứa checkpoint này; bước tiếp **S5a** đã được chủ dự án giao. HEAD/source tiếp nhận tra commit chứa file bàn giao/Activity, không quay về S1.
- Giới hạn mới: không S7/P2, không ADB/cài thiết bị, không đổi vai/sơ bộ/merge/push/quy trình. S6 chạy full Debug/Release unit + assemble Ni; không xem build xanh là QA UI/audio.
- Agent chính là người duy nhất sửa file; sub-agent chỉ đọc kiểm kê S2 đã xong. Không còn lệnh test chạy; daemon thường trú giữ nguyên.
- S2 giao nộp: mới `MeloBrowseState.kt` / test; sửa Activity, LibraryPane, ScreenHost, StudioDeck và hai hồ sơ. Không còn code dở của S2; tất cả commit cùng checkpoint, artifact bị loại. Từ đây source có state owner+scope, LRU16, query/sort/anchor và cuộn nguồn riêng, restore Bundle owner-verified, debounce flush một lần.
- Test cuối: `S2-checkpoint-20261029_113203` dưới artifacts TASK_ID; Debug/Release mỗi biến thể 126 passed/0 skipped/0 failed/0 errors, 7 suites; exit 0, XML fresh, source.json khớp từng file. Báo cáo có lệnh/phạm vi và các lượt trung gian.
- Chưa QA xoay thật; deep anchor sau process recreation chờ trang người dùng nạp, không tự tìm vô hạn. SHANLING_SCREEN_END / SONY_SCREEN_END: không thao tác, chưa xác minh màn. Không APK mới ở S2. Tiếp theo đọc riêng phụ lục S3, giữ player trái và đưa nguồn vào vùng duyệt phải.
- Mốc S3 thay dòng bước tiếp ở trên: player trái không còn bị quickPane thay; nguồn bên phải dùng ListView adapter lazy, count>0/nhóm 0/unknown đúng; thường/Teal cùng bốn tab, scope toàn cục khác lọc trong kênh, Back về ngữ cảnh S2. Host/Activity route nguyên tử, state bổ sung source chatId+offset; không thay sync/player/layout threshold.
- Test S3 cuối `S3-checkpoint-20261029_113957`: mỗi biến thể 130 passed/0 skipped/0 failed/0 errors, 6 suites, exit 0; source.json match, XML fresh; không còn test chạy. File S3: TabletScreen, LibraryPane, LibraryPolicy + test, BrowseUiPolicyTest, ScreenHost, Activity, BrowseState và hai hồ sơ; không code dở/artifact trong commit. Bước tiếp chính xác: đọc phụ lục S4, tái dùng requestPlaylistToggle/SaveToggleGuard để lưu nhanh theo identity. QA1/QA2 chưa chạy thật, không APK/ADB.
- Mốc S4 thay dòng bước tiếp cũ: Row ☆/★/busy/availability, direct đủ 160dp body + hai nút48dp/font≤1.3, fallback ⋮; scope/player không đổi khi save. Target owner/key/chat/raw message của representative; cùng writer/guard/afterSave; star chỉ từ commit, callback guard/owner/lifecycle cũ bị bỏ, ngoài Playlist chỉ notify không reload list.
- S4 log `S4-20261029_114717` dưới artifacts TASK_ID: Debug/Release mỗi biến thể 122 passed/0 skipped/0 failed/0 errors, 7 suites, exit0; source.json match, XML fresh; không tác vụ test còn chạy. File: ScreenHost, Activity, Components, LibraryPane, SavePolicy + RowSavePolicyTest mới, hai hồ sơ; không phần code dở. Chưa QA3/keyboard/TalkBack thật, không APK/ADB. Tiếp theo đọc riêng S5a, thêm feedback attempt/generation và retry deadline, không đổi service/timeout/sync.

## Checkpoint hiện tại — S0–S1, 29/10/2026, sau test cuối 11:07:31 +07

**S0–S1 đã xong code/test mục tiêu; chưa QA UI hoặc tạo candidate. Phiên thực thi dừng sau commit checkpoint này.** Không dùng prompt S0–S1 lịch sử bên dưới để làm lại; không coi checkpoint này là lệnh triển khai S2.

| Mục | Giá trị đã kiểm |
|---|---|
| Vai/công cụ | THỰC THI, Codex; model/effort không xác định từ metadata, không tự đổi |
| Worktree | `C:\Users\ntdun\.codex\worktrees\melo-ui-ux-plan\MeloVault-E91-Shareable` |
| Nhánh | `dot/2026-10-29-melo-ui-ux-ke-hoach` |
| Baseline source | `7f3cca874e525956a10f94f8caf577c6cc80f815` |
| Commit kế hoạch / HEAD trước sửa | `f912ef5eca8574deb304872af73a2f7ad5856f95` |
| Source S1 / HEAD tiếp nhận | Commit chứa code và checkpoint này; lấy hai SHA bằng lệnh bên dưới, sau đó kiểm HEAD thực |
| Báo cáo | [Báo cáo S0–S1](2026-10-29-melo-ui-ux-ke-hoach-bao-cao.md) |
| Quy tắc áp dụng | Bốn file luật 29/10 tại `c9a1c09`, đọc bằng Git; không merge nhánh chứa source E105 khác baseline |
| Trạng thái chính thức | **CHỜ CHỦ DỰ ÁN NGHIỆM THU**; chưa có kết luận sơ bộ/PASS |

~~~powershell
git log -1 --format=%H -- docs/plans/2026-10-29-melo-ui-ux-ke-hoach-ban-giao.md
git log -1 --format=%H -- app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/TelegramPreviewActivity.kt
git status --short
git branch --show-current
git rev-parse HEAD
git log -5 --oneline
git diff --check
~~~

| Bước | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| S0 | Xong | Cây sạch trước sửa, đúng HEAD/baseline; không có build/agent tham chiếu worktree; phiên kế hoạch đã dừng |
| S1 policy/adapter | Xong code | Bảy scope, ID nguồn, initial/update/ready/error, count null, guard owner/generation/lifecycle, retry đúng scope |
| S1 metadata | Xong code | Nghệ sĩ thật tách Kênh; thư mục LOCAL có nhãn; opt-in list Phone/Tablet, mặc định Gọn/Car giữ cũ |
| S1 test | Xong mục tiêu | Debug 220 passed/0 skipped/0 failed/0 errors; Release cùng kết quả, 14 suite/biến thể |
| S2–S6 | Chưa làm | Chỉ thực thi khi chủ dự án giao chặng; S6 có full unit/assemble tích hợp |
| S7 / sơ bộ / chính thức | Chưa làm | Candidate/build/QA/Drive rồi phiên sơ bộ riêng; chủ dự án nghiệm thu |

- Hai bổ sung phạm vi được chủ dự án xác nhận trực tiếp trong hội thoại 29/10: (1) tối thiểu `MeloLocalLibrary.kt` + test; (2) cờ kết quả đọc `LocalMediaRepository.kt` + test. Đã gỡ hai BLOCKED về lỗi LOCAL bị che, không đổi truy vấn/cache/hợp đồng trả về cũ.
- Phần giao nộp: 7 file production (policy mới, host, Activity, pane, components, local library, repository), 3 file test (policy mới, local library, repository), báo cáo và file bàn giao này. Không có code còn dở; các file kế hoạch/luật giữ nguyên. Tất cả phần việc được commit cùng checkpoint; khi tiếp nhận phải kiểm status thực, không nhận file lạ.
- Lượt cuối: 11:07:11–11:07:31 +07, exit 0, BUILD SUCCESSFUL in 19s. Log `artifacts/2026-10-29-melo-ui-ux-ke-hoach/s1-targeted-20261029_110711.log`; XML riêng `s1-results-20261029_110711/`; manifest `s1-source-20261029_110711.txt`, SHA-256 `022977A92D683B1E473838AA437F7D082052F9E5822F14173F99850998FF1951`. Đã kiểm từng hash source/test sau test, tất cả khớp; sau đó chỉ sửa hồ sơ.
- Lượt đầu 219 passed mỗi biến thể, exit 0; log/XML/manifest `20261029_110215` vẫn giữ. Sau lượt đầu có chỉnh giữ rows qua scan/paging và test callback; chỉ lượt cuối đại diện source bàn giao.
- Không có wrapper/test worker/build/ADB nền đang chạy tiếp. Gradle daemon PID 27832 và Kotlin daemon PID 27228 còn thường trú sau lệnh test đã exit; không phải candidate đang build. Một sub-agent chỉ đọc kiểm kê test đã kết thúc; agent chính là người duy nhất sửa file.
- Commit đầu bị từ chối vì thiếu danh tính Git; ba commit gần nhất xác minh cùng `Codex <codex@openai.com>`, dùng lại bằng tùy chọn `git -c` chỉ cho lệnh commit, không thay cấu hình global/local. Source test không đổi.
- Không tạo/cài APK, không ADB/Tiny/Drive, không có ảnh/video/00_tong_hop.png; không xác nhận app đang cài. SHANLING_SCREEN_END và SONY_SCREEN_END: **không thao tác, chưa xác minh trạng thái màn**.
- Chưa chứng minh runtime thường/Teal, tablet thật, hiệu năng, audio/offline; test JVM không thay QA UI. Chưa full suite/assemble; S1 chưa là candidate. E105 không được nghiệm thu bằng nhiệm vụ này.
- Bước chưa làm đầu tiên trong kế hoạch là S2 giữ ngữ cảnh duyệt; chỉ đọc phụ lục S2 và source cần thiết khi chủ dự án giao phiên thực thi mới. Không tự chạy S2–S7/P2, sơ bộ, merge/push hoặc sửa quy trình.

Đã dừng, bàn giao phiên thực thi tiếp theo.

Phần bên dưới là **lịch sử phiên lập kế hoạch**, không thay checkpoint hiện tại ở trên.
Ngày: 29/10/2026. Người lập: Codex; model/effort không xác định từ metadata.
Vai đã thực hiện: **lập kế hoạch**, không code/build/QA.

## 1. Đọc nhanh và quyết định còn hiệu lực

- Chủ dự án giao viết kế hoạch chi tiết để model tiết kiệm token hơn lập trình ở phiên mới; sau đó yêu cầu áp dụng quy tắc mới 29/10 và tiếp tục phần tài liệu bị gián đoạn.
- Codex/Claude ngang quyền, không cố định model theo loại nhiệm vụ; chủ dự án chọn model/effort và chặng.
- Không tự chuyển từ lập kế hoạch sang code. Phiên thực thi kết thúc bằng bàn giao phiên sơ bộ mới; chủ dự án nghiệm thu chính thức.
- Ưu tiên Phone/Tablet; giữ Melo, Android Views; Gọn/Car chỉ hồi quy.
- P1 gồm nguồn/tìm kiếm/ngữ cảnh/lưu nhanh/trạng thái phát–tải/hàng chờ.
- P2-A ảnh bìa theo màu có phương án nhưng chưa được giao code. P2-B lời nhạc phải chốt dịch vụ và chính sách dữ liệu/cache trước. Nhiều Playlist có tên, ghim nguồn chỉ nghiên cứu.

## 2. Worktree, baseline, commit

| Mục | Giá trị |
|---|---|
| Worktree tiếp tục | C:\Users\ntdun\.codex\worktrees\melo-ui-ux-plan\MeloVault-E91-Shareable |
| Nhánh | dot/2026-10-29-melo-ui-ux-ke-hoach |
| Source baseline | 7f3cca874e525956a10f94f8caf577c6cc80f815 |
| HEAD lúc bắt đầu/lúc khảo sát | Cùng source baseline trên |
| Commit kế hoạch/checkpoint | Commit chứa file này, tra bằng lệnh dưới; tránh SHA tự tham chiếu |
| Source dùng build | Chưa có build của nhiệm vụ này |
| Repo đầu vào | D:\Bots\MeloVault-E91-Shareable, chỉ đọc; không lập trình tại nhánh đầu vào |

~~~powershell
git log -1 --format=%H -- docs/plans/2026-10-29-melo-ui-ux-ke-hoach-ban-giao.md
git log -1 --format=%H -- docs/plans/2026-10-29-melo-ui-ux-ke-hoach.md
git status --short
git branch --show-current
git rev-parse HEAD
git log -5 --oneline
git diff --check
~~~

Khi nhận S0–S1 lần đầu, HEAD phải là commit tài liệu đã bàn giao; diff từ 7f3cca8 chỉ có bốn tài liệu liệt kê bên dưới. Nếu có code mới/HEAD lạ, đối chiếu checkpoint và chủ dự án trước, không reset/clean/stash/restore.

**Quy tắc mới không nằm trong working file tại baseline này.** Chủ dự án đã trực tiếp yêu cầu áp dụng bản 29/10. Đọc bằng Git, không merge nhánh tích hợp:

~~~powershell
git show c9a1c09:CLAUDE.md
git show c9a1c09:AGENTS.md
git show c9a1c09:docs/workflow/QUY_TRINH_3_VAI_TRO.md
git show c9a1c09:docs/workflow/PROMPT_CODEX.md
~~~

Nhánh c9a1c09 có production khác E105 so với 7f3cca8; không lấy cả nhánh để “đồng bộ luật”. Nếu ref luật không còn resolve được, báo thiếu nguồn, không tự quay lại phân vai cũ.

## 3. Bộ hồ sơ và checkpoint

| File | Vai trò / trạng thái |
|---|---|
| [Kế hoạch chính](2026-10-29-melo-ui-ux-ke-hoach.md) | Phạm vi, baseline, lựa chọn UI, thứ tự chặng, build/QA/rollback |
| [Phụ lục thực thi](2026-10-29-melo-ui-ux-ke-hoach-chi-tiet.md) | File/hàm, hợp đồng dữ liệu, bước nhỏ, test, điều kiện hoàn tất theo S0–S7 và P2 |
| [Nguồn video](2026-10-29-melo-ui-ux-ke-hoach-nguon-video.md) | Snapshot nguyên văn, chỉ đọc khi cần truy nguồn yêu cầu |
| File bàn giao này | Checkpoint, phạm vi kế tiếp và prompt copy dùng ngay |

| Bước | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| Đọc luật, kiểm Git | Xong | Baseline 7f3cca8; quy tắc c9a1c09; worktree riêng |
| Đọc tài liệu nguồn | Xong | SHA-256 snapshot khớp bản D: |
| Khảo sát source | Xong trong phạm vi lập kế hoạch | Mục 4 kế hoạch; các hàm cụ thể trong phụ lục |
| Viết plan/phụ lục | Xong | Hai file có thật, không chỉ nội dung chat |
| Tạo snapshot/bàn giao | Xong | Bộ bốn tài liệu này |
| S0–S6 code/test | Chưa làm | Chờ phiên thực thi do chủ dự án mở |
| S7 build/QA/APK | Chưa làm | Không có candidate mới, không có link Drive |
| Sơ bộ/chính thức | Chưa làm | Không có PASS hoặc kết luận sơ bộ |
| P2-B | Chưa đủ quyết định để code | Năm điểm chốt trong phụ lục P2-B |

## 4. File chưa commit, tác vụ và thiết bị

- Phần viết trong phiên này chỉ bốn file docs/plans/2026-10-29-melo-ui-ux-ke-hoach*.md liệt kê ở mục 3; được commit cùng đợt bàn giao kế hoạch. Sau commit, chuẩn nhận việc là cây sạch; kiểm thực tế bằng Git, không suy từ câu này.
- Repo D: vẫn có sẵn .codex-remote-attachments/ và docs/plans/2026-10-29-hoc-hoi-ui-ux-telegram-music.md chưa tracked. Không sửa/xóa/commit chúng; snapshot mới được tạo trong worktree.
- Snapshot nguồn SHA-256: AA648F099D43428BFE11832C63E964271454B6E5AF18D501D040D97D161A95BA.
- Git blob của snapshot: 1a6804a83df672c333b6f90d928e4f457cadaa7a. SHA-256 trên là byte lúc sao chụp; checkout Windows có thể chuẩn hóa LF/CRLF, khi đó đối chiếu blob để phân biệt đổi xuống dòng với đổi nội dung.
- Không có tác vụ build/test/ADB/Tiny do phiên này khởi chạy. Kiểm process Java/javaw/Gradle tại thời điểm hoàn thiện không thấy tiến trình tương ứng; phiên nhận vẫn phải kiểm lại.
- Không dùng sub-agent; không mở phiên code hoặc nhắn agent khác.
- Agent lập kế hoạch kết thúc sau commit; chủ dự án giao phiên mới sau khi nhận lời kết. Không có công việc nền của phiên này.
- SHANLING_SCREEN_END: không thao tác thiết bị, chưa xác minh trạng thái màn.
- SONY_SCREEN_END: không thao tác thiết bị, chưa xác minh trạng thái màn.
- Không tạo APK/ảnh QA; không xác nhận bản đang cài. Báo cáo thực thi/APK/sơ bộ tạo khi đến đúng giai đoạn, không tạo hồ sơ giả.

## 5. Rủi ro, giới hạn và bước tiếp

1. E105 trong baseline chưa được phiên này xác minh ĐẠT; không thay kết luận lịch sử hoặc lấy plan UI làm bằng chứng nghiệm thu sync.
2. Restore UI phải dùng ID + owner/generation; deep pagination sau process recreation có giới hạn đã ghi ở S2.
3. Đã tải cần phân biệt cached path và completion thật. S5b chỉ adapter UI/facts, không sửa cache/service; thiếu facts thì báo điểm chặn.
4. Queue hiện map từ queueSongOrder; nếu cần sửa service/order thì tách quyết định, không lén mở rộng S6.
5. LRCLIB docs trả HTTP403 khi mở trong phiên khảo sát; không coi hợp đồng dịch vụ đã được kiểm chứng.
6. Chưa có QA thiết bị/video/audio/offline/hiệu năng mới. Chủ dự án được đọc kế hoạch nhưng đó không phải nghiệm thu APK.
7. Bước tiếp chính xác: **phiên thực thi mới nhận S0–S1**, làm đúng scope loading/search/metadata, ghi checkpoint và dừng. Không làm S2/P2 khi prompt chỉ giao S1.

## 6. Prompt thực thi S0–S1 — copy sang phiên mới

~~~text
MELOVAULT — THỰC THI ĐÚNG KẾ HOẠCH, CHẶNG S0–S1

TASK_ID: 2026-10-29-melo-ui-ux-ke-hoach
Worktree: C:\Users\ntdun\.codex\worktrees\melo-ui-ux-plan\MeloVault-E91-Shareable
Nhánh: dot/2026-10-29-melo-ui-ux-ke-hoach
Baseline source: 7f3cca874e525956a10f94f8caf577c6cc80f815
Kế hoạch: docs/plans/2026-10-29-melo-ui-ux-ke-hoach.md
Chi tiết: docs/plans/2026-10-29-melo-ui-ux-ke-hoach-chi-tiet.md, chỉ S0–S1
Bàn giao: docs/plans/2026-10-29-melo-ui-ux-ke-hoach-ban-giao.md
Commit kế hoạch/HEAD dự kiến: commit chứa bộ bốn file tài liệu;
xác định bằng git log -1 --format=%H -- <file bàn giao>, kiểm diff với baseline.
Model/effort: dùng lựa chọn của tôi trong phiên này; không tự đổi.

Tôi áp dụng quy tắc 29/10: Codex và Claude ngang quyền, không khóa model
theo nhỏ/lớn; các vai ở phiên riêng. Working files luật ở baseline còn cũ:
đọc git show c9a1c09:CLAUDE.md, c9a1c09:AGENTS.md và quy trình/PROMPT_CODEX
cùng ref, không merge nhánh tích hợp để lấy quy tắc.

Phiên lập kế hoạch đã dừng, không có build/ADB nền do phiên đó chạy.
Kiểm Git/agent và đối chiếu bàn giao trước khi sửa.
Tôi giao thực thi S0–S1: scope tìm kiếm, loading/empty/error, metadata dòng.
Không tự làm S2–S7 hoặc P2, không refactor ngoài phạm vi.

Đọc kế hoạch chính một lần, sau đó chỉ file/hàm và test cần cho S1.
Tạo báo cáo thực thi, cập nhật bàn giao sau từng mốc và khi sắp hết quota.
Chạy test mục tiêu Debug/Release cho phần đổi; ghi passed/skipped/failed/errors,
log và source tương ứng. S1 chưa phải candidate cuối nên không cài thiết bị.
Đừng nhận BUILD SUCCESSFUL là QA UI; full gate candidate theo mục 6 kế hoạch.

Giữ package/signer/versionCode, DB/schema, songKey, owner/session,
service/playback và thuật toán sync/cache. Gặp thiếu phạm vi/HEAD lạ thì
ghi BLOCKED đúng phần, không đoán, không reset/clean/stash.
Commit đúng phần S1 + hồ sơ rồi dừng và bàn giao chặng chưa làm.
~~~

## 7. Prompt sơ bộ — chỉ dùng sau S7

Phiên thực thi cuối phải điền **source SHA, APK SHA-256, link và phạm vi thật** vào prompt sau; các ô chưa có dữ liệu không được dùng để bắt đầu kiểm APK:

~~~text
MELOVAULT — NGHIỆM THU SƠ BỘ NHANH, PHIÊN MỚI
TASK_ID: 2026-10-29-melo-ui-ux-ke-hoach
Worktree/nhánh: như bàn giao đã cập nhật.
Source SHA: <điền sau thực thi>; APK SHA-256 và link: <điền sau S7>.
Đọc luật 29/10, bàn giao, kế hoạch/báo cáo và hồ sơ APK/ảnh.
Kiểm diff và các file rủi ro đã liệt kê; chỉ chạy lại test/QA khi có lý do.
Không sửa code/build mới; ghi kết luận sơ bộ và giới hạn vào
docs/plans/2026-10-29-melo-ui-ux-ke-hoach-nghiem-thu-so-bo.md.
Không ghi PASS chính thức; bàn giao chủ dự án nghiệm thu.
~~~

Đã dừng. Mở phiên mới, chọn model thực thi tiết kiệm hơn theo lựa chọn của chủ dự án.
