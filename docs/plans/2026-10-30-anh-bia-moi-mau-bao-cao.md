# Báo cáo thực thi — 2026-10-30-anh-bia-moi-mau (P2-A)

Claude Code (claude-sonnet-5), một phiên, 30/10/2026 ~18:30–20:35 +07. Thiết bị: Ni (repo, Gradle, bundle), Tiny DESKTOP-HNP4GVR (build cô lập + Sony). Trạng thái: **QA CANDIDATE — CHƯA NGHIỆM THU**.

**Quyết định chủ dự án 30/10 17:01** (kế thừa từ `2026-10-30-don-ui-sau-xoay`): chỉ QA trên Sony XQ-AS52 `QV725WEQ54` qua Tiny; tablet và Shanling ngoài phạm vi nhiệm vụ này.

## 1. Kết quả chính

| Mục | Kết quả | Bằng chứng |
|---|---|---|
| rowArtLayer tách khỏi theme | **Đạt** | Unit test 2 bộ mới (CURRENT/TEAL × Phone/Tablet/Compact/Car); production: ảnh hiện ở Current lẫn Teal trên Sony |
| rowArtSwitch là cổng duy nhất cho request | **Đạt** | `rowArtActive(layerShown, switchOn)`; logcat `MeloRowArt` 0 dòng khi tắt, có dòng `sent=` tăng khi bật lại |
| MeloComponents dùng rowArtView khi có artKey | **Đạt** | Bỏ điều kiện `(teal \|\| isCompact)`, chỉ còn `artKey != null` |
| Test Ni (S3) | Debug **2290 passed / 21 skipped / 0 failed**; Release giống hệt | 2 file XML, BUILD SUCCESSFUL cả hai biến thể |
| QA Sony Q1–Q4, Q6 | **Đạt** | Mục 4 |
| QA Sony Q5 (hồi quy) | Q5a **Đạt**; Q5b **chưa kiểm chứng được** (xem mục 6) | Mục 4 |
| Drive owner-only | Xong, đã xác minh qua connector | [apk-test](2026-10-30-anh-bia-moi-mau-apk-test.md) · [hình](2026-10-30-anh-bia-moi-mau-hinh-nghiem-thu.md) |

## 2. Commit

| Bước | Commit | Nội dung |
|---|---|---|
| S0 | `10aebc6` | Kế hoạch + file bàn giao |
| S1 | `bfb3026` | `MeloThemePolicy.rowArtLayer` bỏ phụ thuộc theme cho Phone/Tablet (giữ Compact=true/Car=false); `rowArtActive(theme,switchOn)` chết → `rowArtActive(layerShown,switchOn)`; +test |
| S2 | `92e3db9` | `MeloLibraryPane`: `rowArtEnabled` thay `c.teal` cho listener/request/peek/apply ảnh bìa (giữ `c.teal` cho màu/tab); `MeloComponents.songRow`: bỏ `(teal \|\| isCompact)`, chỉ xét `artKey`; `TelegramPreviewActivity`: đổi tên `rowArtThemeActive`→`rowArtLayerShown`, `rowArtOn()` gọi `MeloThemePolicy.rowArtActive`, sửa 2 comment lỗi thời |

Production `b6f61a0..92e3db9`: 4 file, +18/−19 (`MeloComponents.kt`, `MeloLibraryPane.kt`, `MeloThemePolicy.kt`, `TelegramPreviewActivity.kt`). Test: `MeloThemePolicyTest.kt` +8 dòng ròng (đổi 3 test cũ → 4 test mới). Không đổi schema, songKey, TDLib, playback/service, Đảo, Playlist/E105, package/signer/versionCode, build config; không đụng `MeloRowArtPolicy.kt`/`MeloRowArtLoader.kt`.

## 3. Vì sao hoạt động (đối chiếu code, không chỉ đoán)

- `rowArtLayer` cũ: `appliesTo(theme,...) || profile==COMPACT`; `appliesTo` chỉ true khi `theme==TEAL_BRIGHT`. Mới: `(layer!=null && (PHONE||TABLET)) || profile==COMPACT` — bỏ hẳn tham số `theme` khỏi điều kiện Phone/Tablet.
- `TelegramPreviewActivity` đã gán `rowArtLayerShown = MeloThemePolicy.rowArtLayer(theme, layer, profile)` ở `showNewLayer`/`showCompactLayer` từ trước (không đổi dòng này) — chỉ cần sửa định nghĩa hàm là dây chuyền tự đúng cho CURRENT theme, không cần sửa thêm ở activity ngoài đổi tên biến + nối `rowArtActive`.
- `MeloLibraryPane` có 12 chỗ `c.teal`; chỉ 7 chỗ thực sự là ảnh bìa (listener init/detach, `requestVisibleArt` lúc scroll ×2, request sau refresh, chọn ảnh/`artKey` cho row) đổi sang `rowArtEnabled`; 5 chỗ còn lại (onAccent, nút lọc teal-only, dividerHeight, sortChip ẩn, alphabet bar themed, renderChips/needsSortSwitch, khoảng đệm Save) giữ nguyên `c.teal` vì là lựa chọn màu/tab, không phải cổng ảnh.
- `MeloComponents.songRow`: nhánh teal Phone/Tablet đã tách riêng qua `tealSongRow` (dùng `if (artKey!=null) rowArtView else artView` không điều kiện theme sẵn). Nhánh dùng chung (CURRENT Phone/Tablet + Compact + Car) còn giữ `(teal || isCompact) && artKey!=null` — khi `teal=false` (đúng trường hợp CURRENT) điều kiện luôn false dù `artKey` đã có, chặn `rowArtView`. Bỏ `(teal||isCompact)`, dùng thẳng `artKey!=null` (khớp hệt nhánh teal).

## 4. QA Sony `QV725WEQ54` (XQ-AS52, Android 12) — APK Debug SHA `7F51A1B4…38214A9`

| # | Thao tác | Kỳ vọng | Thực tế | KQ | Bằng chứng (Tiny `C:\Bots\_qa_temp\anhbia-92e3db9-20261030\`) |
|---|---|---|---|---|---|
| Q1 | Mở nguội (theme Hiện tại mặc định), dọc + xoay ngang | Ảnh bìa hiện trên dòng, cả dọc lẫn ngang (Hai khung) | Dọc: ảnh thật trên mọi dòng hiện (trước đây chỉ ô chữ cái). Ngang: tự động vào layout Hai khung (khung phát trái + thư viện phải), ảnh vẫn hiện | **ĐẠT** | `qa1-01-current-port.png`, `qa1-02-current-land.png` |
| Q2 | Cài đặt → Giao diện → Teal sáng, dọc + ngang, rồi trả về Hiện tại | Ảnh bìa hiện, bố cục card teal không vỡ, tên 2 dòng | Cả hai hướng: ảnh thật, card bo góc, tiêu đề 2 dòng; theme trả về "Hiện tại" đúng sau khi test | **ĐẠT** | `qa2-01-teal-port.png`, `qa2-02-teal-land.png` |
| Q3 | Cuộn nhanh ~200 dòng, đổi phạm vi Đã tải, tìm "nhạc", xoay ×3 | Không ảnh sai bài; hàng neo giữ qua 3 lần xoay | Mỗi dòng ảnh khác nhau khớp đúng bài (ảnh phim, selfie, poster, logo kênh — không trùng/lặp sai); `Đã tải` hiện đúng 68 bài khác tập; tìm "nhạc" lọc đúng; neo giữ nguyên **3/3 lần xoay** (digest hàng đầu không đổi) | **ĐẠT** (xem giới hạn mục 6 về kiểm từng-pixel) | `qa3-01-fast-scroll-200.png`, `qa3b-02-datai-scope.png`, `qa3b-03-search-nhac.png` |
| Q4 | Cài đặt → tắt "Tải ảnh bìa từ Telegram" → cuộn sâu ~15 dòng mới → bật lại → cuộn sâu tiếp | Tắt: không request mới, ảnh cũ giữ, dòng mới chỉ ô chữ cái. Bật: tải tiếp | Tắt: dump `adb logcat -d` sau khi `clear` → **0 dòng `MeloRowArt`** (không gửi `getMessage`/decode nào); ảnh chụp mọi dòng mới chỉ có ô chữ cái "M". Bật lại + cuộn sâu hơn: **10 dòng `MeloRowArt`**, `sent` tăng 160→212, ảnh mới xuất hiện đúng | **ĐẠT** | `qa4e-01-off-deep.png`, `qa4e-02-resumed-deep.png`, log trong `qa-steps.log`/`qa4e-result.json` |
| Q5a | Deck: Phát → nút Dừng (kỳ vọng "Tạm dừng") → Bài kế | Có nhãn khi dừng; hết nhãn sau khi chuyển bài | State=2 + `PausedLabelShown=true` khi dừng; +3s và +15s sau Bài kế: State=3, `PausedLabelShown=false`, track digest đổi | **ĐẠT** | `qa5a-01-deck-paused-by-button.png`, `qa5a2-02-next-3s.png`, `qa5a2-03-next-15s.png` |
| Q5b | Đảo bật → đọc hàng 1–4 → Bài kế ×2 = đúng hàng 2 rồi 3 | — | Đảo bật được (`ShuffleAfter`="Đảo bài: bật") nhưng màn Deck dọc (StudioDeck) trên Sony **không hiện danh sách hàng chờ dạng số thứ tự** trong phiên này để đối chiếu tự động (xem mục 6) | **CHƯA KIỂM CHỨNG** | `qa5b4-result.json` |
| Q6 | Không ANR/crash trong cả phiên | logcat không `FATAL EXCEPTION`/ANR cho package | Dump `adb logcat -d`: 16.806 dòng buffer, **0 FATAL, 0 ANR, 0 AndroidRuntime** cho `app.melovault.player` | **ĐẠT** | `qa6-result.json` |

- Ảnh tổng hợp `00_tong_hop.png` (8 ô, 1040×1232) trên Drive.
- **SONY_SCREEN_END=OFF** 20:30:48 (`Display State=OFF`). Cuối phiên: theme trả về "Hiện tại", công tắc tải ảnh bật lại, nhạc tạm dừng (State=2), Đảo trả "tắt", Sony chạy đúng APK Debug candidate (SHA đối chiếu sau cài khớp SHA build).
- Âm thanh: Media3 PLAYING ≠ âm thanh thật → **CHƯA XÁC NHẬN ÂM THANH THỰC**.

## 5. Build / bàn giao

| Mục | Giá trị |
|---|---|
| Bundle | 13.247.? byte (ghi tại `C:\Bots\_qa_temp\anhbia-92e3db9-20261030\anh-bia-92e3db9.bundle`), SHA-256 `FDFDE3ECA27DDAEBC17B9B746BC2CFCEDCB686E60CC5EB8C5646822678E38E7A`, khớp Ni↔Tiny qua `T:\` (chia sẻ SMB trực tiếp Ni↔Tiny, không cần qua Drive) |
| Clone Tiny | `C:\Bots\MeloVault-ANHBIA-IsolatedQA-20261030-92e3db9`, HEAD `92e3db9` khớp Ni, cây sạch, `git diff --check` sạch; `local.properties` chép từ clone DONUI trên Tiny (không in, không về Ni) |
| Build Tiny | `reportTelegramConfig` → `telegram.config.valid=true`; test Debug+Release + assemble Debug+Release: BUILD SUCCESSFUL 7m58s |
| APK Debug | package `app.melovault.player`, versionCode **121**, versionName **0.6.96-beta** (không đổi so với bản DONUI đang cài — không downgrade); SHA-256 `7F51A1B460402949F19886D887359BBB657494512EB0740485AF9E00C38214A9`, 33.576.900 byte; cert SHA-256 `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (khớp bản đang cài và khớp tham chiếu dự án) |
| APK Release | SHA-256 `A8B5517FCE65083C459B728E6955693A1C111BB314564E5E0EFF50C6D0DC5393`, 30.769.518 byte; build xanh, **không cài/QA trên thiết bị** (ngoài phạm vi kế hoạch S4) |
| Drive | `MeloVault-ANHBIA-QA-CANDIDATE-20261030-vc121-92e3db9`, owner-only xác minh qua connector (chỉ 1 permission: owner `ntdung.md@gmail.com`). Chi tiết [apk-test](2026-10-30-anh-bia-moi-mau-apk-test.md) |

## 6. Chưa kiểm chứng / giới hạn

- **Q5b (Đảo + thứ tự hàng chờ):** màn Deck dọc (StudioDeck) trên Sony trong phiên này không hiển thị danh sách "1. Bài…, 2. Bài…" để đối chiếu tự động (chỉ thấy EQ/Hẹn giờ/nghệ sĩ/−30s/⏭/+60s…, không có khối hàng chờ dạng số). Có thể do màn hình "Hàng đợi" là một bề mặt riêng (bottom sheet/màn Hai khung) chưa được script này mở đúng cách. Logic Đảo/hàng chờ **không nằm trong phạm vi sửa đổi của nhiệm vụ này** (không đụng `MeloStudioDeck.kt`, service, thuật toán Đảo); đã có nhiệm vụ riêng `2026-10-30-dao-thu-tu-phat` kiểm trực tiếp phần này trước đó. Đề nghị: nếu chủ dự án cần đối chiếu lại, giao script hoặc QA tay riêng cho hàng chờ.
- **Q3 "không ảnh sai bài":** đối chiếu bằng digest hàng đầu (ổn định qua 3 lần xoay) + so sánh trực quan nhiều ảnh chụp (ảnh khác nhau khớp đúng bài, không thấy ảnh lặp/sai); **không** có phép so từng-pixel-theo-từng-dòng tự động cho toàn bộ 200 dòng đã cuộn. Cơ chế chống ảnh cũ (khóa theo `MeloArtTag`/`isCurrent(generation)`) không bị đổi bởi nhiệm vụ này.
- Tablet, Shanling: ngoài phạm vi theo quyết định chủ dự án 30/10 17:01.
- Âm thanh thật chưa xác nhận (Media3 PLAYING only).
- APK Release chỉ build, chưa cài/QA trên Sony.

## 7. Sự cố trong phiên (minh bạch)

1. Vài lần `adb pull` kết quả `uiautomator dump` bị rớt giữa chừng ("No such file or directory") — transient, không liên quan code; làm lại bước ngay sau đó, không ảnh hưởng kết luận.
2. Cuộn nhanh (20+ vuốt liên tục) làm tiêu đề + nút Cài đặt cuộn ẩn (hành vi header-collapse D1–D3 có sẵn, không phải lỗi mới) — phải cuộn lại lên đầu trước khi mở Cài đặt lần hai; đã xử lý trong script.
3. Literal tiếng Việt chèn thẳng vào script PowerShell trên Tiny bị theo dõi sai do thiếu BOM (PowerShell 5 mis-parse, khớp ghi chú cũ trong bộ nhớ dự án) — chuyển toàn bộ nhãn tiếng Việt sang đọc từ `labels.json` (`Get-Content -Encoding UTF8`), từ đó ổn định.
4. Ban đầu dùng `adb logcat` chạy nền qua `Start-Process -RedirectStandardOutput` để theo dõi cả phiên — file luôn 0 byte vì `adb.exe` tự buffer khi output được redirect, không flush dù dừng process. Chuyển hẳn sang `adb logcat -d` (dump đồng bộ, rõ ràng) cho Q4 và Q6; không mất bằng chứng, chỉ mất một vòng thử.
5. Không uninstall/clear data/logout/xóa cache/TDLib; không đổi timeout/brightness; không merge/push.
