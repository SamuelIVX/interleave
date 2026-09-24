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
    @Override public String id() { return "lost-update"; }
    @Override public String description() { return "Each thread reads counter then writes counter+1 (lost update)"; }

    @Override
    public Program generate(Random rng, GeneratorConfig cfg) {
        int threads = cfg.threadCount();
        List<ModelThread> modelThreads = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int pairs = 1 + rng.nextInt(cfg.maxStepsPerThread() / 2 + 1);
            // clamp pairs so total steps <= maxStepsPerThread
            pairs = Math.min(pairs, (cfg.maxStepsPerThread() + 1) / 2);
            if (pairs < 1) pairs = 1;
            List<Step> steps = new ArrayList<>(pairs * 2);
            for (int p = 0; p < pairs; p++) {
                steps.add(new ReadCounterStep(t));
                steps.add(new WriteCounterStep(t));
            }
            // trim if odd max
            if (steps.size() > cfg.maxStepsPerThread()) steps = steps.subList(0, cfg.maxStepsPerThread());
            modelThreads.add(new ModelThread(t, steps));
        }
        CounterState initial = CounterState.of(0, threads);
        return new Program(initial, modelThreads);
    }
}
