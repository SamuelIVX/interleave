/**
 * Complete owned heap-profile state, including resources, aliases and pending terminal events.
 */
package dev.samhb.interleave.bytecode;

import java.io.*;
import java.lang.classfile.ClassFile;
import java.util.*;

/**
 * Mutable execution snapshot; immutable value identities are safe to share between copies.
 */
final class BytecodeState {
    /**
     * Creates empty owned storage before setup populates it.
     */
    BytecodeState() {}
    /**
     * Setup namespace also identifies its resource owner.
     */
    static final int SETUP = -1;
    /** Private observer execution owner; it never appears in committed actor state. */
    static final int OBSERVER = -4;
    /**
     * Null is a distinct tagged reference, never an integer zero.
     */
    static final Ref NULL = new Ref(-3,0);
    /**
     * Setup allocates the fixture first.
     */
    static final Ref FIXTURE = new Ref(SETUP,0);
    /**
     * Operand/local value families admitted by this profile.
     */
    sealed interface Value permits Int, Ref, Thrown, WideZero, Unavailable, Literal {}
    /**
     * Models a JVM int, including the boolean operand representation.
     * @param value JVM int (also boolean representation)
     */
    record Int(int value) implements Value {}
    /** Internal unknown VM message; never returned as a target-language value. */
    enum Unavailable implements Value { /** Implicit VM message that admission proves cannot be inspected. */ MESSAGE }
    /** Frozen input literal, resolved to an interned identity before execution.
     * @param text exact UTF-16 class-file contents */
    record Literal(String text) implements Value {}
    /** Default-only wide array slot; computations remain excluded until97.
     * @param type LONG, FLOAT or DOUBLE tag */
    record WideZero(String type) implements Value {}
    /**
     * Names a permanent object identity in a setup, actor or constant namespace.
     * @param owner setup/actor/constant namespace
     * @param sequence permanent allocation identity
     */
    record Ref(int owner, long sequence) implements Value, Comparable<Ref> {
        /**
         * Orders complete reference identities deterministically without renumbering objects.
         * @param other reference
         * @return deterministic identity ordering
         */
        @Override public int compareTo(Ref other) {
            int comparison = Integer.compare(owner,other.owner);
            return comparison == 0 ? Long.compare(sequence,other.sequence) : comparison;
        }
    }
    /**
     * Carries a bounded VM fault through compiler-generated cleanup only.
     * @param fault bounded VM fault token used only by verified monitor cleanup
     */
    record Thrown(Fault fault) implements Value {}
    /**
     * Retains the original modeled failure and interpreted invocation stack.
     * @param type modeled exception class
     * @param location original throw site
     * @param stack interpreted frames
     */
    record Fault(String type, BytecodeLocation location, List<BytecodeLocation> stack) {
        /**
         * Freezes the interpreted stack.
         */
        Fault { stack = List.copyOf(stack); }
    }
    /**
     * Records the omitted operation and its resource accounting without freezing an actor.
     * @param reason bounded resource
     * @param location omitted instruction
     * @param limit configured maximum
     * @param required total instruction work when relevant
     * @param budgetOwner allocator/setup/observer charged by the omitted operation
     * @param remaining allowance before attempt
     */
    record Cutoff(String reason, BytecodeLocation location, int limit, int required, int remaining, int budgetOwner) {}
    /**
     * Execution and pending-event phases; zero-work events advance monotonically between opcodes.
     */
    enum Phase {
        /**
         * Ready for an actual opcode or synchronized entry.
         */ RUNNING,
        /**
         * An executed return awaits monitor release and caller resumption.
         */ RETURNING,
        /**
         * A fault awaits handler delivery or one-frame unwinding.
         */ EXCEPTION,
        /**
         * Normal root return awaits a scheduler-selected terminal event.
         */ TERMINATION_PENDING,
        /**
         * Uncaught unwinding awaits a scheduler-selected failure event.
         */ FAILURE_PENDING,
        /**
         * Omitted work awaits a scheduler-selected cutoff event.
         */ CUTOFF_PENDING,
        /**
         * This owner returned normally; other actors can still execute.
         */ TERMINATED,
        /**
         * An uncaught fault ended the whole execution prefix.
         */ FAILED,
        /**
         * A resource bound ended the whole execution prefix.
         */ CUTOFF,
        /** A frozen receiver-monitor intrinsic body awaits execution. */ INTRINSIC_BODY,
        /** An intrinsic body awaits exactly one monitor release. */ INTRINSIC_EXIT
    }
    /**
     * Mutable application object or exact-descriptor array.
     */
    static final class ObjectValue {
        /**
         * Internal class identity.
         */
        final String type;
        /**
         * Owned field values.
         */
        final NavigableMap<BytecodeProgram.FieldId,Value> fields = new TreeMap<>();
        /**
         * Irreversible historical promotion; setup objects begin shared.
         */
        boolean shared = true;
        /**
         * Constructor completion affects legal future execution.
         */
        boolean initialized;
        /** Array elements, null for ordinary objects; type retains rank and exact component descriptor. */
        ArrayList<Value> elements;
        /**
         * Monitor owner; -2 denotes unowned.
         */
        int monitorOwner = -2;
        /**
         * Reentrant depth.
         */
        int monitorDepth;
        /**
         * Creates owned storage for a modeled object.
         * @param type internal class
         */
        ObjectValue(String type) { this.type = type; }
        /**
         * Copies mutable containers while retaining immutable metadata and value identities.
         * @return independent mutable heap object
         */
        ObjectValue copy() {
            var copy = new ObjectValue(type); copy.fields.putAll(fields); copy.shared = shared;
            copy.initialized = initialized; copy.elements=elements==null ? null : new ArrayList<>(elements); copy.monitorOwner = monitorOwner; copy.monitorDepth = monitorDepth;
            return copy;
        }
    }
    /**
     * One actor's complete execution and permanent resource history.
     */
    static final class Execution {
        /**
         * Creates an execution owner with no work or allocations consumed.
         */
        Execution() {}
        /**
         * Ordered invocation frames, outermost first.
         */
        final ArrayList<BytecodeFrame> frames = new ArrayList<>();
        /**
         * Current scheduler/event phase.
         */
        Phase phase = Phase.RUNNING;
        /**
         * Consumed instruction/intrinsic work.
         */
        int instructions;
        /**
         * Permanent successful allocation count.
         */
        int allocations;
        /**
         * Permanent modeled heap-cell charge.
         */
        int cells;
        /**
         * Next namespace-local allocation sequence.
         */
        long nextSequence;
        /**
         * Original failure, retained through unwinding.
         */
        Fault fault;
        /** Materialized exception identity retained while unwinding and rethrowing. */
        Ref throwable;
        /** Exact known VM validation message/cause, absent for unavailable implicit messages. */
        BytecodeThrowables.Payload faultPayload;
        /** Frozen arguments and resource work under a selected intrinsic receiver monitor. */
        BytecodeThrowables.Call intrinsic;
        /**
         * Prefix-ending omitted operation.
         */
        Cutoff cutoff;
        /**
         * Value awaiting interpreted return.
         */
        Value returned;
        /**
         * Causing actual opcode for pending terminal/return events.
         */
        BytecodeLocation eventLocation;
        /**
         * Copies mutable containers while retaining immutable metadata and value identities.
         * @return independent execution frames/counters
         */
        Execution copy() {
            var copy = new Execution(); frames.forEach(f -> copy.frames.add(f.copy()));
            copy.phase=phase; copy.instructions=instructions; copy.allocations=allocations; copy.cells=cells;
            copy.nextSequence=nextSequence; copy.fault=fault; copy.throwable=throwable; copy.faultPayload=faultPayload; copy.intrinsic=intrinsic; copy.cutoff=cutoff; copy.returned=returned;
            copy.eventLocation=eventLocation; return copy;
        }
    }
    /**
     * Deterministic heap identities.
     */
    final NavigableMap<Ref,ObjectValue> heap = new TreeMap<>();
    /**
     * Complete application static slots.
     */
    final NavigableMap<BytecodeProgram.FieldId,Value> statics = new TreeMap<>();
    /**
     * Setup and actor execution owners.
     */
    final NavigableMap<Integer,Execution> executions = new TreeMap<>();

    /**
     * Copies mutable containers while retaining immutable metadata and value identities.
     * @return deeply isolated future-equivalent state
     */
    BytecodeState copy() {
        var copy = new BytecodeState(); heap.forEach((k,v) -> copy.heap.put(k,v.copy()));
        copy.statics.putAll(statics); executions.forEach((k,v) -> copy.executions.put(k,v.copy())); return copy;
    }

    /**
     * Adds a fresh invocation without replacing permanent setup allocation charges.
     * @param owner fixed actor or setup
     * @param method root method
     * @param arguments root receiver/arguments
     */
    void start(int owner, BytecodeMethod method, List<Value> arguments) {
        var execution = executions.computeIfAbsent(owner,ignored -> new Execution());
        execution.frames.add(new BytecodeFrame(method,arguments)); execution.phase=Phase.RUNNING;
    }

    /**
     * Resolves a deterministic application class-monitor identity.
     * @param name internal class
     * @param program artifact
     * @return stable class monitor
     */
    static Ref classMonitor(String name, BytecodeProgram program) { return new Ref(-2,program.classes.indexOf(name)); }

    /**
     * Builds setup-owned defaults and deterministic class-monitor storage.
     * @param program validated artifact
     * @return setup state before constructor execution
     */
    static BytecodeState beforeSetup(BytecodeProgram program) {
        var state = new BytecodeState(); var setup = new Execution();
        state.executions.put(SETUP,setup);
        long cells=(long)BytecodeThrowables.baseCells(program.hierarchy,program.model)+program.classes.size()+program.fields.values().stream().filter(field ->
            (field.flags() & ClassFile.ACC_STATIC)!=0).count()+program.hierarchy.instanceFields(program.model).size();
        if (cells>program.limits.setupHeapCells()) throw new BytecodeLoadException("LIMIT","Setup default cells exceed allowance",null);
        var fixture = new ObjectValue(program.model);
        for (var field : program.fields.values()) {
            if ((field.flags() & ClassFile.ACC_STATIC) != 0) state.statics.put(field.id(),field.initial() instanceof Literal literal ? program.strings.get(literal.text()) : field.initial());

        }
        program.hierarchy.instanceFields(program.model).forEach(field->fixture.fields.put(field.id(),field.initial()));
        state.heap.put(FIXTURE,fixture); setup.allocations=1; setup.nextSequence=1;
        for (String type : program.classes) {
            var monitor = new ObjectValue("java/lang/Class"); monitor.initialized=true;
            state.heap.put(classMonitor(type,program),monitor);
        }
        setup.cells=(int)cells;BytecodeStrings.install(program,state);
        return state;
    }

    /**
     * Reads the selected execution owner's current interpreted frame.
     * @param owner actor/setup
     * @return actual interpreted top frame
     */
    BytecodeFrame frame(int owner) { return executions.get(owner).frames.getLast(); }

    /**
     * Reads a declared fixture field for internal execution diagnostics.
     * @param field fixture field name
     * @return current modeled fixture value
     */
    Value fixtureField(String field) {
        return heap.get(FIXTURE).fields.entrySet().stream().filter(e -> e.getKey().name().equals(field))
            .findFirst().orElseThrow().getValue();
    }

    /**
     * Encodes all future-relevant heap-profile state, independent of diagnostic toString output.
     * @return versioned length-delimited canonical payload
     */
    byte[] canonicalBytes() {
        try {
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            out.writeInt(3); out.writeInt(heap.size());
            for (var entry : heap.entrySet()) {
                value(out,entry.getKey()); var object=entry.getValue(); text(out,object.type);
                out.writeBoolean(object.shared); out.writeBoolean(object.initialized);
                out.writeInt(object.monitorOwner); out.writeInt(object.monitorDepth); fields(out,object.fields);
                out.writeBoolean(object.elements!=null);
                if(object.elements!=null){out.writeInt(object.elements.size());for(Value element:object.elements)value(out,element);}
            }
            fields(out,statics); out.writeInt(executions.size());
            for (var entry : executions.entrySet()) {
                out.writeInt(entry.getKey()); var execution=entry.getValue(); out.writeInt(execution.phase.ordinal());
                out.writeInt(execution.instructions); out.writeInt(execution.allocations); out.writeInt(execution.cells);
                out.writeLong(execution.nextSequence); value(out,execution.throwable);out.writeBoolean(execution.faultPayload!=null);
                if(execution.faultPayload!=null){value(out,execution.faultPayload.message());value(out,execution.faultPayload.cause());out.writeBoolean(execution.faultPayload.causeInitialized());}
                BytecodeThrowables.encode(out,execution.intrinsic); fault(out,execution.fault); location(out,execution.eventLocation);
                value(out,execution.returned); out.writeBoolean(execution.cutoff != null);
                if (execution.cutoff != null) {
                    var cutoff=execution.cutoff; text(out,cutoff.reason()); location(out,cutoff.location());
                    out.writeInt(cutoff.limit()); out.writeInt(cutoff.required()); out.writeInt(cutoff.remaining());out.writeInt(cutoff.budgetOwner());
                }
                out.writeInt(execution.frames.size());
                for (var frame : execution.frames) {
                    location(out,frame.method.location(frame.bci)); out.writeBoolean(frame.entered); value(out,frame.methodMonitor); value(out,frame.constructorReceiver);
                    out.writeInt(frame.locals.size());
                    for (var local : frame.locals.entrySet()) { out.writeInt(local.getKey()); value(out,local.getValue()); }
                    out.writeInt(frame.stack.size()); for (Value operand : frame.stack) value(out,operand);
                }
            }
            out.flush(); return bytes.toByteArray();
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }

    /**
     * Writes ordered field identities and values to the canonical sink.
     * @param out canonical sink
     * @param fields ordered field values
     * @throws IOException for sink failure
     */
    private static void fields(DataOutputStream out, Map<BytecodeProgram.FieldId,Value> fields) throws IOException {
        out.writeInt(fields.size());
        for (var entry : fields.entrySet()) {
            var id=entry.getKey(); text(out,id.owner()); text(out,id.name()); text(out,id.descriptor()); value(out,entry.getValue());
        }
    }
    /**
     * Writes a tagged nullable slot without conflating integers, references and exception tokens.
     * @param out canonical sink
     * @param value nullable typed slot
     * @throws IOException for sink failure
     */
    static void value(DataOutputStream out, Value value) throws IOException {
        switch (value) {
            case null -> out.writeByte(0);
            case Int number -> { out.writeByte(1); out.writeInt(number.value()); }
            case Literal literal -> {out.writeByte(6);text(out,literal.text());}
            case Unavailable ignored -> out.writeByte(5);
            case WideZero wide -> {out.writeByte(4);text(out,wide.type());}
            case Ref reference -> { out.writeByte(2); out.writeInt(reference.owner()); out.writeLong(reference.sequence()); }
            case Thrown thrown -> { out.writeByte(3); fault(out,thrown.fault()); }
        }
    }
    /**
     * Writes the bounded original fault and interpreted frames.
     * @param out canonical sink
     * @param fault pending bounded fault
     * @throws IOException for sink failure
     */
    private static void fault(DataOutputStream out, Fault fault) throws IOException {
        out.writeBoolean(fault != null);
        if (fault != null) {
            text(out,fault.type()); location(out,fault.location()); out.writeInt(fault.stack().size());
            for (var position : fault.stack()) location(out,position);
        }
    }
    /**
     * Represents an actual method position without inventing source coordinates.
     * @param out canonical sink
     * @param location real optional position
     * @throws IOException for sink failure
     */
    private static void location(DataOutputStream out, BytecodeLocation location) throws IOException {
        out.writeBoolean(location != null);
        if (location != null) { text(out,location.owner()); text(out,location.name()); text(out,location.descriptor()); out.writeInt(location.bci()); }
    }
    /**
     * Writes full UTF-16 code units without modified-UTF or replacement-character collisions.
     * @param out canonical sink
     * @param text full UTF-16 code-unit string
     * @throws IOException for sink failure
     */
    static void text(DataOutputStream out, String text) throws IOException {
        out.writeInt(text.length()); for (int i=0;i<text.length();i++) out.writeChar(text.charAt(i));
    }
}
