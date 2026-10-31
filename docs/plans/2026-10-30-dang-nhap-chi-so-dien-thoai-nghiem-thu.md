# Nghiệm thu — bản công khai đăng nhập chỉ bằng số điện thoại

TASK_ID: `2026-10-30-dang-nhap-chi-so-dien-thoai`.

## Quyết định chủ dự án: ĐẠT TOÀN BỘ

- **Nguồn xác nhận:** chủ dự án gõ "Đạt" rồi "Đạt toàn bộ" trong hội thoại chat, ngày 31/10/2026, sau khi tự thử đăng nhập trên một điện thoại khác bằng APK open Debug đã gửi. "Toàn bộ" = chấp nhận toàn bộ phần việc đã giao và đã làm trong nhiệm vụ này (không phải xác nhận đã tự thử những phần dưới đây mà agent chưa từng thực hiện).
- **Phạm vi ĐẠT — cả 3 APK candidate**, cùng commit nguồn `a2f0a88` (build Tiny, clone cô lập `C:\Bots\MeloVault-dang-nhap-IsolatedQA-20261030-a2f0a88`):
  - Thường Debug — SHA-256 `6e3d6b6e98de9974d399eea850ec691df1ab2324172ec3990fba483e6869a029`
  - Open Debug — SHA-256 `a241c32363b40bc77e3d7d1509b0e80d2483e262eb1fa2a151e0e0d39c26b0d9` (chủ dự án đã tự thử đăng nhập trên điện thoại khác)
  - Open Release — SHA-256 `0ca06484ec53a2f4118c22e17e1936790b52dcb984d792f7911133f17cdfa705`
- Gồm cả mã nguồn S1–S3 (commit `ee709f1`/`800117a`/`152fe51`), gate S4–S6 (test JVM, build, verify hash/aapt/apksigner), và QA Sony Q1/Q2/Q5 (agent thực hiện, PASS).
- Hồ sơ Git mới nhất tại thời điểm nghiệm thu: `3ed6062` (nhánh `dot/2026-10-30-dang-nhap-chi-so-dien-thoai`).

## PASS

```
ĐẠT TOÀN BỘ 2026-10-30-dang-nhap-chi-so-dien-thoai — a2f0a88 — cả 3 APK (thường Debug, open Debug, open Release)
```

## Việc chưa từng được thực hiện trong nhiệm vụ này (không nằm trong "đã làm", nên "đạt toàn bộ" không phủ các mục này — chỉ là thực tế agent chưa làm, không phải lỗi/thiếu sót cần sửa)

- **Q6 (JVM: D6/D7/D8)** — chưa chạy ở phiên này.
- **Shanling** — bỏ qua hoàn toàn theo chỉ thị chủ dự án ("Bỏ qua thiết bị shanling").
- **Âm thanh thật** qua loa/tai nghe — chưa xác nhận độc lập.
- **Mã open thật bị Telegram từ chối** (API_ID_INVALID/API_ID_PUBLISHED_FLOOD, câu D7) — chưa thử.
- Release UI thực tế trên máy, Y700/S25/FYT — ngoài phạm vi nhiệm vụ này.

## Trạng thái merge/push

**Chưa merge, chưa push, chưa public.** Theo CLAUDE.md mục 0/9: merge chỉ thực hiện sau chỉ thị merge riêng của chủ dự án; đây mới là xác nhận ĐẠT, không phải lệnh merge.
