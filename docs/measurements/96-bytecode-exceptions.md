<!-- Spec 96 evidence distinguishes approved semantics, source facts, actual measurements and remaining gates. -->
# 96 — General supported exceptions

## Status and reproduction

Implementation is based on freshly fetched merged main `6a571844df9f4580dec8f57cd0acf01dcbe3fa06`
(PR142/95). Local runtime, independent-oracle, consumer and mutation acceptance checks pass.
CI/manual review and merge acceptance remain pending; 97+ is outside this delivery.
[Spec 96](../specs/active/96-bytecode-exceptions.md) and the [shared contract](../plans/bytecode-contract.md)
remain normative. #11 and deferred work stay parked.

Feature checkout: sibling `interleave-bytecode-exceptions`, branch `feat/bytecode-exceptions`.
Untouched detached baseline: `interleave-96-baseline`. Raw logs, PIT XML, compiled-class SHA256
manifests, review NDJSON and measurement samples: `interleave-96-evidence`. The original dirty
checkout is preserved. All local Gradle runs use `--max-workers=1 -PpitestThreads=1`; CI parallelism,
mutation operators/floors/resource gates and existing tests remain enabled.

## Interfaces and semantic boundaries

The existing public loader/limits, exact DFS runner, CLI, reports and replay remain the seams.
`BYTECODE-SC-3`, canonical encoding3 and evidence schema2 authenticate the exception profile.
Legacy schema1 SC1/SC2 remains displayable; current strict replay rejects older semantic profiles.
New cutoff data names the permanent budgetOwner separately from the executing actor. A genuine
merged95 native cutoff (359 bytes, checked-in provenance/hash fixture) deserializes with absent owner
remaining null; the retained five-argument public constructor does not guess an owner. Source/JSON
model behavior and source continuation validation remain unchanged.

The loader uses JDK26 Class-File metadata, a closed hierarchy and descriptor-level intrinsic targets.
It freezes exact validated targets for both runtime dispatch and required constant installation,
including inherited interface calls. No target code executes through the host JVM. The immutable
regular-file directory classpath precondition remains; concurrent replacement is outside that contract.

Typed/catch-all table order, finally/rethrow, null throws and method monitor unwind are interpreted.
General handlers receive target Throwable identities; only verified five-op monitor cleanup can
transport bounded fault metadata. Exact validation messages/causes survive that transport. Unknown
implicit VM messages reject inspection before exploration rather than becoming fabricated nulls.
Observers abort on exception/exhaustion before user handlers or target Throwable materialization;
setup failure remains a load diagnostic.

Throwable has six base cells plus inherited fields and one cell per suppressed reference. Growth
charges the receiver's permanent allocator after escape; nonempty returned arrays charge the caller.
Empty getSuppressed uses one setup-charged constant at namespace8/sequence0. Interned UTF16 literals
use namespace8/sequence1+, cost two cells plus UTF16 units, and spend no application allocation count
or sequence. Unpaired UTF16 units remain lossless. No GC, identity reuse or heap renumbering.

Synchronized intrinsics retain separate entry/body/exit events. Pure full-cost preflight precedes
blocking, blocked attempts retain no plan/fuel, successful entry freezes count/cost, and the selected
body rechecks current allocator budgets. Body cutoff retains its monitor and prior effects. JDK26
NPE.getMessage additionally acquires the receiver when its explicit message is null, despite its
nonsynchronized declaration. A non-null explicit message returns without that lock. The conditional
acquisition matches the installed JDK26 source and an independent JVM blocking harness; the possible
NPE target is forbidden in checks. Unsupported native extended-message behavior is never executed.

## Acceptance and independent evidence

| Requirement | Actual checks |
|---|---|
| R1 | First matching typed/catch-all handler, superclass catches, identity-preserving rethrow, athrow(null), replaced finally; real class-file handler reversal changes JVM/interpreter result1 to2 and rejects old artifact replay |
| R2 | Nested/reentrant cleanup and method monitor release, fault/cutoff during cleanup, exact constructor fuel and original-location materialization cutoff; getter receiver lock and conditional NPE lock produce real two-lock deadlocks |
| R3 | Explicit message/cause identity, disabled suppression, self/null validation, shared empty cache, detached result arrays, numeric suppression order beyond index9; seven TWR variants and reverse-close order over compiler targets17/21/26 |
| R4 | Per-actor/setup/observer accounting, allocator-after-escape growth and body-time recheck, blocked-count/fuel recomputation, held-lock cutoffs, exact pending identities/counts/payloads in canonical state and snapshots |
| R5 | Library/native/JSON, built-jar CLI, reports, viewer, strict replay and tampering, complete ResourceCleanup example, independent instruction enumeration and fixed-seed sequential generation/shrinking/saved regressions |

The current executable tests are in
[BytecodeExceptionTest](../../src/test/java/dev/samhb/interleave/bytecode/BytecodeExceptionTest.java),
[BytecodeExceptionBoundaryTest](../../src/test/java/dev/samhb/interleave/bytecode/BytecodeExceptionBoundaryTest.java),
[BytecodeExceptionConcurrencyTest](../../src/test/java/dev/samhb/interleave/bytecode/BytecodeExceptionConcurrencyTest.java),
[BytecodeReplayTest](../../src/test/java/dev/samhb/interleave/bytecode/BytecodeReplayTest.java),
[BytecodeEncodingTest](../../src/test/java/dev/samhb/interleave/bytecode/BytecodeEncodingTest.java),
[BytecodeReleaseTest](../../src/test/java/dev/samhb/interleave/bytecode/BytecodeReleaseTest.java) and
[BytecodeGeneratedTest](../../src/test/java/dev/samhb/interleave/bytecode/BytecodeGeneratedTest.java).

| Requirement | Current test symbols |
|---|---|
| R1 | `BytecodeExceptionTest.typedHandlerAndFinallyMatchJvm`, `BytecodeExceptionTest.orderedHandlersRethrowAndNullMatchJvm`, `BytecodeExceptionTest.handlerOrderIsArtifactBoundAndMatchesJvm` |
| R2 | `BytecodeExceptionTest.nestedUnwindReleasesExactMonitorDepth`, `BytecodeReplayTest.cleanupFuelCutoffRetainsFaultAndHeldMonitor` |
| R3 | `BytecodeExceptionTest.tryWithResourcesKeepsPrimaryAndSuppressed`, `BytecodeExceptionTest.resourceFailureVariantsMatchJvm`, `BytecodeExceptionBoundaryTest.longSuppressionListRetainsIdentityOrder` |
| R4 | `BytecodeExceptionConcurrencyTest.suppressionRechecksOriginalAllocatorAtBody`, `BytecodeExceptionConcurrencyTest.escapedActorPayloadKeepsAllocatorAndCallerSeparate`, `BytecodeExceptionConcurrencyTest.blockedGetterRecomputesCountAndFuel`, `BytecodeEncodingTest.intrinsicContinuationIsLossless`, `BytecodeExceptionBoundaryTest.suppressionArrayResourceCutoffsRetainReceiver` |
| R5 | `BytecodeExceptionTest.exceptionIdentityAndConsumersReplayStrictly`, `BytecodeReleaseTest.publicBytecodeLoaderAndRunnerWorkFromJar`, `BytecodeReleaseTest.bytecodeViewerUsesFreshProducerArtifacts`, `BytecodeGeneratedTest.generatedExceptionsMatchJvmWithShrinking` |

The two-resource independent JVM output is `21:body:2:second:first:true:true`; both interpreter modes
satisfy its declared checks. The suppression fixture uses nine allocations/47 cells (six six-cell
Throwables, three three-cell arrays, two appended references), cross-checked against actual heap
entries/headers. Twelve distinct suppressed errors preserve numeric identity order; actor allocation
is fourteen headers/98 cells (twelve six-cell errors and two thirteen-cell arrays). A Throwable-derived
fixture consumes11 setup cells, independently counted as eight fields and three headers; allowance10
rejects. Setup/caller/escaped-owner cutoff tests strictly replay and reject owner tampering.

Fixed seed `0x962026` generates twelve bounded sequential exception programs with deletion shrinking
and saved failures, alongside the existing generators. The instruction oracle derives enabledness,
fuel, keys, sharing roots and normalized outcomes without production probe/planner/encoder helpers.
JVM oracle processes and their concurrency harness are test-only; they are never production fallbacks.

Canonical framing adds three absent tags per execution. The unchanged setup/two-actor encoding
fixture retains140 states/246 edges/247 encodings; nine extra bytes per key gives121274 encoded bytes
and68698 retained bytes. External derivation and direct enumeration cross-check these literals.
A separate independent decoder covers every suspended intrinsic argument/count/cost/monitor/result.

## Fresh baseline and mutation gates

Fresh measurements before production edits at 6a57184:

| Scope | KILLED | SURVIVED | NO_COVERAGE | Total | Result |
|---|---:|---:|---:|---:|---|
| Default | 455 | 13 | 0 | 468 | Pass, 5m52s |
| Affected | 4951 | 633 | 598 | 6182 | Pass, 43m21s; no resource detections |

Baseline build/strict Javadoc passes 2127 tests (52s). These are fresh96 observations, not95's assembled
counts. The exact affected census uses unchanged targetTests `dev.samhb.interleave.*` and:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 pitest '-PpitestTargetOverride=dev.samhb.interleave.bytecode.*,dev.samhb.interleave.BytecodeTraceJson*,dev.samhb.interleave.ResultJson*,dev.samhb.interleave.core.BytecodeEvidence*,dev.samhb.interleave.InterleaveRunner*,dev.samhb.interleave.TestResult*,dev.samhb.interleave.TraceRecord*,dev.samhb.interleave.search.TraceReplayer*,dev.samhb.interleave.report.BenchmarkHarness*,dev.samhb.interleave.report.BenchmarkResult*,dev.samhb.interleave.report.ReportWriter*,dev.samhb.interleave.report.SoundnessAttestation*,dev.samhb.interleave.cli.Main*'
```

The first focused Throwable mutation run failed R6:204 KILLED/54 SURVIVED/7 NO_COVERAGE/**1 MEMORY_ERROR**
(total266). Negating a contiguous-key loop guard made it unbounded. Replacing it with finite actual
field iteration fixes that resource failure without changing timeouts or gates. The next actual run
passes242 KILLED/52 SURVIVED/7 NO_COVERAGE (301), no resource detections. That run predates the final
independent continuation decoder and extra suppression-bound tests; the completed census below includes them.
Neither uncovered mutants nor resource detections are killed mutants. No 100% kill claim is made.

## Initial publication mutation comparison (`45b5f5a`)

One actual completed affected census passes R6 in 70m 08s (baseline 43m 21s):
**5683 KILLED /771 SURVIVED /604 NO_COVERAGE =7058**, with zero timeout/memory/run-error detections.
This is one executed report, not a composite or a whole-project mutation claim. Scoped R5/R7 remain
inapplicable; the separate default scope passes its unchanged gates. No thresholds/operators/resource
settings were weakened. The scoped warning about CanonicalEncoder equivalence reasons is inherited;
that class is outside this affected scope, and no general equivalence assertion is added here.

| Selected class | KILLED | SURVIVED | NO_COVERAGE |
|---|---:|---:|---:|
| BytecodePlatform | 51 | 28 | 2 |
| BytecodeStrings | 62 | 6 | 1 |
| BytecodeThrowables | 267 | 32 | 2 |
| BytecodeThrowables.Call | 1 | 0 | 0 |
| BytecodeState | 192 | 22 | 3 |
| OpcodeExecutor | 451 | 29 | 5 |
| BytecodeEvidence outer record | 58 | 16 | 0 |

The outer-record row excludes its nested records; class totals never substitute for aggregate totals.
Raw XML and per-class counts remain in `affected-census.xml` and `final-mutation-by-class.json`.
New-module residuals include trusted-metadata loading/closure guards and healthy-JDK error branches,
constant discovery/boundaries, and Throwable payload/continuation details. They remain residuals, not
killed or blanket-equivalent mutants. The two uncovered Throwable sites are the unsupported-body
failure branch, which admitted descriptors cannot select; this does not reclassify them as kills.

The diagnostic reuses the existing 94C normalized-disassembly helpers, retaining branch/handler
structure and mutation fingerprints. It excludes changed/moved methods, bootstrap-dependent generated
record methods and synthetic lambdas, whose ordinals are unstable. Of the baseline, 1758 kills/177
survivors/64 uncovered lie in matched non-lambda bodies; 3193 kills/456 survivors/534 uncovered are
outside that matching claim. This is not a proof of semantic equivalence for unchanged callers.

Two initial comparison flags were independently adjudicated:

- Old sharing lambda `lambda$refresh$3` traverses frame locals; SC3 inserts an intrinsic-argument
  lambda at that ordinal. Disassembly/source maps the old local/stack roots to new `$4`/`$5`, and
  both corresponding root-removal mutants remain KILLED. Same generated name/body was insufficient
  identity evidence. No tests were removed to obtain that result.
- `BytecodeLoader.typeAccess`, NonVoidMethodCallMutator index23/block7, replaces `ClassDesc.equals`
  with false, adding `classAccess(Object)`. In95 that path throws an incidental missing-Object NPE;
  in96 Object has trusted public metadata, so the extra access check is a no-op. A checker-internal
  probe (no target class loading/execution) confirms baseline NPE versus current acceptance. This
  formerly killed guard is explicitly recorded as a changed-metadata-context survivor, not a kill.

The remaining 1757 matched inherited kills stay killed, with no unexplained loss. The two explicitly
remapped lambda kills are confirmed separately. All excluded and surviving identities remain in raw
reports; no one-to-one preservation claim is made for changed/bootstrap-dependent methods. Compiled
runtime SHA256 manifests at `45b5f5a` are identical between the measurement run and its publication
census. The review correction below changes implementation code; those historical timing samples
are not presented as measurements of the patched binary.

## Initial publication reference/batching measurements (`45b5f5a`)

The reused 94B runner measures the final runtime in three fresh JVM forks, two warmups per fixture/
mode and three alternating repetitions:36 samples. Host: Apple M4,10 cores,16 GiB; OpenJDK26.0.2.1,
Mac OS X/aarch64, fixed512MiB heap, SerialGC with full compaction (`MarkSweepDeadRatio=0`). Each fork
first enforces known independently normalized outcomes. Inputs/limits/source SHA/artifact manifests and
raw samples are retained in `profile-final/fork-{1,2,3}`; `profile-final-summary.json` records all ranges.

| Fixture/mode | States / transitions | Elapsed ms median (min–max) | Retained heap bytes median (min–max) |
|---|---:|---:|---:|
| lost-update/REFERENCE | 140 / 246 | 4.723 (3.130–5.860) | 96984 (96856–98592) |
| lost-update/BATCHED | 60 / 98 | 1.918 (1.206–3.275) | 44232 (44048–44528) |
| two-lock/REFERENCE | 475 / 866 | 4.236 (3.046–6.993) | 478128 (478056–478936) |
| two-lock/BATCHED | 116 / 196 | 1.458 (1.089–1.651) | 123568 (123568–123568) |

| Fixture/mode | Encodings / encoded bytes | Retained key bytes | Sharing traversals / objects / edges |
|---|---:|---:|---:|
| lost-update/REFERENCE | 247 / 121274 | 68698 | 1 / 4 / 0 |
| lost-update/BATCHED | 99 / 47188 | 28678 | 1 / 4 / 0 |
| two-lock/REFERENCE | 867 / 717466 | 392818 | 1 / 10 / 6 |
| two-lock/BATCHED | 197 / 162586 | 95686 | 1 / 10 / 6 |

Counts are stable across all nine samples per fixture/mode. Sharing promotions and states differing
only in historical sharing are zero in these setup-only fixtures; that does not establish zero cost
for actor-created graphs. Encoding time is retained in the raw samples separately from elapsed time.
Retained heap is a post-GC live-heap delta with explorer/result reachable, not process RSS or peak
memory. Small timing differences vary with JVM warmup and system load. These common-path fixtures
do not measure every new Throwable body or justify a percentage target/default-limit change.

The publication build/strict Javadoc passes 2168 tests with zero failures/errors. Private-member strict doclint
passes all 18 changed production files. Final PIT comparison and review disposition remain below.

## Raw interpreter repetitions

These actual 36 samples make the measurement independent of local log availability. The unchanged
[measurement runner](../../src/test/java/dev/samhb/interleave/bytecode/BytecodeMeasurementTest.java)
uses the shared default limits for both modes. Reproduce each fresh fork with:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 profileBytecode -PprofileFork=1
```

Repeat with fork2 and3; output defaults to `build/reports/bytecode-profile/fork-<n>`.
Source SHA256: lost-update `1721b4fa9ec2937ac9087fdca03b392d6359f99aced3424f02591522344a2ab2`,
two-lock `2aa8585b0a91866e84d423e719b3bfe59b3625463d9e0943b3cf6c6a3a12b634`.
Artifact manifests: lost-update `9d58c9e77933e823eef869ab7305dfe79ce385038214b3ef524a8d05ee5112b4`,
two-lock `bf6e17450cb326635d9614ed87e30cf9aa6c33c58c89414149fc8eb1c96720eb`.

```csv
fixture,fork,repetition,mode,states,transitions,opcodes,instructions,encodingCalls,encodingBytes,encodingNanos,retainedKeyBytes,elapsedNanos,heapBefore,heapAfter,heapDelta,gcCollections,checkInvocations,checkInstructions,sharingTraversals,sharingObjects,sharingEdges,sharingPromotions,historyOnlyStates,outcomes
lost-update,1,0,REFERENCE,140,246,178,178,247,121274,2610827,68698,5860333,7217632,7315976,98344,2,2,13,1,4,0,0,0,2
lost-update,1,0,BATCHED,60,98,102,102,99,47188,591872,28678,2148458,7219280,7263808,44528,2,2,13,1,4,0,0,0,2
lost-update,1,1,BATCHED,60,98,102,102,99,47188,212543,28678,1319792,7219976,7264208,44232,2,2,13,1,4,0,0,0,2
lost-update,1,1,REFERENCE,140,246,178,178,247,121274,1052582,68698,3614875,7220376,7317280,96904,2,2,13,1,4,0,0,0,2
lost-update,1,2,REFERENCE,140,246,178,178,247,121274,1219711,68698,4722625,7220584,7317568,96984,2,2,13,1,4,0,0,0,2
lost-update,1,2,BATCHED,60,98,102,102,99,47188,431080,28678,1622542,7220872,7264920,44048,2,2,13,1,4,0,0,0,2
two-lock,1,0,REFERENCE,475,866,762,762,867,717466,1730174,392818,3963709,7228320,7706448,478128,2,1,7,1,10,6,0,0,2
two-lock,1,0,BATCHED,116,196,374,374,197,162586,562882,95686,1477834,7228552,7352120,123568,2,1,7,1,10,6,0,0,2
two-lock,1,1,BATCHED,116,196,374,374,197,162586,575335,95686,1650500,7228704,7352272,123568,2,1,7,1,10,6,0,0,2
two-lock,1,1,REFERENCE,475,866,762,762,867,717466,3325584,392818,6992792,7228856,7707792,478936,2,1,7,1,10,6,0,0,2
two-lock,1,2,REFERENCE,475,866,762,762,867,717466,1456453,392818,3045542,7229920,7707976,478056,2,1,7,1,10,6,0,0,2
two-lock,1,2,BATCHED,116,196,374,374,197,162586,452546,95686,1587583,7230080,7353648,123568,2,1,7,1,10,6,0,0,2
lost-update,2,0,REFERENCE,140,246,178,178,247,121274,1669620,68698,4838167,7217632,7316224,98592,2,2,13,1,4,0,0,0,2
lost-update,2,0,BATCHED,60,98,102,102,99,47188,640082,28678,2173292,7219528,7263696,44168,2,2,13,1,4,0,0,0,2
lost-update,2,1,BATCHED,60,98,102,102,99,47188,564877,28678,1917709,7219864,7264144,44280,2,2,13,1,4,0,0,0,2
lost-update,2,1,REFERENCE,140,246,178,178,247,121274,2788754,68698,5121292,7220312,7317168,96856,2,2,13,1,4,0,0,0,2
lost-update,2,2,REFERENCE,140,246,178,178,247,121274,996290,68698,3289750,7220472,7317456,96984,2,2,13,1,4,0,0,0,2
lost-update,2,2,BATCHED,60,98,102,102,99,47188,373745,28678,1389833,7220760,7264808,44048,2,2,13,1,4,0,0,0,2
two-lock,2,0,REFERENCE,475,866,762,762,867,717466,1805688,392818,4062000,7227992,7706120,478128,2,1,7,1,10,6,0,0,2
two-lock,2,0,BATCHED,116,196,374,374,197,162586,581001,95686,1515917,7228224,7351792,123568,2,1,7,1,10,6,0,0,2
two-lock,2,1,BATCHED,116,196,374,374,197,162586,373800,95686,1131625,7228376,7351944,123568,2,1,7,1,10,6,0,0,2
two-lock,2,1,REFERENCE,475,866,762,762,867,717466,2039175,392818,4594625,7228528,7706584,478056,2,1,7,1,10,6,0,0,2
two-lock,2,2,REFERENCE,475,866,762,762,867,717466,2199810,392818,4564333,7228712,7707648,478936,2,1,7,1,10,6,0,0,2
two-lock,2,2,BATCHED,116,196,374,374,197,162586,365749,95686,1088708,7229752,7353320,123568,2,1,7,1,10,6,0,0,2
lost-update,3,0,REFERENCE,140,246,178,178,247,121274,1192837,68698,4809333,7217568,7315800,98232,2,2,13,1,4,0,0,0,2
lost-update,3,0,BATCHED,60,98,102,102,99,47188,1686051,28678,3274750,7219104,7263632,44528,2,2,13,1,4,0,0,0,2
lost-update,3,1,BATCHED,60,98,102,102,99,47188,1499584,28678,2696958,7219800,7264032,44232,2,2,13,1,4,0,0,0,2
lost-update,3,1,REFERENCE,140,246,178,178,247,121274,1094621,68698,3129792,7220200,7317056,96856,2,2,13,1,4,0,0,0,2
lost-update,3,2,REFERENCE,140,246,178,178,247,121274,1406133,68698,4487542,7220360,7317344,96984,2,2,13,1,4,0,0,0,2
lost-update,3,2,BATCHED,60,98,102,102,99,47188,299588,28678,1206292,7220648,7264744,44096,2,2,13,1,4,0,0,0,2
two-lock,3,0,REFERENCE,475,866,762,762,867,717466,1765310,392818,3881209,7228768,7706896,478128,2,1,7,1,10,6,0,0,2
two-lock,3,0,BATCHED,116,196,374,374,197,162586,563660,95686,1457583,7229000,7352568,123568,2,1,7,1,10,6,0,0,2
two-lock,3,1,BATCHED,116,196,374,374,197,162586,375080,95686,1121333,7229152,7352720,123568,2,1,7,1,10,6,0,0,2
two-lock,3,1,REFERENCE,475,866,762,762,867,717466,1895292,392818,4235750,7229304,7708240,478936,2,1,7,1,10,6,0,0,2
two-lock,3,2,REFERENCE,475,866,762,762,867,717466,2485696,392818,4946959,7230368,7708424,478056,2,1,7,1,10,6,0,0,2
two-lock,3,2,BATCHED,116,196,374,374,197,162586,397371,95686,1106583,7230528,7354096,123568,2,1,7,1,10,6,0,0,2
```

## Batching preservation argument

This argument is for the admitted SC3 profile and its conservative scheduling boundaries. It does
not establish unrestricted Java semantics or independence for DPOR. Reference and batching use the
same opcode executor; the independent test enumerator supplies separate enabledness, fuel, keys,
sharing closure and outcome projections, not a second JVM implementation.

1. Couple states at corresponding observable boundaries by exact object/alias identities, fields,
   arrays, statics, frames/BCIs/operands, monitor ownership/depth, irreversible sharedness and permanent
   resource counters. Pending fault identity, exact known message/cause, original interpreted stack
   and the entire frozen intrinsic call (declaration, arguments, receiver, cost, monitor and result)
   enter copying and canonical keys. Canonical encoding3 therefore does not merge distinct future work, remaining
   fuel or suspended operations. Constants have fixed identities outside application sequences.
2. The existing conservative heap policy remains: all field/array accesses, allocations, monitors,
   returns and exception/resource/terminal events retain scheduling boundaries. Only local computation
   and root-preserving transport, including loads of already-installed immutable literals, can coalesce.
   Reference stores that overwrite a non-null reference, POP/POP2 that drop references, and reference
   comparisons, arraylength, instanceof, CHECKCAST and bare Object constructor calls retain boundaries. Losing a private root changes later sharing closure: a competing
   allocation can promote that unreachable object, and irreversible sharing history affects future
   state. Eight independent-enumerator regressions cover nulling, integer slot reuse, POP after a
   synchronized factory, a null comparison, arraylength, instanceof, a failing cast and a bare constructor; none filters orphan objects from outcome comparisons. Other actors
   and permitted checks cannot inspect an actor's private operands, instruction counters or pending
   call metadata. Shared writes and publication remain separate operations with sharing closure before
   the next observation. A shared increment still has distinct read and write choices.
3. Resolve each call through the closed hierarchy and actual runtime target. Supported application
   callbacks remain ordinary interpreted frames, including interface calls to inherited Throwable
   implementations. Loading rejects unsupported trusted implementations, even when no application
   class is a candidate (String constants are real runtime receiver alternatives). Thus batching
   cannot silently execute an omitted host callback. Whole-program message admission rejects exposed
   unknown implicit VM messages; it never substitutes null to change a branch.
4. For a synchronized intrinsic, pure preflight reads current suppression count and computes the full
   invoke/body cost before testing blocking. It changes no fuel, stack or heap. A blocked attempt
   retains no plan. Successful zero-fuel monitor entry freezes the arguments and cost; those roots
   replace their former operand roots. The separate body debits that work once and checks the charged
   allocator's current budgets. This recheck matters when another receiver can grow an escaped payload
   owned by the same allocator. The receiver lock prevents any other actor from changing that receiver's
   suppression list between successful entry and body. Body reference writes/allocations close sharing;
   pure getter results retain already-traced reachability. NPE.getMessage with an explicit null
   message uses the same receiver protocol for the JDK26 conditional synchronized block; a non-null
   message takes the unlocked shortcut. Both cases retain exact target-visible message identity.
   The potentially synchronizing concrete NPE target is forbidden in checks. Separate zero-fuel exit releases exactly the
   acquired depth before normal resumption or abrupt delivery. A disabled-writable constructor has no
   internal stack-trace acquisition; its new receiver's payload writes still close sharing.
5. Exception delivery chooses the first matching table entry at the real throwing/invocation BCI.
   The closed application/VM Throwable universe supports the verifier's first-match monitor paths;
   handler/finally opcodes then execute normally. Only the verified five-operation monitor-cleanup
   transport can carry a metadata token: it cannot inspect/publish exception identity, and its lock-slot
   proof establishes a valid release. Every general handler receives a modeled identity. Materializing
   a VM fault charges six cells at its original throwing location. Failed materialization ends that
   prefix without inventing an object, OutOfMemoryError or completed cleanup. Synchronized frame
   unwinding releases only its own method acquisition. Rethrow retains the original identity/location.
6. Fault discovery, actual cutoff, intrinsic entry/body/exit, unwind and terminal delivery are distinct
   selected boundaries. A local fault or fuel failure discovered inside a batch yields before terminal
   delivery; competing actors retain an opportunity to reveal a violation. A body cutoff retains its
   held monitor and prior committed effects, with executing actor and charged owner recorded separately.
   No exhausted actor is frozen and no cutoff sentinel is appended. Every remaining sibling prefix
   remains available to exhaustive DFS; no resource independence assumption is used.
7. ALWAYS checks in reference mode run after each instruction. Coalesced intermediate steps cannot
   alter their permitted shared inputs or errors; boundaries remain wherever they can change. FINAL
   checks occur only after normal completion of every actor. Observer exceptions/exhaustion abort
   before user-handler delivery or Throwable materialization, and their scratch execution never mutates
   target state. Setup has its own allowances and fails loading if an exception escapes.

Inductively group only the local reference steps between these boundaries. They commute with other
actors' steps because they alter no reachable shared state or permitted observation; selected terminal
handling remains outside the group. The corresponding batched operation reaches the same coupled
state, or the same cutoff/fault/check/deadlock outcome and relevant shared observations. Conversely,
each batch expands into those executed reference opcodes and finite events. This preserves normalized
observable outcome sets, not trace lengths, transition counts or intermediate private snapshots. Any
future local-heap optimization, intrinsic or reduction must re-establish these premises.

## Compiler and consumer matrix

The compiler is JDK26 with explicit non-preview `--release 17`, `21` or `26`; the independent runtime
oracle is a separate JDK26 JVM. These observations do not claim testing JDK17/21 host runtimes.

| Construct | Release17 | Release21 | Release26 | Evidence |
|---|---|---|---|---|
| Typed catch/finally | Supported | Supported | Supported | Typed handler/JVM fixture; additional nested/reentrant cases |
| Two-resource TWR | Supported | Supported | Supported | Checked-in resource source, JVM primary/suppression order and both modes |
| Private nestmates | Supported | Supported | Supported | Independent JVM and interpreted value7 |
| String switch | Reject | Reject | Reject | String hash/equals computation remains outside96 |
| Enum machinery | Reject | Reject | Reject | Class/initializer restrictions |
| Array clone | Reject | Reject | Reject | Deferred descriptor, not host fallback |
| Java assertions | Reject | Reject | Reject | Executable assertion-status initializer |
| Records | Reject | Reject | Reject | Record/library/bootstrap support not admitted |
| Pattern switch | Not non-preview Java17 | Reject | Reject | Non-preview21/26 bootstraps reject; preview class files reject separately |


Fresh schema2/SC3 artifacts exercise library JSON, CLI, reports, strict replay and browser rendering,
including caught completion, uncaught failures, setup-owned and actor-owned cutoffs. After installing
the existing locked Playwright dependency in this fresh checkout, the complete inherited source/JSON/
bytecode browser environment passes 337 assertions with zero failures or console errors. Synthetic
legacy normalizer fixtures establish display compatibility, not interpreter/replay equivalence.

## Review and remaining gates

Actual CodeRabbit CLI 0.8.1 completed a full48-file review with 12 findings after an initial recoverable
WebSocket failure. Both attempt logs are retained. All findings were independently checked:

| Finding | Disposition |
|---|---|
| Stale baseline-stage status | Updated status docs |
| Known validation message admission | Exact supported message/cause already modeled; verified with self-suppression regression |
| Validation payload lost through monitor cleanup | Preserve payload through metadata rethrow and materialization; exact message/cause regression |
| Concrete NPE target admission | Include closed trusted runtime alternatives; explicit-message regression |
| Broad initCause name rejection | Restrict prohibited Throwable-parameter family, permit unrelated int overload |
| Special invocation target | Apply JVM special selection before intrinsic admission at runtime; forged ancestor JVM case |
| Missing implicit NPE hierarchy | Already included by trusted closure; unnamed implicit-type cleanup regression passes |
| Rebuilt suppression count list | Count finite actual fields without list allocation or duplicate mutable counter |
| Missing budget owner export | Already exported; setup-owner and cross-actor JSON/replay/tampering regressions pass |
| Schema1 intrinsic events | Outer DTO now rejects them, consistent with viewer |
| Generic finally misclassified cleanup | Not reproduced: five-op classifier excludes invokes; finally getter regression passes |
| First rethrow stage masked by later result | Save/assert first-stage result independently |

Ordinary connected-code review additionally reproduced missing sharing-history outcomes when batching
consumed the final private reference, and established the conditional NPE monitor from JDK26 source
and a JVM harness. Root-loss boundaries and conditional lock/purity regressions now pass. The older95
root-loss premise was insufficient for orphan sharing history; the argument above replaces that premise
for SC3 rather than weakening normalization or removing historical sharing flags.

The default PIT passes455 KILLED/13 SURVIVED/0 NO_COVERAGE (468), with every fingerprint/status
matching the fresh baseline. It was run after production changes, before the final test-only review
strengthening. The completed affected census below includes those strengthened tests.
Actual CodeRabbit verification completed 49 files with 8 findings. Its five major admission questions
were already rejected by existing boundaries: global implicit-message admission (including helper
forwarding), reserved platform names/trusted module metadata, observer mutation and cause-only
constructors. New negative regressions verify helper forwarding and shadow files. Three trivial
findings strengthen example/fixture primary identity/message checks and budget-owner encoding;
all are addressed and the publication build passes afterward. Two actual completed reviews are
recorded through agent-setup, separately from ordinary review. No third review was run for the
verified test/example-only follow-ups. Two fresh documentation readers answered all seven delivery questions and independently
checked ambiguity; the stale authoring-time status and canonical/evidence-version ambiguity were fixed. CI/manual acceptance remains outside local evidence. No JPF compatibility, bytecode DPOR
reduction, unrestricted Java behavior or complete Java Memory Model coverage is claimed.

Initial publication configuration/document validation (`45b5f5a`):127 changed local Markdown links exist; all three changed JS/MJS
files pass Node syntax checks; Ruby YAML parsing passes the workflow and CodeRabbit configuration.
`coderabbit config validate` could not use the current official schema: CLI 0.8.1 reports
`strict mode: unknown keyword: "x-undocumented"`. The prior sandbox DNS failure was retried on the
host; no schema bypass or configuration weakening was used. This limitation does not replace or
invalidate the separately completed actual reviews. Context-store validation reports zero errors and
one existing machine-specific rollout-link warning. CI/manual acceptance is not yet checked.

## PR143 feedback correction

The six review comments are addressed together:

| Comment | Correction and direct evidence |
|---|---|
| Review count | First-review findings2 and6 shared one fix but now have distinct table rows:12 findings,12 dispositions |
| Requirement evidence | R1–R5 now name current test symbols and link their source files; every symbol was checked against source |
| Verifier repeated work | Covering candidates are computed once per relevant BCI, retaining table order and stopping at a universal handler. Immutable ancestor closures are memoized in a thread-safe cache bounded by the frozen hierarchy. `BytecodeExceptionBoundaryTest.manyCoveringHandlersPreserveFirstMatch` checks15 overlapping handlers, twenty division sites and each first-match target against expected table entries; independent JVM/interpreter result378 is cross-checked as78+100+200 |
| Failing cast | `BytecodeBatchingOracleTest.failingCastPreservesTerminalSharingHistories` was red before CHECKCAST became observable. Both reference and batched execution now retain the private and promoted terminal histories with `seen=1`; successful casts conservatively retain a boundary too |
| Nonnull NPE getter | `BytecodeExceptionBoundaryTest.nonnullNpeMessageAvoidsMonitorDeadlock` covers the nonnull shortcut under contention, with an independent JVM harness and an explicit-lock control that does deadlock |
| Exact retained monitor | `BytecodeExceptionBoundaryTest.suppressionArrayResourceCutoffsRetainReceiver` resolves `Fixture.primary` from the final snapshot and asserts that exact identity has owner0/depth1 for both array-length and caller-cell cutoffs |

The integrated build/strict Javadoc passes2171 tests (zero failures/errors); private doclint passes all
five touched production files. The complete source/JSON/bytecode browser matrix passes337 assertions, zero errors.
One actual four-class PIT run (BytecodeHierarchy, BytecodeMethod, BytecodeVerifier, OpcodeExecutor)
passes R6 in13m03s:1016 KILLED/169 SURVIVED/66 NO_COVERAGE (1251), no resource detections.
The same classes at `45b5f5a` contributed991 KILLED/158 SURVIVED/66 NO_COVERAGE (1215) to the prior
executed full census; that subset is not misrepresented as a separate fresh baseline run. All200
matched non-lambda/non-bootstrap inherited kills remain killed; changed methods are excluded from
one-to-one comparisons. No new full or composite mutation total is claimed. The original full/default counts above remain historical executed observations, not
an invented post-correction whole-project run or an implicit composite. Runtime and loader changes
are limited to these reviewed fixes; no new language/strategy phase or default-limit change is selected.
No new CodeRabbit CLI cycle is required for this minor feedback correction; the two completed feature
reviews and this ordinary review are recorded separately through agent-setup.
