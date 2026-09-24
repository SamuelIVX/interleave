package dev.samhb.interleave.corpus;

/**
 * Immutable configuration for corpus generation.
 * <p>
 * Validates bounds to control explosion and ensure reproducibility via seed.
 * Use {@link #builder(String)} to construct.
 */
public final class GeneratorConfig {
    private final String templateId;
    private final long seed;
    private final int count;
    private final int maxStepsPerThread;
    private final int threadCount;
    private final int maxStates;

    private GeneratorConfig(Builder b) {
        this.templateId = b.templateId;
        this.seed = b.seed;
        this.count = b.count;
        this.maxStepsPerThread = b.maxStepsPerThread;
        this.threadCount = b.threadCount;
        this.maxStates = b.maxStates;
    }

    /** @return template id */
    public String templateId() { return templateId; }
    /** @return RNG seed */
    public long seed() { return seed; }
    /** @return number of programs to generate */
    public int count() { return count; }
    /** @return max steps per thread */
    public int maxStepsPerThread() { return maxStepsPerThread; }
    /** @return thread count */
    public int threadCount() { return threadCount; }
    /** @return max states before truncation */
    public int maxStates() { return maxStates; }

    /**
     * Creates a builder for the given template.
     *
     * @param templateId template id, must be non-blank
     * @return builder
     */
    public static Builder builder(String templateId) {
        return new Builder(templateId);
    }

    /** Builder for {@link GeneratorConfig}. */
    public static final class Builder {
        private final String templateId;
        private long seed = 42L;
        private int count = 10;
        private int maxStepsPerThread = 5;
        private int threadCount = 2;
        private int maxStates = 10_000;

        /**
         * Creates builder.
         *
         * @param templateId template id
         */
        public Builder(String templateId) {
            if (templateId == null || templateId.isBlank()) throw new IllegalArgumentException("templateId must not be blank");
            this.templateId = templateId;
        }

        /** @param seed RNG seed @return this */
        public Builder seed(long seed) { this.seed = seed; return this; }
        /** @param count number of programs 1..10000 @return this */
        public Builder count(int count) { this.count = count; return this; }
        /** @param v max steps per thread 1..20 @return this */
        public Builder maxStepsPerThread(int v) { this.maxStepsPerThread = v; return this; }
        /** @param v thread count 1..4 @return this */
        public Builder threadCount(int v) { this.threadCount = v; return this; }
        /** @param v max states 1..1000000 @return this */
        public Builder maxStates(int v) { this.maxStates = v; return this; }

        /**
         * Builds validated config.
         *
         * @return config
         * @throws IllegalArgumentException if bounds violated
         */
        public GeneratorConfig build() {
            if (count < 1 || count > 10_000) throw new IllegalArgumentException("count must be in [1, 10000]");
            if (maxStepsPerThread < 1 || maxStepsPerThread > 20) throw new IllegalArgumentException("maxStepsPerThread must be in [1, 20]");
            if (threadCount < 1 || threadCount > 4) throw new IllegalArgumentException("threadCount must be in [1, 4]");
            if (maxStates < 1 || maxStates > 1_000_000) throw new IllegalArgumentException("maxStates must be in [1, 1000000]");
            return new GeneratorConfig(this);
        }
    }
}
