# Kế hoạch: Đổi kênh nhanh ở Hai khung + dòng "Các kênh chưa có nhạc (N)"

> Phiên lập kế hoạch: 18/10/2026, branch `feature/e93-03d-y700-offline-cockpit`, HEAD `52cb89d`. Chưa sửa code. **Không đổi schema DB** (DB vẫn v2), không đụng Car (`MeloCarScreen`), Gọn (`MeloCompactScreen`), Thư viện "Cũ", flavor `personal`.
> Chủ dự án đã chọn (18/10/2026): (1) Khi mở một kênh ở Hai khung, khung trái thành **danh sách kênh kèm số bài + thanh phát nhỏ ở dưới**; về lưới kênh/Thư viện thì trình phát lớn trở lại. (2) Dòng "Các kênh chưa có nhạc (N)" mặc định thu gọn, bấm thì **mở ra danh sách tên kênh 0 bài** (bấm vẫn mở kênh được); bật "Ẩn kênh 0 bài" thì ẩn luôn cả dòng.

## 1. Bối cảnh — hiện tại
- Đặc tả `docs/ui-spec.md:210-212` (Y700 ngang – Nguồn Telegram): lưới 3 cột thẻ kênh; dòng thu gọn "Các kênh chưa có nhạc" ở cuối; khi đang mở một kênh, cột trái chuyển thành danh sách kênh kèm số bài để đổi kênh không cần quay lại. `:240`: Car bỏ dòng "Các kênh chưa có nhạc" (Car là màn riêng, không đụng).
- `MeloTabletScreen` (Hai khung): cột điều hướng 80dp + khung trình phát 40% (`buildPlayerPane`) + `MeloLibraryPane(TWO_PANE)` 60%. BACK khi đang mở kênh → `host.showSources()` (`backCallback`).
- `MeloLibraryPane.renderSources` (dùng chung Điện thoại/Tablet dọc/Hai khung): thẻ "Kho tổng hợp", chip "Ẩn kênh 0 bài"/"Quét lại", lưới thẻ; thẻ kênh 0 bài hiện xen giữa với chữ "Chưa có nhạc" (`sourceCard`).
- Activity: danh sách nguồn chỉ được nạp khi đang xem lưới (`refreshSongRows` nhánh `isBrowseSources`, `TelegramPreviewActivity.kt:3609-3659`): `db.getSources` → `distinctSourcesByChatId` (xếp số bài giảm dần) → lọc `hideZeroSources` → lọc theo ô tìm → `visibleSourceCounts`. Khi mở một kênh, `visibleSourceCounts` giữ giá trị cũ (có thể đã lọc theo ô tìm, hoặc rỗng nếu app mở thẳng vào kênh) → **không dùng được làm danh sách đổi kênh**.
- `loadDatabaseIndex` (`:5211`) và `reloadIndexedSourcesFromDb` (`:5253`) đã gọi `db.getSources` trên executor sau đăng nhập/đồng bộ → chỗ cập nhật bộ nhớ đệm danh sách kênh.
- `host.openSource(chatId)` có sẵn: đặt `selectedChatId`, xóa ô tìm, bỏ chặn A–Z, `refreshSongRows()`. Sắp xếp (Mới nhất/Tên A–Z) được giữ.

## 2. Thiết kế
**A. Dòng "Các kênh chưa có nhạc (N)"** (Điện thoại, Tablet dọc, Hai khung — tức mọi nơi dùng `MeloLibraryPane`)
- Lưới chỉ gồm kênh có `count > 0`. Kênh `count == 0` gom vào một dòng cuối lưới: "▸ Các kênh chưa có nhạc (N)", N = số kênh 0 bài thật (không hardcode).
- Mặc định thu gọn; bấm → "▾ …" và hiện danh sách tên (mỗi dòng cao ≥ `minTouch`, chữ phụ, bấm → `host.openSource(chatId)`); bấm lại → thu gọn. Trạng thái mở/gọn chỉ giữ trong RAM của pane (không lưu prefs).
- N = 0 → không có dòng. "Ẩn kênh 0 bài" bật → activity đã lọc bỏ kênh 0 bài nên N = 0 → dòng ẩn (đúng lựa chọn của chủ dự án).
- Đang gõ tìm nguồn: dòng vẫn tính trên kết quả lọc (N = số kênh 0 bài khớp từ tìm).
- Logic thuần trong `MeloLibraryPolicy`: `splitSources(sources): Pair<List<SourceRow>, List<SourceRow>>` (bỏ `chatId == 0L`, giữ thứ tự), `emptySourcesLabel(n, expanded): String?` (null khi n = 0).

**B. Đổi kênh nhanh ở khung trái Hai khung**
- Điều kiện (`MeloLibraryPolicy.quickSwitchVisible(layer, snap)`): layer `TWO_PANE` **và** `!showingSources` **và** `mode == ALL` **và** đang mở một kênh cụ thể (`selectedChatId != null`, không phải Saved Messages/không phải Playlist/Đã tải). Ngoài điều kiện → khung trình phát lớn như cũ.
- Khung trái khi bật: (1) hàng đầu "☷ Tất cả kênh" (bấm → `host.showSources()`), (2) `ListView` các kênh có số bài > 0, thứ tự giống lưới (số bài giảm dần), mỗi dòng: tên kênh (1–2 dòng) + "N bài", dòng đang mở tô `accentSoft` + viền `accent`, tự cuộn tới dòng đang mở khi vào; bấm → `host.openSource(chatId)` (bấm lại kênh đang mở: không làm gì), (3) thanh phát nhỏ cố định ở đáy: ảnh 56dp, tên bài 1 dòng, nút ⏮ / ▶❚❚ / ⏭ (≥ `minTouch`), bấm vùng tên → `host.openNowPlaying()`.
- Dùng `ListView` + adapter (không dựng hàng trăm view một lúc). Kênh 0 bài không có trong danh sách đổi nhanh.
- BACK giữ như cũ: đang mở kênh → về lưới kênh (trình phát lớn trở lại).
- Cách hiển thị: `MeloTabletScreen` giữ cả hai view trong một `FrameLayout` ở vị trí khung trái, đổi `visibility` trong `onLibraryChanged()`; `onPlaybackChanged()` cập nhật cả trình phát lớn và thanh nhỏ (dùng lại `MeloComponents`: `iconButton`, `artView`, `text`, `roundedBackground`).

**C. Host (màn Melo không tự truy vấn DB)**
- `MeloScreenHost`: thêm `fun channels(): List<SourceRow>` (danh sách kênh đầy đủ, không lọc theo ô tìm, không gồm mục 0L, đã gộp theo `chatId`) và trường `Snapshot.selectedChatId: Long? = null`.
- Activity: biến `meloChannelRows: List<MeloScreenHost.SourceRow>` (chỉ đọc/ghi trên main thread). Cập nhật trong `runOnUiThread` của: nhánh `isBrowseSources` (từ `distinctSources`, trước lọc tìm), `loadDatabaseIndex`, `reloadIndexedSourcesFromDb` (tính `distinctSourcesByChatId` trên executor, gán trên UI, rồi `scheduleMeloLibraryRefresh()`). Nếu `channels()` được gọi khi rỗng và có DB + đã đăng nhập → xếp một lần nạp trên executor (cờ chống gọi lặp, kiểm `uiQueryGeneration`/account trước khi gán). Đổi tài khoản/đăng xuất → xóa `meloChannelRows`.
- `snapshot()` thêm `selectedChatId = selectedChatId.takeIf { allChatsMode && !playlistMode }`.

## 3. File cần sửa
(Gốc: `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/`.)

| File | Sửa gì |
|---|---|
| `MeloLibraryPolicy.kt` | `splitSources`, `emptySourcesLabel`, `quickSwitchVisible(layer, showingSources, mode, selectedChatId)`, `quickSwitchRows(channels)` (bỏ count ≤ 0, giữ thứ tự), `quickSwitchIndex(rows, selectedChatId)`. |
| `MeloLibraryPane.kt` | `renderSources`: lưới chỉ kênh có bài + dòng "Các kênh chưa có nhạc (N)" thu gọn/mở; biến `emptyExpanded`. |
| `MeloTabletScreen.kt` | Khung trái `FrameLayout` {trình phát lớn, khung đổi kênh = hàng "Tất cả kênh" + `ListView` + thanh phát nhỏ}; đổi hiển thị theo `quickSwitchVisible`; cập nhật thanh nhỏ trong `onPlaybackChanged`. |
| `MeloScreenHost.kt` | `channels()`, `Snapshot.selectedChatId`. |
| `TelegramPreviewActivity.kt` | Chỉ: `meloChannelRows` + cập nhật ở 3 chỗ trên + nạp lười; `channels()`; `selectedChatId` trong snapshot. Không đụng phát nhạc, sync, TDLib, DAO. |
| Test (`app/src/test/.../data/telegram/`) | `MeloLibraryPolicyTest` (bổ sung). |

Không đụng: `TelegramLibraryDao`/`AndroidTelegramLibraryDatabase`/`TelegramLibrarySql` (schema), `songKey`, khóa `(accountId, chatId, messageId)`, `MeloCarScreen`, `MeloCompactScreen`, `MeloPhoneScreen` (chỉ hưởng thay đổi lưới qua pane chung), flavor `personal`, package, signing. Không thêm thư viện/Compose. Nếu `MeloUiPreviewActivity`/`MeloEmulation` có host giả thì thêm `channels()` trả danh sách mẫu tại chỗ (chỉ để biên dịch).

## 4. Các bước làm (phiên thực hiện, Sonnet)
1. Đầu phiên kiểm git theo CLAUDE.md.
2. Policy + test (mục 5.1).
3. Host + activity (`channels()`, `selectedChatId`, bộ nhớ đệm, nạp lười).
4. `MeloLibraryPane`: dòng "Các kênh chưa có nhạc (N)".
5. `MeloTabletScreen`: khung đổi kênh + thanh phát nhỏ.
6. Test Debug + Release, `assembleShareableDebug/Release`, `git diff --check`.
7. Commit `feat(shareable): quick channel switch in two-pane + empty channels row, bump b98` (versionCode 98 / versionName `0.6.77-beta`).
8. Gate: bundle delta + SHA256 → Tiny build có Telegram config → verify signer `eecd3df7…ce8` → QA Sony (mục 6) → upload Drive riêng `MeloVault-Owner-Beta-v0.6.77-b98-Y700-REVIEW`.
9. Cập nhật HANDOFF (bản mới nhất, bug mở mục 6 bỏ 2 ý đã làm) và BACKLOG, commit docs, dừng.

## 5. Test (JVM)
1. **`MeloLibraryPolicyTest`** bổ sung:
   - `splitSources`: bỏ mục 0L; kênh count 0 sang nhóm rỗng; giữ thứ tự gốc; danh sách rỗng; toàn kênh 0 bài; count âm coi như 0.
   - `emptySourcesLabel`: n = 0 → null; n = 3 thu gọn "▸ Các kênh chưa có nhạc (3)"; mở → "▾ …"; định dạng số lớn qua `MeloComponentPolicy.formatCount` (1.234).
   - `quickSwitchVisible`: chỉ true khi TWO_PANE + không xem lưới + mode ALL + có `selectedChatId`; false với PHONE_PORTRAIT/TABLET_PORTRAIT, PLAYLIST, DOWNLOADED, `selectedChatId == null`, đang xem lưới.
   - `quickSwitchRows`/`quickSwitchIndex`: bỏ kênh 0 bài; tìm đúng vị trí kênh đang mở; kênh đang mở không có trong danh sách (ví dụ 0 bài) → -1, không crash.
2. Mốc mới = 1.157 + số test thêm, 21 skipped, 0 failed ở cả Debug và Release. Không có test UI tự động cho view; phần view kiểm trên Sony.

## 6. Cách kiểm trên Sony (sau gate)
1. Không gỡ app, không `pm clear`; `adb install -r` đè b97. Kiểm app ở tiền cảnh trước mỗi bước, không BACK ở root. Ghi tổng số bài, số nguồn, playlist, lựa chọn sắp xếp trước/sau — phải y nguyên.
2. Dọc (Điện thoại), tab Telegram, "Hiện kênh 0 bài": cuối lưới có "▸ Các kênh chưa có nhạc (N)", N khớp số thẻ "Chưa có nhạc" ở b97 (ghi số trước khi cài). Lưới không còn thẻ 0 bài. Bấm → mở danh sách tên; bấm một tên → mở kênh (danh sách trống, không crash); BACK → lưới. Bấm lại dòng → thu gọn.
3. Bật "Ẩn kênh 0 bài" → dòng biến mất; tắt → hiện lại. Gõ tìm nguồn → N đổi theo kết quả.
4. Xoay ngang. [CẦN XÁC NHẬN] Sony XQ-AS52 ngang có ≥ 600dp để vào Hai khung (theo quyết định 12 thì có); nếu không vào được Hai khung thì ghi lại, bỏ bước 5–8, để chủ dự án kiểm Y700.
5. Hai khung, tab Telegram: khung trái là trình phát lớn. Bấm một thẻ kênh → khung trái thành danh sách kênh, kênh đang mở được tô và nằm trong tầm nhìn, số bài khớp thẻ; đáy có thanh phát nhỏ.
6. Phát một bài, rồi bấm 5 kênh khác nhau liên tiếp trong khung trái: khung phải đổi đúng kênh (tiêu đề + số bài), nhạc vẫn chạy không ngắt, thanh nhỏ cập nhật tên/nút ▶❚❚; ⏭ sang bài kế; bấm vùng tên → Đang phát.
7. Bật Tên A–Z rồi đổi kênh → vẫn Tên A–Z, thanh chữ hoạt động trong kênh mới.
8. BACK → lưới kênh + trình phát lớn trở lại. Nút Thư viện ở cột điều hướng → Thư viện, trình phát lớn. Playlist/Đã tải → không có khung đổi kênh.
9. Đóng hẳn app, mở lại; không ANR/crash (`adb logcat` lọc `AndroidRuntime`, `ANR`). Chụp màn hình bước 2, 5, 6 (ảnh giữ local trên Tiny). Khôi phục rotation/density.

## 7. Tiêu chí đạt
- Lưới kênh (Điện thoại/Tablet/Hai khung) không còn thẻ 0 bài; có dòng "Các kênh chưa có nhạc (N)" thu gọn với N thật, mở ra được, mở kênh từ đó được; "Ẩn kênh 0 bài" ẩn cả dòng.
- Hai khung khi mở kênh: khung trái là danh sách kênh có số bài + thanh phát nhỏ; đổi kênh bằng một chạm, không quay về lưới, nhạc không ngắt; về lưới/Thư viện thì trình phát lớn trở lại.
- Không đổi gì ở Car, Gọn, Thư viện "Cũ", Playlist, Đã tải. Số bài/nguồn/playlist y nguyên; DB vẫn version 2.
- Test Debug + Release 0 failed, `git diff --check` sạch, signer `eecd3df7…ce8`. Không ANR/crash trên Sony.
- Chủ dự án kiểm trên Y700 ngang (Hai khung) và S25 xoay ngang.

## 8. Ngoài phạm vi
- Chip Mixset theo thời lượng (việc khác trong BACKLOG), EQ.
- Đổi kênh nhanh ở Điện thoại/Tablet dọc (đặc tả chỉ yêu cầu Hai khung).
- Đổi thứ tự kênh, ghim kênh, ảnh đại diện kênh trên thẻ, chấm "đã đồng bộ", số bài đã tải theo kênh (cần thêm truy vấn — làm riêng nếu chủ dự án muốn).
- Car, Gọn, Thư viện "Cũ", flavor `personal`, schema DB.
