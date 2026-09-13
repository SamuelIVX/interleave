package dev.samhb.interleave.format.registry;

/**
 * Configuration holder for registry dependencies.
 * <p>
 * Allows injecting custom registries for testing while providing sensible defaults
 * for production use. Uses a builder pattern for fluent configuration.
 */
public final class LoaderConfig {
    private final StepRegistry stepRegistry;
    private final StateRegistry stateRegistry;
    private final InvariantRegistry invariantRegistry;

    /**
     * Creates a config with default built-in registries.
     */
    public LoaderConfig() {
        this(new StepRegistry(), new StateRegistry(), new InvariantRegistry());
    }

    private LoaderConfig(StepRegistry stepRegistry, StateRegistry stateRegistry, InvariantRegistry invariantRegistry) {
        this.stepRegistry = stepRegistry;
        this.stateRegistry = stateRegistry;
        this.invariantRegistry = invariantRegistry;
    }

    public StepRegistry stepRegistry() {
        return stepRegistry;
    }

    public StateRegistry stateRegistry() {
        return stateRegistry;
    }

    public InvariantRegistry invariantRegistry() {
        return invariantRegistry;
    }

    public LoaderConfig withStepRegistry(StepRegistry stepRegistry) {
        return new LoaderConfig(stepRegistry, this.stateRegistry, this.invariantRegistry);
    }

    public LoaderConfig withStateRegistry(StateRegistry stateRegistry) {
        return new LoaderConfig(this.stepRegistry, stateRegistry, this.invariantRegistry);
    }

    public LoaderConfig withInvariantRegistry(InvariantRegistry invariantRegistry) {
        return new LoaderConfig(this.stepRegistry, this.stateRegistry, invariantRegistry);
    }
}