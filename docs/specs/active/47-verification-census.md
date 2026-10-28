# Spec: Historical verification-scope census

## Objective

Refresh roadmap 9-V1's historical ten-class expanded mutation population after Specs 17–46.
Replace stale progress estimates with current raw statuses and an explicit disposition ledger,
without changing tests, production behavior or mutation gates.

## Scope and requirements

- Measure merged PR #87 at 7b265c1 with the existing target override and all tests enabled.
- Use exactly the ten outer classes named in Spec 16's expanded feedback table. Nested classes,
  report serialization, selector helpers and the initial broader parser scope are distinct.
- Preserve historical measurements as historical; do not call changed denominators identical or
  add overlapping scoped runs together. Dispositions never become assertion-confirmed kills.
- Report KILLED, SURVIVED, NO_COVERAGE and every resource/error status separately. Scoped R6
  non-assertion detection checks apply; R5 total-mutant and R7 coverage-floor gates are skipped.
- Map every current non-kill to its reviewed contract and applicability limits. A mapping is not
  universal equivalence, a new exclusion or an obligation to manufacture unsupported inputs.
- Keep Spec 40's nonterminating property-selector mutation open. Do not rerun it unchanged or
  substitute this census/default scope for its failed scoped check.

This is verification/documentation only: this spec, the roadmap and a current-census pointer in
Spec 16's README. No source, tests, dependencies, scope defaults, operators, exclusions or gates
change. No new harness or modeling capability is introduced.

## Reproduction

All local Gradle commands use one worker and one PIT thread. The historical census is a scoped
run, even though it contains multiple classes:

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.core.Program,dev.samhb.interleave.dpor.DporExplorer,dev.samhb.interleave.format.dsl.Evaluator,dev.samhb.interleave.format.dsl.SourceInstruction,dev.samhb.interleave.format.dsl.SourceProperty,dev.samhb.interleave.por.StaticPorExplorer,dev.samhb.interleave.report.BenchmarkResult,dev.samhb.interleave.report.SoundnessAttestation,dev.samhb.interleave.search.DfsExplorer,dev.samhb.interleave.search.TraceReplayer
```

Then freeze mutations.xml before running default PIT separately. Identify mutations by class,
method, descriptor, mutator, bytecode indices and blocks; source-line numbers are not identities.
Current combined measurements are authoritative; earlier single-class scores are comparison
references, not measurements to sum into a new aggregate.

## Current census

The merged 7b265c1 clean build, strict Javadoc and998-test suite pass with zero failures/errors/
skips. The combined census measures **1,264/1,301 KILLED,19 SURVIVED,18 NO_COVERAGE**, with zero
TIMED_OUT, MEMORY_ERROR, RUN_ERROR or other statuses. Scoped R6 non-assertion detection checks pass;
R5 total-mutant and R7 coverage-floor gates are skipped. This is not the full default mutation gate.

| Outer class | Historical killed/total | Current killed | Survived | Uncovered | Current total |
|---|---|---|---|---|---|
| Program | 26/27 | 27 | 0 | 0 | 27 |
| DporExplorer | 192/289 | 265 | 12 | 12 | 289 |
| Evaluator | 203/282 | 282 | 0 | 0 | 282 |
| SourceInstruction | 102/114 | 114 | 0 | 0 | 114 |
| SourceProperty | 13/13 | 13 | 0 | 0 | 13 |
| StaticPorExplorer | 106/122 | 114 | 2 | 6 | 122 |
| BenchmarkResult | 41/54 | 52 | 2 | 0 | 54 |
| SoundnessAttestation | 64/112 | 113 | 1 | 0 | 114 |
| DfsExplorer | 74/83 | 82 | 1 | 0 | 83 |
| TraceReplayer | 192/203 | 202 | 1 | 0 | 203 |
| **Total** | **1,013/1,299** | **1,264** | **19** | **18** | **1,301** |

These totals come from the single combined report, not a sum of old scoped measurements. The
historical 130 survivors and156 uncovered are no longer current remaining-work counts. The
changed denominator is entirely SoundnessAttestation 112→114: [Spec 29](29-soundness-markdown-contract.md)
added two qualification appends in formatMarkdown. Every other class retains its historical total.
This is a current census of a revised population, not an assertion that all 1,299 old identities
are byte-for-byte unchanged or that exactly251 old mutants were newly killed.

## Retained-identity reconciliation

All37 current non-kills map exactly once to the reviewed dispositions below; no current non-kill
in this ten-class population is unassigned. Raw19 SURVIVED and18 NO_COVERAGE remain raw non-kills.
The referenced proofs and their model/metadata/purity/observer limits remain mandatory; this table
adds no broader equivalence claim. In particular, unreachable duplicate-property bodies assume
pure predicates and non-mutating observers, and write-only bookkeeping remains unobserved only
while no exploration reader is introduced.

Methods and bytecode indices identify the sites, not source lines. NVMC means
NonVoidMethodCallMutator, VMC means VoidMethodCallMutator, NEG means NegateConditionalsMutator,
and FALSE means BooleanFalseReturnValsMutator. All named methods have one descriptor among these
retained entries except the two BenchmarkResult constructors: copy=(BenchmarkResult,Program,DfsResult)
and delegating=the public overload ending in Integer preemptionsUsed without snapshots, as defined
in [Spec 26](26-benchmark-exploration-evidence.md). Blocks are the reported basic-block indices.

| Class.method / bytecode index | Blocks | Mutator | Raw status | Existing disposition |
|---|---|---|---|---|
| DporExplorer.dfsDfs / 135 | 31 | NEG | SURVIVED | [Spec 35](35-dpor-property-contracts.md) |
| DporExplorer.dfsDfs / 146 | 35 | NVMC | NO_COVERAGE | [Spec 35](35-dpor-property-contracts.md) |
| DporExplorer.dfsDfs / 148 | 36 | NVMC | NO_COVERAGE | [Spec 35](35-dpor-property-contracts.md) |
| DporExplorer.dfsDfs / 151 | 37 | NVMC | NO_COVERAGE | [Spec 35](35-dpor-property-contracts.md) |
| DporExplorer.dfsDfs / 157 | 38 | NVMC | NO_COVERAGE | [Spec 35](35-dpor-property-contracts.md) |
| DporExplorer.dfsDfs / 162 | 39 | NEG | NO_COVERAGE | [Spec 35](35-dpor-property-contracts.md) |
| DporExplorer.dfsDfs / 167 | 40 | VMC | NO_COVERAGE | [Spec 35](35-dpor-property-contracts.md) |
| DporExplorer.dfsDfs / 234 | 59 | NVMC | SURVIVED | [Spec 36](36-dpor-program-counter-contracts.md) |
| DporExplorer.dporDfs / 20 | 4 | NVMC | SURVIVED | [Spec 37](37-dpor-coverage-compaction.md) |
| DporExplorer.dporDfs / 130 | 39 | NVMC | SURVIVED | [Spec 37](37-dpor-coverage-compaction.md) |
| DporExplorer.dporDfs / 285 | 77 | NVMC | NO_COVERAGE | [Spec 35](35-dpor-property-contracts.md) |
| DporExplorer.dporDfs / 287 | 78 | NVMC | NO_COVERAGE | [Spec 35](35-dpor-property-contracts.md) |
| DporExplorer.dporDfs / 290 | 79 | NVMC | NO_COVERAGE | [Spec 35](35-dpor-property-contracts.md) |
| DporExplorer.dporDfs / 296 | 80 | NVMC | NO_COVERAGE | [Spec 35](35-dpor-property-contracts.md) |
| DporExplorer.dporDfs / 301 | 81 | NEG | NO_COVERAGE | [Spec 35](35-dpor-property-contracts.md) |
| DporExplorer.dporDfs / 306 | 82 | VMC | NO_COVERAGE | [Spec 35](35-dpor-property-contracts.md) |
| DporExplorer.dporDfs / 540 | 141 | NVMC | SURVIVED | [Spec 39](39-dpor-happens-before-contracts.md) |
| DporExplorer.dporDfs / 546 | 142 | NEG | SURVIVED | [Spec 39](39-dpor-happens-before-contracts.md) |
| DporExplorer.dporDfs / 554 | 143 | VMC | SURVIVED | [Spec 39](39-dpor-happens-before-contracts.md) |
| DporExplorer.dporDfs / 663 | 172 | NVMC | SURVIVED | [Spec 38](38-dpor-visibility-contracts.md) |
| DporExplorer.dporDfs / 825 | 211 | NVMC | SURVIVED | [Spec 36](36-dpor-program-counter-contracts.md) |
| DporExplorer.explore / 62 | 12 | NVMC | SURVIVED | [Spec 35](35-dpor-property-contracts.md) |
| DporExplorer.lambda$dporDfs$2 / 6 | 1 | NVMC | SURVIVED | [Spec 37](37-dpor-coverage-compaction.md) |
| DporExplorer.lambda$dporDfs$2 / 7 | 2 | FALSE | SURVIVED | [Spec 37](37-dpor-coverage-compaction.md) |
| StaticPorExplorer.porDfs / 259 | 69 | NVMC | SURVIVED | [Spec 31](31-static-por-evidence-contracts.md) |
| StaticPorExplorer.porDfs / 434 | 108 | NVMC | SURVIVED | [Spec 31](31-static-por-evidence-contracts.md) |
| StaticPorExplorer.porDfs / 450 | 114 | NVMC | NO_COVERAGE | [Spec 31](31-static-por-evidence-contracts.md) |
| StaticPorExplorer.porDfs / 452 | 115 | NVMC | NO_COVERAGE | [Spec 31](31-static-por-evidence-contracts.md) |
| StaticPorExplorer.porDfs / 455 | 116 | NVMC | NO_COVERAGE | [Spec 31](31-static-por-evidence-contracts.md) |
| StaticPorExplorer.porDfs / 461 | 117 | NVMC | NO_COVERAGE | [Spec 31](31-static-por-evidence-contracts.md) |
| StaticPorExplorer.porDfs / 466 | 118 | NEG | NO_COVERAGE | [Spec 31](31-static-por-evidence-contracts.md) |
| StaticPorExplorer.porDfs / 471 | 119 | VMC | NO_COVERAGE | [Spec 31](31-static-por-evidence-contracts.md) |
| BenchmarkResult.delegating constructor / 18 | 0 | NVMC | SURVIVED | [Spec 26](26-benchmark-exploration-evidence.md) |
| BenchmarkResult.copy constructor / 126 | 18 | NVMC | SURVIVED | [Spec 26](26-benchmark-exploration-evidence.md) |
| SoundnessAttestation.checkSoundness / 106 | 28 | NVMC | SURVIVED | [Spec 27](27-soundness-verdict-policy.md) |
| DfsExplorer.dfs / 313 | 71 | NVMC | SURVIVED | [Spec 30](30-dfs-terminal-contracts.md) |
| TraceReplayer.validateCutoff / 81 | 30 | NVMC | SURVIVED | [Spec 17](17-replay-hardening.md) |

## Remaining work and limits

Spec 40's PropertyPersistentSetComputer component/79 NegateConditionals remains open: latest
110 KILLED,4 SURVIVED,1 TIMED_OUT from 115 identities, scoped R6 FAILED. Its baseline had a
MEMORY_ERROR for the same nonterminating mutant. Neither status earns a kill or disposition.
A bounded-harness audit remains necessary; raising heap or retrying is not a solution.

The helper/report audits in Specs 40–45 are separate populations and are not remeasured by this
ten-class selector. Their earlier scoped results must not be presented as fresh combined totals.

The default state/CBS scope is a separate gate with its own denominator. The original broader
parser run's expansion-guard resource failures belong to compiler-harness work (roadmap 9-V2),
not this ten-class result. This census does not assert whole-repository mutation closure or equal
resource/performance behavior for retained dispositions. Reopen a disposition if its declared
model, observer, purity or metadata assumptions change.

## Verification

Clean build,998 tests, strict Javadoc and the scoped census pass as measured above. All18
normalization checks pass. Separate default PIT and its full aggregate/non-assertion gates pass
at 281/291 KILLED,10 SURVIVED, zero uncovered/resource-error statuses; all 291 semantic identities
and statuses match Spec 46 evidence. CodeRabbit CLI0.8.1 completed its substantive three-file review with zero findings.
Final relative-link, credential-pattern and diff checks pass. Browser testing is not
repeated because this bundle changes only documentation; remote CI is unverified. Publish a draft
and stop after reporting its link.
