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
