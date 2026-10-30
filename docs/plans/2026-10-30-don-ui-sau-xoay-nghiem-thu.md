# Nghiệm thu — 2026-10-30-don-ui-sau-xoay

**Trạng thái: PASS có giới hạn — chủ dự án xác nhận ĐẠT ngày 30/10/2026 18:11 +07** (tin nhắn "Đạt" trong chat, ngay sau bàn giao `180e265`; ngữ cảnh chỉ có một bản: source `8bf9d03`, APK Debug `E6EC8773…E92C`). Phạm vi PASS: D1–D3 và QA Sony Q1–Q6 như bảng dưới. Không bao gồm các mục ở "Giới hạn còn mở". Chưa merge/push — cần chỉ thị riêng.

(Trạng thái trước đó: QA CANDIDATE — CHƯA NGHIỆM THU.)

- Bản: source `8bf9d03`, APK Debug vc121 `0.6.96-beta`, SHA-256 `E6EC8773C8F958301CCE9CB9CFAD4366F77A835A4D5E7A2055F9C1ED1B08E92C`. Link: [apk-test](2026-10-30-don-ui-sau-xoay-apk-test.md) · [hình](2026-10-30-don-ui-sau-xoay-hinh-nghiem-thu.md) · [báo cáo](2026-10-30-don-ui-sau-xoay-bao-cao.md).
- **Quyết định chủ dự án 30/10/2026 17:01:** bỏ QA1 tablet và QA6 Shanling M3; mọi QA thiết bị chỉ trên Sony XQ-AS52 `QV725WEQ54` qua Tiny. Tablet/Shanling: **ngoài phạm vi theo quyết định chủ dự án**.

## Kiểm tra kỹ thuật của agent (không thay quyết định chủ dự án)

| Mục | Kết quả agent |
|---|---|
| D1 Release không có `MeloAnchor` | Đạt (dex 0 chuỗi; logcat Release 0 dòng, có đối chứng) |
| D2 lưu vị trí ngay sau mở app | Đạt (test JVM đỏ→xanh; Sony 0 `save_rejected activeOk=false`, xoay ×3 cùng bài) |
| D3 không khoảng trống sau xoay | Đạt, chỉ bằng chứng thiết bị (ΔY 0, Current và Teal) |
| Hồi quy R1c (Q3), Đảo (Q4), Teal (Q5) | Đạt |
| Test Ni | Debug = Release: 2268 passed / 21 skipped / 0 failed |

## Chủ dự án tự kiểm (Sony, 3–4 thao tác)

1. Mở app (vừa cài) → cuộn xuống ngay, không tìm → xoay ngang rồi dọc vài lần → phải thấy cùng bài ở đầu danh sách, không khoảng trống phía trên.
2. Thư viện → gõ tìm → cuộn → xoay ngang/dọc → hàng đầu giữ nguyên vị trí, không bị đẩy xuống.
3. Mở Đang phát → nút Dừng → Bài kế → nhãn "Tạm dừng" phải biến mất khi bài mới phát.

## Giới hạn còn mở

CHƯA XÁC NHẬN ÂM THANH THỰC; D3 không có test JVM; tải APK bằng trình duyệt chưa thử; R-1 (>2000 bài), P2-A, P2-B, Car Mode không thuộc nhiệm vụ.

## Quyết định chủ dự án

- **30/10/2026 18:11 +07 — ĐẠT** (chủ dự án, tin nhắn "Đạt" trong chat). Áp cho source `8bf9d03`, APK Debug vc121 `E6EC8773C8F958301CCE9CB9CFAD4366F77A835A4D5E7A2055F9C1ED1B08E92C`. Giới hạn giữ nguyên: CHƯA XÁC NHẬN ÂM THANH THỰC; D3 không có test JVM; tải APK bằng trình duyệt chưa thử; tablet/Shanling ngoài phạm vi theo quyết định 17:01.
- Nghiệm thu không tự cho phép merge/push/phát hành công khai; chờ chỉ thị riêng.
