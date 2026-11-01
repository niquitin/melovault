# Nhiều Playlist có tên — kế hoạch kỹ thuật

TASK_ID: `2026-11-01-nhieu-playlist-co-ten`.
Lập kế hoạch: Claude Code, Opus 5.5, phiên 01/11/2026; effort không xác định từ công cụ.
Trạng thái: **KẾ HOẠCH CHỜ CHỦ DỰ ÁN GIAO THỰC THI — CHƯA CODE, CHƯA BUILD, CHƯA NGHIỆM THU.**

## 1. Yêu cầu và quyết định của chủ dự án

Nguồn: tài liệu định hướng `docs/plans/2026-10-29-hoc-hoi-ui-ux-telegram-music.md` trong repo chính. Nó chưa commit; đây là bản sao chủ dự án đã đọc, nằm trên nhánh `dot/2026-10-29-hoc-hoi-ui-ux-telegram-music`. Các mục 3 “Lưu và Playlist” và 4 “Nghiên cứu sau” ghi nhiều Playlist có tên là hạng mục chưa chọn mô hình dữ liệu và cách đồng bộ. Các phần P1, P2-A, P2-B của tài liệu đó đã ĐẠT và đã merge (xem HANDOFF.md).

Chủ dự án chốt trong chat ngày 01/11/2026:

| # | Câu hỏi | Quyết định |
|---|---|---|
| Q1 | Phạm vi | Lập kế hoạch kỹ thuật cho **Nhiều Playlist có tên** |
| Q2 | Đồng bộ | **Đồng bộ ngay qua kênh Telegram hiện có** (không để đợt sau) |
| Q3 | Playlist hiện có | **Giữ làm “Yêu thích” mặc định**: giữ dữ liệu, đồng bộ và nút ☆ một chạm; chỉ đổi nhãn hiển thị |
| Q4 | Thêm ngoài thao tác cơ bản | **Sắp xếp lại bài**, **Lưu hàng chờ thành Playlist**, **Cho bài Nhạc trên máy** |

Thao tác cơ bản đã có trong câu hỏi: tạo, đổi tên, xóa có xác nhận, thêm/bỏ bài, mở và phát Playlist.

**Giao kế hoạch này để thực thi = chủ dự án duyệt hai định dạng mới ở mục 4:** file lưu mã hóa mới `tg_named_playlists_v1/` và loại tin đồng bộ mới `#mvlist v1`. Đây là duyệt theo CLAUDE.md mục 4 (“không đổi schema … nếu chưa có phê duyệt”). Kế hoạch **không đổi** file Playlist V2 (`tg_playlists_v2/`), file V1, giao thức `#mvsync v1`, SQLite thư viện, songKey, package, signer hay versionCode 121.

## 2. Baseline, nhánh, worktree

| Mục | Giá trị |
|---|---|
| Baseline | `feature/e93-03d-y700-offline-cockpit` @ `57c1be64436c3c364bcd8c1a5f8a8193ea52a553` (đã gồm UI/UX P1, Đảo, D1–D3, ảnh bìa P2-A, đăng nhập số điện thoại, giao diện đa thiết bị, lời nhạc P2-B, mã E105) |
| Nhánh | `dot/2026-11-01-nhieu-playlist-co-ten` |
| Worktree | `D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten` |
| Commit kế hoạch | tra `git log -1 --format=%H -- docs/plans/2026-11-01-nhieu-playlist-co-ten.md` |
| Test baseline (HANDOFF 01/11) | mỗi biến thể 186 suite, 2445 test, 21 skipped, 0 failures/errors |
| Repo chính | `D:\Bots\MeloVault-E91-Shareable` đang ở nhánh tài liệu UI/UX của phiên khác, có file chưa commit. **Không làm việc ở đó.** |

Khảo sát của phiên lập kế hoạch: chỉ đọc source baseline. Không chạy Gradle, không ADB, không bật Sony.

## 3. Hiện trạng mã (đã đọc)

- **Playlist V2 = 1 danh sách/tài khoản** — `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/`:
  - `TelegramPlaylistModel.kt`: `PlaylistState` (OR-Set `PlaylistOrSet` của `PlaylistItemId.Telegram(chatId, messageId)`, `Dot(device, counter)`, Lamport, outbox ≤ 5000, `SyncCheckpoint(channelId, lastAppliedServerId, replayPosition)`, legacy keys).
  - `TelegramPlaylistCodec.kt`: văn bản `MVPL2`, dòng `END<TAB>sha256`.
  - `TelegramPlaylistStoreV2.kt`: file mã hóa AES-GCM Keystore, ghi nguyên tử, không bao giờ ghi đè file không đọc được, tối đa 8 MB.
  - `TelegramPlaylistV2Session.kt`: một bên ghi duy nhất, `PlaylistSessionApi`/`PlaylistSyncHost`, chạy trên luồng `TelegramPlaylistRuntime`.
  - V1 `TelegramSavedPlaylistStore.kt` (MAX_IDS 300) là bản chiếu cho APK cũ.
- **Đồng bộ (E96 P2–P5B3 + E105)**:
  - `TelegramPlaylistSyncProtocol.kt`: tin `#mvsync v1`, ≤ 20 op, ≤ 3500 ký tự, trường cách nhau một dấu cách, `sum` sha256. Decode: tin **không bắt đầu bằng `#mvsync`** là `Foreign`.
  - `TelegramPlaylistSyncCoordinator.kt` dòng 331–336: `Foreign` được bỏ qua và con trỏ vẫn tiến. ⇒ **APK cũ bỏ qua an toàn mọi tin có marker khác** — nền của phương án ở mục 4.
  - `TelegramPlaylistSyncTransport.kt`: `send(channelId, text)` / `fetchAfter(channelId, afterServerId, limit)`, chỉ làm việc ở mức văn bản.
  - `TransmitPlanner.kt`: `SyncSendBudget.PRODUCTION_MAX = 3`; không gửi khi có lần giao chưa rõ kết quả.
  - `AutomaticPlaylistSync.kt`: chỉ chạy khi foreground; gom 500 ms sau lưu, kiểm mỗi 15 s, chạy tiếp sau 1 s khi còn việc; kiểm kênh mỗi lượt.
  - `SyncServiceProvider.kt` → `SyncRuntime`; Cài đặt ở `MeloSyncSettingsActivity.kt`.
- **Trạng thái E105:** `E105-DONG-BO-PLAYLIST-nghiem-thu.md` ghi **BLOCKED** (pending/khôi phục chưa khép, open chưa QA). Mã tự đồng bộ có trong nhánh tích hợp qua merge UI/UX P1. Nhiệm vụ này **dựa trên** đường đồng bộ đó; không sửa lỗi E105.
- **UI**:
  - `TelegramPreviewActivity.kt` (8254 dòng): `requestPlaylistToggle` 5107, `loadSavedPlaylist` 5298, `closeSavedPlaylist` 5329, `resolvePlaylistEntries` 5354, `openSongMenu` 6000, `showSongOptionsMenu` ~6548, `showMeloQueue` 4177, `favoriteIds`, `playlistMode`; đăng xuất ~8160 chỉ đóng Playlist, **không xóa file Playlist**.
  - Nhãn “Playlist” ở `MeloLibraryPolicy.kt` 158/196–208/294–295, `MeloBrowseUiPolicy.kt` 50, `MeloBottomBarPolicy.kt` 16, `MeloCompactPolicy.kt` 52, `MeloCompactScreen.kt` 366/391/601, `MeloTabletScreen.kt` 113, `MeloCarPolicy.kt` 49, `MeloCarScreen.kt` 269/525, `MeloSavePolicy.kt` 27–118, `MeloSyncSettingsActivity.kt`.
  - Host chung `MeloScreenHost.kt` (`setMode`, `openSongMenu`, `toggleRowFavorite`, `playAll`, `openQueue`).
  - Nhạc trên máy: `MeloLocalLibrary.kt` / `MeloLocalSourcePolicy.LocalSong`; `PlaylistItemId.Local` hiện “never stored, never synced”.

## 4. Thiết kế đã chọn và lý do

### 4.1 Hai kho riêng, Yêu thích không đổi

- **Yêu thích** = Playlist V2 hiện có. Không đổi mã lưu/đồng bộ của nó; chỉ đổi chữ hiển thị.
- **Playlist có tên** = kho mới `NamedPlaylistState`, file riêng, outbox riêng, checkpoint riêng, deviceId riêng.

Phương án bị loại:
- (a) Thêm `listId` vào `PlaylistState` + `#mvsync v2`: APK cũ gặp `FUTURE_VERSION` thì `Invalid`, dừng đọc ⇒ máy cũ ngừng đồng bộ Yêu thích. Lại phải migrate V2 đang có dữ liệu thật.
- (b) Mỗi Playlist một kênh Telegram: tạo kênh đang bị khóa ở mọi bản (`SYNC_CREATE_ALLOWED`), và tốn nhiều kênh.

### 4.2 Mô hình dữ liệu (CRDT, không phụ thuộc đồng hồ)

`NamedPlaylistState(accountId, deviceId, lamport, opCounter, lists, items, localItems, outbox, checkpoint)`:

- **ListId**: chuỗi `<deviceId>.<counter>` (cùng regex/độ dài với `Dot.key`, dấu `#` thay bằng `.`), sinh lúc tạo; không bao giờ đổi.
- **Tồn tại của Playlist**: OR-Set theo ListId. `LIST_CREATE` mang một dot. `LIST_DELETE` xóa các dot đã quan sát. Đổi tên không hồi sinh Playlist đã xóa.
- **Tên**: thanh ghi LWW `(lamport, deviceId, opId)` lớn nhất thắng. `LIST_CREATE` và `LIST_RENAME` đều ghi. Tên 1–60 ký tự sau trim. Trùng tên trong cùng máy: UI chặn khi tạo/đổi (so sánh không dấu, không phân biệt hoa thường). Trùng do đồng bộ thì vẫn hiện cả hai.
- **Bài Telegram trong Playlist**: OR-Set theo khóa `(listId, PlaylistItemId.Telegram)`. `ITEM_ADD` một dot, `ITEM_REMOVE` các dot đã quan sát. Thêm bài đã có thì không tạo op (không trùng).
- **Thứ tự**: mỗi `(listId, item)` có thanh ghi vị trí LWW `pos` là **chỉ số phân số dạng chuỗi** (base-62, `FractionalIndex.between(a, b)`). `ITEM_ADD` mang `pos`; `ITEM_MOVE` ghi `pos` mới. Hiển thị theo `(pos, item.key)`. Chuỗi dài quá 48 ký tự thì `rebalance` cả Playlist bằng một loạt `ITEM_MOVE` (hiếm; có test).
- **Bài Nhạc trên máy**: `localItems[listId]` = danh sách `LocalEntry(localId, pos, fingerprint)`. `fingerprint` = sha256 rút gọn của (tên hiển thị, thời lượng, kích thước); **không** lưu đường dẫn/URI. Chỉ lưu trên máy này, **không bao giờ vào outbox/tin đồng bộ**. Khi mở: khớp `localId` + fingerprint với `MeloLocalLibrary`; không khớp thì hiện dòng “Bài trên máy không còn · chạm để bỏ”, không phát. Bài trên máy xen theo `pos` với bài Telegram.
- **Legacy key** (bài Telegram chưa xác minh chat/message): không cho thêm vào Playlist có tên; hiện gợi ý giống `MeloSavePolicy.HINT_LEGACY`.
- **Bài Playlist có tên mà thư viện máy này chưa biết**: dòng placeholder như Yêu thích (`placeholderSong`), không phát.
- **Giới hạn**: 100 Playlist còn sống mỗi tài khoản; 1000 mục (Telegram + máy) mỗi Playlist; outbox ≤ 5000 (ném `OutboxFullException`, không bỏ thay đổi); file ≤ 8 MB. Vượt giới hạn thì báo rõ, không cắt ngầm.
- **Xóa Playlist khi máy khác đang thêm bài vào**: Playlist vẫn bị xóa. Bài thêm đồng thời giữ trong dữ liệu nhưng ẩn. Ghi rõ trong test và báo cáo.

### 4.3 File lưu

- `noBackupFilesDir/tg_named_playlists_v1/<TelegramAccountStoragePolicy.directoryName>.bin`.
- Mã hóa bằng `KeystorePlaylistCipher` với **alias mới** (`NAMED_V1_ALIAS`) và AAD `"nl1:" + dir`. Ghi qua `AtomicPlaylistFileIo`.
- Theo quy tắc của `TelegramPlaylistStoreV2`:
  - file có mà không đọc được thì `Unreadable`, không ghi đè, không tạo khóa;
  - ghi xong phải đọc lại đúng mới thay file cũ.
- Codec `NamedPlaylistCodec`: văn bản `MVNL1`, các dòng có tab, kết thúc `END<TAB>sha256`; tất định và chặt chẽ như `TelegramPlaylistCodec`. Tên lưu dạng base64url UTF-8.
- **Đăng xuất**: giống Yêu thích — đóng session, giữ file, không xóa.

### 4.4 Tin đồng bộ `#mvlist v1` (cùng kênh)

```
#mvlist v1
scope <16 hex>        = PlaylistSyncProtocol.scopeOf(account) (dùng lại, không lộ số tài khoản)
sender <deviceId của kho Playlist có tên>
batch <batchId>
op <opId> LCREATE <listId> <seq> <dot> <nameB64>
op <opId> LRENAME <listId> <seq> <nameB64>
op <opId> LDELETE <listId> <seq> <dot,dot,...>
op <opId> IADD <listId> <tg:chat:msg> <seq> <dot> <pos>
op <opId> IREMOVE <listId> <tg:chat:msg> <seq> <dot,dot,...>
op <opId> IMOVE <listId> <tg:chat:msg> <seq> <pos>
sum <sha256 của mọi dòng phía trên>
```

- Cùng luật với `#mvsync`:
  - một dấu cách giữa các trường; văn bản phải là điểm cố định của `TdlibText.normalize` (test bắt buộc);
  - ≤ 20 op, ≤ 3500 ký tự, ≤ 32 dot;
  - decode chặt: sai thì `Invalid` và dừng đọc; không bắt đầu bằng `#mvlist` thì `Foreign`.
- **Tương thích**:
  - APK cũ và coordinator Yêu thích của APK mới thấy `#mvlist` là `Foreign`, bỏ qua — giữ nguyên hành vi;
  - coordinator mới thấy `#mvsync` là `Foreign`, bỏ qua.
- **Checkpoint riêng**: máy lên bản mới bắt đầu FULL_REPLAY từ 0 cho `#mvlist` (APK cũ đã cho con trỏ Yêu thích vượt qua các tin này).
- **Ngân sách gửi riêng**: tối đa **1 tin/lượt** (≤ 20 op) cho Playlist có tên, chạy **sau** lượt Yêu thích trong cùng lượt `AutomaticPlaylistSync`. Mỗi lượt vì vậy ≤ 3 op Yêu thích + 1 tin Playlist. Dùng chung `SyncBackoff`/FLOOD_WAIT: một bên bị FLOOD_WAIT thì lượt dừng cả hai.
- **Lưu hàng chờ 200 bài**: ~10 tin, mỗi lượt cách 1 s ⇒ khoảng 10–20 s khi mạng bình thường. Không gửi lại khi kết quả chưa rõ (`UnconfirmedBlocked`).

### 4.5 Đồng ý và Cài đặt

- **Một công tắc “Tự đồng bộ Playlist” hiện có** áp dụng cho cả Yêu thích và Playlist có tên. Không tạo đồng ý mới, không đổi `FileSyncConsentStore`.
- Hộp “Thay đổi chờ gửi” (`MeloSyncSettingsActivity`, `PendingPlaylistReview`) liệt kê thêm thay đổi Playlist có tên (loại thao tác, tên Playlist, bài) trước khi bật.
- **[CẦN XÁC NHẬN — mặc định đã chọn]** Nếu chủ dự án muốn công tắc riêng cho Playlist có tên, ghi REWORK. Mặc định này có lý do: hai kho đã dùng chung kênh và lịch kiểm.
- Máy **chưa bật** tự đồng bộ: Playlist có tên vẫn tạo/sửa bình thường. Thay đổi nằm trong outbox, chỉ gửi khi bật (giống Yêu thích).

### 4.6 Giao diện (Android Views, không Compose)

- **Nhãn**: mọi chữ “Playlist” chỉ danh sách cũ đổi thành **“Yêu thích”**. Ví dụ: tab/chip trở thành “Playlist” (chỉ mục) với mục đầu “★ Yêu thích”; nút ☆ “Lưu vào Yêu thích”; trạng thái “Đồng bộ Yêu thích & Playlist”. Car Mode chỉ đổi chữ, không thêm Playlist có tên. Bảng chữ chính xác ở phụ lục A.
- **Tab Playlist** (Điện thoại, Tablet dọc, Hai khung, Gọn):
  - Mức 1 — chỉ mục: “★ Yêu thích · N bài”, rồi các Playlist có tên theo thứ tự tạo (`pos` của danh sách = lamport của LCREATE, tie theo listId), rồi “+ Tạo Playlist”.
  - Mức 2 — chi tiết: danh sách bài. Thanh trên có ▶ Phát, ⤨ Phát ngẫu nhiên và ⋮ (Đổi tên, Sắp xếp, Xóa Playlist).
  - Back từ chi tiết về chỉ mục; giữ vị trí cuộn và lựa chọn khi xoay (dùng `MeloBrowseState` hiện có).
- **Thêm vào Playlist…**:
  - Menu ⋮ của dòng bài (`showSongOptionsMenu`, `openLocalRowMenu`) và menu bài đang phát có mục mới.
  - Hộp chọn hiện danh sách Playlist có dấu ✓ ở Playlist đã chứa bài; chạm để thêm/bỏ.
  - Mục “+ Playlist mới…” nhập tên rồi tạo và thêm luôn.
  - Chạm không phát bài. Chỉ báo “Đã thêm …” sau khi file ghi xong.
- **Sắp xếp** (chỉ Playlist có tên): chế độ Sắp xếp hiện ↑/↓ ở mỗi dòng (≥ 48 × 48 dp, DPAD/TalkBack dùng được) và nút Xong. Kéo thả bằng `ItemTouchHelper` **chỉ khi** danh sách là `RecyclerView`; nếu không phải thì chỉ ↑/↓, ghi vào báo cáo. Yêu thích **không** có sắp xếp.
- **Lưu hàng chờ thành Playlist**: nút trong màn Hàng đợi (`showMeloQueue`).
  - Nhập tên, rồi tạo Playlist với bài Telegram đã xác minh và bài trên máy theo đúng thứ tự hàng chờ.
  - Bỏ placeholder/legacy/không xác định; báo “Đã lưu N bài, bỏ qua M bài”.
  - Hàng chờ > 1000 thì từ chối, có thông báo.
- **Phát Playlist**: hàng chờ = các mục phát được theo thứ tự (bài Telegram qua đường `downloadAndPlay`/`playAll` hiện có, bài trên máy qua đường phát local hiện có). Bỏ placeholder. Không đổi `ShareablePlaybackService`/Media3.
- **Xóa Playlist**: hộp xác nhận “Xóa Playlist ‘<tên>’? Bài hát không bị xóa khỏi Telegram hay máy.”

### 4.7 Ngoài phạm vi

- Sắp xếp Yêu thích.
- Chia sẻ Playlist cho người khác; tạo kênh hay chuyển tiếp tin nhắn.
- Đồng bộ bài trên máy.
- Thư mục Playlist; ảnh bìa Playlist; Car Mode Playlist có tên.
- Sửa lỗi E105 BLOCKED.
- Đổi DB, schema SQLite, service, Media3, TDLib auth, package, signer, versionCode.

## 5. Các chặng thực thi

Mỗi chặng là **một phiên thực thi mới** do chủ dự án mở, model do chủ dự án chọn. Mỗi chặng kết thúc bằng commit, `*-bao-cao.md`, `*-ban-giao.md` và prompt chặng sau. Các file mới đặt tại `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/` trừ khi ghi khác; test JVM tại `app/src/test/java/com/bsdung/musicassistantcar/data/telegram/`.

### Chặng 1 — Mô hình, lưu trữ, session (Ni, chỉ JVM + nối runtime, không UI)

| Bước | Việc | Xong khi |
|---|---|---|
| S1 | `FractionalIndex.kt`: `between(a: String?, b: String?)`, `isValid`, `MAX_LEN = 48`; base-62 `0-9A-Za-z`. | Test: luôn `a < kq < b`; 10 000 lần chèn ngẫu nhiên giữ thứ tự; chèn đầu/cuối; không bao giờ trả chuỗi rỗng. |
| S2 | `NamedPlaylistModel.kt`: `ListId`, `NamedOpType` (6 loại), `NamedOp`, `NamedPlaylistState` immutable với `createList`, `renameList`, `deleteList`, `addTelegram`, `removeTelegram`, `moveItem`, `addLocal`, `removeLocal`, `moveLocal`, `rebalance`, `applyRemote(op)`, `acknowledge`, `markRetryRequired`, `revertRefusedDispatch`, `pruneAcknowledged`; view `lists()`, `entries(listId)`. Giới hạn ở 4.2. Không Android/IO/đồng hồ. | Test: hội tụ 2–3 bản sao với op hoán vị/lặp (giao hoán, kết hợp, lũy đẳng); thêm-bỏ-thêm lại; xóa đồng thời với thêm; đổi tên đồng thời (LWW tie theo device); move đồng thời; giới hạn 100/1000/outbox; local không bao giờ vào outbox. |
| S3 | `NamedPlaylistCodec.kt` (`MVNL1`, schema 1) + `NamedPlaylistStore.kt` (file/alias/AAD ở 4.3). Thêm `NAMED_V1_ALIAS` cạnh alias V2 hiện có, không đổi alias cũ. | Test: encode→decode bằng nhau; tất định; checksum sai/dòng lạ/tài khoản khác ⇒ Invalid; file không đọc được ⇒ không ghi đè; tên tiếng Việt/emoji vòng tròn; dùng fake cipher như `TelegramPlaylistStoreV2Test`. |
| S4 | `NamedPlaylistSession.kt`: một bên ghi trên luồng `TelegramPlaylistRuntime`; `open(account)`, `close()`, `snapshot()`, các mutation trả `NamedOutcome` (Ok/Full/OutboxFull/Blocked/NotOpen/Failed/DuplicateName/Refused), `epoch`, interface `NamedPlaylistSyncHost { view; update(account, epoch, transform) }` cùng hợp đồng `PlaylistSyncHost`. Cung cấp từ `TelegramPlaylistRuntime` (thêm getter, không đổi session cũ). | Test: đổi tài khoản A→B→A không lẫn; update stale bị từ chối; lỗi ghi giữ trạng thái cũ; mở file Unreadable ⇒ Blocked, mọi ghi bị từ chối. |
| S5 | Gate Ni (mục 6). Báo cáo + bàn giao + prompt chặng 2. | Toàn bộ test cũ pass, số test tăng đúng phần mới; `git diff --check` sạch. |

Chặng 1 **không** sửa `TelegramPlaylistModel/Codec/StoreV2/V2Session/SyncProtocol/SyncCoordinator`.

### Chặng 2 — Đồng bộ `#mvlist v1` (Ni, JVM + nối vào tự đồng bộ)

| Bước | Việc | Xong khi |
|---|---|---|
| S6 | `NamedPlaylistSyncProtocol.kt`: encode/decode 4.4; dùng lại `TdlibText`, `PlaylistSyncProtocol.scopeOf`, sha256. | Test: điểm cố định `TdlibText.normalize`; ≤ 3500 ký tự với 20 op tên dài nhất; mọi lỗi Error; `#mvsync` ⇒ Foreign; **test chéo**: `PlaylistSyncProtocol.decode(tin #mvlist)` ⇒ Foreign. |
| S7 | `NamedPlaylistSyncCoordinator.kt`: fetch (FULL_REPLAY/INCREMENTAL, chứng minh trang, dừng tại tin Invalid, ACK tin của mình, op của mình phải về đúng nguyên văn) và send (1 tin/lượt, `UnconfirmedBlocked`, ghi cờ trước khi gửi, hoàn tác khi chắc chắn bị từ chối). Viết theo đúng hợp đồng của `TelegramPlaylistSyncCoordinator`; **không** sửa class cũ. Được tách helper **thuần** dùng chung chỉ khi giữ nguyên byte hành vi cũ và toàn bộ test cũ pass không đổi assertion. | Test với `FakeTransport` dùng chung kênh: 2 máy tạo/đổi tên/thêm/xóa/move hội tụ; replay từ 0; trang lỗi/hasMore giả; FLOOD_WAIT; giao chưa rõ không gửi lại; tin `#mvsync` xen kẽ không ảnh hưởng; coordinator Yêu thích chạy trên cùng kênh có tin `#mvlist` vẫn ra kết quả như cũ. |
| S8 | Nối vào `AutomaticPlaylistSync.run`: sau `actions.run(...)` của Yêu thích (trong cùng scope/epoch/kênh đã kiểm), chạy coordinator Playlist có tên với kênh = `checkpoint.channelId` của Yêu thích đã khớp discovery. Thêm vào `SyncActionsPort`/`SyncServiceProvider` một hàm `runNamed(account, mode, maxEnvelopes = 1)` có `DisabledSyncActions` trả BUILD_OFF. Lịch tiếp 1 s khi còn việc ở **bất kỳ** bên nào. FLOOD/backoff dùng chung. Đổi tài khoản/rời app/mất đăng nhập hủy cả hai. | Test `AutomaticPlaylistSyncTest` mới: thứ tự Yêu thích → Playlist; Yêu thích FLOOD ⇒ không chạy Playlist; Playlist lỗi không làm hỏng báo cáo Yêu thích; tắt công tắc ⇒ cả hai dừng, dữ liệu giữ; build off ⇒ không gọi TDLib. Test cũ không sửa assertion. |
| S9 | Trạng thái + Cài đặt: `PlaylistSyncStatusPolicy` có thêm số thay đổi Playlist chờ gửi; `PendingPlaylistReview`/hộp “Thay đổi chờ gửi” liệt kê thêm op Playlist có tên (loại, tên, bài; không hiện id). | Test policy; chữ tiếng Việt đúng phụ lục A. |
| S10 | Gate Ni + báo cáo + bàn giao + prompt chặng 3. | Như S5. |

### Chặng 3 — Giao diện (Ni, Views)

| Bước | Việc | Xong khi |
|---|---|---|
| S11 | Đổi nhãn Yêu thích theo phụ lục A ở các file mục 3. Cập nhật test chuỗi hiện có **chỉ** ở chỗ nhãn (ghi danh sách trong báo cáo). | grep không còn nhãn cũ chỉ danh sách cũ; test pass. |
| S12 | `MeloNamedPlaylistPolicy.kt` (thuần): mô hình hàng chỉ mục/chi tiết, chữ đếm, kiểm tên (1–60, trùng không dấu), quyết định thêm được hay không (Telegram đã xác minh / local / legacy / placeholder), kế hoạch “Lưu hàng chờ”, kế hoạch hàng chờ khi phát. | Test thuần đủ nhánh. |
| S13 | Activity: mở/đóng `NamedPlaylistSession` cùng chỗ `loadSavedPlaylist`/`closeSavedPlaylist`; mọi ghi qua `TelegramPlaylistRuntime.submit`, callback kiểm tài khoản/epoch như `MeloSavePolicy.acceptsCallback`; refresh UI sau khi đồng bộ áp thay đổi (theo mẫu `PlaylistLiveRefresh`). | Test guard/callback tài khoản cũ bị bỏ. |
| S14 | Tab Playlist hai mức (chỉ mục/chi tiết) ở `MeloLibraryPane`/`MeloLibraryPolicy` cho Điện thoại, Tablet dọc, Hai khung; Gọn: menu “Playlist” mở chỉ mục dạng danh sách. `MeloScreenHost`: thêm `openNamedPlaylist(id?)`, `namedPlaylists()`, `playNamed(id, shuffle)`; mặc định rỗng/Unit để Car Mode không đổi. Giữ cuộn/lựa chọn khi xoay. | Test policy + thủ công trên máy ở chặng 4. |
| S15 | Hộp “Thêm vào Playlist…” (`dialog` mới bằng Views, theme `Theme_MeloVault_Dialog`), từ menu dòng bài, menu bài trên máy, menu bài đang phát; tạo mới trong hộp; Đổi tên/Xóa (xác nhận) trong ⋮ chi tiết. | Test policy; vùng bấm ≥ 48 dp. |
| S16 | Sắp xếp ↑/↓ (+ kéo thả nếu RecyclerView) chỉ cho Playlist có tên; mỗi lần di chuyển = 1 `ITEM_MOVE`/`moveLocal`, ghi xong mới vẽ lại. | Test policy vị trí mới. |
| S17 | “Lưu hàng chờ thành Playlist” trong `showMeloQueue`. | Test kế hoạch lưu (thứ tự, bỏ qua, > 1000 từ chối). |
| S18 | Phát Playlist có tên (▶/⤨): dựng hàng chờ qua đường phát hiện có; không đổi service. | Test kế hoạch hàng chờ. |
| S19 | Gate Ni + báo cáo + bàn giao + prompt chặng 4. | Như S5; thêm `assembleShareableDebug -PmeloOpen=true` và `assemblePersonalDebug` (Personal không dùng mã mới, phải vẫn build). |

### Chặng 4 — Build Tiny + QA Sony + bàn giao APK

| Bước | Việc |
|---|---|
| S20 | Bundle Git từ Ni, SHA-256 hai đầu; clone cô lập mới trên Tiny; config Telegram theo ngoại lệ E104 S11 (chỉ `Copy-Item` từ `C:\Bots\MeloVault-E99-IsolatedQA\local.properties`, không đọc/in/hash; kiểm bằng `reportTelegramConfig`). Build Debug + Release bản thường. |
| S21 | Gate APK: package `app.melovault.player`, vc121 / `0.6.96-beta`, cert SHA-256 khớp bản đang cài, config=true, SHA-256/bytes Tiny=Ni. Kiểm Media3 không đang phát rồi mới `adb -s QV725WEQ54 install -r`. Mọi cấm của CLAUDE.md mục 3 giữ nguyên. |
| S22 | QA Sony theo mục 7; ảnh `docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/`, `00_tong_hop.png`; màn tắt trong `finally`, ghi `SONY_SCREEN_END`. |
| S23 | Drive Ni N owner-only theo CLAUDE.md mục 8–9: `*-apk-test.md`, `*-hinh-nghiem-thu.md`; nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**. Drive chặn tải ⇒ BLOCKED đúng phần. |
| S24 | Báo cáo hoàn chỉnh, bàn giao, prompt `PROMPT_NGHIEM_THU_SO_BO.md` cho phiên sơ bộ mới. |

## 6. Gate Ni (mỗi chặng 1–3)

```
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain > <log> 2>&1
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain > <log> 2>&1
git diff --check
```

- Log ghi vào `docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/` (ignored). Chỉ đọc dòng tổng kết.
- Đếm tests/skipped/failures/errors từ XML `app/build/test-results/`. Baseline 2445/21/0/0 mỗi biến thể; số mới phải ≥ baseline + test thêm. Không thêm `@Ignore`, không sửa assertion cũ (trừ nhãn S11, có liệt kê).
- Chặng 3 thêm `-PmeloOpen=true :app:testShareableDebugUnitTest :app:assembleShareableDebug` và `:app:assemblePersonalDebug`.

## 7. QA Sony (chặng 4) — 6 thao tác cho chủ dự án/agent

Thiết bị: Sony XQ-AS52 `QV725WEQ54` trên Tiny (xác minh lại). Màn ON chỉ trong các bước dưới, OFF trong `finally`.

| # | Màn hình / thao tác | Kỳ vọng | Ảnh |
|---|---|---|---|
| Q1 | Mở app → tab Playlist | Chỉ mục: “★ Yêu thích · N bài” đúng số cũ, “+ Tạo Playlist”; nút ☆ hiện “Lưu vào Yêu thích”; Yêu thích mở ra đúng bài cũ | trước/sau |
| Q2 | “+ Tạo Playlist” → nhập `QA Thử 1` → ⋮ của một bài Telegram → Thêm vào Playlist… → chọn `QA Thử 1`; thêm 2 bài nữa và 1 bài Nhạc trên máy | Hộp có ✓ đúng; bài không bị phát; chi tiết `QA Thử 1` có 4 bài đúng thứ tự thêm; bài máy có dấu “trên máy” | 3 ảnh |
| Q3 | Chi tiết → ⋮ Sắp xếp → ↑ bài cuối lên đầu → Xong; đóng mở app | Thứ tự mới giữ sau mở lại | trước/sau |
| Q4 | Hàng đợi → Lưu hàng chờ thành Playlist → `QA Hàng chờ` | Số bài = bài Telegram + máy của hàng chờ; báo bỏ qua đúng | 2 ảnh |
| Q5 | ▶ Phát `QA Thử 1`; Next 1 lần | Hàng chờ = 4 bài theo thứ tự; Media3 PLAYING; **CHƯA XÁC NHẬN ÂM THANH THỰC** | ảnh + dumpsys media_session |
| Q6 | Đổi tên `QA Thử 1` → `QA Thử 2`; Xóa `QA Hàng chờ` (xác nhận); xoay ngang/dọc ở chi tiết | Tên mới; Playlist bị xóa biến mất, bài vẫn còn trong thư viện; xoay giữ màn chi tiết/cuộn | 3 ảnh |

**Đồng bộ hai máy — [CẦN XÁC NHẬN, mặc định BLOCKED]**: chỉ còn một máy QA (Shanling đã bán). Có hai cách, chủ dự án chọn:

- (a) Chủ dự án tự đăng nhập bản open `app.melovault.player.open` trên Sony cùng tài khoản (tự nhập API ID/hash + OTP; agent không nhập) và bật tự đồng bộ ở cả hai gói. Hai gói là hai “máy” riêng.
- (b) Chủ dự án dùng máy thứ hai của mình.

Nếu có: Q7 tạo Playlist ở gói A, gói B nhận trong ≤ 30 s khi foreground; Q8 đổi tên/xóa/move ở B, A nhận; Q9 bản Yêu thích vẫn đồng bộ như cũ. Không có ⇒ ghi **BLOCKED — chưa QA đồng bộ hai máy thật**, chỉ dựa test JVM S7–S8.

**Không tự động**: không bật/tắt tự đồng bộ thật trên tài khoản nếu đang tắt mà chủ dự án chưa cho; không gửi/xóa outbox cũ; không tắt mạng. Dọn sau QA: xóa `QA Thử 2` bằng UI (nếu đồng bộ đang bật, thao tác xóa cũng đồng bộ — ghi lại).

## 8. Rủi ro, rollback, điểm dừng

| Rủi ro | Giảm thiểu |
|---|---|
| Làm hỏng đồng bộ Yêu thích | Không sửa class Yêu thích; test chéo Foreign; chạy toàn bộ test E96/E105 không đổi assertion |
| E105 vẫn BLOCKED | Ghi rõ nhiệm vụ dựa vào nó; lỗi E105 tái hiện trong QA thì ghi riêng, không sửa trong nhiệm vụ này |
| Tin hỏng làm kẹt đọc Playlist có tên | Dừng tại tin Invalid giống Yêu thích, trạng thái “cần xử lý”; không bỏ qua ngầm |
| FLOOD_WAIT khi lưu hàng chờ lớn | 1 tin/lượt, backoff chung, giới hạn 1000 |
| File mới không đọc được | Không ghi đè; Playlist có tên báo không dùng được; Yêu thích không ảnh hưởng |
| Bài trên máy đổi id | Fingerprint; không khớp thì placeholder, không phát nhầm |
| Activity 8254 dòng | Logic thuần ở policy/model; Activity chỉ nối; giữ một luồng ghi |

**Rollback:** cờ mã `TelegramPlaylistFlags.NAMED_ENABLED` (hằng `true`, đặt cạnh `V2_ENABLED`). `false` thì ẩn UI Playlist có tên, không mở session, không chạy coordinator; giữ file. Không xóa dữ liệu, không hạ phiên bản.

**Dừng và báo BLOCKED khi:**
- phải đổi class/định dạng Yêu thích, `#mvsync`, SQLite, service/Media3, package/signer/versionCode;
- test cũ chỉ pass khi sửa assertion (ngoài nhãn S11);
- transport không cho gửi/đọc văn bản tùy ý mà không đổi hành vi cũ;
- Sony không nhận hoặc gate APK sai;
- cần tài khoản/đăng nhập để QA đồng bộ.

## Phụ lục A — chữ hiển thị

| Chỗ | Cũ | Mới |
|---|---|---|
| Nút ☆ | Lưu vào Playlist / Bỏ lưu khỏi Playlist | Lưu vào Yêu thích / Bỏ khỏi Yêu thích |
| Menu dòng | ☆ Thêm vào Playlist / ★ Xóa khỏi Playlist | ☆ Thêm vào Yêu thích / ★ Bỏ khỏi Yêu thích; mục mới **≡ Thêm vào Playlist…** |
| Chế độ cũ ở Gọn/Car | Playlist | Yêu thích (Car: chỉ chữ) |
| Tab/chip | Playlist | Playlist (giờ là chỉ mục) |
| Thông báo lưu | Đã lưu '…' trên máy. | Đã lưu '…' vào Yêu thích. |
| Thông báo Playlist có tên | — | Đã thêm '…' vào ‘<tên>’. / Đã bỏ '…' khỏi ‘<tên>’. / Playlist ‘<tên>’ đã đủ 1000 bài. / Đã có Playlist tên này. |
| Cài đặt | Tự đồng bộ Playlist | Tự đồng bộ Yêu thích & Playlist |
| Lỗi file | Không đọc được Playlist trên máy | (Yêu thích) Không đọc được Yêu thích trên máy / (mới) Không đọc được các Playlist trên máy |

Chữ hiển thị viết hoa/thường đúng như bảng. Mô tả TalkBack đổi tương ứng.
