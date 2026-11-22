/** Immutable artifact-bound bytecode evidence, separate from source spans and source failure contracts. */
package dev.samhb.interleave.core;

import dev.samhb.interleave.bytecode.BytecodeLimits;
import dev.samhb.interleave.bytecode.BytecodeLocation;
import java.io.Serializable;
import java.util.*;

/** Complete recorded bytecode execution with independently authenticated prefix replay observations.
 * @param version wire/native payload schema, currently2
 * @param identity frozen artifact, model declarations, profile, limits and mode
 * @param transitions actual original actor selections and executed operations
 * @param snapshots immutable initial and transition-boundary snapshots
 * @param terminal original run classification, including honest external stops
 * @param replayObservation actual replay observation, otherwise null; never upgrades the original run */
public record BytecodeEvidence(int version,Identity identity,List<Transition> transitions,List<Snapshot> snapshots,
                               Terminal terminal,Terminal replayObservation) implements Serializable {
    /** Freezes aligned evidence and rejects incompatible schemas or a nonterminal original record. */
    public BytecodeEvidence {
        if(version!=1 && version!=2)throw new IllegalArgumentException("Unsupported bytecode evidence version");
        Objects.requireNonNull(identity);Objects.requireNonNull(terminal);
        if((version==2)!=identity.profileVersion().equals("BYTECODE-SC-3")
            || version==1 && !Set.of("BYTECODE-SC-1","BYTECODE-SC-2").contains(identity.profileVersion()))
            throw new IllegalArgumentException("Unsupported schema/profile pairing");
        if(version==2) {
            requireBudgetOwner(terminal,identity.actors().size());if(replayObservation!=null)requireBudgetOwner(replayObservation,identity.actors().size());
        }
        transitions=List.copyOf(transitions);snapshots=List.copyOf(snapshots);
        if(version==1 && transitions.stream().flatMap(transition->transition.operations().stream()).anyMatch(operation->
            Set.of("INTRINSIC_BODY","MONITOR_EXIT").contains(operation.event())))throw new IllegalArgumentException("Intrinsic event requires bytecode evidence schema2");
        if(snapshots.size()!=transitions.size()+1 || terminal.kind().equals("PREFIX"))throw new IllegalArgumentException("Misaligned bytecode evidence");
    }

    /** Descriptor-complete interpreted entry identity.
     * @param owner JVM internal declaring class name
     * @param name method name
     * @param descriptor exact JVM method descriptor */
    public record Method(String owner,String name,String descriptor) implements Serializable {
        /** Requires complete method identity. */
        public Method {text(owner);text(name);text(descriptor);}
    }

    /** Original annotation-declared actor mapping.
     * @param id dense original actor identity
     * @param method public instance no-argument void entry */
    public record Actor(int id,Method method) implements Serializable {
        /** Validates the original actor and method shape. */
        public Actor {Objects.requireNonNull(method);if(id<0 || id>7 || !method.descriptor().equals("()V"))throw new IllegalArgumentException("Invalid actor entry");}
    }

    /** Declared pure observer mapping.
     * @param method public instance no-argument boolean entry
     * @param when ALWAYS or FINAL */
    public record Check(Method method,String when) implements Serializable {
        /** Validates timing and the boolean observer descriptor. */
        public Check {Objects.requireNonNull(method);if(!Set.of("ALWAYS","FINAL").contains(when) || !method.descriptor().equals("()Z"))throw new IllegalArgumentException("Invalid check entry");}
    }

    /** Frozen semantic identity; host directory paths are deliberately absent.
     * @param artifactDigest exact class-manifest SHA-256
     * @param profileVersion admitted semantics/intrinsic-cost version
     * @param modelClass selected binary fixture class name
     * @param actors original dense actor/declaration mapping
     * @param checks deterministically ordered check declarations
     * @param limits immutable selected execution allowances
     * @param mode BATCHED or internal REFERENCE execution granularity
     * @param requestedStrategy requested traversal
     * @param effectiveStrategy actual traversal
     * @param fallbackReason absent for this release's exact DFS */
    public record Identity(String artifactDigest,String profileVersion,String modelClass,List<Actor> actors,List<Check> checks,
                           BytecodeLimits limits,String mode,String requestedStrategy,String effectiveStrategy,String fallbackReason) implements Serializable {
        /** Freezes declarations and rejects malformed identity or unavailable94C traversal metadata. */
        public Identity {
            digest(artifactDigest);text(profileVersion);text(modelClass);Objects.requireNonNull(limits);
            actors=List.copyOf(actors);checks=List.copyOf(checks);
            if(actors.isEmpty() || actors.size()>8)throw new IllegalArgumentException("Invalid actor count");
            for(int index=0;index<actors.size();index++)if(actors.get(index).id()!=index)throw new IllegalArgumentException("Actor identities must be dense");
            if(!Set.of("BATCHED","REFERENCE").contains(mode) || !"DFS".equals(requestedStrategy) || !"DFS".equals(effectiveStrategy) || fallbackReason!=null)
                throw new IllegalArgumentException("Unsupported bytecode strategy or mode");
        }
    }

    /** One real opcode or finite implicit event, never a fabricated source instruction.
     * @param location actual method/descriptor/BCI
     * @param line positive compiler line when available, otherwise null
     * @param opcode real opcode name, null for an implicit/omitted event
     * @param event OPCODE or explicit implicit-event classification */
    public record Operation(BytecodeLocation location,Integer line,String opcode,String event) implements Serializable {
        /** Requires a real position and consistent opcode/event representation. */
        public Operation {
            Objects.requireNonNull(location);
            if(line!=null && line<1)throw new IllegalArgumentException("Invalid compiler line");
            if(!Set.of("OPCODE","MONITOR_ENTER","RETURN","EXCEPTION","TERMINATION","FAILURE","CUTOFF","FUEL_CUTOFF","INTRINSIC_BODY","MONITOR_EXIT").contains(event)
                || (event.equals("OPCODE") || event.equals("INTRINSIC_BODY"))!=(opcode!=null))throw new IllegalArgumentException("Invalid bytecode operation");
            if(opcode!=null)java.lang.classfile.Opcode.valueOf(opcode);
        }
    }

    /** One selected actor's actual scheduling transition.
     * @param actor original actor identity
     * @param operations committed opcode/event positions */
    public record Transition(int actor,List<Operation> operations) implements Serializable {
        /** Freezes nonempty selected work. */
        public Transition {operations=List.copyOf(operations);if(actor<0 || actor>7 || operations.isEmpty())throw new IllegalArgumentException("Invalid bytecode transition");}
    }

    /** Lossless typed value: wide numeric data is a string, never an untyped JSON number.
     * LONG and raw FLOAT_BITS/DOUBLE_BITS retain default wide array cells; wider computation belongs to later phases.
     * @param type INT, LONG, FLOAT_BITS, DOUBLE_BITS, BOOLEAN, REFERENCE, NULL, TEXT or FAULT
     * @param raw exact decimal, raw hex bits, permanent identity or diagnostic UTF-16 text */
    public record Value(String type,String raw) implements Serializable {
        /** Validates tags and exact numeric representations without floating-point coercion. */
        public Value {
            Objects.requireNonNull(raw);
            switch(Objects.requireNonNull(type)) {
                case "INT" -> {if(!Integer.toString(Integer.parseInt(raw)).equals(raw))throw new IllegalArgumentException("Noncanonical int");}
                case "LONG" -> {if(!Long.toString(Long.parseLong(raw)).equals(raw))throw new IllegalArgumentException("Noncanonical long");}
                case "FLOAT_BITS" -> {if(!raw.matches("[0-9a-f]{8}"))throw new IllegalArgumentException("Invalid float bits");}
                case "DOUBLE_BITS" -> {if(!raw.matches("[0-9a-f]{16}"))throw new IllegalArgumentException("Invalid double bits");}
                case "BOOLEAN" -> {if(!raw.equals("true") && !raw.equals("false"))throw new IllegalArgumentException("Invalid boolean");}
                case "NULL" -> {if(!raw.isEmpty())throw new IllegalArgumentException("Invalid null value");}
                case "REFERENCE","FAULT","TEXT" -> {text(raw);}
                default -> throw new IllegalArgumentException("Unknown bytecode value tag");
            }
        }
    }

    /** Heap identity and synchronization/sharedness history, independent of field display names.
     * @param identity permanent setup/actor/constant identity
     * @param type internal class name
     * @param shared irreversible sharing flag
     * @param initialized whether constructor initialization completed
     * @param owner current monitor owner, -2 when unowned
     * @param depth reentrant monitor depth */
    public record ObjectState(String identity,String type,boolean shared,boolean initialized,int owner,int depth) implements Serializable {
        /** Requires a permanent identity and internally consistent monitor state. */
        public ObjectState {text(identity);text(type);if(owner< -2 || owner>7 || depth<0 || (owner== -2)!=(depth==0))throw new IllegalArgumentException("Invalid monitor state");}
    }

    /** Immutable display data plus a full-state authentication digest covering all hidden continuation state.
     * @param stateDigest SHA-256 of the complete canonical state, including counters/frames/history
     * @param fields typed model/static fields and setup diagnostics
     * @param locals per-original-actor frames, operand/local slots, phases and resource diagnostics
     * @param heap immutable heap identity/monitor metadata */
    public record Snapshot(String stateDigest,Map<String,Value> fields,Map<String,Map<String,Value>> locals,List<ObjectState> heap) implements Serializable {
        /** Deeply freezes maps and heap metadata; values contain only immutable tagged strings. */
        public Snapshot {
            digest(stateDigest);fields=Map.copyOf(fields);var copy=new TreeMap<String,Map<String,Value>>();
            locals.forEach((actor,values)->copy.put(actor,Map.copyOf(values)));locals=Collections.unmodifiableMap(copy);heap=List.copyOf(heap);
        }
    }

    /** Original modeled runtime fault and interpreted stack.
     * @param type internal exception class name
     * @param location original faulting instruction
     * @param stack interpreted method/BCI stack, innermost first */
    public record Fault(String type,BytecodeLocation location,List<BytecodeLocation> stack) implements Serializable {
        /** Freezes the genuine fault stack. */
        public Fault {text(type);Objects.requireNonNull(location);stack=List.copyOf(stack);if(stack.isEmpty())throw new IllegalArgumentException("Missing fault stack");}
    }

    /** Omitted modeled operation and exact allowance, separate from host resource interruptions.
     * @param reason bounded resource name
     * @param location omitted operation's real position
     * @param limit selected resource maximum
     * @param required work/demand for the attempt
     * @param remaining allowance before the attempt
     * @param budgetOwner charged allocator/setup/observer; null only for legacy schema1 */
    public record Cutoff(String reason,BytecodeLocation location,int limit,int required,int remaining,Integer budgetOwner) implements Serializable {
        /** Retains source compatibility for legacy schema1 producers.
         * @param reason bounded resource
         * @param location omitted instruction
         * @param limit selected maximum
         * @param required attempted work
         * @param remaining unspent allowance */
        public Cutoff(String reason,BytecodeLocation location,int limit,int required,int remaining){this(reason,location,limit,required,remaining,null);}
        /** Requires genuine resource/location data and a valid explicit or absent legacy owner. */
        public Cutoff {if(budgetOwner!=null && budgetOwner!= -4 && budgetOwner!= -1 && (budgetOwner<0 || budgetOwner>7))throw new IllegalArgumentException("Invalid budget owner");text(reason);Objects.requireNonNull(location);if(limit<1 || required<1 || remaining<0)throw new IllegalArgumentException("Invalid cutoff allowance");}
    }

    /** Pure observer failure identity, never an actor exception.
     * @param kind FALSE, ERROR or LIMIT
     * @param check declared check method
     * @param location actual return, fault or omitted opcode
     * @param fault observer runtime fault for ERROR
     * @param cutoff observer resource stop for LIMIT */
    public record CheckFailure(String kind,Method check,BytecodeLocation location,Fault fault,Cutoff cutoff) implements Serializable {
        /** Validates the exclusive observer cause. */
        public CheckFailure {
            Objects.requireNonNull(check);Objects.requireNonNull(location);
            if(!Set.of("FALSE","ERROR","LIMIT").contains(kind) || (fault!=null)!=kind.equals("ERROR") || (cutoff!=null)!=kind.equals("LIMIT"))throw new IllegalArgumentException("Invalid observer failure");
        }
    }

    /** Concrete deadlock ownership relationship.
     * @param actor blocked original actor
     * @param owner current owning actor
     * @param monitor permanent monitored-object identity
     * @param depth owning reentrant depth */
    public record Wait(int actor,int owner,String monitor,int depth) implements Serializable {
        /** Rejects invalid ownership or an unheld monitor. */
        public Wait {text(monitor);if(actor<0 || actor>7 || owner<0 || owner>7 || depth<1)throw new IllegalArgumentException("Invalid wait relationship");}
    }

    /** Terminal meaning; PREFIX appears only as a separate external-prefix replay observation.
     * @param kind COMPLETED, DEADLOCK, UNCAUGHT_EXCEPTION, CHECK_FALSE, CUTOFF, OBSERVER_ERROR, OBSERVER_LIMIT, INTERRUPTED or PREFIX
     * @param actor fault/cutoff actor, otherwise -1
     * @param fault original actor fault, also retained as context when cleanup is cut off
     * @param cutoff modeled actor cutoff
     * @param check separate observer failure
     * @param waits concrete deadlock relationships
     * @param interruption external checker reason, never a modeled cutoff */
    public record Terminal(String kind,int actor,Fault fault,Cutoff cutoff,CheckFailure check,List<Wait> waits,String interruption) implements Serializable {
        /** Validates classification-specific evidence without executing a model. */
        public Terminal {
            waits=List.copyOf(waits);
            if(!Set.of("COMPLETED","DEADLOCK","UNCAUGHT_EXCEPTION","CHECK_FALSE","CUTOFF","OBSERVER_ERROR","OBSERVER_LIMIT","INTERRUPTED","PREFIX").contains(kind))throw new IllegalArgumentException("Unknown bytecode terminal");
            boolean actorFailure=kind.equals("UNCAUGHT_EXCEPTION") || kind.equals("CUTOFF");
            if(actorFailure ? actor<0 || actor>7 : actor!= -1)throw new IllegalArgumentException("Invalid terminal actor");
            if((kind.equals("UNCAUGHT_EXCEPTION") && fault==null) || (fault!=null && !Set.of("UNCAUGHT_EXCEPTION","CUTOFF").contains(kind))
                || (cutoff!=null)!=kind.equals("CUTOFF")
                || (check!=null)!=Set.of("CHECK_FALSE","OBSERVER_ERROR","OBSERVER_LIMIT").contains(kind)
                || kind.equals("DEADLOCK")!=!waits.isEmpty() || (interruption!=null)!=kind.equals("INTERRUPTED"))throw new IllegalArgumentException("Conflicting bytecode terminal causes");
            if(check!=null && !check.kind().equals(switch(kind){case "CHECK_FALSE"->"FALSE";case "OBSERVER_ERROR"->"ERROR";default->"LIMIT";}))throw new IllegalArgumentException("Wrong observer classification");
            if(interruption!=null && !Set.of("MAX_STATES","MAX_TIME","KEY_BYTES","CANCELLED").contains(interruption))throw new IllegalArgumentException("Unknown checker interruption");
        }
    }

    /** Validates exact SHA-256 values before replay or serialization consumers use them.
     * @param value required lowercase digest */
    private static void digest(String value){if(value==null || !value.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid bytecode digest");}

    /** Requires a nonempty immutable identifier/diagnostic value.
     * @param value required text */
    private static void text(String value){if(value==null || value.isEmpty())throw new IllegalArgumentException("Missing bytecode identity text");}
    /** Requires new-schema resource ownership without guessing absent legacy data.
     * @param actors declared actor count
     * @param terminal original or replay terminal
     * @throws IllegalArgumentException when a new-schema cutoff omits its charged owner */
    private static void requireBudgetOwner(Terminal terminal,int actors) {
        if(terminal.cutoff()!=null && (terminal.cutoff().budgetOwner()==null || terminal.cutoff().budgetOwner()== -4 || terminal.cutoff().budgetOwner()>=actors)
            || terminal.check()!=null && terminal.check().cutoff()!=null && !Integer.valueOf(-4).equals(terminal.check().cutoff().budgetOwner()))
            throw new IllegalArgumentException("Missing cutoff budget owner");
    }

}
