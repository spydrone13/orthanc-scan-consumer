// Printable barcode sheet for one stage: /lot-stages/barcodes?stage=<id>.
// Each barcode encodes the internal id (what the scan form submits); the label shows the
// description. Barcodes are drawn by the vendored JsBarcode (global, loaded before this module).

const API = '/api/lot-stages';

const els = {
  title: document.getElementById('title'),
  status: document.getElementById('status'),
  sections: document.getElementById('sections'),
  print: document.getElementById('print'),
};

function el(tag, props = {}, children = []) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === 'class') node.className = value;
    else if (key === 'text') node.textContent = value;
    else node.setAttribute(key, value);
  }
  node.append(...children);
  return node;
}

function showError(message) {
  els.status.className = 'status status--error';
  els.status.textContent = message;
}

/**
 * A barcode SVG at a consistent bar width (so every label scans alike), shrinking only if it would
 * overflow its label. The viewBox lets CSS scale it without distorting the bars.
 */
function barcode(value) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  JsBarcode(svg, value, {
    format: 'CODE128',
    width: 1.5,
    height: 60,
    displayValue: true,
    font: 'ui-monospace, Menlo, Consolas, monospace',
    fontSize: 14,
    margin: 10,
  });
  if (!svg.getAttribute('viewBox')) {
    svg.setAttribute('viewBox', `0 0 ${parseFloat(svg.getAttribute('width'))} ${parseFloat(svg.getAttribute('height'))}`);
  }
  svg.removeAttribute('style');
  svg.setAttribute('role', 'img');
  svg.setAttribute('aria-label', `Barcode ${value}`);
  return svg;
}

function label(description, value) {
  let code;
  try {
    code = barcode(value);
  } catch {
    code = el('p', { class: 'status--error', text: `${value} can't be encoded as a barcode` });
  }
  return el('div', { class: 'barcode-label' }, [
    el('div', { class: 'barcode-label-title', text: description }),
    code,
  ]);
}

function section(heading, labels) {
  return el('section', { class: 'barcode-section' }, [
    el('h2', { class: 'barcode-section-title', text: heading }),
    el('div', { class: 'barcode-grid' }, labels),
  ]);
}

function wipLabels(stage) {
  return Object.entries(stage['wip-locations'] ?? {})
    .map(([wipId, w]) => label(w?.description || wipId, wipId));
}

async function load() {
  const stageId = new URLSearchParams(location.search).get('stage');
  if (!stageId) {
    showError('No stage selected. Open this page from the Barcodes button on Lot Stages.');
    return;
  }
  if (typeof JsBarcode !== 'function') {
    showError('The barcode library failed to load.');
    return;
  }

  els.status.textContent = 'Loading…';
  let stages;
  try {
    const res = await fetch(API);
    if (!res.ok) throw new Error(`Server returned ${res.status}`);
    stages = await res.json();
  } catch (err) {
    showError(`Could not load lot stages: ${err instanceof TypeError ? 'the server could not be reached' : err.message}.`);
    return;
  }

  const stage = stages[stageId];
  if (!stage) {
    showError(`Unknown stage: ${stageId}`);
    return;
  }

  document.title = `Barcodes — ${stage.description}`;
  els.title.textContent = `Barcodes — ${stage.description}`;

  const nextIds = (stage['next-stages'] ?? []).filter(id => stages[id]);
  // This stage's WIP locations first, then one section per next stage: its move-to-stage
  // barcode followed by its WIP locations.
  const sections = [];
  const ownWip = wipLabels(stage);
  if (ownWip.length) {
    sections.push(section('WIP Locations', ownWip));
  }
  for (const id of nextIds) {
    const next = stages[id];
    sections.push(section(`Next Stage: ${next.description}`, [label(next.description, id), ...wipLabels(next)]));
  }

  els.status.textContent = '';
  if (sections.length === 0) {
    els.status.className = 'status';
    els.status.textContent = 'This stage has no next stages or WIP locations to print.';
    return;
  }
  els.sections.replaceChildren(...sections);
  els.print.hidden = false;
}

els.print.addEventListener('click', () => window.print());

load();
