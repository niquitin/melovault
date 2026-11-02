# Hình nghiệm thu — 2026-11-01-nhay-header-khi-cuon

**QA CANDIDATE — CHƯA NGHIỆM THU.** Ảnh/video QA trên Sony XQ-AS52 `QV725WEQ54` với APK Debug `E5D808D2…6B74` (build từ `4fe5a37`). Bảng bước chi tiết: [báo cáo](2026-11-01-nhay-header-khi-cuon-bao-cao.md).

- Thư mục (owner-only): [hinh](https://drive.google.com/drive/folders/1OEi9a6LaC-UP_Z445xPVhIzENgYR3DUg) trong [thư mục QA candidate](https://drive.google.com/drive/folders/1pMc9Loe7Z5cCwdW1vSVK3VEHTw9yIDgJ)
- **Ảnh tổng hợp:** [00_tong_hop.png](https://drive.google.com/file/d/19gEnVld6DaKB0bj6PTJ6kKmXnaLvrNaz/view) — 8 ô: 1–3 trước khi sửa (giữ tay kéo, header hiện/ẩn cách nhau 0,1 s), 4–5 sau khi sửa (tay còn giữ thì header không đổi, nhả tay mới hiện 1 lần), 6 lướt nhanh ×3 gấp 1 lần, 7–8 đổi tab.

| Bước | Kết quả | Tệp |
|---|---|---|
| Baseline (APK cũ vc121) giữ tay kéo xuống | Header đổi **8** và **14** lần trong hai cử chỉ | [base_04_held.mp4](https://drive.google.com/file/d/1KDYuD3-CLoAlfrFB5r_ZZz1-NarBQYlD/view) |
| Q1 lướt nhanh ×3 | **1** lần đổi (log `phase=FLING`) | [new_q1_fast3.mp4](https://drive.google.com/file/d/1pzw3jplFkMuVhSxtf3uai766-MBkCIKM/view) |
| Q2 giữ tay kéo (cùng chuỗi baseline) | **3** lần đổi, mỗi cử chỉ 1 lần, đều lúc nhả tay (`phase=IDLE`) | [new_q2_held.mp4](https://drive.google.com/file/d/1tvP2B2ob1b7Dx0MPuUnLOIgC2oGXOm6m/view) |
| Q3 lướt nhanh xuống/lên xen kẽ | **5** lần đổi = 5 dòng log, mỗi cử chỉ 1 lần | [new_q3c_updown.mp4](https://drive.google.com/file/d/1w8mReoQpW-PdEGXx0vxDoaUXEk57d3L9/view) |
| Q4 đổi tab | Từ trạng thái gấp: Đã tải → header hiện, danh sách ở đầu | [04b](https://drive.google.com/file/d/1Y4zf3E8qtSlVejLvzG-q-5_w17wIJYxz/view), [04c](https://drive.google.com/file/d/1LVv7yftZptLW0WhKET-Od3vV1qb151yA/view) |
| Q5 lướt nhanh ×8 | 0 lần đổi (header đã gấp), không nháy | [new_q5_pages.mp4](https://drive.google.com/file/d/1uy2cCtv6-GUO-q3ty158RYTGvzU5ftjG/view), [05b](https://drive.google.com/file/d/1la19aaY_lnPcIk3vj-i4CtH01Puca_h6/view) |
| Q6 Hai khung | **CHƯA KIỂM** | — |

Ảnh đầu phiên: [new_00_start.png](https://drive.google.com/file/d/1QPrT6BqLBPjsq9Lh80Yn5oinTpyBAFoc/view). Các tệp `*.logcat.txt`/`*.gestures.txt` nằm cùng thư mục `hinh`.

Bản sao đầy đủ ở Ni `docs/plans/artifacts/2026-11-01-nhay-header-khi-cuon/` (bị `.gitignore`; video có tên bài/kênh nên không commit).

Chưa kiểm được bằng ảnh/video: **âm thanh thật**; Playlist ≥ 20 bài; Hai khung/xoay ngang; ngón tay thật (QA giả lập bằng ADB); Release/`.open`/Personal trên máy.
