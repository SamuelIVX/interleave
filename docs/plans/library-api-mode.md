# Library/API Mode — Design Plan

## TL;DR
Expose `interleave` as a clean Java library so external tools can construct programs,
invoke verification, and consume results programmatically. No new algorithms; this is
an API-surface and packaging change.

## Current State
- `dev.samhb.interleave.cli.Main` is the only public entry point.
- Core types (`Program`, `Step`, `Configuration`, explorers) are package-private or
  lack stable result contracts.
- Results are printed to stdout in the CLI layer; there is no structured return type
  for programmatic consumption.

## Goal
External callers can do:

```java
Program program = Interleave.builder()
    .addThread(t0)
    .addThread(t1)
    .initialState(state)
    .build();

VerificationResult result = Interleave.verify(program, Strategy.DPOR);
if (result.hasViolation()) {
    Trace trace = result.failingTrace();
    System.out.println(trace.replay());
}
```

## Proposed API Surface

### 1. Public Facade: `Interleave`
- `Interleave.builder()` — fluent program builder
- `Interleave.verify(program, strategy)` — run a strategy
- `Interleave.verify(program, strategy, invariant)` — run with invariant
- Overloads returning `CompletableFuture<VerificationResult>` for async use

### 2. Stable Result Types
- `VerificationResult` — immutable, serializable
  - `boolean hasViolation()`
  - `List<Trace> failingTraces()`
  - `Strategy strategyUsed()`
  - `long statesExplored()`
  - `Duration wallTime()`
  - `long peakMemoryBytes()`
- `Trace` — already exists, make it public and immutable
- `TraceStep` — `(threadId, stepDescription)` pair for external trace consumption

### 3. Strategy Enum
```java
public enum Strategy {
    DFS,
    STATIC_POR,
    DPOR,
    HASHING
}
```
- Maps to existing explorer implementations
- Allows future strategies without API changes

### 4. Invariant API
```java
@FunctionalInterface
public interface Invariant {
    boolean test(SharedState state, Configuration config);
}
```
- Already exists internally; promote to public API
- Add `Invariant.and(Invariant...)` / `Invariant.or(...)` combinators

### 5. Builder Pattern for Programs
```java
Program program = Interleave.builder()
    .addThread(thread0)
    .addThread(thread1)
    .initialState(PetersonState.of(false, false, 0))
    .build();
```
- `Program` constructor stays package-private
- Builder validates non-empty thread list, non-null state

### 6. Step API Stability
- `Step` interface stays as-is (already public)
- Add `Step.description()` for human-readable step names in traces
- Existing implementations get default descriptions based on class name

### 7. SharedState Contract
- `SharedState` interface stays as-is
- Add `SharedState.toJson()` / `SharedState.fromJson()` for serialization
- Keep `encodeTo`/`deepCopy` for internal use; JSON is for external consumers

### 8. Module Boundaries
- `dev.samhb.interleave` — public API package (`Interleave`, `Strategy`, `VerificationResult`, `Trace`, `Invariant`)
- `dev.samhb.interleave.api` — builder/facade classes
- `dev.samhb.interleave.internal` — existing implementation classes stay here or move to `impl`
- Gradle: separate `api` and `internal` visibility via `public`/package-private modifiers; no JPMS module system initially

### 9. Distribution
- Add a `interleave-api` Maven/Gradle module? No — single-project Gradle is fine.
- Publish to Maven Central? Out of scope; local JAR consumption is the goal.
- Add a `consumer-producer` sample in `examples/` showing how a Gradle/Maven project depends on `interleave`.

### 10. Backward Compatibility
- CLI mode (`Main`) continues to work unchanged.
- Existing internal code moves to `internal`/`impl` packages but keeps working.
- New public types are additive; no breaking changes to existing public types.

## Acceptance Criteria
- External Gradle project can add `interleave` as a dependency and compile against `dev.samhb.interleave.Interleave`.
- `Interleave.verify(...)` returns a `VerificationResult` with deterministic fields.
- `VerificationResult` is JSON-serializable with a standard library (e.g., Jackson/Gson).
- All existing CLI tests still pass.
- New integration test in `examples/` or `src/test/java/.../api/` demonstrates library usage.

## Out of Scope
- OSGi/JPMS module system.
- Remote/async execution beyond `CompletableFuture` wrappers.
- Binary compatibility guarantees across major versions.

## Implementation Order
1. Promote `Invariant`, `Trace`, `Step` to stable public API with documentation.
2. Add `Interleave` facade + `Strategy` enum + `VerificationResult`.
3. Add `Program.builder()` and make `Program` constructor package-private.
4. Add JSON serialization to `SharedState` and `VerificationResult`.
5. Wire `Interleave.verify()` to existing explorers.
6. Add integration test from an external consumer perspective.
7. Add `examples/` directory with sample usage.
