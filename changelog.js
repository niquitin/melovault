// Nhật ký cập nhật của trang. Mỗi lần đăng bản mới, tools/release/site-update.js (repo nguồn) tự thêm một bản vào đầu "versions".
// Sửa tay chỉ để chữa chữ hoặc đổi loại dòng; giữ "versions" mới nhất trước và JSON hợp lệ.
window.MELOVAULT_CHANGELOG = {
  "schema": 1,
  "versions": [
    {
      "version": "0.6.120",
      "versionCode": 145,
      "date": "2026-11-13",
      "tag": "v0.6.120-open-beta",
      "highlight": false,
      "new": [
        "Giữ một kênh ở tab Nguồn để ghim lên đầu danh sách (tối đa 20 kênh, nhớ riêng theo tài khoản)",
        "Nút \"Ngẫu nhiên\" cạnh \"Phát hết\" phát ngẫu nhiên cả kênh, cả thư viện hoặc cả kết quả tìm đang mở; giữ một kênh còn có \"Phát ngẫu nhiên kênh này\""
      ],
      "improved": [
        "\"Phát hết\" nay phát cả kênh hoặc thư viện đang mở (tối đa 2.000 bài), không còn dừng ở 200 bài đã hiện"
      ],
      "fixed": [],
      "test": [
        "bản Gọn, máy tính bảng ngang và Car Mode chưa thử trên máy thật; bạn dùng các kiểu này nhớ báo giúp"
      ]
    },
    {
      "version": "0.6.119",
      "versionCode": 144,
      "date": "2026-11-13",
      "tag": "v0.6.119-open-beta",
      "highlight": false,
      "new": [],
      "improved": [
        "Cài đặt có biểu tượng mới đồng bộ: mũi tên, nút chọn, công tắc bật/tắt, đầu trang Đồng bộ"
      ],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.118",
      "versionCode": 143,
      "date": "2026-11-12",
      "tag": "v0.6.118-open-beta",
      "highlight": false,
      "new": [
        "Mục Video có thêm chip \"Khác\" (sau Phim) để xem video không rõ là nhạc hay phim; \"Tất cả\" vẫn chỉ nhạc và phim như trước."
      ],
      "improved": [
        "Các nhóm, kênh có video (mục Kênh video ở tab Nguồn) nay cũng được lưu sẵn để mở nhanh và tìm được ở Thư viện.",
        "Hộp \"Nguồn video\" có thêm các kênh này để bạn chọn Nhạc, Phim hoặc Ẩn; kênh đã Ẩn ghi \"đã ẩn ở mục Video\" ở thẻ Kênh video."
      ],
      "fixed": [],
      "test": [
        "Đã thử trên điện thoại (Xanh đêm). Chưa thử trên máy tính bảng ngang và các kiểu màu Liquid Glass, Teal sáng: bạn nào có xin thử và báo giúp."
      ]
    },
    {
      "version": "0.6.117",
      "versionCode": 142,
      "date": "2026-11-12",
      "tag": "v0.6.117-open-beta",
      "highlight": true,
      "title": "Kênh video",
      "new": [
        "Tab Nguồn có thêm mục \"Kênh video\": app tự tìm mọi nhóm, kênh có video (kể cả nhóm không có nhạc và chat đã lưu trữ) và ghi số video thật. Chạm vào để xem video của kênh."
      ],
      "improved": [
        "Lần đầu app mất khoảng một phút để đọc danh sách và đếm; lần sau hiện ngay và chỉ cập nhật khi cần. Bấm \"Quét lại\" để làm mới."
      ],
      "fixed": [
        "Thẻ kênh nhạc nay ghi số video thật thay cho số đã lưu (ví dụ \"70 video\")."
      ],
      "test": [
        "Đã thử trên điện thoại (Xanh đêm). Chưa thử trên máy tính bảng ngang và các kiểu màu Liquid Glass, Teal sáng: bạn nào có xin thử và báo giúp."
      ]
    },
    {
      "version": "0.6.116",
      "versionCode": 141,
      "date": "2026-11-12",
      "tag": "v0.6.116-open-beta",
      "highlight": false,
      "new": [
        "Vòng tròn chữ cái của tài khoản ở đầu màn chính (Xanh đêm, Liquid Glass, Teal sáng): chạm để mở bảng \"Tài khoản Telegram\" với \"+ Thêm tài khoản\" và \"Quản lý tài khoản\".",
        "Giữ vòng tròn để về tài khoản vừa dùng; chưa có tài khoản khác thì báo \"Chưa có tài khoản khác. Chạm để thêm\". Vòng tròn gập cùng đầu trang khi cuộn.",
        "Chạm tên tài khoản khác trong bảng để chuyển."
      ],
      "improved": [],
      "fixed": [],
      "test": [
        "Chuyển tài khoản trong bảng và giữ vòng tròn khi đã có hai tài khoản: chưa thử trên máy thật, bạn nào có hai số xin thử và báo giúp."
      ]
    },
    {
      "version": "0.6.115",
      "versionCode": 140,
      "date": "2026-11-12",
      "tag": "v0.6.115-open-beta",
      "highlight": true,
      "title": "Nhiều tài khoản Telegram",
      "new": [
        "Nhiều tài khoản Telegram: Cài đặt › Tài khoản hiện tài khoản đang dùng và nút \"+ Thêm tài khoản Telegram\" (số điện thoại khác).",
        "Ở màn đăng nhập thêm tài khoản có nút \"← Quay lại\" (hoặc phím Back) để về tài khoản cũ, thư viện giữ nguyên; thêm hoặc quay lại mất chưa tới 1 giây.",
        "Đang phát nhạc trên máy thì vẫn phát khi bấm Thêm tài khoản; bài Telegram dừng và \"Nghe tiếp\" nhớ đúng vị trí."
      ],
      "improved": [
        "Nút Đăng xuất ghi rõ tên tài khoản."
      ],
      "fixed": [],
      "test": [
        "Chuyển qua lại giữa hai tài khoản đã đăng nhập chưa thử trên máy thật: bạn nào có hai số xin thử và báo giúp."
      ]
    },
    {
      "version": "0.6.114",
      "versionCode": 139,
      "date": "2026-11-12",
      "tag": "v0.6.114-open-beta",
      "highlight": false,
      "new": [],
      "improved": [
        "Chuẩn bị nền cho nhiều tài khoản Telegram: app lưu danh sách tài khoản (chưa có nút mới, dùng như cũ).",
        "Cài đè bản mới vẫn vào thẳng thư viện, không phải đăng nhập lại."
      ],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.113",
      "versionCode": 138,
      "date": "2026-11-12",
      "tag": "v0.6.113-open-beta",
      "highlight": false,
      "new": [
        "Mục Video có nút ⏱ lọc theo độ dài: mặc định chỉ hiện video từ 2 phút trở lên, bỏ clip vài giây.",
        "Chạm nút ⏱ để chọn Mọi độ dài, 1, 2, 3, 5, 10 hoặc 20 phút; app nhớ lựa chọn. \"Đã tải\" không bị lọc.",
        "Dòng \"N video khớp\" ở ô tìm Thư viện cũng tính theo độ dài đã chọn."
      ],
      "improved": [],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.112",
      "versionCode": 137,
      "date": "2026-11-12",
      "tag": "v0.6.112-open-beta",
      "highlight": false,
      "new": [
        "Mục Video rộng hơn: kéo danh sách xuống thì phần đầu trang (tiêu đề, ô tìm, các tab, nút Video) tự gập lại, thấy nhiều video hơn; kéo lên lại hiện ra."
      ],
      "improved": [
        "Bỏ dòng \"Phạm vi: Video\" thừa ở đầu danh sách Video."
      ],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.111",
      "versionCode": 136,
      "date": "2026-11-11",
      "tag": "v0.6.111-open-beta",
      "highlight": false,
      "new": [
        "Ô tìm trong Thư viện giờ cho thấy cả video khớp: gõ từ khóa, dòng \"N video khớp ›\" hiện đầu danh sách, chạm để mở danh sách Video với từ khóa đó.",
        "Ô tìm trong mục Video tìm trong danh sách đã lưu trên máy trước (nhanh), có nút \"Tìm thêm trên Telegram\" nếu muốn tìm rộng hơn.",
        "Hộp \"Nguồn video\" và thẻ kênh ở Nguồn Telegram hiện số video app đã lưu của từng kênh."
      ],
      "improved": [],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.110",
      "versionCode": 135,
      "date": "2026-11-11",
      "tag": "v0.6.110-open-beta",
      "highlight": false,
      "new": [],
      "improved": [
        "Mục Video mở tức thì: app nhớ danh sách video nhạc/phim của các kênh, mở lại là thấy ngay.",
        "App tự cập nhật danh sách video mới ở nền khi mở app, không làm chậm nghe nhạc."
      ],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.109",
      "versionCode": 134,
      "date": "2026-11-11",
      "tag": "v0.6.109-open-beta",
      "highlight": true,
      "title": "Nghe video như nhạc",
      "new": [
        "Nghe tiếng video nhạc như một bài hát: bấm ⋮ ở video › Nghe như nhạc, hoặc nút tai nghe 🎧 trong trình phát video (nghe tiếp đúng chỗ đang xem).",
        "Nút Nghe cả danh sách ở mục Video. Tắt màn hình hay đóng app vẫn nghe, có điều khiển trên màn khóa; tiếng video không ghi vào Playlist hay Nghe tiếp."
      ],
      "improved": [],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.108",
      "versionCode": 133,
      "date": "2026-11-11",
      "tag": "v0.6.108-open-beta",
      "highlight": false,
      "new": [],
      "improved": [
        "Chip Nhạc và Phim trong mục Video giờ đọc thẳng từng kênh nhạc/phim, nên không còn thưa dù bạn theo nhiều kênh tin tức.",
        "Hộp \"Nguồn video\" hiện số video của mỗi kênh."
      ],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.107",
      "versionCode": 132,
      "date": "2026-11-11",
      "tag": "v0.6.107-open-beta",
      "highlight": true,
      "title": "Lọc video Nhạc / Phim",
      "new": [
        "Mục Video giờ chỉ có video nhạc và phim: video tin tức được ẩn tự động.",
        "Chip Nhạc / Phim để lọc; \"Nguồn video\" để chọn kênh nào là nhạc, phim hoặc ẩn hẳn.",
        "Bấm ⋮ ở một video để ẩn nhanh cả kênh đó."
      ],
      "improved": [],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.106",
      "versionCode": 131,
      "date": "2026-11-11",
      "tag": "v0.6.106-open-beta",
      "highlight": false,
      "new": [
        "Video có menu ⋮ ở cuối mỗi dòng (hoặc nhấn giữ dòng): Tải về máy, Tạm dừng / Tiếp tục tải, Xóa khỏi máy, Mở trong Telegram, Chi tiết.",
        "Mục mới \"Đã tải\" trong Video: xem lại các video đã tải về máy, có tổng dung lượng và nút Xóa tất cả."
      ],
      "improved": [],
      "fixed": [
        "Dòng video không còn ghi tên kênh dạng \"Chat -100…\" khi chưa biết tên."
      ],
      "test": []
    },
    {
      "version": "0.6.105",
      "versionCode": 130,
      "date": "2026-11-10",
      "tag": "v0.6.105-open-beta",
      "highlight": false,
      "new": [
        "Video gửi dạng tệp (.mkv, .mp4) cũng hiện trong danh sách (dòng \"Tệp MKV\"); video có bản nhẹ thì chọn chất lượng ở nút trên trình phát (Tự động tiết kiệm dữ liệu khi dùng mạng di động).",
        "Hình trong hình: bấm Home khi đang xem để tiếp tục xem trong cửa sổ nhỏ.",
        "Màn dọc có tiêu đề, chú thích và danh sách \"Tiếp theo\"; hết video tự chuyển sang video kế sau 5 giây (tắt được); chạm đúp nửa trái/phải để lùi/tới 10 giây."
      ],
      "improved": [
        "Xem video mượt hơn: tua tới đoạn chưa tải thì tải ngay từ đoạn đó, không phải chờ tải tuần tự."
      ],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.104",
      "versionCode": 129,
      "date": "2026-11-09",
      "tag": "v0.6.104-open-beta",
      "highlight": true,
      "title": "Xem video",
      "new": [
        "Xem video nhạc từ kênh Telegram — Thư viện › chip \"Video\" (gộp các kênh nhạc) hoặc mở một kênh › \"Video trong kênh\".",
        "Chạm video để xem ngay khi đang tải; xoay ngang để xem toàn màn, tua, lùi/tới 10 giây, chuyển video trước/kế."
      ],
      "improved": [
        "Mở video thì nhạc tự tạm dừng."
      ],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.103",
      "versionCode": 128,
      "date": "2026-11-09",
      "tag": "v0.6.103-open-beta",
      "highlight": true,
      "title": "Liquid Glass thành mặc định",
      "new": [
        "Giao diện Liquid Glass thành mặc định (ai đã tự chọn kiểu màu khác vẫn giữ).",
        "Đang phát: nút gọn bằng biểu tượng — Lưu, Đảo, Lặp, Hàng đợi, EQ, Hẹn giờ. Nhấn giữ một nút để xem tên.",
        "Thông tin chất lượng gộp một dòng; nút tua 30/60 giây dạng vòng có số; giờ đã chạy và tổng ở hai đầu.",
        "Muốn giao diện cũ: Cài đặt › Giao diện & màu sắc › Xanh đêm."
      ],
      "improved": [],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.102",
      "versionCode": 127,
      "date": "2026-11-08",
      "tag": "v0.6.102-open-beta",
      "highlight": false,
      "new": [
        "Liquid Glass hoàn chỉnh: màn Hai khung (máy ngang / máy tính bảng), mọi trang Cài đặt, Đồng bộ, hộp thoại và chế độ Sáng / Theo máy."
      ],
      "improved": [
        "Bỏ nhãn \"thử nghiệm\" của giao diện Liquid Glass.",
        "Chỉ khi chọn Liquid Glass trong Cài đặt › Giao diện & màu sắc; mặc định vẫn là giao diện cũ."
      ],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.101",
      "versionCode": 126,
      "date": "2026-11-08",
      "tag": "v0.6.101-open-beta",
      "highlight": false,
      "new": [
        "Liquid Glass (thử nghiệm): màn Đang phát, menu bài hát và hộp thoại có kiểu kính.",
        "Màn Đang phát đổi màu nền theo ảnh bìa; EQ, Hẹn giờ và menu ⋮ của bài hiện từ dưới lên như bảng kính.",
        "Chỉ khi chọn Liquid Glass trong Cài đặt › Giao diện & màu sắc; mặc định vẫn là giao diện cũ."
      ],
      "improved": [],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.100",
      "versionCode": 125,
      "date": "2026-11-08",
      "tag": "v0.6.100-open-beta",
      "highlight": false,
      "new": [
        "Thêm giao diện Liquid Glass (thử nghiệm): Cài đặt › Giao diện & màu sắc.",
        "Nền đổi màu theo ảnh bìa, thanh đáy kính nổi; mặc định vẫn là giao diện cũ."
      ],
      "improved": [],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.99",
      "versionCode": 124,
      "date": "2026-11-08",
      "tag": "v0.6.99-open-beta",
      "highlight": false,
      "new": [],
      "improved": [
        "Màn Đang phát hiện ảnh bìa ngay khi mở bài, không phải chờ bài tải xong; khi bài tải xong, bìa trong tệp nhạc (nét hơn) tự thay vào."
      ],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.98",
      "versionCode": 123,
      "date": "2026-11-08",
      "tag": "v0.6.98-open-beta",
      "highlight": true,
      "title": "Tự cập nhật trong app",
      "new": [
        "Tự báo khi có bản mới (Cài đặt → Giới thiệu → Cập nhật ứng dụng), tối đa 1 lần mỗi ngày, chỉ tải khi bạn đồng ý; tắt được trong Giới thiệu. Lần đầu cài bản cập nhật, Android sẽ hỏi cho phép MeloVault cài ứng dụng."
      ],
      "improved": [
        "Ký bằng khóa phát hành chính thức: các bản sau cài đè hoặc cập nhật trong app, không phải gỡ nữa. Riêng bản 0.6.97 phải gỡ rồi cài một lần."
      ],
      "fixed": [],
      "test": []
    },
    {
      "version": "0.6.97",
      "versionCode": 122,
      "date": "2026-11-07",
      "tag": "v0.6.97-open-beta",
      "highlight": true,
      "title": "Bản thử công khai đầu tiên",
      "new": [
        "Bản thử đầu tiên cho bạn bè: nghe nhạc từ Telegram theo kênh, phát nền, bộ chỉnh âm (EQ), widget Đang phát trên màn hình chính, lời nhạc từ LRCLIB."
      ],
      "improved": [],
      "fixed": [],
      "test": []
    }
  ],
  "milestones": [
    {
      "date": "2026-11-07",
      "dateText": "07/11/2026",
      "title": "Widget Đang phát và bộ lọc",
      "text": "Bản thử nội bộ: widget Đang phát ở màn hình chính; sửa Bộ lọc và màn Đang phát khi xoay ngang."
    },
    {
      "date": "2026-11-02",
      "dateText": "02–06/11/2026",
      "title": "Cài đặt mới và bộ chỉnh âm",
      "text": "Bản thử nội bộ: Cài đặt thiết kế lại, bộ chỉnh âm (EQ) trên màn Đang phát."
    },
    {
      "date": "2026-10-26",
      "dateText": "10/2026",
      "title": "Nghe nhạc từ Telegram",
      "text": "Bản thử nội bộ: thư viện từ Tin đã lưu, nhóm và kênh; tìm không dấu, sắp xếp A–Z, nhạc trên máy, bài đã tải, Nghe tiếp."
    }
  ]
};
