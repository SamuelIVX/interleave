# Final source-property reduction: dispatch and correctness evidence

## Result and scope

On one independently specified final-postcondition model, actual CLI static-POR accepted visits
fall from 18 to 6 and DPOR from 18 to 17. DFS remains 18 and CBS 38. Every exact strategy reports
PASS and the JSON report's attestation remains soundness=true. These are search-visit counts,
not wall-time measurements, unique-state counts for arbitrary DPOR workloads, or universal speedups.

Baseline production/source tree: merged PR #111 at 7bc8ee0. Baseline CLI ran from clean 78d87bb,
whose complete tree matches that merge. After-change CLI ran from 7bc8ee0 plus the uncommitted
Spec 70 patch. The same source bytes and exact store were used in both. Java 26/Gradle settings
and local one-worker/PIT-thread flags were unchanged. Source text and literal expectations follow.

```text
program final_reduction {
  shared int x = 0;
  shared int y = 0;
  shared int z = 0;
  thread 0 { atomic { x = 1; } }
  thread 1 { atomic { y = 1; } atomic { y = 2; } }
  thread 2 { atomic { z = 1; } atomic { z = 2; } }
  invariant final (x == 1);
  expected PASS;
}
```

Save that exact text as `/private/tmp/interleave-final-property-model.ilv`, then run at each revision:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 -q run --args='--source /private/tmp/interleave-final-property-model.ilv --store EXACT --json'
```

The raw JSON files are local `/private/tmp/interleave-final-property-cli-{before,after}.json`.
Their timing/heap fields were not used as benchmarks. The checked dispatch fields are:

| Strategy | Before accepted visits | After accepted visits | Exact verdict |
|---|---:|---:|---|
| DFS | 18 | 18 | PASS |
| STATIC_POR | 18 | 6 | PASS |
| DPOR | 18 | 17 | PASS |
| CONTEXT_BOUNDED (K=2) | 38 | 38 | PASS |

The independent PC product has 2*3*3=18 positions, cross-checked by Python Cartesian enumeration.
The source has five actions; a serial representative plus the initial position has six positions,
also enumerated as the literal schedule 1,1,2,2,0. DPOR's 17 and CBS's 38 are observed implementation
counters, not independently asserted theoretical counts. Opaque wrappers retain 18 visits for both
POR/DPOR. The typed legacy final-metadata fixture retains all nine positions in its independent
three-by-three counter product; the new source contract does not enable legacy reduction.

## Terminal and contract oracles

Nineteen new tests separate state-only versus final metadata, immutable array/index/error reads,
constant final true/false/error predicates, a root-time modulo error that becomes safe at completion,
last-writer final success/failure, final array/modulo errors, deadlock/cutoff without false property
observation, forward branch/repeat outcomes, all three bounded-loop terminal classes, minimization,
unknown configuration callbacks and conservative legacy dispatch. All source evidence replays with
its actual property; existing example/report/attestation tests also pass.

Independent last-writer orders produce observed values {1,2}; forward branch choice produces {2,4}.
An independent finite loop-state enumerator and hand schedules agree on COMPLETED, VIOLATION and
INCOMPLETE for the competing-reset loop. Failure evidence retains existing phase/category/source
semantics. This supplements the [Spec 70](../specs/active/70-final-source-property-reduction.md)
completion-gate/commutation argument; agreement between explorers alone is not the proof.

## Verification and limits

Fresh baseline: 1,380 tests, clean build/strict Javadoc and default 283/293 K + 10 S pass. Final:
1,399 tests/build/strict Javadoc pass. All 293 default identities/statuses remain exactly unchanged,
zero uncovered/resource/run errors, full R5/R6/R7 pass; 94% floor and 293 denominator unchanged.
Separate SourceProperty scope: 13/13 baseline to 17/17 final, retaining every old identity/kill with
four new final-metadata kills; scoped R6 passes, R5/R7 skip. Never add these separate scopes.

Unknown/termination-sensitive callbacks without the explicit contract and legacy declarative final
properties remain exhaustive. Predicate purity, complete step/expression footprints, exact storage,
faithful state identity and finite forward control flow remain assumptions. No source syntax,
pruning rule, sleep representation, trace schema or browser rendering changed; no mechanical proof
or broader liveness/weak-memory claim is made.

All 18 visualizer normalization checks and changed Markdown table/link/diff checks pass.
CodeRabbit CLI 0.8.1 completed all 13 changed files with one minor verification-status finding.
The final ledger now records actual results; no CLI repeat for this minor documentation patch.
Browser rendering tests were not rerun because this change preserves the JSON/trace schema and
renderer. Remote CI is not monitored here.
