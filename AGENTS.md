# AGENTS.md — interleave

Project-specific instructions for agents working in this repository. These override the global
contract on conflict.

## Build and test locally — limit parallelism

This machine has 10 cores. Gradle's own defaults fan out across them, and PIT defaults to
`availableProcessors - 1` minion JVMs at up to 1 GB each — nine concurrent JVMs, which is enough to
make the machine unusable for anything else.

**Always pass both flags on a local run:**

```bash
./gradlew --max-workers=1 -PpitestThreads=1 <task>
```

So, concretely:

```bash
./gradlew --max-workers=1 -PpitestThreads=1 test
./gradlew --max-workers=1 -PpitestThreads=1 clean test javadoc
./gradlew --max-workers=1 -PpitestThreads=1 pitest
./gradlew --max-workers=1 -PpitestThreads=1 pitest -PpitestTargetOverride=<fqcn>
```

A full-scope PIT run takes several minutes at one thread. That is the intended trade — a fast run
that makes the machine unusable gets abandoned rather than waited on, which costs more.

**CI is unaffected and should stay parallel.** `.github/workflows/` invokes `./gradlew` without these
flags, which is correct: the runners are ephemeral and have no other load. Do not add
`--max-workers=1` or a `pitestThreads` default to `gradle.properties` to solve a local problem —
that would serialise CI as a side effect. The flags belong on the command line, or in whatever local
shell profile you prefer.

## Commands

```bash
./gradlew --max-workers=1 -PpitestThreads=1 test       # full suite
./gradlew --max-workers=1 -PpitestThreads=1 pitest     # full mutation scope (state.* + cb.*)
./gradlew javadoc                                       # cheap; no need for the flags
```

Test result XML lands in `build/test-results/test/`, and the mutation report in
`build/reports/pitest/mutations.xml`. Both are easier to query programmatically than to read as HTML.

## Working on the mutation specs

`docs/specs/active/12-mutation-hardening/` is an active spec set. Three rules there are load-bearing
and have been got wrong before:

- **Measure the baseline on `main`, never carry a number forward from an older spec's notes.** Spec
  numbers go stale as sibling specs land — `README.md` carried a `ContextBoundedExplorer` figure
  measured at `c5fdcd0` long after it stopped being true. Run PIT on `main` without the change, then
  again with it, and diff the survivor lists.
- **Per-class kill counts are identical scoped or full-scope — only the totals differ.** Verified on
  `ContextBoundedExplorer` at this commit: `-PpitestTargetOverride` reports 104/105 with
  `NO_COVERAGE` 0, and so does the full default scope. `targetTests` is unchanged by scoping, so
  covering-test selection is unchanged. What a scoped run cannot give you is the *aggregate*: its
  denominator is one class, so its percentage is not comparable to a full-scope percentage. Iterate
  scoped, quote full-scope totals, and do not attribute a per-class difference to scoping — check
  whether the figure is simply older (see the rule above, which is the one that actually bites).
- **Add up inherited mutant lists before writing prose about them.** A count carried from another
  spec is not automatically a subset of anything you have just measured. Concretely: 12.05 left "9
  unassigned" *after* assigning `L41`'s two `NO_COVERAGE` to 12.04, so the nine were L176 ×4, L187,
  L203 ×2, L204 and L214 — and the eight 12.04 closed from that pool were all but `L187`. An
  earlier draft of `DEFERRED.md` E4 listed the closed mutants as "eight of those nine — L41 ×2,
  L176 ×4, L203 ×2, L204, L214", which sums to ten. 12.04 really did close ten in the class; only
  eight came from the unassigned pool, and writing the ten against "eight of nine" contradicts
  itself one clause later. When a total and a subset both appear, state which is which, and re-add
  the list once. The subset reading is the one that is easy to get wrong, because the larger total
  is the number that was measured last and sits freshest in mind.

The second was also nearly written up as a rule — the 12.04 commit claimed scoping changed kill
counts — before measurement showed scoped and full-scope agree per class. All three mistakes were
made and corrected during 12.03 R6, 12.04 and the 12.05 accounting close, and each is written up in
the specs so they are not repeated.