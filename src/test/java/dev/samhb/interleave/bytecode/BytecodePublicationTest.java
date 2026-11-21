/** Transitive publication, operand roots, allocation ordering and two-mode observable preservation. */
package dev.samhb.interleave.bytecode;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

class BytecodePublicationTest {
    /** Cycles remain local on a callee operand stack, then promote before shared escape commits.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void transitivePublicationPrecedesVisibleEscape(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            class Node{Node next;int value;}
            @Model public class Fixture{Node published;public Fixture(){}
              @Actor(id=0)public void actor(){Node n=new Node();Node child=new Node();n.next=child;child.next=n;publish(n);published=null;}
              void publish(Node n){published=n;}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        for(var mode:BytecodeScheduler.Mode.values()) {
            var scheduler=new BytecodeScheduler(program,mode);var state=program.initialState();boolean sawLocal=false,sawShared=false;
            for(int i=0;i<200 && state.executions.get(0).phase!=Phase.TERMINATED;i++) {
                var original=state.canonicalBytes();var next=scheduler.advance(state,0);assertArrayEquals(original,state.canonicalBytes());state=next.state();
                var node=state.heap.get(new Ref(0,0));if(node==null)continue;
                if(state.fixtureField("published").equals(NULL) && !sawShared){assertFalse(node.shared);sawLocal=true;}
                var child=state.heap.get(new Ref(0,1));
                if(child!=null && !sawShared && state.fixtureField("published").equals(NULL))assertFalse(child.shared);
                if(!state.fixtureField("published").equals(NULL)){assertTrue(node.shared);assertNotNull(child);assertTrue(child.shared);sawShared=true;}
                if(sawShared){assertTrue(node.shared);assertNotNull(child);assertTrue(child.shared);}
            }
            assertTrue(sawLocal);assertTrue(sawShared);assertEquals(NULL,state.fixtureField("published"));
            assertEquals(2,state.executions.get(0).allocations);assertEquals(6,state.executions.get(0).cells);
            assertTrue(state.heap.get(new Ref(0,0)).shared);assertTrue(state.heap.get(new Ref(0,1)).shared);
        }
    }

    /** Allocation sequences/charges belong to each actor even after escape and reversed scheduling.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void reversedAllocationsAndEscapeRetainCharges(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture{Object left,right;public Fixture(){}
              @Actor(id=0)public void a(){left=new Object();Object dropped=new Object();}
              @Actor(id=1)public void b(){right=new Object();}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",new BytecodeLimits(256,1,8192,32,64,256,4096,128,8192,1024));
        var scheduler=new BytecodeScheduler(program,BytecodeScheduler.Mode.BATCHED);
        for(int first:new int[]{0,1}) {
            var state=program.initialState();
            // Run actor zero only until publication, so its later cutoff cannot suppress the other allocation.
            for(int turn=0;turn<2;turn++) {
                int actor=turn==0 ? first : 1-first;
                for(int i=0;i<50;i++) {
                    state=scheduler.advance(state,actor).state();
                    if(!state.fixtureField(actor==0 ? "left" : "right").equals(NULL))break;
                }
            }
            assertEquals(new Ref(0,0),state.fixtureField("left"));assertEquals(new Ref(1,0),state.fixtureField("right"));
            assertTrue(state.heap.get(new Ref(0,0)).shared);assertTrue(state.heap.get(new Ref(1,0)).shared);
            for(int step=0;step<100 && state.executions.get(0).phase!=Phase.CUTOFF;step++) {
                var phase=state.executions.get(0).phase;
                assertNotEquals(Phase.TERMINATED,phase);assertNotEquals(Phase.FAILED,phase);
                state=scheduler.advance(state,0).state();
            }
            assertEquals(Phase.CUTOFF,state.executions.get(0).phase);
            assertEquals("ALLOCATIONS",state.executions.get(0).cutoff.reason());assertEquals(1,state.executions.get(0).allocations);
            assertEquals(1,state.executions.get(0).cells);assertEquals(1,state.executions.get(1).allocations);
            assertFalse(state.heap.containsKey(new Ref(0,1)));
        }
    }

    /** Shared arrays and crossed reference graphs preserve independent instruction outcomes in both modes.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void publicationMatchesIndependentInstructions(@TempDir Path directory) throws Exception {
        var fixtures=List.of(
            "int[] shared=new int[1];@Actor(id=0)public void a(){shared[0]++;}@Actor(id=1)public void b(){shared[0]++;}",
            "Node left,right;@Actor(id=0)public void a(){Node n=new Node();n.next=n;left=n;}@Actor(id=1)public void b(){Node n=new Node();right=n;n.next=left;}",
            "Object left,right;@Actor(id=0)public void a(){left=new Object();Object x=new Object();}@Actor(id=1)public void b(){right=new Object();}"
        );
        for(int i=0;i<fixtures.size();i++) {
            var classes=BytecodeFixtures.compile(directory.resolve("case"+i),26,"class Node{Node next;}@Model public class Fixture{public Fixture(){}"+fixtures.get(i)+"}");
            var limits=new BytecodeLimits(64,i==2 ? 1 : 128,8192,32,64,256,4096,128,8192,1024);
            var program=BytecodeLoader.load(classes,"Fixture",limits);var expected=BytecodeInstructionOracle.enumerate(program);
            assertFalse(expected.isEmpty());
            for(var mode:BytecodeScheduler.Mode.values()) {
                var result=new BytecodeExplorer(program,mode).explore(200000);assertTrue(result.traversalComplete());
                assertEquals(expected,BytecodeBatchingOracleTest.independentProjection(result),"case"+i+" "+mode);
            }
        }
    }
    /** Unknown ownership promotes a discarded parent and its actor-rooted descendant in one refresh.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void unknownParentPromotesDescendantsAtomically(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"class Node{Node next;}@Model public class Fixture{public Fixture(){}@Actor(id=0)public void actor(){}}");
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        for(boolean rooted:new boolean[]{true,false}) {
            var state=program.initialState();var parent=new Ref(0,0);var child=new Ref(0,1);
            var p=new ObjectValue("Node");p.shared=false;var c=new ObjectValue("Node");c.shared=false;
            p.fields.put(new BytecodeProgram.FieldId("Node","next","LNode;"),child);state.heap.put(parent,p);state.heap.put(child,c);
            state.frame(0).locals.put(1,rooted ? parent : child);
            var work=BytecodeSharing.refresh(state);
            assertEquals(!rooted,p.shared);assertEquals(!rooted,c.shared);assertEquals(rooted ? 0 : 2,work.promotions());
            assertEquals(0,BytecodeSharing.refresh(state).promotions());
        }
    }

    /** An implicit monitor event before NEW cannot promote a discarded graph before a failed allocation.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void implicitMonitorEntryDoesNotPublish(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"class Node{int value;}@Model public class Fixture{public Fixture(){}void discard(){new Node();}synchronized void lock(){new Object();}@Actor(id=0)public void actor(){discard();lock();}}");
        var limits=new BytecodeLimits(256,1,8192,32,64,256,4096,128,8192,1024);var program=BytecodeLoader.load(classes,"Fixture",limits);
        var expected=BytecodeInstructionOracle.enumerate(program);assertEquals(1,expected.size());var outcome=expected.iterator().next();
        assertEquals("CUTOFF",outcome.kind());assertEquals("ALLOCATIONS",outcome.cutoff().reason());assertTrue(outcome.fields().keySet().stream().noneMatch(k->k.contains("Node")));
        for(var mode:BytecodeScheduler.Mode.values()){var result=new BytecodeExplorer(program,mode).explore(10000);assertTrue(result.traversalComplete());assertEquals(expected,BytecodeBatchingOracleTest.independentProjection(result));}
    }

}
