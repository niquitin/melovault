# Việc: Số "đã tải" hiện ngay khi mở app (thẻ nguồn Telegram)

Ngày viết: 18/10/2026, trên HEAD `cefafcd` (sau Beta108). Bản dự kiến: **Beta109**, vc109 / `0.6.88-beta`. Chủ dự án đã chọn "có" (18/10/2026) cho câu hỏi "số đã tải hiện ngay khi mở app".
**Chưa sửa code.** Phiên này chỉ viết kế hoạch; phiên sau thực hiện bằng **Sonnet 5.5 · Trung bình · Tự động**.

## 1. Vấn đề
Thẻ Telegram (Beta108) chỉ ghi "25.105 bài · đã tải 67" sau khi chủ dự án mở chip **Đã tải** một lần (không lọc kênh/tìm). Mở app lần đầu chỉ thấy "25.105 bài". Mục tiêu: có số đã tải đúng ngay đầu Thư viện, **không** tốn pin trên máy yếu (Shanling 3GB), **không** làm chậm lúc vào app/bấm bài, **không** số giả.

## 2. Hiện trạng mã (đã đọc)
- Số đã tải = số **nhóm** offline của `TelegramOfflineLibrary.scan` + `groups()` toàn thư viện (cùng số chip "Đã tải (N)"). Quét theo trang 200 bài, resolver `getMessageLocally` mỗi lô 32 → với 25.105 bài là ~126 trang, ~785 lô gọi TDLib. Chỉ xác nhận khi TDLib báo tải xong, file thuộc root chủ, đọc được, size khớp.
- `discoverOffline` (`TelegramPreviewActivity`) chạy trên `offlineExecutor` (đơn luồng) nhưng `publish(...)` ghi thẳng vào trạng thái màn hình (`offlineGroups`, `shownSongs`, `tracks.adapter`, tiêu đề…). Không dùng lại được cho quét nền: phải tách một hàm chỉ đếm, không chạm UI.
- `downloadedLibraryTotal` (Beta108): chỉ gán khi quét toàn thư viện xong; xóa khi đăng xuất/đổi tài khoản; không lưu.
- `cachedSongPaths` (messageId → đường dẫn) được cập nhật ở mọi chỗ tải xong/đã có file, nhưng chỉ là bộ nhớ và không có số "nhóm".
- DB không có cột "đã tải" (và **không đổi schema** nếu không được duyệt).

## 3. Phương án
| | Cách | Ưu | Nhược |
|---|---|---|---|
| A | Tự quét toàn thư viện ở nền sau khi mở app | Số luôn thật | Tốn TDLib/pin mỗi lần mở; quét ~126 trang |
| B | Chỉ lưu số lần quét gần nhất (prefs), hiện ngay khi mở | Tức thì, rẻ | Có thể lệch nếu xóa cache/bị dọn bên ngoài |
| C | Đếm file trên đĩa | Rẻ | Không ra số nhóm, không khớp chip; **loại** |
| **D (đề xuất)** | **B + A giới hạn:** hiện số đã lưu ngay (kèm dấu thời gian trong bộ nhớ), quét nền một lần sau khi sẵn sàng để làm mới, rồi cập nhật dần theo sự kiện tải xong/xóa | Tức thì và cuối cùng luôn thật; quét có điều kiện nên không tốn pin vô ích | Phức tạp hơn A hoặc B riêng lẻ |

Đề xuất **D**, với điều kiện quét nền chạy khi (tất cả): đã đăng nhập xong + thư viện có bài + không đang đồng bộ lớn + không đang mở/đệm bài + không ở chế độ tiết kiệm pin (`PowerManager.isPowerSaveMode`) + app ở tiền cảnh, và trễ ~20 giây sau khi thư viện sẵn sàng. Tối đa một lần quét nền mỗi tiến trình; mỗi lần mở app nếu số đã lưu còn "mới" (< 6 giờ) và không có sự kiện bất thường thì bỏ qua quét (**[CẦN XÁC NHẬN]** ngưỡng 6 giờ).

**Bước 0 (đo trước, bắt buộc):** trên Sony đo thời gian quét toàn thư viện thật (25.105 bài) bằng `Log.w` mốc đầu/cuối (không tên bài). Nếu > 60 giây thì thêm `Thread.sleep` ngắn giữa các trang hoặc chỉ quét khi đang sạc; ghi số đo vào HANDOFF. Không có số đo thì không tự quyết ngưỡng.

## 4. Thiết kế
### 4.1 Logic thuần: `MeloDownloadedCountPolicy.kt` (không import Android)
- `shouldBackgroundScan(state)`: nhận `loggedIn, hasSongs, syncing, playingOrOpening, powerSave, foreground, alreadyScannedThisProcess, storedAgeMs` → boolean (theo điều kiện mục 3). Hằng số: trễ 20 000 ms, tuổi tối đa 6 giờ.
- `applyEvent(current: Int?, event)`: `DOWNLOAD_COMPLETE` tăng 1 **chỉ khi** bài đó chưa có trong tập đã biết (tránh đếm đôi; xem 4.3), `DELETED` giảm 1 không xuống dưới 0; `current == null` thì giữ null (không đoán).
- `displayValue(stored, scanned)`: lần quét xong thắng số đã lưu; số đã lưu chỉ dùng khi cùng `accountId`.

### 4.2 Lưu số đã quét
- SharedPreferences riêng `melovault_library_counts` (không phải DB, không phải prefs giao diện): khóa `downloaded_<accountId>` = số, `downloaded_at_<accountId>` = thời điểm. Không lưu tên bài hay đường dẫn. Xóa khi đăng xuất/đổi tài khoản (cùng chỗ xóa `downloadedLibraryTotal`).
- Đọc lúc khởi động (luồng nền, ngay khi biết `userId`), gán `downloadedLibraryTotal` rồi `scheduleMeloLibraryRefresh()`.

### 4.3 Quét nền không chạm UI
- Hàm mới `scanDownloadedCountInBackground(userId)` trong `TelegramPreviewActivity` (hoặc lớp riêng nếu gọn hơn): dùng `TelegramOfflineLibrary.scan(...)` + `groups(...)`, resolver `resolveOfflineBatch` hiện có (cùng giới hạn 32 lô), `cancelled` = generation riêng + `isDestroyed` + `auth.isReady()` + `authorizedUser == userId` + cờ ra nền/tắt app. `progress` bỏ qua; khi `finished` ghi `downloadedLibraryTotal = groups.size`, lưu prefs, `scheduleMeloLibraryRefresh()`. Chạy trên `offlineExecutor` (đơn luồng) **sau** hàng đợi sẵn có để không chen lấn quét chip Đã tải; nếu người dùng mở chip Đã tải trong lúc đang quét nền thì hủy quét nền (kết quả quét chip vốn đã gán cùng số).
- Sự kiện tải xong/xóa: các chỗ đang ghi `cachedSongPaths[...] = ...` khi tải xong gọi `applyEvent(DOWNLOAD_COMPLETE)` một lần cho bài mới (so với `cachedSongPaths.containsKey` **trước** khi ghi); chỗ xóa tệp/`cachedSongPaths.remove` gọi `DELETED`. Số theo sự kiện chỉ là ước lượng giữa hai lần quét: lần quét kế tiếp ghi đè bằng số thật. Nếu việc đếm theo nhóm (bản trùng tên) làm lệch thì **bỏ phần sự kiện**, chỉ giữ B + A (đơn giản hơn) — quyết sau Bước 0.

### 4.4 Thẻ
Không đổi `MeloSourceCardPolicy`/view: vẫn "N bài · đã tải X"; X lúc đầu là số đã lưu, sau khi quét nền xong là số thật. **[CẦN XÁC NHẬN]** có muốn dấu hiệu "đang cập nhật" trên thẻ trong lúc quét nền không (đề xuất: không, tránh nhiễu; số đúng sau vài chục giây).

## 5. Các bước làm
1. Đầu phiên: kiểm Git theo `CLAUDE.md` (HEAD từ `cefafcd`, cây sạch).
2. Bước 0: đo thời gian quét trên Sony (build nhỏ chỉ thêm `Log.w` hoặc đo qua chip Đã tải có sẵn bằng logcat); ghi số đo.
3. `MeloDownloadedCountPolicy.kt` + `MeloDownloadedCountPolicyTest.kt` (mục 6); chạy test riêng cho xanh.
4. Prefs + đọc lúc khởi động + xóa khi đăng xuất/đổi tài khoản.
5. `scanDownloadedCountInBackground` + điều kiện + hủy; (tuỳ Bước 0) nối sự kiện tải xong/xóa.
6. Test Debug + Release, `assembleShareableDebug/Release`, `git diff --check`. Commit `feat(shareable): downloaded count on app start (stored + guarded background refresh)`.
7. Bump vc109 / `0.6.88-beta`, commit `chore(shareable): bump b109 (0.6.88-beta)`; gate mặc định `CLAUDE.md` (bundle → Tiny → build → signer `eecd3df7…ce8` → QA Sony → Drive `H:\My Drive\MeloVault-Owner-Beta-v0.6.88-b109-Y700-REVIEW`).
8. Cập nhật `HANDOFF.md`, `docs/plans/BACKLOG.md`, commit `docs:`. Dừng.

## 6. Test JVM (`MeloDownloadedCountPolicyTest.kt`, tối thiểu 8)
1. Đủ điều kiện (đã đăng nhập, có bài, rảnh, không tiết kiệm pin, tiền cảnh, chưa quét, số lưu cũ/không có) → quét.
2. Từng điều kiện thiếu một (không đăng nhập / chưa có bài / đang đồng bộ / đang phát hoặc mở bài / tiết kiệm pin / ra nền) → không quét (6 ca).
3. Đã quét trong tiến trình này → không quét lại.
4. Số lưu mới (< 6 giờ) → bỏ quét; cũ (≥ 6 giờ) hoặc không có → quét.
5. `applyEvent`: tăng/giảm đúng, không xuống dưới 0, `null` giữ `null`, bài đã biết không đếm đôi.
6. `displayValue`: lần quét xong thắng số lưu; số lưu của tài khoản khác bị bỏ.
Mốc hiện tại 1.240 → khoảng 1.248+, 21 skipped, 0 failed.

## 7. Kiểm trên Sony (Claude QA, theo mục an toàn Sony trong `CLAUDE.md`)
1. Cài đè b108 → b109. Mở app lần đầu (chưa có số lưu): thẻ "25.105 bài", sau tối đa ~Bước 0 + 20 s có "· đã tải N", N khớp chip "Đã tải (N)" (mở chip để so, không lọc).
2. Đóng hẳn app, mở lại: thẻ có "đã tải N" **ngay** (số đã lưu), không chờ.
3. Trong lúc quét nền: bấm bài, cuộn danh sách, đổi kênh vẫn mượt, không ANR; nhạc không ngắt.
4. Bật tiết kiệm pin: không quét nền (log `Log.w` lý do bỏ quét, không tên bài).
5. Tải thêm một bài (nếu làm sự kiện): số tăng 1 (nhóm mới) hoặc đúng quy tắc; không đếm đôi.
6. Logcat: không `FATAL EXCEPTION`/`ANR in app.melovault.player`. Khôi phục rotation 1/0, density 420, nhạc tạm dừng.

## 8. Tiêu chí đạt
- Test Debug + Release xanh (Ni, Tiny); APK ký `eecd3df7…ce8`, có Telegram config.
- Sony: mục 7 đúng; số đã tải trên thẻ **luôn bằng** số chip "Đã tải" sau khi quét xong; không số giả (số lưu chỉ của đúng tài khoản; xóa khi đăng xuất).
- Không đổi DB/schema, TDLib (ngoài việc gọi `getMessageLocally` đã có), đăng nhập, đồng bộ, phát. Số đo thời gian quét + tác động pin ghi vào HANDOFF.
- Còn mở **[CẦN XÁC NHẬN]**: ngưỡng 6 giờ và trễ 20 s; Shanling thật (pin, thời gian quét trên 3GB); có cần "đang cập nhật" trên thẻ không; Y700.

## 9. Rủi ro
- Quét nền gọi TDLib nhiều lô → có thể làm chậm `getMessage` lúc bấm bài. Giảm bằng điều kiện "không đang phát/mở bài", trễ 20 s, hủy ngay khi người dùng bấm bài (so với `playStatus()`).
- Số lưu lệch nếu người dùng xóa file ngoài app: sửa ở lần quét kế (≤ 6 giờ). Chấp nhận, ghi rõ.
- Hai quét cùng lúc (chip Đã tải + nền): đã nêu ở 4.3 (hủy nền khi mở chip).
