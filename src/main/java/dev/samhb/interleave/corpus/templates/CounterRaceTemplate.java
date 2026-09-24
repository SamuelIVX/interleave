package dev.samhb.interleave.corpus.templates;

import dev.samhb.interleave.bugs.ReadCounterStep;
import dev.samhb.interleave.bugs.WriteCounterStep;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.corpus.CorpusTemplate;
import dev.samhb.interleave.corpus.GeneratorConfig;
import java.util.*;

/**
 * Curated counter-race: random mix of reads/writes per thread, bounded.
 */
public final class CounterRaceTemplate implements CorpusTemplate {
    /** @return template id */
    @Override public String id() { return "counter-race"; }
    /** @return description */
    @Override public String description() { return "Random read/write mix per thread over a single counter"; }

    /**
     * Generates a program ensuring every write follows a read for the same thread.
     *
     * @param rng random source
     * @param cfg generation config
     * @return program
     */
    @Override
    public Program generate(Random rng, GeneratorConfig cfg) {
        int threads = cfg.threadCount();
        List<ModelThread> modelThreads = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int n = 1 + rng.nextInt(cfg.maxStepsPerThread());
            List<Step> steps = new ArrayList<>(n);
            boolean hasRead = false;
            for (int i = 0; i < n; i++) {
                boolean isRead = !hasRead || rng.nextBoolean();
                steps.add(isRead ? new ReadCounterStep(t) : new WriteCounterStep(t));
                hasRead = isRead || hasRead;
                if (isRead) hasRead = true;
            }
            modelThreads.add(new ModelThread(t, steps));
        }
        CounterState initial = CounterState.of(rng.nextInt(3), threads); // small initial variation
        return new Program(initial, modelThreads);
    }
}
