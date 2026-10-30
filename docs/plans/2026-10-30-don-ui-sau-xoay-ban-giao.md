# Bàn giao — 2026-10-30-don-ui-sau-xoay

## Checkpoint hiện tại

**CẬP NHẬT 30/10/2026 18:11 +07: chủ dự án trả lời "Đạt" → PASS có giới hạn (xem [nghiệm thu](2026-10-30-don-ui-sau-xoay-nghiem-thu.md)); chưa merge/push/public — cần chỉ thị riêng.** Nội dung dưới là trạng thái lúc bàn giao.

- 30/10/2026 ~18:10 +07, Claude Code (Opus 5.5), thực thi nhỏ một phiên. **S0–S6 xong. Đã dừng, chờ chủ dự án nghiệm thu.** Trạng thái: QA CANDIDATE — CHƯA NGHIỆM THU.
- Worktree `D:\Bots\_worktrees\MeloVault-don-ui-sau-xoay`, nhánh `dot/2026-10-30-don-ui-sau-xoay`, base `805a800`. **HEAD source đã build/QA = `8bf9d03`**; commit sau đó chỉ là hồ sơ. Git trên Ni chưa có identity: commit bằng `git -c user.name/user.email`, không đổi config global. Không merge/push.
- Hồ sơ: [Kế hoạch](2026-10-30-don-ui-sau-xoay.md) · [Báo cáo](2026-10-30-don-ui-sau-xoay-bao-cao.md) · [APK](2026-10-30-don-ui-sau-xoay-apk-test.md) · [Hình](2026-10-30-don-ui-sau-xoay-hinh-nghiem-thu.md) · [Nghiệm thu](2026-10-30-don-ui-sau-xoay-nghiem-thu.md).
- **Quyết định chủ dự án 30/10/2026 17:01:** bỏ QA1 tablet và QA6 Shanling M3; mọi QA thiết bị chỉ trên Sony XQ-AS52 `QV725WEQ54` qua Tiny DESKTOP-HNP4GVR. Tablet/Shanling: **ngoài phạm vi theo quyết định chủ dự án** (không phải BLOCKED).

| Bước | Trạng thái | Bằng chứng |
|---|---|---|
| S0 kế hoạch + nguyên nhân D1–D3 | Xong | `c5339af` |
| S1 D1 | Xong | `42f6ee3` |
| S2 D2 + test | Xong | `36c042b`; test đỏ (16/1 hỏng) → xanh (16/16) |
| S3 D3 | Xong | `8bf9d03` (chỉ bằng chứng thiết bị) |
| S4 test/build Ni | Xong | Debug = Release 2268 passed / 21 skipped / 0 failed; assemble xanh; `diff --check` sạch |
| S5 Tiny build + QA Sony | Xong | Q1–Q6 ĐẠT; APK `E6EC8773…E92C`; **SONY_SCREEN_END=OFF** 18:02:57 |
| S6 Drive + hồ sơ | Xong | Drive owner-only, đọc lại SHA khớp |

- Thiết bị cuối phiên: Sony chạy APK Debug candidate, giao diện "Hiện tại" (đã trả lại sau Q5 Teal), Đảo tắt, không phát, màn OFF. Tiny: clone `C:\Bots\MeloVault-DONUI-IsolatedQA-20261030-8bf9d03`, QA `C:\Bots\_qa_temp\don-ui-8bf9d03-20261030\`. Drive còn thư mục `_transfer_don-ui-8bf9d03` (bundle).
- `docs/plans/artifacts/` bị `.gitignore`: log Gradle ở `docs/plans/artifacts/2026-10-30-don-ui-sau-xoay/` trên Ni chỉ là bản cục bộ.

## Quyết định chủ dự án

- 30/10/2026 18:11 +07 — **ĐẠT** (tin nhắn "Đạt"), cho `8bf9d03` / APK `E6EC8773…E92C`. Giới hạn giữ nguyên như hồ sơ nghiệm thu. Merge/push chờ chỉ thị riêng.
