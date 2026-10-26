# E104-APK-CONG-KHAI — Bản APK "open" không chứa mã Telegram, người dùng tự nhập API ID/hash

- **Lập bởi:** Claude Opus 5.5 (lập kế hoạch), 26/10/2026
- **Nhánh:** `dot/E104-APK-CONG-KHAI`, tạo từ nhánh tích hợp `feature/e93-03d-y700-offline-cockpit`
- **Baseline:** `a638099` (merge workflow Codex/Opus; đã chứa E103 `773d887`)
- **Plan commit:** commit chứa file này (ghi trong `git log` của nhánh)
- **Loại:** **LỚN** (chạm Telegram/TDLib parameters, build config, security). Thực thi: **Codex `gpt-6-astra` medium**, phiên 1 = code + test Ni (S1–S9), phiên 2 = build Tiny + QA + báo cáo (S10–S16).

## 1. Quyết định chủ dự án (26/10/2026, trong phiên lập kế hoạch)

1. Muốn đưa APK lên **web công khai**.
2. Bản công khai **không chứa mã Telegram**; mỗi người dùng **tự nhập API ID/hash** của họ (lấy ở my.telegram.org).
3. Bản công khai dùng **gói riêng `app.melovault.player.open`**, cài song song, không đụng bản riêng `app.melovault.player`.
4. Nơi đăng: **website riêng** (làm ở nhiệm vụ sau, xem mục 3).

## 2. Mục tiêu

- Gradle tạo được biến thể **open** bằng cờ `-PmeloOpen=true`: package `app.melovault.player.open`, `TELEGRAM_APP_API_ID = 0`, `TELEGRAM_APP_API_HASH = ""` **bất kể local.properties có gì**, versionName hậu tố `-open`.
- App open lần đầu mở thấy màn **"Kết nối Telegram của bạn"** để nhập API ID + API hash. Mã được lưu **mã hóa bằng AndroidKeyStore** trong `noBackupFilesDir`, rồi đi vào luồng đăng nhập số điện thoại/QR hiện có.
- **Bản riêng (không cờ) giữ nguyên hành vi:** dùng mã nhúng, không bao giờ hiện màn nhập mã, phiên Telegram trên máy không bị ảnh hưởng.
- Có **cổng kiểm máy móc**: task Gradle quét APK open và báo `true/false` có chứa hash nhúng của máy build hay không (không bao giờ in giá trị).

## 3. Ngoài phạm vi (KHÔNG làm trong E104)

- Khóa ký phát hành (release keystore). E104 vẫn ký bằng khóa debug hiện tại của Tiny như các bản Beta, nên APK open của E104 **chỉ là QA CANDIDATE owner-only, KHÔNG đăng công khai**.
- Website, trang tải, đăng APK lên web, GitHub, Telegram.
- Đổi/xóa mã API sau khi đã đăng nhập (trong Cài đặt). E104 chỉ cho nhập lại **khi chưa đăng nhập**.
- Đổi tên app/icon riêng cho bản open (nhãn vẫn "MeloVault"; phân biệt bằng versionName `-open`).
- Personal flavor, backend, songKey, schema DB, Media3/queue, Playlist sync.

## 4. Phương án và lý do

| Phương án | Kết luận |
|---|---|
| Cờ Gradle `-PmeloOpen=true` trên flavor `shareable` (giống mẫu `meloSyncQa`) | **CHỌN.** Dùng chung source set `shareable`, ít file build nhất, bản riêng không đổi khi không bật cờ. |
| Product flavor mới `open` | Bỏ: phải tách/nhân bản source set `shareable` (hơn 80 file), dễ lệch code. |
| Làm rối (obfuscate) hash trong APK | Bỏ: vẫn lấy ra được, không thật sự an toàn. |
| Một API ID chung riêng cho bản công khai | Chủ dự án không chọn. |

**Nguồn mã khi chạy** (thứ tự ưu tiên, hàm thuần dễ test): mã nhúng hợp lệ → mã người dùng đã lưu hợp lệ → không có. Bản riêng luôn có mã nhúng, nên luôn đi nhánh 1 như hôm nay.

## 5. Hiện trạng mã (đã đọc tại baseline)

- `app/build.gradle`: dòng 22–30 đọc `telegram.api.id/hash` từ local.properties (sai định dạng → 0/""). Flavor `shareable` dòng 87–101: `applicationId 'app.melovault.player'`, `signingConfig signingConfigs.debug`, `versionCode 119`, `versionName '0.6.94'`, `versionNameSuffix '-beta'`, hai `buildConfigField` Telegram. Mẫu cờ `meloSyncQa` ở dòng 39–58.
- `TelegramPreviewActivity.kt`:
  - dòng 924–929: thiếu mã nhúng → `showMessage("Chưa cấu hình Telegram API cho bản thử nội bộ.")` và `return` trước `initNative()`.
  - dòng 5951–5989 `tdlibDatabaseKey()`: mẫu AndroidKeyStore AES-GCM + file `.enc` ghi qua `.part` rồi rename. **Mẫu để sao chép** cho kho mã mới.
  - dòng 5991–6008 `initNative()`.
  - dòng 6152–6171: chẩn đoán lỗi stage `parameters`, có `.replace(BuildConfig.TELEGRAM_APP_API_HASH, "[app-hash]")`. **Bẫy:** ở bản open hash nhúng là `""`, và `String.replace("", x)` chèn `x` vào giữa MỌI ký tự. Bắt buộc bỏ qua chuỗi rỗng.
  - dòng 6330–6346: `setTdlibParameters` đặt `api_id/api_hash` từ `BuildConfig`. Đây là nơi duy nhất gửi parameters (grep toàn `app/src`).
  - đường lỗi bước đăng nhập (~6135–6150) dùng `TelegramLoginStepPolicy.errorFor(...)`.
- `TelegramLoginPanel.kt` (262 dòng): Views thuần, callback `onSubmit/onResend/onEditPhone/onBack/onQr`. Đây là mẫu giao diện cho panel mới.
- `ShareableTdlibOwner.kt`: `attachUi(directory, callback)`, `detachUi(callback)`, client đóng khi mọi owner rời (`onClientClosed`).
- Manifest: không có `authorities` cứng, không có chuỗi `app.melovault.player` trong code Kotlin. Đổi applicationId không gây xung đột provider [CẦN XÁC NHẬN bằng `aapt dump badging` + cài thử ở phiên 2].

## 6. Thay đổi chính xác

### 6.1 `app/build.gradle` (build config, được chủ dự án duyệt qua quyết định mục 1)
- Thêm `def meloOpen = (project.findProperty('meloOpen') ?: 'false').toString().trim() == 'true'`.
- `meloOpen && meloSyncQa` → `throw new GradleException('meloOpen cannot be combined with meloSyncQa')`.
- Trong flavor `shareable`:
  - `applicationId meloOpen ? 'app.melovault.player.open' : 'app.melovault.player'`
  - `versionCode 120`, `versionName '0.6.95'` (bump chung cho cả hai biến thể của candidate này)
  - `versionNameSuffix meloOpen ? '-open' : '-beta'`
  - `buildConfigField 'int', 'TELEGRAM_APP_API_ID', meloOpen ? '0' : telegramApiId`
  - `buildConfigField 'String', 'TELEGRAM_APP_API_HASH', meloOpen ? '""' : ('"' + telegramApiHash + '"')`
  - `buildConfigField 'boolean', 'MELO_OPEN_BUILD', meloOpen ? 'true' : 'false'`
- Flavor `personal`: thêm `buildConfigField 'boolean', 'MELO_OPEN_BUILD', 'false'` chỉ khi code dùng chung cần đến (code mới nằm trong `shareable`, nên thường KHÔNG cần; không thêm nếu build personal không lỗi).
- Task `reportOpenBuild`: in `melo.open=true|false`.
- Task `verifyOpenApkHasNoTelegramHash` (chỉ đọc):
  - Nhận APK qua `-PopenApk=<đường dẫn>`.
  - Hash local rỗng/không hợp lệ → in `open.apk.telegram.hash.check=SKIPPED_NO_LOCAL_HASH`.
  - Ngược lại mở APK bằng `java.util.zip.ZipFile`, đọc từng entry và tìm chuỗi byte ASCII của hash (cả chữ thường và chữ hoa).
  - In `open.apk.telegram.hash.found=true|false`, `found=true` thì `throw GradleException`.
  - **Không bao giờ in hash, ID, tên entry chứa hash hay đoạn byte.**

### 6.2 MỚI `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/TelegramApiCredentialPolicy.kt` (Kotlin thuần, không Android)
- `data class Credentials(val apiId: Int, val apiHash: String, val source: Source)`; `enum Source { EMBEDDED, USER }`.
- `fun parseId(raw: String): Int?`: trim, 1–10 chữ số, `> 0`, `<= Int.MAX_VALUE`.
- `fun parseHash(raw: String): String?`: trim, đúng 32 ký tự hex, trả về chữ thường.
- `fun validate(rawId, rawHash): Result` (ok + Credentials USER, hoặc lỗi theo ô: `ID_EMPTY`, `ID_INVALID`, `HASH_EMPTY`, `HASH_INVALID`) và câu tiếng Việt cho từng lỗi.
- `fun resolve(embeddedId: Int, embeddedHash: String, stored: Credentials?): Credentials?` theo thứ tự mục 4.
- `fun maskId(id: Int): String`, ví dụ `•••• 34` (chỉ 2 số cuối).
- `fun scrub(message: String, secrets: List<String>): String`: **bỏ qua chuỗi rỗng/blank**, thay mỗi secret bằng `[redacted]`.
- `fun loginErrorFor(message: String?): String?`: `API_ID_INVALID`, `API_HASH_INVALID`, `API_ID_PUBLISHED_FLOOD` → câu tiếng Việt dễ hiểu ("Mã API ID/hash không đúng. Kiểm tra lại ở my.telegram.org."), còn lại → null.

### 6.3 MỚI `.../telegram/TelegramApiCredentialStore.kt` (Android)
- File `noBackupFilesDir/e104_tg_api_v1.enc`, alias `melovault_e104_tg_api_v1`, AES-256-GCM, IV 12 byte đầu. Ghi `.part` rồi rename (chép mẫu `tdlibDatabaseKey()`, **không sửa hàm cũ**).
- Payload: `"<id>\n<hash>"` UTF-8.
- `read(): Credentials?`: file thiếu/hỏng/giải mã lỗi → `null`, không crash, không log nội dung (chỉ log `stage=api_store_read_fail` không kèm dữ liệu).
- `write(c)` và `clear()`: `clear` chỉ xóa đúng file `e104_tg_api_v1.enc`(+`.part`), **không đụng** thư mục TDLib, `e91_tdlib_db_key_v1.enc` hay cache.
- Gọi trên thread nền (Keystore có thể chậm); UI chờ callback.

### 6.4 MỚI `.../telegram/TelegramApiSetupPanel.kt` (Views/AppCompat, kiểu `TelegramLoginPanel`)
- Tiêu đề **"Kết nối Telegram của bạn"**.
- Mô tả 3 dòng: (1) vào my.telegram.org → API development tools; (2) chép App api_id và App api_hash; (3) "Mã chỉ được lưu mã hóa trên máy này, không gửi cho ai ngoài Telegram."
- Ô **API ID**: số, `DigitsKeyListener`. Ô **API hash**: `TYPE_TEXT_VARIATION_PASSWORD`, `importantForAutofill=no`.
- Nút **"Lưu và tiếp tục"**, nút phụ **"Mở my.telegram.org"** (`Intent.ACTION_VIEW` `https://my.telegram.org`, bọc `runCatching`).
- Lỗi hiện dưới ô theo `TelegramApiCredentialPolicy.validate`; nút bận khi đang lưu.
- Chạm tối thiểu `MeloDesignTokens.MIN_TOUCH_DP`, màu lấy cùng `TelegramLoginPanel.Colors`.
- Panel không giữ giá trị ở đâu ngoài EditText. Sau khi lưu xong thì xóa text.

### 6.5 Sửa `TelegramPreviewActivity.kt` (giữ diff nhỏ, chỉ các điểm sau)
1. Thêm field `private var apiCredentials: TelegramApiCredentialPolicy.Credentials? = null` và `apiStore` lazy.
2. Dòng 924–929: thay khối kiểm tra bằng đọc store trên nền rồi gọi `resolve(BuildConfig.TELEGRAM_APP_API_ID, BuildConfig.TELEGRAM_APP_API_HASH, stored)`:
   - có mã → `apiCredentials = it; initNative()`;
   - không có và `BuildConfig.MELO_OPEN_BUILD` → hiện `TelegramApiSetupPanel` ở đúng chỗ đang chứa login panel;
   - không có và **không** phải bản open → giữ nguyên `showMessage("Chưa cấu hình Telegram API cho bản thử nội bộ.")`.
   - Bản riêng có mã nhúng **không được đọc store** nếu không cần (đọc mã nhúng trước, chỉ đọc store khi nhúng không hợp lệ).
3. Panel lưu thành công → `apiStore.write` (nền) → `apiCredentials = c` → ẩn panel → `initNative()`.
4. Dòng 6340–6341: `api_id/api_hash` lấy từ `apiCredentials` (null → không gửi, `showMessage` lỗi cấu hình).
5. Dòng 6168: dùng `TelegramApiCredentialPolicy.scrub(message, listOf(secret, apiCredentials?.apiHash.orEmpty(), BuildConfig.TELEGRAM_APP_API_HASH))` thay cho chuỗi `.replace` của hash. Giữ regex `[redacted]` hiện có.
6. Lỗi ở bước đăng nhập: khi `apiCredentials?.source == USER` và `loginErrorFor(message) != null` → `loginPanel.showError(câu đó)` và hiện thêm lựa chọn **"Nhập lại mã API"**.
7. "Nhập lại mã API" (cũng hiện như liên kết nhỏ dưới bước số điện thoại, **chỉ ở bản open và chỉ khi chưa đăng nhập**): `apiStore.clear()` → tách client TDLib của UI (`ShareableTdlibOwner.detachUi(nativeReceiver)`; chờ `onClientClosed` hoặc trạng thái `authorizationStateClosed`) → reset `tdlibParametersSent=false` → hiện panel nhập mã.
   - [CẦN XÁC NHẬN] Nếu tách/khởi tạo lại client không sạch (playback service vẫn giữ client), dùng phương án dự phòng: xóa mã đã lưu và hiện câu "Đã xóa mã API. Hãy đóng hẳn ứng dụng rồi mở lại để nhập mã mới." Ghi rõ phương án đã dùng trong báo cáo.
   - Tuyệt đối không xóa thư mục TDLib, không `logOut`.
8. Không log `apiHash`/`apiId` ở bất kỳ đâu. Log chỉ dùng `source=EMBEDDED|USER|NONE`.

### 6.6 MỚI `app/src/test/java/com/bsdung/musicassistantcar/data/telegram/TelegramApiCredentialPolicyTest.kt`
Ít nhất các ca:
- `parseId`: "", "0", "-1", "abc", "12345678901", " 123 " → 123.
- `parseHash`: 31/33 ký tự, ký tự không hex, chữ hoa → chữ thường, có khoảng trắng hai đầu.
- `resolve`: nhúng hợp lệ thắng mã đã lưu; nhúng 0/"" + có mã đã lưu → USER; cả hai thiếu → null; nhúng sai định dạng (hash 31 ký tự) → bỏ qua.
- `scrub`: secret rỗng không làm thay đổi chuỗi (`scrub("abc", listOf("")) == "abc"`), nhiều secret, secret xuất hiện nhiều lần.
- `maskId` và `loginErrorFor`: 3 mã lỗi và trường hợp null.

## 7. Các bước thực thi

### Phiên 1 — Codex, Ni (code + test)
- **S1.** Kiểm tra đầu phiên theo AGENTS.md/CLAUDE.md. Ở thư mục chính: `git checkout dot/E104-APK-CONG-KHAI` chỉ khi cây sạch; HEAD phải là plan commit (con của `a638099`). Sai thì DỪNG.
- **S2.** Viết `TelegramApiCredentialPolicy.kt` + test (6.2, 6.6). Chạy `:app:testShareableDebugUnitTest --tests '*TelegramApiCredentialPolicyTest*'`.
- **S3.** Viết `TelegramApiCredentialStore.kt` (6.3).
- **S4.** Viết `TelegramApiSetupPanel.kt` (6.4).
- **S5.** Sửa `TelegramPreviewActivity.kt` theo 6.5 (1–8). Commit nhỏ cho mỗi bước S2–S5 là tốt.
- **S6.** Sửa `app/build.gradle` (6.1).
- **S7.** Bộ lệnh chuẩn trên Ni:
  `.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain`
  `.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain`
  rồi thêm `.\gradlew.bat -PmeloOpen=true :app:assembleShareableDebug :app:assembleShareableRelease :app:reportOpenBuild --console=plain`, và `.\gradlew.bat :app:assemblePersonalDebug --console=plain` để chắc personal không vỡ.
  Phải thử `-PmeloOpen=true -PmeloSyncQa=true` → kỳ vọng lỗi Gradle có chủ đích. Ghi số test passed/skipped.
- **S8.** Dùng `aapt2 dump badging` (hoặc `apkanalyzer manifest application-id`) cho 4 APK. Kỳ vọng: package thường `app.melovault.player`, vc 120, `0.6.95-beta`; open `app.melovault.player.open`, vc 120, `0.6.95-open`. Chạy `verifyOpenApkHasNoTelegramHash` trên APK open (Ni không có hash → `SKIPPED_NO_LOCAL_HASH` là bình thường).
- **S9.** `git diff --check`. Commit code, ghi phần phiên 1 trong `docs/plans/E104-APK-CONG-KHAI-bao-cao.md`. Shanling KHÔNG cần bật trong phiên 1. DỪNG.

### Phiên 2 — Codex, Tiny + Shanling (build + QA + báo cáo)
- **S10.** Tạo Git bundle từ `dot/E104-APK-CONG-KHAI` và ghi SHA-256. Trên Tiny (Desktop Commander, clone cô lập mới, không đè clone cũ) verify SHA-256 → checkout đúng commit.
- **S11.** Build trên Tiny với Telegram config chỉ có ở Tiny: bản thường Debug và bản open Debug + Release (`-PmeloOpen=true`). Chạy `reportTelegramConfig` (bản thường phải `true`).
- **S12.** Chạy `verifyOpenApkHasNoTelegramHash` cho **cả hai** APK open. **Bắt buộc `found=false`.** Đối chứng: chạy cùng task trên APK thường để chứng minh task tìm được (kỳ vọng task báo `found=true` và fail). Đây là kiểm chứng task hoạt động; chạy riêng, ghi kết quả true/false, không in giá trị.
- **S13.** Với mỗi APK ghi: package, vc/vname, signer SHA-256 (`apksigner verify --print-certs`, phải đúng signer debug Tiny `eecd3df7…ce8` như các Beta), SHA-256, bytes, đường dẫn, Telegram config embedded true/false.
- **S14.** Chuyển APK sang Ni và kiểm SHA-256. QA Shanling theo mục 8, có vòng đời màn hình `wake → thao tác/ảnh → sleep + Display OFF` trong `finally`.
- **S15.** Báo cáo đầy đủ: bảng QA, mục bàn giao APK cho cả 2 candidate (thường vc120 và open vc120), `SHANLING_SCREEN_END`.
- **S16.** Commit báo cáo (không commit ảnh có PII). DỪNG chờ Opus nghiệm thu.

## 8. QA từ xa — Codex tự làm qua ADB trên Shanling M3 Ultra (serial 30313860180939, Ni)

Gate trước khi cài:
- `adb devices -l` + `getprop` model/manufacturer.
- `pm list packages app.melovault.player.open` phải **rỗng** (cài mới).
- Bản thường: package khớp, vc 120 > vc đang cài (không downgrade), signer khớp.
- MediaSession không đang phát.
- Chỉ dùng `adb -s 30313860180939 install -r "<APK>"`.

| # | Màn | Hành động | Kỳ vọng | Màn |
|---|---|---|---|---|
| Q1 | App **open** (mới cài) | Mở `app.melovault.player.open` lần đầu | Thấy "Kết nối Telegram của bạn", 2 ô, nút "Lưu và tiếp tục"; KHÔNG thấy ô số điện thoại; không crash | ON, ảnh |
| Q2 | Panel nhập mã | Nhập ID `abc` (bàn phím số không cho thì để trống) và hash `12345`, bấm Lưu | Báo lỗi dưới từng ô, vẫn ở panel | ON, ảnh trước/sau |
| Q3 | Panel nhập mã | Nhập **mã giả đúng định dạng**: ID `1`, hash 32 ký tự `0` (không phải mã thật). Bấm Lưu | Sang bước nhập số điện thoại / QR của Telegram (chứng tỏ parameters đã gửi, TDLib chấp nhận định dạng). **KHÔNG nhập số điện thoại.** | ON, ảnh |
| Q4 | Mở lại | `am force-stop app.melovault.player.open` rồi mở lại | Vào thẳng bước số điện thoại, không hỏi lại mã (store bền). Bấm "Nhập lại mã API" → panel trống hiện lại (hoặc câu dự phòng 6.5-7, ghi rõ) | ON, ảnh |
| Q5 | Bản **thường** vc120 | `install -r` bản thường, mở app | Vào thẳng thư viện như trước, vẫn đăng nhập, không có panel nhập mã. Bấm phát 1 bài → Media3 PLAYING, tiến trình tăng | ON, ảnh + dumpsys media_session |
| Q6 | Log | `logcat -d` sau Q1–Q5, tìm chuỗi 32 số `0` và `api_hash` | 0 dòng chứa hash giả; không có stack crash `FATAL EXCEPTION` của 2 package | OFF |

Giới hạn:
- **Đăng nhập thật bằng mã API thật và lỗi `API_ID_INVALID`** chỉ xuất hiện sau khi gửi số điện thoại, nên agent KHÔNG làm (không nhập số điện thoại hay mã API thật). Phần này chỉ kiểm bằng JVM test và bước chủ dự án.
- Âm thanh thật: CHƯA XÁC NHẬN.
- Sony: không bắt buộc ở E104. Nếu chạy thì chỉ là tầng phụ, cùng gate.

## 9. Bác sĩ tự kiểm tra (chủ dự án, 4 thao tác)

1. **Y700 hoặc Shanling** → mở app **MeloVault bản open** (Cài đặt Android → Ứng dụng: thấy hai app MeloVault; bản open có phiên bản `0.6.95-open`) → thấy màn "Kết nối Telegram của bạn".
2. Bấm **"Mở my.telegram.org"** → trình duyệt mở đúng trang. Lấy API ID/hash của bạn, quay lại app, dán vào hai ô, bấm **"Lưu và tiếp tục"** → thấy bước số điện thoại.
3. Đăng nhập bằng số điện thoại + mã OTP như bình thường → thấy thư viện nhạc Telegram, bấm một bài → nghe được tiếng.
4. Mở **bản MeloVault riêng** (bản cũ) → vẫn đăng nhập, thư viện còn nguyên, không bị hỏi mã API.

## 10. Tiêu chí PASS / FAIL (Opus nghiệm thu chấm thẳng)

**PASS khi đủ tất cả:**
- P1. JVM test mới ≥ 15 ca pass. Toàn bộ `testShareableDebugUnitTest` + `testShareableReleaseUnitTest` pass, số skipped không tăng so với baseline.
- P2. 4 APK build được. Package/vc/vname đúng mục S8. Kết hợp `meloOpen + meloSyncQa` bị Gradle chặn.
- P3. Trên Tiny có hash thật: `verifyOpenApkHasNoTelegramHash` → `found=false` cho open Debug **và** Release, `found=true` cho APK thường (đối chứng).
- P4. Q1–Q6 PASS có ảnh. Q5 chứng minh bản riêng không đổi hành vi.
- P5. Diff không chạm: schema/DB, songKey, Media3/queue, `tdlibDatabaseKey()`, thư mục TDLib, personal flavor (ngoài dòng `MELO_OPEN_BUILD` nếu cần), CLAUDE.md/docs/workflow.
- P6. Không có log/commit/báo cáo nào chứa API ID/hash thật. `scrub` bỏ qua chuỗi rỗng (có test).
- P7. Chủ dự án làm mục 9 và nói ĐẠT (hoặc duyệt ảnh từ xa cho Q1–Q5 và tự làm bước 2–3).

**FAIL / REWORK khi có một trong:**
- Bản thường hiện panel nhập mã hoặc mất phiên đăng nhập.
- APK open chứa hash.
- Crash ở Q1–Q5.
- Hash/ID xuất hiện trong log.
- Nhập lại mã làm mất dữ liệu TDLib/thư viện.
- `String.replace("")` làm hỏng thông báo lỗi.

## 11. Rủi ro và rollback

- **Bản riêng hỏng đăng nhập do `resolve` sai:** chặn bằng test `resolve` + Q5. Rollback: không merge nhánh, Shanling cài lại bản vc119 cũ thì bị chặn downgrade, nên **phải DỪNG báo chủ dự án** chứ không ép `-d`.
- **Hai app trên cùng máy chạy hai TDLib:** dữ liệu ở hai sandbox khác nhau (package khác), không chung session. Chủ dự án tự gỡ bản open nếu không muốn giữ. Agent không `uninstall`.
- **Keystore lỗi trên máy lạ:** `read()` trả null thì người dùng chỉ phải nhập lại mã, không crash.
- **Telegram từ chối mã người dùng:** thông báo dễ hiểu + "Nhập lại mã API".
- **APK open vẫn ký khóa debug:** ghi rõ trong bàn giao **"QA CANDIDATE — CHƯA NGHIỆM THU — KHÔNG ĐĂNG CÔNG KHAI"**. Khóa phát hành là việc riêng sau này.

## 12. Trường hợp phải STOP (ghi báo cáo, không đoán)

- Cây bẩn, HEAD/nhánh lạ, agent khác đang code.
- ADB không thấy Shanling/unauthorized, signer mismatch, downgrade, sai package. `app.melovault.player.open` đã có sẵn trên máy (lạ) → DỪNG, không gỡ.
- Tiny không có Telegram config hợp lệ → P3 không chứng minh được: BLOCKED.
- Cần sửa ngoài các file ở mục 6, đổi DB/schema, hoặc phải đụng `ShareableTdlibOwner` ngoài việc gọi API sẵn có → DỪNG xin kiểm toán.
- Q3 không sang bước số điện thoại (TDLib từ chối định dạng mã giả trước cả khi gửi số) → ghi FAIL kèm log đã lọc. Không thử mã thật.

## 13. Bàn giao APK QA

- Phiên 2 ghi trong báo cáo cho **2 candidate**: (a) bản thường `app.melovault.player` vc120 `0.6.95-beta`; (b) bản open `app.melovault.player.open` vc120 `0.6.95-open` Debug (+ Release nếu build).
- Mỗi candidate gồm: loại build, package, vc/vname, Git HEAD/commit nguồn, SHA-256, bytes, cert SHA-256, đường dẫn thật trên Tiny/Ni, Telegram config embedded true/false, kết quả `verifyOpenApkHasNoTelegramHash`, test/QA, cảnh báo.
- Opus nghiệm thu kiểm độc lập, upload **Google Drive owner-only** (không Anyone with link), ghi `docs/plans/E104-APK-CONG-KHAI-apk-test.md` với nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**.
- **Không đăng công khai ở E104**, kể cả khi PASS.
