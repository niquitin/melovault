# Bàn giao — bản công khai đăng nhập chỉ bằng số điện thoại

TASK_ID: **2026-10-30-dang-nhap-chi-so-dien-thoai**.
Worktree `D:\Bots\_worktrees\MeloVault-dang-nhap-chi-so-dien-thoai`; nhánh `dot/2026-10-30-dang-nhap-chi-so-dien-thoai`.

## Checkpoint chặng B — 31/10/2026, Claude Code claude-sonnet-5 — S5+S6 XONG (PASS), chờ S7

- **S5 xong** (ngoại lệ một lần, chủ dự án tự yêu cầu qua chat — chi tiết/lý do ở [`-bao-cao.md`](2026-10-30-dang-nhap-chi-so-dien-thoai-bao-cao.md) mục "Tái kiểm chặng B — 31/10/2026"). Chủ dự án đăng ký app Telegram thứ hai, riêng biệt, gửi `api_id`/`api_hash` qua chat; agent ghi trực tiếp vào `local.properties` trên Tiny, không đọc lại/in giá trị.
- **S6 xong — PASS.** Trên clone cô lập `C:\Bots\MeloVault-dang-nhap-IsolatedQA-20261030-a2f0a88` (HEAD vẫn `a2f0a8837917380475b375e24d99187ec119fde3`, không đổi): build bản thường Debug + bản open Debug/Release đều `BUILD SUCCESSFUL`; `verifyOpenApkHasNoTelegramHash`/`verifyOpenApkHasOpenTelegramHash` đúng kỳ vọng cho cả hai APK open, đối chứng APK thường `found=true`; `aapt2` package/vc121/versionName đúng (`0.6.96-beta` / `0.6.96-open`); `apksigner` cert cả 3 APK đều `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (khớp chữ ký debug Tiny đã biết). Chi tiết SHA-256/bytes từng APK ở [`-bao-cao.md`](2026-10-30-dang-nhap-chi-so-dien-thoai-bao-cao.md) mục "S6.7 — Verify APK (Tiny)".
  - Sự cố phụ: lần build đầu `BUILD FAILED` do BOM UTF-8 lẫn vào `local.properties` (do script sửa cặp open ở bước trước) làm mất khoá `sdk.dir`; đã sửa (ghi lại không BOM), build lại thành công. Không ảnh hưởng tới giá trị `telegram.open.*`.
- Ba APK candidate (**QA CANDIDATE — CHƯA NGHIỆM THU**, chưa cài máy, chưa upload Drive) tại Tiny: `C:\Bots\MeloVault-dang-nhap-IsolatedQA-20261030-a2f0a88\_qa_artifacts\normal\app-shareable-debug.apk`, `...\open\app-shareable-debug.apk`, `...\open\app-shareable-release.apk`.
- **Chưa làm: S7** (QA thiết bị Shanling/Sony theo mục 5 kế hoạch, hồ sơ `-apk-test.md`/`-hinh-nghiem-thu.md`/`00_tong_hop.png`, upload Drive owner-only). Đây là bước đụng thiết bị thật — đang hỏi lại chủ dự án trước khi làm, theo đúng giới hạn ADB/an toàn của CLAUDE.md.
- Không merge/push, không ghi PASS chính thức.

## Checkpoint chặng B — 30/10/2026, Claude Code claude-sonnet-5 — BLOCKED tại S5/S6 (lịch sử, đã qua)

- **Tái kiểm 23:07 (+07) cùng ngày:** chạy lại đúng 2 lệnh `reportTelegramConfig` sau khi copy lại `local.properties` — **kết quả không đổi**, `telegram.open.config.valid=false`. Vẫn BLOCKED. Chi tiết: [`-bao-cao.md`](2026-10-30-dang-nhap-chi-so-dien-thoai-bao-cao.md) mục "Tái kiểm".
- **Trạng thái: BLOCKED.** S5 (cổng chủ dự án) chưa xong theo kiểm tra kỹ thuật thực tế: trên Tiny, trong clone cô lập mới, `-PmeloOpen=true :app:reportTelegramConfig` → `telegram.open.config.valid=false` (cặp `telegram.open.api.id`/`telegram.open.api.hash` trong `C:\Bots\MeloVault-E99-IsolatedQA\local.properties` chưa có hoặc chưa đúng định dạng — **không đọc giá trị để biết chắc**, đúng luật cấm). Không build APK nào. Không có APK mới để bàn giao.
- HEAD không đổi so với cuối chặng A: `a2f0a88` (Ni). Không có commit mới trên nhánh ở phiên này (chỉ thao tác trên Tiny, không sửa code).
- Bundle + clone cô lập đã dựng xong trên Tiny, SHA-256 đối chứng hai đầu khớp — **giữ lại để tái dùng** khi S5 xong (xem chi tiết SHA/đường dẫn ở [`-bao-cao.md`](2026-10-30-dang-nhap-chi-so-dien-thoai-bao-cao.md) mục "Phiên chặng B"):
  - Bundle: `T:\_MeloVault_Git_Transfer\MeloVault_dang-nhap-chi-so-dien-thoai_full_a2f0a88_20261030.bundle` (= `C:\Bots\_MeloVault_Git_Transfer\...` trên Tiny), SHA-256 `84c6d6d97a969596bdcf7f2c35e4404a3f0b523b7c86b116d3a569f26404e4c6`, 13.308.010 bytes.
  - Clone: `C:\Bots\MeloVault-dang-nhap-IsolatedQA-20261030-a2f0a88` trên Tiny, nhánh/HEAD khớp `a2f0a88`, `local.properties` đã copy một lần từ nguồn E99 (sẽ cần copy lại nếu chủ dự án vừa sửa file sau checkpoint này).
- **Phiên tiếp theo:** nếu Ni không có commit mới, DÙNG LẠI clone trên — chỉ cần copy lại `local.properties` rồi chạy lại `reportTelegramConfig` hai biến thể để xác nhận trước khi build. Nếu Ni đã có commit mới hơn `a2f0a88`, dựng bundle/clone MỚI theo đúng S6.1–S6.2 (không tái dùng clone cũ với source lệch HEAD).
- Không merge/push, không ghi PASS, không chọn việc khác. Không ADB/thiết bị (S7 phụ thuộc APK open từ S6, chưa build được).

### Prompt tiếp tục chặng B (dán nguyên văn vào phiên mới, sau khi chủ dự án xác nhận xong S5)

Xem khối `text` ở cuối mục "Phiên chặng B" trong [`-bao-cao.md`](2026-10-30-dang-nhap-chi-so-dien-thoai-bao-cao.md) — đã điền sẵn đường dẫn clone/bundle thật, không cần viết lại.

## Checkpoint thực thi chặng A — 30/10/2026, Claude Code claude-sonnet-5

- **Trạng thái: CHẶNG A XONG (S0–S4). Chờ chủ dự án mở phiên chặng B** (S5 cần chủ dự án tự đăng ký app + điền `local.properties` trên Tiny trước).
- HEAD nhận việc: `5ef716f` (khớp dự kiến). HEAD hiện tại sau chặng A: xem `git log -1 -- docs/plans/2026-10-30-dang-nhap-chi-so-dien-thoai-ban-giao.md` ở phiên sau để lấy đúng SHA (tránh tự tham chiếu); tại lúc ghi checkpoint này, 3 commit code là `ee709f1` (S1), `800117a` (S2), `152fe51` (S3).
- Chi tiết đầy đủ: [`-bao-cao.md`](2026-10-30-dang-nhap-chi-so-dien-thoai-bao-cao.md). Gate S4: `BUILD SUCCESSFUL` ở cả 4 lệnh Gradle (test Debug/Release 2310/21/0/0 mỗi biến thể; assemble thường, open, Personal); `git diff --check` sạch. Log đầy đủ (gitignored, chỉ trên Ni): `docs/plans/artifacts/2026-10-30-dang-nhap-chi-so-dien-thoai/S4-20261030T152509Z/`.
- Không ADB/thiết bị trong chặng A. `SHANLING_SCREEN_END` = KHÔNG THAO TÁC. `SONY_SCREEN_END` = KHÔNG THAO TÁC. Không đọc/in/copy `local.properties`. Không có APK nào được build cho mục đích cài máy (assemble chỉ để kiểm gate; APK Ni không cài theo đúng chỉ định).
- **Chưa làm/chưa kiểm chứng:** S5 (chủ dự án đăng ký my.telegram.org + điền `telegram.open.api.*` trên Tiny) — **BLOCKED chờ chủ dự án**; S6 (build Tiny + verify hash/cert/package); S7 (QA thiết bị Q1–Q6 + hồ sơ APK/ảnh). Chưa thử mã open thật bị Telegram từ chối. Chưa có người dùng E104 thật (mã riêng đã lưu) để kiểm D6 trên máy.
- **Không merge/push/public, không ghi PASS.** Mọi quyết định D1–D10 giữ như kế hoạch; D6 áp dụng mặc định (preferStored=true cho bản open) vì chủ dự án chưa trả lời khác.

### Prompt phiên thực thi — chặng B (dán nguyên văn vào phiên mới, sau khi chủ dự án xong S5)

```text
MELOVAULT — PHIÊN THỰC THI CHẶNG B (TIẾP TỤC CHẶNG A)

Repo/worktree: D:\Bots\_worktrees\MeloVault-dang-nhap-chi-so-dien-thoai (repo chuẩn D:\Bots\MeloVault-E91-Shareable)
TASK_ID: 2026-10-30-dang-nhap-chi-so-dien-thoai
Nhánh: dot/2026-10-30-dang-nhap-chi-so-dien-thoai
Kế hoạch: docs/plans/2026-10-30-dang-nhap-chi-so-dien-thoai.md (bước S5–S7, mục 5 QA, mục 6 tiêu chí)
Bàn giao: docs/plans/2026-10-30-dang-nhap-chi-so-dien-thoai-ban-giao.md
Báo cáo chặng A: docs/plans/2026-10-30-dang-nhap-chi-so-dien-thoai-bao-cao.md
HEAD chặng A hoàn tất: 152fe51 (commit S3; sau đó còn commit hồ sơ bao-cao/ban-giao — kiểm git log để lấy SHA mới nhất trước khi làm)
PHIÊN: 2 = chặng B, S5 (cổng chủ dự án) → S6 (build Tiny) → S7 (QA thiết bị + hồ sơ)

Lưu ý riêng:
- S5 là điều kiện tiên quyết: chủ dự án phải xác nhận đã đăng ký app mới trên my.telegram.org (tên không chứa "Telegram")
  và đã thêm hai dòng telegram.open.api.id= / telegram.open.api.hash= vào C:\Bots\MeloVault-E99-IsolatedQA\local.properties
  trên Tiny. KHÔNG tự tạo tài khoản, KHÔNG đọc/in/hash giá trị. Chưa xác nhận → ghi BLOCKED và dừng, không đoán.
- S6: bundle source đã commit (SHA-256 hai đầu khớp), clone cô lập MỚI trên Tiny, 1 lệnh Copy-Item local.properties
  (kiểm git check-ignore trước), chạy reportTelegramConfig thường + -PmeloOpen=true reportTelegramConfig để xác nhận
  telegram.open.config.valid=true và telegram.open.distinct=true trước khi build APK. Build cả bản thường Debug
  và bản open Debug+Release. Kiểm verifyOpenApkHasNoTelegramHash (found=false cho APK open) VÀ
  verifyOpenApkHasOpenTelegramHash (found=true cho APK open) cho cả hai APK open, đối chứng found=true trên APK
  thường ở verifyOpenApkHasNoTelegramHash. Kiểm aapt package/versionCode/versionName và apksigner cert khớp.
- S7: theo mục 5 kế hoạch (Q1–Q6). Agent KHÔNG tự gõ số điện thoại/mã/mật khẩu cấp 2 — chỉ chủ dự án nhập (Q3–Q4).
  Thiết bị đích bản open: Shanling đã có app.melovault.player.open vc120 → install -r hợp lệ (xác nhận lại
  serial 30313860180939 + getprop trước khi cài). Sony: kiểm pm list packages app.melovault.player.open trước;
  CHƯA có gói open thì KHÔNG tự cài mới — hỏi chủ dự án [CẦN XÁC NHẬN mục 8.3 kế hoạch]. Nếu không thiết bị nào
  hợp lệ → BLOCKED phần QA trên máy (vẫn làm được Q6 JVM).
  Màn hình: chỉ bật khi cần thao tác UI; cleanup trong finally, tắt và kiểm dumpsys display Display State=OFF;
  ghi SHANLING_SCREEN_END/SONY_SCREEN_END đúng thực tế.
  Ảnh/video riêng tư tại docs/plans/artifacts/2026-10-30-dang-nhap-chi-so-dien-thoai/; không commit/đăng PII.
- Hồ sơ cần tạo/bổ sung ở chặng B: -bao-cao.md (tiếp nối, ghi rõ "phiên chặng B"), -apk-test.md, -hinh-nghiem-thu.md,
  00_tong_hop.png (theo CLAUDE.md mục 10), và cập nhật -ban-giao.md sau mỗi mốc S5/S6/S7.
- APK QA candidate (nếu build được): upload Google Drive Ni N owner-only theo CLAUDE.md mục 8–9, nhãn
  "QA CANDIDATE — CHƯA NGHIỆM THU". Không tự public, không tự nâng versionCode, không đổi signing.
- Không merge/push. Không ghi PASS — hồ sơ nghiệm thu ghi CHỜ CHỦ DỰ ÁN NGHIỆM THU (hoặc BLOCKED đúng phần).
- Kết thúc chặng B: đưa prompt phiên nghiệm thu sơ bộ (theo PROMPT_NGHIEM_THU_SO_BO.md), không tự làm vai đó.

Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng. Mở phiên mới để nghiệm thu sơ bộ nhanh."
kèm prompt sơ bộ đã điền HEAD/APK thật — HOẶC nếu BLOCKED ở S5/S6: "Đã dừng, bàn giao phiên thực thi tiếp theo."
kèm đúng phần còn thiếu.
```

## Checkpoint lập kế hoạch — 30/10/2026, Claude Code

- Baseline `baaead77ac60cbd6c752bd0dbe9c575ed30954fc` (nhánh tích hợp sạch lúc 21:51). Kế hoạch [`2026-10-30-dang-nhap-chi-so-dien-thoai.md`](2026-10-30-dang-nhap-chi-so-dien-thoai.md), commit `f1e97f2d9eccf659cf784a40b1dd68e9d8a7cd0f`. HEAD dự kiến khi bắt đầu thực thi: commit chứa file bàn giao này (ngay sau `f1e97f2`).
- Chỉ đọc mã và tài liệu E104. Không code, không build, không dùng ADB/Sony/Tiny/Drive. Không có tiến trình nào còn chạy.
- Chặng: **A** = S0–S4 trên Ni (code + test); **B** = S5–S7 (cổng chủ dự án → Tiny build → QA → hồ sơ).
- **Chờ chủ dự án** (mục 8 kế hoạch):
  1. D6 — mã người dùng đã lưu thắng mã open nhúng. Mặc định: có.
  2. Đăng ký app trên my.telegram.org và thêm `telegram.open.api.id/hash` vào `C:\Bots\MeloVault-E99-IsolatedQA\local.properties` trên Tiny. Chặng A làm trước được; chặng B BLOCKED tới khi xong.
  3. Có được cài mới gói open lên Sony không.
  4. Chữ câu D7 / link D9.
- Không merge/push. Trạng thái: **KẾ HOẠCH — chờ chủ dự án giao thực thi.**

## Prompt phiên thực thi — chặng A (dán nguyên văn vào phiên mới)

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-dang-nhap-chi-so-dien-thoai (repo chuẩn D:\Bots\MeloVault-E91-Shareable; KHÔNG làm trong thư mục chính, nó đang ở nhánh khác)
TASK_ID: 2026-10-30-dang-nhap-chi-so-dien-thoai
Nhánh: dot/2026-10-30-dang-nhap-chi-so-dien-thoai
Kế hoạch đã chốt: docs/plans/2026-10-30-dang-nhap-chi-so-dien-thoai.md
Bàn giao: docs/plans/2026-10-30-dang-nhap-chi-so-dien-thoai-ban-giao.md; HEAD dự kiến: commit bàn giao ngay sau f1e97f2
Baseline: baaead77ac60cbd6c752bd0dbe9c575ed30954fc, commit kế hoạch f1e97f2d9eccf659cf784a40b1dd68e9d8a7cd0f
PHIÊN: 1 = chặng A, code + test Ni, bước S0–S4
Lưu ý riêng:
- Theo đúng quyết định D1–D10 mục 3. D6 áp dụng mặc định trừ khi chủ dự án đã trả lời khác trong bàn giao.
- Không đọc/in/copy local.properties; Ni không có mã nên APK Ni KHÔNG cài.
- S2: đối chiếu tên authenticationCodeType* với TDLib đóng gói trước khi viết; không đoán.
- S3: không được gửi setTdlibParameters lần hai; lưu mã sau parameters → yêu cầu khởi động lại (D8).
- Không đụng QR login, package/signer/versionCode, schema, mã bản riêng.
- Hết S4: dừng, cập nhật bàn giao, đưa prompt chặng B (S5–S7). Không tự làm S5 (cần chủ dự án).

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, file bàn giao và kế hoạch trên. Kiểm git status, branch, HEAD, baseline/plan commit, diff --check, agent đang chạy. Cây bẩn không rõ nguồn hoặc HEAD lạ → STOP, không reset/clean/stash/đè.
2. Làm S1 → S2 → S3 đúng file/hàm trong kế hoạch, commit theo mốc. Viết test mục S2 (≥ 12 ca mới). Chạy đúng các lệnh S4, log ra file dưới docs/plans/artifacts/2026-10-30-dang-nhap-chi-so-dien-thoai/S4-<ts>/, chỉ ghi tổng kết + cộng XML (passed/skipped/failed/errors), git diff --check. Sau mỗi mốc cập nhật docs/plans/2026-10-30-dang-nhap-chi-so-dien-thoai-bao-cao.md (mở đầu "Thực thi: Claude Code <model>, phiên chặng A") và file bàn giao: bước xong/dở, file/commit, bằng chứng, lỗi/giới hạn, việc tiếp.
3. Không ADB/thiết bị trong chặng A. Màn hình Shanling/Sony: không thao tác; ghi SHANLING_SCREEN_END/SONY_SCREEN_END = KHÔNG THAO TÁC.
4. Commit code, báo cáo và bàn giao trên dot/2026-10-30-dang-nhap-chi-so-dien-thoai. Không merge/push/public, không ghi PASS, không chọn việc khác.

Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng, bàn giao phiên thực thi tiếp theo." kèm prompt chặng B đã điền HEAD thật.
```
