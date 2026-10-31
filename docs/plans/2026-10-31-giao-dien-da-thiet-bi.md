# Kế hoạch — giao diện phù hợp mọi loại thiết bị

TASK_ID: `2026-10-31-giao-dien-da-thiet-bi`. Nhánh `dot/2026-10-31-giao-dien-da-thiet-bi`; worktree `D:\Bots\_worktrees\MeloVault-giao-dien-da-thiet-bi`.
Baseline `94ab6b0` (nhánh tích hợp, 31/10/2026 20:09, sạch). Phần `app/` giống hệt `a2f0a88` (sau đó chỉ merge tài liệu).
Lập kế hoạch: Claude Code, 31/10/2026, theo định hướng chủ dự án ngày 31/10: “cố gắng làm phù hợp với mọi loại thiết bị”, giao lập kế hoạch cùng ngày.
Trạng thái: **KẾ HOẠCH — chưa code. Có 4 quyết định [CẦN XÁC NHẬN] ở mục 9.** Bàn giao: [`-ban-giao.md`](2026-10-31-giao-dien-da-thiet-bi-ban-giao.md).

## 1. Mục tiêu, cách tiếp cận và phạm vi

**Mục tiêu:** trên mọi cỡ màn Android thường gặp, các màn chính dùng được và không vỡ:
- điện thoại nhỏ, thường, lớn, cầm dọc và ngang;
- máy gập;
- tablet 7–13 inch;
- chia đôi màn hình;
- màn rất lớn;
- chữ hệ thống phóng to.

“Không vỡ” nghĩa là:
- không cắt chữ quan trọng;
- không có phần tử chồng lên nhau;
- không có nút nhỏ hơn 48 dp;
- không có nội dung nằm dưới thanh hệ thống hoặc tai thỏ;
- không cuộn ngang;
- không có nút điều khiển bị đẩy ra ngoài màn mà không cuộn tới được.

**Cách tiếp cận: khảo sát có hệ thống trước, sửa sau.** Yêu cầu chưa chỉ ra lỗi cụ thể, và chỉ còn một máy thật là Sony. Vì vậy:
1. **G1** khóa hành vi chọn bố cục hiện tại bằng bảng test.
2. **G2** khảo sát trên Sony theo ma trận cỡ màn (mục 4), ra danh sách lỗi có ảnh.
3. **G3** sửa các lỗi thuộc nhóm được phép (mục 5), theo quy tắc cố định. Không tự nghĩ thêm bố cục mới.

**Trong phạm vi:**
- các màn Shareable: Thư viện, Đang phát (Studio Deck), Tiếp theo, Nguồn, Cài đặt, menu ⋮, ô tìm kiếm có bàn phím;
- năm bố cục hiện có: Gọn, Điện thoại dọc, Tablet dọc, Hai khung, Car Mode.

**Ngoài phạm vi:**
- đổi ngưỡng chọn bố cục (600/700/840/960/1200 dp…) nếu chủ dự án chưa duyệt;
- thêm bố cục mới;
- chuyển sang Compose;
- đổi màu hoặc nhận diện Melo;
- màn đăng nhập (không đăng xuất được để xem; chỉ kiểm bằng giả lập nếu có);
- Personal;
- service, DB, TDLib, package, signer, versionCode.

## 2. Baseline và bằng chứng (đọc mã)

- `MeloScreenPolicy.chooseLayout` (`MeloScreenPolicy.kt:150`), theo thứ tự:
  1. Car Mode;
  2. Gọn khi **tấm màn** nhỏ (< 600 × 700 dp) và cửa sổ nhỏ;
  3. Gọn khi cửa sổ hẹp < 600 và thấp < 480 (chia đôi màn);
  4. Điện thoại dọc khi rộng < 600;
  5. Hai khung khi rộng ≥ 960;
  6. Hai khung khi ngang và cao < 560;
  7. còn lại Tablet dọc.
- `profileFor` (`:165`): Hai khung dùng cỡ Tablet khi cạnh nhỏ nhất ≥ 600, ngược lại dùng cỡ Điện thoại.
- `shouldOfferCarMode` (`:174`): mời Car Mode khi cầm ngang và rộng ≥ 1200 dp.
- Ngưỡng ghi là ước lượng spec, đo trên S25 Ultra 17/10 (`:10`).
- Insets:
  - `MeloStudioDeck.kt:138/182` dùng `setDecorFitsSystemWindows(false)` + systemBars | displayCutout;
  - có `WindowInsetsPolicy.HandleBand` (Cài đặt: Tự động / Luôn / Không);
  - `targetSdk 36`: Android 15 trở lên bắt buộc tràn viền, nên mọi màn phải tự chừa insets.
- Manifest: `TelegramPreviewActivity` có `resizeableActivity=true` và `configChanges` gồm orientation, screenSize, smallestScreenSize, screenLayout, density → app tự dựng lại lớp UI khi đổi cỡ, không tạo lại Activity.
- Có công cụ giả lập trong app (`MeloScreenPolicy.Emulation`: Shanling, FYT 160, FYT 240) nhưng **chỉ dùng cho Gọn/Car và màn xem trước thành phần** (`MeloUiPreviewActivity`), không dựng màn Điện thoại/Tablet thật → không đủ để khảo sát G2.
- Đã có test chính sách màn: `MeloScreenPolicyTest`, `ShortWindowLayoutPolicyTest`, `MeloCarLayoutPolicyTest`, `MeloCarLayoutSimulationTest`, `CarPaneLayoutPolicyTest`, `CockpitLayoutPolicyTest`. Chữ lớn đã có test công thức từ S6 UI/UX, nhưng chưa từng chụp thật ở cỡ chữ 1.3/1.5/2.
- Thiết bị: chỉ còn **Sony XQ-AS52** `QV725WEQ54` qua Tiny. Shanling đã bán. Y700/S25/FYT ngoài phạm vi QA theo quyết định 30/10 17:01.

## 3. Quyết định thiết kế

| # | Quyết định | Lý do |
|---|---|---|
| GD1 | **Giữ nguyên ngưỡng chọn bố cục.** G1 viết test khóa đúng kết quả hiện tại cho ma trận mục 4. Muốn đổi ngưỡng → ghi đề xuất ở báo cáo, chủ dự án duyệt riêng | Ngưỡng đã qua nhiều vòng nghiệm thu; đổi sẽ ảnh hưởng mọi máy |
| GD2 | Khảo sát bằng **đổi cỡ màn logic của Sony qua `adb shell wm size` / `wm density`** và **cỡ chữ `settings put system font_scale`**, bắt buộc khôi phục trong `finally`. **[CẦN XÁC NHẬN chủ dự án cho phép]** | Cách duy nhất ra bố cục Tablet/Hai khung/máy gập **thật** trên một máy. Lệnh không đụng dữ liệu, đảo ngược được |
| GD3 | Nếu GD2 không được phép: G2 chỉ khảo sát Sony ở cỡ gốc (dọc/ngang) + chia đôi màn + cỡ chữ (nếu được phép riêng), và ghi rõ các cỡ chưa kiểm | Không giả bằng chứng |
| GD4 | Nhóm lỗi được sửa tự động ở G3:<br>(a) cắt hoặc tràn chữ → `maxLines` + ellipsize hoặc xuống dòng theo token;<br>(b) chồng phần tử → sắp lại theo chiều dọc hoặc cho vùng phụ cuộn;<br>(c) vùng bấm < 48 dp → dùng `sizes.minTouch`;<br>(d) dưới thanh hệ thống/tai thỏ → áp insets như `MeloStudioDeck`;<br>(e) cuộn ngang hoặc phần tử ra ngoài màn → co ảnh hoặc vùng phụ trước, giữ hàng điều khiển và thanh tua (nguyên tắc S6 UI/UX);<br>(f) hộp thoại hoặc menu vượt màn → giới hạn chiều cao + cuộn | Các nhóm này sửa được mà không đổi kiến trúc |
| GD5 | Lỗi ngoài GD4 (cần bố cục mới, đổi ngưỡng, đổi luồng) → **không sửa**, ghi “Đề xuất” trong báo cáo cho chủ dự án | Không để model thực thi tự thiết kế |
| GD6 | Mỗi lỗi sửa một commit, có mã lỗi `UI-xx` từ G2. Nếu điều kiện tính được bằng hàm thuần (ví dụ bề rộng tối thiểu) thì thêm test JVM | Truy vết và rollback từng lỗi |

## 4. Ma trận khảo sát (G2)

Sony gốc: 1080 × 2520 px. Kiểm `wm density` mặc định đầu phiên, khoảng 420 → 411 × 960 dp.

Cột “Bố cục kỳ vọng” tính theo luật hiện tại (GD1); G1 khóa bằng test. Ghi chú `*` là điểm chủ dự án nên xem (mục 9).

| Mã | Mô phỏng | `wm size` / `wm density` | dp dọc → bố cục kỳ vọng | dp ngang → bố cục kỳ vọng |
|---|---|---|---|---|
| M1 | Điện thoại nhỏ cũ | 720x1280 / 320 | 360 × 640 → **Gọn*** (tấm màn < 600 × 700) | 640 × 360 → Hai khung (cỡ Điện thoại); rộng 640 ≥ 600 nên không vào Gọn |
| M2 | Điện thoại hẹp hiện đại | 1080x2340 / 480 | 360 × 780 → Điện thoại dọc | 780 × 360 → Hai khung (cỡ Điện thoại) |
| M3 | Sony gốc | reset / reset | ≈ 411 × 960 → Điện thoại dọc | ≈ 960 × 411 → Hai khung |
| M4 | Điện thoại lớn | 1290x2796 / 480 | 430 × 932 → Điện thoại dọc | 932 × 430 → Hai khung (cỡ Điện thoại) |
| M5 | Máy gập mở (màn trong) | 1812x2176 / 420 | ≈ 690 × 829 → Tablet dọc | ≈ 829 × 690 → Tablet dọc |
| M6 | Tablet 8 inch | 1200x1920 / 320 | 600 × 960 → Tablet dọc | 960 × 600 → Hai khung (cỡ Tablet) |
| M7 | Tablet ~Y700 | 1600x2560 / 320 | 800 × 1280 → Tablet dọc | 1280 × 800 → Hai khung + **mời Car Mode*** |
| M8 | Màn rất lớn | 3840x2160 / 320 | — | 1920 × 1080 → Hai khung + mời Car Mode* |
| M9 | Chia đôi màn (Sony gốc) | reset; mở chia đôi bằng thao tác đa nhiệm | cửa sổ ≈ 411 × ~470 → Gọn (luật chia đôi) | — |
| F1–F3 | Cỡ chữ 1.3 / 1.5 / 2.0 | `settings put system font_scale` trên M3 và M6 | Điện thoại dọc / Tablet dọc | Hai khung |

- Mỗi cấu hình chụp: Thư viện (cuộn giữa), Đang phát, Tiếp theo, Nguồn, Cài đặt (đầu trang), menu ⋮ của một dòng, ô tìm khi bàn phím mở.
- **Ngang chỉ chụp ở M2, M3, M6, M7, M8** để giới hạn khoảng 60 ảnh.
- Không phát nhạc mới. Nếu cần trạng thái Đang phát thì dùng bài đang tạm dừng sẵn, không đổi hàng chờ.
- Không đăng xuất, không đổi tài khoản, không bật chế độ máy bay.

## 5. Các bước thực thi

Ba chặng, mỗi chặng một phiên mới:
- **A** = G0–G2: test khóa + khảo sát; **không sửa mã giao diện**.
- **B** = G3: sửa + gate Ni.
- **C** = G4: Tiny build + chụp lại + hồ sơ.

**G0 — nhận việc.**
- Kiểm Git theo CLAUDE.md §1. Đọc mục 3–4 và câu trả lời mục 9 trong bàn giao.
- Kiểm APK trên Sony khớp source: package `app.melovault.player`, `versionName 0.6.96-beta`, kéo APK về tính SHA-256 so với `6e3d6b6e98de9974d399eea850ec691df1ab2324172ec3990fba483e6869a029` (Debug thường `a2f0a88`, cùng `app/` với baseline).
- Không khớp → ghi lại; G2 vẫn khảo sát bản đang cài nhưng nêu rõ bản nào.

**G1 — test khóa ma trận (Ni).**
- Thêm `MeloLayoutMatrixTest.kt` gọi `chooseLayout` / `profileFor` / `shouldOfferCarMode` / `panelCompact` cho mọi dòng M1–M9 (dọc + ngang) với kết quả kỳ vọng ở bảng mục 4.
- Test **không được sửa luật**. Nếu một dòng ra khác bảng thì bảng tính sai → sửa bảng trong báo cáo, ghi lý do, không sửa mã.
- Chạy test mục tiêu Debug + Release; commit.

**G2 — khảo sát Sony (Tiny, qua Desktop Commander).**
- `adb devices -l` + `getprop` đúng `QV725WEQ54` / XQ-AS52. Ghi `wm size`, `wm density`, `settings get system font_scale` gốc vào log.
- Màn ON chỉ khi chụp; `finally` luôn khôi phục rồi tắt màn và kiểm `Display State=OFF`:
  ```
  wm size reset
  wm density reset
  settings put system font_scale <gốc>
  settings put system user_rotation <gốc>
  settings put system accelerometer_rotation <gốc>
  ```
- Mỗi cấu hình: đặt cỡ → chờ app dựng lại (kiểm `dumpsys window` / ảnh) → chụp theo mục 4.
  - Xoay bằng `user_rotation` khi `accelerometer_rotation=0`, như các QA trước.
  - Đổi cỡ trong lúc app mở; app tự dựng lại theo `configChanges`. Nếu app không tự dựng lại thì ghi là lỗi `UI-xx` loại “đổi cỡ”.
- Chụp bằng `exec-out screencap -p`, kiểm header PNG. Ảnh lưu `docs/plans/artifacts/2026-10-31-giao-dien-da-thiet-bi/G2-<ts>/<Mx>-<dọc|ngang>-<màn>.png`.
- Kết quả: `docs/plans/2026-10-31-giao-dien-da-thiet-bi-khao-sat.md` có bảng lỗi `UI-01…`, mỗi dòng gồm:
  - cấu hình;
  - màn;
  - mô tả;
  - nhóm GD4 (a–f) hoặc “Đề xuất” (GD5);
  - mức (Chặn dùng / Xấu rõ / Nhỏ);
  - ảnh.
- Một ảnh tổng hợp cho mỗi nhóm cấu hình, cạnh dài ≤ 1600 px.
- Lỗi app (crash/ANR) khi đổi cỡ → ghi mức Chặn dùng kèm logcat (chỉ dòng lỗi, không PII).
- **Hết chặng A:** cập nhật báo cáo + bàn giao, đưa prompt chặng B, dừng. Chủ dự án có thể xem bảng lỗi trước khi giao chặng B và gạch bớt lỗi không muốn sửa.

**G3 — sửa (Ni).**
- Chỉ các lỗi `UI-xx` thuộc GD4 mức Chặn dùng / Xấu rõ (trừ lỗi chủ dự án gạch). Mỗi lỗi một commit `fix(ui-da-thiet-bi): UI-xx …`.
- File thường gặp: `MeloComponents.kt`, `MeloLibraryPane.kt`, `MeloStudioDeck.kt`, `MeloTabletScreen.kt`, `MeloPhoneScreen.kt`, `MeloQueuePane.kt`, `MeloBottomBar.kt`, `MeloSettings*`, `MeloDesignTokens.kt` (chỉ dùng token có sẵn hoặc thêm token tối thiểu có test).
- **Không** sửa `MeloScreenPolicy.chooseLayout/profileFor` hay các hằng ngưỡng. G1 phải vẫn xanh.
- Gate Ni:
  ```powershell
  .\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
  .\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
  .\gradlew.bat -PmeloOpen=true :app:assembleShareableDebug --console=plain
  .\gradlew.bat :app:assemblePersonalDebug --console=plain
  git diff --check
  ```
- **Hết chặng B:** commit, dừng.

**G4 — Tiny build, chụp lại, hồ sơ.**
- Bundle SHA-256 → clone cô lập → copy `local.properties` theo ngoại lệ E104 S11 → `reportTelegramConfig=true`.
- Build Debug thường; gate package / vc121 / cert `eecd3df7…fce8`. `install -r` lên Sony.
- Chụp lại **đúng cấu hình và màn của từng lỗi đã sửa** (trước/sau), cộng hồi quy M3 dọc/ngang.
- Hồ sơ: `-bao-cao.md`, `-apk-test.md`, `-hinh-nghiem-thu.md`, `00_tong_hop.png`; Drive owner-only **QA CANDIDATE — CHƯA NGHIỆM THU**; prompt sơ bộ; dừng.

## 6. Thao tác cho chủ dự án thử (sau G4)

1. Sony cầm dọc rồi ngang: Thư viện → Đang phát → Tiếp theo. Không cắt chữ, nút đủ lớn.
2. Cài đặt hệ thống → cỡ chữ lớn nhất → mở app. Tên bài, nút phát, thanh tua vẫn thấy; trả cỡ chữ.
3. Chia đôi màn MeloVault với một app khác. App chuyển Gọn, dùng được.
4. Nếu có máy khác (tablet / máy gập / điện thoại khác): mở Thư viện và Đang phát, chụp gửi agent.
5. Xem bảng lỗi khảo sát và mục “Đề xuất”; chọn lỗi cần làm tiếp.

## 7. Tiêu chí hoàn tất

- G1 xanh; test Debug/Release 0 failures/errors; assemble thường/open/Personal xanh; `diff --check` sạch.
- G2 có bảng lỗi đủ cấu hình, hoặc ghi rõ cấu hình nào chưa kiểm vì GD3.
- Mọi lỗi GD4 mức Chặn dùng / Xấu rõ đã sửa và có ảnh trước/sau, hoặc có lý do không sửa.
- Thiết bị đã được khôi phục: `wm size`, `wm density`, `font_scale`, xoay về giá trị gốc (ghi trong báo cáo); màn OFF.
- Trạng thái chính thức **CHỜ CHỦ DỰ ÁN NGHIỆM THU**.

## 8. Rủi ro, rollback, điểm dừng

**Rủi ro**
- `wm size` / `wm density` làm launcher và các app khác hiển thị lạ trong lúc khảo sát. Chưa khôi phục (mất kết nối ADB giữa chừng) → chủ dự án có thể phải tự chạy `wm size reset` hoặc khởi động lại máy. Giảm rủi ro:
  - **mỗi cấu hình khôi phục ngay** trước khi sang cấu hình sau;
  - ghi lệnh khôi phục vào báo cáo trước khi bắt đầu.
- Sony mô phỏng tablet nhưng chạm, cảm ứng và kích thước vật lý không giống tablet thật → ghi là “mô phỏng trên Sony”, không thay cho QA tablet thật.
- Sửa layout dùng chung có thể làm Gọn/Car xê dịch → hồi quy bằng test có sẵn + ảnh M1 (Gọn).

**Rollback:** revert từng commit `UI-xx` (độc lập). Không có dữ liệu hay migration.

**Dừng và ghi BLOCKED khi:**
- HEAD/cây lạ;
- GD2 chưa được phép và cũng không chọn GD3;
- ADB mất kết nối khi thiết bị đang ở cỡ giả lập (khôi phục trước, rồi mới báo);
- lỗi cần đổi ngưỡng / bố cục mới / service / schema;
- APK trên Sony khác nguồn mà không xác định được bản.

## 9. Câu hỏi cho chủ dự án

| # | Câu hỏi | Mặc định |
|---|---|---|
| 1 | Cho phép đổi tạm `wm size` / `wm density` / `font_scale` / xoay trên Sony để khảo sát, khôi phục ngay sau mỗi cấu hình (GD2)? | **Cần trả lời.** Chưa trả lời thì G2 làm theo GD3 |
| 2 | Điện thoại nhỏ 360 × 640 dp hiện ra bố cục **Gọn** (thiết kế cho Shanling). Giữ? | Giữ, ghi “Đề xuất” nếu khảo sát thấy xấu |
| 3 | Tablet/màn lớn cầm ngang (≥ 1200 dp) được **mời Car Mode** một lần. Giữ? | Giữ |
| 4 | Sau G2, chủ dự án muốn duyệt bảng lỗi trước khi sửa, hay để chặng B sửa ngay theo GD4? | Sửa ngay theo GD4; chủ dự án vẫn có thể gạch lỗi trong bàn giao trước khi giao chặng B |

Đã dừng. Mở phiên mới, chọn model thực thi tiết kiệm hơn theo lựa chọn của chủ dự án.
