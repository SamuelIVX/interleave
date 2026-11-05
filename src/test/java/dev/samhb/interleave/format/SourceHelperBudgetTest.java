/** Independent helper declaration, cached-expansion and aggregate resource boundaries. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.search.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceHelperBudgetTest {
    /** Sixteen definitions are legal; declaration seventeen is rejected before expansion. */
    @Test void helperDeclarationLimitIsInclusive() {
        String definitions = "";
        for (int i = 0; i < 16; i++) definitions += "helper h" + i + " {} ";
        assertNotNull(load(definitions + "thread 0 { call h15; }"));
        rejected(definitions + "helper extra {} thread 0 {}", "At most 16 helper declarations");
    }

    /** An individual definition is checked even if it has no caller.
     * @param privateBody whether the repeated work is inside one private atomic action
     */
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void helperAndPrivateInstructionLimitsAccept64Reject65(boolean privateBody) {
        String unit = privateBody ? "counter = counter + 1;" : "atomic { counter = counter + 1; }";
        String prefix = privateBody ? "atomic { " : "";
        String suffix = privateBody ? " }" : "";
        String accepted = "helper run { " + prefix + "repeat 64 { " + unit + " }" + suffix + " } thread 0 { call run; }";
        BenchmarkProgram model = load(accepted);
        assertEquals(privateBody ? 1 : 64, model.program().threads().getFirst().steps().size());
        Trace trace = new DfsExplorer().explore(model.program()).traces().getFirst();
        assertEquals(privateBody ? 1 : 64, trace.length());
        assertEquals(64, ((dev.samhb.interleave.format.dsl.DynamicState)
            new TraceReplayer().replay(model.program(), trace, (s, c) -> true).state()).getInt("counter"));
        rejected("helper run { " + prefix + "repeat 65 { " + unit + " }" + suffix + " } thread 0 {}",
            privateBody ? "Atomic internal instructions exceed 64" : "Expanded helper instructions exceed 64");
    }

    /** Called fragments consume their full action cost, with no extra call instruction. */
    @Test void aggregateCallsAccept64AndReject65Actions() {
        String definitions = "helper batch { repeat 32 { atomic { counter = counter + 1; } } }"
            + " helper one { atomic { counter = counter + 1; } }";
        var model = load(definitions + "thread 0 { call batch; call batch; }");
        assertEquals(64, model.program().threads().getFirst().steps().size());
        assertEquals(65, new DfsExplorer().explore(model.program()).statesExplored());
        rejected(definitions + "thread 0 { call batch; call batch; call one; }", "Expanded thread instructions exceed 64");
    }

    /** Acyclic composition doubles cached costs; an over-budget definition is rejected before copying. */
    @Test void composedHelperCostsCannotHideMultiplication() {
        String definitions = "helper h0 { atomic {} } ";
        for (int i = 1; i < 7; i++) definitions += "helper h" + i + " { call h" + (i - 1) + "; call h" + (i - 1) + "; } ";
        var model = load(definitions + "thread 0 { call h6; }");
        assertEquals(64, model.program().threads().getFirst().steps().size());
        rejected(definitions + "helper h7 { call h6; call h6; } thread 0 {}", "Expanded helper instructions exceed 64");
    }

    /** Zero-work DAGs and int-max repetitions contribute zero actions without expanding a call tree. */
    @Test void zeroWorkCompositionSkipsHugeRepetition() {
        String definitions = "helper h0 {} ";
        for (int i = 1; i < 16; i++) definitions += "helper h" + i + " { call h" + (i - 1) + "; call h" + (i - 1) + "; } ";
        for (String invocation : List.of("call h15;", "repeat 2147483647 { call h15; }")) {
            var model = load(definitions + "thread 0 { " + invocation + " }");
            assertEquals(0, model.program().threads().getFirst().steps().size());
            assertEquals(0, new DfsExplorer().explore(model.program()).traces().getFirst().length());
        }
        rejected("helper invalid { repeat 0 { atomic { unknown = 1; } } } thread 0 {}", "Unknown");
    }

    /** Definitions validate private limits even inside a dead helper branch. */
    @Test void unusedZeroRepeatStillValidatesHelperAtomics() {
        rejected("helper invalid { repeat 0 { atomic { repeat 65 { counter = counter + 1; } } } } thread 0 {}",
            "Atomic internal instructions exceed 64");
    }

    /** Each caller contributes 2,500 expression nodes; a one-node property crosses the global cap. */
    @Test void everyOwnerAndCallContributesToGlobalExpressionLimit() {
        String assignment = "counter = " + sum(1250) + ";";
        String body = "helper assign { atomic { " + assignment + " } } thread 0 { call assign; } thread 1 { call assign; }";
        assertNotNull(load(body));
        rejected(body + " invariant final (true);", "Expanded expression nodes exceed 5000");
    }

    /** Two 2,500-node assignments fit a definition; one condition makes 5,001 before any call. */
    @Test void individualHelperExpressionLimitIsInclusive() {
        String assignment = "counter = " + sum(1250) + ";";
        String atomic = "atomic { " + assignment + assignment + " }";
        assertNotNull(load("helper assign { " + atomic + " } thread 0 {}"));
        rejected("helper assign { if (true) { " + atomic + " } } thread 0 {}", "Expanded helper expression nodes exceed 5000");
    }

    /** Caller labels stay bounded even at the maximum composed definition depth. */
    @Test void deeplyComposedLabelsKeepTheExistingCap() {
        var names = new ArrayList<String>();
        for (int i = 0; i < 16; i++) names.add("h" + String.format(Locale.ROOT, "%02d", i) + "a".repeat(61));
        String definitions = "helper " + names.getFirst() + " { atomic {} } ";
        for (int i = 1; i < 16; i++) definitions += "helper " + names.get(i) + " { call " + names.get(i - 1) + "; } ";
        var model = load(definitions + "thread 0 { call " + names.getLast() + "; }");
        Trace trace = new DfsExplorer().explore(model.program()).traces().getFirst();
        String label = trace.evidence().executedSteps().getFirst().label();
        assertEquals(257, label.length());
        assertTrue(label.startsWith("call " + names.getLast() + " at "));
        assertTrue(label.endsWith("…"));
        new TraceReplayer().replay(model.program(), trace, (s, c) -> true);
    }

    /** Builds balanced integer input; 1,250 leaves have 2,499 nodes, plus one target gives 2,500.
     * @param leaves positive number of literal leaves
     * @return balanced source expression, with no production counting helper used as an oracle
     */
    private static String sum(int leaves) {
        if (leaves == 1) return "1";
        int left = leaves / 2;
        return "(" + sum(left) + "+" + sum(leaves - left) + ")";
    }

    /** Loads a model through the bounded public source entry point.
     * @param body helper/thread/property source after one shared declaration
     * @return validated model
     */
    private static BenchmarkProgram load(String body) {
        return new SourceProgramLoader().load("program helper_budget { shared int counter = 0; " + body + " }");
    }

    /** Requires deliberate source rejection rather than an incidental exception or resource error.
     * @param body invalid source body
     * @param message expected contract-specific diagnostic text
     */
    private static void rejected(String body, String message) {
        var error = assertThrows(RegistryException.class, () -> load(body));
        assertTrue(error.getMessage().contains(message), error.getMessage());
    }
}
