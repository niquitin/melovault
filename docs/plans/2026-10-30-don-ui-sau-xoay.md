# 2026-10-30-don-ui-sau-xoay — Dọn UI sau xoay (D1–D3)

Thực thi nhỏ, một phiên. Người thực thi: Claude Code (Opus 5.5), 30/10/2026 từ 17:05 +07. Chủ dự án nghiệm thu.

## Baseline

- Worktree `D:\Bots\_worktrees\MeloVault-don-ui-sau-xoay`, nhánh `dot/2026-10-30-don-ui-sau-xoay` tạo từ `805a800` (nhánh `dot/2026-10-30-dao-thu-tu-phat`, đã gồm UI/UX R1c `b599e80` ĐẠT + Đảo `798b254` ĐẠT).
- Đầu phiên (trên worktree Đảo): cây sạch, HEAD `805a800468836b27b0b6c31b26845b5f780ec984`, `git diff --check` sạch; không có Gradle/Java chạy, không file lock; nhánh mới chưa có agent nào.
- Mốc test trước: 2247 passed / 21 skipped / 0 failed (UI/UX R1c) + test Đảo (2287 / 21 / 0 ở `798b254`).
- **Quyết định chủ dự án 30/10/2026 17:01:** bỏ QA1 tablet và QA6 Shanling M3. Mọi QA thiết bị chỉ trên Sony XQ-AS52 `QV725WEQ54` qua Tiny DESKTOP-HNP4GVR. Tablet/Shanling ghi **"ngoài phạm vi theo quyết định chủ dự án"**, không ghi BLOCKED.

## Nguyên nhân tìm được

**D1 — log `MeloAnchor` ra cả Release.** 10 lệnh `Log.w("MeloAnchor", …)` trong `MeloLibraryPane.kt` và 1 lệnh (`save_rejected`) trong `TelegramPreviewActivity.saveBrowsePosition` không có điều kiện; Release `minifyEnabled false` nên không bị lược.

**D2 — `save_rejected activeOk=false` sau mở app.** Không phải do pane: `allChatsMode` mặc định `false`. Khi đăng nhập xong, `loadDatabaseIndex` gọi `refreshSongRows()` → `rememberBrowseContext()` kích hoạt key phạm vi lúc đó (`allChatsMode=false`). Ngay sau, callback `onLoaded` gọi `scanAllTelegramAudio()` đặt `allChatsMode = true` **không làm mới danh sách** → `browseKey()` đổi sang Thư viện, `meloBrowse.active` vẫn là key cũ, generation không đổi. Mọi lần lưu vị trí của danh sách đầu bị từ chối cho tới lần `refreshSongRows()` kế (trang đồng bộ đầu). Log R1c: `context gen=2` → 7 dòng `save_rejected genOk=true activeOk=false` → `genOk=false activeOk=true` (gen 3).

**D3 — khoảng trống header sau xoay về dọc.** XML ô 3 R1c: pane bắt đầu y=266 (trước 79) — `headerBox` của `MeloPhoneScreen` (187 px) đã GONE nhưng vẫn giữ chỗ; giữa hàng "Tất cả" (hết 822) và "Phạm vi" (1074) trống 252 px — `sourceCardsHolder` đã GONE nhưng giữ chỗ. Danh sách vì thế từ y=685 xuống 1124. Nguyên nhân: sau xoay, pane mới chưa gập; `setSelectionFromTop(11, …)` làm ListView gọi `onScroll` **trong chính lượt layout** (`layoutChildren` → `invokeOnItemScrollListener`), `headerCollapsed` đổi true và `onHeaderCollapse`/`applySourceCardsVisibility` đặt GONE ngay trong layout → yêu cầu layout bị bỏ, cha không co lại. Không phải inset hay offset neo (neo đúng bài, offset −171 như trước xoay).

## Cách sửa (chỉ UI browse)

| Mục | File | Sửa |
|---|---|---|
| D1 | `MeloLibraryPane.kt`, `TelegramPreviewActivity.kt` | Một hàm `inline` ghi log chỉ khi `BuildConfig.DEBUG`; Debug giữ nguyên log chẩn đoán |
| D2 | `MeloBrowseState.kt` (+ gọi từ `TelegramPreviewActivity.saveBrowsePosition`) | Hàm thuần `savePosition(key, generationOk, …)`: hàng thuộc generation mới nhất mà phạm vi đổi không qua làm mới → kích hoạt key đang hiện (cùng query/sort/floor như `rememberBrowseContext`) rồi lưu, thay vì bỏ |
| D3 | `MeloLibraryPane.kt` | Đổi gập header/thẻ nguồn khi `list.isInLayout` thì `post` sang sau lượt layout (đọc trạng thái mới nhất); ngoài layout giữ như cũ |

Không đổi schema, songKey, TDLib, playback/service, thứ tự Đảo, Playlist/E105, package/signer/versionCode; không đụng `scanAllTelegramAudio`. Không cần sửa ngoài UI browse → không mục nào BLOCKED ở S0.

## Test

- D2: test JVM `MeloBrowseStateTest` mô phỏng mở app (kích hoạt key SAVED_MESSAGES, phạm vi đổi sang LIBRARY cùng generation, lưu vị trí). Chạy đỏ trên luật cũ (trích nguyên trạng vào `savePosition`) trước khi sửa, xanh sau sửa; thêm ca generation cũ vẫn bị từ chối.
- D3: logic phụ thuộc lượt layout thật của View; dự án không có Robolectric và không được đổi build config → **chỉ có bằng chứng thiết bị** (ảnh + XML bounds).
- D1: kiểm chuỗi `MeloAnchor` trong dex của APK Release + logcat Debug vẫn có.

## Tiêu chí (Sony)

- Q1 D2: mở app → cuộn (không tìm/đổi nguồn) → xoay ngang/dọc ×3 → cùng bài đầu; log Debug không còn `save_rejected` sau khi danh sách đầu đã hiện.
- Q2 D3: Thư viện, gõ tìm, cuộn, xoay ×3 → hàng neo lệch ≤ 1 hàng so với trước xoay; chụp trước/sau.
- Q3 hồi quy R1c (Deck → Back rồi xoay; Deck tạm dừng → Bài kế → không còn "Tạm dừng"); Q4 Đảo ⏭ ×2; Q5 lặp Q1–Q2 giao diện Teal; Q6 Release không có `MeloAnchor`.
- Màn Sony chỉ ON khi thao tác, `finally` OFF, ghi `SONY_SCREEN_END`. Media3 PLAYING ≠ âm thanh thật → **CHƯA XÁC NHẬN ÂM THANH THỰC**.

## Rollback

Revert các commit S1–S3 trên nhánh; không có dữ liệu/di trú.
