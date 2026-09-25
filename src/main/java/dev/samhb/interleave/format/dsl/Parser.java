package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.format.registry.RegistryException;

/**
 * Recursive-descent parser for declarative expression language.
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

    /** findTopLevelEquals method. */
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

    /** parseLhs method. */
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
    /** parseOr method. */
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
    /** parseAnd method. */
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
    /** parseCmp method. */
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
    /** parseAdd method. */
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
    /** parseMul method. */
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
    /** parseUnary method. */
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

    /** checkNesting method. */
    private void checkNesting() {
        if (nestingDepth > MAX_NESTING) {
            throw new RegistryException("Expression nesting depth exceeds " + MAX_NESTING);
        }
    }

    /** parsePrimary method. */
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

    /** parseIntLit method. */
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

    /** parseWord method. */
    private String parseWord() {
        int start = pos;
        while (!eof() && (Character.isLetterOrDigit(peek()) || peek() == '_')) consume();
        return input.substring(start, pos);
    }

    /** parseIdent method. */
    private String parseIdent() {
        int start = pos;
        if (eof() || !Character.isLetter(peek())) throw new RegistryException("Expected identifier at pos " + pos);
        consume();
        while (!eof() && (Character.isLetterOrDigit(peek()) || peek() == '_')) consume();
        String ident = input.substring(start, pos);
        validateIdent(ident);
        return ident;
    }

    /** validateIdent method. */
    private void validateIdent(String ident) {
        if (ident == null || ident.isEmpty()) throw new RegistryException("Empty identifier");
        if (ident.length() > 64) throw new RegistryException("Identifier too long (>64): " + ident);
        if (!ident.matches("[a-z][a-z0-9_]*")) throw new RegistryException("Invalid identifier '" + ident + "': must match [a-z][a-z0-9_]*");
        if ("local".equals(ident) || "tid".equals(ident)) throw new RegistryException("Reserved keyword cannot be used as identifier: " + ident);
    }

    /** skipWs method. */
    private void skipWs() {
        while (!eof() && Character.isWhitespace(peek())) pos++;
    }

    private boolean eof() { return pos >= input.length(); }
    private char peek() { return eof() ? '\0' : input.charAt(pos); }
    private char consume() { char c = input.charAt(pos); pos++; return c; }
    /** match method. */
    private boolean match(String s) {
        if (input.startsWith(s, pos)) { pos += s.length(); return true; }
        return false;
    }
}
