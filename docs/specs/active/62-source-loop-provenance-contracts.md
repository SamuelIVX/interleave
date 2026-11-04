# Spec: Source loop provenance contracts

## Objective and scope

Continue 9-V2 with the source spans, labels and occurrence paths emitted by repeat/while
expansion. Add SourceLoopProvenanceContractTest, this spec, the roadmap and a Spec 16 pointer.
Keep compiled metadata, recorded execution and omitted loop-cutoff evidence together because
they expose the same expansion identities.

No production, harness, dependency, mutation target/filter/operator/exclusion, denominator/floor,
heap/time or CI parallelism changes. Delimiter scanning and general lowering/validator proofs
remain separate; completed 9-V1 stays closed.

## Contract and independent oracles

[Spec 16.06](16-concurrent-program-parser/06-traces-replay-and-minimization.md) requires
source-aware execution and reproducible loop occurrences;
[Spec 16.04](16-concurrent-program-parser/04-incompleteness-and-verdicts.md) defines cutoff evidence. Repeat copies retain the repeat keyword offset and
zero-based occurrence. While checks and bodies retain their while keyword offset and iteration;
the final bound check uses iteration N. Generated checks keep the original while span and label.

Two cases share a real source model: repeat 2 contains a counter reset and while limit 2, whose
body increments the counter in one atomic action. The condition is either counter < 2 or true.
All twelve compiled instructions have independently stated spans, labels and outermost-first
paths, including the second repeat copy even in the cutoff case.

| Field | Literal expectation |
|---|---|
| Repeat keyword offset | 61 |
| While keyword offset | 104 |
| Reset span | 5:5–5:28 |
| While span, including generated checks | 6:5–8:6 |
| Atomic body span | 7:7–7:40 |
| Repeat occurrences | 0, 1 |
| While body/check occurrences | 0, 1 |
| Final while-check occurrence | 2 |

Offsets are zero-based UTF-16 positions in this ASCII fixture; span coordinates are one-based
and end-exclusive. Manual line-prefix lengths and independent Python character enumeration agree
on offsets 61/104 and all span endpoints. These values are literal Java expectations; no compiler
source-map, expansion or work helper computes them.

Each repeat compiles one reset, two check/body pairs and one final check. Manual enumeration and
an independent event-list expansion confirm 2 * (1 + 2 * 2 + 1) = 12 instructions. The completing
case commits all twelve; the permanently true case commits 1 + 2 * 2 = 5 actions, then omits PC 5.
The latter cutoff must have reason LOOP_LIMIT, owner 0, limit/iterations 2, the original while
span and path [(61, 0), (104, 2)]. Both traces replay to counter 2 and the literal expected PC.

The compiled ExecutedStep records are checked against literal source/path/label tables, then
the trace's committed records are checked against the appropriate validated prefix. This prevents
self-consistent compiler/replayer metadata errors from validating each other. A cutoff action
belongs only to cutoff evidence, not the committed thread/outcome/identity prefix.

The fixtures stay within all source, expression and expansion budgets. Delimiters are balanced;
no EOF delimiter fixture, reflection, fabricated private AST or huge loop expansion is added.
Existing replay, cutoff and loop-bound tests remain intact.

## Fresh baseline and selected ledger

Merged PR 103 at 95ba40d passes the fresh clean build, strict Javadoc and 1,174 tests, zero
failures/errors/skips. Fresh outer-compiler baseline is **717/787 KILLED, 39 SURVIVED,
31 NO_COVERAGE**, zero resource/run errors. All four selected expansion entries are SURVIVED.
The other 66 raw residuals comprise 13 existing scoped dispositions and 53 untriaged entries
outside this selection; no new dispositions are proposed.

All selected entries use NonVoidMethodCallMutator in expand, which has one descriptor:

| Removed call / index / block | Protected observation |
|---|---|
| Node.start / 54 / 16 | Repeat occurrence offset |
| Node.count / 92 / 31 | Final while-check occurrence N |
| Node.start / 112 / 38 | While iteration occurrence offset |
| Node.start / 145 / 47 | Generated while-check source span and label |

Freeze mutations.xml before later PIT invocations. Compare class, method, descriptor, mutator,
indices and blocks, then statuses; retain all 787 identities and all 717 baseline kills.
Scoped R6 applies; R5/R7 skip and require the separate default full gate. Resource errors never
earn kills. Parser, compiler, default and historical populations stay separate, with no aggregate
or whole-checker verification claim. The remaining expand count-field entry is outside this
selection, without an equivalence claim.

```bash
./gradlew --max-workers=1 -PpitestThreads=1 clean build javadoc pitest \
  -PpitestTargetOverride=dev.samhb.interleave.format.dsl.SourceCompiler
./gradlew --max-workers=1 -PpitestThreads=1 pitest
```

## Results

Both focused provenance cases and the integrated **1,176 tests** pass, with zero failures/errors/
skips. Build and strict Javadoc pass. Final compiler is **721/787 KILLED, 35 SURVIVED,
31 NO_COVERAGE**, zero resource/run errors. All 787 identities and all 717 prior kills remain.
Exactly the four selected SURVIVED entries become KILLED; all other statuses are unchanged.
The remaining 66 raw residuals comprise 13 existing scoped dispositions and 53 untriaged entries.
Scoped R6 passes; R5/R7 skip as designed.

CodeRabbit CLI 0.8.1 completed the initial four-file review with zero findings; only final
evidence prose followed. All 18 normalizer cases, relative links, credential-pattern scan and
diff checks pass. The separate default full mutation gate passes **281/291 KILLED, 10 SURVIVED**, zero uncovered/
resource/run errors. All 291 identities/statuses match Spec 61; full R5/R6/R7 pass. Browser checks are unnecessary
for unchanged application/rendering; remote CI is unverified.
