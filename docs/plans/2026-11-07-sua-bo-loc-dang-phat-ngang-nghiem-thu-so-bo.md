# Nghiệm thu sơ bộ — Sửa nút Bộ lọc, chip thể loại và màn Đang phát khi xoay ngang

**SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN** cho phạm vi D1–D4 trên Sony. Có **một lỗi nhỏ không chặn (N1)** để chủ dự án quyết định. Đây không phải nghiệm thu chính thức.

| Mục | Giá trị |
|---|---|
| Vòng / người kiểm | Vòng 1, phiên mới, Claude Code, Opus 5.5, 07/11/2026 |
| TASK_ID / nhánh | `2026-11-07-sua-bo-loc-dang-phat-ngang` / `dot/2026-11-07-sua-bo-loc-dang-phat-ngang` |
| Baseline | `f82185ba2ef17fce97d4680de2cd2dc86b2e1b4a` |
| HEAD lúc kiểm | `3423a436995de239913d56c15ba082f207254c09` (sau `f054637` chỉ thêm tài liệu) |
| Source APK | `f05463709faf532949d58a31ef7a46c67211cfd6`; `git diff 233954f f054637 -- app` rỗng |
| APK | SHA-256 `7FB940219704E78A754971D9773DE5CE93CE18422277C53262EC144890FB7A24`, 33.910.894 B, vc122 |

## 1. Kiểm tự chạy

| Kiểm | Kết quả |
|---|---|
| Git | Cây sạch, đúng nhánh, HEAD khớp, `git diff --check f82185b HEAD` sạch; commit cuối cách hơn 1,5 giờ, không thấy agent khác |
| Diff | Đọc toàn bộ diff `app/` và `docs/ui-spec.md`. Không đụng service, Media3, DB, TDLib, package, signer, versionCode |
| Hash APK | Bản Ni, bản Drive mount `Z:` và bản pull từ Sony đều khớp SHA-256 và số byte |
| Clone build Tiny | HEAD `f054637` |
| Drive (connector) | Thư mục và APK: một quyền duy nhất, owner; kích thước khớp |
| Test | Đếm lại XML `app/build/test-results` trên Ni: Debug và Release mỗi loại 207 suite, 2.799 test, 21 skipped, 0 failures, 0 errors. Không chạy lại Gradle vì không có lý do |
| Sony | `QV725WEQ54`, XQ-AS52, `device`, vc122 / `0.6.97-beta`, font 1.0 |

## 2. Kết quả theo phạm vi

| Mục | Đọc mã | Thử trên Sony ở phiên này | Kết luận |
|---|---|---|---|
| D1 bỏ ☰ | Nút chỉ vẽ khi `onFilter != null`; Teal vẫn truyền `openFilterDialog`; Gọn không truyền | 0 node `Bộ lọc` ở Thư viện và **Đã tải** (lấp chỗ báo cáo chỉ dựa trên ảnh) | Không thấy lỗi |
| D3 chip | `toggledGenre` đọc ô tìm lúc chạm; không đổi đường Teal `Chillout ✕` | Tab Đã tải: Mixset bật, **0 bài**, chip vẫn hiện; chạm lại thì ô tìm trống, danh sách về đủ | Không thấy lỗi |
| D4 EQ/Hẹn giờ | `isBaselineAligned=false` | Dọc: `[42,205][540,331]` và `[540,205][1038,331]`. Ngang: `[716,79][842,205]` và `[863,79][1062,205]`, cùng top/bottom | Không thấy lỗi |
| D2 ngang thấp | `landscapeCompact` đúng mốc < 500 dp và font ≤ 1,3; dọc và ngang cao giữ nguyên thứ tự, lề, `maxLines=2` theo đọc diff | Bìa 279 px `[42,226][321,505]`, Đảo/Lặp/Lưu đủ 126 px với tên 1 dòng và tên 3 dòng khi đang phát; chỉ thiếu ở N1 | Không chặn, xem N1 |
| Nhãn dài trên hàng nút đóng (thêm) | Chip theo độ rộng chữ, một dòng | `EQ · Tăng treble` và `Hẹn giờ 14:26` cùng lúc: vừa hàng, còn chữ "Đang phát" | Không thấy lỗi |
| Xoay dọc ↔ ngang khi tạm dừng | — | Đúng dạng, 0 dòng FATAL ở log crash | Không thấy lỗi |

Dựa trên bằng chứng của phiên thực thi, không làm lại: Q2 trong kênh, Q5 bật/tắt Đảo/Lặp/Lưu, Q6 vị trí phát khi xoay lúc đang phát.

## 3. Lỗi tìm thấy

### N1 — Tên 3 dòng cộng dòng trạng thái làm hàng Đảo/Lặp/Lưu bị cắt 16 px (nhỏ, không chặn)

- **Vị trí:** `MeloStudioDeck.buildLandscape` (`addTitleBlock(titleBox, …)`, dòng 275) và `requestArtFit` (dòng 416–417). `MeloPlayFeedbackView` nằm trong khối tên, nhưng cỡ bìa chỉ tính theo phần ngoài `mediaRow`, nên khối tên cao hơn bìa thì đẩy hàng nút xuống. Kế hoạch mục 3 tính "khối tên 3 dòng khoảng 245 px" chưa cộng dòng trạng thái.
- **Tái hiện:** phát một bài đã tải có tên đủ 3 dòng, mở Đang phát, xoay ngang, bấm tạm dừng.
- **Kỳ vọng (D2, "không cần cuộn"):** Đảo/Lặp/Lưu lộ đủ 126 px.
- **Thực tế:**

| Trạng thái | Khối tên | Hàng Đảo/Lặp/Lưu lộ ra |
|---|---|---|
| Đang phát | 245 px (`243`–`488`), thấp hơn bìa 279 px | `526`–`652`, đủ 126 px |
| Tạm dừng | 295 px (`226`–`521`), cao hơn bìa 16 px | `542`–`652`, **110/126 px, mất đáy** |

- Vuốt cột trái 16 px thì hàng nút lộ đủ `[42,526][368,652]`. Hàng nút cũng nhảy 16 px mỗi lần đổi giữa phát và tạm dừng. Không ảnh hưởng tên 1–2 dòng.
- **Ảnh riêng tư:** `docs/plans/artifacts/2026-11-07-sua-bo-loc-dang-phat-ngang/sobo_33_ten3dong_tamdung.png` (không commit, có bìa nhạc). Toàn bộ ảnh phiên này ở `C:\Bots\_qa_temp\sobo-suabolocf054637\shots\` trên Tiny.
- **Nếu chủ dự án giao REWORK (phiên thực thi mới):** có thể ẩn dòng trạng thái ở dạng ngang thấp hoặc giới hạn số dòng tên khi có dòng trạng thái. Phiên sơ bộ không sửa.

## 4. Quan sát, không phải lỗi

- Bật hẹn giờ hoặc EQ làm chip giãn nên vị trí EQ dịch sang trái (khoảng 92 px với `Hẹn giờ 15:00`). Phiên này từng chạm hụt vì tọa độ cũ; người dùng nhìn nút thì không bị ảnh hưởng.
- Khi bàn phím mở, hàng chip thể loại bị ẩn bởi cơ chế thu gọn UI-01 có sẵn, nên phải đóng bàn phím mới bỏ chip được. Không do nhiệm vụ này.

## 5. Chưa kiểm

- **CHƯA XÁC NHẬN ÂM THANH THỰC.** Media3 PLAYING không chứng minh có tiếng.
- Máy 16:9, tablet, xe, chia đôi cửa sổ, chữ lớn, Teal, Gọn: chỉ đọc mã. Không đổi `font_scale`, `wm size` để giả lập vì đó là thay đổi cài đặt máy.
- **Suy luận từ mã, chưa đo trên 16:9 (1080p, 420 dpi):** cột trái khoảng 750 px nên cột chữ hẹp hơn nhiều so với 699 px ở Sony; N1 có thể gặp với tên ngắn hơn. `EQ · Tăng treble` cộng hẹn giờ đang chạy cần khoảng 761 px, nên chip Hẹn giờ có thể bị rút gọn "…".
- **Suy luận từ mã, chưa thử:** lỗi phát có nút "Thử lại" và tối đa 3 dòng thông báo sẽ đẩy hàng nút xuống nhiều hơn N1; cột trái vẫn cuộn được.
- Màn dọc không so được với APK baseline trên cùng bài; ghi chú lệch 8 px ở Q6 vẫn là suy luận. Tải APK bằng trình duyệt chưa thử.

## 6. Trạng thái Sony khi dừng

Đã thử bằng bản đang cài, không cài gì. EQ đã trả về Tắt / Phẳng (dump xác nhận). Hẹn giờ đã tắt. Bài đang chọn và hàng đợi **khác trước phiên** vì phải phát một bài tên dài; nhạc tạm dừng, state=2. Tab đang mở: Thư viện. `user-rotation free`. `SONY_SCREEN_END`: Display State=OFF.

## 7. Chủ dự án tự thử

1. Thư viện và Đã tải: ô tìm không còn ☰ ở cuối.
2. Chạm Mixset, chạm lại Mixset: lọc bỏ, ô tìm trống, danh sách về đủ.
3. Mở Đang phát, xoay ngang: bìa cạnh tên, EQ và Hẹn giờ thẳng hàng với nút đóng, đủ Đảo/Lặp/Lưu.
4. Mở một bài tên rất dài rồi bấm tạm dừng: xem cạnh dưới hàng Đảo/Lặp/Lưu (N1) và quyết định có cần sửa không.
5. Xoay dọc lại: màn Đang phát như cũ, nhạc vẫn chạy; nghe thử có tiếng.

APK và ảnh: [apk-test](2026-11-07-sua-bo-loc-dang-phat-ngang-apk-test.md) · [hồ sơ ảnh](2026-11-07-sua-bo-loc-dang-phat-ngang-hinh-nghiem-thu.md).
