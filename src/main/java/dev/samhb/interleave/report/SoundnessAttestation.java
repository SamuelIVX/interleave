package dev.samhb.interleave.report;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.search.Trace;
import dev.samhb.interleave.search.TraceReplayer;
import java.util.*;

public final class SoundnessAttestation {
    private final List<BenchmarkResult> results;
    private final boolean sound;
    private final String failureReason;

    public SoundnessAttestation(List<BenchmarkResult> results) {
        this.results = List.copyOf(results);
        SoundnessCheck check = checkSoundness();
        this.sound = check.sound();
        this.failureReason = check.reason();
    }

    private SoundnessCheck checkSoundness() {
        Map<String, String> verdicts = new LinkedHashMap<>();
        Map<String, Program> programs = new LinkedHashMap<>();
        
        for (BenchmarkProgram program : BugCorpus.all()) {
            programs.put(program.name(), program.program());
        }
        
        for (BenchmarkResult result : results) {
            String key = result.bugName();
            String verdict = result.verdict();
            
            if (verdicts.containsKey(key)) {
                if (!verdicts.get(key).equals(verdict)) {
                    return SoundnessCheck.failed("Verdict mismatch for " + key + 
                        ": " + verdicts.get(key) + " vs " + verdict);
                }
            } else {
                verdicts.put(key, verdict);
            }
        }
        
        for (BenchmarkResult result : results) {
            if ("VIOLATION".equals(result.verdict())) {
                Trace failingTrace = result.failingTrace().orElse(null);
                if (failingTrace == null) {
                    return SoundnessCheck.failed("Missing failing trace for " + result.bugName() + 
                        " under " + result.strategy());
                }
                
                Program program = programs.get(result.bugName());
                if (program == null) {
                    return SoundnessCheck.failed("Unknown program: " + result.bugName());
                }
                
                TraceReplayer replayer = new TraceReplayer();
                Configuration replayed = replayer.replay(program, failingTrace);
                
                Invariant invariant = result.invariant().orElse(null);
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
            sb.append("All strategies produced identical verdicts for every benchmark. ");
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
