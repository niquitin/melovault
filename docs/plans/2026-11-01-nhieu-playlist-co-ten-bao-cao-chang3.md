# Phụ lục báo cáo — Chặng 3 (S11–S19): giao diện Playlist có tên

TASK_ID: `2026-11-01-nhieu-playlist-co-ten`. Báo cáo chính: [2026-11-01-nhieu-playlist-co-ten-bao-cao.md](2026-11-01-nhieu-playlist-co-ten-bao-cao.md).

Thực thi: Claude Code Sonnet 5.5 (effort không xác định từ công cụ), phiên Chặng 3. Chủ dự án ra lệnh “Tiếp chặng 3” ngay trong hội thoại của Chặng 2 (cùng agent; Chặng 2 đã dừng, cây sạch, HEAD `3f36fd8` khớp bàn giao trước khi làm).
Phạm vi: S11–S19, giao diện Android Views. Không build APK, không ADB/Sony/Tiny/Drive; `SONY_SCREEN_END=KHÔNG THAO TÁC`.
Trạng thái: **CHẶNG 3 XONG về mã và test JVM. Chưa chạy trên máy: toàn bộ giao diện chưa được nhìn thấy hay chạm thử.** Mã dừng ở `751b623`.

## 1. Bảng kết quả

| Bước | Việc | Commit | File chính | Test mới | Kết quả |
|---|---|---|---|---|---|
| S11 | Đổi nhãn Yêu thích theo Phụ lục A | `3f150c5` | `MeloSavePolicy`, `MeloLibraryPolicy`, `MeloCompactPolicy/Screen`, `MeloCarPolicy/Screen`, `MeloSyncSettingsActivity`, `PlaylistSyncStatusPolicy`, `TelegramPreviewActivity`, `MeloUiLabActivity` | 0 (sửa 6 dòng test nhãn, mục 4) | PASS |
| S12 | `MeloNamedPlaylistPolicy` (thuần) | `a02ed2a` | `MeloNamedPlaylistPolicy.kt` | 29 | PASS |
| S13 | `NamedPlaylistController`, mở/đóng cùng Yêu thích, cờ hoàn tác | `79b318e` | `NamedPlaylistController.kt`, `NamedPlaylistFlags.kt` | 22 | PASS |
| S14–S18 | Tab Playlist hai mức, Thêm vào Playlist, Đổi tên/Xóa, Sắp xếp, Lưu hàng chờ, Phát | `0e8d45a` (+`751b623`) | `MeloNamedPlaylistViews`, `NamedPlaylistUi`, `MeloLibraryPane`, `MeloCompactScreen`, `MeloQueuePane`, `MeloScreenHost`, `TelegramPreviewActivity` | 10 (guard nguồn) | PASS |
| S19 | Gate Ni | (commit hồ sơ) | log trong `docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/` | — | PASS |

Test mới Chặng 3: **61** = 29 (`MeloNamedPlaylistPolicyTest`) + 22 (`NamedPlaylistControllerTest`) + 10 (`NamedPlaylistUiGuardTest`); 3 suite mới.

## 2. Gate Ni (S19) — chạy trên HEAD `751b623` (cây sạch), một lần

| Mục | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL (1 phút 54 giây) |
| `:app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL (1 phút 20 giây) |
| `-PmeloOpen=true :app:testShareableDebugUnitTest :app:assembleShareableDebug` | BUILD SUCCESSFUL (1 phút 20 giây) |
| `:app:assemblePersonalDebug` | BUILD SUCCESSFUL (19 giây) |
| `git diff --check` | sạch (exit 0) |

| Biến thể | Suite | Test | Skipped | Failures | Errors |
|---|---|---|---|---|---|
| Debug | 199 | 2683 | 21 | 0 | 0 |
| Release | 199 | 2683 | 21 | 0 | 0 |
| Baseline Chặng 3 (= kết quả Chặng 2) | 196 | 2622 | 21 | 0 | 0 |

- 2683 = 2622 + 61: đúng phần mới; skipped không đổi (21). Không `@Ignore`.
- Lưu ý đếm: thư mục kết quả Debug được lượt `-PmeloOpen=true` ghi đè sau lượt Debug thường (cùng 199 suite); lượt Debug thường đã BUILD SUCCESSFUL. Số Debug ở bảng đọc từ thư mục cuối. Release đọc từ lượt thường.
- Log: `gate-s19-tests.log`, `gate-s19-assemble.log`, `gate-s19-open.log`, `gate-s19-personal.log`, `gate-s19-status.txt`. Trong lúc làm có chạy lọc `--tests` và một lượt Debug toàn bộ (2622 test) ngay sau S11 để tìm test nhãn cũ; gate đầy đủ chỉ chạy một lần ở S19.

## 3. Thiết kế đã làm

**S11 nhãn (Phụ lục A).** “Playlist” chỉ danh sách cũ → “Yêu thích”: nút lưu và mô tả TalkBack, menu dòng (“☆ Thêm vào Yêu thích” / “★ Bỏ khỏi Yêu thích”, bản ngắn “Lưu Yêu thích”), thông báo lưu (“Đã lưu '…' vào Yêu thích.”), lỗi file (“Không đọc được Yêu thích trên máy”), nhãn chế độ Gọn/Car (Car chỉ đổi chữ), tab cũ của giao diện legacy, tiêu đề Cài đặt (“Tự đồng bộ Yêu thích & Playlist”). Tab/chip “Playlist” giữ nguyên chữ (nay là chỉ mục). Guard nguồn mới cấm các cụm cũ.

**S12 policy thuần** (`MeloNamedPlaylistPolicy`): màn của tab (Chỉ mục / Yêu thích / Chi tiết), hàng chỉ mục (“★ Yêu thích · N bài”, các Playlist, “+ Tạo Playlist” khóa ở 100), kiểm tên (khớp luật model), quyết định thêm (Telegram đã xác minh, bài trên máy kèm dấu vân tay, bài cũ/không rõ/placeholder/chưa sẵn sàng bị từ chối kèm lý do), hộp chọn có ✓, mọi câu chữ theo Phụ lục A, kế hoạch Phát, kế hoạch Lưu hàng chờ (đúng thứ tự, bỏ qua và đếm, > 1000 từ chối không cắt), ↑/↓ dừng ở hai đầu.

**S13 controller + nối Activity.** `NamedPlaylistController` làm mọi đọc/ghi session trên luồng Playlist qua `TelegramPlaylistRuntime.submit`; kết quả về UI chỉ khi còn đúng tài khoản, đúng lượt mở/đóng và Activity còn sống (cùng ý với `MeloSavePolicy.acceptsCallback`); thay đổi hiện sau khi file đã ghi; làm mới không đổi gì thì không vẽ lại. Mở/đóng đặt cạnh `loadSavedPlaylist`/`closeSavedPlaylist`, làm mới cạnh `reloadSavedPlaylistLive` (sau khi đồng bộ áp thay đổi). **Từ đây session Playlist có tên được mở trong app, nên mã đồng bộ Chặng 2 bắt đầu chạy thật ở foreground** khi tự đồng bộ đã bật. Cờ hoàn tác `NamedPlaylistFlags.NAMED_ENABLED` (object riêng, xem mục 5) chặn mọi cửa vào của Activity.

**S14–S18 giao diện.**
- Tab Playlist (Điện thoại, Tablet dọc, Hai khung — cùng `MeloLibraryPane`): mức 1 là chỉ mục thay chỗ danh sách bài; mức 2 là Yêu thích hoặc một Playlist, có chip “‹ Playlist”; chi tiết thêm “⤨ Ngẫu nhiên”, “▶ Phát hết”, “⋮” (Đổi tên, Sắp xếp, Xóa Playlist); chi tiết không có chip sắp xếp A–Z và không có thanh chữ cái (giữ đúng thứ tự người dùng). Chạm tab Playlist luôn về chỉ mục. Rời tab thì lần sau bắt đầu lại ở chỉ mục.
- Gọn: mục “☆ Playlist” (menu chính, menu lọc, thanh dưới) mở chỉ mục dạng danh sách trong hộp; menu chính có thêm “Phát / Phát ngẫu nhiên / Đổi tên · Sắp xếp · Xóa” khi đang mở một Playlist; tiêu đề đúng tên Playlist.
- “≡ Thêm vào Playlist…” trong menu dòng bài Telegram, menu bài trên máy, menu dòng của hàng chờ; hộp có ✓ ở Playlist đã chứa bài, chạm để thêm/bỏ, “+ Playlist mới…” tạo và thêm luôn; không phát bài; thông báo sau khi file đã ghi. Trong chi tiết, menu dòng có “Bỏ khỏi Playlist này”.
- Sắp xếp: hộp riêng với ↑/↓ ở mỗi dòng (≥ 48 dp, có mô tả đọc to), mỗi lần bấm là một thao tác Di chuyển (tối đa một thay đổi được gửi), “Xong”. Danh sách của app là `ListView` nên không có kéo thả.
- “Lưu hàng chờ thành Playlist”: chip “≡ Lưu Playlist” trong mọi màn Hàng đợi dùng `MeloQueuePane`; nhập tên; “Đã lưu N bài, bỏ qua M bài.”
- Phát: “▶ Phát hết” và “⤨ Phát ngẫu nhiên” bỏ placeholder và bài trên máy đã mất; chạm dòng phát bài đó.
- Car Mode không có tính năng mới (guard nguồn). Không đụng service, Media3, DB, TDLib auth, package, signer, versionCode.

## 4. Test cũ đã đổi (chỉ nhãn, theo kế hoạch S11)

| File | Dòng đổi | Cũ → mới |
|---|---|---|
| `MeloCompactPolicyTest.kt` | 1 | `"Playlist · 12 bài"` → `"Yêu thích · 12 bài"` |
| `MeloRowSavePolicyTest.kt` | 2 | `"Lưu Bài A vào Playlist"` → `"… vào Yêu thích"`; `"Bỏ lưu Bài A khỏi Playlist"` → `"… khỏi Yêu thích"` |
| `MeloUiDaThietBiTest.kt` | 3 | `"☆ Thêm vào Playlist"` → `"☆ Thêm vào Yêu thích"`; `"☆ Lưu Playlist"` → `"☆ Lưu Yêu thích"`; `"★ Bỏ Playlist"` → `"★ Bỏ Yêu thích"` |

Không assertion nào ngoài nhãn bị đổi. `MeloSavePolicyTest` (cụm “Bỏ lưu bài đang phát”) không phải sửa vì giữ nguyên tiền tố.

## 5. Quyết định, giới hạn và điểm cần chủ dự án biết

1. **Cờ `NAMED_ENABLED` ở file riêng `NamedPlaylistFlags.kt`**, không cạnh `V2_ENABLED` (file đó cấm sửa, và bàn giao chưa ghi chấp thuận). Guard nguồn kiểm: cờ là `true`, Activity dùng nó ở ≥ 10 chỗ, `TelegramPlaylistV2Session.kt` không nhắc tới. Chuyển sang cạnh `V2_ENABLED` là sửa một dòng, cần chủ dự án đồng ý.
2. **Hàng chờ khi Phát Playlist chỉ gồm bài Telegram** theo thứ tự; bài trên máy trong Playlist không nằm trong hàng chờ và phải chạm từng bài. Lý do: app giữ hai hàng chờ riêng (Telegram do `ShareablePlaybackService` giữ, “Nhạc trên máy” có hàng chờ riêng) và trộn chúng cần đổi service/Media3, thuộc điều cấm của kế hoạch. Policy báo lý do bằng một câu khi có bài trên máy (“Bài trên máy không nằm trong hàng chờ khi phát cả Playlist; chạm từng bài để phát.”). **Khác kỳ vọng Q5 của kế hoạch mục 7 khi Playlist có bài trên máy** (Q2 thêm 1 bài máy ⇒ hàng chờ chỉ có 3 bài Telegram). Nếu chủ dự án muốn hàng chờ trộn đủ 4 bài, đó là REWORK có đổi phạm vi.
3. **Sắp xếp là hộp ↑/↓**, không phải chế độ ngay trên dòng như kế hoạch 4.6; cùng chức năng, dùng được ở mọi màn (cả Gọn), không cần sửa adapter 1200 dòng của `MeloLibraryPane`.
4. **Gọn dùng hộp danh sách làm chỉ mục**, đúng chữ “mở chỉ mục dạng danh sách”; trạng thái chỉ mục của Gọn hiện danh sách Yêu thích bên dưới hộp.
5. **“Menu bài đang phát”** của kế hoạch: đã thêm vào menu dòng của hàng chờ (gồm bài đang phát) và menu dòng thư viện; Studio Deck không có menu riêng cho bài đang phát nên không có mục thứ ba.
6. **Bài trên máy** chỉ khớp khi tên, thời lượng và kích thước còn giống lúc lưu; thư viện nhạc máy phải đã đọc (cần quyền): chưa đọc thì dòng ghi “Đang đọc nhạc trên máy…” hoặc “cần cho phép đọc nhạc trên máy”, không báo “không còn”. Chạm một dòng đã mất mở hộp “Bỏ khỏi Playlist”.
7. Tìm kiếm trong chi tiết lọc bài Telegram; bài trên máy ẩn khi đang tìm.
8. Hộp nhập tên cho gõ tới 120 ký tự để báo được “tối đa 60 ký tự”, không cắt âm thầm.

## 6. Chưa kiểm chứng

- **Không có hình, không có máy:** bố cục, kích thước chạm, xoay màn, cỡ chữ lớn, TalkBack, hộp thoại trên Sony 1080×2520, thanh chip có đủ chỗ khi hiện 4 nút (⤨ ▶ ⋮ ‹), `fitHeader()` xếp chồng đúng hay không — tất cả chỉ có guard nguồn và policy, **chưa thấy bằng mắt**. Đây là việc của Chặng 4.
- Giữ cuộn khi xoay ở chi tiết: dùng cơ chế browse sẵn có, chưa thử; chỉ mục lưu vị trí cuộn trong bộ nhớ của pane.
- Tin `#mvlist` thật qua Telegram, hai máy thật, Keystore thật với alias mới, E105: như Chặng 2 (chưa).
- Dòng trạng thái “Đã đồng bộ” có tính cả Playlist có tên từ `refreshPlaylistStatus`; chưa xem trên máy.
- Quyết định treo từ kế hoạch: 4.5 (một công tắc chung, đang theo mặc định) và mục 7 (QA đồng bộ hai máy).

## 7. Việc tiếp

Chặng 4 (S20–S24): bundle sang Tiny, build Debug/Release bản thường, gate APK, QA Sony theo Q1–Q6 (đã điều chỉnh theo mục 5.2), ảnh, Drive owner-only, hồ sơ APK, prompt nghiệm thu sơ bộ. Prompt đầy đủ ở `2026-11-01-nhieu-playlist-co-ten-ban-giao.md`.
