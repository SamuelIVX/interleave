<!-- Internal94B execution/measurement evidence; public BYTECODE consumers belong to94C. -->
# 94B — Concurrent bytecode execution evidence

## Status and boundaries

Internal implementation under [Spec94B](../specs/active/94B-bytecode-concurrency.md) and the
[shared contract](../plans/bytecode-contract.md). Exact DFS, pure checks, instruction-reference and
conservatively batched execution are implemented. Both modes find genuine lost updates and monitor
wait cycles. Local acceptance, measurement and review gates are complete; PR/CI/manual review remains
the publication boundary. Verification and remaining scope limits are recorded below.
Public library/CLI selection, serialized BYTECODE evidence, strict public replay, reports and viewer
remain **94C**. CBS, DPOR, atomics, bitstate and JPF comparison remain later milestones; no measurement
here establishes their correctness or performance.

Starting merged main: `3c2c799d5d01054706e85bd8cdf89ab2ec2addba` (94A/PR139).
The original dirty checkout was preserved. An earlier temporary worktree/log directory disappeared;
authored source edits were recovered from the session's exact tool history into a persistent worktree,
and the baseline was regenerated. Lost temporary logs are not claimed as present evidence.

## Implementation and independent evidence

`OpcodeExecutor.probe` classifies enabled, blocked and pending choices without state/fuel effects.
`BytecodeScheduler` uses that same primitive executor in both modes, preserving real method/BCI
positions rather than source forward PCs. `BytecodeExplorer` uses iterative exact DFS and full canonical
payload equality after hash filtering, following `HashingStateStore`'s membership policy without
weakening `Configuration` or introducing a public backend. One deliberately colliding terminal-payload
fixture retains both outcomes (65 states,108 edges,109 encoding calls), independently derived by a
seven-stage-per-actor model and Python enumeration. Checker state/key limits and cancellation produce
external incomplete results; the internal default key-payload budget is64MiB, excluding JVM overhead.

`BytecodeChecks` uses separate private frames/fuel and copied read-only model state. ALWAYS checks run
initially and after every reference opcode/event; batched omissions are justified below. FINAL runs
only after all actors terminate normally. False, observer error/limit, actor exception, modeled cutoff,
deadlock and external interruption retain separate internal identities. Check frames never enter model
keys, actor fuel or scheduling choices. Existing loader purity/unknown-metadata rejection remains active.

`BytecodeInstructionOracle` independently enumerates each actor's primitive `step`, uses a sorted set
of its own full structural keys, runs its own private observers and builds its own observations.
It does not use production probes, scheduler, canonical encoder or outcome builder. It intentionally
shares the primitive opcode executor; separate JVM differential cases continue to check that layer.
The oracle's record-text tokens are restricted to these controlled ASCII-named fixtures, not an
alternative encoding for arbitrary application inputs.

Both production modes agree with this oracle on the lost-update/two-lock fixtures, targeted terminal
competition, pure local infinite computation, fuel-sensitive shared branches, exact-limit return,
observer arithmetic failure, nested/reentrant cleanup and eight generated programs from seed94002.
Generated failures undergo statement-deletion shrinking and print the minimized fixture. No generated
failure was found; no fictitious saved counterexample is claimed. Targeted regression sources remain
in the tests and checked-in resource fixtures. Internal witness schedules replay through enabled actual
choices and reproduce owned states; that is not the public strict replay format promised by94C.
Independent concurrent runs share only immutable program metadata.

## Batching preservation argument

This argument is restricted to the loader-admitted94B profile. It does not justify future opcodes,
actor allocations, dispatch, general handlers, intrinsics or DPOR. Tests corroborate it; they are not
its proof.

**State relation.** At a scheduling boundary, relate a batched state to the reference state obtained by
expanding its selected transition into the exact same primitive steps. Heap identities/aliases, fields,
statics, monitor owners/depths, every actor's frames/operand/local slots and BCIs, resource counters,
allocation sequences, sharing history and pending events are identical. Transition ordinals, host
measurement counters and number of selected transitions are excluded. At intermediate local points,
only the selected actor's local continuation, operand/local slots and fuel can differ. No observer may
inspect them. The complete encoder retains those differences rather than merging states across fuel.

**Allowed local steps.** The current scheduler treats every field/static access, every explicit monitor
operation, every return/throw, implicit synchronized entry/exit, exception/unwind event, pending terminal
and actual exhaustion as observable. No heap access is classified actor-local. The remaining admitted
steps are operand/local arithmetic, branches and helper call setup. They neither read nor write shared
model cells nor acquire/release monitors. They change only the selected actor's private continuation
and charged fuel. Another actor's operation therefore preserves their enabledness, value computation
and charged cost, and those local steps preserve that actor's operation and observations. Helper calls
remain interpreted: synchronized entry is a distinct boundary; helper returns, depth stops and faults
use the same event protocol. There is no actor allocation or unbounded host callback in this profile.

**Reordering.** A batch contains at most one observable step, with local prefix/suffix work. Moving other
actors' steps out of those local segments to the preceding/following preserved boundaries commutes by
the prior argument. Heap reads and writes cannot pass each other; in particular increment read/write
opportunities remain distinct. Pending monitor acquisition remains disabled with sufficient fuel and
consumes nothing. Fuel exhaustion takes priority over a blocked acquisition, so it cannot be mislabeled
a deadlock. Calls, cleanup and reentrant monitor depth follow the primitive semantics; implicit release
and explicit cleanup releases cannot be coalesced past another observable operation.

**Errors and bounds.** Every executed opcode charges its actual work, including faulting instructions;
omitted work is uncharged. Normal return on instructionN succeeds. Probing never executes/charges a
candidate. Discovery of a fault, depth stop, return or fuel cutoff ends the current batch in its pending
phase, before terminal delivery. Another actor can therefore run before the prefix ends. Even a batch
containing only local infinite-loop steps terminates at its finite fuel allowance and yields before
cutoff delivery. Moving the other actor to that boundary preserves its competing false check/fault or
shared effect. A cutoff ends the entire selected prefix; no exhausted actor is frozen and no sentinel
is appended. This argument concerns exhaustive bounded runs; external checker interruptions may leave
different partial graphs at the two granularities and never establish absence.

**Observers.** Loader-certified transitive purity excludes writes, allocation, synchronization and
resource/history/actor-stack inspection. Each invocation starts with the same independent allowance,
root call depth and model heap/statics. Omitted local intermediates leave all its possible reads,
branch results and errors unchanged, so both its value and failure identity are invariant. After the
batch's single observable step the resulting shared state is checked before any delivered actor
failure/cutoff. A pending local fault in its suffix cannot suppress that check. Ordered observers and
FINAL timing agree; FINAL requires all actors' normal terminal delivery in either mode. Observer errors
and exhausted observer fuel/depth remain incomplete, distinct from actor violations.

**Sharedness.** Every object in94B is setup-allocated and conservative shared from initialization.
Reference writes refresh transitive fixture/static/frame roots before exposing their successor;
failed writes do not count as publication. Unknown reachability remains shared and promotion never
reverts. All history bits remain in keys. Because actor allocation/exclusive ownership is absent,
reachable runs cannot differ only in historical sharing flags: the measured history-only count is
zero by invariant, not an unperformed state-pair benchmark. Tests mutate the internal seam to confirm
encoding, frame roots and irreversible promotion; they do not claim95's nontrivial heap profile.

**Conclusion and extension obligation.** Expanding batches gives reference executions with the same
normalized terminal observations, and commuting reference local interleavings to retained boundaries
gives matching batched outcomes. Compare violation/fault/check identities, final shared fields, deadlock
relationships and cutoff actor/reason/omitted location/limit; equal traces/local snapshots/counts are
not required. Every terminal category carries the last committed shared observations, including
false checks, actor faults, modeled cutoffs, observer failures, deadlocks and external interruptions;
these are not restricted to normally completed executions. Every newly admitted behavior must re-establish these commutation, observation, resource
and terminal-event premises before batching it. This argument makes no independence claim for DPOR.

## Reproducible measurement conditions

Measured on Apple M4 /16GiB RAM, macOS/aarch64, installed Homebrew OpenJDK26.0.2.1.
Java SHA-256: `38025293a292f9d2161c9c2683191555eabefeb8b987cde9d8c88cafb843d333`;
javac: `a587e4bfa51de17788dfc9de7b22960174f11cb5fed8f21bf2867151eb2524fa`.
Compiler target is `--release26`; existing17/21/26 fixtures use this same installed compiler, not
three independent compiler installations. Run each command separately for fork1 and fork2:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 profileBytecode -PprofileFork=1
./gradlew --max-workers=1 -PpitestThreads=1 profileBytecode -PprofileFork=2
```

Default output: `build/reports/bytecode-profile/fork-N/`; use `-PbytecodeProfileOutput=<directory>`
to retain results outside disposable builds. Each task launches a fresh JVM with512MiB fixed heap,
Serial GC and `-XX:MarkSweepDeadRatio=0`. It compiles each checked-in fixture once, loads it once, checks
outcome equality independently, warms each mode twice and measures three repetitions per fixture/mode.
Mode order alternates each repetition. Both modes use the identical artifact and provisional limits.
Elapsed time surrounds search only; GC, compilation, loading and outcome comparison are outside it.
Encoding nanoseconds are instrumented inside search and included in elapsed time. These short runs are
feasibility measurements, not a broad throughput benchmark; OS/JIT noise is visible in the ranges.

Retained heap is actual used JVM heap after two verified explicit full collections while an explicit
root holds the explorer's full key store and result/witnesses. Delta subtracts the pre-search collected
baseline after releasing the preceding store. It includes live Java collection/array/witness overhead,
not merely encoded payload bytes; it excludes native memory and peak/transient allocation. Earlier
experimental runs without full compaction produced misleading/negative deltas because Serial GC may
retain dead space. A standalone10MB weak-reference probe reproduced this even after its referent was
cleared; setting `MarkSweepDeadRatio=0` reclaimed that space. Those preliminary runs are invalid memory
evidence and are excluded. They motivated the explicit compaction precondition in the runner.

The measurements show stable states/transitions/encoding volume across all six samples per group.
No fixed percentage target was imposed. Both fixtures complete within actor fuel256 (no cutoffs), and
batching reduces encoding/storage work. Time ranges overlap for the tiny lost-update fixture, so do
not infer a universal speedup. Default model limits remain provisional and unchanged; these two tiny
fixtures alone cannot calibrate all admitted programs. Conservative all-shared setup traversal occurs
once per run here, visiting2 objects/0 edges (lost update) or4 objects/2 edges (two locks). Promotions
and history-only states are0. These fixtures perform no actor reference writes; the separate rewiring
test exercises actual publication traversals.95 must remeasure nontrivial sharedness cost.

### Measured results

Six samples per cell: two fresh forks × three repetitions. Time and retained-heap delta use median
[min,max]. State/transition/encoding/payload counts are identical across repetitions.

|Fixture/mode|States|Transitions|Encoding calls/bytes|Key payload bytes|Elapsed ms median [min,max]|Encoding ms median [min,max]|Retained heap delta bytes median [min,max]|
|---|---:|---:|---:|---:|---:|---:|---:|
|lost-update/REFERENCE|140|246|247/118191|66946|2.943 [1.472,4.237]|1.084 [0.442,1.691]|95300 [95080,95648]|
|lost-update/BATCHED|60|98|99/45981|27942|1.964 [0.838,3.194]|0.611 [0.226,1.580]|43096 [43096,43376]|
|two-lock/REFERENCE|475|866|867/704665|385797|3.601 [2.734,4.407]|1.696 [1.386,2.223]|471020 [470984,471944]|
|two-lock/BATCHED|116|196|197/159723|93994|1.174 [0.968,1.484]|0.406 [0.335,0.625]|121664 [121664,121664]|

The lost-update fixture declares FINAL `x==2`: the two outcomes are CHECK_FALSE/x=1 and COMPLETED/x=2.
The two-lock fixture has DEADLOCK/x=0 with both concrete waits, and COMPLETED/x=2. Its FINAL check is
absent on the deadlock prefix. The measurement gate asserts these known outcomes as well as equivalence,
so losing the same outcome in both implementations cannot pass. Observer invocation/work totals are
2/13 for lost update and1/7 for two locks in each mode/run; see raw rows below.

### Artifact identities and raw repetitions

Fork1:

- Java: `26.0.2.1`
- VM: `OpenJDK 64-Bit Server VM`
- OS/architecture: `Mac OS X/aarch64`
- JVM arguments: `[-XX:+UseSerialGC, -XX:MarkSweepDeadRatio=0, -Xms512m, -Xmx512m, -Dfile.encoding=UTF-8, -Duser.country=US, -Duser.language=en, -Duser.variant]`
- Warmups: 2 per fixture/mode; repetitions: 3; modes alternate order per repetition.
- Retained heap: used heap after verified explicit GC and full compaction (MarkSweepDeadRatio=0), with explorer and result reachable; delta against pre-run used heap.
- Key bytes are only canonical payload, reported separately. History-only states are zero in the setup-only all-shared profile.
- lost-update source SHA-256: `1721b4fa9ec2937ac9087fdca03b392d6359f99aced3424f02591522344a2ab2`; artifact manifest: `8c00e19be1d23ccd6ab66d697c827a8c4083737439361768b58b9ac4a0f5745c`; limits: `BytecodeLimits[actorInstructions=256, actorAllocations=128, actorHeapCells=8192, maxCallDepth=32, maxArrayLength=64, maxStringLength=256, setupInstructions=4096, setupAllocations=128, setupHeapCells=8192, checkInstructions=1024]`
- two-lock source SHA-256: `2aa8585b0a91866e84d423e719b3bfe59b3625463d9e0943b3cf6c6a3a12b634`; artifact manifest: `b19cb7d7687fcd44aa045d05177c153032e8673d76fa02653cbe39d17fd052fc`; limits: `BytecodeLimits[actorInstructions=256, actorAllocations=128, actorHeapCells=8192, maxCallDepth=32, maxArrayLength=64, maxStringLength=256, setupInstructions=4096, setupAllocations=128, setupHeapCells=8192, checkInstructions=1024]`

```csv
fixture,fork,repetition,mode,states,transitions,opcodes,instructions,encodingCalls,encodingBytes,encodingNanos,retainedKeyBytes,elapsedNanos,heapBefore,heapAfter,heapDelta,gcCollections,checkInvocations,checkInstructions,sharingTraversals,sharingObjects,sharingEdges,sharingPromotions,historyOnlyStates,outcomes
lost-update,1,0,REFERENCE,140,246,178,178,247,118191,1160295,66946,3527500,7044064,7139464,95400,2,2,13,1,2,0,0,0,2
lost-update,1,0,BATCHED,60,98,102,102,99,45981,1580211,27942,3194125,7044672,7087768,43096,2,2,13,1,2,0,0,0,2
lost-update,1,1,BATCHED,60,98,102,102,99,45981,642377,27942,2069625,7044824,7087920,43096,2,2,13,1,2,0,0,0,2
lost-update,1,1,REFERENCE,140,246,178,178,247,118191,908213,66946,2843959,7044976,7140624,95648,2,2,13,1,2,0,0,0,2
lost-update,1,2,REFERENCE,140,246,178,178,247,118191,1008201,66946,2213625,7045832,7140912,95080,2,2,13,1,2,0,0,0,2
lost-update,1,2,BATCHED,60,98,102,102,99,45981,1157254,27942,2023250,7046120,7089216,43096,2,2,13,1,2,0,0,0,2
two-lock,1,0,REFERENCE,475,866,762,762,867,704665,1712127,385797,3597959,7050128,7521112,470984,2,1,7,1,4,2,0,0,2
two-lock,1,0,BATCHED,116,196,374,374,197,159723,533286,93994,1294416,7050288,7171952,121664,2,1,7,1,4,2,0,0,2
two-lock,1,1,BATCHED,116,196,374,374,197,159723,340495,93994,969041,7050440,7172104,121664,2,1,7,1,4,2,0,0,2
two-lock,1,1,REFERENCE,475,866,762,762,867,704665,1645235,385797,3604792,7050592,7521648,471056,2,1,7,1,4,2,0,0,2
two-lock,1,2,REFERENCE,475,866,762,762,867,704665,1385878,385797,2733916,7050848,7522792,471944,2,1,7,1,4,2,0,0,2
two-lock,1,2,BATCHED,116,196,374,374,197,159723,335462,93994,968375,7051968,7173632,121664,2,1,7,1,4,2,0,0,2
```

Fork2:

- Java: `26.0.2.1`
- VM: `OpenJDK 64-Bit Server VM`
- OS/architecture: `Mac OS X/aarch64`
- JVM arguments: `[-XX:+UseSerialGC, -XX:MarkSweepDeadRatio=0, -Xms512m, -Xmx512m, -Dfile.encoding=UTF-8, -Duser.country=US, -Duser.language=en, -Duser.variant]`
- Warmups: 2 per fixture/mode; repetitions: 3; modes alternate order per repetition.
- Retained heap: used heap after verified explicit GC and full compaction (MarkSweepDeadRatio=0), with explorer and result reachable; delta against pre-run used heap.
- Key bytes are only canonical payload, reported separately. History-only states are zero in the setup-only all-shared profile.
- lost-update source SHA-256: `1721b4fa9ec2937ac9087fdca03b392d6359f99aced3424f02591522344a2ab2`; artifact manifest: `8c00e19be1d23ccd6ab66d697c827a8c4083737439361768b58b9ac4a0f5745c`; limits: `BytecodeLimits[actorInstructions=256, actorAllocations=128, actorHeapCells=8192, maxCallDepth=32, maxArrayLength=64, maxStringLength=256, setupInstructions=4096, setupAllocations=128, setupHeapCells=8192, checkInstructions=1024]`
- two-lock source SHA-256: `2aa8585b0a91866e84d423e719b3bfe59b3625463d9e0943b3cf6c6a3a12b634`; artifact manifest: `b19cb7d7687fcd44aa045d05177c153032e8673d76fa02653cbe39d17fd052fc`; limits: `BytecodeLimits[actorInstructions=256, actorAllocations=128, actorHeapCells=8192, maxCallDepth=32, maxArrayLength=64, maxStringLength=256, setupInstructions=4096, setupAllocations=128, setupHeapCells=8192, checkInstructions=1024]`

```csv
fixture,fork,repetition,mode,states,transitions,opcodes,instructions,encodingCalls,encodingBytes,encodingNanos,retainedKeyBytes,elapsedNanos,heapBefore,heapAfter,heapDelta,gcCollections,checkInvocations,checkInstructions,sharingTraversals,sharingObjects,sharingEdges,sharingPromotions,historyOnlyStates,outcomes
lost-update,2,0,REFERENCE,140,246,178,178,247,118191,1691248,66946,4236875,7044048,7139488,95440,2,2,13,1,2,0,0,0,2
lost-update,2,0,BATCHED,60,98,102,102,99,45981,397912,27942,1749583,7044696,7087976,43280,2,2,13,1,2,0,0,0,2
lost-update,2,1,BATCHED,60,98,102,102,99,45981,579540,27942,1904708,7045032,7088408,43376,2,2,13,1,2,0,0,0,2
lost-update,2,1,REFERENCE,140,246,178,178,247,118191,1543113,66946,3041958,7045464,7140664,95200,2,2,13,1,2,0,0,0,2
lost-update,2,2,REFERENCE,140,246,178,178,247,118191,442467,66946,1471500,7045872,7140952,95080,2,2,13,1,2,0,0,0,2
lost-update,2,2,BATCHED,60,98,102,102,99,45981,225998,27942,837916,7046160,7089256,43096,2,2,13,1,2,0,0,0,2
two-lock,2,0,REFERENCE,475,866,762,762,867,704665,1679261,385797,3597625,7050200,7521184,470984,2,1,7,1,4,2,0,0,2
two-lock,2,0,BATCHED,116,196,374,374,197,159723,624669,93994,1484208,7050360,7172024,121664,2,1,7,1,4,2,0,0,2
two-lock,2,1,BATCHED,116,196,374,374,197,159723,345708,93994,1054500,7050512,7172176,121664,2,1,7,1,4,2,0,0,2
two-lock,2,1,REFERENCE,475,866,762,762,867,704665,1774319,385797,3805709,7050664,7521648,470984,2,1,7,1,4,2,0,0,2
two-lock,2,2,REFERENCE,475,866,762,762,867,704665,2222790,385797,4406875,7050848,7522784,471936,2,1,7,1,4,2,0,0,2
two-lock,2,2,BATCHED,116,196,374,374,197,159723,466198,93994,1312125,7051960,7173624,121664,2,1,7,1,4,2,0,0,2
```

## Validation and review ledger

Fresh untouched-main baseline:2020 tests, zero failures/errors/skips; full build/strict Javadoc passes.
Configured default state/cb PIT:455 KILLED +13 SURVIVED /468. Affected bytecode package baseline:
1344 KILLED +171 SURVIVED +105 NO_COVERAGE /1620. These are regenerated reports on the actual merged
starting SHA, not carried-forward94A authoring counts. The default scope is not the whole checker.

Integrated final build/strict Javadoc:2066 tests, zero failures/errors/skips. Private bytecode and mutation-harness
member doclint passes with all groups and warnings as errors. The legacy browser suite passes the
normalization assertions plus248 browser checks against all freshly generated CI fixture families, with zero console errors; no new bytecode viewer path
is claimed. The actual library jar excludes test annotations, profiler/probe classes, the mutation
agent and the public bytecode facade. Legacy public/source/declarative consumers remain unchanged.

The first affected run reported1677 KILLED +223 SURVIVED +111 NO_COVERAGE +3 TIMED_OUT /2014 and
**failed** the R6 mutation gate. The timeouts were DFS `ArrayDeque.pop` removal and observer root-return
condition/size removal. They are not kills. The existing test-only instrumentation agent now installs
`BytecodeWorkBudget`, which derives9S maximum DFS loop visits for at mostS admitted frames (8 actor
selections +1 pop per frame), saturating at Long.MAX_VALUE. Each pure observer gets at most2N+2 loop
visits for N opcode work, helper-return events and final/cutoff views; the allowance resets per check.
The later [mutation-stability prerequisite](bytecode-mutation-progress.md) tightens the DFS assertion
to actual admissions at every prefix and documents zero-deadline test-input normalization after
Spec102 calibration exposed unlimited-budget/test-order and clock-boundary gaps. The historical
counts and completion evidence in this94B ledger remain observations of their original profile.
These counters do not change production code, target fuel or flags and never ship in the library.
Real-definition child-JVM tests preserve original outcomes, reject binding/checkpoint drift and turn
actual stalled mutations into AssertionErrors. The refreshed whole-bytecode-package census passed with1718 KILLED +194 SURVIVED +102 NO_COVERAGE
/2014, zero timeouts/run errors. Later focused assertions cover static publication/promotion and an
actually unreachable setup object (both local and operand roots removed). Their final focused census
is107 KILLED +13 SURVIVED /120, zero uncovered/resource/run-error statuses. Scheduler including its
transition record is71/71; sharing including Work is36/49, with13 survivors. No production bytecode
changed after the whole-package census; focused test patches were followed by integrated verification.
These are scoped bytecode results, not a whole-checker mutation score.
No timeout thresholds, operators, floors, mutation target tests or CI parallelism were weakened.

Actual CodeRabbit0.8.1 reviewed all21 then-changed files and completed with6 findings (2 major,
2 minor,2 trivial). Dispositions: batch cancellation now checks each opcode boundary and Explorer
records external CANCELLED with no scratch commit; exact-state budget enforcement follows duplicate
membership (regression140 states/246 edges); observer fuel resets have repeated successful ALWAYS
coverage; the independent oracle derives fuel/monitor enabledness without probe and verifies `step`;
concurrent test futures are cancelled in finally; known completion/deadlock/lost-update observations
are asserted before measurement equality. The return-cleanup test correctly requires the faulting
actor's depths to be released; a competing actor may acquire before failure delivery. Follow-up
CodeRabbit reviewed all24 files, including the added PIT-only progress harness, and completed with
3 findings (2 major,1 minor). The major findings requested verification, rather than demonstrating
counterexamples: `step` already copies the complete owned state, and `probe` already treats GETSTATIC/
PUTSTATIC as FieldInstructions. Added checks preserve static x=1/x=2 outcomes and both modes agree with
independent instruction enumeration. Deterministic test-only class-file injection interrupts immediately
after a real local opcode, shared write or implicit synchronized entry, proving the parent unchanged;
it also verifies Explorer's external CANCELLED prefix. This replaces a timing-dependent stack-sampling
worker test. No callback/pause hook was added to production. Those focused test patches were reviewed
locally; no third CLI loop was run after the two completed substantive reviews.

A context-free doc-coauthoring reader answered all five scope/preservation/oracle/measurement/history
questions and found no blocking premise gap in the restricted argument. Its one ambiguity—whether
abnormal terminals carry shared observations—was clarified above. This was a document review, not a
source or runtime certification. Connected-source ordinary/ponytail and OWASP review checked owned
state, exact membership, modeled/external terminal distinctions, transitive check purity, metadata-only
target boundaries and host test instrumentation. No unresolved correctness/security finding remains
within94B. Actual ordinary and CodeRabbit evidence is recorded through agent-setup for this session;
focused review-test/status patches used local verification rather than an unbounded third CLI loop.
NO_COVERAGE, timeout and run errors are never counted as kills. No JPF experiment was performed here.

### Mutation reconciliation and residuals

Configured default state/cb:455 KILLED +13 SURVIVED /468; **all468 baseline fingerprints and statuses
match**, including every455 inherited kill. Fingerprints use class/method/descriptor/operator plus
instruction indexes/blocks; line-number shifts are not identities. This default is not the whole checker.
For affected bytecode, all1274 baseline kills in unchanged methods remain killed. The changed primitive
`step` is not compared by coincidentally overlapping instruction indexes; its semantics are covered by
JVM differential, exact-limit/fault/monitor and independent scheduling cases. Among unchanged methods,
9 formerly uncovered and4 formerly surviving mutants are now killed.

The table below composes the whole-package report with the later Scheduler/Sharing report **by class**.
Its1724 KILLED +190 SURVIVED +100 NO_COVERAGE /2014 is a latest-per-class sum, not a single full-scope
PIT invocation. The separate actual invocation totals are stated above. Nested types are included.

|Affected class/group|KILLED|SURVIVED|NO_COVERAGE|
|---|---:|---:|---:|
|BytecodeChecks|41|1|0|
|BytecodeExplorer|170|7|4|
|BytecodeFrame|15|0|0|
|BytecodeInput|108|10|16|
|BytecodeLimits|7|0|0|
|BytecodeLoadException|0|2|0|
|BytecodeLoader|484|72|57|
|BytecodeMethod|34|3|0|
|BytecodeProgram|23|2|0|
|BytecodeScheduler|71|0|0|
|BytecodeSharing|36|13|0|
|BytecodeState|185|13|0|
|BytecodeVerifier|239|59|21|
|OpcodeExecutor|311|8|2|

All residuals remain in raw XML; none are excluded or declared generally equivalent. Most inherited
residuals concern overlapping input/JDK validation, diagnostics and defensive checks. New residuals
include redundant exact-hash/terminal guards, timing-sensitive cancellation paths and the observer's
partial-cancellation accounting. The13 sharing survivors affect redundant/empty/null roots or traversal
in the conservative setup-only heap;95 must revisit them when actual object graphs/local ownership
arrive. Zero history-only metrics do not prove these mutations equivalent for that wider profile.
The mutation ratchet/operators/floors and target-test policy remain unchanged.

### Reproduction and retained evidence

```sh
./gradlew --max-workers=1 -PpitestThreads=1 build javadoc
./gradlew --max-workers=1 -PpitestThreads=1 pitest
./gradlew --max-workers=1 -PpitestThreads=1 pitest '-PpitestTargetOverride=dev.samhb.interleave.bytecode.*'
./gradlew --max-workers=1 -PpitestThreads=1 pitest '-PpitestTargetOverride=dev.samhb.interleave.bytecode.BytecodeScheduler*,dev.samhb.interleave.bytecode.BytecodeSharing*'
```

Independent private doclint runs use `javadoc -private -Xdoclint:all -Werror` over bytecode and the
separate mutation-harness source tree. Browser verification uses all environment paths listed in the
existing Visualizer compatibility CI job after normal tests generate fresh artifacts, before PIT can
replace them. Context-store structural validation has0 errors and1 inherited machine-local link warning.
Documentation validation passes5 changed Markdown documents and all local targets/anchors; R1–R6
mappings, code fences and whitespace checks pass.

Local raw evidence lives in `Documents/repos/worktrees/interleave-94b-evidence` under Sam's home:
`baseline-{build,default,affected}` logs/XML; `publication-build.log`; `verified-{build-and-fork1,fork2}`
logs/directories; `complete-browser.log`; `final-default.log/.xml`; `verified-affected.log/.xml`;
`final-focused.log/.xml`; `compare-mutations.py`; strict/private doclint logs;
`coderabbit.ndjson` and `coderabbit-followup.ndjson`; failing/passing targeted regression logs.
Committed raw interpreter repetitions above make the measurement result independent of that local path.
