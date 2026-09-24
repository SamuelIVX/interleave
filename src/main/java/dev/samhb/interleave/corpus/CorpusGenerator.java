package dev.samhb.interleave.corpus;

import dev.samhb.interleave.bugs.WriteCounterStep;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.search.TraceOutcome;
import java.util.*;

/**
 * Seeded, bounded generator that produces programs via curated templates
 * and classifies them with a budget-aware exact DFS oracle.
 * <p>
 * Mitigates negatives: seeded determinism (flakiness), count/maxSteps/
 * threadCount/maxStates bounds (explosion), exact oracle (oracle problem),
 * curated registry (realism).
 */
public final class CorpusGenerator {

    /**
     * Generates corpus results with oracle verdicts.
     *
     * @param config generation config (validated)
     * @return unmodifiable list of results
     */
    public List<CorpusResult> generate(GeneratorConfig config) {
        CorpusTemplate template = TemplateRegistry.get(config.templateId());
        Random rng = new Random(config.seed());
        List<CorpusResult> out = new ArrayList<>(config.count());
        for (int i = 0; i < config.count(); i++) {
            Program program = template.generate(rng, config);
            for (var t : program.threads()) {
                if (t.steps().size() > config.maxStepsPerThread()) {
                    throw new IllegalStateException("Template violated maxStepsPerThread");
                }
            }
            Invariant invariant = invariantFor(template.id(), program);
            DfsExplorer explorer = new DfsExplorer();
            var res = explorer.explore(program, invariant, null, null, config.maxStates());
            boolean truncated = res.statesExplored() >= config.maxStates();
            String verdict;
            if (truncated) {
                verdict = "TRUNCATED";
            } else {
                boolean violation = res.traces().stream()
                    .anyMatch(tr -> tr.outcome() == TraceOutcome.VIOLATION || tr.outcome() == TraceOutcome.DEADLOCK);
                verdict = violation ? "VIOLATION" : "SAFE";
            }
            out.add(new CorpusResult(program, verdict, res.statesExplored(), truncated));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Generates entries with persisted metadata and program representation.
     *
     * @param config config
     * @return entries
     */
    public List<CorpusEntry> generateEntries(GeneratorConfig config) {
        List<CorpusResult> results = generate(config);
        List<CorpusEntry> entries = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++) {
            entries.add(new CorpusEntry(config.templateId(), config.seed(), i, results.get(i)));
        }
        return entries;
    }

    /**
     * Returns invariant for templates that have a known expected outcome.
     * Currently only lost-update has a precise invariant: final counter must
     * equal initial + number of writes, otherwise a lost update occurred.
     */
    private Invariant invariantFor(String templateId, Program program) {
        if (!"lost-update".equals(templateId)) return null;
        CounterState initial = (CounterState) program.initialConfiguration().state().deepCopy();
        int initialCounter = initial.counter();
        long writes = program.threads().stream()
            .flatMap(t -> t.steps().stream())
            .filter(s -> s instanceof WriteCounterStep)
            .count();
        int expected = initialCounter + (int) writes;
        return (state, cfg) -> {
            if (!cfg.allTerminated()) return true;
            CounterState cs = (CounterState) state;
            return cs.counter() == expected;
        };
    }
}
