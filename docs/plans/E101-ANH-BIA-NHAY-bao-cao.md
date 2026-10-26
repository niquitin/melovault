# Báo cáo Sonnet — E101-ANH-BIA-NHAY (ảnh bìa dòng nhảy/đổi liên tục)

Ngày thực hiện: 26/10/2026. Phiên GỘP S0–S8. Kết luận của Sonnet: **code + test + build xong; QA Shanling bước 1–5 PASS (theo số đếm và ảnh); Sony bước 6 chỉ kiểm khói (Sony không ở Teal nên bộ tải ảnh không chạy) — CHƯA KIỂM đường MeloLibraryPane.** Quyết định PASS/REWORK thuộc Opus nghiệm thu và chủ dự án.

## 1. Git

| Mục | Giá trị |
|---|---|
| Nhánh | `dot/E101-ANH-BIA-NHAY` (cây sạch lúc bắt đầu, HEAD = `409466c` = commit kế hoạch; baseline `6ae510c`) |
| Commit 1 | `1047a22` E101 S2: luật chính sách (hoãn mờ, một nguồn, thứ tự trên-trước) + test |
| Commit 2 | `11c851a` E101 S3: loader bỏ ảnh mờ thừa, gửi dòng trên cùng trước |
| Commit 3 | `458b0fb` E101 S4: một nguồn ảnh mỗi dòng, mờ dần ảnh đầu tiên, đếm swap |
| Commit 4 | `00c4901` E101 S5: vc117 / `0.6.92-beta` (source của APK QA) |
| Commit 5 | báo cáo này (commit sau `00c4901`) |
| File production đổi | `MeloRowArtPolicy.kt` (+36), `MeloRowArtLoader.kt` (+37/−), `MeloComponents.kt`, `MeloCompactScreen.kt`, `MeloLibraryPane.kt`, `app/build.gradle` (vc116→117, 0.6.91→0.6.92) |
| File test | `MeloRowArtPolicyTest.kt` (+49, 5 test mới) |
| Không đụng | `TelegramPreviewActivity.kt`, `Scheduler`, `MAX_IN_FLIGHT`/`PREFETCH_AHEAD`/`CACHE_CAP_BYTES`, service, DB, Car Mode |

## 2. Đã làm trong code (đúng kế hoạch mục 3)

| Nguyên nhân | Sửa |
|---|---|
| A. mờ → nét dù nét đã có | `blurPlan`: ảnh nét đã trên đĩa → không giải mã ảnh mờ; phải tải → `scheduleBlur` hoãn 400 ms (hủy khi ảnh nét về, chạy ngay nếu ảnh nét lỗi, hủy hết ở `reset()`); chỉ có ảnh mờ → hiện ngay như cũ |
| B. thứ tự gửi ngược | `request()` gom job TDLib rồi gửi qua `sendOrder` (dòng trên cùng được hỏi sau cùng ⇒ Scheduler gửi trước) |
| C. hai nguồn ảnh | `getView` (Gọn và LibraryPane) dùng `rowPicture(peekRowArt, row.art)`: ảnh loader thắng, ảnh cũ chỉ bù khi loader chưa có |
| D. ảnh bật giật | `applyRowArt(..., animate)` trả `ArtChange` (NONE/APPEAR/SWAP); ảnh đầu tiên làm ô chữ cái mờ dần 150 ms; `rowArtShowing` để đếm `withArt` |
| Log | `MeloRowArt` thêm `appeared=` và `swapped=` (chỉ số đếm) |

Chấp nhận (kế hoạch đã nêu): ảnh nét tải lỗi mà không có ảnh mờ chạy được → dòng giữ chữ cái; file thumbnail trên đĩa giải mã lỗi → không còn ảnh mờ dự phòng.

## 3. Test và build trên Ni

| Lệnh | Kết quả |
|---|---|
| `gradlew :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL (1m56s) |
| Debug | tests=**2060**, skipped=**21**, failures=0, errors=0 (2055 nền + 5 mới) |
| Release | tests=**2060**, skipped=**21**, failures=0, errors=0 |
| `gradlew :app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL (1m04s). Build Ni không có Telegram config → **không dùng để cài** |
| `git diff --check` | sạch (exit 0) |

Test mới: `blurPlanSkipsTheBlurWhenTheSharpCoverIsOnDisk`, `rowPictureLoaderWinsLegacyFillsIn`, `artChangeNeverTakesAPictureAway`, `sendOrderPutsTheTopRowFirstOnTheScheduler` (dùng `Scheduler` thật: r0,r1,r2), `blurAndFadeTimingsAreTheContract` (400/150). Số đếm từ file XML `app/build/test-results`. Log Gradle ghi ngoài repo.

## 4. Build APK (Tiny) và gate cài

| Mục | Giá trị |
|---|---|
| Chuyển code | bundle đầy đủ `D:\Bots\_transfer\MeloVault_E101-ANH-BIA-NHAY_full_00c4901_20261026.bundle`, SHA-256 `ED760E372DD742E84C4D38AF4FFFE267A938D17B5094C6217C18743155CDE3B9` (Tiny đối chiếu khớp; lần chạy đầu hỏng do tôi gõ sai SHA trong script, không phải bundle) |
| Clone cô lập | `C:\Bots\MeloVault-E101-IsolatedQA` @ `00c4901`, sạch; `local.properties` chép nội bộ Tiny rồi xóa (nội dung không đọc, bản gốc không đổi) |
| Telegram config | `telegram.config.valid=true` (chỉ boolean) |
| Build Tiny | `assembleShareableDebug` BUILD SUCCESSFUL 2m48s |
| Verify APK | package `app.melovault.player`, versionCode **117**, `0.6.92-beta`; cert SHA-256 `eecd3df7…c3fce8` = cert đang cài trên Shanling và Sony |
| SHA-256 sau khi về Ni | khớp Tiny (`0EBA0E68…A6537CF`, 33 413 060 byte) |

## 5. Cài thiết bị

| Thiết bị | Gate | Kết quả |
|---|---|---|
| Shanling M3 Ultra, `30313860180939` (Ni) | model `Shanling M3 Ultra`/`Shanling`, serial `device`; cài vc116; cert khớp; vc117 > vc116; Media3 state=2 (PAUSED, không PLAYING) | `adb -s 30313860180939 install -r` **Success** → vc117 / 0.6.92-beta. Không dùng uninstall/pm clear/-d |
| Sony XQ-AS52, `QV725WEQ54` (Tiny) | model `XQ-AS52`/`Sony`; cài vc116; cert khớp cả hai; không PLAYING | `install -r` **Success** → vc117 / 0.6.92-beta |

## 6. QA đã tự làm

Ảnh/video ở `docs/plans/artifacts/E101-ANH-BIA-NHAY/` (git bỏ qua toàn thư mục, có tên bài/kênh nên không commit). Ảnh tổng hợp: **`00_tong_hop.png`** (1600×1087: ảnh ghép trước/sau bước 1 và 3, ảnh 2, 4, 5a–c, Sony). Kịch bản vuốt chuẩn theo kế hoạch (Gọn, Thư viện, Tất cả, đầu danh sách).

| # | Thiết bị | Hành động | Kỳ vọng | Thực tế | Kết quả | Tệp |
|---|---|---|---|---|---|---|
| 1 | Shanling vc116 (trước khi sửa) | Kịch bản vuốt chuẩn, quay 12 s | chỉ để so sánh | Ảnh điền không theo thứ tự, ô chữ cái → ảnh; logcat `visible=9/10 withArt=9/10 settled`, **không có bộ đếm swap** (vc116 chưa có). Ghi chú: app đã chạy nên bộ đệm ấm, **không bắt được** dòng đổi qua lại hai ảnh khác nhau trong ảnh ghép | đã ghi nhận | `01_shanling_vc116_truoc.mp4`, `…_sheet.png`, `…_log.txt`, `01_shanling_truoc_*.png` |
| 2 | Shanling vc117 | Mở app (Gọn → Thư viện → Tất cả → đầu), chờ 8 s, chụp | 8 dòng, dòng đầu có ảnh, không crash | 8 dòng đủ ảnh bìa, focus = `TelegramPreviewActivity` | **PASS** | `02_shanling_vc117_mo_app_095929.png` |
| 3 | Shanling vc117 | Vuốt chuẩn sau **khởi động nguội** (`am force-stop`, mở lại), logcat stream liên tục | không dòng nào có 2 ảnh khác nhau; ảnh điền từ trên xuống; tổng `swapped` ≤ 10% `appeared`; không FATAL/ANR | Tổng `appeared=15`, **`swapped=0`**; ảnh ghép: mỗi dòng chữ cái → ảnh **một lần**, rồi đứng yên; 0 FATAL/ANR | **PASS** | `03b_shanling_vc117_sau_nguoi.mp4`, `…_sheet.png`, `…_log.txt` |
| 4 | Shanling vc117 | Vuốt nhanh 10 lần về đầu, chờ 2 s, chụp | cột ảnh giống bước 2 | SSIM cột ảnh (84×840 px) giữa bước 2 và 4 = **1.000000** | **PASS** | `04_shanling_vc117_ve_dau_100129.png` |
| 5 | Shanling vc117 | Tab Telegram ↔ Thư viện ×3 (chờ 2 s), chụp mỗi lần | ô ảnh giống hệt qua 3 lần; `swapped=0` | SSIM 05a/05b/05c so bước 2 = 1.000000 mỗi lần; log trong lúc này **không có dòng refresh nào** (không ảnh mới) ⇒ `swapped=0` | **PASS** | `05a–c_*_thuvien_*.png`, `05a–c_*_tab_telegram_*.png`, `05_*_tab_log.txt` |
| 6 | Sony vc117 (Tiny) | Mở app, vuốt chuẩn, chụp | như bước 3 (đường `MeloLibraryPane`) | Sony đang ở **Điện thoại dọc không phải Teal** (ô chữ cái, không ảnh bìa), nên bộ tải ảnh không chạy: `MeloRowArt` = 0 dòng; không FATAL/ANR; vuốt bình thường | **CHƯA KIỂM** đường LibraryPane (chỉ khói PASS). Không đổi theme vì kế hoạch cấm đổi cài đặt UI | `01_sony_vc117_mo_app_100309.png`, `06_sony_vc117_sau_vuot_100537.png`, `06_sony_vc117_vuot.mp4` |

Bổ sung vòng 03 đầu tiên (`03_shanling_vc117_sau.*`): logcat dùng `logcat -d` sau cảnh quay nên vòng đệm 256 KiB đã tràn, chỉ còn 4 dòng (đều `appeared=0 swapped=0`). Vì số đếm không đủ tin cậy nên chạy lại thành vòng **03b** với logcat stream; vòng 03 chỉ giữ làm chứng cứ video/ảnh ghép. Khác biệt cần Opus biết: vòng 03 sau cài lần đầu, 03b sau `force-stop` (mở lạnh); `force-stop` không xóa dữ liệu hay cache.

Không làm: phát nhạc, đổi theme/cài đặt, bật/tắt mạng, đăng nhập lại. Không đo được trực tiếp: mờ dần 150 ms (chỉ ảnh tĩnh và video 30 fps), nhánh hoãn ảnh mờ 400 ms (chỉ có test chính sách; ảnh nét đều nằm trên đĩa hoặc tải rất nhanh nên chưa thấy `LATER` chạy thật).

## 7. Màn hình

| Thiết bị | Kết thúc |
|---|---|
| `SHANLING_SCREEN_END` | **OFF** — 10:02:15, `mScreenState=OFF`, `mWakefulness=Asleep` (keyevent 223 sau bước 5) |
| `SONY_SCREEN_END` | **OFF** — ~10:06, `Display State=OFF`, `mScreenState=OFF`, `mWakefulness=Dozing` (keyevent 223 trong `finally`) |

Sự cố nhỏ: lần chạy Sony đầu bị lỗi cú pháp script (PowerShell 5 không cho `&` sau pipeline) nên không chạy tới `finally`, màn Sony sáng thêm ~1,5 phút; đã sửa, chạy lại và tắt màn như trên. Không đổi timeout/độ sáng/khóa màn.
Video tạm `/sdcard/Download/e101_*.mp4` (Shanling) và `/sdcard/e101_sony.mp4` (Sony) đã xóa sau khi kéo về.

## 8. APK QA cần Opus nghiệm thu bàn giao

| Mục | Giá trị |
|---|---|
| Loại build | **Debug** (Tiny, ký chữ ký Preview, có Telegram config) — **QA CANDIDATE, chưa nghiệm thu** |
| Git commit source | `00c4901` (nhánh `dot/E101-ANH-BIA-NHAY`); báo cáo ở commit sau |
| Tệp | `MeloVault-E101-ANH-BIA-NHAY-00c4901-vc117-TinySigned-Debug.apk` |
| Đường dẫn Tiny | `C:\Bots\_qa_handoff\E101-ANH-BIA-NHAY\` (= `T:\_qa_handoff\E101-ANH-BIA-NHAY\`) |
| Bản sao Ni | `D:\Bots\_transfer\E101\` |
| Package | `app.melovault.player` |
| versionCode / versionName | **117** / `0.6.92-beta` |
| SHA-256 | `0EBA0E683D64F44085F2FD63187846BADE320264FD4B04E54626672D3A6537CF` (đã đối chiếu lại trên Ni) |
| Bytes | 33 413 060 |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (khớp app đang cài) |
| Telegram config | `true` (không in giá trị) |
| Đã cài | Shanling vc117 (M3 Ultra, đang cài); Sony vc117 (đang cài) |
| Gradle | 2060 test mỗi biến thể, 21 skipped, 0 lỗi; Debug/Release build OK |
| Chưa QA | Sony đường LibraryPane/Teal; Y700, S25, FYT; Tablet/Hai khung; nhánh ảnh mờ hoãn 400 ms trên máy; âm thanh (không liên quan); dòng đổi qua lại do nguyên nhân C chưa bắt được ở vc116 nên chưa chứng minh "trước có lỗi" bằng số |

## 9. Rủi ro / giới hạn

- Baseline vc116 trong ảnh ghép không tái hiện rõ lỗi "nhảy"; bằng chứng "sau" mạnh ở phía số đếm (`swapped=0`) hơn là so sánh với "trước".
- Mạng/ảnh hôm nay nhanh và nhiều ảnh nằm sẵn trên đĩa, nên tỷ lệ `swapped ≤ 10%` có thể dễ đạt hơn khi mạng chậm (khi đó ảnh mờ hoãn 400 ms sẽ hiện và đổi thành nét: tính vào `swapped`, đúng như kế hoạch).
- Cảm nhận "mượt" là chủ quan; chủ dự án nên xem `03b_*.mp4` hoặc tự vuốt trên Shanling.
- Rollback: revert `458b0fb` rồi `11c851a`; `1047a22` chỉ thêm hàm thuần; vc117 giữ để không downgrade.
