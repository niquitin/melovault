# Nghiệm thu sơ bộ — Phát hành bản beta cho bạn bè

## Vòng 1 · 07/11/2026 · Claude Code, Opus 5.5, phiên mới (không code)

**Kết luận: SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN** trong phạm vi code S1–S4, gate APK, cài Sony và Q1–Q3, Q8. **Q4–Q7 vẫn BLOCKED** (bản `.open` trên Sony chưa đăng nhập; chủ dự án chưa báo đã đăng nhập). Đây không phải PASS chính thức. Trạng thái nghiệm thu: **CHỜ CHỦ DỰ ÁN NGHIỆM THU**.

| Mục | Giá trị |
|---|---|
| Nhánh / worktree | `dot/2026-11-07-phat-hanh-beta` / `D:\Bots\_worktrees\MeloVault-phat-hanh-beta` |
| Baseline / HEAD lúc kiểm | `71189c9` / `a5935ae` (cây sạch, `git diff --check` sạch) |
| Source APK | `a0694c05b8df22ef90a01cdd52738528747341b7`; `a5935ae` chỉ đổi tài liệu (đã xem `git diff --stat`) |
| APK | `app.melovault.player.open` vc122 `0.6.97-open`, 31.051.460 B, SHA-256 `54AB04C8A0F4418B379EA2DA19FFE281283D84E4A46B5DE90BE7DD8FDFC42CCF` |
| Tài liệu đã đọc | CLAUDE.md, PROMPT_NGHIEM_THU_SO_BO.md, kế hoạch, báo cáo, bàn giao, apk-test, ảnh tổng hợp `r2/00_tong_hop.png` |

### Tự kiểm lại trong phiên này

| Kiểm | Cách | Kết quả |
|---|---|---|
| Diff code | `git diff 71189c9..HEAD` toàn bộ file Kotlin/Gradle/XML/test (trừ toàn văn giấy phép) | Đúng kế hoạch S1–S4; chỉ đổi khi `BuildConfig.MELO_OPEN_BUILD`; bản riêng giữ nguyên chữ cũ |
| `MeloPublicBuildPolicy` và nơi gắn | Đọc mã + grep | Nhãn chữ hoa (`eyebrow`), dòng phụ, câu đăng ký, ẩn QR (`TelegramLoginPanel.render` là nơi duy nhất bật nút QR), dòng không chính thức + liên kết trên màn đăng nhập và Giới thiệu, chặn 7 lần chạm. Công cụ nhà phát triển ẩn qua `devUnlocked` ở cả `onCreate` và `render`; các trang `DEV_TOOLS`, `DEVICES`, `ADVANCED` (chứa "Sao chép số đo", "Giả lập thiết bị") đều `needsDevTools` nên rơi về trang chính. Không còn chuỗi "Preview" hiển thị ngoài hằng số bản riêng |
| `resValue app_name` | `build.gradle:186`, `aapt2 dump badging` | Open = `MeloVault`; `main/res/values/strings.xml` vẫn `MeloVault` (Personal không đổi); không còn `shareable/res/values/strings.xml` |
| Sao lưu Shareable | Đọc XML + `aapt2 dump xmltree` trong APK | Manifest trỏ `@xml/backup_rules`, `@xml/data_extraction_rules`; APK chứa đúng bản Shareable (8 mục loại trừ, cả cloud-backup và device-transfer; `path="."` hợp lệ theo tài liệu Android). Personal không đổi |
| Giấy phép so với cây phụ thuộc | Đối chiếu `deps_shareableReleaseRuntimeClasspath.txt` (artifact) với `MeloAboutPolicy.LICENSES` | Mọi nhóm ngoài AndroidX đều có dòng: Guava (+failureaccess, listenablefuture), OkHttp/Okio, Coil, Kotlin stdlib, Coroutines, JetBrains annotations, JSpecify, Error Prone, Material, Media3. Tất cả Apache 2.0, có toàn văn; TDLib có toàn văn Boost; ALAC có BSD. Không có jar/aar cục bộ trong `build.gradle` |
| Gate APK (file Tiny qua `T:`) | `sha256sum`, `aapt2` 36.0.0, `apksigner` trên Ni | SHA-256 và số byte khớp; package/vc/version/nhãn đúng; **không** `application-debuggable`; Verifies, v2, 1 signer, cert `eecd3df7…fce8` |
| Hash Telegram | Đọc `r2/verify-open-hash.log`, `build-open-release.log` (không chạy lại vì cần cấu hình bí mật) | `telegram.hash.found=false`, `open.hash.found=true`, `open.config.valid=true`, `distinct=true`, BUILD SUCCESSFUL |
| Cài Sony không đụng bản chính | Bằng chứng r2 (`main-unchanged.txt`, `pre/post-*.txt`, `installed-match.txt`) + đọc lại máy lúc kiểm (chỉ `dumpsys`, không bật màn) | `QV725WEQ54` XQ-AS52 Sony `device`; `app.melovault.player` vc122 `0.6.97-beta`, `lastUpdateTime` 14:11:34 giữ nguyên; `.open` vc122 `0.6.97-open` 15:37:52; `Display State=OFF` |
| Q1–Q3 | Xem ảnh tổng hợp r2 | Khớp báo cáo: nhãn MeloVault, "KHÔNG CHÍNH THỨC", không QR, dòng + liên kết, không mục công cụ, Giới thiệu `0.6.97-open (vc122)`, giấy phép đủ và cuộn được |

**Sony:** chỉ đọc qua `adb shell dumpsys`/`getprop`; KHÔNG THAO TÁC màn hình, không cài, không bật màn. `SONY_SCREEN_END`: `Display State=OFF` (đọc lúc kiểm). Không chạy lại Gradle: source không đổi sau `a0694c0`, bằng chứng gate đủ và nhất quán.

### Ghi nhận nhỏ, không chặn (chủ dự án quyết có sửa không)

| # | Ghi nhận | Vị trí |
|---|---|---|
| G1 | Prefs `melovault_local_prefs` (thư mục nhạc trên máy gần nhất) không bị loại khỏi sao lưu; không chứa định danh Telegram, mức nhạy cảm thấp | `main/.../LocalMediaRepository.kt:74`; `shareable/res/xml/*_rules.xml` |
| G2 | Sao lưu mới kiểm cú pháp/đóng gói; chưa thử `bmgr` hay chuyển máy | Báo cáo S4 |
| G3 | Dòng giấy phép "JetBrains Annotations, JSpecify, Error Prone" chỉ trỏ một liên kết (jspecify) | `MeloAboutPolicy.kt` |
| G4 | Toàn văn Apache xuống dòng cứng 80 cột rồi tự xuống dòng lần nữa, đọc hơi xấu (thực thi đã ghi "chấp nhận") | `third_party_notices.txt`, ảnh ô 6 |
| G5 | Dòng phụ "Nhạc từ Telegram của bạn · Ứng dụng không chính thức" bị ghi đè ngay ("Telegram · Chờ kết nối"); thông báo không chính thức thực tế nằm ở nhãn chữ hoa + màn đăng nhập + Giới thiệu. Đủ cho mục tiêu, chỉ ghi để chủ dự án biết | `TelegramPreviewActivity.kt:3227` |
| G6 | Ngăn kéo Sony có hai biểu tượng "MeloVault" (bản open và một gói cũ); bạn bè chỉ có một | Ảnh ô 1 |
| G7 | Quyết định còn treo từ bàn giao: hai nút mã API trên màn đăng nhập, nhãn "Thử nghiệm" ở Đồng bộ, rủi ro tin tài trợ Telegram | Bàn giao |

Ghi chú quy trình: để tìm thư mục SDK, phiên này đã grep riêng dòng `sdk.dir` trong `local.properties` (chỉ in đường dẫn SDK, không đọc khóa nào khác).

### Chưa kiểm
Q4–Q7 (phát, bộ lọc/ngang, widget, EQ trên bản `.open`), âm thanh thật (**CHƯA XÁC NHẬN ÂM THANH THỰC**), cài mới từ đầu, máy khác Sony, chạm liên kết riêng tư, tải APK từ Drive bằng trình duyệt, sao lưu thật.

### Chủ dự án tự thử (3–6 bước)
1. Sony: mở **MeloVault** (bản open), đăng nhập Telegram của bạn; thấy nhãn "KHÔNG CHÍNH THỨC", không có nút QR.
2. Thư viện: phát một bài, **nghe có tiếng**, Tạm dừng, Tiếp (Q4).
3. Lọc bằng chip rồi chạm lại để bỏ lọc; xoay ngang màn Đang phát (Q5).
4. Thêm widget của bản open ra Home, bấm Phát/Tạm dừng/Tiếp/Trước (Q6); bật rồi tắt EQ (Q7).
5. Cài đặt → Giới thiệu: dòng không chính thức, chạm "Chính sách riêng tư trên trang web" mở đúng trang.
6. Nếu được: nhờ một người bạn tải từ Drive và cài trên máy khác.

Phản hồi: `ĐẠT 2026-11-07-phat-hanh-beta — a0694c0 / 54AB04C8…2CCF` hoặc `Yêu cầu sửa 2026-11-07-phat-hanh-beta: <lỗi>`.
