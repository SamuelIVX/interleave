/** Independent small-model oracles for forward-control-flow reduction and bound preservation. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.cb.ContextBoundedExplorer;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.search.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceReductionTest {
    /** Three independent four-position threads have 64 exact positions and a ten-position representative. */
    @Test void branchingIndependentWorkActuallyReduces() {
        var model = new SourceProgramLoader().load("""
            program reduction { shared int x = 0; shared int y = 0; shared int z = 0;
              thread 0 { if (true) { atomic { x = 1; } atomic { x = 2; } } else { atomic { x = 9; } } }
              thread 1 { if (true) { atomic { y = 1; } atomic { y = 2; } } else { atomic { y = 9; } } }
              thread 2 { if (true) { atomic { z = 1; } atomic { z = 2; } } else { atomic { z = 9; } } }
              invariant always (true);
            }
            """);
        Invariant property = model.invariant().orElseThrow();
        // Hand product of four reachable positions per thread; independently enumerated outside Java.
        assertEquals(64, new DfsExplorer().explore(model.program(), property).statesExplored());
        // One decision and two writes per thread, plus the initial configuration.
        assertEquals(10, new StaticPorExplorer().explore(model.program(), property).statesExplored());
        assertTrue(new DporExplorer().explore(model.program(), property).statesExplored() < 64,
            "DPOR must reduce the independently enumerated exhaustive state space");
    }

    /** Both normal completion and bound cutoff are possible depending on the other thread's write. */
    @Test void finalConditionDependsOnOtherThreadAndBothOutcomesSurviveReduction() {
        var model = new SourceProgramLoader().load("""
            program cutoff { shared int counter = 0;
              thread 0 { while (counter < 2) limit 1 { atomic { counter = counter + 1; } } }
              thread 1 { atomic { counter = 2; } }
            }
            """);
        Set<TraceOutcome> expected = Set.of(TraceOutcome.COMPLETED, TraceOutcome.INCOMPLETE);
        for (DfsResult result : List.of(new DfsExplorer().explore(model.program()),
                new StaticPorExplorer().explore(model.program()), new DporExplorer().explore(model.program()))) {
            assertEquals(expected, outcomes(result));
            for (Trace trace : result.traces()) new TraceReplayer().replay(model.program(), trace);
        }
    }

    /** A deterministic grid varies real control dependencies, atomic interiors and final conditions. */
    @Test void exactStrategiesAgreeOnSmallForwardPrograms() {
        for (int initial = 0; initial <= 2; initial++) for (int bound = 0; bound <= 2; bound++) {
            String source = "program grid { shared int x = " + initial + "; shared int y = 0;"
                + " thread 0 { while (x < 2) limit " + bound + " { atomic { if (y == 0) { x = x + 1; } else { x = 2; } } } }"
                + " thread 1 { if (x == 0) { atomic { y = 1; } } else { atomic { y = 2; } } } invariant always (x >= 0 && y >= 0); }";
            var model = new SourceProgramLoader().load(source);
            Invariant property = model.invariant().orElseThrow();
            Set<TraceOutcome> dfs = outcomes(new DfsExplorer().explore(model.program(), property));
            assertEquals(dfs, outcomes(new StaticPorExplorer().explore(model.program(), property)), source);
            assertEquals(dfs, outcomes(new DporExplorer().explore(model.program(), property)), source);
            DfsResult cb = new ContextBoundedExplorer().explore(model.program(), property, null, null, 12);
            assertEquals(dfs, outcomes(cb), source);
        }
    }

    /** Independent prefixes merge with different sleepers before both kinds of terminal action. */
    @Test void mergedSleepContextsPreserveIndependentTerminalWitnesses() {
        List<String> bodies = List.of("if (true) { atomic { x = 1; } }",
            "if (true) { atomic { y = 1; } }",
            "if (true) { atomic { while (true) limit 0 {} } }",
            "if (true) { atomic { z = 1 % 0; } }");
        for (int a = 0; a < 4; a++) for (int b = 0; b < 4; b++)
            for (int c = 0; c < 4; c++) for (int d = 0; d < 4; d++) {
                if (a == b || a == c || a == d || b == c || b == d || c == d) continue;
                List<Integer> order = List.of(a, b, c, d);
                StringBuilder source = new StringBuilder("program merge { shared int x = 0; shared int y = 0; shared int z = 0;");
                for (int id = 0; id < 4; id++) source.append("thread ").append(id).append(" { ").append(bodies.get(order.get(id))).append(" }");
                source.append("}");
                var model = new SourceProgramLoader().load(source.toString());
                // Neither terminal action can complete; each can be scheduled first in every permutation.
                Set<TraceOutcome> expected = Set.of(TraceOutcome.VIOLATION, TraceOutcome.INCOMPLETE);
                assertEquals(expected, outcomes(new DfsExplorer().explore(model.program())), order.toString());
                var reduced = new DporExplorer().explore(model.program());
                assertEquals(expected, outcomes(reduced), order.toString());
                for (Trace trace : reduced.traces()) new TraceReplayer().replay(model.program(), trace);
            }
    }

    /** Returns actual reached terminal classes without using implementation identity encoders. */
    private static Set<TraceOutcome> outcomes(DfsResult result) {
        return result.traces().stream().map(Trace::outcome).collect(Collectors.toSet());
    }
}
