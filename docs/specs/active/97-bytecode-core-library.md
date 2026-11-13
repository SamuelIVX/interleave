<!-- Roadmap #12 / 97: implementation milestone specification; no production implementation in this PR. -->
# Spec 97: Primitive and descriptor-level core library

## Objective

Deliver primitive and descriptor-level core library as one coherent stage of bounded Java bytecode checking.
**Type: delta. Status: specified, not implemented or measured.** The
[shared #12 contract](../../plans/bytecode-contract.md) is normative for common semantics, limits,
interface signatures, profile exclusions, phase ownership and verification. This spec owns 97 only.

## Scope

Extend bytecode/ OpcodeExecutor/value/heap/encoder and IntrinsicRegistry (planned internal name).
Create descriptor/cost/payload table in this spec's implementation ledger, bounded String/wrapper/builder
storage, exact bootstrap validation and pure-check admission metadata. Update snapshots/replay/report JSON,
viewer lossless values, README/examples/compiler fixtures and BytecodePrimitiveTest/BytecodeIntrinsicTest.
Atomics remain 100; no arbitrary JDK callbacks or host execution.

## Non-Goals

No expansion beyond this milestone's behavior. No host execution of target code, executable application
initializers, dynamic actors, collections, wait/notify, weak memory, bytecode static POR/symmetry,
frozen exhausted actors or fuel-free cycle detection. Later-phase support stays rejected until its
complete implementation and consumers merge.

## Invariants

- Existing explicit/source/JSON/replay/symmetry behavior, validation and mutation policy remain intact.
- Owned state and deterministic identities preserve aliases, resource counters and sharedness history.
- Cutoffs end prefixes; external stops and observer errors remain honestly incomplete.
- All executed target behavior stays interpreted; no host fallback or forged source locations.
- Public selection follows the phase release boundary and never advertises partial consumer support.

## Requirements

1. **R1 — Primitives.** WHEN admitted opcodes execute, THE SYSTEM SHALL implement byte/short/char/int/
long/float/double/boolean conversions, category-2 stack/local rules, overflow/shifts, comparisons,
NaNs/infinities/signed-zero and narrowing per JVM semantics. Floating arithmetic SHALL preserve SC
visibility and lossless raw-bit snapshots, not JavaScript rounded representations.
2. **R2 — Finite intrinsics.** WHEN a platform call occurs, THE SYSTEM SHALL admit only the exact
owner/name/descriptors below, with explicit atomicity, effects, fault, fuel and payload accounting.
Unsupported overloads/bootstraps SHALL reject during loading; an implementation whitelist alone SHALL
not define the documented contract.
3. **R3 — Identity and storage.** WHEN strings/wrappers/builders are created, THE SYSTEM SHALL enforce
UTF-16 limits/cells, deterministic bounded constants/caches and observable allocation/publication.
Integer cache SHALL be exactly -128..127. Each user callback SHALL execute as interpreted application
code; host toString/equals/hashCode SHALL never substitute.
4. **R4 — Bootstrap and checks.** WHEN javac string concatenation is encountered, THE SYSTEM SHALL
admit only StringConcatFactory.makeConcatWithConstants with validated recipe/constant types and exact
invocation descriptor. It SHALL lower its specified cost/effects deterministically, reject lambdas/other
bootstraps, and reject allocating/mutating intrinsics in checks.
5. **R5 — Verification and consumers.** WHEN widened values/library support is released, THE SYSTEM
SHALL extend strict replay, lossless JSON/native snapshots, browser rendering and normalized two-mode
oracles. JVM differential tests SHALL cover boundaries and intrinsic faults rather than host fallback.

## Failure Modes & Edge Cases

- WHEN metadata, descriptors or reachable behavior is unsupported, THE SYSTEM SHALL reject before
exploration with actionable identity/location diagnostics; dynamic supported faults SHALL remain runtime behavior.
- WHEN actor/setup/observer budgets fail, THE SYSTEM SHALL preserve their separate ownership and the
shared priority, exact-limit semantics and omitted operation. A check/setup failure SHALL not become an actor violation.
- WHEN a run stops externally, THE SYSTEM SHALL retain original INCOMPLETE classification and genuine
already-recorded findings. It SHALL not manufacture a reproducible modeled resource cutoff.
- WHEN this milestone exposes new payload/state, THE SYSTEM SHALL include every future-relevant field
in keys and replay validation, including pending terminal events and permanent allocation charges.

## Acceptance Criteria

- R1: category-2 dup/pop variants, wide locals, signed shifts, MIN/-1 divide/remainder, NaN compares,
float-to-integral saturation/truncation and raw-bit round trips. Literal expected boundary bits derive
outside the interpreter; canonical keys distinguish future-relevant signed zero/raw representations.
- R2/R3: every admitted descriptor has positive/negative tests including null/type/length/fuel/cells,
cache edges and uncached allocation identity. Shared/possibly escaping StringBuilder models,
including two concurrent appends with final length2 check, reject before search; local builders and
publication of their immutable toString results succeed. Deterministic cache/constant materialization precedes actors.
- R2: `stringEqualsFuelBoundaries` uses the literal costs in the equality table below, checking exact
allowance success and one-unit-short cutoff for every row. Verify true/false/NPE outcomes, unchanged
state/fuel on an omitted invocation, and the invoke BCI/required work/remaining allowance in evidence.
- R4: Java17/21/26 string switches, primitive concat and unsupported Object-concat/callback cases;
unsupported variants reject with descriptor/bootstrap diagnostic. No enum/assertion clinit exemption.
- R5: long extrema, signed zero, infinities and NaN payloads survive Java→JSON→viewer→replay fixtures.
Re-run sequential generator and reference/batched normalized outcomes on new operations.

## Refactor Baseline & Rollback

- Pre-change baseline: fetch and record actual merged-main SHA at implementation start; [96](96-bytecode-exceptions.md) merged, plus the shared contract.
Record fresh full build/strict Javadoc/tests and affected/default PIT fingerprints before production edits.
The authoring SHA and older counts are historical context, never fresh implementation baselines.
- Call-site migration: all paths listed in Scope, plus their existing connected tests and exports;
new profile state reaches executor, encoder, replay and consumers in this same milestone where exposed.
- Preserve existing public signatures/semantics, source forward validation, mutation operators/floors,
resource settings and CI parallelism. No hidden compatibility adapter may bypass validation.
- Roll back/restrict the newly admitted profile coherently if normalized reference outcomes diverge,
state/replay identity omits future behavior, cleanup/evidence is fabricated or legacy compatibility fails.
Do not weaken tests, change bounds to conceal a counterexample or silently enable a later phase.

## Design

Use the shared contract's deep loader/executor/search seams: immutable validated metadata and program,
owned execution state, one opcode executor and mode-aware scheduler, then existing result/consumer
adapters where contracts permit. Keep bytecode continuation identity separate from source forward PCs.
Do not add a generic backend/framework for hypothetical languages. Internal names in Scope are planned;
choose existing equivalent modules at implementation after connected-source inspection.

### Descriptor registry and intrinsic cost model

This finite table is the selected library profile. Internal generated equivalent owner names do not
widen it; inheritance uses resolved declaring owner. Admitted calls are one SC intrinsic operation; mutable StringBuilder calls are admitted only for
proven actor-local builders (or setup-local builders discarded before actors), as specified below.
Except for96's synchronized Throwable entry/body/exit protocol, other methods have one SC intrinsic body,
with one invoke-opcode fuel plus the additional units stated here. Dynamic faults consume the invoke
opcode and work already completed; no partial target mutation on failed validation. Reads/writes/
allocations remain observable even inside intrinsics; non-atomic interpreted user code is not folded
into a host call. Length-dependent costs use input logical UTF-16 units/elements (u); max(1,u) units
in addition to invoke fuel. An intrinsic requiring more total fuel (invoke plus additional units) than remains omits the entire
invocation: no opcode/work/cell/identity charge and no payload change, with a cutoff at the invoke BCI
recording required work and remaining allowance. Thus an expensive intrinsic can cutoff with positive
remaining fuel; the ordinary N/N+1 rule still applies to unit-cost opcodes. Pure preflight validates
operands to determine the normal or fault cost; failed dynamic preconditions cost invoke plus one
intrinsic unit, if available. Costs are debited together only after selection, never by a probe.
Normal completion at exactly the last unit succeeds. String copies read immutable UTF-16 contents. Array clone is admitted only when whole-program
conservative analysis proves its source elements cannot mutate concurrently (actor-exclusive source
or no actor writes after setup, through every alias/dispatch alternative). Unknown proof rejects
before search. Do not treat a concurrently mutable shared array clone as an atomic snapshot.
A two-actor fixture with two ordered source writes and cloning must reject rather than falsely exclude
mixed copied values; an equivalent source-proven-readonly fixture and local array cloning succeed.
General racy cloning requires separately scheduled copy-read semantics and is not enabled here.

| Owner | Exact names/descriptors | Additional fuel and modeled effects |
|---|---|---|
| java/lang/Object | `<init>()V`, `equals(Ljava/lang/Object;)Z`, `hashCode()I` | 1; constructor no payload, equals identity, hash deterministic from identity with specified stable encoding |
| java/lang/String | `<init>()V`, `<init>(Ljava/lang/String;)V`, `length()I`, `isEmpty()Z`, `charAt(I)C`, `equals(Ljava/lang/Object;)Z`, `hashCode()I`, `toString()Ljava/lang/String;` | Scalars 1, copy/hash max(1,u); equality cost defined below; UTF-16 payload, Java polynomial hash, toString returns this |
| java/lang/String | `concat(Ljava/lang/String;)Ljava/lang/String;`, `valueOf(I)Ljava/lang/String;`, `valueOf(J)Ljava/lang/String;`, `valueOf(Z)Ljava/lang/String;`, `valueOf(C)Ljava/lang/String;` | max(1,result units); deterministic text; Z overload returns interned true/false literals with no call-time allocation; concat(empty) returns receiver; I/J/C results allocate |
| java/lang/Integer | `valueOf(I)Ljava/lang/Integer;`, `intValue()I`, `equals(Ljava/lang/Object;)Z`, `hashCode()I` | 1; exact -128..127 cache; header+one field otherwise |
| java/lang/Boolean | `valueOf(Z)Ljava/lang/Boolean;`, `booleanValue()Z`, `equals(Ljava/lang/Object;)Z`, `hashCode()I` | 1; exactly two cached identities |
| java/lang/Byte, Short, Long, Character | valueOf primitive → matching wrapper and matching primitiveValue getter; equals(Object)Z, hashCode()I | 1; Byte all values, Short/Long -128..127, Character 0..127 cached; no extended caches |
| java/lang/Float, Double | valueOf primitive → matching wrapper and matching primitiveValue getter; equals(Object)Z, hashCode()I | 1; no caches; wrapper NaN canonical equality/hash, signed zero distinct per Java wrapper semantics |
| java/lang/Math | `abs(I)I`, `abs(J)J`, `min(II)I`, `max(II)I`, `min(JJ)J`, `max(JJ)J` | 1; Java overflow (including MIN abs); floating/transcendental overloads reject |
| java/util/Objects | `requireNonNull(Ljava/lang/Object;)Ljava/lang/Object;`, `requireNonNull(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;`, `isNull(Ljava/lang/Object;)Z`, `nonNull(Ljava/lang/Object;)Z` | 1; NPE for null requireNonNull; supplier/callback overloads reject |
| java/lang/StringBuilder | `<init>()V`, `<init>(I)V`, `<init>(Ljava/lang/String;)V`, `append(I)Ljava/lang/StringBuilder;`, `append(J)Ljava/lang/StringBuilder;`, `append(Z)Ljava/lang/StringBuilder;`, `append(C)Ljava/lang/StringBuilder;`, `append(Ljava/lang/String;)Ljava/lang/StringBuilder;`, `length()I`, `toString()Ljava/lang/String;` | Scalars 1, construction/append/copy max(1,result units); mutable UTF-16 logical capacity and length, bounds as String; append null String writes "null"; empty toString returns interned empty literal without allocation |
| array receiver | `clone()Ljava/lang/Object;` with array owner descriptor | max(1,length); source proven free of concurrent mutation; shallow array of identical component/rank, fresh identity and header+elements charge |
| Throwable family | Only descriptors established in 96 | Scalars 1; suppression copy max(1,count); allocated exception/header fields and returned arrays charged |

For `String.equals(Ljava/lang/Object;)Z` with a non-null receiver and a String argument,
`u = max(receiver UTF-16 length, argument UTF-16 length)` and additional intrinsic fuel is `max(1,u)`.
Use this fixed cost even for identical references, unequal lengths or an early content mismatch;
the result is true exactly when UTF-16 contents match. A null or non-String argument returns false
with one intrinsic unit. A null receiver raises NPE with one intrinsic unit under the existing fault
rule. Every case additionally costs the invoke opcode's one unit and uses full-cost preflight.
These are profile fuel charges, not measured host-JVM work.

| Receiver | Argument | Outcome with sufficient fuel | Total fuel | One-unit-short allowance |
|---|---|---|---:|---:|
| `"abc"` | `"abc"` | true | 4 | 3 |
| `"ab"` | `"ac"` | false | 3 | 2 |
| `"ab"` | `"abcd"` | false | 5 | 4 |
| `"abcd"` | `"ab"` | false | 5 | 4 |
| `""` | `""` | true | 2 | 1 |
| `"abc"` | null | false | 2 | 1 |
| `"abc"` | plain Object | false | 2 | 1 |
| null | `"abc"` | NPE | 2 | 1 |

The totals are independently specified literals: the invoke contributes one unit, the longest
two/three/four-unit String contributes two/three/four, and empty/null/type/fault cases contribute one.
Each one-unit-short case omits the whole invocation without charges, including the null-receiver case.

For wrapper shorthand above, exact primitive/getter pairs are Byte B/byteValue()B, Short S/shortValue()S,
Long J/longValue()J, Character C/charValue()C, Float F/floatValue()F and Double D/doubleValue()D;
valueOf returns `Ljava/lang/<Wrapper>;`. No constructor/numeric-conversion widening is implied.
When StringBuilder.toString() is reachable, preinstall the interned empty String in setup. Empty
builders return it with no call-time allocation/cells; nonempty results allocate a fresh immutable
String. Fuel remains invoke1+max(1,result units). Test empty identity against "", repeated calls and an
exhausted allocation budget (empty success versus nonempty cutoff). This follows
[OpenJDK26 StringBuilder.toString](https://github.com/openjdk/jdk/blob/jdk-26-ga/src/java.base/share/classes/java/lang/StringBuilder.java).
Object.getClass, clone on arbitrary Object, String.intern, Object-argument String.valueOf/append,
reflection, system clocks/identityHashCode and all other overloads reject.

Retain96's String representation: modeled header plus one length field plus UTF-16 units (no hidden backing-array double
charge). Builders have header plus length/capacity fields plus capacity units; default capacity 16,
explicit capacity n, String constructor capacity length+16. Growth uses max(2*old+2, required), checked
against String length cap and cell budget before mutation; negative/oversized capacities fault or cutoff
as specified by the shared priority. Growth charges incremental cells permanently to the builder's allocator. This initial StringBuilder
profile requires conservative whole-program nonescape proof: builders cannot reach the fixture,
statics, another actor or a published object, including through helper/dispatch alternatives. Unknown
escape rejects during loading; setup-created builders retained by the fixture therefore reject.
Only immutable String results may publish. Do not treat racy shared append as an atomic success or
use a runtime "unsupported" verdict after exploration. General shared StringBuilder support would
require separately specified internal scheduling, pending operations and race/fault semantics; it is
not enabled by this registry. This restriction does not weaken generic application-object publication
or permanent allocator charging. A model with two actors appending to one builder and a final length
check must reject before search rather than falsely pass because both appends were serialized.
Wrapper payload is one field. Throwable payload layout is fixed in 96's registry and encoded explicitly.
String constants are interned by identical UTF-16 contents in a bounded setup-owned deterministic table;
when String.valueOf(Z) is reachable, preinstall both "true" and "false" with ordinary literal setup
cell/length accounting. Calls return those same literal identities without allocation; their fuel cost
remains max(1,result units). Add JVM differential identity cases for both boolean results against literals.
This follows [OpenJDK26 String.valueOf](https://github.com/openjdk/jdk/blob/jdk-26-ga/src/java.base/share/classes/java/lang/String.java);
class monitors and wrapper caches have tagged stable identities, charged to setup cells before actors.
Record exact cache/table cost on setup and reject a setup limit rather than lazily charging whichever
actor first touches a constant. Object hashCode is FNV-1a-32 (offset 2166136261, prime 16777619, modulo 2^32) over one namespace tag
byte (setup=0, actor=1, constant=2), big-endian signed 32-bit owner/subnamespace and big-endian 64-bit
sequence. Setup owner=0; actor owner=id; constants use stable subnamespace IDs 0=class-monitor,
1=String, 2=Integer, 3=Boolean, 4=Byte, 5=Short, 6=Long, 7=Character, 8=empty-Throwable-array, with sequence from sorted class
names, sorted UTF-16 contents or ascending cached values. Hash may collide and is unchanged by
publication; keying/equality never relies on hash uniqueness. This modeled deterministic hash is not
required to equal the host JVM's unspecified identity hash.

Concat accepts only primitive/String operands and primitive/String constants in a verified
makeConcatWithConstants recipe; Object conversions/callbacks reject. Recipe cost is max(1, output
units) plus one per operand conversion; result String allocation obeys cells/count/length caps. Any
interpreted callback elsewhere must have an explicit registered descriptor and scheduling/cost
contract before admission; this table enables no additional callback-taking intrinsic.

## Current State

- [verified] Planning main is `2a242c4`; the existing engine has immutable Program, forward SourceStep
continuations and source-specific evidence. The shared contract records inspected integration constraints.
- [approved decision] This milestone and the shared contract define the selected behavior/release boundary.
- [unverified] This milestone's bytecode implementation, measurements, compiler/runtime acceptance and
mutation results. No authoring review establishes those results.

## Tests

`allPrimitiveEdgesMatchIndependentJvmCases` (R1/R5), `intrinsicDescriptorsAreClosed` (R2),
`cacheIdentityAndPayloadBudgetsAreDeterministic` (R3), `concatRecipesNeverCallHostCallbacks` (R4),
`wideValuesRoundTripWithoutNumericCoercion` (R5). Kill descriptor broadening, cache-boundary shifts,
missing payload charges, NaN comparison inversion and lossy long/raw-bit conversion.
These are intended behavioral contracts/test names, not tests already implemented or executed.
Use independent expected values/oracles, preserve distinct regression cases and save generated seeds.

## Constraints

- Dependencies: [96](96-bytecode-exceptions.md) merged, plus the shared contract; sequential publication into main, with Sam's merge update before the next stage.
- Backward compatibility: all existing explicit/source/declarative inputs, results and stores retain
behavior; source evidence is never manufactured for bytecode and native-stream changes are versioned.
- Oracle contract: [shared verification/measurement gates](../../plans/bytecode-contract.md#verification-and-measurement-gates).
- All local Gradle commands include `--max-workers=1 -PpitestThreads=1`. Full strict Javadoc/build/tests,
affected mutation census plus unchanged default PIT gate, and relevant consumers are mandatory during
implementation. NO_COVERAGE is not a kill; report scoped/default totals and inherited residual identities.
- Create `docs/measurements/97-bytecode-core-library.md` with implementation: fresh baseline/final evidence,
independent checks, raw measurement conditions, review dispositions and limits. Authoring evidence belongs
in the specification-set record, not a fabricated runtime ledger.

## Relevant Skills and Review

- doc-coauthoring: requirements/acceptance completeness and fresh-reader testing.
- domain-modeling / codebase-design: reuse [domain vocabulary](../../../CONTEXT.md) and the shared
small owned interfaces; do not create a competing glossary or source-shaped bytecode abstraction.
- tdd: establish failing behavioral regressions before new production behavior; fixed-seed/shrinking
oracles supplement targeted cases. owasp-security: fail-closed untrusted class metadata and replay input.
- ponytail / ponytail-review: smallest coherent implementation and connected-source review.
- code-review: actual CodeRabbit after this completed specification and before substantial implementation
publication; record completed/quota/error status through agent-setup without inventing completion.
- 94B/95/96/97: review the batching preservation argument for every newly admitted behavior; 99/100/101:
review reduction admission/resource/product obligations in addition to runtime tests.

The specification-only PR runs document/link/structure validation, reader review and required actual
CodeRabbit invocations. It does not run Java/PIT/browser feature checks for code that does not exist.
After publishing this milestone's implementation PR, report its link and stop; do not watch CI.
