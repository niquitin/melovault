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

---

## Phiên 2 — build Tiny + QA thiết bị + bàn giao, 26/10/2026 11:25–11:37

**Kết luận phiên 2 (Sonnet): Shanling bước 1–5 PASS; Sony bước 6 PASS; không FATAL/ANR. Chờ Opus nghiệm thu và chủ dự án xem ảnh.** Chưa xác nhận âm thanh thật (không phát nhạc).

### 9. Build Tiny và gate cài

| Mục | Giá trị |
|---|---|
| Git trên Ni khi bắt đầu | nhánh `dot/E102-CAI-DAT-MOI`, HEAD `2f4d736`, cây sạch, 5 commit S1–S5 + S6 có đủ trên nhánh, `git diff --check` sạch |
| Bundle | `D:\Bots\_transfer\MeloVault_E102-CAI-DAT-MOI_full_2f4d736_20261026.bundle`, 12 765 802 byte, SHA-256 `CDCFA13356E5815AB33D375323EA75C8280EB5AECC3F98BF6496A2B41964F83C` (khớp sau khi chép sang Tiny) |
| Clone cô lập Tiny | `C:\Bots\MeloVault-E102-IsolatedQA` @ `2f4d736`, sạch; `local.properties` chép nội bộ Tiny rồi xóa (nội dung không đọc, bản gốc không đổi: `original unchanged=True`) |
| Build | `assembleShareableDebug` BUILD SUCCESSFUL 2m45s |
| Telegram config | `telegram.config.valid=true` (chỉ boolean) |
| Gradle test trên Ni (phiên 1) | Debug và Release đều 2089 test, 21 bỏ qua, 0 lỗi; phiên 2 không đổi mã nên không chạy lại |

### 10. APK QA cần Opus nghiệm thu bàn giao

| Mục | Giá trị |
|---|---|
| Nhãn | **QA CANDIDATE — CHƯA NGHIỆM THU** (Debug, ký bằng khóa debug Tiny, build Tiny) |
| Git commit nguồn | `2f4d736` (nhánh `dot/E102-CAI-DAT-MOI`) |
| Đường dẫn trên Tiny (đã xác minh tồn tại) | `C:\Bots\_qa_handoff\E102-CAI-DAT-MOI\MeloVault-E102-CAI-DAT-MOI-2f4d736-vc118-TinySigned-Debug.apk` |
| Bản sao trên Ni | `D:\Bots\_transfer\E102\MeloVault-E102-CAI-DAT-MOI-2f4d736-vc118-TinySigned-Debug.apk` (SHA-256 và byte khớp Tiny) |
| Package / version | `app.melovault.player` · versionCode **118** · versionName **0.6.93-beta** · native-code `arm64-v8a` |
| SHA-256 | `0F1DB45A23D82E20CD1F0124CCAC33FD7B7BB21218D6BB16737DE78EE143D414` |
| Bytes | 33 445 828 |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (bằng cert của bản vc117 đang cài trên Shanling và Sony) |
| Telegram config hợp lệ | true |
| Chưa làm | Không upload Drive, không công bố, không đổi quyền; Opus nghiệm thu tự kiểm toán |

### 11. Cài thiết bị (chỉ `adb install -r`)

| Thiết bị | Gate | Kết quả |
|---|---|---|
| Shanling M3 Ultra, Android 10, `30313860180939` (Ni) | model `Shanling M3 Ultra` / `Shanling`, trạng thái `device`; package đúng; đang vc117 (SHA `0EBA0E68…`, cert khớp) → vc118 không downgrade; media_session state=0 (không PLAYING); màn OFF, không ai đang dùng | `install -r` **Success** → vc118 / 0.6.93-beta |
| Sony XQ-AS52, Android 12, `QV725WEQ54` (Tiny) | model `XQ-AS52` / `Sony`, `device`; vc117; cert cài và cert APK đều khớp; không PLAYING (state 2 = PAUSED có sẵn) | `install -r` **Success** → vc118 / 0.6.93-beta |

Không dùng `uninstall`, `pm clear`, `-d`, logout, xóa dữ liệu/cache/TDLib, đổi tài khoản, airplane.

### 12. Giá trị ban đầu của Shanling (ghi trước khi cài, từ màn cài đặt cũ vc117) và khôi phục

| Lựa chọn | Trước | Sau QA |
|---|---|---|
| Kiểu thư viện | Mới | **Mới** (đổi thành Cũ ở bước 3 rồi trả lại; ảnh `03b`) |
| Kiểu giao diện cũ (preset) | Portrait Edge-to-Edge Pro | không đụng (menu cũ ghi ✓) |
| Hiển thị nguồn | Bento Grid | Bento Grid (không đụng; ảnh `03b`) |
| Kiểu màu / Tải ảnh bìa | Teal sáng / BẬT | không đụng |
| Màn Đang phát | Studio Deck | không đụng |
| Chừa chỗ ••• / Car Mode / Gọn / Giả lập / Xem như Car Mode | Tự động / Tắt / Tự động / Tắt / bỏ chọn | không đụng (đối chiếu ở trang chính và trang Thiết bị) |

Cách ghi lại: ảnh màn Cài đặt cũ `00_shanling_vc117_truoc_cai_dat_a…h`, `00_…_truoc_menu_them`. Không đọc file prefs trong máy (thử `run-as` bị bộ kiểm tra từ chối tạm thời nên bỏ).

### 13. QA UI đã tự làm (ảnh private ở `docs/plans/artifacts/E102-CAI-DAT-MOI/`, git bỏ qua cả thư mục)

Ảnh ghép: **`00_tong_hop.png`** (1600 × 1520, 8 ảnh có số bước).

| # | Thiết bị | Hành động | Kỳ vọng | Thực tế | Kết quả | Ảnh |
|---|---|---|---|---|---|---|
| 0 | Shanling vc117 (trước) | Mở menu lọc → Thêm… → Cài đặt giao diện (thử nghiệm), cuộn hết | ghi giá trị mọi lựa chọn | Menu cũ 11 mục có Đăng xuất, Giao diện: …✓, Hiển thị nguồn; trang cũ dài, số đo ngay đầu | ghi nhận | `00_shanling_vc117_truoc_*` |
| 1 | Shanling vc118 (Gọn) | Thư viện → ⋮ → "Thêm… (đồng bộ, nguồn)" | popup mới đủ 6 phần, không còn Đăng xuất/Giao diện/Hiển thị nguồn, chữ không cắt | Thẻ "Thư viện đã đồng bộ · Cập nhật lúc 11:30 · 29.842 bài" + Lấy bài mới/Quét toàn bộ; Tin đã lưu, Nhạc trên máy, Nhóm/kênh/bot, Mở tệp nhạc, đường kẻ, Cài đặt; nằm trong màn | **PASS** | `01_shanling_vc118_popup_moi_113058.png` |
| 2 | Shanling | Bấm Cài đặt; chụp; vuốt xuống; chụp | thẻ tài khoản, 5 nhóm đúng thứ tự, chân trang `0.6.93-beta · vc118` | Thẻ "Đã đăng nhập · 29.842 bài · 29 nguồn · Đồng bộ xong lúc 11:30"; Giao diện / Thư viện & đồng bộ (+ nhãn Thử nghiệm) / Thiết bị & màn hình / Nâng cao / Tài khoản; chân trang đúng | **PASS** | `02a…113112`, `02b…113115` |
| 3 | Shanling | Giao diện & màu sắc → Cũ → Mới → Back | Cũ hiện 3 kiểu; Mới ẩn; Back về trang chính | Cũ: Portrait ✓ / Cockpit / Cổ điển hiện ra; Mới: ẩn; **BACK hệ thống** về "Cài đặt", không thoát (`focus` vẫn `MeloUiLabActivity`) | **PASS** | `03a…113138`, `03b…113156`, `03c…113208` |
| 4 | Shanling | Thiết bị & màn hình → ← → Nâng cao → Xem tất cả số đo | "Tự động" được chọn, "Máy này đang dùng: Gọn (máy nhỏ)"; Nâng cao 6 dòng rồi bung đủ | Đúng; Nâng cao đúng 6 dòng (Bố cục thực tế, Rộng × cao, Cạnh ngắn, Mật độ, Kích thước, Máy + Android), sau nút bung đủ (Tấm nền … Máy, Inset…) | **PASS** | `04a…113211`, `04b…113227`, `04c…113237/113240` |
| 5 | Shanling | Đăng xuất Telegram → chụp hộp → **Hủy** → Back về thư viện | hộp đúng chữ cũ; vẫn đăng nhập | Hộp "Đăng xuất tài khoản Telegram?" / "Bài đã tải và phiên đăng nhập trên thiết bị sẽ thay đổi." / HỦY · ĐĂNG XUẤT. Bấm HỦY (415,733), không phải ĐĂNG XUẤT; còn ở Cài đặt, Back → thư viện 29.842 bài, vẫn đăng nhập | **PASS** | `05a…113303`, `05b…113318`, `05c…113327` |
| 6 | Sony XQ-AS52 vc118 (Điện thoại dọc, 1080×2520) | ⋮ → popup → Cài đặt → Back | như bước 1–2, popup trong màn | Popup nằm trọn trong màn (thẻ "Cập nhật lúc 11:34 · 25.332 bài"); Cài đặt đủ nhóm, "Màu Hiện tại" (Sony không dùng Teal); Back về thư viện; đã kiểm focus trước mỗi tap | **PASS** | `06a_sony_…_2_113552`, `06b_sony_…113555` |

Ghi chú: lần chạy đầu của bước 6 (script `e102_sony4`) không tap vì focus lúc đó là `PopupWindow` (popup còn mở) và regex của script chỉ nhận tên app; script rơi vào nhánh "không tap" và tắt màn đúng quy định, popup đóng theo `onPause`. Chạy lại bằng `e102_sony5` kiểm `mResumedActivity` rồi mới tap.

### 14. Log, Media3, màn hình

| Mục | Kết quả |
|---|---|
| logcat Shanling (xóa buffer trước QA, đọc sau QA, 3119 dòng) | `FATAL EXCEPTION` = 0; `ANR` = 0; 105 dòng `AndroidRuntime` đều từ lệnh `adb shell input`/`monkey`; một dòng `InputDispatcher … MeloUiLabActivity has no registered input channel` ở lúc đóng màn Cài đặt (thông báo hệ thống khi cửa sổ vừa đóng, không crash) |
| logcat Sony | FATAL = 0, ANR = 0 |
| Media3 | Shanling `state=0` đầu và cuối (không PLAYING); Sony `state=2` (PAUSED, vị trí cũ 90 184 ms, không đổi) và một session `state=7` lỗi Bluetooth có sẵn. Không phát nhạc. **CHƯA XÁC NHẬN ÂM THANH THỰC** |
| Đồng bộ | App tự đồng bộ nhẹ lúc mở (Shanling 29.840 → 29.842 bài, cập nhật 11:30; Sony 11:34). Mình không bấm Lấy bài mới / Quét toàn bộ / Tạm dừng / Tiếp tục |
| `SHANLING_SCREEN_END` | **OFF** — `mState=OFF`, `mWakefulness=Asleep`, xác nhận 11:33:42 và lại 11:36:40 (tắt bằng keyevent 223 sau khi làm xong, không giữ sáng lúc build) |
| `SONY_SCREEN_END` | **OFF** — `Display State=OFF`, `mWakefulness=Dozing`, xác nhận ~11:36 (script `finally`) và lại 11:36:43 |

### 15. Sự cố và điểm cần Opus biết

1. **Mình đã ghi đè hai tệp tạm cũ trên Tiny**: script build E102 tạo bằng `sed` từ script E101 chỉ đổi chữ hoa `E101→E102`, nên log/exit trỏ vẫn là `C:\Bots\_qa_temp\e101_build.log` và `e101_build.exit`; chúng bị ghi đè bằng log build E102. Đây là log tạm (không phải artifact trong repo, không phải APK); kết quả E101 vẫn ghi trong `docs/plans/E101-ANH-BIA-NHAY-bao-cao.md`. Không khôi phục được tệp cũ. Log E102 đọc được tại tệp đó (mã hóa UTF-16).
2. Các script Sony mới đều có tên riêng `e102_*` và ghi vào `C:\Bots\_qa_handoff\E102-CAI-DAT-MOI\sony\`.
3. `git bundle` dùng `dot/E102-CAI-DAT-MOI`; clone ở Tiny báo "remote HEAD refers to nonexistent ref" (bình thường với bundle không có HEAD), sau đó checkout nhánh đúng và HEAD `2f4d736`.
4. Màn Cài đặt cũ có "Thông tin màn hình" ngay đầu; bản mới đã đưa xuống trang Nâng cao đúng thiết kế.

### 16. Chưa kiểm chứng / giới hạn

- Âm thanh thật; nút Tạm dừng / Tiếp tục khi đồng bộ thật (không bấm theo yêu cầu); đăng xuất thật (cấm); Lấy bài mới / Quét toàn bộ.
- Đổi Cũ/Mới và Bento có hiệu lực **khi quay lại thư viện** (`onResume`): trên Shanling (Gọn) lựa chọn này không đổi màn nên chưa thấy tác dụng thị giác; Cockpit/Portrait/Classic và Bento chưa đổi thử trên máy thật (chỉ có đường lưu + mã `onResume`).
- Xoay màn giữ trang; Y700, S25, FYT, Car Mode, Tablet; Android 10 chỉ kiểm trên Shanling (Sony Android 12).
- Sony đang ở giao diện Màu Hiện tại, không phải Teal; không đổi cài đặt UI trên Sony.

### 17. Kết quả theo mục 8 của kế hoạch

| Tiêu chí | Kết quả |
|---|---|
| Test Debug + Release 0 lỗi, có test mới, build OK, `git diff --check` sạch, chỉ đổi file trong mục 4 | Đạt (phiên 1) |
| Shanling bước 1–5 PASS; Sony bước 6 PASS | Đạt |
| Mọi lựa chọn cũ đổi được và ghi đúng khóa prefs | Đạt qua mã + test policy + bước 3 (Cũ/Mới); các lựa chọn khác chưa bấm đổi trên máy |
| Không crash/ANR | Đạt |
| Chủ dự án xem ảnh hoặc cài vc118 và nói ĐẠT | **Chờ** |

**Tổng hợp: PASS về phía Sonnet, chờ Opus nghiệm thu và chủ dự án.**

Artifacts: `docs/plans/artifacts/E102-CAI-DAT-MOI/` (Ni, gồm `00_tong_hop.png`, `sony_logs/`); `C:\Bots\_qa_handoff\E102-CAI-DAT-MOI\` (Tiny: APK, ảnh và log Sony).
