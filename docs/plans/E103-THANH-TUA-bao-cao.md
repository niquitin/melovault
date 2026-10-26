# Báo cáo Sonnet — E103-THANH-TUA

## Phiên 1 — code + test trên Ni (S0–S4), 26/10/2026

**Trạng thái phiên 1: XONG phần code, JVM test và build Gradle trên Ni. Chưa build Tiny (có Telegram config), chưa cài máy, chưa QA thiết bị (thuộc phiên 2).**

### 1. Git

| Mục | Giá trị |
|---|---|
| Nhánh | `dot/E103-THANH-TUA` (cây sạch lúc bắt đầu; chỉ có Gradle daemon rảnh, không agent/build khác) |
| Nền / commit kế hoạch | `da7d33d` / `5d88026` (HEAD lúc bắt đầu khớp) |
| S1 | `7886d68` seek bar policy + 5 test |
| S2 | `d586776` `MeloSeekLine` thay `MeloThumbProgress`, nối vào Hai khung |
| S3 | `c3de2fa` thanh tua trở lại bến Studio Deck |
| S4 | commit chứa bump vc119 / `0.6.94-beta` và báo cáo này (xem `git log -1`) |
| Git user | không đổi config; commit bằng `git -c user.name="Claude Code" -c user.email=…` |

### 2. File đổi (đúng mục 4 của kế hoạch, +211 / −62 dòng, không tính báo cáo)

| File | Nội dung |
|---|---|
| `MeloNowPlayingPolicy.kt` | `SEEK_BAR_MAX=1000`, `SEEK_BAR_END_GUARD_MS=1000`, `seekBarEnabled`, `seekBarProgress`, `seekBarTarget` (hàm thuần) |
| `MeloComponents.kt` | Thêm `MeloSeekLine : SeekBar` (track + fill `ClipDrawable`, thumb oval, chỉ tua khi thả tay, phím/TalkBack tua ngay, cờ `dragging` chặn nhịp 1 s); xóa `MeloThumbProgress` (không còn nơi dùng) |
| `MeloTabletScreen.kt` | `seekLine` + `timeBlock` dùng cho cả hai kiểu màu; `renderTimes(now)` gom giờ + thanh; `applyTight` dùng `timeBlock` |
| `MeloStudioDeck.kt` | `seekLine` đặt trong bến ghim trước dòng giờ; `renderTime(now)` (giờ hiện mốc kéo khi đang kéo); KDoc cập nhật |
| `app/build.gradle` | vc **119** / `0.6.94` (+ `-beta` có sẵn) |
| `MeloNowPlayingPolicyTest.kt` | +5 hàm test (≈ 30 phép so sánh) |

Không đụng: Gọn, Car Mode, `NowPlayingFullScreenDialog`, `TelegramPreviewActivity`, `MeloScreenHost.seekTo`, service/Media3/hàng đợi/DB/TDLib, manifest, prefs. Không thêm nút ± vào Hai khung.

### 3. Test và build (log Gradle ghi ra file ngoài repo, chỉ lấy dòng tổng kết)

| Lệnh | Kết quả |
|---|---|
| `.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain` | BUILD SUCCESSFUL (2m32s) |
| Debug (đếm từ XML kết quả) | **2094** test, **21** bỏ qua, **0** lỗi, **0** failure (nền 2089 + 5 mới) |
| Release | **2094** test, **21** bỏ qua, **0** lỗi, **0** failure |
| `.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain` | BUILD SUCCESSFUL (1m48s) |
| `git diff --check` | sạch |

Test mới (TDD: viết test trước rồi mới viết hàm) phủ đủ các ca mục 6 của kế hoạch: `seekBarEnabled` (0, −1, `TIME_UNSET`, 1); `seekBarProgress` (dur 0/`TIME_UNSET`, pos âm, giữa bài, vượt thời lượng, bài 10 giờ không tràn); `seekBarTarget` (null khi chưa biết thời lượng, 0, 500→100.000 ms, kẹp −5/1200, chặn mép cuối `dur−1000`, bài 600 ms ⇒ 0); khứ hồi lệch ≤ dur/1000.

JVM test và Gradle xanh **không** chứng minh chạm/kéo thật; phần đó thuộc QA phiên 2.

### 4. Quyết định thực thi cần Opus lưu ý

- `MeloSeekLine` gọi seek cả khi `ACTION_CANCEL` giữa lúc kéo (hành vi chuẩn của `SeekBar`, giống Gọn/Car Mode).
- Hai khung màu thường: `timeBlock` = [thanh][giờ], giữ `topMargin = s.gap * 2`, thumb màu `p.accent`; Teal: thumb màu `p.text` như E98, vùng chạm cao `s.minTouch`.
- Hằng `SEEK_BAR_END_GUARD_MS = 1000` giữ đúng kế hoạch (mục 11 vẫn [CẦN XÁC NHẬN] với chủ dự án).
- APK ở `app/build/outputs/apk/shareable/{debug,release}/` (33.445.828 và 30.687.598 byte) build trên Ni **không có Telegram config hợp lệ** ⇒ **không cài**, không bàn giao. Dùng bản build ở Tiny cho phiên 2.

### 5. Việc còn lại (phiên 2, chưa làm)

S5 (Git bundle + SHA256 sang Tiny, build có Telegram config, kiểm package/vc119/cert, cài Sony và Shanling theo cổng mục 7) và S6 (QA 6 bước có ảnh, `00_tong_hop.png`, mục "APK QA cần Opus nghiệm thu bàn giao"). Chưa chạy `adb`; Shanling/Sony không bị chạm trong phiên này (`SHANLING_SCREEN_END` / `SONY_SCREEN_END`: không áp dụng, không đánh thức máy).

### 6. Rủi ro / chưa kiểm chứng

- Kéo thanh trong `Dialog` của Deck và trong `ScrollView` của Hai khung chưa thử trên máy (điểm STOP mục 9 kế hoạch nếu `SeekBar` không nhận kéo).
- Bến Deck cao thêm khoảng 48 dp: ảnh bìa trên Sony dọc/FYT 642 dp chưa xem.
- Hai khung Teal, FYT (núm xoay/DPAD), Y700, S25, âm thanh thật: chưa kiểm.

---

## Phiên 2 — build Tiny + QA thiết bị, 26/10/2026 13:50–14:13

**Kết luận phiên 2 (Sonnet): QA vòng 1 FAIL (lỗi thật, đã sửa); QA vòng 2 trên bản sửa `3d576bb`: Sony bước 1–4 và 6a/6b PASS, Shanling bước 5 PASS. Không FATAL/ANR. CHƯA XÁC NHẬN ÂM THANH THỰC.** Chờ Opus nghiệm thu và chủ dự án xem ảnh.

### 7. Vòng 1 (FAIL) và bản sửa

| Mục | Nội dung |
|---|---|
| Bản thử vòng 1 | `fc98e0e` (vc119), build Tiny, APK SHA-256 `692C983E…D3656CB1B`, đã `install -r` lên Sony và Shanling |
| Lỗi thấy trên máy | Sony bước 2: **kéo thanh tới ~75%, thả tay thì nhạc tua về đúng vị trí lúc vừa chạm** (không tua tới đích). Giữa lúc kéo thì giờ hiện mốc đích đúng, nhạc không tua (đúng ý) |
| Nguyên nhân | `MeloSeekLine.onStopTrackingTouch` gọi `onPreview(null)` **trước** khi tính đích; `onPreview(null)` vẽ lại và `set()` đặt chấm về vị trí đang phát, nên `seekBarTarget(progress…)` đọc `progress` đã bị đặt lại. JVM test không bắt được (lớp View) |
| Sửa | Commit `3d576bb`: đọc `target` trước, rồi `dragging=false`, `onPreview(null)`, `onSeek(target)`. Chỉ vài dòng trong `MeloComponents.kt` |
| Bằng chứng vòng 1 | `sony/02a…02c_*`, `sony/04a…04c_*` (bài NONSTOP 3:21:07: trước thả 23,9 s, sau thả 24,1 s thay vì ~2:32:00) |
| Lưu ý | Lần thử đầu dùng bài Mưa Bay Bay (2:14:59) mà file lúc đó có thể còn tải dở nên kết quả mơ hồ; đổi sang bài "✓ Đã tải" thì lỗi lặp lại, nhờ đó mới xác định đúng nguyên nhân |

### 8. Build Tiny, gate cài (bản cuối `3d576bb`)

| Mục | Giá trị |
|---|---|
| Git trên Ni | nhánh `dot/E103-THANH-TUA`, HEAD `fc98e0e` lúc bắt đầu (S1–S4 đủ), sau đó thêm `3d576bb`; cây sạch, `git diff --check` sạch |
| Bundle | `D:\Bots\_transfer\MeloVault_E103-THANH-TUA_full_3d576bb_20261026.bundle`, 12799256 B; SHA-256 `C869D6A4476F0E9D4BBD0BC75353BA326ADF13475579E2F016C1C854D397B733`, khớp sau khi chép sang Tiny |
| Clone cô lập Tiny | `C:\Bots\MeloVault-E103-IsolatedQA2` @ `3d576bb`, sạch; `local.properties` chép nội bộ Tiny rồi xóa, bản gốc không đổi (`original unchanged=True`, `copy gone=True`) |
| Build | `assembleShareableDebug` BUILD SUCCESSFUL 2m48s; `telegram.config.valid=true` (chỉ boolean) |
| Gradle test trên Ni (commit `3d576bb`) | Debug và Release đều **2094** test, **21** bỏ qua, **0** lỗi; assemble Debug+Release BUILD SUCCESSFUL; `git diff --check` sạch |

### 9. APK QA cần Opus nghiệm thu bàn giao

| Mục | Giá trị |
|---|---|
| Nhãn | **QA CANDIDATE — CHƯA NGHIỆM THU** (Debug, ký khóa debug Tiny, build Tiny) |
| Git commit nguồn | `3d576bb` (nhánh `dot/E103-THANH-TUA`) |
| Đường dẫn Tiny (đã xác minh tồn tại) | `C:\Bots\_qa_handoff\E103-THANH-TUA\MeloVault-E103-THANH-TUA-3d576bb-vc119-TinySigned-Debug.apk` |
| Bản sao trên Ni | `D:\Bots\_transfer\E103\MeloVault-E103-THANH-TUA-3d576bb-vc119-TinySigned-Debug.apk` (SHA-256 và byte khớp Tiny) |
| Package / version | `app.melovault.player` · versionCode **119** · versionName **0.6.94-beta** · `arm64-v8a` |
| SHA-256 | `DF7EA87ACE5925176DACD22DF3958E557AAA56F2E0EEF61FBB81B113EA7215EC` |
| Bytes | 33 445 828 |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (bằng cert bản vc118 đang cài, đối chiếu bằng `apksigner` trên `base.apk` kéo từ cả hai máy) |
| Telegram config hợp lệ | true |
| Không làm | Không upload Drive, không công bố, không đổi quyền. **APK `fc98e0e` (SHA `692C983E…`) có lỗi, KHÔNG dùng**; đã bị thay trên cả hai máy |

### 10. Cài thiết bị (chỉ `adb install -r`)

| Thiết bị | Gate | Kết quả |
|---|---|---|
| Sony XQ-AS52 (`QV725WEQ54`, Tiny), Android 12 | model `XQ-AS52`/`Sony`, `device`; vc118, cert khớp; media_session PAUSED (không PLAYING) | `fc98e0e` rồi `3d576bb`: `Success` → vc119 / 0.6.94-beta |
| Shanling M3 Ultra (`30313860180939`, Ni), Android 10 | model `Shanling M3 Ultra`/`Shanling`, `device`; vc118, cert khớp (kéo `base.apk`); state=0 | `fc98e0e` rồi `3d576bb`: `Success` → vc119 / 0.6.94-beta |

Không dùng `uninstall`, `pm clear`, `-d`, logout, xóa dữ liệu/cache/TDLib, đổi tài khoản, airplane, `wm size/density`.

### 11. Bảng bước QA tự làm qua ADB (bản `3d576bb`; ảnh ở `sony/` và `shanling/`, tên `r2_*` / `s5_*`)

Cách đo: tọa độ thanh lấy từ `uiautomator dump` (content-desc "Thanh tua bài hát", Sony); vị trí từ `dumpsys media_session` trước/sau; "nhảy" = Δposition − Δupdated.

| # | Máy | Hành động | Kỳ vọng | Thực tế | KQ | Ảnh |
|---|---|---|---|---|---|---|
| 1 | Sony vc119, dọc | Mở Deck bài NONSTOP (3:21:07, ✓ Đã tải) | Bến: thanh, giờ, 5 nút; ảnh bìa không bị đè | Thanh nằm trên dòng giờ, đủ −30s/⏮/⏸/⏭/+60s, bìa và chữ không đè (bản đầu `01_sony_vc119_deck_portrait_*` cũng đúng) | PASS | `r2_02a_*`; trước vc118: `00_sony_vc118_deck_before_135516` |
| 2 | Sony | Kéo từ đầu tới ~75% (swipe 5 s) | Giữa lúc kéo giờ hiện mốc đích, nhạc không tua; thả mới tua ≈75% ±3% | Giữa: giờ `1:31:42`, nhạc vẫn 62,0 s (tăng tự nhiên). Thả: **9 123 315 ms = 2:32:04 = 75,6%**, vẫn PLAYING, cùng bài | PASS | `r2_02b_*`, `r2_02c_*` |
| 3 | Sony | Chạm ~25%; rồi +60s; rồi −30s | ≈25%; +60 s; −30 s (±2 s) | 24,9% (3 004 908 ms); +59,9 s; −30,1 s | PASS | `r2_03b_*`, `r2_03c_*`, `r2_03d_*` |
| 4 | Sony | Cuộn trang Đang phát xuống hết | Thanh và 5 nút vẫn trên màn | Trang cuộn, bến vẫn ở đáy, `bounds` thanh vẫn `42,2008,1038,2134` | PASS | `r2_04_*` |
| 5 | **Shanling** vc119, Gọn | Mở Đang phát Gọn (bài "✓ Đã tải" 2:02:14), kéo `SeekBar` tới ~50% | Gọn như cũ; ≈50%; không crash | Gọn không đổi; **3 682 037 ms = 50,2%**; logcat FATAL=0, ANR=0 | PASS | `shanling/s5_05…s5_08_*` |
| 6a | Sony ngang | Thư viện ngang → khung trái, kéo thanh tới ~60% | Hai khung, thanh trên dòng giờ, ≈60% | Ra **Hai khung** (màu thường), thanh trên giờ; **7 240 728 ms = 60,0%** | PASS | `r2_06a_landscape_two_pane_*`, `r2_06a_two_pane_after_*` |
| 6b | Sony ngang | Mở Deck ngang, kéo thanh ở bến trái tới ~30% | Deck 2 cột, thanh ở bến trái, ≈30% | Deck 2 cột; **3 620 377 ms = 30,0%**; nhạc không tua giữa lúc kéo; xoay không crash | PASS | `r2_06a_landscape_library_*`, `r2_06b_deck_land_*` |

Ảnh tổng hợp (8 ô, 1600×1600): `docs/plans/artifacts/E103-THANH-TUA/00_tong_hop.png`.

### 12. Xoay màn Sony, màn hình, logcat

| Mục | Kết quả |
|---|---|
| `SONY_ROTATION_START` | `accelerometer_rotation=1 user_rotation=0` (ghi trước khi xoay, file `sony/rotation_start.txt`) |
| `SONY_ROTATION_END` | Lệnh trả lần đầu (trong `finally`) đọc lại `accelerometer_rotation=0`; mình `put accelerometer_rotation 1` lần nữa và đọc lại: **`accelerometer_rotation=1 user_rotation=0`, khớp ban đầu** (14:08:52). Chưa rõ vì sao lần đầu không ăn; không đổi cài đặt nào khác, không `wm size/density` |
| `SONY_SCREEN_END` | **OFF**: `Display State=OFF`, `mWakefulness=Dozing` (14:08:52) |
| `SHANLING_SCREEN_END` | **OFF**: `mState=OFF`, `mWakefulness=Asleep` (14:13:21); màn tắt suốt lúc build/test |
| logcat Sony (vòng 2, 12 043 dòng) | `FATAL EXCEPTION`=0, `ANR in`=0, crash `app.melovault.player`=0 |
| logcat Shanling (20 149 dòng) | `FATAL EXCEPTION`=0, `ANR in`=0, crash `app.melovault.player`=0 |
| Media3 | Cuối QA: Sony state=2 (PAUSED, 2:00:54 bài NONSTOP); Shanling state=2 (PAUSED, ~1:01:31 bài Style Tươi Vol 4). Chỉ chứng minh luồng phát; **CHƯA XÁC NHẬN ÂM THANH THỰC** |

### 13. Sự cố và điểm cần Opus biết

1. **Mình bấm nhầm thẻ "Nghe tiếp" trên Sony ở bước chụp "trước"**: bấm thẻ là PHÁT; bài Mưa Bay Bay (chưa tải) phát khoảng 19 giây rồi mình tạm dừng.
2. Tương tự trên Shanling, bấm thẻ "Nghe tiếp" làm bài Style Tươi Vol 4 (đã tải) phát. **Điểm "nghe tiếp" của cả hai máy đã đổi** (Sony: NONSTOP; Shanling: Style Tươi Vol 4) do QA; không tự khôi phục.
3. Sau cài lại, "Đã tải" của Sony tăng 65 → 66 (Mưa Bay Bay tải xong nền khi phát); mình không xóa hay ghi gì.
4. `uiautomator dump` không chạy được trên Shanling (`could not get idle state`) nên bước 5 lấy tọa độ từ ảnh chụp.
5. Không ghi đè artifact cũ (`00_tong_hop.png` mình tạo hai lần: lần đầu chọn nhầm ảnh, đã xóa chính tệp vừa tạo rồi tạo lại).
6. Vòng 1 lỡ cài `fc98e0e` lên cả hai máy; chỉ `install -r` cùng versionCode (không downgrade).

### 14. Chưa kiểm chứng / giới hạn

- Âm thanh thật; Hai khung kiểu **Teal** (Sony không ở Teal, không đổi Kiểu màu); FYT (núm xoay, DPAD, TalkBack), Y700, S25, Car Mode.
- Kéo tới mép phải để kiểm chặn `thời lượng − 1 s` chưa chạy trên máy (chỉ có unit test); tua vào phần bài chưa tải (cố ý không thử).
- Gọn Shanling không có ảnh "trước" vc118 để so từng điểm; hồi quy dựa trên mã Gọn không đổi (diff chỉ 5 file ở mục 2) và ảnh sau cài.
- Lỗi vòng 1 không có unit test chặn được (logic nằm trong View).

### 15. Kết quả theo mục 8 của kế hoạch

| Tiêu chí | Kết quả |
|---|---|
| Test Debug + Release 0 lỗi, có test mới, build OK, `git diff --check` sạch, chỉ đổi file mục 4 | Đạt (2094/21/0 trên `3d576bb`) |
| Sony bước 1–4 PASS; Shanling bước 5 PASS; bước 6 PASS; `SONY_ROTATION_END` khớp | Đạt (mục 12 ghi chi tiết lần trả xoay) |
| Không crash/ANR | Đạt (FATAL=0, ANR=0 hai máy) |
| Chủ dự án xem ảnh/cài vc119 và nói ĐẠT | **Chờ chủ dự án** |

Artifacts: `docs/plans/artifacts/E103-THANH-TUA/` (Ni; `sony/`, `shanling/`, `00_tong_hop.png`; git bỏ qua) và `C:\Bots\_qa_handoff\E103-THANH-TUA\` (Tiny: APK, ảnh/log Sony).

---

## REWORK vòng 1 (F1: thanh tua dày, chấm tròn không thấy) — Sonnet, 26/10/2026 14:30–14:47

**Kết luận (Sonnet): F1 đã sửa trên máy. Thanh mảnh ≈ 3 dp căn giữa, chấm tròn 16 dp có viền thấy rõ ở Deck dọc, Deck ngang và Hai khung. Sony bước 1, 2, 6a, 6b PASS (chức năng như vòng trước); Shanling Gọn không đổi. Không FATAL/ANR. CHƯA XÁC NHẬN ÂM THANH THỰC.** Chờ Opus nghiệm thu và chủ dự án xem ảnh.

### R-1. Commit và file đổi

| Mục | Giá trị |
|---|---|
| Git đầu phiên | `dot/E103-THANH-TUA` @ `c2ccf3a`, cây sạch, `git diff --check` sạch (có `java.exe` Gradle daemon và `adb.exe` chạy nền, không phải agent code) |
| Commit sửa | **`0a9beda`** `E103 R1: thin seek track with visible dot` |
| File production đổi | Chỉ `MeloComponents.kt` (khối `init` của `MeloSeekLine`), +9/−1 dòng. `git diff --stat 3d576bb..HEAD` ngoài file này chỉ có tài liệu (báo cáo, nghiệm thu, apk-test, hình nghiệm thu) |
| Không đụng | Car Mode/Gọn, Deck, Hai khung, policy, service, Media3, DB, TDLib, build.gradle (vc119 / 0.6.94-beta giữ nguyên) |

Nguyên nhân và cách sửa: `SeekBar` kéo giãn `progressDrawable` theo chiều cao style (~16 dp) nên `setSize(1, trackPx)` không đủ. Sửa: `LayerDrawable.setLayerHeight(i, trackPx)` + `setLayerGravity(i, CENTER_VERTICAL | FILL_HORIZONTAL)` cho cả lớp nền và lớp đã chạy (API 23+, minSdk 26). Chấm giữ 16 dp, thêm viền `thumbPx/8` màu track (`p.border`) để chấm tách khỏi phần đã chạy cùng màu. Logic kéo/thả/phím/khóa/chặn mép không đổi. Không có hàm thuần mới nên không thêm test.

### R-2. Test và build trên Ni (commit `0a9beda`)

| Lệnh | Kết quả |
|---|---|
| `gradlew :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest :app:assembleShareableDebug :app:assembleShareableRelease --console=plain` | BUILD SUCCESSFUL 3m30s (log ra file, chỉ đọc dòng tổng kết) |
| Debug (XML) | **2094** test, **21** bỏ qua, **0** failure, **0** error |
| Release (XML) | **2094** test, **21** bỏ qua, **0** failure, **0** error |
| `git diff --check` | sạch |

JVM test không chứng minh hình dạng thanh; chỉ ảnh thiết bị chứng minh (mục R-5).

### R-3. Bundle và build Tiny

| Mục | Giá trị |
|---|---|
| Bundle | `D:\Bots\_transfer\MeloVault_E103-THANH-TUA_full_0a9beda_20261026.bundle`, 12 813 837 B, SHA-256 `0237FFD022EBBEAFCBEBFD6D1D508CC6FC0057B62E2EA01A9376D56F49677FBB`; hash khớp sau khi chép sang Tiny (`C:\Bots\_MeloVault_Git_Transfer\`), script build kiểm lại hash trước khi dùng |
| Clone cô lập Tiny | `C:\Bots\MeloVault-E103-IsolatedQA2` (detached `0a9beda`, sạch); `local.properties` chép nội bộ Tiny rồi xóa, bản gốc không đổi (`original unchanged=True`, `copy gone=True`) |
| Build | `assembleShareableDebug` BUILD SUCCESSFUL 40 s; `telegram.config.valid=true` (chỉ boolean) |
| Đối chiếu APK cũ | `unzip -l`: cùng danh sách và kích thước mục, chỉ `classes5.dex` +140 B. Tệp lớn hơn bản `3d576bb` khoảng 0,9 MB do nén ZIP khác, không do nội dung |

### R-4. APK QA cần Opus nghiệm thu bàn giao

| Mục | Giá trị |
|---|---|
| Nhãn | **QA CANDIDATE — CHƯA NGHIỆM THU** (Debug, ký khóa debug Tiny, build Tiny) |
| Git commit nguồn | `0a9beda` (nhánh `dot/E103-THANH-TUA`) |
| Đường dẫn Tiny (đã xác minh tồn tại) | `C:\Bots\_qa_handoff\E103-THANH-TUA\MeloVault-E103-THANH-TUA-0a9beda-vc119-TinySigned-Debug.apk` |
| Bản sao trên Ni (đã xác minh) | `D:\Bots\_transfer\E103\MeloVault-E103-THANH-TUA-0a9beda-vc119-TinySigned-Debug.apk` (SHA-256 và byte khớp Tiny) |
| Package / version | `app.melovault.player` · versionCode **119** · versionName **0.6.94-beta** · `arm64-v8a` (`aapt2 dump badging`) |
| SHA-256 | `15A596FFF75B7D69810AA8C97CB623AB1A1F53E4133DD7F1324DF99B2EEC5425` |
| Bytes | 34 334 950 |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (`apksigner verify --print-certs`), bằng cert các bản trước; `install -r` thành công trên cả hai máy nghĩa là chữ ký khớp bản đang cài |
| Telegram config hợp lệ | true |
| Không làm | Không upload Drive, không công bố, không đổi quyền. APK `3d576bb` (SHA `DF7EA87A…`) thành bản cũ, không xóa; hai máy đã được thay bằng `0a9beda` |

### R-5. Cài đặt và QA (chỉ `adb install -r`, cùng versionCode 119)

| Thiết bị | Gate | Kết quả |
|---|---|---|
| Shanling M3 Ultra (`30313860180939`, Ni), Android 10 | `adb devices -l` = device; model `Shanling M3 Ultra`/`Shanling`; media_session state=2 (không PLAYING) | `install -r`: `Success`, vc119 / 0.6.94-beta |
| Sony XQ-AS52 (`QV725WEQ54`, Tiny), Android 12 | `device`; model `XQ-AS52`/`Sony`; state=2 (không PLAYING) | `install -r`: `Success`, vc119 / 0.6.94-beta |

Không dùng `uninstall`, `pm clear`, `-d`, logout, xóa dữ liệu/cache/TDLib, đổi tài khoản, `wm size/density`. Sau khi cài, màn Sony hiện hộp thoại hệ thống USB "Cho phép truy cập?" (ngoài app): **không bấm**, mở MeloVault bằng `monkey` đè lên.

Cách đo: tọa độ thanh từ `uiautomator dump` (content-desc "Thanh tua bài hát", Deck dọc `[42,2008][1038,2134]`); vị trí phát từ `dumpsys media_session` trước/sau. Ảnh ở `docs/plans/artifacts/E103-THANH-TUA/rw1/` (`sony/`, `shanling/`; git bỏ qua); ảnh ghép `rw1/00_tong_hop.png` (1600×1480, có số bước và 4 ô CẬN thanh).

| # | Máy | Hành động | Kỳ vọng | Thực tế | KQ | Ảnh |
|---|---|---|---|---|---|---|
| 1 | Sony vc119 (`0a9beda`), dọc | Mở Deck bài NONSTOP 3:21:07 (đang phát) | Bến: thanh mảnh + chấm tròn rõ, giờ, 5 nút; bìa không bị đè | Thanh mảnh căn giữa (ô cận: dày ước lượng 8–10 px trên 1080 px, trước đây ≈ 45 px), chấm tròn tách rõ, 5 nút đủ, bìa/chữ không đè | PASS | `r1_03_deck_144227`, ô cận trong `00_tong_hop.png` |
| 2 | Sony, dọc | Kéo từ đầu tới ~75% (swipe 5 s) | Giữa lúc kéo giờ hiện mốc đích, nhạc không tua; thả mới tua ≈75% ±3% | Giữa kéo: giờ `1:58:15` (≈ 58,8%, đúng điểm dọc đường kéo), nhạc vẫn chạy tự nhiên. Sau thả: **9 026 766 ms ≈ 74,8%**, vẫn PLAYING cùng bài | PASS | `r1_04_drag75_before/mid/after_14425x–144303` |
| 6b | Sony ngang | Xoay ngang (Deck 2 cột), kéo thanh bến trái tới ~30% | Deck 2 cột, thanh mảnh có chấm, ≈30% | Deck 2 cột; **3 632 445 ms = 30,1%**; chấm rõ | PASS | `r1_05_land_144503`, `r1_06b_deckland30_before/mid/after_144517–144524` |
| 6a | Sony ngang | Đóng Deck → Hai khung (màu thường), kéo thanh tới ~60% | Hai khung, thanh trên giờ, mảnh có chấm, ≈60% | Ra Hai khung, thanh trên dòng giờ; **7 240 728 ms = 60,0%** | PASS | `r1_06a_library_land_144535`, `r1_06a_twopane60_before/mid/after_144552–144559` |
| 5 | **Shanling** vc119, Gọn | Mở app, chụp màn Gọn (thư viện + mini player), 1 ảnh | Gọn không đổi | Gọn y như vòng trước (thư viện, mini player, nút Thư viện/Telegram); không bấm "Nghe tiếp" nên không phát nhạc | PASS | `shanling/r1_s00_launch.png` |

Bước 3 (chạm 25%, ±30/60 s) và 4 (cuộn) không chạy lại vì kế hoạch rework chỉ yêu cầu 1, 2, 6a (6b nếu tiện). Hành vi kéo/thả giữ nguyên mã nên số đo khớp vòng trước (±3%).

### R-6. Màn hình, xoay, logcat

| Mục | Kết quả |
|---|---|
| `SONY_ROTATION_START` / `SONY_ROTATION_END` | `accelerometer_rotation=1 user_rotation=0` / `1 0` (khớp; đọc lại sau khi trả; `rw1/sony/rotation_start.txt`) |
| `SONY_SCREEN_END` | **OFF**: `Display State=OFF`, `mWakefulness=Dozing` (≈14:46) |
| `SHANLING_SCREEN_END` | **OFF**: `mState=OFF`, `Display Power state=OFF`, `mWakefulness=Asleep` (14:46:49); màn chỉ sáng ≈1 phút để chụp 1 ảnh |
| logcat Sony (23 768 dòng) / Shanling (3 518 dòng) | `FATAL EXCEPTION`=0, `ANR in`=0 cả hai |
| Media3 | Sony cuối QA: state=2 (PAUSED, ≈2:00:54 NONSTOP, tạm dừng bằng nút trong app); Shanling state=0, không phát. Chỉ chứng minh luồng phát; **CHƯA XÁC NHẬN ÂM THANH THỰC** |

### R-7. Sự cố / điểm cần Opus biết

1. Lệnh commit đầu thất bại vì máy Ni chưa có `user.name/user.email`; commit lại bằng `-c user.name="Claude Code" -c user.email="noreply@anthropic.com"` (đúng tác giả các commit trước), không đổi cấu hình git.
2. Trên Sony, chạm thẻ "Nghe tiếp" để mở Deck đã **bắt đầu phát NONSTOP** (như vòng trước); sau QA đã tạm dừng. Điểm "Nghe tiếp" của Sony vẫn là NONSTOP (≈2:00:54).
3. Hộp thoại USB "Cho phép truy cập?" trên Sony không bấm.
4. Viền chấm màu `p.border` mảnh (2 px); trên nền tối chấm vẫn nổi rõ trên ảnh. Nếu muốn viền tương phản mạnh hơn thì là chỉnh nhỏ tiếp trong `MeloSeekLine`.

### R-8. Chưa kiểm chứng

- Âm thanh thật; Hai khung kiểu **Teal**; FYT, Y700, S25, Car Mode (không đổi mã).
- Deck/Hai khung trên Shanling (Gọn là kiểu mặc định ở máy này nên không có thanh Deck để chụp).
- Bước 3 và 4 không chạy lại ở vòng này; kéo tới mép phải trên máy; tua vào phần bài chưa tải.
