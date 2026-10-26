(() => {
  "use strict";

  const unpublished = "Chưa công bố";
  const waiting = "Bản test đang chờ phát hành";
  const pendingMessage = "Liên kết và thông tin bản tải sẽ xuất hiện tại đây khi sẵn sàng.";
  const invalidMessage = "Thông tin bản tải chưa đầy đủ. Vui lòng quay lại sau.";
  const config = window.MELOVAULT_RELEASE;
  const release = config && typeof config === "object" && !Array.isArray(config) ? config : {};
  const isText = value => typeof value === "string" && value.trim().length > 0;
  const isPositiveInteger = value => Number.isSafeInteger(value) && value > 0;

  function validDate(value) {
    if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
    const [year, month, day] = value.split("-").map(Number);
    if (year < 1 || month < 1 || month > 12 || day < 1) return false;
    const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
    return day <= [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1];
  }

  function validUrl(value) {
    if (!isText(value)) return null;
    const text = value.trim();
    // Chỉ nhận URL tuyệt đối chuẩn, không có ký tự điều khiển.
    if (!/^https:\/\//i.test(text) || /[\s\\\u0000-\u001f\u007f]/.test(text)) return null;
    try {
      const url = new URL(text);
      return url.protocol === "https:" && url.hostname && !url.username && !url.password ? url.href : null;
    } catch {
      return null;
    }
  }

  const dateOkay = validDate(release.releaseDate);
  const hashOkay = typeof release.sha256 === "string" && /^[a-f\d]{64}$/i.test(release.sha256);
  const notesOkay = Array.isArray(release.notes) && release.notes.length > 0 && Array.from(release.notes).every(isText);
  const href = validUrl(release.url);
  const metadataOkay = isText(release.versionName) && isPositiveInteger(release.versionCode) &&
    isPositiveInteger(release.sizeBytes) && dateOkay && hashOkay && notesOkay;

  function setText(id, value) {
    document.getElementById(id).textContent = value;
  }

  setText("release-version", isText(release.versionName) ? release.versionName.trim() : unpublished);
  setText("release-code", isPositiveInteger(release.versionCode) ? String(release.versionCode) : unpublished);
  setText("release-size", isPositiveInteger(release.sizeBytes)
    ? `${(release.sizeBytes / 1048576).toLocaleString("vi-VN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} MiB (${release.sizeBytes.toLocaleString("vi-VN")} byte)`
    : unpublished);
  setText("release-date", dateOkay ? release.releaseDate.split("-").reverse().join("/") : unpublished);
  setText("release-hash", hashOkay ? release.sha256 : unpublished);

  const notes = document.getElementById("release-notes");
  notes.replaceChildren();
  if (notesOkay) {
    const list = document.createElement("ul");
    release.notes.forEach(note => {
      const item = document.createElement("li");
      item.textContent = note;
      list.appendChild(item);
    });
    notes.appendChild(list);
  } else {
    const text = document.createElement("p");
    text.textContent = unpublished;
    notes.appendChild(text);
  }

  const action = document.getElementById("download-action");
  const blankUrl = release.url === undefined || (typeof release.url === "string" && !release.url.trim());
  if (href && metadataOkay) {
    const link = document.createElement("a");
    link.className = "button button-primary download-button";
    link.textContent = "Tải APK";
    link.href = href;
    link.rel = "noopener noreferrer";
    action.replaceChildren(link);
    setText("download-status", "Hãy đọc hướng dẫn cài đặt trước khi cập nhật.");
  } else {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "button button-primary download-button";
    button.disabled = true;
    button.textContent = waiting;
    action.replaceChildren(button);
    setText("download-status", blankUrl ? pendingMessage : invalidMessage);
  }

  const copyButton = document.getElementById("copy-report");
  const template = document.getElementById("report-template");
  const copyStatus = document.getElementById("copy-status");
  copyButton.hidden = false;
  copyButton.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(template.value);
      copyStatus.textContent = "Đã sao chép mẫu.";
    } catch {
      copyStatus.textContent = "Không thể sao chép tự động. Hãy chọn và sao chép mẫu bên dưới.";
      template.focus();
      template.select();
    }
  });
})();
