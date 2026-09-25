package dev.samhb.interleave.format.dsl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.ModelThread;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.core.Step;
import dev.samhb.interleave.format.model.ProgramDefinition;
import dev.samhb.interleave.format.model.ThreadDefinition;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.search.Invariant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loader for declarative format.
 */
public final class DslLoader {

    private DslLoader() {}

    /**
     * Loads declarative program.
     *
     * @param def program definition with format declarative
     * @return benchmark program
     * @throws RegistryException on validation error
     */
    public static BenchmarkProgram load(ProgramDefinition def) {
        // validate state
        if (def.state() == null) throw new RegistryException("State definition is required");
        JsonObject stateJson = def.state();
        StateDecl decl = parseStateDecl(stateJson);

        int threadCount = def.threads().size();
        if (threadCount < 1 || threadCount > 8) throw new RegistryException("threads must have 1..8 entries");

        // Check total steps limit 512
        int totalSteps = 0;
        for (ThreadDefinition td : def.threads()) totalSteps += td.steps().size();
        if (totalSteps > 512) throw new RegistryException("Total steps must be <= 512, got " + totalSteps);

        // Build DynamicState initial
        DynamicState initial = new DynamicState(decl, threadCount);

        // Parse threads steps
        List<ModelThread> threads = new ArrayList<>();
        int totalNodes = 0;

        for (int i = 0; i < def.threads().size(); i++) {
            ThreadDefinition td = def.threads().get(i);
            if (td.steps() == null || td.steps().isEmpty()) throw new RegistryException("Thread " + i + " must have at least one step");
            if (td.steps().size() > 64) throw new RegistryException("Thread " + i + " steps must be <= 64");
            List<Step> steps = new ArrayList<>();
            for (int sIdx = 0; sIdx < td.steps().size(); sIdx++) {
                JsonObject stepJson = td.steps().get(sIdx);
                String path = "threads[" + i + "].steps[" + sIdx + "]";
                // reject typed keys
                if (stepJson.has("type")) throw new RegistryException("Unknown field 'type' at " + path + ".type (typed steps not allowed in declarative)");
                if (stepJson.has("other") || stepJson.has("value") || stepJson.has("thread")) {
                    throw new RegistryException("Unknown field at " + path + " (only effects, guard, name allowed)");
                }
                // effects required
                if (!stepJson.has("effects")) throw new RegistryException("Missing required 'effects' at " + path);
                JsonElement effEl = stepJson.get("effects");
                if (!effEl.isJsonArray()) throw new RegistryException("'effects' must be array at " + path);
                JsonArray effArr = effEl.getAsJsonArray();
                if (effArr.size() < 1 || effArr.size() > 16) throw new RegistryException("'effects' must have 1..16 entries at " + path);
                List<Effect> effects = new ArrayList<>();
                for (int eIdx = 0; eIdx < effArr.size(); eIdx++) {
                    JsonElement eEl = effArr.get(eIdx);
                    if (!eEl.isJsonPrimitive() || !eEl.getAsJsonPrimitive().isString()) throw new RegistryException("Effect must be string at " + path + ".effects[" + eIdx + "]");
                    String effStr = eEl.getAsString();
                    if (effStr.isBlank()) throw new RegistryException("Effect must be non-empty at " + path + ".effects[" + eIdx + "]");
                    // duplicate '=' already handled by parser, but check identifier length already
                    Effect eff;
                    try {
                        eff = new Parser(effStr).parseEffect();
                    } catch (RegistryException ex) {
                        throw new RegistryException("Invalid effect at " + path + ".effects[" + eIdx + "]: " + ex.getMessage(), ex);
                    }
                    // type-check lhs/rhs
                    // check lhs type matches rhs type
                    // lhs: field/local/array
                    // rhs must be valid and type matches lhs type
                    Lhs lhs = eff.lhs();
                    Expr rhs = eff.rhs();
                    // check rhs type
                    FieldType rhsType;
                    try {
                        rhsType = TypeChecker.check(rhs, decl);
                    } catch (RegistryException ex) {
                        throw new RegistryException("Type error at " + path + ".effects[" + eIdx + "]: " + ex.getMessage(), ex);
                    }
                    // check lhs type and compare
                    if (lhs instanceof Lhs.FieldLhs fl) {
                        FieldDecl fd = decl.findField(fl.name());
                        if (fd == null) throw new RegistryException("Unknown field '" + fl.name() + "' at " + path + ".effects[" + eIdx + "]");
                        FieldType expected = fd.type() == FieldType.INT_ARRAY ? FieldType.INT : fd.type(); // array element is int
                        if (fd.type() == FieldType.INT_ARRAY) expected = FieldType.INT; // array element assignment expects int
                        else if (fd.type() == FieldType.INT && rhsType != FieldType.INT) throw new RegistryException("Type mismatch at " + path + ".effects[" + eIdx + "]: field '" + fl.name() + "' is int, rhs is " + rhsType);
                        else if (fd.type() == FieldType.BOOL && rhsType != FieldType.BOOL) throw new RegistryException("Type mismatch at " + path + ".effects[" + eIdx + "]: field '" + fl.name() + "' is bool, rhs is " + rhsType);
                        // for INT_ARRAY field itself, lhs must be array element, not scalar - but FieldLhs would be scalar field; that case would be type mismatch if field is array and lhs is scalar
                        if (fd.type() == FieldType.INT_ARRAY) throw new RegistryException("Array field '" + fl.name() + "' requires index: use " + fl.name() + "[expr] at " + path + ".effects[" + eIdx + "]");
                    } else if (lhs instanceof Lhs.LocalLhs ll) {
                        LocalDecl ld = decl.findLocal(ll.name());
                        if (ld == null) throw new RegistryException("Unknown local '" + ll.name() + "' at " + path + ".effects[" + eIdx + "]");
                        if (ld.type() != rhsType) throw new RegistryException("Type mismatch at " + path + ".effects[" + eIdx + "]: local '" + ll.name() + "' is " + ld.type() + ", rhs is " + rhsType);
                    } else if (lhs instanceof Lhs.ArrayLhs al) {
                        FieldDecl fd = decl.findField(al.arrayName());
                        if (fd == null || fd.type() != FieldType.INT_ARRAY) throw new RegistryException("Unknown array field '" + al.arrayName() + "' at " + path + ".effects[" + eIdx + "]");
                        if (rhsType != FieldType.INT) throw new RegistryException("Array element assignment requires int at " + path + ".effects[" + eIdx + "]");
                        // check index type
                        try {
                            TypeChecker.requireInt(al.index(), decl);
                        } catch (RegistryException ex) {
                            throw new RegistryException("Array index must be int at " + path + ".effects[" + eIdx + "]: " + ex.getMessage(), ex);
                        }
                        int d = Expr.depth(al.index());
                        int n = Expr.nodeCount(al.index());
                        if (d > 16) throw new RegistryException("Expression depth exceeds 16 at " + path + ".effects[" + eIdx + "] lhs index");
                        if (n > 5000) throw new RegistryException("Expression nodes exceed bound at " + path + ".effects[" + eIdx + "]");
                        totalNodes += n;
                        if (totalNodes > 5000) throw new RegistryException("Total AST node count exceeds 5000");
                        // also check depth for index
                    }
                    int d = Expr.depth(rhs);
                    int n = Expr.nodeCount(rhs);
                    if (d > 16) throw new RegistryException("Expression depth exceeds 16 at " + path + ".effects[" + eIdx + "]");
                    totalNodes += n;
                    if (totalNodes > 5000) throw new RegistryException("Total AST node count exceeds 5000");
                    effects.add(eff);
                }
                // guard optional
                Expr guard = null;
                if (stepJson.has("guard")) {
                    JsonElement gEl = stepJson.get("guard");
                    if (!gEl.isJsonNull()) {
                        if (!gEl.isJsonPrimitive() || !gEl.getAsJsonPrimitive().isString()) throw new RegistryException("'guard' must be string or null at " + path + ".guard");
                        String gStr = gEl.getAsString();
                        if (!gStr.isBlank()) {
                            try {
                                guard = new Parser(gStr).parseExpr();
                            } catch (RegistryException ex) {
                                throw new RegistryException("Invalid guard at " + path + ".guard: " + ex.getMessage(), ex);
                            }
                            try {
                                TypeChecker.requireBool(guard, decl);
                            } catch (RegistryException ex) {
                                throw new RegistryException("Guard must be bool at " + path + ".guard: " + ex.getMessage(), ex);
                            }
                            // forbid tid/local in guard? Actually guard may reference tid and locals and fields - allowed per spec (guard may reference locals)
                            // but invariant forbids local/tid; guard allows
                            int d = Expr.depth(guard);
                            int n = Expr.nodeCount(guard);
                            if (d > 16) throw new RegistryException("Guard depth exceeds 16 at " + path + ".guard");
                            totalNodes += n;
                            if (totalNodes > 5000) throw new RegistryException("Total AST node count exceeds 5000");
                            // check forbidding local array? already via type checker
                        } else {
                            // blank guard is error? treat as missing
                            throw new RegistryException("Guard must be non-empty string or null at " + path + ".guard");
                        }
                    }
                }
                String name = null;
                if (stepJson.has("name")) {
                    JsonElement nEl = stepJson.get("name");
                    if (!nEl.isJsonPrimitive() || !nEl.getAsJsonPrimitive().isString()) throw new RegistryException("'name' must be string at " + path);
                    name = nEl.getAsString();
                }
                // reject unknown keys beyond effects/guard/name
                for (String key : stepJson.keySet()) {
                    if (!key.equals("effects") && !key.equals("guard") && !key.equals("name")) {
                        throw new RegistryException("Unknown field '" + key + "' at " + path);
                    }
                }
                DynamicStep step = new DynamicStep(i, guard, effects, decl, name);
                steps.add(step);
            }
            threads.add(new ModelThread(i, steps));
        }

        // invariant
        Invariant invariant = null;
        if (def.invariant() != null) {
            JsonObject inv = def.invariant();
            if (inv.has("type")) throw new RegistryException("Use invariant.expr in format \"declarative\"; invariant.type is for format \"typed\" at invariant.type");
            if (inv.has("all")) throw new RegistryException("Use invariant.expr in format \"declarative\" for this spec; conjunction is Spec 10 at invariant.all");
            if (!inv.has("expr")) throw new RegistryException("Missing required 'expr' at invariant.expr");
            JsonElement eEl = inv.get("expr");
            if (!eEl.isJsonPrimitive() || !eEl.getAsJsonPrimitive().isString()) throw new RegistryException("'expr' must be string at invariant.expr");
            String exprStr = eEl.getAsString();
            if (exprStr.isBlank()) throw new RegistryException("'expr' must be non-empty at invariant.expr");
            Expr pred;
            try {
                pred = new Parser(exprStr).parseExpr();
            } catch (RegistryException ex) {
                throw new RegistryException("Invalid invariant at invariant.expr: " + ex.getMessage(), ex);
            }
            // invariant may not reference local.* or tid
            if (containsLocalOrTid(pred)) throw new RegistryException("Invariant must not reference local.* or tid at invariant.expr");
            try {
                TypeChecker.requireBool(pred, decl);
            } catch (RegistryException ex) {
                throw new RegistryException("Invariant must be bool at invariant.expr: " + ex.getMessage(), ex);
            }
            int d = Expr.depth(pred);
            int n = Expr.nodeCount(pred);
            if (d > 16) throw new RegistryException("Invariant depth exceeds 16 at invariant.expr");
            totalNodes += n;
            if (totalNodes > 5000) throw new RegistryException("Total AST node count exceeds 5000");
            // also check unknown keys in invariant
            for (String k : inv.keySet()) if (!k.equals("expr")) throw new RegistryException("Unknown field '" + k + "' at invariant");
            invariant = new DslInvariant(pred, decl);
        }

        Program program = new Program(initial, threads);
        String expectedVerdict = def.expectedVerdict();
        if (expectedVerdict != null) {
            String v = expectedVerdict.toUpperCase(java.util.Locale.ROOT);
            if (!v.equals("PASS") && !v.equals("VIOLATION") && !v.equals("DEADLOCK")) throw new RegistryException("Invalid expected_verdict '" + expectedVerdict + "'");
            expectedVerdict = v;
        }
        if (invariant != null && expectedVerdict != null) return new BenchmarkProgram(def.name(), program, expectedVerdict, invariant);
        else if (expectedVerdict != null) return new BenchmarkProgram(def.name(), program, expectedVerdict);
        else if (invariant != null) return new BenchmarkProgram(def.name(), program, null, invariant);
        else return new BenchmarkProgram(def.name(), program);
    }

    private static boolean containsLocalOrTid(Expr e) {
        if (e instanceof Expr.LocalRef) return true;
        if (e instanceof Expr.TidRef) return true;
        if (e instanceof Expr.ArrayAccess a) {
            if (a.arrayName().startsWith("local.")) return true;
            return containsLocalOrTid(a.index());
        }
        if (e instanceof Expr.UnaryOp u) return containsLocalOrTid(u.operand());
        if (e instanceof Expr.BinaryOp b) return containsLocalOrTid(b.left()) || containsLocalOrTid(b.right());
        return false;
    }

    private static StateDecl parseStateDecl(JsonObject stateJson) {
        if (!stateJson.has("fields")) throw new RegistryException("Missing required 'fields' at state.fields");
        JsonElement fieldsEl = stateJson.get("fields");
        if (!fieldsEl.isJsonArray()) throw new RegistryException("'fields' must be array at state.fields");
        JsonArray fieldsArr = fieldsEl.getAsJsonArray();
        if (fieldsArr.size() < 1 || fieldsArr.size() > 32) throw new RegistryException("'fields' must have 1..32 entries at state.fields");
        List<FieldDecl> fields = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < fieldsArr.size(); i++) {
            JsonElement el = fieldsArr.get(i);
            if (!el.isJsonObject()) throw new RegistryException("Field must be object at state.fields[" + i + "]");
            JsonObject o = el.getAsJsonObject();
            String path = "state.fields[" + i + "]";
            if (!o.has("name")) throw new RegistryException("Missing 'name' at " + path);
            if (!o.has("type")) throw new RegistryException("Missing 'type' at " + path);
            if (!o.has("init")) throw new RegistryException("Missing 'init' at " + path);
            JsonElement nEl = o.get("name");
            JsonElement tEl = o.get("type");
            JsonElement initEl = o.get("init");
            if (!nEl.isJsonPrimitive() || !nEl.getAsJsonPrimitive().isString()) throw new RegistryException("'name' must be string at " + path);
            if (!tEl.isJsonPrimitive() || !tEl.getAsJsonPrimitive().isString()) throw new RegistryException("'type' must be string at " + path);
            String name = nEl.getAsString();
            String typeStr = tEl.getAsString();
            validateName(name, path);
            if (names.contains(name)) throw new RegistryException("Duplicate field/local name '" + name + "' at " + path);
            names.add(name);
            if (name.equals("local") || name.equals("tid")) throw new RegistryException("Reserved name '" + name + "' at " + path);
            FieldDecl decl;
            switch (typeStr) {
                case "int" -> {
                    if (!initEl.isJsonPrimitive() || !initEl.getAsJsonPrimitive().isNumber()) throw new RegistryException("'init' must be int at " + path);
                    int v = initEl.getAsInt();
                    decl = FieldDecl.ofInt(name, v);
                }
                case "bool" -> {
                    if (!initEl.isJsonPrimitive() || !initEl.getAsJsonPrimitive().isBoolean()) throw new RegistryException("'init' must be bool at " + path);
                    boolean v = initEl.getAsBoolean();
                    decl = FieldDecl.ofBool(name, v);
                }
                case "int[]" -> {
                    if (!initEl.isJsonArray()) throw new RegistryException("'init' must be array at " + path);
                    JsonArray arr = initEl.getAsJsonArray();
                    if (arr.size() > 64) throw new RegistryException("Array length must be <=64 at " + path);
                    int[] vals = new int[arr.size()];
                    for (int k = 0; k < arr.size(); k++) {
                        if (!arr.get(k).isJsonPrimitive() || !arr.get(k).getAsJsonPrimitive().isNumber()) throw new RegistryException("Array element must be int at " + path + ".init[" + k + "]");
                        vals[k] = arr.get(k).getAsInt();
                    }
                    decl = FieldDecl.ofArray(name, vals);
                }
                default -> throw new RegistryException("Unknown type '" + typeStr + "' at " + path + ".type (valid: int, bool, int[])");
            }
            // unknown keys
            for (String k : o.keySet()) if (!k.equals("name") && !k.equals("type") && !k.equals("init")) throw new RegistryException("Unknown field '" + k + "' at " + path);
            fields.add(decl);
        }
        // locals optional
        List<LocalDecl> locals = new ArrayList<>();
        if (stateJson.has("locals")) {
            JsonElement localsEl = stateJson.get("locals");
            if (!localsEl.isJsonArray()) throw new RegistryException("'locals' must be array at state.locals");
            JsonArray localsArr = localsEl.getAsJsonArray();
            if (localsArr.size() > 8) throw new RegistryException("'locals' must have <=8 entries at state.locals");
            for (int i = 0; i < localsArr.size(); i++) {
                JsonElement el = localsArr.get(i);
                if (!el.isJsonObject()) throw new RegistryException("Local must be object at state.locals[" + i + "]");
                JsonObject o = el.getAsJsonObject();
                String path = "state.locals[" + i + "]";
                if (!o.has("name")) throw new RegistryException("Missing 'name' at " + path);
                if (!o.has("type")) throw new RegistryException("Missing 'type' at " + path);
                if (!o.has("init")) throw new RegistryException("Missing 'init' at " + path);
                String name = o.get("name").getAsString();
                String typeStr = o.get("type").getAsString();
                JsonElement initEl = o.get("init");
                validateName(name, path);
                if (names.contains(name)) throw new RegistryException("Duplicate field/local name '" + name + "' at " + path);
                names.add(name);
                if (name.equals("local") || name.equals("tid")) throw new RegistryException("Reserved name '" + name + "' at " + path);
                LocalDecl ld;
                switch (typeStr) {
                    case "int" -> {
                        if (!initEl.isJsonPrimitive() || !initEl.getAsJsonPrimitive().isNumber()) throw new RegistryException("'init' must be int at " + path);
                        ld = LocalDecl.ofInt(name, initEl.getAsInt());
                    }
                    case "bool" -> {
                        if (!initEl.isJsonPrimitive() || !initEl.getAsJsonPrimitive().isBoolean()) throw new RegistryException("'init' must be bool at " + path);
                        ld = LocalDecl.ofBool(name, initEl.getAsBoolean());
                    }
                    case "int[]" -> throw new RegistryException("Local type 'int[]' not allowed at " + path + ".type");
                    default -> throw new RegistryException("Unknown local type '" + typeStr + "' at " + path + ".type (valid: int, bool)");
                }
                for (String k : o.keySet()) if (!k.equals("name") && !k.equals("type") && !k.equals("init")) throw new RegistryException("Unknown field '" + k + "' at " + path);
                locals.add(ld);
            }
        }
        // reject unknown keys in state
        for (String k : stateJson.keySet()) if (!k.equals("fields") && !k.equals("locals")) throw new RegistryException("Unknown field '" + k + "' at state." + k);
        return new StateDecl(fields, locals);
    }

    private static void validateName(String name, String path) {
        if (name == null || name.isBlank()) throw new RegistryException("Name must be non-empty at " + path);
        if (name.length() > 64) throw new RegistryException("Identifier too long (>64) at " + path + ": " + name);
        if (!name.matches("[a-z][a-z0-9_]*")) throw new RegistryException("Invalid identifier '" + name + "' at " + path + ": must match [a-z][a-z0-9_]*");
    }
}
