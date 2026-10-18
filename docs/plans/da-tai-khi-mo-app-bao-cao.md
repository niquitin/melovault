# Báo cáo Sonnet — da-tai-khi-mo-app (Beta109, 18/10/2026)

Kế hoạch: `docs/plans/da-tai-khi-mo-app.md`. Nhánh `feature/e93-03d-y700-offline-cockpit` (nhiệm vụ bắt đầu trước quy tắc nhánh `dot/<TASK_ID>`; không đổi lịch sử Git). Baseline `ba9fdf7`. Giữa phiên có hai commit tài liệu của quy trình mới (`b67c8db`, `aa10254`, tác giả "ChatGPT Documentation Review") chen vào nhánh; chỉ đụng tài liệu, cây sạch, không xung đột với phần code.

## 1. Commit
| Commit | Nội dung |
|---|---|
| `7b99528` | feat: số "đã tải" lúc mở app (số đã lưu + quét nền có điều kiện) |
| `9a1f129` | bump b109 (0.6.88-beta) |
| `d5409eb` | fix: khởi động luồng đếm ở đường mở app nguội (`loadDatabaseIndex`; bản đầu chỉ gắn ở `checkAutoRecovery` nên không chạy khi mở nguội) |
| `0cf7285` | log chẩn đoán `MeloVaultCount` (không tên bài); **đây là bản APK ứng viên** |

File đổi: `app/build.gradle`, `TelegramPreviewActivity.kt`, `MeloDownloadedCountPolicy.kt` (mới), `MeloDownloadedCountPolicyTest.kt` (mới, 9 test). Không đổi DB/schema, TDLib (ngoài `getMessageLocally` đã có), đăng nhập, đồng bộ, phát.

## 2. Làm gì
- Số đã lưu: SharedPreferences riêng `melovault_library_counts` (`downloaded_<accountId>`, `downloaded_at_<accountId>`; chỉ số, không tên/đường dẫn). Đọc ở luồng DB khi biết `userId`, gán `downloadedLibraryTotal`; xóa toàn bộ khi đăng xuất/đổi tài khoản (`clearDownloadedCount`). Lần quét chip "Đã tải" xong cũng lưu số.
- Quét nền (`runDownloadedCountScan`): chạy trên `offlineExecutor`, dùng `TelegramOfflineLibrary.scan` + `groups` + `resolveOfflineBatch` hiện có, không chạm UI; chỉ ghi `downloadedLibraryTotal` + prefs khi xong. Điều kiện (`MeloDownloadedCountPolicy.decide`): đăng nhập, có bài, tiền cảnh, không đồng bộ, không phát/mở bài, không tiết kiệm pin, chưa quét trong tiến trình, số lưu cũ ≥ 6 giờ hoặc chưa có. Trễ 20 s sau khi thư viện sẵn sàng; nếu bận (đồng bộ/đang phát) xét lại sau 60 s, tối đa 5 lần. Hủy khi: mở chip Đã tải, bắt đầu phát/bấm bài, ra nền (`onPause`), đổi/đăng xuất tài khoản.
- **Không làm** (theo cho phép của kế hoạch mục 4.3): cập nhật +1/−1 theo sự kiện tải xong/xóa (số nhóm theo tên trùng làm ước lượng lệch); thay vào đó lần quét kế tiếp ghi đè. Hệ quả: tải/xóa bài thì thẻ chỉ đúng sau lần quét nền kế (≤ 6 giờ nếu mở lại app) hoặc sau khi mở chip Đã tải.
- Bước 0: đo trên b108 (chip Đã tải, 25.107 bài): quét xong trong ≤ ~32 s kể từ lúc bấm (dump UI bị lỗi "could not get idle state" trong lúc quét nên độ chính xác ±5–15 s). **< 60 s** nên không thêm `Thread.sleep` giữa các trang và không bắt buộc đang sạc (ngưỡng kế hoạch). Số đo quét nền thật trong b109 trên Shanling: xem mục 6 (≈ 44 s cho 29.607 bài).

## 3. Test (lệnh trong CLAUDE.md)
| Nơi | Debug | Release |
|---|---|---|
| Ni (HEAD `0cf7285`) | 1.249 test, 21 skipped, 0 failed | 1.249, 21 skipped, 0 failed |
| Tiny (clone cô lập, ff tới `0cf7285`) | 1.249, 21 skipped, 0 failed | 1.249, 21 skipped, 0 failed |
Mốc cũ 1.240 → +9 test mới (`MeloDownloadedCountPolicyTest`). Test Tiny xanh lần đầu cả ba lượt build (không cần chạy lại `AudioStreamingNetworkRecoveryTest`). `assembleShareableDebug/Release` Ni xanh; `git diff --check` sạch. (Phiên QA Shanling này không sửa mã nên không chạy lại test; HEAD hiện `e14959e` chỉ khác `0cf7285` ở tài liệu.)

## 4. APK ứng viên
`MeloVault-Owner-Beta-v0.6.88-b109.apk` (30.390.830 byte), SHA256 `7989C5F9C298C41FC4F3F9C6D2F145A4B3663B7A35B5418DD4E83EFFC2465C3B`, package `app.melovault.player`, vc109 / `0.6.88-beta`, signer `eecd3df7…ce8` (apksigner trên Tiny và trên Ni, khớp). Telegram config có (chỉ kiểm có/không). Drive riêng (chủ dự án đã duyệt mặc định): `H:\My Drive\MeloVault-Owner-Beta-v0.6.88-b109-Y700-REVIEW` (SHA trên H: khớp). Còn hai bản trung gian cùng vc109 chỉ cài Sony, KHÔNG upload: SHA `B78AF29F…1C88` (`9a1f129`), `84A47E4D…F7C9` (`d5409eb`).

## 5. QA Sony XQ-AS52 (serial QV725WEQ54, qua Tiny; `adb install -r`, không gỡ/xóa dữ liệu)
Đạt:
- Cài đè b108→b109 thành công; lần mở đầu (chưa có số lưu) thẻ Telegram hiện "25.107 bài · đã tải 67" mà **không** mở chip Đã tải; 67 bằng số chip đo ở b108.
- Đóng hẳn rồi mở lại: sau 7 s thẻ đã có "25.109 bài · đã tải 67" (số lưu, không chờ).
- Mở lại khi số lưu còn mới: log `stage=skip reason=stored_fresh` sau ~20–26 s (không quét thừa).
- Không `ANR in`/`FATAL EXCEPTION`; rotation 1/0, density 420; nhạc để tạm dừng.
Chưa kiểm / không đạt bằng chứng **[CẦN XÁC NHẬN]**:
- Thời gian quét nền trong b109 và dòng `stage=done`: không bắt được trên Sony (log bị mất khi USB Sony rớt). Có bằng chứng gián tiếp số 67 đến từ quét nền. Lần quét đầu tiên (build `9a1f129`) log `stage=cancelled elapsedMs=7480` khi Sony bật hộp thoại MTP làm app tạm dừng (hủy đúng thiết kế).
- Mục 7.3 (bấm bài/cuộn trong lúc quét nền), 7.4 (tiết kiệm pin: `settings put global low_power 1` không làm `isPowerSaveMode` đổi nên không chứng minh được), 7.5 (không làm sự kiện): chưa kiểm được. Số lưu còn mới nên không thể ép quét nền lần nữa trên Sony mà không xóa dữ liệu app (cấm).
- Log `stage=flow`/`stage=arm` không thấy trong logcat Sony dù có trong APK (nghi buffer/tốc độ nhật ký Sony); `stage=skip` thì thấy. Trên Shanling các dòng này thấy bình thường khi lọc log kịp (mục 6).
- Lần chạy sớm (build `9a1f129`) im lặng 215 s: do lỗi gắn móc ở đường mở nguội (đã sửa ở `d5409eb`); một lần chạy `d5409eb` cũng im lặng khi USB Sony đang rớt (adb không đọc được) — chưa rõ có phải lỗi adb.
- USB Sony rớt nhiều lần khi cài và khi quét chip Đã tải ("device not found", hộp thoại MTP của Sony); nguyên nhân chưa rõ, lệnh cài báo lỗi giả nhưng bản cài đúng (kiểm SHA từ máy).

## 6. Shanling M3 Ultra (thiết bị chính) — ĐÃ CÀI VÀ QA (phiên tiếp nối 18/10/2026 23:50–23:57 +07)
**Xác minh trước khi cài** (Ni): `adb devices -l` thấy `30313860180939` trạng thái `device`; `getprop` = Shanling / Shanling M3 Ultra / Android 10; màn 768×1280, density 320 (cửa sổ app ≈ 360×566 dp). Đang cài vc107 / `0.6.86-beta`. APK ứng viên lấy từ Tiny qua ổ T: (chỉ đọc), chép vào thư mục scratchpad của phiên (không vào repo): `MeloVault-Owner-Beta-v0.6.88-b109-0cf7285.apk`, SHA256 `7989C5F9…2465C3B` (khớp mục 4), package `app.melovault.player`, vc109 / `0.6.88-beta` (> 107, không hạ phiên bản). `apksigner`: *CN=Android Debug*, SHA-256 chứng chỉ `eecd3df7…ce8` — **trùng** chứng chỉ APK đang cài (kéo `base.apk` vc107 từ Shanling về kiểm lại, cùng `eecd3df7…ce8`). Telegram config: không đọc, không đưa sang Ni; tính dùng được xác nhận bằng việc app tải thư viện 29.607 bài và quét TDLib chạy được trên máy.
**Cài:** đúng một lệnh `adb -s 30313860180939 install -r <APK>` → `Success`; `dumpsys package` sau cài: vc109 / `0.6.88-beta`. Không uninstall/pm clear/xóa dữ liệu/logout. Có dùng `am force-stop` (chỉ kết thúc tiến trình, không xóa dữ liệu) để mở lại app "nguội" và `KEYCODE_WAKEUP` để bật màn hình (hết giờ màn 15 s).
**Lưu ý trung thực:** báo cáo trước ghi Shanling từng đang phát nhạc (~94 phút) và vì thế hoãn cài. Phiên này **không kiểm tra lại trạng thái phát trước khi cài** (lúc kiểm sau cài, màn hình đang tắt và không còn phiên phát nào đang chạy của app); nếu chủ dự án đang nghe nhạc trên Shanling thì lần cài có thể đã ngắt nó. Cần ChatGPT/chủ dự án biết điều này.

**Kết quả** (ảnh trong `docs/plans/artifacts/da-tai-khi-mo-app/`, Git bỏ qua; không đưa ảnh lên đâu):
| # | Mục | Kết quả | Bằng chứng |
|---|---|---|---|
| 1 | Mở app, Thư viện | Đạt: danh sách "Tất cả · 29.607 bài" hiện sau ~8 s | `01-sau-wakeup.png` |
| 2 | Quét nền (tiến trình đầu, chưa có số lưu) | Đạt: log `stage=done groups=3 checked=29607 unknown=15 elapsedMs=43798` (**≈ 44 s**, dưới ngưỡng 60 s của kế hoạch). Lần mở đầu có `stage=skip reason=background` vì màn hình đang tắt (đúng thiết kế); sau khi màn bật: `flow` → `arm delayMs=20000` → quét | log lọc `MeloVaultCount` |
| 3 | Đóng hẳn (`force-stop`) và mở lại | Đạt về logic: sau 20 s `stage=skip reason=stored_fresh` (có số lưu còn mới, không quét lại). **Không thấy số trên màn hình** (xem phát hiện dưới) | log; `04`, `05` |
| 4 | Menu "Đã tải" | Đạt: "Đã tải · 3 bài", dòng phụ "3 nhóm / 7 bản offline đã xác minh · 29607 mục đã kiểm tra · 15 mục chưa xác minh được" — **bằng đúng** `groups=3 checked=29607 unknown=15` của quét nền | `08`, `09` |
| 5 | Phát bài đã tải | Đạt: bài ✓ "VietMix Đi Bay…" ở danh sách Tất cả phát offline (`stage=parsed … completed=true`, `PlaybackState state=3`, vị trí 5,3 s → 11,4 s → 20,3 s); đã tạm dừng (`state=2`) sau khi thử. Trong màn "Đã tải", bấm một nhóm chỉ mở hộp thoại "Có tại N nguồn/bản" (hành vi có sẵn, không thuộc nhiệm vụ này) | `14`; `10`–`12` |
| 6 | Log lỗi/ANR | Không `FATAL EXCEPTION`, không `ANR in`, kho `crash` rỗng; file ANR duy nhất trong `/data/anr` là ngày 08/10 | logcat |

**PHÁT HIỆN QUAN TRỌNG [CẦN CHATGPT/CHỦ DỰ ÁN QUYẾT]:** trên Shanling "đã tải N" **không hiện ở đâu lúc mở app**. Cửa sổ ≈ 360×566 dp rơi vào bố cục **Gọn** (`MeloScreenPolicy.chooseLayout`: rộng < 600 dp và cao < 700 dp), `newLayer` trả null nên `MeloLibraryPane`/hàng thẻ nguồn — nơi duy nhất dùng `downloadedLibraryTotal` qua `sourceSummaries()` — **không được tạo**. Màn Thư viện chỉ có "Tất cả · 29.607 bài", tab Telegram chỉ có "Tất cả nguồn · 29.607 bài" (không có "đã tải"); dump UI không có thẻ nguồn (`02`, `03`, `05`, `07`). Số đúng (3) chỉ thấy khi vào menu → "Đã tải" (quét chip, ~44 s). Nghĩa là tính năng chạy đúng (số lưu + quét nền + log + số khớp chip) nhưng **không có chỗ hiển thị trên bố cục Gọn của Shanling**, nên mục 1–2 của "Bác sĩ tự kiểm tra" trong nghiệm thu không thể đạt trên Shanling như đang viết; chỉ kiểm được thẻ trên bố cục Điện thoại/Tablet (Sony/Y700/S25). Không sửa mã (ngoài phạm vi phiên này); không đổi cỡ màn/density Shanling (là chỉnh hệ thống, cấm).

**Chưa kiểm trên Shanling:** bấm bài/cuộn *trong lúc* quét nền (quét nền chỉ chạy một lần mỗi tiến trình khi số lưu cũ ≥ 6 giờ hoặc chưa có; số lưu còn mới nên không ép chạy lại được nếu không xóa dữ liệu); chế độ tiết kiệm pin (đổi `low_power` là chỉnh cài đặt hệ thống, không làm); mất mạng; đo pin/nhiệt. Trong lúc quét nền lần đầu tôi chỉ đổi sang tab Telegram và chụp ảnh — không giật rõ, không ANR, nhưng đó không phải phép thử tải nặng.
Ghi chú kỹ thuật: bộ đệm logcat Shanling chỉ 256 KiB và bị log TDLib tràn (mất trong ~1 phút), nên phải lọc `MeloVaultCount:W` bằng luồng nền; dòng `stage=start` của quét đầu tiên bị mất, `stage=done` có.

## 7. Bác sĩ tự kiểm tra (3–6 thao tác)
1. Y700/S25 hoặc Sony (bố cục Điện thoại/Tablet): cài b109 đè b108, mở app → Thư viện: thẻ Telegram hiện "N bài" và, sau tối đa ~1 phút, "· đã tải X".
2. Đóng hẳn app rồi mở lại: thẻ có "đã tải X" ngay.
3. Bấm chip "Đã tải": số "(X)" trên chip bằng số trên thẻ.
4. Khi vừa mở app, bấm phát một bài và cuộn danh sách: không giật, nhạc không ngắt.
5. Shanling (đã cài b109): mở Thư viện, chờ ~1 phút, menu ≡ → "Đã tải": thấy "Đã tải · 3 bài"; quay lại "Tất cả", bấm bài có dấu ✓: phát được. **Lưu ý:** Shanling (bố cục Gọn) không có thẻ nguồn nên không có "đã tải N" trên thẻ — cần chủ dự án/ChatGPT quyết nơi hiển thị.

Đã dừng, chờ ChatGPT nghiệm thu.
