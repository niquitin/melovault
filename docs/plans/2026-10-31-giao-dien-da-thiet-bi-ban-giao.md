# Bàn giao — giao diện phù hợp mọi loại thiết bị

TASK_ID: **2026-10-31-giao-dien-da-thiet-bi**. Worktree `D:\Bots\_worktrees\MeloVault-giao-dien-da-thiet-bi`; nhánh `dot/2026-10-31-giao-dien-da-thiet-bi`; baseline `94ab6b0`.

## Checkpoint lập kế hoạch — 31/10/2026, Claude Code

- Kế hoạch: [`2026-10-31-giao-dien-da-thiet-bi.md`](2026-10-31-giao-dien-da-thiet-bi.md). HEAD dự kiến khi thực thi: commit cuối của nhánh (chứa file này).
- Đã đọc mã:
  - `MeloScreenPolicy` (luật chọn bố cục, mời Car Mode, giả lập);
  - insets ở `MeloStudioDeck`;
  - manifest `configChanges`;
  - `MeloUiPreviewActivity` (chỉ xem trước thành phần, không đủ cho khảo sát).

  Bảng kỳ vọng mục 4 đã tính tay từ luật hiện tại; G1 sẽ kiểm bằng test.
- Không code, không build, không dùng ADB. Không có tiến trình nào còn chạy.
- Chặng: **A** = G0–G2 (test khóa + khảo sát Sony, không sửa UI); **B** = G3 (sửa + gate Ni); **C** = G4 (Tiny build + chụp lại + hồ sơ).
- **Chờ chủ dự án:** câu 1 mục 9 (cho phép đổi tạm `wm size` / `wm density` / `font_scale` trên Sony). Không trả lời thì G2 làm theo GD3 (chỉ cỡ gốc). Câu 2–4 có mặc định.
- Không merge/push. Trạng thái lúc lập kế hoạch: **KẾ HOẠCH — chờ chủ dự án giao thực thi.**

## Checkpoint chặng A — 31/10/2026 20:35, Claude Code Sonnet 5.5

- **Chặng A (G0–G2) xong.** Báo cáo: [`-bao-cao.md`](2026-10-31-giao-dien-da-thiet-bi-bao-cao.md). Khảo sát: [`-khao-sat.md`](2026-10-31-giao-dien-da-thiet-bi-khao-sat.md).
- G0: APK trên Sony khớp SHA-256 `6e3d6b6e…a029` (Debug thường `a2f0a88`).
- G1: `MeloLayoutMatrixTest` (7 ca), commit `d9ab585`. Debug 2317/21/0/0 (toàn bộ), Release 7/0/0/0 (chỉ ca mới). Không dòng nào lệch bảng mục 4; không sửa `MeloScreenPolicy`.
- G2: khảo sát **theo GD3** vì bàn giao **chưa ghi** chủ dự án cho phép `wm size` / `wm density` / `font_scale`. Đã chụp M3 dọc, M3 ngang, M9 chia đôi. **Chưa kiểm:** M1, M2, M4–M8, F1–F3.
- Lỗi tìm được (chi tiết ở file khảo sát):

| Mã | Tóm tắt | Nhóm | Mức |
|---|---|---|---|
| UI-01 | M3 ngang: phần đầu Thư viện chiếm ≈ 80% chiều cao, list ≈ 1,3 dòng | (e) | Xấu rõ |
| UI-02 | M3 dọc: ô tìm mở bàn phím, list ≈ 2,5 dòng | (e) | Xấu rõ |
| UI-03 | M3 dọc: Hàng đợi toàn màn không có lề ngang | (d) gần đúng | Xấu rõ |
| UI-04 | M3 ngang: cột Đang phát bên trái sát mép | (d) gần đúng | Nhỏ |
| UI-05 | M3 dọc: dòng “Tạm dừng” đè mép trên list / dính chip | (b) | Nhỏ |
| UI-06 | M3 ngang: bàn phím toàn màn (extract UI) che app | Đề xuất | Nhỏ |

- **Chờ chủ dự án** (xem khảo sát mục 3):
  - cho phép đổi cỡ màn / chữ để khảo sát thêm, hoặc chấp nhận “chưa kiểm” ở chặng C;
  - gạch lỗi không muốn sửa.

  Không trả lời → chặng B sửa UI-01…UI-05 (UI-06 là Đề xuất, không sửa).
- Thiết bị đã khôi phục. `wm size` / `wm density` / `font_scale` không bị đổi; xoay về `user_rotation=0`, `accelerometer_rotation=1`; thoát chia đôi. Nhạc: một lần chạm vô tình **tiếp tục phát ≈ 15–30 giây** bài #140 đang dừng, đã tạm dừng lại (00:46). `SONY_SCREEN_END=OFF` lúc 20:34:54.
- Ảnh private ở `docs/plans/artifacts/2026-10-31-giao-dien-da-thiet-bi/G2-20261031-203502/` (không commit).
- Không merge/push/public, không ghi PASS. Không có APK mới.
- Trạng thái: **CHẶNG A XONG — chờ chủ dự án xem bảng lỗi, giao chặng B.**
- HEAD chặng B: commit cuối của nhánh (chứa file này); số thật nằm trong tin nhắn bàn giao.

## Prompt phiên thực thi — chặng A (dán nguyên văn)

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-giao-dien-da-thiet-bi (repo chuẩn D:\Bots\MeloVault-E91-Shareable; KHÔNG làm trong thư mục chính)
TASK_ID: 2026-10-31-giao-dien-da-thiet-bi
Nhánh: dot/2026-10-31-giao-dien-da-thiet-bi
Kế hoạch đã chốt: docs/plans/2026-10-31-giao-dien-da-thiet-bi.md
Bàn giao: docs/plans/2026-10-31-giao-dien-da-thiet-bi-ban-giao.md; HEAD dự kiến: commit cuối của nhánh (chứa file bàn giao)
Baseline: 94ab6b0 (app/ giống a2f0a88)
PHIÊN: 1 = chặng A, bước G0–G2 (test khóa + khảo sát Sony; KHÔNG sửa mã giao diện)
Lưu ý riêng:
- G1 chỉ thêm MeloLayoutMatrixTest theo bảng mục 4; không sửa MeloScreenPolicy. Dòng nào lệch bảng thì sửa bảng (ghi lý do), không sửa mã.
- G2 dùng Sony QV725WEQ54 qua Tiny (Desktop Commander). wm size/wm density/font_scale CHỈ khi bàn giao ghi chủ dự án đã cho phép (câu 1 mục 9); chưa có thì làm GD3.
- Ghi giá trị gốc trước; mỗi cấu hình khôi phục ngay; finally: wm size reset, wm density reset, font_scale/xoay về gốc, keyevent 223, kiểm Display State=OFF. Ghi SONY_SCREEN_END.
- Không phát bài mới/đổi hàng chờ, không đăng xuất, không máy bay, không uninstall/clear. Đổi cỡ lúc app đang mở; app không tự dựng lại thì ghi lỗi UI-xx.
- Kết quả: docs/plans/2026-10-31-giao-dien-da-thiet-bi-khao-sat.md (bảng UI-xx: cấu hình, màn, mô tả, nhóm GD4 a–f hoặc Đề xuất, mức, ảnh) + ảnh tổng hợp mỗi nhóm cấu hình. Ảnh private, không commit.

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, docs/workflow/SCREEN_LIFECYCLE_QA.md, file bàn giao và kế hoạch trên. Kiểm git status, branch, HEAD, baseline, diff --check, agent/thiết bị đang dùng. Cây bẩn không rõ nguồn hoặc HEAD lạ → STOP, không reset/clean/stash/đè.
2. G0: đối chiếu APK trên Sony với SHA-256 6e3d6b6e98de9974d399eea850ec691df1ab2324172ec3990fba483e6869a029 (Debug thường a2f0a88). G1: test mục tiêu Debug + Release, log dưới docs/plans/artifacts/2026-10-31-giao-dien-da-thiet-bi/G1-<ts>/, chỉ ghi tổng kết + cộng XML; commit. G2: khảo sát theo mục 4, ghi file khảo sát. Sau mỗi mốc cập nhật docs/plans/2026-10-31-giao-dien-da-thiet-bi-bao-cao.md (mở đầu "Thực thi: <Claude Code/Codex> <model>, phiên chặng A") và file bàn giao.
3. Commit test, báo cáo, khảo sát, bàn giao trên dot/2026-10-31-giao-dien-da-thiet-bi. Không merge/push/public, không ghi PASS, không chọn việc khác.

Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Gửi ảnh tổng hợp khảo sát cho chủ dự án. Dòng cuối: "Đã dừng, bàn giao phiên thực thi tiếp theo." kèm prompt chặng B đã điền HEAD thật.
```

## Prompt phiên thực thi — chặng B (dán nguyên văn)

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-giao-dien-da-thiet-bi (repo chuẩn D:\Bots\MeloVault-E91-Shareable; KHÔNG làm trong thư mục chính)
TASK_ID: 2026-10-31-giao-dien-da-thiet-bi
Nhánh: dot/2026-10-31-giao-dien-da-thiet-bi
Kế hoạch đã chốt: docs/plans/2026-10-31-giao-dien-da-thiet-bi.md
Bàn giao: docs/plans/2026-10-31-giao-dien-da-thiet-bi-ban-giao.md; HEAD dự kiến: commit cuối của nhánh (chứa file bàn giao; số thật do tin nhắn bàn giao chặng A ghi)
Baseline: 94ab6b0 (app/ giống a2f0a88). Chặng A đã có commit test d9ab585.
PHIÊN: 2 = chặng B, bước G3 (sửa lỗi UI-xx thuộc GD4 + gate Ni; KHÔNG build Tiny, KHÔNG cài APK)
Lưu ý riêng:
- Đọc docs/plans/2026-10-31-giao-dien-da-thiet-bi-khao-sat.md (bảng UI-01…UI-08, đã gồm khảo sát bổ sung). Sửa UI-01…UI-05, UI-07, UI-08 theo GD4 trừ lỗi chủ dự án đã gạch trong bàn giao (mục "Chờ chủ dự án"). UI-06 là Đề xuất: KHÔNG sửa.
- Ưu tiên: UI-01 và UI-02 (Chặn dùng ở cửa sổ thấp / có bàn phím), rồi UI-03, UI-07; UI-04, UI-05, UI-08 là Nhỏ. Điều kiện đo được bằng hàm thuần (ví dụ chiều cao còn lại cho danh sách) thì thêm test JVM.
- Mỗi lỗi một commit: `fix(ui-da-thiet-bi): UI-xx …`. Điều kiện tính được bằng hàm thuần thì thêm test JVM (GD6).
- KHÔNG sửa MeloScreenPolicy.chooseLayout/profileFor hay hằng ngưỡng; MeloLayoutMatrixTest phải vẫn xanh.
- Không đụng service, DB, TDLib, package, signer, versionCode. Không bố cục mới, không đổi màu/nhận diện.
- Gate Ni (một lần, log ra file, chỉ đọc dòng tổng kết + cộng XML): test Debug + Release; assembleShareableDebug + Release; -PmeloOpen=true assembleShareableDebug; assemblePersonalDebug; git diff --check.
- Không dùng Sony trong chặng này (không có APK mới). Chủ dự án đã cho phép đổi tạm wm size/density/font_scale (xem bàn giao); chặng C dùng lại đúng cấu hình ghi ở khảo sát mục 1 (Sony không nhận wm size rộng hơn ≈ 1096 px: hạ density thay vì tăng size).

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, file bàn giao, kế hoạch, báo cáo, khảo sát. Kiểm git status, branch, HEAD, diff --check, agent đang chạy. Cây bẩn không rõ nguồn hoặc HEAD lạ → STOP.
2. G3 theo mục 5 kế hoạch. Sau mỗi lỗi cập nhật docs/plans/2026-10-31-giao-dien-da-thiet-bi-bao-cao.md (mở đầu "Thực thi: <Claude Code/Codex> <model>, phiên chặng B") và file bàn giao.
3. Commit trên dot/2026-10-31-giao-dien-da-thiet-bi. Không merge/push/public, không ghi PASS, không chọn việc khác.

Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng, bàn giao phiên thực thi tiếp theo." kèm prompt chặng C đã điền HEAD thật.
```

## Quyết định chủ dự án — 31/10/2026 (chat, sau chặng A)

- **Câu 1 mục 9 kế hoạch: ĐƯỢC PHÉP.** Chủ dự án viết trong chat: “Cho phép đổi tạm wm size/density/font_scale trên Sony, khôi phục ngay sau mỗi cấu hình. Làm phiên khảo sát bổ sung M1, M2, M4–M8, F1–F3.”
- Phạm vi: chỉ Sony `QV725WEQ54`. Đổi tạm `wm size`, `wm density`, `font_scale`; **khôi phục ngay sau mỗi cấu hình**. Các luật cấm khác giữ nguyên (không phát bài mới, không đổi hàng đợi, không đăng xuất, không máy bay, không uninstall/clear).
- Lệnh khôi phục (ghi trước khi bắt đầu): `wm size reset`; `wm density reset`; `settings put system font_scale 1.0`; `settings put system user_rotation 0`; `settings put system accelerometer_rotation 1`; `input keyevent 223`. Giá trị gốc: size Physical 1080x2520 (không Override), density Physical 420, font_scale 1.0, user_rotation 0, accelerometer_rotation 1.

## Checkpoint sau khảo sát bổ sung — 31/10/2026 22:10, Claude Code Sonnet 5.5

- **Khảo sát bổ sung xong** (chủ dự án đã cho phép, xem mục “Quyết định chủ dự án” ở trên). Bảng lỗi đã hợp nhất: [`-khao-sat.md`](2026-10-31-giao-dien-da-thiet-bi-khao-sat.md), báo cáo [`-bao-cao.md`](2026-10-31-giao-dien-da-thiet-bi-bao-cao.md). **Bảng ở checkpoint chặng A phía trên đã cũ, lấy bảng trong file khảo sát.**
- Lỗi hiện có:

| Mã | Tóm tắt | Nhóm | Mức |
|---|---|---|---|
| UI-01 | Cửa sổ thấp: phần đầu Thư viện chiếm gần hết, danh sách 0–2 dòng (M2 ngang ≈ 0,5; F3 M3 ngang 0) | (e) | Chặn dùng / Xấu rõ |
| UI-02 | Ô tìm mở bàn phím: kết quả 0–2,5 dòng (M1, F3 M3, F3 M6: 0) | (e) | Xấu rõ / Chặn dùng |
| UI-03 | Hàng đợi toàn màn dọc không có lề ngang | (d) gần đúng | Xấu rõ |
| UI-04 | Mọi cỡ Hai khung: cột Đang phát bên trái sát mép | (d) gần đúng | Nhỏ |
| UI-05 | Dòng “Tạm dừng” lẻ đè mép trên danh sách / dính chip | (b) | Nhỏ |
| UI-06 | Cửa sổ ngang thấp: bàn phím toàn màn che app | Đề xuất | Nhỏ |
| UI-07 | Chữ ≥ 1,5 trên M3 dọc: chip lọc / ô tìm / nút / menu bị cắt | (a) | Xấu rõ |
| UI-08 | Chữ 2,0 trên 600 dp: nhãn tab dưới cắt (“Thư”, “Telegra”) | (a) | Nhỏ |

- **Chưa kiểm / thiếu:** M8 chỉ có ảnh Thư viện; F3 M6 ngang thiếu menu ⋮ và Cài đặt; hộp thoại mời Car Mode không quan sát được; Gọn chỉ thấy qua chia đôi M9 (wm size không tái hiện Gọn).
- **Chờ chủ dự án:** xem bảng lỗi, gạch lỗi không muốn sửa (mặc định chặng B sửa UI-01…UI-05, UI-07, UI-08; UI-06 không sửa). Không trả lời → làm mặc định.
- Thiết bị đã khôi phục (không Override, font 1.0, xoay 0/1); `SONY_SCREEN_END=OFF` 22:09:10. Hai lần `finally` bị ADB cắt, đã khôi phục thủ công ngay (chi tiết khảo sát mục 5). Vị trí phát bài #140 bị kéo từ 00:46 lên 02:48 (luôn tạm dừng).
- Prompt chặng B ở mục trên đã cập nhật (UI-07, UI-08, quyền Sony). HEAD chặng B: commit cuối nhánh (xem tin nhắn bàn giao).
- Trạng thái: **CHẶNG A XONG (gồm khảo sát bổ sung) — chờ chủ dự án xem bảng lỗi, giao chặng B.** Không merge/push/public, không ghi PASS.

## Checkpoint chặng B — 31/10/2026, Claude Code Sonnet 5.5

- **Chặng B (G3) xong.** Chi tiết, bảng commit và gate: [`-bao-cao.md`](2026-10-31-giao-dien-da-thiet-bi-bao-cao.md) mục "Chặng B".
- Chủ dự án đã quyết (chat 31/10): sửa UI-01…UI-05, UI-07, UI-08 theo GD4; **UI-06 không sửa**.
- Commit mã (cũ → mới): UI-01 `fd9720f`, UI-02 `577e094`, UI-03 `6b3a507`, UI-07 `5b971a6`, UI-04 `e685813`, UI-05 `b1ca641`, UI-08 `75375b4` (commit mã cuối). Sau đó chỉ có commit hồ sơ chặng B.
- Gate Ni: test Debug/Release **2336 tests / 21 skipped / 0 failures / 0 errors** mỗi biến thể (2317 + 19 ca mới `MeloUiDaThietBiTest`); `MeloLayoutMatrixTest` 7/7 xanh; assemble thường Debug+Release, `-PmeloOpen=true` Debug, Personal Debug đều `BUILD SUCCESSFUL`; `git diff --check` sạch. Log: `docs/plans/artifacts/2026-10-31-giao-dien-da-thiet-bi/G3-20261031-223609/` (private).
- **Chưa làm:** build Tiny, cài APK, chụp lại trên Sony, hồ sơ APK/ảnh/Drive (đều là chặng C). Không dùng Sony, không đổi `wm size`/`density`/`font_scale` ở chặng B (Sony vẫn ở trạng thái đã khôi phục cuối chặng A).
- Điểm cần xem ảnh ở chặng C: M1 dọc và M2 ngang (UI-01 còn bao nhiêu dòng), F3 M3 dọc/ngang (UI-01, 02, 07), F3 M6 dọc (UI-08), M3 dọc Hàng đợi (UI-03), mọi cỡ Hai khung (UI-04), Thư viện/Nguồn/Đang phát khi có "Tạm dừng" (UI-05), hồi quy M3 dọc/ngang và M9 chia đôi (Gọn).
- Không merge/push/public, không ghi PASS. Trạng thái: **CHẶNG B XONG — chờ giao chặng C.**

## Prompt phiên thực thi — chặng C (dán nguyên văn)

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-giao-dien-da-thiet-bi (repo chuẩn D:\Bots\MeloVault-E91-Shareable; KHÔNG làm trong thư mục chính)
TASK_ID: 2026-10-31-giao-dien-da-thiet-bi
Nhánh: dot/2026-10-31-giao-dien-da-thiet-bi
Kế hoạch đã chốt: docs/plans/2026-10-31-giao-dien-da-thiet-bi.md (mục 5, G4)
Bàn giao: docs/plans/2026-10-31-giao-dien-da-thiet-bi-ban-giao.md; HEAD dự kiến: <HEAD_THAT>
Baseline: 94ab6b0. Commit mã cuối chặng B: 75375b4 (UI-01 fd9720f, UI-02 577e094, UI-03 6b3a507, UI-07 5b971a6, UI-04 e685813, UI-05 b1ca641, UI-08 75375b4).
PHIÊN: 3 = chặng C, bước G4 (Tiny build + QA Sony chụp lại + hồ sơ APK/ảnh)
Lưu ý riêng:
- Không sửa mã app. Lỗi mới phát hiện hoặc lỗi chưa hết thì ghi vào báo cáo cho chủ dự án (REWORK do chủ dự án giao), không tự sửa.
- Bundle SHA-256 → clone cô lập trên Tiny → local.properties theo ngoại lệ E104 S11 → reportTelegramConfig=true → build Debug thường; gate package app.melovault.player / versionCode 121 / cert eecd3df7…fce8; đúng commit HEAD. KHÔNG cài bản .open, KHÔNG downgrade, không adb uninstall/pm clear.
- Sony QV725WEQ54: chủ dự án đã cho phép đổi tạm wm size/density/font_scale và xoay (xem bàn giao). Dùng lại ĐÚNG cấu hình ở khảo sát mục 1 (Sony không nhận wm size rộng hơn ≈ 1096 px: giữ rộng ≤ 1080 và hạ density: M4 1080x2340/400, M5 1080x1298/250, M6 1080x1728/288, M7 1080x1728/216; M1 720x1280/320, M2 1080x2340/480; M8 608x1080/90 xoay ngang). Ghi giá trị gốc trước (size 1080x2520 không Override, density 420, font_scale 1.0, user_rotation 0, accelerometer_rotation 1); MỖI cấu hình khôi phục ngay trong finally; cuối phiên kiểm lại và keyevent 223, Display State=OFF; ghi SONY_SCREEN_END. Kiểm lại sau mỗi lần ADB chớp rớt.
- Chụp lại ĐÚNG cấu hình và màn của từng lỗi đã sửa (trước = ảnh G2/G2B, sau = ảnh mới) + hồi quy M3 dọc/ngang và M9 chia đôi (Gọn). Ưu tiên: UI-01 (M2 ngang, M3 ngang, F3 M3 ngang, F3 M6 ngang, M1 dọc, M5 dọc), UI-02 (M3, M1, M5, F3 M3, F3 M6 có bàn phím), UI-03 (M3 dọc Hàng đợi), UI-04 (Hai khung M2/M3/M6/M7/M8), UI-05 (Thư viện, Nguồn, Đang phát khi tạm dừng), UI-07 (F2, F3 M3 dọc: tab, ô tìm, "Phát ngẫu nhiên", menu ⋮), UI-08 (F3 M6 dọc). UI-06 không sửa: chỉ ghi lại nó còn nguyên.
- Không phát bài mới, không đổi hàng đợi; vuốt chỉ trong vùng cuộn (tránh thanh tua, vị trí bài #140 đã bị kéo ở chặng A). Không đăng xuất/máy bay/uninstall/clear.
- Ảnh private ở docs/plans/artifacts/2026-10-31-giao-dien-da-thiet-bi/ (không commit), mỗi nhóm một ảnh tổng hợp ≤ 1600 px và 00_tong_hop.png; bảng trước/sau trong báo cáo. Hồ sơ: -bao-cao.md, -apk-test.md, -hinh-nghiem-thu.md. Drive Ni N owner-only (CLAUDE.md mục 8–9), ghi QA CANDIDATE — CHƯA NGHIỆM THU; không public.
- Cuối phiên: prompt nghiệm thu sơ bộ cho phiên mới.

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, docs/workflow/SCREEN_LIFECYCLE_QA.md, file bàn giao, kế hoạch, báo cáo, khảo sát. Kiểm git status, branch, HEAD, diff --check, agent/thiết bị đang dùng. Cây bẩn không rõ nguồn hoặc HEAD lạ → STOP.
2. G4 theo mục 5 kế hoạch. Sau mỗi bước cập nhật báo cáo (mở đầu "Thực thi: <Claude Code/Codex> <model>, phiên chặng C") và file bàn giao.
3. Commit hồ sơ trên dot/2026-10-31-giao-dien-da-thiet-bi. Không merge/push/public, không ghi PASS, không chọn việc khác.

Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Gửi ảnh tổng hợp và link Drive đã kiểm. Dòng cuối: "Đã dừng. Mở phiên mới để nghiệm thu sơ bộ nhanh." kèm prompt sơ bộ.
```
