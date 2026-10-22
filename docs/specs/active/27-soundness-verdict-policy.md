# Spec: Soundness attestation verdict policy

## Objective

Protect expected-verdict matching and cross-strategy agreement in a focused roadmap 9-V1 batch.
Separate controlled row-policy tests from claims about an explorer's actual reachable outcomes.

## Scope

- Adds `SoundnessVerdictTest`; updates this spec, the roadmap and verdict-policy class Javadoc.
- No executable production/API, dependency, mutation scope/mutator/exclusion or gate changes.
- Violation replay/diagnostics, Markdown rendering and reduction/serialization remain separate.
- Mutation evidence targets the outer SoundnessAttestation class, not its nested check-result class
  or the full historical expanded population.

## Invariants and Requirements

1. EXACT DFS rows SHALL match a registered non-null expectation, except that source programs may
   report INCOMPLETE or APPROXIMATE_PASS as inconclusive. Legacy DFS matching stays strict.
2. Registered programs with known non-VIOLATION expectations SHALL have consistent conclusive
   EXACT verdicts across strategies. Unknown expectations and expected-VIOLATION programs SHALL
   not be treated as known-correct programs by this agreement check.
3. INCOMPLETE and APPROXIMATE_PASS rows SHALL neither seed nor conflict with cross-strategy
   agreement, regardless of encounter order.
4. BITSTATE rows SHALL be excluded from expectation matching and exact agreement, regardless of
   encounter order. This exclusion does not weaken the separate positive violation replay check.
5. Verdict failures SHALL identify the subject, relevant strategy and disagreeing labels; successful
   attestations SHALL have no failure reason.

## Design and Tests

Use registered source/legacy definitions with supplied expectation metadata and literal row labels.
These are deliberate input-policy oracles, not evidence that the simple fixture model genuinely
produces every supplied verdict. All actual row labels here are non-VIOLATION, keeping replay out
of this contract. Existing real-corpus bounded-search/replay tests remain unchanged.

Negative cases require `isSound == false` and complete literal diagnostic templates with supplied
subject/strategy/verdict values; an unexpected exception is not accepted as a failed attestation.
Positive cases require both a sound result and a null reason. Before/after order cases distinguish
ignoring an excluded row from letting it seed an agreement and merely ignoring later conflicts.
Class Javadoc now distinguishes source-only DFS exemptions from general cross-strategy exclusions.

| Requirement | Regression | Cases |
|---|---|---|
| R1, R5 | `validatesExactDfsExpectations` | 13 source/legacy, matched/mismatched, inconclusive and absent-expectation cases |
| R2, R5 | `checksAgreementOnlyForKnownNonViolationExpectations` | Seven source/legacy agreement/disagreement/expected-VIOLATION/unknown-expectation cases |
| R3, R5 | `inconclusiveRowsDoNotSeedOrConflict` | Four label/order cases |
| R4, R5 | `bitstateDfsIsExcludedFromExpectationMatching` | Two deliberately differing bitstate DFS expectations |
| R4, R5 | `bitstateRowsDoNotSeedOrConflict` | Two approximate/exact encounter orders |

## Acceptance Criteria

- All 28 new invocations and the full existing suite pass without executable production changes.
- Compare fresh merged-main/final semantic mutation populations; retain every baseline kill and
  explicitly classify residual replay/rendering/bookkeeping identities rather than force 100%.
- Full build/tests/Javadoc, unchanged default mutation gate and normalization pass.
- CodeRabbit CLI reviews the four-file bundle; independently disposition any findings.

## Baseline

Merged PR #67 is `854bef5`. Fresh baseline passes 780 tests, no failures/errors/skips;
outer-SoundnessAttestation PIT is **64/112 assertion-confirmed kills**, 30 survivors and 18 uncovered,
zero timeout/error detections. Existing target override measures this class independently of
default state/CBS gates. Local Gradle always uses `--max-workers=1 -PpitestThreads=1`.

## Verification

All **808 tests** pass (28 new invocations), zero failures/errors/skips. Outer-SoundnessAttestation
PIT improves **64/112 → 85/112 assertion-confirmed kills**: 15 survivors and six uncovered mutations
become killed. Fifteen survivors and 12 uncovered remain, zero timeout/error detections. All 112
semantic identities match and all 64 baseline kills remain killed. Comparison uses class/method/
descriptor/mutator plus instruction indices and blocks, not line numbers.

### Scoped mutation ledger

All identities belong to `dev.samhb.interleave.report.SoundnessAttestation`; indices identify
bytecode instructions. Executable production code and mutation scope are unchanged.

| Baseline → final | Method / indices | Contract / disposition |
|---|---|---|
| → KILLED (6) | `checkSoundness` / 55, 65, 71, 72, 98, 99 | Registered expectation lookup, DFS selection and mismatch result; literal negative DFS cases distinguish missing checks from failed attestations. |
| → KILLED (10) | `checkSoundness` / 112, 115, 116, 121, 126, 143, 146, 152, 153, 160 | Correct-program classification, agreement bookkeeping and complete mismatch diagnostics; different exact labels must conflict only under the declared policy. |
| → KILLED (2) | `isInconclusive` / 10, 20 | Inconclusive classification cannot suppress conclusive disagreement or let an inconclusive row seed agreement. |
| → KILLED (3) | `lambda$checkSoundness$0` / 4, 6, 14 | Exact-store filter; bitstate rows must remain outside both verdict checks. |
| SURVIVED (1) | `checkSoundness` / 106 | Removed write to local `dfsVerdicts`; no later read or publication observes that private map. Attestation-output disposition, not equal work/allocation claims. |
| SURVIVED (3) | `checkSoundness` / 255, 269, 274 | Invariant retrieval/guard/holds checks during positive violation replay; separate genuine/counterfeit replay batch. |
| SURVIVED (3) | `checkSoundness` / 208, 210, 211 | Missing-trace strategy/store diagnostic fields; separate replay diagnostics batch. |
| NO_COVERAGE (3) | `checkSoundness` / 234, 236, 237 | Unknown-program violation result/diagnostic; separate replay diagnostics batch. |
| NO_COVERAGE (6) | `checkSoundness` / 295, 299, 301, 302, 306, 307 | Replayed non-violation result/diagnostic; separate counterfeit-replay batch. |
| SURVIVED (8) | `formatMarkdown` / 11, 22, 40, 46, 52, 58, 64, 92 | Successful report claims/layout fragments; separate rendering contract. |
| NO_COVERAGE (3) | `formatMarkdown` / 72, 79, 85 | Failed attestation rendering; separate failure-report contract. |

The retained totals sum to 15 survivors and 12 uncovered. The unused `dfsVerdicts` map is allocated
inside each check, receives immutable String keys/values and is never read or exposed; removing
its ignored put cannot change returned attestation values for valid inputs. Resource failures,
allocation/work/performance equivalence are not claimed. Production bookkeeping remains unchanged;
no private-call assertions, exclusions or score-floor changes force a kill.

All other retained identities are explicit follow-up gaps, not equivalence declarations.
Post-feedback `clean build javadoc pitest` passes all 808 tests and Javadoc. The unchanged default
mutation gate passes at **281/291**, with the same semantic identities and statuses as merged main.
All 18 normalization checks pass. Browser integration was not rerun because no executable output
or browser behavior changed; CI is unverified at publication.


### CLI feedback

The first four-file CodeRabbit review completed with one trivial finding: negative conclusive
cross-strategy cases used only legacy programs. Independently verified; add a source-program
PASS-versus-DEADLOCK disagreement with the same explicit expected diagnostic. This guards against
accidentally excluding all source rows rather than only their inconclusive verdicts. The follow-up
four-file CLI review completed with zero findings. All checks above were rerun after the test fix;
only measured verification prose was finalized after that review.
