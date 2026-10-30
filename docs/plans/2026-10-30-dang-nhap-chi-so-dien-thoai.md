# Kế hoạch — bản công khai đăng nhập chỉ bằng số điện thoại

TASK_ID: `2026-10-30-dang-nhap-chi-so-dien-thoai`. Nhánh `dot/2026-10-30-dang-nhap-chi-so-dien-thoai`; worktree `D:\Bots\_worktrees\MeloVault-dang-nhap-chi-so-dien-thoai`.
Lập kế hoạch: Claude Code, 30/10/2026, theo yêu cầu chủ dự án trong chat: “Lập kế hoạch đăng nhập chỉ số điện thoại cho bản công khai”, sau khi gửi ảnh app iOS “Music Player” (TestFlight). Ảnh đó chỉ có hai bước: nhập số → nhập mã.
Trạng thái: **KẾ HOẠCH — chưa code.** Bàn giao: [`-ban-giao.md`](2026-10-30-dang-nhap-chi-so-dien-thoai-ban-giao.md).

## 1. Mục tiêu và phạm vi

**Mục tiêu:** bản open `app.melovault.player.open` mở lên là vào thẳng màn **Nhập số điện thoại**. Người dùng không cần biết API ID/hash. Luồng giống app iOS: số → mã Telegram → mật khẩu cấp 2 nếu có → vào thư viện.

Cách làm: nhúng **một API ID/hash riêng dành cho bản công khai** (khác mã của bản riêng). Đây là phương án ở [E104 mục 4](E104-APK-CONG-KHAI.md) mà chủ dự án đã không chọn ngày 27/10. Yêu cầu ngày 30/10 thay quyết định đó **chỉ cho bản open**. Phần còn lại của E104 giữ nguyên: tách gói, không chứa mã của bản riêng, màn tự nhập API làm đường dự phòng.

**Trong phạm vi**
1. Gradle: đọc cặp khóa mới `telegram.open.api.id` / `telegram.open.api.hash` (chỉ có ở Tiny). Khi `-PmeloOpen=true` thì nhúng cặp này; thiếu hoặc sai định dạng thì nhúng 0/"" như hiện nay.
2. Chặn build nếu mã open trùng mã bản riêng. Kiểm APK open: **không** chứa hash bản riêng, **có** chứa hash open.
3. Bản open chọn nguồn mã theo thứ tự: mã người dùng đã tự lưu (nếu có) → mã open nhúng → màn tự nhập.
4. Telegram từ chối mã open nhúng (`API_ID_INVALID`, `API_HASH_INVALID`, `API_ID_PUBLISHED_FLOOD`) → báo câu tiếng Việt cố định và mời “Dùng mã API của riêng tôi”, đi lại đường E104 (lưu → đóng hẳn app → mở lại).
5. Bản open, màn nhập số: thêm link nhỏ **“Dùng mã API riêng (nâng cao)”** dẫn tới panel E104 hiện có.
6. Màn nhập mã nói rõ mã gửi qua đâu (app Telegram / SMS / cuộc gọi…), như dòng “Code sent in Telegram” của app iOS. Áp cho cả hai bản, vì dùng chung `TelegramLoginStepPolicy`.

**Ngoài phạm vi:**
- Đăng công khai; khóa ký phát hành. E104 ghi rõ khóa debug hiện tại chỉ dùng cho QA owner-only.
- Đổi package, signer, versionCode (giữ vc121 `0.6.96`), schema, songKey, TDLib DB.
- Mã của bản riêng; QR login (đường file nội bộ cho Lenovo, không sửa); Personal/backend.
- Bản riêng `app.melovault.player` chỉ đổi phần chữ ở mục 6, không đổi nguồn mã.

## 2. Baseline và bằng chứng

- Baseline `baaead77ac60cbd6c752bd0dbe9c575ed30954fc`, nhánh tích hợp `feature/e93-03d-y700-offline-cockpit`. Đã gồm UI/UX R1c, Đảo, D1–D3 và P2-A; test Ni gần nhất 2268/21/0 mỗi biến thể theo hồ sơ anh-bia.
- Đầu phiên lập kế hoạch: nhánh tích hợp sạch, commit cuối 21:14, `diff --check` sạch. Thư mục chính `D:\Bots\MeloVault-E91-Shareable` đang ở nhánh khác và có tệp chưa track của nhiệm vụ khác; không đụng tới.
- Mã hiện tại (đọc trực tiếp):
  - `app/build.gradle:22–35`: đọc `telegram.api.id/hash`, `meloOpen`, task `reportTelegramConfig` (bản open luôn `false`).
  - `app/build.gradle:68–100`: `verifyOpenApkHasNoTelegramHash` quét zip tìm hash bản riêng.
  - `app/build.gradle:136–149`: `applicationId`, `TELEGRAM_APP_API_ID/HASH` (bản open = 0/""), `MELO_OPEN_BUILD`.
  - `TelegramApiCredentialPolicy.kt`: `resolve()`, trong đó **mã nhúng luôn thắng**; `loginErrorFor()`.
  - `TelegramPreviewActivity.kt:1139–1160` `loadApiCredentials` (có mã nhúng thì `initNative()` ngay, không đọc store); `:1163` `saveApiCredentials` (gọi `initNative()` trực tiếp); `:1182` `canReenterApi`; `:1186` `clearApiCredentials`; `:1210` `showApiRestartMessage`.
  - `TelegramPreviewActivity.kt:6782/6800`: lỗi mã API **chỉ xử lý khi nguồn là USER**.
  - `TelegramPreviewActivity.kt:6989–7005`: `setTdlibParameters` gửi `api_id/api_hash` đúng một lần.
  - `TelegramPreviewActivity.kt:7017–7022`: `authorizationStateWaitCode` dùng `code_info.phone_number`, `timeout`, `next_type`; **chưa dùng `code_info.type`**.
  - `TelegramLoginStepPolicy.kt:46–126`: nội dung các bước; đã có “Sửa số điện thoại”, “Gửi lại mã”, 2FA, email.
- Kết luận: bản riêng build ở Tiny **đã** đăng nhập chỉ bằng số. Bản open phải tự nhập API vì E104 cố ý nhúng ID 0.

## 3. Quyết định thiết kế (model thực thi không tự đổi)

| # | Quyết định | Lý do |
|---|---|---|
| D1 | Khóa mới `telegram.open.api.id` / `telegram.open.api.hash` trong `local.properties`, cùng regex `[0-9]{1,10}` / `[a-fA-F0-9]{32}`, sai thì coi như không có | Tách hẳn khỏi mã bản riêng; thiếu khóa thì bản open tự quay về hành vi E104 = rollback không cần sửa code |
| D2 | Bản open nhúng mã open; bản thường vẫn nhúng mã riêng như cũ. Không bao giờ nhúng mã riêng vào bản open | Giữ cam kết E104 |
| D3 | Cả hai cặp hợp lệ mà **trùng ID hoặc trùng hash** → `GradleException('telegram.open.* must differ from telegram.api.*')`, không in giá trị | Tránh vô tình phát tán mã bản riêng |
| D4 | `reportTelegramConfig` in thêm `telegram.open.config.valid=true\|false` và `telegram.open.distinct=true\|false` (chỉ boolean). Dòng `telegram.config.valid` giữ nguyên nghĩa | Gate Tiny không lộ giá trị |
| D5 | Giữ `verifyOpenApkHasNoTelegramHash` (hash riêng phải vắng). Thêm task `verifyOpenApkHasOpenTelegramHash`: in `open.apk.open.hash.found=true\|false`, `false` thì fail. Dùng chung hàm quét zip | Chứng minh APK open đúng là có mã open |
| D6 | Bản open: `resolve` ưu tiên **mã người dùng đã lưu**, rồi mới tới mã open nhúng. Bản thường giữ mã nhúng thắng | Người đã đăng nhập bằng mã riêng ở E104 không bị đổi `api_id` dưới phiên TDLib đang có. Người mới không có mã lưu nên dùng mã open. **[CẦN XÁC NHẬN chủ dự án; mặc định áp dụng D6]** |
| D7 | Bản open + nguồn EMBEDDED + lỗi khớp `loginErrorFor` → câu “Mã kết nối chung của ứng dụng đang bị Telegram từ chối. Bạn có thể dùng API ID/hash của riêng mình.” + nút “Dùng mã API của tôi” mở panel E104 | Mã open bị khóa thì toàn bộ người dùng kẹt; cần lối thoát |
| D8 | Lưu mã người dùng khi đã gửi `setTdlibParameters` → **không** `initNative()` lại. Lưu xong đặt `apiRestartRequired`, hiện “Đã lưu mã API. Hãy đóng hẳn ứng dụng rồi mở lại.” Chưa gửi parameters thì giữ hành vi cũ | TDLib chỉ nhận parameters một lần; E104 cũng phải khởi động lại |
| D9 | Link “Dùng mã API riêng (nâng cao)” chỉ ở bản open, bước WAIT_PHONE_NUMBER, khi `canReenterApi()`; panel có nút đóng quay lại màn số (đóng không lưu gì) | Lối thoát chủ động, không làm rối màn chính |
| D10 | Câu bước mã theo `code_info.type.@type` (bảng mục 4 S2). Loại lạ hoặc thiếu → câu hiện tại | Giống “Code sent in Telegram”; không đoán khi TDLib có loại mới |

## 4. Các bước thực thi

**Chặng A — Ni, code + test (S0–S4).** **Chặng B — Tiny build + QA + hồ sơ (S5–S7).** Mỗi chặng một phiên thực thi mới; chặng B cần cổng chủ dự án ở S5.

**S0 — nhận việc.** Kiểm Git theo CLAUDE.md §1 trong worktree trên: đúng nhánh, HEAD = commit hồ sơ kế hoạch, cây sạch. Đọc mục 2–3. Không code nếu HEAD lạ.

**S1 — Gradle (`app/build.gradle`).**
1. Sau dòng 28 thêm `telegramOpenApiId/Hash` đọc từ `telegram.open.api.*`, cùng kiểm regex như D1.
2. Thêm `def openDistinct` theo D3. Nếu `meloOpen` và cả hai cặp hợp lệ mà `!openDistinct` → `GradleException` (không in giá trị).
3. Dòng 147–148: `meloOpen ? telegramOpenApiId : telegramApiId`, hash tương tự (open thiếu thì `'0'` / `'""'`).
4. `reportTelegramConfig`: thêm hai dòng boolean D4. **Không** đổi công thức dòng `telegram.config.valid`.
5. Tách thân quét zip của `verifyOpenApkHasNoTelegramHash` thành closure `apkContainsHash(path, hash)`; thêm task D5. Không in tên entry hay exception.
- Xong khi: build Ni (không có khóa) cho bản open vẫn ID 0; `reportTelegramConfig` in đủ 3 dòng; `-PmeloOpen=true -PmeloSyncQa=true` vẫn bị chặn.

**S2 — Policy + test JVM.**
- `TelegramApiCredentialPolicy.resolve(embeddedId, embeddedHash, stored, preferStored: Boolean = false)`: `preferStored=true` + stored hợp lệ → stored; còn lại như cũ. Bản riêng gọi `false`.
- `fun rejectionAction(source: Source?, openBuild: Boolean, message: String?): RejectAction`, với `enum RejectAction { NONE, REENTER_USER, OFFER_OWN_API }`:
  - USER + khớp lỗi → `REENTER_USER`;
  - EMBEDDED + openBuild + khớp lỗi → `OFFER_OWN_API`;
  - còn lại → `NONE`.
- Câu D7 đặt hằng trong policy.
- `TelegramLoginStepPolicy.codeSentText(typeName: String?, maskedPhone: String?): String?`:

| `@type` TDLib | Câu |
|---|---|
| `authenticationCodeTypeTelegramMessage` | “Mã đã gửi trong ứng dụng Telegram trên thiết bị khác của bạn.” |
| `authenticationCodeTypeSms`, `…SmsWord`, `…SmsPhrase` | “Mã đã gửi qua SMS đến <số che>.” (không có số: “qua SMS.”) |
| `authenticationCodeTypeCall`, `…FlashCall`, `…MissedCall` | “Telegram sẽ gọi đến <số che> để báo mã.” |
| `authenticationCodeTypeFragment` | “Mã đã gửi qua Fragment.” |
| khác/null | `null` → giữ câu hiện tại |

  - Thêm tham số `codeType: String? = null` vào `contentFor`; WAIT_CODE dùng `codeSentText(...) ?: câu cũ`.
  - **Trước khi viết, đối chiếu tên `@type` với TDLib đóng gói** (header/td_api trong repo hoặc log thật); tên không có trong bản TDLib thì bỏ khỏi bảng, không đoán.
- Test, tối thiểu 12 ca mới:
  - `TelegramApiCredentialPolicyTest`: private embedded thắng stored; open stored thắng; open không stored → embedded; open stored hỏng → embedded; open không gì → null; `rejectionAction` 3 nhánh × 3 lỗi + lỗi khác → NONE; `toString` không lộ hash.
  - `TelegramLoginStepPolicyTest`: mỗi nhóm type, có/không số che, loại lạ → câu cũ, `editingPhone` không đổi.
- Xong khi: test mục tiêu hai biến thể xanh.

**S3 — Activity (`TelegramPreviewActivity.kt`, `TelegramApiSetupPanel.kt` nếu cần nút đóng).**
1. `loadApiCredentials`: nếu `BuildConfig.MELO_OPEN_BUILD` → **luôn đọc `apiStore` trên `apiExecutor` trước**, rồi `resolve(..., preferStored = true)`. Bản thường giữ đường nhanh hiện tại (D6).
2. Hai chỗ lỗi `:6782`, `:6800`: thay điều kiện `source == USER` bằng `rejectionAction`. `REENTER_USER` = hành vi cũ. `OFFER_OWN_API` = câu D7 + nút “Dùng mã API của tôi” (hiện `apiSetupPanel`; ẩn `authControls` như luồng lần đầu).
3. `saveApiCredentials` theo D8: nếu `tdlibParametersSent` → ghi store, `apiRestartRequired = true`, gỡ `nativeReceiver` như `clearApiCredentials`, hiện câu khởi động lại. Không gọi `initNative()` lần hai.
4. Link D9 dưới nút Tiếp tục ở bước số (chỉ open). Panel thêm “Quay lại” khi mở từ link (đóng không lưu). Vùng bấm ≥ 48 dp.
5. `authorizationStateWaitCode`: lấy `info?.optJSONObject("type")?.optString("@type")` → biến `loginCodeType`, truyền vào `contentFor`. Xóa biến khi `Ready` hoặc đổi số, cùng chỗ đang xóa `loginMaskedPhone`. Không log số/mã.
- Xong khi: compile cả `-PmeloOpen=true`; không còn đường gửi `setTdlibParameters` hai lần (đọc lại mã).

**S4 — gate Ni + commit.** Chạy theo CLAUDE.md §4, log ra file dưới `docs/plans/artifacts/<TASK_ID>/S4-*`, chỉ đọc dòng tổng kết + cộng XML:
```powershell
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
.\gradlew.bat -PmeloOpen=true :app:assembleShareableDebug :app:assembleShareableRelease :app:reportOpenBuild :app:reportTelegramConfig --console=plain
.\gradlew.bat :app:assemblePersonalDebug --console=plain
git diff --check
```
- Kỳ vọng:
  - failures/errors = 0; số test ≥ baseline + ca mới; ghi rõ skipped.
  - Ni không có khóa → `telegram.config.valid=false`, `telegram.open.config.valid=false`. APK Ni không cài.
- Commit theo mốc S1/S2/S3/hồ sơ; cập nhật báo cáo + bàn giao. **Hết chặng A: dừng, bàn giao chặng B.**

**S5 — cổng chủ dự án (BLOCKED tới khi xong).**
- Chủ dự án tự đăng ký ứng dụng mới ở my.telegram.org. Tên không chứa chữ “Telegram”, ví dụ “MeloVault Open”.
- Chủ dự án tự thêm hai dòng `telegram.open.api.id=` / `telegram.open.api.hash=` vào `C:\Bots\MeloVault-E99-IsolatedQA\local.properties` trên Tiny. Đây là nguồn mà ngoại lệ copy E104 S11 cho phép.
- Agent **không** tạo tài khoản, không nhập/đọc/in/hash giá trị.
- Xác nhận chỉ qua `reportTelegramConfig` ở S6. Chủ dự án chưa xác nhận đã thêm → chặng B BLOCKED.

**S6 — Tiny build.** Như E104 S11/S12 và các nhiệm vụ 30/10:
1. Bundle source đã commit, SHA-256 hai đầu khớp.
2. Clone cô lập mới `C:\Bots\MeloVault-dang-nhap-IsolatedQA-<ts>`.
3. **Một lệnh** `Copy-Item` `local.properties` từ nguồn trên; `git check-ignore` phải ignore tệp này.
4. Chạy `reportTelegramConfig` (bản thường) → `telegram.config.valid=true`.
5. Chạy `-PmeloOpen=true reportTelegramConfig` → `telegram.open.config.valid=true`, `telegram.open.distinct=true`. Sai thì BLOCKED.
6. Build bản thường Debug và bản open Debug + Release; chạy nền, log và exit code riêng.
7. Kiểm từng APK:
   - `verifyOpenApkHasNoTelegramHash` → found=false cho cả hai APK open; đối chứng found=true trên APK thường;
   - `verifyOpenApkHasOpenTelegramHash` → found=true cho cả hai APK open;
   - aapt: package/vc121/versionName đúng (`0.6.96-open`, `0.6.96-beta`);
   - apksigner cert khớp `eecd3df7…fce8`; SHA-256/bytes.

**S7 — QA thiết bị + hồ sơ.** Theo mục 5, rồi:
- `-bao-cao.md`, `-apk-test.md`, `-hinh-nghiem-thu.md`, `00_tong_hop.png`.
- Drive owner-only nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**; chặn thì BLOCKED đúng phần.
- Prompt sơ bộ. Dừng.

## 5. QA thiết bị (3–6 thao tác)

Chung cho mọi bước:
- Kiểm serial/model trên đúng máy. Trước khi cài, kiểm gói đích: vc không giảm, cert khớp, media không phát. Chỉ `install -r`.
- Màn ON chỉ khi cần; `finally` tắt và kiểm `Display State=OFF`.
- **Agent không gõ số điện thoại, mã đăng nhập hay mật khẩu cấp 2.** Bước cần đăng nhập thật do chủ dự án tự nhập; agent chỉ chụp trước/sau.
- Thiết bị đích của bản open:
  - Shanling đã có `app.melovault.player.open` vc120 (E104, đang ở panel trống) → `install -r` hợp lệ.
  - Sony: kiểm `pm list packages app.melovault.player.open`. **Chưa có gói open thì không tự cài mới** → hỏi chủ dự án **[CẦN XÁC NHẬN]**.
  - Nếu không thiết bị nào hợp lệ → BLOCKED phần QA trên máy.

| Bước | Thiết bị / thao tác | Kỳ vọng | Bằng chứng |
|---|---|---|---|
| Q1 | Máy có bản open (chưa có mã lưu): cài open Debug vc121 → mở app | Vào thẳng “Nhập số điện thoại”; **không** thấy panel “Kết nối Telegram của bạn”; có link “Dùng mã API riêng (nâng cao)” | Ảnh trước cài (panel) / sau cài (màn số) |
| Q2 | Q1 → chạm link nâng cao → chạm “Quay lại” (không nhập gì) | Panel E104 hiện; quay lại màn số, không lưu, không khởi động lại | 2 ảnh |
| Q3 | **Chủ dự án** nhập số → Tiếp tục | Màn mã hiện câu theo loại gửi (vd “Mã đã gửi trong ứng dụng Telegram…”), có “Gửi lại mã (Ns)”, “Sửa số điện thoại” | Ảnh màn mã (che số) |
| Q4 | **Chủ dự án** nhập mã (+ 2FA nếu có) | Vào thư viện; trong Telegram → Thiết bị, phiên mới mang tên app open | Ảnh thư viện (che PII); chủ dự án xác nhận tên phiên |
| Q5 | Sony bản riêng `app.melovault.player`: cài Debug thường vc121 cùng source | Vẫn đăng nhập, không có màn số/panel; thư viện + phát như trước (hồi quy) | Ảnh + media state |
| Q6 | JVM (không trên máy) | Mã lưu thắng (D6); mã open bị từ chối → mời dùng mã riêng (D7); lưu sau parameters → yêu cầu khởi động lại (D8) | Tên test + XML; ghi **CHƯA KIỂM TRÊN MÁY** |

Chưa kiểm, ghi rõ trong báo cáo:
- Mã open thật bị Telegram khóa;
- người dùng E104 đang đăng nhập bằng mã riêng rồi cập nhật;
- Release UI; âm thanh thật; Y700/S25/FYT.

## 6. Tiêu chí hoàn tất

- Test Debug/Release 0 failures/errors, ca mới mục S2 có trong XML; assemble bản thường, open, Personal xanh; `diff --check` sạch.
- Tiny:
  - `telegram.open.config.valid=true`, `telegram.open.distinct=true`;
  - APK open: hash riêng **vắng**, hash open **có** (Debug + Release); APK thường không đổi package/cert/vc.
- Q1–Q2 có ảnh. Q3–Q4 do chủ dự án thực hiện hoặc ghi CHỜ CHỦ DỰ ÁN. Q5 hồi quy đạt.
- Hồ sơ đủ; trạng thái chính thức **CHỜ CHỦ DỰ ÁN NGHIỆM THU**.

## 7. Rủi ro, rollback, điểm dừng

**Rủi ro**
- **Hash open lấy được từ APK.** Mọi app Telegram bên thứ ba đều vậy.
  - Nếu bị lạm dụng, Telegram có thể khóa ID (`API_ID_PUBLISHED_FLOOD`) → mọi người dùng bản open phải dùng D7 cho tới khi có ID mới.
  - Vì vậy bắt buộc tách khỏi ID bản riêng (D3).
- **Điều khoản API Telegram cho app bên thứ ba:** chủ dự án đọc trước khi phát hành công khai. Nhiệm vụ này không phát hành.
- **Đổi `api_id` dưới phiên đã đăng nhập:** chưa biết hành vi chắc chắn. D6 tránh đổi cho người đã có mã lưu. Không thử trên tài khoản thật.
- **Tên `@type` mã:** phụ thuộc phiên bản TDLib đóng gói. Đối chiếu ở S2, không đoán.

**Rollback**
- Không cần sửa code: chủ dự án xóa hai khóa `telegram.open.*` trên Tiny → bản open build lại quay về E104 (ID 0, panel tự nhập).
- Có sửa code: revert từng commit S1–S3 theo thứ tự ngược. Không hạ version, không xóa dữ liệu.

**Dừng và ghi BLOCKED khi:**
- HEAD/cây lạ hoặc agent khác đang sửa;
- S5 chưa xong;
- `report` false hoặc distinct false;
- APK open chứa hash riêng (tuyệt đối không phát tán);
- cert/package/vc lệch;
- thiết bị không authorized;
- Sony cần cài mới gói open mà chưa được phép;
- cần đổi schema/service/signing.

## 8. Câu hỏi cho chủ dự án

1. Đồng ý D6 (mã người dùng đã lưu thắng mã open nhúng trong bản open)? Mặc định: có.
2. Tự đăng ký ứng dụng mới trên my.telegram.org và thêm `telegram.open.*` vào nguồn `local.properties` trên Tiny (S5)?
3. Cho phép cài **mới** gói `app.melovault.player.open` lên Sony nếu Sony chưa có? Hay chỉ QA trên Shanling?
4. Câu D7 và nhãn link D9 có cần đổi chữ không?

Đã dừng. Mở phiên mới, chọn model thực thi tiết kiệm hơn theo lựa chọn của chủ dự án.
