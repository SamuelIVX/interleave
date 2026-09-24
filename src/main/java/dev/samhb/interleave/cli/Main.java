package dev.samhb.interleave.cli;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.corpus.CorpusEntry;
import dev.samhb.interleave.corpus.CorpusGenerator;
import dev.samhb.interleave.corpus.GeneratorConfig;
import dev.samhb.interleave.corpus.TemplateRegistry;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.report.BenchmarkHarness;
import dev.samhb.interleave.report.BenchmarkResult;
import dev.samhb.interleave.report.ReportWriter;
import dev.samhb.interleave.report.StatesExploredTable;
import dev.samhb.interleave.report.StoreType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * CLI entry point for the interleave model checker.
 * Parses command-line arguments and runs benchmarks against the concurrency bug corpus.
 */
public final class Main {
    private static final ProgramLoader LOADER = new ProgramLoader();

    /**
     * Main entry point. Parses flags, runs benchmarks, and outputs results.
     *
     * @param args command-line arguments (bug name, flags, or --file/--all)
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        if ("generate".equals(args[0])) {
            handleGenerate(Arrays.copyOfRange(args, 1, args.length));
            return;
        }

        // Parse flags
        boolean json = false;
        boolean all = false;
        String storeFilter = null;
        String strategyFilter = null;
        int bitstateSize = 1_000_003;
        int bitstateK = 4;
        BenchmarkProgram program = null;

        int i = 0;
        while (i < args.length) {
            switch (args[i]) {
                case "--json" -> {
                    json = true;
                    i++;
                }
                case "--all" -> {
                    all = true;
                    i++;
                }
                case "--store" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Error: --store requires a value (exact|bitstate)");
                        System.exit(1);
                    }
                    storeFilter = args[i + 1].toUpperCase(Locale.ROOT);
                    if (!"EXACT".equals(storeFilter) && !"BITSTATE".equals(storeFilter)) {
                        System.err.println("Error: --store must be 'exact' or 'bitstate'");
                        System.exit(1);
                    }
                    i += 2;
                }
                case "--strategy" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Error: --strategy requires a value (DFS|STATIC_POR|DPOR)");
                        System.exit(1);
                    }
                    strategyFilter = args[i + 1].toUpperCase(Locale.ROOT);
                    if (!"DFS".equals(strategyFilter) && !"STATIC_POR".equals(strategyFilter) && !"DPOR".equals(strategyFilter)) {
                        System.err.println("Error: --strategy must be 'DFS', 'STATIC_POR', or 'DPOR'");
                        System.exit(1);
                    }
                    i += 2;
                }
                case "--bitstate-size" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Error: --bitstate-size requires a positive integer");
                        System.exit(1);
                    }
                    try {
                        bitstateSize = Integer.parseInt(args[i + 1]);
                        if (bitstateSize <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        System.err.println("Error: --bitstate-size must be a positive integer");
                        System.exit(1);
                    }
                    i += 2;
                }
                case "--bitstate-k" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Error: --bitstate-k requires a positive integer");
                        System.exit(1);
                    }
                    try {
                        bitstateK = Integer.parseInt(args[i + 1]);
                        if (bitstateK <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        System.err.println("Error: --bitstate-k must be a positive integer");
                        System.exit(1);
                    }
                    i += 2;
                }
                case "--file" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Error: --file requires a path argument");
                        System.exit(1);
                    }
                    if (program != null) {
                        System.err.println("Error: specify only one bug name or --file");
                        System.exit(1);
                    }
                    try {
                        program = LOADER.loadFromFile(Paths.get(args[i + 1]));
                    } catch (RegistryException e) {
                        System.err.println("Error loading program from file: " + e.getMessage());
                        System.exit(1);
                    }
                    i += 2;
                }
                default -> {
                    if (args[i].startsWith("-")) {
                        System.err.println("Error: unknown flag '" + args[i] + "'");
                        printUsage();
                        System.exit(1);
                    }
                    if (program != null) {
                        System.err.println("Error: specify only one bug name or --file");
                        System.exit(1);
                    }
                    program = findProgram(args[i]);
                    if (program == null) {
                        System.err.println("Unknown bug: " + args[i]);
                        System.err.println("Available bugs: " + String.join(", ", BugCorpus.all().stream().map(BenchmarkProgram::name).toList()));
                        System.exit(1);
                    }
                    i++;
                }
            }
        }

        // Validate: --all is incompatible with --file and named bug
        if (all && program != null) {
            System.err.println("Error: --all is incompatible with --file or a named bug");
            System.exit(1);
        }

        // Validate: must have exactly one of --all, --file, or named bug
        if (!all && program == null) {
            System.err.println("Error: specify a bug name, --file, or --all");
            printUsage();
            System.exit(1);
        }

        // Run benchmarks with filters applied (skip unselected combinations)
        Set<StoreType> storeFilterSet = storeFilter != null ? Set.of(StoreType.valueOf(storeFilter)) : null;
        Set<String> strategyFilterSet = strategyFilter != null ? Set.of(strategyFilter) : null;
        BenchmarkHarness harness = new BenchmarkHarness(bitstateSize, bitstateK, storeFilterSet, strategyFilterSet);

        List<BenchmarkResult> allResults;
        if (all) {
            allResults = harness.runAll();
        } else {
            allResults = harness.runProgram(program);
        }

        // Generate report using all results (preserves soundness attestation and baseline data)
        ReportWriter writer = new ReportWriter(allResults);

        if (json) {
            System.out.println(writer.writeJson());
        } else {
            System.out.println(writer.writeMarkdown());
        }
    }

    /**
     * Finds a benchmark program by name in the corpus.
     *
     * @param name the program name to find
     * @return the program, or null if not found
     */
    private static BenchmarkProgram findProgram(String name) {
        for (BenchmarkProgram program : BugCorpus.all()) {
            if (program.name().equals(name)) {
                return program;
            }
        }
        return null;
    }

    private static void handleGenerate(String[] args) {
        String templateId = null;
        int count = 10;
        long seed = 42L;
        int maxSteps = 5;
        int maxStates = 10_000;
        int threadCount = 2;

        for (int i = 0; i < args.length; ) {
            switch (args[i]) {
                case "--template" -> { if (i + 1 >= args.length) { System.err.println("Error: --template requires value"); System.exit(1); } templateId = args[i + 1]; i += 2; }
                case "--count" -> { if (i + 1 >= args.length) { System.err.println("Error: --count requires value"); System.exit(1); } count = Integer.parseInt(args[i + 1]); i += 2; }
                case "--seed" -> { if (i + 1 >= args.length) { System.err.println("Error: --seed requires value"); System.exit(1); } seed = Long.parseLong(args[i + 1]); i += 2; }
                case "--max-steps" -> { if (i + 1 >= args.length) { System.err.println("Error: --max-steps requires value"); System.exit(1); } maxSteps = Integer.parseInt(args[i + 1]); i += 2; }
                case "--max-states" -> { if (i + 1 >= args.length) { System.err.println("Error: --max-states requires value"); System.exit(1); } maxStates = Integer.parseInt(args[i + 1]); i += 2; }
                case "--threads" -> { if (i + 1 >= args.length) { System.err.println("Error: --threads requires value"); System.exit(1); } threadCount = Integer.parseInt(args[i + 1]); i += 2; }
                default -> { System.err.println("Error: unknown flag '" + args[i] + "' for generate"); printGenerateUsage(); System.exit(1); }
            }
        }
        if (templateId == null) { System.err.println("Error: --template is required"); printGenerateUsage(); System.exit(1); }
        GeneratorConfig cfg;
        try {
            cfg = GeneratorConfig.builder(templateId).seed(seed).count(count).maxStepsPerThread(maxSteps).maxStates(maxStates).threadCount(threadCount).build();
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return;
        }
        try {
            TemplateRegistry.get(templateId);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return;
        }
        CorpusGenerator gen = new CorpusGenerator();
        List<CorpusEntry> entries = gen.generateEntries(cfg);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println(gson.toJson(entries));
    }

    private static void printGenerateUsage() {
        System.out.println("Usage: interleave generate --template <id> [flags]");
        System.out.println("Flags:");
        System.out.println("  --template <id>           Template id (available: " + String.join(", ", TemplateRegistry.ids()) + ")");
        System.out.println("  --count N                 Number of programs (1..10000, default 10)");
        System.out.println("  --seed N                  RNG seed (default 42)");
        System.out.println("  --max-steps N             Max steps per thread (1..20, default 5)");
        System.out.println("  --max-states N            Max states before TRUNCATED (default 10000)");
        System.out.println("  --threads N               Thread count (1..4, default 2)");
    }

    /**
     * Prints usage information and available bugs to stdout.
     */
    private static void printUsage() {
        System.out.println("Usage: interleave <bug-name> [flags]");
        System.out.println("       interleave --file <path> [flags]");
        System.out.println("       interleave --all [flags]");
        System.out.println("       interleave generate --template <id> [flags]");
        System.out.println();
        System.out.println("Flags:");
        System.out.println("  --json                    Output as JSON (default: Markdown)");
        System.out.println("  --store exact|bitstate    Filter by store type (default: both)");
        System.out.println("  --strategy DFS|STATIC_POR|DPOR  Filter by strategy (default: all)");
        System.out.println("  --bitstate-size N         Bitstate bit-array size (default: 1000003)");
        System.out.println("  --bitstate-k N            Bitstate hash function count (default: 4)");
        System.out.println("  --all                     Run entire corpus");
        System.out.println();
        System.out.println("Available bugs: " + String.join(", ", BugCorpus.all().stream().map(BenchmarkProgram::name).toList()));
    }
}
