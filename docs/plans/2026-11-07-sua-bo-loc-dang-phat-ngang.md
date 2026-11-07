# Kế hoạch — Sửa nút Bộ lọc, chip thể loại và màn Đang phát khi xoay ngang

Lập kế hoạch: Claude Code, Opus 5.5, 07/11/2026. **Chưa code.** Phiên thực thi mới làm theo file này, model do chủ dự án chọn.

| Mục | Giá trị |
|---|---|
| TASK_ID | `2026-11-07-sua-bo-loc-dang-phat-ngang` |
| Nhánh | `dot/2026-11-07-sua-bo-loc-dang-phat-ngang` |
| Worktree | `D:\Bots\_worktrees\MeloVault-sua-bo-loc-dang-phat-ngang` |
| Baseline | `f82185ba2ef17fce97d4680de2cd2dc86b2e1b4a` (vc122 / 0.6.97 đã ĐẠT) |
| Gate của baseline | Mỗi Debug/Release: 207 suite, 2.796 test, 21 skipped, 0 failures, 0 errors |

## 1. Yêu cầu và quyết định của chủ dự án

Sơ bộ toàn app ngày 07/11/2026 phát hiện các lỗi dưới đây. Chủ dự án giao lập kế hoạch sửa và đã chốt trong chat cùng ngày:

| # | Lỗi | Quyết định |
|---|---|---|
| D1 | Nút ☰ Bộ lọc ở kiểu màu mặc định chạm không có tác dụng | **Bỏ hẳn nút** ở kiểu mặc định. Không mở hộp lọc. Teal giữ nguyên |
| D2 | Màn Đang phát xoay ngang trên điện thoại: hàng Đảo/Lặp/Lưu bị cắt | **Bìa đặt cạnh tên bài**; EQ và Hẹn giờ lên cùng hàng với nút đóng; không cần cuộn |
| D3 | Chip thể loại (Chillout, LakHouse, Mixset) không tắt được bằng chạm | **Gộp vào nhiệm vụ này:** chạm lại chip đang chọn thì bỏ lọc và xóa chữ trong ô tìm |
| D4 | Chip Hẹn giờ lệch và bị cắt đáy khi xoay ngang | Sửa cùng, vì nằm đúng hàng nút đang đổi |

**Ngoài phạm vi:** hàng "Tiếp theo | Lời nhạc" bị che ở màn dọc; chạm Yêu thích có lúc không mở; EQ `clipped` lúc đổi mẫu; văn bản và 13 thay đổi chờ gửi ở trang Đồng bộ; widget; kiểu Teal; màn dọc; tablet và màn trên xe. Không đổi service, Media3, DB, TDLib, songKey, package, signer, versionCode.

## 2. Nguyên nhân đã xác minh

### D1 — Nút Bộ lọc

- `MeloLibraryPane.kt:126`: `onFilter = if (c.teal) ::openFilterDialog else null`.
- `MeloComponents.kt:806–813`: `searchBarWithField` luôn vẽ nút và chỉ gọi `onFilter?.invoke()`.
- Trên Sony chạm 2 lần, màn hình không đổi.
- `MeloCompactScreen.kt:114` gọi `c.searchBar(null, onQuery = ::queueQuery)` nên màn Gọn cũng có nút chết này.

### D2 — Hàng Đảo/Lặp/Lưu bị cắt

`MeloNowPlayingPolicy.artSide` giữ bìa không nhỏ hơn `rowArt`, cố ý để màn thấp phải cuộn. Số đo trên Sony khi xoay ngang (1 dòng tên bài, đơn vị px):

| Thành phần | Chiều cao |
|---|---|
| Vùng cuộn cột trái (`h`) | 573 |
| Hàng đóng + hàng EQ/Hẹn giờ | 126 + 126 |
| Bìa tối thiểu `rowArt` + lề | 147 + 21 |
| Tên 1 dòng + lề | 62 + 21 |
| Nguồn + lề | 49 + 10 |
| Đảo/Lặp/Lưu + lề | 126 + 21 |
| **Tổng / thiếu** | **709 / 136** |

Tên 2 dòng thiếu 198 px. Ảnh trên máy khớp: hàng nút chỉ lộ khoảng 21/126 px sau khi cột đã bị cuộn sẵn 31 px. Chữ lớn sẽ thiếu nhiều hơn.

### D3 — Chip thể loại

`MeloLibraryPane.renderChips`: `c.chip(genre.label, active == genre) { applyGenre(genre) }` luôn đặt lại đúng từ khóa, không có nhánh bỏ. `applyGenre(null)` đã biết cách xóa.

### D4 — Chip Hẹn giờ

Hàng EQ/Hẹn giờ trong `MeloStudioDeck.topBar()` là `LinearLayout` ngang để mặc định `baselineAligned=true`. Trên Sony, Hẹn giờ đứng thấp hơn EQ 19 px và bị cắt đáy ([552,193] so với [42,174], cùng đáy 300). **Suy luận từ mã:** lệch do căn baseline. Bước S3 phải kiểm lại bằng số đo.

## 3. Thiết kế

### D2 — Dạng "ngang thấp"

**Điều kiện, tính lúc dựng màn:** `layout == DeckLayout.LANDSCAPE && windowHeightDp < 500`.

Chọn mốc 500 dp vì dạng hiện tại cần khoảng 487 dp với tên 2 dòng ở Sony. Y700 và màn xe 782 × 642 cao hơn mốc nên giữ nguyên bố cục. Xoay màn đã dựng lại Deck qua `TelegramPreviewActivity.onConfigurationChanged`, nên không cần đổi bố cục khi đang chạy.

```text
[˅] Đang phát            [ EQ ] [Hẹn giờ]     hàng 1, cao minTouch

+--------+  Tên bài (tối đa 3 dòng)
|  BÌA   |  Kênh: ...                         hàng 2: bìa | khối tên
+--------+

[⇄ Đảo]     [↻ Lặp]     [☆ Lưu]               hàng 3
--------- dock giữ nguyên (ngoài ScrollView) ---------
```

**Kích thước bìa:** `artSide(inner / 2, h, rest, rowArt)`, trong đó `rest` là mọi thứ trừ hàng 2. Trên Sony: `rest` = 126 + 21 + 21 + 126 = 294, nên bìa = 573 − 294 = **279 px** (hiện 147 px). Khối tên 3 dòng cao khoảng 245 px, vẫn vừa.

Luôn dùng dạng chặt: ẩn viên chất lượng và dòng ghi chú như lúc thấp hiện nay. **ScrollView vẫn giữ** để chữ lớn hoặc màn thấp hơn còn cuộn được; không dùng dạng hàng 1 gộp khi `fontScale > 1.3`.

### Phương án đã loại

| Phương án | Lý do loại |
|---|---|
| Mở hộp Bộ lọc ở kiểu mặc định | Chủ dự án chọn bỏ nút; hộp trùng với chip đang hiện |
| Bìa nhỏ ở trên, tên 1 dòng | Chủ dự án chọn bìa cạnh tên; bìa nhỏ hơn và mất chữ |
| Xác định "thấp" bằng đo đạc rồi chuyển view khi chạy | Phức tạp, khó kiểm thử; mốc theo `windowHeightDp` đủ dùng |

## 4. Các bước

Mỗi bước commit riêng. Làm đúng thứ tự.

### S0 — Kiểm đầu phiên

`git status --short`, nhánh, `git rev-parse HEAD`, `git log -5 --oneline`, `git diff --check`, kiểm không có agent khác. `git diff f82185b..HEAD -- app/` phải rỗng trước khi code.

### S1 — Bỏ nút Bộ lọc (D1)

- `MeloComponents.searchBarWithField`: chỉ thêm nút khi `onFilter != null`, cho cả hai nhánh Teal và mặc định. Ô tìm đã có `weight=1` nên tự giãn.
- `MeloLibraryPane.kt:126` giữ nguyên; `openFilterDialog` chỉ còn dùng cho Teal.
- `docs/ui-spec.md` mục **Thanh tìm kiếm**: ghi "nút bộ lọc chỉ có ở kiểu Teal, quyết định 07/11/2026"; không sửa phần còn lại.
- **Xong khi:** kiểu mặc định không còn node `Bộ lọc`; Teal vẫn gọi được hộp lọc theo đọc mã.

### S2 — Chạm lại chip để bỏ lọc (D3)

- `MeloLibraryPolicy`: thêm hàm thuần `toggledGenre(active: GenreChip?, tapped: GenreChip): GenreChip? = if (active == tapped) null else tapped`.
- `MeloLibraryPane.renderChips`: chip thể loại gọi `applyGenre(MeloLibraryPolicy.toggledGenre(MeloLibraryPolicy.activeGenre(searchField.text.toString()), genre))`. Đọc ô tìm lúc chạm, không dùng `snap` đã bắt lúc dựng chip.
- **Test JVM** trong `MeloLibraryPolicyTest`: đang chọn thì về null; chưa chọn thì chọn; đang chọn chip khác thì đổi.
- **Không đổi:** chip "Tất cả trong kênh/chủ đề", đường Teal `Chillout ✕`.

### S3 — Căn thẳng EQ và Hẹn giờ (D4)

Trong `MeloStudioDeck.topBar()`, đặt `isBaselineAligned = false` cho hàng chứa `eqChip` và `sleep`. **Xong khi:** ui dump cho hai chip cùng `top` và `bottom`, kể cả dọc và ngang. Nếu vẫn lệch thì ghi nguyên nhân thực vào báo cáo, không tự đổi phạm vi.

### S4 — Policy "ngang thấp"

- `MeloNowPlayingPolicy`: thêm `LANDSCAPE_COMPACT_MAX_HEIGHT_DP = 500` và `fun landscapeCompact(layout: DeckLayout, windowHeightDp: Int): Boolean`.
- **Test JVM** trong `MeloNowPlayingPolicyTest`: LANDSCAPE 411 → true; 499 → true; 500 → false; 642 (màn xe) → false; PORTRAIT 411 → false.
- `artSide`, `playerTight` và test hiện có **không đổi**.

### S5 — Dựng dạng "ngang thấp" (D2)

Chỉ sửa `MeloStudioDeck.kt`:

1. Thêm field `landscapeCompact` tính từ `layout` và `metrics.windowHeightDp`, cạnh nơi `layout` được khai báo.
2. Tách `addPlayerBody` thành `addTitleBlock(target)` (title, source, qualityChips, qualityNote, playFeedbackView) và `addExtras(target)`. Dọc và ngang thường gọi cả hai trên cùng cột, **thứ tự và lề giữ nguyên**.
3. `topBar()`: khi `landscapeCompact && fontScale <= 1.3` dựng một hàng: nút ˅, nhãn "Đang phát" (`weight=1`, 1 dòng; `GONE` khi cột hẹp hơn 360 dp), EQ, Hẹn giờ; đều `isBaselineAligned=false`.
4. `buildLandscape()` khi `landscapeCompact`: `player` gồm topBar, `mediaRow`, extras. `mediaRow` ngang, `gravity=CENTER_VERTICAL`, `baselineAligned=false`, `topMargin=s.gap`, chứa `artHost` (`marginEnd=s.gap*2`) và `titleBox` dọc (`width=0, weight=1`). `title.maxLines = 3`. Giữ ScrollView và dock như cũ.
5. `requestArtFit()` khi `landscapeCompact`: `applyTight(true)`, `rest = MeloArtFit.restHeight(column, mediaRow, inner)`, `sizeArt(artSide(inner / 2, h, rest, s.rowArt))`. Vẫn gọi `arrangeExtras`.
6. `sizeArt`: `topMargin` là 0 ở dạng này (nếu không, `rest` sẽ thiếu đúng phần lề đó và lại tràn). `applyTight`: không đặt `topMargin` cho `title` trong dạng này.

**Xong khi:** S6 xanh; màn dọc và màn ngang cao không đổi theo đọc diff.

### S6 — Gate Ni

```text
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
.\gradlew.bat -PmeloOpen=true :app:assembleShareableDebug --console=plain
.\gradlew.bat :app:assemblePersonalDebug --console=plain
git diff --check
```

Log ra file, chỉ đọc tổng kết. Đếm từ XML: tổng phải **lớn hơn 2.796**, 0 failures, 0 errors; ghi số skipped thực. Mở hồ sơ `-bao-cao.md` và cập nhật `-ban-giao.md`.

### S7 — Build Tiny, gate APK, cài Sony

- Git bundle kiểm SHA-256, clone cô lập mới dưới `C:\Bots\`, không đụng các repo bị cấm ở CLAUDE.md mục 1. Telegram config chỉ ở Tiny, ghi true/false.
- Kiểm package, `versionCode 122`, cert `eecd3df7…fce8`, SHA-256, bytes, source.
- Trước khi cài: kiểm serial/model, tạm dừng nhạc. Chỉ `adb -s <serial> install -r`.

**Lưu ý:** Sony hiện giữ candidate widget `58BD55F0…` cũng vc122, nên cài đè được và không có widget trên Home. APK nhiệm vụ này **không có widget**, nên muốn sơ bộ widget sau đó phải cài lại APK widget. Ghi rõ bản đang cài vào bàn giao. Không tăng versionCode.

### S8 — QA Sony

Ảnh riêng tư ở `docs/plans/artifacts/<TASK_ID>/`, không commit. Bật màn chỉ khi thao tác; `finally` trả xoay màn tự động (`cmd window user-rotation free`), tắt màn, ghi `SONY_SCREEN_END`.

| Q | Màn và thao tác | Kỳ vọng |
|---|---|---|
| Q1 | Thư viện, Đã tải, Nguồn, Playlist ở kiểu mặc định | Không còn ☰; ô tìm giãn hết hàng; không có node `Bộ lọc` |
| Q2 | Chạm Mixset → chạm lại. Lặp với Chillout. Chọn Mixset → chạm LakHouse. Thử thêm trong một kênh | Bật đúng; chạm lại thì ô tìm trống và danh sách về đủ; đổi chip chuyển lọc |
| Q3 | Bài tên 1 dòng: xoay ngang, chạm ⤢ | Bìa cạnh tên; Đảo/Lặp/Lưu lộ đủ 126 px, **không cần cuộn**; EQ, Hẹn giờ cùng top/bottom |
| Q4 | Lặp Q3 với bài tên 2 dòng và bài không có bìa (chữ cái) | Như Q3; tên không đè nút |
| Q5 | Trong dạng ngang: bấm Đảo, Lặp, Lưu rồi hoàn tác; mở EQ, đóng; mở Hẹn giờ, đóng không chọn; đổi bài; tab Lời nhạc và Tiếp theo | Đều chạy; bìa đổi đúng bài; sau thử trả Đảo/Lặp/Lưu như cũ |
| Q6 | Đang mở Deck: dọc → ngang → dọc, đang phát | Vị trí phát không về 0; màn dọc y như trước (chụp so với baseline); 0 FATAL trong log |

Không tạo Playlist, không bật đồng bộ, không đăng xuất. **CHƯA XÁC NHẬN ÂM THANH THỰC.**

### S9 — Bàn giao

Ghép `00_tong_hop.png`; upload APK và ảnh Drive owner-only theo CLAUDE.md mục 8–9, kiểm quyền; ghi `-apk-test.md`, `-hinh-nghiem-thu.md`, `-bao-cao.md`, `-ban-giao.md`, nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**. Drive chặn tải thì ghi BLOCKED đúng phần, không né cảnh báo. Điền prompt sơ bộ rồi dừng. Không merge, không push.

## 5. Thao tác để chủ dự án tự thử

1. Thư viện: ô tìm không còn nút ☰ ở cuối.
2. Chạm Mixset rồi chạm lại Mixset: lọc bỏ, ô tìm trống.
3. Mở bài đang phát, xoay ngang: bìa cạnh tên, thấy đủ Đảo/Lặp/Lưu mà không cuộn.
4. Ở màn ngang đó: EQ và Hẹn giờ thẳng hàng, chạm được.
5. Xoay dọc lại: màn Đang phát như cũ, nhạc vẫn chạy.

## 6. Rủi ro và rollback

| Rủi ro | Cách xử lý |
|---|---|
| Sony 21:9 là máy thật duy nhất; máy 16:9 ngang chưa thử được | Mốc theo dp, ScrollView còn đó nên vẫn cuộn được; ghi rõ chưa kiểm |
| Đổi `addPlayerBody` ảnh hưởng màn dọc | Tách hàm không đổi thứ tự; Q6 so ảnh baseline |
| Bỏ nút ☰ ở Gọn cũng đổi giao diện Gọn | Nút đó vốn chết, có lợi; ghi vào báo cáo; không có máy Gọn để kiểm |
| `metrics.windowHeightDp` ở cửa sổ chia đôi | Chưa kiểm; nếu sai vẫn cuộn được |
| Chữ lớn | Giữ hai hàng trên, vẫn cuộn; ghi chưa kiểm |

**Rollback:** không có migration, không đổi dữ liệu. Revert các commit theo S1–S5 là đủ; mỗi lỗi một commit nên revert được riêng.

**Không xung đột với nhánh widget** ở phần code theo `git diff --stat f82185b f8e0b25`; hai nhánh chỉ có thể đụng nhau ở `HANDOFF.md` khi merge. Nhánh tích hợp có thể đã đi tiếp khi merge, phải kiểm lại.

## 7. Trường hợp phải dừng

Dừng và ghi BLOCKED nếu: cần sửa service, Media3, DB, TDLib, package, signer hoặc versionCode; cây bẩn lạ hoặc HEAD không khớp; có agent khác đang sửa; Sony unauthorized, sai cert, downgrade hoặc thiếu Telegram config. Nếu dạng mới không vừa Sony như số đã tính, ghi số đo thực vào báo cáo; không tự đổi quyết định bố cục D2 hay mở rộng phạm vi.
