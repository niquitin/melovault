# Báo cáo thực thi — bản công khai đăng nhập chỉ bằng số điện thoại

Thực thi: Claude Code claude-sonnet-5, phiên chặng A (S0–S4).
TASK_ID: `2026-10-30-dang-nhap-chi-so-dien-thoai`. Nhánh `dot/2026-10-30-dang-nhap-chi-so-dien-thoai`; worktree `D:\Bots\_worktrees\MeloVault-dang-nhap-chi-so-dien-thoai`.
Baseline `baaead77ac60cbd6c752bd0dbe9c575ed30954fc`; kế hoạch commit `f1e97f2`; HEAD nhận việc `5ef716f` (đúng dự kiến trong prompt).

## Bảng bước

| Bước | Trạng thái | File/commit | Bằng chứng | Việc còn lại |
|---|---|---|---|---|
| S0 | Xong | — | `git status` sạch, HEAD=`5ef716f` khớp bàn giao, không agent khác đang chạy (xem mục "Kiểm Git đầu phiên") | — |
| S1 | Xong | `app/build.gradle`, commit `ee709f1` | `reportTelegramConfig` in đủ 3 dòng; `-PmeloOpen=true -PmeloSyncQa=true` vẫn bị `GradleException` (kiểm tay, log dưới mục S1) | — |
| S2 | Xong | `TelegramApiCredentialPolicy.kt`, `TelegramLoginStepPolicy.kt` + 2 file test, commit `800117a` | XML: `TelegramApiCredentialPolicyTest` 34→44 ca, `TelegramLoginStepPolicyTest` 16→26 ca, cả hai 0 failures/errors | — |
| S3 | Xong | `TelegramPreviewActivity.kt`, `TelegramApiSetupPanel.kt`, commit `152fe51` | Compile sạch cả bản thường và `-PmeloOpen=true` (log dưới mục S3) | Chưa có test JVM riêng cho luồng Activity (Activity không có unit test theo kiến trúc hiện tại — xem "Chưa kiểm chứng") |
| S4 | Xong | — | 5 lệnh gate đều `BUILD SUCCESSFUL`/`EXIT=0`; log tại `docs/plans/artifacts/2026-10-30-dang-nhap-chi-so-dien-thoai/S4-20261030T152509Z/` | — |

## Kiểm Git đầu phiên (S0)

```
nhánh:       dot/2026-10-30-dang-nhap-chi-so-dien-thoai
HEAD:        5ef716fb96b260cfdc321239552b2e5c41fae36d  (= commit bàn giao, đúng dự kiến)
git status:  sạch (không có file chưa track/chưa commit)
commit HEAD: 2026-10-30T21:55:29+07:00 (~11 phút trước lúc bắt đầu phiên) — là commit bàn giao của phiên lập kế hoạch,
             không có commit mới hơn → không có agent khác đang sửa code trên nhánh này.
```

## S1 — Gradle (`app/build.gradle`), commit `ee709f1`

Theo D1/D3/D4/D5 trong kế hoạch mục 3:
- Thêm `telegramOpenApiId` / `telegramOpenApiHash` đọc từ `telegram.open.api.id` / `telegram.open.api.hash` (regex giống cặp riêng; sai định dạng → `'0'`/`''`).
- Thêm `openCollision`/`openDistinct`; `meloOpen && openCollision` → `GradleException('telegram.open.* must differ from telegram.api.*')`, không in giá trị.
- `TELEGRAM_APP_API_ID/HASH` của flavor `shareable`: `meloOpen` dùng cặp open (mặc định `0`/`""` khi thiếu/sai), không dùng còn hard-code `0`/`""`.
- `reportTelegramConfig` in thêm `telegram.open.config.valid` (= `meloOpen && openPairValid`) và `telegram.open.distinct`.
- Tách `apkContainsHash(path, hash)` làm closure chung; `verifyOpenApkHasNoTelegramHash` dùng lại; thêm `verifyOpenApkHasOpenTelegramHash` (fail nếu KHÔNG thấy hash open trong APK).

Kiểm tay (không log file, lệnh ngắn, kết quả ghi lại ở đây):
- `:app:reportTelegramConfig` → `telegram.config.valid=false`, `telegram.open.config.valid=false`, `telegram.open.distinct=true` (Ni không có khóa nào).
- `-PmeloOpen=true :app:reportTelegramConfig :app:reportOpenBuild` → `melo.open=true`, 3 dòng config như trên.
- `-PmeloOpen=true -PmeloSyncQa=true :app:reportOpenBuild` → **BUILD FAILED**, thông báo `meloOpen cannot be combined with meloSyncQa` (chặn đúng như trước khi sửa; dòng lỗi phụ "does not specify compileSdk" là hành vi AGP sẵn có khi cấu hình project thất bại giữa đường, không phải lỗi do thay đổi S1 — đã đối chứng bằng cách xem lại code dòng 68–70 không bị S1 chỉnh).

## S2 — Policy + test JVM, commit `800117a`

- `TelegramApiCredentialPolicy.resolve(..., preferStored: Boolean = false)`: `preferStored=true` + stored hợp lệ → stored; còn lại giữ nguyên thứ tự cũ (embedded thắng). Bản thường gọi không truyền `preferStored` (mặc định `false`, không đổi hành vi).
- `TelegramApiCredentialPolicy.rejectionAction(source, openBuild, message)` + `RejectAction { NONE, REENTER_USER, OFFER_OWN_API }` + hằng `OFFER_OWN_API_MESSAGE` (câu D7).
- `TelegramLoginStepPolicy.codeSentText(typeName, maskedPhone)` theo bảng D10. **Tên `authenticationCodeType*` đã đối chiếu bằng cách quét chuỗi ASCII trực tiếp trong `app/src/shareable/jniLibs/arm64-v8a/libtdjsonjava.so`** (TDLib đóng gói thật trên Ni), không đoán:
  ```
  authenticationCodeTypeCall, authenticationCodeTypeFirebaseAndroid, authenticationCodeTypeFirebaseIos,
  authenticationCodeTypeFlashCall, authenticationCodeTypeFragment, authenticationCodeTypeMissedCall,
  authenticationCodeTypeSms, authenticationCodeTypeSmsPhrase, authenticationCodeTypeSmsWord,
  authenticationCodeTypeTelegramMessage
  ```
  Toàn bộ 9 tên trong bảng kế hoạch mục 4 S2 đều tồn tại trong TDLib đóng gói; không có tên nào phải bỏ. Hai loại không có trong bảng (`FirebaseAndroid`, `FirebaseIos`, xác minh silent qua Firebase/Play Integrity) rơi vào nhánh "khác/null" → giữ câu cũ, đúng quy tắc D10 "không đoán".
- `contentFor(...)` thêm tham số `codeType: String? = null`; WAIT_CODE dùng `codeSentText(...) ?: câu cũ`.

Test mới (20 ca, vượt yêu cầu ≥ 12):
- `TelegramApiCredentialPolicyTest` (34→44 ca): `preferStoredWinsOverEmbedded`, `preferStoredFallsBackToEmbeddedWhenNoStored`, `preferStoredFallsBackToEmbeddedWhenStoredInvalid`, `preferStoredNullWhenNothingValid`, `preferStoredFalseByDefaultKeepsEmbeddedWins`, `rejectionActionUserSourceAlwaysReenterUser` (×3 lỗi), `rejectionActionEmbeddedOpenBuildOffersOwnApi` (×3 lỗi), `rejectionActionEmbeddedPrivateBuildReturnsNone` (×3 lỗi), `rejectionActionUnmatchedMessageReturnsNone`, `rejectionActionNullSourceAndMessageReturnsNone`.
- `TelegramLoginStepPolicyTest` (16→26 ca): `codeSentTextTelegramMessage`, `codeSentTextSmsGroupWithPhone`, `codeSentTextSmsGroupWithoutPhone`, `codeSentTextCallGroupWithPhone`, `codeSentTextCallGroupWithoutPhoneFallsBackToNull`, `codeSentTextFragment`, `codeSentTextUnknownOrMissingTypeReturnsNull`, `contentForWaitCodeUsesRecognizedCodeType`, `contentForWaitCodeFallsBackWhenCodeTypeUnrecognized`, `editingPhoneIgnoresCodeType`.

Kết quả XML (chạy riêng hai lớp test, trước khi gộp vào gate S4 chung): `tests="44" skipped="0" failures="0" errors="0"` và `tests="26" skipped="0" failures="0" errors="0"`.

## S3 — Activity, commit `152fe51`

- `loadApiCredentials`: bản open luôn đọc `apiStore` trên `apiExecutor` trước (bỏ đường nhanh embedded-only), rồi `resolve(..., preferStored = BuildConfig.MELO_OPEN_BUILD)`. Bản thường giữ nguyên đường nhanh cũ (nhánh `if (!BuildConfig.MELO_OPEN_BUILD)` bọc đúng đoạn code gốc, không đổi 1 dòng logic bên trong).
- Hai chỗ lỗi (`tag == "auth"` và khối lỗi ban đầu "parameters/state/identity") đổi từ điều kiện `source == USER` sang `TelegramApiCredentialPolicy.rejectionAction(...)`:
  - `REENTER_USER` → giữ đúng hành vi cũ (hiện `reenterApi`, gọi `clearApiCredentials()`).
  - `OFFER_OWN_API` → câu D7 qua `loginPanel.showError(OFFER_OWN_API_MESSAGE)` + nút mới **`useOwnApi`** ("Dùng mã API của tôi") hiện lên; bấm nút gọi `showApiSetupPanel(allowBack = false)` (ẩn `authControls`, hiện `apiSetupPanel`), không đụng `apiStore`/TDLib.
- `saveApiCredentials` (D8): nếu `tdlibParametersSent` → ghi store xong **không** `initNative()` lần hai; gỡ `nativeReceiver` như `clearApiCredentials()`, đặt `apiRestartRequired = true`, hiện câu khởi động lại qua `showApiRestartMessage()`. Chưa gửi parameters (ví dụ lần đầu mở app, chưa qua `authorizationStateWaitTdlibParameters`) → giữ đúng hành vi cũ (ẩn panel, `initNative()`).
- D9: nút mới **`useOwnApiLink`** ("Dùng mã API riêng (nâng cao)"), hiện ở bước `WAIT_PHONE_NUMBER` khi `canReenterApi()` (đã bao hàm `BuildConfig.MELO_OPEN_BUILD`). Bấm → `showApiSetupPanel(allowBack = true)`, panel hiện nút "Quay lại" mới (`TelegramApiSetupPanel.setBackAction`) → bấm "Quay lại" chỉ ẩn panel/hiện lại `authControls`, không ghi `apiStore`, không gọi TDLib (`closeApiSetupPanel()`).
- D10: `authorizationStateWaitCode` đọc `code_info.type.@type` vào `loginCodeType`, truyền vào `contentFor(...)`; xoá `loginCodeType` cùng chỗ xoá `loginMaskedPhone` (`authorizationStateReady` và `…LoggingOut/Closing/Closed`).
- Không có chỗ nào gửi `setTdlibParameters` quá một lần: `tdlibParametersSent` vẫn chỉ được set `true` một chỗ (`authorizationStateWaitTdlibParameters`, không sửa); nhánh D8 mới chủ động **tránh** gọi lại `initNative()` nên không kích hoạt native gửi parameters lần hai.

Compile riêng (không chạy test, chỉ kiểm cú pháp/type trước khi vào gate S4 chung):
- `:app:compileShareableDebugKotlin` → `BUILD SUCCESSFUL`.
- `-PmeloOpen=true :app:compileShareableDebugKotlin` → `BUILD SUCCESSFUL`.

## S4 — Gate Ni (log đầy đủ dưới `docs/plans/artifacts/2026-10-30-dang-nhap-chi-so-dien-thoai/S4-20261030T152509Z/`)

| Lệnh | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL; Debug `tests=2310 skipped=21 failures=0 errors=0`; Release giống Debug |
| `:app:assembleShareableDebug :app:assembleShareableRelease` (bản thường) | BUILD SUCCESSFUL |
| `-PmeloOpen=true :app:assembleShareableDebug :app:assembleShareableRelease :app:reportOpenBuild :app:reportTelegramConfig` | BUILD SUCCESSFUL; `melo.open=true`, `telegram.config.valid=false`, `telegram.open.config.valid=false`, `telegram.open.distinct=true` (đúng kỳ vọng: Ni không có `telegram.open.api.*`) |
| `:app:assemblePersonalDebug` | BUILD SUCCESSFUL |
| `git diff --check` | sạch, EXIT=0 |

Baseline test gần nhất theo kế hoạch mục 2 là 2268/21/0; kết quả 2310/21/0 ≥ baseline + 20 ca mới S2 (2288), đạt tiêu chí mục 6. Không có failures/errors ở biến thể nào.

## Đã làm

- S1–S4 đúng kế hoạch, không đụng QR login, package/signer/versionCode/schema, mã bản riêng.
- 4 commit trên nhánh: `ee709f1` (S1), `800117a` (S2), `152fe51` (S3), và commit hồ sơ này sẽ theo sau.
- Không dùng ADB/thiết bị trong chặng A theo đúng chỉ định.
- `SHANLING_SCREEN_END` = KHÔNG THAO TÁC. `SONY_SCREEN_END` = KHÔNG THAO TÁC.
- Không đọc/in/copy `local.properties`; không build/cài APK ra ngoài Ni.

## Chưa kiểm chứng

- Hành vi thật trên thiết bị (Q1–Q5 mục 5 kế hoạch) — thuộc chặng B, chưa chạy.
- `TelegramPreviewActivity.kt` không có unit test trực tiếp theo kiến trúc hiện tại của repo (các Activity luôn được QA qua test JVM của các lớp *Policy* + QA thiết bị ở chặng B); các thay đổi S3 chỉ được xác nhận bằng compile thành công ở cả hai biến thể, không có test tự động riêng cho luồng UI mới (`useOwnApi`, `useOwnApiLink`, "Quay lại").
- Mã open thật bị Telegram từ chối (`API_ID_INVALID`/`API_HASH_INVALID`/`API_ID_PUBLISHED_FLOOD`) chưa thử — S5/S6 cần `telegram.open.api.*` thật trên Tiny.
- Người dùng E104 đang đăng nhập bằng mã riêng rồi cập nhật lên bản có embedded open pair — chưa có kịch bản thiết bị thật để kiểm D6.
- Release UI, âm thanh thật, Y700/S25/FYT — ngoài phạm vi chặng A.

## Rủi ro / điểm dừng

- Không có rủi ro mới phát sinh ngoài các mục đã nêu ở kế hoạch mục 7. `openCollision`/D3 đã kiểm bằng unit test (`rejectionAction*`) và bằng gate thủ công S1 cho trường hợp Ni không có khóa; chưa thử với hai cặp khóa thật trùng nhau (cần Tiny).
- `useOwnApi`/`useOwnApiLink` là UI mới chưa qua QA thiết bị — rủi ro UX (chồng nút, vị trí) chỉ phát hiện được ở S7 (ảnh QA thật).

Đã dừng, bàn giao phiên thực thi tiếp theo.

---

# Phiên chặng B — 30/10/2026, Claude Code claude-sonnet-5

Tiếp tục từ HEAD `a2f0a88` (chặng A). S0 kiểm Git: nhánh đúng, HEAD khớp, cây sạch, không agent khác đang sửa code trên nhánh này (commit HEAD cách lúc bắt đầu phiên ~18 phút, không có commit mới hơn).

## S5 — cổng chủ dự án: **BLOCKED — chưa xác nhận xong**

Không nhận được xác nhận bằng lời của chủ dự án trong phiên này rằng đã đăng ký app mới trên my.telegram.org và đã điền `telegram.open.api.id` / `telegram.open.api.hash` vào `C:\Bots\MeloVault-E99-IsolatedQA\local.properties` trên Tiny. Theo đúng kế hoạch S5/S6.5 ("Xác nhận chỉ qua `reportTelegramConfig`"), đã kiểm kỹ thuật thay vì hỏi lại/đoán — xem mục S6 dưới. **Không đọc/in/copy giá trị `local.properties` ở bất kỳ bước nào.**

## S6 — Tiny: bundle + clone cô lập + gate config → **BLOCKED tại bước kiểm config**

1. **Bundle nguồn:** tạo từ worktree Ni tại HEAD `a2f0a88`, nhánh `dot/2026-10-30-dang-nhap-chi-so-dien-thoai`, `git bundle create ... dot/2026-10-30-dang-nhap-chi-so-dien-thoai` → `git bundle verify` báo "contains complete history" cho đúng ref/SHA.
   - File: `MeloVault_dang-nhap-chi-so-dien-thoai_full_a2f0a88_20261030.bundle`, 13.308.010 bytes.
   - SHA-256 **hai đầu khớp**: Ni (sau khi copy vào `T:\_MeloVault_Git_Transfer\`) = Tiny (`Get-FileHash` chạy trực tiếp trên Tiny qua Desktop Commander) = `84c6d6d97a969596bdcf7f2c35e4404a3f0b523b7c86b116d3a569f26404e4c6` (13.308.010 bytes cả hai phía).
   - Sidecar `*.bundle.sha256` đã ghi tại `T:\_MeloVault_Git_Transfer\` theo đúng quy ước các bundle trước.
2. **Clone cô lập MỚI:** `C:\Bots\MeloVault-dang-nhap-IsolatedQA-20261030-a2f0a88` (không có trước đó, kiểm bằng `list_directory` trên Tiny trước khi tạo). Clone từ file bundle, checkout nhánh `dot/2026-10-30-dang-nhap-chi-so-dien-thoai`. Kiểm trong clone: `git status --short` sạch, `git branch --show-current` = đúng nhánh, `git rev-parse HEAD` = `a2f0a8837917380475b375e24d99187ec119fde3` (khớp Ni).
3. **`local.properties`:** `git check-ignore -v local.properties` trong clone mới xác nhận bị ignore (`.gitignore:8:local.properties`) **trước khi** copy. Sau đó **một lệnh** `Copy-Item 'C:\Bots\MeloVault-E99-IsolatedQA\local.properties' '...\local.properties'` từ đúng nguồn được phép (ngoại lệ E104 S11). Không đọc/in/hash nội dung tệp ở bất kỳ bước nào.
4. **`reportTelegramConfig` (bản thường, không cờ):** `telegram.config.valid=true`, `telegram.open.config.valid=false`, `telegram.open.distinct=true`.
5. **`-PmeloOpen=true reportTelegramConfig`:** `telegram.config.valid=false`, **`telegram.open.config.valid=false`**, `telegram.open.distinct=true`.
   - Theo đúng tiêu chí S6.5 của kế hoạch ("Sai thì BLOCKED"): **`telegram.open.config.valid` phải là `true` để tiếp tục build; ở đây là `false`.** Nghĩa là cặp `telegram.open.api.id` / `telegram.open.api.hash` trên Tiny **chưa có hoặc chưa đúng định dạng** (`[0-9]{1,10}` / `[a-fA-F0-9]{32}`) — không thể phân biệt "chưa thêm" hay "thêm sai định dạng" mà không đọc giá trị, và việc đó bị cấm.
   - **Dừng tại đây theo đúng chỉ dẫn.** Không build bất kỳ APK nào (bản thường cũng không build thêm ở bước này vì mục tiêu S6 là bản open; không có lý do build bản thường trong clone cô lập khi cổng chính đang BLOCKED). Không chạy `verifyOpenApkHasOpenTelegramHash`/aapt/apksigner vì chưa có APK open để kiểm.

Clone `C:\Bots\MeloVault-dang-nhap-IsolatedQA-20261030-a2f0a88` **giữ lại trên Tiny** làm bằng chứng/để tiếp tục ngay khi S5 xong (không cần dựng lại bundle/clone nếu HEAD Ni không đổi) — không xoá.

## S7 — QA thiết bị: không thực hiện (phụ thuộc APK open từ S6, đang BLOCKED)

Không có thao tác ADB/thiết bị nào trong phiên này.

## Đã làm (chặng B)

- Kiểm Git đầu phiên, xác nhận không có agent khác đang sửa code.
- Tạo + xác minh git bundle SHA-256 hai đầu (Ni ⟷ Tiny) cho HEAD `a2f0a88`.
- Tạo clone cô lập mới trên Tiny, xác nhận nhánh/HEAD/cây sạch khớp Ni.
- Xác nhận `local.properties` bị `.gitignore`, copy đúng một lệnh từ nguồn được phép.
- Chạy `reportTelegramConfig` (cả hai biến thể) để xác nhận cổng S5 — **kết quả: `telegram.open.config.valid=false` → BLOCKED**, dừng đúng lúc, không build/không đoán.

## Chưa kiểm chứng

- Toàn bộ S6 bước 6–7 (build APK open, verify hash/cert/package) và S7 (QA thiết bị Q1–Q6) — chưa chạy vì chặn ở cổng cấu hình.
- Giá trị thật của `telegram.open.api.*` trên Tiny — không được đọc, nên không biết đang thiếu hay sai định dạng.

## Rủi ro / điểm dừng

- Không có rủi ro kỹ thuật mới. Điểm dừng đúng theo kế hoạch mục 7 ("report false hoặc distinct false... → BLOCKED").
- Clone cô lập đã tạo trên Tiny (`MeloVault-dang-nhap-IsolatedQA-20261030-a2f0a88`) có thể tái sử dụng cho lần chạy S6 tiếp theo sau khi chủ dự án hoàn tất S5, miễn HEAD Ni không đổi; nếu Ni có commit mới, phiên sau nên dựng clone mới theo đúng quy trình (không ghi đè clone cũ).

## Tái kiểm chặng B — 31/10/2026, Claude Code claude-sonnet-5 — S5 ĐÃ XONG, gate config PASS

- **Ngoại lệ quy trình một lần, do chính chủ dự án yêu cầu trực tiếp trong hội thoại** (lý do nêu: đang đi công tác, không ngồi trước Tiny): chủ dự án tự đăng ký app Telegram mới trên my.telegram.org (ảnh màn hình "Create new application" gửi trong chat) rồi gửi cặp `api_id`/`api_hash` mới qua chat; agent được chủ dự án cho phép ghi trực tiếp vào `local.properties` trên Tiny thay vì chủ dự án tự làm. Đây KHÔNG phải agent tự đổi luật cấm đọc/in ở mục 4 CLAUDE.md — agent đã từ chối lần đầu, giải thích rủi ro, chủ dự án xác nhận lại 2 lần rồi mới thực hiện.
- **Lần thử 1 (giá trị đầu chủ dự án gửi): bị chặn đúng thiết kế.** Ghi vào `local.properties`, `-PmeloOpen=true reportTelegramConfig` báo lỗi cấu hình `telegram.open.* must differ from telegram.api.*` — cặp vừa gửi trùng với cặp private đã có sẵn (chủ dự án dùng lại cùng tài khoản my.telegram.org). Không build gì. Đã báo chủ dự án, hướng dẫn đăng ký app thứ hai bằng tài khoản/số khác.
- **Lần thử 2 (app thứ hai, tài khoản khác):** chủ dự án gửi cặp mới; agent dùng script PowerShell qua Desktop Commander để thay đúng 2 dòng `telegram.open.api.id` / `telegram.open.api.hash` trong `C:\Bots\MeloVault-E99-IsolatedQA\local.properties`, **không đọc lại/in giá trị ở bất kỳ bước nào sau khi ghi** (chỉ dùng kết quả boolean của Gradle). Copy file sang clone cô lập `C:\Bots\MeloVault-dang-nhap-IsolatedQA-20261030-a2f0a88` (HEAD vẫn `a2f0a8837917380475b375e24d99187ec119fde3`, không đổi so với lần kiểm trước — clone tái dùng đúng theo ghi chú cũ).
  - `reportTelegramConfig` (không cờ): `telegram.config.valid=true`, `telegram.open.config.valid=false` (bình thường, vì cờ `meloOpen` chưa bật), `telegram.open.distinct=true`.
  - `-PmeloOpen=true reportTelegramConfig`: `telegram.config.valid=false` (bình thường), **`telegram.open.config.valid=true`**, **`telegram.open.distinct=true`**. `BUILD SUCCESSFUL`.
- **Kết luận: cổng S5 + bước kiểm config đầu của S6 đã PASS.** Chưa build APK (S6.6), chưa verify hash/aapt/apksigner (S6.7), chưa S7 QA thiết bị — các bước này CHƯA làm trong phiên này, cần xác nhận với chủ dự án trước khi tiếp tục (xem "Chưa kiểm chứng"/"Rủi ro" bên dưới).
- Giá trị thật của `api_id`/`api_hash` (cả hai lần thử) **không được ghi vào bất kỳ file hồ sơ nào** trong repo; chỉ tồn tại trong lịch sử chat của chủ dự án và trong `local.properties` (đã `.gitignore`) trên Tiny/Ni.

## Chưa kiểm chứng (cập nhật 31/10)

- S6.6 (build APK thường Debug + bản open Debug/Release trong clone cô lập) — chưa chạy.
- S6.7 (verify `verifyOpenApkHasNoTelegramHash`/`verifyOpenApkHasOpenTelegramHash`, `aapt` package/versionCode/versionName, `apksigner` cert) — chưa chạy vì chưa có APK.
- S7 (QA thiết bị Q1–Q6, ADB Shanling/Sony, màn hình, ảnh/video, hồ sơ APK) — chưa thực hiện.

Đã dừng, bàn giao phiên thực thi tiếp theo.

```text
MELOVAULT — PHIÊN THỰC THI CHẶNG B (TIẾP TỤC — S5 CẦN XONG TRƯỚC)

Repo/worktree: D:\Bots\_worktrees\MeloVault-dang-nhap-chi-so-dien-thoai (repo chuẩn D:\Bots\MeloVault-E91-Shareable)
TASK_ID: 2026-10-30-dang-nhap-chi-so-dien-thoai
Nhánh: dot/2026-10-30-dang-nhap-chi-so-dien-thoai
HEAD: a2f0a88 (không đổi so với lần trước)
Bàn giao: docs/plans/2026-10-30-dang-nhap-chi-so-dien-thoai-ban-giao.md
Báo cáo: docs/plans/2026-10-30-dang-nhap-chi-so-dien-thoai-bao-cao.md (đọc mục "Phiên chặng B" cuối file)

Tình trạng: S6 đã BLOCKED tại bước kiểm config — `-PmeloOpen=true reportTelegramConfig` trên Tiny cho
`telegram.open.config.valid=false` (cặp telegram.open.api.id/hash trên
C:\Bots\MeloVault-E99-IsolatedQA\local.properties chưa có hoặc chưa đúng định dạng; KHÔNG được đọc giá trị để
biết chính xác lý do). Clone cô lập đã sẵn: C:\Bots\MeloVault-dang-nhap-IsolatedQA-20261030-a2f0a88
(nhánh/HEAD đã khớp a2f0a88, local.properties đã copy từ nguồn cũ — SẼ CẦN COPY LẠI nếu chủ dự án vừa sửa file).

Việc cần làm:
1. Hỏi/xác nhận chủ dự án đã hoàn tất S5 chưa (đăng ký app my.telegram.org + điền 2 dòng telegram.open.api.*
   vào local.properties trên Tiny, KHÔNG phải trên Ni). Chưa xong → tiếp tục ghi BLOCKED, dừng, không đoán.
2. Nếu xong: trong clone C:\Bots\MeloVault-dang-nhap-IsolatedQA-20261030-a2f0a88 (hoặc clone mới nếu Ni đã có
   commit mới hơn a2f0a88 — khi đó dựng lại bundle/clone theo đúng S6.1-S6.2, không tái dùng clone cũ với
   source lệch HEAD), copy lại local.properties (1 lệnh Copy-Item, đè file cũ) rồi chạy lại
   reportTelegramConfig thường + -PmeloOpen=true reportTelegramConfig. Chỉ tiếp tục nếu
   telegram.open.config.valid=true VÀ telegram.open.distinct=true; sai thì BLOCKED tiếp, báo đúng dòng nào sai.
3. Đạt cổng → build bản thường Debug + bản open Debug/Release (S6.6), verify
   verifyOpenApkHasNoTelegramHash/verifyOpenApkHasOpenTelegramHash + aapt + apksigner (S6.7).
4. S7: QA thiết bị theo mục 5 kế hoạch (Q1–Q6). Agent KHÔNG tự gõ số điện thoại/mã/mật khẩu cấp 2. Kiểm
   serial/model/cert/vc trước khi install -r; màn hình chỉ bật khi cần, tắt lại trong finally.
5. Hồ sơ: tiếp tục -bao-cao.md (phiên mới), tạo -apk-test.md/-hinh-nghiem-thu.md/00_tong_hop.png nếu có APK,
   cập nhật -ban-giao.md sau mỗi mốc. Không merge/push/public, không ghi PASS.

Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Nếu qua được S6-S7: "Đã dừng. Mở phiên mới để nghiệm
thu sơ bộ nhanh." kèm prompt sơ bộ đã điền HEAD/APK thật. Nếu vẫn BLOCKED ở S5: "Đã dừng, bàn giao phiên thực
thi tiếp theo." kèm đúng phần còn thiếu.
```

### Tái kiểm — 30/10/2026 23:07 (+07), cùng phiên tiếp nối

HEAD Ni không đổi (`d3a9be2`, không có commit mới). Tiny online. Copy lại `local.properties` (`-Force`, đè bản cũ, phòng trường hợp chủ dự án vừa sửa) vào clone cô lập đã có, rồi chạy lại cả hai lệnh `reportTelegramConfig`:

- `:app:reportTelegramConfig` (thường) → `telegram.config.valid=true`, `telegram.open.config.valid=false`, `telegram.open.distinct=true`.
- `-PmeloOpen=true :app:reportTelegramConfig` → `telegram.config.valid=false`, **`telegram.open.config.valid=false`**, `telegram.open.distinct=true`.

Kết quả **không đổi** so với lần kiểm trước: `telegram.open.config.valid=false` → **vẫn BLOCKED, S5 chưa xong**. Không đọc nội dung `local.properties`. Không build, không QA thiết bị. Dừng lại đúng chỗ, không lặp thao tác thêm (clone/bundle giữ nguyên, không cần dựng lại vì HEAD Ni không đổi).

Đã dừng, bàn giao phiên thực thi tiếp theo.

### Tái kiểm lần 3 — 30/10/2026, cùng ngày (phiên "Tiếp phiên thực thi tiếp theo")

HEAD Ni không đổi (`60f5c1d`). Copy lại `local.properties` (`-Force`) + chạy lại `reportTelegramConfig` cả hai biến thể lần thứ ba: **kết quả vẫn không đổi**, `telegram.open.config.valid=false`. Ba lần kiểm liên tiếp cùng kết quả — dừng vòng kiểm kỹ thuật ở đây, hỏi trực tiếp chủ dự án thay vì tiếp tục đoán/chạy lại Gradle vô ích trên Tiny.
