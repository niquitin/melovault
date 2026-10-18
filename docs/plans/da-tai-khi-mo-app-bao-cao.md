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
- Bước 0: đo trên b108 (chip Đã tải, 25.107 bài): quét xong trong ≤ ~32 s kể từ lúc bấm (dump UI bị lỗi "could not get idle state" trong lúc quét nên độ chính xác ±5–15 s). **< 60 s** nên không thêm `Thread.sleep` giữa các trang và không bắt buộc đang sạc (ngưỡng kế hoạch). Số đo thật của quét nền trong b109 **chưa có** (xem 5).

## 3. Test (lệnh trong CLAUDE.md)
| Nơi | Debug | Release |
|---|---|---|
| Ni (HEAD `0cf7285`) | 1.249 test, 21 skipped, 0 failed | 1.249, 21 skipped, 0 failed |
| Tiny (clone cô lập, ff tới `0cf7285`) | 1.249, 21 skipped, 0 failed | 1.249, 21 skipped, 0 failed |
Mốc cũ 1.240 → +9 test mới (`MeloDownloadedCountPolicyTest`). Test Tiny xanh lần đầu cả ba lượt build (không cần chạy lại `AudioStreamingNetworkRecoveryTest`). `assembleShareableDebug/Release` Ni xanh; `git diff --check` sạch.

## 4. APK ứng viên
`MeloVault-Owner-Beta-v0.6.88-b109.apk` (30.390.830 byte), SHA256 `7989C5F9C298C41FC4F3F9C6D2F145A4B3663B7A35B5418DD4E83EFFC2465C3B`, package `app.melovault.player`, vc109 / `0.6.88-beta`, signer `eecd3df7…ce8` (apksigner trên Tiny và trên Ni, khớp). Telegram config có (chỉ kiểm có/không). Drive riêng (chủ dự án đã duyệt mặc định): `H:\My Drive\MeloVault-Owner-Beta-v0.6.88-b109-Y700-REVIEW` (SHA trên H: khớp). Còn hai bản trung gian cùng vc109 chỉ cài Sony, KHÔNG upload: SHA `B78AF29F…1C88` (`9a1f129`), `84A47E4D…F7C9` (`d5409eb`).

## 5. QA Sony XQ-AS52 (serial QV725WEQ54, qua Tiny; `adb install -r`, không gỡ/xóa dữ liệu)
Đạt:
- Cài đè b108→b109 thành công; lần mở đầu (chưa có số lưu) thẻ Telegram hiện "25.107 bài · đã tải 67" mà **không** mở chip Đã tải; 67 bằng số chip đo ở b108.
- Đóng hẳn rồi mở lại: sau 7 s thẻ đã có "25.109 bài · đã tải 67" (số lưu, không chờ).
- Mở lại khi số lưu còn mới: log `stage=skip reason=stored_fresh` sau ~20–26 s (không quét thừa).
- Không `ANR in`/`FATAL EXCEPTION`; rotation 1/0, density 420; nhạc để tạm dừng.
Chưa kiểm / không đạt bằng chứng **[CẦN XÁC NHẬN]**:
- Thời gian quét nền trong b109 và dòng `stage=done`: không bắt được (log bị mất khi USB Sony rớt). Có bằng chứng gián tiếp số 67 đến từ quét nền. Lần quét đầu tiên (build `9a1f129`) log `stage=cancelled elapsedMs=7480` khi Sony bật hộp thoại MTP làm app tạm dừng (hủy đúng thiết kế).
- Mục 7.3 (bấm bài/cuộn trong lúc quét nền), 7.4 (tiết kiệm pin: `settings put global low_power 1` không làm `isPowerSaveMode` đổi nên không chứng minh được), 7.5 (không làm sự kiện): chưa kiểm được. Số lưu còn mới nên không thể ép quét nền lần nữa trên Sony mà không xóa dữ liệu app (cấm).
- Log `stage=flow`/`stage=arm` không thấy trong logcat dù có trong APK (nghi buffer/tốc độ nhật ký Sony); `stage=skip` thì thấy. Không ảnh hưởng chức năng; chưa rõ nguyên nhân.
- Lần chạy sớm (build `9a1f129`) im lặng 215 s: do lỗi gắn móc ở đường mở nguội (đã sửa ở `d5409eb`); một lần chạy `d5409eb` cũng im lặng khi USB Sony đang rớt (adb không đọc được) — chưa rõ có phải lỗi adb.
- USB Sony rớt nhiều lần khi cài và khi quét chip Đã tải ("device not found", hộp thoại MTP của Sony); nguyên nhân chưa rõ, lệnh cài báo lỗi giả nhưng bản cài đúng (kiểm SHA từ máy).

## 6. Shanling M3 Ultra (thiết bị chính theo quy trình mới) — BLOCKED (chưa cài)
`adb devices -l` trên Ni thấy serial `30313860180939` (Shanling M3 Ultra, Android 10, 768×1280, density 320), cài vc107/0.6.86-beta; APK ứng viên cùng chữ ký `eecd3df7…ce8`, không hạ phiên bản. **Không cài** vì lúc kiểm Shanling đang phát nhạc (`state=3`, vị trí ~94 phút, app ở nền): `adb install -r` sẽ tắt tiến trình app và ngắt nhạc chủ dự án đang nghe. Cần chủ dự án tạm dừng nhạc hoặc đồng ý; sau đó cài bằng đúng một lệnh `adb -s 30313860180939 install -r <APK>`. Lưu ý: bố cục Gọn của Shanling **không có hàng thẻ nguồn** (Beta108), nên "đã tải N" chỉ xem được nếu Gọn có hiển thị khác — mục "Bác sĩ tự kiểm tra" của nghiệm thu nói thẻ Telegram trên Shanling; cần ChatGPT/chủ dự án xác định lại nơi xem số. Kiểm được trên Shanling: không crash/ANR, quét nền không làm giật (3GB RAM), log `MeloVaultCount`.

## 7. Bác sĩ tự kiểm tra (3–6 thao tác)
1. Y700/S25 hoặc Sony (bố cục Điện thoại/Tablet): cài b109 đè b108, mở app → Thư viện: thẻ Telegram hiện "N bài" và, sau tối đa ~1 phút, "· đã tải X".
2. Đóng hẳn app rồi mở lại: thẻ có "đã tải X" ngay.
3. Bấm chip "Đã tải": số "(X)" trên chip bằng số trên thẻ.
4. Khi vừa mở app, bấm phát một bài và cuộn danh sách: không giật, nhạc không ngắt.
5. Shanling (sau khi cài): mở Thư viện, phát bài, xem app không treo/không nóng bất thường khi vừa mở.

Đã dừng, chờ ChatGPT nghiệm thu.
