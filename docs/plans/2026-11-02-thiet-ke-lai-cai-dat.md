# 2026-11-02-thiet-ke-lai-cai-dat — Kế hoạch: thiết kế lại màn Cài đặt, ẩn mục thiết bị, thêm Giới thiệu

Lập kế hoạch: Claude Code (02/11/2026). Không code/build trong phiên này. Nhánh `dot/2026-11-02-thiet-ke-lai-cai-dat`, worktree `D:\Bots\_worktrees\MeloVault-thiet-ke-lai-cai-dat`, baseline **`fa4cd09`** (tip `feature/e93-03d-y700-offline-cockpit` sau merge nháy-header `9d84ba7`).

## 1. Yêu cầu của chủ dự án (chat 02/11/2026, kèm ảnh màn Cài đặt vc121)

1. Bỏ nhóm **“THIẾT BỊ & MÀN HÌNH”** (Bố cục Gọn, Car Mode, Chừa chỗ nút •••) và nhóm **“NÂNG CAO”** (Thông tin màn hình & giả lập).
2. Thiết kế lại giao diện khu vực Cài đặt.
3. Thêm phần **Giới thiệu** có tên **Nguyễn Dũng**.
4. Agent đề xuất thêm.

Chủ dự án đã chọn (hộp hỏi trong chat 02/11):

| # | Câu hỏi | Lựa chọn |
|---|---|---|
| LD1 | Cách bỏ 2 nhóm | **Ẩn**, mở lại bằng **chạm 7 lần vào dòng phiên bản** trong Giới thiệu → hiện “Công cụ nhà phát triển” |
| LD2 | Kiểu thiết kế | **Nhóm thẻ có biểu tượng**, thanh tiêu đề cố định khi cuộn (mẫu ở mục 5) |
| LD3 | Giới thiệu | **Tên + mã nguồn GitHub + email góp ý** (+ giấy phép mã nguồn mở) |
| LD4 | Sửa thêm | Cả 4: **sửa chữ bị che**, **tách “Màn Đang phát” khỏi trang Giao diện**, **rút gọn mô tả Lời nhạc**, **dòng Đồng bộ ghi Bật/Tắt** |

## 2. Baseline (đọc mã tại `fa4cd09`)

- Màn Cài đặt: `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloUiLabActivity.kt` (926 dòng), dựng bằng View code, 4 trang `MeloSettingsPolicy.Page { HOME, APPEARANCE, DEVICES, ADVANCED }`; `renderHome()` dòng 291–355.
  - Nhóm cần ẩn: dòng 324–336 (`groupTitle("THIẾT BỊ & MÀN HÌNH")` … `"NÂNG CAO"`); trang `renderDevices()` 479–540, `renderAdvanced()` 544–594.
  - “Giao diện & màu sắc” và “Màn Đang phát” **cùng mở `Page.APPEARANCE`** (dòng 305 và 309) — trùng.
  - Lời nhạc: `switchRow(... MeloLyricsUiPolicy.SETTINGS_SUBTITLE ...)` dòng 319; câu mô tả 3 dòng là **phần công bố quyền riêng tư** (comment dòng 317: bật ở đây = đồng ý như hộp trong tab Lời nhạc).
  - Đồng bộ: dòng 312–316, mô tả cố định “Tự đồng bộ … khi mở app”, không phản ánh trạng thái thật (trạng thái thật là `consent.automatic` trong `MeloSyncSettingsActivity.kt` dòng 122).
  - Dòng phiên bản: dòng 348–354, nằm cuối nội dung cuộn.
  - Insets: `ViewCompat.setOnApplyWindowInsetsListener(scroll)` dòng 257–263 đặt padding systemBars lên ScrollView. **Ảnh chủ dự án cho thấy dòng phiên bản nằm dưới thanh điều hướng 3 nút và thẻ tài khoản chạy dưới đồng hồ/thanh trạng thái** → padding không có hiệu lực (nghi giống SB-1: cửa sổ không ở chế độ edge-to-edge nhất quán / insets không được phát lại sau `setContentView` mới trong `render()`). Phiên thực thi phải tái hiện và tìm nguyên nhân, không đoán.
- **Ràng buộc Car Mode:** hộp thoại “Bật Car Mode?” (`TelegramPreviewActivity.kt` ~5843–5851) ghi “Có thể đổi lại trong ⋮ → Cài đặt → Thiết bị & màn hình”. Nếu ẩn trang đó, máy đã bật Car Mode sẽ không còn chỗ tắt → phải xử lý (bước S3).
- Trạng thái giả lập (`CompactSetting.EMULATE_SHANLING`, `CarSetting.EMULATE_FYT_160/240`, `MeloScreenPolicy.loadEmulation != NONE`, `loadPreviewCarMode`) cũng chỉ tắt được ở 2 trang sẽ ẩn → cùng xử lý ở S3.
- Thành phần UI có sẵn: `MeloComponents` (`roundedBackground`, `pill`, `text`, `vectorIcon`, `iconDrawable`, `sectionTitle`, `spacer`, `logo`), `MeloDesignTokens.Sizes/Palette` (2 bảng màu: Hiện tại, Teal sáng). Biểu tượng có sẵn trong `app/src/main/res/drawable`: `ic_tune`, `ic_play_arrow`, `ic_cloud`, `ic_music_note`, `ic_settings`, `ic_melovault_symbol`, `ic_phone_android`, `ic_chevron_right`, `ic_arrow_back`… Chưa có: info, mail, code/link, description, logout.
- Giấy phép: `THIRD_PARTY_NOTICES.md` ở gốc repo (có ALAC BSD-3 với **nghĩa vụ giữ thông báo bản quyền trong bản nhị phân**) — app hiện chưa hiển thị.
- Repo công khai: `https://github.com/niquitin/melovault` (nhánh mã: `source/feature-e93-03d-y700-offline-cockpit`).
- Test liên quan: `app/src/test/java/com/bsdung/musicassistantcar/data/telegram/MeloSettingsPolicyTest.kt`. Gate gần nhất: 200 suite, **2698 test**, 21 skipped, 0/0 mỗi biến thể.

## 3. Mục tiêu / ngoài phạm vi

**Mục tiêu**
- G1. Trang chính Cài đặt chỉ còn: thẻ tài khoản · GIAO DIỆN · THƯ VIỆN & ĐỒNG BỘ · Giới thiệu · Đăng xuất. Không còn nhóm Thiết bị & màn hình, Nâng cao (trừ khi đã mở khóa công cụ nhà phát triển).
- G2. Thiết kế nhóm thẻ có biểu tượng, thanh tiêu đề cố định, đúng cả 2 bảng màu, cỡ Gọn và chữ lớn.
- G3. Trang Giới thiệu: tên Nguyễn Dũng, phiên bản, mã nguồn, email góp ý, giấy phép mã nguồn mở, quyền riêng tư.
- G4. Không người dùng nào bị kẹt ở Car Mode / khung giả lập khi các trang đó bị ẩn.
- G5. Sửa nội dung bị thanh hệ thống che trên màn Cài đặt (và các trang con).
- G6. LD4: tách trang Màn Đang phát; mô tả Lời nhạc 1 dòng nhưng vẫn giữ công bố quyền riêng tư; dòng Đồng bộ ghi Bật/Tắt thật.

**Ngoài phạm vi (không làm):** đổi logic Gọn/Car Mode/giả lập/handle band (chỉ đổi nơi hiển thị); `MeloSyncSettingsActivity` (chỉ đọc trạng thái, không đổi màn); ⋮ menu; service, Media3, DB/schema, TDLib, sync protocol; package, signer, versionCode/versionName; khóa SharedPreferences hiện có (giữ nguyên tên và ý nghĩa); Personal flavor; SB-1 màn Hàng đợi (chỉ ghi chú nếu cùng nguyên nhân, không sửa ở đây); cache/“xóa bộ nhớ đệm”; đổi ngôn ngữ; kiểm tra cập nhật.

## 4. Phương án và lý do

- **Ẩn chứ không xóa (LD1):** giữ nguyên `renderDevices()`/`renderAdvanced()` và mọi policy; chỉ đổi điều kiện hiện. Lý do: công cụ QA/giả lập và Car Mode cho FYT 7870 vẫn cần cho chủ dự án; rollback rẻ.
- **Mở khóa 7 chạm:** cờ `dev_tools_unlocked` (Boolean) trong file prefs `MeloScreenPolicy.PREFS_NAME`, khóa mới, mặc định `false`. Đếm chạm trong bộ nhớ (không lưu), reset nếu 2 lần chạm cách nhau > 2 s. Chạm thứ 4–6 hiện Toast “Còn N lần nữa…”, chạm thứ 7 lưu cờ, Toast “Đã bật Công cụ nhà phát triển”. Đã mở thì chạm nữa → Toast “Công cụ nhà phát triển đang bật”. Trong trang công cụ có nút “Ẩn Công cụ nhà phát triển” (lưu `false`, quay về HOME). Logic đếm là hàm thuần trong policy để test JVM.
- **Chống kẹt (G4):** trên HOME, *dù đã ẩn công cụ*, vẫn hiện một dòng cảnh báo khi đang ở trạng thái không mặc định, để luôn có lối tắt:
  - `MeloCarPolicy.loadSetting(prefs) == ON` → dòng **“Car Mode · Đang bật”** (biểu tượng `ic_phone_android` hoặc mới `ic_car`), mở trang con chỉ có segmented Tắt/Bật + ghi chú (tái dùng phần Car Mode của `renderDevices`, không có nút khung thử).
  - Bất kỳ trạng thái giả lập nào (`EMULATE_SHANLING`, `EMULATE_FYT_*`, `loadEmulation != NONE`, `loadPreviewCarMode == true`) → dòng **“Đang thử khung giả lập · Tắt”**; chạm = đưa các giá trị đó về mặc định (`CompactSetting.AUTO`, `CarSetting.OFF` nếu đang EMULATE_FYT, `Emulation.NONE`, preview car = false), render lại.
  - Đổi câu hộp thoại Car Mode thành “Có thể tắt lại trong ⋮ → Cài đặt → Car Mode.”
  - Hàm thuần `MeloSettingsPolicy.attentionRows(compact, car, emulation, previewCar)` trả danh sách dòng cần hiện → test.
- **Khôi phục trạng thái:** `Page.fromName()` khi nhận `DEVICES`/`ADVANCED` mà công cụ đang ẩn → `HOME` (tránh mở lại trang ẩn qua `savedInstanceState`/`EXTRA_PAGE`). Trang `CAR` (mới) luôn được phép.
- **Thiết kế (LD2):** khung mới cho `render()`:
  - Gốc `LinearLayout` dọc: **thanh tiêu đề cố định** (nút ← trên trang con, tiêu đề, nền `palette.background`, đường viền dưới mảnh hiện khi đã cuộn) + `ScrollView` nội dung.
  - Insets: gốc nhận `systemBars | displayCutout`: top → padding thanh tiêu đề; trái/phải → padding gốc; bottom → padding ScrollView với `clipToPadding = false` để dòng cuối cuộn được lên trên thanh điều hướng. Sửa đúng nguyên nhân ở S1.
  - `settingsGroup(title?, rows)`: một thẻ `roundedBackground(p.surface, stroke = p.border)`, các dòng bên trong cách nhau bằng vạch 1 px `p.border` thụt lề bằng chiều rộng biểu tượng; tiêu đề nhóm chữ nhỏ in hoa `textSecondary` phía trên thẻ.
  - Dòng: ô biểu tượng tròn góc (`sizes.minTouch * 0.62`, nền `accent` độ đậm ~18%, biểu tượng tô `accent`) · tiêu đề (1 dòng, tối đa 2) · phụ đề tùy chọn (tối đa 2 dòng) · giá trị bên phải `textSecondary` · `›`. Dòng nguy hiểm (Đăng xuất) đứng riêng một thẻ, chữ `danger`, căn giữa, không biểu tượng.
  - Công tắc: tái dùng `switchRow` hiện có nhưng đặt trong thẻ nhóm.
  - Chiều cao chạm tối thiểu `sizes.minTouch`; `contentDescription` = tiêu đề + giá trị.
  - Trang con (Giao diện, Màn Đang phát, Giới thiệu, Giấy phép, Car Mode, Công cụ) dùng cùng khung và cùng kiểu thẻ.
- **Biểu tượng:** dùng sẵn `ic_tune` (Giao diện), `ic_play_arrow` (Màn Đang phát), `ic_cloud` (Đồng bộ), `ic_music_note` (Lời nhạc). Thêm vector Material Symbols (Apache-2.0, cùng nguồn với icon hiện có; kiểm nguồn của icon hiện có trước khi thêm): `ic_info`, `ic_mail`, `ic_code`, `ic_description`, `ic_shield` (quyền riêng tư), `ic_developer_mode`, `ic_car` (nếu chưa có). Vector 24 dp, `android:tint` không cố định (tô bằng code).
- **Giới thiệu (LD3)** — `Page.ABOUT`, nội dung trong policy mới `MeloAboutPolicy.kt` (hằng + hàm thuần để test):
  - Đầu trang: biểu tượng `ic_melovault_symbol`/`components.logo`, “MeloVault”, câu ngắn “Nghe nhạc từ kênh Telegram của bạn”.
  - Thẻ 1: **Phát triển bởi — Nguyễn Dũng**; **Phiên bản — `0.6.96-beta (vc121)`** (lấy từ `BuildConfig`, đây là dòng chạm 7 lần); **Mã nguồn — github.com/niquitin/melovault** (`ACTION_VIEW`; lỗi không có trình duyệt → sao chép link + Toast “Đã sao chép liên kết”).
  - Thẻ 2: **Góp ý qua email** → `Intent.ACTION_SENDTO` `mailto:` tới `MeloAboutPolicy.FEEDBACK_EMAIL`, tiêu đề “Góp ý MeloVault <versionName> (vc<code>)”, thân thư chỉ gồm phiên bản app + `Build.MODEL` + Android `Build.VERSION.RELEASE` (không có thông tin tài khoản/bài hát). Không có app email → sao chép địa chỉ + Toast. **Địa chỉ email do chủ dự án xác nhận (mục 10, CĐR1); chưa xác nhận thì phiên thực thi DỪNG ở bước này, không tự điền.**
  - Thẻ 3: **Quyền riêng tư** (trang con văn bản ngắn): đăng nhập/tải nhạc qua TDLib trực tiếp với Telegram; Yêu thích & Playlist đồng bộ qua chính tài khoản Telegram của bạn khi bật; Lời nhạc gửi tên bài/nghệ sĩ/album/thời lượng tới LRCLIB **chỉ khi bật** (câu `SETTINGS_SUBTITLE` cũ đặt nguyên văn ở đây); không có máy chủ riêng, không quảng cáo/theo dõi. Phiên thực thi phải đối chiếu từng câu với mã thật (grep mạng/HTTP) trước khi ghi; câu nào không chứng minh được thì bỏ.
  - **Giấy phép mã nguồn mở** (trang con): danh sách thành phần + giấy phép + link upstream (TDLib BSL-1.0, AndroidX/Media3 Apache-2.0, Kotlin Apache-2.0, ALAC BSD-3, LRCLIB — dịch vụ dữ liệu, Material Symbols Apache-2.0 …; lấy đúng từ `THIRD_PARTY_NOTICES.md` + `app/build.gradle`, không bịa phiên bản). Toàn văn BSD-3 của ALAC và văn bản `THIRD_PARTY_NOTICES` đưa vào `app/src/shareable/res/raw/third_party_notices.txt` (bản text thuần), nút “Xem toàn văn” hiện trong TextView cuộn được, chọn chữ được.
  - Cuối trang: “© 2026 Nguyễn Dũng”.
  - Dòng phiên bản ở cuối trang chính bỏ (đã có trong Giới thiệu); dòng Giới thiệu trên HOME có giá trị bên phải “0.6.96-beta”.
- **LD4**
  - Tách `Page.NOW_PLAYING`: chuyển khối “Màn Đang phát” (dòng 463–474) khỏi `renderAppearance()` sang trang riêng; `homeSummary()` không còn nói về màn Đang phát (kiểm lại test hiện có).
  - Lời nhạc: hằng mới `MeloLyricsUiPolicy.SETTINGS_SUBTITLE_SHORT = "Tìm lời trên LRCLIB khi mở tab Lời nhạc"`. Bật từ Cài đặt khi chưa đồng ý → **hiện hộp thoại đồng ý có toàn văn công bố** (tái dùng câu/hộp thoại của tab Lời nhạc nếu có hàm dùng chung; nếu không, AlertDialog với `SETTINGS_SUBTITLE` + “Đồng ý”/“Hủy”); chỉ lưu GRANTED khi bấm Đồng ý. Tắt thì lưu ngay như cũ. Giữ `SETTINGS_SUBTITLE` cũ (dùng ở hộp thoại và trang Quyền riêng tư).
  - Đồng bộ: giá trị bên phải “Bật”/“Tắt” theo `consent.automatic`, badge “Thử nghiệm” giữ dạng chip nhỏ dưới tiêu đề hoặc bỏ nếu chật (ưu tiên giữ). Đọc trạng thái bằng **đúng hàm/kho mà `MeloSyncSettingsActivity` dùng**, không gọi TDLib. Nếu việc đọc chạm Keystore/IO nặng: đọc trên luồng nền khi `onResume`, hiện “…” trong lúc chờ. Nếu không đọc được an toàn ngoài Activity đó → giữ mô tả cũ, ghi lý do trong báo cáo (không phải lỗi chặn).
  - Khi quay lại từ `MeloSyncSettingsActivity` (onResume) → render lại để giá trị Bật/Tắt cập nhật.

## 5. Bố cục đích

```
┌ Cài đặt ─────────────────────────┐   ← cố định, dưới thanh trạng thái
 ┌────────────────────────────────┐
 │ (T) Tài khoản Telegram          │
 │     29.996 bài · 29 nguồn       │
 │     Đồng bộ xong lúc 10:43      │
 └────────────────────────────────┘
 GIAO DIỆN
 ┌────────────────────────────────┐
 │ [≡] Giao diện & màu sắc       › │
 │     Mới · Teal sáng · Danh sách │
 │ ─────────────────────────────── │
 │ [▶] Màn Đang phát  Studio Deck › │
 └────────────────────────────────┘
 THƯ VIỆN & ĐỒNG BỘ
 ┌────────────────────────────────┐
 │ [☁] Đồng bộ Yêu thích & Playlist│
 │     Thử nghiệm          Bật  › │
 │ ─────────────────────────────── │
 │ [♪] Lời nhạc từ LRCLIB    [●]  │
 │     Tìm lời trên LRCLIB khi …   │
 └────────────────────────────────┘
 (chỉ khi cần) CHÚ Ý
 ┌────────────────────────────────┐
 │ [🚗] Car Mode         Đang bật › │
 │ [⚠] Đang thử khung giả lập  Tắt │
 └────────────────────────────────┘
 (chỉ khi đã mở khóa) NHÀ PHÁT TRIỂN
 ┌────────────────────────────────┐
 │ [⚙] Công cụ nhà phát triển    › │  → trang gồm: Thiết bị & màn hình, Nâng cao, nút Ẩn
 └────────────────────────────────┘
 ┌────────────────────────────────┐
 │ [i] Giới thiệu    0.6.96-beta › │
 └────────────────────────────────┘
 ┌────────────────────────────────┐
 │       Đăng xuất Telegram        │   (chữ đỏ, hỏi xác nhận như cũ)
 └────────────────────────────────┘
```

Chưa đăng nhập: không có thẻ tài khoản, không có Đăng xuất (như cũ).

## 6. Các bước thực thi

Chia **2 chặng** (chủ dự án có thể cho gộp): Chặng 1 = S0–S8 trên Ni; Chặng 2 = S9–S11 (Tiny build + Sony QA + Drive + hồ sơ). Cập nhật `*-bao-cao.md` và `*-ban-giao.md` sau mỗi bước.

| Bước | Việc | Xong khi |
|---|---|---|
| S0 | Kiểm phiên (CLAUDE.md mục 1): `git status/branch/rev-parse/log -5/diff --check` trong worktree; HEAD = commit kế hoạch trên `fa4cd09`. Chạy gate Ni baseline 1 lần (ra file). | Ghi số test baseline (kỳ vọng 2698/21/0/0) |
| S1 | **Tái hiện + chẩn đoán chữ bị che** trên Sony (APK vc121 đang cài): mở ⋮ → Cài đặt, cuộn hết xuống, chụp; cuộn giữa, chụp thanh trạng thái. Đọc code insets/theme (`Theme_MeloVault_Shareable`, `enableEdgeToEdge`/`setDecorFitsSystemWindows`) để tìm vì sao padding không có hiệu lực. Nếu Sony không tái hiện (Sony dùng cử chỉ?) thì thử bật điều hướng 3 nút? **Không** đổi cài đặt hệ thống Sony; thay bằng đọc `dumpsys window` insets và ghi CHƯA TÁI HIỆN, sửa theo phân tích mã. | Nguyên nhân ghi trong báo cáo, có ảnh trước |
| S2 | Policy (thuần, có test): `Page` thêm `NOW_PLAYING, ABOUT, LICENSES, PRIVACY, CAR, DEV_TOOLS`; `Page.fromName(name, devUnlocked)` chặn DEVICES/ADVANCED/DEV_TOOLS khi khóa; `homeRows(signedIn, devUnlocked, attention)`; `attentionRows(...)`; `DevTapCounter` (7 chạm, cửa sổ 2 s, chuỗi Toast); `MeloAboutPolicy` (tên, URL repo, email, `versionLine`, `feedbackSubject`, `feedbackBody(model, release)`, `mailtoUri`). Khóa prefs mới `dev_tools_unlocked`. | Test mới xanh |
| S3 | Chống kẹt: dòng Car Mode/giả lập trên HOME, trang `CAR`, hành động “Tắt giả lập”; sửa câu hộp thoại Car Mode trong `TelegramPreviewActivity`. | Test `attentionRows` + đọc lại diff |
| S4 | Khung mới + insets: thanh tiêu đề cố định, `settingsGroup`, dòng có biểu tượng, vạch chia, thẻ Đăng xuất; sửa nguyên nhân S1. Thêm vector icon. Áp cho mọi trang. | Build Debug xanh; xem trước trên Sony ở S9 |
| S5 | Trang Giới thiệu, Quyền riêng tư, Giấy phép (+ `res/raw/third_party_notices.txt`), 7 chạm, trang Công cụ nhà phát triển (chứa lối vào DEVICES, ADVANCED và nút Ẩn). | Test About + build xanh |
| S6 | LD4: tách `NOW_PLAYING`; Lời nhạc mô tả ngắn + hộp đồng ý khi bật; Đồng bộ Bật/Tắt + render lại `onResume`. | Test `homeSummary`/lyrics |
| S7 | Rà toàn bộ chuỗi tiếng Việt (chính tả, không cắt chữ ở cỡ Gọn), `contentDescription`, 2 bảng màu. | Danh sách chuỗi trong báo cáo |
| S8 | Gate Ni: lệnh test + assemble ở CLAUDE.md mục 4, thêm `-PmeloOpen=true :app:assembleShareableDebug` và `:app:assemblePersonalDebug` (đảm bảo không vỡ flavor khác). Commit mã. **Kết thúc Chặng 1**, cập nhật bàn giao. | BUILD SUCCESSFUL, 0 failures; ghi số test |
| S9 | Bundle SHA256 → Tiny clone cô lập build Debug có Telegram config → verify package/vc121/cert → cài `install -r` Sony `QV725WEQ54` → QA mục 7, chụp ảnh, ảnh tổng hợp `00_tong_hop.png`. Màn Sony OFF trong `finally`, ghi `SONY_SCREEN_END`. | Bảng QA đủ PASS/FAIL/BLOCKED |
| S10 | Drive Ni N owner-only: APK + ảnh; `*-apk-test.md`, `*-hinh-nghiem-thu.md`. | shared=false đã kiểm |
| S11 | Báo cáo, bàn giao, prompt nghiệm thu sơ bộ; dừng. | Commit hồ sơ |

## 7. QA Sony (XQ-AS52, dọc, giao diện Mới; màn ON chỉ khi chạy)

| # | Thao tác | Kỳ vọng |
|---|---|---|
| Q1 | ⋮ → Cài đặt (đã đăng nhập), chụp đầu trang; cuộn hết xuống, chụp | Không còn nhóm Thiết bị & màn hình / Nâng cao; nhóm thẻ có biểu tượng; tiêu đề cố định; **thẻ Đăng xuất nằm trọn trên thanh điều hướng, không chữ nào bị che** |
| Q2 | Chạm Giao diện & màu sắc → đổi Kiểu màu Hiện tại ↔ Teal sáng → quay lại; chạm Màn Đang phát | Trang Giao diện không còn khối Màn Đang phát; trang Màn Đang phát riêng; HOME đúng màu ở cả 2 bảng màu |
| Q3 | Tắt công tắc Lời nhạc rồi bật lại | Tắt ngay; bật → hộp đồng ý có đủ câu công bố; Hủy giữ Tắt, Đồng ý → Bật |
| Q4 | Chạm Đồng bộ → bật/tắt tự đồng bộ (nếu an toàn với dữ liệu: **chỉ đọc**, không bấm “Đồng bộ ngay”) → quay lại | Giá trị bên phải khớp Bật/Tắt. Nếu đổi trạng thái có rủi ro dữ liệu thì chỉ chụp giá trị hiện có |
| Q5 | Giới thiệu: chụp; chạm Mã nguồn (mở trình duyệt rồi Back); chạm Góp ý (mở trình soạn thư, **không gửi**, Back hủy); mở Giấy phép → Xem toàn văn | Tên Nguyễn Dũng, phiên bản đúng `BuildConfig`; intent đúng địa chỉ/tiêu đề; văn bản giấy phép cuộn được |
| Q6 | Chạm phiên bản 7 lần → về HOME thấy “Công cụ nhà phát triển” → mở Thiết bị & màn hình (xem, không đổi) → “Ẩn Công cụ nhà phát triển” | Toast đếm ngược; mục xuất hiện; ẩn lại được; xoay máy trên trang công cụ khi đã ẩn không mở lại trang ẩn |
| Q7 | (Chống kẹt) Mở công cụ → bật “Thử trong khung Shanling” → Ẩn công cụ → về HOME | Dòng “Đang thử khung giả lập · Tắt” hiện; chạm → về Tự động, dòng biến mất. Kết thúc QA để mọi giá trị về như trước QA |
| Q8 | Xoay ngang trang chính + chữ lớn 1.3 nếu đổi được **chỉ trong app**/không đổi cài đặt hệ thống → nếu không, ghi CHƯA KIỂM | Không chữ bị cắt/chồng |

Ghi lại giá trị prefs Gọn/Car/giả lập/handle band trước QA (chụp trang công cụ) và khôi phục đúng sau QA. Không đăng xuất, không bấm “Đồng bộ ngay”, không gửi email.

## 8. File dự kiến thay đổi

- `data/telegram/MeloUiLabActivity.kt` — khung mới, trang mới, 7 chạm, chống kẹt, insets.
- `data/telegram/MeloSettingsPolicy.kt` — Page, homeRows, attentionRows, DevTapCounter, homeSummary.
- `data/telegram/MeloAboutPolicy.kt` (mới).
- `data/telegram/MeloLyricsUiPolicy.kt` — thêm `SETTINGS_SUBTITLE_SHORT` (giữ hằng cũ).
- `data/telegram/TelegramPreviewActivity.kt` — **chỉ** câu hộp thoại Car Mode.
- `app/src/main/res/drawable/ic_*.xml` (mới), `app/src/shareable/res/raw/third_party_notices.txt` (mới).
- Test: `MeloSettingsPolicyTest.kt` (bổ sung), `MeloAboutPolicyTest.kt` (mới).
- Có thể: `MeloComponents.kt` nếu cần hàm dùng chung; ưu tiên hàm private trong Activity để không ảnh hưởng màn khác.

## 9. Rủi ro, rollback, điểm dừng

| Rủi ro | Giảm thiểu |
|---|---|
| Người dùng kẹt Car Mode/giả lập | Dòng CHÚ Ý trên HOME luôn hiện khi không mặc định (S3, Q7) |
| Lộ trang ẩn qua khôi phục trạng thái | `Page.fromName(…, devUnlocked)` + test |
| Rút gọn mô tả LRCLIB làm mất công bố quyền riêng tư | Hộp đồng ý toàn văn khi bật + trang Quyền riêng tư |
| Email cá nhân công khai trong app/repo | Chỉ dùng địa chỉ chủ dự án xác nhận ở CĐR1 |
| Câu quyền riêng tư sai sự thật | Đối chiếu mã; câu không chứng minh được thì bỏ |
| Sửa insets làm lệch màn khác | Chỉ sửa trong `MeloUiLabActivity`; nếu nguyên nhân nằm ở theme dùng chung → **DỪNG, ghi BLOCKED**, hỏi chủ dự án trước khi đổi theme |
| Đọc trạng thái đồng bộ chạm Keystore trên main thread | Đọc nền; không được thì giữ mô tả cũ |
| Vỡ Gọn/chữ lớn | Kích thước lấy từ `settingsSizes()`; QA Q8 |

Rollback: revert các commit mã của nhánh; prefs mới `dev_tools_unlocked` vô hại khi bản cũ bỏ qua. Không có migration dữ liệu.

**Dừng và báo** khi: cần đổi theme/Activity dùng chung khác; cần đổi package/signer/versionCode; CĐR1 chưa có địa chỉ email; gate test đỏ không do thay đổi này; Sony unauthorized/không có.

## 10. Điều chưa rõ (cần chủ dự án trả lời trước khi thực thi)

- **CĐR1 — Email góp ý:** dùng địa chỉ nào? (Email sẽ hiện công khai trong app và repo.) Phiên thực thi điền vào `MeloAboutPolicy.FEEDBACK_EMAIL`.
- CĐR2 — Tên hiển thị: dự kiến “Phát triển bởi **Nguyễn Dũng**” và “© 2026 Nguyễn Dũng”. Muốn thêm chức danh (ví dụ “BS. Nguyễn Dũng”) thì báo; mặc định giữ đúng “Nguyễn Dũng”.
- CĐR3 — Mặc định đã chọn nếu chủ dự án không nói gì: câu giới thiệu “Nghe nhạc từ kênh Telegram của bạn”; badge “Thử nghiệm” giữ.

## 11. Thao tác để chủ dự án tự thử (sau khi có APK)

1. ⋮ → Cài đặt: chỉ còn Giao diện, Thư viện & đồng bộ, Giới thiệu, Đăng xuất; cuộn hết xuống không chữ nào bị thanh điều hướng che.
2. Giới thiệu: thấy “Nguyễn Dũng”, phiên bản; chạm Mã nguồn mở GitHub; chạm Góp ý mở app email đúng địa chỉ (không cần gửi).
3. Chạm dòng Phiên bản 7 lần → quay lại Cài đặt có “Công cụ nhà phát triển” (Gọn, Car Mode, giả lập vẫn còn); bấm Ẩn để giấu lại.
4. Tắt rồi bật Lời nhạc: lúc bật hiện hộp đồng ý.
5. Đổi màu Hiện tại ↔ Teal sáng: màn Cài đặt đẹp ở cả hai.
