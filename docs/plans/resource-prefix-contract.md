<!-- Approved B8 choice A and the two complete implementation stages. -->
# B8 — Resource-prefix contract

## TL;DR

Sam selected **A as the complete B8 scope**: capture, explicitly replay and present the actual
resource-interrupted source prefix. Deliver Java results, JSON and the existing viewer. No new CLI
mode, shortening operation, dependency or execution policy. B/C shortening objectives remain unselected.
B7 completed in PR #128 at `2f578cf`.

## Owned concepts

- A resource interruption is the original run's observed MAX_STATES/MAX_TIME event.
- A captured prefix is its actual committed execution and source identity, frozen before unwinding.
- A replay observation authenticates that execution under the supplied property. It neither reproduces
  external resource accounting nor establishes exhaustive PASS.

Limits run before property/completion/deadlock checks. The final captured position can therefore be
terminal; explicit replay must expose that observation separately from the interrupted original run.
A generic legal-prefix shortening rule would often return an empty answer, so it is not part of A.

## Two sequential PRs

| Stage | Complete deliverable | Acceptance boundary |
|---|---|---|
| **J1 / [Spec 87](../specs/active/87-resource-prefix-evidence.md)** | One interruption notification at the existing visitor seam; immutable runner-owned ResourcePrefix; Optional result accessor; explicit authenticated replay with immutable observations. | Actual paths across all strategies, no model/property execution during capture, terminal-at-stop observations, original metadata/buckets retained, corruption/immutability/reuse coverage. Existing JSON remains unchanged. |
| **J2 / [Spec 88](../specs/active/88-resource-prefix-presentation.md)** | Shared Gson serialization of raw/replayed prefixes; optional TestResult JSON member; viewer normalization/display; fresh Java-to-browser checks. | No implicit replay during serialization; resource provenance separate from modeled cutoffs; reported replay observations separate from INCOMPLETE run completeness; existing rendering caps and failure precedence retained. |

J1 merged in PR #129 at `9abbc4f`; J2 merged in PR #130 at `1db7870`. B8 is complete. Specs and measured evidence ship
with their implementations; existing source trace/replay specifications retain ownership.

B9/B10 require separate concrete eligibility/approximation contracts. Standalone roadmap #11/#12
and parked work remain outside this sequence. There are no automatic follow-on B/C stages.
