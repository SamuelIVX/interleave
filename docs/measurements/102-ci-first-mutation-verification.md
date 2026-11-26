<!-- Spec102 implementation evidence; remote execution/calibration remain separate pending gates. -->
# 102 — CI-first mutation verification

## Status and baseline

The implementation starts from freshly verified remote main
`555517730eb1877de57c2d9d1de740e591d6272c` (merged specification PR #144). It lives in isolated
`interleave-ci-first-mutation`, branch `feat/ci-first-mutation`. The original dirty checkout and
unfinished Spec 97 worktree remain untouched. No interpreter, search, storage or consumer production
Java source changes in this bundle.

Local tooling/configuration checks and review-fix verification pass. Complete remote baseline/candidate mutation
runs, real shard calibration, runtime/resource improvements and manual acceptance remain **pending**.
Acceptance still uses full mode. This ledger makes no claim that local file fixtures or historical
PIT reports establish a fresh census, a calibration result or a speedup.

## Delivery and gate ownership

- `tools/mutation.py` owns the supported local/controller interface. `mutation_evidence.py` owns the
  shared XML identity and conservative method-comparison implementation; the historical 94C command
  imports it rather than retaining a competing parser.
- `mutation-inputs.init.gradle` exports resolved task options and classpaths from either revision.
  The common controller does not overlay candidate build scripts or production/test sources onto
  the baseline. Build/Javadoc/tests/default PIT run once per revision, not per shard.
- Existing Build, visualizer and default mutation gates remain. The Build workflow exports exact
  candidate build/default evidence; the reusable affected workflow validates that reuse or reruns
  missing/incompatible checks, retaining rejected diagnostics separately.
- Automatic analysis uses the immutable PR base and merge candidate, recording the head/repository
  separately. Manual dispatch uses main's controller and same-repository reachable/ancestral SHAs.
- The affected population is the Spec 102 frozen bytecode/consumer set; default state/CBS remains a
  distinct population. All eligible tests and mutation operators remain available.
- Full mode supplies acceptance. Initial relevant PRs also request two four-shard calibration trials,
  at at most four concurrent analysis jobs and one PIT minion per affected job. A failed/missing trial
  fails validation; it cannot disappear into the full reference's success.

## Test evidence and regression intent

The small Python suite currently passes 31 tests, using actual files, private Git repositories,
fixture subprocesses and independently compiled javac/javap cases. It does not mock the checker or
claim to run PIT. Tests cover:

- Semantic identities/statuses, duplicates, truncation, missing coordinates and forbidden declarations.
  A red regression demonstrated that UTF-16 declarations bypassed a byte-pattern guard. The parser
  now accepts the pinned UTF-8 format and rejects alternative/NUL encodings before declaration checks.
- Duplicate JSON keys, nonfinite numbers, nonobject manifests and unsupported record versions.
- Operand-only pool normalization preserving `"#12"` versus `"#34"`, real added-method inventories,
  changed `synchronized` declarations and conservative unmatched bootstrap/NaN data. The latter two
  cases were observed red before correction. Javap text alone cannot prove raw NaN payload equality.
- Real immutable Git merge relationships, documentation-only decisions and manual ancestry rejection.
- Full input consistency, completed engine totals/process status, baseline check ownership, candidate
  reuse identity, raw-count aggregation and lost matched kills. Test edits invalidate the complete
  candidate population; a stale other-class kill cannot fill it.
- Complete/disjoint partitions, explicit empty jobs, full versus sharded identities, missing trials
  and mismatching calibration. Synthetic counts are fixtures, not Interleave measurement results.
- An actual local wrapper process with a fixture executable: both serialization flags, the shared
  worktree lock, selector injection rejection and cancellation/child cleanup, including a detached
  daemon ignoring TERM after its launcher exits. The fixture never runs PIT. The wrapper observes
  descendant identities across process groups and retains a live-process marker on cleanup failure.
  Direct expert Gradle launches are outside the cooperating wrapper's lock contract.
- Artifact-copy failure after a fixture Gradle exit0 leaves `complete:false`, even with copied XML.
  A skipped Javadoc task cannot establish full checks. Effective feature changes, ordered classpaths,
  coordinator JDK mismatches and changed logical runtime-JAR payloads invalidate provenance.
- Independently compiled moved line tables and trailing-space Strings remain unresolved rather than
  producing false inherited-kill matches. Raw same-revision calibration identities remain unchanged.

Shard-vector expectation uses OpenSSL's independent SHA-256 of `example.A`:
`031c99d5aa6360412786a42186f5220a0107d7bb6e7d2a0fc3aea47608aed740`.
The first eight bytes end in hexadecimal41; their unsigned value modulo4 is literal1. Nested names
use the same family. Aggregate expectations are literal two affected kills and one separate default
row in the bounded file fixture, not percentages averaged across populations.

## Local execution and reproduction

Python 3.10+, Git and JDK26 are required. Ordinary Gradle `check` includes Python tool tests, with
JDK26 selected for their independent compiler fixtures. Local Gradle commands always include both
required serialization flags.

```sh
python3 -B -m unittest discover -s tools -p 'test_*.py'
python3 -B tools/mutation.py local --classes dev.samhb.interleave.bytecode.BytecodeFrame
./gradlew --max-workers=1 -PpitestThreads=1 build javadoc
```

The wrapper's local command launches a real scoped PIT run; do not run that command just to test the
wrapper. The unit suite uses its bounded fixture executable instead. `ci-run` is restricted to
GitHub Actions to prevent accidentally starting unrestricted remote-style work on the Mac.

Two meaningful exporter checks compiled/resolved inputs without executing PIT. The first attempt
used the shell's JDK27 launcher, which the Groovy init parser rejected with class-file major71.
Selecting the required JDK26 launcher fixed it. The resolved exporter then passed, including actual
mutable paths, PIT classpath directories, plugin/engine jar digests, broad tests and disabled history.
This is configuration compatibility evidence, not mutation or performance evidence.

Raw local evidence lives in sibling `interleave-102-evidence`: `metadata-smoke*`, `metadata-final*`,
verification logs and saved CodeRabbit output. Actionlint1.7.12 was downloaded from its official
release and verified against published archive SHA256
`aba9ced2dee8d27fecca3dc7feb1a7f9a52caefa1eb46f3271ea66b6e0e6953f` before use. Both workflow files
pass its syntax/expression checks. No project dependency or review configuration was changed.

Integrated build and strict Javadoc passed. The first integrated execution ran all2171 Java tests
with zero failures/errors/skips (55seconds). The review-fix run passed31 Python tests and reused
unchanged Java/Javadoc outputs, completing in6seconds. Actionlint passes both workflow files.
No local PIT ran. The actual Gradle-to-Python pre-PIT guard passed an exporter-only invocation.

The short Gradle `help` process trace observed a single-use daemon leaving the launcher's process
group. It is process-lifecycle evidence, not mutation performance evidence. A declared NO-SOURCE
directory exposed a real exporter failure during review-fix validation; ordered classpath export now
retains its empty directory role instead of attempting to digest it as a JAR.

## CodeRabbit review dispositions

The first CLI attempt failed with a closed WebSocket (exit1, no completed review); doctor then passed
9/9 checks. After Sam explicitly requested a new run, CodeRabbit0.8.1 completed the15-file review:
13 findings, comprising11 major and2 minor. Raw output: `coderabbit-implementation.ndjson` in the
external evidence directory. This is completed review evidence, not a clean-review claim.

| Finding | Disposition |
|---|---|
| Manual workflow dispatch from a feature ref | Valid; reject before controller checkout and in revision selection; summary checks dependencies before checkout. |
| Manual sharding before calibration | Already prevented; workflow hardcodes full mode and exposes no mode selector. Diagnostic CLI sharding cannot activate workflow acceptance. |
| Rename escaping watched paths | Valid; use `git diff --no-renames`; real Git fixture enables rename detection and moves a watched file out of the tree. |
| Gradle success before artifact completion | Valid; independent completion commit after all artifact/provenance checks; failed-copy regression. |
| Effective PIT features absent | Valid; export and compare ordered configured plus additional features. |
| Debug-sensitive raw mutation indexes | Valid; separate body normalization from coordinate proof; changed class bytes remain unresolved. |
| Lossy javap String rendering | Valid; preserve available literal whitespace and require exact class bytes for automated coordinate matching. |
| Partially written fixture arguments | Valid minor; atomically rename the complete fixture JSON. |
| Launcher exits before descendants | Valid; supervise observed descendant identities across groups, clean on exit/cancellation, retain orphan marker. |
| Requested tasks versus executed checks | Valid; record observed outcomes and reject skipped/no-source/missing/failed checks. |
| Coordinator JDK versus minion JDK | Valid; probe and require JDK26 for both actual executables. |
| Classpath order lost | Valid; preserve ordered files/directories and bind directory contents, including empty declared paths. |
| Unresolved normalization reasons | Valid minor; explicit unresolved bootstrap/constant, coordinate and inventory reasons. |

Independent review also found generated agent/release JARs were not bound through the ordinary test
classpath. Their actual logical ZIP entries are now fingerprinted, rejecting duplicate entries and
ignoring only ZIP timestamps/order.
The bounded final CLI follow-up was invoked on the corrected15-file set, but the session ended
before a completion event. Its saved output stops at reviewing heartbeats; the process/session no
longer exists. This is an interrupted attempt, not a second completed or clean review. No duplicate
was launched on resumption. The completed first review supplies required CLI evidence; all valid
findings above received local regression/configuration verification. Final tool tests pass31/31.
The actual pre-PIT CLI also rejects changed effective features and reordered otherwise identical
classpath entries (exit1, mismatched protocol/inputs), without invoking PIT.

Final source AST checks,80 relative documentation links and `git diff --check` pass. Context validation
has zero errors and one pre-existing unrelated portability warning. Applicable review evidence is
recorded through agent-setup. Independent review used sequential passes; no additional agents were
spawned for implementation or review. Browser and local mutation reruns were omitted because no Java
or viewer source changed, and complete mutation evidence belongs to this PR's pending CI gate.

## Remote acceptance and calibration

After publication, CI must provide fresh full baseline/candidate build/Javadoc/tests/default PIT and
full affected reports, plus the two requested candidate shard trials. Logs, resolved protocol,
compiled class snapshots and input fingerprints are uploaded even on failure; uploading is not
success. Collection rejects missing/failed jobs, wrong revisions/controllers, inconsistent inputs,
changed protocol, duplicate identities and raw/engine-total disagreement.

The default population retains the existing ratchet, including its adjudicated equivalent allow-list.
Only KILLED earns assertion-backed credit. The new affected population accepts only KILLED, SURVIVED
and NO_COVERAGE. Whole-class replacement is allowed for acceptance only under identical full inputs;
focused test-edit follow-ups are diagnostic until the complete revised population runs.

Comparison preserves proved matched unchanged-method kills. Candidate-only/removed/changed/unproven methods,
including zero-mutant cases, remain in the comparison and require human dispositions here and in the
PR. Bootstrap/constant-dynamic behavior or NaN payloads unresolved by javap remain unmatched. A green
automated comparison alone does not close those obligations.

The comparison adapter retains declaration modifiers and does not globally erase `#digits` inside
resolved constants. Historical 94C invocation and derived output field names remain available, with
added-method reporting. Its mixed-input reconstruction remains explicitly historical/diagnostic and
cannot feed the new acceptance aggregate. Raw historical reports/statuses are unchanged.

The written coordinate argument is deliberately narrow: identical defining class bytes and an
identical PIT protocol preserve ClassReader input, ASM node order, mutation generators and raw
coordinates. A normalized executable body alone proves none of that node ordering. Changed class
bytes therefore cannot establish an automated lost/preserved kill; they produce a visible human
obligation. Bootstrap/NaN behavior remains unresolved even for identical class bytes. The comparison
includes reported mutant methods omitted from a disassembly inventory so omissions cannot hide kills.

CI records elapsed seconds per execution and retains coverage/timing logs. `peakRssBytes` is explicitly
null: the collector does not measure simultaneous process-tree resident memory. A heap cap is not
that measurement. After CI, record actual counts, matched kills, unmatched dispositions, calibration
identity/status equality, raw run links, elapsed/aggregate runner time, coverage cost, shard imbalance
and unavailable resource metrics. No invented savings or percentage target is used.

Enable sharded acceptance only after Sam reviews all calibration evidence: change the reviewed
workflow plan mode to `sharded` and disable repeated bootstrap calibration. Until then `full` remains
the gate. A mismatch keeps full mode and visible failure; later fallback requires its own fresh
successful full run and explicit reason. The new baseline-timing policy is not retroactive: Spec 97's
original deviation remains recorded, and prospective adoption awaits implementation/calibration.

After publishing this draft implementation PR, report its link and stop. Sam supplies CI/review/merge
results. No CI watcher, Spec 97 resumption or later milestone starts automatically.
