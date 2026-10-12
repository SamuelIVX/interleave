/** Tests for declarative DSL local array support. */
package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.MemoryLocation;
import dev.samhb.interleave.core.StepOutcome;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.state.HashingStateStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests parsing, type checking, runtime execution, and POR memory footprints for DSL local arrays.
 */
class DslLocalArrayTest {

    private final ProgramLoader loader = new ProgramLoader();

    /** Tests valid declaration and execution of a program with local arrays. */
    @Test
    void loadAndExecute_localArrayProgram_succeeds() {
        String json = """
            {
              "format": "declarative",
              "name": "local-array-demo",
              "state": {
                "fields": [
                  {"name": "done", "type": "bool", "init": false}
                ],
                "locals": [
                  {"name": "items", "type": "int[]", "init": [10, 20, 30]},
                  {"name": "idx", "type": "int", "init": 1}
                ]
              },
              "threads": [
                {
                  "id": 0,
                  "steps": [
                    {
                      "name": "mutate-local",
                      "guard": "local.items[local.idx] == 20",
                      "effects": ["local.items[1] = 99", "done = true"]
                    }
                  ]
                }
              ]
            }
            """;
        BenchmarkProgram prog = loader.load(json);
        assertNotNull(prog);

        DfsExplorer explorer = new DfsExplorer();
        DfsResult result = explorer.explore(prog.program(), null, new HashingStateStore(), null);
        assertNotNull(result);
        assertEquals(2, result.statesExplored());
    }

    /** Tests thread isolation for local arrays. */
    @Test
    void localArray_threadIsolation() {
        StateDecl decl = new StateDecl(
            List.of(FieldDecl.ofInt("shared", 0)),
            List.of(LocalDecl.ofArray("buf", new int[]{1, 2, 3}))
        );
        DynamicState state = new DynamicState(decl, 2);

        // Thread 0 modifies buf[0]
        state.setLocalArrayElement(0, "buf", 0, 100);

        // Thread 1 buf must remain unchanged
        assertEquals(100, state.getLocalArray(0, "buf")[0]);
        assertEquals(1, state.getLocalArray(1, "buf")[0]);
        assertArrayEquals(new int[]{100, 2, 3}, state.getLocalArray(0, "buf"));
        assertArrayEquals(new int[]{1, 2, 3}, state.getLocalArray(1, "buf"));
    }

    /** Tests memory footprint derivation for static and dynamic local array accesses. */
    @Test
    void stepFootprint_localArrayReadsAndWrites() {
        StateDecl decl = new StateDecl(
            List.of(FieldDecl.ofInt("x", 0)),
            List.of(LocalDecl.ofArray("buf", new int[]{0, 0}), LocalDecl.ofInt("i", 0))
        );

        // Static index write: local.buf[1] = 5
        Effect effStatic = new Effect(new Lhs.LocalArrayLhs("buf", new Expr.IntLit(1)), new Expr.IntLit(5));
        DynamicStep stepStatic = new DynamicStep(0, null, List.of(effStatic), decl, "static-write");
        assertEquals(Set.of(MemoryLocation.of("t0.buf[1]")), stepStatic.writes());
        assertTrue(stepStatic.reads().isEmpty());

        // Dynamic index write: local.buf[local.i] = 7
        Effect effDyn = new Effect(new Lhs.LocalArrayLhs("buf", new Expr.LocalRef("i")), new Expr.IntLit(7));
        DynamicStep stepDyn = new DynamicStep(0, null, List.of(effDyn), decl, "dyn-write");
        assertEquals(Set.of(MemoryLocation.of("t0.buf")), stepDyn.writes());
        assertEquals(Set.of(MemoryLocation.of("t0.i")), stepDyn.reads());

        // Dynamic index read in guard: local.buf[local.i] > 0
        Expr guard = new Expr.BinaryOp(new Expr.ArrayAccess("local.buf", new Expr.LocalRef("i")), ">", new Expr.IntLit(0));
        DynamicStep stepGuard = new DynamicStep(0, guard, List.of(effStatic), decl, "guard-step");
        assertEquals(Set.of(MemoryLocation.of("t0.buf"), MemoryLocation.of("t0.i")), stepGuard.reads());
    }

    /** Tests runtime out-of-bounds error handling. */
    @Test
    void outOfBounds_evaluatesToAssertionFailed() {
        StateDecl decl = new StateDecl(
            List.of(FieldDecl.ofInt("x", 0)),
            List.of(LocalDecl.ofArray("buf", new int[]{10, 20}))
        );
        DynamicState state = new DynamicState(decl, 1);

        // Guard OOB: enabled() returns true so execute() reports ASSERTION_FAILED
        Expr oobGuard = new Expr.BinaryOp(new Expr.ArrayAccess("local.buf", new Expr.IntLit(5)), "==", new Expr.IntLit(10));
        Effect dummyEff = new Effect(new Lhs.FieldLhs("x"), new Expr.IntLit(1));
        DynamicStep stepGuard = new DynamicStep(0, oobGuard, List.of(dummyEff), decl, "oob-guard");
        assertTrue(stepGuard.enabled(state));
        assertEquals(StepOutcome.ASSERTION_FAILED, stepGuard.execute(state));

        // Effect OOB write
        Effect oobWrite = new Effect(new Lhs.LocalArrayLhs("buf", new Expr.IntLit(10)), new Expr.IntLit(99));
        DynamicStep stepWrite = new DynamicStep(0, null, List.of(oobWrite), decl, "oob-write");
        assertTrue(stepWrite.enabled(state));
        assertEquals(StepOutcome.ASSERTION_FAILED, stepWrite.execute(state));
    }

    /** Tests validation error on local array without index in expression. */
    @Test
    void validation_localArrayWithoutIndexInExpr_throws() {
        String json = """
            {
              "format": "declarative",
              "name": "bad-ref",
              "state": {
                "fields": [{"name": "x", "type": "int", "init": 0}],
                "locals": [{"name": "arr", "type": "int[]", "init": [1, 2]}]
              },
              "threads": [
                {
                  "id": 0,
                  "steps": [
                    {"effects": ["x = local.arr"]}
                  ]
                }
              ]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("cannot be used as scalar: use local.arr[index]"));
    }

    /** Tests validation error on whole local array assignment. */
    @Test
    void validation_localArrayWholeAssignment_throws() {
        String json = """
            {
              "format": "declarative",
              "name": "bad-assign",
              "state": {
                "fields": [{"name": "x", "type": "int", "init": 0}],
                "locals": [{"name": "arr", "type": "int[]", "init": [1, 2]}]
              },
              "threads": [
                {
                  "id": 0,
                  "steps": [
                    {"effects": ["local.arr = 5"]}
                  ]
                }
              ]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("requires index: use local.arr[expr]"));
    }

    /** Tests validation error on non-int assignment to local array element. */
    @Test
    void validation_nonIntAssignToLocalArray_throws() {
        String json = """
            {
              "format": "declarative",
              "name": "bad-type-assign",
              "state": {
                "fields": [{"name": "x", "type": "int", "init": 0}],
                "locals": [{"name": "arr", "type": "int[]", "init": [1, 2]}]
              },
              "threads": [
                {
                  "id": 0,
                  "steps": [
                    {"effects": ["local.arr[0] = true"]}
                  ]
                }
              ]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("requires int"));
    }

    /** Tests validation error when local array is referenced in invariant. */
    @Test
    void validation_localArrayInInvariant_throws() {
        String json = """
            {
              "format": "declarative",
              "name": "bad-inv",
              "state": {
                "fields": [{"name": "x", "type": "int", "init": 0}],
                "locals": [{"name": "arr", "type": "int[]", "init": [1, 2]}]
              },
              "threads": [
                {
                  "id": 0,
                  "steps": [{"effects": ["x = 1"]}]
                }
              ],
              "invariant": {
                "expr": "local.arr[0] == 1"
              }
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("Invariant must not reference local.*"));
    }

    /** Tests local array init exceeding 64 elements limit. */
    @Test
    void validation_localArrayTooLong_throws() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 65; i++) {
            if (i > 0) sb.append(", ");
            sb.append("0");
        }
        sb.append("]");
        String json = """
            {
              "format": "declarative",
              "name": "too-long",
              "state": {
                "fields": [{"name": "x", "type": "int", "init": 0}],
                "locals": [{"name": "arr", "type": "int[]", "init": %s}]
              },
              "threads": [{"id": 0, "steps": [{"effects": ["x = 1"]}]}]
            }
            """.formatted(sb.toString());
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("Array length must be <=64"));
    }
}
