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
CBS/DPOR/bitstate, atomics and weak memory belong to later phases. Compiler-generated synchronized
cleanup is supported; runtime null/division faults remain modeled behavior rather than loader errors.

[The shared contract](../../docs/plans/bytecode-contract.md) defines the complete supported profile.
The [94B ledger](../../docs/measurements/94B-bytecode-concurrency.md) contains real reference/batched
measurements and the preservation argument. These examples establish no Java Memory Model coverage.
