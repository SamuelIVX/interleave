// Verdict compatibility assertions for normalize(), run by the Visualizer compatibility CI job.
// Covers verdict selection for supported result shapes and fixtures, including bounded runs.
// Does not cover rendering, browser interactions, or freshly generated Java output.
//
// Run: node docs/visualizer-normalize-check.mjs
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const read = (rel) => readFileSync(join(root, rel), 'utf8');
const src = read('docs/visualizer.js');
// Extract normalize(). A guard is required rather than a bare slice:
// indexOf returns -1 on a rename or reformat, and slice(-1, ...) would then yield garbage and
// report it as a per-case failure instead of naming the real cause.
const start = src.indexOf('function normalizeResourcePrefix(');
const normalizeStart = src.indexOf('function normalize(');
const endMarker = normalizeStart < 0 ? -1 : src.indexOf('\n}\n', normalizeStart);
if (start < 0 || normalizeStart < start || endMarker < 0) {
  console.error('Cannot locate normalize() in docs/visualizer.js — extraction aborted.');
  process.exit(2);
}
const end = endMarker + 3;
// Read the production caps instead of restating them in this harness and silently drifting.
const caps = src.match(/^const MAX_(?:SCHEDULE|THREADS) = [0-9]+;$/gm);
assert.equal(caps?.length, 2, 'Cannot locate normalizer cap declarations');
const normalize = new Function(caps.join('\n') + '\n' + src.slice(start, end) + '\nreturn normalize;')();

// Explicit symmetry identity survives both report wrappers and library results; truthy strings do not enable it.
for (const raw of [
  {benchmarks:[{strategy:'DFS',storeType:'EXACT',verdict:'PASS',symmetryReduction:true,statesExplored:5}]},
  {programKind:'SOURCE',storeType:'EXACT',symmetryReduction:true,completedTraces:[],hasViolation:false},
  {benchmarks:[{verdict:'VIOLATION',symmetryReduction:true,failingTrace:{threads:[0],outcome:'VIOLATION'}}]}
]) assert.equal(normalize(raw).meta.symmetryReduction, true);
assert.equal(normalize({benchmarks:[{verdict:'PASS',symmetryReduction:'true'}]}).meta.symmetryReduction, false);

// Fresh producer artifact verifies the combined symmetry/approximate shape used by browser CI.
if(process.env.SOURCE_APPROXIMATE_SYMMETRY_JSON){
  const normalized = normalize(JSON.parse(read(process.env.SOURCE_APPROXIMATE_SYMMETRY_JSON)));
  assert.equal(normalized.meta.symmetryReduction, true);
  assert.equal(normalized.meta.storeType, 'BITSTATE');
  assert.equal(normalized.meta.verdict, 'APPROXIMATE_PASS');
}

if(process.env.SOURCE_APPROXIMATE_CBS_DIR){
  for(const [suffix,verdict] of [['absence','APPROXIMATE_PASS'],['failure','VIOLATION'],['cutoff','INCOMPLETE'],['resource','INCOMPLETE']]){
    const normalized = normalize(JSON.parse(read(process.env.SOURCE_APPROXIMATE_CBS_DIR+'/source-approximate-cbs-'+suffix+'.json')));
    assert.equal(normalized.meta.symmetryReduction,true);
    assert.equal(normalized.meta.storeType,'BITSTATE');
    assert.equal(normalized.meta.verdict,verdict);
  }
}

if(process.env.SOURCE_APPROXIMATE_DPOR_DIR){
  const directory = process.env.SOURCE_APPROXIMATE_DPOR_DIR;
  const all = JSON.parse(read(directory+'/source-approximate-all-strategies.json'));
  assert.equal(all.benchmarks.length,4);
  for(const row of all.benchmarks){
    const normalized = normalize({...all,benchmarks:[row]});
    assert.equal(normalized.meta.symmetryReduction,true);assert.equal(normalized.meta.storeType,'BITSTATE');
    assert.equal(normalized.meta.strategy,row.strategy);assert.equal(normalized.meta.verdict,'APPROXIMATE_PASS');
  }
  for(const [suffix,verdict] of [['assertion','VIOLATION'],['runtime','VIOLATION'],['deadlock','DEADLOCK'],['cutoff','INCOMPLETE'],['resource','INCOMPLETE']]){
    const normalized = normalize(JSON.parse(read(directory+'/source-approximate-dpor-'+suffix+'.json')));
    assert.equal(normalized.meta.symmetryReduction,true);assert.equal(normalized.meta.storeType,'BITSTATE');
    assert.equal(normalized.meta.verdict,verdict);
  }
}

// Proof metadata is producer information, scoped strictly to original subsequences.
const cutoff = {reason:'LOOP_LIMIT',threadId:0,instructionIndex:0,limit:0,iterations:0};
const cutoffSearch = {domain:'ORIGINAL_SUBSEQUENCES',shortestProven:false,candidatesChecked:1,candidateLimit:4};
const partial = {threads:[0],outcome:'INCOMPLETE',cutoff,cutoffSearch};
assert.deepEqual(normalize(partial).trace.cutoffSearch, cutoffSearch);
assert.equal(normalize({...partial,cutoffSearch:{...cutoffSearch,shortestProven:true}}).trace.cutoffSearch.shortestProven, true);
for(const invalid of [null, {}, {...cutoffSearch,domain:'GLOBAL'}, {...cutoffSearch,shortestProven:'true'},
  {...cutoffSearch,candidatesChecked:-1}, {...cutoffSearch,candidatesChecked:5},
  {...cutoffSearch,candidateLimit:0.5}, {...cutoffSearch,candidateLimit:Number.MAX_SAFE_INTEGER+1}]) {
  assert.throws(()=>normalize({...partial,cutoffSearch:invalid}), /Invalid cutoff search/);
}
assert.throws(()=>normalize({...partial,outcome:'PASS'}), /Invalid cutoff search/);
assert.throws(()=>normalize({...partial,cutoff:undefined}), /Invalid cutoff search/);
assert.equal(normalize({threads:[],outcome:'INCOMPLETE',cutoff}).trace.cutoffSearch, undefined);
assert.deepEqual(normalize({benchmarks:[{verdict:'INCOMPLETE',incompleteTrace:partial}]}).trace.cutoffSearch, cutoffSearch);
assert.deepEqual(normalize({incompleteTraces:[partial]}).trace.cutoffSearch, cutoffSearch);
assert.throws(()=>normalize({benchmarks:[{verdict:'PASS',incompleteTrace:partial}]}), /Invalid cutoff search/);

let fails = 0;
const check = (name, raw, expected) => {
  let got;
  try { got = normalize(raw).meta.verdict; } catch (e) { got = 'THREW: ' + e.message; }
  const ok = got === expected;
  if (!ok) fails++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}: got ${JSON.stringify(got)} want ${JSON.stringify(expected)}`);
};

check('incomplete-only', JSON.parse(read('examples/traces/incomplete-only.json')), 'INCOMPLETE');
check('limit-exceeded (not merged)', {failingTraces:[],deadlockedTraces:[],completedTraces:[],incompleteTraces:[],limitExceeded:true,hasViolation:false}, 'LIMIT EXCEEDED');
check('violation still wins over incomplete', {failingTraces:[{threads:[0],outcomes:['ADVANCED'],outcome:'VIOLATION'}],incompleteTraces:[{threads:[1],outcomes:['ADVANCED'],outcome:'INCOMPLETE'}],hasViolation:true,limitExceeded:false}, 'VIOLATION');
check('incomplete wins over completed (bounded run must not read as exhaustive)', {failingTraces:[],deadlockedTraces:[],completedTraces:[{threads:[0,0],outcomes:['ADVANCED','ADVANCED'],outcome:'COMPLETED'}],incompleteTraces:[{threads:[1],outcomes:['ADVANCED'],outcome:'INCOMPLETE'}],hasViolation:false,limitExceeded:false}, 'INCOMPLETE');
check('genuine pass unchanged', {failingTraces:[],deadlockedTraces:[],completedTraces:[],incompleteTraces:[],limitExceeded:false,hasViolation:false}, 'PASS');
check('reportwriter benchmarks INCOMPLETE', {benchmarks:[{bug:'peterson',strategy:'CONTEXT_BOUNDED',verdict:'INCOMPLETE'}]}, 'INCOMPLETE');
check('existing peterson fixture', JSON.parse(read('examples/traces/peterson.json','utf8')), 'PASS');
check('existing lost-update fixture', JSON.parse(read('examples/traces/lost-update.json','utf8')), 'VIOLATION');
check('existing bounded-buffer fixture', JSON.parse(read('examples/traces/bounded-buffer-declarative.json','utf8')), 'PASS');
check('legacy shape (no incompleteTraces) still recognized', {failingTraces:[{threadIds:[0],outcome:'VIOLATION'}],hasViolation:true,limitExceeded:false}, 'VIOLATION');
check('source loop report', {benchmarks:[{bug:'bounded',verdict:'INCOMPLETE',incompleteTrace:{threads:[],outcome:'INCOMPLETE',cutoff:{reason:'LOOP_LIMIT',threadId:0,instructionIndex:0,limit:0,iterations:0}}}]}, 'INCOMPLETE');
check('source resource limit', {programKind:'SOURCE',completedTraces:[],incompleteTraces:[],limitExceeded:true,hasViolation:false}, 'INCOMPLETE');
check('approximate source absence with no trace', {programKind:'SOURCE',storeType:'BITSTATE',completedTraces:[],hasViolation:false}, 'APPROXIMATE_PASS');
check('source with unknown store has no absence claim', {programKind:'SOURCE',completedTraces:[],hasViolation:false}, 'UNKNOWN');
check('unknown source store cannot imply exact pass', {programKind:'SOURCE',storeType:'UNRECOGNIZED',completedTraces:[{threads:[],outcome:'COMPLETED'}],hasViolation:false}, 'COMPLETED');
check('approximate source completion', {programKind:'SOURCE',storeType:'BITSTATE',completedTraces:[{threads:[],outcome:'COMPLETED'}],hasViolation:false}, 'APPROXIMATE_PASS');
for(const [name, trace, expected] of [
  ['incomplete trace outcome', {outcome:'INCOMPLETE'}, 'INCOMPLETE'],
  ['incomplete trace default', {}, 'INCOMPLETE'],
  ['selected trace outcome', {outcome:'DEADLOCK'}, 'DEADLOCK']
]){
  const normalized = normalize({benchmarks:[{incompleteTrace:{threads:[],...trace}}]});
  if(normalized.trace.outcome !== expected || normalized.meta.verdict !== expected){
    fails++; console.log(`FAIL ${name}: trace and meta must both report ${expected}`);
  }
}
check('explicit benchmark verdict wins', {benchmarks:[{verdict:'VIOLATION',incompleteTrace:{threads:[],outcome:'INCOMPLETE'}}]}, 'VIOLATION');
check('legacy failing trace default', {benchmarks:[{failingTrace:{threads:[]}}]}, 'VIOLATION');
const sourceStep = {instructionIndex:2,source:{file:'sample.ilv',line:3,column:1,endLine:3,endColumn:9},label:'atomic {}',expansionPath:[]};
const sourceTrace = {threads:[0],outcomes:['ADVANCED'],outcome:'COMPLETED',sourceDigest:'a'.repeat(64),executedSteps:[sourceStep]};
if(normalize(sourceTrace).trace.executedSteps?.[0]?.instructionIndex !== 2){ fails++; console.log('FAIL source instruction identity'); }
try { normalize({...sourceTrace,executedSteps:[]}); fails++; console.log('FAIL misaligned source metadata accepted'); } catch { /* Expected boundary rejection. */ }
for(const malformed of [{failure:{kind:'INVARIANT',phase:'STEP'}}, {failure:{kind:'RUNTIME_ERROR',phase:'STEP'}},
  {failure:{kind:'ASSERTION',phase:'PROPERTY'}}, {failure:{kind:'ASSERTION',phase:'STEP',category:'ARRAY_BOUNDS'}},
  {cutoff:{reason:'LOOP_LIMIT',threadId:0,instructionIndex:0,limit:0,iterations:0,expansionPath:[{sourceOffset:-1,iteration:0}]}}]){
  try { normalize({...sourceTrace,...malformed}); fails++; console.log('FAIL malformed terminal evidence accepted'); } catch { /* Expected boundary rejection. */ }
}
const assertionFailure = {kind:'ASSERTION',phase:'STEP',source:sourceStep.source};
const assertionTrace = {...sourceTrace,outcomes:['ASSERTION_FAILED'],outcome:'VIOLATION',failure:assertionFailure};
for(const [name, raw] of [
  ['direct assertion trace', assertionTrace],
  ['assertion trace record aliases', {...assertionTrace,threadIds:[0]}],
  ['assertion result wrapper', {failingTraces:[assertionTrace],hasViolation:true}],
  ['assertion report wrapper', {benchmarks:[{verdict:'VIOLATION',failingTrace:assertionTrace}]}]
]){
  check(name, raw, 'VIOLATION');
  assert.deepEqual(normalize(raw).trace.failure, assertionFailure);
}
// Resource provenance never acts as a replayed cutoff or an exhaustive verdict.
const terminalState = {fields:{x:1},locals:{'0':{own:[3,4]}}};
const resource = {...sourceTrace,outcome:'INCOMPLETE',resourceLimitReason:'MAX_STATES',programCounters:[1],terminalState};
const replay = {kind:'PREFIX',propertyChecked:false,states:[{fields:{x:0},locals:{'0':{own:[3,4]}}},terminalState]};
assert.equal(normalize(resource).meta.verdict, 'INCOMPLETE');
assert.deepEqual(normalize(resource).trace.states, [null, terminalState]);
assert.equal(normalize(resource).meta.resourcePrefix, true);
assert.deepEqual(normalize({...resource,replay}).trace.states, replay.states);
for(const kind of ['PREFIX','COMPLETED','DEADLOCK','INCOMPLETE','VIOLATION']) {
  const observed = {...replay,kind,propertyChecked:true,...(kind==='VIOLATION' ? {failure:{kind:'INVARIANT',phase:'PROPERTY'}} : {})};
  const normalized = normalize({...resource,replay:observed});
  assert.equal(normalized.meta.verdict, 'INCOMPLETE');
  assert.equal(normalized.meta.replayKind, kind);
  assert.equal(normalized.trace.failure, undefined);
}
const resourceRun = {programKind:'SOURCE',resourceLimitReason:'MAX_STATES',limitExceeded:true,
  failingTraces:[],deadlockedTraces:[],incompleteTraces:[],completedTraces:[],resourcePrefix:resource};
assert.equal(normalize(resourceRun).meta.resourcePrefix, true);
assert.equal(normalize({...resourceRun,completedTraces:[sourceTrace]}).meta.resourcePrefix, true);
assert.equal(normalize({...resourceRun,incompleteTraces:[partial]}).meta.resourcePrefix, true);
assert.equal(normalize({...resourceRun,failingTraces:[assertionTrace],hasViolation:true}).meta.verdict, 'VIOLATION');
assert.equal(normalize({...resourceRun,deadlockedTraces:[{threads:[],outcome:'DEADLOCK'}]}).meta.verdict, 'DEADLOCK');
for(const invalid of [
  {...resource,outcome:'PASS'}, {...resource,resourceLimitReason:'UNKNOWN'}, {...resource,cutoff},
  {...resource,failure:{kind:'INVARIANT',phase:'PROPERTY'}}, {...resource,cutoffSearch},
  {...resource,sourceDigest:null}, {...resource,executedSteps:null}, {...resource,outcomes:[]},
  {...resource,threads:Array(5001).fill(0)},
  {...resource,outcomes:['ASSERTION_FAILED']}, {...resource,threads:[-1]}, {...resource,threads:[0.5]},
  {...resource,programCounters:[]}, {...resource,programCounters:[-1]}, {...resource,programCounters:Array(65).fill(1)},
  {...resource,terminalState:null}, {...resource,terminalState:{fields:[],locals:{}}},
  {...resource,terminalState:{fields:{},locals:{'0':null}}},
  {...resource,terminalState:{fields:{},locals:{bad:{}}}},
  {...resource,terminalState:{fields:{x:1},locals:{'1':{}}}},
  {...resource,replay:null}, {...resource,replay:{...replay,kind:'PASS'}},
  {...resource,replay:{...replay,propertyChecked:'true'}}, {...resource,replay:{...replay,states:[terminalState]}},
  {...resource,replay:{...replay,states:[terminalState,{fields:{x:2},locals:{}}]}},
  {...resource,replay:{...replay,states:[{fields:{x:0},locals:{'1':{}}},terminalState]}},
  {...resource,replay:{...replay,kind:'VIOLATION'}},
  {...resource,replay:{...replay,failure:{kind:'INVARIANT',phase:'PROPERTY'}}},
  {...resource,replay:{...replay,kind:'VIOLATION',propertyChecked:false,failure:{kind:'INVARIANT',phase:'PROPERTY'}}},
  {...resource,replay:{...replay,cutoff}},
  {...resource,replay:{...replay,kind:'VIOLATION',propertyChecked:true,failure:{kind:'RUNTIME_ERROR',phase:'PROPERTY',category:'BAD'}}},
  {...resourceRun,limitExceeded:false}, {...resourceRun,hasViolation:true}, {...resourceRun,programKind:'LEGACY'},
  {...resourceRun,resourceLimitReason:'MAX_TIME'}, {...resourceRun,resourcePrefix:null}
]) assert.throws(()=>normalize(invalid), /resource|Resource|source|Misaligned|failure/);
assert.equal(normalize({...resource,replay:{...replay,states:[replay.states[0],{locals:terminalState.locals,fields:terminalState.fields}]}}).meta.verdict, 'INCOMPLETE');
console.log(fails === 0 ? '\nall checks passed' : `\n${fails} FAILED`);
process.exit(fails === 0 ? 0 : 1);
