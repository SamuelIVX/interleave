package dev.samhb.interleave.format.dsl;

/**
 * Effect assignment lhs = rhs.
 *
 * @param lhs left-hand side
 * @param rhs right-hand side expression
 */
public record Effect(Lhs lhs, Expr rhs) {}
