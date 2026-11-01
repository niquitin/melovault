# Phụ lục báo cáo — Chặng 4 (S20–S24): build Tiny + QA Sony

TASK_ID: `2026-11-01-nhieu-playlist-co-ten`. Báo cáo chính: [2026-11-01-nhieu-playlist-co-ten-bao-cao.md](2026-11-01-nhieu-playlist-co-ten-bao-cao.md).

Thực thi: Claude Code Sonnet 5.5 (effort không xác định từ công cụ), phiên Chặng 4, 01/11/2026. Không code tính năng mới, không sửa mã.
Trạng thái: **DỪNG GIỮA CHỪNG — S20, S21 xong; S22 (QA Sony) xong Q1–Q3 và dở Q4; S23 (Drive) và S24 chưa làm. BLOCKED: Sony mất kết nối ADB lúc ≈ 18:59 (+07) và chưa quay lại.** Chưa có PASS chính thức nào; đây không phải nghiệm thu.

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

## 3. S22 — QA Sony (dở)

Phương pháp: script PowerShell trên Tiny (`C:\Bots\_qa_temp\NPL-0efec5a\q.ps1`) chạy `adb` + `uiautomator dump`; mọi lần chạm đều kiểm focus thuộc `app.melovault.player` (hoặc menu/hộp thoại của chính app). Ảnh: `docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/chang4/shots/` (29 PNG, hợp lệ). Chưa ghép `00_tong_hop.png` vì QA chưa xong.

**Chênh lệch so với kế hoạch (ghi thẳng):**
- Tên Playlist thử dùng ASCII **`QA Thu 1`** (không phải `QA Thử 1`), vì `adb input text` không gõ được dấu tiếng Việt. Do đó chưa kiểm được tên có dấu/emoji trên máy (đã có test JVM vòng tròn).
- Thao tác Q4 đổi thứ tự sau Q5 (xem mục 4).

| # | Hành động | Kỳ vọng | Thực tế | Kết quả | Ảnh |
|---|---|---|---|---|---|
| Q1 | Mở app → chip Playlist → mở “Yêu thích” | Chỉ mục “★ Yêu thích · N bài”, “+ Tạo Playlist”; Yêu thích mở ra đúng bài cũ; ☆ ghi “Lưu vào Yêu thích” | Chỉ mục: “★ Yêu thích · 2 bài” + “+ Tạo Playlist”; Yêu thích mở đúng 2 bài (Nothingg Mix, Trôi Kem), nút ★ “Bỏ lưu … khỏi Yêu thích”; dòng bài ở Thư viện có ☆ “Lưu … vào Yêu thích”; trạng thái “Chưa bật đồng bộ” | **PASS** (số “2 bài” khớp danh sách; chưa so với số cũ từ trước nhiệm vụ vì không có ảnh trước) | 02, 03, 04 |
| Q2 | “+ Tạo Playlist” → `QA Thu 1` → ⋮ bài Telegram → “≡ Thêm vào Playlist…” → chọn; thêm thêm 2 bài Telegram + 1 bài máy | Hộp có ✓ đúng; không phát bài; chi tiết 4 bài đúng thứ tự thêm; bài máy có dấu “trên máy” | Hộp tạo có bàn phím và không bị che; toast “Đã thêm '# Mưa Bay Bay.mp3' vào ‘QA Thu 1’.”; ✓ hiện đúng ở Playlist đã chứa bài; không có phát nhạc (Media3 không PLAYING); chi tiết “QA Thu 1 · 4 bài”: Mưa Bay Bay (SƯU TẦM), #140 EM MUON TU DO, Mưa Bay Bay (kho nhạc), Bee Moved (“Trên máy · FLAC”); 4 chip ‹ Playlist / ⤨ Ngẫu nhiên / ▶ Phát hết / ⋮ vừa một hàng | **PASS** | 05–19 |
| Q3 | ⋮ → Sắp xếp → ↑ bài cuối lên đầu → Xong; đóng mở app | Thứ tự mới giữ sau mở lại | Hộp Sắp xếp rõ (↑/↓ cao 126 px = 48 dp; ↑ dòng đầu và ↓ dòng cuối mờ); bấm ↑ 3 lần đưa “Bee Moved” lên đầu; sau HOME + `am kill` + `am start` (PID 21706 → 23759) thứ tự giữ: Bee Moved, Mưa Bay Bay, #140, Mưa Bay Bay | **PASS** | 20–24 |
| Q4 | Hàng đợi → “Lưu Playlist” → `QA Hàng chờ` | Số bài = bài Telegram + máy; báo bỏ qua đúng | Mở màn Đang phát → tab “Tiếp theo” (hàng đợi chỉ 1 bài); chạm “Xem cả hàng đợi” rồi ảnh chụp rỗng (0 byte), `dump` rỗng và ADB rớt (mục 5) | **BLOCKED / DỞ** — chưa thấy chip “≡ Lưu Playlist” | 25–28 |
| Q5 | ▶ Phát `QA Thu 1`; Next 1 lần | Hàng chờ = bài Telegram (3, điều chỉnh Q5); Media3 PLAYING; CHƯA XÁC NHẬN ÂM THANH THỰC | **Chưa làm** | **CHƯA LÀM** | — |
| Q6 | Đổi tên → `QA Thu 2`; Xóa `QA Hàng chờ`; xoay ngang/dọc ở chi tiết | Tên mới; xóa không mất bài trong thư viện; xoay giữ chi tiết/cuộn | **Chưa làm** | **CHƯA LÀM** | — |

Quan sát phụ (không phải lỗi chặn):
- Ở Yêu thích và chi tiết Playlist, nhãn nhỏ dưới thanh chip vẫn ghi “Phạm vi: Playlist”; hàng 4 chip có lề trái ≈ 54 px lớn hơn các hàng khác (thẩm mỹ).
- Ở Thư viện, bài đầu danh sách trùng tên “# Mưa Bay Bay.mp3” nhiều bản (khác kênh) nên khó phân biệt; đã phân biệt bằng kênh/thứ tự.
- Studio Deck dọc: nội dung tab “Tiếp theo” bị cắt gần hết nếu không cuộn trang; cuộn lên mới thấy hàng đợi và “Xem cả hàng đợi” (hành vi có sẵn, không thuộc nhiệm vụ này).

## 4. Sự cố và việc chưa làm sạch (tự báo cáo)

1. **`monkey` khởi chạy ứng dụng lạ.** Lệnh `adb shell monkey -p app.melovault.player -c LAUNCHER 1` lần đầu không đưa MeloVault lên trước mà làm hiện hộp thoại hệ thống “Cho phép truy cập?” (USB/MTP), màn đăng nhập nhanh Google Tin nhắn, rồi Vita3K (`launchedFromPackage=com.android.shell`). Tôi chạm đúng một lần “TỪ CHỐI” trên hộp thoại USB, nhấn HOME, không chạm gì khác trong các ứng dụng đó, rồi chuyển sang `am start -n app.melovault.player/…TelegramPreviewActivity`. Vita3K có thể đã tự cập nhật cơ sở dữ liệu tương thích của nó (nội dung hộp thoại của chính app đó); tôi không đụng vào. Tiến trình Vita3K và Tin nhắn có thể còn nằm trong ứng dụng gần đây.
2. **Vô tình phát nhạc.** Chạm nút ⌃ trên thanh “Nghe tiếp” đã phát `#Bản nháp 1.mp3` (Media3 state=3, vị trí ≈ 27–47 s, có thể nghe được trên loa/tai nghe) rồi tôi nhấn “Dừng” (state=2). Không đổi hàng đợi.
3. **Thiết bị đang để cài đặt xoay.** Tôi đặt `accelerometer_rotation=0`, `user_rotation=0` (khóa dọc) để QA. **Chưa khôi phục** về `accelerometer_rotation=1` vì ADB rớt. Việc cần làm khi Sony kết nối lại: `settings put system user_rotation 0` rồi `settings put system accelerometer_rotation 1`.
4. **`SONY_SCREEN_END` chưa xác minh.** Lần kiểm cuối `Display State=ON`; ADB rớt trước khi tôi tắt màn được. Khi máy quay lại phải tắt (`keyevent 223`) và xác minh `Display State=OFF`.
5. **Dữ liệu thử còn trên Sony:** Playlist `QA Thu 1` (4 bài) trong app (chỉ ở máy; đồng bộ đang “Chưa bật đồng bộ” nên không lên Telegram). Chưa dọn.
6. Không `uninstall`, không `pm clear`, không xóa dữ liệu/cache, không logout, không bật/tắt đồng bộ, không máy bay. Một lần `am kill` đóng tiến trình MeloVault (an toàn, ở nền) để kiểm Q3.

## 5. Điểm dừng — Sony mất ADB

- ≈ 18:57 vẫn thao tác bình thường (Q4 mở “Tiếp theo”, cuộn trang).
- ≈ 18:58 chạm “Xem cả hàng đợi”; chụp màn hình trả file 0 byte và `uiautomator dump` rỗng; ngay sau đó `adb` báo `device 'QV725WEQ54' not found`.
- Một lần `adb devices -l` trên Tiny thấy máy với transport id mới (10239) rồi lại rỗng; thăm dò thêm ≈ 4 phút (nhiều lần cách 12–15 s) vẫn không thấy. Chưa rõ: cáp/USB bị rút, máy khởi động lại, hay ứng dụng bị treo/crash khi mở “Xem cả hàng đợi” (chưa đọc được logcat). **Không kết luận lỗi ứng dụng.**
- Watcher QA trên Tiny đã dừng (`watcher_stop=2026-11-01T19:02:27`); không còn tiến trình do phiên này tạo (trừ daemon Gradle).

## 6. Việc còn lại

1. Chủ dự án kiểm cáp/nguồn Sony và xác nhận máy báo `device` trong `adb devices -l` trên Tiny.
2. Phiên tiếp: kiểm `logcat -b crash`/`pidof` để biết app có crash ở “Xem cả hàng đợi” không; khôi phục xoay; tắt màn; làm Q4 (chip “Lưu Playlist”), Q5, Q6; dọn Playlist thử; ghép `00_tong_hop.png`.
3. S23: upload Drive owner-only **chỉ sau** khi QA xong (APK Debug `5B2FB5CC…03FEF8`) → `*-apk-test.md`, `*-hinh-nghiem-thu.md`. Chưa upload, chưa có link; **không có link Drive nào được tạo ở phiên này**.
4. S24: hoàn chỉnh báo cáo và prompt nghiệm thu sơ bộ.
5. Đồng bộ hai máy (Q7–Q9): **BLOCKED — chưa QA đồng bộ hai máy thật** (chưa có chỉ định (a)/(b) của chủ dự án).
6. Âm thanh thật: **CHƯA XÁC NHẬN ÂM THANH THỰC**.
