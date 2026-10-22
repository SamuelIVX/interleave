/** Validates the soundness of benchmark results. */
package dev.samhb.interleave.report;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.search.Trace;
import dev.samhb.interleave.search.TraceReplayer;
import java.util.*;

/**
 * Validates the soundness of benchmark results.
 * <p>
 * Performs two categories of checks:
 * <ol>
 *   <li><strong>Verdict validation</strong> (EXACT results only):
 *       Verifies that DFS verdicts match expected outcomes for all programs,
 *       and that correct (non-VIOLATION) programs produce consistent verdicts
 *       across all strategies. Bitstate results are excluded from both verdict checks because
 *       approximate storage may miss violations. Exact {@code INCOMPLETE} and
 *       {@code APPROXIMATE_PASS} rows never seed or conflict with cross-strategy agreement.
 *       For DFS expectation matching, those labels are accepted as inconclusive only for source
 *       models; legacy DFS expectation matching retains its strict behavior.
 *   <li><strong>Trace replay validation</strong> (all results):
 *       For every reported VIOLATION (including bitstate and context-bounded),
 *       requires a failing trace and registered program, then replays the recorded execution.
 *       When an invariant is supplied, the final configuration must violate it unless replay
 *       authenticates a source runtime error (whose atomic rollback may leave the invariant true).
 *       Without an invariant, replay checks execution and supplied source step evidence only;
 *       success does not establish a property violation. Replay exceptions propagate to the caller.
 *       This positive replay obligation applies to bounded and approximate reports as well.
 * </ol>
 */
public final class SoundnessAttestation {
    /** Results. */
    private final List<BenchmarkResult> results;
    /** Programs. */
    private final Map<String, BenchmarkProgram> programs;
    /** Sound. */
    private final boolean sound;
    /** Failure reason. */
    private final String failureReason;

    /**
     * Creates an attestation from benchmark results.
     *
     * @param results the benchmark results to validate
     * @param programs the benchmark programs (for expected verdicts and invariants)
     */
    public SoundnessAttestation(List<BenchmarkResult> results, List<BenchmarkProgram> programs) {
        this.results = List.copyOf(results);
        Map<String, BenchmarkProgram> programMap = new LinkedHashMap<>();
        for (BenchmarkProgram program : programs) {
            programMap.put(program.name(), program);
        }
        this.programs = Map.copyOf(programMap);
        SoundnessCheck check = checkSoundness();
        this.sound = check.sound();
        this.failureReason = check.reason();
    }

    /**
     * Checks cross-strategy verdict agreement and replays reported violation traces.
     * @return attestation result describing any verdict or replay disagreement
     */
    private SoundnessCheck checkSoundness() {
        Map<String, String> dfsVerdicts = new LinkedHashMap<>();
        Map<String, String> correctVerdicts = new LinkedHashMap<>();

        // Filter to EXACT results for verdict validation (bitstate is incomplete by design)
        List<BenchmarkResult> exactResults = results.stream()
            .filter(r -> r.storeType() == StoreType.EXACT)
            .toList();

        for (BenchmarkResult result : exactResults) {
            String key = result.bugName();
            String actualVerdict = result.verdict();
            BenchmarkProgram program = programs.get(key);

if (program != null) {
                String expectedVerdict = program.expectedVerdict();
                if ("DFS".equals(result.strategy())) {
                    if (expectedVerdict != null && !expectedVerdict.equals(actualVerdict)
                            && !(program.program().isSource() && isInconclusive(actualVerdict))) {
                        return SoundnessCheck.failed("DFS verdict mismatch for " + key +
                            ": expected " + expectedVerdict + " but got " + actualVerdict);
                    }
                    dfsVerdicts.put(key, actualVerdict);
                }

                if (expectedVerdict != null && !"VIOLATION".equals(expectedVerdict)
                    && !isInconclusive(actualVerdict)) {
                    if (correctVerdicts.containsKey(key)) {
                        if (!correctVerdicts.get(key).equals(actualVerdict)) {
                            return SoundnessCheck.failed("Verdict mismatch for correct program " + key +
                                " under " + result.strategy() + ": " + correctVerdicts.get(key) +
                                " vs " + actualVerdict);
                        }
                    } else {
                        correctVerdicts.put(key, actualVerdict);
                    }
                }
            }
        }

        // Trace replay validation: check ALL results (including bitstate and context-bounded) that
        // report violations. A violation found under a bounded search is a real schedule, so the
        // replay check must cover it rather than being skipped along with the inconclusive ones.
        for (BenchmarkResult result : results) {
            if ("VIOLATION".equals(result.verdict())) {
                Trace failingTrace = result.failingTrace().orElse(null);
                if (failingTrace == null) {
                    return SoundnessCheck.failed("Missing failing trace for " + result.bugName() +
                        " under " + result.strategy() + " (" + result.storeType() + ")");
                }

                BenchmarkProgram program = programs.get(result.bugName());
                if (program == null) {
                    return SoundnessCheck.failed("Unknown program: " + result.bugName());
                }

                Program programDef = program.program();
                TraceReplayer replayer = new TraceReplayer();
                Invariant invariant = program.invariant().orElse(null);
                Configuration replayed = replayer.replay(programDef, failingTrace, invariant);
                if (invariant != null && invariant.holds(replayed.state(), replayed)
                        && !(programDef.isSource() && failingTrace.failure() != null
                        && failingTrace.failure().kind() == dev.samhb.interleave.core.FailureEvidence.Kind.RUNTIME_ERROR)) {
                    return SoundnessCheck.failed("Replayed trace for " + result.bugName() +
                        " under " + result.strategy() + " (" + result.storeType() + ") does not violate invariant");
                }
            }
        }

        return SoundnessCheck.passed();
    }

    /**
     * Whether a verdict asserts nothing about the absence of a bug.
     *
     * <p>These must be excluded from the cross-strategy agreement loop entirely, not merely
     * "not validated". The failure mode is a disagreement between two EXACT results: peterson is
     * PASS under exhaustive DFS and INCOMPLETE under a bounded search, which would otherwise
     * report a mismatch and fail the whole attestation. Such a result neither seeds nor conflicts
     * with the agreed verdict.
     *
     * <p>They are <em>not</em> excluded from replay validation below. A bounded search that
     * reports a violation has found a real schedule, and that schedule must be genuine.
     * @param verdict reported exploration verdict
     * @return true for INCOMPLETE or APPROXIMATE_PASS
     */
    private static boolean isInconclusive(String verdict) {
        return "INCOMPLETE".equals(verdict) || "APPROXIMATE_PASS".equals(verdict);
    }

    /**
     * Returns whether the attestation passed (no soundness failures detected).
     *
     * @return true if sound, false otherwise
     */
    public boolean isSound() {
        return sound;
    }

    /**
     * Returns the failure reason if the attestation failed.
     *
     * @return the failure reason, or null if sound
     */
    public String failureReason() {
        return failureReason;
    }

    /**
     * Formats the checks performed and their limits, or the first failure diagnostic, as Markdown.
     * Success describes this attestation's checks, not a proof of whole-checker soundness.
     * Replay without an invariant establishes execution only; authenticated source runtime failures
     * need not leave a false invariant at the replayed configuration.
     *
     * @return a Markdown section with a qualified success summary or warning and failure reason
     */
    public String formatMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Soundness Attestation\n\n");

        if (sound) {
            sb.append("All checked EXACT DFS verdicts matched their supplied expectations. ");
            sb.append("Source DFS results that were INCOMPLETE or APPROXIMATE_PASS were accepted as ");
            sb.append("inconclusive, not matched verdicts; programs without expectations were not expectation-checked. ");
            sb.append("Reported VIOLATION traces (including bitstate and context-bounded) passed execution replay. ");
            sb.append("Supplied invariants failed at the replayed configuration, or source runtime failures were authenticated. ");
            sb.append("Without an invariant, successful replay does not establish a property violation. ");
            sb.append("Inconclusive results (INCOMPLETE or APPROXIMATE_PASS) are excluded ");
            sb.append("from the cross-strategy verdict agreement above, since they assert nothing about ");
            sb.append("the absence of a bug. ");
            sb.append("No soundness failures were detected by these checks.\n");
        } else {
            sb.append("WARNING: ");
            sb.append(failureReason);
            sb.append(". The model checker may not be sound.\n");
        }

        sb.append("\n");
        return sb.toString();
    }

    /** Successful attestation or diagnostic explaining a soundness disagreement. */
    private static final class SoundnessCheck {
        /** Sound. */
        private final boolean sound;
        /** Reason. */
        private final String reason;

        /**
         * Creates an isolated snapshot of soundness check from the supplied values.
         * @param sound whether every checked agreement and replay obligation passed
         * @param reason explanation of the soundness failure
         */
        private SoundnessCheck(boolean sound, String reason) {
            this.sound = sound;
            this.reason = reason;
        }

        /**
         * Creates an attestation result with no detected soundness disagreement.
         * @return successful attestation result
         */
        static SoundnessCheck passed() {
            return new SoundnessCheck(true, null);
        }

        /**
         * Creates an attestation result describing a detected disagreement.
         * @param reason explanation of the soundness failure
         * @return failed attestation result carrying the supplied reason
         */
        static SoundnessCheck failed(String reason) {
            return new SoundnessCheck(false, reason);
        }

        /**
         * Returns sound for this soundness check.
         * @return whether sound
         */
        boolean sound() { return sound; }
        /**
         * Returns reason for this soundness check.
         * @return diagnostic reason, or null for a successful attestation
         */
        String reason() { return reason; }
    }
}
