# 2026-11-02-thiet-ke-lai-cai-dat — Báo cáo thực thi

Thực thi: Claude Code (Sonnet 5.5), 02/11/2026. Chặng 1 (S0–S8, Ni: code + test + commit) rồi, theo lệnh “thực thi phiên tiếp theo” của chủ dự án kèm email, Chặng 2 (S9–S11: build Tiny, QA Sony, Drive, hồ sơ) **trong cùng hội thoại** (chủ dự án quyết định gộp; vẫn cùng một agent sửa code mỗi lúc). APK QA candidate: Debug vc121 `F376BCCB…77E7` build từ `d52fe43`.
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
| S8 | Xong, gate xanh | Mục 6. Commit mã `091dc96`; sau QA thêm `2242319` (email) và `d52fe43` (3 sửa nhỏ), gate chạy lại xanh. |
| S9 | Xong | Mục 7: bundle, build Tiny, cài Sony, QA Q1–Q7 (Q8 chưa kiểm). |
| S10 | Xong | Drive owner-only đã kiểm; [apk-test](2026-11-02-thiet-ke-lai-cai-dat-apk-test.md), [hình](2026-11-02-thiet-ke-lai-cai-dat-hinh-nghiem-thu.md). |
| S11 | Xong | Báo cáo, bàn giao, prompt sơ bộ. |

## 2. CĐR1 — email góp ý

Lúc đầu prompt không có địa chỉ nên `FEEDBACK_EMAIL` để trống và dòng email ẩn. Chủ dự án nhắn trong chat: **`ntdung.md@gmail.com`** (sẽ công khai trong app và repo) → điền vào `MeloAboutPolicy.FEEDBACK_EMAIL` (commit `2242319`), test đổi theo; dòng “Góp ý qua email” hiện. Trên Sony không có app email nên chỉ kiểm nhánh dự phòng (sao chép địa chỉ + “Đã sao chép địa chỉ email”); thân thư/tiêu đề chỉ kiểm bằng test JVM.

## 3. S1 — chẩn đoán “chữ bị thanh hệ thống che”

Không có thiết bị trong phiên này (`adb devices -l` trên Ni rỗng), nên **không có ảnh “trước”**; Q1 ở Chặng 2 phải chụp ảnh sau sửa, và nếu còn máy cho thấy lỗi cũ thì chụp cả hai.

Đọc mã tại `fa4cd09`:
- `targetSdk 36` (app/build.gradle) và theme `Theme.MeloVault.Shareable` không có `windowOptOutEdgeToEdgeEnforcement` → từ Android 15 cửa sổ luôn tràn viền; trên Android 12 (Sony) cửa sổ **không** tràn viền nên lỗi trong ảnh của chủ dự án khó có thể từ Sony — **máy trong ảnh chưa rõ**, cần hỏi/đối chiếu.
- Mã cũ gắn `setOnApplyWindowInsetsListener` lên chính `ScrollView`, đặt padding bằng systemBars, rồi `render()` gọi `setContentView(scroll)` với ScrollView **mới** mỗi lần chọn; không có `requestApplyInsets` sau khi thay view. Nếu hệ thống không gửi lại insets cho view thay thế, padding giữ 0 và nội dung nằm dưới thanh trạng thái/thanh điều hướng — khớp triệu chứng (thẻ tài khoản dưới đồng hồ, dòng cuối dưới 3 nút). **Đây là giả thuyết từ mã, chưa kiểm chứng.**
- `MeloSyncSettingsActivity` dùng đúng mẫu cũ (ScrollView + listener) — **không sửa ở đây** (ngoài phạm vi), ghi để cân nhắc cùng SB-1.

**Cập nhật sau khi có Sony (mục 7):** trên Sony (Android 12, 3 nút) màn mới hiển thị đúng, nội dung nằm trọn giữa thanh trạng thái và thanh điều hướng. Lỗi cũ **không tái hiện được ở máy này** (đúng dự đoán: Android 12 không bị tràn viền), nên **không chứng minh được** cách sửa trên máy Android 15+; cần chủ dự án xem trên máy đó (ví dụ S25).

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

Chạy trên mã của commit `091dc96`. Log nằm trong `docs/plans/artifacts/2026-11-02-thiet-ke-lai-cai-dat/` của worktree (thư mục artifacts bị `.gitignore`, nên log không vào Git).

| Lệnh | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL 1m41s. Mỗi biến thể: 201 suite, **2725 test, 21 skipped, 0 lỗi** (baseline 2698 + 27 test mới). `gate_s8_tests.log` |
| `:app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL 1m18s. `gate_s8_assemble.log` |
| `-PmeloOpen=true :app:assembleShareableDebug` | BUILD SUCCESSFUL 15s. `gate_s8_assemble_open.log` |
| `:app:assemblePersonalDebug` | BUILD SUCCESSFUL 18s. `gate_s8_assemble_personal.log` |
| `git diff --check` | Sạch (chỉ cảnh báo LF→CRLF của Git trên Windows) |

APK build trên Ni **chỉ để kiểm biên dịch**: worktree không có `local.properties`/`secrets.properties`, nên không có Telegram config; **không cài, không bàn giao**. Không đổi versionCode (vẫn 121).

**Gate chạy lại sau 2 commit sau QA** (`2242319` email, `d52fe43` 3 sửa nhỏ): mỗi lần Debug+Release 201 suite, **2725 test, 21 skipped, 0 lỗi**; assemble Shareable Debug/Release, `-PmeloOpen=true`, Personal Debug BUILD SUCCESSFUL; `git diff --check` sạch (`gate_s8b_*`, `gate_s8c_*`). Số test không đổi vì chỉ sửa test email và chuỗi.

## 7. S9 — build Tiny, cài và QA Sony

| Mục | Giá trị |
|---|---|
| Bundle → Tiny | `…_d52fe43_20261102.bundle` 13.777.588 B, SHA-256 `20E20E12…7F29` khớp Ni và Tiny (đã dùng thêm bundle `2242319` `06D79239…2ADE` cho bản build đầu) |
| Clone / config | `C:\Bots\MeloVault-CAIDAT-IsolatedQA-20261102-d52fe43`, detach `d52fe43`, cây sạch; `telegram.config.valid=true` |
| Build | `assembleShareableDebug` BUILD SUCCESSFUL 1m40s; APK 33.812.590 B, SHA-256 `F376BCCB…77E7`, `app.melovault.player` vc121/`0.6.96-beta`, debuggable, cert `eecd3df7…fce8` |
| Gate cài | Sony `XQ-AS52` Sony, `device`, vc121 = vc121, cert khớp, không có phiên phát, màn OFF; **một** lệnh `adb -s QV725WEQ54 install -r` → Success; SHA bản cài = SHA APK |
| Hai lần cài | Bản đầu `1037FB53…DF93` (`2242319`) cài trước để QA chính; sau 3 sửa nhỏ build lại `d52fe43` và cài đè (cùng quy trình, cùng gate). Bản đầu không bàn giao |

Giá trị đầu phiên (đọc từ UI, `run-as` chỉ file `melovault_ui_lab.xml`): Gọn **Tự động**, Car **Tắt**, giả lập **Tắt**, handle band **Tự động**, màu **Hiện tại**, Lời nhạc **đã đồng ý**, `dev_tools_unlocked` không có. Sau QA: `car_mode=off`, `device_emulation=off`, `compact_mode=auto`, `preview_car_mode=false`, `color_theme=current`, `lyrics_consent=granted`, `dev_tools_unlocked=false` — **khôi phục đủ**.

| # | Thao tác (Sony dọc, giao diện Mới) | Kỳ vọng | Thực tế | Kết quả |
|---|---|---|---|---|
| Q1 | ⋮ → Cài đặt | Không còn Thiết bị/Nâng cao; nhóm thẻ biểu tượng; tiêu đề cố định; không chữ bị che | Đúng; trang vừa một màn nên không cần cuộn; thẻ Đăng xuất cách thanh điều hướng xa. Lỗi “bị che” **không tái hiện được** (Android 12) | PASS (phần che: CHƯA TÁI HIỆN, xem mục 3) |
| Q2 | Giao diện → Teal sáng → về → Màn Đang phát → khôi phục | Khối Màn Đang phát ở trang riêng | Đúng; tóm tắt “Màu Teal sáng” cập nhật. **Lưu ý:** màn Cài đặt vẫn một bảng màu (cũng như trước) | PASS |
| Q3 | Tắt/bật Lời nhạc | Bật → hộp đồng ý; Hủy giữ Tắt | Tắt ngay; hộp có đủ câu công bố; Hủy giữ TẮT; Đồng ý → BẬT (đã trả về BẬT) | PASS |
| Q4 | Đồng bộ (chỉ xem) → Back | Giá trị Bật/Tắt khớp | Trang chính “Tắt” = màn Đồng bộ “Chưa bật đồng bộ”; không đổi gì | PASS (chỉ đọc) |
| Q5 | Giới thiệu; Mã nguồn; Góp ý; Quyền riêng tư; Giấy phép → toàn văn | Nội dung đúng, liên kết mở | Mã nguồn mở Chrome tới `github.com/niquitin/melovault`, Back về đúng; Góp ý → “Đã sao chép địa chỉ email” (Sony **không có app email**); Quyền riêng tư 4 ý; Giấy phép + toàn văn cuộn được | PASS (soạn thư CHƯA KIỂM) |
| Q6 | Chạm Phiên bản ×7; mở công cụ; Ẩn | Mục xuất hiện, Ẩn lại được | Xuất hiện; Ẩn → về trang chính không còn mục. Ở bản đầu toast đếm xếp hàng nên không đọc được từng số; `d52fe43` chỉ còn một toast tại một thời điểm — bản cuối vẫn mở khóa đúng sau 7 chạm, nhưng **chưa chụp được từng số đếm/“Đã bật”** (ảnh chụp trễ so với chạm). “Xoay máy khi đã ẩn” CHƯA KIỂM | PASS (xoay: CHƯA KIỂM) |
| Q7 | Bật “Thử trong khung Shanling” → Ẩn công cụ → trang chính | Dòng “Đang thử khung giả lập · Tắt”; chạm → về bình thường | Đúng cả hai lần (bản đầu và bản cuối); sau chạm dòng biến mất, prefs về mặc định | PASS |
| Q8 | Xoay ngang / chữ lớn 1.3 | Không cắt/chồng | **CHƯA KIỂM** (cần đổi xoay/cỡ chữ hệ thống; không tự đổi) | CHƯA KIỂM |

Lỗi phát hiện khi QA và đã sửa trong `d52fe43`: (1) logo “MeloVault” lệch trái trên Giới thiệu; (2) toast 7 chạm xếp hàng; (3) câu Lời nhạc ở Quyền riêng tư lặp ý. Không có crash (`logcat -b crash`/`FATAL EXCEPTION` không có dòng của `app.melovault.player`).

`SONY_SCREEN_END`: màn tắt bằng `keyevent 223` trong `finally`, xác minh `Display State=OFF`, `mWakefulness=Dozing`, không phiên phát. Ảnh tổng hợp: `docs/plans/artifacts/2026-11-02-thiet-ke-lai-cai-dat/00_tong_hop.png`. Ảnh nằm trong `.gitignore`, không commit; ảnh thư viện (có tên bài) không upload.

## 8. APK QA bàn giao chủ dự án nghiệm thu

Xem [apk-test](2026-11-02-thiet-ke-lai-cai-dat-apk-test.md) và [hình](2026-11-02-thiet-ke-lai-cai-dat-hinh-nghiem-thu.md): Drive owner-only (thư mục/`hinh`/APK/ảnh tổng hợp chỉ `ntdung.md@gmail.com`), APK 33.812.590 B, SHA-256 `F376BCCBAB9B9FD9381FB7EA08068BDD9203AACB4C00078E282560971F8D77E7`, nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**. Tải bằng trình duyệt chưa thử.

## 9. Việc chưa kiểm / rủi ro

- **Âm thanh thật**: không phát nhạc. **Máy Android 15+**: lỗi “chữ bị che” chưa tái hiện, cách sửa chưa chứng minh trên máy đó. Soạn thư thật; Q8 xoay/chữ lớn; Release/`.open`/Personal trên máy.
- Trang Giao diện / Màn Đang phát / Thiết bị / Nâng cao giữ kiểu hàng cũ (choice row, nút chia đoạn) trong khung mới (có chủ đích); chỉ trang chính, Giới thiệu, Giấy phép, Công cụ và Car dùng thẻ biểu tượng mới.
- `MeloSyncSettingsActivity` còn mẫu insets cũ (ngoài phạm vi); màn Cài đặt không đổi theo “Kiểu màu” (như trước).
- Email `ntdung.md@gmail.com` công khai trong app/repo theo xác nhận của chủ dự án; chưa push/public.
