// Lot-stage viewer and editor. Reads GET /api/lot-stages and saves with PUT /api/lot-stages/{id}.
// All text goes in via textContent, never innerHTML, since WIP locations are user-entered.

const API = '/api/lot-stages';

const els = {
  filter: document.getElementById('filter'),
  count: document.getElementById('count'),
  status: document.getElementById('status'),
  tableWrap: document.getElementById('table-wrap'),
  rows: document.getElementById('rows'),
  noMatch: document.getElementById('no-match'),
};

/** Stages in process order: { id, description, nextStages, wipLocations: [{ id, description }] }. */
let stages = [];
/** Id of the stage being edited, or null. Only one row is editable at a time. */
let editingId = null;

const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

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

function description(id) {
  return stages.find(s => s.id === id)?.description ?? id;
}

/** Mirrors WipLocation.defaultDescription on the server: "WAFER-PREP-001" in "Wafer Prep" → "Wafer Prep 1". */
function defaultWipDescription(stageDescription, wipId) {
  const m = /-(\d+)$/.exec(wipId);
  return m ? `${stageDescription} ${parseInt(m[1], 10)}` : wipId;
}

/** The API keys WIP locations by id, in display order; the page works with an ordered array. */
function toStage(id, s) {
  return {
    id,
    description: s.description,
    nextStages: s['next-stages'] ?? [],
    wipLocations: Object.entries(s['wip-locations'] ?? {}).map(([wipId, w]) => ({
      id: wipId,
      description: w?.description || wipId,
    })),
  };
}

async function load() {
  els.status.className = 'status';
  els.status.replaceChildren('Loading stages…');
  try {
    const res = await fetch(API);
    if (!res.ok) throw new Error(`Server returned ${res.status}`);
    const body = await res.json();
    stages = Object.entries(body).map(([id, s]) => toStage(id, s));
    els.status.replaceChildren();
    els.tableWrap.hidden = false;
    render();
  } catch (err) {
    els.status.className = 'status status--error';
    els.status.replaceChildren(
      `Could not load lot stages: ${networkAware(err)}. `,
      el('button', { type: 'button', class: 'btn', text: 'Retry', onclick: load }),
    );
  }
}

function matches(stage, text) {
  if (!text) return true;
  const haystack = [
    stage.id,
    stage.description,
    ...stage.nextStages.map(description),
    ...stage.wipLocations.flatMap(w => [w.id, w.description]),
  ];
  return haystack.some(v => v.toLowerCase().includes(text));
}

function render() {
  const text = els.filter.value.trim().toLowerCase();
  const visible = stages.filter(s => s.id === editingId || matches(s, text));

  els.rows.replaceChildren(...visible.map(s => (s.id === editingId ? editRow(s) : viewRow(s))));
  els.noMatch.hidden = visible.length > 0;
  els.count.textContent = visible.length === stages.length
    ? `${stages.length} stages`
    : `${visible.length} of ${stages.length} stages`;
}

function stageCell(stage) {
  return el('td', { class: 'stage-name', 'data-label': 'Stage' }, [
    stage.description,
    el('span', { class: 'stage-id', text: stage.id }),
  ]);
}

function viewRow(stage) {
  const next = stage.nextStages.length
    ? el('div', { class: 'chips' }, stage.nextStages.map(id =>
        el('button', {
          type: 'button',
          class: 'chip chip--stage',
          text: description(id),
          title: `Go to ${description(id)}`,
          onclick: () => jumpTo(id),
        })))
    : el('span', { class: 'final', text: 'Final stage' });

  const wip = stage.wipLocations.length
    ? el('div', { class: 'chips' }, stage.wipLocations.map(loc =>
        el('span', { class: 'chip chip--wip', title: loc.id }, [
          loc.description,
          ...(loc.description === loc.id ? [] : [el('span', { class: 'wip-id', text: loc.id })]),
        ])))
    : el('span', { class: 'empty', text: 'None' });

  return el('tr', { id: `stage-${stage.id}` }, [
    stageCell(stage),
    el('td', { 'data-label': 'Valid next stages' }, [next]),
    el('td', { 'data-label': 'WIP locations' }, [wip]),
    el('td', { class: 'actions' }, [
      el('button', {
        type: 'button',
        class: 'btn',
        text: 'Edit',
        disabled: editingId !== null,
        'aria-label': `Edit ${stage.description}`,
        onclick: () => startEdit(stage.id),
      }),
    ]),
  ]);
}

function editRow(stage) {
  const checked = new Set(stage.nextStages);
  const wip = stage.wipLocations.map(w => ({ ...w }));

  const error = el('div', { class: 'row-error', role: 'alert' });

  const nextOptions = el('fieldset', { class: 'next-options' }, [
    el('legend', { class: 'visually-hidden', text: `Valid next stages for ${stage.description}` }),
    ...stages.filter(s => s.id !== stage.id).map(s =>
      el('label', {}, [
        el('input', {
          type: 'checkbox',
          checked: checked.has(s.id),
          onchange: e => (e.target.checked ? checked.add(s.id) : checked.delete(s.id)),
        }),
        s.description,
      ])),
  ]);

  // One line per WIP location: fixed id, editable description, remove button.
  const wipList = el('div', { class: 'wip-list' });
  const renderWip = () => {
    wipList.replaceChildren(...wip.map((loc, i) =>
      el('div', { class: 'wip-line' }, [
        el('span', { class: 'wip-line-id', text: loc.id }),
        el('input', {
          type: 'text',
          value: loc.description,
          placeholder: defaultWipDescription(stage.description, loc.id),
          'aria-label': `Description for ${loc.id}`,
          oninput: e => { loc.description = e.target.value; },
        }),
        el('button', {
          type: 'button',
          class: 'chip-remove',
          text: '×',
          'aria-label': `Remove ${loc.id}`,
          onclick: () => { wip.splice(i, 1); renderWip(); wipIdInput.focus(); },
        }),
      ])));
    if (wip.length === 0) wipList.append(el('span', { class: 'empty', text: 'None' }));
  };

  const onEnter = e => {
    if (e.key === 'Enter') { e.preventDefault(); addWip(); }
  };
  const wipIdInput = el('input', {
    type: 'text',
    class: 'wip-add-id',
    placeholder: `e.g. ${stage.id.toUpperCase()}-004`,
    'aria-label': 'New WIP location ID',
    onkeydown: onEnter,
    oninput: () => {
      wipDescriptionInput.placeholder = wipIdInput.value.trim()
        ? defaultWipDescription(stage.description, wipIdInput.value.trim())
        : 'Description (optional)';
    },
  });
  const wipDescriptionInput = el('input', {
    type: 'text',
    placeholder: 'Description (optional)',
    'aria-label': 'New WIP location description',
    onkeydown: onEnter,
  });

  function addWip() {
    const id = wipIdInput.value.trim();
    if (!id) return;
    if (wip.some(w => w.id.toLowerCase() === id.toLowerCase())) {
      error.textContent = `${id} is already a WIP location for this stage.`;
      return;
    }
    error.textContent = '';
    wip.push({ id, description: wipDescriptionInput.value.trim() || defaultWipDescription(stage.description, id) });
    wipIdInput.value = '';
    wipDescriptionInput.value = '';
    wipDescriptionInput.placeholder = 'Description (optional)';
    renderWip();
    wipIdInput.focus();
  }

  renderWip();

  const saveBtn = el('button', { type: 'button', class: 'btn btn--primary', text: 'Save' });
  const cancelBtn = el('button', { type: 'button', class: 'btn', text: 'Cancel', onclick: cancelEdit });

  saveBtn.addEventListener('click', async () => {
    saveBtn.disabled = cancelBtn.disabled = true;
    saveBtn.textContent = 'Saving…';
    error.textContent = '';
    try {
      // Send next stages in process order, regardless of click order.
      const nextStages = stages.filter(s => checked.has(s.id)).map(s => s.id);
      const res = await fetch(`${API}/${encodeURIComponent(stage.id)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          'next-stages': nextStages,
          // A blank description is stored as unset, and the server shows the default.
          'wip-locations': Object.fromEntries(wip.map(w => [w.id, { description: w.description.trim() }])),
        }),
      });
      if (!res.ok) throw new Error(await errorMessage(res));
      const saved = toStage(stage.id, await res.json());
      stages = stages.map(s => (s.id === stage.id ? saved : s));
      editingId = null;
      render();
      highlight(stage.id);
    } catch (err) {
      error.textContent = `Could not save: ${networkAware(err)}`;
      saveBtn.disabled = cancelBtn.disabled = false;
      saveBtn.textContent = 'Save';
    }
  });

  return el('tr', { id: `stage-${stage.id}`, class: 'row--editing' }, [
    stageCell(stage),
    el('td', { 'data-label': 'Valid next stages' }, [nextOptions]),
    el('td', { 'data-label': 'WIP locations' }, [
      wipList,
      el('div', { class: 'wip-add' }, [
        wipIdInput,
        wipDescriptionInput,
        el('button', { type: 'button', class: 'btn', text: 'Add', onclick: addWip }),
      ]),
      error,
    ]),
    el('td', { class: 'actions' }, [saveBtn, cancelBtn]),
  ]);
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

function startEdit(id) {
  editingId = id;
  render();
  document.querySelector(`#stage-${CSS.escape(id)} input`)?.focus();
}

function cancelEdit() {
  const id = editingId;
  editingId = null;
  render();
  document.querySelector(`#stage-${CSS.escape(id)} .actions button`)?.focus();
}

function jumpTo(id) {
  let row = document.getElementById(`stage-${id}`);
  if (!row) {
    // Filtered out: clear the filter so the target is visible.
    els.filter.value = '';
    render();
    row = document.getElementById(`stage-${id}`);
  }
  if (!row) return;
  row.scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth', block: 'center' });
  highlight(id);
}

function highlight(id) {
  const row = document.getElementById(`stage-${id}`);
  if (!row) return;
  row.classList.add('row--highlight');
  setTimeout(() => row.classList.remove('row--highlight'), 1200);
}

els.filter.addEventListener('input', render);
document.addEventListener('keydown', e => {
  if (e.key === 'Escape' && editingId !== null) cancelEdit();
});

load();
