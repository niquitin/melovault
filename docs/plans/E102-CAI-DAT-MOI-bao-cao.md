# Báo cáo Sonnet — E102-CAI-DAT-MOI

## Phiên 1 — code + test trên Ni (S0–S6), 26/10/2026

**Trạng thái phiên 1: XONG phần code và JVM test. Chưa build Tiny, chưa cài máy, chưa QA thiết bị (thuộc phiên 2).**

### 1. Git

| Mục | Giá trị |
|---|---|
| Nhánh | `dot/E102-CAI-DAT-MOI` (cây sạch lúc bắt đầu, không agent khác) |
| Nền / commit kế hoạch | `ac53c4e` / `cedcab9` |
| S1 | `27c2827` MeloSettingsPolicy + 28 test |
| S2 | `5a75ccb` `TelegramSyncCoordinator.isPaused()` + 1 test |
| S3 | `9d331cb` MeloOverflowMenu + nối vào `showLibraryOverflow` |
| S4 | `4c8ef8b` viết lại `MeloUiLabActivity` thành Cài đặt (trang chính + 3 trang con) |
| S5 | `39f8e16` kết quả đăng xuất, `onResume` đọc lại preset/nguồn, đổi nhãn |
| S6 | commit chứa bump vc118 / `0.6.93-beta` và báo cáo này (xem `git log -1`) |

### 2. File đổi (đúng mục 4 của kế hoạch)

| File | Nội dung |
|---|---|
| mới `MeloSettingsPolicy.kt` | Thuần Kotlin: `Page`, `syncState/syncCard`, `menuItems`, `homeSummary`, `presetVisible`, `compactHint`, `bandHint`, `SHORT_INFO_LABELS` + `shortInfo`, `formatSyncTime`, `accountSubtitle/accountSyncLine` |
| mới `MeloOverflowMenu.kt` | `PopupWindow` tự dựng: thẻ đồng bộ + 5 hàng + Cài đặt; chỉ vẽ và báo callback |
| `MeloUiLabActivity.kt` | `render()` theo `page`; `renderHome/Appearance/Devices/Advanced`; Back; `EXTRA_SIGNED_IN`, `EXTRA_PAGE`, `RESULT_LOGOUT`; giữ tên lớp |
| `TelegramPreviewActivity.kt` | `showLibraryOverflow` dùng menu mới; `settingsResult`; `uiLabIntent()` thêm `EXTRA_SIGNED_IN`; `onResume` đọc lại preset/nguồn; comment rollback 3 hàm cũ; câu gợi ý Car Mode trỏ "⋮ → Cài đặt → Thiết bị & màn hình" |
| `TelegramSyncCoordinator.kt` | chỉ thêm getter `isPaused()` |
| `MeloCompactScreen.kt`, `MeloPhoneScreen.kt`, `MeloComponents.kt` | đổi nhãn: "⚙ Cài đặt", "Thêm… (đồng bộ, nguồn)", `contentDescription` "Cài đặt" |
| `app/build.gradle` | vc117 → **vc118**, `0.6.92` → **`0.6.93`** (`-beta`) |
| test | `MeloSettingsPolicyTest.kt` (mới), `TelegramSyncCoordinatorTest.kt` (+1) |

Không đụng: manifest, signing, drawable, `MeloSyncSettingsActivity`, `MeloUiPreviewActivity`, Car Mode (trừ nhãn), logic đồng bộ/TDLib/DB/Media3/service.

### 3. Test và build (Ni, log ở scratchpad, chỉ ghi dòng tổng kết)

| Lệnh | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL. Debug: **2089** test, 21 bỏ qua, 0 lỗi. Release: **2089**, 21 bỏ qua, 0 lỗi |
| `:app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL |
| `git diff --check` | sạch |

2089 = 2060 nền + 28 (`MeloSettingsPolicyTest`) + 1 (coordinator). Số bỏ qua giữ 21 như nền.
APK build trên Ni **không có Telegram config** nên **không dùng để cài/đăng nhập**; APK QA phải build ở Tiny (phiên 2).

### 4. Chức năng

| Mục kế hoạch | Trạng thái |
|---|---|
| Menu ⋮ còn 6 phần (thẻ đồng bộ + Tin đã lưu, Nhạc trên máy, Nhóm/kênh/bot, Mở tệp nhạc, Cài đặt) | Xong (mã + test policy), chưa thấy trên máy |
| Thẻ đồng bộ 5 trạng thái, nút giữ điều kiện `auth.isReady()` và `onManualScanRequested()` | Xong; nút Tạm dừng/Tiếp tục chạy đúng thân mục 4 cũ |
| Cài đặt trang chính: thẻ tài khoản, 5 nhóm, chân trang `VERSION_NAME · vcVERSION_CODE` | Xong |
| Giao diện & màu sắc: Kiểu thư viện, 3 kiểu cũ (chỉ khi Cũ), Kiểu màu, công tắc ảnh bìa, nguồn List/Bento, Màn Đang phát | Xong; mỗi lựa chọn gọi đúng hàm save và prefs cũ |
| Thiết bị & màn hình: Gọn (Tự động/Luôn bật/Tắt + thử khung), Car Mode (Tắt/Bật + 2 khung thử), Chừa chỗ ••• | Xong |
| Nâng cao: 6 dòng + "Xem tất cả số đo", Sao chép luôn đủ, Giả lập, xem trước thành phần | Xong |
| Back: trang con → trang chính, trang chính → đóng; `page` giữ khi xoay | Xong (`OnBackPressedCallback`, `onSaveInstanceState`) |
| Đăng xuất: hộp xác nhận chữ cũ → `RESULT_LOGOUT` → host kiểm `auth.isReady()` rồi `logoutUser()` | Xong |
| `onResume` đọc lại preset/nguồn, chỉ áp dụng khi khác, không ghi lại | Xong |
| Khung "Xem trước" của canvas, 2 hàng thông tin đồng bộ | Cố ý bỏ (theo kế hoạch) |

### 5. Điểm lệch so với kế hoạch (cần Opus nghiệm thu biết)

1. **Trạng thái "đã tạm dừng".** Kế hoạch (mục 2) viết `isSyncing` vẫn true sau `pauseSync()`. Mã thật: `pauseSync()` đặt `isSyncing = false` và `isPaused = true` (`TelegramSyncCoordinator.kt`, hàm `pauseSync`). Vì vậy bảng "đang chạy / đã tạm dừng" của kế hoạch không khớp mã. Sửa tối thiểu trong policy: `paused` được ưu tiên trước `syncing` (`isPaused()` đúng → thẻ "Đã tạm dừng đồng bộ", nút Tiếp tục + Quét toàn bộ). Có test `pausedWinsEvenIfTheBusyFlagIsStillSet`. Không đổi logic coordinator. Nhãn cũ "Tạm dừng đồng bộ/Tiếp tục đồng bộ" thực ra vốn đúng; điều kế hoạch gọi là lỗi nhãn không có thật, nhưng thẻ mới vẫn đúng ở cả hai cách hiểu.
2. **Đang chạy** dùng `globalScanLoading || coordinator.isBusy()`, giống nguồn `syncing` mà `TelegramPreviewActivity` đã dùng cho dòng trạng thái thư viện; bấm Tạm dừng vẫn chạy thân mục 4 cũ (chỉ gọi `pauseSync()` khi coordinator đang bận).
3. "Công tắc" ảnh bìa và "Xem như Car Mode" vẽ bằng hàng có viên BẬT/TẮT (không dùng SwitchCompat) để không thêm style/drawable.
4. Kích thước và màu của menu ⋮ lấy từ `MeloUiLabActivity.settingsSizes()` (cùng quy tắc UiLab cũ: Gọn trên máy nhỏ, nếu không thì phone/tablet), kể cả khi mở từ Car Mode.
5. Câu hỏi "Bật Car Mode?" từng ghi "⋮ → Cài đặt giao diện"; đổi thành "⋮ → Cài đặt → Thiết bị & màn hình" vì tên cũ không còn.

### 6. Chưa kiểm chứng (JVM không chứng minh được giao diện)

- Hình dạng thật của popup mới và màn Cài đặt trên Shanling (Android 10), Sony, đặc biệt: popup không tràn, chữ không cắt, cuộn khi màn thấp, đổ bóng, bấm ngoài thì đóng.
- Back từ trang con về trang chính; xoay màn giữ trang; giữ vị trí cuộn sau khi đổi lựa chọn (`OnPreDrawListener`).
- `RESULT_LOGOUT` → `logoutUser()` (cố ý không chạy thật; phiên 2 chỉ mở hộp rồi Hủy).
- `onResume` áp preset Cockpit/Portrait/Classic và Bento khi quay lại thư viện; hai nhánh này chỉ có trong mã.
- Nút Tạm dừng/Tiếp tục khi đồng bộ thật (chỉ có unit test của policy và getter).
- Y700, S25, FYT, Car Mode trên máy thật; âm thanh không liên quan.

### 7. Rủi ro / ghi chú cho phiên 2

- Phiên 2: gửi bundle (SHA256) sang Tiny, build có Telegram config, kiểm package/vc118/cert, rồi mới `adb -s 30313860180939 install -r`. Máy đang vc117; vc118 không downgrade.
- Cần chụp ảnh "trước" của Cài đặt cũ (vc117) và ghi giá trị mọi lựa chọn trước khi cài; trả về giá trị ban đầu trong `finally`.
- Không bấm "Đăng xuất" (chỉ Hủy), không bấm Lấy bài mới / Quét toàn bộ / Tạm dừng / Tiếp tục, không phát nhạc.
- Màn Shanling/Sony: phiên 1 không đánh thức máy nào (`SHANLING_SCREEN_END`, `SONY_SCREEN_END`: không áp dụng ở phiên 1).
- Rollback theo thứ tự S5 → S4 → S3 (độc lập); S1–S2 chỉ thêm code thuần và getter; giữ vc118 để không downgrade.

### 8. APK QA cần Opus nghiệm thu bàn giao

**Chưa có.** Phiên 1 không build trên Tiny nên chưa có APK hợp lệ có Telegram config. Phiên 2 sẽ điền: Git commit, đường dẫn APK trên Tiny, package, versionCode/versionName, SHA-256, bytes, cert SHA-256, loại build, Telegram config true/false, kết quả test, kết quả ADB, điểm chưa QA.
