# Documentation inventory for 13.10

The audit covers 111 production Java files and the four PR #44 test files named in spec 13.10.
Java 26’s compiler tree and `DocTrees` APIs were used to enumerate explicitly written types,
constructors, methods, and fields and check attached Javadoc. This source audit includes private
helpers and anonymous-class methods; implicit record methods and constructors are not counted.
`{@inheritDoc}` counts as attached documentation and is separately resolved by Javadoc.
Record components are covered by their record’s `@param` contracts, rather than field comments.

| Scope | Explicit methods/constructors | With attached Javadoc | Explicit types | With attached Javadoc |
|---|---|---|---|---|
| Production | 762 | 762 | 138 | 138 |
| PR #44 tests | 62 | 62 | 6 | 6 |

The initial audit found 451 methods/constructors without attached Javadoc across the combined
scope. Some already had prose after an annotation; those comments were moved before annotations
so the documentation parser recognizes the existing contract. The remainder received documentation
or valid inherited contracts. Existing parameter and return gaps were also corrected.

The normal Javadoc task checks all doclint groups with warnings as errors. A local private-member
Javadoc run additionally validates implementation-helper and private-record contracts. All new
functions require documentation; CodeRabbit’s 80% check remains a minimum, not the audit target.

This inventory measures explicit source declarations, not CodeRabbit’s touched-function denominator
and not all repository tests. PR #45’s remote CodeRabbit review was skipped because its 122 files
exceeded the available 100-file capacity; no remote coverage percentage was produced. Generated
reports and the temporary compiler audit utility are not committed.
