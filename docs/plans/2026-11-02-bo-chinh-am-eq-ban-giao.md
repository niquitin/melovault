# 2026-11-02-bo-chinh-am-eq — Bàn giao (checkpoint)

## Checkpoint 1 — 02/11/2026, kết thúc lập kế hoạch

1. **TASK_ID:** `2026-11-02-bo-chinh-am-eq`. Vai: **lập kế hoạch** (Claude Code, Opus 5.5). Chủ dự án giao trong chat: “lập plan về EQ”. Quyết định đã chốt trong chat (LD1–LD4, kế hoạch mục 1): tự xử lý EQ trong app bằng Media3 `AudioProcessor`; mẫu sẵn + 10 dải ±12 dB; chỉ mở từ nút EQ ở màn Đang phát; EQ theo đầu ra để nhiệm vụ sau.
2. **Repo/worktree:** `D:\Bots\_worktrees\MeloVault-eq` (giữ tên thư mục này; đổi tên thư mục bị Windows từ chối); nhánh `dot/2026-11-02-bo-chinh-am-eq`; baseline `b90834f` (đầu `feature/e93-03d-y700-offline-cockpit`). Commit kế hoạch: xem `git log -1 -- docs/plans/2026-11-02-bo-chinh-am-eq-ban-giao.md`. Chưa có commit mã.
3. **Hồ sơ:** [kế hoạch](2026-11-02-bo-chinh-am-eq.md). Báo cáo/apk-test/hình/sơ bộ/nghiệm thu: chưa có (tạo ở giai đoạn tương ứng).
4. **Khảo sát Sony (chỉ đọc, 02/11/2026):** `QV725WEQ54` `XQ-AS52` Sony Android 12, `device`; màn `Dozing` suốt phiên (không đánh thức, không chạm), không phiên phát đang chạy; đọc `/vendor/etc/audio_effects.xml` (có equalizer AOSP + equalizer1–4 Sony, dynamics_processing, dap…). Không cài, không đổi gì. `SONY_SCREEN_END`: màn chưa từng bật (Dozing).
5. **Test/build:** không chạy (vai lập kế hoạch). Baseline dự kiến 2725/21/0/0 — phiên thực thi chạy lại ở S0.
6. **File chưa commit:** không (sau commit kế hoạch).
7. **Điều chưa rõ (không chặn):** giá trị dB 9 mẫu; kéo thanh khi EQ Tắt có tự bật không (mặc định không) — kế hoạch mục 10.

| Bước | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| Kế hoạch | Xong | `docs/plans/2026-11-02-bo-chinh-am-eq.md` |
| S0–S7 (Chặng 1, Ni: policy, processor, service, UI, gate, commit) | Chưa làm | — |
| S8–S11 (Chặng 2: Tiny build, Sony QA, đo CPU/underrun, Drive, hồ sơ) | Chưa làm | — |
| Nghiệm thu sơ bộ / chính thức | Chưa làm | — |

**Bước tiếp:** chủ dự án mở **phiên thực thi mới** (model tiết kiệm hơn, Codex hoặc Claude Code), bắt đầu S0. Điều kiện dừng: kế hoạch mục 9 (cần bật float output/thêm thư viện/đổi MediaSession, pipeline phát thay đổi khi EQ Tắt, underrun tăng rõ) → ghi BLOCKED.

### Prompt thực thi (dán vào phiên MỚI)

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH
Repo/worktree: D:\Bots\_worktrees\MeloVault-eq (repo chuẩn D:\Bots\MeloVault-E91-Shareable)
TASK_ID: 2026-11-02-bo-chinh-am-eq
Nhánh: dot/2026-11-02-bo-chinh-am-eq
Kế hoạch đã được chủ dự án CHỐT (LD1–LD4 trong chat 02/11): docs/plans/2026-11-02-bo-chinh-am-eq.md
Bàn giao: docs/plans/2026-11-02-bo-chinh-am-eq-ban-giao.md; HEAD dự kiến: commit kế hoạch ngay trên b90834f (kiểm bằng git log -1 -- docs/plans/2026-11-02-bo-chinh-am-eq-ban-giao.md)
Model: <chủ dự án chọn>; bước tiếp tục: S0
Baseline: b90834f
PHIÊN: 1 = code + test Ni (S0–S7) | 2 = Tiny build + Sony QA + Drive + hồ sơ (S8–S11) | GỘP nếu chủ dự án giao
Lưu ý riêng:
- EQ chỉ trong source set shareable: MeloEqPolicy.kt, MeloEqAudioProcessor.kt, MeloEqPanel.kt (mới); sửa ShareablePlaybackService.kt (RenderersFactory + DefaultAudioSink.setAudioProcessors + prefs listener eq_*) và chip EQ trong MeloStudioDeck.kt. Không đụng MusicService/GainAudioProcessor (Personal), DB, TDLib, MediaSession, build.gradle, versionCode/signer/package.
- Theo khuôn GainAudioProcessor: PCM 16/24/32/float, luôn active, bypass byte giống hệt khi Tắt hoặc Phẳng (phải có test).
- Mặc định EQ Tắt; prefs eq_enabled/eq_preset/eq_custom_db trong "melovault_ui_preferences"; load không bao giờ ném lỗi.
- Cờ hoàn tác MeloEqPolicy.FEATURE_ENABLED: false → pipeline giống hệt b90834f, chip về Toast cũ.
- Agent không nghe được âm thanh: bằng chứng = test đáp tuyến JVM + log Debug MeloEq (RMS vào/ra, µs/buffer) trên Sony; ghi CHƯA XÁC NHẬN ÂM THANH THỰC.
- Kết thúc QA: trả Sony về EQ Tắt, Phẳng; màn OFF trong finally, ghi SONY_SCREEN_END.
Làm theo docs/workflow/PROMPT_SONNET.md (hoặc PROMPT_CODEX.md nếu là Codex).
```

Đã dừng. Mở phiên mới, chọn model thực thi tiết kiệm hơn theo lựa chọn của chủ dự án.

## Checkpoint 2 — 02/11/2026, kết thúc Chặng 1 (S0–S7, thực thi Ni)

1. **Vai/phiên:** thực thi Chặng 1 (Claude Code, Sonnet 5.5). Nhánh `dot/2026-11-02-bo-chinh-am-eq`, worktree `D:\Bots\_worktrees\MeloVault-eq`, baseline `b90834f`. HEAD = commit mã (xem `git log -1`) trên commit kế hoạch `297d635`.
2. **Đã làm:** S0–S7 xong. Baseline 2725/21/0/0 → sau thay đổi **2754 test**, 21 skipped, 0 fail/error, mỗi biến thể Debug/Release (203 suite). Build Shareable Debug/Release, `-PmeloOpen=true`, Personal Debug đều xanh; `git diff --check` sạch. Chi tiết: [báo cáo](2026-11-02-bo-chinh-am-eq-bao-cao.md).
3. **File:** mới `MeloEqPolicy.kt`, `MeloEqAudioProcessor.kt`, `MeloEqPanel.kt` (+ 2 file test); sửa `ShareablePlaybackService.kt`, `MeloStudioDeck.kt`. Cờ hoàn tác `MeloEqPolicy.FEATURE_ENABLED`.
4. **Chưa kiểm chứng:** mọi thứ trên thiết bị — bố cục bảng EQ, chip, log `MeloEq`, chuyển bài/tua/xoay với EQ Bật, CPU/underrun, âm thanh thực (CHƯA XÁC NHẬN ÂM THANH THỰC). Chưa có APK, chưa có ảnh.
5. **Chưa merge/push** (chủ dự án chưa ĐẠT).

| Bước | Trạng thái |
|---|---|
| S0–S7 (Chặng 1) | **Xong** |
| S8–S11 (Chặng 2: Tiny build, Sony QA, đo CPU, Drive, hồ sơ) | Chưa làm — chờ chủ dự án giao |
| Nghiệm thu sơ bộ / chính thức | Chưa làm |

**Bước tiếp:** chủ dự án giao phiên Chặng 2 (S8). Dừng-điều-kiện như kế hoạch mục 9. Lưu ý cho Chặng 2: chụp bảng EQ ở cỡ Gọn và (nếu đổi được trong app) chữ lớn để kiểm chữ không bị cắt; lấy `logcat -s MeloEq` cho Q2/Q4/Q5; thử xoay khi bảng mở.

## Checkpoint 3 — 02/11/2026, kết thúc Chặng 2 (S8–S11, thực thi Tiny + Sony)

1. **Vai/phiên:** thực thi Chặng 2 (Claude Code, Sonnet 5.5), cùng phiên Chặng 1 theo lời chủ dự án “Làm ngay đây”. Nhánh `dot/2026-11-02-bo-chinh-am-eq`, worktree `D:\Bots\_worktrees\MeloVault-eq`, baseline `b90834f`. **HEAD mã = `e2faf85`** (mã EQ `7353587` + sửa hàng mẫu); sau đó chỉ commit hồ sơ.
2. **Đã làm:** bundle SHA-256 khớp Ni/Tiny → Tiny clone cô lập build Debug có Telegram config → verify package/vc121/cert/SHA-256/bytes → cài một lệnh `adb install -r` (hai lần: bản đầu rồi bản sửa) → QA Q1–Q7 trên Sony → đo CPU/underrun EQ Bật vs Tắt → Drive owner-only (đã kiểm quyền) → hồ sơ. Chi tiết: [báo cáo](2026-11-02-bo-chinh-am-eq-bao-cao.md) mục 6–10; [apk-test](2026-11-02-bo-chinh-am-eq-apk-test.md); [hình](2026-11-02-bo-chinh-am-eq-hinh-nghiem-thu.md).
3. **APK:** vc121 `0.6.96-beta`, 33.828.974 B, SHA-256 `146A42859CFDC3EB204FBE1396BEE1A4EFE40F0C02EC8DADE46415446E1F368D`, cert `eecd3df7…fce8`, nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**. Gate Ni trên `e2faf85`: 2754 test/21 skipped/0 lỗi mỗi biến thể; mọi assemble xanh.
4. **Lỗi QA:** hàng mẫu không cuộn tới chip “Tùy chỉnh” sau khi kéo thanh → đã sửa trong `e2faf85`, ảnh xác nhận. Không crash.
5. **Chưa kiểm chứng:** **CHƯA XÁC NHẬN ÂM THANH THỰC**; xoay ngang / chữ lớn 1.3 / FLAC-hi-res / Nhạc trên máy / tai nghe-Bluetooth / máy khác / Release trên máy / tải Drive bằng trình duyệt. Số đo: EQ Bật ≈ 19–20% một nhân so với ≈ 13% khi Tắt (Debug), underrun không tăng; một số mẫu bị chặn biên khi nhiều dải dương chồng nhau (báo cáo mục 7) — chủ dự án nghe thử, có thể REWORK preamp.
6. **Sony:** `SONY_SCREEN_END` Display State=OFF; EQ Tắt, Phẳng; Hẹn giờ không đụng; nhạc tạm dừng (do QA phát); app ở màn hình chính. Không đổi hiệu ứng hệ thống, không đăng xuất, không xóa dữ liệu.
7. **Chưa merge/push** (chủ dự án chưa ĐẠT).

| Bước | Trạng thái |
|---|---|
| S0–S7 (Chặng 1) | Xong |
| S8 build Tiny, cài, QA Q1–Q7 | Xong (Q6 xoay/chữ lớn CHƯA KIỂM) |
| S9 đo CPU/underrun | Xong |
| S10 Drive owner-only | Xong (quyền đã kiểm; tải bằng trình duyệt chưa thử) |
| S11 hồ sơ, trả Sony | Xong |
| Nghiệm thu sơ bộ / chính thức | Chưa làm |

**Bước tiếp:** chủ dự án mở phiên mới cho nghiệm thu sơ bộ nhanh, hoặc tự thử theo mục 11 của kế hoạch rồi trả lời `ĐẠT 2026-11-02-bo-chinh-am-eq — e2faf85/146A4285` hoặc `Yêu cầu sửa 2026-11-02-bo-chinh-am-eq: <lỗi>` (ví dụ chỉnh giá trị mẫu, thêm dư preamp). **Đã dừng. Mở phiên mới để nghiệm thu sơ bộ nhanh.**

### Prompt nghiệm thu sơ bộ (dán vào phiên MỚI)

```text
MELOVAULT — PHIÊN NGHIỆM THU SƠ BỘ NHANH
Repo/worktree: D:\Bots\_worktrees\MeloVault-eq   TASK_ID: 2026-11-02-bo-chinh-am-eq
Nhánh: dot/2026-11-02-bo-chinh-am-eq   HEAD mã: e2faf85 (sau đó chỉ có commit hồ sơ)
Đọc: CLAUDE.md → docs/plans/2026-11-02-bo-chinh-am-eq-bao-cao.md (mục 6–10) → -ban-giao.md → -apk-test.md → docs/workflow/PROMPT_NGHIEM_THU_SO_BO.md.
Việc: git log b90834f..HEAD --oneline và git diff --stat; đọc kỹ MeloEqAudioProcessor.kt (luồng audio, bypass byte giống hệt, ramp preamp, reset ở onFlush) và đoạn eq trong ShareablePlaybackService.kt; chạy lại chỉ test MeloEq* nếu cần. Không sửa code, không build mới. Sony có thể dùng nhưng đừng tranh thiết bị.
Ghi docs/plans/2026-11-02-bo-chinh-am-eq-nghiem-thu-so-bo.md: SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN / CẦN SỬA / BLOCKED, kèm phạm vi và giới hạn (CHƯA XÁC NHẬN ÂM THANH THỰC).
Kết thúc: "Đã dừng, chờ chủ dự án nghiệm thu." + cách phản hồi ĐẠT / Yêu cầu sửa.
```

## Checkpoint 4 — 03/11/2026, chỉnh preamp (yêu cầu “Chỉnh preamp” của chủ dự án)

**Cập nhật: HEAD mã = `cade4dc` (thay `e2faf85`); Checkpoint 3 và prompt sơ bộ bên trên đọc theo commit/APK này.**

1. **Việc:** preamp tự động nay = −đỉnh của đáp tuyến gộp 10 dải (trước: −thanh dương lớn nhất), tính theo sample rate luồng; dải bị bỏ qua gần Nyquist không còn trừ preamp. Chỉ sửa `MeloEqPolicy.kt`, `MeloEqAudioProcessor.kt` và 2 file test. Chi tiết, bảng số: [báo cáo](2026-11-02-bo-chinh-am-eq-bao-cao.md) mục 11.
2. **Kết quả:** Tăng bass −6 → −7,1 dB; Rock −5 → −5,8 dB; một thanh +6 vẫn −6 dB; chỉ cắt/Phẳng 0. Trên Sony, 4 cửa sổ 10 s mỗi cấu hình đều `clipped=0` (trước 104–215 và 0–22). Bật EQ nhỏ hơn bản trước khoảng 1 dB.
3. **Gate Ni (`cade4dc`):** 203 suite, **2759 test** mỗi biến thể, 21 skipped, 0 lỗi; mọi assemble xanh; `git diff --check` sạch.
4. **APK:** vc121 `0.6.96-beta`, 33.828.974 B, SHA-256 `76D1F355095C6E4E98A28F703BDB196BFEE1E562F1780393DF9E419F3B9BDB6C`, cert `eecd3df7…fce8`, build Tiny từ bundle `CABF62E1…8540` (khớp), cài một lệnh `install -r` → Success. Drive owner-only: [apk-test](2026-11-02-bo-chinh-am-eq-apk-test.md), [hình](2026-11-02-bo-chinh-am-eq-hinh-nghiem-thu.md). Nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**; candidate `e2faf85` còn trên Drive, **đã được thay thế**.
5. **Chưa kiểm chứng:** **CHƯA XÁC NHẬN ÂM THANH THỰC**; xoay ngang/chữ lớn; FLAC/hi-res, Nhạc trên máy, tai nghe/Bluetooth, máy khác; QA lại trên máy chỉ gồm bật/mẫu/kéo thanh/Đặt lại + log (Q1–Q7 đầy đủ làm trên `e2faf85`). Cosmetic: log Debug in `-0,0` cho Phẳng.
6. **Sony:** `SONY_SCREEN_END` Display State=OFF; EQ Tắt, Phẳng; Hẹn giờ không đụng; nhạc tạm dừng (do QA phát); app ở màn hình chính.
7. **Chưa merge/push** (chủ dự án chưa ĐẠT).

**Bước tiếp:** chủ dự án nghe thử rồi trả lời `ĐẠT 2026-11-02-bo-chinh-am-eq — cade4dc/76D1F355` hoặc `Yêu cầu sửa 2026-11-02-bo-chinh-am-eq: <lỗi>` (ví dụ muốn to hơn thì giảm độ trừ preamp, hoặc chỉnh giá trị mẫu). **Đã dừng. Mở phiên mới để nghiệm thu sơ bộ nhanh** (dùng prompt ở Checkpoint 3 với HEAD mã `cade4dc`).
