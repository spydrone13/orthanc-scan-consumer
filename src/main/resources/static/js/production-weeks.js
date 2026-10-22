// Production-weeks page. Reads GET /api/production-weeks?year=; creates a year's weeks with
// POST /api/production-weeks/years/{year} and opens or closes one with
// PUT /api/production-weeks/{year}/{week}/status. The selected year is kept in the URL (?year=).

const API = '/api/production-weeks';
const MIN_YEAR = 2000;
const MAX_YEAR = 2100;

const els = {
  year: document.getElementById('year'),
  prevYear: document.getElementById('prev-year'),
  nextYear: document.getElementById('next-year'),
  count: document.getElementById('count'),
  status: document.getElementById('status'),
  tableWrap: document.getElementById('table-wrap'),
  rows: document.getElementById('rows'),
  empty: document.getElementById('empty'),
  emptyMessage: document.getElementById('empty-message'),
  seed: document.getElementById('seed'),
};

/** The year shown; weeks is null until it has loaded. */
let year = initialYear();
/** [{ year, weekNumber, startDate, endDate, status: 'open' | 'closed' }] */
let weeks = null;
/** True while a request is running; all buttons and the year input are disabled meanwhile. */
let busy = false;

const dateFormat = new Intl.DateTimeFormat(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });

function el(tag, props = {}, children = []) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === 'class') node.className = value;
    else if (key === 'text') node.textContent = value;
    else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else if (key in node) node[key] = value;
    else node.setAttribute(key, value);
  }
  node.append(...children);
  return node;
}

function initialYear() {
  const fromUrl = Number(new URLSearchParams(location.search).get('year'));
  return validYear(fromUrl) ? fromUrl : new Date().getFullYear();
}

function validYear(value) {
  return Number.isInteger(value) && value >= MIN_YEAR && value <= MAX_YEAR;
}

function setStatus(message, isError = false) {
  els.status.className = isError ? 'status text-danger' : 'status';
  els.status.replaceChildren(...(message ? [message] : []));
}

async function load(newYear) {
  year = newYear;
  els.year.value = year;
  history.replaceState(null, '', `?year=${year}`);
  weeks = null;
  render();
  setBusy(true);
  try {
    const res = await fetch(`${API}?year=${year}`);
    if (!res.ok) throw new Error(await errorMessage(res));
    const loaded = await res.json();
    // Ignore a response for a year the user has already moved away from.
    if (newYear !== year) return;
    weeks = loaded;
    setStatus();
    render();
  } catch (err) {
    if (newYear === year) setStatus(`Could not load production weeks: ${networkAware(err)}.`, true);
  } finally {
    if (newYear === year) setBusy(false);
  }
}

async function seed() {
  setBusy(true);
  try {
    const res = await fetch(`${API}/years/${year}`, { method: 'POST' });
    if (!res.ok) throw new Error(await errorMessage(res));
    weeks = await res.json();
    setStatus(`Created ${weeks.length} weeks for ${year}.`);
    render();
  } catch (err) {
    setStatus(`Could not create weeks: ${networkAware(err)}.`, true);
  } finally {
    setBusy(false);
  }
}

async function toggle(week) {
  const status = week.status === 'open' ? 'closed' : 'open';
  setBusy(true);
  try {
    const res = await fetch(`${API}/${week.year}/${week.weekNumber}/status`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status }),
    });
    if (!res.ok) throw new Error(await errorMessage(res));
    const updated = await res.json();
    weeks = weeks.map(w => (w.weekNumber === updated.weekNumber ? updated : w));
    setStatus(`Week ${updated.weekNumber} ${updated.status === 'open' ? 'reopened' : 'closed'}.`);
    render();
  } catch (err) {
    setStatus(`Could not update week ${week.weekNumber}: ${networkAware(err)}.`, true);
  } finally {
    setBusy(false);
  }
}

function render() {
  const loaded = weeks !== null;
  const hasWeeks = loaded && weeks.length > 0;
  els.tableWrap.hidden = !hasWeeks;
  els.empty.hidden = !loaded || hasWeeks;
  els.emptyMessage.textContent = `No production weeks for ${year} yet.`;
  els.seed.textContent = `Create weeks for ${year}`;
  if (hasWeeks) {
    const closed = weeks.filter(w => w.status === 'closed').length;
    els.count.textContent = `${weeks.length} weeks · ${closed} closed`;
  } else {
    els.count.textContent = '';
  }
  els.rows.replaceChildren(...(hasWeeks ? weeks.map(row) : []));
}

function setBusy(value) {
  busy = value;
  els.year.disabled = value;
  els.prevYear.disabled = value || year <= MIN_YEAR;
  els.nextYear.disabled = value || year >= MAX_YEAR;
  els.seed.disabled = value;
  for (const button of els.rows.querySelectorAll('button')) button.disabled = value;
}

function row(week) {
  const open = week.status === 'open';
  const current = containsToday(week);
  return el('tr', { class: open ? '' : 'week-closed' }, [
    el('td', { class: 'stage-name', 'data-label': 'Week' }, [
      String(week.weekNumber),
      ...(current ? [el('span', { class: 'this-week', text: 'This week' })] : []),
    ]),
    el('td', { 'data-label': 'Week ending', class: 'text-nowrap', text: dateFormat.format(localDate(week.endDate)) }),
    el('td', { 'data-label': 'Status' }, [
      el('span', { class: open ? 'week-badge badge-open' : 'week-badge badge-closed', text: open ? 'Open' : 'Closed' }),
    ]),
    el('td', { class: 'actions' }, [
      el('button', {
        type: 'button',
        class: 'btn btn-sm btn-outline-secondary',
        text: open ? 'Close' : 'Reopen',
        disabled: busy,
        'aria-label': `${open ? 'Close' : 'Reopen'} week ${week.weekNumber}`,
        onclick: () => toggle(week),
      }),
    ]),
  ]);
}

/** Dates come as YYYY-MM-DD; parsed as local dates so they don't shift with the time zone. */
function localDate(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function containsToday(week) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return localDate(week.startDate) <= today && today <= localDate(week.endDate);
}

/** fetch rejects with a TypeError when the server can't be reached at all. */
function networkAware(err) {
  return err instanceof TypeError ? 'the server could not be reached' : err.message;
}

async function errorMessage(res) {
  try {
    const body = await res.json();
    return body.detail || body.message || `Server returned ${res.status}`;
  } catch {
    return `Server returned ${res.status}`;
  }
}

els.prevYear.addEventListener('click', () => load(year - 1));
els.nextYear.addEventListener('click', () => load(year + 1));
els.year.addEventListener('change', () => {
  const value = Number(els.year.value);
  if (validYear(value)) {
    load(value);
  } else {
    els.year.value = year;
    setStatus(`Pick a year between ${MIN_YEAR} and ${MAX_YEAR}.`, true);
  }
});
els.seed.addEventListener('click', seed);

load(year);
