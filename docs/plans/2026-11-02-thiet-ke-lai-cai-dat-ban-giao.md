# 2026-11-02-thiet-ke-lai-cai-dat — Bàn giao (checkpoint)

## Checkpoint 2 — 02/11/2026, kết thúc thực thi (Chặng 1 + Chặng 2 gộp trong một hội thoại theo lệnh chủ dự án)

1. **TASK_ID:** `2026-11-02-thiet-ke-lai-cai-dat`. Vai: thực thi (Claude Code, Sonnet 5.5). **Đã xong S0–S11.** Việc kế: không có (đã ĐẠT; prompt sơ bộ ở cuối không còn dùng).
2. **Repo/worktree:** `D:\Bots\_worktrees\MeloVault-thiet-ke-lai-cai-dat`; nhánh `dot/2026-11-02-thiet-ke-lai-cai-dat`; baseline `fa4cd09`; kế hoạch `11201fc`; mã: `091dc96` (thiết kế lại), `2242319` (email), `d52fe43` (3 sửa nhỏ từ QA) — **APK build từ `d52fe43`**; sau đó chỉ còn commit hồ sơ (`git rev-parse HEAD`).
3. **Hồ sơ:** [kế hoạch](2026-11-02-thiet-ke-lai-cai-dat.md) · [báo cáo](2026-11-02-thiet-ke-lai-cai-dat-bao-cao.md) · [apk-test](2026-11-02-thiet-ke-lai-cai-dat-apk-test.md) · [hình](2026-11-02-thiet-ke-lai-cai-dat-hinh-nghiem-thu.md). Sơ bộ: không có (chủ dự án quyết định trước). Nghiệm thu: [`-nghiem-thu.md`](2026-11-02-thiet-ke-lai-cai-dat-nghiem-thu.md) — **ĐẠT, PASS kèm giới hạn** (chủ dự án nhắn “đạt” 02/11/2026, bản `d52fe43`/`F376BCCB`).
4. **APK QA candidate (CHƯA NGHIỆM THU):** Debug vc121 `0.6.96-beta`, 33.812.590 B, SHA-256 `F376BCCBAB9B9FD9381FB7EA08068BDD9203AACB4C00078E282560971F8D77E7`, cert `eecd3df7…fce8`. Drive owner-only: [thư mục](https://drive.google.com/drive/folders/1Sm7XOBwnfh6cLwY0o8KcCvtUGt4IemRR), [APK](https://drive.google.com/file/d/10YNRVpKLeIU5WVH1B3gyyjKcIWmHVxaL/view?usp=drivesdk), [ảnh tổng hợp](https://drive.google.com/file/d/1PEXfWTYKmcnUqJdnhRHoQEGiMDuqwmLF/view?usp=drivesdk). Đã cài trên Sony `QV725WEQ54` (SHA bản cài khớp).
5. **Gate Ni (trên `d52fe43`):** Debug/Release mỗi biến thể 201 suite, **2725 test, 21 skipped, 0 lỗi** (baseline 2698); assemble Shareable Debug/Release, `-PmeloOpen=true`, Personal Debug xanh; `git diff --check` sạch.
6. **QA Sony:** Q1–Q7 PASS; Q8 (xoay ngang, chữ lớn) **CHƯA KIỂM**; soạn thư **CHƯA KIỂM** (Sony không có app email); **CHƯA XÁC NHẬN ÂM THANH THỰC**. Lỗi “chữ bị che” **không tái hiện trên Sony Android 12**; sửa theo phân tích mã, cần xem trên máy Android 15+.
7. **Sony hiện:** vc121 bản `d52fe43`, màn OFF (`SONY_SCREEN_END`), prefs đã khôi phục (Gọn Tự động, Car Tắt, giả lập Tắt, công cụ nhà phát triển ẩn, màu Hiện tại, Lời nhạc đã đồng ý).
8. **Sau ĐẠT (chỉ thị chủ dự án 02/11):** agent merge `--no-ff` vào `feature/e93-03d-y700-offline-cockpit` và push fast-forward lên `source/feature-e93-03d-y700-offline-cockpit` (xem mục đầu `HANDOFF.md` để biết SHA). Không đụng `main`, không push nhánh `dot/*`, **chưa phát hành APK công khai**. Không tự chọn nhiệm vụ kế. Email `ntdung.md@gmail.com` công khai cùng lần push.
9. **File chưa commit:** không (ảnh/log ở `docs/plans/artifacts/…` bị `.gitignore`).

| Bước | Trạng thái |
|---|---|
| Kế hoạch | Xong |
| S0–S8 (Ni) | Xong |
| S9 (Tiny build + Sony QA) | Xong (Q8 chưa kiểm) |
| S10 (Drive owner-only) | Xong, đã kiểm quyền |
| S11 (hồ sơ + prompt sơ bộ) | Xong |
| Nghiệm thu sơ bộ | Bỏ qua (chủ dự án ĐẠT trước) |
| Nghiệm thu chính thức | **ĐẠT** (PASS kèm giới hạn) |

### 3–6 thao tác chủ dự án tự thử (Sony hoặc máy Android 15+)

1. Cài APK từ Drive (hoặc đã cài sẵn trên Sony) → ⋮ → Cài đặt: chỉ còn Giao diện, Thư viện & đồng bộ, Giới thiệu, Đăng xuất; **trên máy Android 15+ kiểm không chữ nào bị thanh trạng thái/thanh điều hướng che** (đây là điểm chưa chứng minh).
2. Giới thiệu: thấy Nguyễn Dũng, phiên bản, GitHub mở được, “Góp ý qua email” mở app email đúng địa chỉ (không cần gửi).
3. Chạm dòng Phiên bản 7 lần → quay lại Cài đặt có “Công cụ nhà phát triển” (Gọn, Car Mode, giả lập vẫn còn) → bấm “Ẩn” để giấu.
4. Tắt rồi bật Lời nhạc: lúc bật hiện hộp đồng ý (Hủy giữ Tắt).
5. Bật “Thử trong khung Shanling” trong công cụ → Ẩn công cụ: trang chính vẫn có dòng “Đang thử khung giả lập”, chạm để về bình thường.
6. Xem trang chính khi xoay ngang và chữ lớn (Q8 chưa kiểm).

### Prompt nghiệm thu sơ bộ (dán vào phiên MỚI)

```text
MELOVAULT — NGHIỆM THU SƠ BỘ NHANH, KHÔNG CODE
TASK_ID: 2026-11-02-thiet-ke-lai-cai-dat; nhánh dot/2026-11-02-thiet-ke-lai-cai-dat; worktree: D:\Bots\_worktrees\MeloVault-thiet-ke-lai-cai-dat.
Baseline: fa4cd09; HEAD dự kiến: commit hồ sơ ngay sau d52fe43 (kiểm bằng git rev-parse HEAD); source APK: d52fe43affdb74422224dcc703321214c00b7a77.
Kế hoạch: docs/plans/2026-11-02-thiet-ke-lai-cai-dat.md.
Báo cáo: docs/plans/2026-11-02-thiet-ke-lai-cai-dat-bao-cao.md.
Bàn giao: docs/plans/2026-11-02-thiet-ke-lai-cai-dat-ban-giao.md.
APK và SHA-256: Drive owner-only https://drive.google.com/file/d/10YNRVpKLeIU5WVH1B3gyyjKcIWmHVxaL/view?usp=drivesdk ; F376BCCBAB9B9FD9381FB7EA08068BDD9203AACB4C00078E282560971F8D77E7 (33.812.590 B); hồ sơ: 2026-11-02-thiet-ke-lai-cai-dat-apk-test.md.
Phạm vi cần kiểm nhanh: (1) diff `fa4cd09..HEAD` chỉ ở MeloUiLabActivity/MeloSettingsPolicy/MeloAboutPolicy/MeloLyricsUiPolicy + 1 dòng TelegramPreviewActivity + icon/raw + test, không đụng service/Media3/DB/TDLib/package/signer/versionCode; (2) chống kẹt: Car Mode đang bật hoặc khung giả lập còn bật luôn có lối tắt ở trang chính dù công cụ đã ẩn; Page.fromName không mở lại trang ẩn; (3) câu Quyền riêng tư đúng với mã; (4) trên Sony đã cài: trang chính, Giới thiệu, 7 chạm + Ẩn, hộp đồng ý Lời nhạc (Hủy giữ Tắt — nhớ trả Lời nhạc về BẬT, Gọn/Car/giả lập về như cũ, công cụ ẩn), không bấm Đồng bộ ngay, không gửi email, không đăng xuất; (5) ghi lại phần CHƯA KIỂM: máy Android 15+, soạn thư, Q8 xoay ngang/chữ lớn, âm thanh thật.
Quy tắc: chỉ MỘT lệnh adb install -r nếu thật sự cần cài lại (cùng package/vc121/cert); không uninstall, pm clear, xóa dữ liệu; màn Sony OFF khi không thao tác, ghi SONY_SCREEN_END. Không sửa code, không build APK mới. Kết luận một trong: SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN / SƠ BỘ: CẦN SỬA / SƠ BỘ: BLOCKED vào docs/plans/2026-11-02-thiet-ke-lai-cai-dat-nghiem-thu-so-bo.md.
```

Đã dừng. Mở phiên mới để nghiệm thu sơ bộ nhanh.
