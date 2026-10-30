# Melo UI/UX — chỉ dẫn thực thi theo chặng

Phụ lục của [kế hoạch chính](2026-10-29-melo-ui-ux-ke-hoach.md), baseline `7f3cca8`.
**Đây là thiết kế đề xuất để chủ dự án giao thực thi; không phải code đã tồn tại.**
T = `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/`.
U = `app/src/test/java/com/bsdung/musicassistantcar/data/telegram/`.
Tên file/hàm đánh dấu **mới** là đầu ra cần tạo; các tên còn lại đã được đọc trong source.

## S0 — nhận việc và giới hạn

1. Đọc bàn giao, luật ngày 29/10 qua `git show c9a1c09:CLAUDE.md` và các file luật được dẫn trong kế hoạch. Kiểm status/branch/HEAD/log/diff-check và tác vụ còn chạy.
2. HEAD ban đầu phải là commit tài liệu kế hoạch trên source `7f3cca8`; `git diff --name-only 7f3cca8..HEAD` chỉ có bộ tài liệu kế hoạch trước S1.
3. Agent cũ đã kết thúc; phiên mới chỉ sửa khi không có agent khác đang code. Có file lạ không nhận làm của mình.
4. Tạo báo cáo thực thi ở giai đoạn này, dòng đầu theo quy trình; bảng S0–S7, mọi bước chưa làm ghi rõ.
5. Không đọc bí mật/TDLib data. Không khởi động build/ADB ở bước khảo sát. Ghi kết quả và chặng được giao trong bàn giao.

**Xong khi:** baseline, nhánh, source và vai khớp; không có phần code ngoài kế hoạch.

## S1 — phạm vi tìm kiếm, loading và thông tin dòng

### File được sửa/tạo

- T/MeloScreenHost.kt: mở rộng Snapshot bằng dữ liệu UI với giá trị mặc định; không xóa API cũ.
- T/TelegramPreviewActivity.kt: meloHost.snapshot, localSnapshot, refreshSongRows, discoverOffline; chỉ adapter dữ liệu UI/trạng thái query.
- T/MeloLibraryPane.kt: onLibraryChanged, RowsAdapter, layout ghi chú.
- T/MeloBrowseUiPolicy.kt **mới**; U/MeloBrowseUiPolicyTest.kt **mới**.
- T/MeloComponents.kt/MeloComponentPolicy.kt chỉ sửa helper metadata nếu cần; giữ mặc định Gọn/Car, không đổi palette chung.

### Hợp đồng dữ liệu đề xuất

`BrowseScope` thuần: LIBRARY, CHANNEL(chatId), DOWNLOADED(optional chatId), PLAYLIST, SOURCES, SAVED_MESSAGES, LOCAL.
`BrowseLoadState`: INITIAL_LOADING, UPDATING, READY, ERROR; luôn gắn owner/generation của truy vấn đang hiển thị.
Snapshot thêm scope/loadState; không suy loading từ rows rỗng, số bài=0 hoặc từ chuỗi note.

- INITIAL_LOADING + 0 rows → “Đang tải danh sách…”.
- UPDATING + đã có rows → giữ rows và ghi “Đang cập nhật danh sách…”.
- READY + 0 rows + query có nội dung → “Không tìm thấy bài trong <phạm vi>”.
- READY + 0 rows + query rỗng → “Chưa có bài trong <phạm vi>”.
- ERROR → “Chưa tải được danh sách. Thử lại”; giữ dữ liệu cũ nếu có, không gọi đó là kết quả mới.
- Đã tải đang scan → “Đang kiểm tra tệp trên máy…”; không phải “Không có bài”.
- LOCAL dùng trạng thái thật từ MeloLocalLibrary, không trộn loading Telegram.
- Số lượng null = chưa biết; 0 chỉ sau dữ liệu có thẩm quyền. Không giả N.

### Bước nhỏ

1. Viết policy cho scope/nhãn và unit tests; CHANNEL bắt buộc ID, title chỉ để hiển thị.
2. Khi refreshSongRows bắt đầu, đặt trạng thái cho generation hiện tại. Trong từng callback DB/scan thành công/lỗi, chỉ publish nếu generation, account, lifecycle còn khớp. Paging đang tải thêm không che danh sách hiện tại.
3. Không thêm truy vấn DB/TDLib chỉ để biết loading. Tái dùng các nhánh hoàn tất đang có; không đổi điều kiện lọc hoặc sort ở chặng này.
4. Hint: “Tìm trong thư viện đã đồng bộ…”, “Tìm trong kênh <tên>…”, “Tìm trong Đã tải…”, “Tìm trong Playlist…”, “Tìm nguồn Telegram…”, “Tìm trong Tin đã lưu…”, “Tìm trong Nhạc trên máy…”.
5. Khi vừa lọc Đã tải vừa trong kênh, dòng scope ghi cả hai; hint có thể ngắn nhưng không nói toàn thư viện. Query không dấu giữ đường xử lý hiện có.
6. Tên bài tối đa hai dòng ở Phone/Tablet đã có: giữ, không thiết kế lại. Nguồn ghi “Kênh: …” khi Telegram; nghệ sĩ chỉ dùng performer thật/local metadata. Thiếu metadata bỏ dòng/đoạn thiếu, không tạo Unknown artist hay chất lượng giả.
7. Empty/error đặt trong vùng list, không chặn playback; retry refresh đúng scope hiện tại.

**Test:** 7 scope; query rỗng/có query; initial/update/empty/error; source tên trùng ID khác; callback A về sau B bị bỏ; unknown count không thành 0; tên Việt không dấu không đổi kết quả; 2 bản trùng tên vẫn là 2 ID.

**Xong khi:** scope và empty state đúng ở cả màu thường/Teal; không gọi DB trên bind dòng; list không chớp trắng mỗi tick player.

## S2 — giữ ngữ cảnh duyệt, xoay và đóng/mở player

### File

- T/MeloBrowseState.kt **mới** + U/MeloBrowseStateTest.kt **mới**.
- T/MeloScreenHost.kt: bridge state/anchor tối thiểu.
- T/TelegramPreviewActivity.kt: meloListAnchor, onConfigurationChanged/onSaveInstanceState/onCreate, detachMeloLayer, openSource/showSources/setMode/setQuery, local open/close.
- T/MeloLibraryPane.kt: saveScrollAnchor/onLibraryChanged/detach.
- T/MeloStudioDeck.kt: trạng thái UI phần bổ trợ; không giữ đối tượng View/Activity trong state.

### Thiết kế

Tách ba ID: **bài hiện tại** từ MediaController; **bài đang mở** từ play feedback; **nguồn đang duyệt** từ BrowseScope.
Không dùng sourceTitle hay row index làm khóa identity.

BrowseKey = owner accountId + scope kind + chatId nếu có; trong state của key chứa query, sort, letterFloor, anchorSongKey, offsetPx, source-list anchor. LOCAL có key tách riêng. Giới hạn bộ nhớ 16 ngữ cảnh gần dùng (LRU); đây là cache UI RAM, không đổi DB/schema/TDLib cache.

- Trong cùng Activity và khi onConfigurationChanged rebuild layer: giữ bộ nhớ state và rows đang tải; không reload/reset chỉ vì render.
- onSaveInstanceState chỉ lưu trạng thái **đang mở** và phần cần quay lại khỏi Sources/deck: ID, query giới hạn 512 ký tự, enum, anchor, offset. Không serialize bitmap, hàng nghìn bài, File/path, credentials.
- Gắn owner vào saved state. Chỉ restore sau khi account hợp lệ và khớp; khác owner hoặc chưa xác minh → bỏ restore, không đọc UI của tài khoản cũ.
- saved state không khôi phục playback/seek/playWhenReady. Player service là nguồn duy nhất.
- Đổi source/tab bằng người dùng: lưu state cũ trước, restore state từng ghé hoặc default. Mở/đóng Đang phát không làm đổi BrowseKey.
- Query chưa hết debounce lúc rotate/detach phải được lưu và flush **một lần**, không để callback cũ chạy sau restore.
- Query/sort thay đổi chủ động: reset anchor; update dữ liệu cùng query hoặc xoay: không reset.
- Restore anchor khi đúng generation và bài đã có trong rows; chưa có → giữ pending, không xóa ở batch đầu.
- Xoay không tự reset phân trang/visible limit. Khi process recreation chỉ nạp trang chuẩn; nếu anchor nằm sâu, không tải vô hạn để truy tìm. Giữ pending trong các trang người dùng nạp; nếu dataset đã kết thúc mà không có anchor, bỏ và về đầu, ghi giới hạn này.
- Giữ cuộn source list riêng khỏi song list; chọn source không nhảy vị trí nguồn vô cớ.
- Deck mặc định QUEUE; chỉ có QUEUE trong P1. State có thể lưu enum đó; không tạo tab Lời nhạc giả để thử restore. P2 sẽ mở rộng.

### Test/QA

1. A(query,sort,anchor) → B → A phục hồi đúng; hai nguồn cùng tên không dùng chung state.
2. Phone → Hai khung → Phone giữ key/query/anchor và mediaId; back/đóng deck không pause.
3. Callback cũ + account khác không restore; state hỏng/enum lạ fallback an toàn.
4. Data batch đầu chưa chứa anchor: không tiêu pending; batch sau có → restore một lần; query đổi thì hủy.
5. Rotate giữa debounce không mất ký tự, không gửi hai query; xóa query cũng phục hồi chuỗi rỗng đúng.
6. Evict key thứ 17 chỉ bỏ UI state cũ, không tác động playlist/DB.

**Xong khi:** QA2 trên dữ liệu đã nạp giữ ngữ cảnh qua 3 lần xoay; process recreation ghi rõ giới hạn deep pagination, không hứa giữ mọi hàng chưa nạp.

## S3 — tablet giữ player, chọn nguồn trong vùng thư viện

### File

T/MeloTabletScreen.kt: init, buildNav/onNav/renderNav, buildQuickPane/renderQuickSwitch.
T/MeloLibraryPane.kt: renderSources, header/tab handlers.
T/MeloLibraryPolicy.kt: policy navigation/source presentation.
U/MeloLibraryPolicyTest.kt; U/MeloBrowseUiPolicyTest.kt.

### Bố cục cụ thể

- Hai khung tiếp tục trái phát / phải duyệt, giữ tỷ lệ hiện tại làm điểm bắt đầu; **không sửa ngưỡng chọn layout** của MeloScreenPolicy.
- Player trái luôn tồn tại; remove hoặc ngừng mount quickPane trái cho lớp Phone/Tablet mới. Không sửa đường Gọn/Car.
- Phải: hàng scope/tab → search đúng scope → vùng linh hoạt list bài **hoặc** list nguồn → ghi chú tải/lỗi. Controls phát không nằm trong vùng cuộn này.
- Nguồn dùng ListView/adapter cuộn riêng trong vùng phải ở Hai khung; không dựng tất cả kênh thành hàng nghìn View. Portrait có thể giữ renderer hiện có trong P1 nếu không cần thay.
- Chỉ các nguồn nhạc đã có count>0 từ host.channels/sources; nhóm 0 bài vẫn thu gọn theo helper hiện có. Chưa biết số không gán 0. Không lấy toàn bộ chats từ TDLib lần nữa.
- Nguồn đang chọn có selected + chữ/biểu tượng; màu playing của dòng bài chỉ do player. Hai highlight không có cùng nghĩa.
- Tab Thư viện/Đã tải/Playlist toàn cục dùng một hành động rõ nghĩa trong host (có thể thêm `openBrowseScope(scope)` mới), đặt đủ các cờ allChatsMode/browseSources/playlistMode/filterOnlyDownloaded/selectedChatId rồi refresh **một lần**.
- Không gọi nối openSource + setMode gây hai load/clear query; route qua S2 restore. Nút lọc trong nguồn là scope riêng có chatId.
- Teal dùng bốn tab sẵn có; màu thường chuẩn hóa hàng chip thành bốn lối vào. Tránh 2 thanh khác nhau cùng bật hai mục chọn.
- “Nguồn” mở vùng chọn tạm; Back trở về ngữ cảnh bài trước. Chọn kênh xong mở bài của kênh đó. Đang nghe A vẫn nghe A.
- Khi pane hẹp/font lớn: tab cuộn ngang hoặc nhãn ngắn, source list thay vùng bài; không tạo cột thứ ba.
- Nút Hàng đợi trên player sẽ được S6 làm inline trong vùng phải; không làm mất controls.

**Test:** navigation chuyển đúng mode, cờ downloaded không rò sang Playlist/Thư viện; Saved Messages không bị lẫn all sources; selection nguồn khác playing; không có host.playAt/playPause/seekTo trong handler chỉ duyệt; nguồn 0 bài/đang loading/không có nguồn.

**Xong khi:** QA1 + QA2; player luôn hiện khi duyệt nguồn ở hai kiểu màu; không crash khi nguồn cập nhật trong lúc cuộn.

## S4 — ☆/★ lưu nhanh vào Playlist hiện hữu

### File

T/MeloScreenHost.kt: Row và API toggle theo identity.
T/TelegramPreviewActivity.kt: snapshot/localSnapshot, adapter toggle dòng, requestPlaylistToggle, notify sau ghi.
T/MeloComponents.kt: SongRowModel, songRow, tealSongRow, tag save mới.
T/MeloLibraryPane.kt: RowsAdapter/unclickable.
T/MeloSavePolicy.kt: wording cho dòng (giữ wording hiện tại của NowPlaying).
U/MeloSavePolicyTest.kt; U/MeloRowSavePolicyTest.kt **mới** nếu cần helper tách.

### Hợp đồng

- Row thêm saved:Boolean=false, saveBusy:Boolean=false, saveAvailability mặc định không actionable. Giữ constructor callers cũ qua default.
- API mới `toggleRowFavorite(songKey: Long)`; không nhận row position để quyết định sau async.
- Host đối chiếu đúng row/song hiện tại và owner; target = PlaylistTarget(songKey,chatId,rawMessageId) thật. Không tạo ID từ title, fileId hoặc vị trí.
- Gọi cùng `requestPlaylistToggle`. Giữ `SaveToggleGuard`, playlistSession và `afterSave()` E105; không thêm writer/scheduler/remote call.
- saved đọc favoriteIds sau applySavedPlaylist; không optimistic. busy bám target/guard; lỗi vẫn giữ trạng thái đã commit và có thông báo.
- Nhạc trên máy: không giả hỗ trợ lưu Telegram. Hành động trong ⋮ giải thích hạn chế, không biến local URI thành Telegram identity.
- Đã tải có nhóm nhiều bản: ☆ áp đúng **bản đại diện của dòng** và identity đang hiển thị. Không lưu cả nhóm hay tự chọn bản khác theo tên; ⋮ vẫn cho chọn bản theo cơ chế cũ.
- Dòng chữ trợ năng: “Lưu <tên> vào Playlist” / “Bỏ lưu <tên> khỏi Playlist”; không nói “bài đang phát” khi đó là bài chưa phát.

### Bước nhỏ

1. Policy chọn vị trí nút: direct khi width còn lại cho body ≥160 dp sau ảnh, khoảng cách, duration đo thật và 2 nút 48 dp; fontScale>1.3 ưu tiên ⋮. Đây là rule layout dự án, kiểm biên bằng test.
2. Thêm callback/tag `MELO_SONG_ROW_SAVE_TAG` mới, chỉ Phone/Tablet được opt-in. Gọn/Car/queue cũ default không có nút.
3. `unclickable` xử lý tag save trước khi đệ quy vô hiệu click; listener dùng key đã bind. Giữ ⋮ riêng, parent tap vẫn play, long-press vẫn menu.
4. Keyboard/TalkBack phải tới được save/menu; không để sửa click làm row không còn phát bằng touch. Nếu ListView focus cần tinh chỉnh, test thực tế, không đơn giản đặt mọi con focus=false.
5. Success/fail/remote playlist update refresh đúng rows + NowPlaying; không scroll/reset source, không redraw toàn library mỗi tick playback.
6. Bài đang nghe từ nguồn khác vẫn đổi save đúng identity; thao tác nhanh A/B không đẩy vào target mới do adapter recycle.

**Test bắt buộc:** save bài chưa phát không gọi playback; 2/3 taps cùng bài đúng parity guard; A→B khi write A đang chạy; failure/outbox full/playlist chưa sẵn sàng không hiện ★; stale account callback không cập nhật; narrow fallback; local từ chối; nhóm offline không mất bản trùng.

**Xong khi:** QA3 và suite Playlist/Save hiện có không đổi semantics; không gọi sync trực tiếp từ UI.

## S5a — mở bài, đệm, chờ và lỗi

### File

T/TelegramPlayFeedbackPolicy.kt; T/MeloPlayUiState.kt **mới** nếu cần tách state thuần.
T/TelegramPreviewActivity.kt: downloadAndPlay, reportPlayError, setPlayFeedback, notifyMeloPlayback, nhánh error download trong onNativeEvent.
T/MeloScreenHost.kt: snapshot feedback chỉ đọc và retry có identity.
T/MeloLibraryPane.kt/MeloStudioDeck.kt/MeloTabletScreen.kt: vùng inline feedback.
U/TelegramPlayFeedbackPolicyTest.kt; U/MeloPlayUiStateTest.kt **mới**.

### Hợp đồng và trạng thái

PlayAttempt = owner + attemptGeneration + songKey; mỗi hành động phát mới tăng generation. Kết quả chậm của A không được đổi feedback của B; ngay cả A→B→A cũng cần generation, không chỉ so key.

| Dữ kiện | Nội dung | Hành động |
|---|---|---|
| Resolve đang chạy | Đang mở… | Không tạo resolve thứ hai khi bấm liên tục cùng attempt |
| Current item đã có, đang buffering, playWhenReady=true | Đang đệm nhạc… | Giữ controls; không báo tạm dừng |
| isPlaying=true | Đang phát | Xóa opening đúng attempt |
| Có current, playWhenReady=false | Tạm dừng | Không coi là loading |
| Lỗi có FLOOD_WAIT_N hợp lệ | Telegram yêu cầu chờ N giây | Chặn retry hành động đó tới deadline |
| 429 không có thời gian parse được | Telegram đang giới hạn tốc độ. Thử lại sau. | Không bịa countdown; chỉ retry chủ động |
| Timeout/permission/missing message/download error | Câu cố định tiếng Việt theo loại | Retry chỉ cho lỗi tạm thời; không logout/clear |
| Lỗi cũ khi đổi nguồn | Còn gắn bài/attempt, không giả lỗi của nguồn mới | Player có thể tiếp tục A; lỗi B hiện riêng |

- Parse nguyên chuỗi whitelist `FLOOD_WAIT_([0-9]+)` trước nhánh code chung; dùng Long, kiểm overflow khi nhân 1000/cộng deadline; sai/âm/thiếu số → thông báo generic. Không hiển thị raw message, tên tệp, chat/message IDs.
- Dùng SystemClock.elapsedRealtime để deadline; UI tick chỉ khi đang visible, không sleep main thread. Không auto-retry sau countdown; hết hạn chỉ mở nút “Thử lại”.
- Gate retry đặt ở điểm vào action mở bài của host để tap dòng, nút retry và menu phát cùng tuân thời gian chờ cho cùng yêu cầu. Không tạo vòng gửi TDLib, không sửa owner/session/service/sync coordinator.
- Lỗi resolve và download đều vào UI state; currentDownloadingSong/attempt identity được chụp trước callback, không xóa rồi mới tìm songKey.
- notifyMeloPlayback hiện xóa feedback khi currentKey khớp: sửa để phân biệt đã nạp item và đã phát/paused/error; không xóa BUFFERING quá sớm.
- Inline feedback tối đa vài dòng và nút retry 48 dp; không AlertDialog phủ thư viện. Với Gọn/màn cũ giữ đường thông báo cũ nếu cần để không đổi bố cục.
- Không thêm trạng thái global enum làm các renderer Car bị đổi ngoài ý muốn: ưu tiên presentation/feedback riêng, default không có overlay.
- Không tăng timeout resolve/prefetch để che lỗi; giữ 12s/15s hiện có trừ quyết định riêng.

**Test:** FLOOD_WAIT_1/_60, code420/429, 429 không số, chuỗi chứa PII, số tràn/âm; retry trước/đúng/sau deadline với fake clock; A/B/A callbacks; buffering≠paused; lỗi B không dừng A; rotate không reset countdown; detach hủy tick.

**Nguồn:** [Telegram error handling](https://core.telegram.org/api/errors) quy định FLOOD_WAIT_N phải chờ N giây trước khi lặp hành động. Tài liệu API không chứng minh mọi lỗi TDLib đều dùng cùng chuỗi; chỉ parse định dạng đã xác minh, còn lại generic.

## S5b — Đã tải đáng tin cậy

### File

T/TelegramPreviewActivity.kt: songIsDownloaded, ownedCachedFile, discoverOffline publish, resolveOfflineBatch, snapshot, queue, các nhánh tải hoàn tất/updateFile.
T/MeloDownloadUiPolicy.kt **mới** và U/MeloDownloadUiPolicyTest.kt **mới**.
T/MeloScreenHost.kt: trạng thái tải UI đủ phân biệt unknown/ready/unavailable.
Đọc T/TelegramOfflineLibrary.kt, T/TelegramOfflinePlayPolicy.kt, T/TelegramProgressiveCache.kt và object TelegramAccountStoragePolicy trong `app/src/main/java/com/bsdung/musicassistantcar/data/telegram/TelegramAccountPartition.kt`; **không sửa thuật toán/cache/index này trong P1**.

### Bước và bằng chứng

1. Đối chiếu mọi nơi ghi cachedSongPaths: nguồn dữ liệu, có bằng chứng completed không, account/generation nào. Baseline đã thấy: enqueueTelegramPlayback (~1182, chỉ nhận target); restorePlayingQueueIfAny (~3874, path tồn tại + owner, chưa đủ completion); discoverOffline.publish (~4168, scan đã verifiedPath); xử lý message (~6750, completed); prefetch resolve (~7252, completed); observePrefetch (~7280, completed); resolve phát (~7385, completed); playIfDownloaded (~7460, completed). Đây là điểm vào để kiểm theo tên hàm, không sửa mọi nơi một cách máy móc. Chưa xác minh được đường ghi thì không tự tin gán Ready.
2. State UI: UNKNOWN, REMOTE, DOWNLOADING(percent?), READY, UNAVAILABLE. Playback là trục riêng: có thể PLAYING + DOWNLOADING hoặc PAUSED + READY.
3. READY chỉ từ luồng complete/scan được xác minh hiện có + owner đúng + file còn readable/non-empty; kiểm size kỳ vọng khi metadata thật có sẵn. Percent=100 hoặc tồn tại path riêng lẻ không đủ.
4. Bỏ shortcut `if (filterOnlyDownloaded) return true`. Nhóm offline khi render lấy representative đã xác minh; nếu bản đại diện mất mà có bản hợp lệ khác, dùng helper chọn bản sẵn có và thể hiện identity mới nhất quán. Không xóa nhóm/bản từ DB.
5. Không gọi File.isFile/canRead/length hoặc TDLib cho từng bind trên UI. Dùng snapshot kết quả kiểm nền có owner/generation, kiểm lại khi trở về foreground, sau scan/completion và trước phát theo đường hiện có; gộp trùng yêu cầu.
6. File không còn đọc được → UNAVAILABLE, “Tệp trên máy không còn dùng được”; giữ thư viện/Playlist, hành động tải lại đi qua pipeline cũ sau người dùng bấm. Không xóa cache/TDLib hay auto download cả thư viện.
7. UNKNOWN trong khi kiểm → “Đang kiểm tra…” hoặc không hiện dấu đã tải; không đổi số đếm toàn thư viện theo số rows đang nhìn. Tái dùng downloadedCount generation.
8. STREAMING/playing chưa complete vẫn “Đang phát · Đang tải …” trong vùng phụ; không che trạng thái playback bằng dấu downloaded. LOCAL dùng “Trên máy”.
9. Nếu baseline không cung cấp đủ completion facts mà phải sửa service/format cache/DB schema: **dừng S5b**, ghi hàm và thiếu gì để bổ sung kế hoạch; không đoán theo kích thước path.

**Test:** file thuộc account khác; complete+readable; partial kể cả percent100; complete nhưng mất/unreadable/zero; expected size mismatch; callback owner cũ; nhóm 2 bản có 1 bản invalid; failure không xóa index; current playback không biến thành READY.

Fake filesystem/metadata là đủ cho lỗi unit; không xóa tệp nhạc thật để tạo bằng chứng. QA online tệp đã tải không chứng minh offline mất mạng.

**Xong khi:** cùng một bài có facts tải nhất quán giữa library/Downloaded/queue/now; không có IO kiểm toàn thư viện từ bind UI.

## S6 — Tiếp theo, dock và hồi quy P1

### File

T/MeloNowPlayingPolicy.kt: queueStatus và presentation.
T/MeloStudioDeck.kt: refresh/reloadQueue/queueRow/portrait/landscape.
T/MeloQueuePane.kt **mới**: phần UI hàng chờ tái dùng, không quản lý queue riêng.
T/MeloTabletScreen.kt: mở/đóng queue trong vùng phải qua host callback/UI action.
T/MeloScreenHost.kt và TelegramPreviewActivity.meloHost: route hiển thị queue phù hợp lớp hiện tại, fallback cũ cho Gọn/Car.
U/MeloNowPlayingPolicyTest.kt, U/MeloQueueUiPolicyTest.kt **mới**.
T/MeloComponents.kt/MeloDesignTokens.kt chỉ local opt-in nếu cần chiều cao/contrast Phone/Tablet.

### Bước nhỏ

1. Thêm paused/buffering input mặc định an toàn vào queueStatus; bài current paused phải “Tạm dừng”, buffering phải có nhãn đúng, không suy !isPlaying luôn là pause.
2. refresh queue khi currentKey/paused/buffering/queue content/download state đổi. Không rebuild mỗi giây khi chỉ position đổi. Giữ scroll, chỉ cuộn tới bài mới một lần khi currentKey đổi.
3. Tách renderer queue thành MeloQueuePane dùng host.queue/playQueueAt/openQueueSongMenu. Không tạo bản sao logic reorder/shuffle/prefetch.
4. Studio Deck mặc định nội dung “Tiếp theo”; nút mở full queue nếu portrait đang chỉ hiển thị cửa sổ 12 bài. Không gọi cửa sổ 12 bài là toàn bộ hàng chờ.
5. Tablet: mở Hàng đợi chuyển nội dung **khung phải** sang queue, khung phát trái giữ controls; nút “Thư viện”/Back trả đúng browse state S2. Không mở modal phủ hết để duyệt hàng chờ từ Hai khung.
6. Phone: Studio Deck tiếp tục dock ghim. Giữ close/back chỉ đóng UI; không pause/seek.
7. Không dựa metadata downloadNote.startsWith chữ để xác định refresh; dùng facts typed ở S5.
8. Font 1/1.3/1.5/2; tên dài, h:mm:ss, màn thấp: artwork thu nhỏ/vùng phụ cuộn, dock luôn thấy, title tối đa 2 dòng. Touch target ≥48×48 dp, action không chỉ phân biệt bằng màu.
9. Chữ phụ của UI mới đo contrast thực trên nền/gradient; target ≥4.5:1 với chữ thường nhỏ. Không sửa màu Car/Gọn để giải quyết màu tablet.
10. Chạy full unit Debug/Release + assemble Ni tại source cuối; tách passed/skipped/failed/errors; commit và bàn giao S7.

**Test:** current paused vs buffering vs playing; noncurrent downloading/ready; queue thay đổi cùng currentKey; pause không tự scroll; long duration 0/unknown/59s/3600s/2h+; E103 seek mép phải; dock layout; buttons không bị title/keyboard che.
Suite hồi quy: MeloCompactPolicy/CarPolicy/CarLayoutPolicy, MeloScreenPolicy, MeloBottomBarPolicy/ResumePolicy, MeloSavePolicy, Playlist sync/session hiện có, RowArtPolicy, TelegramOfflineLibrary/OfflinePlayPolicy, SearchPolicy.

**Điểm cần giữ trung thực:** MeloScreenHost nói queue authoritative nhưng implementation dựa queueSongOrder; kiểm mapping với playback thực tại baseline. Nếu phát hiện order shuffle đã sai và cần sửa service, ghi riêng/BLOCKED phần thứ tự; không sửa service trong chặng UI.

**Nguồn kích thước và contrast:** [Android accessibility](https://developer.android.com/guide/topics/ui/accessibility/apps). Áp nguyên lý cho Android Views; không đổi sang Compose.

## S7 — thực thi build/QA và bàn giao

Làm đúng mục 6 kế hoạch chính, không cần đọc lại toàn bộ S1–S6 khi source đã có commit và báo cáo.
Bàn giao tối thiểu: source/plan SHA, test XML + log path + exit code, APK metadata thật, gates, bundle SHA, clone Tiny, SHA/cert/config boolean, install/serial/model, QA1–6, màn cuối, 00_tong_hop.png, Drive owner-only đã xác minh, các giới hạn.
Nếu không có Tiny/Drive/thiết bị thì ghi BLOCKED đúng phần, vẫn giữ code/test đã hoàn thành. Không tự dùng APK cũ để thay source mới.
Kết thúc thực thi đưa prompt sơ bộ ở phiên mới. Sơ bộ kiểm file rủi ro: host state/async guard, Save adapter/writer reuse, feedback/retry deadline, offline facts; không mặc định build lại toàn bộ.

## P2-A — ảnh bìa độc lập kiểu màu (chưa nằm trong lệnh P1)

Khi chủ dự án giao riêng, thực hiện trên source P1 đã kiểm và ghi baseline mới vào bàn giao trước.

- T/MeloThemePolicy.kt: rowArtLayer cho Phone/Tablet NEW ở cả CURRENT/TEAL, giữ COMPACT true và CAR false; rowArtSwitch vẫn điều khiển request.
- T/TelegramPreviewActivity.kt: rowArtOn/snapshot/requestRowArt đang lấy key theo owner; giữ key và scheduler.
- T/MeloLibraryPane.kt: thay các gate c.teal cho listener/request/peek/apply bằng row-art-enabled; giữ gate c.teal chỉ cho màu/tab.
- T/MeloComponents.kt: non-teal Phone/Tablet dùng rowArtView khi có artKey, giữ placeholder và size profile; không thêm loader.
- Không sửa trần concurrency, cache format, thumbnail TDLib, decode trên UI hoặc hiệu ứng E101 đã ổn.
- Test MeloThemePolicy/MeloRowArtPolicy + ảnh 2 màu, bật/tắt tải ảnh, cuộn nhanh, rotate, stale image không sang bài khác.
- Switch OFF không tạo request mới; có thể giữ ảnh đã sẵn trong RAM theo hành vi cũ. Không xóa ảnh/cache khi tắt.
- Candidate/test/QA riêng; không gọi P2-A đã hoàn thành chỉ vì P1 xong.

## P2-B — lời nhạc: hợp đồng rõ, cổng quyết định trước code mạng

### Những điểm chưa chốt, không để model tự đoán

1. Dịch vụ nào cho Shareable: gọi công khai trực tiếp hay một backend được chủ dự án duyệt? Không lấy mặc định CarApiClient của bản Personal.
2. Dữ liệu được gửi, nguồn/độ tin cậy title/artist/album/duration, chính sách với tên file không đáng tin và mixset dài.
3. Quy tắc khớp phiên bản/độ lệch thời lượng, khi nào cần người dùng chọn; không tự đặt ngưỡng rồi coi là duyệt.
4. Cache riêng lời nhạc: vị trí, giới hạn, TTL và phân tách người dùng; không dùng/xóa TDLib cache. Cần quyết định trước vì đây là phần cache mới.
5. Điều kiện dùng dịch vụ/ghi nguồn, timeout/rate limit và xử lý mạng. Phiên này mở LRCLIB docs bị HTTP403, chưa xác minh hợp đồng API hiện hành.

### Hợp đồng UI đề xuất để không phải thiết kế lại

- Tab Tiếp theo mặc định; tab Lời nhạc chỉ xuất hiện khi feature đã hoàn chỉnh. Giữ tab trong phiên và xoay bằng S2.
- State: Loading, Synced, Plain, NoMatch, TemporaryError, Cached; Cached phải chỉ rõ synced/plain và bài đang thuộc về.
- Instrumental chỉ từ dữ liệu xác nhận, không suy từ NoMatch. Mismatch/ambiguous → không tự gán bản lời khác.
- Synced: dùng mốc player hiện hành; người dùng cuộn thì ngừng auto-follow, nút “Về dòng đang hát” bật lại. P2 đầu không thêm chạm câu lời để seek khi chưa có yêu cầu.
- Plain: đọc/cuộn, không tô dòng giả theo thời gian.
- Khi đổi bài/owner: tăng generation, hủy request/auto-scroll cũ; response kiểm token+track signature+owner trước publish.
- Seek/paused: dòng hiện tại tính lại từ position thật; lyric fetch lỗi không đổi player/queue.
- Request ra ngoài chỉ metadata âm nhạc đã chốt; không gửi Telegram account/chat/message IDs, songKey encode định danh, đường dẫn local hoặc session.
- Cache match metadata/record version; không mượn cached lời bài A cho bài B chỉ vì title giống.

### Bước thực thi sau khi quyết định được ghi vào bổ sung plan

L1 viết DTO/provider interface và fake tests → L2 adapter dịch vụ đã duyệt + timeout/cancel → L3 cache policy đã duyệt → L4 UI pane tích hợp StudioDeck/Tablet → L5 unit+QA+APK.
File mới dự kiến ở T: MeloLyricsPolicy, ShareableLyricsProvider, ShareableLyricsController, MeloLyricsPane. Đọc/reuse parser/models thuần ở app/src/main/.../lyrics khi không kéo Personal/backend vào Shareable; quyết định dependency bằng sourceSets thực trước khi chọn tái dùng.
Test: plain/synced/no-match/error/cached, offset thời gian, A→B→A out-of-order, seek/paused, bản remix sai duration, Unicode, lỗi mạng không dừng player, request không chứa identifiers, stale cache không tự coi đúng.

**Gate:** P2-B chưa đủ điều kiện code. Model nhận P1 phải bỏ qua phần này; chủ dự án chốt 5 điểm trên rồi cập nhật kế hoạch kỹ thuật dịch vụ, không triển khai thử bằng credentials/backend có sẵn.

## Nghiên cứu sau, chưa giao code

Nhiều Playlist có tên: chốt create/rename/delete, quyền sở hữu, sync/conflict, định danh/migration và bảo vệ dữ liệu trước; không suy hộp thoại trong video là bằng chứng tạo thành công.
Ghim nguồn: chốt lưu cục bộ/đồng bộ và thao tác pin/unpin sau; không nhét vào scope S3.
