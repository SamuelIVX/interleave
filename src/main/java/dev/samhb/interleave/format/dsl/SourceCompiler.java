/** Parses bounded source and lowers it into immutable forward instructions. */
package dev.samhb.interleave.format.dsl;

import com.google.gson.*;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.search.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** One bounded, call-local frontend; expression semantics remain in the existing DSL. */
public final class SourceCompiler {
    /** Reserved source words cannot shadow declarations. */
    private static final Set<String> RESERVED = Set.of("program", "shared", "local", "thread", "atomic", "when",
        "if", "else", "repeat", "while", "limit", "invariant", "always", "final", "expected", "int", "bool", "true", "false", "tid");
    /** Original source and same-length comment-masked parsing text. */
    private final String source, clean, name;
    /** Token spans, including the terminal EOF token. */
    private final List<Token> tokens;
    /** Immutable diagnostic source map shared with compiled instructions. */
    private final SourceText sourceText;
    /** Earlier immutable helper definitions; declaration order makes calls acyclic. */
    private final Map<String, Helper> helpers = new LinkedHashMap<>();
    /** All written tokens; generated names cannot be forged by source. */
    private final Set<String> occupiedNames;
    /** Read-only formals visible only while parsing their helper definition. */
    private Map<String, Parameter> parameterScope = Map.of();
    /** Declared result type and parsed final return for the current helper only. */
    private FieldType resultType;
    private ReturnValue returned;
    /** Monotonic candidate suffix bounds fresh-name allocation by source tokens plus slot count. */
    private int nextSlot;
    /** Current token and validated declarations. */
    private int position;
    /** Shared state shape. */
    private StateDecl decl;

    /**
     * Prepares one source compilation with bounded text supplied by SourceProgramLoader.
     * @param source original text
     * @param name diagnostic file path or in-memory marker
     */
    public SourceCompiler(String source, String name) {
        this.source = source;
        this.name = name;
        this.sourceText = new SourceText(source, name);
        this.clean = maskComments();
        this.tokens = tokenize();
        this.occupiedNames = new HashSet<>(tokens.stream().map(Token::text).toList());
    }

    /**
     * Compiles a fully consumed source program.
     * @return validated benchmark definition and forward model
     * @throws RegistryException for invalid or oversized input
     */
    public BenchmarkProgram compile() {
        expect("program");
        String programName = identifier();
        expect("{");
        JsonArray fields = new JsonArray(), locals = new JsonArray();
        List<Integer> fieldOffsets = new ArrayList<>(), localOffsets = new ArrayList<>();
        Set<String> names = new HashSet<>();
        if (!peek("shared")) throw error(current().start(), "syntax", "Expected a shared declaration");
        while (peek("shared")) {
            if (fields.size() == 32) throw error(current().start(), "limit", "At most 32 shared declarations");
            fieldOffsets.add(current().start());
            fields.add(declaration("shared", names));
        }
        while (peek("local")) {
            if (locals.size() == 8) throw error(current().start(), "limit", "At most 8 local declarations");
            localOffsets.add(current().start());
            locals.add(declaration("local", names));
        }
        JsonObject state = new JsonObject(); state.add("fields", fields); state.add("locals", locals);
        try { decl = DslLoader.parseStateDecl(state); }
        catch (RegistryException e) {
            var path = java.util.regex.Pattern.compile("state\\.(fields|locals)(?:\\[([0-9]+)\\])?").matcher(e.getMessage());
            int offset = fieldOffsets.getFirst();
            if (path.find()) {
                List<Integer> offsets = path.group(1).equals("fields") ? fieldOffsets : localOffsets;
                int index = path.group(2) == null ? 0 : Integer.parseInt(path.group(2));
                if (index < offsets.size()) offset = offsets.get(index);
            }
            String message = e.getMessage().replaceAll("state\\.(fields|locals)(?:\\[[0-9]+\\])?(?:\\.init(?:\\[[0-9]+\\])?)?", "source declaration");
            throw error(offset, "type", message);
        }
        while (take("helper")) {
            int start = previous().start();
            if (helpers.size() == 16) throw error(start, "limit", "At most 16 helper declarations");
            String id = identifier();
            if (!names.add(id)) throw error(start, "name", "Duplicate declaration '" + id + "'");
            List<Parameter> parameters = parameters(id, names);
            resultType = take("-") ? returnType() : null;
            returned = null;
            parameterScope = parameters.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Parameter::name, value -> value));
            List<Node> body = block(false, 1);
            if (resultType != null && returned == null) throw error(start, "syntax", "Value helpers require one final top-level return");
            long actions = work(body), expressions = expressionWork(body);
            if (!parameters.isEmpty()) actions = cappedAdd(actions, 1, 64);
            if (!parameters.isEmpty() || returned != null) actions = cappedAdd(actions, 1, 64);
            expressions = cappedAdd(expressions, 3L * parameters.size(), 5000);
            if (returned != null) expressions = cappedAdd(expressions, Expr.nodeCount(returned.expression()) + 1L, 5000);
            if (actions > 64) throw error(start, "limit", "Expanded helper instructions exceed 64");
            if (expressions > 5000) throw error(start, "limit", "Expanded helper expression nodes exceed 5000");
            checkHelperAtomics(body);
            helpers.put(id, new Helper(id, parameters, resultType, returned, body, actions, expressions));
            parameterScope = Map.of(); resultType = null; returned = null;
        }
        List<List<Node>> bodies = new ArrayList<>();
        List<Integer> threadOffsets = new ArrayList<>();
        while (take("thread")) {
            threadOffsets.add(previous().start());
            int offset = current().start();
            int id = count();
            if (id != bodies.size()) throw error(offset, "name", "Thread IDs must be sequential from 0");
            if (bodies.size() == 8) throw error(offset, "limit", "At most 8 threads");
            bodies.add(block(false, 1));
        }
        if (bodies.isEmpty()) throw error(current().start(), "syntax", "At least one thread is required");
        Invariant invariant = null;
        int propertyNodes = 0;
        if (take("invariant")) {
            int start = previous().start();
            boolean always = take("always");
            if (!always) expect("final");
            Expr property = condition();
            Set<MemoryLocation> reads = new HashSet<>(); ExpressionReads.collect(property, 0, reads);
            if (containsLocalOrTid(property)) throw error(start, "name", "Properties cannot reference locals or tid");
            expect(";");
            invariant = new SourceProperty(property, always, location(start, previous().end()), reads);
            propertyNodes = Expr.nodeCount(property);
        }
        String expected = null;
        if (take("expected")) {
            expected = current().text();
            if (!Set.of("PASS", "VIOLATION", "DEADLOCK").contains(expected)) throw error(current().start(), "syntax", "Expected a verdict");
            position++; expect(";");
        }
        expect("}"); expect("");
        List<ModelThread> threads = new ArrayList<>();
        long totalNodes = propertyNodes;
        for (int owner = 0; owner < bodies.size(); owner++) {
            List<Node> body = bodies.get(owner);
            if (work(body) > 64) throw error(threadOffsets.get(owner), "limit", "Expanded thread instructions exceed 64");
            totalNodes = cappedAdd(totalNodes, expressionWork(body), 5000);
            if (totalNodes > 5000) throw error(threadOffsets.get(owner), "limit", "Expanded expression nodes exceed 5000");
            List<Node> expanded = expand(body, List.of());
            List<Draft> drafts = new ArrayList<>();
            Fragment whole = lower(expanded, drafts, "");
            patch(whole.exits(), drafts.size(), drafts);
            List<Step> instructions = new ArrayList<>();
            for (int pc = 0; pc < drafts.size(); pc++) {
                Draft d = drafts.get(pc);
                if (d.yes <= pc || d.yes > drafts.size() || d.no <= pc || d.no > drafts.size()) {
                    throw new IllegalStateException("Compiler produced an invalid forward target");
                }
                instructions.add(new SourceInstruction(d.node, d.yes, d.no, owner, decl, sourceText, d.callLabel));
            }
            threads.add(new ModelThread(owner, instructions));
        }
        return new BenchmarkProgram(programName, new Program(new DynamicState(decl, threads.size()), threads, digest()), expected, invariant);
    }

    /** Parses scalar formals and charges their hygienic slots to the existing eight-local cap.
     * @param helper source helper name used only for readable generated names
     * @param names declared shared/local/helper names that formals cannot shadow
     * @return immutable positional formals; empty for legacy syntax
     * @throws RegistryException for invalid types/names, collisions or excess local slots
     */
    private List<Parameter> parameters(String helper, Set<String> names) {
        if (!take("(")) return List.of();
        List<Parameter> result = new ArrayList<>(); Set<String> formalNames = new HashSet<>();
        if (!peek(")")) do {
            FieldType type = scalarType(); int start = current().start(); String name = identifier();
            if (names.contains(name) || !formalNames.add(name)) throw error(start, "name", "Duplicate or shadowing helper parameter '" + name + "'");
            if (decl.locals().size() == 8) throw error(start, "limit", "Expanded local slots exceed 8");
            String prefix = "helper_" + helper + "_" + name;
            prefix = prefix.substring(0, Math.min(prefix.length(), 48)) + "_";
            String slot = freshSlot(prefix);
            List<LocalDecl> locals = new ArrayList<>(decl.locals()); locals.add(new LocalDecl(slot, type, 0, false));
            decl = new StateDecl(decl.fields(), locals);
            result.add(new Parameter(name, type, slot));
        } while (take(","));
        expect(")"); return List.copyOf(result);
    }

    /** Finds a fresh slot within the source-name bound; monotonic suffixes separate all allocations.
     * @param prefix valid bounded identifier prefix ending in an underscore
     * @return unique name absent from every written token
     * @throws IllegalStateException if the bounded fresh-name invariant is violated
     */
    private String freshSlot(String prefix) {
        for (int attempt = 0; attempt <= occupiedNames.size(); attempt++) {
            String slot = prefix + nextSlot++;
            if (!occupiedNames.contains(slot)) return slot;
        }
        throw new IllegalStateException("Cannot allocate a helper slot within the source-name bound");
    }

    /** Reads a scalar helper type without enabling array parameters or results.
     * @return int or bool
     * @throws RegistryException for an unknown or array type
     */
    private FieldType scalarType() {
        FieldType type;
        if (take("int")) type = FieldType.INT;
        else if (take("bool")) type = FieldType.BOOL;
        else throw error(current().start(), "type", "Helper scalar type must be int or bool");
        if (peek("[")) throw error(current().start(), "unsupported", "Helper parameters and results must be scalar");
        return type;
    }

    /** Completes the two-token return arrow and reads its scalar type.
     * @return declared result type
     * @throws RegistryException for malformed arrow or type
     */
    private FieldType returnType() { expect(">"); return scalarType(); }

    /** Parses and type-checks actual values/result targets even in unused or zero-repeat code.
     * @param helper earlier acyclic definition
     * @param start original call keyword offset for arity/type diagnostics
     * @return immutable invocation with saturated complete expression cost
     * @throws RegistryException for syntax, arity, type or target errors
     */
    private Invocation invocation(Helper helper, int start) {
        List<Argument> arguments = new ArrayList<>();
        if (take("(")) {
            if (!peek(")")) do {
                int begin = current().start();
                try {
                    Expr expression = boundExpression(untilAny(Set.of(",", ")"), "',' or ')'"), begin);
                    FieldType type = TypeChecker.check(expression, decl);
                    arguments.add(new Argument(expression, type, begin, current().start()));
                } catch (RegistryException e) { throw diagnostic(begin, "type", e); }
            } while (take(","));
            expect(")");
        }
        if (arguments.size() != helper.parameters().size()) throw error(start, "type", "Expected " + helper.parameters().size() + " helper arguments");
        for (int i = 0; i < arguments.size(); i++) {
            if (arguments.get(i).type() != helper.parameters().get(i).type()) {
                throw error(arguments.get(i).start(), "type", "Helper argument type mismatch");
            }
        }
        Lhs target = null;
        if (take("-")) {
            expect(">"); int begin = current().start();
            if (helper.resultType() == null) throw error(begin, "type", "Void helpers have no return target");
            target = take("local") ? localTarget() : new Lhs.FieldLhs(identifier());
            if (peek("[")) throw error(begin, "unsupported", "Helper results require scalar targets");
            try {
                target = bindEffect(new Effect(target, new Expr.IntLit(0))).lhs();
                if (TypeChecker.check(targetExpression(target), decl) != helper.resultType()) throw new RegistryException("Helper result target type mismatch");
            } catch (RegistryException e) { throw diagnostic(begin, "type", e); }
        } else if (helper.resultType() != null) throw error(current().start(), "type", "Value helpers require a return target");
        expect(";");
        long expressions = helper.expressionWork();
        for (Argument argument : arguments) expressions = cappedAdd(expressions, Expr.nodeCount(argument.expression()), 5000);
        return new Invocation(helper, List.copyOf(arguments), target, expressions);
    }

    /** Reads an explicit caller-local scalar target after the contextual local keyword.
     * @return source local assignment target
     * @throws RegistryException for malformed local reference
     */
    private Lhs localTarget() { expect("."); return new Lhs.LocalLhs(identifier()); }

    /** Checks hostile raw expression bounds before recursively binding read-only formal references.
     * @param text original comment-masked expression
     * @param start source offset used for bounded-validation diagnostics
     * @return expression using existing AST forms and owner-local formal slots
     * @throws RegistryException for malformed or over-budget expressions
     */
    private Expr boundExpression(String text, int start) {
        Expr expression = new Parser(text).parseExpr(); checkExpression(expression, start);
        return bindParameters(expression);
    }

    /** Rewrites lexical formal reads to hygienic slots without changing expression size or timing.
     * @param expression already depth/node-validated expression
     * @return bound immutable expression; unchanged outside a parameter scope
     */
    private Expr bindParameters(Expr expression) {
        if (parameterScope.isEmpty()) return expression;
        return switch (expression) {
            case Expr.VarRef v -> parameterScope.containsKey(v.name()) ? new Expr.LocalRef(parameterScope.get(v.name()).slot()) : v;
            case Expr.UnaryOp u -> new Expr.UnaryOp(u.op(), bindParameters(u.operand()));
            case Expr.BinaryOp b -> new Expr.BinaryOp(bindParameters(b.left()), b.op(), bindParameters(b.right()));
            case Expr.ArrayAccess a -> new Expr.ArrayAccess(a.arrayName(), bindParameters(a.index()));
            default -> expression;
        };
    }

    /** Binds index/RHS formal reads while rejecting all direct writes to a formal.
     * @param effect raw validated-depth assignment
     * @return immutable bound effect
     * @throws RegistryException if a read-only formal is used as an assignment destination
     */
    private Effect bindEffect(Effect effect) {
        if (parameterScope.isEmpty()) return effect;
        Lhs lhs = effect.lhs();
        if (lhs instanceof Lhs.FieldLhs f && parameterScope.containsKey(f.name())) throw new RegistryException("Helper parameters are read-only");
        if (lhs instanceof Lhs.ArrayLhs a) lhs = new Lhs.ArrayLhs(a.arrayName(), bindParameters(a.index()));
        else if (lhs instanceof Lhs.LocalArrayLhs a) lhs = new Lhs.LocalArrayLhs(a.localName(), bindParameters(a.index()));
        return new Effect(lhs, bindParameters(effect.rhs()));
    }

    /** Builds one simultaneous caller-state capture action; only inaccessible callee slots are written.
     * @param call parsed invocation and original call span
     * @param path enclosing expanded loop ancestry
     * @return ordinary trusted atomic capture action
     */
    private Node capture(Node call, List<ExecutedStep.Expansion> path) {
        List<Node> assignments = new ArrayList<>();
        for (int i = 0; i < call.invocation().arguments().size(); i++) {
            Argument argument = call.invocation().arguments().get(i);
            String slot = call.invocation().helper().parameters().get(i).slot();
            assignments.add(new Node(Kind.ASSIGN, new Effect(new Lhs.LocalLhs(slot), argument.expression()), null,
                List.of(), List.of(), 0, argument.start(), argument.end(), path, null));
        }
        return new Node(Kind.ATOMIC, null, null, List.copyOf(assignments), List.of(), 0, call.start(), call.end(), path, null);
    }

    /** Builds the sole return/reset commit, retaining definition spans for result evaluation errors.
     * @param call parsed invocation and optional scalar destination
     * @param path enclosing expanded loop ancestry
     * @return ordinary atomic return/reset action
     */
    private Node complete(Node call, List<ExecutedStep.Expansion> path) {
        Helper helper = call.invocation().helper(); ReturnValue value = helper.returned();
        int start = value == null ? call.start() : value.start(), end = value == null ? call.end() : value.end();
        List<Node> assignments = new ArrayList<>();
        if (value != null) assignments.add(new Node(Kind.ASSIGN, new Effect(call.invocation().target(), value.expression()), null,
            List.of(), List.of(), 0, start, end, path, null));
        for (Parameter parameter : helper.parameters()) {
            Expr zero = parameter.type() == FieldType.INT ? new Expr.IntLit(0) : new Expr.BoolLit(false);
            assignments.add(new Node(Kind.ASSIGN, new Effect(new Lhs.LocalLhs(parameter.slot()), zero), null,
                List.of(), List.of(), 0, start, end, path, null));
        }
        return new Node(Kind.ATOMIC, null, null, List.copyOf(assignments), List.of(), 0, start, end, path, null);
    }

    /** Parses one initialized declaration, preserving the shared DSL declaration validator.
     * @param scope expected shared/local keyword
     * @param names declaration names already used; updated on success
     * @return shared-validator input with a literal initializer
     * @throws RegistryException for names, shapes, types or an array above 64 elements
     */
    private JsonObject declaration(String scope, Set<String> names) {
        expect(scope);
        String type = current().text();
        if (!take("int") && !take("bool")) throw error(current().start(), "type", "Expected int or bool");
        if (take("[")) { expect("]"); if (!type.equals("int")) throw error(previous().start(), "type", "Only int arrays are supported"); type = "int[]"; }
        int start = current().start(); String id = identifier();
        if (!names.add(id)) throw error(start, "name", "Duplicate declaration '" + id + "'");
        expect("=");
        int initializerStart = current().start();
        JsonElement init;
        if (take("[")) {
            JsonArray array = new JsonArray();
            if (!peek("]")) do {
                if (array.size() == 64) throw error(current().start(), "limit", "Array length exceeds 64");
                array.add(signedInt());
            } while (take(","));
            expect("]"); init = array;
        } else if (take("true")) init = new JsonPrimitive(true);
        else if (take("false")) init = new JsonPrimitive(false);
        else init = new JsonPrimitive(signedInt());
        boolean matches = type.equals("int[]") ? init.isJsonArray()
            : init.isJsonPrimitive() && (type.equals("bool") ? init.getAsJsonPrimitive().isBoolean() : init.getAsJsonPrimitive().isNumber());
        if (!matches) throw error(initializerStart, "type", "Initializer for '" + id + "' must match " + type);
        expect(";");
        JsonObject result = new JsonObject(); result.addProperty("name", id); result.addProperty("type", type); result.add("init", init);
        return result;
    }

    /** Parses one braced body, distinguishing scheduled statements from atomic interiors.
     * @param insideAtomic whether statements belong to an outer action; unguarded inner blocks flatten
     * @param depth current braced-body depth relative to the enclosing program
     * @return immutable source-order syntax nodes
     * @throws RegistryException for invalid syntax/types or nesting beyond 16 program/body levels
     */
    private List<Node> block(boolean insideAtomic, int depth) {
        if (depth >= 16) throw error(current().start(), "limit", "Source block nesting exceeds 16");
        expect("{"); List<Node> nodes = new ArrayList<>();
        while (!peek("}")) {
            if (peek("")) throw error(current().start(), "syntax", "Expected '}' before EOF");
            int start = current().start();
            if (take("atomic")) {
                Expr guard = take("when") ? condition() : null;
                List<Node> body = block(true, depth + 1);
                // Inner braces group private work; only the outer action owns scheduling and rollback.
                if (insideAtomic && guard == null) nodes.addAll(body);
                else nodes.add(new Node(insideAtomic ? Kind.GUARD : Kind.ATOMIC, null, guard, body, List.of(), 0,
                    start, previous().end(), List.of(), null));
            } else if (take("if")) {
                Expr test = condition();
                List<Node> yes = block(insideAtomic, depth + 1);
                List<Node> no = take("else") ? block(insideAtomic, depth + 1) : List.of();
                nodes.add(new Node(Kind.IF, null, test, yes, no, 0, start, previous().end(), List.of(), null));
            } else if (take("repeat")) {
                int count = count(); List<Node> body = block(insideAtomic, depth + 1);
                nodes.add(new Node(Kind.REPEAT, null, null, body, List.of(), count, start, previous().end(), List.of(), null));
            } else if (take("while")) {
                Expr test = condition(); expect("limit"); int count = count();
                List<Node> body = block(insideAtomic, depth + 1);
                nodes.add(new Node(Kind.WHILE, null, test, body, List.of(), count, start, previous().end(), List.of(), null));
            } else if (!insideAtomic && take("return")) {
                if (depth != 1 || resultType == null) throw error(start, "unsupported", "Return requires a value helper's final top-level statement");
                int expressionStart = current().start();
                try {
                    Expr value = boundExpression(until(";"), expressionStart); expect(";");
                    if (TypeChecker.check(value, decl) != resultType) throw new RegistryException("Helper return type mismatch");
                    returned = new ReturnValue(value, start, previous().end());
                } catch (RegistryException e) { throw diagnostic(expressionStart, "type", e); }
                if (!peek("}")) throw error(current().start(), "syntax", "Return must be the helper's final statement");
            } else if (peek("assert") && tokens.get(position + 1).text().equals("(")) {
                position++;
                Expr test = condition(); expect(";");
                nodes.add(new Node(Kind.ASSERT, null, test, List.of(), List.of(), 0, start, previous().end(), List.of(), null));
            } else if (peek("call") && !tokens.get(position + 1).text().equals("=")
                    && !tokens.get(position + 1).text().equals("[")) {
                if (insideAtomic) throw error(start, "unsupported", "Helper calls inside atomic blocks are unsupported");
                position++;
                int target = current().start();
                String id = identifier();
                Helper helper = helpers.get(id);
                if (helper == null) throw error(target, "name", "Helper '" + id + "' must be declared before use; recursive calls are unsupported");
                Invocation invocation = invocation(helper, start);
                nodes.add(new Node(Kind.CALL, null, null, List.of(), List.of(), 0, start, previous().end(), List.of(), invocation));
            } else if (insideAtomic) {
                String effectText = until(";"); expect(";");
                Effect effect;
                try {
                    effect = new Parser(effectText).parseEffect();
                    checkExpression(targetExpression(effect.lhs()), start); checkExpression(effect.rhs(), start);
                    effect = bindEffect(effect);
                    Expr target = targetExpression(effect.lhs());
                    if (TypeChecker.check(target, decl) != TypeChecker.check(effect.rhs(), decl)) throw new RegistryException("Assignment type mismatch");
                } catch (RegistryException e) { throw diagnostic(start, "type", e); }
                nodes.add(new Node(Kind.ASSIGN, effect, null, List.of(), List.of(), 0, start, previous().end(), List.of(), null));
            } else throw error(start, "unsupported", "Expected atomic, assert, call, if, repeat or bounded while");
        }
        expect("}"); return List.copyOf(nodes);
    }

    /** Parses a parenthesized boolean condition using the existing expression grammar.
     * @return validated boolean expression at the current token
     * @throws RegistryException for missing delimiters, invalid types or expression budgets
     */
    private Expr condition() {
        expect("("); int start = current().start(); String text = until(")"); expect(")");
        try {
            Expr expr = boundExpression(text, start); TypeChecker.requireBool(expr, decl); return expr;
        } catch (RegistryException e) { throw diagnostic(start, "type", e); }
    }

    /** Rejects expressions whose structural depth would exceed the DSL execution limit.
     * @param expr parsed expression traversed iteratively before recursive semantic visitors
     * @param start original diagnostic offset
     * @throws RegistryException if depth exceeds 16 or this expression exceeds 5,000 nodes
     */
    private void checkExpression(Expr expr, int start) {
        Deque<ExpressionDepth> pending = new ArrayDeque<>(); pending.push(new ExpressionDepth(expr, 1));
        int count = 0;
        while (!pending.isEmpty()) {
            ExpressionDepth item = pending.pop();
            if (item.depth() > 16) throw error(start, "limit", "Expression depth exceeds 16");
            if (++count > 5000) throw error(start, "limit", "Expression nodes exceed 5000");
            if (item.expression() instanceof Expr.BinaryOp b) {
                pending.push(new ExpressionDepth(b.left(), item.depth() + 1)); pending.push(new ExpressionDepth(b.right(), item.depth() + 1));
            } else if (item.expression() instanceof Expr.UnaryOp u) pending.push(new ExpressionDepth(u.operand(), item.depth() + 1));
            else if (item.expression() instanceof Expr.ArrayAccess a) pending.push(new ExpressionDepth(a.index(), item.depth() + 1));
        }
    }

    /** Iterative validation avoids traversing hostile expression chains on the JVM stack. */
    private record ExpressionDepth(Expr expression, int depth) {}

    /** Reads expression text to a balanced delimiter without reparsing it as host code.
     * @param delimiter stopping token, consumed by the caller
     * @return comment-masked original expression slice
     * @throws RegistryException for EOF, braces, or unmatched parentheses/brackets
     */
    private String until(String delimiter) { return untilAny(Set.of(delimiter), "'" + delimiter + "'"); }

    /** Reads to one of several balanced delimiters without consuming it.
     * @param delimiters accepted boundary tokens at expression depth zero
     * @param expected diagnostic description of those boundary tokens
     * @return same-length comment-masked expression text
     * @throws RegistryException for EOF, braces or unmatched delimiters
     */
    private String untilAny(Set<String> delimiters, String expected) {
        int start = current().start(), parens = 0, brackets = 0;
        while (!peek("")) {
            String token = current().text();
            if (delimiters.contains(token) && parens == 0 && brackets == 0) return clean.substring(start, current().start());
            if (token.equals("{") || token.equals("}")) throw error(current().start(), "syntax", "Expected " + expected);
            if (token.equals("(")) parens++; else if (token.equals(")")) parens--;
            if (token.equals("[")) brackets++; else if (token.equals("]")) brackets--;
            if (parens < 0 || brackets < 0) throw error(current().start(), "syntax", "Unmatched expression delimiter");
            position++;
        }
        throw error(current().start(), "syntax", "Expected " + expected + " before EOF");
    }

    /** Validates private budgets in every helper definition before any call can expand it.
     * Earlier callees have already passed this check; CALL nodes have no raw child syntax.
     * @param nodes parsed definition nodes, including unused and zero-repeat branches
     * @throws RegistryException if an atomic body exceeds the existing 64-instruction cap
     */
    private void checkHelperAtomics(List<Node> nodes) {
        for (Node node : nodes) {
            if (node.kind() == Kind.ATOMIC && work(node.yes()) > 64) {
                throw error(node.start(), "limit", "Atomic internal instructions exceed 64");
            }
            checkHelperAtomics(node.yes());
            checkHelperAtomics(node.no());
        }
    }

    /** Computes expanded executable work with saturation before any loop-copy allocation.
     * @param nodes scheduled or private syntax to count using the same instruction rules
     * @return expanded action count, saturated at 65 to signal the inclusive 64-action cap
     */
    private long work(List<Node> nodes) {
        long result = 0;
        for (Node n : nodes) {
            long body = work(n.yes());
            long amount = switch (n.kind()) {
                case ASSIGN, ASSERT -> 1;
                case ATOMIC -> 1;
                case GUARD -> cappedAdd(1, body, 64);
                case IF -> cappedAdd(1, cappedAdd(body, work(n.no()), 64), 64);
                case REPEAT -> cappedMultiply(n.count(), body, 64);
                case WHILE -> cappedAdd((long) n.count() + 1, cappedMultiply(n.count(), body, 64), 64);
                case LIMIT -> 1;
                case CALL -> n.invocation().helper().work();
            };
            result = cappedAdd(result, amount, 64);
        }
        return result;
    }

    /** Counts all expanded expression occurrences, including property/control/error dependencies.
     * @param nodes syntax whose repeated expression occurrences are counted
     * @return occurrence count saturated at 5,001; zero repetitions contribute no work
     */
    private long expressionWork(List<Node> nodes) {
        long result = 0;
        for (Node n : nodes) {
            long test = n.test() == null ? 0 : Expr.nodeCount(n.test());
            long own = n.effect() == null ? test : Expr.nodeCount(n.effect().rhs()) + Expr.nodeCount(targetExpression(n.effect().lhs()));
            long body = expressionWork(n.yes());
            long amount = switch (n.kind()) {
                case CALL -> n.invocation().expressionWork();
                case REPEAT -> cappedMultiply(n.count(), body, 5000);
                case WHILE -> cappedAdd(cappedMultiply((long) n.count() + 1, test, 5000), cappedMultiply(n.count(), body, 5000), 5000);
                default -> cappedAdd(own, cappedAdd(body, expressionWork(n.no()), 5000), 5000);
            };
            result = cappedAdd(result, amount, 5000);
        }
        return result;
    }

    /** Expands repetition and bounded loops while retaining their original ancestry.
     * @param nodes validated bounded syntax; global expansion budgets must already pass
     * @param path enclosing source-loop occurrences, outermost first
     * @return immutable nodes with final conditional limit checks and original source spans
     * @throws RegistryException if a present atomic block exceeds 64 private instructions
     */
    private List<Node> expand(List<Node> nodes, List<ExecutedStep.Expansion> path) {
        List<Node> result = new ArrayList<>();
        for (Node n : nodes) {
            if (n.kind() == Kind.CALL) {
                Helper helper = n.invocation().helper();
                if (helper.work() == 0) continue;
                List<Node> invoked = new ArrayList<>();
                if (!helper.parameters().isEmpty()) invoked.add(capture(n, path));
                invoked.addAll(expand(helper.body(), path));
                if (!helper.parameters().isEmpty() || helper.returned() != null) invoked.add(complete(n, path));
                result.add(new Node(Kind.CALL, null, null, List.copyOf(invoked), List.of(), 0,
                    n.start(), n.end(), path, n.invocation()));
            } else if (n.kind() == Kind.REPEAT) {
                if (work(n.yes()) == 0) continue;
                for (int i = 0; i < n.count(); i++) result.addAll(expand(n.yes(), ancestry(path, n.start(), i)));
            } else if (n.kind() == Kind.WHILE) {
                List<Node> tail = List.of(new Node(Kind.LIMIT, null, n.test(), List.of(), List.of(), n.count(), n.start(), n.end(), ancestry(path, n.start(), n.count()), null));
                for (int i = n.count() - 1; i >= 0; i--) {
                    List<ExecutedStep.Expansion> iteration = ancestry(path, n.start(), i);
                    List<Node> yes = new ArrayList<>(expand(n.yes(), iteration)); yes.addAll(tail);
                    tail = List.of(new Node(Kind.IF, null, n.test(), List.copyOf(yes), List.of(), 0, n.start(), n.end(), iteration, null));
                }
                result.addAll(tail);
            } else {
                if (n.kind() == Kind.ATOMIC && work(n.yes()) > 64) throw error(n.start(), "limit", "Atomic internal instructions exceed 64");
                result.add(new Node(n.kind(), n.effect(), n.test(), expand(n.yes(), path), expand(n.no(), path), n.count(), n.start(), n.end(), path, null));
            }
        }
        return List.copyOf(result);
    }

    /** Lowers source-order fragments and patches direct branch joins without scheduled jumps.
     * @param nodes expanded forward syntax with no repeat/while nodes
     * @param drafts destination instruction list, extended in source order
     * @param callLabel outermost-first caller context, empty for ordinary source actions
     * @return entry position and unresolved continuation edges; empty fragments have entry -1
     */
    private Fragment lower(List<Node> nodes, List<Draft> drafts, String callLabel) {
        int start = -1; List<Edge> exits = List.of();
        for (Node n : nodes) {
            if (n.kind() == Kind.CALL) {
                SourceLocation call = location(n.start(), n.end());
                String context = callLabel + "call " + n.invocation().helper().name() + " at " + call.line() + ":" + call.column() + " -> ";
                Fragment invoked = lower(n.yes(), drafts, context);
                if (invoked.start() < 0) continue;
                if (start < 0) start = invoked.start();
                patch(exits, invoked.start(), drafts);
                exits = invoked.exits();
                continue;
            }
            int pc = drafts.size(); Draft draft = new Draft(n, callLabel); drafts.add(draft);
            if (start < 0) start = pc;
            patch(exits, pc, drafts);
            if (n.kind() == Kind.IF) {
                Fragment yes = lower(n.yes(), drafts, callLabel), no = lower(n.no(), drafts, callLabel);
                List<Edge> next = new ArrayList<>();
                if (yes.start() < 0) next.add(new Edge(pc, true)); else { draft.yes = yes.start(); next.addAll(yes.exits()); }
                if (no.start() < 0) next.add(new Edge(pc, false)); else { draft.no = no.start(); next.addAll(no.exits()); }
                exits = List.copyOf(next);
            } else exits = List.of(new Edge(pc, true), new Edge(pc, false));
        }
        return new Fragment(start, exits);
    }

    /** Resolves all pending continuation edges to a strictly later instruction.
     * @param edges unresolved true/false exits
     * @param target next instruction or thread-end position
     * @param drafts mutable instructions receiving selected targets
     */
    private static void patch(List<Edge> edges, int target, List<Draft> drafts) {
        for (Edge edge : edges) { if (edge.yes()) drafts.get(edge.pc()).yes = target; else drafts.get(edge.pc()).no = target; }
    }

    /** Returns copied enclosing occurrence path.
     * @param path outermost-first enclosing occurrences
     * @param offset original UTF-16 loop keyword offset
     * @param iteration zero-based body occurrence or final check N
     * @return copied enclosing occurrence path
     */
    private static List<ExecutedStep.Expansion> ancestry(List<ExecutedStep.Expansion> path, int offset, int iteration) {
        List<ExecutedStep.Expansion> next = new ArrayList<>(path); next.add(new ExecutedStep.Expansion(offset, iteration)); return List.copyOf(next);
    }

    /** Returns saturated sum, avoiding overflow and allocation beyond a cap.
     * @param a nonnegative first count, possibly already saturated
     * @param b nonnegative second count, possibly already saturated
     * @param cap inclusive allocation budget
     * @return exact sum up to cap, otherwise cap + 1 as an overflow/budget sentinel
     */
    private static long cappedAdd(long a, long b, long cap) { return a > cap || b > cap || a > cap - b ? cap + 1 : a + b; }
    /** Returns saturated product, including zero-work repetition.
     * @param a nonnegative multiplier, possibly already saturated
     * @param b nonnegative multiplicand, possibly already saturated
     * @param cap inclusive allocation budget
     * @return zero for zero work, exact product up to cap, otherwise cap + 1 as a sentinel
     */
    private static long cappedMultiply(long a, long b, long cap) { return a == 0 || b == 0 ? 0 : a > cap || b > cap || a > cap / b ? cap + 1 : a * b; }

    /** Returns expression representing a validated assignment target.
     * @param lhs validated scalar or indexed target
     * @return expression representing a validated assignment target
     */
    private static Expr targetExpression(Lhs lhs) {
        return switch (lhs) {
            case Lhs.FieldLhs f -> new Expr.VarRef(f.name());
            case Lhs.LocalLhs l -> new Expr.LocalRef(l.name());
            case Lhs.ArrayLhs a -> new Expr.ArrayAccess(a.arrayName(), a.index());
            case Lhs.LocalArrayLhs a -> new Expr.ArrayAccess("local." + a.localName(), a.index());
        };
    }

    /** Returns whether a property illegally reads local state or thread identity.
     * @param expr depth-validated candidate property
     * @return whether a property illegally reads local state or thread identity
     */
    private static boolean containsLocalOrTid(Expr expr) {
        return switch (expr) {
            case Expr.LocalRef ignored -> true;
            case Expr.TidRef ignored -> true;
            case Expr.ArrayAccess a -> a.arrayName().startsWith("local.") || containsLocalOrTid(a.index());
            case Expr.UnaryOp u -> containsLocalOrTid(u.operand());
            case Expr.BinaryOp b -> containsLocalOrTid(b.left()) || containsLocalOrTid(b.right());
            default -> false;
        };
    }

    /** Returns a checked, nonreserved source identifier.
     * @return a checked, nonreserved source identifier
     */
    private String identifier() {
        Token token = current();
        if (!token.text().matches("[a-z][a-z0-9_]*") || token.text().length() > 64 || RESERVED.contains(token.text())) {
            throw error(token.start(), "name", "Expected a nonreserved lowercase identifier");
        }
        position++; return token.text();
    }
    /** Returns a nonnegative count fitting signed int.
     * @return a nonnegative count fitting signed int
     */
    private int count() {
        Token token = current();
        if (!token.text().matches("[0-9]+")) throw error(token.start(), "syntax", "Expected a nonnegative literal count");
        try { int result = Integer.parseInt(token.text()); position++; return result; }
        catch (NumberFormatException e) { throw error(token.start(), "limit", "Count exceeds 2147483647"); }
    }
    /** Returns a signed 32-bit literal initializer.
     * @return a signed 32-bit literal initializer
     */
    private int signedInt() {
        boolean minus = take("-"); Token token = current();
        if (!token.text().matches("[0-9]+")) throw error(token.start(), "syntax", "Expected integer initializer");
        try { int result = Integer.parseInt((minus ? "-" : "") + token.text()); position++; return result; }
        catch (NumberFormatException e) { throw error(token.start(), "type", "Integer initializer outside signed int range"); }
    }
    /** Returns current token.
     * @return current token
     */
    private Token current() { return tokens.get(Math.min(position, tokens.size() - 1)); }
    /** Returns last consumed token.
     * @return last consumed token
     */
    private Token previous() { return tokens.get(position - 1); }
    /** Returns whether current token matches without consuming it.
     * @param text exact token spelling to compare
     * @return whether current token matches
     */
    private boolean peek(String text) { return current().text().equals(text); }
    /** Consumes the current token only when it matches.
     * @param text exact token spelling to consume
     * @return true if the token matched and the parser advanced
     */
    private boolean take(String text) { if (!peek(text)) return false; position++; return true; }
    /** Requires and consumes a token or raises a source diagnostic.
     * @param text required token spelling, or an empty string for end of input
     * @throws RegistryException with a syntax diagnostic if the current token does not match
     */
    private void expect(String text) { if (!take(text)) throw error(current().start(), "syntax", "Expected '" + (text.isEmpty() ? "EOF" : text) + "'"); }

    /** Masks comments without changing offsets, preserving CR/LF for diagnostics.
     * @return source text with each non-newline comment character replaced by a space
     * @throws RegistryException with a syntax diagnostic if a block comment is unterminated
     */
    private String maskComments() {
        char[] chars = source.toCharArray();
        for (int i = 0; i + 1 < chars.length; i++) {
            if (chars[i] != '/' || (chars[i + 1] != '/' && chars[i + 1] != '*')) continue;
            boolean block = chars[i + 1] == '*'; int start = i; chars[i++] = ' '; chars[i] = ' ';
            boolean closed = !block;
            while (++i < chars.length) {
                if (block && chars[i] == '*' && i + 1 < chars.length && chars[i + 1] == '/') {
                    chars[i++] = ' '; chars[i] = ' '; closed = true; break;
                }
                if (!block && (chars[i] == '\n' || chars[i] == '\r')) { i--; break; }
                if (chars[i] != '\n' && chars[i] != '\r') chars[i] = ' ';
            }
            if (!closed) throw error(start, "syntax", "Unterminated block comment");
        }
        return new String(chars);
    }

    /** Tokenizes source punctuation and expression operators while retaining original offsets.
     * @return immutable tokens followed by an empty end-of-input token
     */
    private List<Token> tokenize() {
        List<Token> result = new ArrayList<>();
        for (int i = 0; i < clean.length();) {
            if (Character.isWhitespace(clean.charAt(i))) { i++; continue; }
            int start = i; char c = clean.charAt(i++);
            if (Character.isLetterOrDigit(c) || c == '_') {
                while (i < clean.length() && (Character.isLetterOrDigit(clean.charAt(i)) || clean.charAt(i) == '_')) i++;
            } else if (i < clean.length() && Set.of("&&", "||", "==", "!=", "<=", ">=").contains(clean.substring(start, i + 1))) i++;
            result.add(new Token(clean.substring(start, i), start, i));
        }
        result.add(new Token("", clean.length(), clean.length())); return List.copyOf(result);
    }

    /** Returns source span with one-based UTF-16 columns and CRLF counted once.
     * @param start inclusive original-source UTF-16 offset
     * @param end exclusive original-source UTF-16 offset
     * @return source span with one-based UTF-16 columns and CRLF counted once
     */
    private SourceLocation location(int start, int end) { return sourceText.location(start, end); }
    /** Returns a stable categorized diagnostic at an original source offset.
     * @param offset original-source UTF-16 position of the failure
     * @param category stable diagnostic category
     * @param message explanation of the invalid source
     * @return a stable categorized diagnostic at an original source offset
     */
    private RegistryException error(int offset, String category, String message) {
        SourceLocation span = location(offset, offset);
        return new RegistryException(name + ":" + span.line() + ":" + span.column() + ": " + category + ": " + message);
    }

    /** Preserves source-boundary categories and maps expression parser offsets to the original text.
     * Messages already prefixed with this source name are returned unchanged. Otherwise name,
     * limit and syntax message markers override the fallback category. A parser's {@code pos N}
     * or {@code position N} marker is relative to the supplied offset and is capped at source end.
     * @param offset original-source UTF-16 start of the fragment parsed by the failing helper
     * @param fallback category to use when no recognized message marker applies
     * @param exception non-null helper diagnostic with a non-null message
     * @return original source diagnostic or a diagnostic remapped to the original source
     */
    private RegistryException diagnostic(int offset, String fallback, RegistryException exception) {
        String message = exception.getMessage();
        if (message.startsWith(name + ":")) return exception;
        String category = message.contains("Unknown") || message.contains("Duplicate") || message.contains("Reserved") ? "name"
            : message.contains("exceed") || message.contains("<=") || message.contains("1..") || message.contains("depth") ? "limit"
            : message.contains("Expected") || message.contains("Unexpected") || message.contains("Effect must") || message.contains("Empty")
                || message.contains("trailing") ? "syntax" : fallback;
        var matcher = java.util.regex.Pattern.compile("(?:pos|position) ([0-9]+)").matcher(message);
        if (matcher.find()) offset = Math.min(source.length(), offset + Integer.parseInt(matcher.group(1)));
        return error(offset, category, message);
    }
    /** Returns exact-byte SHA-256 without platform newline normalization.
     * @return exact-byte SHA-256 without platform newline normalization
     */
    private String digest() {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    /** Parsed source token. */
    private record Token(String text, int start, int end) {}
    /** Syntax node variants before/after bounded expansion. */
    enum Kind { ASSIGN, ATOMIC, IF, REPEAT, WHILE, LIMIT, CALL, ASSERT, GUARD }
    /** Immutable syntax and retained source ancestry. */
    record Node(Kind kind, Effect effect, Expr test, List<Node> yes, List<Node> no, int count,
                        int start, int end, List<ExecutedStep.Expansion> path, Invocation invocation) {}
    /** Earlier immutable definition with scalar frame slots and cached saturated costs.
     * @param name source helper name
     * @param parameters read-only positional values, with disjoint owner-local slots
     * @param resultType scalar result type, or null for void
     * @param returned final expression/span, or null for void
     * @param body scheduled source syntax without a return statement
     * @param work complete scheduled cost including generated capture/exit
     * @param expressionWork cached cost excluding caller-specific argument expressions
     */
    record Helper(String name, List<Parameter> parameters, FieldType resultType, ReturnValue returned,
                  List<Node> body, long work, long expressionWork) {}
    /** One read-only scalar formal with a hygienic owner-local slot.
     * @param name source spelling
     * @param type scalar int or bool
     * @param slot unique valid local name absent from written input
     */
    record Parameter(String name, FieldType type, String slot) {}
    /** Final return expression and original definition span.
     * @param expression bound scalar result expression
     * @param start original return keyword offset
     * @param end end of the return statement
     */
    record ReturnValue(Expr expression, int start, int end) {}
    /** Bound actual argument with its original callsite span.
     * @param expression scalar argument expression
     * @param type checked scalar type cached once per parsed argument
     * @param start first argument token offset
     * @param end argument boundary offset
     */
    record Argument(Expr expression, FieldType type, int start, int end) {}
    /** Immutable invocation with cached total expression work, avoiding exponential DAG walks.
     * @param helper earlier definition
     * @param arguments typed actual values in declaration order
     * @param target scalar return destination, or null for void
     * @param expressionWork full cached cost including argument expressions
     */
    record Invocation(Helper helper, List<Argument> arguments, Lhs target, long expressionWork) {}
    /** Pending forward continuation. */
    private record Edge(int pc, boolean yes) {}
    /** Lowered entry and unresolved exits. */
    private record Fragment(int start, List<Edge> exits) {}
    /** Call-local target assembly, frozen before a Program is returned. */
    private static final class Draft {
        /** Expanded source instruction whose metadata and effects remain immutable. */
        private final Node node;
        /** Caller annotation for the existing bounded metadata label. */
        private final String callLabel;
        /** True/success and false targets; -1 denotes an unresolved continuation. */
        private int yes = -1, no = -1;
        /** Initializes unresolved forward targets.
         * @param node expanded source instruction retained by the eventual immutable action
         * @param callLabel stable caller context, empty for direct source actions
         */
        private Draft(Node node, String callLabel) { this.node = node; this.callLabel = callLabel; }
    }

}
