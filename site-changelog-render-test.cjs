// Regression test: the actual main.js must render real changelog data (all months, especially Nov/Dec).
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const assert = require('node:assert/strict');

const root = __dirname;
const main = fs.readFileSync(path.join(root, 'main.js'), 'utf8');
const source = fs.readFileSync(path.join(root, 'changelog.js'), 'utf8');
const nodeMap = new Map();

function element(tag = 'div') {
  return {
    tag, textContent: '', children: [], hidden: false, attributes: {},
    classList: { add() {}, remove() {} },
    style: { setProperty() {} },
    append(...parts) { this.children.push(...parts); },
    appendChild(part) { this.children.push(part); },
    replaceChildren(...parts) { this.children = [...parts]; },
    setAttribute(key, value) { this.attributes[key] = value; },
    addEventListener(name, callback) { this[name] = callback; },
    getBoundingClientRect() { return { height: 500, top: 0 }; },
    querySelector() { return element('h3'); },
    focus() {}
  };
}

const latestLabel = element('span');
const document = {
  getElementById(id) {
    if (!nodeMap.has(id)) nodeMap.set(id, element(id));
    return nodeMap.get(id);
  },
  createElement: element,
  createTextNode(text) { const node = element('text'); node.textContent = text; return node; },
  querySelectorAll(selector) { return selector === '[data-cl-latest]' ? [latestLabel] : []; }
};
const window = { matchMedia: () => ({ matches: true }), innerHeight: 600 };
const ctx = { window, document, navigator: { clipboard: {} }, console, };
vm.runInNewContext(source, ctx, { timeout: 1500 });
const allVersions = window.MELOVAULT_CHANGELOG.versions;
assert.ok(allVersions.length >= 30, 'Historical releases must remain available');

// Instrument only the test copy of main.js to access its real date validator.
const injected = main.replace(
  /  try \{\r?\n    renderChangelog\(\);/,
  '  window.__testValidDate = validDate;\n  try {\n    renderChangelog();'
).replace(
  /  \} catch \{\r?\n    \/\/ Giữ nguyên câu dự phòng/,
  '  } catch (error) {\n    console.error(error);\n    throw error;\n    // Giữ nguyên câu dự phòng'
);
assert.notEqual(main, injected, 'Main.js test injection failed');
vm.runInNewContext(injected, ctx, { timeout: 2500 });

const validDate = window.__testValidDate;
assert.equal(typeof validDate, 'function', 'Date validator missing');
for (let m = 1; m <= 12; m++) {
  const date = '2026-' + String(m).padStart(2, '0') + '-15';
  assert.equal(validDate(date), true, 'Must accept month ' + m);
}
for (const date of ['2026-02-30', '2026-11-31', '2026-12-32', '2026-13-01', '2026-00-01', 'not-a-date']) {
  assert.equal(validDate(date), false, 'Invalid date must be rejected: ' + date);
}
assert.equal(validDate('2024-02-29'), true, 'Leap-year February 29 must be accepted');
assert.equal(validDate('2025-02-29'), false, 'Non-leap February 29 must be rejected');

const list = nodeMap.get('cl-list');
assert.equal(list.children.length, Math.min(8, allVersions.length), 'First 8 release cards must render');
assert.equal(nodeMap.get('cl-state').hidden, true, 'Fallback message must be hidden');
assert.equal(nodeMap.get('cl-toolbar').hidden, false, 'Release filter must show');
assert.equal(latestLabel.textContent, allVersions[0].version, 'Latest release title must appear');
const announce = nodeMap.get('latest-announcement');
assert.ok(announce.children.length > 0, 'Latest-release announcement must render');
const more = nodeMap.get('cl-more');
while (list.children.length < allVersions.length) {
  assert.equal(typeof more.click, 'function', 'Load more must be operable');
  more.click();
}
assert.ok(list.children.length >= allVersions.length, 'All history entries must be reachable');
console.log('CHANGELOG_RENDER_PASS: ' + allVersions.length + ' releases, 12 months, opt-in notice, pagination, fallback hidden');
