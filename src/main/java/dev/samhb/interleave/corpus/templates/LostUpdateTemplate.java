package dev.samhb.interleave.corpus.templates;

import dev.samhb.interleave.bugs.ReadCounterStep;
import dev.samhb.interleave.bugs.WriteCounterStep;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.corpus.CorpusTemplate;
import dev.samhb.interleave.corpus.GeneratorConfig;
import java.util.*;

/**
 * Curated lost-update pattern: each thread does read then write.
 * Varies steps per thread up to maxStepsPerThread (pairs of read/write).
 */
public final class LostUpdateTemplate implements CorpusTemplate {
    /** @return template id */
    @Override public String id() { return "lost-update"; }
    /** @return description */
    @Override public String description() { return "Each thread reads counter then writes counter+1 (lost update)"; }

    /**
     * Generates complete read/write pairs; requires {@code maxStepsPerThread >= 2}.
     */
    @Override
    public Program generate(Random rng, GeneratorConfig cfg) {
        if (cfg.maxStepsPerThread() < 2) {
            throw new IllegalArgumentException("lost-update requires maxStepsPerThread >= 2, got " + cfg.maxStepsPerThread());
        }
        int threads = cfg.threadCount();
        List<ModelThread> modelThreads = new ArrayList<>();
        int maxPairs = cfg.maxStepsPerThread() / 2;
        for (int t = 0; t < threads; t++) {
            int pairs = 1 + rng.nextInt(maxPairs);
            List<Step> steps = new ArrayList<>(pairs * 2);
            for (int p = 0; p < pairs; p++) {
                steps.add(new ReadCounterStep(t));
                steps.add(new WriteCounterStep(t));
            }
            modelThreads.add(new ModelThread(t, steps));
        }
        CounterState initial = CounterState.of(0, threads);
        return new Program(initial, modelThreads);
    }
}
