// Failed-scans page. Reads GET /api/dead-letters; retries with POST /api/dead-letters/{id}/retry (or
// /retry for all) and discards with DELETE /api/dead-letters/{id}. Message bodies are arbitrary text, so
// everything goes in via textContent, never innerHTML.

const API = '/api/dead-letters';

const els = {
  count: document.getElementById('count'),
  refresh: document.getElementById('refresh'),
  retryAll: document.getElementById('retry-all'),
  status: document.getElementById('status'),
  tableWrap: document.getElementById('table-wrap'),
  rows: document.getElementById('rows'),
  empty: document.getElementById('empty'),
};

/** { total, deadLetters: [{ id, clientId, lotId, userName, reason, stackTrace, failedAt, body }] } */
let data = { total: 0, deadLetters: [] };
/** True while a request is running; all action buttons are disabled meanwhile. */
let busy = false;

const timeFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' });

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

function setStatus(message, isError = false) {
  els.status.className = isError ? 'status status--error' : 'status';
  els.status.replaceChildren(...(message ? [message] : []));
}

async function load(message) {
  setBusy(true);
  try {
    const res = await fetch(API);
    if (!res.ok) throw new Error(await errorMessage(res));
    data = await res.json();
    setStatus(message);
    render();
  } catch (err) {
    setStatus(`Could not load failed scans: ${networkAware(err)}.`, true);
  } finally {
    setBusy(false);
  }
}

function render() {
  const { total, deadLetters } = data;
  els.tableWrap.hidden = deadLetters.length === 0;
  els.empty.hidden = deadLetters.length > 0;
  els.count.textContent = total > deadLetters.length
    ? `Showing the oldest ${deadLetters.length} of ${total} failed scans`
    : `${total} failed ${total === 1 ? 'scan' : 'scans'}`;
  els.rows.replaceChildren(...deadLetters.map(row));
  els.retryAll.disabled = busy || deadLetters.length === 0;
}

function setBusy(value) {
  busy = value;
  els.refresh.disabled = value;
  els.retryAll.disabled = value || data.deadLetters.length === 0;
  for (const button of els.rows.querySelectorAll('button')) button.disabled = value;
}

function row(deadLetter) {
  const scanCell = deadLetter.clientId
    ? el('td', { class: 'stage-name', 'data-label': 'Scan' }, [el('span', { class: 'mono', text: deadLetter.clientId })])
    : el('td', { class: 'stage-name', 'data-label': 'Scan' }, [
        'Unreadable message',
        el('span', { class: 'stage-id', text: deadLetter.id }),
      ]);

  const reason = el('td', { 'data-label': 'Reason' }, [
    el('div', { class: 'reason', text: deadLetter.reason ?? 'Unknown' }),
    ...(deadLetter.stackTrace ? [details('Stack trace', deadLetter.stackTrace)] : []),
    details('Message', deadLetter.body),
  ]);

  const discardBtn = el('button', { type: 'button', class: 'btn btn--danger', text: 'Discard', disabled: busy });
  // Two clicks to discard: the first arms the button for a few seconds.
  let armed = null;
  discardBtn.addEventListener('click', () => {
    if (!armed) {
      discardBtn.textContent = 'Confirm discard';
      armed = setTimeout(() => { armed = null; discardBtn.textContent = 'Discard'; }, 4000);
      return;
    }
    clearTimeout(armed);
    act(`${API}/${encodeURIComponent(deadLetter.id)}`, 'DELETE', `Discarded ${label(deadLetter)}.`);
  });

  return el('tr', {}, [
    scanCell,
    el('td', { 'data-label': 'Lot', text: deadLetter.lotId ?? '—' }),
    el('td', { 'data-label': 'User', text: deadLetter.userName ?? '—' }),
    el('td', { 'data-label': 'Failed', class: 'nowrap', text: deadLetter.failedAt ? timeFormat.format(new Date(deadLetter.failedAt)) : '—' }),
    reason,
    el('td', { class: 'actions' }, [
      el('button', {
        type: 'button',
        class: 'btn',
        text: 'Retry',
        disabled: busy,
        onclick: () => act(`${API}/${encodeURIComponent(deadLetter.id)}/retry`, 'POST', `Retried ${label(deadLetter)}.`),
      }),
      discardBtn,
    ]),
  ]);
}

function details(summary, text) {
  return el('details', { class: 'detail' }, [el('summary', { text: summary }), el('pre', { text })]);
}

function label(deadLetter) {
  return deadLetter.clientId ? `scan ${deadLetter.clientId}` : 'the unreadable message';
}

/** Runs an action, then reloads the list with the outcome as the status line. */
async function act(url, method, doneMessage) {
  setBusy(true);
  try {
    const res = await fetch(url, { method });
    if (res.status === 404) {
      await load('That scan is no longer in the failed list.');
      return;
    }
    if (!res.ok) throw new Error(await errorMessage(res));
    let message = doneMessage;
    if (res.status === 200) {
      const { replayed } = await res.json();
      message = `Retried ${replayed} ${replayed === 1 ? 'scan' : 'scans'}.`;
    }
    await load(message);
  } catch (err) {
    setStatus(`Could not complete that: ${networkAware(err)}.`, true);
    setBusy(false);
  }
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

els.refresh.addEventListener('click', () => load());
els.retryAll.addEventListener('click', () => act(`${API}/retry`, 'POST'));

load();
