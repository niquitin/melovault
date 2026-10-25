# Báo cáo Sonnet — E99-GON-ANH-BIA (ảnh bìa dòng trong giao diện Gọn)

**Phiên GỘP** (code + test Ni + build Tiny + QA Shanling thật + Sony hồi quy một phần), 25/10/2026 ~15:05–15:42. Vai trò theo `dot/workflow-opus-nghiem-thu` ("ChatGPT" đọc là Opus nghiệm thu). Đã dừng chờ Opus nghiệm thu.

## 1. Tóm tắt trạng thái

| Mục | Kết quả |
|---|---|
| Code + test Ni | **PASS** — Debug/Release 2042 test, 21 bỏ qua, 0 lỗi (nền 2038, +4 test mới) |
| Build Debug/Release Ni, `git diff --check` | **PASS** / sạch |
| APK có cấu hình Telegram (Tiny, từ Git bundle) | **PASS** — vc114 / `0.6.90-beta`, cert khớp, `telegram.config.valid=true` |
| Cài `install -r` lên Shanling thật | **PASS** — vc113 → vc114, `Success` |
| Gọn có ảnh bìa; 8 dòng, dòng 52 dp, ảnh 40 dp, thanh đáy 102 dp, cửa sổ 574 dp | **PASS** (đo uiautomator, mục 6) |
| Tắt công tắc → ô chữ cái, không request mới | **PASS** (0 dòng `MeloRowArt` trong 2 phút) |
| Janky ≤ +5 điểm so với vc113 | **CHƯA ĐẠT CHẶT / CẦN OPUS PHÁN** — ấm đạt (+0,5…+1,6), lạnh vượt (+5,2…+7,4); mục 7 |
| Không ANR/crash, inflight ≤ 3 | **PASS** (crash buffer 0; inflight tối đa 3) |
| Sony hồi quy | **MỘT PHẦN** — cài vc114 OK, mở app OK; Teal Phone chưa kiểm (thấy dấu hiệu có người/agent khác chạm Sony nên dừng) — mục 8 |
| Âm thanh thật | **CHƯA XÁC NHẬN ÂM THANH THỰC** (Media3 PLAYING/PAUSED chỉ chứng minh luồng phát) |

## 2. Git

| Mục | Giá trị |
|---|---|
| Nhánh | `dot/E99-GON-ANH-BIA` (không đụng nhánh tích hợp, không merge) |
| Nền / kế hoạch | `455c8af` / `d0c8531` (S0: cây sạch, HEAD=`d0c8531`, HEAD^=`455c8af`, không commit mới của agent khác) |
| Commit 1 | `3f1efa0` E99 S1: row-art rule and compact layer key |
| Commit 2 | `713dd80` E99 S2: row covers in Gon |
| Commit 3 | `a606bd7` E99 S3: bump shareable beta to vc114 (0.6.90-beta) — **commit nguồn APK** |
| Commit 4 | báo cáo này (commit sau `a606bd7`, không đổi mã) |

File đổi (đúng danh sách kế hoạch mục 4; production +81/−21 dòng gồm build.gradle, test +41):
`MeloThemePolicy.kt` (+`rowArtLayer`, KDoc), `MeloCompactPolicy.kt` (+`layerKey`), `MeloComponents.kt` (điều kiện `(teal || isCompact) && artKey` + `findRowArtFrame` dùng chung), `MeloLibraryPane.kt` (dùng hàm chung, xóa bản riêng), `MeloCompactScreen.kt` (tham số `rowArt`, scroll state, `requestVisibleArt/refreshVisibleArt`, `songRow` truyền `artKey`/`peekRowArt`), `TelegramPreviewActivity.kt` (nhánh Gọn: `rowArt`, khóa `layerKey`, **gán `rowArtThemeActive` sau `detachMeloLayer()`**, `showNewLayer` dùng `rowArtLayer`), `MeloUiLabActivity.kt` (chữ ghi chú), `app/build.gradle` (vc114 / `0.6.90`), test `MeloThemePolicyTest`, `MeloCompactPolicyTest`.
**Không** sửa `MeloRowArtLoader`/`MeloRowArtPolicy`, DB/schema, TDLib handler, Media3, palette/kích thước Gọn, Car Mode, Studio Deck.

Điều kiện bật ảnh ở Gọn: theo mục 3.2 đề xuất mặc định (Kiểu màu = Teal sáng **và** công tắc "Tải ảnh bìa từ Telegram" bật); chủ dự án không nói khác trong chat. versionName `0.6.90` theo kế hoạch (mục 11 "cần xác nhận" — Opus nghiệm thu xác nhận).

## 3. Test và lệnh (Ni)

| Lệnh | Kết quả |
|---|---|
| `.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain` (sau S3) | BUILD SUCCESSFUL 2m 5s |
| `.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain` | BUILD SUCCESSFUL 1m 4s |
| `git diff --check` | sạch (chỉ cảnh báo LF→CRLF của `app/build.gradle`, không phải lỗi) |

| Biến thể | Đã định nghĩa | Pass | Bỏ qua | Lỗi |
|---|---|---|---|---|
| ShareableDebug | 2042 | 2021 | 21 | 0 |
| ShareableRelease | 2042 | 2021 | 21 | 0 |

(Số từ XML `app/build/test-results`; bỏ qua = 21 như nền.) Test mới (+4): `rowArtLayerAddsOnlyTheCompactLayerWithTheTealTheme`, `rowArtLayerKeepsTheE98BehaviourForPhoneAndTablet` (khóa hành vi E98: `rowArtLayer == appliesTo` cho Phone/Tablet × mọi layer × cả hai theme), `layerKeyWithoutCoversIsTheOldKey`, `layerKeyChangesWithCoversAndFontScale`. Phần View chỉ kiểm bằng thiết bị. Log Gradle: `docs/plans/artifacts/E99-GON-ANH-BIA/logs/` (chỉ đọc dòng tổng kết).

## 4. Chuyển code, build Tiny, gói/chứng chỉ

| Mục | Giá trị |
|---|---|
| Chuyển code | **Git bundle** `D:\Bots\_transfer\MeloVault_E99-GON-ANH-BIA_full_a606bd7_20261025.bundle` (12.633.184 byte, đầy đủ lịch sử, `git bundle verify` OK), SHA-256 `959A430B309CF8867D33EE0161B49D9AF749C5EB077A3396FBB295DCC5B8698F` — **khớp trên Tiny** (`Get-FileHash`). Chép file bundle (+`.sha256`) vào thư mục mới `C:\Bots\_MeloVault_Git_Transfer\` qua ổ `T:` (chỉ chép file bundle mới, không chép đè repo) |
| Clone cô lập | `C:\Bots\MeloVault-E99-IsolatedQA` (clone mới từ bundle, HEAD=`a606bd7`, sạch). Clone E93D cũ (đang bẩn do patch E98) **không đụng**. `local.properties` chép **trong nội bộ Tiny** sang clone mới (không đọc, không rời Tiny; git-ignored) |
| Build Tiny | `:app:assembleShareableDebug` BUILD SUCCESSFUL 1m 48s; `reportTelegramConfig` → `telegram.config.valid=true` |
| Gói / phiên bản | `app.melovault.player`, versionCode **114**, versionName **0.6.90-beta** (aapt2) |
| Chứng chỉ | SHA-256 `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` — khớp bản vc113 đang cài trên Shanling (kiểm bằng `apksigner` trên `base.apk` kéo về: SHA `200E6EBC…0210F6` = APK E98 đã duyệt) |
| Chép APK về Ni | `D:\Bots\_e99apk\MeloVault-E99-a606bd7-vc114-TinySigned-debug.apk` — SHA-256 sau khi chép **khớp** Tiny |
| Gate cài Shanling | `adb devices -l` = device `30313860180939`; getprop `Shanling M3 Ultra` / `Shanling` / Android 10; vc114 > vc113; cert khớp; Telegram config true; Media3 `state=2` (tạm dừng, không PLAYING) → `adb -s 30313860180939 install -r` → `Success` (15:30). Không uninstall, `pm clear`, `-d`, xóa dữ liệu/cache/TDLib, logout |

APK từ Ni (Debug/Release `app/build/outputs/apk/shareable/{debug,release}/`) **không có cấu hình Telegram dùng được, không cài, không bàn giao**.

## 5. QA Shanling M3 Ultra (Ni, `30313860180939`, Android 10) — Agent tự làm, 15:24–15:38

Trạng thái UI **trước QA** (ghi từ ảnh 04–07): Kiểu màu = **Teal sáng · ảnh bìa**, công tắc "Tải ảnh bìa từ Telegram" = **bật**, Gọn = **Tự động**, Car Mode = Tắt, Studio Deck. Tab: Tất cả. **Sau QA đã trả đúng như vậy** (ảnh 27, 31); chỉ có hàng đợi phát khác (mục 9).

| # | Hành động | Kỳ vọng | Thực tế | KQ | Ảnh |
|---|---|---|---|---|---|
| 0 | Đo nền vc113: `gfxinfo reset` → 10 swipe (384,900→384,400, 300 ms, nghỉ 0,6 s) → chờ 5 s | ghi Janky | 623 khung, Janky **5 (0,80 %)**, p90 9 ms | ghi nhận | 01, 02 |
| 1 | `install -r` vc114, mở app, chờ 8 s | Gọn màu cũ, ảnh bìa trên dòng, 8 dòng; uiautomator: dòng 104 px, ảnh 80×80, đáy ≥ 96 px, cửa sổ 574 dp | Màu như vc113. **8/8 dòng có ảnh** (thumbnail bài hoặc ảnh kênh). Dòng `[16,156]…[752,260]` = **104 px (52 dp)** × 8 dòng đủ ([884,988] trong list [156,1000]); khung ảnh `[16,168][96,248]` = **80×80 (40 dp)**; thanh đáy `[16,1000][752,1204]` = 204 px = **102 dp**; cửa sổ app 768×1208 px = **384×574 dp** | **PASS** | 08, 09, `logs/ui_02.xml` |
| 2 | Tab Tất cả, chờ 5 s không chạm | ≥ 80 % dòng có ảnh | 8/8 (100 %) bằng mắt; log `compact visible=9 withArt=9 settled=9`; ảnh đúng dòng (cùng kênh → cùng ảnh kênh; bài có bìa riêng khác nhau); 1 bot không ảnh → ô chữ cái "N" (đúng) | **PASS** | 09, 28 |
| 3 | `gfxinfo reset` → 10 swipe như đo nền | Không ANR/crash; Janky ≤ nền+5; inflight ≤ 3; ảnh về đủ | **Lần 1 (ngay sau cài, quá trình mới): 597 khung, Janky 49 (8,21 %), p90 15 ms.** Lần 2: 2,44 % (p90 12). Lần 3: 1,30 % (p90 10). Crash buffer 0, không dialog ANR. inflight chỉ 0–3 (62/33/24/10 lần). Sau dừng ảnh về đủ (`visible=9 withArt=9`) | **PASS không-crash, inflight; Janky: xem mục 7** | 10, `logs/gfx_*` |
| 4 | Menu ☰ → Đã tải → bấm bài "Nhạc 2.mp3" (đã tải ✓); bấm ⏯ tạm dừng; bấm thẻ đáy | Media3 3 → 2; dòng đang phát vẫn có ảnh + biểu tượng; thẻ mở được | `dumpsys media_session` `state=3` rồi `state=2`; dòng đang phát có ảnh nhúng + biểu tượng trạng thái; thẻ mở Đang phát (ảnh 15) và quay lại. Danh sách Đã tải 7 dòng vì dòng ghi chú quét offline 2 dòng (giới hạn đã biết từ E98) | **PASS** | 12–16 |
| 5 | ☰ → Cài đặt giao diện → bỏ chọn "Tải ảnh bìa từ Telegram" → quay lại; quét 10 swipe; rồi bật lại | Tắt: về ô chữ cái, không request; bật: ảnh trở lại | Tắt lúc 15:34:23: các dòng có ảnh kênh/bìa mạng về ô chữ cái "N/L" (ảnh 22). **0 dòng `MeloRowArt` từ 15:34:23 đến 15:36:40** (gồm 10 swipe, cold start) — không request mới. Janky khi tắt: **0,00 %** (p90 8 ms). Bật lại 15:36:41: ảnh trở lại ngay (cache), log `sent=19…` từ 15:36:40. Ghi chú: vài dòng bài đã tải vẫn có ảnh nhúng khi tắt — đó là `row.art` từ `thumbnailCache` có sẵn (cơ chế cũ, không phải loader E99) | **PASS** | 20–23, 27, 28 |
| 6 | ☰ → Nhạc trên máy, chờ 4 s | ảnh nhúng qua `loadThumbnail` hoặc ô chữ cái; không crash | 3.557 bài; nhiều dòng có ảnh nhúng, vài dòng ô chữ cái; không crash | **PASS** | 30 |

Ảnh tổng hợp: `docs/plans/artifacts/E99-GON-ANH-BIA/00_tong_hop.png` (8 ô, 1520×1334). 35 ảnh gốc + `logs/` cùng thư mục (private, git-ignored). Có tên bài/kênh → **không commit**.

## 6. Kích thước và màu Gọn (so với vc113)

Dòng 52 dp, ảnh 40 dp, thanh đáy 102 dp, cửa sổ 574 dp, 8 dòng: giữ nguyên (mục 5 bước 1). Màu: nền, chữ, thẻ đáy, ô chữ cái giống ảnh 01 (vc113) — palette Gọn không đổi (không sửa file palette). Bo góc ô ảnh (rowArtView) hơi khác ô chữ cái cũ (đã nêu ở rủi ro kế hoạch, thẩm mỹ). Thẻ "Đang phát" ở thanh đáy trống ngay sau khi mở app mới (chưa có bài trong phiên, hàng đợi cũ chưa nạp) — hành vi cũ, không thuộc E99.

## 7. Janky — số liệu đầy đủ và đánh giá

| Phép đo (cùng kịch bản 10 swipe) | Khung | Janky | p90 |
|---|---|---|---|
| vc113, app đã chạy lâu (nền, **ấm**), không có ảnh ở Gọn | 623 | 5 = **0,80 %** | 9 ms |
| vc114, ảnh BẬT, ngay sau cài (**lạnh**, lần 1) | 597 | 49 = **8,21 %** | 15 ms |
| vc114, ảnh BẬT, lần 2 (ấm, vùng danh sách mới) | 615 | 15 = **2,44 %** | 12 ms |
| vc114, ảnh BẬT, lần 3 (ấm) | 615 | 8 = **1,30 %** | 10 ms |
| vc114, ảnh TẮT (ấm) | 620 | 0 = **0,00 %** | 8 ms |
| vc114, ảnh TẮT, sau `force-stop` + mở lại (**lạnh**) | 611 | 20 = **3,27 %** | 13 ms |
| vc114, ảnh BẬT, sau `force-stop` + mở lại (**lạnh**) | 599 | 36 = **6,01 %** | 14 ms |

Đánh giá trung thực: so thẳng với nền vc113 (ấm, 0,80 %), ngưỡng kế hoạch +5 điểm → **ấm đạt** (+0,5…+1,6 điểm), **lạnh vượt** (+7,4 lần đầu, +5,2 lần sau). Một phần lớn khoảng lạnh là chi phí khởi động chung (cùng build vc114, ảnh TẮT lạnh đã 3,27 % so với ấm 0,00 %), nên chênh do ảnh trên cùng điều kiện lạnh khoảng +2,7…+4,9 điểm. **Chưa đo vc113 lạnh** (bản đã bị ghi đè sau khi cài), nên chưa tách được hoàn toàn. Quyết định PASS/FAIL tiêu chí này thuộc Opus nghiệm thu; p90 14–15 ms vẫn dưới ngân sách khung 16,7 ms. Không tự thu nhỏ/đổi gì để "đẹp số". Nếu muốn giảm: hướng khả dĩ (ngoài phạm vi, chưa làm) là bớt tải khi `FLING`, hoặc dựng ô ảnh nhẹ hơn.

## 8. Sony Xperia 5 II (Tiny, `QV725WEQ54`, Android 12, XQ-AS52) — hồi quy MỘT PHẦN

- Gate: `adb devices -l` = device; getprop `XQ-AS52`/`Sony`; vc113 → cert `eecd3df7…` khớp; Media3 `state=2` (tạm dừng) → `install -r` vc114 `Success` (15:38:20); mở app (ảnh 32, thư viện Điện thoại dọc, ô chữ cái, giao diện "Hiện tại": **không có log `MeloRowArt`** → không tải ảnh, đúng hợp đồng E98 "Hiện tại = như trước").
- **Ghi nhận bất thường:** giữa ảnh 33 và 34 ô tìm kiếm Sony tự có chữ `xe mô` và bàn phím hiện (tôi chỉ chạm biểu tượng ⚙ và vuốt hai lần; không gõ gì). Nghi có người/agent khác đang dùng Sony ⇒ **dừng thao tác Sony**, không thử Teal Phone và không đổi cài đặt Sony. **CHƯA KIỂM:** Teal Phone dọc còn ảnh như E98 sau khi gom `findArtHost` → `findRowArtFrame`; Gọn giả lập trên Sony.
- Kiểm lại lúc 15:41:58 (ảnh chụp lại trong `shots\04_Sony_recheck_154158.png` trên Tiny): chữ `xe mô`, bàn phím và danh sách **không đổi suốt hơn 2 phút** ⇒ không thấy ai đang chạm. Nên tôi mới tắt màn: `SONY_SCREEN_END` = **OFF** lúc 15:42:20 (`keyevent 223`; `Display State=OFF`, `mWakefulness=Dozing`). Nguồn gốc chữ `xe mô` vẫn **chưa rõ**; ô tìm kiếm Sony còn chữ đó (trạng thái giao diện tạm, không phải cài đặt đã đổi bởi tôi).

## 9. Dọn dẹp, màn hình, log

- `SHANLING_SCREEN_BEGIN` 15:24:31 → `SHANLING_SCREEN_END` **OFF** 15:37:59 (`keyevent 223`; `mScreenState=OFF`, `mWakefulness=Asleep`).
- Cài đặt UI Shanling trả về như trước QA (Teal, công tắc bật, Gọn Tự động). Khác: hàng đợi phát hiện tại (tôi bấm "Nhạc 2.mp3" rồi tạm dừng; sau đó `am force-stop` để đo lạnh nên phiên Media3 về trạng thái 0 — **hàng đợi "Nonstop - (Nhật Ký)…" cũ không còn nạp lại**, là bài của chủ dự án để ở tạm dừng). `force-stop` không xóa dữ liệu/cache/TDLib.
- Media3/ANR: `dumpsys media_session` PLAYING (3) → PAUSED (2) khi bấm; `logcat -b crash` không có dòng `app.melovault`; không dialog ANR. Logcat Shanling bị `chatty` nuốt (TDLib nhiều dòng) nên dùng `logcat MeloRowArt:I '*:S'` ghi tệp trong lúc thao tác (`logs/logcat_rowart_*.txt`, chỉ số đếm, không tên bài/kênh). Tệp đầu tiên chết lúc 15:32:54 (EOF) nên bước 4 không có log ảnh; số liệu bước 4 dựa trên ảnh + media_session.
- **CHƯA XÁC NHẬN ÂM THANH THỰC.**

## 10. Rủi ro và chỗ chưa kiểm chứng

- Janky lạnh vượt +5 so nền ấm (mục 7) — cần Opus phán; chưa đo vc113 lạnh.
- Sony: Teal Phone/Tablet/Hai khung sau đổi `findRowArtFrame` **chưa kiểm trên máy** (mã chỉ di chuyển hàm, test E98 xanh); Car Mode, Y700, S25, FYT không kiểm.
- Gọn lúc đang quét offline: Đã tải còn 7 dòng (giới hạn mở từ E98), không thuộc E99.
- Lưới nguồn Gọn (ô kênh) và trang Đang phát Gọn vẫn ô chữ cái / `thumbnailCache` cũ, đúng phạm vi.
- Khi đổi Kiểu màu sang Hiện tại rồi quay lại màn Gọn: khóa dựng lại có `:art` đã được test JVM, **chưa thử trên máy** (chỉ thử bật/tắt công tắc).
- Vài dòng "Đã tải" vẫn có ảnh khi tắt công tắc do `thumbnailCache` cũ (hành vi có sẵn).

## 11. APK QA cần Opus nghiệm thu bàn giao

| Mục | Giá trị |
|---|---|
| Nhãn | **QA CANDIDATE — CHƯA NGHIỆM THU** (Sonnet không upload/công bố, không đổi quyền Drive) |
| Loại build | Debug QA, ký bằng debug key (cùng chứng chỉ bản đang cài), dựng trên Tiny |
| Git | nguồn `a606bd7` trên `dot/E99-GON-ANH-BIA` (cha `713dd80`, `3f1efa0`, nền `455c8af`); bundle SHA `959A430B…8698F` |
| APK (đã kiểm tồn tại) | Tiny: `C:\Bots\_qa_handoff\E99-GON-ANH-BIA\MeloVault-E99-a606bd7-vc114-TinySigned-debug.apk`; Ni: `D:\Bots\_e99apk\MeloVault-E99-a606bd7-vc114-TinySigned-debug.apk` |
| Gói / phiên bản | `app.melovault.player` / vc114 / `0.6.90-beta` |
| SHA-256 / byte | `272D53AAC70FCD4DDF86C69B4A11C92E6894B5A6AACC7869BBA36DAE7C07B653` / 33.413.060 |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` |
| Telegram config | **true** (chỉ true/false) |
| Test | Debug/Release 2042, 21 bỏ qua, 0 lỗi; build Debug/Release Ni OK |
| Thiết bị | Shanling M3 Ultra (đã cài vc114, QA mục 5); Sony XQ-AS52 (đã cài vc114, QA một phần mục 8) |
| Chưa QA / hạn chế | Janky lạnh (mục 7); Teal Phone trên Sony; âm thanh thật; Y700/S25/FYT/Car |
| Rollback | Người dùng: tắt "Tải ảnh bìa từ Telegram" hoặc Kiểu màu = Hiện tại ⇒ Gọn như vc113. Mã: revert commit `713dd80` (S1 chỉ thêm hàm thuần); không hạ versionCode |

## 12. Đường dẫn

- Ảnh/log: `docs/plans/artifacts/E99-GON-ANH-BIA/` (`00_tong_hop.png`, `00…31_Shanling_*.png`, `32…34_Sony_*.png`, `logs/`).
- Tiny: `C:\Bots\_qa_handoff\E99-GON-ANH-BIA\` (APK, `tiny_build.log`, `shots\`).
- Bundle: `D:\Bots\_transfer\MeloVault_E99-GON-ANH-BIA_full_a606bd7_20261025.bundle` (+`.sha256`); log Gradle Ni: `D:\Bots\_e99logs\`.
