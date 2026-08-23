# Future Work / Extensions

These are explicitly out of scope for the 7-spec deliverable, but are natural extensions, ranked by estimated level of effort (lowest to highest):

## Low LOE
1. **Library/API mode** — expose the checker as a Java library so external tools can construct `Program`/`Step` objects programmatically and invoke verification. Mostly visibility/access changes; reuses existing internals.
2. **JSON/YAML/DSL program definition format** — let users describe programs declaratively without writing Java code. Useful for rapid prototyping and teaching. Simple schema + loader; no new algorithms.

## Medium LOE
3. **Web UI / visualizer** — render the interleaving tree, state space DAG, or failing trace visually. Can start as a minimal HTML trace renderer; complexity grows with visualization depth.
4. **Property-based corpus mining** — instead of hand-written buggy programs, generate concurrent programs from templates. Template design + generation logic; reuses existing explorers.
5. **Context-bounding / CHESS-style stateless search** — bound the depth of context switches instead of exploring all interleavings. Algorithmic addition on top of DFS; needs new exploration strategy but reuses core types.
6. **Bitstate / supertrace mode** — replace exact visited sets with a bloom-filter approximation to trade completeness for memory. Already prototyped in `BitstateStore`; needs integration into explorers and reporting.
7. **Concurrent-program parser** — parse a small imperative language with threads, shared variables, and atomic sections into the checker's internal model. Requires designing a small language, lexer/parser, and semantic mapping to `Step`/`SharedState`.

## High LOE
8. **Symmetry reduction** — exploit thread-identity symmetry to collapse equivalent states that differ only by which thread has which ID. Subtle correctness concerns; needs careful canonicalization and proof of soundness.
9. **Relaxed memory models** — support weak consistency models (e.g., ARM/POWER) instead of assuming sequential consistency. Fundamental change to `Configuration` snapshot semantics and step execution.
10. **Real Java bytecode instrumentation** — instead of hand-written `Step` objects, instrument real Java bytecode so the checker can analyze actual concurrent programs. Requires bytecode analysis (ASM/Javassist), thread detection, and mapping bytecode to atomic steps.
