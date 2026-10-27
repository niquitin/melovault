Thực thi: Codex gpt-6-astra medium, phiên 2
Thực thi: Codex gpt-6-astra medium, phiên 1

| Nội dung | Kết quả phiên 1 trên Ni |
|---|---|
| Phạm vi | E104-APK-CONG-KHAI, S1–S9; chưa chạy phiên 2 |
| Nhánh | `dot/E104-APK-CONG-KHAI` |
| Baseline / plan | `a638099` / `1d278c3f50bb1145464ab3d6be7fc35558ec6f64` |
| Commit mã nguồn | `03e9dc9eb8f11d83ee10d4d3254f9f5b41601a14` |
| Test mới | 34 thành công, 0 bỏ qua, 0 thất bại/lỗi |
| Test toàn bộ, mỗi biến thể | 2.128 định nghĩa; 2.107 thành công; 21 bỏ qua; 0 thất bại; 0 lỗi |
| Build / metadata APK | Bốn APK thường/open Debug/Release build thành công; package/vc/vname đúng kế hoạch |
| Personal / cờ xung đột | Personal Debug build thành công; open + sync QA bị chặn đúng thông báo |
| Quét hash trên Ni | Cả open Debug và Release: `SKIPPED_NO_LOCAL_HASH`; chưa chứng minh P3 |
| Thiết bị / Tiny | Chưa dùng ADB, chưa bật Shanling, chưa truy cập Tiny/Desktop Commander |
| Nghiệm thu | Chưa nghiệm thu; chờ phiên 2 và Opus |

## Phiên 1 — mã và phạm vi

S1: đã đọc CLAUDE.md (mục 0 trước), AGENTS.md, đầu HANDOFF, quy trình ba vai trò, PROMPT_SONNET, PROMPT_CODEX, vòng đời màn hình và kế hoạch E104.
Đã kiểm `git status --short`, branch, HEAD, `git log -5 --oneline`, `git diff --check` và tiến trình đầu phiên: đúng nhánh/plan commit, cây sạch, không phát hiện sửa mã đồng thời trong repo.
Không tạo/đổi nhánh. Baseline test chạy một lần trước S2; số liệu lấy từ XML, không coi task up-to-date là chạy lại mọi test.

| Bước / commit | File thay đổi | Nội dung |
|---|---|---|
| S2 `faeaeb6` | `TelegramApiCredentialPolicy.kt`, `TelegramApiCredentialPolicyTest.kt` | Kiểm định/chuẩn hóa ID và hash; chọn nguồn; che mã; lỗi tiếng Việt; 34 test |
| S3 `223a076` | `TelegramApiCredentialStore.kt` | AES-256-GCM, AndroidKeyStore, file riêng trong noBackup, ghi `.part` rồi rename |
| S4 `bc11fee` | `TelegramApiSetupPanel.kt` | Views nhập mã, hash dạng password, không lưu view state/autofill, validation theo ô, mở my.telegram.org |
| S5 `d09be83` | `TelegramPreviewActivity.kt` | Ưu tiên mã nhúng; đọc/lưu nền; gửi credentials đã resolve; thông báo lỗi; nhập lại mã |
| S6 `3c1b82f` | `app/build.gradle` | Cờ open, package riêng, vc120, kiểm hash APK, chặn kết hợp sync QA |
| Sửa biên dịch `0b54a6a` | `TelegramApiSetupPanel.kt` | Chỉ rõ receiver của trường panel để tránh trùng thuộc tính Android View |
| Hoàn thiện `03e9dc9` | `TelegramPreviewActivity.kt` | Tuần tự hóa I/O qua các lần tạo Activity; bỏ qua callback sau yêu cầu khởi động lại |

Các file Kotlin production ở `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/`.
File test mới ở `app/src/test/java/com/bsdung/musicassistantcar/data/telegram/`.
Ngoài sáu file mục 6 của kế hoạch, chỉ thêm báo cáo này. Các commit dùng Git identity theo lệnh, không đổi cấu hình Git toàn máy.

`tdlibDatabaseKey()` đã đối chiếu với plan commit, nội dung giữ nguyên. Không sửa Owner, TDLib directories, personal flavor, DB/schema, songKey, backend, Media3/queue hoặc workflow.
Không đọc/in/copy local.properties, secrets, keystore hay dữ liệu phiên Telegram qua công cụ; Gradle dùng cơ chế cấu hình sẵn có khi build.

### Quyết định 6.5-7: dùng phương án dự phòng

Đã dùng **“đóng hẳn ứng dụng rồi mở lại”** sau khi xóa mã API đã lưu, không khởi tạo lại client trong cùng process.
Lý do: `detachUi` không bảo đảm client đã đóng khi playback service còn giữ owner; API singleton hiện có không cung cấp xác nhận đóng client riêng cho UI để chờ an toàn.
Chỉ khi chưa đăng nhập, nút nhập lại mới khả dụng; có kiểm tra trạng thái chờ getMe và account của Owner.
Xóa đúng file credential mới và `.part`, tách callback UI, rồi hiện câu dự phòng theo kế hoạch. Cờ ở mức process ngăn Activity recreation tái sử dụng client cũ với mã mới.
Không gọi logOut, không xóa khóa DB, thư mục TDLib, cache hay dữ liệu thư viện. Không sửa Owner/service để mở rộng kiến trúc.

## Kiểm thử và build Ni

| Lượt | Defined | Thành công | Skipped | Failed | Errors |
|---|---:|---:|---:|---:|---:|
| Baseline Debug | 2.094 | 2.073 | 21 | 0 | 0 |
| Baseline Release | 2.094 | 2.073 | 21 | 0 | 0 |
| S2 test policy Debug | 34 | 34 | 0 | 0 | 0 |
| Cuối phiên Debug | 2.128 | 2.107 | 21 | 0 | 0 |
| Cuối phiên Release | 2.128 | 2.107 | 21 | 0 | 0 |

So baseline: thêm 34 test mỗi biến thể; skipped không tăng. Có test riêng cho scrub chuỗi rỗng và blank, tràn Int, hash sai độ dài/hex, ưu tiên nguồn, mask và ba mã lỗi API.
Số liệu cuối lấy từ `app/build/test-results/testShareable{Debug,Release}UnitTest/TEST-*.xml`, lưu bản tổng hợp private tại `05-final-test-counts.json`.

| Lệnh Gradle (đều `--console=plain`, redirect toàn bộ ra file) | Tổng kết / log |
|---|---|
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` trước S2 | BUILD SUCCESSFUL, 5s; `01-baseline-tests.log` |
| `:app:testShareableDebugUnitTest --tests '*TelegramApiCredentialPolicyTest*'` | BUILD SUCCESSFUL, 17s; `02-policy-tests.log` |
| Test tích hợp lượt đầu | BUILD FAILED do receiver Kotlin panel; đã sửa; `03-full-tests.log` |
| Test tích hợp sau sửa | BUILD SUCCESSFUL, 3m23s; `04-full-tests.log` |
| Test toàn bộ trên commit mã cuối | BUILD SUCCESSFUL, 2m49s; `05-final-tests.log` |
| `:app:assembleShareableDebug :app:assembleShareableRelease :app:reportTelegramConfig` | BUILD SUCCESSFUL, 1m35s; config=false; `06-normal-build.log` |
| `-PmeloOpen=true :app:assembleShareableDebug :app:assembleShareableRelease :app:reportOpenBuild :app:reportTelegramConfig` | BUILD SUCCESSFUL, 1m4s; melo.open=true, config=false; `07-open-build.log` |
| `:app:assemblePersonalDebug` | BUILD SUCCESSFUL, 49s; `08-personal-build.log` |
| `-PmeloOpen=true -PmeloSyncQa=true :app:reportOpenBuild` | BUILD FAILED, 1s, đúng thông báo chặn kết hợp cờ; `09-conflict-flags.log` |
| `-PmeloOpen=true -PopenApk=<open Debug> :app:verifyOpenApkHasNoTelegramHash` | BUILD SUCCESSFUL, 1s; `SKIPPED_NO_LOCAL_HASH`; `10-hash-debug.log` |
| Tương tự với `<open Release>` | BUILD SUCCESSFUL, 1s; `SKIPPED_NO_LOCAL_HASH`; `10-hash-release.log` |

Đã dùng `aapt2 dump badging` và `apksigner verify --print-certs` từ Android build-tools 36.0.0 cho cả bốn APK; xác minh chữ ký thành công, không đọc keystore.
Lượt test tích hợp đầu có lỗi biên dịch đã sửa, không phải lỗi test. Có lỗi cú pháp lọc log PowerShell khi kiểm cờ xung đột; đã đọc lại đúng dòng kết quả của cùng log, không chạy lại hay che lỗi Gradle.
Personal Debug chỉ là smoke build, không sửa flavor và không đề nghị cài/bàn giao bản Stable. File giữ riêng tại `personal/app-personal-debug.apk` trong artifact root.
Task kiểm hash chỉ ghi trạng thái, không in ID/hash/tên entry. Trạng thái SKIPPED không được hiểu là đã quét và không thấy hash; phiên 2 phải có đối chứng P3 trên Tiny.

## APK QA cần Opus nghiệm thu bàn giao

**QA CANDIDATE — CHƯA NGHIỆM THU — KHÔNG ĐĂNG CÔNG KHAI.**
APK Ni phục vụ kiểm tra build; chưa dùng thay APK Tiny có signer/config đúng để cài lên thiết bị.
Các APK cũ trước build đã lưu riêng ở `preexisting-apks/`; không đưa vào danh sách candidate E104.

Commit nguồn của cả bốn APK: `03e9dc9eb8f11d83ee10d4d3254f9f5b41601a14`.
Cert SHA-256 Ni (cả bốn): `a9b8cb6b5b0bc0400955b24dbc85801f5e8855356287caca2dcf3bb4e13db280`.
Đây là signer Ni, chưa đối chiếu chứng chỉ app đang cài trên thiết bị; không dùng APK Ni để cài thay Beta Tiny.

| APK | Package | vc / versionName | Bytes | Telegram config embedded hợp lệ |
|---|---|---|---:|---|
| Thường Debug | `app.melovault.player` | 120 / `0.6.95-beta` | 33.462.212 | false |
| Thường Release | `app.melovault.player` | 120 / `0.6.95-beta` | 30.703.982 | false |
| Open Debug | `app.melovault.player.open` | 120 / `0.6.95-open` | 33.462.216 | false |
| Open Release | `app.melovault.player.open` | 120 / `0.6.95-open` | 30.703.990 | false |

| APK | SHA-256 |
|---|---|
| Thường Debug | `fbc0c3eec5e224b84108a7eda1c5130fe755430143cf20bd878d2fea2ea1b2b7` |
| Thường Release | `dd8ed57ea2c541fae831469790b543638869d6d52c7decd4a6d6e320016c1fe0` |
| Open Debug | `83131276bf863b632592ca195c358f448d7584131ae3bdcb23a6ee316e112b1a` |
| Open Release | `349b63eb7e5101e1b627a7371c6d699e9be318d51780c3c9cbbe5bfa216b5cdc` |

Đường dẫn Ni đã xác minh tồn tại:

- Thường Debug: `D:\Bots\MeloVault-E91-Shareable\docs\plans\artifacts\E104-APK-CONG-KHAI\normal\app-shareable-debug.apk`
- Thường Release: `D:\Bots\MeloVault-E91-Shareable\docs\plans\artifacts\E104-APK-CONG-KHAI\normal\app-shareable-release.apk`
- Open Debug: `D:\Bots\MeloVault-E91-Shareable\docs\plans\artifacts\E104-APK-CONG-KHAI\open\app-shareable-debug.apk`
- Open Release: `D:\Bots\MeloVault-E91-Shareable\docs\plans\artifacts\E104-APK-CONG-KHAI\open\app-shareable-release.apk`

Bản thường Ni thiếu config nên chưa dùng kiểm đăng nhập. Bản open cố ý không nhúng config; cần nhập mã người dùng và kiểm UI trong phiên 2. Chưa có kết quả install-r hợp lệ trên thiết bị, chưa cài bất kỳ APK nào.

## Chưa kiểm chứng, rủi ro và bàn giao phiên 2

| Việc | Trạng thái |
|---|---|
| P3 kiểm hash thật trên Tiny + đối chứng APK thường | Chưa thực hiện; Ni không thay thế được chứng cứ này |
| Q1–Q4 UI/validation/lưu Keystore/khởi động lại | Chưa kiểm thử trên Android |
| Q5 bản riêng giữ phiên, thư viện, phát nhạc | Chưa kiểm chứng trên thiết bị |
| Q6 logcat hash giả, crash/ANR | Chưa chạy ADB/logcat |
| API thật, đăng nhập thật, âm thanh nghe được | Chưa xác nhận; không nhập tài khoản hay mã thật |
| Ảnh/video và `00_tong_hop.png` | Chưa tạo vì phiên 1 không thao tác thiết bị |
| `SHANLING_SCREEN_END` | `UNKNOWN` — không dùng ADB, không wake/sleep, không đọc display |
| `SONY_SCREEN_END` | `UNKNOWN` — không truy cập Tiny hay dùng ADB |

Rủi ro cần QA: Keystore trên thiết bị thật, bàn phím/scroll ở panel, vòng đời process khi nhập lại, hai sandbox cài song song và hồi quy đăng nhập bản riêng. JVM/build thành công không chứng minh các hành vi này.
APK vẫn dùng debug signer theo kế hoạch, chưa đủ điều kiện phát hành công khai.
Phiên 2 nhận đúng commit trên nhánh này bằng Git bundle, đối chiếu SHA-256 hai đầu; Tiny chỉ qua Desktop Commander trong clone cô lập mới. Chưa tạo/chuyển bundle hoặc sửa bất kỳ repo Tiny nào trong phiên 1.
Phiên 2 thực hiện S10–S16, kiểm signer/package/version/config trước cài và vòng đời màn hình theo workflow. Chờ Opus nghiệm thu; chưa merge, push, upload hoặc công bố APK.

Artifact private của phiên: `D:\Bots\MeloVault-E91-Shareable\docs\plans\artifacts\E104-APK-CONG-KHAI\` (Git ignored).
S9: đã rà diff đúng phạm vi, `git diff 1d278c3 --check` không lỗi; báo cáo được commit riêng sau các commit mã. Không ghi kết luận nghiệm thu thay Opus.

## Phiên 2 — BLOCKED trước S10–S14 (26/10/2026)

| Nội dung | Kết quả |
|---|---|
| Git đầu phiên | `dot/E104-APK-CONG-KHAI`, HEAD `a10e8162b9f671d9b631799a659fe2b26c2a10e2`, cây sạch, diff check không lỗi |
| Baseline / plan | `a638099` và `1d278c3` đều là tổ tiên HEAD; nguồn code vẫn `03e9dc9` |
| Hoạt động đồng thời | Commit cuối 21:00:19 +07, cách kiểm tra khoảng 20 phút; hai Java process là Gradle/Kotlin daemon, không phát hiện thay đổi Git mới |
| Tiny | Desktop Commander xác nhận online; `hostname` trả `DESKTOP-HNP4GVR` |
| Shanling | ADB `30313860180939` trạng thái device; model `Shanling M3 Ultra`, manufacturer `Shanling` |
| Package open trước cài | `pm list packages app.melovault.player.open` trả rỗng |
| S10 bundle / clone | Chưa thực hiện: dừng khi phát hiện thiếu cách cấp config hợp lệ cho S11 |
| S11 build Tiny | BLOCKED; chưa chạy Gradle, chưa kiểm boolean config trên Tiny |
| S12 / P3 | Chưa chạy open Debug/Release hoặc đối chứng thường; chưa có kết quả found=true/false |
| S13–S14 | Chưa tạo/chuyển APK, chưa kiểm signer/package/vc của APK Tiny, chưa cài |
| S15–S16 | Ghi blocker vào báo cáo này, chỉ commit báo cáo; không sửa code |

### Điểm dừng: thiếu phương thức cấp cấu hình trong clone mới

`app/build.gradle` dòng 6–9, 24–25 chỉ nạp Telegram config từ `local.properties` ở gốc project.
Đã đọc script lịch sử `C:\Bots\_qa_temp\e103c_build.ps1` qua Desktop Commander để xác định cách build cũ; không chạy script.
Script cũ lấy `local.properties` từ repo gốc Tiny và `Copy-Item` vào clone cô lập.
Chỉ dẫn phiên này cấm đọc/copy `local.properties` và cấm chạm repo gốc Tiny; không áp dụng lại thao tác cũ.
S11 yêu cầu config thật trong clone MỚI, nhưng kế hoạch chưa chỉ rõ cách cấp config tuân các giới hạn trên.
Đây là thiếu hướng dẫn chuẩn bị môi trường, **không phải kết luận Tiny thiếu config hay lỗi code**.
Theo yêu cầu “Plan sai/thiếu → DỪNG”, không tự sửa build config, tạo liên kết tới secret, sao chép secret hoặc dùng APK Ni thay thế.
Cần chủ dự án/Opus làm rõ phương thức cấp config được phép cho clone mới trước khi tiếp tục; không yêu cầu gửi giá trị bí mật vào hội thoại.
Chưa đọc/in/copy file bí mật, chưa ghi lên Tiny, chưa đụng các repo cấm.

### QA và màn hình

| Bước | Thực tế | Bằng chứng UI |
|---|---|---|
| Q1 | Chưa cài/mở app open | Chưa có |
| Q2 | Chưa thử validation | Chưa có |
| Q3 | Chưa nhập mã giả; không nhập số điện thoại/OTP/mã thật | Chưa có |
| Q4 | Chưa thử lưu bền hoặc câu dự phòng rồi force-stop/mở lại | Chưa có |
| Q5 | Chưa cài bản thường, chưa mở thư viện/phát nhạc | Chưa có |
| Q6 | Chưa thu logcat; không có kết luận hash giả/crash/ANR | Chưa có |

Không wake màn. Cleanup trong `finally` đã gửi `keyevent 223`; tại 21:22:37 +07, `dumpsys display` ghi `mGlobalDisplayState=OFF` và màn tích hợp `state OFF`, `mWakefulness=Asleep`.
`SHANLING_SCREEN_END=OFF` (Android này không in nguyên nhãn `Display State=OFF`; ghi đúng nhãn đầu ra thực tế ở trên).
`SONY_SCREEN_END=UNKNOWN` — không dùng ADB Sony, không wake hay QA Sony (không bắt buộc E104).
Chưa có ảnh Q1–Q5 nên chưa tạo `00_tong_hop.png`; không dùng ảnh cũ thay bằng chứng phiên này.

### APK QA cần Opus nghiệm thu bàn giao — phiên 2

| Candidate yêu cầu | Trạng thái bàn giao |
|---|---|
| Thường Debug Tiny, `app.melovault.player`, vc120 / `0.6.95-beta` | BLOCKED — chưa build, chưa có đường dẫn/SHA-256/bytes/cert/config được kiểm |
| Open Debug + Release Tiny, `app.melovault.player.open`, vc120 / `0.6.95-open` | BLOCKED — chưa build, chưa có đường dẫn/SHA-256/bytes/cert/P3 được kiểm |

APK Ni và số liệu test phiên 1 ở trên giữ nguyên; không phải APK Tiny được phép cài/bàn giao.
Không chạy lại test/build Ni vì chưa thay code, nhiệm vụ phiên 2 dừng ở chuẩn bị môi trường.
Chưa xác nhận đăng nhập thật, Keystore/UI/TDLib trên máy, hồi quy bản riêng hoặc âm thanh thật.
Không upload, công bố APK, merge, push hoặc viết file nghiệm thu. Dừng chờ Opus xử lý điểm thiếu của kế hoạch.

## Phiên 2 — lần 2 (27/10/2026, S10–S16)

| Nội dung | Kết quả thực thi, chưa phải kết luận nghiệm thu |
|---|---|
| Git | Đầu phiên và sau gián đoạn: đúng nhánh, HEAD `4f8b575995c38c6b99e466f13777cf64780641b2`, cây sạch; nguồn `03e9dc9`; baseline/plan đúng yêu cầu |
| Phạm vi | Chỉ build/QA/báo cáo; không sửa production, plan, workflow hoặc hồ sơ nghiệm thu |
| S10 | Bundle `D:\Bots\_transfer\E104-4f8b575-20261027-0125.bundle`, 12.873.045 bytes; SHA-256 hai đầu `15E67435AC29632558150B7872264E3126890217292C047F3FD24A7DA7B0A529`; bundle verify thành công |
| Tiny | Desktop Commander, clone mới `C:\Bots\MeloVault-E104-IsolatedQA-20261027-0125`, đúng HEAD; bundle tại `C:\Bots\_qa_temp\E104-4f8b575-20261027-0125.bundle` |
| S11 config | Chỉ Copy-Item từ `C:\Bots\MeloVault-E99-IsolatedQA\local.properties` sang clone mới; không mở/in/hash nội dung; check-ignore đúng, Git sạch; reportTelegramConfig không cờ open: true |
| S11 build | Thường Debug thành công (21s lượt xác minh); open Debug+Release thành công (5m35s); open=true, embedded config=false đúng thiết kế |
| S12 | Open Debug found=false/exit 0; open Release found=false/exit 0; thường Debug found=true/exit 1 (BUILD FAILED đúng kỳ vọng đối chứng) |
| Test Ni | Không chạy lại vì mã không đổi; phiên 1: mỗi biến thể 2.107 thành công, 21 bỏ qua, 0 thất bại/lỗi; 34 test mới thành công |
| S13–S14 | Ba APK đúng package/vc/vname/cert; SHA-256 bản Ni khớp Tiny; cài open Debug mới và cập nhật thường Debug bằng install -r đều Success |

Artifact phiên này (A): `D:\Bots\MeloVault-E91-Shareable\docs\plans\artifacts\E104-APK-CONG-KHAI\session2-run2-20261027`.
Artifact Tiny (T): `C:\Bots\_qa_temp\E104-20261027-0125`; bản sao log/metadata/exit tại `A\tiny-logs`.
Build lần đầu bị wrapper PowerShell coi cảnh báo SDK XML là lỗi; giữ `normal-build.log`, error log và `T\unverified-first-normal-debug.apk`, không dùng làm candidate. Script tiếp tục dùng chuyển hướng stderr ra log, mã thoát cuối 0; không sửa Gradle/source. Không ghi đè artifact cũ.
Bundle được Tiny tải từ máy Ni qua HTTP tạm chỉ phục vụ thư mục bundle; server đã dừng sau chuyển. Không chuyển cấu hình/secret ra khỏi Tiny, không ghi repo Tiny qua T:.

### QA Shanling và lỗi phát hiện

Thiết bị `30313860180939`, model `Shanling M3 Ultra`, manufacturer `Shanling`; ADB device. Trước cài open, danh sách package open rỗng; bản thường vc119, cert khớp Tiny, MediaSession state=0. Sau gián đoạn hạn mức, kiểm lại Git/ADB/version/playback trước Q5. Không nhập số điện thoại, OTP hoặc mã API thật.
Tên ảnh trong bảng đều tương đối với A; ảnh/log có metadata Telegram giữ private, không commit/upload.

| Bước | Hành động / thực tế | Kết quả / bằng chứng |
|---|---|---|
| Q1 | Mở open mới cài: có tiêu đề, hai ô và Lưu; chưa có ô điện thoại | Khớp; `02-Q1-open-installed.png` |
| Q2 | ID không nhận abc nên để trống; hash 12345; Lưu báo lỗi dưới cả hai ô | Validation khớp; `03-Q2-before.png`, `04-Q2-validation.png`; có lỗi che hash bên dưới |
| Q3 | ID 1 + hash 32 số 0, Lưu → bước số điện thoại; không gửi số | Khớp; `05-Q3-fake-before.png`, `06-Q3-phone.png` |
| Q4 | Force-stop/mở lại → thẳng bước điện thoại; Nhập lại mã → câu dự phòng; đóng hẳn/mở lại → panel trống | Khớp phương án dự phòng 6.5-7; `07-Q4-persist.png`, `08-Q4-reenter.png`, `09-Q4-empty-after-restart.png` |
| Q5 | Cập nhật thường vc120, vào thư viện không hỏi API; bấm phát Nghe tiếp; MediaSession state=3, speed=1; position 3.703.855 → 3.712.838 ms | Khớp luồng đã quan sát; `10-Q5-library.png`, `11-Q5-playing.png`, `media-session-1-private.log`, `media-session-2-private.log` |
| Q6 | logcat -d sau Q1–Q5: 7.543 dòng, mốc đầu 26/10 14:53:49; 0 dòng hash giả, 0 api_hash, 0 FATAL của hai package, 0 ANR in package | `Q6-summary.json`; chỉ lưu số đếm, không lưu/in raw credentials; đây là phạm vi buffer hiện có |

**Lỗi UI cần Opus xem xét (FAIL yêu cầu 6.4): ô API hash hiển thị rõ ký tự, không che dạng password.** Ảnh 03/04 thấy `12345`, ảnh 05 thấy chuỗi mã giả sau khi nhập; không phải chỉ ký tự cuối hiện tạm. Chưa sửa code, chưa kết luận nghiệm thu. Không có mã thật trong các ảnh này.
Ảnh `01-Q1-open.png` chụp khi lệnh install còn đang chờ hoàn tất, còn màn bản thường vc119; loại khỏi bằng chứng Q1. Chỉ dùng ảnh 02 sau khi install báo Success.
Q5 ban đầu hiện 200 bài khi vừa nạp, sau đó 29.857 bài; không coi số đếm khởi động là mất dữ liệu. Sau kiểm phát đã bấm tạm dừng (state=2, position=3.716.421); ảnh `12-Q5-paused.png`. Điểm Nghe tiếp đã thay đổi do QA.
Ảnh tổng hợp 8 ô: `docs/plans/artifacts/E104-APK-CONG-KHAI/00_tong_hop.png`, 1280×1140, ghép từ ảnh thật. Các lần wake đều cleanup finally keyevent 223, ghi `screen-lifecycle.log`.
`SHANLING_SCREEN_END=OFF` xác minh 27/10/2026 09:12:15 +07: `mGlobalDisplayState=OFF` (nhãn thực của Android này). `SONY_SCREEN_END=UNKNOWN`: không dùng ADB/wake/QA Sony, không bắt buộc E104.

### APK QA cần Opus nghiệm thu bàn giao — lần 2

**QA CANDIDATE — CHƯA NGHIỆM THU — KHÔNG ĐĂNG CÔNG KHAI.** Cả ba build tại HEAD `4f8b575`, nguồn `03e9dc9`, vc120; signer SHA-256 `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` khớp bản vc119 Shanling. Không dùng APK Ni signer a9b8… để cài.
Mỗi tên bên dưới tồn tại tại cả `T\<tên APK>` và `A\<tên APK>` đã xác minh SHA-256/bytes; T/A là đường dẫn đầy đủ nêu trên.

| Tên APK | Package / versionName | Bytes | SHA-256 | Config nhúng / kiểm hash / QA |
|---|---|---:|---|---|
| `E104-4f8b575-normal-debug-vc120.apk` | `app.melovault.player` / `0.6.95-beta` | 33462212 | `1092EE34DAEDD19D3DEC3BA1041EE8B58B87CDAB9725410EBBDF36B1D76F563A` | true / found=true đối chứng / Q5 |
| `E104-4f8b575-open-debug-vc120.apk` | `app.melovault.player.open` / `0.6.95-open` | 33462216 | `406BCFB46D3717A353E17C3BD65D0569A3EBCDA43AA5C5236489997500ECD535` | false có chủ đích / found=false / Q1–Q4, lỗi che hash |
| `E104-4f8b575-open-release-vc120.apk` | `app.melovault.player.open` / `0.6.95-open` | 30703990 | `082C14FBDD89B52D5AADB2D788A691176F07332AFE0F2735CC98EE1B7FD970DE` | false có chủ đích / found=false / chưa cài Release |

Chưa kiểm: đăng nhập/API thật, lỗi API_ID_INVALID trên máy, âm thanh thực, Release UI, Y700/S25/FYT và Sony; chờ chủ dự án duyệt và Opus nghiệm thu. Không rollback vc119 vì downgrade bị cấm; giữ vc120 và dữ liệu hiện có. Open đã trở về panel trống bằng nút Nhập lại mã + khởi động lại; không xóa TDLib/session/cache.
S15–S16: giữ nguyên phần cũ, chỉ bổ sung mục này; diff check trước commit không lỗi; chỉ commit báo cáo trên dot/E104-APK-CONG-KHAI. Không merge/push/upload/công bố APK, không viết file nghiệm thu; dừng chờ Claude Opus.
