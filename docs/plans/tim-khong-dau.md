# Kế hoạch: Tìm không dấu (BACKLOG mục 3)

> Phiên lập kế hoạch: 18/10/2026, branch `feature/e93-03d-y700-offline-cockpit`, HEAD `44c2b4b`. Chưa sửa code.
> Việc này đổi schema DB → chủ dự án cần duyệt kế hoạch này trước khi phiên thực hiện bắt đầu.

## 1. Bối cảnh — hiện tại tìm thế nào

- Ô tìm (`songFilter`) của màn cũ và màn Melo (`MeloScreenHost.setQuery` → `songFilter.setText`) cùng đi qua `TelegramPreviewActivity`:
  - Chế độ "Tất cả" có DB: `db.getSongCount/getSongs(searchQuery = q)` (`TelegramPreviewActivity.kt:3679-3680`, tải thêm `:4304-4306`, offline scan `:3509`).
  - Playlist / một nguồn không DB / danh sách RAM: lọc trong bộ nhớ bằng `title.contains(q, ignoreCase = true) || filename.contains(...)` (`:3673-3700`).
- SQL: `AndroidTelegramLibraryDatabase.getSongs/getSongCount` (`:166-170`, `:206-210`) thêm `title LIKE '%q%'`.
  - `LIKE` của SQLite chỉ không phân biệt hoa/thường với chữ ASCII; "Sử" ≠ "sử" ≠ "su". Chỉ tìm trên `title`, không tìm `performer`/`filename`. Không thoát ký tự `%` `_`.
  - `%q%` đầu có `%` nên **hiện đã quét toàn bảng**, chỉ mục `idx_tg_songs_title` không được dùng cho tìm.
- `InMemoryTelegramLibraryDao` (dùng trong test) lọc `title.lowercase().contains(...)` — cũng phân biệt dấu.
- Đã có sẵn hàm bỏ dấu thuần, có test: `MeloComponentPolicy.searchKey()` ("Sử Thanh Hóa" → "su thanh hoa", "ĐƯỜNG" → "duong") và `matchesSearch()` (mọi từ trong câu tìm đều phải có, không cần đúng thứ tự) — `MeloComponentPolicy.kt:102-117`, test `MeloComponentPolicyTest.kt:80-86`. Hiện chỉ màn xem thử Melo UI dùng.
- DB: `melovault_tg_index_{accountId}.db`, `TelegramLibrarySql.DATABASE_VERSION = 1`, `onUpgrade` trống. DB được mở lần đầu trong `executeOnDb` (`loadDatabaseIndex`, `TelegramPreviewActivity.kt:5119-5122`) → nâng cấp chạy ngoài main thread.

## 2. Có cần đổi schema không? — Có (khuyến nghị)

Android `SQLiteDatabase` không cho đăng ký hàm SQL tự viết (bỏ dấu) trên minSdk 26, và `lower()`/`LIKE` của SQLite không hiểu chữ Việt. Lọc 28.000 bài bằng Kotlin mỗi lần gõ phá phân trang/đếm theo SQL. Vì vậy thêm **một cột chuẩn hóa sẵn**.

**Thay đổi schema (v1 → v2):**
- Bảng `telegram_songs` thêm cột `search_key TEXT NOT NULL DEFAULT ''` = `searchKey(title + " " + performer + " " + filename)`.
- Không thêm chỉ mục mới (tìm chuỗi con `%x%` không dùng được B-tree). Không đổi khóa chính, `songKey`, `(accountId, chatId, messageId)`, bảng nguồn, bảng sync, playlist.
- Không dùng FTS: nặng hơn, phải đồng bộ bảng phụ, và không tìm được giữa từ ("thanh" trong "thanhhoa"); với 28k bài quét thẳng đã đủ nhanh (mục 6).

**Nâng cấp không mất dữ liệu (`onUpgrade` 1→2):**
1. `ALTER TABLE telegram_songs ADD COLUMN search_key TEXT NOT NULL DEFAULT ''` — chỉ thêm cột, mọi dòng cũ giữ nguyên.
2. Điền `search_key` cho mọi dòng có sẵn trong **cùng giao dịch** của `onUpgrade` (SQLiteOpenHelper tự bọc transaction; nếu app bị tắt giữa chừng, version vẫn là 1, lần mở sau làm lại từ đầu — không có trạng thái nửa vời): đọc `account_id, chat_id, message_id, title, performer, filename` bằng cursor, cập nhật bằng một `SQLiteStatement` biên dịch sẵn `UPDATE ... SET search_key=? WHERE account_id=? AND chat_id=? AND message_id=?`.
3. Không xóa/tạo lại bảng, không `DROP`, không xóa cache/playlist/file nhạc. Không cần sync lại Telegram.
4. Lưới an toàn: nếu `search_key = ''` vẫn còn (dòng hỏng), câu tìm vẫn có nhánh dự phòng `OR (search_key = '' AND title LIKE ?)` để không mất kết quả.
5. Hạ phiên bản (cài APK cũ đè): Android không cho cài versionCode thấp hơn đè lên, nên không phát sinh; không viết `onDowngrade`. Ghi chú này vào HANDOFF.
- `onCreate` (cài mới) tạo bảng có sẵn cột; thêm `MIGRATE_1_TO_2` vào `TelegramLibrarySql`.

## 3. Cách tìm mới

- Chuẩn hóa câu tìm bằng cùng hàm `searchKey()`, tách theo khoảng trắng, mỗi từ thêm điều kiện `search_key LIKE ? ESCAPE '\'` (AND giữa các từ) → "su thanh hoa", "Sử Thanh Hóa", "hoa su", "SU THANH" đều ra "Sử Thanh Hóa". Thoát `\`, `%`, `_` trong từ.
- Giới hạn tối đa 8 từ (từ dư bỏ) để câu SQL không phình.
- Ghi: `upsertSongs` và `commitPageAndCheckpoint` luôn `put(search_key, ...)` — bài sync mới tự có khóa.
- Các nhánh lọc trong bộ nhớ (`TelegramPreviewActivity.kt:3673-3700`) đổi sang `MeloComponentPolicy.matchesSearch(q, title, performer, filename)` để playlist / một nguồn cũng tìm không dấu và nhất quán với DB.
- `InMemoryTelegramLibraryDao` dùng cùng hàm để test phản ánh đúng hành vi thật.

## 4. File cần sửa

| File | Sửa gì |
|---|---|
| `data/telegram/TelegramSearchPolicy.kt` (**mới**, logic thuần) | `songSearchKey(title, performer, filename)`, `queryTerms(query)` (tách từ, giới hạn 8), `likePattern(term)` (thoát ký tự), `whereClause(terms)` trả về chuỗi SQL + args. Gọi lại `MeloComponentPolicy.searchKey`, không chép lại. |
| `data/telegram/TelegramLibrarySql.kt` | `DATABASE_VERSION = 2`, hằng `COL_SEARCH_KEY`, cột trong `CREATE_TABLE_SONGS`, `MIGRATE_1_TO_2_ADD_COLUMN`, giữ nguyên `ALL_CREATE_STATEMENTS` còn lại. |
| `data/telegram/AndroidTelegramLibraryDatabase.kt` | `onUpgrade` (bước 2.1–2.2, ghi log chỉ số dòng + ms, không log tên bài); `put(search_key)` ở 2 hàm ghi; `getSongs`/`getSongCount` dùng `TelegramSearchPolicy.whereClause`. |
| `data/telegram/InMemoryTelegramLibraryDao.kt` | Lọc bằng `TelegramSearchPolicy`/`matchesSearch`. |
| `data/telegram/TelegramPreviewActivity.kt` | Chỉ 3 nhánh lọc trong bộ nhớ `:3673-3700` (không đụng chỗ khác). |
| `data/telegram/TelegramLibraryDao.kt` | Cập nhật KDoc: tìm không dấu theo tên bài/ca sĩ/tên file. |

(Đường dẫn gốc: `app/src/shareable/java/com/bsdung/musicassistantcar/`.) Không đụng flavor `personal`, `MainActivity`/`NavigationManager`, package, signing.

## 5. Test cần thêm (JVM, `app/src/test/.../data/telegram/`)

1. **`TelegramSearchPolicyTest`** (mới): "Sử Thanh Hóa"/"su thanh hoa"/"SU THANH HOA"/"hoa su" khớp; đ/Đ, ư, ơ, ă, â, ê, ô; chuỗi dạng NFD (dấu tách rời) và NFC cho cùng khóa; nhiều khoảng trắng; câu trống → không lọc; `%`, `_`, `\` được thoát; >8 từ bị cắt; "lakhouse" không khớp.
2. **`TelegramLibrarySqlSchemaTest`** (bổ sung, SQLite JDBC thật):
   - Cột `search_key` có trong bảng tạo mới.
   - **Migration**: tạo bảng bằng bản sao DDL v1 đóng băng trong test, chèn vài bài (có dấu, ca sĩ null), chạy `MIGRATE_1_TO_2_ADD_COLUMN` + backfill (cùng logic SQL/policy), kiểm: số dòng không đổi, mọi cột cũ giữ nguyên giá trị, khóa chính giữ nguyên, `search_key` đúng.
   - **Câu tìm thật**: chạy `TelegramSearchPolicy.whereClause` trên JDBC: "su thanh hoa" ra đúng 1 bài, đếm (`COUNT`) bằng số dòng trả về, bài `is_deleted=1` không ra, lọc theo `chat_id` vẫn đúng, dòng `search_key=''` vẫn ra nhờ nhánh dự phòng.
3. **`TelegramLibraryDaoTest`** (bổ sung): tìm không dấu qua `InMemoryTelegramLibraryDao`, phân trang offset/limit với câu tìm.
4. **Đo tốc độ** (`TelegramSearchPerformanceTest`, JDBC): sinh 28.000 bài tổng hợp có dấu (không dùng dữ liệu thật), đo backfill và 20 lần tìm (`getSongs` limit 200 + `COUNT`). Chỉ in số ms; assertion rộng (backfill < 10 s, mỗi lần tìm < 1 s trên Ni) để không flaky.
5. Các test cũ phải còn xanh; mốc mới = 1.132 + số test thêm, 21 skipped, 0 failed ở cả Debug và Release.

## 6. Tốc độ với khoảng 28.000 bài (ước lượng — [CẦN XÁC NHẬN] bằng số đo)

- Tìm: quét toàn bảng 28k dòng, so chuỗi ASCII ngắn ~40–80 ký tự, rồi sắp theo `date` lấy 200 dòng; cộng 1 câu `COUNT`. Ước tính 20–80 ms/lần gõ trên Sony, tương đương hôm nay (hôm nay cũng quét toàn bảng). Chạy trên executor DB, hủy theo `uiQueryGeneration` như hiện tại.
- Dung lượng: thêm ~60 byte/bài ≈ 1,5–2 MB cho 28k bài.
- Nâng cấp lần đầu (1 lần duy nhất): đọc + chuẩn hóa + cập nhật 28k dòng trong 1 giao dịch, ước tính 1–4 s trên Sony, chạy ngoài main thread (DB mở lần đầu trong `executeOnDb`). Trong lúc đó thư viện hiện "đang tải" như bình thường.
- Nếu Sony đo > 150 ms/lần gõ: thêm debounce 200 ms ở ô tìm (việc riêng, không làm trong phiên này).

## 7. Cách kiểm trên Sony (sau gate: test Ni → commit → bundle → build Tiny có Telegram config → verify signer)

1. Sony đang có bản Beta trước (DB v1, đã sync). **Không** `pm clear`, không gỡ app. Ghi lại: tổng số bài ở dòng đồng bộ, số nguồn, số bài playlist, 1–2 bài đã tải.
2. `adb install -r` bản mới. Kiểm app ở tiền cảnh trước mỗi bước, không BACK ở root.
3. Mở app, chờ thư viện hiện. Logcat chỉ lọc tag của app: thấy dòng migration (số dòng, ms) — không có tên bài. Ghi thời gian nâng cấp.
4. So sánh: tổng số bài, số nguồn, playlist, bài đã tải — **phải y nguyên**, không tự sync lại từ đầu.
5. Tìm (gõ bằng `adb shell input text`, chuỗi ASCII): chọn 1 bài có dấu đang có trên Sony (ví dụ "Sử Thanh Hóa" nếu có), gõ bản không dấu → bài đó hiện; gõ có dấu → vẫn hiện; gõ đảo thứ tự từ → vẫn hiện; gõ tên ca sĩ không dấu → ra bài của ca sĩ đó. Làm ở chế độ "Tất cả", một nguồn, Playlist, cả màn Melo điện thoại.
6. Bấm chip thể loại (dùng cùng đường tìm) → vẫn ra kết quả.
7. Đo: log thời gian truy vấn tìm (ms, số kết quả) cho 5 lần gõ; chụp màn hình kết quả.
8. Đóng hẳn app, mở lại → không chạy migration lần 2, tìm vẫn đúng.
9. Khôi phục rotation/density nếu đã đổi.

## 8. Tiêu chí đạt

- Gõ "su thanh hoa" (hoặc bất kỳ cách viết không dấu/hoa thường/đảo từ) ra bài "Sử Thanh Hóa"; tìm được theo tên ca sĩ và tên file; ở mọi chế độ danh sách và màn Melo.
- Nâng cấp từ DB cũ giữ nguyên 100% số bài, nguồn, playlist, bài đã tải; không phải sync lại; migration chỉ chạy 1 lần.
- Mỗi lần tìm ≤ 150 ms trên Sony với thư viện thật; nâng cấp lần đầu ≤ 5 s và không đơ màn hình (không ANR).
- Test Debug + Release xanh (0 failed), `git diff --check` sạch, signer `eecd3df7…ce8`.
- Chủ dự án kiểm lại trên Y700 với ~28.000 bài.

## 9. Ngoài phạm vi
- Tìm trong flavor `personal` / màn hình xe (`NavigationManager`).
- Debounce, gợi ý tìm, sắp xếp theo độ khớp.
