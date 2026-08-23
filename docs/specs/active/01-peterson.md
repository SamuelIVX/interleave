# Spec 01 — Peterson's Mutual Exclusion

## TL;DR
Model Peterson's two-thread mutual exclusion algorithm. Encode a hand-written
interleaving as a `Schedule`, run it through the model checker, and assert
that the resulting configuration respects mutual exclusion and alternation.

## Acceptance Criteria
- A Peterson program with 2 threads can be encoded.
- `ExecutionDriver.run(program, schedule)` produces a sequence of configurations.
- The final configuration is either:
  - all threads terminated, or
  - a deadlock candidate with no enabled threads.
- Mutual exclusion is verified: at most one thread is in the critical section
  at any point in the schedule.
- Alternation is verified: the thread that enters the CS first is the one
  that `turn` favors.

## Out of Scope
- N-thread generalization (Dekker-style or bakery).
- Proof of full state-space coverage.
- Dynamic thread creation.

## Commands
```bash
./gradlew test --tests "*PetersonScheduleTest*"
```

## Map
- `src/main/java/dev/samhb/modelcheck/core/` — model checker core
- `src/test/java/dev/samhb/modelcheck/core/PetersonScheduleTest.java` — Spec 01 tests
