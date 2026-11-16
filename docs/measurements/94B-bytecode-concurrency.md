<!-- Internal94B work in progress; no completed batching or public-release claim. -->
# 94B — Concurrent bytecode execution evidence

## Status and baseline

Work in progress under [Spec94B](../specs/active/94B-bytecode-concurrency.md) and the
[shared contract](../plans/bytecode-contract.md). Starting merged main is
`3c2c799d5d01054706e85bd8cdf89ab2ec2addba` (94A/PR139). No public bytecode selectors are exposed.

The earlier temporary draft and logs disappeared between sessions. Its recorded edits were restored
from the current session's tool history into a persistent worktree. Old printed measurements are
historical observations; fresh baseline reports are being regenerated before further production work.
The original dirty checkout and stale worktree records were preserved.

Persistent implementation: `Documents/repos/worktrees/interleave-bytecode-concurrency`, branch
`feat/bytecode-concurrency-resume`. Untouched baseline: `Documents/repos/worktrees/interleave-94b-baseline`.
Local evidence directory: `Documents/repos/worktrees/interleave-94b-evidence` under Sam's home.
Fresh baseline build/strict Javadoc passes; mutation baselines are being collected there.

## Restored behavior and current checks

A pure OpcodeExecutor probe distinguishes runnable, blocked and ended actors while retaining
fuel-before-blocking priority. The reference scheduler selects actual bytecode continuations.
Iterative exact DFS stores full canonical payloads after hash filtering, independently of source
Configuration/forward-PC validation. Checker state/key budgets produce external interruptions,
never modeled cutoffs. Canonical payload accounting is not a JVM retained-memory measurement.

The restored scheduling/monitor suite passes16 tests, including real increment outcomes x=1/x=2,
concrete two-lock ownership/wait relationships, normal completion and static synchronized helpers.
Existing instruction/fuel/cleanup regressions also pass. This does not close94B: observer execution,
batching/sharedness instrumentation, independent enumeration, preservation review and real measurement
repetitions remain pending. No bytecode batching/sharedness/JPF numbers are claimed.

## Required completion evidence

Record fresh baseline/final build, strict/private doclint, affected/default mutation identities and
honest residuals. Add instruction-reference/batching outcome comparisons and a written preservation
argument covering pure local segments, check values/errors, fuel, cleanup and competing terminal events.
Measure both accepted fixtures with actual interpreter executions, raw repetitions, artifact/toolchain
identity, encoding work, elapsed time, retained heap and sharedness/history costs. Use the setup-only
profile's real zero/not-applicable history-only values; do not manufacture actor-allocation fixtures.
Run actual CodeRabbit and ordinary/security review, recording completed/partial/quota evidence through
agent-setup before publishing the94B-only PR. Report its link and stop before94C/CI watching.
