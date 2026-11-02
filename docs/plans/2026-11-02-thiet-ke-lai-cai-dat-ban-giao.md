# 2026-11-02-thiet-ke-lai-cai-dat — Bàn giao (checkpoint)

## Checkpoint 1 — 02/11/2026, kết thúc Chặng 1 (S0–S8, Ni: code + test + commit)

1. **TASK_ID:** `2026-11-02-thiet-ke-lai-cai-dat`. Vai: thực thi Chặng 1 (Claude Code, Sonnet 5.5). Chặng 2 (S9–S11) chưa bắt đầu.
2. **Repo/worktree:** `D:\Bots\_worktrees\MeloVault-thiet-ke-lai-cai-dat`; nhánh `dot/2026-11-02-thiet-ke-lai-cai-dat`; baseline `fa4cd09`; commit kế hoạch `11201fc`; commit mã: `091dc96`; HEAD sau hồ sơ: xem `git rev-parse HEAD`.
3. **Hồ sơ:** [kế hoạch](2026-11-02-thiet-ke-lai-cai-dat.md) · [báo cáo](2026-11-02-thiet-ke-lai-cai-dat-bao-cao.md). apk-test / hình / sơ bộ / nghiệm thu: chưa có.
4. **Gate Ni:** xanh — 201 suite, 2725 test, 21 skipped, 0 lỗi (Debug và Release); assemble Shareable Debug/Release, `-PmeloOpen=true` Debug, Personal Debug đều BUILD SUCCESSFUL; `git diff --check` sạch (chi tiết mục 6 báo cáo; log ở `docs/plans/artifacts/2026-11-02-thiet-ke-lai-cai-dat/`).
5. **CĐR1 email góp ý: CHƯA CÓ.** `MeloAboutPolicy.FEEDBACK_EMAIL = ""` nên dòng “Góp ý qua email” **chưa hiện**. Chủ dự án cho địa chỉ → điền hằng số, build lại (đổi 1 dòng + test `noEmailRowUntilTheOwnerConfirmsAnAddress` phải sửa theo). CĐR2 giữ “Nguyễn Dũng”; CĐR3 mặc định.
6. **S1 chưa tái hiện** (không có thiết bị ở Ni). Giả thuyết và cách sửa ở mục 3 báo cáo. Máy trong ảnh chủ dự án gửi chưa rõ (Android 12 của Sony không bị edge-to-edge bắt buộc, nên nghi máy Android 15+). Chặng 2 phải chụp Q1 và ghi kết quả.
7. **Thiết bị:** Ni không có ADB device. Sony `QV725WEQ54` trên Tiny đang cài vc121 (theo HANDOFF), chưa đụng tới trong phiên này.
8. **File chưa commit:** không có sau commit hồ sơ.

| Bước | Trạng thái |
|---|---|
| Kế hoạch | Xong |
| S0–S8 (Chặng 1, Ni) | Xong (email chờ chủ dự án) |
| S9–S11 (Chặng 2, Tiny/Sony/Drive) | Chưa bắt đầu |

### Điều Chặng 2 cần biết

- Mã mới nằm trong `MeloUiLabActivity.kt` (khung, trang, 7 chạm), `MeloSettingsPolicy.kt`, `MeloAboutPolicy.kt`. Chưa ai nhìn giao diện thật: kỳ vọng phải chỉnh nhỏ (khoảng cách, cỡ icon, chữ bị cắt ở Gọn/chữ lớn) — nếu cần chỉnh thì sửa ở Ni qua phiên thực thi mới, không sửa trên Tiny.
- Trang Giao diện / Màn Đang phát / Thiết bị / Nâng cao vẫn dùng kiểu hàng cũ trong khung mới (có chủ đích, ghi ở báo cáo mục 7).
- Q7 (chống kẹt): trước QA ghi giá trị Gọn/Car/giả lập/handle band (mở khóa công cụ nhà phát triển cần 7 chạm, rồi **ẩn lại** sau QA); khôi phục đúng.
- Không đăng xuất, không bấm “Đồng bộ ngay”, không gửi email. Màn Sony OFF trong `finally`, ghi `SONY_SCREEN_END`.
- `dev_tools_unlocked` là khóa prefs mới (vô hại với bản cũ).

### Prompt thực thi Chặng 2 (dán vào phiên MỚI)

```text
MELOVAULT — THỰC THI CHẶNG 2 (S9–S11: build Tiny + QA Sony + Drive + hồ sơ)
TASK_ID: 2026-11-02-thiet-ke-lai-cai-dat
Worktree Ni: D:\Bots\_worktrees\MeloVault-thiet-ke-lai-cai-dat; nhánh dot/2026-11-02-thiet-ke-lai-cai-dat; baseline fa4cd09; commit mã 091dc96.
Đọc theo thứ tự: CLAUDE.md → docs/workflow/PROMPT_SONNET.md → docs/plans/2026-11-02-thiet-ke-lai-cai-dat.md (mục 6–7) → docs/plans/2026-11-02-thiet-ke-lai-cai-dat-bao-cao.md → docs/plans/2026-11-02-thiet-ke-lai-cai-dat-ban-giao.md.
Email góp ý (CĐR1): <chủ dự án điền; "chưa có" thì build vẫn không có dòng email và QA ghi CHƯA KIỂM phần email>.
Làm S9 (bundle SHA256 → Tiny clone cô lập build Debug có Telegram config → verify package/vc121/cert → cài install -r Sony QV725WEQ54 → QA Q1–Q8, ảnh tổng hợp 00_tong_hop.png, ghi SONY_SCREEN_END), S10 (Drive Ni N owner-only: APK + ảnh; *-apk-test.md, *-hinh-nghiem-thu.md), S11 (báo cáo, bàn giao, prompt sơ bộ).
Q1 phải ghi rõ kết quả "chữ bị che" (S1 chưa tái hiện ở Ni). Nếu giao diện cần chỉnh: ghi lỗi + ảnh, DỪNG và bàn giao phiên sửa ở Ni, không sửa trên Tiny.
Không đổi package/signer/versionCode, không uninstall/pm clear/xóa dữ liệu, không đăng xuất, không gửi email, không "Đồng bộ ngay".
Kết thúc: đưa prompt nghiệm thu sơ bộ và ghi "Đã dừng. Mở phiên mới để nghiệm thu sơ bộ nhanh."
```
