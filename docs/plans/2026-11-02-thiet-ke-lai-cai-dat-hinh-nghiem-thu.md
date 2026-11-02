# Hình nghiệm thu — 2026-11-02-thiet-ke-lai-cai-dat

**QA CANDIDATE — CHƯA NGHIỆM THU.** Ảnh QA trên Sony XQ-AS52 `QV725WEQ54`. Ảnh `B_*` từ APK cuối `F376BCCB…77E7` (build `d52fe43`); ảnh `A_*` từ APK trước đó `1037FB53…DF93` (build `2242319`) — giao diện các trang đó không đổi giữa hai bản (chỉ khác: logo trang Giới thiệu, toast đếm chạm, câu Quyền riêng tư). Bảng bước: [báo cáo](2026-11-02-thiet-ke-lai-cai-dat-bao-cao.md).

- Thư mục (owner-only): [hinh](https://drive.google.com/drive/folders/1TTcTJ2jo5ciCF-3t1V--aFBdGhMP2-Ec) trong [thư mục QA candidate](https://drive.google.com/drive/folders/1Sm7XOBwnfh6cLwY0o8KcCvtUGt4IemRR)
- **Ảnh tổng hợp:** [00_tong_hop.png](https://drive.google.com/file/d/1PEXfWTYKmcnUqJdnhRHoQEGiMDuqwmLF/view?usp=drivesdk) — 8 ô: Q1 trang chính, Q2 Màn Đang phát, Q3 hộp đồng ý Lời nhạc, Q4 màn Đồng bộ (chỉ xem), Q5 Giới thiệu, Q5 Giấy phép (toàn văn), Q6 Công cụ nhà phát triển, Q7 dòng Chú ý khi đang thử khung giả lập.

| Bước | Kết quả | Tệp |
|---|---|---|
| Q1 Trang chính | Nhóm thẻ có biểu tượng, tiêu đề cố định, dòng Đồng bộ ghi “Tắt”, Lời nhạc BẬT, Giới thiệu, Đăng xuất; không còn nhóm Thiết bị/Nâng cao | [B_F1_home](https://drive.google.com/file/d/1zGENmjPgyolhQ4jWYrkphnadkuQ3sC8o/view?usp=drivesdk) |
| Q5 Giới thiệu | Nguyễn Dũng, `0.6.96-beta (vc121)`, GitHub, `ntdung.md@gmail.com`, logo căn giữa | [B_F2_about](https://drive.google.com/file/d/1xCNtF7v5ryJgYvHlQZogNRZ27Qw66ZIU/view?usp=drivesdk) |
| Q6 Mở khóa 7 chạm | Mục “Nhà phát triển” hiện ở trang chính | [B_F3](https://drive.google.com/file/d/1M8NJTX4lfw8qtAZvlEk7poIZ8vbx-YhF/view?usp=drivesdk), [B_F4](https://drive.google.com/file/d/1s0tLgna37LzGRLD05RA182et0NeByLIz/view?usp=drivesdk) |
| Q6 Công cụ nhà phát triển | Trang có Thiết bị & màn hình, Nâng cao, nút Ẩn | [B_F5_devtools](https://drive.google.com/file/d/1RnCAYcA2fsmLwWRF6gHH-bclciUgG9zA/view?usp=drivesdk) |
| Q7 Chống kẹt | Bật khung Shanling → Ẩn công cụ → trang chính vẫn có “Đang thử khung giả lập · Tắt”; chạm → về bình thường | [B_F6](https://drive.google.com/file/d/18FUaByGjKwb6NHbHKcY225t2H-ZTQPW5/view?usp=drivesdk), [B_F7](https://drive.google.com/file/d/1S4SI_TmTYWhVEywPgniBhPxTEjH-nx_h/view?usp=drivesdk), [B_F8](https://drive.google.com/file/d/1K20eDmroTAoFrpuWZ62jHcgrmX_jqqHU/view?usp=drivesdk) |
| Quyền riêng tư | 4 ý, không còn câu lặp | [B_F9_privacy](https://drive.google.com/file/d/1tL6Zw8LoHF6CmeiVieKj6_qCNe6P4cmp/view?usp=drivesdk) |
| Q3 Lời nhạc | Tắt ngay; bật → hộp đồng ý; Hủy giữ Tắt | [A_Q3_dialog](https://drive.google.com/file/d/1M8pE_7F4c1T0TeC57_QtAMJwILuao1jx/view?usp=drivesdk), [A_Q3_cancel_stays_off](https://drive.google.com/file/d/1nltkiHK16OvVb63a8w1ND6Nlajzty7xx/view?usp=drivesdk) |

Các ảnh còn lại (Q2, Q4, Q5 Giấy phép, Q6/Q7 lần 1) nằm cùng thư mục `hinh` (tiền tố `A_`). Không có ảnh thư viện có tên bài (đã loại khi upload). Bản sao đầy đủ ở Ni `docs/plans/artifacts/2026-11-02-thiet-ke-lai-cai-dat/` (bị `.gitignore`).

Chưa kiểm được bằng ảnh: **âm thanh thật**; màn Cài đặt trên máy Android 15+ (nơi lỗi “chữ bị che” được báo); soạn thư thật (Sony không có app email); Q8 xoay ngang/chữ lớn; Release/`.open`/Personal trên máy.
