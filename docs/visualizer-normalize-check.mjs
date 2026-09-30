// Minimal assertion script for normalize(). Not a test suite: no runner, no CI wiring, no
// coverage of the rendering code. It exists because the INCOMPLETE branch it guards is
// security-sensitive (it treats all input as untrusted) and would otherwise have no automated
// guard at all.
//
// Run: node docs/visualizer-normalize-check.mjs
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const read = (rel) => readFileSync(join(root, rel), 'utf8');
const src = read('docs/visualizer.js');
// Extract normalize() plus the constant it needs. A guard is required rather than a bare slice:
// indexOf returns -1 on a rename or reformat, and slice(-1, ...) would then yield garbage and
// report it as a per-case failure instead of naming the real cause.
const start = src.indexOf('function normalize(');
const endMarker = start < 0 ? -1 : src.indexOf('\n}\n', start);
if (start < 0 || endMarker < 0) {
  console.error('Cannot locate normalize() in docs/visualizer.js — extraction aborted.');
  process.exit(2);
}
const end = endMarker + 3;
const MAX_NAME_LEN = 200;
const normalize = new Function('MAX_NAME_LEN', src.slice(start, end) + '\nreturn normalize;')(MAX_NAME_LEN);

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
console.log(fails === 0 ? '\nall checks passed' : `\n${fails} FAILED`);
process.exit(fails === 0 ? 0 : 1);
