# Spec 11.07 — Propagation into Existing Code Paths

## TL;DR
Adding `TraceOutcome.INCOMPLETE` breaks **four** exhaustive `switch` expressions; adding `Strategy.CONTEXT_BOUNDED` breaks **two** more. All are mechanical, but none are covered by Specs 11.01–11.06, and three of the six sit in files the rest of the set never mentions. This spec enumerates every site, plus the reporting, test, and visualizer fallout, and fixes the implementation ordering so `main` never sits uncompilable.

This is the spec most likely to be skipped, and the one whose omission leaves `main` broken.

## Current State

Verified sites, all exhaustive switch **expressions** (no `default` arm, so an unhandled new enum constant is a compile error):

| File | Line | Switches over | Unaffected by `default`? |
|---|---|---|---|
| `VerificationResult.java` | 34 | `TraceOutcome` | no `default` — breaks |
| `InterleaveRunner.java` | 142 | `TraceOutcome` (`PartialResult.withTrace`) | no `default` — breaks |
| `InterleaveRunner.java` | 164 | `TraceOutcome` (`convertToTestResult`) | no `default` — breaks |
| `DeltaDebugger.java` | 81 | `TraceOutcome` (`isStillFailing`) | no `default` — breaks |
| `Interleave.java` | 58 | `Strategy` | no `default` — breaks |
| `Interleave.java` | 88 | `Strategy` | no `default` — breaks |
| `InterleaveRunner.java` | 56 | `Strategy` | **has** `default -> throw` — compiles, but must gain a real case |

Other affected surfaces, verified:
- `TestResult.java` [`:6-63`]: `failingTraces` / `deadlockedTraces` / `completedTraces` + `limitExceeded`. No incomplete bucket.
- `VerificationResult.java` [`:9-43`]: same three buckets; `toTestResult()` at `:77-94` and `toJson()` at `:96-109` both enumerate them.
- `TraceRecord.toJson()` [`:37+`] writes `"outcome": "<enum>"` — additive enum constant is backward compatible for existing JSON.
- `visualizer.js` [`:100-131`]: `normalize()` recognizes the `TestResult` / `VerificationResult` shapes.

## Invariants
- **Every unhandled switch arm is a compile error, not a silent default.** No site may be "fixed" by adding a `default ->` arm; each must handle `INCOMPLETE` deliberately.
- **The enum constant and all six switch-site updates land in one atomic change.** A partial application does not compile, and a "temporary" `default ->` left in place quietly reintroduces the silent-drop bug this spec exists to prevent.
- **Existing serialized JSON stays readable.** Adding an enum constant is additive for Gson/enum-name serialization; do not renumber or reorder the existing constants.
- **`INCOMPLETE` is never coerced into a failing outcome.** It is a statement about completeness, not about correctness.
- **The visualizer must not render `INCOMPLETE` as `PASS`.** A bounded search that found nothing is the single most misleading thing this feature can report.

## Acceptance Criteria

### 1. `TraceOutcome.INCOMPLETE` propagation sites

| File | Line | Required change |
|---|---|---|
| `VerificationResult.java` | 34 | `case INCOMPLETE -> incomplete.add(trace);` + new `incompleteTraces` field, `incompleteTraces()` accessor, passthrough in `toTestResult()` (`:77-94`), and an `"incompleteTraces"` key in `toJson()` (`:96-109`) |
| `InterleaveRunner.java` | 142 | `PartialResult.withTrace` — add an `incomplete` bucket to `PartialResult` (field, ctor param, `withTrace` copy, accessor) |
| `InterleaveRunner.java` | 164 | `convertToTestResult(DfsResult, …)` — add an `incompleteTraces` bucket |
| `DeltaDebugger.java` | 81 | `case INCOMPLETE -> false;` |

**On the `DeltaDebugger` case:** `isStillFailing` replays a candidate trace and checks whether it reproduces `expectedOutcome`. An `INCOMPLETE` outcome means "the search stopped early" — it is a property of the *search*, not of a *schedule*, and can never be reproduced by replaying a prefix. Returning `false` prevents the minimizer from ever reducing an INCOMPLETE trace, which would otherwise produce a shorter trace that also claims to be INCOMPLETE and is therefore meaningless. Add a comment at the case explaining this, because `false` looks arbitrary without it.

A practical consequence: `DeltaDebugger.minimize` rejects an INCOMPLETE trace outright. It should either throw `IllegalArgumentException` with a clear message or document that INCOMPLETE traces are not minimizable. Do not let it silently return the input trace.

### 2. `TestResult` extension

`TestResult.java`:
- add `private final List<TraceRecord> incompleteTraces;`
- extend the constructor (append the parameter **last**, and update all call sites — `InterleaveRunner.java:171` and `:176`, `VerificationResult.java:92`)
- add `incompleteTraces()` and `hasIncomplete()` accessors
- add `"incompleteTraces"` to `toJson()`, routed through the existing `jsonTraces(List)` helper

**Serialization shape:** `incompleteTraces` is emitted **unconditionally**, including as `[]` when
empty. That matches the existing convention — `toJson()` already always emits `failingTraces`,
`deadlockedTraces`, and `completedTraces` via `jsonTraces`, which returns `"[]"` for an empty list
(`TestResult.java:81-94`) — and it keeps the JSON shape independent of the result's content, which is
what the visualizer's `Array.isArray(raw.incompleteTraces)` guard (Spec §6) expects.

The consequence is that the JSON for a result with no INCOMPLETE traces is **not** byte-identical to the
pre-change output: it gains one `"incompleteTraces": []` member. That is additive and backward compatible
for any consumer that reads named members (which is all of them, including `visualizer.js:116`). The
compatibility test below is therefore specified in terms of *unchanged existing members*, not
byte-equality.

> `TestResult implements Serializable` with no declared `serialVersionUID`, so adding a field changes the implicit serialVersionUID and previously-serialized instances will fail to deserialize. That is acceptable — `TestResult` is only used in-process — but state it here so the implementer does not discover it as a surprise.

### 3. `Strategy.CONTEXT_BOUNDED` propagation sites

| File | Line | Required change |
|---|---|---|
| `Interleave.java` | 58 | `case CONTEXT_BOUNDED ->` (Spec 11.02) |
| `Interleave.java` | 88 | `case CONTEXT_BOUNDED ->` — **must pass the custom `store` through** (Spec 11.02) |
| `InterleaveRunner.java` | 56 | Replace the reliance on `default -> throw` with a real `case CONTEXT_BOUNDED` (Spec 11.06) |

### 4. Reporting fallout

- `StatesExploredTable.java:83` — add `"CONTEXT_BOUNDED"` to `strategyOrder`, and handle the case where CBS explores *more* states than the DFS baseline (see Spec 11.05 §5).
- `SoundnessAttestation.java:75-85` — skip `INCOMPLETE` / `APPROXIMATE_PASS` results in the cross-strategy agreement loop; keep the replay loop at `:91-113` covering CBS (see Spec 11.05 §6).
- `ReportWriter.java:95` — no code change needed; `result.verdict()` is emitted verbatim. Add a test instead.

### 5. Existing test updates

| File | Line | Change |
|---|---|---|
| `BenchmarkHarnessTest.java` | 71 | `assertEquals(6, results.size())` → 8 with a 4th strategy |
| `BenchmarkHarnessTest.java` | 74-79 | per-strategy store-type loop enumerates `{"DFS","STATIC_POR","DPOR"}`; add CONTEXT_BOUNDED |
| `BenchmarkHarnessTest.java` | 25 | **no change** — this is the regression guard proving bitstate DFS/POR/DPOR verdicts stayed `"PASS"` (Spec 11.04). Add a comment recording that intent. |
| `SoundnessAttestation` tests | — | see Spec 11.05 `SoundnessAttestationCBTest` |
| `DeltaDebuggerTest.java` | — | add coverage that an INCOMPLETE trace is rejected rather than minimized |

### 6. Visualizer compatibility

`visualizer.js` `normalize()` [`:100-131`] recognizes the `TestResult` / `VerificationResult` shape at `:116-128`. It derives the verdict at `:121` as:

```js
const incomplete = raw.limitExceeded === true && !raw.hasViolation;
const verdict = raw.hasViolation ? 'VIOLATION' : incomplete ? 'LIMIT EXCEEDED' : 'PASS';
```

A CBS run whose only trace is `INCOMPLETE` has `failingTraces`, `deadlockedTraces`, and `completedTraces` all empty, and `limitExceeded === false`. It therefore falls through to **`'PASS'`** — the viewer renders a bounded search that proved nothing as a clean pass. That is the most misleading single outcome this feature can produce, and it is silent.

Required changes:
- Add `Array.isArray(raw.incompleteTraces)` to the guard at `:116`.
- Prefer an `incompleteTraces` entry as a fourth trace source at `:118`, after the existing three.
- When that is the only trace, derive verdict `INCOMPLETE`, never `PASS`.
- `incomplete`/`LIMIT EXCEEDED` and `INCOMPLETE` are **different** states and must render differently: the former means a resource limit stopped the search, the latter means the preemption bound did. Do not merge them.
- Update the footer text at `visualizer.html:65`, which enumerates the supported shapes.
- Add a fixture under `examples/traces/` with an INCOMPLETE-only payload.

**Testing caveat — KNOWN GAP, not a solved problem:** the visualizer has **no automated JS test suite**
today. There is no test runner, no assertions, and no CI coverage for `normalize()`. The fixture plus the
manual checklist below is the entirety of the verification that will exist for this change. This is stated
plainly rather than dressed up as "add a test", because a spec that implies CI enforcement where none
exists is worse than an honest gap.

Consequences to accept explicitly:
- The `INCOMPLETE` → `PASS` misrender is **only** caught if a human opens the viewer with the new fixture
  during review. Nothing fails the build if the fix is wrong or reverted.
- `normalize()` is security-sensitive (it treats all input as untrusted, per the comment at `:95`), and
  this change adds a new branch to it with no automated guard.
- **Follow-up worth filing:** a minimal JS test harness (node:test or a browser-run assert script) for
  `normalize()`, covering all four producer shapes. That is out of scope here, but it should not stay
  unfiled — the same INCOMPLETE branch will be re-broken by the next viewer change otherwise.

### 7. Compile-green implementation order

The two enum additions have **different** dependency shapes, and that is what determines the order:

- `TraceOutcome.INCOMPLETE` needs only a new bucket at each of its four switch sites. No new class is
  referenced, so it can land on its own.
- `Strategy.CONTEXT_BOUNDED` **cannot**. Its switch arms are `case CONTEXT_BOUNDED -> new
  ContextBoundedExplorer()…`, so the constant and the explorer class must land **together** — and the
  explorer itself needs `INCOMPLETE` to emit its budget trace.

Grouping both enums into one "propagation" step therefore does not compile. The set's original stated
order (01 → 02 → 03/04/06 → 05) does not compile either, for a different reason: 04 and 06 call CBS
verdict helpers that need `INCOMPLETE`. Correct order:

1. **11.01** — `StateStore` + both stores. No enum change; compiles standalone.
2. **11.05 §1–2 + this spec's `TraceOutcome` half** — `TraceOutcome.INCOMPLETE`, `Trace.incomplete`, the
   four `TraceOutcome` switch sites (`VerificationResult:34`, `InterleaveRunner:142`,
   `InterleaveRunner:164`, `DeltaDebugger:81`), and the `TestResult` / `VerificationResult` field
   additions. **One atomic commit** — the four switches do not compile without it.
3. **11.02 + this spec's `Strategy` half** — `ContextBoundedExplorer`, the `CONTEXT_BOUNDED` enum
   constant, and its three switch sites (`Interleave:58`, `Interleave:88`, `InterleaveRunner:56`) in the
   same commit. The explorer needs step 2's `Trace.incomplete`; the enum needs the explorer's class.
4. **11.03 / 11.04 / 11.06** — CLI, harness, library API. Parallelizable after step 3.
5. **11.05 §3–7** — reporting: `ReportWriter`, `StatesExploredTable`, `SoundnessAttestation`.
6. **This spec §6** — `visualizer.js` + `visualizer.html`.

Steps 2 and 3 are the ones most likely to be mis-grouped, and both mis-groupings leave `main`
uncompilable. A `default ->` arm is **not** an escape hatch here: it hides the unhandled case rather than
forcing a decision, which is the precise failure mode this spec exists to prevent.

Step 2 is the one that gets skipped, and the one that leaves `main` broken if half-applied.

## Tests

**File:** `src/test/java/dev/samhb/interleave/VerificationResultCBTest.java`
- `verificationResult_incompleteTrace_retained()` — an INCOMPLETE trace survives `from()`
- `verificationResult_incompleteTrace_appearsInToJson()` — `"incompleteTraces"` key present
- `verificationResult_toTestResult_carriesIncomplete()` — the bucket survives the conversion at `:77-94`
- `verificationResult_noIncomplete_emptyBucket()`

**File:** `src/test/java/dev/samhb/interleave/api/InterleaveRunnerPartialResultTest.java`
- `partialResult_incompleteTrace_bucketedSeparately()` — reaches the partial bucket via the
  `onTraceCreated` visitor path (`:137-149`)
- `partialResult_limitAndIncompleteCoexist()` — a run that trips `maxStates` *and* exhausts its preemption
  bound reports both (Spec 11.05 invariant: the two flags are independent)

**File:** `src/test/java/dev/samhb/interleave/minimize/DeltaDebuggerIncompleteTest.java`
- `minimize_incompleteTrace_rejected()` — throws rather than returning a bogus shortened trace
- `minimize_violationTrace_stillMinimizes()` — regression guard that the new case did not break ddmin

**File:** `src/test/java/dev/samhb/interleave/TestResultJsonTest.java`
- `toJson_incompleteTracesKeyPresent()` — key emitted, `[]` when there are no INCOMPLETE traces
- `toJson_incompleteTracesKeyPopulated()` — non-empty when an INCOMPLETE trace is present
- `roundTrip_existingMembers_unchanged()` — **regression guard for the `toJson()` change.** For a
  violation-only result, assert that every **pre-existing** member (`strategy`, `statesExplored`,
  `wallTimeMs`, `heapDeltaBytes`, `hasViolation`, `limitExceeded`, `failingTraces`,
  `deadlockedTraces`, `completedTraces`) is byte-identical to the previous output. The new
  `incompleteTraces` member is expected to be **added**; do not assert whole-document equality, since
  the serializer emits it unconditionally by design (see §2).
- `roundTrip_shapeIndependentOfContent()` — two results differing only in whether an INCOMPLETE trace is
  present produce the **same key set**, differing only in that member's value. Guards against a future
  "omit when empty" optimization that would make the JSON shape content-dependent.

**Visualizer (manual checklist — no automated JS suite exists):**
- [ ] Load an `incompleteTraces`-only fixture in `docs/visualizer.html`; header shows `INCOMPLETE`
- [ ] Load a `limitExceeded` payload; header still shows `LIMIT EXCEEDED` (the two are not merged)
- [ ] Load each existing fixture in `examples/traces/`; output unchanged
- [ ] `docs/visualizer.html:65` footer text matches the implemented shapes

## Out of Scope
- Changes to `BugCorpus` or the 7 corpus programs
- `Program`, `Configuration`, `SharedState`, or any core model change
- A JavaScript test harness for the visualizer (follow-up; the gap and its consequences are detailed in §6)
- `TraceRecord` field changes — `TraceRecord` already carries an arbitrary `TraceOutcome`, so no change
  is needed for `INCOMPLETE`; only the *containers* change
- `CorpusGenerator` / `CorpusEntry` — they consume `DfsResult` from an exact `DfsExplorer` oracle
  (`CorpusEntry.java:108-112`, `CorpusGenerator.java:45-47`) and never see CBS results, so their
  `TraceOutcome.VIOLATION || TraceOutcome.DEADLOCK` checks are unaffected. Add no `INCOMPLETE` branch there:
  an INCOMPLETE oracle verdict would be meaningless, since the corpus oracle is always exhaustive.

## Commands
```bash
./gradlew test
./gradlew test --tests "*VerificationResult*"
./gradlew test --tests "*DeltaDebugger*"
./gradlew test --tests "*TestResult*"
```

## Map
- `src/main/java/dev/samhb/interleave/VerificationResult.java` — `TraceOutcome` switch, new `incompleteTraces` bucket, `toTestResult()`, `toJson()`
- `src/main/java/dev/samhb/interleave/InterleaveRunner.java` — two `TraceOutcome` switches (`:142`, `:164`), `Strategy` switch (`:56`)
- `src/main/java/dev/samhb/interleave/minimize/DeltaDebugger.java` — `TraceOutcome` switch (`:81`)
- `src/main/java/dev/samhb/interleave/TestResult.java` — new `incompleteTraces` field + accessors + `toJson()`
- `src/main/java/dev/samhb/interleave/report/StatesExploredTable.java` — `strategyOrder` (`:83`)
- `src/main/java/dev/samhb/interleave/report/SoundnessAttestation.java` — verdict agreement (`:75-85`), replay loop (`:91-113`)
- `src/test/java/dev/samhb/interleave/report/BenchmarkHarnessTest.java` — result-count assertions (`:71`, `:74-79`)
- `docs/visualizer.js` — `normalize()` (`:100-131`), verdict derivation (`:121`)
- `docs/visualizer.html` — footer shape list (`:65`)
- `examples/traces/` — INCOMPLETE fixture
