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

## Vòng 2 — 27/10/2026 ~09:30 (+07)

**Trạng thái: REWORK — ô "API hash" hiện rõ ký tự (vi phạm 6.4). Mọi phần khác của phiên 2 đạt.**

### 7. Git

HEAD `5875e4c` (Codex phiên 2 lần 2, chỉ thêm 53 dòng báo cáo), cây sạch, `git diff --check` sạch, không có Gradle đang chạy. Mã nguồn vẫn `03e9dc9`; số liệu test Opus vòng 1 (2.128/21/0/0 mỗi biến thể) còn hiệu lực.

### 8. Opus tự kiểm bằng chứng

| Mục | Kết quả Opus kiểm |
|---|---|
| SHA-256 3 APK Tiny (bản trên Ni) | Khớp báo cáo: thường Debug `1092EE34…563A`, open Debug `406BCFB4…D535`, open Release `082C14FB…70DE` |
| Signer | Cả 3 `eecd3df7…3fce8` (log `apksigner` Tiny) |
| Config | `config.log` (bản thường, không cờ open): `telegram.config.valid=true`; `open-build.log`: `melo.open=true`, `valid=false` (đúng thiết kế) |
| P3 | Log hash: open Debug và Release `found=false`; thường Debug `found=true` → BUILD FAILED đối chứng. **ĐẠT** |
| Q6 | `Q6-summary.json`: 0 dòng hash giả, 0 `api_hash`, 0 FATAL, 0 ANR (7.543 dòng buffer) |
| Ảnh | Đã xem `00_tong_hop.png` (8 ô) |

### 9. Lỗi buộc REWORK

Ảnh ô 2 (`04-Q2-validation.png`) thấy rõ `12345` trong ô "API hash"; Codex ghi ảnh 05 thấy cả chuỗi mã giả. Kế hoạch 6.4 yêu cầu ô hash dạng password. Mã người dùng thật sẽ lộ trên màn hình/ảnh chụp.

Nguyên nhân (đọc mã `TelegramApiSetupPanel.kt` hàm `field`): đặt `inputType` có `TYPE_TEXT_VARIATION_PASSWORD` **rồi mới** gọi `setSingleLine(true)`. `setSingleLine` thay `TransformationMethod` thành `SingleLineTransformationMethod`, đè mất `PasswordTransformationMethod`. Font chữ gợi ý "API hash" là monospace (dấu hiệu inputType password vẫn được áp) nên khớp với giải thích này.

Sửa tối thiểu (chỉ file này): sau `setSingleLine(true)` đặt lại `transformationMethod = PasswordTransformationMethod.getInstance()` cho ô hash (hoặc gọi `setSingleLine` trước `inputType`). Không đổi gì khác.

### 10. Chấm tiêu chí

| Tiêu chí | Kết quả |
|---|---|
| P1 | ĐẠT (vòng 1) |
| P2 | ĐẠT — 3 APK Tiny đúng package/vc/vname/cert; Ni đã chứng minh chặn `meloOpen+meloSyncQa` |
| P3 | ĐẠT |
| P4 | **CHƯA ĐẠT** — Q1, Q3, Q4 (phương án dự phòng), Q5 (MediaSession state=3, vị trí tăng), Q6 khớp; Q2/Q3 lộ hash, trái 6.4 |
| P5 | ĐẠT |
| P6 | ĐẠT phần log/commit; lỗi hiển thị thuộc P4/6.4 |
| P7 | Chưa — chờ bản sửa |

### 11. APK

Không upload. Các APK `4f8b575` không phải candidate vì ô hash hiện rõ. Shanling hiện giữ vc120 (thường + open Debug); REWORK cài đè cùng vc120 bằng `install -r` (không downgrade). Âm thanh thật, Release UI, API thật, Y700/S25/FYT/Sony: chưa kiểm.

## Vòng 3 (sau REWORK 1) — 27/10/2026 ~12:30 (+07)

**Trạng thái: BLOCKED — CHỜ CHỦ DỰ ÁN (P7). Phần kỹ thuật P1–P6 ĐẠT.**

### 12. Git và sửa đổi

HEAD `be7c5fc`, cây sạch, `git diff --check` sạch. Mã: `bf174c5` chỉ đổi thứ tự 2 dòng trong `TelegramApiSetupPanel.kt` (`setSingleLine(true)` trước `inputType = type`), đúng mục 9. `setInputType` với biến thể password đặt `PasswordTransformationMethod` và vẫn giữ một dòng, nên đúng cách sửa. Báo cáo: Codex thêm mục "Vòng REWORK 1" nhưng cũng gộp dòng của các mục cũ (nội dung giữ nguyên) và đổi dòng đầu "phiên 2" thành "phiên GỘP — Vòng REWORK 1" — chỉ là trình bày, ghi lại để biết.

### 13. Opus kiểm

| Mục | Kết quả |
|---|---|
| Test Ni (Codex 09:32–09:33, XML) | Debug + Release: 2.128 / 21 skipped / 0 fail / 0 error; `TelegramApiCredentialPolicyTest` 34. Không chạy lại vì diff chỉ đổi thứ tự 2 dòng UI và XML trùng thời điểm sau commit `bf174c5` (09:34 commit, test cùng phiên) |
| SHA-256 3 APK Ni | Khớp báo cáo (`ED65DF38…7ADD`, `29F918E9…D8A2`, `DAF93D97…D0FB`) |
| Log Tiny | cert cả 3 `eecd3df7…3fce8`; `config.log` valid=true; open valid=false; hash: open Debug/Release found=false, thường found=true |
| Ảnh `00_tong_hop_rework1.png` | Q2: `12345` → 5 dấu chấm; Q3: 32 dấu chấm; Q3 sang số điện thoại; Q4 nhớ mã, câu dự phòng, panel trống sau khởi động lại; Q5 bản thường vào thẳng thư viện |
| Q6 | 8.080 dòng: 0 hash giả, 0 `api_hash`, 0 FATAL, 0 ANR |
| Màn hình | `SHANLING_SCREEN_END=OFF` 09:49:27 |

### 14. Chấm tiêu chí

| Tiêu chí | Kết quả |
|---|---|
| P1 | ĐẠT |
| P2 | ĐẠT |
| P3 | ĐẠT |
| P4 | ĐẠT — Q1–Q6 có ảnh; Q5 phát nhạc PLAYING ở vòng trước (vòng này chỉ kiểm nhanh vào thư viện, đúng prompt REWORK) |
| P5 | ĐẠT — REWORK chỉ đụng `TelegramApiSetupPanel.kt` |
| P6 | ĐẠT |
| P7 | **CHỜ** — chủ dự án làm mục 9 kế hoạch (bước 2–3 cần mã API thật + đăng nhập) hoặc duyệt ảnh từ xa |

### 15. APK

Đã upload Drive Ni N owner-only: thư mục `MeloVault_QA/E104-APK-CONG-KHAI-20261027-vc120-bf174c5` (3 APK + 10 PNG + `SHA256.txt`), SHA khớp khi chép, connector xác nhận chỉ owner cho thư mục và 3 APK. Link: `E104-APK-CONG-KHAI-apk-test.md`, ảnh: `E104-APK-CONG-KHAI-hinh-nghiem-thu.md`. Nhãn: **QA CANDIDATE — CHƯA NGHIỆM THU — KHÔNG ĐĂNG CÔNG KHAI** (khóa debug).

Chưa kiểm: đăng nhập/API thật, `API_ID_INVALID` trên máy, âm thanh thật, Release UI, Y700/S25/FYT/Sony.

## 16. Kết luận cuối — **PASS** (27/10/2026)

Chủ dự án trả lời trong phiên Opus nghiệm thu, 27/10/2026, sau khi nhận ảnh tổng hợp `00_tong_hop_rework1.png`, link Drive owner-only 3 APK `bf174c5` và câu hỏi "ĐẠT (kèm đồng ý merge vào nhánh tích hợp) hay báo lỗi": **"đã ĐẠT"**. P7 đạt → **E104-APK-CONG-KHAI: PASS** theo phạm vi đã kiểm.

- APK đã duyệt: vc120 `0.6.95-open` (`app.melovault.player.open`) và `0.6.95-beta` (`app.melovault.player`) @ `bf174c5`, ký khóa debug Tiny → **vẫn KHÔNG đăng công khai** (kế hoạch mục 3: khóa phát hành và website là nhiệm vụ sau).
- Giới hạn còn mở: âm thanh thật chưa xác nhận độc lập; Release UI, `API_ID_INVALID` trên máy, Y700/S25/FYT/Sony chưa kiểm; nhập lại mã dùng phương án "đóng hẳn app rồi mở lại".
- Merge `--no-ff` vào `feature/e93-03d-y700-offline-cockpit` theo xác nhận trên; không push.
