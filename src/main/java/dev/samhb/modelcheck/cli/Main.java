package dev.samhb.modelcheck.cli;

import dev.samhb.modelcheck.bugs.BenchmarkProgram;
import dev.samhb.modelcheck.bugs.BugCorpus;
import dev.samhb.modelcheck.report.BenchmarkHarness;
import dev.samhb.modelcheck.report.BenchmarkResult;
import dev.samhb.modelcheck.report.ReportWriter;

public final class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: interleave <bug-name> [--json]");
            System.out.println("Available bugs: peterson, dekker");
            System.exit(1);
        }
        
        String bugName = args[0];
        boolean json = args.length > 1 && "--json".equals(args[1]);
        
        BenchmarkProgram program = findProgram(bugName);
        if (program == null) {
            System.out.println("Unknown bug: " + bugName);
            System.out.println("Available bugs: peterson, dekker");
            System.exit(1);
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
}
