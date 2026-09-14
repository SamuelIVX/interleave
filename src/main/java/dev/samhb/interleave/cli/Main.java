package dev.samhb.interleave.cli;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.report.BenchmarkHarness;
import dev.samhb.interleave.report.BenchmarkResult;
import dev.samhb.interleave.report.ReportWriter;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class Main {
    private static final ProgramLoader LOADER = new ProgramLoader();

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        boolean json = false;
        BenchmarkProgram program = null;

        if ("--file".equals(args[0])) {
            if (args.length < 2) {
                System.err.println("Error: --file requires a path argument");
                printUsage();
                System.exit(1);
            }
            try {
                program = LOADER.loadFromFile(Paths.get(args[1]));
            } catch (RegistryException e) {
                System.err.println("Error loading program from file: " + e.getMessage());
                System.exit(1);
                return;
            }
            // Check for --json in remaining args
            json = args.length > 2 && "--json".equals(args[2]);
        } else {
            String bugName = args[0];
            json = args.length > 1 && "--json".equals(args[1]);

            program = findProgram(bugName);
            if (program == null) {
                System.err.println("Unknown bug: " + bugName);
                System.err.println("Available bugs: " + String.join(", ", BugCorpus.all().stream().map(BenchmarkProgram::name).toList()));
                System.exit(1);
            }
        }

        BenchmarkHarness harness = new BenchmarkHarness();
        java.util.List<BenchmarkResult> results = harness.runProgram(program);

        ReportWriter writer = new ReportWriter(results);

        if (json) {
            System.out.println(writer.writeJson());
        } else {
            System.out.println(writer.writeMarkdown());
        }
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
        System.out.println("Usage: interleave <bug-name> [--json]");
        System.out.println("       interleave --file <path> [--json]");
        System.out.println("Available bugs: " + String.join(", ", BugCorpus.all().stream().map(BenchmarkProgram::name).toList()));
    }
}