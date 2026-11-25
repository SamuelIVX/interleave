<!-- Spec102 authoring evidence; implementation and calibration are separate future gates. -->
# CI-first mutation specification review

## Status

Spec 102 is a specification-only change based on remote main
`cccd74197530689df6cd58a2883d90b1b90af983`. Original checkout and Spec 97 worktree changes are
preserved. No verification tools, CI jobs, production code or mutation configuration are implemented
in this PR. Implementation, calibration, performance savings and remote CI remain unverified.

## Decisions and review scope

Sam selected CI-first/light-local execution, a separate infrastructure PR before resuming Spec 97,
automatic affected checks on relevant PRs and fresh pinned-base CI analysis instead of prospective
archive-before-edit timing. The spec retains historical evidence and Spec 97's original deviation.

The template at `~/context/project-ideas/SPEC-TEMPLATE.md` supplies objective, scope/non-goals,
invariants, numbered requirements, failure behavior, acceptance, baseline/rollback, design, tagged
current state, tests and constraints. `CONTEXT.md` retains domain-document ownership.

Research compared remote offload, exact class partitioning, cheap behavioral tests, incremental
history, fixture caching, CI threads and commercial acceleration. Only the first two plus local
workflow/provenance changes belong to this infrastructure phase. Primary sources are linked in
Spec 102; no benchmark or speedup was invented.

## Authoring gates

- Fresh-reader review of the chat draft identified incomplete execution/provenance details, manual
  dispatch ambiguity and operational rollback gaps. The repository draft adds completed PIT totals,
  explicit empty-shard rules, immutable merge-candidate identity, same-repository manual dispatch,
  protocol validation and `full`/`sharded` modes with calibration before enabling sharding.
- Fresh-reader review of the completed repository spec answered all six questions about local/CI
  ownership, immutable revisions, completion/partition checks, calibration/rollback, comparison limits
  and delivery boundaries. It found a medium status-policy ownership ambiguity and a low missing
  changed-method disposition location. Both were corrected: the strict three-status rule belongs
  only to the new affected gate; Spec 12.06's default equivalent allow-list remains intact. Human
  changed-method dispositions must be recorded in the implementation ledger and linked PR before
  acceptance. No unsupported speedup or blocking scope contradiction was found. Reader follow-up
  confirms both corrections resolve the findings without a new contradiction.
- Actual CodeRabbit CLI 0.8.1 completed the nine-file specification review with `review_completed`
  and two major findings. Both were independently validated and corrected. The first sandbox attempt
  failed to open its local authentication callback; the host run completed normally. Raw output:
  `/Users/samuelhernandez/Documents/repos/worktrees/interleave-102-evidence/coderabbit-spec102-host.ndjson`.
  The bounded follow-up CLI also completed nine files with `review_completed` and two major findings;
  raw output is `interleave-102-evidence/coderabbit-spec102-followup.ndjson`. Both were independently
  checked against the existing comparator source and addressed as explicit implementation obligations
  and regression scenarios in the specification. No comparator code is changed in this PR. Ordinary
  review is not represented as CLI evidence; neither CLI run is described as zero-finding.
- Final template/link/whitespace and selected-scope credential-pattern validation pass: nine Markdown
  files only, all 12 required template sections, nine numbered requirements, 106 relative links,
  zero missing targets and zero selected-file credential-pattern matches. No YAML/configuration or
  production files change. Raw local summary is `interleave-102-evidence/document-validation.json`.
- Gradle/build/PIT/browser execution: skipped for this documentation-only bundle. Real calibration
  and appropriate implementation checks belong to the infrastructure implementation PR.

Both actual reviews used `coderabbit review --agent --uncommitted --include-untracked` after completing
the specification. The bounded loop stops at two CLI reviews; the related documentation corrections
are independently verified by the final fresh reader. A planned command is not evidence of a
completed check. Publish a specification-only draft PR with the
coauthor trailer, report its link and stop; do not watch CI or begin tooling implementation.

## CodeRabbit dispositions

| Finding | Severity | Disposition |
|---|---|---|
| Retain all fresh baseline checks after adoption | major | Valid. R4, AC2, design and Specs 97–101 now explicitly retain full build/strict Javadoc/tests and separate default/affected PIT at both revisions. Reuse exact candidate job evidence once; candidate-only or affected-only evidence cannot replace baseline checks. Added baseline/reuse regression scenarios |
| Mixed test-revision replacement reports | major | Valid. Acceptance aggregates now require identical full production/test/resource/dependency inputs within each revision. Test edits require a new complete affected census. Focused follow-ups remain diagnostic; a two-class shared-test scenario must reject an older class B kill after tests change |
| Global pool-index normalization erases hash-digit strings | major, follow-up | Valid in existing source: the comparator globally replaces `#digits`, including resolved strings. The spec now requires operand-only normalization and distinct `"#12"`/`"#34"` fixtures before acceptance. Implementation belongs to the infrastructure PR |
| Candidate-only methods omitted from review inventory | major, follow-up | Valid in existing source: class selection and changed-method reporting originate from baseline mutations. The spec now requires both revision inventories, added classes/methods including zero-mutant methods, explicit unmatched reporting and human dispositions. Added-only regression scenario required |

These are prospective new-gate rules, not an edit to old report statuses or a claim that interrupted
Spec 97 follow-ups have passed. Existing default equivalent-status handling remains owned by Spec 12.06.

Connected-code review also made bootstrap uncertainty explicit: identical invokedynamic descriptors
cannot prove unchanged recipes. The spec permits conservative unmatched reporting rather than a new
bootstrap-normalization framework. This is a preservation limit, not a claim that existing historical
comparisons already handled every bootstrap case. The final reader confirmed the two comparator
corrections and bootstrap uncertainty are clear future implementation obligations, with no new finding.
Ordinary connected-code review also checked default-gate ownership, baseline preservation, report
population accounting and the prospective-only policy transition. No runtime correctness or speedup
is established by these authoring reviews. Remote CI/manual approval remain pending at publication.
