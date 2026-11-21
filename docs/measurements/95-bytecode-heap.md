<!-- Spec95 implementation evidence; historical baselines, measured results and pending gates remain separate. -->
# 95 — Objects, arrays and dispatch

## Status and reproducibility

Implementation is based on freshly fetched merged main `5385a3c` (PR141/94C).
Local acceptance results and remaining publication gates are recorded below. Public semantics are defined by
[Spec95](../specs/active/95-bytecode-heap.md) and the [shared contract](../plans/bytecode-contract.md).
No96+ behavior, bytecode reduction or JPF compatibility is claimed.

Baseline is an untouched detached checkout, sibling `interleave-95-baseline`. Mutation XML, final
fork data and browser logs are retained in sibling `interleave-95-evidence`. Earlier temporary
review/build logs are no longer available; their inspected results are recorded below.
The original dirty Interleave checkout is preserved. All Gradle commands use one worker/PIT thread;
CI parallelism, mutation operators/floors, resource settings and tests remain enabled.

## Interfaces and profile

The existing public loader, limits, runner and replay interfaces remain the seams. No new strategy,
CLI selector or host execution route is introduced. `BYTECODE-SC-2` authenticates the expanded language
and canonical encoding v2. SC-1 evidence remains viewable, but this SC-2 runtime rejects it for strict
replay instead of silently assigning new semantics. Source/JSON model formats and source continuations
are unchanged; public evidence record shapes/native serialization fields are unchanged.

The metadata-only loader closes constructors, signatures, field types, hierarchy, nest hosts and all
reachable dispatch bodies. It rescans role/method obligations when the discovered class set grows.
Unreferenced directory classes cannot enter execution: all instantiation sites and supported reference
sources belong to this frozen closure. There is no runtime classpath lookup. Reachable bridge methods
remain rejected because no replacement for the JDK verifier's bridge-method gap is claimed.

Field lookup preserves declaring owner/name/descriptor identities. Virtual lookup respects class
methods and maximally specific interface defaults; special calls retain constructor/super semantics.
Reciprocal same-package nest membership, sealed permitted edges in the single unnamed module,
and member/staticness/access rules are checked.
Resolution and runtime selection are distinct, following the admitted cases of
[JVMS5.4.3–5.4.6](https://docs.oracle.com/en/java/javase/26/docs/specs/jvms/jvms-5.html#jvms-5.4.6);
separate JVM cases distinguish class-typed resolution failures from interface default selection. After JDK
verification, protected-receiver analysis uses verified expanded stack-map types and straight-line
category-one transport, supplementing metadata visibility without interpreting target code on the host.

Heap state includes exact runtime class/array descriptors, owned elements, aliases, initialization,
monitor owner/depth, permanent per-owner counters/sequences and irreversible sharedness. Snapshots
flatten array elements as typed identity/index cells and retain length/type. Wide arrays carry default
zero LONG/FLOAT_BITS/DOUBLE_BITS data only; their element computation/load/store remains excluded.
Array cloning belongs to97; executable initializers, general handlers and unsupported libraries still reject.

## Requirements and independent checks

| Contract | Connected delivery and acceptance |
|---|---|
| R1 | Actor application/Object construction, inherited/hidden fields, constructor chaining, interface/default/abstract/virtual/special/private-nest dispatch; JVM differential compiler releases17/21/26; all alternatives checked for unsupported behavior and observer impurity; missing metadata and forged protected receiver reject before search |
| R2 | Primitive/reference/ranked arrays, narrowing, zero defaults and aliases; null/bounds/negative-size/store/cast faults compared with an independent JVM, including monitor cleanup |
| R3 | Actor-local cyclic graphs and operand/callee roots promote transitively at publication; sharing survives unlinking; reference and batching match independent instruction enumeration for arrays and crossed publication |
| R4 | Namespace-local allocation sequences survive reversed schedules and publication; count/cell/length exact boundaries, failed allocation with no identity, partial multidimensional allocation with earlier identities/charges retained |
| R5 | Public JSON/native result transport, strict replay, built-jar library/CLI, reports, viewer and HeapPublication example; old lost-update/deadlock consumers remain exercised |

`BytecodeHeapTest`, `BytecodeDispatchTest`, `BytecodePublicationTest`, `BytecodeHeapReleaseTest` and
`BytecodeHeapMeasurementTest` exercise the existing approved seams. Existing admission cases for newly
supported behavior migrate to positive assertions rather than disappearing: actor Object allocation,
arrays, constructor chaining, instance helper checks and interface fields. Unsupported narrow field/
method descriptors, executable initialization, general handlers, wider computation and host calls still reject.

Fixed seed `0x952026` generates12 bounded sequential heap/cast/dispatch fixtures over releases17/21/26,
compares a separate JVM and saves deletion-shrunk failures. Existing arithmetic generation/shrinking
and saved regressions remain active. Test-only oracle processes are not a production fallback.

The initial heap-generator attempt emitted `read()--2`; parenthesizing negative operands fixes the
source generator. Its PIT attempt stopped during initial unmutated coverage and produced no census;
that failure is not counted as mutation evidence. The corrected generated suite passes.

## Batching preservation argument

Assumptions: admitted verified category-one code, fixed actors, bounded owned heap, no host callbacks,
no general handlers, pure observers, SC visible accesses and identical selected limits. Relate reference
and batched states by exact heap/alias/static/frame/resource/BCI/monitor/history values at matched
boundaries, permitting a finite stutter of one actor's local arithmetic/transport between boundaries.

All field and array accesses, allocations, monitor operations, returns and fault/terminal delivery remain
conservatively observable boundaries, including actor-local heap operations. No locality optimization
is inferred merely from the allocator or fuel. A shared increment retains distinct load/store choices.
Every batch executes the same fueled opcode primitive and preserves each actual operation/BCI.
Allocation (including partial dimensions) is an explicit boundary; the component sequence is one JVM
opcode, with deterministic depth-first identity/charge order and no interleaving inside that opcode.

An actor-created reference starts solely on its allocating actor's stack. Reachability refresh includes
fixture/statics, historical shared roots and every actor's locals/operands/return/monitor roots, traversing
field and array edges. Only proven exclusive actor reachability permits false sharedness. Unknown roots
promote, and true is never reset. Publication and its graph promotion commit in the same selected
transition before another actor/check can observe the escape. Losing a local root cannot create a new
cross-actor path; a future reachable alias must come through an already traced edge. Canonical keys
retain history even if public roots disappear. Counters permanently belong to the allocator.

Omitted local steps cannot change any supported pure check's value/error: observers cannot reach local
frames or private graphs, allocate/mutate, or inspect budgets/history. Observer instruction/depth allowances
reset per invocation; identical visible inputs and call graph therefore preserve observer fuel/depth/error
behavior as well as values. Extra reference-mode observations of the same input cannot introduce a
new per-invocation limit failure; no aggregate observer-fuel bound is claimed. Dynamic faults and resource
cutoffs leave pending events and yield before terminal delivery; probes charge no work or target effects.
Thus batching cannot consume an uncaught terminal and suppress a competing actor's established violation.
Normal returns on the exact last instruction still succeed. Fault cleanup/exception events remain explicit.

Match observable operations and terminal outcomes by induction over selected boundaries, commuting only
unobserved local stutters of different actors. The relation preserves enabledness, prior committed effects,
check outcomes/errors, real failure sites and cutoff actor/reason/location/limit. It does not require equal
trace length, transition counts or intermediate local snapshots. Independent instruction enumeration and
adversarial tests challenge this argument; they are not a substitute for it. No DPOR preservation claim.

## Fresh baseline

- Fresh merged-main build/tests and strict Javadoc passed before production edits.
- Default PIT:455 KILLED,13 SURVIVED,0 NO_COVERAGE /468.
- Affected census:4024 KILLED,413 SURVIVED,556 NO_COVERAGE /4993; completed without resource/run errors.
- These are newly measured at5385a3c, not imported94C ledger totals.

Default is the existing state/cb gate, not whole-project mutation coverage. Every PIT run retains
`targetTests=dev.samhb.interleave.*`; only production mutation targets are scoped. The baseline and
feature affected scope use the following exact command:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 build javadoc
./gradlew --max-workers=1 -PpitestThreads=1 pitest
./gradlew --max-workers=1 -PpitestThreads=1 pitest '-PpitestTargetOverride=dev.samhb.interleave.bytecode.*,dev.samhb.interleave.BytecodeTraceJson*,dev.samhb.interleave.ResultJson*,dev.samhb.interleave.core.BytecodeEvidence*,dev.samhb.interleave.InterleaveRunner*,dev.samhb.interleave.TestResult*,dev.samhb.interleave.TraceRecord*,dev.samhb.interleave.search.TraceReplayer*,dev.samhb.interleave.report.BenchmarkHarness*,dev.samhb.interleave.report.BenchmarkResult*,dev.samhb.interleave.report.ReportWriter*,dev.samhb.interleave.report.SoundnessAttestation*,dev.samhb.interleave.cli.Main*'
```

Each completed XML is archived before the next PIT run. NO_COVERAGE and resource detections
are never counted as kills. Final scoped results and inherited comparisons are recorded below.

## Dynamic interface receiver fault

JVM verification permits some interface receiver flows that still require a dynamic receiver check.
A saved adversarial transformation replaces a null interface receiver with a freshly initialized plain
Object. Loading succeeds on both the Class-File verifier and JVM; the independent JVM throws
IncompatibleClassChangeError. The earlier interpreter leaked a host NullPointerException during
missing-target lookup. It now checks assignability before dispatch/depth and models the genuine VM
fault through the same fueled unwind/terminal protocol. This is a95 supported-instruction fault,
not admission of arbitrary linkage repair, user handlers or host fallback.

## Dynamic heap measurements

Correctness gate: both checked-in heap fixtures must have identical normalized independent instruction,
REFERENCE and BATCHED outcome sets before timing. Fixture sources are under
`src/test/resources/bytecode/heap/{cyclic-publication,aliased-array}`. Measurements use the same source,
artifact and limits in both modes, JDK26, fixed512MiB heap, compacting SerialGC, two warmups and three
alternating-mode repetitions per fixture/mode in each of three fresh JVM forks.

Retained memory is post-compaction used heap with actual explorer/store/witnesses reachable, minus
pre-run compacted heap, measured outside the execution stack. It is not allocation rate/key bytes.
History-only states are counted in a separate production-granularity enumeration checked against actual
unique-state counts, erasing only sharing flags in comparison copies. Its extra work is outside timing.
Sharing work counts real object/edge visits per root traversal, including repeat visits from actor roots;
it is not directly interchangeable with94B's old all-roots-unioned traversal counts.

```sh
./gradlew --max-workers=1 -PpitestThreads=1 test --tests '*BytecodeHeapMeasurementTest'
./gradlew --max-workers=1 -PpitestThreads=1 profileBytecodeHeap -PprofileFork=1 -PbytecodeProfileOutput=/path/to/evidence/fork-1
# Repeat with profileFork=2/3 and distinct fork-2/fork-3 output directories.
```

Raw `samples.csv` and `conditions.md` record Java/OS/flags, exact source/artifact digests and limits.
Superseded allocation/dispatch samples are archived under `early-forks`; samples predating original
constructor-receiver encoding are under `pre-constructor-measurements`. Neither set is final evidence.
The final six forks are collected after mutation checks, without concurrent PIT work.

## Review and final gates

Ordinary connected-source/security review found a missing symbolic class-visibility check on array
allocation/type-check instructions. An isolated malformed array regression failed before the fix;
array/cast variants pass after the fix. Trusted final Object member overrides now produce an early
LINKAGE diagnostic; the earlier test rejected as UNSUPPORTED because its call referenced Object.
These refinements are verified in the isolated validation checkout while the original compiled
feature census remains unchanged. Final mutation evidence must explicitly replace the loader class
observations after its refinement, rather than pretending the earlier snapshot contains that fix.

CodeRabbit CLI0.8.1 was invoked on the complete staged implementation. Its first invocation ended
with `Connection failed: WebSocket closed`, without findings/completion evidence. The same-scope host
retry completed `review_completed`,8 findings across42 files (`interleave-95-coderabbit-retry.ndjson`).
No completion is attributed to the failed attempt.


Fresh-reader review completed using only the spec/contract/ledger. Valid findings addressed: distinguish
implementation status from historical authoring prose, state observer fuel/depth/error preservation
explicitly, and clarify component-level allocation failure in the shared contract. The reader did not
inspect code or validate runtime evidence.

The final integrated `build javadoc` passes 2,120 tests with zero failures/errors. Strict private
doclint passes all 17 changed production/harness files. Public heap examples and replay are tested
through real built-jar processes. Mutation comparisons, final measurement forks and the fresh
browser matrix are recorded separately below; earlier intermediate checks do not substitute for them.

### Completed CodeRabbit review disposition

Original severities are retained:

- minor: interface static declarations are not inherited; skip them in superclass/interface override comparisons and retain superclass static checks. Regression expects C.value()=2.
- major: reject cross-package nonpublic direct superinterfaces; also check superclass visibility, with public controls.
- major: array/cast leaf-type access checks overlapped the ordinary-review fix; retain malformed array/cast cases and accessible controls.
- major: replace recursive transitive override queries with a bounded superclass walk. Inspect each declaration once from base to candidate, tracking only legally widened inheritance. This avoids repeated false subproblems and keeps metadata immutable without cache/concurrency machinery. The generated30-level cross-package hierarchy retains the base package-private target and a loading ceiling.
- major: observer dispatch purity was already role-specific (`Work(method,role)`), even for decoded methods present in the map. Add the exact actor-then-CHECK mutating-override regression rather than changing correct dispatch semantics.
- major: array-valued stores already check the destination runtime component before mutation. Add int[] success/long[] failure coverage and verify the prior int[] element survives, independently checked on the JVM.
- trivial: assert each public heap fixture's exact terminal/fault category in addition to serialization/replay equality.
- trivial: bound the publication regression's cutoff-driving loop and reject unexpected terminal phases.

All findings have a concrete fix or verified existing behavior with strengthened regressions.
The bounded follow-up disposition and final verification are recorded separately below.

### Completed follow-up review disposition

The bounded follow-up completed `review_completed`,7 findings across43 files
(`interleave-95-coderabbit-followup.ndjson`):

- minor: pending-review prose was stale after the first completed review; replace it with current gate status.
- major: interface default fallback could rediscover a skipped private superclass method. Select only maximally specific interface declarations after class search, while preserving inherited public class implementations. Independent JVM controls expect1 for a private base method and2 for a public base method.
- major: repeated observer-purity concern is covered by role/method validation and the actor-then-check mutating-override rejection; pure override controls remain admitted.
- major: dispatch-retention concern was traced through the fixed-point closure and common loader/runtime selector. All selectable valid implementations and their dependencies are retained. An additional JVM-valid incompatible-receiver case now produces the modeled VM fault rather than a missing-target host exception.
- trivial: capture the instruction counter immediately before each first faulting primitive, require exactly one charged instruction, and retain cleanup/fault assertions.
- trivial: publication now promotes a two-node private cycle; assert both nodes before escape, after escape and after unlinking, with two allocations/six cells.
- trivial: document constructors/actors in both measurement fixtures, including private-cycle construction and the independent competing write.

The override implementation uses a bounded superclass walk rather than recursive caching; both deep
inaccessible same-name declarations and legal transitive widening match the JVM. The subsequent
R6 infrastructure work received its own completed review, recorded below.

## Feature mutation census and loader progress assertion

The completed initial feature census contained4804 KILLED,647 SURVIVED,625 NO_COVERAGE and6
TIMED_OUT /6082. R6 failed: no timeout is an assertion kill. The six timeout identities are in
BytecodeLoader.load: three negated convergence/pending conditions and three removed Map.size or
ArrayDeque.isEmpty calls. Raw `affected-census.xml` retains this failed run unchanged.

The existing PIT-only BytecodeWorkBudget agent now asserts loader fixed-point progress independently
of those conditions. A pass either terminates or grows the application class set; at most64 classes
permit at most64 passes. There are two Map.size checkpoints per pass, with a130-checkpoint invocation
allowance including framing. It binds the exact load(BytecodeLimits) descriptor and validates two
original checkpoints; a single PIT call-removal replacement may retain one. It never reads the mutated
convergence condition to derive its allowance, never instruments target classes and is not shipped in
the production jar. No timeout/resource setting or mutation gate changed.

Real-definition controls preserve outcomes with and without the agent. An actual removed class-count
call becomes a nonconvergent loader; the transformed replacement fails the independent assertion in a
child JVM and reports BOUND_EXCEEDED. Original binding/checkpoint drift rejects. Focused mutation reports replace whole refined classes, including the six incomplete
loader observations, before the assembled comparison is evaluated. The earlier full census itself
will never be labeled a passing final run.

### Additional R6-work review disposition

The R6 progress assertion introduced new test infrastructure, so an additional actual CLI review was
invoked rather than treating earlier reviews as evidence for that work. It completed
`review_completed`,7 findings across46 files (`interleave-95-coderabbit-progress-gate.ndjson`):

- minor: stale pending-review summary; reconcile the final status with actual completed reviews.
- major: retain all legally overriding declarations in the bounded walk, not merely the last one. JVM-verified raw narrowing can preserve an earlier public widening; generated A/B/C/D classes return4 on the JVM and interpreter. Admission follows JVM overriding rather than imposing Java-source visibility-narrowing rules.
- major: private resolved invocations already bypass subclass selection in loader and executor. A base-private/child-shadow regression returns1, preserving subclass dispatch for ordinary methods.
- major: all four runtime field opcodes already use hierarchy-resolved declaring IDs. An inherited static/instance model/base alias regression verifies one static cell and values2/4 on both JVM and interpreter.
- major: ancestors already uses a visited set. A forged I/J interface cycle rejects with LINKAGE; no recursive expansion is admitted.
- major: array loads/stores were already scheduling boundaries. Two shared int[1] increments with FINAL x[0]==2 retain both COMPLETED and CHECK_FALSE and match independent instruction enumeration in both modes.
- major: constructor receiver identity incorrectly depended on mutable local0. Store the original immutable receiver in the frame, copy/encode/snapshot/root it, and mark completion through that reference. A verified setup/actor constructor overwriting local0 with null completes normally on JVM and interpreter.

Canonical framing expectations were independently recalculated with a Python linear-opcode state
model:140 states,366 extra encoded frame slots including root,212 retained frame slots. The updated
literal byte totals are119051 and67438. Independent DataInputStream schema decoding and the actual
search agree. This is an encoding calculation, not a performance proxy.

The final seven-class run (loader, hierarchy, opcode executor, frame, owned state, snapshots and
sharing) completed without R6 detections: 1714 KILLED,237 SURVIVED,93 NO_COVERAGE /2044
(`final-owned-state.xml`). The earlier three-class run
passed1327 KILLED,186 SURVIVED,91 NO_COVERAGE /1604 (`final-refined.xml`).

## Pre-review measured results (4f1da27)

All six fresh JDK26 forks completed with mandatory independent outcome equivalence: three heap
forks and three initial-release forks, nine measured samples per fixture/mode (72 total). No PIT
process ran concurrently. Every fork used `26.0.2.1`, macOS/aarch64 and fixed512MiB compacting
SerialGC. The original initial-release task is reused; its historical94B conditions heading names
the harness origin, not the interpreter/profile revision measured here.

Counts/bytes below are identical across all nine samples. Time and retained heap are median
(minimum–maximum); milliseconds and KiB respectively. These tiny fixtures show variation and are
not throughput estimates for arbitrary Java.

| Fixture/mode | States | Transitions | Encodes | Encoded bytes | Encoding ms | Elapsed ms | Retained KiB |
|---|---:|---:|---:|---:|---:|---:|---:|
| aliased-array/REFERENCE | 98 | 175 | 176 | 118649 | 0.472 (0.299–1.150) | 1.262 (0.863–1.973) | 83.570 (83.570–83.641) |
| aliased-array/BATCHED | 35 | 58 | 59 | 39513 | 0.130 (0.107–0.197) | 0.619 (0.533–0.868) | 31.891 (31.891–31.891) |
| cyclic-publication/REFERENCE | 147 | 266 | 267 | 207740 | 0.613 (0.522–2.534) | 2.101 (1.677–7.794) | 138.531 (138.406–139.711) |
| cyclic-publication/BATCHED | 50 | 85 | 86 | 66267 | 0.253 (0.173–0.285) | 1.470 (0.963–1.792) | 49.562 (49.562–50.094) |
| lost-update/REFERENCE | 140 | 246 | 247 | 119051 | 1.686 (0.545–2.745) | 4.569 (1.941–6.189) | 93.312 (93.188–93.961) |
| lost-update/BATCHED | 60 | 98 | 99 | 46297 | 0.471 (0.336–1.437) | 1.709 (1.468–2.785) | 42.570 (42.375–42.711) |
| two-lock/REFERENCE | 475 | 866 | 867 | 709663 | 1.936 (1.663–2.434) | 4.155 (3.641–5.097) | 462.625 (462.555–463.414) |
| two-lock/BATCHED | 116 | 196 | 197 | 160813 | 0.565 (0.352–0.629) | 1.550 (1.105–1.844) | 119.547 (119.547–119.883) |

| Fixture/mode | Refreshes | Object visits | Edge visits | Promotions | History-only states | Outcome identities |
|---|---:|---:|---:|---:|---:|---:|
| aliased-array/REFERENCE | 22 | 115 | 45 | 7 | 0 | 1 |
| aliased-array/BATCHED | 16 | 81 | 31 | 5 | 0 | 1 |
| cyclic-publication/REFERENCE | 22 | 137 | 45 | 7 | 0 | 1 |
| cyclic-publication/BATCHED | 16 | 97 | 31 | 5 | 0 | 1 |
| lost-update/REFERENCE | 1 | 4 | 0 | 0 | 0 | 2 |
| lost-update/BATCHED | 1 | 4 | 0 | 0 | 0 | 2 |
| two-lock/REFERENCE | 1 | 10 | 6 | 0 | 0 | 2 |
| two-lock/BATCHED | 1 | 10 | 6 | 0 | 0 | 2 |

Sharing visits/promotions are aggregates over explored successors, not one execution. The dynamic
fixtures exercise cyclic/reference-array publication and nonzero sharing work. Historical flags
remain canonical even though these fixtures produced no pair differing solely in those flags; zero
is not evidence that history can be discarded. Independent targeted tests cover unlinking history.

Batched execution has fewer states/transitions/encoded bytes and lower median elapsed time/retained
heap on each measured fixture. Timing ranges overlap in some comparisons; cyclic-publication
reference timing has a7.794ms maximum. No universal speedup or percentage target is claimed.
Per-root traversal revisits aliases deliberately, and larger graph costs remain a practical limit.
There is no measured reason here to change the provisional default budgets or weaken sharing.

Reproduce initial-release forks with `profileBytecode` instead of `profileBytecodeHeap`; use distinct
`final-initial-fork-N` and `final-heap-fork-N` output directories for N=1,2,3. Raw CSV, conditions and
completion logs are retained in the local evidence directory.

Measured checker/harness/test/build diff SHA-256: `215d266cc2ae0be7242fb9bc0a1c96dcdcfe69b21a8302a3e7de595509bec013`.
These samples precede the PR142 corrections below. Source/artifact digests and
exact limits are in each fork’s `conditions.md`.

## Pre-review mutation comparison (4f1da27)

| Observation | KILLED | SURVIVED | NO_COVERAGE | Resource detections | Total |
|---|---:|---:|---:|---:|---:|
| Fresh default baseline |455|13|0|0|468|
| Final default run |455|13|0|0|468|
| Fresh affected baseline |4024|413|556|0|4993|
| Initial feature census, failed R6 |4804|647|625|6 TIMED_OUT|6082|
| Refined three-class run |1327|186|91|0|1604|
| Final seven-class run |1714|237|93|0|2044|
| Assembled affected observations |4917|640|594|0|6151|

The default fingerprint/status map matches baseline exactly. The affected assembly replaces entire
classes in chronological order using `final-refined.xml`, then `final-owned-state.xml`; it is **not
one passing whole-project or affected PIT run** and is never supplied to the mutation ratchet. The
raw failed census stays unchanged. All runs retain the full target-test selection.

Descriptor-complete normalized disassembly identifies unchanged compiled methods with3028 baseline
kills,276 survivors and466 uncovered mutants. All3028 comparable kills remain killed; six inherited
survivors become kills and one uncovered mutant gains coverage but survives. Changed/moved methods
are listed separately in `comparison-inherited.json`: their baseline kills do not have a valid
one-to-one preservation claim. The larger denominators include new/changed code. The remaining640
survivors and594 uncovered observations remain residual risk, not asserted equivalent mutants.

```sh
python3 docs/measurements/94C-compare-mutations.py /path/to/interleave-95-evidence \
  /path/to/interleave-95-baseline/build/classes/java/main build/classes/java/main \
  --javap /path/to/jdk26/bin/javap \
  --replacement-report final-refined.xml --replacement-report final-owned-state.xml
```

The helper retains its historical94C default behavior; its optional repeatable argument supplies
whole-class replacement reports for95. The original94C comparison also passed as a compatibility
check before this final comparison. Missing/duplicate reports, resource statuses and lost comparable
kills fail the diagnostic. This evidence does not establish complete mutation coverage or a proof.

## Final consumer and publication gates

Fresh browser producers pass after the last PIT run. The complete CI environment matrix passes
**309 browser assertions, zero failures and zero console errors/exceptions**: BYTECODE heap/faults,
source, declarative symmetry, exact/approximate strategies and resource-prefix evidence. Normalization
checks pass in the same command. The original source/JSON/symmetry formats retain their checks.

Final ordinary review traces admission through hierarchy/access validation, owned copy/encoding,
opcodes/accounting, publication, scheduler terminals and replay/viewer consumers. Target code is
never host-loaded; the JVM differential subprocesses are test-only. No new runtime dependencies,
timeout relaxations, mutation operator reductions or hidden exact bitstate storage are introduced.
Public additions remain bounded SC; wide array defaults do not admit wide computations.

Three actual CodeRabbit reviews completed as recorded above; the initial WebSocket failure did not.
The original NDJSON and earlier full-build/doclint logs were written to `/private/tmp` during those
runs and are no longer present after session resumption. Their completion/findings were inspected
and dispositions recorded during execution; this ledger preserves that record, not a claim that
those temporary raw logs remain available. Mutation XML and final fork/browser logs are retained
in the sibling evidence directory.

CI and Sam’s manual acceptance remain pending; publish as a draft. No deployment, CI watcher,96+
implementation, optimized bytecode DPOR measurement or JPF compatibility evidence is claimed.

Final fresh-reader testing correctly distinguished local acceptance, pending CI/manual review, mutation
assembly limits, inherited preservation, six-fork measurements and SC-1 compatibility. Two stale
retention/sharedness-status claims were corrected; the reader inspected docs only, not runtime evidence.

## PR142 review corrections

All11 inline findings, the outside-diff viewer finding and the additional sealed-subtype finding
were independently validated. No requested finding was silently deferred.

| Review item | Disposition and regression |
|---|---|
| Protected-receiver failure Javadoc | Document LINKAGE rejection separately from verified stack analysis |
| Hierarchy failure Javadoc | Distinguish missing declarations/null from METADATA, cycles, conflicting defaults and invalid special owners |
| Special selection/static shadow | Select instance declarations through the superclass chain; independent JVM returns1 with static B and2 with instance B |
| Access/nest helper Javadoc | Describe inaccessible symbolic types and invalid reciprocal nests |
| Repeated hierarchy validation | Cache only successful validation by the existing strictly increasing frozen input-byte version; a2000-call/3000-method fixture timed out at20s before the fix and loads within its bound after it; this is a regression ceiling, not a benchmark |
| Access on selected super target | Retain symbolic resolution access, remove repeated selected-target access; JVM accepts package-private B while inaccessible symbolic A still rejects |
| Invokeinterface visibility | Apply selected public-target rule to the opcode, not the declaration’s interface owner; class-view call returns2, interface-view rejects |
| Transitive unknown promotion | Close newly promoted unreachable parents through descendants before observations; actor-rooted graph stays private, repeated refresh adds no promotions |
| Inherited fields/exhaustiveness | Require exhaustive exploration and exactly COMPLETED in addition to JVM values |
| Heap measurement outcome guard | Require exactly the successful expected graph, aliases and values from each fixture in both test and profiling entry |
| Implicit monitor/oracle | Classify publication only after frame entry; allocation cutoff after a discarded Node omits its private cells |
| Outside-diff viewer bound | Replace per-map65536 field cap with setup+actor selected heap-cell allowances plus9 setup diagnostics; actual two-actor arrays cost36883 cells each and exceed65536 snapshot fields; normalize accepts73728 and rejects88202 entries against88201 bound |
| Additional sealed subtype | JVMS5.3.5 allows a permitted public cross-package subtype in the same runtime module; actual JVM positive/nonpublic-negative controls establish the distinction |

The JVM references are [special invocation](https://docs.oracle.com/en/java/javase/26/docs/specs/jvms/jvms-6.html#jvms-6.5.invokespecial)
and [deriving classes](https://docs.oracle.com/en/java/javase/26/docs/specs/jvms/jvms-5.html#jvms-5.3.5).
Before fixes, the new regression batch failed on selection/visibility, sealed admission, unknown
promotion, premature oracle promotion and the hierarchy deadline. The first special-static test
transformation was rejected by the Class-File API; replacing its method body/flags via a fresh method
definition fixed that test setup. Viewer normalization independently reproduced the old field cap.
The related regression batch and normalization boundary checks pass after corrections.

These corrections change sharedness traversal work. The earlier tables and mutation observations
above describe the pre-review snapshot; refreshed measurements/scoped evidence follow below.

### Refreshed verification and measurement evidence

The final integrated build and strict Javadoc pass **2127 tests**. Strict private doclint passes
the four changed production files. Complete browser compatibility passes309 assertions,0 failures
and0 console errors; normalization additionally accepts/rejects the large aggregate snapshot cases.
Browser checks consumed the fresh integrated-build artifacts before PIT, avoiding a redundant
Gradle producer run. Existing default/broad mutation runs are not repeated for these scoped fixes.

Six refreshed forks (`pr142-{heap,initial}-fork-{1,2,3}`) pass normalized outcome equivalence and
the new heap-specific expected-outcome guard. JDK26/512MiB/SerialGC/warmups/repetitions are unchanged.
The short browser matrix overlapped the beginning of this sample batch; no PIT ran concurrently.
Ranges expose this ordinary machine-load variability; no isolated timing or universal speedup is
claimed. All state/transition/encoding/sharedness counts match the pre-review table.

| Fixture/mode | Encoding ms median (range) | Elapsed ms median (range) | Retained KiB median (range) |
|---|---:|---:|---:|
| aliased-array/REFERENCE | 0.409 (0.358–1.215) | 1.092 (0.941–2.098) | 83.570 (83.570–83.766) |
| aliased-array/BATCHED | 0.135 (0.120–0.201) | 0.597 (0.537–0.905) | 31.891 (31.891–31.891) |
| cyclic-publication/REFERENCE | 0.627 (0.455–1.912) | 2.136 (1.595–8.371) | 138.531 (138.406–139.648) |
| cyclic-publication/BATCHED | 0.253 (0.186–0.295) | 1.497 (1.214–2.048) | 49.562 (49.562–50.047) |
| lost-update/REFERENCE | 1.635 (0.930–1.872) | 3.561 (2.798–5.113) | 93.312 (93.234–94.125) |
| lost-update/BATCHED | 0.605 (0.275–1.336) | 1.917 (1.058–2.433) | 42.406 (42.328–42.773) |
| two-lock/REFERENCE | 1.736 (1.397–2.413) | 3.936 (2.989–5.069) | 462.625 (462.555–463.414) |
| two-lock/BATCHED | 0.517 (0.338–0.648) | 1.598 (1.095–1.673) | 119.547 (119.547–119.883) |

Measured correction diff against4f1da27 SHA-256: `5c221818c3c0c1fda23f9e8bc057d4c6188734232cce21a80f8432038ae51544`.
A later loader-only cache-key refinement is outside timed search and does not change the measured
executor, state, sharing, encoding or fixtures. The hash identifies the measured source snapshot. The one integrated run before
this final run caught a redundant traversal of an already-rooted promoted object; narrowing the
extra closure to unknown/unreachable parents preserved the original instrumentation expectation
without weakening its assertion.

CodeRabbit CLI0.8.1 completed an actual `review_completed` with0 findings across12 correction
files (`pr142-coderabbit.ndjson`). The subsequent tiny refinement avoids reclosing already-rooted
promotions; ordinary review and the final integrated build cover it. This is not a fabricated
review of later documentation bookkeeping. Current raw review output is retained durably in the
sibling evidence directory.

No broad/default PIT rerun or additional Gradle producer run is claimed for this patch. The scoped
Loader/Hierarchy/Sharing observations and updated inherited comparison are recorded below.

The first three-class correction census produced1078 KILLED,178 SURVIVED,90 NO_COVERAGE and2
TIMED_OUT /1348; R6 failed, and raw `pr142-scoped-before.xml` preserves it. Both timeouts remove
Map.size calls in the newly added validation cache, reintroducing repeated expensive validation.
The cache now uses the existing bounded aggregate byte count as its class-set version: each new
frozen class read strictly increases total, immutable metadata never changes within that version,
and failed loading aborts. Only successful hierarchy validation records a version. This removes
redundant size queries and a separate generation counter. No operator, timeout, target-test or
resource gate is changed; only the loader needs a scoped rerun.

### Completed correction mutation evidence

The loader-only rerun passes R6: **757 KILLED,122 SURVIVED,50 NO_COVERAGE /929**, with zero
timeout, memory, run-error or nonviable detections (`pr142-loader.xml`). The failed three-class
report remains unchanged. The hierarchy/sharing class observations from it contain no resource
detections; latest loader observations replace that entire class, not selected favorable mutants.

The updated affected assembly is **4947 KILLED,637 SURVIVED,598 NO_COVERAGE /6182**. It is still
not one passing affected/whole-project PIT run and is never supplied to the ratchet. The unchanged
compiled-method comparison retains every one of3017 comparable baseline kills; six inherited
survivors become kills and one uncovered mutant becomes a covered survivor. The comparable count
differs from the pre-review3028 because changed constructor/method bodies leave that comparison,
not because baseline kills are silently discarded or claimed preserved across changed code.
Changed/moved methods remain separately listed with no one-to-one preservation claim.

The helper reads the existing pre-review default455/13/0 observations; no new default run is
claimed. Reproduce the affected assembly by appending these whole-class reports to the earlier
comparison command, in order:

```sh
--replacement-report pr142-scoped-before.xml --replacement-report pr142-loader.xml
```

The2127-test integrated build preceded the small cache-key refinement. Focused loader, dispatch,
input-boundary, strict Javadoc and four-file private doclint pass after it; the final PIT invocation
also passes its unmutated coverage run with the full unchanged target-test selection. Browser
artifacts and search measurements precede that loader-only refinement and cover unchanged search/
consumer behavior. No blanket timeout change, mutation gate relaxation or test weakening occurred.
