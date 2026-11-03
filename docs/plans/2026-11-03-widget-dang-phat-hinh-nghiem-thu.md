# Ảnh QA — Widget Đang phát

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
