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

`docs/specs/active/12-mutation-hardening/` is an active spec set. Two rules there are load-bearing and
have been got wrong before:

- **Measure the baseline on `main`, never carry a number forward from an older spec's notes.** Spec
  numbers go stale as sibling specs land — `README.md` carried a `ContextBoundedExplorer` figure
  measured at `c5fdcd0` long after it stopped being true. Run PIT on `main` without the change, then
  again with it, and diff the survivor lists.
- **A target-scoped PIT run is not comparable to a full-scope one.** Scoping changes which covering
  tests PIT selects, so `-PpitestTargetOverride` reports a different kill count for the same class.
  Use it to iterate on one class; quote full-scope numbers in the specs.

Both mistakes were made and corrected in the 12.03 R6 and 12.04 PRs respectively, and both are written
up in the specs so they are not repeated.