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
Arrays, actor allocations/general dispatch, user catch/finally, wider primitives/core libraries,
CBS/DPOR/bitstate and atomics belong to later selected phases. Weak memory remains unselected. Compiler-generated synchronized
cleanup is supported; runtime null/division faults remain modeled behavior rather than loader errors.

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
are strings/raw bits, reserved for later execution coverage. Native Java streams are build-specific:
this release changes generated serialization identities, and incompatible older streams reject.
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
