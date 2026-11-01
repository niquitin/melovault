# Phụ lục báo cáo — Chặng 4 (S20–S24): build Tiny + QA Sony

TASK_ID: `2026-11-01-nhieu-playlist-co-ten`. Báo cáo chính: [2026-11-01-nhieu-playlist-co-ten-bao-cao.md](2026-11-01-nhieu-playlist-co-ten-bao-cao.md).

Thực thi: Claude Code Sonnet 5.5 (effort không xác định từ công cụ), phiên Chặng 4, 01/11/2026. Không code tính năng mới, không sửa mã.
Trạng thái: **S20–S23 xong; S22 xong Q1–Q6 (đều PASS, Q5 theo điều chỉnh), Q7–Q9 BLOCKED (không có máy thứ hai); S24 hoàn tất bằng bàn giao + prompt sơ bộ. Chưa có PASS chính thức; mọi thứ ở trạng thái QA CANDIDATE — CHƯA NGHIỆM THU.** Phiên đầu (18:30–19:17) dừng vì USB Sony chập chờn; phiên sau (21:39) làm tiếp sau khi chủ dự án cắm lại cáp.

## 1. S20 — bundle và build Tiny (xong)

| Mục | Kết quả |
|---|---|
| Nguồn | Worktree Ni `D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten`, nhánh `dot/2026-11-01-nhieu-playlist-co-ten`, HEAD `0efec5a98eb187f6f85b748bf06bcb77bcec6eda`, cây sạch, `git diff --check` sạch (khớp bàn giao) |
| Bundle | `MeloVault_nhieu-playlist-co-ten_full_0efec5a_20261101.bundle` 13.673.183 byte, SHA-256 `d43922cb8db353a95ecaff5c62918d81a4cdc54a29540536b49bc713fc3fecff`, `git bundle verify` ok trên Ni; **SHA-256 và số byte khớp hai đầu** (Ni `D:\Bots\_transfer\`, Tiny `C:\Bots\_MeloVault_Git_Transfer\`) |
| Clone Tiny (mới, cô lập) | `C:\Bots\MeloVault-NPL-IsolatedQA-20261101-0efec5a`, HEAD `0efec5a…`, `status` 0 dòng |
| Config Telegram | chỉ `Copy-Item` từ `C:\Bots\MeloVault-E99-IsolatedQA\local.properties` (không đọc/in/hash); `git check-ignore` = ignored; `reportTelegramConfig` → `telegram.config.valid=true`, BUILD SUCCESSFUL 16 s |
| `:app:assembleShareableDebug` | BUILD SUCCESSFUL 2m51s (41 tasks) |
| `:app:assembleShareableRelease` | BUILD SUCCESSFUL 3m42s (54 tasks) |
| Sau build | HEAD không đổi, `status` 0 dòng |

Ghi chú: bản này **không** chạy lại test JVM trên Tiny (đã có gate Ni ở Chặng 3: 2683/21 skipped/0/0 mỗi biến thể).

## 2. S21 — gate APK và cài (xong)

| Mục | Debug | Release |
|---|---|---|
| File (Tiny) | `…\app\build\outputs\apk\shareable\debug\app-shareable-debug.apk` | `…\release\app-shareable-release.apk` |
| Byte | 33.773.508 | 30.933.358 |
| SHA-256 | `5B2FB5CC323B486ADFC2F359BF7C9FCC20E104D0D6FDE4DB3D2D3B3BDE03FEF8` | `7C3340E17C60446DAC18AF36F3808AD38FF7A0B073B6515BFA708AB081281810` |
| Package / version | `app.melovault.player`, vc **121** / `0.6.96-beta` | như Debug |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` | như Debug |
| Debuggable | có | không |
| Telegram config | true (không in giá trị) | true |

- Bản đang cài trước đó trên Sony: pull `base.apk` → vc121, SHA-256 `62ED3500…173DB9` (candidate lời nhạc `fd1f943`), cert `eecd3df7…fce8` (trùng; không downgrade, không lệch chữ ký).
- Máy: `QV725WEQ54` = `XQ-AS52` / `Sony` / Android 12, 1080×2520, 420 dpi. Media3 không phát (state=2, tạm dừng).
- Cài: đúng **một** lệnh `adb -s QV725WEQ54 install -r` (APK Debug) → `Success`; SHA-256 bản cài lại (pull `base.apk`) = `5B2FB5CC…03FEF8`, khớp APK vừa build. Không uninstall, không `pm clear`, không `-d`.
- APK Release **chưa cài**, chưa thử trên máy.
- Log: `docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/chang4/` (`s20.log`, `s21-gate.log`, `s21-install.log`; thư mục này git bỏ qua).

## 3. S22 — QA Sony (Q1–Q6 xong; Q7–Q9 BLOCKED)

Phương pháp: script PowerShell trên Tiny (`C:\Bots\_qa_temp\NPL-0efec5a\q.ps1`) chạy `adb` + `uiautomator dump`; mọi lần chạm đều kiểm focus thuộc `app.melovault.player` (hoặc menu/hộp thoại của chính app). Ảnh: `docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/chang4/shots/` (53 PNG hợp lệ) và ảnh tổng hợp `docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/00_tong_hop.png` (1336×1560).

**Chênh lệch so với kế hoạch (ghi thẳng):**
- Tên Playlist thử dùng ASCII **`QA Thu 1`** (không phải `QA Thử 1`), vì `adb input text` không gõ được dấu tiếng Việt. Do đó chưa kiểm được tên có dấu/emoji trên máy (đã có test JVM vòng tròn).
- Thứ tự Q4/Q5 đổi: làm Q5 trước để hàng chờ có nhiều bài rồi mới Q4.

| # | Hành động | Kỳ vọng | Thực tế | Kết quả | Ảnh |
|---|---|---|---|---|---|
| Q1 | Mở app → chip Playlist → mở “Yêu thích” | Chỉ mục “★ Yêu thích · N bài”, “+ Tạo Playlist”; Yêu thích mở ra đúng bài cũ; ☆ ghi “Lưu vào Yêu thích” | Chỉ mục: “★ Yêu thích · 2 bài” + “+ Tạo Playlist”; Yêu thích mở đúng 2 bài (Nothingg Mix, Trôi Kem), nút ★ “Bỏ lưu … khỏi Yêu thích”; dòng bài ở Thư viện có ☆ “Lưu … vào Yêu thích”; trạng thái “Chưa bật đồng bộ” | **PASS** (số “2 bài” khớp danh sách; chưa so với số cũ từ trước nhiệm vụ vì không có ảnh trước) | 02, 03, 04 |
| Q2 | “+ Tạo Playlist” → `QA Thu 1` → ⋮ bài Telegram → “≡ Thêm vào Playlist…” → chọn; thêm thêm 2 bài Telegram + 1 bài máy | Hộp có ✓ đúng; không phát bài; chi tiết 4 bài đúng thứ tự thêm; bài máy có dấu “trên máy” | Hộp tạo có bàn phím và không bị che; toast “Đã thêm '# Mưa Bay Bay.mp3' vào ‘QA Thu 1’.”; ✓ hiện đúng ở Playlist đã chứa bài; không có phát nhạc (Media3 không PLAYING); chi tiết “QA Thu 1 · 4 bài”: Mưa Bay Bay (SƯU TẦM), #140 EM MUON TU DO, Mưa Bay Bay (kho nhạc), Bee Moved (“Trên máy · FLAC”); 4 chip ‹ Playlist / ⤨ Ngẫu nhiên / ▶ Phát hết / ⋮ vừa một hàng | **PASS** | 05–19 |
| Q3 | ⋮ → Sắp xếp → ↑ bài cuối lên đầu → Xong; đóng mở app | Thứ tự mới giữ sau mở lại | Hộp Sắp xếp rõ (↑/↓ cao 126 px = 48 dp; ↑ dòng đầu và ↓ dòng cuối mờ); bấm ↑ 3 lần đưa “Bee Moved” lên đầu; sau HOME + `am kill` + `am start` (PID 21706 → 23759) thứ tự giữ: Bee Moved, Mưa Bay Bay, #140, Mưa Bay Bay | **PASS** | 20–24 |
| Q4 | Hàng đợi → “Lưu Playlist” → tên (ASCII `QA Hang cho`) | Số bài = bài Telegram + máy của hàng chờ; báo bỏ qua đúng | Sau Q5 hàng chờ có 3 bài Telegram. Màn “Xem cả hàng đợi” mở bình thường (“Hàng đợi · 3 bài”, chip “≡ Lưu Playlist”); hộp “Lưu hàng chờ thành Playlist” có bàn phím; toast “Đã lưu 3 bài, bỏ qua 0 bài.”; chỉ mục có “QA Hang cho · 3 bài”; chi tiết đúng 3 bài theo thứ tự hàng chờ | **PASS** (chưa thử hàng chờ có bài bị bỏ qua hoặc > 1000 trên máy; có test JVM) | 30–40 |
| Q5 | ▶ Phát hết trong `QA Thu 1`; Next 1 lần | Hàng chờ = 3 bài Telegram (điều chỉnh Q5); Media3 PLAYING; CHƯA XÁC NHẬN ÂM THANH THỰC | Toast “Bài trên máy không nằm trong hàng chờ khi phát cả Playlist; chạm từng bài để p…”; phát bài Telegram đầu (state=3); Next → bài thứ 2 (`active item id=1`, state=3); “Tiếp theo · tối đa 12 / 3 bài” = Mưa Bay Bay, #140, Mưa Bay Bay đúng thứ tự Playlist (không có Bee Moved); tạm dừng sau đó (state=2) | **PASS** theo điều chỉnh Q5 (không phải 4 bài như kế hoạch gốc); **CHƯA XÁC NHẬN ÂM THANH THỰC** | 30–34 |
| Q6 | Đổi tên → `QA Thu 2`; Xóa `QA Hang cho`; xoay ngang/dọc ở chi tiết | Tên mới; bài vẫn còn trong thư viện; xoay giữ chi tiết | Hộp “Xóa Playlist ‘QA Hang cho’? Bài hát không bị xóa khỏi Telegram hay máy.” (GIỮ LẠI/XÓA); sau XÓA không còn trong chỉ mục; thư viện Telegram vẫn 25.496 bài, nhạc máy 3 bài; đổi tên → “QA Thu 2 · 4 bài”; xoay ngang giữ chi tiết `QA Thu 2` (4 chip vừa hàng); xoay dọc lại về chi tiết như cũ | **PASS** (cuộn không thử vì danh sách ngắn) | 41–49 |

Quan sát phụ (không phải lỗi chặn):
- Ở Yêu thích và chi tiết Playlist, nhãn nhỏ dưới thanh chip vẫn ghi “Phạm vi: Playlist”; hàng 4 chip có lề trái ≈ 54 px lớn hơn các hàng khác (thẩm mỹ).
- Ở Thư viện, bài đầu danh sách trùng tên “# Mưa Bay Bay.mp3” nhiều bản (khác kênh) nên khó phân biệt; đã phân biệt bằng kênh/thứ tự.
- Studio Deck dọc: nội dung tab “Tiếp theo” bị cắt gần hết nếu không cuộn trang; cuộn lên mới thấy hàng đợi và “Xem cả hàng đợi” (hành vi có sẵn, không thuộc nhiệm vụ này).

## 4. Sự cố và việc chưa làm sạch (tự báo cáo)

1. **`monkey` khởi chạy ứng dụng lạ.** Lệnh `adb shell monkey -p app.melovault.player -c LAUNCHER 1` lần đầu không đưa MeloVault lên trước mà làm hiện hộp thoại hệ thống “Cho phép truy cập?” (USB/MTP), màn đăng nhập nhanh Google Tin nhắn, rồi Vita3K (`launchedFromPackage=com.android.shell`). Tôi chạm đúng một lần “TỪ CHỐI” trên hộp thoại USB, nhấn HOME, không chạm gì khác trong các ứng dụng đó, rồi chuyển sang `am start -n app.melovault.player/…TelegramPreviewActivity`. Vita3K có thể đã tự cập nhật cơ sở dữ liệu tương thích của nó (nội dung hộp thoại của chính app đó); tôi không đụng vào. Tiến trình Vita3K và Tin nhắn có thể còn nằm trong ứng dụng gần đây.
2. **Vô tình phát nhạc.** Chạm nút ⌃ trên thanh “Nghe tiếp” đã phát `#Bản nháp 1.mp3` (Media3 state=3, vị trí ≈ 27–47 s, có thể nghe được trên loa/tai nghe) rồi tôi nhấn “Dừng” (state=2). Không đổi hàng đợi.
3. **Cài đặt xoay đã khôi phục.** Tôi đặt khóa dọc để QA; cuối phiên 21:39 chạy `rotreset`: `accelerometer_rotation=1`, `user_rotation=0` (đã kiểm bằng `settings get`).
4. **`SONY_SCREEN_END`: `Display State=OFF`** (`mWakefulness=Dozing`) xác minh sau lệnh tắt cuối phiên 21:39–21:5x. (Trong khoảng ADB rớt 18:58–21:39 màn ở trạng thái `ON`, không tắt được.)
5. **Dữ liệu thử đã dọn bằng UI:** xóa `QA Hang cho` (Q6) và `QA Thu 2` (dọn); chỉ mục còn “★ Yêu thích · 2 bài” + “+ Tạo Playlist”; thư viện Telegram vẫn 25.496 bài, Nhạc trên máy 3 bài. Đồng bộ vẫn “Chưa bật đồng bộ” suốt phiên nên không có gì lên Telegram. Tác dụng phụ còn lại: bài/hàng đợi hiện tại của app giờ là `#140 - EM MUON TU DO` (tạm dừng, 1:43) thay vì `#Bản nháp 1.mp3` trước QA.
6. Không `uninstall`, không `pm clear`, không xóa dữ liệu/cache, không logout, không bật/tắt đồng bộ, không máy bay. Một lần `am kill` đóng tiến trình MeloVault (an toàn, ở nền) để kiểm Q3.

## 5. Điểm dừng — Sony mất ADB

- ≈ 18:57 vẫn thao tác bình thường (Q4 mở “Tiếp theo”, cuộn trang).
- ≈ 18:58 chạm “Xem cả hàng đợi”; chụp màn hình trả file 0 byte và `uiautomator dump` rỗng; ngay sau đó `adb` báo `device 'QV725WEQ54' not found`.
- Một lần `adb devices -l` trên Tiny thấy máy với transport id mới (10239) rồi lại rỗng (và thêm một lần thoáng hiện ≈ 19:04, transport 10556, rồi mất lại; tám lần thử ≈ 15 s/lần sau đó đều không thấy); thăm dò thêm ≈ 4 phút (nhiều lần cách 12–15 s) vẫn không thấy. Chưa rõ: cáp/USB bị rút, máy khởi động lại, hay ứng dụng bị treo/crash khi mở “Xem cả hàng đợi” (chưa đọc được logcat). **Không kết luận lỗi ứng dụng.**
- Watcher QA trên Tiny đã dừng (`watcher_stop=2026-11-01T19:02:27`); không còn tiến trình do phiên này tạo (trừ daemon Gradle).

### 5b. Phiên tiếp theo (19:08–19:17) — vẫn BLOCKED

- Git khớp bàn giao (HEAD `e3d8383`, cây sạch). `adb devices -l` thấy Sony lúc 19:09 nhưng từng lệnh lại báo `device not found`.
- **App không crash:** `uptime` máy 19 ngày (không khởi động lại) và PID `app.melovault.player` vẫn là `23759` như trước sự cố; cửa sổ có focus là `TelegramPreviewActivity`. Nguyên nhân mất kết nối là USB, không phải ứng dụng. (Chưa đọc được `logcat -b crash` và `exit-info` vì lệnh rớt giữa chừng; không có bằng chứng crash nào.)
- **USB tự ngắt/kết nối lại liên tục:** nhật ký 90 giây (`chang4/usbwatch.log`): máy xuất hiện rồi biến mất ≈ 10 lần (có lúc `authorizing`), mỗi lần chỉ vài giây. Thêm cơ chế tự thử lại vào `q.ps1` vẫn không đủ. Lệnh “khôi phục + tắt màn” chỉ một lần ngắn, thử liên tục 3 phút 19 giây (`chang4/restore-burst.log`): **không lần nào hoàn tất** ⇒ xoay vẫn khóa dọc, `SONY_SCREEN_END` vẫn chưa xác minh (lần kiểm cuối `ON`).
- Không thao tác QA thêm trên máy (tránh bấm dở nửa chừng như chọn xóa/xác nhận). Q4–Q6 và dọn Playlist thử vẫn chưa làm.
- Cần chủ dự án: rút/cắm lại hoặc **đổi cáp/cổng USB** (ưu tiên cổng sau của Tiny hoặc cáp dữ liệu khác), kiểm trên điện thoại chế độ USB (Truyền tệp/Không truyền dữ liệu) và hộp thoại “Cho phép gỡ lỗi USB”; xác nhận `adb devices -l` ổn định ≥ 1 phút.

### 5c. Phiên 21:39–21:5x (cáp đã cắm lại, USB ổn định)

- `usbwatch.log` 90 giây: một trạng thái `device` duy nhất, không ngắt. Chẩn đoán trước khi làm tiếp: máy chạy 19 ngày, PID app vẫn `23759`, `logcat -b crash` rỗng, `exit-info` chỉ có lần `am kill` của tôi lúc 18:55 (`USER REQUESTED`) ⇒ **app không crash lúc “Xem cả hàng đợi”; ADB rớt là do USB**.
- Làm tiếp Q5 → Q4 → Q6 → dọn → ảnh tổng hợp → Drive. Mọi thao tác chạm đều qua `q.ps1` có kiểm focus; không dùng `monkey`.

## 6. Việc còn lại

1. **Nghiệm thu sơ bộ** ở phiên mới (prompt trong bàn giao), rồi chủ dự án nghiệm thu chính thức. Chưa có PASS chính thức.
2. Đồng bộ hai máy (Q7–Q9): **BLOCKED — chưa QA đồng bộ hai máy thật** (chưa có chỉ định (a)/(b) của chủ dự án). Đồng bộ Telegram thật cho Playlist có tên chưa chạy.
3. Âm thanh thật: **CHƯA XÁC NHẬN ÂM THANH THỰC**. Tên có dấu/emoji trên máy, Release/`.open`/Personal trên máy, chữ lớn, TalkBack, tablet/Hai khung/Gọn trên máy thật: chưa kiểm.
4. Quyết định treo cho chủ dự án: hàng chờ khi ▶ Phát chỉ gồm bài Telegram (bài máy không vào hàng chờ); công tắc đồng bộ chung; chỗ đặt cờ `NAMED_ENABLED`; sửa lỗi có sẵn SB-1 (lề màn Hàng đợi sát mép — thấy lại ở ảnh 35).
5. Drive: xong (xem `*-apk-test.md`, `*-hinh-nghiem-thu.md`); tải bằng trình duyệt chưa thử.
