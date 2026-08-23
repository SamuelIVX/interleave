# Library/API Mode — Design Plan

## TL;DR
Expose `interleave` as a clean Java library so external tools can construct programs,
invoke verification, and consume results programmatically. Most types are already public;
the work is packaging, a stable facade, and a thin result wrapper.

## Current State (Accurate)
- `Program`, `Step`, `SharedState`, `Configuration`, all explorers, `Trace`, `Invariant` — all public.
- Only package-private member: `ModelThread.advance()`.
- `Program` constructor is public; takes `SharedState` + `List<ModelThread>`.
- `DfsResult` is public but carries internal concerns (raw state map, unfiltered traces).
- CLI (`Main`) prints to stdout; no structured return type for programmatic use.
- `build.gradle.kts` uses `application` plugin only; produces a distribution, not a library JAR.

## Goal
External callers can do:

```java
Program program = Interleave.program(state, thread0, thread1);
VerificationResult result = Interleave.verify(program, Strategy.DPOR);
if (result.hasViolation()) {
    Trace trace = result.failingTraces().get(0);
}
```

## Proposed API Surface

### 1. Public Facade: `Interleave`
- `Interleave.program(SharedState state, ModelThread... threads)` — static factory, validates non-null/non-empty.
- `Interleave.verify(Program program, Strategy strategy)` — returns `VerificationResult`.
- `Interleave.verify(Program program, Strategy strategy, Invariant invariant)` — with invariant checking.
- Overloads returning `CompletableFuture<VerificationResult>` for async use.

### 2. Stable Result Type: `VerificationResult`
Thin wrapper around internal `DfsResult` plus timing/memory:

```java
public final class VerificationResult {
    public boolean hasViolation();
    public List<Trace> failingTraces();      // invariant violations only
    public List<Trace> deadlockedTraces();    // deadlock candidates
    public List<Trace> completedTraces();     // all threads terminated
    public long statesExplored();
    public Duration wallTime();
    public long peakMemoryBytes();
    public Strategy strategyUsed();
}
```

**Relationship to `DfsResult`:**
- `DfsResult` stays internal.
- `VerificationResult` wraps it and filters traces by outcome type.
- `DfsResult.traces()` currently conflates violations, deadlocks, and completions. The wrapper adds `Trace.outcome()` annotation at creation time so filtering is unambiguous.

### 3. Strategy Enum
```java
public enum Strategy {
    DFS,
    STATIC_POR,
    DPOR
}
```
- Maps 1:1 to existing explorers: `DfsExplorer`, `StaticPorExplorer`, `DporExplorer`.
- `HASHING` is not a strategy — it's a state-store choice orthogonal to exploration. Drop it from the enum.
- `Strategy.explorer()` returns the corresponding explorer instance.

### 4. Invariant API
Already public in `dev.samhb.interleave.search.Invariant`:
```java
public interface Invariant {
    boolean holds(SharedState state, Configuration config);
}
```
- No promotion needed.
- Add combinators: `Invariant.and(Invariant...)`, `Invariant.or(...)`.

### 5. Program Construction
Drop the builder. Use a static factory:
```java
Program program = Interleave.program(state, thread0, thread1);
```
- `Program` constructor stays public for now; the factory adds validation without a full builder.
- If stricter immutability is needed later, make the constructor package-private and keep the factory.

### 6. Step API Stability
- `Step` interface stays as-is (already public).
- Add `Step.description()` default method returning `getClass().getSimpleName()`.
- Existing implementations inherit it; override only for human-readable names.

### 7. Serialization
- No JSON on `SharedState` interface — keeps domain model clean.
- Hand-roll JSON in `VerificationResult.toJson()` following existing `ReportWriter.writeJson()` pattern.
- No new dependencies. If structured serialization becomes a real need later, add Jackson/Gson then.

### 8. Trace Immutability
`Trace` is `final` and already uses `List.copyOf()` in its constructor. No change needed.

### 9. Package Boundaries
One public package: `dev.samhb.interleave`.
- Public types: `Interleave`, `Strategy`, `VerificationResult`, `Trace`, `Invariant`, `Program`, `Step`, `SharedState`, `ModelThread`, `Configuration`, `StepOutcome`.
- Implementation types stay in `dev.samhb.interleave.internal` or their existing subpackages (`core`, `search`, `state`, `por`, `dpor`).
- No `api` subpackage.

### 10. JAR Packaging
- `java-library` plugin already added.
- Consumers can depend on the project JAR via `api` or `implementation`.
- CLI main class stays in `application` block; library consumers ignore it.

### 11. Backward Compatibility
- Existing public types stay in place; no package moves.
- New types (`Interleave`, `Strategy`, `VerificationResult`) are additive.
- CLI behavior unchanged.

## Acceptance Criteria
- External Gradle project can add `interleave` as a dependency and compile against `dev.samhb.interleave.Interleave`.
- `Interleave.verify(...)` returns a deterministic `VerificationResult`.
- `VerificationResult` can be serialized to JSON without external libraries.
- All existing tests pass (currently 21/25; 4 pre-existing failures in DeltaDebugger/StaticPorExplorer, unrelated to this work).
- New integration test demonstrates library usage from an external consumer perspective.

## Out of Scope
- OSGi/JPMS module system.
- Remote/async execution beyond `CompletableFuture` wrappers.
- Binary compatibility guarantees across major versions.
- Moving existing public classes to new packages.

## Implementation Order
1. Add `Trace.outcome()` annotation at creation time in explorers.
2. Add `VerificationResult` wrapper + JSON serialization.
3. Add `Interleave` facade + `Strategy` enum.
4. Add `Interleave.program()` factory.
5. Wire `Interleave.verify()` to existing explorers with timing/memory measurement.
6. Add integration test from external consumer perspective.
7. Add `examples/` directory with sample usage.
