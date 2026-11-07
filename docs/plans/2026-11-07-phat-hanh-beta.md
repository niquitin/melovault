# Kế hoạch — Phát hành bản beta cho bạn bè, có nút tải trên trang giới thiệu

Lập kế hoạch: Claude Code, Opus 5.5, 07/11/2026. **Chưa code.** Phiên thực thi mới làm theo file này, model do chủ dự án chọn.

| Mục | Giá trị |
|---|---|
| TASK_ID | `2026-11-07-phat-hanh-beta` |
| Nhánh / worktree | `dot/2026-11-07-phat-hanh-beta` / `D:\Bots\_worktrees\MeloVault-phat-hanh-beta` |
| Baseline | `a982a9a7cf9a0b9b912cb6ce6d092473d3195e79` (nhánh tích hợp, đã push) |
| Gate baseline | Debug/Release mỗi loại 207 suite, 2.799 test, 21 skipped, 0 lỗi |
| Kế hoạch gốc được kế thừa | `dot/2026-10-31-chuan-bi-cong-khai-open:docs/plans/2026-10-31-chuan-bi-cong-khai-open.md` (đọc mục 2 và các PD; file này thắng khi khác) |

## 1. Quyết định của chủ dự án (07/11/2026, trong chat)

| # | Câu hỏi | Quyết định |
|---|---|---|
| 1 | Widget Đang phát | **Gom sau khi duyệt:** chỉ merge khi chủ dự án nhắn ĐẠT cho `be61487/58BD55F0` |
| 2 | Khóa ký | **Tạm dùng khóa debug của Tiny** (`eecd3df7…fce8`), không tạo khóa phát hành |
| 3 | Sửa trong app | **Làm đủ:** tên, dòng không chính thức, ẩn QR và mục thử nghiệm, màn Giới thiệu & giấy phép, rà sao lưu |
| 4 | Đăng | **Chủ dự án tự bấm đăng:** agent chuẩn bị Release nháp và bản sửa trang; không tự công khai |
| — | Bản phát hành | `app.melovault.player.open`, không chứa mã API riêng. **Tuyệt đối không đăng** `app.melovault.player` vì có mã API riêng |
| — | N1 của `2026-11-07-sua-bo-loc-dang-phat-ngang` | Giữ, không sửa |

**Hệ quả chủ dự án đã được báo:** khi đổi sang khóa thật về sau, bạn bè phải gỡ app và mất dữ liệu trong app. Trang công khai nên ai vào cũng tải được, không chỉ bạn bè.

**Ngoài phạm vi:** khóa phát hành; Google Play; R8; tự cập nhật; đổi package; EQ clipped; hàng Tiếp theo|Lời nhạc; O1 (widget 3×3) và O2 ("Nghe tiếp" hàng đợi 1 bài) trừ khi chủ dự án giao thêm.

## 2. Hiện trạng đã kiểm (07/11/2026)

- `app/build.gradle:187`: flavor `shareable` ký `signingConfigs.debug`, có ghi chú phải thay trước khi phân phối công khai. `:189–191`: vc122, `0.6.97`, hậu tố `-open` khi `meloOpen`.
- `app/src/shareable/res/values/strings.xml:2`: `app_name` = `MeloVault Preview`.
- `TelegramPreviewActivity.kt`: `:3172` dòng phụ "Telegram · Nhạc cá nhân · Preview"; `:7415`, `:7946` thông báo QR nhắc máy Lenovo; `:7955` "bản Preview chưa hỗ trợ". Số dòng có thể lệch sau khi merge widget.
- `MeloUiLabActivity.kt`: mục "Giả lập thiết bị (thử nghiệm)", "Sao chép số đo"; đã ẩn sau 7 lần chạm (`dev_tools_unlocked`).
- Chưa có: `MeloAboutActivity`, `licenses.txt`, `docs/public/`, dòng "không chính thức".
- Tiny: có `telegram.open.*` trong nguồn `local.properties` của clone E99; `C:\Bots\_keys` không tồn tại.
- Sony: `app.melovault.player.open` vc121, cùng khóa debug, nên `install -r` lên vc122 được.
- Trang: nhánh `main` (`4e632ad`), GitHub Pages `https://niquitin.github.io/melovault/`, nút tải đọc `config.js`. Câu hỏi thường gặp dòng 128 ghi EQ chưa có, đã cũ. Mục riêng tư có id `quyen-rieng-tu`.
- Repo chưa có Release nào. Drive từng chặn tải APK nên không dùng làm kênh công khai.

## 3. Các bước

### S0 — Kiểm đầu phiên và cổng Widget

Kiểm Git theo CLAUDE.md mục 1. **Chỉ đi tiếp khi** `docs/plans/2026-11-03-widget-dang-phat-nghiem-thu.md` trên nhánh tích hợp đã ghi ĐẠT và merge xong. Chưa có thì ghi BLOCKED, dừng. Merge nhánh tích hợp vào nhánh này (không rebase).

### S1 — Tên và ký (Gradle)

- `resValue` tên app theo `meloOpen`: bản open **"MeloVault"**, bản riêng giữ "MeloVault Preview". Bỏ `app_name` trùng trong `strings.xml` nếu cần để không lỗi resource.
- Giữ `signingConfigs.debug`. Sửa ghi chú: bản beta bạn bè dùng tạm theo quyết định chủ dự án 07/11/2026; phải thay trước bản ổn định. Không thêm `meloPublicRelease`.
- Không đổi versionCode: **giữ vc122**, tên `0.6.97-open`.

### S2 — Tuân thủ trong app

Chỉ áp dụng khi `BuildConfig.MELO_OPEN_BUILD`, qua hàm thuần `MeloPublicBuildPolicy` có test JVM:
- Dòng phụ thành "Nhạc từ Telegram của bạn · Ứng dụng không chính thức".
- Ẩn nút đăng nhập QR.
- Câu `:7955` thành "Tài khoản mới cần đăng ký bằng ứng dụng Telegram chính thức trước."
- Ẩn "Giả lập thiết bị" và "Sao chép số đo" kể cả khi đã mở khóa dev tools.
- Màn đăng nhập thêm dòng "MeloVault là ứng dụng không chính thức, dùng Telegram API. Không liên kết với Telegram." cùng liên kết `https://niquitin.github.io/melovault/#quyen-rieng-tu`.
- Mục chưa rõ là thử nghiệm hay thật thì không ẩn, ghi vào báo cáo.

### S3 — Giới thiệu & giấy phép

Màn `MeloAboutActivity` (Android Views), mở từ Cài đặt, cả hai bản: phiên bản, dòng không chính thức, liên kết riêng tư, danh sách thư viện và toàn văn giấy phép (`app/src/shareable/assets/licenses.txt`). Danh sách **phải lấy từ** `gradlew :app:dependencies --configuration shareableReleaseRuntimeClasspath` cộng TDLib, không đoán.

### S4 — Sao lưu

Đọc `backup_rules.xml`, `data_extraction_rules.xml`. Có thứ nhạy cảm (khóa, session, mã API, DB thư viện kèm định danh) ngoài `noBackupFilesDir` mà chưa loại trừ thì thêm loại trừ cho Shareable. Không đổi `allowBackup` của Personal. Ghi kết luận vào báo cáo.

### S5 — Gate Ni

```text
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
.\gradlew.bat -PmeloOpen=true :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
.\gradlew.bat :app:assemblePersonalDebug --console=plain
git diff --check
```

Đếm từ XML; tổng phải lớn hơn số trước khi code; 0 failures, 0 errors.

### S6 — Build Tiny và gate APK

Bundle kiểm SHA-256, clone cô lập mới. Cấp config bằng đúng cách đã dùng ở các lần build trước (`-PmeloLocalPropertiesFile`), không đọc, in hay chép giá trị. Build open Release (`-PmeloOpen=true`) và Debug thường (hồi quy).

Open Release phải đạt tất cả, sai một mục là BLOCKED:
- `aapt2 dump badging`: `app.melovault.player.open`, vc122, `0.6.97-open`, nhãn `MeloVault`, **không có** `application-debuggable`.
- `verifyOpenApkHasNoTelegramHash` → `found=false`; `verifyOpenApkHasOpenTelegramHash` đạt.
- `apksigner verify --print-certs`: đúng `eecd3df7…fce8`, scheme hợp lệ.
- Ghi SHA-256, byte, source.

### S7 — QA Sony trên đúng APK Release

Chỉ `adb -s <serial> install -r`. Đăng nhập nếu cần là việc của chủ dự án; agent không nhập số điện thoại hay mã. Chưa đăng nhập thì Q3 trở đi BLOCKED, ghi rõ.

| Q | Thao tác | Kỳ vọng |
|---|---|---|
| Q1 | Launcher | Tên MeloVault |
| Q2 | Màn đăng nhập (nếu thấy được) | Có dòng không chính thức và liên kết; không có QR |
| Q3 | Cài đặt, Giới thiệu & giấy phép | Phiên bản đúng, giấy phép cuộn được; không còn mục thử nghiệm |
| Q4 | Thư viện, phát một bài đã tải, tạm dừng, Tiếp | Phát chạy (đây là lần đầu Release chạy trên máy) |
| Q5 | Bản sửa 07/11: không còn ☰; chạm lại chip bỏ lọc; xoay ngang | Như đã ĐẠT, trong bản gộp |
| Q6 | Widget: thêm ra Home, Phát, Tạm dừng, Tiếp, Trước, chạm thẻ | Như đã ĐẠT, trong bản gộp |
| Q7 | EQ bật, tắt | Chạy; trả về như cũ |
| Q8 | Log crash; `app.melovault.player` vẫn còn và không đổi | 0 FATAL |

**CHƯA XÁC NHẬN ÂM THANH THỰC** trừ khi chủ dự án nghe thử. `finally`: trả xoay tự động, tắt màn, ghi `SONY_SCREEN_END`.

### S8 — Bàn giao

APK và ảnh lên Drive owner-only, nhãn **QA CANDIDATE — CHƯA NGHIỆM THU — CHƯA ĐĂNG**; `-apk-test.md`, `-hinh-nghiem-thu.md`, `-bao-cao.md`, `-ban-giao.md`; prompt sơ bộ. Dừng. Hồ sơ không ghi tên bài, tên kênh.

## 4. Sau khi chủ dự án ĐẠT — chuẩn bị đăng (agent không công khai)

1. Merge `--no-ff` vào nhánh tích hợp, cập nhật HANDOFF, push fast-forward theo chỉ thị 02/11.
2. Tạo **Release nháp**, đánh dấu pre-release, tag `v0.6.97-open-beta` trỏ commit source đã push, đính kèm APK và tệp `.sha256`. Ghi chú nêu rõ: bản thử nghiệm, ứng dụng không chính thức dùng Telegram API, ký khóa tạm nên bản ổn định sau này có thể phải gỡ rồi cài lại.
3. Từ `main`, tạo nhánh `site/beta-0.6.97`, sửa:
   - `config.js`: `url` trỏ `https://github.com/niquitin/melovault/releases/download/v0.6.97-open-beta/<tên APK>`, `versionName`, `versionCode`, `sha256`, `sizeBytes`, `releaseDate`, `notes`.
   - `index.html`: câu hỏi về EQ (đã có EQ, chưa có Drive/OneDrive); thêm widget; dòng không chính thức; lưu ý khóa tạm; cập nhật chính sách riêng tư nếu có lời nhạc LRCLIB.
   - Không đưa link Drive, bí mật hay PII.
   Xem thử bằng trình duyệt nội bộ, kiểm nút tải bật đúng. Push nhánh và mở PR vào `main`, **không merge**.
4. Đưa chủ dự án 2 việc, đúng thứ tự:
   1. Bấm **Publish** Release nháp.
   2. Bấm **Merge** PR trang (làm ngược thì nút tải báo 404 cho tới khi Release lên).
5. Khi chủ dự án báo xong: tải APK từ nút trên trang, so SHA-256, ghi `-nghiem-thu.md`.

## 5. Chủ dự án tự thử (trước ĐẠT)

1. Trên Sony mở **MeloVault** bản open: tên không còn "Preview".
2. Cài đặt, Giới thiệu & giấy phép: đọc được, có dòng không chính thức.
3. Phát một bài, **nghe có tiếng**, tạm dừng, Tiếp.
4. Thêm widget ra Home, bấm thử các nút.
5. Nên thử trên một máy khác hoặc nhờ một người bạn: tải, cài, đăng nhập từ đầu. Sony chưa chứng minh được trải nghiệm máy mới.

## 6. Rủi ro và điểm chưa chốt

| Rủi ro | Xử lý |
|---|---|
| **Điều khoản Telegram về tin tài trợ của kênh:** app hiển thị nội dung kênh phải hỗ trợ tin tài trợ; MeloVault chỉ liệt kê nhạc, không có bảng tin kênh. Chưa chốt có áp dụng không; vi phạm có thể bị cắt API sau 10 ngày cảnh báo | Chủ dự án quyết; mặc định ghi là rủi ro, không làm trong nhiệm vụ này |
| Khóa debug: đổi sau thì bạn bè phải gỡ app; ai có `debug.keystore` của Tiny ký được bản cập nhật | Đã báo chủ dự án, giữ keystore trong Tiny, không chép đi |
| Release chưa từng chạy trên máy; máy khác Sony chưa thử | Q4; bước 5 mục 5 |
| Đổi tên hoặc thêm màn ảnh hưởng bản riêng | Điều kiện sau `MELO_OPEN_BUILD`; Debug thường hồi quy |
| O1, O2, N1 bạn bè sẽ gặp | Ghi trong ghi chú bản tải nếu chủ dự án muốn |

**Rollback:** chưa đăng thì bỏ nháp và đóng PR. Đã đăng thì đưa `config.js` về rỗng để tắt nút và gỡ Release; người đã cài không bị ảnh hưởng.

## 7. Trường hợp phải dừng

BLOCKED nếu: widget chưa ĐẠT; cần đổi package, service, schema, Media3, TDLib; APK open có hash riêng hoặc debuggable; thiếu `telegram.open.*`; Sony unauthorized, sai cert, downgrade; repo hay trang có thay đổi lạ.
