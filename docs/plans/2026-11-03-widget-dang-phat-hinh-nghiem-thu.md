# Ảnh QA — Widget Đang phát

## Bộ ảnh hiện tại · 06/11/2026 · **QA CANDIDATE — CHƯA NGHIỆM THU**

Source `be61487`, Sony XQ-AS52 `QV725WEQ54`, launcher Xperia. [Thư mục ảnh riêng tư](https://drive.google.com/drive/folders/134rSpPWMrzsURARUMaeEYKGjn89UNofp) · [Thư mục QA](https://drive.google.com/drive/folders/1rVqHjK2cuTqXV-6D1GBy9jmywbtjNwZr). Thư mục và ảnh tổng hợp đã kiểm: một quyền duy nhất, owner `ntdung.md@gmail.com`. Cần đăng nhập đúng tài khoản; không public.

| Mốc | Liên kết | Byte |
|---|---|---:|
| Ảnh tổng hợp 8 mốc (SHA-256 `F1819126E8CB4747EE3DBA2AB0CCAE7BDEFF4CBBC35A4FDBCC09BE5CBD3EC29D`) | [00_tong_hop.png](https://drive.google.com/file/d/160jhV7kqhhthtYaE1VxwK--KgacBdCo2/view?usp=drivesdk) | 1.275.492 |
| Q1 bộ chọn widget | [05-picker-preview.png](https://drive.google.com/file/d/1fbu-sfFgq3c3PfU_d2igUSiDXf-Ow2cS/view?usp=drivesdk) | 127.899 |
| Q1 trống, nút tắt | [06-added.png](https://drive.google.com/file/d/1q1Lkgg54gqmey7Fi4skRkTfzHptQohMB/view?usp=drivesdk) | 1.492.117 |
| Q2 tạm dừng từ widget | [12-q2-paused.png](https://drive.google.com/file/d/1a0KWUwF0p2jJBTmD8s60twnqtu4Q0TCa/view?usp=drivesdk) | 1.494.029 |
| Q3 Tiếp 2 lần sang B chưa tải | [30-q3-uncached-B-1s.png](https://drive.google.com/file/d/1fcxkF1WOXOq9GNe6TbmKvysN4wobxabk/view?usp=drivesdk) | 1.494.059 |
| Q3 bìa + ca sĩ thật, bài cuối tắt Tiếp | [63-q3-local-last.png](https://drive.google.com/file/d/13coV-7vTuky4MWcAtYgfKRWuKCJnnTV7/view?usp=drivesdk) | 1.516.454 |
| Q5 kéo rộng, đổi bố cục | [44-q5-wide.png](https://drive.google.com/file/d/1xpQ5qaxedCt-zMdIPU9P32DDEpZA7mMq/view?usp=drivesdk) | 1.265.209 |
| Q6 thêm lại, nhạc không đứt | [51-q6-readded.png](https://drive.google.com/file/d/1Y_CjsfPA9KSL4pGL0yObQ6j5A-Dc5yRY/view?usp=drivesdk) | 1.490.324 |
| Bấm widget cũ: trống, không tự phát | [57-q6-after-stale-tap.png](https://drive.google.com/file/d/166lV0tFa0quxFvoD0AZKVi_Bk2pgMA4e/view?usp=drivesdk) | 1.494.783 |

Không đưa lên Drive ảnh có bìa nhạc có thể là người thật (`17`, `19`, `22`, `25`). Các ảnh đó cùng toàn bộ log ở `docs/plans/artifacts/2026-11-03-widget-dang-phat/rework-20261106/sony/` trên Ni (không commit) và `C:\Bots\_qa_temp\widget-20261106-rework\` trên Tiny. Q4 mở Studio Deck đang ở nhóm ảnh này nên không có link Drive; kết quả ghi trong [báo cáo](2026-11-03-widget-dang-phat-bao-cao.md).

Ảnh không chứng minh âm thanh: **CHƯA XÁC NHẬN ÂM THANH THỰC**. `SONY_SCREEN_END`: Display State=OFF.

## Bộ ảnh lịch sử 03/11/2026 (AVD, source `49ed94c`)

03/11/2026 · source `49ed94ccbd73abb2d9443828ede26c205e1e9e56` · **QA CANDIDATE — CHƯA NGHIỆM THU**.

- [Thư mục Drive Ni N owner-only](https://drive.google.com/drive/folders/1n7KhjK_3mr3LlBJ74ZHaD6ZhrVuO7K8g).
- [Ảnh tổng hợp 6 khung](https://drive.google.com/file/d/1L5oFn0s003lQXao2Ibt3mWwxOb5ucuY2/view?usp=drivesdk), 960×1460px, đã mở kiểm trên Ni; authenticated fetch ảnh từ Drive thành công.
- Tất cả 7 PNG đã đọc metadata lại: shared=false, một quyền owner Ni N, bytes khớp file Ni. SHA256 dưới đây tính từ bản Ni trước upload; connector không trả checksum từ xa nên không ghi là đã đối chiếu SHA256 Drive.
- Ảnh thật từ AVD riêng Android36 / Pixel Launcher / emulator-5586; không phải Sony hoặc thiết bị thật. Không có tài khoản cá nhân/Telegram PII.
- Phạm vi ảnh: tìm/preview/thêm widget trống, mở app không tự phát, resize4→3 cột, instance thứ2. **Q2–Q4 playback/bìa thật và Q6 khi có bài BLOCKED** vì thiếu phiên QA đăng nhập. Không dùng các ảnh này làm chứng cứ bìa thật hoặc nút phát đã hoạt động.
- Chưa xác nhận âm thanh thực, next Telegram chưa tải, API30/31, chữ lớn/TalkBack, đồng bộ hai instance khi đổi bài. APK trên Drive bị chặn tải403; xem [hồ sơ APK](2026-11-03-widget-dang-phat-apk-test.md).

| Khung | Ảnh đã xác minh | Bytes | SHA256 bản Ni |
|---|---|---:|---|
| Tổng hợp | [00_tong_hop.png](https://drive.google.com/file/d/1L5oFn0s003lQXao2Ibt3mWwxOb5ucuY2/view?usp=drivesdk) | 658903 | `017892BEAA6200434CE280F7CA0426B77E84A9AF105F5098A9281A8CA6F85B46` |
| 1 | [07-picker-search.png](https://drive.google.com/file/d/1ztrfhi272EIvZd4KrJpTQVNA6LTVC2lD/view?usp=drivesdk) | 227062 | `3B0763375BF400D37E398D47B68208CB8F5324B9BE0B37ED217C93A906BC0003` |
| 2 | [08-widget-preview.png](https://drive.google.com/file/d/1pHFzCgiCn8DfsEvybIv2t0bSsQs-3YGZ/view?usp=drivesdk) | 198689 | `0C65F532DDFAACDCFE19BF423FC3AABB6784F5E18360CC4720E75D9EF5BC69A4` |
| 3 | [09-widget-empty.png](https://drive.google.com/file/d/1n02abnZYdgijZui_NP8U5_d1HluGfJ64/view?usp=drivesdk) | 1085621 | `2D26669FC00E3823ED51D7324205A225A924D5B213A97C8D2EE64422D424FB43` |
| 4 | [10-open-empty-app.png](https://drive.google.com/file/d/1qFaRV-HGkWryaLZMt9WotmyeWbXPEi3M/view?usp=drivesdk) | 106798 | `36417150D0DEE84F259805DA85A757359B7F54D29820FD63FEE2C958180287D2` |
| 5 | [15-widget-resize.png](https://drive.google.com/file/d/1hRgf7-ij0wdwL4lgKuQUOHAroytiqnIk/view?usp=drivesdk) | 1160957 | `36DBE4B2EEA79BF78D4872673C95F237801AD5FC2913F85E60ACB8F56E60E15A` |
| 6 | [17-second-widget.png](https://drive.google.com/file/d/1SIT0bvio6JVM6Uo-ouPdxJRBhlVotor8/view?usp=drivesdk) | 1044030 | `FDC2035C324708C0578B9A9A9B12CF63CBDF794D7508090F9ACCB3940634C8CB` |

Ảnh gốc/private và log nằm tại `docs/plans/artifacts/2026-11-03-widget-dang-phat/` (không commit); metadata/hash manifest `b-drive-verification-20261103.json`, `b-delivery-file-hashes-20261103.json`. Liên kết yêu cầu đúng tài khoản Ni N; không đổi quyền public.
