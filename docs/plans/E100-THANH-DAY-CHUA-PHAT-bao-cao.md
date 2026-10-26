# Báo cáo Sonnet — E100-THANH-DAY-CHUA-PHAT (phiên GỘP S0–S7)

Ngày thực hiện: 26/10/2026. Kết luận của Sonnet: **code + test + build xong; QA Shanling bước 1–7 PASS; Sony (hồi quy rút gọn) PASS phần hiển thị; còn vài giới hạn nêu ở mục 8.** Quyết định PASS/REWORK thuộc Opus nghiệm thu và chủ dự án.

## 1. Git

| Mục | Giá trị |
|---|---|
| Nhánh | `dot/E100-THANH-DAY-CHUA-PHAT` |
| S0 (đầu phiên) | HEAD = `90bf087` (commit kế hoạch), cây sạch, `git diff --check` sạch, không Gradle/Java khác chạy |
| Baseline | `c606424` |
| Commit 1 | `c7f6003` E100 S1: resume record and idle bottom-card rules |
| Commit 2 | `59dd2ae` E100 S2: bottom bar shows resume or play-all card when idle |
| Commit 3 | `c17fc6d` E100 S3: remember last Telegram song per account and resume it |
| Commit 4 | `8295598` E100 S4: bump shareable beta to vc116 (0.6.91-beta) |
| Commit 5 | báo cáo này (commit riêng sau `8295598`) |
| Commit identity | repo chưa cấu hình user; dùng `git -c user.name="Claude Code" -c user.email="noreply@anthropic.com"` cho từng commit (giống các commit cũ), không đổi git config |

## 2. File đổi (so với `90bf087`, chưa kể báo cáo)

| File | Thay đổi |
|---|---|
| `MeloResumePolicy.kt` (mới, 51 dòng) | khóa prefs theo accountId, `Record`, `record()`, `songKeyOf()`, `shouldWrite()`, `startPositionMs()` (MIN 5 s, END_GUARD 10 s) |
| `MeloBottomBarPolicy.kt` (+41) | `Idle {RESUME, PLAY_ALL, PICK}`, `idle()`, `idleActionable()`, chữ gợi ý, `resumeLine()`, `hintLine()`, `idleDescription()`, `idlePlayDescription()` |
| `MeloBottomBar.kt` (+87/−…) | `update(null)` không còn ẩn thẻ: `showIdle()` vẽ RESUME/PLAY_ALL/PICK; bấm thẻ / ▶ theo trạng thái; `refreshIdle()`; `showArt()` tách tham số dùng chung |
| `MeloScreenHost.kt` (+13) | `Resume`, `resumeCard()`, `canPlayAll()`, `playResume()` (đều có mặc định) |
| `MeloCompactScreen.kt`, `MeloPhoneScreen.kt` (+1 mỗi file) | `bar.refreshIdle()` trong `onLibraryChanged()` |
| `TelegramPreviewActivity.kt` (+133/−12) | ghi bản ghi (listener `onMediaItemTransition`, `onIsPlayingChanged(false)`, `onPause`), `clearResume()` ở **cả** `logoutUser()` và nhánh TDLib LoggingOut/Closing/Closed, `startResumeFlow` trong `loadDatabaseIndex`, 3 hàm host, tham số vị trí `downloadAndPlay → enqueueTelegramPlayback → enqueueItems → PendingPlayback → startPlayback` |
| `app/build.gradle` | vc115 → **vc116**, versionName `0.6.90.1` → **`0.6.91`** (hậu tố `-beta` có sẵn) |
| `MeloResumePolicyTest.kt` (mới), `MeloBottomBarPolicyTest.kt` | +9 và +4 test |

Không đụng: `ShareablePlaybackService`, DB/schema, TDLib handler, `MeloRowArtLoader`/`MeloRowArtPolicy`, `MeloCarScreen`, `MeloTabletScreen`, Playlist store, CLAUDE.md, docs/workflow. Tổng diff production ≈ 200 dòng, tests ≈ 120 dòng (đúng ước lượng mục 4 của kế hoạch).

Điểm thiết kế đáng Opus soi:
- Prefs `melovault_resume` chỉ có 3 khóa số mỗi tài khoản (`resume_song_<acc>`, `resume_pos_<acc>`, `resume_at_<acc>`); tên bài/kênh lấy lại từ DB (`getSongByKey`) khi mở app. Không lưu tên/kênh/đường dẫn/file_id.
- Vị trí khi chuyển bài: `onMediaItemTransition` ghi `lastStartPositionMs` nếu lý do là `PLAYLIST_CHANGED` (bài vừa nạp bằng `setMediaItems` với vị trí), ngược lại 0. Nhờ vậy nghe tiếp rồi bị kill khi đang phát vẫn nhớ ≈ vị trí bắt đầu, không bị ghi đè 0.
- Vị trí chỉ được dùng ở nhánh **file đã tải đủ** của `downloadAndPlay`; nhánh progressive không nhận tham số (luôn từ đầu).
- `shouldWrite` yêu cầu `auth.isReady()` + account > 0 + mediaId `telegram:`; bài "Nhạc trên máy" (`local:`, `picked:audio`) không ghi.
- Không tự phát khi mở app; không gọi `prepare()` sẵn.

## 3. Test và lệnh (Ni, log ở `%TEMP%\e100\`)

| Lệnh | Kết quả |
|---|---|
| `gradlew :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --rerun-tasks` | BUILD SUCCESSFUL 2m33s |
| Debug | tests=**2055**, failures=0, errors=0, skipped=**21** |
| Release | tests=**2055**, failures=0, errors=0, skipped=**21** |
| Mốc cũ | 2042 + 13 test mới = 2055 ✓; bỏ qua vẫn 21 ✓ |
| `gradlew :app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL (Ni, Telegram ID 0 → **không dùng để cài/QA**) |
| `git diff --check` | sạch |

JVM test không chứng minh UI/ADB/TDLib; phần View kiểm bằng QA thiết bị bên dưới.

## 4. Build APK và gate cài (Tiny → Ni)

| Mục | Giá trị |
|---|---|
| Chuyển code | bundle đầy đủ `D:\Bots\_transfer\MeloVault_E100-THANH-DAY-CHUA-PHAT_full_8295598_20261026.bundle`, SHA-256 `43136DC419B07061FFA28C8F06A3ADB983C3FCF45C272AC4BB6970FDF96A5049` (khớp phía Tiny) |
| Clone cô lập (mới) | `C:\Bots\MeloVault-E100-IsolatedQA` @ `8295598`, sạch; `local.properties` chép trong nội bộ Tiny rồi xóa sau build (nội dung không đọc, bản gốc không đổi) |
| Telegram config | `telegram.config.valid=true` (chỉ boolean) |
| Build | `assembleShareableDebug` trên Tiny, BUILD SUCCESSFUL 2m50s |

## 5. APK QA cần Opus nghiệm thu bàn giao

| Mục | Giá trị |
|---|---|
| Loại build | **Debug** (Tiny, ký chữ ký Preview, có Telegram config) — **QA CANDIDATE, chưa nghiệm thu** |
| Git commit | source `8295598` (nhánh `dot/E100-THANH-DAY-CHUA-PHAT`); báo cáo ở commit sau |
| Tệp | `MeloVault-E100-THANH-DAY-CHUA-PHAT-8295598-vc116-TinySigned-Debug.apk` |
| Đường dẫn Tiny | `C:\Bots\_qa_handoff\E100-THANH-DAY-CHUA-PHAT\` (= `T:\_qa_handoff\E100-THANH-DAY-CHUA-PHAT\`) |
| Bản sao Ni | `D:\Bots\_transfer\E100\` |
| Package | `app.melovault.player` |
| versionCode / versionName | **116** / `0.6.91-beta` |
| SHA-256 | `F5E358CE89F627DCA9BC57F08861556A4C99CF1DEFB516985CAC3540FEA19515` (đã đối chiếu lại trên Ni) |
| Bytes | 33 413 060 |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (= APK đang cài trên Shanling và Sony) |
| Telegram config | true |
| Test Gradle | 2055/0 lỗi/21 bỏ qua (Debug + Release) |
| Cài | Shanling vc115 → vc116 và Sony vc115 → vc116, đều `install -r` thành công |
| Chưa QA | xem mục 8 |
| Rollback | revert `c17fc6d` (Activity) và `59dd2ae` (view); giữ vc116 để không downgrade; prefs `melovault_resume` bỏ qua được |

Không upload/công bố APK, không đổi quyền Drive: chờ Opus nghiệm thu.

## 6. QA Shanling M3 Ultra (Ni, serial 30313860180939)

Gate trước khi cài: `adb devices -l` = device `Shanling_M3_Ultra`; `getprop` model `Shanling M3 Ultra`, manufacturer `Shanling`; app đang cài vc115 / 0.6.90.1-beta; cert APK đang cài (pull base.apk, apksigner) = cert APK mới; vc116 > vc115; Media3 không PLAYING (state=0, trước đó chưa nạp bài nào); màn OFF. Chỉ dùng `adb -s 30313860180939 install -r` (Success). Dùng thêm `am force-stop app.melovault.player` khi không PLAYING (được phép riêng cho E100). Không logout / pm clear / uninstall / `-d`.

| # | Hành động | Kỳ vọng | Thực tế | Kết quả | Ảnh |
|---|---|---|---|---|---|
| 1 | Sau cài, mở lạnh, chờ ~8 s | Thẻ không trống; Gọn 8 dòng, đáy 102 dp; không tự phát | Thẻ "Chưa phát bài nào / Bấm để phát danh sách đang xem" (ô chữ C), ▶ sáng. uiautomator: thanh đáy `[16,1000][752,1204]` = 204 px = **102 dp** (density 320); 8 nút ⋮ dòng đầy đủ (y=160…888). Media3 state=0 | **PASS** | `01_Shanling_launch_idle_081350.png` |
| 2a | Tab Telegram (màn nguồn), bấm thẻ | "Chọn một bài trong danh sách"; ▶ mờ; bấm thẻ không làm gì | Đúng chữ; sau bấm thẻ Media3 vẫn state=0 | **PASS** | `02_…telegram_tab_081418.png` |
| 2b | Về tab Thư viện | Lại "Bấm để phát danh sách đang xem" | Đúng (content-desc đọc bằng uiautomator) | **PASS** | `03_…library_tab_081424.png` |
| 3 | Bấm thẻ gợi ý | Phát bài đầu danh sách; thẻ chuyển kiểu "Đang phát" | Media3 state=3 (PLAYING), position 4.5 s; thẻ "Nonstop - Lào Cai Phố Vol 3" = dòng 1 danh sách; thanh tiến trình hiện | **PASS** | `04_…after_hint_tap_081453.png` |
| 4 | Mở "Đã tải" (☰ → ✓ Đã tải), bấm "LOW 2026 – Thiện Matthew", nghe ≥30 s, bấm ⏯ | PAUSED ≈ 30 s | state=2 (PAUSED), position **34 526 ms** | **PASS** | `05d_…playing_downloaded_081638.png`, `05_…paused_downloaded_081710.png` |
| 5 | `force-stop` → mở lại, chờ 10 s, không chạm | Thẻ tên bài + "Nghe tiếp · kênh"; ▶ sáng; không tự phát | Thẻ "LOW 2026 – Thiện Matthew / Nghe tiếp · NHÓM TRACK DJ ( EMI )" (ô chữ N); content-desc "Nghe tiếp: … Bấm để phát"; Media3 state=0 (không PLAYING) | **PASS** | `06_…cold_open_resume_081731.png` |
| 6 | Bấm ▶ trên thẻ (không bấm thẻ) | Phát đúng bài từ ≥ 25 s | state=3, **position 34 586 ms ngay lần đọc đầu** (tức vị trí đã dừng), đúng bài (metadata); logcat không FATAL/ANR | **PASS** | `07_…resumed_playing_081747.png` |
| 7 | Gọn: 8 dòng, thanh đáy 102 dp | như bước 1 | đo ở bước 1; ở các ảnh sau vẫn 8 dòng | **PASS** | (01, 06) |
| 8 (tùy chọn) | Bài chưa tải → phát ~20 s → dừng → force-stop → mở lại → ▶ | phát từ đầu | **Không kiểm được nhánh progressive**: "Style Tươi Vol 4" tải xong (✓) trong ~20 s nên lần mở lại là file đã tải đủ → tiếp từ vị trí (≈ 21.9 s) đúng thiết kế. Thẻ lúc này có ảnh bìa thật (cache còn trong bộ nhớ) | **CHƯA KIỂM nhánh progressive** | `08a_…`, `08_…` |

Ảnh tổng hợp 8 ô: `docs/plans/artifacts/E100-THANH-DAY-CHUA-PHAT/00_tong_hop.png` (1600×1334).
Header PNG đã kiểm (`89 50 4E 47`), ảnh đọc được. Ảnh nằm trong thư mục artifacts bị `.gitignore` (không commit).

Trạng thái Shanling sau QA (khác lúc bắt đầu — trước QA chưa nạp bài nào): bộ phát đang nạp "Style Tươi Vol 4" ở trạng thái **tạm dừng** (≈ 26.7 s), hai bài "Nonstop – Lào Cai Phố Vol 3" và "Style Tươi Vol 4" nay hiện ✓ (đã tải xong do lúc phát). Không đổi cài đặt UI, không xóa dữ liệu.

`SHANLING_SCREEN_END=OFF` (08:19:11: `keyevent 223`, `mScreenState=OFF`, `mWakefulness=Asleep`). Màn giữ OFF trong lúc build/test/đọc báo cáo.

## 7. QA Sony Xperia 5 II (Tiny, serial QV725WEQ54) — hồi quy rút gọn

Gate: model `XQ-AS52`, manufacturer `Sony`; đang cài vc115; cert đã cài = cert APK; không PLAYING (một phiên Media3 khác của máy ở trạng thái lỗi Bluetooth, không phải MeloVault, không đụng); `install -r` Success → vc116 / 0.6.91-beta. Lúc đánh thức màn, cửa sổ nổi là hộp thoại USB của Sony (`ConfirmMtpModeActivity`), không tương tác, mở app thì thay thế.

| # | Hành động | Thực tế | Kết quả | Ảnh |
|---|---|---|---|---|
| S1 | force-stop → mở lạnh 12 s (Điện thoại dọc, 4 tab) | Thẻ "Chưa phát bài nào / Bấm để phát danh sách đang xem"; ▶ "Phát hết" bấm được; không tự phát | **PASS** | `09_Sony_cold_open_idle_082050.png` |
| S2 | Bấm thẻ gợi ý, chờ 10 s | state=3, position 8.5 s, phát bài đầu danh sách ("# Mưa Bay Bay.mp3") | **PASS** | `10_Sony_after_hint_tap_082124.png` |
| S3 | Bấm ▶ tạm dừng | state=2, position 9.8 s | **PASS** | `11_Sony_paused_082126.png` |
| S4 | force-stop → mở lạnh 12 s + 3 s | Thẻ "# Mưa Bay Bay.mp3 / Nghe tiếp · SƯU TẦM NHẠC BAY ✈️"; không PLAYING | **PASS** | `12_Sony_cold_open_resume_082141.png` |

Không làm trên Sony: bấm ▶ của thẻ "Nghe tiếp" (chưa kiểm phát lại trên Sony), theo dõi chiều cao thanh đáy 122 dp. Sony hiện nạp "Mưa Bay Bay" (tạm dừng ≈ 9.8 s).
`SONY_SCREEN_END=OFF` (`Display State=OFF`, `mScreenState=OFF`, `mWakefulness=Dozing`).
Ảnh Sony chép về cùng thư mục artifacts trên Ni; bản gốc ở `T:\_qa_handoff\E100-THANH-DAY-CHUA-PHAT\sony\`.

## 8. Chưa kiểm chứng / rủi ro

- **CHƯA XÁC NHẬN ÂM THANH THỰC**: Media3 PLAYING và position tăng chỉ chứng minh luồng phát.
- Xóa bản ghi khi đăng xuất / đổi tài khoản: chỉ bằng mã (`clearResume()` ở `logoutUser()` và nhánh LoggingOut/Closing/Closed) + unit test khóa theo account; **CHƯA KIỂM THIẾT BỊ** (cấm logout).
- Nhánh progressive "luôn từ đầu" chưa chạy được trên máy (bài tải xong quá nhanh); đảm bảo bằng mã: `enqueueProgressivePlayback` không nhận vị trí và `startPositionMs(..., fileComplete=false)=0` (unit test).
- Thẻ "Nghe tiếp" chỉ đọc cache ảnh sẵn có: khi mở lạnh ảnh là ô chữ cái (bước 5), có ảnh bìa khi cache còn (bước 8a) — đúng như kế hoạch mục 11.
- Bài kế đi theo "danh sách đang xem" khi nghe tiếp: dùng nguyên `TelegramQueueRetentionPolicy.forSelected`, chưa kiểm riêng trên máy (bài LOW 2026 không nằm trong 200 bài của danh sách "Tất cả").
- Khi Activity bị hủy lúc đang phát nền (bài kế từ màn khóa) bản ghi không cập nhật — giới hạn đã nêu trong kế hoạch.
- Bấm thẻ "Nghe tiếp" (khác nút ▶) và Hai khung/Tablet dọc/Y700/S25/FYT/Car Mode chưa kiểm trên máy; Janky/hiệu năng chưa đo. Car Mode và `MeloTabletScreen` không đổi mã (host mặc định).
- Log `MeloResume stage=loaded` đã thêm nhưng không còn trong bộ đệm logcat lúc kiểm, nên không dùng làm bằng chứng.
- Ni build Release/Debug (Telegram ID 0) chỉ dùng để chứng minh biên dịch, không cài.

## 9. Artifacts (không commit)

`docs/plans/artifacts/E100-THANH-DAY-CHUA-PHAT/`: `00_tong_hop.png`, `01`–`08`, `05a/05b/05c/05c2/05c3/05d` (ảnh trung gian khi dò menu "Đã tải"), `09`–`12` (Sony). Không xóa/ghi đè artifact cũ.
