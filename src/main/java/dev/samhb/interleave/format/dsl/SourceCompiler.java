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
            Fragment whole = lower(expanded, drafts);
            patch(whole.exits(), drafts.size(), drafts);
            List<Step> instructions = new ArrayList<>();
            for (int pc = 0; pc < drafts.size(); pc++) {
                Draft d = drafts.get(pc);
                if (d.yes <= pc || d.yes > drafts.size() || d.no <= pc || d.no > drafts.size()) {
                    throw new IllegalStateException("Compiler produced an invalid forward target");
                }
                instructions.add(new SourceInstruction(d.node, d.yes, d.no, owner, decl, sourceText));
            }
            threads.add(new ModelThread(owner, instructions));
        }
        return new BenchmarkProgram(programName, new Program(new DynamicState(decl, threads.size()), threads, digest()), expected, invariant);
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
     * @param insideAtomic whether assignments/private control are allowed instead of nested atomics
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
                if (insideAtomic) throw error(start, "unsupported", "Nested atomic blocks are unsupported");
                Expr guard = take("when") ? condition() : null;
                List<Node> body = block(true, depth + 1);
                nodes.add(new Node(Kind.ATOMIC, null, guard, body, List.of(), 0, start, previous().end(), List.of()));
            } else if (take("if")) {
                Expr test = condition();
                List<Node> yes = block(insideAtomic, depth + 1);
                List<Node> no = take("else") ? block(insideAtomic, depth + 1) : List.of();
                nodes.add(new Node(Kind.IF, null, test, yes, no, 0, start, previous().end(), List.of()));
            } else if (take("repeat")) {
                int count = count(); List<Node> body = block(insideAtomic, depth + 1);
                nodes.add(new Node(Kind.REPEAT, null, null, body, List.of(), count, start, previous().end(), List.of()));
            } else if (take("while")) {
                Expr test = condition(); expect("limit"); int count = count();
                List<Node> body = block(insideAtomic, depth + 1);
                nodes.add(new Node(Kind.WHILE, null, test, body, List.of(), count, start, previous().end(), List.of()));
            } else if (insideAtomic) {
                String effectText = until(";"); expect(";");
                Effect effect;
                try {
                    effect = new Parser(effectText).parseEffect();
                    Expr target = targetExpression(effect.lhs());
                    checkExpression(target, start); checkExpression(effect.rhs(), start);
                    if (TypeChecker.check(target, decl) != TypeChecker.check(effect.rhs(), decl)) throw new RegistryException("Assignment type mismatch");
                } catch (RegistryException e) { throw diagnostic(start, "type", e); }
                nodes.add(new Node(Kind.ASSIGN, effect, null, List.of(), List.of(), 0, start, previous().end(), List.of()));
            } else throw error(start, "unsupported", "Expected atomic, if, repeat or bounded while");
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
            Expr expr = new Parser(text).parseExpr(); checkExpression(expr, start); TypeChecker.requireBool(expr, decl); return expr;
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
    private String until(String delimiter) {
        int start = current().start(), parens = 0, brackets = 0;
        while (!peek("")) {
            String token = current().text();
            if (token.equals(delimiter) && parens == 0 && brackets == 0) return clean.substring(start, current().start());
            if (token.equals("{") || token.equals("}")) throw error(current().start(), "syntax", "Expected '" + delimiter + "'");
            if (token.equals("(")) parens++; else if (token.equals(")")) parens--;
            if (token.equals("[")) brackets++; else if (token.equals("]")) brackets--;
            if (parens < 0 || brackets < 0) throw error(current().start(), "syntax", "Unmatched expression delimiter");
            position++;
        }
        throw error(current().start(), "syntax", "Expected '" + delimiter + "' before EOF");
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
                case ASSIGN -> 1;
                case ATOMIC -> 1;
                case IF -> cappedAdd(1, cappedAdd(body, work(n.no()), 64), 64);
                case REPEAT -> cappedMultiply(n.count(), body, 64);
                case WHILE -> cappedAdd((long) n.count() + 1, cappedMultiply(n.count(), body, 64), 64);
                case LIMIT -> 1;
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
            if (n.kind() == Kind.REPEAT) {
                if (work(n.yes()) == 0) continue;
                for (int i = 0; i < n.count(); i++) result.addAll(expand(n.yes(), ancestry(path, n.start(), i)));
            } else if (n.kind() == Kind.WHILE) {
                List<Node> tail = List.of(new Node(Kind.LIMIT, null, n.test(), List.of(), List.of(), n.count(), n.start(), n.end(), ancestry(path, n.start(), n.count())));
                for (int i = n.count() - 1; i >= 0; i--) {
                    List<ExecutedStep.Expansion> iteration = ancestry(path, n.start(), i);
                    List<Node> yes = new ArrayList<>(expand(n.yes(), iteration)); yes.addAll(tail);
                    tail = List.of(new Node(Kind.IF, null, n.test(), List.copyOf(yes), List.of(), 0, n.start(), n.end(), iteration));
                }
                result.addAll(tail);
            } else {
                if (n.kind() == Kind.ATOMIC && work(n.yes()) > 64) throw error(n.start(), "limit", "Atomic internal instructions exceed 64");
                result.add(new Node(n.kind(), n.effect(), n.test(), expand(n.yes(), path), expand(n.no(), path), n.count(), n.start(), n.end(), path));
            }
        }
        return List.copyOf(result);
    }

    /** Lowers source-order fragments and patches direct branch joins without scheduled jumps.
     * @param nodes expanded forward syntax with no repeat/while nodes
     * @param drafts destination instruction list, extended in source order
     * @return entry position and unresolved continuation edges; empty fragments have entry -1
     */
    private Fragment lower(List<Node> nodes, List<Draft> drafts) {
        int start = -1; List<Edge> exits = List.of();
        for (Node n : nodes) {
            int pc = drafts.size(); Draft draft = new Draft(n); drafts.add(draft);
            if (start < 0) start = pc;
            patch(exits, pc, drafts);
            if (n.kind() == Kind.IF) {
                Fragment yes = lower(n.yes(), drafts), no = lower(n.no(), drafts);
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
    /** Returns whether current token matches.
     * @return whether current token matches
     */
    private boolean peek(String text) { return current().text().equals(text); }
    /** Consumes a matching token. @return whether matched */
    private boolean take(String text) { if (!peek(text)) return false; position++; return true; }
    /** Requires a token or raises a source diagnostic. */
    private void expect(String text) { if (!take(text)) throw error(current().start(), "syntax", "Expected '" + (text.isEmpty() ? "EOF" : text) + "'"); }

    /** Masks comments without changing offsets, preserving CR/LF for diagnostics. */
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

    /** Tokenizes source punctuation and expression operators while retaining original offsets. */
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
     * @return source span with one-based UTF-16 columns and CRLF counted once
     */
    private SourceLocation location(int start, int end) { return sourceText.location(start, end); }
    /** Returns a stable categorized diagnostic at an original source offset.
     * @return a stable categorized diagnostic at an original source offset
     */
    private RegistryException error(int offset, String category, String message) {
        SourceLocation span = location(offset, offset);
        return new RegistryException(name + ":" + span.line() + ":" + span.column() + ": " + category + ": " + message);
    }

    /** Preserves source-boundary categories and maps expression parser offsets to the original text. */
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
    enum Kind { ASSIGN, ATOMIC, IF, REPEAT, WHILE, LIMIT }
    /** Immutable syntax and retained source ancestry. */
    record Node(Kind kind, Effect effect, Expr test, List<Node> yes, List<Node> no, int count,
                        int start, int end, List<ExecutedStep.Expansion> path) {}
    /** Pending forward continuation. */
    private record Edge(int pc, boolean yes) {}
    /** Lowered entry and unresolved exits. */
    private record Fragment(int start, List<Edge> exits) {}
    /** Call-local target assembly, frozen before a Program is returned. */
    private static final class Draft {
        /** Expanded source instruction whose metadata and effects remain immutable. */
        private final Node node;
        /** True/success and false targets; -1 denotes an unresolved continuation. */
        private int yes = -1, no = -1;
        /** Initializes unresolved forward targets.
         * @param node expanded source instruction retained by the eventual immutable action
         */
        private Draft(Node node) { this.node = node; }
    }

}
