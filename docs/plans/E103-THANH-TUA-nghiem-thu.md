# Nghiệm thu — E103-THANH-TUA (Opus nghiệm thu độc lập, 26/10/2026)

## 1. Kết luận

**BLOCKED — CHỜ CHỦ DỰ ÁN.** Phần kỹ thuật đạt: đúng phạm vi file, test/build Opus tự chạy lại xanh, APK đạt cổng và đã lên Drive owner-only, QA Sony bước 1–4, 6a, 6b và Shanling bước 5 khớp kỳ vọng về **chức năng** (tua đúng mốc, chỉ tua khi thả tay).

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
