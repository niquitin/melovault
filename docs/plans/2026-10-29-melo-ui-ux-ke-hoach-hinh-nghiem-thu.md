# S7 — chỉ mục bằng chứng QA có điều kiện

TASK_ID: `2026-10-29-melo-ui-ux-ke-hoach`; source `278f0d277aeb077bb938cf76f0e055c5058dd996`.

**QA CANDIDATE — CHƯA NGHIỆM THU; còn điểm chặn thứ tự khi bật Đảo.**

Chưa nghiệm thu sơ bộ/chính thức. Có hai lỗi UI mới cần chủ dự án giao sửa: mất anchor khi xoay và feedback Deck còn “Tạm dừng” sau Next đang phát. Không gọi S6/P1 hoàn tất toàn bộ.

## Ảnh Drive đã xác minh

[Thư mục ảnh che nội dung riêng tư, Ni N owner-only](https://drive.google.com/drive/folders/1DBGtjsp01h2ice3yjB1dYIGS1PnMVmWJ). Folder và cả 9 file chỉ có permission owner `nigamehome@gmail.com`, `shared=false`; kích thước connector đọc lại khớp Ni. Ảnh tổng hợp fetch raw thành công, không inline base64. SHA-256 dưới đây là file Ni đã upload, không phải checksum do Drive trả về.

| Ảnh / liên kết đã kiểm | Bytes | SHA-256 Ni |
|---|---:|---|
| [Ảnh tổng hợp 8 ô, 1280×1580](https://drive.google.com/file/d/1stAVkxFoZUTUDVOi4VPKyi9qkNrGYW6q/view?usp=drivesdk) | 258492 | `F9561AB73078F5A9D12136C0070FF1D9703B9C27E9314339D5D3481D361F0107` |
| [QA3 — Playlist 2→3, có ★](https://drive.google.com/file/d/1Mi7o9xRdp3EeK5ePjpYsFj_0pYEWqXhF/view?usp=drivesdk) | 127627 | `7C72CF27BC080C8D5E62F8577D559834126B3F22431DF1537F1E57006B802941` |
| [QA3 — bỏ lưu, Playlist về 2](https://drive.google.com/file/d/14fqM7UR3OboDo6KCjtFAFbCupL9WfY4C/view?usp=drivesdk) | 124469 | `EEC9D3056AA93C6EDC6E1D11CCC1A4DA44B25013F93331D92F1DD6C1071796F5` |
| [QA4 — bài chưa tải đủ đang đệm/tải](https://drive.google.com/file/d/10JRSXx_vhd-J_NK5GuB8F8qCnOn3Oegi/view?usp=drivesdk) | 123210 | `C2446EC554FBDF23B1D56B2DF5594BE3620572F49DC2E303C9B04D67BC36A5A5` |
| [QA5 — kéo tua 50:29, vẫn paused](https://drive.google.com/file/d/1WztLC-bATpRcxJZLVvDiSjiqXDWVj2x6/view?usp=drivesdk) | 104782 | `920CA14274D8D6BB4889A25FD424792E29405A698130B63DE0E66BC0E8DC5940` |
| [Lỗi — feedback paused, engine/queue playing](https://drive.google.com/file/d/1QNX_lyjc0ed2lgDcwn8gRqps83tyyEwy/view?usp=drivesdk) | 100333 | `1AC5FB8AF00A89A0681093DD7C0A6F72BF39B67EDBE421C41D5A71EF41741662` |
| [QA2 — query/anchor trước xoay](https://drive.google.com/file/d/1lJc_9c1520LNBQ4-SEqoREIsMX96cKx5/view?usp=drivesdk) | 124294 | `90CA892600E37569602BE63F039EC914C2B7F42C77BB36720B66C68DE335EDBF` |
| [Lỗi — query giữ, anchor mất sau xoay](https://drive.google.com/file/d/1fOYhmSgB8kbLqlJK1Euj3lqZCKtWaZxH/view?usp=drivesdk) | 152351 | `3CCF17E71DDA3319B4D95AD4D26A1C46D776DE3742C777B930519E60713C057C` |
| [Teal dọc, giữ bài đang tạm dừng](https://drive.google.com/file/d/1uIFn216Xxu4QJwE8azFbYQEPajsywikZ/view?usp=drivesdk) | 143076 | `ABF79294549E96CF7A6E14205B9280F51263D9DD1FF4C7905501D1C6EAABEC47` |

Bản bàn giao cuối tại `artifacts/2026-10-29-melo-ui-ux-ke-hoach/S7-20261029_170707/redacted-v2/00_tong_hop.png`, SHA256 `F9561AB73078F5A9D12136C0070FF1D9703B9C27E9314339D5D3481D361F0107`. Mỗi ảnh che tên bài/kênh/ảnh bìa và thêm footer; giữ pixel gốc ngoài vùng che, không dựng lại giao diện. Một số status trong dòng cũng bị che cùng nội dung riêng tư; ảnh gốc và media snapshots private dùng kiểm chi tiết. Bản tổng hợp nháp v1 tại root/S7 root được giữ private, **không upload/không dùng bàn giao**; v2 che bổ sung vùng đổi vị trí trong QA4, không ghi đè ảnh cũ.

## Ma trận QA thực tế

| QA | Đã quan sát / bằng chứng mới | Kết quả và giới hạn |
|---|---|---|
| QA1 | Sony Hai khung ngang Current `24_*`; Teal `29–31`: Nguồn bên phải, chọn kênh khác, query2026 có159 kết quả; player trái giữ identity/state2/65943ms | Bổ sung trên Sony; **BLOCKED tablet thật**, không dùng thay Y700 |
| QA2 | `22` cuộn/query “mĩ” (IME chuyển từ phím mix), `23a` Deck, `23` Back; sau đó3 chu kỳ dọc/ngang `24/25_*`; media trước/sau cùng digest/state2/65943ms | Deck→Back giữ nguyên digest dòng và bounds; **LỖI xoay mất anchor** ngay chu kỳ1, query/scope vẫn giữ |
| QA3 | `17–19`: menu ⋮ bài chưa phát → Playlist2→3 có★ → bỏ lưu trực tiếp★ về2; media cùng digest/state2/54544ms | Đã kiểm lưu/bỏ lưu thật, không phát/seek khi save; menu fallback trên hàng hẹp và★ trực tiếp trong Playlist. Star cùng bài ở mọi surface/keyboard/TalkBack chưa kiểm đủ |
| QA4 | `20–21`: chọn bài chưa tải đủ thật, Đang đệm/Đang tải→row Đang phát, media state3; không tạo flood/thay mạng/xóa tệp | Đã quan sát buffer/tải thật; FLOOD_WAIT_3/error/invalid file dùng JVM S6, không tuyên bố đã tái hiện lỗi thật |
| QA5 | `09–16`: mixset3:21:07; paused,+60/−30/seek,queue preview12/54,dock; Next đổi bài3:05:07 | Tua giữ paused và identity. **LỖI feedback Deck “Tạm dừng” khi queue/MediaSession phát≈42s**. Đảo thứ tự vẫn BLOCKED; full queue/IME/chữ lớn thật chưa kiểm đủ |
| QA6 | Ni `adb devices -l` trống đầu/cuối; Compact/Car regression dùng unit S6 | **BLOCKED Shanling thật**; không cài FYT, không dùng Sony làm bằng chứng Shanling |

## Truy vết riêng tư và lỗi

Ảnh gốc/XML/media JSON/log private dưới `docs/plans/artifacts/2026-10-29-melo-ui-ux-ke-hoach/S7-20261029_170707/`, Tiny `C:\Bots\_qa_temp\MeloVault-S7-20261029_170707`. Không commit/upload bản gốc. `redaction-manifest.json` ở redacted-v2 mô tả vùng che; `drive-delivery-evidence.json` đối chiếu quyền/bytes/URL.

- Anchor: `22→23` first-row digest `7EE449…F9DF`, bounds `[42,725][965,857]`; sau xoay cả3 chu kỳ `78BF51…9D72`, bounds `[42,1164][965,1353]`. `qa2-anchor-comparison.json` có đầy đủ hash/query. Ảnh trước/sau chỉ chứng minh source đang kiểm; không suy process recreation/deep pagination đã đạt.
- Feedback: `16a_sony_next_settled_1730.png`, snapshot `16a-next-settled-media.json`: state3,position41867ms,cùng bài mới; queue + nút pause đang phát nhưng feedback trên Deck còn Tạm dừng. Sau đó pause lại state2/54544ms. Điểm đọc sơ bộ: MeloStudioDeck refresh/MeloPlayFeedbackView trong MeloLibraryPane và facts host/Activity; chưa xác định nguyên nhân, không sửa S7.
- Lượt menu tọa độ cũ trong scan bị đổi hàng/chạm phát ngoài dự kiến (`03/05–08`) không dùng chứng minh save. Đã pause, chuyển target từ XML mới, QA3 thật chỉ `17–19`. Một lần ADB reconnect ở lượt seek `12`: không tính bằng chứng; kiểm lại serial/model/device rồi dùng `14`.
- Sony cuối: màu Hiện tại, query thử đã xóa, xoay free, mật độ420/font1.0; media state2/65943ms. `SONY_SCREEN_END=OFF` lúc17:47:44 +07; finally OFF sau mọi nhóm UI. `SHANLING_SCREEN_END=UNKNOWN`: không thao tác, không kết nối.
- Installed APK lấy lại sau QA có SHA256 trùng Debug candidate. Crash buffer PID14776 từ17:18 trả exit0,0 dòng/0 fatal-ANR match; không coi đây là kiểm ANR toàn hệ thống hoặc test dài hạn.
- Âm thanh thật **CHƯA XÁC NHẬN**; offline mất mạng, tablet/Y700/S25/FYT thật, font1.3/1.5/2 và ma trận kích thước thật **CHƯA KIỂM CHỨNG**. Font/layout formulas và fake chỉ có unit S6. QA đã thay bài/nghe tiếp và bắt đầu tải bài từ thao tác thử; không xóa cache/data để hoàn nguyên.

APK Drive bị403 `cannotDownloadAbusiveFile`: xem [APK-index](2026-10-29-melo-ui-ux-ke-hoach-apk-test.md), không coi link APK là tải được. Không có PASS chính thức; cần bổ sung kế hoạch order Đảo trước sửa service/playback, giao phạm vi sửa riêng cho hai lỗi UI và kiểm lại đúng APK sau đó.
