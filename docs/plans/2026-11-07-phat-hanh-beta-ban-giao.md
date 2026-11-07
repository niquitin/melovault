# Bàn giao — Phát hành bản beta cho bạn bè

## Checkpoint chuẩn bị đăng · 07/11/2026 · Claude Code, Opus 5.5

**ĐẠT** (chủ dự án, chat 07/11) → [nghiệm thu](2026-11-07-phat-hanh-beta-nghiem-thu.md). Merge `cce562f` + HANDOFF `2f04846`, đã push lên `source/feature-e93-03d-y700-offline-cockpit`.

| Việc | Trạng thái |
|---|---|
| Release nháp `v0.6.97-open-beta` | **Nháp, pre-release**, target `a0694c0`, tag chưa tạo (GitHub tạo khi Publish). Tệp `MeloVault-0.6.97-open-beta.apk` (31.051.460 B, digest GitHub sha256 `54ab04c8…2ccf` khớp) và `.apk.sha256` |
| Trang | Nhánh `site/beta-0.6.97` (`0a17c7f`, từ `main` `4e632ad`), [PR #1](https://github.com/niquitin/melovault/pull/1) vào `main`, **chưa merge**, mergeable, không có CI. Xem thử cục bộ: nút tải bật, đủ thông tin, không tràn ngang. Worktree `D:\Bots\_worktrees\MeloVault-site-beta` |
| Chủ dự án làm | 1) Publish Release nháp; 2) Merge PR #1 (ngược thứ tự thì nút tải 404 tới khi Release lên) |
| Sau khi chủ dự án báo xong | Tải APK từ nút trên trang, so SHA-256, ghi vào `-nghiem-thu.md` (kế hoạch mục 4 bước 5) |

## Checkpoint sơ bộ · 07/11/2026 · Claude Code, Opus 5.5, phiên mới

**SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN** (code S1–S4, gate APK tự kiểm lại, cài Sony không đụng `app.melovault.player`, Q1–Q3, Q8). **Q4–Q7 vẫn BLOCKED** chờ chủ dự án đăng nhập bản `.open`. Biên bản: [nghiem-thu-so-bo](2026-11-07-phat-hanh-beta-nghiem-thu-so-bo.md) (7 ghi nhận nhỏ G1–G7, không chặn). Không sửa code, không build, không cài, không đăng; Sony chỉ đọc `dumpsys`, `Display State=OFF`. **CHỜ CHỦ DỰ ÁN NGHIỆM THU.** Sau ĐẠT: làm kế hoạch mục 4 (chuẩn bị đăng; chủ dự án tự bấm Publish/Merge).

**Cập nhật vòng 2:** Sony vẫn chưa đăng nhập, Q4–Q7 trên Sony BLOCKED; chủ dự án tự thử Q4–Q7 trên điện thoại riêng bằng APK Drive. Prompt dưới đây chỉ dùng lại nếu sau này cần agent kiểm trên Sony.

### Prompt phiên kiểm Q4–Q7 (chủ dự án giao 07/11/2026)

```text
MELOVAULT — KIỂM BỔ SUNG Q4–Q7 TRÊN SONY, KHÔNG CODE, KHÔNG BUILD
TASK_ID: 2026-11-07-phat-hanh-beta; nhánh dot/2026-11-07-phat-hanh-beta; worktree D:\Bots\_worktrees\MeloVault-phat-hanh-beta.
APK đã cài sẵn trên Sony QV725WEQ54: app.melovault.player.open vc122 0.6.97-open, SHA-256 54AB04C8…2CCF, source a0694c0. Không cài lại.
Đọc: CLAUDE.md, kế hoạch mục S7, báo cáo S7, nghiem-thu-so-bo.md vòng 1.
Bước 0: kiểm adb/serial/model và gói .open vẫn vc122 lastUpdateTime 15:37:52, app.melovault.player vẫn 14:11:34. Mở .open, chụp màn: nếu còn màn nhập số điện thoại thì tắt màn, nhờ chủ dự án tự đăng nhập trên Sony và chờ họ báo xong; agent KHÔNG nhập số/mã.
Q4 phát bài đã tải, tạm dừng, Tiếp; Q5 chip lọc bấm lại để bỏ lọc, không còn ☰, xoay ngang Đang phát; Q6 widget của bản .open (thêm ra Home theo mẹo Xperia, Phát/Tạm dừng/Tiếp/Trước, chạm thẻ; không gỡ widget của app.melovault.player); Q7 EQ bật/tắt. Bằng chứng: ảnh + dumpsys media_session; ghi CHƯA XÁC NHẬN ÂM THANH THỰC; Q8 lại crash buffer.
Cấm: uninstall, pm clear, xóa dữ liệu/TDLib, đổi tài khoản, đụng app.melovault.player. Ảnh không lộ tên bài/kênh khi commit. finally: trả xoay tự động, tắt màn, SONY_SCREEN_END.
Ghi vòng 2 vào nghiem-thu-so-bo.md, cập nhật bàn giao, commit tài liệu; kết luận SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN / CẦN SỬA / BLOCKED. Không push, không đăng.
```

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
