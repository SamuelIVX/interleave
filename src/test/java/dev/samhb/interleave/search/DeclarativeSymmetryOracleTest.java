/** Independent finite transition graphs and explicit owner permutations for declarative symmetry. */
package dev.samhb.interleave.search;

import com.google.gson.Gson;
import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.format.dsl.SourceSymmetry;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Expected graph states never use production group discovery, canonicalization or key encoding. */
class DeclarativeSymmetryOracleTest {
    /** Whole finite graphs agree with independent operational rules and explicit permutation classes.
     * @param mode reference transition family
     * @param owners bounded owner count
     * @param concrete literal concrete-state count
     * @param quotient literal orbit count
     */
    @ParameterizedTest @CsvSource({"counter,4,16,5","semaphore,2,8,5","split,2,13,8"})
    void finiteGraphsMatchIndependentReference(String mode,int owners,int concrete,int quotient) {
        var steps=switch(mode) {
            case "counter" -> List.of(action(null,List.of("x = x + 1")));
            case "semaphore" -> List.of(action("x > 0",List.of("x = x - 1")),action(null,List.of("x = x + 1")));
            case "split" -> List.of(action(null,List.of("local.r = x")),action(null,List.of("x = local.r + 1")));
            default -> throw new AssertionError(mode);
        };
        var model=load(owners,mode.equals("semaphore")?1:0,steps,null);
        var expected=reference(mode,owners);
        assertEquals(concrete,expected.size());
        var permutations=permutations(owners);
        var orbits=new HashSet<String>();
        for(var state:expected)orbits.add(orbit(state,permutations));
        assertEquals(quotient,orbits.size());
        var ordinary=new DfsExplorer().explore(model.program());
        var actual=new HashSet<Reference>();
        for(var state:ordinary.states().values())actual.add(project(state));
        assertEquals(expected,actual);
        for(var backend:SymmetryBackend.values()) {
            var store=SymmetryStateStore.forRun(model.program(),null,Strategy.DFS,backend);
            var reduced=new DfsExplorer().explore(model.program(),null,store,null);
            assertEquals(quotient,reduced.statesExplored());
            var actualOrbits=new HashSet<String>();
            for(var state:reduced.states().values())actualOrbits.add(orbit(project(state),permutations));
            assertEquals(orbits,actualOrbits);
            assertFalse(reduced.traces().isEmpty());
            assertTrue(reduced.traces().stream().allMatch(t->t.outcome()==TraceOutcome.COMPLETED));
            for(var trace:reduced.traces())checkConcreteWitness(model,trace);
        }
    }

    /** Every exact strategy/backend preserves a finite violating final property and concrete witness. */
    @Test void failingFinalPropertyMatchesExhaustiveReferenceAcrossStrategies() {
        var steps=List.of(action(null,List.of("local.r = x")),action(null,List.of("x = local.r + 1")));
        var model=load(2,0,steps,"x == 2","final");
        var reference=reference("split",2);
        var finals=new HashSet<Integer>();
        for(var state:reference)if(state.pcs().equals(List.of(2,2)))finals.add(state.x());
        assertEquals(Set.of(1,2),finals);
        var property=model.invariant().orElseThrow();
        assertTrue(new DfsExplorer().explore(model.program(),property).traces().stream().anyMatch(t->t.outcome()==TraceOutcome.VIOLATION));
        for(var strategy:Strategy.values())for(var backend:SymmetryBackend.values()) {
            var store=SymmetryStateStore.forRun(model.program(),property,strategy,backend);
            var result=switch(strategy) {
                case DFS -> new DfsExplorer().explore(model.program(),property,store,null);
                case STATIC_POR -> new dev.samhb.interleave.por.StaticPorExplorer().explore(model.program(),property,store,null);
                case DPOR -> new dev.samhb.interleave.dpor.DporExplorer().explore(model.program(),property,store,null);
                case CONTEXT_BOUNDED -> new dev.samhb.interleave.cb.ContextBoundedExplorer().explore(model.program(),property,store,null,2);
            };
            var failures=result.traces().stream().filter(t->t.outcome()==TraceOutcome.VIOLATION).toList();
            assertFalse(failures.isEmpty(),strategy+" "+backend);
            for(var trace:failures) {
                var config=model.program().initialConfiguration();
                for(int i=0;i<trace.length();i++) {
                    int owner=trace.threadIds().get(i);assertTrue(config.enabledThreadIds().contains(owner));
                    var next=TransitionExecutor.execute(model.program(),config,owner);
                    assertEquals(StepOutcome.ADVANCED,next.outcome());assertEquals(trace.outcomes().get(i),next.outcome());
                    config=next.successor();
                }
                var observed=project(config);assertTrue(reference.contains(observed));
                assertEquals(List.of(2,2),observed.pcs());assertEquals(1,observed.x());
                assertFalse(property.holds(config.state(),config));
            }
        }
    }

    /** Nested guards, arrays, partial errors and pure properties commute with every owner permutation. */
    @Test void orderedEffectsAndFailuresCommuteWithIndependentFramePermutation() {
        for(var fixture:List.of(new EffectCase(List.of("local.r = x","local.a[0] = local.r + 1","arr[local.a[1]] = local.a[0]","x = arr[0] + 1","local.b = true"),StepOutcome.ADVANCED,false),
                new EffectCase(List.of("x = 7","local.r = x % 0"),StepOutcome.ASSERTION_FAILED,false),
                new EffectCase(List.of("local.a[0] = 1","x = arr[9]"),StepOutcome.ASSERTION_FAILED,true))) {
            var effects=fixture.effects();
            for(String guard:Arrays.asList(null,"false","true || arr[9] == 0","arr[9] == 0")) {
                var model=load(3,0,List.of(action(guard,effects)),"x == 0");
                assertEquals(List.of(List.of(0,1,2)),SourceSymmetry.groups(model.program(),model.invariant().orElseThrow()));
                var initial=(DynamicState)model.program().initialConfiguration().state();
                for(int owner=0;owner<3;owner++) {
                    initial.setLocalInt(owner,"r",owner+2);
                    initial.setLocalBool(owner,"b",owner==1);
                    initial.setLocalArrayElement(owner,"a",0,owner+4);
                    initial.setLocalArrayElement(owner,"a",1,owner%2);
                }
                for(var permutation:permutations(3))for(int owner=0;owner<3;owner++) {
                    var left=(DynamicState)initial.deepCopy();
                    var right=permute(initial,permutation);
                    var a=model.program().threads().get(owner).steps().getFirst();
                    var b=model.program().threads().get(permutation[owner]).steps().getFirst();
                    assertEquals(a.enabled(left),b.enabled(right));
                    var expected="false".equals(guard) ? StepOutcome.BLOCKED
                        : "arr[9] == 0".equals(guard) ? StepOutcome.ASSERTION_FAILED : fixture.outcome();
                    assertEquals(expected,a.execute(left));assertEquals(expected,b.execute(right));
                    assertFramesEqual(permute(left,permutation),right);
                    var lc=new Program(left,model.program().threads()).initialConfiguration();
                    var rc=new Program(right,model.program().threads()).initialConfiguration();
                    boolean expectedProperty="false".equals(guard) || "arr[9] == 0".equals(guard) || fixture.propertyAfterExecution();
                    assertEquals(expectedProperty,model.invariant().orElseThrow().holds(left,lc));
                    assertEquals(expectedProperty,model.invariant().orElseThrow().holds(right,rc));
                }
            }
        }
    }

    /** Enumerates finite transitions using hand-written reference rules, not executing model steps.
     * @param mode selected finite family
     * @param owners owner count
     * @return complete reachable typed scalar/control positions
     */
    private static Set<Reference> reference(String mode,int owners) {
        var root=new Reference(mode.equals("semaphore")?1:0,Collections.nCopies(owners,0),Collections.nCopies(owners,0));
        var seen=new HashSet<Reference>();seen.add(root);
        var queue=new ArrayDeque<Reference>();queue.add(root);
        while(!queue.isEmpty()) {
            var state=queue.removeFirst();
            for(int owner=0;owner<owners;owner++) {
                int pc=state.pcs().get(owner),end=mode.equals("counter")?1:2;
                if(pc==end || mode.equals("semaphore") && pc==0 && state.x()==0)continue;
                int value=state.x();
                var locals=new ArrayList<>(state.locals());var pcs=new ArrayList<>(state.pcs());pcs.set(owner,pc+1);
                switch(mode) {
                    case "counter" -> value++;
                    case "semaphore" -> value+=pc==0?-1:1;
                    case "split" -> {if(pc==0)locals.set(owner,value);else value=locals.get(owner)+1;}
                    default -> throw new AssertionError(mode);
                }
                var next=new Reference(value,List.copyOf(locals),List.copyOf(pcs));
                if(seen.add(next))queue.add(next);
            }
        }
        return Set.copyOf(seen);
    }

    /** Projects actual concrete values without using production keys.
     * @param config concrete position
     * @return independent finite reference position
     */
    private static Reference project(Configuration config) {
        var state=(DynamicState)config.state();var locals=new ArrayList<Integer>();
        for(int owner=0;owner<state.threadCount();owner++)locals.add(state.getLocalInt(owner,"r"));
        return new Reference(state.getInt("x"),List.copyOf(locals),config.programCounters());
    }

    /** Finds a reference orbit by explicitly trying every permutation of complete frames.
     * @param state independent position
     * @param permutations all original-to-image owner maps
     * @return smallest unambiguous finite reference serialization
     */
    private static String orbit(Reference state,List<int[]> permutations) {
        String best=null;
        for(var permutation:permutations) {
            var frames=new String[permutation.length];
            for(int owner=0;owner<permutation.length;owner++)frames[permutation[owner]]=state.pcs().get(owner)+":"+state.locals().get(owner);
            String candidate=state.x()+"|"+String.join("|",frames);
            if(best==null || candidate.compareTo(best)<0)best=candidate;
        }
        return best;
    }

    /** Enumerates permutations independently by insertion into every possible position.
     * @param count number of owners
     * @return original-to-image permutations
     */
    private static List<int[]> permutations(int count) {
        var result=new ArrayList<int[]>();result.add(new int[0]);
        for(int size=0;size<count;size++) {
            var next=new ArrayList<int[]>();
            for(var old:result)for(int slot=0;slot<=size;slot++) {
                var value=new int[size+1];System.arraycopy(old,0,value,0,slot);value[slot]=size;
                System.arraycopy(old,slot,value,slot+1,size-slot);next.add(value);
            }
            result=next;
        }
        return result;
    }

    /** Transports scalar/array owner frames explicitly while shared fields stay fixed.
     * @param original independent concrete state
     * @param permutation original-to-image map
     * @return independent permuted state
     */
    private static DynamicState permute(DynamicState original,int[] permutation) {
        var result=(DynamicState)original.deepCopy();
        for(int owner=0;owner<permutation.length;owner++) {
            result.setLocalInt(permutation[owner],"r",original.getLocalInt(owner,"r"));
            result.setLocalBool(permutation[owner],"b",original.getLocalBool(owner,"b"));
            for(int index=0;index<2;index++)result.setLocalArrayElement(permutation[owner],"a",index,original.getLocalArray(owner,"a")[index]);
        }
        return result;
    }

    /** Compares actual shared and complete typed owner values after the independent transport.
     * @param expected transported reference state
     * @param actual state after corresponding action
     */
    private static void assertFramesEqual(DynamicState expected,DynamicState actual) {
        assertEquals(expected.getInt("x"),actual.getInt("x"));
        assertArrayEquals(expected.getArray("arr"),actual.getArray("arr"));
        for(int owner=0;owner<expected.threadCount();owner++) {
            assertEquals(expected.getLocalInt(owner,"r"),actual.getLocalInt(owner,"r"));
            assertEquals(expected.getLocalBool(owner,"b"),actual.getLocalBool(owner,"b"));
            assertArrayEquals(expected.getLocalArray(owner,"a"),actual.getLocalArray(owner,"a"));
        }
    }

    /** Checks concrete enabled choices/outcomes instead of relying on weak legacy replay alone.
     * @param model real loaded program
     * @param trace actual emitted completion
     */
    private static void checkConcreteWitness(BenchmarkProgram model,Trace trace) {
        var config=model.program().initialConfiguration();
        for(int index=0;index<trace.threadIds().size();index++) {
            int owner=trace.threadIds().get(index);assertTrue(config.enabledThreadIds().contains(owner));
            var next=TransitionExecutor.execute(model.program(),config,owner);
            assertEquals(trace.outcomes().get(index),next.outcome());config=next.successor();
        }
        assertTrue(config.allTerminated());
    }

    /** Creates a JSON action while retaining order and optional guards.
     * @param guard optional guard
     * @param effects ordered effects
     * @return JSON-ready action
     */
    private static Map<String,Object> action(String guard,List<String> effects) {
        var action=new LinkedHashMap<String,Object>();action.put("effects",effects);
        if(guard!=null)action.put("guard",guard);return action;
    }

    /** Loads finite matching owner programs with complete typed scalar/array state.
     * @param owners count of interchangeable owners
     * @param initial shared x
     * @param steps ordered actions
     * @param property optional always property
     * @return validated model
     */
    private static BenchmarkProgram load(int owners,int initial,List<Map<String,Object>> steps,String property) {
        return load(owners,initial,steps,property,"always");
    }

    /** Loads the selected observation timing without changing predicate or transition semantics.
     * @param owners interchangeable owner count
     * @param initial shared x
     * @param steps ordered actions
     * @param property optional shared property
     * @param when final or always observation timing
     * @return validated bounded model
     */
    private static BenchmarkProgram load(int owners,int initial,List<Map<String,Object>> steps,String property,String when) {
        var document=new LinkedHashMap<String,Object>();document.put("format","declarative");document.put("name","oracle");
        document.put("state",Map.of("fields",List.of(Map.of("name","x","type","int","init",initial),
            Map.of("name","arr","type","int[]","init",List.of(0,0))),"locals",List.of(Map.of("name","r","type","int","init",0),
            Map.of("name","b","type","bool","init",false),Map.of("name","a","type","int[]","init",List.of(0,0)))));
        var threads=new ArrayList<Map<String,Object>>();
        for(int owner=0;owner<owners;owner++)threads.add(Map.of("id",owner,"steps",steps));
        document.put("threads",threads);if(property!=null)document.put("invariant",Map.of("when",when,"expr",property));
        return new ProgramLoader().load(new Gson().toJson(document));
    }

    /** Literal effect outcomes and shared-property observations derived from each fixture's statements.
     * @param effects ordered source effects
     * @param outcome expected unguarded execution result
     * @param propertyAfterExecution whether x remains zero after the unguarded attempt
     */
    private record EffectCase(List<String> effects,StepOutcome outcome,boolean propertyAfterExecution) {}

    /** Independent state for the bounded counter, semaphore and split-increment rules.
     * @param x shared integer
     * @param locals owner-local scalar registers
     * @param pcs whole owner control frames
     */
    private record Reference(int x,List<Integer> locals,List<Integer> pcs) {}
}
