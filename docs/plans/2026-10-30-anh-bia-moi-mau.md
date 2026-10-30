# TASK 2026-10-30-anh-bia-moi-mau — ảnh bìa độc lập kiểu màu (P2-A)

**Baseline:** `b6f61a0` (feature/e93-03d-y700-offline-cockpit, đã gồm UI/UX R1c, Đảo, D1–D3; test Ni 2268/21/0).
**Nhánh:** `dot/2026-10-30-anh-bia-moi-mau`; worktree `D:\Bots\_worktrees\MeloVault-anh-bia-moi-mau`.
**Thiết bị QA:** chỉ Sony XQ-AS52 `QV725WEQ54` qua Tiny (quyết định chủ dự án 30/10 17:01).
**Nguồn phương án:** `docs/plans/2026-10-29-melo-ui-ux-ke-hoach-chi-tiet.md` mục "P2-A"; `docs/plans/2026-10-29-melo-ui-ux-ke-hoach.md` mục 3 "Đợt P2" + mục 4 (dòng `MeloThemePolicy.rowArtLayer`).

## Mục tiêu
Ảnh bìa trên dòng bài hiện ở Phone/Tablet NEW cho cả CURRENT và TEAL; quyền tải ảnh (`rowArtSwitch`) tách khỏi màu.

## Hàm thực tế cần sửa (đã đọc source, đúng 4 file trong phạm vi)
1. `MeloThemePolicy.kt:rowArtLayer` — hiện `appliesTo(theme,...) || profile==COMPACT` (chỉ true khi TEAL cho Phone/Tablet). Đổi thành `(layer!=null && (PHONE||TABLET)) || profile==COMPACT` — bỏ phụ thuộc theme, giữ COMPACT=true/CAR=false. `appliesTo`/`effective` giữ nguyên (vẫn chỉ dùng cho màu/tab).
2. `MeloThemePolicy.kt:rowArtActive` — hàm chết (chỉ có trong test), đang mang khái niệm cũ `theme==TEAL && switchOn`. Đổi tham số thành `(layerShown: Boolean, switchOn: Boolean)` để phản ánh đúng "quyền tải tách khỏi màu", rồi nối vào `TelegramPreviewActivity.rowArtOn()` thay vì để chết.
3. `TelegramPreviewActivity.kt:rowArtOn` (dòng ~128) — đổi thân hàm gọi `MeloThemePolicy.rowArtActive(rowArtLayerShown, switch)` thay vì tính tay; đổi tên biến `rowArtThemeActive` → `rowArtLayerShown` (hết nghĩa "theo theme") tại 4 vị trí (khai báo + 3 gán ở `showCompactLayer`/`showNewLayer`/`detachMeloLayer`). Không đổi key, scheduler, `requestRowArt`/`peekRowArt`/`setRowArtListener`. Sửa 2 comment đã lỗi thời ("of the teal theme...", "(or another theme)").
4. `MeloLibraryPane.kt` — thêm `private val rowArtEnabled = MeloThemePolicy.rowArtLayer(c.theme, layer, c.sizes.profile)`; đổi các gate ảnh bìa (listener init/detach dòng ~138/316, `requestVisibleArt` lúc scroll dòng ~198/213, request sau refresh dòng ~457-459, chọn ảnh/`artKey` cho `SongRowModel` dòng ~789/791) từ `c.teal` sang `rowArtEnabled`. **Giữ `c.teal`** cho mọi gate màu/tab khác (onAccent, filter teal-only, dividerHeight, sortChip ẩn teal, alphabet bar themed, renderChips/renderThemeTabs, needsSortSwitch, khoảng đệm Save) — không đụng.
5. `MeloComponents.kt:songRow` (dòng ~284) — điều kiện `(teal || isCompact) && model.artKey != null` đang chặn `rowArtView` ở Phone/Tablet CURRENT dù đã có artKey. Đổi thành chỉ `model.artKey != null` (khớp hệt nhánh `tealSongRow` dòng ~574, vì artKey giờ đã là cổng duy nhất do bước 4 kiểm soát). `rowArtView`/`artView`/placeholder/size profile giữ nguyên, không thêm loader.

## Ngoài phạm vi / không sửa
`MeloRowArtPolicy.kt` (scheduler/decode thuần, không đổi), `MeloCompactScreen.kt`/`MeloStudioDeck.kt`/`MeloTabletScreen.kt` mini-art (các biến `artKey` ở đó là key ghi nhớ cục bộ khác, không phải `SongRowModel.artKey`), TDLib/cache/schema/Media3/Đảo/Playlist, songKey/signer/versionCode. Không phát hiện việc phải sửa ngoài 4 file đã giao.

## Tiêu chí hoàn tất
- Unit test MeloThemePolicy (CURRENT/TEAL × Phone/Tablet/Compact/Car, switch ON/OFF) PASS, không giảm test cũ không lý do.
- Build Debug+Release Ni xanh, `git diff --check` sạch.
- QA Sony: ảnh hiện ở Current & Teal (dọc/ngang), không ảnh sai bài khi cuộn/đổi nguồn/tìm/xoay, tắt switch không tạo request mới, hồi quy Deck/Đảo không vỡ, không ANR.
- Upload APK Debug Drive owner-only hoặc BLOCKED đúng phần; không tự nghiệm thu chính thức.

## Rủi ro / điểm dừng
Nếu phát hiện cần sửa ngoài 4 file (ví dụ service/cache/TDLib) → BLOCKED đúng phần, dừng, báo chủ dự án. Nếu Tiny/Sony/Drive không sẵn sàng ở S4–S5 → ghi BLOCKED, giữ code/test S1–S3 đã xong.
