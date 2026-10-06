import com.sun.management.OperatingSystemMXBean
import java.io.File
import java.lang.management.ManagementFactory
import java.math.BigDecimal
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    `java-library`
    `maven-publish`
    java
    application
    // Newest published release, and the only one that can be used here at all.
    //
    // The obvious-looking alternative, pinning 1.15.0 to avoid a claimed JUnit-platform
    // classpath conflict, does not work: 1.15.0 calls `reporting.baseDir`, removed in
    // Gradle 9, so it fails at apply time before any configuration is evaluated. The
    // conflict it was supposed to avoid does not materialise -- `./gradlew clean test`
    // with this version is 353/353 green.
    //
    // `pitestVersion` below is what actually has to be pinned, and for the opposite
    // reason: the plugin's bundled PIT defaults are far older than this project's
    // Java 26 bytecode.
    id("info.solidsoft.pitest") version "1.19.0"
}

group = "dev.samhb.interleave"
version = "1.0-SNAPSHOT"
description = "interleave — explicit-state model checker for concurrent programs"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "dev.samhb.interleave.cli.Main"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<JavaCompile> {
    options.encoding = Charsets.UTF_8.name()
    options.release.set(26)
}

tasks.withType<Test> {
    systemProperty("file.encoding", Charsets.UTF_8.name())
}

// Javadoc correctness and missing-documentation checks are both enforced after the
// production documentation audit in spec 13.10. Keep -Werror: warnings must fail
// the build rather than silently reintroducing undocumented API contracts.
tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:all", "-quiet")
        addBooleanOption("Werror", true)
        // javadoc stops reporting after 100 warnings by default and says nothing, so a large gap
        // silently presents as a complete report of exactly 100. Raise the cap so the gate sees
        // the whole backlog.
        addStringOption("Xmaxwarns", "10000")
        encoding = "UTF-8"
    }
    isFailOnError = true
}

// ---------------------------------------------------------------------------
// Mutation testing (PIT)
//
// Scoped by default to the two packages carrying the preemption-aware storage
// and the context-bounded search itself -- the code that produced the two
// defects the 124-test suite did not catch. `-PfullMutation` widens to the
// whole engine. Thresholds are 0 while the baseline is measured.
// ---------------------------------------------------------------------------

val fullMutation = providers.gradleProperty("fullMutation").isPresent

// Overrides the scope outright. Exists so the smoke test and any future bisect are
// a command-line flag rather than an edit-and-revert, which is how a throwaway
// debugging change silently becomes permanent.
val targetOverride = (findProperty("pitestTargetOverride") as String?)?.split(",")

// Declared here, beside the property it derives from, because its first USE is in the
// `pitest { }` block below. That ordering compiles either way in a Gradle Kotlin script,
// but a reader hitting `if (isScopedRun)` up there with no visible declaration below has
// no way to tell forward references are safe here. Deriving it adjacent to its input
// removes the question.
val isScopedRun = targetOverride != null

val pitestTargetClasses = targetOverride ?: if (fullMutation) {
    listOf("dev.samhb.interleave.*")
} else {
    listOf(
        "dev.samhb.interleave.state.*",
        "dev.samhb.interleave.cb.*",
    )
}

// Broad on purpose, and load-bearing rather than a placeholder to be trimmed for
// speed. PIT does NOT reach outside this filter to find covering tests: corpus-wide
// suites like `cli.MainCBFlagsTest` genuinely cover `ContextBoundedExplorer` and kill
// mutants here only because this glob admits them. Narrow it to a hand-picked list
// and every excluded covering test stops contributing kills, its mutants surface as
// survivors, and the suite's real strength is silently understated.
val pitestTargetTests = listOf("dev.samhb.interleave.*")

// One core reserved for the Gradle daemon, the minion launcher and the IDE.
// Clamped to at least one HERE, not at the point of use, because this value is consumed
// twice downstream and only one of those uses is guarded. An explicit -PpitestThreads=0
// reaches `memoryBudgetMb / requestedThreads` as a divisor and fails with a bare
// ArithmeticException that names no property; a negative value divides without error and
// then threads through to PIT. Both are typos, but the arithmetic one produces a
// stack trace rather than a diagnosable message, and the fix is one coerceAtLeast here
// rather than a guard at each use site.
val requestedThreads = ((findProperty("pitestThreads") as String?)?.toInt()
    ?: (Runtime.getRuntime().availableProcessors() - 1)).coerceAtLeast(1)

// PER-MINION, not total: peak resident memory is roughly
// pitestThreads * pitestMaxMemoryMc, and the aggregate is what can kill the box.
// The 384 MB floor is a correctness constraint, not a preference: below it a minion
// starts failing for reasons unrelated to the mutant, which converts real kills into
// nondeterministic TIMED_OUT survivors. But clamping UP to that floor silently breaks
// the budget on a small host -- 2 GB / 7 threads asks for 219 MB, gets 384, and
// overcommits to 2688 MB on a 2 GB machine. So the thread count is DERIVED FROM the
// budget instead, which makes the floor unreachable by construction rather than
// violated by a clamp. The 1024 ceiling still binds on large hosts, and that is safe:
// it only ever moves the allocation below the budget.
// Total PHYSICAL memory, not Runtime.maxMemory(). The latter returns the Gradle daemon's
// own max heap (commonly ~512 MB), which silently collapsed this budget to a single minion
// at 384 MB on a machine with plenty to spare. Ask the OS instead.
val hostMemoryMb = ((ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean)
    .totalMemorySize / (1024L * 1024L)).toInt()
val memoryBudgetMb = (hostMemoryMb * 3 / 4).coerceAtLeast(384)

// Provisional per-minion share, used only to size the thread cap. An explicit
// -PpitestMaxMemoryMc replaces it, so the cap has to be re-derived from the value
// that actually ships -- otherwise -PpitestMaxMemoryMc=2048 on a 2 GB host still
// launches 7 minions and asks the kernel for 14 GB.
val requestedMaxMemoryMc: Int = (findProperty("pitestMaxMemoryMc") as String?)?.toInt()
    ?: (memoryBudgetMb / requestedThreads).coerceIn(384, 1024)

// Clamp the per-minion heap into the budget FIRST, then derive threads from the
// clamped value, so no combination of override and thread count can exceed the
// aggregate. The only remaining way to exceed the budget is a host too small to
// give a single minion 384 MB, where the floor wins by exactly one minion -- the
// least-bad option, since below that a minion fails for reasons unrelated to the
// mutant it is supposed to be judging.
val pitestMaxMemoryMc = requestedMaxMemoryMc.coerceIn(384, memoryBudgetMb)
val pitestThreads = requestedThreads.coerceAtMost(
    (memoryBudgetMb / pitestMaxMemoryMc).coerceAtLeast(1),
)

pitest {
    // The plugin's own default PIT is far too old for Java 26 bytecode (class file
    // major 70). PIT shades ASM with no version override available, so this is a hard
    // floor: if 1.30.0 ever fails to read the classes, the fix is lowering
    // `options.release` and re-verifying the suite, not silently downgrading PIT.
    pitestVersion.set("1.30.0")
    junit5PluginVersion.set("1.2.3")

    targetClasses.set(pitestTargetClasses)
    targetTests.set(pitestTargetTests)

    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
    // A typo in the scope -- or an emptied package after a refactor -- would
    // otherwise report success having mutated nothing.
    failWhenNoMutations.set(true)

    // Spec 12.06 R2 — the floor, derived from the post-remediation measurement of
    // Specs 12.01–12.05: 256/270 = 94.81% assertion-backed, at commit c7009a1.
    //
    // This threshold is CORROBORATION, not the gate. Two reasons, both measured:
    //
    //  - PIT rounds. 94.81% renders as 95, so this setting would pass a build that
    //    lost a kill. That is also why the floor is 94 and not 95: a floor equal to
    //    the rounded figure gates nothing the rounding has not already granted.
    //  - PIT counts non-assertions as kills. TIMED_OUT, MEMORY_ERROR, NON_VIABLE,
    //    RUN_ERROR and EQUIVALENT all carry detected = true in PIT 1.30.0, the same
    //    flag as KILLED. A mutant bought with wall time raises this number with no
    //    assertion behind it.
    //
    // The gate that neither of those can fool is the `mutationRatchet` task below,
    // which compares KILLED/total against the same floor in exact integer
    // arithmetic. Set both from the same derivation, and change both together.
    //
    // Zeroed for a SCOPED run (-PpitestTargetOverride), deliberately. The floor was
    // derived from a full-scope measurement, and a scoped run's denominator is one
    // class, so its percentage is not comparable to the aggregate this floor describes.
    // Applying it to a subset would be the exact conflation AGENTS.md warns about --
    // and it would fail spuriously on any class weaker than the whole-target average.
    // Verified before this guard existed: `-PpitestTargetOverride=...BitstateStore`
    // measures 94/97 = 96.9%, which clears 94, while the ratchet's total-mutant
    // assertion fired on "expected 270 mutants, PIT generated 97" and broke the
    // documented scoped-iteration workflow. Scoped runs print the census and gate
    // nothing; CI runs full scope and gates everything.
    mutationThreshold.set(if (isScopedRun) 0 else 94)

    // R3 — line coverage stays ungated, deliberately. It is a useful signal but a
    // separate decision: gating it and gating mutation coverage are not the same
    // choice, and an earlier comment here conflated the two.
    coverageThreshold.set(0)

    // R4 — `testStrengthThreshold.set(0)` is GONE rather than set to 0. Setting a
    // default to its own value implies a gate that does not exist, which is how this
    // file spent months advertising a threshold nobody was enforcing. Test strength
    // also has the wrong denominator for a ratchet: it excludes NO_COVERAGE, so a
    // score built on it hides exactly the unexercised code this spec set exists to
    // close. Mutation coverage is the right denominator and is gated above.

    threads.set(pitestThreads)
    // There is no `maxMemoryInMc` on this plugin; per-minion heap is a plain JVM arg,
    // which is the more general lever since this is also where -XX flags would go.
    jvmArgs.set(listOf("-Xmx${pitestMaxMemoryMc}m"))

    // Must exceed the slowest covering test's normal runtime by a clear margin.
    //
    // TIMING OUT IS NOT A KILL, BUT PIT TREATS IT AS ONE. Verified in pitest-1.30.0.jar:
    // DetectionStatus's static initialiser builds each constant as (name, ordinal, detected),
    // and TIMED_OUT and MEMORY_ERROR both carry detected = true -- the same flag as KILLED. (So
    // do NON_VIABLE, RUN_ERROR and EQUIVALENT; SURVIVED and NO_COVERAGE do not.) A mutant that
    // makes a covering test exceed this budget is scored as killed and the mutation score rises
    // without a single assertion firing.
    //
    // That matters more here than in a typical project. A mutant that merely breaks deduplication
    // makes exploration exponentially slower, so exactly the mutants worth catching are the ones
    // most likely to be killed on time instead of on an assertion. The fix for a mutant that
    // times out is to raise `timeoutConstInMillis` or `timeoutFactor` so the real assertion gets
    // its chance -- those are the only two timeout controls PIT 1.30.0 exposes (verified: the
    // command-line jar holds TIMEOUT_CONST/TIMEOUT_FACTOR and zero occurrences of "faster").
    // The baseline has zero TIMED_OUT and zero MEMORY_ERROR, and the `mutationRatchet` task below
    // now FAILS the build if either becomes non-zero — so this is enforced, not merely checked by
    // hand. api/InterleaveRunnerTest drives maxTime(1ms) and maxTime(30s), so the covering set
    // contains wall-clock-sensitive tests whose kills are non-deterministic under parallel load.
    // Re-run any surprising survivor before believing it.
    //
    // Fuller write-up, including the status-by-status kill classes and the accounting rule, is
    // `docs/specs/active/12-mutation-hardening/06-mutation-ratchet.md` -- six statuses score as
    // detected and only one of them is an assertion. Spec 12.06 landed that gate: the
    // `mutationRatchet` task below counts every non-KILLED status as a survivor and fails on
    // TIMED_OUT, MEMORY_ERROR, NON_VIABLE and RUN_ERROR.
    // api/InterleaveRunnerTest drives maxTime(1ms) and maxTime(30s), so the covering
    // set contains wall-clock-sensitive tests whose kills are non-deterministic under
    // parallel load. Re-run any surprising survivor before believing it.
    // Named `timeoutConstInMillis` here, not `timeoutConstant`.
    timeoutConstInMillis.set(4000)
    timeoutFactor.set(BigDecimal("1.5"))

    // Off for the baseline: incremental analysis reports unchanged classes from stored
    // history rather than re-running them, understating the real starting point. It is
    // also a local optimisation only -- on a fresh CI runner there is no history, so it
    // degenerates to a full run and must never be the reason CI looks fast.
    //
    // The history locations are deliberately NOT set here. Wiring historyInputLocation
    // at a path that does not exist yet makes PIT's EntryPoint.pickHistoryStore abort
    // the run before a single mutant is generated, and the baseline has no use for a
    // history file. Set both locations together with this flag when enabling
    // incremental analysis locally.
    enableDefaultIncrementalAnalysis.set(false)

    // Enumerated explicitly rather than DEFAULTS/STRONGER/ALL. DEFAULTS spends cycles
    // on string mutations that carry no meaning in storage or search code, and an
    // explicit list is auditable. This narrowing, not test selection, is the dominant
    // performance lever.
    //
    //  CONDITIONALS_BOUNDARY  off-by-one in preemption accounting, `p + 1`, loop bounds
    //  NEGATE_CONDITIONALS   invert boolean control flow outright
    //  MATH                  arithmetic in density/FPR/load-factor math
    //  INVERT_NEGS           sign errors in counters
    //  INCREMENTS            loop-counter changes in search and encoding
    //  VOID_METHOD_CALLS     drops a side-effecting call whose result is unused
    //  NON_VOID_METHOD_CALLS replaces a call with a default, catching ignored returns
    //  PRIMITIVE_RETURNS /   `isVisited` returning false unconditionally is the
    //  RETURNS /             canonical catastrophic mutant for a state store
    //  FALSE_RETURNS /
    //  TRUE_RETURNS /
    //  EMPTY_RETURNS
    //
    // Every ID below is verified against `Mutator.allMutatorIds()` on PIT 1.30.0, not
    // taken from memory. `RETURNS_VALUES` was the original guess here and PIT rejects it
    // outright ("Mutator or group RETURNS_VALUES is unknown") before generating a single
    // mutant, so an invented ID is a hard startup failure rather than a silent no-op.
    // Note `NULL_RETURNS` is a real but DIFFERENT mutator (swap for null); the general
    // return-value group is `RETURNS`. Re-verify this list on any PIT bump.
    mutators.set(
        listOf(
            "CONDITIONALS_BOUNDARY",
            "NEGATE_CONDITIONALS",
            "MATH",
            "INVERT_NEGS",
            "INCREMENTS",
            "VOID_METHOD_CALLS",
            "NON_VOID_METHOD_CALLS",
            "PRIMITIVE_RETURNS",
            "RETURNS",
            "FALSE_RETURNS",
            "TRUE_RETURNS",
            "EMPTY_RETURNS",
        )
    )

    verbose.set(true)
}

// ---------------------------------------------------------------------------
// Mutation ratchet — Spec 12.06
//
// PIT's own thresholds cannot be the gate, for two independent reasons, and both
// are measured rather than assumed:
//
//  1. It rounds. 256/270 = 94.81% renders as 95, so `mutationThreshold(95)` would
//     pass a build that lost a kill. The comparison below is exact integer
//     arithmetic -- `killed * 100 >= floor * total` -- so no rounding and no
//     binary floating point decides the outcome.
//  2. It counts non-assertions as kills. In PIT 1.30.0, DetectionStatus builds each
//     constant as (name, ordinal, detected) and TIMED_OUT, MEMORY_ERROR,
//     NON_VIABLE, RUN_ERROR and EQUIVALENT all carry detected = true — the same flag
//     as KILLED. A mutant broken by wall time or heap therefore raises the score
//     with no assertion behind it. For a model checker that is the dangerous
//     direction: a mutant that breaks deduplication runs *slower*, so the mutants
//     most worth catching are the ones most likely to be bought with time.
//
// So this task computes assertion-backed coverage (KILLED only) and gates on that,
// treating every other status as a survivor regardless of how PIT scored it.
//
// It is wired with finalizedBy rather than as a separate CI step on purpose: the
// gate then runs on `./gradlew pitest` locally, so a developer cannot produce a
// green local run that CI would reject. The per-class PIT table in the spec README
// drifted twice before 12.03 caught it, and a gate that only exists in CI is one
// more thing local runs cannot see.
// ---------------------------------------------------------------------------

/**
 * Scope and mutator set are fixed; a different population is a different baseline.
 *
 * 268 remains the measured population in 13.09: CanonicalEncoder gains 12 killed mutants,
 * HashingStateStore loses a net 9 killed mutants, and ContextBoundedExplorer loses a net 3.
 * All 13 survivors are unchanged; the shared key moves derivation rather than suppressing mutants.
 * See 13.09 for the method-level census.
 *
 * Historically, 13.03 moved 270 to 268. `ContextBoundedExplorer` fell from 105 mutants to 103
 * when line 187's hand-rolled `enabled.isEmpty() && !config.allTerminated()` was replaced with
 * `config.isDeadlockCandidate()` — provably the same predicate, conjunction commutated. Two mutants
 * disappeared with the condition: one that was killed, and the `NonVoidMethodCallMutator` on
 * `config.allTerminated()` that had survived since 12.05.
 *
 * That survivor disappearing is a rise in the headline percentage with no new test, which is the shape
 * D5 of the 13 set existed to prevent. It is recorded here rather than left to be discovered, because
 * the count went DOWN and the score went UP and only one of those is obviously good. The mutants went
 * away because the duplicated derivation was consolidated, which was the assigned work; had the call
 * been deleted for the purpose of killing the mutant, this would be the score-laundering D5 forbids.
 */
val EXPECTED_TOTAL_MUTANTS = 268

/**
 * Per-class kills/total, checked against the measurement on every run and reported as a WARNING only.
 *
 * Closes DEFERRED E1's remaining half and E2 with one mechanism. E1 was half-closed when the ratchet
 * started printing the measured census; what kept recurring is that the *spec* tables were
 * hand-maintained alongside it, so they drifted — twice before 12.03 caught it, and again the moment
 * 13.03 changed ContextBoundedExplorer's denominator. A number written in two places and compared in
 * neither is a number that will be wrong.
 *
 * So the expectation lives here, next to the gate that computes the measurement, and every run prints
 * any disagreement. Deliberately not a gate (D4): this is a notice that a human-maintained table has
 * moved, not a claim about correctness. Making it fail would mean a legitimate spec change turns CI
 * red for a documentation reason.
 */
val EXPECTED_PER_CLASS = mapOf(
    "dev.samhb.interleave.state.CanonicalEncoder" to "18/19",
    "dev.samhb.interleave.state.HashingStateStore" to "43/52",
    "dev.samhb.interleave.state.BitstateStore" to "94/97",
    "dev.samhb.interleave.cb.ContextBoundedExplorer" to "100/100",
)

/**
 * Floor as an integer percent of assertion-backed coverage. Frozen at 94 by D2 for all of spec set 13.
 *
 * Not 95: PIT rounds 94.81 up to 95, so a floor of 95 gates nothing the rounding has not already
 * granted. The floor is at most (rounded figure - 1) for exactly that reason.
 *
 * Recomputed against the 268-mutant population: 94*268 = 25192, so 252 kills clear the gate and 251
 * does not. That is one lost kill more permissive than the 270-mutant arithmetic (94*270 = 25380, so
 * 254 cleared and 253 did not) — a fixed percentage against a smaller denominator buys a little slack.
 * Flagged rather than papered over: if the set wants the original sensitivity, the fix is to gate on an
 * absolute kill count, not to nudge this number.
 */
val MUTATION_FLOOR_PERCENT = 94

/**
 * Mutants permitted to be classified EQUIVALENT by PIT itself.
 *
 * Empty, and that is the correct initial state: it means every equivalent mutant
 * must be adjudicated and recorded here before CI will tolerate it. An entry names
 * the mutant and carries the spec verdict justifying it.
 *
 * Note this is NOT where the known-equivalent `CanonicalEncoder` `flush()` mutant
 * belongs. PIT reports it SURVIVED, not EQUIVALENT — it cannot prove equivalence,
 * only fail to kill it — so it never appears in this list and is instead carried in
 * the denominator. See DEFERRED.md E3.
 *
 * Entries are `fully.qualified.Class:line`, the same key shape as
 * `KNOWN_EQUIVALENT_SURVIVORS` below. The mutator is deliberately NOT part of the
 * key: it would have to be spelled out in full at every entry, and a mutator rename
 * would then silently invalidate entries that are still correct.
 */
val EQUIVALENT_ALLOW_LIST = emptyList<String>()

/**
 * Survivors with a *recorded* reason for surviving, keyed `fully.qualified.Class:line`.
 *
 * This does NOT suppress anything. The mutant stays in the denominator and keeps counting against
 * the floor, because dropping it would raise the score without any testing — the exact move R2
 * exists to prevent. What it buys is that the survivor count decomposes into "explained" and
 * "unexplained", so a reviewer reading a red ratchet knows whether an unexplained survivor appeared.
 *
 * The entry is a survivor rather than an EQUIVALENT on purpose: PIT cannot prove equivalence, only
 * fail to kill, so it reports SURVIVED and `EQUIVALENT_ALLOW_LIST` never sees it. The verdict was
 * established by experiment in 12.01 §R5 — deleting the call leaves the whole suite green.
 */
val KNOWN_EQUIVALENT_SURVIVORS = mapOf(
    "dev.samhb.interleave.state.CanonicalEncoder:29" to
        "12.01 R5 — out.flush() removed leaves the full suite green; verified equivalent by" +
        " experiment, not inferred from PIT's status.",
)

/** Statuses PIT scores as `detected` that no assertion earned. */
val NON_ASSERTION_DETECTED = listOf("TIMED_OUT", "MEMORY_ERROR", "NON_VIABLE", "RUN_ERROR")

tasks.register("mutationRatchet") {
    description = "Gates assertion-backed mutation coverage against the Spec 12.06 floor."
    group = "verification"
    // Deliberately NO `dependsOn(pitest)`, even though that reads like the obvious way to
    // make this task usable on its own.
    //
    // `pitest.finalizedBy(mutationRatchet)` already guarantees ordering, and adding
    // `dependsOn(pitest)` quietly BREAKS the gate on the one path it matters most. When
    // `pitest` fails, Gradle still runs its finalizers -- verified, not assumed: a marker
    // finalizer attached to the real `pitest` task ran on a failing run. But a finalizer
    // whose own `dependsOn` includes the failed task is skipped, because its dependency
    // did not succeed. So `mutationRatchet` was silently skipped whenever PIT failed,
    // which is exactly when the census is wanted.
    //
    // Running it standalone now re-reads whatever report exists and refuses it if none does or
    // if it predates the source tree. See the staleness guard in the body — dropping
    // `dependsOn(pitest)` is what made the finalizer work, and it also opened a false-green
    // that the guard closes.

    val report = layout.buildDirectory.file("reports/pitest/mutations.xml")
    // Deliberately NOT `dependsOn(pitest)` — see above for why that broke the finalizer.
    // Compiling is a different matter and is safe: these succeed even when `pitest` fails, and
    // they are what makes the staleness guard below content-accurate rather than mtime-naive.
    dependsOn(tasks.named("classes"), tasks.named("testClasses"))

    doLast {
        val xml = report.get().asFile
        if (!xml.isFile) {
            // NOT a build-ordering failure any more — `dependsOn(pitest)` is gone, so this task
            // no longer implies PIT ran first. The likelier causes are that PIT never ran, or
            // that it died before writing anything (a compile error, or the job being killed).
            // The finalizer runs even in those cases, so this error can appear ALONGSIDE the
            // real failure rather than instead of it; the first error in the log is the cause.
            throw GradleException(
                "Ratchet: no mutation report at ${xml.path}. PIT produced no XML, so there is " +
                    "nothing to gate. Either `pitest` was not run in this invocation, or it " +
                    "failed before writing a report (a compile error, or the job being killed).\n" +
                    "If this is the only error, run `./gradlew pitest` first. If another error " +
                    "precedes this one, that is the cause and this is only its consequence."
            )
        }

        // Staleness guard. Removing `dependsOn(pitest)` (above) is what made the finalizer work,
        // and it also means this task can now be run on its own against whatever report happens
        // to be lying around. Without this check that is a false green: edit production or test
        // source, run `mutationRatchet`, and a report generated from the PREVIOUS source still
        // satisfies both the total-mutant count and the floor. The gate would report PASS for
        // code it never looked at.
        //
        // Compared against COMPILED CLASSES, not source files, and that distinction is the whole
        // design. Gradle decides up-to-dateness by content hash, not mtime, so `touch`, `git
        // checkout` and `git stash` all bump mtimes without changing a byte -- and a first attempt
        // that compared against `src/` reported a stale report after a bare `touch`, then found
        // `pitest` itself UP-TO-DATE and agreed nothing had changed. Both were right; the
        // heuristic was wrong. Class files are only rewritten when content really changes, so they
        // carry exactly the signal being asked about.
        //
        // `dependsOn(classes, testClasses)` makes that signal current before it is read: editing
        // source recompiles, which makes the classes newer than the report, which is the stale
        // case. And because those dependencies still succeed when `pitest` fails, the finalizer
        // keeps running on the failure path -- the original bug. If compilation itself fails,
        // these dependencies fail and the finalizer is skipped, so the misleading missing-report
        // error does not appear at all.
        val newestClass = listOf(
            layout.buildDirectory.dir("classes/java/main"),
            layout.buildDirectory.dir("classes/java/test"),
        ).map { it.get().asFile }
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { f -> f.isFile }.toList() }
            .maxByOrNull { it.lastModified() }
        if (newestClass != null && xml.lastModified() < newestClass.lastModified()) {
            throw GradleException(
                "Ratchet: the mutation report predates the compiled classes.\n" +
                    "  report : ${xml.path}\n" +
                    "  newer  : ${newestClass.path}\n" +
                    "A report generated before that change cannot describe the current code, so " +
                    "passing it here would be a false green. Re-run `./gradlew pitest`."
            )
        }

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
        val mutations = doc.getElementsByTagName("mutation")

        val counts = mutableMapOf<String, Int>()
        val equivalents = mutableListOf<Pair<String, String>>() // stable id to mutator, for display
        val perClass = mutableMapOf<String, IntArray>()
        val nonKillIds = mutableListOf<String>()
        for (i in 0 until mutations.length) {
            val m = mutations.item(i) as org.w3c.dom.Element
            val status = m.attributes.getNamedItem("status").nodeValue
            counts.merge(status, 1, Int::plus)

            val cls = m.getElementsByTagName("mutatedClass").item(0).textContent
            val line = m.getElementsByTagName("lineNumber").item(0).textContent
            val bucket = perClass.computeIfAbsent(cls) { IntArray(2) }
            bucket[0]++
            if (status == "KILLED") bucket[1]++

            if (status == "EQUIVALENT") {
                val mut = m.getElementsByTagName("mutator").item(0).textContent
                equivalents += ("$cls:$line" to mut)
            }
            // Every status other than KILLED is a non-kill, NO_COVERAGE included. An earlier draft
            // excluded NO_COVERAGE here on the reasoning that "survivor" means SURVIVED — but
            // NO_COVERAGE is the *other* way a mutant fails to be killed, and it stays in the
            // denominator, so omitting it under-reports exactly the unexercised code the gate
            // exists to surface. Both SURVIVED and NO_COVERAGE carry detected = false in
            // DetectionStatus, which is the operative test. The list is named `nonKillIds` rather
            // than `survivorIds` for that reason.
            if (status != "KILLED") nonKillIds += "$cls:$line"
        }

        val total = mutations.length
        val killed = counts["KILLED"] ?: 0

        println("Mutation ratchet — assertion-backed coverage")
        println("  total mutants          : $total")
        println("  KILLED (assertion)     : $killed")
        for (status in NON_ASSERTION_DETECTED + "EQUIVALENT") {
            println("  $status :".padEnd(25) + (counts[status] ?: 0))
        }
        println("  SURVIVED               : ${counts["SURVIVED"] ?: 0}")
        println("  NO_COVERAGE            : ${counts["NO_COVERAGE"] ?: 0}")

        // Per-class census, straight from the XML. This is the "generated at ratchet time" half of
        // DEFERRED E1: the spec README's per-class table is still hand-maintained, but the numbers
        // a reviewer needs in order to catch it drifting are now emitted by the build itself, on
        // every run. Read these against the README table rather than re-running PIT by hand.
        println()
        println("  per class:")
        for ((cls, b) in perClass.toSortedMap()) {
            val short = cls.substringAfterLast('.')
            println("    $short :".padEnd(34) + "${b[1]}/${b[0]}")
        }

        // E1/E2 drift notice — non-failing by design (D4). Prints whenever a spec's recorded per-class
        // figure no longer matches the measurement, so a hand-maintained table cannot go quietly stale
        // the way it did twice before 12.03 and once more at 13.03.
        val drifted = EXPECTED_PER_CLASS.mapNotNull { (cls, recorded) ->
            val measured = perClass[cls]?.let { "${it[1]}/${it[0]}" }
            if (measured != null && measured != recorded) (cls to (recorded to measured)) else null
        }
        // A scoped population intentionally omits other classes; only a full run can lose one.
        val missing = if (isScopedRun) emptyList()
            else EXPECTED_PER_CLASS.keys.filterNot { perClass.containsKey(it) }
        if (drifted.isNotEmpty() || missing.isNotEmpty()) {
            println()
            println("  NOTICE — recorded per-class figures have drifted (not a gate; see D4):")
            for ((cls, pair) in drifted) {
                println("    $cls — recorded ${pair.first}, measured ${pair.second}")
            }
            for (cls in missing) {
                println("    $cls — recorded but absent from this run's population")
            }
            println("    Update the spec table AND EXPECTED_PER_CLASS in the same commit.")
        }

        // Decompose the non-kills into explained and unexplained. E3's cost was that a known
        // equivalent was a survivor whose reason lived only in prose; naming it here makes the
        // reason machine-readable without removing it from the denominator.
        val explained = nonKillIds.filter { KNOWN_EQUIVALENT_SURVIVORS.containsKey(it) }
        val unexplained = nonKillIds.filterNot { KNOWN_EQUIVALENT_SURVIVORS.containsKey(it) }
        println()
        println("  non-kills with a recorded reason : ${explained.size}")
        for (id in explained) println("    $id — ${KNOWN_EQUIVALENT_SURVIVORS[id]}")
        println("  non-kills with NO recorded reason: ${unexplained.size}")
        val stale = KNOWN_EQUIVALENT_SURVIVORS.keys.filterNot { nonKillIds.contains(it) }
        if (stale.isNotEmpty()) {
            println("  WARNING — KNOWN_EQUIVALENT_SURVIVORS names mutants that were not non-kills:")
            for (id in stale) println("    $id — the recorded reason is stale; re-adjudicate it")
        }

        println()

        // Scoped run: report everything above, but skip the two AGGREGATE gates.
        //
        // Both are meaningless on a subset — R5's baseline is one class rather than 270,
        // and R7's floor was derived from a whole-target measurement whose denominator a
        // scoped run does not share. AGENTS.md already records that a scoped percentage is
        // not comparable to a full-scope one; enforcing one against the other would be
        // exactly that conflation.
        //
        // Said out loud rather than skipped silently, because a green scoped run must not
        // read as "the gate passed". The per-mutant checks (R6) still run below: whether
        // an individual mutant was killed by wall time is a fact about that mutant, not
        // about the scope.
        val enforceAggregate = !isScopedRun
        if (!enforceAggregate) {
            println("  SCOPED RUN (-PpitestTargetOverride) — R5 (total mutants) and R7 (floor)")
            println("  are NOT enforced: both are full-scope checks and this run is a subset.")
            println("  R6 (non-assertion detections) still applies. For the gate, run full scope.")
        }

        // R5 — assert the denominator. A percentage cannot detect a silent change
        // in the population: dropping 50 mutants and 50 kills looks identical to no
        // change at all. The message names the hypothesis, because the reader is
        // looking at a red job and needs the cause, not just the delta.
        if (enforceAggregate && total != EXPECTED_TOTAL_MUTANTS) {
            throw GradleException(
                "Ratchet: expected $EXPECTED_TOTAL_MUTANTS mutants, PIT generated $total.\n" +
                    "Either the scope moved (targetClasses), the mutator set moved, or the\n" +
                    "runtime changed. Do NOT simply update the constant — confirm which of\n" +
                    "the three it is, and re-derive MUTATION_FLOOR_PERCENT if the population\n" +
                    "changed for a reason other than dead-code removal."
            )
        }

        // R6 — non-assertion detections. Counted as survivors for ratcheting by
        // construction (only KILLED is counted above). Failing is separate and
        // per-status, which is what keeps an allow-listed EQUIVALENT tolerable
        // while still costing it against the floor.
        for (status in NON_ASSERTION_DETECTED) {
            val n = counts[status] ?: 0
            if (n > 0) {
                throw GradleException(
                    "Ratchet: $n mutant(s) scored as detected via $status, which no assertion\n" +
                        "earned. For TIMED_OUT/MEMORY_ERROR, raise timeoutConstInMillis or\n" +
                        "timeoutFactor for the specific mutant and record why — never as a\n" +
                        "blanket policy, which widens the window for every future mutant.\n" +
                        "For NON_VIABLE/RUN_ERROR the remedy is the scope or the build, not the\n" +
                        "timeout."
                )
            }
        }

        // Any EQUIVALENT mutant absent from the allow-list is a finding — the test is
        // `unlisted.isNotEmpty()`, NOT a count comparison. An earlier draft compared
        // `unlisted.size > EQUIVALENT_ALLOW_LIST.size`, which happened to work only while the
        // list was empty (1 unlisted vs 0 entries): with 2 entries and 2 unlisted it read
        // `2 > 2` and passed, so widening the allow-list silently disabled the check. Size
        // comparison is the wrong shape here — membership is a per-mutant property, not a tally.
        val unlisted = equivalents.filterNot { EQUIVALENT_ALLOW_LIST.contains(it.first) }
        if (unlisted.isNotEmpty()) {
            throw GradleException(
                "Ratchet: ${unlisted.size} EQUIVALENT mutant(s) not on the recorded allow-list " +
                    "of ${EQUIVALENT_ALLOW_LIST.size} entries.\nUnlisted:\n  " +
                    unlisted.joinToString("\n  ") { "${it.first} (${it.second})" } +
                    "\nAn equivalent mutant must be adjudicated and recorded with a reason before\n" +
                    "CI tolerates it. Note it still counts against the floor either way."
            )
        }

        // R7 — the binding gate. Exact integer arithmetic on purpose: rounding first
        // would make the gate one kill looser and leave the verdict to floating point.
        if (enforceAggregate && killed.toLong() * 100 < MUTATION_FLOOR_PERCENT.toLong() * total) {
            throw GradleException(
                "Ratchet: assertion-backed coverage is $killed/$total = " +
                    "${"%.2f".format(100.0 * killed / total)}%, below the floor of " +
                    "$MUTATION_FLOOR_PERCENT%.\n" +
                    "PIT's own threshold compares its ROUNDED mutation coverage and may have\n" +
                    "passed this build anyway — that is precisely why this gate exists.\n" +
                    "Diff the survivor list against the last green run: ./gradlew pitest, then\n" +
                    "read build/reports/pitest/mutations.xml."
            )
        }

        if (enforceAggregate) {
            println(
                "  RESULT: PASS — $killed/$total = ${"%.2f".format(100.0 * killed / total)}% " +
                    ">= floor $MUTATION_FLOOR_PERCENT% (exact: ${killed}*100 >= " +
                    "$MUTATION_FLOOR_PERCENT*$total)"
            )
        }
    }
}

tasks.named("pitest") {
    finalizedBy(tasks.named("mutationRatchet"))
}
// `finalizedBy` DOES run `mutationRatchet` when `pitest` fails -- Gradle schedules a
// finalized task's finalizers even on failure, verified here with a marker finalizer
// attached to the real `pitest` task.
//
// An earlier version of this comment claimed the opposite ("Gradle stops the build before
// the finalizer") and used it to justify an explicit CI step. That explanation was invented
// rather than measured, and it was wrong. The real cause of a skipped ratchet was the
// `dependsOn(pitest)` that `mutationRatchet` used to declare, and it has been removed -- see
// the task's own comment. The observable symptom was real (`:mutationRatchet` never appeared
// on a failing run); the mechanism was not.
//
// The explicit `if: always()` CI step is retained for a different and narrower reason: it
// still runs when PIT fails so early that no report exists at all (a compile error, an OOM
// kill), where the workflow skips rather than reporting a misleading missing-report error.
// Also note `-x mutationRatchet` and `--dry-run` prevent the action from executing.

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "dev.samhb.interleave"
            artifactId = "interleave"
            version = "1.0-SNAPSHOT"
        }
    }
    repositories {
        mavenLocal()
    }
}
