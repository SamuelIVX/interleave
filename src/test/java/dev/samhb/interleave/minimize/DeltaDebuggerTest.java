package dev.samhb.interleave.minimize;

import dev.samhb.interleave.bugs.BrokenPeterson;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeltaDebuggerTest {

    private Program buggyProgram() {
        return BrokenPeterson.program().program();
    }

    private Invariant buggyInvariant() {
        return BrokenPeterson.program().invariant().orElseThrow();
    }

    private Trace failingTrace() {
        DfsExplorer explorer = new DfsExplorer();
        DfsResult result = explorer.explore(buggyProgram(), buggyInvariant());
        List<Trace> violations = result.traces().stream()
            .filter(t -> t.outcome() == TraceOutcome.VIOLATION)
            .toList();
        assertFalse(violations.isEmpty(), "Should find at least one VIOLATION trace");
        return violations.get(0);
    }

    @Test
    void minimize_reducesFailingTrace() {
        Trace failing = failingTrace();
        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(buggyProgram(), failing, failing.outcome());
        
        assertTrue(minimized.length() <= failing.length(),
            "Minimized trace should be <= original length");
    }
    
    @Test
    void minimize_preservesViolation() {
        Trace failing = failingTrace();
        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(buggyProgram(), failing, failing.outcome());
        
        assertNotNull(minimized);
        assertTrue(minimized.length() > 0, "Minimized trace should not be empty");
        assertEquals(TraceOutcome.VIOLATION, minimized.outcome(),
            "Minimized trace should still be a VIOLATION");
    }
    
    @Test
    void minimize_returnsSubsequence() {
        Trace failing = failingTrace();
        DeltaDebugger debugger = new DeltaDebugger();
        Trace minimized = debugger.minimize(buggyProgram(), failing, failing.outcome());
        
        List<Integer> originalThreadIds = failing.threadIds();
        List<Integer> minimizedThreadIds = minimized.threadIds();
        
        int originalIndex = 0;
        int minimizedIndex = 0;
        
        while (originalIndex < originalThreadIds.size() && minimizedIndex < minimizedThreadIds.size()) {
            if (originalThreadIds.get(originalIndex).equals(minimizedThreadIds.get(minimizedIndex))) {
                minimizedIndex++;
            }
            originalIndex++;
        }
        
        assertTrue(minimizedIndex == minimizedThreadIds.size(),
            "Minimized trace should be a subsequence of original");
    }
}
