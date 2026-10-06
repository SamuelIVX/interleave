# 07 — Conventions and process debt

**Status:** implemented — **E5 deliberately not closed** (see below)
**Closes:** D2, E1, E2 from `12-mutation-hardening/DEFERRED.md`
**Mutation scope:** none — `build.gradle.kts`, `AGENTS.md` and one spec's prose.

## D2 — how to derive a numeric expectation

12.03 had to establish, from scratch, that a test's expected FPR doubles must be derived independently
of the implementation. It recorded the reasoning in its own spec, which meant the next spec needing the
same derivation would have had to find it there.

The rule now lives in `AGENTS.md` under **Test conventions — numeric expectations**, because that is
where an agent or a new contributor looks, and a convention buried in a spec is not a convention. Four
points, taken from the register's own wording: derive outside the language, cross-check by two
independent routes, assert against literals, and never re-derive with the same expression the
implementation uses.

The last is the one that matters and the one most easily violated by accident.
`assertEquals(expected, 1 - Math.exp(-x))` placed beside an implementation containing
`1 - Math.exp(-x)` reproduces the implementation line for line, passes, and would keep passing if both
were wrong in the same way. It looks like an assertion and verifies nothing.

## E1 and E2 — one mechanism, because they are one problem

E1 and E2 looked like separate items and are the same failure: **a number written in two places and
compared in neither.**

E1 was already half-closed. The ratchet prints a per-class census derived from `mutations.xml` on every
run — its comment says so explicitly — so the build emits the truth. What kept recurring is that the
*spec* tables were hand-maintained next to it, so they drifted. Twice before 12.03 caught it, and then
again immediately when 13.03 changed `ContextBoundedExplorer`'s denominator: the table still read
`104/105` and the prose still said 270 mutants / 256 killed, one spec after the measurement moved to
`103/103` and 268 / 255.

So the expectation moved next to the thing that computes it. `EXPECTED_PER_CLASS` in `build.gradle.kts`
holds the four figures, and every run prints any disagreement.

On a scoped run, classes omitted by `-PpitestTargetOverride` are intentionally absent and do not
produce missing-class notices. Mismatches for classes that did run still produce notices. Full runs
retain both mismatch and missing-class notices.

**Deliberately not a gate.** A legitimate spec change should not turn CI red because a documentation
table moved; this notice says a human-maintained figure has drifted, which is a prompt to update two
files in one commit. It is a notice, not a claim about correctness — D4's rule.

### Proven, not assumed

A check that has never fired is indistinguishable from a check that does not work, so both directions
were run against `./gradlew mutationRatchet` with one recorded figure deliberately set to its
pre-13.03 value:

```
  NOTICE — recorded per-class figures have drifted (not a gate; see D4):
    dev.samhb.interleave.cb.ContextBoundedExplorer — recorded 104/105, measured 103/103
    Update the spec table AND EXPECTED_PER_CLASS in the same commit.
```

Restoring the correct value returns the run to silent. So the notice is not permanently on, and it is
not permanently off either.

### The stale numbers were fixed, not just guarded

Guarding a wrong number is not the same as correcting it, so spec 12's table and its prose were brought
back in line: `ContextBoundedExplorer` to `103/103` / 100.0%, dated 2026-10-05, with `moved by` now
reading **13.03 (denominator shrank)**. That phrasing matters — the existing table already distinguishes
a class that moved by *shrinking its denominator* from one that moved by *killing mutants*, and
conflating the two is the confusion E1 was recorded for. `13.03` deleted two mutants, one killed and
one survived — 256→255 and 14→13 both moved — so the row is dead code removed, not coverage earned.

The table now points to the build's comparison between the XML census and `EXPECTED_PER_CLASS`.
The task does not parse Markdown; reviewers must compare the table with the emitted census and keep
it aligned with those constants. Only drift in the recorded build constants is detected automatically.

## E5 — not closed, and cannot be from here

E5 is recorded as open, on purpose. Its closure condition is empirical and this spec cannot satisfy it:

> E5 closes when the full-scope parallel gate has been green across enough runs that a single timeout is
> more plausibly a regression than a flake — or, more honestly, when it is judged not worth further
> tracking. **Not closed on one run.**

Every local measurement in this project is single-threaded, because `AGENTS.md` mandates
`--max-workers=1 -PpitestThreads=1`. CI deliberately runs in parallel. So the load profile that would
produce a spurious `TIMED_OUT` is precisely the profile no local run can speak to — including the current
255/268 measurement against the frozen 94% floor. The earlier 256/270 measurement is historical;
13.03 removed one killed mutant and one survivor.

Closing E5 needs accumulated evidence from repeated parallel CI runs, which is not a thing a spec can
assert about itself. Marking it closed here would be exactly the kind of unearned claim this register
exists to prevent.

### What was recorded instead

The adjudication procedure E5 specifies, so that whoever hits a real timeout does not have to invent
one: re-run that single mutant scoped and single-threaded, and decide whether it is genuinely slow or
load-induced before changing anything.

And the prohibition, which is the part most likely to be ignored under time pressure — **do not
pre-emptively loosen the timeout budget to avoid a hypothetical red build.** That trades a known,
documented failure mode for an invisible one: the gate keeps passing, and nobody learns which mutants
were being killed by wall time rather than by an assertion. E5's premise is that a mutant bought with
wall time is not a kill; widening the budget to stop noticing is the same error wearing a different hat.

## D1 — closed by 13.01, not by this item

D1 is **closed**, and it was closed by **13.01**, which added the `Configuration.forTest` overloads —
exactly D1's stated closure criterion, *"a test-visible factory taking explicit counters"*. All three
fixture helpers now use it; no test constructs a `Configuration` through reflection any more. The
remaining `setAccessible` calls in `state` set non-final instance fields on state objects, which is an
unrelated need with no factory to delegate to.

Recorded here because this spec previously said D1 was "untouched and still open". That was wrong: it
was out of *this item's* scope, which is not the same as undone. The distinction matters because a
register entry claiming D1 is open would have had it re-picked-up for work already finished.

## Verification

**Suite: 480 tests, 0 failures** — unchanged, as expected for a build-script and documentation item.
`mutationRatchet` verified in both drift states as shown above.

The review fix was validated against the real `mutationRatchet` task with isolated XML reports:

| Population | Expected diagnostic | Result |
|---|---|---|
| Scoped, `CanonicalEncoder` 6/7 | no drift notice for omitted classes | PASS |
| Scoped, `CanonicalEncoder` 5/7 | mismatch notice; no omitted-class notice | PASS |
| Full, all recorded counts | no drift notice | PASS |
| Full, expected class replaced at identical totals | missing-class notice; gate still passes | PASS |

Before the fix, both scoped cases failed the diagnostic assertions because they reported intentional
omissions as drift. These are build-configuration checks using temporary reports, not application
unit tests; the actual scoped and full PIT runs are verified separately.

## Follow-up evidence in 13.10

Six full-scope parallel CI runs across PRs #42–44 passed with three PIT workers and zero timeout or
execution-error statuses. [13.10](10-parallel-ci-and-documentation.md) records each run and the
agreed closure criterion: those runs plus passing parallel CI on the final change. E5 remains open
until that final check; the strict gate and adjudication procedure above remain in force.
