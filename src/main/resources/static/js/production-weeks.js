// Production-weeks page. Reads GET /api/production-weeks?year=; creates a year's weeks with
// POST /api/production-weeks/years/{year}, opens or closes one with PUT …/{year}/{week}/status and sets its
// capacity with PUT …/{year}/{week}/capacities. The selected year is kept in the URL (?year=).

const API = '/api/production-weeks';
const MIN_YEAR = 2000;
const MAX_YEAR = 2100;
// The lot domain's ChipType and ProcessType enums.
const CHIP_TYPES = ['Z_SERIES', 'H_SERIES', 'Q_SERIES', 'X_SERIES'];
const PROCESS_TYPES = ['A', 'B', 'C'];

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
/** [{ year, weekNumber, startDate, endDate, status: 'open' | 'closed', capacities: [{ chipType, processType, waferCount }] }] */
let weeks = null;
/** True while a request is running. */
let busy = false;
/** The week number whose capacity is being edited, or null. */
let editingWeek = null;

const dateFormat = new Intl.DateTimeFormat(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
const numberFormat = new Intl.NumberFormat();

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
  els.status.classList.toggle('text-danger', isError);
  els.status.replaceChildren(...(message ? [message] : []));
}

async function load(newYear) {
  year = newYear;
  editingWeek = null;
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
    replaceWeek(await res.json());
    setStatus(`Week ${week.weekNumber} ${status === 'open' ? 'reopened' : 'closed'}.`);
    render();
  } catch (err) {
    setStatus(`Could not update week ${week.weekNumber}: ${networkAware(err)}.`, true);
  } finally {
    setBusy(false);
  }
}

function replaceWeek(updated) {
  weeks = weeks.map(w => (w.weekNumber === updated.weekNumber ? updated : w));
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
  els.rows.replaceChildren(...(hasWeeks ? weeks.map(w => (w.weekNumber === editingWeek ? editRow(w) : viewRow(w))) : []));
  setBusy(busy);
}

/** Disables everything that would start another request or lose an open edit. */
function setBusy(value) {
  busy = value;
  const locked = value || editingWeek !== null;
  els.year.disabled = locked;
  els.prevYear.disabled = locked || year <= MIN_YEAR;
  els.nextYear.disabled = locked || year >= MAX_YEAR;
  els.seed.disabled = locked;
  for (const button of els.rows.querySelectorAll('button[data-row-action]')) button.disabled = locked;
}

function weekCell(week) {
  return el('td', { class: 'stage-name', 'data-label': 'Week' }, [
    String(week.weekNumber),
    ...(containsToday(week) ? [el('span', { class: 'this-week', text: 'This week' })] : []),
  ]);
}

function viewRow(week) {
  const open = week.status === 'open';
  return el('tr', { id: `week-${week.weekNumber}`, class: open ? '' : 'week-closed' }, [
    weekCell(week),
    el('td', { 'data-label': 'Week ending', class: 'text-nowrap', text: dateFormat.format(localDate(week.endDate)) }),
    el('td', { 'data-label': 'Status' }, [
      el('span', { class: open ? 'week-badge badge-open' : 'week-badge badge-closed', text: open ? 'Open' : 'Closed' }),
    ]),
    el('td', { 'data-label': 'Capacity' }, [capacityList(week.capacities)]),
    el('td', { class: 'actions' }, [
      el('button', {
        type: 'button',
        class: 'btn btn-sm btn-outline-secondary',
        text: 'Capacity',
        'data-row-action': '',
        'aria-label': `Edit capacity for week ${week.weekNumber}`,
        onclick: () => startEdit(week.weekNumber),
      }),
      el('button', {
        type: 'button',
        class: 'btn btn-sm btn-outline-secondary',
        text: open ? 'Close' : 'Reopen',
        'data-row-action': '',
        'aria-label': `${open ? 'Close' : 'Reopen'} week ${week.weekNumber}`,
        onclick: () => toggle(week),
      }),
    ]),
  ]);
}

function capacityList(capacities) {
  if (capacities.length === 0) return el('span', { class: 'text-body-tertiary', text: '—' });
  return el('ul', { class: 'capacity-list' }, capacities.map(c =>
    el('li', {}, [
      el('span', { class: 'capacity-label', text: capacityLabel(c) }),
      el('span', { class: 'capacity-count', text: numberFormat.format(c.waferCount) }),
    ])));
}

/** "All", "Z_SERIES", "Process B" or "Z_SERIES / A". */
function capacityLabel({ chipType, processType }) {
  if (chipType && processType) return `${chipType} / ${processType}`;
  if (chipType) return chipType;
  if (processType) return `Process ${processType}`;
  return 'All';
}

/** The week's row as a capacity editor: one line per rule, each a chip type, a process type and a wafer count. */
function editRow(week) {
  // Selects use '' for "any"; counts are kept as typed until saved.
  const lines = week.capacities.map(c => ({
    chipType: c.chipType ?? '',
    processType: c.processType ?? '',
    waferCount: String(c.waferCount),
  }));
  const error = el('div', { class: 'row-error small text-danger', role: 'alert' });
  const list = el('div', { class: 'capacity-editor' });

  const select = (value, anyLabel, options, label, onchange) =>
    el('select', { class: 'form-select form-select-sm', 'aria-label': label, onchange: e => onchange(e.target.value) }, [
      el('option', { value: '', text: anyLabel, selected: value === '' }),
      ...options.map(o => el('option', { value: o, text: o, selected: value === o })),
    ]);

  const renderLines = () => {
    list.replaceChildren(...lines.map((line, i) =>
      el('div', { class: 'capacity-line' }, [
        select(line.chipType, 'Any chip', CHIP_TYPES, `Chip type for line ${i + 1}`, v => { line.chipType = v; }),
        select(line.processType, 'Any process', PROCESS_TYPES, `Process type for line ${i + 1}`,
          v => { line.processType = v; }),
        el('input', {
          type: 'number',
          class: 'form-control form-control-sm',
          min: '0',
          step: '1',
          inputMode: 'numeric',
          placeholder: 'Wafers',
          value: line.waferCount,
          'aria-label': `Wafer count for line ${i + 1}`,
          oninput: e => { line.waferCount = e.target.value; },
        }),
        el('button', {
          type: 'button',
          class: 'btn-close chip-remove',
          'aria-label': `Remove line ${i + 1}`,
          onclick: () => { lines.splice(i, 1); renderLines(); },
        }),
      ])));
    if (lines.length === 0) list.append(el('span', { class: 'small text-body-tertiary', text: 'No capacity set.' }));
  };
  renderLines();

  const addBtn = el('button', {
    type: 'button',
    class: 'btn btn-sm btn-outline-secondary capacity-add',
    text: 'Add capacity',
    onclick: () => {
      lines.push({ chipType: '', processType: '', waferCount: '' });
      renderLines();
      list.querySelector('.capacity-line:last-child select')?.focus();
    },
  });

  /** Flags bad counts and repeated combinations in place; returns the entries to send, or null. */
  function validate() {
    const lineEls = [...list.querySelectorAll('.capacity-line')];
    lineEls.forEach(l => l.querySelectorAll('.is-invalid').forEach(f => f.classList.remove('is-invalid')));
    const problems = [];
    const seen = new Map();
    lines.forEach((line, i) => {
      const count = Number(line.waferCount);
      if (line.waferCount.trim() === '' || !Number.isInteger(count) || count < 0) {
        lineEls[i].querySelector('input').classList.add('is-invalid');
        problems.push('Each line needs a wafer count of 0 or more.');
      }
      const key = `${line.chipType}|${line.processType}`;
      if (seen.has(key)) {
        for (const j of [seen.get(key), i]) lineEls[j].querySelectorAll('select').forEach(s => s.classList.add('is-invalid'));
        problems.push(`${capacityLabel({ chipType: line.chipType, processType: line.processType })} is listed twice.`);
      } else {
        seen.set(key, i);
      }
    });
    if (problems.length > 0) {
      error.textContent = [...new Set(problems)].join(' ');
      return null;
    }
    return lines.map(line => ({
      chipType: line.chipType || null,
      processType: line.processType || null,
      waferCount: Number(line.waferCount),
    }));
  }

  const saveBtn = el('button', { type: 'button', class: 'btn btn-sm btn-primary', text: 'Save' });
  const cancelBtn = el('button', { type: 'button', class: 'btn btn-sm btn-outline-secondary', text: 'Cancel', onclick: cancelEdit });

  saveBtn.addEventListener('click', async () => {
    error.textContent = '';
    const capacities = validate();
    if (!capacities) return;
    saveBtn.disabled = cancelBtn.disabled = addBtn.disabled = true;
    saveBtn.textContent = 'Saving…';
    busy = true;
    try {
      const res = await fetch(`${API}/${week.year}/${week.weekNumber}/capacities`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(capacities),
      });
      if (!res.ok) throw new Error(await errorMessage(res));
      replaceWeek(await res.json());
      editingWeek = null;
      busy = false;
      setStatus(`Week ${week.weekNumber} capacity saved.`);
      render();
      document.querySelector(`#week-${week.weekNumber} .actions button`)?.focus();
    } catch (err) {
      busy = false;
      error.textContent = `Could not save: ${networkAware(err)}`;
      saveBtn.disabled = cancelBtn.disabled = addBtn.disabled = false;
      saveBtn.textContent = 'Save';
    }
  });

  return el('tr', { id: `week-${week.weekNumber}`, class: 'row--editing' }, [
    weekCell(week),
    el('td', { colSpan: 3, 'data-label': `Capacity for the week ending ${dateFormat.format(localDate(week.endDate))}` }, [
      list,
      addBtn,
      error,
    ]),
    el('td', { class: 'actions' }, [saveBtn, cancelBtn]),
  ]);
}

function startEdit(weekNumber) {
  editingWeek = weekNumber;
  render();
  const row = document.getElementById(`week-${weekNumber}`);
  (row.querySelector('select') ?? row.querySelector('.capacity-add'))?.focus();
}

function cancelEdit() {
  const weekNumber = editingWeek;
  editingWeek = null;
  render();
  document.querySelector(`#week-${weekNumber} .actions button`)?.focus();
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
document.addEventListener('keydown', e => {
  if (e.key === 'Escape' && editingWeek !== null && !busy) cancelEdit();
});

load(year);
