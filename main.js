(() => {
  "use strict";


  const isText = value => typeof value === "string" && value.trim().length > 0;

  function validDate(value) {
    if (typeof value !== "string" || !/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value)) return false;
    const [year, month, day] = value.split("-").map(Number);
    if (year < 1 || month < 1 || month > 12 || day < 1) return false;
    const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
    return day <= [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1];
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
    const box = document.getElementById("latest-announcement");
    if (!box || !latest || !latest.lines.length) return;
    const first = latest.lines[0][1];
    const date = latest.date.split("-").reverse().join("/");
    box.replaceChildren(
      element("strong", "", "Phiên bản " + latest.version + " — " + date + ": "),
      document.createTextNode(first)
    );
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
