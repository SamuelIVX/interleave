/**
 * interleave trace viewer — static schedule + state renderer.
 * Loads TraceRecord / TestResult / benchmarks JSON and an optional program JSON;
 * renders verdict, metadata, timeline, and state strip. No build, no server.
 * SECURITY: all inputs are untrusted JSON — inserted via textContent only, bounded by caps.
 */

const $ = s => document.querySelector(s);
const errorEl = $('#error');
const copyBtn = $('#copyBtn');
const summaryEl = $('#summary');
const timelineSec = $('#timelineSec');
const stateSec = $('#stateSec');
let currentTrace = null;
let currentMeta = null;
let programDef = null;
let selectedIdx = 0;
let traceGen = 0;
let programGen = 0;
let demoGen = 0;

const MAX_PASTE_BYTES = 5 * 1024 * 1024;
const MAX_SCHEDULE = 5000;
const MAX_THREADS = 64;
const MAX_CELLS = 64000;
const MAX_DISPLAY_LEN = 200;
const MAX_FIELDS = 64;
const MAX_NAME_LEN = 64;
const KEY_ALLOW = /^[a-z0-9-]{1,64}$/;
const THREAD_PALETTE = [
  {color:'var(--t0)', bg:'var(--t0-bg)'},
  {color:'var(--t1)', bg:'var(--t1-bg)'},
  {color:'var(--t2)', bg:'var(--t2-bg)'},
  {color:'var(--t3)', bg:'var(--t3-bg)'}
];

/**
 * Show an error message in the banner.
 * @param {string} msg - message to display
 * @returns {void}
 */
function showError(msg){ errorEl.textContent = msg; errorEl.classList.add('show'); }

/**
 * Clear the error banner.
 * @returns {void}
 */
function clearError(){ errorEl.textContent=''; errorEl.classList.remove('show'); }

/**
 * Update the verdict badge for the given verdict.
 * SECURITY: verdict is attacker-influenced but inserted as text only.
 * @param {string} verdict - upper-cased verdict label
 * @returns {void}
 */
function setBadge(verdict){
  const b = $('#verdictBadge');
  b.textContent = verdict || '—';
  b.className = 'badge ' + (verdict==='VIOLATION' ? 'fail' : verdict==='DEADLOCK' ? 'deadlock' : verdict==='PASS' ? 'pass' : 'neutral');
}

/**
 * Coerce a value to a finite number string.
 * @param {*} v - value to coerce
 * @returns {string} numeric string or '—'
 */
function asNum(v){
  const n = Number(v);
  return Number.isFinite(n) ? String(n) : '—';
}

/**
 * Truncate a string for display.
 * @param {*} s - value to stringify and cap
 * @returns {string} capped string
 */
function capStr(s){
  const t = String(s);
  return t.length > MAX_DISPLAY_LEN ? t.slice(0, MAX_DISPLAY_LEN) + '…' : t;
}

/**
 * Truncate a field/local name for display.
 * @param {*} s - value to stringify and cap
 * @returns {string} capped name
 */
function capName(s){
  const t = String(s);
  return t.length > MAX_NAME_LEN ? t.slice(0, MAX_NAME_LEN) + '…' : t;
}

/**
 * Compare two state values for equality (handles primitives and shallow arrays/objects).
 * @param {*} v1 - first value
 * @param {*} v2 - second value
 * @returns {boolean} true if equal
 */
function isValEqual(v1, v2){
  if(v1 === v2) return true;
  if(v1 == null || v2 == null) return false;
  if(Array.isArray(v1) && Array.isArray(v2)){
    if(v1.length !== v2.length) return false;
    return v1.every((x, i) => isValEqual(x, v2[i]));
  }
  if(typeof v1 === 'object' && typeof v2 === 'object'){
    return JSON.stringify(v1) === JSON.stringify(v2);
  }
  return false;
}

/**
 * Format a snapshot value for safe display.
 * @param {*} v - value to format
 * @returns {string} formatted string
 */
function formatVal(v){
  if(v === undefined) return '—';
  if(v === null) return 'null';
  if(typeof v === 'boolean') return String(v);
  if(typeof v === 'number') return String(v);
  if(Array.isArray(v)) return capStr('[' + v.map(formatVal).join(', ') + ']');
  if(typeof v === 'object') return capStr(JSON.stringify(v));
  return capStr(String(v));
}

/**
 * Render a table cell for a snapshot value, adding .val-changed if it changed from previous config.
 * @param {*} val - value at selected config
 * @param {boolean} isChanged - whether value changed from previous config
 * @returns {HTMLTableCellElement} table cell
 */
function renderValCell(val, isChanged){
  const td = document.createElement('td');
  const span = document.createElement('span');
  span.textContent = formatVal(val);
  if(isChanged){
    span.className = 'val-changed';
  }
  td.append(span);
  return td;
}

/**
 * Normalize any supported raw JSON payload into a {trace, meta} pair.
 * Supports TraceRecord {threads}, ReportWriter {benchmarks}, and TestResult
 * {failingTraces|deadlockedTraces|completedTraces|incompleteTraces}.
 * SECURITY: treats all fields as untrusted — never uses innerHTML, validates shapes.
 * @param {*} raw - parsed JSON payload
 * @returns {{trace: Object, meta: Object}} normalized trace and metadata
 * @throws {Error} when the shape is unrecognized or required fields are missing
 */
function normalize(raw){
  if(!raw || typeof raw !== 'object') throw new Error('JSON must be an object');
  const diagnostics = (value, threads) => {
    const validSpan = (span) => span && typeof span.file === 'string' &&
      ['line','column','endLine','endColumn'].every(k=>Number.isSafeInteger(span[k]) && span[k]>0) &&
      (span.endLine>span.line || (span.endLine===span.line && span.endColumn>=span.column));
    const validPath = path => Array.isArray(path) && path.every(p=>p && Number.isSafeInteger(p.sourceOffset) && p.sourceOffset>=0 && Number.isSafeInteger(p.iteration) && p.iteration>=0);
    if(value.sourceDigest != null && (typeof value.sourceDigest !== 'string' || !/^[0-9a-f]{64}$/.test(value.sourceDigest))) throw new Error('Invalid sourceDigest');
    if(value.executedSteps != null){
      if(!Array.isArray(value.executedSteps) || value.executedSteps.length!==threads.length || value.executedSteps.length>5000) throw new Error('Misaligned executedSteps');
      for(const step of value.executedSteps){
        if(!step || !Number.isSafeInteger(step.instructionIndex) || step.instructionIndex<0 || typeof step.label!=='string' || !validSpan(step.source)) throw new Error('Invalid executedSteps source identity');
        if(!validPath(step.expansionPath)) throw new Error('Invalid source expansion path');
      }
    }
    if(value.cutoff != null){
      const c=value.cutoff;
      if(!['LOOP_LIMIT','PREEMPTION_BOUND'].includes(c.reason) || !['threadId','instructionIndex','limit'].every(k=>Number.isSafeInteger(c[k]) && c[k]>=0) || (c.reason==='LOOP_LIMIT' && c.iterations!==c.limit) || (c.source!=null && !validSpan(c.source)) || (c.expansionPath!=null && !validPath(c.expansionPath))) throw new Error('Invalid cutoff evidence');
    }
    if(value.failure != null){
      const f=value.failure;
      if(!['INVARIANT','RUNTIME_ERROR'].includes(f.kind) || !['STEP','PROPERTY'].includes(f.phase) ||
        (f.source!=null && !validSpan(f.source)) || (f.kind==='INVARIANT' ? f.phase!=='PROPERTY' || f.category!=null :
          !['ARRAY_BOUNDS','MODULO_BY_ZERO','OTHER_EVALUATION_ERROR'].includes(f.category))) throw new Error('Invalid failure evidence');
    }
    if(value.failure!=null && value.cutoff!=null) throw new Error('Conflicting terminal evidence');
    return {sourceDigest:value.sourceDigest,executedSteps:value.executedSteps,cutoff:value.cutoff,failure:value.failure};
  };
  const runDiagnostics = value => ({programKind:value.programKind,storeType:value.storeType,resourceLimitReason:value.resourceLimitReason,incompletenessReasons:value.incompletenessReasons});

  if(Array.isArray(raw.threads)){
    return { trace: { ...diagnostics(raw, raw.threads), threads: raw.threads, outcomes: raw.outcomes, outcome: raw.outcome, programHash: raw.programHash, states: raw.states }, meta: { verdict: raw.outcome, bug: raw.programHash } };
  }
  if(Array.isArray(raw.benchmarks)){
    const entry = raw.benchmarks.find(b=> b.failingTrace) || raw.benchmarks.find(b=> b.incompleteTrace) || raw.benchmarks[0];
    if(!entry) throw new Error('benchmarks[] is empty');
    const ft = entry.failingTrace || entry.incompleteTrace;
    if(ft){
      const threads = ft.threads || ft.threadIds;
      if(!Array.isArray(threads)) throw new Error('benchmarks[].failingTrace missing threads/threadIds');
      return { trace: { ...diagnostics(ft, threads), threads, outcomes: ft.outcomes, outcome: entry.verdict || 'VIOLATION', programHash: entry.bug, states: ft.states }, meta: { ...runDiagnostics(entry), verdict: entry.verdict, strategy: entry.strategy, storeType: entry.storeType, statesExplored: entry.statesExplored, wallTimeMs: entry.wallTimeMs, heapDeltaBytes: entry.heapDeltaBytes, bug: entry.bug } };
    }
    return { trace: { threads: [], outcome: entry.verdict || 'PASS', programHash: entry.bug }, meta: { ...runDiagnostics(entry), verdict: entry.verdict, strategy: entry.strategy, storeType: entry.storeType, statesExplored: entry.statesExplored, wallTimeMs: entry.wallTimeMs, heapDeltaBytes: entry.heapDeltaBytes, bug: entry.bug } };
  }
  if(Array.isArray(raw.failingTraces) || Array.isArray(raw.deadlockedTraces) || Array.isArray(raw.completedTraces) || Array.isArray(raw.incompleteTraces)){
    const pick = (arr) => arr && arr.length ? arr[0] : null;
    // Precedence mirrors cbVerdict: a conclusive failure wins, then DEADLOCK, then INCOMPLETE,
    // then a completed schedule. A run that found a violation must show the violation, not the
    // partial schedule that came with it. INCOMPLETE outranks a completed schedule because a
    // bounded run produces both, and rendering the completed one drops the run's boundedness:
    // the viewer would present a partial exploration as an exhaustive PASS. A completed schedule
    // is the fallback and is only reached when the search genuinely was exhaustive.
    let tr = pick(raw.failingTraces) || pick(raw.deadlockedTraces) || pick(raw.incompleteTraces) || pick(raw.completedTraces);
    if(!tr) {
      const incomplete = raw.limitExceeded === true && !raw.hasViolation;
      const verdict = raw.hasViolation ? 'VIOLATION' : incomplete ? (raw.programKind==='SOURCE' ? 'INCOMPLETE' : 'LIMIT EXCEEDED') : (raw.programKind==='SOURCE' ? (raw.storeType==='BITSTATE' ? 'APPROXIMATE_PASS' : raw.storeType==='EXACT' ? 'PASS' : 'UNKNOWN') : 'PASS');
      return { trace: { threads: [], outcome: verdict }, meta: { ...runDiagnostics(raw), verdict, strategy: raw.strategy, statesExplored: raw.statesExplored, wallTimeMs: raw.wallTimeMs, heapDeltaBytes: raw.heapDeltaBytes, limitExceeded: raw.limitExceeded === true } };
    }
    const threads = tr.threads || tr.threadIds;
    if(!Array.isArray(threads)) throw new Error('failingTraces[0] missing threads/threadIds');
    const incomplete = raw.limitExceeded === true && !raw.hasViolation && !['VIOLATION','DEADLOCK'].includes(tr.outcome);
    // LIMIT EXCEEDED (a resource limit stopped the search) and INCOMPLETE (a preemption bound
    // did) are different states and are never merged: the latter arrives here as tr.outcome.
    // Rendering a bounded run that proved nothing as PASS is the most misleading thing this
    // viewer can do, so it must not be reachable.
    let outcome = incomplete ? (raw.programKind==='SOURCE' ? 'INCOMPLETE' : 'LIMIT EXCEEDED') : (tr.outcome || (raw.hasViolation ? 'VIOLATION' : 'PASS'));
    if(raw.programKind==='SOURCE' && ['PASS','COMPLETED'].includes(outcome) && ['EXACT','BITSTATE'].includes(raw.storeType)) outcome = raw.storeType==='BITSTATE' ? 'APPROXIMATE_PASS' : 'PASS';
    return { trace: { ...diagnostics(tr, threads), threads, outcomes: tr.outcomes, outcome, programHash: tr.programHash, states: tr.states }, meta: { ...runDiagnostics(raw), verdict: outcome, strategy: raw.strategy, statesExplored: raw.statesExplored, wallTimeMs: raw.wallTimeMs, heapDeltaBytes: raw.heapDeltaBytes, hasViolation: raw.hasViolation, limitExceeded: raw.limitExceeded === true } };
  }
  throw new Error('Unrecognized shape — expected TraceRecord {threads}, TestResult {failingTraces|incompleteTraces}, or ReportWriter {benchmarks}.');
}

/**
 * Validate a normalized trace. Throws on malformed or oversized input.
 * SECURITY: enforces size and cardinality caps before any DOM work.
 * @param {Object} t - trace with {threads, outcomes}
 * @throws {Error} on validation failure
 * @returns {void}
 */
function validateTrace(t){
  if(!t || !Array.isArray(t.threads)) throw new Error('trace.threads must be an array');
  if(t.threads.length > MAX_SCHEDULE) throw new Error(`threads length ${t.threads.length} exceeds limit ${MAX_SCHEDULE} — truncating not supported, provide a shorter trace`);
  if(t.threads.some(v=> typeof v !== 'number' || !Number.isInteger(v) || v < 0)) throw new Error('threads must be non-negative integers');
  const distinct = new Set(t.threads);
  if(distinct.size > MAX_THREADS) throw new Error(`distinct thread IDs ${distinct.size} exceeds limit ${MAX_THREADS}`);
  const cells = (t.threads.length + 1) * (distinct.size + 2);
  if(cells > MAX_CELLS) throw new Error(`trace too large to render (${cells} cells > ${MAX_CELLS}) — provide a shorter schedule or fewer threads`);
  if(t.outcomes && !Array.isArray(t.outcomes)) throw new Error('outcomes must be an array if present');
  if(t.outcomes && t.outcomes.length > t.threads.length) throw new Error('outcomes longer than threads');
}

/**
 * Validate a program JSON. Throws on malformed or oversized input.
 * SECURITY: enforces shape and cardinality before assignment — prevents poisoned programDef.
 * @param {*} p - parsed program JSON
 * @throws {Error} on validation failure
 * @returns {void}
 */
function validateProgram(p){
  if(p == null || typeof p !== 'object' || Array.isArray(p)) throw new Error('program JSON must be an object');
  if(p.threads !== undefined){
    if(!Array.isArray(p.threads)) throw new Error('threads must be an array');
    if(p.threads.length > MAX_SCHEDULE) throw new Error(`threads length ${p.threads.length} exceeds limit ${MAX_SCHEDULE}`);
    p.threads.forEach((t,i)=>{
      if(!t || typeof t !== 'object' || Array.isArray(t)) throw new Error(`threads[${i}] must be an object`);
      if(typeof t.id !== 'number' || !Number.isInteger(t.id) || t.id < 0) throw new Error(`threads[${i}].id must be a non-negative integer`);
      if(t.steps !== undefined && !Array.isArray(t.steps)) throw new Error(`threads[${i}].steps must be an array`);
    });
  }
  if(p.state != null){
    if(typeof p.state !== 'object' || Array.isArray(p.state)) throw new Error('state must be an object');
    if(p.state.fields !== undefined){
      if(!Array.isArray(p.state.fields)) throw new Error('state.fields must be an array');
      if(p.state.fields.length > MAX_FIELDS) throw new Error(`state.fields length ${p.state.fields.length} exceeds limit ${MAX_FIELDS}`);
      p.state.fields.forEach((f,i)=>{
        if(!f || typeof f !== 'object' || Array.isArray(f)) throw new Error(`state.fields[${i}] must be an object`);
        if(typeof f.name !== 'string' || f.name.length===0) throw new Error(`state.fields[${i}].name must be a non-empty string`);
        if(f.name.length > MAX_NAME_LEN) throw new Error(`state.fields[${i}].name too long`);
        if(f.type !== undefined && typeof f.type !== 'string') throw new Error(`state.fields[${i}].type must be a string`);
        if(f.type && f.type.length > MAX_NAME_LEN) throw new Error(`state.fields[${i}].type too long`);
      });
    }
    if(p.state.locals !== undefined){
      if(!Array.isArray(p.state.locals)) throw new Error('state.locals must be an array');
      if(p.state.locals.length > MAX_FIELDS) throw new Error(`state.locals length ${p.state.locals.length} exceeds limit ${MAX_FIELDS}`);
      p.state.locals.forEach((l,i)=>{
        if(!l || typeof l !== 'object' || Array.isArray(l)) throw new Error(`state.locals[${i}] must be an object`);
        if(typeof l.name !== 'string' || l.name.length===0) throw new Error(`state.locals[${i}].name must be a non-empty string`);
        if(l.name.length > MAX_NAME_LEN) throw new Error(`state.locals[${i}].name too long`);
        if(l.type !== undefined && typeof l.type !== 'string') throw new Error(`state.locals[${i}].type must be a string`);
        if(l.type && l.type.length > MAX_NAME_LEN) throw new Error(`state.locals[${i}].type too long`);
      });
    }
  }
  // invariant is optional — accept any object/string shape, the viewer renders it as text only
  if(p.invariant !== undefined && p.invariant !== null && typeof p.invariant !== 'object' && typeof p.invariant !== 'string') throw new Error('invariant must be an object or string');
}

/**
 * Render the summary header (verdict badge, program name, harness meta).
 * @returns {void}
 */
function renderHeader(){
  summaryEl.className = 'card summary show';
  const rawVerdict = currentMeta.verdict || currentTrace.outcome || 'PASS';
  const verdict = String(rawVerdict).toUpperCase();
  setBadge(verdict);
  const verdictClass = verdict==='VIOLATION' ? 'verdict-violation' : verdict==='DEADLOCK' ? 'verdict-deadlock' : verdict==='PASS' ? 'verdict-pass' : null;
  if(verdictClass) summaryEl.classList.add(verdictClass);
  const nameEl = $('#programName');
  nameEl.textContent = currentMeta.bug || currentTrace.programHash || programDef?.name || '—';
  $('#programFormat').textContent = programDef ? `format: ${programDef.format || '—'}` : '';
  const metaEl = $('#meta');
  metaEl.textContent = '';
  const addKV = (label, value) => {
    const span = document.createElement('span');
    span.append(document.createTextNode(label + ' '));
    const strong = document.createElement('strong');
    strong.textContent = String(value);
    span.append(strong);
    return span;
  };
  const parts = [];
  if(currentMeta.strategy) parts.push(addKV('strategy', capStr(currentMeta.strategy)));
  if(currentMeta.storeType) parts.push(addKV('store', capStr(currentMeta.storeType)));
  if(currentMeta.statesExplored != null) parts.push(addKV('states', asNum(currentMeta.statesExplored)));
  if(currentMeta.wallTimeMs != null) parts.push(addKV('time', asNum(currentMeta.wallTimeMs) + ' ms'));
  if(currentMeta.heapDeltaBytes != null) parts.push(addKV('heap', asNum(currentMeta.heapDeltaBytes)));
  if(currentMeta.limitExceeded) parts.push(addKV('limit', 'exceeded'));
  if(currentMeta.resourceLimitReason) parts.push(addKV('resource', capStr(currentMeta.resourceLimitReason)));
  if(Array.isArray(currentMeta.incompletenessReasons)) parts.push(addKV('bounds', capStr(currentMeta.incompletenessReasons.join(', '))));
  if(currentTrace.sourceDigest) parts.push(addKV('source digest', currentTrace.sourceDigest));
  const terminal = currentTrace.cutoff || currentTrace.failure;
  if(terminal){
    if(terminal.reason) parts.push(addKV('cutoff', `${capStr(terminal.reason)} (${terminal.limit})`));
    if(terminal.kind) parts.push(addKV('failure', capStr(terminal.kind)));
    if(terminal.phase) parts.push(addKV('phase', capStr(terminal.phase)));
    if(terminal.source) parts.push(addKV('source', `${capName(terminal.source.file)}:${terminal.source.line}:${terminal.source.column}–${terminal.source.endLine}:${terminal.source.endColumn}`));
  }

  if(programDef?.expected_verdict) parts.push(addKV('expected', capStr(programDef.expected_verdict)));
  if(parts.length===0){
    const placeholder = document.createElement('span');
    placeholder.className = 'muted';
    placeholder.textContent = 'Schedule-only trace — no harness meta';
    metaEl.append(placeholder);
  } else {
    parts.forEach((p,i)=>{ metaEl.append(p); if(i < parts.length-1) metaEl.append(document.createTextNode(' · ')); });
  }
  $('#stepCount').textContent = String(currentTrace.threads.length);
  copyBtn.disabled = currentTrace.threads.length === 0;
}

/**
 * Look up step info for a thread at a per-thread step index.
 * Uses a prebuilt thread-ID map for O(1) lookup.
 * @param {Map<number, Object>|null} programThreadsById - map from thread id to program thread
 * @param {number} threadId - thread id from the schedule
 * @param {number} stepIdxForThread - per-thread step index
 * @returns {{label: string, detail: string}|null}
 */
function stepInfoFor(programThreadsById, threadId, stepIdxForThread){
  if(!programThreadsById) return null;
  const progThread = programThreadsById.get(threadId);
  if(!progThread || !Array.isArray(progThread.steps)) return null;
  const step = progThread.steps[stepIdxForThread];
  if(!step) return null;
  if(step.type) return { label: step.type, detail: JSON.stringify(step) };
  const guard = step.guard ? `when ${step.guard}` : '';
  const effects = Array.isArray(step.effects) ? step.effects.join('; ') : '';
  return { label: effects || guard || `step ${stepIdxForThread}`, detail: (guard?guard+' — ':'') + effects, guard, effects };
}

/**
 * Render the timeline table for the current trace and program.
 * Selection is toggled via class changes (O(1)) rather than full re-render.
 * @returns {void}
 */
function renderTimeline(){
  timelineSec.style.display = 'block';
  const n = currentTrace.threads.length;
  const distinct = [...new Set(currentTrace.threads)].sort((a,b)=>a-b);
  const threadIds = distinct;
  const programThreadsById = programDef && Array.isArray(programDef.threads)
    ? new Map(programDef.threads.map(t=>[t.id, t]))
    : null;
  const counters = {};
  threadIds.forEach(id=> counters[id]=0);
  const colorForTid = (tid)=>{
    const idx = threadIds.indexOf(tid);
    return THREAD_PALETTE[idx % THREAD_PALETTE.length];
  };

  const container = $('#timeline');
  container.textContent = '';
  const table = document.createElement('table');
  const thead = document.createElement('thead');
  const headRow = document.createElement('tr');
  const thIdx = document.createElement('th'); thIdx.textContent = '#'; headRow.append(thIdx);
  threadIds.forEach(tid=>{
    const th = document.createElement('th');
    th.textContent = `Thread ${tid}`;
    th.className = `tcol-${threadIds.indexOf(tid) % 4}`;
    headRow.append(th);
  });
  const thOut = document.createElement('th'); thOut.textContent = 'Outcome'; headRow.append(thOut);
  thead.append(headRow); table.append(thead);
  const tbody = document.createElement('tbody');
  for(let idx=0; idx<=n; idx++){
    const isFail = idx===n && (currentTrace.outcome==='VIOLATION' || currentMeta.verdict==='VIOLATION');
    const isActive = idx===selectedIdx;
    const tr = document.createElement('tr');
    tr.dataset.idx = String(idx);
    tr.tabIndex = 0; tr.setAttribute('role','button'); tr.setAttribute('aria-label', `Config ${idx}`);
    tr.setAttribute('aria-pressed', isActive ? 'true' : 'false');
    if(isFail && isActive) tr.className = 'fail-row active-row';
    else if(isFail) tr.className = 'fail-row';
    else if(isActive) tr.className = 'active-row';
    const activeTid = idx < n ? currentTrace.threads[idx] : null;
    const outcome = idx < n ? (currentTrace.outcomes?.[idx] || '') : (currentTrace.outcome || currentMeta.verdict || '');

    const tdIdx = document.createElement('td');
    const spanIdx = document.createElement('span'); spanIdx.className='muted'; spanIdx.textContent=String(idx);
    tdIdx.append(spanIdx); tr.append(tdIdx);

    threadIds.forEach(tid=>{
      const td = document.createElement('td');
      if(activeTid===tid){
        const sourceStep = currentTrace.executedSteps?.[idx];
        const stepIdx = sourceStep ? sourceStep.instructionIndex : counters[tid];
        const info = sourceStep ? {label:sourceStep.label,detail:`${capName(sourceStep.source.file)}:${sourceStep.source.line}:${sourceStep.source.column}–${sourceStep.source.endLine}:${sourceStep.source.endColumn} — ${sourceStep.label}`} : stepInfoFor(programThreadsById, tid, stepIdx);
        const rawLabel = info ? info.label : `step ${stepIdx}`;
        const pill = document.createElement('span');
        pill.className = 'cell-active';
        const pal = colorForTid(tid);
        pill.style.color = pal.color;
        pill.style.background = pal.bg;
        pill.style.borderColor = pal.color;
        pill.textContent = capStr(rawLabel);
        if(info && info.detail) pill.title = capStr(info.detail);
        td.append(pill);
      } else {
        const dot = document.createElement('span'); dot.className='muted'; dot.textContent='·';
        td.append(dot);
      }
      tr.append(td);
    });
    if(activeTid!=null) counters[activeTid]++;

    const tdOut = document.createElement('td');
    tdOut.className='muted';
    tdOut.textContent = capStr(outcome);
    tr.append(tdOut);
    tbody.append(tr);
  }
  table.append(tbody);
  container.append(table);
  container.querySelectorAll('tr[data-idx]').forEach(tr=>{
    const activate = () => {
      const prev = container.querySelector('tr.active-row');
      if(prev){ prev.classList.remove('active-row'); prev.setAttribute('aria-pressed','false'); }
      selectedIdx = parseInt(tr.dataset.idx,10);
      tr.classList.add('active-row');
      tr.setAttribute('aria-pressed','true');
      renderStateStrip();
    };
    tr.addEventListener('click', activate);
    tr.addEventListener('keydown', e=>{ if(e.key==='Enter' || e.key===' '){ e.preventDefault(); activate(); }});
  });
}

/**
 * Render the state strip for the selected config.
 * Snapshot display is independent of programDef — renders even without a program.
 * @returns {void}
 */
function renderStateStrip(){
  stateSec.style.display = 'block';
  const labelEl = $('#selectedIdxLabel');
  labelEl.textContent = `— config ${selectedIdx}`;
  const el = $('#stateStrip');
  el.textContent = '';

  const snapshot = currentTrace.states && Array.isArray(currentTrace.states) ? currentTrace.states[selectedIdx] : null;
  const prevSnapshot = currentTrace.states && Array.isArray(currentTrace.states) && selectedIdx > 0 ? currentTrace.states[selectedIdx - 1] : null;

  if(!programDef && !snapshot){
    const p = document.createElement('p'); p.className='placeholder';
    p.append(document.createTextNode('No program JSON loaded — load a program file (e.g. '));
    const code = document.createElement('code'); code.textContent='src/main/resources/programs/lost-update.json';
    p.append(code);
    p.append(document.createTextNode(') to see '));
    const c2 = document.createElement('code'); c2.textContent='fields';
    p.append(c2);
    p.append(document.createTextNode('/'));
    const c3 = document.createElement('code'); c3.textContent='locals';
    p.append(c3);
    p.append(document.createTextNode(' header. State values are schedule-only today.'));
    el.append(p);
    return;
  }

  if(programDef){
    const div = document.createElement('div'); div.style.marginBottom='8px';
    div.append(document.createTextNode('Invariant: '));
    if(programDef.invariant){
      const code = document.createElement('code');
      code.textContent = capStr(programDef.invariant.expr || JSON.stringify(programDef.invariant));
      div.append(code);
    } else {
      const sp = document.createElement('span'); sp.className='muted'; sp.textContent='—';
      div.append(sp);
    }
    const wh = document.createElement('span'); wh.className='muted'; wh.textContent = ` (${programDef.invariant?.when||'final'})`;
    div.append(wh);
    el.append(div);
  }

  const table = document.createElement('table');
  const thead = document.createElement('thead');
  const hr = document.createElement('tr');
  ['Scope','Name','Value at selected config'].forEach(t=>{ const th=document.createElement('th'); th.textContent=t; hr.append(th); });
  thead.append(hr); table.append(thead);
  const tbody = document.createElement('tbody');

  if(snapshot){
    let rowCount = 0;
    const fieldMap = new Map();
    if(snapshot.fields && typeof snapshot.fields === 'object' && !Array.isArray(snapshot.fields)){
      for(const [k, v] of Object.entries(snapshot.fields)){
        fieldMap.set(k, v);
      }
    }
    const declaredFieldTypes = new Map();
    if(programDef && Array.isArray(programDef.state?.fields)){
      programDef.state.fields.forEach(f=>{ if(f && f.name) declaredFieldTypes.set(f.name, f.type); });
    }

    fieldMap.forEach((val, name)=>{
      if(rowCount >= MAX_FIELDS) return;
      rowCount++;
      const tr = document.createElement('tr');
      const tdS = document.createElement('td'); tdS.textContent = 'field';
      const tdN = document.createElement('td');
      const code = document.createElement('code'); code.textContent = capName(String(name));
      tdN.append(code);
      const typeStr = declaredFieldTypes.get(name);
      if(typeStr){
        const mut = document.createElement('span'); mut.className = 'muted'; mut.textContent = ' ' + capName(String(typeStr));
        tdN.append(mut);
      }
      const prevVal = prevSnapshot?.fields ? prevSnapshot.fields[name] : undefined;
      const isChanged = prevSnapshot != null && !isValEqual(prevVal, val);
      const tdV = renderValCell(val, isChanged);
      tr.append(tdS, tdN, tdV);
      tbody.append(tr);
    });

    const declaredLocalTypes = new Map();
    if(programDef && Array.isArray(programDef.state?.locals)){
      programDef.state.locals.forEach(l=>{ if(l && l.name) declaredLocalTypes.set(l.name, l.type); });
    }

    if(snapshot.locals && typeof snapshot.locals === 'object' && !Array.isArray(snapshot.locals)){
      const tids = Object.keys(snapshot.locals).sort((a,b)=> {
        const na = Number(a), nb = Number(b);
        return (!isNaN(na) && !isNaN(nb)) ? na - nb : a.localeCompare(b);
      });
      for(const tid of tids){
        const tLocals = snapshot.locals[tid];
        if(!tLocals || typeof tLocals !== 'object' || Array.isArray(tLocals)) continue;
        for(const [name, val] of Object.entries(tLocals)){
          if(rowCount >= MAX_FIELDS * 2) break;
          rowCount++;
          const tr = document.createElement('tr');
          const tdS = document.createElement('td'); tdS.textContent = `local (T${tid})`;
          const tdN = document.createElement('td');
          const code = document.createElement('code'); code.textContent = capName(String(name));
          tdN.append(code);
          const typeStr = declaredLocalTypes.get(name);
          if(typeStr){
            const mut = document.createElement('span'); mut.className = 'muted'; mut.textContent = ' ' + capName(String(typeStr));
            tdN.append(mut);
          }
          const prevVal = prevSnapshot?.locals?.[tid] ? prevSnapshot.locals[tid][name] : undefined;
          const isChanged = prevSnapshot != null && !isValEqual(prevVal, val);
          const tdV = renderValCell(val, isChanged);
          tr.append(tdS, tdN, tdV);
          tbody.append(tr);
        }
      }
    }

    if(rowCount === 0){
      const tr = document.createElement('tr');
      const td = document.createElement('td'); td.colSpan = 3; td.className = 'placeholder';
      td.textContent = 'Empty state snapshot.';
      tr.append(td);
      tbody.append(tr);
    }
  } else {
    // Schedule only — no snapshot
    const fieldsRaw = programDef.state?.fields || (programDef.state?.type ? [{name: programDef.state.type, type: 'typed'}] : []);
    const localsRaw = programDef.state?.locals || [];
    const fields = Array.isArray(fieldsRaw) ? fieldsRaw.slice(0, MAX_FIELDS) : [];
    const locals = Array.isArray(localsRaw) ? localsRaw.slice(0, MAX_FIELDS) : [];
    const truncatedFields = Array.isArray(fieldsRaw) && fieldsRaw.length > MAX_FIELDS;
    const truncatedLocals = Array.isArray(localsRaw) && localsRaw.length > MAX_FIELDS;

    if(fields.length === 0 && locals.length === 0){
      const tr = document.createElement('tr');
      const td = document.createElement('td'); td.colSpan = 3; td.className = 'placeholder';
      td.textContent = 'No fields/locals declared in program JSON.';
      tr.append(td);
      tbody.append(tr);
    } else {
      fields.forEach(f=>{
        const tr = document.createElement('tr');
        const tdS = document.createElement('td'); tdS.textContent = 'field';
        const tdN = document.createElement('td');
        const code = document.createElement('code'); code.textContent = capName(String(f.name));
        tdN.append(code);
        const mut = document.createElement('span'); mut.className = 'muted'; mut.textContent = ' ' + capName(String(f.type || ''));
        tdN.append(mut);
        const tdV = document.createElement('td'); tdV.className = 'placeholder'; tdV.textContent = 'no snapshot — schedule only (header from program JSON)';
        tr.append(tdS, tdN, tdV);
        tbody.append(tr);
      });
      if(truncatedFields){
        const tr = document.createElement('tr');
        const td = document.createElement('td'); td.colSpan = 3; td.className = 'placeholder';
        td.textContent = `… truncated, ${fieldsRaw.length - MAX_FIELDS} more fields not shown`;
        tr.append(td);
        tbody.append(tr);
      }
      locals.forEach(l=>{
        const tr = document.createElement('tr');
        const tdS = document.createElement('td'); tdS.textContent = 'local';
        const tdN = document.createElement('td');
        const code = document.createElement('code'); code.textContent = capName(String(l.name));
        tdN.append(code);
        const mut = document.createElement('span'); mut.className = 'muted'; mut.textContent = ' ' + capName(String(l.type || ''));
        tdN.append(mut);
        const tdV = document.createElement('td'); tdV.className = 'placeholder'; tdV.textContent = 'no snapshot — schedule only';
        tr.append(tdS, tdN, tdV);
        tbody.append(tr);
      });
      if(truncatedLocals){
        const tr = document.createElement('tr');
        const td = document.createElement('td'); td.colSpan = 3; td.className = 'placeholder';
        td.textContent = `… truncated, ${localsRaw.length - MAX_FIELDS} more locals not shown`;
        tr.append(td);
        tbody.append(tr);
      }
    }
  }

  table.append(tbody);
  el.append(table);
}

/**
 * Handle a raw JSON payload: normalize, validate, and render.
 * @param {*} raw - parsed JSON payload
 * @returns {void}
 */
function handleRaw(raw){
  clearError();
  try{
    const {trace, meta} = normalize(raw);
    validateTrace(trace);
    currentTrace = trace;
    currentMeta = meta;
    selectedIdx = trace.threads.length;
    renderHeader();
    renderTimeline();
    renderStateStrip();
  }catch(e){
    showError(e.message);
  }
}

/**
 * Handle a trace file selection (async, generation-guarded).
 * Invalidates any pending demo load — a manual trace takes precedence.
 * @param {File} file - selected file
 * @returns {Promise<void>}
 */
async function handleTraceFile(file){
  if(!file) return;
  if(file.size > MAX_PASTE_BYTES){ showError(`Trace file too large (${file.size} bytes > ${MAX_PASTE_BYTES})`); return; }
  demoGen++;
  const myGen = ++traceGen;
  try{ const text = await file.text(); if(myGen !== traceGen) return; const raw = JSON.parse(text); if(myGen !== traceGen) return; handleRaw(raw); }
  catch(e){ if(myGen !== traceGen) return; showError('Trace file: ' + e.message); }
}

/**
 * Handle a program file selection (async, generation-guarded).
 * Validates before assignment — never installs a malformed programDef.
 * @param {File|null} file - selected file, or null when cleared
 * @returns {Promise<void>}
 */
async function handleProgramFile(file){
  if(!file) { programDef=null; if(currentTrace) renderStateStrip(); return; }
  if(file.size > MAX_PASTE_BYTES){ showError(`Program file too large (${file.size} bytes)`); return; }
  const myGen = ++programGen;
  try{
    const text = await file.text(); if(myGen !== programGen) return;
    const candidate = JSON.parse(text); if(myGen !== programGen) return;
    validateProgram(candidate);
    if(myGen !== programGen) return;
    programDef = candidate;
    if(currentTrace) { renderHeader(); renderTimeline(); renderStateStrip(); } clearError();
  }
  catch(e){ if(myGen !== programGen) return; showError('Program file: ' + e.message); }
}

$('#traceFile').addEventListener('change', e=> handleTraceFile(e.target.files[0]));
$('#programFile').addEventListener('change', e=> handleProgramFile(e.target.files[0]));
$('#loadPaste').addEventListener('click', ()=>{
  const t = $('#paste').value.trim();
  if(!t) { showError('Paste is empty'); return; }
  if(new TextEncoder().encode(t).length > MAX_PASTE_BYTES){ showError(`Paste too large (> ${MAX_PASTE_BYTES} bytes)`); return; }
  traceGen++; demoGen++;
  try{ const raw = JSON.parse(t); handleRaw(raw); }catch(e){ showError('Paste: ' + e.message); }
});
$('#clearBtn').addEventListener('click', ()=>{
  traceGen++; programGen++; demoGen++;
  $('#paste').value=''; $('#traceFile').value=''; $('#programFile').value=''; programDef=null; currentTrace=null; currentMeta=null; clearError();
  summaryEl.className='card summary'; summaryEl.classList.remove('show'); timelineSec.style.display='none'; stateSec.style.display='none'; copyBtn.disabled=true;
});
copyBtn.addEventListener('click', async ()=>{
  if(!currentTrace) return;
  const text = currentTrace.threads.join(',');
  try{ await navigator.clipboard.writeText(text); copyBtn.textContent='Copied!'; setTimeout(()=> copyBtn.textContent='Copy schedule',1200); }catch{ showError('Clipboard unavailable — schedule: ' + text); }
});

const samples = {
  'lost-update': {
    trace: {
      threads: [0, 1, 0, 1],
      outcomes: ["ADVANCED", "ADVANCED", "ADVANCED", "ADVANCED"],
      outcome: "VIOLATION",
      states: [
        {"fields": {"counter": 0, "control": false}, "locals": {"0": {"register": 0}, "1": {"register": 0}}},
        {"fields": {"counter": 0, "control": false}, "locals": {"0": {"register": 0}, "1": {"register": 0}}},
        {"fields": {"counter": 0, "control": false}, "locals": {"0": {"register": 0}, "1": {"register": 0}}},
        {"fields": {"counter": 1, "control": true}, "locals": {"0": {"register": 0}, "1": {"register": 0}}},
        {"fields": {"counter": 1, "control": true}, "locals": {"0": {"register": 0}, "1": {"register": 0}}}
      ]
    },
    meta: {verdict: "VIOLATION", bug: "lost-update", strategy: "DFS", storeType: "EXACT", statesExplored: 13}
  },
  'peterson': { trace: { threads:[], outcome:"PASS" }, meta:{verdict:"PASS", bug:"peterson", strategy:"DFS", storeType:"EXACT", statesExplored:6} },
  'bounded-buffer': { trace: { threads:[], outcome:"PASS" }, meta:{verdict:"PASS", bug:"bounded-buffer-declarative", strategy:"DFS", storeType:"EXACT", statesExplored:6} }
};
document.querySelectorAll('[data-sample]').forEach(a=>{
  a.addEventListener('click', async (e)=>{
    e.preventDefault();
    const rawKey = a.dataset.sample;
    if(!KEY_ALLOW.test(rawKey)){ showError('Invalid demo key'); return; }
    const key = rawKey;
    traceGen++;
    const myGen = ++demoGen;
    programDef = null;
    const progCandidates = [
      `../examples/programs/${key}.json`,
      `../examples/programs/${key}-declarative.json`,
      `../src/main/resources/programs/${key}.json`,
      `../src/main/resources/programs/${key}-declarative.json`
    ];
    const traceCandidates = [
      `../examples/traces/${key}.json`,
      `../examples/traces/${key}-declarative.json`
    ];
    for(const p of traceCandidates){
      try{
        const res = await fetch(p);
        if(myGen !== demoGen) return;
        if(res.ok){
          const raw = await res.json(); if(myGen !== demoGen) return; handleRaw(raw);
          for(const pp of progCandidates){
            try{ const pr = await fetch(pp); if(myGen !== demoGen) return; if(pr.ok){ const pj = await pr.json(); if(myGen !== demoGen) return; try{ validateProgram(pj); }catch(err){ showError('Demo program invalid: '+err.message); break; } programDef=pj; renderHeader(); renderTimeline(); renderStateStrip(); break; } }catch{}
          }
          return;
        }
      }catch{}
      if(myGen !== demoGen) return;
    }
    if(myGen !== demoGen) return;
    const s = samples[key];
    if(s){ currentTrace=s.trace; currentMeta=s.meta; selectedIdx=s.trace.threads.length; clearError(); renderHeader(); renderTimeline(); renderStateStrip(); }
  });
});
