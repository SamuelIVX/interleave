<!-- External compiler inputs for the94C public bytecode vertical release. -->
# Bounded Java bytecode examples

These models exercise real compiled Java under Interleave's bounded sequentially consistent profile.
`LostUpdate` admits a false final check with counter1 and successful completion with counter2.
`TwoLockDeadlock` admits a concrete wait cycle and successful serialized execution. Neither example
creates threads or executes target code on the host JVM during checking.

Build Interleave with JDK26, then explicitly compile the examples into a directory:

```sh
./gradlew --max-workers=1 -PpitestThreads=1 build
javac --release 17 -g -cp build/libs/interleave-1.0-SNAPSHOT.jar -d build/bytecode-examples examples/bytecode/LostUpdate.java examples/bytecode/TwoLockDeadlock.java
./gradlew --max-workers=1 -PpitestThreads=1 run --args='--bytecode examples.bytecode.LostUpdate --class-path build/bytecode-examples --strategy DFS --store exact --json'
./gradlew --max-workers=1 -PpitestThreads=1 run --args='--bytecode examples.bytecode.TwoLockDeadlock --class-path build/bytecode-examples --strategy DFS --store exact'
```

The jar basename above assumes the clone directory is `interleave`; use the actual jar in `build/libs`
when building an isolated worktree with a different directory name. The loader never compiles inputs
or discovers jars. Class-directory entries must remain immutable regular files while loading. After
successful loading, changes to that directory cannot alter the frozen program or its replay identity.

Library entry points are `BytecodeProgramLoader.load(directory,binaryName)`,
`InterleaveRunner.builder().build().runBytecode(program)` and
`new TraceReplayer().replayBytecode(program,record)`. Custom immutable `BytecodeLimits` are optional.
Only exact DFS is available in this release; incompatible strategies, stores, generic properties and
symmetry selections reject before invoking callbacks. JSON records retain BYTECODE identity and real
method/descriptor/BCI locations, with compiler lines when available. Recompile with `-g:none` to omit
line tables; replay remains based on exact artifact bytes and real BCIs.

Use fixture-constructor fields for locks. A declaration such as `static final Object LOCK=new Object()`
requires executable `<clinit>` and rejects, as do ordinary static arrays, enum initialization and
compiler-generated assertion-status setup. Use declared `@Check` methods instead of Java `assert`.
Core-library computation,
CBS/DPOR/bitstate and atomics belong to later selected phases. Weak memory remains unselected. Compiler-generated synchronized
cleanup is supported; runtime null/division faults remain modeled behavior rather than loader errors.

The current profile (`BYTECODE-SC-4`) also interprets actor-created application objects, inherited/hidden
fields, constructor chaining, closed virtual/interface dispatch and private nest access. Arrays retain
exact rank/component types, reference aliases and per-dimension allocation charges; all primitive and reference array loads/stores are supported. Long and double occupy two local slots
and one logical operand. Long/double shared accesses are single SC accesses in this profile; no
Java Memory Model tearing or weak-memory behavior is claimed. Array cloning remains in97 library work.
Null, bounds, negative-size, array-store and cast faults are modeled, including monitor cleanup.

`WidePrimitives.java` exercises wide arguments/arrays, a long overflow result, negative zero and
runtime IEEE infinity. Its declared FINAL check passes. Compile it with the same explicit classpath
and select `--bytecode examples.bytecode.WidePrimitives`. All primitive arithmetic, shifts,
comparisons and conversions follow JVM computation rules. Integer division/remainder by zero faults;
floating division produces IEEE values. Primitive field/return narrowing follows the declared type;
invocation arguments retain their computational values. Raw constant-pool floating bits survive
transport without conversion through potentially NaN-collapsing Class-File API numeric accessors.
Arithmetic/FP-width conversion NaNs use profile-canonical positive quiet bits for replay across
hosts; FNEG/DNEG flip sign bits directly. Transport never canonicalizes payloads. General String/wrapper/Math/builder/concat
operations remain rejected until the next97 PR completes their descriptors, costs and proofs.

`HeapPublication.java` demonstrates a published cycle with an aliased array and interface check:

```sh
javac --release 17 -g -cp build/libs/interleave-1.0-SNAPSHOT.jar -d build/bytecode-examples examples/bytecode/HeapPublication.java
./gradlew --max-workers=1 -PpitestThreads=1 run --args='--bytecode examples.bytecode.HeapPublication --class-path build/bytecode-examples --json'
```

Allocations are charged permanently to setup or the allocating actor, including after escape and loss
of reachability. Identities are namespace plus sequence and are never renumbered/reused. A failed
multidimensional allocation retains earlier successful dimensions in cutoff evidence. No actor is
frozen after exhaustion: that entire prefix ends and sibling alternatives continue. Each object costs
one header cell plus inherited instance fields; each array costs one header plus length elements.
Unknown reachability is shared; sharing is irreversible within a run. This implementation conservatively
keeps all field/array accesses and allocations as scheduling boundaries, including local heap accesses.
Local arithmetic/transport still batches; REFERENCE and BATCHED use one executor.

The exception profile also supports ordered typed/catch-all handlers, rethrow, compiled finally and
try-with-resources using interpreted application `AutoCloseable.close` callbacks. Throwable,
Exception, RuntimeException and selected VM exception constructors retain message/cause/suppression
state. String literals and String ConstantValue fields are interned immutable UTF-16 payloads;
message identity is preserved, but String computation and general JDK execution remain unsupported.
Unknown implicit VM message inspection rejects before exploration. In particular, a catch block
calling `getMessage` on an implicit null/division fault is unsupported. Conservative joins can also
reject an explicit message whose receiver origin is unknown; a statically typed application exception
subclass or a proven straight-line explicit allocation avoids that ambiguity.

`ResourceCleanup.java` preserves the body failure, closes two resources in reverse order and retains
their ordered suppressed failures. Its FINAL check passes:

```sh
javac --release 17 -g -cp build/libs/interleave-1.0-SNAPSHOT.jar -d build/bytecode-examples examples/bytecode/ResourceCleanup.java
./gradlew --max-workers=1 -PpitestThreads=1 run --args='--bytecode examples.bytecode.ResourceCleanup --class-path build/bytecode-examples --json'
```

A Throwable costs six base cells plus inherited application fields and one cell per suppressed
reference. Appended suppression remains charged to the exception's original allocator, even when
another actor performs the call. Nonempty `getSuppressed` results allocate fresh arrays charged to the
caller; empty results share a bounded constant array. Constants consume setup cells without consuming
setup application-allocation counts or changing application identities. Strings cost a header, length
and one cell per UTF-16 unit. No target objects are garbage-collected or renumbered.

Trusted synchronized getters and suppression operations acquire the receiver monitor and can block or
deadlock. Entry/body/exit are separate events; full instruction fuel is checked before blocking.
`NullPointerException.getMessage` also acquires its receiver monitor for an explicit null message;
a non-null explicit message returns without that acquisition. Potential NPE getter targets and other
synchronizing intrinsics reject in checks. Constructor internal stack-trace synchronization is modeled,
but stack-trace APIs are unsupported.
Throwable `initCause`, cause-only platform constructors and application `fillInStackTrace` overrides
reject because they can invoke callbacks outside this narrow intrinsic contract. Error/LinkageError
metadata supports VM-fault ancestry and catches; it does not admit their arbitrary construction.

Current evidence uses schema2/profileSC4/canonical state4. SC1/SC2 schema1 and SC3 schema2 evidence remain displayable,
but current strict replay rejects older semantic identities. New cutoffs record the executing actor
and the charged budget owner separately; legacy absent ownership is never guessed. The source/JSON
model routes retain their existing semantics. [The96 ledger](../../docs/measurements/96-bytecode-exceptions.md)
records actual exception acceptance, measurements and the batching preservation argument; the95 ledger
retains the historical heap-profile evidence.

[The shared contract](../../docs/plans/bytecode-contract.md) defines the complete supported profile.
The [94B ledger](../../docs/measurements/94B-bytecode-concurrency.md) contains real reference/batched
measurements and the preservation argument. These examples establish no Java Memory Model coverage.

## Replay and transport

```java
import dev.samhb.interleave.InterleaveRunner;
import dev.samhb.interleave.TraceRecord;
import dev.samhb.interleave.format.BytecodeProgramLoader;
import dev.samhb.interleave.search.TraceReplayer;
import java.nio.file.Path;

var program = BytecodeProgramLoader.load(Path.of("build/bytecode-examples"),
    "examples.bytecode.LostUpdate");
var result = InterleaveRunner.builder().build().runBytecode(program);
var saved = result.failingTraces().getFirst().toJson();
var replayed = new TraceReplayer().replayBytecode(program, TraceRecord.fromJson(saved));
```

`TraceRecord.fromJson` imports only the distinct
BYTECODE schema and does not authenticate target execution; strict replay performs that check.
It rejects duplicate keys, conflicting fields, unknown schemas and scalar coercions, with limits of
16 Mi UTF-16 code units of JSON text, 64 nesting levels and one million JSON nodes. Tagged wide numeric values
are decimal strings or fixed-width lowercase raw-bit hex, including noncanonical NaNs and signed zero. Native Java streams are build-specific:
the current BYTECODE payload is versioned. The previous cutoff record remains readable with an absent
legacy budget owner; strict replay still requires the current artifact/profile/limits. Incompatible
older record shapes reject.
Streams produced before the 94C field additions (including merged 94B at `6eb939e`) are outside the
compatible native shape. Export JSON using the producing build before upgrading; a source/declarative
record must continue through its existing consumer route. This importer accepts BYTECODE only.
Keep the original class files for cross-process replay; debug metadata is part of artifact identity.

The viewer accepts result JSON, report JSON or a standalone BYTECODE witness. It shows genuine
method/descriptor/BCI locations and optional compiler lines, monitor waits and exact omitted cutoff
operations. Viewer validation checks shape and precision; only Java strict replay authenticates the
record against an artifact. A completed witness is one execution, not an exhaustive PASS. A global
stop keeps its original INCOMPLETE verdict even if replay observes a completed or failed prefix.

Default actor fuel remains 256 instructions, separately from scheduling transitions. Loops/retries
can exhaust it; a modeled cutoff ends that prefix and leaves the result incomplete. Observer faults
or exhaustion are incomplete rather than actor violations. `maxStates` and `maxTime` are checker
limits; the time limit is checked at scheduling boundaries. The internal exact-store safety budget is
64 MiB of canonical key payloads, excluding JVM overhead. Heap delta in reports is an instantaneous
JVM measurement, not retained-memory evidence. The 94B tiny-fixture measurements justify retaining
provisional defaults, without establishing performance for larger programs.
