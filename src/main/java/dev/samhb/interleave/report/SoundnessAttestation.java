package dev.samhb.interleave.report;

import java.util.*;

public final class SoundnessAttestation {
    private final List<BenchmarkResult> results;
    private final boolean sound;

    public SoundnessAttestation(List<BenchmarkResult> results) {
        this.results = List.copyOf(results);
        this.sound = checkSoundness();
    }

    private boolean checkSoundness() {
        Map<String, String> verdicts = new LinkedHashMap<>();
        
        for (BenchmarkResult result : results) {
            String key = result.bugName();
            String verdict = result.verdict();
            
            if (verdicts.containsKey(key)) {
                if (!verdicts.get(key).equals(verdict)) {
                    return false;
                }
            } else {
                verdicts.put(key, verdict);
            }
        }
        
        return true;
    }

    public boolean isSound() {
        return sound;
    }

    public String formatMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Soundness Attestation\n\n");
        
        if (sound) {
            sb.append("All strategies produced identical verdicts for every benchmark. ");
            sb.append("The model checker is sound.\n");
        } else {
            sb.append("WARNING: Strategies produced different verdicts. ");
            sb.append("The model checker may not be sound.\n");
        }
        
        sb.append("\n");
        return sb.toString();
    }
}
