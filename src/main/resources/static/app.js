import { LABELS, flagLabel, rangeLabel, valueLabel, words, plotModel, RequestGate, parseApiResponse, BrowserCapabilityError } from './client-model.js';

const byId = id => document.getElementById(id);
const gate = new RequestGate();
const state = { context: null, view: 'results', offsets: { results: 0, 'access-log': 0 }, totals: {}, detailTrigger: null };
const views = ['results', 'trend', 'access-log'];
const svgNS = 'http://www.w3.org/2000/svg';

function node(tag, text, className) {
  const element = document.createElement(tag);
  if (text != null) element.textContent = String(text);
  if (className) element.className = className;
  return element;
}
function secondary(parent, text) { parent.append(node('span', text, 'cell-secondary')); }
function status(view, text, kind = '') {
  const element = byId(`${view}-status`);
  element.textContent = text;
  element.className = `view-status${kind ? ` is-${kind}` : ''}`;
}
function flagNode(result) {
  const known = ['NORMAL', 'HIGH', 'LOW', 'NO_APPLICABLE_FLAG'].includes(result.flag);
  const wrap = node('div');
  wrap.append(node('span', flagLabel(result.flag), `flag flag--${known ? result.flag.toLowerCase().replaceAll('_', '-') : 'none'}`));
  if (result.flagReason && result.flagReason !== 'NONE') secondary(wrap, words(result.flagReason));
  return wrap;
}
function closeDetail(restoreFocus = false) {
  gate.cancel('detail');
  byId('result-detail').hidden = true;
  byId('detail-body').replaceChildren();
  if (restoreFocus) {
    if (state.detailTrigger?.isConnected) state.detailTrigger.focus();
    else byId('results-heading').focus();
  }
  state.detailTrigger = null;
}
function clearView(view) {
  byId(`${view}-body`).replaceChildren();
  byId(`${view}-count`).textContent = '—';
  byId(`${view}-empty`).hidden = true;
  delete state.totals[view];
  if (view === 'trend') {
    byId('trend-chart-content').replaceChildren();
    byId('trend-chart-title').textContent = 'Measurement trend';
    byId('trend-chart-description').textContent = 'No trend loaded. Exact values will appear in the table below.';
  } else {
    byId(`${view}-prev`).disabled = true;
    byId(`${view}-next`).disabled = true;
    byId(`${view}-page-label`).textContent = 'Not loaded';
    if (view === 'results') closeDetail();
  }
}
function clearAll() {
  for (const view of views) {
    clearView(view);
    status(view, 'Apply a test context to load this view.');
    byId(`view-${view}`).removeAttribute('aria-busy');
  }
}
function invalidateContext(message = 'Context changed. Apply the test context to load data.') {
  gate.invalidate();
  state.context = null;
  state.offsets = { results: 0, 'access-log': 0 };
  clearAll();
  for (const name of ['patient', 'actor', 'role']) byId(`current-${name}`).textContent = 'Not selected';
  byId('context-error').hidden = true;
  byId('context-error').textContent = '';
  byId('app-status').textContent = message;
}
function failureMessage(statusCode) {
  if (statusCode === 404) return 'Resource unavailable for this test context.';
  if (statusCode === 400) return 'Request rejected. Check the test context and query fields.';
  if (statusCode === 503) return 'Service unavailable. No content is displayed. Select the view to retry.';
  return 'The request could not be completed. No content is displayed. Select the view to retry.';
}

async function read(channel, suffix, success, failure) {
  if (!state.context) return;
  const context = state.context;
  const token = gate.begin(channel);
  const timer = setTimeout(() => {
    if (gate.current(channel, token)) { token.timedOut = true; token.controller.abort(); }
  }, 30000);
  try {
    const response = await fetch(`/patients/${encodeURIComponent(context.patientId)}${suffix}`, {
      method: 'GET', cache: 'no-store', credentials: 'omit', redirect: 'error',
      headers: { 'X-Test-Actor-Id': context.actorId, 'X-Test-Actor-Role': context.role },
      signal: token.controller.signal
    });
    if (!gate.current(channel, token)) return;
    if (response.status !== 200) { failure(failureMessage(response.status)); return; }
    const body = parseApiResponse(await response.text());
    if (!gate.current(channel, token)) return;
    success(body);
  } catch (error) {
    if (gate.current(channel, token)) {
      failure(error instanceof BrowserCapabilityError
        ? 'This browser cannot preserve decimal source text. Use a browser with JSON parse source support. No content is displayed.'
        : token.timedOut ? 'Request timed out. No content is displayed. Select the view to retry.' : failureMessage(0));
    }
  } finally {
    clearTimeout(timer);
    if (gate.current(channel, token)) {
      gate.finish(channel, token);
      if (channel !== 'detail') byId(`view-${channel}`).removeAttribute('aria-busy');
    }
  }
}
function checkPage(page) {
  if (!page || !Array.isArray(page.items) || !Number.isSafeInteger(page.total) || page.total < 0 ||
      !Number.isSafeInteger(page.offset) || !Number.isSafeInteger(page.limit)) throw new Error('Unexpected response');
}
function pageState(view, page) {
  state.totals[view] = page.total;
  state.offsets[view] = page.offset;
  byId(`${view}-count`).textContent = String(page.total);
  byId(`${view}-empty`).hidden = page.items.length !== 0;
  byId(`${view}-page-label`).textContent = page.items.length
    ? `${page.offset + 1}–${page.offset + page.items.length} of ${page.total}` : `0 shown of ${page.total}`;
  byId(`${view}-prev`).disabled = page.offset <= 0;
  byId(`${view}-next`).disabled = page.offset + page.limit >= page.total || page.offset + page.limit > 1000000;
  status(view, `${page.items.length} rows loaded. Access and interpretation were checked by the server.`);
}
function resultValue(result) {
  const wrap = node('div', Array.isArray(result.components) && result.components.length ? 'Component result' : valueLabel(result), 'numeric');
  for (const component of result.components ?? []) {
    secondary(wrap, `${LABELS[component.code] ?? component.code}: ${valueLabel(component)}; range ${rangeLabel(component.range)}; ${flagLabel(component.flag)}`);
  }
  return wrap;
}
function detailButton(id, label = 'View details') {
  const button = node('button', label, 'detail-link');
  button.type = 'button';
  button.addEventListener('click', () => loadDetail(id, button));
  return button;
}
function renderResult(result) {
  const row = node('tr');
  row.append(node('td', result.effective, 'numeric'));
  const name = node('td', LABELS[result.code] ?? result.code, 'cell-primary');
  secondary(name, `${result.code} · ${result.derived ? 'Calculated view' : words(result.status)}`);
  secondary(name, words(result.releaseState));
  row.append(name);
  const value = node('td'); value.append(resultValue(result)); row.append(value);
  row.append(node('td', rangeLabel(result.range), 'numeric'));
  const flag = node('td'); flag.append(flagNode(result)); row.append(flag);
  const detail = node('td');
  const button = detailButton(result.id);
  button.setAttribute('aria-label', `Details: ${LABELS[result.code] ?? result.code}, ${result.effective}`);
  detail.append(button); row.append(detail);
  return row;
}
function loadResults() {
  clearView('results'); status('results', 'Loading results…', 'loading');
  byId('view-results').setAttribute('aria-busy', 'true');
  const query = new URLSearchParams({ offset: state.offsets.results, limit: byId('results-page-size').value });
  return read('results', `/results?${query}`, page => {
    checkPage(page);
    const rows = page.items.map(renderResult);
    byId('results-body').replaceChildren(...rows);
    pageState('results', page);
  }, message => { clearView('results'); status('results', message, 'error'); });
}
function metadata(container, pairs) {
  const list = node('dl', null, 'detail-grid');
  for (const [label, value] of pairs) {
    const group = node('div'); group.append(node('dt', label), node('dd', value)); list.append(group);
  }
  container.append(list);
}
function appendLinks(container, heading, links) {
  if (!links.length) return;
  container.append(node('h4', heading));
  const list = node('ul');
  for (const item of links) {
    const row = node('li'); row.append(detailButton(item.id, item.id));
    if (item.effective) secondary(row, item.effective);
    list.append(row);
  }
  container.append(list);
}
function renderDetail(result) {
  if (!result || typeof result.id !== 'string') throw new Error('Unexpected detail');
  const content = node('div');
  metadata(content, [
    ['Result', `${LABELS[result.code] ?? result.code} · ${result.code}`], ['Observation / view ID', result.id],
    ['Measurement time · UTC', result.effective], ['Status', words(result.status)],
    ['Value', valueLabel(result)], ['Display unit / machine unit', `${result.unit ?? '—'} / ${result.unitCode ?? '—'}`],
    ['Reference range', rangeLabel(result.range)], ['Flag', `${flagLabel(result.flag)}; ${words(result.flagReason)}`],
    ['Release state', words(result.releaseState)],
    ['Critical classification', result.criticality == null ? 'Not independently classified' : words(result.criticality)],
    ['Source critical marker', result.sourceCritical ? 'Present' : 'Absent']
  ]);
  for (const component of result.components ?? []) {
    content.append(node('h4', `${LABELS[component.code] ?? component.code} · ${component.code}`));
    metadata(content, [['Value', valueLabel(component)], ['Range', rangeLabel(component.range)],
      ['Flag', `${flagLabel(component.flag)}; ${words(component.flagReason)}`]]);
  }
  if (result.derived) {
    content.append(node('p', 'This is a calculation from caller-visible inputs. Its release state is inherited; it is not independently reviewed or released.', 'detail-note'));
    metadata(content, [['Calculation', result.computed ? 'Computed' : `Cannot compute — ${words(result.cannotComputeReason)}`], ['Method', result.method ?? 'Not recorded']]);
    if (result.notice) content.append(node('p', result.notice, 'detail-note'));
    appendLinks(content, 'Visible calculation inputs', (result.sources ?? []).map(source => ({ id: source.observationId, effective: source.effective })));
    for (const source of result.sourceReleaseStates ?? []) {
      content.append(node('p', `${source.observationId}: ${words(source.releaseState)}; ${words(source.criticality)}`, 'cell-secondary'));
    }
  }
  appendLinks(content, 'Correction of', result.correctionOf ? [{ id: result.correctionOf }] : []);
  appendLinks(content, 'Visible corrections', (result.correctedBy ?? []).map(id => ({ id })));
  byId('detail-body').replaceChildren(content);
}
function loadDetail(id, trigger) {
  const stableTrigger = trigger?.closest('#detail-body') ? state.detailTrigger : trigger;
  closeDetail();
  state.detailTrigger = stableTrigger;
  byId('result-detail').hidden = false;
  byId('detail-body').replaceChildren(node('p', 'Loading result details…', 'view-status'));
  byId('result-detail').focus();
  return read('detail', `/results/${encodeURIComponent(id)}`, renderDetail,
    message => byId('detail-body').replaceChildren(node('p', message, 'view-status is-error')));
}
function svg(tag, attrs = {}, text) {
  const element = document.createElementNS(svgNS, tag);
  for (const [name, value] of Object.entries(attrs)) element.setAttribute(name, String(value));
  if (text != null) element.textContent = String(text);
  return element;
}
function drawChart(points, code) {
  const group = byId('trend-chart-content');
  const model = plotModel(points);
  const left = 85, right = 870, top = 35, bottom = 265;
  const x = marker => left + marker.x * (right - left), y = marker => top + marker.y * (bottom - top);
  const unit = points.find(point => point.unit)?.unit ?? '';
  byId('trend-chart-title').textContent = `${LABELS[code] ?? code} trend · ${unit}`;
  byId('trend-chart-description').textContent = `${points.length} visible points; ${model.gaps} unplotted points. Lines break at gaps. Exact timestamps, values, ranges, flags and any missing-input reasons follow in the table.`;
  group.replaceChildren();
  if (model.min === null) {
    group.append(svg('text', { x: 450, y: 155, 'text-anchor': 'middle', class: 'chart-gap' }, 'No representable chart points. See the exact-value table.'));
    return;
  }
  group.append(svg('text', { x: left, y: 18, class: 'chart-label' }, unit));
  for (let index = 0; index <= 4; index++) {
    const position = top + index * (bottom - top) / 4;
    const value = model.max - index * (model.max - model.min) / 4;
    group.append(svg('line', { x1: left, x2: right, y1: position, y2: position, class: 'chart-grid' }));
    group.append(svg('text', { x: left - 10, y: position + 4, 'text-anchor': 'end', class: 'chart-label' }, Number(value.toPrecision(5))));
  }
  group.append(svg('line', { x1: left, x2: right, y1: bottom, y2: bottom, class: 'chart-axis' }));
  for (const segment of model.segments) {
    if (segment.length > 1) group.append(svg('polyline', { points: segment.map(marker => `${x(marker)},${y(marker)}`).join(' '), class: 'chart-line' }));
  }
  for (const marker of model.markers) {
    const cx = x(marker), cy = y(marker), size = 5;
    let shape;
    if (marker.point.flag === 'HIGH' || marker.point.flag === 'LOW') {
      const direction = marker.point.flag === 'HIGH' ? -1 : 1;
      shape = svg('polygon', { points: `${cx},${cy + direction * size} ${cx - size},${cy - direction * size} ${cx + size},${cy - direction * size}`, class: 'chart-point' });
    } else if (marker.point.flag === 'NORMAL') {
      shape = svg('circle', { cx, cy, r: size, class: 'chart-point' });
    } else shape = svg('rect', { x: cx - size, y: cy - size, width: size * 2, height: size * 2, class: 'chart-point' });
    shape.append(svg('title', {}, `${marker.point.effective}: ${valueLabel(marker.point)}; ${flagLabel(marker.point.flag)}`));
    group.append(shape);
  }
  const first = points[0]?.effective ?? '', last = points.at(-1)?.effective ?? '';
  group.append(svg('text', { x: left, y: 292, class: 'chart-label' }, first.slice(0, 10)));
  group.append(svg('text', { x: right, y: 292, 'text-anchor': 'end', class: 'chart-label' }, last.slice(0, 10)));
  if (model.gaps) group.append(svg('text', { x: 450, y: 313, 'text-anchor': 'middle', class: 'chart-gap' }, `${model.gaps} unplotted points — lines break at gaps`));
}
function loadTrend() {
  clearView('trend'); status('trend', 'Loading trend…', 'loading');
  byId('view-trend').setAttribute('aria-busy', 'true');
  const code = byId('trend-code').value, query = new URLSearchParams({ code });
  if (byId('trend-from').value) query.set('from', byId('trend-from').value);
  if (byId('trend-to').value) query.set('to', byId('trend-to').value);
  return read('trend', `/trend?${query}`, response => {
    if (!response || response.code !== code || !Array.isArray(response.points) || !Number.isSafeInteger(response.total)) throw new Error('Unexpected trend');
    const rows = response.points.map(point => {
      const row = node('tr');
      row.append(node('td', point.effective, 'numeric'), node('td', valueLabel(point), 'numeric'), node('td', rangeLabel(point.range), 'numeric'));
      const flag = node('td'); flag.append(flagNode(point)); row.append(flag); return row;
    });
    drawChart(response.points, code);
    byId('trend-body').replaceChildren(...rows);
    byId('trend-count').textContent = String(response.total);
    byId('trend-empty').hidden = response.points.length !== 0;
    status('trend', `${response.points.length} points loaded in server order. Missing numeric values remain gaps.`);
  }, message => { clearView('trend'); status('trend', message, 'error'); });
}
function loadLog() {
  clearView('access-log'); status('access-log', 'Loading access log…', 'loading');
  byId('view-access-log').setAttribute('aria-busy', 'true');
  const query = new URLSearchParams({ offset: state.offsets['access-log'], limit: byId('access-log-page-size').value });
  return read('access-log', `/access-log?${query}`, page => {
    checkPage(page);
    const rows = page.items.map(event => {
      const row = node('tr'), actor = node('td', `${event.actorId ?? 'Not recorded'} / ${event.actorRole ?? 'Not recorded'}`);
      secondary(actor, event.actorRosterVerified ? 'Checked against test roster; not authenticated' : 'Unverified test declaration');
      const when = node('td', event.at, 'numeric'); secondary(when, `Event ${event.id}`);
      const outcome = node('td');
      outcome.append(node('span', event.outcome, `outcome${['ALLOWED', 'DENIED'].includes(event.outcome) ? ` outcome--${event.outcome.toLowerCase()}` : ''}`));
      row.append(when, actor, node('td', words(event.action)), outcome, node('td', words(event.source)), node('td', event.targetProxyId ?? '—'));
      return row;
    });
    byId('access-log-body').replaceChildren(...rows);
    pageState('access-log', page);
  }, message => { clearView('access-log'); status('access-log', message, 'error'); });
}
function loadActive() {
  if (!state.context) { byId('app-status').textContent = 'Apply a test context to load data.'; return; }
  if (state.view === 'results') return loadResults();
  if (state.view === 'trend') return loadTrend();
  return loadLog();
}
function navigate(view, focus = true) {
  gate.invalidate(); clearAll(); state.view = view;
  for (const name of views) {
    byId(`view-${name}`).hidden = name !== view;
    if (name === view) byId(`nav-${name}`).setAttribute('aria-current', 'page');
    else byId(`nav-${name}`).removeAttribute('aria-current');
  }
  if (focus) { byId(`${view}-heading`).tabIndex = -1; byId(`${view}-heading`).focus(); }
  return loadActive();
}

byId('context-form').addEventListener('submit', event => {
  event.preventDefault(); invalidateContext();
  if (!byId('context-form').reportValidity()) return;
  const patientId = byId('patient-id').value.trim(), actorId = byId('actor-id').value.trim(), role = byId('actor-role').value;
  if (!patientId || !actorId || !['PATIENT', 'PROXY', 'CLINICIAN', 'STAFF'].includes(role)) {
    byId('context-error').textContent = 'Enter a patient ID, a known test actor ID and an exact declared role.';
    byId('context-error').hidden = false; byId('context-error').focus(); return;
  }
  state.context = { patientId, actorId, role };
  byId('current-patient').textContent = patientId; byId('current-actor').textContent = actorId; byId('current-role').textContent = role;
  byId('app-status').textContent = 'Test context applied. The server checks access separately for each request.';
  navigate(state.view, false);
});
for (const name of ['patient-id', 'actor-id', 'actor-role']) {
  byId(name).addEventListener('input', () => invalidateContext());
  byId(name).addEventListener('change', () => invalidateContext());
}
for (const view of views) byId(`nav-${view}`).addEventListener('click', () => navigate(view));
for (const view of ['results', 'access-log']) {
  byId(`${view}-page-size`).addEventListener('change', () => { state.offsets[view] = 0; if (state.view === view) loadActive(); });
  for (const [button, direction] of [['prev', -1], ['next', 1]]) {
    byId(`${view}-${button}`).addEventListener('click', () => {
      state.offsets[view] = Math.max(0, Math.min(1000000, state.offsets[view] + direction * Number(byId(`${view}-page-size`).value)));
      byId(`${view}-heading`).focus();
      loadActive();
    });
  }
}
byId('detail-close').addEventListener('click', () => closeDetail(true));
byId('trend-form').addEventListener('submit', event => { event.preventDefault(); if (state.context) loadTrend(); else loadActive(); });
for (const name of ['trend-code', 'trend-from', 'trend-to']) {
  for (const event of ['input', 'change']) byId(name).addEventListener(event, () => {
    gate.cancel('trend'); clearView('trend'); byId('view-trend').removeAttribute('aria-busy');
    status('trend', 'Filter changed. Select Show trend to load this window.');
  });
}
window.addEventListener('pagehide', () => {
  invalidateContext('Choose a test context to begin.');
  byId('patient-id').value = ''; byId('actor-id').value = '';
});
window.addEventListener('pageshow', event => { if (event.persisted) invalidateContext('Choose a test context to begin.'); });
for (const view of views) byId(`${view}-heading`).tabIndex = -1;
invalidateContext('Choose a test context to begin.');
