# Future Work / Extensions

These are explicitly out of scope for the 7-spec deliverable, but are natural extensions:

- **Real Java bytecode instrumentation** — instead of hand-written `Step` objects, instrument real Java bytecode so the checker can analyze actual concurrent programs. Mentioned in spec 01 as an explicit out-of-scope extension.
- **Relaxed memory models** — support weak consistency models (e.g., ARM/POWER) instead of assuming sequential consistency. Would require rethinking the `Configuration` snapshot model.
- **Symmetry reduction** — exploit thread-identity symmetry to collapse equivalent states that differ only by which thread has which ID. Mentioned in specs 04, 05, and 07 as an optional optimization.
- **Context-bounding / CHESS-style stateless search** — bound the depth of context switches instead of exploring all interleavings. Mentioned in spec 05 as a spec-07 extension experiment and in spec 07 as an optional extension column.
- **Bitstate / supertrace mode** — replace exact visited sets with a bloom-filter approximation to trade completeness for memory. Prototyped in spec 03's `BitstateStore`, but not part of the baseline report.
- **Property-based corpus mining** — instead of hand-written buggy programs, generate concurrent programs from templates. Mentioned in spec 06 as a spec-07 extension.
- **Web UI / visualizer** — render the interleaving tree, state space DAG, or failing trace visually. Not part of spec 07's CLI-only scope.
- **JSON/YAML/DSL program definition format** — let users describe programs declaratively without writing Java code. Useful for rapid prototyping and teaching.
- **Concurrent-program parser** — parse a small imperative language with threads, shared variables, and atomic sections into the checker's internal model.
- **Library/API mode** — expose the checker as a Java library so external tools can construct `Program`/`Step` objects programmatically and invoke verification.
