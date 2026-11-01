# 2026-11-01-nhay-header-khi-cuon — Bàn giao (checkpoint)

## Checkpoint 3 — 01/11/2026, kết thúc phiên thực thi GỘP S0–S10

1. **TASK_ID:** `2026-11-01-nhay-header-khi-cuon`. Vai: thực thi (Claude Code, Sonnet 5.5), phiên GỘP, đã hết chặng cuối. Yêu cầu của chủ dự án còn hiệu lực: sửa header nháy khi lướt danh sách bài theo phương án A.
2. **Repo/worktree:** `D:\Bots\_worktrees\MeloVault-nhay-header-khi-cuon`; nhánh `dot/2026-11-01-nhay-header-khi-cuon`; baseline `8a8c978`; kế hoạch `8a48584`; **commit mã `4fe5a37`** (nguồn APK).
   - Commit chứa checkpoint này: `git log -1 -- docs/plans/2026-11-01-nhay-header-khi-cuon-ban-giao.md`.
3. **Hồ sơ:** [kế hoạch](2026-11-01-nhay-header-khi-cuon.md), [báo cáo](2026-11-01-nhay-header-khi-cuon-bao-cao.md), [apk-test](2026-11-01-nhay-header-khi-cuon-apk-test.md), [hình nghiệm thu](2026-11-01-nhay-header-khi-cuon-hinh-nghiem-thu.md). Sơ bộ/nghiệm thu chính thức: chưa có (**CHỜ CHỦ DỰ ÁN NGHIỆM THU** sau sơ bộ).

| Bước | Trạng thái | Bằng chứng | Việc còn lại |
|---|---|---|---|
| S0–S6 | Xong | Báo cáo mục 1–4; test 2698/21/0/0 Debug và Release; assemble xanh | — |
| S1 baseline | Xong, tái hiện | Header đổi 8/14 lần mỗi cử chỉ giữ tay | — |
| S7 build Tiny | Xong | APK `E5D808D2…6B74`, config true, cert khớp | — |
| S8 QA Sony | Xong (Q6 CHƯA KIỂM) | Q1–Q5 PASS, mỗi cử chỉ ≤ 1 lần đổi | Q6 Hai khung |
| S9 Drive | Xong | Owner-only đã kiểm; link trong apk-test | — |
| S10 hồ sơ | Xong | Báo cáo/bàn giao | Sơ bộ rồi chủ dự án nghiệm thu |

4. **File chưa commit:** không có sau commit hồ sơ cuối (mã ở `4fe5a37`; video/ảnh trong `docs/plans/artifacts/…` bị `.gitignore`).
5. **Thiết bị:** Sony `QV725WEQ54` đang cài APK candidate vc121 `E5D808D2…6B74`; màn đã tắt (`Display State=OFF`, `SONY_SCREEN_END`). Tiny giữ clone `C:\Bots\MeloVault-NHAY-IsolatedQA-20261101-4fe5a37` và scratch `C:\Bots\_qa_temp\nhay-header\` (script `gesture.ps1`, log, video gốc). Ni giữ bản sao trong `docs/plans/artifacts/2026-11-01-nhay-header-khi-cuon/` (gồm `count_header.ps1`, `gesture.ps1`).
6. **Rủi ro/quyết định:** hành vi mới “header chỉ đổi khi nhả tay hoặc khi trôi” cần chủ dự án xác nhận; phương án B là nhiệm vụ riêng. Q3b thiếu 1 dòng log (xem báo cáo mục 6). Chưa kiểm: Playlist ≥ 20 bài, Hai khung, ngón tay thật, âm thanh thật.
7. **Trạng thái bàn giao:** phiên thực thi đã dừng; không còn tác vụ chạy (không build/ADB/recording nào đang mở).

### Thao tác để chủ dự án tự thử (Sony hoặc máy riêng; cài candidate từ Drive)

1. Tab Thư viện (hoặc Playlist dài) → vuốt nhanh lên/xuống nhiều lần: header không còn nháy, danh sách không nhảy.
2. Kéo chậm và giữ tay: header chưa đổi khi còn giữ tay, chỉ gấp/hiện khi nhả tay.
3. Giữa danh sách vuốt nhanh lên: header hiện lại một lần.
4. Đổi tab (Thư viện ↔ Đã tải): header hiện, danh sách ở đầu.

### Prompt nghiệm thu sơ bộ (dán vào phiên MỚI)

```text
MELOVAULT — NGHIỆM THU SƠ BỘ NHANH, KHÔNG CODE
TASK_ID: 2026-11-01-nhay-header-khi-cuon; nhánh dot/2026-11-01-nhay-header-khi-cuon; worktree: D:\Bots\_worktrees\MeloVault-nhay-header-khi-cuon.
Baseline: 8a8c978; HEAD dự kiến: commit hồ sơ cuối (git log -1); source APK: 4fe5a370ce58719495d63950d9dd33e6ed7a0619.
Kế hoạch: docs/plans/2026-11-01-nhay-header-khi-cuon.md.
Báo cáo: docs/plans/2026-11-01-nhay-header-khi-cuon-bao-cao.md.
Bàn giao: docs/plans/2026-11-01-nhay-header-khi-cuon-ban-giao.md.
APK và SHA-256: Drive owner-only https://drive.google.com/file/d/1KUE-zK-sMNsc3nmJTUxtJmpEV1dqc274/view — E5D808D2C29F70333350EF3682A50FBF686D6140C93513F1A6AA7534A27C6B74 (33.789.892 B, vc121 0.6.96-beta Debug); hồ sơ: 2026-11-01-nhay-header-khi-cuon-apk-test.md.
Phạm vi cần kiểm nhanh: diff 8a8c978..HEAD chỉ gồm MeloHeaderFoldPolicy.kt (mới), MeloLibraryPolicy.kt, MeloLibraryPane.kt, MeloHeaderFoldPolicyTest.kt + hồ sơ; HeaderFold không đổi header khi phase=TOUCH; nhánh isInLayout→post và resetScroll() còn đúng; log MeloHeaderFold chỉ ở Debug và không có PII; so video baseline và video sau sửa (hinh-nghiem-thu); có thể chạy lại 1 chuỗi giữ tay kéo trên Sony (gesture.ps1 trên Tiny, `HU,P,H,P,HU`) và đếm số lần đổi.
```

Lời kết dự kiến: “Đã dừng. Mở phiên mới để nghiệm thu sơ bộ nhanh.”
