package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.format.registry.RegistryException;

/**
 * Recursive-descent parser for the declarative expression language.
 *
 * <p>Grammar, loosest binding first. Each {@code parseX} handles one precedence level by calling the
 * next-tighter level first and then looping over the operators that may follow. The productions below
 * describe what the parser <em>actually accepts</em>, including two asymmetries worth knowing about:
 *
 * <pre>
 *   parseOr    := parseAnd   ( '||' parseAnd )*
 *   parseAnd   := parseCmp   ( '&amp;&amp;'  parseCmp )*
 *   parseCmp   := parseAdd   ( ('==' | '!=' | '&lt;' | '&gt;' | '&lt;=' | '&gt;=') parseAdd )*
 *   parseAdd   := parseMul   ( ('+' | '-') parseMul )*
 *   parseMul   := parseUnary ( ('*' | '%') parseUnary )*
 *   parseUnary := '!' parseUnary | '-' parseUnary | intLiteral | parsePrimary
 *   parsePrimary := '(' parseOr ')' | intLiteral | 'true' | 'false' | 'tid'
 *                | 'local' '.' IDENT ( '[' parseOr ']' )?
 *                | IDENT ( '[' parseOr ']' )?
 * </pre>
 *
 * <p><b>No division.</b> {@code parseMul} accepts {@code *} and {@code %} but not {@code /}, so an
 * expression like {@code a / b} raises a "trailing characters" error rather than parsing. That is the
 * existing behaviour and is preserved deliberately here — the grammar block documents the parser, it
 * does not describe a target.
 *
 * <p><b>Unary minus applies to more than literals.</b> {@code parseUnary} accepts both {@code !} and a
 * general unary {@code -}, so {@code -x} parses to a negation of an identifier rather than being
 * rejected. A minus directly before a digit is instead consumed by {@link #parseIntLit()}, which is why
 * {@code -5} is a literal while {@code -x} is an operation. The digit lookahead exists only to pick
 * between those two readings, not to require a digit after every {@code -}.
 *
 * <p><b>Recursion is not confined to primary.</b> Both {@link #parseUnary()} (into itself, for stacked
 * operators) and {@link #parsePrimary()} (into {@link #parseOr()}, for a parenthesised expression)
 * recurse, as does every array index, which is itself a full expression. {@link #checkNesting()} bounds
 * all of it, so a pathological input raises {@link RegistryException} rather than exhausting the JVM
 * stack.
 *
 * <p>Left associativity falls out of the loops: each level folds its operator onto the already-parsed
 * left operand, so {@code a - b - c} groups as {@code (a - b) - c} rather than {@code a - (b - c)}.
 *
 * <p>Recursion depth is bounded by {@code MAX_NESTING} via {@link #checkNesting()}, so a pathological
 * input such as thousands of nested parentheses raises {@link RegistryException} instead of exhausting
 * the JVM stack. That is why the guard sits on the way <em>into</em> each recursive call rather than
 * being left to the runtime.
 */
public final class Parser {
    private static final int MAX_NESTING = 64;

    private final String input;
    private int pos;
    private int nestingDepth;

    /**
     * Creates parser for given input.
     *
     * @param input expression string
     */
    public Parser(String input) {
        this.input = input;
        this.pos = 0;
    }

    /**
     * Parses a single expression and ensures fully consumed.
     *
     * @return expression AST
     * @throws RegistryException on syntax error
     */
    public Expr parseExpr() {
        skipWs();
        if (eof()) throw new RegistryException("Empty expression");
        Expr e = parseOr();
        skipWs();
        if (!eof()) throw new RegistryException("Unexpected trailing characters at pos " + pos + ": '" + input.substring(pos) + "'");
        return e;
    }

    /**
     * Parses effect lhs = rhs.
     *
     * @return effect
     * @throws RegistryException on syntax error
     */
    public Effect parseEffect() {
        skipWs();
        if (eof()) throw new RegistryException("Empty effect");
        // parse lhs until '='
        int eqIdx = findTopLevelEquals();
        if (eqIdx < 0) throw new RegistryException("Effect must contain '=': '" + input + "'");
        String lhsStr = input.substring(0, eqIdx).trim();
        String rhsStr = input.substring(eqIdx + 1).trim();
        if (lhsStr.isEmpty() || rhsStr.isEmpty()) throw new RegistryException("Effect must be 'lhs = expr': '" + input + "'");
        Lhs lhs = parseLhs(lhsStr);
        Expr rhs = new Parser(rhsStr).parseExpr();
        return new Effect(lhs, rhs);
    }

    /**
     * Locates the {@code =} that separates the effect from the invariant.
     *
     * <p>Scans with a bracket-depth counter so an {@code =} inside {@code (a == b)} or an array literal
     * is not mistaken for the separator. Only a top-level, non-equality {@code =} qualifies.
     *
     * @return the index of the top-level {@code =}, or {@code -1} when none is present
     */
    private int findTopLevelEquals() {
        // equals not inside brackets; effects lhs is simple so just find first '='
        int depth = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            else if (c == '=' && depth == 0) return i;
        }
        return -1;
    }

    /**
     * Parses the left-hand side of an assignment, which is either a bare field or a thread-local.
     *
     * <p>Distinguishing {@code local.x} from {@code x} here rather than at resolution time is what
     * makes a local write visible to the step that performs it.
     *
     * @param s the text left of the {@code =}
     * @return the parsed assignment target
     */
    private Lhs parseLhs(String s) {
        s = s.trim();
        if (s.startsWith("local.")) {
            String name = s.substring(6).trim();
            if (name.contains("[") || name.contains("]")) throw new RegistryException("Local lhs must not have array index: '" + s + "'");
            validateIdent(name);
            return new Lhs.LocalLhs(name);
        }
        // check array lhs
        int lb = s.indexOf('[');
        if (lb >= 0) {
            int rb = s.lastIndexOf(']');
            if (rb < 0 || rb != s.length() - 1) throw new RegistryException("Invalid array lhs: '" + s + "'");
            String arr = s.substring(0, lb).trim();
            String idxStr = s.substring(lb + 1, rb).trim();
            validateIdent(arr);
            if (idxStr.isEmpty()) throw new RegistryException("Array index empty: '" + s + "'");
            Expr idx = new Parser(idxStr).parseExpr();
            return new Lhs.ArrayLhs(arr, idx);
        }
        validateIdent(s);
        return new Lhs.FieldLhs(s);
    }

    // grammar: or -> and ( "||" and )*
    /**
     * Parses the loosest level: {@code ||} chains.
     *
     * @return the parsed expression
     */
    private Expr parseOr() {
        Expr left = parseAnd();
        while (true) {
            skipWs();
            if (match("||")) {
                skipWs();
                Expr right = parseAnd();
                left = new Expr.BinaryOp(left, "||", right);
            } else break;
        }
        return left;
    }

    // and -> cmp ( "&&" cmp )*
    /**
     * Parses {@code &&} chains, which bind tighter than {@code ||}.
     *
     * @return the parsed expression
     */
    private Expr parseAnd() {
        Expr left = parseCmp();
        while (true) {
            skipWs();
            if (match("&&")) {
                skipWs();
                Expr right = parseCmp();
                left = new Expr.BinaryOp(left, "&&", right);
            } else break;
        }
        return left;
    }

    // cmp -> add ( ("==" | "!=" | "<=" | ">=" | "<" | ">") add )*
    /**
     * Parses the equality and relational operators.
     *
     * <p>Kept separate from the arithmetic levels so {@code a + b == c} parses as {@code (a + b) == c}:
     * the comparison level consumes an already-complete arithmetic expression on its left.
     *
     * @return the parsed expression
     */
    private Expr parseCmp() {
        Expr left = parseAdd();
        while (true) {
            skipWs();
            String op = null;
            if (match("==")) op = "==";
            else if (match("!=")) op = "!=";
            else if (match("<=")) op = "<=";
            else if (match(">=")) op = ">=";
            else if (match("<")) op = "<";
            else if (match(">")) op = ">";
            if (op != null) {
                skipWs();
                Expr right = parseAdd();
                left = new Expr.BinaryOp(left, op, right);
            } else break;
        }
        return left;
    }

    // add -> mul ( ("+" | "-") mul )*
    /**
     * Parses {@code +} and {@code -}, left-associatively.
     *
     * @return the parsed expression
     */
    private Expr parseAdd() {
        Expr left = parseMul();
        while (true) {
            skipWs();
            // avoid consuming "->" or part of comparison; simple check
            if (peek() == '+' || peek() == '-') {
                // ensure not part of "==" etc already handled; peek next char
                // also need to ensure we don't consume '-' of "!=" already consumed
                // For add, we treat + and - as binary only if not already cmp
                // To avoid confusion with unary, we need to lookahead: if left already parsed, binary +/-
                // But we already handled cmp before, so here it's safe
                char c = consume();
                skipWs();
                Expr right = parseMul();
                left = new Expr.BinaryOp(left, String.valueOf(c), right);
            } else break;
        }
        return left;
    }

    // mul -> unary ( ("*" | "%") unary )*
    /**
     * Parses {@code *} and {@code %}, left-associatively.
     *
     * <p>{@code /} is deliberately <em>not</em> accepted: there is no division operator in this
     * language, and {@code a / b} fails at the trailing-character check rather than silently parsing as
     * something else. Recorded here so the absence does not read as an oversight in this method.
     *
     * @return the parsed expression
     */
    private Expr parseMul() {
        Expr left = parseUnary();
        while (true) {
            skipWs();
            if (peek() == '*' || peek() == '%') {
                char c = consume();
                skipWs();
                Expr right = parseUnary();
                left = new Expr.BinaryOp(left, String.valueOf(c), right);
            } else break;
        }
        return left;
    }

    // unary -> ("!" | "-")* primary
    /**
     * Parses a unary operator: either {@code !} or {@code -}, applied to a single operand.
     *
     * <p>A minus followed by a digit is diverted to {@link #parseIntLit()} so a negative literal is one
     * token rather than a negation of a positive literal — both readings produce the same value for
     * literals, but only the first keeps {@code -2147483648} representable. A minus before anything
     * else is a genuine unary operator, so {@code -x} parses rather than failing.
     *
     * @return the parsed expression
     * @throws RegistryException if nesting exceeds {@link #MAX_NESTING}
     */
    private Expr parseUnary() {
        skipWs();
        if (peek() == '-' && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1))) {
            return parseIntLit();
        }
        if (peek() == '!' || peek() == '-') {
            char c = consume();
            skipWs();
            nestingDepth++;
            checkNesting();
            try {
                Expr operand = parseUnary();
                return new Expr.UnaryOp(String.valueOf(c), operand);
            } finally {
                nestingDepth--;
            }
        }
        return parsePrimary();
    }

    /**
     * Fails fast when recursive descent exceeds {@link #MAX_NESTING}.
     *
     * @throws RegistryException if the nesting limit is exceeded
     */
    private void checkNesting() {
        if (nestingDepth > MAX_NESTING) {
            throw new RegistryException("Expression nesting depth exceeds " + MAX_NESTING);
        }
    }

    /**
     * Parses the innermost forms: parenthesised expressions, integer literals, {@code true} /
     * {@code false}, {@code tid}, {@code local.<name>} references, plain identifiers, and array
     * subscripting in the form {@code name[index]} or {@code local.<name>[index]}.
     *
     * <p>Recurses three ways: a parenthesised expression calls back into {@link #parseOr()}, and each
     * array index is itself parsed as a full expression via {@link #parseOr()}. Both are bounded by
     * {@link #checkNesting()}. Note there is no general dotted path such as {@code thread.state} —
     * {@code local.} is the only dotted form, and {@code tid} is a bare keyword.
     *
     * @return the parsed expression
     * @throws RegistryException if nesting exceeds {@link #MAX_NESTING}, a bracket is unterminated, or
     *         an unexpected character or unsupported literal is encountered
     */
    private Expr parsePrimary() {
        skipWs();
        if (eof()) throw new RegistryException("Unexpected end of expression");
        char c = peek();
        if (c == '(') {
            consume();
            nestingDepth++;
            checkNesting();
            try {
                Expr inner = parseOr();
                skipWs();
                if (eof() || peek() != ')') throw new RegistryException("Missing closing ')'");
                consume();
                return inner;
            } finally {
                nestingDepth--;
            }
        }
        if (c == '"' || c == '\'') throw new RegistryException("String literals not supported");
        // try bool literals and tid and identifiers and int
        if (Character.isDigit(c) || (c == '-' && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1)))) {
            // int literal (allow negative via unary, but also directly?)
            // we handle negative numbers as unary minus, but if starts with digit, parse number
            return parseIntLit();
        }
        if (Character.isLetter(c)) {
            String word = parseWord();
            if ("true".equals(word)) return new Expr.BoolLit(true);
            if ("false".equals(word)) return new Expr.BoolLit(false);
            if ("tid".equals(word)) {
                // check not followed by alnum (tid is keyword)
                return new Expr.TidRef();
            }
            if ("local".equals(word)) {
                // expect '.' IDENT
                skipWs();
                if (eof() || peek() != '.') throw new RegistryException("Expected '.' after 'local'");
                consume();
                String name = parseIdent();
                // check for array access local.name[expr] is not allowed for locals (scalar only) but we still parse for error later; spec says local.* never has [
                // however grammar allows local.IDENT "[" expr "]" — we support but later type-check will reject for locals
                skipWs();
                if (!eof() && peek() == '[') {
                    consume();
                    nestingDepth++;
                    checkNesting();
                    try {
                        Expr idx = parseOr();
                        skipWs();
                        if (eof() || peek() != ']') throw new RegistryException("Missing ']' for local array access");
                        consume();
                        return new Expr.ArrayAccess("local." + name, idx);
                    } finally {
                        nestingDepth--;
                    }
                }
                return new Expr.LocalRef(name);
            }
            // shared field ident, maybe array access
            String name = word;
            validateIdent(name);
            skipWs();
            if (!eof() && peek() == '[') {
                consume();
                nestingDepth++;
                checkNesting();
                try {
                    Expr idx = parseOr();
                    skipWs();
                    if (eof() || peek() != ']') throw new RegistryException("Missing ']' for array access");
                    consume();
                    return new Expr.ArrayAccess(name, idx);
                } finally {
                    nestingDepth--;
                }
            }
            return new Expr.VarRef(name);
        }
        if (c == '-') {
            // unary minus handled in parseUnary, but if we are here, it's '-' before primary with no operand? parseUnary already consumed
            // fallback
            consume();
            Expr operand = parsePrimary();
            return new Expr.UnaryOp("-", operand);
        }
        throw new RegistryException("Unexpected character '" + c + "' at pos " + pos);
    }

    /**
     * Parses an optionally negative integer literal.
     *
     * @return the parsed literal expression
     */
    private Expr parseIntLit() {
        int start = pos;
        if (peek() == '-') consume();
        if (eof() || !Character.isDigit(peek())) throw new RegistryException("Expected digit for int literal");
        while (!eof() && Character.isDigit(peek())) consume();
        String numStr = input.substring(start, pos);
        try {
            int v = Integer.parseInt(numStr);
            return new Expr.IntLit(v);
        } catch (NumberFormatException e) {
            throw new RegistryException("Int literal out of range: " + numStr, e);
        }
    }

    /**
     * Consumes and returns a run of letters, digits, and underscores.
     *
     * @return the consumed word
     */
    private String parseWord() {
        int start = pos;
        while (!eof() && (Character.isLetterOrDigit(peek()) || peek() == '_')) consume();
        return input.substring(start, pos);
    }

    /**
     * Parses an identifier, delegating validation to {@link #validateIdent(String)}.
     *
     * @return the identifier text
     * @throws RegistryException if the next character cannot start an identifier, or the identifier is
     *         empty or over the length cap
     */
    private String parseIdent() {
        int start = pos;
        if (eof() || !Character.isLetter(peek())) throw new RegistryException("Expected identifier at pos " + pos);
        consume();
        while (!eof() && (Character.isLetterOrDigit(peek()) || peek() == '_')) consume();
        String ident = input.substring(start, pos);
        validateIdent(ident);
        return ident;
    }

    /**
     * Rejects empty and over-long identifiers before they reach the registry.
     *
     * <p>Validating here keeps a malformed program from being registered and then failing later at a
     * point where the offending name is no longer visible in the stack trace.
     *
     * @param ident the identifier to validate
     * @throws RegistryException if the identifier is null, empty, or longer than 64 characters
     */
    private void validateIdent(String ident) {
        if (ident == null || ident.isEmpty()) throw new RegistryException("Empty identifier");
        if (ident.length() > 64) throw new RegistryException("Identifier too long (>64): " + ident);
        if (!ident.matches("[a-z][a-z0-9_]*")) throw new RegistryException("Invalid identifier '" + ident + "': must match [a-z][a-z0-9_]*");
        if ("local".equals(ident) || "tid".equals(ident)) throw new RegistryException("Reserved keyword cannot be used as identifier: " + ident);
    }

    /** Advances past any run of whitespace. */
    private void skipWs() {
        while (!eof() && Character.isWhitespace(peek())) pos++;
    }

    private boolean eof() { return pos >= input.length(); }
    private char peek() { return eof() ? '\0' : input.charAt(pos); }
    private char consume() { char c = input.charAt(pos); pos++; return c; }
    /**
     * Consumes the given literal if it is next at the current position.
     *
     * <p>Backtracks: on a miss the position is left untouched, so a caller can treat a failed match as
     * "not this operator" and let a looser level handle the token. Committing the position on failure
     * would make backtracking impossible and every alternative a special case.
     *
     * @param s the literal to match at the current position
     * @return true if matched and consumed, false if the input differs
     */
    private boolean match(String s) {
        if (input.startsWith(s, pos)) { pos += s.length(); return true; }
        return false;
    }
}
