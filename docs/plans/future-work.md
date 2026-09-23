# Future Work / Extensions

These are explicitly out of scope for the 7-spec deliverable, but are natural extensions.

## Items 1–2: Completed

| # | Item | Status |
|---|---|---|
| 1 | Library/API mode | ✅ Done (PR #1) |
| 2 | JSON program definition format | ✅ Done (PR #15) |

## Remaining Items — Ranked by LOE

| # | Item | LOE | Spec Needed? | Why |
|---|---|---|---|---|
| 3 | Bitstate / supertrace mode | Low-Medium | No | `BitstateStore` already prototyped; wiring into explorers is mechanical |
| 4 | Property-based corpus mining | Medium | No | Template design + generation logic; reuses existing types |
| 5 | General-purpose JSON format | Medium | Yes | New DSL — schema, registries, validation rules need frozen spec before implementation |
| 6 | Web UI / visualizer | Medium | Maybe | Trace renderer: no. Full state-space DAG viz: yes |
| 7 | Context-bounding / CHESS-style | Medium-High | Yes | New exploration strategy — correctness depends on bound semantics |
| 8 | Concurrent-program parser | High | Yes | Language design + parser + semantic mapping — full spec needed |
| 9 | Symmetry reduction | High | Yes | Canonicalization is subtle; soundness must be proven |
| 10 | Relaxed memory models | Very High | Yes | Fundamental semantics change — needs formal spec |
| 11 | Real Java bytecode instrumentation | Very High | Yes | Entirely new subsystem with external deps (ASM/Javassist) |

### Item Details

3. **Bitstate / supertrace mode** — replace exact visited sets with a bloom-filter approximation to trade completeness for memory. Already prototyped in `BitstateStore`; needs integration into explorers and reporting.

4. **Property-based corpus mining** — instead of hand-written buggy programs, generate concurrent programs from templates. Template design + generation logic; reuses existing explorers.

5. **General-purpose JSON program format** — extend the existing JSON loader beyond the 5 hardcoded state types and 19 step types. Allow arbitrary state fields, declarative step definitions (read/write sets, enable conditions), and composable invariants — a JSON-based DSL for defining concurrent programs without writing Java. Builds on the `ProgramLoader`/registry infrastructure from item 2; makes the tool accessible for teaching, rapid prototyping, and expressing real-world concurrency patterns (e.g. producer/consumer pipelines).

6. **Web UI / visualizer** — render the interleaving tree, state space DAG, or failing trace visually. Can start as a minimal HTML trace renderer; complexity grows with visualization depth.

7. **Context-bounding / CHESS-style stateless search** — bound the depth of context switches instead of exploring all interleavings. Algorithmic addition on top of DFS; needs new exploration strategy but reuses core types.

8. **Concurrent-program parser** — parse a small imperative language with threads, shared variables, and atomic sections into the checker's internal model. Requires designing a small language, lexer/parser, and semantic mapping to `Step`/`SharedState`.

9. **Symmetry reduction** — exploit thread-identity symmetry to collapse equivalent states that differ only by which thread has which ID. Subtle correctness concerns; needs careful canonicalization and proof of soundness.

10. **Relaxed memory models** — support weak consistency models (e.g., ARM/POWER) instead of assuming sequential consistency. Fundamental change to `Configuration` snapshot semantics and step execution.

11. **Real Java bytecode instrumentation** — instead of hand-written `Step` objects, instrument real Java bytecode so the checker can analyze actual concurrent programs. Requires bytecode analysis (ASM/Javassist), thread detection, and mapping bytecode to atomic steps.
