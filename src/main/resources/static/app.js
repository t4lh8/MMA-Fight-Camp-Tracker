'use strict';

// User data is only ever written with textContent, never innerHTML, so stored text cannot run as script.
// The token lives in sessionStorage: it is gone when the tab closes and is never sent as a cookie.

const TOKEN_KEY = 'fightcamp.token';
const SVG_NS = 'http://www.w3.org/2000/svg';
const WEIGHT_CLASSES = ['STRAWWEIGHT', 'FLYWEIGHT', 'BANTAMWEIGHT', 'FEATHERWEIGHT', 'LIGHTWEIGHT',
  'WELTERWEIGHT', 'MIDDLEWEIGHT', 'LIGHT_HEAVYWEIGHT', 'HEAVYWEIGHT'];

const $ = (sel) => document.querySelector(sel);

function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === 'class') node.className = v;
    else if (k === 'text') node.textContent = v;
    else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
    else node.setAttribute(k, v);
  }
  for (const c of children) if (c != null) node.append(c);
  return node;
}

function svg(tag, attrs = {}) {
  const node = document.createElementNS(SVG_NS, tag);
  for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v);
  return node;
}

const pretty = (s) => s === 'BJJ' ? 'BJJ' : (s || '').toLowerCase().replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase());
const plural = (n, word) => n + ' ' + word + (n === 1 ? '' : 's');
const fmt = (n) => Number(n).toLocaleString('en-GB');
const today = () => new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);

// API

async function api(path, options = {}) {
  const headers = { 'Accept': 'application/json' };
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (token) headers['Authorization'] = 'Bearer ' + token;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(path, {
    method: options.method || 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });
  if (res.status === 401 && token) {
    logout();
    throw new Error('Your session has expired. Please log in again.');
  }
  const data = res.status === 204 ? null : await res.json().catch(() => null);
  if (!res.ok) {
    const fields = data && data.fields ? Object.entries(data.fields).map(([f, m]) => f + ' ' + m).join(', ') : '';
    throw new Error(fields || (data && data.error) || 'Request failed');
  }
  return data;
}

// Tooltip

const tip = $('#tip');
function showTip(evt, text) {
  tip.textContent = text;
  tip.hidden = false;
  tip.style.left = (evt.clientX + 12) + 'px';
  tip.style.top = (evt.clientY - 28) + 'px';
}
function hideTip() { tip.hidden = true; }

// Status mapping: every status is shown with a dot and a word, never colour alone.

const WORKLOAD_STATUS = {
  NO_BASELINE: ['neutral', 'Not enough history yet'],
  UNDERLOADED: ['warning', 'Below usual load'],
  SWEET_SPOT: ['good', 'In the sweet spot'],
  CAUTION: ['serious', 'Ramping up fast'],
  SPIKE: ['critical', 'Load spike, injury risk'],
};
const CUT_STATUS = {
  ON_WEIGHT: ['good', 'On weight'],
  MANAGEABLE: ['good', 'Manageable cut'],
  HEAVY: ['serious', 'Heavy cut'],
  EXTREME: ['critical', 'Extreme cut'],
};

function statusBadge([cls, label]) {
  return el('div', { class: 'status ' + cls }, el('span', { class: 'dot' }), el('span', { text: label }));
}

function stat(label, big, detail, badge) {
  return el('div', { class: 'card stat' },
    el('div', { class: 'label', text: label }),
    el('div', { class: 'big', text: big }),
    detail ? el('div', { class: 'detail', text: detail }) : null,
    badge || null);
}

// Charts

function weeklyChart(weeks) {
  const W = 640, H = 220, left = 44, right = 8, top = 22, bottom = 28;
  const max = Math.max(1, ...weeks.map((w) => w.load));
  const step = niceStep(max);
  const yMax = Math.ceil(max / step) * step;
  const plotW = W - left - right, plotH = H - top - bottom;
  const slot = plotW / weeks.length;
  const barW = Math.min(36, slot - 2 * 6);
  const y = (v) => top + plotH - (v / yMax) * plotH;

  const root = svg('svg', { class: 'chart', viewBox: `0 0 ${W} ${H}`, role: 'img',
    'aria-label': 'Weekly training load for the last 8 weeks' });
  for (let v = 0; v <= yMax; v += step) {
    root.append(svg('line', { class: 'grid-line', x1: left, x2: W - right, y1: y(v), y2: y(v) }));
    const t = svg('text', { x: left - 6, y: y(v) + 4, 'text-anchor': 'end' });
    t.textContent = fmt(v);
    root.append(t);
  }
  weeks.forEach((w, i) => {
    const cx = left + slot * i + slot / 2;
    const h = (w.load / yMax) * plotH;
    const current = i === weeks.length - 1;
    // The hit area comes right before its bar, so ".hit:hover + .bar" highlights the right week.
    const hit = svg('rect', { class: 'hit', x: cx - slot / 2, y: top, width: slot, height: plotH });
    const text = `Week of ${w.weekStart}: load ${fmt(w.load)}, ${w.sessions} sessions, ${w.minutes} min`;
    hit.addEventListener('mousemove', (e) => showTip(e, text));
    hit.addEventListener('mouseleave', hideTip);
    root.append(hit);
    if (h > 0) {
      // 4px rounded top, square at the baseline.
      const x0 = cx - barW / 2, x1 = cx + barW / 2, yt = y(w.load), yb = y(0), r = Math.min(4, h);
      root.append(svg('path', {
        class: 'bar' + (current ? ' current' : ''),
        d: `M${x0},${yb} V${yt + r} Q${x0},${yt} ${x0 + r},${yt} H${x1 - r} Q${x1},${yt} ${x1},${yt + r} V${yb} Z`,
      }));
    }
    const label = svg('text', { x: cx, y: H - 8, 'text-anchor': 'middle' });
    const d = new Date(w.weekStart + 'T00:00:00');
    label.textContent = current ? 'This week' : d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
    root.append(label);
    if (current && w.load > 0) {
      const v = svg('text', { class: 'value', x: cx, y: y(w.load) - 6, 'text-anchor': 'middle' });
      v.textContent = fmt(w.load);
      root.append(v);
    }
  });
  return root;
}

function niceStep(max) {
  const raw = max / 4;
  const pow = Math.pow(10, Math.floor(Math.log10(raw)));
  const n = raw / pow;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * pow;
}

function disciplineBars(rows) {
  if (!rows.length) return el('p', { class: 'empty', text: 'No sessions in the last 28 days.' });
  const max = Math.max(...rows.map((r) => r.minutes));
  return el('div', { class: 'hbars' }, ...rows.map((r) => {
    const fill = el('span', { class: 'fill' });
    fill.style.width = (r.minutes / max * 100).toFixed(1) + '%';
    const track = el('div', { class: 'track' }, fill);
    track.addEventListener('mousemove', (e) => showTip(e, `${pretty(r.discipline)}: ${r.sessions} sessions, ${r.minutes} min`));
    track.addEventListener('mouseleave', hideTip);
    return el('div', { class: 'hbar' },
      el('span', { class: 'name', text: pretty(r.discipline) }),
      track,
      el('span', { class: 'num', text: r.minutes + ' min' }));
  }));
}

function weeksTable(weeks) {
  return el('details', {},
    el('summary', { text: 'Show as table' }),
    el('table', {},
      el('thead', {}, el('tr', {}, el('th', { text: 'Week of' }), el('th', { text: 'Sessions' }),
        el('th', { text: 'Minutes' }), el('th', { text: 'Load' }))),
      el('tbody', {}, ...weeks.map((w) => el('tr', {},
        el('td', { text: w.weekStart }), el('td', { class: 'num', text: w.sessions }),
        el('td', { class: 'num', text: w.minutes }), el('td', { class: 'num', text: fmt(w.load) }))))));
}

// Dashboard

function renderDashboard(target, d, editable) {
  target.replaceChildren();
  const r = d.record;
  const w = d.workload;
  const thisWeek = d.weeks[d.weeks.length - 1];

  const recordLine = `${r.wins}-${r.losses}-${r.draws}` + (r.noContests ? ` (${r.noContests} NC)` : '');
  const finish = r.finishRate != null
    ? `${r.koWins} KO/TKO, ${r.subWins} sub, ${r.decisionWins} dec. Finish rate ${r.finishRate}%`
    : 'No wins logged yet';

  const ratioCard = stat('Acute:chronic load', w.ratio != null ? Number(w.ratio).toFixed(2) : '-',
    `Last 7 days ${fmt(w.acute)} vs weekly avg ${fmt(w.chronicWeekly)}`, statusBadge(WORKLOAD_STATUS[w.status]));
  if (w.ratio != null) {
    const bar = el('span');
    bar.style.width = Math.min(100, Number(w.ratio) / 2 * 100) + '%';
    ratioCard.append(el('div', { class: 'meter', title: 'Scale 0 to 2.0' }, bar));
  }

  let cutCard;
  if (d.weightCut) {
    const c = d.weightCut;
    cutCard = stat('Weight cut', `${c.kgToCut} kg`,
      `${c.latestKg} kg on ${c.latestDate}, limit ${c.limitKg} kg (${c.percentOfBodyweight}%)`,
      statusBadge(CUT_STATUS[c.status]));
  } else {
    cutCard = stat('Weight cut', '-', d.weightClass ? 'Log a weigh-in to see your cut' : 'No weight class set');
  }

  target.append(
    el('div', { class: 'dash-head' },
      el('h1', { text: editable ? 'Your fight camp' : d.athlete }),
      el('span', { class: 'sub', text: (d.weightClass ? pretty(d.weightClass) + ' · ' : '') + d.today })),
    el('div', { class: 'grid four' },
      stat('Record', recordLine, finish, r.streak ? el('div', { class: 'detail', text: 'Current streak ' + r.streak }) : null),
      ratioCard,
      cutCard,
      stat('This week', plural(thisWeek.sessions, 'session'), `${thisWeek.minutes} min, load ${fmt(thisWeek.load)}`)),
    el('div', { class: 'grid charts' },
      el('div', { class: 'card' }, el('h2', { text: 'Weekly training load (minutes x RPE)' }),
        weeklyChart(d.weeks), weeksTable(d.weeks)),
      el('div', { class: 'card' }, el('h2', { text: 'Minutes by discipline, last 28 days' }),
        disciplineBars(d.disciplines))),
    el('div', { class: 'grid two' },
      el('div', { class: 'card' }, el('h2', { text: 'Recent sessions' }), sessionsTable(d.recentSessions, editable)),
      el('div', { class: 'card' }, el('h2', { text: 'Fights' }), fightsTable(d.fights, editable))));
}

function deleteButton(path) {
  return el('button', { class: 'ghost small', type: 'button', text: 'Delete', onclick: async () => {
    try { await api(path, { method: 'DELETE' }); await loadAthlete(); } catch (e) { alert(e.message); }
  } });
}

function sessionsTable(rows, editable) {
  if (!rows.length) return el('p', { class: 'empty', text: 'No sessions yet.' });
  return el('table', {},
    el('thead', {}, el('tr', {}, el('th', { text: 'Date' }), el('th', { text: 'Type' }), el('th', { text: 'Min' }),
      el('th', { text: 'RPE' }), el('th', { text: 'Notes' }), editable ? el('th') : null)),
    el('tbody', {}, ...rows.map((s) => el('tr', {},
      el('td', { class: 'nowrap', text: s.date }), el('td', { text: pretty(s.discipline) }),
      el('td', { class: 'num', text: s.durationMin }), el('td', { class: 'num', text: s.rpe }),
      el('td', { text: s.notes || '' }),
      editable ? el('td', {}, deleteButton('/api/sessions/' + s.id)) : null))));
}

function fightsTable(rows, editable) {
  if (!rows.length) return el('p', { class: 'empty', text: 'No fights yet.' });
  const method = { KO_TKO: 'KO/TKO', SUBMISSION: 'Sub', DECISION: 'Dec', OTHER: 'Other' };
  return el('table', {},
    el('thead', {}, el('tr', {}, el('th', { text: 'Date' }), el('th', { text: 'Opponent' }), el('th', { text: 'Result' }),
      el('th', { text: 'Method' }), el('th', { text: 'Rd' }), editable ? el('th') : null)),
    el('tbody', {}, ...rows.map((f) => el('tr', { title: f.eventName || '' },
      el('td', { class: 'nowrap', text: f.date }), el('td', { text: f.opponent }),
      el('td', { class: 'result-' + f.result, text: f.result }),
      el('td', { text: method[f.method] }), el('td', { class: 'num', text: f.endRound }),
      editable ? el('td', {}, deleteButton('/api/fights/' + f.id)) : null))));
}

// Views

async function loadAthlete() {
  const [dash, coaches, audit] = await Promise.all([api('/api/dashboard'), api('/api/me/coaches'), api('/api/me/audit')]);
  renderDashboard($('#dash'), dash, true);

  $('#coaches').replaceChildren(...coaches.map((c) => el('li', {}, el('span', { text: c }),
    el('button', { class: 'ghost small', type: 'button', text: 'Remove', onclick: async () => {
      await api('/api/me/coaches/' + encodeURIComponent(c), { method: 'DELETE' });
      await loadAthlete();
    } }))));

  $('#audit').replaceChildren(...audit.slice(0, 8).map((a) => el('tr', {},
    el('td', { text: new Date(a.at).toLocaleString('en-GB', { dateStyle: 'short', timeStyle: 'short' }) }),
    el('td', { text: pretty(a.action) }),
    el('td', { text: a.detail || '' }))));
}

async function loadCoach() {
  const athletes = await api('/api/coach/athletes');
  const list = $('#athlete-list');
  if (!athletes.length) {
    list.replaceChildren(el('p', { class: 'empty', text: 'No athletes have shared with you yet.' }));
    return;
  }
  const open = async (name, btn) => {
    list.querySelectorAll('button').forEach((b) => b.classList.remove('active'));
    btn.classList.add('active');
    renderDashboard($('#coach-dash'), await api('/api/coach/athletes/' + encodeURIComponent(name) + '/dashboard'), false);
  };
  list.replaceChildren(...athletes.map((name) => {
    const b = el('button', { type: 'button', text: name });
    b.addEventListener('click', () => open(name, b));
    return b;
  }));
  open(athletes[0], list.querySelector('button'));
}

async function start() {
  const token = sessionStorage.getItem(TOKEN_KEY);
  $('#auth').hidden = !!token;
  $('#who').hidden = !token;
  $('#athlete').hidden = true;
  $('#coach').hidden = true;
  if (!token) return;
  try {
    const me = await api('/api/me');
    $('#who-name').replaceChildren(el('span', { text: me.username + ' ' }), el('span', { class: 'role-tag', text: me.role }));
    if (me.role === 'COACH') {
      $('#coach').hidden = false;
      await loadCoach();
    } else {
      $('#athlete').hidden = false;
      await loadAthlete();
    }
  } catch (e) {
    $('#auth-msg').textContent = e.message;
  }
}

function logout() {
  sessionStorage.removeItem(TOKEN_KEY);
  start();
}

// Forms

function formData(form) {
  const out = {};
  for (const [k, v] of new FormData(form).entries()) {
    const input = form.elements[k];
    if (v === '') continue;
    out[k] = input.type === 'number' ? Number(v) : v;
  }
  return out;
}

function bindForm(selector, handler) {
  const form = $(selector);
  const msg = form.querySelector('.msg') || $('#auth-msg');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    msg.textContent = '';
    msg.classList.remove('ok');
    try {
      const okText = await handler(formData(form), form);
      if (okText) {
        msg.textContent = okText;
        msg.classList.add('ok');
      }
    } catch (err) {
      msg.textContent = err.message;
    }
  });
}

function init() {
  document.querySelectorAll('.wc-select').forEach((s) => {
    s.append(el('option', { value: '', text: 'None' }), ...WEIGHT_CLASSES.map((w) => el('option', { value: w, text: pretty(w) })));
    s.value = 'LIGHTWEIGHT';
  });
  document.querySelectorAll('input[type=date]').forEach((i) => { i.value = today(); i.max = today(); });

  document.querySelectorAll('.tab').forEach((t) => t.addEventListener('click', () => {
    document.querySelectorAll('.tab').forEach((x) => x.classList.toggle('active', x === t));
    $('#login-form').hidden = t.dataset.tab !== 'login';
    $('#register-form').hidden = t.dataset.tab !== 'register';
    $('#auth-msg').textContent = '';
  }));

  bindForm('#login-form', async (data, form) => {
    const res = await api('/api/auth/login', { method: 'POST', body: data });
    sessionStorage.setItem(TOKEN_KEY, res.token);
    form.reset();
    await start();
  });
  bindForm('#register-form', async (data) => {
    await api('/api/auth/register', { method: 'POST', body: data });
    const res = await api('/api/auth/login', { method: 'POST', body: { username: data.username, password: data.password } });
    sessionStorage.setItem(TOKEN_KEY, res.token);
    await start();
  });
  bindForm('#session-form', async (data) => {
    await api('/api/sessions', { method: 'POST', body: data });
    await loadAthlete();
    return 'Saved.';
  });
  bindForm('#fight-form', async (data) => {
    await api('/api/fights', { method: 'POST', body: data });
    await loadAthlete();
    return 'Saved.';
  });
  bindForm('#weigh-form', async (data) => {
    await api('/api/weigh-ins', { method: 'POST', body: data });
    await loadAthlete();
    return 'Saved.';
  });
  bindForm('#share-form', async (data, form) => {
    await api('/api/me/coaches', { method: 'POST', body: data });
    form.reset();
    await loadAthlete();
    return 'Shared.';
  });
  $('#logout').addEventListener('click', logout);
  start();
}

init();
