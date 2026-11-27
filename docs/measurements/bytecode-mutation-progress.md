<!-- PIT-only prerequisite: preserve the strict failure gates while removing test-order/clock masking. -->
# Bytecode mutation stability prerequisite

## Finding and scope

CI for [PR145](https://github.com/SamuelIVX/interleave/pull/145) exposed two existing weaknesses in the
bytecode mutation harness. The correction changes the PIT-only transformer and its child-JVM probes.
All312 compiled production classes remain byte-identical to the previous local checkout; the library
JAR still excludes the agent. Production fuel, limits, execution, replay and consumers are unchanged.

The fix starts from freshly fetched main `555517730eb1877de57c2d9d1de740e591d6272c` in isolated
`interleave-bytecode-mutation-progress`, branch `fix/bytecode-mutation-progress`. PR145 stays at
`b2512ce`; its worktree, the original dirty checkout and unfinished Spec97 remain untouched.

## Actual CI evidence

The [completed run45903258979](https://github.com/SamuelIVX/interleave/actions/runs/45903258979) used
base `5555177`, merge candidate `df5d32b5a11f4c0034962ad29346f55f98d10073` and head `b2512ce`.
Build, default Mutation and Visualizer checks passed. Both full affected PIT jobs succeeded, while
candidate shard2 failed in both calibration trials. The summary correctly rejected failed dependencies;
its subsequent missing validated-artifact error is secondary. Raw worker artifacts were retained.

All reports were downloaded before editing the harness. Both revisions have identical recorded
protocols, full runtime inputs, compiled production/test classes and harness bytes. These are actual
old-profile CI results, not a new-profile acceptance claim or whole-project mutation score:

| Affected population | KILLED | SURVIVED | NO_COVERAGE | TIMED_OUT | Total |
|---|---:|---:|---:|---:|---:|
| Baseline full | 5709 | 781 | 604 | 0 | 7094 |
| Candidate full | 5708 | 782 | 604 | 0 | 7094 |
| Candidate calibration1 union, diagnostic only | 5707 | 782 | 604 | 1 | 7094 |
| Candidate calibration2 union, diagnostic only | 5707 | 782 | 604 | 1 | 7094 |

Each calibration union differs from its candidate reference in exactly one raw identity:
`BytecodeExplorer.explore(Budget)Result`, nonvoid-call removal, index110/block16, removing
`ArrayDeque.pop`. Both failed trials selected
`BytecodeReplayTest.fabricatedEmptyTerminalsAndTrailingChoicesReject` first, using an unlimited
public state budget. The successful full run selected the bounded
`BytecodeSearchContractTest.hashCollisionsStillCompareCompletePayloads` first.

Baseline versus candidate full differs in one additional identity: `BytecodeExplorer.stopReason()String`,
conditional-boundary mutation, index22/block7. The baseline reports KILLED by
`BytecodeReleaseTest.bytecodeRunnerRejectsForeignCallbacksAndStrategies`; the candidate reports
SURVIVED. That test exercises a zero-duration checker deadline. The real elapsed milliseconds
sometimes equal zero and sometimes exceed it, masking the `>=` to `>` defect.

Timeouts remain non-kills and both calibrations remain failed. No rows were replaced or promoted into
an acceptance aggregate. Raw reports, records and the diagnostic comparison live in sibling
`interleave-102-evidence`: `pr145-ci-*`, `pr145-ci-outcome-diagnosis.json` and
`pr145-ci-input-diagnosis.json`.

## Prefix-work preservation argument

Let S be states actually admitted so far. Every pushed DFS frame follows an admission, so the number
of created frames is at most S. Each frame selects at most8 fixed actors and is popped once. Thus,
at every execution prefix, loop checkpoint visits are at most9S. Terminal or duplicate successors
create no frame; external interruption can only shorten the prefix.

The added assertion counts visits locally and compares against the saturated9S derived from the
checker's actual `states` field at each checkpoint. The original saturated9B caller-budget assertion
is retained independently. It catches a mutant that admits more states than B even when its frame
progress remains otherwise valid. That original assertion also trips before the new work counter
could overflow; multiplication retains the existing saturation policy.

No valid original search exceeds the prefix bound. After a removed pop stalls an exhausted frame,
S stops growing and checkpoint work crosses9S, producing the existing logical AssertionError.
The argument uses neither wall time nor modeled instruction fuel. It applies to the current exact
DFS frame ownership; future search implementations must re-establish the invariant. Observer and
loader assertions retain their existing bounds. Binding to the real instance counter is checked
before transformation.

## Zero-deadline test input

The specified zero-duration result is MAX_TIME before admitting any state. The test agent controls
that exact equality input: at the deadline clock checkpoint, when `maxTimeMillis == 0`, it records
the just-read clock value as the origin. The original subtraction therefore yields zero. It leaves
the original comparison untouched and applies identically to original and mutant definitions.

The original `elapsed >= 0` still stops immediately with zero states; the faulty `elapsed > 0`
does not. Existing result assertions can now distinguish them without depending on which side of
a millisecond tick the covering test runs. The transformation does not inspect the comparison to
decide which definition is a mutant. Untimed and positive deadlines retain their host-clock inputs;
missing clock calls in single call-removal mutants remain permitted and expose their own faults.

This is a test-input normalization for the zero-duration contract. It does not claim reproducibility
under arbitrary host-clock adjustments or exact positive-deadline timing. It adds no production clock
hook, agent runtime dependency, model instruction or target-state mutation. The transformer still
accepts only its named host checker classes; target application bytecode is never host-executed.

## Verification

- The unlimited-pop child regression failed against the old agent by exhausting its15-second child
  safety wait. With the corrected agent it returns the logical `BOUND_EXCEEDED` result.
- A fixed past clock origin makes the old boundary mutant consistently appear to enforce the deadline.
  The archived-agent regression returned `ZERO_DEADLINE` instead of `DEADLINE_MISSED`; the corrected
  agent exposes the violation. Initial archived-agent testing was shadowed by current harness classes
  earlier on the child classpath; placing the original archive first made that red comparison valid.
- Real original definitions preserve normal lost-update/completion outcomes, unlimited-budget
  completion, cancellation ownership and zero/positive deadline interruption. Clock probes verify
  zero admitted states and the MAX_TIME reason. Field/method/checkpoint drift fails closed; actual
  single-call removals remain valid transformed definitions.
- The final integrated build passed all2176 tests with zero failures, errors or skips in64seconds,
  including the caller-budget preservation regression. Public Javadoc and private harness doclint
  pass all groups with warnings as errors.
- The original and transformed definitions pass the JDK26 class-file verifier. Production class
  hashes and library packaging are independently checked. No local PIT or browser rerun was used.

Reproduce the small harness suite with both required local limits:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 test --tests dev.samhb.mutationharness.BytecodeWorkBudgetTest
```

The child timeout is process safety and children are always reaped; it is not the green progress
oracle or mutation credit. The agent's finite logical assertion supplies that oracle. These manual
mutant probes are not fresh PIT counts.

Actual CodeRabbit0.8.1 completed the six-file review with zero findings. Independent follow-up review
identified that the old configured-budget assertion should remain alongside the new prefix check.
A real state-budget boundary mutant with internal budget0 reproduced that gap: the prefix-only
agent reported `STATE_LIMIT_MISSED` instead of `BOUND_EXCEEDED`. Both assertions are now retained.
The corresponding new regression also verifies original zero-budget interruption and the mutant's
observable state-limit violation without instrumentation. Review-record evidence cites the completed CLI scope and this locally verified preservation
follow-up separately.

## Merge ordering and remaining gates

Merge this small prerequisite into main before resuming PR145. Its paired analysis deliberately
requires the same harness protocol on baseline and candidate. Adding this agent change only to
PR145 would make those fingerprints differ. The separate prerequisite preserves that requirement
and adds one fix PR beyond the previously planned specification/implementation pair.

After Sam merges the prerequisite, integrate the updated main into PR145 and run fresh paired
acceptance/calibration under the new common harness. The old results above cannot establish that
profile's acceptance. Keep full mode and all failure gates; do not raise timeouts, narrow tests,
change operators/floors, waive the timing-sensitive inherited kill, or enable sharding on failed
calibration. No speedup, retained-memory improvement or completed calibration is claimed.

After publishing this prerequisite PR, report its link and stop. Sam supplies review, CI and merge
status; no watcher or later milestone starts automatically.
