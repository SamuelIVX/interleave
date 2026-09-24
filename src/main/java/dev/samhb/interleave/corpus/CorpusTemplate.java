package dev.samhb.interleave.corpus;

import dev.samhb.interleave.core.Program;
import java.util.Random;

/**
 * Pure function {@code (rng, config) -> Program}.
 * <p>
 * Implementations must be deterministic and side-effect free so that
 * the same seed reproduces the same program sequence (flakiness mitigation).
 */
public interface CorpusTemplate {
    /**
     * @return template id (e.g., "lost-update")
     */
    String id();

    /**
     * @return human-readable description
     */
    String description();

    /**
     * Generates a program.
     *
     * @param rng random source, seeded by caller
     * @param config generation config
     * @return program
     */
    Program generate(Random rng, GeneratorConfig config);
}
