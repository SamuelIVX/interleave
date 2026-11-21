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
 * Normalize captured resource evidence without treating a reported replay as a run verdict.
 * SECURITY: validates resource/source alignment and snapshot shape before DOM assignment.
 * @param {Object} value - standalone resource-prefix payload
 * @param {Function} diagnostics - existing source/failure diagnostic validator
 * @returns {{trace: Object, meta: Object}} original incomplete prefix and separate observations
 * @throws {Error} on invalid or conflicting resource evidence
 */
function normalizeResourcePrefix(value, diagnostics){
  const object = v => v !== null && typeof v === 'object' && !Array.isArray(v);
  const snapshot = v => object(v) && object(v.fields) && object(v.locals) &&
    Object.entries(v.locals).every(([owner, locals])=>/^(0|[1-9][0-9]*)$/.test(owner) &&
      Number(owner)<value.programCounters.length && object(locals));
  const sameValue = (a, b) => a === b || (object(a) && object(b) || Array.isArray(a) && Array.isArray(b)) &&
    Object.keys(a).length === Object.keys(b).length && Object.keys(a).every(k=>Object.hasOwn(b,k) && sameValue(a[k],b[k]));
  if(!object(value) || value.outcome!=='INCOMPLETE' || !['MAX_STATES','MAX_TIME'].includes(value.resourceLimitReason) ||
    value.cutoff!=null || value.failure!=null || value.cutoffSearch!=null || typeof value.sourceDigest!=='string' ||
    !Array.isArray(value.executedSteps) || !Array.isArray(value.threads) || value.threads.length>MAX_SCHEDULE ||
    !Array.isArray(value.outcomes) || value.outcomes.length!==value.threads.length ||
    !value.outcomes.every(v=>['ADVANCED','BLOCKED','TERMINATED'].includes(v)) ||
    !Array.isArray(value.programCounters) || value.programCounters.length>MAX_THREADS ||
    !value.programCounters.every(v=>Number.isSafeInteger(v) && v>=0) ||
    !value.threads.every(v=>Number.isSafeInteger(v) && v>=0 && v<value.programCounters.length) ||
    !snapshot(value.terminalState)) throw new Error('Invalid resource prefix');
  const source = diagnostics(value, value.threads);
  let states = Array(value.threads.length+1).fill(null);
  states[value.threads.length] = value.terminalState;
  const meta = {verdict:'INCOMPLETE',programKind:'SOURCE',limitExceeded:true,resourcePrefix:true,
    resourceLimitReason:value.resourceLimitReason,programCounters:value.programCounters};
  if(Object.hasOwn(value,'replay')){
    const r = value.replay;
    if(!object(r) || !['PREFIX','COMPLETED','DEADLOCK','VIOLATION','INCOMPLETE'].includes(r.kind) ||
      typeof r.propertyChecked!=='boolean' || !Array.isArray(r.states) || r.states.length!==states.length ||
      !r.states.every(snapshot) || !sameValue(r.states.at(-1), value.terminalState) ||
      (r.kind==='VIOLATION' ? !r.failure || !r.propertyChecked || r.failure.phase!=='PROPERTY' : r.failure!=null) ||
      r.cutoff!=null) throw new Error('Invalid resource replay observation');
    diagnostics({failure:r.failure}, value.threads);
    states = r.states;
    meta.replayKind = r.kind; meta.propertyChecked = r.propertyChecked; meta.replayFailure = r.failure;
  }
  return {trace:{...source,threads:value.threads,outcomes:value.outcomes,outcome:'INCOMPLETE',states},meta};
}

/** Validates distinct BYTECODE data without coercing values or inventing source spans.
 * SECURITY: checks bounded structures before rendering; replay authentication belongs to Java.
 * @param {Object} raw untrusted trace, result or report
 * @returns {{trace:Object,meta:Object}} original witness plus honest run verdict
 * @throws {Error} for incompatible identity, schema, precision or terminal classifications
 */
function normalizeBytecode(raw){
  const fail = () => {throw new Error('Invalid BYTECODE evidence or precision');};
  const object = x => x && typeof x==='object' && !Array.isArray(x);
  const text = x => typeof x==='string' && x.length>0 && x.length<=65536;
  const integer = x => Number.isSafeInteger(x) && x>=0;
  const digest = x => typeof x==='string' && /^[0-9a-f]{64}$/.test(x);
  const method = x => object(x) && ['owner','name','descriptor'].every(k=>text(x[k]));
  const location = x => method(x) && integer(x.bci);
  const identity = x => {
    if(!object(x) || !digest(x.artifactDigest) || !['BYTECODE-SC-1','BYTECODE-SC-2'].includes(x.profileVersion) || !text(x.modelClass) || x.mode!=='BATCHED' ||
      x.requestedStrategy!=='DFS' || x.effectiveStrategy!=='DFS' || x.fallbackReason!=null || !Array.isArray(x.actors) ||
      x.actors.length<1 || x.actors.length>8 || !Array.isArray(x.checks) || x.checks.length>65536) fail();
    x.actors.forEach((a,i)=>{if(!object(a) || a.id!==i || !method(a.method) || a.method.descriptor!=='()V')fail();});
    x.checks.forEach(c=>{if(!object(c) || !method(c.method) || c.method.descriptor!=='()Z' || !['ALWAYS','FINAL'].includes(c.when))fail();});
    const ceilings={actorInstructions:4096,actorAllocations:4096,actorHeapCells:65536,maxCallDepth:256,maxArrayLength:4096,maxStringLength:4096,setupInstructions:65536,setupAllocations:4096,setupHeapCells:65536,checkInstructions:16384};
    if(!object(x.limits) || Object.keys(x.limits).length!==10)fail();
    for(const [k,max] of Object.entries(ceilings))if(!integer(x.limits[k]) || x.limits[k]<1 || x.limits[k]>max)fail();
  };
  const equal = (a,b) => {
    if(a===b)return true;
    if(!a || !b || typeof a!=='object' || typeof b!=='object' || Array.isArray(a)!==Array.isArray(b))return false;
    const keys=Object.keys(a);return keys.length===Object.keys(b).length && keys.every(k=>Object.hasOwn(b,k) && equal(a[k],b[k]));
  };
  const fault = f => {if(!object(f) || !text(f.type) || !location(f.location) || !Array.isArray(f.stack) || !f.stack.length || f.stack.length>256 || !f.stack.every(location))fail();};
  const cutoff = c => {if(!object(c) || !text(c.reason) || !location(c.location) || !integer(c.limit) || c.limit<1 || !integer(c.required) || c.required<1 || !integer(c.remaining))fail();};
  const terminal = (t,replay=false) => {
    const outcomes={COMPLETED:'COMPLETED',DEADLOCK:'DEADLOCK',UNCAUGHT_EXCEPTION:'VIOLATION',CHECK_FALSE:'VIOLATION',CUTOFF:'INCOMPLETE',OBSERVER_ERROR:'INCOMPLETE',OBSERVER_LIMIT:'INCOMPLETE',INTERRUPTED:'INCOMPLETE'};
    if(!object(t) || (!Object.hasOwn(outcomes,t.kind) && !(replay && t.kind==='PREFIX')) || !Array.isArray(t.waits) || t.waits.length>8)fail();
    const actorFailure=['UNCAUGHT_EXCEPTION','CUTOFF'].includes(t.kind);
    if(actorFailure ? !integer(t.actor) || t.actor>7 : t.actor!==-1)fail();
    if(t.fault!=null){if(!actorFailure)fail();fault(t.fault);}else if(t.kind==='UNCAUGHT_EXCEPTION')fail();
    if((t.cutoff!=null)!==(t.kind==='CUTOFF'))fail();if(t.cutoff)cutoff(t.cutoff);
    const checkKind={CHECK_FALSE:'FALSE',OBSERVER_ERROR:'ERROR',OBSERVER_LIMIT:'LIMIT'}[t.kind];
    if((t.check!=null)!==(checkKind!=null))fail();
    if(t.check){const c=t.check;if(c.kind!==checkKind || !method(c.check) || !location(c.location) || (c.fault!=null)!==(checkKind==='ERROR') || (c.cutoff!=null)!==(checkKind==='LIMIT'))fail();if(c.fault)fault(c.fault);if(c.cutoff)cutoff(c.cutoff);}
    if((t.waits.length>0)!==(t.kind==='DEADLOCK'))fail();
    t.waits.forEach(w=>{if(!object(w) || !integer(w.actor) || w.actor>7 || !integer(w.owner) || w.owner>7 || !text(w.monitor) || !integer(w.depth) || w.depth<1)fail();});
    if((t.interruption!=null)!==(t.kind==='INTERRUPTED') || (t.interruption!=null && !['MAX_STATES','MAX_TIME','KEY_BYTES','CANCELLED'].includes(t.interruption)))fail();
    return outcomes[t.kind];
  };
  const opcodes=new Set('NOP POP POP2 DUP DUP_X1 DUP_X2 DUP2 DUP2_X1 DUP2_X2 SWAP IADD ISUB IMUL IDIV IREM INEG ISHL ISHR IUSHR IAND IOR IXOR I2B I2C I2S MONITORENTER MONITOREXIT ATHROW IFEQ IFNE IFLT IFGE IFGT IFLE IF_ICMPEQ IF_ICMPNE IF_ICMPLT IF_ICMPGE IF_ICMPGT IF_ICMPLE IF_ACMPEQ IF_ACMPNE IFNULL IFNONNULL GOTO GOTO_W ACONST_NULL ICONST_M1 ICONST_0 ICONST_1 ICONST_2 ICONST_3 ICONST_4 ICONST_5 BIPUSH SIPUSH LDC LDC_W ILOAD ALOAD ISTORE ASTORE ILOAD_W ALOAD_W ISTORE_W ASTORE_W IINC IINC_W IRETURN ARETURN RETURN LOOKUPSWITCH TABLESWITCH GETFIELD PUTFIELD GETSTATIC PUTSTATIC INVOKESTATIC INVOKESPECIAL INVOKEVIRTUAL INVOKEINTERFACE NEW NEWARRAY ANEWARRAY MULTIANEWARRAY ARRAYLENGTH IALOAD BALOAD CALOAD SALOAD AALOAD IASTORE BASTORE CASTORE SASTORE AASTORE CHECKCAST INSTANCEOF'.split(' '));
  for(const prefix of ['ILOAD','ALOAD','ISTORE','ASTORE'])for(let i=0;i<4;i++)opcodes.add(prefix+'_'+i);
  const typed = v => {
    if(!object(v) || typeof v.raw!=='string' || v.raw.length>65536)fail();
    switch(v.type){
      case 'INT': if(!/^(0|-?[1-9][0-9]*)$/.test(v.raw) || BigInt(v.raw)<-2147483648n || BigInt(v.raw)>2147483647n)fail();break;
      case 'LONG': if(!/^(0|-?[1-9][0-9]*)$/.test(v.raw) || BigInt(v.raw)<-9223372036854775808n || BigInt(v.raw)>9223372036854775807n)fail();break;
      case 'FLOAT_BITS': if(!/^[0-9a-f]{8}$/.test(v.raw))fail();break;
      case 'DOUBLE_BITS': if(!/^[0-9a-f]{16}$/.test(v.raw))fail();break;
      case 'BOOLEAN': if(!['true','false'].includes(v.raw))fail();break;
      case 'NULL': if(v.raw!=='')fail();break;
      case 'REFERENCE': case 'TEXT': case 'FAULT': if(!text(v.raw))fail();break;
      default: fail();
    }
  };
  const values = (map,limit=65536) => {if(!object(map) || Object.keys(map).length>limit)fail();Object.entries(map).forEach(([k,v])=>{if(!text(k))fail();typed(v);});};
  const trace = t => {
    if(!object(t) || t.programKind!=='BYTECODE' || ['sourceDigest','executedSteps','failure','cutoff'].some(k=>Object.hasOwn(t,k)))fail();
    const d=t.bytecodeEvidence;
    if(!object(d) || d.version!==1)fail();identity(d.identity);
    if(t.programHash!==d.identity.artifactDigest || !Array.isArray(t.threads) || t.threads.length>MAX_SCHEDULE || !t.threads.every(a=>integer(a) && a<d.identity.actors.length) ||
      !Array.isArray(t.outcomes) || t.outcomes.length!==t.threads.length || !t.outcomes.every(o=>['ADVANCED','TERMINATED','ASSERTION_FAILED'].includes(o)) ||
      !Array.isArray(d.transitions) || d.transitions.length!==t.threads.length || !Array.isArray(d.snapshots) || d.snapshots.length!==t.threads.length+1 || !Array.isArray(t.states) || t.states.length!==d.snapshots.length)fail();
    let work=0;
    d.transitions.forEach((s,i)=>{
      if(!object(s) || s.actor!==t.threads[i] || !Array.isArray(s.operations) || !s.operations.length || (work+=s.operations.length)>65536)fail();
      s.operations.forEach(op=>{if(!object(op) || !location(op.location) || (op.line!=null && (!integer(op.line) || op.line<1)) || !['OPCODE','MONITOR_ENTER','RETURN','EXCEPTION','TERMINATION','FAILURE','CUTOFF','FUEL_CUTOFF'].includes(op.event) || (op.opcode!=null)!==(op.event==='OPCODE') || (op.opcode!=null && !opcodes.has(op.opcode)))fail();});
    });
    // Each object/array header pays for its length slot; setup has at most nine diagnostics
    // after construction completes. Every actor permanently owns its heap-cell charges.
    const limits=d.identity.limits;
    const fieldLimit=limits.setupHeapCells+d.identity.actors.length*limits.actorHeapCells+9;
    d.snapshots.forEach((s,i)=>{
      if(!object(s) || !digest(s.stateDigest) || !object(s.locals) || Object.keys(s.locals).length>8 || !Array.isArray(s.heap) || s.heap.length>36864)fail();
      values(s.fields,fieldLimit);Object.values(s.locals).forEach(map=>values(map));
      s.heap.forEach(o=>{if(!object(o) || !text(o.identity) || !text(o.type) || typeof o.shared!=='boolean' || typeof o.initialized!=='boolean' || !Number.isSafeInteger(o.owner) || o.owner<-2 || o.owner>7 || !integer(o.depth) || (o.owner===-2)!==(o.depth===0))fail();});
      const display = map => Object.fromEntries(Object.entries(map).map(([k,v])=>[k,v.type.toLowerCase()+':'+v.raw]));
      if(!equal(t.states[i],{fields:display(s.fields),locals:Object.fromEntries(Object.entries(s.locals).map(([a,v])=>[a,display(v)]))}))fail();
    });
    if(terminal(d.terminal)!==t.outcome)fail();if(d.replayObservation!=null)terminal(d.replayObservation,true);
    return t;
  };
  if(Array.isArray(raw.threads)){
    const t=trace(raw);return {trace:t,meta:{programKind:'BYTECODE',verdict:t.outcome,bug:t.bytecodeEvidence.identity.modelClass,storeType:'EXACT',strategy:'DFS'}};
  }
  if(raw.programKind!=='BYTECODE' || raw.storeType!=='EXACT' || raw.strategy!=='DFS' || raw.requestedStrategy!=='DFS' || raw.effectiveStrategy!=='DFS' || raw.fallbackReason!=null || raw.symmetryReduction===true || typeof raw.limitExceeded!=='boolean' || typeof raw.hasViolation!=='boolean')fail();
  identity(raw.bytecodeIdentity);
  const buckets=['failingTraces','deadlockedTraces','incompleteTraces','completedTraces'];
  const expected=['VIOLATION','DEADLOCK','INCOMPLETE','COMPLETED'];
  const traces=[];
  buckets.forEach((key,i)=>{if(!Array.isArray(raw[key]) || raw[key].length>MAX_SCHEDULE)fail();for(const t of raw[key]){trace(t);if(t.outcome!==expected[i] || !equal(t.bytecodeEvidence.identity,raw.bytecodeIdentity))fail();traces.push(t);}});
  if(!traces.length || raw.hasViolation!==(raw.failingTraces.length>0))fail();
  const verdict=raw.failingTraces.length || raw.deadlockedTraces.length ? 'VIOLATION' : raw.limitExceeded || raw.incompleteTraces.length ? 'INCOMPLETE' : 'PASS';
  if(raw.verdict!==verdict)fail();
  const selected=traces[0];
  return {trace:selected,meta:{programKind:'BYTECODE',verdict,bug:raw.bug || raw.bytecodeIdentity.modelClass,storeType:'EXACT',strategy:'DFS',statesExplored:raw.statesExplored,wallTimeMs:raw.wallTimeMs,heapDeltaBytes:raw.heapDeltaBytes,limitExceeded:raw.limitExceeded}};
}

/**
 * Normalize any supported raw JSON payload into a {trace, meta} pair.
 * Supports resource prefixes, TraceRecord {threads}, ReportWriter {benchmarks}, and TestResult
 * {failingTraces|deadlockedTraces|completedTraces|incompleteTraces}.
 * SECURITY: treats all fields as untrusted — never uses innerHTML, validates shapes.
 * @param {*} raw - parsed JSON payload
 * @returns {{trace: Object, meta: Object}} normalized trace and metadata
 * @throws {Error} when the shape is unrecognized or required fields are missing
 */
function normalize(raw){
  if(!raw || typeof raw !== 'object') throw new Error('JSON must be an object');
  if(Array.isArray(raw.benchmarks)){
    const selected=raw.benchmarks.find(b=>b.failingTrace) || raw.benchmarks.find(b=>b.incompleteTrace) || raw.benchmarks[0];
    if(selected?.programKind==='BYTECODE')return normalizeBytecode(selected);
  }
  if(raw.programKind==='BYTECODE' || raw.bytecodeEvidence!=null)return normalizeBytecode(raw);
  const diagnostics = (value, threads, outcome = value.outcome) => {
    const validSpan = (span) => span && typeof span.file === 'string' &&
      ['line','column','endLine','endColumn'].every(k=>Number.isSafeInteger(span[k]) && span[k]>0) &&
      (span.endLine>span.line || (span.endLine===span.line && span.endColumn>=span.column));
    const validPath = path => Array.isArray(path) && path.every(p=>p && Number.isSafeInteger(p.sourceOffset) && p.sourceOffset>=0 && Number.isSafeInteger(p.iteration) && p.iteration>=0);
    if(value.sourceDigest != null && (typeof value.sourceDigest !== 'string' || !/^[0-9a-f]{64}$/.test(value.sourceDigest))) throw new Error('Invalid sourceDigest');
    if(value.executedSteps != null){
      if(!Array.isArray(value.executedSteps) || value.executedSteps.length!==threads.length || value.executedSteps.length>MAX_SCHEDULE) throw new Error('Misaligned executedSteps');
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
      if(!['INVARIANT','RUNTIME_ERROR','ASSERTION'].includes(f.kind) || !['STEP','PROPERTY'].includes(f.phase) ||
        (f.source!=null && !validSpan(f.source)) || (f.kind==='RUNTIME_ERROR' ?
          !['ARRAY_BOUNDS','MODULO_BY_ZERO','OTHER_EVALUATION_ERROR'].includes(f.category) :
          f.category!=null || f.phase!==(f.kind==='ASSERTION' ? 'STEP' : 'PROPERTY'))) throw new Error('Invalid failure evidence');
    }
    if(value.failure!=null && value.cutoff!=null) throw new Error('Conflicting terminal evidence');
    if(Object.hasOwn(value, 'cutoffSearch')){
      const search=value.cutoffSearch;
      if(!search || search.domain!=='ORIGINAL_SUBSEQUENCES' || typeof search.shortestProven!=='boolean' ||
        !['candidatesChecked','candidateLimit'].every(k=>Number.isSafeInteger(search[k]) && search[k]>=0) ||
        search.candidatesChecked>search.candidateLimit || outcome!=='INCOMPLETE' || !value.cutoff) throw new Error('Invalid cutoff search metadata');
    }
    return {sourceDigest:value.sourceDigest,executedSteps:value.executedSteps,cutoff:value.cutoff,failure:value.failure,cutoffSearch:value.cutoffSearch};
  };
  const runDiagnostics = value => ({symmetryReduction:value.symmetryReduction===true,programKind:value.programKind,storeType:value.storeType,resourceLimitReason:value.resourceLimitReason,incompletenessReasons:value.incompletenessReasons});

  if(Array.isArray(raw.threads) && Object.hasOwn(raw,'resourceLimitReason')){
    return normalizeResourcePrefix(raw, diagnostics);
  }
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
      const outcome = entry.verdict || ft.outcome || (entry.failingTrace ? 'VIOLATION' : 'INCOMPLETE');
      return { trace: { ...diagnostics(ft, threads, outcome), threads, outcomes: ft.outcomes, outcome, programHash: entry.bug, states: ft.states }, meta: { ...runDiagnostics(entry), verdict: outcome, strategy: entry.strategy, storeType: entry.storeType, statesExplored: entry.statesExplored, wallTimeMs: entry.wallTimeMs, heapDeltaBytes: entry.heapDeltaBytes, bug: entry.bug } };
    }
    return { trace: { threads: [], outcome: entry.verdict || 'PASS', programHash: entry.bug }, meta: { ...runDiagnostics(entry), verdict: entry.verdict, strategy: entry.strategy, storeType: entry.storeType, statesExplored: entry.statesExplored, wallTimeMs: entry.wallTimeMs, heapDeltaBytes: entry.heapDeltaBytes, bug: entry.bug } };
  }
  if(Array.isArray(raw.failingTraces) || Array.isArray(raw.deadlockedTraces) || Array.isArray(raw.completedTraces) || Array.isArray(raw.incompleteTraces)){
    const checkedResult = raw.programKind==='SOURCE' || raw.symmetryReduction===true;
    const pick = (arr) => arr && arr.length ? arr[0] : null;
    // Earlier conclusive failures retain priority; otherwise show the actual interrupted position
    // before modeled cutoffs or completed paths. A completed path cannot establish exhaustive PASS.
    let tr = pick(raw.failingTraces) || pick(raw.deadlockedTraces);
    if(!tr && Object.hasOwn(raw,'resourcePrefix')){
      if(raw.programKind!=='SOURCE' || raw.limitExceeded!==true || raw.hasViolation===true || !raw.resourcePrefix ||
        raw.resourceLimitReason!==raw.resourcePrefix.resourceLimitReason || Object.hasOwn(raw.resourcePrefix,'replay')) throw new Error('Conflicting resource prefix provenance');
      const prefix = normalizeResourcePrefix(raw.resourcePrefix, diagnostics);
      return {trace:prefix.trace,meta:{...runDiagnostics(raw),strategy:raw.strategy,statesExplored:raw.statesExplored,
        wallTimeMs:raw.wallTimeMs,heapDeltaBytes:raw.heapDeltaBytes,...prefix.meta}};
    }
    tr ||= pick(raw.incompleteTraces) || pick(raw.completedTraces);
    if(!tr) {
      const incomplete = raw.limitExceeded === true && !raw.hasViolation;
      const verdict = raw.hasViolation ? 'VIOLATION' : incomplete ? (checkedResult ? 'INCOMPLETE' : 'LIMIT EXCEEDED') : (checkedResult ? (raw.storeType==='BITSTATE' ? 'APPROXIMATE_PASS' : raw.storeType==='EXACT' ? 'PASS' : 'UNKNOWN') : 'PASS');
      return { trace: { threads: [], outcome: verdict }, meta: { ...runDiagnostics(raw), verdict, strategy: raw.strategy, statesExplored: raw.statesExplored, wallTimeMs: raw.wallTimeMs, heapDeltaBytes: raw.heapDeltaBytes, limitExceeded: raw.limitExceeded === true } };
    }
    const threads = tr.threads || tr.threadIds;
    if(!Array.isArray(threads)) throw new Error('failingTraces[0] missing threads/threadIds');
    const incomplete = raw.limitExceeded === true && !raw.hasViolation && !['VIOLATION','DEADLOCK'].includes(tr.outcome);
    // LIMIT EXCEEDED (a resource limit stopped the search) and INCOMPLETE (a preemption bound
    // did) are different states and are never merged: the latter arrives here as tr.outcome.
    // Rendering a bounded run that proved nothing as PASS is the most misleading thing this
    // viewer can do, so it must not be reachable.
    let outcome = incomplete ? (checkedResult ? 'INCOMPLETE' : 'LIMIT EXCEEDED') : (tr.outcome || (raw.hasViolation ? 'VIOLATION' : 'PASS'));
    if(checkedResult && ['PASS','COMPLETED'].includes(outcome) && ['EXACT','BITSTATE'].includes(raw.storeType)) outcome = raw.storeType==='BITSTATE' ? 'APPROXIMATE_PASS' : 'PASS';
    return { trace: { ...diagnostics(tr, threads, outcome), threads, outcomes: tr.outcomes, outcome, programHash: tr.programHash, states: tr.states }, meta: { ...runDiagnostics(raw), verdict: outcome, strategy: raw.strategy, statesExplored: raw.statesExplored, wallTimeMs: raw.wallTimeMs, heapDeltaBytes: raw.heapDeltaBytes, hasViolation: raw.hasViolation, limitExceeded: raw.limitExceeded === true } };
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
  if(currentMeta.symmetryReduction===true) parts.push(addKV('reduction', 'Thread symmetry (representative visits)'));
  if(currentMeta.statesExplored != null) parts.push(addKV('states', asNum(currentMeta.statesExplored)));
  if(currentMeta.wallTimeMs != null) parts.push(addKV('time', asNum(currentMeta.wallTimeMs) + ' ms'));
  if(currentMeta.heapDeltaBytes != null) parts.push(addKV('heap', asNum(currentMeta.heapDeltaBytes)));
  if(currentMeta.limitExceeded) parts.push(addKV('limit', 'exceeded'));
  if(currentMeta.resourceLimitReason) parts.push(addKV('resource', capStr(currentMeta.resourceLimitReason)));
  if(currentMeta.resourcePrefix){
    parts.push(addKV('evidence', 'Captured resource prefix; original run incomplete'));
    parts.push(addKV('owner counters', currentMeta.programCounters.join(', ')));
    parts.push(addKV('snapshots', currentMeta.replayKind ? 'Explicit replay; initial through final' : 'Captured final position only'));
    if(currentMeta.replayKind){
      parts.push(addKV('reported replay', currentMeta.replayKind));
      parts.push(addKV('property checked', currentMeta.propertyChecked ? 'yes' : 'no'));
      parts.push(addKV('replay scope', 'One prefix; no exhaustive PASS or reproduced resource limit'));
      if(currentMeta.replayFailure){
        parts.push(addKV('replay failure', currentMeta.replayFailure.kind));
        parts.push(addKV('replay phase', currentMeta.replayFailure.phase));
        if(currentMeta.replayFailure.category) parts.push(addKV('replay category', currentMeta.replayFailure.category));
        const source = currentMeta.replayFailure.source;
        if(source) parts.push(addKV('replay source', `${capName(source.file)}:${source.line}:${source.column}–${source.endLine}:${source.endColumn}`));
      }
    }
  }
  if(Array.isArray(currentMeta.incompletenessReasons)) parts.push(addKV('bounds', capStr(currentMeta.incompletenessReasons.join(', '))));
  if(currentTrace.cutoffSearch){
    const search=currentTrace.cutoffSearch;
    parts.push(addKV('cutoff search', search.shortestProven ? 'Shortest among original subsequences' : 'Best found; shortest not proven'));
    parts.push(addKV('candidate checks', `${search.candidatesChecked} / ${search.candidateLimit}`));
    parts.push(addKV('search scope', 'Original subsequences only; no global shortest guarantee'));
  }
  if(currentTrace.bytecodeEvidence){
    const data=currentTrace.bytecodeEvidence, end=data.terminal;
    const at=p=>`${capName(p.owner)}.${capName(p.name)}${capStr(p.descriptor)} BCI ${p.bci}`;
    parts.push(addKV('profile', `BYTECODE ${data.identity.profileVersion}`));
    parts.push(addKV('artifact', data.identity.artifactDigest));
    parts.push(addKV('traversal', `${data.identity.requestedStrategy} → ${data.identity.effectiveStrategy}`));
    parts.push(addKV('terminal', end.kind));
    if(end.fault)parts.push(addKV('fault', `${capStr(end.fault.type)} at ${at(end.fault.location)}`));
    if(end.cutoff)parts.push(addKV('cutoff', `${capStr(end.cutoff.reason)} actor ${end.actor} at ${at(end.cutoff.location)}; limit ${end.cutoff.limit}, required ${end.cutoff.required}, remaining ${end.cutoff.remaining}`));
    if(end.check)parts.push(addKV('check', `${capStr(end.check.kind)} ${at(end.check.location)}`));
    end.waits.forEach(w=>parts.push(addKV('monitor wait', `actor ${w.actor} waits for ${w.owner} on ${capStr(w.monitor)} depth ${w.depth}`)));
    if(end.interruption)parts.push(addKV('interruption', `${end.interruption}; original run INCOMPLETE`));
    if(data.replayObservation)parts.push(addKV('replay observation', `${data.replayObservation.kind}; one prefix only`));
  }
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
        const bytecodeStep=currentTrace.bytecodeEvidence?.transitions[idx];
        const first=bytecodeStep?.operations[0];
        const bytecodeInfo=first ? {label:`${first.location.name} BCI ${first.location.bci}`,detail:bytecodeStep.operations.map(op=>`${op.location.owner}.${op.location.name}${op.location.descriptor} BCI ${op.location.bci}${op.line==null ? '' : ' line '+op.line} ${op.opcode || op.event}`).join('; ')} : null;
        const info = bytecodeInfo || (sourceStep ? {label:sourceStep.label,detail:`${capName(sourceStep.source.file)}:${sourceStep.source.line}:${sourceStep.source.column}–${sourceStep.source.endLine}:${sourceStep.source.endColumn} — ${sourceStep.label}`} : stepInfoFor(programThreadsById, tid, stepIdx));
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

  if(currentMeta.resourcePrefix && !snapshot){
    const p = document.createElement('p'); p.className = 'placeholder';
    p.textContent = 'Earlier state unavailable: this capture contains only the final position. Explicit replay is required for intermediate snapshots.';
    el.append(p);
    return;
  }
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
  const bytecodeHeap=currentTrace.bytecodeEvidence?.snapshots[selectedIdx]?.heap;
  if(bytecodeHeap){
    for(const object of bytecodeHeap.slice(0,MAX_FIELDS)){
      const row=document.createElement('tr');
      const owner=object.owner===-2 ? 'unowned' : object.owner===-1 ? 'setup' : `actor ${object.owner}`;
      for(const value of ['Monitor',`${object.identity} ${object.type}`,`owner ${owner}; depth ${object.depth}; shared ${object.shared}; initialized ${object.initialized}`]){
        const cell=document.createElement('td');cell.textContent=capStr(value);row.append(cell);
      }
      tbody.append(row);
    }
    if(bytecodeHeap.length>MAX_FIELDS){
      const note=document.createElement('p');note.textContent=`Showing ${MAX_FIELDS} of ${bytecodeHeap.length} monitor objects at this configuration.`;el.append(note);
    }
  }


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
