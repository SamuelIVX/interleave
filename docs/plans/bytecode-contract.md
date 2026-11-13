<!-- Selected roadmap #12 contract: bounded SC interpretation, staged ownership and release gates. -->
# #12 — Bounded Java bytecode checking

## Objective and status

**Selected; specification only.** Approved scope: controlled interpretation of compiled Java for fixed
actors under bounded sequential consistency (SC). The first public release must find a real lost update
and a two-lock deadlock through loading, search, evidence, strict replay, library/CLI, reports and viewer.
No target-code host-JVM fallback is permitted. This is not unrestricted Java or full Java Memory Model coverage.

Planning baseline: remote main `2a242c4`, freshly fetched during authoring. B10 is complete through
PRs #136/#137 and its closure commit. Sam explicitly selected #12 after that stopping point; #11
and parked conditional/deferred tasks remain unselected. No bytecode implementation or runtime
measurement is established by this PR. [Vocabulary](../../CONTEXT.md) owns domain terms; this document
owns common behavior, interface seams, verification and phase boundaries.

## Sequential delivery and ownership

One specification PR followed by **ten implementation PRs**, sequentially into main. Split further
only for a distinct reviewable behavior or correctness argument. Each phase owns its affected callers,
tests, fixtures, documentation and consumers. A spec merge enables no capability.

| Phase | Specification | Complete delivery and release boundary |
|---|---|---|
| 94A | [Interpreter foundation](../specs/active/94A-bytecode-foundation.md) | Internal loader, owned state, single-actor execution/faults, fuel and setup lock references; JVM differential cases |
| 94B | [Concurrent execution](../specs/active/94B-bytecode-concurrency.md) | Internal actors, monitors/cleanup, exact DFS, checks, reference/batching and measured feasibility gate |
| 94C | [Initial public release](../specs/active/94C-bytecode-release.md) | BYTECODE evidence, strict replay, library/CLI, reports/viewer and complete lost-update/deadlock examples |
| 95 | [Objects, arrays and dispatch](../specs/active/95-bytecode-heap.md) | Actor allocations, aliases, arrays, hierarchy/dispatch, publication and accounting |
| 96 | [Exceptions](../specs/active/96-bytecode-exceptions.md) | General supported catch/finally, ordered handlers, unwinding, suppression and restricted try-with-resources |
| 97 | [Primitives and core library](../specs/active/97-bytecode-core-library.md) | Remaining primitives, exact descriptor allowlist, deterministic intrinsic semantics |
| 98 | [CBS](../specs/active/98-bytecode-cbs.md) | Exact context bounds, cost dominance, evidence and all consumers |
| 99 | [Restricted DPOR](../specs/active/99-bytecode-dpor.md) | Admission/resource analysis, dependency/observation summaries, preservation argument, fallback and measured reduction |
| 100 | [Narrow atomics](../specs/active/100-bytecode-atomics.md) | Integer/Boolean/Reference construction, get/set and strong CAS; cyclic retry fallback |
| 101 | [Bitstate and completion](../specs/active/101-bytecode-bitstate.md) | One-filter approximate DFS/CBS/DPOR, full consumers, compiler/JPF evidence and scope closure |

Do not expose public bytecode selectors before 94C. Internal 94A/B remain testable without installing
public partial entry points. Do not implement a synthetic list of fuel-limit steps or append cutoff
sentinels. Later phases extend the profile version and admission registry together with strict replay.

## Loading and public interfaces

Use JDK 26 `java.lang.classfile`, with a metadata-only hierarchy resolver.
The checker already requires JDK26+ (build.gradle.kts toolchain/release=26, README requirements and
CI setup-java=26); this adds no runtime-floor or packaging change. Input class-file floor17 is
distinct from the checker runtime requirement. Parse target bytes; never
reflectively load/instantiate/invoke target classes, execute initializers, or fall back to JDK methods.
The [Class-File API](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/classfile/package-summary.html)
provides class models; parsing alone is not this profile's bytecode verifier.

Accept non-preview Java 17–26 class files (major 61–70, minor 0) from **one directory classpath**.
No JAR discovery, automatic compilation or ambient host application classpath. Resolve binary names
under the canonical root, reject escapes/symlink escapes, duplicate/conflicting identities and missing
metadata. Bound reads before allocation: 64 application classes, 1 MiB/class, 8 MiB total. Include
all application classes used by the closed reachable hierarchy, not merely initially called methods.
Use explicit trusted metadata for supported platform classes; missing/unknown metadata rejects.
Validate access, descriptors, stack/local types, branch/handler targets, constructor initialization,
return types and reachable opcode/call support before exploration. Inspect all branch alternatives
and possible dispatch targets; runtime values do not excuse unsupported reachable code. Unreachable
methods may remain unsupported unless hierarchy validity or class initialization requires inspection.
No filesystem access or resolution occurs during exploration/replay after loading the frozen artifact.

Reject application `<clinit>` with executable behavior; an absent or return-only initializer is allowed.
Supported ConstantValue fields initialize without execution; otherwise static fields have JVM default
values. Ordinary static Object locks, static arrays, enum machinery and assertion-status initialization
usually need executable `<clinit>` and reject. Initialize instance locks in the fixture constructor and
use declared checks instead of Java assertions. This remains true in later phases; no initializer allowlist.

94C introduces `dev.samhb.interleave.format.BytecodeProgramLoader`:

```java
public static BytecodeProgram load(Path classDirectory, String modelClass);
public static BytecodeProgram load(Path classDirectory, String modelClass, BytecodeLimits limits);
// InterleaveRunner: a distinct name keeps existing run(Program), including null calls, compatible.
public TestResult runBytecode(BytecodeProgram program);
// TraceReplayer: validate and return an immutable record with actual replay snapshots/observations.
public TraceRecord replayBytecode(BytecodeProgram program, TraceRecord record);
```

`BytecodeProgram` is immutable, retaining bytes/metadata, profile identity, validated declarations,
limits and interpreted setup snapshot. Returned instances cannot be forged by public construction.
All three public types BytecodeProgram/BytecodeLimits/BytecodeLoadException live in
`dev.samhb.interleave.bytecode`; the facade alone lives in `format`. BytecodeProgram has no public
constructor and exposes `artifactDigest()`, `profileVersion()` and `limits()` read-only accessors.
BytecodeLoadException extends IllegalArgumentException and exposes a stable diagnostic category plus
optional bytecode location; no target throwable is loaded on the host.

```java
public record BytecodeLimits(int actorInstructions, int actorAllocations, int actorHeapCells,
    int maxCallDepth, int maxArrayLength, int maxStringLength, int setupInstructions,
    int setupAllocations, int setupHeapCells, int checkInstructions) {
    public static BytecodeLimits defaults();
}
```

The canonical constructor validates every component against the table below; no public mutable builder
or unvalidated bypass. Internal execution mode is
REFERENCE or BATCHED; the production default is BATCHED only after 94B's gate. Evidence records mode.
`load` rejects invalid/null input and setup failure with an actionable loading diagnostic containing
class/method/BCI when available; no search verdict is emitted. Diagnostics must distinguish malformed
input, unsupported behavior, missing metadata, limits and interpreted setup fault. Independent repeated
and concurrent runs share immutable metadata and start from isolated state.
`runBytecode` uses declared interpreted checks and owned storage only. Reject a non-null builder
Invariant, explicit generic stateStore/stateStoreFactory, or symmetry-specific configuration before
execution; do not invoke arbitrary model/property/store callbacks or silently ignore them. Strategy,
maxStates/maxTime and supported CBS options retain their existing meanings. Default bytecode storage
is EXACT. In101 add `Builder.bytecodeBitstate(int bits, int hashFunctions)` for an owned one-filter
selection with positive validated arguments; reject this configuration on ordinary run(Program).
CLI keeps its existing exact|bitstate flags and routes to the corresponding owned bytecode selection.
No caller-supplied StateStore adapter is admitted merely because it has a familiar class name.

Annotations live in `dev.samhb.interleave.bytecode` and are parsed as metadata:
`@Model` on one concrete fixture class; `@Actor(id = 0)` on public instance `()V` methods; dense unique IDs
0..n-1, 1..8 actors; `@Check(when = ALWAYS | FINAL)` on public instance `()Z` methods, default FINAL.
Use CLASS retention, TYPE/METHOD targets respectively. `Actor.id()` is int without a default;
`Check.when()` returns the nested public enum `Check.When { ALWAYS, FINAL }` with default FINAL. In the first profile the fixture directly extends
Object; static helpers have exact known targets. Inherited annotated entry points are rejected; 95
allows internal inheritance/dispatch without silently changing entry-point discovery. One annotation
role per method. Sort checks by declaring binary class, name, descriptor for deterministic failure order.
Construct the shared fixture via interpreted public `()V` constructor. All actor receivers are this same
fixture, with independent stacks. Checks may be absent. Setup finishes before actors/checks begin.

CLI: `--bytecode <binary-name> --class-path <directory>`, mutually exclusive with built-in/all/file
selection; require both exactly once. Reuse strategy/store/bound options and defaults subject to
phase admission: 94C–97 exact DFS only; 98 adds CBS; 99 adds DPOR with reported fallback; 101 adds
BITSTATE for supported strategies. Omitted strategy selects only currently supported bytecode
strategies. Explicit static POR, symmetry, unavailable storage/strategy or incompatible source-only
options reject before search; do not silently substitute them. No new CLI resource flags are required;
use documented BytecodeLimits defaults. Library callers can select immutable custom limits. Omitted store selects EXACT before101;
101 may run the existing exact/bitstate selection matrix. Legacy input-mode defaults remain unchanged.

## First-release language and execution seams

94A/B/C support int/boolean values and field/static access, constants, local loads/stores, integer
arithmetic/bitwise/shifts/conversions i2b/i2c/i2s, comparisons, conditional/unconditional branches,
tableswitch/lookupswitch, loops, known static application helpers, constructor invokespecial, return,
reference locals/fields/null/identity comparisons needed for fixture lock wiring, and synchronized
blocks/methods. Stack operations must obey verifier categories, including wide local-index forms.
Only setup allocates: one fixture plus distinct plain Object monitors via Object.`<init>()V`; no
arbitrary application helper-object construction, actor `new`, arrays, general virtual/interface calls,
explicit user `athrow` or general user handlers until their owning phases. String ConstantValue metadata
may be retained initially; executable String operations/constants requiring modeled String storage
reject until 96 introduces immutable exception-message constants and 97 the remaining String profile. Class literals for arbitrary application use reject; internal
class-monitor identity for static synchronized helpers is supported without reflection.

Every run owns a bytecode state: heap/statics, actor frames (method/BCI, locals, operand stack), pending
exception/unwind/terminal events, monitors and counters. Instruction decoding is immutable. Define one
opcode executor used by both modes; selection/classification must be pure and return executable next
choice, blocked monitor or pending terminal event without charging fuel or committing effects.
Bytecode branches/backedges update real continuations, never source scheduling ordinals.

Internal seam (94A/B; names describe obligations, not new public extension points): frozen metadata +
state + actor + mode → isolated transition result containing committed state, executed opcode locations,
observable effects, or pending terminal/cutoff evidence. Search and replay call this same executor.
A bytecode-specific search adapter owns enabledness and canonical keys. Reuse stores and result types
where honest; do not force bytecode into source Forward SourceStep lists or weaken existing source
validation. An exceptional terminal snapshot may preserve earlier committed bytecode effects; legacy
StepTransition's no-successor failure contract remains unchanged for source/JSON.

## Scheduling, batching and observations

Every executed opcode costs one unit of its execution owner's instruction fuel, including a faulting
opcode. Calls cost their invoke opcode plus executed callee opcodes. Implicit operations/intrinsics
have explicit additional costs specified below; probes and disabled monitor attempts cost zero.
From94A, trusted Object.<init>()V costs invoke1 plus intrinsic1; it adds no payload fields. Class-monitor
entry/exit and initial pending VM-fault metadata propagation cost zero; actual cleanup opcodes cost1.
Insufficient fuel for an existing next opcode creates a selectable cutoff event before testing that
opcode's dynamic blocking/fault conditions. Thus zero-fuel monitor entry is exhaustion, not deadlock;
a disabled monitor attempt with sufficient fuel still consumes nothing. A pending normal termination
has no next opcode and can complete at zero fuel. Counters measure executed work, not scheduling transitions. For unit-cost opcodes, exactly N instructions are permitted;
normal return at N succeeds, the attempted next opcode yields a cutoff before N+1.
Multi-unit intrinsics from94A onward preflight invoke plus intrinsic cost and may cutoff with positive
remaining allowance, omitting the entire invoke without fuel/payload/identity charges. The omitted
location is the invoke BCI; record total required work and remaining allowance. Pure preflight can
inspect fault conditions to select the fixed fault cost; probes never execute callbacks.

Required boundaries: shared field/static/array accesses, monitor acquire/release, reference publication,
atomic operations, termination, exception delivery/terminal failure and actual exhaustion. Shared `x++`
retains distinct read and write choices. Local prefixes may be batched only when they cannot change
another actor's enabledness, check value/error, dependency, resource outcome or terminal outcome.
Reference mode offers a choice at every opcode boundary and evaluates ALWAYS after every executed
opcode. BATCHED executes at most one observable operation plus justified local work and stops before
the next observable or terminal event. Initial/pure-local prefixes are permitted under the same rule.

**Yield before terminal events**, even if a local sequence discovers a fault or budget exhaustion.
Save a pending event at an actual BCI, yield, then let a scheduler selection deliver it. Never charge or
mutate state in a classification probe. Reference mode uses compatible pending-event semantics so
another actor can run before termination, uncaught failure or cutoff is selected. Implicit synchronized
entry/exit and exception propagation are explicit events with real invoke/return/throw locations;
extra events have zero opcode fuel, and every enabled zero-cost event strictly advances pending-event
state. Cleanup opcodes still consume fuel. If cleanup exhausts fuel, end the prefix INCOMPLETE at the
omitted cleanup opcode; never claim monitors were released or the exception fully delivered.

Sharedness: derive exclusive actor reachability transitively from statics, the shared fixture and
all actor roots, including frame references/pending values. Fixture/statics are shared roots. Unknown
ownership means shared. Before reference publication, promote the reachable subgraph; publication
is observable even if the containing object had been local. Once shared, remain shared for that
execution, and encode this history. Aliases/cycles require a terminating visited-object traversal;
account its cost in instrumentation. No heap renumbering, GC or reuse. Lock semantics and errors
remain observable regardless of object locality.

Verify each check's entire reachable call graph against a pure allowlist. Reads and pure supported
operations are allowed; target mutation/allocation/synchronization, I/O, host callbacks and inspection
of actor frames/history/resource counters reject before search. Observer scratch frames are separate
and cannot escape. ALWAYS runs initially and at property-relevant boundaries, including cleanup and
exception effects; batching can omit checks only when both value and error are invariant. FINAL runs
only when every actor has normally terminated. False → VIOLATION/CHECK_FALSE. Check exception or
instruction/depth exhaustion → INCOMPLETE/OBSERVER_ERROR or OBSERVER_LIMIT. An exception raised during
an observer aborts that invocation immediately; observer execution does not enter target exception
handlers or allocate a target Throwable. This restriction remains explicit after96. Check fuel resets per
invocation; setup/actor fuel is unchanged. Evaluate ordered ALWAYS checks at a committed position
before deadlock/completion and before selecting a competing pending terminal. Never turn an observer
failure into PASS. An earlier established finding remains recorded if a later branch is incomplete.

## Faults, monitors and verdicts

Model null field/monitor access as NullPointerException and integer divide/remainder zero as
ArithmeticException. Preserve prior instruction effects; the faulting opcode follows JVM semantics
and consumes fuel. Required cleanup/unwinding runs, then an uncaught actor exception is
VIOLATION/UNCAUGHT_EXCEPTION, distinct from CHECK_FALSE. Capture actor, exception type, throwing
class/method/descriptor/BCI, and interpreted frames; do not use a host stack as target evidence.
Setup fault/limit is a load diagnostic; observer fault/limit is INCOMPLETE. Unsupported user handlers
reject before exploration until 96; compiler cleanup catch-all handlers are recognized by verified
control/data flow in 94A/B, not a compiler-name heuristic or arbitrary catch-all exemption.

Monitor state is identity → owner/depth; reentrant acquisition increments, release decrements, final
release enables competitors. Disabled acquisition has no effects/fuel. Model null/nonowner release
faults; malformed structured-lock behavior rejects at validation. Synchronized method entry must
acquire before body execution, including static helpers' stable class monitors. Normal/abrupt return
releases the method monitor exactly once; nested compiler cleanup and method cleanup do not duplicate
release. A deadlock is a nonterminal state with blocked actors and no runnable opcode/pending event;
record concrete ownership and wait-for relationships. Do not call a fuel cutoff or all-asleep DPOR
position a deadlock. No fairness/liveness claim.

A cutoff ends that prefix, explores sibling alternatives and records actor/reason/omitted location/
limit and last committed state. Never freeze exhausted actors and continue that execution. Any
unexplored bound or resource interruption prevents an exhaustive absence verdict; retain genuine
positive findings with incompleteness metadata. Busy loops/retries may exhaust fuel, while bounded
terminating loops can succeed. Equal values with different remaining budgets are different states.

## Limits, cells and identity

Defaults are provisional settings; 94B measurements must review them before 94C.

| Resource | Default | Hard ceiling | Scope |
|---|---:|---:|---|
| Actor instruction fuel | 256 | 4096 | Each actor, entire execution |
| Allocations | 128 | 4096 | Each allocating actor |
| Heap cells | 8192 | 65536 | Each allocating actor |
| Call depth | 32 | 256 | Each actor |
| Array length | 64 | 4096 | Each array |
| String UTF-16 length | 256 | 4096 | Each String |
| Setup instruction fuel | 4096 | 65536 | Setup only |
| Check instruction fuel | 1024 | 16384 | Each invocation |

Setup has separate allocation/cell allowances with actor defaults/ceilings and separate depth using
actor depth limits. Check depth uses the same ceiling/value on private observer frames. Positive
integer limits at or below ceilings are accepted; zero/negative/overflow/excess values reject.
Allocation/cell charges are permanent to setup/allocating actor even after escape; per-resource checks
occur before object creation. Charge one cell for an object header plus one per inherited instance
field (long/double still one modeled cell), or array header plus length elements. String payload costs
header plus its declared modeled fields plus UTF-16 units; StringBuilder costs header/fields plus
logical capacity units. Throwable suppression/reference payloads and atomic value cells count too.
Do not duplicate charges for aliases or double-charge payloads backed by an already charged modeled
array. Static slots and built-in constants/caches belong to a separate bounded setup-owned table,
charged once against setup heap cells; deterministic materialization cannot depend on actor scheduling.
Constant-table entries do not consume setup's application-allocation count: their identities occupy a
separate tagged namespace, bounded by registry/input caps and setup cells. Count actual interpreted
setup new/array allocations normally. Each class-monitor constant costs one header cell; static slots
cost one cell apiece. Thus deterministic eager wrapper caches do not exhaust the default128 setup
application allocations merely by containing more cached values. 97's registry fixes
payload field/capacity counts and cache identities before enabling those descriptors.

A cutoff identifies the selected executing actor; allocation/cell cutoffs additionally identify
the charged budget owner (setup or original allocator), including mutation of escaped modeled payloads.
Cell limits bound modeled storage, not JVM bytes. Metadata/input caps and a global checker safety
abort protect implementation overhead; report external safety aborts honestly as INCOMPLETE. Allocation,
cell and depth cutoffs name the omitted allocation/invoke location, reason and configured limit.
For simultaneous failures use fuel first, then opcode dynamic validity (e.g. negative length), then
array/String length, allocation count, cells, depth as applicable. Failed allocations charge no cells
or identities; an executed allocating opcode consumes fuel even if it cannot allocate. No exception
object allocation is required for initial modeled VM faults: bounded pending-fault metadata belongs
to actor state, with no fabricated user-visible Throwable before 96.

Identity = `(setup | actor-id, allocation-sequence)`; sequences start at zero in each namespace and increase only on successful allocation.
The setup fixture is sequence0; subsequent setup allocations follow interpreted order.
Built-in class monitors/caches use distinct tagged deterministic constant namespaces. Encode every
future-relevant heap/alias/static/frame/BCI/stack/pending-event/monitor/sharedness/counter/sequence
value with typed length-delimited versioned keys. Exclude diagnostics that cannot affect execution.
Retain allocator charge histories even after objects become unreachable; no state merging by values
alone or identity renaming. Evidence and replay retain actual identities.

## Exact search, CBS and DPOR

DFS exact state enumeration is the execution reference; EXACT membership must compare full canonical payloads after hash filtering. CBS in 98
uses the production scheduling granularity, last actor and minimum preemption cost per complete state.
A preemption is switching away from a runnable previous actor, not a switch forced by blocking or
termination. Minimum-cost dominance for positive behavior does not establish cutoff-equivalence:
retain uncovered cost-context obligations for prefix cutoffs before pruning a dominated position.
No dominated path may turn a bounded/incomplete traversal into an exhaustive absence claim. Compare to independent exhaustive enumeration at the same mode and bound.

99 owns optimized bytecode DPOR. Admission requires acyclic reachable control flow **and** call graphs,
conservative resource upper bounds within selected limits, complete dependencies and check observations.
Include setup, all dispatch alternatives, intrinsic/implicit costs, exceptional cleanup, errors and
observer execution, including repeated ALWAYS invocations when bounding total checker work. Unknown cost/dependency/metadata or possible exhaustion selects exhaustive DFS
traversal; report requested DPOR, effective DFS and reason. Loops, including later CAS retries, remain
usable through DFS/CBS and DPOR fallback. Do not infer independence from separate actor fuel/budgets.

Independence must preserve enabledness, commutation, observations and terminal outcomes, including
publication, constructor effects and allocation identities. Actual terminal cutoffs are conservatively
dependent with competing choices. Do not append sentinels; detect exhaustion from state when next
execution is attempted. Never reuse source's forward future-step scan for dynamic continuations
without bytecode-specific justification. Retain sleep context in exact visited products, and use a
written preservation argument, not tests alone. An eligible acyclic disjoint-write fixture must show
actual reduction versus DFS; labels without reduction fail 99 acceptance. Broader cyclic DPOR remains
research beyond this set.

## Evidence, replay and consumers

94C owns a distinct `BYTECODE` program/evidence discriminator, never SOURCE spans. Artifact identity
is SHA-256 of a versioned, sorted, length-prefixed manifest of binary names and exact class bytes plus
trusted intrinsic/profile version. Record selected limits and execution mode separately and validate
all on replay; changing debug bytes changes artifact identity. Hash the frozen closure, including
setup/check/dispatch metadata. Host directory paths are not semantic identity.

Evidence records the selected modelClass binary name and validated actor/check entry-point mapping
separately from the artifact digest. Replay must match modelClass and declaration mapping even when
two selections have identical class manifests and even for empty-choice root/interrupted records.
Evidence contains actor choices, actual method/descriptor/BCI and optional compiler line, executed
opcode range/list per scheduling transition, outcomes, immutable snapshots, failure/check/deadlock/
cutoff details and profile/limits. No invented source columns. Implicit events identify their causing
real BCI and event kind. Root check failure accepts empty choices. Strict replay rejects artifact/modelClass/declaration,
profile, limits/mode mismatch; disabled/foreign choices; changed outcomes/monitor ownership/checks;
trailing choices after earlier terminal observations; wrong omitted cutoff/limit; and fabricated
completion/deadlock. Replay captured external interruptions only as authenticated prefixes, retaining
original interruption and separate observations; do not reproduce wall-clock deadlines as model events.

Use tagged lossless integer/long and floating raw-bit encodings (including NaN payload and signed zero)
when those types arrive. Viewer uses text-safe labels and caps; no numeric coercion through JavaScript
Number for 64-bit values. Extend TraceRecord/TestResult/reporting compatibly with optional bytecode
payloads or version native serialization when shape requires; do not pin old UIDs or relabel legacy
JSON/source. Minimization/source cutoff helpers remain source-only unless separately specified.
All supported phase additions ship with strict replay, library, CLI, report, normalization and browser
fixtures in the same PR. Requested/effective strategy and fallback reason are visible in all consumers.

## Library profile and exclusions

97 defines the finite descriptor registry, modeled payloads, instruction costs and fault semantics;
100 owns narrow atomics. Supported user callbacks are interpreted through validated dispatch, never
executed by host JDK library code. Integer caching is exactly -128..127; other caches are explicit.
Volatile reads/writes have ordinary SC visible-access behavior with preserved metadata, without JMM
claims. Collections, wait/notify, dynamic threads, reflection, I/O, native/JDK fallback, lambdas,
unsupported bootstraps, bytecode static POR/symmetry and weak memory stay deferred. String concat
bootstrap support is restricted to 97's exact recipe; no general invokedynamic support.

## Verification and measurement gates

Each implementation starts with a fresh fetched merged-main SHA and baseline; historical test/PIT
counts are not baselines. Full build, tests and strict Javadoc; affected mutation census and unchanged
default PIT gate; relevant built-jar/CLI/report/replay/browser compatibility checks are mandatory.
Preserve inherited kills and distinct regressions. Report scoped versus default totals, surviving
fingerprints and NO_COVERAGE separately; uncovered/timeouts/run errors are not killed mutants.
All local Gradle runs include `--max-workers=1 -PpitestThreads=1`; CI stays parallel.

Independent oracles: targeted JVM sequential differential fixtures and fixed-seed generated bounded
sequential programs with shrinking/saved regressions. JVM identity-hash integers and implicit message text are implementation-specific: compare their
specified consistency/type/effect contracts rather than requiring host numeric/text identity.
JVM execution is a **test oracle process only**,
never a production fallback. Concurrent reference enumeration is test-only and independent of batch
classification, optimized admission and canonical encoding. Compare normalized sets of violation/fault
identity, relevant final shared observations, deadlock ownership/wait relationships and cutoff
actor/reason/omitted location/limit. Do not demand equal traces, transition counts or local snapshots.

94B early gate: same lost-update/two-lock programs and limits in REFERENCE/BATCHED; equal normalized
observable outcome sets required. Measure unique states, executed transitions, key-encoding calls and
bytes/time, elapsed time, retained memory, sharedness traversal frequency/objects/edges and states
differing only in historical flags. Record hardware/JDK/compiler/flags, source+artifact SHA, seeds,
warmup/forks, raw runs and variability; do not invent percentage targets or silently substitute heap
allocation for retained memory. 94B must report actual zero/not-applicable history-only metrics if the setup-only heap cannot exercise
nontrivial publication; it must not construct unsupported fixtures to obtain nonzero results. 95 owns
the nontrivial actor-local graph/publication/allocator adversarial cases and repeats those measurements.
Costs/proof gaps require investigation before 94C; changing approved
semantics to improve numbers requires a new decision. Measure normalized terminal pending-event
outcomes, not just verdict strings.

Mandatory adversarial corpus: exhaustion competing with a violation, pure local infinite computation,
fuel-sensitive shared branches, exact-limit return, cyclic/aliased publication, reversed actor
allocation ordering, charges after escape, heap exhaustion, nested/reentrant cleanup and unknown
metadata. Apply each case when its behavior becomes supported: 94B covers terminal/fuel/monitor/check
cases; 95 adds allocation/publication/history cases. Add general exceptions, primitive edge values,
observers and atomics at their owning phases.

Compiler compatibility: keep fixture sources, explicit compiler/release commands and expected
support in Git; generated class files live under build/test outputs, not committed binary artifacts.
Maintain supported/rejected Java 17/21/26 fixtures for string switches, enums,
array clone, nestmates, assertions, try-with-resources, records and pattern switches. Rejection is valid
where it matches phase support; a class-file version alone promises no unsupported construct. 101
pins an actual JPF revision, probes accepted versions/required constructs, then chooses a mutual
compilation release without lowering Interleave's Java 17 input floor. Compare normalized outcomes
and explain scheduler/SC differences. If incompatible, record JPF gate **blocked** and scope not fully
accepted; no fabricated compatibility claim. jcstress/Lincheck may supplement, never replace exact
reference. This is future implementation evidence, not an authoring prerequisite.

## Source-inspection evidence and proof obligations

[verified, planning main] `core/Program` is final and distinguishes source by digest; `SourceStep` and
`Configuration.successorAt` enforce forward source continuations. `TransitionExecutor.execute`
special-cases SourceStep and generic failures produce no successor. `dpor/DporExplorer.dporDfs`
scans later list steps before sleeping competitors; its unknown-observation path uses exhaustive DFS.
These facts establish integration constraints, not bytecode compatibility. No `.codegraph` index or
existing domain authority was present in the isolated tree during inspection.

[approved decision] Interpret target code only; use owned bytecode continuations and one opcode
executor. Batching requires a stuttering/observation argument covering resource and terminal events;
99 additionally needs an independence/sleep-coverage preservation argument. Each names assumptions,
state relation, event matching, counterexample tests and limits. A green suite alone is not a proof.

[unverified] All bytecode performance/sharedness measurements, optimized reduction and JPF compatibility.
[deferred research] Frozen exhausted actors, fuel-free cycle detection, broader cyclic DPOR and
initializer allowlists. None is a release requirement or implied optimization opportunity.

## Authoring and implementation workflow

The [specification review record](bytecode-specification-review.md) separates actual authoring
checks, reader findings and CodeRabbit outcomes from future runtime acceptance.

Follow the repository's spec template: objective, scope, non-goals, invariants, requirements, edge
cases, acceptance, baseline/rollback, design, current state, tests, constraints and skills. Reuse this
contract rather than duplicate semantics. Each milestone includes intended paths; new names are
planned, not claims of existing classes. Run actual CodeRabbit after completing each specification,
independently review dependencies/PR size/claims, and use fresh-reader testing from doc-coauthoring.
Record actual completion or quota/error status through agent-setup; ordinary review cannot stand in
for required CodeRabbit. Never use paid credits or change account/configuration to force reviews.

This specification-only PR validates documentation/links/structure and records review evidence; no
production tests/PIT/interpreter benchmark claims. Each later implementation PR publishes its own
`docs/measurements/<phase>-bytecode-*.md` ledger, updates phase status and support matrix, includes
`Co-authored-by: Codex <noreply@openai.com>`, reports its PR link and stops. Sam supplies CI/review/merge
updates; do not watch CI or begin the next milestone before that update.
