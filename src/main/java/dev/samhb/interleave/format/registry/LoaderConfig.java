/** Configuration holder for registry dependencies. */
package dev.samhb.interleave.format.registry;

/**
 * Configuration holder for registry dependencies.
 * <p>
 * Allows injecting custom registries for testing while providing sensible defaults
 * for production use. Uses a builder pattern for fluent configuration.
 */
public final class LoaderConfig {
    /** Step registry. */
    private final StepRegistry stepRegistry;
    /** State registry. */
    private final StateRegistry stateRegistry;
    /** Invariant registry. */
    private final InvariantRegistry invariantRegistry;

    /**
     * Creates a config with default built-in registries.
     */
    public LoaderConfig() {
        this(new StepRegistry(), new StateRegistry(), new InvariantRegistry());
    }

    /**
     * Creates an isolated snapshot of loader config from the supplied values.
     * @param stepRegistry registry resolving named step factories
     * @param stateRegistry registry resolving named initial-state factories
     * @param invariantRegistry registry resolving named property factories
     */
    private LoaderConfig(StepRegistry stepRegistry, StateRegistry stateRegistry, InvariantRegistry invariantRegistry) {
        this.stepRegistry = stepRegistry;
        this.stateRegistry = stateRegistry;
        this.invariantRegistry = invariantRegistry;
    }

    /**
     * Returns step registry for this loader config.
     * @return configured step-factory registry
     */
    public StepRegistry stepRegistry() {
        return stepRegistry;
    }

    /**
     * Returns state registry for this loader config.
     * @return configured state-factory registry
     */
    public StateRegistry stateRegistry() {
        return stateRegistry;
    }

    /**
     * Returns invariant registry for this loader config.
     * @return configured invariant-factory registry
     */
    public InvariantRegistry invariantRegistry() {
        return invariantRegistry;
    }

    /**
     * Returns a loader configuration using the supplied step registry.
     * @param stepRegistry registry resolving named step factories
     * @return new configuration preserving other registries and replacing step factories
     */
    public LoaderConfig withStepRegistry(StepRegistry stepRegistry) {
        return new LoaderConfig(stepRegistry, this.stateRegistry, this.invariantRegistry);
    }

    /**
     * Returns a loader configuration using the supplied state registry.
     * @param stateRegistry registry resolving named initial-state factories
     * @return new configuration preserving other registries and replacing state factories
     */
    public LoaderConfig withStateRegistry(StateRegistry stateRegistry) {
        return new LoaderConfig(this.stepRegistry, stateRegistry, this.invariantRegistry);
    }

    /**
     * Returns a loader configuration using the supplied invariant registry.
     * @param invariantRegistry registry resolving named property factories
     * @return new configuration preserving other registries and replacing invariant factories
     */
    public LoaderConfig withInvariantRegistry(InvariantRegistry invariantRegistry) {
        return new LoaderConfig(this.stepRegistry, this.stateRegistry, invariantRegistry);
    }
}
