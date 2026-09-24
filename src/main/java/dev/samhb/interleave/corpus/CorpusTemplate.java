package dev.samhb.interleave.corpus;

import dev.samhb.interleave.core.Program;
import java.util.Random;

/**
 * Pure function of (rng, config) -> Program. Must not use global state so
 * seeding guarantees reproducibility (flakiness mitigation).
 */
public interface CorpusTemplate {
    String id();
    String description();
    Program generate(Random rng, GeneratorConfig config);
}
