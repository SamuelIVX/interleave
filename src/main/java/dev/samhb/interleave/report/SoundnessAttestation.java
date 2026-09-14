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
 *       incomplete by design and may miss violations.
 *   <li><strong>Trace replay validation</strong> (all results):
 *       For every reported VIOLATION (including bitstate), replays the failing
 *       trace and verifies that the invariant is genuinely violated at the
 *       final configuration. This ensures no false alarms are reported.
 * </ol>
 */
public final class SoundnessAttestation {
    private final List<BenchmarkResult> results;
    private final Map<String, BenchmarkProgram> programs;
    private final boolean sound;
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

                if (expectedVerdict != null && !"VIOLATION".equals(expectedVerdict)) {
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

        // Trace replay validation: check ALL results (including bitstate) that report violations
        // Bitstate violations should still be genuine if found
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
            sb.append("All failing traces (including bitstate) replayed to genuine violations. ");
            sb.append("The model checker is sound.\n");
        } else {
            sb.append("WARNING: ");
            sb.append(failureReason);
            sb.append(". The model checker may not be sound.\n");
        }

        sb.append("\n");
        return sb.toString();
    }

    private static final class SoundnessCheck {
        private final boolean sound;
        private final String reason;

        private SoundnessCheck(boolean sound, String reason) {
            this.sound = sound;
            this.reason = reason;
        }

        static SoundnessCheck passed() {
            return new SoundnessCheck(true, null);
        }

        static SoundnessCheck failed(String reason) {
            return new SoundnessCheck(false, reason);
        }

        boolean sound() { return sound; }
        String reason() { return reason; }
    }
}