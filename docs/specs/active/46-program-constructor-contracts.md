# Spec: Program constructor validation contracts

## Objective

Close the historical roadmap9-V1 Program empty-thread-list gap with direct public constructor
contracts. Distinguish a program with no modeled owners from an existing owner with no actions.

## Scope

Add ProgramConstructorContractTest, this spec and the roadmap update. Production, API, dependencies,
report output and mutation scope/operators/exclusions/gates remain unchanged. Existing state-copy
and source-digest ownership checks stay in ProgramImmutabilityTest. No broader9-V1 closure claim.
Spec40's nonterminating property-selector mutation and failed scoped ratchet remain separate/open.

## Requirements

1. Both public Program constructors SHALL reject null or empty thread lists with the documented
   IllegalArgumentException. They SHALL reject a null initial state with a valid thread list.
2. A nonempty thread list containing one owner with no steps SHALL remain valid. Its initial
   configuration SHALL have counter[0], no enabled owners, allTerminated=true and
   isDeadlockCandidate=false.
3. Every expectation SHALL be a literal public contract, not reconstructed by another constructor
   or renderer. No exact exception-message spelling is required.
4. The final scoped measurement SHALL preserve every prior kill and account for all identities
   without crediting resource errors or changing gates.

## Design and independent oracles

Four tests exercise both public overloads. Negative fixtures use a real CounterState and null/empty
owner lists; null-state fixtures instead supply an existing empty ModelThread. The three-argument
constructor receives a valid64-character lowercase hexadecimal digest, so unrelated digest rejection
cannot mask the missing-list contract. SourceStep validation is vacuously satisfied for zero steps.

Positive fixtures have one actual owner and zero actions: enumerating that owner gives counter[0]
and no executable step; it has already completed and cannot be a deadlock. These facts are asserted
literally on the real initial configuration, with threadCount1 and the supplied thread retained.
No exploration, synthetic configurations, reflection, impure callbacks or production hooks are
needed. Constructors remain the shared boundary used by ProgramLoader, DslLoader and SourceCompiler.
Their existing parser/compiler validation remains unchanged.

## Baseline and verification

PR #86 merged at093f203. Fresh clean baseline:994 tests pass, zero failures/errors/skips.
Scoped Program has26/27 KILLED,1 SURVIVED and zero uncovered/timeout/memory/run-error statuses;
scoped non-assertion detection checks pass; total-mutant and coverage-floor gates are skipped.
The survivor is the three-argument constructor's bytecode index20
NonVoidMethodCallMutator, which replaces threads.isEmpty() with false. Null threads still reject;
empty lists are erroneously accepted. This confirms the historical26/27 gap is still present.

All four new cases and the998-test full suite pass, zero failures/errors/skips. Final scoped
Program has27/27 KILLED with zero survivors/uncovered/resource-error statuses. Scoped non-assertion
detection checks pass; total-mutant and coverage-floor gates are skipped. All27 semantic identities
(class, method, descriptor, mutator, indices and blocks) match the baseline, and all26 prior kills remain killed. The constructor/index20 is the sole new kill,
asserted by constructorsRejectEmptyThreadLists. All18 normalization checks pass. Final clean build, strict
Javadoc and all998 tests pass. Default PIT281/291 KILLED,10 SURVIVED has zero uncovered/resource-
error statuses, and all291 identities/statuses match Spec45 evidence. CodeRabbit CLI0.8.1 completed its substantive
three-file review with zero findings. All local Gradle commands use `--max-workers=1 -PpitestThreads=1`.
Browser integration is not repeated for test-only changes with unchanged production/report output.
Remote CI remains unverified; publish a draft and stop. The broader historical expanded aggregate
remains unremeasured; default-scope success does not replace Spec40's failed scoped gate.
