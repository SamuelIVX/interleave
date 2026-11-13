/** Exercises loader-issued symmetry trust and reassembled declarative admission. */
package dev.samhb.interleave.format.dsl;

import com.google.gson.Gson;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.SymmetryDfsExplorer;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Checks trusted JSON eligibility through the public loader and checked search. */
class DeclarativeSymmetryEligibilityTest {
    /** The finite semaphore has eight concrete positions and five independently enumerated orbits. */
    @Test void semaphoreMatchesIndependentOrbitCounts() {
        var model = new ProgramLoader().loadFromFile(Path.of("examples/programs/semaphore-declarative.json"));
        var property = model.invariant().orElseThrow();
        assertEquals(List.of(List.of(0, 1)), SourceSymmetry.groups(model.program(), property));
        assertEquals(8, new DfsExplorer().explore(model.program(), property).statesExplored());
        assertEquals(5, new SymmetryDfsExplorer().explore(model.program(), property).statesExplored());
        assertFalse(model.program().isSource());
        assertNull(model.program().sourceDigest());
    }

    /** Public constructors stay usable for ordinary execution but do not issue trust. */
    @Test void publicConstructionAndMissingLoaderPermitCannotIssueTrust() {
        var model = pair(null, List.of("x = x + 1"), null);
        var state = (DynamicState) model.program().initialConfiguration().state();
        var effect = new Parser("x = x + 1").parseEffect();
        var steps = List.of(new ModelThread(0, List.of(new DynamicStep(0, null, List.of(effect), state.decl(), "a"))),
            new ModelThread(1, List.of(new DynamicStep(1, null, List.of(effect), state.decl(), "b"))));
        var untrusted = new Program(state, steps);
        assertEquals(4, new DfsExplorer().explore(untrusted).statesExplored());
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(untrusted, null));
        var publicProperty = new DslInvariant(new Expr.BoolLit(true), state.decl());
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(model.program(), publicProperty));
        assertThrows(NullPointerException.class, () -> DynamicStep.validated(null, 0, null, List.of(effect), state.decl(), null));
        assertThrows(NullPointerException.class, () -> DslInvariant.validated(null,
            List.of(new Expr.LocalRef("v")), state.decl(), DslInvariant.When.ALWAYS));
    }

    /** Trust is tied to complete owner/declaration bindings, including fixed owners and properties. */
    @Test void reassemblyRejectsForeignBindingsAndCallbacks() {
        var a = pair(null, List.of("x = 1"), "true");
        var b = pair(null, List.of("x = 1"), "true");
        var state = (DynamicState) a.program().initialConfiguration().state();
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(a.program(), b.invariant().orElseThrow()));
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(a.program(), (s,c) -> true));
        var foreign = new Program(state, List.of(a.program().threads().get(0), b.program().threads().get(1)));
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(foreign, null));
        var wrongState = new Program(b.program().initialConfiguration().state(), a.program().threads());
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(wrongState, null));
        var wrongOwner = new Program(state, List.of(a.program().threads().get(0),
            new ModelThread(1, a.program().threads().get(0).steps())));
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(wrongOwner, null));
        var wrongId = new Program(state, List.of(a.program().threads().get(0), new ModelThread(7, a.program().threads().get(1).steps())));
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(wrongId, null));
        var wrongCount = new Program(new DynamicState(state.decl(), 3), a.program().threads());
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(wrongCount, null));
    }

    /** Repeated certified occurrences still count against per-owner and aggregate AST bounds. */
    @Test void reassembledModelsRetainAdmissionBounds() {
        var model = pair(null, List.of("x = 1"), null);
        assertEquals(List.of(List.of(0,1)), SourceSymmetry.groups(repeat(model,64), null));
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(repeat(model,65), null));
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(repeat(model,0), null));
        var declaration=((DynamicState)model.program().initialConfiguration().state()).decl();
        var partialEmpty=new Program(new DynamicState(declaration,3),List.of(model.program().threads().get(0),
            model.program().threads().get(1),new ModelThread(2,List.of())));
        assertThrows(IllegalArgumentException.class,()->SourceSymmetry.groups(partialEmpty,null));
        // A balanced sum of 125 leaves has 249 AST nodes; the true guard adds one.
        var budget = pair("true", List.of("x = " + sum(125)), "true");
        assertEquals(List.of(List.of(0,1)), SourceSymmetry.groups(repeat(budget,10), null));
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(repeat(budget,11), null));
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(repeat(budget,10), budget.invariant().orElseThrow()));
        // Two 2,499-node RHS trees plus a two-node shared property exactly reach the admission cap.
        var withProperty=pair(null,List.of("x = "+sum(1250)),"!false");
        assertEquals(List.of(List.of(0,1)),SourceSymmetry.groups(withProperty.program(),withProperty.invariant().orElseThrow()));
        // 247 RHS nodes + one guard + three index nodes = 251; twenty occurrences exceed 5,000.
        for(String target:List.of("arr[0+0]","local.a[0+0]")) {
            var indexed=pair("true",List.of(target+" = "+sum(124)),null);
            assertThrows(IllegalArgumentException.class,()->SourceSymmetry.groups(repeat(indexed,10),null));
        }
    }

    /** Any nested tid syntax fixes its owner, including short-circuited expressions and array indices.
     * @param effect owner-sensitive effect
     */
    @ParameterizedTest @ValueSource(strings={"x = tid", "x = arr[tid]", "arr[tid] = 1", "local.a[tid] = 1", "x = local.a[tid]", "x = -tid"})
    void nestedIdentityObservationsDoNotQualify(String effect) {
        var model = pair(null,List.of(effect),null);
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(model.program(),null));
    }

    /** Grouping preserves structure/order and ignores diagnostic names, not semantic differences. */
    @Test void guardsOrderingAndInitialFramesRemainDistinct() {
        var identity = pair("true || tid == 0",List.of("x = 1"),null);
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(identity.program(),null));
        var model = pair(null,List.of("x = 1","x = x + 1"),null);
        var fixed = model;
        var state = (DynamicState) model.program().initialConfiguration().state();
        state.setLocalInt(1,"v",1);
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(new Program(state,fixed.program().threads()),null));
        var root = (DynamicState) model.program().initialConfiguration().state();
        root.setLocalBool(1,"b",true);
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(new Program(root,fixed.program().threads()),null));
        var arrays = (DynamicState) model.program().initialConfiguration().state();
        arrays.getLocalArrayRef(1,"a")[0]=1;
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(new Program(arrays,fixed.program().threads()),null));
    }

    /** A custom stored state cannot establish trust by returning a DynamicState from later copies. */
    @Test void storedStateTypeRejectsCopyAdaptersBeforeAnotherCallback() {
        var model=new ProgramLoader().loadFromFile(Path.of("examples/programs/semaphore-declarative.json"));
        var copies=new java.util.concurrent.atomic.AtomicInteger();
        SharedState adapter=new SharedState() {
            /** {@inheritDoc} */
            public SharedState deepCopy() {
                return copies.getAndIncrement()==0 ? this : model.program().initialConfiguration().state();
            }
            /** {@inheritDoc} */
            public void encodeTo(java.io.DataOutput out) {throw new AssertionError("untrusted encoder invoked");}
        };
        var forged=new Program(adapter,model.program().threads());
        int before=copies.get();
        assertThrows(IllegalArgumentException.class,()->SourceSymmetry.groups(forged,null));
        assertEquals(before,copies.get(),"certification must not invoke another untrusted copy");
    }

    /** Input provenance cannot be laundered through an adapter's first defensive copy. */
    @Test void firstCopyAdapterCannotIssueTrustedStateProvenance() {
        var model=pair(null,List.of("x = 1"),null);
        var retained=(DynamicState)model.program().initialConfiguration().state();
        var copies=new java.util.concurrent.atomic.AtomicInteger();
        SharedState adapter=new SharedState() {
            /** {@inheritDoc} */
            public SharedState deepCopy() {copies.incrementAndGet();return retained;}
            /** {@inheritDoc} */
            public void encodeTo(java.io.DataOutput out) {throw new AssertionError("untrusted encoder invoked");}
        };
        var forged=new Program(adapter,model.program().threads());
        assertEquals(DynamicState.class,forged.initialStateType());
        assertEquals(adapter.getClass(),forged.initialStateInputType());
        // The adapter retains the snapshot; post-construction writes can change this program's root.
        retained.setInt("x",7);
        assertEquals(7,((DynamicState)forged.initialConfiguration().state()).getInt("x"));
        int before=copies.get();
        assertThrows(IllegalArgumentException.class,()->SourceSymmetry.groups(forged,null));
        assertEquals(before,copies.get(),"certification must reject without another adapter callback");
        // Ordinary construction/copy semantics stay usable; checked admission owns the restriction.
        var trusted=new Program(retained,model.program().threads());
        assertEquals(DynamicState.class,trusted.initialStateInputType());
        assertEquals(List.of(List.of(0,1)),SourceSymmetry.groups(trusted,null));
    }

    /** Direct callers must supply the declarative discriminator before the loader can issue trust.
     * @param format unsupported or missing format
     * @throws java.io.IOException if the checked-in fixture cannot be read
     */
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings="typed")
    void directLoaderRejectsOtherFormatsBeforeCertification(String format) throws java.io.IOException {
        var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
            Path.of("examples/programs/semaphore-declarative.json"))).getAsJsonObject();
        if(format==null)json.remove("format");else json.addProperty("format",format);
        var definition=new Gson().fromJson(json,dev.samhb.interleave.format.model.ProgramDefinition.class);
        var rejected=assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            ()->DslLoader.load(definition));
        assertTrue(rejected.getMessage().contains("format"));
        json.addProperty("format","declarative");
        var valid=DslLoader.load(new Gson().fromJson(json,dev.samhb.interleave.format.model.ProgramDefinition.class));
        assertEquals(List.of(List.of(0,1)),SourceSymmetry.groups(valid.program(),valid.invariant().orElseThrow()));
    }

    /** Fixed owners remain trusted and cannot smuggle executable callbacks into certification. */
    @Test void fixedOwnerCallbackRejectsBeforeEnabledness() {
        var model=pair(null,List.of("x = 1"),null);
        var decl=((DynamicState)model.program().initialConfiguration().state()).decl();
        Step callback=new Step() {
            /** {@inheritDoc} */
            public java.util.Set<MemoryLocation> reads(){return java.util.Set.of();}
            /** {@inheritDoc} */
            public java.util.Set<MemoryLocation> writes(){return java.util.Set.of();}
            /** {@inheritDoc} */
            public boolean enabled(SharedState state){throw new AssertionError("untrusted callback invoked");}
            /** {@inheritDoc} */
            public StepOutcome execute(SharedState state){throw new AssertionError("untrusted callback executed");}
        };
        var threads=new ArrayList<>(model.program().threads());threads.add(new ModelThread(2,List.of(callback)));
        var program=new Program(new DynamicState(decl,3),threads);
        assertThrows(IllegalArgumentException.class,()->SourceSymmetry.groups(program,null));
    }

    /** Names are diagnostic; ordered semantic differences and owner observations stay significant. */
    @Test void noncontiguousGroupsFixObserversAndDoNotNormalizeSyntax() {
        String json="""
            {"format":"declarative","name":"mixed","state":{"fields":[{"name":"x","type":"int","init":0}],"locals":[]},
            "threads":[{"id":0,"steps":[{"name":"first","effects":["x = 1","x = x + 1"]}]},
            {"id":1,"steps":[{"effects":["x = tid"]}]},
            {"id":2,"steps":[{"name":"third","effects":["x = 1","x = x + 1"]}]}]}
            """;
        var model=new ProgramLoader().load(json);
        assertEquals(List.of(List.of(0,2)),SourceSymmetry.groups(model.program(),null));
        var reversed=new ProgramLoader().load(json.replace("\"name\":\"third\",\"effects\":[\"x = 1\",\"x = x + 1\"]",
            "\"effects\":[\"x = x + 1\",\"x = 1\"]"));
        assertThrows(IllegalArgumentException.class,()->SourceSymmetry.groups(reversed.program(),null));
        var guarded=new ProgramLoader().load(json.replace("\"name\":\"third\"","\"guard\":\"false\""));
        assertThrows(IllegalArgumentException.class,()->SourceSymmetry.groups(guarded.program(),null));
        var source=new dev.samhb.interleave.format.SourceProgramLoader().load(
            "program mixed { shared int x = 0; thread 0 { atomic { x = 1; } } thread 1 { atomic { x = 1; } } }");
        var mixed=new Program(source.program().initialConfiguration().state(),source.program().threads());
        assertThrows(IllegalArgumentException.class,()->SourceSymmetry.groups(mixed,null));
    }

    /** Reassembles a bounded number of occurrences without minting new trusted instructions.
     * @param model original validated model
     * @param count occurrences in each owner
     * @return isolated reassembled program
     */
    private static Program repeat(BenchmarkProgram model,int count) {
        var threads=new ArrayList<ModelThread>();
        for(var thread:model.program().threads()) threads.add(new ModelThread(thread.id(),Collections.nCopies(count,thread.steps().getFirst())));
        return new Program(model.program().initialConfiguration().state(),threads);
    }

    /** Builds a balanced bounded expression without relying on production AST node counting.
     * @param leaves literal leaves
     * @return sum expression
     */
    private static String sum(int leaves) {
        return leaves==1 ? "1" : "("+sum(leaves/2)+"+"+sum(leaves-leaves/2)+")";
    }

    /** Loads a real typed JSON pair with scalar/array locals and optional shared property.
     * @param guard optional guard
     * @param effects ordered effects
     * @param property optional always-timed shared predicate
     * @return validated model
     */
    private static BenchmarkProgram pair(String guard,List<String> effects,String property) {
        var gson=new Gson();
        var action=new java.util.LinkedHashMap<String,Object>();
        action.put("effects",effects); if(guard!=null) action.put("guard",guard);
        var json="""
            {"format":"declarative","name":"pair","state":{"fields":[{"name":"x","type":"int","init":0},
            {"name":"arr","type":"int[]","init":[0,0]}],"locals":[{"name":"v","type":"int","init":0},
            {"name":"b","type":"bool","init":false},{"name":"a","type":"int[]","init":[0,0]}]},
            "threads":[{"id":0,"steps":[%s]},{"id":1,"steps":[%s]}]%s}
            """.formatted(gson.toJson(action),gson.toJson(action),property==null ? "" : ",\"invariant\":"+gson.toJson(Map.of("when","always","expr",property)));
        return new ProgramLoader().load(json);
    }
}
