# CodeRabbit configuration audit

> The root `.coderabbit.yaml` is valid and its path prompts are active in PR #49. No configuration change or gate weakening is required for the two review fixes.

## Verified configuration

`coderabbit config validate .coderabbit.yaml` passes against the current official schema. CLI agent inspection identifies YAML authority, the repository root file, and a writable configuration. Local inspection does not establish remote organization settings by itself.

The [PR #49 review](https://github.com/SamuelIVX/interleave/pull/49) supplies remote evidence: **Repository YAML (base), Organization UI (inherited)**, profile **ASSERTIVE**, and all 31 changed files selected for processing. The review details explicitly map implementation prompts to the runner, test-quality prompts to test files, documentation prompts to the README/spec, and general guidance to those reviewed files. This confirms the base-branch configuration is being used; configuration edits on a PR branch should not be assumed active before merging.

The file intentionally defines `inheritance: true` and repository-specific `reviews.path_instructions`. Profile, review scheduling, pre-merge checks and other unspecified settings remain inherited. This agrees with CodeRabbit's [inheritance rules](https://docs.coderabbit.ai/configuration/configuration-inheritance).

## Path checks

Fourteen representative paths were checked with minimatch, the matcher linked by CodeRabbit's [path-instruction documentation](https://docs.coderabbit.ai/configuration/path-instructions): production Java, two nested test paths, an example Java file, visualizer JS/MJS, root and nested Markdown, Gradle Kotlin/properties/wrapper configuration, a workflow, and a model JSON resource. Brace alternatives and nested test paths match their intended rules; production guidance does not match tests.

Under standard minimatch defaults, `**/*` excludes leading-dot paths. `.github/**` explicitly covers automation with its own instructions. These checks validate the patterns, not every private option the remote service might pass to its matcher. Future hidden configuration files needing custom guidance should receive an explicit path rule.

## Prompts versus checks

Path instructions guide review reasoning; they are not deterministic CI assertions and do not disable separate features. CodeRabbit explicitly documents that instructions discouraging trivial docstring requests do **not** disable its Docstring Coverage pre-merge check. The 80% coverage advisory can therefore coexist with passing Gradle Javadoc/doclint. Its configured warning and threshold have not been turned off or reduced.

The review's static-analysis tempfile warning was checked against `MainTest.invokeCli`: the file is deleted in `finally`, after terminating and waiting for a still-live child process. No insecure-tempfile production change is indicated by that warning.

## Feedback addressed

- README: list all eight resource definitions, including declarative `lost-update-3t`, and distinguish resources from runnable examples.
- Reporting schema: compare every matching DFS row in DFS-only and mixed reports. Require 16 unique rows, present/null `preemptionsUsed`, equal nested key sets, and the existing equal top-level schema.
- An isolated conditional-omission defect fails the revised schema test with an assertion about the missing DFS member. The passing control and focused reporting suite establish this is an observable contract check.

The scoped local CodeRabbit review completed with zero findings on the README, audit ledger and reporting test. Build/Javadoc and CI status are reported in the PR; the original mutation measurements remain historical evidence for the cleanup snapshot.
