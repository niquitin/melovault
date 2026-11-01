# APK test — 2026-11-01-nhieu-playlist-co-ten

**QA CANDIDATE — CHƯA NGHIỆM THU.** Không phải bản phát hành; chưa merge/push. Đây là bản Debug QA để chủ dự án thử trên Sony; chưa có chấp thuận ĐẠT.

- Thư mục Drive (owner-only, `ntdung.md@gmail.com`): [MeloVault-NHIEUPLAYLIST-QA-CANDIDATE-20261101-vc121-0efec5a](https://drive.google.com/drive/folders/18RmsQArh_0QVdBId3pDjYwR2RuyU9Zvx)
- APK Debug: [MeloVault-0.6.96-beta-vc121-nhieu-playlist-0efec5a-QA-CANDIDATE-debug.apk](https://drive.google.com/file/d/16CiZb215xnzr5qfZigL1Eh8wN45DTURz/view)
- Ảnh QA: xem [`-hinh-nghiem-thu.md`](2026-11-01-nhieu-playlist-co-ten-hinh-nghiem-thu.md)

| Thuộc tính | Giá trị |
|---|---|
| Loại | Debug QA (debuggable), biến thể `shareable` thường (không `.open`) |
| Package | `app.melovault.player` |
| versionCode / versionName | 121 / `0.6.96-beta` (không đổi, không hạ cấp) |
| Source | Nhánh `dot/2026-11-01-nhieu-playlist-co-ten`; APK build từ HEAD `0efec5a98eb187f6f85b748bf06bcb77bcec6eda`; mã tính năng kết thúc ở `751b623` (sau đó chỉ commit hồ sơ). Baseline `57c1be64436c3c364bcd8c1a5f8a8193ea52a553`, kế hoạch `b0cfeac138e3cb0edbb4857f650795c79bd78957` |
| Build | Tiny, clone cô lập `C:\Bots\MeloVault-NPL-IsolatedQA-20261101-0efec5a` từ bundle `MeloVault_nhieu-playlist-co-ten_full_0efec5a_20261101.bundle` 13.673.183 byte, SHA-256 `d43922cb8db353a95ecaff5c62918d81a4cdc54a29540536b49bc713fc3fecff` (khớp hai đầu); `reportTelegramConfig` → `telegram.config.valid=true`; `assembleShareableDebug` BUILD SUCCESSFUL 2m51s |
| Byte | 33.773.508 |
| SHA-256 | `5B2FB5CC323B486ADFC2F359BF7C9FCC20E104D0D6FDE4DB3D2D3B3BDE03FEF8` |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (khớp app đang cài) |
| Telegram config | true (không in giá trị) |
| File thật | Tiny `C:\Bots\MeloVault-NPL-IsolatedQA-20261101-0efec5a\app\build\outputs\apk\shareable\debug\app-shareable-debug.apk`; bản sao Drive mount Ni `Z:\MeloVault_QA\MeloVault-NHIEUPLAYLIST-QA-CANDIDATE-20261101-vc121-0efec5a\` |
| Test JVM (gate Ni, Chặng 3) | Debug/Release 2683 / 21 skipped / 0 failures / 0 errors (+61 so với Chặng 2); assemble Shareable Debug/Release, `-PmeloOpen=true`, Personal Debug BUILD SUCCESSFUL. Không chạy lại test trên Tiny |
| Thiết bị đã test | Sony XQ-AS52 `QV725WEQ54` Android 12, cài bằng đúng một lệnh `adb install -r`; SHA bản cài (pull `base.apk`) khớp APK |
| Kiểm Drive | Thư mục, `hinh` và APK: chỉ quyền owner (`get_file_permissions` = 1 người, `owner`); kích thước APK trên Drive 33.773.508 byte khớp; SHA-256 bản sao trong thư mục đồng bộ `Z:` khớp APK build; 12 tệp (APK + 11 ảnh). Tải bằng trình duyệt chưa thử |

APK Release (`app-shareable-release.apk`, 30.933.358 byte, SHA-256 `7C3340E17C60446DAC18AF36F3808AD38FF7A0B073B6515BFA708AB081281810`, cùng cert) đã build nhưng **không cài, không upload**, chưa chạy trên máy.

Rollback: cài lại bản trước trên Sony bằng `adb install -r` (cùng vc121/cert), ví dụ candidate lời nhạc `62ED3500C171AAF9CCFCEE3EDDB8D7A11F3D08B65B93DA425A3790126B173DB9` (xem `2026-10-31-loi-nhac-p2b-apk-test.md`); không xóa dữ liệu. Tắt tính năng ngay trong mã: cờ `NamedPlaylistFlags.NAMED_ENABLED = false` (cần build mới); dữ liệu file `tg_named_playlists_v1` giữ nguyên.

## Chưa xác nhận

**CHƯA XÁC NHẬN ÂM THANH THỰC** (Media3 PLAYING chỉ chứng minh luồng phát). Đồng bộ hai máy Playlist có tên: **BLOCKED — chưa QA** (chưa có chỉ định máy thứ hai/gói thứ hai). Đồng bộ Telegram thật cho Playlist có tên (`#mvlist`) chưa chạy (tự đồng bộ đang “Chưa bật đồng bộ”). Tên Playlist có dấu tiếng Việt/emoji trên máy (QA dùng tên ASCII). Release/`.open`/Personal trên máy. Chữ lớn, TalkBack, Y700/S25/FYT. E105 vẫn BLOCKED nghiệm thu.
