# Nghiệm thu — bản công khai đăng nhập chỉ bằng số điện thoại

TASK_ID: `2026-10-30-dang-nhap-chi-so-dien-thoai`.

## Quyết định chủ dự án: ĐẠT

- **Nguồn xác nhận:** chủ dự án gõ "Đạt" trong hội thoại chat, ngày 31/10/2026, ngay sau khi nhận file APK và tự thử đăng nhập trên một điện thoại khác (ngoài Shanling/Sony mà agent dùng để QA).
- **Phạm vi được xác nhận:** bản **open Debug**, `app.melovault.player.open`, `versionCode=121`, `versionName=0.6.96-open`, SHA-256 `a241c32363b40bc77e3d7d1509b0e80d2483e262eb1fa2a151e0e0d39c26b0d9` — đây là file duy nhất agent đã gửi/trỏ link cho chủ dự án trong phiên này ([Drive](https://drive.google.com/file/d/1Tn0t8-2ubI55cofpjNO1qJEpdczq4RPU/view)).
- **Commit nguồn:** `a2f0a88` (build trên Tiny, clone cô lập `C:\Bots\MeloVault-dang-nhap-IsolatedQA-20261030-a2f0a88`). Hồ sơ Git mới nhất tại thời điểm nghiệm thu: `3ed6062` (nhánh `dot/2026-10-30-dang-nhap-chi-so-dien-thoai`).
- **Model nào thử:** chủ dự án không nêu rõ tên máy — agent không biết chi tiết thiết bị, hệ điều hành của "điện thoại khác" đó.

## PASS

```
ĐẠT 2026-10-30-dang-nhap-chi-so-dien-thoai — a2f0a88 / APK open Debug SHA-256 a241c32363b40bc77e3d7d1509b0e80d2483e262eb1fa2a151e0e0d39c26b0d9
```

## Giới hạn còn lại (ngoài phạm vi ĐẠT này, chưa kiểm chứng)

- **Bản open Release** (`0ca06484ec53a2f4118c22e17e1936790b52dcb984d792f7911133f17cdfa705`) — chưa được chủ dự án tự thử; chỉ qua gate kỹ thuật (verify hash/aapt/apksigner) ở S6.7.
- **Bản thường (Q5 hồi quy trên Sony)** — do agent tự kiểm (cài đè, mở app, chụp ảnh), không phải chủ dự án tự tay thử.
- **Q6 (JVM: D6/D7/D8 — mã lưu thắng, mã open bị từ chối, khởi động lại sau lưu mã)** — chưa chạy trong phiên này.
- **Shanling** — bỏ qua hoàn toàn theo chỉ thị chủ dự án ("Bỏ qua thiết bị shanling").
- **Âm thanh thật** qua loa/tai nghe — chưa xác nhận độc lập trong nhiệm vụ này.
- **Mã open thật bị Telegram từ chối** (API_ID_INVALID/API_ID_PUBLISHED_FLOOD, câu D7) — chưa thử.
- Release UI, Y700/S25/FYT — ngoài phạm vi.

## Trạng thái merge/push

**Chưa merge, chưa push, chưa public.** Theo CLAUDE.md mục 0/9: merge chỉ thực hiện sau chỉ thị merge riêng của chủ dự án; đây mới là xác nhận ĐẠT, không phải lệnh merge.
