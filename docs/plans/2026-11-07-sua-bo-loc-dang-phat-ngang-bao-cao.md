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

Giới hạn: JVM test và Gradle xanh không chứng minh giao diện thật; bố cục D2 và D4 chỉ được kiểm bằng QA Sony (S8).

## 3. Chưa làm

| Bước | Trạng thái |
|---|---|
| S7 Build Tiny, gate APK, cài Sony | Chưa làm |
| S8 QA Sony (Q1–Q6) | Chưa làm |
| S9 Ghép ảnh, upload Drive, hồ sơ APK | Chưa làm |
