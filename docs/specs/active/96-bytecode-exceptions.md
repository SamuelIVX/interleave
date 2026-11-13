<!-- Roadmap #12 / 96: implementation milestone specification; no production implementation in this PR. -->
# Spec 96: General supported exceptions

## Objective

Deliver general supported exceptions as one coherent stage of bounded Java bytecode checking.
**Type: delta. Status: specified, not implemented or measured.** The
[shared #12 contract](../../plans/bytecode-contract.md) is normative for common semantics, limits,
interface signatures, profile exclusions, phase ownership and verification. This spec owns 96 only.

## Scope

Extend bytecode/ handler verifier, OpcodeExecutor, frame/unwind/pending state and Throwable payloads.
Add interpreted supported Throwable hierarchy metadata and exception registry entries, preserving narrow
compiler-cleanup support. Update strict replay, TestResult/TraceRecord/report/viewer failures, examples
and compiler support table. Add BytecodeExceptionTest and try-with-resources resources.
General core-library widening belongs to 97; no class initialization expansion. This phase owns the
minimal immutable UTF-16 String literal/message representation needed for non-null exception messages:
ldc String constants, identity/reference passing and message access only, without String computation.
This phase defines String cells as one header, one length field and one cell per UTF-16 unit,
with identical literal contents interned in a deterministic setup-owned table. String length/heap/fuel
limits apply now;97 references and extends this representation without replacing it.

## Non-Goals

No expansion beyond this milestone's behavior. No host execution of target code, executable application
initializers, dynamic actors, collections, wait/notify, weak memory, bytecode static POR/symmetry,
frozen exhausted actors or fuel-free cycle detection. Later-phase support stays rejected until its
complete implementation and consumers merge.

## Invariants

- Existing explicit/source/JSON/replay/symmetry behavior, validation and mutation policy remain intact.
- Owned state and deterministic identities preserve aliases, resource counters and sharedness history.
- Cutoffs end prefixes; external stops and observer errors remain honestly incomplete.
- All executed target behavior stays interpreted; no host fallback or forged source locations.
- Public selection follows the phase release boundary and never advertises partial consumer support.

## Requirements

1. **R1 — Handler order.** WHEN a supported exception is thrown, THE SYSTEM SHALL select the first
matching exception-table entry for the throwing BCI and modeled type, transfer the exception to handler
stack, or unwind with exact throwing/invocation locations. User athrow(null) SHALL produce modeled NPE.
2. **R2 — Cleanup.** WHEN return/throw crosses synchronized/finally paths, THE SYSTEM SHALL execute
compiled cleanup in order and release held method monitors exactly once. Handler/finally faults SHALL
replace the propagating exception per JVM semantics while earlier committed effects remain.
3. **R3 — Throwable support.** WHEN supported application exceptions/Throwable methods execute, THE
SYSTEM SHALL model constructor/message/cause/suppression state, bounded identities and cells. It SHALL
support restricted try-with-resources with interpreted AutoCloseable.close callbacks; unsupported
callback bodies or platform descriptors SHALL reject before exploration.
4. **R4 — Terminal behavior.** WHEN an exception escapes an actor, THE SYSTEM SHALL emit uncaught
VIOLATION with original throw site and interpreted stack; observer failure SHALL remain INCOMPLETE and
setup failure a load diagnostic. Exhaustion during unwinding SHALL end a cutoff prefix without
inventing completed cleanup. All pending exception/handler/cause/suppression state SHALL enter keys.
5. **R5 — Consumers and oracles.** WHEN exception support is enabled, THE SYSTEM SHALL extend strict
replay/evidence/viewer and reference/batching comparison to caught, replaced, suppressed and uncaught
exceptions, with independent JVM sequential cases and generated shrinking regressions.

## Failure Modes & Edge Cases

- WHEN metadata, descriptors or reachable behavior is unsupported, THE SYSTEM SHALL reject before
exploration with actionable identity/location diagnostics; dynamic supported faults SHALL remain runtime behavior.
- WHEN actor/setup/observer budgets fail, THE SYSTEM SHALL preserve their separate ownership and the
shared priority, exact-limit semantics and omitted operation. A check/setup failure SHALL not become an actor violation.
- WHEN a run stops externally, THE SYSTEM SHALL retain original INCOMPLETE classification and genuine
already-recorded findings. It SHALL not manufacture a reproducible modeled resource cutoff.
- WHEN this milestone exposes new payload/state, THE SYSTEM SHALL include every future-relevant field
in keys and replay validation, including pending terminal events and permanent allocation charges.

## Acceptance Criteria

- R1/R2: overlapping handlers preserve class-file order, superclass catches, rethrow, nested finally,
normal return through cleanup and exceptions within catch/finally match the JVM. No Java-source rewrite
of handlers or host catch invocation substitutes for interpreted bytecode.
- R3: explicit registry for Throwable/Exception/RuntimeException and modeled VM fault types includes
`()V`, `(String)V`, `(String,Throwable)V` constructors where declared, getMessage/getCause,
addSuppressed/getSuppressed plus protected Throwable `(String,Throwable,ZZ)V` for supported
subclasses. Return arrays allocate/charge; self-suppression/null and suppression-disabled
cases match semantics. Stack-trace mutation/printing, cause-only platform `(Throwable)V` constructors (which may call overridden
toString), and arbitrary JDK exception helpers reject. The selected profile does not enable that callback
constructor in97 either; supported message+cause constructors provide explicit message semantics.
- R3: Throwable has header plus message, cause, causeInitialized, suppressionEnabled and
writableStackTrace fields, plus one cell per suppressed reference (six base cells). Interpreted stack
metadata is separately bounded by depth and captured at throw; stack trace APIs stay unsupported.
All supported Throwable methods add one intrinsic unit to invoke fuel; getSuppressed instead
charges max(1,count) intrinsic units. For zero suppressed exceptions or suppression disabled,
getSuppressed returns one deterministic shared empty Throwable[] constant with no call-time allocation
or cell charge. Charge its one array-header cell to setup once, in constant namespace8/sequence0.
For nonempty results allocate a fresh array with header+count cells charged to the calling actor.
Repeated empty calls on the same or different exceptions preserve that shared identity; nonempty
calls return distinct arrays. This follows [OpenJDK26 Throwable.getSuppressed](https://github.com/openjdk/jdk/blob/jdk-26-ga/src/java.base/share/classes/java/lang/Throwable.java). Reject reachable application Throwable subclasses overriding fillInStackTrace()Ljava/lang/Throwable;
before exploration: base constructors can invoke it and an atomic intrinsic would omit its effects.
A JVM fixture whose override writes a field demonstrates the behavior; loader rejection prevents a
false effect-free constructor result. Other supported application callbacks still interpret normally.
Constructors without that override validate then assign within one atomic intrinsic. Shared full-cost preflight applies now: insufficient total fuel omits the entire
invoke at its BCI with no charges, stack change or allocation; exactly sufficient fuel succeeds. Each runtime fault becomes a modeled Throwable with allocator charges when
materialized for handlers; allocation failure yields a cutoff at its original throwing BCI, never a
fake OutOfMemoryError or missing exception. Bootstrap initial-profile faults remain bounded metadata
until materialization is needed. Messages/getMessage return the same modeled String reference.
- R3: two resources close in reverse order, one/two close failures, body failure, suppressed ordering
and suppression disabled are independently checked; callbacks execute on the interpreter, never host.
- R4/R5: exact fuel limit in a cleanup handler and exception replacing an earlier pending fault retain
real BCIs/charges/monitor state. Replay rejects altered suppression or handler order; browser distinguishes
caught continuation from terminal violation and check failure. Re-run the 94B normalized mode gate, including exact/insufficient intrinsic fuel on getSuppressed.

## Refactor Baseline & Rollback

- Pre-change baseline: fetch and record actual merged-main SHA at implementation start; [95](95-bytecode-heap.md) merged, plus the shared contract.
Record fresh full build/strict Javadoc/tests and affected/default PIT fingerprints before production edits.
The authoring SHA and older counts are historical context, never fresh implementation baselines.
- Call-site migration: all paths listed in Scope, plus their existing connected tests and exports;
new profile state reaches executor, encoder, replay and consumers in this same milestone where exposed.
- Preserve existing public signatures/semantics, source forward validation, mutation operators/floors,
resource settings and CI parallelism. No hidden compatibility adapter may bypass validation.
- Roll back/restrict the newly admitted profile coherently if normalized reference outcomes diverge,
state/replay identity omits future behavior, cleanup/evidence is fabricated or legacy compatibility fails.
Do not weaken tests, change bounds to conceal a counterexample or silently enable a later phase.

## Design

Use the shared contract's deep loader/executor/search seams: immutable validated metadata and program,
owned execution state, one opcode executor and mode-aware scheduler, then existing result/consumer
adapters where contracts permit. Keep bytecode continuation identity separate from source forward PCs.
Do not add a generic backend/framework for hypothetical languages. Internal names in Scope are planned;
choose existing equivalent modules at implementation after connected-source inspection.

### Exact Throwable profile

Resolve these descriptors only on the declared trusted Throwable/Exception/RuntimeException hierarchy
and supported VM exception metadata; subclass user constructors are interpreted. Do not synthesize a
constructor that a platform class does not declare.

| Owner | Allowed descriptor |
|---|---|
| java/lang/Throwable and declared supported subclasses | `<init>()V`, `<init>(Ljava/lang/String;)V`, `<init>(Ljava/lang/String;Ljava/lang/Throwable;)V` where declared |
| java/lang/Throwable (protected subclass call) | `<init>(Ljava/lang/String;Ljava/lang/Throwable;ZZ)V` |
| java/lang/Throwable | `getMessage()Ljava/lang/String;`, `getCause()Ljava/lang/Throwable;`, `addSuppressed(Ljava/lang/Throwable;)V`, `getSuppressed()[Ljava/lang/Throwable;` |
| java/lang/AutoCloseable | `close()V` only through validated application implementations, interpreted with ordinary opcode costs |

Supported VM types include NullPointerException, ArithmeticException, IllegalMonitorStateException,
ClassCastException, NegativeArraySizeException, ArrayIndexOutOfBoundsException and ArrayStoreException;
IllegalArgumentException/IllegalStateException support the specified Throwable validation faults.
Include their metadata superclasses and IndexOutOfBoundsException. Explicit application-provided messages retain modeled UTF-16 contents. An implicit VM exception
message may remain a tagged unavailable value only when whole-program conservative analysis proves
no reachable getMessage/other admitted operation can expose it to execution. Unsupported implicit
message inspection, including unknown receiver origin, rejects before exploration; never substitute
null or normalize a value used by application branches. A catch block testing getMessage()!=null for
an implicit fault requires a supported exact message rule or loader rejection. Host stack formatting
alone may normalize in differential comparisons; message-dependent target behavior may not.
This initial profile rejects initCause on all receivers: its cause-overwrite error can call the
replacement cause's overridden toString and cannot be an effect-free atomic fault. Include a JVM
fixture where that callback writes a field and a matching loader-negative test. Explicit message+cause
constructors provide supported cause wiring without this hidden callback.
No getStackTrace/fillInStackTrace/printing/toString/cause-only constructor is admitted by this table.

### Synchronized intrinsic calls

Honor trusted declaration synchronization: Throwable.getCause, addSuppressed and getSuppressed acquire
the receiver monitor, block while owned by another actor and release it on normal/abrupt completion.
Use separate entry/body/exit continuation events at the causing invoke BCI; successful acquisition and
release are observable, reentrant and zero opcode fuel. Disabled entry changes nothing and costs zero.
Recompute full-cost fuel preflight from current state whenever entry is actually selected; a blocked
attempt caches neither count nor cost. Successful entry freezes the body plan/count/fuel cost under
the held receiver monitor; the selected body debits invoke+intrinsic work once. Recheck allocation/cell
availability at body execution against current counters, since other actors may charge the allocator
through other escaped payloads. Never reuse a pre-blocking suppression count. Retain
pending call/monitor-release state in canonical keys and replay. Atomicity describes the body while
holding the monitor, not an uninterruptible acquisition-through-return shortcut. An actor explicitly
holding the exception monitor can therefore block another actor's getSuppressed/getCause call. Add that
competing-monitor regression, including a real lock cycle and cleanup. These synchronizing descriptors
are forbidden in checks even when a particular call would not allocate. Throwable constructor internal
fillInStackTrace uses its new unescaped receiver monitor; model required entry/exit events and reject
application overrides as above, rather than invoking target code on the host.

## Current State

- [verified] Planning main is `2a242c4`; the existing engine has immutable Program, forward SourceStep
continuations and source-specific evidence. The shared contract records inspected integration constraints.
- [approved decision] This milestone and the shared contract define the selected behavior/release boundary.
- [unverified] This milestone's bytecode implementation, measurements, compiler/runtime acceptance and
mutation results. No authoring review establishes those results.

## Tests

`exceptionTableOrderMatchesJvm` (R1), `nestedUnwindReleasesExactMonitorDepth` (R2),
`tryWithResourcesKeepsPrimaryAndSuppressed` (R3), `unwindCutoffDoesNotInventRelease` (R4),
`exceptionIdentityAndConsumersReplayStrictly` (R5). Kill handler-order changes, duplicate release,
missing suppression cells, missing pending state and observer-fault-as-violation mutations.
These are intended behavioral contracts/test names, not tests already implemented or executed.
Use independent expected values/oracles, preserve distinct regression cases and save generated seeds.

## Constraints

- Dependencies: [95](95-bytecode-heap.md) merged, plus the shared contract; sequential publication into main, with Sam's merge update before the next stage.
- Backward compatibility: all existing explicit/source/declarative inputs, results and stores retain
behavior; source evidence is never manufactured for bytecode and native-stream changes are versioned.
- Oracle contract: [shared verification/measurement gates](../../plans/bytecode-contract.md#verification-and-measurement-gates).
- All local Gradle commands include `--max-workers=1 -PpitestThreads=1`. Full strict Javadoc/build/tests,
affected mutation census plus unchanged default PIT gate, and relevant consumers are mandatory during
implementation. NO_COVERAGE is not a kill; report scoped/default totals and inherited residual identities.
- Create `docs/measurements/96-bytecode-exceptions.md` with implementation: fresh baseline/final evidence,
independent checks, raw measurement conditions, review dispositions and limits. Authoring evidence belongs
in the specification-set record, not a fabricated runtime ledger.

## Relevant Skills and Review

- doc-coauthoring: requirements/acceptance completeness and fresh-reader testing.
- domain-modeling / codebase-design: reuse [domain vocabulary](../../../CONTEXT.md) and the shared
small owned interfaces; do not create a competing glossary or source-shaped bytecode abstraction.
- tdd: establish failing behavioral regressions before new production behavior; fixed-seed/shrinking
oracles supplement targeted cases. owasp-security: fail-closed untrusted class metadata and replay input.
- ponytail / ponytail-review: smallest coherent implementation and connected-source review.
- code-review: actual CodeRabbit after this completed specification and before substantial implementation
publication; record completed/quota/error status through agent-setup without inventing completion.
- 94B/95/96/97: review the batching preservation argument for every newly admitted behavior; 99/100/101:
review reduction admission/resource/product obligations in addition to runtime tests.

The specification-only PR runs document/link/structure validation, reader review and required actual
CodeRabbit invocations. It does not run Java/PIT/browser feature checks for code that does not exist.
After publishing this milestone's implementation PR, report its link and stop; do not watch CI.
