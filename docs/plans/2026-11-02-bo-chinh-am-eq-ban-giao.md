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
