# Báo cáo thực thi — Sửa nút Bộ lọc, chip thể loại và màn Đang phát khi xoay ngang

Thực thi: Claude Code, Sonnet 5.5, phiên GỘP (S0–S9). Báo cáo cập nhật theo từng mốc; mục nào ghi "chưa làm" là chưa có bằng chứng.

| Mục | Giá trị |
|---|---|
| TASK_ID | `2026-11-07-sua-bo-loc-dang-phat-ngang` |
| Nhánh / worktree | `dot/2026-11-07-sua-bo-loc-dang-phat-ngang` / `D:\Bots\_worktrees\MeloVault-sua-bo-loc-dang-phat-ngang` |
| Baseline / commit kế hoạch | `f82185ba2ef17fce97d4680de2cd2dc86b2e1b4a` / `7999a29` |
| versionCode | Không đổi (122) |

## 1. Commit theo bước

| Bước | Mục | Commit | File chính |
|---|---|---|---|
| S0 | Kiểm đầu phiên | — | Cây sạch, HEAD `7999a29`, `git diff f82185b..HEAD -- app/` rỗng, `git diff --check` sạch, không có Gradle/java chạy |
| S1 | D1 bỏ nút ☰ kiểu mặc định | `1efb96b` | `MeloComponents.kt` (`searchBarWithField`), `docs/ui-spec.md` |
| S2 | D3 chạm lại chip để bỏ lọc | `701d834` | `MeloLibraryPolicy.kt` (`toggledGenre`), `MeloLibraryPane.kt` (`renderChips`), `MeloLibraryPolicyTest.kt` |
| S3 | D4 `isBaselineAligned=false` cho hàng EQ/Hẹn giờ | `20b3011` | `MeloStudioDeck.kt` (`topBar`) |
| S4 | Policy "ngang thấp" | `cb13c21` | `MeloNowPlayingPolicy.kt`, `MeloNowPlayingPolicyTest.kt` |
| S5 | D2 dựng dạng "ngang thấp" | `233954f` | `MeloStudioDeck.kt` |

Chi tiết S1: nút chỉ thêm khi `onFilter != null` (cả Teal và mặc định); `MeloLibraryPane.kt:126` không đổi, nên kiểu mặc định và màn Gọn (`searchBar(null, ...)`) không còn nút chết, Teal vẫn mở hộp lọc.

Chi tiết S5 (chỉ `MeloStudioDeck.kt`): field `landscapeCompact` (LANDSCAPE, cao < 500 dp, `fontScale <= 1.3`); `addPlayerBody` tách `addTitleBlock` + `addExtras` (dọc và ngang cao giữ nguyên thứ tự và lề); `compactTopBar()` một hàng ˅ / "Đang phát" (thay bằng khoảng trống khi cột < 360 dp) / EQ / Hẹn giờ, đều không căn baseline; `mediaRow` = bìa + khối tên (`title.maxLines=3`); `requestArtFit` dạng này luôn `applyTight(true)`, `rest` tính không có `mediaRow`, `artSide(inner / 2, h, rest, rowArt)`; `sizeArt` đặt `topMargin=0`; `applyTight` không trim lề `title`.

Lệch nhỏ so với kế hoạch: kế hoạch ghi `fontScale <= 1.3` ở `topBar()`; tôi áp điều kiện này vào cả `landscapeCompact` (font lớn → giữ nguyên dạng cũ, vẫn cuộn), để dạng cũ và EQ/Hẹn giờ xếp dọc đi cùng nhau. Chưa kiểm ở font lớn.

## 2. Gate Ni (S6)

| Lệnh | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL, 4m59s |
| Debug (đếm từ XML) | 207 suite, **2.799** test, 21 skipped, 0 failures, 0 errors |
| Release (đếm từ XML) | 207 suite, **2.799** test, 21 skipped, 0 failures, 0 errors |
| `:app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL |
| `-PmeloOpen=true :app:assembleShareableDebug` | BUILD SUCCESSFUL |
| `:app:assemblePersonalDebug` | BUILD SUCCESSFUL |
| `git diff --check` | Sạch |

Baseline 2.796 → 2.799 (+3: 2 test chip thể loại, 1 test policy ngang thấp). Log đầy đủ ở thư mục scratchpad của phiên, không commit.

Giới hạn: JVM test và Gradle xanh không chứng minh giao diện thật; bố cục D2 và D4 được kiểm bằng QA Sony (mục 4).

## 3. Build Tiny, gate APK, cài Sony (S7)

| Mục | Kết quả |
|---|---|
| Bundle | `git bundle` đầy đủ từ HEAD `f054637`, SHA-256 `7C31C93D…1E91` khớp Ni và Tiny (chép qua ổ T:, chỉ file bundle). `git bundle verify` chạy trên Ni đạt; bước verify trong script Tiny báo "need a repository" (chạy ngoài repo, không ảnh hưởng: clone thành công và HEAD khớp) |
| Build | Clone cô lập mới `C:\Bots\MeloVault-SUABOLOC-IsolatedQA-20261107-f054637`, detach `f054637`; `telegram.config.valid=true`; BUILD SUCCESSFUL 3m17s |
| APK | `versionCode 122 / 0.6.97-beta`, package `app.melovault.player`, cert `eecd3df7…fce8`, 33.910.894 B, SHA-256 `7FB94021…7A24` |
| Gate trước cài | Sony `QV725WEQ54` model XQ-AS52 (Sony), `device`; đang cài vc122 (candidate widget `58BD55F0…`); MediaSession không PLAYING; đã pull APK cũ làm bản lưu |
| Cài | `adb install -r` thành công; APK pull lại từ máy khớp SHA-256 (`INSTALLED_HASH_MATCH=true`); lastUpdateTime 07/11/2026 11:54 |

Cài APK này đã **thay candidate widget** trên Sony; APK nhiệm vụ này không có widget.

## 4. QA Sony (S8) — 07/11/2026, màn bật chỉ khi thao tác

Ảnh: [hồ sơ ảnh](2026-11-07-sua-bo-loc-dang-phat-ngang-hinh-nghiem-thu.md); ảnh gốc ở `docs/plans/artifacts/2026-11-07-sua-bo-loc-dang-phat-ngang/` (không commit). Sony 1080×2520, 420 dpi, font 1.0.

| Q | Thực tế | Kết quả |
|---|---|---|
| Q1 Thư viện, Đã tải, Playlist, Nguồn (kiểu mặc định) | Ô tìm giãn hết hàng, không còn ☰; UI dump Thư viện, Playlist, Nguồn có 0 node `Bộ lọc` (Đã tải: dump không ra, kết luận từ ảnh) | PASS |
| Q2 chip thể loại | Mixset bật (510 bài) rồi chạm lại: ô tìm trống, về 25.629 bài. Mixset → LakHouse: chuyển lọc (31 bài); chạm lại LakHouse: về đủ. Chillout bật (386 bài) rồi tắt. Trong một kênh: Chillout 43 bài, chạm lại về 654 bài, ô tìm trống | PASS |
| Q3 ngang, tên 1 dòng | Bìa cạnh tên (bìa 279 px, đúng số kế hoạch), Đảo/Lặp/Lưu cao 126 px, lộ đủ, không cuộn; EQ `[716,79][842,205]` và Hẹn giờ `[863,79][1062,205]` cùng top/bottom, cùng hàng nút đóng | PASS |
| Q4 tên 2 dòng, bài không bìa (♪) và bìa chữ cái | Như Q3, tên không đè nút. Chưa thử tên 3 dòng | PASS |
| Q5 trong dạng ngang | Đảo/Lặp/Lưu bật rồi hoàn tác về tắt/tắt/chưa lưu (kiểm bằng content-desc); EQ mở rồi đóng; Hẹn giờ mở rồi đóng, không chọn; đổi bài 4 lần, bìa đổi đúng; tab Tiếp theo và Lời nhạc chạy | PASS |
| Q6 dọc → ngang → dọc khi đang phát | state=3, vị trí 124,9 → 131,0 → 137,2 → 140,1 s (không về 0); logcat crash không có FATAL. EQ/Hẹn giờ ở màn dọc cùng top/bottom (205/331). Màn dọc so với ảnh trước thay đổi: chỉ hàng Đảo/Lặp/Lưu lệch 8 px, nhiều khả năng do dòng tên kênh của bài baseline dùng phông cao hơn (**suy luận, chưa chứng minh**; không có APK baseline để so cùng bài) | PASS, có ghi chú |

Sự kiện trong QA:
- Hộp thoại hệ thống "Cho phép truy cập?" (dữ liệu điện thoại qua USB) xuất hiện lúc 11:56 và chặn chạm; đóng bằng phím Back, **không cấp quyền**.
- Một chạm trượt trúng một dòng bài ở Thư viện nên nhạc bắt đầu phát; dùng luôn cho Q3–Q6. Cuối QA đã tạm dừng (state=2). Hàng chờ/bài đang phát của app trên Sony khác trước QA.
- Sony đang có trạng thái MediaSession cũ "Đã ngắt kết nối âm thanh Bluetooth" (có sẵn trước phiên).
- Cleanup: `cmd window user-rotation free` (tự động xoay = 1), màn tắt. `SONY_SCREEN_END`: Display State=OFF.

## 5. APK QA bàn giao chủ dự án nghiệm thu

**QA CANDIDATE — CHƯA NGHIỆM THU.** Chi tiết (Git commit, tên APK, package, version, SHA-256, byte, cert, loại build, config, test, thiết bị, link Drive owner-only): [apk-test](2026-11-07-sua-bo-loc-dang-phat-ngang-apk-test.md). Drive đã kiểm: một quyền duy nhất (owner), APK 33.910.894 B khớp; tải bằng trình duyệt chưa thử.

**Chưa kiểm / giới hạn:**
- **CHƯA XÁC NHẬN ÂM THANH THỰC** (Media3 PLAYING không chứng minh có tiếng).
- Máy 16:9 ngang, Y700, màn xe, chia đôi cửa sổ (`windowHeightDp` ở đó chưa đo).
- Chữ lớn (font > 1.3): giữ dạng cũ, chưa thử.
- Tên bài 3 dòng ở dạng ngang thấp.
- Kiểu Teal (nút ☰/⚙ vẫn mở hộp lọc theo đọc mã, chưa thử trên máy) và màn Gọn (bỏ nút chết theo đọc mã, chưa thử).
- Q1 tab Đã tải chỉ dựa trên ảnh. Không so được ảnh dọc với APK baseline cùng bài.
- Không tạo Playlist, không bật đồng bộ, không đăng xuất, không mất mạng.

## 6. Rủi ro

| Rủi ro | Ghi chú |
|---|---|
| Sony 21:9 là máy thật duy nhất | Mốc 500 dp chỉ kiểm bằng số Sony (411 dp cao); ScrollView còn đó nên vẫn cuộn được |
| Widget | Candidate widget đã bị thay trên Sony; muốn sơ bộ widget phải cài lại APK của nhánh widget |
| Gộp nhánh | Chỉ có thể đụng `HANDOFF.md`; nhánh tích hợp có thể đã đi tiếp, cần kiểm lại khi merge |
