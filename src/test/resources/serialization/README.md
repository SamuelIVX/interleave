# Native Java result stream fixture

`test-result-ac16444.b64` is Base64-encoded Java object-stream test data, not a class file.
It was emitted using TestResult compiled from merged revision
ac16444e0dc48a5eb919aec9133aa1b9ed36425e with Java 26 and that revision's unchanged runtime
dependencies. The fixture contains a source result interrupted after two visits, with no traces:

```java
new TestResult(Strategy.DFS, 2, 1, 0,
    List.of(), List.of(), List.of(), List.of(), true)
    .withRunMetadata(true, ResourceLimitReason.MAX_STATES, false);
```

Write that value with ObjectOutputStream using the old build, then Base64-encode the resulting
bytes. Do not regenerate the fixture with the current TestResult class. The old computed UID is
6226768608649318615, independently inspected with serialver and the actual stream descriptor.
Decoded bytes: 683; SHA-256: 011c670b05f2d076be1e0959a971c48eba5a1f376b34c43b67fcee1137d01e62. Python hashlib and shasum agree.

PR #118 deliberately retains build-specific native serialization, matching the prior Spec 11
decision. Current readers reject this fixture with InvalidClassException at TestResult.
The test's isolated legacy reader contains only the old binary name and UID: it checks the native
UID rejection gate, not old field-mapping behavior. Separately, the complete old TestResult class
was used in fresh JVM probes to verify old→new, new ordinary→old and new symmetry→old rejection,
plus old→old and new symmetry→new success. Those probes avoid treating the minimal reader as a
full old-library compatibility test. No committed compiled class or new dependency is required.
