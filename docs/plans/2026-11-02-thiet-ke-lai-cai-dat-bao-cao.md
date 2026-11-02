# 2026-11-02-thiet-ke-lai-cai-dat — Báo cáo thực thi

Thực thi: Claude Code (Sonnet 5.5), phiên Chặng 1 (S0–S8, Ni: code + test + commit), 02/11/2026. Chưa build APK, chưa dùng Sony (Ni không có thiết bị ADB), chưa upload Drive.
Nhánh `dot/2026-11-02-thiet-ke-lai-cai-dat`, worktree `D:\Bots\_worktrees\MeloVault-thiet-ke-lai-cai-dat`, baseline `fa4cd09`, commit kế hoạch `11201fc`.
Kế hoạch: [2026-11-02-thiet-ke-lai-cai-dat.md](2026-11-02-thiet-ke-lai-cai-dat.md) · Bàn giao: [2026-11-02-thiet-ke-lai-cai-dat-ban-giao.md](2026-11-02-thiet-ke-lai-cai-dat-ban-giao.md).

## 1. Bảng bước

| Bước | Kết quả | Ghi chú |
|---|---|---|
| S0 | Xong | Cây sạch, HEAD `11201fc`. Gate baseline: 200 suite, 2698 test, 21 skipped, 0 lỗi (Debug và Release), BUILD SUCCESSFUL 3m21s. Log `artifacts/.../gate_s0_baseline.log`. Lưu ý: tôi bắt đầu sửa file trong lúc gate chạy; gate chạy trước khi có thay đổi nào biên dịch vào (số test trùng đúng baseline). |
| S1 | Xong phần đọc mã; **CHƯA TÁI HIỆN trên thiết bị** | Xem mục 3. |
| S2 | Xong | `MeloSettingsPolicy` (Page, `fromName(name, devUnlocked)`, `DevTapCounter`, `attentionRows`, `homeSections`, `syncValue`…), `MeloAboutPolicy` mới, `SETTINGS_SUBTITLE_SHORT`. |
| S3 | Xong | Dòng “Car Mode · Đang bật” và “Đang thử khung giả lập · Tắt” luôn hiện ở HOME; trang `CAR`; hộp thoại Car Mode đổi thành “Có thể tắt lại trong ⋮ → Cài đặt → Car Mode.” |
| S4 | Xong (cần xem trên máy ở Chặng 2) | Khung mới: thanh tiêu đề cố định, nhóm thẻ có biểu tượng, vạch chia, thẻ Đăng xuất riêng, insets sửa. |
| S5 | Xong, **trừ email** | Giới thiệu, Quyền riêng tư, Giấy phép (+ `res/raw/third_party_notices.txt`), 7 chạm, trang Công cụ nhà phát triển. |
| S6 | Xong | Tách trang Màn Đang phát; Lời nhạc mô tả 1 dòng + hộp đồng ý; Đồng bộ ghi Bật/Tắt, đọc nền, vẽ lại ở `onResume`. |
| S7 | Xong (rà bằng đọc) | Mục 5. |
| S8 | Xong, gate xanh | Mục 6. Commit mã `091dc96`. |

## 2. CĐR1 — email góp ý (điểm dừng)

Prompt thực thi ghi “<chủ dự án điền, hoặc ghi chưa có>” và không có địa chỉ → coi là **chưa có**. `MeloAboutPolicy.FEEDBACK_EMAIL = ""`; trang Giới thiệu **không hiện dòng “Góp ý qua email”** cho tới khi có địa chỉ (hàm `feedbackAvailable()`); phần còn lại của luồng email (tiêu đề, thân thư, `mailto:`, dự phòng sao chép) đã viết và có test bằng địa chỉ giả. **Việc còn lại:** chủ dự án cho địa chỉ, điền hằng số, build lại. Không tự điền.

## 3. S1 — chẩn đoán “chữ bị thanh hệ thống che”

Không có thiết bị trong phiên này (`adb devices -l` trên Ni rỗng), nên **không có ảnh “trước”**; Q1 ở Chặng 2 phải chụp ảnh sau sửa, và nếu còn máy cho thấy lỗi cũ thì chụp cả hai.

Đọc mã tại `fa4cd09`:
- `targetSdk 36` (app/build.gradle) và theme `Theme.MeloVault.Shareable` không có `windowOptOutEdgeToEdgeEnforcement` → từ Android 15 cửa sổ luôn tràn viền; trên Android 12 (Sony) cửa sổ **không** tràn viền nên lỗi trong ảnh của chủ dự án khó có thể từ Sony — **máy trong ảnh chưa rõ**, cần hỏi/đối chiếu.
- Mã cũ gắn `setOnApplyWindowInsetsListener` lên chính `ScrollView`, đặt padding bằng systemBars, rồi `render()` gọi `setContentView(scroll)` với ScrollView **mới** mỗi lần chọn; không có `requestApplyInsets` sau khi thay view. Nếu hệ thống không gửi lại insets cho view thay thế, padding giữ 0 và nội dung nằm dưới thanh trạng thái/thanh điều hướng — khớp triệu chứng (thẻ tài khoản dưới đồng hồ, dòng cuối dưới 3 nút). **Đây là giả thuyết từ mã, chưa kiểm chứng.**
- `MeloSyncSettingsActivity` dùng đúng mẫu cũ (ScrollView + listener) — **không sửa ở đây** (ngoài phạm vi), ghi để cân nhắc cùng SB-1.

Cách sửa (chỉ trong `MeloUiLabActivity`, không đổi theme dùng chung):
1. `WindowCompat.setDecorFitsSystemWindows(window, false)` trong `onCreate` → cùng một hành vi trên mọi Android.
2. Listener insets gắn lên **view gốc ổn định**: padding trên/trái/phải cho gốc (thanh tiêu đề nằm dưới thanh trạng thái), padding dưới cho ScrollView với `clipToPadding=false` (dòng cuối cuộn lên khỏi thanh điều hướng).
3. `ViewCompat.requestApplyInsets(root)` sau mỗi `setContentView`.
4. `statusBarColor`/`navigationBarColor` = màu nền của bảng màu đang dùng (Teal sáng khác nền theme).

## 4. File đổi (so với `fa4cd09`)

| File | Thay đổi |
|---|---|
| `data/telegram/MeloUiLabActivity.kt` | Khung mới, 10 trang, 7 chạm, chống kẹt, đọc Bật/Tắt đồng bộ nền, hộp đồng ý Lời nhạc, mở GitHub, license. Giữ nguyên `companion` (đo màn hình, `settingsSizes`, `handleBandPx`, extras). |
| `data/telegram/MeloSettingsPolicy.kt` | Mở rộng như trên. `homeSummary` giữ nguyên chữ (đã không nhắc Màn Đang phát). |
| `data/telegram/MeloAboutPolicy.kt` | Mới. |
| `data/telegram/MeloLyricsUiPolicy.kt` | Thêm `SETTINGS_SUBTITLE_SHORT`; giữ `SETTINGS_SUBTITLE`, `CONSENT_TEXT`. |
| `data/telegram/TelegramPreviewActivity.kt` | **Chỉ** câu hộp thoại Car Mode (1 dòng). |
| `shareable/res/drawable/ic_{info,mail,code,description,shield,car,warning}.xml` | Mới, 7 vector Material (Apache-2.0, cùng nguồn `ic_tune`/`ic_settings`). Đặt trong `shareable/` nên Personal không bị ảnh hưởng. |
| `shareable/res/raw/third_party_notices.txt` | Mới: toàn văn BSD-3 ALAC + danh sách thành phần khác. |
| Test | `MeloSettingsPolicyTest` (+18 test), `MeloAboutPolicyTest` (mới, 9 test). |

Không đổi: package, signer, versionCode/versionName, service, Media3, DB, TDLib, `MeloSyncSettingsActivity`, các khóa SharedPreferences cũ. Khóa mới duy nhất: `dev_tools_unlocked` (Boolean, mặc định false) trong file `melovault_ui_lab`.

## 5. S7 — rà chuỗi và đối chiếu quyền riêng tư

- Tiêu đề trang: Cài đặt · Giao diện & màu sắc · Màn Đang phát · Car Mode · Giới thiệu · Quyền riêng tư · Giấy phép mã nguồn mở · Công cụ nhà phát triển · Thiết bị & màn hình · Nâng cao.
- HOME: GIAO DIỆN · THƯ VIỆN & ĐỒNG BỘ · CHÚ Ý (khi cần) · NHÀ PHÁT TRIỂN (khi đã mở khóa) · Giới thiệu · Đăng xuất Telegram.
- Toast 7 chạm: “Còn 3/2/1 lần nữa để bật Công cụ nhà phát triển”, “Đã bật Công cụ nhà phát triển”, “Công cụ nhà phát triển đang bật”.
- Lời nhạc: dòng phụ “Tìm lời trên LRCLIB khi mở tab Lời nhạc”; bật từ Cài đặt hiện hộp “Lời nhạc từ LRCLIB” với đúng `CONSENT_TEXT` của tab Lời nhạc, nút Đồng ý / Hủy; tắt thì lưu ngay như cũ.
- Đối chiếu từng câu Quyền riêng tư với mã (`grep` HTTP/URL, manifest, `build.gradle`):
  1. “Đăng nhập, danh sách kênh và tải nhạc đi thẳng giữa máy bạn và Telegram; không có máy chủ riêng” — **đúng cho flavor shareable**: `PERSONAL_BACKEND_ENABLED=false`, `CAR_API_*=""`; địa chỉ web duy nhất trong mã shareable là `lrclib.net` và liên kết `my.telegram.org`; `usesCleartextTraffic=false`. Phần backend Personal nằm trong `src/main` nhưng không có địa chỉ khi build shareable.
  2. “Yêu thích/Playlist chỉ đồng bộ qua chính tài khoản Telegram, chỉ khi bật” — khớp màn Đồng bộ (“Chỉ dùng cùng tài khoản Telegram và kênh đồng bộ đã có”, công tắc `consent.automatic`).
  3. Câu Lời nhạc — nguyên văn `SETTINGS_SUBTITLE`; `ShareableLyricsProvider` chỉ gửi `track_name`, `artist_name`, `album_name`, `duration`.
  4. “Không quảng cáo, không công cụ theo dõi hay thống kê” — danh sách `implementation` trong `app/build.gradle` chỉ có AndroidX, Material, Coil, Media3, WorkManager; không có SDK quảng cáo/analytics. **Giới hạn:** đây là bằng chứng từ phụ thuộc khai báo, chưa kiểm bằng bắt gói mạng.
- Giấy phép: lấy từ `THIRD_PARTY_NOTICES.md` + `app/build.gradle`, **không ghi số phiên bản**.
- Chưa kiểm: chữ không bị cắt ở cỡ Gọn / chữ lớn 1.3 / xoay ngang / 2 bảng màu — cần xem trên máy (Q2, Q8).

## 6. S8 — gate Ni

Chạy trên mã của commit `091dc96`. Log ở `artifacts/2026-11-02-thiet-ke-lai-cai-dat/`.

| Lệnh | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL 1m41s. Mỗi biến thể: 201 suite, **2725 test, 21 skipped, 0 lỗi** (baseline 2698 + 27 test mới). `gate_s8_tests.log` |
| `:app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL 1m18s. `gate_s8_assemble.log` |
| `-PmeloOpen=true :app:assembleShareableDebug` | BUILD SUCCESSFUL 15s. `gate_s8_assemble_open.log` |
| `:app:assemblePersonalDebug` | BUILD SUCCESSFUL 18s. `gate_s8_assemble_personal.log` |
| `git diff --check` | Sạch (chỉ cảnh báo LF→CRLF của Git trên Windows) |

APK build trên Ni **chỉ để kiểm biên dịch**: worktree không có `local.properties`/`secrets.properties`, nên không có Telegram config; **không cài, không bàn giao**. APK QA phải build ở Tiny (Chặng 2). Không đổi versionCode (vẫn 121).

## 7. Việc còn lại / rủi ro

- Chặng 2 (S9–S11): bundle → Tiny build Debug có Telegram config → Sony `QV725WEQ54` QA Q1–Q8 → Drive owner-only → hồ sơ.
- Email góp ý (CĐR1) chưa có; nguyên nhân thật của lỗi chữ bị che chưa tái hiện.
- Hiển thị thật (icon, khoảng cách, ripple trong thẻ bo góc, thanh tiêu đề cố định) **chưa nhìn bằng mắt**; chỉ biên dịch và test JVM.
- Trang Giao diện / Màn Đang phát / Thiết bị / Nâng cao giữ kiểu hàng cũ (choice row, nút chia đoạn) bên trong khung mới; chỉ HOME, Giới thiệu, Giấy phép, Công cụ và Car dùng thẻ biểu tượng mới.
