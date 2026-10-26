# Spec: Public trace-record serialization contracts

## Objective

Protect the complete public TraceRecord JSON shape, source terminal diagnostics and constructor
alignment in roadmap9-V1. A matching substring is not proof that the entire payload is valid JSON.

## Scope

Add TraceRecordSerializationTest, this specification and the roadmap update. No production, API,
dependency, output, default mutation scope/operator/exclusion or gate changes. Reuse the existing
JsonAssertions.parseObject strict parser; do not add a serialization framework or duplicate parser.
ReportWriter/report-level serialization stays separate. Spec40's property-selector component/79
resource limitation remains open and is neither rerun nor replaced by this scope.

## Requirements

1. Complete JSON objects SHALL retain ordered thread choices/outcomes, terminal verdict, nullable
   programHash and typed snapshot fields/locals, including arrays and escaped diagnostic strings.
2. Null/empty snapshots SHALL normalize to an empty value and omit states. Legacy records without
   evidence SHALL omit sourceDigest, executedSteps, failure and cutoff rather than adding nulls.
3. Snapshot enrichment SHALL preserve genuine source evidence and leave the original record unchanged.
   Runtime failures SHALL retain actual source identity, instruction metadata and failure diagnostics.
4. Source cutoffs SHALL preserve omitted-action evidence and expansion ancestry without inventing a
   recorded thread choice/outcome/executed step. No failure field SHALL appear alongside cutoff.
5. Constructors SHALL reject misaligned choice/outcome lists and source evidence whose recorded
   actions differ in length. Negative carrier inputs SHALL be rejected, not repaired before validation.
6. Source fixtures SHALL come from actual exploration and direct invariant-aware replay, not forged
   metadata or reconstructed traces. Expected JSON trees SHALL come from independent literals.

## Fixtures and independent oracles

Six cases include four value/constructor cases and two genuine source terminal cases. Strict object
equality checks both missing and extra keys, typed values and array order without freezing formatting
whitespace. The JSON helper uses strict Gson parsing and rejects malformed/trailing content.

### Legacy values, optional fields and alignment

- Choices[2,0], outcomes[ADVANCED,ASSERTION_FAILED], VIOLATION and a diagnostic hash containing a
  quote, backslash, newline, tab, HTML-significant characters and Unicode retain exact string values.
  One snapshot contains counter7, int array[1,4], bool array[true,false], and owner2 local tmp=-2.
  This is a carrier-value fixture, not an assertion that those arbitrary snapshots replay a program.
- Two rows explicitly supply null or empty snapshots with empty choices/outcomes, COMPLETED and
  null programHash. Getters return empty snapshots, and the complete wire object has only the four
  mandatory keys. The four-argument constructor produces the same literal JSON value.
- A choices/outcomes mismatch is rejected. Separately, an authentic one-action source evidence value
  paired with zero choices/outcomes is rejected before publication. Invalid source metadata is never
  sent through the replayer or used to claim execution coverage.

### Genuine source failure and enrichment

Exact source, with no trailing newline:

```text
program record { shared int x = 0; thread 0 { atomic { x = 1 % 0; } } }
```

The only trace is [0]/[ASSERTION_FAILED]/VIOLATION, with an atomic instruction at PC0, no expansion
ancestry and RUNTIME_ERROR/STEP/MODULO_BY_ZERO evidence. Enrich the actual record with the rolled-back
root x=0 snapshot. The expected JSON includes that snapshot and every source diagnostic; the original
still has no states. Compare evidence values and replay the genuine trace directly before serialization.

SHA-256 is the literal7fbbaf463008f20e53ffe0a8a50d5df07ab4d7827d077fed749d3a2587a604f4.
The atomic span is line1, half-open columns47–68; the failing assignment is56–66. The prefix lengths
are17 for `program record { `,18 for the declaration and11 for `thread 0 { `, totaling46 before
atomic. The atomic text has21 characters; its assignment starts nine characters later and has10.
Manual component counts and Python substring offsets independently confirm the literals.

### Genuine omitted cutoff

Exact source, with no trailing newline:

```text
program record { shared int x = 0; thread 0 { while (true) limit 0 {} } }
```

The only trace is empty choices/outcomes/INCOMPLETE. sourceDigest is present, executedSteps is[],
and cutoff is LOOP_LIMIT at owner0/instruction0/limit0/iterations0. The while span is line1,
half-open columns47–70; expansion ancestry is sourceOffset46/iteration0. Prefix46 plus the23-character
loop gives the exclusive end69+1=70. Manual counts and Python offsets cross-check each position.
SHA-256 is the literal21e419ad70e03cf280a0e2f7edb700f458663316abd36e4fc7c09a656c56581a.
Both hashes were independently computed with Python hashlib and shasum over the exact UTF-8 text.

The exploration property is a complete always-true observation supplied unchanged to direct replay.
No source identities are regenerated from already-produced trace metadata before verification.

## Constructor normalization disposition

TraceRecord's four-argument constructor /8 NonVoidMethodCall removes its empty List.of snapshot
argument, passing null to the delegated constructor. The six-argument constructor intentionally
normalizes either null or an empty snapshot list to the empty snapshot value. Choices, outcomes,
verdict, hash, evidence and JSON therefore keep the same values for otherwise valid inputs.

Both paths use valid total empty-list operations and identical alignment checks. This is a selected-
value disposition for this single constructor mutation, not permission to remove null normalization
in the full constructor. Allocation/list identity/work/resource/performance equivalence is excluded.
Keep constructors and production gates unchanged; no invalid model inputs force a kill.

## Baseline and verification

PR #83 merged at799a510. Fresh baseline:977 tests pass, zero failures/errors/skips. Scoped TraceRecord:
68/83 KILLED,14 SURVIVED,1 NO_COVERAGE, zero timeout/memory/run-error statuses. Scoped ratchet passes.

All six new cases and the full983-test suite pass, zero failures/errors/skips. Final scoped
TraceRecord:82/83 KILLED,1 SURVIVED,0 uncovered/timeout/memory/run-error statuses. All83 semantic
identities (class, method, descriptor, mutator, indices and blocks) match the fresh baseline; all68
old kills remain killed. Fourteen identities become kills: thirteen prior survivors and one uncovered.

| Method / bytecode indices | Mutation | Baseline → final |
|---|---|---|
| Six-argument constructor/32 | Removed null-state List.of normalization | NO_COVERAGE → KILLED |
| Six-argument constructor/51 | Removed evidence sourceDigest accessor in alignment check | SURVIVED → KILLED |
| toJson/190,207 | Negated failure/cutoff presence conditions | SURVIVED → KILLED |
| toJson/168,169 | Removed sourceDigest access/serialization calls | SURVIVED → KILLED |
| toJson/180,181 | Removed executedSteps access/serialization calls | SURVIVED → KILLED |
| toJson/189,197,198 | Removed failure access/serialization calls | SURVIVED → KILLED |
| toJson/206,214,215 | Removed cutoff access/serialization calls | SURVIVED → KILLED |

Comma-separated indices are separate identities. Both constructor entries refer to the full
six-argument descriptor, distinct from the retained four-argument constructor/8. The sole survivor
has the normalization disposition above:82 kills+1 scoped survivor disposition=83. No whole-checker
closure or resource/performance equivalence is claimed. The scoped ratchet passes.

CodeRabbit CLI0.8.1 completed the substantive three-file review with zero findings. Final clean build, strict Javadoc and all983 tests pass, zero failures/errors/skips. Default PIT
killed281/291 mutations and left10 SURVIVED, zero uncovered/timeout/memory/run-error statuses;
all291 semantic identities and statuses match merged Spec42 evidence. Both record-scoped and
default gates pass; Spec40's distinct property-selector resource limitation stays open. Only
measured verification/status prose was finalized after CLI review. All local Gradle commands use `--max-workers=1 -PpitestThreads=1`. Browser integration
is outside this bundle because executable production/report output is unchanged. Remote CI remains
unverified; publish as draft and stop. Historical expanded9-V1 totals remain unremeasured; separate
scoped populations do not form a whole-checker score.
