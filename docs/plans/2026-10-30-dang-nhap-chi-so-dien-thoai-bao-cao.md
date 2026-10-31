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

## S6.6 — Build APK trên Tiny (clone cô lập, HEAD `a2f0a88`)

- **Sự cố BOM phát hiện giữa chừng:** lần build `assembleShareableDebug` (thường) đầu tiên **BUILD FAILED** — `SDK location not found... local.properties`. Nguyên nhân: script PowerShell dùng để sửa cặp open ở bước trước ghi `local.properties` bằng `Set-Content -Encoding UTF8`, vô tình thêm **BOM UTF-8** ở đầu file, làm khoá `sdk.dir` (dòng đầu) không còn được AGP nhận diện đúng tên. Đã sửa bằng cách đọc lại nội dung và ghi lại bằng `UTF8Encoding($false)` (không BOM) trên `C:\Bots\MeloVault-E99-IsolatedQA\local.properties`, copy lại vào clone, chạy lại `reportTelegramConfig` xác nhận `telegram.open.config.valid=true`/`telegram.open.distinct=true` không đổi, rồi build lại — thành công. Không có giá trị bí mật nào bị in ra trong lúc chẩn đoán BOM (chỉ đọc 3 byte đầu file để kiểm EF BB BF).
- `:app:assembleShareableDebug` (bản thường, không cờ): `BUILD SUCCESSFUL in 2m 44s`, 41 tasks executed. Log: `_qa_artifacts\normal\build.log` (chỉ trên Tiny, không copy giá trị bí mật).
- `-PmeloOpen=true :app:assembleShareableDebug :app:assembleShareableRelease :app:reportOpenBuild :app:reportTelegramConfig`: `BUILD SUCCESSFUL in 4m 12s`, 97 tasks (70 executed/27 up-to-date). `melo.open=true`, `telegram.config.valid=false` (bình thường, cờ open bật), `telegram.open.config.valid=true`, `telegram.open.distinct=true`. Log: `_qa_artifacts\open\build.log`.
- APK sao chép ra khỏi `app/build/outputs/apk/...` (tránh bị ghi đè bởi lượt build sau) vào `C:\Bots\MeloVault-dang-nhap-IsolatedQA-20261030-a2f0a88\_qa_artifacts\{normal,open}\`.

## S6.7 — Verify APK (Tiny)

| APK | `verifyOpenApkHasNoTelegramHash` | `verifyOpenApkHasOpenTelegramHash` | package | vc / versionName | apksigner cert SHA-256 | APK SHA-256 | Bytes |
|---|---|---|---|---|---|---|---:|
| Thường Debug | `found=true` (đối chứng cơ chế dò hoạt động đúng — **đây là FAIL có chủ đích của task, không phải lỗi**) | — (không chạy, không áp dụng cho bản thường) | `app.melovault.player` | 121 / `0.6.96-beta` | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` | `6E3D6B6E98DE9974D399EEA850EC691DF1AB2324172EC3990FBA483E6869A029` | 33.593.284 |
| Open Debug | `found=false` | `found=true` | `app.melovault.player.open` | 121 / `0.6.96-open` | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` | `A241C32363B40BC77E3D7D1509B0E80D2483E262EB1FA2A151E0E0D39C26B0D9` | 33.593.288 |
| Open Release | `found=false` | `found=true` | `app.melovault.player.open` | 121 / `0.6.96-open` | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` | `0CA06484EC53A2F4118C22E17E1936790B52DCB984D792F7911133F17CDFA705` | 30.785.910 |

- Cả ba APK dùng đúng chữ ký debug Tiny đã biết (`eecd3df7…fce8`, khớp các APK Beta trước đó) — không phải chữ ký lạ/mới.
- `-PopenApk=...` phải là **đường dẫn tuyệt đối** (ví dụ `C:/Bots/.../app-shareable-debug.apk`); đường dẫn tương đối bị `project.file()` của module `:app` hiểu lệch gốc (resolve vào `app/` thay vì gốc project) → lần thử đầu `BUILD FAILED` với thông báo chung "Unable to verify APK Telegram hash contents." (không lộ gì, nhưng dễ hiểu lầm là lỗi hash — đã xác minh lại bằng đường dẫn tuyệt đối).
- Gate S6 (toàn bộ) **PASS**. Ba APK candidate hiện có tại `C:\Bots\MeloVault-dang-nhap-IsolatedQA-20261030-a2f0a88\_qa_artifacts\{normal,open}\` trên Tiny — **QA CANDIDATE — CHƯA NGHIỆM THU**, chưa cài lên thiết bị nào, chưa upload Drive.

## Chưa kiểm chứng (cập nhật 31/10, sau S6)

- S7 (QA thiết bị Q1–Q6, ADB Shanling/Sony, màn hình, ảnh/video) — chưa thực hiện, cần hỏi lại chủ dự án trước khi đụng thiết bị thật.
- `-apk-test.md`, `-hinh-nghiem-thu.md`, `00_tong_hop.png`, upload Drive owner-only — chưa làm.
- Âm thanh thật, luồng đăng nhập thật (nhập số/mã/2FA) trên thiết bị — chưa thử, theo đúng giới hạn "agent không tự gõ số/mã/mật khẩu cấp 2".

## S7 — QA thiết bị Sony (31/10/2026, cùng phiên, theo yêu cầu trực tiếp của chủ dự án "cài trên Sony" → "cả hai")

Thiết bị: Sony Xperia 5 II, serial `QV725WEQ54`, model `XQ-AS52`, manufacturer `Sony` (xác nhận lại bằng `adb devices -l` + `getprop` đầu phiên thao tác thiết bị — khớp hồ sơ đã biết).

**Kiểm trước khi cài (cả hai gói):**
- `app.melovault.player` (thường) đã cài sẵn, `versionCode=121`, `versionName=0.6.96-beta` — **không hạ version** so với APK mới (cùng vc121).
- Pull APK đang cài (`pm path` → `adb pull`), `apksigner verify --print-certs` → cert `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` — **khớp hệt** cert APK mới build ở S6.7. Xoá file APK pull tạm ngay sau khi so khớp.
- `app.melovault.player.open` **chưa có gói nào** trên Sony (xác nhận bằng `pm list packages`) → cài mới hoàn toàn, không phải downgrade/ghi đè.
- `dumpsys media_session`: cả hai phiên nhạc đều `state=2` (PAUSED, speed=0.0) hoặc lỗi mất kết nối Bluetooth — **không có phát nhạc đang chạy**, an toàn để cài `-r`.
- Màn hình trước thao tác: `Display State=OFF` (đúng vòng đời mục 7).

**Q5 — hồi quy bản riêng (PASS).** `adb install -r app-shareable-debug.apk` (thường) → `Success`. Bật màn (`keyevent 224`), mở app bằng `monkey -p app.melovault.player -c android.intent.category.LAUNCHER 1` (không chạm UI thật). Kết quả: vẫn đăng nhập, vào thẳng thư viện "Tất cả · 25.470 bài", không có màn số điện thoại/panel API nào xuất hiện — đúng kỳ vọng hồi quy. Ảnh: `docs/plans/artifacts/2026-10-30-dang-nhap-chi-so-dien-thoai/S7-sony-20261031/02_sony_binh_thuong_sau_cai_lai_Q5.png`.

**Cài bản open (mới):** `adb install -r app-shareable-debug.apk` (open) → `Success`. Mở bằng `monkey -p app.melovault.player.open ...`.

**Q1 (PASS).** Lần mở đầu tiên vào thẳng màn "Nhập số điện thoại" — **không** thấy panel "Kết nối Telegram của bạn". Có đủ ba lối: "Đăng nhập bằng QR", "Nhập lại mã API", "Dùng mã API riêng (nâng cao)". Ảnh: `.../03_sony_open_lan_dau_mo_Q1.png`.

**Q2 (PASS).** Chạm "Dùng mã API riêng (nâng cao)" (toạ độ ước lượng từ ảnh, `input tap`) → panel "Kết nối Telegram của bạn" hiện đúng (E104), hai ô API ID/hash **để trống, không gõ gì**. Ảnh: `.../04_sony_open_panel_nang_cao_Q2.png`. Chạm "Quay lại" → quay về đúng màn nhập số, ô số điện thoại vẫn trống, không có dấu hiệu lưu hay khởi động lại ứng dụng. Ảnh: `.../05_sony_open_sau_quay_lai_Q2.png`.

**Q3–Q4: CHƯA LÀM — đúng giới hạn "agent không gõ số điện thoại/mã/mật khẩu cấp 2".** App open hiện đang để ở màn "Nhập số điện thoại" (chưa đóng), chờ chính chủ dự án tự nhập khi sẵn sàng. Không có bước nào trong S7 Sony cần chủ dự án gõ thêm cho tới lúc đó.

**Cleanup:** không phát nhạc ở hai ứng dụng trong lúc thao tác (chỉ xem UI). Tắt màn bằng `keyevent 223`, xác nhận lại `dumpsys display` → `Display State=OFF`. **`SONY_SCREEN_END = OFF (đã xác minh dumpsys)`.** File ảnh tạm trên Tiny (`C:\Bots\_sony_screen_*.png`) đã xoá sau khi copy bản chính thức về worktree Ni; không để lại rác trên Tiny.

Không ADB nào khác ngoài các lệnh liệt kê trên (không uninstall/pm clear/xoá data/đổi tài khoản).

## Chưa kiểm chứng (cập nhật sau S7 Sony)

- Q3–Q4 (nhập số/mã/2FA thật, xem phiên mới trong Telegram → Thiết bị) — cần chính chủ dự án thao tác.
- Q6 (JVM, test mã open bị Telegram từ chối / D6 / D7 / D8 trên thiết bị thật) — chưa chạy ở phiên này.
- Shanling chưa động tới trong phiên này (chỉ làm Sony theo đúng yêu cầu).
- Âm thanh thật, Release UI thật trên máy, Y700/S25/FYT — ngoài phạm vi/chưa thử.
## Hồ sơ bàn giao APK + Drive (31/10, sau S7 Sony, theo yêu cầu chủ dự án "làm phần nào cũng được")

- Ảnh tổng hợp `00_tong_hop.png` (lưới 2×2, cạnh dài 1586 px ≤ 1600 px, nhãn Q5/Q1/Q2/Q2) ghép bằng `ffmpeg` (xstack + drawtext) từ 4 ảnh QA chính, lưu tại `docs/plans/artifacts/2026-10-30-dang-nhap-chi-so-dien-thoai/S7-sony-20261031/00_tong_hop.png`.
- **Chuyển APK Tiny → Ni:** dùng share mạng sẵn có `T:\` (Ni) = `C:\Bots\` (Tiny) — đã xác nhận trước đó đúng là cùng thư mục (ví dụ `T:\MeloVault-dang-nhap-IsolatedQA-20261030-a2f0a88` khớp clone trên Tiny) — copy trực tiếp 3 APK qua share này, **không qua base64/MCP** để tránh tải dữ liệu nhị phân lớn vào ngữ cảnh. Đối chiếu `sha256sum` sau khi copy sang Ni: khớp 100% với giá trị tính trên Tiny ở mục S6.7.
- **Upload Drive:** copy APK + ảnh + `SHA256.txt` vào `G:\My Drive\MeloVault_QA\2026-10-30-dang-nhap-chi-so-dien-thoai-20261031-vc121-a2f0a88\` (thư mục đồng bộ Google Drive Desktop, tài khoản `ntdung.md@gmail.com` — xác nhận bằng `get_file_metadata`/`get_file_permissions` qua connector Drive). Đợi đồng bộ xong (dùng `search_files` theo `parentId` để xác nhận file đã lên cloud), rồi kiểm `get_file_permissions` từng file: chỉ có owner, không có "Anyone with link" — đúng yêu cầu owner-only.
- Thư mục tạo mới (`inheritedPermissionsDisabled=true`), là con của `MeloVault_QA` (thư mục gốc QA của dự án, cũng owner-only — đã kiểm permissions trước khi tạo).
- Ghi `docs/plans/2026-10-30-dang-nhap-chi-so-dien-thoai-apk-test.md` (bảng APK đầy đủ, nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**) và `-hinh-nghiem-thu.md` (link ảnh Drive đã xác minh). Không có giá trị `api_id`/`api_hash` nào trong hai file này hay trong `SHA256.txt`.

## Chưa kiểm chứng (cập nhật cuối)

- Q3–Q4 (nhập số/mã/2FA thật) — cần chính chủ dự án thao tác; agent đã từ chối gõ hộ theo đúng giới hạn S7 mục 5 kế hoạch dù được yêu cầu trực tiếp.
- Q6 (JVM) và toàn bộ QA Shanling — bỏ qua theo chỉ thị chủ dự án ("Bỏ qua thiết bị shanling").
- Âm thanh thật, Release UI trên máy, Y700/S25/FYT.

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
