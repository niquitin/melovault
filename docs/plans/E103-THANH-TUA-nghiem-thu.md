# Nghiệm thu — E103-THANH-TUA (Opus nghiệm thu độc lập, 26/10/2026)

## 1. Kết luận

**PASS** (chủ dự án "ĐẠT" 26/10/2026, mục 12) — bản `0a9beda`, vc119 / 0.6.94-beta. Trước đó: BLOCKED — CHỜ CHỦ DỰ ÁN ở `4899544` (F1 đã sửa và kiểm, mục 11). Giới hạn giữ nguyên: âm thanh thật chưa xác nhận; Teal, FYT, Y700, S25, Car Mode chưa kiểm.

Lịch sử: **REWORK (vòng 1, F1)** — chủ dự án chốt 26/10/2026: *"REWORK F1 — đưa prompt Sonnet sửa thanh tua thành thanh mảnh có chấm tròn thấy rõ."* Xem mục 10. (Trước đó: BLOCKED — CHỜ CHỦ DỰ ÁN ở `6b9dee6`.) Phần kỹ thuật đạt: đúng phạm vi file, test/build Opus tự chạy lại xanh, APK đạt cổng và đã lên Drive owner-only, QA Sony bước 1–4, 6a, 6b và Shanling bước 5 khớp kỳ vọng về **chức năng** (tua đúng mốc, chỉ tua khi thả tay).

Có **một lệch giao diện so với kế hoạch** (F1 dưới đây): thanh vẽ ra **dày khoảng 16 dp, gần như không thấy chấm tròn**, trong khi kế hoạch mục 3.2 yêu cầu "thanh mảnh có chấm tròn" (track cao `s.progressHeight` = 3 dp, chấm 16 dp) và mục 10 hứa Hai khung Teal "cùng cỡ track/thumb" như E98. Mục 8 không liệt kê lỗi này trong danh sách FAIL, nên Opus không tự xếp REWORK; **đề xuất REWORK nhỏ** và để chủ dự án chốt: chấp nhận thanh dày như ảnh, hoặc cho Sonnet sửa thành thanh mảnh có chấm.

## 2. Git

| Mục | Giá trị |
|---|---|
| Nhánh / HEAD lúc kiểm | `dot/E103-THANH-TUA` @ `e853d09`, cây sạch, không agent/build khác |
| Nền | `da7d33d` (`feature/e93-03d-y700-offline-cockpit`) |
| Commit | `4d494d3`, `5d88026` (kế hoạch) · `7886d68` S1 · `d586776` S2 · `c3de2fa` S3 · `fc98e0e` S4 · `3d576bb` sửa lỗi thả tay · `e853d09` báo cáo |
| `git diff --stat da7d33d..HEAD` | 8 file: `app/build.gradle`, `MeloComponents.kt`, `MeloNowPlayingPolicy.kt`, `MeloStudioDeck.kt`, `MeloTabletScreen.kt`, `MeloNowPlayingPolicyTest.kt`, kế hoạch, báo cáo — **đúng mục 4** |
| `git diff --check da7d33d..HEAD` | sạch |

Không đụng Gọn, Car Mode, `NowPlayingFullScreenDialog`, `TelegramPreviewActivity`, `MeloScreenHost.seekTo`, service/Media3/hàng đợi/DB/TDLib, manifest, prefs.

## 3. Đọc mã (chọn lọc)

| Điểm | Nhận xét |
|---|---|
| `MeloNowPlayingPolicy` 3 hàm + 2 hằng | Đúng 3.1: khóa khi `dur ≤ 0`, nhân `Long`, chặn `dur − 1000`, `max(0, …)` cho bài ngắn |
| `MeloSeekLine` (`MeloComponents.kt:884-964` (lớp từ dòng 891)) | Kéo chỉ `onPreview`; thả mới `onSeek`; phím/TalkBack (`fromUser` không kéo) tua ngay; `set()` bỏ qua chấm khi đang kéo. Bản sửa `3d576bb` đọc `target` **trước** `onPreview(null)` — đúng nguyên nhân lỗi vòng 1. `onDetachedFromWindow` xóa trạng thái kéo — tốt |
| Deck / Hai khung | Thanh trong bến ghim, trên dòng giờ; `renderTime(s)` dùng `previewMs ?: positionMs`; `applyTight` dùng `timeBlock` cho cả hai kiểu — đúng 3.3/3.4 |
| `ACTION_CANCEL` giữa lúc kéo vẫn tua (SeekBar chuẩn) | Chấp nhận, như Gọn/Car Mode; Sonnet đã ghi |

## 4. Test và build (Opus tự chạy trên Ni, 26/10 14:18–14:20)

| Lệnh | Kết quả |
|---|---|
| `gradlew :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest :app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL (task up-to-date) |
| Chạy lại bắt buộc `… --rerun` cho hai task test | BUILD SUCCESSFUL 1m55s |
| Debug (XML) | **2094** test, **21** bỏ qua, **0** failure, **0** error |
| Release (XML) | **2094** test, **21** bỏ qua, **0** failure, **0** error |
| So nền | 2089 → 2094 (+5 hàm test mới, `MeloNowPlayingPolicyTest` 29 test); khớp báo cáo |

JVM không chứng minh chạm/kéo thật; lỗi vòng 1 nằm trong View nên chỉ QA máy bắt được.

## 5. APK candidate (CLAUDE.md §9)

| Trường | Giá trị (Opus tự kiểm) |
|---|---|
| File (Ni) | `D:\Bots\_transfer\E103\MeloVault-E103-THANH-TUA-3d576bb-vc119-TinySigned-Debug.apk` |
| SHA-256 / bytes | `DF7EA87ACE5925176DACD22DF3958E557AAA56F2E0EEF61FBB81B113EA7215EC` / 33.445.828 — trùng báo cáo, trùng `base.apk` **đang cài trên Shanling** (`sha256sum` trên máy, vc119/0.6.94-beta) |
| Package / vc / vn | `app.melovault.player` / 119 / `0.6.94-beta`, arm64-v8a (`aapt2 dump badging`) |
| Cert SHA-256 | `eecd3df7…c3fce8` (`apksigner verify --print-certs`) — khớp các bản trước |
| Telegram config | true (boolean từ log build Tiny; hai máy chạy bài Telegram bình thường sau cài) |
| Drive | `MeloVault_QA/E103-THANH-TUA-20261026-vc119-3d576bb`: APK + 12 PNG + `SHA256.txt`; SHA-256 từng file khớp khi chép; quyền **chỉ owner** (connector: APK, thư mục, thư mục cha) — xem `E103-THANH-TUA-apk-test.md`, `E103-THANH-TUA-hinh-nghiem-thu.md` |
| Nhãn | **QA CANDIDATE — CHƯA NGHIỆM THU** |

APK `fc98e0e` (SHA `692C983E…`) có lỗi thả tay — không bàn giao.

## 6. QA thiết bị (đối chiếu `00_tong_hop.png` + ảnh gốc `r2_02b`, `r2_06a_two_pane_after`)

| # | Máy | Kỳ vọng | Chứng cứ | Opus |
|---|---|---|---|---|
| 1 | Sony dọc | Bến: thanh, giờ, 5 nút; bìa không bị đè | Ô 2–4: thứ tự đúng, bìa/chữ không đè | Đạt (chức năng); xem F1 |
| 2 | Sony | Kéo: giờ theo mốc, nhạc không tua; thả ≈75% | Ô 2 giữa kéo `1:31:42`; ô 3 sau thả `2:32:04/3:21:07` (75,6%) | Đạt |
| 3 | Sony | Chạm 25%, +60s, −30s | Ô 4 `51:09` sau +60s; báo cáo 24,9% / +59,9 / −30,1 s | Đạt |
| 4 | Sony | Cuộn: thanh + 5 nút ở yên | Ô 5: hàng đợi cuộn, bến vẫn đáy | Đạt |
| 5 | Shanling Gọn | Gọn không đổi, ≈50% | Ô 8: Gọn vẫn thanh mảnh + chấm, `1:01:24/2:02:14` | Đạt |
| 6a | Sony ngang | Hai khung, thanh trên giờ, ≈60% | Ô 6 + ảnh gốc: Hai khung màu thường, `2:00:42/3:21:07` | Đạt (chức năng); xem F1 |
| 6b | Sony ngang | Deck 2 cột, thanh bến trái, ≈30% | Ô 7: `1:00:21/3:21:07` | Đạt |
| — | Xoay | `SONY_ROTATION_END` khớp đầu | `accelerometer_rotation=1 user_rotation=0` (lần trả đầu lệch, Sonnet trả lại và đọc khớp) | Đạt |
| — | Màn hình | OFF cuối phiên | `SONY_SCREEN_END` OFF; Shanling Opus đọc lại `mState=OFF` 14:19 | Đạt |
| — | Logcat | Không crash/ANR | FATAL=0, ANR=0 hai máy (báo cáo) | Đạt |

## 7. Lỗi / điểm cần chốt

**F1 — Thanh dày, không thấy chấm tròn (lệch kế hoạch 3.2 và mục 10).**
- Thấy ở: Deck dọc (`sony/r2_02b_drag_mid_140548.png`, thanh y≈1625–1662 trên ảnh 2000 px ⇒ khoảng 45 px ≈ 16 dp), Hai khung (`r2_06a_two_pane_after_140804.png`). So với Gọn trên Shanling (ô 8) là thanh mảnh có chấm.
- Nguyên nhân có khả năng: `SeekBar` co giãn `progressDrawable` theo chiều cao mặc định của style (min/max height ~16 dp), `setSize(1, trackPx)` của track không giới hạn được; chấm 16 dp cùng màu `p.accent` với phần đã chạy nên lẫn vào thanh (`MeloComponents.kt` khối `init` của `MeloSeekLine`).
- Ảnh hưởng: chức năng tua đúng; nhưng giao diện khác thiết kế, bước "Kéo chấm tròn" ở mục Bác sĩ tự kiểm tra khó thấy chấm; Teal (chưa chụp được) nhiều khả năng cũng dày, trái với "cùng cỡ như E98".
- Đề xuất: REWORK nhỏ trong `MeloComponents.kt` (giới hạn chiều cao track về `trackPx`, chấm thấy rõ), không đổi logic; QA lại bước 1, 2, 6a.

**Lưu ý khác (không chặn):** điểm "Nghe tiếp" trên Sony/Shanling đã đổi do QA bấm thẻ; Sony "Đã tải" 65→66 do bài tải nền. Chặn mép cuối 1 s và versionName `0.6.94` vẫn [CẦN XÁC NHẬN] (kế hoạch mục 11).

## 8. Chưa kiểm chứng

- **CHƯA XÁC NHẬN ÂM THANH THỰC** (Media3 PLAYING/vị trí tăng chỉ chứng minh luồng phát).
- Hai khung kiểu Teal; FYT (núm xoay/DPAD/TalkBack), Y700, S25, Car Mode; kéo tới mép phải trên máy (chỉ unit test); tua vào phần bài chưa tải.
- Opus không chạy lại QA thiết bị (chứng cứ đủ, không mâu thuẫn về chức năng).

## 9. Chờ chủ dự án

Chủ dự án xem ảnh (Drive/chat) hoặc cài vc119 rồi trả lời một trong hai:
- **"REWORK F1"** — Sonnet sửa thanh mảnh có chấm (khuyến nghị), Opus nghiệm thu lại; hoặc
- **"ĐẠT"** — chấp nhận thanh dày như ảnh; Opus đổi thành PASS và xin xác nhận merge.

## 10. REWORK vòng 1 — việc Sonnet phải làm (26/10/2026)

Chỉ sửa F1, không đổi logic tua đã đạt.

| # | Yêu cầu | Tiêu chí đạt |
|---|---|---|
| R1 | `MeloSeekLine` (`MeloComponents.kt`, khối `init`) vẽ track + fill **cao đúng `trackPx`** (3 dp ở điện thoại), căn giữa theo chiều dọc trong vùng chạm `s.minTouch` | Trên ảnh Sony 1080 px, track ≤ ~12 px (≈ 4 dp) |
| R2 | Chấm tròn `thumbPx` (16 dp) **nhìn rõ** ở mọi vị trí, kể cả 0% và khi nằm trên phần đã chạy | Thấy chấm tròn tách khỏi thanh trên ảnh Deck dọc, Hai khung, Deck ngang; giống kiểu thanh của Gọn |
| R3 | Giữ vùng chạm `s.minTouch`, giữ toàn bộ hành vi: kéo chỉ xem trước, thả mới tua, phím/TalkBack tua ngay, khóa khi chưa có thời lượng, chặn mép cuối 1 s | QA lại bước 1, 2, 6a (và 6b nếu tiện) đạt như vòng trước (±3%) |
| R4 | Phạm vi: chỉ `MeloComponents.kt` (+ test nếu có hàm thuần mới, + báo cáo). **Không** đụng Car Mode/Gọn (dù Car Mode dùng cùng kiểu `progressTrack`), Deck/Hai khung, policy, service, Media3 | `git diff --stat 3d576bb..HEAD` chỉ gồm file trên |
| R5 | Không tăng versionCode (giữ vc119 / 0.6.94-beta, `install -r` cùng vc như vòng trước); APK mới build ở Tiny, có Telegram config | Báo cáo mục "APK QA cần Opus nghiệm thu bàn giao" cho commit mới; APK `3d576bb` thành bản cũ, không xóa |

Gợi ý kỹ thuật (Sonnet tự kiểm trên máy, không bắt buộc đúng cách này): `SeekBar` co `progressDrawable` theo chiều cao giới hạn của style, nên `setSize(1, trackPx)` không đủ. Với minSdk 26 có thể dùng `LayerDrawable.setLayerHeight(i, trackPx)` + `setLayerGravity(i, Gravity.CENTER_VERTICAL or Gravity.FILL_HORIZONTAL)` cho cả hai lớp (API 23+), hoặc bọc bằng `InsetDrawable`; `setMaxHeight` chỉ có từ API 29 nên không dùng một mình. Nếu chấm vẫn lẫn vào phần fill cùng màu, có thể thêm viền mảnh màu nền cho chấm — chỉ trong `MeloSeekLine`.

## 11. Nghiệm thu lại sau REWORK vòng 1 (Opus, 26/10/2026 14:54–15:00)

**Kết luận: BLOCKED — CHỜ CHỦ DỰ ÁN.** F1 đã sửa; R1–R5 đạt; không thấy lỗi mới.

| Mục | Kết quả (Opus tự kiểm) |
|---|---|
| Git | HEAD `ec8a3cd`, cây sạch; mới từ `c2ccf3a`: `0a9beda` (code) + `ec8a3cd` (báo cáo). `git diff --stat 3d576bb..HEAD` phần production chỉ `MeloComponents.kt` (+9/−1); `git diff --check` sạch |
| Mã (`MeloComponents.kt:906-928`) | Track/fill: bỏ `setSize`, thêm `setLayerHeight(i, trackPx)` + `setLayerGravity(i, CENTER_VERTICAL or FILL_HORIZONTAL)` cho cả 2 lớp (API 23+, minSdk 26 OK). Chấm: thêm viền `max(1, thumbPx/8)` màu track. Logic kéo/thả/phím/khóa/chặn mép không đổi. Car Mode/Gọn không đụng — **R1, R3, R4 đạt** |
| Test/build (`--rerun` hai task test + assemble Debug/Release) | BUILD SUCCESSFUL 2m; Debug **2094**/21 bỏ qua/0 lỗi; Release **2094**/21/0 — khớp báo cáo |
| APK `0a9beda` | SHA-256 `15A596FF…2EEC5425`, 34.334.950 B, `app.melovault.player` vc119 / 0.6.94-beta, cert `eecd3df7…c3fce8`; trùng `base.apk` đang cài trên Shanling (`sha256sum` trên máy) — **R5 đạt** (vc giữ 119) |
| Drive | thư mục mới `MeloVault_QA/E103-THANH-TUA-20261026-vc119-0a9beda`: APK + 7 PNG + `SHA256.txt`, SHA khớp khi chép; quyền chỉ owner (APK, thư mục) — `E103-THANH-TUA-apk-test.md`, `-hinh-nghiem-thu.md` |
| Ảnh `rw1/00_tong_hop.png` | Deck dọc, Deck ngang, Hai khung: **thanh mảnh, chấm tròn có viền tách rõ** (4 ô cận) — **R2 đạt**. Bước 2: giữa kéo giờ `1:58:15`, thả `2:30:28/3:21:07` (≈74,8%); 6b 30,1%; 6a 60,0% (±3%) |
| Shanling | Gọn (thư viện + mini player) không đổi; Opus đọc `mState=OFF` sau phiên |
| Xoay / màn / logcat | `SONY_ROTATION_END` = `1 0` khớp đầu; `SONY_SCREEN_END`, `SHANLING_SCREEN_END` OFF; FATAL=0, ANR=0 hai máy (báo cáo) |

Chưa kiểm ở vòng này: bước 3 (chạm 25%, ±30/60 s) và 4 (cuộn) không chạy lại — chấp nhận vì mã logic và bến không đổi, vòng trước đạt; Deck/Hai khung trên Shanling (máy dùng Gọn). Vẫn **CHƯA XÁC NHẬN ÂM THANH THỰC**; Teal, FYT (núm xoay/DPAD), Y700, S25, Car Mode chưa kiểm.

Điểm phụ (không chặn): viền chấm 2 px màu `p.border`, trên nền tối vẫn rõ; điểm "Nghe tiếp" Sony là NONSTOP do QA. Chặn mép cuối 1 s và versionName `0.6.94` vẫn [CẦN XÁC NHẬN] theo kế hoạch mục 11.

**Chờ chủ dự án:** xem ảnh hoặc cài APK `0a9beda` rồi nói **ĐẠT** (Opus đổi PASS, xin xác nhận merge `--no-ff` vào `feature/e93-03d-y700-offline-cockpit`) hoặc báo lỗi.

## 12. Chủ dự án duyệt

- 26/10/2026 chủ dự án trả lời trong phiên Opus nghiệm thu: **"ĐẠT — đổi E103 thành PASS và merge."** (sau khi nhận ảnh tổng hợp `rw1/00_tong_hop.png`, ảnh Deck dọc, ảnh Hai khung và link Drive owner-only của APK `0a9beda`).
- Trạng thái cuối: **PASS** trong phạm vi đã kiểm (Sony bước 1–4, 6a, 6b; Shanling Gọn bước 5). Vẫn CHƯA XÁC NHẬN ÂM THANH THỰC; Teal, FYT (núm xoay/DPAD/TalkBack), Y700, S25, Car Mode chưa kiểm; chặn mép cuối 1 s giữ như kế hoạch.
- Merge `--no-ff` vào `feature/e93-03d-y700-offline-cockpit` theo lời chủ dự án; không push.
