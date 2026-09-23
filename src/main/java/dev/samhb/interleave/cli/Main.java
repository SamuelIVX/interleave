package dev.samhb.interleave.cli;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.report.BenchmarkHarness;
import dev.samhb.interleave.report.BenchmarkResult;
import dev.samhb.interleave.report.ReportWriter;
import dev.samhb.interleave.report.StatesExploredTable;
import dev.samhb.interleave.report.StoreType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class Main {
    private static final ProgramLoader LOADER = new ProgramLoader();

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
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
                    storeFilter = args[i + 1].toUpperCase();
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
                    strategyFilter = args[i + 1].toUpperCase();
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

        BenchmarkHarness harness = new BenchmarkHarness(bitstateSize, bitstateK);
        List<BenchmarkResult> results;

        if (all) {
            results = harness.runAll();
        } else {
            results = harness.runProgram(program);
        }

        // Apply filters
        results = filterResults(results, storeFilter, strategyFilter);

        ReportWriter writer = new ReportWriter(results);

        if (json) {
            System.out.println(writer.writeJson());
        } else {
            System.out.println(writer.writeMarkdown());
        }
    }

    private static List<BenchmarkResult> filterResults(List<BenchmarkResult> results,
                                                         String storeFilter, String strategyFilter) {
        List<BenchmarkResult> filtered = new ArrayList<>();
        for (BenchmarkResult r : results) {
            if (storeFilter != null && !r.storeType().name().equals(storeFilter)) continue;
            if (strategyFilter != null && !r.strategy().equals(strategyFilter)) continue;
            filtered.add(r);
        }
        return filtered;
    }

    private static BenchmarkProgram findProgram(String name) {
        for (BenchmarkProgram program : BugCorpus.all()) {
            if (program.name().equals(name)) {
                return program;
            }
        }
        return null;
    }

    private static void printUsage() {
        System.out.println("Usage: interleave <bug-name> [flags]");
        System.out.println("       interleave --file <path> [flags]");
        System.out.println("       interleave --all [flags]");
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
