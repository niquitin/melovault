# Bàn giao — Sửa nút Bộ lọc, chip thể loại và màn Đang phát khi xoay ngang

## Checkpoint nghiệm thu chính thức · 07/11/2026 · Claude Code, Opus 5.5

**ĐẠT — PASS kèm giới hạn**, chủ dự án nhắn "ĐẠT, giữ N1". N1 giữ nguyên, không sửa. Biên bản: [nghiem-thu](2026-11-07-sua-bo-loc-dang-phat-ngang-nghiem-thu.md). Trạng thái merge/push: mục đầu `HANDOFF.md` trên nhánh tích hợp.

## Checkpoint nghiệm thu sơ bộ · 07/11/2026 · Claude Code, Opus 5.5

**SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN**, có một lỗi nhỏ N1: tên 3 dòng cộng dòng "Tạm dừng" ở dạng ngang thấp làm hàng Đảo/Lặp/Lưu mất đáy 16 px (110/126 px), vuốt 16 px thì lộ đủ. Biên bản: [nghiem-thu-so-bo](2026-11-07-sua-bo-loc-dang-phat-ngang-nghiem-thu-so-bo.md). **CHỜ CHỦ DỰ ÁN NGHIỆM THU**: chủ dự án quyết định ĐẠT hoặc giao REWORK N1 ở phiên thực thi mới. Không sửa mã, không build, không merge, không push.

**Sony khi dừng:** vẫn APK `f054637` (hash máy khớp), EQ Tắt / Phẳng, hẹn giờ tắt, nhạc tạm dừng, xoay tự động, Display State=OFF. Bài đang chọn và hàng đợi khác trước phiên sơ bộ.

## Checkpoint cuối thực thi S0–S9 (phiên GỘP) · 07/11/2026 · Claude Code, Sonnet 5.5

**Thực thi xong, chờ nghiệm thu sơ bộ ở phiên mới. QA CANDIDATE — CHƯA NGHIỆM THU.** Commit code: S1 `1efb96b`, S2 `701d834`, S3 `20b3011`, S4 `cb13c21`, S5 `233954f`; source APK `f054637` (mã app trùng `233954f`). Gate Ni: Debug và Release đều 207 suite, 2.799 test, 21 skipped, 0 failures, 0 errors; assemble Shareable Debug/Release, `.open` Debug, Personal Debug xanh; `git diff --check` sạch. APK Debug 33.910.894 B, SHA-256 `7FB94021…7A24`, vc122, cert `eecd3df7…fce8`, config true, đã cài `-r` lên Sony `QV725WEQ54` và hash máy khớp. QA Sony Q1–Q6 PASS (Q6 có ghi chú 8 px ở màn dọc). Drive owner-only đã kiểm. **CHƯA XÁC NHẬN ÂM THANH THỰC.** Không file chưa commit, không job chạy, không merge/push.

**Bản đang cài trên Sony:** APK nhiệm vụ này (`f054637`, không widget). Candidate widget `58BD55F0…` đã bị thay; bản đó còn ở `C:\Bots\_qa_temp\sua-bo-loc-20261107-f054637\preinstall-installed.apk` trên Tiny. Nhạc đang tạm dừng, màn OFF (`SONY_SCREEN_END`), xoay màn tự động.

Hồ sơ: [báo cáo](2026-11-07-sua-bo-loc-dang-phat-ngang-bao-cao.md) · [apk-test](2026-11-07-sua-bo-loc-dang-phat-ngang-apk-test.md) · [ảnh](2026-11-07-sua-bo-loc-dang-phat-ngang-hinh-nghiem-thu.md).

### Chủ dự án tự thử (Sony, bản vừa cài)

1. Thư viện: ô tìm không còn ☰ ở cuối.
2. Chạm Mixset rồi chạm lại: lọc bỏ, ô tìm trống.
3. Mở bài đang phát, xoay ngang: bìa cạnh tên, thấy đủ Đảo/Lặp/Lưu mà không cuộn.
4. Ở màn ngang đó: EQ và Hẹn giờ thẳng hàng, chạm được.
5. Xoay dọc lại: màn Đang phát như cũ, nhạc vẫn chạy.

### Prompt phiên nghiệm thu sơ bộ (mở phiên mới, chọn model rồi dán)

```text
MELOVAULT — NGHIỆM THU SƠ BỘ NHANH, KHÔNG CODE
TASK_ID: 2026-11-07-sua-bo-loc-dang-phat-ngang; nhánh dot/2026-11-07-sua-bo-loc-dang-phat-ngang; worktree: D:\Bots\_worktrees\MeloVault-sua-bo-loc-dang-phat-ngang.
Baseline: f82185ba2ef17fce97d4680de2cd2dc86b2e1b4a; HEAD dự kiến: commit mới nhất của nhánh (chỉ thêm tài liệu sau f054637); source APK: f05463709faf532949d58a31ef7a46c67211cfd6 (mã app trùng 233954f).
Kế hoạch: docs/plans/2026-11-07-sua-bo-loc-dang-phat-ngang.md.
Báo cáo: docs/plans/2026-11-07-sua-bo-loc-dang-phat-ngang-bao-cao.md.
Bàn giao: docs/plans/2026-11-07-sua-bo-loc-dang-phat-ngang-ban-giao.md.
APK và SHA-256: Drive owner-only (link trong 2026-11-07-sua-bo-loc-dang-phat-ngang-apk-test.md), MeloVault-0.6.97-beta-vc122-sua-bo-loc-f054637-QA-CANDIDATE-debug.apk, SHA-256 7FB940219704E78A754971D9773DE5CE93CE18422277C53262EC144890FB7A24, 33.910.894 B, vc122, đã cài trên Sony QV725WEQ54.
Phạm vi cần kiểm nhanh: D1 bỏ nút ☰ kiểu mặc định (diff MeloComponents.searchBarWithField); D3 chạm lại chip thể loại để bỏ lọc (MeloLibraryPolicy.toggledGenre, MeloLibraryPane.renderChips); D4 căn EQ/Hẹn giờ; D2 dạng ngang thấp (MeloStudioDeck: landscapeCompact, compactTopBar, mediaRow, requestArtFit, sizeArt, applyTight; mốc MeloNowPlayingPolicy.landscapeCompact < 500 dp). Rủi ro: màn dọc và ngang cao (tablet, xe) không đổi; chữ lớn; máy 16:9; tên bài 3 dòng; Teal và Gọn chưa thử. Ngoài phạm vi: hàng Tiếp theo|Lời nhạc ở màn dọc, Yêu thích, EQ clipped, trang Đồng bộ, widget, Teal, tablet, xe.
```

Đã dừng. Mở phiên mới để nghiệm thu sơ bộ nhanh.

## Checkpoint lập kế hoạch · 07/11/2026 · Claude Code, Opus 5.5 (lịch sử)

**Kế hoạch xong, chưa code.** Bước tiếp: phiên thực thi mới, model do chủ dự án chọn. Quyết định hiệu lực (chủ dự án chốt trong chat 07/11/2026): bỏ nút ☰ ở kiểu mặc định; bìa đặt cạnh tên bài khi ngang thấp; chạm lại chip thể loại để bỏ lọc; sửa cùng chip Hẹn giờ lệch.

| Mục | Giá trị |
|---|---|
| TASK_ID | `2026-11-07-sua-bo-loc-dang-phat-ngang` |
| Worktree / nhánh | `D:\Bots\_worktrees\MeloVault-sua-bo-loc-dang-phat-ngang` / `dot/2026-11-07-sua-bo-loc-dang-phat-ngang` |
| Baseline | `f82185ba2ef17fce97d4680de2cd2dc86b2e1b4a` |
| Commit kế hoạch | `git log -1 --format=%H -- docs/plans/2026-11-07-sua-bo-loc-dang-phat-ngang.md` |
| Kế hoạch | [2026-11-07-sua-bo-loc-dang-phat-ngang.md](2026-11-07-sua-bo-loc-dang-phat-ngang.md) |
| File chưa commit | Không |
| Job còn chạy | Không |

| Bước | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| Lập kế hoạch | Xong | Nguyên nhân D1, D3 từ mã; D2 khớp số đo Sony; D4 là suy luận, S3 kiểm lại |
| S0–S6 code và gate Ni | Xong (07/11/2026) | Xem checkpoint trên cùng và báo cáo |
| S7–S9 build, QA Sony, bàn giao | Xong (07/11/2026) | APK `f054637` đã cài Sony, QA Q1–Q6, Drive owner-only; xem checkpoint trên cùng |
| Sơ bộ | Xong (07/11/2026) | Xem checkpoint trên cùng |
| Nghiệm thu chủ dự án | ĐẠT (07/11/2026) | "ĐẠT, giữ N1"; xem checkpoint trên cùng |

## Trạng thái Sony khi lập kế hoạch

Khảo sát bằng APK sẵn có, không cài gì. Sony `QV725WEQ54` đang giữ candidate widget vc122 `58BD55F0…`, nhạc tạm dừng, không có widget trên Home. `SONY_SCREEN_END`: Display State=OFF, đo lúc kết thúc phiên sơ bộ 07/11, không đo lại khi viết kế hoạch.

Bằng chứng khảo sát ở ngoài repo: biên bản `D:\Bots\_handoff\MeloVault\2026-11-07-so-bo-toan-app\`, ảnh riêng tư `C:\Bots\_qa_temp\sobo-20261107\` trên Tiny (`42_ngang_deck.png`, `45_ngang_deck_cuon.png`, `50_bo_loc_2.png`). Không commit các ảnh này.

## Lưu ý cho phiên thực thi

- Cài APK nhiệm vụ này sẽ thay candidate widget trên Sony. Ghi bản đang cài vào bàn giao; widget sơ bộ sau đó phải cài lại APK của nó.
- Repo GitHub công khai và hồ sơ sẽ lên đó sau ĐẠT: không ghi tên bài, tên kênh hay dữ liệu Telegram vào file `.md`.
- Khi thử trên Sony, gửi phím qua `input keyevent` hoặc `input text` lúc không có ô nhập đang focus sẽ thoát touch mode, và phím cách có thể bấm trúng nút đang focus (đã gặp: bật Đảo). Luôn dump UI kiểm tọa độ trước khi chạm, vì vị trí đổi theo độ dài tên bài và độ cuộn.
- Lệnh DC `start_process` nuốt `$`: ghi script `.ps1` qua `write_file` rồi chạy `-File`. Ghi kết quả UTF-8 ra file và đọc qua `T:` để giữ tiếng Việt. `uiautomator dump` có thể lỗi im lặng nên phải xóa file cũ trước khi pull.

## Prompt phiên thực thi

Chủ dự án mở phiên mới, chọn model rồi dán:

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-sua-bo-loc-dang-phat-ngang
TASK_ID: 2026-11-07-sua-bo-loc-dang-phat-ngang
Nhánh: dot/2026-11-07-sua-bo-loc-dang-phat-ngang
Kế hoạch đã được chủ dự án CHỐT: docs/plans/2026-11-07-sua-bo-loc-dang-phat-ngang.md
Bàn giao: docs/plans/2026-11-07-sua-bo-loc-dang-phat-ngang-ban-giao.md
HEAD dự kiến: commit mới nhất của nhánh, chỉ có tài liệu so với baseline
Baseline: f82185ba2ef17fce97d4680de2cd2dc86b2e1b4a
PHIÊN: GỘP (S0–S9), hoặc chủ dự án tách: 1 = S0–S6, 2 = S7–S9
Lưu ý: 4 mục D1–D4, không mở rộng phạm vi. Không tăng versionCode.
Cài APK sẽ thay candidate widget trên Sony, ghi rõ trong bàn giao.
Làm theo docs/workflow/PROMPT_SONNET.md (Codex: PROMPT_CODEX.md). Trả lời tiếng Việt.
```

Đã dừng. Mở phiên mới, chọn model thực thi tiết kiệm hơn theo lựa chọn của chủ dự án.
