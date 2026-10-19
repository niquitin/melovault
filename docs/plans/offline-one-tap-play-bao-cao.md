# Báo cáo Sonnet — offline-one-tap-play (19/10/2026)

**Vai trò:** Sonnet thực thi + QA từ xa qua ADB. **Nhánh:** `dot/offline-one-tap-play`. **Plan:** `docs/plans/offline-one-tap-play.md` (chủ dự án duyệt nguyên văn, không sửa). Dừng chờ ChatGPT nghiệm thu; không merge, không phát hành, không bump versionCode.

## 1. Git

| Mục | Giá trị |
|---|---|
| HEAD khi bắt đầu | `470e701` (sạch, đúng nhánh) |
| **Commit code** | `fdf64c5` — `feat(shareable): one-tap play in Downloaded with version menu` |
| Commit docs của ChatGPT chen vào trong phiên (không đụng code) | `93c50c1` (quy tắc màn hình), `284944b` (kiểm toán) — nằm trên `fdf64c5` |
| Commit báo cáo này | commit kế tiếp của nhánh (xem `git log -1`) |

Commit dùng danh tính `Claude Code <noreply@anthropic.com>` (như các commit Claude trước) qua `-c`, không đổi cấu hình git.

## 2. File sửa (đúng 2 file production + 1 test, 185 dòng thêm/15 xóa)

1. **MỚI** `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/TelegramOfflinePlayPolicy.kt` (30 dòng) — `defaultEntry(group, accountId, playingKey, readable)` và `queueEntries(groups, selectedKey)`, thuần, không I/O, trả đúng `Entry` gốc.
2. `…/TelegramPreviewActivity.kt` (+60/−15), theo mục A→I của plan:
   - A: helper `isReadableOfflineCopy`, `defaultOfflineEntry`, `playOfflineEntry`, `playOfflineGroup`, `openOfflineGroupMenu`, `offlineQueueSongs` (đặt cạnh `offlineRepresentative`).
   - B: `showOfflineVersions(group)` bỏ tham số `anchor`; chọn bản ⇒ `playOfflineEntry` (giữ kiểm tài khoản/`auth.isReady()`).
   - C: `playShownSong` nhóm >1 bản ⇒ `playOfflineGroup`. D: `tracks.onItemClickListener` gọi `playShownSong`.
   - E/F: nút ⋮ cũ và `meloHost.openSongMenu` ⇒ `openOfflineGroupMenu`. G: `showSongOptionsMenu(..., versions = null)` thêm mục `☰ Chọn bản khác (N bản)` (id 6) ngay sau "▶ Phát bài này"; mục 1 phát đúng bản. H: phụ đề dòng cũ `· ⋮ để chọn bản`. I: hàng đợi `:616` và `:670` dùng `offlineQueueSongs` khi `filterOnlyDownloaded`.
   - `grep showOfflineVersions`: còn đúng định nghĩa + lời gọi từ mục 6 của menu.
3. **MỚI** `app/src/test/java/…/TelegramOfflinePlayPolicyTest.kt` — 8 test (đúng 8 mục plan §7).

Không đổi: `TelegramOfflineLibrary.kt`, DAO/schema, service, TDLib, Media3, `Melo*`, `build.gradle`, CLAUDE.md, docs/workflow. Một chỗ khác chữ plan (không đổi hành vi): `shownSongs` là `List` bất biến nên thay phần tử bằng `toMutableList().also { it[index] = song }` thay vì `shownSongs[index] = song`.

## 3. Test / build

| Nơi | Lệnh | Kết quả |
|---|---|---|
| Ni | `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL. **Debug và Release mỗi biến thể: 1.263 defined, 1.242 passed, 21 skipped, 0 failed, 0 error** (mốc cũ 1.255/1.234/21 → +8, skipped vẫn 21) |
| Ni | `:app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL |
| Ni | `git diff --check` | sạch |
| Tiny (clone cô lập, nhánh `qa/offline-one-tap-play`, HEAD `fdf64c5`, cây sạch) | cùng test | exit 0; Debug/Release mỗi biến thể 1.263 / 1.242 passed / 21 skipped / 0 failed |
| Tiny | `:app:assembleShareableDebug` | BUILD SUCCESSFUL |

Test JVM xanh chỉ chứng minh logic chọn bản/hàng đợi, không chứng minh UI/TDLib/âm thanh.

## 4. Chuỗi gate APK và cài Shanling

- Bundle `MeloVault_offline-one-tap-play_33150d4-fdf64c5_delta_20261019.bundle`, SHA256 `26375f7428754d9b5d500de51d58e8ea6d764c47920b550d40fbd9c640a33aad` (khớp trên Tiny, `bundle verify` OK). Chép chỉ vào `C:\Bots\_qa_temp\OfflineOneTap_fdf64c5\` trên Tiny, không đụng repo Tiny ngoài fetch/checkout nhánh QA.
- APK Debug build trên Tiny: `app-shareable-debug.apk`, 33.609.254 byte, **SHA256 `9BE6753B70C0B78A8ED2D4644F64F56C5670A91E0403543BD56EAA08CD3C44C1`**; package `app.melovault.player`, versionCode **109**, `0.6.88-beta` (không đổi), debuggable; signer SHA-256 **`eecd3df7…ce8`**. Telegram config chỉ kiểm có/không: `local.properties` hợp lệ (có/có), BuildConfig API_ID khác 0 và hash dài 32 — không in hay chép giá trị.
- Bản đang cài trên Shanling trước đó: vc109, SHA256 `7C4F61553465BC7201F5008351419DA8E8194D5C80E69268B98309F285990FDE`, signer `eecd3df7…ce8` (trùng) ⇒ không hạ version, chữ ký khớp.
- Trước cài: `adb devices -l` thấy `30313860180939` device; `getprop` = Shanling M3 Ultra / Shanling / Android 10; MediaSession MeloVault `state=2` (tạm dừng, không phải 3).
- **Chỉ một lệnh:** `adb -s 30313860180939 install -r <APK Tiny>` ⇒ `Success`, 09:19:55. Sau cài kéo `base.apk` về và băm: **`9be6753b…44c1` = APK Tiny**; vc109.
- APK Ni (Debug `d622634b…c5bd`, Release `83ab7f2a…8657`) **không cài ở đâu** (Ni thiếu Telegram config).
- Không dùng uninstall / pm clear / logout / xóa cache hay dữ liệu / `-d` / airplane / FYT. Sony không cài, không thao tác.
- Rollback máy: `install -r` lại APK vc109 cũ (bản kéo về lưu ngoài repo ở thư mục scratchpad của phiên, SHA đã nêu).

## 5. QA thiết bị — Shanling M3 Ultra (Ni), serial `30313860180939`, bố cục Gọn 768×1280

Ảnh/video: `docs/plans/artifacts/offline-one-tap-play/` (private, git bỏ qua; chứa tên bài và kênh Telegram). Mọi PNG đều kiểm header `PNG` và đã xem được. Đọc trạng thái bằng `dumpsys media_session` (package `app.melovault.player`).

Trạng thái danh sách Đã tải lúc QA: **4 nhóm / 9 bản** (bốn nhóm có 2, 2, 2, 3 bản).

| # | Thao tác (giờ máy) | Kỳ vọng | Thực tế | Kết quả | Ảnh / bằng chứng |
|---|---|---|---|---|---|
| 1 | ☰ → ✓ Đã tải; chạm **một lần** dòng 1 (nhóm 2 bản) 09:30:42 | phát ngay, không hộp chọn, dòng "Đang phát", thanh đáy hiện tên bài | không có hộp chọn; dòng 1 sáng + vạch nhạc; thanh đáy hiện tên; Media3 `state=3`, position 5.686 ms (+5 s) → 33.026 ms (lần đọc sau) | **PASS** | `04_before_tap_row1…`, `05_after_tap_row1_5s…`; log `MeloVaultPlay stage=resolve → stage=parsed … completed=true` cách nhau 34 ms (lần chạm dòng 2, 09:34:06) |
| 2 | ⋮ dòng 1 → menu → "☰ Chọn bản khác (2 bản)" → chọn bản **khác** (BOX NHẠC VINAHOUSE); rồi ĐÓNG ở lần mở sau | menu đủ mục, bản đang phát có ▶, chọn bản khác ⇒ đổi bản; ĐÓNG không đổi nhạc | menu: Phát bài này / **Chọn bản khác (2 bản)** / Phát tiếp theo / Thêm vào Playlist / Thông tin; ▶ ở bản EMI; chọn BOX ⇒ metadata nguồn **NHÓM NHẠC EMI → BOX NHẠC VINAHOUSE**, phát lại từ 5,7 s, `state=3`, thanh đáy `… · BO…`; ĐÓNG: nhạc không đổi, position tiếp tục tăng | **PASS** | `06_menu_row1_overflow…`, `07_version_list_row1…`, `08_after_pick_other_copy…`, `11_after_close_dialog…` |
| 3 | chạm lại chính dòng 1 (09:32:28) | phát lại từ đầu **cùng bản vừa chọn** | position 20.838 → 2.825 ms; metadata vẫn BOX NHẠC VINAHOUSE; mở lại ⋮ → Chọn bản khác: dấu ▶ nằm ở bản BOX; dòng 1 của danh sách vẫn hiển thị bản BOX (kiểm ở `dumpsys`/UI lúc 09:34) | **PASS** | `09_after_retap_same_row…`, `10_version_list_after_retap…` |
| 4 | ⏭ (nút thanh đáy) 3 lần liên tiếp | mỗi lần một bài khác, đúng thứ tự dòng, không nghe lại cùng bài | active item 0→1→2→3: dòng 2 (NST VietMix…, nguồn EMI) → dòng 3 (Chill Bên Hiên…) → dòng 4 (ZIK & TIGER…); mỗi lần `state=3`, position ~2,8–3,0 s | **PASS** | `12_next_1/2/3…`; queue cuối phiên (5 nhóm/10 bản) `queue size=5` = một mục/nhóm, không phải 10 |
| 5 | Máy bay / ngắt mạng rồi chạm bài đã tải | phát ngay không chờ mạng | **Không làm** (plan và CLAUDE.md cấm tự bật máy bay/tắt Wi-Fi). Chỉ có gián tiếp: `getMessage` trả `completed=true` sau 34 ms **khi Wi-Fi đang bật** | **CHƯA KIỂM CHỨNG** (BLOCKED theo luật) | log `MeloVaultPlay` ở bước 1 |
| 6 | ☰ → ♫ Thư viện; ⋮ một bài thường; chạm một bài thường (09:36:58) | như cũ, menu **không** có "Chọn bản khác", chạm vẫn phát | Tất cả · 29.612 bài; ⋮ bài thường: Phát bài này / Phát tiếp theo / Thêm vào Playlist / Tải về và phát / Thông tin (không có Chọn bản khác); chạm bài thường ⇒ `state=3`, position 5.493 → 14.618 ms, dòng "Đang phát" | **PASS** | `13_library_all…`, `14_library_row_overflow…`, `15/16_library_tap_regular…` |
| 7 (thêm) | ⋮ nhóm **một bản**; ⋮ nhóm 3 bản; video 14 s chạm một lần | nhóm một bản không có mục Chọn bản khác; nhóm 3 bản liệt kê 3 bản; video | nhóm 1 bản (Nhạc 2.mp3) menu như cũ; nhóm 3 bản: danh sách đủ 3 kênh; video MP4 hợp lệ (110.904 byte) | **PASS** (ảnh) / video chưa xem | `19_single_copy_row_overflow…`, `20_three_copy_version_list…`, `18_video_one_tap_downloaded….mp4` (header MP4 đúng, **tôi không mở xem được nội dung**) |

Cuối phiên: Media3 `state=2` (tạm dừng) như trạng thái ban đầu; `queue size=5`. Quét logcat toàn bộ: **không có `FATAL EXCEPTION`, không ANR**; hai dòng "app died" ở 07:24 (trước phiên) và 09:19:55 (chính lần `install -r` thay gói) là chủ đích.

## 6. Quy tắc màn hình mới (`93c50c1`, đọc muộn)

Quy tắc xuất hiện lúc 09:09 trong phiên; tôi chỉ đọc nó ở bước kiểm Git cuối (≈09:42). Trong QA (09:19–09:42) màn Shanling bật bằng `KEYCODE_WAKEUP` trước mỗi thao tác, không có bước nào để màn sáng cố ý lúc build/chờ (tự tắt theo timeout 15 s, một lần ChatGPT cũng gửi SLEEP). Không đổi timeout/độ sáng/AOD. Cleanup đã chạy 09:42:49 +07: gửi `KEYCODE_SLEEP=223` → `mWakefulness=Asleep`, `mScreenState=OFF`.

- **SHANLING_SCREEN_END=OFF** (xác nhận 09:42:49 +07, `dumpsys power`: `mWakefulness=Asleep`, `dumpsys display`: `mScreenState=OFF`).
- **SONY_SCREEN_END=OFF** (chỉ đọc, không gửi lệnh nào: `Display State=OFF`, `mWakefulness=Dozing`, 09:42:53 +07).

## 7. Lỗi, quan sát, giới hạn

- Không phát hiện lỗi chức năng trong các bước đã chạy.
- **Tác dụng phụ bình thường:** chạm "Nhạc  2.mp3" (bài chưa tải) ở bước 6 làm TDLib tải xong tệp ⇒ danh sách Đã tải thành 5 nhóm/10 bản. Không xóa/gộp gì.
- Bộ công cụ QA: `uiautomator dump` đôi lúc trả rỗng khi UI đang hoạt họa, nên có bước dùng tọa độ từ ảnh; lần chạm ☰ đầu (09:27) rơi vào lúc màn đã tắt nên không có tác dụng — đã làm lại; ảnh trống 3.922 byte `00_shanling_baseline…` là ảnh lúc màn tắt (không phải bằng chứng QA).
- ChatGPT chen thêm commit docs `93c50c1`/`284944b` lúc tôi đang làm; chỉ docs, mã `fdf64c5` nguyên vẹn.

## 8. CHƯA KIỂM CHỨNG

- **Âm thanh thực: CHƯA XÁC NHẬN ÂM THANH THỰC.** Chỉ có Media3 `PLAYING` + position tăng (thanh trạng thái máy hiện chỉ báo tần số 48K/44.1K, tôi không dùng nó làm bằng chứng).
- **Phát khi tắt mạng / máy bay:** chưa chạy (bị cấm). Plan và kiểm toán đã nêu rủi ro `getMessage` khi offline và fallback `defaultEntry` sang bản không đọc được ⇒ `downloadAndPlay` có thể stream; chưa đo.
- Tình huống 5 trong plan (tệp bị mất ⇒ bỏ qua sang bản đọc được): chỉ có test JVM, không giả lập trên máy (cấm xóa file).
- Bố cục khác Gọn: Điện thoại, Tablet, Hai khung, Car, Portrait/Cockpit cũ và nút ⋮ của ListView cũ chưa thử trên máy (cùng `playShownSong`/`openSongMenu` nhưng không có ảnh).
- Sony/Tiny không QA. Phím vật lý Shanling, tắt màn khi đang nhạc, Y700/S25: chưa thử.
- Video `18_…mp4` chưa được tôi xem; ChatGPT cần mở trực tiếp.
- Mục 11 của plan (chủ dự án tự thử, gồm bước máy bay): chủ dự án chưa xác nhận ⇒ theo CLAUDE.md trạng thái cuối chỉ có thể là BLOCKED — CHỜ CHỦ DỰ ÁN.

## 9. Rủi ro / điểm dừng

- `isReadableOfflineCopy` stat tệp trên main thread (tối đa vài lần cho một nhóm, đúng plan).
- Dòng Melo giữ bản đang chọn (thay `shownSongs[index]`); thấy đúng ở Gọn, chưa thấy ở Điện thoại/Tablet.
- Không có điểm dừng §12 nào bị chạm: diff production ~105 dòng, 2 file; không sửa file cấm.
