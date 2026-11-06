# Checked source symmetry: exact DFS evidence

## Scope and guarantee

Continue #10 after merged PR #117 at ac16444. The checker infers disjoint groups with identical
metadata-free SourceInstruction trees/targets and equal initial local values. Every instruction
binding and forward target is checked before initial enabledness; unknown source steps reject
without invoking their callbacks. Only absent or compiler-built shared SourceProperty is accepted.
Nested tid reads exclude an owner from grouping, including unused branches, array indices and
assertions. Correctly bound non-grouped owners remain fixed and may observe their own tid.

[Spec 76](../specs/active/76-source-symmetry-exact-search.md) states the initial-state, transition,
property, termination/deadlock and representative-witness argument. It applies to supported pure
source semantics, not arbitrary callbacks, reduced-strategy composition or a machine-checked proof.
A checked explorer privately owns its orbit store and fresh DFS; callers cannot certify one
program and then reuse that store for another program/property. Concrete configurations execute
and emit original IDs, digests, spans, helper labels, outcomes, failures and cutoff evidence.
Canonical copies are used only for immutable value keys. Witnesses represent verdict orbits, not
all owner-specific failures, cutoffs or schedules.

## Independent state-count oracles

Spec 75 independently derives mask/orbit and split-increment counts with Python enumeration and
hand counting. Here actual search, not offline key collection, must realize those counts:

| Model | Ordinary DFS | Checked symmetry DFS | Independent reason |
|---|---:|---:|---|
| Two one-action identical owners | 4 | 3 | Two-owner binary masks; completed-owner totals 0,1,2 |
| Four one-action identical owners | 16 | 5 | Four-owner binary masks; totals 0 through 4 |
| Eight one-action identical owners | 256 | 9 | Eight-owner binary masks; totals 0 through 8 |
| Two split load/store increments | 13 | 8 | (shared, multiset of (PC,local-r)); terminal values {1,2} |
| Two noncontiguous groups {0,2}/{1,3} | 16 | 9 | Two completed-owner counts, each 0 through 2: 3*3 |
| One group {0,2}, fixed tid-observing owner 1 | 8 | 6 | Group count 0 through 2 times fixed-owner binary completion: 3*2 |

Separate Python Cartesian enumeration confirms the last two 16/9 and 8/6 counts; the product
argument is the second route. Java asserts the literals. Tests replay original witnesses and
retain distinct lost-update final values, helper callsite labels, statement/runtime/property
failures, final/always timing, deadlocks and loop cutoffs. Mutation regressions independently
reject true/false self-target boundaries, IF versus LIMIT semantics, different LIMIT bounds and
nested else-branch tid. The limit-bound fixture recomposes trusted bound source instructions
through public Program construction; it does not claim to be a newly parsed source program.

## User-facing behavior and integrated checks

The library builder symmetryReduction(true) selects built-in exact DFS. Non-DFS strategies and
explicit custom stores/factories reject rather than being ignored. Eligibility validates the
actual run/property, and per-run membership isolates reuse/concurrent runs. State/time limits
retain explicit INCOMPLETE causes, approximate=false and symmetry identity on partial results.

CLI --symmetry requires a source, explicit exact store and DFS, without CBS options. Unsupported
models return a controlled diagnostic. Actual Main child JVM tests compare the four-owner helper
example at 16 versus 5 states, verify PASS/soundness, and retain DFS/EXACT machine identity with
symmetryReduction:true. A split-increment CLI run visits eight states, returns VIOLATION, retains
four original choices/five snapshots and passes actual trace replay attestation. These child
process assertions are not claimed as mutation-covered Main bytecode.

JSON emits the new field only for enabled runs. Markdown groups/displays DFS + symmetry separately
from ordinary DFS, retaining both rows in mixed reports; only ordinary DFS supplies a baseline.
The viewer carries the flag through report/library normalization and visibly labels orbit counts.
CI generates the actual CLI artifact via MainTest.sourceSymmetryCliCountsAndReportIdentity before
passing SOURCE_SYMMETRY_JSON into the browser suite. Parallelism and dependencies are unchanged.

Fresh ac16444 baseline clean build/Javadoc/1,543 tests and full default mutation gate pass.
Pre-review integrated implementation tests total 1,583, zero failures/errors/skips (40 new cases).
Private-member Javadoc for all four core source/canonicalization files passes all doclint groups
and -Werror; existing undocumented private fields/exception parameters in the touched instruction
file were documented without execution changes. Existing 22 verdict normalization cases plus
four explicit mode-identity assertions pass. Full Playwright suite: 49 passed, zero failed and
zero console errors/exceptions, including the fresh CLI quotient report. Workflow YAML parses;
the artifact producer, consumer and test selector agree. Actionlint is not claimed.

## Mutation populations and retained raw residuals

Fresh inherited SourceInstruction baseline: 126/127 K + 1 S. First combined eligibility/explorer/
instruction scope: 275/285 K + 10 S, zero uncovered/resource/run errors. Missing branch/kind/bound/
validation cases were added without production or mutation-setting changes. Pre-review final: **281/285 K
+ 4 S**, zero uncovered/resource/run errors; all 285 identities remain, exactly six S→K changes.
Every inherited SourceInstruction identity/status is retained; no old survivor earns kill credit.

| Pre-review class | Assertion K / total | S |
|---|---:|---:|
| SourceInstruction | 187/189 | 2 |
| SourceSymmetry | 77/78 | 1 |
| SymmetryDfsExplorer | 6/6 | 0 |
| SymmetryDfsExplorer.OrbitStore | 11/12 | 1 |

Scoped R6 passes, R5/R7 skip as configured. The default state/cb population is separate:
**433/449 K + 16 S**, zero uncovered/resource/run errors, full R5/R6/R7 pass; all 449 complete
semantic identities/statuses exactly match the fresh merged baseline. No denominator, floor,
target/filter/operator/test selection/resource/CI parallelism changes. Never add these overlapping
measurements or treat one as an aggregate over the whole checker.

All four pre-review raw survivors are NonVoidMethodCallMutator. Descriptors remain in the frozen XML;
method/index/block identify them below. No disposition is an assertion kill or a filtering rule.

| Class / method / index / block | Scoped disposition |
|---|---|
| SourceInstruction / metadata / 24 / 8 | Inherited survivor: a removed isEmpty call still prefixes nonempty callLabel; for empty label, concatenating the empty string preserves the label value. Scope is immutable non-null compiler-built labels; see Spec 71. |
| SourceInstruction / usesTid / 12 / 2 | Compiler-generated requireNonNull at pattern-switch entry follows the explicit null-return guard. Every value reaching this call is non-null, so removal cannot change expression classification. |
| SourceSymmetry / groups / 5 / 0 | Removing program requireNonNull still throws NPE at program.isSource. No exact diagnostic-message/stack claim. |
| OrbitStore / clear / 5 / 0 | Private store is freshly constructed per checked run and never escapes; DFS clears it before first use when it is empty. Keep the interface implementation; revisit if reuse/caching is introduced. |

## Reproduction and publication boundary

All raw /private/tmp paths are local-only forensic evidence, not shared artifacts. Frozen XML:
interleave-symmetry-search-default-baseline.xml, interleave-symmetry-search-instruction-baseline.xml,
interleave-symmetry-search-first-scoped.xml, interleave-symmetry-search-final-scoped.xml and
interleave-symmetry-search-default-final.xml. Baseline is merged ac16444; final source is this PR.
Gradle writes build/reports/pitest/mutations.xml; copy it before the next PIT invocation overwrites it.

```sh
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest
./gradlew --max-workers=1 -PpitestThreads=1 pitest '-PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceInstruction*,dev.samhb.interleave.format.dsl.SourceSymmetry*,dev.samhb.interleave.search.SymmetryDfsExplorer*'
./gradlew --max-workers=1 -PpitestThreads=1 -q run --args='--source examples/programs/symmetry-counter.ilv --store exact --strategy DFS --symmetry --json'
```

No runtime speedup is claimed from these state counts or nonbenchmark timing fields. Pre-review
post-mutation ordinary build/Javadoc/1,583 tests and regenerated-artifact browser checks pass;
49 browser assertions and all normalization checks pass with zero console errors.

Initial CodeRabbit CLI review completed all 23 files: one major request makes usesTid exhaustive
over sealed Expr, one trivial request adds local-array tid read/assertion cases, and two minor
requests align status wording. The major fix is preventive: no current Expr variant was omitted.
Every leaf/composite is now explicit, so adding a sealed variant requires a compiler-visible
classification. Local-array read and assertion tid cases reject; symmetry plus cutoff simplification
also passes actual CLI authentication with an empty prefix, original omitted owner/limit and one
fresh root snapshot.

Post-fix integrated build, strict/private Javadoc and **1,585 tests** pass, zero failures/errors/
skips (42 new cases). Final core scope remains **281/285 K + 4 S**, zero uncovered/resource/run
errors. Compared with the pre-review version, 274 semantic identities/statuses are unchanged;
11 killed usesTid identities are replaced by 11 killed identities after the exhaustive switch
rewrite. The denominator coincidentally stays 285; this is not a claim of retaining every old ID.
Every inherited 127 SourceInstruction identity/status remains unchanged. Raw final local-only XML:
/private/tmp/interleave-symmetry-search-exhaustive-scoped.xml. The same four scoped survivor
arguments apply, with the same method/index/block identities. No filter/resource/score adjustment.

Post-fix default gate: **433/449 K + 16 S**, zero uncovered/resource/run errors; full R5/R6/R7
pass, all 449 identities/statuses exactly match the fresh baseline. Local-only XML:
/private/tmp/interleave-symmetry-search-exhaustive-default.xml; successful log:
/private/tmp/interleave-symmetry-search-exhaustive-default.log. #10 strategy composition
remains separate; do not mark #10 complete or start #11/#12 while required #10 work remains.

Final CodeRabbit CLI 0.8.1 review **completed all 23 files with zero findings**; agent-setup
review-record saved. Local-only review logs: /private/tmp/interleave-symmetry-search-review.ndjson
and /private/tmp/interleave-symmetry-search-review-final.ndjson. This second core review verifies
the major exhaustive-expression fix, not a repeated check of minor/trivial feedback.

Final local review found a separate constructor-ownership gap after the completed CLI review:
BenchmarkHarness retained caller filter sets. A real regression mutates them after constructing
checked DFS; before the fix, it selected DPOR instead (expected DFS, got DPOR). The constructor now
copies both filters before validating its owned snapshots, closing post-validation mutation and
preserving the checked mode. This is a small local fix after the two completed CLI reviews; no
third CLI invocation is claimed. Red/green logs are local-only under
/private/tmp/interleave-symmetry-search-filter-{red,green}.log.

Final ownership-fix integrated build, strict Javadoc and all **1,585 tests** pass, zero failures/
errors/skips. The final default gate also passes **433/449 K + 16 S**, no uncovered/resource/run
errors, all 449 identities/statuses match baseline, full R5/R6/R7 pass. Frozen local-only XML:
/private/tmp/interleave-symmetry-search-ownership-default.xml; successful log:
/private/tmp/interleave-symmetry-search-ownership-final.log. No source-core mutation target changed
in that filter snapshot fix; the final affected-core scope above remains its measured evidence.

Final ordinary artifact producers pass after mutation work, followed by the complete browser/
normalization suite: **49 browser assertions pass**, zero failures/console errors. Local logs:
/private/tmp/interleave-symmetry-search-artifact-producers.log and
/private/tmp/interleave-symmetry-search-browser-ownership-final.log. Workflow YAML and the actual
producer-selector/environment linkage validate. The Spec 75 status note now records its merged
foundation and points to this phase's public value API. No remote CI result is claimed.
