# 2026-11-02-bo-chinh-am-eq — Báo cáo thực thi

Vai: thực thi (Claude Code, Sonnet 5.5). Nhánh `dot/2026-11-02-bo-chinh-am-eq`, worktree `D:\Bots\_worktrees\MeloVault-eq`, baseline `b90834f`, HEAD đầu phiên `297d635` (commit kế hoạch).
Phạm vi phiên này: **Chặng 1 (S0–S7)**, không cần thiết bị. Chặng 2 (S8–S11: Tiny build, Sony QA, đo CPU, Drive) **chưa làm**.

## 1. Kết quả theo bước

| Bước | Kết quả | Bằng chứng |
|---|---|---|
| S0 | Xong. Cây sạch, HEAD = commit kế hoạch, `git diff --check` sạch, không agent khác. Baseline gate: **2725 test, 21 skipped, 0 fail, 0 error** (mỗi biến thể Debug và Release; 201 suite) | `artifacts/.../s0-baseline-gate.log` |
| S1 | Xong. `MeloEqPolicy.kt` + `MeloEqPolicyTest` (16 test xanh) | `s1s2-tests.log` |
| S2 | Xong. `MeloEqAudioProcessor.kt` + `MeloEqAudioProcessorTest` (13 test xanh) | `s1s2-tests.log` |
| S3 | Xong. Gắn RenderersFactory + prefs listener vào `ShareablePlaybackService`; build Debug xanh | `s3s4-build.log` |
| S4 | Xong. `MeloEqPanel.kt` + chip EQ trong `MeloStudioDeck` | `s3s4-build.log` |
| S5 | Xong (rà bằng mắt, chưa nhìn trên màn hình thật — xem mục 4) | — |
| S6 | Xong. Gate đầy đủ xanh (mục 2) | `s6-*.log` |
| S7 | Commit mã + hồ sơ | `git log` |

## 2. Gate S6

| Lệnh | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL. Mỗi biến thể **2754 test** (baseline 2725 + 29 mới), 21 skipped, 0 fail, 0 error, 203 suite |
| `:app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL |
| `-PmeloOpen=true :app:assembleShareableDebug` | BUILD SUCCESSFUL |
| `:app:assemblePersonalDebug` | BUILD SUCCESSFUL (Personal không đổi) |
| `git diff --check` | sạch |

Số test lấy từ XML `app/build/test-results/test*UnitTest`. 21 skipped là các test bị bỏ qua sẵn có, không thuộc nhiệm vụ này.

## 3. Thay đổi

Mới (shareable): `MeloEqPolicy.kt`, `MeloEqAudioProcessor.kt`, `MeloEqPanel.kt`. Mới (test): `MeloEqPolicyTest.kt`, `MeloEqAudioProcessorTest.kt`.
Sửa: `ShareablePlaybackService.kt` (+49 dòng), `MeloStudioDeck.kt` (+35/−6). Không đụng Personal, DB, TDLib, MediaSession, build.gradle, manifest.

- **Bộ lọc:** 10 peaking biquad RBJ, Q 1,41, tần số 31…16k Hz, tính bằng Double (Transposed DF-II), trạng thái riêng từng kênh. Dải gain 0 và dải ≥ 0,45·sampleRate bị bỏ qua.
- **Định dạng:** PCM 16/24/32-bit + float, tối đa 8 kênh; khác → `NOT_SET`. Đầu vào float NaN/Inf được đưa về 0 để không làm hỏng bộ lọc.
- **Bypass:** processor luôn active với PCM; Tắt hoặc Phẳng → `output.put(input)` (byte giống hệt, có test cho 4 định dạng × 3 cấu hình).
- **Preamp tự động** = −max(dải dương), đổi dần trong ≤ 20 ms; chặn biên cứng [−1, 1].
- **Đổi cài đặt:** `@Volatile settings` bất biến; luồng audio so tham chiếu đầu mỗi `queueInput`, tính lại hệ số, **giữ** trạng thái bộ lọc. Bật lại từ bypass và `onFlush` thì reset trạng thái về 0.
- **Service:** `eqRenderersFactory()` → `DefaultAudioSink.Builder(...).setAudioProcessors(arrayOf(eqProcessor))`, giữ `enableFloatOutput`/`PlaybackParams` được truyền vào. Đọc prefs ở `onCreate`, listener chỉ phản ứng khóa `eq_*`, gỡ ở `onDestroy`. `FEATURE_ENABLED = false` → không gắn factory, chip về Toast cũ.
- **Log Debug** (tag `MeloEq`, chỉ khi `BuildConfig.DEBUG`): `configure sr= enc= ch=`, `settings enabled= preset= gains= preampDb=`, và mỗi ~10 s âm thanh lọc: `stats inRmsDb= outRmsDb= clipped= avgUsPerBuffer=`. Release không log.
- **UI:** chip Deck tắt = "EQ" như cũ; bật = tô màu nhấn, "EQ · <mẫu>"; chạm mở `MeloEqPanel` (Dialog dưới đáy): tiêu đề + ✕, công tắc "Bật EQ", hàng mẫu cuộn ngang, 10 hàng SeekBar 0..24 (−12..+12 dB) với nhãn dải và số dB, chú thích, "Đặt lại". Mỗi thay đổi ghi prefs ngay (`apply()`), service áp trong vòng một buffer. Chỉnh khi đang Tắt không tự bật; phần thân mờ 0,5.
- Chọn chip "Tùy chỉnh" hiện giá trị tùy chỉnh đã lưu lần trước (không tự chép từ mẫu đang chọn); kéo một thanh từ mẫu khác thì chép mẫu đó rồi đổi dải.

Bảng mẫu dB dùng đúng giá trị trong kế hoạch mục 4.3 (vẫn **[CẦN XÁC NHẬN]** bằng tai chủ dự án).

## 4. Chưa kiểm chứng / giới hạn

- **Chưa chạy trên thiết bị nào** (Chặng 2). Chưa xem bố cục bảng EQ thật: chữ có bị cắt ở cỡ Gọn / chữ lớn 1,3, hai bảng màu, màn xe ngang — S5 mới là rà mã, chưa có ảnh. Cần chụp ở Q1–Q2.
- **Chưa xác nhận âm thanh thực**: chỉ có test JVM đáp tuyến (đo biên độ sin qua processor: +6 dB ở 1 kHz trừ preamp 6 dB ≈ 0 dB ±1; 62 Hz ≈ −6 dB ±1; cắt −6 dB ở 1 kHz ≈ −6 dB, 8 kHz ≈ 0) và log `MeloEq` sẽ có ở Chặng 2.
- CPU / underrun trên Sony chưa đo (S9). Thuật toán dùng Double với vòng lặp đơn giản, `when` theo định dạng trong vòng mẫu; nếu đo cho thấy tốn, tối ưu sau, không đổi kiến trúc.
- Bảng EQ là `Dialog` riêng phía trên Deck. Nếu activity bị tạo lại (xoay màn) khi bảng đang mở, bảng có thể rò cửa sổ / đóng theo; cài đặt đã lưu từng bước nên không mất. Chưa thử xoay thật.
- Chuyển bài / tua với EQ Bật: có xử lý `onFlush` (reset trạng thái) và test flush, nhưng chưa thử luồng phát thật (Q6).
- Hiệu ứng hệ thống của Sony (Dolby/ClearAudio+) nếu đang bật thì cộng thêm lên EQ của app — giới hạn đã ghi, không đổi.

## 5. Việc còn lại (Chặng 2)

S8 (bundle SHA256 → Tiny build có Telegram config → cài Sony → QA Q1–Q7 kèm `logcat -s MeloEq`, ảnh tổng hợp `00_tong_hop.png`), S9 (đo CPU/underrun, EQ Bật vs Tắt), S10 (Drive owner-only), S11 (hồ sơ, trả Sony về EQ Tắt/Phẳng, ghi `SONY_SCREEN_END`). Chủ dự án chưa giao Chặng 2 trong phiên này.
