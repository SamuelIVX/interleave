# Contributing

Thank you for your interest in contributing to interleave. This is a personal learning project focused on explicit-state model checking and partial-order reduction in Java. All contributions — bug reports, documentation improvements, test additions, and algorithm refinements — are welcome.

## Getting started

### Prerequisites

- JDK 17 or later
- Git
- Gradle 8.11+ (wrapper included)

### Clone and build

```bash
git clone https://github.com/SamuelIVX/interleave.git
cd interleave
./gradlew build
```

### Run tests

```bash
./gradlew test
```

### Run the CLI

```bash
./gradlew run
```

## How to contribute

### Reporting issues

Before opening a new issue, please search existing issues to avoid duplicates. When opening an issue, include:

- A clear description of the problem or suggestion
- Steps to reproduce, if applicable
- Expected vs actual behavior
- Relevant spec number, if applicable (`docs/specs/active/01-program-representation.md`)

### Proposing changes

1. **Open an issue first** — describe the change you want to make and why. This keeps work aligned with the current spec boundary and avoids wasted effort on out-of-scope features.
2. **Fork and branch** — create a feature branch from `main` with a descriptive name:
   ```bash
   git checkout -b feat/your-change-name
   ```
   Accepted prefixes: `feat/`, `fix/`, `docs/`, `refactor/`, `test/`, `chore/`.

### Pull request process

1. Ensure all tests pass locally:
   ```bash
   ./gradlew test
   ```
2. Follow the PR template when creating a pull request. The template will prompt for:
   - **Summary** of what changed and why
   - **Spec alignment** — which spec or requirement this addresses
   - **Test plan** — how the change was verified
3. Keep PRs small and focused. One logical change per PR.
4. If a PR introduces new public APIs or changes existing ones, update the corresponding spec document.

## Code style

- **Language:** Java 17+
- **Build:** Gradle with Kotlin DSL
- **Testing:** JUnit 5
- **Commits:** use [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`)
- **Documentation:** public classes and methods require JavaDoc; non-obvious implementation details require inline comments

## Code review

- All PRs require at least one review before merging.
- Address review comments by pushing new commits to the same branch — do not force-push or rewrite history after review begins.
- Once approved and all checks pass, the PR will be squash-merged.

## Scope boundaries

This project follows a frozen 7-spec plan. Contributions that fall within the current spec boundary are preferred. Out-of-scope ideas may be noted for future work, but the priority is completing the existing specs with correctness and test coverage.

## Need help?

If you have questions about the codebase, algorithms, or contribution process, open an issue and tag it as a question.
