# Bàn giao — Phát hành bản beta cho bạn bè

## Checkpoint sơ bộ · 07/11/2026 · Claude Code, Opus 5.5, phiên mới

**SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN** (code S1–S4, gate APK tự kiểm lại, cài Sony không đụng `app.melovault.player`, Q1–Q3, Q8). **Q4–Q7 vẫn BLOCKED** chờ chủ dự án đăng nhập bản `.open`. Biên bản: [nghiem-thu-so-bo](2026-11-07-phat-hanh-beta-nghiem-thu-so-bo.md) (7 ghi nhận nhỏ G1–G7, không chặn). Không sửa code, không build, không cài, không đăng; Sony chỉ đọc `dumpsys`, `Display State=OFF`. **CHỜ CHỦ DỰ ÁN NGHIỆM THU.** Sau ĐẠT: làm kế hoạch mục 4 (chuẩn bị đăng; chủ dự án tự bấm Publish/Merge).

## Checkpoint cuối thực thi · 07/11/2026 · Claude Code, Sonnet 5.5, phiên GỘP (S0–S8)

**Thực thi xong về kỹ thuật. Chờ phiên nghiệm thu sơ bộ mới, rồi chủ dự án nghiệm thu.** Q4–Q7 BLOCKED vì Sony chưa đăng nhập bản `.open`. Chưa đăng, chưa tag, chưa Release.

| Mục | Giá trị |
|---|---|
| Worktree / nhánh | `D:\Bots\_worktrees\MeloVault-phat-hanh-beta` / `dot/2026-11-07-phat-hanh-beta` |
| Baseline | `71189c9` (nhánh tích hợp có merge widget `7ce38ec`) |
| Commit | `9526d35` code S1–S4; `3511ea4` báo cáo + sửa test; **`a0694c0` sửa nhãn Preview = source APK**; sau đó chỉ tài liệu |
| Gate Ni | Debug/Release mỗi loại 209 suite, 2.824 test, 21 skipped, 0 lỗi (baseline 208 / 2.815) |
| APK QA | `app.melovault.player.open` vc122 `0.6.97-open`, Release, 31.051.460 B, SHA-256 `54AB04C8A0F4418B379EA2DA19FFE281283D84E4A46B5DE90BE7DD8FDFC42CCF`, cert `eecd3df7…fce8` |
| Drive (owner-only) | [thư mục](https://drive.google.com/drive/folders/16_4T98kAlVHbHYrSdjDwcRfLPYSCnY-C); hồ sơ [apk-test](2026-11-07-phat-hanh-beta-apk-test.md), [hình](2026-11-07-phat-hanh-beta-hinh-nghiem-thu.md), [báo cáo](2026-11-07-phat-hanh-beta-bao-cao.md) |
| File chưa commit | Không (artifact bị `.gitignore`) |
| Job còn chạy | Không |

| Bước | Trạng thái |
|---|---|
| S0 | Xong: cây sạch, widget `7ce38ec` đã gộp, nghiệm thu widget ghi ĐẠT |
| S1 | Xong: nhãn theo `meloOpen` (resValue), giữ khóa debug, vc122 |
| S2 | Xong. **Kế hoạch bỏ sót nhãn chữ hoa "…PREVIEW" luôn hiện; QA lượt 1 phát hiện, sửa ở `a0694c0`** |
| S3 | Xong, **lệch kế hoạch có lý do**: Giới thiệu/Giấy phép đã có sẵn từ nhiệm vụ Cài đặt, mở rộng thay vì tạo Activity trùng |
| S4 | Xong: quy tắc sao lưu riêng cho Shareable; chưa thử `bmgr`/chuyển máy |
| S5 | Xong |
| S6 | Xong: gate APK đạt hết (aapt, hash open có/không, apksigner) |
| S7 | Q1, Q2, Q3, Q8 PASS; **Q4–Q7 BLOCKED** (chưa đăng nhập) |
| S8 | Xong: Drive owner-only đã kiểm, hồ sơ viết |
| Sơ bộ, ĐẠT, chuẩn bị đăng | Chưa làm (kế hoạch mục 4) |

**Sony** `QV725WEQ54`: cài APK QA ở trên cho gói `.open` (từ vc121 lên vc122); `app.melovault.player` (widget candidate `58BD55F0…`) không đổi; Display State=OFF (`SONY_SCREEN_END`).

**Quyết định cần chủ dự án (không tự làm):** (1) đăng nhập `.open` trên Sony để có Q4–Q7, hoặc giao phiên QA tiếp; (2) hai nút "Nhập lại mã API" / "Dùng mã API riêng (nâng cao)" trên màn đăng nhập bản open: giữ hay giấu; (3) nhãn "Thử nghiệm" ở Đồng bộ: giữ hay bỏ; (4) rủi ro tin tài trợ Telegram (kế hoạch mục 6).

## Prompt phiên nghiệm thu sơ bộ

```text
MELOVAULT — NGHIỆM THU SƠ BỘ NHANH, KHÔNG CODE
TASK_ID: 2026-11-07-phat-hanh-beta; nhánh dot/2026-11-07-phat-hanh-beta; worktree: D:\Bots\_worktrees\MeloVault-phat-hanh-beta.
Baseline: 71189c9; HEAD dự kiến: HEAD của nhánh (xem git log; commit sau a0694c0 chỉ là tài liệu); source APK: a0694c05b8df22ef90a01cdd52738528747341b7.
Kế hoạch: docs/plans/2026-11-07-phat-hanh-beta.md.
Báo cáo: docs/plans/2026-11-07-phat-hanh-beta-bao-cao.md.
Bàn giao: docs/plans/2026-11-07-phat-hanh-beta-ban-giao.md.
APK và SHA-256: Drive owner-only trong docs/plans/2026-11-07-phat-hanh-beta-apk-test.md; app.melovault.player.open vc122 0.6.97-open, SHA-256 54AB04C8A0F4418B379EA2DA19FFE281283D84E4A46B5DE90BE7DD8FDFC42CCF; file Tiny C:\Bots\_qa_temp\beta-20261107\r2\.
Phạm vi cần kiểm nhanh: MeloPublicBuildPolicy và nơi gắn (nhãn open, ẩn QR/dev tools, dòng không chính thức); resValue app_name; quy tắc sao lưu shareable; danh sách giấy phép so với cây phụ thuộc; gate APK; cài lên Sony không đụng app.melovault.player; Q4–Q7 chỉ kiểm nếu chủ dự án đã đăng nhập bản .open; không đăng gì.
```

Đã dừng. Mở phiên mới để nghiệm thu sơ bộ nhanh.
