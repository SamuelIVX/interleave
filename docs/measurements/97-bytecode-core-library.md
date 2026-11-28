<!-- Spec97 implementation ledger: completed observations and pending gates remain distinct. -->
# 97 — Primitive and descriptor-level core library

## Status and reproduction

Original implementation began from freshly fetched merged main `cccd74197530689df6cd58a2883d90b1b90af983`
(PR143/96). [Spec97](../specs/active/97-bytecode-core-library.md) and the
[shared contract](../plans/bytecode-contract.md) remain normative. This milestone is in progress;
the primitive execution PR is in progress. Complete97 library/release acceptance is not claimed.98+/#11 and deferred work
remain parked.

The feature checkout is `interleave-bytecode-core-library`, branch `feat/bytecode-core-library`.
Fresh baselines run sequentially in untouched detached `interleave-97-baseline`; raw logs/XML,
compiled-class SHA256 manifest and test reports are retained in sibling `interleave-97-evidence`.
The original checkout's roadmap edit and untracked IDE files remain untouched. Local Gradle commands
use `--max-workers=1 -PpitestThreads=1`; CI parallelism and mutation gates remain unchanged.

## Resumption on current main

Sam resumed the primitive PR after the CI-first infrastructure and calibration audit landed.
Freshly fetched main is `3804acd9b4f4168a235387a2727022d3975c26bd`. The original dirty
`interleave-bytecode-core-library` remains intact. Its complete tracked patch and six untracked files
were transferred without conflicts into isolated `interleave-bytecode-primitives`, branch
`feat/bytecode-primitives`, based on that main revision. The current paired workflow, corrected
mutation harness and full acceptance mode are retained; sharding is not enabled by this work.

The single resumed integrated `build javadoc` run passes2194 Java tests (zero failures/errors/skips)
and38 Python tooling tests in64seconds. It executes the previously unvalidated follow-ups: unsupported
helper return-type admission, nontrivial INEG vectors and immediate numeric constants. The126 browser
checks pass against fresh build producers, including raw NaN payloads. Affected private doclint passes
with all groups/warnings-as-errors. No production edit was needed during this integration/review.
External evidence: `resume97-integrated.log`, `resume97-test-counts.json`, `resume97-browser.log`,
`resume97-private.options` and `resume97-private-javadoc.log` under the original97 evidence directory.

The two earlier full affected censuses completed before this transfer:

| Historical population | KILLED | SURVIVED | NO_COVERAGE | Total | Gradle elapsed |
|---|---:|---:|---:|---:|---|
| Original main baseline | 5709 | 781 | 604 | 7094 | 1h29m03s |
| Corrected primitive feature before final test follow-ups | 5930 | 783 | 605 | 7318 | 1h36m25s |

The completed XML and success logs were recovered after the server restart; compiled manifests match.
The wrapper completion JSON did not survive, so its exact exit code is unknown. The separate recovered
status files record that limitation; these are not completed Spec102 execution records and do not
feed its acceptance aggregate. Default baseline/feature reports at the earlier checkpoint both have
455 KILLED/13 SURVIVED/0 NO_COVERAGE (468), with identical semantic statuses. They are historical
scoped/default observations, not fresh complete acceptance for the current pair.

Historical comparison found one matched kill lost in `BytecodeLoader.signature`: removal of its
return-type `fieldType` call. The narrow-descriptor rejection previously killing it became positive
admission in97. The additional unsupported `java.time.Instant` helper return now tests the retained
closed signature contract. Primitive coverage also exposed an INEG survivor and uncovered immediate
floating conversions, addressed by the follow-up vectors. Those tests pass in the resumed build,
but their new complete-population mutation statuses are **not yet measured**. No lost kill is waived
or called preserved on the strength of a green ordinary test.

A separate bounded real-definition probe removes only the return-validation call from the current
compiled `BytecodeLoader.signature`, verifies the replacement with the JDK Class-File API, and places
it first on a disposable child classpath. The public loader rejects the unsupported helper return
with the original definition; the same assertion fails with the call removed. Target fixture code
is compiled externally and interpreted, never host-executed. This reproduces the lost behavior
without running all1027 loader mutants or altering production class files. Raw source/results are
in `resume97-signature-probe` and `resume97-signature-probe.json`. It is a manual mutation probe,
not a fresh PIT population or a substitute for the complete remote comparison.

Fresh complete baseline/candidate default and affected reports, normalized comparison and changed/
unmatched-method dispositions remain the draft PR's remote acceptance gates. The relevant-PR workflow
requests them automatically against immutable revisions. No old/focused rows are spliced into those
populations, and neither NO_COVERAGE nor runtime errors earn kill credit. No further local full PIT
run was launched; the focused arithmetic check below is separate diagnostic feedback. The original archive-order deviation below remains explicit; implementation of102
does not retroactively satisfy it or adopt a changed historical baseline policy.

### Focused arithmetic follow-up

The serialized Spec102 local wrapper runs only `dev.samhb.interleave.bytecode.BytecodePrimitives`,
retaining all eligible tests/operators and both local Gradle limits. It passes in1m39s with
**105 KILLED/1 SURVIVED/0 NO_COVERAGE (106)**. All106 raw identities agree with the historical feature
report. Exactly three statuses improve to KILLED: INEG negation removal and the previously uncovered
`Float.floatValue`/`floating(float)` calls in `constant`. No identity loses a kill in this scoped map.
This is a class-only result, not complete affected/default acceptance or a whole-project score.

The remaining SURVIVED identity is `BytecodePrimitives.constant(Object)Value`,
`org.pitest.mutationtest.engine.gregor.mutators.NonVoidMethodCallMutator`, index5/block0,
removing compiler-generated `java.util.Objects.requireNonNull`. Its caller in `BytecodeMethod` invokes
it only after `constantValue() instanceof Number`; all other numeric pool loads use the raw sidecar.
Thus null is excluded by the documented helper precondition. The residual stays SURVIVED and earns
no kill credit; this reachability disposition is not an addition to the default ratchet allow-list.
No test or gate is weakened to remove it. Evidence: `resume97-primitives-scoped.log/xml`,
`resume97-primitives-scoped-summary.json` and `resume97-primitives-scoped-diff.json`.

The two actual completed CodeRabbit reviews and all dispositions below remain the required CLI
evidence. This resumption changes no executable behavior beyond the already reviewed primitive patch;
current-main transfer, test-only follow-ups and factual evidence updates receive ordinary review.
No third CLI run is claimed or automatically repeated for that limited follow-up. Complete97 library
behavior, CBS, DPOR, atomics and bitstate remain outside this primitive PR.

## Historical fresh baseline at implementation start

- Build and strict Javadoc pass2171 tests, zero failures/errors (69s).
- Default PIT passes455 KILLED/13 SURVIVED/0 NO_COVERAGE (468;358s).
- At that checkpoint the broad affected census was running. Its completed observations and the
  subsequent resumption are recorded below; no counts are inferred from96's reports.

The fresh baseline was launched before production edits. Build/default reports were archived before
those edits; the affected census continued against the unchanged detached checkout while feature
work proceeded. Its completed fingerprint archive therefore follows the first feature edits rather
than the spec's requested archive-before-edit order. It never uses feature sources or carried-forward96
counts. Final comparison requires its actual completed XML. Review disposition: retain this timing deviation
explicitly for manual review, with no claim that the requested order was met. Detached-source
integrity establishes a fresh same-SHA comparison; it does not retroactively satisfy the process order.
No test, mutation gate or semantic limit is waived.

The first long public loader/runner/snapshot/JSON/replay tracer was red at unsupported signature
admission, then green after coordinated value/frame/executor transport. The floating/array tracer
likewise failed admission before its implementation. Integrated validation and final mutation results
remain separate gates below.

## Confirmed design constraints

The existing loader, owned opcode executor, exact search and replay/consumer interfaces remain the
seams. The existing CONTEXT.md owns domain vocabulary. Do not introduce a generic backend or host
execution fallback. Category-two operands require coordinated frame/local/stack handling, admission,
primitive effects, canonical identity, observer typing and lossless consumer updates.

The installed JDK26 Class-File API's FloatEntry/DoubleEntry documentation explicitly permits NaN
canonicalization when converting entries to Java numeric values. Preserve original floating constant
bits from the validated frozen class bytes, indexed by the API's constant-pool entries; the JDK API
remains responsible for parsing and verification. Do not fabricate a NaN payload after it is lost.
See [FloatEntry](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/classfile/constantpool/FloatEntry.html)
and [DoubleEntry](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/classfile/constantpool/DoubleEntry.html).

An independent JVM26 raw-class-file probe confirms three distinct subword rules:

| Probe | JVM observation |
|---|---|
| Raw int1000 passed to Byte.valueOf(B) | ArrayIndexOutOfBoundsException |
| Raw int2 passed to Boolean.valueOf(Z), then booleanValue() | int1 |
| Raw int2 returned by IRETURN from a Z method | false |

This is a JVM semantic experiment, not an Interleave result or benchmark. It prevents incorrectly
normalizing all invocation arguments. IRETURN narrows B/C/S as the corresponding conversion and Z
with bitwise AND1; see [JVMS26 ireturn](https://docs.oracle.com/en/java/javase/26/docs/specs/jvms/jvms-6.html#jvms-6.5.ireturn).
Raw oracle source/output are retained under `97-evidence/jvm-arguments`.

## Primitive PR boundary

The shared plan permits splitting a distinct reviewable behavior/correctness argument. This first97
PR owns JVM primitive/slot semantics and all consumers. The next sequential97 PR owns the finite
library effects and builder/clone preservation arguments. Neither activates98. The authoritative97
descriptor table and complete milestone acceptance stay unchanged.

| Contract | This PR | Remaining97 PR |
|---|---|---|
| R1 primitives | int-family/long/float/double operations, conversions, category-two stack/locals, raw constants | None |
| R2–R4 finite library | Existing96 Throwable protocol unchanged; all additional descriptors/concat still reject | Exact registry, costs/payloads, caches, identity hash, String/builder/wrappers/Math/Objects, cloning/concat proofs |
| R5 consumers | Lossless primitive snapshots/keys, JSON/native, replay, jar/library/CLI/report/viewer | Library-specific consumer evidence and full97 closure |

No new platform-library descriptor, cache identity, atomic operation, bytecode strategy or default resource allowance
is admitted by the primitive PR. It is not a completed core-library implementation.

## Primitive representation and admission

- `LongValue` carries signed64 bits; `FloatBits` and `DoubleBits` carry raw IEEE bits. Keys and
  immutable snapshots never convert floating payloads through decimal/JavaScript numbers.
- A long/double occupies one logical operand but two local slots. The second local is an explicit
  unusable TOP. Stores invalidate overlapping wide values in either direction. Argument placement
  advances by actual JVM slot width; return transport does not split a value.
- `BytecodeStack` handles verified slot groups: DUP_X2, DUP2_X1 and DUP2_X2 support every legal
  one/two-slot form. The JDK verifier remains the independent authority for operand/control-flow
  validity. The access verifier uses distinct primitive tokens, avoiding collisions with legal
  application classes named `D` or `J`.
- `BytecodeInput` retains one bounded pool-offset array over its privately owned frozen input.
  Numeric payload reads use JDK-provided pool indexes. This supplements NaN-sensitive API accessors;
  it neither parses method behavior independently nor executes target code. Input caps and the
  metadata-only resolver remain unchanged.
- Primitive arithmetic uses the host's specified primitive operators only, without loading/invoking
  target classes. Integral division/remainder by zero trap; floating zero division follows IEEE.
  FP-to-integral conversion saturates/truncates with NaN mapping to zero. Arithmetic and FP-width
  conversion NaNs use canonical bits `7fc00000`/`7ff8000000000000` for deterministic replay across
  hosts; FNEG/DNEG flip the sign bit directly. Raw loads/stores/returns never canonicalize payloads. Comparisons equate signed
  zeros and use the opcode's specified NaN direction. Field stores and IRETURN narrow B/C/S/Z;
  invocation entry retains computational values.
- Every executed opcode costs one fuel unit regardless of slot width. Wide fields/elements cost one
  heap cell per field/element, as before; arrays retain header+elements accounting. Wide shared
  accesses are single SC operations in this profile, without a JMM tearing/weak-memory claim.

Current producers identify `BYTECODE-SC-4`, canonical state4, evidence schema2. Profile4 names the
new admitted primitive semantics; canonical4 changes internal key tags/layout for real wide values
and TOP, replacing canonical3's default-only wide markers. Schema2 names the unchanged public DTO
shape. A canonical3 digest cannot authenticate a canonical4 state. The public evidence
shape/native records are unchanged; schema2 accepts SC3 and SC4 for display/import. Strict replay
requires the current artifact/profile/limits and rejects SC3 semantic identity. Schema1 SC1/SC2
remain displayable. Source/declarative/native routes retain their existing shape.

## Batching preservation argument for primitives

Assumptions: the closed supported program passes the JDK verifier and profile/access/monitor
validation; checks obey the existing pure-observer restrictions; resource limits and scheduling
mode are fixed. No additional library operation or optimized search is admitted. Outcome-set equivalence concerns
complete exploration under identical model limits, including modeled prefix cutoffs. Global time,
state, key-memory and cancellation stops remain honestly INCOMPLETE; their observed prefixes need
not match between modes.

1. Relate reference and batched states at retained scheduling boundaries by equal heap/aliases,
   statics, frames/BCIs/operand raw bits, monitor state, sharing history and all resource counters.
   The same opcode executor advances both modes, so numeric computation never has a separate
   batched implementation. The canonical encoding contains each new raw representation and TOP.
2. A local arithmetic/conversion/dup instruction changes private operands and fuel only. Checks
   cannot inspect either; no new target reference is created. Retained heap reads/writes and array
   operations remain observable, including long/double operations. Their read-modify-write sequences
   retain distinct read and write choices; primitive width does not collapse increments.
3. POP/POP2 and reference-discarding stores retain the existing root-loss boundaries. A two-slot
   store additionally checks its second destination slot for a reference. Losing that root can make
   a later competing sharing traversal promote an object, so the store cannot stutter past that
   choice. Dup operations preserve the reference root set in every legal slot form. The independent
   instruction enumerator exercises both promoted and unpromoted histories.
4. Integral divide faults consume the selected opcode's unit and preserve prior committed heap
   effects. Existing exception delivery/unwinding and monitor cleanup are separate events; batching
   yields before them. Fuel exhaustion is still probed from runtime state, with no committed fuel
   or operands until selection. Exact-limit return succeeds; omission of the next opcode ends that
   prefix. A competing actor can still expose a violation before terminal delivery.
5. Omitted intermediate pure-check evaluations observe the same target heap. Floating computation
   introduces no target allocation or new host/implicit callbacks; arithmetic NaNs/infinities are
   values, not exceptions. Every retained shared boundary and final/abnormal outcome uses existing
   observation rules. Thus local stuttering preserves normalized observations, fault/cutoff identity
   and competing enabledness under the stated profile. This is not a DPOR argument.

Tests challenge the relation with wide lost updates, array increments, NaN branches, division faults
competing with an ALWAYS violation, synchronized cleanup, second-slot reference loss, raw NaN
transport, signed zero, exact-limit return and generated mixed primitive programs. Green tests
supplement this argument; they do not constitute its proof. No new performance percentage or
sharedness-cost claim is inferred from these tests.

## Independent arithmetic and consumer evidence

`BytecodePrimitiveArithmeticTest` has literal signed-overflow, masked-shift, comparison, IEEE and
conversion boundary vectors. Manual routes use modular two's-complement arithmetic and IEEE
sign/exponent/fraction encodings: 3.75=1.111₂×2¹ gives binary32 `40700000`; -0.75=-1.1₂×2⁻¹ gives
`bf400000`; 1.5=1.1₂ gives `3fc00000`. Python `struct` independently confirms these plus double forms
and rounded int/long conversions (12 vectors in `numeric-derivations.json`). Expected results are
literal in tests, never recomputed by the interpreter expression.

Generated class-file stack cases compare manual digit ordering with a separate JVM. Raw NaN fixtures
patch actual float/double constant-pool bytes to quiet payloads `7fc12345` and `7ff823456789abcd`;
independent JVM loads confirm those exact patterns. Quiet NaNs avoid machine-dependent signaling
conversion effects. Transport remains lossless. The profile fixes arithmetic/FP-conversion NaNs to canonical positive
quiet bits, avoiding host-dependent replay. [JVMS26 floating-point values](https://docs.oracle.com/en/java/javase/26/docs/specs/jvms/jvms-2.html#jvms-2.3.2)
permits different NaN result encodings; choosing one deterministic encoding is profile behavior,
not a claim that every independent JVM returns those bits. The new canonical-NaN regression was
red with host payload propagation and green after changing the arithmetic result capture only.
The deterministic generator uses seed `0x972026`, Java17/21/26 fixtures, deletion shrinking and saved
failure source, plus independent instruction enumeration against both production scheduling modes.

`WidePrimitives` is compiled against the actual built jar and checked through the external CLI and
independently compiled public library consumer. Raw wide results also pass JSON/native/replay paths;
tampered NaN payloads and old profiles must reject strict replay. Fresh report/result/trace viewer
fixtures exercise extrema, signed zero, infinity and noncanonical NaNs without numeric coercion.
The CI browser producer explicitly includes the NaN fixture. String switches, concat, cloning and
other compiler constructs retain their existing rejection expectations pending the library PR.

## Reproduction commands and raw evidence

Use JDK26 and the unchanged Gradle configuration. From the feature worktree:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 build javadoc
./gradlew --max-workers=1 -PpitestThreads=1 test --tests dev.samhb.interleave.bytecode.BytecodePrimitiveTest --tests dev.samhb.interleave.bytecode.BytecodePrimitiveArithmeticTest --tests dev.samhb.interleave.bytecode.BytecodeGeneratedTest
npm ci --prefix docs
BYTECODE_DIR=build npm test --prefix docs
./gradlew --max-workers=1 -PpitestThreads=1 pitest
```

The broad affected census uses this exact override on both baseline and feature, with targetTests,
mutators, timeouts and gates unchanged:

```sh
PIT_SCOPE='dev.samhb.interleave.bytecode.*,dev.samhb.interleave.BytecodeTraceJson*,dev.samhb.interleave.ResultJson*,dev.samhb.interleave.core.BytecodeEvidence*,dev.samhb.interleave.InterleaveRunner*,dev.samhb.interleave.TestResult*,dev.samhb.interleave.TraceRecord*,dev.samhb.interleave.search.TraceReplayer*,dev.samhb.interleave.report.BenchmarkHarness*,dev.samhb.interleave.report.BenchmarkResult*,dev.samhb.interleave.report.ReportWriter*,dev.samhb.interleave.report.SoundnessAttestation*,dev.samhb.interleave.cli.Main*'
./gradlew --max-workers=1 -PpitestThreads=1 pitest -PpitestTargetOverride="$PIT_SCOPE"
```

The complete build generates fresh `build/bytecode-*.json` artifacts for the browser, compiles
`WidePrimitives.java` against the current library jar and runs an independent jar consumer plus CLI
JSON report/replay checks. `rawNanPayloadsSurviveAllTransports` emits `bytecode-primitive-nan.json`.
Do not run the browser checks against copied older artifacts. The mixed primitive generator uses
12 samples, six operations per sample, releases17/21/26 cyclically and seed `0x972026`; its source
is in `BytecodeGeneratedTest`. Failed minimized sources are saved under `build/bytecode-regressions`.

Raw local evidence root is `/Users/samuelhernandez/Documents/repos/worktrees/interleave-97-evidence`:
`baseline.py`, `baseline-sha.txt`, `baseline-build.log`, `baseline-default.log/xml`,
`baseline-affected.log/xml`, `baseline-test-results`, `baseline-classes-sha256.json`,
`final-build.log`, `private-affected.options`, `private-affected-javadoc.log`,
`private-javadoc.log`, `baseline-private-javadoc.log`, `numeric-derivations.json`,
`jvm-arguments` and `jvm-concat`. A pending filename is not proof of a completed gate.
The private check uses Javadoc's generated classpath/options, selects bytecode sources and
BytecodeEvidence, then adds `-private -Werror -Xdoclint:all`. The original options and all production
sources were also checked separately to identify the unchanged33-warning backlog. CodeRabbit's raw
output is `coderabbit-primitives.ndjson`; only its actual completion status counts as review evidence.

## Historical verification and review gates

- Corrected integrated build and strict Javadoc pass2188 tests, zero failures/errors/skips. Review
  follow-up adds positive/negative protected-long receiver cases, wide parameter/stack-map transport
  and post-increment stack forms while preserving the original int cases. The subsequent integrated
  build also passes2188 tests. Two additional left-NaN comparison vectors are exercised by the
  corrected affected census's coverage phase; no production change follows the corrected build.
- Final default PIT passes455 KILLED/13 SURVIVED/0 NO_COVERAGE (468), with exact baseline fingerprints
  and statuses. The first final affected attempt was cancelled (exit143 during initial coverage) for
  the replay-portability correction below; no affected numbers are inferred from it. A corrected
  full affected run and inherited-kill comparison remain pending. Browser checks pass126/126. CodeRabbit completed; dispositions below. Reader follow-up passes the six semantic/evidence questions.
- Private affected doclint passes the bytecode package and changed BytecodeEvidence DTO.
- A whole-project private-doclint diagnostic found33 warnings outside the affected bytecode scope;
  merged-main reproduces the same33 warnings. This is a diagnostic limitation, not a green whole-project private-doclint gate.
- No bytecode DPOR reduction, JPF compatibility or new primitive performance benchmark is claimed.

After actual gates and review dispositions are recorded, publish this primitive PR with the required
coauthor trailer, report its link and stop. Complete library97 follows only after Sam's merge update.

## CodeRabbit and reader dispositions

Actual CLI0.8.1 completed `review --agent --uncommitted --include-untracked` over36 files with
`review_completed`/11 findings:8 major,1 minor,2 trivial. Credential-pattern scope inspection found
no selected-file matches. Required CLI evidence is the saved NDJSON, not an ordinary review claim.

| Finding | Severity | Independent disposition |
|---|---|---|
| CI producer selection | major | Existing selected Release producer emits primitives; explicit selected raw-NaN producer emits primitive-nan. Fresh browser artifacts pass; no missing selector found |
| DUP2_X1 category-two form | major | Slot groups already yield wide/receiver/wide; manual-order/JVM stack tests pass; no production fix needed |
| Reference/array zero defaults | major | zero defaults already return NULL for reference/array descriptors, typed positive zero for J/F/D; existing heap and new primitive-array regressions pass |
| TOP consumer handling | major | Canonical tag8 and TEXT diagnostic already deliberate; wide frame snapshots normalize/replay. No unsupported TOP wire type is exposed |
| Double array negative zero | major | Array stores already preserve DoubleBits; strengthened public/JVM fixture stores, reloads and asserts double negative-zero bits |
| Wide invocation locals | major | Frame already advances by width including receiver; extended public tracer with instance select(long,int), marker7, after static wide transport |
| Floating LDC payload | major | RawConstants.numeric and method.constants already preserve exact bits; independent patched-pool/JVM/JSON/native/viewer/replay evidence covers noncanonical NaNs |
| Admission helper checks unexecuted | trivial | Valid: admission regression now explores and requires one COMPLETED witness, executing declared checks |
| Mismatch interrupts shrinking | minor | Valid: generator returns false for normalized outcome mismatches, preserves explicit setup failures, shrinks/saves before final failure; dedicated saved-regression test added |
| Integral division host failure | major | Zero divisors already raise Trap before host division; literal unit traps and concurrent fault/cleanup regressions cover all integral divide/remainder forms |
| Return narrowing masked by store | trivial | Valid: return-only B/C/S fixtures now store into int fields, so field narrowing cannot hide an incorrect IRETURN |

Fresh doc-coauthoring reader answered six questions about scope, compatibility, widths/accounting,
floating payloads, batching and established/pending evidence. Valid findings corrected stale
specification-only wording, added exact reproduction commands/evidence paths, clarified version roles
and replaced premature accepted-work wording. Baseline-order deviation stays explicit rather than
being represented as satisfied. Numeric derivation reproduction uses Python `struct.pack('>f',x)`
or `struct.pack('>d',x)` for exactly representable rationals3.75,-0.75,-3,1.5 and for conversions of
2147483647,9223372036854775807 and16777217. Compare the resulting hex with the literal unit vectors;
the12-row saved derivation file records both expected and independently obtained encodings.

## Final-review NaN replay correction

Independent connected-code review found that raw host arithmetic NaN results can differ by hardware,
while the recorded profile must replay deterministically. A focused test with noncanonical NaN
operands failed against the original arithmetic capture. `floating(float/double)` now uses the JDK's
canonical bit conversion only for arithmetic/FP-width results. Raw constant-pool, field/array/local,
argument/return, snapshot and key transport continue to preserve exact bits. FNEG/DNEG remain explicit
sign-bit operations. This chooses an allowed JVM NaN result without claiming portable JVM payloads.

The running affected census was stopped before source recompilation; completed default XML and the
independent unchanged baseline census were retained. Its incomplete attempt is not acceptance
or a resource-error kill. Corrected build, affected census and a bounded second actual CodeRabbit
review are required before publication; repeated verification is justified by this correctness fix.

The bounded second CLI review completed36 files with9 major verification findings; it is not a
zero-finding review. Each was checked against existing production behavior:

| Second finding | Disposition |
|---|---|
| Protected long DUP2_X1 | Shared slot grouping was already correct; extended the existing protected-receiver test with valid and forged invalid long post-increment cases |
| Primitive/reference array defaults | Existing zero factory returns NULL for `[J`/references and typed positive-zero D/J/F values; array/default/raw-transport tests retain those types |
| Long pad before protected receiver | Verifier argument and stack-map seeding already advance by width; the new protected fixture reads a Child receiver after long pad and checks LINKAGE rejection for a forged Box receiver |
| Second local slot loses reference | Probe already checks both destinations; root-history regression compares independent, reference and batched outcomes |
| Integral zero divisor | Existing explicit Trap guards cover IDIV/IREM/LDIV/LREM before host division; unit and cleanup/error evidence remain valid |
| POP2/DUP2_X2 forms | Existing group widths support one wide or two narrow operands and the two-long permutation; literal/JVM stack cases pass |
| Immediate and pool constants | Existing method table freezes numeric intrinsic, argument and CP constants by BCI; integer/wide/compiler and raw-payload regressions exercise all paths |
| Narrow returns versus wide/reference returns | Shared narrow function has B/C/S/Z cases and passthrough otherwise; isolated-return and long/double/reference replay cases validate it |
| Overlapping wide locals | Existing store clears both halves and root-loss probe preserves the scheduling boundary; overlap and history tests cover the stated case |

No third CLI run is needed for these test-only review follow-ups. Required evidence remains both
actual completed reviews plus independently verified dispositions. A fresh doc reader confirmed the
previous reproduction/version/status corrections. The final NaN/profile reader pass also found no
material ambiguity: raw transport, arithmetic canonicalization, sign-flip negation, external
INCOMPLETE stops and remaining library97 scope are clear. This is documentation review, not
independent execution evidence.

## Historical resumed affected censuses

The earlier baseline and corrected-feature affected runs were interrupted when the prior session
ended. Neither produced a completed archived report; partial XML/logs are not acceptance evidence.
On resumption, remote main remains `cccd74197530689df6cd58a2883d90b1b90af983`, the baseline checkout
is clean, and both compiled-class manifests match their archived validated builds. Only the two
affected censuses were restarted, with the same target override, unchanged tests/operators/gates
and required one-worker/one-minion flags. Build/browser/default gates were not repeated on resumption.
The task-owned runners survived a subsequent server restart and were left running after process/log
verification rather than duplicated.

`resume-affected.py baseline` and `resume-affected.py feature` retain new logs as
`resumed-baseline-affected.log` and `resumed-final-affected.log`. Their status JSON and complete
`baseline-affected.xml`/`final-affected.xml` archives appear only after their respective Gradle gates
finish successfully. Final counts and inherited-kill comparison remain pending at this checkpoint.
