<!-- Internal bytecode foundation: measured acceptance and explicit limits; no public release claim. -->
# 94A — Bytecode interpreter foundation evidence

## Scope and baseline

94A implements the internal loader/owned-state/instruction seams in
[Spec94A](../specs/active/94A-bytecode-foundation.md) under the
[shared contract](../plans/bytecode-contract.md). All production types remain package-private.
Protocol annotations exist only in tests. There is no bytecode runner, CLI selector, concurrent
search, check execution, public witness/replay/report or viewer support in this milestone.

This proposed change introduces94A; it is not present in the specification-only starting revision.
Fresh starting point: merged specification PR#138, main
`b42fe42b3f75ab474a1cefb4a2c9be9017586d5f`. Existing dirty work in Sam's original checkout was preserved;
implementation and baseline use separate worktrees. Baseline full build/strict Javadoc passed
**1,963 tests**, zero failures. Untouched baseline default PIT: **455 K /468 +13 SURVIVED**.
No affected baseline exists because this package did not exist on main. No historical B10 result
is presented as a fresh run.

## Implementation and preservation argument

Metadata admission uses the JDK26 Class-File API with an explicit metadata-only hierarchy resolver.
Every reachable actor/setup/check call-graph role is checked, including unsupported branch alternatives.
Inspection of installed JDK sources confirmed that VerifierImpl skips bridge methods and AnnotationReader
recurses without a depth guard. Reachable bridge methods reject; a bounded container/annotation preflight
caps nesting at32 before recursive metadata decoding. Unsupported Record metadata rejects before its
component annotations can reach recursive verification. The JDK remains the actual parser/verifier.
Both bypasses have failing-before-fix regressions, including20000 nested arrays below the byte cap and an injected Record component.
Method declarations are indexed once per class; repeated/distinct call references reuse the index.
The JDK verifier checks stack/local types independently of profile admission. Application initializers,
platform calls and unsupported handlers reject before interpreted setup. Metadata and input are bounded;
parsing never executes the target. Trusted Object constructor semantics are interpreted directly.

The directory root and entries must remain immutable throughout loading. Pinned directory handles
reject symlink traversal; a no-follow regular-file attribute check precedes open. Concurrent replacement
between that check and open is explicitly outside the user-approved contract. This deliberately avoids
native-access requirements or production reader workers. A subprocess regression tests FIFO rejection
with a deadline so a broken guard cannot block the test JVM. The loaded artifact freezes exact bytes
and is independent of subsequent file changes. Digest identity excludes host paths.

One opcode executor operates on an isolated copy of the parent. Executed opcodes advance fuel; a faulting opcode is executed work. Insufficient fuel omits the entire next operation,
including the two-unit Object constructor intrinsic. Allocation/cell/depth failures retain the defined
executed-opcode charge, and failed allocations consume no object identity or cells. Setup budgets and
identities are separate from actors. Sparse frame locals avoid allocating all65535 slots merely from
an unused max_locals declaration. Canonical bytes use typed, length-delimited UTF-16 names and full-width
identity sequences, retaining heap aliases, static fields, frames, monitors, counters and pending events.

Zero-opcode events advance return/unwind/terminal state independently of instruction fuel. A root
return or fully unwound exception yields before terminal delivery. A resource cutoff ends the prefix;
selecting another actor after a delivered cutoff/failure rejects. Sufficient-fuel blocked monitor
attempts preserve all state. These are instruction-seam contracts; no batching equivalence or search
completeness is claimed here. Setup objects conservatively remain shared. Dynamic sharedness analysis
and its measurements belong to94B/95.

Monitor cleanup admission checks the catch-all store/load/exit/rethrow form and propagates lock-slot
stacks across control flow. Producer/consumer edges must be unbroken: a branch cannot bypass saved-lock
wiring or enter a cleanup body normally. A crafted branch-bypass regression failed before this correction.
Single-actor tests cover nested/reentrant explicit locks and implicit method locks, including fault
cleanup and fuel exhaustion inside cleanup. Multi-actor enabledness/deadlock belongs to94B.

## Independent execution evidence

- Targeted sequential cases compare literal integer edge values and separate-JVM results for branches,
  loops, static helpers, reference/null/identity operations, wide locals, category-one stack forms,
  Boolean low-bit narrowing and synchronized methods/blocks. Fault tests assert the explicit VM types,
  original BCI/stack, committed effects and cleanup contract. The oracle never calls the interpreter or encoder.
- Fixed-seed generator `0x94A2026L`:36 programs with7 generated operations each, cycling compilation
  targets17/21/26. Greedy deletion shrinking saves a failing program under build/bytecode-regressions;
  arithmetic and cleanup regressions are committed as source fixtures and checked against the JVM.
- Compiler: installed JDK26.0.2.1 javac with `--release 17`, `--release 21`, `--release 26`; oracle runtime
  is the same installed JDK26 in a separate process. This is input-target compatibility evidence,
  not evidence from three installed compiler implementations/runtime toolchains.
- Local executable hashes (SHA-256): java
  `38025293a292f9d2161c9c2683191555eabefeb8b987cde9d8c88cafb843d333`;
  javac `a587e4bfa51de17788dfc9de7b22960174f11cb5fed8f21bf2867151eb2524fa`.
- Input regressions cover exact1MiB/class,8MiB aggregate and64-class bounds; malformed stacks/versions,
  missing metadata, annotation placement/roles/purity and classpath escapes. This is bounded fixture
  evidence, not a general proof that all arbitrary class files are safe or supported.

## Initial implementation validation and mutation evidence (67b70ec)

Final integrated build/strict Javadoc passes **2,017 tests**, zero failures/errors/skips. Independent
private-member doclint passes all11 production files with all groups and warnings as errors.
Browser compatibility passes **248 checks**, zero console errors, against freshly generated legacy
source/declarative/exact/approximate artifacts. The actual library jar omits protocol annotations,
the public bytecode facade and mutation harness. Documentation validation passes17 documents,
10 milestone structures/requirement mappings and219 local links/anchors; whitespace checks pass.

All local Gradle runs use `--max-workers=1 -PpitestThreads=1`; operators, floors, mutation target tests
and CI parallelism remain unchanged. The affected census is the new bytecode package; the configured
default census is state/cb, not every production package or a whole-checker score. K counts only KILLED.

| Scope | Fresh baseline | Feature |
|---|---|---|
| Configured default state/cb |455 K /468 +13 SURVIVED| 455 K /468 +13 SURVIVED; all468 fingerprints/statuses unchanged |
| New bytecode package |Absent on baseline|1320 K /1605 +176 SURVIVED +109 NO_COVERAGE|

| Affected class/group |KILLED|SURVIVED|NO_COVERAGE|
|---|---:|---:|---:|
| BytecodeFrame |15|0|0|
| BytecodeInput |108|10|16|
| BytecodeLimits |7|0|0|
| BytecodeLoadException |0|2|0|
| BytecodeLoader |459|73|66|
| BytecodeMethod |34|3|0|
| BytecodeProgram / FieldId |23|2|0|
| BytecodeState and nested types |182|16|0|
| BytecodeVerifier |238|60|21|
| OpcodeExecutor |254|10|6|

The affected census passes R6 with no TIMED_OUT, MEMORY_ERROR, NON_VIABLE, RUN_ERROR or EQUIVALENT
statuses. R5/R7 are deliberately not enforced on an override scope; the separate default gate remains
mandatory. The285 affected non-kills are retained in raw XML and are not excluded or declared equivalent.
Most are in admission/metadata guards, diagnostics and defensive validation paths; overlapping JDK
verification can preserve rejection when one guard changes, but that is not a general equivalence proof.
The remaining encoder/executor residuals also remain explicit. No95% new-module claim is made.
Final minor test-review assertions and Boolean raw-bytecode cases were added after this census; all
pass in the final integrated suite. No production bytecode changed after the affected census.
The final configured-default gate passes R5/R6/R7. All468 baseline identities and statuses match
exactly by class/method/descriptor/operator/instruction/block, preserving every455 baseline kill
and all13 inherited survivors. Both final reports have zero uncovered/resource/run-error statuses in
the default scope; that statement does not erase the109 uncovered bytecode mutants above.

The first affected census failed R6: **1,014 K,245 SURVIVED,162 NO_COVERAGE,10 TIMED_OUT /1,431**.
Timeouts are not kills. A later intermediate census had one stalled explicit-monitor mutation; a
direct monitor-probe regression kills that exact mutation by assertion in the final census.
This led to direct, bounded instruction-event assertions, a test-only bounded
sequential driver and binary-name loop progress checks. No production timeout/fuel policy was relaxed.
The removed production convenience driver had no production callers; tests now drive the agreed
instruction seam with a finite assertion ceiling.

## Review dispositions

Actual CodeRabbit CLI0.8.1 first whole-change review completed with5 findings and8 files unreviewed.
It is partial review evidence, not a full-set clean review. The first production directory review completed all10 original files; the final production review
completed all11 files including BytecodeInput. Test directory reviews completed all changed tests
and saved fixtures, including the new hostile annotation cases. Documentation review completed all4
changed documents under docs. README status wording received ordinary review. These completed scopes
replace the missing coverage of the first attempt; they do not turn its partial outcome into clean evidence.

| Finding | Disposition |
|---|---|
| Entry/access Javadocs omit exact rules | Expanded accepted method shape, access rules and diagnostic conditions. |
| Missing superclass leaks NoSuchElementException | Categorized MALFORMED supplier plus super_class=0 regression. |
| Typed catch handlers could be treated as cleanup | Already rejected by cleanupShapes; focused unrelated catch/fault regression added. |
| FIFO open may block | Confirmed on installed JDK26 Unix provider. User explicitly chose immutable regular-file inputs; guard and contract added. Concurrent replacement remains unsupported. |
| Copy-history assertion starts empty | Starts after17 instructions; asserts literal17 and canonical equality before modifying each copy. |

Further production review confirmed the bridge and annotation/Record verification gaps and the
repeated method-table scan; all were addressed with regressions. Suggestions to change fuel/blocking
priority or add instance-call handling were rejected against the approved contract. Every admitted
non-intrinsic invoke is static. Narrow B/C/S descriptors remain rejected; explicit i2b/i2c/i2s
operations are covered. CHECK purity already rejects synchronized roots and transitive helpers in
the loader work queue; the helper case now has a focused regression.

Test review strengthened pre-call BLOCKED snapshots, independent fault-location/stack cases, literal
input byte bounds, runtime-derived child classpaths and compiler operands that cannot fold to constants.
Annotation/fixture Javadocs were completed. Concurrent driver cleanup is interrupt-aware and bounded;
it does not wait unboundedly in ExecutorService.close. Child probes use the active launcher's local
class-resource roots and do not claim to execute in-memory PIT mutations.

Connected-source review also found the monitor-wiring branch bypass and original setup fault-location
loss after cleanup. Both failed before their fixes. Ordinary review was performed sequentially;
no independent-agent review is claimed. No review recommendation expanded support into95/97.

## PR139 review feedback

All five initial review comments were independently confirmed and patched:

- Corrected the reproducible javac options to `--release 17`, `--release 21`, `--release 26`.
- Reject conflicting public/private/protected bits on every field and method declaration before
  setup, including constructors and unused helpers. The regression covers all four conflicting
  combinations across four member kinds. A separate constructor-failure control proves MALFORMED
  takes priority over executing setup. Both tests failed before the guard was added.
- Exercise every tableswitch and lookupswitch key/default in the existing JVM-oracle fixture;
  assert that both switch encodings are actually present.
- Keep directory rejection unconditional; skip only the separate FIFO case when no executable
  mkfifo is found on PATH. With an intentionally empty test-worker PATH, directory rejection
  passes and only the FIFO case skips. With normal PATH, both execute successfully.
- Capture canonical bytes before the blocked probe and compare both the parent and returned states
  with that snapshot, so in-place mutation cannot satisfy the assertion accidentally.

Patch integrated build/strict Javadoc: **2,020 tests**, zero failures/errors/skips. Private doclint
passes all11 production files. Focused BytecodeLoader mutation census: **472 K /613 +75 SURVIVED
+66 NO_COVERAGE**, zero resource/run-error statuses; scoped R6 passes. All five mutants in the new
visibility helper are killed. All451 fingerprints in unchanged loader methods retain their prior
statuses; the changed read method and new helper account for the population change from598 to613.
The initial complete-package/default censuses above remain historical67b70ec results, not new scores
for this patch. Browser and default state/cb mutation runs were not repeated: no corresponding
production or consumer code changed. Documentation and whitespace validation pass.
Actual CodeRabbit CLI was not rerun for this minor feedback patch, following Sam's standing review
policy; ordinary review and skip evidence are recorded through agent-setup.

Raw patch prefix `/private/tmp/interleave-pr139-`: flags-red.log, flags-green.log,
feedback-tests.log, no-mkfifo.log, feedback-build.log, private-javadoc.log, loader-pit.log/.xml
and loader-comparison.txt.

## Raw local evidence

Prefix `/private/tmp/interleave-94A-`: baseline-build.log and baseline-pit.log; untouched baseline
XML remains in `/private/tmp/interleave-94A-baseline/build/reports/pitest/mutations.xml`.
affected-first.xml, affected-second.xml and pre-record-index.xml retain earlier failures/censuses.
final-affected-v2.log / final-affected.xml retain the final affected population; final-default.log and
its saved XML/comparison retain the configured-default preservation check. final-integrated.log,
private-javadoc-final.log and browser-final.log retain integrated compatibility evidence.
CodeRabbit NDJSON: coderabbit.ndjson (partial first attempt), coderabbit-main.ndjson,
coderabbit-main-final.ndjson, coderabbit-tests.ndjson, coderabbit-tests-final.ndjson and coderabbit-docs.ndjson.
Regression logs retain the regular-file, monitor-wiring, bridge, annotation, Record and setup-location
failing-before-fix outcomes. No heartbeat is used as review completion evidence.

## Deferred evidence

No lost-update/deadlock concurrent search, bytecode reference-versus-batching comparison, sharedness-cost
benchmark, bytecode DPOR reduction or JPF compatibility experiment is established here. Those gates
remain94B,99 and101 respectively. No public BYTECODE evidence/replay compatibility is claimed before94C.
The approved defaults remain provisional pending94B measurements. General heap/dispatch, user exceptions,
core library, CBS, DPOR, atomics and bitstate remain rejected/unavailable until their own complete phases.
