# E105 — Chỉ mục APK kiểm tra 28/10/2026

**QA CANDIDATE — CHƯA NGHIỆM THU. BLOCKED — CHƯA BÀN GIAO BẢN TẢI DÙNG ĐƯỢC.**
Đã cài kiểm tra bản riêng trên Shanling/Sony; không khuyến nghị dùng đồng bộ thường xuyên khi 2 pending trên Shanling chưa giải thích được. Hiện hai máy còn 2 bài nền; bài thử chưa khôi phục.

[Thư mục riêng tư Ni N: APK và ảnh QA](https://drive.google.com/drive/folders/17TenekE6J19SJE848PRxTkkD7d48vuB9).
Connector kiểm thư mục và cả 4 APK: shared=false, chỉ owner Ni N; kích thước Drive khớp local. **Tải lại bản riêng Release bị HTTP403 `cannotDownloadAbusiveFile`** (Drive đánh dấu malware/spam). Chưa xác minh SHA-256 bản tải lại; chưa kiểm khả năng tải ba APK còn lại. Không trình bày các URL dưới đây như liên kết tải đã dùng được, không bỏ qua cảnh báo Drive.

| APK | URL thực đã kiểm metadata | Bytes | SHA-256 local/Tiny |
|---|---|---:|---|
| Riêng Debug | [Ứng viên Debug](https://drive.google.com/file/d/1h_iMtM1rGeKsVWrhoBPOYAfQItU1pNDA/view?usp=drivesdk) | 33462212 | `C9F6D359717FB375CB84B04004991F38A34853512444453A317F7B0B8A8E3A83` |
| Riêng Release | [Ứng viên Release — Drive đang chặn tải](https://drive.google.com/file/d/1ol3sJTf76DaFicw4ck6F84Pb_sAmRDe4/view?usp=drivesdk) | 30703982 | `3D889FF7141C1C8D18EBCDE4CE5BA02E38EF19A0E701282E4416489E018CDB85` |
| Open Debug | [Ứng viên open Debug](https://drive.google.com/file/d/1sji7GVvjMVpIU3OcsEjAz6t-0z1Tj8Pd/view?usp=drivesdk) | 33462216 | `3DEA34FB9D22B6AE5623D52B6CC43D5B85471F5CA7498AE83354F68BB74C8413` |
| Open Release | [Ứng viên open Release](https://drive.google.com/file/d/136q4dNUj3Jm3RoVV4PjfEcWac6eEqIoT/view?usp=drivesdk) | 30703990 | `9E672F614EE8982FE3BD58FF16412AB5B7AC4F6C4FA411ED1C5B4B8113F49DC0` |

| Thuộc tính | Kết quả |
|---|---|
| Git source | `449ff72004c80aa223c66220f90d6af20bc494a4`, dot/E105-DONG-BO-PLAYLIST |
| Bản riêng | app.melovault.player / vc121 / 0.6.96-beta; config nhúng=true |
| Bản open | app.melovault.player.open / vc121 / 0.6.96-open; config nhúng=false đúng thiết kế; runtime config/auth chưa có |
| Certificate SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` |
| ABI | arm64-v8a |
| Build Tiny | C:\Bots\MeloVault-E105-Audit-20261028-0652 |
| Artifact Tiny | C:\Bots\_qa_temp\E105-449ff72-audit-20261028\E105-449ff72-vc121-<private hoặc open>-<debug hoặc release>.apk |
| Artifact Ni | docs/plans/artifacts/E105-DONG-BO-PLAYLIST/audit-20261028-0652/ (cùng tên APK trên) |
| Kiểm độc lập | aapt package/vc/ABI, apksigner verify, SHA-256/bytes Tiny=Ni; config boolean và quét hash open |
| Test Ni | Mỗi biến thể 2144 tổng / 2123 thành công / 21 bỏ qua / 0 lỗi; build đủ 4 biến thể |
| Cài thực tế | Shanling: riêng Debug→Release, open Debug→Release; Sony: riêng Debug; tất cả install -r Success |
| QA cloud thật | ADD Shanling Debug→Sony Debug, REMOVE Sony Debug→Shanling Release; giữ hai bài nền; khôi phục chưa xong |

Không giảm versionCode, không đổi chữ ký, không uninstall/clear data. Sau vc121 không cài APK vc119/120 để rollback. Giữ APK trước cài để đối chiếu, không coi đó là bản được phép downgrade.
Chưa xác nhận âm thanh thật, open đã đăng nhập, toàn ma trận QA, kết quả khôi phục và 2 pending. Không công bố Beta/Stable, không merge/push.
Xem [biên bản hiện tại](E105-DONG-BO-PLAYLIST-nghiem-thu.md) và [ảnh QA](E105-DONG-BO-PLAYLIST-hinh-nghiem-thu.md).
