package dev.samhb.interleave.corpus;

import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.TraceOutcome;
import java.util.*;

/**
 * Seeded, bounded generator. Addresses negatives:
 * - seed ensures reproducibility (flakiness)
 * - count/maxSteps/threadCount/maxStates bound explosion
 * - exact DfsExplorer as oracle fixes oracle problem
 * - only curated templates fixes realism
 */
public final class CorpusGenerator {

    public List<CorpusResult> generate(GeneratorConfig config) {
        CorpusTemplate template = TemplateRegistry.get(config.templateId());
        Random rng = new Random(config.seed());
        List<CorpusResult> out = new ArrayList<>(config.count());
        for (int i = 0; i < config.count(); i++) {
            Program program = template.generate(rng, config);
            // explosion control: validate steps bounded
            for (var t : program.threads()) {
                if (t.steps().size() > config.maxStepsPerThread()) {
                    throw new IllegalStateException("Template violated maxStepsPerThread");
                }
            }
            DfsExplorer explorer = new DfsExplorer();
            var res = explorer.explore(program);
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

    public List<CorpusEntry> generateEntries(GeneratorConfig config) {
        List<CorpusResult> results = generate(config);
        List<CorpusEntry> entries = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++) {
            entries.add(new CorpusEntry(config.templateId(), config.seed(), i, results.get(i)));
        }
        return entries;
    }
}
