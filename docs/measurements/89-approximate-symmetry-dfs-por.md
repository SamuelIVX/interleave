<!-- Fresh K1 baseline, shared-filter lineage, independent oracles and verification limits. -->
# 89 — Approximate symmetry for DFS and static POR: evidence

## TL;DR

K1 implements owned approximate VALUE-orbit membership for source-certified DFS and static POR.
Exact HASHED/ORDERED behavior remains available for all four strategies. Approximate CBS/DPOR
and strategy-omitted approximate CLI selection reject; K2/K3 retain those responsibilities.
See [Spec 89](../specs/active/89-approximate-symmetry-dfs-por.md) and the
[shared contract](../plans/approximate-symmetry-contract.md).

Fresh baseline: merged PR #131 at `6dd7445019076f511cb85d479aa7d1d69f71198e`, measured in an
independent detached worktree. Production and test source, mutation operators, default targets,
resource bounds and the 94% floor are retained. Only measured population bookkeeping changes.

Build, strict Javadoc and all **1,883 tests** pass (zero failures/errors/skips). The expanded
browser suite passes **142 checks**, including fresh Java approximate root/later resource artifacts.

## Behavioral evidence

- Existing ordinary bitstate and exact symmetry regressions passed after each extraction, before
  new approximate traversal was enabled. Shared run metadata contains no visited keys or filters.
- Independent 75-position shadow: three shared values times five complete frame variants squared.
  Frame variants separately vary PC, integer local, boolean local and an array element. Equality
  explicitly checks both owner permutations, without calling the production canonicalizer.
  For each absent target, every unequal orbit is inserted before probing it: zero false positives
  on this fixed universe therefore covers every insertion subset by monotonicity. Equivalent
  permutations stay positive. Fresh DFS/static-POR stores visit the independently expected three
  reachable representatives; emitted traces replay.
- Typed VALUE envelope is read using DataInputStream independently of the ByteBuffer writer.
  Its complete orbit literal was checked with Python struct packing and an independently assembled
  hexadecimal payload. Probe unions for zero, negative, MIN/MAX hashes and overflow were checked
  with integer bit operations and a separate binary-string rotation route.
- Duplicate marks use insertion counts: the literal n=2, m=8, k=2 heuristic is
  `0.15481812174617549`, independently checked with Python math and Decimal. Clear/copy statistics,
  actual occupied bits, empty/saturated membership and independent storage are asserted.
- Forced one-bit collisions omit a real final-property failure but produce neither an invented
  witness nor exhaustive PASS. Both strategies report approximate absence. Real noncolliding
  failure reports undergo existing replay attestation; loop cutoffs remain INCOMPLETE.
- Builder conflicts reject in either setter order. Owned runners repeat and start concurrently
  behind a shared gate. Root and later state-budget stops preserve BITSTATE/symmetry and actual
  resource-prefix values; explicit replay retains the original interruption classification.
- Fresh CLI JSON passes normalization and browser checks, including ordinary exact fixtures,
  approximate symmetry absence and root/later resource interruptions. No viewer production logic
  or wire schema changed. External Java 26 jar consumers use both new public capabilities and
  both supported algorithms without package-private access.

## Fresh baseline census

K = KILLED, S = SURVIVED, NC = NO_COVERAGE. Neither scoped nor aggregate denominators exclude NC.
Default baseline: **440/455 K + 15 S**. Affected baseline: **1,557/2,036 K + 90 S + 389 NC**.

| Class (dev.samhb.interleave prefix) | Baseline K / total | S | NC |
|---|---:|---:|---:|
| state.BitstateStore | 95 / 97 | 2 | 0 |
| search.SymmetryStateStore | 96 / 96 | 0 | 0 |
| InterleaveRunner | 115 / 141 | 22 | 4 |
| InterleaveRunner.Builder | 30 / 30 | 0 | 0 |
| InterleaveRunner anonymous visitor | 30 / 43 | 13 | 0 |
| InterleaveRunner.PartialResult | 21 / 21 | 0 | 0 |
| InterleaveRunner.LimitState | 4 / 4 | 0 | 0 |
| search.DfsExplorer | 90 / 91 | 1 | 0 |
| por.StaticPorExplorer | 129 / 138 | 3 | 6 |
| dpor.DporExplorer | 293 / 317 | 12 | 12 |
| cb.ContextBoundedExplorer | 131 / 131 | 0 | 0 |
| cli.Main | 33 / 396 | 0 | 363 |
| report.BenchmarkHarness | 246 / 277 | 27 | 4 |
| report.BenchmarkResult | 55 / 57 | 2 | 0 |
| report.ReportWriter | 189 / 197 | 8 | 0 |

Main's 363 NC are an inherited in-process PIT coverage boundary: child-JVM CLI tests run real
commands, but their execution is outside the mutated parent JVM. Those tests do not establish
363 mutation kills. This feature does not refactor System.exit or introduce a new CLI test seam.
Likewise, inherited explorer/runner/report residuals are coverage limits, not newly proven bugs
or equivalence claims. No aggregate affected-scope percentage establishes exhaustive correctness.

## Mutation lineage and final census

These are separate measured runs, not one implied final broad run:

| Run | K / total | S | NC | Purpose |
|---|---:|---:|---:|---|
| Fresh default baseline | 440 / 455 | 15 | 0 | Actual merged main |
| Fresh affected baseline | 1,557 / 2,036 | 90 | 389 | Actual merged main |
| Wide feature, before final boundary assertions | 1,644 / 2,133 | 94 | 395 | Initial complete affected census |
| Focused boundary refinements | 450 / 479 | 25 | 4 | Harness, ordinary bitstate, run context, approximate store |
| Reviewed probe reuse and exact validation | 154 / 154 | 0 | 0 | Ordinary bitstate, filter, exact symmetry store |
| Final default gate | **455 / 468** | **13** | **0** | Current source; R5/R6/R7 pass |

The focused runs verify every changed assertion or runtime correction after the wide run.
Their per-class observations replace the corresponding wide observations below; unchanged classes
retain their wide-run evidence. Combined accounting is **1,656 K + 86 S + 395 NC = 2,137**, not an
additional broad PIT run or a new aggregate coverage guarantee. Scoped R5/R7 are not enforced;
R6 passes with zero timeout, memory, nonviable or run-error detections counted as kills.

Final default **455/468 = 97.22%** passes the unchanged 94% floor, which requires 440 kills.
The 13 raw survivors are the inherited CanonicalEncoder (1), HashingStateStore (7) and
ThreadSymmetryCanonicalizer (5) sites; no new default-scope survivor remains. Default lineage
retains all 409 matching fingerprints/statuses: 46 old fingerprints are removed/rewritten and
59 new fingerprints are measured, without counting population growth as inherited kills.

| Class (dev.samhb.interleave prefix) | Latest measured K / total | S | NC | Producing run |
|---|---:|---:|---:|---|
| state.BitstateStore | 84 / 84 | 0 | 0 | Reviewed correction |
| state.BitstateFilter | 26 / 26 | 0 | 0 | Reviewed correction |
| search.SymmetryStateStore | 44 / 44 | 0 | 0 | Reviewed correction |
| search.SymmetryRunContext | 67 / 67 | 0 | 0 | Focused |
| search.BitstateSymmetryStateStore | 33 / 33 | 0 | 0 | Focused |
| InterleaveRunner | 118 / 144 | 22 | 4 | Wide |
| InterleaveRunner.Builder | 42 / 42 | 0 | 0 | Wide |
| InterleaveRunner anonymous visitor | 30 / 43 | 13 | 0 | Wide |
| InterleaveRunner.PartialResult | 21 / 21 | 0 | 0 | Wide |
| InterleaveRunner.LimitState | 4 / 4 | 0 | 0 | Wide |
| search.DfsExplorer | 90 / 91 | 1 | 0 | Wide |
| por.StaticPorExplorer | 129 / 138 | 3 | 6 | Wide |
| dpor.DporExplorer | 293 / 317 | 12 | 12 | Wide |
| cb.ContextBoundedExplorer | 131 / 131 | 0 | 0 | Wide |
| cli.Main | 33 / 402 | 0 | 369 | Wide |
| report.BenchmarkHarness | 267 / 296 | 25 | 4 | Focused |
| report.BenchmarkResult | 55 / 57 | 2 | 0 | Wide |
| report.ReportWriter | 189 / 197 | 8 | 0 | Wide |

Main grows from 396 to 402 sites: **33 K + 369 NC**. Six new CLI selection mutations therefore share
the existing child-JVM coverage boundary. Actual valid/invalid CLI subprocess tests cover the new
selection behavior, but do not turn these raw NC statuses into kills. A future in-process CLI seam
would be a separate change; it is not silently introduced here.

The harness has one new redundant early empty-strategy validation survivor: constructor L119,
NonVoidMethodCallMutator removing Set.isEmpty, bytecode index 66/block 17. The later shared guard
still rejects empty approximate selections. Tests constrain rejection and supported selections,
not the exact diagnostic-message precedence between these two guards. This is a scoped rejection
observation, not a universal equivalence claim about all API output. Owner: harness selection
validation; revisit if exact error wording becomes contractual. The other 24 harness survivors
and four NC remain visible; the table accounts for every inherited explorer/runner/report residual.
Those residuals are not relabeled equivalent or silently scheduled as B9 features.

Initial extraction measured 449/464 K + 15 S. Two original hash residuals are in deleted/moved or
inlined methods; they are not credited as two inherited identity kills. Two existing constructor
boundary mutations initially survived because the helper also rejects zero. Compound-invalid
argument tests now preserve the original first-error contract and kill both. After the review
correction, ordinary CBS computes one probe array per lookup and reuses it across cost vectors,
as before extraction; neither allocation nor probe calculations multiply by the number of levels.

Identity comparison retains class, method, descriptor, mutator, description, bytecode indices and
blocks, excluding shifted source lines. Across the wide run plus final replacements, **1,720**
fingerprints match the baseline: 1,719 statuses are unchanged, and one inherited harness
createResult/statesExplored non-void-call survivor becomes K (index 48/block 10). No matched K
regresses. The remaining 316 old and 417 new fingerprints are removed/moved/rewritten populations,
not inherited kill claims. Matching subsets retain all statuses: DFS 90, static POR 137, DPOR 315,
CBS 128, ReportWriter 197 and BenchmarkResult 57. Exact store mapping operations move into the
shared context; both current classes are fully killed rather than compared by denominator alone.

Raw evidence is retained separately under `/private/tmp/interleave-k1-`: baseline-default.xml,
baseline-affected.xml, final-affected.xml (wide), refined-affected.xml (focused),
probe-reuse-affected.xml (reviewed correction), and reviewed-default.xml (final default).
Earlier final-default.xml/verified-default.xml are interim populations and are not the release gate.

## Consumer/review limits

CodeRabbit 0.8.1 completed the initial 23-file implementation review with one Minor about artifact
directory creation, patched and verified. The final 32-file review reported two Major findings
(probe reuse and public membership preconditions), one Minor (resource fixture directory creation)
and one Trivial (assert the loop-cutoff witness). All four were independently validated and patched.
A focused verification of the two runtime probe-reuse files completed with zero findings. No CLI
rerun for the minor/trivial fixes or final measured-bookkeeping prose alone.

Connected-code review traced certification, ordinary-factory rejection, capability dispatch,
approximate metadata, metrics, clear/copy ownership, source replay and DOM text rendering. The
final integrated build/tests/Javadoc, fresh browser artifacts and external jar consumer were rerun
after the substantive correction.

The bit budget is membership only. Source certification metadata, temporary orbit encodings,
traversal, diagnostic configuration maps and retained traces remain outside it. No speedup,
whole-process memory bound, calibrated false-positive probability or search-coverage percentage
is measured or claimed. No CBS/DPOR approximate execution is implemented in K1.
