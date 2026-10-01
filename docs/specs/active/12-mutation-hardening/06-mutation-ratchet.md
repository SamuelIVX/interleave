# Spec 12.06 — Mutation Ratchet, Threshold Accounting & Wall-Time Kills

## TL;DR

Every PIT threshold is currently `0`, so the `mutation` CI job reports a number and gates nothing.
This spec turns it into a real gate — at the correct floor, with the correct metric, and with an
accounting rule that stops the score being inflated by mutants that no assertion caught.

Two facts drive the design, both measured rather than assumed:

1. **PIT rounds mutation coverage to the nearest integer.** `222/275` renders as `81%`. Threshold 81
   passes *only* on the strength of the single mutant PR #29 added, and fails at the pre-Part-B
   baseline of `221/275`.
2. **`TIMED_OUT` and `MEMORY_ERROR` count as detected** — the same flag as `KILLED` — so a mutant
   killed on wall time raises the score with no assertion behind it. For a model checker this is the
   dangerous direction: a mutant that breaks deduplication runs *slower*, which is exactly the class
   worth catching.

The ratchet also must not trust its own percentage. At `mutationThreshold = 80`, the gate tolerates
losing **three** kills from the current 222 and **fails on the fourth** (218) — it fails at
`mutationThreshold − 4`, not `− 3`, because the gate trips at the first figure that rounds *below* the
threshold. So the mutant count and status breakdown are asserted alongside the percentage, because
the rounded percentage is structurally blind to small movement.

## Objective

Make the mutation score a gate that cannot silently regress, on the correct metric, at a floor
derived from measurement rather than from the current number — and make the score's known
inflation paths visible instead of assumed away.

## Scope

- **Package:** `interleave` / build configuration and CI
- **Modifies:** `build.gradle.kts` (thresholds + reporting), `.github/workflows/build.yml`
  (gating step), and this spec's §R2 derivation record
- **Off-limits:** `src/main/**` and `src/test/**` — this spec changes no production or test code. If
  remediating a gap requires code, that is Specs 12.01–12.05.

## Non-Goals

- Raising the score. The ratchet's job is to detect regression, not to reward. If the number does not
  move after Specs 12.01–12.05, that is a finding about those specs, not a reason to set the floor
  higher.
- Mutating more code. The scope (`state.*`, `cb.*`) and the 12-mutator list are deliberate and
  unchanged; widening them changes the denominator and invalidates the floor.
- Replacing PIT. The integer-threshold blind spot is a real limitation of this plugin, and the
  mitigation is to assert counts alongside the percentage, not to re-platform.

## Current State

All claims [verified] against `build.gradle.kts`, `.github/workflows/build.yml`, and
`build/reports/pitest/`.

### The thresholds gate nothing today

```kotlin
mutationThreshold.set(0)
coverageThreshold.set(0)
testStrengthThreshold.set(0)   // a no-op: explicitly setting the default to its own value
```

The comment above them explains the intent — "Baseline mode: both gates off until real numbers
exist. Ratchet from below." That was the right call for PR #28. It is now debt.

### Three metrics, three denominators

Both columns are the same run on the same scope; the two runs differ by the one mutant PR #29 added,
and **the numerator in every row moves together with its denominator**. Pairing a post-Part-B
numerator with a pre-Part-B denominator produces a figure PIT never printed.

| metric | pre-Part-B (`313df44`) | current (`c5fdcd0`) | denominator | gated by |
|---|---|---|---|---|
| line coverage | 223/235 = 95% | 223/235 = 95% | covered lines | `coverageThreshold` |
| **mutation coverage** | 221/275 = 80.36% | **222/275 = 80.73%** | **all mutants, incl. `NO_COVERAGE`** | `mutationThreshold` |
| test strength | 221/261 = 84.67% | 222/261 = 85.06% | *excludes* `NO_COVERAGE` | `testStrengthThreshold` |

The current run's test strength is **222/261**, not the `221/261` the build comment carries — the
build comment was written against the pre-Part-B run and has not moved, since `build.gradle.kts`
predates Part B and nothing in this spec set touches it. Line coverage is unchanged across both runs
because Part B added tests, not production code, so the covered-line count did not move.

Only test strength excludes `NO_COVERAGE`. **Mutation coverage is the correct floor** because it
counts unexercised code — a metric that quietly ignores the 14 dark mutants would hide exactly the
gap this whole spec set exists to close.

### PIT rounds to nearest, not floor [verified]

From `build/reports/pitest/index.html`, the report renders `222/275` as `81%`.

| killed | exact | PIT reports | passes 80? | passes 81? |
|---|---|---|---|---|
| 222 (current) | 80.73% | **81** | yes | yes |
| 221 (pre-Part-B) | 80.36% | 80 | yes | **no** |
| 220 | 80.00% | 80 | yes | no |
| 219 | 79.64% | 80 | yes | no |
| 218 | 79.27% | **79** | **no** | no |

**Threshold 81 fails at the pre-Part-B baseline.** It is only satisfiable by the one mutant PR #29
added, so it would make the gate depend entirely on that single kill. Threshold 80 passes down to
219/275 and **fails at 218/275 — three kills tolerated, the fourth trips it.**

### Wall-time kills score as detected [verified]

`DetectionStatus`'s static initialiser in `pitest-1.30.0.jar` builds each constant as
`(name, ordinal, detected)`:

| status | `detected` | earned by |
|---|---|---|
| `KILLED` | **true** | a failing assertion |
| `TIMED_OUT` | **true** | exceeding `timeoutFactor` / `timeoutConstInMillis` |
| `MEMORY_ERROR` | **true** | exhausting the minion heap |
| `NON_VIABLE` | **true** | PIT rejecting the mutant at compile time |
| `RUN_ERROR` | **true** | the mutant failing to run at all |
| `EQUIVALENT` | **true** | PIT's own equivalence check |
| `SURVIVED` | false | — |
| `NO_COVERAGE` | false | — |
| `NOT_STARTED`, `STARTED` | false | — |

Six statuses score as detected; only one of them is an assertion. The current run has **zero** of
the five non-assertion detected statuses, so today's 80.73% is honest.

Configured budget [verified, `build.gradle.kts`]: `timeoutConstInMillis = 4000`,
`timeoutFactor = 1.5`.

### A correction this spec must not repeat

An earlier draft of the working notes behind this set described a `fasterThreshold` option that
would auto-kill slow mutants and needed guarding. **`fasterThreshold` does not exist** in PIT 1.30.0
or plugin 1.19.0 —
zero occurrences across `pitest-1.30.0.jar`, `pitest-entry-1.30.0.jar`,
`pitest-command-line-1.30.0.jar`, and `pitest-html-report-1.30.0.jar`, and `javap` on the 1.19.0
Gradle extension matches it zero times [verified]. PIT exposes exactly two timeout controls,
`TIMEOUT_CONST` and `TIMEOUT_FACTOR`, both already set. `thresholdPrecision` is likewise absent.

The error came from reading a zero-hit `javap` as "present in PIT but not on the plugin" when the
correct reading is "absent". Do not reintroduce a guard against a non-existent option.

### The CI job today

`.github/workflows/build.yml` runs `./gradlew pitest` in a 45-minute-scoped `mutation` job and
uploads the report with `if: always()` — so a failure still publishes the HTML that explains it.
That is correct and SHALL be preserved. The job fails or passes purely on PIT's own threshold
evaluation; there is no explicit assertion step.

## Invariants

- **The gate SHALL fail the build when mutation coverage drops below the floor.** A gate that only
  reports is not a gate.
- **The floor SHALL be derived from a post-remediation measurement** (Specs 12.01–12.05), not from
  today's number. Pinning first freezes the gaps.
- **Mutation coverage — not test strength — is the floored metric.** It is the only one that counts
  `NO_COVERAGE`.
- **The total mutant count SHALL be asserted independently of the percentage.** A silent change in
  the denominator invalidates the percentage; PIT's rounded integer cannot detect it.
- **Non-assertion "detections" SHALL be visible.** A `TIMED_OUT` or `MEMORY_ERROR` mutant that
  scores as killed SHALL be reported separately, and SHALL count as a survivor for ratcheting
  purposes regardless of what PIT scored it.
- **Raising the timeout budget SHALL NOT be used as a blanket fix** for timeouts. It is correct for
  one identified slow mutant; as a policy it widens the window in which every future mutant can be
  killed on time.
- **The report SHALL be published on failure**, so a red gate is diagnosable from CI alone.
- **The scope and mutator set SHALL NOT change without re-deriving the floor.** Both are recorded
  explicitly so a diff is possible.

## Requirements

1. **WHEN** mutation coverage falls below the configured floor, **THE SYSTEM SHALL** fail the build.
2. **THE SYSTEM SHALL** set `mutationThreshold` to the floor derived after Specs 12.01–12.05 land,
   and SHALL record that derivation — the killed count, the total, and the date — in this spec's
   §Derivation Record below, so the floor and its justification live with the requirement that
   produces it.
3. **THE SYSTEM SHALL** keep `coverageThreshold` at `0` in this spec. Line coverage is a useful
   signal but a separate decision; gating it and gating mutation coverage are not the same choice.
4. **THE SYSTEM SHALL** remove the `testStrengthThreshold.set(0)` line, which sets a default to its
   own value and implies a gate that does not exist.
5. **THE SYSTEM SHALL** assert, after each PIT run, that the total mutant count equals the recorded
   baseline. That baseline SHALL be re-derived and re-recorded after each spec that changes production
   source, and in particular **after Spec 12.01 lands**, because deleting `CanonicalEncoder.equals`
   removes five mutants outright and moves the total off 275. A mismatch SHALL fail with a message
   naming the expected and actual counts and stating that scope, mutator set, or runtime moved.
6. **THE SYSTEM SHALL** report the count of each non-`KILLED` detected status (`TIMED_OUT`,
   `MEMORY_ERROR`, `NON_VIABLE`, `RUN_ERROR`, `EQUIVALENT`) as a first-class step output.
   `TIMED_OUT` and `MEMORY_ERROR` SHALL be treated as a **failure** — not a warning — until the score
   is computed with those statuses excluded; neither is something a legitimate change produces, so
   any non-zero count means a kill was bought with wall time or heap rather than an assertion.

   `NON_VIABLE`, `RUN_ERROR` and `EQUIVALENT` are different in kind and SHALL **not** fail the build
   unconditionally:

   - **`EQUIVALENT` is expected, once Spec 12.02's adjudication lands.** 12.02 §R4 *requires* certain
     mutants to be adjudicated equivalent and recorded with a reason; PIT's own equivalence check can
     independently reach that verdict. Failing CI on any `EQUIVALENT` would therefore red the build
     on a correct, documented outcome, with no way out. The build SHALL fail when the `EQUIVALENT`
     count **exceeds a recorded allow-list**, where each entry names the mutant and carries the
     12.02 §R4 verdict that justifies it. An `EQUIVALENT` mutant absent from the list is a finding.
   - **`NON_VIABLE` / `RUN_ERROR`** mean a mutant could not be compiled or run. That is normally a
     defect in the mutant or the build, so these SHALL fail — but with a separate message from
     `TIMED_OUT`, because the remedy is different (fix the scope, not the timeout).
7. **THE SYSTEM SHALL** compute and display the **assertion-backed** mutation coverage
   (`KILLED` / total) alongside PIT's own figure, so a wall-time kill cannot quietly inflate the
   headline number.
8. **WHEN** a specific mutant times out, **THE SYSTEM SHALL** be fixable by raising
   `timeoutConstInMillis` narrowly, with the reason recorded in `build.gradle.kts`. It SHALL NOT be
   used as a blanket policy.
9. **THE SYSTEM SHALL** preserve `if: always()` on the report upload, and SHALL preserve
   `failWhenNoMutations.set(true)`.
10. **WHEN** PIT or the plugin version changes, **THE SYSTEM SHALL** re-run and diff the total mutant
    count before the percentage is trusted, and SHALL re-verify the two timeout options still exist.
11. **THE SYSTEM SHALL** not configure or guard against `fasterThreshold` or `thresholdPrecision` in
    build or CI configuration. Naming them in documentation is required — the record that they do not
    exist is itself load-bearing. If a future version adds them, that is a measurement change and
    SHALL be treated as one.

## Acceptance Criteria

- [ ] `mutationThreshold` is set to a non-zero floor, with the derivation recorded (R1, R2).
- [ ] `./gradlew pitest` fails when the floor is deliberately raised above the measured value, and
      passes at the chosen floor — **both demonstrated** (R1).
- [ ] `coverageThreshold` is still `0`, with the rationale in the build comment (R3).
- [ ] `testStrengthThreshold.set(0)` is gone and the comment explains why (R4).
- [ ] A CI step asserts the total mutant count and fails on mismatch, with a message naming both
      values (R5). **Demonstrated** by temporarily editing the expected count to a wrong value.
- [ ] A CI step prints each non-`KILLED` detected status count; fails on non-zero `TIMED_OUT`,
      `MEMORY_ERROR`, `NON_VIABLE` and `RUN_ERROR`; and on `EQUIVALENT` fails only when the count
      exceeds the recorded allow-list, naming the unlisted mutant (R6).
      Currently all are zero, so demonstrate by asserting against a synthetic non-zero expectation or
      by temporarily lowering the timeout so a mutant times out — record which method was used.
- [ ] A CI step prints assertion-backed coverage (`KILLED`/total) next to PIT's figure (R7).
- [ ] The PIT report upload still runs on failure (R9).
- [ ] `./gradlew clean test javadoc` passes and CI is green.
- [ ] No `fasterThreshold` or `thresholdPrecision` appears in `build.gradle.kts` or
      `.github/workflows/build.yml` (R11) — grep-able and empty. Scoped to configuration on purpose:
      this spec *names* the two options in order to record that they do not exist, so a repo-wide
      grep would match its own documentation and prove nothing.

## Derivation Record

Filled in when 12.01–12.05 land and PIT is re-run once. Until then it is deliberately empty rather
than pre-filled with a prediction — the whole point of R2 is that the floor comes from a
measurement, and writing a number here now would invite someone to treat it as the floor.

| field | value |
|---|---|
| measured on | _pending Specs 12.01–12.05_ |
| commit | _pending_ |
| total mutants | _pending_ |
| `KILLED` (PIT) | _pending_ |
| assertion-backed kills (R6/R7) | _pending_ |
| `KILLED` / total | _pending_ |
| non-`KILLED` detected statuses | _pending — `TIMED_OUT`/`MEMORY_ERROR`/`NON_VIABLE`/`RUN_ERROR` must be **zero**; `EQUIVALENT` must be **at or below the allow-list** (R6)_ |
| PIT's rounded figure | _pending_ |
| **`mutationThreshold` set to** | _pending_ |

**The last row is not allowed to equal the row above it.** At the current baseline PIT would render
81 and the floor must be at most 80, because 81 is satisfiable only by the single mutant PR #29
added. If the post-remediation figure rounds to *n*, the floor is at most *n−1*. This is not
conservatism for its own sake: a floor set equal to the rounded measurement gates nothing the
rounding has not already granted.

## Design

### R2 — the floor is measured last, on purpose

This is the set's load-bearing ordering constraint. The floor cannot be set before Specs 12.01–12.05
land, because:

- Setting it now pins 53 non-killed mutants as the accepted state, and every subsequent spec has to
  raise it by hand — with no way to distinguish a deliberate raise from a regression.
- The intermediate values are not monotonic. Removing dead code (Spec 12.01 §R3 Branch A) *reduces*
  the total. Setting a floor before that lands means the total-mutant assertion in R5 fires on a
  legitimate change.

So: land 12.01–12.05, run PIT once, record the number, set the floor. The spec is written now because
the *shape* of the gate is known and should not be improvised later.

### R6/R7 — count the kills that are not kills

The accounting rule is a one-liner and it matters:

> **A mutant counts toward the ratchet only if `KILLED`.** Every other detected status
> (`TIMED_OUT`, `MEMORY_ERROR`, `NON_VIABLE`, `RUN_ERROR`, `EQUIVALENT`) inflates PIT's percentage
> without an assertion behind it, so treat it as a survivor for ratcheting even though PIT scores it
> as detected.

PIT offers no configuration for this — there is no "count only `KILLED`" option on the 1.19.0 Gradle
extension — so it has to be computed from the XML report. That is a small script step in CI reading
`build/reports/pitest/mutations.xml`, which is already published as an artifact
(`outputFormats.set(setOf("HTML", "XML"))`).

Today all five counts are zero, so this changes nothing immediately — and with `EQUIVALENT` at zero
the allow-list starts empty, which is the correct initial state: an empty list means every equivalent
mutant must be adjudicated before CI will tolerate it. It is insurance against a slow
mutant appearing later and quietly buying the team a green build.

**Counting them as survivors is not the same as failing on them.** The rule above is about what
counts toward the ratchet, and it applies uniformly to all five statuses. Failing the build is a
separate, per-status decision, and collapsing the two is what would make R6 wrong: an `EQUIVALENT`
mutant is scored as a survivor for ratcheting *and* is a legitimate, documented outcome of Spec
12.02's adjudication. It should reduce the assertion-backed figure and it should not, on its own,
break the build. So the allow-list in R6 exists to keep those two effects separable — a mutant on the
list still counts against the floor; it just does not need an exception to keep CI green.

### R5 — assert the denominator, not just the ratio

A percentage is fragile in a specific way: if the mutant population changes, the percentage may not
move at all. Dropping 50 mutants and 50 kills looks identical to no change. For a teaching-oriented
model checker the mutator set and scope are deliberate and recorded, so the total is knowable — and
knowing it makes drift detectable.

The assertion message matters as much as the assertion. It should name expected, actual, and the
three likely causes (scope moved, mutator set moved, runtime version moved), because the reader is
looking at a red CI job and needs the hypothesis, not just the delta.

## Tests

Not JUnit tests — this spec is build and CI configuration. Verification is by demonstration:

- `pitest_failsWhenFloorRaisedAboveMeasured` — set `mutationThreshold` to 81, confirm the build
  fails, revert
- `pitest_passesAtChosenFloor` — the floor passes
- `ci_assertsTotalMutantCount_failsOnMismatch` — temporarily corrupt the expected count
- `ci_reportsNonKilledDetectedStatuses` — all zero today; demonstrate the failure path
- `ci_reportsAssertionBackedCoverage` — output shows both figures
- `pitest_reportUploadedOnFailure` — trigger a failure, confirm the artifact exists

Record each demonstration in the PR body. Each is a one-line temporary edit plus one run.

## Constraints

- **Dependencies:** Specs 12.01–12.05 — all of them, for the floor. This spec's *implementation* can
  be prepared earlier, but its threshold value cannot be set until they land.
- **Backward compatibility:** none — build configuration only. CI gains an assertion step; the
  `mutation` job's timeout and artifact behaviour are unchanged.
- **Plugin limitations are not fixable here.** All three thresholds are `Property<Integer>` on the
  info.solidsoft plugin's 1.19.0 extension, and that extension exposes no `thresholdPrecision`. This
  is a limitation of the **Gradle integration**, not of PIT itself — PIT supports decimal precision
  when driven directly (Maven, CLI). So the integer blind spot is a property of *this build's
  configuration surface*, and R5/R7 are the mitigation. Document it rather than pretend it away.
- **Do not widen scope or mutators to move the number.** Changing the denominator to improve the
  ratio is the failure mode this spec exists to prevent.

## Commands

```bash
./gradlew pitest
./gradlew pitest --rerun
grep -c '<mutation ' build/reports/pitest/mutations.xml
./gradlew clean test javadoc
```

## Map

- `build.gradle.kts` — `pitest { }` block: thresholds, timeout budget, mutator list, `failWhenNoMutations`
- `.github/workflows/build.yml` — the `mutation` job and its artifact upload
- `docs/specs/active/12-mutation-hardening/README.md` — implementation order and the "last to land" constraint
