# E96-P0-PLAYLIST-DATA-SAFETY — BÁO CÁO THỰC THI (Sonnet 5.5)

**Trạng thái đề xuất: READY FOR QA — chờ ChatGPT nghiệm thu độc lập.** Không phải PASS, không merge, không phát hành.
APK: **QA CANDIDATE — NOT PRODUCTION** (xem mục 7: APK này chưa dùng được để thử chức năng Telegram và không cài đè được lên bản Beta đang có).

## 1. Git

| Mục | Giá trị |
|---|---|
| Baseline | `9ee6f7164763369191f2d7e89e814404fabe8c9a` (đúng dự kiến; cây sạch; không agent khác trên nhánh E95) |
| Nhánh | `dot/E96-P0-PLAYLIST-DATA-SAFETY` |
| Worktree | `D:\Bots\MeloVault-E96-P0-Playlist-Safety` (giữ lại, chứa nhánh + ảnh/log; không xóa) |
| Commit mã nguồn | **`8c83abbd23f2bafe521797bd793801d4d3bbb042`** (APK build từ đúng cây này) |
| Commit báo cáo | commit liền sau, chỉ thêm file này (không đổi mã) |
| Không làm | không merge, không push, không đụng nhánh E95/main, không reset/clean/stash |

Lưu ý quy trình: không có `docs/plans/E96-P0-PLAYLIST-DATA-SAFETY.md` do Opus; đề bài là prompt thực thi của chủ dự án + `ADR_E96_001` + `PLAN_E96_HOP_NHAT_CHATGPT_DRAFT.md`.

## 2. File thay đổi (11 file, +1228 / −80) — không file ngoài phạm vi

Mã production (`app/src/shareable/.../data/telegram/`):

| File | Thay đổi |
|---|---|
| `TelegramSavedPlaylistStore.kt` | Viết lại: `PlaylistLoadResult {Missing, Ok, Unreadable}`, `PlaylistCipher`/`PlaylistFileIo` (để test JVM), `KeystorePlaylistCipher` (cùng alias/định dạng cũ), `TelegramPlaylistPolicy.toggle` trả `Added/Removed/Full` |
| `TelegramPlaylistSession.kt` (mới) | `TelegramPlaylistSession` (nguồn sự thật trong RAM + người ghi duy nhất), `TelegramPlaylistRuntime` (1 luồng ghi toàn tiến trình), `TelegramPlaylistViewPolicy` |
| `TelegramPreviewActivity.kt` | Hai đường Save → một đường `requestPlaylistToggle`; nạp playlist ngoài luồng UI; Playlist tra DB + bài giữ chỗ; chặn phát/xếp hàng bài giữ chỗ; thông báo |
| `TelegramLibraryDao.kt`, `AndroidTelegramLibraryDatabase.kt`, `InMemoryTelegramLibraryDao.kt` | Thêm `getSongsByKeys` (chỉ SELECT, chia lô 400 khóa). **Không đổi schema, không thêm index** |

Test (`app/src/test/.../data/telegram/`): `TelegramPlaylistPolicyTest` (cập nhật), `TelegramSavedPlaylistStoreTest`, `TelegramPlaylistSessionTest`, `TelegramPlaylistViewPolicyTest`, `TelegramSongsByKeysSqlTest` (mới).

## 3. Từng lỗi và cách sửa

**P0-01 — playlist rỗng 28 byte.** Gốc: `require(bytes.size in 29..8192)` (cũ `:58`) trong khi AES-GCM của chuỗi rỗng = IV 12 + tag 16 = **28 byte** → playlist đã lưu rỗng bị coi là lỗi. Sửa: ngưỡng nhỏ nhất `12+16=28`; file rỗng hợp lệ → `Ok(emptyList())`; giữ nguyên khóa/alias/AAD/định dạng nên đọc được dữ liệu cũ. Phân biệt rõ file rỗng hợp lệ với file hỏng (0–27 byte → `Unreadable(SIZE)`). Trần trên nâng 8192 → 64 KB để không coi playlist hợp lệ là hỏng.

**P0-02 — đọc lỗi → rỗng → ghi đè.** Gốc: `runCatching { load }.getOrDefault(emptyList())` (cũ `:5670`) rồi lần Lưu kế tiếp `save()` ghi đè bằng 1 bài. Sửa: `load` trả `Missing / Ok / Unreadable(IO|SIZE|DECRYPT|FORMAT)`; không bao giờ biến lỗi thành danh sách rỗng. `save` đọc file hiện có trước; nếu `Unreadable` thì ném `PlaylistUnreadableException` **không chạm file**; phiên (`TelegramPlaylistSession`) cũng không gọi `save` khi đang bị chặn (thử đọc lại khi bấm Lưu vì lỗi Keystore có thể tạm thời). Thêm: blob mới được giải mã thử lại trước khi thay file; giải mã **không bao giờ tự tạo khóa Keystore mới** (khóa mới sẽ làm file cũ vĩnh viễn không đọc được). Không đổi tên/xóa/thay file lỗi. UI: thông báo + tiêu đề Playlist "KHÔNG ĐỌC ĐƯỢC DỮ LIỆU ĐÃ LƯU".

**P0-03 — mất bài cũ ở 301.** Gốc: `(deduped + targetId).takeLast(300)` và `.take(300)` khi đọc/ghi. Sửa: đủ 300 → từ chối thêm với thông báo "Playlist đã đủ 300 bài…"; vẫn xóa được; không cắt khi load/save (danh sách >300, nếu có, được giữ nguyên; chỉ chặn ở 1000 bằng ngoại lệ, không cắt).

**P0-04 — bài đã lưu bị ẩn.** Gốc: Playlist lấy `songs.firstOrNull` (chỉ 200 bài mới nhất nạp ở `loadDatabaseIndex`). Sửa: Playlist giữ **thứ tự đã lưu**, tra bộ nhớ trước rồi **một lượt** `getSongsByKeys` trên luồng DB cho phần còn thiếu; bài chưa có trong thư viện hiện thành dòng giữ chỗ "Bài đã lưu · chưa có trong thư viện máy này" (ở cuối), chỉ có menu "Xóa khỏi Playlist"; chạm vào chỉ báo, không phát; không đưa vào hàng đợi (lọc ở 2 chỗ dựng hàng đợi). Không bao giờ tự xóa khỏi playlist. Bản sao `songs.toList()` lấy ở luồng gọi nên luồng DB không đọc danh sách đang bị luồng chính sửa.

**P0-05 — hai đường Save.** `toggleCurrentFavorite` và `toggleFavoriteSong` đều gọi `requestPlaylistToggle` → `TelegramPlaylistRuntime` (1 luồng nền, FIFO, toàn tiến trình) → `TelegramPlaylistSession.toggle` (đồng bộ hóa, luôn bắt đầu từ danh sách đã commit mới nhất → bấm liên tiếp không mất cập nhật). I/O Keystore + file không còn chạy trên luồng UI. UI và thông báo thành công chỉ cập nhật **sau khi file đã ghi**; kết quả tới muộn sau khi đổi/đăng xuất tài khoản bị bỏ qua. Nạp ở `identity` cũng chuyển ra luồng nền (trước đây gán `favoriteIds` ngay trên luồng nhận TDLib). Playback/MediaSession/FYT: không sửa; chỉ lọc dòng giữ chỗ khỏi hàng đợi.

## 4. Kết quả kiểm thử (JVM — chạy trên Ni, worktree, JDK 21)

Lệnh (đúng CLAUDE.md): `.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain` → **BUILD SUCCESSFUL**.

| Biến thể | Tests | Passed | **Skipped** | Failed/Errors |
|---|---|---|---|---|
| Debug | 1.388 | 1.367 | 21 (có sẵn từ trước, không do P0) | 0 / 0 |
| Release | 1.388 | 1.367 | 21 | 0 / 0 |

58 test của P0 (cả 2 biến thể, 0 skipped): Policy 8, Store 23, Session 15, ViewPolicy 7, SQL 5. Log: `docs/plans/artifacts/E96-P0-PLAYLIST-DATA-SAFETY/01..04-*`.

12 kiểm thử bắt buộc:

| # | Yêu cầu | Kết quả | Bằng chứng / giới hạn |
|---|---|---|---|
| 1 | Rỗng lưu & đọc lại | **PASS (JVM)** | `emptyPlaylistIsExactly28BytesAndReadsBackAsOkEmpty` (kiểm blob = 28 byte, AES-GCM thật) |
| 2 | File mã hóa lỗi không bị ghi đè | **PASS (JVM)** | `flippedTagByteIsUnreadable…`, `wrongKey…`, `keystoreFailureOnDecrypt…` (so byte-từng-byte), `readErrorBlocksSaveAndNeverCallsSave` |
| 3 | Mất file ≠ file hỏng | **PASS (JVM)** | `neverSavedIsMissing…`, truncated/zero/huge/dir/IO/format tests |
| 4 | 300 bài giữ đủ 300 | **PASS (JVM)** | `exactly300SongsRoundTripWithoutLoss`, `at299…` |
| 5 | Thêm bài 301 không mất bài cũ | **PASS (JVM)** | `at300ANewSongIsRefused…`, `aFullPlaylistRefusesAndKeepsEverything` |
| 6 | Xóa rồi thêm lại | **PASS (JVM)** | `at300RemoveThenAddAgainWorks`, `removeThenAddAgainWorks…` |
| 7 | Save/Unsave liên tục | **PASS (JVM)** ở lớp phiên | 101 lần bấm 1 bài; 2 luồng × 100 bài khác nhau = đúng 200, thứ tự giữ; FIFO của luồng ghi. **Chạm màn hình thật: NOT TESTED** |
| 8 | Bài ngoài 200 bài vẫn hiện | **PASS (logic + SQL)** | 25.000 bài, 300 khóa: `getSongsByKeys` in-memory và SQLite JDBC (≈6 ms trong JVM); nối vào `TelegramPreviewActivity` chỉ được **biên dịch**, chưa chạy trên UI |
| 9 | Thiếu metadata → giữ chỗ | **PASS (logic)** / UI **NOT TESTED** | `aSavedSongOutsideTheNewest200IsStillShown…`, `nothingIsEverDroppedFromTheSavedList` |
| 10 | Đổi tài khoản không lẫn | **PASS (JVM)** | `switchingAccountsNeverMixesPlaylists`, `anotherAccount…`, `anotherAccountsFileCopiedOverIsRejected` (AAD) |
| 11 | Khởi động lại không mất | **PASS (JVM)** | `restartKeepsThePlaylist` (store mới, cùng khóa). Khởi động lại tiến trình thật + Keystore thật: **NOT TESTED** |
| 12 | Không hồi quy UI/playback | **KHÔNG CHỨNG MINH ĐƯỢC TRÊN THIẾT BỊ** | Chỉ có: 1.388 test hồi quy xanh + build xanh + diff không chạm playback. Thiết bị thật **DEFERRED — NOT TESTED**; emulator **BLOCKED** (xem 6) |

Chỉ build thành công / test JVM xanh **không** chứng minh ADB/TDLib/UI/Keystore thật.

## 5. Build

`.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain` → **BUILD SUCCESSFUL**. `git diff --check` sạch (chỉ cảnh báo LF→CRLF của Git, không phải lỗi).

## 6. Thiết bị / emulator

- Sony, Shanling M3, FYT, S25, Y700: **DEFERRED — NOT TESTED**. Không chạy adb tới thiết bị, không bật màn hình. SONY_SCREEN_END / SHANLING_SCREEN_END: không áp dụng (không động tới).
- Emulator: **BLOCKED**. APK chỉ chứa `arm64-v8a`; các AVD sẵn có trên Ni (`VBN_*`, `RC_QA*`) là `x86_64`, và build này không đăng nhập Telegram được (xem 7). Đổi ABI/Gradle nằm ngoài phạm vi. Không khởi động emulator nào.
- Không có ảnh nghiệm thu (không có gì để chụp một cách trung thực).

## 7. Bàn giao APK (QA CANDIDATE — NOT PRODUCTION)

| Mục | Giá trị |
|---|---|
| Loại | Shareable **Debug** (Release cũng build xong: SHA-256 `0228a4d0fcac4709cf24366554ffe3ce7586b3343856478f7102b26f5e12b0c0`, 30.439.982 byte — không bàn giao) |
| Đường dẫn APK QA | `D:\Bots\_handoff\MeloVault\E96-CROSS-DEVICE-SYNC\QA_APK_E96_P0\MeloVault-E96-P0-PLAYLIST-DATA-SAFETY-8c83abb-shareable-debug-vc109-NiSigned-NoTelegramConfig-QA-CANDIDATE.apk` (+ `.sha256`) |
| Bản gốc build | `D:\Bots\MeloVault-E96-P0-Playlist-Safety\app\build\outputs\apk\shareable\debug\app-shareable-debug.apk` |
| SHA-256 | `32674aee01d28d8d72be18da96f2ededb64169a3aa5c8e9541bf24b1ee09daf7` |
| Kích thước | 33.132.580 byte |
| Package / version | `app.melovault.player` / vc **109** / `0.6.88-beta` (**không bump versionCode**) |
| Chứng chỉ ký (SHA-256) | `a9b8cb6b5b0bc0400955b24dbc85801f5e8855356287caca2dcf3bb4e13db280` |
| Telegram config | **false** (`TELEGRAM_APP_API_ID = 0`; Ni không có API ID/hash hợp lệ) |
| Git | nguồn `8c83abb`, nhánh `dot/E96-P0-PLAYLIST-DATA-SAFETY` |
| Đã cài thiết bị? | **Không** |

Cảnh báo cho ChatGPT/chủ dự án:
1. Chữ ký `a9b8cb6b…` (debug key của Ni) **khác** chữ ký Preview đã ghi trong HANDOFF (`eecd3df7…ce8`, build ở Tiny) → cài đè lên Beta đang có sẽ **signature mismatch → DỪNG** (không gỡ cài, không `pm clear`).
2. Telegram config = false → không dùng được để thử đăng nhập/phát/Playlist thật. Muốn QA chức năng: đóng gói Git bundle + SHA256 → build ở Tiny clone cô lập (có Telegram config), verify package/version/cert.
3. versionCode vẫn 109, trùng Beta109 → theo quyết định #13 (HANDOFF) cần bump trước khi đưa lên máy/Drive; tôi không tự bump (không được phép).
Chưa upload Drive, chưa tạo link; không đổi quyền chia sẻ.

## 8. Rủi ro chưa giải quyết

1. **Keystore thật chưa chạy**: JVM dùng AES-256-GCM phần mềm với đúng bố cục (IV 12 + tag 16). Chưa xác nhận trên thiết bị rằng Android Keystore sinh blob rỗng đúng 28 byte và đọc lại được, cũng như file thật hiện có của chủ dự án.
2. **Không có đường khôi phục trong ứng dụng** khi playlist `Unreadable` (đúng yêu cầu: không tự xóa/đổi tên/thay). Hệ quả: Lưu bị chặn cho tới khi đọc lại được; khôi phục có đồng ý của chủ dự án thuộc P1.
3. Hành vi đổi theo yêu cầu: đủ 300 thì từ chối thay vì tự bỏ bài cũ nhất.
4. `song_key` không có index (không đổi schema): mỗi lô khóa quét một lần bảng của tài khoản — 6 ms ở 25.000 dòng trong JVM; **chưa đo trên thiết bị**.
5. UI giữ chỗ/thông báo/tiêu đề mới và nối DB trong Activity chưa được nhìn thấy trên màn hình.
6. Có thể còn sự khác biệt khi bấm Lưu quá sớm lúc vừa đăng nhập: hiện thông báo "Playlist chưa sẵn sàng" (không mất dữ liệu).
7. Chưa làm (đúng phạm vi): nút Lưu một chạm Car Mode (P4), đồng bộ cloud (P1–P4), nâng trần >300.

## 9. Rollback

- Chưa merge → bỏ nhánh `dot/E96-P0-PLAYLIST-DATA-SAFETY` là đủ; hoặc `git revert 8c83abb` trên nhánh.
- Định dạng file/khóa/alias **không đổi** → dữ liệu playlist hiện có đọc được bởi bản cũ lẫn mới; P0 không di trú, không đổi tên, không xóa file. Trường hợp duy nhất bản cũ đọc kém hơn: playlist rỗng 28 byte (chính là lỗi cũ).
- Máy đã cài APK P0 không hạ được về versionCode thấp hơn; quay lại = phát bản có versionCode cao hơn.

## 10. Dọn dẹp

- Không khởi động emulator/dịch vụ/tiến trình nền nào; không ADB tới thiết bị.
- Dữ liệu test dùng `TemporaryFolder` của JUnit (tự xóa) và SQLite trong bộ nhớ.
- Log tạm trong thư mục tạm của shell đã được sao vào `docs/plans/artifacts/E96-P0-PLAYLIST-DATA-SAFETY/` (Git bỏ qua) rồi xóa bản tạm. Giữ nguyên: báo cáo, log, APK QA, worktree, nhánh.
- Gradle daemon (1 tiến trình IDLE, PID 22788, do các lệnh build của phiên này dùng): đã dừng bằng `gradlew --stop`; không còn daemon nào; không có tiến trình emulator.
- Không dùng `reset --hard`, `clean`, `stash`, force push. Không đụng Private Stable, backend, TelegramSecretary, Beta85 gốc, session/cache TDLib; không đọc/copy `local.properties`.

Đã dừng, chờ ChatGPT nghiệm thu.
