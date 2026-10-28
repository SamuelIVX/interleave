# Spec: Bounded selector mutation harness

## Objective

Make Spec 40's nonterminating property-selector mutation fail a logical work assertion inside PIT,
without weakening the resource-error gate, adding production guards or changing application output.
This is the final currently tracked 9-V1 harness gap after Spec 47's ten-class reconciliation.

## Scope

Add a JDK-only mutationHarness source set, a small PIT-only Java agent, five ordinary harness tests,
a child-JVM probe and Gradle wiring. Update this spec, the roadmap and follow-up pointers in
Specs 40/47. The agent is excluded from the library/runtime distribution by source-set ownership.
No production source/API, external dependency, target-class/test filter, mutation operator,
exclusion, denominator constant, score floor or CI parallelism setting changes.

The five agent tests live outside PIT's unchanged dev.samhb.interleave.* application-test filter.
They test the harness independently; their subprocess watchdog cannot kill application mutants.
Existing application tests remain PIT's behavioral tests. Both scoped and default PIT use the
agent; ordinary application/test JVMs do not, except the explicit child-agent integration probes.

## Requirements

1. Each component invocation SHALL expand at most the program's finite owner count. The harness
   SHALL assert this logical bound, not a wall-clock deadline or heap threshold.
2. State SHALL be invocation-local and initialized from the existing seen-array length. No modeled
   callback, private-state reflection, shared counter or configurable execution budget is added.
3. Instrumentation SHALL bind exactly the selector's component(Configuration,int,boolean[]) method
   and its List.add expansion checkpoint. Unrelated classes SHALL remain unchanged.
4. Initial definition with unexpected method/checkpoint shape SHALL fail visibly, rather than
   silently omitting the bound. PIT redefinitions SHALL retain existing single-call-removal mutants
   as executable mutations; they SHALL NOT be reclassified as NON_VIABLE merely because that call
   is absent.
5. All prior assertion kills and all mutation identities SHALL remain intact. Report the logical
   assertion-backed kill separately from the historical raw TIMED_OUT/MEMORY_ERROR results.
6. Strict Javadoc SHALL cover the separate agent source set. The agent JAR SHALL never enter the
   published application/library runtime. Keep scoped R6 versus skipped aggregate R5/R7 explicit.

## Bound and instrumentation

The original component marks a seed before enqueueing it. Its inner loop only enqueues previously
unseen owners and marks them before enqueueing. Each owner is therefore dequeued and appended to
the component at most once: for n modeled owners, a component performs at most n such expansions.
The bound includes completed owners and is conservative; it does not rely on elapsed time.

The Java 26 Class-File API adds a fresh local counter at method entry, initialized from argument 3's
boolean-array length. Before the component List.add checkpoint it decrements the counter and
throws AssertionError if it becomes negative. The inline code references only JDK classes; no
agent helper must be visible through PIT's application class loader. No assertion runs in the
shipping application. See the [JDK code-transform API](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/classfile/CodeTransform.html).

The transformer applies on initial load and PIT redefinition. The initial class must have exactly
one matching method and checkpoint. A redefinition may omit that checkpoint only to preserve a
single List.add-removal mutant: the original queue/seen guards remain intact for that mutation,
and existing output tests must still kill it. Multiple simultaneous mutations are outside this
argument. Other missing/changed bindings reject the class; the agent returns invalid class bytes
because the [JVM ignores transformer exceptions](https://docs.oracle.com/en/java/javase/26/docs/api/java.instrument/java/lang/instrument/ClassFileTransformer.html).
The rejection bytes must be nonempty: the child-JVM regression first exposed that an empty
replacement was ignored, then verified an actual ClassFormatError after using invalid nonempty bytes. Full scoped comparison must show no
new NON_VIABLE/RUN_ERROR status or lost kill.

## Independent checks

All five ordinary tests use a real compiled selector definition. Three tests launch four child
JVMs in total; the other two validate instrumentation in-process. The source probe has
owner0 reading x through an empty guarded action, owner1 writing x and owner2 writing z. Components
are{0,1} and{2}; the literal expected selection is[2]. The unmodified selector returns that value
with and without the agent.

A test changes only the actual seen-array conditional in the compiled component. With the agent,
the child must report the exact logical-bound assertion, rather than finish by a watchdog or heap
failure. The writing owner self-queues repeatedly; putting a read-only owner first only postpones
that repetition. No custom footprints, impure model callbacks or copied selector algorithm form
the shipping oracle.

Additional tests reject a missing method and changed checkpoint at initial class load, and accept
the single removed List.add checkpoint only during redefinition. Child output goes to a file;
all children are reaped, including failures. A15-second parent watchdog protects broken harness
startup only and is not the mutation-killing oracle. Tests remain outside PIT's application filter.

## Baseline and verification

PR #88 merged at `bf95fcc`. Fresh ordinary baseline:998 tests and strict Javadoc pass, no failures/
errors/skips. The production selector is unchanged. Spec 40's frozen115-identity report records
110 KILLED,4 SURVIVED,1 TIMED_OUT and failed scoped R6; four survivors retain its boundary proofs.

An initial temporary-agent experiment failed R6 at 110 KILLED,4 SURVIVED,1 MEMORY_ERROR because
build.gradle.kts overwrote the temporary agent JVM argument. That run did not activate the bound
and earns no kill. After correcting temporary launch wiring, the prototype reports111/115 KILLED,
4 SURVIVED, zero uncovered/resource/run errors. All115 identities match Spec 40, all 110 previous
kills remain killed; only component/79 changes from TIMED_OUT to KILLED. The kill is the injected
owner-expansion assertion under an existing SourceReductionTest, not the child watchdog.

All five integrated harness cases and the full 1,003-test suite pass, zero failures/errors/skips.
Final clean build and strict application+agent Javadoc pass. Integrated selector PIT is 111/115
KILLED,4 SURVIVED, zero uncovered/resource/run errors. All115 identities match Spec 40; all 110
previous kills remain killed and only component/79 changes to KILLED. Scoped R6 passes; aggregate
R5 total-mutant/R7 coverage-floor gates are skipped. Shipping library JAR and application
distribution contain no agent/harness classes or JAR. All18 normalization checks pass. Separate
default PIT and its full R5/R6/R7 gate pass at 281/291 KILLED,10 SURVIVED, zero uncovered/
resource/run errors; all 291 identities/statuses match Spec 47. CodeRabbit CLI0.8.1 completed its eight-file review with two MINOR
findings: the child-JVM test-count wording was corrected; the Javadoc strictness concern was
already satisfied by tasks.withType<Javadoc>, confirmed in the generated options file with
-Xdoclint:all and -Werror. No duplicate configuration or extra CLI run was added. All local Gradle commands use `--max-workers=1 -PpitestThreads=1`. Browser tests are
not repeated because production/report output is unchanged. Remote CI remains unverified; publish
a draft and stop. This is bounded-work verification under the finite-owner contract, not a
whole-checker or resource/performance equivalence claim.
