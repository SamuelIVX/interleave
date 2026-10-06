# 04 — The L187 equivalence proof

**Status:** implemented — **reframed during the set; see below**
**Closes:** E4 (`12-mutation-hardening/DEFERRED.md`)
**Mutation scope:** none. `ContextBoundedExplorer` is in scope, but 13.03 already removed the mutants
this spec was written about. Total and score are unchanged by anything here.

## This spec is not what it was planned to be

The plan (D5) was: *record the equivalence proof, keep the redundant call. Total stays 270, score stays
94.81%.* The intent was to add a `KNOWN_EQUIVALENT_SURVIVORS` entry so the lone `ContextBoundedExplorer`
survivor would read as **explained** rather than unexplained.

**That is unreachable.** 13.03's assigned consolidation replaced line 187's
`enabled.isEmpty() && !config.allTerminated()` with `config.isDeadlockCandidate()`, which deleted the
`config.allTerminated()` call the surviving mutant mutated. The mutant is gone — CBE went 104/105 to
103/103 — so there is nothing left to register.

The proof is still worth writing down, and for a better reason than the one originally planned: it now
justifies the *deletion*, in the diff, for whoever reads it later. That is more useful than a note
explaining a survivor to whoever reads a PIT report.

## The proof

> `ContextBoundedExplorer.dfs` returns at line 126 when `config.allTerminated()` is true.
>
> Therefore if control reaches line 187, `allTerminated()` was already false, so `!config.allTerminated()`
> is unconditionally true.
>
> The old guard `enabled.isEmpty() && !config.allTerminated()` is therefore exactly `enabled.isEmpty()`,
> which is what `Configuration.isDeadlockCandidate()` already computes.

This also explains why the **same** removal at line 126 *is* killed: line 126 is reached with
`allTerminated()` possibly true, so there the call is load-bearing.

## Where it lives now

`Configuration.isDeadlockCandidate()` is the single derivation, and `ContextBoundedExplorer:187` reads
it. The register's E4 entry recorded the cause as `NOT ESTABLISHED`; it was establishable, and the
argument is three lines long.

The 13 README's TL;DR carries the proof inline rather than linking to this file, so a reader does not
have to open anything to learn why the guard was redundant.

## Verification

No test was added here, and none should be. Two independent reasons:

**The predicate is already covered at the source.** 13.02's `ConfigurationDerivationTest` pins
`isDeadlockCandidate()` at the factory — including the case that distinguishes it from "all
terminated", and the case where an empty enabled set with live threads **is** a deadlock candidate.

**The call site was already covered in both polarities.** Changing *which* expression a guard uses is
only safe if some test would notice either way, and these do:

| Test | Asserts |
|---|---|
| `ContextBoundedExplorerTest.assertSurfacesDeadlock` | a DEADLOCK trace **is** emitted, at several store widths, with a message that names the failure mode (hash collisions pruning real bugs) |
| `ContextBoundedTraceEmissionTest.incompleteTrace_emittedWhenOnlyCompletedTracesExist` | peterson is correct, so **no** DEADLOCK may be present |
| `CbsDifferentialTest` | bounded verdict must match exhaustive search, DEADLOCK included |

The first would fail if the guard stopped firing; the second and third would fail if it fired on a
completed configuration. Together they pin both outcomes, which is what a predicate swap needs.

**Suite: 468 tests, 0 failures.** Unchanged by this spec — no test was warranted.

**Mutation impact: none.** `EXPECTED_TOTAL_MUTANTS` stays 268, score stays 255/268 = 95.15%. No entry
was added to `KNOWN_EQUIVALENT_SURVIVORS`, because there is no longer a `ContextBoundedExplorer`
survivor to name.

### A note on this file having claimed otherwise

An earlier draft of this spec said 13.03 "gained a dedicated test for the CB deadlock path." That was
false — it had been planned, not written, and the sentence was written as though the work were done.
The coverage above already existed, which is the better answer anyway. Recorded rather than quietly
corrected, because a spec that reports work as done when it was only planned is the exact failure mode
this set exists to remove.

