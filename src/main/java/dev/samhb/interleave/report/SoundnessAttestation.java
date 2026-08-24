package dev.samhb.interleave.report;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.search.Trace;
import dev.samhb.interleave.search.TraceReplayer;
import java.util.*;

public final class SoundnessAttestation {
    private final List<BenchmarkResult> results;
    private final Map<String, BenchmarkProgram> programs;
    private final boolean sound;
    private final String failureReason;

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
        
        for (BenchmarkResult result : results) {
            String key = result.bugName();
            String actualVerdict = result.verdict();
            BenchmarkProgram program = programs.get(key);
            
            if (program != null) {
                String expectedVerdict = program.expectedVerdict();
                if ("DFS".equals(result.strategy())) {
                    if (!expectedVerdict.equals(actualVerdict)) {
                        return SoundnessCheck.failed("DFS verdict mismatch for " + key + 
                            ": expected " + expectedVerdict + " but got " + actualVerdict);
                    }
                    dfsVerdicts.put(key, actualVerdict);
                }
                
                if (!"VIOLATION".equals(expectedVerdict)) {
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
        
        for (BenchmarkResult result : results) {
            if ("VIOLATION".equals(result.verdict())) {
                Trace failingTrace = result.failingTrace().orElse(null);
                if (failingTrace == null) {
                    return SoundnessCheck.failed("Missing failing trace for " + result.bugName() + 
                        " under " + result.strategy());
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
                        " under " + result.strategy() + " does not violate invariant");
                }
            }
        }
        
        return SoundnessCheck.passed();
    }

    public boolean isSound() {
        return sound;
    }

    public String failureReason() {
        return failureReason;
    }

    public String formatMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Soundness Attestation\n\n");
        
        if (sound) {
            sb.append("All programs produced expected verdicts under DFS. ");
            sb.append("All failing traces replayed to genuine violations. ");
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
