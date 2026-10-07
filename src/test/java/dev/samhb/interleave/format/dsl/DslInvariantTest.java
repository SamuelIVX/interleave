package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.search.TraceOutcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for declarative invariant forms: expr, all, when, and load-error paths.
 */
class DslInvariantTest {
    private final ProgramLoader loader = new ProgramLoader();

    private String baseFields(String extraInvariant) {
        return """
            {
              "format": "declarative",
              "name": "t",
              "state": {"fields": [{"name": "x", "type": "int", "init": 0}, {"name": "y", "type": "int", "init": 0}]},
              "threads": [{"id": 0, "steps": [{"effects": ["x = 1"]}]}]
              %s
            }
            """.formatted(extraInvariant);
    }

    @Test
    void exprSinglePredicateValid() {
        String json = baseFields(", \"invariant\": {\"expr\": \"x == 0\"}");
        BenchmarkProgram prog = loader.load(json);
        assertNotNull(prog.invariant());
        assertInstanceOf(DslInvariant.class, prog.invariant().orElseThrow());
        DslInvariant inv = (DslInvariant) prog.invariant().orElseThrow();
        assertEquals(1, inv.predicates().size());
        assertEquals(DslInvariant.When.FINAL, inv.when());
    }

    @Test
    void allConjunctionValid() {
        String json = baseFields(", \"invariant\": {\"all\": [\"x >= 0\", \"y >= 0\"]}");
        BenchmarkProgram prog = loader.load(json);
        DslInvariant inv = (DslInvariant) prog.invariant().orElseThrow();
        assertEquals(2, inv.predicates().size());
    }

    @Test
    void allEmptyIsLoadError() {
        String json = baseFields(", \"invariant\": {\"all\": []}");
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("1..16") || ex.getMessage().toLowerCase().contains("empty") || ex.getMessage().contains("invariant.all"));
    }

    @Test
    void allOver16IsLoadError() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 17; i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"x >= 0\"");
        }
        String json = baseFields(", \"invariant\": {\"all\": [" + sb + "]}");
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("1..16") || ex.getMessage().contains("16"));
    }

    @Test
    void exprMustBeBoolTypeError() {
        String json = baseFields(", \"invariant\": {\"expr\": \"x + 1\"}");
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().toLowerCase().contains("bool"));
    }

    @Test
    void allNonBoolIsLoadError() {
        String json = baseFields(", \"invariant\": {\"all\": [\"x >= 0\", \"x + 1\"]}");
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().toLowerCase().contains("bool") && ex.getMessage().contains("all[1]"));
    }

    @Test
    void invariantMustNotReferenceLocalExpr() {
        String json = """
            {
              "format": "declarative",
              "name": "t",
              "state": {
                "fields": [{"name": "x", "type": "int", "init": 0}],
                "locals": [{"name": "r", "type": "int", "init": 0}]
              },
              "threads": [{"id": 0, "steps": [{"effects": ["local.r = 1"]}]}],
              "invariant": {"expr": "local.r == 0"}
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("local"));
        assertTrue(ex.getMessage().contains("invariant.expr"));
    }

    @Test
    void invariantMustNotReferenceLocalInAll() {
        String json = """
            {
              "format": "declarative",
              "name": "t",
              "state": {
                "fields": [{"name": "x", "type": "int", "init": 0}],
                "locals": [{"name": "r", "type": "int", "init": 0}]
              },
              "threads": [{"id": 0, "steps": [{"effects": ["local.r = 1"]}]}],
              "invariant": {"all": ["x >= 0", "local.r == 0"]}
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("local") && ex.getMessage().contains("all[1]"));
    }

    @Test
    void invariantMustNotReferenceTidExpr() {
        String json = baseFields(", \"invariant\": {\"expr\": \"tid == 0\"}");
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("tid"));
    }

    @Test
    void invariantMustNotReferenceTidInAll() {
        String json = baseFields(", \"invariant\": {\"all\": [\"x == 0\", \"tid == 1\"]}");
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("tid") && ex.getMessage().contains("all[1]"));
    }

    @Test
    void invariantTypeInDeclarativeIsLoadError() {
        String json = """
            {
              "format": "declarative",
              "name": "t",
              "state": {"fields": [{"name": "x", "type": "int", "init": 0}]},
              "threads": [{"id": 0, "steps": [{"effects": ["x = 1"]}]}],
              "invariant": {"type": "counter_equals", "expected": 1}
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("invariant.type") && ex.getMessage().contains("declarative"));
    }

    @Test
    void typedWithInvariantExprIsLoadError() {
        String json = """
            {
              "format": "typed",
              "name": "t",
              "state": {"type": "counter", "counter": 0},
              "threads": [{"id": 0, "steps": [{"type": "read_counter"}]}],
              "invariant": {"expr": "counter == 1"}
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("invariant.expr") || ex.getMessage().toLowerCase().contains("typed"));
    }

    @Test
    void typedWithInvariantAllIsLoadError() {
        String json = """
            {
              "format": "typed",
              "name": "t",
              "state": {"type": "counter", "counter": 0},
              "threads": [{"id": 0, "steps": [{"type": "read_counter"}]}],
              "invariant": {"all": ["counter == 1"]}
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("invariant.all"));
    }

    @Test
    void invariantUnknownKeyIsLoadError() {
        String json = baseFields(", \"invariant\": {\"expr\": \"x == 0\", \"unknown\": 1}");
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("Unknown field") && ex.getMessage().contains("invariant.unknown"));
    }

    @Test
    void invariantMustHaveExactlyOneOfExprAll() {
        String json1 = baseFields(", \"invariant\": {\"expr\": \"x == 0\", \"all\": [\"x == 0\"]}");
        RegistryException ex1 = assertThrows(RegistryException.class, () -> loader.load(json1));
        assertTrue(ex1.getMessage().toLowerCase().contains("exactly one"));

        String json2 = baseFields(", \"invariant\": {\"when\": \"final\"}");
        RegistryException ex2 = assertThrows(RegistryException.class, () -> loader.load(json2));
        assertTrue(ex2.getMessage().toLowerCase().contains("exactly one"));
    }



    @Test
    void whenAlwaysParses() {
        String json = baseFields(", \"invariant\": {\"expr\": \"x == 0\", \"when\": \"always\"}");
        DslInvariant inv = (DslInvariant) loader.load(json).invariant().orElseThrow();
        assertEquals(DslInvariant.When.ALWAYS, inv.when());
    }

    @Test
    void whenExplicitFinalParses() {
        String json = baseFields(", \"invariant\": {\"expr\": \"x == 0\", \"when\": \"final\"}");
        DslInvariant inv = (DslInvariant) loader.load(json).invariant().orElseThrow();
        assertEquals(DslInvariant.When.FINAL, inv.when());
    }

    @Test
    void whenInvalidValueIsLoadError() {
        String json = baseFields(", \"invariant\": {\"expr\": \"x == 0\", \"when\": \"sometimes\"}");
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("when") && ex.getMessage().contains("final"));
    }

    @Test
    void whenMustBeString() {
        String json = baseFields(", \"invariant\": {\"expr\": \"x == 0\", \"when\": 123}");
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("when") && ex.getMessage().toLowerCase().contains("string"));
    }

    @Test
    void invariantUnknownFieldInAllIsLoadError() {
        String json = baseFields(", \"invariant\": {\"all\": [\"x == 0\", \"unknownField == 1\"]}");
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().toLowerCase().contains("unknown field") || ex.getMessage().contains("all[1]"));
    }

    @Test
    void invariantDepthBoundEnforced() {
        StringBuilder sb = new StringBuilder("flag");
        for (int i = 0; i < 17; i++) sb.insert(0, "!");
        String deep = sb.toString();
        String json = """
            {
              "format": "declarative",
              "name": "t",
              "state": {"fields": [{"name": "flag", "type": "bool", "init": false}]},
              "threads": [{"id": 0, "steps": [{"effects": ["flag = true"]}]}],
              "invariant": {"expr": "%s"}
            }
            """.formatted(deep);
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().toLowerCase().contains("depth"));
    }

    @Test
    void invariantAllDepthBoundEnforced() {
        StringBuilder sb = new StringBuilder("flag");
        for (int i = 0; i < 17; i++) sb.insert(0, "!");
        String deep = sb.toString();
        String json = """
            {
              "format": "declarative",
              "name": "t",
              "state": {"fields": [{"name": "flag", "type": "bool", "init": false}]},
              "threads": [{"id": 0, "steps": [{"effects": ["flag = true"]}]}],
              "invariant": {"all": ["flag == true", "%s"]}
            }
            """.formatted(deep);
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("all[1]") && ex.getMessage().toLowerCase().contains("depth"));
    }

    @Test
    void whenAlwaysCatchesTransientWhileFinalDoesNot() {
        // Program: x starts 0, thread 0: x=1 then x=0; invariant x==0
        // With when:final -> final state x==0 holds, no violation
        // With when:always -> intermediate state x==1 violates
        String jsonFinal = """
            {
              "format": "declarative",
              "name": "when-final",
              "state": {"fields": [{"name": "x", "type": "int", "init": 0}]},
              "threads": [{"id": 0, "steps": [{"effects": ["x = 1"]}, {"effects": ["x = 0"]}]}],
              "invariant": {"expr": "x == 0"}
            }
            """;
        String jsonAlways = """
            {
              "format": "declarative",
              "name": "when-always",
              "state": {"fields": [{"name": "x", "type": "int", "init": 0}]},
              "threads": [{"id": 0, "steps": [{"effects": ["x = 1"]}, {"effects": ["x = 0"]}]}],
              "invariant": {"expr": "x == 0", "when": "always"}
            }
            """;
        BenchmarkProgram progFinal = loader.load(jsonFinal);
        BenchmarkProgram progAlways = loader.load(jsonAlways);

        DfsResult resFinal = new DfsExplorer().explore(progFinal.program(), progFinal.invariant().orElse(null));
        assertFalse(resFinal.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION),
                "when:final should not report transient violation");

        DfsResult resAlways = new DfsExplorer().explore(progAlways.program(), progAlways.invariant().orElse(null));
        assertTrue(resAlways.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION),
                "when:always should catch transient violation");
    }

    /** Both a false conjunct and an evaluated array error produce safety violations. */
    @Test
    void falseConjunctAndEvaluationErrorBothReportViolation() {
        String json = """
            {
              "format": "declarative",
              "name": "short-circuit",
              "state": {
                "fields": [
                  {"name": "x", "type": "int", "init": 0},
                  {"name": "arr", "type": "int[]", "init": [1,2]}
                ]
              },
              "threads": [{"id": 0, "steps": [{"effects": ["x = 1"]}]}],
              "invariant": {"all": ["x == 1", "arr[5] == 0"], "when": "always"}
            }
            """;


        BenchmarkProgram prog = loader.load(json);
        DfsResult res = new DfsExplorer().explore(prog.program(), prog.invariant().orElse(null));
        assertTrue(res.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION));


        String json2 = """
            {
              "format": "declarative",
              "name": "short-circuit2",
              "state": {
                "fields": [
                  {"name": "x", "type": "int", "init": 0},
                  {"name": "arr", "type": "int[]", "init": [1,2]}
                ]
              },
              "threads": [{"id": 0, "steps": [{"effects": ["x = 0"]}]}],
              "invariant": {"all": ["x == 0", "arr[5] == 0"], "when": "always"}
            }
            """;
        BenchmarkProgram prog2 = loader.load(json2);
        DfsResult res2 = new DfsExplorer().explore(prog2.program(), prog2.invariant().orElse(null));
        assertTrue(res2.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION));
    }

    @Test
    void unknownFormatIsLoadError() {
        String json = "{\"format\": \"unknown\", \"name\": \"t\", \"state\": {\"type\": \"counter\", \"counter\": 0}, \"threads\": [{\"id\": 0, \"steps\": [{\"type\": \"read_counter\"}]}]}";
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("Unknown format"));
    }

    @Test
    void duplicateFieldNameIsLoadError() {
        String json = """
            {
              "format": "declarative",
              "name": "t",
              "state": {
                "fields": [
                  {"name": "x", "type": "int", "init": 0},
                  {"name": "x", "type": "int", "init": 1}
                ]
              },
              "threads": [{"id": 0, "steps": [{"effects": ["x = 1"]}]}]
            }
            """;
        RegistryException ex = assertThrows(RegistryException.class, () -> loader.load(json));
        assertTrue(ex.getMessage().contains("Duplicate"));
    }
}
