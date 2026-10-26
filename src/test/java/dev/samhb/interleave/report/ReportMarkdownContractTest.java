/** Literal report-section composition without freezing blank separator counts. */
package dev.samhb.interleave.report;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ReportMarkdownContractTest {
    /** Independent complete attestation text; success is limited to the checks actually supplied. */
    private static final String ATTESTATION = "## Soundness Attestation\n\n"
        + "All checked EXACT DFS verdicts matched their supplied expectations. "
        + "Source DFS results that were INCOMPLETE or APPROXIMATE_PASS were accepted as "
        + "inconclusive, not matched verdicts; programs without expectations were not expectation-checked. "
        + "Reported VIOLATION traces (including bitstate and context-bounded) passed execution replay. "
        + "Supplied invariants failed at the replayed configuration, or source runtime failures were authenticated. "
        + "Without an invariant, successful replay does not establish a property violation. "
        + "Inconclusive results (INCOMPLETE or APPROXIMATE_PASS) are excluded "
        + "from the cross-strategy verdict agreement above, since they assert nothing about "
        + "the absence of a bug. "
        + "No soundness failures were detected by these checks.\n\n";

    /** Optional bitstate summaries cannot absorb the required Detailed Results heading or reorder content.
     * @param bitstate whether the carrier rows also include approximation diagnostics
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @ResourceLock(Resources.LOCALE)
    void sectionsAndRowsRetainTheirOrderWithAndWithoutBitstate(boolean bitstate) {
        Locale previous = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.ROOT);
            var exact = new BenchmarkResult("DFS", "sample", 17, 23, -31, "PASS");
            var approximate = new BenchmarkResult("DFS", "sample", 19, 29, 37, "PASS", null,
                StoreType.BITSTATE, 1.25e-8, 7, 0.125);
            List<BenchmarkResult> rows = bitstate ? List.of(exact, approximate) : List.of(exact);
            String expected = bitstate ? """
                # interleave Benchmark Report
                ## States Explored Reduction Table
                ### sample
                - DFS (exact): 17 states
                - DFS (bitstate): 19 states
                ## Bitstate Summary
                | Bug | Strategy | FPR | Bit Count | Bit Density |
                |-----|----------|-----|-----------|-------------|
                | sample | DFS | 1.25e-08 | 7 | 0.125000 |
                ## Detailed Results
                | Bug | Strategy | Store Type | States Explored | Wall Time (ms) | Heap Delta (bytes) | Verdict |
                |-----|----------|------------|-----------------|----------------|---------------------|---------|
                | sample | DFS | EXACT | 17 | 23 | -31 | PASS |
                | sample | DFS | BITSTATE | 19 | 29 | 37 | PASS |
                """ : """
                # interleave Benchmark Report
                ## States Explored Reduction Table
                ### sample
                - DFS (exact): 17 states
                ## Detailed Results
                | Bug | Strategy | Store Type | States Explored | Wall Time (ms) | Heap Delta (bytes) | Verdict |
                |-----|----------|------------|-----------------|----------------|---------------------|---------|
                | sample | DFS | EXACT | 17 | 23 | -31 | PASS |
                """;
            assertEquals(contentLines(expected + ATTESTATION),
                contentLines(new ReportWriter(rows, List.of()).writeMarkdown()));
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, previous);
        }
    }

    /** Empty reports still contain the detailed-table heading/schema before qualified attestation. */
    @Test
    void emptyReportRetainsItsRequiredSectionSkeleton() {
        String expected = """
            # interleave Benchmark Report
            ## States Explored Reduction Table
            ## Detailed Results
            | Bug | Strategy | Store Type | States Explored | Wall Time (ms) | Heap Delta (bytes) | Verdict |
            |-----|----------|------------|-----------------|----------------|---------------------|---------|
            """;
        assertEquals(contentLines(expected + ATTESTATION),
            contentLines(new ReportWriter(List.of(), List.of()).writeMarkdown()));
    }

    /** Preserves ordered content and contiguous table rows while allowing blank block separators.
     * @param markdown simple machine-generated report without embedded authored blocks
     * @return all nonblank lines, preserving headings, spaces, rows and paragraph text
     * @throws AssertionError if blank lines split consecutive table rows
     */
    private static List<String> contentLines(String markdown) {
        // shortcut: fixtures contain no authored Markdown blocks, use an AST oracle before broadening that input.
        List<String> lines = markdown.lines().toList();
        int previousContent = -1;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) continue;
            if (line.startsWith("|") && previousContent >= 0
                && lines.get(previousContent).startsWith("|")) {
                assertEquals(previousContent + 1, index, "Blank lines must not split report tables");
            }
            previousContent = index;
        }
        return lines.stream().filter(line -> !line.isBlank()).toList();
    }
}
