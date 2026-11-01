# Nghiệm thu sơ bộ — 2026-10-31-loi-nhac-p2b

## Vòng 1 — 01/11/2026 12:00–12:20, Claude Code (Opus 5.5), phiên mới

**KẾT LUẬN: SƠ BỘ: CẦN SỬA** — 1 lỗi chặn chức năng (B1, xoay màn hình). Phần còn lại không phát hiện lỗi chặn. Trạng thái chính thức vẫn **CHỜ CHỦ DỰ ÁN NGHIỆM THU** (QA CANDIDATE — CHƯA NGHIỆM THU). Không ghi PASS.

| Mục | Giá trị |
|---|---|
| Worktree / nhánh | `D:\Bots\_worktrees\MeloVault-loi-nhac-p2b` / `dot/2026-10-31-loi-nhac-p2b` |
| HEAD lúc kiểm | `e1eac82` (cây sạch, `git diff --check` sạch, không có agent khác đang chạy) |
| Baseline / mã | `94ab6b0` / A `6f274b9`, B `3447310`; APK build từ `8d4284e` (sau `3447310` chỉ đổi hồ sơ) |
| APK | Debug `app.melovault.player` vc121 `0.6.96-beta`, SHA-256 `31609D80389A9E6EAD8F6417719B640FFBAFFEC3D92916083E3611E687821E88`, 33.642.436 byte |

### 1. Đã kiểm (tự chạy trong phiên này)

| # | Kiểm | Kết quả |
|---|---|---|
| G1 | `git log/diff --stat 94ab6b0..HEAD` | 7 commit; 11 file mã + 5 test + 5 hồ sơ. **Không đụng** `app/src/main`, `build.gradle`, manifest, service/Media3/DB/TDLib, package/signer/versionCode. Khớp phạm vi kế hoạch |
| G2 | Đọc `ShareableLyricsController` (request/cancel/deliver), `MeloLyricsPane.setShown/update/start`, `MeloLyricsTabs.setActive/apply`, wiring `MeloStudioDeck`/`MeloTabletScreen`, hook `TelegramPreviewActivity` (`lyricsMetaNow`, `clearLyricsOf` gọi đầu `logoutUser`) | Đúng thiết kế, trừ B1 bên dưới |
| G3 | Log quyền riêng tư: mọi `log(...)` trong provider/controller | Chỉ `stage=…`, `kind=get\|search`, `code=<HTTP>\|io\|err`; không có tên bài/nghệ sĩ/từ khóa. Chỉ gọi `https://lrclib.net`, có connect/read timeout |
| A1 | Drive (connector): APK `1XJAcC1q…` | 33.642.436 byte, owner `ntdung.md@gmail.com`; quyền APK và thư mục candidate: **chỉ 1 owner** |
| A2 | Bản đồng bộ `Z:\MeloVault_QA\…\…-QA-CANDIDATE-debug.apk` (Ni) | SHA-256 khớp `31609D80…1E88`, byte khớp |
| A3 | Sony: `adb devices -l` + getprop trên Tiny | `QV725WEQ54`, `XQ-AS52` / `Sony` |
| A4 | Sony: `pm path` + pull `base.apk` về `C:\Bots\_qa_temp\sobo-loinhac-20261101\` trên Tiny | vc121 `0.6.96-beta`; SHA-256 bản cài **khớp** `31609D80…1E88`, 33.642.436 byte |

Dựa trên bằng chứng cũ (không chạy lại, không có lý do nghi ngờ): gate Ni 2412 test / 21 skipped / 0 lỗi mỗi biến thể; build Tiny + `telegram.config.valid=true`; cert `eecd3df7…fce8`; QA Q1–Q8 và ảnh trong `-bao-cao.md` mục 10, `-hinh-nghiem-thu.md`.

### 2. Lỗi

**B1 — CẦN SỬA (chặn Q8 / S2 "xoay giữ tab"). Giả thuyết trong báo cáo 10.3: XÁC NHẬN bằng đọc mã, và chỉ ra chính xác thứ tự.**

- Tái hiện (theo báo cáo, ảnh `q6-deck-landscape2`, log `stage=lyrics_drop`): mở Deck dọc → tab "Lời nhạc" có bài → xoay ngang → pane kẹt "Đang tìm lời…" mãi.
- Kỳ vọng: pane ở Deck mới hiện lời/thông báo như trước khi xoay. Thực tế: kẹt Loading; trạng thái Loading **không có nút** nào (`MeloLyricsUiPolicy.kt:45`), người dùng chỉ thoát được bằng đổi tab hoặc đổi bài.
- Cơ chế (Activity xử lý xoay bằng `configChanges`, không tạo lại; controller là một đối tượng duy nhất trong `lyricsAccessLazy` của Activity):
  1. `TelegramPreviewActivity.onConfigurationChanged` (`TelegramPreviewActivity.kt:1052`) gọi `studioDeck?.dismiss()` → `Dialog.dismiss()`. `OnDismissListener` của Dialog được Android **post bất đồng bộ** lên main thread, nên `MeloStudioDeck.stop()` (`MeloStudioDeck.kt:144,176`) chưa chạy.
  2. Cùng lượt, `showFullPlayer()` tạo Deck mới → `show()` → `lyricsTabs.setActive(true)` (`MeloStudioDeck.kt:160`) → pane mới `setShown(true)` → `controller.request(...)`: generation N, `current` = yêu cầu mới, `requestedKey` của pane mới = key bài.
  3. Message dismiss chạy → Deck cũ `stop()` → `setActive(false)` → pane cũ `setShown(false)` → `controller.cancel()` (`MeloLyricsPane.kt:130`, `ShareableLyricsController.kt:82`): generation N+1, `current = null`.
  4. Kết quả từ worker về → `deliver` thấy `current == null` → `stage=lyrics_drop` (`ShareableLyricsController.kt:133-141`).
  5. Tick 1 s của pane mới: `key == requestedKey` nên chỉ `highlight`, không hỏi lại (`MeloLyricsPane.kt:142-147`) → kẹt.
- Hệ quả phụ của cùng nguyên nhân: sau bước 3 `current == null` nên `searchManual` cũng bị bỏ qua lặng lẽ (`ShareableLyricsController.kt:90`) cho tới khi có `request` mới.
- Rủi ro liên quan chưa kiểm (cùng cơ chế "một controller, nhiều pane"): pane trong panel Hàng đợi của Hai khung và pane trong Deck cùng hiện/ẩn chồng nhau thì `cancel()` của pane này có thể bỏ kết quả của pane kia.
- Hướng sửa gợi ý cho REWORK (không làm trong sơ bộ): `cancel` chỉ hủy khi generation hiện tại do chính pane đó tạo (ví dụ `request` trả token, `cancel(token)`), hoặc pane đang Loading mà `current` bị xóa thì tự hỏi lại; kèm test JVM cho thứ tự "pane mới request → pane cũ cancel" và thử lại trên Sony cả dọc→ngang và ngang→dọc.

**Không phải lỗi, chủ dự án cần biết (giữ từ báo cáo):** F2 bài Telegram thiếu nghệ sĩ không tự tìm (đúng LD4, phần lớn thư viện hiện tại rơi vào trường hợp này); F3 LRCLIB `/api/search` đôi lúc 503 (app xử lý đúng); F4 Deck dọc phải vuốt mới thấy tab lời.

### 3. Sony

Chỉ lệnh đọc (`devices`, `getprop`, `pm path`, `dumpsys package/media_session/display`, `settings get`, `pull`). **KHÔNG bật màn hình, không chạm UI, không cài, không đổi prefs** (không tái hiện B1 trên máy vì cơ chế đã rõ từ mã và lời nhạc trên máy đang ở trạng thái tắt; bật lại sẽ đổi dữ liệu QA). Nhạc lúc kiểm: tạm dừng (`state=2`). Xoay `1/0`. **`SONY_SCREEN_END`: `Display State=OFF`** (không đổi suốt phiên).

### 4. Chưa kiểm

Như báo cáo 10.5: đường tự động cho bài Telegram có đủ tên + nghệ sĩ khớp LRCLIB; "quay lại bài còn nhớ lựa chọn" và "Bỏ lời đã chọn" trên máy; ngang → dọc; chữ 1.3; TalkBack; Release/`.open`/Personal trên máy; lỗi mạng thật/429/hết hạn cache (chỉ JVM); Y700/S25/FYT. Quyền thư mục `hinh` trên Drive không kiểm lại (báo cáo đã kiểm; thư mục cha chỉ owner). **CHƯA XÁC NHẬN ÂM THANH THỰC.**

### 5. Việc tiếp theo (chủ dự án quyết định)

- Nếu cần xoay màn hình hoạt động trước khi nghiệm thu: `Yêu cầu sửa 2026-10-31-loi-nhac-p2b: B1 — xoay màn khi đang ở tab Lời nhạc của Deck thì pane kẹt "Đang tìm lời…"` → phiên thực thi REWORK mới, rồi build/QA lại và sơ bộ vòng 2.
- Nếu chấp nhận lỗi đã biết: `ĐẠT 2026-10-31-loi-nhac-p2b — 8d4284e / 31609D80…1E88` kèm giới hạn B1.
