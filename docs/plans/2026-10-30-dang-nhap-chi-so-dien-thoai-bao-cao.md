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
