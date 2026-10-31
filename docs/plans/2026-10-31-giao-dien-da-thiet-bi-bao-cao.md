Thực thi: Claude Code Sonnet 5.5, phiên chặng C (G4, 31/10/2026). Chặng A (G0–G2) và B (G3) cũng do Claude Code Sonnet 5.5; xem phần chặng B và chặng C ở cuối file.

# Báo cáo thực thi — giao diện phù hợp mọi loại thiết bị

TASK_ID: `2026-10-31-giao-dien-da-thiet-bi`. Nhánh `dot/2026-10-31-giao-dien-da-thiet-bi`; worktree `D:\Bots\_worktrees\MeloVault-giao-dien-da-thiet-bi`. Baseline `94ab6b0`. HEAD lúc nhận: `3cdcefd`.
Kế hoạch: [`2026-10-31-giao-dien-da-thiet-bi.md`](2026-10-31-giao-dien-da-thiet-bi.md). Bàn giao: [`-ban-giao.md`](2026-10-31-giao-dien-da-thiet-bi-ban-giao.md). Khảo sát: [`-khao-sat.md`](2026-10-31-giao-dien-da-thiet-bi-khao-sat.md).
Trạng thái: **chặng A, B, C xong. CHỜ CHỦ DỰ ÁN NGHIỆM THU** (QA CANDIDATE — CHƯA NGHIỆM THU). APK Debug `20bdf95` đã build ở Tiny, cài lên Sony và chụp lại; **UI-03 chưa sửa được trên máy thật, UI-02 chỉ cải thiện một phần** (xem “Chặng C”). Hồ sơ APK/Drive: [`-apk-test.md`](2026-10-31-giao-dien-da-thiet-bi-apk-test.md).

## Tóm tắt các bước

| Bước | Việc | Kết quả |
|---|---|---|
| Nhận việc | `git status` sạch, nhánh đúng, HEAD `3cdcefd`, `diff --check` sạch; không có tiến trình/agent khác trên nhánh; Sony `device`, màn OFF, không ai dùng | PASS |
| G0 | APK trên Sony: `app.melovault.player` `0.6.96-beta` vc121, kéo `base.apk` (33 593 284 byte) về Tiny, SHA-256 `6E3D6B6E…A029` | **KHỚP** bản `a2f0a88` |
| G1 | Thêm `MeloLayoutMatrixTest` (7 ca) khóa kết quả `chooseLayout` / `profileFor` / `shouldOfferCarMode` / `panelCompact` cho M1–M9 | **PASS** Debug + Release; không dòng nào lệch bảng mục 4; không sửa `MeloScreenPolicy` |
| G2 lượt 1 | Khảo sát Sony ở cỡ gốc: dọc, ngang, chia đôi | UI-01…UI-06 |
| G2 bổ sung | Sau khi chủ dự án cho phép đổi tạm `wm size` / `wm density` / `font_scale` (bàn giao): M1, M2, M4–M8, F1–F3 trên M3 và M6 | Bảng lỗi hợp nhất UI-01…UI-08; M8 và F3 M6 ngang chỉ có một phần ảnh (xem khảo sát mục 4). Mục “Phiên khảo sát bổ sung” bên dưới |

## G0 — chi tiết

- Máy: `adb devices -l` → `QV725WEQ54 device`; `ro.product.model=XQ-AS52`, `manufacturer=Sony`.
- Giá trị gốc trước khảo sát: `wm size` Physical `1080x2520` (không Override), `wm density` Physical `420`, `font_scale` `1.0`, `user_rotation` `0`, `accelerometer_rotation` `1`, `Display State=OFF`.
- Máy đang có cả `app.melovault.player.open` (màn đăng nhập, chủ dự án dùng) và `app.melovault.player`. Khảo sát dùng bản `app.melovault.player` (đúng nguồn kế hoạch); bản `.open` không bị thao tác.

## G1 — chi tiết

- File mới: `app/src/test/java/com/bsdung/musicassistantcar/data/telegram/MeloLayoutMatrixTest.kt`. Commit `d9ab585`.
- Nội dung 7 ca: kích thước dp của ma trận; dọc; ngang; `panelCompact`; mời Car Mode (chỉ ngang ≥ 1200); biên chia đôi M9 (h < 480); các biên 599/600, 559/560, 959/960 và Car Mode thắng mọi cỡ.
- Lệnh: `.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --tests '*MeloLayoutMatrixTest' --console=plain` → `BUILD SUCCESSFUL in 3m 50s`.
- Lưu ý: `--tests` chỉ áp cho tác vụ cuối (Release), nên Debug chạy **toàn bộ** bộ test.
- Cộng XML `build/test-results`:

| Biến thể | suites | tests | skipped | failures | errors |
|---|---|---|---|---|---|
| Debug (toàn bộ) | 180 | 2317 | 21 | 0 | 0 |
| Release (chỉ `MeloLayoutMatrixTest`) | 1 | 7 | 0 | 0 | 0 |

  Debug: trước G1 là 2310/21/0/0 (HANDOFF) → thêm 7 ca = 2317. Khớp.
- Chỉnh bảng mục 4 (theo quy tắc “bảng sai thì sửa bảng”): **chỉ một chỗ nhỏ** — M5 cao 828 dp (bảng ghi ≈ 829, `toInt` cắt 828,95). Không ảnh hưởng bố cục. Không đổi mã.
- Log đầy đủ ở `docs/plans/artifacts/2026-10-31-giao-dien-da-thiet-bi/G1-20261031-202449/` (private, bị `.gitignore`; có `gradle.log` UTF-16 và `tong-ket.txt`).
- Chưa chạy `assemble*` và Personal vì G3 chưa bắt đầu (không sửa mã app).

## G2 — chi tiết

Thao tác trên Sony (qua Tiny, Desktop Commander), ảnh `adb exec-out screencap -p`, kiểm header PNG `89504E47` cho mọi file. Mỗi lần chạm đều kiểm `mCurrentFocus` là `app.melovault.player/` (trừ lúc chia đôi). Không `BACK` ở màn gốc.

| # | Cấu hình | Hành động | Thực tế | Kết quả | Ảnh |
|---|---|---|---|---|---|
| 1 | M3 dọc | Mở app, Thư viện đầu + cuộn giữa | Điện thoại dọc, dùng được | PASS | `M3-doc-thuvien-dau/giua` |
| 2 | M3 dọc | ⋮ dòng bài | menu đủ 5 mục | PASS | `M3-doc-menu-dong` |
| 3 | M3 dọc | Ô tìm + bàn phím | list còn ≈ 2,5 dòng | **UI-02** | `M3-doc-tim-banphim` |
| 4 | M3 dọc | Tab Nguồn | dùng được | PASS | `M3-doc-nguon` |
| 5 | M3 dọc | Mở Đang phát, cuộn xuống Tiếp theo, Hàng đợi | Đang phát đủ; Hàng đợi sát mép | **UI-03, UI-05** | `M3-doc-dang-phat`, `tiep-theo-1/2`, `hang-doi` |
| 6 | M3 dọc | Cài đặt | không lỗi | PASS | `M3-doc-cai-dat-1/2` |
| 7 | M3 ngang | Xoay, Thư viện/Nguồn, cuộn | list ≈ 1,3 dòng; cột trái sát mép | **UI-01, UI-04** | `M3-ngang-thuvien-*` |
| 8 | M3 ngang | ⋮, ô tìm + bàn phím | menu đủ; bàn phím toàn màn | UI-06 (đề xuất) | `M3-ngang-menu-dong`, `tim-banphim` |
| 9 | M3 ngang | Hàng đợi, Cài đặt | có lề, không lỗi | PASS | `M3-ngang-hang-doi`, `cai-dat-1/2` |
| 10 | M9 chia đôi | Đa nhiệm → Chia đôi màn hình → app thứ hai | cửa sổ trên 411 × 435 dp → Gọn | PASS (đúng luật) | `M9-recents*`, `M9-chia-doi-thuvien` |
| 11 | M9 | Đang phát, ô tìm + bàn phím, Cài đặt | dùng được, bàn phím không che cửa sổ trên | PASS | `M9-chia-doi-*` |

Ảnh tổng hợp (cạnh dài ≤ 1600 px): `00_tong_hop_M3-doc.png` (1580×1528), `00_tong_hop_M3-ngang.png` (1580×1086), `00_tong_hop_M9-chia-doi.png` (1600×864).
Danh sách lỗi đầy đủ và quan sát nằm ở file khảo sát.

## Việc dở dang / chưa kiểm chứng

- (Cập nhật sau phiên bổ sung) Những cấu hình ở dòng này lượt 1 chưa kiểm vì chưa được phép đổi cỡ; **nay đã khảo sát** (xem mục “Phiên khảo sát bổ sung”). Còn thiếu: M8 chỉ có ảnh Thư viện; F3 M6 ngang thiếu menu ⋮ và Cài đặt; hộp thoại mời Car Mode không quan sát được.
- Âm thanh thật chưa kiểm: nhạc có lúc phát trong phiên nhưng không có nguồn âm độc lập — CHƯA XÁC NHẬN ÂM THANH THỰC.
- Không có tablet thật, máy gập thật, FYT hay S25 (đã ngoài phạm vi QA).
- Chưa build, chưa `assemble`, chưa kiểm Personal (chặng B).

## Sự cố và thiết bị

- **Vô tình phát nhạc ≈ 15–30 giây:** lần chạm đầu vào mũi tên `^` của thẻ “Nghe tiếp” tiếp tục phát bài #140 đang dừng, thay vì mở Đang phát. Phát hiện sau ≈ 1 phút, bấm Tạm dừng. Không đổi bài, không đổi hàng đợi. Cuối phiên PAUSED, 00:46. (Quy tắc cấm “phát bài mới/đổi hàng đợi” không bị vi phạm; nhưng đây là việc ngoài kế hoạch nên ghi rõ.)
- **Lỗi vặt khi chạy Gradle nền (không ảnh hưởng kết quả):** log do PowerShell ghi dạng UTF-16 và wrapper không tạo được `exit.txt`; Gradle vẫn chạy xong (`BUILD SUCCESSFUL`), kết quả lấy từ XML. Không chạy lại.
- Khôi phục: `wm size`/`wm density`/`font_scale` **không bao giờ bị đổi**; `user_rotation` 0 và `accelerometer_rotation` 1 đã về gốc; thoát chia đôi; đưa app `.open` về trước như lúc nhận.
- `SONY_SCREEN_END=OFF` lúc 2026-10-31 20:34:54 (`Display State=OFF`, `mWakefulness=Dozing`).

## Rủi ro

- Ảnh Sony chứa tên bài/kênh Telegram và vài ảnh có hình thu nhỏ Chrome. Giữ private, không commit.
- Chưa có chủ dự án duyệt bảng lỗi. Chặng B mặc định sửa UI-01…UI-05 theo GD4; UI-06 là Đề xuất.
- UI-01/UI-02 sửa phần đầu Thư viện dùng chung có thể làm Gọn/Car xê dịch → hồi quy bằng test sẵn có và ảnh M1/M9.

## Phiên khảo sát bổ sung (21:04–22:09) — cùng phiên chặng A

- **Căn cứ:** chủ dự án trả lời trong chat sau chặng A: “Cho phép đổi tạm wm size/density/font_scale trên Sony, khôi phục ngay sau mỗi cấu hình. Làm phiên khảo sát bổ sung M1, M2, M4–M8, F1–F3.” Đã ghi vào bàn giao (commit `3ceb057`). Chỉ Sony `QV725WEQ54`; các luật cấm khác giữ nguyên.
- **Cách làm:** script PowerShell trên Tiny (`C:\Bots\_qa_temp\GD-G2\cfg2.ps1`, `survey2.ps1`, `chain*.ps1`). Mỗi cấu hình: đặt cỡ, mở app, tìm phần tử bằng `uiautomator dump` (không đoán tọa độ), chụp, rồi `finally` khôi phục. Mọi thứ nằm ngoài repo, không commit. Ảnh ở `docs/plans/artifacts/…/G2B/`.
- **Kết quả:** bảng UI-01…UI-08 nằm trong [khảo sát](2026-10-31-giao-dien-da-thiet-bi-khao-sat.md). Mở rộng so với chặng A:
  - UI-01 và UI-02 xảy ra ở mọi cửa sổ thấp / có bàn phím, nặng nhất ở M2 ngang (≈ 0,5 dòng) và F3 M3 ngang (0 dòng) → **Chặn dùng** ở các ca đó;
  - UI-04 xảy ra ở mọi cỡ Hai khung;
  - thêm UI-07 (chữ ≥ 1,5 cắt chip / ô tìm / nút) và UI-08 (nhãn tab cắt ở chữ 2,0 trên 600 dp).
- **Bài học kỹ thuật (để chặng sau dùng lại):**
  1. `wm size` của Sony không nhận chiều rộng quá ≈ 1096 px. Muốn ra dp lớn phải hạ `density`: M4 `1080x2340 / 400`, M5 `1080x1298 / 250`, M6 `1080x1728 / 288`, M7 `1080x1728 / 216`.
  2. App xét “tấm màn nhỏ” theo kích thước **vật lý**, nên `wm size` không ra bố cục Gọn (M1 ra Điện thoại dọc).
  3. Vuốt theo chiều dọc trong màn Đang phát có thể rơi vào thanh tua và đổi vị trí phát. Chỉ vuốt trong vùng cuộn.
- **Sự cố, ghi đủ trong khảo sát mục 5:**
  - hai lần khôi phục `finally` bị cắt do ADB chớp rớt, đã khôi phục thủ công trong ≈ 1–2 phút;
  - vị trí phát bài #140 bị kéo từ 00:46 lên 02:48 do vuốt trúng thanh tua (luôn tạm dừng, không đổi bài/hàng đợi).
- **Trạng thái cuối:** không có Override cỡ/mật độ, `font_scale` 1.0, `user_rotation` 0, `accelerometer_rotation` 1; `SONY_SCREEN_END=OFF` lúc 2026-10-31 22:09:10.
- **Chưa làm:** không có APK mới, không `assemble`, không đụng mã app (chỉ thêm test G1). Chưa có chủ dự án duyệt bảng lỗi.

## Chặng B — G3 sửa lỗi UI (31/10/2026, Claude Code Sonnet 5.5)

Căn cứ: chủ dự án quyết trong chat 31/10/2026: "Chặng B sửa UI-01…UI-05, UI-07, UI-08 theo GD4; UI-06 không sửa." Nhận việc: cây sạch, HEAD `bdfab43`, `diff --check` sạch, không có agent khác. Không dùng Sony, không build Tiny, không cài APK; không sửa `MeloScreenPolicy` hay ngưỡng. Mỗi lỗi một commit trên `dot/2026-10-31-giao-dien-da-thiet-bi`. Test mới nằm ở `MeloUiDaThietBiTest` (19 ca).

| Lỗi | Commit | Sửa gì (file chính) | Test JVM |
|---|---|---|---|
| UI-01 | `fd9720f` | Thư viện hết chỗ thì tự ẩn **phần phụ** theo thứ tự: dòng "Phạm vi" → ghi chú → thẻ nguồn → hàng chip thể loại, cho tới khi danh sách còn ≥ 3 dòng (`MeloLibraryPolicy.partsToTrim`, `MeloLibraryPane.applyTrim`). Không bao giờ ẩn hàng tab, ô tìm, hàng "Tất cả · … bài / Phát hết". Quyết định dựa trên cỡ **tự nhiên** của các phần nên không nhấp nháy; khi tiêu đề chủ cuộn gọn lại, phần đã ẩn giữ nguyên | 4 ca |
| UI-02 | `577e094` | Cửa sổ cao < 900 dp: khi bàn phím mở, thanh đáy (mini-player + tab) ẩn, đóng bàn phím thì hiện lại (`MeloBottomBarPolicy.hiddenForKeyboard`, `MeloPhoneScreen.onKeyboardChanged`, móc từ `installLayerInsets`) | 2 ca |
| UI-03 | `6b3a507` | Hàng đợi toàn màn: thêm lề ngang `screenMargin` và khoảng cách trên `gap` (`MeloQueueUiPolicy.fullScreenPadding`, `showMeloQueue`) | 2 ca |
| UI-07 | `5b971a6` | Chữ lớn: hàng tab, nút "Phát ngẫu nhiên/Mở tất cả", "Ẩn kênh 0 bài/Quét lại" xuống dòng thay vì bị cắt (`MeloFlowRow`); gợi ý ô tìm bỏ từng từ cuối cho vừa (`fitHint`); menu ⋮ dòng bài dùng nhãn ngắn từ chữ ≥ 1,5 (`songMenuLabels`) | 8 ca |
| UI-04 | `e685813` | Hai khung: cột Đang phát có lề trái `screenMargin` như cột danh sách (`MeloTabletScreen`) | không (hằng số lề, không có điều kiện để tách) |
| UI-05 | `b1ca641` | Dòng trạng thái ("Tạm dừng", "Đang kiểm tra…") cách danh sách/thẻ "Kho tổng hợp" một `gap` đầy đủ, và cách hàng chip kỹ thuật ở Đang phát một `gap` (`MeloLibraryPane`, `MeloStudioDeck`) | không (hằng số lề) |
| UI-08 | `75375b4` | Nhãn tab dưới đo bằng font thật; từ chữ ≥ 1,5 nhãn rộng hơn ô thì chỉ còn biểu tượng (`MeloBottomBarPolicy.labelOverflows`). Chữ < 1,5 giữ nguyên luật cũ để không đổi nhãn trên Sony/S25 | 3 ca |

UI-06 (bàn phím toàn màn ở cửa sổ ngang thấp): **không sửa**, đúng quyết định (Đề xuất, ngoài GD4).

### Gate Ni (một lần, log ở `docs/plans/artifacts/2026-10-31-giao-dien-da-thiet-bi/G3-20261031-223609/`, bị `.gitignore`)

| Lệnh | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | `BUILD SUCCESSFUL` 1m54s. Cộng XML: Debug **2336** tests / **21** skipped / **0** failures / **0** errors (181 suites); Release **2336 / 21 / 0 / 0** (181 suites) |
| `:app:assembleShareableDebug :app:assembleShareableRelease` | `BUILD SUCCESSFUL` 1m58s |
| `-PmeloOpen=true :app:assembleShareableDebug` | `BUILD SUCCESSFUL` 36s |
| `:app:assemblePersonalDebug` | `BUILD SUCCESSFUL` 36s |
| `git diff --check` | sạch (exit 0) |

- 2336 = 2317 (cuối chặng A) + 19 ca mới. `MeloLayoutMatrixTest` 7/7 vẫn xanh (không đụng `MeloScreenPolicy`).
- Chưa có APK đóng gói cho QA (không build Tiny, cấu hình Telegram không có ở Ni); các APK Ni vừa build chỉ để kiểm biên dịch, **không** dùng để cài/bàn giao.

### Chưa kiểm chứng / giới hạn

- **Chưa có ảnh thiết bị sau sửa** (không cài APK ở chặng này). Chặng C chụp lại đúng các cấu hình ở khảo sát mục 1; JVM test chỉ chứng minh các quy tắc tính toán, không chứng minh bố cục thật.
- UI-01: chỉ ẩn các phần **trong Thư viện**. Tiêu đề chủ của màn Điện thoại dọc (MeloVault / dòng đồng bộ) vẫn chỉ gọn khi cuộn, nên M1 dọc (537 dp) dự kiến còn ≈ 2 dòng, M2 ngang (333 dp) ≈ 1,5–2 dòng; cần chụp để xác nhận. Hàng tab trong Hai khung cố ý giữ vì là đường vào Nguồn/Playlist.
- UI-02: chỉ màn Điện thoại/Tablet dọc (nơi khảo sát thấy lỗi). Hai khung ngang vẫn bị bàn phím toàn màn (UI-06, không sửa).
- UI-07: hàng chip thể loại vẫn là dải cuộn ngang (cố ý, vì đây là bộ lọc cuộn được); menu ⋮ dòng bài là `PopupMenu` hệ thống nên sửa bằng nhãn ngắn chứ không đổi thành menu mới.
- UI-08 đo bằng bề rộng chữ in đậm; cấu hình biên (chữ 1,5 ở 411 dp) cần xem ảnh.
- Âm thanh thật vẫn **CHƯA XÁC NHẬN ÂM THANH THỰC** (không liên quan chặng này).
- Phần thêm ngoài lỗi: `scopeNote` rỗng nay ẩn thay vì chiếm một dòng (văn bản thật luôn khác rỗng nên không đổi hiển thị).

## Chặng C — G4: Tiny build, cài Sony, chụp lại (31/10/2026 22:53–23:56, Claude Code Sonnet 5.5)

Không sửa mã app. Nhận việc: cây sạch, HEAD `20bdf95`, `diff --check` sạch, không có agent khác. Ảnh private: `docs/plans/artifacts/2026-10-31-giao-dien-da-thiet-bi/G4/` (`shots/`, `shots2/`) và các ảnh tổng hợp `tong-hop-*.png`, `00_tong_hop.png` (đều bị `.gitignore`). Ảnh “trước” lấy từ `G2-…` / `G2B/`.

### Build và cài APK

| Bước | Kết quả |
|---|---|
| Bundle đầy đủ nhánh | `gdtb-chang-c-20bdf95-full.bundle` 13.411.291 byte, SHA-256 `7751DECE37D0BED72E3E50C03D83F1C2522C02F4D5533874609D55009D1D0C48`, `git bundle verify` ok; bản trên Tiny khớp SHA |
| Clone Tiny | `C:\Bots\MeloVault-GDTB-IsolatedQA-20261031-20bdf95`, HEAD `20bdf9509464e4fc79f7129875bcc8c9e219564e`, cây sạch; `local.properties` chép từ clone DONUI trên Tiny (ngoại lệ E104 S11, không đọc/in) |
| `reportTelegramConfig` | `telegram.config.valid=true` (bản `.open` = false, **không cài bản `.open`**) |
| `:app:assembleShareableDebug` | `BUILD SUCCESSFUL` 2m42s |
| Gate APK | `app.melovault.player` vc **121** / `0.6.96-beta`, debuggable, cert `eecd3df7…fce8`, 33.593.284 byte, SHA-256 `367CAA37…F1006`, git HEAD đúng, 0 dòng `status` |
| Cài | `adb -s QV725WEQ54 install -r` (XQ-AS52 / Sony, versionCode không đổi, không hạ cấp, chữ ký trùng, nhạc đang dừng) → `Success` |

Không build Release (không có trong kế hoạch G4); Release/test đã qua ở gate Ni chặng B.

### Cấu hình đã chụp (ghi đúng mục 1 khảo sát)

M3 dọc/ngang; M2 ngang (1080×2340/480); M1 dọc (720×1280/320); M5 dọc (1080×1298/250); M6 dọc/ngang (1080×1728/288); M7 ngang (1080×1728/216); M8 ngang (608×1080/90); F2 M3 dọc; F3 M3 dọc/ngang; F3 M6 dọc/ngang. Dùng lại script chặng A (`survey2.ps1` → bản `survey4.ps1`). Mỗi cấu hình khôi phục trong `finally` và kiểm lại; **cả 10 `POSTCHECK` đều ra Physical 1080×2520 / 420, font 1.0, xoay 0/1**; không lần nào ADB rớt. Giá trị gốc ghi trước: size 1080×2520, density 420, font 1.0, `user_rotation=0`, `accelerometer_rotation=1`. `SONY_SCREEN_END`: `Display State=OFF` lúc 23:15:18 (chuỗi) và lúc 23:53:25 (cuối phiên). Có 3 lần xoay phải đặt lại (`ROTATION MISMATCH`, script tự thử lại, ảnh vẫn đúng).

### Bảng trước / sau theo lỗi

Đọc ảnh bằng mắt trên ảnh tổng hợp; số dòng là ước lượng (±0,5 dòng). Ảnh “sau” có thêm khác biệt do app khởi động lại sau khi cài (mini-player ở thẻ “Nghe tiếp”, khung Đang phát trống “Chọn một bài để phát”): đây là trạng thái, không phải lỗi bố cục.

| Mã | Cấu hình / màn | Trước | Sau | Kết luận | Ảnh tổng hợp |
|---|---|---|---|---|---|
| UI-01 | M2 ngang (333 dp) Thư viện | ≈ 0,5 dòng | ≈ 1,5 dòng | Cải thiện rõ, vẫn thấp | `tong-hop-UI-01-ngang` |
| UI-01 | M3 ngang (381 dp) | ≈ 1,3 | ≈ 2 | Cải thiện | idem |
| UI-01 | F3 M3 ngang | **0** | ≈ 1,5 | Hết “Chặn dùng” | idem |
| UI-01 | F3 M6 ngang (486 dp) | ≈ 1 | ≈ 2 | Cải thiện | idem |
| UI-01 | M1 dọc (537 dp) | ≈ 1 | ≈ 2,5 | Cải thiện rõ | `tong-hop-UI-01-02-doc` |
| UI-01 | M5 dọc (699 dp) | ≈ 2 | ≈ 4 | Cải thiện rõ | idem |
| UI-02 | M3 dọc, có bàn phím | ≈ 2,5 | ≈ 3 | Cải thiện nhẹ | idem |
| UI-02 | M5 dọc | ≈ 0–1 | ≈ 1 | Cải thiện nhẹ | idem |
| UI-02 | **M1 dọc** | 0 | **0** | **CHƯA HẾT** (vẫn không thấy kết quả) | idem |
| UI-02 | **F3 M3 dọc** | 0 | ≈ 0,5 | **CHƯA HẾT** | idem |
| UI-02 | **F3 M6 dọc** | 0 | ≈ 0,5 | **CHƯA HẾT** | `tong-hop-UI-02-F3M6` |
| UI-03 | M3 dọc, Hàng đợi toàn màn | không lề ngang | **không lề ngang** (tiêu đề `x=0`, thẻ và nút “Thư viện” chạm mép; khoảng trên không đổi) | **CHƯA SỬA ĐƯỢC trên máy thật** | `tong-hop-UI-03-05` |
| UI-04 | Hai khung M2, M3, M6, M7, M8 | cột Đang phát bắt đầu `x=0` | có lề trái, chữ/nút cách mép | Đã sửa | `tong-hop-UI-04` |
| UI-05 | M3 dọc, Thư viện | dòng “Tạm dừng” lẻ đè mép trên list | không còn dòng lẻ, “Phạm vi…” cách list rõ | Đã sửa (ở trạng thái “Nghe tiếp”) | `tong-hop-UI-03-05` |
| UI-05 | M3 dọc, Đang phát khi tạm dừng | “Tạm dừng” sát hàng chip | cách chip thêm ≈ 8 px | Cải thiện nhỏ, **chủ dự án xem** | idem |
| UI-05 | M3 dọc, Nguồn | dòng “Tạm dừng” lẻ | không còn (nhưng trạng thái thẻ khác nên **chưa so cùng điều kiện**) | Chưa kết luận chặt | idem |
| UI-07 | F2 M3 dọc | chip “Nguồn” cắt nửa | chip bọc xuống dòng, đủ chữ | Đã sửa | `tong-hop-UI-07-08` |
| UI-07 | F3 M3 dọc: chip, ô tìm, “Phát ngẫu nhiên”, menu ⋮ | chip mất, “Phát ngẫu”, “Thêm vào Playli…” | chip bọc dòng, “Phát ngẫu nhiên” đủ, menu đủ 5 mục không cắt; ô tìm rút bằng dấu “…” (“Tìm trong thư…”) | Đã sửa; hint ô tìm vẫn rút gọn | idem |
| UI-08 | F3 M6 dọc, tab dưới | “Thư”, “Telegra” cắt | chỉ còn biểu tượng, không còn nhãn cắt | Đã sửa | idem |
| UI-06 | M3 ngang, ô tìm | bàn phím toàn màn | **còn nguyên** (ảnh `M3-ngang-4-tim`) | Đề xuất, không sửa theo quyết định | — |

### Hồi quy

- **M3 dọc/ngang** (Thư viện, menu ⋮, Nguồn, Cài đặt): không thấy cắt, chồng hay mất nút; ảnh `tong-hop-hoi-quy-M3`. Cài đặt của M1/M5/M6 dọc và F2/F3 dọc không chụp được lại vì sau khi cuộn, nút bánh răng không còn trong cây UI (script ghi `NOTFOUND cai-dat`); chỉ M3, M2, M6 ngang, M7, M8, F3 M3/M6 ngang có trang Cài đặt.
- **M9 chia đôi (Gọn): KHÔNG chụp lại.** Mở Recents thấy thẻ của một phiên khác đang ở trạng thái phát (xem sự cố), nên không chia đôi để khỏi chạm bản/phiên khác. Gọn chỉ được kiểm bằng test JVM `MeloLayoutMatrixTest` 7/7 xanh ở chặng B. **Phần UI-01/UI-02 có thể làm Gọn xê dịch chưa được xác nhận bằng ảnh thật.**
- **M8:** lần này script chụp đủ ≈ 8 ảnh (có cả Nguồn, Cài đặt) nhưng tôi chỉ xem ảnh Thư viện cho UI-04; các ảnh còn lại của M8 chưa được rà, không dùng làm kết luận.

### Sự cố cần chủ dự án biết (Sony)

1. **Nhạc bị phát nhầm ≈ 30 giây khi màn tắt.** Để mở Đang phát/Hàng đợi (UI-03, UI-05) tôi buộc phải chạm “Nghe tiếp” để bài #140 đang dừng tiếp tục, rồi tạm dừng ngay (kế hoạch cấm phát bài mới; đây là cùng bài, như một lần chạm ở chặng A). Sau đó tôi đóng màn Đang phát bằng một lần chạm ở tọa độ cố định (105,142) khi màn đã cuộn xuống, nút “Đảo” rơi vào vị trí đó. Hậu quả quan sát được lúc 23:51 (màn tắt): phiên `app.melovault.player` ở `state=3` (đang phát). Tôi tạm dừng ngay bằng `keyevent 127`. **Bài hiện tại không còn là #140 mà là “#3 Up With Wuan C” (1:00:38), dừng ở 01:51; hàng đợi 200 bài, thứ tự giống lúc trước.** Chưa chắc chắn nguyên nhân chính xác (nghi: lần chạm “Đảo” hoặc phím BACK từ màn Hàng đợi). Không khôi phục được vị trí gốc (#140 ở 02:50, trước đó 01:30) mà không phát thêm nhạc; chủ dự án chọn lại bài #140 nếu muốn.
2. **Hàng đợi đổi.** Chặng A ghi Hàng đợi 1 bài; sau khi cài lại APK và tiếp tục phát, app tự dựng hàng đợi 200 bài quanh bài #140 (xem ảnh “Hàng đợi SAU”). Đây là hành vi app khi tiếp tục phát từ thư viện, không phải do tôi chọn bài; vẫn ghi là thay đổi trạng thái.
3. Không đăng xuất, không uninstall/`pm clear`, không máy bay, không đổi tài khoản. Không thao tác trong bản `.open`.

### Việc còn lại / rework đề xuất (chủ dự án giao, không tự sửa)

- **UI-03:** sửa chưa có hiệu lực (JVM test chỉ chứng minh hàm padding). Cần xem lại vì sao `setPadding` trong listener insets không tới màn Hàng đợi toàn màn (vị trí view/containter khác?).
- **UI-02:** cửa sổ thấp có bàn phím (M1 dọc, F3 M3/M6 dọc) vẫn không thấy kết quả; cần thu nhỏ/cuộn phần đầu hoặc mini-player khi ô tìm đang nhập.
- **UI-01:** M2 ngang (333 dp) còn ≈ 1,5 dòng; chấp nhận hay muốn thêm.
- **Chưa kiểm:** Gọn M9 bằng ảnh; Đang phát/Hàng đợi ở M1/M2/M5–M8; hộp thoại mời Car Mode; âm thanh thật (**CHƯA XÁC NHẬN ÂM THANH THỰC**); tablet/máy gập thật (chỉ mô phỏng trên Sony).
