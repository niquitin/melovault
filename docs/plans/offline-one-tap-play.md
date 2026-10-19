# Kế hoạch — offline-one-tap-play (Đã tải: chạm một lần là phát)

**Vai trò:** Opus lập kế hoạch, 19/10/2026. **Nhánh:** `dot/offline-one-tap-play`. **Baseline code:** `c99fbfba85c0d5b63cb0a56157dd8e3462433b1b` (nhánh `feature/e93-03d-y700-offline-cockpit`, cây sạch). Commit kế hoạch này là HEAD mà Sonnet phải thấy khi bắt đầu.

## 1. Mục tiêu
Chủ dự án thử Shanling: trong "Đã tải", chạm một bài thuộc nhóm nhiều bản thì mở bảng chọn bản, không phát (`docs/plans/da-tai-khi-mo-app-nghiem-thu.md`, mục "Phản hồi QA chủ dự án"). Cần:
- **Chạm một lần:** phát ngay bản offline phù hợp, mọi giao diện (Gọn, Điện thoại/Tablet/Hai khung, Car, Portrait/Cockpit cũ).
- **Nút ⋮:** menu bài quen thuộc + mục **"☰ Chọn bản khác (N bản)"**; chọn một bản là phát bản đó *(chủ dự án chốt 19/10/2026)*.
- **Hàng đợi khi phát từ Đã tải: mỗi nhóm một bài** theo thứ tự danh sách; không nghe lặp cùng một bài từ nguồn khác *(chủ dự án chốt 19/10/2026)*.
- Không xoá/gộp dữ liệu, không đổi quét offline, DB/schema, TDLib, Media3, service, versionCode.

## 2. Hiện trạng (đã đọc mã tại c99fbfb)
- `TelegramOfflineLibrary.groups` (không đổi): cùng `path` ⇒ một nhóm (`sameFile`, nhãn "Có tại N nguồn/bản"); khác path cùng tên ⇒ "Các bản cùng tên · N bản". Thứ tự bản trong nhóm = thứ tự quét = SQL `date DESC, message_id DESC, chat_id DESC` (cố định, xác định được).
- Danh sách Đã tải: `shownSongs[i] = offlineRepresentative(offlineGroups[i])` = bản đang phát nếu có trong nhóm, không thì bản đầu (`TelegramPreviewActivity.kt:3480`).
- Đường chạm có nhóm >1 bản ⇒ `showOfflineVersions(group)`: `playShownSong` (`:4229`, dùng bởi Gọn/MeloLibraryPane/Car qua `meloHost.playAt`) và `tracks.onItemClickListener` cũ (`:2695`).
- Đường ⋮ có nhóm >1 bản ⇒ `showOfflineVersions(group, anchor)` rồi `showSongOptionsMenu(song, true, tracks)` (neo vào ListView cũ): `meloHost.openSongMenu` (`:4382`) và nút ⋮ cũ (`:2593`). Phụ đề dòng cũ ghi "· Chạm để mở" (`:2550`).
- `downloadAndPlay(song)` (`:6029`): `getMessage` → nếu TDLib báo đã tải xong và tệp thuộc vùng riêng ⇒ `enqueueTelegramPlayback` phát `file://` (không tải lại); không thì stream tiến dần (cần mạng). TDLib bật `use_message_database`; các bản "đã xác minh" vốn được tìm bằng `getMessageLocally` ⇒ tin có sẵn ở DB TDLib nên `getMessage` khi không mạng được kỳ vọng trả ngay **[CHƯA KIỂM TRÊN MÁY]**.
- Hàng đợi: `enqueueTelegramPlayback` (`:616`) ở Đã tải dùng `offlineGroups.flatMap { it.entries }` ⇒ nhóm 3 nguồn cùng file thành 3 mục liền nhau, Media3 (`TelegramContiguousQueuePolicy`) đưa cả 3 vào ⇒ hết bài lại nghe y bài đó. `enqueueProgressivePlayback` (`:670`) lại dùng `shownSongs` (không nhất quán).

## 3. Phương án chọn và lý do
Thêm một policy thuần (test JVM được) chọn bản mặc định + dựng hàng đợi một-bản-mỗi-nhóm; trong Activity chỉ đổi điểm rẽ nhánh chạm/⋮ và nguồn danh sách hàng đợi. Phát vẫn qua `downloadAndPlay` hiện có (đường đã được kiểm nhiều bản Beta, có kiểm vùng riêng, phản hồi lỗi, cập nhật fileId), **không** thêm đường phát tệp trực tiếp bỏ qua TDLib. Không chạm `TelegramOfflineLibrary.kt` (thuật toán quét/nhóm giữ nguyên).

Loại bỏ: (a) gộp/khử trùng nhóm — trái nguyên tắc không gộp dữ liệu; (b) chọn theo chất lượng (FLAC/bitrate) — bản cùng tên có thể là bản thu khác, phát khác cái dòng đang hiện; (c) phát tệp trực tiếp không `getMessage` — đổi đường phát, rủi ro lệch trạng thái TDLib; chỉ xem lại nếu bước QA máy bay thất bại (điểm dừng).

## 4. Chính sách chọn bản mặc định (xác định, không I/O trong policy)
`defaultEntry(group, accountId, playingKey, readable)`:
1. Chỉ xét bản của tài khoản hiện tại và `!isDeleted`. Không còn bản nào ⇒ `null` (không làm gì).
2. Thứ tự ứng viên: **bản đang phát** (songKey == bài hiện tại) đứng đầu, sau đó theo thứ tự trong nhóm (tin mới nhất trước).
3. Chọn **ứng viên đầu tiên `readable`** (Activity kiểm: `isFile && canRead && length > 0 && isOwnedFile(filesRoot)`; chỉ vài lần stat cho đúng một nhóm).
4. Không bản nào đọc được ⇒ trả ứng viên đầu (đang phát hoặc bản đầu) để `downloadAndPlay` tự quyết như bài một-bản hôm nay (TDLib báo chưa tải ⇒ stream). Không xoá, không quét lại.

Tình huống: (1) một bản ⇒ đường cũ không đổi; (2) nhiều nguồn cùng file ⇒ bản đang phát hoặc tin mới nhất, các songKey khác giữ nguyên, chọn được qua ⋮; (3) khác file cùng tên ⇒ phát đúng bản mặc định, bản khác qua ⋮; (4) bản đang phát thuộc nhóm ⇒ giữ đúng bản đó, không đổi nguồn (chạm lại = phát lại từ đầu, giống chạm bài đang phát ở mọi danh sách hiện nay — hành vi cũ, ngoài phạm vi); (5) tệp mất ⇒ bỏ qua sang bản khác còn đọc được; hết bản ⇒ như bài chưa tải.

`queueEntries(groups, selectedKey)`: mỗi nhóm đúng một bản theo thứ tự danh sách — nhóm chứa `selectedKey` dùng chính bản đó, nhóm khác dùng bản đầu. Không I/O; bản đầu của nhóm láng giềng nếu mất tệp thì `TelegramContiguousQueuePolicy` đã dừng hàng Media3 tại đó như cũ.

## 5. File và sửa đổi chính xác
**Production (2 file, dự kiến ~80–100 dòng):**
1. **MỚI** `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/TelegramOfflinePlayPolicy.kt` — `internal object TelegramOfflinePlayPolicy { fun defaultEntry(...); fun queueEntries(...) }` theo mục 4, kiểu `TelegramOfflineLibrary.Group/Entry`, trả về đúng đối tượng Entry gốc (không copy/sửa song).
2. `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/TelegramPreviewActivity.kt`:
   - **A. Helper mới** (đặt cạnh `offlineRepresentative`, `:3480`):
     - `isReadableOfflineCopy(entry)` — `runCatching { File(entry.path) … isOwnedFile(filesRoot, it) }.getOrDefault(false)`.
     - `defaultOfflineEntry(group)` — `account = library.authorizedUser()?.value ?: return null`; gọi policy với `currentTelegramMessageId()`.
     - `playOfflineEntry(group, entry)` — kiểm `auth.isReady()` và tài khoản như `showOfflineVersions` đang làm; `song = offlineSong(entry.song)`; nếu `index = offlineGroups.indexOfFirst { it === group }` nằm trong `shownSongs` và khác songKey ⇒ thay `shownSongs[index] = song`, `adapter.notifyDataSetChanged()` (để dòng Melo hiện "Đang phát" đúng bản); rồi `downloadAndPlay(song)`.
     - `playOfflineGroup(group)` — `defaultOfflineEntry(group)?.let { playOfflineEntry(group, it) }`.
     - `openOfflineGroupMenu(group, anchor)` — `entry = defaultOfflineEntry(group) ?: return`; `showSongOptionsMenu(offlineSong(entry.song), true, anchor, versions = group)`.
     - `offlineQueueSongs(selectedKey)` — `TelegramOfflinePlayPolicy.queueEntries(offlineGroups, selectedKey).map { offlineSong(it.song) }`.
   - **B. `showOfflineVersions`** (`:3484`): bỏ tham số `anchor`; chọn một dòng ⇒ giữ các kiểm tra tài khoản hiện có rồi `playOfflineEntry(group, entry)`. Giữ tiêu đề `group.label`, nhãn `versionLabel(..., playing=…)` và nút "Đóng".
   - **C. `playShownSong`** (`:4229`): nhóm >1 bản ⇒ `playOfflineGroup(group)`; nhánh còn lại giữ nguyên.
   - **D. `tracks.onItemClickListener`** (`:2694–2698`): nhánh else gọi `playShownSong(position)` (bỏ trùng lặp).
   - **E. Nút ⋮ cũ** (`:2592–2595`) và **F. `meloHost.openSongMenu`** (`:4382–4386`): nhóm >1 bản ⇒ `openOfflineGroupMenu(group, anchor)`; còn lại giữ nguyên.
   - **G. `showSongOptionsMenu`** (`:4693`): thêm tham số cuối `versions: TelegramOfflineLibrary.Group? = null`. Khi `versions` có >1 bản: thêm ngay sau mục 1 `popup.menu.add(0, 6, 1, "☰ Chọn bản khác (${versions.entries.size} bản)")`; mục 1 với nhóm ⇒ `playOfflineEntry(versions, entry khớp song.messageId)` (không khớp thì `downloadAndPlay(song)`); mục 6 ⇒ `showOfflineVersions(versions)`. Các mục khác và mọi lời gọi cũ không đổi.
   - **H. Phụ đề dòng cũ** (`:2550`): `" · Chạm để mở"` → `" · ⋮ để chọn bản"`.
   - **I. Hàng đợi** `:616` và `:670`: `if (filterOnlyDownloaded) offlineQueueSongs(song.messageId) else shownSongs`. Giữ nguyên `TelegramQueueRetentionPolicy.forSelected`, `configureTelegramQueue`, `TelegramContiguousQueuePolicy`.
   - Sau sửa, `grep showOfflineVersions` chỉ còn định nghĩa + lời gọi từ mục 6.

**Test (mới):** `app/src/test/java/com/bsdung/musicassistantcar/data/telegram/TelegramOfflinePlayPolicyTest.kt` (helper `song()` giống `TelegramOfflineLibraryTest`).

**Không được đổi:** `TelegramOfflineLibrary.kt`, DAO/SQL/schema, `ShareablePlaybackService.kt`, các lệnh TDLib, Media3/DataSource, `MeloScreenHost`/`Melo*Screen`/`MeloLibraryPane`, `app/build.gradle` (versionCode), CLAUDE.md, docs/workflow.

## 6. Các bước nhỏ cho Sonnet
1. Kiểm đầu phiên theo CLAUDE.md; `git branch --show-current` = `dot/offline-one-tap-play`, HEAD = commit kế hoạch, cha = `c99fbfb`. Sai ⇒ DỪNG.
2. Viết `TelegramOfflinePlayPolicy.kt` + test (mục 7); chạy riêng `--tests "*TelegramOfflinePlayPolicyTest"` cho xanh.
3. Sửa Activity bước A → I theo thứ tự; build Debug sau bước G và sau bước I.
4. Chạy đủ lệnh test/build/`git diff --check` của CLAUDE.md; ghi số tests/passed/skipped từng biến thể.
5. Commit code (một commit `feat(shareable): one-tap play in Downloaded with version menu`).
6. QA Shanling (mục 8). Ghi `docs/plans/offline-one-tap-play-bao-cao.md`, commit báo cáo, DỪNG.

## 7. Regression test (JVM)
`TelegramOfflinePlayPolicyTest` tối thiểu:
1. Nhóm một bản ⇒ trả đúng bản đó.
2. Nhiều nguồn cùng path, không bản nào đang phát ⇒ bản đầu; gọi lại 2 lần cùng kết quả; nhóm vẫn đủ N bản (không gộp).
3. Cùng tên khác path: bản đầu không đọc được ⇒ chọn bản thứ hai.
4. Bản đang phát ở vị trí 3 ⇒ chọn bản đó; nếu nó không đọc được ⇒ bản đọc được kế tiếp theo thứ tự.
5. Không bản nào đọc được ⇒ trả bản đang phát (nếu thuộc nhóm) hoặc bản đầu.
6. Bản của tài khoản khác/`isDeleted` bị loại; nhóm chỉ có bản tài khoản khác ⇒ `null`.
7. `queueEntries`: số phần tử = số nhóm, thứ tự giữ nguyên, nhóm chứa `selectedKey` dùng bản đó, nhóm khác dùng bản đầu; không có hai mục cùng path từ cùng một nhóm.
8. `queueEntries` với `selectedKey` không thuộc nhóm nào ⇒ toàn bản đầu; Entry trả về `===` Entry gốc (songKey/chatId/messageId không đổi).
Toàn bộ test cũ phải xanh (mốc gần nhất: mỗi biến thể 1.255 defined, 1.234 passed, 21 skipped, 0 failed). Kỳ vọng: defined +≥8, skipped vẫn 21, 0 failed.

## 8. QA thiết bị (Sonnet, Shanling M3 Ultra trên Ni)
- `adb devices -l` thấy `30313860180939` trạng thái device; `getprop ro.product.model/manufacturer` = Shanling M3 Ultra/Shanling. Không thấy/unauthorized ⇒ BLOCKED phần máy.
- APK Ni thiếu Telegram config ⇒ **không cài**. Lấy APK Debug có config theo chuỗi gate: Git bundle (SHA256) → clone cô lập Tiny `C:\Bots\MeloVault-E93D-IsolatedQA`, nhánh `qa/offline-one-tap-play` → `assembleShareableDebug` → kiểm package `app.melovault.player`, vc109/0.6.88-beta (không đổi version), cert SHA-256 `eecd3df7…ce8`, config chỉ kiểm có/không (không in/chép secret) → chép APK về thư mục tạm ngoài repo, ghi SHA256. Tiny không truy cập được ⇒ BLOCKED phần máy, vẫn nộp test/build.
- Trước cài: `dumpsys media_session` — nếu MeloVault đang phát (state=3) ⇒ DỪNG, nhờ chủ dự án tạm dừng. Chỉ `adb -s 30313860180939 install -r "<APK>"`.
- Kịch bản (Gọn): ☰ → ✓ Đã tải, đợi hết "Đang kiểm tra…"; chạm một nhóm nhiều bản ⇒ phát, không hộp chọn; ⋮ ⇒ menu có "☰ Chọn bản khác (N bản)"; mở danh sách bản, chọn bản khác ⇒ phát bản đó, dòng hiện "Đang phát"; ⏭ 2 lần ⇒ bài khác nhau theo thứ tự dòng; chạm bài thường ở Thư viện ⇒ như cũ. Ảnh `adb exec-out screencap -p` vào `docs/plans/artifacts/offline-one-tap-play/` (không commit). Logcat: không FATAL/ANR của `app.melovault.player`; vị trí Media3 tăng.
- **Không** bật máy bay/tắt Wi-Fi qua adb, **không** xoá tệp để giả lập tệp mất (tình huống 5 chỉ test JVM). Bước máy bay để chủ dự án tự làm (mục 11).
- Sony/Tiny không bắt buộc.

## 9. Tiêu chí PASS / FAIL
**Đạt kỹ thuật** (ChatGPT kiểm): chạm nhóm nhiều bản ở mọi đường (Gọn, MeloLibraryPane, Car, ListView cũ) không còn mở hộp chọn; ⋮ có "Chọn bản khác" và chọn là phát; hàng đợi Đã tải một songKey mỗi nhóm đúng thứ tự; diff chỉ 2 file production + 1 test (+ báo cáo); test/build xanh như mục 7; ảnh Shanling bước 1–3; không FATAL/ANR.
**FAIL/REWORK:** còn đường chạm mở hộp chọn; phát bản khác với bản dòng đang hiện; ⏭ phát lại cùng bài; mất mục ⋮ hoặc không chọn được bản khác; chạm bài thường/Thư viện đổi hành vi; đổi quét/nhóm, DB, service, TDLib, versionCode; test đỏ hoặc skipped khác 21 không giải thích.
**PASS cuối** chỉ khi chủ dự án xác nhận mục 11 (theo CLAUDE.md, thiếu xác nhận ⇒ BLOCKED — CHỜ CHỦ DỰ ÁN).

## 10. Rủi ro và rollback
- `getMessage` khi không mạng có thể chậm/timeout ở máy thật (lập luận dựa `use_message_database` + `getMessageLocally`, chưa đo) ⇒ bước máy bay của bác sĩ phát hiện; nếu lỗi: điểm dừng, không tự thêm đường phát trực tiếp.
- Thay `shownSongs[index]` sau khi chọn bản: chỉ đổi phần tử cùng vị trí, không đổi số dòng; rủi ro thấp; nếu thấy dòng nhảy/lệch ⇒ ghi báo cáo.
- Hàng đợi không còn tự phát các bản trùng tên khác file — đúng chủ dự án chốt; vẫn chọn được qua ⋮.
- Mở menu/danh sách bản khi danh sách đang được quét cập nhật: `offlineGroups` có thể đổi; `indexOfFirst { it === group }` = -1 thì chỉ bỏ bước đồng bộ dòng, vẫn phát.
- **Rollback:** không có di trú dữ liệu. Chưa merge thì bỏ nhánh `dot/offline-one-tap-play`; đã merge thì `git revert` commit code (không reset/force). Máy: cài lại APK Debug vc109 trước đó (SHA256 `7C4F6155…0FDE`, cùng chữ ký) bằng `install -r`, không gỡ, không xoá dữ liệu.

## 11. Bác sĩ tự kiểm tra (Shanling M3 Ultra)
1. Mở MeloVault → nút ☰ → **✓ Đã tải**. Đợi dòng "Đang kiểm tra…" biến mất. **Chạm một lần** vào một bài (nên chọn bài trước đây từng mở bảng chọn bản). Phải thấy: **không** có bảng chọn; trong vài giây có tiếng nhạc, dòng bài hiện "Đang phát", thanh đáy hiện tên bài.
2. Ở một bài có nhiều bản, chạm **⋮** cuối dòng. Phải thấy menu có "▶ Phát bài này", **"☰ Chọn bản khác (… bản)"**, "Phát tiếp theo", "Playlist", "Thông tin". Chạm "Chọn bản khác" → thấy danh sách các bản kèm tên kênh, bản đang phát có dấu ▶. Chạm một bản **khác** → nhạc chuyển sang bản đó. (Nút "Đóng" thì không đổi nhạc.)
3. Ngay sau bước 2, **chạm vào chính dòng đó**: nhạc phát lại từ đầu bằng **cùng bản vừa chọn** (mở lại ⋮ → Chọn bản khác thấy dấu ▶ vẫn ở bản đó), không nhảy sang kênh khác.
4. Bấm **Bài kế ⏭** (thanh đáy hoặc phím vật lý) 2–3 lần: mỗi lần là một bài **khác**, đúng thứ tự dòng trong Đã tải, không nghe lại cùng một bài.
5. Kéo thanh thông báo, **bật Chế độ máy bay**. Về MeloVault (vẫn Đã tải), chạm một bài khác: vẫn **phát ngay**, không có thông báo "Telegram chưa gửi dữ liệu…" sau 15 giây. Thử xong **tắt Chế độ máy bay**.
6. Bấm tab **Thư viện** (Tất cả), chạm một bài bất kỳ: phát/đệm như trước. Chạm ⋮ của bài đó: menu như cũ, **không** có "Chọn bản khác".

## 12. Điểm Sonnet phải dừng để hỏi
- Nhánh/HEAD/cây làm việc khác mục 6.1, hoặc có agent/build khác đang chạy.
- Cần sửa `TelegramOfflineLibrary.kt`, DAO/schema, `ShareablePlaybackService.kt`, `Melo*Screen`/`MeloScreenHost`, lệnh TDLib, Media3, hoặc `build.gradle`.
- Diff production vượt ~130 dòng hoặc ngoài 2 file mục 5; hàm/dòng tham chiếu không còn như mô tả.
- Shanling unauthorized/không thấy; MeloVault đang phát nhạc; chữ ký lệch; downgrade; APK thiếu Telegram config; Tiny không truy cập được.
- Trên máy, chạm Đã tải khi không mạng báo lỗi/timeout `getMessage`: KHÔNG tự thêm đường phát trực tiếp — ghi bằng chứng, chờ ChatGPT.
- Không bump versionCode, không upload Drive, không cài Sony/FYT ngoài yêu cầu — ChatGPT quyết sau kiểm toán.

## 13. Ngoài phạm vi
Chạm lại bài đang phát = phát lại từ đầu (hành vi chung có sẵn); dấu hiệu "nhiều bản" trên dòng Melo (Gọn/Điện thoại chưa có; chỉ ListView cũ có phụ đề); chọn bản theo chất lượng; cập nhật số "đã tải" theo sự kiện.
