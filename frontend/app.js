import { createClient, normalizeApiUrl } from './client.mjs';

const byId = id => document.getElementById(id);
const state = { client: null, entries: [], history: [], result: null, tab: 'matched' };
const currency = value => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'BRL' }).format(Number(value) || 0);
const date = value => value ? new Date(`${value.slice(0, 10)}T12:00:00`).toLocaleDateString('en-US') : '—';
const statusLabels = { CONCILIADO: 'MATCHED', DIVERGENTE: 'UNMATCHED', PENDENTE: 'PENDING', CONCLUIDO: 'COMPLETED', PROCESSANDO: 'PROCESSING', ERRO: 'FAILED' };
const typeLabels = { DEBITO: 'Debit', CREDITO: 'Credit' };

function element(tag, value, className) {
  const node = document.createElement(tag);
  if (value !== undefined && value !== null) node.textContent = String(value);
  if (className) node.className = className;
  return node;
}

function empty(target, message) {
  target.replaceChildren(element('div', message, 'empty'));
}

function table(target, headings, rows) {
  if (!rows.length) return empty(target, 'No records to display.');
  const tableNode = element('table');
  const thead = element('thead');
  const header = element('tr');
  headings.forEach(title => header.append(element('th', title)));
  thead.append(header);
  const tbody = element('tbody');
  rows.forEach(cells => {
    const row = element('tr');
    cells.forEach(cell => {
      const td = element('td');
      if (cell instanceof Node) td.append(cell);
      else td.textContent = String(cell ?? '—');
      row.append(td);
    });
    tbody.append(row);
  });
  tableNode.append(thead, tbody);
  target.replaceChildren(tableNode);
}

function pill(value) { return element('span', statusLabels[value] || value, `pill ${value}`); }
function description(main, detail) {
  const box = element('div');
  box.append(element('strong', main || 'No description'));
  if (detail) box.append(element('small', detail));
  return box;
}

let messageTimer;
function message(text, error = false) {
  const node = byId('message');
  node.textContent = text;
  node.classList.toggle('error', error);
  node.hidden = false;
  clearTimeout(messageTimer);
  messageTimer = setTimeout(() => { node.hidden = true; }, 6000);
}

function setBusy(form, busy) {
  form.querySelectorAll('button').forEach(button => { button.disabled = busy; });
}

function renderSession() {
  const connected = Boolean(state.client);
  byId('disconnect').hidden = !connected;
  byId('session').hidden = !connected;
  byId('session').textContent = connected
    ? `Connected as ${state.client.username}. ${state.client.username === 'viewer' ? 'Read-only access.' : 'Imports and entries enabled.'}`
    : '';
  const writable = connected && state.client.username === 'admin';
  document.querySelectorAll('[data-write]').forEach(button => { button.disabled = !writable; });
  byId('entry-form').querySelectorAll('input,select').forEach(input => { input.disabled = !writable; });
  byId('upload-form').querySelectorAll('input').forEach(input => { input.disabled = !writable; });
}

function renderStats() {
  const r = state.result;
  const items = [
    ['Statement items', r?.totalRegistrosExtrato ?? '—', r ? currency(r.valorTotalExtrato) : 'Waiting for import', ''],
    ['Matched', r?.totalConciliados ?? '—', r ? currency(r.valorTotalConciliado) : '—', 'green'],
    ['Unmatched', r?.totalDivergentes ?? '—', r ? currency(r.valorTotalDivergente) : '—', 'red'],
    ['Pending', r?.totalPendentesNoSistema ?? state.entries.filter(x => x.status === 'PENDENTE').length, 'Internal entries', 'amber']
  ];
  byId('stats').replaceChildren(...items.map(([label, count, detail, color]) => {
    const card = element('div', undefined, 'stat');
    card.append(element('span', label), element('strong', count, color), element('small', detail));
    return card;
  }));
  byId('result-note').textContent = r
    ? `${r.nomeArquivo} · ${r.percentualConciliado}% matched · Import #${r.idImportacao}`
    : 'Import a file to see results in this session. Import history remains in the API.';
}

function renderEntries() {
  table(byId('entries'), ['Date', 'Description', 'Type', 'Amount', 'Status', ''], state.entries.map(entry => {
    const action = element('span', '—');
    if (state.client?.username === 'admin' && entry.status === 'PENDENTE') {
      const button = element('button', 'Delete', 'btn secondary');
      button.type = 'button';
      button.addEventListener('click', () => removeEntry(entry.id));
      return [date(entry.dataLancamento), description(entry.descricao, entry.documento), typeLabels[entry.tipo] || entry.tipo, currency(entry.valor), pill(entry.status), button];
    }
    return [date(entry.dataLancamento), description(entry.descricao, entry.documento), typeLabels[entry.tipo] || entry.tipo, currency(entry.valor), pill(entry.status), action];
  }));
}

function renderHistory() {
  table(byId('history'), ['Date', 'File', 'Status', 'Items', 'Matched', 'Unmatched'], state.history.map(item => [
    date(item.dataImportacao), description(item.nomeArquivo, item.mensagemErro), pill(item.status), item.totalRegistros, item.totalConciliados, item.totalDivergentes
  ]));
}

function renderResults() {
  const r = state.result;
  if (!r) return empty(byId('results'), 'Import a statement to see reconciliation details.');
  if (state.tab === 'pending') {
    return table(byId('results'), ['Date', 'Entry', 'Type', 'Amount', 'Days open'], r.lancamentosPendentes.map(item => [
      date(item.dataLancamento), description(item.descricao, item.documento), typeLabels[item.tipo] || item.tipo, currency(item.valor), item.diasEmAberto
    ]));
  }
  const items = state.tab === 'matched' ? r.itensConciliados : r.itensDivergentes;
  table(byId('results'), ['Statement date', 'Bank transaction', 'Type', 'Amount', 'Linked entry', 'Status'], items.map(item => [
    date(item.dataExtrato), description(item.descricaoExtrato), typeLabels[item.tipoExtrato] || item.tipoExtrato, currency(item.valorExtrato),
    item.idLancamento ? description(item.descricaoLancamento, `${date(item.dataLancamento)} · ${item.documentoLancamento || 'no document'}`) : '—',
    pill(item.status)
  ]));
}

async function refresh() {
  const [entries, history] = await Promise.all([state.client.listEntries(), state.client.listHistory()]);
  state.entries = entries;
  state.history = history;
  renderEntries(); renderHistory(); renderStats(); renderResults();
}

async function health() {
  const indicator = byId('health');
  try {
    const base = normalizeApiUrl(byId('api-url').value);
    const response = await fetch(`${base}/actuator/health`, { cache: 'no-store' });
    if (!response.ok) throw new Error();
    indicator.textContent = '● API online';
    indicator.className = 'badge ok';
  } catch {
    indicator.textContent = '● API offline';
    indicator.className = 'badge fail';
  }
}

byId('connect-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget;
  setBusy(form, true);
  try {
    const client = createClient(byId('api-url').value, byId('username').value, byId('password').value);
    const [entries, history] = await Promise.all([client.listEntries(), client.listHistory()]);
    state.client = client;
    state.entries = entries;
    state.history = history;
    state.result = null;
    byId('password').value = '';
    renderSession(); renderStats(); renderEntries(); renderHistory(); renderResults();
    message('Connected to the live API.');
  } catch (error) { message(error.message, true); }
  finally { setBusy(form, false); renderSession(); health(); }
});

byId('disconnect').addEventListener('click', () => {
  state.client = null; state.entries = []; state.history = []; state.result = null;
  renderSession(); renderStats(); renderEntries(); renderHistory(); renderResults();
  message('Session ended in this tab.');
});

async function removeEntry(id) {
  try { await state.client.deleteEntry(id); await refresh(); message('Entry deleted.'); }
  catch (error) { message(error.message, true); }
}

byId('entry-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget;
  setBusy(form, true);
  try {
    await state.client.createEntry({
      dataLancamento: byId('entry-date').value,
      valor: Number(byId('entry-value').value),
      tipo: byId('entry-type').value,
      descricao: byId('entry-description').value.trim(),
      documento: byId('entry-document').value.trim() || null
    });
    form.reset(); byId('entry-date').value = new Date().toISOString().slice(0, 10);
    await refresh(); message('Entry saved in the API.');
  } catch (error) { message(error.message, true); }
  finally { setBusy(form, false); renderSession(); }
});

byId('seed-demo').addEventListener('click', async event => {
  const button = event.currentTarget;
  button.disabled = true;
  const examples = [
    { dataLancamento: '2026-10-29', valor: 1500, tipo: 'DEBITO', descricao: 'Supplier payment ABC', documento: 'INV-001' },
    { dataLancamento: '2026-10-29', valor: 3200, tipo: 'CREDITO', descricao: 'Customer payment XYZ', documento: 'INV-1042' },
    { dataLancamento: '2026-10-29', valor: 450, tipo: 'DEBITO', descricao: 'Pending energy bill', documento: 'BILL-001' }
  ];
  try {
    for (const entry of examples) await state.client.createEntry(entry);
    await refresh();
    message('Three sample entries were saved in the API. Import the sample CSV.');
  } catch (error) { await refresh().catch(() => {}); message(error.message, true); }
  finally { renderSession(); }
});

byId('upload-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget;
  const file = byId('statement').files[0];
  if (!file || !/\.(ofx|csv)$/i.test(file.name)) return message('Select an OFX or CSV file.', true);
  setBusy(form, true); message('Importing and reconciling the statement…');
  try {
    state.result = await state.client.importStatement(file);
    await refresh();
    message(`Reconciliation complete: ${state.result.totalConciliados} matched item(s).`);
  } catch (error) { await refresh().catch(() => {}); message(error.message, true); }
  finally { setBusy(form, false); renderSession(); }
});

document.querySelectorAll('[data-tab]').forEach(button => button.addEventListener('click', () => {
  state.tab = button.dataset.tab;
  document.querySelectorAll('[data-tab]').forEach(item => item.setAttribute('aria-selected', String(item === button)));
  renderResults();
}));

byId('api-url').addEventListener('change', health);
byId('entry-date').value = new Date().toISOString().slice(0, 10);
renderSession(); renderStats(); renderEntries(); renderHistory(); renderResults(); health();
