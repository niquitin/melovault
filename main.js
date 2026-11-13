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

  // ---- Nhật ký cập nhật (dữ liệu ở changelog.js; chỉ chèn bằng textContent) ----
  const KINDS = [
    ["new", "Mới", "cl-new"],
    ["improved", "Cải tiến", "cl-improved"],
    ["fixed", "Sửa lỗi", "cl-fixed"],
    ["test", "Cần thử giúp", "cl-test"]
  ];
  const PAGE_SIZE = 8;
  const releasesBase = "https://github.com/niquitin/melovault/releases/tag/";

  function validateChangelog(data) {
    if (!data || typeof data !== "object" || data.schema !== 1 || !Array.isArray(data.versions)) return null;
    const versions = [];
    data.versions.forEach(item => {
      if (!item || typeof item !== "object" || typeof item.version !== "string" || !/^\d+\.\d+\.\d+$/.test(item.version) || !validDate(item.date)) return;
      const entry = {
        version: item.version,
        date: item.date,
        title: isText(item.title) ? item.title.trim() : "",
        tag: typeof item.tag === "string" && /^v\d+\.\d+\.\d+[\w.-]*$/.test(item.tag) ? item.tag : "",
        highlight: item.highlight === true,
        lines: []
      };
      KINDS.forEach(([key]) => {
        const list = item[key];
        if (Array.isArray(list)) list.forEach(text => { if (isText(text)) entry.lines.push([key, text.trim()]); });
      });
      if (entry.lines.length) versions.push(entry);
    });
    if (!versions.length) return null;
    const milestones = [];
    (Array.isArray(data.milestones) ? data.milestones : []).forEach(item => {
      if (!item || typeof item !== "object" || !isText(item.title)) return;
      milestones.push({
        dateText: isText(item.dateText) ? item.dateText.trim() : "",
        title: item.title.trim(),
        text: isText(item.text) ? item.text.trim() : ""
      });
    });
    return { versions, milestones };
  }

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function versionCard(entry, latest) {
    const item = element("li", "tl-item" + (entry.highlight ? " tl-major" : "") + (latest ? " tl-latest" : ""));
    const card = element("article", "tl-card");
    const meta = element("div", "tl-meta");
    const time = element("time", "", entry.date.split("-").reverse().join("/"));
    time.setAttribute("datetime", entry.date);
    meta.append(time, element("code", "", entry.version));
    if (latest) meta.append(element("span", "tl-badge", "Mới nhất"));
    const heading = element("h3", "", entry.title || "Bản " + entry.version);
    heading.tabIndex = -1;
    const lines = element("ul", "cl-lines");
    entry.lines.forEach(([key, text]) => {
      const kind = KINDS.find(k => k[0] === key);
      const line = element("li");
      line.append(element("span", "cl-k " + kind[2], kind[1]), element("span", "cl-t", text));
      lines.append(line);
    });
    card.append(meta, heading, lines);
    if (entry.tag) {
      const link = element("a", "tl-link", "Release trên GitHub ›");
      link.href = releasesBase + encodeURIComponent(entry.tag);
      link.rel = "noopener noreferrer";
      card.append(link);
    }
    item.append(card);
    return item;
  }

  function milestoneCard(entry) {
    const item = element("li", "tl-item tl-major tl-milestone");
    const card = element("article", "tl-card");
    const meta = element("div", "tl-meta");
    if (entry.dateText) meta.append(element("span", "tl-date", entry.dateText));
    meta.append(element("span", "tl-badge tl-badge-soft", "Bản thử nội bộ"));
    const heading = element("h3", "", entry.title);
    heading.tabIndex = -1;
    card.append(meta, heading);
    if (entry.text) card.append(element("p", "tl-text", entry.text));
    item.append(card);
    return item;
  }

  function renderWhatsNew(latest) {
    const box = document.getElementById("whats-new");
    const current = typeof release.versionName === "string" ? release.versionName.replace(/-open$/, "") : "";
    if (!box || latest.version !== current) return;
    const first = latest.lines[0][1];
    const short = first.length > 150 ? first.slice(0, 147).replace(/\s+\S*$/, "") + "…" : first;
    const link = element("a", "", "Xem nhật ký ›");
    link.href = "#nhat-ky";
    box.replaceChildren(element("strong", "", "Có gì mới ở " + latest.version + ": "), document.createTextNode(short + " · "), link);
    box.hidden = false;
  }

  function renderChangelog() {
    const list = document.getElementById("cl-list");
    const state = document.getElementById("cl-state");
    const toolbar = document.getElementById("cl-toolbar");
    const more = document.getElementById("cl-more");
    const allButton = document.getElementById("cl-all");
    const majorButton = document.getElementById("cl-major");
    if (!list || !state || !toolbar || !more || !allButton || !majorButton) return;
    const data = validateChangelog(window.MELOVAULT_CHANGELOG);
    if (!data) return;
    const latest = data.versions[0];
    const reduce = typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const observer = !reduce && "IntersectionObserver" in window
      ? new IntersectionObserver(items => items.forEach(it => {
          if (it.isIntersecting) { it.target.classList.add("cl-in"); observer.unobserve(it.target); }
        }), { rootMargin: "0px 0px 12% 0px" })
      : null;
    let mode = "all";
    let shown = 0;

    function add(node) {
      if (observer) { node.classList.add("cl-pre"); observer.observe(node); }
      list.append(node);
      return node;
    }

    function updateMore() {
      const left = data.versions.length - shown;
      more.hidden = mode !== "all" || left <= 0;
      if (!more.hidden) more.textContent = "Xem thêm " + Math.min(PAGE_SIZE, left) + " bản cũ hơn (còn " + left + ")";
    }

    function addMilestones() {
      data.milestones.forEach(m => add(milestoneCard(m)));
    }

    function reset() {
      list.replaceChildren();
      if (mode === "all") {
        shown = Math.min(PAGE_SIZE, data.versions.length);
        data.versions.slice(0, shown).forEach((v, i) => add(versionCard(v, i === 0)));
        if (shown >= data.versions.length) addMilestones();
      } else {
        shown = data.versions.length;
        data.versions.forEach((v, i) => { if (v.highlight) add(versionCard(v, i === 0)); });
        addMilestones();
      }
      allButton.setAttribute("aria-pressed", String(mode === "all"));
      majorButton.setAttribute("aria-pressed", String(mode === "major"));
      updateMore();
      scheduleFill();
    }

    function showMore() {
      const from = shown;
      shown = Math.min(shown + PAGE_SIZE, data.versions.length);
      let firstNew = null;
      data.versions.slice(from, shown).forEach((v, i) => { const n = add(versionCard(v, from + i === 0)); if (!firstNew) firstNew = n; });
      if (shown >= data.versions.length) addMilestones();
      updateMore();
      const heading = firstNew && firstNew.querySelector("h3");
      if (heading) heading.focus({ preventScroll: true });
      scheduleFill();
    }

    let pending = false;
    function fill() {
      pending = false;
      const rect = list.getBoundingClientRect();
      const height = rect.height || 1;
      const progress = reduce ? 1 : Math.max(0, Math.min(1, (window.innerHeight * 0.6 - rect.top) / height));
      list.style.setProperty("--cl-fill", (progress * 100).toFixed(1) + "%");
    }
    function scheduleFill() {
      if (pending) return;
      pending = true;
      if (typeof window.requestAnimationFrame === "function") window.requestAnimationFrame(fill); else fill();
    }
    if (!reduce) {
      window.addEventListener("scroll", scheduleFill, { passive: true });
      window.addEventListener("resize", scheduleFill);
    }

    allButton.addEventListener("click", () => { if (mode !== "all") { mode = "all"; reset(); } });
    majorButton.addEventListener("click", () => { if (mode !== "major") { mode = "major"; reset(); } });
    more.addEventListener("click", showMore);

    document.querySelectorAll("[data-cl-latest]").forEach(node => { node.textContent = latest.version; });
    state.hidden = true;
    toolbar.hidden = false;
    reset();
    renderWhatsNew(latest);
  }

  try {
    renderChangelog();
  } catch {
    // Giữ nguyên câu dự phòng trong HTML; không làm hỏng phần còn lại của trang.
  }
})();
