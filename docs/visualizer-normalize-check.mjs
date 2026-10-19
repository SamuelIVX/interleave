// Verdict compatibility assertions for normalize(), run by the Visualizer compatibility CI job.
// Covers verdict selection for supported result shapes and fixtures, including bounded runs.
// Does not cover rendering, browser interactions, or freshly generated Java output.
//
// Run: node docs/visualizer-normalize-check.mjs
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const read = (rel) => readFileSync(join(root, rel), 'utf8');
const src = read('docs/visualizer.js');
// Extract normalize(). A guard is required rather than a bare slice:
// indexOf returns -1 on a rename or reformat, and slice(-1, ...) would then yield garbage and
// report it as a per-case failure instead of naming the real cause.
const start = src.indexOf('function normalize(');
const endMarker = start < 0 ? -1 : src.indexOf('\n}\n', start);
if (start < 0 || endMarker < 0) {
  console.error('Cannot locate normalize() in docs/visualizer.js — extraction aborted.');
  process.exit(2);
}
const end = endMarker + 3;
// normalize() uses no module-level constant, so nothing needs injecting. Passing MAX_NAME_LEN
// here would restate it as a local (and previously at 200, diverging from the real 64), so a
// future edit that started using it would silently be checked against the wrong limit.
const normalize = new Function(src.slice(start, end) + '\nreturn normalize;')();

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
  {cutoff:{reason:'LOOP_LIMIT',threadId:0,instructionIndex:0,limit:0,iterations:0,expansionPath:[{sourceOffset:-1,iteration:0}]}}]){
  try { normalize({...sourceTrace,...malformed}); fails++; console.log('FAIL malformed terminal evidence accepted'); } catch { /* Expected boundary rejection. */ }
}
console.log(fails === 0 ? '\nall checks passed' : `\n${fails} FAILED`);
process.exit(fails === 0 ? 0 : 1);
