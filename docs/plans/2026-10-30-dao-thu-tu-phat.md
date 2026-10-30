# Kế hoạch — Thứ tự phát khi bật Đảo (gỡ BLOCKED S6)

TASK_ID: `2026-10-30-dao-thu-tu-phat`. Vai: **lập kế hoạch** (Claude Code, 30/10/2026). Không code/build trong phiên này.

## 1. Yêu cầu và nguồn gốc

- Chủ dự án giao 30/10/2026: “Giao lập kế hoạch chi tiết Đảo”.
- Nguồn: nhiệm vụ `2026-10-29-melo-ui-ux-ke-hoach` ghi **BLOCKED thứ tự khi bật Đảo (S6)**; nghiệm thu chính thức R1c `b599e80` (PASS có giới hạn) giữ nguyên mục này ngoài PASS. Bàn giao S6 yêu cầu: chốt **hợp đồng thứ tự** — một authority duy nhất cho Next trên UI/transport/hàng chờ, mapping hàng chờ logic ↔ cửa sổ Media3, hành vi Nhạc trên máy, file/test và quyền sửa service.
- Kế hoạch này **cần sửa `ShareablePlaybackService`** (service phát). Phê duyệt kế hoạch = cho phép sửa service trong đúng phạm vi mục 5. Không đổi schema, songKey, package, signer, tài khoản, TDLib.

## 2. Baseline

| Mục | Giá trị |
|---|---|
| Repo/worktree kế hoạch | `D:\Bots\_worktrees\MeloVault-dao-thu-tu-phat` |
| Nhánh | `dot/2026-10-30-dao-thu-tu-phat` (tạo từ `c6c7970`) |
| Baseline source | `c6c7970` = R1c `b599e80` + hồ sơ (nhánh `dot/2026-10-29-melo-ui-ux-ke-hoach`, chưa merge vào nhánh tích hợp) |
| Phiên bản | vc121 / `0.6.96-beta`; Media3 `1.11.1` |
| APK đang trên Sony | R1c Debug `138688175DC0…3482` (theo hồ sơ R1; phải kiểm lại) |

Lý do lấy `c6c7970` làm gốc: phần sửa phụ thuộc `MeloQueuePane`, Deck/Tablet của S6–R1c, chưa có trên `main`/nhánh tích hợp. Phiên thực thi **không** merge nhánh UI/UX hay nhánh này.

## 3. Hiện trạng (đối chiếu source `c6c7970`, chưa tái hiện trên thiết bị)

Nút Đảo ở mọi màn (`MeloStudioDeck`, `MeloTabletScreen`, `MeloCompactScreen`, `NowPlayingFullScreenDialog`) chỉ lật `shuffleModeEnabled` của Media3. Có **ba nguồn thứ tự** không khớp nhau:

| Nơi quyết định “bài kế” | Thứ tự đang dùng | File/dòng |
|---|---|---|
| UI hàng chờ (`host.queue()`) | `queueSongOrder` = thứ tự gốc | `TelegramPreviewActivity.kt:6122` |
| ⏭/⏮ trên app, hết bài khi app mở (`STATE_ENDED` → `playAdjacent(1)`) | `queueSongOrder` index ±1 (gốc) | `:6317`, `:1323` |
| Tự chuyển bài trong cửa sổ Media3, Next từ khóa màn/tai nghe khi bài kế đã nạp | `ShuffleOrder` ngẫu nhiên của ExoPlayer **trên cửa sổ ≤ 17 bài đã tải liền kề** | `enqueueTelegramPlayback` `:1371`; `NextAwarePlayer.seekToNextMediaItem` service `:136` |
| Tải trước/nạp bài kế (app và service) | `immediateSuccessor` trên thứ tự gốc | `prefetchNextCachedNeighbor` `:7679`; service `nextTrack` `:320` |

Hệ quả dự đoán (cần S0 xác nhận): khi Đảo bật với bài Telegram, bài phát kế có thể là một bài ngẫu nhiên trong cửa sổ đã tải, rồi khi cửa sổ cạn lại quay về “bài sau theo gốc” của bài đó; UI ghi “Thứ tự gốc · Đảo đang bật” nên không phản ánh bài sẽ phát. `playAll(shuffle=true)` (“Phát ngẫu nhiên” ở Kho tổng hợp) luôn bắt đầu từ hàng đầu. Nhạc trên máy: Media3 xáo trên cửa sổ ≤ 500 bài nên transport đúng ngẫu nhiên, nhưng UI hàng chờ hiện thứ tự gốc.

## 4. Hợp đồng thứ tự đề xuất (cần chủ dự án chốt)

**Nguyên tắc: Media3 không tự xáo. Cờ `shuffleModeEnabled` chỉ là trạng thái Bật/Tắt. “Thứ tự phát” do Melo tính bằng một policy thuần, và `ShareablePlaybackService` là nơi duy nhất giữ trạng thái thứ tự (gốc, seed, thứ tự phát).**

| # | Quy tắc | Đề xuất mặc định |
|---|---|---|
| Q1 | Ý nghĩa Đảo | Xáo **toàn bộ hàng chờ của nguồn hiện tại**; bài đang phát đứng đầu, các bài còn lại hoán vị ngẫu nhiên một lần (Fisher–Yates, seed lưu trong service). Thứ tự phát cố định cho tới khi tắt Đảo hoặc chọn hàng chờ mới. |
| Q2 | Phạm vi | Telegram **và** Nhạc trên máy (chặng S4 riêng, có thể cắt nếu chủ dự án chỉ muốn Telegram). |
| Q3 | Tắt Đảo | Trở về thứ tự gốc; bài đang phát giữ nguyên, không tua lại; ⏭ phát bài sau nó theo gốc. |
| Q4 | Chạm bài trong hàng chờ khi Đảo bật | Nhảy tới bài đó, **không xáo lại**. Danh sách gốc nạp thêm (cuộn tải trang) thì giữ phần đã định, bài mới xáo rồi nối cuối. |
| Q5 | “⏭ Phát tiếp theo” khi Đảo bật | Chèn ngay sau bài đang phát trong thứ tự phát (ý định người dùng thắng Đảo); trong thứ tự gốc chèn sau bài hiện tại như cũ. |
| Q6 | “Phát ngẫu nhiên” (`playAll(true)`) | Bật Đảo rồi bắt đầu từ **một bài ngẫu nhiên** trong danh sách đang hiện. |
| Q7 | ⏮ khi Đảo bật | Bài trước trong thứ tự phát; bài đầu thứ tự phát thì giữ hành vi hiện tại. |
| Q8 | Lưu trạng thái | Không thêm lưu Đảo qua khởi động lại (giữ hiện trạng Shareable). |
| Q9 | Lặp tất cả + Đảo | Giữ hành vi hiện có; ghi giới hạn, không mở rộng trong nhiệm vụ này. |

Phương án đã loại:
- **Đọc thứ tự xáo của Media3 để hiển thị**: Media3 chỉ giữ cửa sổ bài đã tải (Telegram ≤ 8 trước/8 sau), không phải hàng chờ logic; không giải được Next ngoài cửa sổ.
- **Activity tự tính và đẩy cho service**: Next/Đảo từ khóa màn, tai nghe, Bluetooth khi app đóng sẽ không cập nhật thứ tự → hai authority.
- **Tắt Đảo cho Telegram/ẩn nút**: không đáp ứng tính năng.

## 5. Thiết kế kỹ thuật

### 5.1 Policy thuần mới — `MeloShuffleOrderPolicy`

File mới `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloShuffleOrderPolicy.kt`, `internal object`, generic `<T>` (Telegram dùng `Long` messageId, Local dùng `String` mediaId). Không Android API, không log, không I/O.

```kotlin
internal object MeloShuffleOrderPolicy {
    /** anchor đứng đầu (nếu có trong base), phần còn lại Fisher–Yates bằng java.util.Random(seed); bỏ trùng, giữ lần xuất hiện đầu. */
    fun <T> shuffled(base: List<T>, anchor: T?, seed: Long): List<T>
    /** Hàng chờ mới được cấu hình. enabled=false → newBase. enabled=true:
     *  - newBase == oldBase và anchor ∈ oldPlay → oldPlay (Q4, không xáo lại)
     *  - anchor ∈ oldPlay và newBase ⊇ phần lớn oldBase (cùng nguồn) → giữ oldPlay lọc theo newBase, bài mới (newBase − oldPlay) shuffled(seed) nối cuối
     *  - còn lại → shuffled(newBase, anchor, seed) */
    fun <T> rebase(oldBase: List<T>, oldPlay: List<T>, newBase: List<T>, anchor: T?, enabled: Boolean, seed: Long): List<T>
    /** Bật: shuffled(base, current, seed). Tắt: base. */
    fun <T> toggled(base: List<T>, current: T?, enabled: Boolean, seed: Long): List<T>
    /** Bỏ id cũ rồi chèn ngay sau current (current không có → cuối). Dùng cho cả base và play (Q5). */
    fun <T> insertNext(order: List<T>, current: T?, id: T): List<T>
    /** Thứ tự gốc của các id còn có mặt; id có mặt mà không thuộc base nối cuối theo thứ tự present. */
    fun <T> restore(base: List<T>, present: List<T>): List<T>
}
```

Quy tắc “cùng nguồn” ở `rebase`: định nghĩa đơn giản và test được — `oldBase.isNotEmpty() && oldBase.count { it in newBaseSet } * 2 >= oldBase.size`. Ghi đúng quy tắc này trong KDoc; không thêm heuristic khác.

Test mới `app/src/test/java/com/bsdung/musicassistantcar/data/telegram/MeloShuffleOrderPolicyTest.kt` (≥ 14 case): hoán vị đủ phần tử/không trùng; anchor đứng đầu; anchor không có trong base; cùng seed → cùng kết quả, khác seed → khác (với n=50); base rỗng/1 phần tử; base có trùng; `toggled` tắt trả base; `rebase` 3 nhánh (giữ nguyên, nối bài mới, xáo lại khi đổi nguồn); `rebase` enabled=false; `insertNext` khi id đã có/current null/current cuối; `restore` có id lạ và id bị xóa; 2000 phần tử chạy < 50 ms.

### 5.2 Service — authority duy nhất (`ShareablePlaybackService.kt`)

1. `onCreate` (sau `ExoPlayer.Builder…build()`, dòng ~210): `player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(0))` (`androidx.media3.exoplayer.source.ShuffleOrder`). Media3 giữ thứ tự đồng nhất qua `cloneAndInsert/cloneAndRemove` khi `setMediaItems/addMediaItem`. Thêm `onTimelineChanged(reason == PLAYLIST_CHANGED)` kiểm: nếu `shuffleModeEnabled` và `player.nextMediaItemIndex` khác `currentMediaItemIndex+1` (khi có bài sau) → đặt lại `UnshuffledShuffleOrder(mediaItemCount)` và log Debug `stage=order_reset` (lưới an toàn; S0/S5 phải chứng minh không xảy ra hoặc đã tự sửa).
2. Trạng thái mới: `telegramBase: List<QueueTrack>`, `shuffleSeed: Long`, `localBaseIds: List<String>`. `queueTracks` đổi nghĩa thành **thứ tự phát**. `clearQueue()` xóa cả ba.
3. Companion API (cùng tiến trình, main thread, giữ kiểm tài khoản như hiện tại):
   - `configureTelegramQueue(accountId, ownerRoot, ordered, anchorMessageId: Long)` — `ordered` là thứ tự gốc. `setQueue` lưu base, tính `queueTracks` bằng `rebase(oldBaseIds, oldPlayIds, newBaseIds, anchor, player.shuffleModeEnabled, seed)`; seed mới (`SystemClock.elapsedRealtimeNanos()` xor `System.nanoTime()`) chỉ khi rơi vào nhánh xáo lại. Giữ giới hạn 2000 và kiểm `messageId/fileId`.
   - `queueTelegramNext(accountId, track: QueueTrack)` — `insertNext` vào cả base và play theo bài đang phát; `refreshCommands()`; xóa khỏi cửa sổ Media3 mọi mục Telegram **sau** current không còn đúng thứ tự phát (dùng chung hàm ở mục 4).
   - `currentTelegramQueue(accountId)` — giữ chữ ký, trả **thứ tự phát**.
   - `orderLocalQueue(baseIds: List<String>, anchorId: String): List<String>` — lưu `localBaseIds`; trả `shuffled(baseIds, anchorId, seed mới)` nếu Đảo bật, ngược lại `baseIds`.
4. Listener ExoPlayer `onShuffleModeEnabledChanged(enabled)` (nhận thay đổi từ **mọi** nơi: app, Deck, dialog, khóa màn, Bluetooth):
   - Bài hiện tại Telegram: seed mới khi bật; `queueTracks = toggled(baseIds, currentId, enabled, seed)` ánh xạ về `QueueTrack`. Cắt cửa sổ Media3: `removeMediaItems(currentIndex+1, count)` và `removeMediaItems(0, currentIndex)` (không động vào bài đang phát, không `stop/prepare`). `refreshCommands()`; nếu UI không gắn → `requestNextIfMissing()`.
   - Bài hiện tại Local (`MeloLocalSourcePolicy.idOfMediaId != null`): bật → nếu `localBaseIds` rỗng lấy thứ tự hiện có làm gốc; đích = `toggled`. Tắt → đích = `restore(localBaseIds, presentIds)`. Áp đích giữ bài đang phát: lấy `MediaItem` theo id trước khi xóa, xóa mọi mục khác, `addMediaItems(0, trướcCurrent)` và `addMediaItems(currentIndex+1, sauCurrent)`. Cờ `reorderingLocal` chặn tái nhập.
   - Không có bài: chỉ đổi cờ.
5. Không đổi: TDLib resolve/download/retry, `insertNext` (chèn sau current, đúng với thứ tự đồng nhất), `nextTrack` (đã dùng `queueTracks`), sleep timer, MediaSession commands.
6. Log Debug duy nhất tag `MeloShuffle`, chỉ khi `BuildConfig.DEBUG`: `stage=<toggle|configure|next_inserted|order_reset|local_reorder> on=<bool> size=<n> cur=<index> next=<index>`. **Không** log tiêu đề, messageId, chatId, đường dẫn.

### 5.3 Activity (`TelegramPreviewActivity.kt`)

1. Helper mới `syncTelegramQueueFromService(base: List<Song>)`: đọc `currentTelegramQueue(account)` và ánh xạ id → `Song` lấy từ `base` (giữ `durationSeconds/sourceTitle`); id không có trong base mới tạo `Song` như `restorePlayingQueueIfAny`. Gán `queueSongOrder`.
2. `enqueueTelegramPlayback` (`:1371`) và `enqueueProgressivePlayback` (`:1424`): tính base như hiện tại (`TelegramQueueRetentionPolicy.forSelected`), gọi `configureTelegramQueue(..., base, anchorMessageId = song.messageId)`, rồi `syncTelegramQueueFromService(base)` **trước** khi tính `TelegramContiguousQueuePolicy.indices` — cửa sổ Media3 vì vậy liền kề theo thứ tự phát. Gom phần map `QueueTrack` lặp 3 lần thành một hàm `queueTracksOf(list)`.
3. `addToUpcomingQueue` (`~:6395`): thay `configureTelegramQueue` bằng `queueTelegramNext`, rồi `syncTelegramQueueFromService(queueSongOrder + song)`.
4. `restorePlayingQueueIfAny` (`:4053`): giữ, nhưng ưu tiên Song có sẵn trong `queueSongOrder/shownSongs` để không mất thời lượng/nguồn trên UI.
5. `playbackListener`: thêm `onShuffleModeEnabledChanged` → `mainRoot.post { restorePlayingQueueIfAny(acc); prefetchNextCachedNeighbor(); updateCockpitPlayer(); notifyMeloPlayback() }` (post để chạy sau khi service đã xử lý).
6. `toggleShuffle` (`:5985`) và nút trong `NowPlayingFullScreenDialog` (`:475`): giữ — chỉ lật cờ; service lo thứ tự.
7. `playAll(shuffle)` (`:6046`): khi `shuffle`, bật cờ trước, chọn chỉ số ngẫu nhiên trong danh sách đang hiện (`kotlin.random.Random`), gọi `playShownSong(i)` / `playLocal(i)` (Q6).
8. `playLocal` (`:757`): sau khi có `window`, gọi `orderLocalQueue(ids của window, anchor = id bài chọn)`; sắp `MediaItem` theo kết quả; `startIndex` = vị trí anchor trong kết quả.
9. `playAdjacent`, `localQueueItems`, `playLocalQueueAt`, `prefetchNextCachedNeighbor`, `insertCachedNeighborIfRelevant`: **không sửa logic**; chúng tự đúng vì `queueSongOrder`/Media3 nay là thứ tự phát. Phiên thực thi phải đọc lại và xác nhận trong báo cáo.

### 5.4 UI chữ

- `MeloQueueUiPolicy` thêm `fun caption(shuffle: Boolean, preview: Boolean): String`: Đảo bật → `"Thứ tự phát · Đảo bật"`; tắt giữ `"Tiếp theo"` (preview) / `"Hàng đợi"`. `MeloQueuePane.kt:63` dùng hàm này. Thêm case vào `MeloQueueUiPolicyTest`.
- KDoc `MeloScreenHost.queue()` (`:133`): “Thứ tự phát thực tế (đã xáo khi Đảo bật), do ShareablePlaybackService giữ.”
- Không đổi bố cục, màu, chip Đảo.

## 6. Các chặng thực thi

Mỗi bước commit riêng trên `dot/2026-10-30-dao-thu-tu-phat`, cập nhật `*-bao-cao.md` và `*-ban-giao.md` ngay sau mỗi mốc.

| Chặng | Việc | Hoàn tất khi |
|---|---|---|
| **S0** (phiên 1, Sony, không code) | Tái hiện baseline R1c: nguồn Telegram ≥ 10 bài phần lớn đã tải, bật Đảo, ⏭ ×3, so với hàng chờ; chụp ảnh + log. | Có/không có lệch được ghi bằng ảnh. Không tái hiện được thì ghi đúng, vẫn tiếp S1. |
| **S1** | `MeloShuffleOrderPolicy` + test. | Test mới pass Debug/Release. |
| **S2** | Service mục 5.2 (identity order, trạng thái, API, listener Telegram/Local, log). | Biên dịch hai biến thể; test cũ không đổi kết quả. |
| **S3** | Activity mục 5.3.1–5.3.7 (Telegram). | Biên dịch; test pass. |
| **S4** | Local: 5.3.8 + nhánh local của listener. Chủ dự án chọn chỉ Telegram ở Q2 → bỏ S4, ghi lý do. | Biên dịch; test pass. |
| **S5** | UI chữ 5.4 + test. | Full test/build Ni xanh. |
| **S6** (phiên 2) | Bundle SHA256 → Tiny build có config → gate APK → Sony QA mục 8 → Shanling nếu ADB thấy → Drive owner-only → hồ sơ APK/ảnh → prompt sơ bộ. | Hồ sơ đủ theo CLAUDE.md 8–9. |

Chia phiên: **Phiên thực thi 1 = S0–S5 trên Ni** (S0 dùng Sony qua Tiny); **Phiên thực thi 2 = S6**. Không gộp với phiên kế hoạch.

## 7. Test và build

Trên Ni, chạy một lần, xuất log ra file dưới `docs/plans/artifacts/2026-10-30-dao-thu-tu-phat/`, chỉ đọc dòng tổng kết và XML `build/test-results`:

```
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
git diff --check
```

Ghi tách passed/skipped/failed; so với mốc gần nhất trong hồ sơ R1 (21 skipped là bình thường). Không commit APK/log/ảnh. Giữ vc121/`0.6.96-beta` (cài `install -r` cùng versionCode không phải downgrade); chỉ tăng nếu chủ dự án yêu cầu.

## 8. QA thiết bị (Sony XQ-AS52 qua Tiny; Shanling nếu ADB thấy)

Gate trước cài theo CLAUDE.md mục 3/6 (serial, model, package, versionCode ≥ đang cài, cert `eecd3df7…ce8`, Telegram config true, MediaSession không phát). Chỉ `install -r`. Màn OFF trong `finally`, ghi `SONY_SCREEN_END`/`SHANLING_SCREEN_END`. Bằng chứng: ảnh trước/sau + dòng log `MeloShuffle` + `dumpsys media_session` (chỉ trạng thái, không tiêu đề nếu có PII).

| # | Kịch bản | Kỳ vọng |
|---|---|---|
| Q-1 | Nguồn Telegram đã tải ≥ 10 bài, phát bài A, bật Đảo, mở hàng chờ | Nhãn “Thứ tự phát · Đảo bật”; A đứng đầu, phần sau không theo gốc; bài A không bị ngắt |
| Q-2 | ⏭ ×3 (nút app) | Bài phát lần lượt = hàng 2, 3, 4 trong hàng chờ; log `next=` khớp |
| Q-3 | Chạm hàng thứ 6 trong hàng chờ, rồi ⏭ | Phát hàng 6, sau đó hàng 7; thứ tự hàng chờ không đổi |
| Q-4 | Menu bài X → “⏭ Phát tiếp theo”, ⏭ | X hiện ngay sau bài đang phát và được phát |
| Q-5 | Tắt Đảo | Hàng chờ về thứ tự gốc, bài đang phát giữ vị trí; ⏭ phát bài sau theo gốc |
| Q-6 | Bật Đảo, tắt màn, để hết bài hoặc ⏭ từ thông báo (Sony; **không dùng `cmd media_session dispatch` trên Shanling**) | Bài kế đúng hàng 2 của thứ tự phát (đối chiếu log) |
| Q-7 | Nhạc trên máy (nếu làm S4): bật Đảo, ⏭ ×2, tắt Đảo | Hàng chờ và transport khớp; tắt về gốc, không ngắt bài |
| Q-8 | Bật/tắt Đảo 10 lần liên tiếp | Không crash/ANR, bài đang phát không dừng |

Media3 PLAYING ≠ âm thanh thật: ghi **CHƯA XÁC NHẬN ÂM THANH THỰC**. Tạo `00_tong_hop.png`. Không dùng Sony thay chứng cứ Shanling.

## 9. Thao tác chủ dự án thử (nghiệm thu chính thức)

1. Sony/Shanling, Thư viện → chọn nguồn Telegram có nhiều bài đã tải → phát một bài → mở Đang phát, bấm **⇄ Đảo**: hàng chờ ghi “Thứ tự phát · Đảo bật”, bài đang nghe đứng đầu.
2. Bấm ⏭ vài lần: mỗi bài phát đúng bài kế trong hàng chờ.
3. Chạm một bài giữa hàng chờ: phát bài đó; hàng chờ không bị xáo lại.
4. Bấm Đảo lần nữa (Tắt): danh sách về thứ tự gốc, nhạc không ngắt.
5. Kho tổng hợp → “Phát ngẫu nhiên”: bắt đầu từ một bài ngẫu nhiên, hàng chờ đã xáo.
6. (Nếu làm S4) Nhạc trên máy: lặp bước 1–4.

## 10. Rủi ro, dừng và rollback

| Rủi ro | Xử lý |
|---|---|
| `UnshuffledShuffleOrder` bị Media3 thay khi controller `setMediaItems` | Lưới an toàn 5.2.1 + log `order_reset`; nếu Q-2 vẫn lệch → **DỪNG, BLOCKED**, không tự đổi kiến trúc |
| Cắt cửa sổ Media3 khi bật Đảo làm mất bài đã nạp | Chấp nhận (bài sẽ tải/nạp lại theo thứ tự phát); không xóa file cache |
| Local 500 bài: remove/add làm giật | Đo thời gian trong log; > 300 ms hoặc gián đoạn tiếng → ghi FAIL Q-7, báo chủ dự án |
| Activity và service lệch do thứ tự callback | Activity luôn đọc lại từ service sau `post`; không tự tính thứ tự |
| Q-6 (màn tắt) không thao tác an toàn được | Ghi CHƯA KIỂM CHỨNG, không dùng lệnh rủi ro crash |

**Dừng ngay khi:** cần đổi schema/DB/TDLib/songKey/signer/package; HEAD/nhánh/cây bẩn lạ; test cũ hỏng không rõ lý do; ADB unauthorized/sai cert/downgrade; service crash khi đổi Đảo.

**Rollback:** commit riêng từng chặng; trước commit chỉ sửa lại phần mình viết. Sau commit, khi chủ dự án giao, dùng `git revert` từng commit theo thứ tự ngược. Thiết bị: cài lại R1c Debug bằng `install -r` sau gate. Cấm reset/clean/stash/uninstall/pm clear.

## 11. Ngoài phạm vi

Lưu Đảo qua khởi động lại; Lặp tất cả + Đảo; P2-A/P2-B của nhiệm vụ UI/UX; nhiều Playlist có tên; Car Mode/Personal (`MainActivity`, `MusicService`, `WindowedQueueManager`); merge/push/public.

## 12. Điều chưa rõ

- Hành vi lệch chưa tái hiện trên thiết bị trong phiên kế hoạch (không dùng Sony); S0 làm việc này.
- Chưa xác minh bằng chạy thực rằng `UnshuffledShuffleOrder` được giữ qua `MediaController.setMediaItems` ở Media3 1.11.1 (đọc theo thiết kế API).
- Q1–Q9 là đề xuất; chủ dự án duyệt kế hoạch tức chốt mặc định, hoặc ghi lựa chọn khác vào `*-ban-giao.md` trước phiên thực thi.
