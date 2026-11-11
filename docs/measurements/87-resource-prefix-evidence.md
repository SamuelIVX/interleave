<!-- Source resource-prefix verification, mutation lineage and explicit retained coverage limits. -->
# 87 — Resource-prefix capture/replay evidence

## TL;DR

[Spec 87](../specs/active/87-resource-prefix-evidence.md) implements approved B8 choice A / J1 on
merged I2 PR #128 at `2f578cf`. Baseline build/strict Javadoc and **1,815 tests** pass. Integrated
feature checks pass **1,852 tests** with zero failures/errors/skips. No previous tests were removed.

Affected baseline: **926/1,013 K + 64 S + 23 NC**. Integrated affected result:
**1,025/1,105 K + 58 S + 22 NC**. Both ResourcePrefix classes total **60/60 K**, no uncovered sites.
Scoped R6 passes with zero TIMED_OUT/MEMORY_ERROR/NON_VIABLE/RUN_ERROR. R5/R7 are not enforced
for this affected subset and are not claimed passed there. The last helper assertion is covered by
a final 519-mutant capture/replay scope: **472 K + 43 S + 4 NC**, R6 passes. All 519 exact identities
and statuses match their entries in the integrated 1,105-site census. The remaining POR/DPOR/CBS
production paths are unchanged; the final default gate additionally measures CBS and state classes.

## Per-class census

Classes below share prefix `dev.samhb.interleave.`. K = KILLED, S = SURVIVED, NC = NO_COVERAGE.
Nested/anonymous classes are included explicitly; their mutation populations are never silently
folded into the enclosing-class figure.

| Class | Fresh baseline | Integrated feature |
|---|---|---|
| `InterleaveRunner` | 106/139 K + 29 S + 4 NC | 110/141 K + 27 S + 4 NC |
| `InterleaveRunner$1` | 29/42 K + 13 S + 0 NC | 30/43 K + 13 S + 0 NC |
| `InterleaveRunner$Builder` | 30/30 K + 0 S + 0 NC | 30/30 K + 0 S + 0 NC |
| `InterleaveRunner$LimitState` | 4/4 K + 0 S + 0 NC | 4/4 K + 0 S + 0 NC |
| `InterleaveRunner$PartialResult` | 17/21 K + 4 S + 0 NC | 21/21 K + 0 S + 0 NC |
| `ResourcePrefix` | 0/0 K + 0 S + 0 NC | 53/53 K + 0 S + 0 NC |
| `ResourcePrefix$Replay` | 0/0 K + 0 S + 0 NC | 7/7 K + 0 S + 0 NC |
| `TestResult` | 116/119 K + 2 S + 1 NC | 126/128 K + 2 S + 0 NC |
| `cb.ContextBoundedExplorer` | 127/127 K + 0 S + 0 NC | 131/131 K + 0 S + 0 NC |
| `dpor.DporExplorer` | 285/309 K + 12 S + 12 NC | 293/317 K + 12 S + 12 NC |
| `por.StaticPorExplorer` | 125/134 K + 3 S + 6 NC | 129/138 K + 3 S + 6 NC |
| `search.DfsExplorer` | 86/87 K + 1 S + 0 NC | 90/91 K + 1 S + 0 NC |
| `search.StateVisitor` | 1/1 K + 0 S + 0 NC | 1/1 K + 0 S + 0 NC |

## Identity and residual accounting

All **427 unchanged-method mutation identities** remain: **420 statuses unchanged**, **six S -> K**
and **one NC -> K**. Identity contains owner, method/descriptor, mutator, operation description,
bytecode indices and blocks; shifted source lines are excluded. Exclude the rewritten traversal
methods, runner `run`, changed TestResult constructors/copy methods and new methods/classes.
The unchanged legacy three-list TestResult constructor remains included.

The six newly detected unchanged-method survivors are the iterative-loop boundary and approximate
OR/AND mutation, PartialResult failure/deadlock getters and the corresponding trace additions.
The uncovered legacy TestResult constructor now has a detecting assertion. These are assertion gains;
the net population increase of 92 is reported separately and is not credited as inherited kills.

For rewritten methods, no site-for-site lineage is claimed. The residual operation/status multiset
(owner, descriptor, mutator, description, status, retaining multiplicity) contains **no new residual
operations** and removes seven baseline residuals. This multiset check is weaker than exact identity
matching and is named separately. All 80 remaining raw non-kills are catalogued below. Their presence
is not a claim that they are harmless or equivalent, nor a claim of complete explorer/runner coverage.

New capture notifications add four killed mutations at each of five visit sites; the separately
measured resource value/replay population has 53 + 7 killed mutations. Other new/rewritten populations
are accounted by the class table rather than by adding overlapping mutation scopes.

## Default gate and unchanged floor

Baseline default: **436/451 K + 15 S**, including **127/127 K** in CBS and 324 state mutations.
The source visit-interruption block adds four CBS mutants, all killed: measured CBS **131/131 K**.
Expected default population is therefore **455**, without changing targets/operators/filters/tests.
Update only EXPECTED_TOTAL_MUTANTS and the informational CBS EXPECTED_PER_CLASS entry.

Keep the **94% floor**. Independent arithmetic: 94*(400+55) = 37,600+5,170 = 42,770, so 428 kills
are required. Enumerating integer K for K*100 >= 94*455 independently gives the same minimum 428.
Expected 440 kills retain **12 kills of headroom**, matching 436 - ceil(94*451/100) = 12.
No mutation resources, coverage floor, source limits or CI parallelism are relaxed.

Final default: **440/455 K + 15 S**, R5/R6/R7 pass with zero resource/run errors and no recorded
per-class drift. All **324 exact state-class identities/statuses** and all **15 raw survivor identities**
match baseline. CBS remains all-killed, growing 127 -> 131. Its rewritten dfs population is not
claimed identical; the class delta and added notification sites are recorded explicitly.

## Independent acceptance and external consumer

Three literal source actions produce four positions. Visit allowances 1,2,4 capture lengths 0,1,3,
final x values 0,1,3 and replay snapshot counts 1,2,4. Limit interruption precedes terminal checking,
so the four-position run records no completed trace even though explicit replay returns COMPLETED.

Controlled-clock tests check elapsed 9/10/11 against a 10ms allowance under all strategies; equality
stops. A simultaneous root state/time stop reports MAX_STATES. Counter-backed real source delegates
show exactly one transition and one property evaluation before a two-visit stop: capture adds neither.
Opaque predicates force DPOR's exhaustive route, while null predicates exercise its reduction route.

K=0 on two two-action owners visits root plus two four-position owner-first chains: **9 positions**.
A ten-visit iterative allowance reaches the next pass's root. Exact and approximate runs both retain
that root prefix, earlier cutoff history and actual store identity, with two distinct stores created.
Earlier completion, violation and deadlock branches are retained when a different branch later stops.

Additional real-source cases cover branch-skipped PCs, repeat/helper metadata, aliased instruction
occurrences, typed shared/local arrays, final-position property errors and empty completion/deadlock.
Wrong identities/outcomes/end position, earlier failure and impossible choices reject; unexpected
property exceptions propagate. Neither ordinary cutoff simplifier accepts the unclassified carrier.
Value getters, replay snapshots, repeated/concurrent replay and runner reuse cannot mutate captured
evidence. Native TestResult object streams round-trip the prefix in the same build; cross-build UID
compatibility remains unsupported as already documented.

An external consumer compiled against the built library jar passes actual capture, PCs/state,
checked/unchecked replay and unchanged original resource classification. The first standalone launch
omitted the existing Gson runtime dependency; rerunning with the declared Gson jar passes without
adding/changing dependencies. Existing verdict normalization checks pass. J1 deliberately leaves
JSON schemas, renderer and CLI unchanged, so no new DOM test is claimed; J2 owns those consumers.

## Review and reproduction

CodeRabbit 0.8.1 completed all 15 initially submitted implementation/test/spec files with one minor
stale-status finding. Corrected the B8 summary to agree with its detailed row; no CLI rerun for minor
feedback. Final lifecycle/metadata/helper assertions and measured bookkeeping/docs received local
review. Independent sequential passes check capture lifetime, no extra execution, copy consistency,
resource/terminal separation and actual source identity. No subagents were used.

In clean checkouts of baseline `2f578cf` and this feature revision, run the ordinary build/Javadoc
and an affected census with the repository's required local flags:

```bash
./gradlew --max-workers=1 -PpitestThreads=1 build javadoc --no-daemon
./gradlew --max-workers=1 -PpitestThreads=1 pitest '-PpitestTargetOverride=dev.samhb.interleave.search.StateVisitor,dev.samhb.interleave.search.DfsExplorer,dev.samhb.interleave.por.StaticPorExplorer,dev.samhb.interleave.dpor.DporExplorer,dev.samhb.interleave.cb.ContextBoundedExplorer,dev.samhb.interleave.InterleaveRunner*,dev.samhb.interleave.TestResult,dev.samhb.interleave.ResourcePrefix*' --no-daemon
```

Read test counts from build/test-results/test/TEST-*.xml. Preserve build/reports/pitest/mutations.xml
before the default run overwrites it, then run the default gate without an override:

```bash
./gradlew --max-workers=1 -PpitestThreads=1 pitest --no-daemon
```

The class table and raw catalogue summarize the measured XML inline. No evidence claim depends on
a host-local path. Record later documentation-only changes separately from measured implementation.
Sam supplies remote CI/review/merge updates after publication; no watcher runs. J2 follows after
J1 merge. Shortening, a new CLI mode, B9/B10 and roadmap #11/#12 remain unselected here.

## Raw affected residuals

Every remaining non-kill is listed by symbol, descriptor, mutator/operation and bytecode indices
with blocks. Grouped rows retain multiplicity; there are no hidden equivalence filters or exclusions.
NC entries are existing coverage gaps. Survivors include host-heap/GC measurements, diagnostic
snapshot/dedup branches and explorer coverage/scheduling guards; this PR makes no blanket equivalence
claim for those paths. All resource-prefix/replay and added notification mutations are KILLED.

| Symbol and descriptor | Mutator / operation | Status | Indices (blocks) |
|---|---|---|---|
| `InterleaveRunner.convertToTestResult(Ldev/samhb/interleave/search/DfsResult;Ldev/samhb/interleave/core/Program;JJZ)Ldev/samhb/interleave/TestResult;` | `NegateConditionalsMutator` — negated conditional | SURVIVED | 78 (21), 81 (23), 116 (30), 119 (32) |
| `InterleaveRunner.convertToTestResult(Ldev/samhb/interleave/search/DfsResult;Ldev/samhb/interleave/core/Program;JJZ)Ldev/samhb/interleave/TestResult;` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/TraceRecord::withStates | NO_COVERAGE | 131 (34) |
| `InterleaveRunner.convertToTestResult(Ldev/samhb/interleave/search/DfsResult;Ldev/samhb/interleave/core/Program;JJZ)Ldev/samhb/interleave/TestResult;` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/core/Program::isSource | SURVIVED | 80 (22), 118 (31) |
| `InterleaveRunner.convertToTestResult(Ldev/samhb/interleave/search/DfsResult;Ldev/samhb/interleave/core/Program;JJZ)Ldev/samhb/interleave/TestResult;` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/search/TraceReplayer::captureSnapshots | NO_COVERAGE | 125 (33) |
| `InterleaveRunner.convertToTestResult(Ldev/samhb/interleave/search/DfsResult;Ldev/samhb/interleave/core/Program;JJZ)Ldev/samhb/interleave/TestResult;` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/search/TraceReplayer::captureSnapshots | SURVIVED | 87 (24) |
| `InterleaveRunner.lambda$isFailure$0(Ldev/samhb/interleave/search/Trace;)Z` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/search/Trace::outcome | SURVIVED | 8 (2) |
| `InterleaveRunner.lambda$run$1(Ldev/samhb/interleave/TraceRecord;Ldev/samhb/interleave/TraceRecord;)Z` | `NegateConditionalsMutator` — negated conditional | SURVIVED | 10 (3) |
| `InterleaveRunner.lambda$run$1(Ldev/samhb/interleave/TraceRecord;Ldev/samhb/interleave/TraceRecord;)Z` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/TraceRecord::evidence | SURVIVED | 9 (2) |
| `InterleaveRunner.lambda$run$1(Ldev/samhb/interleave/TraceRecord;Ldev/samhb/interleave/TraceRecord;)Z` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/core/CutoffEvidence::equals | SURVIVED | 21 (7) |
| `InterleaveRunner.lambda$run$1(Ldev/samhb/interleave/TraceRecord;Ldev/samhb/interleave/TraceRecord;)Z` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/core/TraceEvidence::cutoff | SURVIVED | 17 (6) |
| `InterleaveRunner.lambda$run$1(Ldev/samhb/interleave/TraceRecord;Ldev/samhb/interleave/TraceRecord;)Z` | `BooleanFalseReturnValsMutator` — replaced boolean return with false for dev/samhb/interleave/InterleaveRunner::lambda$run$1 | SURVIVED | 22 (8) |
| `InterleaveRunner.run(Ldev/samhb/interleave/core/Program;Ljava/util/function/LongSupplier;)Ldev/samhb/interleave/TestResult;` | `MathMutator` — Replaced long subtraction with addition | SURVIVED | 20 (5), 190 (34), 204 (35), 296 (61), 310 (62) |
| `InterleaveRunner.run(Ldev/samhb/interleave/core/Program;Ljava/util/function/LongSupplier;)Ldev/samhb/interleave/TestResult;` | `NonVoidMethodCallMutator` — removed call to java/lang/Math::max | SURVIVED | 205 (35), 311 (62) |
| `InterleaveRunner.run(Ldev/samhb/interleave/core/Program;Ljava/util/function/LongSupplier;)Ldev/samhb/interleave/TestResult;` | `NonVoidMethodCallMutator` — removed call to java/lang/Runtime::freeMemory | SURVIVED | 19 (4), 189 (33), 295 (60) |
| `InterleaveRunner.run(Ldev/samhb/interleave/core/Program;Ljava/util/function/LongSupplier;)Ldev/samhb/interleave/TestResult;` | `NonVoidMethodCallMutator` — removed call to java/lang/Runtime::totalMemory | SURVIVED | 17 (3), 187 (32), 293 (59) |
| `InterleaveRunner.run(Ldev/samhb/interleave/core/Program;Ljava/util/function/LongSupplier;)Ldev/samhb/interleave/TestResult;` | `NonVoidMethodCallMutator` — removed call to java/lang/String::valueOf | NO_COVERAGE | 172 (28) |
| `InterleaveRunner.run(Ldev/samhb/interleave/core/Program;Ljava/util/function/LongSupplier;)Ldev/samhb/interleave/TestResult;` | `VoidMethodCallMutator` — removed call to java/lang/Runtime::gc | SURVIVED | 13 (2) |
| `InterleaveRunner.runIterativeDeepening(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/search/Invariant;Ldev/samhb/interleave/search/StateVisitor;Ldev/samhb/interleave/InterleaveRunner$LimitState;)Ldev/samhb/interleave/search/DfsResult;` | `NullReturnValsMutator` — replaced return value with null for dev/samhb/interleave/InterleaveRunner::runIterativeDeepening | NO_COVERAGE | 111 (21) |
| `InterleaveRunner$1.<init>(Ldev/samhb/interleave/InterleaveRunner;[JLdev/samhb/interleave/InterleaveRunner$LimitState;Ljava/util/function/LongSupplier;JLdev/samhb/interleave/core/Program;)V` | `NonVoidMethodCallMutator` — removed call to java/util/Objects::requireNonNull | SURVIVED | 21 (0) |
| `InterleaveRunner$1.lambda$onTraceCreated$0(Ldev/samhb/interleave/search/Trace;Ldev/samhb/interleave/TraceRecord;)Z` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/core/CutoffEvidence::equals | SURVIVED | 8 (3) |
| `InterleaveRunner$1.lambda$onTraceCreated$0(Ldev/samhb/interleave/search/Trace;Ldev/samhb/interleave/TraceRecord;)Z` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/core/TraceEvidence::cutoff | SURVIVED | 7 (2) |
| `InterleaveRunner$1.lambda$onTraceCreated$0(Ldev/samhb/interleave/search/Trace;Ldev/samhb/interleave/TraceRecord;)Z` | `BooleanFalseReturnValsMutator` — replaced boolean return with false for dev/samhb/interleave/InterleaveRunner$1::lambda$onTraceCreated$0 | SURVIVED | 9 (4) |
| `InterleaveRunner$1.onStateVisited(Ldev/samhb/interleave/core/Configuration;)V` | `NonVoidMethodCallMutator` — removed call to java/lang/String::valueOf | SURVIVED | 112 (13) |
| `InterleaveRunner$1.onTraceCreated(Ldev/samhb/interleave/search/Trace;)V` | `NegateConditionalsMutator` — negated conditional | SURVIVED | 10 (1), 14 (3), 18 (5), 24 (7) |
| `InterleaveRunner$1.onTraceCreated(Ldev/samhb/interleave/search/Trace;)V` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/TraceRecord::outcome | SURVIVED | 16 (4), 22 (6) |
| `InterleaveRunner$1.onTraceCreated(Ldev/samhb/interleave/search/Trace;)V` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/core/Program::isSource | SURVIVED | 13 (2) |
| `InterleaveRunner$1.onTraceCreated(Ldev/samhb/interleave/search/Trace;)V` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/search/TraceReplayer::captureSnapshots | SURVIVED | 34 (9) |
| `TestResult.toJson()Ljava/lang/String;` | `NonVoidMethodCallMutator` — removed call to com/google/gson/Gson::toJson | SURVIVED | 155 (46) |
| `TestResult.toJson()Ljava/lang/String;` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/TestResult::incompletenessReasons | SURVIVED | 154 (45) |
| `dpor.DporExplorer.dfsDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;)V` | `NegateConditionalsMutator` — negated conditional | NO_COVERAGE | 202 (45) |
| `dpor.DporExplorer.dfsDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;)V` | `NegateConditionalsMutator` — negated conditional | SURVIVED | 172 (37) |
| `dpor.DporExplorer.dfsDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;)V` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/search/TracePrefix::trace | NO_COVERAGE | 191 (43) |
| `dpor.DporExplorer.dfsDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;)V` | `NonVoidMethodCallMutator` — removed call to java/lang/Integer::intValue | SURVIVED | 277 (65) |
| `dpor.DporExplorer.dfsDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;)V` | `NonVoidMethodCallMutator` — removed call to java/util/List::add | NO_COVERAGE | 197 (44) |
| `dpor.DporExplorer.dfsDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;)V` | `NonVoidMethodCallMutator` — removed call to java/util/List::copyOf | NO_COVERAGE | 184 (41), 186 (42) |
| `dpor.DporExplorer.dfsDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;)V` | `VoidMethodCallMutator` — removed call to dev/samhb/interleave/search/StateVisitor::onTraceCreated | NO_COVERAGE | 207 (46) |
| `dpor.DporExplorer.dporDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/dpor/SleepSet;Ldev/samhb/interleave/dpor/HappensBefore;Ldev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ljava/util/Set;Ljava/util/Map;)V` | `NegateConditionalsMutator` — negated conditional | NO_COVERAGE | 371 (97) |
| `dpor.DporExplorer.dporDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/dpor/SleepSet;Ldev/samhb/interleave/dpor/HappensBefore;Ldev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ljava/util/Set;Ljava/util/Map;)V` | `NegateConditionalsMutator` — negated conditional | SURVIVED | 633 (159) |
| `dpor.DporExplorer.dporDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/dpor/SleepSet;Ldev/samhb/interleave/dpor/HappensBefore;Ldev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ljava/util/Set;Ljava/util/Map;)V` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/por/IndependenceRelation::hasEnableDisableInterference | SURVIVED | 750 (189) |
| `dpor.DporExplorer.dporDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/dpor/SleepSet;Ldev/samhb/interleave/dpor/HappensBefore;Ldev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ljava/util/Set;Ljava/util/Map;)V` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/search/TracePrefix::trace | NO_COVERAGE | 360 (95) |
| `dpor.DporExplorer.dporDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/dpor/SleepSet;Ldev/samhb/interleave/dpor/HappensBefore;Ldev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ljava/util/Set;Ljava/util/Map;)V` | `NonVoidMethodCallMutator` — removed call to java/lang/Integer::intValue | SURVIVED | 627 (158), 913 (228) |
| `dpor.DporExplorer.dporDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/dpor/SleepSet;Ldev/samhb/interleave/dpor/HappensBefore;Ldev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ljava/util/Set;Ljava/util/Map;)V` | `NonVoidMethodCallMutator` — removed call to java/util/List::add | NO_COVERAGE | 366 (96) |
| `dpor.DporExplorer.dporDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/dpor/SleepSet;Ldev/samhb/interleave/dpor/HappensBefore;Ldev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ljava/util/Set;Ljava/util/Map;)V` | `NonVoidMethodCallMutator` — removed call to java/util/List::copyOf | NO_COVERAGE | 353 (93), 355 (94) |
| `dpor.DporExplorer.dporDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/dpor/SleepSet;Ldev/samhb/interleave/dpor/HappensBefore;Ldev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ljava/util/Set;Ljava/util/Map;)V` | `NonVoidMethodCallMutator` — removed call to java/util/List::isEmpty | SURVIVED | 31 (8) |
| `dpor.DporExplorer.dporDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/dpor/SleepSet;Ldev/samhb/interleave/dpor/HappensBefore;Ldev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ljava/util/Set;Ljava/util/Map;)V` | `NonVoidMethodCallMutator` — removed call to java/util/List::removeIf | SURVIVED | 159 (48) |
| `dpor.DporExplorer.dporDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/dpor/SleepSet;Ldev/samhb/interleave/dpor/HappensBefore;Ldev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ljava/util/Set;Ljava/util/Map;)V` | `VoidMethodCallMutator` — removed call to dev/samhb/interleave/dpor/HappensBefore::record | SURVIVED | 641 (160) |
| `dpor.DporExplorer.dporDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/dpor/SleepSet;Ldev/samhb/interleave/dpor/HappensBefore;Ldev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ljava/util/Set;Ljava/util/Map;)V` | `VoidMethodCallMutator` — removed call to dev/samhb/interleave/search/StateVisitor::onTraceCreated | NO_COVERAGE | 376 (98) |
| `dpor.DporExplorer.explore(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/search/Invariant;Ldev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;)Ldev/samhb/interleave/search/DfsResult;` | `NonVoidMethodCallMutator` — removed call to java/util/Optional::empty | SURVIVED | 78 (14) |
| `dpor.DporExplorer.lambda$dporDfs$2(Ljava/util/Set;Ldev/samhb/interleave/dpor/SleepSet;)Z` | `NonVoidMethodCallMutator` — removed call to java/util/Set::containsAll | SURVIVED | 6 (1) |
| `dpor.DporExplorer.lambda$dporDfs$2(Ljava/util/Set;Ldev/samhb/interleave/dpor/SleepSet;)Z` | `BooleanFalseReturnValsMutator` — replaced boolean return with false for dev/samhb/interleave/dpor/DporExplorer::lambda$dporDfs$2 | SURVIVED | 7 (2) |
| `por.StaticPorExplorer.explore(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/search/Invariant;Ldev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;)Ldev/samhb/interleave/search/DfsResult;` | `NonVoidMethodCallMutator` — removed call to java/util/Optional::empty | SURVIVED | 67 (12) |
| `por.StaticPorExplorer.porDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ldev/samhb/interleave/por/PropertyPersistentSetComputer;)V` | `NegateConditionalsMutator` — negated conditional | NO_COVERAGE | 521 (125) |
| `por.StaticPorExplorer.porDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ldev/samhb/interleave/por/PropertyPersistentSetComputer;)V` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/core/Configuration::allTerminated | SURVIVED | 486 (115) |
| `por.StaticPorExplorer.porDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ldev/samhb/interleave/por/PropertyPersistentSetComputer;)V` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/search/TracePrefix::trace | NO_COVERAGE | 510 (123) |
| `por.StaticPorExplorer.porDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ldev/samhb/interleave/por/PropertyPersistentSetComputer;)V` | `NonVoidMethodCallMutator` — removed call to java/lang/Integer::intValue | SURVIVED | 296 (75) |
| `por.StaticPorExplorer.porDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ldev/samhb/interleave/por/PropertyPersistentSetComputer;)V` | `NonVoidMethodCallMutator` — removed call to java/util/List::add | NO_COVERAGE | 516 (124) |
| `por.StaticPorExplorer.porDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ldev/samhb/interleave/por/PropertyPersistentSetComputer;)V` | `NonVoidMethodCallMutator` — removed call to java/util/List::copyOf | NO_COVERAGE | 503 (121), 505 (122) |
| `por.StaticPorExplorer.porDfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ljava/util/Map;Ljava/util/List;Ljava/util/Set;Ldev/samhb/interleave/search/Invariant;[JLdev/samhb/interleave/search/StateStore;Ldev/samhb/interleave/search/StateVisitor;Ldev/samhb/interleave/por/PropertyPersistentSetComputer;)V` | `VoidMethodCallMutator` — removed call to dev/samhb/interleave/search/StateVisitor::onTraceCreated | NO_COVERAGE | 526 (126) |
| `search.DfsExplorer.dfs(Ldev/samhb/interleave/core/Program;Ldev/samhb/interleave/core/Configuration;Ljava/util/List;Ljava/util/List;Ldev/samhb/interleave/search/TracePrefix;Ldev/samhb/interleave/search/Invariant;)V` | `NonVoidMethodCallMutator` — removed call to dev/samhb/interleave/core/Configuration::allTerminated | SURVIVED | 366 (78) |
