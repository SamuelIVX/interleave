package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.search.TraceOutcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for declarative DSL loader (Spec 09).
 */
class DslLoaderTest {
    private final ProgramLoader loader = new ProgramLoader();

    @Test
    /** Tests declarativeLostUpdate_withoutInvariant_isPass. */
    void declarativeLostUpdate_withoutInvariant_isPass() {
        String json = """
            {
              "format": "declarative",
              "name": "lost-update-declarative",
              "state": {
                "fields": [{"name": "counter", "type": "int", "init": 0}],
                "locals": [{"name": "r", "type": "int", "init": 0}]
              },
              "threads": [
                {"id": 0, "steps": [
                  {"effects": ["local.r = counter"]},
                  {"effects": ["counter = local.r + 1"]}
                ]},
                {"id": 1, "steps": [
                  {"effects": ["local.r = counter"]},
                  {"effects": ["counter = local.r + 1"]}
                ]}
              ]
            }
            """;
        BenchmarkProgram prog = loader.load(json);
        DfsResult res = new DfsExplorer().explore(prog.program());
        // no invariant, so completed
        assertTrue(res.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.COMPLETED));
        assertFalse(res.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION));
    }

    @Test
    /** Tests declarativeLostUpdate_withInvariant_matchesTyped. */
    void declarativeLostUpdate_withInvariant_matchesTyped() {
        String json = """
            {
              "format": "declarative",
              "name": "lost-update-declarative",
              "state": {
                "fields": [{"name": "counter", "type": "int", "init": 0}],
                "locals": [{"name": "r", "type": "int", "init": 0}]
              },
              "threads": [
                {"id": 0, "steps": [
                  {"effects": ["local.r = counter"]},
                  {"effects": ["counter = local.r + 1"]}
                ]},
                {"id": 1, "steps": [
                  {"effects": ["local.r = counter"]},
                  {"effects": ["counter = local.r + 1"]}
                ]}
              ],
              "invariant": {"expr": "counter == 2"},
              "expected_verdict": "VIOLATION"
            }
            """;
        BenchmarkProgram dslProg = loader.load(json);
        BenchmarkProgram typedProg = loader.loadFromResource("programs/lost-update.json");
        DfsExplorer explorer = new DfsExplorer();
        DfsResult dslRes = explorer.explore(dslProg.program(), dslProg.invariant().orElse(null));
        DfsResult typedRes = explorer.explore(typedProg.program(), typedProg.invariant().orElse(null));
        assertTrue(dslRes.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION));
        assertEquals(typedRes.traces().stream().filter(t -> t.outcome() == TraceOutcome.VIOLATION).count(),
                     dslRes.traces().stream().filter(t -> t.outcome() == TraceOutcome.VIOLATION).count());
        assertEquals(typedRes.statesExplored(), dslRes.statesExplored());
    }

    @Test
    /** Tests declarativeGuard_blocksExecution. */
    void declarativeGuard_blocksExecution() {
        String json = """
            {
              "format": "declarative",
              "name": "guard-test",
              "state": {
                "fields": [
                  {"name": "x", "type": "int", "init": 0},
                  {"name": "flag", "type": "bool", "init": false}
                ]
              },
              "threads": [
                {"id": 0, "steps": [
                  {"guard": "flag == true", "effects": ["x = 1"]}
                ]},
                {"id": 1, "steps": [
                  {"effects": ["flag = true"]}
                ]}
              ],
              "invariant": {"expr": "x == 0 || flag == true"}
            }
            """;
        BenchmarkProgram prog = loader.load(json);
        DfsResult res = new DfsExplorer().explore(prog.program(), prog.invariant().orElse(null));
        assertFalse(res.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION));
    }

    @Test
    /** Tests formatMissing_throws. */
    void formatMissing_throws() {
        String json = """
            {
              "name": "test",
              "state": {"type": "counter", "counter": 0},
              "threads": [{"id": 0, "steps": [{"type": "read_counter"}]}]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("format") && ex.getMessage().contains("Valid formats"));
    }

    @Test
    /** Tests unknownFormat_throws. */
    void unknownFormat_throws() {
        String json = """
            {
              "format": "unknown",
              "name": "test",
              "state": {"type": "counter", "counter": 0},
              "threads": [{"id": 0, "steps": [{"type": "read_counter"}]}]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("Unknown format"));
    }

    @Test
    /** Tests declarativeDuplicateField_throws. */
    void declarativeDuplicateField_throws() {
        String dup = """
            {
              "format": "declarative",
              "name": "test",
              "state": {
                "fields": [
                  {"name": "x", "type": "int", "init": 0},
                  {"name": "x", "type": "int", "init": 1}
                ]
              },
              "threads": [{"id": 0, "steps": [{"effects": ["x = 1"]}]}]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(dup));
        assertTrue(ex.getMessage().contains("Duplicate"));
    }

    @Test
    /** Tests declarativeFieldUnknownKey_throws. */
    void declarativeFieldUnknownKey_throws() {
        String json = """
            {
              "format": "declarative",
              "name": "test",
              "state": {
                "fields": [{"name": "x", "type": "int", "init": 0, "extra": 1}]
              },
              "threads": [{"id": 0, "steps": [{"effects": ["x = 1"]}]}]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("Unknown field"));
    }

    @Test
    /** Tests declarativeStepUnknownKey_throws. */
    void declarativeStepUnknownKey_throws() {
        String json = """
            {
              "format": "declarative",
              "name": "test",
              "state": {
                "fields": [{"name": "x", "type": "int", "init": 0}]
              },
              "threads": [{"id": 0, "steps": [{"effects": ["x = 1"], "type": "read_counter"}]}]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("Unknown field"));
    }

    @Test
    /** Tests declarativeTypeMismatch_throws. */
    void declarativeTypeMismatch_throws() {
        String json = """
            {
              "format": "declarative",
              "name": "test",
              "state": {
                "fields": [{"name": "flag", "type": "bool", "init": false}]
              },
              "threads": [{"id": 0, "steps": [{"effects": ["flag = 1"]}]}]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("Type mismatch"));
    }

    @Test
    /** Tests declarativeInvariantMustBeBool_throws. */
    void declarativeInvariantMustBeBool_throws() {
        String json = """
            {
              "format": "declarative",
              "name": "test",
              "state": {
                "fields": [{"name": "x", "type": "int", "init": 0}]
              },
              "threads": [{"id": 0, "steps": [{"effects": ["x = 1"]}]}],
              "invariant": {"expr": "x + 1"}
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().toLowerCase().contains("bool"));
    }

    @Test
    /** Tests declarativeInvariantForbidsLocal_throws. */
    void declarativeInvariantForbidsLocal_throws() {
        String json = """
            {
              "format": "declarative",
              "name": "test",
              "state": {
                "fields": [{"name": "x", "type": "int", "init": 0}],
                "locals": [{"name": "r", "type": "int", "init": 0}]
              },
              "threads": [{"id": 0, "steps": [{"effects": ["local.r = x"]}]}],
              "invariant": {"expr": "local.r == 0"}
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("local"));
    }

    @Test
    /** Tests declarativePorDerivation_overApproximate. */
    void declarativePorDerivation_overApproximate() {
        String json = """
            {
              "format": "declarative",
              "name": "por-test",
              "state": {
                "fields": [
                  {"name": "a", "type": "int", "init": 0},
                  {"name": "b", "type": "int", "init": 0},
                  {"name": "arr", "type": "int[]", "init": [0,0,0]}
                ]
              },
              "threads": [
                {"id": 0, "steps": [{"effects": ["a = 1"]}]},
                {"id": 1, "steps": [{"effects": ["b = 1"]}]},
                {"id": 2, "steps": [{"effects": ["arr[0] = 1"]}]},
                {"id": 3, "steps": [{"effects": ["arr[1] = 1"]}]}
              ]
            }
            """;
        BenchmarkProgram prog = loader.load(json);
        // Check derived reads/writes are not empty and are distinct per thread
        var steps = prog.program().threads().stream().flatMap(t -> t.steps().stream()).toList();
        // a and b steps should be independent (different locations) - check writes distinct
        assertNotEquals(steps.get(0).writes(), steps.get(1).writes());
        // arr[0] vs arr[1] should be distinct locations per spec (constant index)
        assertTrue(steps.get(2).writes().toString().contains("arr[0]"));
        assertTrue(steps.get(3).writes().toString().contains("arr[1]"));
        // non-constant index should be whole array
        String json2 = """
            {
              "format": "declarative",
              "name": "por-test2",
              "state": {
                "fields": [{"name": "arr", "type": "int[]", "init": [0,0,0]}]
              },
              "threads": [
                {"id": 0, "steps": [{"effects": ["arr[0] = 1"]}]},
                {"id": 1, "steps": [{"effects": ["arr[a] = 1"], "guard": null}]}
              ]
            }
            """;
        // need a field a, but we omitted, create with a
        String json3 = """
            {
              "format": "declarative",
              "name": "por-test2",
              "state": {
                "fields": [
                  {"name": "arr", "type": "int[]", "init": [0,0,0]},
                  {"name": "a", "type": "int", "init": 0}
                ]
              },
              "threads": [
                {"id": 0, "steps": [{"effects": ["arr[0] = 1"]}]},
                {"id": 1, "steps": [{"effects": ["arr[a] = 1"]}]}
              ]
            }
            """;
        BenchmarkProgram prog2 = loader.load(json3);
        var s0 = prog2.program().threads().get(0).steps().get(0);
        var s1 = prog2.program().threads().get(1).steps().get(0);
        assertTrue(s0.writes().toString().contains("arr[0]"));
        assertTrue(s1.writes().toString().contains("arr"));
        assertFalse(s1.writes().toString().contains("arr["));
    }

    @Test
    /** Tests declarativeArrayOob_reportsViolation. */
    void declarativeArrayOob_reportsViolation() {
        String json = """
            {
              "format": "declarative",
              "name": "oob-test",
              "state": {
                "fields": [{"name": "arr", "type": "int[]", "init": [0,0]}]
              },
              "threads": [
                {"id": 0, "steps": [{"effects": ["arr[5] = 1"]}]}
              ]
            }
            """;
        BenchmarkProgram prog = loader.load(json);
        DfsResult res = new DfsExplorer().explore(prog.program());
        assertTrue(res.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION));
    }

    @Test
    /** Tests declarativeModuloByZero_reportsViolation. */
    void declarativeModuloByZero_reportsViolation() {
        String json = """
            {
              "format": "declarative",
              "name": "mod-zero",
              "state": {
                "fields": [{"name": "x", "type": "int", "init": 10}]
              },
              "threads": [
                {"id": 0, "steps": [{"effects": ["x = x % 0"]}]}
              ]
            }
            """;
        BenchmarkProgram prog = loader.load(json);
        DfsResult res = new DfsExplorer().explore(prog.program());
        assertTrue(res.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION));
    }

    @Test
    /** Tests parserDepthLimit_enforced. */
    void parserDepthLimit_enforced() {
        // build deep expression with 17 unary '!' to exceed depth 16
        StringBuilder sb = new StringBuilder("x");
        for (int i = 0; i < 17; i++) sb.insert(0, "!");
        String expr = sb.toString();
        String json = """
            {
              "format": "declarative",
              "name": "depth-test",
              "state": {"fields": [{"name": "x", "type": "bool", "init": false}, {"name": "x2", "type": "bool", "init": false}]},
              "threads": [{"id": 0, "steps": [{"effects": ["x2 = %s"]}]}]
            }
            """.formatted(expr);
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("depth"));
    }

    @Test
    /** Tests declarativeFractionalInit_throws. */
    void declarativeFractionalInit_throws() {
        String json = """
            {
              "format": "declarative",
              "name": "frac",
              "state": {"fields": [{"name": "x", "type": "int", "init": 1.5}]},
              "threads": [{"id": 0, "steps": [{"effects": ["x = 1"]}]}]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().toLowerCase().contains("int"));
    }

    @Test
    /** Tests declarativeOverflowInit_throws. */
    void declarativeOverflowInit_throws() {
        String json = """
            {
              "format": "declarative",
              "name": "overflow",
              "state": {"fields": [{"name": "x", "type": "int", "init": 3000000000}]},
              "threads": [{"id": 0, "steps": [{"effects": ["x = 1"]}]}]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().toLowerCase().contains("int"));
    }

    @Test
    /** Tests declarativeArrayElementFractional_throws. */
    void declarativeArrayElementFractional_throws() {
        String json = """
            {
              "format": "declarative",
              "name": "arr-frac",
              "state": {"fields": [{"name": "arr", "type": "int[]", "init": [1, 1.5]}]},
              "threads": [{"id": 0, "steps": [{"effects": ["arr[0] = 1"]}]}]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().toLowerCase().contains("int"));
    }

    @Test
    /** Tests declarativeLocalsNonStringName_throws. */
    void declarativeLocalsNonStringName_throws() {
        String json = """
            {
              "format": "declarative",
              "name": "bad-local",
              "state": {
                "fields": [{"name": "x", "type": "int", "init": 0}],
                "locals": [{"name": 123, "type": "int", "init": 0}]
              },
              "threads": [{"id": 0, "steps": [{"effects": ["x = 1"]}]}]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("string"));
    }

    @Test
    /** Tests dynamicStateToString_includesLocals. */
    void dynamicStateToString_includesLocals() {
        String json = """
            {
              "format": "declarative",
              "name": "locals-tostring",
              "state": {
                "fields": [{"name": "x", "type": "int", "init": 0}],
                "locals": [{"name": "r", "type": "int", "init": 5}]
              },
              "threads": [
                {"id": 0, "steps": [{"effects": ["local.r = 1"]}]},
                {"id": 1, "steps": [{"effects": ["x = 1"]}]}
              ]
            }
            """;
        BenchmarkProgram prog = loader.load(json);
        String s = prog.program().initialConfiguration().state().toString();
        assertTrue(s.contains("t0.r="));
        assertTrue(s.contains("t1.r="));
    }

    @Test
    /** Tests dynamicStateToString_distinctForDifferentLocals. */
    void dynamicStateToString_distinctForDifferentLocals() {
        StateDecl decl = new StateDecl(
                java.util.List.of(FieldDecl.ofInt("x", 0)),
                java.util.List.of(LocalDecl.ofInt("r", 0)));
        DynamicState s1 = new DynamicState(decl, 2);
        DynamicState s2 = new DynamicState(decl, 2);
        s1.setLocalInt(0, "r", 1);
        s2.setLocalInt(0, "r", 2);
        assertNotEquals(s1.toString(), s2.toString());
    }

    @Test
    /** Tests guardEvalError_isViolationNotDeadlock. */
    void guardEvalError_isViolationNotDeadlock() {
        String json = """
            {
              "format": "declarative",
              "name": "guard-oob",
              "state": {"fields": [{"name": "arr", "type": "int[]", "init": [0,0]}]},
              "threads": [
                {"id": 0, "steps": [{"guard": "arr[5] == 0", "effects": ["arr[0] = 1"]}]}
              ]
            }
            """;
        BenchmarkProgram prog = loader.load(json);
        DfsResult res = new DfsExplorer().explore(prog.program());
        assertTrue(res.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION));
        assertFalse(res.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.DEADLOCK));
    }

    @Test
    /** Tests fieldDecl_defensiveCopyAndEquals. */
    void fieldDecl_defensiveCopyAndEquals() {
        int[] src = new int[]{1, 2, 3};
        FieldDecl a = FieldDecl.ofArray("arr", src);
        FieldDecl b = FieldDecl.ofArray("arr", new int[]{1, 2, 3});
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        src[0] = 99;
        assertEquals(1, a.arrayInit()[0]);
        int[] out = a.arrayInit();
        out[0] = 99;
        assertEquals(1, a.arrayInit()[0]);
    }

    @Test
    /** Tests parserDeepNesting_doesNotStackOverflow. */
    void parserDeepNesting_doesNotStackOverflow() {
        // 500 nested parens around true
        StringBuilder sb = new StringBuilder("true");
        for (int i = 0; i < 500; i++) sb.insert(0, "(").append(")");
        String expr = sb.toString();
        String json = """
            {
              "format": "declarative",
              "name": "deep-nest",
              "state": {"fields": [{"name": "x", "type": "bool", "init": false}]},
              "threads": [{"id": 0, "steps": [{"effects": ["x = %s"]}]}]
            }
            """.formatted(expr);
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().toLowerCase().contains("depth"));
    }

    @Test
    /** Tests programLoader_missingName_declarativeThrows. */
    void programLoader_missingName_declarativeThrows() {
        String json = """
            {
              "format": "declarative",
              "state": {"fields": [{"name": "x", "type": "int", "init": 0}]},
              "threads": [{"id": 0, "steps": [{"effects": ["x = 1"]}]}]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().toLowerCase().contains("name"));
    }

    @Test
    /** Tests programLoader_nullStep_declarativeThrows. */
    void programLoader_nullStep_declarativeThrows() {
        String json = """
            {
              "format": "declarative",
              "name": "null-step",
              "state": {"fields": [{"name": "x", "type": "int", "init": 0}]},
              "threads": [{"id": 0, "steps": [null]}]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().toLowerCase().contains("null"));
    }

    @Test
    /** Tests porousIndependence_mixedDynamicConstant_agreesAcrossExplorers. */
    void porousIndependence_mixedDynamicConstant_agreesAcrossExplorers() {
        String json = """
            {
              "format": "declarative",
              "name": "por-mixed",
              "state": {
                "fields": [
                  {"name": "arr", "type": "int[]", "init": [0,0]},
                  {"name": "a", "type": "int", "init": 0}
                ]
              },
              "threads": [
                {"id": 0, "steps": [{"effects": ["arr[a] = 1"]}]},
                {"id": 1, "steps": [{"effects": ["arr[0] = 1"]}]},
                {"id": 2, "steps": [{"effects": ["a = 1"]}]}
              ],
              "invariant": {"expr": "false"}
            }
            """;
        BenchmarkProgram prog = loader.load(json);
        DfsExplorer dfs = new DfsExplorer();
        dev.samhb.interleave.por.StaticPorExplorer por = new dev.samhb.interleave.por.StaticPorExplorer();
        dev.samhb.interleave.dpor.DporExplorer dpor = new dev.samhb.interleave.dpor.DporExplorer();
        boolean dfsViol = dfs.explore(prog.program(), prog.invariant().orElse(null)).traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION);
        boolean porViol = por.explore(prog.program(), prog.invariant().orElse(null)).traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION);
        boolean dporViol = dpor.explore(prog.program(), prog.invariant().orElse(null)).traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION);
        assertEquals(dfsViol, porViol);
        assertEquals(dfsViol, dporViol);
    }
}
