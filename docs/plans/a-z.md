# Kế hoạch: Thanh A–Z (BACKLOG mục 1)

> Phiên lập kế hoạch: 18/10/2026, branch `feature/e93-03d-y700-offline-cockpit`, HEAD `ddf712d`. Chưa sửa code. **Không đổi schema DB** (giữ `DATABASE_VERSION = 2`).
> Chủ dự án đã chọn (18/10/2026): (1) thêm nút "Sắp xếp: Mới nhất / Tên A–Z", mặc định vẫn Mới nhất, app nhớ lựa chọn; thanh chữ cái chỉ hiện khi chọn Tên A–Z. (2) Hiện ở Điện thoại và Tablet (dọc + Hai khung ngang); Car và Gọn (Shanling) không có.

## 1. Bối cảnh — hiện tại
- Đặc tả `docs/ui-spec.md:205`: thanh A–Z mép phải, mỗi chữ cao ≥ 24dp, bấm giữ và kéo dọc để lướt. `:232`: Car bỏ A–Z.
- `MeloLibraryPane` (dùng chung Điện thoại/Tablet dọc và khung phải Hai khung) chỉ đọc `MeloScreenHost.snapshot()`; danh sách là `ListView` + `RowsAdapter`, tải thêm qua `host.loadMore()`.
- DB luôn xếp `date DESC, message_id DESC, chat_id DESC` (`AndroidTelegramLibraryDatabase.getSongs`, `:208`), trang 200 bài, tải thêm theo offset (`TelegramPreviewActivity.loadMoreSongsIfNeeded`, `:4285-4320`; tải đầu `:3677-3690`).
- Các nhánh không dùng DB lọc trong bộ nhớ: Playlist `:3671-3676`, một nguồn không DB `:3691-3696`, danh sách RAM `:3697-3702`, Đã tải (`offlineGroups`, `:4286-4292`).
- Có sẵn cột `search_key` (schema v2, Beta96) = `searchKey(title + " " + performer + " " + filename)`: chữ thường, bỏ dấu, đ→d. **Xếp theo cột này = xếp theo tên bài không dấu** ("Đường" nằm cùng nhóm D, "Ánh" cùng nhóm A) → không cần cột hay chỉ mục mới.

## 2. Thiết kế
**Sắp xếp**
- `enum SongSort { NEWEST, NAME }`. Lưu ở prefs `melovault_ui_lab` khóa `library_sort` (mặc định NEWEST).
- SQL khi NAME: `ORDER BY search_key ASC, chat_id ASC, message_id ASC` (thứ tự ổn định khi trùng tên). NEWEST giữ y nguyên câu hiện tại.
- Trong bộ nhớ (Playlist, một nguồn, RAM, Đã tải): `sortedWith(compareBy({ songSearchKey(...) }, { chatId }, { messageId }))` — cùng khóa với SQL nên thứ tự giống hệt.
- Nút chọn: một chip "↕ Mới nhất" / "↕ Tên A–Z" ở hàng tiêu đề nhóm, cạnh "▶ Phát hết". Đổi → về đầu danh sách, tải lại. "Phát hết" phát đúng thứ tự đang hiển thị.

**Nhóm chữ cái** (logic thuần `MeloAlphabetPolicy`)
- Thanh gồm `#` + A…Z (27 ô). Chữ của một bài = ký tự đầu của `search_key`; a–z → chữ hoa tương ứng; còn lại (số, ký hiệu, chữ ngoài Latin, khóa rỗng) → `#`.
- Vì `#` gồm cả ký tự đứng sau 'z' (ví dụ chữ Nhật), thứ tự SQL vẫn là: số/ký hiệu < a…z < ký tự ngoài Latin. Thanh coi `#` là "đầu danh sách" (nhảy về offset 0). [CẦN XÁC NHẬN] trên dữ liệu thật có bao nhiêu bài bắt đầu bằng ký tự ngoài Latin; nếu đáng kể, sau này thêm ô cuối "…".
- Mỗi chữ ánh xạ sang khóa chặn dưới: A→"a", B→"b"…; `#` → null (từ đầu).

**Nhảy chữ cái** (không tải 15.000 dòng vào RAM)
- DB (chế độ Tất cả / một kênh có DB): nhảy tới chữ M = tải lại danh sách với điều kiện thêm `search_key >= 'm'`, xếp NAME, offset 0. Tải thêm tiếp tục tự nhiên (M, N, O…). Muốn lên trên M thì bấm chữ trước đó hoặc `#`. Tiêu đề nhóm vẫn hiện tổng số bài thật (đếm không có chặn dưới); điều kiện dừng tải thêm dùng số đếm có chặn dưới (biến riêng, không ghi đè `totalCountForCurrentView`).
- Trong bộ nhớ: tìm vị trí bài đầu tiên có khóa ≥ chặn dưới (`MeloAlphabetPolicy.firstIndexAtOrAfter`, tìm nhị phân) rồi `list.setSelection(index)` — cuộn thật, không tải lại.
- Nếu chữ không có bài nào: nhảy tới chữ có bài kế tiếp (DB tự làm vì `>=`); nếu hết thì ở cuối danh sách.
- Kéo dọc trên thanh: đổi chữ theo vị trí ngón tay, hiện bong bóng chữ lớn giữa màn; chỉ gửi truy vấn sau khi chữ đứng yên 150 ms hoặc khi nhấc tay, mỗi truy vấn tăng `uiQueryGeneration` để kết quả cũ bị bỏ.
- Khi cuộn, ô chữ của bài đầu tiên đang thấy được tô màu nhấn.
- Đổi tìm kiếm / chip / nguồn / chế độ → bỏ chặn dưới (về đầu).
- Có ô tìm đang gõ: vẫn cho A–Z (chặn dưới cộng thêm điều kiện tìm).

**Giao diện**
- View mới `MeloAlphabetBar` (Android View tự vẽ, không thư viện ngoài), đặt mép phải trong `FrameLayout` bọc `ListView` của `MeloLibraryPane`. Rộng 28dp; mỗi ô cao `max(24dp, chiều cao khả dụng / 27)`; nếu không đủ chỗ cho 27 ô × 24dp (màn ngang thấp) thì gộp ô xen kẽ (A, C, E… vẫn nhảy được mọi chữ khi kéo) — quyết định trong `MeloAlphabetPolicy.visibleLetters(heightPx, minCellPx)`.
- Màu lấy từ `MeloDesignTokens`/palette hiện có; chừa lề phải danh sách bằng chiều rộng thanh để không che nút ⋮/trạng thái.
- `contentDescription` từng chữ cho TalkBack.
- Ẩn thanh khi: sắp xếp Mới nhất, đang xem lưới nguồn Telegram, danh sách < 50 bài, hoặc layer là Car/Gọn.

**Host** (`MeloScreenHost`, activity triển khai — màn Melo không tự truy vấn DB)
- Thêm: `fun sort(): SongSort`, `fun setSort(sort: SongSort)`, `fun jumpToLetter(lowerBound: String?)`; `Snapshot` thêm `sort: SongSort` và `letterFloor: String?`.
- Bản cũ (Cockpit/Portrait, Thư viện "Cũ") giữ nguyên thứ tự Mới nhất, không có thanh.

## 3. File cần sửa
(Gốc: `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/`.)

| File | Sửa gì |
|---|---|
| `MeloAlphabetPolicy.kt` (**mới**, logic thuần) | `enum SongSort`, `letterOf(searchKey)`, `lowerBound(letter)`, `firstIndexAtOrAfter(keys, bound)`, `visibleLetters(heightPx, minCellPx)`, `letterAt(y, height, letters)`, `barVisible(sort, showingSources, rowCount, layer)`. |
| `MeloAlphabetBar.kt` (**mới**) | View vẽ chữ, xử lý chạm/kéo, bong bóng chữ, callback `onLetter(letter, final)`. |
| `MeloLibraryPane.kt` | Bọc list trong FrameLayout + thanh; chip sắp xếp; debounce 150 ms; tô chữ khi cuộn; reset khi đổi nhóm (dùng `groupKey` có sẵn, thêm `sort`). |
| `MeloScreenHost.kt` | 3 hàm + 2 trường `Snapshot` như trên. |
| `TelegramLibraryDao.kt` | `getSongs(..., sort: SongSort = NEWEST, fromKey: String? = null)`, `getSongCount(..., fromKey: String? = null)`; mặc định giữ hành vi cũ → mọi chỗ gọi cũ không đổi. |
| `AndroidTelegramLibraryDatabase.kt` | `ORDER BY` theo `sort`; thêm `AND search_key >= ?` khi có `fromKey`. Không đổi `onCreate`/`onUpgrade`/version. |
| `InMemoryTelegramLibraryDao.kt` | Cùng hành vi (xếp + chặn dưới) để test phản ánh đúng. |
| `TelegramPreviewActivity.kt` | Chỉ: đọc/ghi pref `library_sort`; truyền `sort`/`fromKey` ở tải đầu `:3677-3690` và tải thêm `:4293-4320`; xếp trong bộ nhớ ở 3 nhánh `:3671-3702` và Đã tải; triển khai 3 hàm host + 2 trường snapshot. Không đụng phát nhạc, sync, TDLib. |

Không đụng: schema/`TelegramLibrarySql.kt`, `songKey`, khóa `(accountId, chatId, messageId)`, `MeloCarScreen`, `MeloCompactScreen`, flavor `personal`, package, signing. Không thêm thư viện/Compose.

## 4. Các bước làm (phiên thực hiện, Sonnet)
1. Đầu phiên kiểm git theo CLAUDE.md.
2. Viết `MeloAlphabetPolicy` + test.
3. DAO: thêm tham số, SQL, InMemory + test JDBC.
4. Host + activity (tải đầu, tải thêm, nhánh bộ nhớ, pref).
5. `MeloAlphabetBar` + gắn vào `MeloLibraryPane` + chip sắp xếp.
6. Chạy test Debug + Release, `assembleShareableDebug/Release`, `git diff --check`.
7. Commit `feat(shareable): A–Z jump bar with name sort, bump b97` (bump versionCode 97 / versionName `0.6.76-beta`).
8. Gate: bundle delta + SHA256 → Tiny build có Telegram config → verify signer `eecd3df7…ce8` → QA Sony (mục 6) → upload Drive riêng `MeloVault-Owner-Beta-v0.6.76-b97-Y700-REVIEW`.
9. Cập nhật HANDOFF (bản mới nhất, bug mở) và BACKLOG mục 1, commit docs, dừng.

## 5. Test (JVM, `app/src/test/.../data/telegram/`)
1. **`MeloAlphabetPolicyTest`** (mới): "Ánh trăng"→A, "Đường xa"→D, "ồ"→O, "01 - Intro"/"[FLAC] x"/""→#, "ベース"→#; `lowerBound`; `firstIndexAtOrAfter` (chữ có bài, chữ không có bài → bài kế, quá Z → cuối, danh sách rỗng); `visibleLetters` khi đủ chỗ (27) và thiếu chỗ (gộp nhưng vẫn có A và Z); `letterAt` ở mép trên/dưới/ngoài khung; `barVisible` (NEWEST/nguồn/<50 bài/Car/Gọn → ẩn).
2. **`TelegramLibrarySqlSchemaTest`** hoặc test JDBC mới: chèn bài có dấu; NAME trả về thứ tự Ánh < Bình < Đường < Zing, số/ký hiệu trước A; trùng tên xếp theo `chat_id, message_id`; `fromKey="m"` bỏ bài < m, đếm khớp số dòng; kết hợp `fromKey` + tìm + `chat_id`; bài `is_deleted=1` không ra; NEWEST giữ nguyên thứ tự cũ (hồi quy).
3. **`TelegramLibraryDaoTest`**: InMemory cho cùng thứ tự với SQL trên cùng dữ liệu; phân trang offset/limit với NAME + `fromKey`.
4. **`TelegramSearchPerformanceTest`** (bổ sung): 28.000 bài tổng hợp, đo NAME trang đầu, trang offset 5.000, `fromKey` ở giữa, + COUNT; assertion rộng (< 1 s mỗi lần trên Ni) để không flaky; in số ms.
5. Mốc mới = 1.145 + số test thêm, 21 skipped, 0 failed ở cả Debug và Release.

Tốc độ (ước lượng [CẦN XÁC NHẬN]): không có chỉ mục trên `search_key` nên mỗi trang là quét + sắp xếp top-N trên ~28k dòng; dự kiến 30–120 ms trên Sony. Nếu > 150 ms: đề xuất việc riêng thêm chỉ mục `(account_id, is_deleted, search_key)` — đó là đổi schema (v3), phải được duyệt.

## 6. Cách kiểm trên Sony (sau gate)
1. Không gỡ app, không `pm clear`; `adb install -r` đè b96. Kiểm app ở tiền cảnh trước mỗi bước, không BACK ở root. Ghi tổng số bài, số nguồn, playlist trước/sau — phải y nguyên.
2. Thư viện Mới, chế độ Tất cả: mặc định vẫn Mới nhất, **không** có thanh chữ cái.
3. Bấm chip sắp xếp → "Tên A–Z": danh sách về đầu, xếp theo tên; thanh chữ hiện ở mép phải, không che chữ/nút của dòng.
4. Bấm M → bài đầu tiên bắt đầu bằng M (hoặc chữ kế nếu không có M). Bấm D → có bài "Đ…" trong nhóm D. Bấm # → về đầu. Bấm Z rồi cuộn xuống → tải thêm chạy tới hết.
5. Kéo ngón dọc từ A đến Z (`adb shell input swipe` trên thanh, 1500 ms): bong bóng chữ đổi theo, khi nhấc tay danh sách nhảy đúng chữ cuối; không giật/đơ.
6. Cuộn tay: ô chữ được tô đổi theo bài trên cùng.
7. Bấm một bài sau khi nhảy → phát đúng bài đó; "Phát hết" → hàng đợi theo thứ tự tên từ chữ đang xem.
8. Lặp bước 3–4 ở: một kênh, Playlist, Đã tải, khi đang tìm "nhac".
9. Xoay ngang (Hai khung nếu ≥600dp; Sony ngang có thể không đủ — ghi lại), kiểm thanh vẫn bấm được, ô ≥ 24dp hoặc đã gộp.
10. Đóng hẳn app, mở lại → vẫn nhớ "Tên A–Z". Đổi lại "Mới nhất" → thanh ẩn, thứ tự như cũ.
11. Đo thời gian truy vấn trang (log ms, không log tên bài) cho 5 lần nhảy; chụp màn hình. Khôi phục rotation/density.

## 7. Tiêu chí đạt
- Mặc định không đổi gì với người không bật Tên A–Z (thứ tự Mới nhất, không thanh).
- Bật Tên A–Z: bấm/kéo chữ nhảy đúng nhóm, chữ có dấu nằm đúng chữ gốc (Đ→D, Á→A), số/ký hiệu ở #; ở Tất cả, một kênh, Playlist, Đã tải, khi đang tìm.
- Mỗi lần nhảy ≤ 150 ms trên Sony với thư viện thật; không ANR/crash; thanh không che nội dung; ô ≥ 24dp hoặc gộp hợp lý.
- Lựa chọn sắp xếp được nhớ sau khi mở lại app. Số bài/nguồn/playlist y nguyên; DB vẫn version 2.
- Car và Gọn không có thanh. Test Debug + Release 0 failed, `git diff --check` sạch, signer `eecd3df7…ce8`.
- Chủ dự án kiểm trên Y700 (dọc và ngang Hai khung) và S25.

## 8. Ngoài phạm vi
- Chỉ mục mới cho `search_key` (đổi schema — chỉ làm nếu đo chậm và được duyệt).
- Thư viện "Cũ" (Cockpit/Portrait), Car, Gọn, flavor `personal`.
- Sắp theo ca sĩ/kênh/thời lượng; nhóm riêng cho chữ ngoài Latin.
- Đổi kênh nhanh ở khung trái, "Các kênh chưa có nhạc (N)", chip Mixset theo thời lượng (việc khác trong BACKLOG).
