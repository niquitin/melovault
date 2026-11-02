# 2026-11-02-bo-chinh-am-eq — Báo cáo thực thi

Vai: thực thi (Claude Code, Sonnet 5.5). Nhánh `dot/2026-11-02-bo-chinh-am-eq`, worktree `D:\Bots\_worktrees\MeloVault-eq`, baseline `b90834f`, HEAD đầu phiên `297d635` (commit kế hoạch).
Phạm vi: **Chặng 1 (S0–S7)** trên Ni (mục 1–5) và **Chặng 2 (S8–S11)** trên Tiny + Sony cùng phiên theo lời chủ dự án “Làm ngay đây” (mục 6–9). HEAD cuối: `e2faf85` (commit mã `7353587` + 1 sửa giao diện sau QA).

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

---

# Chặng 2 — build Tiny, QA Sony, đo, bàn giao

## 6. S8 — build Tiny, cài và QA Sony

| Mục | Giá trị |
|---|---|
| Bundle → Tiny (bản cuối) | `EQ-e2faf85_20261102.bundle` 13.830.095 B, SHA-256 `25AA16A3…B4BC7` (khớp Ni và Tiny). Bản đầu `EQ-7353587_20261102.bundle` 13.829.157 B, `2D66C9B9…7821` (khớp) dùng cho build đầu |
| Clone / config | `C:\Bots\MeloVault-EQ-IsolatedQA-20261102-e2faf85`, detach `e2faf85`, cây sạch; `telegram.config.valid=true`. `local.properties` chép ngay trên Tiny từ clone QA trước (như tiền lệ nhiệm vụ Cài đặt), **không đọc/in** |
| Build | `assembleShareableDebug` BUILD SUCCESSFUL 1m38s. APK 33.828.974 B, SHA-256 `146A42859CFDC3EB204FBE1396BEE1A4EFE40F0C02EC8DADE46415446E1F368D`, `app.melovault.player` vc121 / `0.6.96-beta`, debuggable, cert `eecd3df7…fce8` (bằng cert app đang cài, kiểm bằng `apksigner` trên `base.apk` kéo từ máy) |
| Gate cài | Sony `XQ-AS52` Sony, `device`, vc121 = vc121, cert khớp, không có phiên phát, màn OFF; **một** lệnh `adb -s QV725WEQ54 install -r` → Success (làm hai lần: bản đầu `7353587` `03309F19…F36A` rồi bản sửa `e2faf85`) |
| Bản cài lúc đầu | `F376BCCB…77E7` (nhiệm vụ Cài đặt, commit `d52fe43`) — bị thay, không mất dữ liệu |

QA dùng bài đã tải (MP3 44,1 kHz 320 kbps). Nhạc phát thật qua trình phát; agent **không nghe được** → **CHƯA XÁC NHẬN ÂM THANH THỰC**; bằng chứng là log `MeloEq` và số đo.

| # | Thao tác | Kỳ vọng | Thực tế | Kết quả |
|---|---|---|---|---|
| Q1 | Mở Đang phát, chạm EQ | Chip “EQ” không tô; bảng mở, Tắt, Phẳng, 10 thanh 0 dB, chữ không cắt | Đúng (ảnh 03, 04). Cỡ Gọn tự động; hàng mẫu cuộn ngang | PASS |
| Q2 | Bật EQ → Tăng bass | Thanh 31–250 Hz = +6/+5/+4/+2; log `enabled=true preset=bass_up preampDb=-6`; ≤10 s có `stats`; nhạc không dừng | Đúng. `stats inRmsDb=-8,7 outRmsDb=-13,0 clipped=0`; `state=3` PLAYING, vị trí liên tục | PASS |
| Q3 | Kéo 1 kHz +4 | Chuyển “Tùy chỉnh”, dải khác giữ | Log `preset=custom gains=6,5,4,2,0,2…→4…`; **lỗi giao diện:** hàng mẫu dựng lại và cuộn về đầu nên chip “Tùy chỉnh” ngoài màn hình, không chip nào sáng | PASS chức năng; lỗi UI **đã sửa** `e2faf85` (ảnh 12 xác nhận) |
| Q4 | Đóng bảng, chip; app khởi động lại → phát → mở bảng | Chip “EQ · Tùy chỉnh” tô màu; sau khởi động lại còn Bật + giá trị đúng | Cài lại APK làm tiến trình mới (pid 26925→27453); `settings enabled=true preset=custom gains=6,5,4,2,0,4…` áp lúc service tạo; chip “EQ · Tùy chỉnh” tô màu; bảng mở có “Tùy chỉnh” sáng (ảnh 12). Không vuốt app khỏi đa nhiệm (thay bằng cài lại) | PASS |
| Q5 | Tắt EQ | Chip về “EQ”; không còn `stats` | Chip về “EQ” (ảnh 15); log `enabled=false`, không dòng `stats` sau đó; thanh vẫn hiện, bảng mờ (ảnh 14) | PASS |
| Q6 | Tiến, +60 s, xoay, chữ lớn | Không crash, phát tốt | Tiến → `state=3` vị trí 5,8 s, log `configure sr=44100 enc=2 ch=2`; +60 s → 66,4 s; `logcat -b crash` không có dòng app. **Xoay ngang và chữ lớn 1.3: CHƯA KIỂM** (cần đổi cài đặt hệ thống) | PASS (xoay/chữ lớn: CHƯA KIỂM) |
| Q7 | Đặt lại, trả EQ Tắt | Phẳng, Tắt | Log `preset=flat` rồi `enabled=false`; 10 thanh 0 dB (ảnh 18) | PASS |

Lỗi QA và xử lý: (1) hàng mẫu không cuộn tới chip đang chọn → đã sửa (`MeloEqPanel.presetRow`, commit `e2faf85`). Không crash. Ảnh có ảnh bìa của người thật (Deck bài #3) **không** đưa lên Drive/ảnh tổng hợp; ảnh thư viện (có tên bài) cũng không upload.

`SONY_SCREEN_END`: màn tắt bằng `keyevent 223` trong `finally`, xác minh `Display State=OFF`, `mWakefulness=Dozing`; nhạc **tạm dừng** (do QA phát, không phải phiên của chủ dự án), app ở màn hình chính. EQ về **Tắt, Phẳng**; Hẹn giờ không đụng tới; không đổi hiệu ứng âm thanh hệ thống, không đăng xuất, không xóa dữ liệu. Ảnh tổng hợp `docs/plans/artifacts/2026-11-02-bo-chinh-am-eq/00_tong_hop.png` (ảnh nằm `.gitignore`).

## 7. S9 — đo EQ Bật và Tắt (Sony, Debug, ~2 phút mỗi lượt, cùng bài)

| Chỉ số | EQ Bật (Rock) | EQ Tắt |
|---|---|---|
| CPU tiến trình (Δ thời gian CPU / thời gian) | ≈ 20,4 s / ~105 s ≈ **19–20%** của 1 nhân | ≈ 13,5 s / ~105 s ≈ **13%** của 1 nhân |
| Xử lý EQ (log) | 1,6–2,3 ms mỗi buffer | không lọc (bypass) |
| `dumpsys media.audio_flinger` | FastMixer `underruns=16` trước và sau (không tăng); “Normal mixer empty” 151→153 | `underruns=16` trước và sau; empty 163→163 |
| Mẫu bị chặn biên | Rock: 0–22 / 10 s (≈ 882.000 mẫu); Tăng bass + 1 kHz +4: 104–215 / 10 s trên đoạn to nhất (RMS vào −8 dB) | — |

Kết luận: EQ làm tăng khoảng **7 điểm phần trăm 1 nhân** trên máy này (Debug, 44,1 kHz stereo), **underrun không tăng**, không nghe/ghi nhận ngắt nhạc. Chưa đo Release, hi-res (96/192 kHz) hay máy khác. Chặn biên cứng đủ để không tràn nhưng khi nhiều dải dương chồng nhau (Tăng bass + 1 kHz +4) preamp tự động −6 dB chưa đủ bù cho tổng các dải nên có vài trăm mẫu bị cắt mỗi 10 s ở đoạn to. Mức này rất nhỏ (~0,02%) nhưng chủ dự án nên nghe thử; nếu thấy rè có thể REWORK: preamp = −(tổng độ tăng gộp tính theo đáp tuyến) hoặc thêm dư 2–3 dB. Không tự đổi trong phiên này.

## 8. Gate sau sửa giao diện
Sau commit `e2faf85` gate đầy đủ chạy lại trên Ni: `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` BUILD SUCCESSFUL, mỗi biến thể **203 suite, 2754 test, 21 skipped, 0 fail, 0 error** (bằng số trước khi sửa vì chỉ đổi một hàm giao diện); `assembleShareableDebug/Release`, `-PmeloOpen=true` Debug, `assemblePersonalDebug` BUILD SUCCESSFUL; `git diff --check` sạch (log `s11-*.log`). Không chạy lại test trên Tiny.

## 9. APK QA bàn giao chủ dự án nghiệm thu
Xem [apk-test](2026-11-02-bo-chinh-am-eq-apk-test.md) và [hình](2026-11-02-bo-chinh-am-eq-hinh-nghiem-thu.md). Nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**. Drive Ni N owner-only: thư mục `MeloVault-EQ-QA-CANDIDATE-20261102-vc121-e2faf85`, APK, `hinh` — `get_file_permissions` = chỉ `ntdung.md@gmail.com` (owner). Tải bằng trình duyệt chưa thử.

## 10. Chưa kiểm / giới hạn còn lại
- **CHƯA XÁC NHẬN ÂM THANH THỰC**; chỉ Sony XQ-AS52 (Android 12), bài MP3 44,1 kHz. Chưa thử FLAC/hi-res/“Nhạc trên máy”, tai nghe/Bluetooth, Y700/S25/FYT.
- Xoay ngang / chữ lớn 1.3 / Teal sáng / Gọn thủ công: chưa kiểm. Bảng EQ khi activity bị tạo lại: chưa thử.
- Hiệu ứng hệ thống Sony (Dolby/ClearAudio+): không đổi; nếu đang bật thì cộng thêm lên EQ của app.
- Thanh trượt tô từ trái sang thumb (cả quanh mức 0) và công tắc dùng màu hệ thống (xanh) — chưa theo màu nhấn; chip phía sau bảng chỉ cập nhật sau khi đóng bảng. Đều là chi tiết thẩm mỹ, chưa sửa.
- Release/`.open`/Personal chạy trên máy: chưa.
