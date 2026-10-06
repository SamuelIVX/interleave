# 10 — Parallel CI evidence and Java documentation

**Status:** merged in PR #45; E5 closed by verified final parallel CI. Remote CodeRabbit review skipped.
**Addresses:** E5 from `12-mutation-hardening/DEFERRED.md` and PR #44’s documentation warning.

## Purpose and closure criterion

E5 tracks whether PIT’s strict timeout/error gate is usable under parallel CI load. It does not
require a new search algorithm or a speculative timeout workaround. Six completed full-scope
runs across PRs #42–44 now establish repeated operational success. Closure additionally requires
the final change’s normal parallel Mutation job and assertion-backed ratchet to pass.

This is a practical decision to stop tracking the deferred item, not statistical proof that future
load-related timeouts are impossible. The hard gate and investigation procedure remain in force.
Cancelled jobs and local single-worker runs are excluded from the closure evidence.

## Verified parallel evidence

The Mutation job logs, rather than the aggregate workflow badge, were inspected. Each report used
`numberOfThreads=3`, the full default `state.* + cb.*` target classes, and the same covering-test
scope. Each reported **268 mutants / 255 KILLED / 13 SURVIVED**, passed the 94% assertion-backed
ratchet, and reported zero `TIMED_OUT`, `MEMORY_ERROR`, `NON_VIABLE`, and `RUN_ERROR`.
Durations below cover the whole Mutation job, including setup and report upload.

| Run | Commit | Event | Mutation duration | Workers | Ratchet |
|---|---|---|---|---|---|
| [37518730424](https://github.com/SamuelIVX/interleave/actions/runs/37518730424) | `3d11ed7a` | PR #42 | 3m 49s | 3 | PASS |
| [37519158906](https://github.com/SamuelIVX/interleave/actions/runs/37519158906) | `7cdc1ef3` | push main | 6m 36s | 3 | PASS |
| [37525353827](https://github.com/SamuelIVX/interleave/actions/runs/37525353827) | `73ab7e0e` | PR #43 | 5m 59s | 3 | PASS |
| [37526570526](https://github.com/SamuelIVX/interleave/actions/runs/37526570526) | `9276cd4d` | push main | 5m 28s | 3 | PASS |
| [37539518953](https://github.com/SamuelIVX/interleave/actions/runs/37539518953) | `fd78bcf9` | PR #44 | 5m 57s | 3 | PASS |
| [37540326697](https://github.com/SamuelIVX/interleave/actions/runs/37540326697) | `b3f09bbe` | push main | 3m 47s | 3 | PASS |

These runs cover three reviewed changes but occurred on the same day. They establish repeated
success for the current runner configuration; they do not establish long-term failure probabilities.
Logs can be inspected with `gh run view <run> --repo SamuelIVX/interleave --json jobs`, followed by
`gh run view <run> --repo SamuelIVX/interleave --log --job <mutation-job-id>`. Check the report’s
target classes, worker count, complete census, and ratchet result together.

## Preserve the strict gate

Parallel CI and the existing `timeoutConstInMillis=4000`, `timeoutFactor=1.5`, mutation scope,
94% floor, per-class expectations, and status failures remain unchanged. No retries or flaky-mutant
allowances are introduced. Local Gradle commands retain both mandated single-worker flags.

If a future run reports a timeout or memory error, identify the mutant’s class, method, instruction
index, and mutator from the report. Run that containing class locally with one PIT worker:

```bash
./gradlew --max-workers=1 -PpitestThreads=1 pitest -PpitestTargetOverride=<fqcn>
```

Inspect the specific mutant within that report; this class-scoped command still runs every mutant
in the class. Compare the covering test’s execution and assertion behavior with CI before deciding
whether the issue is genuine slowness or load sensitivity. Any timeout-budget experiment is a
separate diagnostic invocation, not a blanket relaxation of the default CI gate. Neither timeout
nor memory exhaustion counts as an assertion-earned kill.

## Documentation contract and scope

CodeRabbit reported **33.62%** documentation coverage against its **80%** threshold on PR #44,
scoped to 116 touched functions across 20 supported files. That threshold is a baseline, not a
completion target. Every newly introduced function receives meaningful Javadoc. Existing
production declarations and nontrivial helpers are documented as thoroughly as possible.

This change audits all **111 production Java files** and these four PR #44 test files:

- `StepFootprintTest`
- `DslPropertyObservationTest`
- `PropertyAwarePorTest`
- `InvariantObservationTest`

The audit includes types, explicit constructors and methods, fields, nested records, and test
fixtures. Record components are documented with `@param` on their record contract. Overrides may
inherit a valid interface/superclass contract; Javadoc appearing after an annotation is normalized
before it so the Java documentation parser recognizes it. Existing contracts take precedence over
mechanically inferred prose. In particular, `TraceReplayer` trusts recorded step outcomes and does
not itself validate replay consistency.

Implicit default constructors that trigger missing-documentation warnings are made explicit with
the same effective visibility and an empty body, allowing their existing construction contract to
be documented. No exploration behavior or public signature is changed.

The normal Javadoc task now enables all doclint groups and retains warnings as errors and the
10,000-warning diagnostic cap. A separate local run includes private members to validate their
parameter/return contracts without changing the public API documentation’s visibility. The
inventory in [10-documentation-inventory.md](10-documentation-inventory.md) records scope and
counts; it is not a substitute for CodeRabbit’s own eventual remote measurement.

## Skills and verification

Use `implement` for this spec, `code-review` for local review, and `receiving-code-review` to
validate findings. Use `tdd` only for a necessary behavior fix at an agreed public seam.
Documentation follows the context store’s `ENGINEERING-PRINCIPLES.md` §10. No behavior tests are
added for comments and the mechanically equivalent explicit constructors; existing construction
and exploration tests provide regression coverage.

Fresh baseline on `main` at `b3f09bbe`: **526 tests**, build and Javadoc pass; full PIT **255/268**,
13 survivors, no uncovered or non-assertion detections. Final local verification and review results
are recorded below after execution. Compare mutation identities by class/method/descriptor,
instruction index, mutator, and status, excluding shifted source lines. Update line-dependent
survivor annotations only to the measured location of the same mutant.

**E5 is closed.** The final PR Mutation job independently satisfies the remaining parallel-CI
condition, as recorded below. Remote docstring coverage is unmeasured because CodeRabbit skipped
the review; its successful status does not establish either a review or an 80% coverage result.

## Final local verification

- `clean build javadoc pitest`: **PASS**, 526 tests, zero failures/errors, Javadoc warnings zero.
- Full PIT: **255/268**, 13 survivors; all other statuses zero. Every mutation identity and status
  matches the fresh baseline after excluding source-line movement. No drift or stale-survivor notice.
- Private-member Javadoc audit: **PASS**, zero warnings, including private helper and record tags.
- Compiler AST comparison: every existing method is unchanged. The only additional executable
  declarations are 20 empty constructors replacing implicit defaults with the same visibility.
- After contract-review corrections, `build javadoc`: **PASS**, including the full test suite.

The first local CodeRabbit pass completed with 17 findings (16 minor, one major). Incorrect
return descriptions, modeled-effect claims, and the runner isolation guarantee were corrected
against the implementation. The three requests to make registry views unmodifiable were resolved
by correcting their new documentation to state that the views are live and mutable; changing
existing API behavior is outside this documentation change. The second pass completed with one minor finding: the exact-store description incorrectly
implied exhaustive results. It now distinguishes exact deduplication from bounded-search
completeness. No actionable local findings remain after verification; no third review was run.

## Final remote evidence and closure

[PR #45](https://github.com/SamuelIVX/interleave/pull/45) merged at `cc6d65b`. Its
[Mutation job](https://github.com/SamuelIVX/interleave/actions/runs/37544489174/job/112545079086)
on head `a7abde9` completed successfully in **5m 54s**, using **three PIT workers** and the full
default mutation scope. The report and ratchet both show **255/268**, 13 survivors, and zero timeout
or execution-error statuses. Build, CodeQL, and security checks also passed. This seventh verified
parallel job satisfies E5’s agreed closure criterion. The floor, budgets, and strict gate remain.

CodeRabbit’s remote review was **skipped**: 122 changed files exceeded its 100-file capacity, and
sufficient usage capacity was unavailable. No remote docstring percentage was produced. The two
completed local reviews, attached-doc inventory, and clean public/private doclint are the verified
documentation evidence. No credits were purchased or review limits bypassed.
