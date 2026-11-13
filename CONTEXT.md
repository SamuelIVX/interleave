<!-- Interleave domain vocabulary; behavior and implementation decisions belong in specifications. -->
# Interleave

Vocabulary for explicit-state checking across existing models and the selected bytecode profile.

## Language

**Actor**: A fixed modeled participant whose actions the checker schedules. In the bytecode profile an actor is an annotation-declared method invocation on the shared fixture.

**Scheduling transition**: One selected actor's observable operation and any justified local computation around that operation. It is distinct from an executed opcode.
_Avoid_: instruction when referring to a scheduler choice.

**Instruction fuel**: The remaining allowance for interpreted opcodes in one actor's execution, setup, or one check invocation.
_Avoid_: transition limit.

**Cutoff**: An omitted modeled operation that ends one bounded execution prefix. It does not freeze the actor for continued execution of that prefix.

**Resource interruption**: An external checker stop, such as a deadline or global memory safeguard. It establishes incompleteness without asserting a reproducible modeled cutoff.

**Sharedness history**: Irreversible knowledge that an object has become shared during an execution, even if its current references later change.

**Bytecode location**: An artifact-bound class, method descriptor and bytecode index, optionally accompanied by a compiler line number.
_Avoid_: source span for bytecode evidence.
