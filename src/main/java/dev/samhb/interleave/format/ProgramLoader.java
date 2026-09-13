package dev.samhb.interleave.format;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.core.ModelThread;
import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.core.Step;
import dev.samhb.interleave.format.model.ProgramDefinition;
import dev.samhb.interleave.format.model.ThreadDefinition;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.format.registry.LoaderConfig;
import dev.samhb.interleave.format.registry.StepRegistry;
import dev.samhb.interleave.format.registry.StateRegistry;
import dev.samhb.interleave.format.registry.InvariantRegistry;
import dev.samhb.interleave.format.registry.JsonHelper;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads a {@link BenchmarkProgram} from a JSON program definition.
 * <p>
 * Supports loading from a JSON string, a classpath resource, or a file system path.
 * Uses {@link com.google.gson.Gson} for JSON parsing and {@link dev.samhb.interleave.format.registry}
 * registries to construct the Java objects.
 */
public final class ProgramLoader {
    private final Gson gson;
    private final StepRegistry stepRegistry;
    private final StateRegistry stateRegistry;
    private final InvariantRegistry invariantRegistry;

    /**
     * Creates a loader with default registries.
     */
    public ProgramLoader() {
        this(new LoaderConfig());
    }

    /**
     * Creates a loader with custom registry configuration.
     *
     * @param config the registry configuration
     */
    public ProgramLoader(LoaderConfig config) {
        this.gson = new Gson();
        this.stepRegistry = config.stepRegistry();
        this.stateRegistry = config.stateRegistry();
        this.invariantRegistry = config.invariantRegistry();
    }

    /**
     * Loads a program from a JSON string.
     *
     * @param jsonContent the JSON string
     * @return the loaded {@link BenchmarkProgram}
     * @throws RegistryException if the JSON is malformed or invalid
     */
    public BenchmarkProgram load(String jsonContent) {
        try {
            ProgramDefinition def = gson.fromJson(jsonContent, ProgramDefinition.class);
            return load(def);
        } catch (JsonSyntaxException e) {
            throw new RegistryException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Loads a program from a classpath resource.
     *
     * @param resourcePath the classpath resource path (e.g., "programs/lost-update.json")
     * @return the loaded {@link BenchmarkProgram}
     * @throws RegistryException if the resource is not found or invalid
     */
    public BenchmarkProgram loadFromResource(String resourcePath) {
        String json = readResource(resourcePath);
        return load(json);
    }

    /**
     * Loads a program from a file system path.
     *
     * @param path the file path
     * @return the loaded {@link BenchmarkProgram}
     * @throws RegistryException if the file is not found, cannot be read, or is invalid
     */
    public BenchmarkProgram loadFromFile(Path path) {
        try {
            String json = Files.readString(path);
            return load(json);
        } catch (IOException e) {
            throw new RegistryException("Failed to read file: " + path, e);
        }
    }

    private String readResource(String path) {
        var is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (is == null) {
            throw new RegistryException("Resource not found: " + path);
        }
        try {
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RegistryException("Failed to read resource: " + path, e);
        }
    }

    private BenchmarkProgram load(ProgramDefinition def) {
        // Validate top-level fields
        if (def.name() == null || def.name().isBlank()) {
            throw new RegistryException("Program name is required");
        }
        if (def.state() == null) {
            throw new RegistryException("State definition is required");
        }
        if (def.threads() == null || def.threads().isEmpty()) {
            throw new RegistryException("At least one thread is required");
        }

        // Validate thread IDs are sequential 0..N-1
        List<ThreadDefinition> threadDefs = def.threads();
        for (int i = 0; i < threadDefs.size(); i++) {
            if (threadDefs.get(i).id() != i) {
                throw new RegistryException(
                    "Thread ID at index " + i + " must be " + i + ", got " + threadDefs.get(i).id()
                );
            }
            if (threadDefs.get(i).steps() == null || threadDefs.get(i).steps().isEmpty()) {
                throw new RegistryException("Thread " + i + " must have at least one step");
            }
        }

        // Load state
        String stateType = def.state().get("type").getAsString();
        SharedState initialState = stateRegistry.create(def.state());

        // Validate step compatibility with state type
        for (ThreadDefinition threadDef : threadDefs) {
            for (com.google.gson.JsonObject stepJson : threadDef.steps()) {
                String stepType = stepJson.get("type").getAsString();
                stepRegistry.validateCompatibility(stepType, stateType);
            }
        }

        // Validate "other" parameter for steps that use it
        int threadCount = threadDefs.size();
        for (ThreadDefinition threadDef : threadDefs) {
            for (com.google.gson.JsonObject stepJson : threadDef.steps()) {
                if (stepJson.has("other")) {
                    int other = stepJson.get("other").getAsInt();
                    if (other < 0 || other >= threadCount) {
                        throw new RegistryException(
                            "Parameter 'other' for step type '" + stepJson.get("type").getAsString() + 
                            "' is " + other + " but must be in range [0, " + (threadCount - 1) + "] " +
                            "(thread " + threadDef.id() + ")"
                        );
                    }
                }
            }
        }

        // Build threads
        List<ModelThread> threads = new ArrayList<>();
        for (int i = 0; i < threadDefs.size(); i++) {
            ThreadDefinition threadDef = threadDefs.get(i);
            List<Step> steps = new ArrayList<>();
            for (com.google.gson.JsonObject stepJson : threadDef.steps()) {
                Step step = stepRegistry.create(stepJson, i);
                steps.add(step);
            }
            threads.add(new ModelThread(i, steps));
        }

        // Load invariant if present
        Invariant invariant = null;
        if (def.invariant() != null) {
            String invariantType = def.invariant().get("type").getAsString();
            invariantRegistry.validateCompatibility(def.invariant().get("type").getAsString(), def.state().get("type").getAsString());
            invariant = invariantRegistry.create(def.invariant());
        }

        // Expected verdict (optional)
        String expectedVerdict = def.expectedVerdict();
        if (expectedVerdict != null) {
            String v = expectedVerdict.toUpperCase();
            if (!v.equals("PASS") && !v.equals("VIOLATION") && !v.equals("DEADLOCK")) {
                throw new RegistryException(
                    "Invalid expected_verdict '" + expectedVerdict + "'. Must be PASS, VIOLATION, or DEADLOCK."
                );
            }
            expectedVerdict = v;
        }

        // Construct Program and BenchmarkProgram
        Program program = new Program(initialState, threads);
        if (invariant != null && expectedVerdict != null) {
            return new BenchmarkProgram(def.name(), program, expectedVerdict, invariant);
        } else if (expectedVerdict != null) {
            return new BenchmarkProgram(def.name(), program, expectedVerdict);
        } else if (invariant != null) {
            return new BenchmarkProgram(def.name(), program, null, invariant);
        } else {
            return new BenchmarkProgram(def.name(), program);
        }
    }
}