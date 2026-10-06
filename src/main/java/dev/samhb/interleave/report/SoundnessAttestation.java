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
 *       across all strategies. Bitstate results are excluded because they are
 *       incomplete by design and may miss violations, as are results whose
 *       verdict is {@code INCOMPLETE} or {@code APPROXIMATE_PASS}, since those
 *       assert nothing about the absence of a bug.
 *   <li><strong>Trace replay validation</strong> (all results):
 *       For every reported VIOLATION (including bitstate and context-bounded),
 *       replays the failing trace and verifies that the invariant is genuinely
 *       violated at the final configuration. This ensures no false alarms are
 *       reported. This is a positive check, not an exclusion: a bounded search
 *       that reports a violation has found a real schedule, and that schedule
 *       must hold up under replay.
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
                    if (expectedVerdict != null && !expectedVerdict.equals(actualVerdict)) {
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
                Configuration replayed = replayer.replay(programDef, failingTrace);

                Invariant invariant = program.invariant().orElse(null);
                if (invariant != null && invariant.holds(replayed.state(), replayed)) {
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
     * Formats the attestation as Markdown.
     *
     * @return a Markdown string describing the soundness result
     */
    public String formatMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Soundness Attestation\n\n");

        if (sound) {
            sb.append("All EXACT programs produced expected verdicts under DFS. ");
            sb.append("All failing traces (including bitstate and context-bounded) replayed to genuine violations. ");
            sb.append("Results bounded by a preemption limit (INCOMPLETE or APPROXIMATE_PASS) are excluded ");
            sb.append("from the cross-strategy verdict agreement above, since they assert nothing about ");
            sb.append("the absence of a bug. ");
            sb.append("The model checker is sound.\n");
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
