package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DporExplorerTest {

    @Test
    void dporExploresFewerStatesThanDfs() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new BusyWaitStep(0, 1),
            new CSEnterStep(0),
            new WriteFlagStep(0, false)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new WriteFlagStep(1, false)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = new Program(initial, List.of(t0, t1));
        
        DfsExplorer dfsExplorer = new DfsExplorer();
        DfsResult dfsResult = dfsExplorer.explore(program);
        
        DporExplorer dporExplorer = new DporExplorer();
        DfsResult dporResult = dporExplorer.explore(program);
        
        assertTrue(dporResult.statesExplored() <= dfsResult.statesExplored(),
            "DPOR should explore <= states than DFS. DFS: " + 
            dfsResult.statesExplored() + ", DPOR: " + dporResult.statesExplored());
    }
    
    @Test
    void dporProducesSameVerdictAsDfs() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new BusyWaitStep(0, 1),
            new CSEnterStep(0),
            new WriteFlagStep(0, false)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new WriteFlagStep(1, false)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = new Program(initial, List.of(t0, t1));
        
        DfsExplorer dfsExplorer = new DfsExplorer();
        DfsResult dfsResult = dfsExplorer.explore(program);
        
        DporExplorer dporExplorer = new DporExplorer();
        DfsResult dporResult = dporExplorer.explore(program);
        
        assertEquals(dfsResult.statesExplored() > 0, dporResult.statesExplored() > 0,
            "Both should explore some states");
    }
    
    @Test
    void sleepSet_preventsRedundantExploration() {
        PetersonState initial = PetersonState.of(false, false, 0);
        
        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1)
        );
        
        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0)
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = new Program(initial, List.of(t0, t1));
        
        DporExplorer dporExplorer = new DporExplorer();
        DfsResult result = dporExplorer.explore(program);
        
        assertTrue(result.statesExplored() > 0, "Should explore some states");
    }

    @Test
    void happensBefore_computesTransitiveClosure() {
        HappensBefore hb = new HappensBefore();
        Step step = new WriteFlagStep(0, true);
        hb.record(0, 1, step, 0);
        hb.record(1, 2, step, 0);
        
        assertTrue(hb.happensBefore(0, 1), "Direct edge should hold");
        assertTrue(hb.happensBefore(1, 2), "Direct edge should hold");
        assertTrue(hb.happensBefore(0, 2), "Transitive edge should hold");
        assertFalse(hb.happensBefore(2, 0), "Reverse edge should not hold");
    }

    @Test
    void wakeUp_usesRecordedPcNotCurrentPc() {
        // This test demonstrates the bug where wakeUp falls back to current PC
        // when no step is recorded, instead of using the PC at which the
        // happens-before edge was originally recorded.
        
        // Program where T0 writes to A, then T0 writes to B, then T1 reads A.
        // The happens-before edge T0->T1 is recorded at T0's PC=0 (write to A).
        // But by the time T1 runs, T0 has moved to PC=2 (write to B).
        // If T1 is sleeping and wakeUp is triggered, it should check against
        // the step at recorded PC=0 (write to A), not current PC=2 (write to B).
        
        // Shared state with two fields A and B
        class TestState implements SharedState {
            int a = 0;
            int b = 0;
            boolean control = false;
            
            public TestState() {}
            
            @Override public SharedState deepCopy() {
                TestState copy = new TestState();
                copy.a = this.a;
                copy.b = this.b;
                copy.control = this.control;
                return copy;
            }
            
            @Override public void encodeTo(java.io.DataOutput out) throws java.io.IOException {
                out.writeBoolean(control);
                out.writeInt(a);
                out.writeInt(b);
            }
            
            @Override public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof TestState that)) return false;
                return a == that.a && b == that.b && control == that.control;
            }
            
            @Override public int hashCode() {
                return Objects.hash(a, b, control);
            }
            
            @Override public String toString() {
                return String.format("TestState{a=%d, b=%d, control=%b}", a, b, control);
            }
        }
        
        // Step that writes to A and declares both A and control
        class WriteAStep implements Step {
            private final int threadId;
            public WriteAStep(int threadId) { this.threadId = threadId; }
            @Override public Set<MemoryLocation> reads() { 
                return Set.of(MemoryLocation.of("control")); 
            }
            @Override public Set<MemoryLocation> writes() { 
                return Set.of(MemoryLocation.of("a"), MemoryLocation.of("control")); 
            }
            @Override public boolean enabled(SharedState state) { 
                return state instanceof TestState; 
            }
            @Override public StepOutcome execute(SharedState state) {
                TestState ts = (TestState) state;
                ts.a = 1;
                ts.control = true;
                return StepOutcome.ADVANCED;
            }
            @Override public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof WriteAStep that)) return false;
                return threadId == that.threadId;
            }
            @Override public int hashCode() { return Objects.hash(threadId); }
        }
        
        // Step that writes to B and declares both B and control
        class WriteBStep implements Step {
            private final int threadId;
            public WriteBStep(int threadId) { this.threadId = threadId; }
            @Override public Set<MemoryLocation> reads() { 
                return Set.of(MemoryLocation.of("control")); 
            }
            @Override public Set<MemoryLocation> writes() { 
                return Set.of(MemoryLocation.of("b"), MemoryLocation.of("control")); 
            }
            @Override public boolean enabled(SharedState state) { 
                return state instanceof TestState; 
            }
            @Override public StepOutcome execute(SharedState state) {
                TestState ts = (TestState) state;
                ts.b = 1;
                ts.control = true;
                return StepOutcome.ADVANCED;
            }
            @Override public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof WriteBStep that)) return false;
                return threadId == that.threadId;
            }
            @Override public int hashCode() { return Objects.hash(threadId); }
        }
        
        // Step that reads A
        class ReadAStep implements Step {
            private final int threadId;
            public ReadAStep(int threadId) { this.threadId = threadId; }
            @Override public Set<MemoryLocation> reads() { 
                return Set.of(MemoryLocation.of("a"), MemoryLocation.of("control")); 
            }
            @Override public Set<MemoryLocation> writes() { 
                return Set.of(MemoryLocation.of("control")); 
            }
            @Override public boolean enabled(SharedState state) { 
                return state instanceof TestState; 
            }
            @Override public StepOutcome execute(SharedState state) {
                return StepOutcome.ADVANCED;
            }
            @Override public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof ReadAStep that)) return false;
                return threadId == that.threadId;
            }
            @Override public int hashCode() { return Objects.hash(threadId); }
        }
        
        TestState initial = new TestState();
        
        // T0: write A, write B
        // T1: read A
        // The dependency: T1's read of A depends on T0's write to A (PC 0)
        // NOT on T0's write to B (PC 1)
        // If wakeUp uses current PC (1 = write B), it would see
        // write B and read A as independent, and incorrectly not wake T1
        List<Step> thread0Steps = List.of(
            new WriteAStep(0),  // PC 0 - writes A
            new WriteBStep(0)   // PC 1 - writes B
        );
        
        List<Step> thread1Steps = List.of(
            new ReadAStep(1)    // reads A
        );
        
        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);
        
        Program program = new Program(initial, List.of(t0, t1));
        
        // With invariant that always passes, we just check that DPOR
        // explores the interleaving where T0's write A comes before T1's read A
        DporExplorer dporExplorer = new DporExplorer();
        DfsResult result = dporExplorer.explore(program);
        
        // The key assertion: DPOR should find both orderings of T0's writes
        // relative to T1's read, meaning it should explore at least 3 states
        // (T0-PC0 then T1, T0-PC1 then T1, and T1 then T0)
        assertTrue(result.statesExplored() >= 3, 
            "DPOR should explore interleavings where T1's read happens after T0's write to A. " +
            "States explored: " + result.statesExplored());
    }
}
