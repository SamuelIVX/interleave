<!-- Active94C implementation ledger; no completed-public-release claim before all consumers pass. -->
# 94C — Public bytecode release evidence

## Status and baseline

Implementation in progress under [Spec94C](../specs/active/94C-bytecode-release.md) and the
[shared contract](../plans/bytecode-contract.md). PR140/94B merged at
`6eb939ea19c3938db8052fc1b32d8f157fc913c7`, verified against remote main before creating the isolated
`feat/bytecode-public-release` checkout. The original dirty checkout and prior worktrees are preserved.
Fresh merged-main build/tests/strict Javadoc passes2066 tests with zero failures/errors/skips.
Configured default mutation baseline:455 KILLED +13 SURVIVED /468. Affected baseline across bytecode,
runner/result/trace/replay, CLI and benchmark/report/attestation:3163 KILLED +253 SURVIVED +478
NO_COVERAGE /3894, zero resource/run-error statuses. Both completed before production edits; the
larger affected run took95 minutes. Raw logs/XML are retained in the persistent evidence sibling.
Historical94B counts are not94C baselines.

## Implementation order and approved seams

1. Publish annotation/immutable program/limits/loading interfaces and the runner's dedicated bytecode
route, producing owned BYTECODE identity, real transition metadata and immutable snapshots.
2. Authenticate strict replay and JSON, including initial failures, modeled cutoffs and honest external
prefix replay. Bytecode and source evidence remain separate; native streams have an upgrade boundary.
3. Route CLI, benchmark/report/attestation and viewer consumers, with externally compiled built-jar
lost-update/deadlock examples and complete legacy/browser compatibility.

Tests use the contract's loader/runner, replay, CLI/report and browser seams. Internal metadata/state
stay owned by the existing bytecode package. One narrow integration facade is needed for cross-package
loader/runner/replayer calls; it exposes no target state, generic callback or configurable backend.
Existing source/JSON interfaces and default selections retain their behavior. Runtime limits/mode and
artifact/model/declaration identity are authenticated separately. Only exact DFS is admitted in94C;
95+ and weak memory remain outside this milestone.

## Gates still required

Record fresh mutation fingerprints, baseline/final build/Javadoc, built-jar public consumers,
strict replay adversaries, no-debug classes, pure-observer/fault/cutoff/global-stop classifications,
reports and fresh browser artifacts. Incorporate the actual [94B measurements and preservation
argument](94B-bytecode-concurrency.md), not invented new measurements. Run ordinary/security review,
fresh-reader checks and actual CodeRabbit; record real review status through agent-setup. Publish one
94C PR, report its link and stop before95 or CI watchers.

## First implementation slice

Public annotation/program/limits/loading and runner access now pass the real lost-update tracer test.
Source diagnostics remain separate from BYTECODE payloads; shared observation order is reused by search
and upcoming strict replay. Public runs capture immutable typed state views during execution, rather
than reexecuting cancelled prefixes after search. Internal94B measurement mode keeps capture disabled.
Connected checks/monitor/exact-state/progress regressions pass. This is an internal checkpoint; strict
replay/JSON and all public consumers/review gates remain unfinished and no94C PR is published.
