import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import java.math.BigDecimal

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

// Javadoc *correctness* is a gate. Javadoc *coverage* is not yet, and conflating the two is
// how gates get switched off. `reference` is the group that catches a {@link} pointing at a
// class the file cannot resolve -- the four that were silently broken before this was
// configured -- and `syntax`/`html` catch malformed tags. -Werror is what makes a warning fail
// the build; without it doclint only logs and the gate is decorative.
//
// `missing` is deliberately NOT enabled yet. Measured on this branch, enabling it surfaces 282
// undocumented public/protected members across 78 files, 173 of them public. Doclint checks the
// source AST rather than only the emitted docs, so it also flags 48 private members javadoc
// would never emit. Enabling it now would fail the build on the entire pre-existing backlog
// while this change documents 9 files, and the fastest way to get a green build would be to
// delete the gate -- exactly the outcome to avoid. Re-enable as `Xdoclint:all,-quiet` once the
// backlog reaches zero.
tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:reference,syntax,html", "-quiet")
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
val requestedThreads = (findProperty("pitestThreads") as String?)?.toInt()
    ?: (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)

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

    // Baseline mode: both gates off until real numbers exist. Ratchet from below.
    //
    // PIT reports three distinct percentages with three different denominators, and
    // conflating them is how a ratchet ends up pinned to a figure PIT never produced.
    // Measured on this scope against PIT 1.30.0:
    //
    //   line coverage      223/235 = 95%  <- gated by coverageThreshold
    //   mutation coverage  221/275 = 80%  <- gated by mutationThreshold
    //   test strength      221/261 = 85%  <- gated by testStrengthThreshold
    //
    // Only test strength excludes NO_COVERAGE mutants. Mutation coverage counts all
    // 14 of them, which is why it is the right denominator for a ratchet: a score
    // that quietly ignores unexercised code hides the exact gap being measured. The
    // 85% test-strength figure is the flattering one and must not become the floor --
    // the honest floor is mutation coverage's 80%.
    mutationThreshold.set(0)
    // Line coverage, not mutation strength. Worth keeping a separate line because
    // gating it and gating mutation coverage are different decisions, and an
    // earlier comment here conflated the two.
    coverageThreshold.set(0)
    testStrengthThreshold.set(0)

    threads.set(pitestThreads)
    // There is no `maxMemoryInMc` on this plugin; per-minion heap is a plain JVM arg,
    // which is the more general lever since this is also where -XX flags would go.
    jvmArgs.set(listOf("-Xmx${pitestMaxMemoryMc}m"))

    // Must exceed the slowest covering test's normal runtime by a clear margin.
    // api/InterleaveRunnerTest drives maxTime(1ms) and maxTime(30s), so the covering
    // set contains wall-clock-sensitive tests whose kills are non-deterministic under
    // parallel load. Re-run any surprising survivor before believing it.
    // Named `timeoutConstInMillis` here, not `timeoutConstant`.
    timeoutConstInMillis.set(4000)
    timeoutFactor.set(BigDecimal("1.5"))

    // PIT's `fasterThreshold` auto-kills any mutation running fasterThreshold x the
    // baseline, which for a model checker is actively harmful: a mutant that merely
    // breaks deduplication runs exponentially slower and gets auto-"killed" on wall
    // time, inflating the score with kills no assertion produced. It is NOT settable
    // through this plugin -- the property does not exist on the 1.19.0 extension -- so
    // this relies on PIT's default (disabled) rather than setting 10.0. Re-check on every
    // plugin bump: silently acquiring the option would turn every slow mutant into a
    // free kill and inflate the baseline this whole exercise exists to measure.

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
