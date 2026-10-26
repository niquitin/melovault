# E104-APK-CONG-KHAI — Biên bản nghiệm thu (Claude Opus)

## Vòng 1 — 27/10/2026 ~01:00 (+07)

**Trạng thái: BLOCKED — CHƯA CÓ PHIÊN 2 (build Tiny + QA). Không phải REWORK.**

### 1. Git và phạm vi

| Mục | Kết quả |
|---|---|
| Nhánh / HEAD lúc nghiệm thu | `dot/E104-APK-CONG-KHAI` @ `5808eb6` (cây sạch, `git diff --check` sạch) |
| Baseline / plan gốc | `a638099` / `1d278c3` |
| Commit mã nguồn cuối | `03e9dc9` (phiên 1 Codex) |
| Báo cáo | `a10e816` (phiên 1), `54c0bc0` (phiên 2 BLOCKED vì thiếu cách cấp config cho clone Tiny mới) |
| Plan bổ sung | `5808eb6` — chủ dự án chốt 26/10: chỉ trên Tiny, `Copy-Item C:\Bots\MeloVault-E99-IsolatedQA\local.properties` vào clone E104 mới, không đọc/in/hash nội dung |
| File thay đổi so baseline | đúng 6 file mục 6 + plan + báo cáo; không chạm DB/schema, songKey, Media3/queue, `ShareableTdlibOwner`, personal flavor, CLAUDE.md/AGENTS.md/docs/workflow |

Phiên Codex sau đó (ảnh chủ dự án gửi 27/10 00:53) dừng ở kiểm tra đầu phiên vì prompt ghi baseline `a10e816` mà HEAD đã đi tiếp. Đây là **prompt cũ**, không phải có agent lạ: các commit sau `a10e816` là báo cáo BLOCKED của Codex (`54c0bc0`) và plan bổ sung của Opus (`5808eb6`). Codex dừng là đúng luật. Phiên 2 cần prompt mới với HEAD `5808eb6` (mục 6).

### 2. Kiểm toán mã phiên 1 (đọc file rủi ro)

- `app/build.gradle`: cờ `meloOpen` đúng 6.1 (package `.open`, vc120, `-open`, ID `0`/hash `""` bất kể local.properties, `MELO_OPEN_BUILD`). Chặn `meloOpen + meloSyncQa`. `verifyOpenApkHasNoTelegramHash` đọc giải nén từng entry, đệm 31 ký tự giữa các khối, không in hash/ID/tên entry, lỗi không đính kèm exception. `reportTelegramConfig` trả `false` khi `meloOpen=true` (hợp lý; S11 phải chạy task này ở bản **thường**).
- `TelegramApiCredentialPolicy`: `scrub` lọc chuỗi rỗng/blank (đã có test), `Credentials.toString()` không lộ giá trị, `resolve` ưu tiên mã nhúng hợp lệ.
- `TelegramApiCredentialStore`: AES-256-GCM AndroidKeyStore, file riêng `e104_tg_api_v1.enc` trong `noBackupFilesDir`, `.part` → rename, `clear()` chỉ xóa 2 file của nó; `read()` lỗi → null.
- `TelegramPreviewActivity`: bản riêng có mã nhúng → `initNative()` ngay, **không đọc store** (đúng 6.5-2). `setTdlibParameters` dùng `apiCredentials`; null → không gửi. Nhập lại mã dùng **phương án dự phòng** "đóng hẳn rồi mở lại" (plan cho phép, báo cáo ghi rõ lý do); không `logOut`, không xóa TDLib. Nút "Nhập lại mã API" chỉ hiện khi `MELO_OPEN_BUILD` và chưa đăng nhập.
- Không thấy log giá trị API ID/hash trong diff.
- Lưu ý cho QA (không phải lỗi): cờ `apiRestartRequired` ở mức process; nếu process còn sống (ví dụ service), người dùng mở lại vẫn thấy câu dự phòng cho tới khi process chết. Q4 dùng `am force-stop` nên vẫn chứng minh được; ghi lại để chủ dự án biết khi thử tay.

### 3. Test/build Opus chạy lại trên Ni (`--rerun-tasks`)

Lệnh: `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest :app:assembleShareableDebug :app:assembleShareableRelease --rerun-tasks` trên `5808eb6` (cùng mã `03e9dc9`), 27/10 00:56–01:02 → **BUILD SUCCESSFUL in 5m 27s**. Log ở scratchpad phiên Opus, không commit.

| Biến thể | Tests | Skipped | Failures | Errors | `TelegramApiCredentialPolicyTest` |
|---|---:|---:|---:|---:|---:|
| Debug | 2.128 | 21 | 0 | 0 | 34 |
| Release | 2.128 | 21 | 0 | 0 | 34 |

Khớp báo cáo phiên 1 (baseline 2.094/21 → +34, skipped không tăng). APK thường Debug Ni dựng lại có SHA-256 `fbc0c3ee…b2b7`, trùng đúng báo cáo phiên 1. Release ra `fbbade3d…11e8` (phiên 1 `dd8ed57e…1fe0`, cùng 30.703.982 byte) — bản Release dựng lại không trùng byte là bình thường với R8/ký; không ảnh hưởng vì APK Ni không bàn giao.

### 4. Chấm theo tiêu chí mục 10 của kế hoạch

| Tiêu chí | Kết quả | Ghi chú |
|---|---|---|
| P1 JVM test ≥ 15 ca mới, toàn bộ pass, skipped không tăng | ĐẠT | 34 test mới; 2.128/21/0/0 mỗi biến thể (Opus chạy lại) |
| P2 4 APK, package/vc/vname đúng, chặn `meloOpen+meloSyncQa` | ĐẠT phần Ni | Theo báo cáo phiên 1 (`aapt2`, log 09); APK Tiny chưa build |
| P3 Tiny có hash thật: open Debug+Release `found=false`, APK thường `found=true` | **CHƯA CÓ** | Ni chỉ `SKIPPED_NO_LOCAL_HASH` |
| P4 Q1–Q6 có ảnh, Q5 bản riêng không đổi | **CHƯA CÓ** | Chưa cài/QA Shanling |
| P5 diff không chạm vùng cấm | ĐẠT | Mục 1–2 |
| P6 không lộ ID/hash; `scrub` bỏ chuỗi rỗng có test | ĐẠT phần mã | Log thiết bị (Q6) chưa có |
| P7 chủ dự án làm mục 9 và nói ĐẠT | **CHƯA CÓ** | |

Kết luận: phần mã phiên 1 không có lỗi buộc REWORK. Thiếu toàn bộ chứng cứ P3/P4/P7 → **BLOCKED**.

### 5. APK

Không bàn giao APK nào. APK Ni phiên 1 thiếu Telegram config và ký signer Ni (`a9b8cb6b…db280`), khác signer Tiny của bản đang cài → **không cài, không upload**. Không tạo `E104-APK-CONG-KHAI-apk-test.md` ở vòng này. Shanling/Sony không bị đánh thức trong phiên nghiệm thu.

Đã kiểm trên Tiny (chỉ `Test-Path`, không mở tệp): nguồn config `C:\Bots\MeloVault-E99-IsolatedQA\local.properties` **tồn tại**, nên lối đi plan `5808eb6` dùng được.

### 6. Việc tiếp theo

Mở **phiên Codex mới `gpt-6-astra` medium**, PHIÊN 2 (S10–S16), baseline HEAD `5808eb6`. Sau khi Codex báo xong, mở phiên Opus nghiệm thu mới.
