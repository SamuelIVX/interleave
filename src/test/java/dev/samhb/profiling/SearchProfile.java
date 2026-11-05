/** Opt-in, dependency-free search measurements; never packaged with the checker. */
package dev.samhb.profiling;

import com.google.gson.GsonBuilder;
import com.sun.management.ThreadMXBean;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.dpor.SleepSet;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.search.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import jdk.jfr.*;
import jdk.jfr.consumer.*;

/** Runs fixed source workloads with plain trials separated from sampled JFR attribution. */
public final class SearchProfile {
    /** Retains results across calls so the workload remains observable. */
    private static volatile DfsResult retained;
    /** Sorted terminal values independently enumerated in Python for four owners. */
    static final String DEPENDENT_DIGEST = "051e302cc07bc145c4c8677f4bd1268fafd6e49f40974980804b43569e6c8a87";
    /** Independent SHA-256 of the normalized singleton observation, zero plus newline. */
    static final String SINGLE_DIGEST = "9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa";

    /** Prevents construction of this executable utility. */
    private SearchProfile() {}

    /** Fixed exploration paths, each using a fresh exact store per invocation. */
    enum Strategy { DFS, POR, DPOR }

    /** Source fixture and its independently specified terminal oracle.
     * @param kind dependent, independent or contexts
     * @param owners number of two-action owners
     * @param model compiled source, outside measured regions
     */
    record Fixture(String kind, int owners, BenchmarkProgram model) {}

    /** Search counters; visits minus unique measures accepted revisits, not antichain width.
     * @param visits accepted search positions including revisits
     * @param unique distinct configuration keys
     * @param traces raw emitted witnesses
     */
    record Counts(long visits, int unique, int traces) {}

    /** Uninstrumented batch totals; divide by operations to obtain per-search costs.
     * @param operations completed searches
     * @param nanos elapsed wall time, including result consumption and counter checks
     * @param bytes current-thread allocated bytes, including those checks
     */
    record Trial(int operations, long nanos, long bytes) {}

    /** One fixture/strategy measurement with raw trials and sampled attribution.
     * @param fixture source workload name
     * @param strategy explorer
     * @param counts stable counters validated outside timing
     * @param replaySteps sum of recorded prefix lengths
     * @param trials five plain batches
     * @param samples JFR event counts and weighted allocation estimates
     */
    record Measurement(String fixture, Strategy strategy, Counts counts, long replaySteps,
                       List<Trial> trials, Map<String, Long> samples) {}

    /** Delimits the measured main-thread region in each recording. */
    @Name("interleave.SearchBatch")
    @Label("Search profiling batch")
    static final class Batch extends Event {
        /** Number of completed searches in this phase. */
        long operations;
        /** Creates an initially empty phase marker. */
        Batch() {}
    }

    /** Runs three fixtures with rotated order and writes raw JSON plus one JFR per case.
     * <pre>{@code ./gradlew --max-workers=1 -PpitestThreads=1 profileSearch -PprofileFork=1}</pre>
     * @param args output directory and fork number (1 through 3)
     * @throws Exception if profiling is unsupported, evidence differs, or output cannot be written
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected output directory and fork 1..3");
        int fork = Integer.parseInt(args[1]);
        if (fork < 1 || fork > 3) throw new IllegalArgumentException("Fork must be 1..3");
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new IllegalStateException("Allocation counter unsupported");
        bean.setThreadAllocatedMemoryEnabled(true);
        var fixtures = new ArrayList<>(List.of(fixture("dependent", 4), fixture("independent", 6), fixture("contexts", 6)));
        Collections.rotate(fixtures, fork - 1);
        var strategies = new ArrayList<>(List.of(Strategy.values()));
        Collections.rotate(strategies, fork - 1);
        var measurements = new ArrayList<Measurement>();
        for (Fixture fixture : fixtures) {
            DfsResult baseline = search(fixture, Strategy.DFS);
            validate(fixture, baseline);
            int expectedStates = fixture.kind().equals("dependent") ? 7365 : 729;
            if (baseline.states().size() != expectedStates) throw new IllegalStateException("DFS state oracle differs");
            for (Strategy strategy : strategies) {
                DfsResult checked = search(fixture, strategy);
                validate(fixture, checked);
                Counts expected = counts(checked);
                if (fixture.kind().equals("contexts") && strategy == Strategy.DPOR && expected.visits() <= expected.unique()) {
                    throw new IllegalStateException("Context fixture does not exercise accepted revisits");
                }
                long warmUntil = System.nanoTime() + 2_000_000_000L;
                do { run(fixture, strategy, expected); } while (System.nanoTime() < warmUntil);
                int operations = 1;
                while (operations < 256 && trial(fixture, strategy, expected, operations, bean).nanos() < 100_000_000L) operations *= 2;
                var trials = new ArrayList<Trial>();
                for (int i = 0; i < 5; i++) trials.add(trial(fixture, strategy, expected, operations, bean));
                validate(fixture, retained);
                Path recording = output.resolve(fixture.kind() + "-" + strategy + ".jfr");
                record(fixture, strategy, expected, recording);
                validate(fixture, retained);
                measurements.add(new Measurement(fixture.kind(), strategy, expected,
                    checked.traces().stream().mapToLong(t -> t.threadIds().size()).sum(), trials, summarize(recording)));
                System.out.println(fixture.kind() + " " + strategy + " " + expected);
            }
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("fork", fork);
        report.put("java", System.getProperty("java.runtime.version"));
        report.put("vm", System.getProperty("java.vm.name"));
        report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        report.put("processors", Runtime.getRuntime().availableProcessors());
        report.put("heapBytes", Runtime.getRuntime().maxMemory());
        report.put("classSha256", fingerprints());
        report.put("measurements", measurements);
        Files.writeString(output.resolve("results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    /** Constructs two-action source owners, with a known empty observation set.
     * @param kind one of the three fixed workloads
     * @param owners positive number of owners
     * @return compiled immutable fixture
     */
    static Fixture fixture(String kind, int owners) {
        if (!Set.of("dependent", "independent", "contexts").contains(kind) || owners < 1) {
            throw new IllegalArgumentException("Unknown fixture or nonpositive owners");
        }
        var source = new StringBuilder("program profile { shared int x = 0; shared int flag = 0; ");
        for (int i = 0; i < owners; i++) source.append("shared int x").append(i).append(" = 0; ");
        for (int i = 0; i < owners; i++) {
            source.append("thread ").append(i).append(" { ");
            if (kind.equals("dependent")) {
                source.append("atomic { x = x * 5 + ").append(i + 1).append("; } ").append("atomic { x = x * 5 + ").append(i + 1).append("; }");
            } else {
                source.append(kind.equals("contexts") ? "atomic { flag = 1; } " : "atomic { x" + i + " = 1; } ");
                source.append("atomic { x").append(i).append(" = ").append(kind.equals("contexts") ? 1 : 2).append("; }");
            }
            source.append(" } ");
        }
        source.append("invariant always (true); }");
        return new Fixture(kind, owners, new SourceProgramLoader().load(source.toString()));
    }

    /** Explores through public APIs without visitors or approximate storage.
     * @param fixture compiled source model
     * @param strategy selected search
     * @return full search result
     */
    static DfsResult search(Fixture fixture, Strategy strategy) {
        var model = fixture.model();
        var invariant = model.invariant().orElseThrow();
        return switch (strategy) {
            case DFS -> new DfsExplorer().explore(model.program(), invariant);
            case POR -> new StaticPorExplorer().explore(model.program(), invariant);
            case DPOR -> new DporExplorer().explore(model.program(), invariant);
        };
    }

    /** Extracts constant-time counters without traversing witness lists.
     * @param result completed search
     * @return visit, key and witness counts
     */
    static Counts counts(DfsResult result) {
        return new Counts(result.statesExplored(), result.states().size(), result.traces().size());
    }

    /** Consumes one search and rejects counter drift without replaying inside timing.
     * @param fixture compiled source
     * @param strategy selected explorer
     * @param expected validated counters
     */
    private static void run(Fixture fixture, Strategy strategy, Counts expected) {
        retained = search(fixture, strategy);
        if (!counts(retained).equals(expected)) throw new IllegalStateException("Search counters drifted");
    }

    /** Measures a plain batch using a per-thread byte counter and monotonic clock.
     * @param fixture compiled source
     * @param strategy selected explorer
     * @param expected validated counters
     * @param operations batch length
     * @param bean enabled host allocation counter
     * @return raw totals (no JFR enabled in this batch)
     */
    private static Trial trial(Fixture fixture, Strategy strategy, Counts expected, int operations, ThreadMXBean bean) {
        long thread = Thread.currentThread().threadId();
        long bytes = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
        for (int i = 0; i < operations; i++) run(fixture, strategy, expected);
        long elapsed = System.nanoTime() - start, allocated = bean.getThreadAllocatedBytes(thread) - bytes;
        if (bytes < 0 || allocated < 0) throw new IllegalStateException("Allocation counter unavailable");
        return new Trial(operations, elapsed, allocated);
    }

    /** Replays every witness and compares normalized observations to an external literal oracle.
     * @param fixture source fixture
     * @param result evidence to validate
     * @return SHA-256 of sorted distinct observations
     */
    static String validate(Fixture fixture, DfsResult result) {
        var values = new TreeSet<Integer>();
        for (Trace trace : result.traces()) {
            var end = new TraceReplayer().replay(fixture.model().program(), trace, fixture.model().invariant().orElseThrow());
            if (trace.outcome() != TraceOutcome.COMPLETED || !end.allTerminated()
                    || trace.threadIds().size() != fixture.owners() * 2
                    || end.programCounters().stream().anyMatch(pc -> pc != 2)) {
                throw new IllegalStateException("Incomplete or invalid witness");
            }
            var state = (DynamicState) end.state();
            if (fixture.kind().equals("dependent")) values.add(state.getInt("x"));
            else {
                int expected = fixture.kind().equals("contexts") ? 1 : 2;
                for (int i = 0; i < fixture.owners(); i++) {
                    if (state.getInt("x" + i) != expected) throw new IllegalStateException("Incorrect terminal field");
                }
                if (fixture.kind().equals("contexts") && state.getInt("flag") != 1) throw new IllegalStateException("Incorrect flag");
                values.add(0);
            }
        }
        String digest = digest(values.stream().map(v -> v + "\n").collect(java.util.stream.Collectors.joining()).getBytes(StandardCharsets.UTF_8));
        String expected = fixture.kind().equals("dependent")
            ? switch (fixture.owners()) {
                case 2 -> "8ac14501cf689e81f25e74b21d06b47e20eb9368e10d58869e73f857a5e42f90";
                case 4 -> DEPENDENT_DIGEST;
                default -> throw new IllegalArgumentException("No independent dependent oracle for owner count");
            } : SINGLE_DIGEST;
        if (!digest.equals(expected)) throw new IllegalStateException("Terminal oracle differs: " + digest);
        return digest;
    }

    /** Records a separate three-second attribution phase, excluding startup, replay and output.
     * @param fixture compiled source
     * @param strategy selected explorer
     * @param expected validated counters
     * @param path recording destination
     * @throws IOException if the recording cannot be written
     */
    private static void record(Fixture fixture, Strategy strategy, Counts expected, Path path) throws IOException {
        try (var recording = new Recording()) {
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1)).withStackTrace();
            recording.enable("jdk.ObjectAllocationSample").withStackTrace();
            recording.enable(Batch.class).withThreshold(Duration.ZERO).withoutStackTrace();
            recording.start();
            var batch = new Batch();
            batch.begin();
            long until = System.nanoTime() + 3_000_000_000L;
            do { run(fixture, strategy, expected); batch.operations++; } while (System.nanoTime() < until);
            batch.end();
            batch.commit();
            recording.stop();
            recording.dump(path);
        }
    }

    /** Classifies inclusive stack families once per sample, in documented precedence order.
     * @param frames class name, hash and method name for every recorded Java frame
     * @return mutually exclusive family; absent stacks are unknown
     */
    static String category(List<String> frames) {
        if (frames.isEmpty()) return "unknown";
        if (frames.stream().anyMatch(f -> f.equals("dev.samhb.interleave.search.Trace#create"))) return "traceReplay";
        if (frames.stream().anyMatch(f -> f.startsWith("dev.samhb.interleave.dpor.DporExplorer#lambda$dporDfs$"))) return "coverageLambda";
        if (frames.stream().anyMatch(f -> f.equals("dev.samhb.interleave.dpor.SleepSet#entries") || f.equals("dev.samhb.interleave.dpor.SleepSet#copy"))) return "coverageAccess";
        if (frames.stream().anyMatch(f -> f.startsWith("dev.samhb.interleave.dpor.SleepSet#"))) return "sleepOperations";
        return "other";
    }

    /** Aggregates only main-thread samples inside exactly one phase marker.
     * Execution samples use sampledThread; allocation weights are estimates, never exact bytes.
     * @param path completed JFR recording
     * @return event counts and weighted allocation estimates by stack family
     * @throws IOException for unreadable recordings
     */
    static Map<String, Long> summarize(Path path) throws IOException {
        var events = RecordingFile.readAllEvents(path);
        var phases = events.stream().filter(e -> e.getEventType().getName().equals("interleave.SearchBatch")).toList();
        if (phases.size() != 1) throw new IllegalStateException("Expected exactly one batch marker");
        var phase = phases.getFirst();
        var totals = new TreeMap<String, Long>();
        totals.put("profileOperations", phase.getLong("operations"));
        for (RecordedEvent event : events) {
            String name = event.getEventType().getName();
            boolean execution = name.equals("jdk.ExecutionSample");
            if (!execution && !name.equals("jdk.ObjectAllocationSample")) continue;
            RecordedThread thread = execution ? event.getThread("sampledThread") : event.getThread();
            if (thread == null || thread.getJavaThreadId() != phase.getThread().getJavaThreadId()
                    || event.getStartTime().isBefore(phase.getStartTime()) || event.getStartTime().isAfter(phase.getEndTime())) continue;
            var frames = event.getStackTrace() == null ? List.<String>of() : event.getStackTrace().getFrames().stream()
                .map(f -> f.getMethod().getType().getName() + "#" + f.getMethod().getName()).toList();
            String family = category(frames);
            totals.merge((execution ? "executionSamples." : "allocationSamples.") + family, 1L, Long::sum);
            if (!execution) totals.merge("allocationWeight." + family, event.getLong("weight"), Long::sum);
        }
        return totals;
    }

    /** Fingerprints measured class bytes without logging environment variables or JVM arguments.
     * @return class names and SHA-256 values for the runner and directly profiled production paths
     * @throws IOException if class bytes are unavailable
     */
    private static Map<String, String> fingerprints() throws IOException {
        var hashes = new TreeMap<String, String>();
        for (Class<?> type : List.of(SearchProfile.class, DfsExplorer.class, StaticPorExplorer.class, DporExplorer.class,
                Trace.class, SleepSet.class)) {
            try (var bytes = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                if (bytes == null) throw new IOException("Missing class bytes: " + type.getName());
                hashes.put(type.getName(), digest(bytes.readAllBytes()));
            }
        }
        return hashes;
    }

    /** Hashes bytes using the mandatory JDK SHA-256 algorithm.
     * @param bytes input bytes
     * @return lowercase hexadecimal digest
     */
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("JDK lacks SHA-256", e); }
    }
}
