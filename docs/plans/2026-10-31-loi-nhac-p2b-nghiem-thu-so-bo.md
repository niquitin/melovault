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

## Vòng 2 — 01/11/2026 15:15–15:30, Claude Code (Sonnet 5.5), theo yêu cầu “Sơ bộ vòng 2” của chủ dự án

**KẾT LUẬN: SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN** (cho phạm vi: sửa B1 `fd1f943` và các luồng chính kiểm lại). Trạng thái chính thức vẫn **CHỜ CHỦ DỰ ÁN NGHIỆM THU** (QA CANDIDATE — CHƯA NGHIỆM THU). Không ghi PASS.

> **Lưu ý độc lập:** khác vòng 1 (phiên Opus mới), vòng 2 do **chính phiên Claude Code (Sonnet 5.5) đã viết mã và sửa B1** thực hiện, vì chủ dự án giao “Sơ bộ vòng 2” ngay trong phiên này. CLAUDE.md mục 0 mô tả sơ bộ là phiên mới; ở đây không có góc nhìn độc lập. Để bù: tôi đọc lại mã từ file thật (không dựa trí nhớ), tự chạy kiểm chứng mới trên Sony với ca khó hơn lần REWORK, và ghi mọi giới hạn. Chủ dự án có thể giao phiên mới chạy lại vòng 2 nếu cần độc lập.

| Mục | Giá trị |
|---|---|
| Worktree / nhánh | `D:\Bots\_worktrees\MeloVault-loi-nhac-p2b` / `dot/2026-10-31-loi-nhac-p2b` |
| HEAD lúc kiểm | `7c6f07a` (cây sạch, `git diff --check` sạch); hai tiến trình java đang chạy là daemon Gradle/Kotlin của chính phiên này (khởi động 14:53), không có agent khác |
| Baseline / mã | `94ab6b0` / A `6f274b9`, B `3447310`, sửa B1 `fd1f943`; APK build từ `fd1f943` |
| APK | Debug `app.melovault.player` vc121 `0.6.96-beta`, SHA-256 `62ED3500C171AAF9CCFCEE3EDDB8D7A11F3D08B65B93DA425A3790126B173DB9`, 33.642.436 byte |

### 1. Đã kiểm (tự chạy trong vòng này)

| # | Kiểm | Kết quả |
|---|---|---|
| G1 | `git log/diff --stat 94ab6b0..HEAD` | 16 file mã/test (11 mã + 5 test), +2688/−9. Mọi file mã nằm trong nhóm lời nhạc + `MeloScreenHost`, `MeloStudioDeck`, `MeloTabletScreen`, `MeloUiLabActivity`, `TelegramPreviewActivity`; **không đụng** `app/src/main`, `build.gradle`, manifest, service/Media3/DB/TDLib, package/signer/versionCode |
| G2 | Đọc lại `ShareableLyricsController` (`request(…, client)`, `cancel(client)`, `deliver`, `searchManual`), `MeloLyricsPane` (`setShown`, `update`, `start`, khóa Loading 40 giây), `onConfigurationChanged`/`showFullPlayer` ở `TelegramPreviewActivity` (dòng 1041–1063, 4071–4078) | Sửa đúng nguyên nhân B1 ở vòng 1: Deck mới `request` trước, Deck cũ `cancel(this)` sau → `current.client !== client` nên không làm gì. Đường thường (Deck cũ đóng trước) vẫn hủy được yêu cầu của chính nó. `searchManual` không còn bị bỏ vì `current` giữ nguyên. `clearOwner` vẫn `cancel()` tất cả. Không thấy lỗi mới |
| G3 | Log quyền riêng tư | Chỉ `stage=…`, `kind=get\|search`, `code=…`; `lyrics-log.txt` của vòng này tìm 7 từ (tên bài/nghệ sĩ đã dùng): **0 lần** |
| A1 | Drive (connector): APK `1VlIW8XO…` | 33.642.436 byte; owner `ntdung.md@gmail.com`; quyền APK, thư mục candidate, thư mục `hinh` và ảnh tổng hợp: **chỉ 1 owner** |
| A2 | Bản đồng bộ `Z:\MeloVault_QA\…fd1f943…\…debug.apk` (Ni) và APK trong clone Tiny | SHA-256 **cùng** `62ED3500…3DB9`, 33.642.436 byte |
| A3 | Sony `QV725WEQ54` (Tiny) | `XQ-AS52` / `Sony`; `pm path` + pull `base.apk`: vc121 `0.6.96-beta`, SHA-256 bản cài **khớp** `62ED3500…3DB9`, cert `eecd3df7…fce8` |
| S1 | **Q8 với request mạng đang bay (ca khó hơn REWORK):** bài trên máy chưa tra lời (“Bee Moved — Blue Monday FM”), mở Deck ở tab Lời nhạc rồi xoay ngang trong khoảng 0,5 giây | Log: `15:24:33.538` request #1 → xoay `15:24:34.084` → `34.772` trả lời #1 (HTTP 503) → `34.773` request #2 của Deck mới → `34.775` **`lyrics_drop` (chính là câu trả lời cũ của Deck cũ, bị bỏ đúng)** → `35.625` trả lời #2 (200) → UI ngang hiện “Bài không lời (nhạc cụ).”. Xoay về dọc: `lyrics_cache hit=true`, không `lyrics_drop` nào thêm; Deck dọc hiện cùng kết quả. **Không kẹt.** Ảnh `s2-inflight-landscape`, `s2-inflight-portrait`, `s2-portrait-pane` |
| S2 | Q7: công tắc Cài đặt TẮT → mở Deck | “Lời nhạc đang tắt.” + “Bật lời nhạc”; **0 request** trong lúc tắt. Bật lại công tắc: “BẬT”. Ảnh `s2-settings-off`, `s2-deck-off`, `s2-settings-on-restored` |
| S3 | Sự cố | `FATAL EXCEPTION`/ANR của app: 0 |

Dựa trên bằng chứng cũ (không chạy lại, không có lý do nghi ngờ): gate Ni 2419 test / 21 skipped / 0 lỗi mỗi biến thể và assemble Debug/Release/open/Personal (báo cáo mục 11.3; `diff --check` chạy lại ở G1); build Tiny `telegram.config.valid=true`; Q1–Q6 của chặng C (không đổi mã ngoài B1).

### 2. Lỗi

**Không phát hiện lỗi chặn.** Quan sát cho chủ dự án (không chặn):

- **Hai pane cùng sống** (panel Hàng đợi của Hai khung mở + Deck mở): request của pane sau thay `current` của pane trước; pane trước nếu đang `Loading` sẽ chờ tới khóa 40 giây mới hỏi lại. Chỉ gặp khi cả hai cùng hiện và cùng tra một bài; hậu quả là một pane chậm tới 40 giây, không kẹt vĩnh viễn. Chưa kiểm trên máy.
- Trạng thái `Loading` vẫn không có nút (như vòng 1); với khóa 40 giây không còn “mãi mãi”.
- F2/F3/F4 của báo cáo 10.3 không đổi (bài Telegram thiếu nghệ sĩ không tự tìm; LRCLIB `search`/`get` đôi lúc 503 — ngay lần thử này request đầu của “Bee Moved” cũng nhận 503 và app xử lý đúng; thanh tab nằm dưới ảnh bìa lớn ở Deck dọc).
- “Bee Moved” được LRCLIB trả là `instrumental` nên hiện “Bài không lời (nhạc cụ)” — đúng LD6 (chỉ khi bản ghi nói vậy).

### 3. Sony (thay đổi trạng thái do vòng này, ghi đúng)

Lệnh đọc + thao tác UI an toàn; không cài, không uninstall/`pm clear`/logout/máy bay. Chạm bài “Bee Moved” đã **phát ~3 giây** rồi tạm dừng (`keyevent 127`); **bài hiện tại giờ là “Bee Moved” (nhạc trên máy) tạm dừng 0:02**; cache lời có thêm mục “instrumental” cho bài này. Công tắc Lời nhạc đang **BẬT** (như trước vòng này). Xoay đã khôi phục `accelerometer_rotation=1`, `user_rotation=0`, `1080×2520`/420, font 1.0. **`SONY_SCREEN_END`: `Display State=OFF`.** Bằng chứng: Tiny `C:\Bots\_qa_temp\sobo2-loinhac-20261101\` (`base.apk`, `lyrics-log.txt`), Ni `docs/plans/artifacts/2026-10-31-loi-nhac-p2b/S2-20261101/` (bị `.gitignore`).

### 4. Chưa kiểm

Âm thanh thật (**CHƯA XÁC NHẬN ÂM THANH THỰC**); lời tự động cho bài Telegram có tên + nghệ sĩ khớp LRCLIB (tài khoản không có bài như vậy); “quay lại bài còn nhớ lựa chọn” và “Bỏ lời đã chọn” trên máy; chữ lớn 1.3; TalkBack; Release/`.open`/Personal trên máy; hai pane cùng sống trên máy; lỗi mạng thật/429/hết hạn cache (chỉ JVM); Y700/S25/FYT.

### 5. Việc tiếp theo (chủ dự án quyết định)

- Dùng thử bản `fd1f943` theo các thao tác dưới đây, rồi `ĐẠT 2026-10-31-loi-nhac-p2b — fd1f943/62ED3500` hoặc `Yêu cầu sửa 2026-10-31-loi-nhac-p2b: <lỗi>`.
- Nếu muốn sơ bộ độc lập: mở phiên mới chạy lại vòng 2 theo prompt trong `-ban-giao.md`.

**3–6 thao tác để chủ dự án thử (Sony hoặc máy của anh, cài APK `62ED3500…3DB9`):**
1. Mở một bài (bài có thẻ nhạc như nhạc trên máy), mở “Đang phát”, vuốt xuống tới tab “Tiếp theo | ♪ Lời nhạc”, chọn “Lời nhạc”: lần đầu hiện hộp đồng ý; chọn “Bật”.
2. Với bài thiếu nghệ sĩ (hầu hết bài Telegram): thấy “Thiếu tên bài hoặc nghệ sĩ…”; bấm “Tìm lời”, gõ tên bài, chọn một kết quả: lời hiện, câu đang hát được tô.
3. Tua +60 giây, rồi cuộn tay: câu tô nhảy theo; cuộn tay thì có nút “Về dòng đang hát”.
4. **Xoay màn hình** khi đang ở tab Lời nhạc (dọc ↔ ngang, vài lần): pane phải hiện nội dung, không kẹt “Đang tìm lời…”.
5. Cài đặt → “Lời nhạc từ LRCLIB”: bật/tắt; khi tắt, tab báo “Lời nhạc đang tắt” và không gửi gì.
6. (Hai khung) bấm “☰ Hàng đợi” → tab “♪ Lời nhạc” ở cột phải.
